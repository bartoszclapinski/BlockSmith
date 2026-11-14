package com.blocksmith.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.core.MempoolPolicy;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP tests for the fee market (Milestone 19d): estimates, bumps and a full mempool.
 */
@DisplayName("API Fee Market Tests")
class ApiFeeMarketTest {

    private static final int API_PORT_BASE = 18150;
    private static final int P2P_PORT_BASE = 20450;
    private static int counter = 0;

    private Node node;
    private ApiServer api;
    private int apiPort;
    private final HttpClient client = HttpClient.newHttpClient();

    /** Starts a node whose mempool holds at most {@code maxTxs}; Alice has 50. */
    private void start(int maxTxs) {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        Blockchain chain = new Blockchain(NoOpChainStore.INSTANCE, ChainParams.DEFAULT,
                new MempoolPolicy(65_536, maxTxs, MempoolPolicy.DEFAULT.incrementalFeeRate()));
        node = new Node(P2P_PORT_BASE + offset, chain);
        api = new ApiServer(node, apiPort);
        api.start();
        chain.minePendingTransactions("Alice");
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

    private JsonObject send(double amount, double fee) throws Exception {
        HttpResponse<String> res = post("/api/transactions",
                "{\"sender\":\"Alice\",\"recipient\":\"Bob\",\"amount\":" + amount + ",\"fee\":" + fee + "}");
        assertEquals(201, res.statusCode(), res.body());
        return obj(res);
    }

    @Test
    @DisplayName("GET /api/fees/estimate: fast/normal/slow with rate and fee, typical size, min rate")
    void estimate_shape() throws Exception {
        start(200);
        JsonObject est = obj(get("/api/fees/estimate"));
        for (String key : new String[] {"fast", "normal", "slow"}) {
            JsonObject e = est.getAsJsonObject(key);
            assertTrue(e.has("blocks") && e.has("feeRate") && e.has("fee"), key);
            assertEquals(0.0, e.get("fee").getAsDouble(), "Empty mempool: free");
        }
        assertEquals(1, est.getAsJsonObject("fast").get("blocks").getAsInt());
        assertEquals(6, est.getAsJsonObject("slow").get("blocks").getAsInt());
        assertTrue(est.get("typicalTxBytes").getAsInt() > 200);
        assertEquals(0.0, est.get("mempoolMinFeeRate").getAsDouble());
    }

    @Test
    @DisplayName("Bump end to end: the old version is gone, the new one is mined and pays the miner")
    void bump_endToEnd() throws Exception {
        start(200);
        JsonObject tx = send(10, 0.1);
        assertTrue(tx.has("sequence"), "API transactions are replaceable");
        String oldId = tx.get("transactionId").getAsString();

        JsonObject mempool = obj(get("/api/mempool"));
        JsonObject listed = mempool.getAsJsonArray("transactions").get(0).getAsJsonObject();
        assertTrue(listed.get("replaceable").getAsBoolean());
        double minBump = listed.get("minBumpFee").getAsDouble();
        assertTrue(minBump > 0.1);

        HttpResponse<String> res = post("/api/transactions/" + oldId + "/bump", "{\"fee\":" + minBump + "}");
        assertEquals(201, res.statusCode(), res.body());
        JsonObject bumped = obj(res);
        assertEquals(oldId, bumped.get("replaces").getAsString());
        assertEquals(tx.get("sequence").getAsLong(), bumped.get("sequence").getAsLong());
        String newId = bumped.get("transactionId").getAsString();

        JsonArray pending = obj(get("/api/mempool")).getAsJsonArray("transactions");
        assertEquals(1, pending.size());
        assertEquals(newId, pending.get(0).getAsJsonObject().get("transactionId").getAsString());
        assertEquals(404, get("/api/transactions/" + oldId).statusCode(), "The old version is gone");

        JsonObject mined = obj(post("/api/mine", "{\"minerAddress\":\"Miner\"}"));
        assertEquals(minBump, mined.get("totalFees").getAsDouble(), 1e-9);
        JsonObject alice = obj(get("/api/wallet/Alice"));
        assertEquals(50 - 10 - minBump, alice.get("balance").getAsDouble(), 1e-9, "Paid once, at the new fee");

        // Without a body the bump uses the smallest allowed fee.
        String id2 = send(1, 0).get("transactionId").getAsString();
        HttpResponse<String> auto = post("/api/transactions/" + id2 + "/bump", "");
        assertEquals(201, auto.statusCode(), auto.body());
        assertTrue(obj(auto).get("fee").getAsDouble() > 0);
    }

    @Test
    @DisplayName("A bump that is too small, or of a tx that is not pending, is refused with a reason")
    void bumpTooSmall_400() throws Exception {
        start(200);
        String id = send(10, 0.5).get("transactionId").getAsString();

        HttpResponse<String> small = post("/api/transactions/" + id + "/bump", "{\"fee\":0.501}");
        assertEquals(400, small.statusCode());
        assertTrue(obj(small).get("error").getAsString().contains("replacement refused"), small.body());

        assertEquals(400, post("/api/transactions/" + id + "/bump", "{\"fee\":-1}").statusCode());
        assertEquals(404, post("/api/transactions/nope/bump", "{\"fee\":1}").statusCode());
        assertEquals(1, obj(get("/api/mempool")).get("count").getAsInt(), "The original still waits");
    }

    @Test
    @DisplayName("A full mempool: a cheap tx gets a 400 with the minimum, the estimate and mempool show it")
    void mempoolFull_400_withMinFeeRate() throws Exception {
        start(2);
        send(1, 0.1);
        send(2, 0.2);

        JsonObject mempool = obj(get("/api/mempool"));
        assertEquals(2, mempool.get("maxMempoolTransactions").getAsInt());
        int sum = 0;
        for (var t : mempool.getAsJsonArray("transactions")) sum += t.getAsJsonObject().get("sizeBytes").getAsInt();
        assertEquals(sum, mempool.get("bytes").getAsInt());
        double min = mempool.get("mempoolMinFeeRate").getAsDouble();
        assertTrue(min > 0);

        HttpResponse<String> cheap = post("/api/transactions",
                "{\"sender\":\"Alice\",\"recipient\":\"Bob\",\"amount\":3,\"fee\":0.1}");
        assertEquals(400, cheap.statusCode());
        assertTrue(obj(cheap).get("error").getAsString().contains("mempool full"), cheap.body());

        JsonObject est = obj(get("/api/fees/estimate"));
        assertEquals(min, est.getAsJsonObject("slow").get("feeRate").getAsDouble(), 1e-12);
        double fastFee = est.getAsJsonObject("fast").get("fee").getAsDouble();
        send(3, fastFee);                                   // paying the estimate gets in
    }
}
