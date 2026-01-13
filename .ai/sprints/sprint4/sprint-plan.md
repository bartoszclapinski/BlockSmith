# Sprint 4: Transactions

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 4 |
| **Title** | Transactions |
| **Duration** | 1 week |
| **Phase** | Phase 4 |
| **Status** | Not Started |
| **Depends On** | Sprint 3 |

---

## 🎯 Goal

Implement the Transaction model with validation and Merkle tree support. Replace the simple string data in blocks with actual transactions.

---

## 📦 Deliverables

### 1. Transaction Class
- [ ] Fields:
  - `String transactionId` - unique hash of transaction
  - `String sender` - sender's address
  - `String recipient` - recipient's address
  - `double amount` - amount to transfer
  - `long timestamp` - when transaction was created
- [ ] Constructor with validation
- [ ] `calculateHash()` - creates unique transaction ID
- [ ] `isValid()` - validates transaction (amount > 0, non-empty addresses)
- [ ] Getters for all fields
- [ ] `toString()` for readable output

### 2. Block Class Updates
- [ ] Replace `String data` with `List<Transaction> transactions`
- [ ] Update `calculateHash()` to include transactions
- [ ] Add `calculateMerkleRoot()` method
- [ ] Add `String merkleRoot` field
- [ ] Update constructor to accept transactions

### 3. Blockchain Class Updates
- [ ] Add `List<Transaction> pendingTransactions` field
- [ ] `addTransaction(Transaction tx)` - adds to pending pool (with validation)
- [ ] Update `addBlock()` to use pending transactions
- [ ] Clear pending transactions after block is mined

### 4. Merkle Tree Implementation
- [ ] `calculateMerkleRoot()` algorithm:
  - Hash all transaction IDs
  - Pair and hash until single root remains
  - Handle odd number of transactions (duplicate last)

### 5. Unit Tests
- [ ] `TransactionTest.java`:
  - Test valid transaction creation
  - Test invalid amount rejected (0, negative)
  - Test empty sender/recipient rejected
  - Test transaction hash is unique
  - Test transaction hash is deterministic
- [ ] `BlockTest.java` (transactions):
  - Test block with transactions
  - Test Merkle root calculation
  - Test Merkle root changes when transactions change
- [ ] `BlockchainTest.java` (transactions):
  - Test pending transactions pool
  - Test transactions included in mined block

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | Transaction can be created with sender, recipient, amount | ⬜ |
| 2 | Invalid transactions are rejected (amount <= 0) | ⬜ |
| 3 | Block contains list of transactions | ⬜ |
| 4 | Merkle root is calculated correctly | ⬜ |
| 5 | Pending transactions are added to blocks | ⬜ |
| 6 | Pending pool is cleared after mining | ⬜ |
| 7 | All unit tests pass | ⬜ |

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

| Test | Input | Expected |
|------|-------|----------|
| Valid | sender="A", recipient="B", amount=10 | Transaction created, isValid()=true |
| Zero amount | amount=0 | isValid()=false or exception |
| Negative | amount=-5 | isValid()=false or exception |
| Empty sender | sender="" | isValid()=false or exception |
| Same hash | Same data twice | Same transactionId |
| Different hash | Different amounts | Different transactionId |

### Merkle Root Tests

| Test | Transactions | Expected |
|------|--------------|----------|
| Single tx | [A] | hash(A) |
| Two tx | [A, B] | hash(hash(A) + hash(B)) |
| Three tx | [A, B, C] | hash(hash(A,B) + hash(C,C)) |
| Four tx | [A, B, C, D] | hash(hash(A,B) + hash(C,D)) |
| Changed tx | Modify tx A | Root changes |

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Status |
|---|------|----------------|--------|
| 1 | Create Transaction class | 45 min | ⬜ |
| 2 | Implement transaction validation | 20 min | ⬜ |
| 3 | Update Block to use transactions | 30 min | ⬜ |
| 4 | Implement Merkle root calculation | 60 min | ⬜ |
| 5 | Update Blockchain for pending pool | 30 min | ⬜ |
| 6 | Write TransactionTest | 45 min | ⬜ |
| 7 | Update BlockTest | 30 min | ⬜ |
| 8 | Update BlockchainTest | 30 min | ⬜ |
| 9 | Add theory comments | 20 min | ⬜ |

**Total Estimated Time:** ~5-6 hours

---

## 🔗 Dependencies

- Sprint 3 must be completed (Blockchain class)
- HashUtil for transaction hashing

---

## 💡 Implementation Hints

### Merkle Root Algorithm
```java
public String calculateMerkleRoot() {
    if (transactions.isEmpty()) {
        return HashUtil.applySha256("");
    }
    
    List<String> hashes = new ArrayList<>();
    for (Transaction tx : transactions) {
        hashes.add(tx.getTransactionId());
    }
    
    while (hashes.size() > 1) {
        List<String> newLevel = new ArrayList<>();
        
        for (int i = 0; i < hashes.size(); i += 2) {
            String left = hashes.get(i);
            String right = (i + 1 < hashes.size()) 
                ? hashes.get(i + 1) 
                : left; // Duplicate if odd
            
            newLevel.add(HashUtil.applySha256(left + right));
        }
        
        hashes = newLevel;
    }
    
    return hashes.get(0);
}
```

---

## 🚫 Out of Scope

- Digital signatures on transactions - Sprint 5
- Balance checking before transaction - Sprint 6
- Transaction fees - Sprint 6 (optional)
- UTXO model - out of project scope
