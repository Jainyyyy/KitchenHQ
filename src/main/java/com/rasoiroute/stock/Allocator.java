package com.rasoiroute.stock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides which orders the current stock can cover.
 *
 * The orders come in a priority order (most urgent first). The allocator walks down the list and gives
 * each order its ingredients if EVERYTHING it needs is still there. If even one ingredient is short, the
 * order is marked "short", keeps nothing, and the next order gets its turn.
 *
 * This is a greedy method. Choosing the best set of orders for several ingredients at once is a hard
 * problem (a multi-dimensional knapsack), so a simple rule that is easy to explain and runs in one pass is
 * the sensible choice. The same method with the booking order instead of the urgency order is the baseline
 * we compare with.
 */
public final class Allocator {

    private static final double EPSILON = 1e-9;

    private Allocator() {
        // static helper only
    }

    /** One order's wish: its id, how important it is, and what it needs. */
    public record Demand(String orderId, int priority, Map<String, Double> requirement) {
    }

    /** What happened to one order. missing is empty when it fits. */
    public record Result(String orderId, boolean fits, Map<String, Double> missing) {
    }

    /** Gives stock to the demands in the order they are listed. The input stock is not changed. */
    public static List<Result> allocate(Map<String, Double> onHand, List<Demand> inPriorityOrder) {
        if (onHand == null || inPriorityOrder == null) {
            throw new IllegalArgumentException("stock and demands are required");
        }
        Map<String, Double> remaining = new LinkedHashMap<>(onHand);
        List<Result> results = new ArrayList<>(inPriorityOrder.size());
        for (Demand demand : inPriorityOrder) {
            Map<String, Double> missing = missingFor(remaining, demand.requirement());
            if (missing.isEmpty()) {
                for (Map.Entry<String, Double> e : demand.requirement().entrySet()) {
                    remaining.merge(e.getKey(), -e.getValue(), Double::sum);
                }
            }
            results.add(new Result(demand.orderId(), missing.isEmpty(), missing));
        }
        return results;
    }

    /** How much of each ingredient is missing for this requirement (empty when everything is there). */
    public static Map<String, Double> missingFor(Map<String, Double> available, Map<String, Double> requirement) {
        Map<String, Double> missing = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : requirement.entrySet()) {
            double have = Math.max(0.0, available.getOrDefault(e.getKey(), 0.0));
            double gap = e.getValue() - have;
            if (gap > EPSILON) {
                missing.put(e.getKey(), gap);
            }
        }
        return missing;
    }

    /** Adds up what several requirements need in total. */
    public static Map<String, Double> total(List<Map<String, Double>> requirements) {
        Map<String, Double> sum = new LinkedHashMap<>();
        for (Map<String, Double> requirement : requirements) {
            for (Map.Entry<String, Double> e : requirement.entrySet()) {
                sum.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        return sum;
    }

    /** The sum of the priorities of the orders that were covered: a simple score for comparing two plans. */
    public static long servedPriority(List<Demand> demands, List<Result> results) {
        long sum = 0;
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).fits()) {
                sum += demands.get(i).priority();
            }
        }
        return sum;
    }

    /** How many whole units of a recipe can be made from the available stock (never negative). */
    public static long maxUnits(Map<String, Double> perUnit, Map<String, Double> available) {
        double fewest = Double.MAX_VALUE;
        for (Map.Entry<String, Double> e : perUnit.entrySet()) {
            double have = Math.max(0.0, available.getOrDefault(e.getKey(), 0.0));
            fewest = Math.min(fewest, have / e.getValue());
        }
        return (long) Math.floor(fewest + EPSILON);
    }
}
