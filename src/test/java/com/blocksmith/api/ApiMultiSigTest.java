package com.blocksmith.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.contract.MultiSigWallet;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.core.Wallet;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multisig over HTTP with the members' keys on the client side (Milestone
 * 23a), on a node that requires signatures. Java {@link Wallet}s play the
 * members' devices; the node only ever sees public keys and signatures.
 */
@DisplayName("API MultiSig Tests")
class ApiMultiSigTest {

    private static final int API_PORT_BASE = 17700;
    private static final int P2P_PORT_BASE = 19200;
    private static final String CLAIMER = "claimer-address";
    private static int counter = 0;

    private Node node;
    private ApiServer api;
    private int apiPort;
    private Blockchain chain;
    private Wallet funder;
    private final Wallet[] members = {new Wallet(), new Wallet(), new Wallet()};
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        chain = new Blockchain(NoOpChainStore.INSTANCE, ChainParams.DEFAULT);
        node = new Node(P2P_PORT_BASE + offset, chain);
        api = new ApiServer(node, apiPort);
        api.start();
        funder = new Wallet();
        chain.minePendingTransactions(funder.getAddress());                     // 50 to lock
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

    private static JsonObject json(HttpResponse<String> res) {
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    private String keys(Wallet... ws) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < ws.length; i++) b.append(i > 0 ? "," : "").append('"').append(ws[i].getPublicKeyHex()).append('"');
        return b.append(']').toString();
    }

    /** Creates a 2-of-3 from the members' public keys, funds it with 20 (signed by the funder), mines it. */
    private String fundedTwoOfThree() throws Exception {
        HttpResponse<String> created = post("/api/multisig/create",
                "{\"memberPublicKeys\":" + keys(members) + ",\"threshold\":2}");
        assertEquals(201, created.statusCode(), created.body());
        String script = json(created).get("lockingScript").getAsString();

        JsonObject t = json(post("/api/contracts/prepare", "{\"funder\":\"" + funder.getAddress()
                + "\",\"amount\":20,\"lockingScript\":\"" + script + "\"}"));
        assertEquals(201, post("/api/transactions", "{\"templateId\":\"" + t.get("templateId").getAsString()
                + "\",\"signature\":\"" + funder.signData(t.get("signatureData").getAsString())
                + "\",\"publicKey\":\"" + funder.getPublicKeyHex() + "\"}").statusCode());
        assertEquals(201, post("/api/mine", "{\"minerAddress\":\"miner\"}").statusCode());
        String contractId = t.get("contractId").getAsString();
        assertEquals("OPEN", get("/api/contracts/" + contractId).get("status").getAsString());
        return contractId;
    }

    private String message(String contractId, String claimer) throws Exception {
        HttpResponse<String> res = post("/api/multisig/claim/prepare",
                "{\"contractId\":\"" + contractId + "\",\"claimer\":\"" + claimer + "\"}");
        assertEquals(200, res.statusCode(), res.body());
        return json(res).get("message").getAsString();
    }

    private HttpResponse<String> claim(String contractId, String claimer, String message, Wallet... signers) throws Exception {
        StringBuilder sigs = new StringBuilder("[");
        for (int i = 0; i < signers.length; i++) {
            sigs.append(i > 0 ? "," : "").append("{\"publicKey\":\"").append(signers[i].getPublicKeyHex())
                    .append("\",\"signature\":\"").append(signers[i].signData(message)).append("\"}");
        }
        return post("/api/multisig/claim", "{\"contractId\":\"" + contractId + "\",\"claimer\":\"" + claimer
                + "\",\"signatures\":" + sigs.append(']') + "}");
    }

    @Test
    @DisplayName("Two of three members sign (in any order): the claim is accepted and settles")
    void mMemberSignatures_anyOrder_accepted() throws Exception {
        String contractId = fundedTwoOfThree();
        JsonObject prep = json(post("/api/multisig/claim/prepare",
                "{\"contractId\":\"" + contractId + "\",\"claimer\":\"" + CLAIMER + "\"}"));
        assertEquals(2, prep.get("threshold").getAsInt());
        assertEquals(3, prep.getAsJsonArray("memberPublicKeys").size());
        assertEquals(20.0, prep.get("amount").getAsDouble());
        String msg = prep.get("message").getAsString();

        HttpResponse<String> res = claim(contractId, CLAIMER, msg, members[2], members[0]);   // out of order
        assertEquals(201, res.statusCode(), res.body());
        assertEquals(201, post("/api/mine", "{\"minerAddress\":\"miner\"}").statusCode());
        assertEquals("CLAIMED", get("/api/contracts/" + contractId).get("status").getAsString());
        assertEquals(20.0, get("/api/wallet/" + CLAIMER).get("balance").getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("The probe: a claim without member signatures (or with too few) is refused")
    void claimWithoutSignatures_refused() throws Exception {
        String contractId = fundedTwoOfThree();
        HttpResponse<String> none = post("/api/multisig/claim",
                "{\"contractId\":\"" + contractId + "\",\"claimer\":\"mallory\"}");
        assertEquals(400, none.statusCode());
        assertTrue(json(none).get("error").getAsString().contains("signatures required"), none.body());

        HttpResponse<String> one = claim(contractId, "mallory", message(contractId, "mallory"), members[0]);
        assertEquals(400, one.statusCode());
        assertTrue(json(one).get("error").getAsString().contains("Need 2"), one.body());

        post("/api/mine", "{\"minerAddress\":\"miner\"}");
        assertEquals(0.0, get("/api/wallet/mallory").get("balance").getAsDouble());
        assertEquals("OPEN", get("/api/contracts/" + contractId).get("status").getAsString());
    }

    @Test
    @DisplayName("Signatures collected for one claimer cannot pay another")
    void signaturesForAnotherClaimer_refused() throws Exception {
        String contractId = fundedTwoOfThree();
        String forBob = message(contractId, "bob");
        HttpResponse<String> res = claim(contractId, "mallory", forBob, members[0], members[1]);
        assertEquals(400, res.statusCode());
        assertTrue(json(res).get("error").getAsString().contains("does not verify for this claim"), res.body());
        assertEquals(201, claim(contractId, "bob", forBob, members[0], members[1]).statusCode(), "They do pay Bob");
    }

    @Test
    @DisplayName("A non-member, the same member twice, duplicate keys or node-made keys are refused")
    void nonMemberOrDuplicateKey_refused() throws Exception {
        String contractId = fundedTwoOfThree();
        String msg = message(contractId, CLAIMER);

        HttpResponse<String> outsider = claim(contractId, CLAIMER, msg, members[0], new Wallet());
        assertEquals(400, outsider.statusCode());
        assertTrue(json(outsider).get("error").getAsString().contains("Not a member"), outsider.body());

        HttpResponse<String> twice = claim(contractId, CLAIMER, msg, members[1], members[1]);
        assertEquals(400, twice.statusCode());
        assertTrue(json(twice).get("error").getAsString().contains("Two signatures from member 2"), twice.body());

        assertEquals(400, post("/api/multisig/create", "{\"memberPublicKeys\":" + keys(members[0], members[0])
                + ",\"threshold\":1}").statusCode(), "Duplicate member key");
        HttpResponse<String> old = post("/api/multisig/create", "{\"members\":3,\"threshold\":2}");
        assertEquals(400, old.statusCode());
        assertTrue(json(old).get("error").getAsString().contains("no longer makes member keys"));
        assertEquals(400, post("/api/multisig/create", "{\"memberPublicKeys\":[\"zz\"],\"threshold\":1}").statusCode());
    }

    @Test
    @DisplayName("The node holds nothing: a fresh API server (a restart) can still settle the claim")
    void nodeHoldsNoKeys_restartLosesNothing() throws Exception {
        String contractId = fundedTwoOfThree();
        String msg = message(contractId, CLAIMER);

        api.stop();                                                    // the API "restarts"
        api = new ApiServer(node, apiPort);
        api.start();

        assertEquals(201, claim(contractId, CLAIMER, msg, members[0], members[1]).statusCode());
        post("/api/mine", "{\"minerAddress\":\"miner\"}");
        assertEquals(20.0, get("/api/wallet/" + CLAIMER).get("balance").getAsDouble(), 1e-9);
    }

    @Test
    @DisplayName("A wallet reads back from its locking script; anything else is not a multisig")
    void fromLockingScript_roundTrip() {
        MultiSigWallet w = MultiSigWallet.ofHex(List.of(members[0].getPublicKeyHex(), members[1].getPublicKeyHex()), 1);
        MultiSigWallet back = MultiSigWallet.fromLockingScript(w.getLockingScript());
        assertNotNull(back);
        assertEquals(w.getMemberPublicKeys(), back.getMemberPublicKeys());
        assertEquals(1, back.getThreshold());
        assertNull(MultiSigWallet.fromLockingScript("SHA256 PUSH abc EQUAL"));
        assertNull(MultiSigWallet.fromLockingScript(w.getLockingScript().replace("PUSH 2 CHECKMULTISIG", "PUSH 3 CHECKMULTISIG")));
        assertNull(MultiSigWallet.fromLockingScript(null));
    }
}
