package com.blocksmith.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PeerManagerTest {
    
    private PeerManager peerManager;

    @BeforeEach
    void setUp() {
        peerManager = new PeerManager();
    }

    // ===== ADD/REMOVE TESTS =====

    @Test
    @DisplayName("addPeer stores peer in registry")
    void addPerrStoresPeerInRegistry() {
        PeerInfo info = new PeerInfo("192.168.1.1", 8333);
        boolean added = peerManager.addPeer(info);

        assertTrue(added, "Peer should be added successfully");
        assertNotNull(peerManager.getPeer("192.168.1.1:8333"), 
            "Peer should be retrievable by address");
    }

    @Test
    @DisplayName("removePeer removes peer from registry")
    void removePeerRemovesPeerFromRegistry() {
        PeerInfo info = new PeerInfo("192.168.1.1", 8333);
        peerManager.addPeer(info);
        peerManager.removePeer("192.168.1.1:8333");

        assertNull(peerManager.getPeer("192.168.1.1:8333"),
            "Peer should be removed from registry");
        assertFalse(peerManager.isKnown("192.168.1.1:8333"),
            "Peer should be marked as not known");
    }

    // ===== QUERY TESTS =====

    @Test
    @DisplayName("getConnectedPeers returns only CONNECTED peers")
    void getConnectedPeersReturnsOnlyConnectedPeers() {
        PeerInfo connected1 = new PeerInfo("10.0.0.1", 8333);
        connected1.markConnected("node-aaa");
        PeerInfo connected2 = new PeerInfo("10.0.0.2", 8333);
        connected2.markConnected("node-bbb");
        PeerInfo discovered1 = new PeerInfo("10.0.0.3", 8333);

        peerManager.addPeer(connected1);
        peerManager.addPeer(connected2);
        peerManager.addPeer(discovered1);

        assertEquals(2, peerManager.getConnectedPeers().size(),
            "Should return only CONNECTED peers");
        assertEquals(3, peerManager.getKnownPeers().size(),
            "Should return all known peers");
    }

    @Test
    @DisplayName("isKnown returns true for existing peer")
    void isKnownReturnsTrueForExistingPeer() {
        PeerInfo info = new PeerInfo("192.168.1.1", 8333);
        peerManager.addPeer(info);

        assertTrue(peerManager.isKnown("192.168.1.1:8333"),
            "Should return true for known peer");
        assertFalse(peerManager.isKnown("192.168.1.2:8333"),
            "Should return false for unknown peer");
    }

    @Test
    @DisplayName("getConnectedCount tracks correctly")
    void getConnectedCountTracksCorrectly() {
        assertEquals(0, peerManager.getConnectedCount(), "Initially no connected peers");

        PeerInfo info1 = new PeerInfo("10.0.0.1", 8333);
        info1.markConnected("node-aaa");
        peerManager.addPeer(info1);

        PeerInfo info2 = new PeerInfo("10.0.0.2", 8333);
        peerManager.addPeer(info2); // DISCOVERED, not CONNECTED

        assertEquals(1, peerManager.getConnectedCount(),
                "Only CONNECTED peers should be counted");
    }

    // ===== LIMIT TESTS =====

    @Test
    @DisplayName("canAcceptMore respects MAX_PEERS limit")
    void canAcceptMore_respectsMaxPeers() {
        PeerManager smallManager = new PeerManager(2);

        PeerInfo info1 = new PeerInfo("10.0.0.1", 8333);
        info1.markConnected("node-aaa");
        PeerInfo info2 = new PeerInfo("10.0.0.2", 8333);
        info2.markConnected("node-bbb");

        smallManager.addPeer(info1);
        assertTrue(smallManager.canAcceptMore(), "Should accept more with 1 of 2");

        smallManager.addPeer(info2);
        assertFalse(smallManager.canAcceptMore(), "Should not accept more at limit");
    }

    @Test
    @DisplayName("addPeer rejects connected peer when at MAX_PEERS")
    void addPeer_rejectsWhenAtLimit() {
        PeerManager smallManager = new PeerManager(1);

        PeerInfo info1 = new PeerInfo("10.0.0.1", 8333);
        info1.markConnected("node-aaa");
        smallManager.addPeer(info1);

        PeerInfo info2 = new PeerInfo("10.0.0.2", 8333);
        info2.markConnected("node-bbb");
        boolean added = smallManager.addPeer(info2);

        assertFalse(added, "Should reject connected peer when at limit");
    }

    @Test
    @DisplayName("addPeer rejects duplicate peer")
    void addPeer_rejectsDuplicate() {
        PeerInfo info = new PeerInfo("192.168.1.1", 8333);
        assertTrue(peerManager.addPeer(info), "First add should succeed");
        assertFalse(peerManager.addPeer(info), "Duplicate add should be rejected");
    }
}
