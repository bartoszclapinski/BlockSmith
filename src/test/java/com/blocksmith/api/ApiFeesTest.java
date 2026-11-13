package com.blocksmith.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.network.Node;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP tests for fees, the mempool view and block size (Milestone 18d).
 */
@DisplayName("API Fees Tests")
class ApiFeesTest {

    private static final int API_PORT_BASE = 18100;
    private static final int P2P_PORT_BASE = 20400;
    private static int counter = 0;

    private Node node;
    private ApiServer api;
    private int apiPort;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        node = new Node(P2P_PORT_BASE + offset);
        api = new ApiServer(node, apiPort);
        api.start();
        node.getBlockchain().minePendingTransactions("Alice");     // Alice has 50
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.stop();
        if (node != null) node.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject obj(HttpResponse<String> res) {
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private double balance(String address) throws Exception {
        return obj(get("/api/wallet/" + address)).get("balance").getAsDouble();
    }

    @Test
    @DisplayName("POST /api/transactions with a fee: the sender pays it, the miner collects it")
    void postTransaction_withFee_charged() throws Exception {
        HttpResponse<String> res = post("/api/transactions",
                "{\"sender\":\"Alice\",\"recipient\":\"Bob\",\"amount\":10,\"fee\":0.75}");
        assertEquals(201, res.statusCode(), res.body());
        assertEquals(0.75, obj(res).get("fee").getAsDouble(), 1e-9);

        JsonObject mined = obj(post("/api/mine", "{\"minerAddress\":\"Miner\"}"));
        assertEquals(0.75, mined.get("totalFees").getAsDouble(), 1e-9);

        assertEquals(50 - 10 - 0.75, balance("Alice"), 1e-9);
        assertEquals(10, balance("Bob"), 1e-9);
        assertEquals(50.75, balance("Miner"), 1e-9);
    }

    @Test
    @DisplayName("A negative or non-numeric fee is a 400 with an error envelope")
    void postTransaction_badFee_400() throws Exception {
        for (String fee : new String[] {"-1", "\"lots\""}) {
            HttpResponse<String> res = post("/api/transactions",
                    "{\"sender\":\"Alice\",\"recipient\":\"Bob\",\"amount\":1,\"fee\":" + fee + "}");
            assertEquals(400, res.statusCode(), "fee=" + fee);
            assertTrue(obj(res).has("error"));
        }
    }

    @Test
    @DisplayName("GET /api/mempool lists pending transactions best fee per byte first")
    void mempool_orderedByFeeRate() throws Exception {
        post("/api/transactions", "{\"sender\":\"Alice\",\"recipient\":\"Bob\",\"amount\":1,\"fee\":0.1}");
        post("/api/transactions", "{\"sender\":\"Alice\",\"recipient\":\"Bob\",\"amount\":2,\"fee\":0.9}");
        post("/api/transactions", "{\"sender\":\"Alice\",\"recipient\":\"Bob\",\"amount\":3}");

        JsonObject mempool = obj(get("/api/mempool"));
        assertEquals(3, mempool.get("count").getAsInt());
        assertEquals(1.0, mempool.get("totalFees").getAsDouble(), 1e-9);
        JsonArray txs = mempool.getAsJsonArray("transactions");
        assertEquals(0.9, txs.get(0).getAsJsonObject().get("fee").getAsDouble(), 1e-9);
        assertEquals(0.1, txs.get(1).getAsJsonObject().get("fee").getAsDouble(), 1e-9);
        assertEquals(0.0, txs.get(2).getAsJsonObject().get("fee").getAsDouble(), 1e-9);
        for (int i = 0; i < txs.size(); i++) {
            JsonObject t = txs.get(i).getAsJsonObject();
            assertTrue(t.get("sizeBytes").getAsInt() > 100);
            assertEquals(t.get("fee").getAsDouble() / t.get("sizeBytes").getAsInt(),
                    t.get("feeRate").getAsDouble(), 1e-12);
        }
    }

    @Test
    @DisplayName("Blocks carry their size and fees; status carries the block limits")
    void blocksAndStatus_haveSizeAndLimits() throws Exception {
        JsonObject latest = obj(get("/api/blocks/latest"));
        assertTrue(latest.get("sizeBytes").getAsInt() > 100);
        assertEquals(0.0, latest.get("totalFees").getAsDouble(), 1e-9);

        JsonArray all = JsonParser.parseString(get("/api/blocks").body()).getAsJsonArray();
        assertTrue(all.get(0).getAsJsonObject().has("sizeBytes"), "List entries too");

        JsonObject status = obj(get("/api/network/status"));
        assertEquals(16_384, status.get("maxBlockBytes").getAsInt());
        assertEquals(50, status.get("maxBlockTransactions").getAsInt());
    }
}
