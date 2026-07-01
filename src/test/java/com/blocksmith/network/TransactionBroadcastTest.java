package com.blocksmith.network;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.network.messages.NewTransactionMessage;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for transaction broadcasting - NewTransactionMessage serialization and
 * the NEW_TRANSACTION handler in Node (validate, add to mempool, ignore
 * duplicates, reject invalid).
 *
 * The handler is registered in the Node constructor, so the node is never
 * started: the test pulls the handler out of the registry by reflection and
 * drives it directly with an in-memory MessageContext.
 */
@DisplayName("Transaction Broadcast Tests")
class TransactionBroadcastTest {

    private Node node;

    private static final int TEST_PORT_BASE = 20000;
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

    /** Funds an address by mining a block so its coinbase reward is spendable. */
    private void fund(Blockchain chain, String address) {
        chain.minePendingTransactions(address);
    }

    @Test
    @DisplayName("NewTransactionMessage round-trips through MessageParser")
    void newTransactionMessage_roundTripsThroughParser() {
        Transaction tx = new Transaction("Alice", "Bob", 10.0);
        String json = new NewTransactionMessage("node-1", tx).toJson();

        Message parsed = MessageParser.parse(json);

        assertInstanceOf(NewTransactionMessage.class, parsed,
                "Should parse as NewTransactionMessage");
        assertEquals(tx.getTransactionId(),
                ((NewTransactionMessage) parsed).getTransaction().getTransactionId(),
                "Transaction id should survive the round trip");
    }

    @Test
    @DisplayName("NEW_TRANSACTION handler adds a valid transaction")
    void newTransactionHandler_addsValidTransaction() throws Exception {
        node = new Node(getNextPort());
        Blockchain chain = node.getBlockchain();
        fund(chain, "Miner1");
        Transaction tx = new Transaction("Miner1", "Alice", 30.0);

        getHandler(node, MessageType.NEW_TRANSACTION)
                .handle(new NewTransactionMessage("sender", tx), sinkContext("sender"));

        assertEquals(1, chain.getPendingTransactions().size(),
                "Valid transaction should be added to the mempool");
    }

    @Test
    @DisplayName("NEW_TRANSACTION handler ignores a duplicate transaction")
    void newTransactionHandler_ignoresDuplicate() throws Exception {
        node = new Node(getNextPort());
        Blockchain chain = node.getBlockchain();
        fund(chain, "Miner1");
        Transaction tx = new Transaction("Miner1", "Alice", 30.0);
        MessageHandler handler = getHandler(node, MessageType.NEW_TRANSACTION);

        handler.handle(new NewTransactionMessage("sender", tx), sinkContext("sender")); // accepted
        handler.handle(new NewTransactionMessage("sender", tx), sinkContext("sender")); // duplicate

        assertEquals(1, chain.getPendingTransactions().size(),
                "Duplicate transaction should not be added a second time");
    }

    @Test
    @DisplayName("NEW_TRANSACTION handler rejects an invalid transaction")
    void newTransactionHandler_rejectsInvalidTransaction() throws Exception {
        node = new Node(getNextPort());
        Blockchain chain = node.getBlockchain();
        // "Nobody" has no balance, so the transfer has insufficient funds.
        Transaction tx = new Transaction("Nobody", "Alice", 100.0);

        getHandler(node, MessageType.NEW_TRANSACTION)
                .handle(new NewTransactionMessage("sender", tx), sinkContext("sender"));

        assertEquals(0, chain.getPendingTransactions().size(),
                "Invalid transaction must not be added");
    }
}
