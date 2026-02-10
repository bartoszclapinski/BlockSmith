package com.blocksmith.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeerInfoTest {

    private PeerInfo peerInfo;

    @BeforeEach
    void setUp() {
        peerInfo = new PeerInfo("127.0.0.1", 8333);
    }

    // ===== CREATION TESTS =====

    @Test
    @DisplayName("Constructor sets host, port and DISCOVERED state")
    void constructor_setsHostPortAndDiscoveredState() {
        assertEquals("127.0.0.1", peerInfo.getHost(), "Host should be set");
        assertEquals(8333, peerInfo.getPort(), "Port should be set");
        assertEquals(PeerState.DISCOVERED, peerInfo.getState(), "Initial state should be DISCOVERED");
        assertNull(peerInfo.getNodeId(), "NodeId should be null before connection");
    }

    // ===== STATE TRANSITION TESTS =====

    @Test
    @DisplayName("markConnected sets state to CONNECTED and assigns nodeId")
    void markConnected_setsStateAndNodeId() {
        peerInfo.markConnected("node-abc-123");

        assertEquals(PeerState.CONNECTED, peerInfo.getState(), "State should be CONNECTED");
        assertEquals("node-abc-123", peerInfo.getNodeId(), "NodeId should be set");
        assertTrue(peerInfo.getConnectedAt() > 0, "ConnectedAt should be set");
        assertTrue(peerInfo.getLastSeen() > 0, "LastSeen should be set on connect");
    }

    @Test
    @DisplayName("markDisconnected sets state to DISCONNECTED")
    void markDisconnected_setsState() {
        peerInfo.markConnected("node-abc-123");
        peerInfo.markDisconnected();

        assertEquals(PeerState.DISCONNECTED, peerInfo.getState(), "State should be DISCONNECTED");
    }

    // ===== TIMESTAMP TESTS =====

    @Test
    @DisplayName("updateLastSeen updates the timestamp")
    void updateLastSeen_updatesTimestamp() throws InterruptedException {
        peerInfo.markConnected("node-abc-123");
        long firstSeen = peerInfo.getLastSeen();

        Thread.sleep(50);
        peerInfo.updateLastSeen();

        assertTrue(peerInfo.getLastSeen() > firstSeen, "LastSeen should be updated to a later time");
    }

    // ===== ADDRESS TESTS =====

    @Test
    @DisplayName("getAddress returns host:port format")
    void getAddress_returnsHostColonPort() {
        assertEquals("127.0.0.1:8333", peerInfo.getAddress(), "Address should be in host:port format");
    }

    // ===== ENUM TESTS =====

    @Test
    @DisplayName("PeerState enum has exactly three values")
    void peerState_hasThreeValues() {
        PeerState[] values = PeerState.values();
        assertEquals(3, values.length, "PeerState should have exactly 3 values");
        assertEquals(PeerState.DISCOVERED, values[0], "First value should be DISCOVERED");
        assertEquals(PeerState.CONNECTED, values[1], "Second value should be CONNECTED");
        assertEquals(PeerState.DISCONNECTED, values[2], "Third value should be DISCONNECTED");
    }
}
