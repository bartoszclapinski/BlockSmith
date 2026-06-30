# Sprint 10: GitHub Issues

> Copy each issue to GitHub. Adjust issue numbers if needed.

---

## Milestone 10a: Blockchain integration + external block append

---

### Issue #74: Integrate a Blockchain reference into Node

**Labels:** `sprint-10`, `milestone-10a`, `enhancement`

**Description:**

Give `Node` a `Blockchain` so it can validate and store blocks received
from peers. Today Node has no chain reference (placeholder at
`Node.java:369` sends chainLength `0`).

**Changes to Node.java:**
- Add a `Blockchain blockchain` field
- Constructor accepts a `Blockchain` (keep a convenience constructor that
  creates a fresh one for existing tests/demo)
- Add `getBlockchain()` getter
- Populate the HELLO `chainLength` from `blockchain.getLatestBlock().getIndex() + 1`
  (replace the `0 // will be set when blockchain is integrated` placeholder)

**Acceptance Criteria:**
- [ ] Node holds a Blockchain and exposes `getBlockchain()`
- [ ] HELLO carries the real chain length
- [ ] Existing tests still pass (`mvn test` green)
- [ ] Compiles with `mvn compile`

---

### Issue #75: Add Blockchain.addBlock(Block) for externally-mined blocks

**Labels:** `sprint-10`, `milestone-10a`, `enhancement`

**Description:**

Add a method that appends a block that was mined elsewhere, after
validating it. Distinct from the existing `addBlock(String data)` which
mines locally.

**New method in Blockchain.java:**
```java
public boolean addBlock(Block block)
```

**Validation (reject if any fails):**
1. `block.getIndex() == getLatestBlock().getIndex() + 1`
2. `block.getPreviousHash().equals(getLatestBlock().getHash())`
3. Recomputed hash equals `block.getHash()` (integrity)
4. Hash meets the PoW target (`"0".repeat(MINING_DIFFICULTY)` prefix)

On success append to the chain and return `true`; otherwise return `false`
(do not throw - block came from the untrusted network).

**Acceptance Criteria:**
- [ ] `addBlock(Block)` validates index, prevHash, hash, and PoW
- [ ] Valid block is appended; invalid block is rejected (returns false)
- [ ] No re-mining of the incoming block
- [ ] THEORY/Javadoc comment explaining external block acceptance
- [ ] Compiles with `mvn compile`

---

### Issue #76: Unit tests for blockchain integration + external append

**Labels:** `sprint-10`, `milestone-10a`, `test`

**Description:**

Test the new external-append path and Node integration.

**Test Cases:**
1. `addBlock_appendsValidExternalBlock` - mined-elsewhere valid block is accepted
2. `addBlock_rejectsWrongIndex` - gap/duplicate index rejected
3. `addBlock_rejectsWrongPreviousHash` - prevHash not matching tip rejected
4. `addBlock_rejectsTamperedHash` - altered block (bad hash/PoW) rejected
5. `node_exposesBlockchain` - `getBlockchain()` returns the injected chain
6. `node_helloCarriesChainLength` - HELLO reports the real chain length

**Acceptance Criteria:**
- [ ] Tests added (BlockchainTest additions and/or new test class)
- [ ] All tests passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 10b: Block broadcast (NEW_BLOCK)

---

### Issue #77: Add Node.broadcastBlock(Block)

**Labels:** `sprint-10`, `milestone-10b`, `enhancement`

**Description:**

Send a newly mined/accepted block to every connected peer.

**New method in Node.java:**
```java
public void broadcastBlock(Block block)
```
- Build a `NewBlockMessage(nodeId, block)`
- Write its JSON to every connected peer's writer
- Optionally accept an "exclude" address so a relayed block is not sent
  back to its sender

**Acceptance Criteria:**
- [ ] `broadcastBlock(Block)` sends NEW_BLOCK to all connected peers
- [ ] Optional sender-exclusion to prevent echo
- [ ] Compiles with `mvn compile`

---

### Issue #78: Implement NEW_BLOCK handler (validate, append, re-gossip)

**Labels:** `sprint-10`, `milestone-10b`, `enhancement`

**Description:**

Register a NEW_BLOCK handler in `registerDefaultHandlers()`.

**Handler flow:**
- Extract the Block from `NewBlockMessage`
- If already known (same hash already in chain) -> ignore, no relay
- Else validate + append via `blockchain.addBlock(block)`
- If newly accepted -> `broadcastBlock(block)` to other peers
  (exclude the sender)
- Blocks that fail because the parent is missing are left for Milestone 10c

**Acceptance Criteria:**
- [ ] NEW_BLOCK handler registered
- [ ] Valid new block appended and re-gossiped
- [ ] Duplicate/known block ignored (no relay storm)
- [ ] Compiles with `mvn compile`

---

### Issue #79: Unit tests for block broadcast

**Labels:** `sprint-10`, `milestone-10b`, `test`

**Description:**

Test broadcast and the NEW_BLOCK handler (reflection-driven, in-memory
MessageContext like the Sprint 9 discovery tests).

**Test Cases:**
1. `newBlockMessage_roundTripsThroughParser` - serialization
2. `newBlockHandler_appendsValidBlock` - chain grows by one
3. `newBlockHandler_ignoresDuplicate` - known block not re-applied/relayed
4. `newBlockHandler_rejectsInvalidBlock` - bad block not appended

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Milestone 10c: Orphan block handling

---

### Issue #80: Buffer orphan blocks whose parent is unknown

**Labels:** `sprint-10`, `milestone-10c`, `enhancement`

**Description:**

When a NEW_BLOCK arrives whose `previousHash` is neither the current tip
nor any block in the chain, buffer it instead of dropping it.

**Implementation:**
- Add an orphan buffer (e.g. `Map<String, Block>` keyed by previousHash,
  or a small list)
- Bound the buffer size (drop oldest when full)

**Acceptance Criteria:**
- [ ] Orphan blocks are buffered, not lost
- [ ] Buffer is size-bounded
- [ ] Compiles with `mvn compile`

---

### Issue #81: Attach orphans when their parent arrives

**Labels:** `sprint-10`, `milestone-10c`, `enhancement`

**Description:**

After any successful append, re-scan the orphan buffer and attach any
block whose parent is now the chain tip. Repeat until no orphan attaches
(a run of out-of-order blocks should fully resolve).

**Acceptance Criteria:**
- [ ] Buffered orphan is appended once its parent is present
- [ ] Chains of orphans resolve in one pass
- [ ] Attached orphans are removed from the buffer
- [ ] Compiles with `mvn compile`

---

### Issue #82: Unit tests for orphan handling

**Labels:** `sprint-10`, `milestone-10c`, `test`

**Description:**

**Test Cases:**
1. `outOfOrderBlocks_bufferThenAttach` - deliver N+1 before N, then N; both end in chain
2. `orphanBuffer_isBounded` - exceeding cap drops oldest, no unbounded growth
3. `orphanWithNoParent_staysBuffered` - never-arriving parent leaves chain unchanged

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 10d: Chain sync (request missing blocks)

---

### Issue #83: Add GetBlocksMessage / BlocksMessage + MessageType entries

**Labels:** `sprint-10`, `milestone-10d`, `enhancement`

**Description:**

Add request/response messages for fetching a range of blocks.

- `MessageType.GET_BLOCKS`, `MessageType.BLOCKS` enum values
- `GetBlocksMessage(nodeId, int fromIndex)`
- `BlocksMessage(nodeId, List<Block> blocks)`
- Register both in `MessageParser.TYPE_REGISTRY`

**Acceptance Criteria:**
- [ ] Both message classes + enum values + parser registration
- [ ] Round-trips through MessageParser
- [ ] Compiles with `mvn compile`

---

### Issue #84: Implement GET_BLOCKS / BLOCKS handlers

**Labels:** `sprint-10`, `milestone-10d`, `enhancement`

**Description:**

- GET_BLOCKS handler: reply with a `BlocksMessage` containing blocks from
  the requested `fromIndex` to the tip
- BLOCKS handler: validate and append the received blocks in order via
  `blockchain.addBlock(Block)`

**Acceptance Criteria:**
- [ ] GET_BLOCKS serves the requested range
- [ ] BLOCKS applies the range in order, rejecting invalid blocks
- [ ] Compiles with `mvn compile`

---

### Issue #85: Trigger sync when a peer advertises a longer chain

**Labels:** `sprint-10`, `milestone-10d`, `enhancement`

**Description:**

Detect that a peer is ahead (via HELLO `chainLength`, or a NEW_BLOCK whose
index is beyond tip+1) and send `GetBlocksMessage(fromIndex = ourTip+1)`
to fetch the gap. Apply received blocks using the longest-valid-chain rule.

**Acceptance Criteria:**
- [ ] Behind-node requests missing blocks from an ahead-peer
- [ ] Received range applied; chain catches up
- [ ] Compiles with `mvn compile`

---

### Issue #86: Unit tests for chain sync

**Labels:** `sprint-10`, `milestone-10d`, `test`

**Description:**

**Test Cases:**
1. `getBlocksMessage_roundTrips` / `blocksMessage_roundTrips` - serialization
2. `getBlocksHandler_servesRangeFromIndex` - correct slice returned
3. `blocksHandler_appendsRangeInOrder` - behind chain catches up
4. `sync_rejectsInvalidBlockInRange` - bad block in range is rejected

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Summary

| Milestone | Issues | Tests |
|-----------|--------|-------|
| 10a: Chain integration | #74, #75, #76 | 6 tests |
| 10b: Block broadcast | #77, #78, #79 | 4 tests |
| 10c: Orphan handling | #80, #81, #82 | 3 tests |
| 10d: Chain sync | #83, #84, #85, #86 | 4 tests |
| **Total** | **13 issues** | **~17 tests** |

---

*Created: 2026-06-30 | Sprint 10 Planning*
