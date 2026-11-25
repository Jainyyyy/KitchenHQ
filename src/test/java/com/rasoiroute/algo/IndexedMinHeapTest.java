package com.rasoiroute.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IndexedMinHeapTest {

    @Test
    void itemsComeOutInIncreasingKeyOrder() {
        Random random = new Random(8);
        int n = 500;
        IndexedMinHeap heap = new IndexedMinHeap(n);
        List<Double> keys = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double k = random.nextDouble() * 1000;
            keys.add(k);
            heap.insert(i, k);
            assertTrue(heap.isValidHeap());
        }
        Collections.sort(keys);
        for (int i = 0; i < n; i++) {
            assertEquals(keys.get(i), heap.minKey(), 0.0);
            heap.extractMin();
            assertTrue(heap.isValidHeap());
        }
        assertTrue(heap.isEmpty());
    }

    @Test
    void decreaseKeyMovesAnItemToTheTop() {
        IndexedMinHeap heap = new IndexedMinHeap(5);
        heap.insert(0, 10);
        heap.insert(1, 20);
        heap.insert(2, 30);
        heap.insert(3, 40);
        heap.insert(4, 50);
        heap.decreaseKey(4, 1);
        assertTrue(heap.isValidHeap());
        assertEquals(4, heap.extractMin());
        assertEquals(0, heap.extractMin());
    }

    @Test
    void manyRandomDecreasesKeepTheHeapValidAndOrdered() {
        Random random = new Random(21);
        int n = 300;
        IndexedMinHeap heap = new IndexedMinHeap(n);
        double[] expected = new double[n];
        for (int i = 0; i < n; i++) {
            expected[i] = 1000 + random.nextInt(1000);
            heap.insert(i, expected[i]);
        }
        for (int step = 0; step < 2000; step++) {
            int item = random.nextInt(n);
            double lower = expected[item] - random.nextInt(50);
            heap.decreaseKey(item, lower);
            expected[item] = lower;
        }
        assertTrue(heap.isValidHeap());
        double previous = Double.NEGATIVE_INFINITY;
        while (!heap.isEmpty()) {
            double k = heap.minKey();
            int item = heap.extractMin();
            assertEquals(expected[item], k, 0.0);
            assertTrue(k >= previous);
            previous = k;
        }
    }

    @Test
    void equalKeysAreBrokenBySmallerItemNumber() {
        IndexedMinHeap heap = new IndexedMinHeap(4);
        heap.insert(3, 5);
        heap.insert(1, 5);
        heap.insert(2, 5);
        heap.insert(0, 5);
        assertEquals(0, heap.extractMin());
        assertEquals(1, heap.extractMin());
        assertEquals(2, heap.extractMin());
        assertEquals(3, heap.extractMin());
    }

    @Test
    void containsAndKeyOfFollowTheItemsInAndOut() {
        IndexedMinHeap heap = new IndexedMinHeap(3);
        assertFalse(heap.contains(1));
        heap.insert(1, 7.5);
        assertTrue(heap.contains(1));
        assertEquals(7.5, heap.keyOf(1), 0.0);
        assertEquals(1, heap.size());
        heap.extractMin();
        assertFalse(heap.contains(1));
        assertEquals(0, heap.size());
    }

    @Test
    void edgeCase_infinityIsAllowedAsAKey() {
        IndexedMinHeap heap = new IndexedMinHeap(3);
        heap.insert(0, Double.POSITIVE_INFINITY);
        heap.insert(1, 4);
        heap.insert(2, Double.POSITIVE_INFINITY);
        assertEquals(1, heap.extractMin());
        assertEquals(0, heap.extractMin());   // both infinite: smaller number first
        assertEquals(2, heap.extractMin());
    }

    @Test
    void edgeCase_emptyHeapAndSingleItem() {
        IndexedMinHeap empty = new IndexedMinHeap(0);
        assertTrue(empty.isEmpty());
        assertThrows(NoSuchElementException.class, empty::extractMin);
        assertThrows(NoSuchElementException.class, empty::minKey);

        IndexedMinHeap one = new IndexedMinHeap(1);
        one.insert(0, 3);
        assertEquals(0, one.extractMin());
        assertTrue(one.isEmpty());
    }

    @Test
    void invalidOperationsAreRejected() {
        IndexedMinHeap heap = new IndexedMinHeap(3);
        heap.insert(0, 5);
        assertThrows(IllegalArgumentException.class, () -> heap.insert(0, 6));            // already inside
        assertThrows(IllegalArgumentException.class, () -> heap.insert(3, 1));            // out of range
        assertThrows(IllegalArgumentException.class, () -> heap.insert(-1, 1));           // out of range
        assertThrows(IllegalArgumentException.class, () -> heap.insert(1, Double.NaN));   // not a number
        assertThrows(IllegalArgumentException.class, () -> heap.decreaseKey(0, 9));       // would raise the key
        assertThrows(IllegalArgumentException.class, () -> heap.decreaseKey(1, 1));       // not inside
        assertThrows(IllegalArgumentException.class, () -> heap.keyOf(2));                // not inside
        assertThrows(IllegalArgumentException.class, () -> new IndexedMinHeap(-1));
    }
}
