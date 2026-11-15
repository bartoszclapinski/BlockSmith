package com.blocksmith.network;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.BlockSmithNode;
import com.blocksmith.BlockSmithNode.Options;
import com.blocksmith.BlockSmithNode.Running;
import com.blocksmith.core.Blockchain;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 26a: joining a network.
 *
 * <p>The planning probe: two {@code BlockSmithNode} processes never connected
 * (no peer argument, no connect API), and a peer that restarted could never
 * be dialled again ("Already connected"). These tests start nodes exactly the
 * way {@code main} does ({@link BlockSmithNode#parseArgs} then
 * {@link BlockSmithNode#launch}), each with its own H2 data directory.
 */
@DisplayName("Joining a network (Sprint 26a)")
class JoiningTest {

    @TempDir
    Path tmp;

    private final List<Running> running = new ArrayList<>();
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        for (Running r : running) {
            try {
                r.close();
            } catch (RuntimeException e) {
                // already closed by the test
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /** Starts a node like the command line would: "p2p api [args...]". */
    private Running start(String dir, int p2p, int api, String... extra) throws IOException {
        String[] args = new String[extra.length + 2];
        args[0] = String.valueOf(p2p);
        args[1] = String.valueOf(api);
        System.arraycopy(extra, 0, args, 2, extra.length);
        Running r = BlockSmithNode.launch(BlockSmithNode.parseArgs(args, null).withDataDir(tmp.resolve(dir)));
        running.add(r);
        return r;
    }

    private HttpResponse<String> post(int apiPort, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + apiPort + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String get(int apiPort, String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + apiPort + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    private void mine(int apiPort) throws Exception {
        assertEquals(201, post(apiPort, "/api/mine", "{\"minerAddress\":\"alice\"}").statusCode());
    }

    private static Blockchain chain(Running r) {
        return r.node().getBlockchain();
    }

    private static boolean sameTip(Running a, Running b) {
        return chain(a).getLatestBlock().getHash().equals(chain(b).getLatestBlock().getHash());
    }

    private static boolean connected(Running r, int count) {
        return r.node().getPeerManager().getConnectedCount() == count;
    }

    private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
        waitFor(condition, what, 20_000);
    }

    private static void waitFor(BooleanSupplier condition, String what, long ms) throws InterruptedException {
        long deadline = System.currentTimeMillis() + ms;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: " + what);
            Thread.sleep(50);
        }
    }

    @Test
    @DisplayName("the probe: a node started with --peer joins, catches up, and receives new blocks")
    void twoNodesFromTheCommandLine_shareBlocks() throws Exception {
        int pA = freePort(), aA = freePort(), pB = freePort(), aB = freePort();
        Running a = start("a", pA, aA);
        for (int i = 0; i < 3; i++) mine(aA);
        assertEquals(4, chain(a).getChainSize());

        // B knows only A's address, given on the command line.
        Running b = start("b", pB, aB, "--peer", "localhost:" + pA);
        waitFor(() -> connected(a, 1) && connected(b, 1), "A and B to connect");
        waitFor(() -> chain(b).getChainSize() == 4, "B to catch up on A's blocks");
        assertTrue(sameTip(a, b));

        // New blocks flow both ways over the one link.
        mine(aA);
        waitFor(() -> chain(b).getChainSize() == 5, "A's new block to reach B");
        mine(aB);
        waitFor(() -> chain(a).getChainSize() == 6, "B's new block to reach A");
        assertTrue(sameTip(a, b));

        // B sees Alice's coins, which the probe showed as 0.
        JsonObject wallet = JsonParser.parseString(get(aB, "/api/wallet/alice")).getAsJsonObject();
        assertTrue(wallet.get("balance").getAsDouble() > 0, wallet.toString());
        assertTrue(get(aB, "/api/network/peers").contains("localhost:" + pA));

        // The same peers from -Dblocksmith.peers; bad input is refused.
        assertEquals(List.of("localhost:" + pA, "10.0.0.2:8333"),
                BlockSmithNode.parseArgs(new String[] {"1", "2"}, " localhost:" + pA + ",10.0.0.2:8333,").peers());
        assertEquals(List.of("localhost:" + pA),
                BlockSmithNode.parseArgs(new String[] {"--peer", "localhost:" + pA, "--peer", "localhost:" + pA}, null).peers());
        assertThrows(IllegalArgumentException.class, () -> BlockSmithNode.parseArgs(new String[] {"--peer", "nohost"}, null));
        assertThrows(IllegalArgumentException.class, () -> BlockSmithNode.parseArgs(new String[] {"--peer"}, null));
        assertThrows(IllegalArgumentException.class, () -> BlockSmithNode.parseArgs(new String[0], "a:1,b:99999"));
        assertThrows(IllegalArgumentException.class, () -> BlockSmithNode.parseArgs(new String[] {"--peers", "a:1"}, null));
    }

    @Test
    @DisplayName("a peer that restarts is reached again, whichever side dialled")
    void peerRestarts_reconnectWorks() throws Exception {
        int pA = freePort(), aA = freePort(), pB = freePort(), aB = freePort();
        Running a = start("a", pA, aA);
        Running b = start("b", pB, aB, "--peer", "localhost:" + pA);
        waitFor(() -> connected(a, 1) && connected(b, 1), "A and B to connect");

        // 1. B (the side that dialled) restarts. A notices at once.
        b.close();
        waitFor(() -> connected(a, 0), "A to see B's link drop");
        mine(aA);
        Running b2 = start("b", pB, aB, "--peer", "localhost:" + pA);
        waitFor(() -> connected(a, 1) && connected(b2, 1), "B to dial A again");
        waitFor(() -> sameTip(a, b2), "B to catch up on the block it missed");
        mine(aA);
        waitFor(() -> sameTip(a, b2), "A to send its next block over the new link");

        // 2. A (the side that was dialled) restarts. B's link is DISCONNECTED
        //    at once, and redialling it no longer says "Already connected".
        // A closes the links B opened to it, so B knows at once (not at its
        // next PING, up to 10 s later), and stopping does not hang on them.
        long t0 = System.currentTimeMillis();
        a.close();
        waitFor(() -> connected(b2, 0), "B to see A's link drop", 2_000);
        assertTrue(System.currentTimeMillis() - t0 < 4_000, "stop + drop took too long");
        assertEquals(PeerState.DISCONNECTED,
                b2.node().getPeerManager().getPeer("localhost:" + pA).getState());
        Running a2 = start("a", pA, aA);
        // What the heartbeat does every 10 s (called here so the test does not wait).
        waitFor(() -> {
            b2.node().maintainConnections();
            return connected(b2, 1);
        }, "B to redial A");
        waitFor(() -> connected(a2, 1), "A to register B");
        mine(aA);
        waitFor(() -> sameTip(a2, b2), "A's block to reach B after the reconnect");
    }

    @Test
    @DisplayName("POST /api/network/peers: 400 bad address, 502 unreachable, 201 connected, 409 again")
    void connectApi_errors() throws Exception {
        int pA = freePort(), aA = freePort(), pB = freePort(), aB = freePort();
        Running a = start("a", pA, aA);
        Running b = start("b", pB, aB);
        mine(aB);

        for (String bad : new String[] {"{}", "{\"address\":\"nonsense\"}", "{\"address\":\"localhost:70000\"}",
                "{\"address\":\":8333\"}", "{\"address\":42}", "not json"}) {
            assertEquals(400, post(aA, "/api/network/peers", bad).statusCode(), bad);
        }

        int closed = freePort();
        HttpResponse<String> unreachable = post(aA, "/api/network/peers", "{\"address\":\"localhost:" + closed + "\"}");
        assertEquals(502, unreachable.statusCode(), unreachable.body());
        assertTrue(a.node().getPersistentPeers().isEmpty(), "a failed connect is not kept");

        HttpResponse<String> ok = post(aA, "/api/network/peers", "{\"address\":\"localhost:" + pB + "\"}");
        assertEquals(201, ok.statusCode(), ok.body());
        JsonObject peer = JsonParser.parseString(ok.body()).getAsJsonObject();
        assertEquals(b.node().getNodeId(), peer.get("nodeId").getAsString());
        assertEquals("localhost:" + pB, peer.get("address").getAsString());
        assertEquals(java.util.Set.of("localhost:" + pB), a.node().getPersistentPeers());
        waitFor(() -> sameTip(a, b), "A to catch up on B's block");

        HttpResponse<String> again = post(aA, "/api/network/peers", "{\"address\":\"localhost:" + pB + "\"}");
        assertEquals(409, again.statusCode(), again.body());
        assertTrue(get(aA, "/api/network/peers").contains(b.node().getNodeId()));
    }
}
