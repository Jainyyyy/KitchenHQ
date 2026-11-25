package com.rasoiroute.stock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Puts the three things together: the orders already booked, the rough recipe of each product family,
 * and what is on the shelf. Pure logic with no web code, so it can be tested exactly.
 *
 * The method, in plain words:
 *   1. Every open order needs (recipe per unit) x (quantity). Adding these up per ingredient (one pass,
 *      a hash map) gives "committed".
 *   2. available = on hand - committed. Below zero means the booked orders need more than we have.
 *   3. The orders are then handed the stock one by one, most urgent first (greedy, see Allocator). Any
 *      order that cannot be fully covered is "at risk" and the missing amounts are listed.
 *   4. For a NEW order the same arithmetic gives before and after amounts, and the number of
 *      further units that can still be made (the smallest of have/needed over its ingredients).
 */
public final class StockPlanner {

    private StockPlanner() {
        // static helper only
    }

    /** One open order's stock need. A bigger priority means more urgent (it is the urgency score). */
    public record Need(String orderId, int priority, LocalDateTime dueAt, String familyId, double quantity) {
    }

    public record Row(Ingredient ingredient, double onHand, double committed, double available, StockLevels.Level level) {
    }

    public record AtRisk(String orderId, Map<String, Double> missing) {
    }

    public record Report(List<Row> rows, List<StockLevels.ShoppingLine> shopping, List<AtRisk> atRisk,
                         int okCount, int lowCount, int shortCount, int outCount,
                         long urgentFirstServed, long bookingOrderServed) {
    }

    public record CheckLine(Ingredient ingredient, double use, double availableBefore, double availableAfter) {
    }

    public record Check(boolean possible, List<CheckLine> lines, long unitsStillPossible, String limitedBy) {
    }

    // ------------------------------------------------------------------ the whole picture

    public static Report report(StockCatalog catalog, Map<String, Double> onHand, List<Need> needs) {
        if (catalog == null || onHand == null || needs == null) {
            throw new IllegalArgumentException("catalog, stock and needs are required");
        }
        List<Allocator.Demand> demands = demands(catalog, needs);
        List<Map<String, Double>> all = new ArrayList<>();
        for (Allocator.Demand d : demands) {
            all.add(d.requirement());
        }
        Map<String, Double> committed = Allocator.total(all);

        List<Row> rows = new ArrayList<>();
        int ok = 0;
        int low = 0;
        int shortCount = 0;
        int out = 0;
        for (Ingredient ingredient : catalog.ingredients().values()) {
            double have = onHand.getOrDefault(ingredient.id(), 0.0);
            double need = committed.getOrDefault(ingredient.id(), 0.0);
            StockLevels.Level level = StockLevels.level(ingredient, have, need);
            rows.add(new Row(ingredient, have, need, have - need, level));
            switch (level) {
                case OK -> ok++;
                case LOW -> low++;
                case SHORT -> shortCount++;
                case OUT -> out++;
            }
        }
        List<StockLevels.ShoppingLine> shopping = StockLevels.shoppingList(catalog.ingredients(), onHand, committed);

        // Most urgent first (the method we propose) against booking order (the baseline).
        List<Allocator.Demand> byUrgency = sortedMostUrgentFirst(demands, needs);
        List<Allocator.Result> urgentResults = Allocator.allocate(onHand, byUrgency);
        List<Allocator.Result> bookingResults = Allocator.allocate(onHand, demands);

        List<AtRisk> atRisk = new ArrayList<>();
        for (Allocator.Result r : urgentResults) {
            if (!r.fits()) {
                atRisk.add(new AtRisk(r.orderId(), r.missing()));
            }
        }
        return new Report(rows, shopping, atRisk, ok, low, shortCount, out,
                Allocator.servedPriority(byUrgency, urgentResults), Allocator.servedPriority(demands, bookingResults));
    }

    // ------------------------------------------------------------------ "can we take this order?"

    public static Check check(StockCatalog catalog, Map<String, Double> onHand, List<Need> openNeeds, String familyId, double quantity) {
        FamilyRecipe family = catalog.family(familyId);
        Map<String, Double> request = family.requirementFor(quantity);   // rejects quantity <= 0

        List<Map<String, Double>> all = new ArrayList<>();
        for (Allocator.Demand d : demands(catalog, openNeeds)) {
            all.add(d.requirement());
        }
        Map<String, Double> committed = Allocator.total(all);

        Map<String, Double> available = new LinkedHashMap<>();
        for (Ingredient ingredient : catalog.ingredients().values()) {
            available.put(ingredient.id(), onHand.getOrDefault(ingredient.id(), 0.0) - committed.getOrDefault(ingredient.id(), 0.0));
        }

        List<CheckLine> lines = new ArrayList<>();
        for (Map.Entry<String, Double> e : request.entrySet()) {
            double before = available.get(e.getKey());
            lines.add(new CheckLine(catalog.ingredient(e.getKey()), e.getValue(), before, before - e.getValue()));
        }
        boolean possible = Allocator.missingFor(available, request).isEmpty();

        // How many more units of this family could be made from what is free right now.
        long units = Allocator.maxUnits(family.perUnit(), available);
        String limitedBy = null;
        double smallest = Double.MAX_VALUE;
        for (Map.Entry<String, Double> e : family.perUnit().entrySet()) {
            double ratio = Math.max(0.0, available.get(e.getKey())) / e.getValue();
            if (ratio < smallest) {
                smallest = ratio;
                limitedBy = catalog.ingredient(e.getKey()).name();
            }
        }
        return new Check(possible, lines, units, limitedBy);
    }

    // ------------------------------------------------------------------ helpers

    private static List<Allocator.Demand> demands(StockCatalog catalog, List<Need> needs) {
        List<Allocator.Demand> list = new ArrayList<>();
        for (Need need : needs) {
            list.add(new Allocator.Demand(need.orderId(), need.priority(),
                    catalog.family(need.familyId()).requirementFor(need.quantity())));
        }
        return list;
    }

    /** Higher priority first; the earlier due time wins a tie; then the order id, so the result is always the same. */
    private static List<Allocator.Demand> sortedMostUrgentFirst(List<Allocator.Demand> demands, List<Need> needs) {
        Map<String, LocalDateTime> due = new LinkedHashMap<>();
        for (Need need : needs) {
            due.put(need.orderId(), need.dueAt());
        }
        List<Allocator.Demand> sorted = new ArrayList<>(demands);
        sorted.sort(Comparator.comparingInt(Allocator.Demand::priority).reversed()
                .thenComparing(d -> due.get(d.orderId()))
                .thenComparing(Allocator.Demand::orderId));
        return sorted;
    }
}
