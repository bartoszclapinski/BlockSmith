package com.blocksmith.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for mining outside the lock (Milestone 20b): template, work, submit.
 *
 * The stop condition handed to the miner runs in the unlocked work phase,
 * so the tests use it to act "in the middle of mining" deterministically:
 * pause the miner, or deliver a peer's block, at a known point.
 */
@DisplayName("Mining Outside the Lock Tests")
class MiningOutsideLockTest {

    private static final ChainParams EASY = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    private static Blockchain chain() {
        return new Blockchain(NoOpChainStore.INSTANCE, EASY);
    }

    @Test
    @DisplayName("Block.mineBlock asks the stop condition first and every N nonces, and gives up when told")
    void mineBlock_stopsWhenAsked() {
        Block block = new Block(1, List.of(new Transaction(BlockchainConfig.COINBASE_ADDRESS, "m", 50)), "prev", 1L);
        AtomicInteger asked = new AtomicInteger();
        boolean found = block.mineBlock(Target.fromLeadingZeros(12), () -> asked.incrementAndGet() >= 3);
        assertFalse(found, "12 zeros is out of reach: it must have stopped, not found");
        assertEquals(3, asked.get(), "Before starting, then every " + Block.STOP_CHECK_INTERVAL + " nonces");
        assertEquals(2L * Block.STOP_CHECK_INTERVAL - 1, block.getNonce());
    }

    @Test
    @DisplayName("While a block is being mined, reads and new transactions are not blocked")
    void slowMine_doesNotBlockReadsOrTransactions() throws Exception {
        Blockchain c = chain();
        c.minePendingTransactions("alice");                                    // Alice has 50
        CountDownLatch mining = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // The miner pauses inside its work phase until the main thread is done.
        CompletableFuture<Block> mined = CompletableFuture.supplyAsync(() -> c.minePendingTransactions("m", () -> {
            if (mining.getCount() > 0) {
                mining.countDown();
                try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { }
            }
            return false;
        }));
        assertTrue(mining.await(10, TimeUnit.SECONDS), "The miner reached its work phase");

        // With the lock held by the miner, these would hang until the timeout.
        Transaction tx = new Transaction("alice", "bob", 5);
        CompletableFuture<Boolean> work = CompletableFuture.supplyAsync(() -> {
            c.getChain();
            c.getBalance("alice");
            c.getMempoolMinFeeRate();
            return c.addTransaction(tx);
        });
        assertTrue(work.get(3, TimeUnit.SECONDS), "Read and add completed while the block was being mined");
        assertFalse(mined.isDone(), "...and the miner was still mining");

        release.countDown();
        Block block = mined.get(10, TimeUnit.SECONDS);
        assertEquals(2, block.getIndex());
        assertEquals(1, block.getTransactions().size(), "The template was built before tx arrived");
        assertEquals(1, c.getPendingCount(), "tx waits for the next block");
        assertTrue(c.minePendingTransactions("m").getTransactions().contains(tx));
    }

    @Test
    @DisplayName("A peer's block mid-mine makes the work stale: ours is rebuilt on top of it, nothing is lost")
    void peerBlockMidMine_staleTemplateNotAppended() {
        Blockchain us = chain();
        Blockchain peer = chain();
        assertTrue(peer.addBlock(us.minePendingTransactions("alice")));       // both: Alice has 50
        Transaction tx = new Transaction("alice", "bob", 7);
        assertTrue(us.addTransaction(tx));                                     // only we know tx
        Block peerBlock = peer.minePendingTransactions("peer");               // #2 from the peer

        AtomicInteger calls = new AtomicInteger();
        Block ours = us.minePendingTransactions("m", () -> {
            if (calls.incrementAndGet() == 1) assertTrue(us.addBlock(peerBlock), "Arrives while we mine");
            return false;
        });

        assertNotNull(ours);
        assertEquals(3, ours.getIndex(), "Rebuilt on the peer's #2");
        assertEquals(peerBlock.getHash(), ours.getPreviousHash());
        assertEquals(List.of(0, 1, 2, 3), us.getChain().stream().map(Block::getIndex).toList());
        assertTrue(ours.getTransactions().contains(tx), "tx survived the stale template");
        assertEquals(7.0, us.getBalance("bob"), 1e-9);
        assertTrue(us.isChainValid());
    }

    @Test
    @DisplayName("Stale work whose transaction the peer already mined: confirmed once, not twice")
    void staleWork_transactionsStayPending() {
        Blockchain us = chain();
        Blockchain peer = chain();
        assertTrue(peer.addBlock(us.minePendingTransactions("alice")));
        Transaction tx = new Transaction("alice", "bob", 7);
        assertTrue(us.addTransaction(tx));
        assertTrue(peer.addTransaction(tx));
        Block peerBlock = peer.minePendingTransactions("peer");               // contains tx
        assertTrue(peerBlock.getTransactions().contains(tx));

        AtomicInteger calls = new AtomicInteger();
        Block ours = us.minePendingTransactions("m", () -> {
            if (calls.incrementAndGet() == 1) us.addBlock(peerBlock);
            return false;
        });

        assertEquals(1, ours.getTransactions().size(), "The rebuilt template no longer has tx");
        assertEquals(7.0, us.getBalance("bob"), 1e-9, "Paid once");
        assertEquals(0, us.getPendingCount());
        assertTrue(us.isChainValid());
    }

    @Test
    @DisplayName("Cancelled mining returns null and changes nothing")
    void cancelledMine_returnsNull() {
        Blockchain c = chain();
        c.minePendingTransactions("alice");
        assertTrue(c.addTransaction(new Transaction("alice", "bob", 1)));
        assertNull(c.minePendingTransactions("m", () -> true));
        assertEquals(2, c.getChainSize());
        assertEquals(1, c.getPendingCount());
    }

    @Test
    @DisplayName("Concurrent mines and sends: valid chain, every admitted tx confirmed once, supply = coinbases")
    void concurrentMines_stillValidChain() throws Exception {
        Blockchain c = chain();
        for (int i = 0; i < 4; i++) c.minePendingTransactions("alice");      // Alice has 200
        List<Transaction> admitted = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch go = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < 6; t++) {
            final int id = t;
            Thread th = new Thread(() -> {
                try {
                    go.await();
                    for (int i = 0; i < 20; i++) {
                        if (id < 3) {
                            assertNotNull(c.minePendingTransactions("miner" + id));
                        } else {
                            Transaction tx = new Transaction("alice", "bob" + id, 1 + i * 0.001 + id * 0.0001);
                            if (c.addTransaction(tx)) admitted.add(tx);
                        }
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            threads.add(th);
            th.start();
        }
        go.countDown();
        for (Thread th : threads) th.join(60_000);
        assertNull(failure.get(), () -> "Failed: " + failure.get());
        while (c.getPendingCount() > 0) c.minePendingTransactions("sweeper");

        List<Block> blocks = c.getChain();
        for (int i = 0; i < blocks.size(); i++) assertEquals(i, blocks.get(i).getIndex());
        assertTrue(c.isChainValid());

        Map<String, Integer> seen = new HashMap<>();
        double coinbases = 0;
        for (Block b : blocks) {
            for (int i = 0; i < b.getTransactions().size(); i++) {
                Transaction tx = b.getTransactions().get(i);
                if (i == 0) coinbases += tx.getAmount();
                else seen.merge(tx.getTransactionId(), 1, Integer::sum);
            }
        }
        assertEquals(60, admitted.size(), "Alice could afford all 60");
        for (Transaction tx : admitted) assertEquals(1, seen.get(tx.getTransactionId()), "confirmed once");
        assertEquals(seen.size(), admitted.size(), "Nothing else got in");

        double supply = 0;
        java.util.Set<String> addresses = new java.util.HashSet<>(List.of("alice", "sweeper"));
        for (int t = 0; t < 6; t++) { addresses.add("miner" + t); addresses.add("bob" + t); }
        for (String a : addresses) supply += c.getBalance(a);
        assertEquals(coinbases, supply, 1e-6, "No coins created or destroyed outside coinbases");
    }
}
