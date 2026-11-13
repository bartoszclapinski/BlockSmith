package com.blocksmith.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for block size and transaction-count limits (Milestone 18b).
 */
@DisplayName("Block Limits Tests")
class BlockLimitsTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    /** Easy target; at most 3 transactions per block (coinbase included) and the default byte limit. */
    private static final ChainParams THREE_TX = params(ChainParams.DEFAULT_MAX_BLOCK_BYTES, 3);

    private static ChainParams params(int maxBytes, int maxTxs) {
        return new ChainParams(Target.fromLeadingZeros(1), 10_000, 10, 4, Target.MAX,
                ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS, maxBytes, maxTxs);
    }

    private static Blockchain chain(ChainParams p) {
        return new Blockchain(NoOpChainStore.INSTANCE, p);
    }

    private static Transaction coinbase(String to) {
        return new Transaction(BlockchainConfig.COINBASE_ADDRESS, to, BlockchainConfig.MINING_REWARD);
    }

    private static Block blockOn(Blockchain c, List<Transaction> txs) {
        Block tip = c.getLatestBlock();
        long ts = Math.max(System.currentTimeMillis(), tip.getTimestamp() + 1);
        Block b = new Block(tip.getIndex() + 1, txs, tip.getHash(), ts);
        b.mineBlock(c.getNextTarget());
        return b;
    }

    /** {@code n} small payments from Alice to Bob (Alice needs n coins). */
    private static List<Transaction> payments(int n) {
        List<Transaction> txs = new ArrayList<>();
        for (int i = 0; i < n; i++) txs.add(new Transaction(ALICE, BOB, 1 + i * 0.01));
        return txs;
    }

    // ===== CONSENSUS =====

    @Test
    @DisplayName("A peer block with more transactions than allowed is rejected; at the limit it is fine")
    void tooManyTransactions_rejected() {
        Blockchain c = chain(THREE_TX);
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(ALICE)))));

        List<Transaction> four = new ArrayList<>(List.of(coinbase(BOB)));
        four.addAll(payments(3));
        assertFalse(c.addBlock(blockOn(c, four)), "4 > 3");

        List<Transaction> three = new ArrayList<>(List.of(coinbase(BOB)));
        three.addAll(payments(2));
        assertTrue(c.addBlock(blockOn(c, three)));
    }

    @Test
    @DisplayName("A peer block over the byte limit is rejected; one under it is fine")
    void oversizePeerBlock_rejected() {
        Blockchain lax = chain(params(64 * 1024, 1_000));
        assertTrue(lax.addBlock(blockOn(lax, List.of(coinbase(ALICE)))));
        List<Transaction> txs = new ArrayList<>(List.of(coinbase(BOB)));
        txs.addAll(payments(10));
        Block big = blockOn(lax, txs);
        int size = big.sizeBytes();

        Blockchain strictTooSmall = chain(params(Math.max(1_024, size - 1), 1_000));
        assertTrue(strictTooSmall.addBlock(lax.getBlock(1)));
        assertFalse(strictTooSmall.addBlock(big), size + " bytes > " + (size - 1));

        Blockchain strictJustRight = chain(params(size, 1_000));
        assertTrue(strictJustRight.addBlock(lax.getBlock(1)));
        assertTrue(strictJustRight.addBlock(big), "Exactly at the limit is allowed");
    }

    @Test
    @DisplayName("An oversize orphan is not even buffered")
    void oversizeOrphan_notBuffered() {
        Blockchain c = chain(params(1_024, 1_000));
        List<Transaction> txs = new ArrayList<>(List.of(coinbase(BOB)));
        txs.addAll(payments(10));                                 // ~2.5 KB
        Block orphan = new Block(5, txs, "0000unknown-parent", System.currentTimeMillis());
        orphan.mineBlock(Target.MAX);
        assertFalse(c.addBlock(orphan));
        assertEquals(0, c.getOrphanCount());
    }

    // ===== MINING =====

    @Test
    @DisplayName("A mempool bigger than one block is mined over several blocks, nothing lost, order kept")
    void fullMempool_spreadOverBlocks_nothingLost() {
        Blockchain c = chain(THREE_TX);
        c.minePendingTransactions(ALICE);
        List<Transaction> sent = payments(5);
        for (Transaction tx : sent) assertTrue(c.addTransaction(tx));

        List<String> minedOrder = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Block b = c.minePendingTransactions("miner");
            assertTrue(b.getTransactions().size() <= 3);
            for (Transaction tx : b.getTransactions().subList(1, b.getTransactions().size())) {
                minedOrder.add(tx.getTransactionId());
            }
        }

        assertEquals(0, c.getPendingCount());
        assertEquals(sent.stream().map(Transaction::getTransactionId).toList(), minedOrder,
                "All five mined, in arrival order, two per block");
        assertTrue(c.isChainValid());
    }

    @Test
    @DisplayName("Mined blocks never exceed the byte limit, even with the nonce grown")
    void minedBlocks_respectByteLimit() {
        ChainParams p = params(1_400, 1_000);                   // room for ~3 payments
        Blockchain c = chain(p);
        c.minePendingTransactions(ALICE);
        for (Transaction tx : payments(10)) assertTrue(c.addTransaction(tx));

        Set<String> mined = new HashSet<>();
        while (c.getPendingCount() > 0) {
            Block b = c.minePendingTransactions("miner");
            assertTrue(b.sizeBytes() <= p.maxBlockBytes(), "Block " + b.getIndex() + " is " + b.sizeBytes());
            assertTrue(b.getTransactions().size() > 1, "Each block makes progress");
            b.getTransactions().forEach(tx -> mined.add(tx.getTransactionId()));
        }
        assertTrue(c.isChainValid());
        assertEquals(10.0 + 0.45, c.getBalance(BOB), 1e-9, "All ten payments arrived");
    }

    // ===== RESTORE =====

    @Test
    @DisplayName("A stored block over the limits is rejected on restore under stricter params")
    void limitsChecked_onRestore(@TempDir Path dir) {
        try (H2ChainStore store = H2ChainStore.openFile(dir)) {
            Blockchain lax = new Blockchain(store, params(64 * 1024, 1_000));
            lax.minePendingTransactions(ALICE);
            for (Transaction tx : payments(5)) assertTrue(lax.addTransaction(tx));
            lax.minePendingTransactions("miner");               // 6 transactions in block 2
        }
        try (H2ChainStore store = H2ChainStore.openFile(dir)) {
            Blockchain strict = new Blockchain(store, THREE_TX);
            Blockchain.RestoreReport report = strict.restoreFromStore();
            assertEquals(1, report.blocksRestored());
            assertEquals(1, report.blocksRejected());
        }
    }
}
