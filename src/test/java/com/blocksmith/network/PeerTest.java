package com.blocksmith.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Peer class - client-side connection functionality.
 */
@DisplayName("Peer Tests")
class PeerTest {

    private Node node;
    private Peer peer;
    
    // Use different ports for each test to avoid conflicts
    private static final int TEST_PORT_BASE = 19100;
    private static int portCounter = 0;
    
    private int getNextPort() {
        return TEST_PORT_BASE + (portCounter++);
    }

    @AfterEach
    void tearDown() {
        if (peer != null) {
            peer.disconnect();
        }
        if (node != null) {
            node.stop();
        }
    }

    // ===== PEER CREATION TESTS =====

    @Test
    @DisplayName("Peer should store host and port")
    void peer_creation_storesHostAndPort() {
        peer = new Peer("localhost", 8080);
        
        assertEquals("localhost", peer.getHost(), "Host should be stored");
        assertEquals(8080, peer.getPort(), "Port should be stored");
        assertFalse(peer.isConnected(), "Should not be connected initially");
    }

    @Test
    @DisplayName("Peer should not be connected before calling connect()")
    void peer_beforeConnect_isNotConnected() {
        peer = new Peer("localhost", 8080);
        
        assertFalse(peer.isConnected(), "Peer should not be connected before connect()");
        assertNull(peer.getRemoteNodeId(), "Remote node ID should be null");
    }

    // ===== CONNECTION TESTS =====

    @Test
    @DisplayName("Peer should connect to running Node")
    void peer_connect_shouldConnectToNode() throws IOException, InterruptedException {
        int port = getNextPort();
        
        // Start a node
        node = new Node(port);
        node.start();
        Thread.sleep(100); // Wait for node to start listening
        
        // Connect peer
        peer = new Peer("localhost", port);
        peer.connect();
        
        assertTrue(peer.isConnected(), "Peer should be connected after connect()");
    }

    @Test
    @DisplayName("Peer should disconnect gracefully")
    void peer_disconnect_shouldDisconnect() throws IOException, InterruptedException {
        int port = getNextPort();
        
        // Start a node
        node = new Node(port);
        node.start();
        Thread.sleep(100);
        
        // Connect and disconnect
        peer = new Peer("localhost", port);
        peer.connect();
        assertTrue(peer.isConnected(), "Peer should be connected");
        
        peer.disconnect();
        assertFalse(peer.isConnected(), "Peer should be disconnected after disconnect()");
    }

    @Test
    @DisplayName("Connecting twice should throw exception")
    void peer_connectTwice_shouldThrowException() throws IOException, InterruptedException {
        int port = getNextPort();
        
        node = new Node(port);
        node.start();
        Thread.sleep(100);
        
        peer = new Peer("localhost", port);
        peer.connect();
        
        assertThrows(IllegalStateException.class, () -> peer.connect(),
                "Connecting twice should throw IllegalStateException");
    }

    // ===== HANDSHAKE TESTS =====

    @Test
    @DisplayName("Peer should perform handshake and receive remote node ID")
    void peer_handshake_shouldReceiveRemoteNodeId() throws IOException, InterruptedException {
        int port = getNextPort();
        
        // Start a node
        node = new Node(port);
        node.start();
        Thread.sleep(100);
        
        // Connect and handshake
        peer = new Peer("localhost", port);
        peer.connect();
        peer.performHandshake("test-peer-id", 9999, 0);
        
        // Wait a bit for handshake to complete
        Thread.sleep(200);
        
        assertNotNull(peer.getRemoteNodeId(), "Remote node ID should be set after handshake");
        assertTrue(peer.getRemoteNodeId().startsWith(NetworkConfig.NODE_ID_PREFIX),
                "Remote node ID should have correct prefix");
    }

    // ===== FAILURE TESTS =====

    @Test
    @DisplayName("Connecting to non-existent node should fail")
    void peer_connectToNonExistent_shouldFail() {
        peer = new Peer("localhost", 19999); // Port with no node
        
        assertThrows(IOException.class, () -> peer.connect(),
                "Connecting to non-existent node should throw IOException");
    }
}