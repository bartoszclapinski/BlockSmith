package com.blocksmith.demo;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.core.ChainParams;
import com.blocksmith.util.Target;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 28c: the demo that is published. GitHub Pages serves
 * {@code docs/demo} as it is committed, so nothing rebuilds it when the
 * dashboard or the API changes. These tests fail when it falls behind, and
 * say how to rebuild it.
 */
@DisplayName("The published demo: docs/demo matches the dashboard and the API of today (Sprint 28c)")
class DemoPublishedTest {

    private static final String REBUILD = " Rebuild docs/demo: mvn compile exec:java"
            + " -Dexec.mainClass=com.blocksmith.demo.DemoSite (add -Dexec.args=--keep if only the dashboard changed).";

    private static final ChainParams EASY_SIGNED = new ChainParams(Target.fromLeadingZeros(1), 10_000, 1_000_000, 4,
            Target.MAX, ChainParams.DEFAULT_MAX_FUTURE_DRIFT_MS,
            ChainParams.DEFAULT_MAX_BLOCK_BYTES, ChainParams.DEFAULT_MAX_BLOCK_TRANSACTIONS);

    private static final Path PROJECT = Path.of("").toAbsolutePath();
    private static final Path SITE = PROJECT.resolve("docs/demo");

    private static DemoRecording published;
    private static DemoRecording today;

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @BeforeAll
    static void readAndRecord() throws Exception {
        published = DemoRecording.fromScript(Files.readString(SITE.resolve(DemoSite.RECORDING_FILE)));
        try (DemoRecorder recorder = new DemoRecorder(EASY_SIGNED, DemoRecorder.Pace.FAST,
                new DemoRecorder.Ports(freePort(), freePort(), freePort(), freePort()))) {
            today = recorder.record();
        }
    }

    /** A path with its ids taken out: "/api/blocks/12" and "/api/blocks/13" are one kind of answer. */
    private static String kind(String path) {
        return path.replaceAll("CONTRACT:[0-9a-f]{64}", "{contract}")
                .replaceAll("0x[0-9a-f]{40}", "{address}")
                .replaceAll("[0-9a-f]{64}", "{hash}")
                .replaceAll("(?<=[/=])\\d+", "{n}")
                .replaceAll("search\\?q=.*", "search?q={query}");
    }

    private static void fields(String at, JsonElement e, Set<String> out) {
        if (e.isJsonObject()) {
            for (var entry : e.getAsJsonObject().entrySet()) fields(at + "." + entry.getKey(), entry.getValue(), out);
        } else if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) fields(at + "[]", x, out);
        } else {
            out.add(at);
        }
    }

    /** Every field of every kind of answer, over the whole recording. */
    private static Map<String, Set<String>> shape(DemoRecording recording) {
        Map<String, Set<String>> out = new TreeMap<>();
        for (DemoRecording.Frame frame : recording.frames()) {
            frame.answers().forEach((path, body) -> fields("", body, out.computeIfAbsent(kind(path), k -> new TreeSet<>())));
        }
        recording.misses().forEach((prefix, miss) -> fields("", miss.body(), out.computeIfAbsent("miss " + prefix, k -> new TreeSet<>())));
        return out;
    }

    @Test
    @DisplayName("docs/demo is what DemoSite builds from the sources today")
    void committedDemo_matchesTheDashboardSources(@TempDir Path out) throws Exception {
        Files.copy(SITE.resolve(DemoSite.RECORDING_FILE), out.resolve(DemoSite.RECORDING_FILE));
        DemoSite.build(PROJECT, null, out);
        for (String name : List.of("index.html", "app.js", "style.css", "demo.js", "demo.css")) {
            assertEquals(Files.readString(out.resolve(name)), DemoSite.text(SITE.resolve(name)),
                    "docs/demo/" + name + " is behind its source." + REBUILD);
        }
        try (var files = Files.list(SITE)) {
            assertEquals(Set.of("index.html", "app.js", "style.css", "demo.js", "demo.css", "recording.js"),
                    new TreeSet<>(files.map(f -> f.getFileName().toString()).toList()));
        }
    }

    @Test
    @DisplayName("the published recording has every field the API has today, and no other")
    void committedRecording_hasTheFieldsTheApiHasToday() {
        Map<String, Set<String>> then = shape(published);
        Map<String, Set<String>> now = shape(today);
        assertEquals(now.keySet(), then.keySet(), "the kinds of answers the recorder asks for changed." + REBUILD);
        for (String kind : now.keySet()) {
            assertEquals(now.get(kind), then.get(kind), "the fields of " + kind + " changed." + REBUILD);
        }
        assertTrue(now.size() >= 20, "status, peers, contracts, mempool, fees, totals, pages, blocks, transactions,"
                + " addresses, wallets, searches, misses: " + now.keySet());
    }

    @Test
    @DisplayName("the published recording is the recorder's story, at a real pace, on the real difficulty")
    void committedRecording_isTheStoryAtRealPace() {
        assertEquals(today.frames().stream().map(DemoRecording.Frame::title).toList(),
                published.frames().stream().map(DemoRecording.Frame::title).toList(),
                "the story changed." + REBUILD);
        DemoRecording.Frame first = published.frames().get(0);
        DemoRecording.Frame last = published.frames().get(published.frames().size() - 1);
        assertTrue(last.at() - first.at() > 60_000, "over a minute, not a few seconds: " + (last.at() - first.at()) + " ms");

        JsonObject status = last.answers().get("/api/network/status").getAsJsonObject();
        int height = status.get("chainLength").getAsInt() - 1;
        long previous = 0;
        for (int i = 1; i <= height; i++) {
            JsonObject block = last.answers().get("/api/blocks/" + i).getAsJsonObject();
            assertTrue(block.get("hash").getAsString().startsWith("000"), "real proof of work in block " + i);
            long time = block.get("timestamp").getAsLong();
            if (i > 1 && i < 19) assertTrue(time - previous >= 5_000, "blocks seconds apart: #" + i + " after " + (time - previous) + " ms");
            previous = time;
        }
        assertEquals(1, status.get("reorgs").getAsInt());
        assertEquals(published.activeAddress(), last.answers().get("/api/blocks/1").getAsJsonObject().get("miner").getAsString(),
                "the active wallet is the one that mined the first blocks");

        String script = published.toScript();
        assertTrue(script.length() < 1_500_000, "small enough to load at once: " + script.length());
    }
}
