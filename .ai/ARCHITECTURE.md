# 🏗️ BlockSmith - Architecture Guide

> Detailed description of all classes, their relationships, and implementation details.

---

## 📊 Class Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BLOCKSMITH                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────┐         ┌─────────────────┐         ┌───────────────┐ │
│  │  BlockSmithDemo │         │   Blockchain    │◄────────│    Block      │ │
│  │  (Entry Point)  │────────▶│   (Chain Mgr)   │         │  (Data Unit)  │ │
│  └─────────────────┘         └────────┬────────┘         └───────┬───────┘ │
│                                       │                          │         │
│                                       │ contains                 │ contains│
│                                       ▼                          ▼         │
│                              ┌─────────────────┐         ┌───────────────┐ │
│                              │   Transaction   │         │  Merkle Root  │ │
│                              │   (Transfer)    │────────▶│  (Summary)    │ │
│                              └─────────────────┘         └───────────────┘ │
│                                       │                                    │
│                                       │ signed by                          │
│                                       ▼                                    │
│                              ┌─────────────────┐                           │
│                              │     Wallet      │                           │
│                              │  (Keys/Signing) │  ✅ Complete              │
│                              └─────────────────┘                           │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐ │
│  │                           UTILITIES                                    │ │
│  │  ┌──────────────┐  ┌──────────────────┐  ┌────────────────────────┐   │ │
│  │  │   HashUtil   │  │ BlockchainConfig │  │    BlockExplorer       │   │ │
│  │  │  (SHA-256)   │  │   (Constants)    │  │  (Viewer) ← TODO       │   │ │
│  │  └──────────────┘  └──────────────────┘  └────────────────────────┘   │ │
│  └───────────────────────────────────────────────────────────────────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Package: `com.blocksmith.core`

### Block.java ✅

**Purpose**: Represents a single block in the blockchain.

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `index` | `int` | Block number in chain (0 = Genesis) |
| `timestamp` | `long` | Creation time (milliseconds) |
| `data` | `String` | Legacy data field (for backward compat) |
| `transactions` | `List<Transaction>` | List of transactions in block |
| `merkleRoot` | `String` | SHA-256 hash summarizing all transactions |
| `previousHash` | `String` | Hash of previous block (chain link) |
| `hash` | `String` | This block's hash |
| `nonce` | `int` | Proof-of-Work solution |

**Constructors**:
```java
// New: Transaction-based blocks
Block(int index, List<Transaction> transactions, String previousHash)

// Legacy: Simple data blocks (backward compatibility)
Block(int index, String data, String previousHash)
```

**Key Methods**:
| Method | Returns | Description |
|--------|---------|-------------|
| `calculateMerkleRoot()` | `String` | Builds Merkle tree from transactions |
| `calculateHash()` | `String` | SHA-256 of block data (uses merkleRoot) |
| `mineBlock(difficulty)` | `long` | Finds valid nonce, returns mining time |
| `createGenesisBlock()` | `Block` | Factory for Genesis block |
| `getTransactions()` | `List<Transaction>` | Returns defensive copy |
| `getTransactionCount()` | `int` | Number of transactions |

**Hash Formula**:
```java
hash = SHA256(index + timestamp + merkleRoot + previousHash + nonce)
```

---

### Blockchain.java ✅

**Purpose**: Manages the chain of blocks and transaction pool.

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `chain` | `List<Block>` | The blockchain (immutable view exposed) |
| `pendingTransactions` | `List<Transaction>` | Mempool - waiting for mining |

**Constructor**:
```java
Blockchain()  // Creates chain with mined Genesis block
```

**Key Methods**:
| Method | Returns | Description |
|--------|---------|-------------|
| `addBlock(String data)` | `Block` | Legacy: Add block with string data |
| `addTransaction(tx)` | `boolean` | Add tx to pending pool (validates first) |
| `minePendingTransactions(miner)` | `Block` | Mine pending txs + reward |
| `getBalance(address)` | `double` | Calculate balance from all txs |
| `isChainValid()` | `boolean` | Validate entire chain integrity |
| `getLatestBlock()` | `Block` | Get most recent block |
| `getChain()` | `List<Block>` | Unmodifiable view of chain |
| `getPendingTransactions()` | `List<Transaction>` | Unmodifiable pending list |
| `getPendingCount()` | `int` | Number of pending txs |
| `printChain()` | `void` | Print formatted chain summary |

**Mining Process**:
1. Create COINBASE reward transaction (50 BSC to miner)
2. Collect all pending transactions
3. Create new block with all transactions
4. Mine block (find valid nonce)
5. Add block to chain
6. Clear pending pool

---

### Transaction.java ✅

**Purpose**: Represents a transfer of value between addresses.

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `transactionId` | `String` | Unique hash (SHA-256 of tx data) |
| `sender` | `String` | Sender's address |
| `recipient` | `String` | Recipient's address |
| `amount` | `double` | Amount to transfer |
| `timestamp` | `long` | When tx was created |
| `signature` | `byte[]` | ECDSA digital signature |
| `senderPublicKey` | `PublicKey` | Sender's public key for verification |

**Constructor**:
```java
Transaction(String sender, String recipient, double amount)
```

**Key Methods**:
| Method | Returns | Description |
|--------|---------|-------------|
| `calculateHash()` | `String` | Generate unique transaction ID |
| `isValid()` | `boolean` | Validate: amount > 0, non-empty addresses |
| `verifySignature()` | `boolean` | Verify ECDSA signature |
| `setSignature(byte[])` | `void` | Set signature (called by Wallet) |
| `setSenderPublicKey(PublicKey)` | `void` | Set public key (called by Wallet) |
| `toString()` | `String` | Formatted: `Transaction{id=..., A -> B: 50.00}` |

**Validation Rules**:
- `amount > 0` (positive transfers only)
- `sender` not null or empty
- `recipient` not null or empty

**Signature Verification**:
```java
public boolean verifySignature() {
    // COINBASE transactions don't need signatures
    if (sender.equals("COINBASE")) return true;
    
    // Must have signature and public key
    if (signature == null || senderPublicKey == null) return false;
    
    // Verify using ECDSA
    Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA");
    ecdsaVerify.initVerify(senderPublicKey);
    ecdsaVerify.update((sender + recipient + amount + timestamp).getBytes());
    return ecdsaVerify.verify(signature);
}
```

---

### Wallet.java ✅

**Purpose**: Manages cryptographic keys and transaction signing.

**Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `privateKey` | `PrivateKey` | ECDSA private key (NEVER exposed) |
| `publicKey` | `PublicKey` | ECDSA public key |
| `address` | `String` | Derived address (0x + 40 hex chars) |

**Constructor**:
```java
Wallet()  // Generates new ECDSA key pair and address
```

**Key Methods**:
| Method | Returns | Description |
|--------|---------|-------------|
| `getAddress()` | `String` | Get wallet address (0x...) |
| `getPublicKey()` | `PublicKey` | Get public key |
| `signTransaction(tx)` | `void` | Sign transaction with private key |

**Key Generation**:
```java
private void generateKeyPair() {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
    keyGen.initialize(ecSpec, SecureRandom.getInstanceStrong());
    
    KeyPair keyPair = keyGen.generateKeyPair();
    this.privateKey = keyPair.getPrivate();
    this.publicKey = keyPair.getPublic();
}
```

**Address Generation**:
```java
private String generateAddress() {
    byte[] publicKeyBytes = publicKey.getEncoded();
    String hash = HashUtil.applySha256(bytesToHex(publicKeyBytes));
    String shortHash = hash.substring(hash.length() - 40);  // Last 20 bytes
    return "0x" + shortHash;
}
```

**Transaction Signing**:
```java
public void signTransaction(Transaction tx) {
    // Verify sender matches wallet
    if (!tx.getSender().equals(this.address)) {
        throw new IllegalStateException("Cannot sign: address mismatch");
    }
    
    Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
    ecdsaSign.initSign(privateKey);
    ecdsaSign.update((sender + recipient + amount + timestamp).getBytes());
    
    tx.setSignature(ecdsaSign.sign());
    tx.setSenderPublicKey(this.publicKey);
}
```

**Security Notes**:
- Private key is NEVER exposed via getter
- Only the wallet owner can sign transactions
- Address is derived from public key hash (one-way)

---

## 📦 Package: `com.blocksmith.util`

### HashUtil.java ✅

**Purpose**: Cryptographic hashing utilities.

**Methods**:
| Method | Returns | Description |
|--------|---------|-------------|
| `applySha256(String input)` | `String` | SHA-256 hash as 64-char hex string |

**Implementation**:
```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
// Convert to hex string (64 characters)
```

---

### BlockchainConfig.java ✅

**Purpose**: Central configuration constants.

**Constants**:
| Constant | Value | Description |
|----------|-------|-------------|
| `MINING_DIFFICULTY` | `4` | Leading zeros required in hash |
| `MINING_REWARD` | `50.0` | BSC reward per mined block |
| `TRANSACTION_FEE` | `0.1` | Optional fee (future use) |
| `GENESIS_PREV_HASH` | `"0"` | Previous hash for Genesis |
| `COINBASE_ADDRESS` | `"COINBASE"` | Mining reward sender |
| `CURRENCY_SYMBOL` | `"BSC"` | Display symbol |

---

### BlockExplorer.java ⬜ (Sprint 7 - TODO)

**Planned Purpose**: Interactive blockchain viewer.

**Planned Methods**:
| Method | Returns | Description |
|--------|---------|-------------|
| `printChainSummary()` | `void` | Overview of entire chain |
| `printBlockDetails(index)` | `void` | Details of specific block |
| `printTransactionHistory(addr)` | `void` | All txs for an address |
| `printAddressBalance(addr)` | `void` | Balance of an address |
| `printPendingTransactions()` | `void` | Show mempool |

---

## 📦 Entry Point

### BlockSmithDemo.java ✅

**Purpose**: Main demo application showcasing all features.

**Demo Sections**:
1. **Block Mining Demo** - Create and mine individual blocks
2. **Chain Summary** - Display mined blocks
3. **Mining Statistics** - Time and nonce analysis
4. **Difficulty Comparison** - Shows exponential scaling
5. **Blockchain Demo** - Full chain with validation
6. **Transaction Demo** - Mining rewards, transfers, balances
7. **Tamper Detection** - Demonstrates chain security

**Run With**:
```bash
mvn compile -q; java -cp target/classes com.blocksmith.BlockSmithDemo
```

---

## 🔄 Data Flow

### 1. Transaction Creation (with Wallet)
```
Wallet created → generateKeyPair() → address derived
    ↓
User creates Transaction(wallet.getAddress(), "Bob", 50.0)
    ↓
wallet.signTransaction(tx) → signature + publicKey set
    ↓
Transaction.isValid() checks rules
    ↓
Blockchain.addTransaction(tx) adds to pendingTransactions
```

### 2. Mining Process
```
Blockchain.minePendingTransactions("Miner1")
    ↓
Create COINBASE → Miner1 (50 BSC)
    ↓
Collect all pendingTransactions
    ↓
Create Block with transactions
    ↓
Block.calculateMerkleRoot()
    ↓
Block.mineBlock(difficulty=4)
    ↓
Find nonce where hash starts with "0000"
    ↓
Add block to chain, clear pending pool
```

### 3. Balance Calculation
```
Blockchain.getBalance("Alice")
    ↓
For each block in chain:
    For each transaction:
        If sender == "Alice": balance -= amount
        If recipient == "Alice": balance += amount
    ↓
Return total balance
```

### 4. Signature Verification
```
Transaction.verifySignature()
    ↓
If sender == "COINBASE" → return true (exempt)
    ↓
If signature or publicKey is null → return false
    ↓
Reconstruct signed data: sender + recipient + amount + timestamp
    ↓
Use publicKey to verify signature → return result
```

---

## 🌳 Merkle Tree Implementation

**Algorithm** (in `Block.calculateMerkleRoot()`):

```
Transactions: [TxA, TxB, TxC, TxD]
                    ↓
Level 0 (leaves): [hash(A), hash(B), hash(C), hash(D)]
                    ↓
Level 1:          [hash(AB), hash(CD)]
                    ↓
Level 2 (root):   [hash(ABCD)] ← Merkle Root
```

**Odd number handling**: Duplicate last hash
```
[TxA, TxB, TxC] → [hash(A), hash(B), hash(C), hash(C)]
```

**Empty transactions**: Hash the `data` field instead
```
if (transactions.isEmpty()) return HashUtil.applySha256(data);
```

---

## 🔗 Chain Validation

**`Blockchain.isChainValid()` checks**:

1. **Genesis Block**:
   - index == 0
   - previousHash == "0"
   - hash is correctly calculated
   - hash meets difficulty

2. **Each Block (i > 0)**:
   - hash == calculateHash() (data integrity)
   - previousHash == chain[i-1].hash (link integrity)
   - hash starts with "0000" (PoW validity)

---

## 📊 Test Architecture

```
src/test/java/com/blocksmith/
├── core/
│   ├── BlockTest.java          # Block creation, mining, transactions
│   ├── BlockchainTest.java     # Chain management, validation, tx pool
│   ├── MiningTest.java         # PoW mechanics, difficulty scaling
│   ├── TransactionTest.java    # Tx creation, validation, signatures
│   └── WalletTest.java         # Key generation, addresses, signing
└── util/
    └── HashUtilTest.java       # SHA-256 basics
```

**Test Naming Convention**:
```java
@Test
@DisplayName("Human readable description")
void methodName_scenario_expectedResult() { ... }
```

---

*Last updated: 2026-01-27*
