# Sprint 25: Loose Ends: Contract IDs, Coinbase Height, Key Rotation - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | - |

---

## Planning probe (not committed)

1. Contract front-run (signatures required): Alice's signed hashlock deploy
   of 30 waited in the mempool; Mallory signed a deploy of 0.1 with
   `PUSH 1` and fee 1 to the same `CONTRACT:<id>`. After one block the
   registry held Mallory's contract (0.1, `PUSH 1`), the address 30.1; a
   claim took 0.1, the contract became CLAIMED and 30 stayed at the address.
2. Two blocks with coinbases for one miner made in the same millisecond
   (same id): both accepted, balance 100, history 1 entry, lookup of the
   first reward returned block 2.

---

## Milestone 25a: Contract ids bound to their deploy - Not started

## Milestone 25b: Unique coinbases + index by position - Not started

## Milestone 25c: Move funds to a new key + docs - Not started

---

*Sprint 25 Log*
