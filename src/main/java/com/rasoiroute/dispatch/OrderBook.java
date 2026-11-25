package com.rasoiroute.dispatch;

import com.rasoiroute.algo.BloomFilter;
import com.rasoiroute.algo.DuplicateScreener;
import com.rasoiroute.model.Order;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The list of orders that have been saved so far.
 * Every new order is checked for repeats before it is saved (Bloom filter first, then an exact check).
 *
 * The methods are "synchronized": the web server may handle two requests at the same moment,
 * and this makes them take turns instead of tripping over each other.
 */
public final class OrderBook {

    private final int expectedOrders;
    private final double falsePositiveRate;

    private DuplicateScreener screener;
    private final List<Order> orders = new ArrayList<>();
    private final Set<String> usedIds = new HashSet<>();
    private final Map<String, String> firstIdByFingerprint = new HashMap<>();

    public OrderBook(int expectedOrders, double falsePositiveRate) {
        this.expectedOrders = expectedOrders;
        this.falsePositiveRate = falsePositiveRate;
        this.screener = newScreener();      // also checks the two numbers are sensible
    }

    private DuplicateScreener newScreener() {
        return new DuplicateScreener(BloomFilter.forExpectedItems(expectedOrders, falsePositiveRate));
    }

    /** Tries to save an order. Refuses it (and says why) if its id is taken or it repeats an earlier order. */
    public synchronized SubmitResult submit(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order is required");
        }
        if (usedIds.contains(order.id())) {
            return new SubmitResult(false, "Order id " + order.id() + " is already used", null);
        }
        if (screener.check(order) == DuplicateScreener.Verdict.DUPLICATE) {
            String firstId = firstIdByFingerprint.get(order.fingerprint());
            return new SubmitResult(false,
                    "Looks like a repeat of order " + firstId + " (same phone, type, item and due date)",
                    firstId);
        }
        screener.register(order);
        orders.add(order);
        usedIds.add(order.id());
        firstIdByFingerprint.put(order.fingerprint(), order.id());
        return new SubmitResult(true, "Order saved", null);
    }

    /** A copy of the saved orders, in the order they were saved. */
    public synchronized List<Order> orders() {
        return List.copyOf(orders);
    }

    public synchronized int size() {
        return orders.size();
    }

    /** Removes every order and starts the repeat check afresh. */
    public synchronized void clear() {
        orders.clear();
        usedIds.clear();
        firstIdByFingerprint.clear();
        screener = newScreener();
    }

    // The three counters from the repeat check, used for the experiment on the Bloom filter.

    public synchronized int bloomHits() {
        return screener.getBloomHits();
    }

    public synchronized int confirmedDuplicates() {
        return screener.getConfirmedDuplicates();
    }

    public synchronized int falseAlarms() {
        return screener.getFalseAlarms();
    }
}
