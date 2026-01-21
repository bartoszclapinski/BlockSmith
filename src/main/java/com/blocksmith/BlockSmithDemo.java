package com.blocksmith;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.util.BlockchainConfig;

/**
 * BlockSmith demonstration - shows Proof-of-Work mining in action.
 * 
 * This demo creates blocks and mines them at different difficulties
 * to demonstrate how Proof-of-Work scales exponentially.
 */
public class BlockSmithDemo {
    
    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                    BLOCKSMITH v1.0.0                       ");
        System.out.println("              Proof-of-Work Mining Demo                     ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();

        // Create and mine Genesis block
        System.out.println("▶ Creating Genesis Block...");
        Block genesis = Block.createGenesisBlock();
        System.out.println("    Mining with difficulty " + BlockchainConfig.MINING_DIFFICULTY + "...");
        long genesisTime = genesis.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        System.out.println();

        // Create and mine Block #1
        System.out.println("▶ Creating Block #1...");
        Block block1 = new Block(1, "Alice sends 10 BSM to Bob", genesis.getHash());
        System.out.println("  Mining with difficulty " + BlockchainConfig.MINING_DIFFICULTY + "...");
        long block1Time = block1.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        System.out.println();

        // Create and mine Block #2
        System.out.println("▶ Creating Block #2...");
        Block block2 = new Block(2, "Bob sends 5 BSM to Charlie", block1.getHash());
        System.out.println("  Mining with difficulty " + BlockchainConfig.MINING_DIFFICULTY + "...");
        long block2Time = block2.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        System.out.println();

        // Show chain summary
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                      CHAIN SUMMARY                         ");
        System.out.println("═══════════════════════════════════════════════════════════");
        printBlockSummary(genesis);
        printBlockSummary(block1);
        printBlockSummary(block2);

        // Mining statistics
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                    MINING STATISTICS                       ");
        System.out.println("═══════════════════════════════════════════════════════════");
        long totalTime = genesisTime + block1Time + block2Time;
        int totalNonces = genesis.getNonce() + block1.getNonce() + block2.getNonce();
        System.out.println("  Total blocks mined: 3");
        System.out.println("  Total mining time:  " + totalTime + "ms");
        System.out.println("  Total attempts:     " + totalNonces);
        System.out.println("  Avg time per block: " + (totalTime / 3) + "ms");
        System.out.println();

        // Difficulty comparison
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                  DIFFICULTY COMPARISON                     ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Mining same block at different difficulties:");
        System.out.println();
        
        for (int diff = 1; diff <= 5; diff++) {
            Block testBlock = new Block(99, "Benchmark test", "0000test");
            long time = testBlock.mineBlock(diff);
            System.out.printf("  Difficulty %d: %,d attempts in %dms%n", 
                diff, testBlock.getNonce(), time);
        }
        
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("  Notice: Each +1 difficulty = ~16x more work (on average)");
        System.out.println("═══════════════════════════════════════════════════════════");

        // Blockchain demo
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                    BLOCKCHAIN DEMO                         ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        
        System.out.println("▶ Creating blockchain with automatic Genesis block...");
        Blockchain blockchain = new Blockchain();
        System.out.println();
        
        System.out.println("▶ Adding blocks to the chain...");
        blockchain.addBlock("Alice sends 50 BSM to Bob");
        blockchain.addBlock("Bob sends 25 BSM to Charlie");
        System.out.println();
        
        System.out.println("▶ Blockchain state:");
        blockchain.printChain();
        
        System.out.println("▶ Validating chain integrity...");
        System.out.println("  Chain valid: " + blockchain.isChainValid());
        System.out.println();
        
        // Transaction demo
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                   TRANSACTION DEMO                         ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        
        System.out.println("▶ Creating a fresh blockchain for transaction demo...");
        Blockchain txBlockchain = new Blockchain();
        System.out.println();
        
        System.out.println("▶ Miner1 mines the first block (receives 50 BSC reward)...");
        txBlockchain.minePendingTransactions("Miner1");
        System.out.println();
        
        System.out.println("▶ Creating transactions...");
        Transaction tx1 = new Transaction("Miner1", "Alice", 30.0);
        Transaction tx2 = new Transaction("Miner1", "Bob", 15.0);
        System.out.println("  " + tx1);
        System.out.println("  " + tx2);
        System.out.println();
        
        System.out.println("▶ Adding transactions to pending pool...");
        txBlockchain.addTransaction(tx1);
        txBlockchain.addTransaction(tx2);
        System.out.println("  Pending transactions: " + txBlockchain.getPendingCount());
        System.out.println();
        
        System.out.println("▶ Miner2 mines the block with pending transactions...");
        txBlockchain.minePendingTransactions("Miner2");
        System.out.println();
        
        System.out.println("▶ Checking balances...");
        System.out.println("  Miner1: " + txBlockchain.getBalance("Miner1") + " BSC");
        System.out.println("  Miner2: " + txBlockchain.getBalance("Miner2") + " BSC");
        System.out.println("  Alice:  " + txBlockchain.getBalance("Alice") + " BSC");
        System.out.println("  Bob:    " + txBlockchain.getBalance("Bob") + " BSC");
        System.out.println();
        
        System.out.println("▶ Alice sends 10 BSC to Bob...");
        txBlockchain.addTransaction(new Transaction("Alice", "Bob", 10.0));
        txBlockchain.minePendingTransactions("Miner1");
        System.out.println();
        
        System.out.println("▶ Final balances:");
        System.out.println("  Miner1: " + txBlockchain.getBalance("Miner1") + " BSC (mined 2 blocks)");
        System.out.println("  Miner2: " + txBlockchain.getBalance("Miner2") + " BSC");
        System.out.println("  Alice:  " + txBlockchain.getBalance("Alice") + " BSC (30 - 10 = 20)");
        System.out.println("  Bob:    " + txBlockchain.getBalance("Bob") + " BSC (15 + 10 = 25)");
        System.out.println();
        
        txBlockchain.printChain();
        
        // Tamper detection
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                  TAMPER DETECTION DEMO                     ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("▶ Simulating attack on Block #1's Merkle root...");
        System.out.println("  (Merkle root summarizes all transactions in a block)");
        System.out.println();
        
        // Tamper using reflection (now targeting merkleRoot)
        try {
            java.lang.reflect.Field merkleField = Block.class.getDeclaredField("merkleRoot");
            merkleField.setAccessible(true);
            merkleField.set(blockchain.getBlock(1), "HACKED_MERKLE_ROOT_1234567890123456789012345678901234");
        } catch (Exception e) {
            System.out.println("  Tamper failed: " + e.getMessage());
        }
        
        System.out.println("▶ Re-validating chain after tampering...");
        boolean isValid = blockchain.isChainValid();
        System.out.println("  Chain valid: " + isValid);
        System.out.println();
        
        if (!isValid) {
            System.out.println("┌─────────────────────────────────────────────────────────┐");
            System.out.println("│  ⚠ ATTACK DETECTED! Blockchain integrity compromised!  │");
            System.out.println("│  This demonstrates why blockchain is tamper-evident.   │");
            System.out.println("└─────────────────────────────────────────────────────────┘");
        }
        System.out.println();        
    }

    private static void printBlockSummary(Block block) {
        System.out.println("┌─ Block #" + block.getIndex());
        System.out.println("│  Data: " + block.getData());
        System.out.println("│  Nonce: " + block.getNonce());
        System.out.println("│  Hash: " + block.getHash().substring(0, 16) + "...");
        System.out.println("└─ PrevHash: " + block.getPreviousHash().substring(0, Math.min(16, block.getPreviousHash().length())) + "...");
        System.out.println();
    }

        
    
}