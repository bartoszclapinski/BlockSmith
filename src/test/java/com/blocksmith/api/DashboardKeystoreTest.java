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

    @Test
    @DisplayName("Every signature in the dashboard goes through the keystore (send, bump, deploys, multisig)")
    void dashboardScript_everySignatureGoesThroughTheKeystore() throws Exception {
        String js = get("/app.js");
        Matcher signs = Pattern.compile("crypto\\.subtle\\.sign\\(").matcher(js);
        int count = 0;
        while (signs.find()) {
            count++;
            String around = js.substring(Math.max(0, js.lastIndexOf("\nasync function ", signs.start())), signs.start());
            assertTrue(around.startsWith("\nasync function signText(") || around.startsWith("\nasync function checkKeyPair("),
                    "A raw signature outside signText / checkKeyPair's pair probe");
        }
        assertEquals(2, count, "signText signs; checkKeyPair only probes a key being imported");
        assertTrue(functionBody(js, "signAndSubmit").contains("await signText(address, template.signatureData)"));
        // the callers: send, bump, hashlock + multisig deploys, multisig claim, member signing
        assertEquals(5, js.split("signAndSubmit\\(", -1).length - 2, "send, bump, two deploys, move funds");
        assertEquals(2, js.split("await signText\\(local\\.address|signText\\(local\\.address", -1).length - 1,
                "claim drawer + member drawer");
    }

    @Test
    @DisplayName("Move funds: a fresh key, the whole spendable balance minus the fee, signed by the old key")
    void dashboardScript_moveFunds_signedByOldKeyToFreshKey() throws Exception {
        String js = get("/app.js");
        String move = functionBody(js, "moveFundsToNewKey");
        assertTrue(move.contains("Math.floor((spendable - fee) * 1e8) / 1e8"), "Rounded down: never more than allowed");
        assertTrue(move.contains("info.balance - (info.pendingOutgoing || 0)"), "Pending payments are not spendable");
        int fresh = move.indexOf("createBrowserWallet(");
        int prepare = move.indexOf("/api/transactions/prepare");
        int sign = move.indexOf("signAndSubmit(template, oldAddress)");
        int retire = move.indexOf("retireKey(oldAddress, fresh)");
        assertTrue(fresh >= 0 && fresh < prepare && prepare < sign && sign < retire,
                "New key, then the payment to it, signed by the OLD key, then the old key is retired");
        assertTrue(move.contains("removeKeys([fresh])"), "A failed move leaves no orphan key");

        assertTrue(functionBody(js, "migrateLegacy").contains("{ plaintextBefore: true }"), "Migrated keys are flagged");
        assertTrue(functionBody(js, "importKeys").contains("flags: { plaintextBefore: true }"), "So are plaintext imports");
        assertTrue(functionBody(js, "changePassphrase").contains("Object.assign({}, e, { iv: box.iv, ciphertext: box.ciphertext })"),
                "A new passphrase keeps the label and the flags");
    }

    @Test
    @DisplayName("Change passphrase re-encrypts under a new salt in one write; import checks every key first")
    void dashboardScript_changePassphraseAtomic_importChecksFirst() throws Exception {
        String js = get("/app.js");
        String change = functionBody(js, "changePassphrase");
        assertEquals(1, change.split("saveStore\\(", -1).length - 1, "One write: all keys or none");
        assertTrue(change.contains("salt: randomHex(16)"), "A new salt");
        assertTrue(change.indexOf("The current passphrase is wrong") < change.indexOf("saveStore("),
                "The current passphrase is checked before anything is written");

        String imp = functionBody(js, "importKeys");
        int checks = imp.indexOf("checkKeyPair(");
        int stores = imp.indexOf("storeKey(");
        assertTrue(checks >= 0 && checks < stores, "Every pair is checked before any is stored");
        assertTrue(imp.contains("\"Wrong passphrase for this backup\""));
        assertTrue(js.contains("blocksmith: \"keystore-backup\""), "Whole-keystore backup");
    }
}
