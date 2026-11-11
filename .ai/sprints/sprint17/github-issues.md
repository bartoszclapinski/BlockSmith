# Sprint 17: GitHub Issues

> Created on GitHub with `--body-file` (one file per issue). Actual numbers
> are assigned on creation.

---

## Milestone 17a: Numeric target (no behaviour change)

---

### Numeric 256-bit target instead of leading zeros

**Labels:** `sprint-17`, `milestone-17a`, `enhancement`

**Description:**

- `Target` utility: `fromLeadingZeros(n)` = `16^(64-n) - 1`, `meets(hashHex, target)`
  (hash as unsigned number `<=` target), `toDifficulty(target)` (leading-zero
  equivalent, e.g. 4.37), hex encode/decode
- `Block.mineBlock(BigInteger target)`; the `int` overload delegates
- `Blockchain` mining, `isWellFormed` and `isChainValid` use a target
- Still today's fixed rule (4 zeros); no behaviour change

**Acceptance Criteria:**
- [ ] `meets(h, fromLeadingZeros(n))` == `h.startsWith("0".repeat(n))` for all tested hashes
- [ ] All existing tests pass unchanged
- [ ] Compiles with `mvn compile`

---

### Tests for the numeric target

**Labels:** `sprint-17`, `milestone-17a`, `test`

**Description:**

**Test Cases:**
1. `fromLeadingZeros_matchesLeadingZeroRule` (random + edge hashes)
2. `meets_boundary_equalPassesPlusOneFails`
3. `toDifficulty_wholeAndFractional`
4. `hexRoundTrip`
5. `mineBlock_withTarget_meetsIt`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 17b: Retargeting + consensus params

---

### Retarget every N blocks from the chain's own timestamps

**Labels:** `sprint-17`, `milestone-17b`, `enhancement`

**Description:**

- `ChainParams` record: initial target, target block time, retarget interval,
  max adjustment factor (4), `powLimit`; `ChainParams.DEFAULT`
- `Blockchain(ChainStore, ChainParams)`; existing constructors use `DEFAULT`
- `expectedTarget(height)`: `old * actual / expected` at interval boundaries,
  clamped to 4x, never easier than `powLimit`
- Mining uses the expected target; validation checks every block against the
  expected target at its height (append, orphan attach, restore, `isChainValid`)
- Orphans checked against `powLimit` until they attach
- Block constructor overload with an explicit timestamp

**Acceptance Criteria:**
- [ ] Fast intervals make the target harder, slow ones easier, clamped at 4x
- [ ] A block mined at an easier target than expected is rejected
- [ ] Two nodes and a restore replay compute identical targets
- [ ] Compiles with `mvn compile`

---

### Tests for retargeting

**Labels:** `sprint-17`, `milestone-17b`, `test`

**Description:**

**Test Cases:**
1. `fastInterval_targetShrinks`
2. `slowInterval_targetGrows`
3. `adjustment_clampedAt4x`
4. `target_neverEasierThanPowLimit`
5. `peerBlock_atEasierTarget_rejected`
6. `restoreAndPeer_computeSameTargets`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 17c: Timestamp rules

---

### Median-time-past and future-time limits for block timestamps

**Labels:** `sprint-17`, `milestone-17c`, `enhancement`

**Description:**

- Block timestamp must be greater than the median of the previous 11 blocks
- And at most 2 minutes ahead of the validating node's clock
- Injectable `java.time.Clock` for tests
- Applied on append and restore

**Acceptance Criteria:**
- [ ] A timestamp at or before the median is rejected
- [ ] A far-future timestamp is rejected
- [ ] A time-warp attempt (fake slow interval) cannot lower the difficulty
- [ ] Compiles with `mvn compile`

---

### Tests for timestamp rules

**Labels:** `sprint-17`, `milestone-17c`, `test`

**Description:**

**Test Cases:**
1. `timestamp_atOrBeforeMedian_rejected`
2. `timestamp_farFuture_rejected`
3. `timestamp_slightlyAhead_accepted`
4. `timeWarp_cannotLowerDifficulty`
5. `storedChain_staysValidLater` (future rule uses the clock at validation)

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 17d: API + dashboard

---

### Live difficulty in the API and dashboard

**Labels:** `sprint-17`, `milestone-17d`, `enhancement`

**Description:**

- `GET /api/network/status`: live decimal `difficulty`, plus `target`,
  `targetBlockTimeMs`, `blocksUntilRetarget`, `averageBlockTimeMs`
- `GET /api/network/difficulty`: history per retarget interval
- Dashboard hero meta: live difficulty + "retarget in N blocks"
- Fix the `node-node-<id>` header label

**Acceptance Criteria:**
- [ ] Status and history match `Blockchain`
- [ ] Dashboard shows the live values
- [ ] Compiles with `mvn compile`

---

### Tests for the difficulty API

**Labels:** `sprint-17`, `milestone-17d`, `test`

**Description:**

**Test Cases:**
1. `status_hasLiveDifficultyFields`
2. `difficultyHistory_shape`
3. `values_matchBlockchain`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-11 | Sprint 17 Planning*
