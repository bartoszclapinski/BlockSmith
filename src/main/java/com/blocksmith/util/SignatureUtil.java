package com.blocksmith.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * THEORY: ECDSA signing/verification helpers for the script VM.
 *
 * The wallet (Sprint 5) signs whole transactions with SHA256withECDSA on the
 * secp256r1 curve. Multisig needs the same primitive at a finer grain: sign an
 * arbitrary message (a "sighash") and verify a signature against a public key.
 *
 * WHY HEX? The script VM operates on string stack elements, so public keys and
 * signatures must travel as text. We encode the raw bytes as lowercase hex:
 * a public key is its X.509 (SubjectPublicKeyInfo) encoding, a signature is the
 * DER-encoded ECDSA output - exactly the bytes the JCA produces and consumes.
 *
 * NEVER-THROWS ON VERIFY: verification is reached from inside the VM, which is
 * consensus-critical and must never crash. A malformed key, a malformed
 * signature, or a bad hex string is simply "not a valid signature" (false),
 * never an exception. Signing, by contrast, is a local wallet operation and may
 * throw - a broken key there is a programming error, not untrusted input.
 */
public final class SignatureUtil {

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private SignatureUtil() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }

    /**
     * Signs a message with a private key (SHA256withECDSA) and returns the
     * signature as a hex string.
     *
     * @param privateKey The signer's private key
     * @param message    The message to authorize (e.g. a claim's sighash)
     * @return The DER signature, hex-encoded
     * @throws RuntimeException if signing fails (a local key error)
     */
    public static String sign(PrivateKey privateKey, String message) {
        try {
            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initSign(privateKey);
            ecdsa.update(message.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(ecdsa.sign());
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign message", e);
        }
    }

    /**
     * Verifies a hex signature against a hex public key over a message.
     *
     * Consensus-safe: returns false for any malformed input rather than
     * throwing, so a bad script element can never crash a node.
     *
     * @param publicKeyHex The signer's public key, hex-encoded (X.509)
     * @param signatureHex The signature, hex-encoded (DER)
     * @param message      The message that was supposedly signed
     * @return true iff the signature is valid for the message under the key
     */
    public static boolean verify(String publicKeyHex, String signatureHex, String message) {
        try {
            PublicKey publicKey = hexToPublicKey(publicKeyHex);
            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initVerify(publicKey);
            ecdsa.update(message.getBytes(StandardCharsets.UTF_8));
            return ecdsa.verify(hexToBytes(signatureHex));
        } catch (Exception e) {
            // Malformed key/signature/hex is "not valid", never a crash.
            return false;
        }
    }

    /**
     * Encodes a public key as a hex string (its X.509 encoding). Safe to share.
     */
    public static String publicKeyToHex(PublicKey publicKey) {
        return bytesToHex(publicKey.getEncoded());
    }

    /**
     * Reconstructs a public key from its hex (X.509) encoding.
     *
     * @throws Exception if the hex or key encoding is invalid
     */
    public static PublicKey hexToPublicKey(String publicKeyHex) throws Exception {
        byte[] encoded = hexToBytes(publicKeyHex);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePublic(new X509EncodedKeySpec(encoded));
    }

    /** Converts a byte array to a lowercase hex string. */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Converts a hex string to bytes.
     *
     * @throws IllegalArgumentException if the length is odd or a character is
     *         not a hex digit (caught by {@link #verify} to yield false)
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string");
        }
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16);
            int lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("Invalid hex digit");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
