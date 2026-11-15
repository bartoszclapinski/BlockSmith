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

## Milestone 26a: Joining - Not started

## Milestone 26b: Identity and discovery - Not started

## Milestone 26c: Mempool both ways, dashboard, live network, docs - Not started

---

*Sprint 26 Log*
