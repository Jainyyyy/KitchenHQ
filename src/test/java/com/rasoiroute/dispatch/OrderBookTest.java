package com.rasoiroute.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.geo.LocalityDirectory;
import com.rasoiroute.model.Fulfilment;
import com.rasoiroute.model.Order;
import com.rasoiroute.model.OrderType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderBookTest {

    private static final LocalDateTime BOOKED = LocalDateTime.of(2026, 11, 17, 9, 0);
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 19, 16, 0);

    private static Order order(String id, String phone, String item, LocalDateTime due) {
        return new Order(id, "Meera", phone, item, OrderType.BAKERY, Fulfilment.DELIVERY, BOOKED, due, "Satellite");
    }

    @Test
    void savesANewOrder() {
        OrderBook book = new OrderBook(100, 0.01);
        SubmitResult result = book.submit(order("A1", "9876543210", "Chocolate cake 1kg", DUE));
        assertTrue(result.accepted());
        assertNull(result.duplicateOfId());
        assertEquals(1, book.size());
    }

    @Test
    void refusesARepeatAndNamesTheFirstOrder() {
        OrderBook book = new OrderBook(100, 0.01);
        book.submit(order("A1", "9876543210", "Chocolate cake 1kg", DUE));
        SubmitResult result = book.submit(order("A2", "9876543210", "Chocolate cake 1kg", DUE.plusHours(1)));
        assertFalse(result.accepted());
        assertEquals("A1", result.duplicateOfId());
        assertTrue(result.message().contains("A1"));
        assertEquals(1, book.size());
    }

    @Test
    void sameCustomerAndItemOnAnotherDayIsNotARepeat() {
        OrderBook book = new OrderBook(100, 0.01);
        book.submit(order("A1", "9876543210", "Chocolate cake 1kg", DUE));
        assertTrue(book.submit(order("A2", "9876543210", "Chocolate cake 1kg", DUE.plusDays(1))).accepted());
        assertEquals(2, book.size());
    }

    @Test
    void phoneWrittenInAnotherStyleIsStillARepeat() {
        OrderBook book = new OrderBook(100, 0.01);
        book.submit(order("A1", "9876543210", "Chocolate cake 1kg", DUE));
        assertFalse(book.submit(order("A2", "+91 98765-43210", "chocolate  CAKE 1kg", DUE)).accepted());
    }

    @Test
    void refusesAnIdThatIsAlreadyUsed() {
        OrderBook book = new OrderBook(100, 0.01);
        book.submit(order("A1", "9876543210", "Chocolate cake 1kg", DUE));
        SubmitResult result = book.submit(order("A1", "9123456780", "Cookie jar", DUE));
        assertFalse(result.accepted());
        assertNull(result.duplicateOfId());
        assertEquals(1, book.size());
    }

    @Test
    void theListOfOrdersIsACopyThatCannotBeChanged() {
        OrderBook book = new OrderBook(100, 0.01);
        book.submit(order("A1", "9876543210", "Chocolate cake 1kg", DUE));
        List<Order> copy = book.orders();
        assertThrows(UnsupportedOperationException.class, () -> copy.remove(0));
        assertEquals(1, book.size());
    }

    @Test
    void clearForgetsEverythingSoTheSameOrderIsAcceptedAgain() {
        OrderBook book = new OrderBook(100, 0.01);
        Order first = order("A1", "9876543210", "Chocolate cake 1kg", DUE);
        book.submit(first);
        book.clear();
        assertEquals(0, book.size());
        assertTrue(book.submit(first).accepted());
    }

    @Test
    void everyRepeatInAGeneratedBatchIsCaughtAndNoNewOrderIsWronglyRefused() {
        LocalityDirectory directory = LocalityDirectory.loadDefault();
        List<Order> batch = new OrderGenerator(directory)
                .generate(3000, LocalDate.of(2026, 11, 19), 7, 0.2, new Random(99));

        Set<String> distinct = new HashSet<>();
        for (Order o : batch) {
            distinct.add(o.fingerprint());
        }

        OrderBook book = new OrderBook(3000, 0.01);
        int accepted = 0;
        for (Order o : batch) {
            if (book.submit(o).accepted()) {
                accepted++;
            }
        }
        assertEquals(distinct.size(), accepted, "exactly one order per distinct fingerprint should be saved");
        assertEquals(batch.size() - distinct.size(), book.confirmedDuplicates());
        assertEquals(book.bloomHits(), book.confirmedDuplicates() + book.falseAlarms());
    }

    @Test
    void rejectsBadInput() {
        OrderBook book = new OrderBook(10, 0.01);
        assertThrows(IllegalArgumentException.class, () -> book.submit(null));
        assertThrows(IllegalArgumentException.class, () -> new OrderBook(0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new OrderBook(100, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new OrderBook(100, 1.5));
    }
}
