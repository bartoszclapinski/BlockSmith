package com.blocksmith.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for fee estimation (Milestone 19c).
 */
@DisplayName("Fee Estimation Tests")
class FeeEstimationTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String MINER = "miner";
    private static final double INCREMENT = MempoolPolicy.DEFAULT.incrementalFeeRate();

    /** Easy PoW; {@code maxTxs} per block, coinbase included. */
    private static ChainParams params(int maxTxs) {
        return new ChainParams(Target.fromLeadingZeros(1), 10_000, 10, 4, Target.MAX,
                ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS, ChainParams.DEFAULT_MAX_BLOCK_BYTES, maxTxs);
    }

    /** Alice has 100. */
    private static Blockchain chain(int maxTxs, MempoolPolicy policy) {
        Blockchain c = new Blockchain(NoOpChainStore.INSTANCE, params(maxTxs), policy);
        c.minePendingTransactions(ALICE);
        c.minePendingTransactions(ALICE);
        return c;
    }

    private static Transaction send(Blockchain c, double amount, double fee) {
        Transaction tx = new Transaction(ALICE, BOB, amount, fee);
        assertTrue(c.addTransaction(tx));
        return tx;
    }

    @Test
    @DisplayName("Empty mempool and blocks with room: any fee will do")
    void emptyMempool_floor() {
        Blockchain c = chain(50, MempoolPolicy.DEFAULT);
        for (int target : new int[] {1, 3, 6}) assertEquals(0.0, c.estimateFeeRate(target));
        send(c, 1, 0.3);
        assertEquals(0.0, c.estimateFeeRate(1), "One tx fits in the next block");
        assertThrows(IllegalArgumentException.class, () -> c.estimateFeeRate(0));
    }

    @Test
    @DisplayName("When the mempool overflows the next block, the estimate beats its cheapest tx, and works")
    void mempoolOverOneBlock_mustBeatCutoff() {
        Blockchain c = chain(3, MempoolPolicy.DEFAULT);                // 2 txs per block
        send(c, 1, 0.1);
        send(c, 1.01, 0.2);
        Transaction third = send(c, 1.02, 0.3);
        send(c, 1.03, 0.4);

        assertEquals(third.feeRate() + INCREMENT, c.estimateFeeRate(1), 1e-12,
                "Next block holds 0.4 and 0.3: beat 0.3");
        assertEquals(0.0, c.estimateFeeRate(2), "All four fit in two blocks");

        // Paying the estimate really gets a tx into the next block.
        double rate = c.estimateFeeRate(1);
        Transaction probe = new Transaction(ALICE, BOB, 1.04, rate * 300);   // ~250 bytes, so a bit above the rate
        assertTrue(probe.feeRate() >= rate);
        assertTrue(c.addTransaction(probe));
        Block next = c.minePendingTransactions(MINER);
        assertTrue(next.getTransactions().stream()
                .anyMatch(t -> t.getTransactionId().equals(probe.getTransactionId())));
    }

    @Test
    @DisplayName("A longer target never costs more")
    void longerTarget_neverMoreExpensive() {
        Blockchain c = chain(3, MempoolPolicy.DEFAULT);
        for (int i = 0; i < 20; i++) send(c, 1 + i * 0.01, ((i * 7) % 20) * 0.05);

        double fast = c.estimateFeeRate(1);
        double normal = c.estimateFeeRate(3);
        double slow = c.estimateFeeRate(6);
        assertTrue(fast > 0);
        assertTrue(fast >= normal && normal >= slow, fast + " >= " + normal + " >= " + slow);
        assertTrue(slow > 0, "20 txs need 10 blocks, so even 6 is not enough for free");
    }

    @Test
    @DisplayName("Recent full blocks keep the estimate up after the mempool empties; blocks with room bring it down")
    void fullRecentBlocks_raiseEstimate() {
        Blockchain c = chain(3, MempoolPolicy.DEFAULT);
        Transaction cheapest = null;
        for (int b = 0; b < 6; b++) {
            Transaction low = send(c, 1 + b * 0.01, 0.2);
            send(c, 1.5 + b * 0.01, 0.3);
            if (cheapest == null || low.feeRate() > cheapest.feeRate()) cheapest = low;
            assertEquals(3, c.minePendingTransactions(MINER).getTransactions().size(), "A full block");
        }
        assertEquals(0, c.getPendingCount());

        assertEquals(cheapest.feeRate(), c.estimateFeeRate(1), 1e-12,
                "Would have made it into every one of the last 6 blocks");
        assertTrue(c.estimateFeeRate(6) > 0 && c.estimateFeeRate(6) <= c.estimateFeeRate(1));

        for (int b = 0; b < 6; b++) {
            send(c, 3 + b * 0.01, 0.2);
            c.minePendingTransactions(MINER);                               // one tx: room left
        }
        assertEquals(0.0, c.estimateFeeRate(1), "The busy period has passed");
    }

    @Test
    @DisplayName("A full mempool: the estimate is at least what it takes to get in")
    void fullMempool_atLeastMinFeeRate() {
        Blockchain c = chain(50, new MempoolPolicy(65_536, 2, INCREMENT));
        send(c, 1, 0.1);
        send(c, 1.01, 0.2);

        double min = c.getMempoolMinFeeRate();
        assertTrue(min > 0, "The mempool is full");
        assertEquals(min, c.estimateFeeRate(6), 1e-12, "Both fit in a block, but the pool itself is full");
    }
}
