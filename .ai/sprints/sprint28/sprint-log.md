# Sprint 28: Recorded Demo - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-17 |
| **Sprint End** | |

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
