package com.blocksmith.api;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.TestChains;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.Transaction;
import com.blocksmith.network.Node;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 26c: the mempool flows both ways when two nodes connect, and the
 * dashboard shows the node's peers and can connect to one.
 */
@DisplayName("Network end-to-end: mempool both ways, dashboard peer panel (Sprint 26c)")
class NetworkPanelTest {

    private final List<Node> nodes = new ArrayList<>();
    private final List<ApiServer> apis = new ArrayList<>();
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        apis.forEach(ApiServer::stop);
        nodes.forEach(Node::stop);
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private Node node(Blockchain chain) throws IOException {
        Node n = new Node(freePort(), chain);
        n.start();
        nodes.add(n);
        return n;
    }

    private int api(Node n) throws IOException {
        int port = freePort();
        ApiServer api = new ApiServer(n, port);
        api.start();
        apis.add(api);
        return port;
    }

    private String get(int port, String path) throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), path);
        return res.body();
    }

    private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: " + what);
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("the dialled node pulls the dialler's pending transactions too")
    void inboundSide_pullsMempool() throws Exception {
        Node a = node(TestChains.unsigned());

        // B has a block A lacks and a pending transaction spending it.
        Blockchain chainB = TestChains.unsigned();
        chainB.minePendingTransactions("Miner1");
        Transaction tx = new Transaction("Miner1", "Alice", 30.0);
        assertTrue(chainB.addTransaction(tx));
        Node b = node(chainB);

        // B dials A. Before Sprint 26c only B (the dialler) asked for a mempool.
        b.connectToPeer("localhost", a.getPort());
        waitFor(() -> a.getBlockchain().getChainSize() == 2, "A to catch up on B's block");
        waitFor(() -> a.getBlockchain().getPendingCount() == 1, "A to pull B's pending transaction");
        assertEquals(tx.getTransactionId(),
                a.getBlockchain().getPendingTransactions().get(0).getTransactionId());
    }

    @Test
    @DisplayName("the dashboard has a peer panel wired to GET/POST /api/network/peers")
    void dashboard_peerPanel() throws Exception {
        Node a = node(TestChains.unsigned());
        Node b = node(TestChains.unsigned());
        int apiA = api(a);
        int apiB = api(b);

        String html = get(apiA, "/");
        assertTrue(html.contains("id=\"peer-list\""), "peer list");
        assertTrue(html.contains("id=\"peer-form\"") && html.contains("id=\"peer-address\""), "connect form");
        assertTrue(html.contains("data-tour=\"peers\""), "tour step anchor");

        String js = get(apiA, "/app.js");
        assertTrue(js.contains("function renderPeers(peers)"), "renderer");
        assertTrue(js.contains("renderPeers(peers);"), "refresh renders the peers it already fetches");
        assertTrue(js.contains("postJson(\"/api/network/peers\", { address: address })"), "connect posts the address");
        assertTrue(js.contains("addEventListener(\"submit\", onPeerConnect)"), "form wired");
        assertTrue(js.contains("sel: \"peers\""), "tour explains the panel");

        // What the panel shows: the same link as outbound on one end, inbound on the other.
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiA + "/api/network/peers"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"address\":\"localhost:" + b.getPort() + "\"}")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(201, res.statusCode(), res.body());
        waitFor(() -> b.getPeerManager().getConnectedCount() == 1, "B to register A");

        JsonObject fromA = JsonParser.parseString(get(apiA, "/api/network/peers")).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals("outbound", fromA.get("direction").getAsString());
        assertEquals(b.getNodeId(), fromA.get("nodeId").getAsString());
        assertEquals("CONNECTED", fromA.get("state").getAsString());
        assertTrue(fromA.get("connectedAt").getAsLong() > 0);

        JsonArray fromB = JsonParser.parseString(get(apiB, "/api/network/peers")).getAsJsonArray();
        assertEquals(1, fromB.size());
        assertEquals("inbound", fromB.get(0).getAsJsonObject().get("direction").getAsString());
        assertEquals(a.getNodeId(), fromB.get(0).getAsJsonObject().get("nodeId").getAsString());
    }
}
