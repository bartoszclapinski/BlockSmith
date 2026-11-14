# Sprint 24: Encrypted Browser Keystore - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | 2026-11-14 |

---

## Planning probe (not committed)

On master: the dashboard in headless Edge (own profile) created Alice's
wallet with the Create wallet button and the node mined 50 to it. After the
browser was closed, a script that only read the profile's files found the
private JWK in `Default/Local Storage/leveldb/000003.log`, signed a transfer
of 40 to Mallory through `/api/transactions/prepare` + `POST
/api/transactions`: 201; after one block Mallory had 40, Alice 10.

---

## Milestone 24a: Encrypted keystore: passphrase, unlock, lock, migration - Complete (PR #360)

- `bs_keystore` v1: PBKDF2-SHA256 (600 000, salt) -> AES-256-GCM per key,
  address as additional data, a passphrase check value; public data stays
  readable while locked.
- "Protect your keys" / "Unlock" dialog, non-extractable keys in memory,
  Lock button, auto-lock after 15 minutes idle, reload = locked; every
  signing path unlocks on demand, Cancel signs nothing.
- Plaintext `bs_keys` encrypted and removed; nothing signs until then.
- Export became an encrypted one-key backup, so its import (with the
  backup's passphrase) moved here from 24b.
- `DashboardKeystoreTest` (2). **Found by mutation testing:** the "unlock
  before signing" check passed when the unlock was missing (`indexOf` = -1);
  fixed. Live: the probe's file read finds 0 plaintext keys. Suite:
  428 -> 430. Issues #353, #354.

---

## Milestone 24b: Backups and passphrase management + Java interop - Complete (PR #361)

- Whole-keystore backup; one `importKeys` for both backup kinds and old
  plaintext exports (every pair checked before any is stored); change
  passphrase (new salt, one write); precise errors.
- `KeystoreInteropTest` (3): a backup from a real browser opens with
  `javax.crypto` (PBKDF2WithHmacSHA256 + AES/GCM, address as AAD), each key
  matches its address and signs; wrong passphrase, moved entry and a changed
  byte fail with `AEADBadTagException`. This is the recovery path without
  the dashboard. `DashboardKeystoreTest` +1; 3 mutations caught.
- Live: change passphrase (old one refused after), backups imported into a
  second profile with labels. Suite: 430 -> 434. Issues #355, #356.

---

## Milestone 24c: End-to-end under encryption + docs - Complete (PR #362)

- Live: send, bump, hashlock deploy, a new member key and a two-profile
  multisig claim, each from a locked keystore; hashlock claim needs no
  prompt; settled on chain; no JS errors.
- **Measured:** a profile closed with Sprint 23 plaintext keys still has
  both in `Local Storage/leveldb/000003.log` after migration and after one
  more open/close: removing an entry writes a deletion marker, and LevelDB
  drops the old record only on compaction. Docs: treat such keys as exposed,
  make new ones and move the funds.
- ARCHITECTURE: keystore format, threat model, recovery; STATUS deferred
  list. `DashboardKeystoreTest` +1 (every signature goes through the
  keystore). Suite: 434 -> 435. Issues #357, #358.

---

## Retrospective

- The probe needed no browser at all: reading one file was enough. The fix
  protects the files, and the measurement showed its limit: what was
  already written in plain text stays there.
- Moving the one-key import into 24a kept export and import together; a
  milestone with an export nobody could import would have shipped a trap.
- The Java interop test turns "the format is standard" into a fact and
  documents recovery without the dashboard.
- Encryption at rest does not stop a malicious script while unlocked.

---

*Sprint 24 Log*
