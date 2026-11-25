package com.rasoiroute.stock;

import com.rasoiroute.model.OrderType;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A "family" of similar products with one rough recipe, for example "Chocolate cake, per kg".
 *
 * perUnit says how much of each ingredient ONE unit needs (one kg, one piece, one plate or one box).
 * An order for 2 kg therefore needs twice the amounts. minQty and maxQty are only used when the
 * demo makes up sample orders.
 */
public record FamilyRecipe(String id, String label, OrderType type, String unit,
                           int minQty, int maxQty, Map<String, Double> perUnit) {

    public FamilyRecipe {
        if (id == null || id.isBlank() || label == null || label.isBlank() || unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("A family needs an id, a label and a unit");
        }
        if (type == null) {
            throw new IllegalArgumentException(label + ": order type is required");
        }
        if (minQty < 1 || maxQty < minQty) {
            throw new IllegalArgumentException(label + ": quantities must be 1 or more, and max must not be below min");
        }
        if (perUnit == null || perUnit.isEmpty()) {
            throw new IllegalArgumentException(label + ": the recipe needs at least one ingredient");
        }
        for (Map.Entry<String, Double> e : perUnit.entrySet()) {
            if (!(e.getValue() > 0) || Double.isInfinite(e.getValue())) {
                throw new IllegalArgumentException(label + ": amount of " + e.getKey() + " must be more than 0");
            }
        }
        id = id.trim().toLowerCase();
        label = label.trim();
        unit = unit.trim();
        perUnit = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(perUnit));
    }

    /** Everything an order of this many units needs. */
    public Map<String, Double> requirementFor(double quantity) {
        if (!(quantity > 0) || Double.isInfinite(quantity)) {
            throw new IllegalArgumentException("Quantity must be more than 0");
        }
        Map<String, Double> need = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : perUnit.entrySet()) {
            need.put(e.getKey(), e.getValue() * quantity);
        }
        return need;
    }
}
