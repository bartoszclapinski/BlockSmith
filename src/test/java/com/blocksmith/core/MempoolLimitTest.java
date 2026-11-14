package com.blocksmith.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.NoOpChainStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the mempool limit and eviction (Milestone 19a).
 */
@DisplayName("Mempool Limit Tests")
class MempoolLimitTest {

    private static final String ALICE = "alice";
    private static final String CAROL = "carol";
    private static final String BOB = "bob";
    private static final double INCREMENT = 0.0001;

    @TempDir
    Path dataDir;

    private static MempoolPolicy maxTxs(int n) {
        return new MempoolPolicy(65_536, n, INCREMENT);
    }

    /** A chain where Alice (and Carol) have 50 each. */
    private static Blockchain chain(MempoolPolicy policy) {
        Blockchain c = new Blockchain(NoOpChainStore.INSTANCE, ChainParams.DEFAULT, policy);
        c.minePendingTransactions(ALICE);
        c.minePendingTransactions(CAROL);
        return c;
    }

    /** Distinct amounts: identical txs in the same millisecond would share an id. */
    private static Transaction tx(String from, double amount, double fee) {
        return new Transaction(from, BOB, amount, fee);
    }

    private static List<String> pendingIds(Blockchain c) {
        List<String> ids = new ArrayList<>();
        for (Transaction t : c.getPendingTransactions()) ids.add(t.getTransactionId());
        return ids;
    }

    @Test
    @DisplayName("A full mempool refuses a transaction that does not pay more per byte")
    void fullPool_rejectsLowFeeRate() {
        Blockchain c = chain(maxTxs(3));
        for (int i = 0; i < 3; i++) assertTrue(c.addTransaction(tx(ALICE, 1 + i * 0.01, 0.1)));

        assertFalse(c.addTransaction(tx(ALICE, 2, 0)), "Zero fee into a full pool");
        assertFalse(c.addTransaction(tx(ALICE, 2.01, 0.1)), "Same fee: no better per byte");
        assertEquals(3, c.getPendingCount());
    }

    @Test
    @DisplayName("A better fee rate evicts the cheapest transaction and frees its sender's funds")
    void betterFeeRate_evictsCheapest() {
        Blockchain c = chain(maxTxs(3));
        Transaction cheap = tx(CAROL, 45, 0);                 // Carol: 5 left to spend
        assertTrue(c.addTransaction(cheap));
        assertTrue(c.addTransaction(tx(ALICE, 1, 0.2)));
        assertTrue(c.addTransaction(tx(ALICE, 1.01, 0.3)));
        assertFalse(c.addTransaction(tx(CAROL, 10, 1)), "Carol cannot afford it while 45 is pending");

        Transaction rich = tx(ALICE, 1.02, 1.0);
        assertTrue(c.addTransaction(rich));
        assertFalse(pendingIds(c).contains(cheap.getTransactionId()), "The cheapest was evicted");
        assertTrue(pendingIds(c).contains(rich.getTransactionId()));
        assertEquals(3, c.getPendingCount());

        // Carol's 45 is no longer promised, so she can spend 49 + 1 now.
        assertTrue(c.addTransaction(tx(CAROL, 49, 1)), "Evicting her tx released her funds");
    }

    @Test
    @DisplayName("Among equal fee rates the newest is evicted first")
    void equalRates_evictNewestFirst() {
        Blockchain c = chain(maxTxs(3));
        Transaction t1 = tx(ALICE, 1, 0);
        Transaction t2 = tx(ALICE, 1.01, 0);
        Transaction t3 = tx(ALICE, 1.02, 0);
        for (Transaction t : List.of(t1, t2, t3)) assertTrue(c.addTransaction(t));

        Transaction paying = tx(ALICE, 1.03, 0.5);
        assertTrue(c.addTransaction(paying));
        assertEquals(List.of(t1.getTransactionId(), t2.getTransactionId(), paying.getTransactionId()),
                pendingIds(c), "t3 arrived last among the zero-fee ones");
    }

    @Test
    @DisplayName("The byte limit holds; one well-paying transaction may evict several small ones")
    void byteLimit_and_countLimit() {
        Blockchain c = chain(new MempoolPolicy(1_024, 100, INCREMENT));
        Wallet wallet = new Wallet();
        c.minePendingTransactions(wallet.getAddress());            // fund it before filling the pool
        int added = 0;
        while (c.addTransaction(tx(ALICE, 1 + added * 0.01, 0))) added++;
        assertTrue(added >= 3, "About four ~250-byte txs fit in 1 KB, got " + added);
        assertTrue(c.getPendingBytes() <= 1_024);

        // A signed tx (~700 bytes) needs room of about three plain ones.
        Transaction big = new Transaction(wallet.getAddress(), BOB, 1, 0.5);
        wallet.signTransaction(big);
        assertTrue(big.sizeBytes() > 2 * 260);
        int before = c.getPendingCount();
        assertTrue(c.addTransaction(big));
        assertTrue(c.getPendingCount() <= before - 1, "Several were evicted for one big tx");
        assertTrue(c.getPendingBytes() <= 1_024);

        Blockchain counted = chain(maxTxs(2));
        assertTrue(counted.addTransaction(tx(ALICE, 1, 0)));
        assertTrue(counted.addTransaction(tx(ALICE, 1.01, 0)));
        assertFalse(counted.addTransaction(tx(ALICE, 1.02, 0)), "Count limit");
    }

    @Test
    @DisplayName("The minimum fee rate is 0 with room and lowest rate + increment when full")
    void minFeeRate_zeroWithRoom_risesWhenFull() {
        Blockchain c = chain(maxTxs(2));
        assertEquals(0.0, c.getMempoolMinFeeRate());
        Transaction low = tx(ALICE, 1, 0.1);
        assertTrue(c.addTransaction(low));
        assertEquals(0.0, c.getMempoolMinFeeRate(), "Still room for one more");
        assertTrue(c.addTransaction(tx(ALICE, 1.01, 0.2)));

        double min = c.getMempoolMinFeeRate();
        assertEquals(low.feeRate() + INCREMENT, min, 1e-12);

        // ~0.025 BSC more on ~250 bytes is about the increment: half of it is not enough...
        assertFalse(c.addTransaction(tx(ALICE, 1.02, 0.1 + 0.01)));
        // ...ten times it is.
        assertTrue(c.addTransaction(tx(ALICE, 1.03, 0.1 + 0.25)));
        assertTrue(c.getMempoolMinFeeRate() > min, "The bar rose with the cheaper tx gone");
    }

    @Test
    @DisplayName("Restoring a mempool larger than the policy keeps the best-paying transactions")
    void restore_trimsOversizeMempool() {
        List<Transaction> sent = new ArrayList<>();
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain c = new Blockchain(store, ChainParams.DEFAULT, maxTxs(10));
            c.minePendingTransactions(ALICE);
            for (int i = 0; i < 5; i++) {
                Transaction t = tx(ALICE, 1 + i * 0.01, i * 0.1);   // fees 0, 0.1 .. 0.4
                sent.add(t);
                assertTrue(c.addTransaction(t));
            }
        }

        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain c = new Blockchain(store, ChainParams.DEFAULT, maxTxs(3));
            assertEquals(3, c.restoreFromStore().pendingRestored());
            assertEquals(List.of(sent.get(2).getTransactionId(), sent.get(3).getTransactionId(),
                    sent.get(4).getTransactionId()), pendingIds(c));
            assertEquals(3, store.loadPending().size(), "Evicted txs are gone from the store too");
        }
    }

    @Test
    @DisplayName("The policy never rejects a block, even one bigger than our whole mempool")
    void policy_neverRejectsBlocks() {
        Blockchain miner = new Blockchain();                        // default policy
        Blockchain small = new Blockchain(NoOpChainStore.INSTANCE, ChainParams.DEFAULT, maxTxs(2));
        Block first = miner.minePendingTransactions(ALICE);
        assertTrue(small.addBlock(first));

        assertTrue(small.addTransaction(tx(ALICE, 7, 0)));
        assertTrue(small.addTransaction(tx(ALICE, 8, 0)));         // small's mempool is full

        for (int i = 0; i < 5; i++) assertTrue(miner.addTransaction(tx(ALICE, 1 + i * 0.01, 0)));
        Block big = miner.minePendingTransactions(BOB);
        assertEquals(6, big.getTransactions().size());
        assertTrue(small.addBlock(big), "5 txs in a block, 2-tx mempool: still valid");
        assertTrue(small.isChainValid());
    }
}
