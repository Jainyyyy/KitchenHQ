package com.rasoiroute.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.geo.MetricChecker.TriangleViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MetricCheckerTest {

    @Test
    void straightLineDistancesBetweenRandomAhmedabadPointsNeverBreakTheRule() {
        Random random = new Random(2026);
        List<Location> points = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double lat = 22.95 + random.nextDouble() * 0.17;
            double lon = 72.45 + random.nextDouble() * 0.25;
            points.add(new Location("P" + i, lat, lon));
        }
        assertTrue(MetricChecker.isMetric(DistanceMatrix.fromLocations(points)));
    }

    @Test
    void defaultLocalitiesObeyTheRule() {
        LocalityDirectory dir = LocalityDirectory.loadDefault();
        List<Location> points = new ArrayList<>();
        for (String name : dir.names()) {
            points.add(dir.find(name));
        }
        assertTrue(MetricChecker.isMetric(DistanceMatrix.fromLocations(points)));
    }

    @Test
    void aBrokenTableIsDetectedAndTheTriangleIsNamed() {
        // 0 to 2 directly is 10, but 0 to 1 to 2 is only 1 + 1 = 2: the rule is broken.
        DistanceMatrix broken = DistanceMatrix.fromTable(new double[][] {
                {0, 1, 10},
                {1, 0, 1},
                {10, 1, 0}
        });
        assertFalse(MetricChecker.isMetric(broken));
        TriangleViolation v = MetricChecker.findViolations(broken, 5).get(0);
        assertEquals(0, v.i());
        assertEquals(2, v.j());
        assertEquals(1, v.k());
        assertEquals(10.0, v.direct(), 1e-9);
        assertEquals(2.0, v.viaK(), 1e-9);
    }

    @Test
    void edgeCase_directDistanceEqualToTheDetourIsNotAViolation() {
        // Three stops in a straight line: 0 to 2 is exactly 0 to 1 plus 1 to 2.
        DistanceMatrix line = DistanceMatrix.fromTable(new double[][] {
                {0, 1, 2},
                {1, 0, 1},
                {2, 1, 0}
        });
        assertTrue(MetricChecker.isMetric(line));
    }

    @Test
    void edgeCase_tinyRoundingDifferencesAreIgnored() {
        DistanceMatrix almost = DistanceMatrix.fromTable(new double[][] {
                {0, 1, 2 + 1e-12},
                {1, 0, 1},
                {2 + 1e-12, 1, 0}
        });
        assertTrue(MetricChecker.isMetric(almost));
    }

    @Test
    void edgeCase_zeroOneAndTwoStopsAreAlwaysFine() {
        assertTrue(MetricChecker.isMetric(DistanceMatrix.fromTable(new double[][] {})));
        assertTrue(MetricChecker.isMetric(DistanceMatrix.fromTable(new double[][] {{0}})));
        assertTrue(MetricChecker.isMetric(DistanceMatrix.fromTable(new double[][] {{0, 7}, {7, 0}})));
    }

    @Test
    void reportStopsAtTheRequestedLimit() {
        // Stops 1, 2 and 3 are each 1 away from stop 0 but 100 away from each other.
        // Every pair among them breaks the rule (100 directly, but only 1 + 1 = 2 via stop 0),
        // and each pair counts in both directions: 3 pairs x 2 directions = 6 violations.
        DistanceMatrix many = DistanceMatrix.fromTable(new double[][] {
                {0, 1, 1, 1},
                {1, 0, 100, 100},
                {1, 100, 0, 100},
                {1, 100, 100, 0}
        });
        assertEquals(2, MetricChecker.findViolations(many, 2).size());
        assertEquals(6, MetricChecker.findViolations(many, 1000).size());
    }

    @Test
    void nullMatrixIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MetricChecker.findViolations(null, 1));
    }
}
