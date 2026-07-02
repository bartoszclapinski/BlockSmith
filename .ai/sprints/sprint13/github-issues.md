# Sprint 13: GitHub Issues

> Copy each issue to GitHub. Actual issue numbers are assigned on creation
> (PRs consume the same number space, so they will not be contiguous).

---

## Milestone 13a: Runnable node entry point + static hosting

---

### Add a runnable node entry point (Node + ApiServer)

**Labels:** `sprint-13`, `milestone-13a`, `enhancement`

**Description:**

Add a `main` that starts a `Node` and an `ApiServer` together so the project is
runnable as a single process.

- `BlockSmithNode` (or similar) with a `main` that boots both
- Ports from `NetworkConfig` (P2P + API); optionally overridable via args
- Clean shutdown hook

**Acceptance Criteria:**
- [ ] Running the entry point starts the P2P node and the REST API
- [ ] Compiles with `mvn compile`

---

### Serve static files + dashboard shell from Javalin

**Labels:** `sprint-13`, `milestone-13a`, `enhancement`

**Description:**

- Configure Javalin static-file hosting (e.g. `src/main/resources/public`)
- `GET /` serves `index.html` (a placeholder shell for now)
- Ensure `/api/*` still routes to the API alongside static hosting

**Acceptance Criteria:**
- [ ] `GET /` returns 200 with HTML
- [ ] A static asset (e.g. `/style.css`) is served
- [ ] API endpoints still work
- [ ] Compiles with `mvn compile`

---

### Tests for entry point + static hosting

**Labels:** `sprint-13`, `milestone-13a`, `test`

**Description:**

**Test Cases:**
1. `root_servesHtmlShell`
2. `staticAsset_isServed`
3. `apiStillRespondsAlongsideStatic`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 13b: Explorer view (blocks + network)

---

### Build the explorer dashboard (blocks + network panel)

**Labels:** `sprint-13`, `milestone-13b`, `enhancement`

**Description:**

`index.html` + `app.js` + `style.css`:
- Blocks view from `GET /api/blocks` (index, hash, prevHash, tx count, nonce),
  latest highlighted
- Network panel from `GET /api/network/status` and `GET /api/network/peers`
- Auto-refresh via polling (configurable interval)

**Acceptance Criteria:**
- [ ] Dashboard renders the chain and network state
- [ ] Auto-refreshes without a manual reload
- [ ] Compiles with `mvn compile`

---

### Tests for the explorer (server-side)

**Labels:** `sprint-13`, `milestone-13b`, `test`

**Description:**

**Test Cases:**
1. `indexHtml_containsExpectedMountPoints`
2. `blocksEndpoint_shapeMatchesUiExpectations` (fields the UI reads exist)

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 13c: Wallet + transaction actions

---

### Add wallet + transaction actions to the dashboard

**Labels:** `sprint-13`, `milestone-13c`, `enhancement`

**Description:**

- Create-wallet button -> `POST /api/wallet/create`, show the address
- Balance lookup -> `GET /api/wallet/{address}`
- Submit-transaction form -> `POST /api/transactions`
- Mine button -> `POST /api/mine`, then refresh the chain
- Surface API error envelopes in the UI

**Acceptance Criteria:**
- [ ] A user can create a wallet, check a balance, submit a tx, and mine from the UI
- [ ] Errors are shown to the user
- [ ] Compiles with `mvn compile`

---

### Tests for dashboard actions (server-side glue)

**Labels:** `sprint-13`, `milestone-13c`, `test`

**Description:**

**Test Cases:**
1. `dashboardActionEndpoints_behaveAsExpected` (any new glue; core paths already
   covered by Sprint 12 tests)

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Summary

| Milestone | Issues | Tests |
|-----------|--------|-------|
| 13a: Entry point + static hosting | 3 | 3 tests |
| 13b: Explorer view | 2 | ~2 tests |
| 13c: Wallet + tx actions | 2 | ~1 test |
| **Total** | **7 issues** | **~6 tests** |

> Test counts are lower than the backend sprints: the browser UI is verified by
> running it, so automated coverage focuses on the server side.

---

*Created: 2026-07-02 | Sprint 13 Planning*
