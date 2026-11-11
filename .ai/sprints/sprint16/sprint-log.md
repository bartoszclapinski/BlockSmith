# Sprint 16: Database Persistence - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-10 |
| **Sprint End** | 2026-11-11 |

---

## Milestone 16a: Storage layer (H2 `ChainStore`) - Complete (PR #258)

- H2 2.5.252 added (embedded, pure Java; compiles and runs on Java 20).
- New package `com.blocksmith.storage`: `ChainStore` interface, `NoOpChainStore`
  (default, keeps `new Blockchain()` in memory), `H2ChainStore` (JDBC; tables
  `blocks` and `pending_tx`, schema created on open, idempotent `MERGE` saves,
  `openFile(dir)` / `openInMemory(name)`), `StorageException`.
- Blocks are stored as the same Gson JSON peers already exchange, plus a few
  lookup columns.
- **Found:** plain Gson cannot serialize a wallet-signed transaction on Java 17+
  (`JsonIOException` on the JDK's `ECPublicKeyImpl`). Added `JsonUtil.GSON` with
  a `PublicKey` adapter (hex X.509 via `SignatureUtil`).
- 8 tests (`H2ChainStoreTest`), incl. a signed transaction that still verifies
  after storage. Suite: 233 -> 241. Issues #161, #175.

---

## Milestone 16b: Persist on append + restore on startup - Complete (PR #261)

- `Blockchain(ChainStore)`; one private `appendBlock()` now serves all four
  append paths (mined, legacy data block, from peers, attached orphans). It
  saves the block **first**, then confirms its transactions and updates the
  contract registry, so a crash cannot lose a transaction.
- Mempool add/confirm mirrored to the store.
- `restoreFromStore()` replays stored blocks through `addBlock(Block)`; it stops
  at the first invalid block (never parks the rest as orphans). Pending
  transactions re-enter via `addTransaction`, skipping ones already in a block
  (prevents a double-mine after a crash between "block saved" and "pending
  cleared").
- `BlockSmithNode`: H2 under `data/node-<p2pPort>/` (`-Dblocksmith.dataDir`
  overrides), prints a restore report, closes the store on shutdown; `data/`
  git-ignored.
- **Bug #260 (found by the tamper test):** block validation only checked the
  hash and PoW, but the hash commits to the *stored* Merkle root and each tx
  keeps its *stored* id. An edited amount was accepted, from disk **and from
  peers**. Added `Transaction.hasValidId()` and `Block.hasValidMerkleRoot()`,
  required by `isWellFormed` and `isChainValid`. Contract scripts are not in the
  tx id yet -> follow-up #264.
- **Bug #259:** `Message` and `ApiServer` used plain Gson, so signed transactions
  could not be gossiped or returned by `/api/blocks`. Both use `JsonUtil.GSON`.
- Live check: mined, sent, deployed a contract, left a tx pending, then
  **hard-killed** the process; after restart everything matched (tip, balance,
  contract, pending tx).
- 12 tests (6 persist/restore, 3 block integrity, 2 message, 1 API). Suite:
  241 -> 253. Issues #187, #203, #259, #260.

---

## Milestone 16c: Indexed queries + API - Complete (PR #262)

- `tx_index` (`tx_id`, `block_idx`, `pos`, `sender`, `recipient`, `amount`) with
  B-tree indexes on sender, recipient, block_idx, written in the same DB
  transaction as its block.
- Store lookups (block by hash, tx by id, txs by address via `UNION ALL` of the
  two indexed halves); `Blockchain.getBlockByHash` / `findTransaction` /
  `getTransactionHistory`. Index answers are hints resolved against the chain,
  so stale rows (e.g. from a block rejected on restore) never leak.
- API: `GET /api/blocks/hash/{hash}`, `GET /api/wallet/{address}/transactions`;
  `GET /api/transactions/{id}` adds `status`, `blockIndex`, `confirmations`.
- 16b databases get their index from the restore replay (verified live).
- Fixed a flaky `MiningTest` (difficulty 1, nonce 0 valid ~1 in 16).
- 13 tests. Suite: 253 -> 266. Issues #214, #226.

---

## Milestone 16d: Backup & restore - Complete (PR #263)

- `ChainStore.backup(zip)`; `H2ChainStore` uses `BACKUP TO` (online snapshot;
  file databases only).
- `POST /api/admin/backup` (loopback only, 503 when disabled) writes
  `<dataDir>/backups/chain-<timestamp>.zip`.
- `BlockSmithNode ... --restore <zip>`: staging folder, exactly one
  `chain.mv.db` entry (rejects path traversal), probe-open to catch a damaged
  file, old database renamed (not deleted), then the normal replay validates.
  A bad backup exits 1 and leaves data untouched.
- Live check: backup on node A, fresh node B with `--restore` had the same tip,
  balance and history; a junk file was refused with data unchanged.
- 11 tests. Suite: 266 -> 277. Issues #237, #246.

---

## Retrospective

- Writing the restore tamper test exposed a real consensus hole (#260) that had
  existed since Sprint 4: tests that attack the system find more than tests
  that only confirm it.
- Housekeeping incidents: a script split issue bodies into lines and created 88
  junk issues (closed as not planned); a background node outlived its task and
  held ports. Both cleaned up; issue bodies are now written via `--body-file`,
  and live nodes are launched with a tracked PID.

---

*Sprint 16 Log*
