package com.rasoiroute.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class ApiServiceTest {

    // The pretend "current time" is 12:00 on Wed 18 Nov 2026 (India time).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T06:30:00Z"), ZoneId.of("Asia/Kolkata"));

    private final ApiService api = new ApiService(LocalityDirectory.loadDefault(), new OrderBook(10_000, 0.01), CLOCK);

    // ---------- helpers ----------

    /** Calls the API and also checks that the answer can always be turned into valid JSON text. */
    private ApiResponse call(String method, String path, String body, String... queryPairs) {
        Map<String, String> query = new LinkedHashMap<>();
        for (int i = 0; i + 1 < queryPairs.length; i += 2) {
            query.put(queryPairs[i], queryPairs[i + 1]);
        }
        ApiResponse response = api.handle(method, path, query, body);
        Json.parse(Json.write(response.body()));
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

    private static String orderJson(String customer, String phone, String item, String type, String fulfilment,
                                    String locality, String dueAt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerName", customer);
        m.put("phone", phone);
        m.put("item", item);
        m.put("type", type);
        m.put("fulfilment", fulfilment);
        m.put("locality", locality);
        m.put("dueAt", dueAt);
        return Json.write(m);
    }

    private static String cake(String phone) {
        return orderJson("Meera", phone, "Chocolate cake 1kg", "BAKERY", "DELIVERY", "Satellite", "2026-11-19T16:00");
    }

    private String errorOf(ApiResponse response) {
        assertEquals(400, response.status(), "expected a 400 but got " + response.status() + " " + response.body());
        return (String) map(response.body()).get("error");
    }

    // ---------- tests ----------

    @Test
    void healthSaysOkAndCountsOrders() {
        ApiResponse response = call("GET", "/api/health", null);
        assertEquals(200, response.status());
        assertEquals("ok", map(response.body()).get("status"));
        assertEquals(0, map(response.body()).get("orders"));
    }

    @Test
    void localitiesListsTheKitchenSeparatelyAndSortsThePlaces() {
        Map<String, Object> body = map(call("GET", "/api/localities", null).body());
        assertEquals("Kitchen", map(body.get("kitchen")).get("name"));
        List<Object> places = list(body.get("localities"));
        assertEquals(16, places.size());
        String previous = "";
        for (Object p : places) {
            String name = (String) map(p).get("name");
            assertFalse(name.equals("Kitchen"));
            assertTrue(name.compareTo(previous) >= 0, name + " is out of order");
            previous = name;
        }
    }

    @Test
    void savesAnOrderAndGivesItAnId() {
        ApiResponse response = call("POST", "/api/orders", cake("9876543210"));
        assertEquals(201, response.status());
        Map<String, Object> body = map(response.body());
        assertEquals(true, body.get("accepted"));
        assertEquals("W-00001", map(body.get("order")).get("id"));
        assertEquals("9876543210", map(body.get("order")).get("phone"));
        assertEquals("2026-11-18T12:00", map(body.get("order")).get("bookedAt"));   // booked "now" by default
    }

    @Test
    void aRepeatIsRefusedWith409AndPointsToTheFirstOrder() {
        call("POST", "/api/orders", cake("9876543210"));
        ApiResponse again = call("POST", "/api/orders", cake("+91 98765 43210"));
        assertEquals(409, again.status());
        Map<String, Object> body = map(again.body());
        assertEquals(false, body.get("accepted"));
        assertEquals("W-00001", body.get("duplicateOfId"));
        assertTrue(((String) body.get("message")).contains("W-00001"));
    }

    @Test
    void reusingAnIdIsRefusedWith409ButIsNotCalledARepeat() {
        String withId = "{\"id\":\"X1\",\"customerName\":\"A\",\"phone\":\"9876543210\",\"item\":\"Cake\","
                + "\"type\":\"BAKERY\",\"fulfilment\":\"PICKUP\",\"dueAt\":\"2026-11-19T16:00\"}";
        String sameIdOtherOrder = "{\"id\":\"X1\",\"customerName\":\"B\",\"phone\":\"9123456780\",\"item\":\"Cookies\","
                + "\"type\":\"BAKERY\",\"fulfilment\":\"PICKUP\",\"dueAt\":\"2026-11-19T17:00\"}";
        assertEquals(201, call("POST", "/api/orders", withId).status());
        ApiResponse second = call("POST", "/api/orders", sameIdOtherOrder);
        assertEquals(409, second.status());
        assertNull(map(second.body()).get("duplicateOfId"));
    }

    @Test
    void missingFieldsAreNamedInTheError() {
        assertEquals("item is required", errorOf(call("POST", "/api/orders",
                orderJson("Meera", "9876543210", "", "BAKERY", "DELIVERY", "Satellite", "2026-11-19T16:00"))));
        assertEquals("customerName is required", errorOf(call("POST", "/api/orders", "{}")));
        assertEquals("dueAt is required", errorOf(call("POST", "/api/orders",
                "{\"customerName\":\"A\",\"phone\":\"9876543210\",\"item\":\"Cake\",\"type\":\"BAKERY\",\"fulfilment\":\"PICKUP\"}")));
    }

    @Test
    void anUnknownLocalityIsRefusedWithAHelpfulMessage() {
        String message = errorOf(call("POST", "/api/orders",
                orderJson("Meera", "9876543210", "Cake", "BAKERY", "DELIVERY", "Mars", "2026-11-19T16:00")));
        assertTrue(message.contains("Mars"));
    }

    @Test
    void aDeliveryWithoutALocalityIsRefused() {
        assertEquals("locality is required for a delivery", errorOf(call("POST", "/api/orders",
                orderJson("Meera", "9876543210", "Cake", "BAKERY", "DELIVERY", null, "2026-11-19T16:00"))));
    }

    @Test
    void aPickupIgnoresAnyLocalityGiven() {
        ApiResponse response = call("POST", "/api/orders",
                orderJson("Meera", "9876543210", "Cake", "BAKERY", "PICKUP", "Mars", "2026-11-19T16:00"));
        assertEquals(201, response.status());
        assertNull(map(map(response.body()).get("order")).get("locality"));
    }

    @Test
    void anUnknownTypeListsTheAllowedOnes() {
        String message = errorOf(call("POST", "/api/orders",
                orderJson("Meera", "9876543210", "Cake", "PIZZA", "DELIVERY", "Satellite", "2026-11-19T16:00")));
        assertTrue(message.contains("CATERING") && message.contains("BAKERY") && message.contains("DAILY_FOOD"));
    }

    @Test
    void typeAndFulfilmentAreAcceptedInAnyCase() {
        assertEquals(201, call("POST", "/api/orders",
                orderJson("Meera", "9876543210", "Cake", "daily food", "pickup", null, "2026-11-19T16:00")).status());
    }

    @Test
    void aBadDateIsRefused() {
        assertTrue(errorOf(call("POST", "/api/orders",
                orderJson("Meera", "9876543210", "Cake", "BAKERY", "DELIVERY", "Satellite", "tomorrow evening")))
                .contains("dueAt"));
    }

    @Test
    void aDueTimeBeforeTheBookingTimeIsRefused() {
        // "now" is 18 Nov 12:00 and booked-on defaults to now, so a due time on the 17th is impossible.
        assertTrue(errorOf(call("POST", "/api/orders",
                orderJson("Meera", "9876543210", "Cake", "BAKERY", "DELIVERY", "Satellite", "2026-11-17T10:00")))
                .toLowerCase().contains("due"));
    }

    @Test
    void aBadPhoneIsRefused() {
        assertFalse(errorOf(call("POST", "/api/orders",
                orderJson("Meera", "12345", "Cake", "BAKERY", "DELIVERY", "Satellite", "2026-11-19T16:00"))).isBlank());
        assertEquals("phone must be text", errorOf(call("POST", "/api/orders",
                "{\"customerName\":\"A\",\"phone\":9876543210,\"item\":\"Cake\",\"type\":\"BAKERY\","
                        + "\"fulfilment\":\"PICKUP\",\"dueAt\":\"2026-11-19T16:00\"}")));
    }

    @Test
    void textThatIsNotJsonIsRefused() {
        assertTrue(errorOf(call("POST", "/api/orders", "this is not json")).startsWith("Invalid JSON"));
        assertTrue(errorOf(call("POST", "/api/orders", "")).startsWith("Invalid JSON"));
        assertTrue(errorOf(call("POST", "/api/orders", "[1,2,3]")).contains("JSON object"));
    }

    @Test
    void ordersAreListedMostUrgentFirstWithTheirNumbers() {
        // Catering far ahead (60 points), bakery due in 90 minutes (70), daily food due in 30 minutes (110).
        call("POST", "/api/orders", orderJson("A", "9000000001", "Party meal", "CATERING", "PICKUP", null, "2026-11-25T12:00"));
        call("POST", "/api/orders", orderJson("B", "9000000002", "Cake", "BAKERY", "PICKUP", null, "2026-11-18T13:30"));
        call("POST", "/api/orders", orderJson("C", "9000000003", "Meal box", "DAILY_FOOD", "PICKUP", null, "2026-11-18T12:30"));

        Map<String, Object> body = map(call("GET", "/api/orders", null).body());
        assertEquals(3, body.get("count"));
        List<Object> orders = list(body.get("orders"));
        assertEquals(110, map(orders.get(0)).get("score"));
        assertEquals(70, map(orders.get(1)).get("score"));
        assertEquals(60, map(orders.get(2)).get("score"));
        assertEquals(30L, map(orders.get(0)).get("minutesLeft"));
        assertEquals(false, map(orders.get(0)).get("late"));
    }

    @Test
    void theNowValueChangesTheUrgencyNumbers() {
        call("POST", "/api/orders", orderJson("C", "9000000003", "Meal box", "DAILY_FOOD", "PICKUP", null, "2026-11-18T12:30"));
        Map<String, Object> later = map(call("GET", "/api/orders", null, "now", "2026-11-18T13:00").body());
        Map<String, Object> first = map(list(later.get("orders")).get(0));
        assertEquals(true, first.get("late"));
        assertEquals(140, first.get("score"));   // 20 type points + 120 for being overdue
    }

    @Test
    void theOrderListCanBeFilteredToOneDay() {
        call("POST", "/api/orders", orderJson("A", "9000000001", "Cake", "BAKERY", "PICKUP", null, "2026-11-19T10:00"));
        call("POST", "/api/orders", orderJson("B", "9000000002", "Cake", "BAKERY", "PICKUP", null, "2026-11-20T10:00"));
        assertEquals(1, map(call("GET", "/api/orders", null, "date", "2026-11-20").body()).get("count"));
        assertTrue(errorOf(call("GET", "/api/orders", null, "date", "20/11/2026")).contains("date"));
    }

    @Test
    void deletingClearsEverything() {
        call("POST", "/api/orders", cake("9876543210"));
        assertEquals(200, call("DELETE", "/api/orders", null).status());
        assertEquals(0, map(call("GET", "/api/orders", null).body()).get("count"));
        assertEquals(201, call("POST", "/api/orders", cake("9876543210")).status());   // accepted again
    }

    @Test
    void demoDataLoadsAndCountsTheRepeatsItBlocked() {
        Map<String, Object> body = map(call("POST", "/api/demo/generate", null,
                "count", "400", "days", "7", "dupRate", "0.25", "seed", "7").body());
        assertEquals(400, body.get("generated"));
        int accepted = (Integer) body.get("accepted");
        int repeats = (Integer) body.get("repeatsBlocked");
        assertEquals(400, accepted + repeats + (Integer) body.get("otherRefused"));
        assertTrue(repeats > 50, "expected a fair number of repeats but got " + repeats);
        assertEquals(0, body.get("otherRefused"));
        assertEquals(accepted, map(call("GET", "/api/health", null).body()).get("orders"));
    }

    @Test
    void demoDataWithTheSameSeedGivesTheSameCounts() {
        ApiResponse first = call("POST", "/api/demo/generate", null, "count", "200", "seed", "5");
        ApiResponse second = call("POST", "/api/demo/generate", null, "count", "200", "seed", "5");
        assertEquals(first.body(), second.body());
    }

    @Test
    void demoDataRefusesSillyNumbers() {
        assertTrue(errorOf(call("POST", "/api/demo/generate", null, "count", "0")).contains("count"));
        assertTrue(errorOf(call("POST", "/api/demo/generate", null, "count", "999999")).contains("count"));
        assertTrue(errorOf(call("POST", "/api/demo/generate", null, "count", "ten")).contains("whole number"));
        assertTrue(errorOf(call("POST", "/api/demo/generate", null, "days", "0")).contains("days"));
        assertTrue(errorOf(call("POST", "/api/demo/generate", null, "dupRate", "1.5")).contains("dupRate"));
        assertTrue(errorOf(call("POST", "/api/demo/generate", null, "dupRate", "NaN")).contains("dupRate"));
    }

    @Test
    void thePlanForADayHasARouteThatKeepsTheGuarantee() {
        call("POST", "/api/demo/generate", null, "count", "300", "days", "3", "dupRate", "0", "seed", "11");
        Map<String, Object> plan = map(call("GET", "/api/plan", null, "date", "2026-11-19", "k", "6").body());

        assertEquals("2026-11-19", plan.get("day"));
        int due = (Integer) plan.get("ordersDue");
        int deliveries = (Integer) plan.get("deliveryCount");
        int pickups = (Integer) plan.get("pickupCount");
        assertEquals(due, deliveries + pickups);
        assertEquals(6, list(plan.get("topUrgent")).size());

        Map<String, Object> route = map(plan.get("route"));
        List<Object> stops = list(route.get("stops"));
        assertEquals("Kitchen", map(stops.get(0)).get("locality"));
        assertEquals(true, route.get("withinGuarantee"));
        double total = (Double) route.get("totalKm");
        double exact = (Double) route.get("exactKm");
        assertTrue(total >= exact - 0.002 && total <= 2 * exact + 0.002, "total " + total + " exact " + exact);
        assertTrue((Double) route.get("arrivalOrderKm") >= exact - 0.002);
    }

    @Test
    void thePlanDefaultsToTodayAndFiveUrgentOrders() {
        call("POST", "/api/demo/generate", null, "count", "300", "days", "1", "dupRate", "0", "seed", "3");
        Map<String, Object> plan = map(call("GET", "/api/plan", null).body());
        assertEquals("2026-11-18", plan.get("day"));
        assertTrue(list(plan.get("topUrgent")).size() <= 5);
    }

    @Test
    void planRefusesBadValues() {
        assertTrue(errorOf(call("GET", "/api/plan", null, "date", "nope")).contains("date"));
        assertTrue(errorOf(call("GET", "/api/plan", null, "k", "-1")).contains("k"));
        assertTrue(errorOf(call("GET", "/api/plan", null, "k", "101")).contains("k"));
        assertTrue(errorOf(call("GET", "/api/plan", null, "now", "soon")).contains("now"));
    }

    @Test
    void unknownPathsAndWrongMethodsGetClearAnswers() {
        assertEquals(404, call("GET", "/api/nothing", null).status());
        assertEquals(405, call("POST", "/api/health", "{}").status());
        assertEquals(405, call("PUT", "/api/orders", "{}").status());
        assertEquals(405, call("GET", "/api/demo/generate", null).status());
    }

    @Test
    void constructorRejectsMissingParts() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ApiService(null, new OrderBook(10, 0.01), CLOCK));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ApiService(LocalityDirectory.loadDefault(), null, CLOCK));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ApiService(LocalityDirectory.loadDefault(), new OrderBook(10, 0.01), null));
    }
}
