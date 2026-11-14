# Sprint 24: Encrypted Browser Keystore - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | - |

---

## Planning probe (not committed)

On master: the dashboard in headless Edge (own profile) created Alice's
wallet with the Create wallet button and the node mined 50 to it. After the
browser was closed, a script that only read the profile's files found the
private JWK in `Default/Local Storage/leveldb/000003.log`, signed a transfer
of 40 to Mallory through `/api/transactions/prepare` + `POST
/api/transactions`: 201; after one block Mallory had 40, Alice 10.

---

## Milestone 24a: Encrypted keystore: passphrase, unlock, lock, migration - Not started

## Milestone 24b: Backups and passphrase management + Java interop - Not started

## Milestone 24c: End-to-end under encryption + docs - Not started

---

*Sprint 24 Log*
