package com.rasoiroute.route;

import com.rasoiroute.geo.DistanceMatrix;

/** Checks shared by every route-building class, so each one rejects bad input in the same way. */
final class TourInputs {

    private TourInputs() {
        // static helper only
    }

    static void check(DistanceMatrix distances, int root) {
        if (distances == null) {
            throw new IllegalArgumentException("distance matrix is required");
        }
        int n = distances.size();
        if (n == 0) {
            throw new IllegalArgumentException("There are no stops to visit");
        }
        if (root < 0 || root >= n) {
            throw new IllegalArgumentException("start stop " + root + " is not valid (0.." + (n - 1) + ")");
        }
    }
}
