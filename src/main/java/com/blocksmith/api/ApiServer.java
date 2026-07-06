package com.blocksmith.api;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.blocksmith.contract.Contract;
import com.blocksmith.contract.MultiSigWallet;
import com.blocksmith.contract.ScriptVM;
import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.core.Wallet;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.SignatureUtil;
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

    /**
     * Server-held member keys for multisig wallets created via the API, keyed
     * by locking script.
     *
     * EDUCATIONAL CONVENIENCE: a real wallet signs client-side and the node
     * never sees a private key. For this demo the node generates the member
     * keys and signs claims on the user's behalf - the same trade-off the
     * unsigned transaction endpoints already make. The private keys live here
     * in memory and are NEVER serialized over the API.
     */
    private final Map<String, MultiSigSession> multisigSessions = new LinkedHashMap<>();

    /** A multisig wallet plus the member private keys the node holds for it. */
    private record MultiSigSession(MultiSigWallet wallet, List<PrivateKey> memberKeys) {}

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

        // Contract endpoints (Milestone 14c).
        app.get("/api/contracts", ctx -> json(ctx, contractsList()));
        app.get("/api/contracts/{id}", this::getContract);
        app.post("/api/contracts", this::postContract);
        app.post("/api/contracts/{id}/claim", this::postContractClaim);

        // Multisig endpoints (Milestone 15c).
        app.post("/api/multisig/create", this::postMultiSigCreate);
        app.post("/api/multisig/claim", this::postMultiSigClaim);

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

    // ===== 14c: CONTRACTS =====

    /**
     * Deploys a contract: locks an amount behind a locking script. The deploy
     * enters the mempool as a transaction and propagates to peers; the contract
     * itself becomes OPEN once the deploy is mined.
     */
    private void postContract(Context ctx) {
        JsonObject body;
        try {
            body = JsonParser.parseString(ctx.body()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return;
        }

        if (!body.has("funder") || !body.has("amount") || !body.has("lockingScript")) {
            ctx.status(400);
            json(ctx, error("Contract requires funder, amount, and lockingScript"));
            return;
        }

        Transaction deploy = blockchain.deployContract(
                body.get("funder").getAsString(),
                body.get("lockingScript").getAsString(),
                body.get("amount").getAsDouble());

        if (deploy == null) {
            ctx.status(400);
            json(ctx, error("Contract deploy rejected (invalid script or insufficient funds)"));
            return;
        }

        node.broadcastTransaction(deploy);
        ctx.status(201);

        // The contract id is the deploy recipient minus the address prefix.
        String contractId = deploy.getRecipient()
                .substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contractId", contractId);
        m.put("funder", deploy.getSender());
        m.put("amount", deploy.getAmount());
        m.put("lockingScript", deploy.getLockingScript());
        m.put("status", "PENDING"); // OPEN once the deploy is mined
        json(ctx, m);
    }

    /** Claims an OPEN contract with unlocking data. */
    private void postContractClaim(Context ctx) {
        String id = ctx.pathParam("id");

        JsonObject body;
        try {
            body = JsonParser.parseString(ctx.body()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return;
        }

        if (!body.has("claimer")) {
            ctx.status(400);
            json(ctx, error("Claim requires a claimer address"));
            return;
        }

        String unlockingScript = body.has("unlockingScript")
                ? body.get("unlockingScript").getAsString() : "";

        Transaction claim = blockchain.claimContract(
                id, body.get("claimer").getAsString(), unlockingScript);

        if (claim == null) {
            ctx.status(400);
            json(ctx, error("Claim rejected (unknown/claimed contract or script failed)"));
            return;
        }

        node.broadcastTransaction(claim);
        ctx.status(201);
        json(ctx, contractInfo(blockchain.getContract(id)));
    }

    /** Returns a single contract by id (404 if no deploy has been mined). */
    private void getContract(Context ctx) {
        Contract contract = blockchain.getContract(ctx.pathParam("id"));
        if (contract == null) {
            ctx.status(404);
            json(ctx, error("No contract with id " + ctx.pathParam("id")));
            return;
        }
        json(ctx, contractInfo(contract));
    }

    private List<Map<String, Object>> contractsList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Contract contract : blockchain.getContracts()) {
            list.add(contractInfo(contract));
        }
        return list;
    }

    /** Serializes a contract to a stable JSON shape for the UI. */
    private Map<String, Object> contractInfo(Contract contract) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contractId", contract.getContractId());
        m.put("funder", contract.getFunder());
        m.put("lockingScript", contract.getLockingScript());
        m.put("amount", contract.getAmount());
        m.put("creationHeight", contract.getCreationHeight());
        m.put("status", contract.getStatus().toString());
        m.put("claimer", contract.getClaimer());
        return m;
    }

    // ===== 15c: MULTISIG =====

    /**
     * Creates an M-of-N multisig wallet. The node generates the N member key
     * pairs, keeps the private keys server-side (see {@link #multisigSessions}),
     * and returns only public data: the wallet address, the member public keys,
     * and the CHECKMULTISIG locking script to deploy with.
     */
    private void postMultiSigCreate(Context ctx) {
        JsonObject body;
        try {
            body = JsonParser.parseString(ctx.body()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return;
        }

        if (!body.has("members") || !body.has("threshold")) {
            ctx.status(400);
            json(ctx, error("Multisig requires members (N) and threshold (M)"));
            return;
        }

        int n = body.get("members").getAsInt();
        int m = body.get("threshold").getAsInt();
        if (n < 1 || n > ScriptVM.MAX_KEYS || m < 1 || m > n) {
            ctx.status(400);
            json(ctx, error("Require 1 <= threshold <= members <= " + ScriptVM.MAX_KEYS));
            return;
        }

        // Generate the member key pairs; keep privates server-side, publish publics.
        List<PublicKey> publicKeys = new ArrayList<>();
        List<PrivateKey> privateKeys = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            KeyPair kp = generateKeyPair();
            publicKeys.add(kp.getPublic());
            privateKeys.add(kp.getPrivate());
        }

        MultiSigWallet wallet = MultiSigWallet.ofPublicKeys(publicKeys, m);
        multisigSessions.put(wallet.getLockingScript(),
                new MultiSigSession(wallet, privateKeys));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("address", wallet.getAddress());
        out.put("threshold", wallet.getThreshold());
        out.put("memberCount", wallet.getMemberCount());
        out.put("memberPublicKeys", wallet.getMemberPublicKeys());
        out.put("lockingScript", wallet.getLockingScript());
        ctx.status(201);
        json(ctx, out);
    }

    /**
     * Assembles and submits a claim against a multisig contract. Given the
     * contract id and the claimer, the node looks up the member keys it holds
     * for that contract's lock, signs the claim sighash with the first M of
     * them, and submits the assembled claim - the signing convenience noted on
     * {@link #multisigSessions}.
     */
    private void postMultiSigClaim(Context ctx) {
        JsonObject body;
        try {
            body = JsonParser.parseString(ctx.body()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return;
        }

        if (!body.has("contractId") || !body.has("claimer")) {
            ctx.status(400);
            json(ctx, error("Multisig claim requires contractId and claimer"));
            return;
        }

        String contractId = body.get("contractId").getAsString();
        String claimer = body.get("claimer").getAsString();

        Contract contract = blockchain.getContract(contractId);
        if (contract == null) {
            ctx.status(400);
            json(ctx, error("Unknown contract (the deploy may not be mined yet)"));
            return;
        }

        MultiSigSession session = multisigSessions.get(contract.getLockingScript());
        if (session == null) {
            ctx.status(400);
            json(ctx, error("No server-held keys for this contract's multisig"));
            return;
        }

        // Sign the claim sighash with the first M member keys, in member order.
        String sighash = contract.claimSighash(claimer);
        StringBuilder unlocking = new StringBuilder();
        for (int i = 0; i < session.wallet().getThreshold(); i++) {
            if (unlocking.length() > 0) unlocking.append(' ');
            unlocking.append("PUSH ").append(
                    SignatureUtil.sign(session.memberKeys().get(i), sighash));
        }

        Transaction claim = blockchain.claimContract(contractId, claimer, unlocking.toString());
        if (claim == null) {
            ctx.status(400);
            json(ctx, error("Multisig claim rejected (unknown/claimed contract or signatures failed)"));
            return;
        }

        node.broadcastTransaction(claim);
        ctx.status(201);
        json(ctx, contractInfo(contract));
    }

    /** Generates a secp256r1 key pair for a server-held multisig member. */
    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            keyGen.initialize(new ECGenParameterSpec("secp256r1"));
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key pair", e);
        }
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
        m.put("difficulty", BlockchainConfig.MINING_DIFFICULTY);
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
