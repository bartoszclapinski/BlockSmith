# Sprint 27: Block Explorer - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-16 |
| **Sprint End** | - |

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
