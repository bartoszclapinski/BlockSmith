package com.blocksmith.core;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.BlockchainConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that appending an externally-mined block prunes its transactions from
 * the pending pool - a transaction gossiped to us earlier must not linger once
 * a block confirms it (Milestone 11b).
 */
@DisplayName("Mempool Prune Tests")
class MempoolPruneTest {

    private Blockchain blockchain;

    @BeforeEach
    void setUp() {
        blockchain = new Blockchain();
        // Fund "Miner1" so it can send spendable transactions.
        blockchain.minePendingTransactions("Miner1");
    }

    /** Builds a valid, mined block carrying the given transactions on the tip. */
    private Block mineBlockWith(List<Transaction> transactions) {
        Block tip = blockchain.getLatestBlock();
        Block block = new Block(tip.getIndex() + 1, transactions, tip.getHash());
        block.mineBlock(BlockchainConfig.MINING_DIFFICULTY);
        return block;
    }

    @Test
    @DisplayName("Appending a block prunes its transactions from the pending pool")
    void appendingBlock_prunesConfirmedTransactions() {
        Transaction tx = new Transaction("Miner1", "Alice", 30.0);
        assertTrue(blockchain.addTransaction(tx), "Transaction should enter the pool");
        assertEquals(1, blockchain.getPendingTransactions().size(), "Pool holds the tx");

        // A peer's block confirming that same transaction arrives.
        Block block = mineBlockWith(List.of(tx));
        assertTrue(blockchain.addBlock(block), "Block extends the tip and is appended");

        assertEquals(0, blockchain.getPendingTransactions().size(),
                "Confirmed transaction should be removed from the pool");
    }

    @Test
    @DisplayName("Appending a block keeps unrelated pending transactions")
    void appendingBlock_keepsUnrelatedPending() {
        Transaction pending = new Transaction("Miner1", "Alice", 30.0);
        assertTrue(blockchain.addTransaction(pending), "Pending tx should enter the pool");

        // The incoming block confirms a different transaction.
        Transaction other = new Transaction("Miner1", "Bob", 10.0);
        Block block = mineBlockWith(List.of(other));
        assertTrue(blockchain.addBlock(block), "Block extends the tip and is appended");

        List<Transaction> pool = blockchain.getPendingTransactions();
        assertEquals(1, pool.size(), "Unrelated pending tx should remain");
        assertEquals(pending.getTransactionId(), pool.get(0).getTransactionId(),
                "The surviving tx should be the unrelated one");
    }
}
