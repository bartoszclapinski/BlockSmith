package com.blocksmith.network;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.core.Wallet;
import com.blocksmith.network.messages.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for network message serialization and deserialization.
 */
@DisplayName("Message Serialization Tests")
class MessageTest {

    // ===== HelloMessage Tests =====

    @Test
    @DisplayName("HelloMessage should serialize to JSON")
    void helloMessage_toJson_containsAllFields() {
        HelloMessage msg = new HelloMessage("node-123", "1.0.0", 8080, 10);
        String json = msg.toJson();
        
        // Note: Compact JSON format (no spaces after colons)
        assertTrue(json.contains("\"type\":\"HELLO\""), "Should contain type");
        assertTrue(json.contains("\"nodeId\":\"node-123\""), "Should contain nodeId");
        assertTrue(json.contains("\"version\":\"1.0.0\""), "Should contain version");
        assertTrue(json.contains("\"port\":8080"), "Should contain port");
        assertTrue(json.contains("\"chainLength\":10"), "Should contain chainLength");
    }

    @Test
    @DisplayName("HelloMessage should deserialize from JSON")
    void helloMessage_fromJson_restoresAllFields() {
        HelloMessage original = new HelloMessage("node-123", "1.0.0", 8080, 10);
        String json = original.toJson();
        
        HelloMessage restored = Message.fromJson(json, HelloMessage.class);
        
        assertEquals(MessageType.HELLO, restored.getType());
        assertEquals("node-123", restored.getNodeId());
        assertEquals("1.0.0", restored.getVersion());
        assertEquals(8080, restored.getPort());
        assertEquals(10, restored.getChainLength());
    }

    // ===== PingMessage Tests =====

    @Test
    @DisplayName("PingMessage should serialize and deserialize")
    void pingMessage_roundTrip_preservesData() {
        PingMessage original = new PingMessage("node-456");
        String json = original.toJson();
        
        PingMessage restored = Message.fromJson(json, PingMessage.class);
        
        assertEquals(MessageType.PING, restored.getType());
        assertEquals("node-456", restored.getNodeId());
    }

    // ===== PongMessage Tests =====

    @Test
    @DisplayName("PongMessage should serialize and deserialize")
    void pongMessage_roundTrip_preservesData() {
        PongMessage original = new PongMessage("node-789");
        String json = original.toJson();
        
        PongMessage restored = Message.fromJson(json, PongMessage.class);
        
        assertEquals(MessageType.PONG, restored.getType());
        assertEquals("node-789", restored.getNodeId());
    }

    // ===== Timestamp Tests =====

    @Test
    @DisplayName("Message timestamp should be set on creation")
    void message_creation_setsTimestamp() {
        long before = System.currentTimeMillis();
        PingMessage msg = new PingMessage("node-test");
        long after = System.currentTimeMillis();
        
        assertTrue(msg.getTimestamp() >= before, "Timestamp should be >= creation start");
        assertTrue(msg.getTimestamp() <= after, "Timestamp should be <= creation end");
    }

    // ===== Type Tests =====

    @Test
    @DisplayName("Each message type should have correct MessageType")
    void messages_shouldHaveCorrectTypes() {
        assertEquals(MessageType.HELLO, new HelloMessage("n", "1.0", 8080, 0).getType());
        assertEquals(MessageType.PING, new PingMessage("n").getType());
        assertEquals(MessageType.PONG, new PongMessage("n").getType());
    }

    // ===== Signed transactions over the wire (#259) =====

    @Test
    @DisplayName("NEW_TRANSACTION with a signed transaction round-trips and still verifies")
    void newTransactionMessage_signedTransaction_roundTrips() {
        Wallet wallet = new Wallet();
        Transaction tx = new Transaction(wallet.getAddress(), "bob", 5);
        wallet.signTransaction(tx);

        String json = new NewTransactionMessage("node-1", tx).toJson();
        NewTransactionMessage restored = Message.fromJson(json, NewTransactionMessage.class);

        assertEquals(tx.getTransactionId(), restored.getTransaction().getTransactionId());
        assertTrue(restored.getTransaction().verifySignature(),
                "Public key and signature must survive serialization");
    }

    @Test
    @DisplayName("BLOCKS with a signed transaction round-trips")
    void blocksMessage_signedTransaction_roundTrips() {
        Wallet wallet = new Wallet();
        Blockchain chain = new Blockchain();
        chain.minePendingTransactions(wallet.getAddress());
        Transaction tx = new Transaction(wallet.getAddress(), "bob", 5);
        wallet.signTransaction(tx);
        assertTrue(chain.addTransaction(tx));
        Block block = chain.minePendingTransactions("miner");

        String json = new BlocksMessage("node-1", java.util.List.of(block)).toJson();
        Block restored = Message.fromJson(json, BlocksMessage.class).getBlocks().get(0);

        assertEquals(block.getHash(), restored.getHash());
        assertTrue(restored.hasValidMerkleRoot());
        assertTrue(restored.getTransactions().stream()
                .filter(t -> t.getTransactionId().equals(tx.getTransactionId()))
                .findFirst().orElseThrow().verifySignature());
    }
}