package com.rasoiroute.stock;

/** A two-ingredient, one-product kitchen small enough to check every number by hand. */
final class TinyKitchen {

    static final String INGREDIENTS = """
            id,name,unit,group,packSize,lowLevel,onHand
            flour,Flour,g,Baking,1000,500,3000
            cream,Cream,ml,Baking,1000,200,1000
            """;
    static final String FAMILIES = """
            family,label,type,unit,minQty,maxQty
            cake,Cake,BAKERY,kg,1,3
            """;
    static final String RECIPES = """
            family,ingredientId,amountPerUnit
            cake,flour,300
            cake,cream,250
            """;

    private TinyKitchen() {
    }

    static StockCatalog catalog() {
        return StockCatalog.fromCsv(INGREDIENTS, FAMILIES, RECIPES);
    }
}
