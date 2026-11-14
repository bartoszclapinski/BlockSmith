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
- **PR per milestone**: push branch, create PR with `Closes #NN` lines, merge to master with a regular merge commit, not squash (master is a protected branch — all changes go through PRs)
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
| **Current Phase** | Phase 4: Production Features — ✅ Complete (all 4 phases done) |
| **Current Sprint** | Sprint 20 (Thread Safety, hardening) — Complete |
| **Current Milestone** | 20c Complete; next: fork choice (most-work chain + reorg) |
| **Total Tests** | 384 (all passing) |
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
│   │   ├── Blockchain.java           # ✅ Complete (external append, orphans, mempool prune, block content rules, fee-rate mining)
│   │   ├── Transaction.java          # ✅ Complete (with signatures and fees)
│   │   ├── Wallet.java               # ✅ Complete (Sprint 5)
│   │   ├── ChainParams.java          # ✅ Consensus params: block time, retarget window, clamp, powLimit, future drift (Sprint 17), block limits (Sprint 18)
│   │   └── MempoolPolicy.java        # ✅ Node policy: mempool size limit + incremental fee rate (Sprint 19)
│   ├── util/                         # Utility classes
│   │   ├── HashUtil.java             # ✅ Complete
│   │   ├── BlockchainConfig.java     # ✅ Complete
│   │   ├── JsonUtil.java             # ✅ Shared Gson + PublicKey adapter (Sprint 16)
│   │   ├── Target.java               # ✅ 256-bit PoW target + difficulty display (Sprint 17)
│   │   └── BlockExplorer.java        # ⬜ Stub only (explorer role covered by the web dashboard)
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
│   ├── api/                          # REST API (Sprint 12)
│   │   └── ApiServer.java            # ✅ Complete (Javalin: blocks, tx, mine, wallet, network, contracts, multisig + static hosting)
│   ├── storage/                      # Persistence (Sprint 16)
│   │   ├── ChainStore.java           # ✅ Store interface: blocks, mempool, lookups, backup
│   │   ├── NoOpChainStore.java       # ✅ Default (in-memory only)
│   │   ├── H2ChainStore.java         # ✅ Embedded H2: blocks, pending_tx, tx_index; backup/restore
│   │   └── StorageException.java     # ✅ Unchecked storage failure
│   ├── contract/                     # Smart contracts (Sprint 14) + multisig (Sprint 15)
│   │   ├── ScriptOp.java             # ✅ Opcode set (+ CHECKSIG/CHECKMULTISIG)
│   │   ├── ScriptVM.java             # ✅ Stack-based interpreter (hashlock/timelock + sighash/signatures)
│   │   ├── Contract.java             # ✅ Contract model (derived from chain) + claim sighash
│   │   ├── ContractStatus.java       # ✅ OPEN / CLAIMED
│   │   └── MultiSigWallet.java       # ✅ M-of-N wallet + CHECKMULTISIG lock (Sprint 15b)
│   └── BlockSmithNode.java           # ✅ Complete (runnable node: P2P + API + dashboard, Sprint 13a)
│
├── src/main/resources/public/        # Web dashboard (Sprint 13-15, redesigned PR #157/#158): index.html, app.js, style.css
├── src/test/java/com/blocksmith/     # 384 unit tests
├── pom.xml                           # Maven configuration (Gson, JUnit, Javalin)
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

### Phase 3: API & Interface ✅ Complete (Sprint 12-15)

**Sprint 12: REST API** — Javalin `ApiServer` on port 7070: block read endpoints + network status, transaction submit/lookup + mining (broadcast on write), wallet balance/create + peers, JSON error envelope (400/404/500).

**Sprint 13: Web Dashboard** — `BlockSmithNode` runnable entry point (Node + ApiServer in one process), Javalin static hosting of a vanilla HTML/JS/CSS dashboard at `/`: explorer view (blocks + network, 4s polling) and actions (create wallet, balance lookup, send tx, mine) with API errors surfaced inline. Run: `mvn exec:java` → `http://localhost:7070/`.

**Sprint 14: Smart Contracts** — `com.blocksmith.contract`: `ScriptVM` stack machine (opcodes for hashlock/timelock; never throws — malformed scripts evaluate to false). Contracts are modelled as transactions (deploy TO `CONTRACT:<id>` with a locking script, claim FROM it with unlocking data), so the balance model needs no changes; the contract registry is derived from blocks so all nodes converge. REST: `POST /api/contracts`, `GET /api/contracts[/{id}]`, `POST /api/contracts/{id}/claim`; dashboard Contracts panel.

**Sprint 15: Multi-signature Wallets** — built ON the Sprint 14 VM. `SignatureUtil` (ECDSA sign/verify, never throws) + `CHECKSIG`/`CHECKMULTISIG` opcodes with a sighash context (`ScriptVM.execute` overload; existing call sites pass an empty sighash). An M-of-N multisig is just a contract whose lock is a `CHECKMULTISIG` (`MultiSigWallet` builds it); the claim sighash `SHA256(contractId+claimer+amount)` (`Contract.claimSighash`) binds signatures to one claim, so they can't be replayed. The one wiring change is `Blockchain.isValidClaim` feeding the sighash to the VM. REST: `POST /api/multisig/create`, `POST /api/multisig/claim` (server-held member keys, an educational signing convenience — private keys never serialized); dashboard Multisig panel.

**Dashboard redesign (PR #157, #158)** — not a sprint; done after Sprint 15 from the design handoff in `.ai/design/UIUX redesign project/`. Single-screen "command deck" (top bar, chain-of-blocks hero, activity feed / actions / balance + contracts grid), "Clay" light/dark themes via CSS variables, command palette (Ctrl/⌘+K), side drawers for send / create wallet / deploy (hashlock, multisig 2-of-3) / claim, toasts, an activity feed derived client-side by diffing the 4s polls, and a 5-step onboarding tour (opt-out stored in `localStorage` as `bs_tour_optout`; `?` restarts it). Still vanilla JS, no build step. `GET /api/network/status` now also returns `difficulty`.

### Phase 4: Production Features — ✅ complete (Sprint 16-19)

**Sprint 16: Database Persistence** — `com.blocksmith.storage`: `ChainStore` + embedded H2 (`H2ChainStore`). Every appended block (one `Blockchain.appendBlock()` path) and every pending tx is saved; on startup `restoreFromStore()` replays stored blocks through the normal `addBlock(Block)` validation (the DB is never trusted). `tx_index` gives block-by-hash, tx-by-id and address history (`GET /api/blocks/hash/{hash}`, `GET /api/wallet/{address}/transactions`); index answers are verified against the chain. Online backup via `POST /api/admin/backup` (localhost only), restore with `BlockSmithNode ... --restore <zip>`. Data lives in `data/node-<p2pPort>/`. Along the way: `JsonUtil.GSON` (signed txs serialize; #259) and block validation now recomputes tx ids and the Merkle root (#260). Follow-up #264: contract scripts are now part of the tx id, so they are covered too.

**Sprint 17: Difficulty Adjustment** — the PoW rule is a 256-bit numeric target (`Target`; "N zeros" = `16^(64-N) - 1`). `ChainParams` (DEFAULT: 10 s blocks, retarget every 10, max 4x, powLimit 3 zeros, 15 s future drift). `Blockchain.getNextTarget()` = `old * actual / expected` at window boundaries; targets are derived from timestamps (never stored), so peers and restore replay agree, and every append path validates against the expected target. Timestamp rules: after the median of the last 11 blocks, at most 15 s ahead of the node's `Clock`. API: live `difficulty`, `blocksUntilRetarget`, `GET /api/network/difficulty`; dashboard shows the countdown.

**Sprint 18: Block Rules, Size & Fees** — started as "Block Size & Limits" and grew when a planning test showed peer blocks were never checked for *content* (#281: a peer could mint 1,000,000 coins and overspend). 18a: `blockRuleViolation` / `transactionRuleViolation` check every block on every path (peer, orphan attach, restore, our own mining) — one coinbase, first, capped; valid and verifying txs; no duplicates; contract claims through the VM; no overspend, tracked across the block by `BlockCheck`; `isChainValid()` replays the chain. 18b: `ChainParams.maxBlockBytes` (16 KB of JSON) / `maxBlockTransactions` (50); mining fills a block and leaves the rest pending. 18c: `Transaction.fee` (in id and signature only when > 0, so old txs keep their ids), sender pays `getTotalCost()`, coinbase <= reward + fees, mining picks by `feeRate()` (fee per byte). 18d: `fee` in `POST /api/transactions`, `GET /api/mempool` (mining order), blocks with `sizeBytes` / `totalFees`, fee field in the dashboard.

**Sprint 19: Fee Market** — 19a: `MempoolPolicy` (node policy, not consensus: 64 KB / 200 txs / incremental fee rate); a full mempool evicts the lowest fee rate (newest first on ties) only for a tx that beats every victim by the increment; `getMempoolMinFeeRate()`. 19b: replace-by-fee. In an account model a replacement with a new id is just a second payment, so `Transaction.sequence` (optional, in id/signature only when set) plus a consensus rule: a `(sender, sequence)` pair confirms at most once (`confirmedSequences`). Mempool rules after BIP125: only the fee changes, higher fee and rate + increment, same key for signed txs. 19c: `estimateFeeRate(n)` = max(mempool view: simulate n blocks like mining; history view: cheapest rate of recent full blocks; mempool minimum). 19d: `GET /api/fees/estimate`, `POST /api/transactions/{id}/bump`, `submitTransaction()` reasons in 400s, dashboard suggested fees + Mempool drawer with Bump.

### Hardening (after the roadmap)

**Sprint 20: Thread Safety** — peer listener threads, Jetty threads and the heartbeat share one `Blockchain`. A probe showed a CME on reads, a corrupted chain from concurrent mining and overdrawn mempools. 20a: `Blockchain` is a monitor (all state-touching public methods `synchronized`, getters return copies). 20b: mining = template (locked) -> nonce search (unlocked; `Block.mineBlock(target, stop)` stops when the `volatile tipHash` moves) -> submit on the same tip (stale work retried); cancellable. 20c: `atomically(Supplier)` for multi-value API responses, `pendingOutgoing` with fees, first two-node TCP test. Found: **no fork choice** (two miners at one height diverge for good).

---

## What's NOT Implemented Yet

### Roadmap complete, hardening in progress
- All planned sprints (0-19) are done; Sprint 20 (thread safety) too. Next: fork choice (most-work chain + reorg), then the deferred list below or a bonus feature (SPV, explorer)

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

# Run the node + web dashboard (http://localhost:7070/)
mvn exec:java

# Run the teaching demo
mvn exec:java -Dexec.mainClass=com.blocksmith.BlockSmithDemo
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
| MessageTest | 8 | Message serialization incl. signed transactions (Sprint 8a, #259) |
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
| TransactionBroadcastTest | 5 | NEW_TRANSACTION serialization and handler (Sprint 11a), gossiped fee bump (Sprint 19b) |
| MempoolPruneTest | 2 | Confirmed-tx pruning on block append (Sprint 11b) |
| MempoolSyncTest | 4 | GET_MEMPOOL/MEMPOOL sync handlers (Sprint 11c) |
| ApiReadEndpointsTest | 6 | REST block/status read endpoints (Sprint 12a) + signed tx in blocks (#259) |
| ApiTransactionEndpointsTest | 5 | REST transaction submit/lookup + mining (Sprint 12b) |
| ApiWalletNetworkEndpointsTest | 4 | REST wallet/network endpoints + JSON errors (Sprint 12c) |
| ApiStaticHostingTest | 3 | Dashboard shell + static assets alongside the API (Sprint 13a) |
| ApiExplorerTest | 2 | Explorer mount points + block JSON shape (Sprint 13b) |
| ApiDashboardActionsTest | 2 | Dashboard action chain end to end (Sprint 13c) |
| ScriptVMTest | 27 | Script opcodes, hashlock/timelock, signature opcodes, failure semantics (Sprint 14a, 15a) |
| ChainContractTest | 9 | Contract deploy/claim, timelock, double-claim, convergence (Sprint 14b) |
| ApiContractsTest | 3 | Contract lifecycle over HTTP + error envelopes (Sprint 14c) |
| MultiSigContractTest | 5 | M-of-N deploy/claim, threshold, wrong-key, replay safety, convergence (Sprint 15b) |
| ApiMultiSigTest | 2 | Multisig lifecycle over HTTP + error envelopes (Sprint 15c) |
| H2ChainStoreTest | 8 | Store round-trip, order, idempotency, signed tx, reopen (Sprint 16a) |
| PersistRestoreTest | 6 | Restart round-trip, mempool, tamper rejected, orphans, no double-mine (Sprint 16b) |
| BlockIntegrityTest | 7 | Edited tx (amount or contract script) keeps the hash but fails the Merkle check (#260, #264) |
| IndexedQueryTest | 9 | Block by hash, tx by id, address history, index/scan parity, stale rows (Sprint 16c) |
| ApiIndexedQueryTest | 4 | History / block-by-hash / tx location over HTTP (Sprint 16c) |
| BackupRestoreTest | 8 | Backup round-trip, snapshot, old DB kept, bad/damaged/traversal zips (Sprint 16d) |
| ApiBackupTest | 3 | `POST /api/admin/backup` path, uniqueness, 503 (Sprint 16d) |
| TargetTest | 7 | Numeric target = leading-zero rule, boundaries, display, hex (Sprint 17a) |
| RetargetTest | 10 | Fast/slow windows, clamp, powLimit, weak peer blocks/orphans rejected, replay parity (Sprint 17b) |
| TimestampRulesTest | 6 | Median rule, future limit, bounded time-warp, mining when peers are ahead (Sprint 17c) |
| DifficultyReportTest | 3 | Epochs, retarget countdown, average block time (Sprint 17d) |
| ApiDifficultyTest | 3 | Status difficulty fields, `/api/network/difficulty` shape (Sprint 17d) |
| BlockContentRulesTest | 13 | Inflated/extra/misplaced coinbase, overspend (also split), duplicates, bad claims, forged stored block (Sprint 18a) |
| BlockLimitsTest | 6 | Oversize / too many txs rejected, full mempool spread over blocks (Sprint 18b) |
| FeeTest | 9 | Fee in id/signature, who pays, coinbase cap, fee-rate priority (Sprint 18c) |
| ApiFeesTest | 4 | Fee over HTTP, bad fee 400, `/api/mempool` order, block size + limits (Sprint 18d) |
| MempoolLimitTest | 7 | Full pool refuses, eviction order, byte/count limits, min fee rate, restore trim (Sprint 19a) |
| ReplaceByFeeTest | 11 | Bump rules, same key, full pool, one version per sequence, conflicts dropped, restore (Sprint 19b) |
| FeeEstimationTest | 5 | Floor, mempool cut-off, 1 >= 3 >= 6, history after full blocks, min fee rate (Sprint 19c) |
| ApiFeeMarketTest | 4 | Estimate shape, bump end to end, bump refused, full mempool 400 (Sprint 19d) |
| ConcurrencyTest | 5 | Reads during appends, concurrent mining, overspend, same block twice, snapshots (Sprint 20a) |
| MiningOutsideLockTest | 6 | Stop condition, unblocked reads, stale template, cancel, concurrent mines (Sprint 20b) |
| NodeConcurrencyTest | 3 | `atomically`, consistent status/wallet under load, two nodes over TCP converge (Sprint 20c) |
| **Total** | **384** | All passing ✅ |

---

## Git Workflow

- **Main branch**: `master` (protected, stable — PRs merge here)
- **Feature branches**: `sprint{N}{letter}/{feature-name}` (e.g., `sprint11a/tx-broadcast`)
- **Doc branches**: `docs/{description}` (e.g., `docs/sprint11-complete`)
- **Commits**: One per issue, format: `feat(scope): description #NN` — never add `Co-Authored-By`
- **PRs**: created with `gh`, include `Closes #NN` lines; merged with a **regular merge commit** (`gh pr merge N --merge`, not squash) so the one-commit-per-issue history stays on master (decided 2026-11-11; #130-#278 were squash-merged); then ff-only pull to sync local master
- **Latest**: Sprint 20 complete (Thread Safety, PR #316, #317, #318); roadmap done, hardening in progress
- **Maven path**: run `mvn` from `F:\Repos\Java\BlockSmith` (capital J); from a lowercase path Jetty 404s static files and 3 dashboard tests fail

---

## For Deep Dives

| File | When to read |
|------|-------------|
| `ARCHITECTURE.md` | Need detailed class fields, methods, data flow diagrams |
| `CONVENTIONS.md` | Need full code style rules, THEORY comment format, test conventions |
| `STATUS.md` | Need exact current status, implementation table, feature checklist |
| `roadmap.md` | Need full project phases and timeline |
| `sprints/sprint20/sprint-plan.md` | Most recent sprint's milestones, issues, acceptance criteria |
| `sprints/sprint20/sprint-log.md` | What was completed in the latest sprint |

---

*Last updated: 2026-11-14 | Sprint 20 Complete (Thread Safety) — roadmap done, hardening in progress*
