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
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderGeneratorTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 11, 19);
    private static final LocalityDirectory DIRECTORY = LocalityDirectory.loadDefault();

    private static List<Order> make(int count, int days, double duplicateRate, long seed) {
        return new OrderGenerator(DIRECTORY).generate(count, DAY1, days, duplicateRate, new Random(seed));
    }

    @Test
    void makesTheRequestedNumberOfOrders() {
        assertEquals(250, make(250, 7, 0.1, 1).size());
        assertEquals(0, make(0, 7, 0.1, 1).size());
    }

    @Test
    void everyOrderHasItsOwnId() {
        Set<String> ids = new HashSet<>();
        for (Order o : make(1000, 7, 0.3, 2)) {
            ids.add(o.id());
        }
        assertEquals(1000, ids.size());
    }

    @Test
    void sameSeedGivesTheSameOrders() {
        assertEquals(make(500, 7, 0.1, 42), make(500, 7, 0.1, 42));
    }

    @Test
    void noRepeatsAppearWhenTheRepeatRateIsZero() {
        Set<String> fingerprints = new HashSet<>();
        for (Order o : make(2000, 7, 0.0, 3)) {
            fingerprints.add(o.fingerprint());
        }
        assertEquals(2000, fingerprints.size());
    }

    @Test
    void aboutTheRequestedShareOfOrdersAreRepeats() {
        Set<String> fingerprints = new HashSet<>();
        for (Order o : make(4000, 7, 0.3, 4)) {
            fingerprints.add(o.fingerprint());
        }
        double repeatShare = 1.0 - fingerprints.size() / 4000.0;
        assertTrue(repeatShare > 0.25 && repeatShare < 0.35, "repeat share was " + repeatShare);
    }

    @Test
    void bookingLeadTimesMatchTheOrderType() {
        for (Order o : make(3000, 7, 0.0, 5)) {
            long minutes = Duration.between(o.bookedAt(), o.dueAt()).toMinutes();
            switch (o.type()) {
                case DAILY_FOOD -> assertTrue(minutes >= 20 && minutes < 24 * 60, "daily food lead " + minutes);
                case BAKERY -> assertTrue(minutes >= 24 * 60 && minutes <= 3 * 24 * 60, "bakery lead " + minutes);
                case CATERING -> assertTrue(minutes >= 24 * 60 && minutes <= 7 * 24 * 60, "catering lead " + minutes);
            }
        }
    }

    @Test
    void thereIsAMixOfTypesAndOfDeliveryAndPickup() {
        EnumSet<OrderType> types = EnumSet.noneOf(OrderType.class);
        int deliveries = 0;
        List<Order> batch = make(3000, 7, 0.0, 6);
        for (Order o : batch) {
            types.add(o.type());
            if (o.fulfilment() == Fulfilment.DELIVERY) {
                deliveries++;
            }
        }
        assertEquals(3, types.size());
        double share = deliveries / 3000.0;
        assertTrue(share > 0.6 && share < 0.8, "delivery share was " + share);
    }

    @Test
    void deliveriesGoToKnownLocalitiesAndPickupsHaveNone() {
        for (Order o : make(1000, 7, 0.1, 7)) {
            if (o.fulfilment() == Fulfilment.DELIVERY) {
                assertTrue(DIRECTORY.contains(o.locality()), "unknown locality " + o.locality());
                assertFalse(o.locality().equalsIgnoreCase(DispatchPlanner.KITCHEN));
            } else {
                assertNull(o.locality());
            }
        }
    }

    @Test
    void dueTimesAreInsideOpeningHoursAndInsideTheDateRange() {
        for (Order o : make(2000, 5, 0.1, 8)) {
            int hour = o.dueAt().getHour();
            assertTrue(hour >= 8 && hour <= 21, "due hour " + hour);
            LocalDate dueDay = o.dueAt().toLocalDate();
            assertTrue(!dueDay.isBefore(DAY1) && !dueDay.isAfter(DAY1.plusDays(4)), "due day " + dueDay);
        }
    }

    @Test
    void rejectsBadInput() {
        OrderGenerator generator = new OrderGenerator(DIRECTORY);
        Random random = new Random(1);
        assertThrows(IllegalArgumentException.class, () -> generator.generate(-1, DAY1, 7, 0.1, random));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(10, null, 7, 0.1, random));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(10, DAY1, 0, 0.1, random));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(10, DAY1, 7, 1.5, random));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(10, DAY1, 7, -0.1, random));
        assertThrows(IllegalArgumentException.class, () -> generator.generate(10, DAY1, 7, 0.1, null));
        assertThrows(IllegalArgumentException.class, () -> new OrderGenerator(null));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderGenerator(LocalityDirectory.fromCsv("Kitchen,23.03,72.55")));
    }
}
