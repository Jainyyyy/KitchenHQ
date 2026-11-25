package com.rasoiroute.stock;

/**
 * One core supply in the kitchen, for example cake flour or whipping cream.
 *
 * It describes the supply itself, not how much is in the kitchen (the amount on the shelf is kept
 * separately, so the same list of supplies can be used with different amounts).
 *
 *   unit      what it is counted in: g, ml or pcs
 *   group     the shelf heading used on the Stock page, for example "Baking" or "Dairy"
 *   packSize  how it is bought (a 1000 g pack), so shopping amounts are rounded up to whole packs
 *   lowLevel  the amount at or below which she wants to reorder
 */
public record Ingredient(String id, String name, String unit, String group, double packSize, double lowLevel) {

    public Ingredient {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Ingredient id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ingredient name is required");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException(name + ": unit is required");
        }
        if (group == null || group.isBlank()) {
            throw new IllegalArgumentException(name + ": group is required");
        }
        if (!(packSize > 0) || Double.isInfinite(packSize)) {
            throw new IllegalArgumentException(name + ": pack size must be more than 0");
        }
        if (!(lowLevel >= 0) || Double.isInfinite(lowLevel)) {
            throw new IllegalArgumentException(name + ": reorder level cannot be negative");
        }
        id = id.trim().toLowerCase();
        name = name.trim();
        unit = unit.trim();
        group = group.trim();
    }
}
