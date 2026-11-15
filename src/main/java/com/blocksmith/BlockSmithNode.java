package com.blocksmith;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
 *   java -cp target/classes com.blocksmith.BlockSmithNode [p2pPort] [apiPort]
 *        [--peer host:port]... [--restore backup.zip]
 * </pre>
 * Ports default to {@link NetworkConfig#DEFAULT_PORT} and
 * {@link NetworkConfig#API_PORT}.
 *
 * <p>Joining a network (Sprint 26a): every {@code --peer host:port} (the
 * option can repeat), plus a comma-separated
 * {@code -Dblocksmith.peers=host:port,host:port}, is dialled after start and
 * kept: if the link drops, the heartbeat dials it again. More peers can be
 * added later from the dashboard ({@code POST /api/network/peers}).
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

    /**
     * What the command line asked for.
     *
     * @param peers peer addresses ("host:port") to join, in order, no duplicates
     */
    public record Options(int p2pPort, int apiPort, Path dataDir, Path restoreFrom, List<String> peers) {

        /** The same options with another data directory (tests use a temp dir). */
        public Options withDataDir(Path dir) {
            return new Options(p2pPort, apiPort, dir, restoreFrom, peers);
        }
    }

    /** A running node: the P2P node, the API and the store, stopped in that order. */
    public record Running(Node node, ApiServer api, H2ChainStore store, Path dataDir,
                          Blockchain.RestoreReport restored) implements AutoCloseable {
        @Override
        public void close() {
            api.stop();
            node.stop();
            store.close();
        }
    }

    public static void main(String[] args) throws IOException {
        Options options;
        try {
            options = parseArgs(args, System.getProperty("blocksmith.peers"));
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        Running running;
        try {
            running = launch(options);
        } catch (StorageException e) {
            // Refuse to start on a bad backup: the current data is untouched.
            System.err.println("Restore failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        Blockchain.RestoreReport restored = running.restored();
        System.out.println("BlockSmith node is running:");
        System.out.println("  P2P      : port " + options.p2pPort());
        System.out.println("  API      : http://localhost:" + options.apiPort() + "/api");
        System.out.println("  Dashboard: http://localhost:" + options.apiPort() + "/");
        System.out.println("  Data     : " + running.dataDir().toAbsolutePath()
                + " (restored " + restored.blocksRestored() + " blocks, "
                + restored.pendingRestored() + " pending"
                + (restored.blocksRejected() > 0
                        ? ", " + restored.blocksRejected() + " stored blocks rejected" : "")
                + ")");
        if (!options.peers().isEmpty()) {
            System.out.println("  Peers    : " + String.join(", ", options.peers()));
        }

        // Stop everything cleanly on Ctrl+C / shutdown; the store goes last
        // so nothing writes to a closed database.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down BlockSmith node...");
            running.close();
        }));
    }

    /**
     * Reads the command line: positional ports, then any number of
     * {@code --peer host:port} and an optional {@code --restore <zip>}
     * anywhere.
     *
     * @param peersProperty the value of {@code -Dblocksmith.peers}
     *                      (comma-separated host:port), or null
     * @throws IllegalArgumentException with a message for the user on bad input
     */
    public static Options parseArgs(String[] args, String peersProperty) {
        List<String> positional = new ArrayList<>();
        LinkedHashSet<String> peers = new LinkedHashSet<>();
        Path restoreFrom = null;
        for (int i = 0; i < args.length; i++) {
            if ("--restore".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--restore needs a backup file, e.g. --restore backup.zip");
                }
                restoreFrom = Path.of(args[++i]);
            } else if ("--peer".equals(args[i])) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--peer needs an address, e.g. --peer localhost:8333");
                }
                peers.add(peerAddress(args[++i]));
            } else if (args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown option " + args[i]
                        + " (options: --peer host:port, --restore backup.zip)");
            } else {
                positional.add(args[i]);
            }
        }
        if (peersProperty != null) {
            for (String part : peersProperty.split(",")) {
                if (!part.isBlank()) peers.add(peerAddress(part));
            }
        }
        if (positional.size() > 2) {
            throw new IllegalArgumentException("Expected at most two ports (p2pPort apiPort), got " + positional);
        }
        int p2pPort = positional.size() > 0 ? port(positional.get(0)) : NetworkConfig.DEFAULT_PORT;
        int apiPort = positional.size() > 1 ? port(positional.get(1)) : NetworkConfig.API_PORT;
        Path dataDir = Path.of(System.getProperty("blocksmith.dataDir", "data/node-" + p2pPort));
        return new Options(p2pPort, apiPort, dataDir, restoreFrom, List.copyOf(peers));
    }

    private static String peerAddress(String raw) {
        var info = Node.parsePeerAddress(raw);
        if (info == null) {
            throw new IllegalArgumentException("Not a peer address: '" + raw + "' (expected host:port)");
        }
        return info.getAddress();
    }

    private static int port(String raw) {
        try {
            int p = Integer.parseInt(raw);
            if (p >= 1 && p <= 65535) return p;
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException("Not a port: '" + raw + "' (expected 1-65535)");
    }

    /**
     * Opens the store, restores the chain, starts the P2P node (which then
     * dials the peers) and the API.
     *
     * @throws StorageException if a requested restore fails (nothing changed)
     * @throws IOException if a port cannot be bound
     */
    public static Running launch(Options options) throws IOException {
        Path dataDir = options.dataDir();
        if (options.restoreFrom() != null) {
            Path previous = H2ChainStore.restoreBackup(options.restoreFrom(), dataDir);
            System.out.println("Installed backup " + options.restoreFrom().toAbsolutePath()
                    + (previous != null ? " (previous database kept as " + previous.getFileName() + ")" : ""));
        }
        H2ChainStore store = H2ChainStore.openFile(dataDir);
        Node node = null;
        try {
            Blockchain blockchain = new Blockchain(store);
            Blockchain.RestoreReport restored = blockchain.restoreFromStore();

            node = new Node(options.p2pPort(), blockchain);
            for (String peer : options.peers()) node.addPersistentPeer(peer);
            node.start();

            ApiServer api = new ApiServer(node, options.apiPort());
            api.enableBackups(dataDir.resolve("backups"));
            api.start();
            return new Running(node, api, store, dataDir, restored);
        } catch (IOException | RuntimeException e) {
            if (node != null) node.stop();
            store.close();
            throw e;
        }
    }

    private BlockSmithNode() {}
}
