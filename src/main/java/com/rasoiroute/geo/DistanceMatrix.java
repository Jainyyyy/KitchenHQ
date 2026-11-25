package com.rasoiroute.geo;

import java.util.List;

/**
 * A table of distances: row i, column j holds the distance from stop i to stop j.
 * Stop 0, 1, 2 ... follow the order of the list it was built from.
 *
 * A valid table has: zeros on the diagonal, no negative numbers, and the same distance
 * both ways (distance i to j equals distance j to i).
 */
public final class DistanceMatrix {

    private static final double SYMMETRY_TOLERANCE = 1e-9;

    private final double[][] d;

    private DistanceMatrix(double[][] d) {
        this.d = d;
    }

    /** Builds the table from real map points, using the straight-line (haversine) distance. */
    public static DistanceMatrix fromLocations(List<Location> locations) {
        if (locations == null) {
            throw new IllegalArgumentException("locations are required");
        }
        int n = locations.size();
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double km = Haversine.distanceKm(locations.get(i), locations.get(j));
                d[i][j] = km;
                d[j][i] = km;
            }
        }
        return new DistanceMatrix(d);
    }

    /** Builds the table from numbers someone typed or uploaded, checking that they make sense. */
    public static DistanceMatrix fromTable(double[][] table) {
        if (table == null) {
            throw new IllegalArgumentException("distance table is required");
        }
        int n = table.length;
        double[][] copy = new double[n][];
        for (int i = 0; i < n; i++) {
            if (table[i] == null || table[i].length != n) {
                throw new IllegalArgumentException("Distance table must be square (same number of rows and columns)");
            }
            copy[i] = table[i].clone();
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = copy[i][j];
                if (!Double.isFinite(v)) {
                    throw new IllegalArgumentException("Distance table has a value that is not a number at row "
                            + i + ", column " + j);
                }
                if (v < 0) {
                    throw new IllegalArgumentException("Distance table has a negative distance at row "
                            + i + ", column " + j);
                }
            }
            if (copy[i][i] != 0.0) {
                throw new IllegalArgumentException("Distance from a stop to itself must be 0 (row " + i + ")");
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(copy[i][j] - copy[j][i]) > SYMMETRY_TOLERANCE) {
                    throw new IllegalArgumentException("Distance from " + i + " to " + j
                            + " is different from " + j + " to " + i);
                }
            }
        }
        return new DistanceMatrix(copy);
    }

    public int size() {
        return d.length;
    }

    public double get(int from, int to) {
        return d[from][to];
    }

    /** Total length of a route that visits the stops in the given order and returns to the first. */
    public double tourLength(List<Integer> order) {
        if (order == null || order.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 0; i < order.size(); i++) {
            int from = order.get(i);
            int to = order.get((i + 1) % order.size());
            total += d[from][to];
        }
        return total;
    }
}
