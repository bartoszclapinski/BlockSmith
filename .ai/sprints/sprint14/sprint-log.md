# Sprint 14: Smart Contracts - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-03 |
| **Sprint End** | 2026-07-03 |

---

## Milestone 14a: Script engine core - Complete ✅

**Issues:** #136 (VM), #137 (tests) | **PR:** #138

- New `com.blocksmith.contract` package
- `ScriptOp` - 12-opcode set: `PUSH`, `DUP`, `DROP`, `SHA256`, `EQUAL`,
  `EQUALVERIFY`, `VERIFY`, `ADD`, `SUB`, `GREATERTHAN`, `LESSTHAN`,
  `CHECKLOCKTIME`
- `ScriptVM` - stack machine, early-Bitcoin execution model (unlocking +
  locking script run once; success = truthy top of stack). **Never throws**:
  unknown opcodes, underflow, non-numeric arithmetic, oversized scripts all
  evaluate to false (consensus safety - a crashing script would be a DoS vector)
- `ScriptVMTest` (21 tests): every opcode, hashlock/timelock scenarios and
  their composition, exhaustive failure semantics

## Milestone 14b: Contracts on the chain - Complete ✅

**Issues:** #139 (deploy/claim), #140 (tests) | **PR:** #141

- `Contract` + `ContractStatus` (OPEN/CLAIMED)
- Contracts modelled as transactions: deploy sends TO `CONTRACT:<id>` with a
  locking script, claim sends FROM it with unlocking data - so the existing
  balance model debits the funder and credits the claimer with no changes to
  `getBalance`, and gossip/mining need no special cases
- Contract registry is **derived state**: every block-append path scans for
  deploys/claims, so all nodes converge on identical contract state
- `Blockchain`: `deployContract`, `claimContract`, `getContract(s)`,
  `registerContracts`, and `addTransaction` rules (script-less transfer to a
  contract address rejected; claim validated via `ScriptVM` at next-block
  height); double-claim protection falls out of the pending-outgoing check
- `ChainContractTest` (9 tests): lifecycle, both lock types, every rejection
  path, and cross-node convergence

## Milestone 14c: API + dashboard integration - Complete ✅

**Issues:** #142 (endpoints + panel), #143 (tests) | **PR:** #144

- `ApiServer`: `POST /api/contracts`, `GET /api/contracts[/{id}]`,
  `POST /api/contracts/{id}/claim` - deploys/claims broadcast to peers,
  rejections return the standard JSON error envelope
- Dashboard Contracts panel: deploy + claim forms (a deploy hands its id to
  the claim form), status-coloured contract cards, inline errors
- `ApiContractsTest` (3 tests): HTTP lifecycle, list reflects deploys,
  rejected deploy/claim/missing-fields return error envelopes
- **Verified live** against a running node: deploy locks funds, correct
  preimage settles, wrong preimage rejected with an error envelope

---

## Results

- **Tests:** 217 -> 220 in this sprint's final milestone; total across the
  sprint 187 -> 220 (+21 VM, +9 chain, +3 API)
- Programmable money end to end: lock funds behind a hashlock or timelock,
  claim by satisfying the script, all drivable from the dashboard
- **Tooling fix:** `mvn exec:java` now runs `BlockSmithNode` (node + dashboard)
  by default; the demo runs via
  `mvn exec:java -Dexec.mainClass=com.blocksmith.BlockSmithDemo`. The jar
  manifest main class is now `BlockSmithNode`. (The old README instruction
  `java -cp target/classes` omitted the dependency jars and did not run.)

## Notes

- Third sprint of Phase 3 (API & Interface); Sprints 12-13 delivered the REST
  API and the web dashboard
- Approach: Bitcoin-Script-style stack-based interpreter (not an Ethereum-style
  VM) - small enough to build correctly in one sprint, fully unit-testable
- Two flagship conditions: hashlock (preimage reveals) and timelock (block
  height) - the building blocks of atomic swaps and vesting
- New package: `com.blocksmith.contract`; no new dependency

---

*Created: 2026-07-03 | Completed: 2026-07-03*
