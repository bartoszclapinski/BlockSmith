package com.blocksmith;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.storage.NoOpChainStore;

/**
 * Chains for tests of rules OTHER than signatures (Sprint 22a).
 *
 * Since Sprint 22 every transfer on a real network must be signed by its
 * sender, which needs a key pair per address. Tests about fees, limits,
 * difficulty, reorgs... use readable names ("alice", "bob") instead, so they
 * run on the default network WITHOUT the signature rule, said explicitly
 * here (the role Bitcoin's regtest plays). The signature rule itself is
 * tested with real wallets and {@link ChainParams#DEFAULT}.
 */
public final class TestChains {

    /** The default network, minus the signature rule. */
    public static final ChainParams PARAMS = ChainParams.DEFAULT.withoutSignatureRule();

    private TestChains() {}

    /** An in-memory chain on {@link #PARAMS}. */
    public static Blockchain unsigned() {
        return new Blockchain(NoOpChainStore.INSTANCE, PARAMS);
    }
}
