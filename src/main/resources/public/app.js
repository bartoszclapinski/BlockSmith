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

// ===== small helpers =====

async function fetchJson(path, options) {
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
    const secs = Math.max(0, Math.round((Date.now() - ts) / 1000));
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
    state.feed.unshift({ dot: dot, title: title, meta: meta, ts: Date.now() });
    state.feed = state.feed.slice(0, 8);
}

function deriveFeed(next) {
    const prev = state.prev;
    if (!prev) {
        // First successful poll: seed a single entry, no backfill spam.
        pushFeed("dot-green", "Dashboard connected", "node " + next.nodeId);
        return;
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
        text.textContent = "synced · node-" + status.nodeId + " · " + status.connectedPeers + " peers";
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

function renderHero(status, blocks) {
    const tipIndex = blocks.length ? blocks[blocks.length - 1].index : 0;
    document.getElementById("hero-meta").textContent =
        "height " + tipIndex + " · difficulty " + status.difficulty + " · " + status.pendingTransactions + " pending";

    const row = document.getElementById("chain-row");
    row.replaceChildren();

    // Newest first, up to 6 cards.
    const shown = blocks.slice(-6).reverse();
    shown.forEach(function (block, i) {
        if (i > 0) row.appendChild(el("div", "conn"));
        const isTip = i === 0;
        const card = el("div", "block " + (isTip ? "block-tip" : "block-older"));
        const top = el("div", "block-top");
        top.appendChild(el("span", "block-num", "#" + block.index));
        if (isTip) top.appendChild(el("span", "tip-badge", "TIP"));
        card.appendChild(top);
        card.appendChild(el("div", "block-hash", shortHash(block.hash)));
        const txCount = Array.isArray(block.transactions) ? block.transactions.length : 0;
        card.appendChild(el("div", "block-meta", txCount + " tx · nonce " + block.nonce));
        row.appendChild(card);
    });

    // Trailing "genesis" cap only when older blocks exist beyond the window.
    const oldestShown = shown.length ? shown[shown.length - 1].index : 0;
    if (oldestShown > 0) {
        row.appendChild(el("div", "conn"));
        row.appendChild(el("div", "genesis", "genesis ···"));
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
        addr.textContent = state.activeAddress + (pending ? "  (−" + pending + " pending)" : "");
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

// ===== command palette =====

let paletteActive = 0;
let paletteQuery = "";

const COMMANDS = [
    { icon: "⛏", label: "Mine pending block", hint: "Sequence the mempool into a new block", run: function () { closeOverlay(); doMine(); } },
    { icon: "↗", label: "Send transaction", hint: "Transfer BSC to an address", run: function () { openDrawer("send"); } },
    { icon: "＋", label: "Create wallet", hint: "Generate a new key pair", run: function () { openDrawer("wallet"); } },
    { icon: "▤", label: "Deploy contract", hint: "Hashlock or multisig", run: function () { openDrawer("deploy"); } },
    { icon: "⪉", label: "Multisig wallet", hint: "Deploy a 2-of-3 contract", run: function () { openDrawer("deploy", { deployType: "multi" }); } },
];

function filteredCommands() {
    const q = paletteQuery.trim().toLowerCase();
    if (!q) return COMMANDS;
    return COMMANDS.filter(function (c) { return (c.label + " " + c.hint).toLowerCase().indexOf(q) !== -1; });
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

function openDrawer(type, opts) {
    if (type === "send") return openSendDrawer();
    if (type === "wallet") return openWalletDrawer();
    if (type === "deploy" || type === "multisig") return openDeployDrawer((opts && opts.deployType) || (type === "multisig" ? "multi" : "hash"));
}

function openSendDrawer() {
    drawerShell("Send transaction", function (drawer) {
        if (!state.activeAddress) {
            drawer.appendChild(el("p", "note", "Create or select a wallet first — it becomes the sender."));
            return;
        }
        const form = el("div", "drawer-form");
        const recip = field("Recipient address", "0x…");
        const amount = field("Amount (BSC)", "0.00");
        const hint = el("span", "field-hint", "Sender " + shortHash(state.activeAddress));
        amount.wrap.appendChild(hint);
        const err = errorBox();
        form.appendChild(recip.wrap);
        form.appendChild(amount.wrap);
        form.appendChild(err.node);
        form.appendChild(submitButton("Submit transaction", function () {
            const r = recip.input.value.trim();
            const a = Number(amount.input.value);
            if (!r) return err.show("Enter a recipient address.");
            if (!a || a <= 0) return err.show("Enter a valid amount.");
            runDrawer(err, function () {
                return postJson("/api/transactions", { sender: state.activeAddress, recipient: r, amount: a })
                    .then(function (tx) { return "Submitted tx " + shortHash(tx.transactionId); });
            });
        }));
        drawer.appendChild(form);
        setTimeout(function () { recip.input.focus(); }, 30);
    });
}

function openWalletDrawer() {
    drawerShell("Create wallet", function (drawer) {
        const form = el("div", "drawer-form");
        form.appendChild(el("p", "note", "Generates a new key pair on the node and sets it as your active wallet. The private key stays on the node (never sent over the API)."));
        const readout = el("div", "readout");
        readout.appendChild(el("div", "readout-label", "New address"));
        const value = el("div", "readout-value", "—");
        readout.appendChild(value);
        const err = errorBox();
        form.appendChild(readout);
        form.appendChild(err.node);
        form.appendChild(submitButton("Create wallet", function () {
            err.hide();
            postJson("/api/wallet/create").then(function (wallet) {
                value.textContent = wallet.address;
                setActiveAddress(wallet.address);
                pushFeed("dot-green", "Wallet created", shortHash(wallet.address));
                toast("Wallet created · set as active");
                renderFeed();
                setTimeout(closeOverlay, 900);
            }).catch(function (e) { err.show(e.message); });
        }));
        drawer.appendChild(form);
    });
}

function openDeployDrawer(deployType) {
    let type = deployType || "hash";
    drawerShell("Deploy contract", function (drawer) {
        if (!state.activeAddress) {
            drawer.appendChild(el("p", "note", "Create or select a wallet first — it funds the contract."));
            return;
        }
        const form = el("div", "drawer-form");

        const typeField = el("div", "field");
        typeField.appendChild(el("label", null, "Contract type"));
        const seg = el("div", "segmented");
        const hashBtn = el("button", "seg-btn", "Hashlock");
        const multiBtn = el("button", "seg-btn", "Multisig 2-of-3");
        hashBtn.type = "button"; multiBtn.type = "button";
        function syncSeg() {
            hashBtn.classList.toggle("active", type === "hash");
            multiBtn.classList.toggle("active", type === "multi");
        }
        hashBtn.addEventListener("click", function () { type = "hash"; syncSeg(); });
        multiBtn.addEventListener("click", function () { type = "multi"; syncSeg(); });
        syncSeg();
        seg.appendChild(hashBtn); seg.appendChild(multiBtn);
        typeField.appendChild(seg);

        const amount = field("Locked amount (BSC)", "0.00");
        const note = el("p", "note", "Hashlock: the node reveals a secret you use to claim. Multisig: a 2-of-3 wallet is created and the node signs claims for you.");
        const err = errorBox();

        form.appendChild(typeField);
        form.appendChild(amount.wrap);
        form.appendChild(note);
        form.appendChild(err.node);
        form.appendChild(submitButton("Deploy contract", function () {
            const a = Number(amount.input.value);
            if (!a || a <= 0) return err.show("Enter an amount to lock.");
            err.hide();
            if (type === "multi") deployMultisig(a, err);
            else deployHashlock(a, err);
        }));
        drawer.appendChild(form);
        setTimeout(function () { amount.input.focus(); }, 30);
    });
}

async function deployHashlock(amount, err) {
    try {
        const secret = randomHex(6);
        const hash = await sha256hex(secret);
        const contract = await postJson("/api/contracts", {
            funder: state.activeAddress,
            amount: amount,
            lockingScript: "SHA256 PUSH " + hash + " EQUAL"
        });
        closeOverlay();
        toast("Hashlock deployed · secret: " + secret);
        pushFeed("dot-amber", "Hashlock secret", secret + " → " + shortHash(contract.contractId));
        renderFeed();
        refresh();
    } catch (e) { err.show(e.message); }
}

async function deployMultisig(amount, err) {
    try {
        const wallet = await postJson("/api/multisig/create", { members: 3, threshold: 2 });
        const contract = await postJson("/api/contracts", {
            funder: state.activeAddress,
            amount: amount,
            lockingScript: wallet.lockingScript
        });
        closeOverlay();
        toast("Multisig 2-of-3 deployed · " + shortHash(contract.contractId));
        refresh();
    } catch (e) { err.show(e.message); }
}

function openClaimDrawer(contract) {
    const t = contractType(contract.lockingScript);
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
        } else if (t.kind === "multi") {
            form.appendChild(el("p", "note", "The node signs with the member keys it holds for this wallet (educational convenience)."));
        }

        const err = errorBox();
        form.appendChild(err.node);
        form.appendChild(submitButton("Claim contract", function () {
            const who = claimer.input.value.trim();
            if (!who) return err.show("Enter a claimer address.");
            if (t.kind === "hash") {
                const s = secret.input.value.trim();
                if (!s) return err.show("Enter the hashlock secret.");
                runDrawer(err, function () {
                    return postJson("/api/contracts/" + encodeURIComponent(contract.contractId) + "/claim", {
                        claimer: who, unlockingScript: "PUSH " + s
                    }).then(function () { return "Claim submitted (mine to settle)"; });
                });
            } else if (t.kind === "multi") {
                runDrawer(err, function () {
                    return postJson("/api/multisig/claim", { contractId: contract.contractId, claimer: who })
                        .then(function () { return "Multisig claim submitted (mine to settle)"; });
                });
            } else {
                err.show("This contract type can't be claimed from the dashboard.");
            }
        }));
        drawer.appendChild(form);
    });
}

/** Runs a drawer submit: on success toast + close + refresh; on error inline. */
function runDrawer(err, work) {
    err.hide();
    work().then(function (message) {
        toast(message);
        closeOverlay();
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
    { sel: "actions", title: "Quick actions", body: "Send a transaction, create a wallet, or deploy a contract — each opens a side panel." },
    { sel: "side", title: "Balance & contracts", body: "Your active-wallet balance and the open contracts (2-of-3 multisig / hashlock). Click a contract to claim it." },
];

const tour = { active: false, step: 0, remember: true, root: null, refs: null };

function tourOptedOut() {
    try { return localStorage.getItem("bs_tour_optout") === "1"; } catch (e) { return false; }
}

function startTour() {
    if (tour.active) return;
    closeOverlay();            // a palette/drawer would sit under the dim; clear it
    tour.active = true;
    tour.step = 0;
    buildTourDom();
    window.addEventListener("resize", positionTour);
    window.addEventListener("scroll", positionTour, true);
    positionTour();
}

function endTour() {
    if (!tour.active) return;
    tour.active = false;
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
    let status, peers, blocks, contracts;
    try {
        const results = await Promise.all([
            fetchJson("/api/network/status"),
            fetchJson("/api/network/peers"),
            fetchJson("/api/blocks"),
            fetchJson("/api/contracts")
        ]);
        status = results[0]; peers = results[1]; blocks = results[2]; contracts = results[3];
    } catch (e) {
        state.reachable = false;
        renderSync(null, false);
        return;
    }

    state.reachable = true;
    const tipIndex = blocks.length ? blocks[blocks.length - 1].index : 0;
    const tipHash = blocks.length ? blocks[blocks.length - 1].hash : "";

    // Feed derivation from the previous snapshot.
    const contractStatus = {};
    contracts.forEach(function (c) { contractStatus[c.contractId] = c.status; });
    const snapshot = {
        nodeId: status.nodeId,
        tipIndex: tipIndex, tipHash: tipHash,
        pending: status.pendingTransactions,
        peers: status.connectedPeers,
        contracts: contracts, contractStatus: contractStatus,
    };
    deriveFeed(snapshot);
    state.prev = snapshot;

    // Active-wallet balance (best-effort).
    let balanceInfo = null;
    if (state.activeAddress) {
        try { balanceInfo = await fetchJson("/api/wallet/" + encodeURIComponent(state.activeAddress)); } catch (e) {}
    }

    renderSync(status, true);
    renderHero(status, blocks);
    renderFeed();
    renderBalance(balanceInfo);
    renderContracts(contracts);
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
    document.getElementById("mine-btn").addEventListener("click", doMine);
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
initTheme();
bg.start();
wire();
refresh();
setInterval(refresh, REFRESH_MS);

// First-run onboarding tour, unless the visitor opted out previously.
if (!tourOptedOut()) setTimeout(function () { if (!tour.active) startTour(); }, 700);
