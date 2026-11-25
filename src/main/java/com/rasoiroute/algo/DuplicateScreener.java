package com.rasoiroute.algo;

import com.rasoiroute.model.Order;

import java.util.HashSet;
import java.util.Set;

/**
 * Screens new orders for repeats.
 *
 * Step 1: ask the Bloom filter (fast, tiny memory).
 *         "Definitely new"  -> done, no further work.
 *         "Possibly seen"   -> go to step 2.
 * Step 2: confirm with an exact lookup in a HashSet, because a Bloom filter
 *         can be wrong when it says "possibly seen".
 */
public final class DuplicateScreener {

    public enum Verdict { NEW, DUPLICATE }

    private final BloomFilter bloom;
    private final Set<String> exact = new HashSet<>();

    // Counters used later in the report's false-positive experiment.
    private int bloomHits;
    private int confirmedDuplicates;
    private int falseAlarms;

    public DuplicateScreener(BloomFilter bloom) {
        if (bloom == null) {
            throw new IllegalArgumentException("bloom filter is required");
        }
        this.bloom = bloom;
    }

    /** Checks an order WITHOUT saving it. */
    public Verdict check(Order order) {
        String fingerprint = order.fingerprint();
        if (!bloom.mightContain(fingerprint)) {
            return Verdict.NEW;          // definitely new: the Bloom filter is never wrong here
        }
        bloomHits++;
        if (exact.contains(fingerprint)) {
            confirmedDuplicates++;
            return Verdict.DUPLICATE;    // confirmed by the exact check
        }
        falseAlarms++;                   // the filter said "possibly", the exact check said no
        return Verdict.NEW;
    }

    /** Saves an order so that future repeats are caught. */
    public void register(Order order) {
        String fingerprint = order.fingerprint();
        if (exact.add(fingerprint)) {    // add() returns false if it was already there
            bloom.add(fingerprint);
        }
    }

    public int getBloomHits() {
        return bloomHits;
    }

    public int getConfirmedDuplicates() {
        return confirmedDuplicates;
    }

    public int getFalseAlarms() {
        return falseAlarms;
    }
}
