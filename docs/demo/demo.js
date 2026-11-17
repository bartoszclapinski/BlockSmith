/*
 * BlockSmith recorded demo: the player (Sprint 28b).
 *
 * Loaded before app.js on the demo page only (docs/demo, GitHub Pages). There
 * is no node behind that page. Instead, recording.js holds what a real node
 * answered during a recorded session: a list of frames, each with the answer
 * to every path the dashboard can ask at that moment. This file answers the
 * dashboard's fetchJson from the current frame and moves through the frames.
 *
 * Nothing about the chain is computed here: no balances, no confirmations,
 * no search. A path that was not recorded gets the node's own recorded "not
 * found"; anything that would change the chain is refused, because a
 * recording cannot be changed.
 */
(function () {
    "use strict";

    const RUN_URL = "https://github.com/bartoszclapinski/BlockSmith#run-a-node-with-the-web-dashboard";
    const READ_ONLY = "A recording cannot be changed. Run BlockSmith to mine, send and deploy.";
    const NOT_RECORDED = "Not in this recording";
    const FORMAT = 1;

    const rec = window.BLOCKSMITH_RECORDING;
    const usable = !!rec && rec.format === FORMAT && Array.isArray(rec.frames) && rec.frames.length > 0;
    const frames = usable ? rec.frames : [];
    const last = frames.length - 1;

    // A link to an explorer view (#/block/12 ...) opens the end of the
    // recording, paused: the block it names may not exist in the first frame.
    const deepLink = /^#\/(blocks|block|tx|address)\b/.test(location.hash);

    let frame = deepLink ? last : 0;
    let playing = usable && !deepLink;
    let shownAt = Date.now();          // when the current frame came up
    let timer = null;
    let app = null;                    // { refresh, restart }, handed over by app.js
    const holds = {};                  // reasons the replay waits (the tour)
    const ui = {};

    /** A recorded value with every {"$": n} replaced by pool[n]: a fresh copy each time. */
    function whole(v) {
        if (Array.isArray(v)) return v.map(whole);
        if (v && typeof v === "object") {
            const keys = Object.keys(v);
            if (keys.length === 1 && keys[0] === "$") return whole(rec.pool[v.$]);
            const out = {};
            keys.forEach(function (k) { out[k] = whole(v[k]); });
            return out;
        }
        return v;
    }

    /** The node trims a search, takes "#12" for a height and a hash in any case; so the same spellings are tried. */
    function spellings(key) {
        const prefix = "/api/explorer/search?q=";
        if (key.indexOf(prefix) !== 0) return [key];
        const q = key.slice(prefix.length).trim();
        return [prefix + q, prefix + q.replace(/^#/, ""), prefix + q.toLowerCase()];
    }

    /** What the node said for something it does not have, by the longest matching start of the path. */
    function miss(key) {
        let best = null;
        Object.keys(rec.misses || {}).forEach(function (prefix) {
            if (key.indexOf(prefix) === 0 && (!best || prefix.length > best.length)) best = prefix;
        });
        if (!best) return NOT_RECORDED;
        const m = rec.misses[best];
        if (!m.body || !m.body.error) return "HTTP " + m.status;
        // The node repeats what was asked for ("No block at index 999999"): put this visitor's words there.
        return m.body.error.split(m.probe).join(key.slice(best.length).trim());
    }

    /** fetchJson, answered from the current frame: resolves with the JSON or rejects like a failed request. */
    function answer(path, options) {
        if (!usable) return Promise.reject(new Error("The recording did not load"));
        if (options && options.method && options.method !== "GET") return Promise.reject(new Error(READ_ONLY));
        let key;
        try { key = decodeURIComponent(path); } catch (e) { key = path; }
        const answers = frames[frame].answers;
        const tries = spellings(key);
        for (let i = 0; i < tries.length; i++) {
            if (Object.prototype.hasOwnProperty.call(answers, tries[i])) return Promise.resolve(whole(answers[tries[i]]));
        }
        return Promise.reject(new Error(miss(key)));
    }

    /** The time on the recorded node's clock: the frame's own time plus how long it has been on screen. */
    function now() {
        return usable ? frames[frame].at + (Date.now() - shownAt) : Date.now();
    }

    /** Longer captions stay longer: 3.5 s plus reading time, at most 10 s. */
    function stepMs(f) {
        return Math.min(10000, 3500 + 35 * (f.title.length + f.detail.length));
    }

    function held() { return Object.keys(holds).length > 0; }

    function schedule() {
        clearTimeout(timer);
        timer = null;
        if (!playing || held() || frame >= last) return;
        timer = setTimeout(function () { show(frame + 1); }, stepMs(frames[frame]));
    }

    function show(index) {
        frame = Math.max(0, Math.min(last, index));
        shownAt = Date.now();
        if (frame >= last) playing = false;
        render();
        if (app) app.refresh();
        schedule();
    }

    function toggle() {
        if (frame >= last) { restart(); return; }
        playing = !playing;
        render();
        schedule();
    }

    function next() {
        if (frame < last) show(frame + 1);
    }

    function restart() {
        frame = 0;
        shownAt = Date.now();
        playing = true;
        render();
        if (app) app.restart();
        schedule();
    }

    /** The replay waits while {@code reason} holds (the tour is pointing at the page). */
    function hold(reason, on) {
        if (on) holds[reason] = true;
        else delete holds[reason];
        render();
        schedule();
    }

    function attach(hooks) {
        app = hooks;
        schedule();
    }

    // ===== the bar =====

    function node(tag, className, text) {
        const n = document.createElement(tag);
        if (className) n.className = className;
        if (text !== undefined) n.textContent = text;
        return n;
    }

    function button(className, label, onClick) {
        const b = node("button", "demo-btn " + className);
        b.type = "button";
        b.addEventListener("click", onClick);
        b.setAttribute("aria-label", label);
        b.title = label;
        return b;
    }

    function build() {
        const bar = node("div", "demo-bar");
        bar.id = "demo-bar";
        bar.setAttribute("role", "region");
        bar.setAttribute("aria-label", "Recorded demo");

        const badge = node("span", "demo-badge");
        badge.appendChild(node("span", "demo-rec"));
        badge.appendChild(node("span", null, "Recorded demo"));
        bar.appendChild(badge);

        const text = node("div", "demo-text");
        ui.title = node("span", "demo-title");
        ui.detail = node("span", "demo-detail");
        text.appendChild(ui.title);
        text.appendChild(ui.detail);
        text.setAttribute("aria-live", "polite");
        bar.appendChild(text);

        const controls = node("div", "demo-controls");
        ui.play = button("demo-play", "Pause", toggle);
        ui.next = button("demo-next", "Next step", next);
        ui.next.textContent = "⏭";
        ui.restart = button("demo-restart", "Start again", restart);
        ui.restart.textContent = "⟲";
        ui.count = node("span", "demo-count mono");
        controls.appendChild(ui.play);
        controls.appendChild(ui.next);
        controls.appendChild(ui.restart);
        controls.appendChild(ui.count);
        bar.appendChild(controls);

        const run = node("a", "demo-run", "Run it yourself →");
        run.href = RUN_URL;
        run.target = "_blank";
        run.rel = "noopener";
        bar.appendChild(run);

        ui.progress = node("div", "demo-progress");
        bar.appendChild(ui.progress);

        const topbar = document.querySelector(".topbar");
        topbar.parentNode.insertBefore(bar, topbar.nextSibling);
        document.documentElement.classList.add("demo");
    }

    function render() {
        if (!ui.title) return;
        if (!usable) {
            ui.title.textContent = "The recording did not load";
            ui.detail.textContent = "recording.js is missing or has another format.";
            ui.play.disabled = ui.next.disabled = ui.restart.disabled = true;
            return;
        }
        const f = frames[frame];
        const ended = frame >= last;
        ui.title.textContent = f.title;
        ui.detail.textContent = f.detail;
        ui.count.textContent = (frame + 1) + " / " + frames.length;
        ui.play.textContent = ended ? "⟲" : (playing ? "⏸" : "▶");
        const label = ended ? "Replay from the start" : (playing ? "Pause" : "Play");
        ui.play.setAttribute("aria-label", label);
        ui.play.title = label;
        ui.next.disabled = ended;
        ui.restart.hidden = ended;       // the play button already says "replay" there
        ui.progress.style.width = (100 * (frame + 1) / frames.length) + "%";
        const date = new Date(rec.recordedAt);
        ui.count.title = "Recorded " + date.toLocaleDateString() + " from a real node";
    }

    window.BlockSmithDemo = {
        answer: answer,
        now: now,
        hold: hold,
        attach: attach,
        readOnly: READ_ONLY,
        runUrl: RUN_URL,
        activeAddress: usable ? rec.activeAddress : null
    };

    build();
    render();
})();
