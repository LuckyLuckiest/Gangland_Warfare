# Parity study: gang fights in arenas (GP-29, GP-30, GP-31, GP-20 wins/losses)

Planning only, 2026-09-30, master @ ff9d813f (0.12.0). No product code. Idea-level parity with Gangs+ only: the verbs,
node names, YAML keys and message text below are Gangland's own. The one external read was the Gangs+ "Adding fight
arenas" doc page through the owner's Chrome extension (connected, `isLocal`), used only to confirm the arena *shape*:
two opposite corners **with a Y range**, one spawn per gang, players cannot leave the box, arenas are hand-edited in a
YAML file that needs a restart. Everything else in this study comes from our own code.

Path shorthand (as PLAN.md): `GG/` = `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/`,
`GM/` = `gangland-features/gangland-mail/src/main/java/org/luckyraven/gangland/mail/`,
`IMPL/` = `gangland-impl/src/main/java/org/luckyraven/gangland/`,
`GT/` = `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/`. **NEW** = does not exist yet.
Graph oriented first (`graphify explain CustomPlayerDeathListener | EntityDamageListener | GangMembersDamageListener`,
`graphify query DownedPlayerRegistry | Wanted | CuboidRegion | WandSelectionManager | CivilianSpawnManager`), then raw reads.

---

## 0. Verdict

1. **Build fights as a NEW runtime module `gangland-gang-fights`** (module id `fights`, `Depends: [gang]`, no `Plugins:`),
   modelled on `gangland-mail`. Not inside `gangland-gang`.
2. **Arena = 3D box (two corners with Y) + N spawns per side + one Exit point**, admin-built with `/glw arena ...`
   from the admin's standing position, saved to a module-owned YAML data file, hand-editable, hot-reloadable. No turf wand.
3. **One listener move makes fights safe by construction:** a `NORMAL`-priority `EntityDamageEvent` handler that cancels a
   *lethal* hit on a fighter and eliminates them. It runs before the wanted/bounty/kill credit (`HIGH`) and before the
   downed-state interception (`HIGHEST`), so a fight death raises no wanted level, pays no bounty, drops no inventory,
   triggers no downed state and no respawn teleport. Needs no Bartizan type.
4. **Bets are hard-escrowed** from the gang banks at accept (in memory), paid to the winner at result, refunded on any
   cancel and on server stop. Allied gangs cannot fight each other.
5. **Reject "turf duels"** as the fights feature (section 9). Keep them as a possible later *stake type* on top of this engine.
6. **Zero `gangland-api` change in v1** (`GanglandApi.VERSION` stays `2.0`; `Host_Api: 2.0`). An additive read seam is staged for
   v1.1, only when the leaderboard lane or turf presence needs it (section 8).

Scope note for the synthesizer: this module touches **none** of the W0-W4 hot files (PLAN.md section 7.1), so it can run
beside W2-W4 or after 0.14.0; the shared files it does touch are listed in section 10.

---

## 1. What Gangland has today (verified)

| Fact | Where |
|---|---|
| No fight, arena, challenge or duel code exists anywhere in the repo | grep over all modules, 0 hits |
| Gang bank is a Keystone `EconomyHandler`; `withdrawAmount` throws `EconomyException("Amount exceeding balance")` when short; `getAmount()` is `BigDecimal` | Keystone `keystone-hooks/.../economy/EconomyHandler.java` (withdraw at L75-80); used by `GG/command/sub/gang/GangWithdrawCommand.java` (`gang.getEconomy().withdrawAmount`, checks `getAmount().compareTo`) |
| Lethal hit on a player: `EntityDamageListener.onPlayerEntityDeath` runs at **HIGH** on `EntityDamageByEntityEvent`, and on a lethal hit does `setKills`, bounty claim, `handleWanted` / kill-combo `recordKill` | `IMPL/listener/player/EntityDamageListener.java` L61-149 (`handlePlayerKills` L100, `handleWanted` L206) |
| Then `CustomPlayerDeathListener.onEntityDamage` at **HIGHEST** on `EntityDamageEvent` cancels the lethal damage, sets health to the downed value, `DownedPlayerRegistry.add`, and `enterDownedState` (L200) drops the whole inventory (`dropInventoryIfAllowed`, below L264, skipped only by the `KEEP_INVENTORY` gamerule), switches game mode, counts down, then `performRespawn` (L264) teleports to the respawn waypoint | `IMPL/listener/player/CustomPlayerDeathListener.java` L91-113, L200-330 (drop helper near the end) |
| Quit while downed is undone at MONITOR (`restoreDownedState`) - precedent for touching a player inside `PlayerQuitEvent` | same file L118-146 |
| Wanted level: `Wanted.setLevel` fires the **cancellable** `WantedLevelChangeEvent` and returns when it is cancelled | `gangland-core/.../core/wanted/Wanted.java` L58-72; `.../events/wanted/WantedLevelChangeEvent.java` (implements `Cancellable`) |
| Cops start hunting from `WantedLevelChangeEvent`/`WantedStartEvent` (`CopManager.onWantedStart` L91) | `gangland-features/cops-n-crooks/.../npc/police/CopManager.java` |
| Bartizan's default damage pipeline is `living.damage(amount, shooter)` through `damageIgnoringInvulnerability`, so it fires ordinary `EntityDamageByEntityEvent`; if health did not drop it treats the hit as "blocked" and skips knockback and `WeaponEntityDamageEvent` | Bartizan `bartizan-plugin/.../raytrace/WeaponRaytracerImpl.java` L661-669 and the `damageBlocked` check in the same method (L536-600) |
| Same-gang **and allied** players cannot damage each other (`gang1.isAlly(gang2) \|\| same id`, `LOWEST`); civilians additionally cancel Bartizan impacts between them | `GG/listener/gang/GangMembersDamageListener.java` L27-49; `gangland-civilians/.../listener/gang/GangAllyWeaponImpactListener.java` L40-48 |
| `Gang.isAlly` was one-directional (GR-35); W0-C makes it symmetric | census `gang.md`; PLAN.md P1.3 |
| A module adds a whole sub-tree under `/glw gang` through a `CommandContribution` (`parent() == "gang"`); mail is the pattern, including its own repository package, listener package, `Depends: [gang]` | `GM/command/GangMailContribution.java` L54-63, `GM/MailModule.java`, `GM/MailModuleConfig.java` |
| Turf regions are X/Z only ("Y is ignored"); the wand and `Selection` live inside turf | `GT/data/CuboidRegion.java` L14-27; `GT/selection/WandSelectionManager.java`, `Selection.java` |
| Turf presence is location based (`GangPresenceTracker`, `TurfLocationTracker`); civilians spawn from admin-placed spawn groups | `GT/task/GangPresenceTracker.java`; `gangland-civilians/.../command/CivilianSpawnGroupCommand.java` |
| Precedent for blocking commands for a restricted player | cops `listener/police/DetainmentListener.onCommand` (L270) |
| Shutdown order: `ShutdownSequence` runs bean shutdown, **module disable**, final save, connection close | `IMPL/Gangland.java` L81-86 |
| Downed state lives in `gangland-core` (`DownedPlayerRegistry`, `PlayerDownedEvent`, `PlayerUndownedEvent`), reachable by a module through the api's compile-scope re-export | `gangland-core/.../core/downed/` |
| `GangDeleteEvent` fires after a gang is removed; mail already listens with `MailGangDeleteListener` | `GG/events/gang/GangDeleteEvent.java` |
| Official module ids are a six-entry code constant (no `gang`, no `lootchest`) | `IMPL/command/sub/module/ModuleInstalls.java` L24-31 |

---

## 2. Placement: a NEW module `gangland-gang-fights`

### Decision

`gangland-features/gangland-gang-fights`, artifact `org.luckyraven:gangland-gang-fights`, `module.yml`:
`Id: fights`, `Main: org.luckyraven.gangland.fights.FightsModule`, `Host_Api: 2.0`, `Depends: [gang]`, **no `Plugins:`**.
It compiles against `gangland-api` and `gangland-gang` at `provided` (exactly mail's pom shape); never `gangland-impl`.

### Why not inside `gangland-gang`

| Reason | Detail |
|---|---|
| Size and hot paths | ~30 classes, three high-frequency listeners (move, damage, command). Isolating them means a server that does not want fights simply does not install the jar; `/glw module remove` works. |
| `gangland-gang` has no module YAML plumbing yet (PLAN.md section 2: the W1 kickoff creates it) and is the hub of W0-C/W1-G/W1-R/W2-P1/W2-P2 lanes; fights inside it would add a sixth lane on the same files. |
| Mail precedent | Invites and ally requests are the same shape (state machine + expiry + repository + `CommandContribution` under `gang`). The architecture already decided that this kind of feature is a module. |
| Dependency direction | Fights needs `Gang`, `GangManager`, `MemberManager`, `GangPermissions`, `GangDeleteEvent`; gang needs nothing from fights. A module keeps that one-way. |
| Bartizan independence | Fights needs no weapon type (section 5). Unlike civilians, cops-n-crooks and gadget it loads on a server without Bartizan or Citizens, as mail and gang do. |
| Fault isolation | A bug in fights cannot abort `GangModule`; the loader's `ReflectionGuard` and per-module enable isolate it. |

Cost, listed honestly: one more artifact to publish (Central), one more id in `ModuleInstalls.OFFICIAL`, a `<module>` line
and a jar copy in the build. Adjacent finding: `OFFICIAL` today lists six ids and omits `gang` and `lootchest`, which are
real modules (`gangland-features/pom.xml` L20-27); a docket triage row (section 11) covers it.

### Module layout (all **NEW**; package `org.luckyraven.gangland.fights`)

| Class | Job |
|---|---|
| `FightsModule`, `FightsModuleConfig` (`@Configuration`, CONFIG-phase beans), `FightsModuleFiles` + `FightsModuleFileConfig` (KERNEL-phase `FileHandler` with the module classloader; copy `GT/TurfModuleFileConfig`) | wiring |
| `command/FightContribution` (`CommandContribution`, `parent() = "gang"`) + `command/fight/*` | `/glw gang fight ...` player verbs |
| `command/ArenaCommand` + `command/arena/*` (module command package, like `TurfCommand`) | `/glw arena ...` admin verbs |
| `arena/Arena`, `arena/ArenaBox` (3D, normalized corners, `contains(Location)`), `arena/ArenaRegistry`, `arena/ArenaFile` (read and write `fight/arenas.yml`), `arena/ArenaDraft` (per-admin, in memory) | arena model |
| `fight/Fight` (state, sides, escrow, clock), `fight/FightState`, `fight/FightManager` (registry: challenge and fight per gang and per player), `fight/FightChallenge`, `fight/FightSide`, `fight/Participant`, `fight/PlayerSnapshot` | state machine |
| `fight/FightRules` (pure: challenge validation, ranked test, winner decision), `fight/FightSettlement` (pure over `EconomyHandler` pairs), `fight/FightClock` (test seam) | testable core |
| `listener/FightDamageListener` (lethal intercept, countdown/result immunity, fighter-vs-fighter PvP allow), `listener/FightContainmentListener` (move, teleport, tick check), `listener/FightCommandListener`, `listener/FightSessionListener` (quit, join eject, respawn, death fallback), `listener/FightWantedGuard`, `listener/FightGangDeleteListener` | guards |
| `database/FightStatsRepository` + `FightStatsTable`, `database/FightRecordRepository` + `FightRecordTable` (module `database` package, scanned via the module loader; `@TempDir(cleanup = NEVER)` test rule applies) | stats and audit |
| `placeholder/FightPlaceholderContribution` (api `PlaceholderContribution`) | `%gang_fight-wins%` and friends, without gang knowing fights |
| resources: `module.yml`, `commands.json`, `fight/fight_rules.yml`, `fight/fight_messages.yml`, `fight/arenas.yml` (empty `Arenas:` block) | YAML rule: defaults live in the module jar at the data-folder path |

`Host_Api: 2.0` is correct because v1 reads only existing api/gang members (Keystone 1.9.2 minor floor).

---

## 3. Arena definition UX

### Reuse the turf wand? No.

Taking `Depends: [turf]` for a wand would (a) drag `civilians` and therefore Bartizan in (turf `Depends: [civilians]`, civilians
`Plugins: [Bartizan]`), making fights unloadable on a Bartizan-less server, and (b) hand us an X/Z-only region
(`CuboidRegion` ignores Y) when an arena needs height (multi-storey buildings, pits). The rule "generic primitives go upstream to
Keystone, never forked" is respected: what fights needs is not the turf wand but a 3D arena draft, which is domain data,
not a reusable primitive. The one reusable piece, drawing a particle outline of a box, is ~40 lines; keep it local with a
`ponytail:` comment, and put a `BoxOutline` in the already-parked Keystone 1.15.0 lane if a second consumer (turf map) wants
it (PLAN.md section 2 promotion rule).

### Commands (admin, root `/glw arena`, console never; nodes `gangland.command.arena.*`, OP by default)

Chained `OptionalArgument` nodes with tab completion (feedback rule); each has a `commands.json` entry in the module jar.

| Command | Effect |
|---|---|
| `arena create <name>` | starts an in-memory `ArenaDraft` for that admin; one word, unique, validated |
| `arena corner <first\|second>` | sets a corner to the admin's **block position** (Y matters); cross-world resets the draft |
| `arena spawn <a\|b>` | appends the admin's exact position (with yaw/pitch) to that side; call again for more spawn points |
| `arena exit` | sets the Exit point (where anyone is put when a fight ends or is found stranded) |
| `arena size <n>` | maximum team size (default `Max_Team_Size` from rules) |
| `arena save` | validates then writes `fight/arenas.yml` atomically and registers the arena live |
| `arena show <name>` | particle outline for the admin for ~10 s (XParticle; no raw `Particle`) |
| `arena list`, `arena tp <name>`, `arena on\|off <name>`, `arena delete <name>`, `arena reload` | management; `reload` re-reads the file (hand edits) |

`arena save` validation: both corners set and same world; world loaded; volume >= `Min_Arena_Volume`; every spawn and the Exit
inside the box (Exit may also be outside: it is where people are sent *to*, so allow either, but it must be in a loaded
world); no overlap with another arena's box (overlap would let one fight bleed into another); at least one spawn per side;
`size` >= 1. A failing check names the exact problem (one sentence, the same wording everywhere).

### Storage: YAML data file, not the database

`plugins/Gangland_Warfare/fight/arenas.yml`, block-style, `Capitalized_Underscore` keys:

```yaml
Arenas:
  Docks:
    World: world
    Corner_One:
      X: 423
      Y: 3
      Z: 761
    Corner_Two:
      X: 433
      Y: 9
      Z: 784
    Max_Team_Size: 4
    Enabled: true
    Spawns:
      A:
        - X: 428.5
          Y: 3
          Z: 764.0
          Yaw: 0
          Pitch: 0
      B:
        - X: 428.5
          Y: 3
          Z: 781.5
          Yaw: 180
          Pitch: 0
    Exit:
      X: 410.5
      Y: 3
      Z: 770.5
```

Reasons: an arena is bound to a world that exists on *this* server, whereas the gang database may be shared through MySQL by
several servers, so per-server file is the correct scope; a hand-editable file matches what admins of the reference plugin
already do; no schema, no migration; a handful of rows. Wins, losses and the audit trail are cross-server data and stay in
the database (section 8). (Turfs are in the DB because their owner is a DB row; arenas have no such tie.)

Beyond the reference plugin: N spawns per side (spread fighters), Y bounds, an Exit point, per-arena size cap and enable flag,
hot reload, and a command flow so no admin has to read F3 coordinates.

---

## 4. State machine

```
            challenge          accept              join window ends            countdown ends
NONE ─────► PENDING ─────────► LOBBY ─────────────► COUNTDOWN ─────────────────► FIGHT
              │ decline/expire   │ cancel/short        │ side falls below min          │ one side has no one alive
              ▼                  ▼                     ▼                               │ or Max_Duration reached
           (refund none)     CANCELLED (refund)    CANCELLED (refund)                  ▼
                                                                                    RESULT ──► RESTORE ──► DONE
```

| State | Entry | What is true | Exit |
|---|---|---|---|
| **PENDING** | `gang fight challenge <gang> <size> [bet]` by a member holding the fight gate | nothing reserved; the target gang's leaders (and holders of the node) online get a clickable accept/decline line; expires after `Challenge_Expiry_Seconds` (90) | `accept` / `decline` / expiry / either gang gone or allied meanwhile |
| **LOBBY** | `accept [gang]` by a member of the target gang holding the gate | both banks re-checked and **both bets withdrawn into the fight's escrow**; arena reserved (challenger's choice or first free arena whose `Max_Team_Size` >= size); members `join` for `Join_Window_Seconds` (60); with `Require_Equal_Teams` (default true) each side must reach exactly `size`, otherwise the smaller side count, minimum `Min_Team_Size` | window ends with enough on both sides -> COUNTDOWN; else CANCELLED with full refund and a `No_Show` note on the side that failed to field a team (feeds the rematch cooldown) |
| **COUNTDOWN** | `Countdown_Seconds` (10) | each fighter's `PlayerSnapshot` is taken (location, game mode, health, food, fire ticks, potion effects, flight) then they are teleported to their side's spawns (round-robin); frozen in place (position locked, look allowed); all damage cancelled; titles and `SoundEffect` ticks | -> FIGHT |
| **FIGHT** | | fighters are alive or eliminated; only fighter-vs-opposing-fighter damage lands; containment and command blocking active; `Max_Duration_Minutes` (10) timer | one side has no living fighter, or timeout -> RESULT |
| **RESULT** | winner decided; **settlement runs here, once, synchronously** (section 6) | everyone frozen and immune for `Result_Seconds` (5); title with the outcome; stats and record written | -> RESTORE |
| **RESTORE** | | every remaining fighter gets their snapshot back and is teleported to their pre-fight location (Exit if that location is gone or the world is not loaded); registry entries removed; the arena frees after `Arena_Cooldown_Seconds` (5); rematch cooldown stamped | DONE |

Winner rules: last side with a living fighter wins. On timeout, `Timeout_Result` = `MOST_ALIVE` (default; tie is a draw) or
`DRAW`. A draw refunds both bets and records no win or loss. `NOT_ENOUGH_PLAYERS` and `ABORTED` never count as fights.

Elimination sources (all funnel into one `Fight.eliminate(participant, reason)`): lethal damage, quitting, leaving the arena
box after the warning budget, `/glw gang fight leave` after the fight has started, the gang being disbanded (section 7).

Timers are plain synchronous Bukkit scheduler tasks or Keystone `RepeatingTimer.start(false)`; never `start(true)` (async, the
feedback rule), because every step teleports or touches entities.

### Command surface (player verbs under `/glw gang fight`, via `FightContribution`)

`challenge <gang> <size> [bet]` (optional `arena <name>` as a trailing pair), `accept [gang]`, `decline [gang]`,
`join`, `leave`, `info` (the fight you are in or the pending one), `arenas` (free and busy, with max size), `stats [gang]`.
`accept`/`decline` take the challenging gang's *name* with tab completion, never a numeric id (the UX audit flagged numeric ids).
Player-facing vocabulary: "fight", "challenge", "bet", "side", "arena", "eliminated"; the same word everywhere.

Gates: `challenge`, `accept`, `decline` need `GangPermissions.allows(member, player, FightPermissions.MANAGE)` where the module
constant is `gangland.gang.fight` (importable because of `Depends: [gang]`; the leader always passes through the top-rank
clause in `GangPermissions.allows`, and W1-R's 3-rank preset lets an officer be granted it). `join`, `leave`, `info`,
`arenas`, `stats` need only gang membership. Staff bypass node for commands: `gangland.gang.fight.bypass_commands`.
W1's `plugin.yml` `permissions:` block (CM-37, player verbs default `true`) must list the player verbs; the admin `arena` root
stays OP.

---

## 5. Inventories, deaths, respawns, and the systems fights must not disturb

### 5.1 The interception design (the central decision)

A single handler in `FightDamageListener`:

```
@EventHandler(priority = NORMAL, ignoreCancelled = true)
onDamage(EntityDamageEvent e):
    fighter = participant of e.getEntity() during FIGHT   // O(1) map lookup, return early for everyone else
    if state == COUNTDOWN or RESULT: cancel; return
    if health - e.getFinalDamage() > 0: return             // non-lethal: let it through
    e.setCancelled(true); fight.eliminate(fighter, reason)  // heal, clear fire/effects, teleport out, restore
```

Listen to the base `EntityDamageEvent`, not only `ByEntity`, so fall, fire, void, poison, explosion and Bartizan custom
impact handlers (fire ticks, biological clouds) are all covered. Priority `NORMAL` is strictly below `EntityDamageListener`
(HIGH) and `CustomPlayerDeathListener` (HIGHEST), both `ignoreCancelled = true`, so neither ever sees the lethal hit.

| Concern | Result of the intercept |
|---|---|
| **Kill credit / stats** | `User.kills` is not touched (verified: `setKills` only in `handlePlayerKills`). Fight kills, deaths and assists are the module's own numbers (a per-fight last-damager map, assist = damage within the last `Assist_Window_Seconds` 8). |
| **Wanted level / cops** | The lethal-hit path that calls `handleWanted` or the kill-combo `recordKill` never runs. Defence in depth: `FightWantedGuard` cancels any *raise* in `WantedLevelChangeEvent` for a fighter while they are in a fight (cancellation is honoured, `Wanted.setLevel` L65-70). Fighters are refused entry while `user.getWanted().isWanted()`, so no cop hunt is running when the fight starts. |
| **Bounty** | No claim and no auto-bounty on a fight death. |
| **Inventory / money drops** | Nothing dies, so `dropInventoryIfAllowed` never runs and no `PlayerDeathEvent` fires. Fighters keep everything. This is the design ("keep inventory in fights"), not a gamerule dependency. |
| **Downed state / respawn** | Never entered. A fighter skips the downed animation; that is the deliberate trade, stated in the docs. Fighters who are already downed (`DownedPlayerRegistry.isDowned`, `gangland-core`) are refused entry. |
| **Bartizan** | Default pipeline uses `living.damage` so it flows through the same event; a cancelled lethal hit reads as "blocked" to the raytracer and skips its post-damage effects, harmless. No `bartizan-api` import, no `Plugins:` line. Weapons a fighter carries keep working; ammo and durability are the fighter's own gear. |
| **Totems of Undying** | Ignored inside a fight (the lethal check fires before vanilla's totem step). Documented; a `Respect_Totems` knob is not worth a branch in v1. |

### 5.2 Fallbacks for deaths that skip `EntityDamageEvent`

`setHealth(0)` by another plugin fires no damage event. `FightSessionListener` handles `PlayerDeathEvent` (LOW) for a live
fighter: `setKeepInventory(true)`, `setKeepLevel(true)`, clear drops and dropped XP, eliminate; and `PlayerRespawnEvent`
sets the respawn location to the arena Exit. This is rare and only keeps the world clean; the wanted and drop guarantees
above come from the intercept.

### 5.3 Civilians, cops, turf

| System | Rule |
|---|---|
| **Wanted and cops** | Above. Fights never raise wanted; cops never spawn because of a fight. |
| **Civilians** | Civilian spawn groups are admin-placed (`CivilianSpawnGroupCommand`). Rule for admins, stated in the arena docs and in `arena save` output: do not build an arena over a civilian spawn group. There is no code coupling (an overlap check would need a civilians dependency for one warning). A stray hit on a civilian that would raise wanted is caught by the wanted guard. |
| **Turf** | Turf presence is location based, so a fighter standing inside a turf's footprint could count as an attacker or defender. v1 rule: place arenas outside turf footprints, or in a world that has none (documented, and `arena save` cannot check it without `Depends: [turf]`). The robust fix is the staged read seam in section 8 plus a one-line guard in `GT/task/TurfLocationTracker` (owned by W2-R; a small lane after W2-R merges). No capture, income, contribution or wake-up effect is triggered by anything the module does. |
| **Cuffed or jailed players** | There is no api-level restraint seam (grep of `gangland-api` and `gangland-core`: none), and fights must not take `Depends: [cops-n-crooks]`. v1: fighters must be in `SURVIVAL` or `ADVENTURE` mode, not downed, not wanted; a cuffed player cannot type the join command in practice, and the module's teleport does not un-cuff. The staged seam (section 8) adds a cancellable eligibility event cops can veto. |
| **Vehicles and gadgets** | Arena flag `Allow_Vehicles` (default false) dismounts on entry; jetpacks are covered by the containment tick check (section 5.4). |

### 5.4 Holding players inside

Enforcement is layered, all for fighters only (an `O(1)` `Map<UUID, Participant>` lookup first, so the cost on everyone else is
a single miss):

1. `PlayerMoveEvent`: when the destination *block* differs from the source and is outside `ArenaBox`, cancel by
   `setTo(getFrom())`.
2. `PlayerTeleportEvent`: cancel if the target is outside the box, **unless** the fight itself is teleporting that player (a
   per-player `expectTeleport` set, because `TeleportCause.PLUGIN` cannot tell our teleport from a waypoint's). This also
   stops ender pearls, chorus fruit, `/tpa` accepted by someone else, and Gangland waypoints.
3. A 5-tick check task: any fighter found outside (elytra, riptide, vehicle, knockback across the edge, a plugin `setLocation`
   with no event) is put back at their side's spawn and warned; after `Max_Leave_Warnings` (3) the fighter is eliminated
   for leaving.
4. `PlayerPortalEvent` cancelled for fighters.
5. Optional `Protect_Blocks` (default true): `BlockBreakEvent`/`BlockPlaceEvent` by fighters cancelled inside the box.

### 5.5 Commands during a fight

`FightCommandListener` on `PlayerCommandPreprocessEvent` (HIGHEST so it runs after other plugins rewrite the message; precedent
`DetainmentListener.onCommand` L270), for every fighter in LOBBY, COUNTDOWN, FIGHT and RESULT. Config: `Command_Block_Mode`
= `ALL` (default) or `LISTED`, with `Blocked_Commands` and an `Allowed_Commands` allowlist (defaults: `glw gang fight leave`,
`glw gang fight info`, `msg`, `r`). Normalisation is the part that matters: lowercase, strip the leading `/`, take the first
token, **strip any `plugin:` namespace prefix** (`minecraft:tp` must not bypass a `tp` block), and resolve aliases through the
command map (`Bukkit.getPluginCommand`/`SimpleCommandMap` canonical name). `gangland.gang.fight.bypass_commands` skips the
check. Blocked attempts get the one-sentence message "Commands are disabled during a fight; leave with the fight leave verb".
This covers commands only; console and command blocks are not fighters.

---

## 6. Bets, escrow, settlement

**Model: hard escrow in memory, single settlement transfer.** Chosen over a "reservation" model because a reservation cannot stop
a leader from `gang withdraw`-ing the bet away mid-fight, nor turf's Quartermaster spending it (turf calls
`gang.getEconomy().withdrawAmount` directly, verified in the census), whereas an escrowed amount is simply not in the bank.

| Step | Action |
|---|---|
| Challenge | `bet` validated: `0 <= bet <= Max_Bet`, `bet <= Max_Bet_Percent_Of_Bank` (50) of the challenger's bank, and the bank currently covers it. |
| Accept | Re-check both banks; withdraw both bets **in one method** on the main thread: withdraw A, withdraw B, and if B throws `EconomyException` deposit A back and cancel. Amounts held as two `BigDecimal` in the `Fight`. |
| Result | `FightSettlement.settle(winner, loser)`: winner's bank receives `2 x bet` minus `House_Cut_Percent` (default 0, a sink for servers that want one). Winner deposit respects `Gang.Account.Maximum_Balance`; any overflow returns to the loser. |
| Draw, decline, expiry, cancel, abort, lobby shortfall | Each escrow returns to its own gang, unchanged. |
| Server stop | `FightsModule.onDisabled()` calls `FightManager.abortAll(SERVER_STOP)`: refund both banks, teleport every fighter home and restore snapshots, no stats. `ShutdownSequence` runs module disable *before* the final save (`IMPL/Gangland.java` L84-86), so the refunded balances are persisted by the flush that follows. |
| Hard crash | Escrow is in memory, so a crash loses it. Fighters found inside an arena on next join are ejected to the arena Exit (section 7) so nobody is stranded. Recorded ceiling: `// ponytail: in-memory escrow, a crash mid-fight loses the bets; write a fight_escrow row (debit, save gang rows, then journal) if a playtest ever loses money.` |

Money never appears from nowhere: a fight is a zero-sum bank-to-bank transfer (plus an optional sink). Members can already
move gang money with `gang withdraw`, so the residual abuse is not the transfer itself but ranked-stat farming (section 7).

Persistence of the balances after settlement rides the normal autosave (`PeriodicalUpdates` upsert of the gang rows); no
special save call is added in v1.

---

## 7. Disconnects, disbands, anti-abuse

### Disconnect and lifecycle edge cases

| Case | Handling |
|---|---|
| Fighter quits mid-fight | `FightSessionListener` on `PlayerQuitEvent` (LOWEST, player still online): eliminate, restore snapshot, teleport to pre-fight location, so the state written to `player.dat` is the normal one (precedent `restoreDownedState`). A whole side empty -> the other side wins. |
| Fighter kicked, or server crash, while inside | On `PlayerJoinEvent`, a player standing inside any arena box who is not a live participant is teleported to that arena's Exit. Zero persistence needed. |
| Quit during LOBBY or COUNTDOWN | Removed from the roster; if a side drops below the minimum before COUNTDOWN ends, CANCELLED with refund. |
| Challenger side empty before accept | Challenge dropped (no escrow yet). |
| Gang disbanded mid-fight | `FightGangDeleteListener` on `GangDeleteEvent` (mail's pattern): the fight aborts as a **forfeit**, the surviving gang wins and receives the disbanded gang's escrow; if the survivor is the one disbanded-against, normal. Prevents disband-to-escape. `gang delete` splits the *vault*, and the escrow is already out of it, so nothing is paid twice. Stats rows of a deleted gang are removed (ids are random ints and can be reused). |
| Member kicked from the gang mid-fight | Eliminated (the gang-membership check fails). |
| `/glw reload` | Managed reload (`context.reloadBeans()`) does not carry a fight across; `abortAll(RELOAD)` on the module bean shutdown. Module install, update and remove are restart-only anyway. |
| Arena world unloaded | Fight aborts with refund, arena marked disabled and reported once through `Diagnostics`. |

### Anti-abuse

| Threat | Control (all numbers in `fight_rules.yml`) |
|---|---|
| **Alt-gang WLR farming** | A fight is **ranked** only when `bet >= Ranked_Min_Bet` and every fighter's `Member.gangJoinDateLong` is at least `Ranked_Min_Member_Age_Hours` (24; the same number and idea as the frozen D1 qualifying-member rule, but its own key because turf's file is not readable from another module) and teams are equal. Otherwise it is a sparring fight: winner takes the bet, no stat change. |
| Repeated pair | `Rematch_Cooldown_Minutes` (30) per gang pair; `Ranked_Per_Pair_Per_Day` (3), after which the fight is unranked; both read from the `FightRecordRepository`. |
| One-sided money funnel via bets | Cap `Max_Bet_Percent_Of_Bank` (50) plus `Max_Net_Transfer_Per_Pair_Per_Day` measured from records; beyond it, the challenge is refused with the number in the sentence. |
| **Bet laundering between allies** | **Allied gangs cannot fight** (`gangsAllied` either direction; after W0-C `Gang.isAlly` is symmetric, until then check both directions). Independent reason: `GangMembersDamageListener` and `GangAllyWeaponImpactListener` would make an allied fight deal no damage anyway. A gang could unally, fight, re-ally; the pair caps above bound that, and the alliance abandon path is the only place to add a cool-off if a playtest shows it (`GangAlliance.since` exists but a break time does not). |
| Throwing a fight | Same controls; the money moves bank to bank between two non-allied gangs, capped per pair per day. |
| Griefing challenges | One outgoing challenge per gang, one pending per pair, expiry, a `Decline_Cooldown_Seconds` so a declined gang is not re-challenged instantly. |
| Fighting to dodge a debt or a hunt | Wanted or downed players cannot join (section 5). |
| Same-household false positives | An optional `Block_Same_Address` (default off) refuses ranked fights where opposing fighters share an IP; off by default because shared households and CGNAT are common. |

---

## 8. Stats (GP-20) and how the rest of the plugin sees them

Module-owned tables, so the gang schema is untouched:

- `fight_stats` (gang id PK: wins, losses, draws, fight kills, fight deaths, assists, net winnings, last fight time),
- `fight_player_stats` (uuid PK: kills, deaths, assists, fights, wins),
- `fight_record` (id, challenger gang, target gang, winner or none, team size, bet, ranked flag, result, started, ended).

`WLR` = wins / max(1, losses), computed on read. Exposure without a gang-to-fights dependency: `FightPlaceholderContribution`
(api `PlaceholderContribution`) supplies `%gang_fight-wins%`, `%gang_fight-losses%`, `%gang_fight-wlr%`, `%gang_fight-kills%`,
`%gang_fight-deaths%`, `%gang_fight-earnings%` and the player-side family, plus `fight stats [gang]` in chat.

**Staged for v1.1 (not v1):** the leaderboard lane (GP-21, sortable by WLR) and `gang info` (GP-04) live in `gangland-gang`
and need fight numbers, and turf presence wants "is this player fighting". Both are served by ONE additive api read seam in
`gangland-api/data/gang/`: a `GangFights` holder (installed by the fights module at `@PostConstruct`, inert when absent,
exactly like `GangMembership`) with `isFighting(UUID)`, `wins(int)`, `losses(int)`, plus a cancellable
`FightEligibilityEvent` that cops-n-crooks can veto for a restrained player. That is the `GanglandApi.VERSION` minor bump
(2.0 -> 2.1, or whatever the merged master carries) and is exactly why v1 ships without it: v1 needs no api change, and the
seam should be built once for its real consumers, in the same wave as the leaderboard.

---

## 9. Alternative: "turf duels" that reuse turf capture

Idea: a gang challenges another to a duel over a turf; the capture engine (presence, bar, guards) is the fight.

| Criterion | Arena fights (recommended) | Turf duels |
|---|---|---|
| Parity (GP-29..31) | Full: team size, money bet, accept/decline, join/leave, multiple arenas, held in, command block, equal teams, winner takes bet | Misses most of it: no fixed rosters, no containment, no bet flow, no command block; capture is open to every player in the zone |
| Fit with the frozen plan | Orthogonal: touches none of the W0-W4 hot files | Adds a consensual-declaration layer on `CaptureService` (owned by W2-R, the opus lane) and on `CaptureEligibility`; PLAN.md section 1 "Dropped" and section 12 cut declared raids with fees, warnings, windows and persistent wars after four reviews, and a turf duel is a declared raid |
| Wanted, cops, civilians, guards | Isolated by construction (section 5) | All of them are live in the turf zone: wanted from kills, cop dispatch, civilian and guard NPCs, other gangs walking in |
| Stakes | Bank bet | Real territory loss; big blast radius when tuned wrong, and D1/D2 (owners online, caps by level) already decide who can take turf |
| Fair stats | Clean per-fight record, WLR credible | A capture is not a fight: no roster, so wins, losses and assists cannot be attributed |
| Cost | ~30 classes in one new module, no risk to existing code | Small code, high coupling and regression risk in capture rules |

**Recommendation: arena fights.** Keep "turf as a stake" as a possible later `Stake_Type: TURF` on the same challenge
flow (the winner takes ownership through the turf module's existing owner-change path), gated on W2-R being merged and an
owner decision; it is an extension of this engine, not an alternative to it.

---

## 10. Delivery: lanes, tests, shared files

Wave placement: a separate fights wave (call it **F**), on its own branch from master, independent of W0-W4. Prerequisites that
already exist in the plan and are needed at *release* time, not for coding: W0-C (symmetric `Gang.isAlly`), W1's `plugin.yml`
`permissions:` block (CM-37) and W1-R's 3-rank preset (so an officer can hold the fight node), and the `*ModuleFileConfig`
pattern (W1 kickoff copies `TurfModuleFileConfig` for gang; fights copies it too).

| Lane | Model | Scope | Owns |
|---|---|---|---|
| F-K kickoff | sonnet + haiku YAML | module skeleton and pom, `module.yml`, `FightsModuleFileConfig`/`Files`, `fight_rules.yml`, `fight_messages.yml`, empty `arenas.yml`, `FightContribution` and `ArenaCommand` stubs, `ModuleInstalls.OFFICIAL` id (+ its test), `gangland-features/pom.xml` and `gangland-build/pom.xml` lines | new module dir; the three shared lines |
| F-A arenas | sonnet | `Arena`, `ArenaBox`, `ArenaRegistry`, `ArenaFile`, `ArenaDraft`, the `arena` commands, outline | `arena/**`, `command/arena/**` |
| F-S state machine | sonnet | `Fight`, states, `FightManager`, `FightRules`, `FightClock`, `PlayerSnapshot`, the player `fight` commands, expiry and lobby | `fight/**`, `command/fight/**` |
| F-G guards | **opus** (the one adversarial lane: event ordering and bypasses) | the six listeners: damage intercept, containment, command filter, session (quit, join eject, death fallback), wanted guard, gang-delete | `listener/**` |
| F-E economy and stats | sonnet | `FightSettlement`, escrow, ranked rules, the two repositories and tables, placeholder contribution, anti-abuse rules | `database/**`, `placeholder/**`, settlement |
| F-D docs | haiku | `documentation/features/fights.md`, arena how-to, migration note | docs |

Opus concurrency: 1. Merge order F-K -> F-A, F-S, F-E (parallel) -> F-G -> F-D.

Tests (JUnit 5 + Mockito; DB tests follow the `@TempDir(cleanup = CleanupMode.NEVER)` + `releaseDbFiles` rule; no pom edits
beyond F-K): `FightRulesTest` (challenge matrix: allied, same gang, size, bet caps, rank gate, wanted, downed, member age,
ranked decision), `FightStateMachineTest` with a fake `FightClock` (every transition and every cancel refunds), `FightSettlementTest`
using real `EconomyHandler` instances (win, draw, cap overflow, second withdraw throws -> first refunded), `ArenaBoxTest`
(inclusive edges, negative coords, Y), `ArenaFileTest` round trip and a hand-edited malformed file, `CommandFilterTest`
(`/MINECRAFT:TP`, aliases, allowlist, bypass), `FightDamageListenerTest` (lethal hit cancelled and eliminated, non-lethal
passes, countdown immune, non-fighter untouched), `FightWantedGuardTest`, `FightSessionListenerTest` (quit restores, join
eject), `FightGangDeleteListenerTest` (forfeit pays the survivor the escrow), `FightStatsRepositoryTest`,
`FightCommandsJsonParityTest` (registered sub-commands vs the module's `commands.json`, same idea as the planned
`GangCommandsJsonParityTest`, which is a NEW W0-C deliverable, not an existing test). New tests are green-from-new; the one
behavioural claim that no unit test can prove is the cross-plugin event ordering (intercept before `HIGH`/`HIGHEST`
listeners), so the F-G exit needs a **manual two-account check on the Spigot test server** (`smoke.py` is console-only and
cannot drive PvP): lethal hit by hand and by a Bartizan weapon, then confirm wanted stays 0, no drop, no downed state.

Shared/core files touched (orchestrator-owned per PLAN.md section 7.1): `gangland-features/pom.xml` (one `<module>`),
`gangland-build/pom.xml` (jar copy), `IMPL/command/sub/module/ModuleInstalls.java` + `ModuleInstallsTest`,
`gangland-impl/src/main/resources/plugin.yml` (player-verb `permissions:` entries), CLAUDE.md module table (main checkout,
orchestrator). The module's own `commands.json` is its own file, so the merge-conflict hot spot in 7.1 is not involved.

---

## 11. Decisions for the owner and docket rows

| Id | Question | Options | Recommended |
|---|---|---|---|
| DF1 | Where do fights live? | A new module `gangland-gang-fights`. B inside `gangland-gang`. | **A** |
| DF2 | May allied gangs fight? | A never (also prevents bet laundering). B allowed for bet 0 sparring (needs an uncancel path past two damage cancels). | **A** |
| DF3 | Ranked rules | A bet >= minimum, equal teams, 24 h members, pair caps. B every fight counts. | **A** |
| DF4 | Equal teams | A default on (exactly `size` each side). B allow unequal up to `size`. | **A**, one config key |
| DF5 | Timeout | A `MOST_ALIVE` (tie draws). B always a draw. | **A** |
| DF6 | Eliminated fighters | A returned home at once. B spectate inside the arena (needs spectator containment). | **A** in v1, B later |
| DF7 | Stake types | A money only in v1; turf-as-stake later. | **A** |
| DF8 | Arena placement vs turf | A admin rule in v1, robust guard with the v1.1 api seam. B ship the api seam with v1. | **A** |

Docket triage rows to add (`triage/*.txt`, ids at triage time; last used are GR-38 and CM-37): (1) `ModuleInstalls.OFFICIAL`
omits the real `gang` and `lootchest` modules and states "six ids" (`IMPL/command/sub/module/ModuleInstalls.java` L24-31);
(2) `EntityDamageListener.handleWanted` (L206) increments the bounty from `wanted.getLevel()` after `incrementLevel()` even when
a `WantedLevelChangeEvent` listener cancelled the change, so a cancelled raise can still move the bounty (to be confirmed
against `Bounty.getAutoBountyIncrease` before filing as a bug; this study relies on the lethal intercept, not on that guard);
(3) observation, not a bug: `CustomPlayerDeathListener` cancels lethal damage before vanilla's totem step, so a player with a
totem is downed instead of saved. Nothing in this study depends on rows 2-3 being fixed.
