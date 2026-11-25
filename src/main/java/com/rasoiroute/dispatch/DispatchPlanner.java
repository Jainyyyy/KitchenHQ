package com.rasoiroute.dispatch;

import com.rasoiroute.geo.DistanceMatrix;
import com.rasoiroute.geo.Location;
import com.rasoiroute.geo.LocalityDirectory;
import com.rasoiroute.model.Fulfilment;
import com.rasoiroute.model.Order;
import com.rasoiroute.route.ApproxTour;
import com.rasoiroute.route.ArrivalOrderTour;
import com.rasoiroute.route.ExactTsp;
import com.rasoiroute.route.NearestNeighbourTour;
import com.rasoiroute.route.Tour;
import com.rasoiroute.route.TourBuilder;
import com.rasoiroute.urgency.ScoredOrder;
import com.rasoiroute.urgency.UrgencyRanker;
import com.rasoiroute.urgency.UrgencyScorer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;

/**
 * Turns a pile of orders into a plan for ONE day:
 *   1. keep only the orders due on that day
 *   2. split them into deliveries and pickups
 *   3. pick the most urgent ones (Quickselect)
 *   4. group deliveries by locality, so each locality is ONE stop
 *   5. build the delivery route (spanning tree + shortcut) and compare it with the baselines
 *
 * An order that cannot be planned (for example, an unknown locality) is reported in
 * "problems" and left out; it never stops the rest of the plan.
 */
public final class DispatchPlanner {

    /** The name of the starting point in the locality list. */
    public static final String KITCHEN = "Kitchen";

    private final LocalityDirectory directory;

    public DispatchPlanner(LocalityDirectory directory) {
        if (directory == null) {
            throw new IllegalArgumentException("locality directory is required");
        }
        if (!directory.contains(KITCHEN)) {
            throw new IllegalArgumentException("The locality list must contain a row called " + KITCHEN);
        }
        this.directory = directory;
    }

    public DispatchPlan plan(List<Order> orders, LocalDate day, LocalDateTime now, int topK, Random random) {
        if (orders == null || day == null || now == null || random == null) {
            throw new IllegalArgumentException("orders, day, current time and random source are required");
        }
        if (topK < 0) {
            throw new IllegalArgumentException("topK must not be negative");
        }

        // 1 and 2: this day's orders, split by delivery / pickup
        List<Order> dayOrders = new ArrayList<>();
        List<Order> deliveries = new ArrayList<>();
        List<Order> pickups = new ArrayList<>();
        for (Order order : orders) {
            if (order.dueAt().toLocalDate().equals(day)) {
                dayOrders.add(order);
                if (order.fulfilment() == Fulfilment.DELIVERY) {
                    deliveries.add(order);
                } else {
                    pickups.add(order);
                }
            }
        }
        pickups.sort(Comparator.comparing(Order::dueAt).thenComparing(Order::id));
        // "arrival order" = the order the bookings were made
        deliveries.sort(Comparator.comparing(Order::bookedAt).thenComparing(Order::id));

        int lateCount = 0;
        for (Order order : dayOrders) {
            if (UrgencyScorer.isLate(order, now)) {
                lateCount++;
            }
        }

        // 3: the most urgent orders of the day
        List<ScoredOrder> topUrgent = UrgencyRanker.topUrgent(dayOrders, topK, now, random);

        // 4: one stop per locality. Stop 0 is the kitchen.
        List<String> problems = new ArrayList<>();
        Location kitchen = directory.find(KITCHEN);
        List<Location> locations = new ArrayList<>();
        locations.add(kitchen);
        Map<String, Integer> stopOfLocality = new LinkedHashMap<>();
        stopOfLocality.put(kitchen.name(), 0);
        Map<Integer, List<Order>> ordersAtStop = new LinkedHashMap<>();

        for (Order order : deliveries) {
            if (!directory.contains(order.locality())) {
                problems.add("Order " + order.id() + ": unknown locality \"" + order.locality()
                        + "\", so it is left out of the route");
                continue;
            }
            Location place = directory.find(order.locality());
            Integer stop = stopOfLocality.get(place.name());
            if (stop == null) {
                stop = locations.size();
                stopOfLocality.put(place.name(), stop);
                locations.add(place);
            }
            ordersAtStop.computeIfAbsent(stop, key -> new ArrayList<>()).add(order);
        }

        // 5: the route
        RouteSummary route = buildRoute(locations, ordersAtStop);

        return new DispatchPlan(day, dayOrders.size(), deliveries.size(), pickups.size(), lateCount,
                topUrgent, List.copyOf(pickups), route, List.copyOf(problems));
    }

    private static RouteSummary buildRoute(List<Location> locations, Map<Integer, List<Order>> ordersAtStop) {
        DistanceMatrix distances = DistanceMatrix.fromLocations(locations);
        int stopCount = locations.size();

        ApproxTour approx = TourBuilder.build(distances, 0);
        Tour nearest = NearestNeighbourTour.solve(distances, 0);
        Tour arrival = ArrivalOrderTour.solve(distances, 0);
        OptionalDouble exact = stopCount <= ExactTsp.MAX_STOPS
                ? OptionalDouble.of(ExactTsp.solve(distances, 0).length())
                : OptionalDouble.empty();

        List<Integer> order = approx.tour().order();
        List<RouteStop> stops = new ArrayList<>(stopCount);
        for (int position = 0; position < order.size(); position++) {
            int stop = order.get(position);
            double legKm = position == 0 ? 0.0 : distances.get(order.get(position - 1), stop);

            List<Order> here = new ArrayList<>(ordersAtStop.getOrDefault(stop, List.of()));
            here.sort(Comparator.comparing(Order::dueAt).thenComparing(Order::id));
            List<String> ids = new ArrayList<>();
            for (Order o : here) {
                ids.add(o.id());
            }

            Location place = locations.get(stop);
            stops.add(new RouteStop(place.name(), place.latitude(), place.longitude(), legKm, ids));
        }
        double returnKm = distances.get(order.get(order.size() - 1), order.get(0));

        return new RouteSummary(List.copyOf(stops), returnKm, approx.tour().length(), approx.mstWeight(),
                nearest.length(), arrival.length(), exact);
    }
}
