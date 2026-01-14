package com.blocksmith.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for Block class functionality.
 */
public class BlockTest {
    
    @Test
    @DisplayName("Block should be created with correct properties")
    void testBlockCreation() {
        Block block = new Block(1, "Test data", "previous-hash-123");

        assertEquals(1, block.getIndex(), "Index should match");
        assertEquals("Test data", block.getData(), "Data should match");
        assertEquals("previous-hash-123", block.getPreviousHash(), "Previous hash should match");
        assertNotNull(block.getHash(), "Hash should not be null");
        assertTrue(block.getTimestamp() > 0, "Timestamp should be greater than 0");        
    }

    @Test
    @DisplayName("Block hash should be 64 characters long")
    void testHashLength() {
        Block block = new Block(1, "Test data", "previous-hash-123");
        String hash = block.getHash();
        assertEquals(64, hash.length(), "Hash should be 64 characters long");
    }

    @Test
    @DisplayName("calculateHash should produce consistent hashes")
    void testHashConsistency() {
        Block block = new Block(1, "Test data", "previous-hash-123");
        String hash1 = block.calculateHash();
        String hash2 = block.calculateHash();
        assertEquals(hash1, hash2, "Hashes should be the same");
    }

    @Test
    @DisplayName("Genesis block should have correct properties")
    void testGenesisBlock() {
        Block genesis = Block.createGenesisBlock();
        assertEquals(0, genesis.getIndex(), "Index should be 0");
        assertEquals("0", genesis.getPreviousHash(), "Previous hash should be 0");
        assertNotNull(genesis.getHash(), "Hash should not be null");
        assertTrue(genesis.getData().contains("Genesis"), "Genesis block should contain 'Genesis' in data");
    }

    @Test
    @DisplayName("Different blocks should have different hashes")
    void testDifferentBlocksHaveDifferentHashes() {
        Block block1 = new Block(1, "Test data 1", "previous-hash-123");
        Block block2 = new Block(1, "Test data 2", "previous-hash-123");
        assertNotEquals(block1.getHash(), block2.getHash(), "Hashes should be different");
    }

    @Test
    @DisplayName("Block toString should contain all key information")
    void testToString() {
        Block block = new Block(1, "Test data", "previous-hash-123");
        String toString = block.toString();
        assertTrue(toString.contains("index=1"), "toString should contain index");
        assertTrue(toString.contains("Test data"), "toString should contain data");
        assertTrue(toString.contains("previous-hash-123"), "toString should contain previous hash");
    }
}
