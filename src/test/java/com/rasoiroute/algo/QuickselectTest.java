package com.rasoiroute.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class QuickselectTest {

    private static final Comparator<Integer> NATURAL = Comparator.naturalOrder();

    /** The slow, obviously-correct answer: sort everything and take the first k. */
    private static List<Integer> bySorting(List<Integer> items, int k) {
        List<Integer> sorted = new ArrayList<>(items);
        Collections.sort(sorted);
        return new ArrayList<>(sorted.subList(0, Math.min(k, sorted.size())));
    }

    private static List<Integer> sortedCopy(List<Integer> items) {
        List<Integer> copy = new ArrayList<>(items);
        Collections.sort(copy);
        return copy;
    }

    @Test
    void matchesFullSortingOnManyRandomInputs() {
        Random data = new Random(42);
        Random pivots = new Random(7);
        for (int trial = 0; trial < 300; trial++) {
            int n = 1 + data.nextInt(60);
            List<Integer> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                items.add(data.nextInt(1000));
            }
            int k = data.nextInt(n + 3);
            List<Integer> got = Quickselect.firstK(items, k, NATURAL, pivots);
            assertEquals(bySorting(items, k), sortedCopy(got), "trial " + trial + ", n=" + n + ", k=" + k);
        }
    }

    @Test
    void doesNotChangeTheCallersList() {
        List<Integer> items = new ArrayList<>(List.of(5, 3, 9, 1, 7));
        List<Integer> before = new ArrayList<>(items);
        Quickselect.firstK(items, 2, NATURAL, new Random(1));
        assertEquals(before, items);
    }

    @Test
    void worksOnAlreadySortedAndReverseSortedInput() {
        List<Integer> ascending = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            ascending.add(i);
        }
        List<Integer> descending = new ArrayList<>(ascending);
        Collections.reverse(descending);
        assertEquals(bySorting(ascending, 10), sortedCopy(Quickselect.firstK(ascending, 10, NATURAL, new Random(3))));
        assertEquals(bySorting(descending, 10), sortedCopy(Quickselect.firstK(descending, 10, NATURAL, new Random(3))));
    }

    @Test
    void handlesManyDuplicateValues() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            items.add(i % 3);   // only the values 0, 1, 2
        }
        for (int k : new int[] {1, 99, 100, 101, 250, 299}) {
            assertEquals(bySorting(items, k), sortedCopy(Quickselect.firstK(items, k, NATURAL, new Random(5))), "k=" + k);
        }
    }

    @Test
    void edgeCase_allItemsEqual() {
        List<Integer> items = Collections.nCopies(1000, 7);
        List<Integer> got = Quickselect.firstK(items, 10, NATURAL, new Random(2));
        assertEquals(10, got.size());
        assertTrue(got.stream().allMatch(v -> v == 7));
    }

    @Test
    void edgeCase_kIsZeroGivesEmptyList() {
        assertTrue(Quickselect.firstK(List.of(3, 1, 2), 0, NATURAL, new Random(1)).isEmpty());
    }

    @Test
    void edgeCase_kLargerThanSizeGivesEverything() {
        List<Integer> got = Quickselect.firstK(List.of(3, 1, 2), 10, NATURAL, new Random(1));
        assertEquals(List.of(1, 2, 3), sortedCopy(got));
    }

    @Test
    void edgeCase_emptyAndSingleItemLists() {
        assertTrue(Quickselect.firstK(new ArrayList<Integer>(), 5, NATURAL, new Random(1)).isEmpty());
        assertEquals(List.of(9), Quickselect.firstK(List.of(9), 1, NATURAL, new Random(1)));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Quickselect.firstK(List.of(1, 2), -1, NATURAL, new Random()));
        assertThrows(IllegalArgumentException.class, () -> Quickselect.firstK(null, 1, NATURAL, new Random()));
        assertThrows(IllegalArgumentException.class, () -> Quickselect.firstK(List.of(1), 1, null, new Random()));
        assertThrows(IllegalArgumentException.class, () -> Quickselect.firstK(List.of(1), 1, NATURAL, null));
    }

    @Test
    void usesAboutLinearNumberOfComparisons() {
        int n = 100_000;
        Random data = new Random(11);
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(data.nextInt());
        }
        long[] comparisons = {0};
        Comparator<Integer> counting = (a, b) -> {
            comparisons[0]++;
            return Integer.compare(a, b);
        };
        Quickselect.firstK(items, 50, counting, new Random(13));
        // Full sorting needs about n * log2(n) = 1.7 million comparisons here.
        // Quickselect should need only a few times n.
        assertTrue(comparisons[0] < 10L * n, "comparisons were " + comparisons[0]);
    }
}
