# Sprint 12: REST API - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-02 |
| **Sprint End** | TBD |

---

## Milestone 12a: HTTP server bootstrap + read/query endpoints - Pending

---

## Milestone 12b: Transaction + mining endpoints - Pending

---

## Milestone 12c: Wallet + network endpoints + error handling - Pending

---

## Notes

- First sprint of Phase 3 (API & Interface); Phase 2 completed at Sprint 11
- Continuing issue-first, milestone-per-branch workflow from Sprints 8-11
- **HTTP framework pending final confirmation**: Javalin (recommended, roadmap's
  first-named option) vs built-in `com.sun.net.httpserver` (zero-dependency) vs
  Spark. The Maven dependency + code are held until confirmed
- The API sits beside the P2P layer and drives the same Blockchain/Node, so
  actions taken over HTTP propagate to peers via the existing broadcast paths

---

*Created: 2026-07-02*
