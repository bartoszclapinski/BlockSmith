package com.blocksmith.core;

import com.blocksmith.util.HashUtil;
import com.blocksmith.util.BlockchainConfig;

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
        this.index = index;
        this.timestamp = System.currentTimeMillis();
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
        this.index = index;
        this.timestamp = System.currentTimeMillis();
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
     * Calculates the hash of this block based on its contents.
     * 
     * THEORY: The hash is a unique identifier for the block.
     * It includes the Merkle root (which summarizes all transactions),
     * so any change to any transaction will change the block hash.
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
        // Create target string: "0000" for difficulty 4
        String target = "0".repeat(difficulty);

        long startTime = System.currentTimeMillis();

        // Keep incrementing nonce until we find a valid hash
        while (!hash.startsWith(target)) {
            nonce++;
            hash = calculateHash();
        }

        long endTime = System.currentTimeMillis();
        long miningTime = endTime - startTime;

        System.out.println("Block mined! Nonce: " + nonce + " | Time: " + miningTime + "ms");
        System.out.println("Hash: " + hash);

        return miningTime;
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
            BlockchainConfig.GENESIS_PREV_HASH
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