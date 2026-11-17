package com.blocksmith.demo;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import com.blocksmith.api.ApiServer;
import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.core.Transaction;
import com.blocksmith.core.Wallet;
import com.blocksmith.network.NetworkConfig;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Records a session on a real node for the demo on the project site
 * (Sprint 28a).
 *
 * It starts two nodes, plays a fixed story against the first one's HTTP API
 * the way a browser does (prepare, sign with a {@link Wallet}, submit) and,
 * after every step, asks that node everything the dashboard can ask. The
 * answers, step by step, are the {@link DemoRecording}.
 *
 * THEORY: WHY THE RECORDER ASKS EVERYTHING
 *
 * A recording can only answer what was asked while it was made. A visitor
 * may open any block, any transaction, any address, at any moment of the
 * replay. So after every step the recorder walks the whole state: the fixed
 * paths (status, peers, mempool...), every page of blocks, every block by
 * height and by hash, every transaction, every address, every search that
 * finds something. For a chain of 20 blocks that is a few hundred requests
 * per step, answered by a local node in well under a second.
 *
 * The story is in {@link #record()}: transfers at different fees, a
 * hashlock contract, a 2-of-3 multisig, a fee raised on a waiting transfer,
 * a second node joining, and a reorganisation when that node comes back
 * from being offline with a longer branch.
 */
public final class DemoRecorder implements AutoCloseable {

    /** What the dashboard asks with: the block cards, a page of blocks, a page of an address's history. */
    public static final int DECK_BLOCKS = 6;
    public static final int BLOCK_PAGE = 20;
    public static final int HISTORY_PAGE = 25;

    /** The secret of the hashlock contract in the story. */
    public static final String SECRET = "open-sesame";

    /**
     * How fast the story is played.
     *
     * @param afterStepMs     wait after every step, so timestamps are apart
     * @param betweenBlocksMs the least time between two blocks
     */
    public record Pace(long afterStepMs, long betweenBlocksMs) {
        /** For tests: no waiting. */
        public static final Pace FAST = new Pace(0, 0);
        /** For the published demo: blocks a little under the 10 s target, like an impatient miner. */
        public static final Pace REAL = new Pace(2_500, 8_000);
    }

    /** The ports of the recorded node and of the second node. */
    public record Ports(int p2p, int api, int peerP2p, int peerApi) {
        public static final Ports DEFAULT = new Ports(NetworkConfig.DEFAULT_PORT, NetworkConfig.API_PORT,
                NetworkConfig.DEFAULT_PORT + 1, NetworkConfig.API_PORT + 1);
    }

    private final Pace pace;
    private final Ports ports;
    private final HttpClient http = HttpClient.newHttpClient();

    private final Blockchain chain;
    private final Node node;
    private final ApiServer api;

    private final Blockchain peerChain;
    private final String peerId;
    private Node peerNode;
    private ApiServer peerApi;

    private final Wallet alice = new Wallet();
    private final Wallet bob = new Wallet();
    private final Wallet carol = new Wallet();
    private final Wallet dave = new Wallet();
    private final Wallet[] members = {new Wallet(), new Wallet(), new Wallet()};

    private final List<DemoRecording.Frame> frames = new ArrayList<>();
    private long lastBlockAt;

    public DemoRecorder(ChainParams params, Pace pace, Ports ports) throws IOException {
        this.pace = pace;
        this.ports = ports;
        this.chain = new Blockchain(NoOpChainStore.INSTANCE, params);
        this.node = new Node(ports.p2p(), chain);
        node.start();
        this.api = new ApiServer(node, ports.api());
        api.start();
        this.peerChain = new Blockchain(NoOpChainStore.INSTANCE, params);
        this.peerNode = new Node(ports.peerP2p(), peerChain);
        this.peerId = peerNode.getNodeId();
    }

    /** The recorded node's chain (for checks after {@link #record()}). */
    public Blockchain chain() {
        return chain;
    }

    public int apiPort() {
        return ports.api();
    }

    public String aliceAddress() { return alice.getAddress(); }
    public String bobAddress() { return bob.getAddress(); }
    public String carolAddress() { return carol.getAddress(); }
    public String daveAddress() { return dave.getAddress(); }

    @Override
    public void close() {
        stopPeer();
        api.stop();
        node.stop();
    }

    // ===== the story =====

    /** Plays the story once and returns what the node answered after every step. */
    public DemoRecording record() throws Exception {
        final String a = alice.getAddress(), b = bob.getAddress(), c = carol.getAddress(), d = dave.getAddress();

        // Before the first frame: a chain worth opening, and long enough (with
        // what follows) to need a second page of blocks.
        for (int i = 0; i < 6; i++) mine(a);
        transfer(alice, b, 12, 0.1);
        mine(a);
        transfer(alice, c, 4, 0.1);
        transfer(alice, b, 6, 0.1);
        mine(a);
        transfer(alice, c, 8, 0.1);
        transfer(bob, c, 2, 0.05);
        mine(a);
        transfer(alice, c, 1, 0.02);
        transfer(bob, a, 3, 0.05);
        mine(a);
        frame("A node with " + (height() + 1) + " blocks",
                "A recording of a real BlockSmith node. Alice (" + brief(a) + ") mined these blocks and paid Bob and"
                + " Carol; hers is the active wallet. Everything you see was answered by the node.");

        transfer(alice, b, 25, 0.2);
        frame("Alice sends 25 BSC to Bob",
                "Signed with Alice's key, outside the node, then submitted. It waits in the mempool until a block"
                + " takes it.");

        mine(a);
        frame("Block #" + height() + " mined",
                "The transfer is in a block and Bob (" + brief(b) + ") has the coins. The miner, Alice, got the"
                + " reward and the 0.2 BSC fee.");

        transfer(alice, c, 10, 0.05);
        transfer(bob, c, 5, 0.6);
        transfer(alice, b, 3, 0.3);
        frame("Three transfers, three fees",
                "Sent in this order with fees 0.05, 0.6 and 0.3 BSC. Open Mempool: they are sorted by fee per"
                + " byte, which is the order a miner takes them in.");

        mine(a);
        frame("Block #" + height() + " takes the best fee first",
                "All three fit. Open the block: after the coinbase they are in fee order, 0.6, 0.3, 0.05.");

        String hashlock = "SHA256 PUSH " + HashUtil.applySha256(SECRET) + " EQUAL";
        String hashlockId = deploy(alice, hashlock, 20);
        frame("Alice locks 20 BSC behind a secret",
                "A hashlock contract: whoever shows the text whose SHA-256 is in the script may take the coins."
                + " The deploy waits for a block.");

        mine(a);
        frame("The contract is on the chain",
                "Block #" + height() + " holds the deploy. The 20 BSC now sit at the contract's own address"
                + " (Open contracts, on the right).");

        JsonObject claim = new JsonObject();
        claim.addProperty("claimer", b);
        claim.addProperty("unlockingScript", "PUSH " + SECRET);
        post(ports.api(), "/api/contracts/" + hashlockId + "/claim", claim, 201);
        frame("Bob claims it with the secret",
                "His claim carries the text \"" + SECRET + "\". The node ran the script, got true, and accepted"
                + " the claim into the mempool.");

        mine(a);
        frame("Contract claimed",
                "Block #" + height() + " pays the 20 BSC from the contract to Bob. Search for the contract's id"
                + " to see both of its transactions.");

        JsonObject wallet = new JsonObject();
        JsonArray keys = new JsonArray();
        for (Wallet m : members) keys.add(m.getPublicKeyHex());
        wallet.add("memberPublicKeys", keys);
        wallet.addProperty("threshold", 2);
        String multisigScript = post(ports.api(), "/api/multisig/create", wallet, 201).get("lockingScript").getAsString();
        String multisigId = deploy(alice, multisigScript, 30);
        frame("Alice funds a 2-of-3 multisig wallet",
                "30 BSC locked by three public keys; any two of their owners must sign to move the coins. The"
                + " node holds none of the keys.");

        mine(a);
        frame("The multisig contract is open", "Block #" + height() + ". Two signatures are still needed to spend it.");

        startPeer();
        connectPeer();
        frame("A second node connects",
                "It started with the genesis block only, asked this node for the rest and checked every block."
                + " Both now have the same chain (Peers, on the right).");

        waitForBlockSlot();
        post(ports.peerApi(), "/api/mine", minerBody(d), 201);
        await(this::sameTip, "the second node's block to arrive");
        lastBlockAt = System.currentTimeMillis();
        frame("Block #" + height() + " arrives from the other node",
                "Mined there by Dave (" + brief(d) + ") and sent over the network. This node verified the proof of"
                + " work and every transaction before adding it.");

        JsonObject ask = new JsonObject();
        ask.addProperty("contractId", multisigId);
        ask.addProperty("claimer", c);
        String message = post(ports.api(), "/api/multisig/claim/prepare", ask, 200).get("message").getAsString();
        JsonArray signatures = new JsonArray();
        for (Wallet m : new Wallet[]{members[0], members[2]}) {
            JsonObject s = new JsonObject();
            s.addProperty("publicKey", m.getPublicKeyHex());
            s.addProperty("signature", m.signData(message));
            signatures.add(s);
        }
        ask.add("signatures", signatures);
        post(ports.api(), "/api/multisig/claim", ask, 201);
        frame("Two of three members sign",
                "Members 1 and 3 each signed \"pay this contract to Carol\" on their own device. Two signatures"
                + " reach the threshold; the claim waits for a block.");

        mine(a);
        frame("Multisig claimed", "Block #" + height() + ": Carol (" + brief(c) + ") received the 30 BSC.");

        String slow = transfer(alice, c, 2, 0.01);
        frame("A transfer with a small fee",
                "Alice pays Carol 2 BSC with a 0.01 BSC fee. On a busy chain it could wait a long time.");

        JsonObject bump = new JsonObject();
        bump.addProperty("fee", 0.5);
        submit(post(ports.api(), "/api/transactions/" + slow + "/bump/prepare", bump, 201), alice);
        frame("Fee raised: the transfer is replaced",
                "The same transfer with the same sequence number and a 0.5 BSC fee, signed again. The mempool"
                + " keeps the better-paying one and drops the first.");

        mine(a);
        frame("Block #" + height() + " mined", "The replacement is confirmed; the first version never reached a block.");

        stopPeer();
        await(() -> node.getPeerManager().getConnectedCount() == 0, "the second node to drop");
        frame("The second node goes offline", "Its connection is gone: 0 peers. Each node now builds on its own.");

        transfer(alice, b, 1, 0.1);
        mine(a);
        String lonely = chain.getLatestBlock().getHash();
        frame("Block #" + height() + " mined here, alone",
                "It holds a 1 BSC transfer from Alice to Bob. The other node does not know about it.");

        // Meanwhile the other node mines two blocks at the same height and above.
        peerChain.minePendingTransactions(d);
        peerChain.minePendingTransactions(d);
        startPeer();
        connectPeer();
        await(() -> sameTip() && chain.getReorgCount() > 0, "the reorganisation");
        lastBlockAt = System.currentTimeMillis();
        frame("Chain reorganised",
                "The other node came back with two blocks of its own: more work than this node's one. This node"
                + " switched branches. Its block " + brief(lonely) + " was dropped and the 1 BSC transfer is"
                + " waiting in the mempool again.");

        mine(a);
        await(this::sameTip, "the last block to reach the second node");
        frame("Block #" + height() + ": confirmed again",
                "Nothing was lost: the transfer went back to the mempool and into the next block. This is the end"
                + " of the recording; every block, transaction and address stays open in the Explorer.");

        return new DemoRecording(System.currentTimeMillis(), a, misses(), List.copyOf(frames));
    }

    // ===== steps =====

    private int height() {
        return chain.getLatestBlock().getIndex();
    }

    private boolean sameTip() {
        return chain.getLatestBlock().getHash().equals(peerChain.getLatestBlock().getHash());
    }

    private static JsonObject minerBody(String address) {
        JsonObject body = new JsonObject();
        body.addProperty("minerAddress", address);
        return body;
    }

    private void mine(String miner) throws Exception {
        waitForBlockSlot();
        post(ports.api(), "/api/mine", minerBody(miner), 201);
        lastBlockAt = System.currentTimeMillis();
    }

    private void waitForBlockSlot() throws InterruptedException {
        long wait = lastBlockAt + pace.betweenBlocksMs() - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
    }

    /** Prepare on the node, sign here, submit: what the dashboard does with a browser key. Returns the id. */
    private String transfer(Wallet from, String to, double amount, double fee) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("sender", from.getAddress());
        body.addProperty("recipient", to);
        body.addProperty("amount", amount);
        body.addProperty("fee", fee);
        return submit(post(ports.api(), "/api/transactions/prepare", body, 201), from);
    }

    /** Deploys a contract funded by {@code funder}; returns the contract id. */
    private String deploy(Wallet funder, String lockingScript, double amount) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("funder", funder.getAddress());
        body.addProperty("amount", amount);
        body.addProperty("lockingScript", lockingScript);
        JsonObject template = post(ports.api(), "/api/contracts/prepare", body, 201);
        submit(template, funder);
        return template.get("contractId").getAsString();
    }

    private String submit(JsonObject template, Wallet signer) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("templateId", template.get("templateId").getAsString());
        body.addProperty("signature", signer.signData(template.get("signatureData").getAsString()));
        body.addProperty("publicKey", signer.getPublicKeyHex());
        return post(ports.api(), "/api/transactions", body, 201).get("transactionId").getAsString();
    }

    private void startPeer() throws IOException {
        if (peerNode == null) peerNode = new Node(ports.peerP2p(), peerChain, peerId);
        peerNode.start();
        peerApi = new ApiServer(peerNode, ports.peerApi());
        peerApi.start();
    }

    private void stopPeer() {
        if (peerApi != null) peerApi.stop();
        if (peerNode != null) peerNode.stop();
        peerApi = null;
        peerNode = null;
    }

    /** The recorded node dials the second one, like the dashboard's Connect form; then both sync. */
    private void connectPeer() throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("address", "localhost:" + ports.peerP2p());
        HttpResponse<String> res = send(ports.api(), "/api/network/peers", body.toString());
        if (res.statusCode() != 201 && res.statusCode() != 409) {     // 409: its own redial got there first
            throw new IllegalStateException("Connecting to the second node: " + res.statusCode() + " " + res.body());
        }
        await(() -> node.getPeerManager().getConnectedCount() == 1 && sameTip(), "the two nodes to agree");
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Timed out waiting for " + what);
            Thread.sleep(50);
        }
    }

    private static String brief(String hash) {
        return hash.length() <= 14 ? hash : hash.substring(0, 8) + "…" + hash.substring(hash.length() - 4);
    }

    // ===== recording =====

    private void frame(String title, String detail) throws Exception {
        if (pace.afterStepMs() > 0) Thread.sleep(pace.afterStepMs());
        frames.add(new DemoRecording.Frame(title, detail, System.currentTimeMillis(), everyAnswer()));
        System.out.println("[demo] frame " + frames.size() + ": " + title);
    }

    /** Everything the dashboard can ask the node right now: decoded path -> answer. */
    private Map<String, JsonElement> everyAnswer() throws Exception {
        Map<String, JsonElement> out = new LinkedHashMap<>();
        for (String path : List.of("/api/network/status", "/api/network/peers", "/api/contracts", "/api/mempool",
                "/api/fees/estimate", "/api/explorer/stats", "/api/explorer/blocks?limit=" + DECK_BLOCKS)) {
            ask(out, path, path);
        }
        String page = "/api/explorer/blocks?limit=" + BLOCK_PAGE;
        while (page != null) {
            JsonObject p = ask(out, page, page).getAsJsonObject();
            JsonElement next = p.get("nextBefore");
            page = next == null || next.isJsonNull() ? null
                    : "/api/explorer/blocks?limit=" + BLOCK_PAGE + "&before=" + next.getAsInt();
        }

        List<Transaction> transactions = new ArrayList<>();
        for (Block block : chain.getChain()) {
            ask(out, "/api/blocks/" + block.getIndex(), null);
            ask(out, "/api/blocks/hash/" + block.getHash(), null);
            search(out, String.valueOf(block.getIndex()));
            search(out, block.getHash());
            transactions.addAll(block.getTransactions());
        }
        transactions.addAll(chain.getPendingTransactions());

        Set<String> addresses = new LinkedHashSet<>();
        for (Wallet w : new Wallet[]{alice, bob, carol, dave}) addresses.add(w.getAddress());
        for (Transaction tx : transactions) {
            ask(out, "/api/transactions/" + tx.getTransactionId(), null);
            search(out, tx.getTransactionId());
            if (tx.getSender() != null) addresses.add(tx.getSender());
            if (tx.getRecipient() != null) addresses.add(tx.getRecipient());
        }
        for (JsonElement contract : out.get("/api/contracts").getAsJsonArray()) {
            String id = contract.getAsJsonObject().get("contractId").getAsString();
            search(out, id);
            addresses.add(BlockchainConfig.CONTRACT_ADDRESS_PREFIX + id);
        }
        for (String address : addresses) {
            ask(out, "/api/wallet/" + address, "/api/wallet/" + encode(address));
            search(out, address);
            int offset = 0, total;
            do {
                String query = "?limit=" + HISTORY_PAGE + "&offset=" + offset;
                JsonObject view = ask(out, "/api/explorer/address/" + address + query,
                        "/api/explorer/address/" + encode(address) + query).getAsJsonObject();
                total = view.get("transactionCount").getAsInt() + view.get("pendingCount").getAsInt();
                offset += HISTORY_PAGE;
            } while (offset < total);
        }
        return out;
    }

    /** Asks the node and keeps a 200 under {@code key}; {@code url} is the path as sent (null: the same). */
    private JsonElement ask(Map<String, JsonElement> out, String key, String url) throws Exception {
        HttpResponse<String> res = get(url == null ? key : url);
        if (res.statusCode() != 200) {
            throw new IllegalStateException(key + " -> " + res.statusCode() + " " + res.body());
        }
        JsonElement body = JsonParser.parseString(res.body());
        out.put(key, body);
        return body;
    }

    /** A search is kept only when it finds something: an address nobody used yet is a 404. */
    private void search(Map<String, JsonElement> out, String query) throws Exception {
        HttpResponse<String> res = get("/api/explorer/search?q=" + encode(query));
        if (res.statusCode() == 200) out.put("/api/explorer/search?q=" + query, JsonParser.parseString(res.body()));
    }

    /** The node's own words for something it does not have, by the start of the path. */
    private Map<String, DemoRecording.Miss> misses() throws Exception {
        Map<String, String> probes = new LinkedHashMap<>();
        probes.put("/api/explorer/search?q=", "/api/explorer/search?q=nothing-like-this");
        probes.put("/api/blocks/hash/", "/api/blocks/hash/" + "0".repeat(64));
        probes.put("/api/blocks/", "/api/blocks/999999");
        probes.put("/api/transactions/", "/api/transactions/" + "0".repeat(64));
        Map<String, DemoRecording.Miss> out = new LinkedHashMap<>();
        for (var probe : probes.entrySet()) {
            HttpResponse<String> res = get(probe.getValue());
            if (res.statusCode() == 200) throw new IllegalStateException(probe.getValue() + " was found");
            out.put(probe.getKey(), new DemoRecording.Miss(res.statusCode(), JsonParser.parseString(res.body())));
        }
        return out;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + ports.api() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(int port, String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject post(int port, String path, JsonObject body, int expected) throws Exception {
        HttpResponse<String> res = send(port, path, body.toString());
        if (res.statusCode() != expected) {
            throw new IllegalStateException("POST " + path + " -> " + res.statusCode() + " " + res.body());
        }
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    /**
     * Records the published demo: default parameters, real pace (about four
     * minutes). Writes the recording script to {@code args[0]} (default
     * {@code target/demo/recording.js}).
     */
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/demo/recording.js");
        DemoRecording recording;
        try (DemoRecorder recorder = new DemoRecorder(ChainParams.DEFAULT, Pace.REAL, Ports.DEFAULT)) {
            recording = recorder.record();
        }
        if (out.getParent() != null) Files.createDirectories(out.getParent());
        String script = recording.toScript();
        Files.writeString(out, script, StandardCharsets.UTF_8);
        System.out.println("[demo] " + recording.frames().size() + " frames, " + script.length() + " characters -> "
                + out.toAbsolutePath());
        System.exit(0);
    }
}
