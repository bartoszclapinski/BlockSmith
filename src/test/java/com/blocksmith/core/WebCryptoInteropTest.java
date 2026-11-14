package com.blocksmith.core;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.util.SignatureUtil;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The dashboard (JavaScript, Web Crypto) and the node (Java) must agree on
 * addresses and signatures (Milestone 22c). These vectors were produced by
 * the dashboard's own code ({@code createBrowserWallet}, {@code signText})
 * in a real browser (headless Edge), so a change on either side that breaks
 * the other fails here, not in a user's browser.
 *
 * The second vector's {@code s} starts with a byte >= 0x80: its DER INTEGER
 * needs a leading 0x00 ("022100..."), the trickiest part of converting Web
 * Crypto's r||s signature to the DER form Java verifies.
 */
@DisplayName("Web Crypto Interop Tests")
class WebCryptoInteropTest {

    private record Vector(String address, String publicKey, String signature, String text) {}

    private static final List<Vector> VECTORS = List.of(
            new Vector("0x7efb99641eefeb9fd1d5b17367cce57cccb8f9a9",
                    "3059301306072a8648ce3d020106082a8648ce3d030107034200041fb901cabd76f2a5bb895b18effe39e8c478afc392aa"
                            + "1132604dd8990657bc335342c78417455c652a9fea72c5ba34002d3eeb93d37d41d9e2dc54163ddc6b12",
                    "304402206972887d3e8f04694fda7fc4f323013aa23539c570962acbe4b7a4dac3f729fa0220362f7795ebf6a7ce4b18"
                            + "0f5694026cbf0ea46d40c5c4b5cef9a91ae3b0a0271c",
                    "BlockSmith interop vector"),
            new Vector("0xc18f9584a05aa948402cac3135e1911e4631c702",
                    "3059301306072a8648ce3d020106082a8648ce3d03010703420004bdc597e6e0765efde3ecc2ee755c518864a391fc8c13"
                            + "0def380c07bac29ccc1015199a67f27b680049b47b3b50a4397463fb7a7b6d6bbe5eaf7c9be7d1a47680",
                    "304502207f769621aa63fbef6423de54bec39e799fe83cad30cb30f070df4f941a9d327a02210096febdd93eb3424e3ea5"
                            + "8c7e8f2b96267b203bf87b3ef90ce1a99f413829f46e",
                    "BlockSmith interop vector 0"));

    @Test
    @DisplayName("The address the browser derived is the one Java derives from the same key")
    void webCryptoAddress_matchesJava() throws Exception {
        for (Vector v : VECTORS) {
            assertEquals(v.address(), Wallet.addressOf(SignatureUtil.hexToPublicKey(v.publicKey())));
        }
    }

    @Test
    @DisplayName("The browser's signature (r||s converted to DER) verifies in Java; other text does not")
    void webCryptoSignature_verifiesInJava() {
        assertTrue(VECTORS.get(1).signature().contains("022100"), "Vector 2 exercises the 0x00 padding");
        for (Vector v : VECTORS) {
            assertTrue(SignatureUtil.verify(v.publicKey(), v.signature(), v.text()), v.text());
            assertFalse(SignatureUtil.verify(v.publicKey(), v.signature(), v.text() + "!"), "Other text");
        }
        assertFalse(SignatureUtil.verify(VECTORS.get(0).publicKey(), VECTORS.get(1).signature(), VECTORS.get(1).text()),
                "Another key's signature");
    }
}
