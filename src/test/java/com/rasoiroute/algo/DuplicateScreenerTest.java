package com.rasoiroute.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.algo.DuplicateScreener.Verdict;
import com.rasoiroute.model.Fulfilment;
import com.rasoiroute.model.Order;
import com.rasoiroute.model.OrderType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DuplicateScreenerTest {

    private static final LocalDateTime BOOKED = LocalDateTime.of(2026, 11, 18, 10, 0);
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 20, 16, 0);

    private static Order order(String id, String phone, String item, LocalDateTime due) {
        return new Order(id, "Customer " + id, phone, item, OrderType.BAKERY,
                Fulfilment.DELIVERY, BOOKED, due, "Satellite");
    }

    @Test
    void firstOrderIsNew() {
        DuplicateScreener screener = new DuplicateScreener(BloomFilter.forExpectedItems(100, 0.01));
        assertEquals(Verdict.NEW, screener.check(order("a", "9876543210", "Cake", DUE)));
    }

    @Test
    void repeatOfARegisteredOrderIsConfirmedAsDuplicate() {
        DuplicateScreener screener = new DuplicateScreener(BloomFilter.forExpectedItems(100, 0.01));
        screener.register(order("a", "9876543210", "Cake", DUE));
        assertEquals(Verdict.DUPLICATE, screener.check(order("b", "+91 98765 43210", "cake", DUE)));
        assertEquals(1, screener.getConfirmedDuplicates());
    }

    @Test
    void sameCustomerSameItemOnAnotherDayIsNotADuplicate() {
        DuplicateScreener screener = new DuplicateScreener(BloomFilter.forExpectedItems(100, 0.01));
        screener.register(order("a", "9876543210", "Cake", DUE));
        assertEquals(Verdict.NEW, screener.check(order("b", "9876543210", "Cake", DUE.plusDays(5))));
    }

    @Test
    void falseAlarmFromTheBloomFilterIsCorrectedByTheExactCheck() {
        // A deliberately tiny filter fills up, so it says "possibly seen" for almost anything.
        DuplicateScreener screener = new DuplicateScreener(BloomFilter.withSize(8, 1));
        for (int i = 0; i < 60; i++) {
            screener.register(order("r" + i, "98765" + String.format("%05d", i), "Cake " + i, DUE));
        }
        // These customers are genuinely new. The exact check must still call them NEW.
        for (int i = 0; i < 20; i++) {
            Order stranger = order("s" + i, "90000" + String.format("%05d", i), "Pie " + i, DUE);
            assertEquals(Verdict.NEW, screener.check(stranger));
        }
        assertTrue(screener.getFalseAlarms() > 0, "the tiny filter should have raised false alarms");
        assertEquals(0, screener.getConfirmedDuplicates());
    }

    @Test
    void registeringTheSameOrderTwiceDoesNotBreakAnything() {
        DuplicateScreener screener = new DuplicateScreener(BloomFilter.forExpectedItems(100, 0.01));
        Order o = order("a", "9876543210", "Cake", DUE);
        screener.register(o);
        screener.register(o);
        assertEquals(Verdict.DUPLICATE, screener.check(o));
    }
}
