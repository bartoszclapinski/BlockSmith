package com.blocksmith.network;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.network.messages.NewBlockMessage;
import com.blocksmith.util.BlockchainConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for block broadcasting - NewBlockMessage serialization and the
 * NEW_BLOCK handler in Node (validate, append, ignore duplicates).
 *
 * The handler is registered in the Node constructor, so the node is never
 * started: the test pulls the handler out of the registry by reflection and
 * drives it directly with an in-memory MessageContext.
 */
@DisplayName("Block Broadcast Tests")
class BlockBroadcastTest {

    private Node node;

    private static final int TEST_PORT_BASE = 19800;
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

    @SuppressWarnings("unchecked")
    private MessageHandler getHandler(Node target, MessageType type) throws Exception {
        Field handlersField = Node.class.getDeclaredField("handlers");
        handlersField.setAccessible(true);
        Map<MessageType, MessageHandler> handlers =
                (Map<MessageType, MessageHandler>) handlersField.get(target);
        return handlers.get(type);
    }

    private MessageContext sinkContext(String remoteNodeId) {
        return new MessageContext(new PrintWriter(new StringWriter()), remoteNodeId);
    }

    /** Builds a valid, mined block that extends the chain's current tip. */
    private Block mineNextBlock(Blockchain chain) {
        Block tip = chain.getLatestBlock();
        Block next = new Block(tip.getIndex() + 1, "external", tip.getHash());
        next.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        return next;
    }

    @Test
    @DisplayName("NewBlockMessage round-trips through MessageParser")
    void newBlockMessage_roundTripsThroughParser() {
        Block block = mineNextBlock(new Blockchain());
        String json = new NewBlockMessage("node-1", block).toJson();

        Message parsed = MessageParser.parse(json);

        assertInstanceOf(NewBlockMessage.class, parsed, "Should parse as NewBlockMessage");
        assertEquals(block.getHash(), ((NewBlockMessage) parsed).getBlock().getHash(),
                "Block hash should survive the round trip");
    }

    @Test
    @DisplayName("NEW_BLOCK handler appends a valid block")
    void newBlockHandler_appendsValidBlock() throws Exception {
        node = new Node(getNextPort());
        Block block = mineNextBlock(node.getBlockchain());

        getHandler(node, MessageType.NEW_BLOCK)
                .handle(new NewBlockMessage("sender", block), sinkContext("sender"));

        Blockchain chain = node.getBlockchain();
        assertEquals(2, chain.getChainSize(), "Chain should grow by one");
        assertEquals(block.getHash(), chain.getLatestBlock().getHash(),
                "Received block should be the new tip");
    }

    @Test
    @DisplayName("NEW_BLOCK handler ignores a duplicate block")
    void newBlockHandler_ignoresDuplicate() throws Exception {
        node = new Node(getNextPort());
        Block block = mineNextBlock(node.getBlockchain());
        MessageHandler handler = getHandler(node, MessageType.NEW_BLOCK);

        handler.handle(new NewBlockMessage("sender", block), sinkContext("sender")); // accepted
        handler.handle(new NewBlockMessage("sender", block), sinkContext("sender")); // duplicate

        assertEquals(2, node.getBlockchain().getChainSize(),
                "Duplicate block should not be appended a second time");
    }

    @Test
    @DisplayName("NEW_BLOCK handler rejects an invalid block")
    void newBlockHandler_rejectsInvalidBlock() throws Exception {
        node = new Node(getNextPort());
        Block tip = node.getBlockchain().getLatestBlock();
        // Wrong previousHash and unmined: fails validation in addBlock.
        Block bad = new Block(tip.getIndex() + 1, "external", "not-the-tip-hash");

        getHandler(node, MessageType.NEW_BLOCK)
                .handle(new NewBlockMessage("sender", bad), sinkContext("sender"));

        assertEquals(1, node.getBlockchain().getChainSize(),
                "Invalid block must not be appended");
    }
}
