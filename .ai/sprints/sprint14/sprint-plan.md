# Sprint 14: Smart Contracts

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 14 |
| **Title** | Smart Contracts |
| **Phase** | Phase 3: API & Interface |
| **Status** | Planning |
| **Depends On** | Sprint 13 Complete (Web Dashboard) |

> **Approach (recommended):** a **Bitcoin-Script-style stack-based interpreter**,
> not an Ethereum-style VM. Funds are locked in a contract by a small script;
> whoever presents data that makes the script evaluate to true can claim them.
> A stack machine is small enough to build correctly in one sprint, is fully
> unit-testable with no networking, and teaches "programmable money" the way it
> historically appeared. No new dependency - plain Java.

---

## Goal

Make money programmable. A user can lock funds behind a condition instead of a
recipient address: a **hashlock** ("claimable by whoever reveals the preimage
of this SHA-256 hash") or a **timelock** ("claimable only after block height
N"). A small script VM evaluates the condition; the chain stores the contract
and enforces the rules; the API and dashboard let a user deploy and claim
contracts end to end.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **14a** | Script engine core | `sprint14a/script-engine` | Pending |
| **14b** | Contracts on the chain | `sprint14b/chain-contracts` | Pending |
| **14c** | API + dashboard integration | `sprint14c/contracts-api` | Pending |

---

## Milestone 14a: Script engine core

### Deliverables

- [ ] `ScriptVM` - stack-based interpreter: executes a script against a stack,
      result is the truthiness of the top of the stack
- [ ] Opcodes: data push, `DUP`, `DROP`, `SHA256`, `EQUAL`, `EQUALVERIFY`,
      `VERIFY`, `ADD`, `SUB`, `GREATERTHAN`, `LESSTHAN`, `CHECKLOCKTIME`
- [ ] Script parsing: text form (e.g. `"SHA256 PUSH <hash> EQUAL"`) to opcode
      list; unknown opcode / malformed script fails safely (script = false)
- [ ] Execution context: current block height (for `CHECKLOCKTIME`)
- [ ] Failure semantics: stack underflow, overflow limits, oversized scripts
      all evaluate to false - the VM never throws to the caller
- [ ] Tests: opcode-by-opcode + hashlock and timelock script scenarios

### Why this is first

The VM is pure logic with zero dependencies on the chain. Building and testing
it in isolation means 14b integrates a known-good component.

---

## Milestone 14b: Contracts on the chain

### Deliverables

- [ ] `Contract` model: id, locking script, locked amount, funder, status
      (OPEN / CLAIMED), creation block height
- [ ] Deploy: a funder locks an amount behind a script; the balance rules treat
      locked funds as spent by the funder and owned by no one
- [ ] Claim: a claimer presents unlocking data; the node runs
      `unlocking data + locking script` through the VM; on true, the locked
      amount is credited to the claimer
- [ ] `Blockchain` integration: contract registry, deploy/claim recorded in
      blocks so every node reaches the same contract state
- [ ] Validation: cannot deploy more than the funder's balance, cannot claim a
      CLAIMED contract, failed scripts leave the contract untouched
- [ ] Tests: hashlock deploy/claim, timelock rejected-before/accepted-after,
      double-claim rejected, balance accounting

---

## Milestone 14c: API + dashboard integration

### Deliverables

- [ ] `POST /api/contracts` - deploy (funder, amount, locking script)
- [ ] `GET /api/contracts` and `GET /api/contracts/{id}` - inspect
- [ ] `POST /api/contracts/{id}/claim` - claim with unlocking data
- [ ] JSON error envelope for rejected deploys/claims (consistent with Sprint 12)
- [ ] Dashboard: Contracts panel - list contracts with status, deploy form,
      claim form; errors surfaced inline (consistent with Sprint 13)
- [ ] Tests (server-side): deploy -> claim chain over HTTP, error envelopes

---

## Theory: Programmable money

```
A plain transaction says:   "move X from A to B"
A contract says:            "move X from A to WHOEVER can make this script true"

  Deploy:  funder locks 25 BSC behind   SHA256 PUSH <H> EQUAL
  Claim:   claimer presents <preimage>

  VM runs:   PUSH <preimage> | SHA256 PUSH <H> EQUAL
  Stack:     [preimage] -> [sha256(preimage)] -> [sha256(preimage), H] -> [true]

  Top of stack is true -> the 25 BSC belongs to the claimer.

This is exactly how Bitcoin locks every coin (P2PKH is just a 5-opcode script).
Hashlocks are the building block of atomic swaps and Lightning; timelocks power
vesting and refunds. An Ethereum-style VM generalizes the idea; the principle
is the same: the chain enforces a program, not a signature alone.
```

---

## Dependencies

- Sprint 12 REST API (`ApiServer`) and Sprint 13 dashboard for 14c
- `HashUtil` (SHA-256) for hashlock scripts
- No new dependency; the VM is plain Java

---

## Open decisions

- **Script form**: text opcodes parsed to an enum list (recommended) vs byte
  encoding - text keeps it readable and educational
- **Claim transport**: dedicated deploy/claim records in blocks (recommended)
  vs overloading `Transaction` - dedicated records keep Transaction untouched
- **Locktime unit**: block height (recommended) vs timestamp - height is
  deterministic and testable

---

*Created: 2026-07-03 | Sprint 14 Planning*
