# Cops N Crooks System

## Overview

The Cops N Crooks module (`gangland-features/cops-n-crooks`) is the AI-driven law enforcement and civilian NPC
subsystem for Gangland Warfare. It provides:

- **Police NPCs** -- AI-driven cop NPCs that pursue, cuff, and engage wanted players
- **Civilian NPCs** -- ambient and hostile NPCs with independent behavior (wander, flee, combat)
- **Wanted system** -- GTA-style 1-5 star wanted level tracking with escalating police response
- **Bounty system** -- player bounty tracking with configurable scaling and kill rewards
- **Kill combo system** -- consecutive kill tracking that feeds into wanted level escalation
- **Detainment and jail** -- handcuffing, jail cells, visual restraint effects

The module contains 91 classes organized across these packages:

```
org.luckyraven.gangland.copsncrooks
  bounty/            Bounty tracking and executor
  combo/             Kill combo system
  detainment/        Detainment states, registry, service
  entity/            Abstract NPC base, spawner framework, entity marks
  events/            Custom Bukkit events (11 total)
  jail/              Jail cells, registry, service
  listener/          Bukkit event listeners
  npc/civilian/      Civilian NPC system
  npc/police/        Cop NPC system
  wanted/            Wanted level tracking and executor
```

All NPC entities are backed by the [Citizens](https://citizensnpcs.com/) plugin API.


---

## NPC Architecture

### Inheritance Hierarchy

```
NpcBehavior<T>                   (interface -- tick/onEnter/onExit contract)
  CopBehavior                    (marker interface, T = CopNpc)
  CivilianBehavior               (marker interface, T = CivilianNpc)

AbstractNpc                      (base class -- weapon pipeline, navigation, combat)
  CopNpc                         (cop state machine, tier config, cuff lock)
  CivilianNpc                    (civilian state machine, group membership, entity target queue)
```

### AbstractNpc -- Base NPC Layer

**Package:** `org.luckyraven.gangland.copsncrooks.entity.npc`

`AbstractNpc` is the shared base class for all Citizens-backed NPC types. It owns:

| Responsibility                  | Key Methods                                                                                    |
|---------------------------------|------------------------------------------------------------------------------------------------|
| **Citizens NPC lifecycle**      | `isValid()`, `destroy(EntityMarkManager)`, `markForRemoval()`, `getEntity()`                   |
| **Three-tier attack pipeline**  | `attack(Player)`, `attackEntity(LivingEntity)` -- gangland weapon > vanilla ranged > melee     |
| **Gangland weapon integration** | `setHeldWeapon(Weapon, JavaPlugin)`, `performGanglandWeaponAttack()`, `triggerReload()`        |
| **Vanilla ranged attacks**      | `performVanillaRangedAttack(Player)` -- ray-traced ranged damage with `BOW`/`CROSSBOW`         |
| **Melee fallback**              | `performMeleeAttack(Player)`, `performMeleeAttackOnEntity(LivingEntity)` -- knockback + damage |
| **Citizens navigation**         | `navigateTo(Location)`, `stopNavigation()`, stuck detection, hopeless fallback                 |
| **Ranged hold**                 | `shouldHoldPursuitPosition(target)` -- ranged NPCs hold position within firing window          |
| **Wander pathfinding**          | `findForwardWanderDestination(min, max)` -- forward-biased cone scan for walkable destinations |

#### Attack Pipeline

When `attack(Player)` is called, the NPC selects the highest-priority available attack method:

```
1. Gangland weapon attack (GunWeapon projectile with reload management)
   - Consumes ammo, fires WeaponProjectile, plays shot sound
   - Auto-reloads when magazine empty or weapon broken
   - Cooldown = gun's projectile cooldown (minimum 5 ticks)

2. Vanilla ranged attack (BOW/CROSSBOW in main hand)
   - Ray-trace 35 blocks, particle + sound effects
   - Cooldown = 15 ticks

3. Melee fallback
   - Direct damage + knockback vector
   - Cooldown = 5 ticks
```

#### Navigation and Stuck Detection

`AbstractNpc` integrates Citizens pathfinding with a custom stuck-detection layer:

```
navigateTo(location)
  │
  ╰── shouldRecalculateNavigation(location)
  │     Throttles by recalculation interval, checks distance delta >= 2.25 blocks
  │
  ╰── updateNavigationProgress()    (called each tick by subclasses)
        Samples position every stuckCheckIntervalTicks
        If progress < minProgressDistance:
          consecutiveStuckChecks++
          If >= maxStuckChecks:        isNavigationStuck() = true
          If >= maxHopelessStuckChecks: isNavigationHopeless() = true
```

When navigation is hopeless, subclass behaviors call `resolveHopelessFallbackLocation(target)` which uses a
three-strategy fallback chain:

1. **Gap walk** -- `findLastReachableGroundBeforeGap()` walks block-by-block toward the target, returning the last
   standable position before an impassable gap
2. **Line approach** -- `findLineApproachLocation()` probes along the line from the target toward the NPC
3. **Ring approach** -- `findBestRingApproachLocation()` scans concentric rings around the target, scoring candidates
   by distance, ideal-radius deviation, and clear-shot availability for ranged NPCs

All location candidates are validated with `normalizeToStandableLocation()` which searches +2/-4 Y offsets for a
position with solid ground, passable feet/head, and no hazards (lava, water, cactus, magma, portals).

#### Abstract Contract

Subclasses must implement:

```java
boolean canUseWeapons();           // Whether this NPC type uses the weapon pipeline

double getAttackDamage();         // Base melee damage

void equip();                   // Apply armor + weapon to EntityEquipment

void cleanupTransientState();   // Release behavior resources on destroy
```

---

## Cop NPC System

### Package Structure

```
npc/police/
  CopService           Service facade -- initializes all cop components
  CopManager           Central manager -- spawn tasks, AI tasks, target resolution
  CopGroup             Collection of cops pursuing the same target player
  config/
    CopConfigProvider   Interface -- all cop configuration values
    YamlCopConfigProvider   YAML-backed implementation
    CopConfig           Raw parsed YAML data
    CopLoader           YAML loader
    CopTierConfig       Per-tier record (health, damage, speed, armor, weapons, difficulty, tactics, fire rate)
    CopSettings         Settings.yml cop section contract
    BackupSettings      Cops.Backup record (0.12.0)
  radio/
    CopRadio            The police radio: squad signals -> chat lines, dispatch, responder calls (0.12.0)
    CopRadioMessages    npc/cop_radio_messages(_es).yml lines, via LocalizedModuleYaml (0.12.0)
  npc/
    CopNpc              Individual cop NPC instance
    CopNpcFactory       Creates and equips CopNpc instances
  spawn/
    CopSpawnManager     Extends EntitySpawner -- location finding + factory calls
    CopSpawner          EntitySpawnerPoint for cops
  state/
    CopState            Enum: IDLE, PURSUING, CUFFING, COMBAT, RETURNING
    CopBehavior         Interface extending NpcBehavior<CopNpc>
    CopBehaviorFactory  Creates the full state-to-behavior map
    CuffLockRegistry    Global one-cop-per-target cuffing lock
    behavior/
      IdleBehavior
      PursuingBehavior
      CuffingBehavior
      CombatBehavior
      ReturningBehavior
  targeting/
    TargetingManager    Interface
    WantedTargetingManager   Tracks wanted players, finds nearest target
```

### CopService

Entry point for the cop subsystem. Called once during plugin enable:

```java
CopService copService = new CopService();
CopManager copManager = copService.initialize(
		plugin, copConfigProvider, entityMarkManager,
		weaponService, spawnerRepository, detainmentService
);
```

Internally wires:

1. `WantedTargetingManager` -- tracks which players are wanted
2. `CopBehaviorFactory` -- creates behavior maps (shared `CuffLockRegistry`)
3. `CopNpcFactory` -- creates individual cop NPCs with equipment
4. `CopSpawnManager` -- finds spawn locations and delegates to factory
5. `CopManager` -- orchestrates spawn tasks and AI tasks per player

### CopManager

The central orchestrator with ~20 methods managing cop lifecycle:

| Method                                          | Purpose                                                              |
|-------------------------------------------------|----------------------------------------------------------------------|
| `onWantedStart(Player, Wanted)`                 | Registers wanted player, reports the crime scene to the group's squad, sends the group's returning cops back to pursuit, starts spawn + AI tasks |
| `onWantedEnd(Player)`                           | Unregisters target, lets cops organically find new targets or return |
| `onWantedLevelChange(Player, Wanted, old, new)` | Routes to start/end based on level transitions                       |
| `onCopAttackedAlert(CopNpc, Player)`            | Alerts ALL cops in group -- forces combat mode; reports a sighting when the attacker is the group's target |
| `onCopAttacked(CopNpc, Player)`                 | Forces single cop into combat with attacker                          |
| `removeCopAttacker(UUID)`                       | Clears attacker from registry (death/leave), clears cop targets      |
| `getCopsForPlayer(UUID)`                        | Returns all cops assigned to a player                                |
| `isCopNpc(Entity)`                              | Checks if entity is a managed cop                                    |
| `findCopByEntity(Entity)`                       | Looks up CopNpc by Bukkit entity                                     |
| `shutdown()`                                    | Stops all tasks, despawns all cops                                   |

#### Spawn Task

A per-player `BukkitTask` running at `configProvider.getSpawnCheckRate()` interval:

```
Every spawn check interval:
  1. Skip if player is offline or detained
  2. Remove invalid/marked cops from group
  3. Calculate target count = getTargetCopCount(wantedLevel)
  4. Calculate tier = getTierForWantedLevel(wantedLevel)
  5. While currentCount < targetCount AND < maxCopsPerPlayer:
       Spawn cop near player at tier
       Set target, set combatForced if alert active
       Transition to PURSUING
```

Cops spawned during an active combat alert are immediately set to `combatForced = true`.

#### AI Task

A per-player `BukkitTask` running at `configProvider.getAiTickRate()` interval:

```
Every AI tick:
  1. Skip if player is offline
  2. Self-cleanup if group empty and player no longer wanted
  3. For each cop in group:
     a. Remove if marked or invalid
     b. resolveTarget(cop, defaultPlayer) -> LivingEntity
     c. cop.tick(target)
```

#### Target Resolution Priority

`resolveTarget()` determines what each cop should pursue, in order:

```
1. Current entity target (hostile civilian NPC) -- keep if still alive
2. Current player target -- keep if still wanted OR is a cop-attacker with combatForced
3. Nearest wanted player in same world (skip downed players)
4. Nearest player who previously attacked a cop (engage in COMBAT)
5. Nearest wanted hostile civilian NPC (in COMBAT state)
6. Pending entity attacker queue (self-defense, lowest priority)
7. No target found -> transition to RETURNING
```

#### Squads (Keystone `NpcSquad`, 0.11.0)

Each `CopGroup` owns one Keystone `NpcSquad`: the group's shared awareness of its wanted player (last-known position,
sighting time, routes). A cop joins it in `CopGroup.add` when the spawn task assigns it, leaves it on
`ReturningBehavior` entry, and is dropped from it by `CopGroup.release` when it is despawned, killed or invalid.
`CopNpc.squadFor(target)` returns the group squad only when `target` is the group's wanted player; for an entity target
or a player the cop retargeted to it returns a squad of the cop's own, seeded with the target's position when the cop
is handed that target and replaced when the target changes.

Sightings are reported by `onWantedStart` (the crime scene), `onCopAttackedAlert` (the attacker, when they are the group's
target), `IdleBehavior` (`cop.canSee(target, alertRange)`) and by Keystone's `AbstractNpc.pursue`, which every
`PURSUING` and `COMBAT` tick calls with `Cops.Behaviour.Alert_Range` as the sight range.

Since 0.12.0 a group also opens one **attacker squad** per other target its cops turn on (an attacker, a retarget, a
wanted civilian): `CopGroup.attackerSquad(id, seed)`. `CopNpc.squadFor(target)` hands out the group squad for the
group's player and the group's attacker squad for anyone else, so cops of one group coordinate on an attacker too.
Only a cop with no group still gets a squad of its own. The previous squad is left whenever the answer changes.

#### Squad tactics per tier (0.12.0)

`CopNpcFactory.applyTuning` gives every cop its tier's `TacticsConfig` engagement (`Cops.Tactics`, overridden key by
key by `Tiers.<n>.Tactics`), the `Cops.Melee` profile (`Approach` clamped to `Cuff_Radius - 0.5`) and a Keystone
fire-rate scale from `NpcFireRate.scale(Fire_Rate_Multiplier, rangedAttack)`: `1 / multiplier` for a Bartizan
weapon, half that for the vanilla bow/crossbow fallback, so `1 / AI_Tick_Rate` keeps the 0.11 cadence on both paths.
The group squad's formation arc comes from the group's tier (shipped: Lieutenant 200, SWAT 270, Military 330);
Keystone ignores the arc for melee members, which always surround at 360/n. `Tactics.Enabled: false` puts the tier
on `NpcEngagement.LEGACY`: 0.11's freeze-in-band, with no formation and no order / `CONTACT` / `RELOADING` /
`CHECK_FIRE` / retreat signals. It is positioning only: `NpcSquad.memberDown` still sends `MAN_DOWN` /
`LEADER_DOWN` (so casualty radio, backup and responders still fire), Gangland's own `Dispatch_Wanted` / `Escalate` /
`Resisting` / `Stand_Down` lines still go out, and `CombatBehavior` still retreats a hurt cop to cover (its radio line
is suppressed). Those are switched by `Radio.Enabled`, `Backup.Enabled`, `Radio.Responder_Max` and
`Retreat.Enabled`, not by `Tactics.Enabled`.

#### Police radio, orders and acks (0.12.0)

`CopRadio` wraps one gangland-api `SquadRadio` (package `org.luckyraven.gangland.npc.radio`, shared with the civilian
shouts) for every `CopGroup`. `CopManager.newGroup` installs `copRadio.listenerFor(group, pendingCalls::add)` on the
group squad and every attacker squad (`CopGroup.setListener`), so each Keystone `NpcSquadSignal` becomes a chat line
keyed by the signal name (`CONTACT` -> `Lines.Contact`, `FLANK_LEFT` -> `Lines.Flank_Left`, ...).

- **Delivery:** a line reaches every player within `Radio.Range` of the speaker or the addressed cop, plus the hunted
  player within `Radio.Target_Range`. `SquadRadio.speak` drops (never queues) a line that fails a gate: radio
  disabled or an empty line list, the squad gap (`Squad_Gap_Ticks`, skipped by `Priority` kinds, acks and
  `SquadRadio.sayLater` follow-ups), the per-kind `Cooldown_Ticks`, and per listening player the player gap (`Player_Gap_Ticks`, skipped by priority kinds).
- **Orders and acks:** `PUSH`, `FLANK_LEFT`, `FLANK_RIGHT`, `SEARCH` and `NO_ROUTE` are spoken as orders: the squad
  leader speaks them to the member (`%member%`), and a leader never orders itself. `MAN_DOWN`/`LEADER_DOWN` are also
  spoken by the leader; every other signal by the member itself. A delivered order schedules the member's `Ack` after
  `Ack_Delay_Ticks` (forced to at least `Player_Gap_Ticks + 5`, so the ack isn't throttled by its own order).
- **Direct lines:** `CopRadio.dispatch` (`Dispatch_Wanted` on `onWantedStart`, `Escalate` when the group's tier rises)
  and `CopRadio.sayFromLeader` (`Stand_Down`, `Resisting`) speak outside the signal flow.
- The radio names a cop with `CopRadio.speakerName`, which renders the `Speaker_Name` template (`{rank}`, `{role}`, `{name}`, `{number}`) from `copsncrooks/cop_radio_messages.yml` with its colours kept (default `{rank} {role} &f{name} &7#{number}`, so `&9Officer &cMedic &fBob &7#1592&7`); the line's own colour is appended so it resumes after the name. `CopRadio.callsign` is that default with colours stripped (`Officer Medic Bob #1592`), used only by logs and `CopListCommand`; it does not follow an owner's `Speaker_Name` edit. A cop with no callsign falls back to `SWAT-17` (tier name plus Citizens id).

#### Role radio lines (0.13.0, H13b)

`CopRadio.kindOf(CopRole)` maps a role to a radio kind: the `Medic` / `Commander` flags and a block cone (Defender) come
from the role itself, `Marksman` and `Assault` from the role's name (case-insensitive); any other role speaks the
generic lines. Every kind below has its own `Lines.<Key>` (2-3 variants, English and Spanish), its own
`Cooldown_Ticks` (code default in `COP_RADIO_DEFAULTS`, in milliseconds) and fires once per signal edge.

| Key | Fires when | Placeholders |
|---|---|---|
| `Overwatch_Set` | a Marksman `ENGAGE`s (replaces `Engage`) | `%distance%` `%direction%` `%target%` `%role%` |
| `Marksman_Spotted` | a Marksman's `CONTACT` (replaces `Contact`; priority) | `%distance%` `%direction%` `%target%` |
| `Marksman_Lost` / `Marksman_Reloading` | a Marksman's `CONTACT_LOST` / `RELOADING` | `%direction%` |
| `Shield_Up` | a Defender `ENGAGE`s (replaces `Engage`) | `%role%` |
| `Flanking` | an Assault cop is ordered to a flank; said two ack delays later, after its `Ack` | `%direction%` |
| `Commander_Orders` | any `ENGAGE` with a live Commander and a Defender, an Assault and a Marksman in the squad; the Commander speaks one ack delay later | `%direction%` |
| `Commander_Orders_Undirected` / `Flanking_Undirected` | the orders / flank call when the signal has no spot to name a direction for (they share the base pool's cooldown: `Flanking` / `Commander_Orders`); a Marksman line with no spot falls back to the generic one | none |
| `Commander_Orders_Basic` | the same `ENGAGE` when the squad lacks one of those roles: orders without role names | `%direction%` |
| `Status_Check` | `CONTACT_LOST` with a live Commander | none |
| `Pull_Back` | `MAN_DOWN` / `LEADER_DOWN` of a non-Commander with a live Commander | `%member%` |
| `Medic_Treating` | the medic's channel first progresses, once per treatment (also after being pinned on arrival) | `%member%` `%health%` `%role%` |

`Hit`, `Medic_Moving` and `Patched_Up` gained `%health%` (whole percent, `Patched_Up` after the heal), `%distance%` and
`%eta%` (rough seconds, `Medic_Moving`: 4.3 blocks/s times the medic's tier `Speed`). A role's radio kind comes from its traits (Commander/Medic flags, a block cone is a Defender, a `FLANK` placement an Assault, a firing band from 12 blocks out a Marksman), the role name only as a fallback, so renamed roles keep their lines. Marksman/Defender lines say `%distance%`/`%direction%` only when the signal carries a spot. A role pool that is muted (`[]`) falls back to the generic line. `Commander_Orders` goes out on the squad's first `CONTACT` as well as on `ENGAGE`; a Marksman's `Overwatch_Set` and a Defender's `Shield_Up` also fire as a follow-up when its flank or push order arrives (distance and direction measured to the hunted target when the line is said, three ack delays out, 0.21.1), so they are heard on open ground where no member takes the fan's centre.
Extras now fill a line before the built-in `%distance%` / `%direction%`, so a caller-measured value wins over the
speaker-to-spot one. Only `Marksman_Spotted` joins the priority set.

#### Radio responders (0.12.0)

A `CONTACT`, `MAN_DOWN` or `LEADER_DOWN` signal queues a `RadioCall(group, squad, origin)`. `CopManager` answers
the queue in `drainRadioCalls()` after the AI tick's cop loop, so no listener changes a cop list mid-iteration. For
each call it takes up to `Radio.Responder_Max` (and no more than the group's room under `Max_Per_Player`) of the
nearest cops within `Radio.Range` of the origin that belong to
**another** group and are `RETURNING`, `IDLE` or chasing a civilian (never cuffing, guarding or hunting a player).
The responder is `detach`ed from its group, added to the calling group, speaks `Responding` and joins the hunt. The
calling group's own cops are never pulled back: a cop walking home was released after a backup or rotated out.
No call is answered once the suspect is restrained. Everything here is NPC hearing and runs with no player in range.

#### Backup (0.12.0)

On `MAN_DOWN`/`LEADER_DOWN` the listener also calls `CopGroup.requestBackup(now, BackupSettings)`. It is granted unless
`Backup.Enabled` is off, `Extra_Cops` is 0 or `Cooldown_Ticks` has not passed, and a granted request is radioed
(`Backup`). While it lasts (`Duration_Ticks`), the spawn task's target count adds `backupExtra` (still capped by
`Max_Per_Player`). `consumeBackupExpiry` then adds `Extra_Cops` to `pendingRelease`, and the spawn task
(`releaseSurplus`) sends home the newest free cops of a role the group holds more of than its base squad for the
current target count (`CopRole.nextRole` over the wanted level's `Squad_Composition`; roles off, any cop). A
replacement spawned mid-backup for a fallen Commander or Pointman therefore stays and the backup's extra goes (T-146),
and the group's last Commander is never sent, even once the wanted level has fallen to a composition without one: he
stays as one cop over the target and `pendingRelease` is capped at what the role excess can ever release (the
Commander-flagged roles' excess pooled and capped once at every Commander but the last, however many roles carry
`Commander: true`), so no radio responder joining later is sent home in his place (T-435). A cop fighting, cuffing or
guarding is never sent; a pursuing, idle or POSTED one is (T-510: leaving POSTED releases its post, and the perimeter,
standoff and breach controllers drop a cop that left it; a perimeter whose last posts went that way ends `reason=EMPTY`);
an OVERWATCH Marksman is not sent and holds the release until `Overwatch.Max_Seconds`. The release retries next run
until enough have gone. `releaseSurplus` runs only while the
player is wanted: a backup that runs out inside the post-escape search sends nobody home (T-187).

#### Resisting (0.12.0)

`CuffingBehavior.failCuff` counts every failed or broken cuff per group (`CopGroup.recordCuffFailure`), because the
cuff lock passes between the surrounding officers. At `Max_Cuff_Attempts`, or when the suspect hits a cop
(`onCopAttackedAlert`), `CopGroup.escalate` sets the group's combat alert. At the end of the next AI tick
`pollResisting` radios `Resisting` once and `fightResisting` moves every cop still after the suspect (not guarding or
returning) to `COMBAT`; cops spawned later fight too.
`clearCombatAlert` resets it when the player is no longer wanted.

#### Retreat to cover (0.12.0)

`CopRetreat` checks `RetreatSettings` (`Cops.Retreat`) each tick of every state that fights: `CombatBehavior`, and
`PursuingBehavior` for ranged cops (a band shooter hunting a wanted player stays in PURSUING and never reaches COMBAT
unless the player attacks). A melee cop in PURSUING is chasing to cuff, not fighting, so it does not retreat, and a
cop with the suspect in cuff range still cuffs. A cop at or below `Health_Fraction` of max health calls Keystone's
`takeCover(target, Radius, squad)`, which joins the squad and sends `FALL_BACK`/`IN_COVER`. A retreat lasts at most
`CopRetreat.MAX_COVER_MS` (10 s) per state episode. `FAILED` (no cover in reach) keeps the cop fighting.
`Fall_Back` and `In_Cover` are `Radio.Priority` kinds, so a same-tick `Resisting` from the hit that caused the
retreat cannot drop them through the per-player gap.

#### Squad roles (0.13.0)

`CopRole` (`copsncrooks/cop_roles.yml` `Roles`, built-in catalogue in `YamlCopConfigProvider.builtInRoles`) is picked per
spawn by `CopRole.nextRole(getSquadComposition(level), liveRoles)` in `CopManager.spawnTick`, counting only the cops the
spawn count counts (a stranded RETURNING cop's role is refilled). The roles file is its own `FileHandler`
(`CopsNCrooksYamlConfig`, written from the module jar when missing); `CopLoader` hands its reader to
`YamlCopConfigProvider` next to the `cops.yml` one, and a missing file (`null` reader) means the built-ins. The shipped
file and `builtInRoles` must stay equal (`CopRolesFileTest.shippedFile_matchesBuiltIns`).

A role carries a `Kit` (`Weapon_Pool` + `Gear`) and per-tier `tierKits`; `CopRole.kitFor(tier, canUseWeapons)` reads
the tier's kit slot by slot over the role's (the role's own pool dropped on a melee tier). `Gear` is a description
(material, leather `Color`, glow) turned into an `ItemStack` only at spawn (`Gear.toItem`), so config parsing never
needs a server. `CopNpcFactory.createCop(..., role)` spawns the tier with `role.overlay(tier)` (health, difficulty, fire
rate, strafe, and the kit's armour and weapon pool replacing the tier's slot by slot, the tier's vanilla items kept as
the no-Bartizan fallback), so `CopNpc.equip()` and the entity-replacement `refreshLoadout` path dress the cop with no
role code of their own. `applyRole` then sets the Keystone 1.14.0 hooks: `setFanPlacement`, `setLeaderPriority` and
`setRangedBand` clamped under `BartizanNpcWeapons.reach(weapon)` of the gun actually picked from the role's pool. The
band needs a resolved gun: with `NpcRangedAttack.NONE` (Bartizan missing, an unknown name, a melee tier) the cop keeps
the settings band, and a name Bartizan does not know is reported once per factory as `cops.role.weapon_unknown`.
`readGear` reports and drops a `Leather_Color` on a non-`LEATHER_*` piece. Only leather takes a dye, so the shipped
role-coloured pieces stay leather on every tier and glint (`Glow`) from SWAT up. The off-hand item (`CopRole.offHandFor(tier)`) goes on through the loadout with no drop-chance call (PLAYER entities throw
on those). `CopNpc.getRole()` exposes the role (`medic()`, `commander()`, `displayName()` for the plain word,
`display()` for the coloured symbol and word); the callsign reads the tier's display name as `%rank%` and
`role.display()` as `%role%` (`CopNames.callsign`; empty for no role), which the default `Format` shows.

The Defender's block is one guard at the top of `CopListener.onCopDamaged`, ahead of the raytrace skip: Bartizan
applies gunfire through `victim.damage(amount, shooter)`, so melee, arrows and guns all reach it. A Commander's
`LEADER_DOWN` is voiced as `Commander_Down` by the squad's new leader, and `CopGroup.setFallBackUntil` makes
`CopRetreat.takeCover` send every cop of the group to cover for `CopRadio.COMMANDER_FALL_BACK_MS`, outside its own
cover budget.

#### Field care (0.13.0)

`CopFieldCare` (one per `CopManager`, no static state) runs `tick(group)` at the end of each `aiTick`, after the cops'
own behaviours. It reads `FieldCareSettings` (`Cops.Field_Care`, gangland-api) and keeps its per-cop state weak-keyed:

- **Hurt edge:** `isHurt` (false with `Enabled: false`, so switching it off is a falling edge) flips
  `CopNpc.applySpeed(Limp_Speed | 1.0)`, which writes the Citizens navigator's default AND local parameters (Citizens
  clones the defaults into the locals on every new path). The rising edge radios `Hit` through `CopRadio.sayAs`
  (`Hit` and `Patched_Up` are `Radio.Priority` kinds, so a squad line just before cannot drop them through the squad
  or player gap; their own 5 s cooldowns still stop repeats);
  `DAMAGE_INDICATOR` particles spawn every AI tick while hurt.
- **Assignment:** a hurt PURSUING/COMBAT cop with no medic gets the nearest cop whose `CopRole.medic()` is set, that
  is fighting, not hurt and free, within `Medic_Radius`. The medic gets `CopNpc.setPatient` and radios `Medic_Moving`
  one ack delay later; `Covering_Fire`, two ack delays later, comes from the squad leader unless it is the medic or
  the patient (then any other fighting cop, else nobody). Covering fire is a radio line only. Both go through
  `CopRadio.sayAsLater` (`SquadRadio.sayLater`: past the squad gap, each step one `Ack_Delay_Ticks`), because said in
  the same tick as the patient's `Hit` the squad and player gaps would drop them. One rule for every scheduled
  follow-up (`sayLater`, which `Medic_Pinned`, the Commander's orders and the role post lines also use): it skips the
  squad gap and the player gap, and keeps only its own key's cooldown. `Hit` cools down per speaker, so every wounded
  cop reports its own hurt edge. Each is dropped when it comes due
  if the treatment is over by then (patient dead, medic out of the fight or reassigned).
- **Treatment:** while `getPatient() != null`, `CombatBehavior.holdsForFieldCare` makes the fighting behaviours return
  after their attack, so `CopFieldCare` walks the medic (`navigateTo`, a direct route to the patient, no cover search).
  A patient walking to cover (`isMovingToCover()`) is never paused: the medic follows it, waits within `Heal_Range`
  (`pauseNavigation()` on the medic only), and the channel starts once the patient is in cover or its retreat is over.
  Otherwise, within `Heal_Range` both are paused on the same tick (`pauseNavigation()` on medic and patient) and the
  patient gets `setUnderCare(true)` (SneakTrait crouch; from then on its behaviours `pauseNavigation()` and return,
  melee included). A patient under care whose retreat now sends it to cover (shot below its retreat line, or its group
  falling back) stands up and goes: `holdsForFieldCare` asks the retreat first, in PURSUING and COMBAT alike, and drops
  `underCare` instead of pausing. Once under care the patient may drift `CopFieldCare.HOLD_MARGIN` (1 block) past
  `Heal_Range` before the channel resets. The channel starts on the arrival tick and advances by `getAiTickRate()` on
  each AI tick after it, so the heal lands a full `Channel_Ticks` after the medic arrives; a medic health drop resets it
  (`Medic_Pinned`). At `Channel_Ticks` an `EntityRegainHealthEvent(CUSTOM)` is fired and its amount applied
  (`Patched_Up`); a cancelled event ends the treatment silently and keeps that patient out of assignment for
  `GIVE_UP_MS`.
- **Ending:** either cop invalid or out of the group, out of PURSUING/COMBAT, the patient no longer hurt, the medic
  hurt, or `CopFieldCare.GIVE_UP_MS` (15 s from when the medic takes the patient on, restarted on every tick the patient
  walks to cover, so the walk never costs the treatment). `CopNpc.transitionTo` also drops both care slots whenever a
  cop leaves PURSUING/COMBAT. A treatment holds no group: it runs on its medic's group's tick, and one whose medic is
  invalid or has left its patient's group (a radio responder) is ended by whichever group ticks next. `CopManager` calls
  `CopFieldCare.clear(group)` when it drops a group (`despawnAllForPlayer`, so also shutdown and reload, and the
  empty-group self-cleanup), which ends that group's treatments on both sides.

Radio cooldowns for the five keys are milliseconds in `COP_RADIO_DEFAULTS` and ticks in `cops.yml`. The medic stays in
its squad while treating (so `MAN_DOWN` still fires if it dies), and its old fan post stays reserved until the next
pursue.

A killed cop is found with `findDyingCop` (its entity is no longer valid during `EntityDeathEvent`), and
`CopListener` calls `memberDown` on its current squad and on the group squad, so the Man Down line, backup and
responders fire even for a cop killed mid-navigation (T-111).

### CopNpc

Individual cop NPC extending `AbstractNpc`. Key additions:

| Field/Method                                 | Purpose                                                     |
|----------------------------------------------|-------------------------------------------------------------|
| `CopTierConfig tierConfig`                   | Tier-specific stats (health, damage, speed, armor, weapons) |
| `Map<CopState, CopBehavior> behaviors`       | State machine behavior map                                  |
| `CopState currentState`                      | Current AI state                                            |
| `UUID targetPlayerId`                        | Current player target UUID                                  |
| `LivingEntity targetEntity`                  | Current non-player target (hostile civilian)                |
| `boolean combatForced`                       | If true, skip cuffing and go straight to combat             |
| `Deque<LivingEntity> pendingEntityAttackers` | Queue of entities that attacked this cop                    |
| `transitionTo(CopState)`                     | State machine transition with exit/enter callbacks          |
| `tick(LivingEntity)`                         | Runs one AI tick with current behavior                      |
| `attemptCuff(Player)`                        | Returns true if within cuff radius and has line of sight    |
| `CopGroup group`                             | Group the cop was assigned to; its squad is the group's     |
| `squadFor(LivingEntity)`                     | The group squad for the group's target, else the cop's own squad for that target |
| `leaveSquad()`                               | Leaves the group squad (called when returning)              |

### CopTierConfig

Record holding per-tier configuration:

```java
record CopTierConfig(
		int tier,                    // Tier number (1-5)
		String displayName,          // e.g. "Officer", "SWAT"
		double health,               // Max health in half-hearts
		double damage,               // Base melee damage
		double speed,                // Movement speed
		double cuffRadius,           // Blocks within which cuffing is attempted
		boolean canUseWeapons,       // Whether this tier uses the weapon pipeline
		boolean skipCuffing,         // Whether this tier skips cuffing (goes straight to combat)
		List<String> weaponNamePool, // Gangland weapon names for random selection
		List<ItemStack> weaponPool,  // Vanilla weapon fallbacks
		ItemStack helmet, chestplate, leggings, boots, // Armor
		NpcDifficulty difficulty,    // Aim error, reaction time, fire rate, melee hit chance
		TacticsConfig tactics,       // Cops.Tactics overridden by Tiers.<n>.Tactics (0.12.0)
		double fireRateMultiplier    // Fire_Rate_Multiplier, default 1 / AI_Tick_Rate (0.12.0)
)
```

Higher tiers (e.g., SWAT, Military) have `skipCuffing = true` -- they engage in combat immediately.

### Cop AI State Machine

```
                                    ╭───────────────────────────╮
                                    │          IDLE             │
                                    │ Scans for targets within  │
                                    │ alertRange. Stands still. │
                                    ╰───────────────────────────╯
                                                │
                                    target detected within alertRange
                                    and has line of sight
                                                │
                                                v
╭───────────────────╮              ╭───────────────────────────╮
│    RETURNING      │<─────────────│        PURSUING           │
│ Navigates to      │  target lost │ Navigates toward target.  │
│ nearest station.  │  or detained │ Ranged cops shoot while   │
│ Waits for no      │              │ closing distance.         │
│ observers, then   │              ╰───────────────────────────╯
│ despawns.         │                   │                │
│ Re-engages only a │      within cuffRadius       combatForced
│ freed prisoner.   │      + has LOS               OR skipCuffing
╰───────────────────╯           │                        │
       ^                        v                        v
       │               ╭──────────────────╮    ╭──────────────────╮
       │               │     CUFFING      │    │     COMBAT       │
       ├<──────────────│ Wind-up timer,   │    │ Attacks target   │
       │  cuff success │ then attemptCuff.│    │ within range.    │
       │               │ One cop per      │    │ Ranged cops hold │
       ╰<──────────────│ target via       │    │ firing position. │
          target out   │ CuffLockRegistry.│    │ Melee cops close │
          of range     ╰──────────────────╯    │ distance.        │
                              │                ╰──────────────────╯
                              │                        │
                         target escapes          target detained
                         ─> PURSUING             or killed
                                                       │
                                                       v
                                               ╭──────────────────╮
                                               │    RETURNING     │
                                               ╰──────────────────╯
```

#### State Details

**IDLE** (`IdleBehavior`)

- Stands at spawn position with navigation stopped
- Each tick: `cop.canSee(target, alertRange)` reports a sighting to the cop's squad
- Transition: `IDLE -> PURSUING` when the cop sees the target or its squad has a fresh sighting

**PURSUING** (`PursuingBehavior`)

- Navigates with `cop.pursue(target, cop.squadFor(target), alertRange)` — Keystone's squad pursuit (chase, route,
  search, wait below an unreachable target); a failed path never sends the cop back
- Ranged cops fire while closing distance (`cop.attack()` called when LOS + canAttack)
- Transition conditions when within `cuffRadius` + has LOS:
    - `skipCuffing` or `combatForced` -> `COMBAT`
    - Otherwise -> `CUFFING`
- Transition: `PURSUING -> RETURNING` when target goes offline/dies/is detained
- Rotation: `pursuitTicks` counts only AI ticks where `isNavigationStuck()` and nobody in the squad has a fresh
  sighting (an entity target: the cop itself cannot see it), and resets otherwise; reaching `Pursuit.Max_Ticks`, or
  a target farther than `Pursuit.Max_Distance`, sends the cop to `RETURNING` for good and the spawner replaces it

**CUFFING** (`CuffingBehavior`)

- Only one cop per target at a time, enforced by `CuffLockRegistry`
- On enter: attempts to acquire cuff lock; if another cop holds it -> `PURSUING`
- Wind-up timer counts down `cuffingCooldown` AI ticks, firing `DuringCuffingEvent` each tick
- On wind-up complete: calls `cop.attemptCuff(player)`
    - Success -> fires `CuffedEvent`, resets the group's escape count, transitions to `RETURNING`
    - Failure, or the target breaks out of the zone -> `failCuff`: counted per group; below `Max_Cuff_Attempts` the cop
      goes back to `PURSUING`, at the limit the group escalates and the cop goes to `COMBAT` (see Resisting above)
- The other cops of the group hold their surround posts in `PURSUING` while the lock is held by someone else
- Entity targets (hostile civilians) bypass cuffing entirely -> `COMBAT`
- On exit: always releases the cuff lock

**COMBAT** (`CombatBehavior`)

- Attacks a target within `alertRange` (a ranged cop, `isRangedAttacker()`) or `combatRange` (melee; the swing lands
  only within `Cops.Melee.Reach`)
- A badly hurt cop retreats to cover first (see Retreat to cover above)
- Navigates with `cop.pursue(target, cop.squadFor(target), alertRange)`: ranged cops work their formation post and
  strafe while they see the target inside their firing band, melee cops surround, everyone else closes in (any other
  target uses the group's attacker squad for it)
- Transition: `COMBAT -> RETURNING` when target goes offline/dies/is detained

**RETURNING** (`ReturningBehavior`)

- On enter: leaves the squad (frees its slot and any route plan it owns)
- Finds nearest registered spawner location (or falls back to NPC's spawn location)
- Navigates to station; considers itself arrived at `stationArrivalDistance`
- Despawns (`markForRemoval()`) on arrival or after `maxReturnTicks`
- Re-engagement: only a cop that entered `RETURNING` because its target was restrained or jailed re-engages when the
  target is freed before it reaches the station (`COMBAT` if combatForced, otherwise `PURSUING`). A cop rotated out
  of a pursuit never re-engages — that bounce froze cops below unreachable targets before 0.11.0 (D1). A new wanted
  start for the same player (`onWantedStart`) sends every returning cop of the group back to `PURSUING`.

### CuffLockRegistry

Global mutex ensuring only one cop across ALL groups can cuff a given target at any time:

```java
boolean tryAcquire(UUID targetId, UUID copId)  // Atomic putIfAbsent

boolean isOwner(UUID targetId, UUID copId)      // Check ownership

void release(UUID targetId, UUID copId)      // Release only if owner

void forceRelease(UUID targetId)             // Admin force-release

void releaseByCop(UUID copId)                // Release all held by cop (on cop death)
```

### CopSpawnManager

Extends `EntitySpawner<CopSpawner>`. Spawn location strategy:

```
spawnNearPlayer(target, tier, allowed, role):   -- role: CopRole.nextRole in CopManager.spawnTick (null = plain tier)
  1. findClosestSpawnerLocation(target, allowed) -- closest registered spawner within preference radius and
     Spawner_Max_Y_Diff that `allowed` accepts (CopManager passes loc -> !group.isAvoided(loc, now): the spawner
     of a recycled cop is skipped for Cops.Stuck.Avoid_Spawner_Seconds)
     Success -> copNpcFactory.createCop(location, tier, false, role)

  2. findRingLocation(target) -- findSpawnLocation, a two-phase random search
     Phase 1: preferred ring behind player (p1MinDistance to maxDistance)
     Phase 2: shrinking radius fallback (maxDistance down to minDistance)
     Each spot is within Spawn.Max_Y_Diff of the player and as indoor/outdoor as he is. A player under a roof
     with only open street around him gets a second search that takes either kind (isOutdoor overridden to true
     while it runs), still within Max_Y_Diff.
     Success -> copNpcFactory.createCop(location, tier, validateAfterSpawn=true, role)

  3. Return null -- no valid location this interval
```

Recycling (Cops.Stuck): each spawn run, a PURSUING/COMBAT cop hunting the group's player whose
millisUnreachable() >= Recycle_Seconds is released unless the player sees it (60-degree cone plus clear line
within max(24, Visibility_Check_Distance); past 2x Recycle_Seconds within 24 only), it is within melee reach on his
level (|dy| <= 1.5, clear line), or another player faces it (past 2x Recycle_Seconds: another real player with it in
his cone, clear line, within 24). A recycle is no news of the player (0.21.1, R4): no tip and no tip-off, so the
replacements inherit the squad's own last-known spot, its search circle keeps widening and the evasion clock keeps
running (T-147). The same run refills the group, capped at Max_Per_Player.

Tier selection: `tier = min(wantedLevel, maxTier)`

Cop count: configurable per wanted level via `getCopsPerWantedLevel()` map, capped at `maxCopsPerPlayer`.


---

## Civilian NPC System

### Package Structure

```
npc/civilian/
  CivilianService       Lifecycle manager -- spawning, AI ticking, proximity spawners
  CivilianGroup         Spawn-group with stay-together behavior
  CivilianState         Enum: IDLE, WANDERING, FLEEING, COMBAT
  config/
    CiviliansConfig       Top-level config (types + groups)
    CiviliansLoader       YAML loader for civilians.yml
    YamlCiviliansConfigProvider   YAML-backed provider
    CivilianTypeConfig       Per-type record (full NPC config)
    CivilianGroupConfig      Group definition (member types + counts)
    CivilianAIBehaviorConfig Per-type AI behavior settings
    CivilianNavigationConfig Navigation settings (implements NpcNavigationConfig)
    CivilianWearableConfig   Armor slot strings
    CivilianDropConfig       Death drop configuration
    CivilianSettings         Settings.yml civilian section contract
  npc/
    CivilianNpc              Individual civilian NPC instance
    CivilianNpcFactory       Creates and equips CivilianNpc instances
  spawn/
    CivilianSpawnManager     Extends EntitySpawner -- proximity + manual spawning
    CivilianSpawner          EntitySpawnerPoint for civilians
  state/
    CivilianBehavior         Interface extending NpcBehavior<CivilianNpc>
    CivilianBehaviorFactory  Creates the state-to-behavior map
    behavior/
      CivilianIdleBehavior
      CivilianWanderBehavior
      CivilianFleeBehavior
      CivilianCombatBehavior
      CivilianLookController   Shared ambient look-around logic
```

### CivilianService

Central manager for all civilian NPCs. Initialized once:

```java
CivilianService civilianService = new CivilianService();
civilianService.

initialize(plugin, markerConfig, entityMarkManager,
           spawnerRepository, civilianSettings, spawnConfigProvider,
           itemParser, weaponService);
```

Two scheduled tasks run after initialization:

1. **AI tick timer** (`civilianAiTickRate`) -- calls `tickAll()` which iterates all active NPCs, calling `npc.tick()`
   and removing dead/marked NPCs
2. **Proximity spawner timer** (`civilianSpawnerCheckInterval`) -- checks registered spawner locations against
   online player positions:
    - Player within activation radius -> spawn NPCs (individual or group)
    - All players outside despawn radius -> mark spawner's NPCs for removal

Key methods:

| Method                          | Purpose                                           |
|---------------------------------|---------------------------------------------------|
| `register(CivilianNpc)`         | Registers an already-spawned NPC for AI ticking   |
| `registerGroup(CivilianGroup)`  | Registers a civilian group                        |
| `getNpc(UUID)`                  | Looks up NPC by entity UUID                       |
| `getActiveNpcs()`               | Returns all active civilian NPCs                  |
| `spawnGroup(Location, groupId)` | Spawns a complete group from civilians.yml config |
| `shutdown()`                    | Destroys all NPCs and clears registries           |
| `alertFaction(npc, attacker, playerAttacker)` | Squads a hit civilian and alerts its faction (one hop) |
| `dropSquads(UUID)`              | Forgets every squad hunting a dead or downed target |

### CivilianNpc

Individual civilian NPC extending `AbstractNpc`. Key additions:

| Field/Method                            | Purpose                                                |
|-----------------------------------------|--------------------------------------------------------|
| `CivilianTypeConfig typeConfig`         | Full type configuration                                |
| `String groupId`                        | Group key (nullable)                                   |
| `CivilianGroup group`                   | Parent group reference (nullable)                      |
| `Integer spawnerId`                     | Spawner that created this NPC (nullable)               |
| `CivilianState currentState`            | Current AI state                                       |
| `UUID targetPlayerId`                   | Player combat target                                   |
| `Deque<LivingEntity> entityTargetQueue` | NPC-to-NPC combat targets (priority queue)             |
| `boolean wantedByPolice`                | True when hostile civilian enters COMBAT (cops pursue) |
| `Location lastAttackerLocation`         | Used by flee behavior                                  |
| `transitionTo(CivilianState)`           | State transition with exit/enter callbacks             |
| `tick()`                                | Runs one AI tick                                       |
| `isHostile()`                           | Whether this NPC type is configured as hostile         |
| `addEntityTargetToFront(LivingEntity)`  | Bumps attacker to front of entity target queue         |
| `NpcSquad squad` / `UUID squadTargetId` | Combat squad and the target it hunts (nullable)        |
| `joinSquad(NpcSquad, UUID)` / `leaveSquad()` | Squad membership (COMBAT leaves on exit)          |

The `wantedByPolice` flag is automatically set when a hostile civilian transitions to `COMBAT` and cleared when
leaving `COMBAT`. Cops use `CivilianNpc.isWantedByPolice()` to identify civilians to pursue.

### CivilianTypeConfig

Record holding per-type configuration loaded from `civilians.yml`:

```java
record CivilianTypeConfig(
		String typeId,                         // e.g. "pedestrian", "gang_member"
		String displayName,                    // Color-coded name
		EntityType entityType,                 // Citizens body type
		double health,                         // Max health
		boolean hostile,                       // Whether NPC attacks players
		CivilianWearableConfig wearables,      // Armor (raw ItemParser strings)
		List<String> itemPool,                 // Random items given on spawn
		List<String> weaponNamePool,           // Gangland weapon names (hostile types)
		List<ItemStack> weaponPool,            // Vanilla weapon fallback
		CivilianDropConfig drops,              // Death drop config
		CivilianAIBehaviorConfig ai,           // Per-type AI settings
		String faction                         // Side it fights for (default: the type id)
)
```

### CivilianAIBehaviorConfig

Per-type AI behavior settings:

```java
record CivilianAIBehaviorConfig(
		boolean wanderEnabled,       // Whether NPC wanders when idle
		int wanderRange,         // Blocks radius for wander targets
		boolean fleeEnabled,         // Whether NPC flees when damaged
		int fleeRange,           // How far (blocks) the NPC runs
		boolean combatEnabled,       // Whether NPC engages in combat
		double attackDamage,        // Base damage per attack
		double attackRange,         // Detection/attack range (blocks)
		int attackIntervalTicks, // Server ticks between attacks
		NpcDifficulty difficulty,    // Combat profile
		double alertRange,          // Sight range + faction hearing radius (default 16.0)
		int searchSeconds        // Squad unseen this long -> give up (default 20)
)
```

### Civilian AI State Machine

```
                    ╭────────────────────────╮
                    │         IDLE           │
                    │ Stands still, looks    │
                    │ around at nearby       │
                    │ entities. Re-engages   │
                    │ remembered targets     │
                    │ within 2x attackRange. │
                    ╰────────────────────────╯
                       │               │
           70% chance after        remembered target
           idle countdown          returns in range
                       │               │
                       v               v
           ╭─────────────────╮   ╭─────────────────╮
           │   WANDERING     │   │     COMBAT      │
           │ Random forward- │   │ Pursues and     │
           │ biased movement.│   │ attacks target  │
           │ Group members   │   │ (player or NPC).│
           │ return to group │   │ Entity targets  │
           │ center if       │   │ take priority   │
           │ straying.       │   │ (self-defense). │
           ╰─────────────────╯   │ Gives up after  │
                  │              │ Search_Seconds. │
           arrival or            ╰─────────────────╯
           stuck x3                    │
                  │              target lost or
                  v              out of range
                IDLE                   │
                                       v
                                     IDLE

           (from any state when damaged and flee enabled)
                       │
                       v
           ╭─────────────────╮
           │    FLEEING      │
           │ Runs away from  │
           │ attacker until  │
           │ fleeRange       │
           │ exceeded.       │
           ╰─────────────────╯
                  │
           arrived or
           navigation hopeless
                  │
                  v
                IDLE
```

#### State Details

**IDLE** (`CivilianIdleBehavior`)

- Random idle countdown: 40-100 AI ticks
- Ambient look-around every 15-35 ticks at entities within 12 blocks
- Re-engage check: if a remembered player or entity target returns within `2 * attackRange`, transition to `COMBAT`
- When countdown expires and `wanderEnabled`: 70% chance -> `WANDERING`, 30% chance -> reset idle timer
- When `wanderEnabled` is false: always resets idle timer

**WANDERING** (`CivilianWanderBehavior`)

- Navigates to random forward-biased destinations using `findForwardWanderDestination(min, max)`
- Periodically redirects mid-path (every 60-120 ticks) to appear natural
- Looks at nearby entities while walking (every 10-20 ticks)
- Group coherence: if member has strayed beyond `stayTogetherRange`, navigates to group center instead
- Max 3 stuck reverts before giving up -> `IDLE`
- Reverts to `IDLE` on arrival or stuck

**FLEEING** (`CivilianFleeBehavior`)

- Triggered externally by listeners when a non-hostile NPC takes damage
- Calculates direction vector away from `lastAttackerLocation`
- Navigates to a point `fleeRange` blocks away in the opposite direction
- Checks arrival every 20 ticks: if no longer navigating or navigation hopeless -> `IDLE`
- Also reverts when distance from flee origin exceeds `fleeRange`
- On exit: clears `lastAttackerLocation`

**COMBAT** (`CivilianCombatBehavior`)

- Only available for hostile civilian types
- Target resolution: entity target queue (self-defense priority) > player target (fallback)
- Navigates with `npc.pursue(target, squad, ai.alertRange())` — Keystone's squad pursuit; ranged NPCs hold while
  they see the target inside their firing band
- Squad: the `(faction, target)` squad `CivilianService.alertFaction` put it in; a civilian that entered combat another
  way (turf defender retarget, idle re-engage) or whose target switched hunts in a squad of its own, seeded with the
  target's current position
- Attacks when within `attackRange`, with line of sight, and cooldown elapsed
- Gives up (clears its target, `IDLE`) when nobody in its squad has seen the target for `AI.Combat.Search_Seconds`
- On exit: leaves its squad; targets are otherwise preserved so `IDLE` can re-engage if the target returns

### CivilianGroup

Manages a spawn-group of civilians that stay together:

```java
// Spawned from civilians.yml group definitions
CivilianGroup group = civilianService.spawnGroup(location, "street_gang");

// Group tracks:
group.

getGroupCenter()          // Average location of alive members
group.

isMemberStraying(npc)     // Distance to center > stayTogetherRange
group.

pruneDeadMembers()        // Remove invalid members
group.

isEmpty()                 // All members dead
```

Group coherence is enforced during `WANDERING`: if a member's distance from the group center exceeds
`stayTogetherRange`, it navigates back to the center instead of picking a random destination.


---

## Entity Spawning Framework

### EntitySpawner<S>

**Package:** `org.luckyraven.gangland.copsncrooks.entity`

Abstract base providing the shared spawn-location algorithm and persistent spawner registry.
Extended by `CopSpawnManager` and `CivilianSpawnManager`.

#### Spawner Registry

Spawner points are persisted via `IRepository<S>`:

```java
setSpawnerLocation(Location)    // Register new spawner point (auto-incrementing ID)

removeSpawner(int id)           // Delete spawner

getSpawnerLocations()           // List all registered locations

getSpawnerIds()                 // List all IDs

reloadSpawners()                // Reload from database
```

#### Two-Phase Spawn Location Algorithm

```
findSpawnLocation(player):

  PHASE 1 ─ Preferred Ring (behind player)
  ╭───────────────────────────────────────────────────╮
  │ For each of spawnPhase1Attempts attempts:         │
  │   Random angle, random distance [p1Min, maxDist]  │
  │   Check chunk loaded                              │
  │   findGroundNearY (±verticalSearchRange)          │
  │   Require: behind player (>90 deg from facing)    │
  │   Require: indoor/outdoor matches player          │
  ╰───────────────────────────────────────────────────╯

  PHASE 2 ─ Shrinking Ring (any direction)
  ╭───────────────────────────────────────────────────╮
  │ For max = maxDist down to minDist (step shrink):  │
  │   For each of spawnPhase2Attempts attempts:       │
  │     Random angle, random distance [min, max]      │
  │     Same ground/chunk/indoor validation           │
  │     No behind─player requirement                  │
  ╰───────────────────────────────────────────────────╯
```

Ground validation (`isValidGround`):

- Ground block must be solid
- Feet, head, and above-head blocks must be empty (passable)
- At least `minOpenHorizontalSides` open sides at feet and head level

Additional checks:

- `isVisibleToOtherPlayers(location, exclude)` -- 60-degree cone visibility check used during cop despawn
- `findClosestSpawnerLocation(player)` -- prefers registered spawner within `spawnerPreferenceRadius`

### EntitySpawnerPoint

Base class for spawner point data:

```java
int getId()

Location getLocation()

void setLocation(Location)
```

Concrete types: `CopSpawner`, `CivilianSpawner` (adds `typeId` and `groupId` for type-specific spawning).

### EntityMark

Enum classifying NPC entities:

```java
enum EntityMark {
	CIVILIAN,    // Civilian NPC
	POLICE,      // Cop NPC
	UNSET;       // Unknown / not an NPC

	boolean isCivilian()       // true for CIVILIAN and POLICE

	boolean countForWanted()   // true for CIVILIAN and POLICE
}
```

`EntityMarkManager` maintains a mapping from entity UUID to `EntityMark`, used by listeners and the wanted system
to determine if a killed entity should increment the player's wanted level.


---

## Wanted System

**Package:** `org.luckyraven.gangland.copsncrooks.wanted`

### Wanted

Core data class tracking a player's wanted status:

```java
// Fields:
int level         // Current wanted level (0 = not wanted, capped at maxLevel)
int maxLevel      // Maximum wanted level (configurable)
int increments    // How many levels to add per incrementLevel() call
boolean wanted        // Derived: level > 0
Player owner         // The owning player

// Key methods:
void setLevel(int level)     // Clamps [0, maxLevel], fires events; cause UNKNOWN (legacy form)

void setLevel(int level, WantedCause cause)   // 0.15.0: the one choke point; the cause rides on all three events

void incrementLevel()        // setLevel(level + increments); also incrementLevel(cause)

void decrementLevel()        // setLevel(level - 1); also decrementLevel(cause)

String getLevelStars()         // e.g. "★★★☆☆" for 3/5

void reset()                 // Sets level to 0 and stops timer; also reset(cause)
```

`WantedCause` (`org.luckyraven.gangland.core.wanted`, since 0.15.0): `CRIME`, `SIGN`, `ADMIN`, `RESTORE`, `DECAY`,
`EVASION`, `BRIBE`, `ARREST`, `DEATH`, `UNKNOWN`. Every star change outside core names one. `WantedLevelChangeEvent`,
`WantedStartEvent` and `WantedEndEvent` expose it as `getCause()`; the change event still fires before the level
mutates and is cancellable.

When `setLevel()` changes the level:

1. Fires `WantedLevelChangeEvent` (cancellable)
2. If level went from 0 -> positive: fires `WantedStartEvent`
3. If level went from positive -> 0: fires `WantedEndEvent`

Events are always fired synchronously on the main thread. If called from an async thread, the level change is
deferred via `Bukkit.getScheduler().runTask()`.

### WantedStars

`org.luckyraven.gangland.core.wanted.WantedStars` (core bean, 0.15.0) is the one place stars are raised, dropped and
restored, so the decay clock, the star-drop charge and the messages cannot drift apart. `WantedContext` is the
user-and-wanted pair the callers already hold.

```java
int raise(WantedContext context, int stars, WantedCause origin)   // main thread; stars added (0 at max or cancelled)
int drop(WantedContext context, int stars, WantedCause cause)     // any thread (hops); stars actually dropped
void restore(WantedContext context, int level)                    // login: sets the saved level, cause RESTORE
Timer startDecayClock(WantedContext context)                      // the sync WantedExecutor timer
void installDecayPolicy(WantedDecayPolicy policy)                 // holder seam, installed by cops-n-crooks
void suppressStarChat(BooleanSupplier whileTrue)                  // the HUD hides the plain chat line
```

`drop` order: pick the target level, `setLevel(target, cause)` (a cancelled change ends the call, nothing is charged),
withdraw the summed star price once, send the decreased message for the new level, stop the timer at 0. The price of one
star is `MoneyFormula.evaluate(Take_Money.Formula, vars, amount * multiplier ^ wanted)` and is charged only when
`Wanted.Take_Money.Enable` is true.

### WantedDecayPolicy

A functional interface in core: `boolean handlesDecay(Player owner, Wanted wanted)`. `WantedExecutor`'s tick asks
`stars.isDecayHandled(wanted)`; when an installed policy answers true the tick drops nothing (the safety-net timer keeps
running). cops-n-crooks installs `EvasionClock` from `EvasionModuleConfig`'s `@PostConstruct`.

### MoneyFormula

`org.luckyraven.gangland.core.money.MoneyFormula.evaluate(formula, variables, fallback)` never throws: a syntax error,
unknown variable, division by zero, NaN, infinity or negative result logs one warning per distinct formula and returns
the fallback, clamped to >= 0. `userVariables(user)` builds `balance`, `level`, `experience`, `bounty`, `wanted`. Used
by `WantedStars` and `PlayerDeathListener`.

### WantedExecutor

Drives the periodic wanted-level decrease timer (the safety net since 0.15.0). Configuration comes via `WantedSettings`:

```
Timer interval = timerTime * (timerMultiplierAmount ^ currentLevel)
```

Each timer tick:

1. If an installed `WantedDecayPolicy` handles decay for this player, do nothing
2. Fire `WantedEvent` (cancellable)
3. `stars.drop(context, 1, DECAY)` (charges the price when enabled, sends the message, fires the events)
4. Stop timer when level reaches 0

### WantedSettings Contract

```java
// Timer configuration:
double getTimerTime()                // Base interval in seconds

boolean isTimerMultiplierEnabled()   // Whether to scale interval by level

double getTimerMultiplierAmount()    // Multiplier base (e.g. 1.5)

boolean isTimerEnabled()             // 0.15.0, default true; Settings.isWantedTimerEnabled()

// Money loss (0.15.0: charged only when enabled):
boolean isTakeMoneyEnabled()         // Wanted.Take_Money.Enable, default false

String getTakeMoneyFormula()         // Wanted.Take_Money.Formula

BigDecimal getTakeMoneyAmount()      // Numbers fed to the formula

double getTakeMoneyMultiplier()
```

### The chase (cops-n-crooks, 0.15.0)

Package `org.luckyraven.gangland.copsncrooks.wanted`, configured by `ChaseConfig` (`copsncrooks/wanted.yml`, loaded by
`ChaseConfigLoader`) and `WantedMessages` (`copsncrooks/wanted_messages.yml`); the beans live in `ChaseModuleConfig`,
`HeatModuleConfig` and `EvasionModuleConfig`.

| Piece | Class | Role |
|---|---|---|
| Crime bus | `CrimeService.commit(player, crimeId, location[, seenByCop, witnesses])` in `gangland-api` | Main thread; fires `CrimeCommittedEvent`; false when a listener cancelled it |
| Heat | `wanted.heat.HeatLedger`, `CrimeRecord`, `ReportKind`; `HeatListener` | Records crimes with the multipliers (0.21.2: the witness factor is `Seen_By_Cop_Multiplier` for a cop eyewitness, else 1.0, plus `Per_Extra_Witness` per witness past the first up to `Max_Extra_Witnesses`; the squad's fresh sighting stands in for a cop only on a crime outside the enabled `Witnesses.Crimes`, T-538), keeps the per-chase list the charge sheet reads, and replays the core star trigger while heat is above the next threshold. Cleared on `WantedEndEvent` |
| Kill seam | `seam.HeatWantedTracker` (replaces `KillComboWantedTracker`) | Installed into core's `WantedKillTrackers` holder. Heat on: kills become `Kill_Player` crimes (`Kill_Civilian` is published by gangland-civilians at the victim, 0.21.2 T-550; `Kill_Cop` since 0.21.2 by `CopListener.onCopDeath` through `HeatLedger.reportCopKill` at the body, before `memberDown`, with the killer from `getKiller()` or the player who hit that cop within 5 s, T-544; Assault_Cop is reported one tick after the hit and skipped when the cop is dead by then, T-535/T-536); heat off: the kill combo or one star per kill. A defender killing inside their own contested turf is dropped first, and `exemptsKill` tells the core to add no kill notoriety or bounty timer either |
| Evasion | `wanted.evasion.EvasionClock` (a `WantedDecayPolicy`), `EvasionListener` | Runs on the cop AI tick; fires `WantedEvasionStateEvent`; drops through `WantedStars.drop(.., EVASION)` |
| HUD | `wanted.hud.WantedHud`, `StarCard`, `listener.wanted.WantedHudListener` | Boss bar, card, title, siren, ring and compass; a 10-tick timer; reads the events only |
| Charge sheet | `detainment.intake.JailIntakeService` | Reads `ledger.chaseCrimes` before the ARREST clear |
| Witnesses (0.20.0) | `listener.wanted.WitnessGateListener`, `NineOneOneListener`, (0.21.2) `wanted.report.CopCrimeReports`, `listener.wanted.CrimeReportListener` | The gate cancels a crime in `Wanted.Witnesses.Crimes` (0.21.1: `Kill_Civilian`; 0.21.2: `Assault_Cop`, `Kill_Cop`) that carries no seen-by-cop flag and no witness count, and hands it to `CopCrimeReports.open(player, crime, at, HeatLedger.currentVictim())` (0.21.2 removed the squad-sighting and cop-in-view bypasses, T-541). `open` collects the cop eyewitnesses (`liveCops()` + `beatCops()`; `CopPerception.sees` within `Alert_Range` or `canSee` within `Witnesses.Cops.Witness_Floor`; engaged, loud via `WitnessService.loudWithin`/`Loud_Crimes`, or inside `WitnessService.inView` at `View_Angle`; not the dead cop of a Kill_Cop) and opens a glowing-reporter case that lands after `Report_Seconds` on the bean's 10-tick main-thread `RepeatingTimer` as `commitReported(COP, commit(.., true, liveWitnesses))`, `reportTip(at)` and `CopRadio.dispatchAt(.., "Officer_Report")`; a Kill_Cop supersedes the victim's pending Assault_Cop and keeps its deadline; death/cuff/arrest (`CrimeReportListener`) and a restraint at landing drop it; a quit defers it, landing one tick after `UserDataInitEvent`. With no cop eyewitness `open` asks the civilians `WitnessService` (a call cut short by the criminal's logout fires a `deferred` `CivilianCalledInEvent`, which `NineOneOneListener` hands to `CopCrimeReports.defer`, T-551); a landed call goes to `CopCrimeReports.landCivilian`, the same landing as a cop report (0.21.2 review r2: dropped when restrained, a squad's live sighting kept): it recommits with its viewers as the witness count (0.21.2 T-540: `CivilianCalledInEvent.getViewers()`, the caller plus the watchers still valid at landing; 1 before 0.21.2), tips the squad to the spot and says `Caller_911` through `CopRadio.dispatchAt` about the crime spot (0.21.1, no speaker needed: T-345). A Kill_Cop no cop saw, of a cop whose Assault_Cop another living cop saw, never asks the civilians: the assault report stands (`REPORT stand ... reason=unseen_kill`). The recommit runs inside `HeatLedger.commitReported(player, ReportKind, commit)`, so only that `CrimeRecord` carries a `kind` (COP, CIVILIAN, DISPATCH, SCENE; a safe alarm, a hold-up or a crew mate's share has none) and its `witnesses`; `WantedHudListener` names the reporter on the chat card only (`Hud.Card.Seen`/`Reported`/`Missing`/`Scene` with `%count%`; `%cause%` stays plain, R1). One commit whose heat crosses two thresholds replays the star trigger once per star; `HeatLedger.raisingTo(id)` names the final level during that replay (0 otherwise), so `WantedHudListener` skips the intermediate raises (one subtitle, card and siren, at the final level; `onStart` shows the final level) and `CopManager.onWantedStart` (`setRaisingTo`, wired in `HeatModuleConfig`) says `Dispatch_Wanted` and sizes the episode at it (0.21.2 R2, T-583) |
| Silent officers (0.21.2 S5) | `wanted.report.CopCrimeReports` (`silentPath`, `silentRoles`, `silentStation`), `npc.police.config.CheckInSettings` (`Cops.Check_In`), `CopNpc.stampReport`, `CopGroup.spendBiasSeed`, `PatrolManager.respondTo`/`setStationHold`, `CopManager.setSilentRoles`, `CopDeathEvent.isSilent` | The 10-tick task stamps every live squad and beat cop's `lastReport`/`lastReportAt` (every run while `getCurrentSquad().isInContact()`, else every `Check_In_Seconds`). `CopListener.onCopDeath` books Kill_Cop first (the gate opens a cop report if a cop saw the killer), then asks `silentPath`: with `Check_In.Enable`, Kill_Cop gated and heat on, no open case (Kill_Cop, or the standing Assault_Cop) for that victim and no cop who `sees` the body (cone or loud) it records a `Silence` (radio name, group, `stationOf`, last check-in, hunting, and `heldSlot`: the spawn task's count at the death, so not `group.isSentHome(cop)` nor `copManager.isStranded(cop, target)`, R2 T-580) and returns true; the listener then `remove`s the cop from his squads instead of `memberDown`, keeps `setBackupHeld`, and fires `CopDeathEvent(silent=true)` (ignored by `StandoffListener`/`PursuitReportListener`). A body seen opens a SCENE and the old radio runs. `silentRoles(group)` (by group identity, `heldSlot` silences only; a `null` role still counts) joins `liveRoles` in the spawn task (dispatch-off loop, `lacking` and the sent-home re-engage limit subtract it; a held Commander role counts as leading in `reengageReturning`, so no sent-home Commander is called back for him, R2 T-581; `currentCount`, `releaseSurplus` and the episode trim count real cops only) and holds `EvasionClock` (`hold=silence`); `silentStation` holds `PatrolManager.manage`'s replacement. At `lastReportAt + Check_In_Seconds` `Radio_Check`, at `+ Silent_Seconds` `Officer_Missing` (`CopRadio.dispatchAt`, a throwaway `CopGroup` when the killer has none); `discover` fires the non-silent event, then the same group (`groupOf(target) == group`) records the casualty and requests backup, and when it hunted the killer releases the hold, sets a `SpawnBias` toward the last check-in, `spendBiasSeed()`, `reportTip` (no fresh sighting) and `markTipOff`; a beat cop's station `respondTo(id, spot, Scene_Seconds ms)`: the nearest beat mate gets `setInvestigateUntil(now + Scene_Seconds)` and `navigateTo(spot)` now, and the held replacement is owed (`owed` map, never past that deadline): `manage` spawns it once `lostAt + Respawn_Seconds` passed, at the anchor when `CopSpawnManager.watched(anchor)` is false (R6, R2 T-587; `lostAt` is no longer cleared), else at `CopSpawnManager.unwatchedNear(anchor)` (R3 T-606: `hiddenRing` round the nearest player, spots within `max(24, Visibility_Check_Distance)` of the anchor and not `watched`; `null` = `PATROL station=<name> held=watched`, try next run) with `CopNpc.setBeatAnchor(anchor)` (`PatrolBehavior.walk` beats round `getBeatAnchor()` over `getSpawnLocation()`) and `navigateTo(anchor)` unless owed, then gives it the same hold and walk. `PatrolBehavior.walk` returns while `now < getInvestigateUntil()` (no wander, no walk back, T-579). Pursuit_Presumed (the group re-resolved at that moment) lands `DISPATCH (false, 1)`, else a SCENE: `eyes` on the killer within `Scene_Radius` lands `SCENE (true, n)` plus a sighting and `Scene_Suspect`; the first cop within 6 blocks says `Scene_Found`; a cop whose `CopNpc.getSpawnedAt()` (wall clock, stamped in the constructor; 0 = unknown, no grace) is under `Check_In.Spawn_Grace_Seconds` old is dropped from both (`inGrace`, R3 T-601, `SCENE grace <player> at=x,y,z cop=<name>`); `Scene_Seconds` = `Scene_Clear`. `landCivilian(Kill_Cop)` closes the silence (`discover` at the call's spot), else the scene, whose body (`diedAt`) lay within 1 block of the call's crime spot; a DISPATCH or SCENE commit records the kill as `Counted` (5 min), and a later call (`landCivilian` or `defer`) about it logs `REPORT drop ... reason=counted` instead of committing. `drop`/`onQuit` and an offline killer forget them; `onShutdown` clears them. Debug: `CHECKIN silent|radio_check|missing|close|drop <unit> last=x,y,z age=<s>`, `SCENE open|found|seen|clear|close <player> at=x,y,z` |
| Regroup | `CopGroup` casualty state + `CopRadio` | See the squad sections above |
| Shot noise | `listener.police.ShotNoiseListener` | Bartizan `WeaponShootEvent` at MONITOR |

---

### Where the police come from (cops-n-crooks, 0.16.0)

New in 0.16.0 (all in `org.luckyraven.gangland.copsncrooks` unless noted). Player-facing rules are in
[`features/cops-n-crooks.md`](../features/cops-n-crooks.md); every key is in [`configuration.md`](./configuration.md).

| Piece | Classes | Role |
|---|---|---|
| Registries | `station.Station`/`StationRegistry`, `place.AdminRegion`/`AdminRegionRegistry`, `place.SetupPoint`/`SetupPointRegistry`; tables `cop_station`, `cop_region`, `cop_point` | The only writer is the setup wand. Beans in `config.RegistryModuleConfig` (registered last in `CopsNCrooksModule`). `AdminRegionRegistry` is a `RegionProvider` (source `copsncrooks`) and registers itself with `PlaceNames` |
| Spawner grouping | `CopSpawner.stationId` (`cop_spawner.station_id`, appended last), `CopSpawnManager.assignStation/assignNearby/unassignStation/spawnersOf` | A station owns the spawners within `Station_Radius`; the positional `doLoadAll` read takes the new column only when the row has it |
| Dispatch | `npc.police.dispatch.Dispatcher`, `PendingUnit`, `SpawnBias`; `CopGroup` queue (`enqueue`, `takeDue`, `requeue`, `pendingCount`, `unitsEnRoute`, `clearPending`) | `CopManager.spawnTick` turns every missing slot into a `PendingUnit(role, tier, arriveAt, station, bias)`; due units are spawned through `CopSpawnManager.spawnUnit` |
| Out-of-sight spawn | `CopSpawnManager.spawnUnit` / `hiddenRing`, Keystone `EntitySpawner.isOutOfSight` + `findHiddenSpawnLocation` | Order: the unit's station spawners (within `Spawner_Preference_Radius`, allowed, out of sight, nearest first); a hidden ring biased to the hand-off cone or the station side; a hidden ring anywhere; the 0.15 `spawnNearPlayer`. Null = requeue |
| Breather | `BreatherSettings`, `CopGroup.getBreatherUntil`, `casualtyWithin`, `lastCasualtyAt` | A wipe is a `lastCasualtyAt` after the previous breather, inside `Wipe_Window_Seconds`; `Wipe_Refill` radio line |
| Perimeter | `npc.police.perimeter.PerimeterController`, `PostRing`, `state.CopState.POSTED`, `state.behavior.PostedBehavior`, `listener.police.PerimeterListener` | Started from `WantedEvasionStateEvent` SEARCHING, ended by SEEN (back to PURSUING) or OFF (RETURNING). Posts use Keystone `AbstractNpc.holdPost` |
| Hand-off | `npc.police.handoff.HandoffController` (an AI-tick hook) | Samples the suspect, on the "engaged to not engaged" edge sets a `SpawnBias` on the group and radios `Handoff` |
| Evasion multipliers | `wanted.evasion.Hideouts`, `QuietTrail`, `EvasionClock` (speed = `min(Max_Speed, zone x hideout x cold)`), `ChaseArcs.offlineTotalMs` | Hideouts are read from `PlaceNames` (tag `hideout`); offline time never counts as quiet |
| Bribe stars | `wanted.bribe.BribeStars` (`BeanLifecycle`, a 10-tick sync task) | Items at `cop_point` rows of kind `pickup`; cause `WantedCause.CONTACT` |
| Setup wand | `setup.SetupCommands`, `SetupSelections`, `SetupOutline`, `SetupMessages`, `command.cops.setup.SetupCommand`, `listener.setup.SetupWandListener`; `copsncrooks/setup.yml` | Saves only through the three registries; the wand item carries the NBT tag `cnc_setup_wand` |

**Config reads.** `CopConfigProvider` is not a container bean (`CopLoader` replaces it on every load). A class that
needs it (`Dispatcher`, `PerimeterController`, `HandoffController`, the station commands) takes a
`Supplier<CopConfigProvider>` from `copLoader::getLoadedProvider` and reads it **on every call** with
`requireNonNullElse(provider.getDispatchSettings(), DispatchSettings.DEFAULT)` (same for `getBreatherSettings`,
`getHandoffSettings`, `getPerimeterSettings`), so `/glw reload` applies. A Mockito mock returns null for a new getter,
which the default covers. `CopConfigProvider.getSquadTiers(level)` returns the `@tier` list parallel to
`getSquadComposition` (0 = the star's tier); `CopRole.nextSlot(composition, live)` is the index `nextRole` delegates to.

**Squad tiers.** `YamlCopConfigProvider` parses `"<Role>@<tier>"`; an unknown tier id is judged against the tier ids parsed
from `cops.yml` and reported as `config.unknown_tier`. The slot tier is clamped to `getMaxTier`; escalation and the
formation arc keep the star tier. Code defaults (`COMPOSITION_DEFAULTS`) carry no `@`, only the bundled file mixes.

**Dispatch flow (`CopManager.spawnTick`).** Both Dispatch and Breather off: today's instant loop with the slot tier.
Otherwise: wipe detection, enqueue per missing slot (`targetCount - counted - pending`), one radio line per batch
(`Wipe_Refill` after a wipe, else `Dispatch_En_Route` when a station answered), then `takeDue` -> `spawnUnit`. A unit
spawned with a bias reports `bias.lastSeen()` (never the live position) and `group.markTipOff`. `onWantedStart(player,
wanted, cause)` takes the `WantedCause`: a RESTORE start (rejoin) skips the crime-scene seed and sets
`breatherUntil = now + Rejoin_Grace_Seconds`. `releaseSurplus` calls queued units off before it sends live cops home.
`group.unitsEnRoute(now)` is true for a unit up to 10 s past its ETA (a `ponytail:` constant, not a key).

**Evasion hold.** Since 0.21.2 (T-537) `handlesDecay` is `Evasion.Enable && groupOf(player) != null`, and `tick` clears
only when the group is gone or he is no longer wanted. With no valid non-RETURNING cop standing, `noCopHold` pauses the
clock (lastTick = now, progress kept) for the first response only (`!group.hasStood() && (unitsEnRoute || now <
breatherUntil)`, the rejoin grace included; `hasStood` is set
by `CopGroup.add` and cleared by a wanted start that finds the group empty) and for cause E (an active `biasAt(now)` or a
leashed walker). Otherwise he is searched at the squad's last-known spot, never SEEN (a dead squad's last sighting is no
one's eyes), at `Wanted.Evasion.No_Cop_Inside_Speed` (0.25, floored at 0.01) inside the zone and `Outside_Zone_Speed`
outside, and the drop is the usual `EVASION`. After a wipe the breather and the ETA no longer hold. The S5 silence hold
is `hold=silence` (`copManager.silentRoles(group)` not empty). The first run past every hold with no cop standing calls
`setNoCopSearch`'s consumer once per episode (`noCopTold`, forgotten in `clear`): `EvasionModuleConfig` sends
`Hud.No_Cop_Search` in chat (0.21.2 R2, T-586).
`QuietTrail.tick` sets `group.setBackupHeld(...)`, which makes `requestBackup` and `grantRegroupBackup` refuse.

**Keystone 1.15.0.** Gangland now **requires Keystone 1.15.0**: it uses `EntitySpawner.isOutOfSight(spot, target)` and
`findHiddenSpawnLocation(player, allowed)` (a block ray, not the facing cone of `isVisibleToOtherPlayers`; NPC observers
are skipped) and `NpcPost` with `AbstractNpc.holdPost/getPost/releasePost/tickPost` (leash, arrival and facing; no squad
logic). `KeystoneFloor` (gangland-impl, `keystone-floor.properties` filtered from `<keystone.version>`) makes
`Gangland.onEnable` log one line and disable the plugin on an older Keystone instead of a `NoSuchMethodError`.

**Id floor (docket T-180).** `CopSpawnerRepository`, `CivilianSpawnerRepository` and `JailRepository` record the highest
stored id **before** they skip a row whose world is not loaded; `CopSpawnManager`/`CivilianSpawnManager.raiseIdFloor()`
lifts `EntitySpawner.ID` to it after `onInitialize` and `reloadSpawners`, and `JailService.ID` is `max`ed above the skip. A
new spawner or jail can no longer take the id of a row that is waiting for its world. The station, region and point
registries keep every row (the location is nullable), so their `max + 1` already covers unloaded worlds.

**Debug lines** (`log.debug`, read by the acceptance verdicts): `DISPATCH {player} count= station= eta= hold= reason= bias=`,
`UNIT {player} callsign= tier= role= fromStation= hidden= bias= ahead= at=`, `PERIMETER {player} start|end reason=`
(`OFF`, `CONTACT`, `TIMEOUT`, `SIGHTED`, or `EMPTY` when every post was killed or sent home unseen),
`HANDOFF {player} heading=`, `HANDOFF_LEAD {player} at=x,y,z heading= dir=dx,dz from=x,y,z` (0.21.1: the seed a hand-off
unit applied, `from` the bias's last-known spot), `HANDOFF_RING {player} center=x,y,z fraction= clamped= fallback=`
(0.21.1, T-509: logged just before that unit's `UNIT` line when `Spawn_Between` is above 0; `fallback=true`
when no hidden spot lay within 16 b of the between point, or the lead is in another world (`center` = his own spot), and
the unit took the ring ahead or a later step), `EVASION {player} speed= zone= hideout= quiet=` and
`EVASION {player} hold=enroute|handoff`.

**Radio.** `CopRadio` keeps both constructors; `setPlaceNames` and `placeOf(location)` (place name or `Unknown_Place`) supply
`%place%` to every line (the voice uses the squad's last-known location, `dispatch` the target's). New overloads:
`dispatch(group, target, key, level, tier, extra)`, `sayFromLeader(group, key, extra)`, `compassWord(from, to)`. The lines
`Dispatch_En_Route` and `Wipe_Refill` are in the bundled `Cops.Radio.Priority` list, in the code default set, and are
unioned into a server's own `Priority` list (so an old list still speaks them). `YamlCopConfigProvider.DISPATCH_PRIORITY`
also holds `Handoff`, `Post_Up`, `Most_Wanted` and (0.21.2 R2, T-571) `Officer_Report`, `Radio_Check`,
`Officer_Missing`, `Scene_Found`, `Scene_Suspect` and `Scene_Clear`.

**0.21.1 spawn and dispatch (cnc-cop-ai lane spawn).**
- `CopManager.spawnTick` releases a stranded RETURNING cop (`isStrandedReturning`) that no real player watches
  (`isWatched` -> `CopSightlines.watched`: `inView` with line of sight within `max(24, Visibility_Check_Distance)`,
  Citizens NPCs skipped; not `isVisibleToOtherPlayers`, which counts NPCs and the walker itself). r5: the same helper
  gates `ReturningBehavior`'s stranded-on-return despawn (owner ruling R3; the factory passes
  `Visibility_Check_Distance`), so a walker with no way home (blocked past the stuck window, or its station in another
  world) keeps walking or standing in view and despawns on the first tick nobody looks. The `Return.Max_Ticks` backstop
  (any walk home, review 2) waits for nobody within the 24-block floor (`CopSightlines.watched(body, 0)`, the overdue
  rule `recycles()` applies), so a far AFK bystander no longer keeps a walker forever and no near viewer sees it vanish;
  r6 (owner ruling): that near view holds it for `Cops.Return.Watched_Cap_Seconds` (60, from the first tick it held
  it; 0 = no cap, `CopConfigProvider.getReturnWatchedCapSeconds`), then it despawns watched or not;
  arrival at a registered station despawns regardless, but a cop with no station (home = its own spawn spot, which
  was only hidden when it spawned) stands there while watched and despawns once nobody looks (T-527, acc9-handoff-lead).
  Every removal of a walker that still targets the player past the leash, in
  `spawnTick` or `aiTick` (the R3 release, `recycles()`, `isMarkedForRemoval` after `ReturningBehavior` ended its walk,
  usually reaped by the next AI tick before any spawn run, or `!isValid`), goes through `CopManager.release`, which
  stamps `CopGroup.markLeashReleased(now)` (radio clock): `HandoffController` counts it as leashed out on an
  engaged -> not engaged edge within `Handoff.Bias_Seconds` of the stamp, however many cops chased him in between (a
  refill spawned in the release run included; the judge r5 Q1/Q1c pair: the release timing must not decide the
  hand-off). An older stamp is no cause of the fall-away (a squad wiped a minute later). That edge consumes it; a wanted
  start, a wanted end (the search's `keepSquadOnSearch` included) and the controller's disabled/search early return
  clear it. `isEngaged` counts CUFFING and GUARDING with PURSUING/COMBAT (review 2): a landed cuff sends the rest home
  beside him, which is the chase won, not lost. r6 (review 1): a stamp that did not age past `Bias_Seconds` while a cop
  chased (PURSUING/COMBAT/CUFFING/GUARDING: `tick` clears a stamp older than that while `closing(group)`, review 2)
  still counts on the edge, at any age, when a valid POSTED/OVERWATCH cop of the group now stands farther than
  `Pursuit.Max_Distance` from him (`HandoffController.postLeftBehind`): posts alone held the chase since and he left
  one behind (`pa_postHoldsPastBiasSeconds_thenFallsAway_handsOff`; a minute of chase before the post, or a free
  chaser leaving the post behind and then killed, hands nothing off:
  `staleRelease_aMinuteOfChase_thenThePostIsLeftBehind_noHandoff`,
  `staleRelease_freeChaserLeavesThePostBehind_thenIsKilled_noHandoff`). A post killed or called off is no leash, so a fall-away
  through its loss keeps the `Bias_Seconds` rule (`postHoldsAMinute_thenThePostIsKilled_noHandoff`,
  `staleRelease_thenTheSquadIsWipedAMinuteLater_noHandoff`). The r6 first cut re-stamped the record while posts held
  the chase, which also handed off on a post killed a minute after the release; it was dropped. r7 (review 1): a cop the
  group sent home (`CopGroup.isSentHome`: a backup's extra or a star drop, T-510) was called off, so neither
  `HandoffController.leashedOut` nor the `CopManager.release` stamp counts him, however far he walks with his target
  kept (`levelDropWhilePosted_thenPostLeftBehind_noHandoff`, `levelDropWhilePursuing_...`,
  `levelDropWalkerReleased_...`).
  During the post-escape search
  (`isSearching`) the leash is measured to the squad's last-known spot, the search's goal (`CopManager.leashAnchor`
  for `isStrandedReturning`/`isStranded`, `PursuingBehavior.leashDistance` for the pursuit leash), never his live
  position; `HandoffController` does nothing for a searched player (T-432: the search group runs at level 1 but he has no
  stars), so its own distances need no anchor. The spawner's leash margin reads from the same anchor
  (`CopSpawnManager.setLeashAnchor`, wired by the `CopManager` constructor): a search refill is placed within
  `LEASH_MARGIN` x `Max_Distance` of the last-known spot, so it is never leashed (and churned) on its first tick. The
  Keystone ring and spawner search stay centred on the player (Player-only `EntitySpawner` API), so a search 120+ blocks
  from where he hides gets no refill until Keystone offers a Location-centred ring. T-433: `CopManager.standDown` marks
  the group `stoodDown` after its `Stand_Down` line; `CopRadio` then drops every signal of the group's own squad and every
  line it queued before (`CopRadio.later` wraps `SquadRadio.sayLater`), so no "Flanking, keep him busy!" follows
  "Suspect cleared"; the next wanted start clears it, and so does the target hitting a cop of the group
  (`onCopAttackedAlert`, review 2): a squad he fights again is heard again (its contact call, Man_Down and backup). Neither this release nor
  `recycles()` tips the squad (review r2, R4): no `reportTip`, no `markTipOff`, not even
  `reportTip(lastKnownLocation())`, because `reportTip` re-stamps the search circle back to 6 blocks; the refill
  inherits the squad's own lead and its widening circle (pinned by `strandedReleased_searchCircleKeepsWidening` and
  `recycle_searchCircleKeepsWidening`). Before dispatching it trims the queue to `targetCount - counted`
  (`CopGroup.trimPending`, newest first), so a level drop calls off units. Live cops over the target go home through
  `releaseSurplus` (`CopGroup.pendingRelease` caps how many, itself capped at what the role excess can release less the
  last Commander's, so a leftover never waits for a radio responder), never the last Commander, in two cases (T-435). Inside an
  episode, only when the level's base target dropped below `CopGroup.lastTarget`: radio responders joining at the same
  target stay. At a new wanted start, `onWantedStart` sets `pendingRelease = max(0, engaged - target)` against the
  cops engaged after the call back, not against `lastTarget`: a correction of the r3 note, which keyed the new episode
  on `lastTarget` too, so a re-spot after a stepwise escape (`Drop_Mode` ONE_STAR: 3, 2, 1, search) kept the four
  cops the search called back, while an all-stars escape trimmed to two. `releaseSurplus` runs only at wanted level
  > 0, so neither a drop's release still waiting for free cops (all fighting) nor a backup that runs out mid-search
  fires in the post-escape search (wanted level 0, run at one star), which keeps every walker it called back (T-187);
  the next wanted start replaces whatever is still owed. A new wanted start calls back walkers in
  range only up to the new level's target (`reengageReturning(group, player, limit, eligible)`, nearest first; a group
  with no engaged Commander calls its nearest walking Commander back first, past the limit if need be, so the start
  never leaves the last Commander sent home, T-146; when the new level's composition has no Commander slot he stays
  as the one cop over its target). A
  walker the group sent home (`releaseSurplus`, or left over by that capped call back) is marked in
  `CopGroup.sentHome`: it is out of the counted cops, and while the group lacks cops (a level rise, a casualty) the
  nearest one in range is called back before anything spawns (T-436). Only a new wanted start and `releaseSurplus`
  mark: inside an episode a walker rotated out of a pursuit or leashed is never marked, so the D1 no-bounce rule holds
  there. A walker called back loses its mark at once (`CopGroup.unmarkSentHome` in `reengageReturning`), so one the AI
  rotates out again before any `isSentHome` read is an ordinary walker, never called back a second time (r5). `spawnDueUnits` lends a unit queued before a hand-off the group's
  active bias; a unit refused a spot is requeued without a bias that ran out (T-434). `reengageReturning` skips
  stranded cops.
- Tips (Keystone 1.15.4 `reportTip`, no sighting stamp): the wanted start (plus `markTipOff`, which keeps the
  `Dispatch_Wanted` once-per-hunt gate and the evasion hold), the biased spawn seed, and a hit on a cop that has no line
  of sight to the attacker. The seed (`reportTip(bias.lead(CopSpawnManager.handoffLead(provider)))` plus `markTipOff`) is given once per
  hand-off bias and only while no sighting or tip moved the squad's lead since the bias was set
  (`CopGroup.takeBiasSeed`, T-434), so it never rewinds a fresher lead or the hand-off heading sampled from it. Owner
  ruling R5: `SpawnBias.lead(blocks)` is the bias's `lastSeen` (the squad's lead at the hand-off) moved
  `Cops.Handoff.Lead_Blocks` (24) along the bias heading, which `HandoffController` reads from the squad's last-known
  history, never the live position; `CopSpawnManager.handoffLead` caps the lead's length at `CopSpawnManager.LEASH_MARGIN x
  Pursuit.Max_Distance` from that spot (not from the player, whose live position the cap would leak). The lead stops a
  block short of the first solid block on the way (a ray trace at eye height) and stands on ground near his height
  there (`PostRing.standingSpot`), facing the heading; no dry standing room (a pond's or lava lake's bed counts as none:
  liquids are passable; r5: so are feet in seagrass, tall seagrass, kelp, a kelp plant, a bubble column or any
  waterlogged block, `SpawnBias.wet`) or an unloaded chunk anywhere in the box the lead spans gives `lastSeen` itself, checked before
  the ray trace so the trace never loads a chunk on the main thread, so a lead is never underground, under water or
  inside a building he never entered (a breach trigger). The seed goes through
  `CopGroup.seedHandoff`, which remembers it: `HandoffController` takes no heading sample while the squad's lead is that
  seed, so a second hand-off with no news since leads from the squad's real last-known spot again (x 20 -> 44, never
  44 -> 68; `secondHandoffWithoutNews_leadsFromTheLastKnownSpotAgain`). The units head for that spot and search it:
  the tip restarts the search circle at 6 blocks there, once per hand-off (not once per refill). Spawn placement
  (owner ruling T-423, r6): `CopSpawnManager.betweenRing` takes a `hiddenRing` spot within `BETWEEN_RADIUS` (16) of the
  point `Cops.Handoff.Spawn_Between` (0.5) of the way from his live spot to `bias.lead(handoffLead)`, that point held
  within `min(Spawn.Max_Distance, LEASH_MARGIN x Pursuit.Max_Distance)` of him, through `fits` (leash margin and the R6
  view gate); none (or `Spawn_Between: 0`, or a lead in another world) falls back to `hiddenRing` ahead of the bias as
  before. The ring is still sampled around him (Keystone's rings are Player-centred) and only accepted near the
  between point. Spawning near his live spot is what every unit does; the goal stays the lead. Leash release
  and recycle never re-tip (above). Only member sightings make `NpcPursuit`
  chase the live position.
- **0.21.1 r6, Keystone 1.15.4 consumption.** The six sightings by an NPC the cops drive outside `pursue` report that
  NPC (`NpcSquad.reportSighting(AbstractNpc, Location[, range])`), so members without their own sight chase toward the
  reported spot only while it still sees him (re-checked on each ask, 1.5 s at most unless reported again; every
  per-tick caller re-reports): `OverwatchController` and `PerimeterController` with `settings.sightRange()`,
  `StandoffController.reportPostedSightings` with its `sightRange()`, `IdleBehavior` and `PatrolBehavior` with the
  perception range they looked with (`CopPerception.rangeFor`: a dark factor would otherwise leave the mark at 64), and
  `CopManager.onCopAttackedAlert` (a hit the cop has line of sight to) with the two-argument one. The plain
  `reportSighting(Location)` stays for the two seeds where a cop takes on a target it is fighting (`attackerSquad`,
  the solo squad). `PursuingBehavior` keeps `squad.hasFreshSighting()` (decided per site): the stuck rotation is
  squad-level by design and tips no longer stamp it (`stuckWithOnlyATip_stillCountsTowardRotation`); the cuff waiver
  needs the cop's own line of sight inside `Cuff_Radius`; `CopPerception.pursuitRange` only lifts the lock-on filters
  for the cop's own look, and Keystone takes the live goal from a member's own sight alone. None acts on a live
  position the cop did not see (R4). `isInContact()` was not used: it is never true under `Tactics.Enabled: false`.
- **0.21.1 r6, the pointman freeze.** `CitizensNavigator.run()` stops a walk (`CancelReason.STUCK`) while its end is
  farther than `localParams.range()` (Citizens `default-range-blocks`, 100 on the test server), and Keystone 1.15.4
  sets no range and hands Citizens whole route legs (`NpcRoute.legsFrom` splits only at doors and ladders). A hand-off
  pointman spawned 239 blocks from its lead had its `moveTo` stopped, its planned route's single leg stopped twice
  ("route walk failed 2 legs"), the goal marked unreachable, and stood until the stuck recycle. The pursuit leash and
  the R5 seed did not cause the frozen tick. Residual (owner decision, review 1): `PursuingBehavior.leashDistance`
  measures to his live spot unless the post-escape search runs, so a hand-off unit walking toward a lead farther than
  `Pursuit.Max_Distance` from him goes RETURNING once it is that far from his (hidden) live spot, wherever its lead is;
  that walker's release can hand the chase off again. Hand-off units therefore cannot reach a lead more than
  `Max_Distance` from him. Leashing a tip-only unit to its lead instead was not taken: a spawn-between unit spawns
  within `0.75 x Max_Distance` of him, so with a lead 205 blocks off (the acc7 geometry) it would leash the moment it
  spawned. `CopNpc.pursue` (goal: the squad's last-known spot)
  and `CopNpc.navigateTo` (stations, perches, posts, beats) set both navigator parameter sets' range to
  `distance + 56` (`WALK_RANGE_MARGIN`: past Keystone's 48-block `NpcSquad.SEARCH_MAX_RADIUS`, for a search round an unreachable goal; uncapped, so a far station walk raises the vanilla pathfinder's budget) and back to the server's own range for a near goal. The upstream fix (Keystone
  capping each walk and leg inside the range) would retire it.
- `CopSpawnManager.fits(target)` gates every spawn path (`spawnUnit` and `spawnNearPlayer`): within
  `0.75 x Pursuit.Max_Distance` (3D) and not `inViewOf` (60 degree eye cone plus `isOutOfSight` false). The ring
  fallback takes no Keystone predicate, so a ring spot in view returns `null` (the caller retries next run). A biased unit
  tries `betweenRing`, then `hiddenRing` ahead of the bias, before the station spawners.
- `EvasionClock.noCopHold` (0.21.1 `hasLiveCop`) also holds for an active `biasAt(now)` or a RETURNING cop still targeting the player past
  `Pursuit.Max_Distance` (`CopManager.isStranded`, the walker the next spawn tick refills), so a whole-squad leash
  pauses the search whichever AI-tick hook runs first. A walker inside the range holds nothing (a leashed cop keeps
  its target, so keying on every walker froze the stars).
- `PatrolManager` stamps a station's loss (manage prune and AI-tick prune) and refills only after
  `Cops.Patrol.Respawn_Seconds` (`PatrolSettings.respawnSeconds`, 10-arg constructor keeps the default 30).

**Sharing places.** A module publishes places by implementing `RegionProvider` and registering it with the core `PlaceNames`
bean from its own `@Bean` method (cops: `AdminRegionRegistry`, turf: `place.TurfRegionProvider`, core:
`WaypointRegionProvider`). See [`gangland-api.md`](../gangland-api.md).

**Core features that run without cops (gangland-impl).** `data.wanted.ContactDesk`, `listener.wanted.EvasionStateListener`,
`command.sub.contact.ContactCommand` and `WantedAspect` (contacts and the sign gate; the desk keeps the unseen state from
`WantedEvasionStateEvent`); `data.teleportation.HospitalShield`, `listener.player.HospitalShieldListener`,
`WaypointManager.nearest(location, type)` and the hospital branches of `CustomPlayerDeathListener`/`PlayerDeathListener`
(one bill: `quote` + `charge`, a pending bill released on `PlayerUndownedEvent` or quit); `EntityDamageListener` (self-defence
and takedown rules, `Wanted.Self_Defence`, `Bounty.Takedown_Minimum`). `DetainmentListener.onUndowned` and
`JailIntakeService.admit(player, deathCommit)` make a downed handcuffed player pay the ward bill and no charge sheet.

---

## Kill Combo System

**Package:** `org.luckyraven.gangland.copsncrooks.combo`

### KillCombo

Tracks consecutive kills per player and triggers wanted level increments at configurable thresholds:

```java
KillCombo killCombo = new KillCombo(plugin, wantedKillCounter);

// Configurable callbacks:
killCombo.

setOnWantedLevelTrigger(event ->{ /* increment wanted */ });
		killCombo.

setOnComboIncrement(event ->{ /* display combo UI */ });
		killCombo.

setOnComboReset(event ->{ /* clear combo UI */ });
		killCombo.

setOnPlayerDeath(playerId ->{ /* handle death reset */ });
```

#### Flow

```
Player kills entity
  │
  recordKill(killer, wantedKiller, killed, resetAfterSeconds)
  │
  ├── Get or create KillComboTracker for player
  ├── tracker.addKill(killed, points=1)
  ├── Fire onComboIncrement callback
  ├── checkWantedLevelTrigger:
  │     Compare pointKillCount against wantedKillCounter thresholds
  │     Thresholds auto-scale linearly if fewer than maxLevel entries
  │     If threshold met -> fire onWantedLevelTrigger callback
  ╰── tracker.restartTimer() (resets inactivity countdown)
```

### KillComboTracker

Per-player tracker that holds:

- Kill count and point kill count
- List of killed entities
- Inactivity timer (resets combo after `wantedKillComboResetAfter` seconds of no kills)

When the timer expires, the tracker fires the combo reset callback and removes itself.


---

## Bounty System

**Package:** `org.luckyraven.gangland.copsncrooks.bounty`

### Bounty

Core data class tracking a player's bounty:

```java
// Fields:
double amount                        // Current total bounty
double baseAmount                    // Base bounty per wanted level
double levelMultiplier               // User level scaling factor
Map<CommandSender, Double> userSetBounty  // Per-setter bounty contributions

// Key methods:
void addBounty(CommandSender, amount, userLevel)  // Level-scaled add

void addBounty(CommandSender, amount)              // Direct add

void removeBounty(CommandSender)                   // Remove sender's contribution

double calculateLevelScaledBounty(base, level)       // base * (1 + level * multiplier / 10)

double getAutoBountyIncrease(userLevel, wantedLevel) // baseAmount * wantedLevel, then scaled

void resetBounty()                                  // Clear all

// 0.15.0 posted/notoriety split (the ledger is keyed by Bounty.posterId(sender)):
BigDecimal getPostedAmount()     // sum of what players actually paid in
BigDecimal getNotoriety()        // max(0, amount - posted): the server-made part
void addNotoriety(BigDecimal x)
BigDecimal claimPosted()         // returns the posted escrow, leaves the notoriety, clears the ledger
String serializeLedger()         // "id=posted:paid;..." stored in user.bounty_posters
void restoreLedger(String s)     // null = pre-0.15 row: the whole amount counts as posted, once
```

### BountyExecutor

Drives the periodic bounty-increase timer:

```
Timer interval = settings.getTimeInterval() seconds

Each tick:
  1. Calculate increase: currentBounty * timerMultiple, then level-scale
  2. Cap at timerMax
  3. Fire BountyEvent (cancellable)
  4. Apply increase to bounty amount
```

Since 0.15.0 the timer grows **notoriety only** (it stops when the notoriety is 0 or reaches the cap) and runs on the
main thread. A claim pays `getPostedAmount()` unless `Bounty.Pay_Notoriety` is true.

---

## Detainment and Jail System

### DetainmentState

```java
enum DetainmentState {
	NORMAL,       // Free player
	HANDCUFFED,   // Restrained, cannot interact, visual effects
	JAILED        // Teleported to jail cell, fully restrained
}
```

### DetainedPlayer

Data class:

```java
class DetainedPlayer {
	UUID            playerId;
	Integer         jailId;      // Nullable -- only set when JAILED
	DetainmentState state;
}
```

### DetainmentService

Central service for all detainment operations:

| Method                    | Purpose                                                           |
|---------------------------|-------------------------------------------------------------------|
| `getState(Player)`        | Returns current `DetainmentState`                                 |
| `isHandcuffed(Player)`    | State == `HANDCUFFED`                                             |
| `isJailed(Player)`        | State == `JAILED`                                                 |
| `isRestrained(Player)`    | `HANDCUFFED` or `JAILED`                                          |
| `handcuff(Player)`        | Apply handcuffs: set state, apply visuals, send title/action bar  |
| `jail(Player, jailId)`    | Jail player: register in jail, set state, teleport, apply visuals |
| `release(Player)`         | Full release: unregister from jail, clear state, clear visuals    |
| `setState(Player, state)` | Low-level state change with visual sync                           |
| `handleJoin(Player)`      | Re-apply visuals on login; teleport jailed players back to cell   |
| `handleQuit(Player)`      | Handcuffed players auto-escalate to JAILED on quit                |
| `handleRespawn(Player)`   | Jailed players are re-teleported to cell on respawn               |
| `tickVisuals(Player)`     | Periodic visual refresh (called from scheduled task)              |

#### Visual Effects

Restrained players receive:

- **Slowness IV** (infinite duration)
- **Blindness I** (infinite duration)
- Forced inventory close on initial restraint
- Action bar messages: `"Handcuffed - You cannot interact"` / `"Jailed - You cannot interact"`
- Title messages on state transitions

Effects are skipped for spectator mode, dead players, and downed players.

#### Quit-to-Jail Escalation

When a handcuffed player logs out, their state automatically escalates to `JAILED`. On rejoin, they are teleported
to their assigned jail cell. This prevents escape-by-disconnect.

### JailRegistry

In-memory registry of jail cells:

```java
Jail getJail(int id)                      // Get jail by ID

Location getJailLocation(int id)          // Get jail location by ID

Location getJailLocation(UUID playerId)   // Get jail location for a jailed player

Jail setJailLocation(int id, Location, maxCapacity)  // Create or update

void detainPlayer(int jailId, UUID)       // Add player to jail (releases from previous)

void releasePlayer(UUID)                  // Remove player from all jails

Integer getJailIdForPlayer(UUID)          // Which jail is a player in?

Integer findAvailableJailId()             // First available jail cell
```

### JailService

Persistence layer wrapping `JailRegistry` with `IRepository<Jail>`:

```java
Jail setJailLocation(Location, maxCapacity)   // Create + persist

void detainPlayer(int jailId, UUID playerId)  // Detain + persist

void removeJail(int jailId)                   // Delete + persist

void releasePlayer(UUID playerId)             // Release + persist all

void saveAll()                                // Flush to database

void reload()                                 // Clear + reload from database
```

---

## Events

The module fires these custom Bukkit events (two chase events, `CrimeCommittedEvent` and `WantedEvasionStateEvent`, live in `gangland-api`):

### Wanted Events (`events.wanted`)

| Event                    | When Fired                                    | Key Fields                                 | Cancellable |
|--------------------------|-----------------------------------------------|--------------------------------------------|-------------|
| `WantedStartEvent`       | Player's wanted level goes from 0 to positive | `player`, `wanted`, `wantedLevel`, `cause`          | No          |
| `WantedLevelChangeEvent` | Any wanted level change                       | `player`, `wanted`, `oldLevel`, `newLevel`, `cause` | Yes         |
| `WantedEndEvent`         | Wanted level reaches 0                        | `player`, `wanted`, `cause`                         | No          |
| `WantedEvasionStateEvent` (gangland-api, `events.wanted`) | 0.15.0: the evasion clock changes state (`SEEN`, `SEARCHING`, `EVADED`, `OFF`) or the search countdown changes | `player`, `state`, `level`, `secondsLeft`, `zoneCentre`, `zoneRadius` | No |
| `CrimeCommittedEvent` (gangland-api, `events.crime`) | 0.15.0: `CrimeService.commit` | `player`, `crimeId`, `location`, `seenByCop`, `witnesses` | Yes |
| `WantedEvent`            | Periodic timer tick (before decrement)        | Inherits from base event                   | Yes         |

### Bounty Events (`events.bounty`)

| Event         | When Fired                    | Key Fields      | Cancellable |
|---------------|-------------------------------|-----------------|-------------|
| `BountyEvent` | Periodic bounty increase tick | `amountApplied` | Yes         |

### Kill Combo Events (`events.combo`)

| Event            | When Fired                        | Key Fields          | Cancellable |
|------------------|-----------------------------------|---------------------|-------------|
| `KillComboEvent` | Combo increment, wanted trigger or reset (0.15.0: fired through Bukkit; `Kind` is `INCREMENT`, `WANTED_TRIGGER` or `RESET`) | `player`, `tracker`, `kind` | No          |

### Police Events (`events.police`)

| Event                | When Fired                       | Key Fields                                                                 | Cancellable |
|----------------------|----------------------------------|----------------------------------------------------------------------------|-------------|
| `CuffedEvent`        | Cop successfully cuffs a player  | `cop`, `target`, `cuffRadius`, `maxAttempts`                               | No          |
| `DuringCuffingEvent` | Each tick during cuffing wind-up | `cop`, `target`, `cuffRadius`, `maxAttempts`, `cooldown`, `remainingTicks` | No          |

### NPC Events (`events.npc`)

| Event                | When Fired                | Key Fields                                       | Cancellable |
|----------------------|---------------------------|--------------------------------------------------|-------------|
| `NpcEvent`           | Base class for NPC events | `npc` (AbstractNpc)                              | No          |
| `CopDeathEvent`      | Cop NPC dies              | `npc`, `killer` (nullable)                       | No          |
| `CivilianDeathEvent` | Civilian NPC dies         | `civilianNpc`, `killer` (nullable), `experience` | No          |

### Listening to Events

```java

@EventHandler
public void onWantedStart(WantedStartEvent event) {
	Player player = event.getPlayer();
	int    level  = event.getWantedLevel();
	// Spawn cops, notify the player, etc.
}

@EventHandler
public void onCopDeath(CopDeathEvent event) {
	Player killer = event.getKiller();
	if (killer != null) {
		// Award experience, increment wanted level, etc.
	}
}

@EventHandler
public void onCivilianDeath(CivilianDeathEvent event) {
	CivilianNpc npc = event.getCivilianNpc();
	double      xp  = event.getExperience();
	// Award XP, check if NPC counts for wanted level, etc.
}

@EventHandler
public void onCuffed(CuffedEvent event) {
	Player target = event.getTarget();
	// Trigger detainment, jail the player, etc.
}
```

---

## Listeners

**Package:** `org.luckyraven.gangland.copsncrooks.listener`

| Listener                 | Package               | Handles                                                        |
|--------------------------|-----------------------|----------------------------------------------------------------|
| `CopListener`            | `listener.detainment` | Cop NPC damage events -- alerts group, tracks attackers        |
| `DetainmentListener`     | `listener.police`     | Detainment state changes, visual effects, movement restriction |
| `CuffingListener`        | `listener.police`     | Cuffing process events, UI feedback during wind-up             |
| `CivilianDeathListener`  | `listener.civilian`   | Civilian death -- drops, experience, wanted level tracking     |
| `CivilianDamageListener` | `listener.civilian`   | Civilian damaged -- triggers flee/combat, cop-civilian combat  |

---

## Configuration

### Module-owned tuning (0.15.1)

Since 0.15.1 the module reads its tuning from its own files, not `settings.yml`. The shipped files are the reference:
every key there carries its comment and default, so copy key names from them rather than from this page.

| File | Root | What it holds |
|---|---|---|
| `copsncrooks/cops.yml` | `Cops:` | `Count`, `Behaviour` (incl. `Guard_Radius`), `Spawn`, `Pursuit`, `Return`, `Navigation` |
| `copsncrooks/detainment.yml` | `Detainment:` | jail capacity, transit delay, break-free, bribes, bail, sentence, fallback exit waypoint, sounds |
| `copsncrooks/wanted.yml` | `Wanted:` | `Kill_Combo` (`Enable`, `Kill_Counter`, `Reset_After`) beside the chase/heat config |

Each moved key is read through `MovedSetting` (gangland-api 2.2), so an old `settings.yml` value, or the
`settings-old*.yml` backup Keystone writes on upgrade, still applies for one release with a warning. The full
legacy-path to new-path table is in [`migration-0.15.1.md`](../migration-0.15.1.md). Code still reaches these values
through the contract interfaces (`CopSettings`, `WantedSettings`, `CivilianSettings`); only the adapters behind them
changed.

### cops.yml

Per-tier cop configuration loaded by `CopLoader`. Since 0.12.0 `Cops` also carries the `Melee`, `Tactics`, `Radio`,
`Backup` and `Retreat` blocks, and each tier `Difficulty`, `Fire_Rate_Multiplier` and an optional `Tactics` override.
The shipped file and [the feature doc](../features/cops-n-crooks.md) list every key; the radio lines live in
`copsncrooks/cop_radio_messages.yml`. The abridged example below predates those keys:

```yaml
tiers:
   1:
      display_name: "&9Officer"
      health: 20.0
      damage: 3.0
      speed: 1.0
      cuff_radius: 3.5
      can_use_weapons: false
      skip_cuffing: false
      weapon_pool:
         - WOODEN_SWORD
      armor:
         helmet: CHAINMAIL_HELMET
         chestplate: CHAINMAIL_CHESTPLATE
         leggings: CHAINMAIL_LEGGINGS
         boots: CHAINMAIL_BOOTS
   2:
      display_name: "&1Sergeant"
      # ... higher stats
   3:
      display_name: "&5Lieutenant"
      can_use_weapons: true
      weapon_name_pool:
         - "Pistol"
      # ...
   4:
      display_name: "&4SWAT"
      skip_cuffing: true
      weapon_name_pool:
         - "Rifle"
         - "SMG"
      # ...
   5:
      display_name: "&cMilitary"
      skip_cuffing: true
      # ... highest stats and weapons
```

### civilians.yml

Civilian type and group definitions loaded by `CiviliansLoader`:

```yaml
types:
   pedestrian:
      display_name: "&7Pedestrian"
      entity_type: PLAYER
      health: 20.0
      hostile: false
      ai:
         wander_enabled: true
         wander_range: 8
         flee_enabled: true
         flee_range: 20
         combat_enabled: false
      wearables:
         helmet: LEATHER_HELMET
      drops:
         money: 5.0
         experience: 10.0
   gang_member:
      display_name: "&cGang Member"
      hostile: true
      ai:
         wander_enabled: true
         wander_range: 6
         flee_enabled: false
         combat_enabled: true
         attack_damage: 4.0
         attack_range: 6.0
      weapon_name_pool:
         - "Pistol"
   trader:
      display_name: "&6Trader"
      hostile: false
      ai:
         wander_enabled: false
      inventory:
         title: "&6Trader"
         items:
            - "DIAMOND_SWORD:1:5000"

groups:
   street_gang:
      members:
         gang_member: 3
      stay_together_range: 15.0
```

---

## Integration Points

### With gangland-impl

The cops-n-crooks module is a feature module with no direct dependency on `gangland-impl`. Integration flows
through:

1. **Contract interfaces** -- `WantedSettings`, `BountySettings`, `CopSettings`, `CivilianSettings` are implemented
   in `gangland-impl` and passed during initialization
2. **Event bus** -- Bukkit events fired by this module are handled by listeners in `gangland-impl`
3. **DependencyContainer** -- `CopService`, `CivilianService`, etc. are produced as `@Bean` methods in
   `CopsAndGadgetsConfig` and registered in the DI container automatically during the CONFIG phase

### With gangland-weapon

- `WeaponService` resolves gangland weapon names from `weaponNamePool` configs
- `AbstractNpc` fires `WeaponShootEvent` through the standard weapon pipeline
- `GunWeapon` projectile and reload systems are used as-is for NPC combat

### With plugin-persistence

- `IRepository<CopSpawner>`, `IRepository<CivilianSpawner>`, `IRepository<Jail>` provide persistence
- Spawner points and jail cells are loaded on startup and saved on change

### With Citizens API

- All NPCs are created via `CitizensAPI.createNPC()` (or equivalent)
- Navigation uses Citizens' built-in pathfinder (`npc.getNavigator().setTarget()`)
- NPC entity type is configurable (PLAYER, ZOMBIE, etc.)
- Entity lifecycle managed through Citizens `NPC.spawn()`, `NPC.despawn()`, `NPC.destroy()`
