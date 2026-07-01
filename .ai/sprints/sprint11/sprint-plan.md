# Sprint 11: Mempool Sync

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 11 |
| **Title** | Mempool Sync |
| **Phase** | Phase 2: Network Layer |
| **Status** | Planning |
| **Depends On** | Sprint 10 Complete (Block Broadcasting) |

---

## Goal

Give the network a shared view of *pending* transactions. When a node
receives a transaction it gossips it to every peer; receivers validate it,
add it to their mempool, and re-broadcast. When a block is appended, its
transactions are pruned from the mempool so they are never re-mined or
re-gossiped. A freshly connected node pulls the current mempool from a peer
so it can start mining with the same pending set. By the end of this sprint
a transaction submitted anywhere propagates to every node, and confirmed
transactions cleanly leave the pool.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **11a** | Transaction broadcast (NEW_TRANSACTION) | `sprint11a/tx-broadcast` | Pending |
| **11b** | Prune confirmed transactions from the mempool | `sprint11b/mempool-prune` | Pending |
| **11c** | Mempool sync on connect (GET_MEMPOOL / MEMPOOL) | `sprint11c/mempool-sync` | Pending |

---

## Milestone 11a: Transaction broadcast (NEW_TRANSACTION)

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| TBD | Add Node.broadcastTransaction(Transaction) | Pending |
| TBD | Implement NEW_TRANSACTION handler (validate, add, re-gossip) | Pending |
| TBD | Unit tests for transaction broadcast | Pending |

### Deliverables

- [ ] `Node.broadcastTransaction(Transaction)` /
      `broadcastTransaction(tx, excludeNodeId)` sends a `NewTransactionMessage`
      to all connected peers (sender-exclusion on relay, like `broadcastBlock`)
- [ ] NEW_TRANSACTION handler: validate + add via `Blockchain.addTransaction`,
      and re-gossip only if newly accepted (duplicates/invalid return false ->
      no relay storm)
- [ ] Unit tests: parser round-trip, accepted tx re-gossips, duplicate ignored,
      invalid tx rejected

### Why this is first

`NewTransactionMessage` and the `NEW_TRANSACTION` type already exist and are
registered in `MessageParser`, but there is **no handler in Node** - the same
starting point NEW_BLOCK had in Sprint 10. Broadcast is the smallest useful
increment and mirrors the block-broadcast code directly.

---

## Milestone 11b: Prune confirmed transactions from the mempool

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| TBD | Remove a block's transactions from the pending pool on append | Pending |
| TBD | Unit tests for mempool pruning | Pending |

### Deliverables

- [ ] `Blockchain.addBlock(Block)` (external append) and the local mine path
      remove the block's transactions from `pendingTransactions`
- [ ] Pruning matches on transaction id so only confirmed txs are dropped
- [ ] Unit tests: confirmed txs leave the pool; unrelated pending txs remain

### Why this matters

Without pruning, a transaction that has already been mined stays in the pool
and gets re-broadcast forever (and could be mined again into a later block).
This is a real gap today and underpins the correctness of 11a's gossip.

---

## Milestone 11c: Mempool sync on connect (GET_MEMPOOL / MEMPOOL)

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| TBD | Add GET_MEMPOOL / MEMPOOL types + GetMempoolMessage / MempoolMessage | Pending |
| TBD | Implement GET_MEMPOOL / MEMPOOL handlers + request on connect | Pending |
| TBD | Unit tests for mempool sync | Pending |

### Deliverables

- [ ] `MessageType.GET_MEMPOOL`, `MessageType.MEMPOOL` enum values
- [ ] `GetMempoolMessage(nodeId)` and `MempoolMessage(nodeId, List<Transaction>)`,
      registered in `MessageParser` (mirrors GET_BLOCKS / BLOCKS)
- [ ] GET_MEMPOOL handler serves `getPendingTransactions()`; MEMPOOL handler
      applies each via `addTransaction`
- [ ] A node requests the mempool after the HELLO handshake so a fresh node
      catches up on pending transactions
- [ ] Unit tests: message round-trips, handler serves the pool, received pool
      is applied

---

## Theory: Transaction Propagation and the Mempool

```
THEORY: How a transaction reaches every node's mempool

PROBLEM:
- A user submits a transaction to one node. Every miner must learn about it
  so it can be included in the next block, no matter who mines it.

BITCOIN'S APPROACH:
1. Node relays "inv" announcing the new tx id
2. Peers that don't have it reply "getdata"
3. Node sends the full "tx"
4. Each receiver validates (signature, funds, no double-spend) and relays on
5. When a block confirms the tx, every node removes it from its mempool

OUR SIMPLIFIED MODEL:
1. Node calls broadcastTransaction() -> NEW_TRANSACTION to all peers (full tx)
2. Receiver validates and adds via Blockchain.addTransaction(tx)
3. If newly accepted, receiver re-gossips to its other peers
4. When a block is appended, its txs are pruned from the pool (11b)
5. Freshly connected? GET_MEMPOOL / MEMPOOL to fetch the current pending set

DOUBLE-SPEND & VALIDATION:
- addTransaction already checks signature, balance, and rejects COINBASE
  from users (Sprints 5-6), so the gossip path reuses that validation
```

---

## Dependencies

- Sprint 10 Complete (broadcast fan-out, sender-exclusion, handler registry,
  deterministic genesis so nodes share a chain root)
- Existing MessageType: NEW_TRANSACTION (registered in MessageParser)
- Existing message class: NewTransactionMessage (carries a full Transaction)
- Core: Blockchain.addTransaction(), getPendingTransactions(), Transaction
  validation/signature verification

---

*Created: 2026-07-01 | Sprint 11 Planning*
