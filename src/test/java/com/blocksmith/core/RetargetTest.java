package com.blocksmith.core;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for difficulty retargeting (Milestone 17b). Blocks get explicit
 * timestamps so a test can play a fast or a slow network; easy params
 * (1 leading zero, 4-block window, 1 s per block) keep mining instant.
 */
@DisplayName("Retarget Tests")
class RetargetTest {

    private static final long T0 = 1_750_000_000_000L;   // mid-2025: after genesis, in the past
    private static final BigInteger EASY = Target.fromLeadingZeros(1);

    /** 4-block window, 1 s target, 4x clamp; powLimit chosen per test. */
    private static ChainParams params(BigInteger powLimit) {
        return new ChainParams(EASY, 1_000, 4, 4, powLimit);
    }

    private static Blockchain chain(ChainParams p) {
        return new Blockchain(NoOpChainStore.INSTANCE, p);
    }

    /** Builds (does not add) the next block with a given timestamp, mined at {@code target}. */
    private static Block blockAt(Blockchain c, int index, String prevHash, long timestamp, BigInteger target) {
        Block b = new Block(index, List.of(), prevHash, timestamp);
        b.mineBlock(target);
        return b;
    }

    /** Mines and appends the next block, honestly, at the given timestamp. */
    private static void mineAt(Blockchain c, long timestamp) {
        Block b = blockAt(c, c.getChainSize(), c.getLatestBlock().getHash(), timestamp, c.getNextTarget());
        assertTrue(c.addBlock(b), "Honest block at height " + b.getIndex());
    }

    /** Heights 1..3 spaced {@code spacingMs} apart: the first window (blocks 1..3). */
    private static void firstWindow(Blockchain c, long spacingMs) {
        for (int h = 1; h <= 3; h++) mineAt(c, T0 + h * spacingMs);
    }

    private static BigInteger scaled(BigInteger t, long num, long den) {
        return t.multiply(BigInteger.valueOf(num)).divide(BigInteger.valueOf(den));
    }

    // ===== DIRECTION =====

    @Test
    @DisplayName("Blocks on schedule: the target does not move (genesis is never measured)")
    void onSchedule_targetUnchanged() {
        Blockchain c = chain(params(Target.MAX));
        firstWindow(c, 1_000);
        assertEquals(EASY, c.getNextTarget(),
                "Genesis (fixed 2024 timestamp) must not make the window look years slow");
    }

    @Test
    @DisplayName("Blocks 2x too fast: the target halves (2x harder)")
    void fastInterval_targetShrinks() {
        Blockchain c = chain(params(Target.MAX));
        firstWindow(c, 500);                       // 2 gaps took 1 s instead of 2 s
        assertEquals(scaled(EASY, 1_000, 2_000), c.getNextTarget());
        assertTrue(Target.toDifficulty(c.getNextTarget()) > Target.toDifficulty(EASY));
    }

    @Test
    @DisplayName("Blocks 2x too slow: the target doubles (2x easier)")
    void slowInterval_targetGrows() {
        Blockchain c = chain(params(Target.MAX));
        firstWindow(c, 2_000);
        assertEquals(scaled(EASY, 4_000, 2_000), c.getNextTarget());
    }

    @Test
    @DisplayName("Within a window the target stays put; it only moves at boundaries")
    void target_onlyChangesAtBoundaries() {
        Blockchain c = chain(params(Target.MAX));
        firstWindow(c, 500);
        BigInteger afterFirst = c.getNextTarget();
        mineAt(c, T0 + 2_000);                     // height 4 (boundary block)
        assertEquals(afterFirst, c.getTargetAt(4));
        assertEquals(afterFirst, c.getNextTarget(), "Heights 5..7 reuse it");
    }

    // ===== GUARD RAILS =====

    @Test
    @DisplayName("One window moves the target at most 4x, either way")
    void adjustment_clampedAt4x() {
        Blockchain fast = chain(params(Target.MAX));
        firstWindow(fast, 1);                      // absurdly fast
        assertEquals(scaled(EASY, 500, 2_000), fast.getNextTarget(), "At most 4x harder");

        Blockchain slow = chain(params(Target.MAX));
        firstWindow(slow, 1_000_000);              // absurdly slow
        assertEquals(scaled(EASY, 8_000, 2_000), slow.getNextTarget(), "At most 4x easier");
    }

    @Test
    @DisplayName("The target never gets easier than powLimit")
    void target_neverEasierThanPowLimit() {
        Blockchain c = chain(params(EASY));        // powLimit = the initial target
        firstWindow(c, 5_000);
        assertEquals(EASY, c.getNextTarget());
    }

    @Test
    @DisplayName("Default params: ten quick blocks make the next one harder")
    void defaultParams_fastMining_raisesDifficulty() {
        Blockchain c = new Blockchain();
        BigInteger initial = c.getNextTarget();
        for (int i = 0; i < 10; i++) c.minePendingTransactions("miner");   // far faster than 10 s each
        assertTrue(c.getNextTarget().compareTo(initial) < 0, "Target shrank, mining got harder");
        assertTrue(c.isChainValid());
    }

    // ===== VALIDATION =====

    @Test
    @DisplayName("A peer block mined at an easier target than expected is rejected")
    void peerBlock_atEasierTarget_rejected() {
        Blockchain c = chain(params(Target.MAX));
        firstWindow(c, 250);                       // 4x too fast -> target / 4
        BigInteger expected = c.getNextTarget();

        Block weak = weakBlock(c, expected);
        assertFalse(c.addBlock(weak), "Meets the old target only");
        assertEquals(4, c.getChainSize());

        mineAt(c, T0 + 1_000);
        assertEquals(5, c.getChainSize(), "An honest block at the right target is accepted");
    }

    @Test
    @DisplayName("An orphan is parked on a loose check, but must meet the real target to attach")
    void orphan_checkedAgainstRealTargetWhenAttached() {
        Blockchain c = chain(params(Target.MAX));
        firstWindow(c, 250);
        BigInteger expected = c.getNextTarget();

        Block honest4 = blockAt(c, 4, c.getLatestBlock().getHash(), T0 + 1_000, expected);
        Block weak5 = weakBlockOn(honest4, 5, expected);

        assertFalse(c.addBlock(weak5), "Parent unknown: parked");
        assertEquals(1, c.getOrphanCount(), "Meets powLimit, so it is buffered");
        assertTrue(c.addBlock(honest4));
        assertEquals(5, c.getChainSize(), "#5 was too weak for its height and was not attached");
    }

    @Test
    @DisplayName("A peer and a restore replay compute the same targets")
    void restoreAndPeer_computeSameTargets() {
        ChainParams p = params(Target.MAX);
        try (H2ChainStore store = H2ChainStore.openInMemory("retarget-" + UUID.randomUUID())) {
            Blockchain a = new Blockchain(store, p);
            long t = T0;
            long[] spacing = {300, 300, 300, 300, 2_500, 2_500, 2_500, 2_500, 900, 900, 900, 900, 400};
            for (long s : spacing) mineAt(a, t += s);

            Blockchain peer = chain(p);
            for (int i = 1; i < a.getChainSize(); i++) assertTrue(peer.addBlock(a.getBlock(i)));

            Blockchain restored = new Blockchain(store, p);
            assertEquals(a.getChainSize() - 1, restored.restoreFromStore().blocksRestored());

            for (int h = 0; h < a.getChainSize(); h++) {
                assertEquals(a.getTargetAt(h), peer.getTargetAt(h), "peer, height " + h);
                assertEquals(a.getTargetAt(h), restored.getTargetAt(h), "restore, height " + h);
            }
            assertNotEquals(a.getTargetAt(4), a.getTargetAt(8), "Targets really moved");
            assertTrue(peer.isChainValid());
            assertTrue(restored.isChainValid());
        }
    }

    /** A block for the next height that meets the initial target but NOT {@code expected}. */
    private static Block weakBlock(Blockchain c, BigInteger expected) {
        for (long ts = T0 + 1_000; ; ts++) {
            Block b = blockAt(c, c.getChainSize(), c.getLatestBlock().getHash(), ts, EASY);
            if (!Target.meets(b.getHash(), expected)) return b;
        }
    }

    /** Same, on top of a given parent block. */
    private static Block weakBlockOn(Block parent, int index, BigInteger expected) {
        for (long ts = T0 + 2_000; ; ts++) {
            Block b = new Block(index, List.of(), parent.getHash(), ts);
            b.mineBlock(EASY);
            if (!Target.meets(b.getHash(), expected)) return b;
        }
    }
}
