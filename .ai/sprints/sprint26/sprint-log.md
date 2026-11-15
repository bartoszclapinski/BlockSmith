# Sprint 26: Networking for Real - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-15 |
| **Sprint End** | - |

---

## Planning probe (not committed)

1. Live: two `BlockSmithNode` processes (P2P 18601 / 18602, API 18681 /
   18682), 3 blocks mined on A. A: chain length 4, B: chain length 1, both
   `/api/network/peers` empty, B shows Alice's balance as 0. Nothing in the
   main code can connect two nodes.
2. Java (nodes connected by the test): after B restarted on the same port, A
   still listed B as CONNECTED and `connectToPeer` threw "Already connected
   to localhost:21602". B's nodeId changed (`node-a692534a...` ->
   `node-71bb6858...`). C, connected to B, sent `GET_PEERS` and stored its own
   address `127.0.0.1:21603` as DISCOVERED (and B as `localhost:21602`).

---

## Milestone 26a: Joining - Done (#375, #376)

- `BlockSmithNode`: `parseArgs` (positional ports, repeatable `--peer host:port`,
  `-Dblocksmith.peers=a:1,b:2`, `--restore`; unknown options and bad addresses
  or ports exit with a message) and `launch` (returns a `Running` handle), so
  tests start nodes exactly like `main`
- `Node`: persistent peers (`addPersistentPeer`) dialled at start in the
  background and redialled by `maintainConnections()`, which the heartbeat
  runs off its thread; `connectToPeer(address)`; `parsePeerAddress` is public
  and checks the port range
- Reconnect: `connectToPeer` refuses only a CONNECTED address;
  `PeerManager.register` replaces a DISCOVERED / DISCONNECTED entry (the
  inbound side used `addPeer`, so a restarted peer that dialled in again was
  never registered and got no broadcasts); a dropped outbound link loses its
  writer at once
- Catch-up on connect: both sides send `GET_BLOCKS` with their locator right
  after the handshake (before, a node that joined late waited for the next
  mined block)
- `stop()` closes inbound sockets first: before, a stopped node still looked
  connected until the other side's next PING (up to 10 s) and `stop()` waited
  5 s on the blocked threads
- One `PrintWriter` per outbound link for the heartbeat, broadcasts and
  handler replies (it locks on itself, so lines do not interleave)
- `POST /api/network/peers {address}`: localhost only (403), 400 bad address,
  409 already connected / full, 502 unreachable, 201 with the peer's nodeId;
  the peer is kept and redialled
- `JoiningTest` (3): the probe with `--peer` (catch-up, blocks both ways,
  Alice's balance on B, `-Dblocksmith.peers` and bad input); restart of the
  dialling side and of the dialled side (drop seen within 2 s); the API codes
- `CommunicationTest`: raw-peer listeners skip the node's `GET_BLOCKS`
  catch-up request
- Mutations (6, all killed): no `GET_BLOCKS` after dialling; `isKnown` blocks
  reconnect; inbound keeps the stale entry; `stop()` leaves inbound sockets
  open (killed after the 2 s drop check was added); the API does not keep
  the peer; no port range check
- Tests: 448/448

## Milestone 26b: Identity and discovery - Done (#377, #378)

- `Node.loadOrCreateNodeId(dataDir)`: the id lives in `<dataDir>/node-id`
  (created once; a file without a valid id is replaced); `BlockSmithNode`
  passes it to the new `Node(port, chain, nodeId)`; in-memory nodes keep a
  random id
- Self: an inbound HELLO with our own id is answered (so the dialler learns)
  and not registered; the dialler drops the link, remembers the address in
  `selfAddresses` and refuses it from then on (409 from the connect API)
- Duplicates: every handshake records address -> nodeId, so another name for
  a linked node is refused before dialling. A second link found after the
  HELLO is settled by the dialling side under `linkLock`: our own existing
  link wins; between their link and ours, the one opened by the smaller id
  stays (both ends agree, so two nodes dialling each other at once keep one
  link, not zero). The losing inbound link is marked DISCONNECTED at once
  and its socket closed
- Discovery: `GET_PEERS` right after an outbound handshake (Bitcoin:
  getaddr) and every 3rd heartbeat (`askForPeers`); the reply lists the
  addresses of CONNECTED peers, one per nodeId, never the asker or us;
  `PEERS` skips our own and already-linked addresses, records the rest as
  DISCOVERED and runs `maintainConnections` soon
- Auto-connect in `maintainConnections`: dials DISCOVERED / DISCONNECTED
  addresses while outbound links < `TARGET_OUTBOUND` (4) and below
  `MAX_PEERS` (10); a failed dial backs off 10 s, doubling up to 5 min
- `PeerInfo.isOutbound()`; `GET /api/network/peers` adds `direction`,
  `connectedAt`, `lastSeen`
- `IdentityDiscoveryTest` (5): id kept across a restart (and seen by a
  peer); self-dial refused, remembered, never a peer (also under
  `localhost`, which the inbound side sees as 127.0.0.1); duplicate links
  with fixed ids (`node-000...1` / `node-fff...f`) for both tie-break
  outcomes, the surviving link carries blocks both ways; A and C find each
  other through B without a heartbeat, B's reply is just C, the heartbeat
  asks every 3rd beat, A's block reaches C after B stops; 7 nodes on a hub:
  the newcomer stops at 4 outbound, nobody above 10, an unreachable address
  backs off
- Mutations (13): 12 killed (persisted id; inbound self check; dialler self
  check; inverted and disabled tie-break; asker in the reply; own address
  from PEERS; heartbeat never asks; no auto-connect; no outbound limit; no
  back-off; no GET_PEERS on connect). Survived: `closeInbound` without the
  immediate mark: closing the socket ends the other thread before
  `connectToPeer` returns, so only timing differs (kept: it is free)
- The first mutation run used `-Dtest=A+B` (not a Surefire class list: Maven
  failed before any test ran and every mutation looked killed); rerun with
  `-Dtest=A,B` and stale reports deleted before each run
- Tests: 453/453

## Milestone 26c: Mempool both ways, dashboard, live network, docs - Done (#379, #380)

- The inbound side sends `GET_MEMPOOL` after the handshake too (it already
  sent `GET_BLOCKS` since 26a): a dialled node now sees the dialler's
  pending transactions
- Dashboard **Peers** card: → outbound / ← inbound, address, short id,
  state, a count badge; a `host:port` form (client check, then the API's
  400 / 409 / 502 messages as toasts); palette command "Connect to peer";
  a tour step. The contracts card got a minimum height (the new card had
  squeezed its empty-state text out of view at 1440 x 900)
- `NetworkPanelTest` (2): `inboundSide_pullsMempool` (B dials A with a block
  and a pending tx A lacks; A gets both); the served page and script carry
  the panel, and the same link reads outbound on one end and inbound on
  the other. Mutation: no inbound `GET_MEMPOOL` -> killed
- `CommunicationTest`: raw-peer listeners also skip `GET_MEMPOOL`
- Docs: ARCHITECTURE (BlockSmithNode `--peer`, network table, "Joining,
  identity, discovery" section), STATUS (four network items leave the
  deferred list; reachability of advertised addresses and ban scores
  added), README ("Run a network of nodes")
- Tests: 455/455

### Live: three processes, then a fourth from the dashboard

Real `BlockSmithNode` processes (P2P 18601-18604, API 18681-18684), data in
the scratchpad:

1. B alone; A `--peer localhost:18602`; C `--peer localhost:18602`. After
   5 s: A has B (out) and C (in), C has B (out) and A (out): **C heard of
   A from B and dialled it**. All three chain length 1
2. 2 blocks mined on A: all three length 3, same tip `0000fb9c85`
3. B hard-killed (`Stop-Process -Force`): A and C drop to 1 peer at once
4. 1 block mined on A while B is down: **C has it** (length 4, tip
   `00003635c8`) over the A-C link
5. B restarted with no `--peer`: back after 7 s with **the same nodeId**
   (`node-c58280de7c2393e3`); A and C redialled it (persistent peers),
   B caught up to length 4
6. D started alone (18604/18684), dashboard in headless Edge: empty state;
   `nonsense` -> client message; `localhost:1` -> "Could not reach";
   `localhost:18601` -> "Connected to localhost:18601 (node-1b03...)";
   then **D dialled B and C by itself** (3 outbound, badge 3, header
   "3 peers"), chain length 4; same address again -> "Already connected";
   390 px: no horizontal scroll; palette command focuses the input; no
   page errors (14/14 checks)
7. All PIDs stopped; no java.exe or headless Edge left; no exceptions in
   the node logs

---

*Sprint 26 Log*
