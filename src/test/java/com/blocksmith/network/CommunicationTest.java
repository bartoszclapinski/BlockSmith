package com.blocksmith.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.network.messages.PingMessage;
import com.blocksmith.network.messages.PongMessage;

/**
 * Integration tests for bidirectional message exchange.
 * Tests the full communication stack: Node + Peer + MessageParser + Handlers.
 */
@DisplayName("Communication Tests (Milestone 8d)")
class CommunicationTest {

    private Node node;
    private Peer peer;

    // Use different ports for each test to avoid conflicts
    private static final int TEST_PORT_BASE = 19200;
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

    // ===== PING/PONG TESTS =====

    @Test
    @DisplayName("Node should respond with PONG when Peer sends PING")
    void node_receivesPing_respondsPong() throws IOException, InterruptedException {
        int port = getNextPort();

        // Start node
        node = new Node(port);
        node.start();
        Thread.sleep(100);

        // Connect peer and handshake
        peer = new Peer("localhost", port);
        peer.connect();
        peer.performHandshake("test-peer", 9999, 0);

        // Set up listener to capture messages
        CountDownLatch latch = new CountDownLatch(1);
        List<Message> received = new ArrayList<>();

        peer.startListening(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                received.add(message);
                latch.countDown();
            }

            @Override
            public void onDisconnect() {}
        });

        // Send PING
        peer.sendMessage(new PingMessage("test-peer"));

        // Wait for PONG response
        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Should receive PONG within 5 seconds");
        assertEquals(1, received.size(), "Should receive exactly one message");
        assertEquals(MessageType.PONG, received.get(0).getType(),
                "Response should be PONG");
    }

    @Test
    @DisplayName("Node should respond to multiple PINGs with multiple PONGs")
    void node_multiplePings_multiplesPongs() throws IOException, InterruptedException {
        int port = getNextPort();

        node = new Node(port);
        node.start();
        Thread.sleep(100);

        peer = new Peer("localhost", port);
        peer.connect();
        peer.performHandshake("test-peer", 9999, 0);

        int pingCount = 3;
        CountDownLatch latch = new CountDownLatch(pingCount);
        List<Message> received = new ArrayList<>();

        peer.startListening(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                received.add(message);
                latch.countDown();
            }

            @Override
            public void onDisconnect() {}
        });

        // Send multiple PINGs
        for (int i = 0; i < pingCount; i++) {
            peer.sendMessage(new PingMessage("test-peer"));
            Thread.sleep(50); // Small delay between messages
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Should receive all PONGs within 5 seconds");
        assertEquals(pingCount, received.size(),
                "Should receive one PONG for each PING");

        for (Message msg : received) {
            assertEquals(MessageType.PONG, msg.getType(),
                    "All responses should be PONG");
        }
    }

    // ===== CUSTOM HANDLER TESTS =====

    @Test
    @DisplayName("Custom handler should be called for registered message type")
    void node_customHandler_getsCalled() throws IOException, InterruptedException {
        int port = getNextPort();

        node = new Node(port);

        // Register custom handler for PONG (just for testing)
        CountDownLatch handlerLatch = new CountDownLatch(1);
        List<Message> handlerReceived = new ArrayList<>();

        node.registerHandler(MessageType.PONG, (message, context) -> {
            handlerReceived.add(message);
            handlerLatch.countDown();
        });

        node.start();
        Thread.sleep(100);

        peer = new Peer("localhost", port);
        peer.connect();
        peer.performHandshake("test-peer", 9999, 0);

        // Send PONG to node (unusual, but tests custom handler)
        peer.sendMessage(new PongMessage("test-peer"));

        assertTrue(handlerLatch.await(5, TimeUnit.SECONDS),
                "Custom handler should be called within 5 seconds");
        assertEquals(1, handlerReceived.size(),
                "Handler should receive exactly one message");
        assertEquals(MessageType.PONG, handlerReceived.get(0).getType(),
                "Handler should receive PONG message");
    }

    // ===== UNREGISTERED MESSAGE TESTS =====

    @Test
    @DisplayName("Node should not crash on unregistered message type")
    void node_unregisteredType_noCrash() throws IOException, InterruptedException {
        int port = getNextPort();

        node = new Node(port);
        node.start();
        Thread.sleep(100);

        peer = new Peer("localhost", port);
        peer.connect();
        peer.performHandshake("test-peer", 9999, 0);

        CountDownLatch pongLatch = new CountDownLatch(1);

        peer.startListening(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                if (message.getType() == MessageType.PONG) {
                    pongLatch.countDown();
                }
            }

            @Override
            public void onDisconnect() {}
        });

        // Send PONG (no handler registered by default) - should not crash
        peer.sendMessage(new PongMessage("test-peer"));
        Thread.sleep(200);

        // Now send PING - node should still be working
        peer.sendMessage(new PingMessage("test-peer"));

        assertTrue(pongLatch.await(5, TimeUnit.SECONDS),
                "Node should still respond to PING after unhandled message");
    }

    // ===== DISCONNECT TESTS =====

    @Test
    @DisplayName("Listener should detect when peer disconnects")
    void listener_peerDisconnects_onDisconnectCalled() throws IOException, InterruptedException {
        int port = getNextPort();

        node = new Node(port);
        node.start();
        Thread.sleep(100);

        peer = new Peer("localhost", port);
        peer.connect();
        peer.performHandshake("test-peer", 9999, 0);

        CountDownLatch disconnectLatch = new CountDownLatch(1);

        peer.startListening(new MessageListener() {
            @Override
            public void onMessage(Message message) {}

            @Override
            public void onDisconnect() {
                disconnectLatch.countDown();
            }
        });

        // Disconnect peer
        peer.disconnect();

        assertTrue(disconnectLatch.await(5, TimeUnit.SECONDS),
                "onDisconnect should be called when peer disconnects");
    }

    // ===== FULL CYCLE TESTS =====

    @Test
    @DisplayName("Full cycle: handshake, PING/PONG, disconnect")
    void fullCycle_handshakePingPongDisconnect() throws IOException, InterruptedException {
        int port = getNextPort();

        // Start node
        node = new Node(port);
        node.start();
        Thread.sleep(100);

        // Connect and handshake
        peer = new Peer("localhost", port);
        peer.connect();
        assertTrue(peer.isConnected(), "Peer should be connected");

        peer.performHandshake("test-peer", 9999, 0);
        assertNotNull(peer.getRemoteNodeId(), "Should have remote node ID after handshake");

        // Set up listener
        CountDownLatch pongLatch = new CountDownLatch(1);
        CountDownLatch disconnectLatch = new CountDownLatch(1);
        List<Message> received = new ArrayList<>();

        peer.startListening(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                received.add(message);
                pongLatch.countDown();
            }

            @Override
            public void onDisconnect() {
                disconnectLatch.countDown();
            }
        });

        // Send PING and wait for PONG
        peer.sendMessage(new PingMessage("test-peer"));
        assertTrue(pongLatch.await(5, TimeUnit.SECONDS), "Should receive PONG");
        assertEquals(MessageType.PONG, received.get(0).getType(), "Should be PONG");

        // Disconnect
        peer.disconnect();
        assertTrue(disconnectLatch.await(5, TimeUnit.SECONDS),
                "onDisconnect should fire");
        assertFalse(peer.isConnected(), "Peer should be disconnected");
    }
}