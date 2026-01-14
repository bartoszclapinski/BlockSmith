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
        this.hash = calculateHash();
    }

    /**
     * Calculates the hash of this block basedon its contents.
     * 
     * THEORY: The hash is a unique identifier for the block.
     * It's calculated from all the block's data concatenated in a specific order.
     * Any change to the block's data will produce a completely different hash (avalanche effect).
     * 
     * @return 64-character hexadecimal hash
     */
    public String calculateHash() {
        String dataToHash = index + timestamp + data + previousHash;
        return HashUtil.applySha256(dataToHash);
    }

    /**
     * Factory method to create the Genesis block (first block in chain).
     * 
     * THEORY: Genesis block is special:
     * - Always has index 0
     * - previousHash is always "0" (no previous block exists yet)
     * - Contains special message or timestamp to identify it as genesis
     * 
     * @return The Genesis block
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

    @Override
    public String toString() {
        return "Block{" +
            "index=" + index +
            ", timestamp=" + timestamp +
            ", data='" + data + '\'' +
            ", previousHash='" + previousHash + '\'' +
            ", hash='" + hash + '\'' +
            '}';
    }    
}