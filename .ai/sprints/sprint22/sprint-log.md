# Sprint 22: Required Signatures - Log

## Sprint Timeline

| Event | Date |
|-------|------|
| **Sprint Start** | 2026-11-14 |
| **Sprint End** | 2026-11-14 |

---

## Planning probe (not committed)

On master, Victim funded with 50: an unsigned transfer from Victim and a
transfer signed with Mallory's own key (sender = Victim) were both accepted;
after one block Mallory 80, Victim 20, `isChainValid()` true. Test usage:
178 `new Transaction(` calls in 26 test files; 35 `new Blockchain()` and 47
`new Node(port)` call sites.

---

## Milestone 22a: Consensus: the sender must sign - Complete (PR #338)

- `Wallet.addressOf(PublicKey)`; `Transaction.isSignedBySender()` (signature
  verifies AND address(key) == sender). Always on: a carried key must be the
  sender's. `ChainParams.requireSignatures` (DEFAULT true) +
  `withoutSignatureRule()`; exempt: coinbase, contract claims. Mempool and
  block rules.
- **Also found:** contract scripts were not signed, so a relay could swap
  the locking script of a signed deploy; `getSignatureData()` now covers them.
- Tests of other rules moved to explicit unsigned params via `TestChains`
  (148 mechanical rewrites in 50 files). The demo uses wallets.
- `SignatureRuleTest` (8); 4 mutations caught. Suite: 403 -> 411.
  Issues #331, #332.

---

## Milestone 22b: API: prepare, sign elsewhere, submit - Complete (PR #339)

- `POST /api/transactions/prepare`, `/api/contracts/prepare`,
  `/api/transactions/{id}/bump/prepare` -> templateId + exact signatureData;
  `POST /api/transactions` {templateId, signature, publicKey}; templates
  bounded, 5 min, used once. Unsigned calls get the rule's reason.
- **Found by mutation testing:** `minimumBumpFee` priced a signed bump on an
  unsigned copy, so the advertised minimum failed about 1 time in 3; now the
  signed size + 32 bytes (30-round test).
- `Wallet.signData`, `getPublicKeyHex`. `ApiSigningTest` (6).
  Suite: 411 -> 417. Issues #333, #334.

---

## Milestone 22c: Dashboard: keys and signing in the browser - Complete (PR #340)

- Web Crypto P-256 keys, address like Java, `localStorage` keystore with
  export / checked import, r||s -> DER, prepare -> sign -> submit for send,
  deploys and bumps; read-only marker; `/api/wallet/create` retired (410).
- Live in headless Edge on a real node: create, mine, signed send, signed
  bump, signed hashlock deploy, balance exactly 85.
- `WebCryptoInteropTest` (2): vectors from the dashboard's own code in a real
  browser, one with DER 0x00 padding. The browser-to-node acceptance path is
  covered by the live check, not by an automated test.
- `ForkSyncTest` convergence: a final tie could leave the nodes apart (seen
  once); the test now mines one more block until B follows.
- Suite: 417 -> 419. Issues #335, #336.

---

## Retrospective

- The probe found one hole and the work found three more (keys not bound to
  the sender, unsigned contract scripts, mispriced signed bumps): writing
  the rule down precisely is what exposed them.
- Prepare/submit avoided re-implementing Java's number formatting in
  JavaScript; the interop vectors pin the two crypto stacks together.
- Old local `data/` chains do not restore past their first unsigned
  transfer: start a fresh data directory.

---

*Sprint 22 Log*
