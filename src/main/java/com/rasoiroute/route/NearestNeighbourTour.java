package com.rasoiroute.route;

import com.rasoiroute.geo.DistanceMatrix;
import java.util.ArrayList;
import java.util.List;

/**
 * Baseline 1: "always go to the closest stop you have not visited yet".
 * Simple and usually decent, but it has NO guarantee: on unlucky inputs it can be far worse.
 * Takes about n x n steps.
 */
public final class NearestNeighbourTour {

    private NearestNeighbourTour() {
        // static helper only
    }

    public static Tour solve(DistanceMatrix distances, int root) {
        TourInputs.check(distances, root);

        int n = distances.size();
        boolean[] visited = new boolean[n];
        List<Integer> order = new ArrayList<>(n);

        int current = root;
        visited[current] = true;
        order.add(current);

        for (int step = 1; step < n; step++) {
            int nearest = -1;
            double nearestDistance = Double.POSITIVE_INFINITY;
            for (int candidate = 0; candidate < n; candidate++) {
                if (!visited[candidate]) {
                    double d = distances.get(current, candidate);
                    if (d < nearestDistance) {      // "<" keeps the smaller stop number on a tie
                        nearestDistance = d;
                        nearest = candidate;
                    }
                }
            }
            visited[nearest] = true;
            order.add(nearest);
            current = nearest;
        }
        return new Tour(order, distances.tourLength(order));
    }
}
