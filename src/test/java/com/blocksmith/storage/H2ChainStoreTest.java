package com.blocksmith.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.core.Wallet;
import com.blocksmith.util.HashUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the H2 chain store (Milestone 16a): blocks and pending
 * transactions round-trip losslessly, saves are idempotent, order is kept,
 * and a file database survives being closed and reopened.
 */
@DisplayName("H2 Chain Store Tests")
class H2ChainStoreTest {

    private static final String ALICE = "alice-address";
    private static final String BOB = "bob-address";

    private H2ChainStore store;
    private Blockchain blockchain;

    @BeforeEach
    void setUp() {
        store = H2ChainStore.openInMemory("store-" + UUID.randomUUID());
        blockchain = new Blockchain();
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    /** Mines a reward to Alice, then a block with Alice -> Bob 10 BSC. */
    private void mineSampleChain() {
        blockchain.minePendingTransactions(ALICE);
        assertTrue(blockchain.addTransaction(new Transaction(ALICE, BOB, 10)));
        blockchain.minePendingTransactions(ALICE);
    }

    private static List<String> txIds(Block block) {
        List<String> ids = new ArrayList<>();
        for (Transaction tx : block.getTransactions()) ids.add(tx.getTransactionId());
        return ids;
    }

    // ===== BLOCK TESTS =====

    @Test
    @DisplayName("A saved block loads back with the same hash, links and transactions")
    void saveAndLoad_blockRoundTripsWithTransactions() {
        mineSampleChain();
        for (Block block : blockchain.getChain()) store.saveBlock(block);

        List<Block> loaded = store.loadBlocks();

        assertEquals(blockchain.getChainSize(), loaded.size());
        for (int i = 0; i < loaded.size(); i++) {
            Block original = blockchain.getBlock(i);
            Block copy = loaded.get(i);
            assertEquals(original.getIndex(), copy.getIndex());
            assertEquals(original.getHash(), copy.getHash());
            assertEquals(original.getPreviousHash(), copy.getPreviousHash());
            assertEquals(original.getMerkleRoot(), copy.getMerkleRoot());
            assertEquals(txIds(original), txIds(copy));
            // Integrity: the loaded content still hashes to the stored hash.
            assertEquals(copy.getHash(), copy.calculateHash(),
                    "Block #" + i + " content must reproduce its hash after loading");
        }
    }

    @Test
    @DisplayName("loadBlocks returns blocks ordered by index, whatever the save order")
    void loadBlocks_orderedByIndex() {
        mineSampleChain();
        List<Block> chain = blockchain.getChain();
        for (int i = chain.size() - 1; i >= 0; i--) store.saveBlock(chain.get(i));

        List<Block> loaded = store.loadBlocks();

        for (int i = 0; i < loaded.size(); i++) {
            assertEquals(i, loaded.get(i).getIndex());
        }
    }

    @Test
    @DisplayName("Saving the same block twice stores it once")
    void saveBlock_twice_isIdempotent() {
        Block genesis = blockchain.getBlock(0);

        store.saveBlock(genesis);
        store.saveBlock(genesis);

        List<Block> loaded = store.loadBlocks();
        assertEquals(1, loaded.size());
        assertEquals(genesis.getHash(), loaded.get(0).getHash());
    }

    @Test
    @DisplayName("Contract locking scripts survive the round trip")
    void contractScripts_surviveRoundTrip() {
        String lock = "SHA256 PUSH " + HashUtil.applySha256("open-sesame") + " EQUAL";
        blockchain.minePendingTransactions(ALICE);
        Transaction deploy = blockchain.deployContract(ALICE, lock, 15);
        assertNotNull(deploy, "Deploy should be accepted");
        Block mined = blockchain.minePendingTransactions(ALICE);

        store.saveBlock(mined);
        Block loaded = store.loadBlocks().get(0);

        Transaction stored = loaded.getTransactions().stream()
                .filter(tx -> tx.getTransactionId().equals(deploy.getTransactionId()))
                .findFirst().orElseThrow();
        assertEquals(lock, stored.getLockingScript());
        assertEquals(loaded.getHash(), loaded.calculateHash());
    }

    @Test
    @DisplayName("A wallet-signed transaction still verifies after storage")
    void signedTransaction_survivesRoundTrip_signatureStillVerifies() {
        Wallet wallet = new Wallet();
        blockchain.minePendingTransactions(wallet.getAddress());
        Transaction signed = new Transaction(wallet.getAddress(), BOB, 5);
        wallet.signTransaction(signed);
        assertTrue(signed.verifySignature(), "Precondition: signature is valid");
        assertTrue(blockchain.addTransaction(signed));

        // Pending first, then confirmed in a block.
        store.savePending(signed);
        assertTrue(store.loadPending().get(0).verifySignature(),
                "Pending copy must keep a verifiable signature");

        Block mined = blockchain.minePendingTransactions(ALICE);
        store.saveBlock(mined);
        Transaction confirmed = store.loadBlocks().get(0).getTransactions().stream()
                .filter(tx -> tx.getTransactionId().equals(signed.getTransactionId()))
                .findFirst().orElseThrow();
        assertTrue(confirmed.verifySignature(),
                "Confirmed copy must keep a verifiable signature (public key round-trips)");
    }

    // ===== PENDING TESTS =====

    @Test
    @DisplayName("Pending transactions: add, dedupe, remove, load in arrival order")
    void pending_addRemoveLoad() {
        Transaction t1 = new Transaction(ALICE, BOB, 1);
        Transaction t2 = new Transaction(ALICE, BOB, 2);
        Transaction t3 = new Transaction(BOB, ALICE, 3);

        store.savePending(t1);
        store.savePending(t2);
        store.savePending(t3);
        store.savePending(t2); // duplicate: no new row, order unchanged
        store.removePending(t1.getTransactionId());

        List<Transaction> loaded = store.loadPending();
        assertEquals(2, loaded.size());
        assertEquals(t2.getTransactionId(), loaded.get(0).getTransactionId());
        assertEquals(t3.getTransactionId(), loaded.get(1).getTransactionId());
    }

    // ===== FILE DATABASE TESTS =====

    @Test
    @DisplayName("A fresh file database creates its schema and survives a reopen")
    void freshDatabase_createsSchema_andPersistsAcrossReopen(@TempDir Path tmp) {
        Path dir = tmp.resolve("node-data");
        Block genesis = blockchain.getBlock(0);

        try (H2ChainStore fileStore = H2ChainStore.openFile(dir)) {
            assertTrue(fileStore.loadBlocks().isEmpty(), "New database starts empty");
            assertTrue(fileStore.loadPending().isEmpty());
            fileStore.saveBlock(genesis);
        }
        assertTrue(Files.exists(dir.resolve("chain.mv.db")), "Database file should be created");

        try (H2ChainStore reopened = H2ChainStore.openFile(dir)) {
            List<Block> loaded = reopened.loadBlocks();
            assertEquals(1, loaded.size());
            assertEquals(genesis.getHash(), loaded.get(0).getHash());
        }
    }

    @Test
    @DisplayName("The no-op store remembers nothing")
    void noOpStore_remembersNothing() {
        ChainStore noOp = NoOpChainStore.INSTANCE;
        noOp.saveBlock(blockchain.getBlock(0));
        noOp.savePending(new Transaction(ALICE, BOB, 1));

        assertTrue(noOp.loadBlocks().isEmpty());
        assertTrue(noOp.loadPending().isEmpty());
    }
}
