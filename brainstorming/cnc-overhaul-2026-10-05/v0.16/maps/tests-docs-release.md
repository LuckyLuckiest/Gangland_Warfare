# 0.16 "Where they come from" — tests-docs-release Map

**Area:** Release bookkeeping, documentation, acceptance harness, smoke harness, bug docket curated list.
**Base:** 0.15.2 merges onto master 629af929; 0.16 is built on top.
**Revision:** 0.16.0 | **gangland-api:** 2.3 | **Keystone:** 1.15.0 | **Bartizan:** 0.6.0 (pin stays)

---

## Release Bookkeeping

### Root POM — E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\pom.xml

| Component | Current | Target 0.16 | Location |
|-----------|---------|------------|----------|
| `<revision>` | `0.15.1` | `0.16.0` | line 57 |
| `<keystone.version>` | `1.14.0` | `1.15.0` | line 70 |
| `<bartizan.version>` | `0.6.0` | `0.6.0` | line 71 (no change) |
| `<maven.compiler.release>` | `17` | `17` | line 62 (no change) |

**Action:** Bump revision to `0.16.0` (becomes module jar versions via `${project.version}` in module.yml). Keystone to `1.15.0` when the out-of-sight spawn helper + hold-post leash promotions are committed upstream (section 4: "Keystone fixes go UPSTREAM").

### Module Host_Api — all modules use `${project.version}` in module.yml at jar root

| Module | File | Current Host_Api | Target 0.16 | Reason |
|--------|------|------------------|------------|--------|
| cops-n-crooks | `gangland-features/cops-n-crooks/src/main/resources/module.yml` | `2.2` | `2.3` | Uses RegionProvider, PlaceNames.locate(), Waypoint.WaypointType.HOSPITAL |
| turf | `gangland-features/gangland-turf/src/main/resources/module.yml` | `2.2` | `2.2` (no api use yet) | — |
| civilians | `gangland-features/gangland-civilians/src/main/resources/module.yml` | `2.2` | `2.2` | — |
| gang | `gangland-features/gangland-gang/src/main/resources/module.yml` | `2.2` | `2.2` | — |
| gadget | `gangland-features/gangland-gadget/src/main/resources/module.yml` | `2.2` | `2.2` | — |
| mail | `gangland-features/gangland-mail/src/main/resources/module.yml` | `2.2` | `2.2` | — |
| npc-shops | `gangland-features/gangland-npc-shops/src/main/resources/module.yml` | `2.2` | `2.2` | — |
| lootchest | `gangland-features/gangland-lootchest/src/main/resources/module.yml` | `2.0` | `2.0` | — |
| healthbars | `gangland-features/gangland-healthbars/src/main/resources/module.yml` | `2.0` | `2.0` | — |

**Action:** Update only cops-n-crooks Host_Api line to 2.3.

### GanglandApi VERSION — gangland-api/src/main/java/org/luckyraven/gangland/api/

**Current:** 2.2 (shipped 0.15.1). **Target:** 2.3.
**Rule:** Additive only; contract: no removal, rename, or signature change of public members.
**Additions in 2.3 (per spec §4 "Decisions for you"):**
- `RegionProvider` SPI (id, name, 3D cuboid or sphere, owner, tags: restricted/hideout)
- `PlaceNames.locate(Location)` lookup
- `Waypoint.WaypointType.HOSPITAL` (additive value; existing code unchanged)

---

## Documentation Paths & Changelog Structure

### Current docs structure
- **Main docs:** `documentation/features/cops-n-crooks.md` (user), `documentation/developer/cops-n-crooks.md` (dev)
- **YAML configs:** `gangland-features/cops-n-crooks/src/main/resources/copsncrooks/` (cops.yml, cops_roles.yml, wanted.yml, wanted_messages.yml, detainment.yml, cop_radio_messages.yml)
- **Changelog precedent:** `documentation/v0.15.0/` contains CHANGELOG.md (Markdown, tagged emojis: ✨ New, 🔧 Changed, 🐛 Fixed, ⚙️ Configuration, 🧩 API, ⬆️ Upgrade notes) and CHANGELOG.bbcode.txt (BBCode format for forums, using `[SIZE=N][B]`, unicode bar, no `[HR]`)

### For 0.16 (create new folders)
- **Create:** `documentation/v0.16.0/` folder
- **Files:** `CHANGELOG.md` (emoji headings; see 0.15.0 for style) + `CHANGELOG.bbcode.txt` (dual format)
- **Sections to cover** (per spec section 6 "Done when"):
  1. Dispatch from stations (part built, new: ETA/place names/out-of-sight spawn)
  2. Mixed squads (part built, new: tier mixing, post-wipe breather)
  3. Hideouts & cooldown (new: places system, hideout speed multiplier)
  4. Crooked contacts (part built, new: phone page, price-per-star, unseen gate)
  5. Cold trail (new: speed multiplier, quiet-time tracking)
  6. Hospital respawn (new: additive HOSPITAL WaypointType, bill at respawn)
  7. Self-defence rules (part built, new: tuning keys, exploit guards, same-gang guard)
  8. Pursuit hand-off & containment perimeter (new features)

### Cops-n-crooks module configuration (0.16 updates)
All new feature toggles go in `copsncrooks/*.yml` (module-owned, never in settings.yml):
- `copsncrooks/wanted.yml`: new `Quiet_Speed` block (Enable, Per_Minute, Max, Backup_Skip_Seconds) for cold trail
- `copsncrooks/wanted.yml`: new `Self_Defence` block (Window, Min_Damage, Same_Gang_Guard, Per_Pair_Cooldown)
- `copsncrooks/wanted.yml`: new `Hideout_Speed` (Enable) for hideouts evasion multiplier
- New `Bribe_Stars` block for bribe pickup config (respawn timer, icon, radius)
- `copsncrooks/cops.yml`: new `Station` block (registry, names, districts, spawners)
- `copsncrooks/cops.yml`: new `Setup_Wand` block (modes, particles)
- New strings in `copsncrooks/wanted_messages.yml` for dispatch radio (station names, ETA), cold trail lines, perimeter lines

---

## Acceptance Harness (brainstorming/cnc-overhaul-2026-10-05/acceptance/)

**Structure:** Node.js + Python bot (Mineflayer) + server sandbox.

| Component | Path | File(s) |
|-----------|------|---------|
| Scenario generator (Node) | `ACC/` | `gen-cnc015.js` → outputs `ACC/scenarios/*.json` |
| Sandbox prep | `ACC/` | `prep-cnc015.sh <profile> [name] [port]` stages jars + YAML tweaks |
| Run harness | `ACC/` | `run-row.sh <server> <scenario.json>` + `run-all-cnc015.sh [rows...]` |
| Verdict scripts | `ACC/` | `cnc-verdict.js <row> <run1> [<run2>]` (Node.js logic + regex extraction) |
| Bot harness | `E:/Programming/java/wt/_programme/harness/` | Mineflayer scripts (6,852 files, copied 2026-10-05) |
| Profiles | `ACC/` | `default` (0.15 settings), `legacy-settings` (0.13.0), `r3`/`r4` (evasion tuning), `n3`/`n3-broken` (charge formula), `n4-old`/`n4-new` (bounty upgrade) |

**Pass line for 0.16:** Extends 0.15's R1-R4, N1-N8 (all PASS) with new rows for 0.16 features (T1-T9 draft).

---

## Smoke Harness (brainstorming/bartizan-split-2026-09-08/smoke/)

**Structure:** Python console harness + live test server (`E:/Documents/Minecraft/Test Server`).

| Component | File | Purpose |
|-----------|------|---------|
| Driver | `smoke.py` | `--rows <row> --deploy --keystone` boots server, deploys jars, runs console commands, reads log |
| Scenarios | `scenarios.json` | Deploy matrix: modules to load, `--dry-run` option |
| Reports | `reports/<row>.txt` | Per-row stdout (command output + error check) |

**Row example:** `cnc-015-boot` — deploy nine modules + Bartizan, no ERROR line, "Done (" appears.

**For 0.16:** Extend matrix for new components (regions, stations, wand modes).

---

## Bug Docket Curated List (0.16 scope)

**Sources:** `brainstorming/bug-docket-2026-09-06/bugs.json` + `brainstorming/cross-docket-2026-09-10/` (Keystone/Bartizan/Oriel entries, filter Gangland).

**Scope:** Entries touching spawners, stations, dispatch, regions, districts, waypoints, hospital, respawn, bribe, contacts, hideouts, self-defence, bounty takedowns, cop backup.

### Candidates (grep searches for keywords; verify against spec)

**Dispatch & stations (none found yet in 0.15 docket; likely open, added in 0.16).**

**Bounty & self-defence:**
- **WB-48** — Bounty.Minimum ships 0, so $0.01 post turns any kill into takedown (spec O1: "fix it in the Self-defence card"). Covered in self-defence card, MUST-FIX in 0.16.
- **WB-43** (bounty arithmetic, fixed 0.15): Verify still holds; tests `EntityDamageListenerTest.selfPostedBounty_killIsStillACrime`, `claim_paysPostedOnly`.

**Evasion & hideouts:**
- Evasion architecture exists (0.15); hideout speed multiplier is 0.16 new. No prior docket entries for hideout system itself.

**Hospital respawn:**
- Death bill logic shipped 0.15 with formula safety net; hospital waypoint type is 0.16 new.

**Cop backup & regroup:**
- Regroup exists 0.15 (Regroup.Enabled, Casualty window, Fall_Back_Seconds). Post-wipe breather breaker is 0.16 new.

**Contacts & bribe:**
- [WANTED] sign exists 0.15 (WantedAspect.canExecute only checks stars). Phone page + unseen gate + bribe pickups are 0.16 new.

### Action
- **Search docket for 0.15 bugs** touching evasion, cops, wanted, bounty, detainment. List any that affect 0.16 features.
- **Record 0.16 findings** in `triage/<slug>.txt` and rebuild docket before release (per CLAUDE.md rule: "A bug found that is **not** in the docket gets added to `triage/<slug>.txt`").

---

## Spec Drift (0.16 spec vs. today's code)

**None detected:** Spec matches 0.15.1 code state. All "already built" clauses in spec §5 verified against current module versions and line numbers (e.g., CopSpawnManager :71-84, CopManager :91-117, EntityDamageListener line ~120).

---

## Seams (Core-Module Boundaries)

1. **RegionProvider** (gang-api 2.3 addition): Core owns admin regions; turf/gang contribute their own through this SPI. Cops never depend on turf.
2. **PlaceNames** (gangland-api 2.3): Radio dispatch and hideout/restriction HUD rely on this lookup. Turf/gang name their regions through it.
3. **Waypoint.WaypointType.HOSPITAL**: Hospital respawn uses this marker. Waypoint lookup via `WaypointRepository.valueOf(name)` keeps old rows (no migration needed).
4. **Module YAML ownership**: Every feature toggle in `copsncrooks/*.yml`, never in `settings.yml` (api contract: new strings/settings in module-owned YAML).
5. **Keystone 1.15.0 surface**: Out-of-sight spawn helper + hold-post leash used by dispatch and containment perimeter.
6. **CopRadio.requestBackup / CopGroup.requestBackup:** Cold trail card and perimeter post-wipe breaker both affect backup timing (no module dependency).

---

**Lines:** 246 | **Created:** 2026-10-07
