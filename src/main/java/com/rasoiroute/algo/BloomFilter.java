package com.rasoiroute.algo;

import java.nio.charset.StandardCharsets;

/**
 * A Bloom filter written from scratch (no java.util.BitSet, no library).
 *
 * What it answers: "have I probably seen this text before?"
 *   - false  -> DEFINITELY not added before (never wrong)
 *   - true   -> POSSIBLY added before (can be wrong; confirm with an exact check)
 *
 * How it works: a long row of bits, all 0 at the start.
 * To add a text, k different hash functions each choose one position and we set those bits to 1.
 * To check a text, we look at the same k positions: if any bit is 0 the text was never added.
 */
public final class BloomFilter {

    private final long[] words;   // the bit array: each long holds 64 bits
    private final int bitCount;   // m: total number of bits
    private final int hashCount;  // k: how many positions each text uses
    private int insertedCount;    // n: how many texts have been added

    private BloomFilter(int bitCount, int hashCount) {
        if (bitCount <= 0) {
            throw new IllegalArgumentException("bitCount must be positive");
        }
        if (hashCount <= 0) {
            throw new IllegalArgumentException("hashCount must be positive");
        }
        this.bitCount = bitCount;
        this.hashCount = hashCount;
        this.words = new long[(bitCount + 63) / 64];
    }

    /** Builds a filter sized for about expectedItems texts and a target false-positive rate. */
    public static BloomFilter forExpectedItems(int expectedItems, double targetFalsePositiveRate) {
        if (expectedItems <= 0) {
            throw new IllegalArgumentException("expectedItems must be positive");
        }
        if (!(targetFalsePositiveRate > 0.0 && targetFalsePositiveRate < 1.0)) {
            throw new IllegalArgumentException("false-positive rate must be between 0 and 1");
        }
        double ln2 = Math.log(2);
        // Standard sizing formulas: m = -n ln(p) / (ln 2)^2   and   k = (m / n) ln 2
        int m = (int) Math.ceil(-expectedItems * Math.log(targetFalsePositiveRate) / (ln2 * ln2));
        int k = Math.max(1, (int) Math.round((double) m / expectedItems * ln2));
        return new BloomFilter(m, k);
    }

    /** Builds a filter with an exact size. Mainly useful for experiments and tests. */
    public static BloomFilter withSize(int bitCount, int hashCount) {
        return new BloomFilter(bitCount, hashCount);
    }

    /** Adds a text to the filter. */
    public void add(String text) {
        requireText(text);
        long[] h = baseHashes(text);
        for (int i = 0; i < hashCount; i++) {
            setBit(position(h, i));
        }
        insertedCount++;
    }

    /** false = definitely new. true = possibly seen before (must be confirmed). */
    public boolean mightContain(String text) {
        requireText(text);
        long[] h = baseHashes(text);
        for (int i = 0; i < hashCount; i++) {
            if (!getBit(position(h, i))) {
                return false;
            }
        }
        return true;
    }

    /** The false-positive rate the maths predicts right now: (1 - e^(-kn/m))^k. */
    public double expectedFalsePositiveRate() {
        double fractionSet = 1.0 - Math.exp(-(double) hashCount * insertedCount / bitCount);
        return Math.pow(fractionSet, hashCount);
    }

    public int getBitCount() {
        return bitCount;
    }

    public int getHashCount() {
        return hashCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    // ---------- private helpers ----------

    private static void requireText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
    }

    /**
     * Instead of writing k separate hash functions, we compute just two good ones (h1, h2)
     * and combine them: position i = h1 + i * h2 (this trick is called double hashing).
     */
    private static long[] baseHashes(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        long h1 = mix(fnv1a(bytes, 0xcbf29ce484222325L));
        long h2 = mix(fnv1a(bytes, 0x84222325cbf29ce4L)) | 1L; // odd, so steps never get stuck
        return new long[] {h1, h2};
    }

    private int position(long[] h, int i) {
        return (int) Math.floorMod(h[0] + (long) i * h[1], (long) bitCount);
    }

    /** FNV-1a: a simple, well-known way to turn bytes into a 64-bit number. */
    private static long fnv1a(byte[] data, long seed) {
        long hash = seed;
        for (byte b : data) {
            hash ^= (b & 0xffL);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /** Scrambles the bits of a number so that similar texts give very different results. */
    private static long mix(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return x;
    }

    private void setBit(int index) {
        words[index >>> 6] |= (1L << (index & 63));
    }

    private boolean getBit(int index) {
        return (words[index >>> 6] & (1L << (index & 63))) != 0;
    }
}
