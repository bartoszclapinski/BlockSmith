package com.blocksmith.util;

import java.math.BigInteger;

/**
 * The Proof-of-Work target: the largest hash value a block may have.
 *
 * THEORY: From "leading zeros" to a number
 *
 * A SHA-256 hash is a 256-bit number, written as 64 hex digits. "The hash
 * starts with 4 zeros" is the same statement as "the hash, read as a number,
 * is at most 0000ffff...ffff":
 *
 *   4 leading zeros  <=>  hash <= 16^60 - 1   (= 2^240 - 1)
 *
 * Writing the rule as a number makes it continuous. Leading zeros can only
 * move in steps of 16x (one more hex digit), but a target can be any value:
 * halve it and mining gets exactly 2x harder. That is what lets the network
 * tune difficulty smoothly (Sprint 17b); Bitcoin works the same way.
 *
 *   bigger target  = more hashes qualify = EASIER
 *   smaller target = fewer hashes qualify = HARDER
 *
 * For people we still show a "difficulty": the leading-zero equivalent
 * (4.00 for today's rule, 4.25 for a target half as big).
 *
 * Comparisons never throw: a malformed hash simply does not meet the target
 * (consensus code must not crash on bad input).
 */
public final class Target {

    /** Number of hex digits in a SHA-256 hash. */
    public static final int HEX_DIGITS = 64;

    /** The largest possible target (every hash qualifies). */
    public static final BigInteger MAX = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);

    private static final BigInteger SIXTEEN = BigInteger.valueOf(16);
    private static final double LN_16 = Math.log(16);

    private Target() {}

    /**
     * The target equivalent to "the hash starts with {@code zeros} zeros".
     *
     * @param zeros 0..64
     * @return {@code 16^(64 - zeros) - 1}
     */
    public static BigInteger fromLeadingZeros(int zeros) {
        if (zeros < 0 || zeros > HEX_DIGITS) {
            throw new IllegalArgumentException("Leading zeros must be 0..64, got " + zeros);
        }
        return SIXTEEN.pow(HEX_DIGITS - zeros).subtract(BigInteger.ONE);
    }

    /**
     * True if {@code hashHex}, read as a number, is at most {@code target}.
     * A malformed hash (wrong length, not hex) never meets any target.
     */
    public static boolean meets(String hashHex, BigInteger target) {
        return meetsHex(hashHex, toHex(target));
    }

    /**
     * Fast form for the mining loop: both values as 64-digit lowercase hex.
     *
     * For equal-length, zero-padded hex strings, comparing the text compares
     * the numbers ("0000a..." < "0000b..." < "0001..."), so no BigInteger
     * has to be built per attempt.
     */
    public static boolean meetsHex(String hashHex, String targetHex) {
        if (!isHash(hashHex)) return false;
        return hashHex.compareTo(targetHex) <= 0;
    }

    /**
     * The leading-zero equivalent of a target, for display: 64 - log16(target + 1).
     * {@code fromLeadingZeros(4)} gives 4.0; half that target gives 4.25.
     */
    public static double toDifficulty(BigInteger target) {
        double log16 = log(target.add(BigInteger.ONE)) / LN_16;
        double difficulty = HEX_DIGITS - log16;
        // Snap away floating-point dust so whole values print as whole numbers.
        double rounded = Math.round(difficulty * 1e9) / 1e9;
        return rounded;
    }

    /** The target as 64 lowercase hex digits (zero-padded), as used in the API. */
    public static String toHex(BigInteger target) {
        if (target.signum() < 0 || target.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Target out of range");
        }
        String hex = target.toString(16);
        return "0".repeat(HEX_DIGITS - hex.length()) + hex;
    }

    /** Parses a target written in hex (any case, padded or not). */
    public static BigInteger fromHex(String hex) {
        BigInteger value = new BigInteger(hex, 16);
        if (value.signum() < 0 || value.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("Target out of range: " + hex);
        }
        return value;
    }

    /** True for exactly 64 lowercase hex digits (the format HashUtil produces). */
    private static boolean isHash(String s) {
        if (s == null || s.length() != HEX_DIGITS) return false;
        for (int i = 0; i < HEX_DIGITS; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    /** Natural log of a positive BigInteger (works past the range of long). */
    private static double log(BigInteger value) {
        int excess = Math.max(0, value.bitLength() - 1022);
        double scaled = value.shiftRight(excess).doubleValue();
        return Math.log(scaled) + excess * Math.log(2);
    }
}
