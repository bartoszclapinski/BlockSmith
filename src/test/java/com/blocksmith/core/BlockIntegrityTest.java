package com.blocksmith.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.HashUtil;
import com.blocksmith.util.JsonUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that a block's transactions must match the Merkle root its hash
 * commits to (#260). Before the fix, a block with an edited transaction kept
 * its old ids and Merkle root, so its hash and Proof-of-Work still checked out.
 * Contract scripts are covered too (#264).
 */
@DisplayName("Block Integrity Tests")
class BlockIntegrityTest {

    private static final String ALICE = "alice-address";
    private static final String BOB = "bob-address";
    private static final String SECRET = "open-sesame";
    private static final String SECRET_HASH = HashUtil.applySha256(SECRET);
    private static final String HASHLOCK = "SHA256 PUSH " + SECRET_HASH + " EQUAL";

    /** Peer chain: #1 reward to Alice, #2 hashlock deploy, #3 Bob's claim. */
    private static Blockchain contractChain() {
        Blockchain peer = new Blockchain();
        peer.minePendingTransactions(ALICE);
        Transaction deploy = peer.deployContract(ALICE, HASHLOCK, 15);
        assertNotNull(deploy);
        peer.minePendingTransactions(ALICE);
        String contractId = deploy.getRecipient().substring("CONTRACT:".length());
        assertNotNull(peer.claimContract(contractId, BOB, "PUSH " + SECRET));
        peer.minePendingTransactions(ALICE);
        return peer;
    }

    /** A wire copy of {@code block} with one piece of its JSON replaced. */
    private static Block forged(Block block, String from, String to) {
        String json = JsonUtil.GSON.toJson(block);
        assertTrue(json.contains(from), "Precondition: '" + from + "' is in the block");
        return JsonUtil.GSON.fromJson(json.replace(from, to), Block.class);
    }

    // ===== CONTRACT SCRIPTS (#264) =====

    @Test
    @DisplayName("A plain transfer keeps exactly the id it had before scripts joined the id")
    void plainTransfer_idUnchanged() {
        Transaction tx = new Transaction(ALICE, BOB, 10);
        String legacyId = HashUtil.applySha256(ALICE + BOB + 10.0 + tx.getTimestamp());
        assertEquals(legacyId, tx.getTransactionId(),
                "Existing chains of ordinary transfers must stay valid");
    }

    @Test
    @DisplayName("Setting a script changes the id, and the new id is self-consistent")
    void settingScript_changesId() {
        Transaction tx = new Transaction(ALICE, "CONTRACT:abc", 5);
        String before = tx.getTransactionId();
        tx.setLockingScript(HASHLOCK);

        assertNotEquals(before, tx.getTransactionId());
        assertTrue(tx.hasValidId());
    }

    @Test
    @DisplayName("Swapping a deploy's locking script inside a block is detected")
    void editedLockingScript_blockRejected() {
        Blockchain peer = contractChain();
        String attackerHash = HashUtil.applySha256("attacker-knows-this");
        Block forgedDeploy = forged(peer.getBlock(2), SECRET_HASH, attackerHash);

        assertEquals(peer.getBlock(2).getHash(), forgedDeploy.calculateHash(),
                "The block hash alone does not see the edit");
        assertFalse(forgedDeploy.hasValidMerkleRoot(), "...but the tx id now does");

        Blockchain us = new Blockchain();
        assertTrue(us.addBlock(peer.getBlock(1)));
        assertFalse(us.addBlock(forgedDeploy), "Forged deploy block rejected");
        assertTrue(us.addBlock(peer.getBlock(2)), "Honest block still accepted");
    }

    @Test
    @DisplayName("Swapping a claim's unlocking script inside a block is detected")
    void editedUnlockingScript_blockRejected() {
        Blockchain peer = contractChain();
        Block forgedClaim = forged(peer.getBlock(3), "PUSH " + SECRET, "PUSH not-the-secret");

        assertFalse(forgedClaim.hasValidMerkleRoot());

        Blockchain us = new Blockchain();
        assertTrue(us.addBlock(peer.getBlock(1)));
        assertTrue(us.addBlock(peer.getBlock(2)));
        assertFalse(us.addBlock(forgedClaim));
        assertTrue(us.addBlock(peer.getBlock(3)));
        assertEquals(15.0, us.getBalance(BOB), 1e-9);
    }

    // ===== AMOUNTS (#260) =====

    /** A peer chain: reward to Alice, then a block with Alice -> Bob 10 BSC. */
    private static Blockchain peerChain() {
        Blockchain peer = new Blockchain();
        peer.minePendingTransactions(ALICE);
        assertTrue(peer.addTransaction(new Transaction(ALICE, BOB, 10)));
        peer.minePendingTransactions(ALICE);
        return peer;
    }

    /** A copy of the block (as it would arrive over the wire) with 10 -> 1000. */
    private static Block withEditedAmount(Block block) {
        String json = JsonUtil.GSON.toJson(block).replace("\"amount\":10.0", "\"amount\":1000.0");
        assertTrue(json.contains("\"amount\":1000.0"), "Precondition: the amount was edited");
        return JsonUtil.GSON.fromJson(json, Block.class);
    }

    @Test
    @DisplayName("Honest blocks have a valid Merkle root and transaction ids")
    void honestBlocks_haveValidMerkleRoot() {
        for (Block block : peerChain().getChain()) {
            assertTrue(block.hasValidMerkleRoot(), "Block #" + block.getIndex());
            for (Transaction tx : block.getTransactions()) assertTrue(tx.hasValidId());
        }
    }

    @Test
    @DisplayName("Editing a transaction keeps the block hash but breaks the Merkle check")
    void editedTransaction_keepsHash_failsMerkleCheck() {
        Block original = peerChain().getBlock(2);
        Block forged = withEditedAmount(original);

        // This is the trap: the hash commits to the stored Merkle root only.
        assertEquals(original.getHash(), forged.calculateHash(),
                "The forged block still hashes to the original hash");
        assertFalse(forged.hasValidMerkleRoot(), "...but its transactions no longer match");
    }

    @Test
    @DisplayName("A forged block from a peer is rejected; the honest one is accepted")
    void forgedBlockFromPeer_rejected() {
        Blockchain peer = peerChain();
        Blockchain us = new Blockchain();
        assertTrue(us.addBlock(peer.getBlock(1)));

        assertFalse(us.addBlock(withEditedAmount(peer.getBlock(2))), "Forged block rejected");
        assertEquals(0.0, us.getBalance(BOB), 1e-9, "The forged 1000 never counts");

        assertTrue(us.addBlock(peer.getBlock(2)), "The honest block still fits");
        assertEquals(10.0, us.getBalance(BOB), 1e-9);
        assertTrue(us.isChainValid());
    }
}
