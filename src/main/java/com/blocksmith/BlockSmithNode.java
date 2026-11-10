package com.blocksmith;

import java.nio.file.Path;

import com.blocksmith.api.ApiServer;
import com.blocksmith.core.Blockchain;
import com.blocksmith.network.NetworkConfig;
import com.blocksmith.network.Node;
import com.blocksmith.storage.H2ChainStore;

/**
 * Runnable entry point that boots a full BlockSmith node in one process:
 * the P2P {@link Node} (TCP gossip with peers) and the {@link ApiServer}
 * (HTTP/JSON API + web dashboard for users).
 *
 * <p>Usage:
 * <pre>
 *   java -cp target/classes com.blocksmith.BlockSmithNode [p2pPort] [apiPort]
 * </pre>
 * Ports default to {@link NetworkConfig#DEFAULT_PORT} and
 * {@link NetworkConfig#API_PORT}.
 *
 * <p>Persistence (Sprint 16): the chain and mempool are kept in an H2
 * database under {@code data/node-<p2pPort>/} and restored on startup, so a
 * restart picks up where the node left off. Override the directory with
 * {@code -Dblocksmith.dataDir=<path>}.
 */
public class BlockSmithNode {

    public static void main(String[] args) throws java.io.IOException {
        int p2pPort = args.length > 0 ? Integer.parseInt(args[0]) : NetworkConfig.DEFAULT_PORT;
        int apiPort = args.length > 1 ? Integer.parseInt(args[1]) : NetworkConfig.API_PORT;

        Path dataDir = Path.of(System.getProperty("blocksmith.dataDir", "data/node-" + p2pPort));
        H2ChainStore store = H2ChainStore.openFile(dataDir);
        Blockchain blockchain = new Blockchain(store);
        Blockchain.RestoreReport restored = blockchain.restoreFromStore();

        Node node = new Node(p2pPort, blockchain);
        node.start();

        ApiServer api = new ApiServer(node, apiPort);
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
