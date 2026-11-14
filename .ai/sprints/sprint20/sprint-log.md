# Sprint 20: Thread Safety - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | 2026-11-14 |

---

## Planning probe (not committed)

Run on master before any change:
- reader iterating `getChain()` during appends: `ConcurrentModificationException`
- 4 threads x 50 mines: 164 blocks, 162 with the wrong index, chain invalid
- 4 concurrent 40-coin spends of 50: more than one admitted in 153/200 rounds

---

## Milestone 20a: `Blockchain` as a monitor + snapshot getters - Complete (PR #316)

- 37 public methods `synchronized` (one reentrant monitor); only the
  immutable `ChainParams` / `MempoolPolicy` getters stay unlocked.
- `getChain()` / `getPendingTransactions()` return copies, not live views.
- Lock order `Blockchain` -> `ChainStore`; the store never calls back and the
  chain never calls `Node` or the API. Threading model in the class Javadoc.
- `ConcurrencyTest` (5): the planning probes as assertions. All five fail on
  the old class (checked by stashing the change) and pass on the monitor.
- Suite: 370 -> 375. Issues #309, #310.

---

## Milestone 20b: Mining outside the lock - Complete (PR #317)

- template (locked) -> nonce search (unlocked) -> submit (locked, same tip
  only); stale work rebuilds the template, at most 100 attempts.
- `Block.mineBlock(target, stop)` asks before starting and every 1000 nonces;
  the miner stops when the `volatile tipHash` moves or the caller cancels.
  New overload `minePendingTransactions(miner, cancelled)` (null if cancelled).
- Tests act mid-mine through the stop condition (it runs in the unlocked
  phase), so they need no sleeps: pause the miner, deliver a peer block.
- `MiningOutsideLockTest` (6). Mutations caught: whole mine under the lock
  (timeout), no tip check on submit (invalid block).
- Suite: 375 -> 381. Issues #311, #312.

---

## Milestone 20c: Consistent API / node views + end-to-end test - Complete (PR #318)

- `Blockchain.atomically(Supplier)`: a group of calls under one lock. Used by
  status (chain part), wallet, mempool, fee estimate, difficulty, contracts;
  peer data is read outside it (never hold two locks).
- Bug fixed: `/api/wallet` `pendingOutgoing` ignored fees.
- `Node` handlers reviewed: harmless check-then-act, no change.
- First two-node TCP test: 2 mining + 4 sending threads through both APIs;
  chains identical and valid, 40 transfers confirmed once, supply = 50 x blocks.
- **Found:** no fork choice. Two nodes mining the same height diverge for
  good (no most-work rule, no reorg). Recorded as a deferred item; the test
  mines on one node.
- `NodeConcurrencyTest` (3). Mutation (`atomically` unlocked, 3 runs): the
  deterministic test failed 3/3, the API status test 2/3.
- Suite: 381 -> 384. Issues #313, #314.

---

## Retrospective

- Probing first paid off: the plan started from measured failures (a
  corrupted chain from two concurrent mines), and each became a test that
  fails on the old code.
- Using the miner's own stop condition as a test hook made the timing tests
  deterministic instead of sleep-based.
- The end-to-end test surfaced the next real gap (fork choice), which is a
  consensus feature, not a threading one.

---

*Sprint 20 Log*
