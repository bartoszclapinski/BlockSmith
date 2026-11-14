# Sprint 24: Encrypted Browser Keystore

## Sprint Info

| Field | Value |
|-------|-------|
| **Sprint** | 24 |
| **Title** | Encrypted Browser Keystore |
| **Phase** | Hardening (after the roadmap) |
| **Status** | ✅ Complete (2026-11-14) |
| **Depends On** | Sprint 22 (browser keys), Sprint 23 (multisig member keys) |

> **Why:** since Sprints 22-23 the node never holds a private key: the
> dashboard makes and keeps them, in `localStorage` (`bs_keys`), as plain
> JWK. A probe on master (not committed):
>
> 1. The dashboard (headless Edge, its own profile) creates Alice's wallet
>    with the Create wallet button; Alice gets 50
> 2. The browser is closed. A script that only READS FILES opens the
>    profile's `Local Storage/leveldb/000003.log` and finds
>    `"privateKey":{..."d":"rNUAMefN..."}`
> 3. With that key, outside any browser, it prepares, signs and submits a
>    transfer of 40 to Mallory: **201**; after one block Mallory has 40,
>    Alice 10
>
> Whoever gets a copy of the browser profile (a backup, a synced folder,
> malware that reads files, a shared computer) gets the money. The same
> holds for every multisig member key.

---

## Goal

Private keys are stored only encrypted under a passphrase the user chooses
(PBKDF2-SHA256 -> AES-256-GCM, Web Crypto, no library). Addresses, public
keys and labels stay readable, so balances and member lists work while
locked; signing needs the keystore unlocked. Unlocked keys live in memory
only (non-extractable `CryptoKey`s) and are forgotten on lock, idle timeout
or tab close. The format is documented and standard: a backup can be
decrypted without the dashboard (a Java test does it).

No node or consensus change: this sprint is the dashboard plus a Java
interop test.

---

## Milestones

| Milestone | Title | Branch | Status |
|-----------|-------|--------|--------|
| **24a** | Encrypted keystore: passphrase, unlock, lock, migration | `sprint24a/encrypted-keystore` | ✅ PR #360 (#353, #354) |
| **24b** | Backups and passphrase management + Java interop | `sprint24b/keystore-backup` | ✅ PR #361 (#355, #356) |
| **24c** | End-to-end under encryption + docs | `sprint24c/keystore-e2e` | ✅ PR #362 (#357, #358) |

---

## Milestone 24a: Encrypted keystore

### Deliverables

- [ ] Keystore v1 in `localStorage` (`bs_keystore`, see Theory): KDF
      parameters + salt, a passphrase check value, and per key
      {publicKey, label, iv, ciphertext}; the ciphertext is the private JWK
      under AES-256-GCM with the address as additional data
- [ ] First key (or first visit with old keys): "Set a passphrase" (min 10
      characters, typed twice, "cannot be recovered" warning)
- [ ] Unlock (passphrase -> derived key -> check value) / Lock button /
      auto-lock after 15 min idle; unlocked keys are non-extractable
      `CryptoKey`s in memory
- [ ] Every signing path (send, bump, deploy, multisig claim, member sign)
      asks to unlock when locked, then continues
- [ ] Migration: plaintext `bs_keys` is encrypted into the keystore and
      removed; signing is blocked until that happens
- [ ] Tests: the served dashboard never writes a private key outside the
      encrypted keystore (static check); live: the probe repeated finds no
      private key in the profile files and cannot spend

---

## Milestone 24b: Backups and passphrase management

### Deliverables

- [ ] Export an encrypted backup (one key or all) with its KDF parameters;
      import it with ITS passphrase, re-encrypted under the current one
- [ ] Import an old plaintext export (Sprint 22 format): encrypted on
      arrival, with a warning to delete the plaintext copy
- [ ] Change passphrase (re-encrypt everything with a new salt, atomically)
- [ ] Clear errors: wrong passphrase, corrupt or tampered backup
- [ ] `KeystoreInteropTest` (Java, `javax.crypto`): a backup made by the
      dashboard in a real browser decrypts with its passphrase, the key
      matches the address and its signature verifies; a wrong passphrase
      and an entry moved to another address fail

---

## Milestone 24c: End-to-end under encryption + docs

### Deliverables

- [ ] Live: send, bump, hashlock deploy/claim and a two-profile multisig
      claim, all with locked keystores unlocked on demand; reload = locked
- [ ] Live: migration from a Sprint 23 profile (plaintext keys) and what is
      left on disk afterwards (see Open decisions)
- [ ] Docs: ARCHITECTURE (keystore format, threat model, recovery without
      the dashboard), STATUS (the deferred item "keys in localStorage
      without encryption" changes to what remains)

---

## Theory: what the passphrase protects

```
 passphrase --PBKDF2(SHA-256, 600 000 rounds, 16-byte salt)--> 256-bit key   (never stored)
 private JWK --AES-256-GCM(key, 12-byte iv, additional data = address)--> ciphertext + tag

 bs_keystore = { version: 1,
                 kdf:   { name: "PBKDF2", hash: "SHA-256", iterations: 600000, salt },
                 check: { iv, ciphertext }                  # a known text: wrong passphrase -> fails here
                 keys:  { <address>: { publicKey, label, iv, ciphertext } } }
```

- **PBKDF2** makes each passphrase guess cost 600 000 SHA-256 rounds (the
  OWASP 2023 figure); the salt stops one precomputed table cracking every
  keystore.
- **AES-GCM** is authenticated: a wrong key or a flipped bit fails to
  decrypt instead of producing garbage. The address as additional data
  binds each ciphertext to its entry: moving it under another address fails.
- **Protects:** the keys at rest (profile copies, backups, file-reading
  malware, someone at a locked dashboard).
- **Does not protect:** a malicious script running while the keystore is
  unlocked (it can ask the keys to sign, though not read them), a keylogger,
  a weak passphrase. Hardware wallets exist for these.

---

## Open decisions

- **KDF: PBKDF2-SHA256, 600 000 rounds** (recommended: built into Web
  Crypto, no build step) vs Argon2id (stronger against GPUs, needs a WASM
  library)
- **One passphrase for the keystore** (recommended) vs one per key
- **Auto-lock after 15 minutes idle** (recommended), plus lock on reload
- **Migration is mandatory**: signing with plaintext keys stops until a
  passphrase is set (recommended), vs optional encryption
- **Old plaintext on disk:** removing `bs_keys` does not scrub the browser's
  files at once (LevelDB keeps old records until compaction). 24c measures
  it; the docs advise moving funds to a new key if an old profile copy may
  exist

---

*Created: 2026-11-14 | Sprint 24 Planning | Completed: 2026-11-14*
