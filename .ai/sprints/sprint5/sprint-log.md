# Sprint 5: Wallets and Signatures - Log

## 📅 Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-01-23 |
| **Sprint End** | 2026-01-27 |

---

## 📝 Daily Progress

### Day 1 (2026-01-23)
- [x] Sprint started
- [x] Created branch `sprint5/wallets`
- [x] Implemented ECDSA key pair generation
- [x] Created WalletTest with 3 initial tests
- [x] Committed: "feat(wallet): Add ECDSA key pair generation"

### Day 2 (2026-01-25)
- [x] Implemented address generation (0x format)
- [x] Added 5 address tests to WalletTest
- [x] Committed: "feat(wallet): Add address generation from public key"

### Day 3 (2026-01-26-27)
- [x] Added signature/senderPublicKey fields to Transaction
- [x] Implemented signTransaction() in Wallet
- [x] Added 5 signing tests to WalletTest
- [x] Implemented verifySignature() in Transaction
- [x] Added 5 verification tests to TransactionTest
- [x] Fixed test bug (COINBASE sender in unsigned test)
- [x] All 81 tests passing
- [x] Committed: "feat(wallet): Add transaction signing and verification"

---

## 🎯 Sprint Summary

| Metric | Value |
|--------|-------|
| **Planned Tasks** | 9 |
| **Completed Tasks** | 9 |
| **Completion Rate** | 100% |
| **Total Tests** | 81 |
| **New Tests Added** | 25 (13 WalletTest + 12 TransactionTest updates) |
| **Lines Added** | ~300 |

---

## 🔐 Cryptography Test Results

| Test Case | Result | Notes |
|-----------|--------|-------|
| Key generation | ✅ Pass | EC algorithm, secp256r1 curve |
| Address format | ✅ Pass | 0x + 40 hex chars = 42 total |
| Valid signature | ✅ Pass | SHA256withECDSA |
| Tampered tx | ✅ Pass | Signature fails verification |
| Wrong key | ✅ Pass | Cannot sign for different address |
| COINBASE | ✅ Pass | No signature required |

---

## 📍 Generated Addresses (samples)

| Wallet # | Address Format | Valid? |
|----------|----------------|--------|
| Any | 0x + 40 hex characters | ✅ |

---

## 💡 Lessons Learned

1. **Missing import causes confusing errors**: Forgot `java.security.KeyPair` import - compiler showed type mismatch instead of missing import.

2. **COINBASE special case**: Mining rewards don't require signatures since they're system-generated, not user-initiated.

3. **Test data matters**: Using "COINBASE" as sender in a test for "unsigned transaction fails" caused false positive since COINBASE always verifies.

4. **Step-by-step approach works well**: Implementing and testing each feature before moving on caught issues early.

---

## 🔄 Carry-over Items

_None - all tasks completed_

---

## 📌 Notes

### Implementation Highlights

- **Key Generation**: ECDSA with secp256r1 curve via Java Cryptography Architecture
- **Address**: SHA-256 hash of public key, last 40 chars with 0x prefix
- **Signing**: SHA256withECDSA algorithm on transaction data
- **Verification**: Same algorithm, using sender's public key

### Files Changed

| File | Changes |
|------|---------|
| Wallet.java | +155 lines (new implementation) |
| Transaction.java | +74 lines (signature fields + verification) |
| WalletTest.java | +167 lines (new test class) |
| TransactionTest.java | +75 lines (signature tests) |

### Git Activity

```
Branch: sprint5/wallets
Commits:
- feat(wallet): Add ECDSA key pair generation
- feat(wallet): Add address generation from public key
- feat(wallet): Add transaction signing and verification
```

### Test Summary

| Test Class | Tests | Status |
|------------|-------|--------|
| WalletTest | 13 | ✅ All Pass |
| TransactionTest | 22 | ✅ All Pass |
| BlockchainTest | 19 | ✅ All Pass |
| BlockTest | 12 | ✅ All Pass |
| MiningTest | 9 | ✅ All Pass |
| HashUtilTest | 6 | ✅ All Pass |
| **Total** | **81** | ✅ **All Pass** |
