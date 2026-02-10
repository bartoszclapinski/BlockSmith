package com.blocksmith.network;

/**
 * THEORY: Peer Metadata Tracking
 * 
 * In P2P network, a node needs to know more than just "who exists".
 * For each pee, we track metadata that helps manage the connection:
 * 
 * WHAT WE TRACK:
 * - Identity: nodeId, host, port
 * - State: DISCOVERED -> CONNECTED -> DISCONNECTED
 * - Timing: when we last heard from them (lastSeen), when they connected (connectedAt)
 * 
 * WHY TIMING MATTERS:
 * - lastSeen is updated every time we receive a message (especially PONG)
 * - If (now - lastSeen) > timeout, the peer is probably dead
 * - This is the foundation for heartbeat-based eviction (sprint 9c)
 * 
 * BITCOIN COMPARSION:
 * Vitcoin Core's CNode class tracks similar metadata: address, connection time,
 * last send/receive timestamps, and connection state. Our PeerInfo is a 
 * simplified version of the same concept.
 */
public class PeerInfo {
    
    private String nodeId;
    private final String host;
    private final int port;
    private PeerState state;
    private long lastSeen;
    private long connectedAt;

    /**
     * Creates a new PeerInfo in DISCOVERED state.
     * The peer is known but not yet connected.
     * 
     * @param host IP address or hostname of the peer
     * @param port listening port of the peer
     */
    public PeerInfo(String host, int port) {
        this.host = host;
        this.port = port;
        this.state = PeerState.DISCOVERED;
        this.lastSeen = 0;
        this.connectedAt = 0;
    }

    /**
     * Transition peer to CONNECTED state.
     * Called after a successful TCP handshake.
     *
     * @param nodeId the remote node's unique identifier
     */
    public void markConnected(String nodeId) {
        this.nodeId = nodeId;
        this.state = PeerState.CONNECTED;
        this.connectedAt = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
    }

    /**
     * Transition peer to DISCONNECTED state.
     * Called when the connection is lost or closed.
     */
    public void markDisconnected() {
        this.state = PeerState.DISCONNECTED;
    }

    /**
     * Update the lastSeen timestamp to now.
     * Called every time we receive a message from this peer.
     */
    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    /**
     * Returns the peer address in "host:port" format.
     * Used as a unique key in the PeerManager registry.
     * 
     * @return address string in "host:port" format
     */
    public String getAddress() {
        return host + ":" + port;
    }

    // === Getters ===

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public PeerState getState() {
        return state;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    @Override
    public String toString() {
        return "PeerInfo{" +
            "nodeId='" + nodeId + '\'' +
            ", address=" + getAddress() +
            ", state=" + state +
            ", lastSeen=" + lastSeen +
            '}';
    }

}
