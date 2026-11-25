package com.rasoiroute.algo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Randomized Quickselect, written from scratch.
 *
 * The problem it solves: "give me the k items that come FIRST", without sorting everything.
 * Example: out of 500 orders, find the 5 most urgent ones.
 *
 * Sorting all 500 takes about n log n steps. Quickselect needs only about n steps on average,
 * because it never bothers to put the other 495 in order.
 *
 * How it works (the idea in plain words):
 *   1. Pick a RANDOM item, called the pivot.
 *   2. Split everything into three groups: before the pivot, equal to the pivot, after the pivot.
 *   3. Look at where the spot we care about falls:
 *        - inside the "before" group  -> throw away everything else and repeat on that group
 *        - inside the "after" group   -> throw away everything else and repeat on that group
 *        - inside the "equal" group   -> done
 * Each round throws away a big chunk, so the work shrinks quickly.
 * Picking the pivot at random means no particular input can reliably make it slow.
 */
public final class Quickselect {

    private Quickselect() {
        // static helper only
    }

    /**
     * Returns the k items that come first according to the comparator.
     * The returned items are NOT in sorted order; the caller sorts them if needed (k is small).
     *
     * @param items the input list (it is not changed)
     * @param k     how many to return; 0 gives an empty list, k >= size gives everything
     * @param cmp   says which of two items comes first (negative means the first item comes first)
     * @param rnd   source of randomness (a seeded Random makes tests repeatable)
     */
    public static <T> List<T> firstK(List<T> items, int k, Comparator<? super T> cmp, Random rnd) {
        if (items == null || cmp == null || rnd == null) {
            throw new IllegalArgumentException("items, comparator and random must not be null");
        }
        if (k < 0) {
            throw new IllegalArgumentException("k must not be negative");
        }
        int n = items.size();
        if (k == 0) {
            return new ArrayList<>();
        }
        if (k >= n) {
            return new ArrayList<>(items);
        }

        List<T> work = new ArrayList<>(items);   // we rearrange a copy, never the caller's list
        select(work, k - 1, cmp, rnd);           // make position k-1 hold its final-sorted item
        return new ArrayList<>(work.subList(0, k));
    }

    /**
     * Rearranges the list so that the item at position 'target' is exactly the item that
     * would be there if the whole list were sorted, and every item before it comes first.
     */
    private static <T> void select(List<T> a, int target, Comparator<? super T> cmp, Random rnd) {
        int lo = 0;
        int hi = a.size() - 1;

        while (lo < hi) {
            T pivot = a.get(lo + rnd.nextInt(hi - lo + 1));   // random pivot

            // Three-way split of the range lo..hi:
            //   lo .. lt-1   items that come BEFORE the pivot
            //   lt .. gt     items EQUAL to the pivot
            //   gt+1 .. hi   items that come AFTER the pivot
            int lt = lo;
            int i = lo;
            int gt = hi;
            while (i <= gt) {
                int c = cmp.compare(a.get(i), pivot);
                if (c < 0) {
                    swap(a, lt, i);
                    lt++;
                    i++;
                } else if (c > 0) {
                    swap(a, i, gt);
                    gt--;
                } else {
                    i++;
                }
            }

            if (target < lt) {
                hi = lt - 1;          // the spot we want is in the "before" group
            } else if (target > gt) {
                lo = gt + 1;          // the spot we want is in the "after" group
            } else {
                return;               // the spot we want is in the "equal" group: done
            }
        }
    }

    private static <T> void swap(List<T> a, int i, int j) {
        T tmp = a.get(i);
        a.set(i, a.get(j));
        a.set(j, tmp);
    }
}
