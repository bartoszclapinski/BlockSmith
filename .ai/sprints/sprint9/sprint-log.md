# Sprint 9: Node Discovery - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-02-09 |
| **Sprint End** | TBD |

---

## Milestone 9a: PeerInfo - ✅ Complete (2026-02-10)

### Delivered
- `PeerState.java` - Enum with 3 connection lifecycle states (DISCOVERED, CONNECTED, DISCONNECTED)
- `PeerInfo.java` - Peer metadata class (nodeId, host, port, state, lastSeen, connectedAt)
- `PeerInfoTest.java` - 6 unit tests for PeerInfo and PeerState

### Issues Closed
- #47: Create PeerState enum ✅
- #48: Create PeerInfo class ✅
- #49: Unit tests for PeerInfo ✅

### Stats
- Tests added: 6 (total: 120)
- PR: #52 merged to master

---

## Milestone 9b: PeerManager + Node Integration - ✅ Complete (2026-02-14)

### Delivered
- `PeerManager.java` - Peer registry backed by ConcurrentHashMap, enforces MAX_PEERS for CONNECTED peers
- Node integration - registers peers after handshake, removes on disconnect
- `Node.connectToPeer(host, port)` - outgoing connection support
- `PeerManagerTest.java` - 8 unit tests

### Issues Closed
- #54: Create PeerManager class ✅
- #55: Integrate PeerManager into Node ✅
- #56: Add outgoing connection support (connectToPeer) ✅
- #57: Unit tests for PeerManager and Node peer tracking ✅

### Stats
- Tests added: 8 (total: 128)

---

## Milestone 9c: Heartbeat - ✅ Complete (2026-06-30)

### Delivered
- `HEARTBEAT_INTERVAL_MS` and `PEER_TIMEOUT_MS` constants in NetworkConfig
- ScheduledExecutorService in Node sends periodic PINGs to connected peers
- PONG handler; lastSeen updated on every received message
- Dead peer detection and eviction in heartbeatTask (evict-then-ping): peers past PEER_TIMEOUT_MS are removed from the registry, their writer closed
- `HeartbeatTest.java` - 4 unit tests (eviction, live-peer retention, mixed eviction, config invariant)

### Issues Closed
- #61: Add heartbeat constants to NetworkConfig ✅
- #62: Implement heartbeat scheduler in Node ✅
- #63: Implement dead peer detection and eviction ✅
- #64: Unit tests for heartbeat mechanism ✅

### Stats
- Tests added: 4 (total: 132)

---

## Milestone 9d: Peer Discovery - Pending

---

## Notes

- Continuing issue-first workflow from Sprint 8
- Last Sprint 8 issue: #43 (+ PR issues #44-46)
- Sprint 9 issues start at #47

---

*Created: 2026-02-09*
