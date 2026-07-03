# Sprint 14: GitHub Issues

> Copy each issue to GitHub. Actual issue numbers are assigned on creation
> (PRs consume the same number space, so they will not be contiguous).

---

## Milestone 14a: Script engine core

---

### Implement the stack-based script VM

**Labels:** `sprint-14`, `milestone-14a`, `enhancement`

**Description:**

`ScriptVM` in a new `com.blocksmith.contract` package:

- Stack machine executing an opcode list; result = truthiness of the top of
  the stack after execution
- Opcodes: data push, `DUP`, `DROP`, `SHA256`, `EQUAL`, `EQUALVERIFY`,
  `VERIFY`, `ADD`, `SUB`, `GREATERTHAN`, `LESSTHAN`, `CHECKLOCKTIME`
- Script parsing from text form; unknown opcodes / malformed scripts fail
  safely (evaluate to false)
- Execution context carries the current block height for `CHECKLOCKTIME`
- The VM never throws to the caller: underflow, oversized scripts, and bad
  data all evaluate to false

**Acceptance Criteria:**
- [ ] Hashlock script evaluates true with the right preimage, false otherwise
- [ ] Timelock script false before height N, true at/after
- [ ] Malformed input never throws
- [ ] Compiles with `mvn compile`

---

### Tests for the script VM

**Labels:** `sprint-14`, `milestone-14a`, `test`

**Description:**

**Test Cases:**
1. Opcode-by-opcode behavior (push, DUP, DROP, SHA256, EQUAL/VERIFY variants,
   arithmetic, comparisons)
2. `hashlockScript_trueOnlyWithCorrectPreimage`
3. `timelockScript_respectsBlockHeight`
4. Failure semantics: stack underflow, unknown opcode, empty script,
   oversized script -> false, no exception

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 14b: Contracts on the chain

---

### Add contract deploy/claim to the blockchain

**Labels:** `sprint-14`, `milestone-14b`, `enhancement`

**Description:**

- `Contract` model: id, locking script, locked amount, funder, status
  (OPEN / CLAIMED), creation block height
- Deploy: locks part of the funder's balance behind a script; balance rules
  treat locked funds as spent by the funder
- Claim: unlocking data + locking script run through `ScriptVM`; on true the
  amount is credited to the claimer, contract becomes CLAIMED
- Deploys/claims recorded in blocks so all nodes converge on contract state
- Validation: insufficient funder balance, double claim, and failed scripts
  are rejected without side effects

**Acceptance Criteria:**
- [ ] Hashlock: deploy then claim with the preimage moves the funds
- [ ] Timelock: claim rejected before height N, accepted after
- [ ] Double claim rejected
- [ ] Compiles with `mvn compile`

---

### Tests for chain contracts

**Labels:** `sprint-14`, `milestone-14b`, `test`

**Description:**

**Test Cases:**
1. `deploy_locksFunderBalance`
2. `claim_withCorrectPreimage_creditsClaimer`
3. `claim_withWrongPreimage_rejected`
4. `timelockClaim_beforeHeight_rejected_afterHeight_accepted`
5. `doubleClaim_rejected`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 14c: API + dashboard integration

---

### Contract endpoints + dashboard panel

**Labels:** `sprint-14`, `milestone-14c`, `enhancement`

**Description:**

- `POST /api/contracts` - deploy (funder, amount, locking script)
- `GET /api/contracts`, `GET /api/contracts/{id}` - inspect
- `POST /api/contracts/{id}/claim` - claim with unlocking data
- JSON error envelope for rejected deploys/claims (Sprint 12 style)
- Dashboard Contracts panel: list with status, deploy form, claim form,
  errors surfaced inline (Sprint 13 style)

**Acceptance Criteria:**
- [ ] A user can deploy and claim a contract from the UI
- [ ] Errors are shown to the user
- [ ] Compiles with `mvn compile`

---

### Tests for contract endpoints

**Labels:** `sprint-14`, `milestone-14c`, `test`

**Description:**

**Test Cases:**
1. `contractLifecycle_overHttp` (deploy -> inspect -> claim -> balances move)
2. `rejectedDeployAndClaim_returnErrorEnvelope`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Summary

| Milestone | Issues | Tests |
|-----------|--------|-------|
| 14a: Script engine core | 2 | ~12 tests |
| 14b: Contracts on the chain | 2 | ~5 tests |
| 14c: API + dashboard | 2 | ~2 tests |
| **Total** | **6 issues** | **~19 tests** |

> 14a is the test-heavy milestone: the VM is pure logic, so it gets exhaustive
> unit coverage. 14c follows the Sprint 13 pattern - the browser UI is verified
> by running it; automated tests cover the server side.

---

*Created: 2026-07-03 | Sprint 14 Planning*
