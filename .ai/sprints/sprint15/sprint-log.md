# Sprint 15: Multi-signature Wallets - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-03 |
| **Sprint End** | TBD |

---

## Milestone 15a: Signature opcodes in the VM - Complete

- `SignatureUtil` added: ECDSA sign/verify (secp256r1, SHA256withECDSA) with
  hex encode/decode for public keys (X.509) and signatures (DER). `verify`
  never throws - malformed input is "not valid" (false), consistent with the
  VM's consensus-safety rule.
- `ScriptVM.execute(...)` gained a sighash overload; the existing 3-arg and
  2-arg forms delegate with an empty sighash, so every Sprint 14 call site and
  test is unchanged (signature scripts simply fail without a sighash context).
- `CHECKSIG` and `CHECKMULTISIG` opcodes added. Multisig layout is Bitcoin's
  bare form `<sigs> M <pubkeys> N CHECKMULTISIG`; matching is ordered and
  distinct-key, so a duplicate signature cannot satisfy two slots.
- 6 new tests in `ScriptVMTest` (valid single-sig, 2-of-3, insufficient,
  wrong-key, duplicate-not-double-counted, malformed-never-throws). Suite:
  220 -> 226, all green.
- Issues #147 (opcodes), #148 (tests).

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
