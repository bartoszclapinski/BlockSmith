# Sprint 28: Recorded Demo - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-17 |
| **Sprint End** | 2026-11-17 |

---

## Planning probe (not committed)

| Asked | Got |
|-------|-----|
| `https://bartoszclapinski.github.io/BlockSmith/demo/` | 404 |
| `https://bartoszclapinski.github.io/style.css`, `/app.js` (what `index.html` asks for when copied to Pages) | 404, 404 |
| `https://bartoszclapinski.github.io/api/network/status` | 404 |
| `/api/...` paths in `app.js` | 23, all through `fetchJson` |
| One state of a signed chain (33 blocks, 5 wallets): every answer the dashboard can ask for | 308 answers, 322,387 bytes |
| Ten states, as they are | 2,971,985 bytes (360,173 gzipped) |
| Ten states, identical answers stored once | 1,816,535 bytes |
| Ten states, repeated objects and lists over 80 characters stored once | 726,840 bytes (90,673 gzipped) |
| The same with a 200-character threshold | 867,986 bytes (141,696 gzipped) |

Also noted: the activity feed is built from the difference between two
polls (`deriveFeed`), so a single frozen state shows one line for ever; an
address row is a flat transaction with `confirmations`, so it changes with
every block and only the parts below it repeat.

---

## Milestone 28a: Recorder - Complete (PR #404)

- `com.blocksmith.demo.DemoRecorder`: two real nodes (`Node` + `ApiServer`,
  signatures required); a 22-step story over the first one's HTTP API,
  signed with `Wallet`s like a browser (prepare, sign, submit): transfers at
  different fees, a hashlock contract deployed and claimed, a 2-of-3
  multisig, a fee raised on a waiting transfer, a second node joining and
  mining, a reorg when it returns from being offline with two blocks against
  one. After every step: every answer the dashboard can ask for
- `DemoRecording`: frames (`title`, `detail`, `at`, path -> answer), every
  object or list of 80+ characters written once in a pool (`{"$": n}`), the
  node's own 404 bodies, the active wallet; `toScript` / `fromScript`
- The whole story ran on the first try, the reorg included
- Tests: `DemoRecorderTest` (5). 22 mutations; 4 survived the first run
  (searches by height, by transaction id and by address were recorded but
  not asserted, and a search that finds nothing could have been kept);
  assertions added, then 22/22 detected
- Wrong expectations of mine in the first test run: 161 answers in a frame,
  not "over 250"; a replaced transfer keeps the pending count, so the feed
  is silent in that step (the caption and the mempool show it); Alice's
  history was 23 rows, one page, so the story got four more transfers and
  six more blocks: now two pages of history and two pages of blocks
- 470 -> 475 tests

## Milestone 28b: Player - Complete (PR #405)

- `src/main/demo/demo.js` + `demo.css`: sets `window.BlockSmithDemo` before
  `app.js`. `answer(path, options)`: the current frame; a POST is refused
  ("A recording cannot be changed. Run BlockSmith to mine, send and
  deploy."); a miss is the node's recorded sentence. A bar: caption, pause /
  next / start again, position, "Run it yourself". A link to an explorer
  view opens the last frame, paused
- `app.js` behind `const DEMO = window.BlockSmithDemo || null`: `fetchJson`
  asks the player; `nowMs()` (the recording's clock) for relative ages;
  `ensureUnlocked` refuses before the passphrase dialog; action buttons
  open a short explanation instead of a form; the recording's active
  wallet; the tour holds the replay
- `index.html`: `style.css` and `app.js` by relative path
- `DemoSite`: builds `docs/demo` (`--keep`, `--fast`, `--out`)
- Tests: `DemoSiteTest` (5); 18 mutations, all detected
- Live (headless Edge, a static server under `/BlockSmith/demo/`, a fast
  recording): 40/40
- Found while checking live: (1) the Send panel said "this browser has no
  key ... create a wallet here", which a recording cannot do: the action
  buttons now explain what they do on a node; (2) a search for
  "no-such-thing" answered "Nothing found for nothing-like-this", the phrase
  used when recording: a miss now carries what it was asked with and the
  player puts the visitor's words there (2 of 40 then failed until my
  script's expectations were updated)
- 475 -> 480 tests

## Milestone 28c: Published demo - Complete (PR #406)

- The real recording: default parameters, real pace (blocks at least 8 s
  apart, 2.5 s after each step). 22 frames, 22 blocks, the difficulty
  retargeted on the way (4.14 at the end), one reorg.
  `recording.js` is 679,070 bytes, 58,975 gzipped
- `docs/demo/` committed; the project site's first button and its dashboard
  section link to it; README "Try the demo"; ARCHITECTURE "Recorded demo"
- Tests: `DemoPublishedTest` (3): `docs/demo` equals what `DemoSite` builds
  today; the published recording has exactly the fields a recording made
  now has, per kind of answer; same story, real pace, real proof of work.
  6 mutations (stale page, style, player; a field lost, a field gained; the
  story changed), all detected. My first threshold was wrong: the 22 frames
  span 84 s, not "over two minutes" (the waits before blocks overlap the
  waits after steps)
- Live on the real recording (`docs/` served as `/BlockSmith/`): 40/40, no
  request to `/api`, no page errors
- Live on Pages after the merges: 38/40 on the first run. The page had no icon, so
  the browser asked for `/favicon.ico` at the root of the host, outside the
  demo's folder: a 404 and a request to another path. Fixed in PR #408
  (#407): an inline icon. 40/40 locally with no request for an icon; checked
  again on Pages before the release
- 480 -> 483 tests

---
