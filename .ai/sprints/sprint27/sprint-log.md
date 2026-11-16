# Sprint 27: Block Explorer - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-16 |
| **Sprint End** | 2026-11-16 |

---

## Planning probe (not committed)

A chain of 61 blocks (60 mined on `TestChains.unsigned()`, one transfer in
each from the second on), 119 transactions, served by `ApiServer`:

| Asked | Got |
|-------|-----|
| `GET /api/blocks` | 61 blocks, 42,892 bytes (what the dashboard asks for every 4 s) |
| `GET /api/blocks?limit=5` | the same 61 blocks: the parameter is ignored |
| `GET /api/blocks/10` fields | `index, timestamp, data, previousHash, transactions, merkleRoot, hash, nonce, sizeBytes, totalFees`; no `confirmations`, `nextHash`, `miner`, `reward` |
| `GET /api/transactions/{id}` fields | the transaction + `status, blockIndex, confirmations`; no `blockHash`, `blockTimestamp`, `sizeBytes`, `feeRate`, `position` |
| `GET /api/wallet/Miner1` fields | `address, balance, pendingOutgoing` |
| `GET /api/wallet/Miner1/transactions` | 119 rows, 28,220 bytes; `?limit=5` gives the same 119 |
| `GET /api/search?q=` height / block hash / tx id / address | 404 each (no such route) |
| a block hash on `/api/blocks/{index}` | 400 |
| a block hash on `/api/transactions/{id}` | 404 |
| `/api/network/status` | no `totalTransactions`, `totalSupply`, `chainBytes`; `/api/stats` is 404 |
| dashboard | no search field; `app.js` never reads a block, a transaction by id or an address history; `blocks.slice(-6)` |

Also noted: mining those 60 blocks took 245 s (the difficulty retargets up
when blocks come faster than the target), so the sprint's tests keep their
chains short.

---

## Milestone 27a: Explorer queries and API - Complete (PR #393)

- `BlockExplorer` (an empty stub since Sprint 0) is the read-only query
  class: `blockPage`, `blockView`, `transactionView` / `describe`,
  `addressView`, `search`, `stats`; every answer inside
  `Blockchain.atomically`
- `GET /api/explorer/blocks?limit=&before=` (summaries, newest first, cursor
  = height, limit 1-100), `/api/explorer/address/{address}?limit=&offset=`,
  `/api/explorer/search?q=` (404 nothing, 400 empty), `/api/explorer/stats`
- A single block adds `confirmations`, `nextHash`, `miner`, `reward`,
  `transactionCount`; a transaction (and each wallet history row) adds
  `blockHash`, `blockTimestamp`, `position`, `sizeBytes`, `feeRate`.
  `GET /api/blocks` is unchanged
- `GET /api/blocks/{index}` checked the size and read the block in two
  calls; it is one snapshot now
- `stats()` keeps its sums and reads only new blocks; after a reorg it
  counts again from genesis
- `ExplorerApiTest` (8). 34 mutations, all detected. 463 tests
- One wrong expectation in a new test: after a reorg the old miner's
  transfers do not go back to the mempool, because the coins they spend no
  longer exist. The code was right

## Milestone 27b: Explorer in the dashboard - Complete (PR #394)

- Views chosen by the address bar: `#/blocks`, `#/block/<height or hash>`,
  `#/tx/<id>`, `#/address/<address>`; `onRoute` on `hashchange` and at load
- Blocks (totals, "Load older blocks" by cursor), block (previous / next),
  transaction, address (history by pages); a search field; the palette
  offers "Search the chain" for whatever is typed
- Block cards, the "all blocks" cap and the active wallet's address link in
- An open view reloads when the tip or the mempool changes and keeps the
  older pages already loaded
- The poll reads `/api/explorer/blocks?limit=6`, no longer the whole chain
- `ExplorerViewsTest` (3). 466 tests
- Live (real node, headless Edge), 44/44: cards are links; totals; 20 rows,
  then all 27, each block once; a new block appears by itself; search by
  height, hash (upper case), transaction id, address, and for nothing;
  block -> transaction -> address by clicking; back button twice; pending
  turning confirmed without a reload; a direct link; an unknown block; the
  palette; 390 px without horizontal scroll on every view; no page errors.
  First run 41/44: the three failures were wrong numbers in the check
  script (28 history rows, not 29) and a label compared without its icon
- After the first run the history table on a phone lost its direction
  column (the sign of the amount says it), so the amount fits at 390 px

## Milestone 27c: Pending and contracts, live check, docs - Complete (PR #395)

- A pending transaction: `mempoolPosition` of `mempoolSize` in the order a
  miner takes them; shown on its page
- A `CONTRACT:` address: the contract (status, kind, amount, script, funder,
  deploy block, claimer) next to its transactions
- Palette command "Explorer"; a tour step; the tour leaves an explorer view
  before it starts
- `ExplorerEndToEndTest` (4): everything mined found by search; the chain
  walked tip -> genesis by `previousHash` and back by `nextHash`; one id
  from the mempool (3 of 3) into a block. 7 mutations, all detected. 470 tests
- Live, 14/14: a contract found by id, open, then claimed without a reload;
  two pending transfers ordered by fee rate, then confirmed; palette; tour
  from an explorer view; 390 px; no page errors
- First live run 12/14 ("0 of 2" in the mempool line): the node ran from
  `target/classes` that still held the last mutant of the mutation run. The
  source was restored, the compiled class was not. Recompiled: 14/14
- Docs: ARCHITECTURE (BlockExplorer, endpoints, views), README

---

## Retrospective

- The explorer was mostly already there and unreachable: lookups by hash,
  id and address existed since Sprint 16c, and nothing in the dashboard
  called them. What was missing was the context around a row (confirmations,
  next hash, totals), paging, one search, and pages that link to each other.
- The dashboard had been downloading the whole chain every 4 seconds since
  Sprint 13. Nobody noticed, because test chains are short. The probe
  measured it (42,892 bytes for 61 blocks, growing for ever).
- A cursor instead of a page number, and "one snapshot per answer", are the
  two rules that keep an explorer honest while the chain moves under it.
- A mutation run leaves its last mutant in `target/classes`. Compile before
  anything else runs from there.
- Suite: 455 -> 470 (ExplorerApiTest 8, ExplorerViewsTest 3,
  ExplorerEndToEndTest 4).
- This was the last sprint: Sprints 0-27, 470 tests. The project is
  closed; what is knowingly left out is listed in STATUS.md.

---

*Sprint 27 Log*
