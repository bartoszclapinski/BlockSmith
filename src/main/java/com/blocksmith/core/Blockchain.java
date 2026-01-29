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
 * TRANSACTION FLOW:
 * 1. User creates a transaction
 * 2. Transaction is added to the PENDING POOL
 * 3. Miner collects pending transactions
 * 4. Miner creates a new block with those transactions
 * 5. Miner mines the block (Proof-of-Work)
 * 6. Block is added to chain, transactions are confirmed
 * 
 * MINING REWARD:
 * When a miner successfully mines a block, they receive a reward.
 * This is implemented as a special "coinbase" transaction from
 * the system to the miner's address. 
 */
public class Blockchain {

    private final List<Block> chain;
    private final List<Transaction> pendingTransactions;

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
        this.pendingTransactions = new ArrayList<>();

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
     * Adds a new block to the blockchain (Legacy method for string data).    
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
     * Adds a transaction to the pending pool.
     * 
     * THEORY: MEMPOOL (Memory Pool)
     * 
     * In real blockchains, pending transactions wait in a "mempool"
     * until a miner includes them in a block. Miners typically
     * prioritize transactions wiht higher fees.
     * 
     * VALIDATION: 
     * 1. Transaction must be valid (amount > 0, non-empty addresses)
     * 2. COINBASE transactions are rejected (only created by mining)
     * 3. Sender must have sufficient balance
     * 
     * BALANCE CHECK:
     * We check both confirmed balance (in blockchain) and pending
     * outgoind transactions to prevent double-spending.
     * 
     * @param transaction The transaction to add
     * @return true if transaction was added, false if invalid
     */
    public boolean addTransaction(Transaction transaction) {
        // Validate the transaction
        if (transaction == null || !transaction.isValid()) {
            System.out.println("Transaction rejected: Invalid transaction");
            return false;
        }

        // Reject COINBASE transactions (only mining creates these)
        if (transaction.getSender().equals(BlockchainConfig.COINBASE_ADDRESS)) {
            System.out.println("Transaction rejected: Cannot manually create COINBASE transactions");
            return false;
        }

        // Check sender has sufficient balance
        double senderBalance = getBalance(transaction.getSender());
        double pendingOutgoing = getPendingOutgoing(transaction.getSender());
        double availableBalance = senderBalance - pendingOutgoing;

        if (availableBalance < transaction.getAmount()) {
            System.out.println("Transacion rejected: Insufficient funds. " + 
                "Available: " + availableBalance + " " + BlockchainConfig.CURRENCY_SYMBOL + 
                ", Required: " + transaction.getAmount() + " " + BlockchainConfig.CURRENCY_SYMBOL);
                return false;
        }

        // Add to pending pool
        pendingTransactions.add(transaction);
        System.out.println("Transaction added to pending pool: " + transaction);
        return true;
    }

    /**
     * Calculates total outgoing amount in pending transactions for an address
     * 
     * THEORY: PENDING BALANCE
     * 
     * When checking if someone can afford a transaction, we must also
     * consider funds they've already "promised" in pending transactions.
     * Otherwise, they could submit multiple transactions thath together
     * exceed their balance.
     * 
     * @param address Ther address to check
     * @return Total amount being sent in pending transactions
     */
    private double getPendingOutgoing(String address) {
        double pending = 0;
         for (Transaction tx : pendingTransactions) {
            if (tx.getSender().equals(address)) pending += tx.getAmount();
         }
         return pending;
    }

    /**
     * Mines all pending transactions into a new block.
     * 
     * THEORY: MINING PROCESS
     * 
     * 1. Collect pending transactions
     * 2. Add a COINBASE transaction (mining reward)
     * 3. Create a new block with all transactions
     * 4. Mine the block (find valid nonce)
     * 5. Add block to chain
     * 6. Clear pending transactions
     * 
     * COINBASE TRANSACTION:
     * A special transaction with no sender (from "COINBASE")
     * that rewards the miner. This is how new coins are created.
     * 
     * @param minerAddress Address to receive mining reward
     * @return The newly mined block, or null if no transactions
     */
    public Block minePendingTransactions(String minerAddress) {
        // Create coinbase (reward) transaction
        Transaction rewardTx = new Transaction(
            BlockchainConfig.COINBASE_ADDRESS,
            minerAddress,
            BlockchainConfig.MINING_REWARD
        );

        // Create transaction list with reward + pending transactions
        List<Transaction> blockTransactions = new ArrayList<>();
        blockTransactions.add(rewardTx);
        blockTransactions.addAll(pendingTransactions);

        // Create and mine the new block
        Block latestBlock = getLatestBlock();
        int newIndex = latestBlock.getIndex() + 1;

        Block newBlock = new Block (newIndex, blockTransactions, latestBlock.getHash());

        System.out.println("\n⛏️  Mining block #" + newIndex + " with " + blockTransactions.size() + " transactions...");
        newBlock.mineBlock(BlockchainConfig.MINING_DIFFICULTY);

        // Add block to chain
        chain.add(newBlock);

        // Clear pending transactions (they're now in a block)
        pendingTransactions.clear();

        System.out.println("✅ Block mined and added to chain!");
        System.out.println("   Miner " + minerAddress + " received " + 
            BlockchainConfig.MINING_REWARD + " " + BlockchainConfig.CURRENCY_SYMBOL);
        
        return newBlock;        
    }

    /**
     * Calculates the balance of an address.
     * 
     * THEORY: ACCOUNT BALANCE
     * 
     * In our simple model, balance = incoming - outgoing.
     * We scan ALL transactions in ALL blocks to calculate this.
     * 
     * BITCOIN uses UTXO (Unspent Transaction Outputs) instead,
     * which is more efficient for large chains but more complex.
     * 
     * @param address The addres to check
     * @return The current balance
     */
    public double getBalance(String address) {
        double balance = 0;

        // Scan all blocks
        for (Block block : chain) {
            // scan all transactions in block
            for (Transaction tx : block.getTransactions()) {
                // if address is sender, substract amount
                if (tx.getSender().equals(address)) balance -= tx.getAmount();

                // if address is recipient, add amount
                if (tx.getRecipient().equals(address)) balance += tx.getAmount();                       
            }
        }
        return balance;
    }

    /**
     * Return the pending transaction pool.
     * 
     * @return Unmodifiable list of pending transactions
     */
    public List<Transaction> getPendingTransactions() {
        return Collections.unmodifiableList(pendingTransactions);
    }

    /**
     * Returns the number of pending transactions.
     * 
     * @return Number of transactions waiting to be mined
     */
    public int getPendingCount() {
        return pendingTransactions.size();
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
        System.out.println("Pending transactions: " + pendingTransactions.size());
        System.out.println("Chain valid: " + (isChainValid() ? "✓ YES" : "✗ NO"));
        System.out.println("───────────────────────────────────────────────────────────");

        for (Block block : chain) {
            System.out.println("Block #" + block.getIndex());
            if (block.getTransactions().size() > 0) {
                System.out.println("  Transactions: " + block.getTransactionCount());
                for (Transaction tx : block.getTransactions()) {
                    System.out.println("    " + tx);
                }
            } else {
                System.out.println("  Data: " + block.getData());
            }            
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
            ", pendingTx= " + pendingTransactions.size() +
            ", isValid= " + isChainValid() +
            ", latestBlockHash= " + getLatestBlock().getHash().substring(0, 16) + "..." +
            "}";
    }
}