package com.blocksmith.core;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.blocksmith.contract.Contract;
import com.blocksmith.contract.MultiSigWallet;
import com.blocksmith.contract.ScriptVM;
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
 *
 * The claim vector (Milestone 23c) comes from a live 2-of-3 claimed with
 * signatures from two separate browser profiles: member 1 signed in profile
 * A (the claimer's), member 2 in profile B ("Multisig member" drawer).
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

    // ===== 23c: a multisig claim signed in two browsers =====

    private static final String CLAIM_CONTRACT = "5d2d788f90aece5da774be930f7263c2ef384e50cebbcc5381b1445df85f5eb3";
    private static final double CLAIM_AMOUNT = 12.0;
    private static final String CLAIMER = "frank";
    private static final String CLAIM_MESSAGE = "1e5715be412c8d2bee702e6b8a8d6bb068c45312cac081bf2a4cb374feefd63d";
    private static final List<String> MEMBER_KEYS = List.of(
            "3059301306072a8648ce3d020106082a8648ce3d03010703420004e13c8f37d2af9b7c958fdabd105f4cae9901e04bb7"
                    + "4fba6a39c60ea43064fd1006f340d7c7c8548d791ede4988c4f8656e1c07a0f8b429d26940d382e436b8d4",
            "3059301306072a8648ce3d020106082a8648ce3d030107034200048d27e89d06153c6652e5a83d8ee35fa08dfafa65b0"
                    + "e8710b956c31fa5f1347765244a4b04a67cb4158097ca667f792bdb1eab2b26f1593cbd6316e110cc1ad83",
            "3059301306072a8648ce3d020106082a8648ce3d03010703420004b2c0b92b3ea38e24ce64da34a160b263d32b2f10fe"
                    + "6c3a4ba77c82f889e2a1a21fbe44bb38a6d1eb6ef335ccea3d3ce93e87726fe2882cb8715f8e6cd04f07ba");
    /** Member 1 (profile A) and member 2 (profile B), over CLAIM_MESSAGE. */
    private static final String SIG_MEMBER_1 = "30440220605b9a30e13149f3443e82cc94946c4e82b766ea435658d42b0c610ad86d7c8c02200ca6fab0200bf621f06c"
            + "a3bbc0c9b1c7f87357c61f972209dd6eaf52a2a13701";
    private static final String SIG_MEMBER_2 = "30450221009b3bfa7ab7fcc033f1d0ca38ae2e66f381dfd129c95ef3f2bb39fb9186e3509c0220031556f41350db4b5a"
            + "93ce49327833e467def638154af6d1c3970b16301c1616";

    private static Contract claimContract() {
        return new Contract(CLAIM_CONTRACT, "funder", MultiSigWallet.ofHex(MEMBER_KEYS, 2).getLockingScript(), CLAIM_AMOUNT, 0);
    }

    @Test
    @DisplayName("Two browsers' claim signatures pass CHECKMULTISIG in the Java VM (in member order only)")
    void webCryptoClaimSignature_passesCheckMultiSig() {
        Contract contract = claimContract();
        assertEquals(CLAIM_MESSAGE, contract.claimSighash(CLAIMER), "Java computes the message the browsers signed");
        assertTrue(SIG_MEMBER_2.contains("022100"), "Member 2's signature exercises the DER 0x00 padding");

        ScriptVM vm = new ScriptVM();
        String lock = contract.getLockingScript();
        assertTrue(vm.execute("PUSH " + SIG_MEMBER_1 + " PUSH " + SIG_MEMBER_2, lock, 1, CLAIM_MESSAGE));
        assertFalse(vm.execute("PUSH " + SIG_MEMBER_2 + " PUSH " + SIG_MEMBER_1, lock, 1, CLAIM_MESSAGE),
                "CHECKMULTISIG matches left to right: why the node orders the signatures");
        assertFalse(vm.execute("PUSH " + SIG_MEMBER_1, lock, 1, CLAIM_MESSAGE), "One signature is not two");
        assertFalse(vm.execute("PUSH " + SIG_MEMBER_1 + " PUSH " + SIG_MEMBER_1, lock, 1, CLAIM_MESSAGE),
                "The same member twice is not two members");
    }

    @Test
    @DisplayName("The same browser signatures cannot pay another claimer")
    void webCryptoClaimSignature_otherClaimer_fails() {
        Contract contract = claimContract();
        String lock = contract.getLockingScript();
        String unlock = "PUSH " + SIG_MEMBER_1 + " PUSH " + SIG_MEMBER_2;
        assertFalse(new ScriptVM().execute(unlock, lock, 1, contract.claimSighash("mallory")));
        assertFalse(SignatureUtil.verify(MEMBER_KEYS.get(1), SIG_MEMBER_2, contract.claimSighash("mallory")));
    }
}
