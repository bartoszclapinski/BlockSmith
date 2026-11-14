# Sprint 22: Required Signatures - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | - |

---

## Planning probe (not committed)

On master, Victim funded with 50: an unsigned transfer from Victim and a
transfer signed with Mallory's own key (sender = Victim) were both accepted;
after one block Mallory 80, Victim 20, `isChainValid()` true. Test usage:
178 `new Transaction(` calls in 26 test files; 35 `new Blockchain()` and 47
`new Node(port)` call sites.

---

## Milestone 22a: Consensus: the sender must sign - Not started

## Milestone 22b: API: prepare, sign elsewhere, submit - Not started

## Milestone 22c: Dashboard: keys and signing in the browser - Not started

---

*Sprint 22 Log*
