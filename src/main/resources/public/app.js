/*
 * BlockSmith dashboard - explorer view (Sprint 13b).
 *
 * Vanilla JS, no build step. Polls the REST API and renders the chain and
 * network state. Values are written with textContent (never innerHTML) so
 * data from the node can never inject markup.
 */

const REFRESH_MS = 4000;

/**
 * Fetches JSON from the node's API. On a non-2xx response, throws an Error
 * carrying the API's error envelope message when present, so action handlers
 * can surface exactly what the node reported.
 */
async function fetchJson(path, options) {
    const res = await fetch(path, options);
    const data = await res.json().catch(function () { return null; });
    if (!res.ok) {
        const message = data && data.error ? data.error : "HTTP " + res.status;
        throw new Error(message);
    }
    return data;
}

/** POSTs a JSON body and returns the parsed response (throws on API error). */
function postJson(path, body) {
    return fetchJson(path, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {})
    });
}

/** Small DOM helper: element with optional class and text. */
function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

/** Renders a labelled stat tile. */
function statTile(label, value) {
    const tile = el("div", "stat");
    tile.appendChild(el("span", "stat-value", String(value)));
    tile.appendChild(el("span", "stat-label", label));
    return tile;
}

function renderNetwork(status, peers) {
    const stats = document.getElementById("network-stats");
    stats.replaceChildren(
        statTile("Chain length", status.chainLength),
        statTile("Pending tx", status.pendingTransactions),
        statTile("Peers", status.connectedPeers)
    );

    const peersBox = document.getElementById("peers");
    peersBox.replaceChildren();
    const heading = el("p", "peers-heading", "Node " + status.nodeId
        + " · P2P " + status.p2pPort + " · API " + status.apiPort);
    peersBox.appendChild(heading);

    if (peers.length === 0) {
        peersBox.appendChild(el("p", "muted", "No connected peers"));
        return;
    }
    peers.forEach(function (p) {
        peersBox.appendChild(el("div", "peer", p.address + "  (" + p.state + ")"));
    });
}

/** Short hash for display: first 10 + last 6 hex chars. */
function shortHash(hash) {
    if (!hash || hash.length <= 20) return hash;
    return hash.slice(0, 10) + "…" + hash.slice(-6);
}

function renderBlocks(blocks) {
    const list = document.getElementById("block-list");
    list.replaceChildren();

    // Newest first; the tip is highlighted.
    const tipIndex = blocks.length - 1;
    for (let i = tipIndex; i >= 0; i--) {
        const block = blocks[i];
        const card = el("div", i === tipIndex ? "block latest" : "block");

        const head = el("div", "block-head");
        head.appendChild(el("span", "block-index", "#" + block.index));
        const txCount = Array.isArray(block.transactions) ? block.transactions.length : 0;
        head.appendChild(el("span", "block-txs", txCount + " tx"));
        card.appendChild(head);

        card.appendChild(el("div", "block-hash", "hash " + shortHash(block.hash)));
        card.appendChild(el("div", "block-prev", "prev " + shortHash(block.previousHash)));
        card.appendChild(el("div", "block-nonce", "nonce " + block.nonce));
        list.appendChild(card);
    }
}

function setStatus(text, ok) {
    const bar = document.getElementById("refresh-status");
    bar.textContent = text;
    bar.classList.toggle("error", !ok);
}

// ===== Actions (Sprint 13c) =====

/** Writes an action's outcome into its result line, styled ok/error. */
function showResult(id, text, ok) {
    const line = document.getElementById(id);
    line.textContent = text;
    line.classList.toggle("error", !ok);
    line.classList.toggle("ok", ok);
}

/**
 * Runs an action, surfacing success or the API error message, and refreshes
 * the explorer afterwards so any resulting state change is visible at once.
 */
async function runAction(resultId, work) {
    try {
        const message = await work();
        showResult(resultId, message, true);
        refresh();
    } catch (e) {
        showResult(resultId, e.message, false);
    }
}

function wireActions() {
    document.getElementById("create-wallet").addEventListener("click", function () {
        runAction("wallet-result", async function () {
            const wallet = await postJson("/api/wallet/create");
            const input = document.getElementById("balance-address");
            if (!input.value) input.value = wallet.address;
            return "Created wallet " + wallet.address;
        });
    });

    document.getElementById("check-balance").addEventListener("click", function () {
        runAction("wallet-result", async function () {
            const address = document.getElementById("balance-address").value.trim();
            if (!address) throw new Error("Enter an address to look up");
            const w = await fetchJson("/api/wallet/" + encodeURIComponent(address));
            return "Balance " + w.balance + " · pending out " + w.pendingOutgoing;
        });
    });

    document.getElementById("send-tx").addEventListener("click", function () {
        runAction("tx-result", async function () {
            const tx = await postJson("/api/transactions", {
                sender: document.getElementById("tx-sender").value.trim(),
                recipient: document.getElementById("tx-recipient").value.trim(),
                amount: Number(document.getElementById("tx-amount").value)
            });
            return "Submitted tx " + shortHash(tx.transactionId);
        });
    });

    document.getElementById("mine-block").addEventListener("click", function () {
        runAction("mine-result", async function () {
            const address = document.getElementById("mine-address").value.trim();
            const block = await postJson("/api/mine", { minerAddress: address });
            return "Mined block #" + block.index + " (" + shortHash(block.hash) + ")";
        });
    });
}

async function refresh() {
    try {
        const [status, peers, blocks] = await Promise.all([
            fetchJson("/api/network/status"),
            fetchJson("/api/network/peers"),
            fetchJson("/api/blocks")
        ]);
        renderNetwork(status, peers);
        renderBlocks(blocks);
        setStatus("Updated · refreshing every " + (REFRESH_MS / 1000) + "s", true);
    } catch (e) {
        setStatus("Cannot reach the node API: " + e.message, false);
    }
}

wireActions();
refresh();
setInterval(refresh, REFRESH_MS);
