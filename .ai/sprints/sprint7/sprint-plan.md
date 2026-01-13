# Sprint 7: Demo and Documentation

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 7 |
| **Title** | Demo and Documentation |
| **Duration** | 1 week |
| **Phase** | Phase 7 |
| **Status** | Not Started |
| **Depends On** | Sprint 6 |

---

## 🎯 Goal

Create an interactive demo showcasing all blockchain features and build the BlockExplorer utility for querying the blockchain. Complete project documentation.

---

## 📦 Deliverables

### 1. BlockExplorer Class
- [ ] `printChainSummary()` - overview of entire blockchain:
  - Total blocks
  - Total transactions
  - Chain validity status
  - Latest block info
- [ ] `printBlockDetails(int index)` - detailed view of single block:
  - Index, timestamp, hash, previousHash
  - Nonce, mining difficulty
  - Merkle root
  - All transactions in block
- [ ] `printTransactionHistory(String address)` - all transactions for address:
  - Sent and received
  - Timestamps and amounts
  - Running balance
- [ ] `printAddressBalance(String address)` - current balance
- [ ] `printPendingTransactions()` - transactions waiting to be mined

### 2. BlockSmithDemo Class
- [ ] Complete demonstration scenario:
  1. Create blockchain (Genesis block)
  2. Create 3 wallets (Alice, Bob, Charlie)
  3. Mine initial blocks to give Alice coins
  4. Transfer coins between wallets
  5. Mine blocks with transactions
  6. Show balances
  7. Demonstrate chain validation
  8. Demonstrate tamper detection
  9. Show BlockExplorer features
- [ ] Pretty console output with formatting
- [ ] Clear section headers and explanations

### 3. Mining Benchmark
- [ ] Demonstrate mining time vs difficulty:
  - Mine with difficulty 1, 2, 3, 4, 5
  - Show time and nonce for each
  - Display exponential growth

### 4. Console Output Formatting
- [ ] Box drawing for sections (┌ ─ ┐ │ └ ┘)
- [ ] Consistent spacing and alignment
- [ ] Color codes (optional, may not work in all terminals)
- [ ] Progress indicators for mining

### 5. README.md
- [ ] Project description
- [ ] Features list
- [ ] Prerequisites (Java 20, Maven)
- [ ] Build instructions
- [ ] Run instructions
- [ ] Example output
- [ ] Project structure
- [ ] Learning objectives
- [ ] References

### 6. Unit Tests
- [ ] `BlockExplorerTest.java`:
  - Test chain summary output
  - Test block details retrieval
  - Test transaction history
- [ ] `DemoTest.java` (optional):
  - Test demo runs without errors

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | BlockExplorer provides all query methods | ⬜ |
| 2 | Demo showcases all features in logical order | ⬜ |
| 3 | Mining benchmark shows exponential time growth | ⬜ |
| 4 | Console output is formatted and readable | ⬜ |
| 5 | README contains all required sections | ⬜ |
| 6 | `mvn exec:java` runs the demo successfully | ⬜ |
| 7 | All unit tests pass | ⬜ |

---

## 🎨 Console Output Format

### Section Header Example
```
╔══════════════════════════════════════════════════════════════╗
║                    BLOCKSMITH DEMO                           ║
║              Educational Blockchain in Java                  ║
╚══════════════════════════════════════════════════════════════╝
```

### Block Display Example
```
┌─────────────────────────────────────────────────────────────┐
│ BLOCK #1                                                     │
├─────────────────────────────────────────────────────────────┤
│ Timestamp:    2026-01-15 14:30:22                           │
│ Previous Hash: 0000abc123...                                 │
│ Hash:          0000def456...                                 │
│ Nonce:         12847                                         │
│ Merkle Root:   789xyz...                                     │
│ Transactions:  3                                             │
└─────────────────────────────────────────────────────────────┘
```

### Transaction Display Example
```
  ┌──────────────────────────────────────────────────────────┐
  │ TX: abc123def456...                                       │
  │ From: 0x71C7656EC7ab88b098defB751B7401B5f6d8976F         │
  │ To:   0x8Ba1f109551bD432803012645Ac136ddd64DBA72         │
  │ Amount: 25.0 BSC                                          │
  └──────────────────────────────────────────────────────────┘
```

---

## 📝 Demo Scenario Script

```
=== BLOCKSMITH DEMO ===

1. INITIALIZATION
   - Creating new blockchain...
   - Genesis block created!

2. WALLET CREATION
   - Creating Alice's wallet: 0x71C7...
   - Creating Bob's wallet: 0x8Ba1...
   - Creating Charlie's wallet: 0x2f3e...

3. INITIAL MINING (Alice is miner)
   - Mining block #1... Done! (234ms, nonce: 12847)
   - Alice balance: 50.0 BSC

4. TRANSACTIONS
   - Alice sends 10 BSC to Bob
   - Alice sends 5 BSC to Charlie
   
5. MINING BLOCK #2
   - Mining... Done!
   - Transactions included: 3 (1 coinbase + 2 transfers)

6. BALANCE CHECK
   - Alice: 85.0 BSC (100 mined - 15 sent)
   - Bob: 10.0 BSC
   - Charlie: 5.0 BSC

7. CHAIN VALIDATION
   - Validating blockchain... VALID ✓

8. TAMPER DETECTION DEMO
   - Tampering with block #1 data...
   - Validating blockchain... INVALID ✗
   - Tampering detected!

9. BLOCK EXPLORER
   - Chain summary: 3 blocks, 5 transactions
   - Block #1 details: [...]
   - Alice's transaction history: [...]

10. MINING BENCHMARK
    - Difficulty 1: 0.5ms (nonce: 3)
    - Difficulty 2: 2ms (nonce: 47)
    - Difficulty 3: 18ms (nonce: 891)
    - Difficulty 4: 156ms (nonce: 15234)
    - Difficulty 5: 1847ms (nonce: 187562)

=== DEMO COMPLETE ===
```

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Status |
|---|------|----------------|--------|
| 1 | Implement BlockExplorer class | 90 min | ⬜ |
| 2 | Create console formatting utilities | 45 min | ⬜ |
| 3 | Implement BlockSmithDemo scenario | 90 min | ⬜ |
| 4 | Add mining benchmark | 30 min | ⬜ |
| 5 | Write README.md | 60 min | ⬜ |
| 6 | Write BlockExplorerTest | 45 min | ⬜ |
| 7 | Final testing and polish | 60 min | ⬜ |

**Total Estimated Time:** ~7-8 hours

---

## 🔗 Dependencies

- Sprint 6 must be completed (all features implemented)
- All previous sprints' functionality working

---

## 💡 Implementation Hints

### Console Box Drawing
```java
public class ConsoleFormatter {
    public static void printHeader(String title) {
        int width = 60;
        System.out.println("╔" + "═".repeat(width) + "╗");
        System.out.println("║" + center(title, width) + "║");
        System.out.println("╚" + "═".repeat(width) + "╝");
    }
    
    private static String center(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text + " ".repeat(width - padding - text.length());
    }
}
```

### Demo Main Method
```java
public static void main(String[] args) {
    BlockSmithDemo demo = new BlockSmithDemo();
    
    demo.printWelcome();
    demo.createBlockchain();
    demo.createWallets();
    demo.mineInitialBlocks();
    demo.performTransactions();
    demo.mineTransactionBlocks();
    demo.showBalances();
    demo.validateChain();
    demo.demonstrateTamperDetection();
    demo.showBlockExplorer();
    demo.runMiningBenchmark();
    demo.printGoodbye();
}
```

---

## 🚫 Out of Scope

- GUI interface
- Web interface
- Interactive CLI (command input)
- Real-time updates
