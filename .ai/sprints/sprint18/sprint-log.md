# Sprint 18: Block Rules, Size & Fees - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-11 |
| **Sprint End** | 2026-11-13 |

---

## Milestone 18a: Block content validation (consensus) - Complete (PR #290)

- Fixes **#281 (critical)**: a peer block whose coinbase paid 1,000,000 and in
  which Alice spent coins she never had was accepted, and `isChainValid()`
  still said true.
- `blockRuleViolation(block)` / `transactionRuleViolation(tx, check)`: the
  coinbase is first, alone and pays at most the reward; every other tx is
  well formed, a signed one verifies, it is not already included (in the
  block or on the chain, via a `confirmedTxIds` set), a deploy has a locking
  script, a claim passes the script VM at that height (and only once per
  block), and the sender can afford it. A `BlockCheck` carries the balances
  and ids of the transactions before it in the same block, so an overspend
  split over two txs is caught.
- One path for every block: peer, orphan attach, restore replay and our own
  mining (a locally mined invalid block throws). `isChainValid()` replays the
  chain onto a fresh `Blockchain`, so it runs exactly the same rules.
- Mining drops pending txs that went stale (e.g. a peer block spent the same
  funds); `addTransaction` rejects bad signatures and already confirmed txs.
- Coinbase txs are exempt from the "already on the chain" rule (two blocks can
  pay the same miner the same reward in the same millisecond; Bitcoin's BIP30
  problem), they are checked by position instead.
- 13 tests (`BlockContentRulesTest`); `MempoolPruneTest` helper fixed (it
  built a block that is now correctly rejected). Suite: 310 -> 323.
  Issues #281, #282.

---

## Milestone 18b: Block size & transaction limits - Complete (PR #291)

- `ChainParams.maxBlockBytes` / `maxBlockTransactions`, defaults **16 KB / 50**
  (the old 5- and 6-argument constructors still work).
- `Block.sizeBytes()` = bytes of the block's JSON, the form peers exchange.
  `limitViolation` rejects oversize blocks on every path; oversize orphans
  are not even buffered.
- Mining fills a block up to the limits and leaves the rest pending, keeping
  `NONCE_DIGITS_MARGIN` (9) bytes for the nonce to grow while mining.
- 6 tests (`BlockLimitsTest`). Also fixed the flaky
  `MiningTest.nonceChangesAfterMining` (fixed timestamp). Suite: 323 -> 329.
  Issues #283, #284.

---

## Milestone 18c: Transaction fees + fee-priority mining - Complete (PR #292)

- `Transaction.fee` + 4-argument constructor. The fee is part of the id and
  the signed data **only when it is not 0**, so every existing transaction
  keeps its id and signature (`getSignatureData()` is shared by `Wallet` and
  `verifySignature`). Negative, NaN and infinite fees are invalid.
- `getTotalCost()` (amount + fee) is what the sender pays everywhere: balance,
  pending outgoing, block checks. `sizeBytes()` and `feeRate()` (fee per byte).
- Coinbase may claim reward + the block's fees and carries no fee itself.
- Mining sorts the mempool by fee rate (stable, so equal rates keep arrival
  order), selects against a reward-only placeholder coinbase, then builds the
  real one (reward + fees); `COINBASE_AMOUNT_MARGIN` (24 bytes) keeps room for
  the bigger amount.
- 9 tests (`FeeTest`). Suite: 329 -> 338. Issues #285, #286.

---

## Milestone 18d: API + dashboard - Complete (PR #293)

- `POST /api/transactions` takes an optional `fee` (400 for a negative or
  non-numeric one).
- New `GET /api/mempool`: count, total fees, block limits and the pending txs
  in mining order (`Blockchain.getPendingInMiningOrder()`), each with
  `sizeBytes` and `feeRate`.
- Block endpoints add `sizeBytes` and `totalFees` (peers still exchange the
  plain block); `/api/network/status` adds `maxBlockBytes` and
  `maxBlockTransactions`.
- Dashboard: "Fee (BSC, optional)" in the send drawer; block cards show
  `4 tx · fees 1 · 1016 B` instead of the nonce.
- Live: fees 0.75 / 0.25 / 0 listed in that order, coinbase 51.0.
- 4 tests (`ApiFeesTest`). Suite: 338 -> 342. Issues #287, #288.

---

## Retrospective

- The sprint started as "Block Size & Limits" and grew once a planning test
  showed that block *contents* were never checked. Limits and fees are content
  rules too, so the fix (18a) became the base the rest plugged into.
- One rule set for every block (mined, peer, orphan, restore, `isChainValid`)
  is what keeps these rules from drifting apart.

---

*Sprint 18 Log*
