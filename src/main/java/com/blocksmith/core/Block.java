package com.blocksmith.core;

import com.blocksmith.util.HashUtil;

/**
 * Represents a single block in the blockchain.
 * 
 * THEORY: A block is the fundamental unit of a blockchain.
 * Each block contains:
 * - index: Position in the chain (0 for genesis)
 * - timestamp: When the block was created (Unix time in milliseconds)
 * - hash: Unique identifier calculated from block contents
 * - previousHash: Links this block to the previous one
 * - data: The actual content (later will be replaced with transactions)
 * - nonce: Number used in mining to find valid hash
 * 
 * IMMUTABILITY: If any data changes, the hash changes,
 * breaking the link to the next block and invalidating the chain.
 */
public class Block {
    
    private final int index;
    private final long timestamp;
    private final String data;
    private final String previousHash;
    private String hash;
    private int nonce;

    /**
     * Creates a new block.
     * 
     * @param index Block number in the chain
     * @param data Block content (temporary; will be replaced with transactions in Sprint 4)
     * @param previousHash Hash of the previous block
     */
    public Block(int index, String data, String previousHash) {
        this.index = index;
        this.timestamp = System.currentTimeMillis();
        this.data = data;
        this.previousHash = previousHash;
        this.nonce = 0;
        this.hash = calculateHash();
    }

    /**
     * Calculates the hash of this block based on its contents.
     * 
     * THEORY: The hash is a unique identifier for the block.
     * It's calculated from all the block's data concatenated in a specific order.
     * Any change to the block's data will produce a completely different hash (avalanche effect).
     * 
     * NOTE: The nonce is included in the hash calculation. This is what allows
     * miners to change the hash output by incrementing the nonce.
     * 
     * @return 64-character hexadecimal hash
     */
    public String calculateHash() {
        String dataToHash = index + timestamp + data + previousHash + nonce;
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
            com.blocksmith.util.BlockchainConfig.GENESIS_PREV_HASH
        );
    }

    // Getters

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

    @Override
    public String toString() {
        return "Block{" +
            "index=" + index +
            ", timestamp=" + timestamp +
            ", nonce=" + nonce +
            ", data='" + data + '\'' +
            ", previousHash='" + previousHash + '\'' +
            ", hash='" + hash + '\'' +
            '}';
    }    
}