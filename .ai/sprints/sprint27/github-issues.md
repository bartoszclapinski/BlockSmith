# Sprint 27: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 27a: Explorer queries and API

---

### Explorer queries: paged blocks, block and transaction context, address view, search, chain totals

**Labels:** `sprint-27`, `milestone-27a`, `enhancement`

**Description:**

Measured on master (61 blocks, 119 transactions): `GET /api/blocks` returns
the whole chain (42,892 bytes) and ignores `?limit=5`; there is no search
(`/api/search` is 404; a block hash on `/api/blocks/{index}` is 400); a
block's JSON has no confirmations, next hash, miner or reward; the address
history returns every row and ignores `?limit`; there are no chain totals.
`BlockExplorer.java` is an empty stub.

**Add:**
- `BlockExplorer`: read-only queries over a `Blockchain`, each from one
  snapshot
- `GET /api/explorer/blocks?limit=&before=`: summaries, newest first, with a
  cursor (`limit` 1-100)
- `GET /api/blocks/{index}`, `/api/blocks/hash/{hash}`: `confirmations`,
  `nextHash`, `miner`, `reward`, `transactionCount`
- `GET /api/transactions/{id}`: `blockHash`, `blockTimestamp`, `position`,
  `sizeBytes`, `feeRate`
- `GET /api/explorer/address/{address}?limit=&offset=`: balance, pending
  outgoing, count, total received, total sent, one page of history
- `GET /api/explorer/search?q=`: height, block hash, transaction id,
  contract id or address -> `{type, ...}`; 404 for nothing, 400 for empty
- `GET /api/explorer/stats`: height, transactions, coins issued, chain
  bytes, open contracts

**Acceptance Criteria:**
- [ ] Existing endpoints only gain fields; `/api/blocks` is unchanged
- [ ] Compiles with `mvn compile`

---

### Tests for the explorer queries and API

**Labels:** `sprint-27`, `milestone-27a`, `test`

**Description:**

**Test Cases:**
1. `blockPages_walkEveryBlockOnce` (cursor paging, newest first, limit bounds)
2. `blockView_confirmationsNextHashMinerReward`
3. `transactionView_blockContextSizeAndFeeRate`
4. `addressView_totalsMatchBalance_andHistoryPages`
5. `search_findsEachKind_and404ForNothing`
6. `stats_countTransactionsAndCoins`
7. `badParameters_are400`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 27b: Explorer in the dashboard

---

### Explorer views in the dashboard: blocks, block, transaction, address, search

**Labels:** `sprint-27`, `milestone-27b`, `enhancement`

**Description:**

The dashboard shows the last 6 blocks as cards that cannot be opened, has no
search, and downloads the whole chain every 4 seconds.

- Views with their own links: `#/blocks`, `#/block/<height>`, `#/tx/<id>`,
  `#/address/<address>`; back button and direct links work
- Blocks: chain totals and a list that loads older blocks
- Block: header with previous / next links, its transactions
- Transaction: status, confirmations, block, from, to, amount, fee, fee
  rate, size
- Address: balance, received, sent, count, paged history
- Search field (height / hash / transaction id / address), also from the
  command palette
- Block cards and the active wallet's address link into the explorer
- Polling uses `/api/explorer/blocks?limit=6`

**Acceptance Criteria:**
- [ ] A block, a transaction and an address can each be reached by search
      and by a direct link
- [ ] No horizontal scroll at 390 px; light and dark themes
- [ ] Compiles with `mvn compile`

---

### Tests for the explorer views

**Labels:** `sprint-27`, `milestone-27b`, `test`

**Description:**

**Test Cases:**
1. The served page has the explorer mount points and the search field
2. The served script has the four routes, reads each explorer endpoint and
   handles `hashchange`
3. The script no longer asks for `/api/blocks` without a limit
4. Live browser check (results in the sprint log)

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 27c: Pending and contracts, live check, docs

---

### Explorer: pending transactions and contracts, palette and tour, docs

**Labels:** `sprint-27`, `milestone-27c`, `enhancement`

**Description:**

- A pending transaction's page: 0 confirmations and its place in the mempool
  by fee rate
- A contract address shows the contract (status, amount, script, funder,
  claimer) with its transactions
- Palette command and tour step for the explorer
- Docs: ARCHITECTURE (explorer), STATUS, README, roadmap, project site

**Acceptance Criteria:**
- [ ] A pending transaction and a contract can be opened from search
- [ ] Compiles with `mvn compile`

---

### Explorer end-to-end checks

**Labels:** `sprint-27`, `milestone-27c`, `test`

**Description:**

**Test Cases:**
1. `everythingMined_canBeFoundBySearch` (block, transfer, deploy, address,
   contract)
2. `chainWalk_tipToGenesisAndBack` (`previousHash` down, `nextHash` up)
3. `pendingTransaction_thenConfirmed` (the same id before and after mining)
4. Live: a real node driven in a browser, results in the sprint log

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-16 | Sprint 27 Planning*
