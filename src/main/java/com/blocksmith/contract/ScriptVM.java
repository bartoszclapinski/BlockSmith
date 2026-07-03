package com.blocksmith.contract;

import java.util.ArrayDeque;
import java.util.Deque;

import com.blocksmith.util.HashUtil;

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

    /** Canonical truthy/falsy values pushed by comparison opcodes. */
    private static final String TRUE = "1";
    private static final String FALSE = "0";

    /**
     * Runs an unlocking script concatenated with a locking script.
     *
     * @param unlockingScript The claimer's data (may be empty or null)
     * @param lockingScript The contract's lock
     * @param blockHeight Current chain height, for CHECKLOCKTIME
     * @return true if the combined script finishes with a truthy top of stack
     */
    public boolean execute(String unlockingScript, String lockingScript, long blockHeight) {
        String unlocking = unlockingScript == null ? "" : unlockingScript;
        String locking = lockingScript == null ? "" : lockingScript;
        return execute(unlocking + " " + locking, blockHeight);
    }

    /**
     * Runs a single script against a fresh stack.
     *
     * @param script Whitespace-separated tokens
     * @param blockHeight Current chain height, for CHECKLOCKTIME
     * @return true if execution finishes with a truthy top of stack
     */
    public boolean execute(String script, long blockHeight) {
        if (script == null || script.isBlank()) return false;

        String[] tokens = script.trim().split("\\s+");
        if (tokens.length > MAX_SCRIPT_TOKENS) return false;

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

                if (!apply(op, stack, blockHeight)) return false;
            }
        } catch (RuntimeException e) {
            // Defense in depth: no script may crash the node.
            return false;
        }

        return !stack.isEmpty() && isTruthy(stack.peek());
    }

    // ===== OPCODE EXECUTION =====

    /** Applies one non-PUSH opcode. Returns false to fail the script. */
    private boolean apply(ScriptOp op, Deque<String> stack, long blockHeight) {
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
            default:
                return false;
        }
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
