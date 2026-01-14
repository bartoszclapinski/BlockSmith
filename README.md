# ⚒️ BlockSmith

**Blockchain implementation in Java with Proof-of-Work mining and transaction system**

---

## 🚀 Features (planned)

- SHA-256 cryptographic hashing
- Proof-of-Work mining with adjustable difficulty
- ECDSA digital signatures for transactions
- Merkle tree for transaction verification
- Wallet with key pair generation
- Mining rewards and balance tracking
- Chain validation and tamper detection

---

## 📋 Prerequisites

- **Java JDK 20** or higher
- **Maven 3.8** or higher

---

## 🔧 Build & Run

### Compile the project
```bash
mvn clean compile
```

### Run tests
```bash
mvn test
```

### Run the demo
```bash
mvn exec:java
```

### Create JAR package
```bash
mvn package
java -jar target/blocksmith-1.0.0.jar
```

---

## 📁 Project Structure

```
BlockSmith/
├── src/main/java/com/blocksmith/
│   ├── core/           # Block, Blockchain, Transaction, Wallet
│   ├── util/           # HashUtil, BlockchainConfig, BlockExplorer
│   └── BlockSmithDemo.java
├── src/test/java/      # Unit tests
├── data/               # Blockchain persistence (JSON)
└── pom.xml             # Maven configuration
```

---

## 📚 Learning Objectives

This project demonstrates:
- Cryptographic hashing (SHA-256)
- Digital signatures (ECDSA)
- Proof-of-Work consensus mechanism
- Blockchain data structures
- Merkle trees
- Transaction validation

---

## 🏗️ Development Status

| Phase | Status |
|-------|--------|
| Sprint 0: Project Setup | 🟡 In Progress |
| Sprint 1: Fundamentals | ⬜ Not Started |
| Sprint 2: Proof-of-Work | ⬜ Not Started |
| Sprint 3: Blockchain | ⬜ Not Started |
| Sprint 4: Transactions | ⬜ Not Started |
| Sprint 5: Wallets | ⬜ Not Started |
| Sprint 6: Economics | ⬜ Not Started |
| Sprint 7: Demo | ⬜ Not Started |

---

## 📝 License

This project is for educational purposes.

---

## 👤 Author

**Bartek** - [GitHub](https://github.com/bartoszclapinski)