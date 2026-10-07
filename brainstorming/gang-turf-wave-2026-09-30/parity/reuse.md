# Parity pass: what already exists and must be reused (2026-09-30)

Scope: Gangland master @ ff9d813f (rev 0.12.0), Keystone sibling repo. Purpose: for the Gangs+ parity ids GP-01..GP-45
(`competitors/gangsplus.md`), list the existing code a parity lane must build on instead of re-inventing, and the exact gap
left over. Planning only; no product code was touched. Paths are repo-relative unless they start with `Keystone/`.

Method: `graphify query` was run first (WantedKillTracker, WaypointLookupContract, PlaceholderService,
GangPlaceholderContribution, LocalizedModuleYaml; `AsyncPlayerChatEvent` and `ActionBarManager` returned no node, which
agreed with the greps). `graphify-out/graph.json` was written 2026-09-29 23:08, the newest commit is 23:05 the same day, so
the graph is fresh. Every fact below was then read from source. `.claude/worktrees/**` holds stale copies of the tree and was
excluded from every grep. Classes marked NEW do not exist.

Ground rules carried in (from the task and `PLAN.md`): Spigot only, 1.16.5 compile floor, Java 17; modules compile against
`gangland-api` only; `PLAN.md` line 94 says "no new api surface in W0-W3", so section 10 lists every gap that would need an
additive `gangland-api` change so the planner can either schedule it (W4+ / a dedicated api lane) or route around it. Note that
`GanglandApi.VERSION` is `"2.0"` (CLAUDE.md still says `Host_Api: 1.0`).

## 0. Verdict table

| Capability | Reuse verdict | Biggest gap |
|---|---|---|
| Kills / deaths / KDR (GP-20, 22) | Reuse `User` counters, both user caches; gang KDR is a sum over members | No assists, no fights won/lost, no per-world gate, no PvP kill event |
| Homes / teleport (GP-26..28, 42) | Reuse `Waypoint` + `WaypointTeleport` (both in api) for warmup, cooldown, shield, cost, veto event | No safe-location check, no write seam, names are global, no disband cleanup |
| Chat (GP-13..16) | Nothing exists: zero chat listeners | Whole pipeline is NEW; `HashMap` managers are not thread safe for async chat |
| Placeholders (GP-15, 34) | Reuse `PlaceholderContribution` for anything player-scoped | Leaderboard tokens need a null-player path the seam does not have |
| Confirmation (GP-01, 03, 20-ish admin resets) | Reuse Keystone `ConfirmArgument` | Three idioms coexist already |
| Titles / action bar (GP-36) | `ChatUtil.sendTitle`, `ActionBarManager` | Nothing missing |
| World disable (GP-18, 22, 32) | None: greenfield | New setting and a shared predicate |
| GUIs (GP-39, 21) | Reuse YAML menus + `Item_Source` and/or keystone-inventory `Panel`/`PagedRegion` | `gang_stat.yml` is an empty shell; no profile menu exists |
| Module messages / YAML (GP-36) | Reuse `LocalizedModuleYaml` | Gang and mail have no module YAML plumbing yet; only `en`/`es` |
| Combat tag / logout in combat (GP-33) | None | No last-damage store anywhere |
| WorldGuard (GP-18 flag) | None | Zero references; flag registration has a lifecycle constraint |
| Arena regions (GP-30) | Partial: `Region`/`CuboidRegion` shape and the wand pattern | X/Z only, turf-private, no 3D, no arena registry |

---

## 1. Kill, death and assist tracking; where gang stats live

### 1.1 What exists

- **Per-player counters live on the core `User`.** `gangland-core/src/main/java/org/luckyraven/gangland/core/user/User.java:44`
  declares `int kills, deaths, mobKills, gangId`; `getKillDeathRatio()` (L134-136) is `kills / max(deaths, 1)`.
  Persisted in table `user` (`gangland-impl/.../database/tables/player/UserTable.java`, columns `kills`, `deaths`, `mob_kills`)
  and already exposed as PlaceholderAPI tokens `user_kills`, `user_deaths`, `user_mob-kills`, `user_kd`
  (`gangland-impl/.../data/placeholder/worker/GanglandPlaceholder.java:215-218`).
- **Kill credit** is given in `gangland-impl/.../listener/player/EntityDamageListener.java`:
  `onPlayerEntityDeath` (L61-98, `EventPriority.HIGH`, `ignoreCancelled = true`) decides "this hit is lethal" by comparing
  `getHealth()` to `getFinalDamage()` (L76-78), skips a victim already in `DownedPlayerRegistry` (L81), and for a real player
  victim runs `handlePlayerKills` which does `damagerUser.setKills(+1)` at **L121**. The damager may be the player or a
  projectile shooter (L64-68). The same method also pays the victim's bounty (L126-137), otherwise raises the killer's own
  bounty via `handleBounty` (L142), and raises wanted via `handleWanted` / the kill combo (L145-148).
- **Death credit** is `user.setDeaths(+1)` in `PlayerDeathListener.java` at **L79** (`PlayerDeathEvent`, LOWEST) and **L107**
  (`PlayerDownedEvent`), with a 500 ms de-dup map (L36, L63-72) because both paths can fire for one death. Citizens NPCs are
  skipped (`NpcSupport.isNpc`, L54).
- **Both user caches are complete and share stats.** `PlayerBootstrapService.loadOfflinePlayers` (L116-140) loads the whole
  `user` table into the `@Qualifier("offline") UserManager<OfflinePlayer>` at boot; `RemoveAccountListener` (L84-100) copies
  `kills`, `deaths`, `mobKills`, `gangId`, balance, level, bounty and bank from the online `User` into the offline cache on
  quit. So `UserManager.getUser(UUID)` on the online bean, falling back to the offline bean, answers for **any** member, online
  or not. `UserManager` also has `getUsers()` (unmodifiable map) for scans.
- **Gang membership is enumerable.** `MemberManager.getMembers()` (`Map<UUID, Member>`, unmodifiable),
  `Gang.getMembers()` (copy), `Gang.getOnlineMembers(userLookup)` (`Gang.java:175`, iterates every online player), and each
  `Member`/`User` carries the gang id (`User.gangId`, set by `Gang.addMember`, `Gang.java:124`).
- **Members' money** (the "members' total money" leaderboard sort, GP-21/34) is `user.getEconomy().getAmount()` per member,
  same lookup as above.

### 1.2 So: where would gang stats live

- **Gang kills, deaths, KDR, "members' money"**: derive; no table. Sum `User.kills/deaths` over the current members of a gang,
  resolving each UUID against the online then the offline `UserManager`. Cost is O(members) per gang; for a `/g top`-style list
  or a `top_<stat>_<n>_<prop>` placeholder cache a snapshot on a timer (precedent for a periodic task:
  `gangland-impl/.../bootstrap/PeriodicalUpdates.java`, `RepeatingTimer`; module precedent `TurfLocationTracker`,
  `InactivityReleaseTask`) instead of recomputing per request.
- **Caveat to state in the parity plan**: a derived sum is "current members only". A player who leaves or is kicked takes
  their kills and deaths out of the gang's history. If the owner wants a durable gang record (Gangs+ keeps gang-level
  stats), the gang needs its own counters.
- **Fights won/lost, WLR, assists, anything gang-scoped and durable**: NEW columns on the module's `gang` table
  (`gangland-features/gangland-gang/.../gang/database/GangTable` + `GangRepository`, the census table in `census/gang.md` s1).
  The `Gang` entity already carries persisted `level`, `experience`, `bounty`, `state`; adding `fightsWon`, `fightsLost`,
  `kills`, `deaths`, `assists` follows the same path (column, `Gang` field, `GangRepository` parse, placeholder). The
  `MemberTable` (`member` table: `contribution`, `join_date`, `rank_id`) is where a per-member gang-scoped kill counter would go
  if "kills earned while in this gang" is wanted (avoids the leaver-takes-history problem).
- **A player profile (`/g player`, GP-20)** needs no new store beyond the above: `User` counters, `Member` rank and join date,
  `Wanted`, `Bounty`, `Level`, all already present.

### 1.3 The gap, exactly

1. **No assist tracking exists anywhere.** Grep for `lastDamage|lastHit|lastAttack|recentlyDamaged|damageTracker` in core,
   impl, api and all modules finds only `Player.getLastDamageCause()` (cosmetic death message,
   `PlayerDeathListener.java:173`) and the civilian NPC's `lastAttackerLocation`. There is no per-victim map of "who hurt me in
   the last N seconds". Assists and combat tagging (section 6) are the same NEW data structure: a damage ledger recorded at
   `EventPriority.MONITOR`, `ignoreCancelled = true` on `EntityDamageByEntityEvent`, read at kill time.
2. **No kill event.** `EntityDamageListener` mutates `User` directly; nothing fires a `PlayerKillEvent`-style event, so a
   downstream module (gang stats, fights, achievements) cannot subscribe. The cheapest hook is the ledger above plus a NEW
   event fired next to L121; alternatively listen to `PlayerDownedEvent` / `PlayerDeathEvent`.
3. **`WantedKillTracker` (`gangland-core/.../wanted/WantedKillTracker.java`, `WantedKillTrackers.java`) is the wrong seam.** It
   is the cops-n-crooks wanted-level kill-combo delegate (`countsForWanted`, `recordKill`, `resetCombo`); the only
   implementation is `KillComboWantedTracker` in cops-n-crooks. It stores no stats. Do not hang gang stats on it.
4. **Downed players interact with kill credit.** With `Settings.isRespawnEnabled()` the lethal hit is cancelled at HIGHEST by
   `CustomPlayerDeathListener.onEntityDamage` (`gangland-impl/.../listener/player/CustomPlayerDeathListener.java`, the
   `EntityDamageEvent` handler, then `enterDownedState` fires `PlayerDownedEvent` at ~L211) and `PlayerDeathEvent` never fires.
   Credit still lands because `EntityDamageListener` runs earlier (HIGH), but anything that reacts to "a player died" must
   listen to `PlayerDownedEvent` **and** `PlayerDeathEvent`, and treat `DownedPlayerRegistry.isDowned` as dead
   (`gangland-core/.../downed/DownedPlayerRegistry.java`, a static set, plus `PlayerDownedEvent`, `PlayerUndownedEvent`).
5. **Kills count everywhere.** No world gate (section 5), so GP-22 "no stat counting in gang-disabled worlds" needs a check at
   L121 or on the ledger.
6. **Fights would pollute the shared counters.** Every kill, including an arena kill, currently pays or raises bounty
   (L126-142), raises wanted (L145-148), and every death applies the death money penalty or console commands
   (`PlayerDeathListener.onPlayerDeath` L82-85, `handleMoney` L131-161). None of these has an "arena context" opt-out. A
   fights lane needs either an additive predicate seam (api) or to run its own elimination rule without letting the vanilla
   death path run (arena damage handled and cancelled before it becomes lethal).
7. **Thread safety for leaderboards.** `GangManager.gangs` and `MemberManager.members` are plain `HashMap`
   (`GangManager.java:17,23`, `MemberManager.java:25,37`); `UserManager.users` is also a `HashMap`. Compute leaderboards on
   the main thread (or into an immutable snapshot published from the main thread); never from an async chat or PAPI thread.

## 2. Teleport, warmup and safe-location; can gang homes be waypoints

### 2.1 What exists (and where it lives, which matters)

`Waypoint`, `WaypointTeleport`, `IllegalTeleportException`, `WaypointLookupContract` and `TeleportEvent` are in
**`gangland-api`** (`gangland-api/src/main/java/org/luckyraven/gangland/data/teleportation/`,
`.../events/teleportation/TeleportEvent.java`). The manager, table, repository, access rule and every command are
**`gangland-impl`** (`WaypointManager`, `WaypointAccess`, `database/tables/waypoint/WaypointTable`,
`database/repositories/waypoint/WaypointRepository`, `command/sub/waypoint/*`). A module therefore can construct `Waypoint`
objects and call `waypoint.getWaypointTeleport().teleport(plugin, user, duringTimer)` without any api change.

What a `Waypoint` already carries: `name`, coordinates + world, `type` (`SPAWN, GANG, QUEST, SAFE_ZONE, GLOBAL`; `GANG` exists),
**`gangId`** with `forGang()`, `timer` (warmup seconds), `cooldown`, `shield` (invulnerability seconds after arrival), `cost`,
`radius`, and a permission node `<prefix>.waypoint.<name>`.

What `WaypointTeleport.teleport` already does (`WaypointTeleport.java`): refuses while the player is on cooldown
(`IllegalTeleportException`), runs a `CountdownTimer` warmup (skipped when `timer == 0`), cancels the warmup when the player
moves more than 1.5 blocks in total (`onPlayerMove`, L~79-105, message `WAYPOINT_TELEPORT_CANCELLED`), fires the cancellable
`TeleportEvent(user, from, waypoint)` (L126), teleports, then applies cooldown and shield timers. It returns a
`CompletableFuture<TeleportResult(success, user, waypoint)>`. The movement listener is a single instance registered in
`gangland-impl/.../config/WiringConfig.java:58-61` (a dummy `Waypoint` whose `WaypointTeleport` is added to the
`ListenerManager`), and the cooldown, countdown and distance maps are **static**, so that one listener covers every
`WaypointTeleport` instance including ones a module creates.

`TeleportCommand` (`command/sub/waypoint/TeleportCommand.java`) adds: the access rule `WaypointAccess.canAccess` (player is in
the waypoint's gang, or holds the waypoint permission), a paid teleport with a "re-type to confirm" step
(`reconfirm` map + 30 s `CountdownTimer`, L~123-146), a cooldown-bypass node
`<prefix>.command.<label>.force_rank`, and the withdrawal of `cost` after a successful jump (L~186-192).

The gang link already exists on the admin side: `/glw waypoint gangId <id>` (`WaypointGangIdCommand`) lets a player attach a
waypoint to **their own** gang; `WaypointTable` persists `gang_id` (nullable, deliberately no FK, comment in the file);
`WaypointRepository` restores it.

### 2.2 Can gang homes be waypoints with a gang owner? Yes, with two ways to wire it

- **Option A (no api change, recommended for W0-W3 given PLAN line 94):** the gang module owns a NEW `gang_home` table
  (`gang_id`, `name`, world, x, y, z, yaw, pitch) and builds a transient `Waypoint` per home (or per teleport), setting
  `type = GANG`, `gangId`, `timer`, `cooldown`, `shield`, `cost` from module YAML, then calls `getWaypointTeleport().teleport`.
  It gets warmup, move-cancel, cooldown, shield, cost and the `TeleportEvent` veto for free. Nothing is registered in
  `WaypointManager`, so `/glw waypoint list/teleport` do not see homes (a feature, not a bug: no name collisions).
- **Option B (additive api):** widen `WaypointLookupContract` (currently read-only: `get(String)`, `getWaypoints()`, implemented by
  `WaypointManager`, reached through `GanglandApi.waypoints()`, `GanglandApiImpl.java:40`) with add/remove, or add a NEW
  `WaypointRegistry` interface. Then homes appear in `/glw teleport` and inherit `WaypointAccess`. Costs an api minor bump.

### 2.3 The gaps, exactly

1. **No safe-location check.** `WaypointType` has a `safe` boolean, but nothing reads it (grep `isSafe()`: zero callers). The
   teleport goes to raw stored coordinates. Existing ad-hoc heuristics only: `CarDismountListener.findSafeExitLocation`
   (`gangland-features/gangland-gadget/.../listener/car/CarDismountListener.java:72-90`, private, scans up 5 blocks for a 2-high
   passable gap), `getHighestBlockYAt` in `TurfTpCommand.java:106` and `GarrisonDeployListener.java:109`, and
   `isPassable` in `CopNpcFactory.java:203`. No shared helper exists in Keystone either (grep found none). GP-26's safe-location
   check (with an off switch) is a NEW helper; if generic, upstream it to Keystone rather than forking.
2. **`Waypoint` names are global.** `WaypointManager.get(String)` returns the first case-insensitive name match across the whole
   server and `WaypointCreateCommand` has no duplicate check. Two gangs both naming a home "base" collide. Option A avoids this.
3. **Global side effects of constructing a `Waypoint`.** The constructor bumps a shared static `ID` counter
   (`Waypoint.java`, `usedId = ++ID`) and builds a permission string; `WaypointManager.initialize` and `clear` reset that counter
   (`setID`). Transient instances are harmless (the `WiringConfig` dummy does the same) but must never be handed to
   `WaypointManager.add`.
4. **Warmup is cancelled by movement only, not by damage or combat**, and the maps are non-concurrent statics.
5. **Nothing cleans homes on disband.** The gang module never references `Waypoint` (grep: only cops-n-crooks and turf touch it),
   `GangDeleteCommand` does not remove waypoints, and impl cannot listen to `GangDeleteEvent` (module-owned type). With Option A
   the module deletes its own rows in the disband path; with Option B it needs the write seam.
6. **`TeleportEvent` has no listener today** (grep: only the firing site). It is the natural veto point for "no home teleport
   in a fight or while combat-tagged", but it only covers waypoint-based teleports, not raw `player.teleport` calls.
7. **Regroup / summon (GP-27, GP-42)** are new: fan a teleport out to `Gang.getOnlineMembers(userManager::getUser)` (which scans
   all online players, fine for regroup, not for per-tick use) through the same `WaypointTeleport` path with the target home.
8. **`/glw waypoint` (admin surface)** offers: `create`, `delete`, `list`, `select`, `deselect`, `info`, `type`, `gangId`,
   `timer`, `cooldown`, `shield`, `cost`, `radius` and `/glw teleport <name>`. All are admin-oriented (`gangland.command.waypoint`
   is OP by default); there is no player-facing "set my gang's home" and no per-gang limit. GP-28 (admin home tools) maps to
   `list`/`delete`/`teleport` if Option B is chosen.

## 3. Chat pipeline: gang chat, ally chat, `{gang}` token, social spy

### 3.1 What exists

**Nothing.** There is no chat listener in Gangland or Keystone: grep for `AsyncPlayerChatEvent`, `AsyncChatEvent`,
`PlayerChatEvent`, `getRecipients`, `setFormat(` over `gangland-*/src/main`, `gangland-features/*/src/main` and
`Keystone/*/src/main` returns no code. The gang display name never appears in chat today, and there is no channel concept.
`documentation/tests/features/gangs.md` lists gang chat as a feature and `ranks.md` shows a `gang.chat` node; neither is
implemented (`census/gang.md` s4). PLAN lane **W2-P2** already reserves `GangChatCommand` + `GG/listener/gang/GangChatListener`
(NEW).

Building blocks that do exist:

- **Gang chat display token for PAPI-based chat formatters (GP-15) already works**: `%gangland_gang_display-name%`,
  `%gangland_gang_name%`, `%gangland_gang_color-code%` resolve through `PapiExpansionAdapter`
  (`Keystone/keystone-hooks/.../papi/PapiExpansionAdapter.java`, registered in `Gangland.java:158-159`,
  `persist() == true`). Only non-PAPI chat plugins need a NEW literal `{gang}`-style token, and that requires our own chat
  listener anyway.
- **Permission-gated fan-out precedent (the closest thing to social spy):**
  `ChatUtil.sendToOperators(permission, message[, logger, sendAsWarn])`
  (`Keystone/keystone-common/.../util/ChatUtil.java:230-250`, wrapped by `GanglandChatUtil.sendToOperators`,
  `gangland-api/.../util/GanglandChatUtil.java:35-41`): sends to every online player holding a node and echoes to console or the
  logger. A social spy is "recipients = players with the spy node who toggled it on"; the per-player toggle store is NEW.
  Logging gang/ally chat to the server log (GP-14) reuses the console echo or `@CustomLog`.
- **Colour handling (GP-06, GP-36):** Keystone `ChatUtil.color` already expands `&#RRGGBB` hex to the 1.16 `§x` form and honours
  a `\&` escape; `ChatUtil.replaceColorCodes(msg, replaceWith)` strips `&x&…`, `&#…` and legacy codes (use it to "ignore colour
  codes" when measuring or validating a gang name); `ChatUtil.uncolor` round-trips. Gang name gap: `Gang.color` is a 16-value
  Keystone `Color` enum name, so a hex gang colour is not representable; and the only name sanitiser in the toolbox,
  `ChatUtil.removeSymbol`, strips everything except ASCII letters, digits and spaces (it would delete Unicode names, GP-06),
  so name validation is NEW.
- **Message channels:** `GanglandChatUtil` (`gangland-api/.../util/GanglandChatUtil.java`): `color`, `prefixMessage`,
  `commandMessage`, `errorMessage`, `informationMessage`, `commandDesign`, `confirmCommand`, `setArguments`.

### 3.2 Gaps and hazards a chat lane must handle

1. **Async thread.** On Spigot `AsyncPlayerChatEvent` fires off the main thread. `GangManager`, `MemberManager` and `UserManager`
   are plain `HashMap`s mutated on the main thread, and `Gang.hasOnlineMember` calls `Bukkit.getOfflinePlayer` per member
   (`Gang.java:~170`). The listener must not read them directly: either copy immutable per-gang recipient snapshots on the main
   thread (refresh on join, leave, kick, disband, ally change, login, quit) or cancel the async event and hop to the main
   thread with `Bukkit.getScheduler().runTask` before resolving recipients.
2. **Interaction with other chat plugins.** No format-plugin interplay exists to reuse. Decide `EventPriority` and whether the
   listener rewrites `event.setFormat` (for `{gang}`) or `getRecipients()` (for a channel).
3. **Ally chat needs the ally set**: `Gang.getAllies()` (`Set<GangAlliance>`) and `GangManager.breakAlliance` exist;
   `GangMembership.alliedOrSame(UUID, UUID)` is the module-free form (`gangland-api/.../data/gang/GangMembership.java`).
4. **Chat events for other plugins (GP-16)**: no gang event exists except `GangDeleteEvent`. New `GangChatEvent`/`AllyChatEvent`
   are NEW (module-owned, like `GangLevelUpEvent`).
5. **Mute / channel state** (toggle persistence across relog) has no home; `Member` is the natural row (transient, in memory).

## 4. Placeholders: `PlaceholderService` (impl) vs `GangPlaceholderContribution` (module)

### 4.1 How a module adds placeholders today

- **Core side**: `PlaceholderService` (`gangland-impl/.../data/placeholder/PlaceholderService.java`) is a Keystone
  `CompositePlaceholderProvider` chain: PlaceholderAPI first (when hooked), then every registered `Placeholder` resolver.
  `GanglandPlaceholder` (`.../worker/GanglandPlaceholder.java`, prefix `gangland`, registers itself in the service constructor,
  L52-62) owns `user_*`, `bank_*`, `unique-item_*` and delegates the rest to
  `PlaceholderContributions.from(container)` (`contributions()` L67-73, resolved lazily and cached forever; the first non-null
  answer wins, `PlaceholderContributions.resolve`, `gangland-api/.../placeholder/extension/PlaceholderContributions.java`).
  Dispatch in `resolveInnerPlaceholder` (L136-163) uses substring tests (`param.contains("user_")`, `"bank_"`,
  `"unique-item_"`), then `contributions().resolve(player, param)`, then the settings map; an unanswered token with a
  Gangland-owned prefix (`user_`, `bank_`, `unique-item_`, `gang_`) returns the literal `"NA"`, anything else returns `null` so
  the raw `%token%` stays visible.
- **Module side**: implement `PlaceholderContribution` (`gangland-api/.../placeholder/extension/PlaceholderContribution.java`:
  `@Nullable String resolve(OfflinePlayer player, String parameter)`, parameter lower-cased) as a bean. The gang module does
  exactly this in `gangland-features/gangland-gang/.../gang/placeholder/GangPlaceholderContribution.java` (`user_*` member family
  L45-70 by `startsWith`, `gang_*` family L72-118 including the level family). To add a placeholder, add one more branch (or a
  second contribution bean, since the chain takes any number). The prefix and the `NA` rule mean a new gang token should start
  with `gang_`.
- **Also reusable for stats tokens**: `user_kills`, `user_deaths`, `user_kd`, `user_mob-kills` already resolve in core
  (`GanglandPlaceholder.java:215-218`), so a player profile menu or scoreboard needs no new player-stat tokens. Missing: gang
  `kills`, `deaths`, `kdr`, `assists`, `wins`, `losses`, `wlr`, `members-money`, `rank-number`, `friendly-fire`, `home-count`,
  `member lists` (GP-34), and every turf-related gang token (`census/gang.md` s2.3).

### 4.2 The gap, exactly

1. **Leaderboard tokens (`top_<stat>_<position>_<property>`) cannot flow through the seam as it stands.**
   `GanglandPlaceholder.onRequest` (L104-113) answers a `null` player from the settings map only (or `"NA"`), and
   `resolveInnerPlaceholder` returns `null` at its first line for a null player (L137-138);
   `PlaceholderContribution.resolve` documents a non-null player. Leaderboard tokens are player-independent (holograms,
   scoreboards, signs typically resolve them with no or an arbitrary player). Two routes: (a) additive api, a default method
   such as `default @Nullable String resolveGlobal(String parameter) { return null; }` on `PlaceholderContribution` plus a
   null-player branch in `GanglandPlaceholder` (impl change, api minor); or (b) accept that leaderboard tokens are only correct
   when some real player is passed and document it. Route (a) is needed for correct PAPI holograms.
2. **`NA` is not configurable** (GP-15 "default prefix for gangless players", GP-34 "configurable default value"):
   `isGanglandOwnedPrefix` hard-codes `"NA"`. A gangless player asking `%gangland_gang_display-name%` gets `NA`, which would
   print into chat lines. Needs a configurable default in the module YAML and a hook or an api tweak.
3. **Substring dispatch order.** `param.contains("user_")` runs before the contribution chain, so a leaderboard token that
   contains `user_` (or `bank_`) anywhere would be swallowed by the core branches. Name leaderboard tokens `gang_top_...`
   (and make sure no part contains `user_`).
4. **Thread safety**: PAPI expansions can be called from chat plugins on the async chat thread; the maps behind
   `GangPlaceholderContribution` are unsynchronised `HashMap`s (section 1.3 item 7).
5. **Leaderboard-style precedent: none.** grep for `leaderboard|top_|baltop|richest` finds only a comment in `settings.yml:717`
   ("the module ... exposes it to commands / leaderboards; values here just control ..."). No sorted, cached top-N structure
   exists anywhere; build one snapshot object shared by placeholder, command and GUI so all three agree.

## 5. Confirmation, titles, action bar, world-disable, GUIs, module YAML

### 5.1 Confirmation pattern (dangerous commands)

Three idioms already coexist; a parity lane must pick the first and not add a fourth:

1. **Keystone `ConfirmArgument` + `ArgumentLock`** (`Keystone/keystone-command/.../argument/types/ConfirmArgument.java`): the
   sender types the literal `confirm` sub-argument after the command has `lock`ed them; used by `GangCreateCommand`,
   `GangDeleteCommand` (with a 60 s `CountdownTimer` that unlocks), `GangTransferCommand`, `RankCreateCommand`,
   `RankDeleteCommand`, `WaypointCreateCommand`, `BankCreateCommand`. Hint text via `GanglandChatUtil.confirmCommand(args)`.
   This is the shape GP-01 ("disband ... (`confirm`)") maps to; it is already how `gang delete` works.
2. **Type the command twice**: `GangLeaveCommand.executeArgument` (`.../command/sub/gang/GangLeaveCommand.java:52-72`), a lock
   plus a `runTaskLater` auto-unlock over `CONFIRM_WINDOW_TICKS = 1200`, hint `Messages.ARGUMENT_CONFIRM_HINT`.
3. **Re-type with a cost notice**: `TeleportCommand` `reconfirm` map + 30 s timer.

Also, the sign module has `BulkActionManager` (`gangland-ui/sign-api/.../sign/bulk/BulkActionManager.java`): a
pending-action-with-expiry per player that is a good model if one shared confirmation service is ever extracted (not needed
for parity). **Gap**: no shared service; the confirm windows are hard-coded 60 s / 30 s (`census/gang.md` notes three idioms).
Admin destructive actions (GP-01 admin disband, GP-22 reset stats, GP-24 bank reset) should reuse idiom 1.

### 5.2 Titles and action bar

- **Titles**: `ChatUtil.sendTitle(player, title, subtitle[, fadeIn, stay, fadeOut])` in Keystone
  (`ChatUtil.java:168-176`), used by cops-n-crooks (`DetainmentService`, `BreakFreeService`). There is a toggle precedent for
  "titles on or off": `TurfDisplayContract.isEnterTitleEnabled()` backed by `settings.yml` `Turf.Show_Enter_Title`
  (`gangland-features/gangland-turf/.../contract/TurfDisplayContract.java`, wired in `TurfModuleConfig.java:107`). GP-36
  "notifications as titles" needs a per-notification key in the gang module YAML, nothing else.
- **Action bar**: `ActionBarManager` (`Keystone/keystone-common/.../util/ActionBarManager.java`): foreground
  `send(player, msg)` / `send(plugin, player, msg, duration)` and priority-arbitrated `sendBackground(player, msg[, priority])`.
  Turf uses it (`TurfActionBarListener`). CLAUDE.md rule: all action-bar sends go through it, never `ChatUtil`.
- Boss bar precedent: `TurfBossBarListener` (capture progress).

### 5.3 World-disable settings precedent: none

There is no disabled-worlds key in `gangland-impl/src/main/resources/settings.yml` (grep `world` finds nothing there), no
`Settings` getter, and no shared predicate. The only world-aware code is turf's per-region world name
(`CuboidRegion.getWorld`, `TurfManager.getTurfsInWorld`, `TurfLocationTracker.tick` L70-71 skipping worlds with no turf).
Also note `Gang.Enable` no longer disables anything (`census/gang.md` s2.4). GP-18/22/32 are greenfield: one NEW
`Disabled_Worlds` list (module YAML, per PLAN's `GangModuleFiles`) plus one shared predicate `GangWorlds.enabled(World)` used by
the command root, the damage listener, the stat ledger and the chat listener. World gating of friendly fire per world with
overrides (GP-18) is an extension of the same predicate.

### 5.4 GUIs: profile and leaderboard (keystone-inventory and the YAML dialect)

Two UI stacks coexist; both can host a profile and a leaderboard.

- **YAML menus (impl dialect)** in `gangland-impl/src/main/resources/inventory/` registered in
  `GameplayConfig.java:185-191`: `gang_info.yml` (member-facing gang page with `%gangland_gang_*%` lore and `OnClick: Command`
  / `Inventory:` targets), `user_stat.yml` (despite the name it is the **gang members** list: `Type: multi-inventory`,
  `Item_Source: gang_members`, `Item_Template` with `%member_*%` row placeholders, `Static_Items` with an anvil name search and
  `/glw filter gang_members search|sort|clear` buttons), `phone_gang_search.yml` (`Item_Source: gangs` list),
  `alliance_stat.yml`, and **`gang_stat.yml`, an empty 54-slot shell (8 lines) still linked from `gang_info.yml`**. A leaderboard
  screen is one more `multi-inventory` file over a NEW item source `gang_top`, and a profile screen is a plain `inventory`
  file over `%gangland_user_*%` tokens.
  - The module feeds item sources through `GangItemSourceContribution`
    (`gangland-api/.../data/gang/GangItemSourceContribution.java`: `supports(source)`, `entries(player, source)` returns
    `List<Map<String,String>>` placeholder rows), implemented by `gang/menu/GangMenuItemSourceContribution` (sources `gangs`,
    `gang_members`, `gang_allies`). Add `gang_top` there and the rows can already be pre-sorted and rank-numbered.
  - Sort and filter: `FilterAdapter`/`FilterField`/`FilterApplier`/`SortDescriptor` in `gangland-api/.../menu/filter/`.
    `GangFilterAdapter.project` supports only `NAME, DESCRIPTION, COLOR, MEMBERS, DATE` (`default -> null`, treated as
    non-match). A stat sort (KDR, WLR, level, bank) is a NEW domain `FilterField` implemented by the module (the interface
    explicitly allows features to implement `FilterField` directly), no api change.
- **Keystone `keystone-inventory` panels** (`Keystone/keystone-inventory`): `Panel<S extends FlowState>` (`rows`, `title`,
  `render`), `MenuFlow`, `ChestMenu`/`ChestMenuBuilder`, `PagedRegion` + `PageConfig` (static paging helper; the consumer owns
  prev/next buttons), `ItemComponent`, `BorderComponent`, `FillComponent`. `gangland-gang` already depends on
  `keystone-inventory` (`gangland-gang/pom.xml:57`) and injects `InventoryService` (`GangColorCommand.java`, a 16-colour wool
  chest). Paged-list precedents: `LootChestWand` preview (`PagedRegion` grid rows 1-4, cols 1-7), the shop admin views, the
  banker and trader views (`panel-create` skill documents the scaffold). Use panels when the screen needs click flow, state or
  live refresh (profile with tabs, fight challenge screen); use the YAML dialect when it is a static list of placeholder rows.
- **Leaderboard hologram**: `Keystone/keystone-hologram` `HologramService.createUpdatingHologram(location, intervalTicks,
  updater, lines...)` is a ready primitive (used by lootchest); bounded by the same placeholder caveats (section 4.2).
- **Gap**: there is **no player-profile screen**. GP-39 (announced but never shipped by Gangs+) is a chance to ship one first.
  Anvil text input exists (`AnvilGUI`, gang `desc`, lootchest wand), but no chat-input helper exists (and no chat listener).

### 5.5 `LocalizedModuleYaml` for module messages

`gangland-api/src/main/java/org/luckyraven/gangland/file/configuration/LocalizedModuleYaml.java`: abstract base;
`<base>.yml` (English, required) plus optional `<base>_es.yml` chosen when `Settings.getLanguagePicked()` is `es`; accessors
`raw`, `color`, `command`, `error`, `information`, `prefix`, `list`. Consumers: `CivilianMessages`, `CopRadioMessages` (plus
`CopsNCrooksYamlConfig`, `CiviliansYamlConfig` as registration). Registration pattern (KERNEL phase, module class loader):
`CiviliansYamlConfig.civiliansFiles` adds `new FileHandler(plugin, "civilian_messages", "npc", ".yml", moduleLoader.classLoader())`.
Turf has module YAML for gameplay (`turf/turf_powerups.yml`, `turf/turf_npcs.yml`) but routes its **messages** through the api
`Messages` enum via `TurfMessageContract`/`GanglandTurfMessages`.

**Gaps**: (1) `gangland-gang` and `gangland-mail` ship only `commands.json` + `module.yml`; no module YAML, no
`*ModuleFiles`/`*ModuleFileConfig` (PLAN creates them at the W1 kickoff / W0-D, copying `TurfModuleFileConfig`). (2) Language
support is `en` and `es` only, hard-coded (the class javadoc says parameterise if a third language shows up); GP-36 "any
language" needs that generalisation (small, additive: read the picked code and try `<base>_<code>.yml`). (3) The 88
`GANG_*`/`RANK_*` constants live in the api `Messages` enum and the contract rule says new strings go in module YAML, so gang
parity messages must not be added to `Messages`.

## 6. Combat tagging and the downed-player registry

### 6.1 What exists

- **Downed state** (`gangland-core/.../downed/`): `DownedPlayerRegistry` (static `ConcurrentHashMap` set with `add`, `remove`,
  `isDowned`), `PlayerDownedEvent`, `PlayerUndownedEvent`. Driven by `CustomPlayerDeathListener` (impl): lethal damage is
  cancelled at HIGHEST, health set to 0.5, the player is put in a downed game mode with a respawn countdown, inventory dropped
  by hand (`dropInventoryIfAllowed`), interactions, block edits, drops and pickups cancelled, and a **quit while downed**
  restores health and game mode (`onPlayerQuit`, `restoreDownedState`). Anything that models "is this player out of a fight"
  must consult `DownedPlayerRegistry` (the class javadoc says so: other systems treat downed as dead).
- **Command blocking precedent (GP-31)**: `DetainmentListener.onCommand`
  (`gangland-features/cops-n-crooks/.../listener/police/DetainmentListener.java:270-278`, `PlayerCommandPreprocessEvent`,
  HIGHEST, cancels every command for a restrained player unless they hold the bypass permission
  `detainmentService.getCommandBypassPermission()`). A fight's "block all / block a list of commands, with bypass permission" is
  the same handler with a set of fighters and a configurable command list.
- **Nothing else** in gameplay code blocks or reacts by "in combat".

### 6.2 The gap, exactly

- **No combat-tag equivalent and no logout-in-combat handling anywhere.** Grep for `combat|inCombat|combatLog|combatTag`
  finds only the cop NPC `Combat_Range` setting. There is no last-hit-by-player timestamp store. GP-33 "combat-tag plugin
  support" has two halves: (a) an external-plugin adapter (soft dependency, none exists) and (b) a built-in tag. The
  built-in tag and assist tracking share the same NEW ledger recorded at `EntityDamageByEntityEvent` MONITOR (attacker,
  victim, time, damage), with a `PlayerQuitEvent` handler for punishment. `CustomPlayerDeathListener.onPlayerQuit` is the local
  precedent for quit handling; `TeleportEvent` (section 2.3 item 6) is the veto point for waypoint teleports.
- **Friendly fire is decided in three unrelated places** (relevant to GP-17..19, 44): the gang module's
  `GangMembersDamageListener` (`gangland-features/gangland-gang/.../listener/gang/GangMembersDamageListener.java`,
  `EventPriority.LOWEST`, cancels every player-vs-player hit between same-gang or allied players, direct melee and projectile
  only, **no toggle, no world check, no config**; it also calls `gang1.isAlly(gang2)` on two `getGang` results without a null
  guard), civilians' `GangAllyWeaponImpactListener` (weapon impacts, via `GangMembership.alliedOrSame`), and cops-n-crooks'
  `TurfFriendlyFireListener` (via `GangMembership.gangsAllied`). A per-gang toggle, a global toggle and a negotiated ally toggle
  all have to be readable from all three; the module-free seam for the latter two is `GangMembership`/`GangMembershipView`
  (`gangland-api/.../data/gang/`, three methods today), so a friendly-fire query needs an additive api method there.

## 7. WorldGuard

**Nothing to reuse: not a dependency today.** Grep for `worldguard|sk89q` over Gangland (excluding `.claude/worktrees`,
`graphify-out`, `target`, `brainstorming` design notes) and over Keystone source, poms and yml finds no code, no dependency and no
softdepend (`gangland-impl/src/main/resources/plugin.yml` `softdepend` is PlaceholderAPI, Vault, ViaVersion, Citizens, Bartizan).
The only textual hits: `brainstorming/features/gang_turfs_spec.md` ("WorldGuard is NOT required", turf uses its own
`CuboidRegion`), and Javadoc in Bartizan's `BlockDamageManager` / `WeaponBlockBreakEvent` noting that firing `BlockBreakEvent`
lets protection plugins veto.

Constraints to record for the GP-18 "region flag for friendly fire" feature: a WorldGuard flag must be registered before
WorldGuard enables, i.e. from `Gangland.onLoad()` (it exists, `Gangland.java:52`) and needs `softdepend: WorldGuard` in
`plugin.yml` so WG's `onLoad` runs first. Modules load during `onEnable`'s bootstrap, and the core must never name a module
type, so the flag can only be registered by the core with a state-only api seam that the gang module reads later. That is a
real cross-cutting cost for one flag; the alternative that needs no soft dependency is to cover the same use case with Gangland's
own regions or the per-world overrides of section 5.3.

## 8. Region and selection tools an arena could reuse

- **`Region` / `CuboidRegion`** (`gangland-features/gangland-turf/.../turf/data/Region.java`, `CuboidRegion.java`): `contains(Location)`,
  `getWorld()`, `overlaps(CuboidRegion)`. **X/Z only; Y is ignored by design** ("bedrock to sky"). The interface comment says
  other shapes can be added later. An arena needs a full 3D box (players must not leave upward or into a lower level), so the
  class cannot be used verbatim.
- **Selection tooling** (turf module): `Selection` (per-admin `pos1`/`pos2`, same-world rule), `WandSelectionManager`
  (in-memory `ConcurrentHashMap<UUID, Selection>`, cleared on quit, admin node `gangland.turf.admin`, NBT marker
  `gangturf_wand`), `WandListener` (left click = pos1, right click = pos2, a hard-coded English message string that bypasses the
  message system), `TurfWandCommand`, `TurfPos1Command`, `TurfPos2Command`. All **turf-private**: none of it is in
  `gangland-api`, so the gang module (which turf depends on, not the reverse) cannot import it. A NEW arena module would need
  `Depends: [gang, turf]` (turf already depends on gang), or the primitives get promoted: per house rule a generic region and
  wand primitive belongs upstream in **Keystone** (a 3D `Region` + selection + wand base), not copied.
- **Lootchest wand** (`gangland-features/gangland-lootchest/.../LootChestWand.java`, `LootChestWandTag`) is a separate,
  NBT-tagged wand for a different job (chest configuration + a `PagedRegion` preview); nothing to reuse except the
  `ItemBuilder.hasNBTTag` wand-detection idiom.
- **Presence / enter-exit detection precedent**: `TurfLocationTracker` (`.../turf/task/TurfLocationTracker.java`) polls at 1 Hz
  and fires `TurfEnterEvent`/`TurfExitEvent`; the turf spec forbids `PlayerMoveEvent` there for TPS. "Players cannot leave the
  arena during a fight" (GP-30) needs a tighter loop (5-10 tick poll over the fighters only, so a handful of players, or a
  `PlayerMoveEvent` handler with an early exit on same-block moves) and a teleport-back rule. `WaypointTeleport.onPlayerMove`
  already uses `PlayerMoveEvent` for warmup, so the codebase is not strictly opposed to it for a tiny fixed player set.
- **Do not model arenas as turfs**: `Turf` carries ownership, capture, income, garrison and powerup NPC state
  (`Turf.java`, `CaptureService`, `TurfIncomeDistributor`) and `TurfLocationTracker` would fire enter/exit and capture ticks for
  it. Reuse the geometry, not the entity.
- **Persistence pattern** for an arena registry: copy the turf/jail shape (module table + repository + manager with
  `setDataSupplier`, per `feedback_repository_data_supplier`), and the `commands.json` + `GangCommandsJsonParityTest` rule for
  new admin commands.
- **Fight elimination** must account for the downed state (section 1.3 item 4) and the death penalties (item 6); the arena
  spawn/return teleport can reuse `WaypointTeleport` (section 2) but `Settings.getRespawnTeleportWaypoint` in
  `CustomPlayerDeathListener.performRespawn` (L~285-297) would otherwise pull an eliminated fighter to the respawn waypoint.

## 9. Other existing pieces found while verifying (reuse, do not rebuild)

- **Console commands on membership events (GP-05)**: precedent `PlayerDeathListener.handleCommandExecution` (L116-129):
  `Settings.isDeathMoneyCommandEnabled()` + `Settings.getDeathMoneyCommandExecutables()` (`settings.yml` `Death.Money.Command`
  `Enable`/`Executable`), each entry passed through `placeholder.replacePlaceholder(player, exec)` then
  `Bukkit.getServer().dispatchCommand(console, exec)` (the only `dispatchCommand` in the codebase). Reuse the shape (YAML list,
  placeholder replace, console dispatch). Two cautions: it strips **every** `/` from the string (`executable.replace("/", "")`),
  and gang/player names are spliced in by placeholder while names have no validation today (`census/gang.md` Obs. 29), which is
  a console-command injection path; validate names (W0) before shipping GP-05.
- **Gang level machinery (GP-10..12)** exists but is unproducing: `Gang.level` is `new Level()` (`Gang.java:68`, the user curve),
  `Level.addExperience(double, LevelUpEvent)` and `addLevels(int, LevelUpEvent)` (`gangland-core/.../user/Level.java:38-74`)
  are ready, `Level(int maxLevel, double baseAmount)` allows a gang-specific curve without core changes,
  `GangLevelUpEvent` + `GangLevelMessageListener` already announce a level-up to online members (never fired today),
  placeholders `gang_level*`/`gang_experience*` read it. GP-10 "pay to level up" = withdraw from `gang.getEconomy()` then
  `addLevels(1, new GangLevelUpEvent(...))`. Per-level limits (GP-11) and reward commands (GP-12) are YAML in the new module
  file; the console-command shape is the bullet above. PLAN decision D2 (gang XP only from held turf) must be reconciled with
  "pay to level up" (they can coexist: one currency path, one XP path).
- **Auto-purge inactive players and gangs (GP-40)**: a scheduler skeleton exists, an action does not.
  `PluginDataCleanupService.checkAndPerformCleanup/forceCleanup` (`gangland-impl/.../data/plugin/`) only rolls the "next scan"
  date and deletes **nothing** (`performCleanup` L68-81), driven from `PeriodicalUpdates`. Presence facts exist:
  `Gang.lastMemberOnlineAt` (written by turf's `GangPresenceTracker`, persisted `last_member_online_at`) and
  `OfflinePlayer.getLastPlayed`. The purge sweep itself has a working model in turf: `InactivityReleaseTask`
  (`.../turf/task/InactivityReleaseTask.java:28-56`, releases turfs of gangs quiet for N days).
- **Help lists only what you may run (GP-04)**: Keystone already applies the sender's permission check to the help list and
  tab completion, and `CommandVisibilityFilter` (`Keystone/keystone-command/.../CommandVisibilityFilter.java`, installed per
  manager; gangland's is `DevCommandVisibilityFilter` in `gangland-impl/.../command/`) is the extension point. Gap: rank-node
  gates (`GangPermissions.allows`) are not server permissions, so help cannot yet hide commands a player's rank forbids.
  `commands.json` per module is checked by `GangCommandsJsonParityTest`.
- **Public API and events (GP-37)**: `GanglandApi` (`gangland-api/.../GanglandApi.java`: `gangs()`, `waypoints()`, ..., version
  `2.0`) is the module-free façade; the only gang event today is `GangDeleteEvent`; turf ships a full event set
  (`TurfCapturedEvent`, `TurfEnterEvent`, `TurfOwnerChangedEvent`, ...) as the style to copy (module-owned, carries the entity).
  Missing lifecycle events are listed in `census/gang.md` s2.3 (create, rename, join, leave with reason, kick, rank change,
  ally change, bank change).
- **Bank (GP-23/24)**: `Gang.getEconomy()` is Keystone `EconomyHandler` (`depositAmount`, `withdrawAmount`, `getAmount`,
  `Currency.of`), with a cap (`Account.Maximum_Balance`) and the contribution ledger on `Member`. Balance visibility and admin
  give/take/reset are commands only. Note the dual-unit `contribution` field (`census/gang.md` s1).
- **Timers**: Keystone `CountdownTimer` and `RepeatingTimer` (`org.luckyraven.keystone.timer`); CLAUDE.md rule: `start(true)`
  is async and only safe for flag flips.

## 10. Additive `gangland-api` surface the parity plan would need (vs PLAN line 94)

`PLAN.md` promises **no new api surface in W0-W3**. These gaps cannot be closed without it; each has an in-module fallback.

| Need | Api change (additive) | Fallback with no api change |
|---|---|---|
| Leaderboard placeholders with no player (s4.2) | `PlaceholderContribution.resolveGlobal(String)` default method + null-player branch in impl `GanglandPlaceholder` | Document that tokens need a real player; leaderboard commands and GUIs unaffected |
| Configurable "NA" / gangless default (s4.2) | none if the module answers `gang_*` for gangless players itself (return the YAML default instead of `null`) | This fallback is enough: contribution returns a value, core never reaches `NA` |
| Gang homes in `/glw teleport` and disband cleanup (s2) | Write seam on `WaypointLookupContract` or NEW `WaypointRegistry` | Option A: module-owned `gang_home` table, transient `Waypoint` objects |
| Friendly-fire flags readable by civilians and cops-n-crooks (s6.2) | Method on `GangMembership`/`GangMembershipView` (for example `friendlyFireBlocked(UUID, UUID)`) | Gang module's listener only; weapon impacts and turf friendly fire keep today's semantics until W4+ |
| Arena context opt-out from bounty, wanted, death penalty (s1.3 item 6) | NEW predicate seam in api or core (`FightContext`) | Fights module cancels arena damage and resolves elimination itself before the vanilla death path |
| Kill event for stats/assists (s1.3 item 2) | none: NEW module-owned event + ledger listener at MONITOR | Fully in-module |
| World-disable gate (s5.3) | none: module YAML + predicate | In-module; core code paths (waypoint, user kills) stay ungated |
| Language beyond `es` (s5.5) | additive change inside `LocalizedModuleYaml` | Ship `en`/`es` only |
| WorldGuard flag (s7) | core `onLoad` hook + a state seam in api | Do not ship the flag; use per-world overrides |

Everything else in sections 1-9 is reusable as is, or is a NEW class inside `gangland-gang` / `gangland-mail` / a new module
that already fits the existing module contract.
