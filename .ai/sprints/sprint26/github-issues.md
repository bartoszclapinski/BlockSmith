# Sprint 26: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 26a: Joining: peer arguments, connect API, reconnect

---

### Two real nodes cannot connect: no way to join a network

**Labels:** `sprint-26`, `milestone-26a`, `bug`

**Description:**

Reproduced on master: two `BlockSmithNode` processes, 3 blocks mined on A;
A has chain length 4, B has 1, both peer lists are empty. There is no peer
argument, `SEED_NODES` is empty, there is no API to connect, and nothing
sends `GET_PEERS`. In tests, after a peer restarts, `connectToPeer` throws
"Already connected" for good.

**Fix:**
- `--peer host:port` (repeatable) and `-Dblocksmith.peers`
- `POST /api/network/peers {address}` (400 / 409 / 502)
- Reconnect to DISCOVERED / DISCONNECTED peers; mark dropped outbound links
  DISCONNECTED at once

**Acceptance Criteria:**
- [ ] Two nodes started like `BlockSmithNode` share blocks
- [ ] Compiles with `mvn compile`

---

### Tests for joining a network

**Labels:** `sprint-26`, `milestone-26a`, `test`

**Description:**

**Test Cases:**
1. `twoNodesFromTheCommandLine_shareBlocks` (the probe)
2. `peerRestarts_reconnectWorks`
3. `connectApi_errors` (bad address, already connected, unreachable)

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 26b: Identity and discovery

---

### Persistent node identity, self and duplicate detection, discovery with auto-connect

**Labels:** `sprint-26`, `milestone-26b`, `enhancement`

**Description:**

- `nodeId` stored in `<dataDir>/node-id`
- HELLO with our own nodeId: self-dial, closed and remembered; a second link
  to a connected nodeId: closed
- Heartbeat sends `GET_PEERS`; `PEERS` lists listening addresses of peers we
  connected to (not the asker, not us); DISCOVERED peers dialled while
  outbound < 4, never above `MAX_PEERS`; failed dials back off

**Acceptance Criteria:**
- [ ] A node that only knows B finds and connects to C
- [ ] Compiles with `mvn compile`

---

### Tests for identity and discovery

**Labels:** `sprint-26`, `milestone-26b`, `test`

**Description:**

**Test Cases:**
1. `nodeId_survivesRestart`
2. `dialingSelf_refusedAndRemembered`
3. `secondLinkToSamePeer_closed`
4. `discovery_findsAndConnectsThirdNode`
5. `autoConnect_respectsLimit`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 26c: Mempool both ways, dashboard peers, live network, docs

---

### Inbound mempool sync, dashboard peer panel, live three-node network, docs

**Labels:** `sprint-26`, `milestone-26c`, `enhancement`

**Description:**

- The inbound side requests the peer's mempool too
- Dashboard: peers (nodeId, address, state, direction), connect to an address
- Live: A and C joined to B only; A discovers C; B killed; A's block reaches
  C; B restarts and catches up
- Docs: ARCHITECTURE, STATUS (network deferred items), README (run a network)

**Acceptance Criteria:**
- [ ] Three real processes keep one chain when the middle one dies
- [ ] Compiles with `mvn compile`

---

### Network end-to-end checks

**Labels:** `sprint-26`, `milestone-26c`, `test`

**Description:**

**Test Cases:**
1. `inboundSide_pullsMempool`
2. Dashboard: served script / page checks for the peer panel
3. Live: the three-process scenario, results in the sprint log

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-15 | Sprint 26 Planning*
