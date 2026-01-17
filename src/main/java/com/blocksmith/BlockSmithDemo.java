package com.blocksmith;

import com.blocksmith.core.Block;
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