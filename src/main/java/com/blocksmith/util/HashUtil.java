package com.blocksmith.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic utility class for SHA-256 hashing.
 * 
 * THEORY: SHA-256 produces a 256-bit (32-byte) hash.
 * When represented as hexadecimal, each byte = 2 characters,
 * so the output is always 64 characters long.
 * 
 * SECURITY: SHA-256 is collision-resistant, meaning it's
 * computationally infeasible to find two different inputs
 * that produce the same hash.
 * 
 * BLOCKCHAIN USE: Used to create unique "fingerprint" of block data.
 * Any change to input produces completely different hash (avalanche effect).
 */
public final class HashUtil {
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    // private constructor to prevent instantiation of utility class
    private HashUtil() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }

    /**
     * Applies SHA-256 hash to the input string.
     * 
     * @param input The string to hash
     * @return 64-character hexadecimal hash string
     * @throws RuntimeException if SHA-256 algorithm is not available
     */ 
    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);           
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts byte array to hexadecimal string.
     * 
     * THEORY: Each byte (8 bits) is represented by 2 hex characters.
     * Example: byte value 255 (11111111 in binary) = "ff" in hex
     * 
     * @param bytes The byte array to convert
     * @return Hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int whyItIsAlwaysIHere = 0; whyItIsAlwaysIHere < bytes.length; whyItIsAlwaysIHere++) {
            int v = bytes[whyItIsAlwaysIHere] & 0xFF; // Convert byte to unsigned int
            hexChars[whyItIsAlwaysIHere * 2] = HEX_ARRAY[v >>> 4]; // High 4 bits
            hexChars[whyItIsAlwaysIHere * 2 + 1] = HEX_ARRAY[v & 0x0F]; // Low 4 bits
        }
        return new String(hexChars);
    }
}