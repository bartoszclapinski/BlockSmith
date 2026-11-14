# Sprint 22: GitHub Issues

> Created with `--body-file` (one file per issue) after a dry run.

---

## Milestone 22a: Consensus: the sender must sign

---

### Anyone can spend anyone's coins: unsigned transfers and keys not bound to the sender

**Labels:** `sprint-22`, `milestone-22a`, `bug`

**Description:**

Reproduced on master with a funded Victim wallet: an unsigned transfer with
`sender` = Victim is accepted, and so is a transfer signed with Mallory's own
key. `verifySignature()` never checks that the attached public key belongs to
the sender. After one block: Mallory 80 (50 + 30 stolen), Victim 20,
`isChainValid()` true.

**Fix:**
- `Wallet.addressOf(PublicKey)`; `Transaction.isSignedBySender()` (signature
  verifies and address(key) == sender)
- always: a transaction carrying a key must carry the sender's key
- `ChainParams.requireSignatures` (DEFAULT true; `withoutSignatureRule()` for
  tests of other rules); exempt: coinbase, contract claims
- enforced in the mempool and the block rules; tests of other rules switch to
  explicit unsigned params via one helper; the demo uses wallets

**Acceptance Criteria:**
- [ ] Both attacks rejected (mempool and peer block)
- [ ] Compiles with `mvn compile`

---

### Tests for required signatures

**Labels:** `sprint-22`, `milestone-22a`, `test`

**Description:**

**Test Cases:**
1. `unsignedTransfer_rejected`
2. `signedWithOtherKey_rejected_evenWithoutRule`
3. `ownerSigned_accepted`
4. `peerBlockWithUnsignedTransfer_rejected`
5. `contractDeploy_needsFunderSignature_claimStillWorks`
6. `bump_mustBeResigned`
7. `restoreOfUnsignedChain_stopsAtFirstUnsigned`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 22b: API: prepare, sign elsewhere, submit

---

### Prepare / sign / submit in the API

**Labels:** `sprint-22`, `milestone-22b`, `enhancement`

**Description:**

- `POST /api/transactions/prepare`: node builds the tx, returns a template id
  and the exact `signatureData` (kept a few minutes, bounded)
- `POST /api/transactions` with {templateId, signature, publicKey}; unsigned
  submissions are a 400 "signature required" under the rule
- same two steps for contract deploy and fee bump
- retire `POST /api/wallet/create` (server-made keys are not the user's)

**Acceptance Criteria:**
- [ ] A transfer signed outside the node is accepted and mined
- [ ] Compiles with `mvn compile`

---

### Tests for prepare / submit

**Labels:** `sprint-22`, `milestone-22b`, `test`

**Description:**

**Test Cases:**
1. `prepareSignSubmit_minedEndToEnd`
2. `wrongKeyOrTamperedTemplate_rejected`
3. `unknownOrExpiredTemplate_rejected`
4. `deployAndBump_prepareSignSubmit`
5. `unsignedSubmit_400`

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

## Milestone 22c: Dashboard: keys and signing in the browser

---

### Browser keys and signing in the dashboard

**Labels:** `sprint-22`, `milestone-22c`, `enhancement`

**Description:**

- Web Crypto ECDSA P-256 keys; address derived like Java
- keys in `localStorage` (labelled educational), export / import
- send, deploy, bump: prepare -> sign -> DER-encode -> submit
- docs; "API transactions are unsigned" leaves the deferred list

**Acceptance Criteria:**
- [ ] A transfer signed in a real browser is accepted and mined
- [ ] Compiles with `mvn compile`

---

### Cross-implementation test (Web Crypto vs Java)

**Labels:** `sprint-22`, `milestone-22c`, `test`

**Description:**

**Test Cases:**
1. `webCryptoAddress_matchesJava` (fixed vector from a real browser)
2. `webCryptoSignature_verifiesInJava`
3. `dashboardFlow_signedTransferMined` (live, headless browser)

**Acceptance Criteria:**
- [ ] Tests added and passing
- [ ] `mvn test` green

---

*Created: 2026-11-14 | Sprint 22 Planning*
