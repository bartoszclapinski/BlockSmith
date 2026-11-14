package com.blocksmith.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.TestChains;
import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.util.BlockchainConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unique coinbases and an index keyed by position (Milestone 25b). The
 * planning probe: two rewards for one miner made in the same millisecond
 * shared an id, and tx_index (keyed by id) kept only one of them.
 */
@DisplayName("Coinbase Height Tests")
class CoinbaseHeightTest {

    @TempDir
    Path dataDir;

    private static Block blockOn(Blockchain c, List<Transaction> txs) {
        Block tip = c.getLatestBlock();
        Block b = new Block(tip.getIndex() + 1, txs, tip.getHash(), Math.max(System.currentTimeMillis(), tip.getTimestamp() + 1));
        b.mineBlock(c.getNextTarget());
        return b;
    }

    private static Transaction reward(String miner, Long height) {
        return new Transaction(BlockchainConfig.COINBASE_ADDRESS, miner, BlockchainConfig.MINING_REWARD, 0, height);
    }

    @Test
    @DisplayName("The probe: two old-style rewards with one id are both in the history; the lookup finds the first")
    void sameIdRewards_bothInHistory_lookupFindsFirst() {
        Blockchain chain = new Blockchain(H2ChainStore.openInMemory("cb-probe-" + System.nanoTime()), TestChains.PARAMS);
        Transaction a, b;
        do {                                                         // pre-25b coinbases: no height
            a = reward("miner", null);
            b = reward("miner", null);
        } while (!a.getTransactionId().equals(b.getTransactionId()));

        assertTrue(chain.addBlock(blockOn(chain, List.of(a))), "Old blocks without a height stay valid");
        assertTrue(chain.addBlock(blockOn(chain, List.of(b))));
        assertEquals(100.0, chain.getBalance("miner"), 1e-9);
        assertEquals(2, chain.getTransactionHistory("miner").size(), "Both rewards, not one");
        assertEquals(1, chain.findTransaction(a.getTransactionId()).blockIndex(), "The earliest occurrence");
    }

    @Test
    @DisplayName("Mined coinbases carry their height, so rewards made in one millisecond differ")
    void rewardsInOneMillisecond_haveDifferentIds() {
        Transaction one, two;
        do {
            one = reward("miner", 1L);
            two = reward("miner", 2L);
        } while (one.getTimestamp() != two.getTimestamp());
        assertNotEquals(one.getTransactionId(), two.getTransactionId());

        Blockchain chain = new Blockchain(NoOpChainStore.INSTANCE, TestChains.PARAMS);
        chain.minePendingTransactions("miner");
        chain.minePendingTransactions("miner");
        for (int h = 1; h <= 2; h++) {
            assertEquals(Long.valueOf(h), chain.getBlock(h).getTransactions().get(0).getSequence(), "Height in block #" + h);
        }
    }

    @Test
    @DisplayName("A coinbase that states a height must state its own block's")
    void coinbaseWithWrongHeight_refused() {
        Blockchain chain = new Blockchain(NoOpChainStore.INSTANCE, TestChains.PARAMS);
        assertFalse(chain.addBlock(blockOn(chain, List.of(reward("miner", 5L)))));
        assertFalse(chain.addBlock(blockOn(chain, List.of(reward("miner", 0L)))));
        assertTrue(chain.addBlock(blockOn(chain, List.of(reward("miner", 1L)))));
    }

    private List<String> primaryKeyOfTxIndex(Connection c) throws Exception {
        List<String> key = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT k.COLUMN_NAME"
                + " FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS t JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE k"
                + " ON k.CONSTRAINT_SCHEMA = t.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = t.CONSTRAINT_NAME"
                + " WHERE t.TABLE_NAME = 'TX_INDEX' AND t.CONSTRAINT_TYPE = 'PRIMARY KEY' ORDER BY k.ORDINAL_POSITION")) {
            while (rs.next()) key.add(rs.getString(1));
        }
        return key;
    }

    @Test
    @DisplayName("A database with the old index (keyed by id) gets the new one, refilled on restore")
    void oldIndexSchema_rebuiltOnOpen() throws Exception {
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain chain = new Blockchain(store, TestChains.PARAMS);
            chain.minePendingTransactions("miner");
            chain.minePendingTransactions("miner");
        }
        String url = "jdbc:h2:file:" + dataDir.toAbsolutePath().resolve("chain").toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {   // a pre-25b database
            st.execute("DROP TABLE tx_index");
            st.execute("CREATE TABLE tx_index (tx_id VARCHAR(128) PRIMARY KEY, block_idx INT NOT NULL, pos INT NOT NULL,"
                    + " sender VARCHAR(256) NOT NULL, recipient VARCHAR(256) NOT NULL, amount DOUBLE NOT NULL)");
            st.execute("INSERT INTO tx_index VALUES ('stale', 9, 0, 'x', 'miner', 1)");
            assertEquals(List.of("TX_ID"), primaryKeyOfTxIndex(c));
        }

        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            try (Connection c = DriverManager.getConnection(url)) {
                assertEquals(List.of("BLOCK_IDX", "POS"), primaryKeyOfTxIndex(c));
            }
            Blockchain chain = new Blockchain(store, TestChains.PARAMS);
            assertEquals(2, chain.restoreFromStore().blocksRestored());
            assertEquals(2, chain.getTransactionHistory("miner").size(), "Refilled by the restore, the stale row gone");
            assertEquals(100.0, chain.getBalance("miner"), 1e-9);
        }
    }
}
