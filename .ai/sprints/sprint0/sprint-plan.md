# Sprint 0: Project Setup

## 📋 Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 0 |
| **Title** | Project Setup |
| **Duration** | 1 week |
| **Phase** | Setup |
| **Status** | Not Started |

---

## 🎯 Goal

Initialize the Maven project with proper structure, dependencies, and configuration. This sprint focuses on setup only - no implementation code.

---

## 📦 Deliverables

### 1. Maven Project Configuration
- [ ] Create `pom.xml` with:
  - Group ID: `com.blocksmith`
  - Artifact ID: `blocksmith`
  - Version: `1.0.0`
  - Java 20 compiler settings
  - JUnit 5 dependency (5.10.1)
  - Gson dependency (2.10.1)
  - Maven plugins (compiler, surefire, jar)

### 2. Package Structure
- [ ] Create source directories:
  ```
  src/main/java/com/blocksmith/
  ├── core/
  └── util/
  ```
- [ ] Create test directories:
  ```
  src/test/java/com/blocksmith/
  ├── core/
  └── util/
  ```

### 3. Placeholder Classes (empty with TODOs)
- [ ] `com.blocksmith.core.Block.java`
- [ ] `com.blocksmith.core.Blockchain.java`
- [ ] `com.blocksmith.core.Transaction.java`
- [ ] `com.blocksmith.core.Wallet.java`
- [ ] `com.blocksmith.util.HashUtil.java`
- [ ] `com.blocksmith.util.BlockchainConfig.java`
- [ ] `com.blocksmith.util.BlockExplorer.java`
- [ ] `com.blocksmith.BlockSmithDemo.java`

### 4. Project Documentation
- [ ] Create basic `README.md` with:
  - Project name and description
  - Prerequisites (Java 20, Maven)
  - Build instructions
  - Project status

### 5. Git Configuration
- [ ] Initialize Git repository (if not already)
- [ ] Create `.gitignore` for Java/Maven

### 6. Data Directory
- [ ] Create `data/` folder for future persistence (BONUS phase)

---

## ✅ Acceptance Criteria

| # | Criteria | Status |
|---|----------|--------|
| 1 | `mvn clean compile` executes without errors | ⬜ |
| 2 | All placeholder classes exist in correct packages | ⬜ |
| 3 | Project structure matches tech-stack.md specification | ⬜ |
| 4 | README.md contains basic project information | ⬜ |
| 5 | .gitignore properly excludes build artifacts | ⬜ |

---

## 🔧 Technical Notes

### pom.xml Key Elements
```xml
<properties>
    <maven.compiler.source>20</maven.compiler.source>
    <maven.compiler.target>20</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>
```

### Placeholder Class Template
```java
package com.blocksmith.core;

/**
 * TODO: Implement in Sprint X
 * 
 * [Brief description of what this class will do]
 */
public class ClassName {
    // TODO: Implementation
}
```

---

## 📝 Tasks Breakdown

| # | Task | Estimated Time | Status |
|---|------|----------------|--------|
| 1 | Create pom.xml | 15 min | ⬜ |
| 2 | Create directory structure | 10 min | ⬜ |
| 3 | Create placeholder classes | 20 min | ⬜ |
| 4 | Create README.md | 15 min | ⬜ |
| 5 | Create .gitignore | 5 min | ⬜ |
| 6 | Verify Maven build | 10 min | ⬜ |

**Total Estimated Time:** ~1-2 hours

---

## 🚫 Out of Scope

- Any actual implementation code
- Unit tests (will be added in Sprint 1+)
- Detailed documentation
