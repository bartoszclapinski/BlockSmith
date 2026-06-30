package com.blocksmith.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import com.blocksmith.network.messages.GetPeersMessage;
import com.blocksmith.network.messages.PeersMessage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for peer discovery - GetPeersMessage/PeersMessage serialization and
 * the GET_PEERS / PEERS handlers in Node.
 *
 * Handlers are registered in the Node constructor, so the node is never
 * started: tests pull the handler out of the registry by reflection and
 * drive it directly with an in-memory MessageContext.
 */
@DisplayName("Peer Discovery Tests")
class PeerDiscoveryTest {

    private Node node;

    private static final int TEST_PORT_BASE = 19600;
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

    // ===== REFLECTION HELPER =====

    @SuppressWarnings("unchecked")
    private MessageHandler getHandler(Node target, MessageType type) throws Exception {
        Field handlersField = Node.class.getDeclaredField("handlers");
        handlersField.setAccessible(true);
        Map<MessageType, MessageHandler> handlers =
                (Map<MessageType, MessageHandler>) handlersField.get(target);
        return handlers.get(type);
    }

    private PeerInfo connectedPeer(String host, int port, String nodeId) {
        PeerInfo peer = new PeerInfo(host, port);
        peer.markConnected(nodeId);
        return peer;
    }

    // ===== SERIALIZATION TESTS =====

    @Test
    @DisplayName("GetPeersMessage round-trips through MessageParser")
    void getPeersMessage_parsesToCorrectType() {
        String json = new GetPeersMessage("node-123").toJson();

        Message parsed = MessageParser.parse(json);

        assertInstanceOf(GetPeersMessage.class, parsed, "Should parse as GetPeersMessage");
        assertEquals(MessageType.GET_PEERS, parsed.getType());
        assertEquals("node-123", parsed.getNodeId());
    }

    @Test
    @DisplayName("PeersMessage round-trips its peer list through MessageParser")
    void peersMessage_preservesPeerList() {
        List<String> addresses = List.of("10.0.0.1:8335", "10.0.0.2:8335");
        String json = new PeersMessage("node-123", addresses).toJson();

        Message parsed = MessageParser.parse(json);

        assertInstanceOf(PeersMessage.class, parsed, "Should parse as PeersMessage");
        assertEquals(addresses, ((PeersMessage) parsed).getPeers(),
                "Peer list should survive the round trip");
    }

    // ===== GET_PEERS HANDLER =====

    @Test
    @DisplayName("GET_PEERS handler responds with known peer addresses")
    void getPeersHandler_respondsWithKnownPeers() throws Exception {
        node = new Node(getNextPort());
        node.getPeerManager().addPeer(connectedPeer("10.0.0.1", 8335, "node-a"));
        node.getPeerManager().addPeer(connectedPeer("10.0.0.2", 8335, "node-b"));

        StringWriter sink = new StringWriter();
        MessageContext ctx = new MessageContext(new PrintWriter(sink, true), "requester");
        getHandler(node, MessageType.GET_PEERS)
                .handle(new GetPeersMessage("requester"), ctx);

        Message response = MessageParser.parse(sink.toString().trim());
        assertInstanceOf(PeersMessage.class, response, "Reply should be a PeersMessage");
        List<String> peers = ((PeersMessage) response).getPeers();
        assertTrue(peers.contains("10.0.0.1:8335"), "Reply should include first peer");
        assertTrue(peers.contains("10.0.0.2:8335"), "Reply should include second peer");
    }

    // ===== PEERS HANDLER =====

    @Test
    @DisplayName("PEERS handler records received addresses as DISCOVERED")
    void peersHandler_addsReceivedPeers() throws Exception {
        node = new Node(getNextPort());
        PeersMessage incoming = new PeersMessage("sender",
                List.of("10.0.0.5:8335", "10.0.0.6:8335"));

        MessageContext ctx = new MessageContext(new PrintWriter(new StringWriter()), "sender");
        getHandler(node, MessageType.PEERS).handle(incoming, ctx);

        PeerManager pm = node.getPeerManager();
        assertTrue(pm.isKnown("10.0.0.5:8335"), "First received peer should be recorded");
        assertTrue(pm.isKnown("10.0.0.6:8335"), "Second received peer should be recorded");
        assertEquals(PeerState.DISCOVERED, pm.getPeer("10.0.0.5:8335").getState(),
                "Recorded peer should be DISCOVERED, not CONNECTED");
    }

    @Test
    @DisplayName("PEERS handler skips malformed addresses")
    void peersHandler_skipsMalformedAddresses() throws Exception {
        node = new Node(getNextPort());
        PeersMessage incoming = new PeersMessage("sender",
                List.of("garbage", "10.0.0.7:8335", "noport:"));

        MessageContext ctx = new MessageContext(new PrintWriter(new StringWriter()), "sender");
        getHandler(node, MessageType.PEERS).handle(incoming, ctx);

        PeerManager pm = node.getPeerManager();
        assertTrue(pm.isKnown("10.0.0.7:8335"), "Valid address should be recorded");
        assertFalse(pm.isKnown("garbage"), "Malformed address should be skipped");
        assertEquals(1, pm.getKnownPeers().size(), "Only the valid address should be recorded");
    }
}
