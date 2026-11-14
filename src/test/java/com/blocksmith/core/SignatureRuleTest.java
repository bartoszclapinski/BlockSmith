package com.blocksmith.core;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.TestChains;
import com.blocksmith.contract.ContractStatus;
import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.blocksmith.util.JsonUtil;
import com.blocksmith.util.SignatureUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the signature rule (Milestone 22a): only the owner of an address
 * can spend from it. Real wallets, real network params ({@link ChainParams#DEFAULT}).
 */
@DisplayName("Signature Rule Tests")
class SignatureRuleTest {

    @TempDir
    Path dataDir;

    /** A real network: signatures required. */
    private static Blockchain chain() {
        return new Blockchain(NoOpChainStore.INSTANCE, ChainParams.DEFAULT);
    }

    /** A wallet's private key: only the test, playing the attacker, reaches for it. */
    private static PrivateKey keyOf(Wallet w) {
        try {
            Field f = Wallet.class.getDeclaredField("privateKey");
            f.setAccessible(true);
            return (PrivateKey) f.get(w);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code tx} signed with {@code signer}'s key, whoever the sender is (what Mallory does). */
    private static Transaction signWith(Wallet signer, Transaction tx) {
        tx.setSenderPublicKey(signer.getPublicKey());
        tx.setSignature(SignatureUtil.hexToBytes(SignatureUtil.sign(keyOf(signer), tx.getSignatureData())));
        return tx;
    }

    private static Transaction signed(Wallet from, String to, double amount) {
        Transaction tx = new Transaction(from.getAddress(), to, amount);
        from.signTransaction(tx);
        return tx;
    }

    /** A peer's block on {@code c}'s tip: coinbase + {@code tx}. */
    private static Block blockWith(Blockchain c, Transaction tx) {
        Block tip = c.getLatestBlock();
        Block b = new Block(tip.getIndex() + 1,
                List.of(new Transaction(BlockchainConfig.COINBASE_ADDRESS, "peer", BlockchainConfig.MINING_REWARD), tx),
                tip.getHash(), Math.max(System.currentTimeMillis(), tip.getTimestamp() + 1));
        b.mineBlock(c.getNextTarget());
        return b;
    }

    @Test
    @DisplayName("The address is derived from the key, and isSignedBySender checks both halves")
    void addressOf_and_isSignedBySender() {
        Wallet owner = new Wallet();
        Wallet other = new Wallet();
        assertEquals(owner.getAddress(), Wallet.addressOf(owner.getPublicKey()));
        assertTrue(signed(owner, "bob", 1).isSignedBySender());
        Transaction forged = signWith(other, new Transaction(owner.getAddress(), "bob", 1));
        assertTrue(forged.verifySignature(), "A real signature...");
        assertFalse(forged.isSignedBySender(), "...by the wrong key");
        assertFalse(new Transaction(owner.getAddress(), "bob", 1).isSignedBySender());
    }

    @Test
    @DisplayName("An unsigned transfer is refused, with the reason")
    void unsignedTransfer_rejected() {
        Wallet victim = new Wallet();
        Blockchain c = chain();
        c.minePendingTransactions(victim.getAddress());
        String why = c.submitTransaction(new Transaction(victim.getAddress(), "mallory", 10));
        assertNotNull(why);
        assertTrue(why.startsWith("unsigned"), why);
        assertEquals(50.0, c.getBalance(victim.getAddress()), 1e-9);
    }

    @Test
    @DisplayName("Signed with someone else's key: refused, even on a chain without the signature rule")
    void signedWithOtherKey_rejected_evenWithoutRule() {
        Wallet victim = new Wallet();
        Wallet mallory = new Wallet();
        for (Blockchain c : List.of(chain(), TestChains.unsigned())) {
            c.minePendingTransactions(victim.getAddress());
            Transaction forged = signWith(mallory, new Transaction(victim.getAddress(), mallory.getAddress(), 20));
            String why = c.submitTransaction(forged);
            assertEquals("signed with a key that does not belong to the sender", why);
        }
    }

    @Test
    @DisplayName("The owner's signed transfer is accepted and mined")
    void ownerSigned_accepted() {
        Wallet owner = new Wallet();
        Blockchain c = chain();
        c.minePendingTransactions(owner.getAddress());
        assertTrue(c.addTransaction(signed(owner, "bob", 10)));
        c.minePendingTransactions("miner");
        assertEquals(40.0, c.getBalance(owner.getAddress()), 1e-9);
        assertEquals(10.0, c.getBalance("bob"), 1e-9);
        assertTrue(c.isChainValid());
    }

    @Test
    @DisplayName("A peer's block with an unsigned or wrongly signed transfer is rejected")
    void peerBlockWithUnsignedTransfer_rejected() {
        Wallet victim = new Wallet();
        Wallet mallory = new Wallet();
        Blockchain c = chain();
        c.minePendingTransactions(victim.getAddress());

        assertFalse(c.addBlock(blockWith(c, new Transaction(victim.getAddress(), "mallory", 10))), "Unsigned");
        assertFalse(c.addBlock(blockWith(c,
                signWith(mallory, new Transaction(victim.getAddress(), "mallory", 10)))), "Mallory's key");
        assertTrue(c.addBlock(blockWith(c, signed(victim, "bob", 10))), "The owner's signature");
        assertEquals(40.0, c.getBalance(victim.getAddress()), 1e-9);
    }

    @Test
    @DisplayName("A contract deploy needs the funder's signature, which also covers the script; claims still work")
    void contractDeploy_needsFunderSignature_claimStillWorks() {
        String secret = "open-sesame";
        String hashlock = "SHA256 PUSH " + HashUtil.applySha256(secret) + " EQUAL";
        Wallet funder = new Wallet();
        Blockchain c = chain();
        c.minePendingTransactions(funder.getAddress());
        assertNull(c.deployContract(funder.getAddress(), hashlock, 15), "Unsigned deploy refused");

        Transaction deploy = Blockchain.buildDeploy(funder.getAddress(), hashlock, 15);
        String contractId = deploy.getRecipient().substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
        funder.signTransaction(deploy);

        // A relay swaps the lock for its own: the signature no longer matches.
        String json = JsonUtil.GSON.toJson(deploy).replace(HashUtil.applySha256(secret), HashUtil.applySha256("mine"));
        Transaction swapped = JsonUtil.GSON.fromJson(json, Transaction.class);
        assertFalse(swapped.verifySignature(), "The locking script is signed");

        assertTrue(c.addTransaction(deploy));
        c.minePendingTransactions("miner");
        assertNotNull(c.claimContract(contractId, "bob", "PUSH " + secret), "A claim is authorised by the script");
        c.minePendingTransactions("miner");
        assertEquals(ContractStatus.CLAIMED, c.getContract(contractId).getStatus());
        assertEquals(15.0, c.getBalance("bob"), 1e-9);
    }

    @Test
    @DisplayName("A fee bump is a new version of the payment: it must be signed again")
    void bump_mustBeResigned() {
        Wallet owner = new Wallet();
        Blockchain c = chain();
        c.minePendingTransactions(owner.getAddress());
        Transaction v1 = new Transaction(owner.getAddress(), "bob", 10, 0.1, 7L);
        owner.signTransaction(v1);
        assertTrue(c.addTransaction(v1));

        Transaction unsignedBump = v1.withFee(0.5);
        assertNotNull(c.submitTransaction(unsignedBump));
        Transaction bump = v1.withFee(0.5);
        owner.signTransaction(bump);
        assertTrue(c.addTransaction(bump));
        assertEquals(List.of(bump.getTransactionId()),
                c.getPendingTransactions().stream().map(Transaction::getTransactionId).toList());
    }

    @Test
    @DisplayName("A chain stored without the rule restores under it only up to the first unsigned transfer")
    void restoreOfUnsignedChain_stopsAtFirstUnsigned() {
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain old = new Blockchain(store, TestChains.PARAMS);   // same targets, no signature rule
            old.minePendingTransactions("alice");
            assertTrue(old.addTransaction(new Transaction("alice", "bob", 10)));
            old.minePendingTransactions("m");
            old.minePendingTransactions("m");
        }
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain now = new Blockchain(store, ChainParams.DEFAULT);
            Blockchain.RestoreReport report = now.restoreFromStore();
            assertEquals(1, report.blocksRestored(), "#1 (coinbase only) is fine");
            assertEquals(2, report.blocksRejected(), "#2 holds the unsigned transfer, #3 builds on it");
            assertEquals(2, now.getChainSize());
        }
    }
}
