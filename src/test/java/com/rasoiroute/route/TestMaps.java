package com.rasoiroute.route;

import com.rasoiroute.geo.DistanceMatrix;
import com.rasoiroute.geo.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Small helpers shared by the route tests. They are for testing only and are not part of the app. */
final class TestMaps {

    private TestMaps() {
    }

    /** n random points inside the Ahmedabad area, with real straight-line (haversine) distances. */
    static DistanceMatrix randomAhmedabad(int n, long seed) {
        Random random = new Random(seed);
        List<Location> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points.add(new Location("P" + i, 22.95 + random.nextDouble() * 0.17, 72.45 + random.nextDouble() * 0.25));
        }
        return DistanceMatrix.fromLocations(points);
    }

    /** Stops on a straight line at the given positions: distance = difference in position. */
    static DistanceMatrix onALine(double... positions) {
        int n = positions.length;
        double[][] table = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                table[i][j] = Math.abs(positions[i] - positions[j]);
            }
        }
        return DistanceMatrix.fromTable(table);
    }

    /**
     * The most obviously-correct way to find the best round trip: try EVERY order of the stops
     * and keep the shortest. Only usable for about 9 stops or fewer. Used to double-check ExactTsp.
     */
    static double bruteForceBest(DistanceMatrix d, int root) {
        int n = d.size();
        List<Integer> rest = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (i != root) {
                rest.add(i);
            }
        }
        double[] best = {Double.POSITIVE_INFINITY};
        List<Integer> path = new ArrayList<>();
        path.add(root);
        permute(d, rest, new boolean[rest.size()], path, best);
        return best[0];
    }

    private static void permute(DistanceMatrix d, List<Integer> rest, boolean[] used, List<Integer> path, double[] best) {
        if (path.size() == rest.size() + 1) {
            best[0] = Math.min(best[0], d.tourLength(path));
            return;
        }
        for (int i = 0; i < rest.size(); i++) {
            if (!used[i]) {
                used[i] = true;
                path.add(rest.get(i));
                permute(d, rest, used, path, best);
                path.remove(path.size() - 1);
                used[i] = false;
            }
        }
    }
}
