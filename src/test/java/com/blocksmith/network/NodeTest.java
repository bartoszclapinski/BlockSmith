package com.blocksmith.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Node class - network server functionality.
 */
@DisplayName("Node Tests")
class NodeTest {

    private Node node;
    
    // Use different ports for each test to avoid conflicts
    private static final int TEST_PORT_BASE = 19000;
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

    // ===== NODE CREATION TESTS =====

    @Test
    @DisplayName("Node should generate unique ID on creation")
    void node_creation_generatesUniqueId() {
        Node node1 = new Node(getNextPort());
        Node node2 = new Node(getNextPort());
        
        assertNotNull(node1.getNodeId(), "Node ID should not be null");
        assertNotNull(node2.getNodeId(), "Node ID should not be null");
        assertNotEquals(node1.getNodeId(), node2.getNodeId(), 
                "Different nodes should have different IDs");
    }

    @Test
    @DisplayName("Node ID should have correct format")
    void nodeId_shouldHaveCorrectFormat() {
        node = new Node(getNextPort());
        String nodeId = node.getNodeId();
        
        assertTrue(nodeId.startsWith(NetworkConfig.NODE_ID_PREFIX), 
                "Node ID should start with prefix");
        assertEquals(21, nodeId.length(), 
                "Node ID should be prefix (5) + 16 hex chars = 21");
    }

    @Test
    @DisplayName("Node should use specified port")
    void node_shouldUseSpecifiedPort() {
        int port = getNextPort();
        node = new Node(port);
        
        assertEquals(port, node.getPort(), "Node should use specified port");
    }

    @Test
    @DisplayName("Node should use default port when not specified")
    void node_shouldUseDefaultPort() {
        node = new Node();
        
        assertEquals(NetworkConfig.DEFAULT_PORT, node.getPort(), 
                "Node should use default port");
    }

    // ===== START/STOP TESTS =====

    @Test
    @DisplayName("Node should start and listen on port")
    void node_start_shouldListenOnPort() throws IOException {
        int port = getNextPort();
        node = new Node(port);
        
        assertFalse(node.isRunning(), "Node should not be running before start");
        
        node.start();
        
        assertTrue(node.isRunning(), "Node should be running after start");
    }

    @Test
    @DisplayName("Node should stop gracefully")
    void node_stop_shouldStopGracefully() throws IOException {
        int port = getNextPort();
        node = new Node(port);
        node.start();
        
        assertTrue(node.isRunning(), "Node should be running");
        
        node.stop();
        
        assertFalse(node.isRunning(), "Node should not be running after stop");
    }

    @Test
    @DisplayName("Starting already running node should throw exception")
    void node_startTwice_shouldThrowException() throws IOException {
        int port = getNextPort();
        node = new Node(port);
        node.start();
        
        assertThrows(IllegalStateException.class, () -> node.start(),
                "Starting twice should throw IllegalStateException");
    }

    // ===== CONNECTION TESTS =====

    @Test
    @DisplayName("Node should accept incoming connection")
    void node_shouldAcceptConnection() throws IOException, InterruptedException {
        int port = getNextPort();
        node = new Node(port);
        node.start();
        
        // Give server time to start listening
        Thread.sleep(100);
        
        // Try to connect as a client
        try (Socket clientSocket = new Socket("localhost", port)) {
            assertTrue(clientSocket.isConnected(), 
                    "Client should connect to node");
        }
    }
}