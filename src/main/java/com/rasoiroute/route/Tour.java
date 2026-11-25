package com.rasoiroute.route;

import java.util.List;

/**
 * A round trip: the stops in the order they are visited, and the total length in km.
 * The trip starts at the first stop in the list and returns to it at the end,
 * so the way back is NOT written in the list.
 */
public record Tour(List<Integer> order, double length) {

    public Tour {
        if (order == null || order.isEmpty()) {
            throw new IllegalArgumentException("A tour needs at least one stop");
        }
        if (!Double.isFinite(length) || length < 0) {
            throw new IllegalArgumentException("Tour length must be a number that is not negative");
        }
        order = List.copyOf(order);   // an unchangeable copy, so nobody can alter the route later
    }
}
