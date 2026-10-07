# cnc-ideas: cops-n-crooks NPC programme (H11-H13) mined for gangs and turf wars

Census agent `cnc-ideas`, wave `gang-turf-wave-2026-09-30`, written 2026-09-30. Planning only, no product code.
Repo: Gangland master `ff9d813f` (0.12.0). Keystone master `da4d0d2` (1.13.0). Unmerged H13: Keystone `phase-h13-npc-roles`
(1.14.0, worktree `wt/keystone-1.14.0`, head `6ef88c6`) and Gangland `npc-roles-medics` (0.13.0, head `f9295ec0`, plus
sub-worktrees `gl13-roles`, `gl13-stuck`, `gl13-names`, `gl13-drop`).

Effort scale: S = up to 1 day, M = 2-4 days, L = 5+ days, each including tests and a docket row. Effort assumes the
Keystone/Gangland version the idea rides is already merged; sequencing is in section 5.

## 0. What the programme is, in one page

| Phase | Ships in | What it added (verified) |
|---|---|---|
| H11 | Keystone 1.12.0, Gangland 0.11.0 | `NpcSquad` (public) with shared last-known position and shared route; `AbstractNpc.pursue(target, squad, sightRange)` and `canSee`; reverse route planner (`NpcRoutePlanner`, stairs/doors/ladders, never loads a chunk); the `PURSUING`/`RETURNING` bounce fix. Docs: `Keystone/docs/phase-h11-npc-navigation.md`. |
| H12 | Keystone 1.13.0, Gangland 0.12.0 | Formation arc (`NpcSquad.setFormationArc`), moving while firing (`NpcEngagement`), melee surround and reach band (`NpcMeleeProfile`), check-fire, `AbstractNpc.takeCover`, per-NPC `setFireRateScale`, and the squad signal SPI (`NpcSquadSignal` + `spi.NpcSquadListener`). Gangland side: `SquadRadio` and friends in `gangland-api`, police radio (`CopRadio`), dispatch, responders, backup, retreat, faction shouts (`CivilianService`). Docs: `Keystone/docs/phase-h12-npc-tactics.md`, `Gangland/documentation/migration-0.12.0.md`. |
| H13 (unmerged) | Keystone 1.14.0, Gangland 0.13.0 | Stagnation clock `AbstractNpc.millisUnreachable()`; `EntitySpawner.findClosestSpawnerLocation(Player, Predicate<Location>)`; `NpcFanPlacement {ANY, CENTER, FLANK}`, `setLeaderPriority`, `setRangedBand`. Gangland side: `CopRole` + `Squad_Composition` (gl13-roles), `Cops.Stuck` recycling (gl13-stuck), badge callsigns `CopNames` + new module `gangland-healthbars` (gl13-names), money-drop classifier fix (gl13-drop). Field care (medic heals) is planned in `wt/_programme/h13/plan.md` as `GL-CARE` but has no code yet. Docs: `Keystone/docs/phase-h13-npc-roles.md` (in the 1.14.0 worktree). |

Key structural fact for this wave: the radio, tactics config and retreat settings already live in **`gangland-api`**
(`org.luckyraven.gangland.npc.radio.SquadRadio`, `RadioVoice`, `RadioSettings`, `RadioLines`, `RadioSides`;
`org.luckyraven.gangland.npc.TacticsConfig`, `RetreatSettings`, `NpcFireRate`), not in cops-n-crooks. Turf can use them
today with no new module edge. What is still cop-private: `CopRole`, `CopNames`, `BackupSettings`, `StuckSettings`,
the backup/responder logic in `CopManager`, and `CopRadio`'s dispatch/backup wiring.

## 1. What turf NPCs already inherit (do not re-plan these)

Turf defenders and the Quartermaster are ordinary `CivilianNpc`s of `civilians.yml` types `turf_defender` and
`quartermaster` (spawned via `CivilianSpawnManager.spawnCivilian` in
`gangland-features/gangland-turf/.../npc/defender/TurfDefenderDeployer.java` and `.../npc/TurfPowerupNpc.java`). They
therefore already get, with no turf-side code:

| Inherited from | Evidence |
|---|---|
| H11 `pursue`, route planning up stairs/ladders, hopeless-target handling | `CivilianCombatBehavior` calls `npc.pursue(target, squad, ai.alertRange())` |
| H12 formation arc, strafing, check-fire, melee surround | `civilians.yml` `turf_defender.AI.Combat.Tactics` (arc 100, strafe 8) and `quartermaster` (arc 120, strafe 10) |
| H12 retreat to cover | `civilians.yml` `Combat.Retreat` block on both types; `CivilianCombatBehavior` lines 66-80 |
| H12 shouts and recruitment | `CivilianService.squadListener`, `recruit`, `drainPendingRecruits`; `civilian_messages.yml` `Shouts:` |
| H12 `Fire_Rate_Multiplier` cadence | `civilians.yml` both types `0.05` |
| H13 health bars, once `gangland-healthbars` ships | `CivilianNpcFactory` line 92-93: `setProtected(false)` and `SHOULD_SAVE=false`, so `NpcHealthBar.isTransient` accepts them |

What they do **not** get, and why (this is the gap the catalogue fills):

- Squad identity is per **(faction id, target)**, not per turf: `CivilianService.squadFor` keys `SquadKey(faction, target uuid)`
  and both defenders and Quartermaster faction ids are per **type** (`turf_defender`, `quartermaster`). Defenders of
  two different turfs hunting the same attacker share one squad and can recruit across turfs.
- Defenders all spawn on one point (`GarrisonDeployListener.regionCentreSurface`) and stay there when idle; nothing
  pulls them back after a chase (`TurfDefenderDeployer.retarget` picks the nearest challenger within
  `targetingRadius` from the defender's own position, with no turf-bounds check). Cops have `RETURNING` and
  `Cops.Pursuit.Max_Distance`; `CivilianState` has only `IDLE, WANDERING, FLEEING, COMBAT`.
- The whole garrison is consumed and spawned at capture start (`GarrisonDeployListener.onCaptureStart`:
  `garrisons.consume(turfId, stock)`); already in the docket census (`docket-gang-turf.md` around line 398).
- The shout format reads `[%faction%]`, so an attacker sees `[turf_defender] Turf Defender: ...`
  (`civilian_messages.yml` `Shouts.Format`; `CivilianService.FactionVoice.extras` supplies only `faction`).
- No callsigns, no roles, no reinforcement, no responders, no stuck recycling, no money on death
  (`civilians.yml` `turf_defender.Drops.Items: []`, `Experience: 0`).
- Only the listener side of chat reaches gangs by **range**: a squad line is heard by players within `Shouts.Range` (24)
  of the speaker plus the hunted player (`SquadRadio.speak`). An owner-gang member on the other side of the map
  hears nothing when their turf is attacked. Existing gang notification is `TurfCaptureNotifier` (milestone + capture
  broadcast) and the boss bar, none NPC-voiced.
- H12 acceptance could **not** verify the faction-alert scenario in the sandbox (`Keystone/docs/phase-h11-npc-navigation.md`
  table: "Unit tests only"; `Gangland/documentation/migration-0.12.0.md` section 9 lists "the civilian and faction
  scenarios (GL-4)" as not covered). Every idea below that leans on faction squads should start with a sandbox
  acceptance of the existing behaviour.

## 2. Ranked catalogue (by player-visible value)

| # | Idea | Source | Player-visible payoff | Rides | Lands in | Effort |
|---|---|---|---|---|---|---|
| T1 | Turf alert radio to the owning gang, anywhere | H12 `SquadRadio`, `CopRadio.dispatch` | Owners are told their turf is hit, how bad, and from which direction | `SquadRadio.say`, `RadioVoice` (+ additive audience seam) | api + turf | M |
| T2 | Garrison as a per-turf `NpcSquad` with posts and a leash | H11/H12 squads, cop `RETURNING` | Defenders act as a team, hold the turf, stop chasing across the map | `NpcSquad`, `FactionSquads` seam (+ small civilians hit-path change) | civilians + turf | M |
| T3 | Reinforcement waves and alert tiers | H12 `CopGroup.requestBackup`, `Backup.*`, Escalate | Fights escalate and have a shape; garrison stock is a reserve, not a one-shot | cop backup pattern, ring spawn | turf + api | M |
| T4 | Roles: shield Defender, Marksman, Medic, Commander (= Quartermaster) | H13 `CopRole`, `Squad_Composition` | Recognisable, counterable garrison composition | `NpcFanPlacement`, `setLeaderPriority`, `setRangedBand` (Keystone 1.14.0) | api + civilians + turf | L |
| T5 | Turf heat feeds wanted level; cops join gang fights | H12 dispatch, wanted tiers | Big fights draw the police; risk/reward for war | `CopManager.onWantedStart`, `CopRadio.dispatch` | cops-n-crooks (listens to turf events) | M |
| T6 | Gang-tagged callsigns and radio identity | H13 `CopNames`, `CopRadio.callsign` | Attackers see whose man it is: `[Vipers] Defender Bob #1042` | hologram line, `RadioVoice.callsign` | api + civilians + turf | S-M |
| T7 | Health bars on defenders and Quartermaster | H13 `gangland-healthbars` | See how close a defender is to dropping | Citizens `HologramTrait` | no code (verify) | XS |
| T8 | Neighbour and allied responders | H12 `CopManager.responders`, `Radio.Responder_Max` | Holding contiguous land pays off; allies' garrisons trickle in | radio `CONTACT`/`MAN_DOWN` calls | turf | M |
| T9 | Defender bounty drop | H13 `MoneyDropClassifier` module source, `CopsMoneyDropSource` | Killing defenders pays; owners' garrison spend returns to attackers | money-drop seam | turf | S-M |
| T10 | Commander-down morale | H13 `Commander_Down`, `CopRetreat.takeCover` with duration | Killing the Quartermaster breaks the garrison for a moment | `LEADER_DOWN`, `takeCover` | turf | S |
| T11 | Gang comms: man-down and ping callouts for players | H12 `SquadRadio.say`, `RadioSides.compass8` | Gang-mates hear "Ryan is down at Riverside, 40 m north" | `SquadRadio` with a member-less `NpcSquad` as throttle key | gang or turf | M |
| T12 | Stuck recycling for defenders | H13 `Cops.Stuck`, `millisUnreachable()` | No defender frozen on a roof or in a fence | `AbstractNpc.millisUnreachable`, filtered spawner search | turf | S-M |
| T13 | De-escalation: stand-down and walk-home instead of pop-out | H12 `Stand_Down`, `releaseSurplus` | Fights end with a line, not with NPCs vanishing | `takeCover`/`navigateTo`, radio `say` | turf | S |

Details follow. Every entry gives: source, what it does today, how it maps to gangs/turf, primitive, effort, and risk.

### T1. Turf alert radio to the owning gang (highest value)

- **Source:** `gangland-api/.../npc/radio/SquadRadio.java` (`listener`, `say`, `speak`), `RadioSettings`, `RadioLines`;
  cop wiring in `cops-n-crooks/.../npc/police/radio/CopRadio.java` (`dispatch`, `sayFromLeader`, `respond`,
  `Stand_Down`, `stoodDown` set in `CopManager.onWantedEnd`).
- **Today:** a line is delivered to every player in the speaker's world within `Range` of the speaker or the
  addressee, plus the hunted player within `Target_Range`. Throttles are per squad (gap, per-key cooldown) and per
  player (gap). `SquadRadio` keys its throttle state on an `NpcSquad` object only (`WeakHashMap<NpcSquad, SquadState>`),
  so it does not need real members.
- **For turf:** define a `TurfRadio` (NEW, in turf) that wraps a `SquadRadio` with a `RadioVoice` whose `extras()` supplies
  `%turf%`, `%gang%`, `%attackers%`, `%defenders%`, `%progress%` and whose `hunted()` is the challenger gang's nearest
  member. Lines, all owner-audience: `Contest_Start` ("Riverside is under attack! 3 of them, coming from the north-east"),
  `Milestone_50`, `Defender_Down` ("Two defenders left at Riverside!"), `Last_Stand`, `Turf_Lost`, `Turf_Held`.
  Trigger from the events the capture loop already fires: `TurfCaptureStartEvent`, `TurfCaptureProgressEvent`
  (milestones), `TurfCapturedEvent`, `TurfCaptureFailedEvent` (`capture/CaptureService.java` lines 246-417) and from
  `NpcSquadSignal.MAN_DOWN`/`LEADER_DOWN` of the garrison squad (T2).
- **The one API gap:** delivery is range-based. Add an **additive** seam to `gangland-api`: `RadioVoice.audience(NpcSquad)`
  returning `@Nullable Collection<Player>`, default `null` = today's range rule (contract rule: the api only adds;
  minor bump of `GanglandApi.VERSION`). When non-null the audience hears the line at any distance and the
  player-gap still applies. Tests: extend `gangland-api/src/test/.../SquadRadioTest.java`.
- **Keystone:** none. **Module edge:** none (turf already depends on `gang` and `civilians`).
- **Risk:** chat spam in a see-saw fight. Reuse the existing throttles and add `Milestone` lines only on upward crossings
  (the capture loop already fires milestones on upward crossings only). Add a per-gang off switch
  (`/glw gang alerts`) as a follow-up, mirroring `Radio.Enabled`.
- **Map link:** the compass/side words (`RadioSides.compass8`) give the line a direction; the map agent can render the
  same "contested" state as a marker. Suggest exposing `TurfAlertLevel` (T3) as a small pure enum in `gangland-api` so
  the map reads it without depending on turf.

### T2. Garrison as a per-turf `NpcSquad`, with posts and a leash

- **Source:** H11/H12 `NpcSquad` (`add`/`remove`/`leader`/`reportSighting`/`setFormationArc`/`setListener`), the cop
  group model `CopGroup.getSquad()`, `CopManager.newGroup`, cop `ReturningBehavior` and `Cops.Pursuit.Max_Distance`.
- **Today:** `TurfDefenderDeployer.Group` is only a list plus a supplier; it owns no `NpcSquad`. Each defender joins the
  faction squad `CivilianService.squadFor` resolves (per type and target, shared across turfs). Defenders stand on
  the region centre and chase anyone in `targetingRadius`.
- **For turf:** NEW `TurfGarrisonSquad` (in turf) holds one `NpcSquad` per turf, sets `setFormationArc` from the garrison
  tier (T3), installs the turf radio listener (T1), and is handed to each defender through the existing seam: `CivilianNpc.setFactionSquads(FactionSquads)` is a Lombok
  `@Setter` (public; field at `CivilianNpc.java` line 94) and `CivilianNpcRegistry` already injects it per NPC, so turf can
  override it per defender with a `FactionSquads` implementation returning the turf squad.
  **That seam alone is not enough (a civilians-side change is required).** It is consulted only on the retarget/idle
  entry path (`CivilianCombatBehavior.squadFor`, lines 118-127). On a **hit**, `CivilianService.alertFaction` (line 194-213)
  does `squads.computeIfAbsent(new SquadKey(faction, attackerId), ...)` and `victim.joinSquad(...)` directly, and
  `CivilianCombatBehavior.squadFor` then keeps `npc.getSquad()` because the target matches. So the first time a defender is
  hit it leaves the turf squad and rejoins the shared `(turf_defender, attacker)` squad. Fix in `gangland-civilians`: route
  `alertFaction` (and its `recruit` scope) through `victim.getFactionSquads()` when one is set, or make the faction id
  per turf (for example `turf_defender:<turfId>`, which also scopes `recruit`'s same-faction test to one garrison).
  Add two turf-owned behaviours the cops already have:
  1. **Posts:** derive 3-6 defensive posts from the turf region (corners and the highest-block centre, using the same
     surface lookup as `regionCentreSurface`) instead of stacking on one block; an idle defender walks to its post.
     Optional admin `/glw turf post add`. Use `NpcFanPlacement` (T4) to prefer front/back posts.
  2. **Leash:** a defender whose position leaves the region plus a margin (config `Leash_Margin`, default 16) stops
     chasing and returns to its post, the way a cop returns beyond `Pursuit.Max_Distance`. Implement in
     `TurfDefenderDeployer.tick` (it already runs every 5 ticks over every defender) by clearing the target and
     `navigateTo(post)`; needs no new `CivilianState` if done as a deployer-owned override.
- **Keystone:** none required. A generic "hold post + leash" (`AbstractNpc.setPost(Location, radius)`) is a plausible
  upstream primitive for a later Keystone 1.15.0 if cops/civilians also want it; do not fork it locally. Decision for the
  planner: build the deployer-owned version first, promote only if a second consumer appears.
- **Risk:** changing squad identity (with the civilians hit-path fix above) moves defenders out of the shared `(faction, target)` squad, so cross-turf
  recruitment stops. That is desirable, but the docket entry about `findOwningTurfId` scanning every defender per
  damage event (docket census line ~504, "Index defenders by entity UUID") should be fixed in the same change, since the
  deployer's tracking structure is being reworked anyway.

### T3. Reinforcement waves and alert tiers

- **Source:** `CopGroup.requestBackup`/`backupExtra`/`consumeBackupExpiry` and `config/BackupSettings.java`
  (`Extra_Cops`, `Duration_Ticks`, `Cooldown_Ticks`); `CopManager.spawnTick` (target count scaling, `Escalate`
  dispatch when the tier rises, `releaseSurplus`); wanted tiers doc `documentation/features/wanted-bounty.md`
  ("Police Response Per Star").
- **Today:** turf consumes all stock at once and spawns everything at the centre. Docket already flags it
  (`GarrisonDeployListener.java:48-57`, "whole garrison is consumed on the first capture start").
- **For turf:** treat the garrison stock as a **reserve** and deploy it in waves, mirroring wanted-level scaling:
  - `TurfAlertLevel` (1-3, NEW, pure enum) derived from capture progress and attacker head-count; the level picks
    a composition (T4) and cadence, like a wanted level picks a cop tier.
  - `Max_Alive` per turf caps simultaneous defenders; a defender death (`MAN_DOWN`) or a progress milestone triggers a
    wave after a cooldown, like `Backup.Cooldown_Ticks`. Waves spawn on the turf **perimeter, out of the attackers' sight**
    (see below) and walk to posts, instead of appearing in the middle.
  - **Refund:** at capture end, stock never deployed returns to the turf's garrison row (fixes the consumed-on-first-start
    docket item; keep `GarrisonManager.consume` semantics but deploy incrementally).
  - Promote `BackupSettings` to `gangland-api` as a shared record (`WaveSettings` NEW, additive) so cops and turf read the same
    shape; it is currently `cops-n-crooks/.../config/BackupSettings.java`.
- **Spawn placement:** the H13 `EntitySpawner.findClosestSpawnerLocation(Player, Predicate<Location>)` overload is
  player-relative and spawner-based, so it does not fit a turf perimeter. Cops use `CopSpawnManager.spawnNearPlayer`
  (ring fallback); civilians have `CivilianSpawnManager.spawnCivilian(Location, typeId)`. For turf, pick perimeter spots
  inside the region and reject any spot in line of sight of a challenger (`LivingEntity.hasLineOfSight`), cheap for
  a handful of candidates. If both cops and turf want a generic "spawn out of sight around a region", propose it upstream
  in `keystone-npc` `EntitySpawner` rather than copying the cop ring code.
- **Keystone:** none required for the base; optional upstream placement helper.
- **Risk:** balance and server load (`Cops.Max_Per_Player` is the precedent: keep a hard `Max_Alive`). Alert-level
  thresholds are tuning, not code; ship conservative defaults and a `Waves.Enabled` off switch.

### T4. Roles: Defender (shield), Marksman, Medic, Commander (= Quartermaster)

- **Source:** unmerged `gl13-roles` (`c47e859f`..`7dc4ddaf`): `config/CopRole.java` (record with `placement`, `rangedMin/Max`,
  `healthMultiplier`, `offHand`, `leaderPriority`, `strafeDegrees`, `fireRateScale`, `difficultyBonus`, `retreat`,
  `blockFraction`, `blockConeDegrees`, `medic`, `commander`; `overlay(tier)`, `rangedBand(reach)`, `blocks(self, from)`,
  `nextRole(composition, live)`), built-in catalogue Pointman/Assault/Defender/Marksman/Medic/Commander and
  `Squad_Composition` by wanted level in `YamlCopConfigProvider` (lines ~44-52 and ~414-430), `CopListener` shield
  reduction in the shared damage handler, `BartizanNpcWeapons.reach` for band clamps. Keystone 1.14.0 hooks:
  `NpcFanPlacement`, `AbstractNpc.setFanPlacement`, `setLeaderPriority`, `setRangedBand`/`clearRangedBand`.
- **Today (cops):** roles are on by default; each spawn takes the next unfilled entry of the composition for the wanted
  level. **Field care (Medic heals a hurt mate) is planned but not implemented** (`_programme/h13/plan.md` "Field care").
- **For turf:** a garrison composition by alert level (T3), e.g. L1 `[Guard, Guard]`, L2 `[Commander(QM), Defender,
  Marksman, Guard]`, L3 adds `Medic`. The Quartermaster is the natural **Commander** (`leaderPriority` high, radio speaker,
  sits at the rear, T10 when it falls). Shielded Defenders at the doors and a Marksman on the highest post make the
  garrison counterable (flank the shield, kill the marksman).
- **Structural blocker:** `CopRole.overlay` takes a `CopTierConfig`, so the record is cop-shaped. To reuse it, promote a
  generic `NpcRole` (NEW; name, placement, band, health multiplier, off-hand, leader priority, block fraction and cone,
  medic, commander) into `gangland-api` next to `TacticsConfig`/`RetreatSettings` (which already made this trip), keep
  `CopRole` as a thin adapter, and add a `CivilianTypeConfig` overlay in `gangland-civilians` (new optional
  `AI.Combat.Roles` block, built-in defaults so old `civilians.yml` files keep working). The shield reduction needs one guard
  in the civilian damage path (`CivilianDamageListener`); check both gun and melee converge on the same event as
  H13 did for cops.
- **Keystone:** 1.14.0 required (unmerged). **Depends on** H13 merge.
- **Risk:** largest item; role UI text (Spanish files exist: `civilian_messages_es.yml`). Gate behind
  `Roles.Enabled`. Medic field care should wait for `GL-CARE` to land for cops first, then be shared.

### T5. Turf heat feeds wanted level; cops join gang fights

- **Source:** wanted lifecycle in `CopManager.onWantedStart`/`onWantedLevelChange`/`onWantedEnd`, `Wanted.setLevel`/
  `incrementLevel` (`gangland-core/.../wanted/Wanted.java`), dispatch (`CopRadio.dispatch`, `Dispatch_Wanted`,
  `Escalate`), and today's civilian rule in `CivilianDeathRewardListener`: killing a civilian raises wanted **unless**
  it was hostile and in `COMBAT`, so killing turf defenders currently costs no heat.
- **Today:** turf wars and cops are disconnected. The only bridge is `cops-n-crooks/.../listener/turf/TurfFriendlyFireListener.java`
  (owner-gang damage cancel on defenders).
- **For turf:** NEW `TurfHeat` in **cops-n-crooks** (legal edge: `cops-n-crooks module.yml Depends: [turf, civilians]`; turf
  must never depend on cops). It listens to turf events and gang-on-gang kills on a turf, accumulates heat per gang with
  decay, and at thresholds sets or bumps wanted level for the aggressors present, plus a dispatch line ("Shots fired
  at Riverside"). Design rules to keep it fair:
  - heat accrues to the **initiator** (the challenger that started the contest) and to any player who kills a
    non-hostile bystander; defenders defending are not penalised;
  - a per-turf flag `Policed` (default true only for turfs inside a configured district) so remote turfs stay lawless;
  - cops respond to the *location*, not to a side: they hunt wanted players and treat defenders as bystanders, never
    as targets, so an owner cannot weaponise the police against attackers without provoking them.
- **Keystone:** none. **Reuse:** dispatch tiers and the `Backup.Extra_Cops` cap keep the load bounded.
- **Risk:** highest balance and griefing risk in the list; needs owner decisions (see section 6). Ship behind
  `Turf.Heat.Enabled: false` first.

### T6. Gang-tagged callsigns and radio identity

- **Source:** unmerged `gl13-names`: `cops-n-crooks/.../config/CopNames.java` (format `"%rank% &f%name% &7#%badge%"`, badge =
  1000 + Citizens id, `shortName` at most 16 chars, no colours), `CopNpcFactory` (hidden nameplate + coloured hologram
  line), `CopRadio.callsign`. Hard rule from the H13 plan: the words `CIT-` must never appear (a Citizens PLAYER NPC
  name over 16 chars swaps to the team name).
- **For turf:** defenders read `[Vipers] Defender Bob #1042`, the Quartermaster `[Vipers] Quartermaster`, both with the
  owning gang's colour and tag. The radio voice (`FactionVoice.callsign` in `CivilianService` today returns the type
  display name) returns the same callsign so shouts and nameplates agree, and `Shouts.Format` becomes
  `&c[%gang%] &f%unit%&7: &f%line%` instead of `[%faction%]`. Promote `CopNames` to a shared `NpcNames` (NEW) in
  `gangland-api` (additive), keep `CopNames` as the cop format.
- **Cost drivers:** a defender is spawned by `CivilianSpawnManager`, so the gang tag must be applied after spawn in
  `TurfDefenderDeployer.deploy`; the badge and hologram code is Citizens-typed and already lives in Citizens-safe helper
  classes (`CitizensNpcs` in civilians, static utilities per the H13 healthbars note).
- **Effort note:** S if only the format and tag change; M if defenders also get first-name pools per gang.

### T7. Health bars on defenders and Quartermaster (near free)

- **Source:** unmerged `gl13-names`: module `gangland-features/gangland-healthbars` (`module.yml` `Plugins: [Citizens]`,
  `bar/NpcHealthBar.java`, `healthbars.yml`). It draws a bar under the callsign line only for transient, unprotected,
  spawned Citizens NPCs (`NpcHealthBar.update`, `isTransient`), hidden at full health.
- **For turf:** civilians are created transient and unprotected (`CivilianNpcFactory` lines 92-93), so defenders and the
  Quartermaster qualify with zero code. Work is verification (line layout when T6's hologram line is present, the
  insert-then-blink behaviour, `Segments`/`Format` defaults) and shipping the module in `target/modules`. Optional follow-up:
  colour the bar by owning gang, no more than a `HealthBarSettings` extension.
- **Verified hook:** `gangland-healthbars/.../listener/HealthBarListener.java` (in `gl13-names`) listens to plain Bukkit
  `EntityDamageEvent` and `EntityRegainHealthEvent` at MONITOR and only requires the Citizens `"NPC"` metadata, so it is not
  cop-specific and needs no turf hook.
- **Heads-up:** this module's `module.yml` declares `Plugins: [Citizens]`, a hard fail-fast Citizens requirement, which
  differs from CLAUDE.md's "no `module.yml` names Citizens" convention (Citizens is a degradation gate elsewhere). Fine for
  an optional module, but the planner should know and decide whether to keep it.
- **Effort:** XS to verify and document. **Depends on** H13 merge.

### T8. Neighbour and allied responders

- **Source:** `CopManager.responders(RadioCall, range, limit)` and `isFreeToRespond` (lines ~685-727): a `CONTACT`/`MAN_DOWN`/
  backup call pulls up to `Radio.Responder_Max` nearby free cops, **across groups**, as NPC hearing independent of any
  player. `CopRadio.RadioCall` carries group, squad and origin.
- **For turf:** on `CONTACT` or `MAN_DOWN` at a turf, garrisons of turfs **adjacent** to it (same owner) and of **allied**
  gangs' adjacent turfs send `Responder_Max` defenders (default 1) for a limited time; players of those gangs get a T1-style
  alert. It rewards holding contiguous land, which is exactly what a chunk map shows, and gives alliances a concrete
  in-fight effect. Adjacency comes from the turf regions (`CuboidRegion` in `turf/data`), a bounding-box overlap test
  with margin.
- **Keystone:** none. The `Radio` "responder" record shape is copyable; keep it turf-local (`TurfRadioCall`, NEW) unless T3
  promotes wave settings to the api.
- **Risk:** cascade fights across a large territory; cap responders per call and per minute; a responder returns to its
  post after the alert (T2).

### T9. Defender bounty drop

- **Source:** unmerged `gl13-drop`: `GanglandMoneyDropClassifier` (module source first, `NPC` context for unknown Citizens
  NPCs), `MoneyDropListener` (one MONITOR handler classifying every death), `cops-n-crooks/.../seam/CopsMoneyDropSource.java`,
  `money.yml` ships no NPC source so unrecognised NPCs drop nothing.
- **Today:** `turf_defender` and `quartermaster` drop nothing (`Drops.Items: []`, `Experience: 0`); `CivilianDeathEvent`
  already carries experience, so XP is a config change.
- **For turf:** NEW `TurfDefenderMoneyDropSource` in turf (module source, same shape as the cop one). A killed defender drops a
  fraction (config, default 50%) of its purchase cost (`TurfPowerupGarrisonView.PER_DEFENDER_COST`, hard-coded today per the
  docket), funded from the cost the owner already paid: attackers are rewarded, the owner pays for weak defence, and
  no money is minted. Add `Drops.Experience` for the same kill.
- **Depends on** the H13 classifier fix (without it a PLAYER-type NPC's death takes the player path and drops nothing;
  fixed in `gl13-drop` commit `5b7325b5`).
- **Risk:** economy sink/faucet balance; farming a friendly garrison. Friendly fire from the owner is already cancelled
  (`TurfFriendlyFireListener`); exclude allied gangs the same way.

### T10. Commander-down morale

- **Source:** `NpcSquadSignal.LEADER_DOWN` (`NpcSquad.memberDown`), H13 `Commander_Down` line and "falls back briefly"
  (`CopRetreat.takeCover` with a duration, `state/behavior/CopRetreat.java`).
- **For turf:** when the Quartermaster (the squad's `leader()` via leader priority, T4) dies, the garrison radios
  `Commander_Down`, retreats for a few seconds and the capture rate gets a one-off bump for attackers. Small, legible,
  rewards an assault that targets the QM.
- **Effort:** S once T2 exists. Needs Keystone 1.14.0 only if leader priority (T4) is used; without it the leader is the
  first-joined defender and the effect is weaker.

### T11. Gang comms for players: man-down and ping callouts

- **Source:** `SquadRadio.say`, `RadioSides` (`compass8`, `sideOf`), `RadioLines` (`Compass`, `Sides`).
- **For gangs:** a member-less `NpcSquad` per gang serves as the throttle key (SquadRadio keys only on the object), with an
  audience of online gang-mates (T1 seam). Lines: "Ryan is down at Riverside", "Contact at the harbour, north-east". Also a
  `/glw gang ping` command that emits a `Contact`-style line with the sender's position. Relevant even outside turf
  fights, and it is the player-facing twin of the NPC radio.
- **Risk:** low; command needs the `commands.json` entry in the owning jar (repo rule). Module owner is `gang`
  (`Id: gang`), not turf, unless the line is turf-specific.

### T12. Stuck recycling for defenders

- **Source:** unmerged `gl13-stuck`: `config/StuckSettings.java`, `Cops.Stuck` (`Enabled`, `Recycle_Seconds` 12, `View_Distance`
  24, `Avoid_Spawner_Seconds` 60), `CopManager.spawnTick` recycle rules (kept while the suspect sees it, within melee
  reach, or a bystander faces it), Keystone `AbstractNpc.millisUnreachable()`.
- **For turf:** `TurfDefenderDeployer.tick` already reaps invalid/expired defenders; add a stagnation check (unreachable for
  N seconds, out of the challenger's view) and respawn the defender at another post (T2), skipping the post it was stuck at.
  Fits the surface-spawn pattern, where `getHighestBlockYAt` can place a defender on a tree or a roof (`regionCentreSurface`).
- **Keystone:** 1.14.0 (unmerged). **Depends on** H13 merge. Do not port before the cop version is accepted live; H13
  handoff notes the cap-verdict acceptance risk (35-block climb) that would silently disable it.

### T13. De-escalation: stand-down and walk-home

- **Source:** `Stand_Down` (`CopManager.onWantedEnd`, `stoodDown` set), `Backup` expiry and `releaseSurplus` (cops walk home
  and despawn instead of lingering).
- **For turf:** `onCaptured`/`onFailed` in `GarrisonDeployListener` call `defenders.recall`, which `markForRemoval`s every
  defender instantly. Instead, radio `Turf_Held`/`Turf_Lost`, let survivors walk to a post and despawn a few seconds
  later (or stay as a light garrison for an owner-paid grace period).
- **Effort:** S. Pure polish, but it makes the end of a fight readable.

## 3. Mapping H11-H13 primitives to gang/turf uses (checklist for the planner)

| Keystone / api primitive | Gang and turf use | Catalogue |
|---|---|---|
| `NpcSquad` + `setListener` | one squad per turf garrison; radio edge signals | T2, T1 |
| `NpcSquadSignal` `CONTACT`, `MAN_DOWN`, `LEADER_DOWN`, `FALL_BACK`, `CLIMB` | alert lines, responder calls, morale | T1, T8, T10 |
| `AbstractNpc.pursue` / `canSee` / `takeCover` | already used; leash and posts sit on top | T2 |
| `NpcSquad.setFormationArc` | per alert tier, not per type | T3 |
| `NpcFanPlacement`, `setLeaderPriority`, `setRangedBand` (1.14.0) | shield front, marksman rear, QM leads | T4 |
| `AbstractNpc.millisUnreachable()` (1.14.0) | recycle stranded defenders | T12 |
| `EntitySpawner` predicate overload (1.14.0) | optional spawner-based waves; turf mostly needs a region-relative helper | T3 |
| `SquadRadio` / `RadioVoice` / `RadioSides` (`gangland-api`) | turf and gang radio, callsigns, directions | T1, T6, T11 |
| `CopRadio` dispatch/backup/respond wiring (cop-private) | copy shape into a `TurfRadio` | T1, T3, T8 |
| `MoneyDropClassifier` module source (H13) | defender bounty | T9 |
| `gangland-healthbars` module (H13) | defender and QM bars | T7 |

## 4. Deliberately not transferred

- **Cuff-then-fight escalation and detainment.** Cops cuff wanted players, then fight after enough escapes
  (`migration-0.12.0.md` section 6). Gang wars are combat, not arrest; the equivalent (cop arrival, T5) already carries it.
- **Wanted-decay freeze and boss-bar HUD** were declined in H12 (`phase-h12-npc-tactics.md` "Deliberately not built");
  turf already has its own boss bar (`TurfBossBarListener`) and progress bars.
- **Suppressive fire and radio-operator silence** were declined by the owner (`decisions.md` #7); do not reopen for turf.
- **Bartizan weapons on defenders.** Defenders already carry `weapon:rifle` (`civilians.yml`); the Bartizan cadence/reload
  fixes (0.6.0) apply with no turf work, and `Plugins: [Bartizan]` stays on civilians, not turf.
- **Per-cop first names as game content.** Keep names cosmetic; do not add a "named NPC" progression system.

## 5. Sequencing and version constraints

1. **Merge H13 first** (Keystone 1.14.0 `phase-h13-npc-roles`, Gangland `npc-roles-medics` and its four sub-branches).
   T4, T7, T9, T10 (full), T12 need it; T6 shares `CopNames`. Gangland built on 1.14.0 fails with `NoSuchMethodError` on a
   1.13.0 server (`phase-h13-npc-roles.md` "Backward compatibility"), so the wave's Keystone pin becomes 1.14.0.
2. **Sandbox-accept faction alert** on the current civilians module (H12 left it unverified), because T1/T2/T8 stand on it.
3. **Build order inside the wave:** T2 (squad + posts + leash, **including the `gangland-civilians` `alertFaction` hit-path change**, which ships in the civilians jar and needs its own version bump and test) and the `RadioVoice.audience` api seam (T1) first; then T1 lines,
   T13, T3; then T6 + T7 in parallel; then T4; then T8, T9, T10, T12; T5 and T11 last (they cross modules).
4. **Module edges** (never break): `turf Depends: [civilians, gang]`, `cops-n-crooks Depends: [turf, civilians]` and
   `Plugins: [Bartizan]`. Only additive `gangland-api` classes/methods within the current major; promoted records
   (`NpcRole` NEW, `NpcNames` NEW, `WaveSettings` NEW) are new classes, so bump the api minor and add them to `documentation/gangland-api.md`.
   A module that needs new strings puts them in its own YAML (`civilian_messages*.yml`, a new `turf_messages*.yml`), not in `Messages`.
5. **Keystone upstream candidates** (only if a second consumer appears): a generic `AbstractNpc` hold-post-and-leash
   primitive (T2) and a region-relative out-of-sight spawn helper on `EntitySpawner` (T3). Nothing else in this list needs
   Keystone work.
6. **Docket:** each shipped idea closes or references existing rows: garrison consumed at first start (T3), `findOwningTurfId`
   indexing (T2), hard-coded `PER_DEFENDER_COST` (T9), `reinforced_defense`/`garrison_discount` buffs do nothing
   (`docket-gang-turf.md` lines ~272, ~377, ~398, ~504). New bugs found go into `triage/<slug>.txt` and the docket rebuild.

## 6. Owner decisions needed

1. **T5 policy:** should turf fighting ever raise wanted? Default proposed: yes, for the initiator only, only in `Policed`
   districts, off until decided.
2. **T3 refund:** should undeployed garrison stock refund to the owner when a capture fails or is defended, or be spent
   on the attempt? Default proposed: refund.
3. **T9 economy:** defender bounty as a fraction of the owner's spend (neutral) or minted (a faucet)? Default proposed:
   neutral, funded by the owner.
4. **T2 leash:** may defenders leave the turf at all (e.g. chase to the border), or hold strictly inside? Default proposed:
   region plus 16 blocks.
5. **T4 medic:** wait for cops' `GL-CARE` (planned, not built) or ship roles without the Medic first? Default proposed:
   ship without Medic.

## 7. Verification notes (what I actually read)

Read in full or in the relevant sections: `Keystone/docs/phase-h11-npc-navigation.md`, `phase-h12-npc-tactics.md`,
`keystone-npc.md` (1.12.0-1.14.0 sections) and the H13 doc from `wt/keystone-1.14.0`; the H11 design spec's problem/finding
sections (not the 6,829-line plan); `gangland-api` `SquadRadio`, `RadioVoice`, `RadioSides`; `CopRadio`, `CopManager`
(wanted lifecycle, spawn tick, responders index), `CopGroup` and `BackupSettings` by outline; `CivilianService`,
`FactionSquads`, `CivilianCombatBehavior` (squad and retreat), `civilians.yml` turf types, `civilian_messages.yml` shouts,
`CivilianDeathRewardListener`; turf `TurfDefenderDeployer`, `TurfDefenderConfig`, `TurfPowerupNpc`, `GarrisonManager`,
`Garrison`, `GarrisonDeployListener`, `CaptureService` (outline and events), `CaptureSettings`; module descriptors for
turf, cops-n-crooks, gang; H13 sources from `gl13-roles` (`CopRole`, catalogue defaults), `gl13-stuck` (commit message and
`cops.yml` `Stuck` block), `gl13-names` (`CopNames`, `NpcHealthBar`, `healthbars.yml`, `module.yml`), `gl13-drop` (commit
message and file list), `_programme/h13/decisions.md` and `plan.md`.

Not read: the graph could not answer for Keystone classes (graph covers only this repo; `NpcSquad` appears as an external
node), so Keystone claims come from its docs and the Gangland call sites. `documentation/features/cops-n-crooks.md` was only skimmed by heading/first section. `migration-0.11.0.md` was read in full (46 lines) and agrees with this report (`Faction` defaults to the type id, `AI.Combat.Alert_Range` 16, faction alert is one hop, a same-faction hit recruits nobody); nuance for T12: since 0.11.0 cops already rotate out on `Cops.Pursuit.Max_Ticks`/`Max_Distance` when stuck and unseen, so the H13 stagnation recycle adds to that, while turf defenders have no equivalent at all. `gangland-gang` internals and the turf boss-bar/notifier
listeners were not read (gang-side identity for T11 needs the gang census). The `TurfDefenderDeployer` leash claim (no
turf-bounds check) is from reading `retarget`, not from a live run.
