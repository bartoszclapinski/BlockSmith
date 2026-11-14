package com.blocksmith.core;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.blocksmith.util.JsonUtil;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for replace-by-fee (Milestone 19b): a (sender, sequence) pair
 * confirms at most once, and a pending version can be bumped.
 */
@DisplayName("Replace-by-Fee Tests")
class ReplaceByFeeTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CAROL = "carol";
    private static final String MINER = "miner";
    private static final long SEQ = 7L;

    @TempDir
    Path dataDir;

    private static final ChainParams EASY = new ChainParams(Target.fromLeadingZeros(1), 10_000, 10, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    private static Blockchain chain() {
        return chain(MempoolPolicy.DEFAULT);
    }

    /** Alice has 50. */
    private static Blockchain chain(MempoolPolicy policy) {
        Blockchain c = new Blockchain(NoOpChainStore.INSTANCE, EASY, policy);
        c.minePendingTransactions(ALICE);
        return c;
    }

    /** A peer's block on top of {@code c}: coinbase (reward + fees) plus {@code txs}. */
    private static Block blockOn(Blockchain c, Transaction... txs) {
        double fees = 0;
        for (Transaction tx : txs) fees += tx.getFee();
        List<Transaction> all = new java.util.ArrayList<>();
        all.add(new Transaction(BlockchainConfig.COINBASE_ADDRESS, MINER, BlockchainConfig.MINING_REWARD + fees));
        all.addAll(List.of(txs));
        Block tip = c.getLatestBlock();
        Block b = new Block(tip.getIndex() + 1, all, tip.getHash(),
                Math.max(System.currentTimeMillis(), tip.getTimestamp() + 1));
        b.mineBlock(c.getNextTarget());
        return b;
    }

    private static java.security.KeyPair keyPair() {
        try {
            java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("EC");
            g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
            return g.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> pendingIds(Blockchain c) {
        return c.getPendingTransactions().stream().map(Transaction::getTransactionId).toList();
    }

    // ===== ID AND SIGNATURE =====

    @Test
    @DisplayName("No sequence: same id and JSON as before, not replaceable, never conflicts")
    void noSequence_neverConflicts_idUnchanged() {
        Transaction plain = new Transaction(ALICE, BOB, 10, 0.5);
        assertNull(plain.getSequence());
        assertFalse(plain.isReplaceable());
        assertEquals(HashUtil.applySha256(ALICE + BOB + 10.0 + plain.getTimestamp() + "|fee:0.5"),
                plain.getTransactionId(), "The Sprint 18c id formula");
        assertFalse(JsonUtil.GSON.toJson(plain).contains("sequence"), "No key in the JSON");
        assertThrows(IllegalStateException.class, () -> plain.withFee(1));

        Blockchain c = chain();
        assertTrue(c.addTransaction(plain));
        assertTrue(c.addTransaction(new Transaction(ALICE, BOB, 10, 0.9)), "Same payment again = a second payment");
        assertEquals(2, c.getPendingCount());
    }

    @Test
    @DisplayName("The sequence is part of the id and the signature")
    void sequence_inIdAndSignature() {
        Wallet wallet = new Wallet();
        Transaction tx = new Transaction(wallet.getAddress(), BOB, 10, 0.5, SEQ);
        wallet.signTransaction(tx);
        assertTrue(tx.hasValidId());
        assertTrue(tx.verifySignature());
        assertNotEquals(new Transaction(wallet.getAddress(), BOB, 10, 0.5).getTransactionId(), tx.getTransactionId());

        String json = JsonUtil.GSON.toJson(tx).replace("\"sequence\":7", "\"sequence\":8");
        Transaction edited = JsonUtil.GSON.fromJson(json, Transaction.class);
        assertEquals(8L, edited.getSequence());
        assertFalse(edited.hasValidId());
        assertFalse(edited.verifySignature());
    }

    // ===== MEMPOOL: REPLACING =====

    @Test
    @DisplayName("A bump replaces the pending version; the miner gets the new fee, Alice pays once")
    void bump_replacesPending() {
        Blockchain c = chain();
        Transaction v1 = new Transaction(ALICE, BOB, 49, 0.5, SEQ);     // 49.5 of her 50
        assertTrue(c.addTransaction(v1));
        Transaction v2 = v1.withFee(1.0);                                // 50: only affordable with v1 released
        assertEquals(SEQ, v2.getSequence());
        assertNotEquals(v1.getTransactionId(), v2.getTransactionId());

        assertTrue(c.addTransaction(v2));
        assertEquals(List.of(v2.getTransactionId()), pendingIds(c));

        Block mined = c.minePendingTransactions(MINER);
        assertEquals(BlockchainConfig.MINING_REWARD + 1.0, mined.getTransactions().get(0).getAmount(), 1e-9);
        assertEquals(0.0, c.getBalance(ALICE), 1e-9);
        assertEquals(49.0, c.getBalance(BOB), 1e-9);
        assertTrue(c.isChainValid());
    }

    @Test
    @DisplayName("A bump that is not clearly higher is refused and the old version stays")
    void bumpTooSmall_rejected() {
        Blockchain c = chain();
        Transaction v1 = new Transaction(ALICE, BOB, 10, 0.5, SEQ);
        assertTrue(c.addTransaction(v1));

        assertFalse(c.addTransaction(v1.withFee(0.5)), "Same fee");
        assertFalse(c.addTransaction(v1.withFee(0.4)), "Lower fee");
        assertFalse(c.addTransaction(v1.withFee(0.501)), "Higher, but less than the increment per byte (~0.025)");
        assertEquals(List.of(v1.getTransactionId()), pendingIds(c));
        assertTrue(c.addTransaction(v1.withFee(0.6)));
    }

    @Test
    @DisplayName("A replacement may only change the fee")
    void changedAmountOrRecipient_rejected() {
        Blockchain c = chain();
        Transaction v1 = new Transaction(ALICE, BOB, 10, 0.5, SEQ);
        assertTrue(c.addTransaction(v1));

        assertFalse(c.addTransaction(new Transaction(ALICE, CAROL, 10, 2, SEQ)), "Other recipient");
        assertFalse(c.addTransaction(new Transaction(ALICE, BOB, 5, 2, SEQ)), "Other amount");
        assertTrue(c.addTransaction(new Transaction(ALICE, BOB, 10, 2, SEQ + 1)), "Another sequence: a new payment");
        assertEquals(2, c.getPendingCount());
    }

    @Test
    @DisplayName("A signed payment can only be bumped by the same key")
    void signedPayment_bumpNeedsSameKey() {
        Wallet wallet = new Wallet();
        Blockchain c = chain();
        c.minePendingTransactions(wallet.getAddress());
        Transaction v1 = new Transaction(wallet.getAddress(), BOB, 10, 0.5, SEQ);
        wallet.signTransaction(v1);
        assertTrue(c.addTransaction(v1));

        assertFalse(c.addTransaction(v1.withFee(2)), "Unsigned bump of a signed payment");
        // Someone else's key, with a valid signature over the bump's data.
        Transaction byOther = v1.withFee(2);
        java.security.KeyPair other = keyPair();
        byOther.setSenderPublicKey(other.getPublic());
        byOther.setSignature(com.blocksmith.util.SignatureUtil.hexToBytes(
                com.blocksmith.util.SignatureUtil.sign(other.getPrivate(), byOther.getSignatureData())));
        assertTrue(byOther.verifySignature(), "A real signature, just not the sender's");
        assertFalse(c.addTransaction(byOther), "Signed by someone else");

        Transaction mine = v1.withFee(2);
        wallet.signTransaction(mine);
        assertTrue(c.addTransaction(mine));
    }

    @Test
    @DisplayName("A bump fits into a full mempool by taking its old version's place")
    void bump_inFullMempool() {
        Blockchain c = chain(new MempoolPolicy(65_536, 2, 0.0001));
        Transaction v1 = new Transaction(ALICE, BOB, 1, 0.5, SEQ);
        assertTrue(c.addTransaction(v1));
        Transaction other = new Transaction(ALICE, BOB, 2, 0.6);
        assertTrue(c.addTransaction(other));

        Transaction v2 = v1.withFee(0.8);
        assertTrue(c.addTransaction(v2));
        assertEquals(List.of(other.getTransactionId(), v2.getTransactionId()), pendingIds(c),
                "Nothing else was evicted");
    }

    // ===== CONSENSUS =====

    @Test
    @DisplayName("A block holding both versions of one payment is rejected")
    void blockWithBothVersions_rejected() {
        Blockchain c = chain();
        Transaction v1 = new Transaction(ALICE, BOB, 10, 0.5, SEQ);
        Transaction v2 = v1.withFee(1);
        assertFalse(c.addBlock(blockOn(c, v1, v2)), "Bob would be paid twice");
        assertEquals(50.0, c.getBalance(ALICE), 1e-9);
        assertTrue(c.addBlock(blockOn(c, v2)));
    }

    @Test
    @DisplayName("Once one version is confirmed, the other can neither be added nor mined")
    void replacementOfConfirmed_rejected() {
        Blockchain c = chain();
        Transaction v1 = new Transaction(ALICE, BOB, 10, 0.5, SEQ);
        Transaction v2 = v1.withFee(1);
        assertTrue(c.addBlock(blockOn(c, v2)), "The bumped version confirms first");

        assertFalse(c.addTransaction(v1), "The old version is dead");
        assertFalse(c.addTransaction(v2.withFee(2)), "So is any later bump");
        assertFalse(c.addBlock(blockOn(c, v1)), "And no block may confirm it");
        assertEquals(10.0, c.getBalance(BOB), 1e-9);
        assertTrue(c.isChainValid());
    }

    @Test
    @DisplayName("A pending version is dropped when a peer's block confirms the other one")
    void pendingConflict_droppedWhenOtherVersionMined() {
        Blockchain c = chain();
        Transaction v1 = new Transaction(ALICE, BOB, 10, 0.5, SEQ);
        assertTrue(c.addTransaction(v1));

        assertTrue(c.addBlock(blockOn(c, v1.withFee(1))), "A miner who saw the bump first");
        assertEquals(0, c.getPendingCount(), "v1 can never be mined now");
        assertEquals(50 - 11.0, c.getBalance(ALICE), 1e-9);
    }

    @Test
    @DisplayName("Restore rebuilds the used sequences, so a dead version stays dead after a restart")
    void restore_rebuildsSequences() {
        Transaction v1 = new Transaction(ALICE, BOB, 10, 0.5, SEQ);
        Transaction pending = new Transaction(ALICE, BOB, 5, 0.2, SEQ + 1);
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain c = new Blockchain(store, EASY, MempoolPolicy.DEFAULT);
            c.minePendingTransactions(ALICE);
            assertTrue(c.addBlock(blockOn(c, v1.withFee(1))));
            assertTrue(c.addTransaction(pending));
        }

        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain c = new Blockchain(store, EASY, MempoolPolicy.DEFAULT);
            assertEquals(2, c.restoreFromStore().blocksRestored());
            assertFalse(c.addTransaction(v1), "Still dead after the restart");
            assertEquals(List.of(pending.getTransactionId()), pendingIds(c));
            assertTrue(c.addTransaction(pending.withFee(0.5)), "The restored pending one can still be bumped");
        }
    }
}
