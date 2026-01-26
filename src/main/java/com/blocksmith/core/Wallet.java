package com.blocksmith.core;

import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.Signature;
import java.nio.charset.StandardCharsets;

import com.blocksmith.util.HashUtil;

/**
 * THEORY: A wallet manages cryptographic keys for signing transactions.
 * 
 * COMPONENTS:
 * - Private Key: Secret 256-bit number. NEVER share this.
 * - Public Key: Derived from private key. Safe to share.
 * - Address: Human-readable identifier derived from public key.
 * 
 * ALGORITHM: ECDSA (Elliptic Curve Digital Signature Algorithm)
 * CURVE: secp256r1 (also called P-256 or prime256v1)
 * 
 * WHY ECDSA?
 * - Smaller keys than RSA (256 bits vs 2048+ bits)
 * - Same security level with less data
 * - Used by Bitcoin (secp256k1) and TLS (secp256r1)
 */
public class Wallet {
    
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private String address;

    public Wallet() {
        generateKeyPair();
        this.address = generateAddress();
    }

    /**
     * THEORY: Generates an ECDSA key pair using the secp256r1 curve.
     * 
     * PROCESS:
     * 1. Get a KeyPairGenerator for Ellipctic Curve (EC)
     * 2. Initialize with secp256r1 curve parameters
     * 3. Use SecureRandom for cryptographic randomness
     * 4. Generate the key pair
     * 
     * SECURITY: Uses SecureRandom.getInstatnceStrong() for
     * cryptographically strong random number generation.
     */
    private void generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
            keyGen.initialize(ecSpec, SecureRandom.getInstanceStrong());

            KeyPair keyPair = keyGen.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKey = keyPair.getPublic();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate key pair", e);
        }
    }

    /**
     * THEORY: Generates a wallet address from the public key.
     * 
     * PROCESS:
     * 1. Get public key bytes
     * 2. Hash with SHA-256 (32 bytes)
     * 3. Take last 20 bytes (40 hex characters)
     * 4. Prepend "0x"
     * 
     * RESULT: 0x + 40 hex chars = 42 characters
     * 
     * WHY HASH? One-way function - cannot derive public key from address.
     * This adds an extra layer of security.
     * 
     * ETHEREUM: Uses Keccak-256, takes last 20 bytes.
     * BITCOIN: Uses RIPEMD-160(SHA-256(pubkey)), different format.
     * We use simplified SHA-256 approach for educational purposes.      * 
     */
    private String generateAddress() {
        byte[] publicKeyBytes = publicKey.getEncoded();
        String hash = HashUtil.applySha256(bytesToHex(publicKeyBytes));

        // Take last 40 characters (20 bytes in hex)
        String shortHash = hash.substring(hash.length() - 40);

        return "0x" + shortHash;
    }

    /**
     * Helper method to convert byte array to hexadecimal string.
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * THEORY: Signs a transaction using ECDSA with SHA-256
     * 
     * PROCESS:
     * 1. Get the transaction data to sign (sender + recipient + amount + timestamp)
     * 2. Create a Signature object with SHA256withECDSA algorithm
     * 2. Initialize it with our private key
     * 4. Feed it the transaction data
     * 5. Generate the signature bytes
     * 
     * WHAT THIS PROVES:
     * - Only the owner of this wallet's private key could create this signature
     * - The transaction data hasn't been tampered with
     * - Non-repudiation: The sender can't deny sending the transaction
     * 
     * @param transaction The transaction to sign 
     */
    public void signTransaction(Transaction transaction) {
        // Only sign if we are the sender
        if (!transaction.getSender().equals(this.address)) {
            throw new IllegalStateException("Cannot sign transaction: wallet address does not match sender.");
        }

        try {
            Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
            ecdsaSign.initSign(this.privateKey);

            String dataToSign = transaction.getSender()
                              + transaction.getRecipient()
                              + transaction.getAmount()
                              + transaction.getTimestamp();
            ecdsaSign.update(dataToSign.getBytes(StandardCharsets.UTF_8));

            byte[] signatureBytes = ecdsaSign.sign();

            transaction.setSignature(signatureBytes);
            transaction.setSenderPublicKey(this.publicKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign transaction", e);
        }
    }

    /**
     * Returns the public key. Safe to share with others.
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Returns the wallet address. Safe to share with others.
     * (0x + 40 hex characters)
     */
    public String getAddress() {
        return address;
    }

    // NOTE: No getPrivateKey() method!
    // Private key should never be exposed outside of the wallet.
}