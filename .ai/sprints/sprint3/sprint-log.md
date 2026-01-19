# Sprint 3: Blockchain - Log

## 📅 Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-01-19 |
| **Sprint End** | 2026-01-19 |

---

## 📝 Daily Progress

### Day 1 (2026-01-19)
- [x] Created `Blockchain.java` class with chain management
- [x] Implemented automatic Genesis block creation in constructor
- [x] Implemented `addBlock()` with correct linking and mining
- [x] Implemented `isChainValid()` for tamper detection
- [x] Added helper methods: `getLatestBlock()`, `getChain()`, `getChainSize()`, `getBlock(int)`
- [x] Added display methods: `printChain()`, `toString()`
- [x] Created comprehensive `BlockchainTest.java` (12 tests)
- [x] Updated `BlockSmithDemo.java` with Blockchain and tamper detection demo

---

## 🎯 Sprint Summary

| Metric | Value |
|--------|-------|
| **Planned Tasks** | 8 |
| **Completed Tasks** | 8 |
| **Completion Rate** | 100% |

---

## 🔗 Chain Validation Results

| Test Case | Result | Notes |
|-----------|--------|-------|
| Valid chain | ✅ PASS | `isChainValid()` returns true for unmodified chain |
| Tampered data | ✅ PASS | Detects modified block data via hash mismatch |
| Tampered hash | ✅ PASS | Handled by recalculated hash comparison |
| Broken link | ✅ PASS | Detects previousHash tampering |

---

## 📊 Test Results

```
Tests run: 33, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### BlockchainTest.java - 12 tests:
- **Genesis Block Tests (3):** Creation, data content, mining verification
- **Add Block Tests (5):** Chain size, index, linking, mining, data storage
- **Chain Validation Tests (2):** Valid chain, genesis-only chain
- **Tamper Detection Tests (2):** Data tampering, broken links

---

## 💡 Lessons Learned

1. **Java Reflection for Testing** - Used reflection to modify private fields, simulating real-world attacks
2. **Chain Integrity** - Two-layer validation: hash consistency + link integrity
3. **Immutability Pattern** - Private fields with no setters enforce blockchain immutability

---

## 🔄 Carry-over Items

_None - all tasks completed_

---

## 📌 Notes

- `Blockchain` class auto-creates and mines Genesis block on construction
- `isChainValid()` checks both hash recalculation AND chain links
- Demo includes live tamper detection - shows attack being caught
- All 33 project tests pass (HashUtil: 6, Block: 6, Mining: 9, Blockchain: 12)
