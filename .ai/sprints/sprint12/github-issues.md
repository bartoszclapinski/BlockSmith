# Sprint 12: GitHub Issues

> Copy each issue to GitHub. Actual issue numbers are assigned on creation
> (PRs consume the same number space, so they will not be contiguous).

---

## Milestone 12a: HTTP server bootstrap + read/query endpoints

---

### Add ApiServer bootstrap + HTTP dependency

**Labels:** `sprint-12`, `milestone-12a`, `enhancement`

**Description:**

Add the HTTP framework dependency (Javalin, pending confirmation) and an
`ApiServer` that starts/stops on a configurable port, separate from the P2P
port.

- Add `NetworkConfig.API_PORT` (default 7070)
- `ApiServer(Blockchain, Node, int port)` with `start()` / `stop()`
- Wire nothing else yet - just prove the server comes up

**Acceptance Criteria:**
- [ ] Server starts and stops cleanly on the configured port
- [ ] Holds references to the node's Blockchain and Node
- [ ] Compiles with `mvn compile`

---

### Implement read/query endpoints

**Labels:** `sprint-12`, `milestone-12a`, `enhancement`

**Description:**

- `GET /api/blocks` - full chain as JSON
- `GET /api/blocks/{index}` - block by index (404 if out of range)
- `GET /api/blocks/latest` - current tip
- `GET /api/network/status` - chain length, pending count, peer count, node id

**Acceptance Criteria:**
- [ ] All four endpoints return correct JSON and status codes
- [ ] Out-of-range block index returns 404
- [ ] Compiles with `mvn compile`

---

### Unit tests for read endpoints

**Labels:** `sprint-12`, `milestone-12a`, `test`

**Description:**

Drive the running `ApiServer` with an HTTP client (java.net.http.HttpClient)
against a node on an ephemeral port.

**Test Cases:**
1. `getBlocks_returnsChain`
2. `getBlockByIndex_returnsBlock` / `getBlockByIndex_404WhenMissing`
3. `getLatest_returnsTip`
4. `getStatus_returnsCounts`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 12b: Transaction + mining endpoints

---

### Implement POST /api/transactions and GET /api/transactions/{id}

**Labels:** `sprint-12`, `milestone-12b`, `enhancement`

**Description:**

- `POST /api/transactions` - parse a JSON transaction, add via
  `Blockchain.addTransaction`; 201 on success, 400 on rejection; broadcast via
  `Node.broadcastTransaction`
- `GET /api/transactions/{id}` - find a tx by id in the pending pool or in a
  mined block; 404 if unknown

**Acceptance Criteria:**
- [ ] Valid tx accepted (201) and broadcast; invalid rejected (400)
- [ ] Lookup finds pending and confirmed txs; 404 otherwise
- [ ] Compiles with `mvn compile`

---

### Implement POST /api/mine

**Labels:** `sprint-12`, `milestone-12b`, `enhancement`

**Description:**

`POST /api/mine` with a miner address - mine pending transactions
(`minePendingTransactions`), broadcast the new block via `Node.broadcastBlock`,
return the mined block.

**Acceptance Criteria:**
- [ ] Mining produces a block and returns it as JSON
- [ ] New block is broadcast to peers
- [ ] Compiles with `mvn compile`

---

### Unit tests for transaction + mining endpoints

**Labels:** `sprint-12`, `milestone-12b`, `test`

**Description:**

**Test Cases:**
1. `postTransaction_acceptsValid` / `postTransaction_rejectsInvalid`
2. `getTransactionById_findsPendingAndConfirmed`
3. `postMine_producesBlock`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 12c: Wallet + network endpoints + error handling

---

### Implement wallet + network endpoints

**Labels:** `sprint-12`, `milestone-12c`, `enhancement`

**Description:**

- `GET /api/wallet/{address}` - balance + pending outgoing
- `POST /api/wallet/create` - generate a `Wallet`, return the address only
  (document that a real node never returns private keys)
- `GET /api/network/peers` - connected peers (address, nodeId, state)

**Acceptance Criteria:**
- [ ] Wallet balance and create endpoints work
- [ ] Peers endpoint lists connected peers
- [ ] Compiles with `mvn compile`

---

### Add JSON error handling

**Labels:** `sprint-12`, `milestone-12c`, `enhancement`

**Description:**

Consistent error envelope (`{ "error": "..." }`) and status codes for bad
input (400), not-found (404), and server errors (500).

**Acceptance Criteria:**
- [ ] Malformed/invalid requests return a JSON error + correct status
- [ ] Unknown routes/resources return 404 JSON
- [ ] Compiles with `mvn compile`

---

### Unit tests for wallet, network, and errors

**Labels:** `sprint-12`, `milestone-12c`, `test`

**Description:**

**Test Cases:**
1. `getWalletBalance_returnsBalance`
2. `postWalletCreate_returnsAddress`
3. `getPeers_returnsConnectedPeers`
4. `badRequest_returnsJsonError`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Summary

| Milestone | Issues | Tests |
|-----------|--------|-------|
| 12a: Bootstrap + read endpoints | 3 | 4 tests |
| 12b: Transaction + mining | 3 | 3 tests |
| 12c: Wallet + network + errors | 3 | 4 tests |
| **Total** | **9 issues** | **~11 tests** |

---

*Created: 2026-07-02 | Sprint 12 Planning*
