package com.blocksmith.core;

import com.blocksmith.util.BlockchainConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Blockchain class.
 * 
 * Test cover:
 * - Chain creation with Genesis block
 * - Adding blocks and maintaining chain integrity
 * - Chain validation (isChainValid)
 * - Tamper detection
 */
@DisplayName("Blockchain Tests")
public class BlockchainTest {
    
    private Blockchain blockchain;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
    }

    // ==================== GENESIS BLOCK TESTS ====================

    @Test
    @DisplayName("New blockchain starts with Genesis block")
    void newBlockchainHasGenesisBlock() {
        assertEquals(1, blockchain.getChainSize());

        Block genesis = blockchain.getBlock(0);
        assertEquals(0, genesis.getIndex(), "Genesis block index should be 0");
        assertEquals(BlockchainConfig.GENESIS_PREV_HASH, genesis.getPreviousHash(), 
                "Genesis block previous hash should be '0'");
    }

    @Test
    @DisplayName("Genesis block has correct data (contains 'Genesis')")
    void genesisBlockHasCorrectData() {
        Block genesis = blockchain.getBlock(0);        
        assertTrue(genesis.getData().contains("Genesis"),
                "Genesis block data should contain 'Genesis'");
    }

    @Test
    @DisplayName("Genesis block hash meets mining difficulty")
    void genesisBlockIsMinedCorrectly() {
        Block genesis = blockchain.getBlock(0);        
        String target = "0".repeat(BlockchainConfig.MINING_DIFFICULTY);
        assertTrue(genesis.getHash().startsWith(target),
                "Genesis block hash should start with " + target);
    }

    // ==================== ADD BLOCK TESTS ====================

    @Test
    @DisplayName("Adding block increases chain size")
    void addBlockIncreasesChainSize() {
        blockchain.addBlock("Test block 1");
        assertEquals(2, blockchain.getChainSize(), "Chain size should be 2 after adding one block");

        blockchain.addBlock("Test block 2");
        assertEquals(3, blockchain.getChainSize(), "Chain size should be 3 after adding two blocks");
    }

    @Test
    @DisplayName("Added block has correct index")
    void addedBlockHasCorrectIndex() {
        Block block1 = blockchain.addBlock("Block 1");
        assertEquals(1, block1.getIndex(), "Index should be 1 for first block");
        
        Block block2 = blockchain.addBlock("Block 2");
        assertEquals(2, block2.getIndex(), "Index should be 2 for second block");
    }

    @Test
    @DisplayName("Added block links to previous block")
    void addedBlockLinksCorrectly() {
        Block genesis = blockchain.getBlock(0);
        Block block1 = blockchain.addBlock("Block 1");
        
        assertEquals(genesis.getHash(), block1.getPreviousHash(), "Previous hash should match genesis block");
        
        Block block2 = blockchain.addBlock("Block 2");
        assertEquals(block1.getHash(), block2.getPreviousHash(), "Previous hash should match previous block");
    }

    @Test
    @DisplayName("Added block is mined")
    void addedBlockIsMined() {
        Block block1 = blockchain.addBlock("Test data");
        String target = "0".repeat(BlockchainConfig.MINING_DIFFICULTY);
        assertTrue(block1.getHash().startsWith(target), "Hash should start with " + target);
    }

    @Test
    @DisplayName("Added block stores correct data")
    void addedBlockStoresData() {
        String testData = "Alice sends 100 BSM to Bob";
        Block block = blockchain.addBlock(testData);
        assertEquals(testData, block.getData(), "Data should match");
    }

    // ==================== CHAIN VALIDATION TESTS ====================

    @Test
    @DisplayName("Valid chain returns true")
    void validChainReturnsTrue() {
        blockchain.addBlock("Block 1");
        blockchain.addBlock("Block 2");
        blockchain.addBlock("Block 3");
        
        assertTrue(blockchain.isChainValid(), "Unmodified chain should be valid");
    }

    @Test
    @DisplayName("Empty chain (only genesis) is valid")
    void emptyChainIsValid() {
        assertTrue(blockchain.isChainValid(), "Chain with only genesis should be valid");
    }

    // ==================== TAMPER DETECTION TESTS ====================

    @Test
    @DisplayName("Tampered block data invalidates chain")
    void tamperedDataInvalidatesChain() {
        blockchain.addBlock("Original data");
        blockchain.addBlock("More data");
        
        // Tamper with block 1's merkleRoot using reflection
        // (merkleRoot is now used in hash calculation instead of data)
        Block block1 = blockchain.getBlock(1);
        try {
            java.lang.reflect.Field merkleField = Block.class.getDeclaredField("merkleRoot");
            merkleField.setAccessible(true);
            merkleField.set(block1, "HACKED_MERKLE_ROOT_12345678901234567890123456789012");
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
        
        assertFalse(blockchain.isChainValid(), "Chain with tampered merkle root should be invalid");
    }

    @Test
    @DisplayName("Broken chain link invalidates chain")
    void brokenLinkInvalidatesChain() {
        blockchain.addBlock("Block 1");
        blockchain.addBlock("Block 2");
        
        // Tamper with block 2's previousHash using reflection
        Block block2 = blockchain.getBlock(2);
        try {
            java.lang.reflect.Field prevHashField = Block.class.getDeclaredField("previousHash");
            prevHashField.setAccessible(true);
            prevHashField.set(block2, "0000fake_previous_hash");
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
        
        assertFalse(blockchain.isChainValid(), "Chain with broken link should be invalid");
    }

    // ===== TRANSACTION POOL TESTS =====

    @Test
    @DisplayName("addTransaction should accept valid transaction")
    void addTransactionShouldAcceptValidTransaction() {
        Transaction tx = new Transaction("Alice", "Bob", 50.0);
        
        assertTrue(blockchain.addTransaction(tx), "Valid transaction should be accepted");
        assertEquals(1, blockchain.getPendingCount(), "Pending count should be 1");
    }

    @Test
    @DisplayName("addTransaction should reject null transaction")
    void addTransactionShouldRejectNull() {
        assertFalse(blockchain.addTransaction(null), "Null transaction should be rejected");
        assertEquals(0, blockchain.getPendingCount(), "Pending count should be 0");
    }
    
    @Test
    @DisplayName("addTransaction should reject invalid transaction")
    void addTransactionShouldRejectInvalid() {
        Transaction invalidTx = new Transaction("Alice", "Bob", -100.0);
        
        assertFalse(blockchain.addTransaction(invalidTx), "Invalid transaction should be rejected");
        assertEquals(0, blockchain.getPendingCount(), "Pending count should be 0");
    }
    
    @Test
    @DisplayName("minePendingTransactions should create block with transactions")
    void minePendingTransactionsShouldCreateBlock() {
        blockchain.addTransaction(new Transaction("Alice", "Bob", 50.0));
        blockchain.addTransaction(new Transaction("Bob", "Charlie", 25.0));
        
        int initialSize = blockchain.getChainSize();
        Block minedBlock = blockchain.minePendingTransactions("Miner1");
        
        assertEquals(initialSize + 1, blockchain.getChainSize(), "Chain should grow by 1");
        assertEquals(3, minedBlock.getTransactionCount(), "Block should have 3 transactions (reward + 2)");
        assertEquals(0, blockchain.getPendingCount(), "Pending pool should be empty");
    }
    
    @Test
    @DisplayName("minePendingTransactions should include mining reward")
    void minePendingTransactionsShouldIncludeReward() {
        Block minedBlock = blockchain.minePendingTransactions("Miner1");
        
        Transaction rewardTx = minedBlock.getTransactions().get(0);
        assertEquals("COINBASE", rewardTx.getSender(), "First tx should be from COINBASE");
        assertEquals("Miner1", rewardTx.getRecipient(), "Reward should go to miner");
        assertEquals(50.0, rewardTx.getAmount(), "Reward should be 50 BSC");
    }
    
    @Test
    @DisplayName("getBalance should calculate correct balance")
    void getBalanceShouldCalculateCorrectBalance() {
        // Mine a block so Miner1 gets 50 BSC
        blockchain.minePendingTransactions("Miner1");
        
        // Miner1 sends 20 to Alice
        blockchain.addTransaction(new Transaction("Miner1", "Alice", 20.0));
        blockchain.minePendingTransactions("Miner2");
        
        // Miner1: 50 (reward) - 20 (sent) = 30
        assertEquals(30.0, blockchain.getBalance("Miner1"), 0.001, "Miner1 balance should be 30");
        // Alice: 0 + 20 (received) = 20
        assertEquals(20.0, blockchain.getBalance("Alice"), 0.001, "Alice balance should be 20");
        // Miner2: 50 (reward)
        assertEquals(50.0, blockchain.getBalance("Miner2"), 0.001, "Miner2 balance should be 50");
    }
    
    @Test
    @DisplayName("getPendingTransactions should return unmodifiable list")
    void getPendingTransactionsShouldReturnUnmodifiableList() {
        blockchain.addTransaction(new Transaction("Alice", "Bob", 50.0));
        
        assertThrows(UnsupportedOperationException.class, () -> {
            blockchain.getPendingTransactions().clear();
        }, "Should not be able to modify pending transactions");
    }

}
