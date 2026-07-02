# Sprint 13: Web Dashboard

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 13 |
| **Title** | Web Dashboard |
| **Phase** | Phase 3: API & Interface |
| **Status** | Planning |
| **Depends On** | Sprint 12 Complete (REST API) |

> **Approach (recommended):** vanilla HTML/JS/CSS with **no framework and no
> build step**, served directly by Javalin's static-file handler. Refresh via
> **polling** (re-fetch the REST API every few seconds) rather than WebSockets -
> simpler and enough for a demo; WebSocket push can be a later enhancement. No
> new dependency is added.

---

## Goal

Put a human face on the node. A browser dashboard, served by the node itself,
lets a user watch the chain grow, inspect blocks and network state, create a
wallet, check a balance, submit a transaction, and mine - all through the REST
API from Sprint 12. By the end of this sprint the project is demoable end to
end: start a node, open a browser, and drive the blockchain.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **13a** | Runnable node entry point + static hosting | `sprint13a/node-runner` | Pending |
| **13b** | Explorer view (blocks + network) | `sprint13b/explorer-ui` | Pending |
| **13c** | Wallet + transaction actions | `sprint13c/wallet-ui` | Pending |

---

## Milestone 13a: Runnable node entry point + static hosting

### Deliverables

- [ ] A runnable entry point (e.g. `BlockSmithNode` main) that starts a `Node`
      and an `ApiServer` together, with ports from `NetworkConfig`
- [ ] Configure Javalin to serve static files (e.g. from `src/main/resources/public`)
- [ ] `GET /` serves the dashboard shell (`index.html`)
- [ ] Tests (server-side): `GET /` returns 200 HTML; a static asset is served;
      the API still responds alongside static hosting

### Why this is first

There is currently no single command that boots a node with its API, and no
static hosting. The dashboard needs both, so this is the foundation.

---

## Milestone 13b: Explorer view (blocks + network)

### Deliverables

- [ ] `index.html` + `app.js` + `style.css` under the static folder
- [ ] Blocks view: fetch `GET /api/blocks`, render index / hash / prevHash /
      tx count / nonce; highlight the latest block
- [ ] Network panel: fetch `GET /api/network/status` and `GET /api/network/peers`
      (chain length, pending count, peers)
- [ ] Auto-refresh via polling (configurable interval)
- [ ] Tests (server-side): the served `index.html` includes the expected mount
      points; endpoints the UI depends on return the expected shape

### Note on testing

The UI itself is verified by running the node and opening the browser. Automated
coverage focuses on the server side (static serving + the endpoints the UI
consumes), so this sprint has fewer unit tests than the backend sprints.

---

## Milestone 13c: Wallet + transaction actions

### Deliverables

- [ ] Create-wallet button -> `POST /api/wallet/create`, show the new address
- [ ] Balance lookup -> `GET /api/wallet/{address}`
- [ ] Submit-transaction form (sender, recipient, amount) -> `POST /api/transactions`
- [ ] Mine button (miner address) -> `POST /api/mine`, then refresh the chain
- [ ] Surface API errors (the JSON error envelope) in the UI
- [ ] Tests (server-side): the action endpoints behave as the UI expects
      (already covered by Sprint 12 tests; add any dashboard-specific glue)

---

## Theory: A node that serves its own UI

```
Sprint 12 gave the node an HTTP/JSON API. Sprint 13 serves a small web app from
that SAME Javalin instance:

  Browser --GET /-------------------> index.html (static)
  Browser --GET /api/blocks--------->  ApiServer -> Blockchain
  Browser --POST /api/transactions->  ApiServer -> addTransaction -> broadcast

Because the page is served from the same origin as the API, there is no CORS to
configure. The dashboard is just another API client - the difference is a human
is driving it. Actions still propagate to peers via the P2P broadcast paths.
```

---

## Dependencies

- Sprint 12 REST API (`ApiServer` and all endpoints)
- Javalin static-file hosting (already on the classpath)
- No new dependency; no JS build tooling

---

## Open decisions

- **Frontend**: vanilla HTML/JS/CSS, no build (recommended) vs a framework
- **Real-time**: polling (recommended) vs WebSocket push
- **Static location**: `src/main/resources/public` (bundled on the classpath)

---

*Created: 2026-07-02 | Sprint 13 Planning*
