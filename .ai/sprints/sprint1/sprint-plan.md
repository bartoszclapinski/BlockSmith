# Sprint 1: Fundamentals

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 1 |
| **Title** | Fundamentals |
| **Duration** | 1 week |
| **Phase** | Phase 1 |
| **Status** | Not Started |
| **Depends On** | Sprint 0 |

---

## 🎯 Goal

Implement the cryptographic foundation (SHA-256 hashing) and basic block structure. This sprint establishes the core building blocks for the entire blockchain.

---

## 📦 Deliverables

### 1. BlockchainConfig Class
- [ ] Create configuration constants:
  ```java
  public static final int MINING_DIFFICULTY = 4;
  public static final double MINING_REWARD = 50.0;
  public static final double TRANSACTION_FEE = 0.1;
  public static final String GENESIS_PREV_HASH = "0";
  public static final String COINBASE_ADDRESS = "COINBASE";
  ```

### 2. HashUtil Class
- [ ] Implement `applySha256(String input)` method
- [ ] Return 64-character hexadecimal string
- [ ] Add THEORY comment explaining SHA-256

### 3. Block Class (Basic)
- [ ] Fields:
  - `int index` - position in chain
  - `long timestamp` - creation time
  - `String hash` - this block's hash
  - `String previousHash` - link to previous block
  - `String data` - block data (temporary, will be replaced with transactions)
- [ ] Constructor with parameters
- [ ] `calculateHash()` method using HashUtil
- [ ] Getters for all fields
- [ ] Genesis Block factory method: `createGenesisBlock()`

### 4. Unit Tests
- [ ] `HashUtilTest.java`:
  - Test hash determinism (same input = same output)
  - Test hash length (always 64 characters)
  - Test hash uniqueness (different inputs = different hashes)
  - Test empty string handling
  - Test special characters handling
- [ ] `BlockTest.java` (basic):
  - Test block creation
  - Test calculateHash consistency
  - Test Genesis block creation
  - Test previousHash linking
- [ ] `BlockchainConfigTest.java`:
  - Test config values are accessible
  - Test config values are reasonable

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | `HashUtil.applySha256()` returns 64-char hex string | ⬜ |
| 2 | Same input always produces same hash | ⬜ |
| 3 | Different inputs produce different hashes | ⬜ |
| 4 | Block can be created with all required fields | ⬜ |
| 5 | `calculateHash()` produces consistent results | ⬜ |
| 6 | Genesis block has index 0 and previousHash "0" | ⬜ |
| 7 | All unit tests pass | ⬜ |
| 8 | Code contains THEORY comments | ⬜ |

---

## 📝 Theory Comments Required

### HashUtil - SHA-256 Explanation
```java
/**
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
```

### Block - Structure Explanation
```java
/**
 * THEORY: A block is the fundamental unit of a blockchain.
 * Each block contains:
 * - index: Position in the chain (0 for genesis)
 * - timestamp: When the block was created (Unix time)
 * - hash: Unique identifier calculated from block contents
 * - previousHash: Links this block to the previous one
 * - data: The actual content (later: transactions)
 * 
 * IMMUTABILITY: If any data changes, the hash changes,
 * breaking the link to the next block and invalidating the chain.
 */
```

---

## 🧪 Test Scenarios

### HashUtilTest

| Test | Input | Expected Output |
|------|-------|-----------------|
| Determinism | "hello" twice | Same 64-char hash |
| Length | Any string | 64 characters |
| Uniqueness | "hello" vs "Hello" | Different hashes |
| Empty | "" | Valid 64-char hash |
| Special | "!@#$%^&*()" | Valid 64-char hash |

### BlockTest

| Test | Scenario | Expected |
|------|----------|----------|
| Creation | New block with data | All fields populated |
| Hash | Calculate hash twice | Same result |
| Genesis | Create genesis block | index=0, prevHash="0" |
| Link | Block with prevHash | prevHash matches parent's hash |

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Status |
|---|------|----------------|--------|
| 1 | Implement BlockchainConfig | 15 min | ⬜ |
| 2 | Implement HashUtil with SHA-256 | 30 min | ⬜ |
| 3 | Write HashUtilTest | 30 min | ⬜ |
| 4 | Implement Block (basic) | 45 min | ⬜ |
| 5 | Write BlockTest | 30 min | ⬜ |
| 6 | Write BlockchainConfigTest | 15 min | ⬜ |
| 7 | Add theory comments | 20 min | ⬜ |
| 8 | Run all tests and fix issues | 30 min | ⬜ |

**Total Estimated Time:** ~4 hours

---

## 🔗 Dependencies

- Sprint 0 must be completed (project structure exists)
- Java Cryptography Architecture (JCA) - built into JDK

---

## 🚫 Out of Scope

- Mining (nonce, mineBlock) - Sprint 2
- Blockchain class - Sprint 3
- Transactions - Sprint 4
- Wallets/Signatures - Sprint 5
