package com.blocksmith.core;

import java.lang.reflect.Field;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.BlockchainConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Blockchain.addBlock(Block) - accepting an externally-mined
 * block (e.g. one received from a peer) after validation.
 */
@DisplayName("External Block Append Tests")
class ExternalBlockTest {

    private Blockchain blockchain;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
    }

    /** Builds a valid, mined block that extends the current chain tip. */
    private Block mineNextBlock() {
        Block tip = blockchain.getLatestBlock();
        Block next = new Block(tip.getIndex() + 1, "external", tip.getHash());
        next.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        return next;
    }

    @Test
    @DisplayName("Valid externally-mined block is appended")
    void addBlock_appendsValidExternalBlock() {
        Block next = mineNextBlock();

        assertTrue(blockchain.addBlock(next), "Valid block should be accepted");
        assertEquals(2, blockchain.getChainSize(), "Chain should grow by one");
        assertSame(next, blockchain.getLatestBlock(), "Appended block becomes the new tip");
    }

    @Test
    @DisplayName("Block with a non-sequential index is rejected")
    void addBlock_rejectsWrongIndex() {
        Block tip = blockchain.getLatestBlock();
        Block gap = new Block(tip.getIndex() + 2, "external", tip.getHash());
        gap.mineBlock(BlockchainConfig.MINING_DIFFICULTY);

        assertFalse(blockchain.addBlock(gap), "Wrong index should be rejected");
        assertEquals(1, blockchain.getChainSize(), "Chain must be unchanged");
    }

    @Test
    @DisplayName("Block whose previousHash does not match the tip is rejected")
    void addBlock_rejectsWrongPreviousHash() {
        Block tip = blockchain.getLatestBlock();
        Block wrongLink = new Block(tip.getIndex() + 1, "external", "deadbeef");
        wrongLink.mineBlock(BlockchainConfig.MINING_DIFFICULTY);

        assertFalse(blockchain.addBlock(wrongLink), "Broken link should be rejected");
        assertEquals(1, blockchain.getChainSize(), "Chain must be unchanged");
    }

    @Test
    @DisplayName("Block whose stored hash does not match its contents is rejected")
    void addBlock_rejectsTamperedHash() throws Exception {
        Block next = mineNextBlock();

        // Corrupt the stored hash: still starts with the PoW target so it
        // passes the difficulty check, but no longer matches calculateHash().
        String target = "0".repeat(BlockchainConfig.MINING_DIFFICULTY);
        Field hashField = Block.class.getDeclaredField("hash");
        hashField.setAccessible(true);
        hashField.set(next, target + "f".repeat(64 - target.length()));

        assertFalse(blockchain.addBlock(next), "Tampered block should be rejected");
        assertEquals(1, blockchain.getChainSize(), "Chain must be unchanged");
    }
}
