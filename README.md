# ⚒️ BlockSmith

**A complete blockchain and cryptocurrency implementation built from scratch in Java**

BlockSmith is a comprehensive blockchain project that goes beyond tutorials - implementing a fully functional distributed cryptocurrency system with P2P networking, REST API, web dashboard, and basic smart contracts. Built to deeply understand how Bitcoin and Ethereum work under the hood.

---

## 🚀 Features

### Phase 1: Core Blockchain ✅ In Progress
- SHA-256 cryptographic hashing
- Proof-of-Work mining with adjustable difficulty
- ECDSA digital signatures (secp256r1)
- Merkle tree for transaction verification
- Wallet with key pair generation
- Mining rewards and balance tracking
- Chain validation and tamper detection

### Phase 2: Network Layer 🔜 Planned
- P2P networking with TCP sockets
- Node discovery and peer management
- Block and transaction broadcasting
- Mempool synchronization

### Phase 3: API & Interface 🔜 Planned
- REST API for blockchain interaction
- Web dashboard for monitoring
- Basic smart contract support
- Multi-signature wallets

### Phase 4: Production Features 🔜 Planned
- Database persistence (SQLite)
- Dynamic difficulty adjustment
- Block size limits and fee market

---

## 🏛️ Architecture

### How Blockchain Works

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            BLOCKCHAIN STRUCTURE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐                 │
│   │  BLOCK #0   │      │  BLOCK #1   │      │  BLOCK #2   │                 │
│   │  (Genesis)  │      │             │      │             │                 │
│   ├─────────────┤      ├─────────────┤      ├─────────────┤                 │
│   │ prevHash: 0 │◄─────│ prevHash ───│◄─────│ prevHash ───│◄── ...          │
│   │ timestamp   │      │ timestamp   │      │ timestamp   │                 │
│   │ data/txs    │      │ data/txs    │      │ data/txs    │                 │
│   │ nonce       │      │ nonce       │      │ nonce       │                 │
│   │ hash ───────│─────►│ hash ───────│─────►│ hash        │                 │
│   └─────────────┘      └─────────────┘      └─────────────┘                 │
│                                                                             │
│   Each block contains the hash of the previous block, creating an           │
│   immutable chain. Changing any block invalidates all following blocks.     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Transaction Flow

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│    WALLET    │     │  TRANSACTION │     │   MEMPOOL    │     │    BLOCK     │
│              │     │              │     │  (Pending)   │     │              │
│  Private Key │────►│ Sign with    │────►│ Validate &   │────►│ Mine with    │
│  Public Key  │     │ Private Key  │     │ Queue        │     │ Proof-of-Work│
│  Address     │     │              │     │              │     │              │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
                                                                      │
                                                                      ▼
                                                               ┌──────────────┐
                                                               │  BLOCKCHAIN  │
                                                               │  Add Block   │
                                                               │  Update      │
                                                               │  Balances    │
                                                               └──────────────┘
```

### Full System Architecture (After Phase 3)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BLOCKSMITH NETWORK                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│    ┌─────────────┐         ┌─────────────┐         ┌─────────────┐          │
│    │   NODE A    │◄───────►│   NODE B    │◄───────►│   NODE C    │          │
│    │             │   P2P   │             │   P2P   │             │          │
│    │ Blockchain  │         │ Blockchain  │         │ Blockchain  │          │
│    │ Mempool     │         │ Mempool     │         │ Mempool     │          │
│    │ Wallet      │         │ Wallet      │         │ Wallet      │          │
│    └──────┬──────┘         └──────┬──────┘         └──────┬──────┘          │
│           │                       │                       │                 │
│           │ REST API              │ REST API              │ REST API        │
│           ▼                       ▼                       ▼                 │
│    ┌─────────────┐         ┌─────────────┐         ┌─────────────┐          │
│    │ Web Client  │         │ Web Client  │         │ Mobile App  │          │
│    │ Dashboard   │         │ Dashboard   │         │ (Future)    │          │
│    └─────────────┘         └─────────────┘         └─────────────┘          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Proof-of-Work Mining

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PROOF-OF-WORK MINING                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   Target: Hash must start with "0000" (difficulty = 4)                      │
│                                                                             │
│   nonce = 0  ──► hash("...0") = "8a3f2b..."  ✗ Invalid                     │
│   nonce = 1  ──► hash("...1") = "c7e9f1..."  ✗ Invalid                     │
│   nonce = 2  ──► hash("...2") = "1d4a8c..."  ✗ Invalid                     │
│      ...                                                                    |
│   nonce = 52847 ──► hash("...52847") = "0000a8b2..."  ✓ VALID!             │
│                                                                             │
│   Average attempts for difficulty 4: ~65,536 hashes                         │
│   Higher difficulty = More zeros = Exponentially harder                     │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

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

## 📚 Skills & Technologies

### Core Blockchain
- Cryptographic hashing (SHA-256)
- Digital signatures (ECDSA)
- Proof-of-Work consensus
- Merkle trees & data structures

### Networking
- TCP socket programming
- P2P protocols
- Distributed systems
- Concurrent programming

### Full-Stack
- REST API design (Javalin)
- Web development (HTML/JS)
- Database design (SQLite)

### Software Engineering
- Clean architecture
- Unit testing (JUnit 5)
- Documentation
- Git workflow

---

## 🏗️ Development Status

### Phase 1: Core Blockchain
| Sprint | Status |
|--------|--------|
| Sprint 0: Project Setup | ✅ Complete |
| Sprint 1: Fundamentals | ✅ Complete |
| Sprint 2: Proof-of-Work | ⬜ Next |
| Sprint 3: Blockchain | ⬜ Pending |
| Sprint 4: Transactions | ⬜ Pending |
| Sprint 5: Wallets | ⬜ Pending |
| Sprint 6: Economics | ⬜ Pending |
| Sprint 7: Demo | ⬜ Pending |

### Phase 2: Network Layer
| Sprint | Status |
|--------|--------|
| Sprint 8: P2P Networking | ⬜ Planned |
| Sprint 9: Node Discovery | ⬜ Planned |
| Sprint 10: Block Broadcasting | ⬜ Planned |
| Sprint 11: Mempool Sync | ⬜ Planned |

### Phase 3: API & Interface
| Sprint | Status |
|--------|--------|
| Sprint 12: REST API | ⬜ Planned |
| Sprint 13: Web Dashboard | ⬜ Planned |
| Sprint 14: Smart Contracts | ⬜ Planned |
| Sprint 15: Multi-sig Wallets | ⬜ Planned |

### Phase 4: Production
| Sprint | Status |
|--------|--------|
| Sprint 16: Database | ⬜ Planned |
| Sprint 17: Difficulty Adjustment | ⬜ Planned |
| Sprint 18: Block Limits | ⬜ Planned |
| Sprint 19: Fee Market | ⬜ Planned |

---

## 📝 License

This project is for educational purposes.

---

## 👤 Author

**Bartek** - [GitHub](https://github.com/bartoszclapinski)