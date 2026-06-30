package com.blocksmith.network;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.network.messages.BlocksMessage;
import com.blocksmith.network.messages.GetBlocksMessage;
import com.blocksmith.util.BlockchainConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for chain sync - GET_BLOCKS/BLOCKS serialization and the handlers in
 * Node that serve a range of blocks and apply a received range in order.
 *
 * Like the other Node handler tests, the node is never started: the handler is
 * pulled from the registry by reflection and driven directly with a capturing
 * MessageContext.
 */
@DisplayName("Chain Sync Tests")
class ChainSyncTest {

    private Node node;

    private static final int TEST_PORT_BASE = 19900;
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

    /** A MessageContext that captures the message the handler sends back. */
    private static class CapturingContext extends MessageContext {
        final AtomicReference<Message> sent = new AtomicReference<>();

        CapturingContext(String remoteNodeId) {
            super(null, remoteNodeId);
        }

        @Override
        public void sendMessage(Message message) {
            sent.set(message);
        }
    }

    /** Mines a valid block extending the given tip. */
    private Block mineOn(Block tip) {
        Block next = new Block(tip.getIndex() + 1, "sync", tip.getHash());
        next.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        return next;
    }

    /** Builds a list of {@code count} valid blocks extending {@code startTip}. */
    private List<Block> mineRange(Block startTip, int count) {
        List<Block> blocks = new ArrayList<>();
        Block tip = startTip;
        for (int i = 0; i < count; i++) {
            Block next = mineOn(tip);
            blocks.add(next);
            tip = next;
        }
        return blocks;
    }

    @Test
    @DisplayName("GetBlocksMessage round-trips through MessageParser")
    void getBlocksMessage_roundTrips() {
        String json = new GetBlocksMessage("node-1", 5).toJson();

        Message parsed = MessageParser.parse(json);

        assertInstanceOf(GetBlocksMessage.class, parsed, "Should parse as GetBlocksMessage");
        assertEquals(5, ((GetBlocksMessage) parsed).getFromIndex(),
                "fromIndex should survive the round trip");
    }

    @Test
    @DisplayName("BlocksMessage round-trips through MessageParser")
    void blocksMessage_roundTrips() {
        Blockchain chain = new Blockchain();
        List<Block> range = mineRange(chain.getLatestBlock(), 2);
        String json = new BlocksMessage("node-1", range).toJson();

        Message parsed = MessageParser.parse(json);

        assertInstanceOf(BlocksMessage.class, parsed, "Should parse as BlocksMessage");
        List<Block> received = ((BlocksMessage) parsed).getBlocks();
        assertEquals(2, received.size(), "Both blocks should survive the round trip");
        assertEquals(range.get(0).getHash(), received.get(0).getHash(),
                "First block hash should match");
    }

    @Test
    @DisplayName("GET_BLOCKS handler serves the range from the requested index")
    void getBlocksHandler_servesRangeFromIndex() throws Exception {
        node = new Node(getNextPort());
        Blockchain chain = node.getBlockchain();
        for (Block b : mineRange(chain.getLatestBlock(), 3)) {
            assertTrue(chain.addBlock(b), "Setup block should append");
        }
        // Chain: genesis(0), 1, 2, 3 -> size 4. Request from index 2 -> blocks 2,3.
        CapturingContext context = new CapturingContext("requester");

        getHandler(node, MessageType.GET_BLOCKS)
                .handle(new GetBlocksMessage("requester", 2), context);

        Message reply = context.sent.get();
        assertInstanceOf(BlocksMessage.class, reply, "Reply should be a BlocksMessage");
        List<Block> served = ((BlocksMessage) reply).getBlocks();
        assertEquals(2, served.size(), "Should serve blocks 2 and 3");
        assertEquals(2, served.get(0).getIndex(), "Range starts at the requested index");
        assertEquals(3, served.get(1).getIndex(), "Range ends at the tip");
    }

    @Test
    @DisplayName("BLOCKS handler appends the received range in order")
    void blocksHandler_appendsRangeInOrder() throws Exception {
        node = new Node(getNextPort()); // behind: only genesis
        assertEquals(1, node.getBlockchain().getChainSize(), "Behind node starts at genesis");

        // Range extends the behind node's own genesis (peers share a genesis).
        List<Block> range = mineRange(node.getBlockchain().getLatestBlock(), 3);

        getHandler(node, MessageType.BLOCKS)
                .handle(new BlocksMessage("sender", range), new CapturingContext("sender"));

        assertEquals(4, node.getBlockchain().getChainSize(),
                "Behind node should catch up to genesis + 3");
        assertEquals(range.get(2).getHash(), node.getBlockchain().getLatestBlock().getHash(),
                "Tip should be the last block in the range");
    }

    @Test
    @DisplayName("BLOCKS handler rejects an invalid block within the range")
    void sync_rejectsInvalidBlockInRange() throws Exception {
        node = new Node(getNextPort());

        List<Block> range = mineRange(node.getBlockchain().getLatestBlock(), 3);
        // Corrupt the middle block: wrong previousHash breaks the link at index 2.
        Block tampered = new Block(2, "evil", "not-the-right-parent");
        tampered.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        range.set(1, tampered);

        getHandler(node, MessageType.BLOCKS)
                .handle(new BlocksMessage("sender", range), new CapturingContext("sender"));

        // First block applies; the tampered second stops the chain; the third is
        // orphaned (parent never arrives), so the chain ends at genesis + 1.
        assertEquals(2, node.getBlockchain().getChainSize(),
                "Only the first valid block should be applied");
    }
}
