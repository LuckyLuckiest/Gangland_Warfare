# WS7 G6 report — 2026-09-20

Status: DONE

Docs-only gate (no production code touched, per the dispatch). Done directly by me — no subagents, since accurately
documenting seven gates' worth of prior facts (exact file paths, test names, line numbers, ruling numbers) carries
real hallucination risk if handed to a fresh agent with no memory of the wave; every fact below was either read
directly from the current worktree state or cross-checked against an existing gate report/review before being
written.

## What changed

### Gangland worktree (`E:\Programming\java\wt\gangland-0.9.2`)

- **`CLAUDE.md`** — module table: `cops-n-crooks` now noted as "the only module still hard-coupled to Bartizan";
  `gangland-civilians`/`gangland-gadget` rows rewritten to say Bartizan is soft since 0.9.2 (`Host_Api: 1.1`,
  degrade behaviour spelled out); `gangland-turf` row notes it loads without Bartizan transitively. The Bartizan
  intro paragraph (Keystone section) rewritten from "hard, fail-fast for the three modules" to "only `cops-n-crooks`
  now" and gained four new paragraphs: jetpack ownership (`items/jetpacks.yml`, `/glw jetpack give <id> [amount]`,
  legacy re-stamp keeping fuel/`fuel_max`, the `Bartizan_Traits:` opt-in via Bartizan 0.4.0's
  `WearableCatalog.register`), and the two safety nets (`BartizanBlindScan`/`BartizanReferenceScan`) with the
  "a Bartizan reference inside a method body needs the allowlist" lesson (C2, fix round 1) spelled out explicitly.
  "Two tiers" section's stale "civilians/cops-n-crooks/gadget skipped" paragraph rewritten to the current, correct
  "only cops-n-crooks skipped" shape, `Host_Api: 1.1` for gadget/civilians noted. Module API contract section
  gained the `<module>/<module>_messages.yml` naming precedent (ruling W22, WS7 G4 fix round 1).
- **`documentation/module-loader.md`** — jar-layout diagram updated to current versions (Keystone 1.10.0, Bartizan
  0.4.0, Gangland 0.9.2) with soft/hard annotations per module jar, plus a new paragraph explaining the 0.9.2
  soft-coupling change. "What is a module today" table: civilians/gadget rows updated (soft Bartizan, `Host_Api:
  1.1`), turf row notes the transitive unblock. "Writing a module"'s live-examples paragraph rewritten: the old
  "`gangland-civilians` and `gangland-gadget` each declare `Plugins:` with `- Bartizan` alone" sentence replaced
  with the current soft-coupling story and a pointer to `documentation/TESTING.md`'s new §4b.
- **`documentation/TESTING.md`** — new **§4b "`BartizanBlindScan` / `BartizanReferenceScan` — proving a module is
  safe without Bartizan"**: what each tool checks (a comparison table), the C2 lesson that motivated building the
  second tool, worked usage snippets for both, how to add/remove a class from an allowlist, and the red-first
  requirement when adding either test to a new module.
- **`documentation/migration-0.9.2.md`** (new) — the server-owner migration note, modelled on the existing
  `migration-0.9.0.md`'s style/structure: before-you-upgrade, jetpack ownership + fuel/`fuel_max` survival on
  re-stamp, the armour/trait opt-in (`Bartizan_Traits:`, needs Bartizan 0.4.0+), the `wearable:jetpack` →
  `jetpack:<id>` shop/loot-chest re-add, module jar/`Host_Api` replacement, a table of exactly what a Bartizan-less
  server keeps versus loses per module (before and after 0.9.2), and a summarised list of every user-visible break.

### Bartizan worktree (`E:\Programming\java\wt\bartizan-0.4.0`)

Both target sections already existed with substantial content from an earlier pass (§12 of `migration.md` and the
"External wearable registration" section of `bartizan-api.md` were already written) — only the two specific
additions the dispatch asked for were missing:

- **`documentation/migration.md` §12** — gained the `fuel_max` sentence: a jetpack given out before 0.4.0 keeps its
  Bartizan-stamped `fuel_max` (typically `3600`) through Gangland's re-stamp-on-equip migration regardless of the
  catalogue's current `Max_Fuel:`, plus a pointer to Gangland's new `documentation/migration-0.9.2.md`.
- **`documentation/bartizan-api.md`**'s "External wearable registration" section — gained the pointer to Gangland's
  migration note (what it covers: item ownership, the vocabulary rename, fuel survival, what a Bartizan-less server
  keeps/loses).

### Main checkout (`brainstorming/decoupling-wave-2026-09-14/exec/WS7/`)

- **`G6-manual-checklist.md`** (new) — four numbered checklists, house `documentation/tests/TEMPLATE.md` style
  (Overview/Pre-Conditions/numbered steps/Regression Risks): punch a car without Bartizan, fly a jetpack without
  Bartizan (including the legacy-migration fuel-preservation step), a hostile civilian spawn without Bartizan (the
  one item confirmed by direct source read to be categorically unreachable by console smoke —
  `CivilianSpawnCommand`/`CivilianSpawnGroupCommand` both require `sender instanceof Player`), and jetpack armour
  reduction with Bartizan present (new behaviour from the `Bartizan_Traits:` opt-in, not previously covered by any
  smoke row or live-server test).
- **`G6-docket.md`** (new) — docket rows as text for the orchestrator to file. Every entry below was verified
  against the actual docket JSON (`brainstorming/bug-docket-2026-09-06/bugs.json`) or an existing gate review
  transcript before writing — see "Docket ids touched" below for the summary.

## Deviations from the plan

None. Every file the dispatch named was found or created at the path it specified.

## Red-first evidence

N/A — docs-only gate, no production code or tests touched. (The two safety-net tools' own red-first evidence from
the gate that introduced them is already recorded in `exec/WS7/G5-report.md`'s "Fix round 1" section, and is
referenced, not repeated, from `documentation/TESTING.md` §4b.)

## Build

- `graphify update . --force` — Gangland worktree: rebuilt clean, **15552 nodes, 40126 edges, 597 communities**
  (`graph.html` skipped — over the 5000-node visualisation limit, as documented).
- `graphify update . --force` — Bartizan worktree: rebuilt clean, **3846 nodes, 11191 edges, 184 communities**.
- `mvn test` — Gangland reactor (22 modules): `BUILD SUCCESS`, summed per-module `Tests run:` lines across the
  whole reactor: **845 run, 0 failures, 0 errors, 0 skipped**.
- `mvn test` — Bartizan reactor (3 modules): `BUILD SUCCESS`, **310 run, 0 failures, 0 errors, 0 skipped**.
- Both `mvn test` runs executed sequentially, one Maven process at a time, per the lesson recorded in the previous
  fix round (two subagents' concurrent `mvn clean` runs against the same worktree caused transient file-lock
  failures).

## Docket ids touched

Full detail and sourcing in `exec/WS7/G6-docket.md` (the orchestrator files these). Summary:

- **GD-12** → FIXED (0.9.2 G2, `JetpackAddonLoadTest`).
- **GD-20** → the jetpack/`glideDescentRate` half is now moot (the field no longer exists in that form — jetpack
  left `WearableAddon` entirely); the `Car.maxHealth`/`CarAddon` half is unaffected, still open.
- **GD-27** → open, unchanged (`CarDamageState` centralizes the token's read/consume across the G5 split but does
  not close the underlying race).
- **New**: default-material substitution in `CarAddon` (`CarAddon.java:77`, `XMaterial.matchXMaterial(...).orElse(XMaterial.MINECART)`)
  — the sibling bug to one the WS7 G2-G3 review found and fixed in `JetpackAddon` only, explicitly deferring
  `CarAddon`'s identical instance to a G6 docket row (that review's own words, quoted in `G6-docket.md`).
- **New**: negative/huge `<amount>` in `CarGiveCommand`/`JetpackGiveCommand` — found in the WS7 G4 review
  (Important 2), fixed in 0.9.2 G4 fix round 1; filed now per house rule (a fix isn't done until the docket knows).
- **New (`BZ-`, Bartizan project)**: `WearableRefresher.canRefresh` claims any `wearable`-tagged stack but
  `WearableService.getWearable` can miss for a foreign/stale tag, silently falling through the refresh chain —
  found in the WS7 G0-G1 review, pre-existing, not touched by this wave.
- **New (`BZ-`, Bartizan project, cross-referenced against `GD-07`)**: an externally-registered wearable
  (`WearableCatalog.register`, the mechanism the jetpack's `Bartizan_Traits:` opt-in uses) has `getPermission()`
  return `null` by design — none of Bartizan's own wearable-permission machinery (the subject of the still-open
  `GD-07`) ever applies to it. No fix needed for the jetpack itself (Gangland's own `Jetpack.getPermission()`
  already gates it) — filed so the relationship to `GD-07` is explicit and searchable for a future external
  registrant.

No docket rows needed for anything C1/C2-shaped from fix round 1 — both were introduced and fixed within the same
unreleased batch (per that round's own note, reconfirmed here).

## Subagents used

None — every file in this gate was read, cross-checked and written directly by me.

## Concerns / open questions

None blocking. One note for whoever picks up WS8: `GD-20`'s `glideDescentRate` moot-status is scoped narrowly (only
the specific `WearableAddon.glideDescentRate` field the docket named is moot) — if `JetpackAddon`/`Jetpack` grew its
own glide/descent-rate field during the G2/G3 rehome, whether *that* field is live or dead was not audited as part
of this gate and should not be assumed clean.

## After this gate

Per the dispatch: after G6, the WS7 stream (gadget wave) is complete. WS8 gets a fresh lead.
