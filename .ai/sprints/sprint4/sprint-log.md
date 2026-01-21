# Sprint 4: Transactions - Log

## 📅 Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-01-21 |
| **Sprint End** | 2026-01-21 |

---

## 📝 Daily Progress

### Day 1 (2026-01-21)
- [x] Sprint started
- [x] Created Transaction.java with full validation
- [x] Updated Block.java with transactions list and Merkle root
- [x] Updated Blockchain.java with pending pool and mining rewards
- [x] Wrote TransactionTest.java (12 tests)
- [x] Updated BlockTest.java (7 new tests)
- [x] Updated BlockchainTest.java (8 new tests)
- [x] Updated BlockSmithDemo.java with transaction demo
- [x] Fixed typo: `addTransation` → `addTransaction`
- [x] Fixed tamper detection test to use `merkleRoot` instead of `data`
- [x] All 56 tests passing
- [x] Committed and pushed to `sprint4/transactions` branch

---

## 🎯 Sprint Summary

| Metric | Value |
|--------|-------|
| **Planned Tasks** | 9 |
| **Completed Tasks** | 9 |
| **Completion Rate** | 100% |
| **Total Tests** | 56 |
| **New Tests Added** | 27 |
| **Lines Added** | ~807 |

---

## 🌳 Merkle Tree Tests

_Merkle root calculation test results:_

| # Transactions | Expected Behavior | Result |
|----------------|-------------------|--------|
| 0 (empty) | Root = hash(data) | ✅ Pass |
| 1 | Root = hash(tx) | ✅ Pass |
| 2 | Root = hash(hash(tx1) + hash(tx2)) | ✅ Pass |
| 3 | Duplicate last, then hash | ✅ Pass |
| 4+ | Standard binary tree | ✅ Pass |

---

## 💡 Lessons Learned

1. **Backward Compatibility Matters**: Keeping the legacy `Block(index, data, prevHash)` constructor prevented breaking existing tests and code.

2. **Hash Calculation Changes Require Test Updates**: When `calculateHash()` changed to use `merkleRoot` instead of `data`, the tamper detection test needed updating.

3. **Defensive Copies Are Important**: Returning `new ArrayList<>(transactions)` prevents external code from modifying internal state.

4. **COINBASE Transaction Pattern**: Mining rewards are elegantly implemented as the first transaction in each block, creating new coins "from nothing."

---

## 🔄 Carry-over Items

_None - all tasks completed_

---

## 📌 Notes

### Implementation Highlights

- **Transaction ID**: Generated using SHA-256 hash of sender + recipient + amount + timestamp
- **Merkle Root**: Calculated using binary tree reduction with duplicate-last for odd counts
- **Mining Reward**: 50 BSC from "COINBASE" address to miner
- **Balance**: Calculated by scanning all transactions (simpler than UTXO)

### Git Activity

```
Branch: sprint4/transactions
Commit: 218ca45
Message: Sprint 4: Add transaction support with Merkle trees
Files: 7 changed, 807 insertions(+), 47 deletions(-)
```

### Test Summary

| Test Class | Tests | Status |
|------------|-------|--------|
| TransactionTest | 12 | ✅ All Pass |
| BlockTest | 16 | ✅ All Pass |
| BlockchainTest | 20 | ✅ All Pass |
| MiningTest | 6 | ✅ All Pass |
| HashUtilTest | 2 | ✅ All Pass |
| **Total** | **56** | ✅ **All Pass** |
