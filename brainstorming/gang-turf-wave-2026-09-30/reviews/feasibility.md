# Adversarial feasibility review - gang-turf-wave-2026-09-30 PLAN.md / roadmap.json

Reviewer: opus 5.5 (feasibility lane), 2026-09-30. Planning only; no product code touched.
Graph freshness: `graphify-out/graph.json` 2026-09-29 23:08 is newer than HEAD `ff9d813f` (23:05), so the graph is fresh.
Oriented with `graphify explain CaptureService | TurfCaptureNotifier | RadioSides | GangPresenceTracker | TurfDefenderDeployer |
GangMembershipInstaller | GangLevelUpEvent | GangBountyEvent | GangPermissions | GangDeleteEvent` and
`graphify query "CivilianNpc setFactionSquads FactionSquads alertFaction"`, then raw reads of the files those pointed at.
Keystone checked by Grep/Read in `E:/Programming/java/Keystone` (master `da4d0d2` = 1.13.0; `phase-h13-npc-roles` = 1.14.0).
Spigot API checked with `javap` against `~/.m2/.../spigot-api-1.16.5-R0.1-SNAPSHOT-shaded.jar`.

## Verdict: PASS_WITH_FIXES

The backbone holds. Every class, seam, event, test file and Keystone API the plan names resolved, the four "codebase
corrections" in section 1 are true, module boundaries are respected (turf compiles against `gangland-gang` and
`gangland-civilians` at `provided` scope under `Depends: [civilians, gang]`, the existing pattern; cops reads turf through
its legal `Depends: [turf]`), and nothing needs Paper, NMS or a breaking `gangland-api` change. But four findings change
the design as written; each must be fixed in the plan before the wave that owns it starts.

---

## 1. Blocking issues

### B1. D7 (Open gangs) turns every existing gang into a walk-in gang

Evidence: `GG/gang/Gang.java` L78 `this.state = State.OPEN;` (constructor default); `GG/gang/database/tables/gang/GangTable.java`
L33 `state.setDefaultValue(Gang.State.OPEN.name())`; `GG/gang/database/repositories/gang/GangRepository.java` L87 and L92
`parseState` returns `OPEN` for null and for any unknown value. `Gang.State` is inert today, so every gang in every live
database is `OPEN`.

Failure: W2-P wires `gang join <name>` for `OPEN` gangs -> any player joins any existing gang without an invite, gets a
bank share (`Member.contribution` payout on disband) and counts as a defender/attacker. Folding `CLOSE` into `INVITE` by
deleting the constant makes stored `CLOSE` rows parse to `OPEN` through the fallback, i.e. the gangs that explicitly
closed become the most open.

Fix (W2-P, add to its owned files and tests):
- Default `INVITE` in all three places (constructor, table default, `parseState` null/unknown fallback).
- Keep `CLOSE` as a deprecated constant, or map `"CLOSE"` -> `INVITE` in `parseState`; never let an unknown value become `OPEN`.
- One data migration on load or in `createTables`: `state IN ('OPEN','CLOSE') -> 'INVITE'` (existing gangs never opted in).
- `gang recruiting open` is the only way a gang becomes `OPEN`; migration doc states it.
- Test: a stored `OPEN`/`CLOSE`/garbage row loads as `INVITE`; `gang join` on an `INVITE` gang is refused.

### B2. The W1 exit criteria cannot hold on a fresh install: `/glw` is OP-only

Evidence: `gangland-impl/src/main/resources/plugin.yml` L20-28 `glw: permission: gangland.command.main` and
`permissions: gangland.command.main: default: op`. Keystone `keystone-command/.../argument/Argument.java` L111-120
`addPermission` registers every command node as `new Permission(permission)` (Bukkit default = OP) and skips names
already declared. So a non-op player without a permissions plugin cannot run `/glw` at all.

Failure: W1 exit "a player with no guidance opens `/glw map`", "a gangless player gets the onboarding screen", P2.6 "a
fresh-install member can deposit" are false on a default install. The plan's risk row only mentions
`gangland.command.map`, and only as "check the framework default" - it is OP, verified.

Fix (W1 kickoff, orchestrator-owned `plugin.yml`, already in the shared-append table):
- Add `permissions:` entries with `default: true` for the player verbs the ten-minute script uses:
  `gangland.command.main`, `gangland.command.map`, `gangland.command.gang` (and the gang player sub-verbs the script
  needs: info, list, create, join, deposit, chat), `gangland.command.turf` (+ `info`, `list`). Admin verbs keep OP.
  Declaring them in plugin.yml wins because `addPermission` skips already-known names.
- Migration doc: this is a behaviour change on live servers (players can now reach `/glw`); every admin/destructive
  node still defaults to OP.
- Test: a resource test that parses `plugin.yml` and asserts the player set is `default: true` and `turf.admin` is not.

### B3. The always-on HUD line starves Bartizan's ammo bar and the gadget HUDs

Evidence: Keystone `keystone-common/.../util/ActionBarManager.java` L93-112: a background send claims
`computeGrace(priority)` (priority 0 = 2.8-4.0 s, priority 10 = 0.28-0.4 s) during which **strictly higher** priorities
are blocked, and a same-priority resend passes and renews the grace. Existing priority-10 senders: Bartizan
`bartizan-plugin/.../hud/HudService.java` L104 (ammo), gadget `VehicleSession.java` L132 (fuel) and `JetpackTask.java` L198.

Failure: P2.3 `TurfHudTask` at 1 Hz through `sendBackground(player, msg)` (priority 0) renews a 2.8-4 s grace every
second, so the ammo, car-fuel and jetpack HUDs never display while the turf line is on. The plan's escape, "skips
unchanged strings", breaks the other way: the vanilla action bar fades after about 2 s, so an unchanged line disappears
and the HUD is not "always on". D12 recommends the HUD on by default for gang members, and D6 retires the presence boss
bar in favour of it, so this lands on every gang member.

Fix (W1-H):
- Send at priority 9 (grace about 0.53-0.76 s + the 0.6 s window), re-send every 2 s regardless of change, never
  "skip unchanged". A priority-10 HUD then displaces it within under a second and holds the slot while its owner
  keeps sending; the turf line reappears when the weapon/vehicle HUD stops.
- Drop the outside "Nearest turf: Docks 120m NE" line from the default (it would occupy the action bar server-wide);
  keep it behind `map hud on` or in the placeholders only. Otherwise D12 should be option B (off by default).
- Manual checklist item: hold a Bartizan gun inside a turf, drive a car inside a turf; both HUDs must be readable.
- Note `FuelHoldDisplayListener` (gangland-item L104) sends at priority 0 and will lose to a priority-9 turf line;
  record it as accepted or bump fuel-hold to 9 in the same lane.

### B4. "One `NpcSquad` per turf" contradicts Keystone's squad model and bypasses civilians' shout wiring

Evidence:
- Keystone `keystone-npc/.../NpcSquad.java` class javadoc: "A team of NPCs hunting one target"; `reportSightingBy`
  (L187-198) calls `loseContact` whenever a member sights a target other than `contactTarget`.
- `GC/npc/CivilianService.java` L194-215: squads are created only in private `newSquad(SquadKey, ...)`, which wires
  `squadListener` (faction shouts, recruitment) and `keyOf`; `FactionVoice.hunted`/`extras` (L362-384) resolve through
  `keyOf.get(squad)`.
- `GC/npc/CivilianNpcRegistry.java` L35 sets `npc.setFactionSquads(factionSquads)` (the CivilianService) on **every**
  register, so "honour `victim.getFactionSquads()` when set" is always true.
- PLAN section 2: "Civilians stays gang-free", yet P5.3 has civilians `FactionVoice.extras` supply `%gang%`.

Failure: with two or more attackers, one per-turf squad thrashes CONTACT / CONTACT_LOST every tick (shout spam, the
fan centre jumping between targets). A squad created turf-side has no listener (no shouts at all) and no `keyOf` entry
(`hunted` null, `%faction%` missing). The "when set" test cannot distinguish a turf provider from the default.

Fix (W3-A then W3-B):
- `SquadKey` gains a `scope` (`SquadKey(faction, scope, targetId)`, default scope `""`); NEW public
  `CivilianService.squadFor(CivilianNpc, LivingEntity, String scope)` creates through `newSquad`, so listener and
  `keyOf` stay wired. Per-target semantics are kept.
- Turf's `FactionSquads` implementation (NEW `GT/npc/defender/TurfGarrisonSquads`) passes `"turf:" + turfId` as scope
  and is set after spawn; `alertFaction` / `recruit` use `victim.getFactionSquads() != this` as the "custom provider"
  test and only recruit NPCs whose provider and scope match (no cross-turf pull, also on the shout-driven
  `drainPendingRecruits` path).
- `FactionSquads` gains `default Map<String, String> extras(NpcSquad squad)` returning `Map.of()`; `FactionVoice.extras`
  merges it, so turf supplies `gang` and civilians never names a gang. `Shouts.Format` `%gang%` falls back to `%faction%`.
- Posts, leash, reserve and waves live in a turf-side per-turf holder (NEW `TurfGarrison` state object), not in `NpcSquad`.
- Tests: two attackers on one turf produce one CONTACT per target squad, no CONTACT_LOST thrash; two turfs' guards
  never share a squad; default path (no custom provider) unchanged, cops tests green.

---

## 2. Non-blocking fixes

| # | Plan item | Finding (evidence) | Fix |
|---|---|---|---|
| F1 | P5.2 "spawn ... out of `hasLineOfSight`" | Spigot 1.16.5 `LivingEntity` has only `hasLineOfSight(Entity)` (javap); the `Location` overload is Paper-only | Test a candidate post with `World.rayTraceBlocks(attackerEye, dir, dist)` (1.13+) from each nearby attacker |
| F2 | P2.5 `gang/gang_rules.yml`, P1.4 "`Invite_Offline_Expiry_Hours` in mail YAML" | `gangland-gang` and `gangland-mail` resources hold only `commands.json`, `module.yml`, `module.properties`; neither module has a FileHandler / file bean | Mark as NEW infra: a `GangModuleFiles`/`MailModuleFiles` record + file config bean built with the module classloader (copy `TurfModuleFileConfig`), owned by W1-G / W0-D |
| F3 | Shared file `GT/TurfModuleFiles.java` | It is a 2-field record `(powerups, npcs)`; the handlers are built in `TurfModuleFileConfig` | Put `TurfModuleFileConfig.java` in the shared-append table beside `TurfModuleFiles` |
| F4 | W1-T `Turf.Snap_To_Chunks`, TF-21 size limits | No YAML exists for them in W1 (`turf_rules.yml` is created by the W2 kickoff) | Create `turf/turf_rules.yml` + `TurfRules` loader in the W1 kickoff; W2 kickoff only adds keys |
| F5 | P2.4 snap | Snapping grows a region to chunk bounds after `findConflict` may have passed | Run `TurfManager.findConflict` on the snapped region; test a snap that would overlap a neighbour |
| F6 | Section 5.1 size "10 lines = unfocused chat" | Legend line 1 in 5.2 is about 79 chars (~430 px) against the 320 px default chat width, so it wraps; gang names up to 16 chars make it worse | Cap each legend line to about 50 chars (letter + truncated name, max 4 gangs, `+N more`), full names in the letter hovers |
| F7 | Render rule 6 vs 5.1 hover | Run-length merge of equal neighbours conflicts with a hover that carries cell coordinates and per-cell coverage (`Edge of Docks (40 %)`) | Hover carries turf-level facts only; drop cell coordinates, show coverage only in the centre-cell tooltip, or merge only cells with identical hover |
| F8 | P5.3 callsigns | `[Vipers] Guard #1042` and `[Vipers] Quartermaster` are 20 chars against the plan's own `<= 16` rule | `[VIP] Guard 42` style (tag = 3-4 char short code) or a hologram line; test the formatter against the limit |
| F9 | W3-W dynmap | The turf pom has no dynmap API dependency or repository; the lane gate says no pom edits; a `@Bean` in `TurfModuleConfig` naming a dynmap type would make `ReflectionGuard` skip the whole config class | W3-W owns `gangland-turf/pom.xml` (+ root repository entry); dynmap types only inside `GT/map/web/**`, created behind `Bukkit.getPluginManager().getPlugin("dynmap") != null` from a type-free factory |
| F10 | W4-R "api 2.0 -> 2.1" | `h13-roles` adds `gangland-api/.../npc/FieldCareSettings.java` with `GanglandApi.VERSION` still `"2.0"` | W4-R reads the merged master's `VERSION` first; if H13 bumps to 2.1, `NpcRole` is 2.2 |
| F11 | W4-R "cops `config/CopRole.java`" | `CopRole` exists only on `h13-roles` at `copsncrooks/npc/police/config/CopRole.java` | Correct the path |
| F12 | Shared files, W1/W3 | `MapCommand` root is extended by W1-H (`map hud`), W3-Z, W3-V, W3-I | Kickoff stubs every child class and wires it into the root, or add `MapCommand` to the shared-append table |
| F13 | W2-P deletes `GT/contribution/**` | Its beans live in `TurfModuleConfig`, where lanes may only add `@Bean` methods | Orchestrator removes those bean methods in the W2 kickoff (or W2-P is granted that one edit) |
| F14 | P3.5 turf cap | `TurfRules.cap(gang)` is touched by W2-R (`GT/capture/**`), W2-N (income) and W2-P ("`Turf_Cap_*` read through `Gang.getLevel()`") | W2 kickoff writes `TurfRules.cap` from `gang.getLevel().getLevelValue()` (Lombok getter on `core/user/Level`, starts at 0); W2-P only adds XP |
| F15 | Wave gate, W1-E | `documentation/features/turf.md` and `documentation/tests/features/turf.md` do not exist (gangs.md / ranks.md do) | Mark both NEW (W1-E creates them) |
| F16 | P4.3 digest "since your last quit" | No per-player last-quit timestamp is named; `OfflinePlayer.getLastPlayed()` at join is not a reliable previous-quit time | Store a last-seen stamp per player in the turf log (or read it at `PlayerQuitEvent`) and query `> lastSeen` |
| F17 | P1.3 citation | `GangMembershipInstaller.gangsAllied` is at L56, and `TurfFriendlyFireListener` is `cops-n-crooks/.../listener/turf/TurfFriendlyFireListener.java` (L103) | Correct the citation |
| F18 | P2.3 placeholders | `GanglandPlaceholder.isGanglandOwnedPrefix` knows `user_`/`bank_`/`unique-item_`/`gang_` only, so an unanswered `turf_*` token renders raw, not `NA`; existing tokens use dashes after the prefix (`gang-id`) | Accept (turf module absent = raw token) or add `turf_` there in the orchestrator lane; pick one token style |
| F19 | Docket accounting | `bugs.json` holds TF-01..39 (39) and GR-01..37 (37); the 38 open TF / 25 open GR split comes from the artifact db and was not re-read here. Lane accounting (38 distinct TF, TF-02 twice) checks out | None beyond a re-read at W0-E |

## 3. Verified as stated (no change)

- `CaptureService.isCapturable` L112-127 (grace via `owner.getLastMemberOnlineAt()`), "exactly one challenger, zero
  defenders" at L209, dead `dominantGang(..., exclude)` (always `null`), `complete` L388-409, `cancel` L411-418. A
  running contest does not re-check `isCapturable`, so the "contest lock" is already true.
- P3.1 flip is sound: `GangPresenceTracker` heartbeats every 60 s while a member is online and `GangPresenceListener`
  stamps on join (L37) and quit (L42/L62); `last_member_online_at` is persisted (`GangTable` L25).
- `TurfCaptureNotifier.notifyDefenders` L77-89 loops `Bukkit.getOnlinePlayers()`; `TurfCaptureFailedEvent` has no
  handler (the failure really is silent); `Reason.CANCELLED` exists.
- `RadioSides.compass8(Location, Location)` is in `gangland-api` (`npc/radio/RadioSides.java` L49) - turf may use it.
- `setOwnerGangId` writers: `CaptureService` L395, `TurfSetOwnerCommand` L78/L90, `InactivityReleaseTask` L38/L44,
  `TurfIncomeDistributor` L66 - exactly the W0-A list.
- `Gang.isAlly` L102-104 is one-directional; `GangDeleteEvent` is fired only by `GangDeleteCommand` L286; payouts
  snapshot the pool at L186 into `payouts(...)` L301.
- `PlaceholderContribution` is an api seam (`gangland-api/.../data/placeholder/extension/`), collected from the
  container by `PlaceholderContributions.from`, consulted by `GanglandPlaceholder` L154; `GangPlaceholderContribution`
  is the pattern to copy.
- `TurfLocationTracker` exposes `@Getter playerTurfCache`, ticks at 1 Hz, and its javadoc forbids `PlayerMoveEvent`.
- `TurfListCommand.sendRow` already compiles `ComponentBuilder` + `HoverEvent(Action, new Text(...))` + `ClickEvent` at
  the 1.16.5 floor (`Text.class` present in the shaded API jar). `CuboidRegion` is x/z-only and immutable, so
  `snappedToChunks()` returning a new region fits.
- Keystone `Color` has 16 names over 14 codes (`&6` and `&5` repeat). `ActionBarManager.sendBackground` exists.
- `CivilianNpc.factionSquads` has Lombok public getter/setter; `FactionSquads` is a functional interface (since 1.13.0).
- Keystone `AbstractNpc.navigateTo`, `canSee`, `pursue`, `takeCover`, `NpcSquadSignal.LEADER_DOWN` are on master
  (1.13.0); `NpcFanPlacement`, `setLeaderPriority`, `setRangedBand`, `millisUnreachable` exist only on
  `phase-h13-npc-roles` (revision 1.14.0) - W4 correctly waits.
- H13 branches (`npc-roles-medics`, `h13-roles/names/stuck/drop`) touch no file in `gangland-turf`, `gangland-gang` or
  `gangland-mail`; civilians only `BartizanNpcWeapons` (+ tests). `h13-roles`/`h13-stuck` pin Keystone 1.14.0,
  the other three 1.13.0. `GanglandMoneyDropClassifier`/`NpcMoneyDropSource` are already on master (api).
- `WantedKillTracker` (core), `WantedKillTrackers`, impl `EntityDamageListener.handlePlayerKills` L100, cops
  `seam/KillComboWantedTracker` exist; a `default` method is additive.
- Every pinned/extended test the plan names exists (`CaptureServiceStartAndCompleteTest`, `CaptureServiceOwnedTurfTest`,
  `CaptureServiceHelpersTest`, `TurfManagerTest`, `RankManagerTest`, `GangAllianceTest`, `ActiveBuffManagerTest`,
  `GarrisonManagerTest`, `RankPermissionApplierTest`, `TurfDeleteCommandTest`, `TurfRepositoryTest`,
  `RankRepositorySpiTest`, `TurfModuleConfigTest`, `TurfPowerupManagerTest`, `TurfDefenderDeployerTest`, `GangModuleTest`, ...).
- `EffectType.CAPTURE_DEFENSE_BONUS`/`GARRISON_DISCOUNT`, `reinforced_defense` in `turf_powerups.yml`, inventory YAMLs
  `gang_info/alliance_stat/gang_stat.yml`, `WandSelectionManager`, `RankParent*`, `CivilianDamageListener` exist.
- Spigot 1.16.5 has `BossBar`, `MapView`/`MapRenderer` (contextual), `World.rayTraceBlocks`, `AsyncPlayerChatEvent`;
  no plan item needs `io.papermc.*` or NMS once F1 is applied. squaremap/Pl3xMap correctly marked out.
- No Keystone change in W0-W4; the promotion candidates wait for a second consumer - consistent with the upstream rule.

## 4. Module-boundary check

| Edge the plan adds | Legal? |
|---|---|
| turf `TurfGangDeleteListener` on gang `GangDeleteEvent` | yes: turf pom has `gangland-gang` provided + `Depends: gang` |
| turf -> `GangManager.addExperience` / `GangLookupContract` | yes (same edge) |
| turf `FactionSquads` impl + `CivilianService.squadFor(..., scope)` | yes: `Depends: civilians`; civilians gains a generic scope, no gang type (B4) |
| cops `KillComboWantedTracker` reads turf contest state | yes: cops `Depends: [turf, civilians]` |
| core `WantedKillTracker.exemptsKill` default | additive host API -> modules that use it declare `Host_Api: 2.1` (check F10) |
| api `NpcRole` record | additive, W4 only |
| api `Settings.java` body fix (Display_Name_Char) | body only, no signature change, no bump |
| impl `plugin.yml` softdepend `dynmap` + `permissions:` block (B2) | orchestrator-owned shared file |
