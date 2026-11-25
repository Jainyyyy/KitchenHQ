package com.rasoiroute.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rasoiroute.geo.DistanceMatrix;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TourBuilderTest {

    private static final double EPS = 1e-9;

    private static void assertVisitsEveryStopOnce(List<Integer> order, int n) {
        assertEquals(n, order.size(), "route must list every stop");
        Set<Integer> seen = new HashSet<>(order);
        assertEquals(n, seen.size(), "no stop may appear twice");
        for (int stop : order) {
            assertTrue(stop >= 0 && stop < n, "stop number out of range: " + stop);
        }
    }

    @Test
    void startsAtTheKitchenAndVisitsEveryStopExactlyOnce() {
        DistanceMatrix m = TestMaps.randomAhmedabad(30, 1);
        ApproxTour result = TourBuilder.build(m, 0);
        assertEquals(0, (int) result.tour().order().get(0));
        assertVisitsEveryStopOnce(result.tour().order(), 30);
    }

    @Test
    void reportedLengthIsTheRealLengthOfTheRoute() {
        DistanceMatrix m = TestMaps.randomAhmedabad(25, 2);
        ApproxTour result = TourBuilder.build(m, 0);
        assertEquals(m.tourLength(result.tour().order()), result.tour().length(), EPS);
    }

    @Test
    void lineExampleWorkedByHand() {
        // Kitchen at 0 and stops at 1, 2, 3 on a line. The tree is the chain 0-1-2-3 (length 3).
        // The walk is 0,1,2,3 and back: 1 + 1 + 1 + 3 = 6, which is exactly twice the tree.
        ApproxTour result = TourBuilder.build(TestMaps.onALine(0, 1, 2, 3), 0);
        assertEquals(List.of(0, 1, 2, 3), result.tour().order());
        assertEquals(6.0, result.tour().length(), EPS);
        assertEquals(3.0, result.mstWeight(), EPS);
        assertEquals(2.0, result.ratioToMst(), EPS);
        assertTrue(result.withinGuarantee());
    }

    @Test
    void neverLongerThanTwiceTheTreeOnManyRandomMaps() {
        Random random = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            int n = 2 + random.nextInt(59);
            ApproxTour result = TourBuilder.build(TestMaps.randomAhmedabad(n, 1000 + trial), 0);
            assertTrue(result.withinGuarantee(),
                    "trial " + trial + " (n=" + n + ") ratio to tree was " + result.ratioToMst());
        }
    }

    @Test
    void neverLongerThanTwiceTheBestRouteOnSmallMaps() {
        // For up to 8 stops we can find the true best route by trying every order,
        // so here the 2x promise is checked against the REAL optimum, not just the tree.
        Random random = new Random(11);
        for (int trial = 0; trial < 300; trial++) {
            int n = 2 + random.nextInt(7);
            DistanceMatrix m = TestMaps.randomAhmedabad(n, 5000 + trial);
            double best = TestMaps.bruteForceBest(m, 0);
            double ours = TourBuilder.build(m, 0).tour().length();
            assertTrue(ours >= best - EPS, "cannot be shorter than the best route (trial " + trial + ")");
            assertTrue(ours <= 2.0 * best + EPS,
                    "trial " + trial + ": ours " + ours + " best " + best);
        }
    }

    @Test
    void singleStopIsATripOfLengthZero() {
        ApproxTour result = TourBuilder.build(TestMaps.onALine(0), 0);
        assertEquals(List.of(0), result.tour().order());
        assertEquals(0.0, result.tour().length(), EPS);
        assertEquals(1.0, result.ratioToMst(), EPS);
        assertTrue(result.withinGuarantee());
    }

    @Test
    void twoStopsGoThereAndBack() {
        ApproxTour result = TourBuilder.build(TestMaps.onALine(0, 4), 0);
        assertEquals(List.of(0, 1), result.tour().order());
        assertEquals(8.0, result.tour().length(), EPS);
    }

    @Test
    void canStartFromAnyStop() {
        DistanceMatrix m = TestMaps.randomAhmedabad(20, 3);
        for (int root = 0; root < 20; root += 7) {
            ApproxTour result = TourBuilder.build(m, root);
            assertEquals(root, (int) result.tour().order().get(0));
            assertVisitsEveryStopOnce(result.tour().order(), 20);
            assertTrue(result.withinGuarantee());
        }
    }

    @Test
    void sameInputAlwaysGivesTheSameRoute() {
        DistanceMatrix m = TestMaps.randomAhmedabad(40, 4);
        assertEquals(TourBuilder.build(m, 0).tour().order(), TourBuilder.build(m, 0).tour().order());
    }

    @Test
    void stopsAtTheSamePlaceGiveLengthZero() {
        // Every stop in the same spot: all distances are 0.
        double[][] zeros = new double[6][6];
        ApproxTour result = TourBuilder.build(DistanceMatrix.fromTable(zeros), 0);
        assertEquals(0.0, result.tour().length(), EPS);
        assertEquals(1.0, result.ratioToMst(), EPS);
        assertTrue(result.withinGuarantee());
    }

    @Test
    void parentIsAlwaysVisitedBeforeItsChildren() {
        DistanceMatrix m = TestMaps.randomAhmedabad(50, 5);
        SpanningTree tree = PrimMst.build(m, 0);
        List<Integer> order = TourBuilder.preorder(tree);
        int[] position = new int[50];
        for (int i = 0; i < order.size(); i++) {
            position[order.get(i)] = i;
        }
        for (int v = 1; v < 50; v++) {
            assertTrue(position[tree.parentOf(v)] < position[v], "stop " + v + " came before its parent");
        }
    }

    @Test
    void aVeryLongChainOfStopsDoesNotCrash() {
        // A tree shaped like one long chain 0 -> 1 -> 2 -> ... -> 99,999.
        // A method that calls itself once per stop would overflow the stack here.
        int n = 100_000;
        int[] parent = new int[n];
        double[] weight = new double[n];
        parent[0] = -1;
        for (int v = 1; v < n; v++) {
            parent[v] = v - 1;
            weight[v] = 1.0;
        }
        SpanningTree chain = new SpanningTree(0, parent, weight, n - 1.0);
        List<Integer> order = TourBuilder.preorder(chain);
        assertEquals(n, order.size());
        assertEquals(n - 1, (int) order.get(n - 1));
    }

    @Test
    void anAlmostStraightLineOf1500StopsWorksEndToEnd() {
        double[] positions = new double[1500];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = i;
        }
        ApproxTour result = TourBuilder.build(TestMaps.onALine(positions), 0);
        assertVisitsEveryStopOnce(result.tour().order(), 1500);
        assertEquals(2.0 * 1499, result.tour().length(), EPS);
    }

    @Test
    void rejectsBadInput() {
        DistanceMatrix m = TestMaps.randomAhmedabad(5, 6);
        assertThrows(IllegalArgumentException.class, () -> TourBuilder.build(null, 0));
        assertThrows(IllegalArgumentException.class, () -> TourBuilder.build(m, -1));
        assertThrows(IllegalArgumentException.class, () -> TourBuilder.build(m, 5));
        assertThrows(IllegalArgumentException.class,
                () -> TourBuilder.build(DistanceMatrix.fromTable(new double[0][0]), 0));
    }

    @Test
    void guaranteeCheckNoticesARouteThatIsTooLong() {
        // A made-up result whose route is 3 times the tree. It must NOT pass the check.
        ApproxTour fake = new ApproxTour(new Tour(List.of(0, 1), 30.0), 10.0);
        assertFalse(fake.withinGuarantee());
        assertEquals(3.0, fake.ratioToMst(), EPS);
    }
}
