package com.rasoiroute.urgency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.model.Order;
import com.rasoiroute.model.OrderType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class UrgencyRankerTest {

    private static final LocalDateTime NOW = UrgencyScorerTest.NOW;

    private static Order order(String id, OrderType type, LocalDateTime due) {
        return UrgencyScorerTest.order(id, type, due);
    }

    private static List<String> ids(List<ScoredOrder> ranked) {
        List<String> ids = new ArrayList<>();
        for (ScoredOrder s : ranked) {
            ids.add(s.order().id());
        }
        return ids;
    }

    private static List<Order> workedExample() {
        return List.of(
                order("A", OrderType.DAILY_FOOD, NOW.plusMinutes(30)),
                order("B", OrderType.BAKERY, NOW.plusMinutes(90)),
                order("C", OrderType.CATERING, NOW.plusMinutes(150)),
                order("D", OrderType.DAILY_FOOD, NOW.plusMinutes(180)),
                order("E", OrderType.BAKERY, NOW.plusMinutes(15)));
    }

    @Test
    void topTwoOfTheWorkedExampleAreEThenA() {
        List<ScoredOrder> top = UrgencyRanker.topUrgent(workedExample(), 2, NOW, new Random(1));
        assertEquals(List.of("E", "A"), ids(top));
        assertEquals(145, top.get(0).score());
        assertEquals(110, top.get(1).score());
    }

    @Test
    void topThreeAddsB() {
        assertEquals(List.of("E", "A", "B"), ids(UrgencyRanker.topUrgent(workedExample(), 3, NOW, new Random(1))));
    }

    @Test
    void equalScoresAreBrokenByEarlierDueTime() {
        // Both are bakery orders due beyond the 2-hour window, so both score 40.
        Order later = order("a-later", OrderType.BAKERY, NOW.plusHours(4));
        Order sooner = order("z-sooner", OrderType.BAKERY, NOW.plusHours(3));
        List<ScoredOrder> top = UrgencyRanker.topUrgent(List.of(later, sooner), 2, NOW, new Random(1));
        assertEquals(List.of("z-sooner", "a-later"), ids(top));
    }

    @Test
    void equalScoreAndDueTimeAreBrokenByOrderId() {
        LocalDateTime due = NOW.plusHours(3);
        Order b = order("b", OrderType.BAKERY, due);
        Order a = order("a", OrderType.BAKERY, due);
        List<ScoredOrder> top = UrgencyRanker.topUrgent(List.of(b, a), 2, NOW, new Random(1));
        assertEquals(List.of("a", "b"), ids(top));
    }

    @Test
    void edgeCase_kBiggerThanTheNumberOfOrdersReturnsAllOfThem() {
        assertEquals(List.of("E", "A", "B", "C", "D"),
                ids(UrgencyRanker.topUrgent(workedExample(), 50, NOW, new Random(1))));
    }

    @Test
    void edgeCase_kZeroAndEmptyListGiveEmptyResults() {
        assertTrue(UrgencyRanker.topUrgent(workedExample(), 0, NOW, new Random(1)).isEmpty());
        assertTrue(UrgencyRanker.topUrgent(new ArrayList<Order>(), 3, NOW, new Random(1)).isEmpty());
    }

    @Test
    void lateOrdersAreMarkedAndRankFirst() {
        Order late = order("late", OrderType.DAILY_FOOD, NOW.minusMinutes(20));
        Order onTime = order("ok", OrderType.BAKERY, NOW.plusMinutes(60));
        List<ScoredOrder> top = UrgencyRanker.topUrgent(List.of(onTime, late), 2, NOW, new Random(1));
        assertEquals("late", top.get(0).order().id());
        assertTrue(top.get(0).late());
        assertEquals(-20, top.get(0).minutesLeft());
    }

    @Test
    void quickselectAnswerAlwaysMatchesTheFullSortAnswer() {
        Random data = new Random(99);
        OrderType[] types = OrderType.values();
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            OrderType type = types[data.nextInt(types.length)];
            LocalDateTime due = NOW.plusMinutes(data.nextInt(400) - 30);   // some already late
            orders.add(order("o" + i, type, due));
        }
        for (int k : new int[] {1, 5, 50, 250, 499, 500, 600}) {
            List<String> fast = ids(UrgencyRanker.topUrgent(orders, k, NOW, new Random(k)));
            List<String> slow = ids(UrgencyRanker.topUrgentBySorting(orders, k, NOW));
            assertEquals(slow, fast, "k=" + k);
        }
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> UrgencyRanker.topUrgent(null, 2, NOW, new Random()));
        assertThrows(IllegalArgumentException.class, () -> UrgencyRanker.topUrgent(workedExample(), 2, null, new Random()));
        assertThrows(IllegalArgumentException.class, () -> UrgencyRanker.topUrgent(workedExample(), -1, NOW, new Random()));
        assertThrows(IllegalArgumentException.class, () -> UrgencyRanker.topUrgentBySorting(workedExample(), -1, NOW));
    }
}
