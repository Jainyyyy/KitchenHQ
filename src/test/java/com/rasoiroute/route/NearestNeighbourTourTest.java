package com.rasoiroute.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.geo.DistanceMatrix;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class NearestNeighbourTourTest {

    private static final double EPS = 1e-9;

    @Test
    void goesToTheClosestUnvisitedStopEachTime() {
        // Positions: stop0=0 (kitchen), stop1=5, stop2=1, stop3=3.
        // From 0 the closest is stop2 (1). From there the closest is stop3 (2 away), then stop1.
        Tour tour = NearestNeighbourTour.solve(TestMaps.onALine(0, 5, 1, 3), 0);
        assertEquals(List.of(0, 2, 3, 1), tour.order());
        assertEquals(1 + 2 + 2 + 5, tour.length(), EPS);
    }

    @Test
    void onATieTheSmallerStopNumberIsChosen() {
        // Stops 1 and 2 are both 1 away from the kitchen (one on each side).
        Tour tour = NearestNeighbourTour.solve(TestMaps.onALine(0, 1, -1), 0);
        assertEquals(1, (int) tour.order().get(1));
    }

    @Test
    void visitsEveryStopOnceAndStartsAtTheGivenStop() {
        DistanceMatrix m = TestMaps.randomAhmedabad(40, 8);
        Tour tour = NearestNeighbourTour.solve(m, 3);
        assertEquals(3, (int) tour.order().get(0));
        assertEquals(40, tour.order().size());
        assertEquals(40, new HashSet<>(tour.order()).size());
        assertEquals(m.tourLength(tour.order()), tour.length(), EPS);
    }

    @Test
    void neverShorterThanTheTrueBestRoute() {
        Random random = new Random(5);
        for (int trial = 0; trial < 50; trial++) {
            int n = 2 + random.nextInt(8);
            DistanceMatrix m = TestMaps.randomAhmedabad(n, 300 + trial);
            assertTrue(NearestNeighbourTour.solve(m, 0).length() >= TestMaps.bruteForceBest(m, 0) - EPS);
        }
    }

    @Test
    void singleStopIsATripOfLengthZero() {
        Tour tour = NearestNeighbourTour.solve(TestMaps.onALine(0), 0);
        assertEquals(List.of(0), tour.order());
        assertEquals(0.0, tour.length(), EPS);
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> NearestNeighbourTour.solve(null, 0));
        assertThrows(IllegalArgumentException.class, () -> NearestNeighbourTour.solve(TestMaps.onALine(0, 1), -1));
        assertThrows(IllegalArgumentException.class, () -> NearestNeighbourTour.solve(TestMaps.onALine(0, 1), 2));
    }
}
