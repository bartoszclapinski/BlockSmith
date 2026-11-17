# Sprint 28: Recorded Demo

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 28 |
| **Title** | Recorded Demo (the dashboard on GitHub Pages) |
| **Phase** | Hardening (after the roadmap); reopens the project closed at v4.8.0 |
| **Status** | ✅ Complete (2026-11-17) |
| **Depends On** | Sprint 13 (dashboard), Sprint 22 (keys in the browser), Sprint 27 (explorer, release v4.8.0) |

> **Why:** the project site (GitHub Pages, `docs/`) describes BlockSmith with
> three screenshots; nobody can try the dashboard without Java, Maven and a
> clone. A probe on master (not committed):
>
> 1. **There is no demo.** `https://bartoszclapinski.github.io/BlockSmith/demo/`
>    is 404. The site has a Proof-of-Work toy and screenshots, nothing that
>    shows the dashboard working.
> 2. **The dashboard cannot be copied to a static host.** `index.html` loads
>    `/style.css` and `/app.js`; on Pages those resolve to the root of
>    `github.io` (404 each, checked). `app.js` asks 23 `/api/...` paths, every
>    one of them answered by the Java node; `/api/network/status` on Pages is
>    404, so the page would show "node unreachable" and nothing else.
> 3. **A recording is small enough to ship.** A signed chain of 33 blocks,
>    served by `ApiServer`: every answer the dashboard can ask for in one
>    state (status, peers, contracts, mempool, fees, totals, block pages,
>    every block by height and by hash, every transaction, every address
>    page, every search) is 308 answers, 322,387 bytes. Ten such states are
>    2,971,985 bytes as they are; 1,816,535 with identical answers stored
>    once; 726,840 with every repeated part (a transaction inside a block,
>    inside an address page, inside its own page) stored once, 90,673 bytes
>    gzipped.
> 4. **The page is driven by change.** The activity feed is derived from the
>    difference between two polls; on one frozen state it would say
>    "Dashboard connected" for ever. A demo needs the states in between.

---

## Goal

Anyone can open a link and see the BlockSmith dashboard working: blocks
arrive, transactions wait and confirm, a contract is deployed and claimed, a
peer joins, the chain reorganises, and every block, transaction and address
opens in the explorer. Every number on the screen is an answer a real node
gave; nothing is simulated in JavaScript. What a recording cannot do (mine,
send, deploy) says so and points to running the node.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **28a** | Recorder | `sprint28a/demo-recorder` | ✅ PR #404 (#397, #398) |
| **28b** | Player | `sprint28b/demo-player` | ✅ PR #405 (#399, #400) |
| **28c** | Published demo, live check, docs | `sprint28c/demo-live` | ✅ PR #406 (#401, #402) |

---

## Milestone 28a: Recorder

### Deliverables

- [x] `com.blocksmith.demo.DemoRecorder`: starts real nodes (`Node` +
      `ApiServer`, signatures required), plays a scripted session against
      the node's own HTTP API the way a browser does (prepare, sign with a
      `Wallet`, submit) and, after every step, records every answer the
      dashboard can ask for
- [x] The session: wallets and first blocks; transfers at different fees
      (pending, then confirmed in fee order); a hashlock contract deployed
      and claimed; a multisig contract; a second node joining and mining; a
      reorg (a longer branch replaces a block, its transaction goes back to
      the mempool)
- [x] `DemoRecording`: the file format. Frames (a caption + path -> answer),
      with every repeated part of the answers stored once; reading it back
      gives exactly the JSON the node sent
- [x] Runnable: `mvn exec:java -Dexec.mainClass=com.blocksmith.demo.DemoRecorder`
      at a real pace (blocks seconds apart, the real difficulty); tests run
      the same session without the waits on an easy target
- [x] Tests: the session ends in the state it describes; every frame holds
      every fixed path; the last frame equals what the node answers at that
      moment, path by path; the file round-trips

---

## Milestone 28b: Player

### Deliverables

- [x] `index.html` loads its style and script by relative path (the node
      serves the same page at `/`)
- [x] `app.js` demo mode behind one object (`window.BlockSmithDemo`):
      `fetchJson` asks it instead of the network; the clock used for "3s
      ago" is the recording's; signing is refused before the passphrase
      dialog; the active wallet is the recording's; the tour pauses the
      replay
- [x] `demo.js` + `demo.css` (the player): a bar with the step's caption,
      position, pause / step / replay and "Run it yourself"; answers looked
      up in the current frame; anything that would change the chain is
      refused with one sentence that says why and what to do; a search for
      something not in the recording answers like the node (recorded miss);
      a link to an explorer view opens the end of the recording
- [x] `DemoSite`: writes `docs/demo/` (the page, the dashboard's files, the
      player, the recording)
- [x] Tests: the built page is the dashboard's page plus the player; the
      recording answers every GET path `app.js` asks; every POST path is one
      the player refuses; nothing in demo mode writes keys

---

## Milestone 28c: Published demo, live check, docs

### Deliverables

- [x] The real recording (default parameters, real pace) committed under
      `docs/demo/`; a test that fails when `docs/demo/` no longer matches the
      dashboard's sources or the API's fields (so the demo cannot go stale
      silently)
- [x] The project site and the README link to the demo
- [x] Live, from a static server under a sub-path (as on Pages): the replay
      runs to its end, the feed fills, every explorer view opens, search
      (hit and miss), a direct link, mine / send refused with the sentence,
      the tour, 390 px without horizontal scroll, both themes, no page
      errors, no request to `/api`
- [x] Live on Pages after the merge: the same checks against
      `https://bartoszclapinski.github.io/BlockSmith/demo/`
- [x] Docs: ARCHITECTURE (recorder, format, player), STATUS, README, site

---

## Theory

### Why a recording and not a simulation

There are three ways to show a node's dashboard without a node:

| Way | What the visitor sees | Problem |
|-----|-----------------------|---------|
| Rewrite the node in JavaScript | A working copy | Two implementations of the rules; the one people try is not the one the 470 tests cover |
| Host a node for everyone | The real thing | A server to pay for and to defend; one visitor's actions are every visitor's chain |
| Replay what a real node answered | The real answers, in order | Nothing can be changed |

BlockSmith is a teaching project whose claim is "this Java code does it".
A JavaScript twin would show something else under the same name. A
recording shows the node's own answers and is honest about its one limit.

### Record and replay at the API boundary

```
 recording:  scripted session --HTTP--> real node --answers--> DemoRecording
                                                                   |
 replaying:  dashboard --fetchJson--> player --lookup(path)--------+
```

The dashboard already talks to the node through one function
(`fetchJson`). Cutting there means the 2,571 lines above it run unchanged
in the demo: the same rendering, the same explorer, the same feed. This is
the idea behind HTTP "cassette" libraries (VCR, Polly, WireMock recordings):
record at a narrow boundary, replay without the other side.

The price: a recording only answers what was asked while recording. So the
recorder asks everything: after every step it walks the whole state (every
block, transaction, address, search). The probe measured that this is
affordable.

### Storing repeated parts once

The same transaction appears in its block, on its own page and on the pages
of two addresses, in every frame. The recorder stores every object or list
longer than 80 characters once, in a pool, and writes a reference in its
place:

```
 pool[17] = {"transactionId":"9d8b...","sender":"0x85fb...", ...}
 frame 4:  "/api/blocks/31" -> {"index":31, ..., "transactions": {"$":212}}
           pool[212] = [{"$":16},{"$":17}]
```

Reading is one recursive function that follows `$`. It knows nothing about
blocks or transactions, so a new field in the API needs no change here.

---

## Open decisions

- **Recording, read-only** (recommended, see Theory) vs a JavaScript node
- **Every state in full** (recommended: any view is right at any moment of
  the replay) vs replaying only the deck and showing the explorer at the end
  state
- **`docs/demo/` committed, guarded by a test** (recommended: Pages keeps
  serving `docs/` from master; no change to the repository's settings) vs a
  GitHub Actions build of the site
- **The recording as `recording.js`** (one assignment; the page starts
  without waiting and also opens from a file) vs `recording.json` fetched at
  start
- **Real pace when recording** (recommended: real timestamps and the real
  difficulty; about five minutes, once) vs a faked clock

---

*Created: 2026-11-17 | Sprint 28 Planning | Completed: 2026-11-17*
