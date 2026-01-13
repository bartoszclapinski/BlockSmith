# ⚒️ BlockSmith - Product Requirements Document

## 📋 Informacje o projekcie

| Pole | Wartość |
|------|---------|
| **Nazwa projektu** | BlockSmith |
| **Typ** | Projekt edukacyjny |
| **Język** | Java |
| **Autor** | Bartek |
| **Data rozpoczęcia** | Styczeń 2026 |

---

## 🎯 Cel projektu

Zbudowanie **funkcjonalnej implementacji blockchain od zera** w celu nauki i zrozumienia:

1. Jak działają kryptowaluty (Bitcoin, Ethereum)
2. Kryptografia w praktyce (hashing, podpisy cyfrowe)
3. Mechanizm konsensusu Proof-of-Work
4. Struktury danych blockchain
5. Bezpieczeństwo i niezmienność danych

---

## 📚 Czego się nauczysz

### Kryptografia
- [ ] Hashowanie SHA-256 - "odcisk palca" danych
- [ ] Podpisy cyfrowe ECDSA - weryfikacja tożsamości
- [ ] Merkle Trees - efektywne podsumowanie transakcji

### Blockchain
- [ ] Struktura bloku (index, timestamp, hash, previousHash, nonce)
- [ ] Łańcuch bloków i niezmienność (immutability)
- [ ] Genesis Block - blok początkowy

### Proof-of-Work
- [ ] Kopanie (mining) - znajdowanie nonce
- [ ] Trudność (difficulty) - ile zer na początku hasha
- [ ] Dlaczego to wymaga mocy obliczeniowej

### Transakcje i portfele
- [ ] Model transakcji (sender, recipient, amount)
- [ ] Portfele z parami kluczy (publiczny/prywatny)
- [ ] Adresy w stylu Ethereum (0x...)
- [ ] Podpisywanie i weryfikacja transakcji

### System ekonomiczny
- [ ] Nagroda za kopanie (mining reward)
- [ ] Obliczanie salda na podstawie historii transakcji
- [ ] Pula oczekujących transakcji (pending pool)
- [ ] Opłaty transakcyjne (transaction fees) - opcjonalnie

---

## 🏗️ Architektura systemu

```
┌─────────────────────────────────────────────────────────────────┐
│                         BLOCKSMITH                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐         │
│  │   Wallet    │───▶│ Transaction │───▶│   Block     │         │
│  │  (Portfel)  │    │ (Transakcja)│    │   (Blok)    │         │
│  └─────────────┘    └─────────────┘    └─────────────┘         │
│        │                   │                  │                 │
│        │ tworzy            │ zawiera          │ łączy           │
│        ▼                   ▼                  ▼                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    BLOCKCHAIN                            │   │
│  │  ┌───────┐   ┌───────┐   ┌───────┐   ┌───────┐          │   │
│  │  │Block 0│──▶│Block 1│──▶│Block 2│──▶│Block n│          │   │
│  │  │Genesis│   │       │   │       │   │       │          │   │
│  │  └───────┘   └───────┘   └───────┘   └───────┘          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  ┌─────────────┐  ┌──────────────────┐  ┌─────────────────┐    │
│  │  HashUtil   │  │ BlockchainConfig │  │  BlockExplorer  │    │
│  │  (SHA-256)  │  │   (Ustawienia)   │  │   (Podgląd)     │    │
│  └─────────────┘  └──────────────────┘  └─────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 Funkcjonalności do zaimplementowania

### Faza 1: Fundamenty ⚙️
- [ ] Klasa `BlockchainConfig` - centralna konfiguracja (difficulty, mining reward, etc.)
- [ ] Klasa `HashUtil` - hashowanie SHA-256
- [ ] Klasa `Block` - struktura pojedynczego bloku
- [ ] Metoda `calculateHash()` - obliczanie hasha bloku
- [ ] Genesis Block - tworzenie bloku początkowego
- [ ] 📝 Komentarze teoretyczne wyjaśniające działanie algorytmów

**🧪 Testy Fazy 1:**
| Test | Opis |
|------|------|
| Hash determinism | Ten sam input = ten sam output |
| Hash length | Hash zawsze ma 64 znaki (256 bitów hex) |
| Hash uniqueness | Różne inputy = różne hashe |
| Config values | Wartości konfiguracji są prawidłowe |

### Faza 2: Proof-of-Work ⛏️
- [ ] Pole `nonce` w bloku
- [ ] Metoda `mineBlock(difficulty)` - kopanie
- [ ] Walidacja trudności hasha
- [ ] ⏱️ Pomiar czasu kopania (benchmark)

**🧪 Testy Fazy 2:**
| Test | Opis |
|------|------|
| Mining validity | Wykopany hash zaczyna się od wymaganej liczby zer |
| Nonce changes | Nonce zmienia się podczas kopania |
| Difficulty scaling | Wyższa trudność = więcej zer na początku |
| Mining time | Wyższa trudność = dłuższy czas kopania |

### Faza 3: Blockchain 🔗
- [ ] Klasa `Blockchain` - zarządzanie łańcuchem
- [ ] Dodawanie nowych bloków
- [ ] Walidacja całego łańcucha
- [ ] Wykrywanie manipulacji

**🧪 Testy Fazy 3:**
| Test | Opis |
|------|------|
| Chain validation | Prawidłowy łańcuch przechodzi walidację |
| Tamper detection | Zmieniony blok jest wykrywany |
| Link integrity | previousHash każdego bloku = hash poprzedniego |
| Genesis validation | Genesis block jest poprawny |

### Faza 4: Transakcje 💸
- [ ] Klasa `Transaction` - model transakcji
- [ ] Walidacja transakcji (kwota > 0, niepuste pola)
- [ ] Merkle Root - podsumowanie transakcji w bloku
- [ ] Pula oczekujących transakcji

**🧪 Testy Fazy 4:**
| Test | Opis |
|------|------|
| Transaction validity | Poprawna transakcja przechodzi walidację |
| Invalid amount | Kwota <= 0 jest odrzucana |
| Empty fields | Puste pola są odrzucane |
| Merkle root | Merkle root jest prawidłowo obliczany |
| Pending pool | Transakcje są dodawane/usuwane z puli |

### Faza 5: Portfele i podpisy 🔐
- [ ] Klasa `Wallet` - generowanie kluczy ECDSA
- [ ] Generowanie adresu portfela (styl 0x...)
- [ ] Podpisywanie transakcji
- [ ] Weryfikacja podpisów

**🧪 Testy Fazy 5:**
| Test | Opis |
|------|------|
| Key generation | Para kluczy jest generowana poprawnie |
| Address format | Adres zaczyna się od "0x" |
| Signature valid | Poprawny podpis przechodzi weryfikację |
| Signature invalid | Niepoprawny podpis jest odrzucany |
| Different keys | Różne portfele mają różne klucze |

### Faza 6: System ekonomiczny 💰
- [ ] Mining reward - nagroda dla górnika
- [ ] Obliczanie salda adresu
- [ ] Historia transakcji
- [ ] 🆕 Opłaty transakcyjne (transaction fees) - opcjonalnie

**🧪 Testy Fazy 6:**
| Test | Opis |
|------|------|
| Mining reward | Górnik otrzymuje nagrodę po wykopaniu bloku |
| Balance calculation | Saldo jest prawidłowo obliczane |
| Insufficient funds | Transakcja bez wystarczających środków jest odrzucana |
| Transaction history | Historia transakcji jest kompletna |

### Faza 7: Demo i dokumentacja 📖
- [ ] Klasa `BlockSmithDemo` - prezentacja możliwości
- [ ] Klasa `BlockExplorer` - interaktywny podgląd blockchain
- [ ] ⏱️ Benchmark kopania (pokazanie wpływu difficulty na czas)
- [ ] Ładne formatowanie wyjścia w konsoli
- [ ] README projektu

**🧪 Testy Fazy 7:**
| Test | Opis |
|------|------|
| Demo runs | Demo wykonuje się bez błędów |
| Explorer queries | BlockExplorer zwraca poprawne dane |

### 🌟 Faza BONUS: Persystencja (opcjonalna)
- [ ] Zapis łańcucha do pliku JSON
- [ ] Odczyt łańcucha z pliku JSON
- [ ] Walidacja wczytanego łańcucha

**🧪 Testy Fazy BONUS:**
| Test | Opis |
|------|------|
| Save chain | Łańcuch zapisuje się do pliku |
| Load chain | Łańcuch wczytuje się z pliku |
| Loaded validation | Wczytany łańcuch przechodzi walidację |

---

## 🔧 Klasy pomocnicze

### BlockchainConfig
Centralna konfiguracja parametrów blockchain:

```java
public class BlockchainConfig {
    public static final int MINING_DIFFICULTY = 4;       // Liczba zer na początku hasha
    public static final double MINING_REWARD = 50.0;     // Nagroda za wykopanie bloku
    public static final double TRANSACTION_FEE = 0.1;    // Opłata transakcyjna (opcjonalnie)
    public static final String GENESIS_PREV_HASH = "0";  // Previous hash Genesis Block
    public static final String COINBASE_ADDRESS = "COINBASE"; // Adres źródłowy nagród
}
```

### BlockExplorer
Narzędzie do przeglądania blockchain:

```java
public class BlockExplorer {
    public void printChainSummary();              // Podsumowanie całego łańcucha
    public void printBlockDetails(int index);     // Szczegóły pojedynczego bloku
    public void printTransactionHistory(String address); // Historia transakcji adresu
    public void printAddressBalance(String address);     // Saldo adresu
    public void printPendingTransactions();       // Oczekujące transakcje
}
```

---

## 📝 Styl kodu - komentarze teoretyczne

Każda kluczowa metoda powinna zawierać komentarz wyjaśniający teorię:

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
public static String applySha256(String input) { ... }
```

---

## ⚠️ Ograniczenia (poza zakresem)

Ten projekt **NIE** implementuje:
- Sieci peer-to-peer (P2P)
- ~~Persystencji danych (baza danych)~~ → Przeniesione do Fazy BONUS (plik JSON)
- REST API
- Smart kontraktów
- Interfejsu graficznego

To jest **projekt edukacyjny** skupiony na zrozumieniu fundamentów blockchain.

---

## 📊 Kryteria sukcesu

Projekt będzie uznany za ukończony gdy:

1. ✅ Można tworzyć nowe bloki z transakcjami
2. ✅ Kopanie wymaga znalezienia odpowiedniego nonce
3. ✅ Łańcuch jest walidowany i wykrywa manipulacje
4. ✅ Portfele tworzą podpisane transakcje
5. ✅ Salda są prawidłowo obliczane
6. ✅ Demo pokazuje wszystkie funkcjonalności
7. ✅ BlockExplorer pozwala przeglądać blockchain
8. ✅ Testy jednostkowe przechodzą dla każdej fazy
9. ✅ Kod zawiera komentarze teoretyczne

**Kryteria BONUS:**
- ⭐ Blockchain można zapisać/wczytać z pliku JSON
- ⭐ Opłaty transakcyjne są zaimplementowane

---

## 🔗 Inspiracje i zasoby

- [Bitcoin Whitepaper](https://bitcoin.org/bitcoin.pdf) - oryginalny dokument Satoshiego
- [Blockchain Demo](https://andersbrownworth.com/blockchain/) - wizualna demonstracja
- [Mastering Bitcoin](https://github.com/bitcoinbook/bitcoinbook) - książka o Bitcoin
