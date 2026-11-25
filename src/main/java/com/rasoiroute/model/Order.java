package com.rasoiroute.model;

import java.time.LocalDateTime;

/**
 * One customer order.
 *
 * A record is a short way to write a class whose fields never change.
 * The block inside the record (the "compact constructor") runs every time
 * an Order is created, so bad data is rejected right here, once.
 */
public record Order(
        String id,
        String customerName,
        String phone,
        String item,
        OrderType type,
        Fulfilment fulfilment,
        LocalDateTime bookedAt,
        LocalDateTime dueAt,
        String locality) {

    public Order {
        if (isBlank(id)) {
            throw new IllegalArgumentException("Order id is required");
        }
        if (isBlank(customerName)) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (isBlank(item)) {
            throw new IllegalArgumentException("Item is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Order type is required");
        }
        if (fulfilment == null) {
            throw new IllegalArgumentException("Fulfilment (delivery or pickup) is required");
        }
        if (bookedAt == null || dueAt == null) {
            throw new IllegalArgumentException("Booked-on and due-on times are required");
        }
        if (dueAt.isBefore(bookedAt)) {
            throw new IllegalArgumentException("Due time cannot be before the booking time");
        }
        phone = normalizePhone(phone);
        customerName = customerName.trim();
        item = item.trim();
        locality = isBlank(locality) ? null : locality.trim();
        if (fulfilment == Fulfilment.DELIVERY && locality == null) {
            throw new IllegalArgumentException("A delivery order needs a locality");
        }
    }

    /**
     * Turns "+91 98765-43210", "098765 43210" or "9876543210" into "9876543210",
     * so the same customer always gives the same phone text.
     */
    static String normalizePhone(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Phone number is required");
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 10) {
            throw new IllegalArgumentException("Phone number must have 10 digits");
        }
        return digits;
    }

    /**
     * The text the repeat-order check looks at: who + what kind + what item + which day.
     * The due DATE is included on purpose, so the same cake booked for two different
     * birthdays is not wrongly treated as a repeat.
     */
    public String fingerprint() {
        String cleanItem = item.toLowerCase().replaceAll("\\s+", " ");
        return phone + "|" + type.name() + "|" + cleanItem + "|" + dueAt.toLocalDate();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
