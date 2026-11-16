# Sprint 27: Block Explorer

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 27 |
| **Title** | Block Explorer |
| **Phase** | Hardening (after the roadmap); the last sprint of the project |
| **Status** | ✅ Complete (2026-11-16) |
| **Depends On** | Sprint 12 (REST API), Sprint 13 (dashboard), Sprint 16c (transaction index), Sprint 26 (release v4.7.0) |

> **Why:** the roadmap still lists "Blockchain Explorer Web App" as optional,
> `BlockExplorer.java` has been an empty stub since Sprint 0, and the
> dashboard shows the last 6 blocks as cards that cannot be opened. A probe on
> master (not committed), a chain of 61 blocks with 119 transactions:
>
> 1. **Nothing can be looked up from the dashboard.** `app.js` never reads a
>    block, a transaction by id or an address history; there is no search
>    field. The API to do it exists since Sprint 16c and nothing uses it.
> 2. **The dashboard downloads the whole chain every 4 seconds.**
>    `GET /api/blocks` returned 61 blocks, 42,892 bytes; `?limit=5` returned
>    the same 61 blocks (the parameter is ignored). The page shows 6 of them.
>    The cost grows with every block, for ever.
> 3. **No search.** `GET /api/search?q=...` is 404 for a height, a block
>    hash, a transaction id and an address. A block hash on
>    `/api/blocks/{index}` is 400, on `/api/transactions/{id}` 404: the
>    caller must already know what kind of thing a string is.
> 4. **A block does not say where it sits.** Its JSON has no confirmations,
>    no next hash, no miner, no reward. A transaction has `blockIndex` but no
>    block hash, no block time, no size, no fee rate.
> 5. **An address page cannot be built.** `/api/wallet/{address}` gives the
>    balance and pending outgoing only (no totals, no count), and the history
>    returned all 119 rows, 28,220 bytes, with `?limit=5` ignored.
> 6. **No chain totals**: no transaction count, no coins issued, no chain size.

---

## Goal

Anyone with the dashboard open can answer "what is in this block", "what
happened to this transaction" and "what did this address do", starting from
a search field or from any block, id or address shown on the page, and each
of these answers has a link that can be shared. The dashboard stops
downloading the whole chain.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **27a** | Explorer queries and API | `sprint27a/explorer-api` | ✅ PR #393 (#386, #387) |
| **27b** | Explorer in the dashboard | `sprint27b/explorer-ui` | ✅ PR #394 (#388, #389) |
| **27c** | Pending and contracts, live check, docs | `sprint27c/explorer-live` | ✅ PR #395 (#390, #391) |

---

## Milestone 27a: Explorer queries and API

### Deliverables

- [x] `BlockExplorer` (the stub since Sprint 0) becomes the read-only query
      class over a `Blockchain`: block page, block view, transaction view,
      address view, search, chain totals. Each answer is built from one
      snapshot of the chain (`atomically`)
- [x] `GET /api/explorer/blocks?limit=20&before=<height>`: block summaries,
      newest first, without their transactions (index, hash, time, number of
      transactions, size, fees, miner, reward); `limit` 1-100; the reply
      carries the height and the cursor for the next page
- [x] `GET /api/blocks/{index}` and `/api/blocks/hash/{hash}` add
      `confirmations`, `nextHash`, `miner`, `reward`, `transactionCount`;
      `GET /api/transactions/{id}` adds `blockHash`, `blockTimestamp`,
      `position`, `sizeBytes`, `feeRate` (additions only: nothing existing
      changes)
- [x] `GET /api/explorer/address/{address}?limit=25&offset=0`: balance,
      pending outgoing, number of transactions, total received, total sent,
      and one page of the history
- [x] `GET /api/explorer/search?q=`: a height, a block hash, a transaction
      id, a contract id or an address, answered with `{type, ...}` (the
      thing to open); 404 when nothing matches, 400 for an empty query
- [x] `GET /api/explorer/stats`: height, transactions, coins issued, chain
      size in bytes, open contracts (recomputed only when the tip changes)
- [x] Tests: paging walks every block exactly once; the block and
      transaction additions; address totals equal the balance; search for
      each kind and for nothing; bad parameters are 400

---

## Milestone 27b: Explorer in the dashboard

### Deliverables

- [x] Views inside the dashboard, each with its own link (`#/blocks`,
      `#/block/12`, `#/tx/<id>`, `#/address/<address>`); the browser's back
      button works; opening a link directly shows that view
- [x] Blocks: the totals from `/api/explorer/stats` and a list that loads
      older blocks on request
- [x] Block: header (hash, previous and next as links, Merkle root, nonce,
      time, size, fees, miner, reward, confirmations) and its transactions
- [x] Transaction: status and confirmations, block (link), from and to
      (links), amount, fee, fee rate, size
- [x] Address: balance, received, sent, count, history by pages
- [x] A search field (height / hash / transaction id / address); the same
      search from the command palette
- [x] Everything already on the page links in: block cards, the active
      wallet's address
- [x] The dashboard polls `/api/explorer/blocks?limit=6` instead of the whole
      chain
- [x] Tests: the served page and script have the views, the routes and the
      search; nothing asks for `/api/blocks` without a limit

---

## Milestone 27c: Pending and contracts, live check, docs

### Deliverables

- [x] A pending transaction's page says so (0 confirmations, its place in
      the mempool by fee rate); a contract address shows the contract
      (status, amount, script, funder, claimer) next to its transactions
- [x] A palette command and a tour step for the explorer
- [x] End-to-end test over HTTP: mine, send, deploy; then find each of them
      by search and walk the chain from the tip to genesis through
      `previousHash` and back through `nextHash`
- [x] Live: a real node, the explorer driven in a browser (search each kind,
      open a block, a transaction, an address; back button; a direct link;
      390 px without horizontal scroll; both themes; no page errors)
- [x] Docs: ARCHITECTURE (explorer), STATUS, README, roadmap (the optional
      explorer is done), project site

---

## Theory

### What a block explorer is

A blockchain is a public database with no query language. A node keeps the
blocks in the order they were mined, because that is what validation needs;
a person asks different questions: "did my payment arrive?", "what did this
address do?". An explorer is the read side: indexes over the chain plus pages
that link to each other.

```
 search box --> what is this string?
                  a number          -> block at that height
                  64 hex characters -> a block hash, or a transaction id
                  CONTRACT:...      -> a contract
                  anything else     -> an address (if it ever appeared)

 block  --previousHash-->  block  --previousHash-->  ...  genesis
   |  ^--nextHash (not in the block: only the chain knows what came after)
   +-- transactions --> transaction --> from / to --> address --> transactions
```

Three things an explorer shows are NOT stored in a block and must be
computed from the chain around it:

| Shown | Why it is not in the block |
|-------|----------------------------|
| Confirmations | They grow with every later block: tip height - block height + 1 |
| Next block hash | A block is hashed before its successor exists |
| Balance of an address | BlockSmith is account-based, but the balance is still the sum of every transaction; nothing stores it per block |

That is why Bitcoin Core needs `-txindex` to serve a transaction by id, and
why public explorers run their own databases next to a node. BlockSmith
already has the index (Sprint 16c: `tx_index` on id, sender, recipient); this
sprint adds the pages and the paging on top of it.

### Paging by cursor, not by page number

```
 GET /api/explorer/blocks?limit=20            -> blocks 60..41, nextBefore = 41
 GET /api/explorer/blocks?limit=20&before=41  -> blocks 40..21, nextBefore = 21
```

"Page 2" changes meaning every time a block is mined (every block shifts by
one, so a reader sees one block twice). "The 20 blocks below height 41" means
the same thing for ever, because heights do not move. A reorg can replace the
block at a height; the next request then shows the new one, and its hash
tells the reader it changed.

---

## Open decisions

- **Views inside the dashboard with `#/...` links** (recommended: one page,
  shareable links, no second server route) vs a separate `explorer.html`
- **New endpoints under `/api/explorer/`** (recommended: `/api/blocks`
  keeps its meaning for existing callers) vs changing `/api/blocks` to a
  paged reply
- **Additions to the block and transaction JSON** (recommended) vs separate
  "detail" endpoints
- **Search returns what to open, the page then loads it** (recommended) vs
  search returning the full object
- **Side-branch blocks are not searchable** (recommended: the explorer shows
  the active chain; side branches live in memory only) vs listing them

---

*Created: 2026-11-16 | Sprint 27 Planning | Completed: 2026-11-16*
