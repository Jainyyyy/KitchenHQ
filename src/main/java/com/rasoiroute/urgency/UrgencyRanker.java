package com.rasoiroute.urgency;

import com.rasoiroute.algo.Quickselect;
import com.rasoiroute.model.Order;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Picks the most urgent orders.
 *
 * "Most urgent first" means:
 *   1. higher urgency score first
 *   2. if scores are equal, the earlier due time first
 *   3. if still equal, the smaller order id first (so the answer is always the same)
 */
public final class UrgencyRanker {

    public static final Comparator<ScoredOrder> MOST_URGENT_FIRST =
            Comparator.comparingInt(ScoredOrder::score).reversed()
                    .thenComparing(s -> s.order().dueAt())
                    .thenComparing(s -> s.order().id());

    private UrgencyRanker() {
        // static helper only
    }

    /**
     * The k most urgent orders, most urgent first, found with randomized Quickselect.
     * Only the k winners are sorted at the end; the rest are never put in order.
     */
    public static List<ScoredOrder> topUrgent(List<Order> orders, int k, LocalDateTime now, Random rnd) {
        List<ScoredOrder> scored = scoreAll(orders, now);
        List<ScoredOrder> winners = Quickselect.firstK(scored, k, MOST_URGENT_FIRST, rnd);
        winners.sort(MOST_URGENT_FIRST);
        return winners;
    }

    /**
     * The same answer, found the slow way: sort everything, then keep the first k.
     * Used as the baseline in tests and in the benchmark.
     */
    public static List<ScoredOrder> topUrgentBySorting(List<Order> orders, int k, LocalDateTime now) {
        if (k < 0) {
            throw new IllegalArgumentException("k must not be negative");
        }
        List<ScoredOrder> scored = scoreAll(orders, now);
        scored.sort(MOST_URGENT_FIRST);
        return new ArrayList<>(scored.subList(0, Math.min(k, scored.size())));
    }

    /** Scores every order. The result is in the same order as the input. */
    public static List<ScoredOrder> scoreAll(List<Order> orders, LocalDateTime now) {
        if (orders == null || now == null) {
            throw new IllegalArgumentException("orders and current time are required");
        }
        List<ScoredOrder> scored = new ArrayList<>(orders.size());
        for (Order order : orders) {
            scored.add(new ScoredOrder(
                    order,
                    UrgencyScorer.score(order, now),
                    UrgencyScorer.minutesLeft(order, now),
                    UrgencyScorer.isLate(order, now)));
        }
        return scored;
    }
}
