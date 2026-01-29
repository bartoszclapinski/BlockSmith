# Sprint 6: Economic System

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 6 |
| **Title** | Economic System |
| **Duration** | 1 week |
| **Phase** | Phase 6 |
| **Status** | In Progress (80%) |
| **Depends On** | Sprint 5 |

---

## 🎯 Goal

Implement the economic layer: mining rewards, balance tracking, and transaction validation based on available funds. This sprint makes the blockchain function like actual cryptocurrency.

---

## 📦 Deliverables

### 1. Mining Rewards (Coinbase Transaction)
- [x] Special transaction type where sender = "COINBASE" *(done in Sprint 4)*
- [x] Created automatically when block is mined *(done in Sprint 4)*
- [x] Amount = BlockchainConfig.MINING_REWARD *(done in Sprint 4)*
- [x] Recipient = miner's address *(done in Sprint 4)*
- [x] No signature required (system-generated) *(done in Sprint 5)*

### 2. Blockchain Class Updates
- [x] `minePendingTransactions(String minerAddress)` *(done in Sprint 4)*:
  1. Create coinbase transaction for miner
  2. Add coinbase + pending transactions to new block
  3. Mine the block
  4. Add to chain
  5. Clear pending pool
- [x] `getBalance(String address)` *(done in Sprint 4)*:
  - Loop through all blocks and transactions
  - Add received amounts, subtract sent amounts
  - Return final balance
- [ ] `getTransactionHistory(String address)` *(optional)*:
  - Return all transactions involving the address

### 3. Transaction Validation Enhancement
- [x] Check sender has sufficient balance before accepting transaction
- [x] Reject transactions where amount > sender's balance
- [x] Coinbase transactions bypass balance check
- [x] Reject manual COINBASE transactions from users
- [x] Track pending outgoing to prevent double-spending

### 4. Optional: Transaction Fees
- [ ] Add optional `fee` field to Transaction
- [ ] Fee goes to miner (added to coinbase reward)
- [ ] Higher fee = priority in pending pool (optional)

### 5. Unit Tests
- [x] `BlockchainTest.java` (economics):
  - Test mining reward is received *(existing)*
  - Test balance calculation is correct *(existing)*
  - [x] Test insufficient funds rejection *(new - 6 tests)*
  - [ ] Test transaction history *(optional)*
  - Test multiple transactions between wallets *(existing)*
- [x] `TransactionTest.java` (coinbase):
  - Test coinbase transaction creation *(existing)*
  - Test coinbase validation (no signature needed) *(existing)*

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | Miner receives reward after mining block | ✅ (Sprint 4) |
| 2 | Balance correctly calculated from transaction history | ✅ (Sprint 4) |
| 3 | Transaction rejected if sender has insufficient funds | ✅ |
| 4 | Coinbase transaction is valid without signature | ✅ (Sprint 5) |
| 5 | getTransactionHistory returns all related transactions | ⬜ (optional) |
| 6 | All unit tests pass (87 tests) | ✅ |

---

## 📝 Theory Comments Required

### Mining Reward Explanation
```java
/**
 * THEORY: Mining Reward (Block Reward)
 * 
 * PURPOSE: Incentive for miners to spend computational resources.
 * New coins are created "out of thin air" as reward.
 * 
 * COINBASE TRANSACTION:
 * - Special transaction with no sender (or sender = "COINBASE")
 * - First transaction in every block
 * - Creates new coins for the miner
 * - No signature required (system-generated)
 * 
 * BITCOIN:
 * - Started at 50 BTC per block (2009)
 * - Halves every 210,000 blocks (~4 years)
 * - Current (2024): 6.25 BTC
 * - Will reach 0 around year 2140
 * - Total supply capped at 21 million BTC
 * 
 * OUR MODEL: Fixed reward (no halving for simplicity)
 */
```

### Balance Calculation Explanation
```java
/**
 * THEORY: Balance Calculation (Account-based model)
 * 
 * Unlike traditional databases, blockchain doesn't store "current balance".
 * Balance is CALCULATED by scanning all transactions.
 * 
 * ALGORITHM:
 * 1. Start with balance = 0
 * 2. For each transaction in all blocks:
 *    - If recipient == address: balance += amount
 *    - If sender == address: balance -= amount
 * 3. Return final balance
 * 
 * EFFICIENCY: This is O(n) where n = total transactions.
 * Real cryptocurrencies use optimizations like UTXO set.
 * 
 * BITCOIN uses UTXO (Unspent Transaction Outputs) model.
 * ETHEREUM uses account-based model (like ours, but with state trie).
 */
```

---

## 🧪 Test Scenarios

### Mining Reward Tests

| Test | Setup | Expected |
|------|-------|----------|
| Reward received | Mine block, check miner balance | Balance = MINING_REWARD |
| Multiple mines | Mine 3 blocks | Balance = 3 * MINING_REWARD |
| Coinbase first | Check block transactions | Coinbase is first transaction |

### Balance Tests

| Test | Transactions | Expected Balance |
|------|--------------|------------------|
| Fresh wallet | None | 0 |
| Received only | A receives 50 | A: 50 |
| Sent some | A has 50, sends 20 to B | A: 30, B: 20 |
| Multiple | A→B(10), B→C(5), C→A(2) | A: -8, B: 5, C: 3 (relative) |

### Insufficient Funds Tests

| Test | Setup | Action | Expected |
|------|-------|--------|----------|
| Exact amount | Balance 50 | Send 50 | Accepted |
| Over amount | Balance 50 | Send 60 | Rejected |
| Zero balance | Balance 0 | Send 10 | Rejected |

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Status |
|---|------|----------------|--------|
| 1 | Implement coinbase transaction support | 30 min | ✅ (Sprint 4) |
| 2 | Implement minePendingTransactions() | 45 min | ✅ (Sprint 4) |
| 3 | Implement getBalance() | 30 min | ✅ (Sprint 4) |
| 4 | Implement getTransactionHistory() | 20 min | ⬜ (optional) |
| 5 | Add balance check to addTransaction() | 30 min | ✅ |
| 6 | (Optional) Implement transaction fees | 45 min | ⬜ (optional) |
| 7 | Write economics tests | 60 min | ✅ (6 new tests) |
| 8 | Write coinbase tests | 30 min | ✅ (Sprint 4/5) |
| 9 | Add theory comments | 20 min | ✅ (Sprint 4) |

**Total Estimated Time:** ~5-6 hours
**Actual Time (core features):** ~2 hours

---

## 🔗 Dependencies

- Sprint 5 must be completed (Wallets, Signatures)
- BlockchainConfig.MINING_REWARD
- BlockchainConfig.COINBASE_ADDRESS

---

## 💡 Implementation Hints

### minePendingTransactions
```java
public void minePendingTransactions(String minerAddress) {
    // Create coinbase transaction (reward)
    Transaction coinbase = new Transaction(
        BlockchainConfig.COINBASE_ADDRESS,
        minerAddress,
        BlockchainConfig.MINING_REWARD
    );
    // Coinbase doesn't need signature
    
    // Combine with pending transactions
    List<Transaction> blockTransactions = new ArrayList<>();
    blockTransactions.add(coinbase);
    blockTransactions.addAll(pendingTransactions);
    
    // Create and mine new block
    Block newBlock = new Block(
        chain.size(),
        getLatestBlock().getHash(),
        blockTransactions
    );
    newBlock.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
    
    // Add to chain and clear pending
    chain.add(newBlock);
    pendingTransactions.clear();
}
```

### getBalance
```java
public double getBalance(String address) {
    double balance = 0;
    
    for (Block block : chain) {
        for (Transaction tx : block.getTransactions()) {
            if (tx.getRecipient().equals(address)) {
                balance += tx.getAmount();
            }
            if (tx.getSender().equals(address)) {
                balance -= tx.getAmount();
            }
        }
    }
    
    return balance;
}
```

---

## 🚫 Out of Scope

- Halving mechanism - advanced feature
- Dynamic fees based on network congestion
- Multiple reward recipients (mining pools)
- Double-spend detection in mempool
