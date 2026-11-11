package com.blocksmith.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Blockchain.TransactionLookup;
import com.blocksmith.core.Transaction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for indexed lookups (Milestone 16c): block by hash, transaction by
 * id, address history. The same questions must get the same answers with
 * the H2 index and with a plain in-memory scan, and stale index rows must
 * never leak into a result.
 */
@DisplayName("Indexed Query Tests")
class IndexedQueryTest {

    private static final String ALICE = "alice-address";
    private static final String BOB = "bob-address";
    private static final String CAROL = "carol-address";

    private H2ChainStore store;
    private Blockchain chain;

    // Kept so tests can assert on specific transactions.
    private Transaction aliceToBob;
    private Transaction bobToAlice;
    private Transaction aliceToSelf;
    private Transaction pendingAliceToCarol;

    @BeforeEach
    void setUp() {
        store = H2ChainStore.openInMemory("query-" + UUID.randomUUID());
        chain = new Blockchain(store);

        chain.minePendingTransactions(ALICE);                              // #1 Alice +50
        aliceToBob = new Transaction(ALICE, BOB, 10);
        assertTrue(chain.addTransaction(aliceToBob));
        chain.minePendingTransactions(CAROL);                              // #2
        bobToAlice = new Transaction(BOB, ALICE, 3);
        assertTrue(chain.addTransaction(bobToAlice));
        aliceToSelf = new Transaction(ALICE, ALICE, 1);
        assertTrue(chain.addTransaction(aliceToSelf));
        chain.minePendingTransactions(CAROL);                              // #3
        pendingAliceToCarol = new Transaction(ALICE, CAROL, 2);
        assertTrue(chain.addTransaction(pendingAliceToCarol));             // pending
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    private static List<String> ids(List<TransactionLookup> lookups) {
        return lookups.stream().map(l -> l.transaction().getTransactionId()).toList();
    }

    // ===== BLOCK BY HASH =====

    @Test
    @DisplayName("Every block is found by its hash; an unknown hash is not")
    void blockByHash_found_andMissing() {
        for (Block block : chain.getChain()) {
            assertSame(block, chain.getBlockByHash(block.getHash()), "Block #" + block.getIndex());
        }
        assertNull(chain.getBlockByHash("0000not-a-real-hash"));
    }

    // ===== TRANSACTION BY ID =====

    @Test
    @DisplayName("A confirmed transaction is found via the index with its height and confirmations")
    void txById_confirmedViaIndex() {
        TransactionLookup found = chain.findTransaction(aliceToBob.getTransactionId());

        assertNotNull(found);
        assertTrue(found.isConfirmed());
        assertEquals(2, found.blockIndex());
        assertEquals(2, found.confirmations(), "Block #2 with #3 on top = 2 confirmations");

        chain.minePendingTransactions(CAROL);                              // #4
        assertEquals(3, chain.findTransaction(aliceToBob.getTransactionId()).confirmations(),
                "Each new block adds a confirmation");
    }

    @Test
    @DisplayName("A pending transaction is found with 0 confirmations; an unknown id is not found")
    void txById_pendingAndUnknown() {
        TransactionLookup pending = chain.findTransaction(pendingAliceToCarol.getTransactionId());
        assertNotNull(pending);
        assertFalse(pending.isConfirmed());
        assertEquals(0, pending.confirmations());

        assertNull(chain.findTransaction("does-not-exist"));
    }

    // ===== ADDRESS HISTORY =====

    @Test
    @DisplayName("Address history has sent and received txs, pending first, then newest block back")
    void addressHistory_bothDirections_newestFirst() {
        List<TransactionLookup> history = chain.getTransactionHistory(ALICE);

        // Block #1 holds only Alice's coinbase reward.
        String reward = chain.getBlock(1).getTransactions().get(0).getTransactionId();
        assertEquals(List.of(
                pendingAliceToCarol.getTransactionId(),   // pending
                aliceToSelf.getTransactionId(),           // #3, later in the block
                bobToAlice.getTransactionId(),            // #3
                aliceToBob.getTransactionId(),            // #2
                reward),                                  // #1
                ids(history));
        assertEquals(1, ids(history).stream().filter(aliceToSelf.getTransactionId()::equals).count(),
                "A self-transfer is listed once, not as both sent and received");
    }

    @Test
    @DisplayName("The store's own index answers: refs newest first, self-transfer once")
    void storeIndex_returnsRefsNewestFirst() {
        assertTrue(store.isIndexed());
        List<ChainStore.TxRef> refs = store.findTransactionsByAddress(ALICE);

        // Confirmed only (the store does not index the mempool).
        assertEquals(List.of(3, 3, 2, 1), refs.stream().map(ChainStore.TxRef::blockIndex).toList());
        assertEquals(aliceToSelf.getTransactionId(), refs.get(0).transactionId());
        assertEquals(2, store.findTransactionBlockIndex(aliceToBob.getTransactionId()).getAsInt());
        assertEquals(3, store.findBlockIndexByHash(chain.getLatestBlock().getHash()).getAsInt());
        assertTrue(store.findBlockIndexByHash("nope").isEmpty());
    }

    @Test
    @DisplayName("An address with no transactions has an empty history")
    void addressHistory_unknownAddress_empty() {
        assertTrue(chain.getTransactionHistory("nobody").isEmpty());
    }

    // ===== INDEX vs SCAN =====

    @Test
    @DisplayName("Without a database (scan) the answers are identical to the indexed ones")
    void fallbackWithoutDatabase_givesSameAnswers() {
        // A plain in-memory node holding the very same blocks and mempool.
        Blockchain scan = new Blockchain();
        for (int i = 1; i < chain.getChainSize(); i++) assertTrue(scan.addBlock(chain.getBlock(i)));
        assertTrue(scan.addTransaction(pendingAliceToCarol));

        for (String address : List.of(ALICE, BOB, CAROL, "nobody")) {
            List<TransactionLookup> a = chain.getTransactionHistory(address);
            List<TransactionLookup> b = scan.getTransactionHistory(address);
            assertEquals(ids(a), ids(b), "History of " + address);
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).blockIndex(), b.get(i).blockIndex());
                assertEquals(a.get(i).confirmations(), b.get(i).confirmations());
            }
        }
        for (Block block : chain.getChain()) {
            assertEquals(block.getHash(), scan.getBlockByHash(block.getHash()).getHash());
        }
        TransactionLookup indexed = chain.findTransaction(bobToAlice.getTransactionId());
        TransactionLookup scanned = scan.findTransaction(bobToAlice.getTransactionId());
        assertEquals(indexed.blockIndex(), scanned.blockIndex());
        assertEquals(indexed.confirmations(), scanned.confirmations());
    }

    // ===== INDEX MAINTENANCE =====

    @Test
    @DisplayName("Index rows of a block rejected on restore never show up in results")
    void staleIndexRows_fromRejectedBlock_areIgnored(@TempDir Path dir) throws Exception {
        String bobTxId;
        try (H2ChainStore fileStore = H2ChainStore.openFile(dir)) {
            Blockchain c = new Blockchain(fileStore);
            c.minePendingTransactions(ALICE);                              // #1
            Transaction t = new Transaction(ALICE, BOB, 10);
            assertTrue(c.addTransaction(t));
            c.minePendingTransactions(ALICE);                              // #2 (will be tampered)
            bobTxId = t.getTransactionId();
        }
        // Tamper block #2's JSON; its tx_index rows (Bob, block 2) stay in the table.
        try (Connection conn = DriverManager.getConnection(jdbcUrl(dir));
             Statement st = conn.createStatement()) {
            assertEquals(1, st.executeUpdate(
                    "UPDATE blocks SET json = REPLACE(json, '\"amount\":10.0', '\"amount\":99.0') WHERE idx = 2"));
        }

        try (H2ChainStore fileStore = H2ChainStore.openFile(dir)) {
            Blockchain c = new Blockchain(fileStore);
            assertEquals(1, c.restoreFromStore().blocksRestored(), "Only #1 survives");
            assertTrue(fileStore.findTransactionBlockIndex(bobTxId).isPresent(),
                    "Precondition: the stale index row is still in the table");

            assertNull(c.findTransaction(bobTxId), "Stale row must not resolve");
            assertTrue(c.getTransactionHistory(BOB).isEmpty(), "Stale row must not appear in history");
        }
    }

    @Test
    @DisplayName("An empty index (database from before 16c) is rebuilt by restore")
    void emptyIndex_isRebuiltOnRestore(@TempDir Path dir) throws Exception {
        String bobTxId;
        try (H2ChainStore fileStore = H2ChainStore.openFile(dir)) {
            Blockchain c = new Blockchain(fileStore);
            c.minePendingTransactions(ALICE);
            Transaction t = new Transaction(ALICE, BOB, 10);
            assertTrue(c.addTransaction(t));
            c.minePendingTransactions(ALICE);
            bobTxId = t.getTransactionId();
        }
        try (Connection conn = DriverManager.getConnection(jdbcUrl(dir));
             Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM tx_index");                      // like a 16b database
        }

        try (H2ChainStore fileStore = H2ChainStore.openFile(dir)) {
            Blockchain c = new Blockchain(fileStore);
            c.restoreFromStore();
            assertEquals(2, c.findTransaction(bobTxId).blockIndex());
            assertEquals(List.of(bobTxId), ids(c.getTransactionHistory(BOB)));
        }
    }

    private static String jdbcUrl(Path dir) {
        return "jdbc:h2:file:" + dir.toAbsolutePath().resolve("chain").toString().replace('\\', '/');
    }
}
