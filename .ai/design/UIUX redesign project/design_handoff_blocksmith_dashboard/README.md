# Handoff: BlockSmith — Node Dashboard (Overview screen)

## Overview
BlockSmith is a blockchain-node control panel. This handoff covers the **redesigned Overview screen** — a single-screen "command deck" that replaces the old long, vertically-scrolling page. From one view the operator can: watch the chain grow, run node actions (mine / send / create wallet / deploy contract) via a command palette or side panels, follow a live activity feed, and see balance + open contracts. A first-run onboarding tour explains the layout.

The design ships in **two color themes** — both are the same layout, only the palette differs:
- **Dark** ("Clay dark") — `BlockSmith App.dc.html`
- **Light** ("Clay") — `BlockSmith App Light.dc.html`

## About the Design Files
The files in this bundle are **design references authored in HTML** — working prototypes showing the intended look, layout, and behavior. They are **not production code to paste in**. The `.dc.html` files use a small internal component runtime (`support.js`) purely so the prototype could be built quickly; **do not port `support.js`**.

Your task: **recreate these designs in the target codebase's existing environment** (React, Vue, Svelte, etc.), using its established patterns, component library, and state management. If no front-end environment exists yet, pick the most appropriate framework for the project (React + a CSS solution is a safe default) and implement there. Treat the HTML as the source of truth for *appearance and behavior*, not for structure.

You can open either `.dc.html` file directly in a browser to interact with the prototype.

## Fidelity
**High-fidelity.** Colors, typography, spacing, radii, and interactions are final. Recreate pixel-accurately using the codebase's own primitives. Exact hex values and measurements are listed under **Design Tokens**.

## Layout (single screen)

Full-viewport, three stacked regions in a column:

1. **Top bar** — fixed height **58px**. Solid card background, 1px bottom border.
   - Left: logo mark (26×26 rounded-7px accent square with `⚒` glyph) + wordmark "BlockSmith" (700, 16px).
   - Center-left: **command-palette trigger** — a pill (max-width 440px, flex:1) with `⌘` icon, muted placeholder "Run a command — mine, send, deploy…", and a `⌘K` key hint chip on the right. Clicking it opens the palette.
   - Right: **sync status** (`● synced · node-7a3f · 4 peers`, mono font, green dot with slow pulse) + a **`?` help button** (30×30) that restarts the tour.

2. **Main** — scrollable, but content is capped and centered: an inner wrapper of **max-width 1560px, `margin:auto`, padding 24px**, column layout, 18px gap. This is the key fix for ultra-wide/UHD screens — content never stretches edge-to-edge; it centers with side margins and does not sprawl vertically.
   - **Chain hero card** (full width, radius 16px, padding 18/20). Header row: "The chain" (700,15px) + meta (`height 1284 · difficulty 4 · 3 pending`, mono, muted) + **Mine block** button (accent, right-aligned). Below: a **horizontal chain of block cards** connected by short 26×2px connector bars:
     - **Tip block** (first): 176px wide, accent border, tinted bg, accent block number, `TIP` badge.
     - **Older blocks**: 160px wide, normal border/bg.
     - Each block card shows `#<height>` (mono 15–16px), short hash `0000a8b2…7e75` (mono 11px), and `<n> tx · nonce <n>` (mono 10px, muted).
     - Trailing **dashed "genesis ···"** card (120px) closes the row. The row scrolls horizontally if it overflows.
   - **Bottom grid** — 3 columns `1.4fr 1fr 1fr`, gap 16px, **fixed height 420px** (cards do NOT flex-grow vertically — this prevents the empty-space sprawl; long lists scroll inside their card):
     - **Activity feed** (col 1): uppercase mono label "ACTIVITY FEED"; rows of `● <title> <meta> <time>` with a colored status dot (accent = block, green = tx/wallet, amber = peer), 1px row dividers, internal scroll.
     - **Actions** (col 2): uppercase mono label "ACTIONS"; four full-width outline buttons — "↗ Send transaction", "＋ Create wallet", "▤ Deploy contract", "⧉ Multisig wallet". Hover = accent border + tint bg.
     - **Right column** (col 3): stacked — **Balance card** ("Balance", big 24px number `155.0` + "BSC", mono address) and **Open contracts card** (rows of `<amount> BSC` + type badge `2-OF-3`/`HASHLOCK` + mono id, internal scroll).

3. **Footer** — fixed height **40px**, solid card bg, 1px top border. Inner content capped to 1560px, mono 11px muted: `BlockSmith Node v0.4.2 · P2P 8335 · API 7070` on the left; right-aligned `● chain healthy · uptime 4d 12h` (green dot).

**Background animation** (behind everything): a full-viewport `<canvas>` (z-index 0; all regions sit at z-index 1). Small drifting squares ("blocks") connected by faint lines when near — a subtle blockchain-graph motif in the accent color. Details under **Interactions**.

## Interactions & Behavior

### Command palette (⌘K)
- Opens on `⌘K` / `Ctrl+K`, on click of the trigger pill, or on a command's selection.
- Centered modal (560px) over a dimmed, blurred backdrop, dropping in (`translateY(8px)→0`, opacity, ~160ms).
- Search input (autofocused) filters the command list by label + hint substring.
- Command rows: 30×30 icon tile + label (600,13.5px) + hint (11.5px muted). Hover = tint bg.
- Commands: **Mine pending block**, **Send transaction**, **Create wallet**, **Deploy contract**, **Multisig wallet**. Selecting one either runs it (mine) or opens the matching side panel.
- `Esc` closes.

### Side drawer (Send / Create wallet / Deploy)
- Right-aligned drawer (420px) over a dimmed backdrop, sliding in (`translateX(24px)→0`, ~200ms).
- Header: title + `✕` close button. Fields use dark/light inputs with accent focus ring.
- **Send**: recipient address + amount; validation — recipient required (min length), amount > 0, amount ≤ balance; error shown in a red inline box. On submit: pushes a tx to the mempool, decrements balance, adds a feed entry, toast "Transaction added to mempool", closes.
- **Create wallet**: shows a generated address; on confirm adds a feed entry + toast "Wallet created".
- **Deploy contract**: segmented type toggle (Hashlock / Multisig 2-of-3) + locked amount; validation amount > 0; on submit prepends a contract to the list + feed entry + toast.

### Mine block
- If mempool empty → toast "Mempool empty — nothing to mine".
- Otherwise: creates a new tip block (height+1, random hash + nonce, tx count = mempool size), prepends it to the chain, clears the mempool (pending → 0), adds mining reward (**+50 BSC**) to balance, prepends a feed entry, and shows toast "Block #N mined · +50 BSC". New tip animates in.

### Toast
- Bottom-center pill, accent border, `✓` + message, rises in (~220ms), auto-dismisses after **2600ms**.

### Onboarding tour (first run)
- Auto-starts ~**700ms** after load **unless** the user previously opted out (localStorage key `bs_tour_optout` in dark, `bs_tour_optout_light` in light — keep them independent per theme, or use one key if you unify themes).
- Dims the whole screen and cuts a **spotlight** around the current target: a fixed box matching the target's bounding rect + 8px padding, radius 14px, accent 1.5px border, and a massive `box-shadow: 0 0 0 9999px <dim>` to darken everything else. The spotlight transitions position/size (~280ms) between steps.
- A **comic-style speech bubble** (322px) with a small rotated-square tail points at the target, auto-placed **below** the target if there's room else **above**, horizontally clamped to the viewport. Contents: step counter `n / 5` chip, title (700,14.5px), body (12.5px), and a footer row.
- Footer row: **☑ "Zapamiętaj mój wybór"** checkbox (default **checked**) + **"Pomiń"** (skip) + **"Dalej"/"Gotowe"** (next / finish).
- 5 steps target, in order: command palette → chain hero → activity feed → actions → right column (balance/contracts). Targets are located via `data-tour="palette|chain|feed|actions|side"` attributes.
- Closing (skip or finish): if "remember" is checked, write the opt-out key so the tour won't auto-open again; if unchecked, clear it. `?` button re-opens the tour anytime. Recompute spotlight/bubble on window resize + scroll while active.

### Background canvas animation
- Node count scales with viewport area: `N = clamp(24, 58, round(W*H / 48000))`.
- Each node: random position, velocity `±0.17 px/frame`, square size `6–17px`, ~22% are "accent" nodes (rest are muted).
- Each frame: clear; draw lines between node pairs closer than **195px** with alpha `0.10 * (1 - d/195)` in the accent color; then move + wrap nodes at edges and draw each as a slightly rotated (0.16rad) stroked+filled square (accent nodes ~0.13 fill / 0.32 stroke alpha; muted ~0.08 / 0.20).
- Respect `prefers-reduced-motion`: draw a single static frame, no animation loop.
- Provide an on/off toggle (the prototype exposes a `bgMotion` boolean, default on).

## State Management
State variables (names from the prototype):
- `height` (number), `difficulty` (4), `reward` (50), `peers` (4)
- `balance` (number, BSC), `address` (string)
- `blocks[]` — `{ height, hash, tx, nonce }`, newest first
- `mempool[]` — `{ hash, from, to, amount }`; `pending` = `mempool.length`
- `contracts[]` — `{ amount, type ('2-OF-3'|'HASHLOCK'), id, kind ('multi'|'hash') }`
- `feed[]` — `{ dot (color), title, meta, time }`, newest first, capped ~8
- Palette: `paletteOpen`, `query`
- Panel: `panel` (`null|'send'|'wallet'|'deploy'`), `deployType` (`'hash'|'multi'`), `form { recipient, amount }`, `formError`, `newAddress`
- `toast` (string)
- Tour: `tourActive`, `tourStep` (0–4), `tourRemember` (bool)

Triggers: mine → mutates blocks/mempool/balance/feed/toast; send/deploy/wallet → mutate mempool/contracts/balance/feed/toast + close panel; palette/panel open-close; tour next/skip/toggle. No network — all state is local/simulated in the prototype; wire to real node RPC in production.

## Design Tokens

Two palettes, same roles. Left = **Dark (Clay dark)**, right = **Light (Clay)**.

| Role | Dark | Light |
|------|------|-------|
| App background | `#12100c` | `#f8f4ee` |
| Card / surface | `#1c1712` | `#ffffff` |
| Border | `#2e2820` | `#ece3d7` |
| Divider (row) | `#241f18` | `#f0e9de` |
| Soft border / connector | `#3a3226` | `#ddd0bf` |
| Chip / older-block bg | `#191510` | `#faf6f0` |
| Dashed / gap bg | `#181410` | `#f1e9dd` |
| Accent tint (tip bg, hover) | `#251a11` | `#fbeee5` |
| **Accent** | `#e0894c` | `#c26b3d` |
| Accent hover | `#ed975a` | `#b25f31` |
| On-accent text | `#20120a` | `#ffffff` |
| Text (primary) | `#f0ebe2` | `#221c14` |
| Text soft | `#ddd4c5` | `#3a3226` |
| Text mid | `#b0a794` | `#6e6456` |
| Muted | `#8f8677` | `#8a8073` |
| Muted 2 (faint) | `#6f675a` | `#aba393` |
| Hover border | `#4a4030` | `#d8cbb8` |
| Success / sync (green) | `#6fd6a0` | `#1f8f4e` |
| Warn / amber dot | `#e0a24e` | `#d08a2a` |
| Hashlock tag bg | `#17271d` | `#e3f3ea` |
| Error bg / border / text | `#2a140e` / `#4a231a` / `#f08a6a` | `#fdeee2` / `#f0cdb6` / `#b23c1e` |

**Typography**
- UI / display: **Space Grotesk** (400/500/600/700).
- Mono (numbers, hashes, addresses, labels, keycaps): **IBM Plex Mono** (400/500/600).
- Common sizes: wordmark 16/700; card titles 14–15/700; body 12.5–13.5; big balance 24/700; uppercase mono labels 10.5px with `letter-spacing:.7px`; block numbers mono 15–16/700; hashes mono 11px; meta mono 10px.

**Radii**: cards 14–16px; hero 16px; block cards 12px; buttons/inputs 8–9px; chips/badges 4–5px; icon tiles 7–8px; status dots 50%.

**Spacing**: outer padding 24px; card padding 16–20px; grid/row gaps 16–18px; button padding ~11×13px.

**Shadows**: palette `0 24px 70px rgba(0,0,0,.6)` (dark) / `rgba(70,52,34,.16)` (light); drawer `-20px 0 60px …`; toast `0 16px 44px …`; tour bubble `0 22px 64px …`. Spotlight dim: `0 0 0 9999px rgba(8,6,4,.74)` (dark) / `rgba(40,32,22,.5)` (light). Modal backdrops use the same dim family with `backdrop-filter: blur(2–3px)`.

**Keyframes**: `bsPulse` (sync dot, 2.6s), `bsIn` (fade+rise 8px), `bsDrawer` (slide from +24px), `bsToast` (rise from +16px). Spotlight uses CSS transitions on left/top/width/height (~280ms ease).

**Layout constants**: top bar 58px, footer 40px, content max-width **1560px** centered, bottom grid height **420px**, drawer 420px, palette 560px, tour bubble 322px.

## Assets
No image or icon files — glyphs are Unicode characters (`⚒ ⌘ ⛏ ↗ ＋ ▤ ⧉ ✓ ✕ ● ◈`). In production, substitute your icon library's equivalents. Fonts load from Google Fonts (Space Grotesk, IBM Plex Mono) — swap for your app's font pipeline if preferred.

## Files
- `standalone/BlockSmith-Dashboard-Dark.html` — **fully working, self-contained** dark build. Double-click to open in any browser; no server, no dependencies, works offline. Behaves exactly like the live prototype (⌘K, mining, panels, tour, background animation).
- `standalone/BlockSmith-Dashboard-Light.html` — same, light theme.
- `BlockSmith App.dc.html` — dark theme source prototype (authoring format; needs `support.js` alongside it).
- `BlockSmith App Light.dc.html` — light theme source prototype.
- `support.js` — prototype runtime only. **Reference for behavior, do not port.**

For interacting with the design, use the `standalone/` files. For reading the exact markup/logic, use the `.dc.html` sources. Both themes are identical in layout and logic; only the token values differ (see the table). If you build a single themeable component, expose the palette as CSS variables / a theme object keyed by the roles above.
