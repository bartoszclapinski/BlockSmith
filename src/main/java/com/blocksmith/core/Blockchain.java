package com.blocksmith.core;

import com.blocksmith.util.BlockchainConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages the blockchain - a linked list of blocks.
 * 
 * THEORY: A blockchain is a linked list of blocks where each block
 * contains the hash of the previous block, creating a chain.
 * 
 * STRUCTURE:
 * [Genesis] -> [Block 1] -> [Block 2] -> ... -> [Block N]
 *    ↑            ↑            ↑                    ↑
 * prevHash=0   prevHash=    prevHash=           prevHash=
 *              genesis.hash  block1.hash        blockN-1.hash
 * 
 * IMMUTABILITY: If any block is modified, its hash changes,
 * which breaks the link from the next block (prevHash mismatch).
 * This invalidates that block AND all subsequent blocks.
 * 
 * SECURITY: To tamper with block N, attacker must:
 * 1. Modify block N
 * 2. Re-mine block N (find new valid nonce)
 * 3. Re-mine ALL blocks after N
 * 4. Do this faster than honest network adds new blocks
 */
public class Blockchain {

    private final List<Block> chain;

    /**
     * Creates a new blockchain with the Genesis block.
     * 
     * THEORY: Genesis Block is the first block in any blockchain.
     * 
     * SPECIAL PROPERTIES:
     * - index = 0 (first block)
     * - previousHash = "0" (no previous block exists)
     * - Usually hardcoded or has special data
     * 
     * BITCOIN'S GENESIS: Contains the message:
     * "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
     * This proves the block couldn't have been created before that date.
     */
    public Blockchain() {
        this.chain = new ArrayList<>();

        // Create and mine the Genesis block
        Block genesis = Block.createGenesisBlock();
        genesis.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        chain.add(genesis);
    }
        
    /**
     * Returns the latest (most recent) block in the chain
     *   
     * @return The latest block added to the chain
     */
    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    /**
     * Adds a new block to the blockchain.
     * 
     * The block is automatically:
     * 1. Assigned the correct previousHash (from latest block)
     * 2. Mined with the configured difficulty
     * 
     * @param data The data to store in the new block
     * @return The newly created and mined block
     */ 
    public Block addBlock(String data) {
        Block latestBlock = getLatestBlock();
        int newIndex = latestBlock.getIndex() + 1;

        Block newBlock = new Block(newIndex, data, latestBlock.getHash());
        newBlock.mineBlock(BlockchainConfig.MINING_DIFFICULTY);

        chain.add(newBlock);
        
        return newBlock;
    }

    /**
     * Validates the entire blockchain integrity.
     * 
     * THEORY: Chain validation checks:
     * 1. Genesis block is valid (index=0, previousHash="0")
     * 2. Each block's hash matches its calculated hash
     * 3. Each block's previousHash matches previous block's hash
     * 4. Each block was properly mined (hash meets difficulty)
     * 
     * WHY THIS MATTERS: 
     * - Detects any tampering with block data
     * - Detects broken links between blocks
     * - Ensures Proof-of-Work was performed correctly
     * 
     * @return true if chain is valid, false if tampered
     */
    public boolean isChainValid() {
        // Check Genesis block
        Block genesis = chain.get(0);
        if (genesis.getIndex() != 0) return false;
        if (!genesis.getPreviousHash().equals(BlockchainConfig.GENESIS_PREV_HASH)) return false;

        // Build target string for difficulty check
        String target = "0".repeat(BlockchainConfig.MINING_DIFFICULTY);

        // Check Genesis block hash validity
        if (!genesis.getHash().equals(genesis.calculateHash())) return false;
        if (!genesis.getHash().startsWith(target)) return false;

        // Check rest of chain (from block 1 onwards)
        for (int i = 1; i < chain.size(); i++) {
            Block currentBlock = chain.get(i);
            Block previousBlock = chain.get(i - 1);

            // Verify current block's hash is correctly calculated
            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) return false;

            // Verify link to previous block
            if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) return false;

            // Verify block was mined (hash meets difficulty)
            if (!currentBlock.getHash().startsWith(target)) return false;
        }

        return true;
    }

    /**
     * Returns an unmodifiable copy of the chain.
     * 
     * THEORY: We return an unmodifiable list to preserve immutability.
     * External code should not be able to directly modify the chain.
     * 
     * @return Unmodifiable view of the blockchain
     */
    public List<Block> getChain() {
        return Collections.unmodifiableList(chain);
    }

    /**
     * Returns the number of blocks in the chain.
     * 
     * @return Chain length (including Genesis block)
     */
    public int getChainSize() {
        return chain.size();
    }

    /**
     * Gets a block by its index.
     * 
     * @param index Block index (0 = Genesis)
     * @return The block at the specified index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public Block getBlock(int index) {
        return chain.get(index);
    }

    /**
     * Prints a summary of the entire blockchain.
     */
    public void printChain() {
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("                     BLOCKCHAIN STATE                        ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("Chain length: " + chain.size() + " blocks");
        System.out.println("Chain valid: " + (isChainValid() ? "✓ YES" : "✗ NO"));
        System.out.println("───────────────────────────────────────────────────────────");

        for (Block block : chain) {
            System.out.println("Block #" + block.getIndex());
            System.out.println("  Data: " + block.getData());
            System.out.println("  Nonce: " + block.getNonce());
            System.out.println("  Hash: " + block.getHash().substring(0, 16) + "...");
            System.out.println("  Prev: " + block.getPreviousHash().substring(0, Math.min(16, block.getPreviousHash().length())) + "...");
            System.out.println();
        }
    }

    @Override
    public String toString() {
        return "Blockchain{" +
            "chainSize= " + chain.size() +
            ", isValid= " + isChainValid() +
            ", latestBlockHash= " + getLatestBlock().getHash().substring(0, 16) + "..." +
            "}";
    }
}