# Sprint 1: Fundamentals - Log

## 📅 Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-01-14 |
| **Sprint End** | 2026-01-14 |

---

## 📝 Daily Progress

### Day 1 (2026-01-14)
- [x] Implemented `BlockchainConfig` - configuration constants
- [x] Implemented `HashUtil` - SHA-256 hashing with `applySha256()`
- [x] Implemented `Block` - block structure with `calculateHash()` and `createGenesisBlock()`
- [x] Created `HashUtilTest` - 6 tests for hashing functionality
- [x] Created `BlockTest` - 6 tests for block functionality
- [x] All 12 tests passing ✅

---

## 🎯 Sprint Summary

| Metric | Value |
|--------|-------|
| **Planned Tasks** | 8 |
| **Completed Tasks** | 8 |
| **Completion Rate** | 100% ✅ |
| **Tests Written** | 12 |
| **Tests Passing** | 12 |

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | `HashUtil.applySha256()` returns 64-char hex string | ✅ |
| 2 | Same input always produces same hash | ✅ |
| 3 | Different inputs produce different hashes | ✅ |
| 4 | Block can be created with all required fields | ✅ |
| 5 | `calculateHash()` produces consistent results | ✅ |
| 6 | Genesis block has index 0 and previousHash "0" | ✅ |
| 7 | All unit tests pass | ✅ |
| 8 | Code contains THEORY comments | ✅ |

---

## 🧪 Test Results

```
[INFO] Running com.blocksmith.core.BlockTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0

[INFO] Running com.blocksmith.util.HashUtilTest  
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0

[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 💡 Lessons Learned

- Java's `MessageDigest` provides built-in SHA-256 support
- Byte-to-hex conversion requires handling signed bytes correctly
- `final` fields enforce immutability in Block
- Factory methods (`createGenesisBlock()`) provide clean API for special cases

---

## 📌 Notes

- `Block` class will be extended in Sprint 2 with `nonce` and `mineBlock()` method
- Currency symbol set to "BSM" (BlockSMith)
- Mining difficulty set to 4 (hash must start with "0000")
