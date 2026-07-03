package com.blocksmith.contract;

/**
 * THEORY: The opcode set of the BlockSmith script language.
 *
 * A script is a sequence of operations executed against a stack. This is how
 * Bitcoin locks every coin: the "program" that guards funds is a short list
 * of opcodes, and spending means providing data that makes it evaluate true.
 *
 * Our set is a small educational subset of Bitcoin Script:
 *
 * DATA:
 * - PUSH        Pushes the next token of the script onto the stack
 *
 * STACK:
 * - DUP         Duplicates the top element
 * - DROP        Removes the top element
 *
 * CRYPTO:
 * - SHA256      Replaces the top element with its SHA-256 hash
 *
 * COMPARISON / FLOW:
 * - EQUAL       Pops two, pushes 1 if equal else 0
 * - EQUALVERIFY Pops two, fails the script immediately if not equal
 * - VERIFY      Pops one, fails the script immediately if not truthy
 *
 * ARITHMETIC (integer):
 * - ADD         Pops b, a; pushes a + b
 * - SUB         Pops b, a; pushes a - b
 * - GREATERTHAN Pops b, a; pushes 1 if a > b else 0
 * - LESSTHAN    Pops b, a; pushes 1 if a < b else 0
 *
 * CONTRACT:
 * - CHECKLOCKTIME Pops a height N; pushes 1 if the current block height
 *                 is >= N else 0 (the timelock building block)
 */
public enum ScriptOp {
    PUSH,
    DUP,
    DROP,
    SHA256,
    EQUAL,
    EQUALVERIFY,
    VERIFY,
    ADD,
    SUB,
    GREATERTHAN,
    LESSTHAN,
    CHECKLOCKTIME
}
