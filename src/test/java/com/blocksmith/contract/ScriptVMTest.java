package com.blocksmith.contract;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.HashUtil;

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
}
