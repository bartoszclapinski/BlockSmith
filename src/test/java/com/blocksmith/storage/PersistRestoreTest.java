package com.blocksmith.storage;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.contract.Contract;
import com.blocksmith.contract.ContractStatus;
import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for persisting the chain and restoring it on startup (Milestone 16b).
 *
 * Each test simulates a restart: run a node against a file database, close
 * it, then build a brand-new Blockchain on the same directory and replay.
 */
@DisplayName("Persist + Restore Tests")
class PersistRestoreTest {

    private static final String ALICE = "alice-address";
    private static final String BOB = "bob-address";
    private static final String SECRET = "open-sesame";
    private static final String HASHLOCK = "SHA256 PUSH " + HashUtil.applySha256(SECRET) + " EQUAL";

    @TempDir
    Path dataDir;

    /** A "restarted node": fresh chain on the same directory, replayed. */
    private record Restarted(Blockchain chain, Blockchain.RestoreReport report, H2ChainStore store) {}

    private Restarted restart() {
        H2ChainStore store = H2ChainStore.openFile(dataDir);
        Blockchain chain = new Blockchain(store);
        return new Restarted(chain, chain.restoreFromStore(), store);
    }

    private static String contractIdOf(Transaction deploy) {
        return deploy.getRecipient().substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
    }

    // ===== RESTART ROUND-TRIP TESTS =====

    @Test
    @DisplayName("Restart restores the tip, balances and contracts; a contract can still be claimed")
    void restart_restoresTipBalancesAndContracts() {
        String tipHash;
        String contractId;
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain chain = new Blockchain(store);
            chain.minePendingTransactions(ALICE);                           // Alice 50
            assertTrue(chain.addTransaction(new Transaction(ALICE, BOB, 10)));
            Transaction deploy = chain.deployContract(ALICE, HASHLOCK, 15);
            assertNotNull(deploy);
            chain.minePendingTransactions(ALICE);                           // +50 -10 -15
            tipHash = chain.getLatestBlock().getHash();
            contractId = contractIdOf(deploy);
        }

        Restarted node = restart();
        try {
            Blockchain chain = node.chain();
            assertEquals(2, node.report().blocksRestored());
            assertEquals(0, node.report().blocksRejected());
            assertEquals(3, chain.getChainSize());
            assertEquals(tipHash, chain.getLatestBlock().getHash());
            assertEquals(75.0, chain.getBalance(ALICE), 1e-9);
            assertEquals(10.0, chain.getBalance(BOB), 1e-9);

            Contract contract = chain.getContract(contractId);
            assertNotNull(contract, "Contract registry is rebuilt from the replayed blocks");
            assertEquals(ContractStatus.OPEN, contract.getStatus());

            // The restored node is fully usable: claim the contract.
            assertNotNull(chain.claimContract(contractId, BOB, "PUSH " + SECRET));
            chain.minePendingTransactions(ALICE);
            assertEquals(25.0, chain.getBalance(BOB), 1e-9);
            assertTrue(chain.isChainValid());
        } finally {
            node.store().close();
        }
    }

    @Test
    @DisplayName("Restart restores the mempool; mining it afterwards clears it from the store")
    void restart_restoresMempool() {
        Transaction pending = new Transaction(ALICE, BOB, 5);
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain chain = new Blockchain(store);
            chain.minePendingTransactions(ALICE);
            assertTrue(chain.addTransaction(pending));
        }

        Restarted node = restart();
        try {
            Blockchain chain = node.chain();
            assertEquals(1, node.report().pendingRestored());
            assertEquals(1, chain.getPendingCount());
            assertEquals(pending.getTransactionId(),
                    chain.getPendingTransactions().get(0).getTransactionId());

            chain.minePendingTransactions(ALICE);
            assertEquals(0, chain.getPendingCount());
            assertTrue(node.store().loadPending().isEmpty(),
                    "Confirmed transactions are removed from the stored mempool");
        } finally {
            node.store().close();
        }
    }

    // ===== SAFETY TESTS =====

    @Test
    @DisplayName("A block edited on disk is rejected; replay stops there")
    void restore_tamperedBlock_rejectedAndStops() throws Exception {
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain chain = new Blockchain(store);
            chain.minePendingTransactions(ALICE);                             // #1
            assertTrue(chain.addTransaction(new Transaction(ALICE, BOB, 10)));
            chain.minePendingTransactions(ALICE);                             // #2
            chain.minePendingTransactions(ALICE);                             // #3
        }

        // Someone edits block #2 on disk: Bob now "received" 1000 instead of 10.
        String url = "jdbc:h2:file:" + dataDir.toAbsolutePath().resolve("chain").toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE blocks SET json = REPLACE(json, '\"amount\":10.0', '\"amount\":1000.0') WHERE idx = 2")) {
            assertEquals(1, ps.executeUpdate(), "Precondition: the row was edited");
        }

        Restarted node = restart();
        try {
            assertEquals(1, node.report().blocksRestored(), "Only #1 is valid");
            assertEquals(2, node.report().blocksRejected(), "#2 is tampered, #3 links to it");
            assertEquals(2, node.chain().getChainSize(), "Chain is genesis + #1");
            assertEquals(0.0, node.chain().getBalance(BOB), 1e-9, "The forged 1000 never counts");
            assertEquals(0, node.chain().getOrphanCount(), "Rejected blocks are not parked as orphans");
        } finally {
            node.store().close();
        }
    }

    @Test
    @DisplayName("An empty store restores genesis only; restoring twice is refused")
    void restore_emptyStore_genesisOnly() {
        Restarted node = restart();
        try {
            assertEquals(new Blockchain.RestoreReport(0, 0, 0), node.report());
            assertEquals(1, node.chain().getChainSize());

            node.chain().minePendingTransactions(ALICE);
            assertThrows(IllegalStateException.class, () -> node.chain().restoreFromStore(),
                    "Restore is only allowed on a fresh chain");
        } finally {
            node.store().close();
        }
    }

    @Test
    @DisplayName("Blocks from peers, including attached orphans, are persisted")
    void externalAndOrphanBlocks_arePersisted() {
        // A peer mines three blocks...
        Blockchain peer = new Blockchain();
        peer.minePendingTransactions(ALICE);
        peer.minePendingTransactions(ALICE);
        peer.minePendingTransactions(ALICE);
        List<Block> peerChain = peer.getChain();

        // ...and they reach us out of order: #3, #2 (orphans), then #1.
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain us = new Blockchain(store);
            assertFalse(us.addBlock(peerChain.get(3)));
            assertFalse(us.addBlock(peerChain.get(2)));
            assertTrue(us.addBlock(peerChain.get(1)));
            assertEquals(4, us.getChainSize(), "Orphans attach once #1 arrives");
        }

        Restarted node = restart();
        try {
            assertEquals(3, node.report().blocksRestored());
            assertEquals(peer.getLatestBlock().getHash(), node.chain().getLatestBlock().getHash());
        } finally {
            node.store().close();
        }
    }

    @Test
    @DisplayName("A transaction left in the stored mempool but already mined is not re-added")
    void restore_confirmedButStillStoredPending_notReadded() {
        Transaction tx = new Transaction(ALICE, BOB, 5);
        try (H2ChainStore store = H2ChainStore.openFile(dataDir)) {
            Blockchain chain = new Blockchain(store);
            chain.minePendingTransactions(ALICE);
            assertTrue(chain.addTransaction(tx));
            chain.minePendingTransactions(ALICE);
            // Simulate a crash between "block saved" and "pending cleared".
            store.savePending(tx);
        }

        Restarted node = restart();
        try {
            assertEquals(0, node.report().pendingRestored());
            assertEquals(0, node.chain().getPendingCount(),
                    "Re-adding it would let the same transaction be mined twice");
            assertTrue(node.store().loadPending().isEmpty(), "The stale row is cleaned up");
        } finally {
            node.store().close();
        }
    }
}
