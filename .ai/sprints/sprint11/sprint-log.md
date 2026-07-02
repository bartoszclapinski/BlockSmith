# Sprint 11: Mempool Sync - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-01 |
| **Sprint End** | 2026-07-02 |

---

## Milestone 11a: Transaction broadcast (NEW_TRANSACTION) - Complete

- Added `Node.broadcastTransaction(Transaction)` /
  `broadcastTransaction(tx, excludeNodeId)` with sender-exclusion on relay
- NEW_TRANSACTION handler validates via `addTransaction`, adds to the mempool,
  and re-gossips only if newly accepted
- Added an id-based duplicate guard to `Blockchain.addTransaction` so a gossiped
  transaction is added (and relayed) at most once - stops relay storms
- Issues #96, #97, #98 - merged via PR #99
- Tests: `TransactionBroadcastTest` (4)

---

## Milestone 11b: Prune confirmed transactions - Complete

- Added `Blockchain.removeConfirmed(Block)` (matches by transaction id), called
  after every external append: `addBlock(Block)` and each orphan in `attachOrphans()`
- The local mine path already clears the whole pool, so this closed the
  network-appended gap
- Issues #100, #101 - merged via PR #102
- Tests: `MempoolPruneTest` (2)

---

## Milestone 11c: Mempool sync on connect (GET_MEMPOOL / MEMPOOL) - Complete

- Added `GET_MEMPOOL` / `MEMPOOL` types and `GetMempoolMessage(nodeId)` /
  `MempoolMessage(nodeId, transactions)`, registered in `MessageParser`
- GET_MEMPOOL handler serves `getPendingTransactions()`; MEMPOOL handler applies
  each received tx via `addTransaction`
- `connectToPeer` sends a `GetMempoolMessage` right after the handshake so a new
  outbound connection catches up
- Issues #103, #104, #105 - merged via PR #106
- Tests: `MempoolSyncTest` (4)

---

## Outcome

- **Sprint 11 complete** (11a-11c): transactions now propagate across the peer
  network, confirmed transactions are pruned from the mempool, and a freshly
  connected node pulls the peer's pending set
- **Phase 2 (Network Layer) complete** (Sprints 8-11)
- Test count: 155 -> 166 (+11, including the deterministic-genesis test)

---

## Notes

- Continued issue-first, milestone-per-branch workflow from Sprints 8-10
- Genesis-determinism fix (PR #94) landed before 11a so nodes share a chain root
- `NewTransactionMessage` + `NEW_TRANSACTION` already existed; the missing piece
  was the Node handler (same start as NEW_BLOCK in Sprint 10)
- Follow-up: the mempool request is sent on outbound connect only; the inbound
  side does not yet pull the peer's mempool

---

*Created: 2026-07-01 | Completed: 2026-07-02*
