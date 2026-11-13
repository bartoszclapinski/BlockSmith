# Sprint 19: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 19a: Mempool limit + eviction

---

### Mempool size limit with lowest-fee-rate eviction

**Labels:** `sprint-19`, `milestone-19a`, `enhancement`

**Description:**

The mempool is unbounded: anyone can queue any number of zero-fee
transactions. Add a node policy (not consensus) that bounds it.

- `core.MempoolPolicy` record: `maxBytes` (64 KB), `maxTransactions` (200),
  `incrementalFeeRate`; `Blockchain(store, params, policy)`, old constructors
  use `DEFAULT`
- A new tx that does not fit must beat the lowest pending fee rate by the
  increment, else it is rejected; the lowest-rate txs (newest first among
  equal rates) are evicted until it fits, also from the store
- `getMempoolMinFeeRate()`: 0 while there is room, else lowest rate + increment
- Restore trims a stored mempool that is over the limit

**Acceptance Criteria:**
- [ ] A full pool rejects a cheap tx and accepts a better-paying one by evicting
- [ ] Blocks are never rejected because of the policy
- [ ] Compiles with `mvn compile`

---

### Tests for the mempool limit

**Labels:** `sprint-19`, `milestone-19a`, `test`

**Description:**

**Test Cases:**
1. `fullPool_rejectsLowFeeRate`
2. `betterFeeRate_evictsCheapest` (pending outgoing of the evicted sender freed)
3. `equalRates_evictNewestFirst`
4. `byteLimit_and_countLimit`
5. `minFeeRate_zeroWithRoom_risesWhenFull`
6. `restore_trimsOversizeMempool`
7. `policy_neverRejectsBlocks`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 19b: Replace-by-fee (consensus)

---

### Replace-by-fee with a per-sender sequence

**Labels:** `sprint-19`, `milestone-19b`, `enhancement`

**Description:**

In an account model a "replacement" with a new id is just a second payment;
both could be mined. Give transactions an optional conflict key.

- `Transaction.sequence` (optional `Long`), in the id and signed data only
  when set (existing ids and signatures unchanged)
- Consensus: a `(sender, sequence)` pair confirms at most once, in a block
  and across the chain (`confirmedSequences`, rebuilt by restore replay);
  not required to be consecutive
- Mempool (BIP125-style): a tx with a pending `(sender, sequence)` replaces it
  if recipient and amount are the same, the fee is higher and the fee rate
  beats the old one by `incrementalFeeRate`, and the sender can afford it
  with the old cost released
- Pending conflicts are dropped when a block confirms the pair

**Acceptance Criteria:**
- [ ] Only one version of a replaced payment can ever confirm
- [ ] Transactions without a sequence behave exactly as before
- [ ] Compiles with `mvn compile`

---

### Tests for replace-by-fee

**Labels:** `sprint-19`, `milestone-19b`, `test`

**Description:**

**Test Cases:**
1. `bump_replacesPending`
2. `bumpTooSmall_rejected`
3. `changedAmountOrRecipient_rejected`
4. `noSequence_neverConflicts_idUnchanged`
5. `blockWithBothVersions_rejected`
6. `replacementOfConfirmed_rejected` (and the original after the replacement confirmed)
7. `pendingConflict_droppedWhenOtherVersionMined`
8. `restore_rebuildsSequences`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 19c: Fee estimation

---

### Fee estimation from the mempool and recent blocks

**Labels:** `sprint-19`, `milestone-19c`, `enhancement`

**Description:**

- `Blockchain.estimateFeeRate(int targetBlocks)`:
  - mempool view: simulate `targetBlocks` blocks in mining order under the
    block limits; everything fits -> floor, else the rate of the last tx in
    block `targetBlocks` plus the increment
  - history view: median of the lowest included fee rate of recent full blocks
  - result = max(mempool view, history view, `getMempoolMinFeeRate()`)
- Targets 1, 3, 6 blocks

**Acceptance Criteria:**
- [ ] Estimates rise as the mempool grows past one block
- [ ] A longer target never costs more than a shorter one
- [ ] Compiles with `mvn compile`

---

### Tests for fee estimation

**Labels:** `sprint-19`, `milestone-19c`, `test`

**Description:**

**Test Cases:**
1. `emptyMempool_floor`
2. `mempoolOverOneBlock_mustBeatCutoff`
3. `longerTarget_neverMoreExpensive`
4. `fullRecentBlocks_raiseEstimate`
5. `fullMempool_atLeastMinFeeRate`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 19d: API + dashboard

---

### Fee market in the API and dashboard

**Labels:** `sprint-19`, `milestone-19d`, `enhancement`

**Description:**

- `GET /api/fees/estimate`: fee rate for 1 / 3 / 6 blocks, fee for a typical
  tx, `mempoolMinFeeRate`
- `POST /api/transactions`: new txs get a `sequence`; "mempool full" is a 400
  with the minimum fee rate
- `POST /api/transactions/{id}/bump` with a new `fee`
- `GET /api/mempool`: policy limits, bytes used, `mempoolMinFeeRate`
- Dashboard: suggested fees in the send drawer; Mempool drawer with "Bump fee"

**Acceptance Criteria:**
- [ ] A stuck transaction can be bumped from the dashboard and gets mined first
- [ ] Compiles with `mvn compile`

---

### Tests for the fee market API

**Labels:** `sprint-19`, `milestone-19d`, `test`

**Description:**

**Test Cases:**
1. `estimate_shape`
2. `bump_endToEnd` (old gone, new pending, miner gets the new fee)
3. `bumpTooSmall_400`
4. `mempoolFull_400_withMinFeeRate`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-13 | Sprint 19 Planning*
