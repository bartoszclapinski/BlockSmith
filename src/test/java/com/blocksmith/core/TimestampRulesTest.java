package com.blocksmith.core;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.storage.ChainStore;
import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for block timestamp rules (Milestone 17c): median-time-past and the
 * future-time limit, and how they bound the "time-warp" trick of faking
 * timestamps to make mining easier.
 */
@DisplayName("Timestamp Rules Tests")
class TimestampRulesTest {

    private static final long NOW = 1_760_000_000_000L;    // the node's (fixed) "now"
    private static final long SEC = 1_000;
    private static final BigInteger EASY = Target.fromLeadingZeros(1);

    /** 10 s blocks, retarget every 4, 4x clamp, 15 s future limit. */
    private static final ChainParams PARAMS = new ChainParams(EASY, 10 * SEC, 4, 4, Target.MAX, 15 * SEC);

    private static Clock clockAt(long millis) {
        return Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
    }

    private static Blockchain chain(ChainParams params, ChainStore store, long now) {
        return new Blockchain(store, params, clockAt(now));
    }

    private static Blockchain chain() {
        return chain(PARAMS, NoOpChainStore.INSTANCE, NOW);
    }

    /** The next block at {@code timestamp}, mined at the chain's expected target. */
    private static Block next(Blockchain c, long timestamp) {
        Block b = new Block(c.getChainSize(), List.of(), c.getLatestBlock().getHash(), timestamp);
        b.mineBlock(c.getNextTarget());
        return b;
    }

    private static void add(Blockchain c, long timestamp) {
        assertTrue(c.addBlock(next(c, timestamp)), "Block at " + timestamp);
    }

    // ===== MEDIAN-TIME-PAST =====

    @Test
    @DisplayName("A timestamp at or before the median of the last 11 is rejected; just after is fine")
    void timestamp_atOrBeforeMedian_rejected() {
        Blockchain c = chain();
        for (int i = 11; i >= 1; i--) add(c, NOW - i * 10 * SEC);    // heights 1..11, 10 s apart
        long median = c.medianTimePast(12);
        assertEquals(NOW - 60 * SEC, median, "Median of heights 1..11 is height 6");

        assertFalse(c.addBlock(next(c, median - 1)));
        assertFalse(c.addBlock(next(c, median)));
        assertTrue(c.addBlock(next(c, median + 1)),
                "Earlier than the tip (wobble) is allowed, as long as it is past the median");
    }

    // ===== FUTURE LIMIT =====

    @Test
    @DisplayName("Up to 15 s ahead of our clock is accepted; one millisecond more is not")
    void timestamp_futureLimit() {
        Blockchain c = chain();
        assertFalse(c.addBlock(next(c, NOW + 15 * SEC + 1)), "Too far in the future");
        assertTrue(c.addBlock(next(c, NOW + 15 * SEC)), "Slightly ahead is fine");
    }

    @Test
    @DisplayName("A far-future block is not even parked as an orphan")
    void farFutureOrphan_notBuffered() {
        Blockchain c = chain();
        Block orphan = new Block(5, List.of(), "0000unknown-parent", NOW + 3_600 * SEC);
        orphan.mineBlock(Target.MAX);
        assertFalse(c.addBlock(orphan));
        assertEquals(0, c.getOrphanCount());
    }

    // ===== TIME-WARP =====

    @Test
    @DisplayName("Time-warp: faking a slow window is rejected; the most a miner can fake is bounded")
    void timeWarp_boundedByFutureLimit() {
        // Honest window so far: blocks 1 and 2, 10 s apart, ending now.
        Blockchain honest = chain();
        add(honest, NOW - 20 * SEC);
        add(honest, NOW - 10 * SEC);

        // The attacker stamps block 3 an hour ahead to make blocks 1..3 look slow.
        assertFalse(honest.addBlock(next(honest, NOW + 3_600 * SEC)), "Rejected by the future limit");

        // The furthest they may go is now + 15 s: window = 35 s instead of 20 s.
        add(honest, NOW + 15 * SEC);
        BigInteger bounded = honest.getNextTarget();
        assertEquals(EASY.multiply(BigInteger.valueOf(35)).divide(BigInteger.valueOf(20)), bounded,
                "At most 1.75x easier");

        // Without the rule (a 10-year limit) the same trick hits the 4x clamp.
        ChainParams lax = new ChainParams(EASY, 10 * SEC, 4, 4, Target.MAX, 3_650L * 24 * 3_600 * SEC);
        Blockchain unprotected = chain(lax, NoOpChainStore.INSTANCE, NOW);
        add(unprotected, NOW - 20 * SEC);
        add(unprotected, NOW - 10 * SEC);
        add(unprotected, NOW + 3_600 * SEC);
        assertEquals(EASY.multiply(BigInteger.valueOf(4)), unprotected.getNextTarget(), "4x easier");
        assertTrue(bounded.compareTo(unprotected.getNextTarget()) < 0);
    }

    // ===== MINING AND RESTORE =====

    @Test
    @DisplayName("When peers' clocks run ahead, our mined block uses median + 1 and stays valid")
    void mining_whenPeersAhead_usesMedianPlusOne() {
        Blockchain c = chain();
        for (int i = 1; i <= 6; i++) add(c, NOW + i * SEC);        // peers a few seconds ahead
        long median = c.medianTimePast(7);
        assertTrue(median > NOW, "Precondition: the median is past our clock");

        Block mined = c.minePendingTransactions("miner");

        assertEquals(median + 1, mined.getTimestamp());
        assertTrue(c.isChainValid());
    }

    @Test
    @DisplayName("A stored chain stays valid later: the future rule uses the clock at validation time")
    void storedChain_staysValidLater() {
        try (H2ChainStore store = H2ChainStore.openInMemory("ts-" + UUID.randomUUID())) {
            Blockchain today = chain(PARAMS, store, NOW);
            add(today, NOW - 30 * SEC);
            add(today, NOW - 20 * SEC);
            add(today, NOW + 10 * SEC);                             // slightly ahead, accepted today

            Blockchain nextYear = chain(PARAMS, store, NOW + 365L * 24 * 3_600 * SEC);
            assertEquals(3, nextYear.restoreFromStore().blocksRestored());
            assertTrue(nextYear.isChainValid());
        }
    }
}
