# Sprint 15: Multi-signature Wallets - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-07-03 |
| **Sprint End** | 2026-07-05 |

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

## Milestone 15b: Multisig contracts (M-of-N) - Complete

- `MultiSigWallet` added: holds N member public keys (hex) + threshold M,
  builds the `CHECKMULTISIG` locking script, and derives a stable `MS:` identity
  address from that script. Holds public keys only - signing stays with the
  member key holders. Factories `ofPublicKeys` / `ofHex`.
- Claim sighash lives on `Contract.claimSighash(claimer)` =
  `SHA256(contractId + claimer + amount)` - the data lives on the contract, so
  both the signer and the verifier compute the identical message.
- A multisig is just a Sprint 14 contract with a signature lock: deploy/claim/
  registry are unchanged. The ONE wiring change is `Blockchain.isValidClaim`
  now passing the claim sighash into `scriptVM.execute(...)`; hashlock and
  timelock contracts ignore it, so nothing from Sprint 14 changes.
- Replay safety falls out of the sighash: a 2-of-3 set signed for one claimer
  fails when presented for another (different sighash).
- 5 new tests in `MultiSigContractTest` (2-of-3 valid, one-sig rejected,
  wrong-key rejected, replay-to-different-claimer rejected, cross-node
  convergence). Suite: 226 -> 231, all green.
- Issues #150 (multisig), #151 (tests).

---

## Milestone 15c: API + dashboard integration - Complete

- `POST /api/multisig/create` (members N + threshold M): the node generates the
  N member key pairs, keeps the private keys server-side, and returns only the
  address, member public keys, and CHECKMULTISIG locking script. Private keys
  are NEVER serialized (verified live: no `privateKey` field in the response).
- `POST /api/multisig/claim` (contractId + claimer): the node looks up the
  member keys it holds for the contract's lock, signs the claim sighash with
  the first M, assembles `PUSH sig1 ... PUSH sigM`, and submits the claim. This
  is the educational server-side signing convenience, flagged as such and
  consistent with the unsigned transaction endpoints from Sprint 12.
- Member keys are stored in `ApiServer.multisigSessions`, keyed by locking
  script, so a claim finds its keys from the contract alone.
- Dashboard: a new Multisig panel creates a wallet (and hands the lock to the
  Deploy form) and claims from a contract id; errors surface inline. Reuses the
  existing action styles - no CSS change.
- 2 new server tests in `ApiMultiSigTest` (full HTTP lifecycle; rejection
  envelopes). Suite: 231 -> 233, all green.
- Verified LIVE against a running node: create -> deploy -> mine -> claim ->
  mine credited the claimer 20 BSC and closed the contract CLAIMED; rejections
  returned a 400 error envelope; the dashboard serves the panel.
- Issues #153 (endpoints/UI), #154 (tests).

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
