package com.rasoiroute.geo;

/**
 * Straight-line distance between two points on the Earth's surface (the "great-circle" distance),
 * using the haversine formula.
 *
 * Why this distance? It always obeys the triangle rule (going directly is never longer than
 * going via a third place), which is exactly what the 2x route guarantee needs.
 * Real roads are longer than this, which we state as an assumption.
 */
public final class Haversine {

    /** Average radius of the Earth in kilometres. */
    public static final double EARTH_RADIUS_KM = 6371.0088;

    private Haversine() {
        // static helper only
    }

    public static double distanceKm(Location a, Location b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Both locations are required");
        }
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());

        double sinHalfLat = Math.sin(dLat / 2);
        double sinHalfLon = Math.sin(dLon / 2);
        double h = sinHalfLat * sinHalfLat + Math.cos(lat1) * Math.cos(lat2) * sinHalfLon * sinHalfLon;

        h = Math.min(1.0, Math.max(0.0, h));   // protects against tiny rounding errors
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h));
    }
}
