# Sprint 20: Thread Safety

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 20 |
| **Title** | Thread Safety |
| **Phase** | Hardening (after the roadmap; first item of the deferred list) |
| **Status** | 📋 Planned |
| **Depends On** | Sprints 0-19 complete |

> **Why now:** `Blockchain` has no synchronization, but three kinds of thread
> use it at once: one listener thread per peer (blocks and transactions from
> the network), Jetty's request threads (API and dashboard) and the heartbeat.
> A probe written while planning (not committed) showed all three failure
> kinds on the current master:
>
> | Probe | Result |
> |-------|--------|
> | API-style reader iterates `getChain()` while blocks are appended | `ConcurrentModificationException` |
> | 4 threads call `minePendingTransactions` 50 times each | chain of 164 blocks instead of 200, **162 with the wrong index**, `isChainValid()` false: the chain is corrupted |
> | 4 threads each send 40 of Alice's 50 coins at once | more than one admitted to the mempool in **153 of 200** rounds |
>
> The second one needs nothing more than two "Mine block" clicks at the same
> time, or an API mine while a peer's block arrives.

---

## Goal

Every operation on a node's chain and mempool is atomic: readers see a
consistent snapshot, writers never interleave, and mining (which can take
seconds) does not block the API or the network while it searches for a nonce.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **20a** | `Blockchain` as a monitor + snapshot getters | `sprint20a/blockchain-monitor` | ⬜ Planned (#309, #310) |
| **20b** | Mining outside the lock (template, work, submit) | `sprint20b/mining-outside-lock` | ⬜ Planned (#311, #312) |
| **20c** | Consistent API / node views + end-to-end concurrency test | `sprint20c/concurrent-node` | ⬜ Planned (#313, #314) |

---

## Milestone 20a: `Blockchain` as a monitor + snapshot getters

### Deliverables

- [ ] Every public method that reads or changes chain, mempool, orphans,
      contracts or targets is `synchronized` on the `Blockchain` (one
      intrinsic lock; it is reentrant, so public methods may call each other)
- [ ] Getters return **copies** taken under the lock (`getChain()`,
      `getPendingTransactions()`, `getContracts()`, ...), never live views
- [ ] Immutable values (`ChainParams`, `MempoolPolicy`) need no lock
- [ ] Javadoc on the class: the threading model in a few lines
- [ ] Tests (`ConcurrencyTest`): the three probes above become assertions
      (no exception while reading, a valid chain with heights 0..n after
      concurrent mining, exactly one of four concurrent overspends admitted),
      plus concurrent `addBlock` from two "peers" with the same block

### Note

After 20a mining still runs inside the lock, so a slow mine blocks
everything else for its duration. That is correct, only slow; 20b fixes it.

---

## Milestone 20b: Mining outside the lock

### Deliverables

- [ ] `minePendingTransactions` in three steps, like a real miner:
  1. **template** (under the lock): tip, selected transactions, coinbase,
     target, timestamp
  2. **work** (no lock): the nonce search
  3. **submit** (under the lock): appended only if the tip is still the one
     the template was built on and the block passes the normal rules;
     otherwise the work is stale and the method builds a new template
     (bounded retries)
- [ ] The nonce search stops early when the tip moves (a cheap check every
      N nonces), so a peer's block does not leave us mining a dead template
- [ ] Tests: reads and `addTransaction` complete while a slow block is being
      mined; a peer block arriving mid-mine makes the template stale and our
      block is not appended (its transactions stay pending, nothing is lost);
      concurrent mines still yield a valid chain

---

## Milestone 20c: Consistent API / node views + end-to-end test

### Deliverables

- [ ] Responses built from several reads (e.g. `/api/network/status`,
      `/api/mempool`, `/api/wallet/{address}`) take them from one consistent
      snapshot (a single locked call), not from separate calls between which
      a block can land
- [ ] `Node` handlers: check-then-act sequences reviewed (block / chain
      requests, mempool sync) and made atomic where it matters
- [ ] End-to-end test: two nodes connected over TCP, several threads posting
      transactions and mining through the API of both; afterwards both chains
      are valid, identical, and the total supply equals the sum of coinbases
- [ ] Docs: threading model in `ARCHITECTURE.md`, deferred item removed

---

## Theory: why one lock

```
   peer threads           Jetty threads            heartbeat
  (blocks, txs)        (API, dashboard)          (chain height)
        \                     |                       /
         +-----------> [ Blockchain monitor ] <------+
                        one thread at a time
                        inside; copies out
```

A blockchain's state changes in big, all-or-nothing steps (a block applies
all its transactions or none), and almost every operation reads several
pieces of state together (balance = chain + pending; a block check = tip +
targets + balances + contracts). Fine-grained locks would have to be taken in
a fixed order everywhere and would still leave gaps between them. One monitor
is how Bitcoin Core started too (`cs_main`, a single lock around chain state),
and it is plenty for a node that handles a few requests per second. The one
long operation, the nonce search, does not need the state at all, which is
why 20b moves it out.

---

## Open decisions

- **`synchronized` methods** (recommended: simple, reentrant, no lock objects
  to leak) vs a `ReentrantReadWriteLock` (parallel reads, but every reader of
  `getBalance` scans the chain anyway and contention is low)
- **Stale work: retry** (recommended, bounded) vs return null and let the
  caller decide
- **Out of scope**: making `ChainStore` lock-free, parallel block validation

---

*Created: 2026-11-14 | Sprint 20 Planning*
