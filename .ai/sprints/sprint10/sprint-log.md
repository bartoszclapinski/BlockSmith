# Sprint 10: Block Broadcasting - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-06-30 |
| **Sprint End** | 2026-06-30 |

---

## Milestone 10a: Blockchain integration + external block append - Complete

- Wired a `Blockchain` reference into `Node` (constructor injection + `getBlockchain()`)
- HELLO handshake now reports the real chain length (replaced the `0` placeholder)
- Added `Blockchain.addBlock(Block)` to append externally-mined blocks after
  validating index, previousHash, hash integrity, and PoW (no re-mining)
- Issues #74, #75, #76 - merged via PR #78
- Tests: `ExternalBlockTest` (4), `ChainIntegrationTest` (2)

---

## Milestone 10b: Block broadcast (NEW_BLOCK) - Complete

- Added `Node.broadcastBlock(Block)` / `broadcastBlock(Block, excludeNodeId)`
- NEW_BLOCK handler validates, appends, and re-gossips a newly accepted block;
  duplicates and invalid blocks fail `addBlock` so they are not relayed (no storm)
- Sender exclusion on relay to avoid bouncing a block back to its source
- Issues #77, #78, #79 - merged via PR #82
- Tests: `BlockBroadcastTest` (4)

---

## Milestone 10c: Orphan block handling - Complete

- Bounded orphan buffer (`MAX_ORPHANS = 50`) keyed by previousHash
- Blocks arriving ahead of their parent are buffered, then attached once the
  parent appears; chains of orphans resolve in a single pass
- Issues #80, #81, #82 - merged via PR #86
- Tests: `OrphanBlockTest` (3)

---

## Milestone 10d: Chain sync (request missing blocks) - Complete

- Added `GetBlocksMessage(nodeId, fromIndex)` and `BlocksMessage(nodeId, blocks)`,
  registered in `MessageParser`
- GET_BLOCKS handler serves blocks from the requested index to the tip;
  BLOCKS handler applies the received range in order via `addBlock`
- NEW_BLOCK handler detects an ahead-peer (block beyond tip+1) and requests the
  gap with `GetBlocksMessage(fromIndex = getChainSize())`
- Issues #87, #88, #89, #90 - merged via PR #91
- Tests: `ChainSyncTest` (5)

---

## Outcome

- **Sprint 10 complete** (10a-10d): blocks now propagate across the peer network,
  out-of-order delivery is tolerated, and a behind node can catch up via sync
- Test count: 137 -> 155 (+18)

---

## Notes

- Continued issue-first, milestone-per-branch workflow from Sprints 8-9
- Last Sprint 9 issue: #71 (PRs went up to #73)
- Sprint 10 issues started at #74 (PRs consumed some numbers, so the final 10d
  issues landed at #87-90, merged by PR #91)
- Foundational gap closed in 10a: Node previously had no Blockchain reference
- Known follow-up: every `new Blockchain()` mines a genesis with a wall-clock
  timestamp, so independent chains never share a genesis hash. A real multi-node
  sync demo needs a deterministic (fixed/hardcoded) genesis

---

*Created: 2026-06-30 | Completed: 2026-06-30*
