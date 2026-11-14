package com.blocksmith.core;

import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;
import java.util.Map;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.SignatureUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A keystore backup made by the dashboard in a real browser (Milestone 24b)
 * opens with standard Java crypto: PBKDF2-HMAC-SHA256 for the key, AES-GCM
 * (128-bit tag) with the address as additional data for each entry. This is
 * the recovery path without the dashboard, and it pins the browser format:
 * a change on either side fails here.
 *
 * Vector: {@code src/test/resources/vectors/keystore-backup.json} (test keys).
 */
@DisplayName("Keystore Interop Tests")
class KeystoreInteropTest {

    private static final String CHECK_TEXT = "BlockSmith keystore v1";

    private static JsonObject vector;
    private static JsonObject full;
    private static SecretKey aes;          // derived once: 600 000 PBKDF2 rounds

    @BeforeAll
    static void load() throws Exception {
        try (var in = KeystoreInteropTest.class.getResourceAsStream("/vectors/keystore-backup.json")) {
            assertNotNull(in, "vector resource");
            vector = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        full = vector.getAsJsonObject("full");
        aes = derive(vector.get("passphrase").getAsString(), full.getAsJsonObject("kdf"));
    }

    /** PBKDF2-HMAC-SHA256 -> a 256-bit AES key, as the browser's deriveKey. */
    static SecretKey derive(String passphrase, JsonObject kdf) throws Exception {
        assertEquals("PBKDF2", kdf.get("name").getAsString());
        assertEquals("SHA-256", kdf.get("hash").getAsString());
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(),
                SignatureUtil.hexToBytes(kdf.get("salt").getAsString()), kdf.get("iterations").getAsInt(), 256);
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        return new SecretKeySpec(key, "AES");
    }

    /** AES-GCM decrypt of {iv, ciphertext} with {@code aad} as additional data; the tag is the last 16 bytes. */
    static String unseal(SecretKey key, JsonObject box, String aad) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, SignatureUtil.hexToBytes(box.get("iv").getAsString())));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(SignatureUtil.hexToBytes(box.get("ciphertext").getAsString())), StandardCharsets.UTF_8);
    }

    /** A P-256 JWK's private scalar d (base64url) on the public key's curve. */
    static PrivateKey privateKey(JsonObject jwk, ECPublicKey pub) throws Exception {
        BigInteger d = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get("d").getAsString()));
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(d, pub.getParams()));
    }

    private static BigInteger coordinate(JsonObject jwk, String name) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(jwk.get(name).getAsString()));
    }

    @Test
    @DisplayName("Java opens the browser's backup: each key matches its address and signs verifiably")
    void browserBackup_decryptsInJava_keyMatchesAddressAndSigns() throws Exception {
        assertEquals(CHECK_TEXT, unseal(aes, full.getAsJsonObject("check"), "check"), "The passphrase check value");
        assertEquals(2, full.getAsJsonObject("keys").size(), "A wallet and a multisig member key");

        for (Map.Entry<String, JsonElement> e : full.getAsJsonObject("keys").entrySet()) {
            String address = e.getKey();
            JsonObject entry = e.getValue().getAsJsonObject();
            JsonObject jwk = JsonParser.parseString(unseal(aes, entry, address)).getAsJsonObject();
            assertEquals("EC", jwk.get("kty").getAsString());
            assertEquals("P-256", jwk.get("crv").getAsString());

            String publicKeyHex = entry.get("publicKey").getAsString();
            ECPublicKey pub = (ECPublicKey) SignatureUtil.hexToPublicKey(publicKeyHex);
            assertEquals(pub.getW().getAffineX(), coordinate(jwk, "x"), "The JWK is this public key's pair");
            assertEquals(pub.getW().getAffineY(), coordinate(jwk, "y"));
            assertEquals(address, Wallet.addressOf(pub), "Stored under its own address");

            String signature = SignatureUtil.sign(privateKey(jwk, pub), "recovered without the dashboard");
            assertTrue(SignatureUtil.verify(publicKeyHex, signature, "recovered without the dashboard"));
        }

        JsonObject single = vector.getAsJsonObject("single");
        String alice = vector.get("alice").getAsString();
        assertEquals(alice, single.get("address").getAsString());
        assertEquals(unseal(aes, full.getAsJsonObject("keys").getAsJsonObject(alice), alice),
                unseal(derive(vector.get("passphrase").getAsString(), single.getAsJsonObject("kdf")), single, alice),
                "The one-key backup holds the same key");
    }

    @Test
    @DisplayName("A wrong passphrase does not open the backup (GCM tag fails, no garbage)")
    void browserBackup_wrongPassphrase_fails() throws Exception {
        SecretKey wrong = derive("interop passphrase 24c", full.getAsJsonObject("kdf"));
        assertThrows(AEADBadTagException.class, () -> unseal(wrong, full.getAsJsonObject("check"), "check"));
        String alice = vector.get("alice").getAsString();
        assertThrows(AEADBadTagException.class, () -> unseal(wrong, full.getAsJsonObject("keys").getAsJsonObject(alice), alice));
    }

    @Test
    @DisplayName("An entry moved under another address, or with one byte changed, does not open")
    void browserBackup_entryMovedToAnotherAddress_fails() throws Exception {
        JsonObject keys = full.getAsJsonObject("keys");
        String alice = vector.get("alice").getAsString();
        String other = keys.keySet().stream().filter(a -> !a.equals(alice)).findFirst().orElseThrow();
        assertThrows(AEADBadTagException.class, () -> unseal(aes, keys.getAsJsonObject(alice), other),
                "The address is authenticated: a key cannot be relabelled as someone else's");

        JsonObject flipped = keys.getAsJsonObject(alice).deepCopy();
        String ct = flipped.get("ciphertext").getAsString();
        flipped.addProperty("ciphertext", (ct.charAt(0) == 'a' ? "b" : "a") + ct.substring(1));
        assertThrows(AEADBadTagException.class, () -> unseal(aes, flipped, alice));
    }
}
