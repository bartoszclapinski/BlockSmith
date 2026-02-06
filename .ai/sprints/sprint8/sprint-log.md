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

## 📝 Milestone 8d: Communication

_Not started_

---

## 🎯 Sprint Summary

| Metric | Value |
|--------|-------|
| **Milestones** | 4 |
| **Completed** | 3 (8a, 8b, 8c) |
| **Total Issues** | 4 (8a) + 4 (8b) + 3 (8c) = 11 |
| **Tests Added** | 21 (6 + 8 + 7) |
| **Total Tests** | 108 |

---

## 💡 Lessons Learned

1. **Gson was already included** - Sprint 0 setup was thorough
2. **Abstract base class pattern** - Clean design for message hierarchy
3. **Issue-first workflow** - Great for tracking progress

---

## 📌 Notes

- Using issue-first approach
- Issues: #22, #23, #24, #25 for milestone 8a (all closed)
- Issues: #28, #29, #30, #31 for milestone 8b (all closed)
- Issues: #34, #35, #36 for milestone 8c (all closed)
- Milestone 8d next: Full message loop and integration tests
- Key fix: Message.toJson() must produce single-line JSON for BufferedReader.readLine()
