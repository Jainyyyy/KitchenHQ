package com.rasoiroute.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BloomFilterTest {

    @Test
    void addedTextIsAlwaysReportedPossiblyPresent() {
        BloomFilter filter = BloomFilter.forExpectedItems(1000, 0.01);
        filter.add("9876543210|BAKERY|chocolate cake|2026-11-20");
        assertTrue(filter.mightContain("9876543210|BAKERY|chocolate cake|2026-11-20"));
    }

    @Test
    void neverGivesAFalseNegative() {
        int n = 10_000;
        BloomFilter filter = BloomFilter.forExpectedItems(n, 0.01);
        for (int i = 0; i < n; i++) {
            filter.add("order-" + i);
        }
        for (int i = 0; i < n; i++) {
            assertTrue(filter.mightContain("order-" + i), "false negative for order-" + i);
        }
    }

    @Test
    void emptyFilterSaysNothingIsPresent() {
        BloomFilter filter = BloomFilter.forExpectedItems(100, 0.01);
        for (int i = 0; i < 1000; i++) {
            assertFalse(filter.mightContain("anything-" + i));
        }
    }

    @Test
    void measuredFalsePositiveRateIsCloseToTheTarget() {
        int n = 10_000;
        double target = 0.01;
        BloomFilter filter = BloomFilter.forExpectedItems(n, target);
        for (int i = 0; i < n; i++) {
            filter.add("member-" + i);
        }
        int trials = 100_000;
        int falsePositives = 0;
        for (int i = 0; i < trials; i++) {
            if (filter.mightContain("stranger-" + i)) {
                falsePositives++;
            }
        }
        double measured = (double) falsePositives / trials;
        // The target is 1%. We allow up to 2% to keep the test stable but meaningful.
        assertTrue(measured < 0.02, "measured false-positive rate was " + measured);
        // The maths prediction should be close to the target as well.
        assertEquals(target, filter.expectedFalsePositiveRate(), 0.005);
    }

    @Test
    void sizingMatchesTheStandardFormulas() {
        // For n = 1000 and p = 1%, the textbook answer is about 9586 bits and 7 hashes.
        BloomFilter filter = BloomFilter.forExpectedItems(1000, 0.01);
        assertEquals(9586, filter.getBitCount());
        assertEquals(7, filter.getHashCount());
    }

    @Test
    void addingTheSameTextTwiceStillWorks() {
        BloomFilter filter = BloomFilter.forExpectedItems(100, 0.01);
        filter.add("same");
        filter.add("same");
        assertTrue(filter.mightContain("same"));
    }

    @Test
    void edgeCase_emptyTextCanBeStoredAndFound() {
        BloomFilter filter = BloomFilter.forExpectedItems(100, 0.01);
        filter.add("");
        assertTrue(filter.mightContain(""));
    }

    @Test
    void edgeCase_tinyFilterGivesFalsePositivesButNeverFalseNegatives() {
        BloomFilter filter = BloomFilter.withSize(8, 1);
        for (int i = 0; i < 50; i++) {
            filter.add("x" + i);
        }
        for (int i = 0; i < 50; i++) {
            assertTrue(filter.mightContain("x" + i));
        }
        assertTrue(filter.mightContain("never-added"), "a full tiny filter should say 'possibly'");
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.forExpectedItems(0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.forExpectedItems(10, 0.0));
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.forExpectedItems(10, 1.0));
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.withSize(0, 3));
        assertThrows(IllegalArgumentException.class, () -> BloomFilter.withSize(10, 0));
        BloomFilter filter = BloomFilter.forExpectedItems(10, 0.01);
        assertThrows(IllegalArgumentException.class, () -> filter.add(null));
        assertThrows(IllegalArgumentException.class, () -> filter.mightContain(null));
    }
}
