package com.blocksmith.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.JsonUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that a block's transactions must match the Merkle root its hash
 * commits to (#260). Before the fix, a block with an edited transaction kept
 * its old ids and Merkle root, so its hash and Proof-of-Work still checked out.
 */
@DisplayName("Block Integrity Tests")
class BlockIntegrityTest {

    private static final String ALICE = "alice-address";
    private static final String BOB = "bob-address";

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
