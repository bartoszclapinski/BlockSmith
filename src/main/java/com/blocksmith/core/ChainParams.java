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
 */
public record ChainParams(
        BigInteger initialTarget,
        long targetBlockTimeMs,
        int retargetInterval,
        int maxAdjustmentFactor,
        BigInteger powLimit) {

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
            Target.fromLeadingZeros(3));

    public ChainParams {
        if (initialTarget == null || powLimit == null) throw new IllegalArgumentException("Targets are required");
        if (initialTarget.signum() <= 0 || initialTarget.compareTo(powLimit) > 0) {
            throw new IllegalArgumentException("initialTarget must be in (0, powLimit]");
        }
        if (powLimit.compareTo(Target.MAX) > 0) throw new IllegalArgumentException("powLimit above MAX");
        if (targetBlockTimeMs <= 0) throw new IllegalArgumentException("targetBlockTimeMs must be positive");
        if (retargetInterval < 3) throw new IllegalArgumentException("retargetInterval must be at least 3");
        if (maxAdjustmentFactor < 2) throw new IllegalArgumentException("maxAdjustmentFactor must be at least 2");
    }
}
