package com.blocksmith.network;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.network.messages.GetMempoolMessage;
import com.blocksmith.network.messages.MempoolMessage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for mempool sync - GET_MEMPOOL/MEMPOOL serialization and the handlers
 * in Node that serve the pending pool and apply a received pool.
 *
 * The node is never started: the handler is pulled from the registry by
 * reflection and driven with a capturing MessageContext.
 */
@DisplayName("Mempool Sync Tests")
class MempoolSyncTest {

    private Node node;

    private static final int TEST_PORT_BASE = 20100;
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

    /** Funds an address so it can send spendable transactions. */
    private void fund(Blockchain chain, String address) {
        chain.minePendingTransactions(address);
    }

    @Test
    @DisplayName("GetMempoolMessage round-trips through MessageParser")
    void getMempoolMessage_roundTrips() {
        String json = new GetMempoolMessage("node-1").toJson();

        Message parsed = MessageParser.parse(json);

        assertInstanceOf(GetMempoolMessage.class, parsed, "Should parse as GetMempoolMessage");
        assertEquals("node-1", parsed.getNodeId(), "nodeId should survive the round trip");
    }

    @Test
    @DisplayName("MempoolMessage round-trips through MessageParser")
    void mempoolMessage_roundTrips() {
        Transaction tx = new Transaction("Alice", "Bob", 10.0);
        String json = new MempoolMessage("node-1", List.of(tx)).toJson();

        Message parsed = MessageParser.parse(json);

        assertInstanceOf(MempoolMessage.class, parsed, "Should parse as MempoolMessage");
        List<Transaction> received = ((MempoolMessage) parsed).getTransactions();
        assertEquals(1, received.size(), "The transaction should survive the round trip");
        assertEquals(tx.getTransactionId(), received.get(0).getTransactionId(),
                "Transaction id should match");
    }

    @Test
    @DisplayName("GET_MEMPOOL handler serves the pending pool")
    void getMempoolHandler_servesPendingPool() throws Exception {
        node = new Node(getNextPort());
        Blockchain chain = node.getBlockchain();
        fund(chain, "Miner1");
        Transaction tx = new Transaction("Miner1", "Alice", 30.0);
        assertTrue(chain.addTransaction(tx), "Setup tx should enter the pool");

        CapturingContext context = new CapturingContext("requester");
        getHandler(node, MessageType.GET_MEMPOOL)
                .handle(new GetMempoolMessage("requester"), context);

        Message reply = context.sent.get();
        assertInstanceOf(MempoolMessage.class, reply, "Reply should be a MempoolMessage");
        List<Transaction> served = ((MempoolMessage) reply).getTransactions();
        assertEquals(1, served.size(), "Should serve the one pending transaction");
        assertEquals(tx.getTransactionId(), served.get(0).getTransactionId(),
                "Served tx should be the pending one");
    }

    @Test
    @DisplayName("MEMPOOL handler applies received transactions")
    void mempoolHandler_appliesReceivedTransactions() throws Exception {
        node = new Node(getNextPort());
        Blockchain chain = node.getBlockchain();
        fund(chain, "Miner1"); // fund so the received tx is valid on our chain
        assertEquals(0, chain.getPendingTransactions().size(), "Pool starts empty");

        Transaction tx = new Transaction("Miner1", "Alice", 30.0);
        getHandler(node, MessageType.MEMPOOL)
                .handle(new MempoolMessage("sender", List.of(tx)), new CapturingContext("sender"));

        assertEquals(1, chain.getPendingTransactions().size(),
                "Received transaction should be added to the pool");
        assertEquals(tx.getTransactionId(),
                chain.getPendingTransactions().get(0).getTransactionId(),
                "The added tx should be the received one");
    }
}
