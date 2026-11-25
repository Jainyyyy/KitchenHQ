package com.rasoiroute.geo;

/**
 * A named point on the map: a locality (or the kitchen) with its latitude and longitude.
 * Bad coordinates are rejected the moment a Location is created.
 */
public record Location(String name, double latitude, double longitude) {

    public Location {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Location name is required");
        }
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be a number between -90 and 90: " + latitude);
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be a number between -180 and 180: " + longitude);
        }
        name = name.trim();
    }
}
