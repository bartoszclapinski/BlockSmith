package com.blocksmith.demo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.api.ApiServer;
import com.blocksmith.core.Blockchain;
import com.blocksmith.core.ChainParams;
import com.blocksmith.network.Node;
import com.blocksmith.storage.NoOpChainStore;
import com.blocksmith.util.Target;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 28b: the demo player and the site built around it. The player is
 * JavaScript, so these tests check what can be checked from here: that the
 * built page is the dashboard plus the player, that the recording holds an
 * answer for every path the dashboard reads, and that the seams in app.js
 * leave no way around the player. The player itself is driven in a browser
 * in the sprint's live check.
 */
@DisplayName("Demo player: the dashboard answered from a recording (Sprint 28b)")
class DemoSiteTest {

    private static final ChainParams EASY_SIGNED = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    private static final Path PROJECT = Path.of("").toAbsolutePath();
    private static final Path DASHBOARD = PROJECT.resolve("src/main/resources/public");
    private static final Path PLAYER = PROJECT.resolve("src/main/demo");

    private static DemoRecording recording;
    private static String appJs;
    private static String demoJs;

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @BeforeAll
    static void recordOnce() throws Exception {
        try (DemoRecorder recorder = new DemoRecorder(EASY_SIGNED, DemoRecorder.Pace.FAST,
                new DemoRecorder.Ports(freePort(), freePort(), freePort(), freePort()))) {
            recording = recorder.record();
        }
        appJs = DemoSite.text(DASHBOARD.resolve("app.js"));
        demoJs = DemoSite.text(PLAYER.resolve("demo.js"));
    }

    @AfterAll
    static void forget() {
        recording = null;
    }

    /** Every "/api/..." text in app.js, split by whether its line sends it with postJson. */
    private static List<String> apiPaths(boolean posted) {
        List<String> out = new ArrayList<>();
        Pattern literal = Pattern.compile("\"(/api/[^\"]*)\"");
        for (String line : appJs.split("\n")) {
            if (line.contains("postJson(") != posted) continue;
            Matcher m = literal.matcher(line);
            while (m.find()) out.add(m.group(1));
        }
        return out;
    }

    @Test
    @DisplayName("the built page is the dashboard's page plus three lines; its files are the dashboard's and the player's")
    void builtPage_isTheDashboardPlusThePlayer(@TempDir Path out) throws Exception {
        DemoSite.build(PROJECT, recording, out);

        String dashboard = DemoSite.text(DASHBOARD.resolve("index.html"));
        String page = Files.readString(out.resolve("index.html"));
        assertEquals(DemoSite.page(dashboard), page);
        int rec = page.indexOf("<script src=\"recording.js\"></script>");
        int player = page.indexOf("<script src=\"demo.js\"></script>");
        int app = page.indexOf("<script src=\"app.js\"></script>");
        assertTrue(rec > 0 && rec < player && player < app, "the recording, then the player, then the dashboard");
        assertTrue(page.indexOf("href=\"style.css\"") < page.indexOf("href=\"demo.css\""), "the player's style after the dashboard's");
        assertFalse(page.contains("=\"/"), "no path from the root of the host: the demo lives in a sub-folder");
        assertTrue(page.contains("<title>BlockSmith Demo: a recorded node</title>"));

        // Taking the added lines out gives the dashboard's page back.
        List<String> kept = new ArrayList<>();
        for (String line : page.split("\n", -1)) {
            if (line.contains("demo.css") || line.contains("recording.js") || line.contains("demo.js")
                    || line.contains("<meta name=\"description\"") || line.startsWith("<!-- The recorded demo")
                    || line.startsWith("     answered; demo.js")) continue;
            kept.add(line.replace("BlockSmith Demo: a recorded node", "BlockSmith Dashboard"));
        }
        assertEquals(dashboard, String.join("\n", kept));

        for (String name : DemoSite.DASHBOARD_FILES) {
            assertEquals(DemoSite.text(DASHBOARD.resolve(name)), Files.readString(out.resolve(name)), name);
        }
        for (String name : DemoSite.PLAYER_FILES) {
            assertEquals(DemoSite.text(PLAYER.resolve(name)), Files.readString(out.resolve(name)), name);
        }
        assertEquals(recording, DemoRecording.fromScript(Files.readString(out.resolve(DemoSite.RECORDING_FILE))));
        try (var files = Files.list(out)) {
            assertEquals(6, files.count(), "index.html, app.js, style.css, demo.js, demo.css, recording.js");
        }

        // Keeping the recording: the page is rebuilt, the recording is not touched.
        Files.writeString(out.resolve("index.html"), "old");
        DemoSite.build(PROJECT, null, out);
        assertEquals(page, Files.readString(out.resolve("index.html")));
        assertEquals(recording, DemoRecording.fromScript(Files.readString(out.resolve(DemoSite.RECORDING_FILE))));
        assertThrows(IllegalStateException.class, () -> DemoSite.build(PROJECT, null, out.resolve("empty")));
        assertThrows(IllegalStateException.class, () -> DemoSite.page("<html></html>"));
    }

    @Test
    @DisplayName("the recording answers every path the dashboard reads, with the page sizes the dashboard asks for")
    void recording_answersEveryGetPathTheDashboardAsks() {
        List<String> reads = apiPaths(false);
        assertEquals(Set.of("/api/network/status", "/api/network/peers", "/api/contracts", "/api/mempool",
                "/api/fees/estimate", "/api/wallet/", "/api/blocks/", "/api/blocks/hash/", "/api/transactions/",
                "/api/explorer/stats", "/api/explorer/blocks?limit=", "/api/explorer/blocks?limit=6",
                "/api/explorer/address/", "/api/explorer/search?q="), new TreeSet<>(reads),
                "a new path in app.js must be recorded too");
        for (DemoRecording.Frame frame : recording.frames()) {
            for (String path : reads) {
                assertTrue(frame.answers().keySet().stream().anyMatch(k -> k.startsWith(path)),
                        frame.title() + " has no answer for " + path);
            }
        }
        assertTrue(appJs.contains("const BLOCK_PAGE = " + DemoRecorder.BLOCK_PAGE + ";"));
        assertTrue(appJs.contains("const HISTORY_PAGE = " + DemoRecorder.HISTORY_PAGE + ";"));
        assertTrue(appJs.contains("\"/api/explorer/blocks?limit=" + DemoRecorder.DECK_BLOCKS + "\""));
        assertTrue(appJs.contains("\"?limit=\" + HISTORY_PAGE + \"&offset=\" + explorer.offset"), "the address pages, as recorded");
        assertTrue(appJs.contains("\"/api/explorer/blocks?limit=\" + BLOCK_PAGE + \"&before=\" + explorer.nextBefore"));
    }

    @Test
    @DisplayName("everything that would change the chain goes out as a POST, and the player refuses every POST")
    void everyPostPath_isRefusedByThePlayer() {
        assertEquals(Set.of("/api/mine", "/api/transactions", "/api/transactions/", "/api/transactions/prepare",
                "/api/network/peers", "/api/contracts/", "/api/contracts/prepare", "/api/multisig/create",
                "/api/multisig/claim", "/api/multisig/claim/prepare"), new TreeSet<>(apiPaths(true)));
        assertTrue(appJs.contains("function postJson(path, body) {\n    return fetchJson(path, {\n        method: \"POST\","),
                "postJson is fetchJson with the method POST");
        assertTrue(demoJs.contains("if (options && options.method && options.method !== \"GET\") return Promise.reject(new Error(READ_ONLY));"));
        assertTrue(demoJs.contains("const READ_ONLY = \"A recording cannot be changed. Run BlockSmith to mine, send and deploy.\";"));
        assertTrue(demoJs.indexOf("options.method !== \"GET\"") < demoJs.indexOf("frames[frame].answers"),
                "refused before anything is looked up");
    }

    @Test
    @DisplayName("in the demo the dashboard never reaches the network or the keystore; the player keeps nothing")
    void demoMode_neverReachesTheKeystoreOrTheNetwork() {
        assertTrue(appJs.contains("const DEMO = window.BlockSmithDemo || null;"));
        int fetchJson = appJs.indexOf("async function fetchJson(path, options) {");
        int seam = appJs.indexOf("if (DEMO) return DEMO.answer(path, options);");
        int network = appJs.indexOf("await fetch(path, options);");
        assertTrue(fetchJson > 0 && fetchJson < seam && seam < network, "the player is asked before the network");
        assertEquals(1, appJs.split("\\bfetch\\(", -1).length - 1, "one fetch in the whole dashboard: the one behind the seam");
        assertFalse(appJs.contains("XMLHttpRequest") || appJs.contains("WebSocket(") || appJs.contains("sendBeacon"));

        int unlock = appJs.indexOf("async function ensureUnlocked() {");
        int refuse = appJs.indexOf("if (DEMO) throw new Error(DEMO.readOnly);");
        assertTrue(unlock > 0 && unlock < refuse && refuse < appJs.indexOf("if (vault.aes) {", unlock),
                "refused before the passphrase dialog");
        assertTrue(appJs.contains("async function createBrowserWallet(label) {\n    await ensureUnlocked();"),
                "a new key goes through the same gate");

        assertTrue(appJs.contains("function nowMs() { return DEMO ? DEMO.now() : Date.now(); }"));
        assertEquals(1, appJs.split("Date\\.now\\(\\)", -1).length - 1, "every age is counted from nowMs()");
        assertTrue(appJs.contains("if (DEMO) DEMO.hold(\"tour\", true);") && appJs.contains("if (DEMO) DEMO.hold(\"tour\", false);"));
        assertTrue(appJs.contains("state.activeAddress = DEMO.activeAddress;"));
        assertTrue(appJs.contains("restart: function () { state.prev = null; state.feed = []; refresh(); }"));

        // The player: answers and a clock, nothing else.
        for (String forbidden : List.of("fetch(", "XMLHttpRequest", "localStorage", "sessionStorage", "crypto.", "innerHTML", "eval(")) {
            assertFalse(demoJs.contains(forbidden), "demo.js uses " + forbidden);
        }
        assertTrue(demoJs.contains("window.BlockSmithDemo = {"));
        assertTrue(demoJs.contains("rec.format === FORMAT") && demoJs.contains("const FORMAT = " + DemoRecording.FORMAT + ";"));
        assertTrue(demoJs.contains("if (keys.length === 1 && keys[0] === \"$\") return whole(rec.pool[v.$]);"), "the pool, as DemoRecording writes it");
        assertTrue(demoJs.contains("return m.body.error.split(m.probe).join(key.slice(best.length).trim());"),
                "a miss is the node's sentence with what this visitor asked for");
        assertTrue(appJs.contains("if (DEMO && DEMO_ACTIONS[type]) return openDemoDrawer(type);"),
                "an action opens its explanation, not a form that cannot be sent");
        assertTrue(demoJs.contains("const deepLink = /^#\\/(blocks|block|tx|address)\\b/.test(location.hash);"));
    }

    @Test
    @DisplayName("the node still serves the dashboard: relative assets, no player, no demo mode")
    void nodeStillServesTheDashboard_withRelativeAssets() throws Exception {
        Node node = new Node(freePort(), new Blockchain(NoOpChainStore.INSTANCE, EASY_SIGNED));
        int port = freePort();
        ApiServer api = new ApiServer(node, port);
        api.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            java.util.function.Function<String, HttpResponse<String>> get = path -> {
                try {
                    return client.send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            String page = get.apply("/").body();
            assertTrue(page.contains("<link rel=\"stylesheet\" href=\"style.css\">"));
            assertTrue(page.contains("<script src=\"app.js\"></script>"));
            assertFalse(page.contains("demo.js") || page.contains("recording.js") || page.contains("demo.css"));
            assertEquals(200, get.apply("/style.css").statusCode());
            assertEquals(200, get.apply("/app.js").statusCode());
            assertEquals(404, get.apply("/demo.js").statusCode(), "the player is not part of the node");
            assertEquals(404, get.apply("/recording.js").statusCode());
        } finally {
            api.stop();
            node.stop();
        }
    }
}
