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
 * Tests for Milestone 13a: the ApiServer serves the static web dashboard
 * alongside the REST API (both from the same Javalin instance).
 */
@DisplayName("API Static Hosting Tests")
class ApiStaticHostingTest {

    private static final int API_PORT_BASE = 17300;
    private static final int P2P_PORT_BASE = 18800;
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
    @DisplayName("GET / serves the HTML dashboard shell")
    void root_servesHtmlShell() throws Exception {
        HttpResponse<String> res = get("/");

        assertEquals(200, res.statusCode(), "Root should return 200");
        assertTrue(res.body().contains("BlockSmith"),
                "Dashboard shell should mention BlockSmith");
        assertTrue(res.body().contains("<html"),
                "Response should be an HTML document");
    }

    @Test
    @DisplayName("Static assets are served")
    void staticAsset_isServed() throws Exception {
        HttpResponse<String> res = get("/style.css");

        assertEquals(200, res.statusCode(), "Static CSS should be served");
        assertFalse(res.body().isBlank(), "CSS body should not be empty");
    }

    @Test
    @DisplayName("The REST API still responds alongside static hosting")
    void apiStillRespondsAlongsideStatic() throws Exception {
        HttpResponse<String> res = get("/api/network/status");

        assertEquals(200, res.statusCode(), "API should still respond");
        JsonObject status = JsonParser.parseString(res.body()).getAsJsonObject();
        assertTrue(status.has("chainLength"), "Status JSON should be intact");
    }
}
