# Sprint 21: Fork Choice

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 21 |
| **Title** | Fork Choice |
| **Phase** | Hardening (after the roadmap) |
| **Status** | 📋 Planned |
| **Depends On** | Sprint 20 (thread safety), Sprint 17 (targets), Sprint 18-19 (block rules, sequences) |

> **Why:** found while writing the Sprint 20 two-node test. A node only
> accepts a block that extends its own tip; a competing block is dropped.
> A probe on master (not committed):
>
> - nodes A and B each mine block #1 (a fork), then A mines #2 and #3
> - B is handed A's whole branch in order: **rejects all three**
> - B stays at height 1 while A is at 3; A's coins and a later payment to
>   Carol exist only on A, and B rejects every later block from A as well
>
> Any two miners that find a block at the same time split the network for
> good. Bitcoin's answer is the core of Nakamoto consensus: follow the valid
> chain with the **most cumulative work**, and switch (reorganise) when a
> competing branch overtakes yours.

---

## Goal

A node keeps competing branches it hears about, follows the valid chain with
the most work, and reorganises onto a better branch: blocks back to the fork
point are undone, the new branch is applied, and the transactions of undone
blocks go back to the mempool. Two nodes that mine at the same time always
end up on the same chain.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **21a** | Chain work + side branches | `sprint21a/chain-work` | ⬜ Planned (#320, #321) |
| **21b** | Reorganisation onto the most-work chain | `sprint21b/reorg` | ⬜ Planned (#322, #323) |
| **21c** | Fork-aware sync, API + dashboard, two miners converge | `sprint21c/fork-sync` | ⬜ Planned (#324, #325) |

---

## Milestone 21a: Chain work + side branches

### Deliverables

- [ ] `Target.work(target)` = 2^256 / (target + 1): the expected number of
      hashes a block at that target took. Height is not the measure: ten
      easy blocks can be less work than one hard one
- [ ] Cumulative work per height, derived next to the targets (never stored)
      and `getTotalWork()`
- [ ] **Side branches:** a block whose parent is known (on our chain or on a
      side branch) but which does not extend our tip is kept in a bounded
      `sideBlocks` map instead of being dropped, after the cheap checks
      orphans already get (well formed against `powLimit`, not too far in
      the future, within block limits) and only if its fork point is at most
      `MAX_REORG_DEPTH` (100) blocks below our tip
- [ ] No switching yet: the main chain behaves exactly as before
- [ ] Tests: work grows as targets shrink; equal targets = equal work per
      block; a competing block is kept as a side block, not dropped; a
      side-branch child of a side block is kept; too-deep forks and junk are
      not kept; the bound holds

---

## Milestone 21b: Reorganisation onto the most-work chain

### Deliverables

- [ ] **Fork choice:** after a side block is added, if its branch could beat
      the main chain, build the candidate chain (main up to the fork point +
      the branch) and **replay it** through full validation on a fresh
      `Blockchain` (the same path `isChainValid()` and restore use: targets,
      timestamps, content rules, sequences, contracts). Adopt it only if it
      is valid **and** has strictly more total work (a tie keeps the chain we
      saw first, as Bitcoin does). An invalid branch is dropped
- [ ] **Reorg (under the lock, all or nothing):** swap in the candidate's
      state (chain, targets, work, confirmed ids and sequences, contracts,
      `tipHash`); the undone main blocks become side blocks (we could switch
      back); the mempool is rebuilt: undone blocks' transactions plus the old
      pending ones are re-submitted against the new chain, in order, so
      anything now confirmed, conflicting or unaffordable falls out
- [ ] **Store:** blocks from the fork point are rewritten and rows above the
      new tip deleted (`ChainStore.truncateAbove(height)`); `tx_index` follows;
      pending rows rewritten
- [ ] A mine in progress sees the tip move and starts over (Sprint 20b)
- [ ] Tests: the probe above (B switches to A's longer branch); 1-deep and
      3-deep reorgs; undone transactions return to the mempool; a payment
      double-spent on the winning branch is dropped; an invalid branch with
      more blocks is rejected and the main chain kept; equal work keeps the
      first; a contract claimed only on the losing branch is open again; a
      sequence used only on the losing branch is free again; a reorg survives
      a restart (store rewritten); `MAX_REORG_DEPTH` respected

---

## Milestone 21c: Fork-aware sync, API + dashboard

### Deliverables

- [ ] **Block locator:** `GET_BLOCKS` may carry a list of our block hashes
      (tip, tip-1, tip-2, tip-4, tip-8, ... genesis). The peer answers with its
      blocks after the first hash it also has on its main chain, so a node on
      another branch gets the missing ancestors. Index-only requests still work
- [ ] `NEW_BLOCK` whose parent we do not know: ask the sender with a locator
- [ ] API: `/api/network/status` adds `totalWork`, `reorgs`, `lastReorg`
      (`depth`, old tip, new tip, time); dashboard feed shows "Chain reorganised"
- [ ] End-to-end: two nodes over TCP **both mining** through their APIs while
      transactions flow; afterwards identical valid chains, every surviving
      transaction confirmed once, nothing lost (undone transactions back in
      a block or the mempool). This is the test Sprint 20 could not write
- [ ] Docs: fork choice in `ARCHITECTURE.md`, deferred item removed

---

## Theory: most work, not longest

```
            +-- B1                      (B's block: first seen by B)
 G -- 1 --+
            +-- A1 -- A2 -- A3          (more work: everyone switches here)

 reorg on B:  undo B1  ->  apply A1, A2, A3  ->  B1's txs back to the mempool
```

Proof-of-Work makes history expensive to rewrite: to replace the last N
blocks, an attacker must redo their work AND outpace the honest network.
That only holds if every node follows the chain with the most work; a node
that keeps the first chain it saw can be split off by anyone with one lucky
block. "Most work" (sum of 2^256 / (target + 1)) rather than "most blocks"
matters once difficulty varies: a short branch of hard blocks beats a long
branch of easy ones, so an attacker cannot win by mining many cheap blocks
on a private branch whose timestamps lowered its difficulty.

A confirmed transaction is therefore only *probably* final: each block on
top makes a reorg past it exponentially less likely (Bitcoin's "6
confirmations"). The API's `confirmations` field already says how deep a
transaction is; after this sprint that number can also go down.

---

## Open decisions

- **Reorg by replaying the candidate chain** (recommended: reuses the one
  validation path every block already goes through, so a reorg cannot apply
  a rule differently; O(chain length) per reorg, fine at this scale) vs
  Bitcoin-style undo data per block (fast, but a second code path for every
  rule)
- **Try a branch when its height reaches ours, decide by work**: a cheap
  pre-filter; within a retarget window both branches share the target, so
  a lower branch cannot have more work, and the replay computes the exact
  work before anything is adopted
- **`MAX_REORG_DEPTH` = 100**: deeper forks are ignored (protects against a
  long private chain forcing a full replay; real nodes rely on the work
  itself and, historically, checkpoints)
- **Out of scope:** headers-first sync, compact blocks, persisting side
  branches across restarts (they are re-learned from peers)

---

*Created: 2026-11-14 | Sprint 21 Planning*
