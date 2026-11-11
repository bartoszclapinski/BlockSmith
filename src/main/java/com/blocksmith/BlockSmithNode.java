package com.blocksmith;

import java.nio.file.Path;

import com.blocksmith.api.ApiServer;
import com.blocksmith.core.Blockchain;
import com.blocksmith.network.NetworkConfig;
import com.blocksmith.network.Node;
import com.blocksmith.storage.H2ChainStore;
import com.blocksmith.storage.StorageException;

/**
 * Runnable entry point that boots a full BlockSmith node in one process:
 * the P2P {@link Node} (TCP gossip with peers) and the {@link ApiServer}
 * (HTTP/JSON API + web dashboard for users).
 *
 * <p>Usage:
 * <pre>
 *   java -cp target/classes com.blocksmith.BlockSmithNode [p2pPort] [apiPort] [--restore backup.zip]
 * </pre>
 * Ports default to {@link NetworkConfig#DEFAULT_PORT} and
 * {@link NetworkConfig#API_PORT}.
 *
 * <p>Persistence (Sprint 16): the chain and mempool are kept in an H2
 * database under {@code data/node-<p2pPort>/} and restored on startup, so a
 * restart picks up where the node left off. Override the directory with
 * {@code -Dblocksmith.dataDir=<path>}.
 *
 * <p>Backups (Sprint 16d): {@code POST /api/admin/backup} (from localhost)
 * writes a zip to {@code <dataDir>/backups/}. Start with
 * {@code --restore <zip>} to install a backup first; the current database is
 * kept next to it (renamed), and the restored chain is re-validated block by
 * block like any other restart.
 */
public class BlockSmithNode {

    public static void main(String[] args) throws java.io.IOException {
        // Positional ports, plus an optional "--restore <zip>" anywhere.
        java.util.List<String> positional = new java.util.ArrayList<>();
        Path restoreFrom = null;
        for (int i = 0; i < args.length; i++) {
            if ("--restore".equals(args[i])) {
                if (i + 1 >= args.length) {
                    System.err.println("--restore needs a backup file, e.g. --restore backup.zip");
                    System.exit(2);
                }
                restoreFrom = Path.of(args[++i]);
            } else {
                positional.add(args[i]);
            }
        }
        int p2pPort = positional.size() > 0 ? Integer.parseInt(positional.get(0)) : NetworkConfig.DEFAULT_PORT;
        int apiPort = positional.size() > 1 ? Integer.parseInt(positional.get(1)) : NetworkConfig.API_PORT;

        Path dataDir = Path.of(System.getProperty("blocksmith.dataDir", "data/node-" + p2pPort));
        if (restoreFrom != null) {
            try {
                Path previous = H2ChainStore.restoreBackup(restoreFrom, dataDir);
                System.out.println("Installed backup " + restoreFrom.toAbsolutePath()
                        + (previous != null ? " (previous database kept as " + previous.getFileName() + ")" : ""));
            } catch (StorageException e) {
                // Refuse to start on a bad backup: the current data is untouched.
                System.err.println("Restore failed: " + e.getMessage());
                System.exit(1);
            }
        }
        H2ChainStore store = H2ChainStore.openFile(dataDir);
        Blockchain blockchain = new Blockchain(store);
        Blockchain.RestoreReport restored = blockchain.restoreFromStore();

        Node node = new Node(p2pPort, blockchain);
        node.start();

        ApiServer api = new ApiServer(node, apiPort);
        api.enableBackups(dataDir.resolve("backups"));
        api.start();

        System.out.println("BlockSmith node is running:");
        System.out.println("  P2P      : port " + p2pPort);
        System.out.println("  API      : http://localhost:" + apiPort + "/api");
        System.out.println("  Dashboard: http://localhost:" + apiPort + "/");
        System.out.println("  Data     : " + dataDir.toAbsolutePath()
                + " (restored " + restored.blocksRestored() + " blocks, "
                + restored.pendingRestored() + " pending"
                + (restored.blocksRejected() > 0
                        ? ", " + restored.blocksRejected() + " stored blocks rejected" : "")
                + ")");

        // Stop everything cleanly on Ctrl+C / shutdown; the store goes last
        // so nothing writes to a closed database.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down BlockSmith node...");
            api.stop();
            node.stop();
            store.close();
        }));
    }

    private BlockSmithNode() {}
}
