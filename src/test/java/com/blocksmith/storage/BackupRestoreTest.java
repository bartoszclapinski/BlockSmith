package com.blocksmith.storage;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for online backup and restore-at-startup (Milestone 16d).
 */
@DisplayName("Backup + Restore Tests")
class BackupRestoreTest {

    private static final String ALICE = "alice-address";
    private static final String BOB = "bob-address";

    @TempDir
    Path tmp;

    /** Builds a node with Alice 50 -> Bob 10 mined; returns the tip hash. */
    private static String buildChain(Blockchain chain) {
        chain.minePendingTransactions(ALICE);
        assertTrue(chain.addTransaction(new Transaction(ALICE, BOB, 10)));
        chain.minePendingTransactions(ALICE);
        return chain.getLatestBlock().getHash();
    }

    /** Opens {@code dir} as a node would at startup and replays it. */
    private static Blockchain startNode(H2ChainStore store) {
        Blockchain chain = new Blockchain(store);
        chain.restoreFromStore();
        return chain;
    }

    // ===== HAPPY PATH =====

    @Test
    @DisplayName("Backup, then restore into a fresh node: same tip and balances")
    void backup_thenRestore_sameTipAndBalances() {
        Path zip = tmp.resolve("backups/chain.zip");
        String tip;
        try (H2ChainStore store = H2ChainStore.openFile(tmp.resolve("nodeA"))) {
            Blockchain chain = new Blockchain(store);
            tip = buildChain(chain);
            chain.backupTo(zip);
        }
        assertTrue(Files.exists(zip));

        Path nodeB = tmp.resolve("nodeB");
        assertNull(H2ChainStore.restoreBackup(zip, nodeB), "Fresh directory: nothing to move aside");
        try (H2ChainStore store = H2ChainStore.openFile(nodeB)) {
            Blockchain chain = startNode(store);
            assertEquals(tip, chain.getLatestBlock().getHash());
            assertEquals(90.0, chain.getBalance(ALICE), 1e-9);
            assertEquals(10.0, chain.getBalance(BOB), 1e-9);
            assertTrue(chain.isChainValid());
        }
    }

    @Test
    @DisplayName("Backup is taken while the node keeps running, as a snapshot")
    void backup_whileRunning_isASnapshot() {
        Path zip = tmp.resolve("snap.zip");
        String tipAtBackup;
        try (H2ChainStore store = H2ChainStore.openFile(tmp.resolve("live"))) {
            Blockchain chain = new Blockchain(store);
            tipAtBackup = buildChain(chain);
            chain.backupTo(zip);
            chain.minePendingTransactions(ALICE);       // the node carries on after the backup
            assertEquals(4, chain.getChainSize());
        }

        Path restored = tmp.resolve("restored");
        H2ChainStore.restoreBackup(zip, restored);
        try (H2ChainStore store = H2ChainStore.openFile(restored)) {
            Blockchain chain = startNode(store);
            assertEquals(3, chain.getChainSize(), "Blocks mined after the backup are not in it");
            assertEquals(tipAtBackup, chain.getLatestBlock().getHash());
        }
    }

    @Test
    @DisplayName("Restoring over existing data keeps the old database (renamed, not deleted)")
    void restore_keepsPreviousDatabaseAside() throws Exception {
        Path zip = tmp.resolve("b.zip");
        try (H2ChainStore store = H2ChainStore.openFile(tmp.resolve("source"))) {
            Blockchain chain = new Blockchain(store);
            buildChain(chain);
            chain.backupTo(zip);
        }
        Path target = tmp.resolve("target");
        try (H2ChainStore store = H2ChainStore.openFile(target)) {
            new Blockchain(store).minePendingTransactions(BOB);    // some other history
        }
        byte[] before = Files.readAllBytes(target.resolve(H2ChainStore.DB_FILE));

        Path previous = H2ChainStore.restoreBackup(zip, target);

        assertNotNull(previous);
        assertArrayEquals(before, Files.readAllBytes(previous), "Old database kept byte for byte");
        try (H2ChainStore store = H2ChainStore.openFile(target)) {
            assertEquals(3, startNode(store).getChainSize(), "The backup is now live");
        }
    }

    // ===== FAILURES =====

    @Test
    @DisplayName("A missing backup file fails with a clear error")
    void restore_missingFile_clearError() {
        StorageException e = assertThrows(StorageException.class,
                () -> H2ChainStore.restoreBackup(tmp.resolve("nope.zip"), tmp.resolve("node")));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    @DisplayName("A corrupt zip fails clearly and leaves the current database untouched")
    void restore_corruptZip_clearError_dataUntouched() throws Exception {
        Path node = tmp.resolve("node");
        try (H2ChainStore store = H2ChainStore.openFile(node)) {
            buildChain(new Blockchain(store));
        }
        byte[] before = Files.readAllBytes(node.resolve(H2ChainStore.DB_FILE));
        Path junk = tmp.resolve("junk.zip");
        Files.write(junk, "this is not a zip file at all".getBytes());

        StorageException e = assertThrows(StorageException.class,
                () -> H2ChainStore.restoreBackup(junk, node));
        assertTrue(e.getMessage().contains("Not a BlockSmith backup"), e.getMessage());
        assertArrayEquals(before, Files.readAllBytes(node.resolve(H2ChainStore.DB_FILE)));
        try (var files = Files.list(node)) {
            assertTrue(files.noneMatch(p -> p.getFileName().toString().startsWith("restore-")),
                    "Staging folder is cleaned up");
        }
    }

    @Test
    @DisplayName("A zip with a damaged database inside fails before touching current data")
    void restore_damagedDatabaseInsideZip_clearError() throws Exception {
        Path node = tmp.resolve("node");
        try (H2ChainStore store = H2ChainStore.openFile(node)) {
            buildChain(new Blockchain(store));
        }
        Path bad = tmp.resolve("bad.zip");
        writeZip(bad, H2ChainStore.DB_FILE, "garbage, not an H2 database".getBytes());

        StorageException e = assertThrows(StorageException.class,
                () -> H2ChainStore.restoreBackup(bad, node));
        assertTrue(e.getMessage().contains("damaged"), e.getMessage());
        try (H2ChainStore store = H2ChainStore.openFile(node)) {
            assertEquals(3, startNode(store).getChainSize(), "Current data still works");
        }
    }

    @Test
    @DisplayName("A zip with unexpected entries (e.g. path traversal) is rejected")
    void restore_unexpectedEntry_rejected() throws Exception {
        Path evil = tmp.resolve("evil.zip");
        writeZip(evil, "../../outside.mv.db", new byte[] {1, 2, 3});

        StorageException e = assertThrows(StorageException.class,
                () -> H2ChainStore.restoreBackup(evil, tmp.resolve("node")));
        assertTrue(e.getMessage().contains("unexpected entry"), e.getMessage());
        assertFalse(Files.exists(tmp.getParent().resolve("outside.mv.db")));
    }

    @Test
    @DisplayName("An in-memory chain cannot be backed up")
    void backup_inMemory_unsupportedOrFails() {
        assertThrows(UnsupportedOperationException.class,
                () -> new Blockchain().backupTo(tmp.resolve("x.zip")));
        try (H2ChainStore mem = H2ChainStore.openInMemory("mem-" + UUID.randomUUID())) {
            assertThrows(StorageException.class, () -> mem.backup(tmp.resolve("y.zip")),
                    "An H2 in-memory database has no file to back up");
        }
    }

    private static void writeZip(Path zip, String entryName, byte[] content) throws Exception {
        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry(entryName));
            z.write(content);
            z.closeEntry();
        }
    }
}
