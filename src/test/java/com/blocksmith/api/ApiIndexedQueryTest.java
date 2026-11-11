package com.blocksmith.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.network.Node;
import com.blocksmith.storage.H2ChainStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP tests for the indexed query endpoints (Milestone 16c), against a node
 * backed by an in-memory H2 store.
 */
@DisplayName("API Indexed Query Tests")
class ApiIndexedQueryTest {

    private static final int API_PORT_BASE = 17800;
    private static final int P2P_PORT_BASE = 18950;
    private static int counter = 0;

    private H2ChainStore store;
    private Node node;
    private ApiServer api;
    private int apiPort;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        store = H2ChainStore.openInMemory("api-query-" + UUID.randomUUID());
        node = new Node(P2P_PORT_BASE + offset, new Blockchain(store));
        api = new ApiServer(node, apiPort);
        api.start();
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.stop();
        if (node != null) node.stop();
        if (store != null) store.close();
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

    /** Alice mines a reward, sends Bob 10, and the transfer is mined. Returns its id. */
    private String aliceSendsBobTen() throws Exception {
        post("/api/mine", "{\"minerAddress\":\"Alice\"}");
        String id = obj(post("/api/transactions",
                "{\"sender\":\"Alice\",\"recipient\":\"Bob\",\"amount\":10}")).get("transactionId").getAsString();
        post("/api/mine", "{\"minerAddress\":\"Miner\"}");
        return id;
    }

    @Test
    @DisplayName("GET /api/blocks/hash/{hash} returns the block; unknown hash -> 404 envelope")
    void blockByHash_foundAndMissing() throws Exception {
        aliceSendsBobTen();
        JsonObject latest = obj(get("/api/blocks/latest"));

        HttpResponse<String> found = get("/api/blocks/hash/" + latest.get("hash").getAsString());
        assertEquals(200, found.statusCode());
        assertEquals(latest.get("index").getAsInt(), obj(found).get("index").getAsInt());

        HttpResponse<String> missing = get("/api/blocks/hash/0000nope");
        assertEquals(404, missing.statusCode());
        assertTrue(obj(missing).has("error"), "Standard JSON error envelope");
    }

    @Test
    @DisplayName("GET /api/wallet/{address}/transactions lists history with status and confirmations")
    void walletTransactions_historyShape() throws Exception {
        String confirmedId = aliceSendsBobTen();
        String pendingId = obj(post("/api/transactions",
                "{\"sender\":\"Bob\",\"recipient\":\"Carol\",\"amount\":4}")).get("transactionId").getAsString();

        HttpResponse<String> res = get("/api/wallet/Bob/transactions");
        assertEquals(200, res.statusCode());
        JsonArray history = JsonParser.parseString(res.body()).getAsJsonArray();
        assertEquals(2, history.size());

        JsonObject pending = history.get(0).getAsJsonObject();
        assertEquals(pendingId, pending.get("transactionId").getAsString());
        assertEquals("pending", pending.get("status").getAsString());
        assertEquals(0, pending.get("confirmations").getAsInt());
        assertFalse(pending.has("blockIndex"));

        JsonObject confirmed = history.get(1).getAsJsonObject();
        assertEquals(confirmedId, confirmed.get("transactionId").getAsString());
        assertEquals("confirmed", confirmed.get("status").getAsString());
        assertEquals(2, confirmed.get("blockIndex").getAsInt());
        assertEquals(1, confirmed.get("confirmations").getAsInt());
    }

    @Test
    @DisplayName("Unknown address -> empty list (200), like a zero balance")
    void walletTransactions_unknownAddress_emptyList() throws Exception {
        HttpResponse<String> res = get("/api/wallet/nobody/transactions");
        assertEquals(200, res.statusCode());
        assertEquals(0, JsonParser.parseString(res.body()).getAsJsonArray().size());
    }

    @Test
    @DisplayName("GET /api/transactions/{id} adds blockIndex and confirmations, keeps the tx fields")
    void transactionById_includesLocation() throws Exception {
        String id = aliceSendsBobTen();
        post("/api/mine", "{\"minerAddress\":\"Miner\"}");             // one more block on top

        JsonObject tx = obj(get("/api/transactions/" + id));
        assertEquals(id, tx.get("transactionId").getAsString());
        assertEquals("Bob", tx.get("recipient").getAsString());
        assertEquals("confirmed", tx.get("status").getAsString());
        assertEquals(2, tx.get("blockIndex").getAsInt());
        assertEquals(2, tx.get("confirmations").getAsInt());
    }
}
