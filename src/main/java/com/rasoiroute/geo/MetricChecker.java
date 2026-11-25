package com.rasoiroute.geo;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks the triangle rule: going from i straight to j must never be longer than
 * going from i to k and then on to j.
 *
 * The 2x guarantee of the route algorithm is only true when this rule holds,
 * so the app checks it before it makes any claim about route quality.
 */
public final class MetricChecker {

    /** Differences smaller than this are treated as rounding noise, not real violations. */
    public static final double TOLERANCE = 1e-9;

    private MetricChecker() {
        // static helper only
    }

    /** One broken triangle: going i to j directly is longer than the detour through k. */
    public record TriangleViolation(int i, int j, int k, double direct, double viaK) {
    }

    /**
     * Looks at every triple of stops and reports violations (at most maxReport of them).
     * This takes about n x n x n steps, which is fine for the sizes a delivery day has.
     */
    public static List<TriangleViolation> findViolations(DistanceMatrix m, int maxReport) {
        if (m == null) {
            throw new IllegalArgumentException("distance matrix is required");
        }
        List<TriangleViolation> found = new ArrayList<>();
        int n = m.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                double direct = m.get(i, j);
                for (int k = 0; k < n; k++) {
                    if (k == i || k == j) {
                        continue;
                    }
                    double detour = m.get(i, k) + m.get(k, j);
                    if (direct > detour + TOLERANCE) {
                        found.add(new TriangleViolation(i, j, k, direct, detour));
                        if (found.size() >= maxReport) {
                            return found;
                        }
                    }
                }
            }
        }
        return found;
    }

    public static boolean isMetric(DistanceMatrix m) {
        return findViolations(m, 1).isEmpty();
    }
}
