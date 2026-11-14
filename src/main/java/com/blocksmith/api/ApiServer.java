package com.blocksmith.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.blocksmith.contract.Contract;
import com.blocksmith.contract.ContractStatus;
import com.blocksmith.contract.MultiSigWallet;
import com.blocksmith.contract.ScriptVM;
import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.core.Wallet;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.SignatureUtil;
import com.blocksmith.util.Target;
import com.blocksmith.network.NetworkConfig;
import com.blocksmith.network.Node;
import com.blocksmith.network.PeerInfo;
import com.blocksmith.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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
    // Shared chain-data Gson: blocks may hold signed transactions, whose
    // PublicKey plain Gson cannot serialize (#259).
    private final Gson gson = JsonUtil.GSON;

    private Javalin app;

    /** Where POST /api/admin/backup writes; null = backups disabled (Sprint 16d). */
    private java.nio.file.Path backupDirectory;

    public ApiServer(Node node) {
        this(node, NetworkConfig.API_PORT);
    }

    public ApiServer(Node node, int port) {
        this.node = node;
        this.blockchain = node.getBlockchain();
        this.port = port;
    }

    /**
     * Enables {@code POST /api/admin/backup}, writing backups into
     * {@code directory}. Without this the endpoint answers 503.
     */
    public void enableBackups(java.nio.file.Path directory) {
        this.backupDirectory = directory;
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
        app.get("/api/blocks", ctx -> {
            JsonArray blocks = new JsonArray();
            for (Block b : blockchain.getChain()) blocks.add(blockJson(b));
            json(ctx, blocks);
        });
        app.get("/api/blocks/latest", ctx -> json(ctx, blockJson(blockchain.getLatestBlock())));
        app.get("/api/blocks/{index}", this::getBlockByIndex);
        app.get("/api/blocks/hash/{hash}", this::getBlockByHash);
        app.get("/api/network/status", ctx -> json(ctx, status()));
        app.get("/api/network/difficulty", ctx -> json(ctx, blockchain.atomically(this::difficulty)));

        // Transaction + mining endpoints (Milestone 12b).
        app.post("/api/transactions/prepare", this::postTransactionPrepare);
        app.post("/api/transactions", this::postTransaction);
        app.get("/api/transactions/{id}", this::getTransactionById);
        app.post("/api/mine", this::postMine);
        app.get("/api/mempool", ctx -> json(ctx, blockchain.atomically(this::mempoolView)));
        app.post("/api/transactions/{id}/bump", this::postBump);
        app.post("/api/transactions/{id}/bump/prepare", this::postBumpPrepare);
        app.get("/api/fees/estimate", ctx -> json(ctx, blockchain.atomically(this::feeEstimate)));

        // Wallet + network endpoints (Milestone 12c).
        app.get("/api/wallet/{address}", this::getWallet);
        app.get("/api/wallet/{address}/transactions", this::getWalletTransactions);
        app.post("/api/wallet/create", this::postWalletCreate);
        app.get("/api/network/peers", this::getPeers);

        // Contract endpoints (Milestone 14c).
        app.get("/api/contracts", ctx -> json(ctx, blockchain.atomically(this::contractsList)));
        app.get("/api/contracts/{id}", this::getContract);
        app.post("/api/contracts/prepare", this::postContractPrepare);
        app.post("/api/contracts", this::postContract);
        app.post("/api/contracts/{id}/claim", this::postContractClaim);

        // Multisig endpoints (Milestone 15c).
        app.post("/api/multisig/create", this::postMultiSigCreate);
        app.post("/api/multisig/claim/prepare", this::postMultiSigClaimPrepare);
        app.post("/api/multisig/claim", this::postMultiSigClaim);

        // Admin endpoints (Milestone 16d).
        app.post("/api/admin/backup", this::postBackup);

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

        // Signed submission of a prepared transaction (Sprint 22b).
        if (body.has("templateId")) {
            submitSigned(ctx, body);
            return;
        }

        if (!body.has("sender") || !body.has("recipient") || !body.has("amount")) {
            ctx.status(400);
            json(ctx, error("Transaction requires sender, recipient, and amount"));
            return;
        }

        // Optional fee (Sprint 18c/18d): paid by the sender on top of the
        // amount, collected by whoever mines the transaction.
        double fee = body.has("fee") && !body.get("fee").isJsonNull() ? parseFee(body.get("fee")) : 0;
        if (Double.isNaN(fee)) {
            ctx.status(400);
            json(ctx, error("fee must be a non-negative number"));
            return;
        }

        // Build the transaction through its constructor so the id and timestamp
        // are computed correctly (Gson would bypass the constructor).
        // Every API transaction gets a random sequence (Sprint 19d), so it
        // can be bumped later with POST /api/transactions/{id}/bump.
        Transaction tx = new Transaction(
                body.get("sender").getAsString(),
                body.get("recipient").getAsString(),
                body.get("amount").getAsDouble(),
                fee,
                Transaction.randomSequence());

        String why = blockchain.submitTransaction(tx);
        if (why != null) {
            ctx.status(400);
            json(ctx, error("Transaction rejected: " + why));
            return;
        }

        node.broadcastTransaction(tx);
        ctx.status(201);
        json(ctx, tx);
    }

    /**
     * Looks up a transaction by id in the mempool or in a mined block (via the
     * store's index when there is one). The transaction's own fields stay at
     * the top level; {@code status}, {@code blockIndex} and
     * {@code confirmations} are added next to them.
     */
    private void getTransactionById(Context ctx) {
        String id = ctx.pathParam("id");
        Blockchain.TransactionLookup found = blockchain.findTransaction(id);
        if (found == null) {
            ctx.status(404);
            json(ctx, error("No transaction with id " + id));
            return;
        }
        json(ctx, lookupJson(found));
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
        json(ctx, blockJson(mined));
    }

    // ===== 12c: WALLET + NETWORK =====

    /** Reports an address's confirmed balance and its pending outgoing total. */
    private void getWallet(Context ctx) {
        String address = ctx.pathParam("address");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("address", address);
        // One snapshot (Sprint 20c): otherwise a block landing between the
        // two reads could count a payment in both, or in neither.
        blockchain.atomically(() -> {
            m.put("balance", blockchain.getBalance(address));
            // Amount + fee: both leave the balance (was amount only).
            m.put("pendingOutgoing", blockchain.getPendingOutgoing(address));
            return null;
        });
        json(ctx, m);
    }

    /**
     * Creates a fresh wallet and returns its address ONLY. A real node must
     * never expose the private key over the API - the key stays with the owner;
     * here we simply never serialize it.
     */
    private void postWalletCreate(Context ctx) {
        // Retired (Sprint 22c): a key pair made on the node is not the
        // user's key. It used to be thrown away, so the address could never
        // spend. Wallets are created where their owner keeps the key (the
        // dashboard: Web Crypto in the browser).
        ctx.status(410);
        json(ctx, error("Retired: create the key pair in your own wallet (the dashboard does it in the browser);"
                + " the node never holds private keys"));
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

        Transaction deploy = Blockchain.buildDeploy(
                body.get("funder").getAsString(),
                body.get("lockingScript").getAsString(),
                body.get("amount").getAsDouble());

        // Unsigned: only on a chain without the signature rule (Sprint 22b);
        // otherwise use POST /api/contracts/prepare and sign.
        String why = blockchain.submitTransaction(deploy);
        if (why != null) {
            ctx.status(400);
            json(ctx, error("Contract deploy rejected: " + why));
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
        // A Contract's status changes when a block claims it; read it under
        // the chain's lock so the fields are seen together (Sprint 20c).
        return blockchain.atomically(() -> contractFields(contract));
    }

    private Map<String, Object> contractFields(Contract contract) {
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

    // ===== 15c / 23a: MULTISIG =====
    //
    // THEORY: the node coordinates, the members sign (Sprint 23a)
    //
    // Sprint 15 had the node make the member keys and sign claims itself, so
    // anyone who could reach the API could claim any multisig. Now:
    //   1. create:  from the members' PUBLIC keys -> locking script + address
    //   2. prepare: the claim message (sighash = SHA-256(contractId + claimer
    //               + amount)), the members in script order, the threshold
    //   3. members sign the message wherever their keys are
    //   4. claim:   {publicKey, signature} pairs; the node orders them by
    //               member position (CHECKMULTISIG matches left to right),
    //               builds the unlocking script, and the VM decides
    // The message names the claimer, so signatures collected for one claimer
    // are worthless for another. Bitcoin's PSBT (BIP174) is the same round trip.

    /** POST /api/multisig/create {memberPublicKeys: [hex X.509], threshold}. */
    private void postMultiSigCreate(Context ctx) {
        JsonObject body = jsonBody(ctx);
        if (body == null) return;
        if (body.has("members") && !body.has("memberPublicKeys")) {
            ctx.status(400);
            json(ctx, error("The node no longer makes member keys (Sprint 23): send memberPublicKeys (hex) and threshold"));
            return;
        }
        if (!body.has("memberPublicKeys") || !body.get("memberPublicKeys").isJsonArray() || !body.has("threshold")) {
            ctx.status(400);
            json(ctx, error("Multisig requires memberPublicKeys (hex X.509 EC keys) and threshold (M)"));
            return;
        }
        List<String> keys = new ArrayList<>();
        for (var k : body.getAsJsonArray("memberPublicKeys")) {
            String hex = k.getAsString().trim().toLowerCase();
            try {
                SignatureUtil.hexToPublicKey(hex);
            } catch (Exception e) {
                ctx.status(400);
                json(ctx, error("Not a hex X.509 EC public key: " + shortKey(hex)));
                return;
            }
            if (keys.contains(hex)) {
                ctx.status(400);
                json(ctx, error("The same member key twice: " + shortKey(hex)));
                return;
            }
            keys.add(hex);
        }
        int m = body.get("threshold").getAsInt();
        if (keys.isEmpty() || keys.size() > ScriptVM.MAX_KEYS || m < 1 || m > keys.size()) {
            ctx.status(400);
            json(ctx, error("Require 1 <= threshold <= members <= " + ScriptVM.MAX_KEYS));
            return;
        }

        MultiSigWallet wallet = MultiSigWallet.ofHex(keys, m);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("address", wallet.getAddress());
        out.put("threshold", wallet.getThreshold());
        out.put("memberCount", wallet.getMemberCount());
        out.put("memberPublicKeys", wallet.getMemberPublicKeys());
        out.put("lockingScript", wallet.getLockingScript());
        ctx.status(201);
        json(ctx, out);
    }

    /** POST /api/multisig/claim/prepare {contractId, claimer}: what the members must sign. */
    private void postMultiSigClaimPrepare(Context ctx) {
        JsonObject body = jsonBody(ctx);
        if (body == null) return;
        if (!body.has("contractId") || !body.has("claimer")) {
            ctx.status(400);
            json(ctx, error("Multisig claim requires contractId and claimer"));
            return;
        }
        String contractId = body.get("contractId").getAsString();
        String claimer = body.get("claimer").getAsString();
        Map<String, Object> out = blockchain.atomically(() -> {
            Contract contract = blockchain.getContract(contractId);
            MultiSigWallet wallet = contract == null ? null : MultiSigWallet.fromLockingScript(contract.getLockingScript());
            if (wallet == null || contract.getStatus() != ContractStatus.OPEN) return null;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("contractId", contractId);
            m.put("claimer", claimer);
            m.put("amount", contract.getAmount());
            m.put("message", contract.claimSighash(claimer));
            m.put("threshold", wallet.getThreshold());
            m.put("memberPublicKeys", wallet.getMemberPublicKeys());
            return m;
        });
        if (out == null) {
            ctx.status(400);
            json(ctx, error("Not an open multisig contract (the deploy may not be mined yet, or it is claimed)"));
            return;
        }
        json(ctx, out);
    }

    /**
     * POST /api/multisig/claim {contractId, claimer, signatures: [{publicKey, signature}]}:
     * the node orders the members' signatures, builds the unlocking script
     * and submits the claim. It signs nothing itself.
     */
    private void postMultiSigClaim(Context ctx) {
        JsonObject body = jsonBody(ctx);
        if (body == null) return;
        if (!body.has("contractId") || !body.has("claimer")) {
            ctx.status(400);
            json(ctx, error("Multisig claim requires contractId and claimer"));
            return;
        }
        String contractId = body.get("contractId").getAsString();
        String claimer = body.get("claimer").getAsString();
        if (!body.has("signatures") || !body.get("signatures").isJsonArray()
                || body.getAsJsonArray("signatures").isEmpty()) {
            ctx.status(400);
            json(ctx, error("signatures required: M member signatures over the claim message"
                    + " (POST /api/multisig/claim/prepare gives the message)"));
            return;
        }

        Contract contract = blockchain.getContract(contractId);
        MultiSigWallet wallet = contract == null ? null : MultiSigWallet.fromLockingScript(contract.getLockingScript());
        if (wallet == null) {
            ctx.status(400);
            json(ctx, error("Not a multisig contract we know (the deploy may not be mined yet)"));
            return;
        }
        String message = contract.claimSighash(claimer);
        List<String> members = wallet.getMemberPublicKeys();

        // Each signature at its member's position; checked here so the
        // caller learns WHICH one is wrong (the VM would only say "invalid").
        String[] byMember = new String[members.size()];
        int count = 0;
        for (var item : body.getAsJsonArray("signatures")) {
            JsonObject pair = item.getAsJsonObject();
            String key = pair.has("publicKey") ? pair.get("publicKey").getAsString().trim().toLowerCase() : "";
            String sig = pair.has("signature") ? pair.get("signature").getAsString().trim() : "";
            int position = members.indexOf(key);
            if (position < 0) {
                ctx.status(400);
                json(ctx, error("Not a member of this multisig: " + shortKey(key)));
                return;
            }
            if (byMember[position] != null) {
                ctx.status(400);
                json(ctx, error("Two signatures from member " + (position + 1)));
                return;
            }
            if (!SignatureUtil.verify(key, sig, message)) {
                ctx.status(400);
                json(ctx, error("Member " + (position + 1) + "'s signature does not verify for this claim"
                        + " (made for another claimer or message?)"));
                return;
            }
            byMember[position] = sig;
            count++;
        }
        if (count < wallet.getThreshold()) {
            ctx.status(400);
            json(ctx, error("Need " + wallet.getThreshold() + " member signatures, got " + count));
            return;
        }

        // Exactly M signatures, in member order: CHECKMULTISIG pops M of them.
        StringBuilder unlocking = new StringBuilder();
        int used = 0;
        for (String sig : byMember) {
            if (sig == null || used == wallet.getThreshold()) continue;
            if (unlocking.length() > 0) unlocking.append(' ');
            unlocking.append("PUSH ").append(sig);
            used++;
        }

        Transaction claim = blockchain.claimContract(contractId, claimer, unlocking.toString());
        if (claim == null) {
            ctx.status(400);
            json(ctx, error("Multisig claim rejected (the contract may be claimed already)"));
            return;
        }
        node.broadcastTransaction(claim);
        ctx.status(201);
        json(ctx, contractInfo(contract));
    }

    private static String shortKey(String hex) {
        return hex.length() <= 16 ? hex : hex.substring(0, 8) + "..." + hex.substring(hex.length() - 8);
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
        json(ctx, blockJson(blockchain.getBlock(index)));
    }

    /** Finds a block by hash (16c); 404 if no block on our chain has it. */
    private void getBlockByHash(Context ctx) {
        String hash = ctx.pathParam("hash");
        Block block = blockchain.getBlockByHash(hash);
        if (block == null) {
            ctx.status(404);
            json(ctx, error("No block with hash " + hash));
            return;
        }
        json(ctx, blockJson(block));
    }

    /**
     * Transaction history of an address (16c): pending first, then confirmed
     * from the newest block back, each with status and confirmations. An
     * address with no transactions gets an empty list (not a 404), like a
     * fresh wallet's zero balance.
     */
    private void getWalletTransactions(Context ctx) {
        JsonArray out = new JsonArray();
        for (Blockchain.TransactionLookup l : blockchain.getTransactionHistory(ctx.pathParam("address"))) {
            out.add(lookupJson(l));
        }
        json(ctx, out);
    }

    /** A transaction's JSON plus where it sits on the chain. */
    private JsonObject lookupJson(Blockchain.TransactionLookup lookup) {
        JsonObject o = gson.toJsonTree(lookup.transaction()).getAsJsonObject();
        o.addProperty("status", lookup.isConfirmed() ? "confirmed" : "pending");
        if (lookup.isConfirmed()) o.addProperty("blockIndex", lookup.blockIndex());
        o.addProperty("confirmations", lookup.confirmations());
        return o;
    }

    // ===== HELPERS =====

    /** A snapshot of node/chain state for GET /api/network/status. */
    private Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodeId", node.getNodeId());
        m.put("p2pPort", node.getPort());
        m.put("apiPort", port);
        // Chain values from ONE snapshot (Sprint 20c): read one by one, a
        // block landing in between could pair a new height with the old
        // target or countdown. Peer data is read outside the chain's lock
        // (the peer manager has its own; never hold both).
        blockchain.atomically(() -> {
            m.put("chainLength", blockchain.getChainSize());
            // Live difficulty (Sprint 17d): the target the next block must meet,
            // shown as its leading-zero equivalent (4.0 = four zeros, 4.25 = 2x harder).
            java.math.BigInteger next = blockchain.getNextTarget();
            m.put("difficulty", Target.toDifficulty(next));
            m.put("target", Target.toHex(next));
            m.put("targetBlockTimeMs", blockchain.getChainParams().targetBlockTimeMs());
            m.put("retargetInterval", blockchain.getChainParams().retargetInterval());
            m.put("blocksUntilRetarget", blockchain.getBlocksUntilRetarget());
            // Block limits (Sprint 18b/18d)
            m.put("maxBlockBytes", blockchain.getChainParams().maxBlockBytes());
            m.put("maxBlockTransactions", blockchain.getChainParams().maxBlockTransactions());
            Long avg = blockchain.getAverageBlockTimeMs();
            if (avg != null) m.put("averageBlockTimeMs", avg);
            m.put("pendingTransactions", blockchain.getPendingCount());
            // Fork choice (Sprint 21c). Work is a 256-bit number: sent as a
            // decimal string, since JavaScript numbers would round it.
            m.put("totalWork", blockchain.getTotalWork().toString());
            m.put("sideBlocks", blockchain.getSideBlockCount());
            m.put("reorgs", blockchain.getReorgCount());
            Blockchain.ReorgEvent r = blockchain.getLastReorg();
            if (r != null) {
                Map<String, Object> last = new LinkedHashMap<>();
                last.put("depth", r.depth());
                last.put("forkHeight", r.forkHeight());
                last.put("oldTip", r.oldTip());
                last.put("newTip", r.newTip());
                last.put("newHeight", r.newHeight());
                last.put("transactionsBack", r.transactionsBack());
                last.put("timestamp", r.timestamp());
                m.put("lastReorg", last);
            }
            return null;
        });
        m.put("connectedPeers", node.getPeerManager().getConnectedPeers().size());
        return m;
    }

    /**
     * Difficulty history for GET /api/network/difficulty (Sprint 17d): the
     * network's rules, the current target, and one entry per retarget epoch
     * with the window that produced it (raw timespans, before the 4x clamp).
     */
    private Map<String, Object> difficulty() {
        Map<String, Object> m = new LinkedHashMap<>();
        var params = blockchain.getChainParams();
        m.put("targetBlockTimeMs", params.targetBlockTimeMs());
        m.put("retargetInterval", params.retargetInterval());
        m.put("maxAdjustmentFactor", params.maxAdjustmentFactor());
        m.put("currentDifficulty", Target.toDifficulty(blockchain.getNextTarget()));
        m.put("blocksUntilRetarget", blockchain.getBlocksUntilRetarget());
        List<Map<String, Object>> epochs = new ArrayList<>();
        for (Blockchain.DifficultyEpoch e : blockchain.getDifficultyHistory()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("startHeight", e.startHeight());
            row.put("difficulty", Target.toDifficulty(e.target()));
            row.put("target", Target.toHex(e.target()));
            if (e.windowFirst() != null) {
                row.put("windowFirst", e.windowFirst());
                row.put("windowLast", e.windowLast());
                row.put("actualTimespanMs", e.actualTimespanMs());
                row.put("expectedTimespanMs", e.expectedTimespanMs());
            }
            epochs.add(row);
        }
        m.put("epochs", epochs);
        return m;
    }

    /**
     * A block's JSON plus {@code sizeBytes} and {@code totalFees} (Sprint 18d).
     * The extra fields are for people; peers exchange the plain block.
     */
    private JsonObject blockJson(Block block) {
        JsonObject o = gson.toJsonTree(block).getAsJsonObject();
        double fees = 0;
        List<Transaction> txs = block.getTransactions();
        for (int i = 1; i < txs.size(); i++) fees += txs.get(i).getFee();
        o.addProperty("sizeBytes", block.sizeBytes());
        o.addProperty("totalFees", fees);
        return o;
    }

    /**
     * GET /api/mempool (Sprint 18d): pending transactions in the order a miner
     * considers them (best fee per byte first), each with its size and fee rate.
     */
    private Map<String, Object> mempoolView() {
        JsonArray txs = new JsonArray();
        double totalFees = 0;
        for (Transaction tx : blockchain.getPendingInMiningOrder()) {
            JsonObject o = gson.toJsonTree(tx).getAsJsonObject();
            o.addProperty("sizeBytes", tx.sizeBytes());
            o.addProperty("feeRate", tx.feeRate());
            // Bump info (Sprint 19d): the API can only re-build unsigned ones;
            // a signed tx must be bumped (re-signed) by its wallet.
            o.addProperty("replaceable", tx.isReplaceable());
            if (tx.isReplaceable()) o.addProperty("minBumpFee", blockchain.minimumBumpFee(tx));
            txs.add(o);
            totalFees += tx.getFee();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", txs.size());
        m.put("totalFees", totalFees);
        m.put("maxBlockTransactions", blockchain.getChainParams().maxBlockTransactions());
        m.put("maxBlockBytes", blockchain.getChainParams().maxBlockBytes());
        // Mempool policy (Sprint 19a/19d)
        m.put("bytes", blockchain.getPendingBytes());
        m.put("maxMempoolBytes", blockchain.getMempoolPolicy().maxBytes());
        m.put("maxMempoolTransactions", blockchain.getMempoolPolicy().maxTransactions());
        m.put("mempoolMinFeeRate", blockchain.getMempoolMinFeeRate());
        m.put("transactions", txs);
        return m;
    }

    /**
     * POST /api/transactions/{id}/bump (Sprint 19d): replace a pending
     * transaction with the same payment at a higher fee. Body: {@code {"fee": x}};
     * without a fee the smallest allowed bump is used.
     */
    private void postBump(Context ctx) {
        String id = ctx.pathParam("id");
        Transaction old = blockchain.getPendingTransaction(id);
        if (old == null) {
            ctx.status(404);
            json(ctx, error("No pending transaction " + id));
            return;
        }
        if (!old.isReplaceable()) {
            ctx.status(400);
            json(ctx, error("Transaction " + id + " is not replaceable (it has no sequence)"));
            return;
        }
        if (old.getSignature() != null) {
            ctx.status(400);
            json(ctx, error("A signed transaction is bumped by its sender: POST /api/transactions/"
                    + id + "/bump/prepare, sign, then POST /api/transactions"));
            return;
        }

        double fee;
        try {
            String raw = ctx.body();
            JsonObject body = raw == null || raw.isBlank() ? new JsonObject()
                    : JsonParser.parseString(raw).getAsJsonObject();
            fee = body.has("fee") && !body.get("fee").isJsonNull()
                    ? parseFee(body.get("fee")) : blockchain.minimumBumpFee(old);
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return;
        }
        if (Double.isNaN(fee)) {
            ctx.status(400);
            json(ctx, error("fee must be a non-negative number"));
            return;
        }

        Transaction bumped = old.withFee(fee);
        String why = blockchain.submitTransaction(bumped);
        if (why != null) {
            ctx.status(400);
            json(ctx, error("Bump rejected: " + why));
            return;
        }
        node.broadcastTransaction(bumped);
        JsonObject o = gson.toJsonTree(bumped).getAsJsonObject();
        o.addProperty("replaces", id);
        ctx.status(201);
        json(ctx, o);
    }

    /**
     * GET /api/fees/estimate (Sprint 19d): the fee rate for "next block",
     * "within 3" and "within 6", and what that costs on a typical dashboard
     * transaction. The typical size is measured on a real transaction with
     * 0x addresses, a fee and a sequence, rounded up to 0.0001 BSC.
     */
    private Map<String, Object> feeEstimate() {
        String address = "0x" + "0".repeat(40);
        int bytes = new Transaction(address, address, 1000.0, 1.2345, Long.MAX_VALUE).sizeBytes();
        Map<String, Object> m = new LinkedHashMap<>();
        String[][] targets = {{"fast", "1"}, {"normal", "3"}, {"slow", "6"}};
        for (String[] t : targets) {
            int blocks = Integer.parseInt(t[1]);
            double rate = blockchain.estimateFeeRate(blocks);
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("blocks", blocks);
            e.put("feeRate", rate);
            e.put("fee", Blockchain.roundUpFee(rate * bytes));
            m.put(t[0], e);
        }
        m.put("typicalTxBytes", bytes);
        m.put("mempoolMinFeeRate", blockchain.getMempoolMinFeeRate());
        return m;
    }

    // ===== 22b: PREPARE / SIGN / SUBMIT =====
    //
    // THEORY: the node builds, the owner signs
    //
    // The node holds no private keys, and a client cannot rebuild the exact
    // text the node verifies (Java writes the amount 10 as "10.0", JavaScript
    // as "10"; timestamp, fee and sequence must match to the character). So:
    //   1. prepare: the node builds the transaction and returns its exact
    //      signatureData plus a template id (the transaction id)
    //   2. sign:    the owner signs that text with their key, wherever it is
    //   3. submit:  {templateId, signature, publicKey}; the normal rules
    //               decide, including "the key must be the sender's"
    // A client should show what it signs: the node could prepare something
    // else, but it can never sign for the user.

    /** A prepared, unsigned transaction waiting for its signature. */
    private record Template(Transaction tx, long createdAt, String replaces) {}

    /** Most templates held at once; the oldest go first. */
    static final int MAX_TEMPLATES = 1_000;

    /** How long a template can be signed and submitted. */
    private volatile long templateTtlMs = 5 * 60_000;

    /** Tests shorten the template lifetime. */
    void setTemplateTtlMs(long ttlMs) {
        this.templateTtlMs = ttlMs;
    }

    private final Map<String, Template> templates = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Template> eldest) {
                    return size() > MAX_TEMPLATES;
                }
            });

    /** POST /api/transactions/prepare {sender, recipient, amount, fee?}: a transfer to sign. */
    private void postTransactionPrepare(Context ctx) {
        JsonObject body = jsonBody(ctx);
        if (body == null) return;
        if (!body.has("sender") || !body.has("recipient") || !body.has("amount")) {
            ctx.status(400);
            json(ctx, error("Transaction requires sender, recipient, and amount"));
            return;
        }
        double fee = body.has("fee") && !body.get("fee").isJsonNull() ? parseFee(body.get("fee")) : 0;
        if (Double.isNaN(fee)) {
            ctx.status(400);
            json(ctx, error("fee must be a non-negative number"));
            return;
        }
        Transaction tx = new Transaction(body.get("sender").getAsString(), body.get("recipient").getAsString(),
                body.get("amount").getAsDouble(), fee, Transaction.randomSequence());
        prepared(ctx, tx, null);
    }

    /** POST /api/contracts/prepare {funder, amount, lockingScript}: a contract deploy to sign. */
    private void postContractPrepare(Context ctx) {
        JsonObject body = jsonBody(ctx);
        if (body == null) return;
        if (!body.has("funder") || !body.has("amount") || !body.has("lockingScript")) {
            ctx.status(400);
            json(ctx, error("Contract requires funder, amount, and lockingScript"));
            return;
        }
        Transaction deploy = Blockchain.buildDeploy(body.get("funder").getAsString(),
                body.get("lockingScript").getAsString(), body.get("amount").getAsDouble());
        prepared(ctx, deploy, null);
    }

    /** POST /api/transactions/{id}/bump/prepare {fee?}: a new version of a pending payment to sign. */
    private void postBumpPrepare(Context ctx) {
        String id = ctx.pathParam("id");
        Transaction old = blockchain.getPendingTransaction(id);
        if (old == null) {
            ctx.status(404);
            json(ctx, error("No pending transaction " + id));
            return;
        }
        if (!old.isReplaceable()) {
            ctx.status(400);
            json(ctx, error("Transaction " + id + " is not replaceable (it has no sequence)"));
            return;
        }
        String raw = ctx.body();
        double fee;
        try {
            JsonObject body = raw == null || raw.isBlank() ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject();
            fee = body.has("fee") && !body.get("fee").isJsonNull() ? parseFee(body.get("fee")) : blockchain.minimumBumpFee(old);
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return;
        }
        if (Double.isNaN(fee)) {
            ctx.status(400);
            json(ctx, error("fee must be a non-negative number"));
            return;
        }
        prepared(ctx, old.withFee(fee), id);
    }

    /** Stores a template and answers with what the owner must sign. */
    private void prepared(Context ctx, Transaction tx, String replaces) {
        if (!tx.isValid()) {
            ctx.status(400);
            json(ctx, error("Invalid transaction (amount, fee, sender or recipient)"));
            return;
        }
        templates.put(tx.getTransactionId(), new Template(tx, System.currentTimeMillis(), replaces));
        JsonObject out = new JsonObject();
        out.addProperty("templateId", tx.getTransactionId());
        out.addProperty("signatureData", tx.getSignatureData());
        out.addProperty("expiresInMs", templateTtlMs);
        if (replaces != null) out.addProperty("replaces", replaces);
        if (tx.getRecipient().startsWith(BlockchainConfig.CONTRACT_ADDRESS_PREFIX)) {
            out.addProperty("contractId", tx.getRecipient().substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length()));
        }
        out.add("transaction", gson.toJsonTree(tx));
        ctx.status(201);
        json(ctx, out);
    }

    /** POST /api/transactions {templateId, signature, publicKey}: attach, then the rules decide. */
    private void submitSigned(Context ctx, JsonObject body) {
        String id = body.get("templateId").getAsString();
        Template t = templates.get(id);
        if (t == null || System.currentTimeMillis() - t.createdAt() > templateTtlMs) {
            templates.remove(id);
            ctx.status(404);
            json(ctx, error("Unknown or expired template " + id + ": prepare it again"));
            return;
        }
        if (!body.has("signature") || !body.has("publicKey")) {
            ctx.status(400);
            json(ctx, error("signature and publicKey (hex) are required"));
            return;
        }
        Transaction tx = t.tx();
        String why;
        synchronized (t) {
            try {
                tx.setSenderPublicKey(SignatureUtil.hexToPublicKey(body.get("publicKey").getAsString()));
                tx.setSignature(SignatureUtil.hexToBytes(body.get("signature").getAsString()));
            } catch (Exception e) {
                ctx.status(400);
                json(ctx, error("signature must be hex DER and publicKey hex X.509"));
                return;
            }
            why = blockchain.submitTransaction(tx);
        }
        if (why != null) {
            ctx.status(400);
            json(ctx, error("Transaction rejected: " + why));
            return;
        }
        templates.remove(id);
        node.broadcastTransaction(tx);
        JsonObject out = gson.toJsonTree(tx).getAsJsonObject();
        if (t.replaces() != null) out.addProperty("replaces", t.replaces());
        ctx.status(201);
        json(ctx, out);
    }

    /** The request body as a JSON object, or null after answering 400. */
    private JsonObject jsonBody(Context ctx) {
        try {
            return JsonParser.parseString(ctx.body()).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            ctx.status(400);
            json(ctx, error("Request body must be a JSON object"));
            return null;
        }
    }

    /** A fee from JSON, or NaN if it is not a non-negative finite number. */
    private static double parseFee(com.google.gson.JsonElement e) {
        try {
            double fee = e.getAsDouble();
            return fee >= 0 && !Double.isInfinite(fee) ? fee : Double.NaN;
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    private Map<String, String> error(String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", message);
        return m;
    }

    // ===== 16d: ADMIN =====

    /**
     * Takes an online backup of the node's database.
     *
     * Admin endpoints answer only requests from this machine (loopback): a
     * backup writes to the node's disk, and anyone on the network could
     * otherwise fill it. A real deployment would also require authentication.
     */
    private void postBackup(Context ctx) {
        if (!isLoopback(ctx.ip())) {
            ctx.status(403);
            json(ctx, error("Admin endpoints are only available from localhost"));
            return;
        }
        if (backupDirectory == null) {
            ctx.status(503);
            json(ctx, error("Backups are not enabled on this node"));
            return;
        }
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        java.nio.file.Path file = backupDirectory.resolve("chain-" + stamp + ".zip").toAbsolutePath();
        try {
            blockchain.backupTo(file);
        } catch (UnsupportedOperationException e) {
            ctx.status(503);
            json(ctx, error(e.getMessage()));
            return;
        } catch (RuntimeException e) {
            ctx.status(500);
            json(ctx, error("Backup failed: " + e.getMessage()));
            return;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("path", file.toString());
        try {
            m.put("sizeBytes", java.nio.file.Files.size(file));
        } catch (java.io.IOException e) {
            m.put("sizeBytes", -1);
        }
        m.put("chainLength", blockchain.getChainSize());
        ctx.status(201);
        json(ctx, m);
    }

    private static boolean isLoopback(String ip) {
        try {
            return java.net.InetAddress.getByName(ip).isLoopbackAddress();
        } catch (java.net.UnknownHostException e) {
            return false;
        }
    }

    /** Writes {@code data} as a JSON response using Gson. */
    private void json(Context ctx, Object data) {
        ctx.contentType("application/json");
        ctx.result(gson.toJson(data));
    }
}
