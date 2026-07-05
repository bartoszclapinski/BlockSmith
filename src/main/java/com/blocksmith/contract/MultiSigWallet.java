package com.blocksmith.contract;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.blocksmith.util.HashUtil;
import com.blocksmith.util.SignatureUtil;

/**
 * THEORY: An M-of-N multisig is just a contract with a signature-checking lock.
 *
 * Instead of one key controlling funds, N members hold keys and any M of them
 * must sign to move the money - a 2-of-3 escrow, a shared treasury, a
 * backup-key setup. Bitcoin expresses this with a bare multisig script; so do
 * we, reusing the Sprint 14 contract machinery wholesale:
 *
 *   Locking script:  M <pub1> <pub2> ... <pubN> N CHECKMULTISIG
 *   (as VM tokens)   PUSH M PUSH pub1 ... PUSH pubN PUSH N CHECKMULTISIG
 *
 * To spend, a claimer presents M signatures over the claim's sighash
 * (see {@link Contract#claimSighash}) as the unlocking data:
 *
 *   Unlocking data:  PUSH sig1 PUSH sig2   (in member order)
 *
 * This wallet holds only PUBLIC keys - it builds the lock and identifies the
 * member set. Signing is done by whoever holds each member's private key.
 */
public class MultiSigWallet {

    /** Prefix for a multisig wallet's stable identity address. */
    public static final String ADDRESS_PREFIX = "MS:";

    /** Member public keys, hex-encoded (X.509), in signing order. */
    private final List<String> memberPublicKeys;

    /** Threshold M: the number of signatures required to spend. */
    private final int threshold;

    private MultiSigWallet(List<String> memberPublicKeys, int threshold) {
        int n = memberPublicKeys == null ? 0 : memberPublicKeys.size();
        if (n < 1 || n > ScriptVM.MAX_KEYS) {
            throw new IllegalArgumentException(
                    "Member count must be between 1 and " + ScriptVM.MAX_KEYS);
        }
        if (threshold < 1 || threshold > n) {
            throw new IllegalArgumentException(
                    "Threshold M must be between 1 and the member count N");
        }
        for (String key : memberPublicKeys) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Member public keys must be non-empty");
            }
        }
        this.memberPublicKeys = List.copyOf(memberPublicKeys);
        this.threshold = threshold;
    }

    /**
     * Builds a multisig wallet from member public keys.
     *
     * @param members   The N member public keys, in signing order
     * @param threshold M, the number of signatures required to spend
     */
    public static MultiSigWallet ofPublicKeys(List<PublicKey> members, int threshold) {
        List<String> hex = new ArrayList<>();
        if (members != null) {
            for (PublicKey key : members) {
                hex.add(key == null ? null : SignatureUtil.publicKeyToHex(key));
            }
        }
        return new MultiSigWallet(hex, threshold);
    }

    /**
     * Builds a multisig wallet from hex-encoded member public keys.
     *
     * @param memberPublicKeysHex The N member public keys as hex, in order
     * @param threshold           M, the number of signatures required
     */
    public static MultiSigWallet ofHex(List<String> memberPublicKeysHex, int threshold) {
        return new MultiSigWallet(memberPublicKeysHex, threshold);
    }

    /**
     * Builds the CHECKMULTISIG locking script guarding this wallet's funds:
     * {@code PUSH M PUSH pub1 ... PUSH pubN PUSH N CHECKMULTISIG}.
     */
    public String getLockingScript() {
        StringBuilder script = new StringBuilder("PUSH ").append(threshold);
        for (String key : memberPublicKeys) {
            script.append(" PUSH ").append(key);
        }
        script.append(" PUSH ").append(memberPublicKeys.size()).append(" CHECKMULTISIG");
        return script.toString();
    }

    /**
     * A stable identity address for this member set + threshold, derived from
     * the locking script. Two wallets with the same members and threshold share
     * an address; note the actual funds live in a per-deploy CONTRACT:&lt;id&gt;
     * address, not here.
     */
    public String getAddress() {
        return ADDRESS_PREFIX + HashUtil.applySha256(getLockingScript());
    }

    /** @return M, the number of signatures required to spend. */
    public int getThreshold() {
        return threshold;
    }

    /** @return N, the number of member keys. */
    public int getMemberCount() {
        return memberPublicKeys.size();
    }

    /** @return the member public keys, hex-encoded, in signing order. */
    public List<String> getMemberPublicKeys() {
        return Collections.unmodifiableList(memberPublicKeys);
    }
}
