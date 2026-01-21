# Sprint 4: Transactions

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 4 |
| **Title** | Transactions |
| **Duration** | 1 day |
| **Phase** | Phase 4 |
| **Status** | ✅ Completed |
| **Depends On** | Sprint 3 |

---

## 🎯 Goal

Implement the Transaction model with validation and Merkle tree support. Replace the simple string data in blocks with actual transactions.

---

## 📦 Deliverables

### 1. Transaction Class
- [x] Fields:
  - `String transactionId` - unique hash of transaction
  - `String sender` - sender's address
  - `String recipient` - recipient's address
  - `double amount` - amount to transfer
  - `long timestamp` - when transaction was created
- [x] Constructor with validation
- [x] `calculateHash()` - creates unique transaction ID
- [x] `isValid()` - validates transaction (amount > 0, non-empty addresses)
- [x] Getters for all fields
- [x] `toString()` for readable output

### 2. Block Class Updates
- [x] Replace `String data` with `List<Transaction> transactions`
- [x] Update `calculateHash()` to include merkleRoot
- [x] Add `calculateMerkleRoot()` method
- [x] Add `String merkleRoot` field
- [x] Update constructor to accept transactions
- [x] Keep legacy constructor for backward compatibility

### 3. Blockchain Class Updates
- [x] Add `List<Transaction> pendingTransactions` field
- [x] `addTransaction(Transaction tx)` - adds to pending pool (with validation)
- [x] `minePendingTransactions(minerAddress)` - mines pending txs into block
- [x] Clear pending transactions after block is mined
- [x] `getBalance(address)` - calculate balance from all transactions
- [x] `getPendingTransactions()` - returns unmodifiable list
- [x] `getPendingCount()` - returns pending count

### 4. Merkle Tree Implementation
- [x] `calculateMerkleRoot()` algorithm:
  - Hash all transaction IDs
  - Pair and hash until single root remains
  - Handle odd number of transactions (duplicate last)
  - Handle empty transactions (hash the data field)

### 5. Unit Tests
- [x] `TransactionTest.java`:
  - Test valid transaction creation
  - Test invalid amount rejected (0, negative)
  - Test empty sender/recipient rejected
  - Test transaction hash is unique
  - Test transaction hash is deterministic
  - Test toString format
- [x] `BlockTest.java` (transactions):
  - Test block with transactions
  - Test Merkle root calculation
  - Test Merkle root changes when transactions change
  - Test defensive copy of transactions
  - Test genesis block with empty transactions
- [x] `BlockchainTest.java` (transactions):
  - Test pending transactions pool
  - Test addTransaction accepts/rejects
  - Test minePendingTransactions
  - Test mining reward included
  - Test getBalance calculation
  - Test unmodifiable pending list

### 6. Demo Update
- [x] Added transaction demo section to BlockSmithDemo
- [x] Shows mining rewards, transfers, and balance tracking
- [x] Updated tamper detection to use merkleRoot

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | Transaction can be created with sender, recipient, amount | ✅ |
| 2 | Invalid transactions are rejected (amount <= 0) | ✅ |
| 3 | Block contains list of transactions | ✅ |
| 4 | Merkle root is calculated correctly | ✅ |
| 5 | Pending transactions are added to blocks | ✅ |
| 6 | Pending pool is cleared after mining | ✅ |
| 7 | All unit tests pass | ✅ |

---

## 📝 Theory Comments Required

### Transaction Explanation
```java
/**
 * THEORY: A transaction represents a transfer of value between addresses.
 * 
 * COMPONENTS:
 * - sender: The address sending the funds
 * - recipient: The address receiving the funds
 * - amount: How much is being transferred
 * - timestamp: When the transaction was created
 * - transactionId: Unique hash identifying this transaction
 * 
 * VALIDATION (basic):
 * - amount must be positive
 * - sender and recipient must be non-empty
 * 
 * BITCOIN vs OUR MODEL:
 * Bitcoin uses UTXO (Unspent Transaction Outputs) model.
 * We use simpler account-based model (like Ethereum).
 */
```

### Merkle Tree Explanation
```java
/**
 * THEORY: Merkle Tree (or Hash Tree) efficiently summarizes all transactions.
 * 
 * STRUCTURE:
 *              [Merkle Root]
 *              /           \
 *         [Hash AB]      [Hash CD]
 *         /      \       /      \
 *     [Hash A] [Hash B] [Hash C] [Hash D]
 *        |        |        |        |
 *      [Tx A]   [Tx B]   [Tx C]   [Tx D]
 * 
 * ALGORITHM:
 * 1. Hash each transaction
 * 2. Pair adjacent hashes and hash together
 * 3. Repeat until single root remains
 * 4. If odd number, duplicate the last hash
 * 
 * BENEFITS:
 * - Efficiently proves transaction is in block (O(log n))
 * - Any change to any transaction changes the root
 * - Used in SPV (Simplified Payment Verification)
 */
```

---

## 🧪 Test Scenarios

### Transaction Tests

| Test | Input | Expected | Result |
|------|-------|----------|--------|
| Valid | sender="A", recipient="B", amount=10 | Transaction created, isValid()=true | ✅ |
| Zero amount | amount=0 | isValid()=false | ✅ |
| Negative | amount=-5 | isValid()=false | ✅ |
| Empty sender | sender="" | isValid()=false | ✅ |
| Same hash | Same data twice | Same transactionId | ✅ |
| Different hash | Different amounts | Different transactionId | ✅ |

### Merkle Root Tests

| Test | Transactions | Expected | Result |
|------|--------------|----------|--------|
| Single tx | [A] | hash(A) | ✅ |
| Two tx | [A, B] | hash(hash(A) + hash(B)) | ✅ |
| Three tx | [A, B, C] | hash(hash(A,B) + hash(C,C)) | ✅ |
| Four tx | [A, B, C, D] | hash(hash(A,B) + hash(C,D)) | ✅ |
| Changed tx | Modify tx A | Root changes | ✅ |

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Actual Time | Status |
|---|------|----------------|-------------|--------|
| 1 | Create Transaction class | 45 min | ~30 min | ✅ |
| 2 | Implement transaction validation | 20 min | ~15 min | ✅ |
| 3 | Update Block to use transactions | 30 min | ~45 min | ✅ |
| 4 | Implement Merkle root calculation | 60 min | ~30 min | ✅ |
| 5 | Update Blockchain for pending pool | 30 min | ~45 min | ✅ |
| 6 | Write TransactionTest | 45 min | ~30 min | ✅ |
| 7 | Update BlockTest | 30 min | ~20 min | ✅ |
| 8 | Update BlockchainTest | 30 min | ~25 min | ✅ |
| 9 | Update BlockSmithDemo | 20 min | ~30 min | ✅ |

**Total Estimated Time:** ~5-6 hours
**Total Actual Time:** ~4.5 hours

---

## 🔗 Dependencies

- Sprint 3 must be completed (Blockchain class) ✅
- HashUtil for transaction hashing ✅

---

## 💡 Implementation Notes

### Key Design Decisions

1. **Backward Compatibility**: Kept legacy `Block(index, data, prevHash)` constructor for existing code
2. **Defensive Copies**: `getTransactions()` returns a copy to prevent external modification
3. **Mining Reward**: Added COINBASE transaction (50 BSC) as first transaction in each mined block
4. **Balance Calculation**: Simple scan of all transactions (not UTXO-based)

### Files Changed

| File | Lines Changed |
|------|---------------|
| Transaction.java | +126 (new) |
| Block.java | +130 (updated) |
| Blockchain.java | +150 (updated) |
| TransactionTest.java | +186 (new) |
| BlockTest.java | +60 (updated) |
| BlockchainTest.java | +80 (updated) |
| BlockSmithDemo.java | +65 (updated) |

---

## 🚫 Out of Scope

- Digital signatures on transactions - Sprint 5
- Balance checking before transaction - Sprint 6
- Transaction fees - Sprint 6 (optional)
- UTXO model - out of project scope
