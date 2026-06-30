package com.blocksmith.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.BlockchainConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for orphan block handling in Blockchain - buffering blocks that
 * arrive before their parent and attaching them once the parent appears.
 */
@DisplayName("Orphan Block Tests")
class OrphanBlockTest {

    private Blockchain blockchain;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
    }

    /** Mines a well-formed block at the given index linking to parentHash. */
    private Block mine(int index, String parentHash) {
        Block block = new Block(index, "orphan", parentHash);
        block.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        return block;
    }

    @Test
    @DisplayName("Out-of-order blocks are buffered then attached when the parent arrives")
    void outOfOrderBlocks_bufferThenAttach() {
        Block genesis = blockchain.getLatestBlock();
        Block n = mine(1, genesis.getHash());
        Block n1 = mine(2, n.getHash());

        // Child arrives first: cannot connect yet, so it is buffered.
        assertFalse(blockchain.addBlock(n1), "Child has no parent yet -> not appended");
        assertEquals(1, blockchain.getChainSize(), "Chain unchanged while child is orphaned");
        assertEquals(1, blockchain.getOrphanCount(), "Child should be buffered");

        // Parent arrives: it appends and pulls the buffered child in behind it.
        assertTrue(blockchain.addBlock(n), "Parent extends the tip -> appended");
        assertEquals(3, blockchain.getChainSize(), "Parent and child both end up in the chain");
        assertEquals(0, blockchain.getOrphanCount(), "Buffer drained after attaching");
        assertEquals(n1.getHash(), blockchain.getLatestBlock().getHash(),
                "Child should be the new tip");
    }

    @Test
    @DisplayName("Orphan buffer is bounded")
    void orphanBuffer_isBounded() {
        for (int i = 0; i < Blockchain.MAX_ORPHANS + 10; i++) {
            // Distinct parent hashes -> distinct buffer keys; index beyond tip+1.
            blockchain.addBlock(mine(2 + i, "parent-" + i));
        }

        assertEquals(Blockchain.MAX_ORPHANS, blockchain.getOrphanCount(),
                "Buffer must not grow past its cap");
        assertEquals(1, blockchain.getChainSize(), "No orphan should have attached");
    }

    @Test
    @DisplayName("Orphan whose parent never arrives stays buffered")
    void orphanWithNoParent_staysBuffered() {
        Block orphan = mine(5, "ghost-parent");

        assertFalse(blockchain.addBlock(orphan), "Disconnected block is not appended");
        assertEquals(1, blockchain.getChainSize(), "Chain remains just the genesis block");
        assertEquals(1, blockchain.getOrphanCount(), "Orphan remains buffered");
    }
}
