package com.blocksmith.api;

import java.util.LinkedHashMap;
import java.util.Map;

import com.blocksmith.core.Blockchain;
import com.blocksmith.network.NetworkConfig;
import com.blocksmith.network.Node;
import com.google.gson.Gson;

import io.javalin.Javalin;
import io.javalin.http.Context;

/**
 * THEORY: REST API in front of a P2P node.
 *
 * A blockchain node has two audiences: OTHER NODES (which speak our TCP gossip
 * protocol) and USERS/TOOLS (which speak HTTP/JSON). This class is the second
 * interface. It does not replace the P2P layer - it sits beside it and drives
 * the SAME {@link Blockchain}/{@link Node}, so an action taken over HTTP on one
 * node propagates to peers through the existing broadcast paths.
 *
 * Milestone 12a provides the server bootstrap and read-only endpoints;
 * state-changing endpoints (transactions, mining, wallets) follow in 12b/12c.
 *
 * JSON is produced with Gson (already a project dependency) rather than
 * Javalin's default mapper, so responses match the wire format used elsewhere.
 */
public class ApiServer {

    private final Node node;
    private final Blockchain blockchain;
    private final int port;
    private final Gson gson = new Gson();

    private Javalin app;

    public ApiServer(Node node) {
        this(node, NetworkConfig.API_PORT);
    }

    public ApiServer(Node node, int port) {
        this.node = node;
        this.blockchain = node.getBlockchain();
        this.port = port;
    }

    /** Starts the HTTP server and registers routes. */
    public void start() {
        app = Javalin.create(config -> config.showJavalinBanner = false);
        registerRoutes();
        app.start(port);
    }

    /** Stops the HTTP server if it is running. */
    public void stop() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    public int getPort() {
        return port;
    }

    // ===== ROUTES =====

    private void registerRoutes() {
        // Read/query endpoints (Milestone 12a).
        app.get("/api/blocks", ctx -> json(ctx, blockchain.getChain()));
        app.get("/api/blocks/latest", ctx -> json(ctx, blockchain.getLatestBlock()));
        app.get("/api/blocks/{index}", this::getBlockByIndex);
        app.get("/api/network/status", ctx -> json(ctx, status()));
    }

    private void getBlockByIndex(Context ctx) {
        int index;
        try {
            index = Integer.parseInt(ctx.pathParam("index"));
        } catch (NumberFormatException e) {
            ctx.status(400);
            json(ctx, error("Block index must be an integer"));
            return;
        }

        if (index < 0 || index >= blockchain.getChainSize()) {
            ctx.status(404);
            json(ctx, error("No block at index " + index));
            return;
        }
        json(ctx, blockchain.getBlock(index));
    }

    // ===== HELPERS =====

    /** A snapshot of node/chain state for GET /api/network/status. */
    private Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", node.getNodeId());
        m.put("p2pPort", node.getPort());
        m.put("apiPort", port);
        m.put("chainLength", blockchain.getChainSize());
        m.put("pendingTransactions", blockchain.getPendingTransactions().size());
        m.put("connectedPeers", node.getPeerManager().getConnectedPeers().size());
        return m;
    }

    private Map<String, String> error(String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    /** Writes {@code data} as a JSON response using Gson. */
    private void json(Context ctx, Object data) {
        ctx.contentType("application/json");
        ctx.result(gson.toJson(data));
    }
}
