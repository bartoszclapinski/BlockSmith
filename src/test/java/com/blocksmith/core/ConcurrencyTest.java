package com.blocksmith.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Blockchain thread safety (Milestone 20a). Each one reproduced a
 * real failure on the unsynchronized code (see the Sprint 20 plan).
 */
@DisplayName("Concurrency Tests")
class ConcurrencyTest {

    /** Easy PoW, and no retarget within these tests (the difficulty stays put). */
    private static final ChainParams EASY = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    private static Blockchain chain() {
        return new Blockchain(NoOpChainStore.INSTANCE, EASY);
    }

    /** Runs {@code work} on {@code n} threads released together; returns the first failure, if any. */
    private static Throwable runTogether(int n, java.util.function.IntConsumer work) throws InterruptedException {
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            final int id = t;
            Thread th = new Thread(() -> {
                try {
                    go.await();
                    work.accept(id);
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            threads.add(th);
            th.start();
        }
        go.countDown();
        for (Thread th : threads) th.join(60_000);
        return failure.get();
    }

    @Test
    @DisplayName("Reading the chain while blocks are appended never throws")
    void readWhileAppending_noException() throws Exception {
        Blockchain c = chain();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread miner = new Thread(() -> { for (int i = 0; i < 200; i++) c.minePendingTransactions("m"); });
        Thread reader = new Thread(() -> {
            try {
                while (miner.isAlive()) {
                    for (Block b : c.getChain()) b.getHash();               // the API's /api/blocks
                    for (Transaction t : c.getPendingTransactions()) t.getTransactionId();
                    c.getBalance("m");
                    c.getContracts();
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        miner.start();
        reader.start();
        miner.join();
        reader.join();
        assertNull(failure.get(), () -> "Reader failed: " + failure.get());
        assertEquals(201, c.getChainSize());
    }

    @Test
    @DisplayName("Four threads mining at once still build one valid chain, heights 0..n")
    void concurrentMining_validChain() throws Exception {
        Blockchain c = chain();
        Throwable failure = runTogether(4, id -> { for (int i = 0; i < 25; i++) c.minePendingTransactions("m"); });
        assertNull(failure);

        List<Block> blocks = c.getChain();
        assertEquals(101, blocks.size(), "Every one of the 100 mines added exactly one block");
        for (int i = 0; i < blocks.size(); i++) assertEquals(i, blocks.get(i).getIndex(), "height " + i);
        assertTrue(c.isChainValid());
        assertEquals(100 * BlockchainConfig.MINING_REWARD, c.getBalance("m"), 1e-9);
    }

    @Test
    @DisplayName("Four concurrent 40-coin spends of a 50 balance: exactly one is admitted")
    void concurrentOverspend_onlyOneAdmitted() throws Exception {
        for (int round = 0; round < 100; round++) {
            Blockchain c = chain();
            c.minePendingTransactions("alice");                                  // 50
            AtomicInteger admitted = new AtomicInteger();
            Throwable failure = runTogether(4, id -> {
                if (c.addTransaction(new Transaction("alice", "bob", 40 + id * 0.01))) admitted.incrementAndGet();
            });
            assertNull(failure);
            assertEquals(1, admitted.get(), "round " + round);
            assertEquals(1, c.getPendingCount(), "round " + round);
        }
    }

    @Test
    @DisplayName("The same block from two peers at once is appended once")
    void sameBlockFromTwoPeers_appendedOnce() throws Exception {
        Blockchain miner = chain();
        Blockchain node = chain();
        for (int round = 0; round < 50; round++) {
            Block block = miner.minePendingTransactions("m");
            AtomicInteger accepted = new AtomicInteger();
            Throwable failure = runTogether(2, id -> { if (node.addBlock(block)) accepted.incrementAndGet(); });
            assertNull(failure);
            assertEquals(1, accepted.get(), "round " + round);
            assertEquals(round + 2, node.getChainSize());
        }
        assertTrue(node.isChainValid());
        assertEquals(miner.getLatestBlock().getHash(), node.getLatestBlock().getHash());
    }

    @Test
    @DisplayName("Getters hand out snapshots: later changes do not show up in them, and they cannot be edited")
    void getters_returnSnapshots() {
        Blockchain c = chain();
        c.minePendingTransactions("alice");
        List<Block> blocks = c.getChain();
        assertTrue(c.addTransaction(new Transaction("alice", "bob", 1)));
        List<Transaction> pending = c.getPendingTransactions();

        c.minePendingTransactions("m");                                          // changes both
        assertEquals(2, blocks.size(), "The old snapshot still has 2 blocks");
        assertEquals(1, pending.size(), "The old snapshot still has the pending tx");
        assertEquals(3, c.getChainSize());
        assertEquals(0, c.getPendingCount());

        assertThrows(UnsupportedOperationException.class, () -> blocks.add(blocks.get(0)));
        assertThrows(UnsupportedOperationException.class, pending::clear);
    }
}
