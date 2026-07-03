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
 * Server-side glue for the Milestone 13c dashboard actions.
 *
 * The browser UI is verified by running it; here we drive the SAME endpoint
 * chain the dashboard buttons hit - create wallet, mine, check balance, submit
 * a transaction, mine again - end to end, and confirm value moves as a user
 * would expect from the page. The individual endpoints already have focused
 * Sprint 12 tests; this checks they compose correctly for the UI flow.
 */
@DisplayName("API Dashboard Actions Tests")
class ApiDashboardActionsTest {

    private static final int API_PORT_BASE = 17500;
    private static final int P2P_PORT_BASE = 19000;
    private static final double MINING_REWARD = 50.0;
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

    private static JsonObject json(HttpResponse<String> res) {
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    @Test
    @DisplayName("Dashboard action chain moves value end to end")
    void dashboardActionEndpoints_behaveAsExpected() throws Exception {
        // Create wallet button -> POST /api/wallet/create.
        HttpResponse<String> created = post("/api/wallet/create", "{}");
        assertEquals(201, created.statusCode());
        String miner = json(created).get("address").getAsString();
        assertNotNull(miner);

        // Mine button -> POST /api/mine, giving the miner a coinbase reward.
        assertEquals(201, post("/api/mine", "{\"minerAddress\":\"" + miner + "\"}").statusCode());

        // Balance lookup -> GET /api/wallet/{address}.
        JsonObject balance = json(get("/api/wallet/" + miner));
        assertEquals(MINING_REWARD, balance.get("balance").getAsDouble(), 0.0001,
                "Miner should hold the coinbase reward after mining");

        // Send transaction form -> POST /api/transactions (spend part of the reward).
        String recipient = "recipient-address";
        String txBody = "{\"sender\":\"" + miner + "\",\"recipient\":\""
                + recipient + "\",\"amount\":10}";
        HttpResponse<String> txRes = post("/api/transactions", txBody);
        assertEquals(201, txRes.statusCode());
        assertTrue(json(txRes).has("transactionId"), "Response should echo the transaction id");

        // The transaction is pending until mined.
        assertEquals(0.0, json(get("/api/wallet/" + recipient)).get("balance").getAsDouble(), 0.0001);
        assertEquals(10.0, json(get("/api/wallet/" + miner)).get("pendingOutgoing").getAsDouble(), 0.0001);

        // Mine again -> the pending transaction confirms.
        assertEquals(201, post("/api/mine", "{\"minerAddress\":\"" + miner + "\"}").statusCode());
        assertEquals(10.0, json(get("/api/wallet/" + recipient)).get("balance").getAsDouble(), 0.0001,
                "Recipient should receive the transferred amount once mined");
    }

    @Test
    @DisplayName("Rejected action returns an error envelope the UI can surface")
    void rejectedAction_returnsErrorEnvelope() throws Exception {
        // Submit-transaction form with a missing field -> 400 + {"error": ...}.
        HttpResponse<String> res = post("/api/transactions", "{\"sender\":\"a\",\"amount\":5}");
        assertEquals(400, res.statusCode());
        assertTrue(json(res).has("error"), "Error responses must carry an 'error' message for the UI");
    }
}
