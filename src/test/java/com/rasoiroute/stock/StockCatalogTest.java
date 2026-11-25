package com.rasoiroute.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.model.OrderType;
import org.junit.jupiter.api.Test;

class StockCatalogTest {

    @Test
    void theShippedSampleFilesLoad() {
        StockCatalog c = StockCatalog.loadDefault();
        assertEquals(16, c.ingredients().size());
        assertEquals(7, c.families().size());
        assertEquals(8000.0, c.startingStock().get("cake-flour"), 1e-9);
    }

    @Test
    void everyRecipeInTheSampleFilesUsesAKnownIngredient() {
        StockCatalog c = StockCatalog.loadDefault();
        for (FamilyRecipe f : c.families().values()) {
            for (String id : f.perUnit().keySet()) {
                assertTrue(c.ingredients().containsKey(id), f.id() + " uses unknown " + id);
            }
        }
    }

    @Test
    void familiesCanBeFoundByType() {
        assertEquals(3, StockCatalog.loadDefault().familiesOfType(OrderType.BAKERY).size());
        assertEquals(1, StockCatalog.loadDefault().familiesOfType(OrderType.DAILY_FOOD).size());
    }

    @Test
    void lookupsIgnoreCapitalLetters() {
        assertEquals("Flour", TinyKitchen.catalog().ingredient("FLOUR").name());
        assertEquals("Cake", TinyKitchen.catalog().family(" Cake ").label());
    }

    @Test
    void unknownNamesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> TinyKitchen.catalog().ingredient("saffron"));
        assertThrows(IllegalArgumentException.class, () -> TinyKitchen.catalog().family("pizza"));
    }

    @Test
    void aRecipeWithAnUnknownIngredientIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockCatalog.fromCsv(TinyKitchen.INGREDIENTS, TinyKitchen.FAMILIES,
                TinyKitchen.RECIPES + "cake,saffron,1\n"));
    }

    @Test
    void aFamilyWithoutARecipeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockCatalog.fromCsv(TinyKitchen.INGREDIENTS,
                TinyKitchen.FAMILIES + "pie,Pie,BAKERY,kg,1,2\n", TinyKitchen.RECIPES));
    }

    @Test
    void aDuplicateIngredientIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockCatalog.fromCsv(TinyKitchen.INGREDIENTS + "flour,Flour,g,Baking,1000,500,1\n",
                TinyKitchen.FAMILIES, TinyKitchen.RECIPES));
    }

    @Test
    void aWrongNumberOfColumnsNamesTheLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> StockCatalog.fromCsv(
                "id,name,unit,group,packSize,lowLevel,onHand\nflour,Flour,g\n", TinyKitchen.FAMILIES, TinyKitchen.RECIPES));
        assertTrue(e.getMessage().contains("line 2"));
    }

    @Test
    void negativeStartingStockAndNonNumbersAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockCatalog.fromCsv(
                TinyKitchen.INGREDIENTS.replace(",3000", ",-5"), TinyKitchen.FAMILIES, TinyKitchen.RECIPES));
        assertThrows(IllegalArgumentException.class, () -> StockCatalog.fromCsv(
                TinyKitchen.INGREDIENTS.replace(",3000", ",lots"), TinyKitchen.FAMILIES, TinyKitchen.RECIPES));
    }

    @Test
    void anEmptyIngredientListIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockCatalog.fromCsv("id,name,unit,group,packSize,lowLevel,onHand\n",
                TinyKitchen.FAMILIES, TinyKitchen.RECIPES));
    }

    @Test
    void ingredientAndRecipeRulesRejectBadNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new Ingredient("x", "X", "g", "G", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Ingredient("x", "X", "g", "G", 1, -1));
        FamilyRecipe f = TinyKitchen.catalog().family("cake");
        assertThrows(IllegalArgumentException.class, () -> f.requirementFor(0));
        assertEquals(600.0, f.requirementFor(2).get("flour"), 1e-9);
    }
}
