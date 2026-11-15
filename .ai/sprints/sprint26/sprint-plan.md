# Sprint 26: Networking for Real: Joining, Identity, Discovery

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 26 |
| **Title** | Networking for Real: Joining, Identity, Discovery |
| **Phase** | Hardening (after the roadmap) |
| **Status** | 🔄 In progress |
| **Depends On** | Sprints 8-11 (P2P layer), Sprint 21 (sync), Sprint 25 (release v4.6.0) |

> **Why:** the deferred list said "nodeId is random per start" and "no
> auto-connect to discovered peers". A probe on master (not committed) found
> that the gap is bigger: **two real nodes cannot connect at all.**
>
> 1. Two `BlockSmithNode` processes (P2P 18601 / 18602, API 18681 / 18682);
>    3 blocks mined on A
> 2. A: chain length 4; **B: chain length 1**; both peer lists empty; B sees
>    Alice's balance as 0
>
> There is no way in: no peer argument, `SEED_NODES` is empty, no API to
> connect, and nothing in the main code ever sends `GET_PEERS`. The whole P2P
> layer runs only in tests that call `connectToPeer` themselves.
>
> In those tests (Java probe):
> - after B restarts, A still lists B as CONNECTED and `connectToPeer` throws
>   **"Already connected"**: a restarted peer can never be reached again
> - B's `nodeId` changes on restart (`node-a692...` -> `node-71bb...`)
> - C asks B for peers and stores **its own address** (`127.0.0.1:21603`) as
>   a discovered peer; the same node also appears as `localhost` and
>   `127.0.0.1`

---

## Goal

A node started from the command line joins a network (peer addresses on the
command line or from the dashboard), keeps one identity across restarts,
recognises itself and duplicate links by that identity, finds more peers
through `GET_PEERS` and connects to them up to a limit, and reconnects to a
peer that came back. Three real processes keep one chain even when the node
in the middle dies.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **26a** | Joining: peer arguments, connect API, reconnect | `sprint26a/joining` | ✅ Done (#375, #376) |
| **26b** | Identity and discovery | `sprint26b/identity-discovery` | ✅ Done (#377, #378) |
| **26c** | Mempool both ways, dashboard peers, live network, docs | `sprint26c/live-network` | ⬜ Planned (#379, #380) |

---

## Milestone 26a: Joining

### Deliverables

- [x] `BlockSmithNode`: `--peer host:port` (repeatable) and
      `-Dblocksmith.peers=a:1,b:2`; connected after start (best effort,
      retried by the heartbeat)
- [x] `POST /api/network/peers {address}` connects to a peer (400 for a
      bad address, 409 when already connected, 502 when unreachable)
- [x] Reconnect: a peer that is DISCOVERED or DISCONNECTED can be connected
      again; an outbound link that drops is marked DISCONNECTED at once
- [x] Tests: the probe (two nodes started like `BlockSmithNode` share
      blocks); reconnect after a peer restarts; the API errors

---

## Milestone 26b: Identity and discovery

### Deliverables

- [x] Persistent `nodeId` in `<dataDir>/node-id` (created once)
- [x] Handshake by identity: a HELLO carrying OUR nodeId = we dialled
      ourselves (closed, address remembered as self); a second link to a
      nodeId we are already connected to is closed (localhost vs 127.0.0.1)
- [x] Discovery: the heartbeat asks connected peers for `GET_PEERS`; `PEERS`
      advertises only listening addresses we have connected to (not the
      asker, not ourselves); DISCOVERED peers are dialled while outbound
      links < 4 (never above `MAX_PEERS`); a peer that fails is retried later
      with a back-off
- [x] Tests: same nodeId after a restart; self-dial refused; duplicate link
      closed; A-B + C-B: A finds C and connects; the limit holds

---

## Milestone 26c: Mempool both ways, dashboard, live network, docs

### Deliverables

- [ ] The inbound side asks for the peer's mempool too (deferred item)
- [ ] Dashboard: the network panel lists peers (nodeId, address, state,
      direction) and connects to an address
- [ ] Live: three processes, A and C both joined to B only; A discovers C;
      B is killed; a block mined on A reaches C; B restarts and catches up
- [ ] Docs: ARCHITECTURE (joining, identity, discovery), STATUS (four
      network items leave the deferred list), README (how to run a network)

---

## Theory

### How a node finds the network

```
 start  -> peers from the command line (Bitcoin: -addnode / DNS seeds)
 HELLO  -> nodeId + listening port: who is this, and how to reach them
 every heartbeat: GET_PEERS to connected peers -> PEERS (addresses)
 DISCOVERED addresses -> dial while outbound < 4 (Bitcoin: 8 outbound)
```

A node is identified by its nodeId, not by its address: one machine is
`localhost`, `127.0.0.1` and its LAN address at once, and an address can
change. With a stable id a node knows when it dialled itself (the HELLO
that comes back carries its own id) and when two links lead to the same
peer. Bitcoin nodes do the same with a random nonce in their `version`
message. The id is not a security feature: anyone can claim any id. It is
a label for routing, not an identity to trust; blocks and transactions are
still checked on their own.

---

## Open decisions

- **Peers on the command line + an API to connect** (recommended) vs only
  a config file
- **nodeId in `<dataDir>/node-id`** (recommended: it lives and dies with the
  node's data) vs derived from a key
- **Dial while outbound < 4, `MAX_PEERS` 10** (recommended) vs dial every
  discovered peer
- **Discovery every 3rd heartbeat (~30 s)** (recommended) vs every heartbeat

---

*Created: 2026-11-15 | Sprint 26 Planning*
