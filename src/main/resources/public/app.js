/*
 * BlockSmith dashboard - "command deck" redesign.
 *
 * Vanilla JS, no build step. Polls the real REST API every REFRESH_MS and
 * renders a single-screen deck: chain hero, activity feed (derived client-side
 * from poll diffs), actions, balance, and contracts. A command palette (Cmd/
 * Ctrl+K), side drawers, and toasts drive node actions. Two themes (dark /
 * light) flip via a data-theme attribute on <html>.
 *
 * All node-supplied values are written with textContent (never innerHTML) so
 * data from the chain can never inject markup.
 */

const REFRESH_MS = 4000;

/**
 * Set by demo.js on the recorded demo of the project site (Sprint 28b); null
 * on a node. In the demo the answers come from a recording of a real node,
 * the clock is the recording's, and nothing can be signed or changed.
 */
const DEMO = window.BlockSmithDemo || null;

/** The moment relative ages ("3s", "2m") are counted from: the recording's clock in the demo. */
function nowMs() { return DEMO ? DEMO.now() : Date.now(); }

// ===== small helpers =====

async function fetchJson(path, options) {
    if (DEMO) return DEMO.answer(path, options);
    const res = await fetch(path, options);
    const data = await res.json().catch(function () { return null; });
    if (!res.ok) {
        const message = data && data.error ? data.error : "HTTP " + res.status;
        throw new Error(message);
    }
    return data;
}

function postJson(path, body) {
    return fetchJson(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {})
    });
}

/** element with optional class and text. Extra children can be appended. */
function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}

/** Short hash for display: first 8 + last 4 hex chars. */
function shortHash(hash) {
    if (!hash) return "";
    if (hash.length <= 14) return hash;
    return hash.slice(0, 8) + "…" + hash.slice(-4);
}

/** Compact relative age from a timestamp (ms). */
function relTime(ts) {
    const secs = Math.max(0, Math.round((nowMs() - ts) / 1000));
    if (secs < 3) return "now";
    if (secs < 60) return secs + "s";
    const mins = Math.round(secs / 60);
    if (mins < 60) return mins + "m";
    const hours = Math.round(mins / 60);
    if (hours < 24) return hours + "h";
    return Math.round(hours / 24) + "d";
}

/** SHA-256 hex of a string (matches HashUtil.applySha256 on the node). */
async function sha256hex(message) {
    const bytes = new TextEncoder().encode(message);
    const digest = await crypto.subtle.digest("SHA-256", bytes);
    return Array.from(new Uint8Array(digest))
        .map(function (b) { return b.toString(16).padStart(2, "0"); })
        .join("");
}

// ===== browser keystore (Sprint 22c; encrypted since Sprint 24a) =====
//
// Keys are made and kept HERE, with Web Crypto (ECDSA P-256 = Java's
// secp256r1). The node never sees a private key: it hands out the exact
// text to sign (prepare), and gets back a signature and the public key
// (submit).
//
// THEORY: a passphrase protects the keys at rest (Sprint 24a)
//
//   passphrase --PBKDF2(SHA-256, 600 000 rounds, salt)--> AES-256 key   (never stored)
//   private JWK --AES-GCM(key, iv, additional data = address)--> ciphertext
//
// localStorage holds ciphertext plus what is public anyway (address, public
// key, label), so balances and member lists work while locked. Unlocking
// decrypts each key into a NON-extractable CryptoKey kept in memory until
// Lock, 15 minutes idle, or a reload. A wrong passphrase fails on the check
// value; a ciphertext moved under another address fails AES-GCM's tag.
// A copy of the browser profile no longer gives anyone a key.
//
//   bs_keystore = { version: 1, kdf: {name, hash, iterations, salt},
//                   check: {iv, ciphertext},
//                   keys: {<address>: {publicKey, label, iv, ciphertext}} }

const KEYSTORE = "bs_keystore";
const LEGACY_KEYSTORE = "bs_keys";            // Sprints 22-23: plaintext JWKs; encrypted, then removed
const EC = { name: "ECDSA", namedCurve: "P-256" };
const SIGN = { name: "ECDSA", hash: "SHA-256" };
const KDF_ITERATIONS = 600000;                // OWASP 2023 for PBKDF2-HMAC-SHA256
const MIN_PASSPHRASE = 10;
const AUTO_LOCK_MS = 15 * 60 * 1000;
const CHECK_TEXT = "BlockSmith keystore v1";

/** The unlocked state: memory only, gone on Lock, idle timeout or reload. */
const vault = { aes: null, keys: {}, timer: null };

function readJson(name) {
    try { return JSON.parse(localStorage.getItem(name) || "null"); } catch (e) { return null; }
}

function loadStore() { return readJson(KEYSTORE); }

/** Throws if storage is unavailable: better than losing a new key silently. */
function saveStore(store) { localStorage.setItem(KEYSTORE, JSON.stringify(store)); }

function loadLegacy() { return readJson(LEGACY_KEYSTORE) || {}; }

/**
 * Public view of every key here: {address: {publicKey, label, plaintextBefore,
 * retiredTo}}. Never a secret. A plaintext (Sprint 22-23) key counts as
 * plaintextBefore from the start.
 */
function loadKeys() {
    const out = {};
    const legacy = loadLegacy();
    Object.keys(legacy).forEach(function (a) {
        out[a] = { publicKey: legacy[a].publicKey, label: legacy[a].label, plaintextBefore: true };
    });
    const store = loadStore();
    if (store) Object.keys(store.keys).forEach(function (a) {
        const e = store.keys[a];
        out[a] = { publicKey: e.publicKey, label: e.label, plaintextBefore: e.plaintextBefore, retiredTo: e.retiredTo };
    });
    return out;
}

/** True if this browser holds the key for {@code address} (locked or not). */
function hasKey(address) {
    return !!(address && loadKeys()[address]);
}

/** "none" (no keys yet), "legacy" (plaintext keys waiting for a passphrase), "locked", "unlocked". */
function keystoreState() {
    if (Object.keys(loadLegacy()).length) return "legacy";
    if (!loadStore()) return "none";
    return vault.aes ? "unlocked" : "locked";
}

function bytesToHex(bytes) {
    return Array.from(new Uint8Array(bytes)).map(function (b) { return b.toString(16).padStart(2, "0"); }).join("");
}

function hexToBytes(hex) {
    const out = new Uint8Array(hex.length / 2);
    for (let i = 0; i < out.length; i++) out[i] = parseInt(hex.substr(i * 2, 2), 16);
    return out;
}

/** Same as Java's Wallet.addressOf: "0x" + last 40 hex of SHA-256(hex of the X.509/SPKI key). */
async function addressOfPublicKeyHex(spkiHex) {
    return "0x" + (await sha256hex(spkiHex)).slice(-40);
}

/** passphrase -> AES-256-GCM key; non-extractable, never stored. */
async function deriveAesKey(passphrase, kdf) {
    const material = await crypto.subtle.importKey("raw", new TextEncoder().encode(passphrase), "PBKDF2", false, ["deriveKey"]);
    return crypto.subtle.deriveKey({ name: "PBKDF2", hash: "SHA-256", salt: hexToBytes(kdf.salt), iterations: kdf.iterations },
        material, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
}

/** AES-GCM with a fresh 12-byte iv; {@code aad} (additional data) is authenticated, not encrypted. */
async function seal(aes, text, aad) {
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const ct = await crypto.subtle.encrypt({ name: "AES-GCM", iv: iv, additionalData: new TextEncoder().encode(aad) },
        aes, new TextEncoder().encode(text));
    return { iv: bytesToHex(iv), ciphertext: bytesToHex(ct) };
}

/** Throws (OperationError) on a wrong key, a changed byte or a different {@code aad}. */
async function unseal(aes, box, aad) {
    const pt = await crypto.subtle.decrypt({ name: "AES-GCM", iv: hexToBytes(box.iv), additionalData: new TextEncoder().encode(aad) },
        aes, hexToBytes(box.ciphertext));
    return new TextDecoder().decode(pt);
}

/** Restarts the 15-minute idle timer (any click or key press while unlocked). */
function touchVault() {
    if (!vault.aes) return;
    clearTimeout(vault.timer);
    vault.timer = setTimeout(function () {
        lockKeystore();
        toast("Keys locked after 15 minutes idle");
    }, AUTO_LOCK_MS);
}

function lockKeystore() {
    clearTimeout(vault.timer);
    vault.aes = null;
    vault.keys = {};
    vault.timer = null;
    renderLock();
}

/** First passphrase: a new keystore, then the plaintext keys (if any) move into it. */
async function createKeystore(passphrase) {
    const kdf = { name: "PBKDF2", hash: "SHA-256", iterations: KDF_ITERATIONS, salt: randomHex(16) };
    const aes = await deriveAesKey(passphrase, kdf);
    saveStore({ version: 1, kdf: kdf, check: await seal(aes, CHECK_TEXT, "check"), keys: {} });
    vault.aes = aes;
    vault.keys = {};
    await migrateLegacy();
    touchVault();
}

async function unlockKeystore(passphrase) {
    const store = loadStore();
    const aes = await deriveAesKey(passphrase, store.kdf);
    try {
        if (await unseal(aes, store.check, "check") !== CHECK_TEXT) throw new Error("check");
    } catch (e) { throw new Error("Wrong passphrase"); }
    const keys = {};
    for (const address of Object.keys(store.keys)) {
        let jwk;
        try { jwk = JSON.parse(await unseal(aes, store.keys[address], address)); }
        catch (e) { throw new Error("The stored key for " + shortHash(address) + " does not decrypt (moved or damaged)"); }
        keys[address] = await crypto.subtle.importKey("jwk", jwk, EC, false, ["sign"]);
    }
    vault.aes = aes;
    vault.keys = keys;
    await migrateLegacy();
    touchVault();
}

/**
 * Encrypts a private JWK into the keystore; memory keeps a non-extractable
 * copy. {@code flags} (Sprint 25c): plaintextBefore (the key once existed in
 * plain text), retiredTo (its funds were moved to that address).
 */
async function storeKey(address, publicKey, jwk, label, flags) {
    if (!vault.aes) throw new Error("Unlock your keys first");
    const box = await seal(vault.aes, JSON.stringify(jwk), address);
    const store = loadStore();
    const f = flags || {};
    store.keys[address] = { publicKey: publicKey, label: label, plaintextBefore: f.plaintextBefore || undefined,
        retiredTo: f.retiredTo || undefined, iv: box.iv, ciphertext: box.ciphertext };
    saveStore(store);
    vault.keys[address] = await crypto.subtle.importKey("jwk", jwk, EC, false, ["sign"]);
}

/** Sprint 22-23 plaintext keys -> the encrypted keystore; the plaintext entry is removed. */
async function migrateLegacy() {
    const legacy = loadLegacy();
    const addresses = Object.keys(legacy);
    if (!addresses.length) return;
    for (const address of addresses) {
        const e = legacy[address];
        if (!loadStore().keys[address]) await storeKey(address, e.publicKey, e.privateKey, e.label, { plaintextBefore: true });
    }
    localStorage.removeItem(LEGACY_KEYSTORE);
    toast("Encrypted " + addresses.length + " key" + (addresses.length === 1 ? "" : "s") + " that were stored in plain text");
}

function removeKeys(addresses) {
    const store = loadStore();
    if (!store) return;
    addresses.forEach(function (a) { delete store.keys[a]; delete vault.keys[a]; });
    saveStore(store);
}

/** Resolves once keys can sign or be added; asks for the passphrase (or a first one) if needed. */
async function ensureUnlocked() {
    // A recording has nothing to sign for: refuse before asking for a passphrase.
    if (DEMO) throw new Error(DEMO.readOnly);
    if (vault.aes) {
        await migrateLegacy();
        touchVault();
        return;
    }
    await askPassphrase();
}

let passphrasePrompt = null;      // one prompt at a time; later callers wait on it

/** The passphrase dialog: "Protect your keys" the first time, "Unlock" after. Rejects on Cancel. */
function askPassphrase() {
    if (passphrasePrompt) return passphrasePrompt;
    passphrasePrompt = new Promise(function (resolve, reject) {
        const creating = !loadStore();
        const plain = Object.keys(loadLegacy()).length;
        const root = el("div", "unlock-root");
        const back = el("div", "backdrop");
        const box = el("div", "unlock-box");
        box.setAttribute("role", "dialog");
        box.appendChild(el("div", "drawer-title", creating ? "Protect your keys" : "Unlock your keys"));
        box.appendChild(el("p", "note", creating
            ? (plain ? "This browser keeps " + plain + " key" + (plain === 1 ? "" : "s") + " in plain text: anyone with a copy "
                + "of the browser's files can spend with them. " : "")
              + "Choose a passphrase: keys are stored only encrypted with it. It cannot be recovered; without it the keys "
              + "are lost, so keep an exported backup and the passphrase apart."
            : "Enter your passphrase to sign. Keys stay unlocked in this tab until you lock them, reload, or after 15 minutes idle."));
        const pass = passwordField("Passphrase", creating ? "new-password" : "current-password");
        const again = creating ? passwordField("Passphrase again", "new-password") : null;
        const err = errorBox();
        const label = creating ? "Encrypt and unlock" : "Unlock";
        const ok = submitButton(label, go);
        const cancel = el("button", "seg-btn", "Cancel");
        cancel.type = "button";
        cancel.addEventListener("click", function () { finish(new Error("Keys are locked: nothing was signed")); });
        box.appendChild(pass.wrap);
        if (again) box.appendChild(again.wrap);
        box.appendChild(err.node);
        box.appendChild(ok);
        box.appendChild(cancel);
        root.appendChild(back);
        root.appendChild(box);
        root.addEventListener("keydown", function (e) {
            e.stopPropagation();                         // not the palette / drawer shortcuts
            if (e.key === "Enter") go();
            if (e.key === "Escape") cancel.click();
        });

        function finish(error) {
            root.remove();
            passphrasePrompt = null;
            renderLock();
            if (error) reject(error); else resolve();
        }

        async function go() {
            const p = pass.input.value;
            if (creating) {
                if (p.length < MIN_PASSPHRASE) return err.show("At least " + MIN_PASSPHRASE + " characters.");
                if (p !== again.input.value) return err.show("The two passphrases differ.");
            }
            err.hide();
            ok.disabled = true;
            ok.textContent = creating ? "Encrypting…" : "Unlocking…";
            try {
                if (creating) await createKeystore(p); else await unlockKeystore(p);
                finish(null);
            } catch (e) {
                ok.disabled = false;
                ok.textContent = label;
                err.show(e.message);
            }
        }

        document.body.appendChild(root);
        setTimeout(function () { pass.input.focus(); }, 30);
    });
    return passphrasePrompt;
}

/** The top-bar key button: Locked / Unlocked / plaintext warning; hidden without keys. */
function renderLock() {
    const btn = document.getElementById("lock-btn");
    if (!btn) return;
    const st = keystoreState();
    btn.hidden = st === "none";
    btn.className = "lock-btn " + st;
    btn.textContent = st === "unlocked" ? "🔓 Unlocked" : st === "legacy" ? "⚠ Encrypt keys" : "🔒 Locked";
    btn.title = st === "unlocked" ? "Lock your keys now" : st === "legacy" ? "Keys are in plain text: set a passphrase"
        : "Unlock your keys to sign";
}

function onLockButton() {
    if (keystoreState() === "unlocked") {
        lockKeystore();
        toast("Keys locked");
    } else {
        askPassphrase().catch(function () {});
    }
}

/** Makes a key pair here; {@code label} names keys that are not wallets (multisig members). */
async function createBrowserWallet(label) {
    await ensureUnlocked();
    const pair = await crypto.subtle.generateKey(EC, true, ["sign", "verify"]);
    const publicKey = bytesToHex(await crypto.subtle.exportKey("spki", pair.publicKey));
    const address = await addressOfPublicKeyHex(publicKey);
    await storeKey(address, publicKey, await crypto.subtle.exportKey("jwk", pair.privateKey), label);
    return address;
}

/** The key this browser holds for a public key (hex SPKI): {address, entry}, or null. */
function keyForPublicKey(publicKeyHex) {
    const want = String(publicKeyHex || "").trim().toLowerCase();
    const keys = loadKeys();
    for (const address in keys) {
        if (keys[address].publicKey === want) return { address: address, entry: keys[address] };
    }
    return null;
}

/** Names a key (public, stored next to the ciphertext); travels with a backup. */
function labelKey(address, label) {
    const store = loadStore();
    if (!store || !store.keys[address]) return;
    store.keys[address].label = label;
    saveStore(store);
}

/** Marks {@code address} as retired: its funds went to {@code to} (Sprint 25c). The key is kept. */
function retireKey(address, to) {
    const store = loadStore();
    const e = store && store.keys[address];
    if (!e) return;
    e.retiredTo = to;
    e.label = (e.label ? e.label + " · " : "") + "retired → " + shortHash(to);
    saveStore(store);
}

/**
 * Moves everything spendable from {@code oldAddress} to a new key (Sprint 25c).
 *
 * THEORY: Key rotation
 *
 * Encryption protects a key from now on; it cannot take back a copy that
 * already exists. Sprint 24 measured that keys stored in plain text before
 * the migration stay in the browser's files (LevelDB keeps old records
 * until compaction). A key that may have been copied is retired the way
 * wallets do it: make a new key and send it everything. Whoever holds the
 * old key then holds an empty address.
 *
 *   amount = floor((balance - pending outgoing - fee) * 1e8) / 1e8
 *
 * Rounded DOWN, so binary fractions never make amount + fee exceed what the
 * node allows; at most 0.00000001 stays behind. The old key signs (it owns
 * the funds), through the keystore like every other signature. The old key
 * is kept, marked retired: money sent to it later can be moved again.
 */
async function moveFundsToNewKey(oldAddress, fee) {
    const info = await fetchJson("/api/wallet/" + encodeURIComponent(oldAddress));
    const spendable = info.balance - (info.pendingOutgoing || 0);
    const amount = Math.floor((spendable - fee) * 1e8) / 1e8;
    if (!(amount > 0)) {
        throw new Error("Nothing to move: " + formatAmount(spendable) + " BSC spendable, the fee is " + formatAmount(fee));
    }
    await ensureUnlocked();
    const fresh = await createBrowserWallet("moved from " + shortHash(oldAddress));
    try {
        const template = await postJson("/api/transactions/prepare",
            { sender: oldAddress, recipient: fresh, amount: amount, fee: fee });
        const tx = await signAndSubmit(template, oldAddress);           // the OLD key signs
        retireKey(oldAddress, fresh);
        return { address: fresh, amount: amount, fee: fee, tx: tx };
    } catch (e) {
        removeKeys([fresh]);                                           // nothing moved: no orphan key
        throw e;
    }
}

/** SPKI keys share a long common prefix (the curve), so show their tail. */
function keyTail(publicKeyHex) {
    return "…" + String(publicKeyHex || "").slice(-12);
}

/**
 * An encrypted backup of one key: the stored ciphertext plus the keystore's
 * KDF parameters, so it opens with the passphrase it was made under (and
 * with standard tools, see ARCHITECTURE). No unlocking needed: nothing is
 * decrypted to make it.
 */
function keyBackup(address) {
    const store = loadStore();
    const e = store && store.keys[address];
    if (!e) return null;
    return JSON.stringify({ blocksmith: "key-backup", version: 1, kdf: store.kdf, address: address,
        publicKey: e.publicKey, label: e.label, plaintextBefore: e.plaintextBefore, retiredTo: e.retiredTo,
        iv: e.iv, ciphertext: e.ciphertext });
}

/** The whole keystore as an encrypted backup (Sprint 24b): every key, its labels, the KDF and check value. */
function keystoreBackup() {
    const store = loadStore();
    return store ? JSON.stringify(Object.assign({ blocksmith: "keystore-backup" }, store)) : null;
}

/** Checks that a private JWK, its public key and its address belong together. */
async function checkKeyPair(jwk, publicKey, address) {
    const priv = await crypto.subtle.importKey("jwk", jwk, EC, true, ["sign"]);
    const pub = await crypto.subtle.importKey("spki", hexToBytes(publicKey), EC, true, ["verify"]);
    const probe = new TextEncoder().encode("blocksmith key check");
    const sig = await crypto.subtle.sign(SIGN, priv, probe);
    if (!(await crypto.subtle.verify(SIGN, pub, sig, probe))) throw new Error("The private key does not match the public key");
    const derived = await addressOfPublicKeyHex(publicKey);
    if (address && address !== derived) throw new Error("The backup's address does not match its key");
    return derived;
}

/**
 * Imports keys (Sprint 24a/24b): a keystore backup or a one-key backup
 * (each opens with ITS passphrase), or an old plaintext export ({publicKey,
 * privateKey}, Sprints 22-23). Every pair is checked, then stored encrypted
 * under THIS keystore. Returns the imported addresses.
 */
async function importKeys(text, backupPassphrase) {
    let backup;
    try { backup = JSON.parse(text); } catch (e) { throw new Error("Paste the exported JSON"); }
    if (!backup || typeof backup !== "object") throw new Error("Not a BlockSmith key backup");

    const found = [];                 // [{address, publicKey, label, jwk}]
    if (backup.blocksmith === "keystore-backup" || backup.blocksmith === "key-backup") {
        if (!backup.kdf || !backup.kdf.salt || !(backup.kdf.iterations > 0)) throw new Error("Not a BlockSmith key backup (no KDF parameters)");
        if (!backupPassphrase) throw new Error("This backup is encrypted: enter the passphrase it was made with");
        const aes = await deriveAesKey(backupPassphrase, backup.kdf);
        const entries = backup.blocksmith === "key-backup" ? { [backup.address]: backup } : (backup.keys || {});
        if (backup.blocksmith === "keystore-backup") {
            try {
                if (await unseal(aes, backup.check, "check") !== CHECK_TEXT) throw new Error("check");
            } catch (e) { throw new Error("Wrong passphrase for this backup"); }
        }
        for (const address of Object.keys(entries)) {
            const e = entries[address];
            let jwk;
            try { jwk = JSON.parse(await unseal(aes, e, address)); }
            catch (err) {
                throw new Error(backup.blocksmith === "key-backup"
                    ? "The backup does not open: wrong passphrase, or the backup was changed"
                    : "The key for " + shortHash(address) + " does not open: the backup was changed");
            }
            found.push({ address: address, publicKey: e.publicKey, label: e.label, jwk: jwk,
                flags: { plaintextBefore: e.plaintextBefore, retiredTo: e.retiredTo } });
        }
    } else if (backup.publicKey && backup.privateKey) {
        // It sat in a file in plain text: whoever saw that file has the key (Sprint 25c).
        found.push({ address: null, publicKey: backup.publicKey, label: backup.label, jwk: backup.privateKey,
            flags: { plaintextBefore: true } });
    } else {
        throw new Error("Not a BlockSmith key backup");
    }
    if (!found.length) throw new Error("The backup holds no keys");

    for (const k of found) k.address = await checkKeyPair(k.jwk, k.publicKey, k.address);   // all checked before any is stored
    await ensureUnlocked();
    for (const k of found) await storeKey(k.address, k.publicKey, k.jwk, k.label, k.flags);
    return found.map(function (k) { return k.address; });
}

/**
 * New passphrase (Sprint 24b): every key is decrypted with the current one
 * and re-encrypted under a new salt; the keystore is written once, so a
 * failure half way leaves the old one intact. Old backups keep their own
 * passphrase.
 */
async function changePassphrase(current, next) {
    if (next.length < MIN_PASSPHRASE) throw new Error("The new passphrase needs at least " + MIN_PASSPHRASE + " characters.");
    if (keystoreState() === "legacy") await ensureUnlocked();
    const store = loadStore();
    if (!store) throw new Error("No keys to protect yet");
    const oldAes = await deriveAesKey(current, store.kdf);
    try {
        if (await unseal(oldAes, store.check, "check") !== CHECK_TEXT) throw new Error("check");
    } catch (e) { throw new Error("The current passphrase is wrong"); }

    const kdf = { name: "PBKDF2", hash: "SHA-256", iterations: KDF_ITERATIONS, salt: randomHex(16) };
    const aes = await deriveAesKey(next, kdf);
    const keys = {};
    const unlocked = {};
    for (const address of Object.keys(store.keys)) {
        const e = store.keys[address];
        const jwkText = await unseal(oldAes, e, address);
        const box = await seal(aes, jwkText, address);
        keys[address] = Object.assign({}, e, { iv: box.iv, ciphertext: box.ciphertext });   // label and flags kept
        unlocked[address] = await crypto.subtle.importKey("jwk", JSON.parse(jwkText), EC, false, ["sign"]);
    }
    saveStore({ version: 1, kdf: kdf, check: await seal(aes, CHECK_TEXT, "check"), keys: keys });   // one write
    vault.aes = aes;
    vault.keys = unlocked;
    touchVault();
    renderLock();
}

/**
 * Web Crypto signs as r||s (2 x 32 bytes, "IEEE P1363"); Java's
 * SHA256withECDSA verifies DER: SEQUENCE { INTEGER r, INTEGER s }. Each
 * INTEGER drops leading zero bytes, and gets one 0x00 back if its first
 * byte has the top bit set (DER integers are signed).
 */
function p1363ToDer(sig) {
    function integer(bytes) {
        let i = 0;
        while (i < bytes.length - 1 && bytes[i] === 0) i++;
        let body = Array.from(bytes.slice(i));
        if (body[0] & 0x80) body = [0].concat(body);
        return [0x02, body.length].concat(body);
    }
    const half = sig.length / 2;
    const seq = integer(sig.slice(0, half)).concat(integer(sig.slice(half)));
    return new Uint8Array([0x30, seq.length].concat(seq));
}

/** Signs {@code text} with {@code address}'s key (unlocking first if needed): {signature (hex DER), publicKey (hex SPKI)}. */
async function signText(address, text) {
    const entry = loadKeys()[address];
    if (!entry) throw new Error("No key for " + shortHash(address) + " in this browser (read-only address)");
    await ensureUnlocked();
    const key = vault.keys[address];
    if (!key) throw new Error("No key for " + shortHash(address) + " in this browser (read-only address)");
    const sig = new Uint8Array(await crypto.subtle.sign(SIGN, key, new TextEncoder().encode(text)));
    return { signature: bytesToHex(p1363ToDer(sig)), publicKey: entry.publicKey };
}

/** Step 2 + 3 of prepare / sign / submit (Sprint 22b): sign the node's exact text, send it back. */
async function signAndSubmit(template, address) {
    const signed = await signText(address, template.signatureData);
    return postJson("/api/transactions", {
        templateId: template.templateId, signature: signed.signature, publicKey: signed.publicKey
    });
}

function randomHex(n) {
    const bytes = new Uint8Array(n);
    crypto.getRandomValues(bytes);
    return Array.from(bytes).map(function (b) { return b.toString(16).padStart(2, "0"); }).join("");
}

/** Infers a contract's display type from its locking script. */
function contractType(lockingScript) {
    const s = lockingScript || "";
    if (s.indexOf("CHECKMULTISIG") !== -1) {
        const tokens = s.trim().split(/\s+/);
        const m = tokens[1];
        const n = tokens[tokens.length - 2];
        const label = (/^\d+$/.test(m) && /^\d+$/.test(n)) ? (m + "-OF-" + n) : "MULTISIG";
        return { label: label, kind: "multi" };
    }
    if (s.indexOf("SHA256") !== -1 || s.indexOf("EQUAL") !== -1) {
        return { label: "HASHLOCK", kind: "hash" };
    }
    return { label: "SCRIPT", kind: "other" };
}

// ===== app state =====

const state = {
    activeAddress: null,
    feed: [],           // { dot, title, meta, ts }
    prev: null,         // last poll snapshot for feed derivation
    reachable: false,
};

const overlay = document.getElementById("overlay-root");

// ===== theme =====

function currentTheme() {
    return document.documentElement.getAttribute("data-theme") === "light" ? "light" : "dark";
}

function applyTheme(theme) {
    if (theme === "light") document.documentElement.setAttribute("data-theme", "light");
    else document.documentElement.removeAttribute("data-theme");
    try { localStorage.setItem("bs_theme", theme); } catch (e) {}
    bg.recolor();
}

function toggleTheme() {
    applyTheme(currentTheme() === "light" ? "dark" : "light");
}

// ===== background canvas =====

const bg = (function () {
    const cv = document.getElementById("bg");
    const ctx = cv.getContext("2d");
    let W = 0, H = 0, dpr = 1, nodes = [], raf = null;
    let accent = "224,137,76", muted = "126,114,96";
    const reduce = window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    function readColors() {
        const cs = getComputedStyle(document.documentElement);
        accent = cs.getPropertyValue("--canvas-accent").trim() || accent;
        muted = cs.getPropertyValue("--canvas-muted").trim() || muted;
    }

    function resize() {
        dpr = Math.min(window.devicePixelRatio || 1, 2);
        W = cv.clientWidth; H = cv.clientHeight;
        cv.width = W * dpr; cv.height = H * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        const N = Math.max(24, Math.min(58, Math.round(W * H / 48000)));
        if (nodes.length !== N) {
            nodes = Array.from({ length: N }, function () {
                return {
                    x: Math.random() * W, y: Math.random() * H,
                    vx: (Math.random() - 0.5) * 0.17, vy: (Math.random() - 0.5) * 0.17,
                    s: 6 + Math.random() * 11, a: Math.random() < 0.22,
                };
            });
        }
        if (reduce) draw();
    }

    function draw() {
        ctx.clearRect(0, 0, W, H);
        for (let i = 0; i < nodes.length; i++) {
            for (let j = i + 1; j < nodes.length; j++) {
                const dx = nodes[i].x - nodes[j].x, dy = nodes[i].y - nodes[j].y;
                const d = Math.hypot(dx, dy);
                if (d < 195) {
                    ctx.strokeStyle = "rgba(" + accent + "," + (0.10 * (1 - d / 195)).toFixed(3) + ")";
                    ctx.lineWidth = 1;
                    ctx.beginPath(); ctx.moveTo(nodes[i].x, nodes[i].y); ctx.lineTo(nodes[j].x, nodes[j].y); ctx.stroke();
                }
            }
        }
        for (const p of nodes) {
            if (!reduce) {
                p.x += p.vx; p.y += p.vy;
                if (p.x < -24) p.x = W + 24; if (p.x > W + 24) p.x = -24;
                if (p.y < -24) p.y = H + 24; if (p.y > H + 24) p.y = -24;
            }
            ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(0.16);
            ctx.fillStyle = p.a ? "rgba(" + accent + ",0.13)" : "rgba(" + muted + ",0.08)";
            ctx.strokeStyle = p.a ? "rgba(" + accent + ",0.32)" : "rgba(" + muted + ",0.20)";
            ctx.lineWidth = 1;
            ctx.beginPath(); ctx.rect(-p.s / 2, -p.s / 2, p.s, p.s); ctx.fill(); ctx.stroke();
            ctx.restore();
        }
        if (!reduce) raf = requestAnimationFrame(draw);
    }

    return {
        start: function () {
            readColors();
            resize();
            window.addEventListener("resize", resize);
            if (!reduce) draw();
        },
        recolor: readColors,
    };
})();

// ===== toast =====

let toastTimer = null;
function toast(message, isError) {
    const existing = overlay.querySelector(".toast");
    if (existing) existing.remove();
    const t = el("div", "toast" + (isError ? " error" : ""));
    t.appendChild(el("span", "toast-mark", isError ? "⚠" : "✓"));
    t.appendChild(el("span", null, message));
    overlay.appendChild(t);
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.remove(); }, 2600);
}

// ===== activity feed (derived from poll diffs) =====

function pushFeed(dot, title, meta) {
    state.feed.unshift({ dot: dot, title: title, meta: meta, ts: nowMs() });
    state.feed = state.feed.slice(0, 8);
}

function deriveFeed(next) {
    const prev = state.prev;
    if (!prev) {
        // First successful poll: seed a single entry, no backfill spam.
        pushFeed("dot-green", "Dashboard connected", "node " + next.nodeId);
        return;
    }
    // Chain reorganised (Sprint 21): a branch with more work won.
    if (next.reorgs > prev.reorgs && next.lastReorg) {
        const r = next.lastReorg;
        pushFeed("dot-amber", "Chain reorganised", r.depth + " block(s) replaced from #" + (r.forkHeight + 1)
            + " · " + r.transactionsBack + " tx back to mempool");
    }
    // New tip block(s).
    if (next.tipIndex > prev.tipIndex) {
        pushFeed("dot-accent", "Block #" + next.tipIndex + " mined", shortHash(next.tipHash));
    }
    // New pending transactions.
    if (next.pending > prev.pending) {
        const delta = next.pending - prev.pending;
        pushFeed("dot-green", "Tx entered mempool", delta + " pending · " + next.pending + " total");
    }
    // New contracts.
    next.contracts.forEach(function (c) {
        const before = prev.contractStatus[c.contractId];
        if (before === undefined) {
            const t = contractType(c.lockingScript);
            pushFeed("dot-amber", "Contract deployed", t.label + " · " + c.amount + " BSC");
        } else if (before !== "CLAIMED" && c.status === "CLAIMED") {
            pushFeed("dot-green", "Contract claimed", shortHash(c.contractId));
        }
    });
    // Peer count change.
    if (next.peers > prev.peers) pushFeed("dot-amber", "Peer connected", next.peers + " peers");
    else if (next.peers < prev.peers) pushFeed("dot-amber", "Peer left", next.peers + " peers");
}

// ===== rendering =====

function renderSync(status, ok) {
    const sync = document.getElementById("sync");
    const text = document.getElementById("sync-text");
    if (ok) {
        sync.classList.remove("stale");
        // nodeId already starts with "node-"
        text.textContent = "synced · " + status.nodeId + " · " + status.connectedPeers + " peers";
    } else {
        sync.classList.add("stale");
        text.textContent = "node unreachable";
    }
    const footHealth = document.getElementById("footer-health");
    footHealth.classList.toggle("stale", !ok);
    footHealth.lastChild.textContent = ok ? "chain healthy" : "node unreachable";
    if (ok) {
        document.getElementById("footer-p2p").textContent = "P2P " + status.p2pPort;
        document.getElementById("footer-api").textContent = "API " + status.apiPort;
    }
}

function renderHero(status, page) {
    const tipIndex = page.height;
    // Live difficulty (Sprint 17d): leading-zero equivalent of the next target.
    const difficulty = typeof status.difficulty === "number" ? status.difficulty.toFixed(2) : status.difficulty;
    const retarget = status.blocksUntilRetarget != null
        ? " · retarget in " + status.blocksUntilRetarget + (status.blocksUntilRetarget === 1 ? " block" : " blocks")
        : "";
    document.getElementById("hero-meta").textContent =
        "height " + tipIndex + " · difficulty " + difficulty + retarget + " · " + status.pendingTransactions + " pending";

    const row = document.getElementById("chain-row");
    row.replaceChildren();

    // Newest first, up to 6 cards: the page already comes in that order (Sprint 27b).
    const shown = page.blocks;
    shown.forEach(function (block, i) {
        if (i > 0) row.appendChild(el("div", "conn"));
        const isTip = i === 0;
        // A link (Sprint 27b): each card opens its block in the explorer.
        const card = el("a", "block " + (isTip ? "block-tip" : "block-older"));
        card.href = blockLink(block.index);
        const top = el("div", "block-top");
        top.appendChild(el("span", "block-num", "#" + block.index));
        if (isTip) top.appendChild(el("span", "tip-badge", "TIP"));
        card.appendChild(top);
        card.appendChild(el("div", "block-hash", shortHash(block.hash)));
        const txCount = block.transactionCount || 0;
        // Size and fees (Sprint 18d) instead of the nonce: what a block holds, not how it was mined.
        const fees = typeof block.totalFees === "number" && block.totalFees > 0
            ? " · fees " + formatAmount(block.totalFees) : "";
        const size = typeof block.sizeBytes === "number" ? " · " + formatBytes(block.sizeBytes) : "";
        card.appendChild(el("div", "block-meta", txCount + " tx" + fees + size));
        row.appendChild(card);
    });

    // Trailing "genesis" cap only when older blocks exist beyond the window.
    const oldestShown = shown.length ? shown[shown.length - 1].index : 0;
    if (oldestShown > 0) {
        row.appendChild(el("div", "conn"));
        const all = el("a", "genesis", "all blocks ···");
        all.href = "#/blocks";
        row.appendChild(all);
    }
}

function renderFeed() {
    const list = document.getElementById("feed-list");
    list.replaceChildren();
    if (state.feed.length === 0) {
        list.appendChild(el("div", "feed-empty", "No activity yet"));
        return;
    }
    state.feed.forEach(function (f) {
        const row = el("div", "feed-row");
        row.appendChild(el("span", "dot " + f.dot));
        row.appendChild(el("span", "feed-title", f.title));
        row.appendChild(el("span", "feed-meta", f.meta || ""));
        row.appendChild(el("span", "feed-time", relTime(f.ts)));
        list.appendChild(row);
    });
}

function renderBalance(balanceInfo) {
    const amount = document.getElementById("balance-amount");
    const addr = document.getElementById("balance-address");
    if (!state.activeAddress) {
        amount.textContent = "—";
        addr.textContent = "no active wallet";
        return;
    }
    if (balanceInfo) {
        amount.textContent = Number(balanceInfo.balance).toFixed(1);
        const pending = balanceInfo.pendingOutgoing;
        const key = loadKeys()[state.activeAddress];
        addr.textContent = state.activeAddress + (pending ? "  (−" + formatAmount(pending) + " pending)" : "")
            + (!key ? "  · read-only (no key in this browser)"
                : key.retiredTo ? "  · retired key (funds moved to " + shortHash(key.retiredTo) + ")"
                : key.plaintextBefore ? "  · this key was once stored in plain text: move its funds (Wallet)" : "");
    } else {
        amount.textContent = "—";
        addr.textContent = state.activeAddress;
    }
}

function renderContracts(contracts) {
    const list = document.getElementById("contract-list");
    list.replaceChildren();
    if (!contracts.length) {
        list.appendChild(el("div", "contract-empty", "No contracts deployed yet"));
        return;
    }
    // Open first, then claimed; newest within each.
    const ordered = contracts.slice().reverse().sort(function (a, b) {
        return (a.status === "CLAIMED" ? 1 : 0) - (b.status === "CLAIMED" ? 1 : 0);
    });
    ordered.forEach(function (c) {
        const claimed = c.status === "CLAIMED";
        const t = contractType(c.lockingScript);
        const row = el("div", "contract-row" + (claimed ? " claimed" : ""));
        row.appendChild(el("span", "contract-amount", c.amount + " BSC"));
        row.appendChild(el("span", "contract-tag " + t.kind, t.label));
        row.appendChild(el("span", "contract-id", shortHash(c.contractId)));
        if (!claimed) {
            row.addEventListener("click", function () { openClaimDrawer(c); });
        }
        list.appendChild(row);
    });
}

// ===== peers (Sprint 26c) =====

/** The node's live links: direction, address, short node id, state. */
function renderPeers(peers) {
    const list = document.getElementById("peer-list");
    list.replaceChildren();
    document.getElementById("peer-count").textContent = peers.length ? String(peers.length) : "";
    if (!peers.length) {
        list.appendChild(el("div", "contract-empty", "No peers yet: connect to another node's host:port"));
        return;
    }
    // Links we opened first, then the ones that dialled us.
    const ordered = peers.slice().sort(function (a, b) {
        if (a.direction !== b.direction) return a.direction === "outbound" ? -1 : 1;
        return a.address < b.address ? -1 : a.address > b.address ? 1 : 0;
    });
    ordered.forEach(function (p) {
        const row = el("div", "peer-row");
        const outbound = p.direction === "outbound";
        row.title = (outbound ? "We dialled " : "Dialled us from ") + p.address + "\n" + p.nodeId
            + (p.connectedAt ? "\nconnected " + new Date(p.connectedAt).toLocaleTimeString() : "");
        row.appendChild(el("span", "peer-dir " + p.direction, outbound ? "→" : "←"));
        row.appendChild(el("span", "peer-address", p.address));
        row.appendChild(el("span", "peer-id", shortHash((p.nodeId || "").replace(/^node-/, ""))));
        row.appendChild(el("span", "peer-state", p.state));
        list.appendChild(row);
    });
}

/** Connects the node to host:port (POST /api/network/peers, from localhost only). */
async function onPeerConnect(event) {
    event.preventDefault();
    const input = document.getElementById("peer-address");
    const button = document.getElementById("peer-connect");
    const address = input.value.trim();
    if (!/^[^\s:]+:\d{1,5}$/.test(address)) {
        toast("Enter host:port, e.g. localhost:8335", true);
        input.focus();
        return;
    }
    button.disabled = true;
    try {
        const peer = await postJson("/api/network/peers", { address: address });
        toast("Connected to " + peer.address + " (" + peer.nodeId + ")");
        input.value = "";
        refresh();
    } catch (e) {
        toast(e.message, true);
    } finally {
        button.disabled = false;
    }
}

// ===== explorer (Sprint 27b) =====
//
// Views inside the dashboard, each with its own link: #/blocks,
// #/block/<height or hash>, #/tx/<id>, #/address/<address>. The address bar
// is the state: the back button, a reload and a pasted link all land on the
// same view.

const BLOCK_PAGE = 20;
const HISTORY_PAGE = 25;

const explorer = {
    route: null,        // { view, arg }, or null for the dashboard
    blocks: [],         // block summaries loaded so far, newest first
    nextBefore: null,   // cursor for the next older page; null once genesis is loaded
    offset: 0,          // first row of the address history on screen
    seq: 0,             // drops the answer for a view the reader already left
};

/** "#/block/12" -> { view: "block", arg: "12" }; anything else is the dashboard. */
function parseRoute(hash) {
    const m = /^#\/(blocks|block|tx|address)(?:\/(.+))?$/.exec(hash || "");
    if (!m) return null;
    if (m[1] === "blocks") return { view: "blocks", arg: null };
    if (!m[2]) return null;
    let arg = m[2];
    try { arg = decodeURIComponent(arg); } catch (e) {}
    return { view: m[1], arg: arg };
}

function blockLink(indexOrHash) { return "#/block/" + encodeURIComponent(indexOrHash); }
function txLink(id) { return "#/tx/" + encodeURIComponent(id); }
function addressLink(address) { return "#/address/" + encodeURIComponent(address); }

/** An in-page link; its text is set with textContent like everything from the chain. */
function link(href, text, className) {
    const a = el("a", className || "x-link", text);
    a.href = href;
    return a;
}

/** An address as a link. COINBASE is where new coins come from, not an address. */
function addressNode(address, full) {
    if (!address) return el("span", "x-dim", "—");
    if (address === "COINBASE") return el("span", "x-tag", "coinbase");
    const a = link(addressLink(address), full ? address : shortHash(address));
    a.title = address;
    return a;
}

function when(ts) {
    const age = relTime(ts);
    return new Date(ts).toLocaleString() + " (" + (age === "now" ? "just now" : age + " ago") + ")";
}

function coins(x) { return formatAmount(x) + " BSC"; }

/** One "label: value" line. The value is text or a node. */
function kv(list, label, value) {
    const row = el("div", "x-kv");
    row.appendChild(el("div", "x-k", label));
    const v = el("div", "x-v");
    if (value instanceof Node) v.appendChild(value);
    else v.textContent = value;
    row.appendChild(v);
    list.appendChild(row);
    return v;
}

/** A table: headers are [text, class]; returns its node and the body to fill. */
function table(headers) {
    const wrap = el("div", "x-scroll");
    const t = el("table", "x-table");
    const head = el("tr");
    headers.forEach(function (h) { head.appendChild(el("th", h[1] || null, h[0])); });
    const thead = el("thead");
    thead.appendChild(head);
    t.appendChild(thead);
    const body = el("tbody");
    t.appendChild(body);
    wrap.appendChild(t);
    return { node: wrap, body: body };
}

function cell(row, content, className) {
    const td = el("td", className || null);
    if (content instanceof Node) td.appendChild(content);
    else td.textContent = content;
    row.appendChild(td);
    return td;
}

function panel(label) {
    const p = el("div", "panel x-panel");
    if (label) p.appendChild(el("div", "panel-label", label));
    return p;
}

function titleRow(text) {
    const row = el("div", "x-title");
    row.appendChild(el("h1", null, text));
    return row;
}

/** Shows the view the address bar names, or the dashboard. */
function onRoute() {
    const route = parseRoute(location.hash);
    explorer.route = route;
    document.getElementById("deck").hidden = !!route;
    document.getElementById("explorer").hidden = !route;
    if (!route) {
        document.title = "BlockSmith Dashboard";
        return;
    }
    closeOverlay();
    explorer.offset = 0;
    loadExplorer(true);
}

/**
 * Loads the current view. {@code fresh} is a navigation (show "Loading",
 * scroll to the top); otherwise it is the poll refreshing what is on screen.
 */
async function loadExplorer(fresh) {
    const route = explorer.route;
    if (!route) return;
    const token = ++explorer.seq;
    const body = document.getElementById("explorer-body");
    if (fresh) {
        body.replaceChildren(el("div", "x-empty", "Loading…"));
        document.querySelector(".main").scrollTop = 0;
    }
    let view;
    try {
        if (route.view === "blocks") view = await buildBlocksView(fresh);
        else if (route.view === "block") view = await buildBlockView(route.arg);
        else if (route.view === "tx") view = await buildTxView(route.arg);
        else view = await buildAddressView(route.arg);
    } catch (e) {
        if (!fresh) return;            // a failed background refresh keeps what is shown
        view = notFoundView(e.message);
    }
    if (token !== explorer.seq) return;
    body.replaceChildren(view);
}

function notFoundView(message) {
    document.title = "Not found · BlockSmith";
    const view = el("div", "x-view");
    const p = panel(null);
    p.appendChild(titleRow("Nothing here"));
    p.appendChild(el("div", "x-empty", message));
    p.appendChild(link("#/blocks", "See the latest blocks"));
    view.appendChild(p);
    return view;
}

/** Looks a string up on the chain and opens what it names. */
async function explorerSearch(query) {
    const q = (query || "").trim();
    if (!q) {
        toast("Type a block height, a block hash, a transaction id or an address", true);
        return;
    }
    try {
        const found = await fetchJson("/api/explorer/search?q=" + encodeURIComponent(q));
        if (found.type === "block") location.hash = blockLink(found.blockIndex);
        else if (found.type === "transaction") location.hash = txLink(found.transactionId);
        else location.hash = addressLink(found.address);
        document.getElementById("explorer-query").value = "";
    } catch (e) {
        toast(e.message, true);
    }
}

// ----- blocks -----

function statTile(label, value, unit) {
    const tile = el("div", "x-stat");
    tile.appendChild(el("div", "x-stat-label", label));
    const v = el("div", "x-stat-value", value);
    if (unit) v.appendChild(el("span", "x-stat-unit", " " + unit));
    tile.appendChild(v);
    return tile;
}

function blockRow(b) {
    const row = el("tr");
    cell(row, link(blockLink(b.index), "#" + b.index));
    const hash = link(blockLink(b.index), shortHash(b.hash));
    hash.title = b.hash;
    cell(row, hash);
    cell(row, relTime(b.timestamp)).title = new Date(b.timestamp).toLocaleString();
    cell(row, String(b.transactionCount), "num");
    cell(row, formatBytes(b.sizeBytes), "num opt");
    cell(row, formatAmount(b.totalFees), "num opt");
    cell(row, addressNode(b.miner), "opt");
    return row;
}

async function buildBlocksView(fresh) {
    const results = await Promise.all([
        fetchJson("/api/explorer/stats"),
        fetchJson("/api/explorer/blocks?limit=" + BLOCK_PAGE)
    ]);
    const stats = results[0], page = results[1];
    // A refresh keeps the older pages the reader already loaded, if they still join up.
    const lowest = page.blocks.length ? page.blocks[page.blocks.length - 1].index : 0;
    const older = fresh ? [] : explorer.blocks.filter(function (b) { return b.index < lowest; });
    if (older.length && older[0].index === lowest - 1) {
        explorer.blocks = page.blocks.concat(older);
    } else {
        explorer.blocks = page.blocks;
        explorer.nextBefore = page.nextBefore == null ? null : page.nextBefore;
    }
    document.title = "Blocks · BlockSmith";

    const view = el("div", "x-view");
    const strip = el("div", "x-stats");
    strip.id = "x-stats";
    strip.appendChild(statTile("Height", String(stats.height)));
    strip.appendChild(statTile("Transactions", String(stats.transactions)));
    strip.appendChild(statTile("Coins issued", formatAmount(stats.coinsIssued), "BSC"));
    strip.appendChild(statTile("Chain size", formatBytes(stats.chainBytes)));
    strip.appendChild(statTile("Pending", String(stats.pendingTransactions)));
    strip.appendChild(statTile("Contracts", stats.openContracts + " open", "of " + stats.contracts));
    view.appendChild(strip);

    const p = panel("Blocks");
    const t = table([["Height"], ["Hash"], ["Age"], ["Txs", "num"], ["Size", "num opt"], ["Fees", "num opt"], ["Mined by", "opt"]]);
    t.body.id = "x-blocks";
    explorer.blocks.forEach(function (b) { t.body.appendChild(blockRow(b)); });
    p.appendChild(t.node);
    const more = el("button", "x-more", "Load older blocks");
    more.type = "button";
    more.id = "x-older";
    more.hidden = explorer.nextBefore == null;
    more.addEventListener("click", function () { loadOlderBlocks(t.body, more); });
    p.appendChild(more);
    view.appendChild(p);
    return view;
}

/** The next page down: the blocks below the lowest one shown (a cursor, not a page number). */
async function loadOlderBlocks(body, button) {
    if (explorer.nextBefore == null) return;
    button.disabled = true;
    try {
        const page = await fetchJson("/api/explorer/blocks?limit=" + BLOCK_PAGE + "&before=" + explorer.nextBefore);
        explorer.blocks = explorer.blocks.concat(page.blocks);
        explorer.nextBefore = page.nextBefore == null ? null : page.nextBefore;
        page.blocks.forEach(function (b) { body.appendChild(blockRow(b)); });
        button.hidden = explorer.nextBefore == null;
    } catch (e) {
        toast(e.message, true);
    } finally {
        button.disabled = false;
    }
}

// ----- one block -----

async function buildBlockView(arg) {
    const byHeight = /^\d+$/.test(arg);
    const b = await fetchJson(byHeight ? "/api/blocks/" + arg : "/api/blocks/hash/" + encodeURIComponent(arg));
    document.title = "Block #" + b.index + " · BlockSmith";

    const view = el("div", "x-view");
    const p = panel(null);
    const title = titleRow("Block #" + b.index);
    const nav = el("div", "x-nav");
    if (b.index > 0) nav.appendChild(link(blockLink(b.index - 1), "← #" + (b.index - 1)));
    if (b.nextHash) nav.appendChild(link(blockLink(b.index + 1), "#" + (b.index + 1) + " →"));
    title.appendChild(nav);
    p.appendChild(title);

    const list = el("div", "x-list");
    kv(list, "Hash", b.hash);
    kv(list, "Confirmations", String(b.confirmations));
    kv(list, "Mined", when(b.timestamp));
    kv(list, "Mined by", b.miner ? addressNode(b.miner, true) : "nobody (the genesis block)");
    kv(list, "Coinbase paid", b.miner ? coins(b.reward) + (b.totalFees > 0 ? " (fees " + formatAmount(b.totalFees) + " included)" : "") : "—");
    kv(list, "Transactions", String(b.transactionCount));
    kv(list, "Size", formatBytes(b.sizeBytes) + " (" + b.sizeBytes + " bytes)");
    kv(list, "Nonce", String(b.nonce));
    kv(list, "Merkle root", b.merkleRoot || "—");
    kv(list, "Previous block", b.index > 0 ? link(blockLink(b.index - 1), b.previousHash) : "none: this is the first block");
    kv(list, "Next block", b.nextHash ? link(blockLink(b.index + 1), b.nextHash) : "none yet: this is the tip");
    p.appendChild(list);
    view.appendChild(p);

    const txs = panel("Transactions in this block");
    if (!b.transactions.length) {
        txs.appendChild(el("div", "x-empty", b.data ? "No transactions. Message: " + b.data : "No transactions"));
    } else {
        const t = table([["#", "opt"], ["Id"], ["From"], ["To"], ["Amount", "num"], ["Fee", "num opt"]]);
        t.body.id = "x-block-txs";
        b.transactions.forEach(function (tx, i) {
            const row = el("tr");
            cell(row, String(i), "opt");
            const id = link(txLink(tx.transactionId), shortHash(tx.transactionId));
            id.title = tx.transactionId;
            cell(row, id);
            cell(row, addressNode(tx.sender));
            cell(row, addressNode(tx.recipient));
            cell(row, formatAmount(tx.amount), "num");
            cell(row, formatAmount(tx.fee || 0), "num opt");
            t.body.appendChild(row);
        });
        txs.appendChild(t.node);
    }
    view.appendChild(txs);
    return view;
}

// ----- one transaction -----

async function buildTxView(id) {
    const tx = await fetchJson("/api/transactions/" + encodeURIComponent(id));
    document.title = "Transaction " + shortHash(tx.transactionId) + " · BlockSmith";
    const confirmed = tx.status === "confirmed";

    const view = el("div", "x-view");
    const p = panel(null);
    const title = titleRow("Transaction");
    title.appendChild(el("span", "x-pill " + (confirmed ? "ok" : "wait"), tx.status));
    p.appendChild(title);

    const list = el("div", "x-list");
    kv(list, "Id", tx.transactionId);
    if (confirmed) {
        kv(list, "Confirmations", String(tx.confirmations));
        const where = el("span");
        where.appendChild(link(blockLink(tx.blockIndex), "#" + tx.blockIndex));
        where.appendChild(document.createTextNode(" · position " + tx.position + " · " + tx.blockHash));
        kv(list, "Block", where);
        kv(list, "Mined", when(tx.blockTimestamp));
    } else {
        kv(list, "Confirmations", "0: waiting in the mempool for a block");
        if (tx.mempoolPosition != null) {
            kv(list, "Place in the mempool", tx.mempoolPosition + " of " + tx.mempoolSize
                + " by fee rate (a miner takes the best fee per byte first)");
        }
    }
    kv(list, "Created", when(tx.timestamp));
    kv(list, "From", addressNode(tx.sender, true));
    kv(list, "To", addressNode(tx.recipient, true));
    kv(list, "Amount", coins(tx.amount));
    kv(list, "Fee", coins(tx.fee || 0) + (tx.fee > 0 ? " (" + formatRate(tx.feeRate) + ")" : ""));
    kv(list, "Size", tx.sizeBytes + " bytes");
    if (tx.sequence != null) kv(list, tx.sender === "COINBASE" ? "Height in coinbase" : "Sequence", String(tx.sequence));
    kv(list, "Signature", tx.signature ? "signed by the sender's key" : (tx.sender === "COINBASE" ? "none: a coinbase has no sender" : "none"));
    if (tx.lockingScript) kv(list, "Locking script", tx.lockingScript);
    if (tx.unlockingScript) kv(list, "Unlocking script", tx.unlockingScript);
    p.appendChild(list);
    view.appendChild(p);
    return view;
}

// ----- one address -----

async function buildAddressView(address) {
    const a = await fetchJson("/api/explorer/address/" + encodeURIComponent(address)
        + "?limit=" + HISTORY_PAGE + "&offset=" + explorer.offset);
    document.title = "Address " + shortHash(a.address) + " · BlockSmith";
    const total = a.transactionCount + a.pendingCount;

    const view = el("div", "x-view");
    const p = panel(null);
    p.appendChild(titleRow("Address"));
    const list = el("div", "x-list");
    kv(list, "Address", a.address);
    kv(list, "Balance", coins(a.balance));
    kv(list, "Received", coins(a.totalReceived));
    kv(list, "Sent", coins(a.totalSent) + " (amounts and fees)");
    kv(list, "Transactions", a.transactionCount + " confirmed" + (a.pendingCount ? ", " + a.pendingCount + " pending" : ""));
    if (a.pendingOutgoing > 0) kv(list, "Leaving (pending)", coins(a.pendingOutgoing));
    p.appendChild(list);
    view.appendChild(p);

    // A contract's address (Sprint 27c): what the coins are locked behind.
    if (a.contract) {
        const c = a.contract;
        const cp = panel("Contract");
        cp.id = "x-contract";
        const cl = el("div", "x-list");
        const open = c.status === "OPEN";
        kv(cl, "Status", el("span", "x-pill " + (open ? "wait" : "ok"), open ? "open" : c.status.toLowerCase()));
        kv(cl, "Kind", contractType(c.lockingScript).label.toLowerCase());
        kv(cl, "Locked", coins(c.amount));
        kv(cl, "Locking script", c.lockingScript);
        kv(cl, "Funded by", addressNode(c.funder, true));
        kv(cl, "Deployed in", link(blockLink(c.creationHeight), "#" + c.creationHeight));
        kv(cl, "Claimed by", c.claimer ? addressNode(c.claimer, true) : "nobody yet");
        cp.appendChild(cl);
        view.appendChild(cp);
    }

    const history = panel("History");
    if (!total) {
        history.appendChild(el("div", "x-empty", "This address has not sent or received anything."));
    } else {
        // On a phone the direction column goes: the sign of the amount says it.
        const t = table([["Id"], ["Block"], ["Age", "opt"], ["", "opt"], ["With"], ["Amount", "num"], ["Fee", "num opt"]]);
        t.body.id = "x-history";
        a.transactions.forEach(function (tx) {
            const row = el("tr");
            const id = link(txLink(tx.transactionId), shortHash(tx.transactionId));
            id.title = tx.transactionId;
            cell(row, id);
            cell(row, tx.status === "confirmed" ? link(blockLink(tx.blockIndex), "#" + tx.blockIndex) : el("span", "x-pill wait", "pending"));
            cell(row, relTime(tx.blockTimestamp || tx.timestamp), "opt");
            const out = tx.sender === a.address;
            const self = out && tx.recipient === a.address;
            cell(row, el("span", "x-pill " + (out ? "out" : "in"), self ? "self" : out ? "out" : "in"), "opt");
            cell(row, addressNode(out ? tx.recipient : tx.sender));
            cell(row, (out && !self ? "−" : "+") + formatAmount(tx.amount), "num");
            cell(row, out ? formatAmount(tx.fee || 0) : "", "num opt");
            t.body.appendChild(row);
        });
        history.appendChild(t.node);

        const pager = el("div", "x-pager");
        const newer = el("button", null, "← Newer");
        newer.type = "button";
        newer.disabled = a.offset === 0;
        newer.addEventListener("click", function () { explorer.offset = Math.max(0, a.offset - HISTORY_PAGE); loadExplorer(true); });
        const olderBtn = el("button", null, "Older →");
        olderBtn.type = "button";
        olderBtn.disabled = a.offset + a.transactions.length >= total;
        olderBtn.addEventListener("click", function () { explorer.offset = a.offset + HISTORY_PAGE; loadExplorer(true); });
        pager.appendChild(newer);
        pager.appendChild(olderBtn);
        pager.appendChild(el("span", null, (a.offset + 1) + "–" + (a.offset + a.transactions.length) + " of " + total));
        if (total > HISTORY_PAGE) history.appendChild(pager);
    }
    view.appendChild(history);
    return view;
}

/** The balance card links to the active wallet's history. */
function renderBalanceLink() {
    const a = document.getElementById("balance-explore");
    a.hidden = !state.activeAddress;
    if (state.activeAddress) a.href = addressLink(state.activeAddress);
}

// ===== command palette =====

let paletteActive = 0;
let paletteQuery = "";

const COMMANDS = [
    { icon: "⛏", label: "Mine pending block", hint: "Sequence the mempool into a new block", run: function () { closeOverlay(); doMine(); } },
    { icon: "↗", label: "Send transaction", hint: "Transfer BSC to an address", run: function () { openDrawer("send"); } },
    { icon: "≡", label: "Mempool", hint: "Pending transactions by fee rate · bump a fee", run: function () { openDrawer("mempool"); } },
    { icon: "＋", label: "Create wallet", hint: "Generate a new key pair", run: function () { openDrawer("wallet"); } },
    { icon: "▤", label: "Deploy contract", hint: "Hashlock or multisig", run: function () { openDrawer("deploy"); } },
    { icon: "⪉", label: "Multisig wallet", hint: "Deploy an M-of-N contract from member keys", run: function () { openDrawer("deploy", { deployType: "multi" }); } },
    { icon: "✎", label: "Multisig member", hint: "Member keys · sign another member's claim", run: function () { openDrawer("member"); } },
    { icon: "⇄", label: "Connect to peer", hint: "Join another node by its host:port", run: function () { closeOverlay(); document.getElementById("peer-address").focus(); } },
    { icon: "⌕", label: "Explorer", hint: "Blocks, transactions and addresses · or type a height, hash or address here", run: function () { closeOverlay(); location.hash = "#/blocks"; } },
];

function filteredCommands() {
    const q = paletteQuery.trim().toLowerCase();
    if (!q) return COMMANDS;
    const typed = paletteQuery.trim();
    // Whatever is typed can also be looked up on the chain (Sprint 27b).
    return COMMANDS.filter(function (c) { return (c.label + " " + c.hint).toLowerCase().indexOf(q) !== -1; })
        .concat([{ icon: "⌕", label: "Search the chain", hint: typed, run: function () { closeOverlay(); explorerSearch(typed); } }]);
}

function openPalette() {
    paletteQuery = "";
    paletteActive = 0;
    overlay.replaceChildren();
    const back = el("div", "backdrop");
    back.addEventListener("click", closeOverlay);
    const wrap = el("div", "palette-wrap");
    wrap.addEventListener("click", function (e) { if (e.target === wrap) closeOverlay(); });

    const panel = el("div", "palette");
    const search = el("div", "palette-search");
    search.appendChild(el("span", "palette-icon", "⌘"));
    const input = el("input");
    input.type = "text";
    input.placeholder = "Search a command…";
    input.addEventListener("input", function () { paletteQuery = input.value; paletteActive = 0; renderCommands(); });
    search.appendChild(input);
    search.appendChild(el("span", "kbd", "ESC"));
    panel.appendChild(search);

    const listBox = el("div", "palette-list");
    listBox.id = "palette-list";
    panel.appendChild(listBox);
    wrap.appendChild(panel);
    overlay.appendChild(back);
    overlay.appendChild(wrap);
    renderCommands();
    setTimeout(function () { input.focus(); }, 30);
}

function renderCommands() {
    const listBox = document.getElementById("palette-list");
    if (!listBox) return;
    listBox.replaceChildren();
    const cmds = filteredCommands();
    if (paletteActive >= cmds.length) paletteActive = Math.max(0, cmds.length - 1);
    cmds.forEach(function (c, i) {
        const row = el("div", "cmd" + (i === paletteActive ? " active" : ""));
        row.appendChild(el("span", "cmd-icon", c.icon));
        const body = el("div");
        body.appendChild(el("span", "cmd-label", c.label));
        body.appendChild(el("span", "cmd-hint", c.hint));
        row.appendChild(body);
        row.addEventListener("mouseenter", function () { paletteActive = i; });
        row.addEventListener("click", c.run);
        listBox.appendChild(row);
    });
}

function isPaletteOpen() { return !!overlay.querySelector(".palette"); }

// ===== side drawers =====

function drawerShell(title, buildBody) {
    overlay.replaceChildren();
    const back = el("div", "backdrop");
    back.addEventListener("click", closeOverlay);
    const wrap = el("div", "drawer-wrap");
    wrap.addEventListener("click", function (e) { if (e.target === wrap) closeOverlay(); });
    const drawer = el("div", "drawer");
    const head = el("div", "drawer-head");
    head.appendChild(el("span", "drawer-title", title));
    const close = el("button", "icon-btn");
    close.type = "button";
    close.textContent = "✕";
    close.addEventListener("click", closeOverlay);
    head.appendChild(close);
    drawer.appendChild(head);
    buildBody(drawer);
    wrap.appendChild(drawer);
    overlay.appendChild(back);
    overlay.appendChild(wrap);
}

/** "1.2 KB" / "640 B" for block sizes (Sprint 18d). */
function formatBytes(n) {
    return n < 1024 ? n + " B" : (n / 1024).toFixed(1) + " KB";
}

/** Up to 4 decimals, no trailing zeros: 0.5, 0.125, 2. */
function formatAmount(x) {
    return String(Math.round(x * 10000) / 10000);
}

function field(labelText, placeholder, value) {
    const wrap = el("div", "field");
    wrap.appendChild(el("label", null, labelText));
    const input = el("input");
    input.type = "text";
    input.placeholder = placeholder || "";
    if (value) input.value = value;
    wrap.appendChild(input);
    return { wrap: wrap, input: input };
}

/** A multi-line field (pasted keys, requests, signatures). */
function textArea(labelText, placeholder) {
    const wrap = el("div", "field");
    wrap.appendChild(el("label", null, labelText));
    const input = el("textarea");
    input.rows = 3;
    input.spellcheck = false;
    input.placeholder = placeholder || "";
    wrap.appendChild(input);
    return { wrap: wrap, input: input };
}

/** A button that copies {@code text}; says so, or asks to copy by hand where the clipboard is off. */
function copyButton(label, text, className) {
    const btn = el("button", className || "seg-btn", label);
    btn.type = "button";
    btn.addEventListener("click", function () {
        function done(msg) {
            btn.textContent = msg;
            setTimeout(function () { btn.textContent = label; }, 1500);
        }
        if (!navigator.clipboard) return done("Select and copy it");
        navigator.clipboard.writeText(text).then(function () { done("Copied"); }, function () { done("Select and copy it"); });
    });
    return btn;
}

function passwordField(labelText, autocomplete) {
    const f = field(labelText, "");
    f.input.type = "password";
    f.input.autocomplete = autocomplete || "current-password";
    return f;
}

function errorBox() {
    const box = el("div", "form-error");
    box.style.display = "none";
    return {
        node: box,
        show: function (msg) { box.textContent = msg; box.style.display = "block"; },
        hide: function () { box.style.display = "none"; },
    };
}

function submitButton(label, onClick) {
    const btn = el("button", "drawer-submit", label);
    btn.type = "button";
    btn.addEventListener("click", onClick);
    return btn;
}

/** What each action does on a node: shown in the recorded demo, where it cannot be done. */
const DEMO_ACTIONS = {
    send: ["Send transaction", "On a node, this signs a transfer with your key, here in the browser, and submits it. It waits in the mempool until a block takes it."],
    wallet: ["Create wallet", "On a node, this makes a key pair in this browser and encrypts it under your passphrase. The node never sees the private key."],
    deploy: ["Deploy contract", "On a node, this locks coins behind a script: a hashlock (whoever knows a secret) or a multisig (m of n signatures)."],
    multisig: ["Multisig wallet", "On a node, this locks coins behind several public keys; a number of their owners must sign to move them."],
    member: ["Multisig member", "On a node, a member of a multisig signs a claim here and passes the signature to the others."],
};

function openDemoDrawer(type) {
    const action = DEMO_ACTIONS[type];
    drawerShell(action[0], function (drawer) {
        drawer.appendChild(el("p", "note", action[1]));
        drawer.appendChild(el("p", "note", DEMO.readOnly));
        const run = link(DEMO.runUrl, "Run it yourself →", "x-link");
        run.target = "_blank";
        run.rel = "noopener";
        drawer.appendChild(run);
    });
}

function openDrawer(type, opts) {
    if (DEMO && DEMO_ACTIONS[type]) return openDemoDrawer(type);
    if (type === "send") return openSendDrawer();
    if (type === "mempool") return openMempoolDrawer();
    if (type === "wallet") return openWalletDrawer();
    if (type === "member") return openMemberDrawer();
    if (type === "deploy" || type === "multisig") return openDeployDrawer((opts && opts.deployType) || (type === "multisig" ? "multi" : "hash"));
}

function openSendDrawer() {
    drawerShell("Send transaction", function (drawer) {
        if (!state.activeAddress) {
            drawer.appendChild(el("p", "note", "Create or select a wallet first — it becomes the sender."));
            return;
        }
        if (!hasKey(state.activeAddress)) {
            drawer.appendChild(el("p", "note", "This browser has no key for " + shortHash(state.activeAddress)
                + ", so it cannot sign for it. Create a wallet here, or import its key (Wallet panel)."));
            return;
        }
        const form = el("div", "drawer-form");
        const recip = field("Recipient address", "0x…");
        const amount = field("Amount (BSC)", "0.00");
        const hint = el("span", "field-hint", "Sender " + shortHash(state.activeAddress));
        amount.wrap.appendChild(hint);
        // Optional fee (Sprint 18d): a higher fee per byte gets mined sooner when blocks are full.
        const fee = field("Fee (BSC, optional)", "0");
        fee.wrap.appendChild(el("span", "field-hint", "Paid to the miner on top of the amount"));
        // Suggested fees (Sprint 19d): what gets mined within 1 / 3 / 6 blocks.
        const suggest = el("div", "segmented fee-suggest");
        fee.wrap.appendChild(suggest);
        fetchJson("/api/fees/estimate").then(function (est) {
            [["fast", "Next block"], ["normal", "~3 blocks"], ["slow", "~6 blocks"]].forEach(function (pair) {
                const e = est[pair[0]];
                const btn = el("button", "seg-btn");
                btn.type = "button";
                btn.appendChild(el("span", "fee-suggest-label", pair[1]));
                btn.appendChild(el("span", "fee-suggest-value", formatAmount(e.fee) + " BSC"));
                btn.addEventListener("click", function () {
                    fee.input.value = String(e.fee);
                    suggest.querySelectorAll(".seg-btn").forEach(function (b) { b.classList.remove("active"); });
                    btn.classList.add("active");
                });
                suggest.appendChild(btn);
            });
        }).catch(function () { suggest.remove(); });
        const err = errorBox();
        form.appendChild(recip.wrap);
        form.appendChild(amount.wrap);
        form.appendChild(fee.wrap);
        form.appendChild(err.node);
        form.appendChild(submitButton("Submit transaction", function () {
            const r = recip.input.value.trim();
            const a = Number(amount.input.value);
            const f = fee.input.value.trim() === "" ? 0 : Number(fee.input.value);
            if (!r) return err.show("Enter a recipient address.");
            if (!a || a <= 0) return err.show("Enter a valid amount.");
            if (!(f >= 0)) return err.show("The fee must be zero or more.");
            runDrawer(err, function () {
                // Prepare (node builds the exact text), sign here, submit.
                return postJson("/api/transactions/prepare", { sender: state.activeAddress, recipient: r, amount: a, fee: f })
                    .then(function (template) { return signAndSubmit(template, state.activeAddress); })
                    .then(function (tx) { return "Signed and submitted tx " + shortHash(tx.transactionId); });
            });
        }));
        drawer.appendChild(form);
        setTimeout(function () { recip.input.focus(); }, 30);
    });
}

/** Mempool drawer (Sprint 19d): pending txs in mining order, with a bump button. */
function openMempoolDrawer() {
    drawerShell("Mempool", function (drawer) {
        const body = el("div", "drawer-form");
        const summary = el("div", "mp-summary", "Loading…");
        const list = el("div", "mp-list");
        const err = errorBox();
        body.appendChild(summary);
        body.appendChild(err.node);
        body.appendChild(list);
        drawer.appendChild(body);

        fetchJson("/api/mempool").then(function (mp) {
            summary.textContent = mp.count + " pending · " + formatBytes(mp.bytes) + " of "
                + formatBytes(mp.maxMempoolBytes) + " · fees " + formatAmount(mp.totalFees) + " BSC"
                + (mp.mempoolMinFeeRate > 0 ? " · full: min " + formatRate(mp.mempoolMinFeeRate) : "");
            if (!mp.transactions.length) {
                list.appendChild(el("div", "feed-empty", "Nothing waiting. Send a transaction to see it here."));
                return;
            }
            mp.transactions.forEach(function (tx, i) {
                const row = el("div", "mp-row");
                const main = el("div", "mp-main");
                main.appendChild(el("div", "mp-title", "#" + (i + 1) + "  " + formatAmount(tx.amount) + " BSC → " + shortHash(tx.recipient)));
                main.appendChild(el("div", "mp-meta", "fee " + formatAmount(tx.fee) + " · " + formatRate(tx.feeRate)
                    + " · " + formatBytes(tx.sizeBytes) + " · from " + shortHash(tx.sender)));
                row.appendChild(main);
                if (typeof tx.minBumpFee === "number") {
                    const bump = el("button", "mp-bump", "Bump → " + formatAmount(tx.minBumpFee));
                    bump.type = "button";
                    const mine = hasKey(tx.sender);
                    bump.disabled = !mine;
                    bump.title = mine ? "Replace it with the same payment at the smallest higher fee allowed (signed here)"
                        : "Only the sender can bump it: this browser has no key for " + shortHash(tx.sender);
                    bump.addEventListener("click", function () {
                        err.hide();
                        postJson("/api/transactions/" + encodeURIComponent(tx.transactionId) + "/bump/prepare", { fee: tx.minBumpFee })
                            .then(function (template) { return signAndSubmit(template, tx.sender); })
                            .then(function (b) {
                                openMempoolDrawer();   // before the toast: redrawing clears the overlay
                                toast("Bumped to fee " + formatAmount(b.fee) + " · " + shortHash(b.transactionId));
                                refresh();
                            })
                            .catch(function (e) { err.show(e.message); });
                    });
                    row.appendChild(bump);
                }
                list.appendChild(row);
            });
        }).catch(function (e) { summary.textContent = ""; err.show(e.message); });
    });
}

/** A fee rate for people: BSC per kB (rates per byte are tiny numbers). */
function formatRate(perByte) {
    return formatAmount(perByte * 1000) + " BSC/kB";
}

function openWalletDrawer() {
    drawerShell("Wallet", function (drawer) {
        const form = el("div", "drawer-form");
        form.appendChild(el("p", "note", "Creates a key pair in this browser (Web Crypto, P-256). The private key never "
            + "leaves it: the node only ever receives signatures. It is stored encrypted under your passphrase (asked "
            + "the first time); export an encrypted backup to keep a copy."));
        const readout = el("div", "readout");
        readout.appendChild(el("div", "readout-label", "Address"));
        const value = el("div", "readout-value", state.activeAddress && hasKey(state.activeAddress) ? state.activeAddress : "—");
        readout.appendChild(value);
        const err = errorBox();
        form.appendChild(readout);
        form.appendChild(err.node);

        function activate(address, what) {
            value.textContent = address;
            setActiveAddress(address);
            pushFeed("dot-green", what, shortHash(address));
            toast(what + " · set as active");
            renderFeed();
            refresh();
        }
        form.appendChild(submitButton("Create wallet", function () {
            err.hide();
            createBrowserWallet().then(function (address) { activate(address, "Wallet created"); })
                .catch(function (e) { err.show(e.message); });
        }));

        const imp = field("Import a key (backup JSON)", "{\"blocksmith\": \"key-backup\", …}");
        const impPass = passwordField("Backup passphrase (encrypted backups)", "off");
        form.appendChild(imp.wrap);
        form.appendChild(impPass.wrap);
        const importBtn = el("button", "seg-btn", "Import key");
        importBtn.type = "button";
        importBtn.addEventListener("click", function () {
            err.hide();
            importKeys(imp.input.value.trim(), impPass.input.value).then(function (addresses) {
                activate(addresses[0], addresses.length === 1 ? "Key imported" : addresses.length + " keys imported");
            }).catch(function (e) { err.show(e.message); });
        });
        form.appendChild(importBtn);

        if (hasKey(state.activeAddress)) {
            const exportBtn = el("button", "seg-btn", "Export the active key (encrypted)");
            exportBtn.type = "button";
            exportBtn.addEventListener("click", function () {
                err.hide();
                (keyBackup(state.activeAddress) ? Promise.resolve() : ensureUnlocked()).then(function () {
                    const backup = keyBackup(state.activeAddress);
                    const out = el("div", "readout");
                    out.appendChild(el("div", "readout-label", "Encrypted with your passphrase: keep both, but not together"));
                    out.appendChild(el("div", "readout-value export-key", backup));
                    exportBtn.replaceWith(out);
                    out.after(copyButton("Copy the backup", backup));
                }).catch(function (e) { err.show(e.message); });
            });
            form.appendChild(exportBtn);
        }

        // Move funds to a new key (Sprint 25c).
        if (hasKey(state.activeAddress)) {
            const active = state.activeAddress;
            const key = loadKeys()[active];
            if (key.plaintextBefore && !key.retiredTo) {
                const warn = el("div", "readout ks-exposed");
                warn.appendChild(el("div", "readout-label", "This key was once stored in plain text"));
                warn.appendChild(el("p", "note", "Before Sprint 24 (or as a plaintext export) it sat unencrypted in a file, and "
                    + "the browser may still keep that copy. Treat it as exposed: move its funds to a new key."));
                form.appendChild(warn);
            }
            if (key.retiredTo) {
                form.appendChild(el("p", "note", "Retired: its funds were moved to " + shortHash(key.retiredTo)
                    + ". Anything sent here later can be moved again."));
            }
            const moveBtn = el("button", "seg-btn", "Move funds to a new key");
            moveBtn.type = "button";
            moveBtn.addEventListener("click", function () {
                err.hide();
                Promise.all([fetchJson("/api/wallet/" + encodeURIComponent(active)), fetchJson("/api/fees/estimate")])
                    .then(function (r) {
                        const fee = r[1].normal.fee;
                        const spendable = r[0].balance - (r[0].pendingOutgoing || 0);
                        const amount = Math.floor((spendable - fee) * 1e8) / 1e8;
                        if (!(amount > 0)) throw new Error("Nothing to move: " + formatAmount(spendable) + " BSC spendable, the fee is " + formatAmount(fee));
                        const box = el("div", "drawer-form mv-confirm");
                        const what = el("div", "readout");
                        what.appendChild(el("div", "readout-label", "A new key gets everything from " + shortHash(active)
                            + " (fee " + formatAmount(fee) + " BSC). The old key stays, marked retired."));
                        what.appendChild(el("div", "readout-value", formatAmount(amount) + " BSC"));
                        box.appendChild(what);
                        const go = submitButton("Move " + formatAmount(amount) + " BSC", function () {
                            err.hide();
                            go.disabled = true;
                            moveFundsToNewKey(active, fee).then(function (moved) {
                                activate(moved.address, "Funds moved to a new key");
                                pushFeed("dot-amber", "Key retired", shortHash(active) + " → " + shortHash(moved.address));
                                renderFeed();
                                box.replaceWith(el("p", "note", "Moved " + formatAmount(moved.amount) + " BSC to " + moved.address
                                    + " (mine to settle). The old key is kept, marked retired."));
                            }).catch(function (e) { go.disabled = false; err.show(e.message); });
                        });
                        box.appendChild(go);
                        moveBtn.replaceWith(box);
                    }).catch(function (e) { err.show(e.message); });
            });
            form.appendChild(moveBtn);
        }

        // The whole keystore (Sprint 24b): one encrypted backup, and the passphrase.
        if (loadStore()) {
            form.appendChild(el("div", "readout-label", "Keystore · " + Object.keys(loadStore().keys).length + " keys, encrypted"));
            const allBtn = el("button", "seg-btn", "Back up all keys (encrypted)");
            allBtn.type = "button";
            allBtn.addEventListener("click", function () {
                const backup = keystoreBackup();
                const out = el("div", "readout");
                out.appendChild(el("div", "readout-label", "Every key here, encrypted with your passphrase"));
                out.appendChild(el("div", "readout-value export-key ks-backup", backup));
                allBtn.replaceWith(out);
                out.after(copyButton("Copy the backup", backup));
            });
            form.appendChild(allBtn);

            const changeBtn = el("button", "seg-btn", "Change passphrase");
            changeBtn.type = "button";
            changeBtn.addEventListener("click", function () {
                const box = el("div", "drawer-form ks-change");
                const cur = passwordField("Current passphrase", "current-password");
                const next = passwordField("New passphrase", "new-password");
                const again = passwordField("New passphrase again", "new-password");
                const go = submitButton("Re-encrypt with the new passphrase", function () {
                    err.hide();
                    if (next.input.value !== again.input.value) return err.show("The two new passphrases differ.");
                    go.disabled = true;
                    changePassphrase(cur.input.value, next.input.value).then(function () {
                        openWalletDrawer();                  // before the toast: redrawing clears the overlay
                        toast("Passphrase changed · every key re-encrypted");
                    }).catch(function (e) { go.disabled = false; err.show(e.message); });
                });
                [cur.wrap, next.wrap, again.wrap, go].forEach(function (n) { box.appendChild(n); });
                changeBtn.replaceWith(box);
            });
            form.appendChild(changeBtn);
        }
        drawer.appendChild(form);
    });
}

function openDeployDrawer(deployType) {
    let type = deployType || "hash";
    let members = "local";               // multisig (Sprint 23b): "local" = 3 new keys here, "paste" = members' public keys
    drawerShell("Deploy contract", function (drawer) {
        if (!state.activeAddress) {
            drawer.appendChild(el("p", "note", "Create or select a wallet first — it funds the contract."));
            return;
        }
        const form = el("div", "drawer-form");
        const err = errorBox();

        const typeField = el("div", "field");
        typeField.appendChild(el("label", null, "Contract type"));
        const seg = el("div", "segmented");
        const hashBtn = el("button", "seg-btn", "Hashlock");
        const multiBtn = el("button", "seg-btn", "Multisig");
        hashBtn.type = "button"; multiBtn.type = "button";
        hashBtn.addEventListener("click", function () { type = "hash"; sync(); });
        multiBtn.addEventListener("click", function () { type = "multi"; sync(); });
        seg.appendChild(hashBtn); seg.appendChild(multiBtn);
        typeField.appendChild(seg);

        // Who the members are: keys made here, or other people's public keys.
        const multiBox = el("div", "drawer-form");
        const memberSeg = el("div", "segmented");
        const localBtn = el("button", "seg-btn", "3 new keys here (2-of-3)");
        const pasteBtn = el("button", "seg-btn", "Members' public keys");
        localBtn.type = "button"; pasteBtn.type = "button";
        localBtn.addEventListener("click", function () { members = "local"; sync(); });
        pasteBtn.addEventListener("click", function () { members = "paste"; sync(); });
        memberSeg.appendChild(localBtn); memberSeg.appendChild(pasteBtn);
        multiBox.appendChild(memberSeg);

        const pasteBox = el("div", "drawer-form");
        const keysField = textArea("Member public keys (hex, one per line)", "3059301306072a8648ce3d0201…");
        keysField.input.rows = 5;
        const addKey = el("button", "seg-btn", "+ New member key in this browser");
        addKey.type = "button";
        addKey.addEventListener("click", function () {
            err.hide();
            createBrowserWallet("multisig member key").then(function (address) {
                const current = keysField.input.value.trim();
                keysField.input.value = (current ? current + "\n" : "") + loadKeys()[address].publicKey;
            }).catch(function (e) { err.show(e.message); });
        });
        const threshold = field("Signatures needed (M)", "2", "2");
        pasteBox.appendChild(keysField.wrap);
        pasteBox.appendChild(addKey);
        pasteBox.appendChild(threshold.wrap);
        multiBox.appendChild(pasteBox);

        const amount = field("Locked amount (BSC)", "0.00");
        const note = el("p", "note");

        function sync() {
            hashBtn.classList.toggle("active", type === "hash");
            multiBtn.classList.toggle("active", type === "multi");
            localBtn.classList.toggle("active", members === "local");
            pasteBtn.classList.toggle("active", members === "paste");
            multiBox.style.display = type === "multi" ? "" : "none";
            pasteBox.style.display = members === "paste" ? "" : "none";
            note.textContent = type === "hash"
                ? "Hashlock: the node reveals a secret you use to claim."
                : members === "local"
                    ? "Three member keys are made in this browser and a 2-of-3 wallet locks the amount. A claim needs two "
                        + "member signatures; here you hold all three, which shows the mechanics but shares no control."
                    : "Each member makes a key on their own device (Multisig member) and sends you its PUBLIC key. "
                        + "The node gets public keys only; a claim needs M of the members' signatures.";
        }
        sync();

        form.appendChild(typeField);
        form.appendChild(multiBox);
        form.appendChild(amount.wrap);
        form.appendChild(note);
        form.appendChild(err.node);
        form.appendChild(submitButton("Deploy contract", function () {
            const a = Number(amount.input.value);
            if (!a || a <= 0) return err.show("Enter an amount to lock.");
            err.hide();
            if (type === "hash") return deployHashlock(a, err);
            if (members === "local") return deployMultisig(a, err, null, 2);
            const keys = keysField.input.value.split(/[\s,]+/).filter(Boolean);
            const m = Number(threshold.input.value);
            if (!keys.length) return err.show("Paste the members' public keys, one per line.");
            if (!(Number.isInteger(m) && m >= 1 && m <= keys.length)) return err.show("M must be between 1 and the number of members.");
            deployMultisig(a, err, keys, m);
        }));
        drawer.appendChild(form);
        setTimeout(function () { amount.input.focus(); }, 30);
    });
}

async function deployHashlock(amount, err) {
    try {
        const secret = randomHex(6);
        const hash = await sha256hex(secret);
        const contract = await postJson("/api/contracts/prepare", {
            funder: state.activeAddress,
            amount: amount,
            lockingScript: "SHA256 PUSH " + hash + " EQUAL"
        });
        await signAndSubmit(contract, state.activeAddress);          // the funder signs the deploy
        closeOverlay();
        toast("Hashlock deployed · secret: " + secret);
        pushFeed("dot-amber", "Hashlock secret", secret + " → " + shortHash(contract.contractId));
        renderFeed();
        refresh();
    } catch (e) { err.show(e.message); }
}

/**
 * Deploys a multisig (Sprint 23b). memberKeys null = make three member keys
 * here (2-of-3); otherwise the members' public keys. The node only ever
 * sees public keys: it turns them into the locking script.
 */
async function deployMultisig(amount, err, memberKeys, threshold) {
    const made = [];
    let submitted = false;
    try {
        let keys = memberKeys;
        if (!keys) {
            for (let i = 1; i <= 3; i++) made.push(await createBrowserWallet("multisig member " + i + " of 3"));
            keys = made.map(function (address) { return loadKeys()[address].publicKey; });
        }
        const wallet = await postJson("/api/multisig/create", { memberPublicKeys: keys, threshold: threshold });
        const contract = await postJson("/api/contracts/prepare", {
            funder: state.activeAddress,
            amount: amount,
            lockingScript: wallet.lockingScript
        });
        await signAndSubmit(contract, state.activeAddress);          // the funder signs the deploy
        submitted = true;

        const name = wallet.threshold + "-of-" + wallet.memberCount;
        let held = 0;
        wallet.memberPublicKeys.forEach(function (pk, i) {
            const local = keyForPublicKey(pk);
            if (!local) return;
            held++;
            if ((local.entry.label || "").indexOf(" · ") === -1) {   // not yet tied to a contract
                labelKey(local.address, "member " + (i + 1) + " of " + name + " · " + shortHash(contract.contractId));
            }
        });
        closeOverlay();
        toast("Multisig " + name + " deployed · " + shortHash(contract.contractId));
        pushFeed("dot-amber", "Multisig " + name, held + " member key" + (held === 1 ? "" : "s") + " here → " + shortHash(contract.contractId));
        renderFeed();
        refresh();
    } catch (e) {
        if (!submitted && made.length) removeKeys(made);    // keys for a wallet that never came to be
        err.show(e.message);
    }
}

function openClaimDrawer(contract) {
    const t = contractType(contract.lockingScript);
    if (t.kind === "multi") return openMultisigClaimDrawer(contract, t);
    drawerShell("Claim contract", function (drawer) {
        const form = el("div", "drawer-form");
        const readout = el("div", "readout");
        readout.appendChild(el("div", "readout-label", t.label + " · " + contract.amount + " BSC"));
        readout.appendChild(el("div", "readout-value", contract.contractId));
        form.appendChild(readout);

        const claimer = field("Claimer address", "0x…", state.activeAddress || "");
        form.appendChild(claimer.wrap);

        let secret = null;
        if (t.kind === "hash") {
            secret = field("Secret (preimage)", "the hashlock secret");
            form.appendChild(secret.wrap);
        }

        const err = errorBox();
        form.appendChild(err.node);
        form.appendChild(submitButton("Claim contract", function () {
            const who = claimer.input.value.trim();
            if (!who) return err.show("Enter a claimer address.");
            if (t.kind !== "hash") return err.show("This contract type can't be claimed from the dashboard.");
            const s = secret.input.value.trim();
            if (!s) return err.show("Enter the hashlock secret.");
            runDrawer(err, function () {
                return postJson("/api/contracts/" + encodeURIComponent(contract.contractId) + "/claim", {
                    claimer: who, unlockingScript: "PUSH " + s
                }).then(function () { return "Claim submitted (mine to settle)"; });
            });
        }));
        drawer.appendChild(form);
    });
}

/**
 * Multisig claim (Sprint 23b): prepare -> sign with the member keys held
 * here -> paste the other members' signatures -> submit when M are in.
 * The message names the claimer, so a new claimer starts over.
 */
function openMultisigClaimDrawer(contract, t) {
    drawerShell("Claim multisig", function (drawer) {
        const form = el("div", "drawer-form");
        const readout = el("div", "readout");
        readout.appendChild(el("div", "readout-label", t.label + " · " + contract.amount + " BSC"));
        readout.appendChild(el("div", "readout-value", contract.contractId));
        form.appendChild(readout);

        const claimer = field("Claimer address", "0x…", state.activeAddress || "");
        form.appendChild(claimer.wrap);
        const box = el("div", "drawer-form ms-claim");
        const err = errorBox();
        let claim = null;        // the prepared claim: {claimer, amount, message, threshold, memberPublicKeys}
        let sigs = {};           // member position -> {publicKey, signature}

        const button = submitButton("Prepare & sign", function () {
            if (!claim) return prepare();
            const list = Object.keys(sigs).map(function (i) { return sigs[i]; });
            if (list.length < claim.threshold) return err.show("Need " + claim.threshold + " member signatures, have " + list.length + ".");
            runDrawer(err, function () {
                return postJson("/api/multisig/claim", { contractId: contract.contractId, claimer: claim.claimer, signatures: list })
                    .then(function () { return "Multisig claim submitted (mine to settle)"; });
            });
        });
        claimer.input.addEventListener("input", function () {
            if (!claim) return;
            claim = null; sigs = {};
            box.replaceChildren();
            button.textContent = "Prepare & sign";
        });

        async function prepare() {
            const who = claimer.input.value.trim();
            if (!who) return err.show("Enter a claimer address.");
            err.hide();
            try {
                const p = await postJson("/api/multisig/claim/prepare", { contractId: contract.contractId, claimer: who });
                const fresh = {};
                let signed = 0;
                for (let i = 0; i < p.memberPublicKeys.length && signed < p.threshold; i++) {
                    const local = keyForPublicKey(p.memberPublicKeys[i]);
                    if (!local) continue;
                    fresh[i] = await signText(local.address, p.message);      // signed here, key stays here
                    signed++;
                }
                if (claimer.input.value.trim() !== who) return;               // changed meanwhile: stale
                claim = p; sigs = fresh;
                render();
            } catch (e) { err.show(e.message); }
        }

        function addPasted(text) {
            let s;
            try { s = JSON.parse(text); } catch (e) { throw new Error("Paste the signature JSON the member copied"); }
            if (!s || !s.publicKey || !s.signature) throw new Error("Expected {publicKey, signature}");
            if (s.contractId && s.contractId !== contract.contractId) throw new Error("That signature is for another contract");
            if (s.claimer && s.claimer !== claim.claimer) {
                throw new Error("That signature pays " + shortHash(s.claimer) + ", not " + shortHash(claim.claimer));
            }
            const i = claim.memberPublicKeys.indexOf(String(s.publicKey).trim().toLowerCase());
            if (i < 0) throw new Error("Key " + keyTail(s.publicKey) + " is not a member of this multisig");
            if (sigs[i]) throw new Error("Member " + (i + 1) + " has signed already");
            sigs[i] = { publicKey: claim.memberPublicKeys[i], signature: String(s.signature).trim() };
        }

        function render() {
            box.replaceChildren();
            const have = Object.keys(sigs).length;
            const head = el("div", "readout");
            head.appendChild(el("div", "readout-label", "Pays " + claim.amount + " BSC to " + shortHash(claim.claimer)
                + " · needs " + claim.threshold + " of " + claim.memberPublicKeys.length));
            head.appendChild(el("div", "readout-value ms-count", have + " of " + claim.threshold + " signatures"));
            box.appendChild(head);

            const list = el("div", "mp-list");
            claim.memberPublicKeys.forEach(function (pk, i) {
                const here = !!keyForPublicKey(pk);
                const row = el("div", "mp-row");
                const main = el("div", "mp-main");
                main.appendChild(el("div", "mp-title", "Member " + (i + 1)));
                main.appendChild(el("div", "mp-meta", "key " + keyTail(pk)));
                row.appendChild(main);
                const status = sigs[i] ? (here ? "signed here" : "signature pasted") : (here ? "key here" : "key elsewhere");
                row.appendChild(el("span", "ms-status" + (sigs[i] ? " ok" : ""), status));
                list.appendChild(row);
            });
            box.appendChild(list);

            if (have < claim.threshold) {
                box.appendChild(el("p", "note", "Send this request to the other members: they open Multisig member in their own "
                    + "browser, check what it pays, sign, and send you the signature to paste below."));
                const request = JSON.stringify({ contractId: contract.contractId, claimer: claim.claimer, message: claim.message });
                const rq = el("div", "readout");
                rq.appendChild(el("div", "readout-label", "Claim request"));
                rq.appendChild(el("div", "readout-value export-key ms-request", request));
                box.appendChild(rq);
                box.appendChild(copyButton("Copy the request", request));
                const paste = textArea("A member's signature", "{\"publicKey\": …, \"signature\": …}");
                box.appendChild(paste.wrap);
                const add = el("button", "seg-btn", "Add signature");
                add.type = "button";
                add.addEventListener("click", function () {
                    try { addPasted(paste.input.value.trim()); err.hide(); render(); } catch (e) { err.show(e.message); }
                });
                box.appendChild(add);
            }
            button.textContent = have >= claim.threshold ? "Submit claim" : "Submit claim (" + have + " of " + claim.threshold + ")";
        }

        form.appendChild(box);
        form.appendChild(err.node);
        form.appendChild(button);
        drawer.appendChild(form);
    });
}

/**
 * Multisig member (Sprint 23b): what a member does on their own device.
 * Make a member key and hand out its PUBLIC key; sign a claim request
 * after asking our node what it pays (a member never signs a hash blind).
 */
function openMemberDrawer() {
    drawerShell("Multisig member", function (drawer) {
        const form = el("div", "drawer-form");
        const err = errorBox();
        form.appendChild(el("p", "note", "A member key locks a shared multisig. Give its public key to whoever deploys the "
            + "wallet. When a claim needs you, paste the request, check what it pays, and send back your signature."));

        const keys = loadKeys();
        const addresses = Object.keys(keys);
        form.appendChild(el("div", "readout-label", "Keys in this browser"));
        const list = el("div", "mp-list");
        if (!addresses.length) list.appendChild(el("div", "feed-empty", "No keys here yet"));
        addresses.forEach(function (address) {
            const entry = keys[address];
            const row = el("div", "mp-row");
            const main = el("div", "mp-main");
            main.appendChild(el("div", "mp-title", entry.label || "wallet " + shortHash(address)));
            main.appendChild(el("div", "mp-meta", "public key " + keyTail(entry.publicKey)));
            row.appendChild(main);
            row.appendChild(copyButton("Copy public key", entry.publicKey, "mp-bump"));
            list.appendChild(row);
        });
        form.appendChild(list);
        const newKey = el("button", "seg-btn", "New member key");
        newKey.type = "button";
        newKey.addEventListener("click", function () {
            createBrowserWallet("multisig member key").then(function () {
                openMemberDrawer();                       // before the toast: redrawing clears the overlay
                toast("Member key created · copy its public key for the deployer");
            }).catch(function (e) { err.show(e.message); });
        });
        form.appendChild(newKey);

        const req = textArea("Claim request (from the claimer)", "{\"contractId\": …, \"claimer\": …, \"message\": …}");
        form.appendChild(req.wrap);
        const check = el("button", "seg-btn", "Check the request");
        check.type = "button";
        const out = el("div", "drawer-form ms-sign");
        check.addEventListener("click", function () {
            err.hide();
            out.replaceChildren();
            checkRequest(req.input.value.trim()).catch(function (e) { err.show(e.message); });
        });
        form.appendChild(check);
        form.appendChild(err.node);
        form.appendChild(out);
        drawer.appendChild(form);

        async function checkRequest(text) {
            let r;
            try { r = JSON.parse(text); } catch (e) { throw new Error("Paste the claim request JSON"); }
            if (!r || !r.contractId || !r.claimer || !r.message) throw new Error("Expected {contractId, claimer, message}");
            // Ask our own node what this claim is; sign only if the message is exactly that.
            const p = await postJson("/api/multisig/claim/prepare", { contractId: r.contractId, claimer: r.claimer });
            if (p.message !== r.message) throw new Error("The message does not match this claim on our node: do not sign it");
            const what = el("div", "readout");
            what.appendChild(el("div", "readout-label", "You would let " + p.threshold + " of " + p.memberPublicKeys.length
                + " members pay " + p.amount + " BSC from contract " + shortHash(p.contractId) + " to"));
            what.appendChild(el("div", "readout-value", p.claimer));
            out.appendChild(what);
            let mine = 0;
            p.memberPublicKeys.forEach(function (pk, i) {
                const local = keyForPublicKey(pk);
                if (!local) return;
                mine++;
                const sign = el("button", "drawer-submit", "Sign as member " + (i + 1));
                sign.type = "button";
                sign.addEventListener("click", function () {
                    err.hide();
                    signText(local.address, p.message).then(function (s) {
                        const blob = JSON.stringify({ contractId: p.contractId, claimer: p.claimer, publicKey: s.publicKey, signature: s.signature });
                        const res = el("div", "readout");
                        res.appendChild(el("div", "readout-label", "Member " + (i + 1) + "'s signature: send it to the claimer"));
                        res.appendChild(el("div", "readout-value export-key ms-signature", blob));
                        sign.replaceWith(res);
                        res.after(copyButton("Copy the signature", blob));
                    }).catch(function (e) { err.show(e.message); });
                });
                out.appendChild(sign);
            });
            if (!mine) throw new Error("This browser holds none of that multisig's member keys");
        }
    });
}

/** Runs a drawer submit: on success toast + close + refresh; on error inline. */
function runDrawer(err, work) {
    err.hide();
    work().then(function (message) {
        closeOverlay();          // first: closing clears the overlay, toast included
        toast(message);
        refresh();
    }).catch(function (e) { err.show(e.message); });
}

// ===== actions =====

function doMine() {
    if (!state.activeAddress) { toast("Create or select a wallet to mine to", true); return; }
    postJson("/api/mine", { minerAddress: state.activeAddress })
        .then(function (block) { toast("Block #" + block.index + " mined · +50 BSC"); refresh(); })
        .catch(function (e) { toast(e.message, true); });
}

function setActiveAddress(address) {
    state.activeAddress = address || null;
    try {
        if (address) localStorage.setItem("bs_active_address", address);
        else localStorage.removeItem("bs_active_address");
    } catch (e) {}
}

function closeOverlay() { overlay.replaceChildren(); }

// ===== onboarding tour =====

const TOUR = [
    { sel: "palette", title: "Command palette", body: "Run any action from one place — press ⌘K (Ctrl+K) or click this bar, then type and pick." },
    { sel: "chain", title: "The chain", body: "A live view of the block chain. “Mine block” sequences the pending transactions into a new block." },
    { sel: "feed", title: "Activity feed", body: "Events as they happen: blocks mined, transactions submitted, contracts deployed, and peers joining." },
    { sel: "actions", title: "Quick actions", body: "Send a transaction, create a wallet (its key stays in this browser), or deploy a contract — each opens a side panel; anything you spend is signed here." },
    { sel: "side", title: "Balance & contracts", body: "Your active-wallet balance and the open contracts (multisig / hashlock). Click a contract to claim it: a multisig needs its members' signatures." },
    { sel: "explore", title: "Explorer", body: "Every block, transaction and address can be opened: click a block card, or go in here and search by height, hash, transaction id or address. Each view has its own link." },
    { sel: "peers", title: "Peers", body: "The nodes this one is linked to: → we dialled them, ← they dialled us. Type another node's host:port to join it; the node also finds more peers by itself." },
];

const tour = { active: false, step: 0, remember: true, root: null, refs: null };

function tourOptedOut() {
    try { return localStorage.getItem("bs_tour_optout") === "1"; } catch (e) { return false; }
}

function startTour() {
    if (tour.active) return;
    // The tour points at the dashboard; leave an explorer view first (Sprint 27c).
    if (explorer.route) { location.hash = "#/"; onRoute(); }
    closeOverlay();            // a palette/drawer would sit under the dim; clear it
    tour.active = true;
    if (DEMO) DEMO.hold("tour", true);     // the replay waits while the tour points at the page
    tour.step = 0;
    buildTourDom();
    window.addEventListener("resize", positionTour);
    window.addEventListener("scroll", positionTour, true);
    positionTour();
}

function endTour() {
    if (!tour.active) return;
    tour.active = false;
    if (DEMO) DEMO.hold("tour", false);
    try {
        if (tour.remember) localStorage.setItem("bs_tour_optout", "1");
        else localStorage.removeItem("bs_tour_optout");
    } catch (e) {}
    window.removeEventListener("resize", positionTour);
    window.removeEventListener("scroll", positionTour, true);
    if (tour.root) { tour.root.remove(); tour.root = null; tour.refs = null; }
}

function tourNext() {
    if (tour.step >= TOUR.length - 1) endTour();
    else { tour.step++; positionTour(); }
}

function toggleRemember() {
    tour.remember = !tour.remember;
    updateTourRemember();
}

function updateTourRemember() {
    if (!tour.refs) return;
    tour.refs.remember.classList.toggle("on", tour.remember);
    tour.refs.check.textContent = tour.remember ? "✓" : "";
}

function buildTourDom() {
    const root = el("div");
    const block = el("div", "tour-block");
    const spot = el("div", "tour-spot");
    const bubble = el("div", "tour-bubble");
    const tail = el("div", "tour-tail");
    const body = el("div", "tour-body");

    const top = el("div", "tour-top");
    const count = el("span", "tour-count");
    const heading = el("span", "tour-heading");
    top.appendChild(count);
    top.appendChild(heading);

    const text = el("div", "tour-text");

    const foot = el("div", "tour-foot");
    const remember = el("div", "tour-remember");
    const check = el("span", "tour-check");
    remember.appendChild(check);
    remember.appendChild(el("span", null, "Remember my choice"));
    remember.addEventListener("click", toggleRemember);
    const skip = el("button", "tour-skip", "Skip");
    skip.type = "button";
    skip.addEventListener("click", endTour);
    const next = el("button", "tour-next", "Next");
    next.type = "button";
    next.addEventListener("click", tourNext);
    foot.appendChild(remember);
    foot.appendChild(skip);
    foot.appendChild(next);

    body.appendChild(top);
    body.appendChild(text);
    body.appendChild(foot);
    bubble.appendChild(tail);
    bubble.appendChild(body);
    root.appendChild(block);
    root.appendChild(spot);
    root.appendChild(bubble);
    document.body.appendChild(root);

    tour.root = root;
    tour.refs = { spot: spot, bubble: bubble, tail: tail, count: count, heading: heading, text: text, next: next, remember: remember, check: check };
    updateTourRemember();
}

/** Places the spotlight and bubble over the current step's target. */
function positionTour() {
    if (!tour.active || !tour.refs) return;
    const stepDef = TOUR[tour.step];
    const r = tour.refs;
    r.count.textContent = (tour.step + 1) + " / " + TOUR.length;
    r.heading.textContent = stepDef.title;
    r.text.textContent = stepDef.body;
    r.next.textContent = tour.step >= TOUR.length - 1 ? "Done" : "Next";

    const target = document.querySelector('[data-tour="' + stepDef.sel + '"]');
    if (!target) return;
    const rect = target.getBoundingClientRect(), pad = 8;
    const sx = rect.left - pad, sy = rect.top - pad, sw = rect.width + pad * 2, sh = rect.height + pad * 2;
    r.spot.style.left = sx + "px";
    r.spot.style.top = sy + "px";
    r.spot.style.width = sw + "px";
    r.spot.style.height = sh + "px";

    const bw = 322, vh = window.innerHeight, vw = window.innerWidth, bubbleH = 176;
    const below = sy + sh + 16 + bubbleH < vh;
    const bt = below ? sy + sh + 14 : Math.max(16, sy - 14 - bubbleH);
    const bl = Math.min(Math.max(sx + sw / 2 - bw / 2, 16), vw - bw - 16);
    r.bubble.style.left = bl + "px";
    r.bubble.style.top = bt + "px";

    const tailLeft = Math.min(Math.max(sx + sw / 2 - bl - 7, 20), bw - 34);
    r.tail.style.left = tailLeft + "px";
    if (below) {
        r.tail.style.top = "-7px";
        r.tail.style.bottom = "";
        r.tail.style.transform = "rotate(45deg)";
    } else {
        r.tail.style.bottom = "-7px";
        r.tail.style.top = "";
        r.tail.style.transform = "rotate(225deg)";
    }
}

// ===== poll loop =====

async function refresh() {
    let status, peers, page, contracts;
    try {
        const results = await Promise.all([
            fetchJson("/api/network/status"),
            fetchJson("/api/network/peers"),
            fetchJson("/api/explorer/blocks?limit=6"),
            fetchJson("/api/contracts")
        ]);
        status = results[0]; peers = results[1]; page = results[2]; contracts = results[3];
    } catch (e) {
        state.reachable = false;
        renderSync(null, false);
        return;
    }

    state.reachable = true;
    const tipIndex = page.height;
    const tipHash = page.blocks.length ? page.blocks[0].hash : "";

    // Feed derivation from the previous snapshot.
    const contractStatus = {};
    contracts.forEach(function (c) { contractStatus[c.contractId] = c.status; });
    const snapshot = {
        nodeId: status.nodeId,
        tipIndex: tipIndex, tipHash: tipHash,
        pending: status.pendingTransactions,
        peers: status.connectedPeers,
        reorgs: status.reorgs || 0, lastReorg: status.lastReorg || null,
        contracts: contracts, contractStatus: contractStatus,
    };
    // What an open explorer view shows changes with the tip (confirmations,
    // new blocks) and with the mempool (a pending transaction appears).
    const moved = !state.prev || state.prev.tipHash !== tipHash || state.prev.pending !== snapshot.pending;
    deriveFeed(snapshot);
    state.prev = snapshot;

    // Active-wallet balance (best-effort).
    let balanceInfo = null;
    if (state.activeAddress) {
        try { balanceInfo = await fetchJson("/api/wallet/" + encodeURIComponent(state.activeAddress)); } catch (e) {}
    }

    renderSync(status, true);
    renderHero(status, page);
    renderFeed();
    renderBalance(balanceInfo);
    renderContracts(contracts);
    renderPeers(peers);
    renderBalanceLink();
    if (moved && explorer.route) loadExplorer(false);
}

// ===== wiring =====

function initTheme() {
    let theme;
    try { theme = localStorage.getItem("bs_theme"); } catch (e) {}
    if (!theme) {
        const prefersLight = window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches;
        theme = prefersLight ? "light" : "dark";
    }
    applyTheme(theme);
}

function wire() {
    document.getElementById("palette-trigger").addEventListener("click", openPalette);
    document.getElementById("theme-toggle").addEventListener("click", toggleTheme);
    document.getElementById("help-btn").addEventListener("click", startTour);
    document.getElementById("lock-btn").addEventListener("click", onLockButton);
    // Any click or key press while unlocked restarts the 15-minute idle timer.
    document.addEventListener("pointerdown", touchVault, true);
    document.addEventListener("keydown", touchVault, true);
    document.getElementById("mine-btn").addEventListener("click", doMine);
    document.getElementById("peer-form").addEventListener("submit", onPeerConnect);
    document.getElementById("explorer-search").addEventListener("submit", function (e) {
        e.preventDefault();
        explorerSearch(document.getElementById("explorer-query").value);
    });
    window.addEventListener("hashchange", onRoute);
    // Click the balance address to track any existing address (multi-address node).
    document.getElementById("balance-address").addEventListener("click", function () {
        const input = window.prompt("Track an address in the balance card:", state.activeAddress || "");
        if (input === null) return;
        setActiveAddress(input.trim() || null);
        refresh();
    });
    document.querySelectorAll(".action-btn").forEach(function (btn) {
        btn.addEventListener("click", function () { openDrawer(btn.getAttribute("data-action")); });
    });

    document.addEventListener("keydown", function (e) {
        if (tour.active) return;   // the tour owns keyboard input while it's up
        if ((e.metaKey || e.ctrlKey) && (e.key === "k" || e.key === "K")) {
            e.preventDefault();
            if (isPaletteOpen()) closeOverlay();
            else openPalette();
            return;
        }
        if (e.key === "Escape") { closeOverlay(); return; }
        if (isPaletteOpen()) {
            const cmds = filteredCommands();
            if (e.key === "ArrowDown") { e.preventDefault(); paletteActive = Math.min(cmds.length - 1, paletteActive + 1); renderCommands(); }
            else if (e.key === "ArrowUp") { e.preventDefault(); paletteActive = Math.max(0, paletteActive - 1); renderCommands(); }
            else if (e.key === "Enter") { e.preventDefault(); if (cmds[paletteActive]) cmds[paletteActive].run(); }
        }
    });
}

// ===== boot =====

try { state.activeAddress = localStorage.getItem("bs_active_address") || null; } catch (e) {}
if (DEMO) {
    // The recording's wallet, whatever this browser tracked before. The player
    // asks for a refresh when a frame comes up and for a clean feed on replay.
    state.activeAddress = DEMO.activeAddress;
    DEMO.attach({
        refresh: refresh,
        restart: function () { state.prev = null; state.feed = []; refresh(); }
    });
}
initTheme();
bg.start();
wire();
renderLock();
if (keystoreState() === "legacy") {
    toast("Your keys are stored in plain text: click ⚠ Encrypt keys to protect them with a passphrase", true);
}
onRoute();   // a link opened directly lands on its view
refresh();
setInterval(refresh, REFRESH_MS);

// First-run onboarding tour, unless the visitor opted out previously.
if (!tourOptedOut()) setTimeout(function () { if (!tour.active) startTour(); }, 700);
