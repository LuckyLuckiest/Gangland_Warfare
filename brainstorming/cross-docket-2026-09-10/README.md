# Cross-project bug docket — 2026-09-10

One docket for Gangland Warfare (0.9.0), Keystone (phase-h8-item-npc, 1.9.1), Bartizan (master, 0.1.x) and
Oriel (0.7.0). Orchestrated by Fable: haiku mappers -> opus per-project leads (who scope and order
sonnet/haiku scanners) -> Fable verification -> one artifact with a per-project filter.

Layout
- `<project>/systems.md` — phase 1: system-code mapping (code, slug, name, packages, hubs, risk hotspots)
- `<project>/findings/<system-slug>.txt` — phase 2: one finding per line,
  `num~~tier~~title~~fix~~tests~~location~~observation~~confidence`
  (tier P0/P1/P2/P3/X, tests `-` when none, location `Module path/File.java:line`, confidence High/Medium/Low)
- `<project>/REVIEW.md` — phase 2: the opus lead's scope decisions, scanner orders, culled findings and why
- `gangland/` — carried over from `../bug-docket-2026-09-06/bugs.json` (464 entries) + live statuses from the
  old artifact's db; Gangland is not re-scanned here
- `build_docket.py` — merges everything into `bugs.json` and renders `docket_template.html` into
  `cross-project-bug-docket.html`
- `prompts/` — the exact prompts handed to each agent (audit trail)

Ids: Gangland keeps its ids (`CL-01`, `T-35`). New projects: `KS-<code>-<nn>` Keystone, `BZ-<code>-<nn>`
Bartizan, `OR-<code>-<nn>` Oriel. Published page: https://claude.ai/code/artifact/4a903fb6-cbdd-4810-90b8-88a863e9013c ("LuckyRaven Bug Docket", 702 entries: Gangland 498, Keystone 103, Bartizan 64, Oriel 37).
Status/notes live in that artifact's shared db (collection `bugs`,
doc id = bug id, `{status, note, updatedAt}`); Gangland rows were seeded from the old artifact.

Tiers (unchanged from 2026-09-06):
- P0 fix now: money/item creation or loss, data loss/corruption, permission holes, crashes on normal paths, thread-safety faults
- P1 fix next: wrong-but-bounded behaviour, stuck states, uptime leaks, silent failures hidden from admins
- P2 scheduled: edge-case crashes, backend-specific failures, latent traps, dead features shipped as working, missing UX
- P3 cleanup: dead code/config, help + i18n drift, convention violations, cosmetics
- X withdrawn / by design
