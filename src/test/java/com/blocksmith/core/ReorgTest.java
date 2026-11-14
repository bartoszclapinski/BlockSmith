package com.blocksmith.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.contract.ContractStatus;
import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for reorganisation onto the chain with the most work (Milestone 21b).
 *
 * Every block here has the same target (no retarget), so "more work" means
 * "more blocks": the comparisons are easy to read.
 */
@DisplayName("Reorg Tests")
class ReorgTest {

    private static final ChainParams EASY = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    @TempDir
    Path dataDir;

    private static Blockchain chain() {
        return new Blockchain(NoOpChainStore.INSTANCE, EASY);
    }

    /**
     * Delivers all of {@code from}'s blocks to {@code to}, as a peer sync would,
     * and checks that {@code to} ends on the same tip (shared history, or a reorg).
     */
    private static void sync(Blockchain from, Blockchain to) {
        for (Block b : from.getChain()) if (b.getIndex() > 0) to.addBlock(b);
        assertEquals(from.getLatestBlock().getHash(), to.getLatestBlock().getHash(), "converged");
    }

    private static List<String> hashes(Blockchain c) {
        return c.getChain().stream().map(Block::getHash).toList();
    }

    private static List<String> pendingIds(Blockchain c) {
        return c.getPendingTransactions().stream().map(Transaction::getTransactionId).toList();
    }

    @Test
    @DisplayName("The planning probe: B switches to A's longer branch")
    void longerBranch_wins() {
        Blockchain a = chain();
        Blockchain b = chain();
        Block a1 = a.minePendingTransactions("A");
        Block b1 = b.minePendingTransactions("B");
        Block a2 = a.minePendingTransactions("A");
        Block a3 = a.minePendingTransactions("A");

        assertFalse(b.addBlock(a1), "Equal work: B keeps the block it saw first");
        assertEquals(b1.getHash(), b.getLatestBlock().getHash());
        assertTrue(b.addBlock(a2), "A's branch now has more work: B switches, a2 is on B's chain");
        assertTrue(b.addBlock(a3), "...and a3 simply extends it");

        assertEquals(hashes(a), hashes(b));
        assertEquals(1, b.getReorgCount());
        Blockchain.ReorgEvent r = b.getLastReorg();
        assertEquals(1, r.depth());
        assertEquals(0, r.forkHeight());
        assertEquals(b1.getHash(), r.oldTip());
        assertTrue(b.isSideBlock(b1.getHash()), "B's own block is kept: it could win again");
        assertEquals(0.0, b.getBalance("B"), 1e-9);
        assertEquals(150.0, b.getBalance("A"), 1e-9);
        assertTrue(b.isChainValid());
    }

    @Test
    @DisplayName("A 3-deep reorg: three of our blocks undone, four of theirs applied")
    void reorg_threeDeep() {
        Blockchain a = chain();
        Blockchain b = chain();
        for (int i = 0; i < 3; i++) b.minePendingTransactions("B");
        for (int i = 0; i < 4; i++) a.minePendingTransactions("A");

        List<Block> branch = a.getChain();
        for (int i = 1; i <= 3; i++) assertFalse(b.addBlock(branch.get(i)), "Not more work yet at #" + i);
        assertTrue(b.addBlock(branch.get(4)));

        assertEquals(hashes(a), hashes(b));
        assertEquals(3, b.getLastReorg().depth());
        assertEquals(4, b.getLastReorg().newHeight());
        assertEquals(3, b.getSideBlockCount(), "B's three undone blocks");
    }

    @Test
    @DisplayName("Transactions of undone blocks go back to the mempool and get mined again")
    void undoneTransactions_backInMempool() {
        Blockchain a = chain();
        Blockchain b = chain();
        a.minePendingTransactions("alice");
        sync(a, b);                                                    // both: Alice has 50

        Transaction pay = new Transaction("alice", "bob", 10);
        assertTrue(b.addTransaction(pay));
        b.minePendingTransactions("B");                                // only on B's branch
        assertEquals(10.0, b.getBalance("bob"), 1e-9);

        a.minePendingTransactions("A");
        a.minePendingTransactions("A");
        sync(a, b);                                                    // B reorganises onto A

        assertEquals(1, b.getReorgCount());
        assertEquals(0.0, b.getBalance("bob"), 1e-9, "The block that paid Bob is undone");
        assertEquals(List.of(pay.getTransactionId()), pendingIds(b), "...but the payment is not lost");
        assertEquals(1, b.getLastReorg().transactionsBack());
        b.minePendingTransactions("B");
        assertEquals(10.0, b.getBalance("bob"), 1e-9);
    }

    @Test
    @DisplayName("A payment double-spent on the winning branch is dropped, not resurrected")
    void doubleSpendOnWinningBranch_dropped() {
        Blockchain a = chain();
        Blockchain b = chain();
        a.minePendingTransactions("alice");
        sync(a, b);

        Transaction toBob = new Transaction("alice", "bob", 40);
        assertTrue(b.addTransaction(toBob));
        b.minePendingTransactions("B");

        assertTrue(a.addTransaction(new Transaction("alice", "carol", 45)));   // the same coins, elsewhere
        a.minePendingTransactions("A");
        a.minePendingTransactions("A");
        sync(a, b);

        assertEquals(45.0, b.getBalance("carol"), 1e-9);
        assertEquals(0.0, b.getBalance("bob"), 1e-9);
        assertEquals(0, b.getPendingCount(), "Alice has 5 left: the 40 to Bob cannot come back");
        assertTrue(b.isChainValid());
    }

    @Test
    @DisplayName("A longer branch with an invalid block is rejected; our chain stays")
    void invalidBranch_rejected_mainKept() {
        Blockchain a = chain();
        Blockchain b = chain();
        Block a1 = a.minePendingTransactions("A");
        Block b1 = b.minePendingTransactions("B");

        // a2 pays its miner a million: well built (passes the cheap checks), not allowed.
        Block a2 = new Block(2, List.of(new Transaction(BlockchainConfig.COINBASE_ADDRESS, "A", 1_000_000)),
                a1.getHash(), a1.getTimestamp() + 1);
        a2.mineBlock(Target.fromLeadingZeros(1));
        Block a3 = new Block(3, List.of(new Transaction(BlockchainConfig.COINBASE_ADDRESS, "A", 50)),
                a2.getHash(), a2.getTimestamp() + 1);
        a3.mineBlock(Target.fromLeadingZeros(1));

        assertFalse(b.addBlock(a1));
        assertFalse(b.addBlock(a2), "Replay finds the inflated coinbase");
        assertFalse(b.addBlock(a3));
        assertEquals(b1.getHash(), b.getLatestBlock().getHash());
        assertEquals(0, b.getReorgCount());
        assertFalse(b.isSideBlock(a2.getHash()), "The invalid block is forgotten");
        assertTrue(b.isSideBlock(a1.getHash()), "Its valid parent stays");
    }

    @Test
    @DisplayName("Equal work keeps the chain we saw first")
    void equalWork_keepsFirstSeen() {
        Blockchain a = chain();
        Blockchain b = chain();
        a.minePendingTransactions("A");
        a.minePendingTransactions("A");
        b.minePendingTransactions("B");
        Block b2 = b.minePendingTransactions("B");

        sync(a, chain());                                              // (sanity: a is a valid chain)
        for (Block blk : a.getChain().subList(1, 3)) assertFalse(b.addBlock(blk));
        assertEquals(b2.getHash(), b.getLatestBlock().getHash());
        assertEquals(0, b.getReorgCount());
    }

    @Test
    @DisplayName("A contract claimed and a sequence used only on the losing branch are open again")
    void contractClaim_andSequence_undone() {
        String secret = "open-sesame";
        String hashlock = "SHA256 PUSH " + HashUtil.applySha256(secret) + " EQUAL";
        Blockchain a = chain();
        Blockchain b = chain();
        a.minePendingTransactions("alice");
        Transaction deploy = a.deployContract("alice", hashlock, 15);
        a.minePendingTransactions("alice");
        sync(a, b);
        String contractId = deploy.getRecipient().substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());

        assertNotNull(b.claimContract(contractId, "bob", "PUSH " + secret));
        Transaction seq = new Transaction("alice", "carol", 5, 0.1, 42L);
        assertTrue(b.addTransaction(seq));
        b.minePendingTransactions("B");
        assertEquals(ContractStatus.CLAIMED, b.getContract(contractId).getStatus());

        a.minePendingTransactions("A");
        a.minePendingTransactions("A");
        sync(a, b);

        assertEquals(ContractStatus.OPEN, b.getContract(contractId).getStatus(), "The claim was undone");
        assertEquals(2, b.getPendingCount(), "Claim and payment are pending again");
        assertTrue(b.addTransaction(seq.withFee(0.5)), "Sequence 42 is free again: the bump replaces the pending one");
        b.minePendingTransactions("B");
        assertEquals(ContractStatus.CLAIMED, b.getContract(contractId).getStatus());
        assertEquals(5.0, b.getBalance("carol"), 1e-9);
        assertTrue(b.isChainValid());
    }

    @Test
    @DisplayName("A sequence confirmed on the winning branch stays used: no second version can follow")
    void sequenceConfirmedOnWinningBranch_staysUsed() {
        Blockchain a = chain();
        Blockchain b = chain();
        a.minePendingTransactions("alice");
        sync(a, b);

        Transaction v1 = new Transaction("alice", "bob", 5, 0.1, 7L);
        Transaction v2 = v1.withFee(0.5);
        assertTrue(a.addTransaction(v1));
        a.minePendingTransactions("A");                                  // v1 confirmed on A's branch
        a.minePendingTransactions("A");
        assertTrue(b.addTransaction(v2));                                // B only knows the bump
        b.minePendingTransactions("B");                                  // v2 confirmed on B's branch

        sync(a, b);                                                      // A's branch wins
        assertEquals(5.0, b.getBalance("bob"), 1e-9, "Bob paid once, by v1");
        assertEquals(0, b.getPendingCount(), "v2 cannot come back: sequence 7 is used by v1");
        assertFalse(b.addTransaction(v1.withFee(0.9)), "Nor can any later version");
        assertTrue(b.isChainValid());
    }

    @Test
    @DisplayName("After a reorg the store holds the new chain: a restart restores it")
    void reorg_survivesRestart() {
        Blockchain a = chain();
        for (int i = 0; i < 3; i++) a.minePendingTransactions("A");
        List<String> expected;
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain b = new Blockchain(store, EASY);
            b.minePendingTransactions("B");
            b.minePendingTransactions("B");
            sync(a, b);
            assertEquals(1, b.getReorgCount());
            expected = hashes(b);
        }
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain b = new Blockchain(store, EASY);
            assertEquals(3, b.restoreFromStore().blocksRestored());
            assertEquals(expected, hashes(b));
            assertEquals(150.0, b.getBalance("A"), 1e-9);
            assertEquals(0.0, b.getBalance("B"), 1e-9);
        }
    }

    @Test
    @DisplayName("truncateAbove drops higher blocks and their index rows")
    void store_truncateAbove() {
        Blockchain c = chain();
        c.minePendingTransactions("alice");
        assertTrue(c.addTransaction(new Transaction("alice", "bob", 1)));
        c.minePendingTransactions("m");
        Transaction high = c.getChain().get(2).getTransactions().get(1);
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            for (Block blk : c.getChain()) store.saveBlock(blk);
            assertTrue(store.findTransactionBlockIndex(high.getTransactionId()).isPresent());
            store.truncateAbove(1);
            assertEquals(2, store.loadBlocks().size());
            assertTrue(store.findTransactionBlockIndex(high.getTransactionId()).isEmpty());
        }
    }

    @Test
    @DisplayName("A fork deeper than MAX_REORG_DEPTH never wins, however long")
    void maxReorgDepth_respected() {
        Blockchain a = chain();
        Blockchain b = chain();
        for (int i = 0; i <= Blockchain.MAX_REORG_DEPTH; i++) b.minePendingTransactions("B");     // tip #101
        for (int i = 0; i <= Blockchain.MAX_REORG_DEPTH + 2; i++) a.minePendingTransactions("A"); // tip #103
        String tip = b.getLatestBlock().getHash();
        List<Block> blocks = new ArrayList<>(a.getChain());
        for (Block blk : blocks.subList(1, blocks.size())) assertFalse(b.addBlock(blk));
        assertEquals(tip, b.getLatestBlock().getHash());
        assertEquals(0, b.getReorgCount());
    }
}
