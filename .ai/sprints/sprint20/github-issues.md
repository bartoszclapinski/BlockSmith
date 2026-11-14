# Sprint 20: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 20a: `Blockchain` as a monitor + snapshot getters

---

### Blockchain is not thread-safe: concurrent mining corrupts the chain

**Labels:** `sprint-20`, `milestone-20a`, `bug`

**Description:**

Peer listener threads, Jetty request threads and the heartbeat all use one
`Blockchain` without synchronization. Reproduced on master:
- reading `getChain()` while blocks are appended: `ConcurrentModificationException`
- 4 threads x 50 `minePendingTransactions`: 164 blocks, 162 with the wrong
  index, `isChainValid()` false
- 4 concurrent 40-coin spends of a 50 balance: more than one admitted in
  153 of 200 rounds

**Fix:**
- `synchronized` public methods that touch chain, mempool, orphans,
  contracts or targets (one reentrant monitor)
- getters return copies taken under the lock, never live views
- class Javadoc describing the threading model

**Acceptance Criteria:**
- [ ] The three reproductions pass
- [ ] Compiles with `mvn compile`

---

### Tests for Blockchain thread safety

**Labels:** `sprint-20`, `milestone-20a`, `test`

**Description:**

**Test Cases:**
1. `readWhileAppending_noException`
2. `concurrentMining_validChain`
3. `concurrentOverspend_onlyOneAdmitted`
4. `sameBlockFromTwoPeers_appendedOnce`
5. `getters_returnSnapshots`

**Acceptance Criteria:**
- [ ] Tests added and passing (and failing on the old code)
- [ ] `mvn test` green

---

## Milestone 20b: Mining outside the lock

---

### Mine outside the lock: template, work, submit

**Labels:** `sprint-20`, `milestone-20b`, `enhancement`

**Description:**

After 20a a slow mine holds the lock for the whole nonce search. Split it:
- template under the lock (tip, transactions, coinbase, target, timestamp)
- nonce search without the lock, stopping early if the tip moves
- submit under the lock only if the tip is unchanged and the block passes
  the rules; stale work builds a new template (bounded retries)

**Acceptance Criteria:**
- [ ] Reads and `addTransaction` are not blocked by a slow mine
- [ ] A peer block mid-mine makes our work stale; nothing is lost
- [ ] Compiles with `mvn compile`

---

### Tests for mining outside the lock

**Labels:** `sprint-20`, `milestone-20b`, `test`

**Description:**

**Test Cases:**
1. `slowMine_doesNotBlockReadsOrTransactions`
2. `peerBlockMidMine_staleTemplateNotAppended`
3. `staleWork_transactionsStayPending`
4. `concurrentMines_stillValidChain`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 20c: Consistent API / node views + end-to-end test

---

### Consistent snapshots in the API and node handlers

**Labels:** `sprint-20`, `milestone-20c`, `enhancement`

**Description:**

- Multi-value responses (`/api/network/status`, `/api/mempool`,
  `/api/wallet/{address}`) from one locked snapshot
- `Node` check-then-act sequences reviewed and made atomic where needed
- Threading model in `ARCHITECTURE.md`; deferred item removed from `STATUS.md`

**Acceptance Criteria:**
- [ ] No response mixes state from before and after a block
- [ ] Compiles with `mvn compile`

---

### End-to-end concurrency test with two nodes

**Labels:** `sprint-20`, `milestone-20c`, `test`

**Description:**

**Test Cases:**
1. `twoNodes_concurrentApiTraffic_convergeToSameValidChain`
2. `totalSupply_equalsSumOfCoinbases`
3. `statusSnapshot_isConsistent`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-14 | Sprint 20 Planning*
