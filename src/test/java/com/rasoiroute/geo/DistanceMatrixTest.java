package com.rasoiroute.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DistanceMatrixTest {

    private static final double[][] TRIANGLE = {
            {0, 3, 5},
            {3, 0, 4},
            {5, 4, 0}
    };

    @Test
    void builtFromLocationsIsSymmetricWithZeroDiagonal() {
        List<Location> points = List.of(
                new Location("A", 23.00, 72.50),
                new Location("B", 23.05, 72.55),
                new Location("C", 23.10, 72.60));
        DistanceMatrix m = DistanceMatrix.fromLocations(points);
        assertEquals(3, m.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0, m.get(i, i), 0.0);
            for (int j = 0; j < 3; j++) {
                assertEquals(m.get(i, j), m.get(j, i), 1e-12);
            }
        }
    }

    @Test
    void tourLengthAddsEveryLegAndReturnsToTheStart() {
        DistanceMatrix m = DistanceMatrix.fromTable(TRIANGLE);
        assertEquals(12.0, m.tourLength(List.of(0, 1, 2)), 1e-9);   // 3 + 4 + 5
        assertEquals(12.0, m.tourLength(List.of(0, 2, 1)), 1e-9);   // 5 + 4 + 3
    }

    @Test
    void edgeCase_tourLengthOfNothingOrOneStopIsZero() {
        DistanceMatrix m = DistanceMatrix.fromTable(TRIANGLE);
        assertEquals(0.0, m.tourLength(new ArrayList<>()), 0.0);
        assertEquals(0.0, m.tourLength(List.of(1)), 0.0);
        assertEquals(0.0, m.tourLength(null), 0.0);
    }

    @Test
    void edgeCase_emptyAndSingleStopMatricesAreAllowed() {
        assertEquals(0, DistanceMatrix.fromLocations(new ArrayList<>()).size());
        assertEquals(1, DistanceMatrix.fromLocations(List.of(new Location("A", 1, 1))).size());
    }

    @Test
    void validTableIsAccepted() {
        DistanceMatrix m = DistanceMatrix.fromTable(TRIANGLE);
        assertEquals(4.0, m.get(1, 2), 0.0);
    }

    @Test
    void tableThatIsNotSquareIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DistanceMatrix.fromTable(new double[][] {{0, 1}, {1, 0}, {2, 2}}));
        assertThrows(IllegalArgumentException.class,
                () -> DistanceMatrix.fromTable(new double[][] {{0, 1, 2}, {1, 0}}));
    }

    @Test
    void negativeOrNotANumberDistancesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DistanceMatrix.fromTable(new double[][] {{0, -1}, {-1, 0}}));
        assertThrows(IllegalArgumentException.class,
                () -> DistanceMatrix.fromTable(new double[][] {{0, Double.NaN}, {Double.NaN, 0}}));
    }

    @Test
    void asymmetricTableIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DistanceMatrix.fromTable(new double[][] {{0, 1}, {2, 0}}));
    }

    @Test
    void nonZeroDiagonalAndNullInputsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> DistanceMatrix.fromTable(new double[][] {{1, 1}, {1, 0}}));
        assertThrows(IllegalArgumentException.class, () -> DistanceMatrix.fromTable(null));
        assertThrows(IllegalArgumentException.class, () -> DistanceMatrix.fromLocations(null));
    }

    @Test
    void changingTheOriginalArrayAfterwardsDoesNotChangeTheMatrix() {
        double[][] table = {{0, 1}, {1, 0}};
        DistanceMatrix m = DistanceMatrix.fromTable(table);
        table[0][1] = 99;
        assertEquals(1.0, m.get(0, 1), 0.0);
    }
}
