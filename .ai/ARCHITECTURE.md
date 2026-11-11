# 🏗️ BlockSmith - Architecture Guide

> Detailed description of all classes, their relationships, and implementation details.
>
> The diagram and the `core` / `util` sections below cover Phase 1 in depth. Phases 2-3
> (`network`, `api`, `contract`, web dashboard) are summarised in their own sections further
> down; `STATUS.md` has the per-class implementation table.

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
│  │  │  (SHA-256)   │  │   (Constants)    │  │  (Viewer) ← stub only  │   │ │
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

### BlockExplorer.java ⬜ (stub only)

> Still an empty placeholder. Its role (browsing blocks, balances, the mempool) is covered
> in practice by the REST API + web dashboard (Sprints 12-13). Implement or remove later.

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
mvn exec:java -Dexec.mainClass=com.blocksmith.BlockSmithDemo
```

### BlockSmithNode.java ✅ (Sprint 13a)

**Purpose**: Runnable node: P2P `Node` + `ApiServer` + dashboard in one process. Default
`mvn exec:java` target and jar main class.

```bash
mvn exec:java                              # P2P on NetworkConfig.DEFAULT_PORT, API + dashboard on :7070
mvn exec:java -Dexec.args="18555 18580"    # custom [p2pPort] [apiPort]
mvn exec:java -Dexec.args="8335 7070 --restore backup.zip"   # install a backup first (Sprint 16d)
```

Since Sprint 16 it opens an `H2ChainStore` under `data/node-<p2pPort>/` (override with
`-Dblocksmith.dataDir`), restores the chain by replay, enables backups in `<dataDir>/backups/`,
and closes the store on shutdown.

---

## 📦 Package: `com.blocksmith.network` (Phase 2, Sprints 8-11)

TCP peer-to-peer layer. Messages are JSON over sockets.

| Class | Role |
|-------|------|
| `Node` | Server socket + message loop + handler registry; tracks peers, heartbeat eviction, peer discovery, seed bootstrap, block/tx broadcast with sender exclusion, chain + mempool sync |
| `Peer` | Outbound client connection with an async listener thread |
| `PeerManager` / `PeerInfo` / `PeerState` | Peer registry (`MAX_PEERS`), per-peer metadata, connection lifecycle |
| `Message` / `MessageType` / `MessageParser` | Base message, type enum, JSON-to-message routing |
| `MessageHandler` / `MessageContext` / `MessageListener` | Handler interface, connection wrapper, async listener |
| `NetworkConfig` | Ports, heartbeat timings, seed nodes |
| `messages/*` | `Hello`, `Ping`/`Pong`, `GetPeers`/`Peers`, `NewBlock`, `GetBlocks`/`Blocks`, `NewTransaction`, `GetMempool`/`Mempool` |

**Key flows**: handshake (`HELLO` with chain length) → behind peer requests `GET_BLOCKS` →
new blocks/txs gossip via `NEW_BLOCK` / `NEW_TRANSACTION` (validated, deduped, re-gossiped
to everyone except the sender); blocks with an unknown parent wait in a bounded orphan buffer.

---

## 📦 Package: `com.blocksmith.storage` (Sprint 16)

| Class | Role |
|-------|------|
| `ChainStore` | Interface: `saveBlock`, `loadBlocks`, `savePending`, `removePending`, `loadPending`; optional indexed lookups (`isIndexed`, block by hash, tx by id, txs by address) and `backup` |
| `NoOpChainStore` | Default for `new Blockchain()`: remembers nothing |
| `H2ChainStore` | Embedded H2 via JDBC. Tables `blocks(idx, hash, previous_hash, json)`, `pending_tx(seq, tx_id, json)`, `tx_index(tx_id, block_idx, pos, sender, recipient, amount)` with indexes on sender / recipient / block_idx. A block and its index rows are written in one DB transaction. `backup(zip)` = `BACKUP TO`; static `restoreBackup(zip, dir)` |
| `StorageException` | Unchecked wrapper for storage failures |

**Rules**:
- Blocks are stored in the same Gson JSON peers exchange (`JsonUtil.GSON`, which handles `PublicKey`)
- `Blockchain.appendBlock()` is the single append path; it saves the block before touching the mempool
- On startup `Blockchain.restoreFromStore()` replays every stored block through `addBlock(Block)`
  and stops at the first invalid one: **the database is data, validation is the authority**
- Index answers are hints: `Blockchain` resolves them against the in-memory chain, so stale rows are ignored

---

## 📦 Package: `com.blocksmith.api` (Sprint 12-16)

`ApiServer` — Javalin REST API on its own port; serves the dashboard from
`src/main/resources/public` at `/`. Errors use one JSON envelope (`{"error": ...}`, 400/404/500).
Writes (tx, mined block) are broadcast to peers.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/blocks`, `/api/blocks/latest`, `/api/blocks/{index}`, `/api/blocks/hash/{hash}` | Read the chain (by hash: Sprint 16c) |
| GET | `/api/network/status`, `/api/network/peers` | Node stats (incl. `difficulty`), peer list |
| POST / GET | `/api/transactions`, `/api/transactions/{id}` | Submit (unsigned, educational) / look up a tx (+ `status`, `blockIndex`, `confirmations`) |
| POST | `/api/mine` | Mine pending txs (or an empty block) for `minerAddress` |
| GET / POST | `/api/wallet/{address}`, `/api/wallet/{address}/transactions`, `/api/wallet/create` | Balance + pending outgoing / history (Sprint 16c) / new wallet |
| GET / POST | `/api/contracts`, `/api/contracts/{id}`, `/api/contracts/{id}/claim` | Deploy, inspect, claim contracts |
| POST | `/api/multisig/create`, `/api/multisig/claim` | M-of-N multisig with server-held member keys |
| POST | `/api/admin/backup` | Online database backup; localhost only, 503 if disabled (Sprint 16d) |

---

## 📦 Package: `com.blocksmith.contract` (Sprints 14-15)

| Class | Role |
|-------|------|
| `ScriptOp` | Opcodes: `PUSH`, `DUP`, `DROP`, `SHA256`, `EQUAL(VERIFY)`, `VERIFY`, `ADD`, `SUB`, `GREATER`, `LESS`, `CHECKLOCKTIME`, `CHECKSIG`, `CHECKMULTISIG` |
| `ScriptVM` | Stack machine; runs unlocking + locking script with an optional sighash; never throws (malformed = `false`) |
| `Contract` / `ContractStatus` | Locked funds (`OPEN` / `CLAIMED`); `claimSighash = SHA256(contractId+claimer+amount)` |
| `MultiSigWallet` | Builds the `CHECKMULTISIG` lock and the `MS:` address from public keys |

**Model**: a contract is just transactions — deploy sends TO `CONTRACT:<id>` with a locking
script, claim sends FROM it with unlocking data. `Blockchain` derives the contract registry
from blocks (so all nodes converge) and checks claims with the VM. A multisig is a contract
whose lock is `CHECKMULTISIG`.

---

## 🖥️ Web Dashboard (`src/main/resources/public`)

Vanilla HTML/JS/CSS, no build step. Redesigned in PR #157/#158 from the handoff in
`.ai/design/UIUX redesign project/`.

- **Layout**: single-screen "command deck": top bar, chain-of-blocks hero (`#chain-row`,
  `#hero-meta`), bottom grid with activity feed (`#feed-list`), actions, balance +
  contracts (`#contract-list`), footer.
- **Data**: polls the REST API every 4s; the activity feed is built client-side by diffing polls.
- **UI**: "Clay" dark/light themes (CSS variables), command palette (Ctrl/⌘+K), side drawers
  (send, create wallet, deploy hashlock/multisig, claim), toasts, 5-step onboarding tour
  (`data-tour` anchors; opt-out in `localStorage`).
- **Safety**: renders with `textContent` only (no HTML injection from chain data).

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

*Last updated: 2026-11-11 (added storage package, Sprint 16 endpoints)*
