package com.rasoiroute.stock;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The amounts on the kitchen shelf right now (ingredient id to amount). Like the OrderBook, its methods
 * are "synchronized" so that two requests at the same moment take turns.
 */
public final class StockBook {

    private final StockCatalog catalog;
    private final Map<String, Double> onHand = new LinkedHashMap<>();

    public StockBook(StockCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog is required");
        }
        this.catalog = catalog;
        reset();
    }

    public StockCatalog catalog() {
        return catalog;
    }

    /** A copy of the amounts, so callers cannot change the shelf by accident. */
    public synchronized Map<String, Double> snapshot() {
        return new LinkedHashMap<>(onHand);
    }

    /** Adds (positive) or removes (negative) an amount and returns the new amount. Never goes below zero. */
    public synchronized double adjust(String ingredientId, double change) {
        Ingredient ingredient = catalog.ingredient(ingredientId);
        if (Double.isNaN(change) || Double.isInfinite(change)) {
            throw new IllegalArgumentException("The amount must be a number");
        }
        double have = onHand.get(ingredient.id());
        double updated = have + change;
        if (updated < -1e-9) {
            throw new IllegalArgumentException("Only " + show(have) + " " + ingredient.unit() + " of "
                    + ingredient.name() + " is left, so " + show(-change) + " " + ingredient.unit() + " cannot be used");
        }
        updated = Math.max(0.0, updated);
        onHand.put(ingredient.id(), updated);
        return updated;
    }

    /** Sets an amount after counting the shelf. */
    public synchronized double set(String ingredientId, double amount) {
        Ingredient ingredient = catalog.ingredient(ingredientId);
        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount < 0) {
            throw new IllegalArgumentException("The amount in stock cannot be negative");
        }
        onHand.put(ingredient.id(), amount);
        return amount;
    }

    /**
     * Takes out what a finished order used. If the shelf has less than the rough estimate says, that
     * ingredient simply goes to zero: a real kitchen does not stop cooking over an estimate.
     */
    public synchronized void use(Map<String, Double> requirement) {
        if (requirement == null) {
            throw new IllegalArgumentException("requirement is required");
        }
        for (Map.Entry<String, Double> e : requirement.entrySet()) {
            Ingredient ingredient = catalog.ingredient(e.getKey());
            double have = onHand.get(ingredient.id());
            onHand.put(ingredient.id(), Math.max(0.0, have - e.getValue()));
        }
    }

    /** Goes back to the starting amounts (handy before a demo). */
    public synchronized void reset() {
        onHand.clear();
        onHand.putAll(catalog.startingStock());
    }

    private static String show(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100.0) / 100.0);
    }
}
