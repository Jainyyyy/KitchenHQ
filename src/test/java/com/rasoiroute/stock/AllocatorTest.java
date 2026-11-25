package com.rasoiroute.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AllocatorTest {

    private static Map<String, Double> map(Object... pairs) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], ((Number) pairs[i + 1]).doubleValue());
        }
        return m;
    }

    @Test
    void everyOrderIsCoveredWhenThereIsEnough() {
        List<Allocator.Result> r = Allocator.allocate(map("flour", 1000),
                List.of(new Allocator.Demand("A", 10, map("flour", 400)), new Allocator.Demand("B", 5, map("flour", 500))));
        assertTrue(r.get(0).fits());
        assertTrue(r.get(1).fits());
    }

    @Test
    void anOrderThatDoesNotFitKeepsNothingAndALaterSmallerOrderStillGetsServed() {
        List<Allocator.Result> r = Allocator.allocate(map("flour", 1000),
                List.of(new Allocator.Demand("big", 10, map("flour", 900)),
                        new Allocator.Demand("huge", 9, map("flour", 800)),
                        new Allocator.Demand("small", 8, map("flour", 100))));
        assertTrue(r.get(0).fits());
        assertFalse(r.get(1).fits());
        assertTrue(r.get(2).fits());                  // 100 left after "big", and "huge" took nothing
    }

    @Test
    void missingAmountsAreReportedPerIngredient() {
        List<Allocator.Result> r = Allocator.allocate(map("flour", 100, "cream", 1000),
                List.of(new Allocator.Demand("A", 1, map("flour", 350, "cream", 200))));
        assertFalse(r.get(0).fits());
        assertEquals(250.0, r.get(0).missing().get("flour"), 1e-9);
        assertFalse(r.get(0).missing().containsKey("cream"));
    }

    @Test
    void theStockPassedInIsNotChanged() {
        Map<String, Double> stock = map("flour", 1000);
        Allocator.allocate(stock, List.of(new Allocator.Demand("A", 1, map("flour", 400))));
        assertEquals(1000.0, stock.get("flour"), 1e-9);
    }

    @Test
    void noDemandsGivesNoResults() {
        assertTrue(Allocator.allocate(map("flour", 5), List.of()).isEmpty());
    }

    @Test
    void anIngredientThatIsNotInStockCountsAsZero() {
        List<Allocator.Result> r = Allocator.allocate(map(), List.of(new Allocator.Demand("A", 1, map("saffron", 1))));
        assertFalse(r.get(0).fits());
        assertEquals(1.0, r.get(0).missing().get("saffron"), 1e-9);
    }

    @Test
    void exactlyEnoughStillFits() {
        assertTrue(Allocator.allocate(map("flour", 300), List.of(new Allocator.Demand("A", 1, map("flour", 300)))).get(0).fits());
    }

    @Test
    void totalAddsUpTheSameIngredientAcrossRequirements() {
        Map<String, Double> sum = Allocator.total(List.of(map("flour", 100, "cream", 5), map("flour", 250)));
        assertEquals(350.0, sum.get("flour"), 1e-9);
        assertEquals(5.0, sum.get("cream"), 1e-9);
    }

    @Test
    void maxUnitsIsTheWholeNumberOfTheScarcestIngredient() {
        assertEquals(2, Allocator.maxUnits(map("flour", 300, "cream", 250), map("flour", 2400, "cream", 500)));
        assertEquals(2, Allocator.maxUnits(map("flour", 300, "cream", 250), map("flour", 2400, "cream", 749)));
    }

    @Test
    void maxUnitsIsZeroWhenOneIngredientIsMissingOrNegative() {
        assertEquals(0, Allocator.maxUnits(map("flour", 300, "cream", 250), map("flour", 2400)));
        assertEquals(0, Allocator.maxUnits(map("flour", 300), map("flour", -50)));
    }

    @Test
    void servedPriorityAddsUpOnlyTheCoveredOrders() {
        List<Allocator.Demand> d = List.of(new Allocator.Demand("A", 100, map("flour", 800)), new Allocator.Demand("B", 50, map("flour", 800)));
        assertEquals(100, Allocator.servedPriority(d, Allocator.allocate(map("flour", 1000), d)));
    }

    @Test
    void nullInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Allocator.allocate(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> Allocator.allocate(map(), null));
    }
}
