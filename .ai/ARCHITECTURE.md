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

## ⛏️ Difficulty (Sprint 17)

| Piece | Role |
|-------|------|
| `util.Target` | 256-bit PoW target: `fromLeadingZeros(n)` = `16^(64-n) - 1`, `meets(hash, target)` (never throws), `toDifficulty` (leading-zero equivalent), hex |
| `core.ChainParams` | Consensus record: initial target, `targetBlockTimeMs` (10 s), `retargetInterval` (10), `maxAdjustmentFactor` (4), `powLimit` (3 zeros), `maxFutureDriftMs` (15 s) |
| `Blockchain.getNextTarget()` | At each window boundary: `old * actual / expected`, actual clamped to 4x, capped at powLimit; the window never starts at genesis and measures every gap |
| Timestamp rules | timestamp > median of the previous 11 blocks, and <= node `Clock` + 15 s; mining stamps `max(now, median + 1)` |

**Rules**: targets are derived from block timestamps (already covered by the hash), kept per height
next to the chain and never stored, so peers and a restore replay compute the same values. Every
append path (peer block, orphan attach, restore) checks the block against the expected target at its
height; orphans are only parked if they meet `powLimit` and are not too far in the future.

---

## 🧾 Block rules, limits and fees (Sprint 18)

| Piece | Role |
|-------|------|
| `Blockchain.blockRuleViolation(block)` | Content rules for a whole block; `null` = allowed, else the reason. Coinbase first, only one, pays <= reward + fees, no fee, no script |
| `Blockchain.transactionRuleViolation(tx, check)` | Per tx, in block order: well formed, signature verifies (if signed), not already included, deploy has a lock, claim passes the VM once, sender can afford `getTotalCost()` |
| `BlockCheck` (inner class) | Height + balances and ids changed by the earlier txs of the same block, so a split overspend is caught |
| `ChainParams.maxBlockBytes` / `maxBlockTransactions` | 16 KB of block JSON / 50 txs (coinbase included); `Block.sizeBytes()` |
| `Transaction.fee` | Paid by the sender on top of the amount; in the id and signed data only when != 0 (old ids unchanged). `feeRate()` = fee / `sizeBytes()` |

**Rules**: the same checks run on every path (peer block, orphan attach, restore replay, our own
mining, and `isChainValid()`, which replays the chain onto a fresh `Blockchain`). Proof-of-Work proves
the work was done, not that the block is allowed. Mining sorts the mempool by fee rate (stable, so
equal rates keep arrival order), fills the block up to the limits, drops stale txs, and only then
builds the coinbase (reward + fees). Any subset of the mempool is a valid block, because every pending
tx was admitted against confirmed balances only. Since Sprint 25b the coinbase carries the block height in
its `sequence` (Bitcoin's BIP34), so no two coinbases are alike; a coinbase that states a height must state
its own block's (older blocks without one stay valid).

---

## 💸 Fee market (Sprint 19)

| Piece | Role |
|-------|------|
| `core.MempoolPolicy` | Node **policy** (not consensus): `maxBytes` 64 KB, `maxTransactions` 200, `incrementalFeeRate` 0.0001 BSC/byte |
| Eviction (`addTransaction`) | A tx that does not fit must beat every victim's rate by the increment; victims = cheapest, newest first on ties; all or nothing. `getMempoolMinFeeRate()` |
| `Transaction.sequence` | Optional replace-by-fee key, in the id and signed data only when set; `withFee()` builds the same payment at a new fee |
| `confirmedSequences` | Consensus: a `(sender, sequence)` pair confirms at most once (in a block and on chain); rebuilt by replay |
| Replacement rules | Only the fee changes; higher fee and rate >= old rate + increment; signed only by the same key; old cost released |
| `estimateFeeRate(n)` | max(mempool view: simulate n blocks like mining, beat the cheapest that got in; history view: cheapest rate of each of the last 6 blocks, 0 if it had room; mempool minimum) |
| `submitTransaction(tx)` | `addTransaction` with the refusal reason (API 400s); `minimumBumpFee(tx)` for the dashboard |

**Rules**: the policy only shapes this node's mempool; blocks are never checked against it. RBF is
safe because consensus, not the mempool, guarantees that two versions of one payment cannot both
confirm. The sequence does not have to be consecutive, so any subset of the mempool is still a valid block.

---

## ✍️ Signatures (Sprint 22)

| Piece | Role |
|-------|------|
| `Wallet.addressOf(PublicKey)` | `0x` + last 40 hex of SHA-256(hex X.509 key): one derivation for wallets, the node and the dashboard (JS does the same on the SPKI hex) |
| `Transaction.isSignedBySender()` | The signature verifies with the attached key **and** that key's address is the sender (P2PKH: hash the key, compare, then check the signature) |
| Authorisation rule | Always: a tx carrying a key must carry the sender's key. With `ChainParams.requireSignatures` (DEFAULT): every transfer must be signed. Exempt: coinbase, contract claims (their script decides). Checked in the mempool and the block rules |
| Signed data | sender, recipient, amount, timestamp, fee (if any), sequence (if any), contract scripts (if any) |
| Prepare / sign / submit | `POST .../prepare` returns `templateId` + exact `signatureData`; the owner signs it; `POST /api/transactions` {templateId, signature (hex DER), publicKey (hex X.509)}; templates bounded (1000), 5 min, used once |
| Dashboard keys | Web Crypto ECDSA P-256 in the browser, encrypted `localStorage` keystore (`bs_keystore`, Sprint 24; see below), r\|\|s -> DER before submit; `/api/wallet/create` is retired (410) |

**Rules**: the node never holds a user's private key; it builds the text, the owner signs. Tests of other
rules run on `ChainParams.withoutSignatureRule()` through `TestChains`; `WebCryptoInteropTest` pins the
browser and Java to the same addresses and signature encoding with vectors from a real browser.

---

## 🔑 Browser keystore (Sprint 24)

```
 passphrase --PBKDF2-HMAC-SHA256(600 000 rounds, 16-byte salt)--> AES-256 key      (never stored)
 private JWK --AES-256-GCM(12-byte iv, additional data = address, 128-bit tag)--> ciphertext

 bs_keystore = { version: 1,
                 kdf:   { name: "PBKDF2", hash: "SHA-256", iterations: 600000, salt: <hex> },
                 check: { iv, ciphertext }                       # "BlockSmith keystore v1", aad "check"
                 keys:  { <address>: { publicKey, label?, iv, ciphertext } } }
 backups:  {blocksmith: "keystore-backup", ...the same}  |  {blocksmith: "key-backup", version, kdf, address, publicKey, label?, iv, ciphertext}
```

| Piece | Role |
|-------|------|
| Locked | Only ciphertext + public data (address, public key, label) in storage: balances, read-only marker, member lists work |
| Unlock | Passphrase -> key -> check value (wrong passphrase stops here) -> every key decrypted into a **non-extractable** `CryptoKey` in memory |
| Lock | Button, 15 minutes without a click or key press, or a reload: memory cleared |
| Signing | Every signature goes through `signText` (send, bump, deploys, multisig claim, member signing); locked -> the Unlock dialog, Cancel signs nothing |
| Migration | Sprint 22-23 plaintext `bs_keys` -> encrypted on the first passphrase, then removed; nothing signs until then |
| Backups | Encrypted as stored (no unlock needed to export); import checks every pair before storing any, re-encrypts under this keystore |
| Change passphrase | Current one checked, every key re-encrypted under a new salt, one write (all or nothing) |

**Threat model.** Protects the keys at rest: a copy of the browser profile, a backup, file-reading malware, someone at
a locked dashboard. Does not protect against a malicious script while unlocked (it can ask the keys to sign, though
not read them), a keylogger, or a weak passphrase (600 000 PBKDF2 rounds slow guessing; they do not stop it).

**Old plaintext stays on disk.** Measured in 24c: a profile closed with Sprint 23 plaintext keys still had both keys
in `Local Storage/leveldb/000003.log` after migration, and after one more open/close. Removing a localStorage entry
writes a deletion marker; LevelDB drops the old record only when it compacts, which the page cannot trigger. If an
old copy of the profile may exist, treat those keys as exposed: make new ones and move the funds.

**Move funds to a new key (Sprint 25c).** Wallet drawer: a new key, then one transfer of
`floor((balance - pending outgoing - fee) * 1e8) / 1e8` (rounded down, so it always fits) signed by the OLD key
through the keystore; the old key stays, marked `retiredTo`. Keys that ever existed in plain text (migrated from
`bs_keys`, or imported from a plaintext export) carry `plaintextBefore`, and the balance card and Wallet drawer
say so until their funds are moved. Labels and flags survive passphrase changes and backups.

**Recovery without the dashboard** (what `KeystoreInteropTest` does with a backup from a real browser): PBKDF2WithHmacSHA256
(passphrase, salt, iterations, 256 bits) -> AES/GCM/NoPadding with the 12-byte iv and the address (UTF-8) as additional
data -> the private JWK; `d` (base64url) is the P-256 private scalar.

---

## 🔐 Multisig: the node coordinates, the members sign (Sprint 23)

```
 lock (on chain):  PUSH M  PUSH <key1> ... PUSH <keyN>  PUSH N CHECKMULTISIG
 claim message:    claimSighash = SHA-256(contractId + claimer + amount)
 unlock (claim):   PUSH <sig by key i> PUSH <sig by key j> ...   (exactly M, in key order)

 deployer:  member public keys --POST /api/multisig/create--> lock + MS: address --> signed deploy
 claimer:   POST /api/multisig/claim/prepare -> message, member keys (script order), M
            signs with the member keys it holds, sends the "claim request" to the others
 member:    asks ITS node to prepare the same claim, compares the message, signs, sends back
            {contractId, claimer, publicKey, signature}
 claimer:   POST /api/multisig/claim {signatures: [{publicKey, signature}]} -> node checks each,
            orders by member position, builds the unlocking script; the VM decides
```

| Piece | Role |
|-------|------|
| `MultiSigWallet.fromLockingScript` | Reads the members and M back from a lock; only the canonical form counts (anything else: not a multisig) |
| `/api/multisig/create` | Public keys in, lock + address out; nothing kept (same keys, same wallet). `{members: N}` is refused: the node makes no keys |
| `/api/multisig/claim` | Per-signature pre-check (member? twice? verifies for THIS claimer?) for a precise 400; the script VM is the real gate |
| Dashboard | Member keys in `bs_keys` with a label; deploy from "3 new keys here" (learning) or pasted public keys; claim drawer collects M; "Multisig member" drawer checks and signs a request |

**Rules**: the claim message names the claimer, so signatures collected for one claimer are worthless for
another; `CHECKMULTISIG` matches signatures to keys left to right, which is why the node orders them. A
member never signs a hash blind: the dashboard recomputes the claim on its own node first. Bitcoin's PSBT
(BIP174) formalises the same round trip. `WebCryptoInteropTest` holds a claim signed in two real browser
profiles that passes `CHECKMULTISIG` in Java.

---

## 🔀 Fork choice (Sprint 21)

| Piece | Role |
|-------|------|
| `Target.work(target)` | 2^256 / (target + 1): expected hashes per block; cumulative work per height kept next to the targets (derived, never stored), `getTotalWork()` |
| Side branches | A block whose parent is known but which does not extend the tip is kept in `sideBlocks` (bounded, 200) after the cheap checks, if its fork point is within `MAX_REORG_DEPTH` (100) of the tip; orphans waiting on it follow it |
| `tryReorg` | When a branch reaches our height: replay ours-up-to-fork + branch through `addBlock` on a fresh `Blockchain`; invalid -> branch forgotten from the bad block on; valid and strictly more work -> `adoptChain` (ties keep the first seen) |
| `adoptChain` | Under the lock: chain-derived state from the candidate, undone blocks become side blocks, store rewritten + `truncateAbove`, mempool rebuilt (undone txs, then old pending, re-submitted); `ReorgEvent`, `getReorgCount()` |
| Block locator | `getBlockLocator()`: tip, 10 one by one, then doubling gaps, genesis; `GET_BLOCKS` may carry it and the peer replies with `getBlocksAfter(locator, 500)`; `NEW_BLOCK` with an unknown parent triggers it |

**Rules**: there is one validation path: a reorg can never apply a rule differently from normal block
acceptance, because it IS normal block acceptance on a candidate chain. `addBlock` returns true when the
block ends up on our chain through a reorg, so it is relayed like any accepted block. API: `totalWork`
(decimal string), `sideBlocks`, `reorgs`, `lastReorg` in `/api/network/status`; dashboard feed shows
"Chain reorganised".

---

## 🧵 Threading (Sprint 20)

| Thread(s) | Touches the chain via |
|-----------|-----------------------|
| One listener per peer (`Peer`) | `Node` handlers: `addBlock`, `addTransaction`, `getChain` (GET_BLOCKS), `getPendingTransactions` (GET_MEMPOOL) |
| Jetty request threads (`ApiServer`) | every endpoint; mining via `POST /api/mine` |
| Heartbeat (`Node`) | `getChainSize` |

| Piece | Role |
|-------|------|
| `Blockchain` monitor | Every public method that touches state is `synchronized` on the instance (one reentrant lock). Only `getChainParams()` / `getMempoolPolicy()` (immutable) are unlocked |
| Snapshot getters | `getChain()`, `getPendingTransactions()`, `getContracts()` return copies, safe to iterate on any thread |
| `atomically(Supplier)` | Runs a group of calls under the lock: one consistent state (used by `/api/network/status`, `/api/wallet`, `/api/mempool`, `/api/fees/estimate`, `/api/network/difficulty`, contracts) |
| Mining | template (locked) -> nonce search (unlocked; `Block.mineBlock(target, stop)` stops when the `volatile tipHash` moves or the caller cancels) -> submit (locked, same tip only; stale work retried, max 100) |

**Rules**: lock order is `Blockchain` -> `ChainStore`; the store never calls back, and `Blockchain` never calls
`Node` or the API, so there is no cycle. API code reads peer data outside `atomically` (the peer manager has its
own lock; never hold both). Work passed to `atomically` must be short: no I/O, no waiting.

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
| `H2ChainStore` | Embedded H2 via JDBC. Tables `blocks(idx, hash, previous_hash, json)`, `pending_tx(seq, tx_id, json)`, `tx_index(block_idx, pos, tx_id, sender, recipient, amount)` keyed by position (Sprint 25b: pre-25b coinbases can share an id), with indexes on tx_id / sender / recipient / block_idx; a lookup by id returns the earliest; an old index keyed by tx_id is dropped on open and refilled by the restore. A block and its index rows are written in one DB transaction. `backup(zip)` = `BACKUP TO`; static `restoreBackup(zip, dir)` |
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
| GET | `/api/blocks`, `/api/blocks/latest`, `/api/blocks/{index}`, `/api/blocks/hash/{hash}` | Read the chain (by hash: Sprint 16c); each block adds `sizeBytes` and `totalFees` (Sprint 18d) |
| GET | `/api/network/status`, `/api/network/peers`, `/api/network/difficulty` | Node stats (live `difficulty`, `target`, `blocksUntilRetarget`, `maxBlockBytes`, `maxBlockTransactions`...), peer list, difficulty epochs (Sprint 17d) |
| POST | `/api/transactions/prepare`, `/api/contracts/prepare`, `/api/transactions/{id}/bump/prepare` | Build a transfer / deploy / bump to sign: `templateId`, `signatureData`, the tx (Sprint 22b) |
| POST / GET | `/api/transactions`, `/api/transactions/{id}` | Submit a signed template {templateId, signature, publicKey} (or, on an unsigned test network, a plain transfer); 400 says why / look up a tx (+ `status`, `blockIndex`, `confirmations`) |
| POST | `/api/transactions/{id}/bump` | Same payment at a higher `fee` (default: smallest allowed bump); 404 not pending, 400 not replaceable / signed / too small (Sprint 19d) |
| GET | `/api/fees/estimate` | `fast` / `normal` / `slow` (1 / 3 / 6 blocks): `feeRate` and `fee` for a typical tx, `typicalTxBytes`, `mempoolMinFeeRate` (Sprint 19d) |
| GET | `/api/mempool` | Pending txs in mining order with `sizeBytes`, `feeRate`, `replaceable`, `minBumpFee`; count, total fees, block limits, mempool `bytes` / limits / `mempoolMinFeeRate` (Sprint 18d, 19d) |
| POST | `/api/mine` | Mine pending txs (or an empty block) for `minerAddress` |
| GET / POST | `/api/wallet/{address}`, `/api/wallet/{address}/transactions`, `/api/wallet/create` | Balance + pending outgoing (amount + fee) / history (Sprint 16c) / retired: 410 (Sprint 22c, keys are made by their owner) |
| GET / POST | `/api/contracts`, `/api/contracts/{id}`, `/api/contracts/{id}/claim` | Deploy, inspect, claim contracts |
| POST | `/api/multisig/create`, `/api/multisig/claim/prepare`, `/api/multisig/claim` | M-of-N multisig from member public keys; prepare returns the claim message; claim takes the members' {publicKey, signature} pairs (Sprint 23a) |
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

**Contract ids (Sprint 25a)**: `id = Blockchain.contractIdFor(funder, lockingScript, amount, sequence)` =
SHA-256 of the four joined with `|`; every deploy carries a sequence and must pay to `CONTRACT:` + its own
id (mempool and block rules). Before, the client wrote any id it liked: a probe front-ran Alice's pending
deploy with Mallory's own deploy to the same id, and the registry (first deploy wins) left Alice's 30 stuck.
Now Mallory's deploy hashes to another id; two deploys with one id would need one (sender, sequence) pair,
which confirms once; a fee bump keeps the sequence and the id. Like Ethereum's creator + nonce address.

---

## 🖥️ Web Dashboard (`src/main/resources/public`)

Vanilla HTML/JS/CSS, no build step. Redesigned in PR #157/#158 from the handoff in
`.ai/design/UIUX redesign project/`.

- **Layout**: single-screen "command deck": top bar, chain-of-blocks hero (`#chain-row`,
  `#hero-meta`), bottom grid with activity feed (`#feed-list`), actions, balance +
  contracts (`#contract-list`), footer.
- **Data**: polls the REST API every 4s; the activity feed is built client-side by diffing polls.
- **UI**: "Clay" dark/light themes (CSS variables), command palette (Ctrl/⌘+K), side drawers
  (send, create wallet, deploy hashlock/multisig, claim, multisig member), toasts, 5-step onboarding tour
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

*Last updated: 2026-11-14 (added signatures section, Sprint 22)*
