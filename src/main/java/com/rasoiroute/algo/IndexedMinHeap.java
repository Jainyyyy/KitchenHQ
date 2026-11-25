package com.rasoiroute.algo;

import java.util.NoSuchElementException;

/**
 * A binary min-heap written from scratch, for items numbered 0, 1, 2 ... capacity-1.
 *
 * What a min-heap does: it always lets you grab the item with the SMALLEST key quickly.
 *
 * How it is stored: as a row of numbers that we picture as a tree.
 * The item at row position i has its "parent" at position (i - 1) / 2 and its
 * "children" at positions 2i + 1 and 2i + 2. The rule of the heap is:
 * every parent has a key that is smaller than (or equal to) its children's keys.
 * So the smallest key is always at position 0, the top.
 *
 * "Indexed" means we also remember where each item sits (the 'pos' row), so we can find
 * an item instantly and lower its key later. Prim's algorithm needs that "decrease key" step.
 *
 * Costs: insert, decreaseKey and extractMin each take about log2(n) steps.
 */
public final class IndexedMinHeap {

    private final int[] heap;     // heap[p] = which item sits at position p
    private final int[] pos;      // pos[item] = the position of that item, or -1 if it is not in the heap
    private final double[] key;   // key[item] = the item's current key
    private int size;

    public IndexedMinHeap(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity must not be negative");
        }
        this.heap = new int[capacity];
        this.pos = new int[capacity];
        this.key = new double[capacity];
        java.util.Arrays.fill(pos, -1);
    }

    /** Adds an item with the given key. */
    public void insert(int item, double itemKey) {
        checkRange(item);
        checkKey(itemKey);
        if (contains(item)) {
            throw new IllegalArgumentException("item " + item + " is already in the heap");
        }
        key[item] = itemKey;
        heap[size] = item;
        pos[item] = size;
        size++;
        siftUp(size - 1);
    }

    /** Lowers the key of an item that is already in the heap. A bigger key is not allowed. */
    public void decreaseKey(int item, double newKey) {
        checkRange(item);
        checkKey(newKey);
        if (!contains(item)) {
            throw new IllegalArgumentException("item " + item + " is not in the heap");
        }
        if (newKey > key[item]) {
            throw new IllegalArgumentException("decreaseKey cannot raise a key");
        }
        key[item] = newKey;
        siftUp(pos[item]);
    }

    /** Removes and returns the item with the smallest key. */
    public int extractMin() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        int top = heap[0];
        size--;
        if (size > 0) {
            heap[0] = heap[size];        // move the last item to the top ...
            pos[heap[0]] = 0;
            siftDown(0);                 // ... then let it sink to where it belongs
        }
        pos[top] = -1;
        return top;
    }

    /** The smallest key, without removing anything. */
    public double minKey() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        return key[heap[0]];
    }

    public boolean contains(int item) {
        return item >= 0 && item < pos.length && pos[item] != -1;
    }

    public double keyOf(int item) {
        if (!contains(item)) {
            throw new IllegalArgumentException("item " + item + " is not in the heap");
        }
        return key[item];
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** Checks the heap rule everywhere. Used by tests to prove the structure is never broken. */
    public boolean isValidHeap() {
        for (int p = 0; p < size; p++) {
            if (pos[heap[p]] != p) {
                return false;
            }
            if (p > 0 && less(p, (p - 1) / 2)) {
                return false;
            }
        }
        return true;
    }

    // ---------- private helpers ----------

    /** True if the item at position a should come above the item at position b. */
    private boolean less(int a, int b) {
        int x = heap[a];
        int y = heap[b];
        if (key[x] != key[y]) {
            return key[x] < key[y];
        }
        return x < y;     // equal keys: the smaller item number wins, so results are repeatable
    }

    /** Moves the item at position i upward while it is smaller than its parent. */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (less(i, parent)) {
                swap(i, parent);
                i = parent;
            } else {
                return;
            }
        }
    }

    /** Moves the item at position i downward while a child is smaller than it. */
    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            int right = left + 1;
            int smallest = i;
            if (left < size && less(left, smallest)) {
                smallest = left;
            }
            if (right < size && less(right, smallest)) {
                smallest = right;
            }
            if (smallest == i) {
                return;
            }
            swap(i, smallest);
            i = smallest;
        }
    }

    private void swap(int a, int b) {
        int x = heap[a];
        int y = heap[b];
        heap[a] = y;
        heap[b] = x;
        pos[y] = a;
        pos[x] = b;
    }

    private void checkRange(int item) {
        if (item < 0 || item >= pos.length) {
            throw new IllegalArgumentException("item " + item + " is outside 0.." + (pos.length - 1));
        }
    }

    private static void checkKey(double k) {
        if (Double.isNaN(k)) {
            throw new IllegalArgumentException("key must be a number");
        }
    }
}
