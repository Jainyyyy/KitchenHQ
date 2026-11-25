package com.rasoiroute.urgency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.model.Fulfilment;
import com.rasoiroute.model.Order;
import com.rasoiroute.model.OrderType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UrgencyScorerTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 12, 0);
    static final LocalDateTime BOOKED = LocalDateTime.of(2026, 11, 17, 9, 0);

    static Order order(String id, OrderType type, LocalDateTime due) {
        return new Order(id, "Customer " + id, "9876543210", "Item " + id, type,
                Fulfilment.DELIVERY, BOOKED, due, "Satellite");
    }

    @Test
    void workedExampleFromTheDesign() {
        // These are the five orders we worked out by hand: A=110, B=70, C=60, D=20, E=145.
        assertEquals(110, UrgencyScorer.score(order("A", OrderType.DAILY_FOOD, NOW.plusMinutes(30)), NOW));
        assertEquals(70, UrgencyScorer.score(order("B", OrderType.BAKERY, NOW.plusMinutes(90)), NOW));
        assertEquals(60, UrgencyScorer.score(order("C", OrderType.CATERING, NOW.plusMinutes(150)), NOW));
        assertEquals(20, UrgencyScorer.score(order("D", OrderType.DAILY_FOOD, NOW.plusMinutes(180)), NOW));
        assertEquals(145, UrgencyScorer.score(order("E", OrderType.BAKERY, NOW.plusMinutes(15)), NOW));
    }

    @Test
    void orderBookedForNextWeekGetsOnlyTypePoints() {
        Order cateringNextWeek = order("X", OrderType.CATERING, NOW.plusDays(7));
        assertEquals(60, UrgencyScorer.score(cateringNextWeek, NOW));
    }

    @Test
    void timePointsStartExactlyAtTheEdgeOfTheWindow() {
        assertEquals(0, UrgencyScorer.timePoints(120));
        assertEquals(1, UrgencyScorer.timePoints(119));
        assertEquals(0, UrgencyScorer.timePoints(500));
    }

    @Test
    void edgeCase_dueRightNowGetsFullTimePointsAndIsNotLate() {
        Order dueNow = order("N", OrderType.DAILY_FOOD, NOW);
        assertEquals(0, UrgencyScorer.minutesLeft(dueNow, NOW));
        assertEquals(20 + 120, UrgencyScorer.score(dueNow, NOW));
        assertFalse(UrgencyScorer.isLate(dueNow, NOW));
    }

    @Test
    void overdueOrderIsLateAndTimePointsStopAtTheMaximum() {
        Order overdue = order("L", OrderType.BAKERY, NOW.minusMinutes(10));
        assertEquals(-10, UrgencyScorer.minutesLeft(overdue, NOW));
        assertTrue(UrgencyScorer.isLate(overdue, NOW));
        assertEquals(40 + 120, UrgencyScorer.score(overdue, NOW));
        // Even very overdue orders do not get more than the maximum.
        assertEquals(120, UrgencyScorer.timePoints(-5000));
    }

    @Test
    void typePointsRankCateringAboveBakeryAboveDailyFood() {
        LocalDateTime due = NOW.plusHours(5);
        int catering = UrgencyScorer.score(order("1", OrderType.CATERING, due), NOW);
        int bakery = UrgencyScorer.score(order("2", OrderType.BAKERY, due), NOW);
        int daily = UrgencyScorer.score(order("3", OrderType.DAILY_FOOD, due), NOW);
        assertTrue(catering > bakery && bakery > daily);
    }

    @Test
    void missingInputsAreRejected() {
        Order o = order("1", OrderType.BAKERY, NOW.plusHours(1));
        assertThrows(IllegalArgumentException.class, () -> UrgencyScorer.score(null, NOW));
        assertThrows(IllegalArgumentException.class, () -> UrgencyScorer.score(o, null));
        assertThrows(IllegalArgumentException.class, () -> UrgencyScorer.minutesLeft(null, NOW));
        assertThrows(IllegalArgumentException.class, () -> UrgencyScorer.isLate(o, null));
    }
}
