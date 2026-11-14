# Sprint 21: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 21a: Chain work + side branches

---

### Competing blocks are dropped: keep side branches and measure chain work

**Labels:** `sprint-21`, `milestone-21a`, `bug`

**Description:**

A block that does not extend our tip is dropped, so two nodes that mine at the
same height diverge for good. Reproduced: B rejects all of A's longer branch
(a1, a2, a3) and every later block from A.

First step (no switching yet):
- `Target.work(target)` = 2^256 / (target + 1); cumulative work per height
  (derived, never stored); `getTotalWork()`
- keep competing blocks in a bounded `sideBlocks` map when their parent is
  known, after the orphan checks (well formed vs `powLimit`, not too far in
  the future, within limits) and only if the fork point is within
  `MAX_REORG_DEPTH` (100) of the tip

**Acceptance Criteria:**
- [ ] A competing block is kept as a side block
- [ ] The main chain behaves exactly as before
- [ ] Compiles with `mvn compile`

---

### Tests for chain work and side branches

**Labels:** `sprint-21`, `milestone-21a`, `test`

**Description:**

**Test Cases:**
1. `work_growsAsTargetShrinks`
2. `competingBlock_keptAsSideBlock`
3. `sideBranchChild_kept`
4. `tooDeepOrJunk_notKept`
5. `sideBlocks_bounded`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 21b: Reorganisation onto the most-work chain

---

### Reorganise onto the valid chain with the most work

**Labels:** `sprint-21`, `milestone-21b`, `enhancement`

**Description:**

- When a side branch could beat the main chain, replay main-up-to-fork +
  branch through full validation on a fresh Blockchain; adopt it only if
  valid and strictly more work (tie keeps the first seen); drop it if invalid
- Reorg under the lock, all or nothing: swap chain, targets, work, confirmed
  ids/sequences, contracts, `tipHash`; undone blocks become side blocks;
  rebuild the mempool by re-submitting undone + old pending txs in order
- Store: rewrite from the fork point, `ChainStore.truncateAbove(height)`,
  `tx_index` and pending rows follow

**Acceptance Criteria:**
- [ ] The probe scenario converges (B switches to A's branch)
- [ ] Nothing is lost or doubled: undone txs return to the mempool unless
      confirmed or conflicting on the new branch
- [ ] Compiles with `mvn compile`

---

### Tests for reorganisation

**Labels:** `sprint-21`, `milestone-21b`, `test`

**Description:**

**Test Cases:**
1. `longerBranch_wins` (the probe)
2. `reorg_oneAndThreeDeep`
3. `undoneTransactions_backInMempool`
4. `doubleSpendOnWinningBranch_dropped`
5. `invalidBranch_rejected_mainKept`
6. `equalWork_keepsFirstSeen`
7. `contractClaim_andSequence_undone`
8. `reorg_survivesRestart`
9. `maxReorgDepth_respected`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 21c: Fork-aware sync, API + dashboard

---

### Fork-aware sync (block locator) and reorg visibility

**Labels:** `sprint-21`, `milestone-21c`, `enhancement`

**Description:**

- `GET_BLOCKS` with an optional block locator (tip, tip-1, tip-2, tip-4, ...
  genesis); the peer replies with its blocks after the first locator hash on
  its main chain; index-only requests still work
- `NEW_BLOCK` with an unknown parent: ask the sender with a locator
- `/api/network/status`: `totalWork`, `reorgs`, `lastReorg`; dashboard feed
  "Chain reorganised"
- Docs: fork choice in ARCHITECTURE, deferred item removed

**Acceptance Criteria:**
- [ ] A node on another branch fetches the missing ancestors and reorganises
- [ ] Compiles with `mvn compile`

---

### End-to-end: two mining nodes converge

**Labels:** `sprint-21`, `milestone-21c`, `test`

**Description:**

**Test Cases:**
1. `locator_findsCommonAncestor`
2. `nodeOnOtherBranch_syncsAndReorgs`
3. `twoMiningNodes_convergeToSameValidChain`
4. `status_reportsReorgs`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-14 | Sprint 21 Planning*
