package com.blocksmith.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.TestChains;
import com.blocksmith.network.Node;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The served dashboard keeps private keys only encrypted (Milestone 24a).
 * The crypto itself runs in the browser and is checked live (see the
 * sprint log); these checks pin the shape of the script so a change that
 * writes a key to storage some other way fails here.
 */
@DisplayName("Dashboard Keystore Tests")
class DashboardKeystoreTest {

    private static final int API_PORT_BASE = 18400;
    private static final int P2P_PORT_BASE = 20800;
    private static int counter = 0;

    /** Everything the dashboard may write to localStorage; the keystore is the only key store. */
    private static final Set<String> ALLOWED_STORAGE_KEYS =
            Set.of("KEYSTORE", "\"bs_theme\"", "\"bs_active_address\"", "\"bs_tour_optout\"");

    private Node node;
    private ApiServer api;
    private int apiPort;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        int offset = counter++;
        apiPort = API_PORT_BASE + offset;
        node = new Node(P2P_PORT_BASE + offset, TestChains.unsigned());
        api = new ApiServer(node, apiPort);
        api.start();
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.stop();
        if (node != null) node.stop();
    }

    private String get(String path) throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + apiPort + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode(), path);
        return res.body();
    }

    /** The body of {@code function name(...) { ... }} (up to the next top-level function). */
    private static String functionBody(String js, String name) {
        int start = js.indexOf("function " + name + "(");
        assertTrue(start >= 0, "missing function " + name);
        int end = js.indexOf("\nfunction ", start + 1);
        int endAsync = js.indexOf("\nasync function ", start + 1);
        if (endAsync >= 0 && (end < 0 || endAsync < end)) end = endAsync;
        return js.substring(start, end < 0 ? js.length() : end);
    }

    @Test
    @DisplayName("Keys reach storage only through the encrypting path (PBKDF2 -> AES-GCM, address bound)")
    void dashboardScript_storesKeysEncryptedOnly() throws Exception {
        String js = get("/app.js");

        Matcher writes = Pattern.compile("localStorage\\.setItem\\(\\s*([^,]+?)\\s*,").matcher(js);
        int count = 0;
        while (writes.find()) {
            count++;
            assertTrue(ALLOWED_STORAGE_KEYS.contains(writes.group(1)), "Unexpected storage write: " + writes.group(1));
        }
        assertEquals(4, count, "keystore, theme, active address, tour opt-out");
        assertTrue(js.contains("const KEYSTORE = \"bs_keystore\""));

        assertTrue(js.contains("KDF_ITERATIONS = 600000"), "PBKDF2 work factor");
        assertTrue(js.contains("name: \"PBKDF2\", hash: \"SHA-256\""));
        assertTrue(js.contains("{ name: \"AES-GCM\", length: 256 }"));
        assertTrue(js.contains("additionalData"), "Ciphertexts bound to their address");

        String storeKey = functionBody(js, "storeKey");
        assertTrue(storeKey.contains("seal(vault.aes, JSON.stringify(jwk), address)"), "The JWK is sealed under the address");
        assertFalse(storeKey.contains("privateKey"), "No plaintext key field in a stored entry");
        assertTrue(storeKey.contains("importKey(\"jwk\", jwk, EC, false, [\"sign\"])"), "In memory: non-extractable");

        assertFalse(js.contains("JSON.stringify(loadKeys()["), "The old plaintext export is gone");
        assertTrue(functionBody(js, "migrateLegacy").contains("localStorage.removeItem(LEGACY_KEYSTORE)"),
                "Plaintext keys are removed once encrypted");
    }

    @Test
    @DisplayName("The deck shows the key lock, and signing asks to unlock first")
    void dashboard_lockButtonAndUnlockBeforeSigning() throws Exception {
        assertTrue(get("/").contains("id=\"lock-btn\""));
        String js = get("/app.js");
        String sign = functionBody(js, "signText");
        int unlock = sign.indexOf("await ensureUnlocked()");
        assertTrue(unlock >= 0 && unlock < sign.indexOf("crypto.subtle.sign"), "Unlock, then sign");
        assertTrue(sign.contains("vault.keys[address]"), "Signs with the in-memory key only");
        assertTrue(functionBody(js, "createBrowserWallet").contains("await ensureUnlocked()"), "New keys need the passphrase");
        assertTrue(js.contains("AUTO_LOCK_MS = 15 * 60 * 1000"));
    }
}
