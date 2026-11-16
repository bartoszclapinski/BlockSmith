package com.blocksmith.api;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.core.Transaction;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.BlockchainConfig;
import com.blocksmith.util.HashUtil;
import com.blocksmith.util.Target;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 27c: the explorer end to end, the way a reader uses it. Whatever
 * was mined can be found by search and opened; the chain can be walked both
 * ways through the links a block page shows; a transaction is followed from
 * the mempool into a block; a contract's address shows the contract.
 */
@DisplayName("Explorer end to end: search everything, walk the chain, pending to confirmed, contracts (Sprint 27c)")
class ExplorerEndToEndTest {

    private static final ChainParams EASY = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS).withoutSignatureRule();

    private static final String MINER = "Miner1";
    private static final String SECRET = "open-sesame";
    private static final String HASHLOCK = "SHA256 PUSH " + HashUtil.applySha256(SECRET) + " EQUAL";

    private final HttpClient client = HttpClient.newHttpClient();
    private Blockchain chain;
    private Node node;
    private ApiServer api;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        chain = new Blockchain(NoOpChainStore.INSTANCE, EASY);
        node = new Node(freePort(), chain);
        port = freePort();
        api = new ApiServer(node, port);
        api.start();
    }

    @AfterEach
    void tearDown() {
        api.stop();
        node.stop();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject object(String path) throws Exception {
        HttpResponse<String> res = get(path);
        assertEquals(200, res.statusCode(), path + " -> " + res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private Transaction send(String from, String to, double amount, double fee) {
        Transaction tx = new Transaction(from, to, amount, fee);
        assertTrue(chain.addTransaction(tx));
        return tx;
    }

    /** What the page does with a search answer: the path of the thing to open. */
    private String open(String query) throws Exception {
        JsonObject found = object("/api/explorer/search?q=" + enc(query));
        switch (found.get("type").getAsString()) {
            case "block": return "/api/blocks/" + found.get("blockIndex").getAsInt();
            case "transaction": return "/api/transactions/" + found.get("transactionId").getAsString();
            default: return "/api/explorer/address/" + enc(found.get("address").getAsString());
        }
    }

    @Test
    @DisplayName("a block, a transfer, a deploy, an address and a contract are each found by search and open")
    void everythingMined_canBeFoundBySearch() throws Exception {
        chain.minePendingTransactions(MINER);
        Transaction transfer = send(MINER, "Alice", 10.0, 0.5);
        Transaction deploy = chain.deployContract(MINER, HASHLOCK, 20);
        chain.minePendingTransactions(MINER);
        String contractAddress = deploy.getRecipient();
        String contractId = contractAddress.substring(BlockchainConfig.CONTRACT_ADDRESS_PREFIX.length());

        JsonObject block = object(open(chain.getBlock(2).getHash()));
        assertEquals(2, block.get("index").getAsInt());
        assertEquals(3, block.get("transactionCount").getAsInt());

        JsonObject tx = object(open(transfer.getTransactionId()));
        assertEquals("Alice", tx.get("recipient").getAsString());
        assertEquals(chain.getBlock(2).getHash(), tx.get("blockHash").getAsString());

        JsonObject deployTx = object(open(deploy.getTransactionId()));
        assertEquals(contractAddress, deployTx.get("recipient").getAsString());
        assertEquals(HASHLOCK, deployTx.get("lockingScript").getAsString());

        JsonObject alice = object(open("Alice"));
        assertEquals(10.0, alice.get("balance").getAsDouble());
        assertFalse(alice.has("contract"), "an ordinary address has no contract");

        // The contract, by its id and by its address: the same page.
        for (String q : new String[]{contractId, contractAddress}) {
            JsonObject page = object(open(q));
            assertEquals(contractAddress, page.get("address").getAsString());
            assertEquals(20.0, page.get("balance").getAsDouble(), "the locked coins sit at the contract's address");
            JsonObject contract = page.getAsJsonObject("contract");
            assertEquals(contractId, contract.get("contractId").getAsString());
            assertEquals("OPEN", contract.get("status").getAsString());
            assertEquals(HASHLOCK, contract.get("lockingScript").getAsString());
            assertEquals(MINER, contract.get("funder").getAsString());
            assertEquals(2, contract.get("creationHeight").getAsInt());
            assertEquals(1, page.getAsJsonArray("transactions").size(), "the deploy");
        }

        // Claimed: the page shows who took it, and both transactions.
        assertNotNull(chain.claimContract(contractId, "Bob", "PUSH " + SECRET));
        chain.minePendingTransactions(MINER);
        JsonObject claimed = object(open(contractAddress));
        assertEquals("CLAIMED", claimed.getAsJsonObject("contract").get("status").getAsString());
        assertEquals("Bob", claimed.getAsJsonObject("contract").get("claimer").getAsString());
        assertEquals(0.0, claimed.get("balance").getAsDouble(), 1e-9);
        assertEquals(2, claimed.getAsJsonArray("transactions").size(), "the claim, then the deploy");
        assertEquals(20.0, object(open("Bob")).get("balance").getAsDouble(), 1e-9);

        // A CONTRACT: address nobody deployed is an empty address without a contract.
        JsonObject ghost = object("/api/explorer/address/" + enc("CONTRACT:" + "0".repeat(64)));
        assertFalse(ghost.has("contract"));
    }

    @Test
    @DisplayName("the chain can be walked from the tip to genesis by previousHash and back by nextHash")
    void chainWalk_tipToGenesisAndBack() throws Exception {
        for (int i = 0; i < 12; i++) chain.minePendingTransactions(MINER);

        List<String> down = new ArrayList<>();
        JsonObject block = object("/api/blocks/latest");
        while (true) {
            down.add(block.get("hash").getAsString());
            assertEquals(down.size(), block.get("confirmations").getAsInt(), "one more with every step down");
            if (block.get("index").getAsInt() == 0) break;
            block = object("/api/blocks/hash/" + block.get("previousHash").getAsString());
        }
        assertEquals(13, down.size(), "the tip, 11 blocks, genesis");
        assertEquals(BlockchainConfig.GENESIS_PREV_HASH, block.get("previousHash").getAsString());

        List<String> up = new ArrayList<>();
        while (true) {
            up.add(block.get("hash").getAsString());
            if (!block.has("nextHash")) break;
            block = object("/api/blocks/hash/" + block.get("nextHash").getAsString());
        }
        assertEquals(12, block.get("index").getAsInt(), "the walk up ends at the tip");
        List<String> reversed = new ArrayList<>(down);
        java.util.Collections.reverse(reversed);
        assertEquals(reversed, up, "the same blocks both ways");
    }

    @Test
    @DisplayName("one transaction id: pending with its place in the mempool, then confirmed in a block")
    void pendingTransaction_thenConfirmed() throws Exception {
        chain.minePendingTransactions(MINER);
        Transaction cheap = send(MINER, "Alice", 1.0, 0.01);
        Transaction rich = send(MINER, "Bob", 1.0, 0.9);
        Transaction middle = send(MINER, "Carol", 1.0, 0.3);

        // Sent first, but a miner takes the best fee per byte first.
        String path = "/api/transactions/" + cheap.getTransactionId();
        JsonObject pending = object(path);
        assertEquals("pending", pending.get("status").getAsString());
        assertEquals(0, pending.get("confirmations").getAsInt());
        assertEquals(3, pending.get("mempoolPosition").getAsInt(), "last of three by fee rate");
        assertEquals(3, pending.get("mempoolSize").getAsInt());
        assertEquals(1, object("/api/transactions/" + rich.getTransactionId()).get("mempoolPosition").getAsInt());
        assertEquals(2, object("/api/transactions/" + middle.getTransactionId()).get("mempoolPosition").getAsInt());
        assertEquals(path, open(cheap.getTransactionId()), "search opens a pending transaction too");

        chain.minePendingTransactions(MINER);
        JsonObject confirmed = object(path);
        assertEquals("confirmed", confirmed.get("status").getAsString());
        assertEquals(2, confirmed.get("blockIndex").getAsInt());
        assertEquals(1, confirmed.get("confirmations").getAsInt());
        assertEquals(3, confirmed.get("position").getAsInt(), "the block keeps the miner's order: coinbase, rich, middle, cheap");
        assertFalse(confirmed.has("mempoolPosition") || confirmed.has("mempoolSize"), "it left the mempool");

        chain.minePendingTransactions(MINER);
        assertEquals(2, object(path).get("confirmations").getAsInt(), "and it gets deeper with every block");
    }

    @Test
    @DisplayName("the dashboard has the explorer in the palette and the tour, and shows contracts and mempool places")
    void dashboard_explorerInPaletteAndTour() throws Exception {
        String js = get("/app.js").body();
        assertTrue(js.contains("label: \"Explorer\""), "palette command");
        assertTrue(js.contains("location.hash = \"#/blocks\";"), "which opens the blocks view");
        assertTrue(js.contains("sel: \"explore\", title: \"Explorer\""), "tour step");
        assertTrue(get("/").body().contains("data-tour=\"explore\""), "its anchor on the page");
        assertTrue(js.contains("if (explorer.route) { location.hash = \"#/\"; onRoute(); }"), "the tour leaves an explorer view first");
        assertTrue(js.contains("tx.mempoolPosition + \" of \" + tx.mempoolSize"), "a pending transaction's place");
        assertTrue(js.contains("if (a.contract) {"), "a contract's address shows the contract");
    }
}
