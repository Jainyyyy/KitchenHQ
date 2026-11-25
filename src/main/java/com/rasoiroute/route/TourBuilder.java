package com.rasoiroute.route;

import com.rasoiroute.geo.DistanceMatrix;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Builds a round trip that is guaranteed to be at most TWICE as long as the best possible one.
 * This is the classic "MST 2-approximation" for the travelling salesman problem.
 *
 * Steps:
 *   1. Build the minimum spanning tree (shortest set of roads joining every stop).
 *   2. Walk around the tree, always going down into a branch before moving on (depth-first).
 *      Write down each stop the FIRST time we reach it.   -> this is the "preorder" list.
 *   3. Visit the stops in that list order and return to the start.
 *
 * Why the promise holds (needs distances that obey the triangle rule: a direct road is never
 * longer than a detour):
 *   - Best round trip >= tree. Remove one road from the best round trip and you get a tree,
 *     and the minimum tree is no longer than any tree.
 *   - A full walk around the tree uses every road twice, so it is 2 x tree long.
 *   - Our route is that walk with repeated stops skipped. Skipping a stop replaces a detour by
 *     a direct road, which by the triangle rule is never longer.
 *   - So route <= 2 x tree <= 2 x best.
 */
public final class TourBuilder {

    private TourBuilder() {
        // static helper only
    }

    public static ApproxTour build(DistanceMatrix distances, int root) {
        TourInputs.check(distances, root);

        SpanningTree tree = PrimMst.build(distances, root);
        List<Integer> order = preorder(tree);
        double length = distances.tourLength(order);

        return new ApproxTour(new Tour(order, length), tree.totalWeight());
    }

    /**
     * Lists the stops of a tree in depth-first "first visit" order, starting at the root.
     *
     * A stack (last in, first out) does the walking, so a very long chain of stops cannot
     * overflow the program's call stack the way a recursive method could.
     */
    static List<Integer> preorder(SpanningTree tree) {
        List<Integer> order = new ArrayList<>(tree.size());
        Deque<Integer> toVisit = new ArrayDeque<>();
        toVisit.push(tree.root());

        while (!toVisit.isEmpty()) {
            int stop = toVisit.pop();
            order.add(stop);

            // Push the children from the LAST to the FIRST, so that the smallest-numbered
            // child comes off the stack first. This makes the route the same every time.
            List<Integer> children = tree.childrenOf(stop);
            for (int i = children.size() - 1; i >= 0; i--) {
                toVisit.push(children.get(i));
            }
        }
        return order;
    }
}
