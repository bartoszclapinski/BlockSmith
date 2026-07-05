package com.blocksmith.contract;

import java.util.ArrayDeque;
import java.util.Deque;

import com.blocksmith.util.HashUtil;
import com.blocksmith.util.SignatureUtil;

/**
 * THEORY: A stack-based virtual machine for contract scripts.
 *
 * PROGRAMMABLE MONEY:
 * A plain transaction says "move X from A to B". A contract says "move X from
 * A to WHOEVER can make this script evaluate to true". The script is the lock;
 * the data a claimer provides is the key.
 *
 * EXECUTION MODEL (like early Bitcoin):
 * The claimer's UNLOCKING script (usually just data pushes) is concatenated
 * with the contract's LOCKING script and the whole thing runs once, left to
 * right, against a shared stack. The script succeeds if execution finishes
 * without a failure and the top of the stack is truthy.
 *
 *   Hashlock:  PUSH <preimage>  |  SHA256 PUSH <hash> EQUAL
 *   Stack:     [preimage] -> [sha256(preimage)] -> [sha256(preimage), hash] -> [1]
 *
 *   Timelock:  (no unlocking data)  |  PUSH <height> CHECKLOCKTIME
 *   Stack:     [height] -> [1] once the chain has reached <height>
 *
 * CONSENSUS SAFETY - THE VM NEVER THROWS:
 * A malformed script must be INVALID, never a crash: every node evaluates
 * every script, so an exception would be a denial-of-service vector. Stack
 * underflow, unknown opcodes, non-numeric arithmetic, and oversized scripts
 * all simply evaluate to false.
 *
 * TEXT FORM:
 * Scripts are whitespace-separated tokens, e.g. "SHA256 PUSH ab12 EQUAL".
 * PUSH consumes the next token as literal data. Text keeps scripts readable
 * and diff-friendly; a real chain would use a compact byte encoding.
 */
public class ScriptVM {

    /** Upper bound on script length (tokens) - oversized scripts are invalid. */
    public static final int MAX_SCRIPT_TOKENS = 200;

    /** Upper bound on stack depth during execution. */
    public static final int MAX_STACK_SIZE = 100;

    /** Upper bound on a single data element's length (characters). */
    public static final int MAX_ELEMENT_LENGTH = 520;

    /** Upper bound on the N of an M-of-N CHECKMULTISIG. */
    public static final int MAX_KEYS = 20;

    /** Canonical truthy/falsy values pushed by comparison opcodes. */
    private static final String TRUE = "1";
    private static final String FALSE = "0";

    /**
     * Runs an unlocking script concatenated with a locking script, with no
     * signature context (scripts that use CHECKSIG/CHECKMULTISIG will fail).
     *
     * @param unlockingScript The claimer's data (may be empty or null)
     * @param lockingScript The contract's lock
     * @param blockHeight Current chain height, for CHECKLOCKTIME
     * @return true if the combined script finishes with a truthy top of stack
     */
    public boolean execute(String unlockingScript, String lockingScript, long blockHeight) {
        return execute(unlockingScript, lockingScript, blockHeight, "");
    }

    /**
     * Runs an unlocking script concatenated with a locking script, providing a
     * sighash for signature opcodes.
     *
     * @param unlockingScript The claimer's data (may be empty or null)
     * @param lockingScript The contract's lock
     * @param blockHeight Current chain height, for CHECKLOCKTIME
     * @param sighash The message a spender authorizes, for CHECKSIG/CHECKMULTISIG
     * @return true if the combined script finishes with a truthy top of stack
     */
    public boolean execute(String unlockingScript, String lockingScript, long blockHeight, String sighash) {
        String unlocking = unlockingScript == null ? "" : unlockingScript;
        String locking = lockingScript == null ? "" : lockingScript;
        return execute(unlocking + " " + locking, blockHeight, sighash);
    }

    /**
     * Runs a single script against a fresh stack, with no signature context.
     *
     * @param script Whitespace-separated tokens
     * @param blockHeight Current chain height, for CHECKLOCKTIME
     * @return true if execution finishes with a truthy top of stack
     */
    public boolean execute(String script, long blockHeight) {
        return execute(script, blockHeight, "");
    }

    /**
     * Runs a single script against a fresh stack.
     *
     * @param script Whitespace-separated tokens
     * @param blockHeight Current chain height, for CHECKLOCKTIME
     * @param sighash The message a spender authorizes, for CHECKSIG/CHECKMULTISIG
     * @return true if execution finishes with a truthy top of stack
     */
    public boolean execute(String script, long blockHeight, String sighash) {
        if (script == null || script.isBlank()) return false;

        String[] tokens = script.trim().split("\\s+");
        if (tokens.length > MAX_SCRIPT_TOKENS) return false;

        String message = sighash == null ? "" : sighash;
        Deque<String> stack = new ArrayDeque<>();
        try {
            for (int i = 0; i < tokens.length; i++) {
                ScriptOp op = parseOp(tokens[i]);
                if (op == null) return false;

                if (op == ScriptOp.PUSH) {
                    // PUSH consumes the next token as literal data.
                    if (i + 1 >= tokens.length) return false;
                    String data = tokens[++i];
                    if (data.length() > MAX_ELEMENT_LENGTH) return false;
                    if (stack.size() >= MAX_STACK_SIZE) return false;
                    stack.push(data);
                    continue;
                }

                if (!apply(op, stack, blockHeight, message)) return false;
            }
        } catch (RuntimeException e) {
            // Defense in depth: no script may crash the node.
            return false;
        }

        return !stack.isEmpty() && isTruthy(stack.peek());
    }

    // ===== OPCODE EXECUTION =====

    /** Applies one non-PUSH opcode. Returns false to fail the script. */
    private boolean apply(ScriptOp op, Deque<String> stack, long blockHeight, String sighash) {
        switch (op) {
            case DUP: {
                if (stack.isEmpty() || stack.size() >= MAX_STACK_SIZE) return false;
                stack.push(stack.peek());
                return true;
            }
            case DROP: {
                if (stack.isEmpty()) return false;
                stack.pop();
                return true;
            }
            case SHA256: {
                if (stack.isEmpty()) return false;
                stack.push(HashUtil.applySha256(stack.pop()));
                return true;
            }
            case EQUAL: {
                if (stack.size() < 2) return false;
                String b = stack.pop();
                String a = stack.pop();
                stack.push(a.equals(b) ? TRUE : FALSE);
                return true;
            }
            case EQUALVERIFY: {
                if (stack.size() < 2) return false;
                return stack.pop().equals(stack.pop());
            }
            case VERIFY: {
                if (stack.isEmpty()) return false;
                return isTruthy(stack.pop());
            }
            case ADD:
            case SUB:
            case GREATERTHAN:
            case LESSTHAN: {
                if (stack.size() < 2) return false;
                Long b = parseNumber(stack.pop());
                Long a = parseNumber(stack.pop());
                if (a == null || b == null) return false;
                switch (op) {
                    case ADD: stack.push(String.valueOf(a + b)); break;
                    case SUB: stack.push(String.valueOf(a - b)); break;
                    case GREATERTHAN: stack.push(a > b ? TRUE : FALSE); break;
                    default: stack.push(a < b ? TRUE : FALSE); break;
                }
                return true;
            }
            case CHECKLOCKTIME: {
                if (stack.isEmpty()) return false;
                Long required = parseNumber(stack.pop());
                if (required == null) return false;
                stack.push(blockHeight >= required ? TRUE : FALSE);
                return true;
            }
            case CHECKSIG: {
                // Stack (top -> bottom): pubkey, signature
                if (stack.size() < 2) return false;
                String pubKey = stack.pop();
                String signature = stack.pop();
                stack.push(SignatureUtil.verify(pubKey, signature, sighash) ? TRUE : FALSE);
                return true;
            }
            case CHECKMULTISIG:
                return applyCheckMultiSig(stack, sighash);
            default:
                return false;
        }
    }

    /**
     * Applies CHECKMULTISIG. Stack layout, top to bottom:
     *
     *   N, pubN, ..., pub1, M, sigM, ..., sig1
     *
     * i.e. the script reads {@code <sigs> M <pubkeys> N CHECKMULTISIG}, exactly
     * as Bitcoin lays out a bare multisig. Pushes 1 iff all M signatures verify
     * against DISTINCT public keys scanned left to right (so the signatures must
     * be supplied in the same order as their keys, and a duplicate signature
     * cannot satisfy two slots). Any malformed count or shortfall pushes 0.
     */
    private boolean applyCheckMultiSig(Deque<String> stack, String sighash) {
        // N: number of public keys
        if (stack.isEmpty()) return false;
        Long n = parseNumber(stack.pop());
        if (n == null || n < 1 || n > MAX_KEYS) return false;
        int keyCount = n.intValue();

        if (stack.size() < keyCount) return false;
        String[] pubKeys = new String[keyCount];
        // pubN is on top; store so pubKeys[0] is the first key pushed (pub1).
        for (int k = keyCount - 1; k >= 0; k--) pubKeys[k] = stack.pop();

        // M: required number of signatures
        if (stack.isEmpty()) return false;
        Long m = parseNumber(stack.pop());
        if (m == null || m < 1 || m > keyCount) return false;
        int sigCount = m.intValue();

        if (stack.size() < sigCount) return false;
        String[] sigs = new String[sigCount];
        // sigM is on top; store so sigs[0] is the first signature pushed (sig1).
        for (int s = sigCount - 1; s >= 0; s--) sigs[s] = stack.pop();

        // Ordered matching: walk keys left to right, consuming one per matched
        // signature. Each key is used at most once (distinct keys), and a later
        // signature can only match a later key (Bitcoin-style ordering).
        int keyIndex = 0;
        int matched = 0;
        for (int s = 0; s < sigCount; s++) {
            while (keyIndex < keyCount
                    && !SignatureUtil.verify(pubKeys[keyIndex], sigs[s], sighash)) {
                keyIndex++;
            }
            if (keyIndex >= keyCount) break; // ran out of keys; this sig is unmatched
            matched++;
            keyIndex++;
        }

        stack.push(matched == sigCount ? TRUE : FALSE);
        return true;
    }

    // ===== HELPERS =====

    /** Parses a token into an opcode, or null if unknown. */
    private ScriptOp parseOp(String token) {
        try {
            return ScriptOp.valueOf(token);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Parses a stack value as a number, or null if it is not one. */
    private Long parseNumber(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A value is truthy unless it is empty or the literal 0. */
    private boolean isTruthy(String value) {
        return value != null && !value.isEmpty() && !value.equals(FALSE);
    }
}
