package com.blocksmith.api;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.TestChains;
import com.blocksmith.network.Node;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sprint 27b: the explorer views of the dashboard. The views themselves are
 * checked in a browser (see the sprint log); here the served page and script
 * must have the mount points, the four routes and the endpoints they read,
 * and the poll must ask for a page of blocks, never the whole chain.
 */
@DisplayName("Explorer views: served page and script, polling by page (Sprint 27b)")
class ExplorerViewsTest {

    private final HttpClient client = HttpClient.newHttpClient();
    private Node node;
    private ApiServer api;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        node = new Node(freePort(), TestChains.unsigned());
        port = freePort();
        api = new ApiServer(node, port);
        api.start();
    }

    @AfterEach
    void tearDown() {
        api.stop();
        node.stop();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private String get(String path) throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), path);
        return res.body();
    }

    @Test
    @DisplayName("the page has the explorer next to the deck, a search form, and links into it")
    void page_hasExplorerMountPoints() throws Exception {
        String html = get("/");
        assertTrue(html.contains("id=\"deck\""), "the dashboard, hidden while a view is open");
        assertTrue(html.contains("id=\"explorer\"") && html.contains("id=\"explorer-body\""), "the explorer and its content");
        assertTrue(html.contains("<div id=\"explorer\" class=\"main-inner explorer\" hidden>"), "hidden until a #/ link is opened");
        assertTrue(html.contains("id=\"explorer-search\"") && html.contains("id=\"explorer-query\""), "search form");
        assertTrue(html.contains("type=\"search\""), "a search field");
        assertTrue(html.contains("id=\"explore-link\"") && html.contains("href=\"#/blocks\""), "the way in from the chain hero");
        assertTrue(html.contains("href=\"#/\""), "the way back to the dashboard");
        assertTrue(html.contains("id=\"balance-explore\""), "the active wallet's history");

        String css = get("/style.css");
        assertTrue(css.contains(".main-inner[hidden] { display: none; }"), "[hidden] must win over display: flex");
    }

    @Test
    @DisplayName("the script routes #/blocks, #/block, #/tx, #/address and reads the explorer endpoints")
    void script_hasRoutesAndReadsTheEndpoints() throws Exception {
        String js = get("/app.js");
        assertTrue(js.contains("/^#\\/(blocks|block|tx|address)(?:\\/(.+))?$/"), "the four routes");
        assertTrue(js.contains("window.addEventListener(\"hashchange\", onRoute)"), "back button and pasted links");
        assertTrue(js.contains("onRoute();   // a link opened directly lands on its view"), "a direct link at page load");

        assertTrue(js.contains("fetchJson(\"/api/explorer/stats\")"), "totals");
        assertTrue(js.contains("\"/api/explorer/blocks?limit=\" + BLOCK_PAGE + \"&before=\" + explorer.nextBefore"), "older blocks by cursor");
        assertTrue(js.contains("byHeight ? \"/api/blocks/\" + arg : \"/api/blocks/hash/\" + encodeURIComponent(arg)"), "a block by height or hash");
        assertTrue(js.contains("fetchJson(\"/api/transactions/\" + encodeURIComponent(id))"), "a transaction");
        assertTrue(js.contains("fetchJson(\"/api/explorer/address/\" + encodeURIComponent(address)"), "an address");
        assertTrue(js.contains("fetchJson(\"/api/explorer/search?q=\" + encodeURIComponent(q))"), "search");

        assertTrue(js.contains("getElementById(\"explorer-search\").addEventListener(\"submit\"")
                && js.contains("explorerSearch(document.getElementById(\"explorer-query\").value);"), "the search form is wired");
        assertTrue(js.contains("label: \"Search the chain\""), "the palette searches too");
        assertTrue(js.contains("card.href = blockLink(block.index);"), "block cards open their block");
        assertTrue(js.contains("if (moved && explorer.route) loadExplorer(false);"), "an open view follows the chain");
        assertFalse(js.contains(".innerHTML"), "chain data is written with textContent only");
    }

    @Test
    @DisplayName("the poll asks for 6 block summaries; nothing asks for the whole chain")
    void script_pollsOnePageOfBlocks() throws Exception {
        String js = get("/app.js");
        assertTrue(js.contains("fetchJson(\"/api/explorer/blocks?limit=6\")"), "the hero's six cards");
        assertFalse(js.contains("fetchJson(\"/api/blocks\")"), "the whole chain, every 4 seconds, is gone");
        assertFalse(js.contains("blocks.slice(-6)"), "no client-side cut of a full download");

        // What the poll gets is what the hero reads: a height and summaries without transactions.
        node.getBlockchain().minePendingTransactions("Miner1");
        JsonObject page = JsonParser.parseString(get("/api/explorer/blocks?limit=6")).getAsJsonObject();
        assertEquals(1, page.get("height").getAsInt());
        JsonObject tip = page.getAsJsonArray("blocks").get(0).getAsJsonObject();
        for (String field : new String[]{"index", "hash", "transactionCount", "sizeBytes", "totalFees"}) {
            assertTrue(tip.has(field), "the hero card reads '" + field + "'");
        }
        assertFalse(tip.has("transactions"));
    }
}
