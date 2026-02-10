# Sprint 9: GitHub Issues

> Copy each issue to GitHub. Adjust issue numbers if needed.

---

## Milestone 9a: PeerInfo

---

### Issue #47: Create PeerState enum for peer connection states

**Labels:** `sprint-9`, `milestone-9a`, `enhancement`

**Description:**

Create a `PeerState` enum in `com.blocksmith.network` that represents the lifecycle
of a peer connection.

**States:**
- `DISCOVERED` - We know about this peer (from peer exchange) but haven't connected yet
- `CONNECTED` - Active TCP connection established
- `DISCONNECTED` - Was connected but connection was lost or closed

**Acceptance Criteria:**
- [ ] `PeerState.java` created in `network/` package
- [ ] Three states: DISCOVERED, CONNECTED, DISCONNECTED
- [ ] THEORY comment explaining peer lifecycle
- [ ] Compiles with `mvn compile`

---

### Issue #48: Create PeerInfo class for peer metadata

**Labels:** `sprint-9`, `milestone-9a`, `enhancement`

**Description:**

Create a `PeerInfo` class in `com.blocksmith.network` that holds all known
information about a peer node.

**Fields:**
- `nodeId` (String) - Remote node's unique ID (may be null if only discovered)
- `host` (String) - IP address or hostname
- `port` (int) - Listening port
- `state` (PeerState) - Current connection state
- `lastSeen` (long) - Timestamp of last received message (for heartbeat)
- `connectedAt` (long) - Timestamp when connection was established

**Methods:**
- Constructor: `PeerInfo(String host, int port)` - creates with state DISCOVERED
- `markConnected(String nodeId)` - transition to CONNECTED, set connectedAt
- `markDisconnected()` - transition to DISCONNECTED
- `updateLastSeen()` - update lastSeen to current time
- `getAddress()` - returns `"host:port"` string
- Standard getters
- `toString()` override

**Acceptance Criteria:**
- [ ] `PeerInfo.java` created in `network/` package
- [ ] All fields and methods implemented
- [ ] THEORY comment explaining peer metadata tracking
- [ ] Compiles with `mvn compile`

---

### Issue #49: Unit tests for PeerInfo

**Labels:** `sprint-9`, `milestone-9a`, `test`

**Description:**

Write unit tests for `PeerInfo` and `PeerState`.

**Test Cases:**
1. `constructor_setsHostPortAndDiscoveredState` - New PeerInfo starts as DISCOVERED
2. `markConnected_setsStateAndNodeId` - Transition to CONNECTED works
3. `markDisconnected_setsState` - Transition to DISCONNECTED works
4. `updateLastSeen_updatesTimestamp` - lastSeen changes after call
5. `getAddress_returnsHostColonPort` - Format is "host:port"
6. `peerState_hasThreeValues` - Enum has exactly 3 values

**Acceptance Criteria:**
- [ ] `PeerInfoTest.java` created in `test/network/` package
- [ ] All 6 tests passing
- [ ] All existing 114 tests still passing
- [ ] `mvn test` green

---

## Milestone 9b: PeerManager + Node Integration

---

### Issue #50: Create PeerManager class for peer registry

**Labels:** `sprint-9`, `milestone-9b`, `enhancement`

**Description:**

Create a `PeerManager` class in `com.blocksmith.network` that manages the
node's known and connected peers.

**Fields:**
- `peers` - `ConcurrentHashMap<String, PeerInfo>` (keyed by "host:port")
- `maxPeers` - int (from NetworkConfig.MAX_PEERS)

**Methods:**
- `addPeer(PeerInfo info)` - Add peer to registry (respects MAX_PEERS for connected)
- `removePeer(String address)` - Remove peer from registry
- `getPeer(String address)` - Get PeerInfo by "host:port"
- `getConnectedPeers()` - List of all CONNECTED peers
- `getKnownPeers()` - List of ALL known peers (any state)
- `getConnectedCount()` - Number of CONNECTED peers
- `isKnown(String address)` - Check if peer is in registry
- `canAcceptMore()` - True if connected count < maxPeers

**Thread Safety:**
- Use `ConcurrentHashMap` for the peer registry
- All methods must be safe to call from multiple threads

**Acceptance Criteria:**
- [ ] `PeerManager.java` created in `network/` package
- [ ] Thread-safe implementation with ConcurrentHashMap
- [ ] MAX_PEERS enforcement for connected peers
- [ ] THEORY comment explaining peer management
- [ ] Compiles with `mvn compile`

---

### Issue #51: Integrate PeerManager into Node (track connections)

**Labels:** `sprint-9`, `milestone-9b`, `enhancement`

**Description:**

Wire `PeerManager` into `Node` so that incoming connections are tracked.

**Changes to Node.java:**
- Add `PeerManager` field, initialize in constructor
- After handshake in `handleConnection()`:
  - Create PeerInfo from HelloMessage data (host, port, nodeId)
  - Call `peerManager.addPeer()` and `peerInfo.markConnected()`
- On connection close (finally block):
  - Call `peerInfo.markDisconnected()`
- On each received message:
  - Call `peerInfo.updateLastSeen()`
- Expose `getPeerManager()` getter

**Acceptance Criteria:**
- [ ] Node creates PeerManager in constructor
- [ ] Incoming peers registered after handshake
- [ ] Peers marked disconnected on connection close
- [ ] lastSeen updated on message receipt
- [ ] `getPeerManager()` getter added
- [ ] Existing tests still pass

---

### Issue #52: Add outgoing connection support to Node (connectToPeer)

**Labels:** `sprint-9`, `milestone-9b`, `enhancement`

**Description:**

Give Node the ability to initiate outgoing connections using Peer objects.

Currently Node is server-only. For peer discovery to work, Node must also
be able to connect OUT to other nodes.

**New method in Node.java:**
```java
public Peer connectToPeer(String host, int port) throws IOException
```

**Flow:**
1. Check if already connected (via PeerManager)
2. Check if MAX_PEERS reached
3. Create Peer object, call connect() and performHandshake()
4. Register in PeerManager as CONNECTED
5. Start listener on the Peer (for incoming messages)
6. Return the Peer object

**Also add:**
- `outboundPeers` - `List<Peer>` to track outgoing connections
- Clean up outbound peers in `stop()`

**Acceptance Criteria:**
- [ ] `connectToPeer(host, port)` method added to Node
- [ ] Outbound peer tracked in list and PeerManager
- [ ] MAX_PEERS check prevents over-connection
- [ ] Already-connected check prevents duplicates
- [ ] Outbound peers cleaned up in stop()
- [ ] Compiles with `mvn compile`

---

### Issue #53: Unit tests for PeerManager and Node peer tracking

**Labels:** `sprint-9`, `milestone-9b`, `test`

**Description:**

Write unit tests for PeerManager and the new Node peer tracking features.

**PeerManager Tests (PeerManagerTest.java):**
1. `addPeer_storesPeerInRegistry` - Peer can be added and retrieved
2. `removePeer_removesPeerFromRegistry` - Peer can be removed
3. `getConnectedPeers_returnsOnlyConnected` - Filters by CONNECTED state
4. `canAcceptMore_respectsMaxPeers` - Returns false when at limit
5. `isKnown_returnsTrueForExistingPeer` - Known check works
6. `getConnectedCount_tracksCorrectly` - Count matches actual connected

**Node Peer Tracking Tests (add to NodeTest.java or new file):**
7. `node_tracksPeerAfterConnection` - PeerManager has entry after handshake
8. `node_connectToPeer_establishesOutboundConnection` - Outgoing connection works

**Acceptance Criteria:**
- [ ] `PeerManagerTest.java` created with 6+ tests
- [ ] Node peer tracking tests added
- [ ] All tests passing
- [ ] `mvn test` green

---

## Milestone 9c: Heartbeat

---

### Issue #54: Add heartbeat constants to NetworkConfig

**Labels:** `sprint-9`, `milestone-9c`, `enhancement`

**Description:**

Add heartbeat-related constants to `NetworkConfig`.

**New Constants:**
- `HEARTBEAT_INTERVAL_MS` = 10000 (10 seconds) - How often to send PINGs
- `PEER_TIMEOUT_MS` = 30000 (30 seconds) - How long before a peer is considered dead

**Acceptance Criteria:**
- [ ] Both constants added to NetworkConfig
- [ ] Javadoc comments explaining each value
- [ ] Compiles with `mvn compile`

---

### Issue #55: Implement heartbeat scheduler in Node

**Labels:** `sprint-9`, `milestone-9c`, `enhancement`

**Description:**

Add a scheduled task to Node that periodically sends PING messages to all
connected peers.

**Implementation:**
- Add `ScheduledExecutorService heartbeatScheduler` field
- Start scheduler in `Node.start()` with HEARTBEAT_INTERVAL_MS
- Heartbeat task: iterate connected peers via PeerManager, send PING to each
- Shut down scheduler in `Node.stop()`
- Update PONG handler: when PONG received, call `peerInfo.updateLastSeen()`

**Acceptance Criteria:**
- [ ] ScheduledExecutorService created and started
- [ ] Periodic PING sent to all connected peers
- [ ] PONG handler updates lastSeen
- [ ] Scheduler shut down in stop()
- [ ] Compiles with `mvn compile`

---

### Issue #56: Implement dead peer detection and eviction

**Labels:** `sprint-9`, `milestone-9c`, `enhancement`

**Description:**

Extend the heartbeat task to detect and evict peers that haven't responded
within the timeout period.

**Implementation:**
- In heartbeat task, after sending PINGs:
  - Check each connected peer's lastSeen timestamp
  - If `(now - lastSeen) > PEER_TIMEOUT_MS`, mark as disconnected
  - Close the connection to timed-out peers
- Add `PeerManager.getTimedOutPeers(long timeoutMs)` helper method

**Acceptance Criteria:**
- [ ] Dead peer detection based on lastSeen + timeout
- [ ] Timed-out peers marked DISCONNECTED
- [ ] Connection closed for dead peers
- [ ] `getTimedOutPeers()` helper added to PeerManager
- [ ] Compiles with `mvn compile`

---

### Issue #57: Unit tests for heartbeat mechanism

**Labels:** `sprint-9`, `milestone-9c`, `test`

**Description:**

Write unit tests for the heartbeat and dead peer eviction.

**Test Cases:**
1. `heartbeat_sendsPingToConnectedPeers` - Verify PINGs are being sent
2. `pongReceived_updatesLastSeen` - PONG updates peer's lastSeen
3. `timedOutPeer_isDetected` - Peer past timeout is flagged
4. `timedOutPeer_isEvicted` - Dead peer gets disconnected
5. `activePeer_isNotEvicted` - Healthy peer stays connected

**Acceptance Criteria:**
- [ ] Tests added (HeartbeatTest.java or added to NodeTest.java)
- [ ] All tests passing
- [ ] `mvn test` green

---

## Milestone 9d: Peer Discovery

---

### Issue #58: Create GetPeersMessage and PeersMessage classes

**Labels:** `sprint-9`, `milestone-9d`, `enhancement`

**Description:**

Create two new concrete message classes for peer discovery.

**GetPeersMessage:**
- Extends Message
- Type: `MessageType.GET_PEERS`
- No extra payload (just "send me your peers")
- Constructor: `GetPeersMessage(String nodeId)`

**PeersMessage:**
- Extends Message
- Type: `MessageType.PEERS`
- Payload: list of peer addresses (as `List<String>` in "host:port" format)
- Constructor: `PeersMessage(String nodeId, List<String> peerAddresses)`
- Getter: `getPeerAddresses()`

**Also update:**
- `MessageParser.parse()` - add cases for GET_PEERS and PEERS types

**Acceptance Criteria:**
- [ ] `GetPeersMessage.java` created in `network/messages/`
- [ ] `PeersMessage.java` created in `network/messages/`
- [ ] MessageParser updated to handle both types
- [ ] THEORY comments added
- [ ] Compiles with `mvn compile`

---

### Issue #59: Implement GET_PEERS and PEERS handlers in Node

**Labels:** `sprint-9`, `milestone-9d`, `enhancement`

**Description:**

Register handlers in Node for the peer discovery protocol.

**GET_PEERS handler:**
- When a peer asks for our peer list
- Get all known peer addresses from PeerManager
- Send back a PeersMessage with the list
- Don't include the requesting peer in the list

**PEERS handler:**
- When we receive a peer list from another node
- For each address in the list:
  - Skip if it's our own address
  - Skip if already known
  - Add as DISCOVERED to PeerManager
- Optionally: connect to newly discovered peers (if under MAX_PEERS)

**Acceptance Criteria:**
- [ ] GET_PEERS handler registered in registerDefaultHandlers()
- [ ] PEERS handler registered in registerDefaultHandlers()
- [ ] Requesting peer excluded from response
- [ ] Own address excluded from processing
- [ ] New peers added as DISCOVERED
- [ ] Compiles with `mvn compile`

---

### Issue #60: Add seed nodes config and bootstrap on startup

**Labels:** `sprint-9`, `milestone-9d`, `enhancement`

**Description:**

Add the ability to configure seed nodes and bootstrap the network on startup.

**Changes to NetworkConfig:**
- Add `SEED_NODES` - `List<String>` of default seed node addresses (empty by default)

**Changes to Node:**
- Add `setSeedNodes(List<String> seeds)` method (call before start)
- In `start()`, after server socket is ready:
  - For each seed node, attempt `connectToPeer(host, port)`
  - After connecting, send `GetPeersMessage` to request their peer list
  - Log success/failure for each seed

**Acceptance Criteria:**
- [ ] Seed nodes configurable on Node
- [ ] Bootstrap attempts connection to each seed
- [ ] GET_PEERS sent after successful connection
- [ ] Failed seed connections logged but don't prevent startup
- [ ] Compiles with `mvn compile`

---

### Issue #61: Unit tests for peer discovery

**Labels:** `sprint-9`, `milestone-9d`, `test`

**Description:**

Write integration tests for the full peer discovery flow.

**Test Cases:**
1. `getPeersMessage_serializesCorrectly` - JSON round-trip works
2. `peersMessage_serializesWithAddressList` - Peer list serialized correctly
3. `getPeersHandler_respondsWithPeerList` - Node responds to GET_PEERS
4. `peersHandler_addsNewPeers` - Received peers added to PeerManager
5. `bootstrap_connectsToSeedNodes` - Node connects to seeds on startup
6. `discovery_nodeFindsNewPeers` - Full flow: A connects to B, discovers C

**Acceptance Criteria:**
- [ ] `PeerDiscoveryTest.java` created in `test/network/`
- [ ] All 6 tests passing
- [ ] All existing tests still passing
- [ ] `mvn test` green

---

## Summary

| Milestone | Issues | Test Count |
|-----------|--------|------------|
| 9a: PeerInfo | #47, #48, #49 | 6 tests |
| 9b: PeerManager | #50, #51, #52, #53 | 8 tests |
| 9c: Heartbeat | #54, #55, #56, #57 | 5 tests |
| 9d: Peer Discovery | #58, #59, #60, #61 | 6 tests |
| **Total** | **15 issues** | **~25 tests** |

---

*Created: 2026-02-09 | Sprint 9 Planning*
