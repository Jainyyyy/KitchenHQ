package com.rasoiroute.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final LocalDateTime BOOKED = LocalDateTime.of(2026, 11, 18, 10, 0);
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 11, 20, 16, 0);

    private static Order delivery(String phone, String item, LocalDateTime due) {
        return new Order("o1", "Mrs Shah", phone, item, OrderType.BAKERY,
                Fulfilment.DELIVERY, BOOKED, due, "Navrangpura");
    }

    @Test
    void differentPhoneFormatsGiveTheSameFingerprint() {
        Order a = delivery("+91 98765 43210", "Chocolate Cake", DUE);
        Order b = delivery("098765-43210", "chocolate   cake", DUE);
        Order c = delivery("9876543210", "Chocolate cake", DUE);
        assertEquals(a.fingerprint(), b.fingerprint());
        assertEquals(a.fingerprint(), c.fingerprint());
        assertEquals("9876543210", a.phone());
    }

    @Test
    void sameItemOnADifferentDueDateIsNotTheSameFingerprint() {
        Order a = delivery("9876543210", "Chocolate cake", DUE);
        Order b = delivery("9876543210", "Chocolate cake", DUE.plusDays(3));
        assertNotEquals(a.fingerprint(), b.fingerprint());
    }

    @Test
    void sameDayDifferentTimeIsStillTheSameFingerprint() {
        Order a = delivery("9876543210", "Chocolate cake", DUE);
        Order b = delivery("9876543210", "Chocolate cake", DUE.withHour(11));
        assertEquals(a.fingerprint(), b.fingerprint());
    }

    @Test
    void deliveryWithoutLocalityIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new Order("o1", "Mrs Shah", "9876543210", "Cake", OrderType.BAKERY,
                        Fulfilment.DELIVERY, BOOKED, DUE, "  "));
    }

    @Test
    void pickupWithoutLocalityIsAllowed() {
        Order pickup = new Order("o1", "Mrs Shah", "9876543210", "Cake", OrderType.BAKERY,
                Fulfilment.PICKUP, BOOKED, DUE, null);
        assertNull(pickup.locality());
    }

    @Test
    void dueTimeBeforeBookingTimeIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new Order("o1", "Mrs Shah", "9876543210", "Cake", OrderType.BAKERY,
                        Fulfilment.PICKUP, DUE, BOOKED, null));
    }

    @Test
    void badPhoneNumbersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> delivery("12345", "Cake", DUE));
        assertThrows(IllegalArgumentException.class, () -> delivery(null, "Cake", DUE));
        assertThrows(IllegalArgumentException.class, () -> delivery("abcdefghij", "Cake", DUE));
    }

    @Test
    void missingRequiredFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new Order("", "Mrs Shah", "9876543210", "Cake", OrderType.BAKERY,
                        Fulfilment.PICKUP, BOOKED, DUE, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Order("o1", " ", "9876543210", "Cake", OrderType.BAKERY,
                        Fulfilment.PICKUP, BOOKED, DUE, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Order("o1", "Mrs Shah", "9876543210", "", OrderType.BAKERY,
                        Fulfilment.PICKUP, BOOKED, DUE, null));
        assertThrows(IllegalArgumentException.class, () ->
                new Order("o1", "Mrs Shah", "9876543210", "Cake", null,
                        Fulfilment.PICKUP, BOOKED, DUE, null));
    }
}
