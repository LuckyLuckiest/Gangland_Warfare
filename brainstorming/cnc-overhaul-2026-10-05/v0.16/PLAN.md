# PLAN - Cops N Crooks 0.16 "Where they come from" (Gangland 0.16.0, gangland-api 2.3, Keystone 1.15.0, Bartizan pin 0.6.0)

Built on 0.15.2 (Drop_Mode AUTO). Every worker reads `CONSTRAINTS.md` + `CONTRACTS.md` + its own task section only.
21 tasks: 4 implementation waves + 2 release waves. Two repos: Keystone (Tasks 1, 2, 6) and Gangland (the rest).
Orchestrator, before W1: merge `0.15.2` into master, create the Gangland integration worktree `E:/Programming/java/wt/gangland-0.16.0`
(branch `0.16.0`) and the Keystone integration worktree `E:/Programming/java/wt/keystone-1.15.0` (branch `phase-h14-dispatch-posts`),
refresh graphify. Per wave: one lane worktree per task (CONSTRAINTS "Where"), merge every lane into the integration branch, run
`mvn -q clean install` + full `mvn test` there, save reports, file reported triage rows, refresh graphify, start the next wave.
W1 starts only on a frozen 0.15.2: merged into master with no later 0.15.2 commits (its HEAD at review 2 was 604e72de); T5's
lane re-reads `ChaseArcs.view` there before writing (CONTRACTS C7).
Owner checks: ANSWERED 2026-10-07 (`OWNER-RULINGS.md`, which overrides `DECISIONS.md` where they differ), so W1 is not blocked
on them. D14 ALLOW (api 2.3 carries CONTACT, eleven Settings getters and eight Messages constants, the D19 shield included), D15
admin regions in cops-n-crooks, D16 no-track counts as unseen, D19 the ALTERNATIVE (hospital respawn shield,
`User.Death.Hospital.Shield_Seconds` 5: T3 adds the key/getter/message, T8 the behaviour), every other D recommended (D23 by
default: OWNER-RULINGS does not name it). The tasks below already carry these answers.
- Gate before W3: Task 6 has installed Keystone 1.15.0 into `~/.m2` and its Gangland `<keystone.version>` bump is merged.
- Lane prompts name the lane's model so its commit trailer is right (CONSTRAINTS "Ownership and commits").
Release checklist (orchestrator, after W6 merges): docket rows updated for every fixed/filed id the reports name (SPEC section 7),
then `graphify update . --force` from the main checkout.

## Wave diagram
```
W1 foundations (parallel, no cross-compile)
  T1 KS out-of-sight helper   T2 KS hold-post+leash   T3 api 2.3 + core keys/messages + versions
  T4 cop config + tiers + radio lines                  T5 chase config + risk-11 rampage fix
        |                         |                          |
W2 registries + core features (needs W1)
  T6 Keystone 1.15.0 release + Gangland pin + floor guard (T1,T2)   T7 stations/regions/points + RegionProvider + T-180 id floor (T3,T4)
  T8 hospital + one bill + respawn shield + money drop (T3)          T9 self-defence + takedown guards (T3)
  T10 crooked contacts: phone + sign gate (T3)
        |
W3 police from real places (needs W2 + Keystone 1.15.0 installed)
  T11 dispatch queue + stations + mixed tiers + breather (opus; T4,T6,T7)   T12 setup wand (T7)
  T13 bribe stars (T3,T5,T7)   T14 turf RegionProvider (T3)   T15 containment perimeter (T4,T6,T7)
        |
W4 evasion (needs W3)
  T16 hideouts + cold trail + en-route hold on the evasion clock (T5,T7,T11,T14,T15)   T17 pursuit hand-off (T11,T15)
  T18 acceptance scenarios + profiles (authoring; reads W1-W3 reports)
        |
W5 docs (needs W4)
  T19 docs + commands/help audit + migration (its commands.json fixes land here)
        |
W6 release (needs T19 merged, so the accepted build is the shipped one)
  T20 CHANGELOG .md + .bbcode.txt (T19)   T21 acceptance run + smoke + report (T18, T19)
```
Note: T15 calls T7's `CopRadio.placeOf`; T13 uses T3's `WantedCause.CONTACT`; both list them.
Note: T13's unseen gate reads `CopManager.groupOf` and the squad's `millisSinceSighting`, which already exist on 0.15.2; it
never edits CopManager (T11's file in W3) and needs nothing T11 adds.

## File ownership per wave (a file appears once per wave; "new" = created)
Abbreviations as CONTRACTS.md; `CNCT` = cops-n-crooks test root, `KST` = `keystone-npc/src/test/java/org/luckyraven/keystone/npc`,
`MAIN` = `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]` (untracked brainstorming paths), `CIV` = gangland-civilians
main root (CONTRACTS.md abbreviations); the civilians test root mirrors it under `src/test/java`.

W1
| Task | Files |
|---|---|
| T1 | KS/entity/EntitySpawner.java, KST/entity/EntitySpawnerTest.java |
| T2 | KS/NpcPost.java (new), KS/AbstractNpc.java, KST/NpcPostTest.java (new), KST/AbstractNpcPostTest.java (new) |
| T3 | API/data/region/{RegionShape,PlaceRegion,RegionProvider,PlaceNames}.java (new), API/data/teleportation/Waypoint.java, CORE/wanted/WantedCause.java, API/GanglandApi.java, API/file/configuration/{Settings,Messages}.java, IMPLR/settings.yml, IMPLR/message/message_{en,es}.yml, IMPL/config/DataConfig.java, root pom.xml (`<revision>`), cops-n-crooks + gangland-turf module.yml, api tests {RegionShapeTest,PlaceRegionTest,PlaceNamesTest} (new), impl SettingsTest, MessagesTest, HolderSeamBeanTypeTest (only if it enumerates holder beans) |
| T4 | CNCR/copsncrooks/{cops,cop_roles,cop_radio_messages,cop_radio_messages_es}.yml, CNC/npc/police/config/{CopConfigProvider,YamlCopConfigProvider,CopRole}.java, CNC/npc/police/config/{DispatchSettings,BreatherSettings,HandoffSettings,PerimeterSettings}.java (new), CNC/npc/police/radio/{CopRadio,CopRadioMessages}.java, tests YamlCopConfigProviderTest, CopRolesFileTest, CopRoleTest, CopRadioMessagesTest, CopRadioTest |
| T5 | CNCR/copsncrooks/wanted.yml, CNC/wanted/config/{EvasionSettings,ChaseConfig,AutoSettings}.java, CNC/wanted/config/{HideoutSettings,QuietSpeedSettings,BribeStarSettings}.java (new), CNC/wanted/evasion/ChaseArcs.java (the `view` overload), CNC/wanted/evasion/EvasionClock.java (the one `arcs.view(` call in `views`, find by name), tests ChaseConfigTest, ChaseArcsTest, EvasionClockTest (the one `verify(arcs, never()).view(...)` line, find by text) |

W2
| Task | Files |
|---|---|
| T6 | Keystone: pom.xml (`<revision>`), docs/phase-h14-dispatch-posts.md (new), docs/keystone-npc.md; Gangland: root pom.xml (`<keystone.version>`), IMPL/Gangland.java (`onEnable` guard only), IMPL/util/KeystoneFloor.java (new), IMPLR/keystone-floor.properties (new), test KeystoneFloorTest (new) |
| T7 | CNC/station/{Station,StationRegistry}.java, CNC/place/{AdminRegion,AdminRegionRegistry,SetupPoint,SetupPointRegistry}.java, CNC/database/{StationTable,StationRepository,AdminRegionTable,AdminRegionRepository,SetupPointTable,SetupPointRepository}.java (all new), CNC/database/{CopSpawnerTable,CopSpawnerRepository}.java, CNC/npc/police/spawn/{CopSpawner,CopSpawnManager}.java, CNC/command/cops/spawner/{CopSpawnerCommand,CopSpawnerSetCommand}.java, CNC/command/cops/CopCommand.java (constructor plumbing only), CNC/npc/police/radio/CopRadio.java, CNC/config/{RegistryModuleConfig (new),CopsNCrooksModuleConfig}.java, CNC/CopsNCrooksModule.java, CNC/database/JailRepository.java (T-180 id floor only), CIV/database/CivilianSpawnerRepository.java + CIV/npc/spawn/CivilianSpawnManager.java (T-180 id floor only; gangland-civilians, no other W2 owner), tests (new) StationRegistryTest, AdminRegionRegistryTest, SetupPointRegistryTest, CopSpawnerRepositoryStationTest, CopSpawnManagerStationTest, CopSpawnerRepositoryTest, JailRepositoryTest, CivilianSpawnerRepositoryTest (civilians test root); CopRadioTest, CopsNCrooksModuleTest (the configuration list) |
| T8 | IMPL/listener/player/{PlayerDeathListener,CustomPlayerDeathListener,HospitalShieldListener (new)}.java, IMPL/data/teleportation/{WaypointManager,WaypointRegionProvider (new),HospitalShield (new)}.java, IMPL/database/repositories/waypoint/WaypointRepository.java, IMPL/config/DataConfig.java, IMPLR/items/money.yml, CNC/listener/police/DetainmentListener.java, CNC/detainment/intake/JailIntakeService.java (the `admit(Player, boolean)` overload only), tests JailIntakeServiceTest (one case), PlayerDeathListenerTest, CustomPlayerDeathListenerHospitalTest (new), WaypointManagerNearestTest (new), WaypointRegionProviderTest (new), WaypointRepositoryTypeTest (new), BundledMoneyYmlTest (new), DetainmentListenerUndownedTest (new), HospitalShieldTest (new), HospitalShieldListenerTest (new), CustomPlayerDeathListenerQuitTest (constructor call site only) |
| T9 | IMPL/listener/player/EntityDamageListener.java, EntityDamageListenerTest |
| T10 | IMPL/data/wanted/ContactDesk.java (new), IMPL/listener/wanted/EvasionStateListener.java (new), IMPL/command/sub/contact/ContactCommand.java (new), IMPL/sign/aspect/WantedAspect.java, IMPL/sign/type/WantedSign.java, IMPL/sign/SignManager.java, IMPL/config/{GameplayConfig,WiringConfig}.java, IMPL/data/placeholder/worker/GanglandPlaceholder.java, IMPLR/inventory/{phone,phone_contacts (new)}.yml, IMPLR/commands.json, IMPLR/plugin.yml (only if the permission needs a default), tests ContactDeskTest, ContactCommandTest, EvasionStateListenerTest (new), GanglandPlaceholderTest, WiringConfigTest (call sites only), PhoneContactsPageTest (new), WantedAspectTest, SignManager*Test (constructor call sites only) |

W3
| Task | Files |
|---|---|
| T11 | CNC/npc/police/{CopManager,CopGroup}.java, CNC/npc/police/spawn/CopSpawnManager.java (no constructor change), CNC/npc/police/dispatch/{PendingUnit,SpawnBias,Dispatcher}.java (new), CNC/listener/detainment/CopListener.java, CNC/config/CopsNCrooksModuleConfig.java, tests CopManagerFixture, CopManagerSquadTest, CopManagerStuckTest, CopGroupSquadTest, CopSpawnManagerFallbackTest, DispatcherTest (new), CopManagerDispatchTest (new), CopSpawnManagerUnitTest (new), CopListener tests (CopListenerDeathTest, CopListenerShieldTest: call sites only) |
| T12 | CNC/setup/** (new), CNC/listener/setup/SetupWandListener.java (new), CNC/command/cops/setup/** (new), CNC/command/cops/CopCommand.java, CNCR/commands.json, CNCR/copsncrooks/setup.yml (new), CNC/config/{RegistryModuleConfig,CopsNCrooksFileConfig,CopsNCrooksYamlConfig}.java (Yaml: `FILES` += "setup"), tests (new) SetupSelectionTest, SetupSaveCommandTest, SetupCommandsTest, SetupOutlineTest, SetupWandListenerTest |
| T13 | CNC/wanted/bribe/BribeStars.java (new), CNC/config/ChaseModuleConfig.java, CNCR/copsncrooks/wanted_messages.yml, CNC/wanted/WantedMessages.java, tests BribeStarsTest (new), WantedMessagesTest |
| T14 | TURF/place/TurfRegionProvider.java (new), TURF/TurfModuleConfig.java, test TurfRegionProviderTest (new) |
| T15 | CNC/npc/police/state/{CopState,CopBehaviorFactory}.java, CNC/npc/police/state/behavior/PostedBehavior.java (new), CNC/npc/police/perimeter/{PerimeterController,PostRing}.java (new), CNC/listener/police/PerimeterListener.java (new), CNC/config/EvasionModuleConfig.java, tests (new) PerimeterControllerTest, PostRingTest, PostedBehaviorTest, PerimeterListenerTest; EvasionModuleConfigTest |

W4
| Task | Files |
|---|---|
| T16 | CNC/wanted/evasion/{EvasionClock,ChaseArcs,ChaseArc}.java, CNC/wanted/evasion/{Hideouts,QuietTrail}.java (new), CNC/config/EvasionModuleConfig.java, tests EvasionClockTest, ChaseArcsTest, CopManagerStuckTest (one added case), EvasionModuleConfigTest, HideoutsTest (new), QuietTrailTest (new) |
| T17 | CNC/npc/police/handoff/HandoffController.java (new), CNC/config/CopsNCrooksModuleConfig.java, tests HandoffControllerTest (new), CNCT/npc/police/CopManagerHandoffTest.java (new; the package of the package-private CopManagerFixture) |
| T18 | MAIN/brainstorming/cnc-overhaul-2026-10-05/acceptance/{gen-cnc016.js,prep-cnc016.sh,run-all-cnc016.sh,cnc016-verdict.js,scenarios/cnc016-*.json} (new, untracked), MAIN/brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json |

W5
| Task | Files |
|---|---|
| T19 | documentation/migration-0.16.0.md (new), documentation/features/{cops-n-crooks,wanted-bounty,waypoints}.md, documentation/developer/{cops-n-crooks,configuration,commands}.md, documentation/{gangland-api,module-loader}.md, documentation/tests/features/{cops-n-crooks,wanted-bounty,waypoints}.md (all three exist; extend them); commands.json fixes only if the audit finds a gap (report first) |

W6
| Task | Files |
|---|---|
| T20 | documentation/v0.16.0/CHANGELOG.md, documentation/v0.16.0/CHANGELOG.bbcode.txt (new) |
| T21 | MAIN/brainstorming/cnc-overhaul-2026-10-05/acceptance/runs/cnc016-** (new, untracked), MAIN/brainstorming/cnc-overhaul-2026-10-05/v0.16/ACCEPTANCE.md (new) |

Sequenced shared files (one owner per wave): CopSpawnManager T7 -> T11; CopCommand T7 -> T12; CopRadio + CopRadioTest T4 -> T7;
CopsNCrooksModuleConfig T7 -> T11 -> T17; RegistryModuleConfig T7 -> T12; EvasionModuleConfig + EvasionModuleConfigTest T15 -> T16;
DataConfig T3 -> T8; root pom.xml T3 -> T6; EvasionSettings/ChaseConfig/wanted.yml T5 only; ChaseArcs + ChaseArcsTest T5 -> T16;
EvasionClock + EvasionClockTest T5 -> T16; CopManagerStuckTest T11 -> T16; CopManagerFixture T11 only (package-private; T17's end-to-end test lives in its package, npc.police);
DetainmentListener + JailIntakeService T8 only; HospitalShield + HospitalShieldListener T8 only (EntityDamageListener stays T9's);
JailRepository, CivilianSpawnerRepository, CivilianSpawnManager T7 only (T-180); CopsNCrooksYamlConfig T12 only;
cops.yml T4 only; commands.json (cops) T12 only.

## Risks
1. 0.15.2 is not on master yet (branch `0.15.2`, HEAD 604e72de at review 2). W1 cannot start until it merges, frozen; T5 and T16 depend on its
   class shapes (ChaseArcs, AutoDrop.ChaseView, the EvasionClock constructor). The graph is refreshed after that merge.
2. Keystone gate: W3 lanes branch from the integration branch after the W2 merge, which already carries T6's `<keystone.version>`
   1.15.0, so ALL of W3 waits for T6's install (no lane builds on the 1.14.0 pin).
3. Host_Api 2.3 on cops-n-crooks and gangland-turf makes those jars refuse a 2.2 host; they ship with 0.16.0 (fine). A HOSPITAL
   waypoint row breaks a downgrade's waypoint load today; T8 makes unknown types skip with a warning (forward-safe from 0.16.0 on).
4. CopManager/CopGroup are the hot files: T11 rewrites `spawnTick`; T16 and T17 only call the seams T11 adds (C8). The legacy
   instant path stays reachable (Dispatch/Breather disabled) so the 0.12-0.15 squad tests keep pinning it.
5. Out-of-sight is a preference: in a closed room every ring spot may be visible or invalid, so a unit can still appear in view
   (step 4 of C8). The ray test sees through glass and leaves (rayTraceBlocks ignorePassable only skips passable blocks).
6. Perimeter posts sit at most 32 blocks out (`0.8 x Sight_Range`, R23) so they can see the centre; a suspect who leaves the zone
   far from both posts still slips through, which is the card's "slip out between the posts". Acceptance S5 measures arrival.
7. Bill at respawn is memory-only: a server crash while downed loses the bill (player-favourable). Contacts cooldowns, bribe-star
   timers and the hospital respawn shield are memory-only too (a restart resets them).
8. Mockito returns null for new provider getters; every caller null-guards to DEFAULT, and the CopManager fixture stubs Dispatch and
   Breather DISABLED for legacy tests (T11).
9. Floating bribe-star items: hoppers or other plugins could still collect them; the proximity check, `setPersistent(false)` and a
   respawn-on-death rule keep them from duplicating across restarts.
10. EntityDamageListener and the death listeners are also Gang & Turf's files (0.14 rebases on this): keep diffs inside the named methods.
11. Unseen gate depends on cops-n-crooks firing `WantedEvasionStateEvent`; with Evasion.Enable false or no cops module, the sign and
    the phone never refuse for sight (they still honour cooldown and price).
12. Keystone floor: cops-n-crooks 0.16 calls Keystone 1.15.0 members (`findHiddenSpawnLocation`, `holdPost`, `tickPost`) from AI
    ticks; on an older Keystone they would throw `NoSuchMethodError` every tick (and a module bean body calling one would abort
    `Gangland.onEnable`, docket KS-MO-07, open). T6's floor guard refuses to enable Gangland on a Keystone below the pin; the
    migration note says Keystone 1.15.0 is required; T21 checks the guard's message on a 1.14.0 boot.
13. Accepted escapes (Ruling R46), each with an acceptance row or a docs line: any death still resets all stars (suicide escape,
    priced by the ward bill; D17); hospitals can be camped once the 5 s respawn shield runs out or the player attacks (D19, owner chose the shield); vehicles and ender pearls outrun posts (0.19
    roadblocks / 0.20 mounted units); waypoint teleports are governed by the existing waypoint rules and 0.15.2's teleported flag; a
    logout costs nothing beyond 0.15.2's quits lock, but no longer escapes (stars persist, the clock holds until a unit arrives,
    S16).

## Rulings
- Ruling R1: 0.16.0 is a new branch `0.16.0` from master after 0.15.2 merges; root `<revision>` 0.16.0 - owner ruling (patch-bumped branch per wave) - none.
- Ruling R2 (OWNER CHECK, DECISIONS D14: answered ALLOW 2026-10-07): api 2.3 = the three named surfaces (RegionProvider + PlaceRegion/RegionShape, PlaceNames, WaypointType.HOSPITAL) plus the seams the cards force: WantedCause.CONTACT (core, re-exported), eleven Settings getters and eight Messages constants for core features (the D19 hospital shield's getter and message included; owner ALLOWED D14 2026-10-07) - the bump is one minor whatever it holds and every item is additive; "exactly three" was the page's headline - none (additive), but T19 lists every 2.3 member in gangland-api.md.
- Ruling R3: no `GanglandApi.regions()` accessor - the GanglandApi class javadoc's "Exactly four accessors" (final ruling R7 of the module-API wave, not this plan's R7); modules get the PlaceNames bean by injection - an external plugin wanting places waits for 2.4.
- Ruling R4: `PlaceNames.locate(Location)` is an instance method of a core holder bean with explicit `register(provider)`, not a static and not a `getAllInstances` scan - the GangMembership/BankTiers pattern, testable, no load-order cache - each provider must register itself (three do).
- Ruling R5 (OWNER CHECK, DECISIONS D15: owner confirmed 2026-10-07): admin regions (district, hideout, restricted, breaker) live in cops-n-crooks (`cop_region`), exposed through RegionProvider, not in gangland-core as the page says - the cops wand is the only writer; a core registry would need an api write surface plus an impl table, and readers only ever see RegionProvider/PlaceNames, so moving the owner later is invisible to them - one table migration if a non-cops module must write districts.
- Ruling R6: cops-n-crooks keeps `Depends: [turf, civilians]` and its four turf imports - they read capture state and owners, not geography; RegionProvider cannot replace them - "cops never depend on turf" stays untrue until a separate decoupling (map drift 2).
- Ruling R7: turf contributes `TurfRegionProvider` and declares `Host_Api: 2.3` - it implements a 2.3 type - a turf jar refuses a 2.2 host.
- Ruling R8: shapes are block-inclusive int cuboids (Y may be unbounded) or spheres; PlaceNames ranks by horizontal footprint, smallest first - turfs are X/Z columns and the wand picks block corners - a tall narrow region outranks a wide flat one.
- Ruling R9: a hideout is a region tagged `hideout` whose owner is -1 or the player's gang; owned turfs and GANG waypoints (their radius, or 8 blocks when 0) contribute; "gang safehouses" do not exist and are not built - spec card + map drift 4 - a gang without waypoints or turf has only admin hideouts.
- Ruling R10: a hideout only speeds the clock when it is not the hideout that holds the search centre (the last sighting) - "they only count if you arrive unseen" - a player seen walking in gets nothing until he is seen outside and slips back in.
- Ruling R11: progress speed = zone x hideout x quiet, capped at `Max_Speed` 4.0; AUTO's need is untouched - spec section 6 - a server raising both multipliers hits the cap.
- Ruling R12: cold-trail quiet time runs from the last heat-ledger crime ON THIS CHASE (the `view` on-chase rule: at or after the chase's begin minus Opening_Seconds), or from the chase start when the chase has none (a stale sub-threshold ledger crime from before the chase never anchors it) - "only heat-ledger crimes count (stars from signs and commands do not)" - a sign-raised chase gets cold-trail speed from its first minute; a kill-combo star raise (`EntityDamageListener.onKillComboWantedTrigger`) publishes no crime and does not reset quiet either (the player kills that build the combo do); offline time is never quiet (C10).
- Ruling R13: cold trail holds backup through a CopGroup flag checked by `requestBackup` and `grantRegroupBackup`, only while no cop has a fresh sighting; granted backup expires normally; the base squad and the breather refill are never skipped - spec guards - none.
- Ruling R14: a station is a `cop_station` row (id, name, anchor, nullable jail id); spawners join through an additive `cop_spawner.station_id`; the district is resolved live through PlaceNames, never stored - a stored id goes stale when a district is removed - a station outside every district radios the unknown-place word.
- Ruling R15: every dispatched unit waits in a per-group queue; the station is the nearest in the world (a biased hand-off prefers one ahead); ETA = clamp(distance / Unit_Speed, 0, 40) s; no station in the world = ring at ETA 0 with `fromStation=false` - spec + the 0.20 mounted-unit note - a world without stations behaves as 0.15.
- Ruling R16: arrival spot order: the station's own spawners in range and out of sight, a hidden ring spot on the station side, any hidden ring spot, then today's `spawnNearPlayer` - a cop in view beats no cop - units can still appear in view in tight spaces.
- Ruling R17: dispatch radio names a count ("2 units en route from Northside Station, ETA 20 s"), not unit numbers - badges are Citizens ids assigned at spawn - the line differs from the page's "Units 4 and 7".
- Ruling R18: tier mixing uses `"Role@tier"` composition entries; the bundled cop_roles.yml mixes stars 3-5; old files keep one tier per star - per-slot tier is the smallest change to the composition list - admins must edit their file to get the mix.
- Ruling R19: a full wipe = no counted cop left and a casualty within 10 s; the refill arrives at now + breather + ETA; breather [15,13,10,8,6] s by stars - spec "15 s at one star down to 6 at five" - a squad that wanders off without dying gets no breather.
- Ruling R20: units en route (`CopGroup.unitsEnRoute`: pending, at most 10 s overdue) count as a live cop for evasion OWNERSHIP, so the Repeating_Timer cannot drop a star; but while no cop of the group stands in the world the clock HOLDS (no search, no progress) until the first unit spawns - spec section 6 recommendation, made safe: counting queued units as live without the hold let a 1-2 star crime far from a station decay to 0 before any cop existed (review) - a far crime "buys you time" as a head start, never as a free star drop; units stuck 10 s past their ETA release ownership (0.15 behaviour).
- Ruling R21 (DECISIONS D5): a RESTORE start skips the crime-scene seed and adds `Rejoin_Grace_Seconds` 15 to the first units; the clock holds through the grace and the ETA (R20); the 0.15.2 logout lock (quits = 1) is untouched - owner decision W3 "quitting is never the best way out" - a rejoining player keeps his stars until a unit arrives and the search starts.
- Ruling R22: the perimeter starts on `WantedEvasionStateEvent` SEARCHING (the clock losing sight), not Keystone CONTACT_LOST - the same moment, already an api event, testable; CONTACT_LOST also needs a member that looked and failed - no perimeter with Evasion.Enable false.
- Ruling R23: posts are a new `CopState.POSTED` driven by Keystone `NpcPost`; up to 2 posts (Marksman, Defender first), never the last free cop; ring at `min(zone radius, 0.8 x Sight_Range)` (DECISIONS D18: the card says "a ring of the evasion zone radius", but at 90-180 blocks a post with Sight_Range 40 never sees a suspect near the centre, and the card's picture is "the corners around the block") with a clear 16-block lane toward the centre; a post forced into COMBAT (the suspect attacks, `fightResisting`) drops its post; a post that sees the suspect reports, radios Eyes_On and goes PURSUING; the perimeter ends on SEEN (all posts PURSUING), OFF (RETURNING) or after 60 s (PURSUING) - spec card - posts beyond Pursuit.Max_Distance are fine because POSTED has no leash give-up.
- Ruling R24: the Keystone 1.15.0 helpers are NEW code in keystone-npc (`EntitySpawner.isOutOfSight`/`findHiddenSpawnLocation`, `NpcPost` + `AbstractNpc.holdPost/tickPost`), not moves - Gang & Turf T2/T3 never started; owner K1 - Gang & Turf adopts them on rebase; the CommandMap helper stays deferred to 1.15.x.
- Ruling R25: out of sight = no clear block ray from the target's eye (any distance) or another player's eye (within Visibility_Check_Distance) to spot+1.6 - Spigot `rayTraceBlocks`, no Paper - glass counts as cover.
- Ruling R26: hand-off fires when the group goes from engaged to not engaged while a cop walks home beyond `Pursuit.Max_Distance`; heading = last 2 s of suspect samples; bias 10 s, cone +-60 degrees; the bias is stamped on every unit ENQUEUED inside the 10 s and travels with it, so a unit with a 40 s ETA still spawns ahead and seeded; the seed is the hand-off position (last-known sighting, never the live position); a station ahead wins only when its ETA is within Bias_Seconds of the nearest's - spec card + map drift 8 (a bare seed reads as SEEN and cancels the evasion it continues) - a hand-off seed pauses the clock for Lost_Sight_Seconds.
- Ruling R27 (OWNER CHECK with R2, DECISIONS D14): crooked contacts are a core feature (impl `/glw contact`, phone page, settings.yml knobs, Messages strings) - the sign is core, shares the phone's cooldown, and impl cannot name a module class; the unseen state arrives through the existing api event - on a server without cops-n-crooks the phone works without a sight gate, like the sign today.
- Ruling R28 (OWNER CHECK, DECISIONS D16: owner confirmed 2026-10-07): "unseen" = the last evasion state is not SEEN (SEARCHING, EVADED or no track) - the spec's "SEARCHING or EVADED" made total over the no-track case - a player can phone during an ETA before any cop arrives.
- Ruling R29: price-0 [WANTED] signs keep the sight gate but neither check nor start the cooldown; paid REMOVE/CLEAR and the phone share one cooldown - "admin signs priced at 0 stay free" - an admin event sign still refuses in a cop's view.
- Ruling R30: phone and bribe-star clears tag `WantedCause.CONTACT` (new); the sign keeps SIGN - one new value covers both paid-off paths - the 0.19 record cannot tell phone from pickup without another value.
- Ruling R31 (DECISIONS D22): bribe stars are `cop_point` rows of kind `pickup` (wand), shown as non-persistent floating items, picked up by proximity, 1 star, respawn 300 s in memory; a pickup is refused while the player's squad has seen him within Lost_Sight_Seconds (the card's "only works while nobody is looking", which the sign and phone get; no shared contacts cooldown) - lootchest pattern copied, not imported - a restart respawns every star at once.
- Ruling R32: per-crime charge-sheet prices and the [WANTED] bag-conversion clause are NOT in 0.16 - spec O4 / page "bag rule 0.17" - the 0.15.0 star-priced sheet stays.
- Ruling R33: with `Death.Hospital.Enable` (default true) the downed-path bill is quoted at the down (formula variables before the DEATH reset) and charged on `PlayerUndownedEvent` or quit while downed; the respawn goes to the nearest HOSPITAL waypoint in that world by a direct teleport (no timer, cooldown or cost), else the configured waypoint; a hospital respawn (either path) grants the `Shield_Seconds` 5 respawn shield (owner ruling D19, CONTRACTS C13: incoming damage cancelled, ends when the player attacks anything or the time runs out, 0 = off); a vanilla respawn also goes to the nearest HOSPITAL unless it is a bed or anchor respawn; the real-death bill stays at death; a jailed or handcuffed player downed is put back in jail by cops-n-crooks on `PlayerUndownedEvent` (C13); accepted: hospitals can be camped after the shield and any death still resets the stars (D17, D19) - spec + map drift 4/5 - Enable false is exactly 0.15 (the ward-bill message is also gated on Enable: false keeps the old "Death penalty" line), except the `recentDeaths` prune, a bug fix (US-33/WB-17) that ships regardless.
- Ruling R34: `Death.Money.Command` executables still run at the down - they are commands, not the bill - a server using them as the bill keeps charging at the down.
- Ruling R35: `WaypointRepository` skips a row with an unknown type (warning) instead of aborting the load - forward safety for the new constant - an admin downgrading loses only the hospital rows until he upgrades again.
- Ruling R36: self-defence knobs live in settings.yml `Wanted.Self_Defence` (core feature, runs without cops): window 8 s, the victim must have dealt >= 2.0 damage first, same gang or allies never count as a first strike, one exemption per pair per 600 s, and a killer who hit the victim in the 60 s before the victim's first strike is never covered (provocation memory, code constant: "hit, wait out the 8 s window, let him swing back, then kill" stays a crime); Enable false = no self-defence exemption at all - spec card; Min_Damage and the cooldown value were unspecified - the 0.15 fixed-window variant is not kept.
- Ruling R37 (DECISIONS D7, D20): WB-48 - a takedown is crime-free only when other players' escrow is > `Bounty.Minimum` AND >= new `Bounty.Takedown_Minimum` 100, the pair is not in its cooldown, and the killer has had fewer than 3 crime-free takedowns in the last hour (code constant, DECISIONS D20: an alt posting $100 on each victim is otherwise a reusable murder licence); the payout itself is unchanged - a new key with a code default reaches existing servers, raising the shipped Minimum would not - a cheap legitimate bounty pays out but the kill is a crime.
- Ruling R38 (DECISIONS D13): risk 11 - the AUTO rampage opening counts only crimes whose raw weight >= `Auto.Rampage_Min_Weight` 80; PETTY's `Max_Crimes` stays a plain count - cheap crimes can only make a chase less petty, never a rampage; 0.16 publishes no cheap crime - revisit PETTY when 0.17 publishes Brandish_Near_Cop.
- Ruling R39: no `WantedEvasionDropEvent` in 2.3 - 0.15.2 decision D4 (criminal record is 0.19) - none.
- Ruling R40: wand knobs and strings go in a new `copsncrooks/setup.yml`; breaker mode stores a `breaker` region plus a `breaker_trigger` point and nothing reads them until 0.19 - spec "stored-only placeholder" - none.
- Ruling R41: `/glw cop setup ...` (not `/cops setup`) - every cops command lives under `/glw cop` (`CNC/command/cops/CopCommand.java`) - none.
- Ruling R42: lanes, installs and graph refresh as 0.15 (one worktree per lane, only the orchestrator/T6 installs, graph refreshed after each wave merge) - parallel installs race in `~/.m2` - none.
- Ruling R43: `Dispatch_En_Route` and `Wipe_Refill` are always priority (bundled list, code default, and unioned into a server's own `Priority` list) - a non-priority line within 20 ticks of the priority `Dispatch_Wanted` is silently dropped by the player gap - a server cannot make those two lines low-priority (it can empty their Lines).
- Ruling R44: `%place%` is available on every CopRadio line (squad last-known position; dispatch lines use the target's position) and the bundled `Contact_Lost` names it - the card asks for place-named radio, and one place in CopRadio covers it - lines that do not use the token are unchanged.
- Ruling R45: the tier mix has no Enable key: it is composition data (`"Role@tier"`); an entry without `@` keeps the star's tier, so removing the `@` suffixes is the off switch, and an old cop_roles.yml is already "off" - a toggle would duplicate the data - documented in migration-0.16.0.md.
- Ruling R46: accepted escapes for 0.16 (PLAN risk 13): death resets all stars (D17), hospital camping after the 5 s respawn shield (D19), vehicles and pearls outrun posts, waypoint teleports under the existing waypoint rules; a logout no longer escapes (R20/R21, S16) - each is a later card or an owner question - T19 documents them in features/cops-n-crooks.md "Known limits".
- Ruling R47: Gangland refuses to enable on a Keystone older than its pin (T6, DECISIONS D21) - KS-MO-07 is open and 0.16 is the first release whose module calls new keystone-npc members from AI ticks - a server owner sees one clear error instead of a stack trace per tick.
- Ruling R48 (DECISIONS D23): a down that turns into an arrest (a handcuffed player downed, committed on `PlayerUndownedEvent` by T8) pays exactly one charge, the ward bill: the commit is a death-commit (`JailIntakeService.admit(player, true)`, no sheet) - spec section 3 keeps 0.15.0's "no sheet when the arrest commits on death" until the 0.18 arrest-on-down path - the sheet-only reading needs an api-visible restrained signal (D23).

---

### Task 1: Keystone 1.15.0 - out-of-sight spawn helper
Repo keystone | Lane `h14-ks-sight` | Wave 1 | Depends on: none | Model: sonnet

Files - OWN: `KS/entity/EntitySpawner.java`, `KST/entity/EntitySpawnerTest.java`.
Behaviour: add exactly CONTRACTS C3 (`isOutOfSight`, `findHiddenSpawnLocation`), `@since 1.15.0`, javadoc as written. The eye of a
player is `player.getEyeLocation()`; the target point is `spot.clone().add(0, 1.6, 0)`; the ray is `world.rayTraceBlocks(eye,
direction, eye.distance(point), FluidCollisionMode.NEVER, true)`; a non-null result = blocked. Players in another world are
ignored, and so is every world player for which `NpcSupport.isNpc(p)` is true (Citizens player-type NPCs: cops, civilians).
`findHiddenSpawnLocation` reuses `trySingleSpawnAttempt` with `requireBehind = false` and the same `playerOutdoor` the
current `findSpawnLocation` computes (through the overridable `isOutdoor`); it never mutates config. Keep
`findSpawnLocation`/`findClosestSpawnerLocation` untouched.
Tests (red first, compile-red allowed for the new methods; use the existing World/Block mock helpers of EntitySpawnerTest). The ring
angle comes from `ThreadLocalRandom`, so stub `rayTraceBlocks` and the ground checks by the spot's COORDINATES (e.g. every spot with
x > player x is blocked/hidden), never by call order:
`isOutOfSight_blockBetween_isHidden`, `isOutOfSight_clearRayFromTarget_isVisible_atAnyDistance`,
`isOutOfSight_bystanderBeyondVisibilityDistance_isIgnored`, `isOutOfSight_bystanderInRange_withClearRay_isVisible`,
`isOutOfSight_npcBystanderWithClearRay_isIgnored`,
`findHiddenSpawnLocation_skipsVisibleSpots_andReturnsAHiddenOne`, `findHiddenSpawnLocation_respectsTheAllowedPredicate`,
`findHiddenSpawnLocation_noHiddenSpot_returnsNull`, `findHiddenSpawnLocation_acceptsSpotsInFrontOfThePlayer`.
Command: `mvn -q -pl keystone-npc -am test -Dtest=EntitySpawnerTest -Dsurefire.failIfNoSpecifiedTests=false`, then
`mvn -q -pl keystone-npc -am test`. Docket: cross docket KS- rows touching EntitySpawner - check, report.
Done when: C3 compiles as written, the 9 tests were red then green, keystone-npc suite green, no Gangland word in the code.

### Task 2: Keystone 1.15.0 - hold-post and leash
Repo keystone | Lane `h14-ks-post` | Wave 1 | Depends on: none | Model: sonnet

Files - create: `KS/NpcPost.java`, `KST/NpcPostTest.java`, `KST/AbstractNpcPostTest.java`. OWN: `KS/AbstractNpc.java`.
Behaviour: CONTRACTS C4 exactly. `tickPost` reads the NPC entity location (`getEntity()`), never throws when the entity is null
(returns false). Facing the watch point is NEW code (C4): a private helper taking a Location that sets yaw/pitch toward it with no
aim error (`faceTarget` :656 only delegates to `NpcCombatDelegate.faceTarget(Player)`, which adds aim error; do not reuse it).
`destroy` clears the post. Do not touch pursuit, cover or combat code.
Tests (red first): `NpcPostTest`: `withinLeash_sameWorldInsideRadius`, `withinLeash_otherWorld_isFalse`, `arrived_withinOneAndAHalfBlocks`,
`constructor_clonesLocations`, `leashBelowArriveRadius_isRaisedToIt`. `AbstractNpcPostTest` (a minimal test subclass, Citizens
NPC mocked as other AbstractNpc tests do): `holdPost_navigatesToTheAnchor`, `tickPost_offTheLeash_navigatesBack`,
`tickPost_arrived_stopsAndReturnsTrue`, `tickPost_withoutPost_returnsFalse`, `releasePost_clearsAndStops`, `destroy_clearsThePost`.
Command: `mvn -q -pl keystone-npc -am test -Dtest=NpcPostTest,AbstractNpcPostTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Done when: C4 compiles as written, tests red then green, keystone-npc suite green.

### Task 3: gangland-api 2.3 surface, core keys and messages, versions
Repo gangland | Lane `api23` | Wave 1 | Depends on: none | Model: sonnet

Files - see W1 matrix row T3.
Behaviour: CONTRACTS C1 and C2 exactly (C2 as the orchestrator confirmed it after owner decision D14). `GanglandApi.VERSION = "2.3"` with a javadoc bullet "2.3 adds RegionProvider, PlaceRegion,
RegionShape, PlaceNames, Waypoint.WaypointType.HOSPITAL, WantedCause.CONTACT and the self-defence, contacts and hospital
settings (the hospital respawn shield included)". `DataConfig.placeNames()` bean. settings.yml: `Wanted.Self_Defence` and `Wanted.Contacts` blocks after
`Wanted.Take_Money`, `Bounty.Takedown_Minimum` after `Bounty.Pay_Notoriety`, `User.Death.Hospital.Enable` and
`User.Death.Hospital.Shield_Seconds: 5` (owner ruling D19; 0 = off) inside the Death block,
each key commented, block style, file indent. Messages constants (eight, `DEATH_HOSPITAL_SHIELD` included) +
`message_en.yml`/`message_es.yml` lines at the C2 paths. Settings: eleven getters (C2), `getHospitalShieldSeconds()` included.
Root `pom.xml` `<revision>` 0.16.0. `module.yml` `Host_Api: 2.3` for cops-n-crooks and gangland-turf only.
Tests (red first): api `RegionShapeTest` (cuboid normalises corners, inclusive edges, column ignores Y, sphere contains,
footprints), `PlaceRegionTest` (contains checks world name, null location false, tags copied), `PlaceNamesTest`
(`noProvider_isEmpty`, `smallestFootprintFirst`, `sameSourceReplaces`, `throwingProviderIsSkipped`, `locate_skipsBlankNames`,
`withTag_findsTheSmallestTagged`, `nullWorld_isEmpty`). impl `SettingsTest`: every new key's default with the key absent and its
value when present (`Shield_Seconds`: 5 absent, 0 and 12 read as written). `MessagesTest`: new constants resolve in en and es
(`DEATH_HOSPITAL_SHIELD` included). Any test enumerating WantedCause/WaypointType values
is updated (report which).
Command: `mvn -q -pl gangland-api -am test -Dtest=RegionShapeTest,PlaceRegionTest,PlaceNamesTest -Dsurefire.failIfNoSpecifiedTests=false`,
`mvn -q -pl gangland-impl -am test -Dtest=SettingsTest,MessagesTest -Dsurefire.failIfNoSpecifiedTests=false`, then full
`gangland-api`, `gangland-core`, `gangland-impl` suites and reactor `clean verify -DskipTests`.
Docket: none expected. Done when: C1/C2 compile as written, VERSION 2.3, revision 0.16.0, both module.yml at 2.3, suites green.

### Task 4: Cop config spine - dispatch, breather, hand-off, perimeter, tier mix, radio lines
Repo gangland | Lane `cop-config` | Wave 1 | Depends on: none | Model: sonnet

Files - see W1 matrix row T4.
Behaviour: CONTRACTS C6 exactly. cops.yml: blocks `Cops.Dispatch`, `Cops.Breather`, `Cops.Handoff`, `Cops.Perimeter` with the
CONSTRAINTS values, after `Cops.Regroup`, every key commented. cop_roles.yml Squad_Composition: the CONSTRAINTS "Mixed tiers"
entries for 3, 4, 5 and a comment explaining `@<tier id>` (tier ids are the `Cops.Tiers` keys 1-5). Radio: the C6 table in
`cop_radio_messages.yml` (English) and `_es.yml` (Spanish), `Unknown_Place` root word, the `Contact_Lost` place wording, `DEFAULT_LINES` fallbacks for every new key,
the priority entries (Ruling R43: bundled `Cops.Radio.Priority`, the `CopConfigProvider.java:49` default set, and the union in
`YamlCopConfigProvider`) and cooldowns in cops.yml. The two `CopRadio` overloads and `compassWord` (Location, Location).
`CopRole.nextSlot`.
Tests (red first): `YamlCopConfigProviderTest`: each new block parses, missing block = DEFAULT, bad number = default + warning,
`"Marksman@3"` -> role Marksman tier 3, `"Pointman"` -> tier 0, `"Medic@x"` -> tier 0 + warning, `getSquadTiers` length equals
composition length, `customPriorityList_stillHoldsTheDispatchLines`; `breatherMs_byStars` (1 star 15 s, 5 stars 6 s, 0 and 7 stars
clamp to the ends; disabled -> 0). `CopRolesFileTest`: the bundled file's 3-star tiers are [3,2,3,3,2]. `CopRoleTest`: `nextSlot` first
unfilled, last when all filled, -1 for null. `CopRadioMessagesTest`: an upgraded file without the new keys still yields the C6 lines;
`Unknown_Place` falls back to "the area". `CopRadioTest`: `dispatch` with extras fills `%station%`/`%eta%`/`%count%`;
`enRoute_rightAfterDispatchWanted_isDelivered` (same player, same tick, default settings); `compassWord` returns the file's word for
each of 8 sides.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=YamlCopConfigProviderTest,CopRolesFileTest,CopRoleTest,CopRadioMessagesTest,CopRadioTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Done when: C6 compiles as written, behaviour of every existing caller unchanged (nextRole same results), suite green.

### Task 5: Chase config additions and the risk-11 rampage fix
Repo gangland | Lane `chase-config` | Wave 1 | Depends on: none (0.15.2 merged) | Model: sonnet

Files - see W1 matrix row T5. `AutoDrop.ChaseView` is built in `ChaseArcs.view` (find by name; it calls the private `onChase` prefilter first); its one production caller
is `EvasionClock.views` (find `arcs.view(`). Orient with `graphify explain "ChaseArcs"` after the 0.15.2 merge refresh.
Behaviour: CONTRACTS C7 exactly: the 4-arg `view` overload (3-arg delegates, unchanged behaviour), EvasionClock's one call
switched to it, the 4-arg `ChaseConfig` constructor and the null-to-DEFAULT `bribeStars`. wanted.yml under `Wanted.Evasion`: `Hideout` block, `Quiet_Speed` block, `Max_Speed`, and
`Auto.Rampage_Min_Weight` (comment: "Only crimes at least this heavy count toward the rampage opening; the shipped cheap crimes
(Brandish_Near_Cop 25, Assault_Civilian 30, Car_Theft 60) never do"); `Wanted.Bribe_Stars` block. Validation in ChaseConfig
logs and falls back (speeds <= 0 -> default, Max_Speed < 1 -> 4.0, Per_Minute < 0 -> 0.25).
Tests (red first): `ChaseConfigTest`: each new block parses with defaults when absent; `quietSpeed_speedFor` (0 min = 1.0,
2 min = 1.5, 10 min = 2.0 capped, disabled = 1.0); `fourArgConstructor_defaultsBribeStars`. Rampage pin (`ChaseArcsTest`, calling the
4-arg `view` with `HeatSettings.DEFAULT::weightOf`): `fourBrandishCrimesInTheOpening_areNotARampage` (four Brandish_Near_Cop records
within 30 s -> opening 0 -> the planner's rampage rule does not fire) - red against 0.15.2 (written against the 3-arg form, it
reads opening 4); `oneKillCop_isStillARampage`; `fourKillPlayerCrimes_stillCountAsTheOpening`;
`cheapCrimeFirst_windowStartsAtTheFirstHeavyCrime` (a Brandish at t0, four Kill_Player at t0+20..t0+45 s -> opening 4);
`preChaseHeavyCrime_isNotTheWindowStart` (a Kill_Player 10 min before the chase start, then three Kill_Player on the chase: the
on-chase prefilter drops the old one first, the window starts at the first on-chase heavy crime, opening 3, crimes 3);
`threeArgView_countsEveryCrime` (characterization pin). EvasionClockTest: the `never().view` verify moved to 4 matchers.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=ChaseConfigTest,ChaseArcsTest,EvasionClockTest,ChaseArcListenerTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Docket: 0.15.2 PLAN risk 11 (file it as fixed in the report; if 0.15.2 filed it as a docket row, give that id).
Done when: C7 compiles; existing EvasionSettings/AutoSettings/ChaseConfig constructor callers and every 3-arg `view` caller
unchanged; the rampage pin was red then green.

### Task 6: Keystone 1.15.0 release and Gangland pin
Repo keystone + gangland | Lane: Keystone integration worktree + Gangland lane `pin` | Wave 2 | Depends on: T1, T2 | Model: sonnet

Files - Keystone: `pom.xml` (`<revision>` 1.15.0, :62), `docs/phase-h14-dispatch-posts.md` (new), `docs/keystone-npc.md`.
Gangland: root `pom.xml` `<keystone.version>` 1.15.0 (:70); the Keystone floor guard (Ruling R47): new
`gangland-impl/src/main/resources/keystone-floor.properties` with `keystone.floor=${keystone.version}` (every module's
`src/main/resources` is filtered, root `pom.xml:515-520`); new `IMPL/util/KeystoneFloor.java` with
`static boolean satisfied(@Nullable String installed, String floor)` (Keystone `org.luckyraven.keystone.update.PluginVersion.parse`
+ `compareTo`; `PluginVersion.parse` never throws - null, blank or junk becomes 0.0.0, below every floor - so `satisfied` first
checks `installed` against `^\d+(\.\d+){0,2}([-+].*)?$` itself: null, blank or no match -> true with one warning, never a false
refusal; only a matching string is parsed and compared) and
`static @Nullable String floor(JavaPlugin)` (reads the properties resource); `Gangland.onEnable` (:90) checks it FIRST, before
`new GanglandContext`: below the floor -> `log.error("Gangland <ver> needs Keystone <floor> or newer; found <installed>")`,
`getServer().getPluginManager().disablePlugin(this)`, return (make sure `onDisable` tolerates a null context). Test
`KeystoneFloorTest` (equal passes, older patch/minor fails, newer passes, `1.15.0-SNAPSHOT` vs floor 1.15.0 passes, unparsable
`"dev"`/`""`/null pass (the regex path, not a parse exception), missing resource passes).
Behaviour: merge lanes `h14-ks-sight` and `h14-ks-post` into `phase-h14-dispatch-posts` (no conflicts expected). Write the
phase doc in the shape of `docs/phase-h13-npc-roles.md`: goal (Cops N Crooks 0.16 is the second consumer; product-free),
the C3/C4 API, tests, out of scope (CommandMap helper deferred to 1.15.x, carrier bodies 1.16.0). Add a keystone-npc.md section
"Out-of-sight spawns and posts (1.15.0)" and class-map rows for `NpcPost`. Run `mvn clean install` in the Keystone integration
worktree (installs 1.15.0 to `~/.m2`). Then in the Gangland lane bump `<keystone.version>` and run
`mvn -q clean verify -DskipTests`, `mvn -q -pl gangland-impl -am test -Dtest=KeystoneFloorTest -Dsurefire.failIfNoSpecifiedTests=false`
(red first) and `mvn -q -pl gangland-features/cops-n-crooks -am test`. Do not push; report the Keystone commit for the orchestrator to
merge into Keystone master.
Tests: Keystone none new (full `mvn test` of the Keystone reactor green); Gangland `KeystoneFloorTest` red then green, impl suite green.
Done when: Keystone 1.15.0 installed locally, phase doc + module guide committed on the phase branch, Gangland builds on 1.15.0, the
floor guard is in and tested.

### Task 7: Station, admin-region and setup-point registries
Repo gangland | Lane `registries` | Wave 2 | Depends on: T3, T4 | Model: sonnet

Files - see W2 matrix row T7.
Behaviour: CONTRACTS C5 exactly. Tables/repositories in the shape of `CNC/database/JailExitTable.java`/`JailExitRepository.java`;
registries in the shape of `CNC/jail/JailExitService.java` (BeanLifecycle; `onInitialize` loads, `setDataSupplier` in the
constructor; create/remove/link persist at once). `AdminRegionRegistry.regionsAt` scans that world's regions (ponytail: linear,
like `TurfManager.findAt`). Tags stored lowercase, comma-joined, trimmed. `cop_spawner.station_id` appended last; the positional
read takes index 7 only when present. `RegistryModuleConfig` registered in `CopsNCrooksModule` (update
`CopsNCrooksModuleTest`'s expected configuration list). `CopRadio.placeOf`, `setPlaceNames` (no constructor change; the
`CopsNCrooksModuleConfig` radio bean takes `PlaceNames` and calls the setter), the `place` voice extra and the dispatch `place`
merge (C5, Ruling R44). `CopSpawnerSetCommand` auto-assigns per C5 (pass `StationRegistry` down through `CopCommand` ->
`CopSpawnerCommand`); it reads `Station_Radius` per call from `copLoader.getLoadedProvider()` with
`requireNonNullElse(..., DispatchSettings.DEFAULT)` (CONTRACTS C8 "Config reads"; no `CopConfigProvider` bean exists).
Docket T-180 (P1, data loss), CONTRACTS C5 "Id floor over unloaded worlds" exactly: `CopSpawnerRepository` and
`CivilianSpawnerRepository` record `highestStoredId` over EVERY row before the unloaded-world skip; `CopSpawnManager` and
`CivilianSpawnManager` raise `ID` to it after `onInitialize` and `reloadSpawners`; `JailRepository` takes
`JailService.ID = max(JailService.ID, id)` before its skip. No Keystone change (nothing goes to Task 1); `JailExitRepository` and
`TurfPowerupNpcRepository` checked and unchanged (their keys are the jail id / turf id, never a counter; C5 says why). This lane
also builds and tests `gangland-features/gangland-civilians` (no other W2 task touches it).
Tests (red first; SQLite tests per CONSTRAINTS): `StationRegistryTest` (create assigns max+1 and persists; duplicate name ->
null, nothing stored; remove; byName case-insensitive; nearest same world only; linkJail persists; reload round trip),
`AdminRegionRegistryTest` (create normalises, rejects two worlds, regionsAt returns PlaceRegion with id `copsncrooks:<id>` and the tag,
round trip, withTag), `SetupPointRegistryTest` (ofKind, round trip), `CopSpawnerRepositoryStationTest` (an old table without
`station_id` loads, the column is added and round-trips), `CopSpawnManagerStationTest` (assignNearby radius, a spawner of another
station is skipped, spawnersOf, unassign), `CopRadioTest` (`placeOf` returns the district name, unknown place word when none;
`dispatch_addsThePlaceOfTheTarget`; `contactLost_namesTheLastKnownPlace`), `CopsNCrooksModuleTest` (the list now ends with
`RegistryModuleConfig`). T-180, each with `Bukkit.getWorld` stubbed ("world" loaded, "gone" null) and red against today's code:
`CopSpawnerRepositoryTest.reloadWithAnUnloadedWorld_newRowDoesNotReuseItsId` (rows 1 "world" and 2 "gone" stored; a
`CopSpawnManager` over the real repository runs `onInitialize`; `setSpawnerLocation` creates id 3 and row 2 is still in the table
with world "gone"; the same after `reloadSpawners`), `JailRepositoryTest.reloadWithAnUnloadedWorld_newRowDoesNotReuseItsId`
(jails 1 "world" and 2 "gone"; after `JailService.onInitialize` a new jail gets id 3 and row 2 survives; `JailService.ID` reset
to 0 in setUp), `CivilianSpawnerRepositoryTest.reloadWithAnUnloadedWorld_newRowDoesNotReuseItsId` (gangland-civilians, same
shape through `CivilianSpawnManager`).
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=StationRegistryTest,AdminRegionRegistryTest,SetupPointRegistryTest,CopSpawnerRepositoryStationTest,CopSpawnManagerStationTest,CopSpawnerRepositoryTest,JailRepositoryTest,CopRadioTest,CopRadioRolesTest,CopsNCrooksModuleTest -Dsurefire.failIfNoSpecifiedTests=false`,
`mvn -q -pl gangland-features/gangland-civilians -am test -Dtest=CivilianSpawnerRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false`,
then the cops-n-crooks and gangland-civilians suites.
Docket: T-180 fixed (report the commit and the three covering tests; note JailExitRepository/TurfPowerupNpcRepository checked,
no change). Ids reused after a delete of the highest row are harmless (the row is gone); report anything else, do not fix.
Done when: C5 compiles as written, beans wired, the admin regions answer through `PlaceNames` in a test, the three T-180 tests
went red then green, cops-n-crooks and gangland-civilians suites green.

### Task 8: Hospital respawn, one bill, respawn shield, bundled cash drop off
Repo gangland | Lane `hospital` | Wave 2 | Depends on: T3 | Model: sonnet

Files - see W2 matrix row T8.
Behaviour: CONTRACTS C13 exactly; `WaypointRegionProvider` per C1 (registered from a `DataConfig` bean method that takes
`PlaceNames` and `WaypointManager`; it reads `getWaypoints()` on each call). `WaypointRepository.java:56`: an unknown type logs one
warning and skips the row (Ruling R35). `items/money.yml:57` PLAYER `Enabled: false` with the comment "One death costs one bill;
set true to also drop part of the wallet at the body". `recentDeaths` entries older than the dedup window are pruned on each put
(docket US-33/WB-17). With `Hospital.Enable` true the ward-bill message replaces the hard-coded "Death penalty" line (both paths); with it false the old
line stays (R33). Real death: unchanged timing.
`WaypointRegionProvider` caps a GANG waypoint's radius at 64.0 (C1). Detainment on the downed path (C13, Ruling R48): cops-n-crooks
`DetainmentListener.onUndowned` commits a handcuffed player as a death-commit through the new
`JailIntakeService.admit(Player, boolean)` (no sheet; the bill is the one charge) (this lane also builds and tests
`gangland-features/cops-n-crooks`).
Hospital respawn shield (owner ruling D19 = the alternative; CONTRACTS C13 "Hospital respawn shield" exactly): new `HospitalShield`
holder (bean `DataConfig.hospitalShield()`) and new `HospitalShieldListener` (`@ListenerHandler`, not a bean). Granted for
`Settings.getHospitalShieldSeconds()` (5; 0 or less = off) at the hospital respawn on BOTH paths (downed: `performRespawn` after
the hospital teleport; vanilla: the `PlayerRespawnEvent` HIGH handler after `setRespawnLocation`); while active every incoming
damage to the shielded player is cancelled at LOWEST (except VOID), so no down, no crime and no self-defence first strike come
from it; it ends early when the player damages any entity (melee or his projectile) and when the time runs out or he quits.
`CustomPlayerDeathListener` gains the `HospitalShield` constructor parameter (update `CustomPlayerDeathListenerQuitTest`'s call
site). Both new classes are T8's; no other W2 task touches them, and `EntityDamageListener` (T9) is NOT edited.
Tests (red first): `PlayerDeathListenerTest`: `downed_withHospital_chargesNothingAtTheDown`, `undowned_chargesTheQuotedBillOnce`,
`quitWhileDowned_chargesTheBill`, `quote_usesTheWantedLevelBeforeTheDeathReset`, `hospitalDisabled_chargesAtTheDownAsBefore`,
`belowThreshold_noPendingBill`, `recentDeaths_arePruned`; existing five cases stay green. `CustomPlayerDeathListenerHospitalTest`:
`performRespawn_teleportsToTheNearestHospital`, `noHospital_usesTheConfiguredWaypoint`, `vanillaRespawn_setsTheNearestHospital`,
`bedRespawn_isLeftAlone`, `hospitalDisabled_vanillaRespawnUntouched`. `WaypointManagerNearestTest` (same world only, unloaded
skipped, nearest wins). `WaypointRegionProviderTest` (GANG with radius 0 -> 8-block sphere owned by the gang, tagged hideout;
radius 500 -> 64; non-GANG ignored). `WaypointRepositoryTypeTest` (unknown type row skipped, others load; `hospital` loads).
`BundledMoneyYmlTest` (the shipped money.yml has PLAYER Enabled false and COP unchanged). `DetainmentListenerUndownedTest`
(`jailedDowned_isPutBackInJail`, `handcuffedDowned_paysTheBillNotTheSheet` (transit cancelled, `admit(player, true)`),
`freeDowned_isLeftAlone`); `JailIntakeServiceTest`: `deathCommitFlag_skipsTheSheet_forALivePlayer`; existing intake cases green.
`hospitalDisabled_keepsTheDeathPenaltyLine` in PlayerDeathListenerTest.
Shield (clock injected through the package-private constructor; `SettingsFixture` sets `Shield_Seconds`): `HospitalShieldTest`:
`expires` (shielded at +4.9 s, not at +5.0 s, entry gone), `zeroDisables` (Shield_Seconds 0: `grant` stores nothing and sends no
message; -1 the same), `grant_sendsTheShieldMessage` (%seconds% = 5). `HospitalShieldListenerTest`: `blocksDamage` (a shielded
victim's `EntityDamageEvent` and `EntityDamageByEntityEvent` are cancelled; an unshielded one is not), `attackingEndsIt` (the
shielded player hits a zombie: his shield ends and the zombie's damage is not cancelled; the same through his arrow),
`voidDamage_isNotBlocked`, `quit_endsIt`. `CustomPlayerDeathListenerHospitalTest` gains `performRespawn_atAHospital_grantsTheShield`,
`vanillaHospitalRespawn_grantsTheShield`, `noHospital_grantsNoShield`.
Command: `mvn -q -pl gangland-impl -am test -Dtest=PlayerDeathListenerTest,CustomPlayerDeathListenerHospitalTest,CustomPlayerDeathListenerQuitTest,HospitalShieldTest,HospitalShieldListenerTest,WaypointManagerNearestTest,WaypointRegionProviderTest,WaypointRepositoryTypeTest,BundledMoneyYmlTest -Dsurefire.failIfNoSpecifiedTests=false`,
`mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=DetainmentListenerUndownedTest,JailIntakeServiceTest -Dsurefire.failIfNoSpecifiedTests=false`,
then the impl and cops-n-crooks suites.
Docket: US-33/WB-17 (fixed), US-05/US-06/WB-41/WB-38/LS-36 (touch only if a test pins them; report).
Done when: C13 compiles as written, Hospital.Enable false reproduces 0.15 behaviour in a test (no hospital, no shield), the four
shield tests (`blocksDamage`, `expires`, `attackingEndsIt`, `zeroDisables`) went red then green, suites green.

### Task 9: Self-defence and posted-bounty takedown guards
Repo gangland | Lane `self-defence` | Wave 2 | Depends on: T3 | Model: sonnet

Files - OWN: `IMPL/listener/player/EntityDamageListener.java`, `EntityDamageListenerTest`.
Behaviour (Rulings R36, R37): replace `FIGHT_WINDOW_MS` (:54) with `Settings.getSelfDefenceWindowSeconds() * 1000L` read per call;
`recordHit` (:229) also adds `event.getFinalDamage()` to a `damage` map keyed `"attacker>victim"` (reset with the fight; pass the
damage from the call site :95-98); `selfDefence(killer, victim)` (:241) is true only when `Settings.isSelfDefenceEnabled()`, not
`gangs.alliedOrSame(killer, victim)`, the victim struck first (today's rule), the victim's damage to the killer >=
`getSelfDefenceMinDamage()`, the killer did NOT hit the victim in the 60 s before the victim's first strike of this fight
(provocation memory: a separate `lastHit` map keyed `"attacker>victim"` -> time, kept 60 s, independent of the fight window;
constant `PROVOCATION_MS = 60_000L` with a `ponytail:` comment), and the sorted pair has no running exemption cooldown. The takedown
return (:181) requires `byOthers.compareTo(Settings.getBountyMinimum()) > 0 && byOthers.compareTo(Settings.getBountyTakedownMinimum()) >= 0`,
no pair cooldown, and fewer than 3 crime-free takedowns by this killer in the last hour (memory map killer -> recent takedown
times; constant `TAKEDOWNS_PER_HOUR = 3` with a `ponytail:` comment; the 4th still pays but is a crime). Granting either exemption
stamps the pair cooldown (`now + Pair_Cooldown_Seconds*1000`). Payout logic unchanged. `pruneStale`/`onPlayerQuit` also clear damage
and provocation entries; expired cooldowns and takedown stamps are pruned with stale fights. Keep the ponytail comment updated.
Tests (red first): FLIP `fightOlderThanThirtySeconds_isForgotten_soTheKillIsACrime` (:357) to
`fightOlderThanTheWindow_isForgotten_soTheKillIsACrime` (9 s with Window 8); add `victimBelowMinDamage_killIsACrime`,
`victimHitFirstWithEnoughDamage_isSelfDefence`, `gangmateFirstStrike_neverCounts`, `secondExemptionInsideThePairCooldown_isACrime`,
`selfDefenceDisabled_killIsACrime`, `provokeWaitRetaliate_isACrime` (K hits V, 9 s pass, V hits K for 4.0, K kills V -> crime),
`takedownBelowTakedownMinimum_paysButIsACrime` (WB-48: $0.01 posted), `takedownAtTheMinimum_isNotACrime`,
`takedownInsideThePairCooldown_isACrime`, `fourthTakedownWithinTheHour_paysButIsACrime`. Existing
`victimStruckFirst_killIsSelfDefence_noStarNoNotoriety` (:333), `postedTakedown_isNotACrime`,
`selfPostedBounty_killIsStillACrime`, `claim_paysPostedOnly_notorietyStays` stay green (adjust their posted amounts/damage only if the
new floor makes them red, and say so). The shared helper `hit()` (:530) deals 1.0 (`getFinalDamage` :537), below Min_Damage 2.0, so
BOTH `victimStruckFirst_killIsSelfDefence_noStarNoNotoriety` (:333) and `selfDefence_alsoHoldsWithoutTheTracker` (:404) go red:
raise `hit()`'s damage to 4.0 (fix the helper, never the rule) and add a below-floor variant for `victimBelowMinDamage_killIsACrime`.
Command: `mvn -q -pl gangland-impl -am test -Dtest=EntityDamageListenerTest -Dsurefire.failIfNoSpecifiedTests=false`, then the impl suite.
Docket: WB-48 (fixed), WB-32/WB-43/WB-45 (check still green). Done when: every listed test red then green, suite green.

### Task 10: Crooked contacts - phone desk, /glw contact, [WANTED] sign gate
Repo gangland | Lane `contacts` | Wave 2 | Depends on: T3 | Model: sonnet

Files - see W2 matrix row T10.
Behaviour: CONTRACTS C12 exactly (Rulings R27-R30). `ContactDesk` bean in `GameplayConfig` (clock `System::currentTimeMillis`),
passed to `SignManager` (:257) -> `WantedSign` (`SignManager.java:157`) -> `WantedAspect` (`WantedSign.java:38`). `ContactCommand`
registered like the other impl sub-commands (`command/sub/*`, auto-discovered); permission default for players (follow how
`/glw bounty` player commands are granted). Phone: `phone.yml` new slot 31 "&6&lCrooked Contact" -> `Inventory: phone_contacts`;
`phone_contacts.yml` (register it with `loader.addExpectedFile(... "phone_contacts", "inventory", ".yml")` beside
`GameplayConfig.java:188`) with two buttons `Command: "/glw contact 1"` / `"/glw contact 2"`, lore showing
`%gangland_contact_price%` per star and `%gangland_contact_cooldown%`. `commands.json` entry `contact`
("/glw contact [stars]", "Pays a crooked desk sergeant to wipe stars while no cop sees you").
Tests (red first): `ContactDeskTest` (seen after SEEN, seen cleared by OFF/forget, cooldown math, priceFor,
`quitAndRejoin_cooldownSurvives`, `wantedEndThenNewChase_cooldownSurvives`), `EvasionStateListenerTest`,
`GanglandPlaceholderTest` (`contactPrice_isThePerStarPrice`, `contactCooldown_readyOrSecondsLeft`), `PhoneContactsPageTest` (the
bundled `phone.yml` slot 31 opens `phone_contacts`, whose two buttons run `/glw contact 1` and `/glw contact 2`),
`ContactCommandTest` (`seen_refuses`, `cooldown_refuses`, `notWanted_refuses`, `noMoney_refuses_andMovesNothing`,
`twoStars_chargesTwiceThePrice_andLowersWithContactCause`, `clampsToTheCurrentLevel`, `disabled_refuses`). `WantedAspectTest`:
`remove_whileSeen_refusesWithReason`, `paidRemove_startsTheCooldown`, `paidClear_insideTheCooldown_refuses`,
`freeSign_whileSeen_refuses_butNeverCoolsDown`, `contactsDisabled_signAsBefore`; the five existing cases stay green (desk empty = allowed).
Command: `mvn -q -pl gangland-impl -am test -Dtest=ContactDeskTest,EvasionStateListenerTest,ContactCommandTest,WantedAspectTest,GanglandPlaceholderTest,PhoneContactsPageTest,WiringConfigTest -Dsurefire.failIfNoSpecifiedTests=false`, then the impl suite.
Docket: none known; report new. Done when: C12 compiles as written, a cancelled sign moves no money (test), suites green.

### Task 11: Dispatch from stations, mixed squads and the post-wipe breather
Repo gangland | Lane `dispatch` | Wave 3 | Depends on: T4, T6, T7 | Model: opus

Files - see W3 matrix row T11.
Behaviour: CONTRACTS C8 and the seeding half of C11 (Rulings R15-R21). `spawnTick` (`CopManager.java:511-609`):
1. When `getDispatchSettings()` and `getBreatherSettings()` are both disabled (or null-guarded DISABLED in tests), the refill
   loop (:577-592) is today's, except that `tier` comes from the slot (step 3); everything else of the method is unchanged.
2. Otherwise: after the prune, a wipe = breather enabled, no counted cop, `casualtyWithin(now, Wipe_Window_Seconds*1000)` and
   `breatherUntil <= now` -> `breatherUntil = now + breatherMs(level)` (remember "wipe" for this pass's radio line). `missing = targetCount - counted - pendingCount`; for each missing slot:
   slot = `CopRole.nextSlot(composition, liveRoles + pending roles)`, role/tier per step 3, `bias = group.biasAt(now)`,
   `plan = dispatcher.plan(player, now, bias)`, `arriveAt = max(now, breatherUntil) + plan.etaMs()`, enqueue
   `new PendingUnit(role, tier, arriveAt, plan.station(), bias)` (the bias travels with the unit, C8). One radio line
   per non-empty batch: `Wipe_Refill` when this pass detected the wipe, else `Dispatch_En_Route` when the plan has a station (extras count,
   station name, eta seconds; `place` is merged by CopRadio), else none. Then `takeDue(now)`: `spawnUnit` each; null ->
   `requeue`; a spawned unit gets today's setup (target, combatForced, PURSUING, add) plus, when `unit.bias() != null`, the hand-off
   seed `squad.reportSighting(unit.bias().lastSeen())` + `group.markTipOff(now)` (C11).
3. Slot tier = `tiers.get(slot)` (clamped to maxTier) when `tiers = requireNonNullElse(getSquadTiers(level), List.of())` and
   `slot >= 0 && slot < tiers.size() && tiers.get(slot) > 0`, else `getTierForWantedLevel(level)` (the fixture's empty tiers and
   `nextSlot` -1 take the else branch); the Escalate
   check and formation arc keep using the star tier.
`Dispatch_Wanted` at :119 stays the 5-arg call: CopRadio (T7) merges `%place%` into every dispatch line, so `CopManagerSquadTest:150`
(`verify(fx.radio, times(1)).dispatch(group, player, "Dispatch_Wanted", 2, "SWAT")`) stays green untouched. `CopManager` gets the
`Dispatcher` as a new last constructor parameter (C8; `CopsNCrooksModuleConfig` bean `dispatcher(StationRegistry, CopLoader)` per
C8 "Config reads" + `CopManagerFixture`, which builds `new Dispatcher(stations, () -> provider)`). `onWantedEnd`/
`despawnAllForPlayer` clear the queue; `releaseSurplus` counts slots as today. `CopGroup` per C8 (backup gate in
`requestBackup`/`grantRegroupBackup`; `unitsEnRoute`). `CopSpawnManager.spawnUnit` and `hiddenRing` per C8 (no constructor change)
using Keystone `findHiddenSpawnLocation`/`isOutOfSight`, with the anyRoof retry for an indoor suspect; the station side test uses the
target->station bearing, cone 60 degrees. `CopListener` passes the cause. Debug lines per C8 (including `bias=` and `ahead=`).
Tests (red first): `CopManagerFixture` stubs Dispatch/Breather DISABLED and `getSquadTiers` empty by default and passes a Dispatcher
(existing `CopManagerSquadTest`/`CopManagerStuckTest`/`CopGroupSquadTest` stay green unchanged except fixture plumbing). New
`CopManagerDispatchTest`: `crime_enqueuesUnitsWithTheStationEta_andSpawnsNothingYet`, `dueUnits_spawn_andEnRouteIsRadioedOnce`,
`noStation_ringUnitsArriveAtOnce_fromStationFalse`, `wipe_addsTheBreather_andRadiosWipeRefill`, `noWipe_noBreather`,
`restoreStart_noSeed_andGraceDelay`, `pendingUnits_countTowardTheTarget_noDoubleEnqueue`, `failedSpawn_isRequeued`,
`threeStars_slotsGetTheirTiers` (Pointman tier 2, Marksman tier 3), `biasActive_newUnitsAreSeededWithTipOff`,
`biasSurvivesALongEta` (station ETA 20 s > Bias_Seconds 10: the unit spawns after the bias expired and is still seeded with
`lastSeen` and spawned ahead), `backupHeld_baseSquadStillRefills` (held flag set, a lost base-squad cop is still re-enqueued),
`wantedEnd_clearsTheQueue`. `DispatcherTest`: nearest station, bias prefers the station ahead, a far station ahead beyond
nearest ETA + Bias_Seconds loses to the nearest, ETA clamp/rounding, other world ignored, disabled -> (null, 0).
`CopSpawnManagerUnitTest`: station spawner out of sight wins, visible spawner skipped -> hidden ring on the station side,
`activeBias_ringSpotIsAhead`, `indoorSuspect_hiddenRingRetriesAnyRoof`, `removedStation_fallsBackToTheRing` (no spawners), nothing
hidden -> legacy path. `CopGroupSquadTest`: `backupHeld_refusesBackupAndRegroupGrant`, queue ops, `unitsEnRoute_stopsTenSecondsAfterEta`.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=CopManagerDispatchTest,DispatcherTest,CopSpawnManagerUnitTest,CopGroupSquadTest,CopManagerSquadTest,CopManagerStuckTest,CopSpawnManagerFallbackTest,CopSpawnManagerStationTest,CopListenerDeathTest,CopListenerShieldTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Docket: check cops spawn/backup rows (CN-*, T-146); report.
Done when: C8 compiles as written; with both features disabled the 0.15 squad tests pass untouched; all new tests red then green.

### Task 12: Admin setup wand and /glw cop setup
Repo gangland | Lane `wand` | Wave 3 | Depends on: T7 | Model: sonnet

Files - see W3 matrix row T12.
Behaviour: CONTRACTS C15 exactly. Copy (do not import) the turf wand shape (`TURF/selection/{WandSelectionManager,Selection}.java`,
`TURF/listener/WandListener.java`): NBT key `cnc_setup_wand`, permission node per the existing `/glw cop spawner` commands, left
click block = pos1, right click block = pos2 (point modes use pos1), selection cleared on quit and on world change. `SetupMode` enum
STATION, DISTRICT, HIDEOUT, PICKUP, RESTRICTED, BREAKER -> tag/kind per C15. Outline: one sync repeating task (Interval_Ticks)
drawing the selected cuboid's 12 edges (one particle per block, max 256 per admin per draw) or a 1-block marker for a point, only for
online admins holding the wand, per-player particles with the `WantedHud.spawnForced` pattern
(`CNC/wanted/hud/WantedHud.java:207-227`, copy). Commands as chained `OptionalArgument`s with tab completion (modes, kinds, ids from
the registries, jail ids from the jail registry); `list` prints `kind id name world x y z [tags]`; `tp` teleports to anchor/pos1
or region centre. `setup.yml` is copied out of the module jar by adding "setup" to `CopsNCrooksYamlConfig.FILES` (KERNEL phase; without it the file
never reaches the data folder; `CopsNCrooksYamlConfigTest.shippedDefaultsLiveInModuleFolder` then covers it) and read through a
small `SetupMessages` bean (shape of `WantedMessages`, built in `CopsNCrooksFileConfig` or `RegistryModuleConfig`). The station save
reads `Station_Radius` per call from `copLoader.getLoadedProvider()` (CONTRACTS C8 "Config reads"). commands.json (cops, resources root) entries `cop_setup_wand|mode|save|list|remove|tp|link`.
Tests (red first): `SetupSelectionTest` (corners, world change resets, point modes need pos1 only), `SetupSaveCommandTest`
(station save creates and assigns nearby spawners, duplicate station name refuses and stores nothing, district save creates a tagged
region readable through PlaceNames, pickup save, breaker save stores region + trigger, incomplete selection refuses, remove station
unassigns), `SetupCommandsTest` (`list_printsOneLinePerRow_inTheC15Format`, `list_filtersByKind`, `tp_station_goesToTheAnchor`,
`tp_region_goesToTheCentre`, `link_setsTheJail`, `link_none_unlinks`, `link_unknownIds_refuse`), `SetupOutlineTest`
(`cuboid_drawsTwelveEdges`, `bigCuboid_capsAt256Particles`, `point_drawsOneMarker`, `onlyAdminsHoldingTheWand_seeIt`,
`taskStops_whenNobodyHoldsTheWand`), `SetupWandListenerTest` (wrong permission ignored, non-wand ignored, pos1/pos2 set).
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=SetupSelectionTest,SetupSaveCommandTest,SetupCommandsTest,SetupOutlineTest,SetupWandListenerTest,CopsNCrooksYamlConfigTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Done when: every mode writes only through C5, commands.json has every new path, suite green.

### Task 13: Police bribe stars
Repo gangland | Lane `bribe-stars` | Wave 3 | Depends on: T3, T5, T7 | Model: sonnet

Files - see W3 matrix row T13.
Behaviour: CONTRACTS C14 exactly (Ruling R31, DECISIONS D22). Bean `bribeStars(...)` in `ChaseModuleConfig` (params: `JavaPlugin`,
`SetupPointRegistry`, `@Qualifier("online") UserManager<Player>`, `ChaseConfigLoader`, `WantedMessages`, `CopManager` for the unseen
gate: `copManager.groupOf(id)` and its squad's `millisSinceSighting()` against `evasion().lostSightSeconds()`; both exist on 0.15.2). Item material through
`XMaterial.matchXMaterial(name)` (unknown -> NETHER_STAR + one warning). The task is started in `onInitialize(firstLoad)` and
cancelled in `onClear`/shutdown; `Bribe_Stars.Enable` false = no items, no task work. `wanted_messages.yml` key `Bribe_Star_Taken`
"&6You pocketed a police bribe star. &e-%stars% star(s)." and `Bribe_Star_Seen` "&cNot with a cop watching." with `WantedMessages`
constants + fallbacks.
Tests (red first, BukkitStatics; items mocked): `spawnsAnItemAtEachLoadedPickupPoint`, `wantedPlayerInRange_losesOneStarWithContactCause_andTheItemGoes`,
`notWantedPlayer_takesNothing`, `seenPlayer_takesNothing_andIsToldOncePerFiveSeconds`, `takenStar_respawnsAfterTheTimer`,
`despawnedItem_respawnsAtOnce`, `disabled_spawnsNothing`, `shutdown_removesLiveItems`. `WantedMessagesTest`: both new keys fall back on
an old file.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=BribeStarsTest,WantedMessagesTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Done when: C14 behaviour pinned, suite green.

### Task 14: Turf RegionProvider
Repo gangland | Lane `turf-places` | Wave 3 | Depends on: T3 | Model: haiku

Files - create `TURF/place/TurfRegionProvider.java`, test `TurfRegionProviderTest`; OWN `TURF/TurfModuleConfig.java` (declares
`turfManager(...)` at :119-120).
Behaviour: CONTRACTS C1 provider row. Code (package `org.luckyraven.gangland.turf.place`):
```java
import org.bukkit.Location;
import org.luckyraven.gangland.data.region.PlaceRegion;
import org.luckyraven.gangland.data.region.RegionProvider;
import org.luckyraven.gangland.data.region.RegionShape;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.util.List;
import java.util.Set;

public final class TurfRegionProvider implements RegionProvider {

	private final TurfManager turfs;

	public TurfRegionProvider(TurfManager turfs) {
		this.turfs = turfs;
	}

	@Override
	public String source() {
		return "turf";
	}

	@Override
	public List<PlaceRegion> regionsAt(Location at) {
		Turf turf = turfs.findAt(at);
		if (turf == null) return List.of();

		CuboidRegion region = turf.getRegion();
		Integer      owner  = turf.getOwnerGangId();
		Set<String>  tags   = owner == null ? Set.of(PlaceRegion.TAG_TURF)
		                                    : Set.of(PlaceRegion.TAG_TURF, PlaceRegion.TAG_HIDEOUT);

		return List.of(new PlaceRegion("turf:" + turf.getId(), turf.getDisplayName(), region.getWorld(),
		                               RegionShape.Cuboid.column(region.getMinX(), region.getMinZ(), region.getMaxX(),
		                                                         region.getMaxZ()),
		                               owner == null ? PlaceRegion.NO_OWNER : owner, tags));
	}
}
```
Bean in the turf config: `@Bean public TurfRegionProvider turfRegionProvider(TurfManager turfs, PlaceNames places)`
that registers it and returns it.
Tests (red first): `unclaimedTurf_isATurfWithNoOwner`, `ownedTurf_isAHideoutOwnedByTheGang`, `outsideEveryTurf_isEmpty`, `yIsIgnored`.
Command: `mvn -q -pl gangland-features/gangland-turf -am test -Dtest=TurfRegionProviderTest -Dsurefire.failIfNoSpecifiedTests=false`, then the turf suite.
Done when: turf names answer through `PlaceNames.locate` in a test, suite green.

### Task 15: Containment perimeter
Repo gangland | Lane `perimeter` | Wave 3 | Depends on: T4, T6, T7 | Model: sonnet

Files - see W3 matrix row T15.
Behaviour: CONTRACTS C9 exactly (Rulings R22, R23). `start`: pick up to `Posts` cops of the group that are valid, PURSUING or
COMBAT, and whose `getTargetPlayerId()` is the player (C9: `fightResisting` only reaches such cops), `Roles` order first (CopRole name match), then nearest to the centre, leaving at least one such cop un-posted; ring radius
`min(radius, 0.8 * Sight_Range)` (C9); spots from `PostRing.find(centre, ringRadius, n, Lane_Length)`; each cop
`holdPost(new NpcPost(spot, Leash_Radius, centre))`, `transitionTo(POSTED)`,
`copRadio.sayAs(group, cop, "Post_Up", Map.of("place", copRadio.placeOf(spot)))`. `tick`: drop posts that are dead or no longer
POSTED; a post with `cop.canSee(player, Sight_Range)` -> `group.getSquad().reportSighting(player.getLocation())`,
`sayAs(... "Eyes_On", Map.of("place", copRadio.placeOf(player.getLocation()), "direction",
copRadio.compassWord(post.getEntity().getLocation(), player.getLocation())))` (C6's `compassWord(Location, Location)`; skip the line
when the entity is null), `releasePost`, `transitionTo(PURSUING)`; after `Max_Seconds` -> `end(player, PURSUING)` and mark spent until
the next SEEN. `end` is idempotent. `PostedBehavior.onExit` releases the post. Bean + hook + listener per C9; the listener is a
separate `@ListenerHandler` class. `EvasionModuleConfig.installEvasion` adds the perimeter hook beside the clock hook; update
`EvasionModuleConfigTest` (register `PerimeterController` in its container, assert both hooks: capture with `times(2)` and check
each one ticks its target).
Tests (red first): `PostRingTest` (order of angles, skips unloaded chunks, skips blocked lanes, keeps 90-degree spacing, count),
`PerimeterControllerTest` (cops in every case carry `getTargetPlayerId()` = the player unless the case says otherwise;
`copChasingSomeoneElse_isNeverPosted`, `searchingAtThreeStars_postsMarksmanAndDefender`, `postsSitWithinSightOfTheCentre` (zone 130, Sight 40 ->
ring 32), `twoStars_noPerimeter`, `neverPostsTheLastFreeCop`, `postSeesTheSuspect_reportsRadiosAndPursues`,
`postForcedIntoCombat_isDroppedFromThePerimeter`, `seen_endsWithEveryPostPursuing`, `off_sendsPostsHome`,
`timeout_endsAndStaysSpentUntilSeen`, `disabled_doesNothing`), `PostedBehaviorTest` (ticks the post, no leash give-up,
`onExit_releasesThePost`), `PerimeterListenerTest` (event routing), `EvasionModuleConfigTest` (both hooks installed).
(The "POSTED is never recycled" and "POSTED counts as live" characterization pins land in Task 16, which owns those test classes in W4.)
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=PostRingTest,PerimeterControllerTest,PostedBehaviorTest,PerimeterListenerTest,EvasionModuleConfigTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Config: the controller reads `Cops.Perimeter` per call through `Supplier<CopConfigProvider>` (bean takes `CopLoader`, C8 "Config
reads").
Done when: C9 compiles as written; CopManager and EvasionClock are not edited; suite green.

### Task 16: Hideouts and cold trail on the evasion clock
Repo gangland | Lane `evasion-speed` | Wave 4 | Depends on: T5, T7, T11, T14, T15 | Model: sonnet

Files - see W4 matrix row T16.
Behaviour: CONTRACTS C10 exactly (Rulings R9-R13, R20, R21). `EvasionModuleConfig.evasionClock(...)` gains `Hideouts` and `QuietTrail`
params; beans `hideouts(PlaceNames, GangMembership)` and `quietTrail(HeatLedger, ChaseArcs, ChaseConfigLoader, CopRadio)`;
`installEvasion` also installs `copManager.addAiTickHook(quietTrail::tick)` (update `EvasionModuleConfigTest`: register the new
beans, assert the three hooks). `ChaseArcs`: `offlineTotalMs(UUID)` and the `restore` sum (C10), with the total held on `ChaseArc` (`offlineTotal`, T16 owns
ChaseArc.java); the start anchor reads `arcs.arc(id).startedAt()` (0.15.2 already exposes it, offline-shifted), and the on-chase
test reuses `arcs.hadCrime(id, List.of(last), auto)`. `startSearch` records `seenHideout = hideouts.idAt(centre, id)`. The en-route
hold (C10): `hasLiveCop` also counts `group.unitsEnRoute(now)`, and `tick` holds while no group cop stands in the world. The countdown
(`fireCountdown`) keeps its formula with the new speed, so the boss bar stays honest.
Tests (red first): `EvasionClockTest` (constructor call updated; `setUp` builds `Hideouts`/`QuietTrail` as mocks and stubs
`quiet.speed(any(), anyLong())` -> 1.0 and `hideouts.at(any())`/`idAt(any(), any())` -> null, so every existing case keeps speed 1.0;
C10's `max(1.0, ...)` quiet floor is the backstop): `insideAHideoutReachedUnseen_countsAtHideoutSpeed`,
`hideoutHoldingTheLastSighting_doesNotCount`, `rivalGangHideout_doesNotCount`, `hideoutDisabled_speedOne`,
`quietTwoMinutes_countsOneAndAHalfTimesFaster`, `speedsMultiply_andAreCappedAtMaxSpeed` (outside 2 x hideout 2 x quiet 2 -> 4),
`seen_noMultiplierApplies`, `unitsEnRouteOnly_ownDecay_butMakeNoProgress` (no cop in the world, 30 s pass, wanted 1 star: still 1
star, no track, no countdown; `handlesDecay` true), `restoreGrace_noProgressUntilAUnitSpawns` (RESTORE start, grace + ETA pass with
units queued: no progress, no sighting, and the arc's `quits`/`lastHotAt` unchanged by the grace (SPEC 6: the grace changes
neither the STILL_HOT lock nor quiet); first unit spawns -> the search starts), `stuckUnits_releaseOwnership` (a unit 11 s overdue: `handlesDecay`
false), `postedCop_countsAsLive` (characterization pin), existing cases green. `CopManagerStuckTest`: `postedCop_isNeverRecycled`
(characterization pin: a POSTED cop past Recycle_Seconds stays in the group). `ChaseArcsTest`: `offlineTotalMs_sumsRestoredGaps`.
`HideoutsTest` (owner rule, smallest wins, none -> null). `QuietTrailTest`: `noLedgerCrime_quietFromChaseStart`, `newCrime_resetsQuiet`,
`staleLedgerCrimeBeforeTheChase_quietRunsFromChaseStart` (ledger's last crime 2 h before a sign-raised chase: quietMs = now - startedAt),
`longOfflinePrunedArc_rejoinIsNotQuiet` (no arc kept, RESTORE start: quiet from the rejoin), `noArc_isNotQuiet`,
`offlineTimeIsNotQuiet` (crime, 4 min offline, rejoin: quietMs ~ 0, speed 1.0, backup not held), `sixtySecondsQuiet_holdsBackup_andSaysReturningOnce`,
`freshSighting_neverHolds`, `crimeAfterHold_releases_andRearmsTheLine`, `disabled_neverHolds_speedOne`.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=EvasionClockTest,ChaseArcsTest,CopManagerStuckTest,EvasionModuleConfigTest,HideoutsTest,QuietTrailTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Docket: 0.15.2 D9 row (mid-search carry-over) touched, left open. Done when: C10 compiles; AUTO need and ONE_STAR/ALL_STARS counts unchanged (existing tests); suite green.

### Task 17: Pursuit hand-off and intercept
Repo gangland | Lane `handoff` | Wave 4 | Depends on: T11, T15 | Model: sonnet

Files - see W4 matrix row T17.
Behaviour: CONTRACTS C11 exactly (Ruling R26). Bean `handoffController(CopManager, CopRadio, CopLoader)` in
`CopsNCrooksModuleConfig` (C11; `CopConfigProvider` is not a bean, CONTRACTS C8 "Config reads") with an `@PostConstruct` (or the bean method) adding the AI tick hook; disabled -> the hook returns at once.
Samples are appended every AI tick for wanted players with a group; trimmed to `Heading_Seconds`; cleared when the group or chase
ends (null group). The `%direction%` word is `copRadio.compassWord(oldest, newest)` (C6). The bias carries the newest sample as
`lastSeen` (C8/C11).
Tests (red first): `leashBreak_setsABiasAheadForTenSeconds_andRadiosHandoff`, `bias_carriesTheHandoffPosition_notTheLivePosition`,
`postedCopKeepsTheGroupEngaged`, `stillEngaged_noHandoff`, `notWanted_noHandoff`,
`headingFromTheLastTwoSeconds`, `barelyMoved_usesFacing`, `oneHandoffPerBias`, `disabled_doesNothing`. New
`CNCT/npc/police/CopManagerHandoffTest` (package npc.police, so it can use the package-private `CopManagerFixture`, read only):
`handoffBias_reachesTheDispatcherAndTheSpawnedUnit` (a HandoffController on the fixture sets the bias; the next enqueued unit carries
it and the spawned cop is seeded with `lastSeen`).
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=HandoffControllerTest,CopManagerHandoffTest -Dsurefire.failIfNoSpecifiedTests=false`, then the module suite.
Done when: C11 compiles; the bias reaches `Dispatcher`/`spawnUnit` in `CopManagerHandoffTest`; suite green.

### Task 18: Acceptance scenarios and profiles (authoring only)
Repo: brainstorming (untracked) | Wave 4 | Depends on: W1-W3 reports | Model: sonnet

Files - see W4 matrix row T18. Read `MAIN/brainstorming/cnc-overhaul-2026-10-05/acceptance/README.md` and the 0.15 scripts
(`gen-cnc015.js`, `prep-cnc015.sh`, `run-row.sh`, `run-all-cnc015.sh`, `cnc-verdict.js`) first; copy their shape.
Behaviour: one scenario per Done-when sentence (SPEC 3a) plus two review rows, ids S1-S16: S1 wand places "Northside Station" + "Docks" district;
S2 crime in the Docks -> `Dispatch_En_Route` names Northside Station with an ETA, `UNIT ... hidden=true` after the delay;
S3 three stars -> UNIT lines with tiers 2 and 3; S4 wipe a three-star squad -> `Wipe_Refill` + `reason=wipe hold>0` in the DISPATCH line;
S5 break contact at 3 stars -> PERIMETER start posts=2 + two Post_Up lines; S6 outrun -> HANDOFF line, then a DISPATCH line with
`bias=true` and UNIT lines with `bias=true ahead=true`;
S7 hideout reached unseen -> EVASION hideout=2.0; S8 one quiet minute -> Returning_To_Patrol + EVASION quiet>1; S9 `/glw contact 1`
while unseen -> the chat text of `Wanted_Level.Contact.Used` and a level drop; S10 [WANTED] sign in view -> the chat text of
`Wanted_Level.Contact.Seen`, balance unchanged (S9/S10 grade chat text, there is no contact debug line); S11 rival hits
first then dies -> no star; S12 posted bounty (>= 100) claimed -> no star; S13 kill an unposted five-star player -> +1 star;
S14 downed with Respawn on -> HOSPITAL line, one WARD_BILL at RESPAWN, `SHIELD ... seconds=5` (owner ruling D19); S15 vanilla death with `Death.Respawn.Enable: false` (the
default; MoneyDropListener only runs on this path) -> one WARD_BILL at DEATH, vanilla respawn at the nearest HOSPITAL with a `SHIELD ... seconds=5` line, and no cash
item at the body with the bundled money.yml; S16 logout mid-chase at 1 star, rejoin -> `DISPATCH ... reason=restore hold=15s`,
`EVASION ... hold=enroute`, the star is still there when the first UNIT line appears. Profile `default-016` (bundled files + Debug on
for the module) and `s14` (`Death.Respawn.Enable: true`). Regression list: R1-R4, N1-N8, A1-A9 run unchanged. Verdicts grep the
C8-C13 debug lines and chat text.
Add smoke rows `cnc-016-boot` (all modules + Bartizan + Keystone 1.15.0, no ERROR, "Done (") and `cnc-016-old-keystone`
(Keystone 1.14.0: Gangland logs the floor error from Ruling R47 and disables itself, no stack trace) to `smoke/scenarios.json`.
Done when: `node gen-cnc016.js` writes 16 scenario files; `prep-cnc016.sh default-016` stages a sandbox without errors (dry run).

### Task 19: Documentation, commands/help audit, migration notes
Repo gangland | Lane `docs` | Wave 5 | Depends on: W1-W4 (T1-T18) | Model: sonnet

Files - see W5 matrix row T19. Read every W1-W4 report first.
Behaviour: user docs (`features/cops-n-crooks.md`: stations, districts, wand, dispatch/ETA, mixed squads, breather, perimeter,
hand-off, hideouts, cold trail, bribe stars; `features/wanted-bounty.md`: contacts, sign gate, self-defence keys, Takedown_Minimum;
`features/waypoints.md`: HOSPITAL type, the respawn rule and the hospital respawn shield (`User.Death.Hospital.Shield_Seconds` 5,
0 = off; incoming damage cancelled, ends when the player attacks anything or the time runs out; owner ruling D19); a "Known limits" list in `features/cops-n-crooks.md` for Ruling R46).
Developer docs (`developer/cops-n-crooks.md`: registries, dispatch
queue, SpawnBias, perimeter, Keystone 1.15.0 use; `developer/configuration.md`: every new key with default; `developer/commands.md`).
`gangland-api.md`: the 2.3 list (Ruling R2), RegionProvider how-to (register from a bean). `module-loader.md`: Host_Api table.
`migration-0.16.0.md`: what changes on upgrade (old files keep values; bundled money.yml only for new installs; set
`Death.Respawn.Enable`/HOSPITAL waypoints for hospitals; tier mix needs the new cop_roles.yml entries and is switched off by
removing the `@` suffixes (Ruling R45); a custom `Cops.Radio.Priority` list still gets the two dispatch lines (R43); Keystone 1.15.0
is REQUIRED and Gangland refuses to start on an older one (R47); Host_Api 2.3; the hospital respawn shield is ON for upgraded
servers too (an old settings.yml lacks `Shield_Seconds`, so the code default 5 applies; add `Shield_Seconds: 0` to turn it off);
spawners and jails in a world loaded after Gangland keep their ids now (T-180); downgrade note).
`developer/configuration.md` and `gangland-api.md` include `User.Death.Hospital.Shield_Seconds` / `getHospitalShieldSeconds()` and
`Death.Hospital_Shield`; `developer/cops-n-crooks.md` notes the spawner/jail id floor (T-180).
`documentation/tests/features/*` (the three files exist; extend them): manual checklists for S1-S16, plus a hospital-shield check
in `waypoints.md` (respawn at a hospital, take no damage for 5 s, hit a mob and take damage at once, `Shield_Seconds: 0` = none). Audit both commands.json files against the command classes and
`/glw help` output (report gaps; fix only entries for commands added in 0.16).
Done when: every new key, command, line and type is documented once; links resolve.

### Task 20: CHANGELOG (Markdown + BBCode)
Repo gangland | Lane `changelog` | Wave 6 (after T19 is merged) | Depends on: T19 | Model: haiku

Files - create `documentation/v0.16.0/CHANGELOG.md` and `documentation/v0.16.0/CHANGELOG.bbcode.txt`.
Behaviour: copy the exact structure and heading style of `documentation/v0.15.0/CHANGELOG.md` and `.bbcode.txt` (BBCode: `[SIZE=N][B]`
headings, unicode bar separators, no `[HR]`, no `[HEADING]`). Sections: New (dispatch from stations, mixed squads + breather,
hand-off, perimeter, hideouts, cold trail, crooked contacts + bribe stars, hospital + one bill + 5 s hospital respawn shield, setup wand, districts and place
names), Changed (self-defence keys and guards, [WANTED] sign gate, bundled PLAYER cash drop off, rampage counts heavy crimes),
Fixed (WB-48, US-33/WB-17, T-180 spawner/jail ids no longer reused for rows in a world loaded after Gangland, and every docket
id the reports mark fixed), Configuration (every new key with default, `User.Death.Hospital.Shield_Seconds` 5 included, from
`developer/configuration.md`), API (gangland-api 2.3 list; Keystone 1.15.0), Upgrade notes (from `migration-0.16.0.md`).
Done when: both files list the same items; no item absent from the docs.

### Task 21: Acceptance run, smoke and release report
Repo gangland (integration worktree) + brainstorming | Wave 6 | Depends on: T18, T19 (W1-W5 merged) | Model: sonnet

Files - see W6 matrix row T21.
Behaviour: in `E:/Programming/java/wt/gangland-0.16.0` (with T19 merged, so any commands.json fix is in the build) run
`mvn clean verify` (record test counts) and `mvn clean package`, never `install` (CONSTRAINTS, R42; the orchestrator installed
after the W5 merge);
run smoke rows `cnc-016-boot` and `cnc-016-old-keystone`; run S1-S16 with `run-all-cnc016.sh` (two runs each where the 0.15 verdicts did) and the regression rows
R1-R4, N1-N8, A1-A9 on default profiles. `ACCEPTANCE.md`: a table row per scenario (PASS/FAIL, run ids, the evidence line), failures
with logs and a suspected task, manual checks for anything the bot cannot see (boss bar, the wand's particle outline - its logic
is unit-tested in T12's `SetupOutlineTest`). Do not fix code; report.
Done when: every row has a verdict with evidence; build and test counts recorded; FAIL rows carry a reproduction.
