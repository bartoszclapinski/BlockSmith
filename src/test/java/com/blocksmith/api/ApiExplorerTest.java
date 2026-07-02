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
 * Server-side tests for the Milestone 13b explorer view. The dashboard itself
 * is verified by running it; here we check the shell exposes the mount points
 * app.js targets and that the blocks endpoint carries the fields the UI reads.
 */
@DisplayName("API Explorer Tests")
class ApiExplorerTest {

    private static final int API_PORT_BASE = 17400;
    private static final int P2P_PORT_BASE = 18900;
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

    @Test
    @DisplayName("Served index.html exposes the mount points app.js targets")
    void indexHtml_containsExpectedMountPoints() throws Exception {
        String html = get("/").body();

        assertTrue(html.contains("id=\"network-stats\""), "Needs the network-stats mount point");
        assertTrue(html.contains("id=\"block-list\""), "Needs the block-list mount point");
        assertTrue(html.contains("/app.js"), "Should load the dashboard script");
    }

    @Test
    @DisplayName("Blocks endpoint carries the fields the explorer reads")
    void blocksEndpoint_shapeMatchesUiExpectations() throws Exception {
        node.getBlockchain().addBlock("explorer");

        JsonArray blocks = JsonParser.parseString(get("/api/blocks").body()).getAsJsonArray();
        assertFalse(blocks.isEmpty(), "Chain should have at least the genesis block");

        JsonObject block = blocks.get(blocks.size() - 1).getAsJsonObject();
        for (String field : new String[]{"index", "hash", "previousHash", "nonce", "transactions"}) {
            assertTrue(block.has(field), "Block JSON should include '" + field + "' for the UI");
        }
        assertTrue(block.get("transactions").isJsonArray(), "transactions should be an array");
    }
}
