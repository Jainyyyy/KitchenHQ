package com.rasoiroute.route;

import com.rasoiroute.geo.DistanceMatrix;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The truly shortest round trip, found exactly (the Held-Karp method).
 * It is far too slow for big inputs (it doubles in work for every extra stop), so it is used
 * only as a yardstick: it tells us how close the fast 2-approximation really gets.
 *
 * Idea (dynamic programming): remember, for every SET of stops already visited and every stop we
 * could be standing at, the shortest way to have done exactly that. Bigger sets are built from
 * smaller ones, so each answer is worked out once and reused.
 *
 * Work: about n x n x 2^n steps. Memory: about n x 2^n numbers. Hence the small limit below.
 */
public final class ExactTsp {

    /** Most stops allowed (kitchen included). 17 stops need about 12 MB and well under a second. */
    public static final int MAX_STOPS = 17;

    private ExactTsp() {
        // static helper only
    }

    public static Tour solve(DistanceMatrix distances, int root) {
        TourInputs.check(distances, root);
        int n = distances.size();
        if (n > MAX_STOPS) {
            throw new IllegalArgumentException("Exact search is limited to " + MAX_STOPS
                    + " stops but " + n + " were given");
        }
        if (n == 1) {
            return new Tour(List.of(root), 0.0);
        }

        // "others" are all stops except the start. We number them 0..m-1 so a set of them
        // can be stored as the bits of one whole number (bit j set = stop others[j] visited).
        int m = n - 1;
        int[] others = new int[m];
        int next = 0;
        for (int stop = 0; stop < n; stop++) {
            if (stop != root) {
                others[next++] = stop;
            }
        }

        int sets = 1 << m;                 // how many different sets of visited stops exist
        int full = sets - 1;               // the set where every stop is visited
        // best[set * m + last] = shortest way to start at root, visit exactly "set", end at "last"
        double[] best = new double[sets * m];
        int[] before = new int[sets * m];  // the stop we stood at just before "last" (to rebuild the route)
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Arrays.fill(before, -1);

        for (int j = 0; j < m; j++) {      // sets with one stop: go straight there from the start
            best[(1 << j) * m + j] = distances.get(root, others[j]);
        }

        // Smaller numbers are always smaller sets, so going upward means every set is finished
        // before any bigger set needs it.
        for (int set = 1; set <= full; set++) {
            for (int last = 0; last < m; last++) {
                if ((set & (1 << last)) == 0) {
                    continue;              // "last" must be inside the set
                }
                double soFar = best[set * m + last];
                if (soFar == Double.POSITIVE_INFINITY) {
                    continue;
                }
                for (int to = 0; to < m; to++) {
                    if ((set & (1 << to)) != 0) {
                        continue;          // already visited
                    }
                    int biggerSet = set | (1 << to);
                    double candidate = soFar + distances.get(others[last], others[to]);
                    int slot = biggerSet * m + to;
                    if (candidate < best[slot]) {
                        best[slot] = candidate;
                        before[slot] = last;
                    }
                }
            }
        }

        // Close the loop: from the final stop, go home. Pick the cheapest ending.
        double bestTotal = Double.POSITIVE_INFINITY;
        int bestLast = -1;
        for (int last = 0; last < m; last++) {
            double total = best[full * m + last] + distances.get(others[last], root);
            if (total < bestTotal) {
                bestTotal = total;
                bestLast = last;
            }
        }

        // Walk backwards through "before" to recover the route.
        List<Integer> backwards = new ArrayList<>(m);
        int set = full;
        int last = bestLast;
        while (last != -1) {
            backwards.add(others[last]);
            int previous = before[set * m + last];
            set = set & ~(1 << last);
            last = previous;
        }
        Collections.reverse(backwards);

        List<Integer> order = new ArrayList<>(n);
        order.add(root);
        order.addAll(backwards);
        return new Tour(order, distances.tourLength(order));
    }
}
