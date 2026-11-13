package com.blocksmith.core;

import java.math.BigInteger;

import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.Target;

/**
 * Consensus parameters: the rules every node on one network must share.
 *
 * THEORY: Why these are "consensus" values
 *
 * Two nodes with different retarget intervals would compute different
 * targets for the same block and disagree on whether it is valid: the
 * network would split. So these values are fixed per network, exactly like
 * Bitcoin's 10-minute block time and 2016-block retarget window.
 *
 * @param initialTarget        target for the first interval (genesis onwards)
 * @param targetBlockTimeMs    how far apart blocks should be, on average
 * @param retargetInterval     recompute the target every this many blocks
 * @param maxAdjustmentFactor  the target moves at most this factor per retarget
 * @param powLimit             the easiest target ever allowed (largest value)
 * @param maxFutureDriftMs     how far ahead of our clock a block's timestamp
 *                             may be (Sprint 17c)
 * @param maxBlockBytes        largest allowed block, in bytes of its JSON (Sprint 18b)
 * @param maxBlockTransactions most transactions per block, coinbase included (Sprint 18b)
 *
 * THEORY: Choosing the future-time limit
 *
 * The retarget formula reads timestamps, so a miner who may stamp a block
 * D milliseconds in the future can make a window look up to D longer than
 * it was, and the next target easier. D must be small next to a window:
 * Bitcoin allows 2 hours against a 2-week window (0.6%). Our default window
 * is about 100 s, so 2 hours (or even the 2 minutes first planned) would let a
 * miner more than halve the difficulty. 15 s keeps the effect small while
 * still tolerating ordinary clock differences between machines.
 */
public record ChainParams(
        BigInteger initialTarget,
        long targetBlockTimeMs,
        int retargetInterval,
        int maxAdjustmentFactor,
        BigInteger powLimit,
        long maxFutureDriftMs,
        int maxBlockBytes,
        int maxBlockTransactions) {

    /** Default future-time limit: 1.5 target block times. */
    public static final long DEFAULT_MAX_FUTURE_DRIFT_MS = 15_000;

    /**
     * Default block limits (Sprint 18b): 16 KB holds ~65 plain transactions
     * (~235 bytes each) or ~22 signed ones (~700 bytes); 50 transactions caps
     * the plain case. Small on purpose, so a full block can be seen in the
     * dashboard. Bitcoin's equivalent is ~4 MB of weight per block.
     */
    public static final int DEFAULT_MAX_BLOCK_BYTES = 16_384;
    public static final int DEFAULT_MAX_BLOCK_TRANSACTIONS = 50;

    /**
     * The network a real node runs: today's 4-zero target, one block every
     * 10 seconds, retarget every 10 blocks (so the effect shows up in the
     * dashboard within a couple of minutes), at most 4x per step, and never
     * easier than 3 leading zeros.
     */
    public static final ChainParams DEFAULT = new ChainParams(
            Target.fromLeadingZeros(BlockchainConfig.MINING_DIFFICULTY),
            10_000,
            10,
            4,
            Target.fromLeadingZeros(3),
            DEFAULT_MAX_FUTURE_DRIFT_MS,
            DEFAULT_MAX_BLOCK_BYTES,
            DEFAULT_MAX_BLOCK_TRANSACTIONS);

    /** Params with the default block limits (before Sprint 18b). */
    public ChainParams(BigInteger initialTarget, long targetBlockTimeMs, int retargetInterval,
                       int maxAdjustmentFactor, BigInteger powLimit, long maxFutureDriftMs) {
        this(initialTarget, targetBlockTimeMs, retargetInterval, maxAdjustmentFactor, powLimit,
                maxFutureDriftMs, DEFAULT_MAX_BLOCK_BYTES, DEFAULT_MAX_BLOCK_TRANSACTIONS);
    }

    /** Params with the default future-time limit (before Sprint 17c). */
    public ChainParams(BigInteger initialTarget, long targetBlockTimeMs, int retargetInterval,
                       int maxAdjustmentFactor, BigInteger powLimit) {
        this(initialTarget, targetBlockTimeMs, retargetInterval, maxAdjustmentFactor, powLimit,
                DEFAULT_MAX_FUTURE_DRIFT_MS);
    }

    public ChainParams {
        if (initialTarget == null || powLimit == null) throw new IllegalArgumentException("Targets are required");
        if (initialTarget.signum() <= 0 || initialTarget.compareTo(powLimit) > 0) {
            throw new IllegalArgumentException("initialTarget must be in (0, powLimit]");
        }
        if (powLimit.compareTo(Target.MAX) > 0) throw new IllegalArgumentException("powLimit above MAX");
        if (targetBlockTimeMs <= 0) throw new IllegalArgumentException("targetBlockTimeMs must be positive");
        if (retargetInterval < 3) throw new IllegalArgumentException("retargetInterval must be at least 3");
        if (maxAdjustmentFactor < 2) throw new IllegalArgumentException("maxAdjustmentFactor must be at least 2");
        if (maxFutureDriftMs < 0) throw new IllegalArgumentException("maxFutureDriftMs must not be negative");
        if (maxBlockBytes < 1_024) throw new IllegalArgumentException("maxBlockBytes must be at least 1024");
        if (maxBlockTransactions < 1) throw new IllegalArgumentException("maxBlockTransactions must be at least 1");
    }
}
