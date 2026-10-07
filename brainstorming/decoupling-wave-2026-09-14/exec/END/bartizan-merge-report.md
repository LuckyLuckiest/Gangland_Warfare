# Bartizan merge report: 0.4.0 → 0.5.0

Repo: `E:\Programming\java\Bartizan`. No subagents used, no push, no remote created.

## Result

- Merge commit: **`9fa25c3f0dbfbf4ed86ce753b4f18363d433fae5`** on branch `0.5.0`
  ("Bartizan 0.5.0: merge 0.4.0 (external wearables, jetpack residue gone) into the HO/HP line")
- `master` fast-forwarded in place (no checkout) via `git fetch . 0.5.0:master`:
  `99ca0863015a8e9a75f85d9ac1b2a659888411e0` → **`9fa25c3f0dbfbf4ed86ce753b4f18363d433fae5`**
- Working tree stayed on `0.5.0` throughout; `git status` clean after the merge commit.

## Branches merged

- `0.4.0` tip `705885e` ("docs: migration note keeps fuel_max on re-stamped jetpacks; bartizan-api.md points at
  Gangland's migration note") + parent `dda1710` ("Bartizan 0.4.0: jetpack residue leaves; external (damage-only)
  wearables for gadget-owned armour"), branched from merge-base `0e9e456`.
- Into `0.5.0` tip `bd78dfe` ("Roadmap, README, API and migration notes for 0.5.0 (HO staged reload, HP spyglass
  scope)"), 12 commits ahead of the same merge-base (compile-floor drop, HUD refresh, reload duration, ammo id
  quoting, bundled weapon defaults, HM import flags, then the HO/HP feature commits).
- Neither `705885e` nor `bd78dfe`'s commit message states an explicit test count, so there was nothing numeric to
  reconcile against — the build gate below is the first combined count for this merge.

## Per-file resolution

`git merge 0.4.0 --no-commit --no-ff` on `0.5.0` auto-merged everything except two files; two others merged
cleanly with no conflict despite being touched on both sides.

| File | Outcome |
|---|---|
| `pom.xml` | **Conflict on the `<revision>` line only** (`0.5.0` vs `0.4.0`) — resolved to **`0.5.0`** per the task rule. `<bukkit.version>` had no conflict: 0.4.0 never touched that line, so git kept 0.5.0's `1.16.5-R0.1-SNAPSHOT` (0.5.0's own compile-floor-lowering commit `8ba89bc`) automatically. `<keystone.version>` was untouched by both sides (stays `1.9.0`). |
| `bartizan-api/.../Wearable.java` | **Auto-merged, no conflict.** Kept both: 0.4.0's `external` boolean field (`@Builder.Default private final boolean external = false;`) plus the `getPermission()` guard (`if (temporary \|\| external \|\| wearableKey == null) return null;`), and 0.5.0's own additions (new imports `java.lang.reflect.Method` / `org.bukkit.NamespacedKey`, `Enchantment.getByKey(NamespacedKey.minecraft(key))` reflective enchantment lookup for the HO/HP work). Verified post-merge by grepping both markers in the merged file — both present. |
| `documentation/bartizan-api.md` | **Auto-merged, no conflict.** Kept 0.4.0's "External wearable registration (`WearableCatalog.register`, 0.4.0, WS7-D4)" subsection and the `register(String, Wearable)` accessor-table row, alongside 0.5.0's new "Staged reload" / spyglass-scope subsections. |
| `documentation/migration.md` | **Conflict**: both sides appended a `## 12.` section (0.4.0's jetpack-departure note vs 0.5.0's HO/HP note) with different titles. Resolved per the task rule — kept **both**, ordered by version: 0.4.0's "## 12. 0.4.0 — jetpack leaves Bartizan entirely (Gangland WS7)" kept as `§12` (matches the existing forward-reference at line 44, "superseded by §12", which merged in cleanly and needed no renumbering), 0.5.0's "## 13. 0.5.0 (gates HO, HP) — staged reload, spyglass scope, crossbow aim pose" renumbered from `§12` to `§13`. |

Everything else 0.4.0 touched (`WearableCatalog.java`, the wearable command/converter/serializer/refresher classes,
`ItemConfig.java`, `BartizanItemVocabulary.java`, `WearableAddon.java`/`WearableService.java`,
`items/wearables.yml`, the deleted `WearableAddonLegacyJetpackTest.java`, the new
`WearableExternalRegistrationTest.java`) auto-merged with zero conflicts since 0.5.0 never touched those files.

## Intent cross-check (dda1710 / WS7 G1)

Confirmed against `dda1710`'s message and the WS7 report/review (`Gangland Warfare/brainstorming/decoupling-wave-2026-09-14/exec/WS7/G1-report.md`, `G1-review.md`): `WearableCatalog.register(String, Wearable)` is api, `Wearable.external`
marks an entry Bartizan never builds/converts/gives/lists/serialises (only applies damage reduction/effects to
whatever the registering plugin stamps `wearable=<key>` onto), and `getPermission()` returns `null` for an
external entry so no unregistered permission node blocks equipping. All of that is intact in the merged
`Wearable.java` / `WearableCatalog.java`, and `WearableExternalRegistrationTest` (the red-first pinning test named
in the commit) is present and green post-merge.

## Build gate

`mvn clean verify` in the Bartizan reactor — **one build, both modules**:

```
Bartizan API ....................................... SUCCESS [  9.398 s]
Bartizan Plugin .................................... SUCCESS [ 12.958 s]
BUILD SUCCESS
```

- `bartizan-api`: Tests run: 154, Failures: 0, Errors: 0, Skipped: 0
- `bartizan-plugin`: Tests run: 330, Failures: 0, Errors: 0, Skipped: 0
- **Reactor total: 484 tests, 0 failures, 0 errors, 0 skipped.**
- Jar built as `bartizan-plugin/target/Bartizan-0.5.0.jar` (confirms the `pom.xml` revision resolution landed
  correctly). One pre-existing, harmless shade warning (`META-INF/MANIFEST.MF` overlap across
  `Bartizan-0.5.0.jar`/`bartizan-api-0.5.0.jar`/bStats jars) — not new to this merge.
- `WearableService - external registration (WS7-D4)` (6 tests) and the surviving `WearableAddonTest` (3 tests,
  down from 4 after the legacy-jetpack test's removal) are both present and green in the module-level rollup,
  confirming 0.4.0's wearable-external work carried through the merge alongside 0.5.0's HO/HP test suites
  (staged reload, spyglass scope, crossbow pose, etc.).

## Not done (out of scope per task)

- No push, no remote created (`git remote -v` empty).
- No code review beyond the intent cross-check above.
