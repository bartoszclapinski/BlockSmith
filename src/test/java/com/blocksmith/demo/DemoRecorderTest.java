package com.blocksmith.demo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.core.ChainParams;
import com.blocksmith.util.Target;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 28a: the demo recorder. The story is played once (no waiting, an
 * easy target, signatures required as on a real node) and every test reads
 * the same recording, with the recorded node still running next to it.
 */
@DisplayName("Demo recorder: a scripted session on a real node, every answer recorded (Sprint 28a)")
class DemoRecorderTest {

    /** Easy PoW, no retarget, signatures required. */
    private static final ChainParams EASY_SIGNED = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    private static final List<String> FIXED = List.of("/api/network/status", "/api/network/peers", "/api/contracts",
            "/api/mempool", "/api/fees/estimate", "/api/explorer/stats", "/api/explorer/blocks?limit=6",
            "/api/explorer/blocks?limit=20");

    private static DemoRecorder recorder;
    private static DemoRecording recording;
    private static final HttpClient client = HttpClient.newHttpClient();

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @BeforeAll
    static void recordOnce() throws Exception {
        recorder = new DemoRecorder(EASY_SIGNED, DemoRecorder.Pace.FAST,
                new DemoRecorder.Ports(freePort(), freePort(), freePort(), freePort()));
        recording = recorder.record();
    }

    @AfterAll
    static void stop() {
        if (recorder != null) recorder.close();
    }

    private static JsonElement live(String path) throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + recorder.apiPort() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), path + " -> " + res.body());
        return JsonParser.parseString(res.body());
    }

    private static DemoRecording.Frame last() {
        return recording.frames().get(recording.frames().size() - 1);
    }

    private static JsonObject object(DemoRecording.Frame frame, String path) {
        JsonElement e = frame.answers().get(path);
        assertNotNull(e, "not recorded: " + path);
        return e.getAsJsonObject();
    }

    private static DemoRecording.Frame titled(String start) {
        return recording.frames().stream().filter(f -> f.title().startsWith(start)).findFirst()
                .orElseThrow(() -> new AssertionError("no frame titled " + start));
    }

    private static double balance(String address) {
        return object(last(), "/api/wallet/" + address).get("balance").getAsDouble();
    }

    @Test
    @DisplayName("the session ends in the state it describes: balances, contracts, the peer, one reorg")
    void session_endsInTheStateItDescribes() {
        JsonObject status = object(last(), "/api/network/status");
        assertEquals(22, status.get("chainLength").getAsInt(), "genesis + 21");
        assertEquals(0, status.get("pendingTransactions").getAsInt());
        assertEquals(1, status.get("connectedPeers").getAsInt());
        assertEquals(1, status.get("reorgs").getAsInt());

        // Bob: +12 +6 -2.05 -3.05 +25 -5.6 +3 +20 (the hashlock) +1; Carol: 4 +8 +2 +1 +10 +5 +30 (the multisig) +2.
        assertEquals(56.3, balance(recorder.bobAddress()), 1e-9);
        assertEquals(62.0, balance(recorder.carolAddress()), 1e-9);
        assertEquals(150.0, balance(recorder.daveAddress()), 1e-9, "three blocks mined on the second node");
        assertEquals(recorder.aliceAddress(), recording.activeAddress());

        JsonObject stats = object(last(), "/api/explorer/stats");
        assertEquals(21 * 50.0, stats.get("coinsIssued").getAsDouble(), 1e-9);
        assertEquals(2, stats.get("contracts").getAsInt());
        assertEquals(0, stats.get("openContracts").getAsInt(), "both claimed");
        assertEquals(1050.0, balance(recorder.aliceAddress()) + 56.3 + 62.0 + 150.0, 1e-6, "every coin has an owner");

        // Fee order inside the block of the three transfers: 0.6, 0.3, 0.05 after the coinbase.
        int height = object(titled("Three transfers"), "/api/network/status").get("chainLength").getAsInt();
        JsonArray txs = object(last(), "/api/blocks/" + height).getAsJsonArray("transactions");
        assertEquals(4, txs.size());
        assertEquals(0.6, txs.get(1).getAsJsonObject().get("fee").getAsDouble());
        assertEquals(0.3, txs.get(2).getAsJsonObject().get("fee").getAsDouble());
        assertEquals(0.05, txs.get(3).getAsJsonObject().get("fee").getAsDouble());

        // The raised fee replaced the 0.01 one: one pending transfer before and after, a different id.
        JsonArray before = object(titled("A transfer with a small fee"), "/api/mempool").getAsJsonArray("transactions");
        JsonArray after = object(titled("Fee raised"), "/api/mempool").getAsJsonArray("transactions");
        assertEquals(1, before.size());
        assertEquals(1, after.size());
        assertEquals(0.01, before.get(0).getAsJsonObject().get("fee").getAsDouble());
        assertEquals(0.5, after.get(0).getAsJsonObject().get("fee").getAsDouble());
        assertNotEquals(before.get(0).getAsJsonObject().get("transactionId"),
                after.get(0).getAsJsonObject().get("transactionId"));
    }

    @Test
    @DisplayName("every frame holds every fixed path, a caption and a time; the chain in it is complete")
    void everyFrame_holdsEveryFixedPath_andACaption() {
        assertEquals(22, recording.frames().size());
        long previous = 0;
        for (DemoRecording.Frame f : recording.frames()) {
            assertFalse(f.title().isBlank());
            assertTrue(f.detail().length() > 20, f.title());
            assertTrue(f.at() >= previous, "frames are in the order they happened");
            previous = f.at();
            for (String path : FIXED) assertTrue(f.answers().containsKey(path), f.title() + " lacks " + path);

            int height = object(f, "/api/network/status").get("chainLength").getAsInt() - 1;
            for (int i = 0; i <= height; i++) {
                JsonObject block = object(f, "/api/blocks/" + i);
                String hash = block.get("hash").getAsString();
                assertEquals(block, object(f, "/api/blocks/hash/" + hash), "by height and by hash: the same answer");
                assertEquals(height - i + 1, block.get("confirmations").getAsInt(), "as deep as it was at that moment");
                assertEquals("block", object(f, "/api/explorer/search?q=" + hash).get("type").getAsString());
                assertEquals(i, object(f, "/api/explorer/search?q=" + i).get("blockIndex").getAsInt(), "by height");
                for (JsonElement tx : block.getAsJsonArray("transactions")) {
                    JsonObject t = tx.getAsJsonObject();
                    String id = t.get("transactionId").getAsString();
                    assertEquals(i, object(f, "/api/transactions/" + id).get("blockIndex").getAsInt());
                    assertEquals(id, object(f, "/api/explorer/search?q=" + id).get("transactionId").getAsString());
                    assertEquals("address", object(f, "/api/explorer/search?q=" + t.get("recipient").getAsString())
                            .get("type").getAsString().replace("contract", "address"));
                    assertTrue(f.answers().containsKey("/api/explorer/address/" + t.get("recipient").getAsString()
                            + "?limit=25&offset=0"), "the recipient's page");
                }
            }
            assertFalse(f.answers().containsKey("/api/blocks/" + (height + 1)), "nothing from the future");
            // Only searches that found something are kept; the rest get the recorded miss.
            f.answers().forEach((path, body) -> {
                if (path.startsWith("/api/explorer/search?q=")) assertTrue(body.getAsJsonObject().has("type"), path);
            });
        }
        DemoRecording.Frame first = recording.frames().get(0);
        assertTrue(first.title().startsWith("A node with 11 blocks"));
        assertEquals(0.0, object(first, "/api/wallet/" + recorder.daveAddress()).get("balance").getAsDouble(),
                "a wallet nobody paid yet can be tracked from the first frame");
        assertFalse(first.answers().containsKey("/api/explorer/search?q=" + recorder.daveAddress()),
                "but the node does not find an address that never appeared on the chain");

        // The pages of blocks, followed by their cursors, hold every height once, newest first.
        List<Integer> heights = new ArrayList<>();
        String page = "/api/explorer/blocks?limit=20";
        while (page != null) {
            JsonObject p = object(last(), page);
            for (JsonElement b : p.getAsJsonArray("blocks")) heights.add(b.getAsJsonObject().get("index").getAsInt());
            page = p.get("nextBefore") == null || p.get("nextBefore").isJsonNull() ? null
                    : "/api/explorer/blocks?limit=20&before=" + p.get("nextBefore").getAsInt();
        }
        List<Integer> expected = new ArrayList<>();
        for (int i = 21; i >= 0; i--) expected.add(i);
        assertEquals(expected, heights, "two pages: 21..2, then 1..0");

        // A contract is found by its id and by its address, in every frame after its deploy.
        for (JsonElement c : last().answers().get("/api/contracts").getAsJsonArray()) {
            String id = c.getAsJsonObject().get("contractId").getAsString();
            assertEquals("CONTRACT:" + id, object(last(), "/api/explorer/search?q=" + id).get("address").getAsString());
            assertEquals("CLAIMED", object(last(), "/api/explorer/address/CONTRACT:" + id + "?limit=25&offset=0")
                    .getAsJsonObject("contract").get("status").getAsString());
        }
        assertTrue(recording.recordedAt() >= previous);
    }

    @Test
    @DisplayName("the last frame is what the node answers now, path by path; a miss is the node's own 404")
    void lastFrame_equalsTheNodesAnswers_pathByPath() throws Exception {
        // Peers carry a "last seen" time and the status an uptime: they move while we read.
        Set<String> moving = Set.of("/api/network/peers", "/api/network/status");
        int compared = 0;
        for (Map.Entry<String, JsonElement> answer : last().answers().entrySet()) {
            if (moving.contains(answer.getKey())) continue;
            assertEquals(live(answer.getKey()), answer.getValue(), answer.getKey());
            compared++;
        }
        assertTrue(compared > 150, "every block, transaction, address and search of a 22-block chain: " + compared);

        assertEquals(Set.of("/api/explorer/search?q=", "/api/blocks/hash/", "/api/blocks/", "/api/transactions/"),
                recording.misses().keySet());
        for (DemoRecording.Miss miss : recording.misses().values()) {
            assertEquals(404, miss.status());
            assertFalse(miss.body().getAsJsonObject().get("error").getAsString().isBlank());
        }

        // Every address page is recorded to its last row.
        String alice = recorder.aliceAddress();
        JsonObject first = object(last(), "/api/explorer/address/" + alice + "?limit=25&offset=0");
        int total = first.get("transactionCount").getAsInt();
        assertTrue(total > 25, "Alice's history needs more than one page: " + total);
        JsonObject second = object(last(), "/api/explorer/address/" + alice + "?limit=25&offset=25");
        assertEquals(total - 25, second.getAsJsonArray("transactions").size());
    }

    @Test
    @DisplayName("the file reads back equal, and a part that repeats is written once")
    void recording_roundTrips_andStoresRepeatedPartsOnce() {
        String script = recording.toScript();
        assertEquals(recording, DemoRecording.fromScript(script));
        assertTrue(script.startsWith("window.BLOCKSMITH_RECORDING = {\"format\":1,"));
        assertThrows(IllegalArgumentException.class,
                () -> DemoRecording.fromJson(recording.toJson().replaceFirst("\"format\":1", "\"format\":2")));
        assertThrows(IllegalArgumentException.class, () -> DemoRecording.fromScript("alert(1)"));

        long written = 0;
        for (DemoRecording.Frame f : recording.frames()) {
            for (var a : f.answers().entrySet()) written += a.getKey().length() + a.getValue().toString().length();
        }
        assertTrue(script.length() * 3 < written, script.length() + " of " + written + " characters");

        // A signature is in the block, the transaction's page and two address pages of every later frame.
        JsonObject signed = object(last(), "/api/blocks/7").getAsJsonArray("transactions").get(1).getAsJsonObject();
        String signature = signed.get("signature").toString();
        assertTrue(signature.length() >= DemoRecording.MIN_SHARED);
        assertEquals(script.indexOf(signature), script.lastIndexOf(signature), "written once");
        assertTrue(script.indexOf(signature) > 0);
    }

    @Test
    @DisplayName("everything the activity feed reports happens between two frames: block, pending, deploy, claim, peer, reorg")
    void feedEvents_areAllInTheFrames() {
        List<String> events = new ArrayList<>();
        JsonObject previous = null;
        Map<String, String> contracts = new HashMap<>();
        for (DemoRecording.Frame f : recording.frames()) {
            JsonObject status = object(f, "/api/network/status");
            List<String> now = new ArrayList<>();
            if (previous != null) {
                if (status.get("reorgs").getAsInt() > previous.get("reorgs").getAsInt()) now.add("reorg");
                if (status.get("chainLength").getAsInt() > previous.get("chainLength").getAsInt()) now.add("block");
                if (status.get("pendingTransactions").getAsInt() > previous.get("pendingTransactions").getAsInt()) now.add("pending");
                if (status.get("connectedPeers").getAsInt() > previous.get("connectedPeers").getAsInt()) now.add("peer joined");
                if (status.get("connectedPeers").getAsInt() < previous.get("connectedPeers").getAsInt()) now.add("peer left");
            }
            for (JsonElement c : f.answers().get("/api/contracts").getAsJsonArray()) {
                String id = c.getAsJsonObject().get("contractId").getAsString();
                String state = c.getAsJsonObject().get("status").getAsString();
                String before = contracts.put(id, state);
                if (previous != null && before == null) now.add("deployed");
                else if (before != null && !before.equals("CLAIMED") && state.equals("CLAIMED")) now.add("claimed");
            }
            // A replacement keeps the number of pending transfers: the feed is silent, the mempool shows it.
            if (previous != null && !f.title().startsWith("Fee raised")) {
                assertFalse(now.isEmpty(), "nothing the feed would show happened in: " + f.title());
            }
            events.addAll(now);
            previous = status;
        }
        for (String kind : List.of("block", "pending", "deployed", "claimed", "peer joined", "peer left", "reorg")) {
            assertTrue(events.contains(kind), kind + " in " + events);
        }
        assertEquals(2, events.stream().filter("deployed"::equals).count());
        assertEquals(2, events.stream().filter("claimed"::equals).count());

        // The reorg frame: one block replaced, its transfer back in the mempool, the dropped block gone.
        DemoRecording.Frame alone = titled("Block #19 mined here, alone");
        DemoRecording.Frame reorg = titled("Chain reorganised");
        JsonObject lastReorg = object(reorg, "/api/network/status").getAsJsonObject("lastReorg");
        assertEquals(1, lastReorg.get("depth").getAsInt());
        assertEquals(18, lastReorg.get("forkHeight").getAsInt());
        assertEquals(1, lastReorg.get("transactionsBack").getAsInt());
        String dropped = object(alone, "/api/blocks/19").get("hash").getAsString();
        assertEquals(dropped, lastReorg.get("oldTip").getAsString());
        assertFalse(reorg.answers().containsKey("/api/blocks/hash/" + dropped), "not on the chain any more");
        assertEquals(21, object(reorg, "/api/network/status").get("chainLength").getAsInt(), "the other branch: #19, #20");
        assertEquals(recorder.daveAddress(), object(reorg, "/api/blocks/19").get("miner").getAsString());

        String transfer = object(alone, "/api/blocks/19").getAsJsonArray("transactions").get(1).getAsJsonObject()
                .get("transactionId").getAsString();
        assertEquals("pending", object(reorg, "/api/transactions/" + transfer).get("status").getAsString());
        assertEquals(21, object(last(), "/api/transactions/" + transfer).get("blockIndex").getAsInt());
    }
}
