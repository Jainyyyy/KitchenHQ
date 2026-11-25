package com.rasoiroute.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.dispatch.OrderBook;
import com.rasoiroute.geo.LocalityDirectory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The stock endpoints, tested through ApiService directly (no network). Uses the sample ingredient files. */
class ApiServiceStockTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

    private final ApiService api = new ApiService(LocalityDirectory.loadDefault(), new OrderBook(10_000, 0.01), CLOCK);

    private ApiResponse call(String method, String path, String body, String... queryPairs) {
        Map<String, String> query = new LinkedHashMap<>();
        for (int i = 0; i + 1 < queryPairs.length; i += 2) {
            query.put(queryPairs[i], queryPairs[i + 1]);
        }
        ApiResponse response = api.handle(method, path, query, body);
        Json.parse(Json.write(response.body()));          // the answer must always be valid JSON
        return response;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return (List<Object>) o;
    }

    private static String order(String phone, String family, Object quantity, String dueAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerName", "Meera");
        m.put("phone", phone);
        m.put("item", "Chocolate cake");
        m.put("type", "BAKERY");
        m.put("fulfilment", "PICKUP");
        m.put("dueAt", dueAt);
        if (family != null) {
            m.put("family", family);
        }
        if (quantity != null) {
            m.put("quantity", quantity);
        }
        return Json.write(m);
    }

    private static String json(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], pairs[i + 1]);
        }
        return Json.write(m);
    }

    private Map<String, Object> report(String... query) {
        ApiResponse r = call("GET", "/api/stock", null, query);
        assertEquals(200, r.status());
        return map(r.body());
    }

    private static Map<String, Object> rowOf(Map<String, Object> report, String id) {
        for (Object o : list(report.get("rows"))) {
            if (id.equals(map(o).get("id"))) {
                return map(o);
            }
        }
        throw new AssertionError("no row for " + id);
    }

    private static double num(Object o) {
        return ((Number) o).doubleValue();
    }

    // ---------- the report ----------

    @Test
    void anEmptyKitchenReportsEveryIngredientAsFine() {
        Map<String, Object> r = report();
        assertEquals(16, list(r.get("rows")).size());
        assertEquals(16.0, num(map(r.get("counts")).get("ok")), 1e-9);
        assertEquals(0.0, num(r.get("linkedOrders")), 1e-9);
        assertEquals(7, list(r.get("families")).size());
        assertTrue(list(r.get("shopping")).isEmpty());
    }

    @Test
    void anOrderWithAProductAndQuantityIsCountedAgainstTheShelf() {
        assertEquals(201, call("POST", "/api/orders", order("9876500001", "choc-cake", 2, "2026-11-19T16:00")).status());
        Map<String, Object> r = report();
        assertEquals(1.0, num(r.get("linkedOrders")), 1e-9);
        assertEquals(500.0, num(rowOf(r, "whipping-cream").get("committed")), 1e-9);     // 2 kg x 250 ml
        assertEquals(3500.0, num(rowOf(r, "whipping-cream").get("available")), 1e-9);
    }

    @Test
    void anOrderWithoutAProductIsSavedButNotCounted() {
        assertEquals(201, call("POST", "/api/orders", order("9876500002", null, null, "2026-11-19T16:00")).status());
        assertEquals(0.0, num(report().get("linkedOrders")), 1e-9);
    }

    @Test
    void aBigOrderMakesAnIngredientShortAndFillsTheShoppingListAndAtRiskList() {
        assertEquals(201, call("POST", "/api/orders", order("9876500003", "choc-cake", 20, "2026-11-19T16:00")).status());
        Map<String, Object> r = report();
        assertEquals("SHORT", rowOf(r, "whipping-cream").get("level"));
        assertTrue(num(map(r.get("counts")).get("short")) >= 1);
        boolean creamListed = false;
        for (Object o : list(r.get("shopping"))) {
            creamListed |= "whipping-cream".equals(map(o).get("id"));
        }
        assertTrue(creamListed);
        assertEquals(1, list(r.get("atRisk")).size());
        assertTrue(map(list(r.get("atRisk")).get(0)).get("missing") != null);
    }

    @Test
    void ordersDueBeyondTheWindowAreNotCounted() {
        call("POST", "/api/orders", order("9876500004", "choc-cake", 2, "2026-11-28T16:00"));
        assertEquals(0.0, num(report("days", "3").get("linkedOrders")), 1e-9);
        assertEquals(1.0, num(report("days", "30").get("linkedOrders")), 1e-9);
    }

    @Test
    void anOverdueOrderThatIsNotMadeStillCounts() {
        call("POST", "/api/orders", order("9876500005", "choc-cake", 2, "2026-11-18T13:00"));
        assertEquals(1.0, num(report("now", "2026-11-18T18:00").get("linkedOrders")), 1e-9);
    }

    @Test
    void theWindowSettingsAreValidated() {
        assertEquals(400, call("GET", "/api/stock", null, "days", "0").status());
        assertEquals(400, call("GET", "/api/stock", null, "days", "abc").status());
        assertEquals(400, call("GET", "/api/stock", null, "date", "tomorrow").status());
    }

    @Test
    void theStockPageOnlyAnswersGet() {
        assertEquals(405, call("POST", "/api/stock", "{}").status());
    }

    // ---------- saving orders with a product ----------

    @Test
    void familyAndQuantityMustComeTogether() {
        assertEquals(400, call("POST", "/api/orders", order("9876500006", "choc-cake", null, "2026-11-19T16:00")).status());
        assertEquals(400, call("POST", "/api/orders", order("9876500006", null, 2, "2026-11-19T16:00")).status());
    }

    @Test
    void anUnknownProductOrABadQuantityIsRefusedAndTheOrderIsNotSaved() {
        assertEquals(400, call("POST", "/api/orders", order("9876500007", "pizza", 2, "2026-11-19T16:00")).status());
        assertEquals(400, call("POST", "/api/orders", order("9876500007", "choc-cake", 0, "2026-11-19T16:00")).status());
        assertEquals(400, call("POST", "/api/orders", order("9876500007", "choc-cake", -3, "2026-11-19T16:00")).status());
        assertEquals(400, call("POST", "/api/orders", order("9876500007", "choc-cake", "two", "2026-11-19T16:00")).status());
        assertEquals(400, call("POST", "/api/orders", order("9876500007", "choc-cake", 100000, "2026-11-19T16:00")).status());
        assertEquals(0.0, num(map(call("GET", "/api/health", null).body()).get("orders")), 1e-9);
    }

    @Test
    void aRepeatedOrderIsRefusedAndDoesNotCountTwice() {
        call("POST", "/api/orders", order("9876500008", "choc-cake", 2, "2026-11-19T16:00"));
        assertEquals(409, call("POST", "/api/orders", order("9876500008", "choc-cake", 2, "2026-11-19T18:00")).status());
        assertEquals(500.0, num(rowOf(report(), "whipping-cream").get("committed")), 1e-9);
    }

    // ---------- "can we make this?" ----------

    @Test
    void aSmallOrderIsPossible() {
        ApiResponse r = call("POST", "/api/orders/check", json("family", "choc-cake", "quantity", 2));
        assertEquals(200, r.status());
        assertEquals(Boolean.TRUE, map(r.body()).get("possible"));
        assertTrue(((String) map(r.body()).get("message")).startsWith("Yes"));
    }

    @Test
    void anOrderBiggerThanTheShelfIsNotPossibleAndTheMessageNamesWhatIsShort() {
        ApiResponse r = call("POST", "/api/orders/check", json("family", "choc-cake", "quantity", 20));
        assertEquals(Boolean.FALSE, map(r.body()).get("possible"));
        assertTrue(((String) map(r.body()).get("message")).contains("Whipping cream by 1 L"));
    }

    @Test
    void theCheckCountsOrdersAlreadyBookedWhenevertheyAreDue() {
        call("POST", "/api/orders", order("9876500009", "choc-cake", 14, "2026-12-30T16:00"));
        ApiResponse r = call("POST", "/api/orders/check", json("family", "choc-cake", "quantity", 4));
        assertEquals(Boolean.FALSE, map(r.body()).get("possible"));     // 14 + 4 kg needs 4500 ml cream, 4000 on the shelf
    }

    @Test
    void theCheckSaysHowManyMoreUnitsCanStillBeMade() {
        ApiResponse r = call("POST", "/api/orders/check", json("family", "choc-cake", "quantity", 1));
        assertTrue(num(map(r.body()).get("unitsStillPossible")) >= 10);
        assertTrue(map(r.body()).get("limitedBy") != null);
    }

    @Test
    void theCheckRefusesBadInput() {
        assertEquals(400, call("POST", "/api/orders/check", json("family", "pizza", "quantity", 1)).status());
        assertEquals(400, call("POST", "/api/orders/check", json("family", "choc-cake", "quantity", 0)).status());
        assertEquals(400, call("POST", "/api/orders/check", json("family", "choc-cake")).status());
        assertEquals(400, call("POST", "/api/orders/check", "[1,2]").status());
        assertEquals(400, call("POST", "/api/orders/check", "not json").status());
        assertEquals(405, call("GET", "/api/orders/check", null).status());
    }

    // ---------- bought more / used some / counted ----------

    @Test
    void boughtMoreAddsToTheShelf() {
        ApiResponse r = call("POST", "/api/stock/adjust", json("ingredientId", "cake-flour", "change", 1000));
        assertEquals(200, r.status());
        assertEquals(9000.0, num(map(r.body()).get("onHand")), 1e-9);
    }

    @Test
    void usedSomeTakesFromTheShelfButNotMoreThanIsThere() {
        assertEquals(7500.0, num(map(call("POST", "/api/stock/adjust", json("ingredientId", "cake-flour", "change", -500)).body()).get("onHand")), 1e-9);
        assertEquals(400, call("POST", "/api/stock/adjust", json("ingredientId", "cake-flour", "change", -99999)).status());
        assertEquals(7500.0, num(rowOf(report(), "cake-flour").get("onHand")), 1e-9);
    }

    @Test
    void aCountedAmountCanBeSet() {
        assertEquals(1234.0, num(map(call("POST", "/api/stock/adjust", json("ingredientId", "sugar", "set", 1234)).body()).get("onHand")), 1e-9);
        assertEquals(400, call("POST", "/api/stock/adjust", json("ingredientId", "sugar", "set", -1)).status());
    }

    @Test
    void adjustRefusesBadInput() {
        assertEquals(400, call("POST", "/api/stock/adjust", json("ingredientId", "sugar")).status());
        assertEquals(400, call("POST", "/api/stock/adjust", json("ingredientId", "sugar", "change", 1, "set", 2)).status());
        assertEquals(400, call("POST", "/api/stock/adjust", json("ingredientId", "saffron", "change", 1)).status());
        assertEquals(400, call("POST", "/api/stock/adjust", json("ingredientId", "sugar", "change", "lots")).status());
        assertEquals(400, call("POST", "/api/stock/adjust", json("change", 1)).status());
    }

    @Test
    void resetGoesBackToTheStartingAmounts() {
        call("POST", "/api/stock/adjust", json("ingredientId", "sugar", "change", -9000));
        assertEquals(200, call("POST", "/api/stock/reset", "{}").status());
        assertEquals(10000.0, num(rowOf(report(), "sugar").get("onHand")), 1e-9);
    }

    // ---------- marking an order made ----------

    @Test
    void makingAnOrderTakesItsIngredientsOffTheShelfAndStopsItBeingCommitted() {
        ApiResponse created = call("POST", "/api/orders", order("9876500010", "choc-cake", 2, "2026-11-19T16:00"));
        String id = (String) map(map(created.body()).get("order")).get("id");
        ApiResponse made = call("POST", "/api/orders/made", json("id", id));
        assertEquals(200, made.status());
        Map<String, Object> r = report();
        assertEquals(3500.0, num(rowOf(r, "whipping-cream").get("onHand")), 1e-9);       // 4000 - 2 x 250
        assertEquals(0.0, num(rowOf(r, "whipping-cream").get("committed")), 1e-9);
        assertEquals(0.0, num(r.get("linkedOrders")), 1e-9);
    }

    @Test
    void anOrderCanOnlyBeMadeOnce() {
        ApiResponse created = call("POST", "/api/orders", order("9876500011", "choc-cake", 2, "2026-11-19T16:00"));
        String id = (String) map(map(created.body()).get("order")).get("id");
        assertEquals(200, call("POST", "/api/orders/made", json("id", id)).status());
        assertEquals(409, call("POST", "/api/orders/made", json("id", id)).status());
        assertEquals(3500.0, num(rowOf(report(), "whipping-cream").get("onHand")), 1e-9);     // taken out only once
    }

    @Test
    void anOrderWithoutAProductOrAnUnknownIdCannotBeMade() {
        ApiResponse created = call("POST", "/api/orders", order("9876500012", null, null, "2026-11-19T16:00"));
        String id = (String) map(map(created.body()).get("order")).get("id");
        assertEquals(400, call("POST", "/api/orders/made", json("id", id)).status());
        assertEquals(400, call("POST", "/api/orders/made", json("id", "W-99999")).status());
        assertEquals(400, call("POST", "/api/orders/made", "{}").status());
    }

    // ---------- clearing ----------

    @Test
    void clearingAllOrdersAlsoForgetsTheirProducts() {
        call("POST", "/api/orders", order("9876500013", "choc-cake", 2, "2026-11-19T16:00"));
        assertEquals(200, call("DELETE", "/api/orders", null).status());
        assertEquals(0.0, num(report().get("linkedOrders")), 1e-9);
        assertFalse(call("POST", "/api/orders", order("9876500013", "choc-cake", 2, "2026-11-19T16:00")).status() == 409);
    }

    @Test
    void loadingDemoOrdersForgetsEarlierProducts() {
        call("POST", "/api/orders", order("9876500014", "choc-cake", 2, "2026-11-19T16:00"));
        assertEquals(200, call("POST", "/api/demo/generate", null, "count", "20", "seed", "1").status());
        assertEquals(0.0, num(report().get("linkedOrders")), 1e-9);
    }

    // ---------- the order list shows the product ----------

    @Test
    void theOrderListShowsTheProductQuantityAndWhetherItIsMade() {
        ApiResponse created = call("POST", "/api/orders", order("9876500015", "choc-cake", 2, "2026-11-19T16:00"));
        String id = (String) map(map(created.body()).get("order")).get("id");
        call("POST", "/api/orders", order("9876500016", null, null, "2026-11-19T17:00"));

        List<Object> orders = list(map(call("GET", "/api/orders", null).body()).get("orders"));
        assertEquals(2, orders.size());
        Map<String, Object> linked = null;
        Map<String, Object> plain = null;
        for (Object o : orders) {
            if (id.equals(map(o).get("id"))) {
                linked = map(o);
            } else {
                plain = map(o);
            }
        }
        assertEquals("Chocolate cake", linked.get("family"));
        assertEquals(2.0, num(linked.get("quantity")), 1e-9);
        assertEquals(Boolean.FALSE, linked.get("made"));
        assertFalse(plain.containsKey("family"));

        call("POST", "/api/orders/made", json("id", id));
        for (Object o : list(map(call("GET", "/api/orders", null).body()).get("orders"))) {
            if (id.equals(map(o).get("id"))) {
                assertEquals(Boolean.TRUE, map(o).get("made"));
            }
        }
    }
}
