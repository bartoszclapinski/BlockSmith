# Sprint 17: Difficulty Adjustment - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-11 |
| **Sprint End** | 2026-11-11 |

---

## Milestone 17a: Numeric target (no behaviour change) - Complete (PR #276)

- `Target` utility: `fromLeadingZeros(n)` = `16^(64-n) - 1`, `meets(hash, target)`
  (malformed hashes never meet, never throw), `meetsHex` (the mining loop
  compares 64-digit hex text, which orders like the numbers), `toDifficulty`
  (leading-zero equivalent), hex encode/decode.
- `Block.mineBlock(BigInteger)`; the `int` overload delegates. `Blockchain`
  mining and validation use a single target constant.
- Equivalence proven on 3000 hashes for 0..8 zeros; genesis unchanged.
- 7 tests. Suite: 281 -> 288. Issues #267, #268.

---

## Milestone 17b: Retargeting + consensus params - Complete (PR #277)

- `ChainParams` record (`DEFAULT`: 4 zeros, 10 s blocks, retarget every 10,
  max 4x, powLimit 3 zeros); `Blockchain(store, params)`.
- `targetAt(height)`: `old * actual / expected` at window boundaries, actual
  clamped to 4x, capped at powLimit. The window never starts at genesis (its
  2024 timestamp would look "years slow") and measures every gap (no
  Bitcoin-style off-by-one).
- Targets are derived from timestamps, kept per height, never stored. Every
  append path (peer, orphan attach, restore) validates against the expected
  target at that height; orphans are parked on a powLimit check.
- Live: 21 fast blocks -> 4x harder from #10, 16x from #20.
- 10 tests. Suite: 288 -> 298. Issues #269, #270.

---

## Milestone 17c: Timestamp rules - Complete (PR #278)

- Timestamp > median of the previous 11 blocks; <= clock + `maxFutureDriftMs`.
- **Changed from the plan: 15 s instead of 2 min.** A miner can make a window
  look up to D longer; our window is ~100 s, so 2 min would allow a >2x easier
  target. Test: the time-warp trick hits the 4x clamp without the rule and is
  held to 1.75x with it.
- Injectable `Clock`; mining stamps `max(now, median + 1)`.
- 6 tests (one flawed test of mine removed: wrong median assumption). Suite:
  298 -> 304. Issues #271, #272.

---

## Milestone 17d: API + dashboard - Complete (PR #279)

- `Blockchain.getDifficultyHistory()`, `getBlocksUntilRetarget()`,
  `getAverageBlockTimeMs()`.
- `/api/network/status`: live `difficulty`, `target`, `targetBlockTimeMs`,
  `retargetInterval`, `blocksUntilRetarget`, `averageBlockTimeMs`;
  new `/api/network/difficulty` (epochs with measured windows).
- Dashboard: `difficulty 4.50 · retarget in 8 blocks`; header `node-node-` fixed.
- 6 tests. Suite: 304 -> 310. Issues #273, #274.

---

## Process change

- PRs are now merged with a **regular merge commit** (keeps one commit per
  issue on master). #279 still went in as a squash (the GitHub button
  remembered the old default), so squash and rebase merges were disabled in
  the repo settings.

---

*Sprint 17 Log*
