package com.rasoiroute.route;

import com.rasoiroute.geo.DistanceMatrix;
import java.util.ArrayList;
import java.util.List;

/**
 * Baseline 2: "do it the way a person without a plan would": visit the stops in the order
 * they were numbered (the order the orders arrived), then come home.
 * This is what the business does today if nobody plans the route.
 */
public final class ArrivalOrderTour {

    private ArrivalOrderTour() {
        // static helper only
    }

    public static Tour solve(DistanceMatrix distances, int root) {
        TourInputs.check(distances, root);

        int n = distances.size();
        List<Integer> order = new ArrayList<>(n);
        order.add(root);
        for (int stop = 0; stop < n; stop++) {
            if (stop != root) {
                order.add(stop);
            }
        }
        return new Tour(order, distances.tourLength(order));
    }
}
