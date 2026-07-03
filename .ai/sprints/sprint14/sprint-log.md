# Sprint 14: Smart Contracts - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-03 |
| **Sprint End** | TBD |

---

## Milestone 14a: Script engine core - Pending

---

## Milestone 14b: Contracts on the chain - Pending

---

## Milestone 14c: API + dashboard integration - Pending

---

## Notes

- Third sprint of Phase 3 (API & Interface); Sprints 12-13 delivered the REST
  API and the web dashboard
- Approach: Bitcoin-Script-style stack-based interpreter (not an Ethereum-style
  VM) - small enough to build correctly in one sprint, fully unit-testable,
  and historically how programmable money appeared
- Two flagship conditions: hashlock (preimage reveals) and timelock (block
  height) - the building blocks of atomic swaps and vesting
- New package: `com.blocksmith.contract`; no new dependency

---

*Created: 2026-07-03*
