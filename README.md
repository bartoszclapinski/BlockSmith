# ⚒️ BlockSmith

**A complete blockchain and cryptocurrency implementation built from scratch in Java**

🌐 **Project page & in-browser mining demo:** https://bartoszclapinski.github.io/BlockSmith/

BlockSmith is a comprehensive blockchain project that goes beyond tutorials - implementing a fully functional distributed cryptocurrency system with P2P networking, REST API, web dashboard, and basic smart contracts. Built to deeply understand how Bitcoin and Ethereum work under the hood.

[![Java](https://img.shields.io/badge/Java-20+-orange.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-blue.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-483%20passing-brightgreen.svg)](#)
[![Phase](https://img.shields.io/badge/Phase%204-Complete-brightgreen.svg)](#)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)](#)

---

## 🚀 Features

### Phase 1: Core Blockchain ✅ Complete (100%)
- ✅ SHA-256 cryptographic hashing
- ✅ Proof-of-Work mining with adjustable difficulty
- ✅ Merkle tree for transaction verification
- ✅ Transaction model with validation
- ✅ Pending transaction pool (mempool)
- ✅ Mining rewards (50 BSC from COINBASE)
- ✅ Balance tracking for addresses
- ✅ Chain validation and tamper detection
- ✅ ECDSA digital signatures
- ✅ Wallet with key pair generation
- ✅ Ethereum-style addresses (0x format)
- ✅ Balance validation before transfers
- ✅ Double-spend prevention (pending tracking)

### Phase 2: Network Layer ✅ Complete (100%)
- ✅ Message protocol with JSON serialization (Sprint 8a)
- ✅ Server-side TCP socket node (Sprint 8b)
- ✅ Client-side peer connections with handshake (Sprint 8c)
- ✅ Bidirectional message exchange with handler pattern (Sprint 8d)
- ✅ Node discovery and peer management (Sprint 9)
  - ✅ PeerInfo / PeerState metadata tracking (Sprint 9a)
  - ✅ PeerManager registry with MAX_PEERS enforcement (Sprint 9b)
  - ✅ Heartbeat PING/PONG with dead-peer eviction (Sprint 9c)
  - ✅ Peer discovery (GET_PEERS/PEERS) + seed-node bootstrap (Sprint 9d)
- ✅ Block broadcasting (Sprint 10)
  - ✅ Blockchain wired into Node + external block append (Sprint 10a)
  - ✅ NEW_BLOCK broadcast, validate, and re-gossip (Sprint 10b)
  - ✅ Orphan block buffering and attachment (Sprint 10c)
  - ✅ Chain sync (GET_BLOCKS/BLOCKS) for behind nodes (Sprint 10d)
- ✅ Mempool synchronization (Sprint 11)
  - ✅ Transaction broadcast (NEW_TRANSACTION) + relay (Sprint 11a)
  - ✅ Prune confirmed transactions from the mempool (Sprint 11b)
  - ✅ Mempool sync on connect (GET_MEMPOOL/MEMPOOL) (Sprint 11c)

### Phase 3: API & Interface ✅ Complete (100%)
- ✅ REST API for blockchain interaction (Sprint 12)
  - ✅ Javalin server + block read endpoints (Sprint 12a)
  - ✅ Transaction submit/lookup + mining endpoints (Sprint 12b)
  - ✅ Wallet + network endpoints + JSON errors (Sprint 12c)
- ✅ Web dashboard (Sprint 13)
  - ✅ Runnable node entry point + static hosting (Sprint 13a)
  - ✅ Explorer view: blocks + network, polling refresh (Sprint 13b)
  - ✅ Wallet, transaction, and mine actions in the UI (Sprint 13c)
- ✅ Smart contracts (Sprint 14)
  - ✅ Stack-based script VM: hashlock + timelock opcodes (Sprint 14a)
  - ✅ Contract deploy/claim on the chain, registry derived from blocks (Sprint 14b)
  - ✅ Contract REST endpoints + dashboard panel (Sprint 14c)
- ✅ Multi-signature wallets (Sprint 15)
  - ✅ CHECKSIG/CHECKMULTISIG opcodes + sighash in the script VM (Sprint 15a)
  - ✅ M-of-N multisig contracts, replay-safe claim sighash (Sprint 15b)
  - ✅ Multisig REST endpoints + dashboard panel (Sprint 15c)

### Phase 4: Production Features ✅ Complete (100%)
- ✅ Database persistence (Sprint 16)
  - ✅ Embedded H2 store; blocks kept in the same JSON format peers exchange (Sprint 16a)
  - ✅ Every block + pending tx saved; restart restores by replaying through validation (Sprint 16b)
  - ✅ Block validation recomputes tx ids and the Merkle root (Sprint 16b)
  - ✅ Indexed lookups: block by hash, address history with confirmations (Sprint 16c)
  - ✅ Online backup + `--restore <zip>` at startup (Sprint 16d)
- ✅ Dynamic difficulty adjustment (Sprint 17)
  - ✅ Numeric 256-bit target instead of leading zeros (Sprint 17a)
  - ✅ Retarget every 10 blocks toward 10 s per block, max 4x per step (Sprint 17b)
  - ✅ Timestamp rules: median-time-past + 15 s future limit (Sprint 17c)
  - ✅ Live difficulty and retarget countdown in the API and dashboard (Sprint 17d)
- ✅ Block rules, size limits and fees (Sprint 18)
  - ✅ Every block's contents are checked: coinbase cap, no overspending, no duplicates, contract claims (Sprint 18a)
  - ✅ Max 16 KB / 50 transactions per block; the rest waits for the next block (Sprint 18b)
  - ✅ Transaction fees; miners fill blocks by fee per byte and collect the fees (Sprint 18c)
  - ✅ Fees, `GET /api/mempool` and block size in the API and dashboard (Sprint 18d)
- ✅ Fee market (Sprint 19)
  - ✅ Mempool limit (64 KB / 200 txs); when full, the lowest fee per byte is evicted (Sprint 19a)
  - ✅ Replace-by-fee: bump a stuck transaction; the chain guarantees only one version confirms (Sprint 19b)
  - ✅ Fee estimation for the next block / ~3 / ~6 blocks (Sprint 19c)
  - ✅ Suggested fees, fee bumps and a Mempool view in the API and dashboard (Sprint 19d)

### Hardening
- ✅ Thread safety (Sprint 20)
  - ✅ The chain is one monitor; readers get snapshots (Sprint 20a)
  - ✅ Mining runs outside the lock, so the API and peers never wait for it (Sprint 20b)
  - ✅ Consistent API responses; a two-node TCP test under parallel traffic (Sprint 20c)
- ✅ Fork choice (Sprint 21)
  - ✅ Chain work; competing blocks kept on side branches (Sprint 21a)
  - ✅ Reorganisation onto the valid chain with the most work; undone transactions back to the mempool (Sprint 21b)
  - ✅ Block-locator sync: two nodes that both mine end on one chain (Sprint 21c)
- ✅ Required signatures (Sprint 22)
  - ✅ Only the owner can spend: every transfer is signed by the key behind the sender's address (Sprint 22a)
  - ✅ Prepare / sign elsewhere / submit: the node never holds your private key (Sprint 22b)
  - ✅ Keys created and kept in the browser (Web Crypto) by the dashboard (Sprint 22c)

- ✅ Client-side multisig (Sprint 23)
  - ✅ The node makes no member keys: a multisig is created from the members' public keys (Sprint 23a)
  - ✅ Members sign a claim in their own browsers; the node only assembles the signatures (Sprint 23b)
  - ✅ A claim signed in two browser profiles is checked by the Java script VM (Sprint 23c)
- ✅ Encrypted browser keystore (Sprint 24)
  - ✅ Keys stored only encrypted under your passphrase (PBKDF2 + AES-GCM); unlocked on demand, auto-lock (Sprint 24a)
  - ✅ Encrypted backups and passphrase change; a backup opens with standard Java crypto (Sprint 24b)
  - ✅ Every signing flow checked live with locked keys (Sprint 24c)
- ✅ Loose ends (Sprint 25)
  - ✅ A contract's id is bound to its deploy, so nobody can deploy to someone else's pending contract (Sprint 25a)
  - ✅ Every new coinbase carries its block height; the transaction index survives duplicate ids (Sprint 25b)
  - ✅ "Move funds to a new key" in the dashboard (Sprint 25c)
- ✅ Networking for real (Sprint 26)
  - ✅ Join a network with `--peer host:port` or from the dashboard; restarted peers are reached again (Sprint 26a)
  - ✅ A node keeps its id, knows itself and duplicate links, and finds more peers on its own (Sprint 26b)
  - ✅ Pending transactions flow both ways; a Peers card in the dashboard; checked live with three processes (Sprint 26c)
- ✅ Block explorer (Sprint 27)
  - ✅ Explorer API: blocks a page at a time, a block and a transaction with their place on the chain, an address with its totals, one search, chain totals (Sprint 27a)
  - ✅ Explorer views in the dashboard, each with its own link: blocks, block, transaction, address (Sprint 27b)
  - ✅ Pending transactions and contracts in the explorer; checked end to end and live in a browser (Sprint 27c)
- ✅ Recorded demo on GitHub Pages (Sprint 28): https://bartoszclapinski.github.io/BlockSmith/demo/
  - ✅ Recorder: a scripted session on two real nodes, every answer recorded after every step (Sprint 28a)
  - ✅ Player: the dashboard answered from the recording, read-only (Sprint 28b)
  - ✅ Published under `docs/demo`, with a test that fails when it falls behind the dashboard or the API (Sprint 28c)

> Upgrading from an older checkout? Chains saved before Sprint 22 contain unsigned transfers and
> restore only up to the first one, and multisig contracts from before Sprint 23 cannot be claimed:
> start with a fresh `data/` directory. Browser keys from before Sprint 24 were stored in plain text and
> may stay in the browser's files after they are encrypted: use "Move funds to a new key". Chains with
> contract deploys made before Sprint 25 restore only up to the first one: start with a fresh `data/` directory.

---

## 🎮 Demo Output

```
═══════════════════════════════════════════════════════════
                    BLOCKSMITH v1.0.0                       
              Proof-of-Work Mining Demo                     
═══════════════════════════════════════════════════════════

▶ Creating Genesis Block...
    Mining with difficulty 4...
Block mined! Nonce: 8208 | Time: 103ms
Hash: 0000aeaf2928201f80df08494337a342bf04c5f72a33442db24f58ee7e76ee75

═══════════════════════════════════════════════════════════
                   TRANSACTION DEMO                         
═══════════════════════════════════════════════════════════

▶ Miner1 mines the first block (receives 50 BSC reward)...
⛏️  Mining block #1 with 1 transactions...
Block mined! Nonce: 45049 | Time: 43ms
✅ Block mined and added to chain!
   Miner Miner1 received 50.0 BSC

▶ Creating transactions...
  Transaction{id=964904f5..., Miner1 -> Alice: 30.00}
  Transaction{id=69df15dc..., Miner1 -> Bob: 15.00}

▶ Final balances:
  Miner1: 55.0 BSC (mined 2 blocks)
  Alice:  20.0 BSC
  Bob:    25.0 BSC
```

---

## 🏛️ Architecture

### Core Classes

```
┌─────────────────────────────────────────────────────────────────┐
│                         BLOCKSMITH                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐     ┌─────────────┐         │
│  │   Wallet    │ ──▶│ Transaction │───▶│   Block     │         │
│  │  (Keys)     │    │ (Signed)    │     │   (Mined)   │         │
│  └─────────────┘    └─────────────┘     └─────────────┘         │
│        │                   │                  │                 │
│        │ signs             │ contains         │ links           │
│        ▼                   ▼                  ▼                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    BLOCKCHAIN                           │    │
│  │  ┌───────┐   ┌───────┐    ┌───────┐    ┌───────┐        │    │
│  │  │Block 0│──▶│Block 1│──▶│Block 2│──▶│Block n│        │    │
│  │  │Genesis│   │       │    │       │    │       │        │    │
│  │  └───────┘   └───────┘    └───────┘    └───────┘        │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─────────────┐  ┌──────────────────┐  ┌─────────────────┐     │
│  │  HashUtil   │  │ BlockchainConfig │  │  BlockExplorer  │     │
│  │  (SHA-256)  │  │   (Constants)    │  │   (Viewer)      │     │
│  └─────────────┘  └──────────────────┘  └─────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Blockchain Structure

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│  BLOCK #0   │      │  BLOCK #1   │      │  BLOCK #2   │
│  (Genesis)  │      │             │      │             │
├─────────────┤      ├─────────────┤      ├─────────────┤
│ prevHash: 0 │◄─────│ prevHash ───│◄─────│ prevHash ───│◄── ...
│ timestamp   │      │ timestamp   │      │ timestamp   │
│ merkleRoot  │      │ merkleRoot  │      │ merkleRoot  │
│ nonce       │      │ nonce       │      │ nonce       │
│ hash ───────│─────►│ hash ───────│─────►│ hash        │
└─────────────┘      └─────────────┘      └─────────────┘
```

### Merkle Tree

```
                    ┌──────────────────┐
                    │   Merkle Root    │
                    │  (in block hash) │
                    └────────┬─────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
        ┌─────┴─────┐                 ┌─────┴─────┐
        │ Hash(AB)  │                 │ Hash(CD)  │
        └─────┬─────┘                 └─────┬─────┘
              │                             │
       ┌──────┴──────┐               ┌──────┴──────┐
       │             │               │             │
   ┌───┴───┐     ┌───┴───┐       ┌───┴───┐     ┌───┴───┐
   │ Tx A  │     │ Tx B  │       │ Tx C  │     │ Tx D  │
   └───────┘     └───────┘       └───────┘     └───────┘
```

### Proof-of-Work Mining

```
Target: Hash must start with "0000" (difficulty = 4)

nonce = 0     → hash = "8a3f2b..."      ✗ Invalid
nonce = 1     → hash = "c7e9f1..."      ✗ Invalid
nonce = 2     → hash = "1d4a8c..."      ✗ Invalid
    ...
nonce = 52847 → hash = "0000a8b2..."    ✓ VALID!

Average attempts: ~16^difficulty (~65,536 for difficulty 4)
```

---

## 📋 Prerequisites

- **Java JDK 20** or higher
- **Maven 3.8** or higher

---

## 🔧 Build & Run

### Compile the project
```bash
mvn clean compile
```

### Run all tests (483 tests)
```bash
mvn test
```

### Try the demo (nothing to install)
**https://bartoszclapinski.github.io/BlockSmith/demo/** is the dashboard replaying what a real node answered
during a recorded session: transfers at different fees, a hashlock and a multisig contract, a second node, a
chain reorganisation, and the explorer for every block, transaction and address. Nothing in it is simulated in
JavaScript; it is a recording, so it cannot be changed (mine, send and deploy need a node, below).

Record it again after changing the dashboard or the API (Sprint 28):
```bash
mvn compile exec:java -Dexec.mainClass=com.blocksmith.demo.DemoSite                    # a new session, about 5 minutes
mvn compile exec:java -Dexec.mainClass=com.blocksmith.demo.DemoSite -Dexec.args=--keep # same session, new page
```
`DemoPublishedTest` fails when `docs/demo/` falls behind the sources.

### Run a node with the web dashboard
```bash
mvn exec:java
# open http://localhost:7070/
```
Optional ports (P2P, API): `mvn exec:java -Dexec.args="8335 7070"`

### Explore the chain
Open the dashboard and click **Explorer** (or any block card). Every view has its own link:

| Link | Shows |
|------|-------|
| `http://localhost:7070/#/blocks` | chain totals and all blocks, newest first |
| `http://localhost:7070/#/block/12` | block 12 (or `#/block/<hash>`): header, previous / next, transactions |
| `http://localhost:7070/#/tx/<id>` | a transaction: status, confirmations, block, amount, fee |
| `http://localhost:7070/#/address/<address>` | an address: balance, received, sent, history; a contract address shows the contract |

The search field takes a block height, a block hash, a transaction id or an address. The
same lookups over the API: `GET /api/explorer/search?q=...`, `/api/explorer/blocks?limit=20&before=41`,
`/api/explorer/address/<address>`, `/api/explorer/stats` (Sprint 27).

### Run a network of nodes
Each node needs its own ports and data directory. Give a new node the address of
any node already running; it finds the others by itself (Sprint 26):
```bash
# terminal 1: the first node
mvn exec:java -Dexec.args="8335 7070"
# terminal 2: joins the first one
mvn exec:java -Dexec.args="8336 7071 --peer localhost:8335"
# terminal 3: also knows only the first one, then discovers node 2
mvn exec:java -Dexec.args="8337 7072 --peer localhost:8335"
```
`--peer` can repeat (or use `-Dblocksmith.peers=host:port,host:port`). A peer given this
way is dialled again whenever its link drops. From the dashboard, the **Peers** card lists
the links (→ we dialled, ← they dialled us) and connects to another `host:port`. Each node
keeps its id in `data/node-<p2pPort>/node-id`; peers are found through `GET_PEERS`, and a
node dials them while it has fewer than 4 links of its own (at most 10 in all).

### Run the teaching demo
```bash
mvn exec:java -Dexec.mainClass=com.blocksmith.BlockSmithDemo
```

### Create JAR package
```bash
mvn package
java -jar target/blocksmith-1.0.0.jar   # starts the node + dashboard
```

---

## 📁 Project Structure

```
BlockSmith/
├── .ai/                    # Project documentation for AI/LLMs
│   ├── ONBOARDING.md       # Quick start guide
│   ├── ARCHITECTURE.md     # Class descriptions
│   ├── CONVENTIONS.md      # Code style guide
│   ├── STATUS.md           # Current sprint status
│   ├── prd.md              # Product requirements
│   ├── tech-stack.md       # Technologies used
│   ├── roadmap.md          # Full project roadmap
│   └── sprints/            # Sprint plans and logs
├── src/main/java/com/blocksmith/
│   ├── core/
│   │   ├── Block.java          # Block with transactions & Merkle root
│   │   ├── Blockchain.java     # Chain management & mining
│   │   ├── Transaction.java    # Value transfers with signatures
│   │   └── Wallet.java         # ECDSA keys & signing
│   ├── util/
│   │   ├── HashUtil.java       # SHA-256 hashing
│   │   ├── SignatureUtil.java  # ECDSA sign/verify + hex (Sprint 15a)
│   │   ├── BlockchainConfig.java # Configuration constants
│   │   └── BlockExplorer.java  # Explorer queries: pages, views, search, totals (Sprint 27)
│   ├── network/                # P2P networking (Sprint 8)
│   │   ├── MessageType.java    # Network message types
│   │   ├── Message.java        # Base message class
│   │   ├── MessageParser.java  # JSON-to-Message routing
│   │   ├── MessageHandler.java # Handler interface
│   │   ├── MessageContext.java # Connection wrapper
│   │   ├── MessageListener.java # Async listener interface
│   │   ├── NetworkConfig.java  # Network constants
│   │   ├── Node.java           # Server node + message loop + heartbeat + discovery
│   │   ├── Peer.java           # Client peer with async listener
│   │   ├── PeerState.java     # Peer connection lifecycle
│   │   ├── PeerInfo.java      # Peer metadata tracking
│   │   ├── PeerManager.java   # Peer registry with MAX_PEERS enforcement
│   │   └── messages/           # Concrete message classes (incl. GetPeers/Peers)
│   ├── api/
│   │   └── ApiServer.java      # Javalin REST API + static dashboard hosting
│   ├── contract/               # Smart contracts (Sprint 14) + multisig (Sprint 15)
│   │   ├── ScriptOp.java       # Script opcode set (+ CHECKSIG/CHECKMULTISIG)
│   │   ├── ScriptVM.java       # Stack-based script interpreter (+ sighash)
│   │   ├── Contract.java       # Contract model (locking script + funds + claim sighash)
│   │   ├── ContractStatus.java # OPEN / CLAIMED
│   │   └── MultiSigWallet.java # M-of-N wallet + CHECKMULTISIG lock (Sprint 15b)
│   ├── storage/                # Persistence (Sprint 16)
│   │   ├── ChainStore.java     # Store interface: blocks, mempool, lookups, backup
│   │   └── H2ChainStore.java   # Embedded H2 implementation
│   ├── BlockSmithNode.java     # Runnable node (P2P + API + dashboard)
│   └── BlockSmithDemo.java     # Teaching demo application
├── src/main/resources/public/  # Web dashboard (index.html, app.js, style.css)
├── src/test/java/              # 483 unit tests
├── data/                       # Node databases (H2), one folder per node: data/node-<port>/
├── pom.xml                     # Maven configuration
└── README.md
```

---

## 🧪 Test Coverage

| Test Class | Tests | Description |
|------------|-------|-------------|
| HashUtilTest | 6 | SHA-256 hashing |
| BlockTest | 12 | Block creation, mining, transactions |
| BlockchainTest | 26 | Chain management, validation, balance, deterministic genesis |
| MiningTest | 9 | Proof-of-Work mechanics |
| TransactionTest | 22 | Transaction validation, signatures |
| WalletTest | 13 | Key generation, addresses, signing |
| MessageTest | 8 | Network message serialization (incl. signed transactions) |
| NodeTest | 8 | Node start/stop, connections |
| PeerTest | 7 | Peer connections, handshake |
| CommunicationTest | 6 | Bidirectional message exchange |
| PeerInfoTest | 6 | Peer metadata and state transitions |
| PeerManagerTest | 8 | Peer registry, MAX_PEERS enforcement |
| HeartbeatTest | 4 | Heartbeat ping, dead-peer eviction |
| PeerDiscoveryTest | 5 | Peer discovery messages and handlers |
| ChainIntegrationTest | 2 | Node-backed blockchain, HELLO chain length |
| ExternalBlockTest | 4 | External block append + validation |
| BlockBroadcastTest | 4 | NEW_BLOCK serialization and handler |
| OrphanBlockTest | 3 | Orphan buffering and attachment |
| ChainSyncTest | 5 | GET_BLOCKS/BLOCKS sync handlers |
| TransactionBroadcastTest | 5 | NEW_TRANSACTION serialization and handler, gossiped fee bump |
| MempoolPruneTest | 2 | Confirmed-tx pruning on block append |
| MempoolSyncTest | 4 | GET_MEMPOOL/MEMPOOL sync handlers |
| ApiReadEndpointsTest | 6 | REST block/status read endpoints |
| ApiTransactionEndpointsTest | 5 | REST transaction submit/lookup + mining |
| ApiWalletNetworkEndpointsTest | 4 | REST wallet/network endpoints + JSON errors |
| ApiStaticHostingTest | 3 | Dashboard shell + static assets alongside the API |
| ApiExplorerTest | 2 | Explorer mount points + block JSON shape |
| ApiDashboardActionsTest | 2 | Dashboard action chain end to end |
| ScriptVMTest | 27 | Script opcodes, hashlock/timelock, signature opcodes, failure semantics |
| ChainContractTest | 9 | Contract deploy/claim, timelock, double-claim, convergence |
| ApiContractsTest | 3 | Contract lifecycle over HTTP + error envelopes |
| MultiSigContractTest | 5 | M-of-N deploy/claim, threshold, wrong-key, replay safety, convergence |
| ApiMultiSigTest | 9 | Members sign, the node coordinates (any order, too few, other claimer, dashboard flow) |
| H2ChainStoreTest | 8 | H2 store round-trip, ordering, idempotency, reopen |
| PersistRestoreTest | 6 | Restart restores chain + mempool; tampered blocks rejected |
| BlockIntegrityTest | 7 | Edited transactions (amounts, contract scripts) fail the Merkle check |
| IndexedQueryTest | 9 | Block by hash, tx by id, address history, index vs scan |
| ApiIndexedQueryTest | 4 | History and block-by-hash over HTTP |
| BackupRestoreTest | 8 | Backup round-trip and bad-backup handling |
| ApiBackupTest | 3 | Backup endpoint over HTTP |
| TargetTest | 7 | Numeric PoW target equals the leading-zero rule |
| RetargetTest | 10 | Difficulty retargeting and validation |
| TimestampRulesTest | 6 | Median-time-past, future limit, time-warp |
| DifficultyReportTest | 3 | Difficulty epochs and countdown |
| ApiDifficultyTest | 3 | Difficulty over HTTP |
| BlockContentRulesTest | 13 | Peer blocks cannot mint coins, overspend or bypass contracts |
| BlockLimitsTest | 6 | Block size and transaction-count limits |
| FeeTest | 9 | Who pays fees, who collects them, fee-rate priority |
| ApiFeesTest | 4 | Fees and the mempool over HTTP |
| MempoolLimitTest | 7 | Mempool limit and lowest-fee-rate eviction |
| ReplaceByFeeTest | 11 | Fee bumps; one version of a payment confirms |
| FeeEstimationTest | 5 | Fee estimates for 1 / 3 / 6 blocks |
| ApiFeeMarketTest | 4 | Estimates, bumps and a full mempool over HTTP |
| ConcurrencyTest | 5 | Chain stays valid under concurrent reads, mines and spends |
| MiningOutsideLockTest | 6 | Mining does not block; stale work is redone |
| NodeConcurrencyTest | 3 | Consistent API snapshots; two nodes over TCP converge |
| SideBranchTest | 5 | Chain work; competing blocks kept |
| ReorgTest | 11 | Switching to the chain with the most work |
| ForkSyncTest | 3 | Fork-aware sync; two mining nodes converge |
| SignatureRuleTest | 8 | Only the sender's own key can authorise a transfer |
| ApiSigningTest | 6 | Prepare / sign / submit over HTTP |
| WebCryptoInteropTest | 4 | Browser (Web Crypto) and Java agree on addresses, signatures and multisig claims |
| DashboardKeystoreTest | 5 | The dashboard stores keys encrypted only, signs through the keystore, moves funds to a new key |
| KeystoreInteropTest | 3 | A browser keystore backup opens with standard Java crypto |
| ContractIdTest | 5 | A contract id belongs to its deploy: front-running refused |
| CoinbaseHeightTest | 4 | Unique coinbases and an index keyed by position |
| JoiningTest | 3 | Nodes started with `--peer` share blocks and reconnect after a restart |
| IdentityDiscoveryTest | 5 | Persistent node id, self and duplicate links, discovery and auto-connect |
| NetworkPanelTest | 2 | Mempool pulled both ways; the dashboard's Peers card |
| ExplorerApiTest | 8 | Explorer API: pages, views, search, totals |
| ExplorerViewsTest | 3 | Explorer views served by the dashboard |
| ExplorerEndToEndTest | 4 | Search everything, walk the chain, pending to confirmed, contracts |
| DemoRecorderTest | 5 | The recorded session, frame by frame |
| DemoSiteTest | 5 | The demo page and the seams of demo mode |
| DemoPublishedTest | 3 | `docs/demo` matches the dashboard and the API |
| **Total** | **483** | All passing ✅ |

---

## 📚 Skills & Technologies

### Core Blockchain
- Cryptographic hashing (SHA-256)
- Digital signatures (ECDSA)
- Proof-of-Work consensus
- Merkle trees & data structures
- Transaction pools (mempool)

### Networking & API
- TCP socket P2P networking (joining, node identity, peer discovery with auto-connect)
- JSON messaging (Gson)
- REST API over HTTP (Javalin / embedded Jetty)

### Java
- Java Cryptography Architecture (JCA)
- Collections framework
- Object-oriented design
- Reflection (for testing)

### Software Engineering
- Clean architecture
- Unit testing (JUnit 5)
- Maven build system
- Git workflow with branches
- Comprehensive documentation

---

## 🏗️ Development Status

### Phase 1: Core Blockchain ✅ Complete (100%)
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 0 | Project Setup | ✅ Complete |
| Sprint 1 | Fundamentals (Hash, Block) | ✅ Complete |
| Sprint 2 | Proof-of-Work Mining | ✅ Complete |
| Sprint 3 | Blockchain Management | ✅ Complete |
| Sprint 4 | Transactions & Merkle Trees | ✅ Complete |
| Sprint 5 | Wallets & Digital Signatures | ✅ Complete |
| Sprint 6 | Economic System | ✅ Complete |

### Phase 2: Network Layer ✅ Complete (100%)
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 8 | P2P Networking | ✅ Complete (8a, 8b, 8c, 8d) |
| Sprint 9 | Node Discovery | ✅ Complete (9a, 9b, 9c, 9d) |
| Sprint 10 | Block Broadcasting | ✅ Complete (10a, 10b, 10c, 10d) |
| Sprint 11 | Mempool Sync | ✅ Complete (11a, 11b, 11c) |

### Phase 3: API & Interface ✅ Complete (100%)
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 12 | REST API | ✅ Complete (12a, 12b, 12c) |
| Sprint 13 | Web Dashboard | ✅ Complete (13a, 13b, 13c) |
| Sprint 14 | Smart Contracts | ✅ Complete (14a, 14b, 14c) |
| Sprint 15 | Multi-sig Wallets | ✅ Complete (15a, 15b, 15c) |

### Phase 4: Production ✅ Complete (100%)
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 16 | Database Persistence | ✅ Complete (16a, 16b, 16c, 16d) |
| Sprint 17 | Difficulty Adjustment | ✅ Complete (17a, 17b, 17c, 17d) |
| Sprint 18 | Block Rules, Size & Fees | ✅ Complete (18a, 18b, 18c, 18d) |
| Sprint 19 | Fee Market | ✅ Complete (19a, 19b, 19c, 19d) |

### Hardening
| Sprint | Title | Status |
|--------|-------|--------|
| Sprint 20 | Thread Safety | ✅ Complete (20a, 20b, 20c) |
| Sprint 21 | Fork Choice | ✅ Complete (21a, 21b, 21c) |
| Sprint 22 | Required Signatures | ✅ Complete (22a, 22b, 22c) |
| Sprint 23 | Client-side Multisig | ✅ Complete (23a, 23b, 23c) |
| Sprint 24 | Encrypted Browser Keystore | ✅ Complete (24a, 24b, 24c) |
| Sprint 25 | Loose Ends | ✅ Complete (25a, 25b, 25c) |
| Sprint 26 | Networking for Real | ✅ Complete (26a, 26b, 26c) |
| Sprint 27 | Block Explorer | ✅ Complete (27a, 27b, 27c) |
| Sprint 28 | Recorded Demo | ✅ Complete (28a, 28b, 28c) |

---

## 🎓 Learning Resources

- [Bitcoin Whitepaper](https://bitcoin.org/bitcoin.pdf) - Original Satoshi paper
- [Blockchain Demo](https://andersbrownworth.com/blockchain/) - Visual demonstration
- [Mastering Bitcoin](https://github.com/bitcoinbook/bitcoinbook) - Comprehensive book

---

## 📝 License

This project is for educational purposes.

---

## 👤 Author

**Bartek** - [GitHub](https://github.com/bartoszclapinski)

---

*Last updated: 2026-07-05 | Phase 3 ✅ Complete (Sprint 15 - Multi-signature Wallets Complete)*
