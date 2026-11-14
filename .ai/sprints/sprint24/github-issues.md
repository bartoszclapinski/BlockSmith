# Sprint 24: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 24a: Encrypted keystore: passphrase, unlock, lock, migration

---

### Browser private keys are stored in plaintext: reading the profile files is enough to spend

**Labels:** `sprint-24`, `milestone-24a`, `bug`

**Description:**

Reproduced on master: the dashboard (headless Edge, own profile) creates
Alice's wallet; after the browser is closed, a script that only reads the
profile's `Local Storage/leveldb` files finds the private JWK (`"d":...`),
signs a transfer of 40 to Mallory through the API (201) and Mallory has 40
after one block.

**Fix:**
- Keystore v1 (`bs_keystore`): PBKDF2-SHA256 (600 000 rounds, salt) ->
  AES-256-GCM per key, address as additional data, a passphrase check value
- Set a passphrase (min 10, twice), unlock, lock, auto-lock after 15 min
  idle; unlocked keys are non-extractable `CryptoKey`s in memory only
- Every signing path unlocks on demand
- Plaintext `bs_keys` migrated and removed; signing blocked until then

**Acceptance Criteria:**
- [ ] No private key in the profile files after creating a wallet
- [ ] Compiles with `mvn compile`

---

### Tests for the encrypted keystore

**Labels:** `sprint-24`, `milestone-24a`, `test`

**Description:**

**Test Cases:**
1. `dashboardScript_storesKeysEncryptedOnly` (served script: the keystore
   is written only through the encrypting path; PBKDF2 + AES-GCM present)
2. Live: the probe repeated finds no private key on disk and cannot spend
3. Live: locked -> sign asks for the passphrase; wrong passphrase refused;
   reload = locked

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 24b: Backups and passphrase management + Java interop

---

### Encrypted backups, plaintext import, change passphrase

**Labels:** `sprint-24`, `milestone-24b`, `enhancement`

**Description:**

- Export an encrypted backup (one key or all) with its KDF parameters;
  import with its passphrase, re-encrypted under the current one
- Import an old plaintext export: encrypted on arrival, with a warning
- Change passphrase: re-encrypt everything with a new salt, atomically
- Clear errors for a wrong passphrase or a corrupt / tampered backup

**Acceptance Criteria:**
- [ ] A backup restores into another browser profile and signs there
- [ ] Compiles with `mvn compile`

---

### Interop: a dashboard backup decrypts in Java

**Labels:** `sprint-24`, `milestone-24b`, `test`

**Description:**

**Test Cases:**
1. `browserBackup_decryptsInJava_keyMatchesAddressAndSigns`
2. `browserBackup_wrongPassphrase_fails`
3. `browserBackup_entryMovedToAnotherAddress_fails`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 24c: End-to-end under encryption + docs

---

### End-to-end live check under encryption and keystore docs

**Labels:** `sprint-24`, `milestone-24c`, `enhancement`

**Description:**

- Live: send, bump, hashlock deploy/claim, two-profile multisig claim, all
  from locked keystores unlocked on demand; reload = locked
- Live: migration from a plaintext (Sprint 23) profile; measure what stays
  in the profile files afterwards
- Docs: ARCHITECTURE (format, threat model, recovery without the
  dashboard), STATUS deferred list

**Acceptance Criteria:**
- [ ] Every signing flow works with an encrypted keystore
- [ ] Compiles with `mvn compile`

---

### Keystore end-to-end checks

**Labels:** `sprint-24`, `milestone-24c`, `test`

**Description:**

**Test Cases:**
1. Live: all signing flows with an encrypted keystore, no JS errors
2. Live: the disk scan after migration (result recorded in the sprint log)
3. `mvn test` green

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-14 | Sprint 24 Planning*
