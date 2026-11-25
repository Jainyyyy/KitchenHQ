package com.rasoiroute.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.geo.DistanceMatrix;
import com.rasoiroute.geo.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PrimMstTest {

    // ---------- helpers ----------

    /** Stops on a straight line at the given positions: distance = difference in position. */
    private static DistanceMatrix onALine(double... positions) {
        int n = positions.length;
        double[][] table = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                table[i][j] = Math.abs(positions[i] - positions[j]);
            }
        }
        return DistanceMatrix.fromTable(table);
    }

    private static DistanceMatrix randomAhmedabad(int n, long seed) {
        Random random = new Random(seed);
        List<Location> points = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            points.add(new Location("P" + i, 22.95 + random.nextDouble() * 0.17, 72.45 + random.nextDouble() * 0.25));
        }
        return DistanceMatrix.fromLocations(points);
    }

    /** A completely different method (Kruskal's algorithm) used only to double-check Prim. */
    private static double kruskalWeight(DistanceMatrix m) {
        int n = m.size();
        List<double[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                edges.add(new double[] {m.get(i, j), i, j});
            }
        }
        edges.sort((a, b) -> Double.compare(a[0], b[0]));
        int[] group = new int[n];
        for (int i = 0; i < n; i++) {
            group[i] = i;
        }
        double total = 0;
        for (double[] e : edges) {
            int a = find(group, (int) e[1]);
            int b = find(group, (int) e[2]);
            if (a != b) {
                group[a] = b;
                total += e[0];
            }
        }
        return total;
    }

    private static int find(int[] group, int x) {
        while (group[x] != x) {
            group[x] = group[group[x]];
            x = group[x];
        }
        return x;
    }

    /** Every stop must reach the root by following parents, without going round in a loop. */
    private static void assertIsAValidTree(SpanningTree tree) {
        int n = tree.size();
        int edgeCount = 0;
        for (int v = 0; v < n; v++) {
            if (v == tree.root()) {
                assertEquals(-1, tree.parentOf(v));
                continue;
            }
            edgeCount++;
            int steps = 0;
            int current = v;
            while (current != tree.root()) {
                current = tree.parentOf(current);
                assertTrue(current >= 0, "stop " + v + " is not connected to the root");
                steps++;
                assertTrue(steps <= n, "loop found while walking up from stop " + v);
            }
        }
        assertEquals(n - 1, edgeCount);
    }

    // ---------- tests ----------

    @Test
    void stopsOnALineAreJoinedInAChain() {
        // Positions 0, 1, 3, 6: the cheapest tree is the chain 0-1-3-6 with roads 1 + 2 + 3 = 6.
        SpanningTree tree = PrimMst.build(onALine(0, 1, 3, 6), 0);
        assertEquals(6.0, tree.totalWeight(), 1e-9);
        assertEquals(-1, tree.parentOf(0));
        assertEquals(0, tree.parentOf(1));
        assertEquals(1, tree.parentOf(2));
        assertEquals(2, tree.parentOf(3));
        assertEquals(2.0, tree.weightToParent(2), 1e-9);
    }

    @Test
    void cornersOfASquareNeedThreeSides() {
        double s = Math.sqrt(2);
        DistanceMatrix square = DistanceMatrix.fromTable(new double[][] {
                {0, 1, s, 1},
                {1, 0, 1, s},
                {s, 1, 0, 1},
                {1, s, 1, 0}
        });
        assertEquals(3.0, PrimMst.build(square, 0).totalWeight(), 1e-9);
    }

    @Test
    void agreesWithKruskalOnManyRandomInstances() {
        for (int trial = 0; trial < 40; trial++) {
            int n = 2 + trial % 25;
            DistanceMatrix m = randomAhmedabad(n, 1000 + trial);
            SpanningTree tree = PrimMst.build(m, 0);
            assertEquals(kruskalWeight(m), tree.totalWeight(), 1e-9, "trial " + trial + ", n=" + n);
            assertIsAValidTree(tree);
        }
    }

    @Test
    void agreesWithKruskalOnALargerInstance() {
        DistanceMatrix m = randomAhmedabad(300, 77);
        assertEquals(kruskalWeight(m), PrimMst.build(m, 0).totalWeight(), 1e-6);
    }

    @Test
    void theStartingStopDoesNotChangeTheTotalLength() {
        DistanceMatrix m = randomAhmedabad(20, 5);
        double reference = PrimMst.build(m, 0).totalWeight();
        for (int root = 1; root < 20; root++) {
            SpanningTree tree = PrimMst.build(m, root);
            assertEquals(reference, tree.totalWeight(), 1e-9, "root " + root);
            assertIsAValidTree(tree);
        }
    }

    @Test
    void childrenAreListedInIncreasingOrder() {
        // Starting from the middle stop of 0, 1, 3, 6 gives stop 1 two children: 0 and 2.
        SpanningTree tree = PrimMst.build(onALine(0, 1, 3, 6), 1);
        assertEquals(List.of(0, 2), tree.childrenOf(1));
        assertEquals(List.of(3), tree.childrenOf(2));
        assertTrue(tree.childrenOf(3).isEmpty());
    }

    @Test
    void theTreeIsNeverLongerThanAnyRoundTripThroughAllStops() {
        // This is the fact the 2x guarantee is built on: remove one road from a round trip and
        // you get a tree, so the cheapest tree cannot be longer than any round trip.
        DistanceMatrix m = randomAhmedabad(8, 31);
        double treeWeight = PrimMst.build(m, 0).totalWeight();
        Random random = new Random(4);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            order.add(i);
        }
        for (int trial = 0; trial < 500; trial++) {
            Collections.shuffle(order, random);
            assertTrue(treeWeight <= m.tourLength(order) + 1e-9, "trial " + trial);
        }
    }

    @Test
    void edgeCase_oneStopHasAnEmptyTreeOfLengthZero() {
        SpanningTree tree = PrimMst.build(onALine(5), 0);
        assertEquals(0.0, tree.totalWeight(), 0.0);
        assertEquals(1, tree.size());
        assertEquals(-1, tree.parentOf(0));
    }

    @Test
    void edgeCase_twoStopsAreJoinedByOneRoad() {
        SpanningTree tree = PrimMst.build(onALine(2, 9), 0);
        assertEquals(7.0, tree.totalWeight(), 1e-9);
        assertEquals(0, tree.parentOf(1));
    }

    @Test
    void edgeCase_stopsAtTheSamePlaceCostNothing() {
        // Stops 0 and 1 are at the same position, so the road between them has length 0.
        SpanningTree tree = PrimMst.build(onALine(4, 4, 10), 0);
        assertEquals(6.0, tree.totalWeight(), 1e-9);
        assertIsAValidTree(tree);
    }

    @Test
    void invalidInputsAreRejected() {
        DistanceMatrix m = onALine(0, 1, 2);
        assertThrows(IllegalArgumentException.class, () -> PrimMst.build(null, 0));
        assertThrows(IllegalArgumentException.class, () -> PrimMst.build(m, -1));
        assertThrows(IllegalArgumentException.class, () -> PrimMst.build(m, 3));
        assertThrows(IllegalArgumentException.class,
                () -> PrimMst.build(DistanceMatrix.fromTable(new double[][] {}), 0));
    }
}
