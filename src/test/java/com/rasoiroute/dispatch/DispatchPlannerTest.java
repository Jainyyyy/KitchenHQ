package com.rasoiroute.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.geo.Haversine;
import com.rasoiroute.geo.Location;
import com.rasoiroute.geo.LocalityDirectory;
import com.rasoiroute.model.Fulfilment;
import com.rasoiroute.model.Order;
import com.rasoiroute.model.OrderType;
import com.rasoiroute.urgency.ScoredOrder;
import com.rasoiroute.urgency.UrgencyRanker;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DispatchPlannerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 19);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);
    private static final double EPS = 1e-9;

    private static final LocalityDirectory SMALL = LocalityDirectory.fromCsv(
            "Kitchen,23.0300,72.5500\n"
                    + "Alpha,23.0400,72.5600\n"
                    + "Beta,23.0500,72.5400\n"
                    + "Gamma,23.0200,72.5900\n");

    private final DispatchPlanner planner = new DispatchPlanner(SMALL);

    private static Order delivery(String id, int bookedHour, int dueHour, String locality) {
        return new Order(id, "Cust " + id, "9876543210", "Item " + id, OrderType.BAKERY, Fulfilment.DELIVERY,
                DAY.minusDays(1).atTime(bookedHour, 0), DAY.atTime(dueHour, 0), locality);
    }

    private static Order pickup(String id, int dueHour) {
        return new Order(id, "Cust " + id, "9876543211", "Item " + id, OrderType.DAILY_FOOD, Fulfilment.PICKUP,
                DAY.minusDays(1).atTime(9, 0), DAY.atTime(dueHour, 0), null);
    }

    private DispatchPlan plan(List<Order> orders) {
        return planner.plan(orders, DAY, NOW, 5, new Random(1));
    }

    @Test
    void onlyOrdersDueOnThatDayAreIncluded() {
        Order today = delivery("T", 9, 12, "Alpha");
        Order tomorrow = new Order("N", "Cust", "9876543212", "Item N", OrderType.BAKERY, Fulfilment.DELIVERY,
                DAY.minusDays(1).atTime(9, 0), DAY.plusDays(1).atTime(12, 0), "Beta");
        DispatchPlan plan = plan(List.of(today, tomorrow));
        assertEquals(1, plan.ordersDue());
        assertEquals(1, plan.deliveryCount());
        assertEquals(2, plan.route().stops().size());   // kitchen + Alpha only
    }

    @Test
    void pickupsAreListedEarliestFirstAndAreNotPartOfTheRoute() {
        DispatchPlan plan = plan(List.of(pickup("P2", 15), pickup("P1", 10)));
        assertEquals(2, plan.pickupCount());
        assertEquals(0, plan.deliveryCount());
        assertEquals("P1", plan.pickups().get(0).id());
        assertEquals("P2", plan.pickups().get(1).id());
        assertEquals(1, plan.route().stops().size());   // only the kitchen
    }

    @Test
    void twoOrdersToTheSameLocalityShareOneStop() {
        DispatchPlan plan = plan(List.of(delivery("A", 9, 14, "Alpha"), delivery("B", 10, 11, "alpha")));
        assertEquals(2, plan.route().stops().size());
        RouteStop alpha = plan.route().stops().get(1);
        assertEquals("Alpha", alpha.locality());
        assertEquals(List.of("B", "A"), alpha.orderIds());   // earliest due first
    }

    @Test
    void routeStartsAtTheKitchenAndVisitsEachLocalityOnce() {
        DispatchPlan plan = plan(List.of(delivery("A", 9, 12, "Alpha"), delivery("B", 10, 13, "Beta"),
                delivery("C", 11, 14, "Gamma"), delivery("D", 12, 15, "Beta")));
        List<RouteStop> stops = plan.route().stops();
        assertEquals("Kitchen", stops.get(0).locality());
        assertEquals(0.0, stops.get(0).legKm(), EPS);
        Set<String> names = new HashSet<>();
        for (RouteStop stop : stops) {
            names.add(stop.locality());
        }
        assertEquals(4, stops.size());
        assertEquals(4, names.size());
    }

    @Test
    void legsPlusTheWayBackAddUpToTheTotal() {
        DispatchPlan plan = plan(List.of(delivery("A", 9, 12, "Alpha"), delivery("B", 10, 13, "Beta"),
                delivery("C", 11, 14, "Gamma")));
        double sum = plan.route().returnKm();
        for (RouteStop stop : plan.route().stops()) {
            sum += stop.legKm();
        }
        assertEquals(plan.route().totalKm(), sum, EPS);
    }

    @Test
    void routeIsWithinTwiceTheBestAndNeverBetterThanTheBest() {
        DispatchPlan plan = plan(List.of(delivery("A", 9, 12, "Alpha"), delivery("B", 10, 13, "Beta"),
                delivery("C", 11, 14, "Gamma")));
        RouteSummary route = plan.route();
        assertTrue(route.withinGuarantee());
        assertTrue(route.exactKm().isPresent());
        assertTrue(route.totalKm() >= route.exactKm().getAsDouble() - EPS);
        assertTrue(route.totalKm() <= 2.0 * route.exactKm().getAsDouble() + EPS);
        assertTrue(route.exactKm().getAsDouble() <= route.nearestNeighbourKm() + EPS);
        assertTrue(route.exactKm().getAsDouble() <= route.arrivalOrderKm() + EPS);
    }

    @Test
    void arrivalOrderBaselineFollowsTheBookingOrder() {
        // Booked in this order: Gamma (9:00), Alpha (10:00), Beta (11:00).
        DispatchPlan plan = plan(List.of(delivery("C", 11, 14, "Beta"), delivery("A", 9, 12, "Gamma"),
                delivery("B", 10, 13, "Alpha")));
        Location k = SMALL.find("Kitchen");
        Location g = SMALL.find("Gamma");
        Location a = SMALL.find("Alpha");
        Location b = SMALL.find("Beta");
        double expected = Haversine.distanceKm(k, g) + Haversine.distanceKm(g, a)
                + Haversine.distanceKm(a, b) + Haversine.distanceKm(b, k);
        assertEquals(expected, plan.route().arrivalOrderKm(), EPS);
    }

    @Test
    void anUnknownLocalityIsReportedAndLeftOutNotFatal() {
        DispatchPlan plan = plan(List.of(delivery("A", 9, 12, "Alpha"), delivery("X", 10, 13, "Mars")));
        assertEquals(1, plan.problems().size());
        assertTrue(plan.problems().get(0).contains("X"));
        assertTrue(plan.problems().get(0).contains("Mars"));
        assertEquals(2, plan.route().stops().size());   // kitchen + Alpha
        assertEquals(2, plan.deliveryCount());          // still counted as a delivery due that day
    }

    @Test
    void aDayWithNoOrdersGivesAKitchenOnlyRoute() {
        DispatchPlan plan = plan(List.of());
        assertEquals(0, plan.ordersDue());
        assertTrue(plan.topUrgent().isEmpty());
        assertEquals(1, plan.route().stops().size());
        assertEquals(0.0, plan.route().totalKm(), EPS);
        assertTrue(plan.route().withinGuarantee());
    }

    @Test
    void lateOrdersAreCounted() {
        // "now" is 12:00 on 18 Nov, so make these due earlier that same day.
        Order late = new Order("L", "Cust", "9876543213", "Item L", OrderType.DAILY_FOOD, Fulfilment.PICKUP,
                LocalDateTime.of(2026, 11, 18, 8, 0), LocalDateTime.of(2026, 11, 18, 11, 0), null);
        Order notLate = new Order("N", "Cust", "9876543214", "Item N", OrderType.DAILY_FOOD, Fulfilment.PICKUP,
                LocalDateTime.of(2026, 11, 18, 8, 0), LocalDateTime.of(2026, 11, 18, 18, 0), null);
        DispatchPlan plan = planner.plan(List.of(late, notLate), LocalDate.of(2026, 11, 18), NOW, 5, new Random(1));
        assertEquals(1, plan.lateCount());
    }

    @Test
    void mostUrgentListMatchesTheSlowSortingWay() {
        List<Order> orders = new OrderGenerator(LocalityDirectory.loadDefault())
                .generate(400, DAY, 1, 0.0, new Random(5));
        DispatchPlanner full = new DispatchPlanner(LocalityDirectory.loadDefault());
        DispatchPlan plan = full.plan(orders, DAY, NOW, 10, new Random(2));

        List<ScoredOrder> slow = UrgencyRanker.topUrgentBySorting(orders, 10, NOW);
        assertEquals(10, plan.topUrgent().size());
        for (int i = 0; i < 10; i++) {
            assertEquals(slow.get(i).order().id(), plan.topUrgent().get(i).order().id());
        }
    }

    @Test
    void worksOnAWholeWeekOfGeneratedOrdersAndKeepsTheGuarantee() {
        LocalityDirectory directory = LocalityDirectory.loadDefault();
        DispatchPlanner full = new DispatchPlanner(directory);
        List<Order> orders = new OrderGenerator(directory).generate(700, DAY, 7, 0.0, new Random(8));
        for (int d = 0; d < 7; d++) {
            DispatchPlan plan = full.plan(orders, DAY.plusDays(d), NOW, 8, new Random(3));
            RouteSummary route = plan.route();
            assertTrue(route.withinGuarantee(), "day " + d);
            assertTrue(route.exactKm().isPresent(), "17 stops or fewer, so the exact answer is available");
            assertTrue(route.totalKm() >= route.exactKm().getAsDouble() - EPS, "day " + d);
            assertTrue(route.totalKm() <= 2.0 * route.exactKm().getAsDouble() + EPS, "day " + d);
            assertEquals(plan.ordersDue(), plan.deliveryCount() + plan.pickupCount());
        }
    }

    @Test
    void exactAnswerIsSkippedWhenThereAreTooManyStops() {
        StringBuilder csv = new StringBuilder("Kitchen,23.0300,72.5500\n");
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            csv.append("Place").append(i).append(',').append(23.0 + i * 0.002).append(',')
                    .append(72.5 + (i % 5) * 0.01).append('\n');
            orders.add(delivery("D" + i, 9, 12, "Place" + i));
        }
        DispatchPlanner big = new DispatchPlanner(LocalityDirectory.fromCsv(csv.toString()));
        RouteSummary route = big.plan(orders, DAY, NOW, 5, new Random(1)).route();
        assertEquals(26, route.stops().size());
        assertFalse(route.exactKm().isPresent());
        assertTrue(route.withinGuarantee());
    }

    @Test
    void sameInputGivesTheSamePlan() {
        List<Order> orders = List.of(delivery("A", 9, 12, "Alpha"), delivery("B", 10, 13, "Beta"),
                delivery("C", 11, 14, "Gamma"), pickup("P", 10));
        assertEquals(plan(orders), plan(orders));
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> new DispatchPlanner(null));
        assertThrows(IllegalArgumentException.class,
                () -> new DispatchPlanner(LocalityDirectory.fromCsv("Alpha,23.04,72.56")));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(null, DAY, NOW, 5, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(List.of(), null, NOW, 5, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(List.of(), DAY, null, 5, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(List.of(), DAY, NOW, -1, new Random(1)));
        assertThrows(IllegalArgumentException.class, () -> planner.plan(List.of(), DAY, NOW, 5, null));
    }
}
