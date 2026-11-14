package com.blocksmith.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.core.Wallet;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.HashUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prepare / sign / submit over HTTP (Milestone 22b), on a node that requires
 * signatures. A Java {@link Wallet} plays the client: it only ever sees the
 * {@code signatureData} the node hands out, like a browser would.
 */
@DisplayName("API Signing Tests")
class ApiSigningTest {

    private static final int API_PORT_BASE = 18350;
    private static final int P2P_PORT_BASE = 20750;
    private static int counter = 0;

    private Node node;
    private ApiServer api;
    private int apiPort;
    private Blockchain chain;
    private Wallet alice;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        chain = new Blockchain(NoOpChainStore.INSTANCE, ChainParams.DEFAULT);       // signatures required
        node = new Node(P2P_PORT_BASE + offset, chain);
        api = new ApiServer(node, apiPort);
        api.start();
        alice = new Wallet();
        chain.minePendingTransactions(alice.getAddress());                           // Alice has 50
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.stop();
        if (node != null) node.stop();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + apiPort + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject get(String path) throws Exception {
        return JsonParser.parseString(client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + apiPort + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body()).getAsJsonObject();
    }

    private static JsonObject obj(HttpResponse<String> res) {
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private JsonObject prepareTransfer(double amount, double fee) throws Exception {
        HttpResponse<String> res = post("/api/transactions/prepare", "{\"sender\":\"" + alice.getAddress()
                + "\",\"recipient\":\"bob\",\"amount\":" + amount + ",\"fee\":" + fee + "}");
        assertEquals(201, res.statusCode(), res.body());
        return obj(res);
    }

    private HttpResponse<String> submit(JsonObject template, Wallet signer, String signedText) throws Exception {
        return post("/api/transactions", "{\"templateId\":\"" + template.get("templateId").getAsString()
                + "\",\"signature\":\"" + signer.signData(signedText)
                + "\",\"publicKey\":\"" + signer.getPublicKeyHex() + "\"}");
    }

    private HttpResponse<String> submit(JsonObject template, Wallet signer) throws Exception {
        return submit(template, signer, template.get("signatureData").getAsString());
    }

    @Test
    @DisplayName("Prepare, sign outside the node, submit: accepted, mined, paid once")
    void prepareSignSubmit_minedEndToEnd() throws Exception {
        JsonObject template = prepareTransfer(10, 0.5);
        assertTrue(template.get("signatureData").getAsString().startsWith(alice.getAddress() + "bob10.0"),
                "The exact text the node will verify (note Java's 10.0)");
        assertTrue(template.getAsJsonObject("transaction").has("sequence"), "Replaceable");

        HttpResponse<String> res = submit(template, alice);
        assertEquals(201, res.statusCode(), res.body());
        assertEquals(template.get("templateId").getAsString(), obj(res).get("transactionId").getAsString());

        assertEquals(201, post("/api/mine", "{\"minerAddress\":\"miner\"}").statusCode());
        assertEquals(50 - 10.5, get("/api/wallet/" + alice.getAddress()).get("balance").getAsDouble(), 1e-9);
        assertEquals(10.0, get("/api/wallet/bob").get("balance").getAsDouble(), 1e-9);
        assertEquals(404, submit(template, alice).statusCode(), "A template is used once");
    }

    @Test
    @DisplayName("Someone else's key, a signature over other text, or garbage: refused with the reason")
    void wrongKeyOrTamperedTemplate_rejected() throws Exception {
        JsonObject template = prepareTransfer(10, 0);

        HttpResponse<String> mallory = submit(template, new Wallet());
        assertEquals(400, mallory.statusCode());
        assertTrue(obj(mallory).get("error").getAsString().contains("does not belong to the sender"), mallory.body());

        String other = template.get("signatureData").getAsString().replace("bob10.0", "bob49.0");
        HttpResponse<String> tampered = submit(template, alice, other);
        assertEquals(400, tampered.statusCode());
        assertTrue(obj(tampered).get("error").getAsString().contains("signature does not verify"), tampered.body());

        HttpResponse<String> garbage = post("/api/transactions", "{\"templateId\":\""
                + template.get("templateId").getAsString() + "\",\"signature\":\"zz\",\"publicKey\":\"00\"}");
        assertEquals(400, garbage.statusCode());

        assertEquals(201, submit(template, alice).statusCode(), "The template still works for its owner");
    }

    @Test
    @DisplayName("Unknown or expired templates are 404")
    void unknownOrExpiredTemplate_rejected() throws Exception {
        assertEquals(404, post("/api/transactions",
                "{\"templateId\":\"nope\",\"signature\":\"00\",\"publicKey\":\"00\"}").statusCode());
        api.setTemplateTtlMs(1);
        JsonObject template = prepareTransfer(1, 0);
        Thread.sleep(20);
        HttpResponse<String> res = submit(template, alice);
        assertEquals(404, res.statusCode());
        assertTrue(obj(res).get("error").getAsString().contains("prepare it again"));
    }

    @Test
    @DisplayName("A contract deploy and a fee bump go through the same prepare / sign / submit")
    void deployAndBump_prepareSignSubmit() throws Exception {
        // Deploy a hashlock, signed by its funder.
        String lock = "SHA256 PUSH " + HashUtil.applySha256("secret") + " EQUAL";
        HttpResponse<String> prep = post("/api/contracts/prepare", "{\"funder\":\"" + alice.getAddress()
                + "\",\"amount\":15,\"lockingScript\":\"" + lock + "\"}");
        assertEquals(201, prep.statusCode(), prep.body());
        JsonObject deploy = obj(prep);
        String contractId = deploy.get("contractId").getAsString();
        assertTrue(deploy.get("signatureData").getAsString().contains("|lock:"), "The script is part of what is signed");
        assertEquals(201, submit(deploy, alice).statusCode());

        // A transfer, then a signed bump of it.
        JsonObject transfer = prepareTransfer(5, 0.1);
        assertEquals(201, submit(transfer, alice).statusCode());
        String oldId = transfer.get("templateId").getAsString();
        JsonArray pending = get("/api/mempool").getAsJsonArray("transactions");
        double minBump = 0;
        for (var t : pending) {
            JsonObject o = t.getAsJsonObject();
            if (o.get("transactionId").getAsString().equals(oldId)) minBump = o.get("minBumpFee").getAsDouble();
        }
        assertTrue(minBump > 0.1, "Signed transactions get a bump price too");

        HttpResponse<String> bumpPrep = post("/api/transactions/" + oldId + "/bump/prepare", "");
        assertEquals(201, bumpPrep.statusCode(), bumpPrep.body());
        JsonObject bump = obj(bumpPrep);
        assertEquals(oldId, bump.get("replaces").getAsString());
        assertEquals(minBump, bump.getAsJsonObject("transaction").get("fee").getAsDouble(), 1e-9,
                "Without a fee, the smallest allowed bump");
        HttpResponse<String> bumped = submit(bump, alice);
        assertEquals(201, bumped.statusCode(), bumped.body());
        assertEquals(oldId, obj(bumped).get("replaces").getAsString());
        assertEquals(400, post("/api/transactions/" + bump.get("templateId").getAsString() + "/bump", "{}").statusCode(),
                "The unsigned bump endpoint points signed transactions to prepare");

        assertEquals(201, post("/api/mine", "{\"minerAddress\":\"miner\"}").statusCode());
        assertEquals("OPEN", get("/api/contracts/" + contractId).get("status").getAsString());
        assertEquals(50 - 15 - 5 - minBump, get("/api/wallet/" + alice.getAddress()).get("balance").getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("A signed bump at the advertised minimum fee is always accepted (signature sizes vary)")
    void signedBump_atMinimumFee_alwaysAccepted() {
        // DER signatures are 70-72 bytes, and fee digits change the size too:
        // the bump price must leave room, or a bump can miss the bar by a hair.
        for (int i = 0; i < 30; i++) {
            Wallet w = new Wallet();
            Blockchain c = new Blockchain(NoOpChainStore.INSTANCE, ChainParams.DEFAULT);
            c.minePendingTransactions(w.getAddress());
            com.blocksmith.core.Transaction v1 = new com.blocksmith.core.Transaction(w.getAddress(), "bob", 5, 0.1, 7L);
            w.signTransaction(v1);
            assertTrue(c.addTransaction(v1));
            com.blocksmith.core.Transaction bump = v1.withFee(c.minimumBumpFee(v1));
            w.signTransaction(bump);
            assertNull(c.submitTransaction(bump), "round " + i);
        }
    }

    @Test
    @DisplayName("Unsigned submissions are refused on a network that requires signatures")
    void unsignedSubmit_400() throws Exception {
        HttpResponse<String> res = post("/api/transactions", "{\"sender\":\"" + alice.getAddress()
                + "\",\"recipient\":\"bob\",\"amount\":1}");
        assertEquals(400, res.statusCode());
        assertTrue(obj(res).get("error").getAsString().contains("unsigned"), res.body());

        HttpResponse<String> deploy = post("/api/contracts", "{\"funder\":\"" + alice.getAddress()
                + "\",\"amount\":1,\"lockingScript\":\"PUSH 1\"}");
        assertEquals(400, deploy.statusCode());
        assertTrue(obj(deploy).get("error").getAsString().contains("unsigned"), deploy.body());
    }
}
