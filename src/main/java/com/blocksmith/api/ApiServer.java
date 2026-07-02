package com.blocksmith.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.core.Wallet;
import com.blocksmith.network.NetworkConfig;
import com.blocksmith.network.Node;
import com.blocksmith.network.PeerInfo;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

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
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            // Serve the web dashboard from the classpath (src/main/resources/public).
            // API routes take precedence; "/" falls through to index.html.
            config.staticFiles.add("/public", Location.CLASSPATH);
        });
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

        // Transaction + mining endpoints (Milestone 12b).
        app.post("/api/transactions", this::postTransaction);
        app.get("/api/transactions/{id}", this::getTransactionById);
        app.post("/api/mine", this::postMine);

        // Wallet + network endpoints (Milestone 12c).
        app.get("/api/wallet/{address}", this::getWallet);
        app.post("/api/wallet/create", this::postWalletCreate);
        app.get("/api/network/peers", this::getPeers);

        // JSON error handling (Milestone 12c).
        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(500);
            json(ctx, error("Internal server error"));
        });
        app.error(404, ctx -> {
            // Leave explicit JSON 404s (from our handlers) untouched; only
            // replace the framework default for unmatched routes.
            String contentType = ctx.res().getContentType();
            if (contentType == null || !contentType.contains("application/json")) {
                json(ctx, error("Not found: " + ctx.path()));
            }
        });
    }

    // ===== 12b: TRANSACTION + MINING =====

    /** Accepts a transaction, adds it to the mempool, and broadcasts it. */
    private void postTransaction(Context ctx) {
        JsonObject body;
        try {
            body = JsonParser.parseString(ctx.body()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return;
        }

        if (!body.has("sender") || !body.has("recipient") || !body.has("amount")) {
            ctx.status(400);
            json(ctx, error("Transaction requires sender, recipient, and amount"));
            return;
        }

        // Build the transaction through its constructor so the id and timestamp
        // are computed correctly (Gson would bypass the constructor).
        Transaction tx = new Transaction(
                body.get("sender").getAsString(),
                body.get("recipient").getAsString(),
                body.get("amount").getAsDouble());

        if (!blockchain.addTransaction(tx)) {
            ctx.status(400);
            json(ctx, error("Transaction rejected (invalid or insufficient funds)"));
            return;
        }

        node.broadcastTransaction(tx);
        ctx.status(201);
        json(ctx, tx);
    }

    /** Looks up a transaction by id in the mempool or in a mined block. */
    private void getTransactionById(Context ctx) {
        String id = ctx.pathParam("id");
        Transaction found = findTransaction(id);
        if (found == null) {
            ctx.status(404);
            json(ctx, error("No transaction with id " + id));
            return;
        }
        json(ctx, found);
    }

    /** Mines the pending transactions to a miner address and broadcasts the block. */
    private void postMine(Context ctx) {
        JsonObject body;
        try {
            body = JsonParser.parseString(ctx.body()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return;
        }

        if (!body.has("minerAddress") || body.get("minerAddress").getAsString().isBlank()) {
            ctx.status(400);
            json(ctx, error("minerAddress is required"));
            return;
        }

        Block mined = blockchain.minePendingTransactions(body.get("minerAddress").getAsString());
        node.broadcastBlock(mined);
        ctx.status(201);
        json(ctx, mined);
    }

    private Transaction findTransaction(String id) {
        for (Transaction tx : blockchain.getPendingTransactions()) {
            if (id.equals(tx.getTransactionId())) return tx;
        }
        for (Block block : blockchain.getChain()) {
            for (Transaction tx : block.getTransactions()) {
                if (id.equals(tx.getTransactionId())) return tx;
            }
        }
        return null;
    }

    // ===== 12c: WALLET + NETWORK =====

    /** Reports an address's confirmed balance and its pending outgoing total. */
    private void getWallet(Context ctx) {
        String address = ctx.pathParam("address");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("address", address);
        m.put("balance", blockchain.getBalance(address));
        m.put("pendingOutgoing", pendingOutgoing(address));
        json(ctx, m);
    }

    /**
     * Creates a fresh wallet and returns its address ONLY. A real node must
     * never expose the private key over the API - the key stays with the owner;
     * here we simply never serialize it.
     */
    private void postWalletCreate(Context ctx) {
        Wallet wallet = new Wallet();
        Map<String, String> m = new LinkedHashMap<>();
        m.put("address", wallet.getAddress());
        ctx.status(201);
        json(ctx, m);
    }

    /** Lists the currently connected peers. */
    private void getPeers(Context ctx) {
        List<Map<String, Object>> peers = new ArrayList<>();
        for (PeerInfo peer : node.getPeerManager().getConnectedPeers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("address", peer.getAddress());
            m.put("nodeId", peer.getNodeId());
            m.put("state", peer.getState().toString());
            peers.add(m);
        }
        json(ctx, peers);
    }

    /** Sum of pending transactions sent by {@code address}. */
    private double pendingOutgoing(String address) {
        double sum = 0;
        for (Transaction tx : blockchain.getPendingTransactions()) {
            if (address.equals(tx.getSender())) sum += tx.getAmount();
        }
        return sum;
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
