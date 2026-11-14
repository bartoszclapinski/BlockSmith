package com.blocksmith.core;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for chain work and side branches (Milestone 21a). No switching yet:
 * competing blocks are kept, the main chain is unchanged.
 */
@DisplayName("Chain Work + Side Branch Tests")
class SideBranchTest {

    private static final BigInteger ONE_ZERO = Target.fromLeadingZeros(1);

    /** Easy PoW, no retarget: every block has the same target, so the same work. */
    private static final ChainParams EASY = new ChainParams(ONE_ZERO, 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    private static Blockchain chain() {
        return new Blockchain(NoOpChainStore.INSTANCE, EASY);
    }

    /** A competing block on {@code parent}, built by hand (as another miner would). */
    private static Block blockOn(Block parent, String miner) {
        Block b = new Block(parent.getIndex() + 1,
                List.of(new Transaction(BlockchainConfig.COINBASE_ADDRESS, miner, BlockchainConfig.MINING_REWARD)),
                parent.getHash(), Math.max(System.currentTimeMillis(), parent.getTimestamp() + 1));
        b.mineBlock(ONE_ZERO);
        return b;
    }

    @Test
    @DisplayName("Work = 2^256 / (target + 1): one hex zero = 16, four = 65,536; it adds up along the chain")
    void work_growsAsTargetShrinks() {
        assertEquals(BigInteger.valueOf(16), Target.work(Target.fromLeadingZeros(1)));
        assertEquals(BigInteger.valueOf(65_536), Target.work(Target.fromLeadingZeros(4)));
        assertEquals(BigInteger.ONE, Target.work(Target.MAX));
        assertTrue(Target.work(Target.fromLeadingZeros(4).shiftRight(1))
                .compareTo(Target.work(Target.fromLeadingZeros(4))) > 0, "Half the target, about twice the work");

        Blockchain c = chain();
        BigInteger genesis = c.getTotalWork();
        assertEquals(BigInteger.valueOf(16), genesis, "Genesis counts at the network's initial target");
        for (int i = 0; i < 5; i++) c.minePendingTransactions("m");
        assertEquals(genesis.add(BigInteger.valueOf(5 * 16)), c.getTotalWork());
    }

    @Test
    @DisplayName("A competing block is kept on a side branch, not dropped; our chain is unchanged")
    void competingBlock_keptAsSideBlock() {
        Blockchain a = chain();
        Blockchain b = chain();
        Block a1 = a.minePendingTransactions("A");
        Block b1 = b.minePendingTransactions("B");
        BigInteger work = b.getTotalWork();

        assertFalse(b.addBlock(a1), "Not appended: it does not extend B's tip");
        assertEquals(1, b.getSideBlockCount());
        assertTrue(b.isSideBlock(a1.getHash()));
        assertEquals(b1.getHash(), b.getLatestBlock().getHash());
        assertEquals(work, b.getTotalWork());
        assertFalse(b.addBlock(a1), "A second copy changes nothing");
        assertEquals(1, b.getSideBlockCount());

        // B keeps mining on its own tip as before.
        assertEquals(b1.getHash(), b.minePendingTransactions("B").getPreviousHash());
        assertTrue(b.isChainValid());
    }

    @Test
    @DisplayName("A side branch grows: a child of a side block is kept, in either arrival order")
    void sideBranchChild_kept() {
        Blockchain a = chain();
        Blockchain b = chain();
        Block a1 = a.minePendingTransactions("A");
        Block a2 = a.minePendingTransactions("A");
        Block a3 = a.minePendingTransactions("A");
        b.minePendingTransactions("B");

        assertFalse(b.addBlock(a1));
        assertFalse(b.addBlock(a2));
        assertEquals(2, b.getSideBlockCount(), "a2's parent a1 is a side block");

        Blockchain c = chain();
        c.minePendingTransactions("C");
        assertFalse(c.addBlock(a3));
        assertFalse(c.addBlock(a2));
        assertEquals(0, c.getSideBlockCount(), "Parents unknown so far: waiting as orphans");
        assertFalse(c.addBlock(a1));
        assertEquals(3, c.getSideBlockCount(), "a1 arrived: a2 and a3 followed it onto the side branch");
        assertEquals(2, c.getChainSize(), "Still C's own chain (no switching before 21b)");
    }

    @Test
    @DisplayName("Not kept: our own blocks, malformed blocks, wrong heights, forks deeper than the limit")
    void tooDeepOrJunk_notKept() {
        Blockchain b = chain();
        Block b1 = b.minePendingTransactions("B");
        assertFalse(b.addBlock(b1), "Already on our chain");

        // A peer's block edited in transit: the stored hash no longer matches the content.
        Block honest = blockOn(b.getChain().get(0), "X");
        String json = com.blocksmith.util.JsonUtil.GSON.toJson(honest)
                .replace("\"timestamp\":" + honest.getTimestamp(), "\"timestamp\":" + (honest.getTimestamp() + 1));
        Block forged = com.blocksmith.util.JsonUtil.GSON.fromJson(json, Block.class);
        assertNotEquals(forged.getHash(), forged.calculateHash());
        assertFalse(b.addBlock(forged));

        Block wrongHeight = new Block(5,
                List.of(new Transaction(BlockchainConfig.COINBASE_ADDRESS, "X", BlockchainConfig.MINING_REWARD)),
                b.getChain().get(0).getHash(), System.currentTimeMillis());
        wrongHeight.mineBlock(ONE_ZERO);
        assertFalse(b.addBlock(wrongHeight), "Parent is genesis, so it must be #1");
        assertEquals(0, b.getSideBlockCount());

        // A fork that leaves our chain more than MAX_REORG_DEPTH below the tip is ignored.
        Blockchain deep = chain();
        for (int i = 0; i <= Blockchain.MAX_REORG_DEPTH; i++) deep.minePendingTransactions("D");
        assertFalse(deep.addBlock(blockOn(deep.getChain().get(0), "X")), "Forks at genesis, 101 below the tip");
        assertEquals(0, deep.getSideBlockCount());
        assertFalse(deep.addBlock(blockOn(deep.getChain().get(1), "X")), "Forks at #1, exactly 100 below");
        assertEquals(1, deep.getSideBlockCount());
    }

    @Test
    @DisplayName("The side-branch store is bounded")
    void sideBlocks_bounded() {
        Blockchain b = chain();
        b.minePendingTransactions("B");
        Block genesis = b.getChain().get(0);
        for (int i = 0; i < Blockchain.MAX_SIDE_BLOCKS + 10; i++) b.addBlock(blockOn(genesis, "X" + i));
        assertEquals(Blockchain.MAX_SIDE_BLOCKS, b.getSideBlockCount());
        assertEquals(2, b.getChainSize());
    }
}
