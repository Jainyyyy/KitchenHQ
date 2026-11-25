package com.rasoiroute.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.geo.DistanceMatrix;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ExactTspTest {

    private static final double EPS = 1e-9;

    @Test
    void agreesWithTryingEveryOrderOnManyRandomSmallMaps() {
        Random random = new Random(21);
        for (int trial = 0; trial < 120; trial++) {
            int n = 1 + random.nextInt(8);
            DistanceMatrix m = TestMaps.randomAhmedabad(n, 9000 + trial);
            double expected = TestMaps.bruteForceBest(m, 0);
            assertEquals(expected, ExactTsp.solve(m, 0).length(), EPS, "trial " + trial + " n=" + n);
        }
    }

    @Test
    void lineExampleWorkedByHand() {
        // Stops at 0,1,2,3 on a line: best trip goes out and straight back = 2 x 3 = 6.
        assertEquals(6.0, ExactTsp.solve(TestMaps.onALine(0, 1, 2, 3), 0).length(), EPS);
    }

    @Test
    void cornersOfASquareBestTripIsThePerimeter() {
        double s = Math.sqrt(2.0);
        double[][] square = {
                {0, 1, s, 1},
                {1, 0, 1, s},
                {s, 1, 0, 1},
                {1, s, 1, 0}};
        assertEquals(4.0, ExactTsp.solve(DistanceMatrix.fromTable(square), 0).length(), EPS);
    }

    @Test
    void singleStopAndTwoStops() {
        assertEquals(List.of(0), ExactTsp.solve(TestMaps.onALine(0), 0).order());
        assertEquals(0.0, ExactTsp.solve(TestMaps.onALine(0), 0).length(), EPS);
        assertEquals(10.0, ExactTsp.solve(TestMaps.onALine(0, 5), 0).length(), EPS);
    }

    @Test
    void routeStartsAtTheGivenStopAndVisitsEveryStopOnce() {
        DistanceMatrix m = TestMaps.randomAhmedabad(14, 31);
        Tour tour = ExactTsp.solve(m, 5);
        assertEquals(5, (int) tour.order().get(0));
        assertEquals(14, new HashSet<>(tour.order()).size());
        assertEquals(14, tour.order().size());
        assertEquals(m.tourLength(tour.order()), tour.length(), EPS);
    }

    @Test
    void neverLongerThanAnyOtherMethod() {
        Random random = new Random(33);
        for (int trial = 0; trial < 60; trial++) {
            int n = 3 + random.nextInt(10);
            DistanceMatrix m = TestMaps.randomAhmedabad(n, 700 + trial);
            double exact = ExactTsp.solve(m, 0).length();
            assertTrue(exact <= TourBuilder.build(m, 0).tour().length() + EPS, "approx beat exact?! trial " + trial);
            assertTrue(exact <= NearestNeighbourTour.solve(m, 0).length() + EPS, "nearest beat exact?! trial " + trial);
            assertTrue(exact <= ArrivalOrderTour.solve(m, 0).length() + EPS, "arrival beat exact?! trial " + trial);
        }
    }

    @Test
    void largestAllowedSizeStillWorks() {
        DistanceMatrix m = TestMaps.randomAhmedabad(ExactTsp.MAX_STOPS, 41);
        Tour tour = ExactTsp.solve(m, 0);
        assertEquals(ExactTsp.MAX_STOPS, new HashSet<>(tour.order()).size());
        assertTrue(tour.length() <= TourBuilder.build(m, 0).tour().length() + EPS);
    }

    @Test
    void rejectsTooManyStopsAndBadInput() {
        assertThrows(IllegalArgumentException.class,
                () -> ExactTsp.solve(TestMaps.randomAhmedabad(ExactTsp.MAX_STOPS + 1, 1), 0));
        assertThrows(IllegalArgumentException.class, () -> ExactTsp.solve(null, 0));
        assertThrows(IllegalArgumentException.class, () -> ExactTsp.solve(TestMaps.onALine(0, 1), 2));
    }
}
