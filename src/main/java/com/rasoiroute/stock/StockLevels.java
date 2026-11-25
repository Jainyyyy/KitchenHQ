package com.rasoiroute.stock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns the numbers into plain traffic-light statuses, and builds the shopping list.
 *
 * "committed" is what the orders still to be made will use. "available" is what is left after that
 * (on hand minus committed); it can be negative, which means the booked orders need more than we have.
 */
public final class StockLevels {

    public enum Level { OK, LOW, SHORT, OUT }

    private StockLevels() {
        // static helper only
    }

    /**
     * OUT   nothing on the shelf
     * SHORT the booked orders need more than is on the shelf (available below zero)
     * LOW   on the shelf or after the orders, the amount is at or below the reorder level
     * OK    everything fine
     */
    public static Level level(Ingredient ingredient, double onHand, double committed) {
        double available = onHand - committed;
        if (onHand <= 0) {
            return Level.OUT;
        }
        if (available < -1e-9) {
            return Level.SHORT;
        }
        if (onHand <= ingredient.lowLevel() || available <= ingredient.lowLevel()) {
            return Level.LOW;
        }
        return Level.OK;
    }

    /** One line of the shopping list. */
    public record ShoppingLine(Ingredient ingredient, double onHand, double committed, double toBuy, long packs, double buyAmount, String reason) {
    }

    /**
     * What to buy so that, after all the booked orders are made, at least the reorder level is left.
     * The amount is rounded UP to whole packs, because that is how things are bought.
     */
    public static List<ShoppingLine> shoppingList(Map<String, Ingredient> ingredients, Map<String, Double> onHand, Map<String, Double> committed) {
        List<ShoppingLine> list = new ArrayList<>();
        for (Ingredient ingredient : ingredients.values()) {
            double have = onHand.getOrDefault(ingredient.id(), 0.0);
            double needed = committed.getOrDefault(ingredient.id(), 0.0);
            double toBuy = needed + ingredient.lowLevel() - have;
            if (toBuy <= 1e-9) {
                continue;
            }
            long packs = (long) Math.ceil(toBuy / ingredient.packSize() - 1e-9);
            String reason = needed - have > 1e-9 ? "Booked orders need more than we have" : "Will fall to the reorder level";
            list.add(new ShoppingLine(ingredient, have, needed, toBuy, packs, packs * ingredient.packSize(), reason));
        }
        return list;
    }
}
