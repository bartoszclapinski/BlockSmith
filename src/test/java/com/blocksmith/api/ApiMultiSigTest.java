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
 * Server-side tests for the Milestone 15c multisig endpoints. Drives the same
 * HTTP chain the dashboard's Multisig panel hits - create a wallet, deploy its
 * lock as a contract, mine, claim with the node's assembled signatures, mine -
 * and confirms funds move and rejections carry an error envelope.
 */
@DisplayName("API Multisig Tests")
class ApiMultiSigTest {

    private static final int API_PORT_BASE = 17700;
    private static final int P2P_PORT_BASE = 19200;
    private static int counter = 0;

    private static final String FUNDER = "funder-address";
    private static final String CLAIMER = "claimer-address";
    private static final String MINER = "miner-address";

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
    @DisplayName("Multisig lifecycle over HTTP: create, deploy, mine, claim, settle")
    void multisigLifecycle_overHttp() throws Exception {
        // Give the funder a mining reward to lock.
        mineTo(FUNDER);

        // Create a 2-of-3 multisig; the node holds the member keys.
        HttpResponse<String> created = post("/api/multisig/create",
                "{\"members\":3,\"threshold\":2}");
        assertEquals(201, created.statusCode());
        JsonObject wallet = json(created);
        assertEquals(2, wallet.get("threshold").getAsInt());
        assertEquals(3, wallet.get("memberCount").getAsInt());
        assertEquals(3, wallet.getAsJsonArray("memberPublicKeys").size());
        // The private keys must never be exposed.
        assertFalse(created.body().contains("privateKey"));
        String lockingScript = wallet.get("lockingScript").getAsString();

        // Deploy the multisig lock as a contract.
        String deployBody = "{\"funder\":\"" + FUNDER + "\",\"amount\":20,"
                + "\"lockingScript\":\"" + lockingScript + "\"}";
        HttpResponse<String> deployed = post("/api/contracts", deployBody);
        assertEquals(201, deployed.statusCode());
        String contractId = json(deployed).get("contractId").getAsString();

        // Mine the deploy: the contract becomes OPEN.
        mineTo(MINER);
        assertEquals("OPEN", json(get("/api/contracts/" + contractId)).get("status").getAsString());

        // Claim via the multisig endpoint: the node assembles the M signatures.
        HttpResponse<String> claimed = post("/api/multisig/claim",
                "{\"contractId\":\"" + contractId + "\",\"claimer\":\"" + CLAIMER + "\"}");
        assertEquals(201, claimed.statusCode());

        // Mine to settle, then verify the funds moved and the contract closed.
        mineTo(MINER);
        assertEquals("CLAIMED", json(get("/api/contracts/" + contractId)).get("status").getAsString());
        assertEquals(20.0, json(get("/api/wallet/" + CLAIMER)).get("balance").getAsDouble(), 0.0001,
                "Claimer should receive the locked amount once settled");
    }

    @Test
    @DisplayName("Rejected multisig create and claim return an error envelope")
    void rejectedMultisigClaim_returnsErrorEnvelope() throws Exception {
        // Claim an unknown contract -> 400 + error.
        HttpResponse<String> unknown = post("/api/multisig/claim",
                "{\"contractId\":\"nope\",\"claimer\":\"" + CLAIMER + "\"}");
        assertEquals(400, unknown.statusCode());
        assertTrue(json(unknown).has("error"));

        // Missing threshold on create -> 400 + error.
        HttpResponse<String> missing = post("/api/multisig/create", "{\"members\":3}");
        assertEquals(400, missing.statusCode());
        assertTrue(json(missing).has("error"));

        // Threshold greater than the member count -> 400 + error.
        HttpResponse<String> badThreshold = post("/api/multisig/create",
                "{\"members\":2,\"threshold\":5}");
        assertEquals(400, badThreshold.statusCode());
        assertTrue(json(badThreshold).has("error"));
    }
}
