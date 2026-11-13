package com.blocksmith.core;

import com.blocksmith.util.HashUtil;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;

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
    
    // Not final: setting a contract script changes the content, so the id is
    // recomputed (#264). Still never changes once the transaction is shared.
    private String transactionId;
    private final String sender;
    private final String recipient;
    private final double amount;
    private final long timestamp;
    private byte[] signature;
    private PublicKey senderPublicKey;

    /**
     * Contract scripts (Sprint 14). A DEPLOY transaction (recipient is a
     * CONTRACT: address) carries the locking script that guards the funds;
     * a CLAIM transaction (sender is a CONTRACT: address) carries the
     * unlocking data that satisfies it. Both travel inside blocks so every
     * node can re-verify the claim and converge on the same contract state.
     * Null for ordinary transfers.
     */
    private String lockingScript;
    private String unlockingScript;

    /**
     * What the sender pays the miner on top of {@code amount} (Sprint 18c).
     * 0 for transactions created before fees existed (and for the coinbase).
     */
    private double fee;

    /**
     * Creates a new transaction.
     * 
     * @param sender The address sending funds
     * @param recipient The address receiving funds
     * @param amount The amount to transfer (must be positive)     * 
     */
    public Transaction(String sender, String recipient, double amount) {
        this(sender, recipient, amount, 0);
    }

    /**
     * Creates a transaction that pays a fee to whoever mines it (Sprint 18c).
     * The sender is charged {@code amount + fee}.
     */
    public Transaction(String sender, String recipient, double amount, double fee) {
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
        this.fee = fee;
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
     * CONTRACT SCRIPTS (#264): a deploy's locking script decides who can
     * spend the locked funds, and a claim's unlocking script is the proof.
     * Both are part of what the transaction says, so both are part of its id
     * (and through the id, of the Merkle root and the block hash). Otherwise
     * a script could be swapped inside a block without changing its hash.
     *
     * Scripts are appended only when present, each with a label and its
     * length ("|lock:11:SHA256 PUSH"), so two different scripts can never
     * produce the same text, however their contents are split.
     * An ordinary transfer therefore keeps exactly the id it always had.
     *
     * @return SHA-256 hash of transaction data
     */
    private String calculateHash() {
        StringBuilder data = new StringBuilder()
                .append(sender).append(recipient).append(amount).append(timestamp);
        // The fee joins the id only when there is one, so every transaction
        // created before Sprint 18c keeps exactly the id it always had.
        if (fee != 0) data.append("|fee:").append(fee);
        if (lockingScript != null) {
            data.append("|lock:").append(lockingScript.length()).append(':').append(lockingScript);
        }
        if (unlockingScript != null) {
            data.append("|unlock:").append(unlockingScript.length()).append(':').append(unlockingScript);
        }
        return HashUtil.applySha256(data.toString());
    }

    /**
     * THEORY: The id is a claim, so re-check it
     *
     * The id is computed once, in the constructor, and then travels with the
     * transaction (over the network, into the database). A received copy
     * could carry an old id next to edited fields, for example a bigger
     * amount. Recomputing the id from the content exposes that edit,
     * including an edited contract script (#264).
     *
     * @return true if the stored id matches the transaction's content
     */
    public boolean hasValidId() {
        return transactionId != null && transactionId.equals(calculateHash());
    }

    /**
     * THEORY: Verifies the digital signature of this transaction.
     * 
     * VERIFICATION PROCESS:
     * 1. Reconstruct the data that was signed
     * 2. Use sender's public key to verify the signature
     * 3. If signature matches, the transaction is authentic
     * 
     * WHAT THIS PROVES: 
     * - The transaction was signed by the owner of the sender's private key
     * - The transaction data hasn't been modified since signing
     * 
     * COINBASE EXCEPTION:
     * Mining reward transactions (from "COINBASE") don't need signatures
     * because they're created by the system, not by a user.
     * 
     * @return true if signature is valid, false otherwise
     */
    public boolean verifySignature() {
        // COINBASE transactions don't need signatures
        if (sender.equals("COINBASE")) return true;

        // Must have signature and public key
        if (signature == null || senderPublicKey == null) return false;

        try {
            Signature ecdsaVerify = Signature.getInstance("SHA256withECDSA");
            ecdsaVerify.initVerify(senderPublicKey);

            ecdsaVerify.update(getSignatureData().getBytes(StandardCharsets.UTF_8));

            return ecdsaVerify.verify(signature);
        } catch (Exception e) {
            return false;
        }
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

        // Fee must be a real, non-negative number (a negative fee would let a
        // sender pay less than the amount and inflate the coinbase allowance)
        if (Double.isNaN(fee) || Double.isInfinite(fee) || fee < 0) return false;

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

    /** The fee paid to the miner (Sprint 18c). */
    public double getFee() {
        return fee;
    }

    /** Size of this transaction in bytes: its JSON, as it travels inside a block. */
    public int sizeBytes() {
        return com.blocksmith.util.JsonUtil.GSON.toJson(this)
                .getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Fee per byte (Sprint 18c): what a miner earns for the block space this
     * transaction takes. Block space is the scarce resource, so a 700-byte
     * signed transaction must pay three times the fee of a 235-byte one to
     * look as attractive. Bitcoin quotes the same idea in sat/vB.
     */
    public double feeRate() {
        return fee / sizeBytes();
    }

    /** What the sender is charged: amount plus fee. */
    public double getTotalCost() {
        return amount + fee;
    }

    /**
     * The exact text a wallet signs and a node verifies.
     *
     * THEORY: Sign everything that moves money
     *
     * A signature only protects what it covers. If the fee were not signed,
     * anyone relaying the transaction could raise it and drain the sender.
     * Like the id, the fee is appended only when present, so signatures made
     * before fees existed still verify.
     */
    public String getSignatureData() {
        String data = sender + recipient + amount + timestamp;
        return fee != 0 ? data + "|fee:" + fee : data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the digital signature of this transaction.
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Returns the sender's public key for signature verification.
     */
    public PublicKey getSenderPublicKey() {
        return senderPublicKey;
    }

    // ===== SETTERS =====

    /**
     * Sets the digital signature.
     */
    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    /**
     * Sets the sender's public key.
     */
    public void setSenderPublicKey(PublicKey senderPublicKey) {
        this.senderPublicKey = senderPublicKey;
    }

    /**
     * Returns the locking script (contract deploy transactions only).
     */
    public String getLockingScript() {
        return lockingScript;
    }

    /**
     * Sets the locking script for a contract deploy transaction and
     * recomputes the id (the script is part of it, #264). Call it before the
     * transaction is added to the mempool or sent anywhere.
     */
    public void setLockingScript(String lockingScript) {
        this.lockingScript = lockingScript;
        this.transactionId = calculateHash();
    }

    /**
     * Returns the unlocking script (contract claim transactions only).
     */
    public String getUnlockingScript() {
        return unlockingScript;
    }

    /**
     * Sets the unlocking script for a contract claim transaction and
     * recomputes the id (the script is part of it, #264). Call it before the
     * transaction is added to the mempool or sent anywhere.
     */
    public void setUnlockingScript(String unlockingScript) {
        this.unlockingScript = unlockingScript;
        this.transactionId = calculateHash();
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