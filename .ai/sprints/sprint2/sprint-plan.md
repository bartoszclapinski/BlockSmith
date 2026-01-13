# Sprint 2: Proof-of-Work

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 2 |
| **Title** | Proof-of-Work |
| **Duration** | 1 week |
| **Phase** | Phase 2 |
| **Status** | Not Started |
| **Depends On** | Sprint 1 |

---

## 🎯 Goal

Implement the mining mechanism with adjustable difficulty. This sprint introduces the concept of Proof-of-Work - the computational puzzle that makes blockchain secure.

---

## 📦 Deliverables

### 1. Block Class Extensions
- [ ] Add `nonce` field (int) - the value miners search for
- [ ] Update `calculateHash()` to include nonce
- [ ] Implement `mineBlock(int difficulty)` method:
  - Loop until hash starts with required number of zeros
  - Increment nonce on each iteration
  - Return when valid hash found
- [ ] Add mining time measurement

### 2. HashUtil Extensions (Optional)
- [ ] Add helper method `meetsTarget(String hash, int difficulty)`:
  - Returns true if hash starts with `difficulty` number of zeros
  - Used to validate mined blocks

### 3. Mining Benchmark
- [ ] Create method to measure mining time for different difficulties
- [ ] Demonstrate exponential increase in mining time with difficulty

### 4. Unit Tests
- [ ] `BlockTest.java` (mining):
  - Test mining produces valid hash (starts with zeros)
  - Test nonce changes during mining
  - Test different difficulty levels
  - Test mining time increases with difficulty
- [ ] `HashUtilTest.java` (if meetsTarget added):
  - Test target validation

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | `mineBlock(4)` produces hash starting with "0000" | ⬜ |
| 2 | Nonce is different for each mined block | ⬜ |
| 3 | Higher difficulty requires more iterations | ⬜ |
| 4 | Mining time is measurable and logged | ⬜ |
| 5 | Difficulty 1 mines faster than difficulty 4 | ⬜ |
| 6 | All unit tests pass | ⬜ |

---

## 📝 Theory Comments Required

### Proof-of-Work Explanation
```java
/**
 * THEORY: Proof-of-Work (PoW) is a consensus mechanism that requires
 * miners to solve a computational puzzle before adding a block.
 * 
 * THE PUZZLE: Find a nonce value that, when combined with block data
 * and hashed, produces a hash starting with N zeros (where N = difficulty).
 * 
 * WHY IT WORKS:
 * - SHA-256 is unpredictable - you can't reverse-engineer the nonce
 * - Only way to find valid nonce is brute-force (try millions of values)
 * - Higher difficulty = more zeros required = exponentially harder
 * 
 * SECURITY: To tamper with a block, attacker must re-mine it AND all
 * subsequent blocks faster than the network adds new ones.
 * 
 * BITCOIN: Uses difficulty ~19 (hash starts with 19+ zeros)
 * This requires trillions of hash calculations.
 */
```

### Nonce Explanation
```java
/**
 * THEORY: Nonce = "Number used ONCE"
 * 
 * It's the variable that miners change to find a valid hash.
 * Starting from 0, increment until hash meets the target.
 * 
 * EXAMPLE (difficulty = 2):
 * nonce=0: hash="8f3a2b..." (invalid, doesn't start with "00")
 * nonce=1: hash="c7e9f1..." (invalid)
 * ...
 * nonce=347: hash="00a8b2..." (VALID! starts with "00")
 */
```

---

## 🧪 Test Scenarios

### Mining Tests

| Test | Scenario | Expected |
|------|----------|----------|
| Valid hash | Mine with difficulty 2 | Hash starts with "00" |
| Valid hash | Mine with difficulty 4 | Hash starts with "0000" |
| Nonce used | Mine block | Nonce > 0 (likely) |
| Consistency | Mine same data twice | Different nonces possible, but both valid |

### Benchmark Tests

| Difficulty | Expected Iterations (approx) | Expected Time (approx) |
|------------|------------------------------|------------------------|
| 1 | ~16 | < 1ms |
| 2 | ~256 | < 10ms |
| 3 | ~4,096 | < 100ms |
| 4 | ~65,536 | < 1 second |
| 5 | ~1,048,576 | several seconds |

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Status |
|---|------|----------------|--------|
| 1 | Add nonce field to Block | 10 min | ⬜ |
| 2 | Update calculateHash() | 15 min | ⬜ |
| 3 | Implement mineBlock() | 45 min | ⬜ |
| 4 | Add mining time measurement | 20 min | ⬜ |
| 5 | Implement meetsTarget() helper | 15 min | ⬜ |
| 6 | Write mining tests | 45 min | ⬜ |
| 7 | Create benchmark demo | 30 min | ⬜ |
| 8 | Add theory comments | 20 min | ⬜ |

**Total Estimated Time:** ~4 hours

---

## 🔗 Dependencies

- Sprint 1 must be completed (HashUtil, Block basics)
- BlockchainConfig.MINING_DIFFICULTY constant

---

## 💡 Implementation Hints

### mineBlock Algorithm
```java
public void mineBlock(int difficulty) {
    String target = "0".repeat(difficulty); // e.g., "0000" for difficulty 4
    
    long startTime = System.currentTimeMillis();
    
    while (!hash.startsWith(target)) {
        nonce++;
        hash = calculateHash();
    }
    
    long endTime = System.currentTimeMillis();
    System.out.println("Block mined in " + (endTime - startTime) + "ms");
    System.out.println("Nonce: " + nonce);
    System.out.println("Hash: " + hash);
}
```

---

## 🚫 Out of Scope

- Blockchain class (chain management) - Sprint 3
- Transactions in blocks - Sprint 4
- Dynamic difficulty adjustment - advanced feature
- Multi-threaded mining - advanced feature
