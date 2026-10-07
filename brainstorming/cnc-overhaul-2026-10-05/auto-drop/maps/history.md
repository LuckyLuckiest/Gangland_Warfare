# AUTO Drop_Mode: Chase History Architecture Map

**Scope:** Designing a "learning" AUTO mode that remembers past chases and decides when/how many stars to drop based on historical patterns.

**Key finding:** Gangland already tracks crimes per chase (HeatLedger.Chase.crimes list) and has a persistence layer built on Keystone repositories with autosave. An AUTO mode needs (1) a per-player persistent chase history table, (2) a server-wide aggregate stats table for pattern learning, and (3) event-driven capture of chase endings.

---

## 1. Current Transient Chase Tracking

### HeatLedger: In-Memory Chase State
- **Location:** `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/wanted/heat/HeatLedger.java`
- **Data:** Holds `Map<UUID, Chase>` with per-player `Chase` objects (L47-48, 171-178)
- **Chase record fields:** `double heat`, `List<CrimeRecord> crimes`
- **Crime record (L10):** `CrimeRecord(String crimeId, double heat, long at, Location location)`
- **Lifecycle:** Created on first crime (L95-96), cleared on chase end via `HeatListener.onChaseEnd()` (L34-35)

**Problem:** Entire chase history is lost on WantedEndEvent. No persistent record of patterns.

---

## 2. How Chases End Today (All Paths)

Every path fires **WantedEndEvent** with a `WantedCause`:

| End Cause | Trigger Location | Event/Method | Notes |
|-----------|------------------|--------------|-------|
| **EVASION** | EvasionClock.java:149 | `stars.drop()` → WantedLevelChangeEvent → WantedEndEvent (core logic) | Player evaded LoS for full timer at one level; Cause passed to WantedStars.drop() |
| **ARREST** | JailIntakeService.java:72 | `wantedClearContract.clearWanted(player.getUniqueId(), WantedCause.ARREST)` | Booked into jail; HeatLedger.clear() fires via HeatListener:35 |
| **DEATH** | EntityDamageListener.java:310 | `deadUser.getWanted().reset(WantedCause.DEATH)` | Player dies; WantedEndEvent fired, HeatLedger.clear() via HeatListener:35 |
| **DECAY** | WantedExecutor.java:68 | `stars.drop(context, 1, WantedCause.DECAY)` | Safety-net timer tick (unless WantedDecayPolicy handles it — evasion does) |
| **ADMIN** | WantedAddCommand/ClearCommand | `/glw wanted clear` | Manual reset by admin |
| **SIGN** | WantedSign.java | `[WANTED] INCREASE/REMOVE/CLEAR` | Sign interaction clears or increments |
| **BRIBE** | BribeService.java | Handcuff bribe → WantedCause.BRIBE | Arrested player pays to exit cuffs |
| **RESTORE** | UserDataLoader.java | Chase loaded on login | Historical level persisted in DB, restored at join |
| **LOGOUT** | N/A (implicit) | Player disconnect → no WantedEndEvent in 0.15.0 | Chase data only cleared in memory on next login or explicit event |

**Critical detail:** All clear paths call `HeatListener.onChaseEnd()` (L34-35), which calls `ledger.clear(playerId)` to wipe the in-memory chase. No permanent record is created.

---

## 3. Current Persistence Layer

### User Domain Table
- **Table:** `gangland-impl/src/main/java/org/luckyraven/gangland/database/tables/player/UserTable.java`
- **Columns (L17-27):**
  - `uuid` (PK), `balance`, `kills`, `deaths`, `mob_kills`, `bounty`, `level`, `experience`, `wanted` (current wanted level), `bounty_posters` (TEXT, added 0.15.0)
- **Load:** UserRepository.doLoadAll() @ DATABASE phase
- **Stats available:** kills, deaths, mobKills (per-player aggregate), current wanted level

### Repository Pattern
- **Base:** Keystone `AbstractRepository<T>` (extends provided scope, never shaded)
- **Registry:** `RepositoryRegistry` scans packages for `@Repository` classes
  - Core: `gangland-impl/database/repositories/player/`, `gangland-impl/database/repositories/plugin/` etc.
  - Module: `org.luckyraven.gangland.copsncrooks.database` (DetainmentRepository, JailRepository, CopSpawnerRepository, etc.)
- **Data supplier pattern:** Every manager (UserManager, JailManager, etc.) calls `setDataSupplier(...)` during initialize() so autosave via PeriodicalUpdates can snapshot and persist
  - Example: UserRepository field in UserManager holds the supplier lambda
- **Autosave:** PeriodicalUpdates.updatingDatabase() → RepositoryRegistry.saveAll() reads from data suppliers and upserts via TableBackend (L77-80)

### Keystone Database Support
- `org.luckyraven.keystone.persistence.database.backend.DatabaseBackend` (SPI)
- Gangland uses `SqliteBackend` and `MysqlBackend` (dialect upserts, batch transactions)
- Schema diffs applied on startup; UNIQUE and FK constraints honored

**Key rule:** Every `@Repository` class must wire `setDataSupplier(...)` to bind the manager's snapshot lambda, or autosave throws `No data supplier set`.

---

## 4. Module-Owned Repository Pattern (0.9.0+)

Runtime modules (cops-n-crooks, civilians, turf, etc.) own their repositories:
- **Location:** `<module>.database` package
- **Scan:** RepositoryRegistry scans with module classloader during bootstrap
- **Example:** CopsNCrooksModuleConfig.java calls `registry.scanAndRegisterRepositories("org.luckyraven.gangland.copsncrooks.database", moduleLoader.classLoader())`
- **Tables alongside repos:** Constants in same package (e.g., `DetainmentTable`, `JailTable`)
- **YAML defaults:** Module jar contains `resources/<module>/` YAMLs (e.g., `copsncrooks/cops.yml`, `copsncrooks/civilians.yml`) loaded by FileHandler with module classloader

**For AUTO Drop_Mode:** A new ChaseHistoryRepository would live in cops-n-crooks.database, scanning at startup; config in `copsncrooks/wanted.yml`.

---

## 5. Event Capture Points

### WantedLevelChangeEvent
- **Fired:** Every `Wanted.setLevel(newLevel, cause)` call (via public setLevel() and drop() paths)
- **Carries:** old level, new level, cause, player, wanted object
- **Use:** Track heat → stars progression during a chase
- **Listeners:** WantedHudListener (display), HeatListener (ledger sync), EvasionListener (evasion state sync)
- **File:** `gangland-core/src/main/java/org/luckyraven/gangland/core/events/wanted/WantedLevelChangeEvent.java`

### WantedEndEvent
- **Fired:** One final time when Wanted.setLevel() reaches 0 (chase cleared)
- **Carries:** player, wanted (now level 0), cause
- **Guarantees:** Called once per chase end, after level is set to 0
- **Listeners:** CopListener (cop state), EvasionListener (evasion OFF), HeatListener (ledger clear), WantedHudListener (HUD clear)
- **File:** `gangland-core/src/main/java/org/luckyraven/gangland/core/events/wanted/WantedEndEvent.java`

### CrimeCommittedEvent
- **Fired:** Every crime recorded (CrimeService.commit -> HeatListener.onCrime)
- **Carries:** player, crimeId, location, isSeenByCop flag
- **Data:** Used by HeatLedger to build Chase.crimes list and apply multipliers
- **File:** `gangland-api/src/main/java/org/luckyraven/gangland/events/crime/CrimeCommittedEvent.java`

**Strategy:** Capture WantedEndEvent + dump the HeatLedger.chaseCrimes(playerId) snapshot to a persistent table **before** HeatListener.clear() wipes it.

---

## 6. What a Per-Player Chase History Table Needs

### Schema (ChaseHistoryTable.java)
```
columns:
  id                  PK
  player_uuid         VARCHAR(36), indexed
  session_start_ms    BIGINT (when first crime was recorded)
  session_end_ms      BIGINT (when WantedEndEvent fired)
  duration_seconds    INT (computed: end - start / 1000)
  start_level         INT (wanted level when first crime occurred)
  end_level           INT (wanted level at WantedEndEvent)
  end_cause           VARCHAR(20) enum(EVASION, ARREST, DEATH, DECAY, ADMIN, SIGN, BRIBE, RESTORE)
  peak_level          INT (highest level reached)
  total_heat          DOUBLE (sum of crime heat values)
  crime_count         INT (number of crimes in this chase)
  unique_crime_ids    TEXT (comma-sep list or JSON, e.g., "KILL_PLAYER,SHOOT_GUN")
  arrest_location     VARCHAR(255) nullable (location at arrest, null for other ends)
  surrender_time_ms   INT nullable (delay from peak level to final drop for evasion)
  final_drop_mode     VARCHAR(10) enum(ONE_STAR, ALL_STARS) (what DropMode was used)
  created_at          BIGINT (timestamp)

indexes:
  (player_uuid, session_end_ms DESC) — fast "recent chases for player" queries
  (end_cause, session_end_ms DESC) — analyze by end type
```

### Data supplier (in CopsNCrooksModuleConfig)
- OnWantedEnd listener → read HeatLedger.chaseCrimes(playerId), assemble ChaseHistory object, add to in-memory list
- Manager.initialize() wires `setDataSupplier(() -> [list of dirty ChaseHistory objects])` for autosave
- On drop (via EvasionClock.149, WantedExecutor.68, etc.), capture drop_mode used (from DropMode enum)

---

## 7. What a Server-Wide Stats Table Needs

### Schema (DropStatsTable.java)
```
columns:
  id                      PK
  player_uuid             VARCHAR(36), indexed
  stat_date               DATE (YYYY-MM-DD for daily rollup)
  avg_chase_duration_sec  DOUBLE
  median_peak_level       INT
  avg_crimes_per_chase    DOUBLE
  evasion_escape_rate     DOUBLE (pct of EVASION causes that succeeded vs. arrests)
  arrest_rate             DOUBLE (pct of chases ending in ARREST)
  common_crime_ids        TEXT (JSON array of top N crime types)
  preferred_drop_trigger  VARCHAR(50) (heuristic: "AFTER_5_MINS", "ON_PEAK_DROP", "POST_ARREST", etc.)
  recent_mode_success     VARCHAR(10) (ONE_STAR or ALL_STARS: which ended more chases recently)
  sample_size             INT (how many chases this rollup covers)
  created_at              BIGINT
  updated_at              BIGINT
```

### Aggregation logic
- Runs once per hour (or daily): query ChaseHistory grouped by player_uuid, calculate rolling stats
- DropStatsTable feeds the AUTO decision logic: "Player X tends to evade after 5 min; use ONE_STAR drops. Player Y gets arrested every 2 min; use ALL_STARS."

---

## 8. Per-Player Chase History Table Implementation

### Repository (ChaseHistoryRepository.java)
```java
@Repository(ChaseHistory.class)
public class ChaseHistoryRepository extends AbstractRepository<ChaseHistory> { ... }
```

- Constructor: `public ChaseHistoryRepository(JavaPlugin plugin, DatabaseHandler db, DatabaseBackend backend)`
- Schema: ChaseHistoryTable (new Attribute fields as above)
- Load: `doLoadAll()` scans all rows (for server startup validation; optional for AUTO)
- Save: triggered by PeriodicalUpdates → RepositoryRegistry.saveAll()

### Data model (ChaseHistory.java record or class)
```java
public record ChaseHistory(
    UUID playerId,
    long sessionStartMs,
    long sessionEndMs,
    int startLevel,
    int endLevel,
    int peakLevel,
    double totalHeat,
    int crimeCount,
    String uniqueCrimeIds,
    WantedCause endCause,
    DropMode finalDropMode
) { }
```

### Manager (ChaseHistoryManager.java)
- Constructor: takes ChaseHistoryRepository, UserManager, HeatLedger
- Listens to WantedEndEvent (via @ListenerHandler on an inner listener class)
- On end event:
  1. Read HeatLedger.chaseCrimes(playerId) and lastCrime() to populate fields
  2. Create ChaseHistory object
  3. Add to in-memory `List<ChaseHistory> dirty`
  4. Call `repository.setDataSupplier(() -> new ArrayList<>(dirty))`; dirty.clear()
- Exposes getter: `List<ChaseHistory> recentChases(UUID playerId, int limit)` for AUTO logic to read

---

## 9. AUTO Mode Decision Logic Input

Once tables exist, the AUTO decision engine needs:
- **Per-player cohort:** Last N chases (e.g., last 10) from ChaseHistory
- **Aggregate stats:** DropStatsTable row for player
- **Current chase state:** HeatLedger.heatOf(playerId), HeatLedger.chaseCrimes(playerId), level
- **Config knobs (wanted.yml):**
  ```yaml
  Evasion:
    Drop_Mode: AUTO
    # Only if AUTO:
    Auto:
      History_Window_Chases: 10         # how many past chases to analyze
      Heat_Threshold_Per_Crime: 25      # average heat per crime before drop
      Duration_Threshold_Seconds: 300   # after 5 min with no arrest, prefer ONE_STAR
      Arrest_Immunity_Seconds: 60       # if last arrest was <60s ago, use ALL_STARS
      Evasion_Success_Threshold: 0.6    # if player evaded 60%+ of past chases, use ONE_STAR
  ```

---

## 10. Chase End Event Timeline (Example: Evasion Path)

```
1. Player commits crime → CrimeCommittedEvent → HeatListener.onCrime → HeatLedger.record()
   HeatLedger.Chase updated with CrimeRecord

2. Heat crosses star threshold → WantedLevelChangeEvent (via stars.drop trigger loop)
   HeatListener.onLevelChanged() syncs chase floor heat

3. Player in LoS evasion for full timer → EvasionClock.tick() (line 149)
   → stars.drop(user, mode == ALL_STARS ? level : 1, WantedCause.EVASION)
   → WantedStars.drop() → Wanted.setLevel(..., EVASION) → WantedLevelChangeEvent
   → If level reaches 0: Wanted.setLevel() fires WantedEndEvent

4. WantedEndEvent → HeatListener.onChaseEnd(event)
   → ledger.clear(playerId) [LOSS OF IN-MEMORY DATA]

5. (NEW) ChaseHistoryManager listener catches WantedEndEvent **before** HeatListener
   (via EventPriority.HIGH) → snapshot HeatLedger.chaseCrimes() → create ChaseHistory → add to dirty list

6. Next PeriodicalUpdates tick (every 5-10 min by default)
   → RepositoryRegistry.saveAll() → ChaseHistoryRepository upserts all dirty rows
```

---

## 11. Event Priority Chain for AUTO Mode Snapshot

To capture chase data **before** HeatListener.clear() runs:

```java
@ListenerHandler
public class ChaseHistoryListener implements Listener {

    private final ChaseHistoryManager manager;
    
    @EventHandler(priority = EventPriority.HIGH)  // runs BEFORE HeatListener (MONITOR)
    public void onWantedEnd(WantedEndEvent event) {
        manager.captureChaseEnd(event);
    }
}
```

ChaseHistoryManager.captureChaseEnd():
1. Get HeatLedger.chaseCrimes(playerId)
2. Get HeatLedger.heatOf(playerId)
3. Read current User.wanted for start_level, peak_level
4. Assemble ChaseHistory
5. Add to dirty list (manager will upsert on next periodical)

---

## Summary: 10 Critical Facts with File:Line

1. **HeatLedger in-memory chase state** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/wanted/heat/HeatLedger.java:47-48, 171-178` — Map<UUID, Chase> with crimes list cleared on end.

2. **Chase ending clears all data** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/listener/wanted/HeatListener.java:34-35` — ledger.clear() wipes crimes and heat on WantedEndEvent; no permanent record created.

3. **Eight end causes** — `gangland-core/src/main/java/org/luckyraven/gangland/core/wanted/WantedCause.java:7-28` — EVASION, ARREST, DEATH, DECAY, ADMIN, SIGN, BRIBE, RESTORE carried by every Wanted.setLevel() call.

4. **WantedEndEvent is single clearing point** — `gangland-core/src/main/java/org/luckyraven/gangland/core/events/wanted/WantedEndEvent.java:11-42` — fired once when level reaches 0; all paths route through here.

5. **User stats exist for kills/deaths/mobKills** — `gangland-impl/src/main/java/org/luckyraven/gangland/database/tables/player/UserTable.java:19-20` — persisted in user table; available for historical trend analysis.

6. **Module repository pattern** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/database/DetainmentRepository.java:24-40` — @Repository classes in <module>.database scanned at startup; schema auto-created via Keystone backend.

7. **Autosave via data suppliers** — `gangland-impl/src/main/java/org/luckyraven/gangland/bootstrap/PeriodicalUpdates.java:77-80` — RepositoryRegistry.saveAll() reads from manager data suppliers every interval; all repos must call setDataSupplier().

8. **Evasion drop call passes DropMode** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/wanted/evasion/EvasionClock.java:149` — stars.drop(..., mode == ALL_STARS ? level : 1, WantedCause.EVASION); AUTO mode needs to compute this value here.

9. **CrimeRecord fields** — `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/wanted/heat/CrimeRecord.java:10` — crimeId, heat (post-multiplier), at (ms), location; available via HeatLedger.chaseCrimes() before clear().

10. **High priority listener captures before clear** — Add ChaseHistoryListener with `@EventHandler(priority = EventPriority.HIGH)` on WantedEndEvent to snapshot HeatLedger before HeatListener's MONITOR priority runs and calls clear().
