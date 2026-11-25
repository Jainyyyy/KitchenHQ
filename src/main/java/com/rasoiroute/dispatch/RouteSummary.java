package com.rasoiroute.dispatch;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The delivery route for one day, and how it compares with the alternatives.
 * All distances are straight-line kilometres, NOT road distances.
 *
 * @param stops              the route in visiting order, starting at the kitchen
 * @param returnKm           distance from the last stop back to the kitchen
 * @param totalKm            length of the whole round trip (legs + the way back)
 * @param mstKm              length of the spanning tree the route was built from (a lower bound on any route)
 * @param nearestNeighbourKm the "always go to the closest next stop" baseline
 * @param arrivalOrderKm     the "visit in the order the orders came in" baseline
 * @param exactKm            the true best round trip (only worked out when there are few enough stops)
 */
public record RouteSummary(
        List<RouteStop> stops,
        double returnKm,
        double totalKm,
        double mstKm,
        double nearestNeighbourKm,
        double arrivalOrderKm,
        OptionalDouble exactKm) {

    /** True when the route is at most twice the spanning tree, which proves it is within 2x of the best. */
    public boolean withinGuarantee() {
        return totalKm <= 2.0 * mstKm * (1.0 + 1e-9) + 1e-9;
    }
}
