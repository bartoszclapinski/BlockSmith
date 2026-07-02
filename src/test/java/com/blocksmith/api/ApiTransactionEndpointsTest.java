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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Milestone 12b transaction + mining endpoints, driving the
 * running ApiServer over HTTP.
 */
@DisplayName("API Transaction Endpoint Tests")
class ApiTransactionEndpointsTest {

    private static final int API_PORT_BASE = 17100;
    private static final int P2P_PORT_BASE = 18600;
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
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.stop();
        if (node != null) node.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Gives "Miner1" a spendable balance by mining a reward block. */
    private void fundMiner1() {
        node.getBlockchain().minePendingTransactions("Miner1");
    }

    @Test
    @DisplayName("POST /api/transactions accepts a valid transaction")
    void postTransaction_acceptsValid() throws Exception {
        fundMiner1();

        HttpResponse<String> res = post("/api/transactions",
                "{\"sender\":\"Miner1\",\"recipient\":\"Alice\",\"amount\":30}");

        assertEquals(201, res.statusCode(), "Valid transaction should be accepted");
        JsonObject tx = JsonParser.parseString(res.body()).getAsJsonObject();
        assertTrue(tx.has("transactionId"), "Response should include the assigned id");
        assertEquals(1, node.getBlockchain().getPendingTransactions().size(),
                "Transaction should be in the mempool");
    }

    @Test
    @DisplayName("POST /api/transactions rejects a transaction with insufficient funds")
    void postTransaction_rejectsInvalid() throws Exception {
        HttpResponse<String> res = post("/api/transactions",
                "{\"sender\":\"Nobody\",\"recipient\":\"Alice\",\"amount\":100}");

        assertEquals(400, res.statusCode(), "Unfunded transaction should be rejected");
        assertEquals(0, node.getBlockchain().getPendingTransactions().size(),
                "Rejected transaction must not enter the mempool");
    }

    @Test
    @DisplayName("GET /api/transactions/{id} finds pending and confirmed transactions")
    void getTransactionById_findsPendingAndConfirmed() throws Exception {
        fundMiner1();
        HttpResponse<String> submit = post("/api/transactions",
                "{\"sender\":\"Miner1\",\"recipient\":\"Alice\",\"amount\":30}");
        String id = JsonParser.parseString(submit.body()).getAsJsonObject()
                .get("transactionId").getAsString();

        // While still pending.
        HttpResponse<String> pending = get("/api/transactions/" + id);
        assertEquals(200, pending.statusCode(), "Pending transaction should be found");

        // After a block confirms it.
        post("/api/mine", "{\"minerAddress\":\"Miner2\"}");
        HttpResponse<String> confirmed = get("/api/transactions/" + id);
        assertEquals(200, confirmed.statusCode(), "Confirmed transaction should still be found");
        assertEquals(id, JsonParser.parseString(confirmed.body()).getAsJsonObject()
                .get("transactionId").getAsString(), "Should return the same transaction");
    }

    @Test
    @DisplayName("GET /api/transactions/{id} returns 404 for an unknown id")
    void getTransactionById_404WhenUnknown() throws Exception {
        HttpResponse<String> res = get("/api/transactions/does-not-exist");

        assertEquals(404, res.statusCode(), "Unknown transaction id should return 404");
    }

    @Test
    @DisplayName("POST /api/mine produces a new block")
    void postMine_producesBlock() throws Exception {
        int before = node.getBlockchain().getChainSize();

        HttpResponse<String> res = post("/api/mine", "{\"minerAddress\":\"Miner1\"}");

        assertEquals(201, res.statusCode(), "Mining should succeed");
        JsonObject block = JsonParser.parseString(res.body()).getAsJsonObject();
        assertEquals(before, block.get("index").getAsInt(), "Mined block extends the tip");
        assertEquals(before + 1, node.getBlockchain().getChainSize(),
                "Chain should grow by one block");
    }
}
