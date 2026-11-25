# KitchenHQ benchmark results

Java 21.0.12.1, 2 CPU cores, seed 20261120. Times are medians in milliseconds.

## A. Route builder against baselines

Round trip from the kitchen (stop 0). Distances are straight-line (haversine) km. The spanning tree weight is a lower bound: no round trip can be shorter.

| Layout | Stops | Method | Round trip (km) | x spanning tree | Time (ms) |
|---|---|---|---|---|---|
| uniform | 50 | Our 2-approximation (MST + shortcut) | 157.4 | 1.60 | 0.302 |
| uniform | 50 | Nearest stop each time | 148.6 | 1.51 | 0.089 |
| uniform | 50 | Arrival order (no planning) | 611.0 | 6.22 | 0.016 |
| uniform | 500 | Our 2-approximation (MST + shortcut) | 482.8 | 1.56 | 2.171 |
| uniform | 500 | Nearest stop each time | 445.4 | 1.44 | 0.938 |
| uniform | 500 | Arrival order (no planning) | 5446.2 | 17.61 | 0.061 |
| uniform | 2000 | Our 2-approximation (MST + shortcut) | 978.2 | 1.56 | 10.8 |
| uniform | 2000 | Nearest stop each time | 864.4 | 1.38 | 3.112 |
| uniform | 2000 | Arrival order (no planning) | 22373.6 | 35.74 | 0.160 |
| clustered | 50 | Our 2-approximation (MST + shortcut) | 113.3 | 1.61 | 0.015 |
| clustered | 50 | Nearest stop each time | 129.6 | 1.84 | 0.0023 |
| clustered | 50 | Arrival order (no planning) | 536.1 | 7.60 | 0.0010 |
| clustered | 500 | Our 2-approximation (MST + shortcut) | 349.8 | 1.60 | 0.987 |
| clustered | 500 | Nearest stop each time | 326.5 | 1.49 | 0.246 |
| clustered | 500 | Arrival order (no planning) | 5286.6 | 24.18 | 0.012 |
| clustered | 2000 | Our 2-approximation (MST + shortcut) | 734.3 | 1.59 | 11.3 |
| clustered | 2000 | Nearest stop each time | 647.3 | 1.40 | 3.222 |
| clustered | 2000 | Arrival order (no planning) | 17551.8 | 38.01 | 0.049 |

### Small days: against the exact answer (Held-Karp)

| Stops (with kitchen) | Exact (km) | Ours (km) | Ours / exact | Nearest (km) | Nearest / exact | Exact time (ms) | Ours time (ms) |
|---|---|---|---|---|---|---|---|
| 8 | 41.93 | 51.54 | avg 1.112, worst 1.293 | 41.93 | avg 1.057 | 0.040 | 0.0010 |
| 10 | 61.35 | 75.23 | avg 1.151, worst 1.332 | 62.87 | avg 1.102 | 0.279 | 0.0011 |
| 12 | 64.51 | 80.34 | avg 1.180, worst 1.369 | 67.33 | avg 1.107 | 1.313 | 0.0015 |
| 14 | 63.94 | 76.03 | avg 1.203, worst 1.462 | 74.17 | avg 1.133 | 5.058 | 0.0019 |

(Exact/ours/nearest columns show the last of 20 random days; ratios are averaged over all 20.)

### Is the triangle inequality true for the real distances?

| Stops | Violations found | Check time (ms) |
|---|---|---|
| 50 | 0 | 0.200 |
| 100 | 0 | 1.481 |
| 200 | 0 | 12.4 |

## B. Bloom filter: false positives and memory

10,000 order keys are added. Then 200,000 keys that were NEVER added are checked; every 'maybe' for those is a false alarm. All 10,000 added keys are also re-checked (a Bloom filter must never miss them).

| Target false-alarm rate | Bits | Hash functions | Memory (KB) | Measured false alarms | Predicted by formula | Missed real keys |
|---|---|---|---|---|---|---|
| 10.0% | 47926 | 3 | 5.9 | 10.111% | 10.071% | 0 |
| 1.0% | 95851 | 7 | 11.7 | 0.999% | 1.004% | 0 |
| 0.1% | 143776 | 10 | 17.6 | 0.106% | 0.100% | 0 |

### Lookup speed and memory: Bloom filter against an exact HashSet

| Keys stored | Bloom check, 100k lookups (ms) | HashSet check, 100k lookups (ms) | Bloom memory (KB) | HashSet memory, rough (KB) |
|---|---|---|---|---|
| 1000 | 11.5 | 1.029 | 1.2 | 136 |
| 10000 | 10.0 | 1.441 | 11.7 | 1375 |
| 100000 | 10.4 | 2.906 | 117.0 | 13943 |

## C. Quickselect against sorting everything

Task: find the k most urgent items out of n (lowest score first). Quickselect (ours) is compared with sorting the whole list, and with a heap that keeps the best k.

| n | k | Quickselect (ms) | Full sort (ms) | Heap of k (ms) | Fastest |
|---|---|---|---|---|---|
| 1,000 | 5 | 0.128 | 0.350 | 0.341 | Quickselect |
| 10,000 | 5 | 0.205 | 2.850 | 0.371 | Quickselect |
| 100,000 | 5 | 3.988 | 19.0 | 1.696 | Heap of k |
| 1,000,000 | 5 | 44.5 | 408.8 | 32.0 | Heap of k |
| 100,000 | 10,000 | 4.208 | 19.0 | 10.9 | Quickselect |
| 1,000,000 | 100,000 | 52.2 | 393.7 | 213.5 | Quickselect |

### Does the time grow like n? (k = 5)

| n | Quickselect (ms) | Time per 1,000 items (microseconds) |
|---|---|---|
| 100,000 | 4.272 | 42.72 |
| 200,000 | 4.973 | 24.86 |
| 400,000 | 16.1 | 40.36 |
| 800,000 | 23.2 | 29.01 |

### Many equal scores (the reason for the three-way split)

| n | Distinct scores | Quickselect (ms) |
|---|---|---|
| 200,000 | 2 | 4.009 |
| 200,000 | 10 | 9.042 |
| 200,000 | 1,000,000 | 9.168 |


