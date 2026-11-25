package com.rasoiroute.dispatch;

import com.rasoiroute.geo.LocalityDirectory;
import com.rasoiroute.model.Fulfilment;
import com.rasoiroute.model.Order;
import com.rasoiroute.model.OrderType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Makes realistic pretend orders for demos and experiments (there is no real customer data).
 *
 * What "realistic" means here:
 *   - about half are daily food, a quarter bakery, a quarter catering
 *   - daily food is booked up to a day ahead, bakery 1-3 days ahead, catering 1-7 days ahead
 *   - about 70% are deliveries, 30% pickups
 *   - due between 8:00 and 21:55
 *   - a chosen share are accidental repeats: the same customer sending the same order again
 */
public final class OrderGenerator {

    private static final String[] DAILY_ITEMS = {"Dal rice meal box", "Roti sabzi box", "Khichdi and kadhi", "Paneer meal box"};
    private static final String[] BAKERY_ITEMS = {"Chocolate cake 1kg", "Eggless brownie box", "Cupcakes dozen", "Cookie jar"};
    private static final String[] CATERING_ITEMS = {"Party meal for 30", "Kitty party snacks for 20", "Wedding sweets box", "Pooja prasad for 50"};

    private final List<String> deliveryLocalities = new ArrayList<>();

    public OrderGenerator(LocalityDirectory directory) {
        if (directory == null) {
            throw new IllegalArgumentException("locality directory is required");
        }
        for (String name : directory.names()) {
            if (!name.equalsIgnoreCase(DispatchPlanner.KITCHEN)) {
                deliveryLocalities.add(directory.find(name).name());
            }
        }
        if (deliveryLocalities.isEmpty()) {
            throw new IllegalArgumentException("The locality list has no places to deliver to");
        }
    }

    /**
     * @param count         how many orders to make
     * @param firstDay      the earliest due day
     * @param days          how many days the due dates are spread over (at least 1)
     * @param duplicateRate share of orders (0 to 1) that repeat an earlier order
     * @param random        the random source (use a fixed seed to get the same orders every time)
     */
    public List<Order> generate(int count, LocalDate firstDay, int days, double duplicateRate, Random random) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        if (firstDay == null || random == null) {
            throw new IllegalArgumentException("first day and random source are required");
        }
        if (days < 1) {
            throw new IllegalArgumentException("days must be at least 1");
        }
        if (!(duplicateRate >= 0.0 && duplicateRate <= 1.0)) {
            throw new IllegalArgumentException("duplicateRate must be between 0 and 1");
        }

        List<Order> orders = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            String id = String.format("O-%05d", i);
            if (!orders.isEmpty() && random.nextDouble() < duplicateRate) {
                Order original = orders.get(random.nextInt(orders.size()));
                orders.add(new Order(id, original.customerName(), original.phone(), original.item(),
                        original.type(), original.fulfilment(), original.bookedAt(), original.dueAt(),
                        original.locality()));
            } else {
                orders.add(freshOrder(id, firstDay, days, random));
            }
        }
        return orders;
    }

    private Order freshOrder(String id, LocalDate firstDay, int days, Random random) {
        double roll = random.nextDouble();
        OrderType type = roll < 0.5 ? OrderType.DAILY_FOOD : roll < 0.75 ? OrderType.BAKERY : OrderType.CATERING;

        LocalDate dueDay = firstDay.plusDays(random.nextInt(days));
        LocalDateTime dueAt = LocalDateTime.of(dueDay, LocalTime.of(8 + random.nextInt(14), 5 * random.nextInt(12)));

        int leadMinutes;
        String item;
        switch (type) {
            case DAILY_FOOD -> {
                leadMinutes = 20 + random.nextInt(24 * 60 - 20);            // up to a day ahead
                item = DAILY_ITEMS[random.nextInt(DAILY_ITEMS.length)];
            }
            case BAKERY -> {
                leadMinutes = 24 * 60 + random.nextInt(2 * 24 * 60 + 1);    // 1 to 3 days ahead
                item = BAKERY_ITEMS[random.nextInt(BAKERY_ITEMS.length)];
            }
            default -> {
                leadMinutes = 24 * 60 + random.nextInt(6 * 24 * 60 + 1);    // 1 to 7 days ahead
                item = CATERING_ITEMS[random.nextInt(CATERING_ITEMS.length)];
            }
        }

        boolean delivery = random.nextDouble() < 0.7;
        String locality = delivery ? deliveryLocalities.get(random.nextInt(deliveryLocalities.size())) : null;
        String phone = (6 + random.nextInt(4)) + String.format("%09d", random.nextInt(1_000_000_000));

        return new Order(id, "Customer " + id.substring(2), phone, item, type,
                delivery ? Fulfilment.DELIVERY : Fulfilment.PICKUP,
                dueAt.minusMinutes(leadMinutes), dueAt, locality);
    }
}
