# Sprint 28: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 28a: Recorder

---

### Demo recorder: a scripted session on a real node, every answer recorded

**Labels:** `sprint-28`, `milestone-28a`, `enhancement`

**Description:**

Measured on master: the project site has no demo (`/BlockSmith/demo/` is
404) and the dashboard cannot run on a static host (23 `/api/...` paths, all
answered by the Java node). One state of a signed 33-block chain is 308
answers, 322,387 bytes; ten states with repeated parts stored once are
726,840 bytes (90,673 gzipped).

**Add:**
- `com.blocksmith.demo.DemoRecorder`: real nodes (`Node` + `ApiServer`,
  signatures required); a scripted session over the node's own HTTP API
  (prepare, sign with a `Wallet`, submit); after every step, every answer the
  dashboard can ask for
- The session: wallets and first blocks; transfers at different fees; a
  hashlock contract deployed and claimed; a multisig contract; a second node
  joining and mining; a reorg
- `DemoRecording`: frames (caption + path -> answer), repeated parts stored
  once; reading gives back exactly the node's JSON
- A `main` that records at a real pace with the default parameters

**Acceptance Criteria:**
- [ ] Nothing in the node, the API or the dashboard changes
- [ ] Compiles with `mvn compile`

---

### Tests for the demo recorder

**Labels:** `sprint-28`, `milestone-28a`, `test`

**Description:**

**Test Cases:**
1. `session_endsInTheStateItDescribes` (balances, contracts, peers, reorg count)
2. `everyFrame_holdsEveryFixedPath_andACaption`
3. `lastFrame_equalsTheNodesAnswers_pathByPath`
4. `recording_roundTrips_andStoresRepeatedPartsOnce`
5. `feedEvents_areAllInTheFrames` (block, pending, deploy, claim, peer, reorg)

**Acceptance Criteria:**
- [ ] All tests pass with `mvn test`

---

## Milestone 28b: Player

---

### Demo player: the dashboard answered from a recording

**Labels:** `sprint-28`, `milestone-28b`, `enhancement`

**Description:**

**Add:**
- `index.html`: style and script by relative path
- `app.js`: demo mode behind `window.BlockSmithDemo`: `fetchJson` asks it;
  the recording's clock for relative times; signing refused before the
  passphrase dialog; the recording's active wallet; the tour pauses the
  replay
- `demo.js`, `demo.css`: a bar (caption, position, pause / step / replay,
  "Run it yourself"); lookup in the current frame; a clear refusal for
  everything that would change the chain; the node's own "not found" for a
  search outside the recording; a link to an explorer view opens the end of
  the recording
- `DemoSite`: writes `docs/demo/`

**Acceptance Criteria:**
- [ ] The node's dashboard behaves as before (no `BlockSmithDemo`, no change)
- [ ] Compiles with `mvn compile`

---

### Tests for the demo player and the built site

**Labels:** `sprint-28`, `milestone-28b`, `test`

**Description:**

**Test Cases:**
1. `builtPage_isTheDashboardPlusThePlayer`
2. `recording_answersEveryGetPathTheDashboardAsks`
3. `everyPostPath_isRefusedByThePlayer`
4. `demoMode_neverReachesTheKeystoreOrTheNetwork` (static checks of the seams)
5. `nodeStillServesTheDashboard_withRelativeAssets`

**Acceptance Criteria:**
- [ ] All tests pass with `mvn test`

---

## Milestone 28c: Published demo, live check, docs

---

### Publish the recorded demo: real recording, links, docs

**Labels:** `sprint-28`, `milestone-28c`, `enhancement`

**Description:**

**Add:**
- The real recording (default parameters, real pace) under `docs/demo/`
- A link to the demo on the project site and in the README
- Docs: ARCHITECTURE (recorder, format, player), README (how to re-record)

**Acceptance Criteria:**
- [ ] `https://bartoszclapinski.github.io/BlockSmith/demo/` serves the demo
      after the merge (Pages keeps serving `docs/` from master)

---

### Staleness guard and live check of the published demo

**Labels:** `sprint-28`, `milestone-28c`, `test`

**Description:**

**Test Cases:**
1. `committedDemo_matchesTheDashboardSources`
2. `committedRecording_hasTheFieldsTheApiHasToday`
3. Live, static server under a sub-path: replay to the end, feed, every
   explorer view, search hit and miss, direct link, refused actions, tour,
   390 px, both themes, no page errors, no request to `/api`
4. Live on Pages after the merge

**Acceptance Criteria:**
- [ ] All tests pass with `mvn test`
- [ ] The live checks are recorded in the sprint log
