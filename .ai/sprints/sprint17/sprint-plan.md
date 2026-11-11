# Sprint 17: Difficulty Adjustment

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 17 |
| **Title** | Difficulty Adjustment |
| **Phase** | Phase 4: Production Features |
| **Status** | ✅ Complete (17a, 17b, 17c, 17d) |
| **Depends On** | Sprint 16 Complete (persistence, restore by replay) |

> **Approach (recommended):** the Bitcoin model. Replace "N leading zeros"
> with a **numeric target** (a 256-bit number; a block is valid if its hash,
> read as a number, is at most the target). Every `RETARGET_INTERVAL` blocks
> the target is recomputed from how long the last interval actually took
> versus how long it should have taken, clamped to 4x either way. The target
> is **derived from the chain's own timestamps**, so every node (and every
> restore replay) computes the same value without storing it. Timestamp
> rules stop miners from faking time to make mining easier.

---

## Goal

Keep block times near a target no matter how much hashing power the network
has. Today difficulty is a constant (4 leading hex zeros): a faster machine
mines faster blocks, a slower one slower blocks, and validation checks every
block against that one constant. After this sprint the chain tunes its own
difficulty, every node agrees on it, and the API and dashboard show it.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **17a** | Numeric target (no behaviour change) | `sprint17a/numeric-target` | ✅ Complete (PR #276) |
| **17b** | Retargeting + consensus params | `sprint17b/retarget` | ✅ Complete (PR #277) |
| **17c** | Timestamp rules | `sprint17c/timestamp-rules` | ✅ Complete (PR #278) |
| **17d** | API + dashboard | `sprint17d/difficulty-api` | ✅ Complete (PR #279) |

---

## Milestone 17a: Numeric target (no behaviour change)

### Deliverables

- [ ] `Target` utility (`com.blocksmith.util`): 256-bit target as `BigInteger`
  - `fromLeadingZeros(n)` = `16^(64-n) - 1` (so 4 zeros = today's rule exactly)
  - `meets(hashHex, target)`: hash as an unsigned number `<=` target
  - `toDifficulty(target)`: human-readable "leading-zero equivalent" (e.g. 4.00, 4.37)
  - hex encode/decode for the API
- [ ] `Block.mineBlock(BigInteger target)`; the `int` overload delegates
- [ ] `Blockchain` mining and validation (`isWellFormed`, `isChainValid`) use a
      target instead of `"0".repeat(MINING_DIFFICULTY)`
- [ ] Still a fixed target (today's 4 zeros); all existing tests unchanged
- [ ] Tests: leading-zeros equivalence (both directions, edge values), `meets`
      boundaries (`hash == target`, `target + 1`), difficulty display

### Why this is first

It is a pure refactor with an exact equivalence (4 zeros <=> `hash <= 16^60 - 1`).
Landing it alone proves nothing changed before the rules start to move.

---

## Milestone 17b: Retargeting + consensus params

### Deliverables

- [ ] `ChainParams` (record): initial target, target block time, retarget
      interval, max adjustment factor (4), minimum difficulty (`powLimit`).
      `ChainParams.DEFAULT` for real nodes; tests can pass easier params
- [ ] `Blockchain(ChainStore, ChainParams)`; existing constructors use `DEFAULT`
- [ ] `Blockchain.expectedTarget(height)`: same target within an interval; at
      each interval boundary:
      `new = old * actualTimespan / expectedTimespan`, clamped to `[old/4, old*4]`
      and never easier than `powLimit`. Pure function of earlier blocks
- [ ] Mining uses `expectedTarget(tip + 1)`
- [ ] **Validation uses the expected target at the block's height** on every
      path: append, attach orphan, restore replay, `isChainValid`
- [ ] Orphans (parent unknown, so no history yet): checked against `powLimit`
      only; full check when they attach
- [ ] Block constructor overload with an explicit timestamp (for tests that
      simulate slow / fast networks)
- [ ] Tests: blocks too fast -> target shrinks (harder); too slow -> grows;
      clamp at 4x; never below `powLimit`; a peer block mined at an easier
      target than expected is rejected; restore and a second node compute
      identical targets

### Note

The target is not stored in the block: it is recomputed from timestamps that
are already covered by the block hash. Nothing to forge, nothing to migrate.

---

## Milestone 17c: Timestamp rules

### Deliverables

- [ ] A block's timestamp must be **greater than the median of the previous 11**
      (median-time-past, as in Bitcoin)
- [ ] And **not more than 15 seconds in the future** of the validating node's
      (changed during 17c from the planned 2 minutes, see Open decisions)
      clock (Bitcoin allows 2 hours; our blocks are seconds apart)
- [ ] Injectable `java.time.Clock` for tests
- [ ] Applied on append and restore; the future-time rule uses the node's clock
      at validation time, so a stored chain stays valid later
- [ ] Tests: timestamp before the median rejected; far-future rejected; the
      "time-warp" attempt (fake old timestamps to lower difficulty) fails

### Why it matters

Without these rules a miner can stamp blocks with fake times: make the last
interval look slow and the next target gets easier. The retarget formula is
only as honest as the timestamps it reads.

---

## Milestone 17d: API + dashboard

### Deliverables

- [ ] `GET /api/network/status`: `difficulty` becomes the live decimal value;
      adds `target` (hex), `targetBlockTimeMs`, `blocksUntilRetarget`,
      `averageBlockTimeMs` (last interval)
- [ ] `GET /api/network/difficulty`: history per retarget interval (height,
      difficulty, actual vs expected timespan)
- [ ] Dashboard hero meta shows the live difficulty and "retarget in N blocks"
- [ ] Fix: dashboard header shows `node-node-<id>` (prefix added twice)
- [ ] Tests: status fields, history shape, values match `Blockchain`

---

## Theory: the retarget formula

```
Expected time for one interval = RETARGET_INTERVAL * TARGET_BLOCK_TIME
Actual time                    = timestamp(last block) - timestamp(first block)

new target = old target * actual / expected

  blocks came 2x too fast -> actual = expected / 2 -> target halves (2x harder)
  blocks came 2x too slow -> actual = expected * 2 -> target doubles (2x easier)

Clamp: the target may move at most 4x per interval (avoids wild swings and
limits what a burst of fake timestamps can do).
```

A bigger target is EASIER (more hashes qualify). "Difficulty" for humans is
the inverse; we show it as the leading-zero equivalent.

---

## Dependencies

- `java.math.BigInteger` (JDK) for 256-bit arithmetic; no new library
- Sprint 16 restore-by-replay: replay recomputes targets, so stored chains
  are re-validated under the new rules automatically

---

## Open decisions

- **Numeric target** (recommended: smooth adjustment, Bitcoin's model) vs
  +/-1 leading zero (16x steps, too coarse to converge)
- **Retarget interval / target time**: 10 blocks of 10 s each (recommended for
  a demo: adjustment visible within ~2 minutes) vs Bitcoin's 2016 x 10 min
- **Clamp 4x** (recommended, Bitcoin) vs unclamped
- **Future limit: 15 s** (decided in 17c; first planned as 2 min). A miner may fake a
  window up to D longer, and our window is ~100 s, so 2 min would allow a >2x easier
  target. Bitcoin's 2 h is only 0.6% of its 2-week window. Configurable as
  `ChainParams.maxFutureDriftMs`
- **Existing data**: local `data/` is empty; chains mined before this sprint
  will be re-validated under the new rules on restore

---

*Created: 2026-11-11 | Sprint 17 Planning*
