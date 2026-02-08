# Sprint 8: P2P Networking - Log

## 📅 Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-01-29 |
| **Sprint End** | TBD |

---

## 📝 Milestone 8a: Message Protocol ✅ COMPLETE

### Day 1 (2026-01-29)
- [x] Sprint planning complete
- [x] Created GitHub issues #22-25

### Day 2 (2026-02-02)
- [x] Issue #22: Gson dependency (already existed)
- [x] Issue #23: Created MessageType enum with 12 message types
- [x] Issue #24: Created Message base class + 5 concrete classes
- [x] Issue #25: Added 6 unit tests for serialization
- [x] All 93 tests passing
- [x] Committed to `sprint8a/message-protocol`

**Files Created:**
- `network/MessageType.java`
- `network/Message.java`
- `network/messages/HelloMessage.java`
- `network/messages/PingMessage.java`
- `network/messages/PongMessage.java`
- `network/messages/NewBlockMessage.java`
- `network/messages/NewTransactionMessage.java`
- `test/network/MessageTest.java`

---

## 📝 Milestone 8b: Server Side ✅ COMPLETE

### Day 3 (2026-02-04)
- [x] Created GitHub issues #28-31
- [x] Issue #28: Created NetworkConfig class for network constants
- [x] Issue #29: Created Node class with ServerSocket
- [x] Issue #30: Implemented multi-threaded connection acceptance
- [x] Issue #31: Added 8 unit tests for Node
- [x] All 101 tests passing
- [x] Committed to `sprint8b/server-node`
- [x] Merged to master

**Files Created:**
- `network/NetworkConfig.java`
- `network/Node.java`
- `test/network/NodeTest.java`

---

## 📝 Milestone 8c: Client Side ✅ COMPLETE

### Day 4 (2026-02-04)
- [x] Created GitHub issues #34-36
- [x] Issue #34: Created Peer class for outgoing TCP connections
- [x] Issue #35: Implemented HelloMessage handshake protocol
  - Peer sends HelloMessage on connect
  - Node responds with HelloMessage
  - Fixed Message.toJson() to single-line (removed setPrettyPrinting)
  - Updated MessageTest assertions for compact JSON
- [x] Issue #36: Added 7 unit tests for Peer
- [x] All 108 tests passing
- [x] Committed to `sprint8c/peer-client`
- [x] Merged to master

**Files Created:**
- `network/Peer.java`
- `test/network/PeerTest.java`

**Files Modified:**
- `network/Message.java` - removed setPrettyPrinting for single-line JSON
- `network/Node.java` - added handshake response in handleConnection
- `test/network/MessageTest.java` - updated assertions for compact JSON

---

## 📝 Milestone 8d: Communication ✅ COMPLETE

### Day 5 (2026-02-08)
- [x] Created GitHub issues #39-43
- [x] Issue #39: Created MessageParser utility class for JSON message routing
  - Parses raw JSON, reads `type` field, maps to concrete Message subclass
  - Handles malformed JSON, unknown types gracefully (returns null)
- [x] Issue #40: Created MessageHandler interface and MessageContext class
  - @FunctionalInterface for lambda-based handlers
  - MessageContext wraps PrintWriter + remote node info
- [x] Issue #41: Refactored Node with message loop and handler registry
  - Added `Map<MessageType, MessageHandler>` handler registry
  - Replaced one-shot handleConnection with continuous message loop
  - Registered default PING -> PONG handler
  - Removed Thread.sleep(100) placeholder
- [x] Issue #42: Created MessageListener interface and async listener in Peer
  - Background daemon thread reads messages continuously
  - Fires onMessage() and onDisconnect() callbacks
  - Listener thread cleaned up on disconnect()
- [x] Issue #43: Added 6 integration tests for bidirectional communication
- [x] All 114 tests passing
- [x] Committed to `sprint8d/message-exchange`
- [x] Merged to master

**Files Created:**
- `network/MessageParser.java`
- `network/MessageHandler.java`
- `network/MessageContext.java`
- `network/MessageListener.java`
- `test/network/CommunicationTest.java`

**Files Modified:**
- `network/Node.java` - handler registry + message loop
- `network/Peer.java` - async listener thread

---

## 🎯 Sprint Summary

| Metric | Value |
|--------|-------|
| **Milestones** | 4 |
| **Completed** | 4 (8a, 8b, 8c, 8d) ✅ |
| **Total Issues** | 4 (8a) + 4 (8b) + 3 (8c) + 5 (8d) = 16 |
| **Tests Added** | 27 (6 + 8 + 7 + 6) |
| **Total Tests** | 114 |

---

## 💡 Lessons Learned

1. **Gson was already included** - Sprint 0 setup was thorough
2. **Abstract base class pattern** - Clean design for message hierarchy
3. **Issue-first workflow** - Great for tracking progress
4. **Handler pattern** - @FunctionalInterface allows clean lambda handlers
5. **CountDownLatch** - Perfect for testing async message exchange
6. **Daemon threads** - Ideal for background I/O that shouldn't block shutdown

---

## 📌 Notes

- Using issue-first approach
- Issues: #22, #23, #24, #25 for milestone 8a (all closed)
- Issues: #28, #29, #30, #31 for milestone 8b (all closed)
- Issues: #34, #35, #36 for milestone 8c (all closed)
- Issues: #39, #40, #41, #42, #43 for milestone 8d (all closed)
- Key fix: Message.toJson() must produce single-line JSON for BufferedReader.readLine()
- Sprint 8 COMPLETE - all 4 milestones done
