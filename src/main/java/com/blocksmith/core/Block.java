package com.blocksmith.core;

import com.blocksmith.util.HashUtil;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.JsonUtil;
import com.blocksmith.util.Target;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single block in the blockchain.
 * 
 * THEORY: A block is the fundamental unit of a blockchain.
 * Each block contains:
 * - index: Position in the chain (0 for genesis)
 * - timestamp: When the block was created (Unix time in milliseconds)
 * - hash: Unique identifier calculated from block contents
 * - previousHash: Links this block to the previous one
 * - transactions: List of transactions included in this block
 * - merkleRoot: Hash summarizing all transactions (see calculateMerkleRoot)
 * - nonce: Number used in mining to find valid hash
 * 
 * IMMUTABILITY: If any data changes, the hash changes,
 * breaking the link to the next block and invalidating the chain.
 * 
 * MERKLE TREE: Instead of hashing all transactions directly, we build
 * a binary tree of hashes. This allows efficient verification of
 * individual transactions without downloading the entire block.
 */
public class Block {
    
    private final int index;
    private final long timestamp;
    private final String data;  // Legacy: used for Genesis block message
    private final String previousHash;
    private final List<Transaction> transactions;
    private final String merkleRoot;
    private String hash;
    private int nonce;

    /**
     * Creates a new block with transactions.
     * 
     * @param index Block number in the chain
     * @param transactions List of transactions to include
     * @param previousHash Hash of the previous block
     */
    public Block(int index, List<Transaction> transactions, String previousHash) {
        this(index, transactions, previousHash, System.currentTimeMillis());
    }

    /**
     * Creates a block with an explicit timestamp (Sprint 17b). Used to
     * simulate fast or slow networks in tests; real mining uses the clock.
     */
    public Block(int index, List<Transaction> transactions, String previousHash, long timestamp) {
        this.index = index;
        this.timestamp = timestamp;
        this.transactions = new ArrayList<>(transactions);  // Defensive copy
        this.data = "";  // Not used when we have transactions
        this.previousHash = previousHash;
        this.merkleRoot = calculateMerkleRoot();
        this.nonce = 0;
        this.hash = calculateHash();
    }

    /**
     * Creates a new block with simple data (legacy constructor).
     * Used for Genesis block and backward compatibility.
     * 
     * @param index Block number in the chain
     * @param data Block content (simple string)
     * @param previousHash Hash of the previous block
     */
    public Block(int index, String data, String previousHash) {
        this(index, data, previousHash, System.currentTimeMillis());
    }

    /**
     * Creates a data block with an explicit timestamp.
     * Used for the Genesis block, whose timestamp must be fixed so its hash is
     * identical on every node.
     *
     * @param index Block number in the chain
     * @param data Block content (simple string)
     * @param previousHash Hash of the previous block
     * @param timestamp Creation time (Unix millis)
     */
    public Block(int index, String data, String previousHash, long timestamp) {
        this.index = index;
        this.timestamp = timestamp;
        this.data = data;
        this.transactions = new ArrayList<>();  // Empty list
        this.previousHash = previousHash;
        this.merkleRoot = calculateMerkleRoot();
        this.nonce = 0;
        this.hash = calculateHash();
    }

    /**
     * Calculates the Merkle root of all transactions.
     * 
     * THEORY: MERKLE TREE (Binary Hash Tree)
     * 
     * A Merkle tree is a binary tree where:
     * - Leaf nodes contain hashes of individual transactions
     * - Parent nodes contain hash of concatenated children hashes
     * - Root node (Merkle root) summarizes ALL transactions
     * 
     * EXAMPLE with 4 transactions (A, B, C, D):
     * 
     *             MERKLE ROOT
     *            /           \
     *       Hash(AB)      Hash(CD)
     *       /     \       /     \
     *    Hash(A) Hash(B) Hash(C) Hash(D)
     *      |       |       |       |
     *     Tx A    Tx B    Tx C    Tx D
     * 
     * BENEFITS:
     * 1. Compact: Single 64-char hash represents ALL transactions
     * 2. Tamper-evident: Changing ANY transaction changes the root
     * 3. Efficient verification: Prove tx exists with O(log n) hashes
     * 
     * BITCOIN uses Merkle trees to enable SPV (Simple Payment Verification)
     * where light clients can verify transactions without full blockchain.
     * 
     * @return 64-character Merkle root hash
     */
    private String calculateMerkleRoot() {
        // If no transactions, hash the data field (for Genesis block)
        if (transactions.isEmpty()) {
            return HashUtil.applySha256(data);
        }
        
        // If only one transaction, return its hash
        if (transactions.size() == 1) {
            return transactions.get(0).getTransactionId();
        }
        
        // Build the Merkle tree from transaction IDs
        List<String> hashes = new ArrayList<>();
        for (Transaction tx : transactions) {
            hashes.add(tx.getTransactionId());
        }
        
        // Keep hashing pairs until we get one root
        while (hashes.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            
            for (int i = 0; i < hashes.size(); i += 2) {
                String left = hashes.get(i);
                // If odd number, duplicate the last hash
                String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
                
                // Combine and hash
                String combined = HashUtil.applySha256(left + right);
                nextLevel.add(combined);
            }
            
            hashes = nextLevel;
        }
        
        return hashes.get(0);
    }

    /**
     * THEORY: Is the Merkle root honest?
     *
     * The block hash covers the STORED merkleRoot, not the transactions
     * themselves. A received block could keep its original Merkle root and
     * hash while its transactions were edited, and calculateHash() would
     * still match. Rebuilding the root from the transactions (and each
     * transaction's id from its content) closes that gap:
     *
     *   txs --ids--> merkle tree --root--> must equal stored merkleRoot
     *                                       --> covered by the block hash
     *
     * @return true if every transaction's id matches its content and the
     *         Merkle root matches those ids
     */
    public boolean hasValidMerkleRoot() {
        for (Transaction tx : transactions) {
            if (!tx.hasValidId()) return false;
        }
        return merkleRoot != null && merkleRoot.equals(calculateMerkleRoot());
    }

    /**
     * Calculates the hash of this block based on its contents.
     *
     * THEORY: The hash is a unique identifier for the block.
     * It includes the Merkle root (which summarizes all transactions),
     * so a change to a transaction changes the block hash - provided the
     * Merkle root is recomputed from the transactions (see hasValidMerkleRoot).
     *
     * NOTE: The nonce is included in the hash calculation. This is what allows
     * miners to change the hash output by incrementing the nonce.
     * 
     * @return 64-character hexadecimal hash
     */
    public String calculateHash() {
        String dataToHash = index + timestamp + merkleRoot + previousHash + nonce;
        return HashUtil.applySha256(dataToHash);
    }

    /**
     * Mines this block by finding a nonce that produces a valid hash.
     * 
     * THEORY: Proof-of-Work (PoW) requires finding a nonce value that,
     * when combined with block data and hashed, produces a hash starting 
     * with a required number of zeros (determined by difficulty).
     * 
     * THE PUZZLE: 
     * - difficulty = 4 means hash must start with "0000"
     * - Only way to find valid nonce is brute-force (try many values)
     * - Higher difficulty = more zeros required = exponentially more attempts needed
     * 
     * SECURITY: To tamper with a block, attacker must re-mine it AND 
     * all subsequent blocks faster than the network adds new ones. 
     *
     * @param difficulty Number of leading zeros required in hash
     * @return Mining time in milliseconds
     */
    public long mineBlock(int difficulty) {
        // "N leading zeros" is exactly the target 16^(64-N) - 1 (see Target).
        return mineBlock(Target.fromLeadingZeros(difficulty));
    }

    /**
     * Mines this block until its hash, read as a number, is at most
     * {@code target} (Sprint 17a). A smaller target is harder to meet.
     *
     * @param target the largest acceptable hash value
     * @return Mining time in milliseconds
     */
    public long mineBlock(java.math.BigInteger target) {
        long startTime = System.currentTimeMillis();
        mineBlock(target, () -> false);
        return System.currentTimeMillis() - startTime;
    }

    /** How many nonces are tried between two checks of the stop condition (Sprint 20b). */
    public static final int STOP_CHECK_INTERVAL = 1_000;

    /**
     * Mines like {@link #mineBlock(java.math.BigInteger)}, but asks
     * {@code stop} before starting and every {@value #STOP_CHECK_INTERVAL}
     * nonces whether to give up (Sprint 20b), e.g. because a peer's block
     * just made this one pointless: it would no longer extend the tip.
     *
     * @return true if a valid nonce was found, false if stopped first
     */
    public boolean mineBlock(java.math.BigInteger target, java.util.function.BooleanSupplier stop) {
        // Compare as 64-digit hex text: same order as the numbers, but no
        // BigInteger has to be built for every attempt.
        String targetHex = Target.toHex(target);

        long startTime = System.currentTimeMillis();
        if (stop.getAsBoolean()) return false;

        // Keep incrementing nonce until we find a valid hash
        long tries = 0;
        while (!Target.meetsHex(hash, targetHex)) {
            if (++tries % STOP_CHECK_INTERVAL == 0 && stop.getAsBoolean()) {
                System.out.println("Mining stopped after " + tries + " tries");
                return false;
            }
            nonce++;
            hash = calculateHash();
        }

        long miningTime = System.currentTimeMillis() - startTime;
        System.out.println("Block mined! Nonce: " + nonce + " | Time: " + miningTime + "ms");
        System.out.println("Hash: " + hash);
        return true;
    }

    /**
     * Factory method to create the Genesis block (first block in chain).
     * 
     * THEORY: Genesis block is special:
     * - Always has index 0
     * - previousHash is always "0" (no previous block exists yet)
     * - Contains special message or timestamp to identify it as genesis
     * 
     * @return The Genesis block (not mined yet = call mineBlock() after creation)
     */
    public static Block createGenesisBlock() {
        return new Block(
            0,
            "Genesis Block - BlockSmith Blockchain initialized",
            BlockchainConfig.GENESIS_PREV_HASH,
            BlockchainConfig.GENESIS_TIMESTAMP
        );
    }

    // ===== GETTERS =====

    public int getIndex() {
        return index;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getData() {
        return data;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    /**
     * Size of this block in bytes: its JSON, exactly as peers exchange it
     * (Sprint 18b). The block-size limit is measured on this.
     */
    public int sizeBytes() {
        return JsonUtil.GSON.toJson(this).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    public String getHash() {
        return hash;
    }

    public int getNonce() {
        return nonce;
    }

    public List<Transaction> getTransactions() {
        return new ArrayList<>(transactions);  // Defensive copy
    }

    public String getMerkleRoot() {
        return merkleRoot;
    }

    public int getTransactionCount() {
        return transactions.size();
    }

    @Override
    public String toString() {
        return "Block{" +
            "index=" + index +
            ", timestamp=" + timestamp +
            ", nonce=" + nonce +
            ", transactions=" + transactions.size() +
            ", merkleRoot='" + merkleRoot.substring(0, 8) + "...'" +
            ", previousHash='" + previousHash.substring(0, Math.min(8, previousHash.length())) + "...'" +
            ", hash='" + hash.substring(0, 8) + "...'" +
            '}';
    }    
}