package com.blocksmith.core;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the difficulty numbers the API and dashboard show (Milestone
 * 17d): epochs, blocks until the next retarget, average block time.
 */
@DisplayName("Difficulty Report Tests")
class DifficultyReportTest {

    private static final long T0 = 1_750_000_000_000L;
    private static final BigInteger EASY = Target.fromLeadingZeros(1);
    private static final ChainParams PARAMS = new ChainParams(EASY, 1_000, 4, 4, Target.MAX);

    private static Blockchain chain() {
        return new Blockchain(NoOpChainStore.INSTANCE, PARAMS);
    }

    private static void mineAt(Blockchain c, long timestamp) {
        Block b = new Block(c.getChainSize(), List.of(), c.getLatestBlock().getHash(), timestamp);
        b.mineBlock(c.getNextTarget());
        assertTrue(c.addBlock(b));
    }

    @Test
    @DisplayName("A fresh chain: one epoch, 3 blocks until the first retarget, no average yet")
    void freshChain() {
        Blockchain c = chain();
        List<Blockchain.DifficultyEpoch> history = c.getDifficultyHistory();

        assertEquals(1, history.size());
        assertEquals(0, history.get(0).startHeight());
        assertEquals(EASY, history.get(0).target());
        assertNull(history.get(0).windowFirst());
        assertEquals(3, c.getBlocksUntilRetarget(), "Heights 1, 2, 3 use the initial target");
        assertNull(c.getAverageBlockTimeMs());
    }

    @Test
    @DisplayName("After a 2x-fast window: a new epoch with its measured window, countdown restarts")
    void afterFirstWindow() {
        Blockchain c = chain();
        for (int h = 1; h <= 3; h++) mineAt(c, T0 + h * 500);

        List<Blockchain.DifficultyEpoch> history = c.getDifficultyHistory();
        assertEquals(2, history.size(), "The next block (height 4) opens a new epoch");
        Blockchain.DifficultyEpoch epoch = history.get(1);
        assertEquals(4, epoch.startHeight());
        assertEquals(c.getNextTarget(), epoch.target());
        assertEquals(1, epoch.windowFirst());
        assertEquals(3, epoch.windowLast());
        assertEquals(1_000L, epoch.actualTimespanMs());
        assertEquals(2_000L, epoch.expectedTimespanMs());
        assertEquals(4, c.getBlocksUntilRetarget(), "Heights 4..7 share the new target");
        assertEquals(500L, c.getAverageBlockTimeMs());

        mineAt(c, T0 + 2_000);
        assertEquals(3, c.getBlocksUntilRetarget());
        assertEquals(2, c.getDifficultyHistory().size());
        assertEquals(epoch.target(), c.getTargetAt(4), "Reported target is the one actually used");
    }

    @Test
    @DisplayName("Difficulty display follows the target: 2x harder = +0.25")
    void difficultyDisplay_tracksTarget() {
        Blockchain c = chain();
        double before = Target.toDifficulty(c.getNextTarget());
        for (int h = 1; h <= 3; h++) mineAt(c, T0 + h * 500);
        double after = Target.toDifficulty(c.getNextTarget());
        assertEquals(before + 0.25, after, 1e-6);
    }
}
