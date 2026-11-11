package com.blocksmith.api;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.Block;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.Target;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP tests for the live difficulty fields (Milestone 17d). Blocks get
 * explicit timestamps so the expected numbers are exact.
 */
@DisplayName("API Difficulty Tests")
class ApiDifficultyTest {

    private static final int API_PORT_BASE = 18050;
    private static final int P2P_PORT_BASE = 20300;
    private static int counter = 0;

    private static final long T0 = 1_750_000_000_000L;
    private static final BigInteger EASY = Target.fromLeadingZeros(1);
    private static final ChainParams PARAMS = new ChainParams(EASY, 1_000, 4, 4, Target.MAX);

    private Blockchain chain;
    private Node node;
    private ApiServer api;
    private int apiPort;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        chain = new Blockchain(NoOpChainStore.INSTANCE, PARAMS);
        node = new Node(P2P_PORT_BASE + offset, chain);
        api = new ApiServer(node, apiPort);
        api.start();
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.stop();
        if (node != null) node.stop();
    }

    private JsonObject getObject(String path) throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), res.body());
        return JsonParser.parseString(res.body()).getAsJsonObject();
    }

    /** Heights 1..3, 500 ms apart: twice as fast as the 1 s target. */
    private void fastWindow() {
        for (int h = 1; h <= 3; h++) {
            Block b = new Block(h, List.of(), chain.getLatestBlock().getHash(), T0 + h * 500);
            b.mineBlock(chain.getNextTarget());
            assertTrue(chain.addBlock(b));
        }
    }

    @Test
    @DisplayName("Status carries the live difficulty fields")
    void status_hasLiveDifficultyFields() throws Exception {
        fastWindow();
        JsonObject status = getObject("/api/network/status");

        assertEquals(Target.toDifficulty(chain.getNextTarget()), status.get("difficulty").getAsDouble(), 1e-9);
        assertEquals(Target.toHex(chain.getNextTarget()), status.get("target").getAsString());
        assertEquals(1_000, status.get("targetBlockTimeMs").getAsLong());
        assertEquals(4, status.get("retargetInterval").getAsInt());
        assertEquals(4, status.get("blocksUntilRetarget").getAsInt());
        assertEquals(500, status.get("averageBlockTimeMs").getAsLong());
    }

    @Test
    @DisplayName("A fresh node has no average block time yet (field omitted)")
    void status_freshNode_noAverage() throws Exception {
        JsonObject status = getObject("/api/network/status");
        assertFalse(status.has("averageBlockTimeMs"));
        assertEquals(1.0, status.get("difficulty").getAsDouble(), 1e-9, "1 leading zero");
    }

    @Test
    @DisplayName("GET /api/network/difficulty lists epochs with their measured windows")
    void difficultyHistory_shape() throws Exception {
        fastWindow();
        JsonObject body = getObject("/api/network/difficulty");

        assertEquals(1_000, body.get("targetBlockTimeMs").getAsLong());
        assertEquals(4, body.get("retargetInterval").getAsInt());
        assertEquals(4, body.get("maxAdjustmentFactor").getAsInt());
        JsonArray epochs = body.getAsJsonArray("epochs");
        assertEquals(2, epochs.size());

        JsonObject initial = epochs.get(0).getAsJsonObject();
        assertEquals(0, initial.get("startHeight").getAsInt());
        assertFalse(initial.has("windowFirst"), "The initial epoch was not measured");

        JsonObject second = epochs.get(1).getAsJsonObject();
        assertEquals(4, second.get("startHeight").getAsInt());
        assertEquals(1, second.get("windowFirst").getAsInt());
        assertEquals(3, second.get("windowLast").getAsInt());
        assertEquals(1_000, second.get("actualTimespanMs").getAsLong());
        assertEquals(2_000, second.get("expectedTimespanMs").getAsLong());
        assertEquals(Target.toHex(chain.getNextTarget()), second.get("target").getAsString());
        assertEquals(body.get("currentDifficulty").getAsDouble(), second.get("difficulty").getAsDouble(), 1e-12);
    }
}
