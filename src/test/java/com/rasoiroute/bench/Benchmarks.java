package com.rasoiroute.bench;

import com.rasoiroute.algo.BloomFilter;
import com.rasoiroute.algo.Quickselect;
import com.rasoiroute.geo.DistanceMatrix;
import com.rasoiroute.geo.Location;
import com.rasoiroute.geo.MetricChecker;
import com.rasoiroute.route.ApproxTour;
import com.rasoiroute.route.ArrivalOrderTour;
import com.rasoiroute.route.ExactTsp;
import com.rasoiroute.route.NearestNeighbourTour;
import com.rasoiroute.route.Tour;
import com.rasoiroute.route.TourBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Benchmarks for the three questions the CIA 3 brief asks about:
 *   A. Route builder (our 2-approximation) against baselines, at three or more sizes.
 *   B. Bloom filter false-positive rate at three sizes, against the theory.
 *   C. Quickselect against sorting everything (and against a heap), at several sizes.
 *
 * Run (after compiling main + test classes):
 *   java -cp target/classes;target/test-classes com.rasoiroute.bench.Benchmarks
 * Every random choice uses a fixed seed, so the numbers can be reproduced.
 * Times are the MEDIAN of several runs after warm-up runs, in milliseconds.
 */
public final class Benchmarks {

    private static final long SEED = 20261120L;
    private static volatile Object sink;   // stops the JVM from optimising the work away

    private Benchmarks() {
    }

    // ------------------------------------------------------------------ timing helper

    private static double medianMs(int warmups, int runs, Supplier<Object> work) {
        warmups = Math.max(warmups, 12);   // the JIT compiler needs time to optimise; early runs are not representative
        runs = Math.max(runs, 21);
        for (int i = 0; i < warmups; i++) {
            sink = work.get();
        }
        double[] t = new double[runs];
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            sink = work.get();
            t[i] = (System.nanoTime() - start) / 1e6;
        }
        Arrays.sort(t);
        return t[runs / 2];
    }

    private static String ms(double v) {
        return v < 0.01 ? String.format(Locale.ROOT, "%.4f", v)
                : v < 10 ? String.format(Locale.ROOT, "%.3f", v)
                : String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ A. route builder

    /** Random delivery points in a box roughly the size of Ahmedabad. Index 0 is the kitchen. */
    private static List<Location> points(int n, long seed, boolean clustered) {
        Random r = new Random(seed);
        List<Location> list = new ArrayList<>();
        double[][] centres = new double[6][2];
        for (double[] c : centres) {
            c[0] = 22.95 + r.nextDouble() * 0.2;
            c[1] = 72.45 + r.nextDouble() * 0.2;
        }
        for (int i = 0; i < n; i++) {
            double lat;
            double lng;
            if (clustered) {
                double[] c = centres[r.nextInt(centres.length)];
                lat = c[0] + r.nextGaussian() * 0.015;
                lng = c[1] + r.nextGaussian() * 0.015;
            } else {
                lat = 22.95 + r.nextDouble() * 0.2;
                lng = 72.45 + r.nextDouble() * 0.2;
            }
            list.add(new Location("P" + i, lat, lng));
        }
        return list;
    }

    private static void routeBenchmark(StringBuilder out) {
        out.append("## A. Route builder against baselines\n\n");
        out.append("Round trip from the kitchen (stop 0). Distances are straight-line (haversine) km. ")
           .append("The spanning tree weight is a lower bound: no round trip can be shorter.\n\n");
        out.append("| Layout | Stops | Method | Round trip (km) | x spanning tree | Time (ms) |\n");
        out.append("|---|---|---|---|---|---|\n");

        int[] sizes = {50, 500, 2000};
        for (boolean clustered : new boolean[]{false, true}) {
            for (int n : sizes) {
                List<Location> pts = points(n + 1, SEED + n, clustered);
                DistanceMatrix m = DistanceMatrix.fromLocations(pts);
                String layout = clustered ? "clustered" : "uniform";
                int reps = n >= 2000 ? 5 : 15;

                ApproxTour approx = TourBuilder.build(m, 0);
                double mst = approx.mstWeight();
                Tour nn = NearestNeighbourTour.solve(m, 0);
                Tour arrival = ArrivalOrderTour.solve(m, 0);

                double tApprox = medianMs(3, reps, () -> TourBuilder.build(m, 0));
                double tNn = medianMs(3, reps, () -> NearestNeighbourTour.solve(m, 0));
                double tArr = medianMs(3, reps, () -> ArrivalOrderTour.solve(m, 0));

                row(out, layout, n, "Our 2-approximation (MST + shortcut)", approx.tour().length(), mst, tApprox);
                row(out, layout, n, "Nearest stop each time", nn.length(), mst, tNn);
                row(out, layout, n, "Arrival order (no planning)", arrival.length(), mst, tArr);
            }
        }
        out.append("\n");

        // exact comparison on small days
        out.append("### Small days: against the exact answer (Held-Karp)\n\n");
        out.append("| Stops (with kitchen) | Exact (km) | Ours (km) | Ours / exact | Nearest (km) | Nearest / exact | Exact time (ms) | Ours time (ms) |\n");
        out.append("|---|---|---|---|---|---|---|---|\n");
        for (int n : new int[]{8, 10, 12, 14}) {
            double worst = 0;
            double sumRatio = 0;
            double sumNnRatio = 0;
            int trials = 20;
            double lastExact = 0;
            double lastOurs = 0;
            double lastNn = 0;
            for (int t = 0; t < trials; t++) {
                DistanceMatrix m = DistanceMatrix.fromLocations(points(n, SEED * 3 + n * 100 + t, t % 2 == 0));
                Tour exact = ExactTsp.solve(m, 0);
                Tour ours = TourBuilder.build(m, 0).tour();
                Tour nn = NearestNeighbourTour.solve(m, 0);
                double ratio = ours.length() / exact.length();
                worst = Math.max(worst, ratio);
                sumRatio += ratio;
                sumNnRatio += nn.length() / exact.length();
                lastExact = exact.length();
                lastOurs = ours.length();
                lastNn = nn.length();
            }
            DistanceMatrix m = DistanceMatrix.fromLocations(points(n, SEED * 3 + n * 100, true));
            double tEx = medianMs(2, 7, () -> ExactTsp.solve(m, 0));
            double tOurs = medianMs(3, 15, () -> TourBuilder.build(m, 0));
            out.append(String.format(Locale.ROOT,
                    "| %d | %.2f | %.2f | avg %.3f, worst %.3f | %.2f | avg %.3f | %s | %s |%n",
                    n, lastExact, lastOurs, sumRatio / trials, worst, lastNn, sumNnRatio / trials, ms(tEx), ms(tOurs)));
        }
        out.append("\n(Exact/ours/nearest columns show the last of 20 random days; ratios are averaged over all 20.)\n\n");

        // metric check
        out.append("### Is the triangle inequality true for the real distances?\n\n");
        out.append("| Stops | Violations found | Check time (ms) |\n|---|---|---|\n");
        for (int n : new int[]{50, 100, 200}) {
            DistanceMatrix m = DistanceMatrix.fromLocations(points(n, SEED + 7, true));
            int v = MetricChecker.findViolations(m, 10).size();
            double t = medianMs(1, 5, () -> MetricChecker.findViolations(m, 10));
            out.append(String.format(Locale.ROOT, "| %d | %d | %s |%n", n, v, ms(t)));
        }
        out.append("\n");
    }

    private static void row(StringBuilder out, String layout, int n, String method, double len, double mst, double ms) {
        out.append(String.format(Locale.ROOT, "| %s | %d | %s | %.1f | %.2f | %s |%n",
                layout, n, method, len, len / mst, ms(ms)));
    }

    // ------------------------------------------------------------------ B. Bloom filter

    private static void bloomBenchmark(StringBuilder out) {
        out.append("## B. Bloom filter: false positives and memory\n\n");
        out.append("10,000 order keys are added. Then 200,000 keys that were NEVER added are checked; ")
           .append("every 'maybe' for those is a false alarm. All 10,000 added keys are also re-checked ")
           .append("(a Bloom filter must never miss them).\n\n");
        out.append("| Target false-alarm rate | Bits | Hash functions | Memory (KB) | Measured false alarms | Predicted by formula | Missed real keys |\n");
        out.append("|---|---|---|---|---|---|---|\n");

        int n = 10_000;
        int probes = 200_000;
        for (double target : new double[]{0.10, 0.01, 0.001}) {
            BloomFilter bf = BloomFilter.forExpectedItems(n, target);
            for (int i = 0; i < n; i++) {
                bf.add("order|" + i + "|Customer " + i);
            }
            int missed = 0;
            for (int i = 0; i < n; i++) {
                if (!bf.mightContain("order|" + i + "|Customer " + i)) {
                    missed++;
                }
            }
            int falseAlarms = 0;
            for (int i = 0; i < probes; i++) {
                if (bf.mightContain("never|" + i + "|Someone " + i)) {
                    falseAlarms++;
                }
            }
            out.append(String.format(Locale.ROOT, "| %.1f%% | %d | %d | %.1f | %.3f%% | %.3f%% | %d |%n",
                    target * 100, bf.getBitCount(), bf.getHashCount(), bf.getBitCount() / 8.0 / 1024,
                    100.0 * falseAlarms / probes, 100.0 * bf.expectedFalsePositiveRate(), missed));
        }
        out.append("\n");

        // speed against an exact HashSet
        out.append("### Lookup speed and memory: Bloom filter against an exact HashSet\n\n");
        out.append("| Keys stored | Bloom check, 100k lookups (ms) | HashSet check, 100k lookups (ms) | Bloom memory (KB) | HashSet memory, rough (KB) |\n");
        out.append("|---|---|---|---|---|\n");
        for (int keys : new int[]{1_000, 10_000, 100_000}) {
            BloomFilter bf = BloomFilter.forExpectedItems(keys, 0.01);
            Set<String> exact = new HashSet<>();
            String[] probe = new String[100_000];
            long avgLen = 0;
            for (int i = 0; i < keys; i++) {
                String k = "order|" + i + "|Customer " + i + "|Kitchen item";
                bf.add(k);
                exact.add(k);
                avgLen += k.length();
            }
            for (int i = 0; i < probe.length; i++) {
                probe[i] = (i % 2 == 0 ? "order|" + (i % keys) + "|Customer " + (i % keys) + "|Kitchen item" : "x|" + i);
            }
            double tb = medianMs(3, 9, () -> {
                int c = 0;
                for (String p : probe) {
                    if (bf.mightContain(p)) {
                        c++;
                    }
                }
                return c;
            });
            double te = medianMs(3, 9, () -> {
                int c = 0;
                for (String p : probe) {
                    if (exact.contains(p)) {
                        c++;
                    }
                }
                return c;
            });
            // rough: ~ (string chars + 56 bytes object/array overhead + 48 bytes map entry) per key
            double hashSetKb = keys * ((avgLen / (double) keys) + 56 + 48) / 1024.0;
            out.append(String.format(Locale.ROOT, "| %d | %s | %s | %.1f | %.0f |%n",
                    keys, ms(tb), ms(te), bf.getBitCount() / 8.0 / 1024, hashSetKb));
        }
        out.append("\n");
    }

    // ------------------------------------------------------------------ C. Quickselect

    private static void quickselectBenchmark(StringBuilder out) {
        out.append("## C. Quickselect against sorting everything\n\n");
        out.append("Task: find the k most urgent items out of n (lowest score first). ")
           .append("Quickselect (ours) is compared with sorting the whole list, and with a heap that keeps the best k.\n\n");
        out.append("| n | k | Quickselect (ms) | Full sort (ms) | Heap of k (ms) | Fastest |\n");
        out.append("|---|---|---|---|---|---|\n");

        int[][] cases = {{1_000, 5}, {10_000, 5}, {100_000, 5}, {1_000_000, 5}, {100_000, 10_000}, {1_000_000, 100_000}};
        for (int[] c : cases) {
            int n = c[0];
            int k = c[1];
            Random r = new Random(SEED + n + k);
            List<Double> data = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                data.add(r.nextDouble() * 1000);
            }
            int reps = n >= 1_000_000 ? 5 : 15;
            double tq = medianMs(3, reps, () -> Quickselect.firstK(data, k, Double::compare, new Random(1)));
            double ts = medianMs(3, reps, () -> {
                List<Double> copy = new ArrayList<>(data);
                Collections.sort(copy);
                return new ArrayList<>(copy.subList(0, k));
            });
            double th = medianMs(3, reps, () -> {
                PriorityQueue<Double> worstOnTop = new PriorityQueue<>(k + 1, Collections.reverseOrder());
                for (Double d : data) {
                    worstOnTop.add(d);
                    if (worstOnTop.size() > k) {
                        worstOnTop.poll();
                    }
                }
                return worstOnTop;
            });
            String best = tq <= ts && tq <= th ? "Quickselect" : ts <= th ? "Full sort" : "Heap of k";
            out.append(String.format(Locale.ROOT, "| %,d | %,d | %s | %s | %s | %s |%n", n, k, ms(tq), ms(ts), ms(th), best));
        }
        out.append("\n");

        // growth check: doubling n should roughly double Quickselect time
        out.append("### Does the time grow like n? (k = 5)\n\n| n | Quickselect (ms) | Time per 1,000 items (microseconds) |\n|---|---|---|\n");
        for (int n : new int[]{100_000, 200_000, 400_000, 800_000}) {
            Random r = new Random(SEED + n);
            List<Double> data = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                data.add(r.nextDouble());
            }
            double t = medianMs(3, 9, () -> Quickselect.firstK(data, 5, Double::compare, new Random(1)));
            out.append(String.format(Locale.ROOT, "| %,d | %s | %.2f |%n", n, ms(t), t * 1000 / (n / 1000.0)));
        }
        out.append("\n");

        // many ties: the three-way split should stay fast
        out.append("### Many equal scores (the reason for the three-way split)\n\n| n | Distinct scores | Quickselect (ms) |\n|---|---|---|\n");
        for (int distinct : new int[]{2, 10, 1_000_000}) {
            int n = 200_000;
            Random r = new Random(SEED + distinct);
            List<Integer> data = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                data.add(r.nextInt(distinct));
            }
            double t = medianMs(3, 9, () -> Quickselect.firstK(data, 5, Integer::compare, new Random(1)));
            out.append(String.format(Locale.ROOT, "| %,d | %,d | %s |%n", n, distinct, ms(t)));
        }
        out.append("\n");
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) {
        StringBuilder out = new StringBuilder();
        out.append("# RasoiRoute benchmark results\n\n");
        out.append("Java ").append(System.getProperty("java.version")).append(", ")
           .append(Runtime.getRuntime().availableProcessors()).append(" CPU cores, seed ").append(SEED)
           .append(". Times are medians in milliseconds.\n\n");
        routeBenchmark(out);
        bloomBenchmark(out);
        quickselectBenchmark(out);
        System.out.println(out);
    }
}
