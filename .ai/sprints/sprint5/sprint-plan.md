# Sprint 5: Wallets and Signatures

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 5 |
| **Title** | Wallets and Signatures |
| **Duration** | 5 days |
| **Phase** | Phase 1 |
| **Status** | ✅ Completed |
| **Depends On** | Sprint 4 |

---

## 🎯 Goal

Implement ECDSA key pairs, wallet addresses, and digital signatures for transactions. This sprint adds cryptographic authentication - proving that transactions are authorized by the account owner.

---

## 📦 Deliverables

### 1. Wallet Class
- [x] Fields:
  - `PrivateKey privateKey` - secret key (never shared)
  - `PublicKey publicKey` - public key (can be shared)
  - `String address` - derived from public key (0x...)
- [x] `generateKeyPair()` - creates new ECDSA key pair using secp256r1
- [x] `getAddress()` - returns wallet address
- [x] `getPublicKey()` - returns public key
- [x] `signTransaction(Transaction tx)` - signs transaction with private key
- [x] Private key should NEVER be exposed via getter

### 2. Address Generation
- [x] Generate address from public key:
  1. Get public key bytes
  2. Hash with SHA-256
  3. Take last 20 bytes (40 hex characters)
  4. Prepend "0x"
- [x] Address format: `0x` + 40 hex characters (Ethereum-style)

### 3. Transaction Class Updates
- [x] Add `byte[] signature` field
- [x] Add `PublicKey senderPublicKey` field (for verification)
- [x] `setSignature()` / `setSenderPublicKey()` - setters for wallet to use
- [x] `verifySignature()` - verifies signature matches sender
- [x] COINBASE transactions exempt from signature requirement

### 4. Digital Signature Implementation
- [x] Use ECDSA algorithm with SHA256
- [x] Sign: hash of (sender + recipient + amount + timestamp)
- [x] Verify: use sender's public key to verify signature

### 5. Unit Tests
- [x] `WalletTest.java`:
  - Test key pair generation
  - Test address format (starts with "0x", correct length)
  - Test different wallets have different addresses
  - Test signing transaction
- [x] `TransactionTest.java` (signatures):
  - Test signed transaction is valid
  - Test unsigned transaction is invalid
  - Test tampered transaction fails verification
  - Test COINBASE transaction doesn't need signature

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | Wallet generates valid ECDSA key pair | ✅ |
| 2 | Address starts with "0x" and is 42 characters | ✅ |
| 3 | Different wallets have different addresses | ✅ |
| 4 | Transaction can be signed by sender | ✅ |
| 5 | Valid signature passes verification | ✅ |
| 6 | Tampered transaction fails verification | ✅ |
| 7 | All unit tests pass | ✅ |

---

## 📝 Theory Comments Required

### ECDSA Explanation
```java
/**
 * THEORY: ECDSA (Elliptic Curve Digital Signature Algorithm)
 * 
 * COMPONENTS:
 * - Private Key: Secret number (256 bits). Must NEVER be shared.
 * - Public Key: Point on elliptic curve derived from private key.
 *               Can be shared freely. Cannot derive private key from it.
 * - Signature: Proof that private key holder authorized a message.
 * 
 * HOW SIGNING WORKS:
 * 1. Create hash of message (transaction data)
 * 2. Use private key + hash + random number to create signature
 * 3. Signature is two numbers (r, s)
 * 
 * HOW VERIFICATION WORKS:
 * 1. Take message, signature, and public key
 * 2. Mathematical operation confirms signature is valid
 * 3. Only the private key holder could have created it
 * 
 * CURVE: secp256r1 (also called P-256 or prime256v1)
 * Used in TLS, Bitcoin uses secp256k1 (different curve).
 */
```

### Address Generation Explanation
```java
/**
 * THEORY: Wallet address is derived from public key.
 * 
 * PROCESS:
 * 1. Take public key bytes
 * 2. Hash with SHA-256 (32 bytes)
 * 3. Take last 20 bytes (for shorter address)
 * 4. Convert to hexadecimal
 * 5. Prepend "0x"
 * 
 * RESULT: 0x + 40 hex chars = 42 characters total
 * 
 * EXAMPLE: 0x71C7656EC7ab88b098defB751B7401B5f6d8976F
 * 
 * WHY HASH? One-way function - cannot derive public key from address.
 * This adds extra layer of security.
 * 
 * ETHEREUM: Uses Keccak-256 instead of SHA-256, takes last 20 bytes.
 * BITCOIN: Uses RIPEMD-160(SHA-256(pubkey)), different format.
 */
```

---

## 🧪 Test Scenarios

### Wallet Tests

| Test | Action | Expected | Result |
|------|--------|----------|--------|
| Key generation | new Wallet() | privateKey and publicKey not null | ✅ |
| Address format | getAddress() | Starts with "0x", length 42 | ✅ |
| Unique addresses | Create 2 wallets | Different addresses | ✅ |
| Deterministic | Same wallet | Same address each time | ✅ |

### Signature Tests

| Test | Setup | Expected | Result |
|------|-------|----------|--------|
| Valid signature | Sign with sender's key | verifySignature() = true | ✅ |
| Unsigned | Don't sign | verifySignature() = false | ✅ |
| Wrong key | Sign with different wallet | Exception thrown | ✅ |
| Tampered amount | Change amount after signing | verifySignature() = false | ✅ |
| COINBASE | No signature | verifySignature() = true | ✅ |

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Actual Time | Status |
|---|------|----------------|-------------|--------|
| 1 | Create Wallet class structure | 20 min | ~15 min | ✅ |
| 2 | Implement key pair generation | 45 min | ~30 min | ✅ |
| 3 | Implement address generation | 30 min | ~20 min | ✅ |
| 4 | Update Transaction with signature fields | 20 min | ~15 min | ✅ |
| 5 | Implement signTransaction() | 45 min | ~30 min | ✅ |
| 6 | Implement verifySignature() | 45 min | ~25 min | ✅ |
| 7 | Write WalletTest | 45 min | ~40 min | ✅ |
| 8 | Write signature tests | 45 min | ~35 min | ✅ |
| 9 | Add theory comments | 25 min | ~20 min | ✅ |

**Total Estimated Time:** ~6 hours
**Total Actual Time:** ~4 hours

---

## 🔗 Dependencies

- Sprint 4 must be completed (Transaction class) ✅
- Java Cryptography Architecture (JCA):
  - `java.security.KeyPairGenerator`
  - `java.security.Signature`
  - `java.security.spec.ECGenParameterSpec`

---

## 💡 Implementation Hints

### Key Pair Generation
```java
public void generateKeyPair() {
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
```

### Signing
```java
public byte[] sign(String data, PrivateKey privateKey) {
    try {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    } catch (Exception e) {
        throw new RuntimeException("Failed to sign data", e);
    }
}
```

### Verification
```java
public boolean verify(String data, byte[] signatureBytes, PublicKey publicKey) {
    try {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return signature.verify(signatureBytes);
    } catch (Exception e) {
        return false;
    }
}
```

---

## 🚫 Out of Scope

- Balance checking - Sprint 6
- Key export/import (wallet persistence) - advanced feature
- HD wallets (hierarchical deterministic) - advanced feature
- Multiple signature schemes - advanced feature
