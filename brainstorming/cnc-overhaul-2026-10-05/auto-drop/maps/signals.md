# AUTO Drop Mode Signals Inventory

**Purpose:** Enumerate every per-chase signal already available or cheaply derivable that could decide how many stars an evasion drops under AUTO mode.

**Methodology:** For each signal, identify its source (file:line), whether it is recorded today, and what it would cost to record it (none = no instrumentation needed; cheap = add a counter or timestamp; expensive = requires new infrastructure).

---

## Heat & Crime Signals

| Signal | Source | Currently Recorded | Cost | Notes |
|--------|--------|-------------------|------|-------|
| **Chase heat total** | `HeatLedger.java:L155-157` | ✓ Yes | None | `heatOf(playerId)` returns current chase heat as `double` |
| **Crime count** | `HeatLedger.java:L166-168` | ✓ Yes | None | `chaseCrimes(playerId)` returns unmodifiable list; size is free |
| **Crimes per minute** | `HeatLedger.java:L166-168 + clock` | ✓ Yes | None | Derive: `crime_count / ((now - first_crime_time) / 60000)` |
| **Crime IDs in chase** | `CrimeRecord.java:L10` | ✓ Yes | None | List of crime IDs from `chaseCrimes()`; analyze mix (e.g., 2 kills vs 1 kill) |
| **Heat per crime** | `CrimeRecord.java:L10` | ✓ Yes | None | Each `CrimeRecord.heat()` records multiplied heat: base × streak × seenByCop × turf |
| **Last crime time** | `HeatLedger.java:L160-162` | ✓ Yes | None | `lastCrime(playerId).at()` → milliseconds; time since = `now - at` |
| **Last crime location** | `CrimeRecord.java:L10` | ✓ Yes | None | `lastCrime(playerId).location()` → Bukkit Location |
| **Streak active** | `HeatLedger.java:L99-102` | ✓ Yes | None | Check if time between last two crimes `<= streakWindowSeconds * 1000` |
| **Seen by cop flag** | `HeatLedger.java:L104` | ✓ Yes | None | Encoded in multiplier: if `mult > 1` after base, cop saw it |
| **Turf war active** | `HeatLedger.java:L105-106` | ✓ Yes | None | Encoded in multiplier: Crimes.KILL_PLAYER + contestedTurf → mult includes 0.5 |

---

## Chase Lifecycle Signals

| Signal | Source | Currently Recorded | Cost | Notes |
|--------|--------|-------------------|------|-------|
| **Chase start time** | `HeatLedger.java:L95-96` | ✗ No | Cheap | Add `long startMs` field to `Chase` class; record at creation |
| **Chase start cause** | `WantedCause` enum (L7-27) | ✓ Partial | Cheap | Available at WantedStartEvent (L18-19); store in Chase or HeatLedger |
| **Chase initial level** | `HeatLedger.java:L96` | ✓ Yes | None | `floorOf(wanted.getLevel(), maxLevel)` at chase creation |
| **Initial crime that started it** | `WantedCause.CRIME` (L9) | ✗ No | Cheap | On first crime after wanted → 0, flag it as chase-starter |
| **Current wanted level** | `EvasionClock.java:L104` | ✓ Yes | None | `wanted.getLevel()` via UserManager |
| **Max wanted level** | `EvasionClock.java:L62` | ✓ Yes | None | `wanted.getMaxLevel()` via UserManager |
| **Peak level in this chase** | `HeatLedger.java:L171-177` | ✗ No | Cheap | Track max level reached in Chase object |

---

## Evasion State Signals

| Signal | Source | Currently Recorded | Cost | Notes |
|--------|--------|-------------------|------|-------|
| **Evasion state** | `EvasionClock.java:L44,L117-119` | ✓ Yes | None | `Track.state` = SEEN, SEARCHING, EVADED; query via `snapshot(playerId)` |
| **Time in search zone** | `EvasionClock.java:L143-145,L189-195` | ✓ Partial | Cheap | Calculate from player location vs zone centre/radius; store cumulative ms |
| **Time outside search zone** | `EvasionClock.java:L189-195` | ✓ Partial | Cheap | `now - last_zone_enter_time` while outside |
| **Zone centre location** | `EvasionClock.java:L47,L130` | ✓ Yes | None | `Track.centre` (set at search start or re-centre) |
| **Zone radius** | `EvasionClock.java:L48,L143` | ✓ Yes | None | `Track.radius` from `EvasionSettings.radiusFor(level)` |
| **Distance from zone centre** | `EvasionClock.java:L189-195` | ✓ Yes | None | Player location distance to `Track.centre` |
| **Distance from last-known location** | `EvasionClock.java:L130` | ✓ Yes | None | Player location distance to `squad.lastKnownLocation()` |
| **Zone re-centres count** | `EvasionClock.java:L129-133,L135-137` | ✗ No | Cheap | Increment counter on zone restart (state → SEARCHING); track in Track or HeatLedger |
| **Seconds left to drop** | `EvasionClock.java:L46,L180` | ✓ Yes | None | `Track.secondsLeft` (updated each countdown tick) |
| **Progress toward drop (ms)** | `EvasionClock.java:L49,L145-146` | ✓ Yes | None | `Track.progress` accumulates per tick based on speed |
| **Speed multiplier active** | `EvasionClock.java:L144,L189-196` | ✓ Yes | None | 1.0 inside zone, `outsideZoneSpeed` outside; re-calc per tick |

---

## Cop Sighting & Awareness Signals

| Signal | Source | Currently Recorded | Cost | Notes |
|--------|--------|-------------------|------|-------|
| **Time since last sighting (ms)** | `CopGroup.java:L42` + `NpcSquad` (Keystone) | ✓ Yes | None | `squad.millisSinceSighting()` via Keystone API |
| **Last known location** | `CopGroup.java:L42` + `NpcSquad` | ✓ Yes | None | `squad.lastKnownLocation()` – location of last fresh sighting |
| **Has fresh sighting** | `NpcSquad` (Keystone) | ✓ Yes | None | `squad.hasFreshSighting()` (within lost-sight window) |
| **Sighting count** | `NpcSquad` (Keystone) | ✗ Partial | Cheap | NpcSquad tracks sightings; query count via `squad.getMarkings().size()` or similar |
| **Cops alive in group** | `CopGroup.java:L41,L99-103` | ✓ Yes | None | `cops.size()` from synchronized list |
| **Cops killed in chase** | `CopGroup.java:L81` | ✗ No | Cheap | Track in CopGroup: deaths - respawns; listen to CopDeathEvent |
| **Combat alert active** | `CopGroup.java:L56` | ✓ Yes | None | `combatAlert` boolean (set on escape attempt or hit) |
| **Cuff failures per player** | `CopGroup.java:L62` | ✓ Yes | None | `cuffFailures.get(targetId)` → count of break-free events |

---

## Backup & Regroup Signals

| Signal | Source | Currently Recorded | Cost | Notes |
|--------|--------|-------------------|------|-------|
| **Backup active** | `CopGroup.java:L59-60` | ✓ Yes | None | `backupUntil > now` (request() sets it) |
| **Backup remaining duration (ms)** | `CopGroup.java:L59` | ✓ Yes | None | `backupUntil - now` when active |
| **Backup extra cops count** | `CopGroup.java:L179-180` | ✓ Yes | None | `backupExtra(now, settings)` returns count |
| **Regroup active** | `CopGroup.java:L82` | ✓ Yes | None | `regrouping` boolean |
| **Regroup ready time (ms)** | `CopGroup.java:L83` | ✓ Yes | None | `regroupReadyAt - now` |
| **Casualty times** | `CopGroup.java:L81` | ✓ Partial | Cheap | List of recent death times; count/average gap → unit attrition rate |
| **Tier spawned** | `CopGroup.java:L47-48` | ✓ Yes | None | `lastTier` and `level` (wanted level at spawn) |

---

## Player State & Detainment Signals

| Signal | Source | Currently Recorded | Cost | Notes |
|--------|--------|-------------------|------|-------|
| **Currently handcuffed** | `EvasionClock.java:L110` + `DetainmentService` | ✓ Yes | None | `detainment.isHandcuffed(player)` or `isRestrained()` |
| **Break-free tap count** | `BreakFreeService.java:L42,L54-58` | ✓ Partial | Cheap | Store cumulative count (not just current counter); track success count |
| **Break-free attempts (total)** | `BreakFreeService.java:L46-72` | ✗ No | Cheap | Add counter to track total successful escapes per chase |
| **Cuffed → free transitions** | `DetainmentService` (implied) | ✗ No | Moderate | Track breaks in release/freedom timeline; add event/listener |
| **Player logged out/rejoin** | Bukkit events | ✓ Partial | Cheap | PlayerQuitEvent / PlayerJoinEvent timestamps; detect mid-chase rejoin |
| **Player health** | `Player.getHealth()` | ✓ Yes | None | Direct query; low health → fled while injured |
| **Player armor** | `Player.getInventory().getArmorContents()` | ✓ Yes | None | Calculate effective armor; durability may indicate combat |

---

## Configuration & Threshold Signals

| Signal | Source | Currently Recorded | Cost | Notes |
|--------|--------|-------------------|------|-------|
| **Heat thresholds** | `ChaseConfig.java:L47` / `wanted.yml:L15-20` | ✓ Yes | None | Star thresholds from config; compare heat to thresholds |
| **Seconds to drop** | `EvasionSettings.java:L30-31` / `wanted.yml:L75-80` | ✓ Yes | None | Per-level target seconds; compare against elapsed time |
| **Search radius** | `EvasionSettings.java:L24-26` / `wanted.yml:L68-73` | ✓ Yes | None | Per-level zone size |
| **Lost sight seconds** | `EvasionSettings.java:L9` / `wanted.yml:L64` | ✓ Yes | None | Threshold before search starts (default 3) |
| **Streak window** | `HeatLedger.java:L44,L99-102` / `settings.yml` | ✓ Yes | None | Configurable crime combo window |

---

## Derived / Composite Signals (Cheap to Calculate)

| Signal | Source | Currently Recorded | Cost | Notes |
|--------|--------|-------------------|------|-------|
| **Chase duration (seconds)** | `clock.getAsLong() - chase_start_time` | ✗ No | Free | Calculate at drop time |
| **Average heat per second** | `heat / chase_duration` | ✗ No | Free | Intensity of the chase |
| **Cop pressure (sighting frequency)** | `sighting_count / chase_duration` | ✗ No | Free | How often cops found the player |
| **Evasion intensity (zone re-centres)** | `zone_recentres / chase_duration` | ✗ No | Free | How mobile the player was |
| **Survival margin** | `seconds_left - 0` when evasion completed | ✓ Yes | None | How close to cap (negative = couldn't make it) |
| **Escape risk index** | `cuff_failures + break_free_attempts` | ✗ No | Cheap | Escalation: tried to escape multiple times |
| **Bouncing ratio** | `zone_recentres / chase_duration` | ✗ No | Free | Fleeing in/out vs steady evasion |

---

## Summary: Cost-Benefit Matrix

### Free (No Instrumentation)
- Chase heat, crime count, crime IDs, per-crime heat, heat thresholds
- Current wanted level, max level, evasion state
- Cop count, sighting time, combat alert, cuff failures
- Configuration (thresholds, timers, zones)

### Cheap (Add One Counter/Timestamp per Chase)
- Chase start time
- Peak wanted level reached
- Zone re-centre count
- Break-free success count (total)
- Time inside/outside zone (cumulative)
- Initial crime ID

### Moderate (Track State Changes)
- Cuffs-to-free transitions
- Mid-chase rejoin detection
- Cop kill count per chase

### Not Available Today (Would Need New Infrastructure)
- Detailed sighting location trail
- Cop tier progression history

---

## Recommended Instrumentation Plan

### Phase 1: Cheap Additions (1–2 hours)
Add these fields to `HeatLedger.Chase` class:
- `long startMs` – when the chase began
- `int peakLevel` – highest level reached
- `int zoneReCentres` – count of zone restarts
- `int breakFreeSuccesses` – cumulative escapes
- `long timeInsideZoneMs` – accumulated time in zone
- `String initialCrimeId` – the crime that started it

Add tracking in `HeatLedger.record()` (store first crime) and `EvasionClock.startSearch()` (increment zone count).

Add cumulative time tracker in `EvasionClock.tick()` by comparing `Track.centre` location on each tick.

### Phase 2: Use Signals for AUTO Logic
Once phase 1 is done, all signals needed for AUTO are available:
- Duration, heat, crime count, escalation markers (cuff failures, break-free count)
- Cop pressure (sighting time, combat alert)
- Survival performance (zone time ratio, re-centres)

### Not Recommended
- Cop kill count: requires new listener; marginal value
- Sighting trail: Keystone NpcSquad doesn't expose full history

---

## AUTO Drop Mode Example Logic Flow

At the moment stars.drop() is called:

1. **Query all signals** via `HeatLedger.heatOf()`, `HeatLedger.chaseCrimes()`, `EvasionClock.snapshot()`, `CopGroup` fields
2. **Score the chase:**
   - Base: config `Seconds_To_Drop` tier (1 star minimum)
   - Intensity: if heat > peak threshold OR crimes per minute > threshold → +1
   - Escalation: if cuff failures > 2 OR break-free attempts > 1 → +1
   - Duration: if chase > 5 minutes → +1
   - Survival: if player spent > 70% time inside zone → reduce by 1 (skilled)
3. **Cap** to `wanted.getLevel()` (never drop more than remaining)
4. **Fire event** so listeners can log/audit the decision

---

## Notes

- **Keystone NpcSquad API** (via `CopGroup.squad`): provides `millisSinceSighting()`, `lastKnownLocation()`, `hasFreshSighting()`. No need to re-instrument sighting awareness.
- **BreakFreeService** (L42): already tracks taps per player; add cumulative success counter to survive player logout.
- **Wanted.getLevel()** is the only source of truth; always available via `UserManager.getUser(player).getWanted()`.
- **All timestamps are in milliseconds** (system or clock supplier); use consistently.
