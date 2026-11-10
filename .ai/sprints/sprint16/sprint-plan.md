# Sprint 16: Database Persistence

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 16 |
| **Title** | Database Persistence |
| **Phase** | Phase 4: Production Features |
| **Status** | 📋 Planned |
| **Depends On** | Phase 3 Complete (Sprint 15) |

> **Approach (recommended):** an embedded **H2** database behind a small
> `ChainStore` interface. Blocks are stored in the **same Gson JSON the P2P
> layer already exchanges** (`BlocksMessage`, proven by `ChainSyncTest`), plus
> a few indexed columns for queries. On startup the node does **not trust the
> database**: it replays every stored block through the existing
> `Blockchain.addBlock(Block)` validation (link, hash integrity, PoW), exactly
> as if a peer had sent them. Balances and the contract registry are derived
> from the chain, so they rebuild for free.

---

## Goal

Make a node survive a restart. Today the chain, the mempool and every balance
live in memory only, so stopping the process loses everything (and a public
demo would reset on every redeploy). After this sprint a node writes each
accepted block and pending transaction to disk, restores its full state on
startup, answers indexed queries (address history, block by hash), and can be
backed up and restored.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **16a** | Storage layer (H2 `ChainStore`) | `sprint16a/chain-store` | ⬜ Planned (#161, #175) |
| **16b** | Persist on append + restore on startup | `sprint16b/persist-restore` | ⬜ Planned (#187, #203) |
| **16c** | Indexed queries + API | `sprint16c/indexed-queries` | ⬜ Planned (#214, #226) |
| **16d** | Backup & restore | `sprint16d/backup-restore` | ⬜ Planned (#237, #246) |

---

## Milestone 16a: Storage layer (H2 `ChainStore`)

### Deliverables

- [ ] Add the H2 dependency (`com.h2database:h2`, embedded, pure Java)
- [ ] New package `com.blocksmith.storage`
- [ ] `ChainStore` interface: `saveBlock`, `loadBlocks` (ordered by index),
      `savePending`, `removePending`, `loadPending`, `close`
- [ ] `NoOpChainStore`: the default; keeps today's in-memory behaviour so all
      existing tests and call sites are untouched
- [ ] `H2ChainStore`: JDBC implementation; creates its schema on first open
      (`CREATE TABLE IF NOT EXISTS`); works with both a file URL
      (`jdbc:h2:file:...`) and an in-memory URL for tests (`jdbc:h2:mem:...`)
- [ ] Schema: `blocks(idx PK, hash UNIQUE, previous_hash, json)` and
      `pending_tx(tx_id PK, json)`
- [ ] Saving the same block twice is a no-op (idempotent), so a replay or a
      re-gossiped block never duplicates rows
- [ ] Tests (in-memory H2): block round-trip incl. transactions and contract
      scripts, ordering, idempotent save, pending add/remove/load, schema
      created on a fresh database

### Why this is first

The store is pure I/O with a narrow interface. Building and testing it alone
means 16b only has to wire it into `Blockchain`.

---

## Milestone 16b: Persist on append + restore on startup

### Deliverables

- [ ] `Blockchain` gets an optional `ChainStore` (constructor overload; default
      `NoOpChainStore`)
- [ ] **One append path**: today blocks are appended in three places
      (`addBlock(String)`, `addBlock(Block)` + orphan attach,
      `minePendingTransactions`). Route them through a single private
      `appendBlock(...)` that also persists, so no path can forget the store
- [ ] Mempool persisted: added transactions are saved; confirmed/pruned ones
      are removed
- [ ] `Blockchain.restore(ChainStore)`: replays stored blocks through the
      normal validation; stops at the first invalid block and reports how many
      were accepted (a tampered database row is rejected, never trusted)
- [ ] Pending transactions re-enter through `addTransaction` (balance rules
      still apply)
- [ ] `BlockSmithNode` uses `H2ChainStore` under `data/node-<p2pPort>/` by
      default, logs "restored N blocks", and closes the store on shutdown
- [ ] Tests: restart round-trip (same tip hash, balances, contracts, mempool),
      tampered stored block rejected, restore of an empty store = genesis only,
      default `NoOpChainStore` leaves the existing suite unchanged

### Note

Genesis is deterministic (fixed timestamp), so every node and every restart
starts from the same root; restore only needs to replay blocks 1..N.

---

## Milestone 16c: Indexed queries + API

### Deliverables

- [ ] `tx_index(tx_id PK, block_idx, sender, recipient, amount)` with indexes
      on `sender` and `recipient`, filled when a block is saved
- [ ] Store queries: block by hash, transaction by id (with its block index),
      transaction history for an address (both directions, newest first)
- [ ] API:
  - `GET /api/blocks/hash/{hash}`
  - `GET /api/wallet/{address}/transactions` (history + confirmations)
  - `GET /api/transactions/{id}` also finds confirmed transactions via the index
- [ ] Without a database (`NoOpChainStore`) the same queries fall back to
      scanning the in-memory chain, so behaviour is identical, just slower
- [ ] Tests: store queries, HTTP shape of the new endpoints, 404 envelopes

---

## Milestone 16d: Backup & restore

### Deliverables

- [ ] `H2ChainStore.backup(Path)`: online backup to a zip (H2 `BACKUP TO`)
      while the node keeps running
- [ ] `POST /api/admin/backup`: writes a timestamped backup and returns its path
- [ ] Restore at startup: `BlockSmithNode ... --restore <backup.zip>` unpacks
      into the data directory, then the normal 16b replay validates it
- [ ] Tests: backup -> fresh node restore -> same tip hash and balances; a
      corrupt or missing backup fails with a clear error

---

## Theory: why replay instead of trusting the database

```
Option 1: trust the DB          load rows -> set chain = rows
          fast, but a corrupted or hand-edited row becomes "truth"

Option 2: replay (chosen)       for each stored block:
                                    blockchain.addBlock(block)   <- same checks
                                    as a block from an untrusted peer
          a bad row is rejected; derived state (balances, contracts)
          is rebuilt exactly as on any other node
```

Bitcoin Core does the same on `-reindex`: the block files on disk are data,
the validation rules are the authority.

---

## Dependencies

- **New:** `com.h2database:h2` (latest 2.x; confirm it compiles on Java 20 in 16a)
- Gson (existing): the block JSON format already used by `BlocksMessage`
- `Node(int, Blockchain)` constructor (existing) for injecting a restored chain

---

## Open decisions

- **H2** (recommended: pure Java, no native library, in-memory mode for tests)
  vs SQLite (needs a native JDBC driver)
- **JSON blob + indexed columns** (recommended: reuses the wire format, minimal
  mapping code) vs a fully normalised schema
- **Multisig member keys stay in memory** (recommended for now): persisting
  private keys in a plain database is a security smell. A multisig created via
  the dashboard therefore cannot be claimed after a restart; documented as a
  known limitation until client-side signing exists
- **Persistence on by default** for `BlockSmithNode` (recommended), off by
  default in tests and `new Blockchain()`

---

*Created: 2026-11-10 | Sprint 16 Planning*
