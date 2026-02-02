package com.blocksmith.network.messages;

import com.blocksmith.core.Transaction;
import com.blocksmith.network.Message;
import com.blocksmith.network.MessageType;

/**
 * Broadcast a new transaction to peers.
 */
public class NewTransactionMessage extends Message {
    
    private Transaction transaction;
    
    public NewTransactionMessage(String nodeId, Transaction transaction) {
        super(MessageType.NEW_TRANSACTION, nodeId);
        this.transaction = transaction;
    }
    
    public NewTransactionMessage() {}
    
    public Transaction getTransaction() { return transaction; }
}