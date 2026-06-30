package com.blocksmith.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the heartbeat mechanism in Node - dead peer detection and eviction.
 *
 * The node is created but never started: heartbeatTask() only reads nodeId,
 * peerManager, and peerWriters, all available without binding a socket. This
 * keeps the tests free of ports and real timing.
 */
@DisplayName("Heartbeat Tests")
class HeartbeatTest {

    private Node node;

    private static final int TEST_PORT_BASE = 19500;
    private static int portCounter = 0;

    private int getNextPort() {
        return TEST_PORT_BASE + (portCounter++);
    }

    @AfterEach
    void tearDown() {
        if (node != null) {
            node.stop();
        }
    }

    // ===== REFLECTION HELPERS =====

    /** Invokes the private heartbeatTask() one time. */
    private void runHeartbeat(Node target) throws Exception {
        Method task = Node.class.getDeclaredMethod("heartbeatTask");
        task.setAccessible(true);
        task.invoke(target);
    }

    /** Forces a peer's lastSeen to a specific timestamp (no public setter exists). */
    private void setLastSeen(PeerInfo peer, long millis) throws Exception {
        Field lastSeen = PeerInfo.class.getDeclaredField("lastSeen");
        lastSeen.setAccessible(true);
        lastSeen.setLong(peer, millis);
    }

    private PeerInfo connectedPeer(String host, int port, String nodeId) {
        PeerInfo peer = new PeerInfo(host, port);
        peer.markConnected(nodeId);
        return peer;
    }

    // ===== EVICTION TESTS =====

    @Test
    @DisplayName("heartbeat evicts peer whose lastSeen exceeds PEER_TIMEOUT_MS")
    void heartbeat_evictsDeadPeer() throws Exception {
        node = new Node(getNextPort());
        PeerInfo dead = connectedPeer("10.0.0.1", 9001, "node-dead");
        setLastSeen(dead, System.currentTimeMillis() - NetworkConfig.PEER_TIMEOUT_MS - 1000);
        node.getPeerManager().addPeer(dead);

        runHeartbeat(node);

        assertFalse(node.getPeerManager().isKnown("10.0.0.1:9001"),
                "Peer past the timeout should be evicted");
    }

    @Test
    @DisplayName("heartbeat keeps peer seen within PEER_TIMEOUT_MS")
    void heartbeat_keepsLivePeer() throws Exception {
        node = new Node(getNextPort());
        PeerInfo live = connectedPeer("10.0.0.2", 9002, "node-live");
        node.getPeerManager().addPeer(live);

        runHeartbeat(node);

        assertTrue(node.getPeerManager().isKnown("10.0.0.2:9002"),
                "Recently seen peer should not be evicted");
    }

    @Test
    @DisplayName("heartbeat evicts only stale peers, keeps live ones")
    void heartbeat_evictsOnlyStalePeers() throws Exception {
        node = new Node(getNextPort());
        PeerInfo dead = connectedPeer("10.0.0.3", 9003, "node-dead");
        setLastSeen(dead, System.currentTimeMillis() - NetworkConfig.PEER_TIMEOUT_MS - 1000);
        PeerInfo live = connectedPeer("10.0.0.4", 9004, "node-live");

        node.getPeerManager().addPeer(dead);
        node.getPeerManager().addPeer(live);

        runHeartbeat(node);

        assertFalse(node.getPeerManager().isKnown("10.0.0.3:9003"),
                "Stale peer should be evicted");
        assertTrue(node.getPeerManager().isKnown("10.0.0.4:9004"),
                "Live peer should remain");
        assertEquals(1, node.getPeerManager().getConnectedCount(),
                "Only the live peer should remain connected");
    }

    // ===== CONFIG INVARIANT =====

    @Test
    @DisplayName("peer timeout must exceed the heartbeat interval")
    void heartbeatConfig_timeoutLongerThanInterval() {
        assertTrue(NetworkConfig.PEER_TIMEOUT_MS > NetworkConfig.HEARTBEAT_INTERVAL_MS,
                "A peer must be pinged at least once before it can time out");
    }
}
