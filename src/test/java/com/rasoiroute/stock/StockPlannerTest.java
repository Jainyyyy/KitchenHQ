package com.rasoiroute.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StockPlannerTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 11, 23, 9, 0);
    private static final LocalDateTime FIVE = LocalDateTime.of(2026, 11, 23, 17, 0);

    private final StockCatalog catalog = TinyKitchen.catalog();
    private final Map<String, Double> shelf = new StockBook(catalog).snapshot();     // flour 3000, cream 1000

    private static StockPlanner.Need need(String id, int priority, LocalDateTime due, double kg) {
        return new StockPlanner.Need(id, priority, due, "cake", kg);
    }

    private static StockPlanner.Row row(StockPlanner.Report r, String id) {
        return r.rows().stream().filter(x -> x.ingredient().id().equals(id)).findFirst().orElseThrow();
    }

    // ---- report

    @Test
    void withNoOrdersNothingIsCommittedAndTheShelfIsJustReported() {
        StockPlanner.Report r = StockPlanner.report(catalog, shelf, List.of());
        assertEquals(0.0, row(r, "flour").committed(), 1e-9);
        assertEquals(StockLevels.Level.OK, row(r, "flour").level());
        assertTrue(r.atRisk().isEmpty());
        assertTrue(r.shopping().isEmpty());
    }

    @Test
    void orderNeedsAreAddedUpPerIngredient() {
        StockPlanner.Report r = StockPlanner.report(catalog, shelf, List.of(need("A", 100, NINE, 2), need("B", 50, FIVE, 3)));
        assertEquals(1500.0, row(r, "flour").committed(), 1e-9);     // (2 + 3) kg x 300 g
        assertEquals(1250.0, row(r, "cream").committed(), 1e-9);     // (2 + 3) kg x 250 ml
        assertEquals(-250.0, row(r, "cream").available(), 1e-9);
    }

    @Test
    void anIngredientTheBookedOrdersOverspendIsShortAndGoesOnTheShoppingList() {
        StockPlanner.Report r = StockPlanner.report(catalog, shelf, List.of(need("A", 100, NINE, 2), need("B", 50, FIVE, 3)));
        assertEquals(StockLevels.Level.SHORT, row(r, "cream").level());
        assertEquals(1, r.shortCount());
        assertEquals(1, r.okCount());
        assertEquals(1, r.shopping().size());
        assertEquals("cream", r.shopping().get(0).ingredient().id());
        assertEquals(1000.0, r.shopping().get(0).buyAmount(), 1e-9);      // 1250 + 200 - 1000 = 450 -> one 1000 ml pack
    }

    @Test
    void theOrderThatCannotBeCoveredIsListedAsAtRiskWithWhatIsMissing() {
        StockPlanner.Report r = StockPlanner.report(catalog, shelf, List.of(need("A", 100, NINE, 2), need("B", 50, FIVE, 3)));
        assertEquals(1, r.atRisk().size());
        assertEquals("B", r.atRisk().get(0).orderId());
        assertEquals(250.0, r.atRisk().get(0).missing().get("cream"), 1e-9);    // B needs 750, 500 is left after A
    }

    @Test
    void servingTheMostUrgentOrderFirstProtectsMorePriorityThanBookingOrder() {
        // The less urgent order was booked first. Booking order wastes the cream on it.
        StockPlanner.Report r = StockPlanner.report(catalog, shelf, List.of(need("B", 50, FIVE, 3), need("A", 100, NINE, 2)));
        assertEquals(100, r.urgentFirstServed());
        assertEquals(50, r.bookingOrderServed());
        assertEquals("B", r.atRisk().get(0).orderId());
    }

    @Test
    void urgencyTiesAreBrokenByTheEarlierDueTimeThenTheId() {
        // Same priority, only enough for one: the one due earlier is served.
        StockPlanner.Report r = StockPlanner.report(catalog, shelf, List.of(need("LATE", 70, FIVE, 3), need("EARLY", 70, NINE, 3)));
        assertEquals(1, r.atRisk().size());
        assertEquals("LATE", r.atRisk().get(0).orderId());
    }

    @Test
    void lowStockWithEnoughForTheOrdersIsLowNotShort() {
        Map<String, Double> low = Map.of("flour", 700.0, "cream", 1000.0);
        StockPlanner.Report r = StockPlanner.report(catalog, low, List.of(need("A", 100, NINE, 1)));
        assertEquals(StockLevels.Level.LOW, row(r, "flour").level());     // 700 on hand, 400 left after the order
        assertTrue(r.atRisk().isEmpty());
    }

    @Test
    void anOrderForAnUnknownProductOrWithZeroQuantityIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockPlanner.report(catalog, shelf,
                List.of(new StockPlanner.Need("X", 1, NINE, "pizza", 1))));
        assertThrows(IllegalArgumentException.class, () -> StockPlanner.report(catalog, shelf, List.of(need("X", 1, NINE, 0))));
    }

    @Test
    void nullInputsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> StockPlanner.report(null, shelf, List.of()));
        assertThrows(IllegalArgumentException.class, () -> StockPlanner.report(catalog, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> StockPlanner.report(catalog, shelf, null));
    }

    // ---- check a new order

    @Test
    void aNewOrderThatFitsIsPossibleAndShowsBeforeAndAfter() {
        StockPlanner.Check c = StockPlanner.check(catalog, shelf, List.of(need("A", 100, NINE, 2)), "cake", 1);
        assertTrue(c.possible());
        StockPlanner.CheckLine cream = c.lines().stream().filter(l -> l.ingredient().id().equals("cream")).findFirst().orElseThrow();
        assertEquals(500.0, cream.availableBefore(), 1e-9);       // 1000 - 2 kg x 250
        assertEquals(250.0, cream.availableAfter(), 1e-9);
    }

    @Test
    void aNewOrderThatNeedsMoreThanIsFreeIsNotPossible() {
        assertFalse(StockPlanner.check(catalog, shelf, List.of(need("A", 100, NINE, 2)), "cake", 3).possible());
    }

    @Test
    void checkCountsTheOrdersAlreadyBooked() {
        assertTrue(StockPlanner.check(catalog, shelf, List.of(), "cake", 3).possible());
        assertFalse(StockPlanner.check(catalog, shelf, List.of(need("A", 100, NINE, 2)), "cake", 3).possible());
    }

    @Test
    void checkSaysHowManyMoreCanStillBeMadeAndWhatLimitsIt() {
        StockPlanner.Check c = StockPlanner.check(catalog, shelf, List.of(need("A", 100, NINE, 2)), "cake", 1);
        assertEquals(2, c.unitsStillPossible());         // cream: 500 / 250; flour would allow 8
        assertEquals("Cream", c.limitedBy());
    }

    @Test
    void whenNothingIsFreeZeroMoreCanBeMade() {
        StockPlanner.Check c = StockPlanner.check(catalog, shelf, List.of(need("A", 100, NINE, 4), need("B", 90, NINE, 1)), "cake", 1);
        assertFalse(c.possible());
        assertEquals(0, c.unitsStillPossible());
    }

    @Test
    void checkRefusesAnUnknownProductAndAZeroQuantity() {
        assertThrows(IllegalArgumentException.class, () -> StockPlanner.check(catalog, shelf, List.of(), "pizza", 1));
        assertThrows(IllegalArgumentException.class, () -> StockPlanner.check(catalog, shelf, List.of(), "cake", 0));
    }

    @Test
    void theSampleKitchenCanBeReportedWithoutErrors() {
        StockCatalog sample = StockCatalog.loadDefault();
        StockPlanner.Report r = StockPlanner.report(sample, new StockBook(sample).snapshot(), List.of(
                new StockPlanner.Need("1", 90, NINE, "choc-cake", 2), new StockPlanner.Need("2", 80, FIVE, "veg-lunch", 60)));
        assertEquals(16, r.rows().size());
    }
}
