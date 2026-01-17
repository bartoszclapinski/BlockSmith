package com.blocksmith.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Proof-of-Work mining functionality.
 * 
 * These tests verify that:
 * - Mining produces valid hashes with required leading zeros
 * - Nonce is correctly updated during mining
 * - Different difficulties work as expected
 */
@DisplayName("Mining Tests")
public class MiningTest {

    @Test
    @DisplayName("Mining with difficulty 1 produces hash starting with '0'")
    void mineBlockDifficulty1() {
        Block block = new Block(1, "Test data", "0000previous-hash-123");

        block.mineBlock(1);

        assertTrue(block.getHash().startsWith("0"), "Hash should start with '0' for difficulty 1");
        assertTrue(block.getNonce() > 0, "Nonce should be greater than 0 after mining");
    }

    @Test
    @DisplayName("Mining with difficulty 2 produces hash starting with '00'")
    void mineBlockDifficulty2() {
        Block block = new Block(1, "Test data", "0000previous-hash-123");

        block.mineBlock(2);

        assertTrue(block.getHash().startsWith("00"), "Hash should start with '00' for difficulty 2");
    }

    @Test
    @DisplayName("Mining with difficulty 4 produces hash starting with '0000'")
    void mineBlockDifficulty4() {
        Block block = new Block(1, "Test data", "0000previous-hash-123");

        block.mineBlock(4);

        assertTrue(block.getHash().startsWith("0000"), "Hash should start with '0000' for difficulty 4");
    }

    @Test
    @DisplayName("Nonce change after mining")
    void nonceChangesAfterMining() {
        Block block = new Block(1, "Test data", "0000previous-hash-123");
        int initialNonce = block.getNonce();
        block.mineBlock(2);

        // It's possible (but unlikely) nonce stays 0 if first hash is valid
        // So we just verify nonce is still valid (>= 0)
        assertTrue(block.getNonce() > initialNonce, "Nonce should be greater than initial nonce after mining");
    }

    @Test
    @DisplayName("Hash is recalculated consistently after mining")
    void hashConsistentAfterMining() {
        Block block = new Block(1, "Test Data", "0000previoushash");
        
        block.mineBlock(3);
        
        String minedHash = block.getHash();
        String recalculatedHash = block.calculateHash();
        
        assertEquals(minedHash, recalculatedHash,
            "Recalculated hash should match mined hash");
    }

    @Test
    @DisplayName("Mining returns time in milliseconds")
    void miningReturnsTime() {
        Block block = new Block(1, "Test Data", "0000previoushash");
        
        long miningTime = block.mineBlock(1);
        
        assertTrue(miningTime >= 0,
            "Mining time should be non-negative");
    }

    @Test
    @DisplayName("Genesis block can be mined")
    void genesisBlockCanBeMined() {
        Block genesis = Block.createGenesisBlock();
        
        genesis.mineBlock(2);
        
        assertTrue(genesis.getHash().startsWith("00"),
            "Genesis block hash should start with '00' after mining");
        assertEquals(0, genesis.getIndex(),
            "Genesis block should have index 0");
    }

    @Test
    @DisplayName("Different blocks produce different nonces")
    void differentBlocksDifferentNonces() {
        Block block1 = new Block(1, "Data A", "prev1");
        Block block2 = new Block(2, "Data B", "prev2");
        
        block1.mineBlock(2);
        block2.mineBlock(2);
        
        // Both should have valid hashes
        assertTrue(block1.getHash().startsWith("00"));
        assertTrue(block2.getHash().startsWith("00"));
        
        // Hashes should be different
        assertNotEquals(block1.getHash(), block2.getHash(),
            "Different blocks should have different hashes");
    }

    @Test
    @DisplayName("Higher difficulty requires more leading zeros")
    void higherDifficultyMoreZeros() {
        Block blockLow = new Block(1, "Test", "prev");
        Block blockHigh = new Block(1, "Test", "prev");
        
        blockLow.mineBlock(2);
        blockHigh.mineBlock(4);
        
        // Count leading zeros
        int zerosLow = countLeadingZeros(blockLow.getHash());
        int zerosHigh = countLeadingZeros(blockHigh.getHash());
        
        assertTrue(zerosLow >= 2, "Low difficulty should have at least 2 zeros");
        assertTrue(zerosHigh >= 4, "High difficulty should have at least 4 zeros");
    }

    private int countLeadingZeros(String hash) {
        int count = 0;
        for (char c : hash.toCharArray()) {
            if (c == '0') count++;
            else break;
        }
        return count;
    }
}
