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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Milestone 12a read/query endpoints. The ApiServer is started on
 * a real port and driven with an HTTP client, exercising the full Javalin ->
 * Blockchain path.
 */
@DisplayName("API Read Endpoint Tests")
class ApiReadEndpointsTest {

    private static final int API_PORT_BASE = 17070;
    private static final int P2P_PORT_BASE = 18500;
    private static int counter = 0;

    private Node node;
    private ApiServer api;
    private int apiPort;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        // Node is never started (no TCP server needed); the API only reads it.
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

    @Test
    @DisplayName("GET /api/blocks returns the whole chain")
    void getBlocks_returnsChain() throws Exception {
        node.getBlockchain().addBlock("first");
        node.getBlockchain().addBlock("second");
        int expected = node.getBlockchain().getChainSize(); // genesis + 2 = 3

        HttpResponse<String> res = get("/api/blocks");

        assertEquals(200, res.statusCode(), "Should return 200");
        JsonArray blocks = JsonParser.parseString(res.body()).getAsJsonArray();
        assertEquals(expected, blocks.size(), "Should return every block in the chain");
    }

    @Test
    @DisplayName("GET /api/blocks/{index} returns the block at that index")
    void getBlockByIndex_returnsBlock() throws Exception {
        node.getBlockchain().addBlock("first");

        HttpResponse<String> res = get("/api/blocks/1");

        assertEquals(200, res.statusCode(), "Should return 200");
        JsonObject block = JsonParser.parseString(res.body()).getAsJsonObject();
        assertEquals(1, block.get("index").getAsInt(), "Should return block #1");
    }

    @Test
    @DisplayName("GET /api/blocks/{index} returns 404 for an out-of-range index")
    void getBlockByIndex_404WhenMissing() throws Exception {
        HttpResponse<String> res = get("/api/blocks/99");

        assertEquals(404, res.statusCode(), "Missing block should return 404");
        JsonObject body = JsonParser.parseString(res.body()).getAsJsonObject();
        assertTrue(body.has("error"), "404 body should carry an error message");
    }

    @Test
    @DisplayName("GET /api/blocks/latest returns the current tip")
    void getLatest_returnsTip() throws Exception {
        node.getBlockchain().addBlock("first");
        String tipHash = node.getBlockchain().getLatestBlock().getHash();

        HttpResponse<String> res = get("/api/blocks/latest");

        assertEquals(200, res.statusCode(), "Should return 200");
        JsonObject block = JsonParser.parseString(res.body()).getAsJsonObject();
        assertEquals(tipHash, block.get("hash").getAsString(), "Should return the tip block");
    }

    @Test
    @DisplayName("GET /api/network/status reports chain and node counts")
    void getStatus_returnsCounts() throws Exception {
        node.getBlockchain().addBlock("first");
        int chainLength = node.getBlockchain().getChainSize();

        HttpResponse<String> res = get("/api/network/status");

        assertEquals(200, res.statusCode(), "Should return 200");
        JsonObject status = JsonParser.parseString(res.body()).getAsJsonObject();
        assertEquals(chainLength, status.get("chainLength").getAsInt(),
                "Status should report the real chain length");
        assertEquals(0, status.get("connectedPeers").getAsInt(),
                "A lone node has no connected peers");
        assertEquals(node.getNodeId(), status.get("nodeId").getAsString(),
                "Status should report the node id");
    }
}
