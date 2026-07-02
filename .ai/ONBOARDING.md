# BlockSmith - LLM Onboarding Guide

> **This file is your complete context. Read it fully before doing anything.**
> **No additional prompt is needed — everything you need to work on this project is here.**

---

## How to Work With the User

### Communication Rules (MANDATORY)
1. **Always check the user's English** at the beginning of every answer. Give gentle corrections/hints before your main response. The user is learning English and expects this feedback every time.
2. **Keep responses concise.** Don't over-explain things the user already knows.
3. The collaboration model is set per session — the user may ask you to implement code directly, or to guide while they implement. Follow the current session's instruction.

### Development Workflow
- **Issue-first approach**: Each task maps to a GitHub issue. Reference issue numbers in commits.
- **Branch per milestone**: `sprint{N}{letter}/{feature-name}` (e.g., `sprint11a/tx-broadcast`)
- **One commit per issue** with message format: `feat(scope): description` or `test(scope): description`, including the `#NN` issue reference
- **NEVER add `Co-Authored-By` lines** to commit messages
- **Doc updates** go on a separate `docs/` branch after milestone merge
- **PR per milestone**: push branch, create PR with `Closes #NN` lines, merge to master (master is a protected branch — all changes go through PRs)
- **Run `mvn test`** after every code change to verify nothing broke

### Environment
- **OS**: Windows with PowerShell
- **Shell**: Use `;` not `&&` for command chaining
- **IDE**: VS Code / Cursor
- **`gh` CLI**: available — used for creating issues and PRs

---

## Quick Reference

| Item | Value |
|------|-------|
| **Project** | BlockSmith - Educational Blockchain in Java |
| **Language** | Java 20 |
| **Build Tool** | Maven 3.9.x |
| **Test Framework** | JUnit 5 |
| **Serialization** | Gson 2.10.1 |
| **Current Phase** | Phase 2: Network Layer — Complete |
| **Current Sprint** | Sprint 11 (Mempool Sync) — Complete |
| **Current Milestone** | 11c Complete; next: Phase 3 (API & Interface) |
| **Total Tests** | 166 (all passing) |
| **Main Branch** | `master` |

---

## Project Goal

Build a **functional blockchain implementation from scratch** for educational purposes:
- Cryptographic hashing (SHA-256)
- Proof-of-Work mining
- Transaction management with Merkle trees
- Digital signatures (ECDSA)
- Wallet address generation
- P2P networking
- Balance tracking

---

## Project Structure

```
BlockSmith/
├── .ai/                              # AI/LLM documentation (READ FIRST!)
│   ├── ONBOARDING.md                 # ← YOU ARE HERE
│   ├── ARCHITECTURE.md               # Detailed class descriptions
│   ├── CONVENTIONS.md                # Code style guide
│   ├── STATUS.md                     # Current project status (check this!)
│   ├── prd.md                        # Product requirements
│   ├── tech-stack.md                 # Technologies used
│   ├── roadmap.md                    # Full project roadmap
│   └── sprints/                      # Sprint plans and logs
│       ├── sprint0/ through sprint11/ # Completed sprints (Phase 1 + Phase 2)
│       └── sprint-bonus/             # Optional features
│
├── src/main/java/com/blocksmith/
│   ├── BlockSmithDemo.java           # Main demo application
│   ├── core/                         # Core blockchain classes
│   │   ├── Block.java                # ✅ Complete (deterministic genesis)
│   │   ├── Blockchain.java           # ✅ Complete (external append, orphans, mempool prune)
│   │   ├── Transaction.java          # ✅ Complete (with signatures)
│   │   └── Wallet.java               # ✅ Complete (Sprint 5)
│   ├── util/                         # Utility classes
│   │   ├── HashUtil.java             # ✅ Complete
│   │   ├── BlockchainConfig.java     # ✅ Complete
│   │   └── BlockExplorer.java        # ⬜ TODO (Phase 3)
│   └── network/                      # Network layer (Sprint 8-11)
│       ├── MessageType.java          # ✅ Complete
│       ├── Message.java              # ✅ Complete
│       ├── MessageParser.java        # ✅ Complete
│       ├── MessageHandler.java       # ✅ Complete
│       ├── MessageContext.java       # ✅ Complete
│       ├── MessageListener.java      # ✅ Complete
│       ├── NetworkConfig.java        # ✅ Complete
│       ├── Node.java                 # ✅ Complete (broadcast + sync + mempool)
│       ├── Peer.java                 # ✅ Complete
│       ├── PeerState.java            # ✅ Complete (Sprint 9a)
│       ├── PeerInfo.java             # ✅ Complete (Sprint 9a)
│       ├── PeerManager.java          # ✅ Complete (Sprint 9b)
│       └── messages/                 # Concrete message classes (11 types)
│           ├── HelloMessage.java     # ✅ Complete
│           ├── PingMessage.java      # ✅ Complete
│           ├── PongMessage.java      # ✅ Complete
│           ├── GetPeersMessage.java  # ✅ Complete (Sprint 9d)
│           ├── PeersMessage.java     # ✅ Complete (Sprint 9d)
│           ├── NewBlockMessage.java  # ✅ Complete
│           ├── GetBlocksMessage.java # ✅ Complete (Sprint 10d)
│           ├── BlocksMessage.java    # ✅ Complete (Sprint 10d)
│           ├── NewTransactionMessage.java # ✅ Complete
│           ├── GetMempoolMessage.java # ✅ Complete (Sprint 11c)
│           └── MempoolMessage.java   # ✅ Complete (Sprint 11c)
│
├── src/test/java/com/blocksmith/     # 166 unit tests
├── pom.xml                           # Maven configuration
└── README.md                         # Public documentation
```

---

## What's Already Implemented

### Phase 1: Core Blockchain ✅ (Sprints 0-6)
- `HashUtil` - SHA-256 hashing
- `Block` - Block structure, mining, Merkle root, two constructors (transaction-based + legacy string), deterministic genesis (fixed timestamp)
- `BlockchainConfig` - Central constants (difficulty=4, reward=50 BSC, COINBASE, genesis timestamp)
- `Blockchain` - Chain management, genesis block, validation, tamper detection
- `Transaction` - Model with validation, ECDSA signature verification
- `Wallet` - ECDSA key pairs (secp256r1), Ethereum-style addresses (0x + 40 hex), signing
- Mining rewards (COINBASE), balance calculation, balance validation, double-spend prevention

### Phase 2: Network Layer ✅ Complete (Sprints 8-11)

**Sprint 8: P2P Networking** — message protocol (JSON), server/client TCP node, HelloMessage handshake, MessageParser/Handler/Context/Listener, PING→PONG.

**Sprint 9: Node Discovery** — PeerState/PeerInfo, PeerManager (MAX_PEERS), outgoing connections, heartbeat + dead-peer eviction, GET_PEERS/PEERS gossip, seed-node bootstrap.

**Sprint 10: Block Broadcasting** — Blockchain wired into Node, external block append with validation, NEW_BLOCK broadcast/validate/re-gossip, bounded orphan buffer with attach-on-parent, GET_BLOCKS/BLOCKS chain sync.

**Sprint 11: Mempool Sync** — NEW_TRANSACTION broadcast/relay + mempool dedupe, prune confirmed transactions on append, GET_MEMPOOL/MEMPOOL sync with request-on-connect.

---

## What's NOT Implemented Yet

### Phase 3: API & Interface (Sprints 12-15) ← NEXT
- REST API, web dashboard, basic smart contracts, multi-sig wallets

### Phase 4: Production Features (Sprints 16-19)
- Database persistence, dynamic difficulty, block limits, fee market

### Deferred / known follow-ups
- Mempool request is sent on outbound connect only; inbound side does not yet pull the peer's mempool
- Gossip auto-connect to DISCOVERED peers (needs MAX_PEERS guarding)
- Self-identification filtering in peer lists

---

## Common Commands

```bash
# Compile
mvn compile

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=MempoolSyncTest

# Run the demo (PowerShell)
mvn compile -q; java -cp target/classes com.blocksmith.BlockSmithDemo
```

---

## Key Design Decisions

1. **Backward Compatibility**: Block has two constructors (transaction-based + legacy string data)
2. **Merkle Root in Hash**: `hash = SHA256(index + timestamp + merkleRoot + previousHash + nonce)`
3. **Deterministic Genesis**: Genesis uses a fixed timestamp (`BlockchainConfig.GENESIS_TIMESTAMP`) so every node shares an identical chain root
4. **Defensive Copies**: `getTransactions()` returns `new ArrayList<>(transactions)`
5. **Mining Rewards**: COINBASE transactions (first tx in block, 50 BSC)
6. **Balance Calculation**: Simple scan of all transactions (not UTXO-based)
7. **Wallet Signs Transactions**: Wallet owns private key, validates address match before signing
8. **COINBASE Exception**: Mining rewards skip signature verification
9. **External block append** (`addBlock(Block)`): validates index, prevHash, hash integrity, and PoW; returns false (not throws) for untrusted network input
10. **Gossip storm prevention**: a block/transaction is relayed only when it is newly accepted (duplicates/invalid return false)

---

## Code Conventions (Summary)

- **THEORY comments required** on all key methods (see `CONVENTIONS.md` for full format)
- **Classes**: PascalCase | **Methods**: camelCase | **Constants**: UPPER_SNAKE_CASE
- **Test naming**: `methodName_scenario_expectedResult()` with `@DisplayName`
- **Test sections**: `// ===== SECTION HEADER =====`
- **Assertions**: Always include message parameter (e.g., `assertEquals(expected, actual, "reason")`)
- **Imports order**: project → java standard → third-party → test
- **Collections**: Return defensive copies or unmodifiable views
- **No magic numbers**: Use constants in `BlockchainConfig` / `NetworkConfig`
- **Handler tests**: pull the handler from the private `handlers` map by reflection and drive it with an in-memory `MessageContext` against an unstarted node

---

## Test Coverage

| Class | Tests | Description |
|-------|-------|-------------|
| HashUtilTest | 6 | SHA-256 basics |
| BlockTest | 12 | Block creation, mining, transactions, Merkle |
| BlockchainTest | 26 | Chain management, validation, tx pool, balance, deterministic genesis |
| MiningTest | 9 | PoW mechanics, difficulty scaling |
| TransactionTest | 22 | Tx creation, validation, signatures |
| WalletTest | 13 | Key generation, addresses, signing |
| MessageTest | 6 | Message serialization (Sprint 8a) |
| NodeTest | 8 | Node start/stop, connections (Sprint 8b) |
| PeerTest | 7 | Peer connections, handshake (Sprint 8c) |
| CommunicationTest | 6 | Bidirectional message exchange (Sprint 8d) |
| PeerInfoTest | 6 | Peer metadata, state transitions (Sprint 9a) |
| PeerManagerTest | 8 | Peer registry, MAX_PEERS enforcement (Sprint 9b) |
| HeartbeatTest | 4 | Heartbeat ping, dead-peer eviction (Sprint 9c) |
| PeerDiscoveryTest | 5 | Peer discovery messages and handlers (Sprint 9d) |
| ChainIntegrationTest | 2 | Node-backed blockchain, HELLO chain length (Sprint 10a) |
| ExternalBlockTest | 4 | External block append + validation (Sprint 10a) |
| BlockBroadcastTest | 4 | NEW_BLOCK serialization and handler (Sprint 10b) |
| OrphanBlockTest | 3 | Orphan buffering and attachment (Sprint 10c) |
| ChainSyncTest | 5 | GET_BLOCKS/BLOCKS sync handlers (Sprint 10d) |
| TransactionBroadcastTest | 4 | NEW_TRANSACTION serialization and handler (Sprint 11a) |
| MempoolPruneTest | 2 | Confirmed-tx pruning on block append (Sprint 11b) |
| MempoolSyncTest | 4 | GET_MEMPOOL/MEMPOOL sync handlers (Sprint 11c) |
| **Total** | **166** | All passing ✅ |

---

## Git Workflow

- **Main branch**: `master` (protected, stable — PRs merge here)
- **Feature branches**: `sprint{N}{letter}/{feature-name}` (e.g., `sprint11a/tx-broadcast`)
- **Doc branches**: `docs/{description}` (e.g., `docs/sprint11-complete`)
- **Commits**: One per issue, format: `feat(scope): description #NN` — never add `Co-Authored-By`
- **PRs**: created with `gh`, include `Closes #NN` lines, ff-only merge to sync local master
- **Latest**: Sprint 11 complete (PR #106); docs at PR #107

---

## For Deep Dives

| File | When to read |
|------|-------------|
| `ARCHITECTURE.md` | Need detailed class fields, methods, data flow diagrams |
| `CONVENTIONS.md` | Need full code style rules, THEORY comment format, test conventions |
| `STATUS.md` | Need exact current status, implementation table, feature checklist |
| `roadmap.md` | Need full project phases and timeline |
| `sprints/sprint11/sprint-plan.md` | Most recent sprint's milestones, issues, acceptance criteria |
| `sprints/sprint11/sprint-log.md` | What was completed in the latest sprint |

---

*Last updated: 2026-07-02 | Sprint 11 Complete (Milestone 11c) — Phase 2 Complete*
