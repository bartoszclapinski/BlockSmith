package com.blocksmith.util;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Block;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the numeric Proof-of-Work target (Milestone 17a). The key
 * property: the numeric rule is EXACTLY the old "N leading zeros" rule.
 */
@DisplayName("Target Tests")
class TargetTest {

    /** Real SHA-256 hashes plus hand-made edge cases around zero boundaries. */
    private static List<String> sampleHashes() {
        List<String> hashes = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < 3000; i++) hashes.add(HashUtil.applySha256("sample-" + random.nextLong()));
        for (int zeros = 0; zeros <= 8; zeros++) {
            String z = "0".repeat(zeros);
            hashes.add(z + "f".repeat(64 - zeros));                    // largest with `zeros` zeros
            if (zeros < 64) hashes.add(z + "1" + "0".repeat(63 - zeros)); // just above the next boundary
        }
        hashes.add("0".repeat(64));
        hashes.add("f".repeat(64));
        return hashes;
    }

    // ===== EQUIVALENCE =====

    @Test
    @DisplayName("meets(h, fromLeadingZeros(n)) is exactly h.startsWith(n zeros)")
    void fromLeadingZeros_matchesLeadingZeroRule() {
        for (int n = 0; n <= 8; n++) {
            BigInteger target = Target.fromLeadingZeros(n);
            String zeros = "0".repeat(n);
            for (String hash : sampleHashes()) {
                assertEquals(hash.startsWith(zeros), Target.meets(hash, target),
                        "n=" + n + " hash=" + hash);
            }
        }
    }

    @Test
    @DisplayName("Mining with the int rule and with the equivalent target finds the same nonce")
    void mineBlock_intAndTarget_sameNonce() {
        Block a = new Block(1, "same content", "0000prev", 1704067200000L);
        Block b = new Block(1, "same content", "0000prev", 1704067200000L);

        a.mineBlock(3);
        b.mineBlock(Target.fromLeadingZeros(3));

        assertEquals(a.getNonce(), b.getNonce());
        assertEquals(a.getHash(), b.getHash());
        assertTrue(Target.meets(b.getHash(), Target.fromLeadingZeros(3)));
    }

    @Test
    @DisplayName("The genesis block is unchanged by the refactor")
    void genesis_unchanged() {
        Block genesis = Block.createGenesisBlock();
        genesis.mineBlock(Target.fromLeadingZeros(4));
        assertEquals("0000c69641767962831fee39e53100854e20c6a25e6451cf6d0730732fa7b11a", genesis.getHash());
        assertEquals(33269, genesis.getNonce());
    }

    // ===== BOUNDARIES =====

    @Test
    @DisplayName("A hash equal to the target meets it; one more does not")
    void meets_boundary_equalPassesPlusOneFails() {
        BigInteger target = Target.fromHex("00000abc" + "0".repeat(56));
        String equal = Target.toHex(target);
        String plusOne = Target.toHex(target.add(BigInteger.ONE));
        String minusOne = Target.toHex(target.subtract(BigInteger.ONE));

        assertTrue(Target.meets(equal, target));
        assertTrue(Target.meets(minusOne, target));
        assertFalse(Target.meets(plusOne, target));
    }

    @Test
    @DisplayName("Malformed hashes never meet a target, and never throw")
    void meets_malformedHash_false() {
        BigInteger easiest = Target.MAX;
        assertFalse(Target.meets(null, easiest));
        assertFalse(Target.meets("", easiest));
        assertFalse(Target.meets("00", easiest));
        assertFalse(Target.meets("0".repeat(63) + "g", easiest));
        assertFalse(Target.meets("0".repeat(63) + "A", easiest), "Uppercase is not what HashUtil produces");
        assertFalse(Target.meets("0".repeat(65), easiest));
    }

    // ===== DISPLAY =====

    @Test
    @DisplayName("Difficulty is the leading-zero equivalent, including fractions")
    void toDifficulty_wholeAndFractional() {
        BigInteger four = Target.fromLeadingZeros(4);
        assertEquals(4.0, Target.toDifficulty(four), 1e-12);
        assertEquals(0.0, Target.toDifficulty(Target.MAX), 1e-12);

        // Halve the target (+1 handled by the formula) -> 2x harder -> +log16(2) = +0.25
        BigInteger half = four.add(BigInteger.ONE).shiftRight(1).subtract(BigInteger.ONE);
        assertEquals(4.25, Target.toDifficulty(half), 1e-9);
        BigInteger doubled = four.add(BigInteger.ONE).shiftLeft(1).subtract(BigInteger.ONE);
        assertEquals(3.75, Target.toDifficulty(doubled), 1e-9);
    }

    @Test
    @DisplayName("Hex form is 64 digits, zero-padded, and round-trips")
    void hexRoundTrip() {
        for (int n = 0; n <= 64; n += 8) {
            BigInteger t = Target.fromLeadingZeros(n);
            String hex = Target.toHex(t);
            assertEquals(64, hex.length());
            assertTrue(hex.startsWith("0".repeat(n)));
            assertEquals(t, Target.fromHex(hex));
        }
        assertEquals("0".repeat(64), Target.toHex(BigInteger.ZERO));
        assertThrows(IllegalArgumentException.class, () -> Target.toHex(Target.MAX.add(BigInteger.ONE)));
        assertThrows(IllegalArgumentException.class, () -> Target.fromLeadingZeros(65));
    }
}
