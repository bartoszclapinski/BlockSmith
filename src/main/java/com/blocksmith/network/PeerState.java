package com.blocksmith.network;

/**
 * THEORY: Peer Connection Lifecycle
 *
 * In a P2P network, each peer goes through a series of states
 * as the node learns about it, connects, and eventually disconnects.
 *
 * LIFECYCLE:
 *   DISCOVERED ──► CONNECTED ──► DISCONNECTED
 *       │                              │
 *       └──────────────────────────────┘
 *              (can be re-discovered)
 *
 * DISCOVERED:
 * - We know this peer exists (e.g. received its address from another node)
 * - No TCP connection yet — just an address in our registry
 *
 * CONNECTED:
 * - Active TCP connection established and handshake completed
 * - We can send and receive messages with this peer
 *
 * DISCONNECTED:
 * - Was previously connected but the connection was lost or closed
 * - Could be due to timeout, network error, or graceful shutdown
 * - May be re-discovered and reconnected later
 *
 * BITCOIN COMPARISON:
 * Bitcoin Core tracks similar states internally — nodes move from
 * "addr" (discovered via address gossip) to "connected" to "disconnected".
 * Dead peers are eventually forgotten if they don't come back online.
 */
public enum PeerState {

    /** We know about this peer but haven't connected yet */
    DISCOVERED,

    /** Active TCP connection established */
    CONNECTED,

    /** Was connected but connection was lost or closed */
    DISCONNECTED
}
