package com.rasoiroute.route;

import com.rasoiroute.algo.IndexedMinHeap;
import com.rasoiroute.geo.DistanceMatrix;

import java.util.Arrays;

/**
 * Prim's algorithm: finds the cheapest set of roads that joins every stop.
 *
 * The idea in plain words:
 *   1. Start with one stop (the root) as a tiny tree.
 *   2. Look at all stops not yet in the tree. For each, remember the shortest road that
 *      connects it to the tree (its "key") and which tree stop that road comes from.
 *   3. Take the stop with the smallest key (the heap gives it instantly), add it to the tree.
 *   4. Adding it may offer shorter roads to the others, so update their keys.
 *   5. Repeat until every stop is in the tree.
 *
 * It uses the min-heap from this project. With n stops the work is about n x n x log(n).
 */
public final class PrimMst {

    private PrimMst() {
        // static helper only
    }

    public static SpanningTree build(DistanceMatrix distances, int root) {
        if (distances == null) {
            throw new IllegalArgumentException("distance matrix is required");
        }
        int n = distances.size();
        if (n == 0) {
            throw new IllegalArgumentException("There are no stops to connect");
        }
        if (root < 0 || root >= n) {
            throw new IllegalArgumentException("root " + root + " is not a valid stop (0.." + (n - 1) + ")");
        }

        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        double[] weightToParent = new double[n];
        double total = 0.0;

        // Everyone starts "infinitely far" from the tree, except the root, which costs nothing.
        IndexedMinHeap heap = new IndexedMinHeap(n);
        for (int v = 0; v < n; v++) {
            heap.insert(v, v == root ? 0.0 : Double.POSITIVE_INFINITY);
        }

        while (!heap.isEmpty()) {
            double cost = heap.minKey();
            int u = heap.extractMin();           // u joins the tree
            if (u != root) {
                weightToParent[u] = cost;
                total += cost;
            }
            for (int v = 0; v < n; v++) {
                if (heap.contains(v)) {          // v is still outside the tree
                    double d = distances.get(u, v);
                    if (d < heap.keyOf(v)) {     // a shorter road to v has appeared
                        heap.decreaseKey(v, d);
                        parent[v] = u;
                    }
                }
            }
        }
        return new SpanningTree(root, parent, weightToParent, total);
    }
}
