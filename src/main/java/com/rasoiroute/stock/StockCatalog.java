package com.rasoiroute.stock;

import com.rasoiroute.model.OrderType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The fixed lists the stock feature works from: the ingredients, the product families and their recipes,
 * plus the starting stock. They are read from three small CSV files (src/main/resources/stock/), so the
 * kitchen can change the numbers without touching any code.
 *
 * A line starting with # is a comment. The first row of each file is a header and is skipped.
 */
public final class StockCatalog {

    private final Map<String, Ingredient> ingredients;
    private final Map<String, FamilyRecipe> families;
    private final Map<String, Double> startingStock;

    private StockCatalog(Map<String, Ingredient> ingredients, Map<String, FamilyRecipe> families, Map<String, Double> startingStock) {
        this.ingredients = Collections.unmodifiableMap(ingredients);
        this.families = Collections.unmodifiableMap(families);
        this.startingStock = Collections.unmodifiableMap(startingStock);
    }

    /** Reads the sample files that ship inside the project. */
    public static StockCatalog loadDefault() {
        return fromCsv(read("/stock/ingredients.csv"), read("/stock/families.csv"), read("/stock/recipes.csv"));
    }

    public static StockCatalog fromCsv(String ingredientsCsv, String familiesCsv, String recipesCsv) {
        Map<String, Ingredient> ingredients = new LinkedHashMap<>();
        Map<String, Double> stock = new LinkedHashMap<>();
        for (Row row : rows(ingredientsCsv, "ingredients", 7)) {
            Ingredient ingredient = new Ingredient(row.cell(0), row.cell(1), row.cell(2), row.cell(3),
                    row.number(4, "pack size"), row.number(5, "low level"));
            if (ingredients.containsKey(ingredient.id())) {
                throw new IllegalArgumentException("Ingredient listed twice: " + ingredient.id());
            }
            double onHand = row.number(6, "starting stock");
            if (onHand < 0) {
                throw new IllegalArgumentException(ingredient.name() + ": starting stock cannot be negative");
            }
            ingredients.put(ingredient.id(), ingredient);
            stock.put(ingredient.id(), onHand);
        }
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("The ingredients list is empty");
        }

        Map<String, Map<String, Double>> recipeAmounts = new LinkedHashMap<>();
        for (Row row : rows(recipesCsv, "recipes", 3)) {
            String family = row.cell(0).toLowerCase();
            String ingredientId = row.cell(1).toLowerCase();
            if (!ingredients.containsKey(ingredientId)) {
                throw new IllegalArgumentException("Recipe for " + family + " uses an unknown ingredient: " + ingredientId);
            }
            double amount = row.number(2, "amount");
            Map<String, Double> amounts = recipeAmounts.computeIfAbsent(family, k -> new LinkedHashMap<>());
            if (amounts.containsKey(ingredientId)) {
                throw new IllegalArgumentException("Recipe for " + family + " lists " + ingredientId + " twice");
            }
            amounts.put(ingredientId, amount);
        }

        Map<String, FamilyRecipe> families = new LinkedHashMap<>();
        for (Row row : rows(familiesCsv, "families", 6)) {
            String id = row.cell(0).toLowerCase();
            if (families.containsKey(id)) {
                throw new IllegalArgumentException("Family listed twice: " + id);
            }
            Map<String, Double> amounts = recipeAmounts.get(id);
            if (amounts == null) {
                throw new IllegalArgumentException("Family " + id + " has no recipe lines");
            }
            OrderType type;
            try {
                type = OrderType.valueOf(row.cell(2).toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Family " + id + ": type must be BAKERY, CATERING or DAILY_FOOD");
            }
            families.put(id, new FamilyRecipe(id, row.cell(1), type, row.cell(3),
                    (int) row.number(4, "min quantity"), (int) row.number(5, "max quantity"), amounts));
        }
        for (String id : recipeAmounts.keySet()) {
            if (!families.containsKey(id)) {
                throw new IllegalArgumentException("Recipe lines exist for an unknown family: " + id);
            }
        }
        if (families.isEmpty()) {
            throw new IllegalArgumentException("The families list is empty");
        }
        return new StockCatalog(ingredients, families, stock);
    }

    public Map<String, Ingredient> ingredients() {
        return ingredients;
    }

    public Map<String, FamilyRecipe> families() {
        return families;
    }

    /** The stock the kitchen starts with (ingredient id to amount). */
    public Map<String, Double> startingStock() {
        return startingStock;
    }

    public Ingredient ingredient(String id) {
        Ingredient found = id == null ? null : ingredients.get(id.trim().toLowerCase());
        if (found == null) {
            throw new IllegalArgumentException("Unknown ingredient: " + id);
        }
        return found;
    }

    public FamilyRecipe family(String id) {
        FamilyRecipe found = id == null ? null : families.get(id.trim().toLowerCase());
        if (found == null) {
            throw new IllegalArgumentException("Unknown product family: " + id);
        }
        return found;
    }

    public List<FamilyRecipe> familiesOfType(OrderType type) {
        List<FamilyRecipe> list = new ArrayList<>();
        for (FamilyRecipe f : families.values()) {
            if (f.type() == type) {
                list.add(f);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ reading CSV text

    private record Row(int line, String[] cells) {
        String cell(int i) {
            String value = cells[i].trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Line " + line + " has an empty value in column " + (i + 1));
            }
            return value;
        }

        double number(int i, String what) {
            try {
                double value = Double.parseDouble(cell(i));
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new NumberFormatException();
                }
                return value;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Line " + line + ": " + what + " is not a number");
            }
        }
    }

    private static List<Row> rows(String csv, String fileName, int columns) {
        if (csv == null) {
            throw new IllegalArgumentException("The " + fileName + " text is required");
        }
        List<Row> rows = new ArrayList<>();
        String[] lines = csv.split("\\R");
        boolean headerSeen = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (!headerSeen) {
                headerSeen = true;          // the first real line is the header row
                continue;
            }
            String[] cells = line.split(",", -1);
            if (cells.length != columns) {
                throw new IllegalArgumentException("In " + fileName + ", line " + (i + 1) + " must have " + columns + " columns");
            }
            rows.add(new Row(i + 1, cells));
        }
        return rows;
    }

    private static String read(String resource) {
        try (InputStream in = StockCatalog.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(resource + " was not found in the project resources");
            }
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line).append('\n');
                }
            }
            return text.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
    }
}
