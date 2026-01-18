# Sprint 2: Proof-of-Work - Log

## 📅 Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-01-17 |
| **Sprint End** | 2026-01-18 |

---

## 📝 Daily Progress

### Day 1 (2026-01-17)
- [x] Added `nonce` field to Block class
- [x] Implemented `mineBlock(difficulty)` method
- [x] Updated `calculateHash()` to include nonce
- [x] Created MiningTest.java with 9 comprehensive tests
- [x] Updated BlockSmithDemo with mining benchmark
- [x] All 21 tests passing
- [x] Branch pushed and PR merged

---

## 🎯 Sprint Summary

| Metric | Value |
|--------|-------|
| **Planned Tasks** | 8 |
| **Completed Tasks** | 8 |
| **Completion Rate** | 100% |

---

## ⏱️ Mining Benchmarks

_Recorded during development (difficulty comparison test):_

| Difficulty | Nonce Found | Time (ms) | Date |
|------------|-------------|-----------|------|
| 1 | 10 | 0 | 2026-01-17 |
| 2 | 134 | 0 | 2026-01-17 |
| 3 | 4,726 | 2 | 2026-01-17 |
| 4 | 66,398 | 37 | 2026-01-17 |
| 5 | 176,538 | 104 | 2026-01-17 |

**Observation:** ~16x more attempts per difficulty level (exponential growth confirmed!)

---

## 💡 Lessons Learned

- Proof-of-Work creates security through computational cost
- SHA-256 is uniformly distributed, making brute-force the only option
- Higher difficulty = exponentially more work (16^N attempts on average)
- Mining time varies due to randomness (sometimes lucky, sometimes not)

---

## 🔄 Carry-over Items

_None - all tasks completed_

---

## 📌 Notes

- Block class now includes nonce in hash calculation
- mineBlock() returns mining time in milliseconds for benchmarking
- Demo shows real-time mining with statistics
- Tests verify hash validity at multiple difficulty levels
