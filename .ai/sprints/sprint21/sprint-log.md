# Sprint 21: Fork Choice - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | 2026-11-14 |

---

## Planning probe (not committed)

On master: A and B each mine #1 (a fork), A mines #2 and #3. B is handed
a1, a2, a3 in order and rejects all three; B stays at height 1 (A at 3),
A's coins and a later payment to Carol exist only on A, and B rejects a4 too.

---

## Milestone 21a: Chain work + side branches - Complete (PR #327)

- `Target.work(target)` = 2^256 / (target + 1); cumulative work per height
  next to the targets (derived), `getTotalWork()`; `heightByHash`.
- Competing blocks (parent known, not extending the tip) kept in a bounded
  `sideBlocks` map (200) after the cheap checks, if the fork point is within
  `MAX_REORG_DEPTH` (100); orphans waiting on them follow. A block at tip+1
  with an unknown parent is now an orphan (was dropped).
- `SideBranchTest` (5). Also fixed a flaky `MempoolPruneTest` helper (same
  millisecond as the tip -> median-time-past failure, ~1 run in 6).
- Suite: 384 -> 389. Issues #320, #321.

---

## Milestone 21b: Reorganisation onto the most-work chain - Complete (PR #328)

- When a branch reaches our height, ours-up-to-fork + branch is replayed
  through `addBlock` on a fresh `Blockchain` (the one validation path).
  Invalid -> branch forgotten from the bad block on. Valid and strictly more
  work -> `adoptChain`; ties keep the first seen.
- `adoptChain` under the lock: chain-derived state from the candidate,
  undone blocks become side blocks, store rewritten + new
  `ChainStore.truncateAbove`, mempool rebuilt (undone txs, then old pending).
  `addBlock` returns true when the block ends up on our chain via a reorg.
  `ReorgEvent`, `getReorgCount()`, `getLastReorg()`.
- `ReorgTest` (11). A mutation (winning-branch sequences not swapped) first
  survived; a test for "a sequence confirmed on the winning branch stays
  used" was added and catches it. `SideBranchTest` adjusted to equal work.
- Not covered by a reorg test: a shorter-but-more-work winner (needs varying
  targets); `truncateAbove` is tested at the store level.
- Suite: 389 -> 400. Issues #322, #323.

---

## Milestone 21c: Fork-aware sync, API + dashboard - Complete (PR #329)

- Block locator (10 one by one, then doubling gaps, genesis);
  `getBlocksAfter(locator, 500)`; `isKnownBlock`. `GET_BLOCKS` carries an
  optional locator; `NEW_BLOCK` with an unknown parent asks with it; a full
  reply asks for more.
- Status: `totalWork` (string), `sideBlocks`, `reorgs`, `lastReorg`;
  dashboard feed "Chain reorganised".
- `ForkSyncTest` (3): locator shape; a node on another branch reorganises
  over TCP in 167 ms; two nodes both mining converge (3 runs, 1-2 real
  reorgs each, every admitted transfer confirmed once).
- Suite: 400 -> 403. Issues #324, #325.

---

## Retrospective

- Replaying the candidate through the normal `addBlock` meant no second
  validation path: every rule from Sprints 3-19 applies to reorgs for free.
- Mutation testing found a real gap in the tests (winning-branch sequences),
  which is exactly what it is for.
- The two-node test Sprint 20 could not write now passes with real forks.

---

*Sprint 21 Log*
