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
import com.blocksmith.util.BlockchainConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Milestone 12c wallet + network endpoints and JSON error
 * handling, driving the running ApiServer over HTTP.
 */
@DisplayName("API Wallet & Network Endpoint Tests")
class ApiWalletNetworkEndpointsTest {

    private static final int API_PORT_BASE = 17200;
    private static final int P2P_PORT_BASE = 18700;
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

    private HttpResponse<String> post(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("GET /api/wallet/{address} returns the address balance")
    void getWalletBalance_returnsBalance() throws Exception {
        node.getBlockchain().minePendingTransactions("Miner1"); // reward = 50

        HttpResponse<String> res = get("/api/wallet/Miner1");

        assertEquals(200, res.statusCode(), "Should return 200");
        JsonObject wallet = JsonParser.parseString(res.body()).getAsJsonObject();
        assertEquals(BlockchainConfig.MINING_REWARD, wallet.get("balance").getAsDouble(), 0.001,
                "Balance should equal the mining reward");
        assertEquals(0.0, wallet.get("pendingOutgoing").getAsDouble(), 0.001,
                "No pending outgoing yet");
    }

    @Test
    @DisplayName("POST /api/wallet/create returns a new address")
    void postWalletCreate_returnsAddress() throws Exception {
        HttpResponse<String> res = post("/api/wallet/create");

        assertEquals(201, res.statusCode(), "Should return 201");
        JsonObject body = JsonParser.parseString(res.body()).getAsJsonObject();
        assertTrue(body.has("address"), "Response should include an address");
        assertTrue(body.get("address").getAsString().startsWith("0x"),
                "Address should be 0x-prefixed");
        assertFalse(body.has("privateKey"), "Private key must never be returned");
    }

    @Test
    @DisplayName("GET /api/network/peers returns connected peers (empty for a lone node)")
    void getPeers_returnsConnectedPeers() throws Exception {
        HttpResponse<String> res = get("/api/network/peers");

        assertEquals(200, res.statusCode(), "Should return 200");
        JsonArray peers = JsonParser.parseString(res.body()).getAsJsonArray();
        assertEquals(0, peers.size(), "A lone node has no connected peers");
    }

    @Test
    @DisplayName("Unknown routes return a JSON error with 404")
    void unknownRoute_returnsJsonError() throws Exception {
        HttpResponse<String> res = get("/api/does-not-exist");

        assertEquals(404, res.statusCode(), "Unknown route should return 404");
        JsonObject body = JsonParser.parseString(res.body()).getAsJsonObject();
        assertTrue(body.has("error"), "404 body should be a JSON error envelope");
    }
}
