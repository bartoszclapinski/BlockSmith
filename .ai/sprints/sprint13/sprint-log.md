# Sprint 13: Web Dashboard - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-02 |
| **Sprint End** | 2026-07-03 |

---

## Milestone 13a: Runnable node entry point + static hosting - Complete ✅

**Issues:** #124 (entry point), #125 (static hosting), #126 (tests) | **PR:** #127

- `BlockSmithNode` main: boots a P2P `Node` and an `ApiServer` in one process,
  ports overridable via args, clean shutdown hook
- Javalin static hosting from `src/main/resources/public` (classpath); `/`
  serves the dashboard shell, `/api/*` keeps routing to the API
- `ApiStaticHostingTest` (3 tests): root shell, static asset, API coexistence

## Milestone 13b: Explorer view (blocks + network) - Complete ✅

**Issues:** #128 (explorer), #129 (tests) | **PR:** #130

- `app.js`: polls `/api/network/status`, `/api/network/peers`, `/api/blocks`
  every 4s; renders stat tiles, peer list, block cards (newest first, tip
  highlighted); all values via `textContent` (never `innerHTML`)
- `index.html` mount points + dark-theme `style.css`
- `ApiExplorerTest` (2 tests): mount points served, block JSON shape matches
  what the UI reads

## Milestone 13c: Wallet + transaction actions - Complete ✅

**Issues:** #131 (actions), #132 (tests) | **PR:** #133

- Actions section: create-wallet button, balance lookup, send-transaction
  form, mine button - wired to the Sprint 12 write endpoints
- `fetchJson` reads the API `{"error": ...}` envelope on non-2xx and surfaces
  the node's message inline; every action refreshes the explorer on success
- `ApiDashboardActionsTest` (2 tests): full action chain moves value end to
  end; rejected action returns an error envelope

---

## Results

- **Tests:** 180 -> 187 (+3 static hosting, +2 explorer, +2 dashboard actions)
- The project is now runnable as a single process with a browser UI:
  `java -cp target/classes com.blocksmith.BlockSmithNode` -> `http://localhost:7070/`
- An action taken in the browser propagates to peers over P2P via the
  existing broadcast paths

## Notes

- Second sprint of Phase 3 (API & Interface); Sprint 12 delivered the REST API
- Approach: vanilla HTML/JS/CSS, no build step, served by Javalin static hosting;
  polling refresh (WebSocket push is a possible later enhancement)
- Closed the "no runnable main" gap noted after Sprint 12: 13a boots Node +
  ApiServer as a single process
- Testing reality: the browser UI is verified by running it, so automated tests
  focus on the server side (static serving + the endpoints the UI consumes)

---

*Created: 2026-07-02 | Completed: 2026-07-03*
