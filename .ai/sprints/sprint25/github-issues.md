# Sprint 25: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 25a: Contract ids bound to their deploy

---

### A contract deploy can be front-run: the victim's funds are stuck for good

**Labels:** `sprint-25`, `milestone-25a`, `bug`

**Description:**

Reproduced on master (signatures required): Alice's signed hashlock deploy
of 30 waits in the mempool; Mallory signs a deploy of 0.1 with `PUSH 1` and
fee 1 to the same `CONTRACT:<id>`. It is mined first, the registry keeps
Mallory's contract (`putIfAbsent`), the address holds 30.1, a claim takes
0.1 and Alice's 30 can never be claimed.

**Fix:**
- `contractId = SHA-256(funder | lockingScript | amount | sequence)`;
  `buildDeploy` gives every deploy a random sequence
- Mempool and block rules: a deploy needs a sequence and the matching id
- A fee bump keeps the sequence, so the id stays

**Acceptance Criteria:**
- [ ] Mallory's deploy to Alice's address is refused
- [ ] Compiles with `mvn compile`

---

### Tests for bound contract ids

**Labels:** `sprint-25`, `milestone-25a`, `test`

**Description:**

**Test Cases:**
1. `frontRunDeploy_refused` (the probe, mempool)
2. `frontRunDeploy_inBlock_refused`
3. `deployWithoutSequenceOrWrongId_refused`
4. `bumpedDeploy_keepsContractId`
5. API: prepare / sign / submit a deploy, claim it

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 25b: Unique coinbases + index by position

---

### Two rewards in one millisecond share an id and tx_index loses one

**Labels:** `sprint-25`, `milestone-25b`, `bug`

**Description:**

Reproduced on master: two blocks whose coinbases for one miner were made in
the same millisecond are both accepted (balance 100), but the address
history shows one reward and a lookup of the first points at block 2.

**Fix:**
- Mining puts the block height in the coinbase sequence (BIP34); a coinbase
  sequence must equal the block index; old blocks without one stay valid
- `tx_index` keyed by (block, position); lookup returns the earliest;
  history lists all; an old index is rebuilt on open

**Acceptance Criteria:**
- [ ] Both rewards appear in the history
- [ ] Compiles with `mvn compile`

---

### Tests for unique coinbases and the position index

**Labels:** `sprint-25`, `milestone-25b`, `test`

**Description:**

**Test Cases:**
1. `sameIdRewards_bothInHistory_lookupFindsFirst` (the probe)
2. `rewardsInOneMillisecond_haveDifferentIds`
3. `coinbaseWithWrongHeight_refused`
4. `oldIndexSchema_rebuiltOnOpen`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 25c: Move funds to a new key + docs

---

### Move a wallet's funds to a new key from the dashboard

**Labels:** `sprint-25`, `milestone-25c`, `enhancement`

**Description:**

- Wallet drawer: "Move funds to a new key": new key, transfer of the
  spendable balance minus the suggested fee signed by the old key, old key
  labelled retired, new key active
- Keys migrated from plaintext are flagged; the dashboard suggests moving
- Docs: ARCHITECTURE, STATUS (three deferred items closed)

**Acceptance Criteria:**
- [ ] Funds move to a new key in one step, live
- [ ] Compiles with `mvn compile`

---

### Move-funds checks

**Labels:** `sprint-25`, `milestone-25c`, `test`

**Description:**

**Test Cases:**
1. Served-script checks for the move (signed by the old key, through the
   keystore, amount = spendable - fee)
2. Live: balance moves, old key retired, new key active, no JS errors

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-14 | Sprint 25 Planning*
