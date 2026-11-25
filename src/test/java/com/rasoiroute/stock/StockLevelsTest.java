package com.rasoiroute.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockLevelsTest {

    private final Ingredient flour = new Ingredient("flour", "Flour", "g", "Baking", 1000, 500);

    @Test
    void plentyIsOk() {
        assertEquals(StockLevels.Level.OK, StockLevels.level(flour, 3000, 1000));
    }

    @Test
    void atOrBelowTheReorderLevelIsLow() {
        assertEquals(StockLevels.Level.LOW, StockLevels.level(flour, 500, 0));
        assertEquals(StockLevels.Level.LOW, StockLevels.level(flour, 2000, 1500));   // 500 left after the orders
    }

    @Test
    void bookedOrdersNeedingMoreThanWeHaveIsShort() {
        assertEquals(StockLevels.Level.SHORT, StockLevels.level(flour, 1000, 1001));
    }

    @Test
    void anEmptyShelfIsOutEvenWithNoOrders() {
        assertEquals(StockLevels.Level.OUT, StockLevels.level(flour, 0, 0));
    }

    @Test
    void shoppingListSkipsWhatIsFine() {
        assertTrue(StockLevels.shoppingList(Map.of("flour", flour), Map.of("flour", 3000.0), Map.of("flour", 1500.0)).isEmpty());
    }

    @Test
    void shoppingAmountIsRoundedUpToWholePacksAndKeepsTheReorderLevel() {
        // needs 1500 + keep 500 - have 1200 = 800 to buy -> one 1000 g pack
        List<StockLevels.ShoppingLine> list = StockLevels.shoppingList(Map.of("flour", flour), Map.of("flour", 1200.0), Map.of("flour", 1500.0));
        assertEquals(1, list.size());
        assertEquals(800.0, list.get(0).toBuy(), 1e-9);
        assertEquals(1, list.get(0).packs());
        assertEquals(1000.0, list.get(0).buyAmount(), 1e-9);
    }

    @Test
    void justOverAPackBuysTwo() {
        List<StockLevels.ShoppingLine> list = StockLevels.shoppingList(Map.of("flour", flour), Map.of("flour", 0.0), Map.of("flour", 1001.0));
        assertEquals(2, list.get(0).packs());
    }

    @Test
    void theReasonSaysWhetherTheOrdersCannotBeCovered() {
        StockLevels.ShoppingLine short1 = StockLevels.shoppingList(Map.of("flour", flour), Map.of("flour", 100.0), Map.of("flour", 400.0)).get(0);
        StockLevels.ShoppingLine low1 = StockLevels.shoppingList(Map.of("flour", flour), Map.of("flour", 600.0), Map.of("flour", 400.0)).get(0);
        assertTrue(short1.reason().contains("need more"));
        assertTrue(low1.reason().contains("reorder level"));
    }

    @Test
    void anIngredientMissingFromTheShelfIsTreatedAsZero() {
        assertEquals(1, StockLevels.shoppingList(Map.of("flour", flour), Map.of(), Map.of()).size());   // 0 on hand, 500 to keep
    }
}
