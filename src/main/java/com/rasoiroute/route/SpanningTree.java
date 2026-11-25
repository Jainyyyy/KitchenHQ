package com.rasoiroute.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A spanning tree: a set of roads that joins every stop, with no loops.
 * Each stop except the root has one "parent": the stop it was joined to.
 */
public final class SpanningTree {

    private final int root;
    private final int[] parent;
    private final double[] weightToParent;
    private final double totalWeight;
    private final List<List<Integer>> children;

    SpanningTree(int root, int[] parent, double[] weightToParent, double totalWeight) {
        this.root = root;
        this.parent = parent.clone();
        this.weightToParent = weightToParent.clone();
        this.totalWeight = totalWeight;

        int n = parent.length;
        List<List<Integer>> kids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            kids.add(new ArrayList<>());
        }
        for (int v = 0; v < n; v++) {          // going upward in v keeps each child list sorted
            if (parent[v] >= 0) {
                kids.get(parent[v]).add(v);
            }
        }
        this.children = kids;
    }

    public int size() {
        return parent.length;
    }

    public int root() {
        return root;
    }

    /** The stop this one was joined to, or -1 for the root. */
    public int parentOf(int stop) {
        return parent[stop];
    }

    /** The length of the road joining this stop to its parent (0 for the root). */
    public double weightToParent(int stop) {
        return weightToParent[stop];
    }

    /** Total length of all roads in the tree. */
    public double totalWeight() {
        return totalWeight;
    }

    /** The stops joined directly below this one, in increasing stop number. */
    public List<Integer> childrenOf(int stop) {
        return Collections.unmodifiableList(children.get(stop));
    }
}
