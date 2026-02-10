# Sprint 9: Node Discovery

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 9 |
| **Title** | Node Discovery |
| **Phase** | Phase 2: Network Layer |
| **Status** | Planning |
| **Depends On** | Sprint 8 Complete (P2P Networking) |

---

## Goal

Give nodes the ability to track connected peers, detect dead connections,
and discover new peers through the network. By the end of this sprint,
nodes will maintain a live peer registry, send heartbeats, and exchange
peer lists for automatic network growth.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **9a** | PeerInfo | `sprint9a/peer-info` | Pending |
| **9b** | PeerManager + Node Integration | `sprint9b/peer-manager` | Pending |
| **9c** | Heartbeat | `sprint9c/heartbeat` | Pending |
| **9d** | Peer Discovery | `sprint9d/peer-discovery` | Pending |

---

## Milestone 9a: PeerInfo

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #47 | Create PeerState enum for peer connection states | Pending |
| #48 | Create PeerInfo class for peer metadata | Pending |
| #49 | Unit tests for PeerInfo | Pending |

### Deliverables

- [ ] `PeerState` enum (DISCOVERED, CONNECTED, DISCONNECTED)
- [ ] `PeerInfo` class with nodeId, host, port, state, lastSeen
- [ ] State transition methods (markConnected, markDisconnected, updateLastSeen)
- [ ] Unit tests for PeerInfo

---

## Milestone 9b: PeerManager + Node Integration

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #50 | Create PeerManager class for peer registry | Pending |
| #51 | Integrate PeerManager into Node (track connections) | Pending |
| #52 | Add outgoing connection support to Node (connectToPeer) | Pending |
| #53 | Unit tests for PeerManager and Node peer tracking | Pending |

### Deliverables

- [ ] `PeerManager` class with ConcurrentHashMap registry
- [ ] Add/remove/query peers, enforce MAX_PEERS
- [ ] Node registers peers after handshake, removes on disconnect
- [ ] Node.connectToPeer(host, port) for outgoing connections
- [ ] Unit tests for PeerManager and Node integration

---

## Milestone 9c: Heartbeat

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #54 | Add heartbeat constants to NetworkConfig | Pending |
| #55 | Implement heartbeat scheduler in Node | Pending |
| #56 | Implement dead peer detection and eviction | Pending |
| #57 | Unit tests for heartbeat mechanism | Pending |

### Deliverables

- [ ] `HEARTBEAT_INTERVAL_MS` and `PEER_TIMEOUT_MS` in NetworkConfig
- [ ] ScheduledExecutorService sends periodic PINGs
- [ ] Update lastSeen on PONG received
- [ ] Evict peers that exceed timeout
- [ ] Unit tests for heartbeat

---

## Milestone 9d: Peer Discovery

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #58 | Create GetPeersMessage and PeersMessage classes | Pending |
| #59 | Implement GET_PEERS and PEERS handlers in Node | Pending |
| #60 | Add seed nodes config and bootstrap on startup | Pending |
| #61 | Unit tests for peer discovery | Pending |

### Deliverables

- [ ] `GetPeersMessage` class
- [ ] `PeersMessage` class (carries list of host:port pairs)
- [ ] Handler: GET_PEERS -> respond with known peers
- [ ] Handler: PEERS -> add new peers to PeerManager
- [ ] Seed nodes list in NetworkConfig
- [ ] Bootstrap: connect to seed nodes on Node.start()
- [ ] Unit tests for peer discovery

---

## Theory: Node Discovery in Blockchain

```
THEORY: How nodes find each other in a P2P network

PROBLEM:
- No central server to ask "who's online?"
- Nodes join and leave constantly
- New nodes need to find existing peers

BITCOIN'S APPROACH:
1. Hard-coded "seed nodes" (DNS seeds)
2. New node connects to seeds
3. Sends "getaddr" message (= our GET_PEERS)
4. Receives list of known peers (= our PEERS)
5. Connects to some of those peers
6. Repeats - network grows organically

OUR SIMPLIFIED MODEL:
1. Configurable seed nodes (host:port list)
2. On startup, connect to seeds
3. Exchange peer lists periodically
4. Heartbeat (PING/PONG) detects dead peers
5. Evict dead peers, discover new ones

GOSSIP PROTOCOL:
- Each node tells its peers about other peers
- Information spreads like gossip in a social network
- Eventually all nodes learn about each other
```

---

## Dependencies

- Sprint 8 Complete (message protocol, Node, Peer, handlers)
- Existing MessageTypes: GET_PEERS, PEERS, PING, PONG

---

*Created: 2026-02-09*
