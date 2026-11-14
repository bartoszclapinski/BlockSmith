# Sprint 20: Thread Safety - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | - |

---

## Planning probe (not committed)

Run on master before any change:
- reader iterating `getChain()` during appends: `ConcurrentModificationException`
- 4 threads x 50 mines: 164 blocks, 162 with the wrong index, chain invalid
- 4 concurrent 40-coin spends of 50: more than one admitted in 153/200 rounds

---

## Milestone 20a: `Blockchain` as a monitor + snapshot getters - Not started

## Milestone 20b: Mining outside the lock - Not started

## Milestone 20c: Consistent API / node views + end-to-end test - Not started

---

*Sprint 20 Log*
