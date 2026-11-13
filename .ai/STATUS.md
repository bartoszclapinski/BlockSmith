# 📊 BlockSmith - Current Status

> **Quick reference for current project state. Update after each sprint.**

---

## 🎯 Current Status

| Field | Value |
|-------|-------|
| **Phase** | 3 - API & Interface - ✅ COMPLETE |
| **Current Sprint** | 19 (Fee Market) - Planned |
| **Current Milestone** | 18d Complete (Fees, mempool and block size in API + dashboard) |
| **Status** | Sprint 18 complete (18a-18d): block content rules, size limits, fees with fee-rate mining, `/api/mempool`. Next: Sprint 19 (Fee Market) |

---

## ✅ Completed Sprints

| Sprint | Title | Key Deliverables | Date |
|--------|-------|------------------|------|
| 0 | Project Setup | Maven, structure, placeholders | 2026-01-19 |
| 1 | Fundamentals | HashUtil, Block, Config | 2026-01-19 |
| 2 | Proof-of-Work | Mining, nonce, difficulty | 2026-01-20 |
| 3 | Blockchain | Chain management, validation | 2026-01-20 |
| 4 | Transactions | Transaction model, Merkle tree, pending pool | 2026-01-21 |
| 5 | Wallets | ECDSA keys, addresses, signing, verification | 2026-01-27 |

---

## 📈 Progress

```
Phase 1: Core Blockchain     [███████████████] 100% ✅ COMPLETE
├── Sprint 0: Setup          ✅
├── Sprint 1: Fundamentals   ✅
├── Sprint 2: Proof-of-Work  ✅
├── Sprint 3: Blockchain     ✅
├── Sprint 4: Transactions   ✅
├── Sprint 5: Wallets        ✅
└── Sprint 6: Economics      ✅

Phase 2: Network Layer       [███████████████] 100% ✅ COMPLETE
├── Sprint 8: P2P Networking ✅ COMPLETE (8a ✅, 8b ✅, 8c ✅, 8d ✅)
├── Sprint 9: Node Discovery ✅ COMPLETE (9a ✅, 9b ✅, 9c ✅, 9d ✅)
├── Sprint 10: Broadcasting  ✅ COMPLETE (10a ✅, 10b ✅, 10c ✅, 10d ✅)
└── Sprint 11: Mempool Sync  ✅ COMPLETE (11a ✅, 11b ✅, 11c ✅)

Phase 3: API & Interface     [███████████████] 100% ✅ COMPLETE
├── Sprint 12: REST API      ✅ COMPLETE (12a ✅, 12b ✅, 12c ✅)
├── Sprint 13: Web Dashboard ✅ COMPLETE (13a ✅, 13b ✅, 13c ✅)
├── Sprint 14: Smart Contracts ✅ COMPLETE (14a ✅, 14b ✅, 14c ✅)
└── Sprint 15: Multi-sig Wallets ✅ COMPLETE (15a ✅, 15b ✅, 15c ✅)

Phase 4: Production Features  [███████████░░░░] 75% ← IN PROGRESS
├── Sprint 16: Persistence   ✅ COMPLETE (16a ✅, 16b ✅, 16c ✅, 16d ✅)
├── Sprint 17: Difficulty    ✅ COMPLETE (17a ✅, 17b ✅, 17c ✅, 17d ✅)
├── Sprint 18: Block Rules   ✅ COMPLETE (18a ✅, 18b ✅, 18c ✅, 18d ✅)
└── Sprint 19: Fee Market    📋 PLANNED (19a ⬜, 19b ⬜, 19c ⬜, 19d ⬜)
```

---

## 🧪 Test Summary

| Test Class | Count | Status |
|------------|-------|--------|
| HashUtilTest | 6 | ✅ |
| BlockTest | 12 | ✅ |
| BlockchainTest | 26 | ✅ |
| MiningTest | 9 | ✅ |
| TransactionTest | 22 | ✅ |
| WalletTest | 13 | ✅ |
| MessageTest | 8 | ✅ |
| NodeTest | 8 | ✅ |
| PeerTest | 7 | ✅ |
| CommunicationTest | 6 | ✅ |
| PeerInfoTest | 6 | ✅ |
| PeerManagerTest | 8 | ✅ |
| HeartbeatTest | 4 | ✅ |
| PeerDiscoveryTest | 5 | ✅ |
| ChainIntegrationTest | 2 | ✅ |
| ExternalBlockTest | 4 | ✅ |
| BlockBroadcastTest | 4 | ✅ |
| OrphanBlockTest | 3 | ✅ |
| ChainSyncTest | 5 | ✅ |
| TransactionBroadcastTest | 4 | ✅ |
| MempoolPruneTest | 2 | ✅ |
| MempoolSyncTest | 4 | ✅ |
| ApiReadEndpointsTest | 6 | ✅ |
| ApiTransactionEndpointsTest | 5 | ✅ |
| ApiWalletNetworkEndpointsTest | 4 | ✅ |
| ApiStaticHostingTest | 3 | ✅ |
| ApiExplorerTest | 2 | ✅ |
| ApiDashboardActionsTest | 2 | ✅ |
| ScriptVMTest | 27 | ✅ |
| ChainContractTest | 9 | ✅ |
| ApiContractsTest | 3 | ✅ |
| MultiSigContractTest | 5 | ✅ |
| ApiMultiSigTest | 2 | ✅ |
| H2ChainStoreTest | 8 | ✅ |
| PersistRestoreTest | 6 | ✅ |
| BlockIntegrityTest | 7 | ✅ |
| IndexedQueryTest | 9 | ✅ |
| ApiIndexedQueryTest | 4 | ✅ |
| BackupRestoreTest | 8 | ✅ |
| ApiBackupTest | 3 | ✅ |
| TargetTest | 7 | ✅ |
| RetargetTest | 10 | ✅ |
| TimestampRulesTest | 6 | ✅ |
| DifficultyReportTest | 3 | ✅ |
| ApiDifficultyTest | 3 | ✅ |
| BlockContentRulesTest | 13 | ✅ |
| BlockLimitsTest | 6 | ✅ |
| FeeTest | 9 | ✅ |
| ApiFeesTest | 4 | ✅ |
| **Total** | **342** | ✅ |

Last test run: `mvn test` - All passing

---

## 📁 Implementation Status

### Core Classes (`com.blocksmith.core`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| Block.java | ✅ Complete | ~290 | Transactions + Merkle root + `hasValidMerkleRoot()` recomputes it from the txs (Sprint 16b, #260) + `mineBlock(BigInteger target)`, explicit-timestamp constructor (Sprint 17) + `sizeBytes()` (Sprint 18b) |
| Blockchain.java | ✅ Complete | ~1440 | Pending pool + mining + external append + orphan buffer (Sprint 10) + tx dedupe + mempool prune (Sprint 11) + contract registry (deploy/claim, derived from chain) (Sprint 14b) + optional `ChainStore`, single `appendBlock()`, `restoreFromStore()` replay (Sprint 16b) + `getBlockByHash` / `findTransaction` / `getTransactionHistory` (Sprint 16c) + `backupTo` (Sprint 16d) + `ChainParams`, targets per height, `getNextTarget` / retarget, median-time-past + future limit with injectable `Clock`, difficulty report (Sprint 17) + block content rules (`blockRuleViolation` / `transactionRuleViolation`, `BlockCheck`), `isChainValid()` by replay (Sprint 18a) + block limits, mining fills up to them (18b) + coinbase = reward + fees, fee-rate block assembly, `getPendingInMiningOrder()` (18c/18d) |
| Transaction.java | ✅ Complete | ~340 | Validation + signing + verification + optional contract locking/unlocking scripts (Sprint 14b) + `hasValidId()` (Sprint 16b, #260); contract scripts are part of the id (#264) + `fee` (in id/signature only when > 0), `getTotalCost()`, `sizeBytes()`, `feeRate()` (Sprint 18c) |
| Wallet.java | ✅ Complete | ~169 | ECDSA keys + signing |
| ChainParams.java | ✅ Complete | ~100 | Consensus params record: initial target, block time, retarget interval, 4x clamp, powLimit, max future drift (Sprint 17b/17c), max block bytes / transactions (Sprint 18b) |

### Utility Classes (`com.blocksmith.util`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| HashUtil.java | ✅ Complete | ~50 | SHA-256 |
| SignatureUtil.java | ✅ Complete | ~140 | ECDSA sign/verify (secp256r1) + hex encode/decode; verify never throws (Sprint 15a) |
| BlockchainConfig.java | ✅ Complete | ~56 | Constants |
| Target.java | ✅ Complete | ~120 | 256-bit PoW target: from leading zeros, meets (never throws), difficulty display, hex (Sprint 17a) |
| JsonUtil.java | ✅ Complete | ~60 | Shared Gson with a `PublicKey` adapter (hex X.509); used by storage, P2P messages and the API (Sprint 16a/16b, #259) |
| BlockExplorer.java | ⬜ Stub | ~14 | Explorer role covered by the API + dashboard |

### Network Classes (`com.blocksmith.network`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| MessageType.java | ✅ Complete | ~45 | Message types enum |
| Message.java | ✅ Complete | ~82 | Base message class |
| NetworkConfig.java | ✅ Complete | ~103 | Network constants + heartbeat + seed nodes |
| Node.java | ✅ Complete | ~650 | Server + message loop + peer tracking + heartbeat eviction + peer discovery + seed bootstrap + block broadcast + chain sync (Sprint 10) + tx broadcast + mempool sync (Sprint 11) |
| Peer.java | ✅ Complete | ~294 | Client + async listener thread |
| MessageParser.java | ✅ Complete | ~116 | JSON-to-Message routing (Sprint 8d) |
| MessageHandler.java | ✅ Complete | ~36 | Handler functional interface (Sprint 8d) |
| MessageContext.java | ✅ Complete | ~58 | Connection wrapper for handlers (Sprint 8d) |
| MessageListener.java | ✅ Complete | ~43 | Async listener interface (Sprint 8d) |
| PeerState.java | ✅ Complete | ~43 | Peer connection lifecycle enum (Sprint 9a) |
| PeerInfo.java | ✅ Complete | ~110 | Peer metadata tracking (Sprint 9a) |
| PeerManager.java | ✅ Complete | ~145 | Peer registry, MAX_PEERS enforcement (Sprint 9b) |
| messages/*.java | ✅ Complete | ~280 | 11 concrete message types (+ GetMempool/Mempool, Sprint 11c) |

### Storage Classes (`com.blocksmith.storage`) - Sprint 16

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| ChainStore.java | ✅ Complete | ~110 | Store interface: blocks, pending txs; indexed lookups (default: not indexed); backup (default: unsupported) |
| NoOpChainStore.java | ✅ Complete | ~45 | Default store: remembers nothing, keeps `new Blockchain()` in memory |
| H2ChainStore.java | ✅ Complete | ~470 | Embedded H2 (JDBC): `blocks`, `pending_tx`, `tx_index` (indexed on sender/recipient/block); block + index rows in one DB transaction; `BACKUP TO`; `restoreBackup` (staging, single-entry check, probe open, old DB kept) |
| StorageException.java | ✅ Complete | ~15 | Unchecked wrapper for storage failures |

### API Classes (`com.blocksmith.api`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| ApiServer.java | ✅ Complete | ~780 | Javalin REST API: blocks, transactions, mining, wallet, network, JSON errors (Sprint 12) + static dashboard hosting (Sprint 13a) + contract deploy/claim/inspect endpoints (Sprint 14c) + multisig create/claim endpoints, server-held member keys (Sprint 15c) + `JsonUtil` Gson (#259) + block by hash, address history, tx location (Sprint 16c) + `POST /api/admin/backup`, loopback only (Sprint 16d) + live difficulty in status, `GET /api/network/difficulty` (Sprint 17d) + optional `fee`, `GET /api/mempool`, block `sizeBytes` / `totalFees`, limits in status (Sprint 18d) |

### Contract Classes (`com.blocksmith.contract`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| ScriptOp.java | ✅ Complete | ~55 | Opcode enum: PUSH/DUP/DROP/SHA256/EQUAL(VERIFY)/VERIFY/ADD/SUB/GREATER/LESS/CHECKLOCKTIME (Sprint 14a) + CHECKSIG/CHECKMULTISIG (Sprint 15a) |
| ScriptVM.java | ✅ Complete | ~290 | Stack machine; hashlock/timelock; sighash + signature opcodes; never throws (malformed = false) (Sprint 14a, 15a) |
| Contract.java | ✅ Complete | ~120 | Contract model: locking script, amount, funder, status, derived from chain (Sprint 14b) + claimSighash (Sprint 15b) |
| ContractStatus.java | ✅ Complete | ~12 | OPEN / CLAIMED (Sprint 14b) |
| MultiSigWallet.java | ✅ Complete | ~130 | M-of-N wallet: builds CHECKMULTISIG lock, derives MS: address; public keys only (Sprint 15b) |

### Web Dashboard (`src/main/resources/public`)

| File | Status | Notes |
|------|--------|-------|
| index.html | ✅ Complete | Single-screen "command deck": top bar, chain-of-blocks hero, activity feed / actions / balance + contracts grid, footer; `data-tour` anchors (redesign, PR #157/#158) |
| app.js | ✅ Complete | Vanilla JS, no build step: 4s polling, client-side activity feed (diff of polls), command palette (Ctrl/⌘+K), side drawers (send, create wallet, deploy hashlock/multisig, claim), toasts, theme toggle, 5-step onboarding tour (redesign, PR #157/#158) |
| style.css | ✅ Complete | CSS-variable themes "Clay dark" + "Clay" (light), `prefers-color-scheme` default, animated background canvas (respects `prefers-reduced-motion`) (redesign, PR #157) |

Design handoff (prototype + README) lives in `.ai/design/UIUX redesign project/`.

### Entry Points

| Class | Status | Notes |
|-------|--------|-------|
| BlockSmithNode.java | ✅ Complete | Runnable node: P2P Node + ApiServer + dashboard in one process (Sprint 13a). Default `mvn exec:java` target + jar main class (Sprint 14) + H2 store under `data/node-<p2pPort>/`, restore report, `--restore <zip>`, backups enabled (Sprint 16) |
| BlockSmithDemo.java | ✅ Complete | Mining + Transactions demo (`mvn exec:java -Dexec.mainClass=com.blocksmith.BlockSmithDemo`) |

---

## 🔧 Key Features Implemented

- [x] SHA-256 hashing
- [x] Block structure with timestamps
- [x] Proof-of-Work mining
- [x] Difficulty-based validation
- [x] Blockchain with Genesis block
- [x] Chain validation and tamper detection
- [x] Transaction model with validation
- [x] Merkle tree calculation
- [x] Pending transaction pool (mempool)
- [x] Mining rewards (COINBASE)
- [x] Balance calculation
- [x] ECDSA key pairs
- [x] Wallet address generation (0x format)
- [x] Transaction signing
- [x] Signature verification
- [x] Balance validation before transfer (Sprint 6)
- [x] Reject COINBASE from users (Sprint 6)
- [x] Track pending outgoing amounts (Sprint 6)
- [x] Network message protocol with JSON serialization (Sprint 8a)
- [x] Server-side TCP socket node (Sprint 8b)
- [x] Multi-threaded connection acceptance (Sprint 8b)
- [x] Client-side peer connections (Sprint 8c)
- [x] HelloMessage handshake protocol (Sprint 8c)
- [x] MessageParser for JSON message routing (Sprint 8d)
- [x] MessageHandler interface + MessageContext wrapper (Sprint 8d)
- [x] Node message loop with handler registry (Sprint 8d)
- [x] Async message listener in Peer (Sprint 8d)
- [x] Default PING -> PONG handler (Sprint 8d)
- [x] Bidirectional message exchange (Sprint 8d)
- [x] PeerState enum for connection lifecycle (Sprint 9a)
- [x] PeerInfo class for peer metadata tracking (Sprint 9a)
- [x] PeerManager registry with MAX_PEERS enforcement (Sprint 9b)
- [x] Node peer tracking + outgoing connections (connectToPeer) (Sprint 9b)
- [x] Heartbeat PING scheduler + PONG handling (Sprint 9c)
- [x] Dead peer detection and eviction on timeout (Sprint 9c)
- [x] GetPeersMessage / PeersMessage for peer discovery gossip (Sprint 9d)
- [x] GET_PEERS / PEERS handlers in Node (Sprint 9d)
- [x] Seed-node config + bootstrap on startup (Sprint 9d)
- [x] Blockchain reference wired into Node + real HELLO chain length (Sprint 10a)
- [x] External block append with validation, no re-mining (Sprint 10a)
- [x] Node.broadcastBlock + NEW_BLOCK validate/append/re-gossip (Sprint 10b)
- [x] Sender exclusion on relay to prevent gossip storms (Sprint 10b)
- [x] Bounded orphan buffer with attach-on-parent-arrival (Sprint 10c)
- [x] GetBlocksMessage / BlocksMessage for chain sync (Sprint 10d)
- [x] GET_BLOCKS / BLOCKS handlers + behind-peer sync trigger (Sprint 10d)
- [x] Deterministic genesis block (fixed timestamp, shared chain root)
- [x] Node.broadcastTransaction + NEW_TRANSACTION validate/add/re-gossip (Sprint 11a)
- [x] Transaction dedupe in the mempool to stop gossip storms (Sprint 11a)
- [x] Prune confirmed transactions from the mempool on append (Sprint 11b)
- [x] GetMempoolMessage / MempoolMessage for mempool sync (Sprint 11c)
- [x] GET_MEMPOOL / MEMPOOL handlers + mempool request on connect (Sprint 11c)
- [x] Javalin REST API server on a dedicated port (Sprint 12a)
- [x] Block read endpoints + network status (Sprint 12a)
- [x] Transaction submit/lookup + mining endpoints, broadcast on write (Sprint 12b)
- [x] Wallet balance/create + peers endpoints (Sprint 12c)
- [x] Consistent JSON error envelope (400/404/500) (Sprint 12c)
- [x] Runnable node entry point: Node + ApiServer in one process (Sprint 13a)
- [x] Javalin static hosting for the dashboard at `/` (Sprint 13a)
- [x] Explorer view: blocks + network panel with polling refresh (Sprint 13b)
- [x] Dashboard actions: create wallet, balance lookup, send tx, mine (Sprint 13c)
- [x] API error envelopes surfaced inline in the UI (Sprint 13c)
- [x] Stack-based script VM with hashlock/timelock opcodes; never throws (Sprint 14a)
- [x] Contract deploy/claim on the chain, registry derived from blocks (Sprint 14b)
- [x] Contract balance rules + script-verified claims + double-claim protection (Sprint 14b)
- [x] Contract REST endpoints + dashboard panel (deploy/claim) (Sprint 14c)
- [x] Signature opcodes CHECKSIG/CHECKMULTISIG + sighash in the script VM (Sprint 15a)
- [x] ECDSA sign/verify helper (SignatureUtil); verify never throws (Sprint 15a)
- [x] M-of-N multisig as a contract (MultiSigWallet + CHECKMULTISIG lock) (Sprint 15b)
- [x] Claim sighash SHA256(contractId+claimer+amount) binds signatures, replay-safe (Sprint 15b)
- [x] Multisig REST endpoints + dashboard panel; server-held keys, never serialized (Sprint 15c)
- [x] Dashboard redesign: command-deck layout, light/dark themes, command palette, side drawers, activity feed (PR #157)
- [x] Onboarding tour (5-step spotlight, opt-out remembered, `?` restarts) + drawer backdrop fix (PR #158)
- [x] H2 `ChainStore` storage layer; blocks stored in the P2P JSON format (Sprint 16a)
- [x] Signed transactions serialize everywhere (`JsonUtil` PublicKey adapter) (Sprint 16a/16b, #259)
- [x] Persist every appended block + mempool; restore on startup by replaying through validation (Sprint 16b)
- [x] Block validation recomputes tx ids and the Merkle root; edited blocks rejected from peers and disk (Sprint 16b, #260)
- [x] Indexed lookups: block by hash, tx by id, address history with confirmations (Sprint 16c)
- [x] Online backup (`POST /api/admin/backup`) + `--restore <zip>` at startup (Sprint 16d)
- [x] Contract scripts are part of the transaction id, so swapping one inside a block is detected (#264)
- [x] Numeric 256-bit PoW target, exactly equivalent to the old leading-zero rule (Sprint 17a)
- [x] Difficulty retargets every 10 blocks toward 10 s/block, clamped to 4x, derived from timestamps (Sprint 17b)
- [x] Timestamp rules: after the median of the last 11 blocks, at most 15 s in the future (Sprint 17c)
- [x] Live difficulty + retarget countdown in the API and dashboard (Sprint 17d)
- [x] Block content rules on every path: coinbase cap, no overspend, no duplicates, signatures, contract claims (Sprint 18a, #281)
- [x] Block limits: 16 KB / 50 transactions; mining fills a block and leaves the rest pending (Sprint 18b)
- [x] Transaction fees: sender pays amount + fee, miner collects them, blocks filled by fee per byte (Sprint 18c)
- [x] Fees, `/api/mempool` and block size in the API and dashboard (Sprint 18d)

---

## 🌳 Git Status

| Item | Value |
|------|-------|
| **Current Branch** | `master` |
| **Last Commit** | Sprint 18 complete (PR #290, #291, #292, #293) |
| **Tag** | `v1.0.0` (Phase 1) |
| **Main Branch** | `master` |

---

## ⚠️ Known Issues

- Multisig member keys are held in memory by the API (by design, not persisted):
  a multisig created via the dashboard cannot be claimed after a node restart.

---

## 📝 Notes for Next Session

1. **Sprint 16 COMPLETE** - Database Persistence (2026-11-10 to 2026-11-11)
   - 16a (PR #258): `com.blocksmith.storage` - `ChainStore`, `NoOpChainStore`,
     `H2ChainStore` (H2 2.5.252, embedded). Blocks stored as the same Gson JSON
     peers exchange. Found: plain Gson cannot serialize a signed tx's EC
     `PublicKey` on Java 17+ -> `JsonUtil.GSON` with a hex X.509 adapter
   - 16b (PR #261): `Blockchain(ChainStore)`, one `appendBlock()` for all four
     append paths (saves the block first), mempool mirrored, `restoreFromStore()`
     replays through `addBlock(Block)` and stops at the first invalid block;
     `BlockSmithNode` uses `data/node-<p2pPort>/`. Bugs fixed: #259 (P2P + API
     used plain Gson), #260 (block validation never recomputed tx ids / Merkle
     root, so edited blocks were accepted from peers and disk). Live-tested with
     a hard kill
   - 16c (PR #262): `tx_index` table, store lookups, `getBlockByHash` /
     `findTransaction` / `getTransactionHistory` (index = hint, verified against
     the chain), `GET /api/blocks/hash/{hash}`, `GET /api/wallet/{address}/transactions`.
     16b databases get their index from the restore replay. Also fixed a flaky
     `MiningTest` (fixed timestamp)
   - 16d (PR #263): online backup (`BACKUP TO`), `POST /api/admin/backup`
     (loopback only), `--restore <zip>` with staging + probe + old DB kept

2. **Sprint 17 COMPLETE** - Difficulty Adjustment (2026-11-11)
   - 17a (PR #276): `Target` (256-bit numeric target, equivalent to N zeros)
   - 17b (PR #277): `ChainParams`, retarget every 10 blocks (4x clamp, powLimit,
     window skips genesis, no off-by-one); targets derived, never stored;
     every append path validates against the expected target
   - 17c (PR #278): median-time-past + future limit (**15 s**, not the planned
     2 min: the window is only ~100 s); injectable `Clock`
   - 17d (PR #279): live difficulty in status, `/api/network/difficulty`,
     dashboard countdown; `node-node-` header fixed
   - Process: PRs now use a regular merge commit; squash/rebase disabled in
     repo settings

3. **Sprint 18 COMPLETE** - Block Rules, Size & Fees (2026-11-11 to 2026-11-13)
   - 18a (PR #290): block content rules on every path (fixes critical #281:
     peers could mint coins and overspend); `isChainValid()` by replay
   - 18b (PR #291): `maxBlockBytes` 16 KB / `maxBlockTransactions` 50; mining
     fills a block to the limits, the rest stays pending
   - 18c (PR #292): `Transaction.fee` (in id/signature only when > 0), sender
     pays amount + fee, coinbase <= reward + fees, fee-rate block assembly
   - 18d (PR #293): `fee` in `POST /api/transactions`, `GET /api/mempool`,
     block size + fees in the API and dashboard

4. **Phases 1-3 COMPLETE**; Phase 4 at 75% (3 of 4 sprints)

5. **Next: Sprint 19 (Fee Market)** - not yet planned: fee estimation from
   recent blocks / the mempool, replace-by-fee, mempool size limit with
   eviction of the lowest fee rate. Builds on `feeRate()` and
   `getPendingInMiningOrder()`

6. **Deferred / future work**
   - Gossip auto-connect to DISCOVERED peers (needs MAX_PEERS guarding)
   - Self-identification filtering in peer lists
   - Mempool request is sent on outbound connect only; inbound side does not
     yet pull the peer's mempool
   - API transactions are unsigned (educational); signature-carrying submission
     would need public-key + signature fields in the request body
   - Multisig signing is a server-side convenience (the node holds the member
     keys and signs claims); a production design signs client-side and submits
     only signatures. Member keys live in memory only (lost on restart)
   - `nodeId` is random per start (not persisted with the chain)
   - `Blockchain` itself is not synchronized (network + API threads); the store is
   - WebSocket push for the dashboard (currently 4s polling)
   - Contract deploy uses `System.currentTimeMillis()` in the id seed; fine for
     uniqueness but not reproducible across a re-mine
   - Two coinbases with the same id (same miner, reward and millisecond)
     overwrite each other's row in `tx_index`

---

*Last updated: 2026-11-13 | Sprint 18 Complete (Block Rules, Size & Fees) - Phase 4 in progress (75%)*
