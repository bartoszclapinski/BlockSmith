package com.blocksmith.core;

import com.blocksmith.util.HashUtil;

/**
 * THEORY: A transaction represents a transfer of value between addresses.
 * 
 * COMPONENTS:
 * - sender: The address sending the funds
 * - recipient: The address receiving the funds
 * - amount: How much is being transferred
 * - transactionId: Unique hash identifying this transaction
 * 
 * VALIDATION (basic):
 * - amount must be positive
 * - sender and recipient must be non-empty
 * 
 * BITCOIN vs OUR MODEL:
 * Bitcoin uses UTXO (Unspent Transaction Outputs) model where each transaction
 * consumes previous outputs and creates new ones.
 * We use a simpler account-based model (like Ethereum) where each address
 * has a balance that gets debited/credited.
 * 
 * TRANSACTION ID:
 * The transactionId is a SHA-256 hash of all transaction data, making it:
 * - Unique: Different transactions have different IDs
 * - Deterministic: Same data always produces same ID
 * - Tamper-evident: Any change produces completely different ID
 */
public class Transaction {
    
    private final String transactionId;
    private final String sender;
    private final String recipient;
    private final double amount;
    private final long timestamp;

    /**
     * Creates a new transaction.
     * 
     * @param sender The address sending funds
     * @param recipient The address receiving funds
     * @param amount The amount to transfer (must be positive)     * 
     */
    public Transaction(String sender, String recipient, double amount) {
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
        this.transactionId = calculateHash();
    }

    /**
     * Calculates the unique hash (ID) for this transaction.
     * 
     * THEORY: The hash includes all transaction data, ensuring:
     * - Any change to any field produces a different hash
     * - Same transaction data always produces the same hash
     * 
     * @return SHA-256 hash of transaction data
     */
    private String calculateHash() {
        String data = sender + recipient + amount + timestamp;
        return HashUtil.applySha256(data);
    }    

    /**
     * Validates the transaction according to basic rules.
     * 
     * VALIDATION RULES:
     * 1. Amount must be positive (> 0)
     * 2. Sender address must not be null or empty
     * 3. Recipient address must not be null or empty
     * 
     * NOTE: This does NOT check:
     * - If sender has sufficient balance (done in Blockchain)
     * - Digital signatures (added in Sprint 5)
     * - Double-spending prevention (requires UTXO tracking)
     * 
     * @return true if transaction passes basic validation
     */
    public boolean isValid() {
        // Amount must be positive
        if (amount <= 0) return false;

        // Sender must be non-empty
        if (sender == null || sender.trim().isEmpty()) return false;

        // Recipient must be non-empty
        if (recipient == null || recipient.trim().isEmpty()) return false;

        return true;
    }

    // ===== GETTERS =====

    public String getTransactionId() {
        return transactionId;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public double getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US, "Transaction{id=%s..., %s -> %s: %.2f}",
            transactionId.substring(0, 8),
            sender,
            recipient,
            amount
        );
    }
}