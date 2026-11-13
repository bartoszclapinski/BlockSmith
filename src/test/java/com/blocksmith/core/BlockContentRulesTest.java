package com.blocksmith.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for block content rules (Milestone 18a, #281): a block is accepted
 * only if what its transactions DO is allowed, not just if it is well built.
 * Each attack is paired with the honest variant that must still pass.
 */
@DisplayName("Block Content Rules Tests")
class BlockContentRulesTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String MALLORY = "mallory";
    private static final double REWARD = BlockchainConfig.MINING_REWARD;

    /** Easy target (1 zero) so building forged blocks is instant. */
    private static final ChainParams EASY = new ChainParams(Target.fromLeadingZeros(1), 10_000, 10, 4, Target.MAX);

    private static Blockchain chain() {
        return new Blockchain(NoOpChainStore.INSTANCE, EASY);
    }

    private static Transaction coinbase(String to, double amount) {
        return new Transaction(BlockchainConfig.COINBASE_ADDRESS, to, amount);
    }

    /** A properly mined block on top of {@code c} with exactly these transactions. */
    private static Block blockOn(Blockchain c, List<Transaction> txs) {
        Block tip = c.getLatestBlock();
        long ts = Math.max(System.currentTimeMillis(), tip.getTimestamp() + 1);
        Block b = new Block(tip.getIndex() + 1, txs, tip.getHash(), ts);
        b.mineBlock(c.getNextTarget());
        return b;
    }

    /** Chain where Alice has 50 (one block: reward to Alice). */
    private static Blockchain aliceHas50() {
        Blockchain c = chain();
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(ALICE, REWARD)))));
        return c;
    }

    // ===== THE ORIGINAL ATTACK (#281) =====

    @Test
    @DisplayName("The forged block from #281 (1,000,000 coinbase + overspend) is rejected")
    void originalForgedBlock_rejected() {
        Blockchain c = chain();
        Block forged = blockOn(c, List.of(coinbase(MALLORY, 1_000_000), new Transaction(ALICE, MALLORY, 500)));

        assertFalse(c.addBlock(forged));
        assertEquals(0.0, c.getBalance(MALLORY), 1e-9);
        assertEquals(0.0, c.getBalance(ALICE), 1e-9);
        assertEquals(1, c.getChainSize());
    }

    // ===== COINBASE =====

    @Test
    @DisplayName("Coinbase above the reward is rejected; exactly the reward (or less) is fine")
    void inflatedCoinbase_rejected() {
        Blockchain c = chain();
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD + 0.01)))));
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD)))));
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, 1)))), "Paying yourself less is allowed");
    }

    @Test
    @DisplayName("Two coinbases, a coinbase not first, or transactions without a coinbase are rejected")
    void coinbasePlacement_rejected() {
        Blockchain c = aliceHas50();
        Transaction pay = new Transaction(ALICE, BOB, 5);

        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), coinbase(MALLORY, REWARD)))));
        assertFalse(c.addBlock(blockOn(c, List.of(pay, coinbase(MALLORY, REWARD)))));
        assertFalse(c.addBlock(blockOn(c, List.of(pay))));
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), pay))));
    }

    // ===== BALANCES =====

    @Test
    @DisplayName("Overspending is rejected, also when split across two transactions in one block")
    void overspend_rejected() {
        Blockchain c = aliceHas50();
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(BOB, REWARD), new Transaction(ALICE, BOB, 51)))));
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(BOB, REWARD),
                new Transaction(ALICE, BOB, 30), new Transaction(ALICE, BOB, 30)))), "30 + 30 > 50");
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(BOB, REWARD),
                new Transaction(ALICE, BOB, 30), new Transaction(ALICE, BOB, 20)))), "30 + 20 = 50 is fine");
        assertEquals(0.0, c.getBalance(ALICE), 1e-9);
    }

    @Test
    @DisplayName("Money received earlier in the same block can be spent later in it")
    void spendReceivedInSameBlock_allowed() {
        Blockchain c = aliceHas50();
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD),
                new Transaction(ALICE, BOB, 20), new Transaction(BOB, MALLORY, 20)))));
        assertEquals(0.0, c.getBalance(BOB), 1e-9);
        assertEquals(70.0, c.getBalance(MALLORY), 1e-9);
    }

    // ===== DUPLICATES =====

    @Test
    @DisplayName("A transaction twice in one block, or replayed from an earlier block, is rejected")
    void duplicateTransaction_rejected() {
        Blockchain c = aliceHas50();
        Transaction pay = new Transaction(ALICE, BOB, 10);

        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), pay, pay))), "Twice in one block");
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), pay))));
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), pay))), "Replayed: Bob paid twice");
        assertEquals(10.0, c.getBalance(BOB), 1e-9);
        assertFalse(c.addTransaction(pay), "Nor can it re-enter the mempool");
    }

    // ===== SIGNATURES =====

    @Test
    @DisplayName("A signature copied onto a different transaction is rejected")
    void foreignSignature_rejected() {
        Wallet wallet = new Wallet();
        Blockchain c = chain();
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(wallet.getAddress(), REWARD)))));

        Transaction signed = new Transaction(wallet.getAddress(), BOB, 1);
        wallet.signTransaction(signed);
        Transaction stolen = new Transaction(wallet.getAddress(), MALLORY, 49);
        stolen.setSignature(signed.getSignature());
        stolen.setSenderPublicKey(signed.getSenderPublicKey());

        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), stolen))));
        assertFalse(c.addTransaction(stolen));
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), signed))));
    }

    // ===== CONTRACTS =====

    @Test
    @DisplayName("A contract claim inside a peer block must pass the script VM")
    void invalidClaimInPeerBlock_rejected() {
        String secret = "open-sesame";
        Blockchain c = aliceHas50();
        Transaction deploy = new Transaction(ALICE, "CONTRACT:c1", 15);
        deploy.setLockingScript("SHA256 PUSH " + HashUtil.applySha256(secret) + " EQUAL");
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), deploy))));

        Transaction wrong = new Transaction("CONTRACT:c1", MALLORY, 15);
        wrong.setUnlockingScript("PUSH not-the-secret");
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), wrong))));

        Transaction right = new Transaction("CONTRACT:c1", BOB, 15);
        right.setUnlockingScript("PUSH " + secret);
        Transaction again = new Transaction("CONTRACT:c1", MALLORY, 15);
        again.setUnlockingScript("PUSH " + secret);
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), right, again))),
                "One contract cannot be claimed twice in a block");
        assertTrue(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD), right))));
        assertEquals(15.0, c.getBalance(BOB), 1e-9);
    }

    @Test
    @DisplayName("A deploy without a locking script inside a peer block is rejected")
    void deployWithoutScript_rejected() {
        Blockchain c = aliceHas50();
        assertFalse(c.addBlock(blockOn(c, List.of(coinbase(MALLORY, REWARD),
                new Transaction(ALICE, "CONTRACT:burn", 10)))));
    }

    // ===== MEMPOOL HYGIENE =====

    @Test
    @DisplayName("A pending tx made stale by a peer block is dropped when mining, not mined")
    void stalePendingTransaction_droppedWhenMining() {
        Blockchain peer = aliceHas50();
        Blockchain us = chain();
        assertTrue(us.addBlock(peer.getBlock(1)));

        assertTrue(us.addTransaction(new Transaction(ALICE, BOB, 50)), "Alice promises Bob 50 to us...");
        assertTrue(peer.addTransaction(new Transaction(ALICE, MALLORY, 50)), "...and Mallory 50 to the peer");
        Block peerBlock = peer.minePendingTransactions("peer-miner");
        assertTrue(us.addBlock(peerBlock), "The peer's block is valid on its own");

        Block ours = us.minePendingTransactions("us");
        assertEquals(1, ours.getTransactions().size(), "Only our coinbase: the stale tx was dropped");
        assertEquals(0, us.getPendingCount());
        assertEquals(0.0, us.getBalance(BOB), 1e-9);
        assertTrue(us.isChainValid());
    }

    // ===== WHOLE-CHAIN CHECKS =====

    @Test
    @DisplayName("isChainValid replays content rules: a forged block slipped into the list is caught")
    @SuppressWarnings("unchecked")
    void isChainValid_detectsForgedContent() throws Exception {
        Blockchain c = aliceHas50();
        assertTrue(c.isChainValid());

        Block forged = blockOn(c, List.of(coinbase(MALLORY, 1_000_000)));
        var field = Blockchain.class.getDeclaredField("chain");
        field.setAccessible(true);
        ((List<Block>) field.get(c)).add(forged);    // bypasses addBlock entirely

        assertFalse(c.isChainValid());
    }

    @Test
    @DisplayName("Restore rejects a forged block written straight into the database")
    void restore_rejectsForgedStoredBlock(@TempDir Path dir) {
        try (H2ChainStore store = H2ChainStore.openFile(dir)) {
            Blockchain c = new Blockchain(store, EASY);
            assertTrue(c.addBlock(blockOn(c, List.of(coinbase(ALICE, REWARD)))));
            store.saveBlock(blockOn(c, List.of(coinbase(MALLORY, 1_000_000))));   // not via addBlock
        }
        try (H2ChainStore store = H2ChainStore.openFile(dir)) {
            Blockchain restored = new Blockchain(store, EASY);
            Blockchain.RestoreReport report = restored.restoreFromStore();
            assertEquals(1, report.blocksRestored());
            assertEquals(1, report.blocksRejected());
            assertEquals(0.0, restored.getBalance(MALLORY), 1e-9);
        }
    }

    @Test
    @DisplayName("Honest mining with transfers and contracts still passes everywhere")
    void honestChain_stillValid() {
        Blockchain a = chain();
        a.minePendingTransactions(ALICE);
        assertTrue(a.addTransaction(new Transaction(ALICE, BOB, 10)));
        Transaction deploy = a.deployContract(ALICE, "SHA256 PUSH " + HashUtil.applySha256("s") + " EQUAL", 5);
        assertNotNull(deploy);
        a.minePendingTransactions(MALLORY);
        String id = deploy.getRecipient().substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
        assertNotNull(a.claimContract(id, BOB, "PUSH s"));
        a.minePendingTransactions(MALLORY);

        Blockchain b = chain();
        List<Block> blocks = new ArrayList<>(a.getChain());
        for (int i = 1; i < blocks.size(); i++) assertTrue(b.addBlock(blocks.get(i)), "Block " + i);
        assertTrue(a.isChainValid());
        assertTrue(b.isChainValid());
        assertEquals(a.getBalance(BOB), b.getBalance(BOB), 1e-9);
    }
}
