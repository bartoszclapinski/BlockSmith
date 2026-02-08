# Sprint 8: P2P Networking

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 8 |
| **Title** | P2P Networking |
| **Phase** | Phase 2: Network Layer |
| **Status** | ✅ Complete |
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
| **8b** | Server Side | `sprint8b/server-node` | ✅ Complete |
| **8c** | Client Side | `sprint8c/peer-client` | ✅ Complete |
| **8d** | Communication | `sprint8d/message-exchange` | ✅ Complete |

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

## 📦 Milestone 8b: Server Side ✅

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #28 | Create NetworkConfig class for network constants | ✅ |
| #29 | Create Node class with ServerSocket | ✅ |
| #30 | Implement connection acceptance with threads | ✅ |
| #31 | Unit tests for Node connection handling | ✅ |

### Deliverables

- [x] `NetworkConfig.java` class with network constants
- [x] `Node.java` class with ServerSocket
- [x] Multi-threaded connection acceptance (ExecutorService)
- [x] Graceful shutdown mechanism
- [x] 8 unit tests for Node

---

## 📦 Milestone 8c: Client Side ✅

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #34 | Create Peer class for outgoing TCP connections | ✅ |
| #35 | Implement HelloMessage handshake protocol | ✅ |
| #36 | Unit tests for Peer connection handling | ✅ |

### Deliverables

- [x] `Peer.java` class for client-side TCP connections
- [x] Outgoing connection handling with Socket
- [x] HelloMessage handshake protocol
- [x] Node responds to handshake
- [x] Fixed Message JSON to single-line (for readLine())
- [x] 7 unit tests for Peer

---

## 📦 Milestone 8d: Communication ✅

### GitHub Issues

| Issue | Title | Status |
|-------|-------|--------|
| #39 | Create MessageParser utility class for JSON message routing | ✅ |
| #40 | Create MessageHandler interface and MessageContext class | ✅ |
| #41 | Implement message loop and handler registry in Node | ✅ |
| #42 | Create MessageListener interface and add async listener to Peer | ✅ |
| #43 | Integration tests for bidirectional message exchange | ✅ |

### Deliverables

- [x] `MessageParser.java` - Parse raw JSON to correct Message subclass
- [x] `MessageHandler.java` - Functional interface for message handling
- [x] `MessageContext.java` - Connection wrapper for handlers
- [x] `MessageListener.java` - Async callback interface for Peer
- [x] Node message loop with handler registry
- [x] Default PING -> PONG handler
- [x] Async listener thread in Peer
- [x] 6 integration tests for bidirectional communication

---

## 🏗️ New Package Structure

```
com.blocksmith/
├── core/                    # Existing blockchain classes
├── util/                    # Existing utilities
└── network/                 # NEW - Network layer
    ├── MessageType.java     # ✅ Enum of message types
    ├── Message.java         # ✅ Abstract base message
    ├── MessageParser.java   # ✅ JSON-to-Message routing (Sprint 8d)
    ├── MessageHandler.java  # ✅ Handler interface (Sprint 8d)
    ├── MessageContext.java  # ✅ Connection wrapper (Sprint 8d)
    ├── MessageListener.java # ✅ Async listener interface (Sprint 8d)
    ├── NetworkConfig.java   # ✅ Network configuration
    ├── Node.java            # ✅ Network node (server + message loop)
    ├── Peer.java            # ✅ Peer connection (client + async listener)
    └── messages/            # ✅ Concrete message classes
        ├── HelloMessage.java
        ├── PingMessage.java
        ├── PongMessage.java
        ├── NewBlockMessage.java
        └── NewTransactionMessage.java
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

*Created: 2026-01-29 | Updated: 2026-02-08 - Sprint 8 Complete*
