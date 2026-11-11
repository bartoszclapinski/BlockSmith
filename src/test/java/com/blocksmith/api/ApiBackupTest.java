package com.blocksmith.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.blocksmith.core.Blockchain;
import com.blocksmith.network.Node;
import com.blocksmith.storage.H2ChainStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP tests for {@code POST /api/admin/backup} (Milestone 16d).
 */
@DisplayName("API Backup Tests")
class ApiBackupTest {

    private static final int API_PORT_BASE = 17900;
    private static final int P2P_PORT_BASE = 18980;
    private static int counter = 0;

    @TempDir
    Path tmp;

    private H2ChainStore store;
    private Node node;
    private ApiServer api;
    private int apiPort;
    private final HttpClient client = HttpClient.newHttpClient();

    /** Starts an API over a node; with a file store and backups enabled if asked. */
    private void start(boolean withBackups) {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        Blockchain chain;
        if (withBackups) {
            store = H2ChainStore.openFile(tmp.resolve("data"));
            chain = new Blockchain(store);
        } else {
            chain = new Blockchain();
        }
        node = new Node(P2P_PORT_BASE + offset, chain);
        api = new ApiServer(node, apiPort);
        if (withBackups) api.enableBackups(tmp.resolve("data/backups"));
        api.start();
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.stop();
        if (node != null) node.stop();
        if (store != null) store.close();
    }

    private HttpResponse<String> postBackup() throws Exception {
        return client.send(HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + apiPort + "/api/admin/backup"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("POST /api/admin/backup returns the path of a backup that exists")
    void backup_returnsExistingPath() throws Exception {
        start(true);
        node.getBlockchain().minePendingTransactions("Alice");

        HttpResponse<String> res = postBackup();

        assertEquals(201, res.statusCode(), res.body());
        JsonObject body = JsonParser.parseString(res.body()).getAsJsonObject();
        Path zip = Path.of(body.get("path").getAsString());
        assertTrue(Files.exists(zip), "Backup file should exist: " + zip);
        assertTrue(zip.startsWith(tmp.resolve("data/backups").toAbsolutePath()));
        assertEquals(Files.size(zip), body.get("sizeBytes").getAsLong());
        assertEquals(2, body.get("chainLength").getAsInt());
    }

    @Test
    @DisplayName("Two backups in a row get different files")
    void backup_twice_differentFiles() throws Exception {
        start(true);
        String a = JsonParser.parseString(postBackup().body()).getAsJsonObject().get("path").getAsString();
        String b = JsonParser.parseString(postBackup().body()).getAsJsonObject().get("path").getAsString();
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Without backups enabled the endpoint answers 503 with an error envelope")
    void backup_notEnabled_503() throws Exception {
        start(false);

        HttpResponse<String> res = postBackup();

        assertEquals(503, res.statusCode());
        assertTrue(JsonParser.parseString(res.body()).getAsJsonObject().has("error"));
    }
}
