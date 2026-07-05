package com.blocksmith.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.HashUtil;
import com.blocksmith.util.SignatureUtil;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the stack-based script VM (Milestone 14a).
 *
 * The VM is consensus-critical: every node evaluates every script, so besides
 * the happy paths we exhaustively check the failure semantics - anything
 * malformed must evaluate to FALSE, and nothing may throw.
 */
@DisplayName("ScriptVM Tests")
class ScriptVMTest {

    private ScriptVM vm;

    @BeforeEach
    void setUp() {
        vm = new ScriptVM();
    }

    // ===== DATA + STACK OPCODES =====

    @Nested
    @DisplayName("Data and stack opcodes")
    class DataAndStack {

        @Test
        @DisplayName("PUSH leaves its data as the truthy result")
        void push_leavesDataOnStack() {
            assertTrue(vm.execute("PUSH hello", 0));
        }

        @Test
        @DisplayName("PUSH of literal 0 is falsy")
        void push_zeroIsFalsy() {
            assertFalse(vm.execute("PUSH 0", 0));
        }

        @Test
        @DisplayName("DUP duplicates the top element")
        void dup_duplicatesTop() {
            // [a, a] -> EQUAL -> [1]
            assertTrue(vm.execute("PUSH a DUP EQUAL", 0));
        }

        @Test
        @DisplayName("DROP removes the top element")
        void drop_removesTop() {
            // Push truthy, push falsy, drop the falsy -> truthy remains.
            assertTrue(vm.execute("PUSH yes PUSH 0 DROP", 0));
        }
    }

    // ===== CRYPTO + COMPARISON OPCODES =====

    @Nested
    @DisplayName("Crypto and comparison opcodes")
    class CryptoAndComparison {

        @Test
        @DisplayName("SHA256 hashes the top element")
        void sha256_hashesTop() {
            String hash = HashUtil.applySha256("secret");
            assertTrue(vm.execute("PUSH secret SHA256 PUSH " + hash + " EQUAL", 0));
        }

        @Test
        @DisplayName("EQUAL pushes 1 for equal values and 0 otherwise")
        void equal_comparesValues() {
            assertTrue(vm.execute("PUSH a PUSH a EQUAL", 0));
            assertFalse(vm.execute("PUSH a PUSH b EQUAL", 0));
        }

        @Test
        @DisplayName("EQUALVERIFY fails the script on mismatch")
        void equalVerify_failsOnMismatch() {
            // On match it consumes both values; the earlier push decides.
            assertTrue(vm.execute("PUSH yes PUSH a PUSH a EQUALVERIFY", 0));
            assertFalse(vm.execute("PUSH yes PUSH a PUSH b EQUALVERIFY", 0));
        }

        @Test
        @DisplayName("VERIFY fails the script on a falsy top")
        void verify_failsOnFalsy() {
            assertTrue(vm.execute("PUSH yes PUSH 1 VERIFY", 0));
            assertFalse(vm.execute("PUSH yes PUSH 0 VERIFY", 0));
        }
    }

    // ===== ARITHMETIC OPCODES =====

    @Nested
    @DisplayName("Arithmetic opcodes")
    class Arithmetic {

        @Test
        @DisplayName("ADD sums two numbers")
        void add_sums() {
            assertTrue(vm.execute("PUSH 2 PUSH 3 ADD PUSH 5 EQUAL", 0));
        }

        @Test
        @DisplayName("SUB subtracts top from second (a - b)")
        void sub_subtractsInOrder() {
            assertTrue(vm.execute("PUSH 7 PUSH 3 SUB PUSH 4 EQUAL", 0));
        }

        @Test
        @DisplayName("GREATERTHAN and LESSTHAN compare in push order (a op b)")
        void comparisons_respectOperandOrder() {
            assertTrue(vm.execute("PUSH 5 PUSH 3 GREATERTHAN", 0));
            assertFalse(vm.execute("PUSH 3 PUSH 5 GREATERTHAN", 0));
            assertTrue(vm.execute("PUSH 3 PUSH 5 LESSTHAN", 0));
            assertFalse(vm.execute("PUSH 5 PUSH 3 LESSTHAN", 0));
        }

        @Test
        @DisplayName("Arithmetic on non-numeric data fails the script")
        void arithmetic_nonNumeric_fails() {
            assertFalse(vm.execute("PUSH abc PUSH 1 ADD", 0));
        }
    }

    // ===== CONTRACT SCENARIOS =====

    @Nested
    @DisplayName("Contract scenarios")
    class Scenarios {

        @Test
        @DisplayName("Hashlock: true only with the correct preimage")
        void hashlockScript_trueOnlyWithCorrectPreimage() {
            String locking = "SHA256 PUSH " + HashUtil.applySha256("open-sesame") + " EQUAL";

            assertTrue(vm.execute("PUSH open-sesame", locking, 0));
            assertFalse(vm.execute("PUSH wrong-word", locking, 0));
            assertFalse(vm.execute("", locking, 0));
        }

        @Test
        @DisplayName("Timelock: false before height N, true at and after")
        void timelockScript_respectsBlockHeight() {
            String locking = "PUSH 5 CHECKLOCKTIME";

            assertFalse(vm.execute("", locking, 4));
            assertTrue(vm.execute("", locking, 5));
            assertTrue(vm.execute("", locking, 6));
        }

        @Test
        @DisplayName("Hashlock + timelock compose with VERIFY")
        void hashlockAndTimelock_compose() {
            String locking = "SHA256 PUSH " + HashUtil.applySha256("key") + " EQUALVERIFY "
                    + "PUSH 10 CHECKLOCKTIME";

            assertFalse(vm.execute("PUSH key", locking, 9));   // right key, too early
            assertTrue(vm.execute("PUSH key", locking, 10));   // right key, height reached
            assertFalse(vm.execute("PUSH bad", locking, 10));  // wrong key, height reached
        }
    }

    // ===== FAILURE SEMANTICS =====

    @Nested
    @DisplayName("Failure semantics - malformed scripts are false, never a crash")
    class FailureSemantics {

        @Test
        @DisplayName("Null, empty, and blank scripts are false")
        void nullEmptyBlank_areFalse() {
            assertFalse(vm.execute(null, 0));
            assertFalse(vm.execute("", 0));
            assertFalse(vm.execute("   ", 0));
            assertFalse(vm.execute(null, null, 0));
        }

        @Test
        @DisplayName("Unknown opcode fails the script")
        void unknownOpcode_fails() {
            assertFalse(vm.execute("PUSH a EXPLODE", 0));
        }

        @Test
        @DisplayName("Stack underflow fails the script for every opcode")
        void stackUnderflow_fails() {
            assertFalse(vm.execute("DUP", 0));
            assertFalse(vm.execute("DROP", 0));
            assertFalse(vm.execute("SHA256", 0));
            assertFalse(vm.execute("PUSH a EQUAL", 0));
            assertFalse(vm.execute("PUSH a EQUALVERIFY", 0));
            assertFalse(vm.execute("VERIFY", 0));
            assertFalse(vm.execute("PUSH 1 ADD", 0));
            assertFalse(vm.execute("CHECKLOCKTIME", 0));
        }

        @Test
        @DisplayName("PUSH without a data token fails the script")
        void trailingPush_fails() {
            assertFalse(vm.execute("PUSH", 0));
        }

        @Test
        @DisplayName("Oversized script, element, and stack are rejected")
        void sizeLimits_areEnforced() {
            // Script over MAX_SCRIPT_TOKENS tokens.
            StringBuilder longScript = new StringBuilder();
            for (int i = 0; i <= ScriptVM.MAX_SCRIPT_TOKENS; i++) longScript.append("DUP ");
            assertFalse(vm.execute(longScript.toString(), 0));

            // Data element over MAX_ELEMENT_LENGTH characters.
            assertFalse(vm.execute("PUSH " + "x".repeat(ScriptVM.MAX_ELEMENT_LENGTH + 1), 0));

            // Stack past MAX_STACK_SIZE via repeated DUP.
            StringBuilder deepStack = new StringBuilder("PUSH a ");
            for (int i = 0; i < ScriptVM.MAX_STACK_SIZE + 1; i++) deepStack.append("DUP ");
            assertFalse(vm.execute(deepStack.toString(), 0));
        }

        @Test
        @DisplayName("CHECKLOCKTIME with non-numeric height fails the script")
        void checkLockTime_nonNumeric_fails() {
            assertFalse(vm.execute("PUSH soon CHECKLOCKTIME", 0));
        }
    }

    // ===== SIGNATURE OPCODES (Sprint 15a) =====

    @Nested
    @DisplayName("Signature opcodes - CHECKSIG and CHECKMULTISIG")
    class SignatureOpcodes {

        /** The message a claim authorizes; the VM verifies signatures over it. */
        private final String sighash = "claim:contract-1:0xbob:100";

        @Test
        @DisplayName("CHECKSIG: true only for a valid signature over the sighash")
        void checkSig_trueForValidSignature_falseOtherwise() {
            KeyPair kp = newKeyPair();
            String pubKey = SignatureUtil.publicKeyToHex(kp.getPublic());
            String sig = SignatureUtil.sign(kp.getPrivate(), sighash);
            String locking = "PUSH " + pubKey + " CHECKSIG";

            // Valid signature over the sighash -> true.
            assertTrue(vm.execute("PUSH " + sig, locking, 0, sighash));

            // The same signature against a different sighash -> false (replay-safe).
            assertFalse(vm.execute("PUSH " + sig, locking, 0, "other-message"));

            // A signature from a different key -> false.
            String wrongSig = SignatureUtil.sign(newKeyPair().getPrivate(), sighash);
            assertFalse(vm.execute("PUSH " + wrongSig, locking, 0, sighash));

            // No sighash context (the 3-arg path) -> signature scripts never pass.
            assertFalse(vm.execute("PUSH " + sig, locking, 0));
        }

        @Test
        @DisplayName("CHECKMULTISIG: 2-of-3 valid with two correct signatures")
        void checkMultiSig_twoOfThree_valid() {
            KeyPair k1 = newKeyPair(), k2 = newKeyPair(), k3 = newKeyPair();
            String locking = multisigLock(2, k1.getPublic(), k2.getPublic(), k3.getPublic());

            String unlocking = "PUSH " + SignatureUtil.sign(k1.getPrivate(), sighash)
                             + " PUSH " + SignatureUtil.sign(k2.getPrivate(), sighash);

            assertTrue(vm.execute(unlocking, locking, 0, sighash));
        }

        @Test
        @DisplayName("CHECKMULTISIG: fewer signatures than the threshold fails")
        void checkMultiSig_insufficientSignatures_false() {
            KeyPair k1 = newKeyPair(), k2 = newKeyPair(), k3 = newKeyPair();
            String locking = multisigLock(2, k1.getPublic(), k2.getPublic(), k3.getPublic());

            // Only one signature supplied against a 2-of-3 lock.
            String unlocking = "PUSH " + SignatureUtil.sign(k1.getPrivate(), sighash);

            assertFalse(vm.execute(unlocking, locking, 0, sighash));
        }

        @Test
        @DisplayName("CHECKMULTISIG: a signature from a non-member key fails")
        void checkMultiSig_wrongKeySignature_false() {
            KeyPair k1 = newKeyPair(), k2 = newKeyPair(), k3 = newKeyPair();
            KeyPair outsider = newKeyPair();
            String locking = multisigLock(2, k1.getPublic(), k2.getPublic(), k3.getPublic());

            // One valid member signature plus one from a key not in the set.
            String unlocking = "PUSH " + SignatureUtil.sign(k1.getPrivate(), sighash)
                             + " PUSH " + SignatureUtil.sign(outsider.getPrivate(), sighash);

            assertFalse(vm.execute(unlocking, locking, 0, sighash));
        }

        @Test
        @DisplayName("CHECKMULTISIG: the same signature cannot satisfy two slots")
        void checkMultiSig_duplicateSignatureNotDoubleCounted() {
            KeyPair k1 = newKeyPair(), k2 = newKeyPair(), k3 = newKeyPair();
            String locking = multisigLock(2, k1.getPublic(), k2.getPublic(), k3.getPublic());

            // One member's signature presented twice - distinct-key rule rejects it.
            String sig1 = SignatureUtil.sign(k1.getPrivate(), sighash);
            String unlocking = "PUSH " + sig1 + " PUSH " + sig1;

            assertFalse(vm.execute(unlocking, locking, 0, sighash));
        }

        @Test
        @DisplayName("Malformed keys, signatures, and counts are false, never a crash")
        void malformedInput_isFalse_neverThrows() {
            // Garbage CHECKSIG operands (non-hex pubkey).
            assertFalse(vm.execute("PUSH deadbeef PUSH nothex CHECKSIG", 0, sighash));

            // Non-numeric N for CHECKMULTISIG.
            assertFalse(vm.execute("PUSH notanumber CHECKMULTISIG", 0, sighash));

            // Count mismatch: M (3) greater than N (2).
            KeyPair k1 = newKeyPair(), k2 = newKeyPair();
            String badCounts = "PUSH 3 PUSH " + SignatureUtil.publicKeyToHex(k1.getPublic())
                             + " PUSH " + SignatureUtil.publicKeyToHex(k2.getPublic())
                             + " PUSH 2 CHECKMULTISIG";
            assertFalse(vm.execute("", badCounts, 0, sighash));

            // Bare opcodes with an empty stack underflow to false.
            assertFalse(vm.execute("CHECKSIG", 0, sighash));
            assertFalse(vm.execute("CHECKMULTISIG", 0, sighash));
        }

        /** Builds an M-of-N lock: {@code M <pub1> ... <pubN> N CHECKMULTISIG}. */
        private String multisigLock(int m, PublicKey... keys) {
            StringBuilder sb = new StringBuilder("PUSH ").append(m);
            for (PublicKey key : keys) {
                sb.append(" PUSH ").append(SignatureUtil.publicKeyToHex(key));
            }
            sb.append(" PUSH ").append(keys.length).append(" CHECKMULTISIG");
            return sb.toString();
        }
    }

    /** Generates a fresh secp256r1 key pair for signature tests. */
    private KeyPair newKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"));
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key pair", e);
        }
    }
}
