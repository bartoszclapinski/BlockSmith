package com.blocksmith;

import com.blocksmith.api.ApiServer;
import com.blocksmith.network.NetworkConfig;
import com.blocksmith.network.Node;

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
 */
public class BlockSmithNode {

    public static void main(String[] args) throws java.io.IOException {
        int p2pPort = args.length > 0 ? Integer.parseInt(args[0]) : NetworkConfig.DEFAULT_PORT;
        int apiPort = args.length > 1 ? Integer.parseInt(args[1]) : NetworkConfig.API_PORT;

        Node node = new Node(p2pPort);
        node.start();

        ApiServer api = new ApiServer(node, apiPort);
        api.start();

        System.out.println("BlockSmith node is running:");
        System.out.println("  P2P      : port " + p2pPort);
        System.out.println("  API      : http://localhost:" + apiPort + "/api");
        System.out.println("  Dashboard: http://localhost:" + apiPort + "/");

        // Stop both cleanly on Ctrl+C / shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down BlockSmith node...");
            api.stop();
            node.stop();
        }));
    }

    private BlockSmithNode() {}
}
