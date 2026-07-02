# Sprint 12: REST API - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-02 |
| **Sprint End** | 2026-07-02 |

---

## Milestone 12a: HTTP server bootstrap + read/query endpoints - Complete

- Added Javalin (`io.javalin:javalin` 6.3.0, embedded Jetty) - first dependency
  beyond Gson/JUnit
- `NetworkConfig.API_PORT` (7070), separate from the P2P port
- `ApiServer(Node[, port])` with `start()` / `stop()`; JSON via Gson
- Read endpoints: `GET /api/blocks`, `/blocks/{index}` (400/404), `/blocks/latest`,
  `/api/network/status`
- Issues #110, #111, #112 - merged via PR #113
- Tests: `ApiReadEndpointsTest` (5)

---

## Milestone 12b: Transaction + mining endpoints - Complete

- `POST /api/transactions` - build via constructor (so id/timestamp compute),
  add + broadcast; 201/400
- `GET /api/transactions/{id}` - find in mempool or a mined block; 404 otherwise
- `POST /api/mine` - mine pending txs, broadcast the block, return it
- Issues #114, #115, #116 - merged via PR #117
- Tests: `ApiTransactionEndpointsTest` (5)

---

## Milestone 12c: Wallet + network endpoints + error handling - Complete

- `GET /api/wallet/{address}` - balance + pending outgoing (mempool scan)
- `POST /api/wallet/create` - returns the address only (never the private key)
- `GET /api/network/peers` - connected peers
- JSON error envelope: 500 for uncaught exceptions, 404 JSON for unmatched routes
  (explicit handler 404s preserved)
- Issues #118, #119, #120 - merged via PR #121
- Tests: `ApiWalletNetworkEndpointsTest` (4)

---

## Outcome

- **Sprint 12 complete** (12a-12c): a fully usable REST API sits beside the P2P
  node - browse the chain, submit/look up transactions, mine, create wallets,
  check balances, inspect peers, all over HTTP
- Writes over HTTP propagate to peers via the existing Sprint 10/11 broadcast paths
- **Phase 3 now in progress** (25%)
- Test count: 166 -> 180 (+14)

---

## Notes

- First sprint of Phase 3 (API & Interface)
- Continued issue-first, milestone-per-branch workflow from Sprints 8-11
- Framework confirmed: Javalin (the roadmap's first-named option)
- Follow-ups: `ApiServer` is not yet wired into a runnable main/demo; API
  transactions are unsigned (educational) - signed submission would need
  public-key + signature fields in the request body

---

*Created: 2026-07-02 | Completed: 2026-07-02*
