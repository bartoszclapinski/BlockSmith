package com.blocksmith.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for HashUtil SHA-256 hashing functionality.
 */
public class HashUtilTest {
    
    @Test
    @DisplayName("Hash should always be 64 characters long")
    void testHashLength() {
        String hash = HashUtil.applySha256("test");
        assertEquals(64, hash.length(), "SHA-256 hash should be 64 characters");
    }

    @Test
    @DisplayName("Same input should produce same hash (determinism)")
    void testHashDeterminism() {
        String input = "Hello BlockSmith";
        String hash1 = HashUtil.applySha256(input);
        String hash2 = HashUtil.applySha256(input);
        assertEquals(hash1, hash2, "Same input should produce same hash");
    }

    @Test
    @DisplayName("Different input should produce different hashes")
    void testHashUniqueness() {
        String hash1 = HashUtil.applySha256("Hello");
        String hash2 = HashUtil.applySha256("hellO");
        assertNotEquals(hash1, hash2, "Different input should produce different hashes");
    }

    @Test
    @DisplayName("Empty string should produce non-empty and valid hash")
    void testEmptyStringHash() {
        String hash = HashUtil.applySha256("");
        assertNotNull(hash, "Empty string should produce non-empty hash");
        assertEquals(64, hash.length(), "Empty string hash should be 64 characters");
    }

    @Test
    @DisplayName("Special characters should be handled correctly")
    void testSpecialCharacters() {
        String hash = HashUtil.applySha256("!@#$%^&*()_+-=[]{}|;:',.<>?/");
        assertNotNull(hash, "Special characters should produce non-empty hash");
        assertEquals(64, hash.length(), "Special characters hash should be 64 characters");
    }

    @Test
    @DisplayName("Hash should only contain hexadecimal characters")
    void testHexadecimalCharacters() {
        String hash = HashUtil.applySha256("Test");
        assertTrue(hash.matches("[0-9a-f]{64}"), "Hash should only contain lowercase hexadecimal characters");
    }
}
