package com.blocksmith.core;

/**
 * Mempool limits (Sprint 19a): how many pending transactions this node keeps.
 *
 * THEORY: Policy vs consensus
 *
 * {@link ChainParams} are consensus: every node must agree on them or the
 * network splits. These values are only POLICY: each node chooses what it is
 * willing to hold in memory. Two nodes may keep different mempools, and a
 * block is never rejected because of them (a block can contain transactions
 * that never passed through our mempool at all). Bitcoin Core's equivalent
 * is {@code -maxmempool} (300 MB by default).
 *
 * THEORY: Eviction
 *
 * When the mempool is full, a new transaction only gets in if it pays a
 * better fee per byte than the worst one already waiting, by at least
 * {@code incrementalFeeRate}. The worst ones are then evicted. Without the
 * increment, two transactions with almost the same rate could push each
 * other out forever, each time costing the network bandwidth for nothing.
 *
 * @param maxBytes           total size of all pending transactions, in bytes of their JSON
 * @param maxTransactions    most pending transactions
 * @param incrementalFeeRate the step (BSC per byte) a new fee rate must beat
 *                           the evicted one by
 */
public record MempoolPolicy(int maxBytes, int maxTransactions, double incrementalFeeRate) {

    /**
     * Size of a typical plain transaction (~235-260 bytes of JSON), used to
     * tell whether the mempool still has room for one more.
     */
    public static final int TYPICAL_TX_BYTES = 256;

    /**
     * Default: 64 KB = four full blocks (Sprint 18b's 16 KB), 200
     * transactions = four blocks of 50. The increment of 0.0001 BSC per
     * byte is about 0.025 BSC on a typical transaction.
     */
    public static final MempoolPolicy DEFAULT = new MempoolPolicy(65_536, 200, 0.0001);

    public MempoolPolicy {
        if (maxBytes < 1_024) throw new IllegalArgumentException("maxBytes must be at least 1024");
        if (maxTransactions < 1) throw new IllegalArgumentException("maxTransactions must be at least 1");
        if (!(incrementalFeeRate >= 0) || Double.isInfinite(incrementalFeeRate)) {
            throw new IllegalArgumentException("incrementalFeeRate must be a non-negative number");
        }
    }
}
