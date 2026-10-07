# Turf census - gangland-turf runtime module @ 0.12.0

Created 2026-09-30 by the turf census agent (planning-only wave). Repo `master @ ff9d813f`, Keystone pin `1.13.0`
(root `pom.xml:70`). Graph freshness checked first: `graphify-out/graph.json` (2026-09-29 23:08) is newer than the last
commit (23:05), so graph orientation is current. Everything below was re-read from source at 0.12.0; the older audit
`docs/wiki/workflow-audit-10-turf.md` (taken on branch 0.8.1) was used for its Overview / Components / Observations
table only and every observation was re-verified (see section 7 for what is stale).

Module root: `gangland-features/gangland-turf` - 97 main + 26 test Java files, ~7.5k main LOC, package
`org.luckyraven.gangland.turf.*`. All paths below are relative to
`gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/` unless they start with another module name.

## 0. Headline findings (read this first)

1. **A turf is an admin-drawn X/Z rectangle, not a chunk claim, and nothing in the player-facing surface shows
   where they are.** No map, no compass, no distance/direction; `/glw turf list` prints every turf on the server with
   id / name / owner / world (bounds only in a hover) and a `(tp)` link.
2. **Owned turfs are only capturable while the owning gang is fully offline for 10+ minutes.**
   `CaptureService.isCapturable` (capture/CaptureService.java:99) returns false while any owner member is online or was
   within `Post_Logoff_Protection_Minutes` (10). Start of contest additionally needs zero defenders/allies inside and
   exactly one challenger gang (`tickIdle`, :168). So by construction the defender gang is offline when a raid starts:
   "get told it is under attack" is mostly a login-time bar, and the whole garrison/Quartermaster kit is an offline-defence
   mechanic. This is the single biggest shape constraint for the redesign.
3. **Nothing tells an offline gang what happened.** No mail (`gangland-mail` has zero turf references), no login
   digest; failure is intentionally silent (`TurfCaptureNotifier` javadoc, "spec 2.6"); a capture is a global broadcast
   only when `Broadcast_Globally` is true. Income payouts send no message at all (`TurfIncomeDistributor.distribute`).
4. **The Phase-1 "steal the contest" reward documented in `CapturePhase` and in `settings.yml` (lines 663-665) is not
   implemented**: Phase 1 never decrements (`tickContestingUnclaimed`, delta = base or 0) and `dominantGang`'s `exclude`
   parameter is dead. Two of the four shipped powerups (`reinforced_defense`, `garrison_discount`) have no consumer.
5. **Defenders/Quartermaster already ride the H12 squad machinery, but only indirectly**, through the civilians module
   (`CivilianService implements FactionSquads`). The turf module itself has zero references to `NpcSquad`,
   `NpcPursuit` or `NpcSquadSignal` (grep of `gangland-turf/src` is empty). Defenders are one un-roled
   `turf_defender` faction, spawned at one column, never leashed. Details in section 3.
6. **Rank-gated turf permissions are a dangling hook.** `gangland.turf.capture`, `.contribute`, `.upgrade` exist only in
   `RankPermissionApplierTest` fixtures; no production code checks them. Any gang member (a fresh recruit included) can
   capture and, via the Quartermaster panel, spend the gang bank.

## 1. Model

| Class (NEW = proposed, none here) | File | Facts |
|---|---|---|
| `Turf` | data/Turf.java | Persisted: `id` (int), `displayName`, `region`, `ownerGangId` (nullable Integer = unclaimed), `incomeAmount` (BigDecimal), `createdAt`, `lastCaptureTimestamp`. No type, tier, level, upgrade slot, tag or neighbour list. `setDisplayName` has no caller. |
| `CuboidRegion` implements `Region` | data/CuboidRegion.java, data/Region.java | `world` name + `minX/maxX/minZ/maxZ`, normalised in the constructor; **Y is ignored** ("bedrock to sky"). `contains(Location)` and `overlaps(CuboidRegion)` both inclusive. Arbitrary size, no minimum/maximum, no chunk alignment. |
| `TurfRuntimeState` | data/TurfRuntimeState.java | Not persisted: `state` (IDLE/CONTESTING/COOLDOWN), `phase` (CLAIM/CONSOLIDATE), `captureProgress` 0..100 within the current phase, `challengerGangId`, `lastChallengerSeenAt`. Resets on every start. |
| `TurfState`, `CapturePhase` | state/ | Enums. `COOLDOWN` is runtime-only (see 2.5). |
| `TurfManager` | manager/TurfManager.java | Registry: `turfsById`, `turfsByWorld` (live `ArrayList` per world - TF-34), `runtimeStates`, `allocateId()` (seeded `max(id)+1` on `initialize()`), `findAt(Location)` (**linear scan over the world's turfs**), `findConflict`, `create`, `delete` (removes from indexes + repository only), `persist`. No spatial index, no chunk index, no `neighbours()`/`adjacent()` API. |
| `TurfRepository` / `TurfTable` | database/TurfRepository.java, database/TurfTable.java | Table `turf`: `display_name, world, min_x, max_x, min_z, max_z, owner_gang_id, income_amount (Double), created_at, last_capture_timestamp`. Hard casts `(int)/(long)` in `doLoadAll` (TF-14); income stored as Double (TF-15). Three child tables with no FK: `turf_active_buff`, `turf_garrison`, `turf_powerup_npc`. |
| `TurfRepositoryContract` | contract/TurfRepositoryContract.java | Test seam over `IRepository<Turf>`. |

### How turfs are defined (admin only)
Wand path: `/glw turf wand` gives an NBT-tagged `Turf.Wand_Item_Type` item (`command/TurfWandCommand.java`; hardcoded
English name/lore); `listener/WandListener.java` sets pos1/pos2 on left/right block click. Command path:
`/glw turf pos1`, `pos2` (`TurfPos1Command`, `TurfPos2Command`). Both write a per-admin in-memory
`selection/Selection` (`world, pos1, pos2, activeTurfId`) held by `selection/WandSelectionManager` (cleared on quit).
`/glw turf create <displayName...>` (`command/TurfCreateCommand.java`) takes X/Z from the two corners, rejects a
missing selection, cross-world corners and overlap (`TurfManager.findConflict`), allocates an id, stamps
`Settings.getTurfDefaultIncomeAmount()` (100.0), persists and auto-selects the new turf. Validation gaps: no min/max
area (TF-21), no duplicate-name check (TF-29), no adjacency, no per-world cap. A cross-world corner silently wipes both
corners in `Selection.set`, so `TURF_CREATE_FAIL_CROSS_WORLD` is effectively unreachable (TF-29).
`command/TurfSelectionResolver.java` resolves the target of most subcommands: `activeTurfId` -> turf the sender stands in
-> `TURF_NO_ACTIVE`.

Implication for a map: turfs are arbitrary rectangles, so a chunk grid cannot be "the data"; a map would have to
**sample** a grid through `findAt` (or a NEW spatial index in `TurfManager`) and draw region edges, or the plan must
decide to migrate to chunk-aligned regions (a breaking data change).

## 2. Capture loop

### 2.1 Cadence and inputs
`task/TurfLocationTracker.java` (`@Bean`, `TurfModuleConfig.turfLocationTracker`, `start()` at bean creation) runs at
1 Hz: for every online player it resolves `turfs.findAt(player.getLocation())`, fires `TurfExitEvent` /
`TurfEnterEvent` on transitions (worlds with no turfs short-circuit), then calls `CaptureService.tick(playerTurfCache)`.
It implements `Listener` with an `onQuit` but has no `@ListenerHandler` (TF-06, still true) so the cache entry of a
quitter lingers until the next tick's `Bukkit.getPlayer(uuid) == null` skip in `indexPlayersByTurf`.
`TurfContributionTickTask` performs its own independent per-player `findAt` scan at 1 Hz (duplicated work).

### 2.2 State machine (`capture/CaptureService.java`, 449 LOC)
`tick` iterates `turfs.getAll()`; per turf it builds `TickGroups{defenders, challengersByGang}` (`classify`): dead
players skipped, gangless players skipped, members of the owner gang **or gangs that `isAlly` the owner** count as
defenders, every other gang goes into `challengersByGang`. Ally check is one-directional (TF-24).

* **IDLE -> CONTESTING (`tickIdle`)**: needs `isCapturable(turf, now)` (cooldown elapsed AND owner offline > grace, or
  unclaimed, or owner record missing). Unclaimed: any gang member inside starts a CLAIM contest (challenger label =
  dominant gang or lowest id). Owned: needs `defenders == 0` and **exactly one** challenger gang (two rival gangs
  arriving together cancel each other, spec 2.3 "bystanders").
* **Owned contest (`tickContestingOwned`)**: `net = challengers - defenders` (empty room = `-1`), `delta = net * 100 /
  Duration_Seconds` (180) per second, clamp 0..100. 100 -> `complete`; 0 with no challengers -> `ABANDONED`; 0 with
  defenders > challengers -> `DEFENDED`. Only the registered challenger gang counts; a third gang on the turf neither helps
  nor hinders (TF-26, intentional). `Abandon_Grace_Seconds` is **not consulted** on this path (TF-12); an emptied contest
  just decays at the 1v0 rate.
* **Unclaimed contest (`tickContestingUnclaimed`)**: Phase 1 CLAIM (90 s) is **global** - any gang member inside fills
  it, rivals included, and it freezes (never decays) when empty, ended only by `Abandon_Grace_Seconds` (15 s). At 100 the
  dominant gang (strict majority, ties stall at 100) becomes challenger and the phase flips to CONSOLIDATE (90 s),
  re-firing `TurfCaptureStartEvent`. Phase 2 is a per-gang tug-of-war (capturing vs. all opposers aggregated, empty = -1);
  rolling to 0 reverts to CLAIM at 100 with a null challenger; 100 -> `complete`.
* **`complete`** sets owner + `lastCaptureTimestamp`, state COOLDOWN, persists, plays the sound to everyone inside, fires
  `TurfCapturedEvent` **only if the new owner gang still exists**, but writes `setOwnerGangId`/`persist` first (TF-01,
  verified at :395-408: a capture into a disbanded gang leaves a dangling owner and no cleanup event). Also unboxes a
  `@Nullable Integer` (TF-13).
* **`cancel`** resets state, plays the failed sound, fires `TurfCaptureFailedEvent(ABANDONED|DEFENDED)`
  (`CANCELLED` is never fired - TF-28).
* Milestones `Turf.Capture.Progress_Milestones` `[25,50,75]` fire `TurfCaptureProgressEvent` on upward crossings only.

### 2.3 `CaptureSettings` (capture/CaptureSettings.java) and the `Turf.Capture.*` keys
Immutable record built by `TurfModuleConfig.captureSettings()` from `Settings` (`gangland-api`, key block in
`gangland-impl/src/main/resources/settings.yml:637`): `Duration_Seconds 180`, `Unclaimed_Phase1_Seconds 90`,
`Unclaimed_Phase2_Seconds 90`, `Cooldown_Minutes 15`, `Abandon_Grace_Seconds 15`, `Post_Logoff_Protection_Minutes 10`,
`Inactivity_Auto_Release_Days 10`, `Enable_Sound`, `Broadcast_Globally`, `Progress_Milestones`, sounds
`Start/Complete/Failed/Tick/Unclaimed`. **No key exists for**: minimum attackers, minimum defenders online, per-gang turf
cap, max simultaneous contests, contest timeout, adjacency, or a reward/penalty scale.

### 2.4 Contribution points
`contribution/TurfContributionTickTask.java` (1 Hz): for each online player standing in a CONTESTING turf, +0.5
(`Defender_Presence_Tick`) to defender-gang members, +1.0 (`Attacker_Presence_Tick`) to the challenger gang; written via
`Member.increaseContribution` (gang module persists). `listener/contribution/TurfContributionListener.java`: +50
(`Capture_Complete_Bonus`) to present challenger members on `TurfCapturedEvent`, +25 (`Defense_Success_Bonus`) to present
owner members on `TurfCaptureFailedEvent` with reason `DEFENDED` only. Hardcoded `y < 0 || y > 319` filter excludes deep
players (TF-16, verified :64). **No consumer inside gangland-turf** (grep finds writes only); gang-module readers (display/ranking)
were not audited here.

### 2.5 Presence heartbeat, inactivity, admin set-owner
* `task/GangPresenceTracker.java`: 1-minute heartbeat stamps `Gang.lastMemberOnlineAt` for gangs with an online member;
  24 h `releaseTask` runs `task/InactivityReleaseTask.java`, whose first run is 24 h after boot (TF-09, still true), frees
  turfs whose owner is missing or whose `lastMemberOnlineAt > 0 && > Inactivity_Auto_Release_Days` ago. Fires **no**
  `TurfOwnerChangedEvent` (TF-10), so bars/sound/defenders are not touched.
* `task/GangPresenceListener.java` (`@ListenerHandler`, lives in `turf.task`, not `turf.listener`): stamps
  `lastMemberOnlineAt` on join, and on quit if that was the gang's last online member.
* `/glw turf setowner <gang|none>` (`command/TurfSetOwnerCommand.java`): sets/clears owner, `state.reset()`, zeroes
  `lastCaptureTimestamp`, persists, fires `TurfOwnerChangedEvent`. It does **not** fire `TurfCaptureFailedEvent`, so capture
  bars, deployed defenders and an engaged Quartermaster are not cleaned up (TF-04, verified).
* Cooldown display: `TurfState.COOLDOWN` is set at runtime by `complete`, not persisted; after a restart
  `/glw turf status` says IDLE while `isCapturable` still honours `lastCaptureTimestamp` (TF-27 is display-only).

## 3. Rewards and defence

### 3.1 Income
`task/TurfIncomeDistributor.java`: every `Turf.Income_Interval_Minutes` (10) each owned turf pays
`incomeAmount x INCOME_MULTIPLIER-buff product` (2 dp) into `gang.getEconomy()` (Keystone economy). **No online
requirement, no upkeep, no cap on stacked multipliers (TF-37), no message to anyone, no per-gang total anywhere.** Orphan
turfs are auto-released here too (also without an event, TF-10). Admin sets the amount with `/glw turf income <amount>`
(`TurfIncomeCommand`, admin-guarded). Income is a flat per-turf number chosen by an admin, not derived from size, buildings
or population.

### 3.2 Quartermaster and buffs
* `npc/TurfPowerupManager.java` (`BeanLifecycle`, the only lifecycle bean besides `PowerupRegistryLoader`): admin
  places one Quartermaster per turf (`/glw turf powerupnpc set|remove`, `command/TurfPowerupNpcCommand.java`, display name
  always `null`, TF-38). Spawned through `CivilianSpawnManager.spawnCivilian(loc, "quartermaster")`
  (`npc/TurfPowerupNpc.java`), Citizens metadata `gangland.turfpowerup.turfid` routes right-click
  (`listener/powerups/TurfPowerupInteractListener.java`, chunk-deferred respawn in `TurfPowerupChunkLoadListener`).
  `remove()` does a full `repository.loadAll()` (TF-33).
* Right-click -> `npc/config/TurfPowerupOpenContractImpl.java`: checks turf exists + owned + viewer has a gang + is owner or
  **ally**; **no rank/permission check** and hardcoded English deny strings (TF-23). Panels
  (`npc/view/TurfPowerupFlow`, `TurfPowerupMenuView`, `TurfPowerupBuffCatalogueView`, `TurfPowerupGarrisonView`,
  `TurfPowerupFlowSession`) run on Keystone `keystone-inventory`. The session caches turf and gangs at open time and never
  re-validates ownership on click (TF-19). The catalogue renders slots 9-17 only (TF-18).
* `powerups/PowerupRegistry` + `PowerupRegistryLoader` (reads `turf/turf_powerups.yml`), `PowerupDefinition`,
  `ActiveBuffManager` (1 Hz prune, per-buff `repository.delete`, TF-32), `ActiveTurfBuff`, `EffectType`
  (`INCOME_MULTIPLIER`, `CAPTURE_DEFENSE_BONUS`, `GARRISON_DISCOUNT`). Four shipped entries:
  `small_income_boost` 1.25x/1 h/5000, `large_income_boost` 1.75x/1 h/15000, `reinforced_defense` 1.0/30 min/8000,
  `garrison_discount` 0.8/30 min/2500. **Only `INCOME_MULTIPLIER` has a consumer** (`effectiveMultiplier` is called from
  `TurfIncomeDistributor` only; verified by grep). `CAPTURE_DEFENSE_BONUS` ("phantom defenders" in the tug-of-war) and
  `GARRISON_DISCOUNT` are purchasable and do nothing (TF-02); the per-defender price is a hardcoded `1500` constant in
  `TurfPowerupGarrisonView` (TF-17). `/glw turf buff <id>` grants any buff free (staff tool, TF-31).
* The buff economy is inflation-shaped: income boosts cost 5-15k for +25-75% of a 100-per-10-minutes base, so the payback is
  strongly negative at default income - it only makes sense if admins raise `incomeAmount` a lot. (Balance observation,
  not a code bug.)

### 3.3 Garrison and defenders
* `powerups/Garrison`, `powerups/GarrisonManager`: per-turf integer stock (`add/consume/count/remove`), persisted in
  `turf_garrison`. Bought 1 at a time (1500 each) from the Quartermaster panel; `/glw turf garrison [count]` is the staff
  override.
* `listener/powerups/GarrisonDeployListener.java`: on `TurfCaptureStartEvent` for an **owned** turf it (a) always engages
  the Quartermaster (`TurfPowerupManager.engage`, 32-block radius, targets nearest challenger-gang member) and (b)
  consumes the **entire** stock in one go and deploys it at `regionCentreSurface` (region centre, `getHighestBlockYAt+1`,
  which may be a roof) - TF-20. `TurfCapturedEvent`/`TurfCaptureFailedEvent` recall defenders and disengage.
* `npc/defender/TurfDefenderDeployer.java` + `TurfDefenderConfig` (`Type_Id turf_defender`, `Targeting_Radius 32`,
  `Lifespan_Seconds 600`, from `turf/turf_npcs.yml`): one `Group` per turf, 5-tick AI loop retargets the nearest live
  challenger member within `targetingRadius` of **the defender's own position** and forces `CivilianState.COMBAT`.
  `findOwningTurfId` is a linear scan per damage event (TF-35). Without Citizens, `NpcSupport.available()` false makes
  `deploy` a no-op **after** the stock was already consumed.
  Friendly-fire (owner gang + allies cannot hurt the NPCs) is `cops-n-crooks/.../listener/turf/TurfFriendlyFireListener.java`,
  the only outside consumer of turf classes (cops `Depends: [turf, civilians]`).
* `turf_defender` in `gangland-civilians/.../npc/civilians.yml:233`: PLAYER entity, 30 HP, iron armour, `weapon:rifle`
  (Bartizan weapon reference), `Faction: turf_defender`, `Combat.Difficulty NORMAL`, `Alert_Range 16`, retreat at 30 % HP,
  `Tactics.Formation_Arc 100`, wander off. `quartermaster` (line 282) is the same shape: 40 HP, `HARD`, faction
  `quartermaster`, formation arc 120.

**Do defenders use keystone-npc squads (H11-H13)? Indirectly yes, directly no.** The turf module never touches `NpcSquad`,
`NpcPursuit` or `NpcSquadSignal`. But `CivilianService implements FactionSquads`
(`gangland-civilians/.../npc/FactionSquads.java`, `CivilianService.squadFor`, :221), and `CivilianNpc`/
`CivilianCombatBehavior` call it when a civilian "entered combat another way (turf defender retarget)". So each defender
that `TurfDefenderDeployer.retarget` flips into COMBAT joins the shared squad keyed **(faction, target uuid)** and inherits
the formation arc, CONTACT recruitment and faction shouts (`SquadRadio`). Consequences and gaps:
1. The squad key is faction-wide, not turf-wide: two turfs' `turf_defender`s hunting the same player share one squad, and the
   Quartermaster (faction `quartermaster`) never squads with the defenders next to it.
2. No roles (medic/suppressor/etc. from H13), no callsigns, no health bars on turf NPCs; the turf module cannot set them
   because it only supplies a `typeId`.
3. No leash / return-to-post: retargeting is relative to the defender's own position, so an attacker who runs out of the turf
   drags the squad away until `Lifespan_Seconds`, contest end, or death. No reinforcement waves; no respawn during a contest.
4. NPC presence has **no effect on the capture maths** - only players count in `classify`. Defenders only threaten/kill
   attackers (a dead attacker is skipped by `player.isDead()`, which pauses the bar). `CAPTURE_DEFENSE_BONUS` was meant to
   bridge this and is unwired.
5. `NpcSquad`/`NpcPursuit` in Keystone 1.13.0 give squad state, reverse route planning and pursuit that a turf-aware
   "defence plan" (rally points, patrol routes along the boundary, spotter -> alert) could drive; nothing in turf uses them.
   Any such reuse should be additive `gangland-civilians` API (a NEW `TurfGarrisonPlan`-style contract), or new Keystone
   API; the turf module already `Depends: [civilians]`.

## 4. Feedback surface

| Class | Trigger | What the player sees |
|---|---|---|
| `task/TurfVisualization` | `/glw turf show` | Particle wire-frame (4 pillars + top/bottom rectangles), 0.5-block step, 1 s refresh, viewer Y +-10, default 30 s. Per-viewer runnable, no throttle (TF-21). The **only** in-world boundary cue, and it is command-triggered. |
| `listener/TurfActionBarListener` | `TurfEnterEvent` / `TurfExitEvent` | Title + subtitle (if `Show_Enter_Title`) and action bar; wording branches unclaimed / owned / contesting-claim / contesting-consolidate; exit action bar. |
| `listener/TurfPresenceBarListener` | enter/exit/captured/owner-changed | Persistent boss bar "Territory of X" while inside an **owned** turf; colour GREEN own, RED rival, YELLOW gangless viewer; unclaimed turfs get no bar. |
| `listener/TurfBossBarListener` (331 LOC) | contest start/progress/end, join | Dual boss bars while CONTESTING (unclaimed: claim bar + consolidate bar; owned: one bar); RED for defender gang, GREEN challenger, WHITE bystander. Viewers = anyone inside **plus every online member of the challenger and defender gangs wherever they are**. 1 Hz refresh task is scheduled in the constructor with no handle (TF-08). Tick sound on upward progress. Rebuilds on join for involved gangs. |
| `listener/TurfCaptureNotifier` | start / 50 % / captured | Chat warning to online defender-gang members at contest start and at the 50-75 bracket; global broadcast on capture (`Captured_Broadcast`, gated by `Broadcast_Globally`). **Failure is silent.** |
| `listener/TurfCaptureFeedbackListener` | `TurfEnterEvent` | Explains why a gang member entering a rival turf cannot start a capture: cooldown remaining, or owner-gang protection remaining. Silent otherwise. |
| `listener/TurfOwnerSoundListener` | `TurfOwnerChangedEvent(new=null)` | Owner-cleared SFX to players inside (never plays for auto-release, TF-10). |
| `listener/GangDisplayNameResolver` | helper | Null/blank-safe gang label. |
| Sounds | `config/GanglandTurfSounds` -> `TurfSoundContract` | start/complete/failed/tick/owner-cleared via Keystone `SoundEffect`. |
| Messages | `config/GanglandTurfMessages` -> `TurfMessageContract` | `TURF_*` paths (59 and "none in `message_es.yml`" per the 0.8.1 audit, not re-counted, TF-22) live in `gangland-api` `Messages` (legacy, per CLAUDE.md); several strings bypass the layer (wand lore, `TurfStatusCommand` phase suffix, all Quartermaster panels). |

Events (`events/`): `TurfEnterEvent`, `TurfExitEvent`, `TurfCaptureStartEvent(turf, challengerGang)`,
`TurfCaptureProgressEvent`, `TurfCapturedEvent(turf, oldOwner, newOwner)` (only when newOwner != null),
`TurfCaptureFailedEvent(turf, reason)`, `TurfOwnerChangedEvent(turf, oldId, newId)` (admin `setowner` only). There is **no**
event for: contest joined/left, defender arrived, income paid, turf created/deleted, buff bought, garrison deployed - so a
map/notification layer has only capture-level hooks today.

## 5. Commands, config, module descriptor

* **Commands: root `/glw turf` + 16 subcommands = 17 `commands.json` entries** (`resources/commands.json`): `wand, pos1,
  pos2, create, delete, setowner, list, info, show, status, select, tp, income, garrison, buff, powerupnpc`. Root
  (`command/TurfCommand.java`, `@CommandHandler`, console allowed): standing in a turf -> select + `renderInfo`; else the
  sender gang's owned turfs; console -> help. Admin guard `gangland.turf.admin` (`WandSelectionManager.ADMIN_PERMISSION`) is
  present in 10 of them (`buff, create, delete, garrison, income, pos1, pos2, powerupnpc, setowner, wand`); **`list, info,
  status, select, tp, show` have no admin guard** and depend purely on `gangland.command.turf.<sub>` being granted, so
  whichever server grants `turf.tp` lets a player teleport into any turf (and `list` shows every turf with a click-to-tp
  component). Two disjoint permission namespaces (TF-30). `TurfSelectionResolver` is the helper, not a command.
  No `rename` (TF-39), no player-facing "my turfs / nearest turf / where to attack" command, no `capture` command (capture is
  passive standing).
* **Config**: `settings.yml` `Turf:` block (line 637, read through `gangland-api` `Settings`); `turf/turf_powerups.yml`
  and `turf/turf_npcs.yml` ship in the module jar and are registered by `TurfModuleFileConfig`/`TurfModuleFiles`.
  `turf_npcs.yml` says "hot-reloadable" but `TurfNpcsConfigLoader.load()` runs only from its constructor (TF-36); none of the
  turf task beans are `BeanLifecycle`, so `/glw reload` re-reads no `Turf.*` value and cancels no task (TF-07).
  Per CLAUDE.md, new module knobs belong in the module's own YAML, not in `Settings`/`Messages`.
* **`module.yml`**: `Id: turf`, `Host_Api: 2.0`, **`Depends: [civilians, gang]`**, no `Plugins:`, artifact
  `org.luckyraven:gangland-turf`. (CLAUDE.md's module table still says `Depends: [civilians]` and `Host_Api: 1.0`; the
  descriptor is the truth.) **Why `civilians`**: `TurfModuleConfig` constructor-injects `CivilianService` and
  `CivilianSpawnManager` into `TurfDefenderDeployer` and `TurfPowerupManager` because the Quartermaster and every defender
  are `CivilianNpc`s (`TurfPowerupNpc.spawn`, `TurfDefenderDeployer.deploy` call `spawnManager.spawnCivilian`); their entity
  types, gear, health and AI live in `civilians.yml`. **Why `gang`**: `GangLookupContract` / `Gang` / `Member` are in
  `gangland-features/gangland-gang` now. Citizens is a soft gate (`TurfModule.onEnabled` reports
  `NpcSupport.FAULT_CITIZENS_MISSING`); Bartizan is reached only indirectly (defender/Quartermaster `Weapon_Pool: weapon:rifle`); neither turf nor
  civilians declares `Plugins: [Bartizan]` in `module.yml` at 0.12.0 (CLAUDE.md's older "civilians needs Bartizan" is stale),
  so turf loads on a Bartizan-less server and its NPCs simply hold no weapon (verify at smoke time).
* **Tests** (audit said none): 26 test files under `gangland-turf/src/test`, notably `CaptureService{Owned,Unclaimed,
  StartAndComplete,Helpers}Test`, `TurfManagerTest`, `CuboidRegionTest`, `TurfDefenderDeployerTest`, `ActiveBuffManagerTest`,
  `GarrisonManagerTest`, `TurfPowerupManagerTest`, `TurfDeleteCommandTest`. Several pin today's wrong behaviour (docket
  "pins; flip when fixed": TF-01, TF-05, TF-12).

## 6. Player-journey friction

| Question | What actually happens today | Verdict |
|---|---|---|
| How does a gang discover turfs? | Stumble into one (enter title + action bar), or `/glw turf list` (all turfs, no distance/direction/state/yield, hover for bounds) with a tp link; `/glw turf show` draws an outline. No map, compass, waypoint, or "nearest unclaimed". | Opaque. This is the gap the Factions-style `/f map` would fill. |
| Who owns what? | Enter title/subtitle, presence boss bar on owned turfs only, `info` (owner, raw bounds, income, state). No overview of the whole territory; no gang colour (`Gang.color` exists in `Gang.java` but bars use fixed BarColors). | Local only, no global picture. |
| What does a turf yield? | `info` shows income per interval; buffs/garrison/Quartermaster location are not shown; payouts are silent; no per-gang income total; income is an admin-set flat number. | Yield is invisible after the fact. |
| How to start a capture? | Walk in and stand there; the only text is a blocked-reason on entry (cooldown / owner protected). Owned turf requires zero defenders inside, one challenger gang, owner gang offline > 10 min. There is no "capture" verb, no objective/flag inside the region, no capture time estimate shown up front. | Natural for unclaimed; owned turfs are effectively offline-raid-only and the rule is only explained by the blocked message. |
| Told it is under attack? | Online defender-gang members get chat lines (start, 50 %) and boss bars wherever they are; on login bars rebuild. But start requires the owner gang offline, so at contest start typically nobody is online. No mail, no login summary, no result message for the losing gang except the global broadcast (if enabled). | Broken for its main case. |
| How to defend? | Pre-buy defenders (1500 each, one at a time) and buffs from the Quartermaster (admin-placed; any gang member or ally may spend the bank); log in mid-contest and stand in the zone (counts as defenders, pushes progress back); online presence before the raid blocks the raid entirely. Garrison is all-or-nothing, spawns at one point. Two of four buffs do nothing. | Offline defence is the real game; it is shallow and partly non-functional. |
| Limits | No per-gang turf cap, no min gang size/online count to attack, no adjacency/front-line (a gang can hit any turf anywhere), a gang can run any number of simultaneous contests, one contest per turf (challenger label + "3rd gang ignored" on owned), 15 min cooldown after capture, restart wipes in-flight contests, allies count as defenders, dead players skipped. Solo 1-member gangs can hold unlimited turfs. | Wide open; nothing bounds snowballing. |
| Admin experience | Wand + `create`, but no min/max size, no rename, two permission namespaces, `/glw turf list/tp` open to non-admins by omission. | Serviceable, rough edges. |

## 7. Audit currency: what changed since `workflow-audit-10-turf.md` (0.8.1)

Re-verified against source at 0.12.0.
* **Moved/renamed**: the whole feature is now module `gangland-turf` (`turf.*`); the Quartermaster/defender code and
  `turf_npcs.yml` moved in from cops-n-crooks and `gangland-impl` (group I, T-I3..T-I6); `TurfNpcContract` bridge deleted;
  `Gang`/`GangLookupContract` now in `gangland-gang`; only `TurfFriendlyFireListener` remains in cops-n-crooks. Commands are 16
  subs (not 18 classes), tests exist (26), `Host_Api 2.0`, `Depends: [civilians, gang]`.
* **Fixed since**: GI-34/TF-03 partial - `TurfDeleteCommand` now cascades `garrisons.remove`, `buffs.removeAll`,
  `powerupNpcs.remove` (so TF-05 id reuse no longer inherits child rows), but `TurfManager.delete` still does not cancel the
  contest or fire `TurfCaptureFailedEvent`, so boss bars and defenders still leak. GI-35 - ten subcommands now check
  `gangland.turf.admin`. GI-82 - `activeBuffManager` no longer double-schedules its prune task.
* **Still open at 0.12.0 (verified in code)**: TF-01, 02, 04, 06, 07, 08, 09, 10, 11, 12, 13, 16, 17, 18, 19, 20, 23,
  24, 27, 28, 29, 30, 33, 36, 37, 38, 39. Live docket status is in the artifact's `bugs` collection and was not read in this
  census; rows never written default to `open`.
* **New since the audit, not in the docket**: (a) `settings.yml` comment and `CapturePhase` javadoc promise the Phase-1
  steal (same cause as TF-11, recorded there); (b) `TurfContributionTickTask` duplicates the tracker's per-player `findAt`;
  (c) `GarrisonDeployListener` consumes the stock before `deploy` may no-op on a Citizens-less server (stock burned for
  nothing); (d) rank permissions `gangland.turf.capture|contribute|upgrade` have no production consumer; (e) defender squads
  key on faction, not turf (see 3.3). Add to `triage/turf.txt` if the plan adopts them.

## 8. Ideas from this census for the plan (inputs, not decisions)

* **Map**: a `TurfMapService` (NEW) that samples a chunk-aligned grid via a NEW `TurfManager.ownerAt(world, cx, cz)` /
  spatial index, rendering (a) a chat chunk-grid keyed by owner/unclaimed/contested/cooldown (Factions `/f map` style, own
  gang = green, ally = blue, enemy = red, contested = flashing/bold), (b) optionally a map-item or GUI rendering. Because
  regions are rectangles, chat cells will show region membership, not chunk ownership; decide early whether the model stays
  rectangle-based (map is a lens) or becomes chunk-claim-based (migration + `TurfTable` change). Pure read-side, no module
  coupling: fits entirely inside `gangland-turf`.
* **Make raids reachable**: reconcile "owner must be offline 10+ min" with "defend your turf" - options include a
  war-declaration window, an owner-online defender bonus instead of full immunity, or a minimum-attackers/online-defenders
  balance. Whatever is chosen must include an offline notification path (mail module hook via additive `gangland-api`
  surface, or `gangland-mail` contribution) and a "while you were away" digest on login.
* **Wire the dead hooks first**: `CAPTURE_DEFENSE_BONUS` (phantom defenders in `classify`), `GARRISON_DISCOUNT`, garrison
  cost config, Phase-1 steal, rank permissions, contribution-as-currency - all low-risk, high-clarity fixes that make the
  existing text true.
* **From cops-n-crooks H11-H13**: garrison as a `NpcSquad` with roles (medic, suppressor), callsigns and radio lines
  (`SquadRadio`), health bars, patrol/hold-position with a leash to the region, incremental deploy with reinforcement waves
  and stuck recycling; these live in civilians/keystone-npc, so the turf side only needs a per-turf defence-plan contract
  passed to `spawnCivilian` (additive civilians API; new Keystone API if pursuit/patrol primitives are missing).
* **Events**: add income-paid, contest-joined, defender-arrived and turf-created/deleted events so the map, digest and
  scoreboard can subscribe instead of polling.
