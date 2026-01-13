# 🛠️ BlockSmith - Tech Stack

## Technologie używane w projekcie

---

## ☕ Język programowania

| Technologia | Wersja | Opis |
|-------------|--------|------|
| **Java** | 20+ | Główny język projektu |

### Dlaczego Java?
- Silne typowanie - mniej błędów w runtime
- Bogata biblioteka standardowa (w tym kryptografia)
- Doskonałe wsparcie dla OOP
- Popularna w enterprise i fintech

---

## 📦 Build Tool

| Narzędzie | Wersja | Opis |
|-----------|--------|------|
| **Maven** | 3.9.x | Zarządzanie projektem i zależnościami |

### Główne komendy Maven:
```bash
mvn clean compile    # Kompilacja
mvn test             # Uruchomienie testów
mvn package          # Tworzenie JAR
mvn exec:java        # Uruchomienie aplikacji
```

---

## 📚 Zależności (Dependencies)

### Produkcyjne

| Biblioteka | Wersja | Cel użycia |
|------------|--------|------------|
| **Gson** | 2.10.1 | Serializacja JSON (persystencja w fazie BONUS) |

### Testowe

| Biblioteka | Wersja | Cel użycia |
|------------|--------|------------|
| **JUnit 5** | 5.10.x | Testy jednostkowe |

---

## 🔐 Kryptografia (wbudowana w Java)

Używamy **Java Cryptography Architecture (JCA)** - wbudowana w JDK:

| Algorytm | Klasa Java | Zastosowanie |
|----------|------------|--------------|
| **SHA-256** | `MessageDigest` | Hashowanie bloków i transakcji |
| **ECDSA** | `Signature`, `KeyPairGenerator` | Podpisy cyfrowe |
| **secp256r1** | `ECGenParameterSpec` | Krzywa eliptyczna dla kluczy |

### Przykład użycia SHA-256:
```java
MessageDigest digest = MessageDigest.getInstance("SHA-256");
byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
```

### Przykład generowania kluczy ECDSA:
```java
KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
ECGenParameterSpec ecSpec = new ECGenParameterSpec("secp256r1");
keyGen.initialize(ecSpec, SecureRandom.getInstanceStrong());
KeyPair keyPair = keyGen.generateKeyPair();
```

---

## 📁 Struktura projektu

```
BlockSmith/
├── .ai/                          # Dokumentacja projektu
│   ├── prd.md                    # Opis projektu
│   ├── tech-stack.md             # Ten plik
│   └── sprints/                  # Plany i logi sprintów
│       ├── sprint0/
│       ├── sprint1/
│       └── ...
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/
│   │           └── blocksmith/
│   │               ├── core/           # Główne klasy blockchain
│   │               │   ├── Block.java
│   │               │   ├── Blockchain.java
│   │               │   ├── Transaction.java
│   │               │   └── Wallet.java
│   │               ├── util/           # Narzędzia pomocnicze
│   │               │   ├── HashUtil.java
│   │               │   ├── BlockchainConfig.java
│   │               │   └── BlockExplorer.java
│   │               └── BlockSmithDemo.java
│   └── test/
│       └── java/
│           └── com/
│               └── blocksmith/
│                   ├── core/           # Testy klas głównych
│                   │   ├── BlockTest.java
│                   │   ├── BlockchainTest.java
│                   │   ├── TransactionTest.java
│                   │   └── WalletTest.java
│                   └── util/           # Testy narzędzi
│                       ├── HashUtilTest.java
│                       └── BlockExplorerTest.java
├── data/                         # Dane aplikacji (Faza BONUS)
│   └── blockchain.json           # Zapisany łańcuch
├── pom.xml                       # Konfiguracja Maven
└── README.md                     # Dokumentacja publiczna
```

---

## 🧪 Testowanie

### Framework: JUnit 5

```java
@Test
@DisplayName("Hash should be 64 characters")
void testHashLength() {
    String hash = HashUtil.applySha256("test");
    assertEquals(64, hash.length());
}
```

### Struktura testów (per faza):

| Faza | Klasy testowe |
|------|---------------|
| Faza 1 | `HashUtilTest`, `BlockTest` (basic), `BlockchainConfigTest` |
| Faza 2 | `BlockTest` (mining) |
| Faza 3 | `BlockchainTest` |
| Faza 4 | `TransactionTest` |
| Faza 5 | `WalletTest` |
| Faza 6 | `BlockchainTest` (economics) |
| Faza 7 | `BlockExplorerTest`, `DemoTest` |
| BONUS | `PersistenceTest` |

### Uruchamianie testów:
```bash
mvn test                           # Wszystkie testy
mvn test -Dtest=HashUtilTest       # Konkretna klasa
mvn test -Dtest="*Test"            # Wzorzec nazwy
```

---

## 🖥️ IDE i narzędzia

| Narzędzie | Zastosowanie |
|-----------|--------------|
| **Cursor** | Główne IDE |
| **Git** | Kontrola wersji |
| **PowerShell** | Terminal na Windows |

---

## 📋 Wymagania systemowe

- **Java JDK**: 17 lub nowsza (mamy 20)
- **Maven**: 3.8 lub nowsza (mamy 3.9.12)
- **RAM**: minimum 2GB
- **Dysk**: ~100MB na projekt

---

## 🔧 Konfiguracja pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.blocksmith</groupId>
    <artifactId>blocksmith</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <name>BlockSmith</name>
    <description>Educational blockchain implementation in Java</description>

    <properties>
        <maven.compiler.source>20</maven.compiler.source>
        <maven.compiler.target>20</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <junit.version>5.10.1</junit.version>
        <gson.version>2.10.1</gson.version>
    </properties>

    <dependencies>
        <!-- JSON (for persistence in BONUS phase) -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>${gson.version}</version>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.2</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>com.blocksmith.BlockSmithDemo</mainClass>
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 📊 Konwencje kodu

### Komentarze teoretyczne
Każda kluczowa metoda powinna zawierać blok `THEORY:` wyjaśniający działanie:

```java
/**
 * THEORY: [Wyjaśnienie teoretyczne algorytmu]
 * 
 * SECURITY: [Aspekty bezpieczeństwa]
 * 
 * BLOCKCHAIN USE: [Zastosowanie w blockchain]
 */
```

### Nazewnictwo
- Klasy: `PascalCase` (np. `BlockExplorer`)
- Metody: `camelCase` (np. `calculateHash`)
- Stałe: `UPPER_SNAKE_CASE` (np. `MINING_DIFFICULTY`)
- Pakiety: `lowercase` (np. `com.blocksmith.core`)
