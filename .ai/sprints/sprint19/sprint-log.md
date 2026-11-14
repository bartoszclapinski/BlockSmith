# Sprint 19: Fee Market - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-13 |
| **Sprint End** | 2026-11-14 |

---

## Milestone 19a: Mempool limit + eviction - Complete (PR #304)

- `MempoolPolicy` record: node **policy**, kept apart from the consensus
  `ChainParams`. Default 64 KB (four full blocks), 200 transactions,
  `incrementalFeeRate` 0.0001 BSC/byte (~0.025 BSC on a typical tx).
- A tx that does not fit must beat every victim's fee rate by the increment;
  victims are the cheapest, newest first among equal rates. All or nothing:
  a refused tx evicts nobody. Evicted txs leave the store too, and their
  sender's pending outgoing is released.
- `getMempoolMinFeeRate()`, `getPendingBytes()`. Restore trims an oversize
  stored mempool by the same rule; `RestoreReport.pendingRestored` now counts
  what is left after trimming (it counted additions before).
- Found: 3 dashboard tests failed with 404 when Maven ran from
  `F:\Repos\java` (lowercase); Jetty treats a path whose case differs from
  the real folder `Java` as an alias. Not a code issue.
- 7 tests (`MempoolLimitTest`). Suite: 342 -> 349. Issues #295, #296.

---

## Milestone 19b: Replace-by-fee (consensus) - Complete (PR #305)

- In an account model a "replacement" with a new id is just a second
  payment. New optional `Transaction.sequence`, in the id and signed data only
  when set (left out of the JSON when null, so old ids, JSON and sizes stay).
- **Consensus**: a `(sender, sequence)` pair confirms at most once
  (`confirmedSequences`, rebuilt by replay); not required to be consecutive,
  so any subset of the mempool is still a valid block. Coinbase: no sequence.
- **Mempool (BIP125-style)**: only the fee may change; higher fee and fee rate
  + increment; a signed original only by the same key; the old cost is
  released; the old version gives up its place (and is put back if the new
  one still does not fit). A pending version is dropped when a block
  confirms the other one.
- `withFee()`, `randomSequence()`, `getPendingTransaction(id)`.
- 11 tests (`ReplaceByFeeTest`) + 1 gossip test (`TransactionBroadcastTest`).
  Suite: 349 -> 361. Issues #297, #298.

---

## Milestone 19c: Fee estimation - Complete (PR #306)

- `estimateFeeRate(targetBlocks)` = max of: the mempool view (simulate the
  next N blocks like mining; beat the cheapest that got in), the history view
  (cheapest included rate of each of the last 6 blocks, 0 for a block with
  room; target 1 takes the highest, 6 the lowest) and the mempool minimum.
  A longer target never costs more.
- 5 tests (`FeeEstimationTest`). Suite: 361 -> 366. Issues #299, #300.

---

## Milestone 19d: API + dashboard - Complete (PR #307)

- `GET /api/fees/estimate` (fast / normal / slow = 1 / 3 / 6 blocks, rate and
  fee for a measured 281-byte dashboard tx, rounded up).
- `POST /api/transactions`: random `sequence`, the refusal reason in the 400
  (`Blockchain.submitTransaction`). `POST /api/transactions/{id}/bump`
  (default: `minimumBumpFee`). `/api/mempool`: bytes, limits, min fee rate,
  per-tx `replaceable` / `minBumpFee`.
- Dashboard: suggested fees in the send drawer; Mempool drawer with "Bump".
- Live (headless Edge): 53 pending txs; the "Next block" suggestion put a tx
  at #43 of 49 slots; a bump moved the cheapest from #54 to #48.
- Fixed on the way: success toasts were removed at once by closing the
  drawer; pending outgoing showed float noise; `roundUpFee(0)` gave -0.0.
- 4 tests (`ApiFeeMarketTest`). Suite: 366 -> 370. Issues #301, #302.

---

## Retrospective

- Sprint 19 closes Phase 4 and the roadmap's 20 sprints (0-19).
- The one design decision with consensus impact was the sequence: RBF looks
  like a mempool feature, but in an account model it is only safe once the
  chain itself guarantees that two versions of a payment cannot both confirm.
- Mutation checks (break a rule, see a test fail) were run for 19a-19c and
  each broken rule was caught.

---

*Sprint 19 Log*
