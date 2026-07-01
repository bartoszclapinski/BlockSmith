# Sprint 11: Mempool Sync - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-01 |
| **Sprint End** | TBD |

---

## Milestone 11a: Transaction broadcast (NEW_TRANSACTION) - Pending

---

## Milestone 11b: Prune confirmed transactions - Pending

---

## Milestone 11c: Mempool sync on connect (GET_MEMPOOL / MEMPOOL) - Pending

---

## Notes

- Continuing issue-first, milestone-per-branch workflow from Sprints 8-10
- Sprint 10 wrapped at PR #92 (docs); genesis-determinism fix landed at PR #94
- `NewTransactionMessage` + `NEW_TRANSACTION` already exist and are registered
  in `MessageParser`; the missing piece is the Node handler (same start as
  NEW_BLOCK in Sprint 10)
- 11b (pruning) closes a real gap: mined transactions currently stay in the
  pending pool and would be re-gossiped/re-mined

---

*Created: 2026-07-01*
