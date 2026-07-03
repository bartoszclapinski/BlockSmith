# 📊 BlockSmith - Current Status

> **Quick reference for current project state. Update after each sprint.**

---

## 🎯 Current Status

| Field | Value |
|-------|-------|
| **Phase** | 3 - API & Interface - In Progress |
| **Current Sprint** | 14 (Smart Contracts) - Complete |
| **Current Milestone** | 14c Complete (Contract endpoints + dashboard panel) |
| **Status** | Sprint 14 complete (14a-14c), next: Sprint 15 (Multi-sig Wallets) |

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

Phase 3: API & Interface     [███████████░░░░] 75% ← CURRENT
├── Sprint 12: REST API      ✅ COMPLETE (12a ✅, 12b ✅, 12c ✅)
├── Sprint 13: Web Dashboard ✅ COMPLETE (13a ✅, 13b ✅, 13c ✅)
├── Sprint 14: Smart Contracts ✅ COMPLETE (14a ✅, 14b ✅, 14c ✅)
└── Sprint 15: Multi-sig Wallets ⬜ ← NEXT
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
| MessageTest | 6 | ✅ |
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
| ApiReadEndpointsTest | 5 | ✅ |
| ApiTransactionEndpointsTest | 5 | ✅ |
| ApiWalletNetworkEndpointsTest | 4 | ✅ |
| ApiStaticHostingTest | 3 | ✅ |
| ApiExplorerTest | 2 | ✅ |
| ApiDashboardActionsTest | 2 | ✅ |
| ScriptVMTest | 21 | ✅ |
| ChainContractTest | 9 | ✅ |
| ApiContractsTest | 3 | ✅ |
| **Total** | **220** | ✅ |

Last test run: `mvn test` - All passing

---

## 📁 Implementation Status

### Core Classes (`com.blocksmith.core`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| Block.java | ✅ Complete | ~268 | Transactions + Merkle root |
| Blockchain.java | ✅ Complete | ~640 | Pending pool + mining + external append + orphan buffer (Sprint 10) + tx dedupe + mempool prune (Sprint 11) + contract registry (deploy/claim, derived from chain) (Sprint 14b) |
| Transaction.java | ✅ Complete | ~230 | Validation + signing + verification + optional contract locking/unlocking scripts (Sprint 14b) |
| Wallet.java | ✅ Complete | ~169 | ECDSA keys + signing |

### Utility Classes (`com.blocksmith.util`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| HashUtil.java | ✅ Complete | ~50 | SHA-256 |
| BlockchainConfig.java | ✅ Complete | ~56 | Constants |
| BlockExplorer.java | ⬜ TODO | ~14 | Sprint 7 |

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

### API Classes (`com.blocksmith.api`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| ApiServer.java | ✅ Complete | ~420 | Javalin REST API: blocks, transactions, mining, wallet, network, JSON errors (Sprint 12) + static dashboard hosting (Sprint 13a) + contract deploy/claim/inspect endpoints (Sprint 14c) |

### Contract Classes (`com.blocksmith.contract`)

| Class | Status | Lines | Notes |
|-------|--------|-------|-------|
| ScriptOp.java | ✅ Complete | ~50 | Opcode enum: PUSH/DUP/DROP/SHA256/EQUAL(VERIFY)/VERIFY/ADD/SUB/GREATER/LESS/CHECKLOCKTIME (Sprint 14a) |
| ScriptVM.java | ✅ Complete | ~200 | Stack machine; hashlock/timelock; never throws (malformed = false) (Sprint 14a) |
| Contract.java | ✅ Complete | ~100 | Contract model: locking script, amount, funder, status, derived from chain (Sprint 14b) |
| ContractStatus.java | ✅ Complete | ~12 | OPEN / CLAIMED (Sprint 14b) |

### Web Dashboard (`src/main/resources/public`)

| File | Status | Notes |
|------|--------|-------|
| index.html | ✅ Complete | Dashboard shell: network, actions, blocks sections (Sprint 13) |
| app.js | ✅ Complete | Vanilla JS explorer + actions, 4s polling, textContent-only rendering (Sprint 13b/13c) |
| style.css | ✅ Complete | Dark theme, stat tiles, block cards, action forms (Sprint 13) |

### Entry Points

| Class | Status | Notes |
|-------|--------|-------|
| BlockSmithNode.java | ✅ Complete | Runnable node: P2P Node + ApiServer + dashboard in one process (Sprint 13a). Default `mvn exec:java` target + jar main class (Sprint 14) |
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

---

## 🌳 Git Status

| Item | Value |
|------|-------|
| **Current Branch** | `master` |
| **Last Commit** | Sprint 14 complete (contract endpoints merged, PR #144) |
| **Tag** | `v1.0.0` (Phase 1) |
| **Main Branch** | `master` |

---

## ⚠️ Known Issues

_None currently._

---

## 📝 Notes for Next Session

1. **Sprint 14 COMPLETE** - Smart Contracts
   - All milestones 14a, 14b, 14c merged to master
   - `com.blocksmith.contract`: `ScriptVM` stack machine (hashlock/timelock,
     never throws), `Contract`/`ContractStatus`
   - Contracts modelled as transactions (deploy TO `CONTRACT:<id>` with a
     locking script, claim FROM it with unlocking data); registry derived from
     blocks so all nodes converge - no changes needed to `getBalance`
   - REST: `POST /api/contracts`, `GET /api/contracts[/{id}]`,
     `POST /api/contracts/{id}/claim`; dashboard Contracts panel
   - Tooling: `mvn exec:java` now runs the node + dashboard by default; jar main
     class is `BlockSmithNode`; demo via `-Dexec.mainClass=...BlockSmithDemo`

2. **Phase 3 IN PROGRESS** - API & Interface (Sprints 12-14 done, 75%)

3. **Next: Sprint 15** - Multi-sig Wallets
   - Not yet planned; M-of-N signatures, threshold signing. The Sprint 14
     script VM is a natural foundation (a CHECKMULTISIG-style opcode)

4. **Deferred / future work**
   - Gossip auto-connect to DISCOVERED peers (needs MAX_PEERS guarding)
   - Self-identification filtering in peer lists
   - Mempool request is sent on outbound connect only; inbound side does not
     yet pull the peer's mempool
   - API transactions are unsigned (educational); signature-carrying submission
     would need public-key + signature fields in the request body
   - WebSocket push for the dashboard (currently 4s polling)
   - Contract deploy uses `System.currentTimeMillis()` in the id seed; fine for
     uniqueness but not reproducible across a re-mine

---

*Last updated: 2026-07-03 | Sprint 14 Complete (Milestone 14c) - Phase 3 In Progress*
