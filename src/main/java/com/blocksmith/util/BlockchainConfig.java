package com.blocksmith.util;

/**
 * Central configuration for the BlockSmith blockchain.
 * 
 * THEORY: In real blockchains, these parameters define the economic
 * and security properties of the network. For example:
 * - Bitcoin's difficulty adjusts every 2016 blocks
 * - Bitcoin's reward halves every 210,000 blocks
 * 
 * Our implementation uses fixed values for simplicity.
 */
public final class BlockchainConfig {
    private BlockchainConfig() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }

    /**
     * Number of leading zeros required in a valid block hash.
     * Higher = harder to mine = more secure = slower.
     * 
     * Examples:
     * - Difficulty 1: hash must start with "0" (~16 attempts)
     * - Difficulty 4: hash must start with "0000" (~65,536 attempts)
     * - Difficulty 6: hash must start with "000000" (~16 million attempts)
     */
    public static final int MINING_DIFFICULTY = 4;

    /**
     * Reward given to miner for successfully mining a block.
     * This is how new coins enter circulation.
    */
    public static final double MINING_REWARD = 50.0;

    /**
     * Optional transaction fee (for future implementation)
     */
    public static final double TRANSACTION_FEE = 0.1;

    /**
     * Previous hash value for the Genesis block.
     * Since Genesis has no predecessor, we use "0".
    */
    public static final String GENESIS_PREV_HASH = "0";

    /**
     * Address used as sender for mining reward transactions.
     * Coinbase transactions create new coins "from nothing".
    */
    public static final String COINBASE_ADDRESS = "COINBASE";

    /**
     * Currency symbol for display purposes.
    */
    public static final String CURRENCY_SYMBOL = "BSC";
}