# Sprint 10: Block Broadcasting

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 10 |
| **Title** | Block Broadcasting |
| **Phase** | Phase 2: Network Layer |
| **Status** | Planning |
| **Depends On** | Sprint 9 Complete (Node Discovery) |

---

## Goal

Make the network agree on a shared chain. When one node mines a block it
gossips it to every peer; receivers validate the block, append it, and
re-broadcast. Blocks that arrive before their parent are buffered as
orphans and attached later. A node that has fallen behind requests the
missing blocks from a peer. By the end of this sprint a freshly mined
block propagates across all connected nodes and chains converge.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **10a** | Blockchain integration + external block append | `sprint10a/chain-integration` | Pending |
| **10b** | Block broadcast (NEW_BLOCK) | `sprint10b/block-broadcast` | Pending |
| **10c** | Orphan block handling | `sprint10c/orphan-blocks` | Pending |
| **10d** | Chain sync (request missing blocks) | `sprint10d/chain-sync` | Pending |

---

## Milestone 10a: Blockchain integration + external block append

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #74 | Integrate a Blockchain reference into Node | Pending |
| #75 | Add Blockchain.addBlock(Block) for externally-mined blocks | Pending |
| #76 | Unit tests for blockchain integration + external append | Pending |

### Deliverables

- [ ] Node holds a `Blockchain` (constructor param + `getBlockchain()` getter)
- [ ] `Blockchain.addBlock(Block)` validates an externally-mined block
      (correct index, prevHash matches tip, recomputed hash, PoW target met)
      and appends without re-mining; returns boolean
- [ ] HELLO chainLength field populated from the real chain (replaces the
      `0  // will be set when blockchain is integrated` placeholder)
- [ ] Unit tests for accept/reject of external blocks

### Why this is first

`Node` currently has no chain reference (placeholder at Node.java:369).
Broadcasting is meaningless until a received block can be validated and
appended, so chain integration is the foundation for 10b-10d.

---

## Milestone 10b: Block broadcast (NEW_BLOCK)

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #77 | Add Node.broadcastBlock(Block) | Pending |
| #78 | Implement NEW_BLOCK handler (validate, append, re-gossip) | Pending |
| #79 | Unit tests for block broadcast | Pending |

### Deliverables

- [ ] `Node.broadcastBlock(Block)` sends a `NewBlockMessage` to all
      connected peers
- [ ] NEW_BLOCK handler: validate via `Blockchain.addBlock`, and if newly
      accepted, re-broadcast to other peers (skip the sender to avoid echo)
- [ ] Duplicate/known blocks are ignored (no re-broadcast storm)
- [ ] Unit tests: accepted block re-gossips, duplicate does not

---

## Milestone 10c: Orphan block handling

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #80 | Buffer orphan blocks whose parent is unknown | Pending |
| #81 | Attach orphans when their parent arrives | Pending |
| #82 | Unit tests for orphan handling | Pending |

### Deliverables

- [ ] Orphan buffer (blocks whose `previousHash` is not the current tip
      and not yet in the chain)
- [ ] When a block is appended, re-check the buffer and attach any block
      whose parent is now present
- [ ] Buffer is bounded (drop oldest / cap size) to avoid memory growth
- [ ] Unit tests: out-of-order arrival converges to the correct chain

---

## Milestone 10d: Chain sync (request missing blocks)

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #83 | Add GetBlocksMessage / BlocksMessage + MessageType entries | Pending |
| #84 | Implement GET_BLOCKS / BLOCKS handlers (serve + apply range) | Pending |
| #85 | Trigger sync when a peer advertises a longer chain | Pending |
| #86 | Unit tests for chain sync | Pending |

### Deliverables

- [ ] `GetBlocksMessage` (fromIndex) and `BlocksMessage` (list of blocks)
- [ ] `GET_BLOCKS` handler serves blocks from the requested index
- [ ] `BLOCKS` handler validates and appends the received range in order
- [ ] On HELLO / NEW_BLOCK indicating a peer is ahead, request the gap
- [ ] Longest-valid-chain rule when applying a received range
- [ ] Unit tests for the sync flow

---

## Theory: Block Propagation in Blockchain

```
THEORY: How a mined block reaches the whole network

PROBLEM:
- A miner finds a block locally. Every other node must learn about it
  quickly and agree it is valid, or the network forks.

BITCOIN'S APPROACH:
1. Miner sends "inv" (inventory) announcing the new block hash
2. Peers that don't have it reply "getdata"
3. Miner sends the full "block"
4. Each receiver validates (PoW, prev hash, txs) and relays onward
5. Gossip floods the block to the entire network in seconds

OUR SIMPLIFIED MODEL:
1. Miner calls broadcastBlock() -> NEW_BLOCK to all peers (full block)
2. Receiver validates and appends via Blockchain.addBlock(Block)
3. If newly accepted, receiver re-gossips to its other peers
4. Block arrives before parent? Buffer as orphan, attach later
5. Fell behind? GET_BLOCKS / BLOCKS to fetch the missing range

FORKS & LONGEST CHAIN:
- Two miners can find a block at the same height -> temporary fork
- Nodes follow the longest valid chain; the shorter branch is orphaned
```

---

## Dependencies

- Sprint 9 Complete (peer registry, connected-peer iteration, handlers)
- Existing MessageTypes: NEW_BLOCK, NEW_TRANSACTION
- Existing message class: NewBlockMessage (carries a full Block)
- Core: Blockchain.getLatestBlock(), Block index/prevHash/hash, PoW target

---

*Created: 2026-06-30 | Sprint 10 Planning*
