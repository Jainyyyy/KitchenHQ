package com.rasoiroute.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StockBookTest {

    private final StockBook book = new StockBook(TinyKitchen.catalog());

    @Test
    void startsWithTheStartingAmounts() {
        assertEquals(3000.0, book.snapshot().get("flour"), 1e-9);
        assertEquals(1000.0, book.snapshot().get("cream"), 1e-9);
    }

    @Test
    void boughtMoreIncreasesTheAmount() {
        assertEquals(4000.0, book.adjust("flour", 1000), 1e-9);
    }

    @Test
    void usedSomeDecreasesTheAmount() {
        assertEquals(2500.0, book.adjust("flour", -500), 1e-9);
    }

    @Test
    void usingMoreThanIsLeftIsRefusedAndNothingChanges() {
        assertThrows(IllegalArgumentException.class, () -> book.adjust("cream", -1001));
        assertEquals(1000.0, book.snapshot().get("cream"), 1e-9);
    }

    @Test
    void usingExactlyEverythingLeavesZero() {
        assertEquals(0.0, book.adjust("cream", -1000), 1e-9);
    }

    @Test
    void anUnknownIngredientIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> book.adjust("saffron", 1));
        assertThrows(IllegalArgumentException.class, () -> book.adjust(null, 1));
    }

    @Test
    void notANumberIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> book.adjust("flour", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> book.set("flour", Double.POSITIVE_INFINITY));
    }

    @Test
    void settingACountedAmountWorksButNotANegativeOne() {
        assertEquals(1234.0, book.set("flour", 1234), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> book.set("flour", -1));
    }

    @Test
    void finishingAnOrderTakesItsAmountsOutAndNeverGoesBelowZero() {
        book.use(Map.of("flour", 600.0, "cream", 5000.0));
        assertEquals(2400.0, book.snapshot().get("flour"), 1e-9);
        assertEquals(0.0, book.snapshot().get("cream"), 1e-9);
    }

    @Test
    void theSnapshotIsACopy() {
        book.snapshot().put("flour", 0.0);
        assertEquals(3000.0, book.snapshot().get("flour"), 1e-9);
    }

    @Test
    void resetGoesBackToTheStart() {
        book.adjust("flour", -2000);
        book.reset();
        assertEquals(3000.0, book.snapshot().get("flour"), 1e-9);
    }
}
