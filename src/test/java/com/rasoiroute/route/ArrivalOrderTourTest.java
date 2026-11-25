package com.rasoiroute.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.geo.DistanceMatrix;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ArrivalOrderTourTest {

    private static final double EPS = 1e-9;

    @Test
    void visitsStopsInTheOrderTheyAreNumbered() {
        Tour tour = ArrivalOrderTour.solve(TestMaps.onALine(0, 5, 1, 3), 0);
        assertEquals(List.of(0, 1, 2, 3), tour.order());
        assertEquals(5 + 4 + 2 + 3, tour.length(), EPS);   // 0->5, 5->1, 1->3, 3->0
    }

    @Test
    void whenStartingElsewhereTheStartComesFirstThenTheRestInOrder() {
        Tour tour = ArrivalOrderTour.solve(TestMaps.onALine(0, 1, 2, 3, 4), 2);
        assertEquals(List.of(2, 0, 1, 3, 4), tour.order());
    }

    @Test
    void reportedLengthMatchesTheDistanceTable() {
        DistanceMatrix m = TestMaps.randomAhmedabad(30, 9);
        Tour tour = ArrivalOrderTour.solve(m, 0);
        assertEquals(m.tourLength(tour.order()), tour.length(), EPS);
    }

    @Test
    void neverShorterThanTheTrueBestRoute() {
        Random random = new Random(6);
        for (int trial = 0; trial < 50; trial++) {
            int n = 2 + random.nextInt(8);
            DistanceMatrix m = TestMaps.randomAhmedabad(n, 400 + trial);
            assertTrue(ArrivalOrderTour.solve(m, 0).length() >= TestMaps.bruteForceBest(m, 0) - EPS);
        }
    }

    @Test
    void singleStopIsATripOfLengthZero() {
        Tour tour = ArrivalOrderTour.solve(TestMaps.onALine(0), 0);
        assertEquals(List.of(0), tour.order());
        assertEquals(0.0, tour.length(), EPS);
    }

    @Test
    void rejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> ArrivalOrderTour.solve(null, 0));
        assertThrows(IllegalArgumentException.class, () -> ArrivalOrderTour.solve(TestMaps.onALine(0, 1), 5));
    }
}
