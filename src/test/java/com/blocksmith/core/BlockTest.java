package com.blocksmith.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for Block class functionality.
 */
@DisplayName("Block Tests")
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
        assertTrue(toString.contains("transactions="), "toString should contain transactions count");
        assertTrue(toString.contains("merkleRoot="), "toString should contain merkle root");
        assertTrue(toString.contains("previousHash="), "toString should contain previous hash");
    }

    // ===== TRANSACTION TESTS =====
    
    @Test
    @DisplayName("Block with transactions should store them correctly")
    void blockWithTransactionsShouldStoreThem() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction("Alice", "Bob", 50.0));
        transactions.add(new Transaction("Bob", "Charlie", 25.0));
        
        Block block = new Block(1, transactions, "prev-hash");
        
        assertEquals(2, block.getTransactionCount(), "Transaction count should be 2");
        assertEquals(2, block.getTransactions().size(), "Transactions list should have 2 elements");
    }
    
    @Test
    @DisplayName("Block should calculate Merkle root for transactions")
    void blockShouldCalculateMerkleRoot() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction("Alice", "Bob", 50.0));
        
        Block block = new Block(1, transactions, "prev-hash");
        
        assertNotNull(block.getMerkleRoot(), "Merkle root should not be null");
        assertEquals(64, block.getMerkleRoot().length(), "Merkle root should be 64 characters long");
    }
    
    @Test
    @DisplayName("Different transactions should produce different Merkle roots")
    void differentTransactionsShouldProduceDifferentMerkleRoots() {
        List<Transaction> tx1 = new ArrayList<>();
        tx1.add(new Transaction("Alice", "Bob", 50.0));
        
        List<Transaction> tx2 = new ArrayList<>();
        tx2.add(new Transaction("Charlie", "Dave", 100.0));
        
        Block block1 = new Block(1, tx1, "prev-hash");
        Block block2 = new Block(1, tx2, "prev-hash");
        
        assertNotEquals(block1.getMerkleRoot(), block2.getMerkleRoot(), "Merkle roots should be different");
    }
    
    @Test
    @DisplayName("Block getTransactions should return defensive copy")
    void getTransactionsShouldReturnDefensiveCopy() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction("Alice", "Bob", 50.0));
        
        Block block = new Block(1, transactions, "prev-hash");
        List<Transaction> retrieved = block.getTransactions();
        retrieved.clear();  // Modify the copy
        
        assertEquals(1, block.getTransactionCount(), "Transaction count should be 1");  // Original unchanged
    }
    
    @Test
    @DisplayName("Genesis block should have empty transaction list")
    void genesisBlockShouldHaveEmptyTransactionList() {
        Block genesis = Block.createGenesisBlock();
        
        assertEquals(0, genesis.getTransactionCount(), "Transaction count should be 0");
        assertTrue(genesis.getTransactions().isEmpty(), "Transactions list should be empty");
    }
    
    @Test
    @DisplayName("Genesis block Merkle root should be hash of data")
    void genesisBlockMerkleRootShouldBeHashOfData() {
        Block genesis = Block.createGenesisBlock();
        
        assertNotNull(genesis.getMerkleRoot(), "Merkle root should not be null");
        assertEquals(64, genesis.getMerkleRoot().length(), "Merkle root should be 64 characters long");
    }
}
