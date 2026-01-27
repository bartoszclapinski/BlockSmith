# 🚀 BlockSmith - LLM Onboarding Guide

> **Read this file first when starting a new chat/session about BlockSmith.**

---

## 📋 Quick Reference

| Item | Value |
|------|-------|
| **Project** | BlockSmith - Educational Blockchain in Java |
| **Language** | Java 20 |
| **Build Tool** | Maven 3.9.x |
| **Test Framework** | JUnit 5 |
| **Current Phase** | Phase 1: Core Blockchain |
| **Current Sprint** | Sprint 6 (Economic System) - Next |
| **Last Completed** | Sprint 5 (Wallets & Signatures) |
| **Total Tests** | 81 (all passing) |

---

## 🎯 Project Goal

Build a **functional blockchain implementation from scratch** for educational purposes. The project demonstrates:
- Cryptographic hashing (SHA-256)
- Proof-of-Work mining
- Transaction management with Merkle trees
- Digital signatures (ECDSA)
- Wallet address generation
- Balance tracking

---

## 📁 Project Structure

```
BlockSmith/
├── .ai/                              # AI/LLM documentation (READ FIRST!)
│   ├── ONBOARDING.md                 # ← YOU ARE HERE
│   ├── ARCHITECTURE.md               # Detailed class descriptions
│   ├── CONVENTIONS.md                # Code style guide
│   ├── prd.md                        # Product requirements
│   ├── tech-stack.md                 # Technologies used
│   ├── roadmap.md                    # Full project roadmap
│   └── sprints/                      # Sprint plans and logs
│       ├── sprint0/ through sprint7/ # Phase 1 sprints
│       └── sprint-bonus/             # Optional features
│
├── src/main/java/com/blocksmith/
│   ├── BlockSmithDemo.java           # Main demo application
│   ├── core/                         # Core blockchain classes
│   │   ├── Block.java                # ✅ Complete
│   │   ├── Blockchain.java           # ✅ Complete
│   │   ├── Transaction.java          # ✅ Complete (with signatures)
│   │   └── Wallet.java               # ✅ Complete (Sprint 5)
│   └── util/                         # Utility classes
│       ├── HashUtil.java             # ✅ Complete
│       ├── BlockchainConfig.java     # ✅ Complete
│       └── BlockExplorer.java        # ⬜ TODO (Sprint 7)
│
├── src/test/java/com/blocksmith/
│   ├── core/
│   │   ├── BlockTest.java            # 12 tests
│   │   ├── BlockchainTest.java       # 19 tests
│   │   ├── MiningTest.java           # 9 tests
│   │   ├── TransactionTest.java      # 22 tests
│   │   └── WalletTest.java           # 13 tests
│   └── util/
│       └── HashUtilTest.java         # 6 tests
│
├── pom.xml                           # Maven configuration
└── README.md                         # Public documentation
```

---

## ✅ What's Already Implemented

### Sprint 0-1: Fundamentals
- `HashUtil` - SHA-256 hashing
- `Block` - Block structure with hash calculation
- `BlockchainConfig` - Configuration constants

### Sprint 2: Proof-of-Work
- `Block.mineBlock(difficulty)` - Mining with nonce search
- Difficulty-based hash validation (leading zeros)

### Sprint 3: Blockchain Management
- `Blockchain` - Chain management
- Genesis block creation
- Chain validation and tamper detection
- `addBlock(data)` - Add blocks with string data

### Sprint 4: Transactions
- `Transaction` - Transaction model with validation
- `Block` - Now supports `List<Transaction>` 
- `Block.calculateMerkleRoot()` - Merkle tree implementation
- `Blockchain.addTransaction(tx)` - Pending transaction pool
- `Blockchain.minePendingTransactions(miner)` - Mine with rewards
- `Blockchain.getBalance(address)` - Balance calculation
- Mining rewards (50 BSC from COINBASE)

### Sprint 5: Wallets & Signatures ← **LATEST**
- `Wallet` - ECDSA key pair generation (secp256r1)
- `Wallet.getAddress()` - Ethereum-style address (0x + 40 hex)
- `Wallet.signTransaction()` - Sign transactions with private key
- `Transaction.verifySignature()` - Verify transaction signatures
- COINBASE transactions exempt from signature requirement

---

## ⬜ What's NOT Implemented Yet

### Sprint 6: Economic System (NEXT)
- Balance validation before transfers
- Insufficient funds rejection
- Transaction fees (optional)

### Sprint 7: Demo & Documentation
- `BlockExplorer.java` - Currently a placeholder
- Interactive blockchain viewer
- Full demo scenarios

---

## 🔧 Common Commands

```bash
# Compile the project
mvn compile

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=WalletTest

# Run the demo (PowerShell)
mvn compile -q; java -cp target/classes com.blocksmith.BlockSmithDemo

# Check git status
git status

# Current branch format: sprint{N}/{feature-name}
```

---

## 📝 Key Design Decisions

### 1. Backward Compatibility
Block class has TWO constructors:
- `Block(index, List<Transaction>, prevHash)` - New transaction-based
- `Block(index, String data, prevHash)` - Legacy for simple data

### 2. Merkle Root in Hash
`calculateHash()` uses `merkleRoot` (not `data`) in hash calculation:
```java
String dataToHash = index + timestamp + merkleRoot + previousHash + nonce;
```

### 3. Defensive Copies
`getTransactions()` returns `new ArrayList<>(transactions)` to prevent external modification.

### 4. Mining Rewards
Implemented as COINBASE transactions (first tx in each block):
```java
Transaction rewardTx = new Transaction("COINBASE", minerAddress, 50.0);
```

### 5. Balance Calculation
Simple scan of all transactions (not UTXO-based):
```java
for each block: for each tx: if sender==address: balance -= amount; if recipient==address: balance += amount
```

### 6. Wallet Signs Transactions
`wallet.signTransaction(tx)` - Wallet class owns the private key and performs signing:
- Validates sender address matches wallet
- Sets signature bytes and public key on transaction

### 7. COINBASE Exception
Mining rewards don't require signatures since they're system-generated:
```java
if (sender.equals("COINBASE")) return true; // Always valid
```

---

## 🧪 Test Coverage

| Class | Tests | Coverage |
|-------|-------|----------|
| HashUtilTest | 6 | SHA-256 basics |
| BlockTest | 12 | Block creation, mining, transactions, Merkle |
| BlockchainTest | 19 | Chain management, validation, tx pool |
| MiningTest | 9 | PoW mechanics, difficulty scaling |
| TransactionTest | 22 | Tx creation, validation, signatures |
| WalletTest | 13 | Key generation, addresses, signing |
| **Total** | **81** | All passing ✅ |

---

## 🌳 Git Workflow

- **Main branch**: `main` (stable, protected)
- **Sprint branches**: `sprint{N}/{feature}` (e.g., `sprint5/wallets`)
- **Commits**: Descriptive messages with sprint context
- **After sprint**: Push branch, create PR, merge to main

Current branch: `sprint5/wallets` (completed, ready for merge)

---

## ⚠️ Important Notes for LLMs

1. **Always check `.ai/sprints/` for current sprint status**
2. **Run tests after changes**: `mvn test`
3. **Keep THEORY comments** in code - they're educational
4. **Follow existing code style** - see `CONVENTIONS.md`
5. **User prefers** the existing demo style in `BlockSmithDemo.java`
6. **PowerShell syntax**: Use `;` not `&&` for command chaining
7. **Update sprint docs** after completing tasks
8. **User implements code** - LLM guides and explains, user writes

---

## 📚 Related Documentation

| File | Purpose |
|------|---------|
| `ARCHITECTURE.md` | Detailed class descriptions and relationships |
| `CONVENTIONS.md` | Code style, naming, comment format |
| `prd.md` | Full product requirements |
| `roadmap.md` | Project phases and timeline |
| `sprints/sprint{N}/sprint-plan.md` | Sprint deliverables |
| `sprints/sprint{N}/sprint-log.md` | Sprint progress log |

---

## 🎯 Next Steps (Sprint 6)

1. Update `Blockchain.addTransaction()`:
   - Check sender balance before accepting transaction
   - Reject if insufficient funds

2. Update `Transaction.isValid()`:
   - Optionally verify signature in validation

3. Write tests for balance validation

4. Update `BlockSmithDemo.java` with wallet demo

---

*Last updated: 2026-01-27 (after Sprint 5 completion)*
