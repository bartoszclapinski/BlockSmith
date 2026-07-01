# Sprint 11: GitHub Issues

> Copy each issue to GitHub. Actual issue numbers are assigned on creation
> (PRs consume the same number space, so they will not be contiguous).

---

## Milestone 11a: Transaction broadcast (NEW_TRANSACTION)

---

### Add Node.broadcastTransaction(Transaction)

**Labels:** `sprint-11`, `milestone-11a`, `enhancement`

**Description:**

Send a received/submitted transaction to every connected peer, mirroring
`broadcastBlock`.

**New methods in Node.java:**
```java
public void broadcastTransaction(Transaction tx)
public void broadcastTransaction(Transaction tx, String excludeNodeId)
```
- Build a `NewTransactionMessage(nodeId, tx)`
- Write its JSON to every connected peer's writer
- Skip `excludeNodeId` so a relayed tx is not sent back to its sender

**Acceptance Criteria:**
- [ ] Sends NEW_TRANSACTION to all connected peers
- [ ] Sender-exclusion on relay
- [ ] Compiles with `mvn compile`

---

### Implement NEW_TRANSACTION handler (validate, add, re-gossip)

**Labels:** `sprint-11`, `milestone-11a`, `enhancement`

**Description:**

Register a NEW_TRANSACTION handler in `registerDefaultHandlers()`.

**Handler flow:**
- Extract the Transaction from `NewTransactionMessage`
- Add via `blockchain.addTransaction(tx)` (validates signature, balance,
  rejects COINBASE from users)
- If newly accepted -> `broadcastTransaction(tx, sender)` to other peers
- Duplicate/invalid tx returns false -> not relayed (no gossip storm)

**Acceptance Criteria:**
- [ ] NEW_TRANSACTION handler registered
- [ ] Valid new tx added and re-gossiped
- [ ] Duplicate/invalid tx ignored
- [ ] Compiles with `mvn compile`

---

### Unit tests for transaction broadcast

**Labels:** `sprint-11`, `milestone-11a`, `test`

**Description:**

Reflection-driven handler tests with an in-memory MessageContext, like the
Sprint 10 broadcast tests.

**Test Cases:**
1. `newTransactionMessage_roundTripsThroughParser` - serialization
2. `newTransactionHandler_addsValidTransaction` - pool grows by one
3. `newTransactionHandler_ignoresDuplicate` - known tx not re-added/relayed
4. `newTransactionHandler_rejectsInvalidTransaction` - bad tx not added

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 11b: Prune confirmed transactions

---

### Remove a block's transactions from the pending pool on append

**Labels:** `sprint-11`, `milestone-11b`, `enhancement`

**Description:**

When a block is appended, drop its transactions from `pendingTransactions`
so they are never re-mined or re-gossiped.

**Implementation:**
- Add a helper (e.g. `removeConfirmed(Block)`) called from `addBlock(Block)`
  and the local mine path
- Match on transaction id so only confirmed txs are removed

**Acceptance Criteria:**
- [ ] Confirmed transactions leave the pending pool
- [ ] Unrelated pending transactions remain
- [ ] Compiles with `mvn compile`

---

### Unit tests for mempool pruning

**Labels:** `sprint-11`, `milestone-11b`, `test`

**Description:**

**Test Cases:**
1. `appendingBlock_prunesConfirmedTransactions` - confirmed txs removed
2. `appendingBlock_keepsUnrelatedPending` - other pending txs untouched

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 11c: Mempool sync on connect (GET_MEMPOOL / MEMPOOL)

---

### Add GET_MEMPOOL / MEMPOOL types + message classes

**Labels:** `sprint-11`, `milestone-11c`, `enhancement`

**Description:**

Add request/response messages for fetching a peer's pending transactions.

- `MessageType.GET_MEMPOOL`, `MessageType.MEMPOOL` enum values
- `GetMempoolMessage(nodeId)`
- `MempoolMessage(nodeId, List<Transaction>)`
- Register both in `MessageParser.TYPE_REGISTRY`

**Acceptance Criteria:**
- [ ] Both message classes + enum values + parser registration
- [ ] Round-trips through MessageParser
- [ ] Compiles with `mvn compile`

---

### Implement GET_MEMPOOL / MEMPOOL handlers + request on connect

**Labels:** `sprint-11`, `milestone-11c`, `enhancement`

**Description:**

- GET_MEMPOOL handler: reply with a `MempoolMessage` containing
  `getPendingTransactions()`
- MEMPOOL handler: add each received transaction via `addTransaction`
- After the HELLO handshake, send `GetMempoolMessage` so a fresh node catches
  up on the current pending set

**Acceptance Criteria:**
- [ ] GET_MEMPOOL serves the pending pool
- [ ] MEMPOOL applies received transactions
- [ ] New connection triggers a mempool request
- [ ] Compiles with `mvn compile`

---

### Unit tests for mempool sync

**Labels:** `sprint-11`, `milestone-11c`, `test`

**Description:**

**Test Cases:**
1. `getMempoolMessage_roundTrips` / `mempoolMessage_roundTrips` - serialization
2. `getMempoolHandler_servesPendingPool` - correct pool returned
3. `mempoolHandler_appliesReceivedTransactions` - pool catches up

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Summary

| Milestone | Issues | Tests |
|-----------|--------|-------|
| 11a: Transaction broadcast | 3 | 4 tests |
| 11b: Mempool pruning | 2 | 2 tests |
| 11c: Mempool sync | 3 | 3 tests |
| **Total** | **8 issues** | **~9 tests** |

---

*Created: 2026-07-01 | Sprint 11 Planning*
