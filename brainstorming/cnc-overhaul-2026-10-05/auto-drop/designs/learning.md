# AUTO Drop_Mode - design B: "it learns" (designer: learning)

Status: plan only. No source was edited. Graphify oriented first (`graphify query "EvasionClock"`, EvasionClock.java L37); every
`file:line` below was re-read in the source on 2026-10-07 (branch 0.15.1).

Paths: `EC` = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks`,
`ECR` = `gangland-features/cops-n-crooks/src/main/resources/copsncrooks`, `CORE` = `gangland-core/src/main/java/org/luckyraven/gangland/core`.

Two corrections to the brief, both verified:
- The module YAML folder is `copsncrooks/` since commit e57da275, not `npc/`. The files are `ECR/wanted.yml` and `ECR/wanted_messages.yml`.
- `GanglandApi.VERSION` is already "2.2" in the uncommitted working tree (`gangland-api/.../GanglandApi.java:33`, from the 0.15.1 settings-ownership
  wave; 0.15.0 shipped 2.1). This plan's api bump is therefore "next minor after 0.15.1 lands" = 2.3 (cops-n-crooks `module.yml:6` already says `Host_Api: 2.2`).

---

## 1. The idea in plain words

AUTO answers three questions every time the cops lose you:

1. How long must you hide before a star falls? (time)
2. How many stars fall? 1, 2, N or all. (size)
3. Is this player someone who always gets away, or someone who always gets caught? (habit)

The first two come from a small, fixed formula over live chase facts. The third, and the yardsticks the formula uses, are **learned from recorded
chase outcomes**:

- **Server-wide, per star level**: how often chases that peaked at that level end in a clean escape, and how long those escaped chases usually last.
  This is "typical chase length" `T(level)`. A chase that runs much longer than typical has "dragged on" and is loosened (more stars per drop, shorter waits).
- **Per player**: whether he escapes more or less often than the server would expect for the mix of chases he has had. This is "habit" `delta`.
  Habitual escapers are tightened (longer wait, never more than one star per drop). Habitual losers are loosened (shorter wait).

Everything is a handful of numbers per player and per level, updated by one fixed rule at the end of each chase. No ML library, no randomness: the same
stored numbers and the same chase facts always give the same answer.

**Cold-start guarantee (the safety invariant): with no stored data, and a chase that is not yet "long" (ratio below `Extra_Star_Ratio`), AUTO behaves
exactly like ONE_STAR.** A fresh server loses nothing by switching AUTO on.

Mapping to the owner's wording:

| Owner phrase | What AUTO uses |
|---|---|
| "after a specific period" | the per-level wait `Seconds_To_Drop[level]` times a bounded factor `f` (section 5) |
| "how long was the chase" | `age` = wall-clock since the chase began, minus time spent cuffed; compared with the learned typical length `T` as the ratio `r = age / T` |
| "how it started" | `startCause`. Only chases that began from a real crime count for learning; ADMIN/SIGN/RESTORE starts are played with AUTO but never learned from. Peak level picks the statistics row |
| "how it might end" | the learned escape rate: observed escapes minus the escapes the server would expect = `delta` |
| "one star, two, x, or all" | `N` = 1 base; extra stars as `r` grows past `Extra_Star_Ratio`; all stars at `All_Stars_Ratio`; capped to 1 for habitual escapers |

---

## 2. Decision inputs and where each is read

| Input | Read from | Today? |
|---|---|---|
| level now | `wanted.getLevel()`, EvasionClock.java:115 | yes |
| base wait | `cfg.secondsToDropFor(level)`, EvasionClock.java:147 (EvasionSettings.java:30) | yes |
| in-zone speed | `speed(...)`, EvasionClock.java:144, 189-196 | yes |
| progress | `track.progress`, EvasionClock.java:145 | yes, but the Track is thrown away on every SEEN (EvasionClock.java:117-121) |
| restrained (pause) | `detainment.isRestrained(player)`, EvasionClock.java:110 | yes |
| chase start time, start cause, start level, peak level | NEW `ChaseLog`, fed by `WantedLevelChangeEvent` (old 0 -> new > 0 starts it; any rise updates the peak) | no. HeatLedger has no start field (HeatLedger.java:171-178, `Chase` = heat + crimes only) |
| time spent cuffed | NEW `ChaseLog.pausedMs`, added in the clock's pause branch (EvasionClock.java:110-113) when `isRestrained` | no |
| chase outcome | `WantedEndEvent` cause (`CORE/wanted/WantedCause.java:7-27`: EVASION, DECAY, ARREST, BRIBE, DEATH, ADMIN, SIGN, RESTORE, UNKNOWN) | event exists; nothing records it |
| player habit `delta` | NEW `ChaseLearner` (cache of `ChaseHabit` rows) | no |
| typical length `T(peak)`, escape rate `p(peak)` | NEW `ChaseLearner` (cache of `ChaseServerStat` rows) | no |

Deliberately NOT used in v1 (cost not worth it for the "learns" angle): heat total and crime mix (`HeatLedger.heatOf` L155, `chaseCrimes` L166), sighting
count, zone re-centres, cop casualties, cuff failures. They stay available for the sibling designs; the learner's per-chase record has room to add them
(section 6, "extension point").

`age` uses wall-clock on purpose (`track.progress` is speed-weighted milliseconds, EvasionClock.java:145, so a 20 s need can be 10 real seconds outside
the zone; using it as "chase length" would be wrong).

---

## 3. When the decision is made

- **The wait (time factor `f`) is decided at the start of each search** (`startSearch`, EvasionClock.java:166-176) and again when the level changes
  mid-search (EvasionClock.java:142, `track.level = level`). It is stored in the `Track` and frozen, so the HUD countdown does not jump around. It uses the
  `delta` and the ratio `r` of that moment. Since a new search starts after every SEEN and after every drop, a long chase is re-evaluated often and "loosens" as it drags on.
- **The size `N` is decided at drop time only**, at EvasionClock.java:148-149, from the final `age`.
- **A preview of `N` is computed while counting down** (inside `fireCountdown`, EvasionClock.java:179-186): `rAtDrop = (age + secondsLeft*1000) / T`, then the
  same size function. This is only for the HUD "(-2)" hint; it never drops anything.
- Nothing is decided outside the evasion tick. The `Repeating_Timer` safety net is untouched (section 5).
- **Learning happens once per chase, at `WantedEndEvent`**, never mid-chase. A player who quits mid-chase (no end event; "LOGOUT" is not a cause in 0.15.0)
  records nothing, which is deliberate: abandoning a chase neither helps nor hurts his habit.

---

## 4. Output

`Plan(stars, factor, reason)`:

- `stars` in `1..level` (never 0: a zero drop must not call `stars.drop` because that returns 0 and the clock reads 0 as "no drop", EvasionClock.java:152).
- `factor` in `[Min_Time_Factor, Max_Time_Factor]`.
- `reason` one of `BASELINE`, `LONG_CHASE`, `COLD_TRAIL` (all stars), `HABIT_CAP` (a long chase would have given more, but the habit cap held it to 1).

Size function (`r` = age / T):

```
if r < Extra_Star_Ratio:                 N = 1                                  (BASELINE)
else:                                    N = 1 + (1 + floor((r - Extra_Star_Ratio) / Extra_Star_Step_Ratio))   (LONG_CHASE)
if All_Stars_Ratio > 0 and r >= All_Stars_Ratio:  N = level                    (COLD_TRAIL)
N = min(N, level, Max_Stars_Per_Drop)    -- except COLD_TRAIL, which ignores Max_Stars_Per_Drop
if delta >= Habitual_Escaper_Delta:      N = 1                                  (HABIT_CAP when it changed N)
```

Time factor (frozen per search):

```
f = clamp( (1 + Habit_Time_Strength * delta) * (1 - Drag_Relief * clamp(r - 1, 0, 1)), Min_Time_Factor, Max_Time_Factor )
need = max(1000 ms, Seconds_To_Drop[level] * 1000 * f)
```

The drop call becomes: `stars.drop(user, plan.stars(), WantedCause.EVASION)` (the one line EvasionClock.java:149 replaces when `dropMode == AUTO`;
ONE_STAR and ALL_STARS keep their exact current code).

### Interaction with Seconds_To_Drop and Repeating_Timer

- AUTO never skips the wait. A drop still needs `progress >= need`; AUTO can only shorten `need` to `Min_Time_Factor` (default 0.6) of the configured time.
  `Seconds_To_Drop` stays the human-readable baseline and the thing owners tune. Speed-weighting (`Outside_Zone_Speed`) works unchanged on top.
- After a multi-star drop the clock goes to EVADED and re-enters SEARCHING at the new, lower level (EvasionClock.java:135-137), picking that level's
  `Seconds_To_Drop` and a fresh `f`. A drop to 0 ends the chase: the end event has already cleared him and fired OFF (EvasionClock.java:151-152).
- `Repeating_Timer`: while a live cop hunts, the clock owns decay (`handlesDecay`, EvasionClock.java:73-76; `WantedExecutor.java:83-99` returns early).
  When the last live cop goes RETURNING the clock clears the track (EvasionClock.java:99-101) and the timer takes over at one star per interval with cause
  DECAY. AUTO does not touch this. Its learner records such an end as a half escape (o = 0.5, section 6).
- Pricing: `WantedStars.drop` charges Take_Money per star dropped (`CORE/wanted/WantedStars.java:129-134`), so an AUTO 3-star drop is charged like ALL_STARS today. Documented, not changed.

---

## 5. Stored data and update rule

### 5.1 What is stored (two tiny tables, module-owned, `EC/database`)

`chase_habit` - one row per player who ever finished a countable chase. Immutable record `ChaseHabit` replaced as a whole in a `ConcurrentHashMap` (so the
async autosave can never read half an update).

| column | type | meaning |
|---|---|---|
| `uuid` | String PK | player |
| `n` | double | decayed number of countable chases (max about 10 at `Decay_Per_Chase` 0.90) |
| `actual` | double | decayed sum of outcomes `o` (1 escape, 0.5 timer/decay end, 0 caught) |
| `expected` | double | decayed sum of the server escape rate `p(peak)` at the time of each of his chases |
| `last_at` | long | millis of the last counted outcome (drives the rate limit and the time decay) |

`chase_server_stat` - five rows, `level` 1..5 as PK (rows are created on first use; a missing row means "cold start").

| column | type | meaning |
|---|---|---|
| `level` | int PK | the chase's PEAK level |
| `n` | double | decayed weighted chase count |
| `escaped` | double | decayed weighted sum of `o` |
| `median_len_s` | double | running median of the length of fully escaped chases, seconds |
| `len_count` | double | how many lengths fed the median (capped at 100) |
| `updated_at` | long | millis |

Why two scalars `actual`/`expected` instead of a raw escape rate: a player who mostly runs 5-star chases escapes rarely and that is normal. Comparing him
to the server's own rate for the levels he actually played removes the level mix from his habit. It is also O(1) storage and fully explainable.

### 5.2 Chase classification (at `WantedEndEvent`)

| End cause | counted? | outcome `o` |
|---|---|---|
| EVASION (last star dropped by the clock) | yes | 1.0 (ESCAPED) |
| DECAY (timer ended it, the cops had given up) | yes | 0.5 |
| ARREST, BRIBE, DEATH | yes | 0.0 (CAUGHT) |
| ADMIN, SIGN, RESTORE, UNKNOWN | no | - |

A chase is counted only if: it started from a CRIME (`startCause`), lasted at least `Min_Chase_Seconds` (cuffed time excluded), peak level >= 1, and the
player's previous counted outcome is at least `Min_Seconds_Between_Outcomes` ago. Learning can be switched off completely (`Learning.Enable: false`).

### 5.3 The update rule (one function, pure given `now`)

Let `L` = peak level, `o` = outcome, `len` = effective chase seconds, `hl` = `Half_Life_Days`, `d` = `Decay_Per_Chase`, `sd` = `Server_Decay_Per_Chase`.

```
# 0. server expectation BEFORE this chase is added (so a chase never grades itself)
p_L = (S[L].escaped + Server_Prior_Chases * Cold_Start_Escape_Rate[L]) / (S[L].n + Server_Prior_Chases)

# 1. player row
g = 0.5 ^ (max(0, now - H.last_at) / (hl days))          # idle players drift back to neutral
H.n = H.n*g*d + 1;  H.actual = H.actual*g*d + o;  H.expected = H.expected*g*d + p_L;  H.last_at = now

# 2. server row, weighted so a grinder cannot own the statistics
w = min(1, Server_Fair_Share_Chases / max(1, H.n_before))
S[L].n = S[L].n*sd + w;  S[L].escaped = S[L].escaped*sd + w*o
if o == 1:  m = S[L].median_len_s ; if len_count == 0: m = len else m += sign(len - m) * Median_Step * m * w
            len_count = min(100, len_count + w)
```

Reading it back:

```
delta     = clamp( (H.actual - H.expected) / (H.n + Prior_Chases), -1, 1 )        # 0 for a stranger
T(L)      = (S[L].median_len_s * S[L].len_count + Cold_Start_Typical_Seconds[L] * Server_Prior_Chases) / (S[L].len_count + Server_Prior_Chases)
```

Properties (each becomes a unit test):
- **Cold start**: no rows -> `delta = 0`, `T = Cold_Start_Typical_Seconds[L]`, `p = Cold_Start_Escape_Rate[L]`. Nothing is read from a database that may not exist yet.
- **Shrinkage**: a player with one chase has `delta` near 0 because `Prior_Chases` (5) sits in the denominator. Habit needs a streak, not an accident.
- **Bounded**: `n <= 1/(1-d)` = 10, server `n <= 100`, `delta` in [-1, 1], `f` in [Min, Max], `N <= level`.
- **Forgetting**: old chases fade by `d` per newer chase and by half-life in idle time. A reformed player stops being "habitual" within roughly 10 chases or a month idle.
- **Median without a sort**: the running-median step moves toward the new sample by 10 % of the current median; it needs one number, tracks drift, and ignores outliers.
- **Deterministic**: no clock reads inside the maths (the caller passes `now`), no randomness, no map-iteration dependence. Replaying the same outcome list gives the same rows.

### 5.4 Persistence

- Repositories: `ChaseHabitRepository` and `ChaseServerStatRepository` in `EC/database`, `@Repository(ChaseHabit.class)` / `@Repository(ChaseServerStat.class)`,
  constructor `(JavaPlugin, DatabaseHandler, DatabaseBackend)`, `getTable()` and `doLoadAll()` like `JailExitRepository` (EC/database/JailExitRepository.java:21-80).
  The module scan already finds this package. Tables via `Attribute` as in `JailExitTable`.
- A manager (`ChaseLearner`) calls `repository.setDataSupplier(cache::values)` in its constructor (same pattern as `DetainmentRegistry.java:29`), or autosave throws "No data supplier set".
  Autosave and the shutdown flush then upsert every row. Ceiling: a full snapshot per autosave is fine up to tens of thousands of players; upgrade path = a dirty set.
- Load once at startup (`doLoadAll`). Rows idle longer than `Forget_After_Days` (default 90) are skipped on load and deleted, which keeps the table bounded.
- Migration: new tables only, created by the backend diff engine; nothing existing changes. Dropping AUTO leaves two harmless tables.
- Network note: the server rows are keyed by level only, so two servers sharing one MySQL would overwrite each other (last writer wins). Out of scope; if it matters, add `server_id` to the PK later.

---

## 6. Resistance to farming and exploits

| Attack | Why it fails or is bounded |
|---|---|
| Spam tiny chases to pile up "escapes" | `Min_Chase_Seconds` (30), `Min_Seconds_Between_Outcomes` (180), ADMIN/SIGN/RESTORE ignored. And escapes make him TIGHTER, so there is nothing to gain |
| "Lose-to-win": get arrested/killed on purpose to look like a habitual loser and get faster drops | every caught chase has to last 30 s and be 3+ minutes apart; the loosening is capped by `Min_Time_Factor` (0.6) and affects the wait only, never the star count; arrest costs jail time and a fine, the cost is far above a 40 % shorter wait |
| One grinder skews the server's typical length / escape rates | server weight `w = min(1, Server_Fair_Share_Chases / n)`, so a heavy player counts for a fraction; median moves 10 % per sample at most |
| Alt accounts or a friend feed easy escapes at level 1 | they only change the level-1 row, which only changes the level-1 `T` and `p`; the effect on `delta` of others is through `expected`, which moves by at most `w` per chase |
| Sit in a sealed room (the N1 pattern) to farm drops | the timer still runs per level at normal speed; long chases need real time; `T` default (3 times the ladder time) means a clean hide never reaches `Extra_Star_Ratio` |
| Relog to reset chase age | resets `age` to 0 -> fewer extra stars, so no gain. A restored chase (cause RESTORE) is played but not learned from |
| Edit the DB by hand | a fresh `delta` from edited rows is still clamped, `f` and `N` are bounded, and `Learning.Enable: false` is the kill switch |
| Take_Money charges per star | documented side effect; owners who dislike it keep `Max_Stars_Per_Drop: 1` and `All_Stars_Ratio: 0` |

Extension point: `ChaseLearner.record(ChaseRecord)` takes a small immutable record (peak level, outcome, effective seconds, start cause, end cause). Heat,
sightings or cuff failures can be added to that record and to the formulas later without changing the tables for existing fields.

---

## 7. Config (module-owned, `ECR/wanted.yml`, under `Wanted.Evasion`)

`Drop_Mode` accepts `AUTO` today with no parser change (`ChaseConfig.dropMode`, ChaseConfig.java:80-94 matches `DropMode.values()` case-insensitively), so the enum
constant plus the new block is the whole config change. Shipped YAML (block style, Capitalized_Underscore, every key commented):

```yaml
      # What a completed evasion takes: ONE_STAR, ALL_STARS or AUTO (AUTO decides from how long the chase has run and what
      # chases usually look like on this server; the Auto block below only matters for AUTO)
      Drop_Mode: ONE_STAR

      # Only read when Drop_Mode is AUTO. With no learned data and a short chase AUTO behaves exactly like ONE_STAR
      Auto:
         # Most stars one drop may take (the cold-trail all-stars case ignores this)
         Max_Stars_Per_Drop: 3
         # A chase this many times longer than a typical one starts handing out extra stars
         Extra_Star_Ratio: 1.5
         # Every further step of this size adds one more star
         Extra_Star_Step_Ratio: 0.75
         # A chase this many times longer than typical drops every star at once (0 = never)
         All_Stars_Ratio: 3.0
         # The wait can shrink to this share of Seconds_To_Drop, never lower
         Min_Time_Factor: 0.6
         # ...and grow to this share, never higher
         Max_Time_Factor: 1.6
         # How much shorter the wait gets once a chase runs past typical (0 = not at all)
         Drag_Relief: 0.3
         # How strongly a player's escape habit stretches (escaper) or shortens (usually caught) the wait
         Habit_Time_Strength: 0.8
         # A player at or above this escape habit never gets more than one star per drop
         Habitual_Escaper_Delta: 0.30
         # What a "typical" chase lasts, in seconds, for chases that peak at 1, 2, 3, ... stars; used until the server has its own data
         Cold_Start_Typical_Seconds:
            - 90
            - 180
            - 300
            - 420
            - 600
         # Learning from finished chases
         Learning:
            # false = no database, no habit, AUTO uses the cold-start numbers only
            Enable: true
            # Chases of evidence a player needs before his habit counts fully (a larger number = slower to judge)
            Prior_Chases: 5
            # Same for the server-wide numbers
            Server_Prior_Chases: 20
            # How much an older chase still counts after each newer one (0.90 = about the last 10 chases)
            Decay_Per_Chase: 0.90
            # Same for the server-wide numbers
            Server_Decay_Per_Chase: 0.99
            # Days of inactivity after which a player's habit has faded by half
            Half_Life_Days: 30
            # How often chases peaking at 1, 2, 3, ... stars end in a clean escape on a fresh server
            Cold_Start_Escape_Rate:
               - 0.90
               - 0.75
               - 0.55
               - 0.35
               - 0.20
            # How far one escaped chase moves the typical length (share of the current value)
            Median_Step: 0.10
            # Chases shorter than this (cuffed time excluded) are not learned from
            Min_Chase_Seconds: 30
            # A player's chases closer together than this are not learned from
            Min_Seconds_Between_Outcomes: 180
            # A player with more recent chases than this counts for less in the server-wide numbers
            Server_Fair_Share_Chases: 4
            # Habit rows of players idle this long are deleted
            Forget_After_Days: 90
```

### Validation (reported through `ConfigReport`, the way ChaseConfig.java:90-91 already does)

Each failure is a WARNING at the key's source location with code `config.range` (or `config.enum` / `config.length`), and the key falls back to its default.

| Key | Rule | On failure |
|---|---|---|
| `Drop_Mode` | ONE_STAR, ALL_STARS, AUTO (message text at ChaseConfig.java:91 must list AUTO) | ONE_STAR |
| `Max_Stars_Per_Drop` | >= 1 | 3 |
| `Extra_Star_Ratio`, `Extra_Star_Step_Ratio` | > 0 | defaults |
| `All_Stars_Ratio` | 0, or >= `Extra_Star_Ratio` | 0 (never) |
| `Min_Time_Factor` / `Max_Time_Factor` | 0 < Min <= 1 <= Max <= 4 | both defaults |
| `Drag_Relief` | 0..0.9 | 0.3 |
| `Habit_Time_Strength` | 0..2 | 0.8 |
| `Habitual_Escaper_Delta` | 0.05..1 | 0.30 |
| `Cold_Start_Typical_Seconds` | non-empty, every entry >= 10, ascending; shorter than 5 entries is padded with the last (same clamp as `radiusFor`, EvasionSettings.java:25) with an INFO | defaults |
| `Cold_Start_Escape_Rate` | entries in 0..1 | defaults |
| `Prior_Chases`, `Server_Prior_Chases` | >= 1 | 5 / 20 |
| `Decay_Per_Chase`, `Server_Decay_Per_Chase` | 0.5..1 | 0.90 / 0.99 |
| `Half_Life_Days`, `Forget_After_Days` | >= 1 | 30 / 90 |
| `Median_Step` | 0.01..0.5 | 0.10 |
| `Min_Chase_Seconds`, `Min_Seconds_Between_Outcomes`, `Server_Fair_Share_Chases` | >= 0, >= 0, >= 1 | 30 / 180 / 4 |

Record change: `EvasionSettings` (EvasionSettings.java:16) gets a 7th component `AutoSettings auto`. Keep the current 6-argument constructor delegating with
`AutoSettings.DEFAULT` so `new EvasionSettings(...)` in `EvasionClockTest.java:212, 268` and `ChaseConfig.java:73` keep compiling. A reload applies at once
because the clock reads `config.get()` on every tick (EvasionClock.java:97).

---

## 8. Code shape (all in cops-n-crooks; `gangland-impl`, core and every other module untouched)

New package `EC/wanted/auto`:

- `AutoSettings` (record, parsed in `ChaseConfig.evasion(...)`, ChaseConfig.java:66-78).
- `AutoDropPlanner` - pure: `plan(level, effAgeMs, delta, typicalSeconds, settings)` -> `Plan`. No Bukkit types. The only place the size/time formulas live.
- `ChaseLog` - per-player in-memory facts: `startedAt`, `startCause`, `startLevel`, `peakLevel`, `pausedMs`, `lastTouch`. Listener-fed, cleared at end, never persisted.
- `ChaseLearner` - owns the two caches and repositories; `record(ChaseRecord)`, `delta(UUID, now)`, `typicalSeconds(level)`, `escapeRate(level)`. Pure given `now`.
- `ChaseHabit`, `ChaseServerStat` - immutable records (entities).
- `ChaseOutcomeListener` (`@ListenerHandler`, MONITOR) - `WantedLevelChangeEvent` (start / peak), `WantedEndEvent` (classify + record + clear), `PlayerQuitEvent` (clear the log; no outcome).

Changes in existing code:

1. `DropMode.java:5-8`: add `AUTO`.
2. `EvasionClock` constructor (EvasionClock.java:62-63) gains `AutoDropPlanner` and `ChaseLog`; update `EvasionModuleConfig.java:28-33` and `EvasionClockTest.setUp` (EvasionClockTest.java:73 on).
3. `Track` (EvasionClock.java:42-51) gains `double factor = 1` and `int plannedStars`.
4. `tick`: in the pause branch (L110-113) call `log.touch(id, now, restrained)`; every other branch calls `log.touch(id, now, false)`. At `startSearch` (L166-176) and on a level change (L142) compute `factor` when mode is AUTO. At L147 `need = secondsToDropFor(level) * 1000 * track.factor` (only AUTO changes it). At L149 call the planner when AUTO (ONE_STAR/ALL_STARS keep the current expression). The whole AUTO branch sits in a try/catch that logs once and falls back to ONE_STAR behaviour, so a planner bug can never freeze a chase.
5. `fireCountdown` (L179-186) computes `plannedStars` and fires the event with it.
6. `StarCard.dropCard` (StarCard.java:35-43) gets an overload `(messages, cause, dropped, remaining)`; `WantedHudListener.onLevelChange` (WantedHudListener.java:80-81) already has `event.getOldLevel()`/`getNewLevel()`, so `dropped = old - new` needs **no api change**.
7. `ECR/wanted.yml`, `ECR/wanted_messages.yml`, `WantedMessages.Key` (WantedMessages.java:21-37, each key has an in-code fallback equal to the shipped line).

Optional explain command (stretch, ship only if the owner wants it): `/glw wanted auto [player]` (permission `gangland.wanted.auto`) prints `delta`,
`T`, `f`, planned stars. One `CommandContribution` on the existing wanted command path plus a `commands.json` entry in the module jar, as the project rule requires.
Without it the same facts go to the module's Debug log at each decision (`log.info` via Lombok `@CustomLog`).

---

## 9. API and event changes (additive only)

Next free `GanglandApi.VERSION` minor (2.3 if 2.2 lands first), documented in the javadoc list at GanglandApi.java:24-30, and `module.yml` `Host_Api` of cops-n-crooks raised to match:

- `WantedEvasionStateEvent` (gangland-api/.../events/wanted/WantedEvasionStateEvent.java:16-50): add `int plannedStars` (0 = unknown / not AUTO), a 7-argument constructor and a getter.
  **Keep the 6-argument constructor** (it sets 0). Existing listeners (WantedHudListener.java:88, any turf code) compile and behave as before.
- No new event. A "decision" event was considered and rejected: nothing consumes it yet, and the module's Debug log covers audit. Add it the day a second module needs it.
- Fallback if the owner prefers zero api change: the HUD could ask `EvasionClock.snapshot(UUID)` (EvasionClock.java:79-84, which has no main-code caller today) for `plannedStars` on its 10-tick timer. Event-driven is the recommendation because it keeps the HUD and other modules on one channel.

---

## 10. HUD and text (`ECR/wanted_messages.yml`, module-owned, no `Messages` change)

New keys with in-code fallbacks, `%count%` is an existing placeholder:

```yaml
Hud:
   Bar:
      # Shown while searching when the next drop will take more than one star; %count% is that number
      Searching_Plan: "&e%stars% &6&lSEARCHING %time% &7(-%count%)"
      # A drop that took several stars just happened
      Evaded_Many: "&a%stars% &2&l%count% STARS LOST"
   Card:
      # Several stars fell because the chase ran long; %count% is how many
      Drop_Evasion_Many: "&aYou shook them: &e-%count% stars"
      # Every star fell at once because the trail went completely cold
      Drop_Evasion_All: "&aThe trail went completely cold"
```

- `StarCard.barTitle` (StarCard.java:46-53) uses `Searching_Plan` only when `plannedStars > 1`, otherwise the unchanged `Searching` line. `Evaded_Many` the same for EVADED with a multi-star drop.
- `Drop_Evasion_All` is used when `newLevel == 0`; this works because `isGetaway(EVASION)` already lets a level-0 card show (WantedHudListener.java:80, 103-105).
- A habitual escaper sees only the normal bar (no "(-2)"), which is the honest preview of what he will get.
- The secondsLeft countdown stays valid: `need` is frozen per search, so `ceil((need - progress)/1000/speed)` (EvasionClock.java:180) keeps its meaning.
- Update the header comment (wanted_messages.yml line 6-7) to say `%count%` also fills the star-drop lines.

---

## 11. Worked examples

All use shipped defaults: `Seconds_To_Drop` = [10, 20, 30, 45, 60]; `Cold_Start_Typical_Seconds` = [90, 180, 300, 420, 600]; `Extra_Star_Ratio` 1.5, step 0.75, `All_Stars_Ratio` 3.0.

1. **Fresh server, fresh player, 3 stars, hides in the open** (peak 3, T = 300 s). Search starts at age 40 s: `delta` 0, `r` 0.13, `f` = 1 -> need 30 s. At age 73 s `r` = 0.24, N = 1,
   reason BASELINE. Identical to ONE_STAR (the cold-start guarantee). HUD: `SEARCHING 0:30`.
2. **A chase that drags on** (peak 4, T = 420 s). Level 4 hide finishes at age 480 s, `r` = 1.14 -> still 1 star, but the next wait is already `f` = 1 - 0.3*0.14 = 0.96. A later drop at age 700 s: `r` = 1.67 ->
   N = 1 + (1 + floor(0.17/0.75)) = 2 stars. At age 1000 s `r` = 2.38 -> N = 1 + (1 + floor(0.88/0.75)) = 3. At age 1260 s `r` = 3.0 -> COLD_TRAIL, all stars. HUD card for the 2-star case: "You shook them: -2 stars".
3. **A habitual escaper.** His last 10 chases: all escapes, server rate for his mix 0.55 -> `actual` 10, `expected` 5.5, `n` 10 -> `delta` = 4.5 / (10 + 5) = **0.30**, exactly `Habitual_Escaper_Delta`. A level-3 search now waits 30 * (1 + 0.8*0.30) = **37 s** and
   a long chase that would give 2 stars gives 1 (reason HABIT_CAP). The same chase for a stranger: 30 s and 2 stars.
4. **A habitual loser.** 10 chases, 1 escape at expected 5.5: `delta` = (1 - 5.5)/15 = -0.30 -> wait 30 * (1 - 0.24) = 22.8 s, and drag relief applies. Floor: even `delta` -1 cannot go below `Min_Time_Factor` 0.6 = 18 s.
5. **The server learns.** After 40 escapes at peak 2 with median 80 s: `T(2)` = (80*40 + 180*20)/(40 + 20) = **113 s** (was 180). A 170 s chase is now `r` = 1.50 -> 2 stars where a fresh server gave 1. When escapes on this server run long, `T` rises and AUTO relaxes.
6. **Update arithmetic** (stranger, ARREST at peak 3, server `p_3` = 0.55 cold): `H.n` = 1, `actual` 0, `expected` 0.55, `delta` = (0 - 0.55)/(1 + 5) = -0.09 -> wait x 0.93. One arrest barely moves him; a streak does.
7. **Restart**: kill the server after example 3; on boot `doLoadAll` reads the rows and the same `delta` and `T` come back (acceptance row A4).

---

## 12. Tests

### Unit (JUnit 5 + Mockito, module `src/test`)

- `AutoDropPlannerTest` (pure, table-driven): ratio to stars at every threshold boundary (1.49, 1.5, 2.24, 2.25, 2.99, 3.0); cap at `level`; `Max_Stars_Per_Drop`; `All_Stars_Ratio` 0 disables all; habit cap at 0.30 and not at 0.29; `f` clamps at both ends; drag relief at `r` 1, 1.5, 2+; **invariant: for all `r < Extra_Star_Ratio` and `delta` 0, plan equals ONE_STAR (stars 1, f 1)**; stars never 0.
- `ChaseLearnerTest`: example 3 (`delta` = 0.30), example 4, example 5 (`T` = 113), example 6; decay (`n` never above 10 over 1000 chases); half-life (30 days idle halves the sums; negative clock jump treated as 0); guards (30 s minimum, 180 s gap, ADMIN/SIGN/RESTORE/UNKNOWN ignored, RESTORE-started chase ignored); server weight `w` for a heavy player; median step; replay determinism (same list -> equal rows); cold start returns config defaults; `Learning.Enable` false returns delta 0 and writes nothing.
- `ChaseOutcomeListenerTest`: start on 0 -> n, peak tracking, end classification per cause, PlayerQuit records nothing, cuffed time excluded from `len`.
- `ChaseConfigTest` (existing at ChaseConfigTest.java:132-139 for the unknown mode): `Drop_Mode: AUTO` parses; the Auto block defaults when absent; each validation row in section 7 (bad value -> default + WARNING with the right code); the unknown-Drop_Mode message lists AUTO.
- `EvasionClockTest` additions (injected clock, `callEvent`, mocked `stars` that lowers the level, as in setUp at EvasionClockTest.java:73-110): AUTO cold start drops exactly 1 at `Seconds_To_Drop`; with a seeded learner a long chase drops 2 and the event carries `plannedStars` 2 beforehand; habitual escaper waits 1.24x and drops 1; planner exception falls back to ONE_STAR and logs once; drop of all stars ends the chase without an EVADED event (the existing L152 path); restraint pause does not age the chase; level raised mid-search recomputes `f`; ONE_STAR and ALL_STARS tests unchanged and still green.
- Repository tests for both tables, following the CLAUDE.md Windows rules: `@TempDir(cleanup = CleanupMode.NEVER)`, track and disconnect every `DatabaseHandler`, `MockPluginFactory.releaseDbFiles(tempDir)` in `@AfterEach`. Round trip, load of an empty table, prune of rows idle past `Forget_After_Days`.
- Every new test must be shown RED against the pre-feature code before it is taken green (project rule).

### Acceptance harness (sandbox, `brainstorming/cnc-overhaul-2026-10-05/acceptance/`)

A real chase takes minutes, so a profile shrinks the numbers. New profile `auto` in `prep-cnc015.sh` (set with `yset.js`, which already exists for exactly this): `Wanted.Evasion.Drop_Mode: AUTO`,
`Seconds_To_Drop: [5, 5, 5, 30, 5]`, `Auto.Cold_Start_Typical_Seconds: [20, 20, 20, 24, 30]`, `Extra_Star_Ratio: 1.0`, `All_Stars_Ratio: 9`. New rows in `gen-cnc015.js` and verdicts in `cnc-verdict.js`, same step format as `N1-evasion-drop.json`:

| Row | Setup | Pass line |
|---|---|---|
| A1 auto-cold-start | profile `auto`, Runner at 2 stars sealed in the stone room (the N1 recipe) | exactly one star falls (`decreased`, 1 star left), no death. AUTO equals ONE_STAR |
| A2 auto-long-chase | profile `auto`, `/glw wanted add 4`, sealed room, wait | at level 4 the first drop happens after about 30 s of hiding and takes 2 stars (`r` = 34-38/24 = 1.4-1.6, inside the 1.0-1.75 two-star band, so it is robust to timing noise); a card line "You shook them" appears in the transcript |
| A3 auto-habit | profile `auto` + seed step writing a `chase_habit` row for Runner (n 10, actual 10, expected 5.5, last_at now) with the sandbox `sqlite3`; `Seconds_To_Drop` level 1 = 30 | the first drop comes 35 s or later after hiding (37 s expected) and takes exactly 1 star; chat has no "(-2)" preview |
| A4 auto-learn-persist | two quick escapes (30 s+ apart, 180 s gap lowered by the profile to 5), restart the server, read `chase_habit` and `chase_server_stat` | rows exist after the restart with `n` about 2; `/glw wanted auto` (or the Debug log line) shows `delta` > 0 |
| A5 auto-safe-boot | profile `auto` + `Learning.Enable: false`, then a profile with the tables absent | no ERROR at boot, A1 behaviour holds |

Regression: R1-R4 and N1-N8 run on the default profile (still ONE_STAR) and must stay green; AUTO is opt-in.

---

## 13. Risks and open points

1. **Unpredictability.** Players may not like "sometimes 2 stars". Mitigations: cold start equals ONE_STAR; the HUD previews the size; the Debug log and the optional command explain every decision; ship opt-in (default stays ONE_STAR) and flip the default only after a soak.
2. **Wrong typical lengths early on.** The cold defaults (3 times the ladder time) are a guess. They are config; the server's own median takes over as it fills (prior weight 20 equals roughly 20 chases).
3. **Lose-to-win** (section 6) is bounded, not eliminated: at worst a 40 % shorter wait for a player who repeatedly pays jail time for it.
4. **Existing quirk AUTO inherits:** a star raise mid-search refreshes `track.level` but keeps `progress` (EvasionClock.java:141-142), so old progress is measured against the new, larger need. AUTO recomputes `f` there but does not fix this; it is not an AUTO bug.
5. **`Take_Money` charges per star** (WantedStars.java:129-134): a 3-star AUTO drop is as expensive as ALL_STARS. Documented.
6. **Autosave full snapshot** (section 5.4) and **single-server statistics** (shared MySQL). Both flagged with their upgrade paths.
7. **Pauses lump two cases** in the clock (restraint and tip-off, EvasionClock.java:110). AUTO excludes only restraint from `age`; tip-off pauses (about 3 s) stay counted.
8. **Chase start across restarts.** `ChaseLog` is memory only; a restart mid-chase restores the level with cause RESTORE and AUTO plays the chase with `age` counted from the join, never learning from it.
9. **Docket.** I did not consult the bug docket. When implementing, record in `triage/` the inherited quirks above (item 4; `snapshot` having no caller; empty `Search_Radius`/`Seconds_To_Drop` lists falling back silently) if they are not already entries.
10. **Release shape (recommendation).** New branch named after a patch-bumped revision (project convention), api minor bump, one module jar rebuilt (cops-n-crooks). Roadmap fit: independent of the 0.16+ items (districts, stations, hideouts, dispatch); those can later feed the planner (for example, "inside a hideout" as an extra signal) through the `ChaseRecord` extension point.
