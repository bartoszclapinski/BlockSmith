# Sprint 12: REST API

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 12 |
| **Title** | REST API |
| **Phase** | Phase 3: API & Interface |
| **Status** | Planning |
| **Depends On** | Phase 2 Complete (Sprints 8-11) |

> **Framework decision (pending final confirmation):** **Javalin** (the roadmap's
> first-named option). Lightweight, Jetty-backed, clean routing, easy JSON via
> Gson. Adds one Maven dependency (`io.javalin:javalin`). This can still be
> swapped for the built-in `com.sun.net.httpserver` (zero-dependency) or Spark
> before 12a implementation begins.

---

## Goal

Expose the blockchain node over HTTP so it can be driven without writing Java.
A running node serves a REST API: browse blocks, submit and look up
transactions, trigger mining, create wallets and check balances, and inspect
network state. This is the first step of Phase 3 and the foundation the web
dashboard (Sprint 13) will build on.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **12a** | HTTP server bootstrap + read/query endpoints | `sprint12a/api-bootstrap` | Pending |
| **12b** | Transaction + mining endpoints | `sprint12b/api-transactions` | Pending |
| **12c** | Wallet + network endpoints + error handling | `sprint12c/api-wallet-network` | Pending |

---

## Milestone 12a: HTTP server bootstrap + read/query endpoints

### Deliverables

- [ ] Add the HTTP dependency and an `ApiServer` class that starts/stops on a
      configurable port (e.g. `NetworkConfig.API_PORT`, default 7070, separate
      from the P2P port)
- [ ] `ApiServer` holds a reference to the node's `Blockchain` (and `Node`)
- [ ] Read endpoints:
  - `GET /api/blocks` - full chain
  - `GET /api/blocks/{index}` - block by index (404 if out of range)
  - `GET /api/blocks/latest` - current tip
  - `GET /api/network/status` - chain length, pending count, peer count, node id
- [ ] JSON responses via Gson
- [ ] Tests: server starts; each endpoint returns the expected JSON / status code

### Why this is first

The server bootstrap + read endpoints are the foundation: they prove the HTTP
layer works end-to-end against the existing `Blockchain` before any state-
changing endpoints are added.

---

## Milestone 12b: Transaction + mining endpoints

### Deliverables

- [ ] `POST /api/transactions` - accept a JSON transaction, validate + add via
      `Blockchain.addTransaction`; 201 on success, 400 on rejection. Broadcast
      it to peers via `Node.broadcastTransaction`
- [ ] `GET /api/transactions/{id}` - look up a transaction by id in the pending
      pool and in mined blocks; 404 if unknown
- [ ] `POST /api/mine` - mine pending transactions to a miner address
      (`minePendingTransactions`), broadcast the new block via
      `Node.broadcastBlock`; return the mined block
- [ ] Tests: submit tx (accepted/rejected), look up tx, mine produces a block

### Notes

- Reuses existing validation and the Sprint 10/11 broadcast paths, so API
  submissions propagate across the network exactly like gossiped ones.

---

## Milestone 12c: Wallet + network endpoints + error handling

### Deliverables

- [ ] `GET /api/wallet/{address}` - balance + pending outgoing for an address
- [ ] `POST /api/wallet/create` - generate a new `Wallet`, return address
      (never return the private key in a real system - note this in THEORY
      comment; for the educational demo, document the choice explicitly)
- [ ] `GET /api/network/peers` - connected peers (address, nodeId, state)
- [ ] Consistent JSON error envelope (`{ "error": "..." }`) + status codes for
      bad input, not-found, and server errors
- [ ] Tests: wallet balance, wallet create, peers list, error responses

---

## Theory: Why a REST API on top of a P2P node

```
A blockchain node has two very different audiences:

1. OTHER NODES  -> talk the P2P gossip protocol (TCP, our Message types)
2. USERS/TOOLS  -> talk HTTP/JSON (browsers, curl, the Sprint 13 dashboard)

The REST API is the SECOND interface. It does NOT replace the P2P layer - it
sits beside it and drives the SAME Blockchain/Node:

  submit tx   -> POST /api/transactions -> addTransaction -> broadcast to peers
  mine        -> POST /api/mine         -> minePendingTransactions -> broadcast
  read chain  -> GET  /api/blocks       -> blockchain.getChain()

So an action taken over HTTP on one node propagates through the P2P network to
every other node - the API is just a human-friendly front door.
```

---

## Dependencies

- Phase 2 complete: `Blockchain`, `Node` (broadcastTransaction / broadcastBlock),
  `Wallet`, `Transaction`, `PeerManager`
- Gson (already a dependency) for JSON
- HTTP framework: Javalin (pending confirmation)

---

## Open decisions

- **HTTP framework**: Javalin (recommended) vs built-in `HttpServer` (zero-dep)
  vs Spark - confirm before 12a
- **API port**: default 7070 (distinct from P2P `DEFAULT_PORT`)
- **Wallet key handling**: educational demo returns address only; document that
  a real node never exposes private keys over the API

---

*Created: 2026-07-02 | Sprint 12 Planning*
