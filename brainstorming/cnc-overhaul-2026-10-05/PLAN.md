# PLAN - Cops N Crooks 0.15 "Lose them" (Gangland 0.15.0, gangland-api 2.1, Keystone 1.14.0, Bartizan pin 0.6.0)

Integration worktree `E:/Programming/java/wt/gangland-0.15.0`, branch `cnc-lose-them` (from master 16d1f064 = 0.13.0). Every
worker reads `CONSTRAINTS.md` + `CONTRACTS.md` + its own task section only. 18 tasks, 3 implementation waves + 1 release wave.
Lanes: at the start of a wave the orchestrator gives every code/docs task its own worktree and branch
(`git -C E:/Programming/java/wt/gangland-0.15.0 worktree add -b cnc-0.15-t<N> E:/Programming/java/wt/cnc015-t<N> cnc-lose-them`);
T10 and T18 write only untracked brainstorming files in the main checkout and need none (T18 builds in the integration
worktree). The orchestrator saves each lane's final report to the main checkout's
`brainstorming/cnc-overhaul-2026-10-05/reports/T<N>.md`, merges every lane of a wave into `cnc-lose-them`, runs
`mvn -q clean install` + full `mvn test` there, appends the reported triage rows, then starts the next wave.
Merge rule: T6 and T7 merge together or neither (T6 drops the `Kill_Combo.Enable` gate in EntityDamageListener on the
grounds that T7's HeatWantedTracker applies it; T6 alone would give combo stars to a server that disabled the combo).

## Wave diagram
```
W1 foundations (parallel, no cross-compile)
  T1 wanted spine (core)      T2 money formulas+settings+death bill   T3 crime bus+evasion event+dormant events+versions
  T4 chase config+strings     T5 cop config+radio lines+CopManager/CopGroup seams
        |                          |                               |
W2 features I (needs W1)
  T6 wire every star change (T1)   T7 heat ledger (T1,T3,T4,T5)   T8 LOS evasion (T1,T3,T4,T5)
  T9 star-drop charge (T1,T2)      T10 acceptance harness+scenarios (authoring, no code deps)
        |
W3 features II (needs W2)
  T11 HUD & compass (T1,T3,T4,T7,T8)   T12 charge sheet (T4,T6,T7)   T13 paid bounties + self-defence (T2,T6)
  T14 pull back & regroup (T5)         T15 shots give you away (T5; behaviour needs T8)
        |
W4 release (needs W3)
  T16 docs + commands.json/help   T17 CHANGELOG .md + .bbcode.txt   T18 acceptance run + report
```

## File ownership per wave (a file appears once per wave; "new" = created)
W1
| Task | Files |
|---|---|
| T1 | CORE/wanted/{WantedCause,WantedDecayPolicy,WantedStars}.java (new), CORE/wanted/{Wanted,WantedExecutor,WantedSettings}.java, CORE/events/wanted/{WantedLevelChangeEvent,WantedStartEvent,WantedEndEvent}.java, IMPL/file/configuration/wanted/GanglandWantedSettings.java, IMPL/config/DataConfig.java, core tests WantedTest/WantedExecutorTest/WantedStarsTest |
| T2 | CORE/money/MoneyFormula.java (new), API/file/configuration/Settings.java, gangland-impl/src/main/resources/settings.yml, IMPL/listener/player/PlayerDeathListener.java, tests MoneyFormulaTest, SettingsTest, PlayerDeathListenerTest (new) |
| T3 | API/crime/{CrimeService,Crimes}.java, API/events/crime/CrimeCommittedEvent.java, API/events/wanted/{WantedEvasionStateEvent,EvasionState}.java (new), API/GanglandApi.java, IMPL/config/WiringConfig.java, CNC/listener/detainment/CopListener.java, CNC/combo/KillCombo.java, CNC/events/combo/KillComboEvent.java, root pom.xml, cops-n-crooks + gangland-civilians module.yml, tests CrimeServiceTest, KillComboTest, CopListenerDeathTest |
| T4 | CNCR/npc/{wanted,wanted_messages}.yml (new), CNC/wanted/config/* (new), CNC/wanted/WantedMessages.java (new), CNC/config/{ChaseModuleConfig,HeatModuleConfig,EvasionModuleConfig}.java (new), CNC/config/CopsNCrooksYamlConfig.java, CNC/CopsNCrooksModule.java, tests ChaseConfigTest, WantedMessagesTest, CopsNCrooksModuleTest |
| T5 | CNCR/npc/{cops,cop_radio_messages,cop_radio_messages_es}.yml, CNC/npc/police/radio/CopRadioMessages.java, CNC/npc/police/config/{CopConfigProvider,YamlCopConfigProvider}.java, CNC/npc/police/config/{RegroupSettings,ShotNoiseSettings}.java (new), CNC/npc/police/{CopManager,CopGroup}.java, test CopManagerFixture + CopRadioMessagesTest, CopManagerSquadTest, CopManagerStuckTest, CopGroupSquadTest, YamlCopConfigProviderTest |

W2
| Task | Files |
|---|---|
| T6 | IMPL/listener/player/EntityDamageListener.java, IMPL/sign/aspect/WantedAspect.java, IMPL/sign/type/WantedSign.java, IMPL/sign/SignManager.java, IMPL/config/GameplayConfig.java, IMPL/command/sub/wanted/{WantedCommand,WantedAddCommand,WantedRemoveCommand,WantedClearCommand}.java, IMPL/data/user/UserDataLoader.java, IMPL/config/DataConfig.java, CNC/detainment/wanted/WantedClearContract.java, CNC/integration/detainment/GanglandWantedClearContract.java, CNC/detainment/bribe/BribeService.java, CNC/detainment/intake/JailIntakeService.java, tests BountySetCommandTest, WantedAmountGuardTest, SignManagerContributionTest, SignManagerLegacyAliasTest, WantedAspectTest (new), EntityDamageListenerTest (new), UserDataLoaderTest (new), GanglandWantedClearContractTest (new) |
| T7 | CNC/wanted/heat/{HeatLedger,CrimeRecord}.java (new), CNC/seam/HeatWantedTracker.java (new), CNC/seam/KillComboWantedTracker.java (delete) + its test, CNC/listener/wanted/HeatListener.java (new), CNC/config/{HeatModuleConfig,CopsNCrooksModuleConfig}.java, CNC/detainment/breakfree/BreakFreeService.java, CIV/listener/npc/CivilianDeathRewardListener.java, tests HeatLedgerTest, HeatWantedTrackerTest, HeatModuleConfigTest, BreakFreeServiceTest, CivilianDeathRewardListenerTest (all new) |
| T8 | CNC/wanted/evasion/{EvasionClock,EvasionSnapshot}.java (new), CNC/listener/wanted/EvasionListener.java (new), CNC/config/EvasionModuleConfig.java, test EvasionClockTest |
| T9 | CORE/wanted/WantedStars.java (price block of `drop` only), CORE/wanted/WantedSettings.java, IMPL/file/configuration/wanted/GanglandWantedSettings.java, tests WantedStarsTest, WantedExecutorTest, GanglandWantedSettingsTest |
| T10 | MAIN/brainstorming/cnc-overhaul-2026-10-05/acceptance/** (new), E:/Programming/java/wt/_programme/harness/ (copy), MAIN/brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json (MAIN = `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]`; untracked, never committed) |

W3
| Task | Files |
|---|---|
| T11 | CNC/wanted/hud/{StarCard,WantedHud}.java (new), CNC/listener/wanted/WantedHudListener.java (new), tests StarCardTest, WantedHudTest, WantedHudListenerTest (all new) |
| T12 | CNC/detainment/intake/JailIntakeService.java, CNC/detainment/DetainedPlayer.java, CNC/database/{DetainmentTable,DetainmentRepository}.java, CNC/detainment/paperwork/PaperworkView.java, CNC/config/CopsNCrooksModuleConfig.java, tests JailIntakeServiceTest (new), PaperworkViewTest (new), DetainmentRepositoryMigrationTest, DetainmentRepositorySpiTest |
| T13 | CORE/bounty/{Bounty,BountyExecutor}.java, IMPL/listener/player/{EntityDamageListener,RemoveAccountListener}.java, IMPL/database/tables/player/UserTable.java, IMPL/data/user/UserDataLoader.java, IMPL/database/repositories/player/UserRepository.java, tests BountyTest, BountyExecutorTest (new), EntityDamageListenerTest, UserDataLoaderTest, UserBountyLedgerIntegrationTest (new), BountySetCommandTest |
| T14 | CNC/npc/police/{CopGroup,CopManager}.java, CNC/npc/police/radio/CopRadio.java, tests CopGroupSquadTest, CopRadioTest, CopManagerSquadTest |
| T15 | CNC/listener/police/ShotNoiseListener.java (new), test ShotNoiseListenerTest |

W4
| Task | Files |
|---|---|
| T16 | documentation/migration-0.15.0.md (new), documentation/features/{wanted-bounty,cops-n-crooks,jail-detainment}.md, documentation/developer/{configuration,cops-n-crooks}.md, documentation/{gangland-api,module-loader}.md, documentation/tests/features/{wanted-bounty,cops-n-crooks,jail-detainment}.md, (commands.json only if a command was added - none planned) |
| T17 | documentation/v0.15.0/CHANGELOG.md, documentation/v0.15.0/CHANGELOG.bbcode.txt (new) |
| T18 | MAIN/brainstorming/cnc-overhaul-2026-10-05/acceptance/runs/** (new, untracked), E:/Programming/java/wt/_programme/acceptance/cnc-0.15.md (new) |

Sequenced shared files (one owner per wave): EntityDamageListener T6 -> T13; DataConfig T1 -> T6; JailIntakeService T6 -> T12;
CopsNCrooksModuleConfig T7 -> T12; CopManager + CopGroup T5 -> T14; WantedStars/WantedSettings/GanglandWantedSettings T1 -> T9;
CopListener T3 only; UserDataLoader T6 -> T13; BountySetCommandTest T6 -> T13; EntityDamageListenerTest T6 -> T13;
UserDataLoaderTest T6 -> T13; CopManagerSquadTest T5 -> T14; CopGroupSquadTest T5 -> T14; WantedStarsTest + WantedExecutorTest T1 -> T9.

## Risks
1. EntityDamageListener and core wanted are also Gang & Turf W4's files; 0.14 rebases onto 0.15 (spec ruling). Keep diffs
   local to the methods named; T6/T13 must not reformat the file.
2. The mineflayer harness lives in a volatile temp dir (`...\Temp\claude\E--Programming-java-Keystone\testserver-work`);
   T10 copies it first. If it is already gone, T10 reports and T18 falls back to smoke.py boot rows + manual checks.
3. The bot cannot read boss bars or titles: HUD acceptance is a manual checklist; chat/radio/balance lines carry the rest.
4. Persistence: `user.bounty_posters` VARCHAR(4096) caps a ledger at roughly 70 posters (ponytail note in code); the
   detainment table has a hand-written SQLite rebuild that must list the new columns (T12).
5. The decay clock moves from async to sync (WB-06). No listener of `WantedEvent` exists in the repo; external ones now run sync.
6. `WantedLevelChangeEvent` still fires before the level changes (CJ-27 open): every new listener reads `getNewLevel()`.
7. Squad freshness is also written by non-sight events (wanted-start seed, hitting a cop, stuck tip-off). The tip-off is
   paused (C11/C13); the other two are real contact and count as "seen" on purpose.
8. Heat tuning: Assault_Cop is deduped per victim, but a fast multi-kill with streak + seen multipliers climbs 2-3 stars at
   once. Values are the spec's starting values; tuning is config-only.
9. A cuffed player who dies pays the hospital bill, not the charge sheet; a down that becomes an arrest can still pay both
   until 0.16 moves the bill to respawn (spec schedules that for 0.16).
10. Host_Api 2.1 on cops-n-crooks/civilians makes those jars refuse a 2.0 host; they ship together with 0.15.0, so fine.
11. Mockito provider mocks return null for new `CopConfigProvider` getters: every caller null-guards (C10) and the fixture stubs them (T5).

## Rulings
- Ruling: gangland-api goes 2.0 -> 2.1 in T3 and only cops-n-crooks + gangland-civilians declare Host_Api 2.1 - owner ruling (next minor after the current VERSION); only those two use new api - Gang & Turf must renumber to 2.2.
- Ruling: WantedCause, WantedDecayPolicy, WantedStars, MoneyFormula live in gangland-core, the crime bus and evasion event in gangland-api - Wanted/WantedExecutor are core and core cannot see the api; modules see core through the api re-export - a type in the wrong module would force a later move, which the additive rule forbids.
- Ruling: the "one increment method" is `WantedStars.raise(context, stars, origin)`, injected by constructor into the four raise sites - house style is constructor injection, and the clock needs settings the Wanted object lacks - more plumbing (sign + command constructors, 4 test fixtures) than a static seam.
- Ruling: the star-drop charge applies to every DECAY and EVASION drop and is computed in core `WantedStars.drop`; the decay policy only requests drops and never sees a wallet - spec: "charge on every star drop" and "the policy gets no wallet" - if the owner meant evasion drops are always free, T9's price must skip EVASION.
- Ruling: the charge is taken only after the level change succeeded - today a cancelled WantedLevelChangeEvent still charges (WantedExecutor.java:68-77) - a listener relying on the old order sees no charge on cancel.
- Ruling: the safety-net clock runs sync with a sync WantedEvent (WB-06) - it fires events and touches the economy - a trivial main-thread cost every Time seconds per wanted player.
- Ruling: evasion owns decay when Evasion.Enable and the player's group has a live, non-returning cop; otherwise Repeating_Timer decays as today - spec "safety net when no cop can spawn" - a gap between cop deaths and respawns can let one timer tick drop a star.
- Ruling: "seen" = `squad.millisSinceSighting() < Lost_Sight_Seconds*1000`; a stuck-recycle tip-off pauses the clock instead of counting as a sighting - the tip-off is not eyes-on but refreshes freshness - a real sighting inside that pause is only noticed after it.
- Ruling: the countdown accrues only while SEARCHING, any sighting resets it, the zone centre is fixed at the moment sight is lost, and ONE_STAR keeps searching at the new level - spec "stay unseen and the clock runs... get spotted and the zone snaps back" - matches the spec's 10+20+30+45+60 s estimate.
- Ruling: Hideout_Speed is dropped from 0.15 - hideouts arrive with the 0.16 setup wand - the key is added in 0.16 with a code default.
- Ruling: all chase knobs live in a NEW `npc/wanted.yml` (cops-n-crooks), strings in `npc/wanted_messages.yml`; Regroup and Shot_Noise go in `cops.yml` - a new file reaches upgraded servers with its comments; the Regroup/Shot_Noise blocks and the three new radio cooldowns reach them as code defaults (cooldowns merge per key), but a radio `Priority:` list is replaced wholesale by the file's, so 0.15 adds no priority entries and speaks the regroup lines through the gap-bypassing follow-up path (C10/C11) - admins tune Regroup/Shot_Noise by adding the block by hand.
- Ruling: `HeatWantedTracker` replaces `KillComboWantedTracker` and is always installed; Heat.Enable is read per call and `false` restores the old star math (combo or one star per kill) - one install path, live toggle - heat-off is not byte-for-byte the old tracker: the defending-own-turf exemption (tracker) and the self-defence/posted-bounty exemptions (EntityDamageListener) decide whether a kill is a crime at all and hold on both paths, and a civilian kill now counts once instead of twice.
- Ruling: Kill_Combo.Enable keeps its meaning with heat off (combo vs one star per kill) and only gates the streak bonus with heat on; EntityDamageListener routes counted kills to the tracker whenever it is active - avoids a heat-bypassing kill path and the civilian double count - an admin who disabled Kill_Combo now gets heat-weighted stars for kills while heat is on.
- Ruling: civilian kills are published by gangland-civilians as Kill_Civilian (hostile-in-combat exemption kept) and the tracker ignores CIVILIAN marks; without cops-n-crooks a civilian kill mints no star - today one kill counts twice (EntityDamageListener mob path + CivilianDeathRewardListener:48) - a civilians-without-cops server loses that star, which no cop could chase anyway.
- Ruling: 0.15 publishes Kill_Player, Kill_Cop, Kill_Civilian, Assault_Cop (once per attacker+cop per Assault_Repeat_Seconds 10) and Resisting_Arrest (break free); the other weights ship unpublished - only these crimes exist in code today - later crimes need only a publisher, no api bump.
- Ruling: Turf_War_Multiplier (0.5) applies to Kill_Player inside a CONTESTING turf only; a player kill made by a member of the gang that owns that contested turf (defending your own turf) is not a crime at all - no heat, no combo, no star, on both heat paths (HeatWantedTracker.recordKill, C12) - owner ruling "turf-war kills half heat, turf-defender kills none" = Gang & Turf P6.4 "kills you make defending your own turf during an attack do not raise your wanted level" (docket TF-49, fixed here instead of in 0.14) - cop/civilian kills there keep full heat; the exemption is inert until the gang module is installed (gangIdOf = -1); Gang & Turf P6.4 shrinks to "verify" when it rebases.
- Ruling: heat floors to the new level's threshold on any drop, rises to it on non-crime raises, starts at floor(level) for a restored chase and is cleared at WantedEndEvent - stars and heat must not disagree - a crime right after a drop needs the full step again.
- Ruling: a crime crossing several thresholds calls the core trigger (handleWanted) once per star - keeps notoriety and messages per star - several cards flash in one tick.
- Ruling: seenByCop on the event is the publisher's knowledge; the ledger ORs its own squad freshness check - core/civilians publishers cannot see squads - other listeners may see false for a crime a cop watched.
- Ruling: KillComboEvent fires through Bukkit on the combo path with a Kind (INCREMENT/WANTED_TRIGGER/RESET); with heat on there is no combo object and CrimeCommittedEvent is the public signal - the event wraps a live KillComboTracker - an external KillComboEvent listener hears nothing on default config.
- Ruling: the charge sheet total is `min(Maximum, Base + Per_Wanted_Level x wantedAtArrest)` (200/250/10000), crimes are listed by name, unpaid money becomes `ceil(unpaid x 0.1)` s capped at 600 - spec: "priced linearly by base plus per star until the heat ledger has prices, and capped"; the Done-when $700 sheet at two stars - per-crime prices come later with a Fine column.
- Ruling: fine knobs live in `ChargeSheetSettings` (module YAML), not `DetainmentCostsContract` - that contract's getters are core-settings-backed and new knobs belong in module YAML - the spec's `computeFineCost` name is not used.
- Ruling: no charge sheet when the arrest commits on death (`player.isDead()`) - the hospital bill was already charged at LOWEST (DetainmentListener.onDeath:66-71) - those players pay the bill, not the sheet, until 0.16.
- Ruling: only fine_paid and fine_extra_seconds are persisted; the crime list is shown in chat at intake - the paperwork "lists both" figures - the crime list is gone after a relog.
- Ruling: the per-poster bounty ledger is persisted in one nullable `bounty_posters` column; NULL marks a pre-0.15 row whose whole amount counts as posted once (id "legacy") - spec: a restart keeps the refund, and every old bounty pays in full once - one last payout of server-made money (spec-accepted).
- Ruling: a claim pays the posted total (sum of paid figures) and is not a crime; a notoriety-only target pays nothing and takes the crime path; Pay_Notoriety true pays everything as before - spec n-paid-bounties - none.
- Ruling: gangmates and allies never collect each other's bounty (`GangMembership.alliedOrSame`) - CONSTRAINTS Fair PvP guardrail, docket WB-43 - inert until the gang module installs, so gangless alts can still claim posted money (which someone paid).
- Ruling (OWNER CHECK): notoriety is not cleared on death or arrest - the spec sentence "Notoriety stays until death or arrest clears it, as today" contradicts itself (today only a player-kill claim's `resetBounty` clears a bounty, EntityDamageListener.java:130; no death or arrest path does), so 0.15 keeps today's code - a criminal's notoriety only grows (capped by BountyExecutor at Maximum) until a posted or Pay_Notoriety claim; if the owner wants the literal reading, a WantedEndEvent(DEATH/ARREST) handler that sets amount = postedAmount is a one-method follow-up. T16 states the behaviour in migration-0.15.0.md.
- Ruling: the [BOUNTY] sign CLEAR (BountyAspect.java:51-60, WB-22) is unchanged - spec silent - it still burns posters' escrow.
- Ruling: Regroup counts MAN_DOWN/LEADER_DOWN while the group is in combat alert (cuff-first = not), ends when cops within Arrival_Radius 24 reach the target count or the 15 s fall-back runs out, and uses new radio keys Regroup / Regroup_Push spoken through `CopRadio.sayFromLeaderLater` (SquadRadio.sayLater: past the squad and player gaps, so the Man_Down that triggers Regroup cannot swallow it, on fresh and upgraded cops.yml alike) - spawns arrive at once in spawnTick, so "count reaches target" alone would end a regroup before anyone arrives; the existing Push line needs %member% - a server with a tiny spawn distance sees short regroups; the Regroup line lands one or two ack delays (about 1-2 s) after the casualty line.
- Ruling: Shot noise reads `Weapon.getCategory()`; unlisted weapon types (INCENDIARY, BIOLOGICAL, BEAM, OTHER) make no noise; the 3 s throttle is the per-squad radio cooldown `Shots_Fired: 60` - spec lists three types; one squad hunts one player - an admin adds BEAM etc. by hand.
- Ruling: HUD boss bar RED seen, YELLOW/WHITE flashing while searching, GREEN for 3 s after a drop that leaves stars; title+siren on every raise; the star card replaces the chat line via `WantedStars.suppressStarChat`; the last star's drop (the getaway) sends its drop card and the bar hides with the chase; compass target restored when the chase ends; a state event for a player whose bar is hidden is ignored - owner approved the bar (stars + countdown, never awareness) - colours repeat turf/car bar colours; titles tell them apart; no green bar for the final escape.
- Ruling: the star card uses the tier's shipped singular Display_Name ("Sergeant inbound"), not the spec example's plural - the card is built from tier fields the config already has (spec) - an admin wanting plurals edits Display_Name.
- Ruling: the death bill skips entirely when Lose_Money is false, falls back to `balance * 0.15`, and withdraws through `User.withdraw` (clamped) - spec + US-05 - a server using Lose_Money:false as a payout loses it (Death.Money.Command replaces it).
- Ruling: Bartizan stays pinned at 0.6.0 - WeaponShootEvent/Weapon.getCategory are unchanged in 0.6.1 - none.
- Ruling: no new commands and no new placeholders in 0.15; commands.json is unchanged - no feature needs one - an admin cannot read heat in game except through the star card.
- Ruling: release notes go to `documentation/migration-0.15.0.md` + `documentation/v0.15.0/CHANGELOG.{md,bbcode.txt}` - migration docs are the 0.9+ convention, the CHANGELOG pair is the house rule - none.
- Ruling: workers never edit triage files or the docket db; the orchestrator writes them after each wave - parallel lanes would collide - a missed report means a missing row.
- Ruling: a self-defence kill is not a crime: when the victim's first hit on the killer in this fight came before the killer's first hit (a fight ends after 30 s with no hit either way), the kill mints no Kill_Player, no star and no notoriety; tracked in EntityDamageListener next to the posted-bounty exemption (T13, C16) - owner took every recommended decision: "Is killing a wanted player nobody has put money on a crime? Yes, unless it was self-defence" and the principle "Defending yourself ... is not a crime" - first hit wins, so a player who lands one punch and walks away can bait a free kill for 30 s; fixed window, no config key.
- Ruling: each lane works in its own worktree `E:/Programming/java/wt/cnc015-t<N>` and never runs `mvn install` (`-am` resolves siblings and their test-jars from the reactor); only the orchestrator installs, in the integration worktree, after a merge - two branches cannot share one working tree, and parallel installs of the same coordinates race in `~/.m2` - one worktree per lane per wave to create and prune.
- Ruling: T6 and T7 merge together or neither - T6's removal of the Kill_Combo.Enable gate is only correct with T7's tracker - a failed T7 holds T6 back a wave.
- Ruling: the graphify graph is refreshed by the orchestrator after `cnc-lose-them` merges to master - the graph tracks the main checkout - none.

---

### Task 1: Wanted spine - causes, WantedStars and the decay-policy seam (gangland-core)
Lane `core-wanted` | Wave 1 | Depends on: none | Model: opus

Files - create: `CORE/wanted/WantedCause.java`, `CORE/wanted/WantedDecayPolicy.java`, `CORE/wanted/WantedStars.java`,
`gangland-core/src/test/java/org/luckyraven/gangland/core/wanted/WantedStarsTest.java`. Modify (OWN): `CORE/wanted/Wanted.java`,
`CORE/wanted/WantedExecutor.java`, `CORE/wanted/WantedSettings.java` (add only `isTimerEnabled()` default), the three events in
`CORE/events/wanted/` (`WantedLevelChangeEvent`, `WantedStartEvent`, `WantedEndEvent`; leave `WantedEvent`),
`IMPL/file/configuration/wanted/GanglandWantedSettings.java` (override `isTimerEnabled()` -> `Settings.isWantedTimerEnabled()`),
`IMPL/config/DataConfig.java` (add `@Bean public WantedStars wantedStars(WantedSettings wantedSettings)` returning
`new WantedStars(gangland, wantedSettings)` beside `wantedKillTrackers()` :136), tests `WantedTest.java`, `WantedExecutorTest.java`.
Do NOT touch any caller of Wanted (Task 6) nor the money rule (Task 9).

Behaviour: implement CONTRACTS C1, C2, C3 exactly (signatures, order inside `drop`, thread hops). In this task the star-drop
price inside `drop` is today's rule moved verbatim from `WantedExecutor.java:54-60` (Amount > 0 -> Amount x Multiplier^level,
per star, level = the level before that star falls), summed and withdrawn once with `context.withdraw`, AFTER the level change
succeeded. `WantedExecutor.execute` = C3 order; the 4-arg constructor stays and the existing
`cancelledTick_thenUncancelledTick_decrements` must stay green with exactly ONE allowed edit: its `setUp` adds
`bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);` (the tick now calls `WantedStars.drop`, which hops off-thread;
the bare `mockStatic` answers false and `getScheduler()` null). No other change to that test. `startDecayClock` uses a SYNC timer
(`start(false)`) and `new WantedEvent(false, wanted)`. Old no-cause methods delegate with `WantedCause.UNKNOWN`.

Tests (red first; `Wanted` with an owner needs a mocked static `Bukkit` - `isPrimaryThread`, `getPluginManager`, `getScheduler` -
use `BukkitStatics.install()` and stub `isPrimaryThread` per CONSTRAINTS "Thread stubs": true for main-thread cases,
`thenReturn(false, true)` for the three off-thread cases, or the inline `runTask` re-run recurses):
- `WantedTest`: `setLevelWithCause_changeStartAndEndEventsCarryTheCause`; `setLevelOffThread_reschedulesWithTheSameCause`;
  `resetWithCause_endEventCarriesItAndStopsTheTimer`; `legacySetLevel_firesUnknownCause`.
- `WantedStarsTest`: `raise_addsStarsWithTheOrigin_andStartsASyncClock` (scheduler `runTaskTimer`, not the async variant);
  `raise_timerDisabled_startsNoClock`; `raise_atMaximum_returnsZero`; `raise_cancelledChange_returnsZeroAndStartsNoClock`;
  `drop_lowersWithTheCause_andSendsTheDecreasedMessageForTheNewLevel`; `drop_chargesAmountTimesMultiplierPowLevelPerStar`
  (Amount 50, Multiplier 5: one star from 2 -> 1,250; two stars from 2 -> 1,250 + 250 = 1,500);
  `drop_cancelledChange_chargesNothing` (red against today's executor order); `drop_toZero_stopsTheClock`;
  `drop_offMainThread_hopsToTheMainThread`; `restore_offMainThread_setsRestoreLevel_andStartsTheClock`;
  `restore_zero_startsNoClock`; `isDecayHandled_onlyWithAPolicyAndAnOwner`; `suppressStarChat_supplierTrue_hidesTheChatLine`.
- `WantedExecutorTest`: `installedPolicyHandlingDecay_tickChangesNothing_andFiresNoEvent`; `tick_dropsThroughWantedStarsWithDecayCause`.
Command: `mvn -q -pl gangland-core -am test -Dtest=WantedTest,WantedStarsTest,WantedExecutorTest -Dsurefire.failIfNoSpecifiedTests=false`,
then `mvn -q -pl gangland-impl -am test` (DataConfig, HolderSeamBeanTypeTest, everything else still green).

Docket: WB-06 wanted half (sync clock) - seam ready, counted fixed when Task 6 routes callers; CJ-27 touched, left open.
New bug to report (wanted-bounty-combat): "the star-drop charge is withdrawn before the level change, so a cancelled
WantedLevelChangeEvent still charges (WantedExecutor.java:68-77)" - fixed by this task.

Done when: C1-C3 compile exactly as written; every listed test was red then green; gangland-core and gangland-impl suites green.

### Task 2: Safe money formulas, new settings keys and the death bill fix
Lane `money` | Wave 1 | Depends on: none | Model: sonnet

Files - create: `CORE/money/MoneyFormula.java`, `gangland-core/src/test/java/org/luckyraven/gangland/core/money/MoneyFormulaTest.java`,
`gangland-impl/src/test/java/org/luckyraven/gangland/listener/player/PlayerDeathListenerTest.java`. Modify (OWN):
`API/file/configuration/Settings.java`, `gangland-impl/src/main/resources/settings.yml`,
`IMPL/listener/player/PlayerDeathListener.java`, `gangland-impl/src/test/java/org/luckyraven/gangland/file/configuration/SettingsTest.java`.

Behaviour: CONTRACTS C4 and C5 exactly. settings.yml `Wanted.Take_Money` becomes the spec block (SPEC-0.15.md lines 131-157,
3-space indent, block style); add `Bounty.Pay_Notoriety: false` with its comment; rewrite the `Lose_Money` comment. In
`PlayerDeathListener.handleMoney` (:131-161): (1) `if (!Settings.isDeathLoseMoney()) return;` and delete the deposit branch
(:151-153) - false means no charge and no payout; (2) `deduct = MoneyFormula.evaluate(Settings.getDeathLoseMoneyFormula(),
MoneyFormula.userVariables(user), balance * 0.15)` and delete `amountDeduction` (:213-227); (3) keep the `== 0` skip, the bank
discount and the `<= 0` skip; (4) withdraw with `BigDecimal taken = user.withdraw(Currency.of(deduct))` (clamped, never throws);
`taken.signum() == 0` -> no message; the message prints `taken` with today's text. `handleCommandExecution` and the threshold
are unchanged. Make `handleMoney` package-private with a "test seam" comment (or drive tests through `onPlayerDeath`).

Tests (red first):
- `MoneyFormulaTest`: `validFormula_evaluates` ("balance * 0.15", balance 1000 -> 150); `badSyntax_returnsFallback` ("balance *");
  `unknownVariable_returnsFallback` ("cash * 2"); `divisionByZero_returnsFallback` ("balance / 0");
  `negativeResult_returnsFallback` ("0 - balance"); `nanResult_returnsFallback` ("ln(0 - 1)");
  `negativeFallback_returnsZero`; `warning_loggedOncePerFormulaText` (same bad text twice -> 1 warning, another text -> 2; via `warnSink`);
  `starDropDefault_evaluatesWithoutWarning` ("amount * multiplier ^ wanted", amount 50, multiplier 5, wanted 2 -> 1250, zero
  warnings); `userVariables_matchTheDeathFormulaSet` (balance, level, experience, bounty, wanted). `@BeforeEach` calls
  `MoneyFormula.resetWarnings()` and captures `warnSink`; `@AfterEach` restores it (the warned set is JVM-static).
- `SettingsTest`: `takeMoneyEnable_absent_isFalse`, `takeMoneyFormula_absent_isTheDefault`, `takeMoneyEnable_true_isRead`,
  `payNotoriety_absent_isFalse`, `bundledSettings_takeMoneyOffByDefault`.
- `PlayerDeathListenerTest` (SettingsFixture + BukkitStatics, real User/EconomyHandler like BountySetCommandTest):
  `loseMoneyFalse_chargesNothing_andPaysNothing` (red: deposits today); `brokenFormula_charges15PercentFallback_withoutThrowing`
  (red: throws); `formulaAboveBalance_takesTheWallet_withoutThrowing` (red: EconomyException); `zeroFormula_sendsNoMessage`;
  `balanceAtThreshold_paysNothing`.
Commands: `mvn -q -pl gangland-core -am test -Dtest=MoneyFormulaTest -Dsurefire.failIfNoSpecifiedTests=false`;
`mvn -q -pl gangland-impl -am test -Dtest=SettingsTest,PlayerDeathListenerTest -Dsurefire.failIfNoSpecifiedTests=false`; both full suites.

Docket: US-05 fixed (clamped withdraw). New bugs to report (users-levels-economy-bank): "Lose_Money:false pays the player the
formula amount on every death (PlayerDeathListener.java:148-154)"; "a broken death Formula throws out of the death handler and
NaN reaches Currency.of (PlayerDeathListener.java:213-227)" - both fixed here. WB-41 untouched.

Done when: an upgraded settings.yml without `Take_Money.Enable` reads false; all tests red-then-green; both suites green.

### Task 3: Crime event bus, evasion state event, dormant events and the version bump
Lane `api` | Wave 1 | Depends on: none | Model: sonnet

Files - create: `API/crime/CrimeService.java`, `API/crime/Crimes.java`, `API/events/crime/CrimeCommittedEvent.java`,
`API/events/wanted/WantedEvasionStateEvent.java`, `API/events/wanted/EvasionState.java`,
`gangland-api/src/test/java/org/luckyraven/gangland/crime/CrimeServiceTest.java`,
`gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/combo/KillComboTest.java`. Modify (OWN):
`API/GanglandApi.java` (`VERSION = "2.1"`, javadoc line naming what 2.1 adds), `IMPL/config/WiringConfig.java`
(`@Bean public CrimeService crimeService()`), `CNC/listener/detainment/CopListener.java` (onCopDeath only), `CNC/combo/KillCombo.java`,
`CNC/events/combo/KillComboEvent.java`, root `pom.xml:57` (`<revision>0.15.0</revision>`),
`gangland-features/cops-n-crooks/src/main/resources/module.yml` and `gangland-features/gangland-civilians/src/main/resources/module.yml`
(`Host_Api: 2.1`; every other module.yml stays 2.0), test `CopListenerDeathTest.java`.

Behaviour: CONTRACTS C6, C7, C8 exactly. `CrimeService.commit` builds the event, `Bukkit.getPluginManager().callEvent(e)`,
returns `!e.isCancelled()`. KillComboEvent kinds fire at the three KillCombo moments; RESET is fired from
`Bukkit.getScheduler().runTask(plugin, ..)` because the tracker timer is async; existing Consumers still get the event.

Tests (red first): `CrimeServiceTest`: `commit_firesTheEventWithEveryField`, `commit_cancelledByAListener_returnsFalse`,
`shortCommit_defaultsToUnseenAndNoWitnesses`. `KillComboTest`: `recordKill_firesIncrementThroughBukkit`,
`thresholdReached_firesWantedTrigger_andStillRunsTheConsumer`, `resetCombo_firesResetOnTheMainThread`.
`CopListenerDeathTest`: `copDeath_firesCopDeathEvent_withTheKiller`, `copDeath_noKiller_firesWithNull`.
Commands: `mvn -q -pl gangland-api -am test -Dtest=CrimeServiceTest -Dsurefire.failIfNoSpecifiedTests=false`;
`mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=KillComboTest,CopListenerDeathTest,KillComboWantedTrackerTest -Dsurefire.failIfNoSpecifiedTests=false`;
then `mvn -q clean verify -DskipTests` (plugin.yml/module.yml in `target/` now show 0.15.0; no install, CONSTRAINTS "Build").

Docket: CJ-39 fixed, WB-29 fixed, WB-28 (increment now publicly signalled; report whether you deleted the dead Consumer).

Done when: the five api types match C6/C7; jars build as 0.15.0; listed tests red-then-green; api and cops-n-crooks suites green.

### Task 4: Chase config file, records, loader and strings (cops-n-crooks)
Lane `cnc-config` | Wave 1 | Depends on: none | Model: sonnet

Files - create: `CNCR/npc/wanted.yml`, `CNCR/npc/wanted_messages.yml`, `CNC/wanted/config/{ChaseConfig,HeatSettings,
EvasionSettings,DropMode,HudSettings,ChargeSheetSettings,ChaseConfigLoader}.java`, `CNC/wanted/WantedMessages.java`,
`CNC/config/ChaseModuleConfig.java`, `CNC/config/HeatModuleConfig.java` (empty shell), `CNC/config/EvasionModuleConfig.java`
(empty shell), tests `.../copsncrooks/wanted/config/ChaseConfigTest.java`, `.../copsncrooks/wanted/WantedMessagesTest.java`.
Modify (OWN): `CNC/config/CopsNCrooksYamlConfig.java` (two `addFile` lines: `wanted`, `wanted_messages`),
`CNC/CopsNCrooksModule.java` (register ChaseModuleConfig, HeatModuleConfig, EvasionModuleConfig after CopsNCrooksModuleConfig),
test `gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/CopsNCrooksModuleTest.java` (its
`configure_declaresConfigsAndPackages` pins the exact configuration list at :24-25: flip it to the six classes in order
Yaml, File, Module, Chase, Heat, Evasion, and update its DisplayName and class javadoc; write the new expectation first and
watch it fail against today's `configure`).

Behaviour: CONTRACTS C9 exactly - every key path, default, record, helper and Key. YAML block style, 3-space indent, a comment
on every key (Crimes comment: "Kill_*, Assault_Cop and Resisting_Arrest are reported in 0.15.0; the rest are read by later
releases"; Evasion comment: "replaces the fixed decay timer while cops hunt you; settings.yml Wanted.Repeating_Timer stays the
safety net"). `ChaseConfigLoader` mirrors `CopLoader` (FileLoader, `resolvePrimaryHandler` -> `wanted`, `FileHandlerReader.read`,
`reader.get("Wanted").asMapping().orNull()` -> `NodeReader.of(..)`; lists via `asList().ofInts().orEmpty()`, maps via `keys()`).
Bean methods per C9; the two shells hold only the `(JavaPlugin plugin, DependencyContainer container)` constructor.

Tests (red first): `ChaseConfigTest`: `parse_nullRoot_isDefault`; `parse_bundledFile_equalsDefault` (read the shipped resource);
`parse_overridesEveryBlock`; `unknownDropMode_fallsBackToOneStar_andReports`; `heatStarsFor` (99 -> 0, 100 -> 1, 449 -> 2,
1000 -> 5 at max 5); `heatStarsFor_resizesAShortList`; `heatFloorOf` (0 -> 0, 2 -> 250); `evasionRadiusFor_clamps` (0 -> 40,
7 -> 180); `chargeSheetFineFor` (2 -> 700, 100 -> 10000); `chargeSheetExtraSecondsFor` (400 -> 40, 1e9 -> 600, 0 -> 0).
`WantedMessagesTest`: `format_replacesPlaceholdersAndColours_fallsBackWhenMissing`; `crimeName_fallsBackToTheSpacedId`;
`duration_formats` (45 -> "45s", 65 -> "1m 05s").
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=ChaseConfigTest,WantedMessagesTest,CopsNCrooksModuleTest -Dsurefire.failIfNoSpecifiedTests=false`,
then the full cops-n-crooks suite (module/configuration scans must accept the empty shells; if a scan rejects a bean-less
@Configuration, give each shell nothing but a no-op `@PostConstruct` and say so in the report).

Docket: none.

Done when: both YAML files ship in the module jar under `npc/`; C9 compiles as written; tests red-then-green; suite green.

### Task 5: Cop config blocks, radio lines and the CopManager/CopGroup seams
Lane `cnc-seams` | Wave 1 | Depends on: none | Model: sonnet

Files - create: `CNC/npc/police/config/RegroupSettings.java`, `CNC/npc/police/config/ShotNoiseSettings.java`.
Modify (OWN): `gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/npc/police/config/YamlCopConfigProviderTest.java`
(add the parse cases below beside its radio/backup/retreat/stuck cases), `CNCR/npc/cops.yml`, `CNCR/npc/cop_radio_messages.yml`, `CNCR/npc/cop_radio_messages_es.yml`,
`CNC/npc/police/radio/CopRadioMessages.java`, `CNC/npc/police/config/CopConfigProvider.java`,
`CNC/npc/police/config/YamlCopConfigProvider.java`, `CNC/npc/police/CopManager.java`, `CNC/npc/police/CopGroup.java`, tests
`CopManagerFixture.java` (stub `getRegroupSettings`/`getShotNoiseSettings` with DEFAULT), `CopRadioMessagesTest.java`,
`CopManagerSquadTest.java`, `CopManagerStuckTest.java`, `CopGroupSquadTest.java`.

Behaviour: CONTRACTS C10 and the T5 part of C11 exactly (cops.yml blocks and radio cooldown entries - NO new Priority entries -,
the two records, the two provider getters, COP_RADIO_DEFAULTS cooldowns, the aiTick hook on BOTH aiTick paths (C11), parse like `parseBackupSettings` :859-873, radio lines in DEFAULT_LINES +
both yml files - write natural Spanish for `_es` - the three CopManager methods, the tip-off stamp in `recycles`, and the two
CopGroup tip-off methods). Do NOT add any regroup state (Task 14) or evasion logic (Task 8).

Tests (red first): `YamlCopConfigProviderTest`: `absentBlocks_areDefault`, `regroup_isParsed_secondsToMillis`, `shotNoise_radiusFor_isCaseInsensitive_unlistedIsZero`,
`shotNoiseDisabled_radiusIsZero`, `bundledCopsYml_equalsDefaults`, `preUpgradeRadioBlock_getsTheNewCooldownsAsDefaults` (a
`Cops.Radio` block with today's `Priority:`/`Cooldown_Ticks:` lists -> Regroup/Regroup_Push/Shots_Fired cooldowns present).
`CopRadioMessagesTest`: `newLines_exist_andNeedNoMember`
(Regroup, Regroup_Push, Shots_Fired). `CopManagerSquadTest`: `aiTickHook_runsOncePerAiTick_withTheGroup`,
`aiTickHook_runsForAnEmptyGroup_beforeTheEarlyReturn` (wanted player, group with no cops -> the hook gets that group once;
red: today's branch returns before the end of aiTick), `aiTickHook_runsWithNullWhenNoGroupExists`,
`throwingAiTickHook_doesNotStopTheTick`, `copAttackedHook_receivesTheCopAndTheAttacker`, `groupOf_returnsTheHuntedGroup`.
`CopManagerStuckTest`: `recycle_marksATipOff`, `noRecycle_noTipOff`. `CopGroupSquadTest`: `tippedOffWithin_window`.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=CopRadioMessagesTest,CopManagerSquadTest,CopManagerStuckTest,CopGroupSquadTest,YamlCopConfigProviderTest -Dsurefire.failIfNoSpecifiedTests=false`, then the full suite.

Docket: none.

Done when: C10/C11 (T5 part) compile as written; existing radio/squad tests unchanged and green; listed tests red-then-green.

### Task 6: Route every star change through WantedStars with its cause
Lane `wiring` | Wave 2 | Depends on: Task 1 | Model: sonnet

Files - modify (OWN): `IMPL/listener/player/EntityDamageListener.java`, `IMPL/sign/aspect/WantedAspect.java`,
`IMPL/sign/type/WantedSign.java`, `IMPL/sign/SignManager.java`, `IMPL/config/GameplayConfig.java` (SignManager bean :255 only),
`IMPL/command/sub/wanted/{WantedCommand,WantedAddCommand,WantedRemoveCommand,WantedClearCommand}.java`,
`IMPL/data/user/UserDataLoader.java`, `IMPL/config/DataConfig.java` (userDataLoader bean :97-100 only),
`CNC/detainment/wanted/WantedClearContract.java`, `CNC/integration/detainment/GanglandWantedClearContract.java`,
`CNC/detainment/bribe/BribeService.java` (:70 only), `CNC/detainment/intake/JailIntakeService.java` (:54 only).
Tests - modify: `BountySetCommandTest` (EntityDamageListener ctor), `WantedAmountGuardTest`, `SignManagerContributionTest`,
`SignManagerLegacyAliasTest` (ctor); create: `gangland-impl/src/test/java/org/luckyraven/gangland/sign/aspect/WantedAspectTest.java`,
`gangland-impl/src/test/java/org/luckyraven/gangland/listener/player/EntityDamageListenerTest.java` (build the listener ONLY
through one private `listener(..)` factory method, so Task 13 next wave changes the constructor in one place),
`gangland-impl/src/test/java/org/luckyraven/gangland/data/user/UserDataLoaderTest.java` (real SQLite per TESTING sec 5 and
CONSTRAINTS: `@TempDir(cleanup = CleanupMode.NEVER)`, disconnect in `@AfterEach`, then `DbFiles.release`; model the database
setup on `database/repositories/player/BankAmountGuardIntegrationTest.java`; build the loader through one private factory),
`gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/integration/detainment/GanglandWantedClearContractTest.java`.

Behaviour: CONTRACTS C18 row by row, plus the routing rule of C12: in EntityDamageListener drop
`&& Settings.isWantedKillComboEnabled()` at :110, :139, :145, :160 (the tracker now applies Kill_Combo.Enable). `handleWanted`
keeps its bounty block untouched (Task 13 owns it next wave) and sends the chat line (:241-245) only when
`wantedStars.isStarChat()`. `onPlayerDeathResetWanted`: return when `Bukkit.getPlayer(id)` is null, then `reset(DEATH)`.
UserDataLoader: replace `getWanted().setLevel(wanted)` (:105) with `wantedStars.restore(user, wanted)` and delete the timer block
(:165-170); leave the bounty timer block (Task 13). WantedAspect also guards `WantedType.valueOf` (:26, :70): unknown content ->
`AspectResult.failure(..)` / `canExecute` false (docket WB-21/LS-22, WantedAspect half). Constructor plumbing per C18
"Injection" (WantedStars appended as the LAST parameter everywhere).

Tests (red first: write each assertion against today's constructor first and watch it fail, then switch constructors):
- `WantedAspectTest`: `increase_startsADecayClock` (real WantedStars, BukkitStatics; today the timer stays null);
  `increase_tagsSignCause`; `remove_lowersByTheAmount_withSignCause`; `clear_resetsWithSignCause`;
  `unknownContent_failsInsteadOfThrowing`.
- `WantedAmountGuardTest` (add a case): `add_startsADecayClock_andReportsTheAddedCount`.
- `EntityDamageListenerTest`: `kill_raisesWithCrimeCause_andStartsASyncClock`; `suppressedStarChat_sendsNoChatLine`;
  `comboDisabled_activeTracker_stillGetsTheKill`; `deathResetForAnOfflinePlayer_doesNotThrow` (report if it was already green).
- `UserDataLoaderTest`: `login_restoresTheSavedLevelThroughWantedStars` (a saved row with wanted 2, online owner ->
  `wantedStars.restore(user, 2)` once; red today: the loader calls `setLevel` and starts its own async timer);
  `login_noWantedLevel_restoresNothing`.
- `GanglandWantedClearContractTest`: `clearWithCause_endEventCarriesTheCause`, `offlinePlayer_isANoOp`.
Commands: `mvn -q -pl gangland-impl -am test -Dtest=WantedAspectTest,WantedAmountGuardTest,EntityDamageListenerTest,UserDataLoaderTest,BountySetCommandTest,SignManagerContributionTest,SignManagerLegacyAliasTest -Dsurefire.failIfNoSpecifiedTests=false`;
`mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=GanglandWantedClearContractTest -Dsurefire.failIfNoSpecifiedTests=false`; both full suites.

Merge: lands together with Task 7 or not at all (PLAN header merge rule).

Docket: WB-20 fixed, WB-04 fixed (covered by UserDataLoaderTest + T1's restore tests), WB-06 + US-18 wanted halves fixed (every clock now via `startDecayClock`), WB-21/LS-22
WantedAspect half fixed (BountyAspect half left open). Report the death-reset NPE as a new row if `getUser(null)` threw.

Done when: no caller of `Wanted.setLevel/incrementLevel/decrementLevel/reset` outside core uses the no-cause form
(`grep` proves it; exceptions: the offline-copy sites in RemoveAccountListener/UserRepository keep `setLevel(int)` - owner null,
no events - and CivilianDeathRewardListener, which Task 7 rewrites in this same wave);
tests red-then-green; impl and cops-n-crooks suites green.

### Task 7: Heat ledger - every crime feeds one ledger, heat becomes stars
Lane `heat` | Wave 2 | Depends on: Tasks 1, 3, 4, 5 | Model: sonnet

Files - create: `CNC/wanted/heat/HeatLedger.java`, `CNC/wanted/heat/CrimeRecord.java`, `CNC/seam/HeatWantedTracker.java`,
`CNC/listener/wanted/HeatListener.java`, tests `.../copsncrooks/wanted/heat/HeatLedgerTest.java`,
`.../copsncrooks/seam/HeatWantedTrackerTest.java`, `.../copsncrooks/config/HeatModuleConfigTest.java`,
`.../copsncrooks/detainment/breakfree/BreakFreeServiceTest.java`,
`gangland-features/gangland-civilians/src/test/java/org/luckyraven/gangland/civilians/listener/npc/CivilianDeathRewardListenerTest.java`.
Delete: `CNC/seam/KillComboWantedTracker.java` and `KillComboWantedTrackerTest` (port its five cases into HeatWantedTrackerTest
as heat-off cases). Modify (OWN): `CNC/config/HeatModuleConfig.java` (fill the shell), `CNC/config/CopsNCrooksModuleConfig.java`
(`installCoreSeams` :360-374 installs `HeatWantedTracker`; `breakFreeService` bean :270 passes CrimeService),
`CNC/detainment/breakfree/BreakFreeService.java`, `CIV/listener/npc/CivilianDeathRewardListener.java`.

Behaviour: CONTRACTS C12 exactly (constructor, record algorithm, multipliers, trigger loop, level sync, tracker routing, the
listener, the bean and the attacked hook). BreakFreeService: CrimeService as the last constructor field; on a successful break
(:60-62, before `releasePipeline.release`) `crimes.commit(player, Crimes.RESISTING_ARREST, player.getLocation())`.
CivilianDeathRewardListener: inject `CrimeService`; replace :47-49 with
`if (!(hostile && COMBAT)) crimes.commit(killer, Crimes.KILL_CIVILIAN, killer.getLocation());` (XP block unchanged; it no
longer touches Wanted). Use `copManager.groupOf` (Task 5) for copSight, `HeatLedger.contestedTurfAt(turfs, loc)` (C12:
`TurfManager.findAt` + `getRuntimeState(id).getState() == CONTESTING`, null-safe) for the ledger's contested-turf predicate and
for the tracker's `defendingOwnTurf` predicate, which `installCoreSeams` builds from `container.getInstance(TurfManager.class)`
and `container.getInstance(GangMembership.class)` exactly as C12 writes it (turf-defender kills mint nothing, owner ruling).

Tests (red first; User mock with a real `Wanted(null, 1, 5)`, trigger fake `p -> wanted.setLevel(wanted.getLevel() + 1)`;
HeatWantedTracker reads the static `Settings.isWantedKillComboEnabled()` - prime the `wantedKillComboEnabled` field by
reflection as CopRadioTest primes `moneySymbol`, and reset it in `@AfterEach`):
- `HeatLedgerTest`: `assaultCop_once_givesTheFirstStar_andIsTheLastCrime` (Done-when line); `killCopTwiceInTheStreakWindow_reachesTwoStars`
  (150 + 225); `seenByCop_multipliesByOneAndAHalf`; `copSight_aloneAlsoMultiplies`; `turfWar_halvesKillPlayerOnlyInsideAContestedTurf`;
  `jailbreak_crossesThreeThresholds_triggersThreeTimes`; `unknownCrime_andHeatDisabled_addNothing`; `levelDrop_floorsTheHeat`;
  `adminRaise_liftsHeatToTheFloor`; `restoredChase_startsAtTheFloorOfItsLevel`; `chaseCrimes_keepOrder_andClearEmptiesThem`;
  `reportAssault_sameVictimInsideTheWindow_commitsOnce_thenAgainAfterIt`.
- `HeatWantedTrackerTest`: the five ported cases; `heatOffComboDisabled_triggersAStarAtOnce`; `heatOn_copKill_commitsKillCop`;
  `heatOn_playerKill_commitsKillPlayer`; `heatOn_civilianKill_commitsNothing`; `onWantedTrigger_reachesTheLedgerAndTheCombo`;
  `onVictimDeath_stillReachesKillCombo`; `defenderKillInsideOwnContestedTurf_commitsNothing_heatOnAndOff` (TF-49: no commit, no
  combo, no trigger); `attackerKillInsideAContestedTurf_stillCommitsKillPlayer`; `heatOff_civilianKill_reachesTheComboPathOnce`
  (with CivilianDeathRewardListener no longer incrementing, heat-off counts a civilian kill once, not twice).
- `HeatModuleConfigTest` (real Keystone `DependencyContainer` with `registerInstance`, CopManager and HeatLedger as Mockito
  inline mocks): `postConstruct_registersAnAttackedHookThatReportsTheAssault` (capture the hook passed to
  `addCopAttackedHook`, invoke it with a cop whose entity has a UUID -> `ledger.reportAssault(player, thatUuid, player loc)`);
  `attackedHook_copWithoutEntity_reportsNothing`. Together with T5's `copAttackedHook_receivesTheCopAndTheAttacker` and
  `assaultCop_once_givesTheFirstStar_andIsTheLastCrime` this proves "hit a cop once and the heat ledger gives you a star".
- `BreakFreeServiceTest` (contract mocks, `getBreakFreeTapsRequired()` 1, mock Player): `successfulBreak_commitsResistingArrest_beforeTheRelease`
  (InOrder: `crimes.commit(player, RESISTING_ARREST, loc)` then `releasePipeline.release`); `unfinishedBreak_commitsNothing`.
- `CivilianDeathRewardListenerTest`: `civilianKill_commitsKillCivilian_andDoesNotIncrementWanted` (red: increments today);
  `hostileCivilianInCombat_commitsNothing`; `xpIsStillAwarded`.
Commands: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=HeatLedgerTest,HeatWantedTrackerTest,HeatModuleConfigTest,BreakFreeServiceTest -Dsurefire.failIfNoSpecifiedTests=false`;
`mvn -q -pl gangland-features/gangland-civilians -am test -Dtest=CivilianDeathRewardListenerTest -Dsurefire.failIfNoSpecifiedTests=false`; both full suites.

Docket: new bug to report (wanted-bounty-combat), fixed here: "a civilian kill counts twice toward wanted
(EntityDamageListener.handleMobKills :157-163 + CivilianDeathRewardListener :48)". Fixed: TF-49 (defending your own contested
turf mints no star, both heat paths; covered by `defenderKillInsideOwnContestedTurf_commitsNothing_heatOnAndOff`; note in the
report that Gang & Turf P6.4 is now done). Touched, left open: WB-07/WB-30/WB-31 (KillCombo unchanged on the heat-off path).

Merge: lands together with Task 6 or not at all (PLAN header merge rule).

Done when: with default config a cop hit gives one star through `handleWanted` (crime cause) and the attacked hook is proven
registered; heat-off keeps the old star math (combo or one star per kill) except that a civilian kill counts once and a
defender kill not at all; tests red-then-green, both suites green.

### Task 8: Line-of-sight evasion and the search zone
Lane `evasion` | Wave 2 | Depends on: Tasks 1, 3, 4, 5 | Model: sonnet

Files - create: `CNC/wanted/evasion/EvasionClock.java`, `CNC/wanted/evasion/EvasionSnapshot.java`,
`CNC/listener/wanted/EvasionListener.java`, test `.../copsncrooks/wanted/evasion/EvasionClockTest.java`.
Modify (OWN): `CNC/config/EvasionModuleConfig.java` (fill the shell).

Behaviour: CONTRACTS C13 exactly (policy rule, per-tick order, SEEN/SEARCHING/EVADED/OFF transitions, dt clamp, inside/outside
speed, drop via `WantedStars.drop(user, n, WantedCause.EVASION)`, event timing, bean + installs, listener). Values from
`ChaseConfigLoader.get().evasion()` on every tick. Use `XSeries` nowhere here; distances only within the same world (different
world = outside).

Tests (red first; CopGroup mock -> `getSquad()` a mocked `NpcSquad` (final, Mockito inline) with stubbed
`millisSinceSighting()`/`lastKnownLocation()`, `getCops()` -> valid PURSUING CopNpc mocks, `tippedOffWithin` false; User mock with a
real Wanted; WantedStars mock (verify `drop`); `long[] clock`; events collected from the `Consumer<Event>`):
`seen_holds_andFiresSeenOnce`; `unseenPastLostSight_searches_withTheLevelsCountdownAndRadius` (2 stars: 20 s, 60 blocks, centre =
lastKnown); `twentySecondsUnseenInsideTheZone_dropsOneStarWithEvasionCause_andFiresEvaded` (Done-when line);
`outsideTheZone_countsTwiceAsFast` (10 s); `spottedAgain_resetsTheCountdown_andTurnsSeen`; `allStarsMode_dropsEveryStar`;
`restrained_holds`; `tipOff_holds`; `disabled_orOnlyReturningCops_doesNotHandleDecay_andTurnsOff`;
`emptyOrNullGroup_whileSearching_turnsOff` (the hook now also runs on CopManager's empty-group branch, C11: a SEARCHING player
whose cops are all gone gets OFF once and `handlesDecay` false); `lastStarDrop_firesNoEvadedAfterTheChaseEnds` (1 star, drop to
0: the WantedEndEvent's `clear` fires OFF, and the clock fires nothing after it);
`afterADrop_theSearchContinuesAtTheNewLevel` (1 star: 10 s, 40 blocks, same centre); `countdownEvent_onlyWhenTheSecondChanges`.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=EvasionClockTest -Dsurefire.failIfNoSpecifiedTests=false`, then the full suite.

Docket: none (the H11/H12 decay-freeze decline is superseded by the owner ruling: evasion replaces the timer).

Done when: with cops hunting, the safety-net timer never drops a star; with Evasion.Enable false it does exactly as before;
tests red-then-green; suite green.

### Task 9: Star-drop charge - off by default, priced by formula
Lane `charge` | Wave 2 | Depends on: Tasks 1, 2 | Model: sonnet

Files - modify (OWN): `CORE/wanted/WantedStars.java` (the price computation inside `drop` only), `CORE/wanted/WantedSettings.java`
(add `isTakeMoneyEnabled()` and `getTakeMoneyFormula()` defaults), `IMPL/file/configuration/wanted/GanglandWantedSettings.java`
(two overrides), tests `WantedStarsTest.java`, `WantedExecutorTest.java`; create
`gangland-impl/src/test/java/org/luckyraven/gangland/file/configuration/wanted/GanglandWantedSettingsTest.java`.

Behaviour: CONTRACTS C3 "T9" rule exactly. Off unless `Take_Money.Enable` is true; per star `l` (the level before that star
falls) `MoneyFormula.evaluate(formula, vars, amount * multiplier^l)` with vars = user variables + amount, multiplier, wanted=l;
sum, withdraw once (clamped), money line only when the withdrawn amount is non-zero. The star always drops. Nothing else in
`drop` changes.

Tests (red first against Task 1's rule; never read MoneyFormula's package-private seams - the warn-once rule is proven in T2's
`MoneyFormulaTest`; prove routing here by price): `chargeOff_byDefault_movesNoMoney` (Amount 50 still set); `chargeOn_defaultFormula_chargesTodaysPrice`
(2 stars -> 1,250; 5 stars -> 156,250); `chargeOn_customFormulaUsesAmountMultiplierAndWanted` ("amount * multiplier + wanted",
one star from 2 -> 252, not the 1,250 fallback: proves the formula ran with all three variables);
`chargeOn_zeroAmount_chargesNothing_andSendsNoMoneyLine`;
`chargeOn_brokenFormula_chargesTheFallback_andTheStarStillDrops` ("amount * * wanted", two drops, each charged 50 x 5^l);
`chargeOn_negativeFormula_fallsBack`; `chargeOn_balanceVariable` ("balance * 0.02 * wanted", User context);
`allStarsDrop_sumsThePricePerStar`; `WantedExecutorTest.tickWithChargeOff_neverWithdraws`;
`GanglandWantedSettingsTest.delegatesToSettings` (Enable absent -> false, Formula absent -> default; SettingsFixture).
Flip, in the same change: Task 1's `WantedStarsTest.drop_chargesAmountTimesMultiplierPowLevelPerStar` pins the always-on charge
and goes red once the charge is off by default; delete it as superseded by `chargeOn_defaultFormula_chargesTodaysPrice` and say
so in the report.
Commands: `mvn -q -pl gangland-core -am test -Dtest=WantedStarsTest,WantedExecutorTest -Dsurefire.failIfNoSpecifiedTests=false`;
`mvn -q -pl gangland-impl -am test -Dtest=GanglandWantedSettingsTest -Dsurefire.failIfNoSpecifiedTests=false`; both full suites.

Docket: none existing (implements the spec toggle; the ordering bug was Task 1's).

Done when: Done-when lines "a star dropping moves no money, including on an upgraded server whose settings.yml has no Enable
key" and "Enable true charges 50 x 5^stars; a broken formula warns once (MoneyFormulaTest), charges the fallback, the star still
drops" are unit-proven.

### Task 10: Acceptance harness and 0.15 scenarios (authoring)
Lane `acceptance-prep` | Wave 2 | Depends on: none (authoring only) | Model: sonnet

Where: no lane worktree. Every file of this task is an UNTRACKED file in the main checkout
`E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]` (MAIN below) or under `E:/Programming/java/wt/_programme/`; write them
at these absolute paths and commit nothing (the worktrees do not contain `brainstorming/`).
Files - create (OWN): `MAIN/brainstorming/cnc-overhaul-2026-10-05/acceptance/README.md`, `MAIN/brainstorming/cnc-overhaul-2026-10-05/acceptance/scenarios/*.json`,
`MAIN/brainstorming/cnc-overhaul-2026-10-05/acceptance/prep-cnc015.sh`, generator scripts if needed (`.../acceptance/gen-*.js`); copy the mineflayer harness from
`C:\Users\Hashim\AppData\Local\Temp\claude\E--Programming-java-Keystone\testserver-work\harness\` to
`E:/Programming/java/wt/_programme/harness/` (skip if already there; record source, date and file count in the README).
Modify (OWN): `MAIN/brainstorming/bartizan-split-2026-09-08/smoke/scenarios.json` (repoint `paths` to the integration worktree `E:\Programming\java\wt\gangland-0.15.0`,
Keystone 1.14.0, the Bartizan jar from its master (0.6.1, api-compatible with the 0.6.0 pin); add row `cnc-015-boot`:
all nine modules load, no ERROR, `Done (`).

Behaviour: one scenario JSON per row below, in the harness step format (`console`, `wait`, `join`, `chat`, `expectChat`,
`expectLog`, `quit`, ...); reuse `scen-h11-losbreak.json`, the `h13reg-f-*` lanes and `scen-h13brm-*` where they fit. The prep
script clones a server (`harness/clone.sh`), stages core + `target/modules/*.jar` + Keystone + Bartizan + Citizens config, and
writes the per-row settings.yml overrides. Rows and pass lines:
- R1 `reg-ladder-1-3-5`: `/glw wanted add 1|3|5` -> squad sizes from Cops.Count and the Squad_Composition roles, no ERROR.
- R2 `reg-los-break`: the H11 line-of-sight break still loses the cops' contact.
- R3 `reg-cuffed`: cuffed at 1-2 stars, stars kept while cuffed, cleared at intake, no star drops while cuffed.
- R4 `reg-logout`: 2 stars, quit, rejoin -> still 2 stars and the decay clock runs (override `Repeating_Timer.Time: 10`, evasion off).
- N1 `evasion-drop`: 2 stars, break line of sight behind a building, wait <= 3 + 20 + 5 s -> the decreased chat line, 1 star, no death.
- N2 `no-money-on-drop`: default config and a 0.13.0 settings.yml (no Enable key) -> balance unchanged after a drop.
- N3 `charge-switched-on`: Enable true -> a drop at 2 stars takes 1,250; broken Formula -> exactly one "Money formula" warning over two drops, fallback charged, stars still fall.
- N4 `bounty-upgrade`: on a 0.13.0 server post a 500 bounty, stop, deploy 0.15.0, kill the target -> 500 collected once; a second kill collects no posted money.
- N5 `shots-fired`, N6 `assault-cop-star` (one hit -> 1 star), N7 `charge-sheet` ($300 at 2 stars -> sheet 700, paid 300, +40s),
  N8 `regroup` (two cop kills inside 20 s -> Regroup then Regroup_Push radio lines): best effort.
- M manual checklist (the bot cannot see them): boss bar red/flashing yellow with countdown/green, title + siren, zone ring, compass needle.
Pass criterion for 0.15 (spec): R1, R2, R3, R4, N1, N2, N3, N4 PASS.

Tests: every JSON parses (`node -e "JSON.parse(require('fs').readFileSync(f))"` per file); `python smoke.py --list` (run in
`MAIN/brainstorming/bartizan-split-2026-09-08/smoke/`) shows `cnc-015-boot`; `python smoke.py --dry-run --rows cnc-015-boot`
exits 0. No Maven. The README names every path absolutely, so Task 18 can follow it from any directory.

Docket: none.

Done when: the README tells T18 exactly how to run every row unattended, the harness copy exists outside the temp dir, and the
JSON/dry-run checks pass.

### Task 11: Wanted HUD - star cards, title flash, siren, boss bar, zone ring and compass
Lane `hud` | Wave 3 | Depends on: Tasks 1, 3, 4, 7, 8 | Model: sonnet

Files - create (OWN): `CNC/wanted/hud/StarCard.java`, `CNC/wanted/hud/WantedHud.java`, `CNC/listener/wanted/WantedHudListener.java`,
tests `.../copsncrooks/wanted/hud/StarCardTest.java`, `.../copsncrooks/wanted/hud/WantedHudTest.java`,
`.../copsncrooks/listener/wanted/WantedHudListenerTest.java`.

Behaviour (reads only the surfaces in CONTRACTS C14):
- `WantedHudListener` (`@ListenerHandler`), ctor `(JavaPlugin plugin, ChaseConfigLoader chase, WantedMessages messages,
  HeatLedger ledger, CopSpawnManager spawns, CopLoader copLoader, WantedStars stars)`: calls
  `stars.suppressStarChat(() -> chase.get().hud().starCard())`, builds one `WantedHud`, schedules
  `Bukkit.getScheduler().runTaskTimer(plugin, hud::tick, 10L, 10L)`. Handlers (all MONITOR): `WantedStartEvent` -> `hud.show`;
  `WantedLevelChangeEvent` (ignoreCancelled) -> update stars from `getNewLevel()`; on a raise: title
  (`ChatUtil.sendTitle(player, TITLE, card, 5, 40, 10)` when Title on; card = CARD_RAISE when Star_Card on, else "") and siren
  (`new SoundEffect(SoundType.VANILLA, sound, volume, pitch).playSound(player)` when Siren on); when Title is off but Star_Card
  is on, the card goes to chat; on a drop to > 0: the drop card the same way; on a drop to 0 with cause EVASION or DECAY (the
  getaway): the drop card the same way, then the bar hides with the chase; `WantedEvasionStateEvent` -> `hud.state(..)`;
  `WantedEndEvent` and `PlayerQuitEvent` -> `hud.hide(player)`.
- `WantedHud`: per player one `BossBar` (`Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID)`, `addPlayer`) only when
  Boss_Bar on; SEEN -> RED + BAR_SEEN, progress 1.0; SEARCHING -> YELLOW on even ticks / WHITE on odd ticks + BAR_SEARCHING
  (`%time%` = `WantedMessages.duration(secondsLeft)`), progress = secondsLeft / secondsToDropFor(level) clamped 0..1; EVADED ->
  GREEN + BAR_EVADED for 3 s, then the current state; OFF hides the ring and restores the compass; `state(..)` for a player
  without a shown bar does nothing (a late event never re-creates a hidden bar). Every second tick while
  SEARCHING and Zone_Ring on: `Points` evenly spaced points on the circle (centre, radius) at the player's Y + 1, sent with
  `player.spawnParticle(..)` to that player only; particle resolved like gangland-turf `TurfVisualization.resolveParticle` (:94-106)
  (`XParticle.valueOf(name.toUpperCase()).get()`, an unknown name or a null result -> `XParticle.DUST.get()`; `XParticle.of(String)`
  returns an Optional), data `new Particle.DustOptions(Color.RED, 1.5F)` when the Bukkit
  `particle.getDataType() == Particle.DustOptions.class`, else no data. While SEARCHING and Compass on:
  remember `getCompassTarget()` once, then `setCompassTarget(StarCard.exitPoint(centre, radius, player.getLocation()))`;
  restore on OFF/hide. Never touch the action bar. `// ponytail: fixed point count, the client culls far points`.
- `StarCard` (pure, static): `raiseCard(WantedMessages m, String crimeName, String tierName, boolean skipCuffing)`,
  `dropCard(WantedMessages m, WantedCause cause)`, `barTitle(WantedMessages m, EvasionState state, String stars, int secondsLeft)`,
  `barColor(EvasionState state, boolean flashOn)`, `exitPoint(Location centre, double radius, Location from)` (centre + the
  horizontal unit vector towards `from` x radius, y = from.y; from == centre -> +X).

Tests (red first): `StarCardTest`: `raiseCard_namesCrimeTierAndCuffs` (tier name taken from the bundled cops.yml tier config
via `CopRadio.tierName`, singular as shipped: "Assault on an officer: Sergeant inbound, they still want you in cuffs", colour
codes stripped); `raiseCard_skipCuffingTier_saysShootFirst`; `raiseCard_noCrime_usesReportedCrime`; `dropCard_perCause`;
`barTitle_searching_showsTheCountdown`; `barColor_flashesYellowAndWhite`; `exitPoint_isOnTheZoneEdgeTowardsThePlayer`.
`WantedHudTest` (mockStatic `Bukkit.createBossBar` as VehicleSessionTest:48 does; mock Player): `show_createsARedBarForThatPlayer`;
`searching_turnsYellowWithTheCountdown_andFlashes`; `searchingThenSeen_turnsRedAgain` (spec "get spotted and it turns red
again"); `evaded_isGreenForThreeSeconds`; `hide_removesTheBar_andRestoresTheCompass`; `stateAfterHide_createsNoBar`;
`searching_spawnsZoneRingPointsForThatPlayerOnly` (`Points` calls on that player's `spawnParticle`, none on another player);
`zoneRingDisabled_spawnsNothing`; `searching_retargetsTheCompassToTheExitPoint`; `compassDisabled_leavesTheCompassAlone`;
`bossBarDisabled_createsNoBar`.
`WantedHudListenerTest` (BukkitStatics + `BukkitRegistryFixture.install()` in `@BeforeAll` for the siren's XSound; mock Player;
fake ChaseConfigLoader returning a HudSettings per case; the ledger/tier inputs stubbed): `listenerConstructor_suppressesTheStarChatLine`;
`raise_sendsTitleWithTheCardAsSubtitle_andPlaysTheSiren` (verify `player.sendTitle(.., card, 5, 40, 10)` and one `playSound`);
`titleOff_starCardOn_sendsTheCardToChat`; `titleOn_starCardOff_sendsAnEmptySubtitle`; `sirenOff_playsNoSound`;
`dropToOneStar_sendsTheEvasionDropCard`; `lastStarDropByEvasion_sendsTheEscapeCard_thenHides`.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=StarCardTest,WantedHudTest,WantedHudListenerTest -Dsurefire.failIfNoSpecifiedTests=false`, then the full suite.

Docket: none.

Done when: every Hud.*.Enable switch independently removes its piece, each proven by its own "disabled/off" test above
(Boss_Bar, Star_Card, Title, Siren, Zone_Ring, Compass); the title + siren slice is unit-proven; tests red-then-green; suite green.

### Task 12: Fines at the end - the charge sheet with shortfall served as time
Lane `charge-sheet` | Wave 3 | Depends on: Tasks 4, 6, 7 | Model: sonnet

Files - modify (OWN): `CNC/detainment/intake/JailIntakeService.java`, `CNC/detainment/DetainedPlayer.java`,
`CNC/database/DetainmentTable.java`, `CNC/database/DetainmentRepository.java`, `CNC/detainment/paperwork/PaperworkView.java`,
`CNC/config/CopsNCrooksModuleConfig.java` (`jailIntakeService` bean :228-243 and `paperworkView` bean :285 only), tests
`DetainmentRepositoryMigrationTest.java`, `DetainmentRepositorySpiTest.java`; create
`.../copsncrooks/detainment/intake/JailIntakeServiceTest.java`, `.../copsncrooks/detainment/paperwork/PaperworkViewTest.java`.

Behaviour: CONTRACTS C15 exactly. JailIntakeService gains (appended to its `@RequiredArgsConstructor` fields)
`DetainmentEconomyContract economy`, `ChaseConfigLoader chase`, `HeatLedger ledger`, `WantedMessages messages`; the bean passes them.
`%money_symbol%` = `Settings.getMoneySymbol()`, amounts via `Settings.formatAmount(BigDecimal.valueOf(x))`, `%time%` via
`WantedMessages.duration`. PaperworkView: a package-private static `@Nullable String fineLine(WantedMessages m, DetainedPlayer d)`
(test seam; null when `d.getFinePaid()` is null, else PAPERWORK_FINE with `%paid%`/`%time%`), appended to the info lore (:106)
when non-null. DetainmentRepository's legacy SQLite rebuild: edit BOTH the `_migration` DDL string and the `columnsToCopy`
varargs (C15).
Bail, bribes and the hospital bill are untouched; the bank is never touched.

Tests (red first; contract fakes, mocked online Player, real DetainmentRegistry where cheap):
`busted300Against700_pays300_andServes40ExtraSeconds` (Done-when: 2 stars, wallet 300 -> fine 700, paid 300, extra
ceil(400 x 0.1) = 40, sentence 180 + 120 + 40 = 340 s, the row holds 300/40, chat shows paid and extra);
`richPlayer_paysTheWholeFine_noExtraTime`; `brokePlayer_paysNothing_servesTheWholeFineAsTime` (5 stars -> fine 1,450 -> +145 s);
`extraTime_isCappedAtMaxExtraSeconds` (Seconds_Per_Unpaid 1.0 -> 1,450 s capped to 600);
`deathCommit_chargesNoSheet`; `sheetDisabled_chargesNothing_sentenceUnchanged`; `crimesAreListedGroupedInFirstSeenOrder`;
`chaseIsReadBeforeTheWantedClear`; `arrestClearsWithTheArrestCause`. Repository: `roundTrip_keepsTheFineColumns`,
`legacyRows_readNullFines`, `sqliteRebuild_keepsTheFineColumns` (start from a legacy DB with the UNIQUE jail_id index AND the two
fine columns already holding values, run the migration, assert the values survive; TESTING sec 5 rules).
`PaperworkViewTest` (WantedMessages on its fallbacks, `Settings` money symbol primed by reflection like CopRadioTest):
`fineLine_listsPaidAndExtraTime` (paid 300, extra 40 -> the line holds "Fine paid: ", the money symbol + the amount as
`Settings.formatAmount` prints 300, and "Extra time: 40s", colours stripped; prime whatever Settings statics formatAmount reads);
`fineLine_legacyRow_isNull`.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=JailIntakeServiceTest,PaperworkViewTest,DetainmentRepositoryMigrationTest,DetainmentRepositorySpiTest -Dsurefire.failIfNoSpecifiedTests=false`, then the full suite.

Docket: none fixed; CJ-19 (detainPlayer twice per intake, :56-57) touched and left open unless removing the duplicate is one
line with a test - say which in the report.

Done when: the Done-when $300/$700 line, including "the paperwork lists both", is unit-proven; existing detainment rows still
load; tests red-then-green; suite green.

### Task 13: Paid bounties and kill exemptions - pay only what players posted, persist the ledger, one-time upgrade, self-defence
Lane `bounty` | Wave 3 | Depends on: Tasks 2, 6 | Model: sonnet

Files - modify (OWN): `CORE/bounty/Bounty.java`, `CORE/bounty/BountyExecutor.java`, `IMPL/listener/player/EntityDamageListener.java`
(onPlayerEntityDeath hit recording before the death check, handlePlayerKills :100 with the claim branch :122-148, handleWanted
bounty block :221-238, handleBounty :248-279, constructor + `GangMembership gangs` last),
`IMPL/database/tables/player/UserTable.java`, `IMPL/data/user/UserDataLoader.java` (bounty read :96/:150 and bounty timer
:155-162), `IMPL/database/repositories/player/UserRepository.java` (:44-66), `IMPL/listener/player/RemoveAccountListener.java` (:96),
tests `BountyTest.java`, `BountySetCommandTest.java` (constructor only),
`gangland-impl/src/test/java/org/luckyraven/gangland/listener/player/EntityDamageListenerTest.java` (Task 6 created it: change its
`listener(..)` factory for the new constructor parameter, add the cases below),
`gangland-impl/src/test/java/org/luckyraven/gangland/data/user/UserDataLoaderTest.java` (Task 6 created it: add the bounty cases);
create `gangland-core/src/test/java/org/luckyraven/gangland/core/bounty/BountyExecutorTest.java`,
`gangland-impl/src/test/java/org/luckyraven/gangland/database/repositories/player/UserBountyLedgerIntegrationTest.java`.

Behaviour: CONTRACTS C16 exactly. Also: `handleWanted`'s auto bounty uses `addNotoriety(autoBounty)` and starts the
BountyExecutor only while `getNotoriety() < Repeating_Timer.Maximum`, sync (`start(false)`, `new UserBountyEvent(false, ..)`);
`handleBounty` (WB-14): `scaled = calculateLevelScaledBounty(Kill.Each, level)`; skip when `notoriety + scaled > Kill.Maximum`;
fire `new UserBountyEvent(false, user, scaled)` synchronously, return when cancelled; `addNotoriety(scaled)`; then start the
executor (sync) when the timer is on and notoriety < Maximum. UserDataLoader starts the bounty timer from a main-thread task.
`// ponytail: 4096-char ledger, about 70 posters; a bounty_post table when that is not enough` on the column.
Self-defence: C16 "Self-defence" exactly (first-hit maps, 30 s fight window, the `clock` test seam, the check placed after the
claim branch and before `handleBounty` + the crime path, so a self-defence kill raises no star and no notoriety on either
path, with or without cops-n-crooks). Keep the diff inside the methods named (Gang & Turf rebases onto this file).

Tests (red first): `BountyTest`: flip the WB-13 pin (:160) to "notoriety moves the total, never the posted ledger";
`ledger_isKeyedByPosterId_aRelogStillFindsThePoster` (two Player mocks, same UUID); give the :217 contributor/stranger mocks
distinct names; `postedAmount_sumsThePaidFigures`; `notoriety_isTheServerMadeRemainder`; `claimPosted_paysPosted_keepsNotoriety`;
`serializeRestore_roundTrips`; `restoreNull_legacyBountyCountsAsPostedOnce`; `restoreEmpty_isEmpty`; `badLedgerEntries_areSkipped`;
the WB-03 pin stays. `BountyExecutorTest`: `growsNotorietyOnly_postedUntouched` (red: doubles all); `capClampsTheLastDoubling`
(red, WB-16: 15,000 -> 20,000 not 30,000); `postedOnlyBounty_stopsTheTimer`. `EntityDamageListenerTest` (add):
`claim_paysPostedOnly_notorietyStays` (Done-when); `postedTakedown_isNotACrime`; `notorietyOnlyTarget_paysNothing_andTheKillIsACrime`;
`payNotorietyTrue_paysEverythingOnce_andResets`; `gangmatesOrAllies_collectNothing` (WB-43); `legacyBounty_paysInFullOnce` (Done-when);
`victimStruckFirst_killIsSelfDefence_noStarNoNotoriety` (red: today the kill raises a star); `killerStruckFirst_killIsACrime`;
`fightOlderThanThirtySeconds_isForgotten_soTheKillIsACrime` (via the `clock` seam); `selfDefence_alsoHoldsWithoutTheTracker`
(inactive WantedKillTrackers -> handleWanted is not called).
`UserDataLoaderTest` (add): `login_withNotoriety_startsTheBountyTimerOnTheMainThread` (verify `runTaskTimer`, never
`runTaskTimerAsynchronously`; red today: `timer.start(true)` off the async load).
`UserBountyLedgerIntegrationTest` (real SQLite, TempDir NEVER, disconnect then `DbFiles.release`): `legacyNullColumn_readsAsPosted`,
`saveAndReload_keepsPostersAndTheRefund`, `newUser_insertsAnEmptyLedger`. `BountySetCommandTest` stays green.
Commands: `mvn -q -pl gangland-core -am test -Dtest=BountyTest,BountyExecutorTest -Dsurefire.failIfNoSpecifiedTests=false`;
`mvn -q -pl gangland-impl -am test -Dtest=EntityDamageListenerTest,UserDataLoaderTest,UserBountyLedgerIntegrationTest,BountySetCommandTest -Dsurefire.failIfNoSpecifiedTests=false`; both full suites.

Docket: fixed WB-12, WB-13 (pin flipped), WB-14, WB-16, WB-43, WB-06 + US-18 bounty halves (covered by
`login_withNotoriety_startsTheBountyTimerOnTheMainThread` + BountyExecutorTest); left open WB-03, WB-22.
New bugs to report (wanted-bounty-combat), fixed here: "the bounty timer doubles the players' posted escrow too
(BountyExecutor.java:62-71), so a claim pays out minted money"; "killing a player in self-defence raises your wanted level
(no self-defence rule exists; EntityDamageListener.handlePlayerKills :100-148)".

Done when: Done-when lines "you collect only what players posted" and "a bounty saved before the upgrade is still paid in full,
once" are proven, and a self-defence kill is proven not a crime; tests red-then-green; core and impl suites green.

### Task 14: Pull back and regroup
Lane `regroup` | Wave 3 | Depends on: Task 5 | Model: sonnet

Files - modify (OWN): `CNC/npc/police/CopGroup.java` (regroup state), `CNC/npc/police/radio/CopRadio.java` (`listenerFor` :90-107,
`commanderDown` :443, new `sayFromLeaderLater` beside `sayFromLeader` :117), `CNC/npc/police/CopManager.java` (`spawnTick` :462 only),
tests `CopGroupSquadTest.java`, `CopRadioTest.java`, `CopManagerSquadTest.java`.

Behaviour: CONTRACTS C11 regroup part, including the regroup radio block. In `listenerFor`, after `followUps(..)`, for MAN_DOWN/LEADER_DOWN:
`RegroupSettings r` from the provider (null -> DEFAULT); `group.recordCasualty(now())`; `if (group.shouldRegroup(now(), r))`
-> `group.startRegroup(now(), r)` and `sayFromLeaderLater(group, "Regroup", 2, group::isRegrouping)` (NOT a direct `say`: the
casualty line just stamped every listener's last-heard time, so a same-call non-priority line is swallowed by the player gap,
and 0.15 adds no Priority entries, C10). Backup is already requested by every casualty (:104); the
granted extra cop counts in the target. `commanderDown`: `setFallBackUntil(Math.max(getFallBackUntil(), now() + COMMANDER_FALL_BACK_MS))`.
`spawnTick`: after the spawn loop, before `consumeBackupExpiry` - when `group.isRegrouping()`: `arrived` = cops that are valid,
not RETURNING, in the player's world and within `r.arrivalRadius()` of him; `if (arrived >= targetCount || !group.isFallingBack(now))`
-> `group.endRegroup()` and `copRadio.sayFromLeaderLater(group, "Regroup_Push", 1, () -> true)`. Cops leave cover on their next AI tick because
CopRetreat re-reads `isFallingBack` (CopRetreat.java:55).

Tests (red first): `CopGroupSquadTest`: `twoCasualtiesInside20s_whileFighting_shouldRegroup`; `oneCasualty_doesNot`;
`casualtiesOutsideTheWindow_doNot`; `cuffFirstSquad_neverRegroups`; `secondRegroup_waits60s`;
`startRegroup_fallsBack15s_endRegroupClearsIt`. `CopRadioTest` (use the package-private `CopRadio(provider, lines, clock,
later)` constructor and run the captured `later` tasks): `secondManDownInside20s_radiosRegroup_andFallsBack` (the Regroup line
reaches the hunted player after the Man_Down line);
`regroupLine_isDelivered_withAPreUpgradePriorityList` (provider whose radio settings have today's Priority list, i.e. no
Regroup entry -> still delivered right after Man_Down); `commanderDownDuringARegroup_keepsTheLongerFallBack`;
`cuffFirstSquad_radiosNoRegroup`. `CopManagerSquadTest`:
`regroup_endsWhenArrivedCopsReachTheTarget_andRadiosPushOnce`; `regroup_endsAtTheFallBackTimeout`;
`regroup_holdsWhileReinforcementsAreStillFar`.
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=CopGroupSquadTest,CopRadioTest,CopManagerSquadTest -Dsurefire.failIfNoSpecifiedTests=false`, then the full suite.

Docket: none.

Done when: Done-when "kill two cops in quick succession and the rest pull back, radio for backup and push together when it
lands" is unit-proven; Cops.Regroup.Enabled false = today's behaviour; suite green.

### Task 15: Shots give you away
Lane `shots` | Wave 3 | Depends on: Task 5 (behaviour also needs Task 8) | Model: sonnet

Files - create (OWN): `CNC/listener/police/ShotNoiseListener.java`, test
`gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/listener/police/ShotNoiseListenerTest.java`.

Behaviour: CONTRACTS C17 exactly. MONITOR + ignoreCancelled so DetainmentListener's restrained-shooter cancel (:45-50) wins.
Settings from `copLoader.getLoadedProvider().getShotNoiseSettings()` (null provider or null settings -> DEFAULT). The 3 s throttle
is the radio's per-squad `Shots_Fired` cooldown (Task 5); add nothing else.

Tests (red first; real `WeaponShootEvent(mock(Weapon), shooter)` with `getCategory()` stubbed): `gunShot_wantedShooter_copInRange_reportsTheSighting_andRadios`;
`meleeSwing_makesNoNoise`; `copOutsideTheRadius_hearsNothing`; `notWanted_nothing`; `npcShooter_isIgnored`; `noiseDisabled_nothing`;
`unlistedBeamWeapon_nothing`; `onlyTheNearestCopSpeaks`. (Shots_Fired stays a direct `sayAs`: no other line is spoken in the
same call, and the re-centring is the behaviour; a squad-gap drop of the line itself is accepted.)
Command: `mvn -q -pl gangland-features/cops-n-crooks -am test -Dtest=ShotNoiseListenerTest -Dsurefire.failIfNoSpecifiedTests=false`, then the full suite.

Docket: none.

Done when: a gunshot re-centres the squad on the shooter (which the evasion clock reads as a sighting) with a Shots_Fired line;
BartizanBlindScan/ReferenceScan untouched (cops-n-crooks declares Bartizan); suite green.

### Task 16: Documentation, commands.json and help
Lane `docs` | Wave 4 | Depends on: Tasks 1-15 | Model: sonnet

Files - create (OWN): `documentation/migration-0.15.0.md`. Modify (OWN): `documentation/features/wanted-bounty.md`,
`documentation/features/cops-n-crooks.md`, `documentation/features/jail-detainment.md`, `documentation/developer/configuration.md`
(cops.yml Regroup/Shot_Noise rows, the new npc/wanted.yml and wanted_messages.yml tables), `documentation/developer/cops-n-crooks.md`,
`documentation/gangland-api.md` (2.1 table: CrimeService, Crimes, CrimeCommittedEvent, WantedEvasionStateEvent, EvasionState;
core re-exports WantedCause, WantedStars, WantedDecayPolicy, MoneyFormula, event cause getters, Settings getters),
`documentation/module-loader.md` ("Core seams": WantedStars + decay policy, CrimeService; HeatWantedTracker replaced
KillComboWantedTracker), `documentation/tests/features/{wanted-bounty,cops-n-crooks,jail-detainment}.md` (new unchecked items
mirroring the Done-when lines).

Behaviour: migration-0.15.0.md in the 0.12.0 doc's shape: required versions (Keystone 1.14.0, Bartizan 0.6.x, module Host_Api
2.1 for cops-n-crooks/civilians, 2.0 module jars still load); new files copied on first boot (`npc/wanted.yml`,
`npc/wanted_messages.yml`); cops.yml keys that only exist as code defaults until added by hand; settings.yml: Take_Money.Enable
false (upgraded servers stop charging without an edit; keep today's charge with Enable true and Amount 50), Formula,
Pay_Notoriety, Lose_Money:false = no death charge; database columns added automatically (`user.bounty_posters`,
`detainment.fine_paid`, `detainment.fine_extra_seconds`; every old bounty pays in full once); behaviour changes (evasion drives
decay while cops hunt, sync decay clock, heat ledger, civilian kill counted once, posted claims, self-defence kills and kills
defending your own contested turf are not crimes, gangmates collect nothing, notoriety is kept through death and arrest
(say so plainly; it is flagged for the owner), safe death formula, charge sheet). Radio: the 0.15 lines need no `Priority:`
edit (they bypass the radio gaps); the three new `Cooldown_Ticks` keys apply as code defaults. commands.json: confirm no command
was added (`git diff --stat` of `command/` packages); edit nothing unless one was. Document only what shipped; nothing from 0.16+.
Inputs (read-only): the lane reports in `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/reports/T*.md`.

Tests: every relative link resolves (scripted check); every YAML key named in the docs exists in the shipped files (grep).

Docket: none.

Done when: an admin can upgrade from 0.13.0 using migration-0.15.0.md alone.

### Task 17: CHANGELOG in Markdown and BBCode
Lane `changelog` | Wave 4 | Depends on: Tasks 1-15 | Model: sonnet

Files - create (OWN): `documentation/v0.15.0/CHANGELOG.md`, `documentation/v0.15.0/CHANGELOG.bbcode.txt`.

Behaviour: same sections and tone as `documentation/v0.7.5-DEV/CHANGELOG.{md,bbcode.txt}`: headline ("Lose them"), New
(heat ledger, line-of-sight evasion, wanted HUD and compass, charge sheet, shots give you away, pull back and regroup, paid
bounties), Changed (star-drop charge off by default, death bill rules, decay clock, self-defence and own-turf defence kills
are not crimes), Fixed (with docket ids from the wave's reports: WB-04, WB-06, WB-12, WB-13, WB-14, WB-16, WB-20,
WB-21/LS-22 part, WB-28, WB-29, WB-43, CJ-39, US-05, US-18, TF-49 and the new rows), Configuration, API 2.1, Upgrade notes
(link migration-0.15.0.md). BBCode uses `[SIZE=N][B]..[/B][/SIZE]` headings,
never `[HEADING]`, and a unicode bar line (e.g. `━━━━━━━━`) instead of `[HR]`. Source facts from
`git log --oneline master..cnc-lose-them` (run in your lane worktree) and the lane reports, read-only, in
`E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/reports/T*.md`; no 0.16+ items.

Tests: both files list the same items in the same order (scripted diff of stripped item text).

Docket: none.

Done when: both files exist and match.

### Task 18: Acceptance run against the 0.15.0 build and report
Lane `acceptance-run` | Wave 4 | Depends on: Tasks 1-15, 10 | Model: sonnet

Where: no lane worktree. Build in the integration worktree `E:/Programming/java/wt/gangland-0.15.0` (W3 merged); every file
you write is untracked and absolute, never committed.
Files - create (OWN): `E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/acceptance/runs/**`,
`E:/Programming/java/wt/_programme/acceptance/cnc-0.15.md`.

Behaviour: build `mvn -q clean package` in the integration worktree; follow
`E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/cnc-overhaul-2026-10-05/acceptance/README.md` (Task 10) to stage the server and run
rows R1-R4, N1-N8 unattended plus `python smoke.py --rows cnc-015-boot --deploy --restore` (in
`E:/Programming/java/Gangland Warfare [Cubed-GTA recoded]/brainstorming/bartizan-split-2026-09-08/smoke/`); collect server.log, chat.txt,
steps.json per row. Write the report in the shape of `_programme/acceptance/h13-regression.md`: per row PASS/FAIL with the
evidence lines, the manual checklist M marked "needs a human look", and the spec pass line (R1-R4 + N1-N4). Do not fix code:
a failure is reported with its log excerpt and the suspected task for the orchestrator. Do not run graphify (the orchestrator
refreshes the graph after the merge to master).

Tests: the report's pass/fail table matches the runs' exit codes.

Docket: report any new bug a run exposes (title, evidence, suspected file).

Done when: the report exists with a verdict for every row and the spec's pass criterion stated as met or not met.
