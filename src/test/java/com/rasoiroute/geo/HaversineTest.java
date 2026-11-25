package com.rasoiroute.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HaversineTest {

    @Test
    void distanceToTheSamePointIsZero() {
        Location p = new Location("A", 23.03, 72.55);
        assertEquals(0.0, Haversine.distanceKm(p, p), 1e-9);
    }

    @Test
    void distanceIsTheSameBothWays() {
        Location a = new Location("A", 23.03, 72.55);
        Location b = new Location("B", 23.10, 72.60);
        assertEquals(Haversine.distanceKm(a, b), Haversine.distanceKm(b, a), 1e-12);
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        Location a = new Location("A", 10.0, 50.0);
        Location b = new Location("B", 11.0, 50.0);
        assertEquals(111.195, Haversine.distanceKm(a, b), 0.01);
    }

    @Test
    void oneDegreeOfLongitudeOnTheEquatorIsAbout111Km() {
        Location a = new Location("A", 0.0, 10.0);
        Location b = new Location("B", 0.0, 11.0);
        assertEquals(111.195, Haversine.distanceKm(a, b), 0.01);
    }

    @Test
    void edgeCase_oppositeSidesOfTheEarthIsHalfTheCircumference() {
        Location a = new Location("A", 0.0, 0.0);
        Location b = new Location("B", 0.0, 180.0);
        assertEquals(Math.PI * Haversine.EARTH_RADIUS_KM, Haversine.distanceKm(a, b), 0.01);
    }

    @Test
    void twoAhmedabadLocalitiesAreAFewKilometresApart() {
        Location a = new Location("Navrangpura", 23.0383, 72.5600);
        Location b = new Location("Satellite", 23.0267, 72.5086);
        double km = Haversine.distanceKm(a, b);
        assertTrue(km > 4.0 && km < 7.0, "distance was " + km);
    }

    @Test
    void invalidLocationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Location("Bad", 91.0, 72.0));
        assertThrows(IllegalArgumentException.class, () -> new Location("Bad", 23.0, 181.0));
        assertThrows(IllegalArgumentException.class, () -> new Location("Bad", Double.NaN, 72.0));
        assertThrows(IllegalArgumentException.class, () -> new Location(" ", 23.0, 72.0));
        assertThrows(IllegalArgumentException.class, () -> Haversine.distanceKm(null, new Location("A", 1, 1)));
    }
}
