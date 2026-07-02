# Sprint 13: Web Dashboard - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | TBD |
| **Sprint End** | TBD |

---

## Milestone 13a: Runnable node entry point + static hosting - Pending

---

## Milestone 13b: Explorer view (blocks + network) - Pending

---

## Milestone 13c: Wallet + transaction actions - Pending

---

## Notes

- Second sprint of Phase 3 (API & Interface); Sprint 12 delivered the REST API
- Approach: vanilla HTML/JS/CSS, no build step, served by Javalin static hosting;
  polling refresh (WebSocket push is a possible later enhancement)
- Closes the "no runnable main" gap noted after Sprint 12: 13a boots Node +
  ApiServer as a single process
- Testing reality: the browser UI is verified by running it, so automated tests
  focus on the server side (static serving + the endpoints the UI consumes)

---

*Created: 2026-07-02*
