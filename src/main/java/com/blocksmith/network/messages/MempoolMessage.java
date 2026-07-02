package com.blocksmith.network.messages;

import java.util.List;

import com.blocksmith.core.Transaction;
import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Carries a peer's pending transactions in reply to a {@link GetMempoolMessage}.
 * The receiver validates and adds each one to its own mempool.
 */
public class MempoolMessage extends Message {

    private List<Transaction> transactions;

    public MempoolMessage(String nodeId, List<Transaction> transactions) {
        super(MessageType.MEMPOOL, nodeId);
        this.transactions = transactions;
    }

    /** Default constructor for Gson. */
    public MempoolMessage() {}

    public List<Transaction> getTransactions() { return transactions; }
}
