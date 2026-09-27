# 0.12 "Heat" — implementation design

Spec: `heat_0.12_spec.md` (same folder). This file is the contract implementers follow. Build order:
**F1 → F2 → F4 → F3 → F6 → F5.** Features run one after another (several share a handful of files, see
"Shared files" at the end); each feature commits on its own.

Abbreviations: `cnc` = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks`,
`cnc-test` = the matching `src/test/java` root, `cops.yml` = `gangland-features/cops-n-crooks/src/main/resources/npc/cops.yml`.

## Facts the design relies on (verified in this repo)

- `Settings` (static getters, parsed with `section/intVal/dbl/bool/str/intList` helpers) lives in
  **gangland-api** `org.luckyraven.gangland.file.configuration.Settings`, not gangland-impl. Shipped yml:
  `gangland-impl/src/main/resources/settings.yml`.
- cops-n-crooks depends on `gangland-turf` and `gangland-civilians` (pom + `module.yml Depends`), so
  `TurfManager`, `TurfRuntimeState`, `CivilianNpcRegistry`, `CivilianNpc`, `CivilianState`, `EntityMarks`, `EntityMark`
  are all reachable. Turf info is therefore available with no new dependency.
  - `TurfManager#findAt(Location)` → `@Nullable Turf`; `Turf#getId()`; `TurfManager#getRuntimeState(int)` → may be
    `null`; `TurfRuntimeState#getState()` → `TurfState.CONTESTING` means contested.
  - `CivilianNpcRegistry#getNpc(UUID entityId)`; `CivilianNpc#isHostile()`, `#getCurrentState()`.
- Keystone `NpcSquad` accessors used in this repo: `new NpcSquad()`, `reportSighting(Location)`,
  `hasFreshSighting()` (PursuingBehavior/IdleBehavior), `millisSinceSighting()` (CivilianCombatBehavior; returns
  `Long.MAX_VALUE` if never sighted), `lastKnownLocation()` (`@Nullable`, used by IdleBehaviorTest /
  CivilianCombatBehaviorTest, same jar), `members()`, `isEmpty()`. `CopGroup#getSquad()` exposes the group's squad.
  F2 uses `millisSinceSighting()` + `lastKnownLocation()`, and additionally remembers the player's position on every
  SEEN tick so a `null` last-known position never breaks the zone.
- `CopDeathEvent` exists but is **never fired** today. F6 fires it.
- Messages: cops-n-crooks has no module message file (detainment uses the host `Messages` enum, which prints
  `<missing: …>` for keys absent from an existing server's language file). So F3/F6 strings live in `cops.yml`
  with code defaults.
- `WantedExecutor` fires the cancellable `WantedEvent` (async) *before* each decay step and returns on cancel
  (no decrement, no money). F2 uses that to make the old timer a fallback without touching core.
- `Wanted.setLevel` fires `WantedLevelChangeEvent` (cancellable, before the change) then `WantedStartEvent` /
  `WantedEndEvent`. `CopListener` → `CopManager` keys off these; nothing new is needed for cops to react.
- Config parsing for the module: `CopLoader.loadData` builds one `NodeReader` over `cops.yml` root. New top-level
  sections (`Heat`, `Hud`, `Pursuit_Report`) are parsed there into immutable config objects exposed by `@Getter`
  fields on `CopLoader` (class already has `@Getter`). Every parser returns code defaults for a missing section/key.
  Readers: `root.get("Heat").asMapping().orNull()` → `NodeReader.of(node, report)`; scalars
  `.asInt().orDefault(x)`, `.asDouble().orDefault(x)`, `.asBool().orDefault(x)`, `.asString().orDefault(x)`,
  lists `.asList().ofInts().orEmpty()` / `.ofStrings().orEmpty()`, `keys()`.

---

## F1. Heat ledger

### Config — `cops.yml`, new top-level section (parsed by `HeatConfig.parse`)
```yaml
Heat:
   Enable: true
   Star_Thresholds: [100, 250, 450, 700, 1000]
   Streak_Bonus: 1.5                  # crime inside Wanted.Kill_Combo.Reset_After of the previous kill
   Turf_War_Multiplier: 0.5           # kills whose victim stands inside a CONTESTING turf
   Assault_Cop_Cooldown_Seconds: 10   # one Assault_Cop per (player, cop) per window
   Crimes:
      Kill_Player: 80
      Kill_Civilian: 100
      Kill_Cop: 150
      Assault_Cop: 100
```
Missing section ⇒ `HeatConfig.defaults()` (all values above). Empty/short `Star_Thresholds` ⇒ defaults /
`NumberUtil.resizeLinear(thresholds, maxLevel)` exactly like `KillCombo.shouldTriggerWantedLevel`.

### New classes — package `org.luckyraven.gangland.copsncrooks.heat`
- `Crime` enum: `KILL_PLAYER("Kill_Player", 80, true)`, `KILL_CIVILIAN("Kill_Civilian", 100, true)`,
  `KILL_COP("Kill_Cop", 150, true)`, `ASSAULT_COP("Assault_Cop", 100, false)`; getters `getConfigKey()`,
  `getDefaultWeight()`, `isKill()`.
- `HeatConfig` (immutable, `@Getter`): `boolean enabled; List<Integer> starThresholds; double streakBonus;
  double turfWarMultiplier; int assaultCopCooldownSeconds; Map<Crime,Integer> weights (EnumMap)`.
  - `static HeatConfig defaults()`
  - `static HeatConfig parse(NodeReader root, ConfigReport report)`
  - `int weightOf(Crime crime)`
  - `int starsFor(int heat, int maxLevel)` — number of thresholds `<= heat`, capped at `maxLevel`.
  - `int floorFor(int level)` — `0` for level ≤ 0, else threshold of `level` (index `level-1`, clamped).
- `HeatService` (plain class, bean):
  ```java
  public HeatService(KillCombo killCombo, NpcMarkManager markManager,
                     Predicate<Location> inContestedTurf, Supplier<HeatConfig> config)
  public static Predicate<Location> contestedTurf(TurfManager turfs)   // findAt → getRuntimeState(id) null-safe → CONTESTING
  public boolean isEnabled()
  public HeatConfig getConfig()                                         // supplier value, defaults() if null
  public Crime classifyKill(Entity victim)       // EntityMark POLICE → KILL_COP, CIVILIAN → KILL_CIVILIAN, else KILL_PLAYER
  public boolean isCop(Entity entity)            // EntityMarks.of(markManager.getMark(e)) == EntityMark.POLICE
  public int weigh(Crime crime, boolean streak, @Nullable Location scene)
        // round(weight * (streak ? streakBonus : 1) * (crime.isKill() && inContestedTurf(scene) ? turfWarMultiplier : 1))
  public int recordCrime(Player offender, Wanted wanted, Crime crime, @Nullable Location scene)
        // streak = killCombo.getTracker(offender.getUniqueId()) != null; pts = weigh(..); addHeat(..); return pts
  public boolean recordAssault(Player offender, Wanted wanted, Entity cop)   // cooldown per (offender, cop uuid); false if rate-limited
  public void addHeat(Player offender, Wanted wanted, int points)
        // heat = max(heat, floorFor(wanted.getLevel())) + points;
        // target = starsFor(heat, wanted.getMaxLevel()); if (target > wanted.getLevel() && starTrigger != null) starTrigger.accept(offender, target)
  public int getHeat(UUID playerId)
  public void lowerTo(UUID playerId, int newLevel)    // heat = min(heat, floorFor(newLevel)) — called on star loss
  public void clear(UUID playerId)                    // heat + assault cooldowns
  public void setStarTrigger(BiConsumer<Player, Integer> trigger)
  ```
  State: `Map<UUID, Integer> heat`, `Map<UUID, Map<UUID, Long>> assaultCooldowns` (main thread only).
  The heat floor makes relog / `/wanted add` / evasion drops consistent (no burst of stars from stale heat).

### Core seam (additive) — gangland-core `org.luckyraven.gangland.core.wanted`
- `WantedKillTracker` add:
  ```java
  default boolean scoresAllKills() { return false; }                         // heat ledger on: every kill goes through recordKill
  default void onHeatStarTrigger(BiConsumer<Player, Integer> handler) { }    // (player, targetLevel)
  ```
- `WantedKillTrackers` add field `BiConsumer<Player,Integer> heatStarTrigger`, method
  `public boolean isHeatActive()` (`delegate != null && delegate.scoresAllKills()`), method
  `public void onHeatStarTrigger(BiConsumer<Player,Integer> handler)` (store + forward, same as the others), and
  replay it in `install(...)`.

### Changes
- `cnc/combo/KillCombo`: add overload
  `public void recordKill(Player killer, Wanted wantedKiller, Entity killed, int resetAfter, int points, boolean checkThresholds)`;
  the old 4-arg method delegates with `(…, 1, true)`. `points` goes to `tracker.addKill(killed, points)`;
  `checkWantedLevelTrigger` runs only when `checkThresholds`.
- `cnc/seam/KillComboWantedTracker`: keep the 2-arg constructor (delegates with nulls), add
  `KillComboWantedTracker(KillCombo, NpcMarkManager, @Nullable CivilianNpcRegistry, @Nullable HeatService)`.
  - `countsForWanted(victim)`: `EntityMarks.countsForWanted(...) && !isSelfDefence(victim)`;
    `isSelfDefence` = registry non-null, `getNpc(victim.getUniqueId())` non-null, `isHostile()` and
    `getCurrentState() == CivilianState.COMBAT` (the exemption moved from CivilianDeathRewardListener; applies to
    every civilian kill path since EntityDamageListener checks `countsForWanted` first).
  - `scoresAllKills()`: `heatService != null && heatService.isEnabled()`.
  - `recordKill(...)`: heat on ⇒ `int pts = heat.recordCrime(killer, wanted, heat.classifyKill(victim), victim.getLocation());
    killCombo.recordKill(killer, wanted, victim, resetAfter, pts, false);` — heat off ⇒ unchanged.
  - `onHeatStarTrigger(h)`: `if (heatService != null) heatService.setStarTrigger(h)`.
- `cnc/npc/police/config/CopLoader`: field `private HeatConfig loadedHeatConfig;` set in `loadData`
  (`HeatConfig.parse(reader, report)`), nulled in `clear()`.
- `cnc/config/CopsNCrooksModuleConfig`: `@Bean HeatService heatService(KillCombo, NpcMarkManager, TurfManager, CopLoader)`
  (`HeatService.contestedTurf(turfManager)`, `copLoader::getLoadedHeatConfig`); `installCoreSeams()` builds the
  4-arg `KillComboWantedTracker` with `CivilianNpcRegistry` and `HeatService`.
- New listener `cnc/listener/heat/HeatListener` (`@ListenerHandler`, ctor `HeatService, @Qualifier("online") UserManager<Player>`):
  - `EntityDamageByEntityEvent` MONITOR ignoreCancelled: real player attacker (`Player` not `NpcSupport.isNpc`, or
    projectile shooter), `heatService.isCop(victim)`, skip if lethal (`LivingEntity#getHealth() <= event.getFinalDamage()`,
    the kill scores Kill_Cop), `user.getWanted()` → `recordAssault`.
  - `org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent` MONITOR ignoreCancelled: same for `getShooter()`/`getHitEntity()` (cooldown dedups
    double delivery).
  - `WantedLevelChangeEvent` MONITOR ignoreCancelled: `newLevel < oldLevel` ⇒ `lowerTo(player, newLevel)`.
  - `WantedEndEvent`, `PlayerQuitEvent` MONITOR ⇒ `clear(uuid)`.
- gangland-impl `listener/player/EntityDamageListener`:
  - `private boolean routeThroughTracker()` = `wantedKills.isActive() && (Settings.isWantedKillComboEnabled() || wantedKills.isHeatActive())`;
    replace the four `wantedKills.isActive() && Settings.isWantedKillComboEnabled()` checks.
  - `setupKillComboCallbacks()` also registers `wantedKills.onHeatStarTrigger(this::onHeatStarTrigger)`.
  - `handleWanted(User)` becomes `handleWanted(user, wanted.getLevel() + wanted.getIncrements())` (identical to
    `incrementLevel()`); new `handleWanted(User<Player> user, int targetLevel)`: return if `targetLevel <= level`,
    else `wanted.setLevel(targetLevel)`, then the existing timer/bounty/message code unchanged. This is the single
    star-gain path (timers + bounty + events).
- gangland-civilians `listener/npc/CivilianDeathRewardListener`: delete the `incrementLevel()` block (XP stays);
  update the class Javadoc: wanted is raised only by `EntityDamageListener` via the seam.
- `cops.yml`: add the `Heat:` section with comments.

### Tests
- `cnc-test/heat/HeatConfigTest` (defaults, `starsFor`, `floorFor`, resize when fewer thresholds than max level).
- `cnc-test/heat/HeatServiceTest` (streak, turf multiplier only for kills, floor, star trigger target, assault cooldown).
- extend `cnc-test/seam/KillComboWantedTrackerTest` (self-defence exemption, heat routing, `scoresAllKills`).
- extend `gangland-core/src/test/.../wanted/WantedKillTrackersTest` (heat trigger replay, `isHeatActive`).

### Files owned by F1
`cnc/heat/*` (new), `cnc/listener/heat/HeatListener.java` (new), `cnc/combo/KillCombo.java`,
`cnc/seam/KillComboWantedTracker.java`, core `WantedKillTracker.java`, `WantedKillTrackers.java`,
impl `EntityDamageListener.java`, civilians `CivilianDeathRewardListener.java`, tests above.
Shared (own block only): `CopLoader.java`, `CopsNCrooksModuleConfig.java`, `cops.yml`.

---

## F2. Line-of-sight evasion and search zone

### Config — `settings.yml` `Wanted.Evasion` (parsed in gangland-api `Settings`)
```yaml
   Evasion:
      Enable: true
      Lost_Sight_Seconds: 3
      Drop_Mode: ONE_STAR                    # ONE_STAR | ALL_STARS
      Search_Radius: [40, 60, 90, 130, 180]  # blocks, per star
      Seconds_To_Drop: [10, 20, 30, 45, 60]  # unseen seconds, per star
      Outside_Zone_Speed: 2.0
      Hideout_Speed: 1.5                     # reserved for 0.14, parsed but unused
```
`Settings` (additive fields + parse lines next to the other `Wanted` keys, `NodeReader wantedEvasion = section(wanted, "Evasion", report)`):
`wantedEvasionEnabled` (true), `wantedEvasionLostSightSeconds` (3), `wantedEvasionDropMode` (String "ONE_STAR"),
`wantedEvasionSearchRadius` / `wantedEvasionSecondsToDrop` (`List<Integer>`, empty ⇒ the defaults above),
`wantedEvasionOutsideZoneSpeed` (2.0), `wantedEvasionHideoutSpeed` (1.5). All `private static @Getter`.

### New classes — package `org.luckyraven.gangland.copsncrooks.evasion`
- `EvasionState` enum: `SEEN, SEARCHING, NONE`.
- `EvasionDropMode` enum: `ONE_STAR, ALL_STARS`; `static EvasionDropMode parse(@Nullable String)` → ONE_STAR on junk.
- `EvasionConfig` record `(boolean enabled, int lostSightSeconds, EvasionDropMode dropMode, List<Integer> searchRadius,
  List<Integer> secondsToDrop, double outsideZoneSpeed, double hideoutSpeed)`; `static EvasionConfig fromSettings()`;
  `int radiusFor(int level)`, `int secondsToDropFor(int level)` (index `level-1` clamped to the list; min 1).
- `EvasionSnapshot` record `(EvasionState state, int level, double remainingSeconds, double totalSeconds,
  @Nullable Location zoneCenter, double zoneRadius)`; `static EvasionSnapshot none()`.
- `EvasionClock` — pure per-player state machine (no Bukkit calls except `Location` getters; unit-testable):
  ```java
  public boolean tick(long millisSinceSighting, @Nullable Location lastKnown, Location playerLocation,
                      int level, EvasionConfig config, double deltaSeconds)   // returns true when a star drop is due
  public void afterDrop()        // elapsed = 0, stay SEARCHING
  public EvasionState getState(); public EvasionSnapshot snapshot(int level, EvasionConfig config);
  ```
  Rules: `millisSinceSighting < lostSightSeconds*1000` ⇒ SEEN, elapsed = 0, `lastSeen = playerLocation`.
  Otherwise SEARCHING: center = `lastKnown != null ? lastKnown : lastSeen`; radius = `radiusFor(level)`;
  outside = horizontal distance (dx,dz; different world counts as outside) > radius;
  `elapsed += deltaSeconds * (outside ? outsideZoneSpeed : 1)`; due when `elapsed >= secondsToDropFor(level)`.
- `EvasionService implements BeanLifecycle`:
  ```java
  public EvasionService(JavaPlugin plugin, CopManager copManager, UserManager<Player> users /* @Qualifier("online") at the bean */,
                        DetainmentService detainment, WantedSettings wantedSettings)
  public void track(Player player)      // WantedStart / 0→n
  public void untrack(UUID playerId)    // WantedEnd / quit; fires change → NONE
  public boolean handles(@Nullable Player owner)   // enabled && group != null && group.isStaffed(); thread-safe (read-only)
  public EvasionState getState(UUID playerId)
  public EvasionSnapshot getSnapshot(UUID playerId) // EvasionSnapshot.none() if untracked / not handled
  ```
  One sync `runTaskTimer` every 20 ticks started lazily on first `track`, cancelled when empty and in
  `onPreClear/onShutdown`. Per tracked player: skip (state unchanged, clock paused) if offline, restrained
  (`detainment.isRestrained`) or downed (`DownedPlayerRegistry.isDowned`); if `!handles` ⇒ state NONE;
  else feed `group.getSquad().millisSinceSighting()` / `lastKnownLocation()` into the clock with `delta = 1.0`.
  On drop: `newLevel = ONE_STAR ? level-1 : 0`; `wanted.setLevel(newLevel)`; chat
  `wantedSettings.getWantedDecreasedMessageTemplate()` with `%level%`/`%stars%` (`Wanted.buildStars`);
  `clock.afterDrop()`. Fires `EvasionStateChangeEvent` whenever the state differs from last tick.
  `EvasionConfig.fromSettings()` is re-read each tick (cheap, reload-safe).

### Event — `cnc/events/evasion/EvasionStateChangeEvent` (sync, not cancellable, `@Getter`)
`(Player player, EvasionState oldState, EvasionState newState, EvasionSnapshot snapshot)`, standard `HandlerList`.

### Changes
- `cnc/npc/police/CopManager`: add `public @Nullable CopGroup getGroup(UUID playerId)` (keep `groupFor`).
- `cnc/npc/police/CopGroup`: `private volatile boolean staffed;` set `true` in `add(...)`; `public boolean isStaffed()`
  (via `@Getter`). "No cop group" in the spec = group missing or never staffed (e.g. no spawn location/Citizens).
- New listener `cnc/listener/evasion/EvasionListener` (`@ListenerHandler`, ctor `EvasionService`):
  - `WantedStartEvent` MONITOR ⇒ `track`; `WantedEndEvent` MONITOR, `PlayerQuitEvent` MONITOR ⇒ `untrack`.
  - `WantedEvent` LOWEST ignoreCancelled ⇒ `if (evasion.handles(event.getWanted().getOwner())) event.setCancelled(true)`.
    Old repeating decay therefore runs only as the fallback; evasion disabled ⇒ nothing is cancelled (today's behaviour).
- `CopsNCrooksModuleConfig`: `@Bean EvasionService evasionService(CopManager, @Qualifier("online") UserManager<Player>, DetainmentService, WantedSettings)`.
- `settings.yml`: add the `Evasion:` block under `Wanted:` with comments.

### Tests
`cnc-test/evasion/EvasionClockTest` (SEEN reset, SEARCHING counts, outside speed, drop due, null lastKnown fallback),
`cnc-test/evasion/EvasionConfigTest` (index clamp, drop-mode parse).

### Files owned by F2
`cnc/evasion/*`, `cnc/events/evasion/EvasionStateChangeEvent.java`, `cnc/listener/evasion/EvasionListener.java`,
gangland-api `Settings.java` (Evasion block), tests above.
Shared: `CopManager.java` (one accessor), `CopGroup.java` (staffed flag), `CopsNCrooksModuleConfig.java`, `settings.yml` (`Wanted.Evasion`).

---

## F4. Mixed squads and backup waves

### Config — `cops.yml`, under `Cops:` (parsed in `YamlCopConfigProvider`)
```yaml
Cops:
   Rosters:          # wanted star → { tier: count }
      1: { 1: 2 }
      2: { 1: 2, 2: 2 }
      3: { 2: 2, 3: 2 }
      4: { 3: 2, 4: 4 }
      5: { 4: 3, 5: 4 }
   Backup_Delay_Seconds: [15, 12, 10, 8, 6]   # per star
   Tiers: ...
```
(Write the rosters in block style in the shipped file to match the rest of cops.yml.) Missing `Rosters` ⇒ empty map
⇒ legacy `getTierForWantedLevel` + `getTargetCopCount`. Missing delay list ⇒ the defaults above.

### Changes
- `cnc/npc/police/config/CopConfigProvider`: add
  `default Map<Integer, Integer> getRoster(int wantedLevel) { return Collections.emptyMap(); }` and
  `default int getBackupDelaySeconds(int wantedLevel) { return 0; }`.
- `YamlCopConfigProvider`: fields `Map<Integer, Map<Integer,Integer>> rosters`, `List<Integer> backupDelaySeconds`;
  `loadRosters(NodeReader cops, ConfigReport)` (non-integer keys: `log.warn` and skip, like tiers; counts < 0 skipped);
  overrides of the two methods (delay index clamped; star with no roster ⇒ empty map).
- New `cnc/npc/police/spawn/RosterPlanner` (pure, static, unit-tested):
  `static Map<Integer,Integer> deficits(Map<Integer,Integer> roster, Map<Integer,Integer> currentByTier, int currentTotal, int maxTotal)`
  — per-tier missing counts in ascending tier order, total clamped so `currentTotal + sum <= maxTotal`.
- `CopSpawnManager`: `public Map<Integer,Integer> getRosterForWantedLevel(int wantedLevel)` (configured roster, else
  `Map.of(getTierForWantedLevel(l), getTargetCopCount(l))`) and `public int getBackupDelaySeconds(int wantedLevel)`.
- `CopGroup`: backup bookkeeping — `private int pendingBackup; private long backupDueAt; private boolean initialResponseDone;`
  with `addLosses(int count, long dueAt)` (adds to `pendingBackup`, sets `backupDueAt` only if not already scheduled),
  `boolean isBackupDue(long now)`, `void clearBackup()`, `markInitialResponseDone()`.
- `CopManager.startSpawnTask`: count cops released by the `removeIf` pass (marked for removal / invalid = lost);
  if lost > 0 and `initialResponseDone` ⇒ `group.addLosses(lost, now + delay*1000)`. Build `currentByTier` from
  `cop.getTierConfig().tier()`. `deficits = RosterPlanner.deficits(roster, currentByTier, cops.size(), maxPerPlayer)`;
  allowed now = `sum(deficits) - pendingBackup` (roster growth / initial response spawn at once) unless
  `isBackupDue(now)` ⇒ all, then `clearBackup()`. Spawn tier by tier via the existing `spawnNearPlayer(player, tier)`
  loop (same setup lines as today). After the first pass set `markInitialResponseDone()`.

### Tests
`cnc-test/npc/police/spawn/RosterPlannerTest`; roster/delay parsing test for `YamlCopConfigProvider` if a NodeReader
can be built in tests the way existing config tests do (check `CitizensAndConfigSafetyTest` first; skip otherwise).

### Files owned by F4
`RosterPlanner.java` (new), `CopConfigProvider.java`, `YamlCopConfigProvider.java`, `CopSpawnManager.java`, tests.
Shared: `CopManager.java` (spawn task only), `CopGroup.java` (backup fields), `cops.yml` (`Cops.Rosters`, `Cops.Backup_Delay_Seconds`).

---

## F3. HUD: boss bar, flashing stars, zone ring, escape compass

### Config — `cops.yml` top-level `Hud:` (parsed by `HudConfig.parse` in `CopLoader`)
```yaml
Hud:
   Enable: true
   Boss_Bar: true
   Search_Zone_Ring: true
   Escape_Compass: true
   Star_Gain_Title: true
   Messages:
      In_Sight: "%stars% &c&lIN SIGHT"
      Searching: "%stars% &e&lSEARCHING %time%"
      Wanted: "%stars% &c&lWANTED"          # state NONE (evasion off / fallback)
      Star_Lost: "%stars% &a&lSTAR LOST"
      Star_Gained_Title: "&c&lWANTED"
      Star_Gained_Subtitle: "%stars%"
```

### New classes — package `org.luckyraven.gangland.copsncrooks.hud`
- `HudConfig` (immutable, `@Getter`, `defaults()`, `parse(NodeReader root, ConfigReport)`).
- `HudFormat` (pure static, unit-tested): `String stars(int level, int max, boolean flashOff)` (flashOff ⇒ all grey
  `&7`, else `Wanted.buildStars` in red/yellow), `String time(double seconds)` → `m:ss`,
  `String apply(String template, String stars, String time)`.
- `EscapeCompass` (pure static math + per-player memory): `static Location pointOutside(Location center, double radius, Location player)`
  — horizontal ray from center through player to `radius + 5`, +X if player at center; instance
  `Map<UUID, Location> previousTargets`, `void point(Player, Location)` (remember `getCompassTarget()` once),
  `void restore(Player)`.
- `WantedHudService implements BeanLifecycle`:
  ```java
  public WantedHudService(JavaPlugin plugin, EvasionService evasion, @Qualifier("online") UserManager<Player> users, CopLoader copLoader)
  public void show(Player player)                 // create/update bar
  public void onStarGained(Player player, int newLevel, int maxLevel)   // title + BLOCK_NOTE_BLOCK_PLING
  public void onStarLost(Player player, int newLevel, int maxLevel)     // GREEN "STAR LOST" for 40 ticks, then normal
  public void hide(Player player)                 // bar.removeAll(), compass restore, forget
  ```
  One 20-tick sync task (lazy, cancelled when no bars / on shutdown). Per player: `EvasionSnapshot s = evasion.getSnapshot(id)`;
  SEEN ⇒ `BarColor.RED`, progress 1, `In_Sight`; SEARCHING ⇒ `BarColor.YELLOW`, progress `remaining/total`,
  stars flash by tick parity, `Searching`, ring + compass; NONE ⇒ RED full `Wanted`. Bukkit API only:
  `Bukkit.createBossBar(title, BarColor, BarStyle.SOLID)`, `BossBar#setTitle/setColor/setProgress/addPlayer/removeAll`,
  `Player#sendTitle(String,String,int,int,int)`, `Player#playSound`, `Player#setCompassTarget/getCompassTarget`,
  ring: `Player#spawnParticle(Particle.REDSTONE, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.RED, 1.5F))`,
  points = `min(64, max(16, (int) (2πr / 4)))`, y = player's y + 1, only points within 48 blocks of the player.
  Whole service no-ops when `Hud.Enable` is false.
- Listener `cnc/listener/hud/WantedHudListener` (`@ListenerHandler`, ctor `WantedHudService`):
  `WantedStartEvent` ⇒ `show` + `onStarGained`; `WantedLevelChangeEvent` MONITOR ignoreCancelled ⇒ gain/lost by
  comparing levels (newLevel 0 handled by end); `EvasionStateChangeEvent` ⇒ `show` (immediate refresh);
  `WantedEndEvent`, `PlayerQuitEvent`, `PlayerDeathEvent`, `PlayerDownedEvent` ⇒ `hide`.
- `CopLoader`: `private HudConfig loadedHudConfig;`. `CopsNCrooksModuleConfig`: `@Bean WantedHudService`.

### Tests
`cnc-test/hud/HudFormatTest`, `cnc-test/hud/EscapeCompassTest` (pointOutside math).

### Files owned by F3
`cnc/hud/*`, `cnc/listener/hud/WantedHudListener.java`, tests. Shared: `CopLoader.java`, `CopsNCrooksModuleConfig.java`, `cops.yml` (`Hud`).

---

## F6. Pursuit report and breaking news

### Config — `cops.yml` top-level `Pursuit_Report:` (parsed by `PursuitReportConfig.parse` in `CopLoader`)
```yaml
Pursuit_Report:
   Enable: true
   Breaking_News:
      Enable: true
      Min_Stars: 4
      Min_Duration_Seconds: 120
      Cooldown_Seconds: 300
   Messages:
      Report:
         - "&8&m----------&r &c&lPURSUIT REPORT &8&m----------"
         - "&7Outcome: %outcome%"
         - "&7Duration: &f%duration%  &7Top stars: %stars%"
         - "&7Cops down: &f%cops%"
      Outcome_Escaped: "&aESCAPED"
      Outcome_Busted: "&9BUSTED"
      Outcome_Wasted: "&4WASTED"
      Breaking_News: "&c&lBREAKING NEWS &7» &f%player% %outcome_plain% after a %duration% chase at %stars%&f, %cops% officers down."
```

### New classes — package `org.luckyraven.gangland.copsncrooks.report`
- `ChaseOutcome` enum `ESCAPED, BUSTED, WASTED`.
- `ChaseRecord` (mutable, `@Getter`): `long startedAt; int maxStars; int copsKilled; boolean wasted; long cuffedAt;`
  `void raiseStars(int)`, `void copKilled()`, `ChaseOutcome resolve(boolean restrained, long now)`
  (wasted ⇒ WASTED; restrained or cuffed within 30 s ⇒ BUSTED; else ESCAPED — covers evasion, decay and admin clear).
- `PursuitReportConfig` (immutable, `@Getter`, `defaults()`, `parse(NodeReader root, ConfigReport)`).
- `PursuitReportService`:
  ```java
  public PursuitReportService(CopLoader copLoader, DetainmentService detainment)   // max stars for %stars% via Settings.getWantedMaximumLevel()
  public void start(Player player, int level)
  public void raise(UUID playerId, int level)
  public void copKilled(@Nullable Player killer)
  public void markWasted(UUID playerId)
  public void markCuffed(UUID playerId)
  public void finish(Player player)          // resolve, send report lines, maybe broadcast, forget
  public void discard(UUID playerId)         // quit mid-chase: no report
  List<String> formatReport(ChaseRecord r, ChaseOutcome o, long now)   // package-private, unit-tested
  boolean shouldBroadcast(ChaseRecord r, long now)                     // Min_Stars OR Min_Duration, and global cooldown
  ```
  Broadcast via `Bukkit.broadcastMessage(ChatUtil.color(line))`; player lines via `ChatUtil.color`.
- Listener `cnc/listener/report/PursuitReportListener` (`@ListenerHandler`, ctor `PursuitReportService`):
  `WantedStartEvent` MONITOR ⇒ `start`; `WantedLevelChangeEvent` MONITOR ignoreCancelled ⇒ `raise`;
  `CopDeathEvent` MONITOR ⇒ `copKilled(event.getKiller())`; `CuffedEvent` MONITOR ⇒ `markCuffed(target)`;
  `PlayerDeathEvent` LOWEST and `PlayerDownedEvent` LOWEST ⇒ `markWasted` (runs before `WantedLevelListener`
  resets wanted through the kill-combo death callback); `WantedEndEvent` MONITOR ⇒ `finish`; `PlayerQuitEvent` ⇒ `discard`.

### Changes
- `cnc/listener/detainment/CopListener.onCopDeath`: after the `isCopNpc` check and before `cop.destroy()`,
  `if (cop != null) Bukkit.getPluginManager().callEvent(new CopDeathEvent(cop, event.getEntity().getKiller()));`
- `CopLoader`: `private PursuitReportConfig loadedPursuitReportConfig;`. `CopsNCrooksModuleConfig`: `@Bean PursuitReportService`.

### Tests
`cnc-test/report/ChaseRecordTest` (outcome resolution), `cnc-test/report/PursuitReportServiceTest`
(format placeholders, broadcast thresholds + cooldown).

### Files owned by F6
`cnc/report/*`, `cnc/listener/report/PursuitReportListener.java`, `CopListener.java` (one call), tests.
Shared: `CopLoader.java`, `CopsNCrooksModuleConfig.java`, `cops.yml` (`Pursuit_Report`).

---

## F5. Fines and bills instead of a chase tax

- `settings.yml`: `Wanted.Take_Money.Amount: 0` with a comment that it is the legacy per-star drain charged by the
  fallback decay timer (0 disables it). Keep the code default in `Settings` (`"50"`) untouched so a server that
  deleted the key behaves as before.
- `settings.yml` `User.Death.Money.Formula` comment: document `wanted` and `bounty` variables with the example
  `balance * 0.15 + wanted * 500`; shipped formula stays `balance * 0.15`.
- Audit (no code change expected): with evasion on, `WantedExecutor` money is skipped because its `WantedEvent` is
  cancelled; the only other per-star costs are detainment (`Per_Wanted_Level` bail/bribe/sentence), which are
  arrest costs, allowed. Record the audit result in the commit message.
- Optional doc touch: `documentation/features/wanted-bounty.md` (Heat, evasion, Take_Money default).

Files owned by F5: `settings.yml` (`Take_Money`, `User.Death.Money` comments only), `documentation/features/wanted-bounty.md`.

---

## Shared interfaces between features

| Producer | API | Consumers |
|---|---|---|
| F1 `HeatService` | `recordCrime`, `recordAssault`, `addHeat`, `lowerTo`, `clear`, `getHeat` | seam, HeatListener (F6 may show heat later) |
| F1 core seam | `WantedKillTracker#scoresAllKills/onHeatStarTrigger`, `WantedKillTrackers#isHeatActive/onHeatStarTrigger` | `EntityDamageListener` |
| impl `EntityDamageListener#handleWanted(user, targetLevel)` | the single star-gain path (timers, bounty, events) | F1 |
| F2 `EvasionService` | `getSnapshot(UUID)`, `getState(UUID)`, `handles(Player)` | F3 HUD, F2 guard listener |
| F2 `EvasionStateChangeEvent` | state transitions | F3 |
| F2 `CopManager#getGroup(UUID)`, `CopGroup#isStaffed()` | squad access | F2 (F4 reuses CopGroup) |
| F4 `CopSpawnManager#getRosterForWantedLevel`, `RosterPlanner` | spawn planning | CopManager |
| F6 `CopDeathEvent` (now fired) | cop kills | F6 (anyone later) |
| existing `WantedStartEvent`/`WantedLevelChangeEvent`/`WantedEndEvent` | wanted lifecycle | F1, F2, F3, F6, CopManager |

## Shared files (edit only your own block, in build order)

| File | F1 | F2 | F4 | F3 | F6 | F5 |
|---|---|---|---|---|---|---|
| `CopLoader.java` | heat field+parse | | | hud field+parse | report field+parse | |
| `CopsNCrooksModuleConfig.java` | HeatService bean, installCoreSeams | EvasionService bean | | WantedHudService bean | PursuitReportService bean | |
| `cops.yml` | `Heat:` | | `Cops.Rosters`, `Cops.Backup_Delay_Seconds` | `Hud:` | `Pursuit_Report:` | |
| `CopManager.java` | | `getGroup` | spawn task | | | |
| `CopGroup.java` | | `staffed` | backup fields | | | |
| `settings.yml` | | `Wanted.Evasion` | | | | `Take_Money`, death-formula comment |
| `Settings.java` (api) | | Evasion fields/parse | | | | |

Each new bean in `CopsNCrooksModuleConfig` goes under a new `// ---- <Feature> ----` banner after "Cop services";
each new `CopLoader` field/parse line goes after `loadedConfig` in `loadData`, always null-safe (`clear()` nulls it,
consumers fall back to `X.defaults()`).
