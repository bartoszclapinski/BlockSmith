# Sprint 8: P2P Networking

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 8 |
| **Title** | P2P Networking |
| **Phase** | Phase 2: Network Layer |
| **Status** | In Progress |
| **Depends On** | Phase 1 Complete (v1.0.0) |

---

## 🎯 Goal

Transform BlockSmith from a single-node blockchain into a distributed network where multiple nodes can communicate using TCP sockets.

---

## 📦 Milestones

Sprint 8 is divided into 4 milestones:

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **8a** | Message Protocol | `sprint8a/message-protocol` | ✅ Complete |
| **8b** | Server Side | `sprint8b/server-node` | ⬜ Next |
| **8c** | Client Side | `sprint8c/peer-client` | ⬜ Pending |
| **8d** | Communication | `sprint8d/message-exchange` | ⬜ Pending |

---

## 📦 Milestone 8a: Message Protocol

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #22 | Add Gson dependency for JSON serialization | ✅ (existed) |
| #23 | Create MessageType enum | ✅ |
| #24 | Create Message base class and subtypes | ✅ |
| #25 | Unit tests for message serialization | ✅ |

### Deliverables

- [x] Gson dependency in pom.xml
- [x] `MessageType` enum with all network message types
- [x] `Message` abstract base class
- [x] Concrete message classes (HelloMessage, PingMessage, PongMessage, NewBlockMessage, NewTransactionMessage)
- [x] JSON serialization/deserialization
- [x] 6 unit tests for message serialization

---

## 📦 Milestone 8b: Server Side

### Planned Issues

- Create `Node.java` - main network node class
- Implement `ServerSocket` to listen for connections
- Accept incoming connections in separate threads
- Basic connection logging

### Deliverables

- [ ] `Node.java` class
- [ ] Server socket listening
- [ ] Connection acceptance
- [ ] Thread management for connections

---

## 📦 Milestone 8c: Client Side

### Planned Issues

- Create `Peer.java` - peer connection handler
- Connect to other nodes
- Store peer information (IP, port, status)

### Deliverables

- [ ] `Peer.java` class
- [ ] Outgoing connection handling
- [ ] Peer state management

---

## 📦 Milestone 8d: Communication

### Planned Issues

- Send messages between nodes
- Receive and parse messages
- Message handlers
- Integration tests

### Deliverables

- [ ] Bidirectional message exchange
- [ ] Message parsing and routing
- [ ] Integration test: two nodes communicating

---

## 🏗️ New Package Structure

```
com.blocksmith/
├── core/                    # Existing blockchain classes
├── util/                    # Existing utilities
└── network/                 # NEW - Network layer
    ├── MessageType.java     # Enum of message types
    ├── Message.java         # Abstract base message
    ├── messages/            # Concrete message classes
    │   ├── HelloMessage.java
    │   ├── BlockMessage.java
    │   ├── TransactionMessage.java
    │   └── ...
    ├── Node.java            # Network node (server)
    ├── Peer.java            # Peer connection
    └── NetworkConfig.java   # Network configuration
```

---

## 📝 Theory: P2P Networking

```java
/**
 * THEORY: Peer-to-Peer (P2P) Networking in Blockchain
 * 
 * WHY P2P?
 * - No central server = no single point of failure
 * - Censorship resistant
 * - Scales with more nodes
 * 
 * HOW BITCOIN DOES IT:
 * 1. New node connects to known "seed" nodes
 * 2. Requests peer list from connected nodes
 * 3. Connects to more peers (typically 8-125)
 * 4. Broadcasts transactions and blocks to peers
 * 5. Peers relay to their peers (gossip protocol)
 * 
 * OUR SIMPLIFIED MODEL:
 * - TCP sockets for communication
 * - JSON messages (not binary like Bitcoin)
 * - Manual peer configuration (no DNS seeds)
 * - Basic gossip protocol
 */
```

---

## 🔗 Dependencies

- Phase 1 complete (v1.0.0)
- Gson library for JSON serialization

---

## 💡 Implementation Notes

### Message Protocol Design

All messages are JSON with common fields:
```json
{
  "type": "HELLO",
  "timestamp": 1706540000000,
  "nodeId": "abc123",
  "payload": { ... }
}
```

### Thread Safety

- Each peer connection runs in its own thread
- Shared data (blockchain, mempool) needs synchronization
- Use `ConcurrentHashMap` for peer list

---

*Created: 2026-01-29*
