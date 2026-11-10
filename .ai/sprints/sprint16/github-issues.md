# Sprint 16: GitHub Issues

> Copy each issue to GitHub. Actual issue numbers are assigned on creation
> (PRs consume the same number space, so they will not be contiguous).

---

## Milestone 16a: Storage layer (H2 `ChainStore`)

---

### ChainStore interface + H2 implementation

**Labels:** `sprint-16`, `milestone-16a`, `enhancement`

**Description:**

- Add `com.h2database:h2` (embedded, pure Java)
- `com.blocksmith.storage.ChainStore`: `saveBlock`, `loadBlocks`,
  `savePending`, `removePending`, `loadPending`, `close`
- `NoOpChainStore` (default, in-memory behaviour unchanged)
- `H2ChainStore` (JDBC): schema created on open; file and in-memory URLs
- Tables `blocks(idx PK, hash UNIQUE, previous_hash, json)` and
  `pending_tx(tx_id PK, json)`; blocks stored in the Gson format peers exchange
- Saving the same block twice is a no-op

**Acceptance Criteria:**
- [ ] A saved block loads back equal (hash, transactions, scripts)
- [ ] `loadBlocks` returns blocks ordered by index
- [ ] Re-saving a block does not duplicate it
- [ ] Compiles with `mvn compile` on Java 20

---

### Tests for the chain store

**Labels:** `sprint-16`, `milestone-16a`, `test`

**Description:**

**Test Cases:**
1. `saveAndLoad_blockRoundTripsWithTransactions`
2. `loadBlocks_orderedByIndex`
3. `saveBlock_twice_isIdempotent`
4. `pending_addRemoveLoad`
5. `freshDatabase_createsSchema`
6. Contract locking/unlocking scripts survive the round trip

**Acceptance Criteria:**
- [ ] Tests added and passing (in-memory H2)
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 16b: Persist on append + restore on startup

---

### Persist blocks and mempool; restore by replay

**Labels:** `sprint-16`, `milestone-16b`, `enhancement`

**Description:**

- `Blockchain(ChainStore)` overload; default `NoOpChainStore`
- Single private `appendBlock(...)` used by `addBlock(String)`,
  `addBlock(Block)` (incl. orphan attach) and `minePendingTransactions`;
  it persists the block
- Mempool add/prune mirrored to the store
- `Blockchain.restore(ChainStore)`: replay stored blocks through
  `addBlock(Block)` validation, stop at the first invalid one, report the count;
  pending transactions re-enter via `addTransaction`
- `BlockSmithNode`: `H2ChainStore` under `data/node-<p2pPort>/`, log
  "restored N blocks", close on shutdown

**Acceptance Criteria:**
- [ ] Restarting a node restores the same tip hash, balances, contracts and mempool
- [ ] A tampered stored block is rejected on restore
- [ ] Existing tests unchanged (default store is a no-op)
- [ ] Compiles with `mvn compile`

---

### Tests for persist + restore

**Labels:** `sprint-16`, `milestone-16b`, `test`

**Description:**

**Test Cases:**
1. `restart_restoresTipBalancesAndContracts`
2. `restart_restoresMempool`
3. `restore_tamperedBlock_rejectedAndStops`
4. `restore_emptyStore_genesisOnly`
5. `externalAndOrphanBlocks_arePersisted`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 16c: Indexed queries + API

---

### Indexed queries and history endpoints

**Labels:** `sprint-16`, `milestone-16c`, `enhancement`

**Description:**

- `tx_index(tx_id PK, block_idx, sender, recipient, amount)`, indexed on
  `sender` and `recipient`, filled on block save
- Store queries: block by hash, tx by id (+ block index), address history
- `GET /api/blocks/hash/{hash}`, `GET /api/wallet/{address}/transactions`;
  `GET /api/transactions/{id}` also finds confirmed transactions
- `NoOpChainStore` falls back to scanning the in-memory chain

**Acceptance Criteria:**
- [ ] Address history returns sent and received transactions, newest first,
      with confirmations
- [ ] Unknown hash / id returns the standard 404 envelope
- [ ] Compiles with `mvn compile`

---

### Tests for indexed queries

**Labels:** `sprint-16`, `milestone-16c`, `test`

**Description:**

**Test Cases:**
1. `blockByHash_found_andMissing`
2. `addressHistory_bothDirections_newestFirst`
3. `txById_confirmedViaIndex`
4. HTTP: history + block-by-hash shape, 404 envelopes
5. Fallback without a database gives the same answers

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 16d: Backup & restore

---

### Online backup + restore at startup

**Labels:** `sprint-16`, `milestone-16d`, `enhancement`

**Description:**

- `H2ChainStore.backup(Path)` via H2 `BACKUP TO` (node keeps running)
- `POST /api/admin/backup` returns the backup path
- `BlockSmithNode ... --restore <backup.zip>`: unpack into the data dir, then
  the normal replay validates it

**Acceptance Criteria:**
- [ ] Backup then restore on a fresh node gives the same tip hash and balances
- [ ] Corrupt / missing backup fails with a clear error
- [ ] Compiles with `mvn compile`

---

### Tests for backup + restore

**Labels:** `sprint-16`, `milestone-16d`, `test`

**Description:**

**Test Cases:**
1. `backup_thenRestore_sameTipAndBalances`
2. `backup_whileRunning_succeeds`
3. `restore_missingFile_clearError`
4. `restore_corruptZip_clearError`
5. HTTP: `POST /api/admin/backup` returns a path that exists

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

*Created: 2026-11-10 | Sprint 16 Planning*
