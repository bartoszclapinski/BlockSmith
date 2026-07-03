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
import com.blocksmith.util.HashUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Server-side tests for the Milestone 14c contract endpoints. Drives the same
 * HTTP chain the dashboard's Contracts panel hits - deploy, mine, inspect,
 * claim, mine - and confirms funds move and rejections carry an error envelope.
 */
@DisplayName("API Contracts Tests")
class ApiContractsTest {

    private static final int API_PORT_BASE = 17600;
    private static final int P2P_PORT_BASE = 19100;
    private static int counter = 0;

    private static final String FUNDER = "funder-address";
    private static final String CLAIMER = "claimer-address";
    private static final String MINER = "miner-address";
    private static final String SECRET = "open-sesame";

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

    private void mineTo(String address) throws Exception {
        post("/api/mine", "{\"minerAddress\":\"" + address + "\"}");
    }

    @Test
    @DisplayName("Contract lifecycle over HTTP: deploy, mine, inspect, claim, settle")
    void contractLifecycle_overHttp() throws Exception {
        // Give the funder a mining reward to lock.
        mineTo(FUNDER);

        // Deploy a hashlock contract.
        String hashlock = "SHA256 PUSH " + HashUtil.applySha256(SECRET) + " EQUAL";
        String deployBody = "{\"funder\":\"" + FUNDER + "\",\"amount\":20,"
                + "\"lockingScript\":\"" + hashlock + "\"}";
        HttpResponse<String> deployed = post("/api/contracts", deployBody);
        assertEquals(201, deployed.statusCode());
        String contractId = json(deployed).get("contractId").getAsString();

        // Mine the deploy: the contract becomes OPEN.
        mineTo(MINER);
        HttpResponse<String> inspected = get("/api/contracts/" + contractId);
        assertEquals(200, inspected.statusCode());
        assertEquals("OPEN", json(inspected).get("status").getAsString());
        assertEquals(20.0, json(inspected).get("amount").getAsDouble(), 0.0001);

        // Claim with the correct preimage, then mine to settle.
        String claimBody = "{\"claimer\":\"" + CLAIMER + "\",\"unlockingScript\":\"PUSH " + SECRET + "\"}";
        assertEquals(201, post("/api/contracts/" + contractId + "/claim", claimBody).statusCode());
        mineTo(MINER);

        assertEquals("CLAIMED", json(get("/api/contracts/" + contractId)).get("status").getAsString());
        assertEquals(20.0, json(get("/api/wallet/" + CLAIMER)).get("balance").getAsDouble(), 0.0001,
                "Claimer should receive the locked amount once settled");
    }

    @Test
    @DisplayName("GET /api/contracts lists deployed contracts")
    void contractsList_reflectsDeploys() throws Exception {
        mineTo(FUNDER);
        String hashlock = "SHA256 PUSH " + HashUtil.applySha256(SECRET) + " EQUAL";
        post("/api/contracts", "{\"funder\":\"" + FUNDER + "\",\"amount\":10,"
                + "\"lockingScript\":\"" + hashlock + "\"}");
        mineTo(MINER);

        HttpResponse<String> res = get("/api/contracts");
        assertEquals(200, res.statusCode());
        assertTrue(JsonParser.parseString(res.body()).getAsJsonArray().size() >= 1);
    }

    @Test
    @DisplayName("Rejected deploy and claim return an error envelope")
    void rejectedDeployAndClaim_returnErrorEnvelope() throws Exception {
        // Deploy beyond the funder's (zero) balance -> 400 + error.
        String hashlock = "SHA256 PUSH " + HashUtil.applySha256(SECRET) + " EQUAL";
        HttpResponse<String> badDeploy = post("/api/contracts",
                "{\"funder\":\"" + FUNDER + "\",\"amount\":999,\"lockingScript\":\"" + hashlock + "\"}");
        assertEquals(400, badDeploy.statusCode());
        assertTrue(json(badDeploy).has("error"));

        // Claim an unknown contract -> 400 + error.
        HttpResponse<String> badClaim = post("/api/contracts/nope/claim",
                "{\"claimer\":\"" + CLAIMER + "\",\"unlockingScript\":\"PUSH " + SECRET + "\"}");
        assertEquals(400, badClaim.statusCode());
        assertTrue(json(badClaim).has("error"));

        // Missing required fields -> 400 + error.
        HttpResponse<String> missing = post("/api/contracts", "{\"funder\":\"" + FUNDER + "\"}");
        assertEquals(400, missing.statusCode());
        assertTrue(json(missing).has("error"));
    }
}
