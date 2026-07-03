# Sprint 15: Multi-signature Wallets - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-03 |
| **Sprint End** | TBD |

---

## Milestone 15a: Signature opcodes in the VM - Pending

---

## Milestone 15b: Multisig contracts (M-of-N) - Pending

---

## Milestone 15c: API + dashboard integration - Pending

---

## Notes

- Final sprint of Phase 3 (API & Interface); completes the phase and the
  wallet/contract/VM arc
- Approach: multisig built ON the Sprint 14 script VM - `CHECKSIG` /
  `CHECKMULTISIG` opcodes, and an M-of-N multisig is a contract with a
  signature-checking locking script (Bitcoin P2SH-style)
- The one new crypto piece is the sighash: `SHA256(contractId + claimer +
  amount)`, which binds a signature to a single claim (replay safety)
- Reuses Sprint 5 `Wallet` primitives (ECDSA secp256r1) and Sprint 14 contract
  machinery; no new dependency
- Signing is a server-side convenience for the demo (flagged educational),
  consistent with the unsigned transaction endpoints from Sprint 12

---

*Created: 2026-07-03*
