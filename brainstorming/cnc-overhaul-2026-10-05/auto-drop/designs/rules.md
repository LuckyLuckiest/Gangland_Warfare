# AUTO Drop_Mode - Design A "readable rules" (designer: rules)

Status: PLAN ONLY. No source was edited. Target: Cops N Crooks on top of 0.15.1 (branch `0.15.1`, head 6c777dda); proposed release 0.15.2 (own branch, see section 14).
Paths: `CNC` = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks`, `RES` = `gangland-features/cops-n-crooks/src/main/resources/copsncrooks`.

Correction to the brief: since 0.15.1 (commit e57da275) the shipped file is `RES/wanted.yml` (data folder `copsncrooks/`), not `npc/wanted.yml`. The sandbox harness still says `npc/wanted.yml` in `acceptance/README.md` (profile `r4`) and `prep-cnc015.sh`; that path must be updated before any row below runs on 0.15.1+.

---

## 1. The idea in five lines

AUTO keeps today's evasion clock (SEEN / SEARCHING / EVADED, `Seconds_To_Drop`, zone, speeds). It only changes two numbers, both from one score table an admin can read:

1. **How many stars fall** when the hide timer completes: 1, 2, N, or all.
2. **How long the hide timer is** for this chase: 0.5x to 2x the admin's `Seconds_To_Drop` baseline.

The score has two halves, so the story is easy to tell a player:

- **Record points** (R): what is on the player's sheet. How long the chase has run, how it started, how many stars are at stake, how fresh and how violent his crimes are, whether the cops are still on alert, and whether he keeps getting away. Known before he hides.
- **Getaway points** (G): how cleanly he got away. How long no cop has seen him, and whether he left the search zone.

`score S = max(0, R + G)`. The stars that fall come from a ladder of thresholds on S. R alone sets the timer length.

"Learns" in this design, honestly: (a) inside one chase it accumulates the chase's own history (start, crimes, sightings, cop alert), and (b) per player it remembers recent getaways in memory and makes the next one harder to get cheaply. It does NOT build server-wide statistics or persist anything; that is another designer's angle and is listed as a future term slot (section 13).

---

## 2. Decision inputs and where each is read

All reads happen on the main thread inside `EvasionClock.tick` (`CNC/wanted/evasion/EvasionClock.java:96`), which is fed once per cop AI tick per online chased player (`CopManager.addAiTickHook`, wired at `CNC/config/EvasionModuleConfig.java:36-40`).

| # | Input | Where it is read today / new accessor | Cost |
|---|---|---|---|
| 1 | Current stars `level`, max level | `wanted.getLevel()` at `EvasionClock.java:115`; `wanted.getMaxLevel()` (as `HeatLedger.java:93`) | none |
| 2 | Chase age (wall seconds) | NEW `ChaseLog.startMs` in the clock: earliest of (a) the first tick that passed the gate at `EvasionClock.java:99` and (b) the first crime `chaseCrimes(id).get(0).at()` (`HeatLedger.java:166-169`). Both clocks are `System::currentTimeMillis` (`EvasionModuleConfig.java:30`, `HeatModuleConfig.java:40`). Pauses (restraint, tip-off, `EvasionClock.java:110`) count: it is wall time | 1 field |
| 3 | How it started: opening stars | first `CrimeRecord` of `HeatLedger.chaseCrimes` (`HeatLedger.java:166`, record at `CrimeRecord.java`), converted with `HeatSettings.starsFor(first.heat(), maxLevel)` (`CNC/wanted/config/HeatSettings.java:52`) = "stars the opening crime alone was worth". If no crime sits within `Opening_Window_Seconds` of `startMs` (admin `/glw wanted add`, sign, bribe, login restore, `HeatLedger.onLevelChanged` creates a crime-less `Chase(0)` at `HeatLedger.java:144`) the start is **borrowed** | none |
| 4 | Every crime id + age | same list; `ageSeconds = (now - CrimeRecord.at()) / 1000`; violent = id in `Violent_Crimes` (ids are the `Crimes.*` strings, `gangland-api/.../crime/Crimes.java:9-14`) | none; the list is never trimmed by star drops (only `heat` is clamped, `HeatLedger.java:140-142`) |
| 5 | Time unseen (wall seconds) | `group.getSquad().millisSinceSighting()` as already used at `EvasionClock.java:116`. It is the real hidden streak: it keeps growing across several drops and resets only when any cop, shot, or witness reports a sighting (`signals.md` section Cop Sighting) | none |
| 6 | Outside the search zone | the test inside `speed(...)` (`EvasionClock.java:189-196`), extracted to `insideZone(player, track)` and reused | trivial refactor |
| 7 | Cop alert | `group.isCombatAlert()` (field `CopGroup.java:56`, class `@Getter` at `:37`) or `group.isRegrouping()` (`CopGroup.java:271`) | none |
| 8 | Recent getaways (memory) | NEW in-memory `Map<UUID, ArrayDeque<Long>>` in the clock: a timestamp is added when an AUTO drop takes the player to 0 stars (the branch at `EvasionClock.java:152`). Pruned on read by `Repeat_Window_Minutes` | one map |
| 9 | Crime count (cache key) | NEW `HeatLedger.crimeCount(UUID)` (additive, `chases.get(id).crimes.size()`), because `chaseCrimes` copies the list (`HeatLedger.java:168`) and must not run every tick | 3 lines |

Not used on purpose: player health/armor, cuff counters (a break-free fight already shows up as `Resisting_Arrest` in the crime list), Bartizan, civilians, turf. `HeatLedger` is a new constructor argument of `EvasionClock` (today it has none; `EvasionModuleConfig.java:28-33` and `EvasionClockTest.setUp` at `:73` build it).

---

## 3. When the decision is made

- **Drop count (1, 2, N, all): at drop time only.** The full score is computed in the drop branch (`EvasionClock.java:148`) on the same tick as `stars.drop(...)`, so the inputs and the level it caps against can never be stale.
- **Timer length: when a search starts and whenever the record changes**, not every tick. `Track.needMs` is computed in `startSearch` (`EvasionClock.java:166`, which also runs again after every EVADED), and recomputed only when `wanted.getLevel() != track.level` (the line that already refreshes the level, `:141-142`) or `HeatLedger.crimeCount(id)` differs from `Track.crimeCount`. The boss-bar countdown therefore does not wobble as chase minutes tick up. A recompute can move `need` below the already earned `progress`; the existing `progress >= need` check then drops on the next tick, exactly as a level change does today (`evasion.md` section 9).
- **Never mid-search** for the star count; the player cannot lose stars the timer has not earned.

Because hidden time keeps counting across drops (input 5), long hides cascade: a 3-star chase typically sheds 1 star at the first timer and the rest at the next one or two (example E2/E2c). That is the "after a specific period" behaviour: patience is rewarded by bigger and faster drops.

## 4. Output and interaction with the existing timers

- Output: `stars in [1 .. level]`. 1 = today's ONE_STAR, `level` = today's ALL_STARS, anything between is new. A decision of 0 is not a thing (the safety floor `Minimum_Stars` is 1): "not yet" is expressed by the longer timer, so `stars.drop(user, 0, ..)` (a no-op that returns 0, `WantedStars.java:112-148`, which the clock reads as "no EVADED", `EvasionClock.java:152`) never happens.
- `Seconds_To_Drop` per level (`EvasionSettings.secondsToDropFor`) stays the baseline and the gate: `need = Seconds_To_Drop[level] * 1000 * timerScale`, `timerScale = clamp(1 - Timer_Per_Point * R, Timer_Scale_Min, Timer_Scale_Max)`. `Outside_Zone_Speed` still shortens it in real time (`EvasionClock.java:144-145`).
- `Wanted.Repeating_Timer` (settings.yml) is untouched and still the safety net. `handlesDecay` (`EvasionClock.java:73-76`) is the same; when the last live cop goes RETURNING the track is cleared (`:99-101`) and the timer takes 1 star per interval (`WantedExecutor.java:83-99`). That fallback never runs the planner and never counts as a "getaway" for the repeat rule.
- `Take_Money` (price per dropped star, `WantedStars.java:129-134`, default off since 0.15): a multi-star AUTO drop is priced per star, same as ALL_STARS today.
- `ONE_STAR` and `ALL_STARS` code paths are byte-for-byte the current ones (`EvasionClock.java:149` keeps its ternary for them).

---

## 5. The formula (exact)

```
chase_minutes = (now - startMs) / 60000
chase_pts     = clamp(Chase_Points_Per_Minute * chase_minutes, -Chase_Points_Max, +Chase_Points_Max)
opening_pts   = borrowed ? Borrowed_Start_Points
                         : Opening_Stars_Points[min(openingStars, size-1)]
level_pts     = Level_Points[min(level, size) - 1]
crime_pts     = clamp( SUM over crimes c of  pts(c) * max(0, 1 - age_c / Crime_Fade_Seconds),
                       -Crime_Penalty_Max, +Crime_Penalty_Max )
                pts(c) = Violent_Crime_Points if c.id in Violent_Crimes else Other_Crime_Points
pressure_pts  = (combatAlert or regrouping) ? Cop_Pressure_Points : 0
repeat_pts    = clamp( Repeat_Getaway_Points * getawaysInWindow, -Repeat_Penalty_Max, +Repeat_Penalty_Max )

R = chase_pts + opening_pts + level_pts + crime_pts + pressure_pts + repeat_pts

hidden_pts    = min(Hidden_Points_Max, Hidden_Points_Per_Second * unseenSeconds)
outside_pts   = outsideZone ? Outside_Zone_Points : 0
G = hidden_pts + outside_pts

S = max(0, R + G)

n = 1 + count(t in Stars_Thresholds where S >= t)       # ladder
n = max(n, Minimum_Stars)
n = (Maximum_Stars > 0) ? min(n, Maximum_Stars) : n
n = clamp(n, 1, level)
if S >= All_Stars_Score: n = level                       # the jackpot ignores Maximum_Stars

stars_to_drop = n
timer_scale   = clamp(1 - Timer_Per_Point * R, Timer_Scale_Min, Timer_Scale_Max)
need_ms       = round(Seconds_To_Drop[level] * 1000 * timer_scale)
```

Reading it: a threshold list `[20, 36, 50]` means S under 20 drops 1 star, 20-35 drops 2, 36-49 drops 3, 50 and over drops 4, and `All_Stars_Score` (60) drops everything. Every threshold is inclusive.

## 6. YAML block (exact text for `RES/wanted.yml`, under `Wanted.Evasion`)

The `Drop_Mode` comment changes; `Auto` goes after `Outside_Zone_Speed`. Shipped default stays `ONE_STAR`. Block-style, `Capitalized_Underscore`, every key commented.

```yaml
      # What a completed evasion takes: ONE_STAR (one star), ALL_STARS (every star) or AUTO (the plugin reads the
      # chase and decides each time; see the Auto block below)
      Drop_Mode: ONE_STAR
      ...
      # How much faster the timer runs while you are outside the search zone
      Outside_Zone_Speed: 2.0
      # Used only while Drop_Mode is AUTO. The plugin adds up points for the chase and turns the total (the score)
      # into stars. Record points are what is on your sheet, Getaway points are how cleanly you got away. A higher
      # score means a bigger drop; the Record points also stretch or shrink the hide timer
      Auto:
         # A drop always takes at least this many stars
         Minimum_Stars: 1
         # A drop never takes more than this many stars from the ladder (0 = no limit besides your own stars)
         Maximum_Stars: 0
         # Score needed for a 2 star drop, a 3 star drop, a 4 star drop, ... (under the first one it takes 1 star)
         Stars_Thresholds:
            - 20
            - 36
            - 50
         # Score at which every star goes at once, whatever Maximum_Stars says
         All_Stars_Score: 60
         # How much each Record point changes the hide timer: scale = 1 - this * Record points. 0.01 and +20 points
         # is a timer 20% shorter
         Timer_Per_Point: 0.01
         # The hide timer is never scaled below this (of Seconds_To_Drop)
         Timer_Scale_Min: 0.5
         # The hide timer is never scaled above this (of Seconds_To_Drop)
         Timer_Scale_Max: 2.0
         # What is on your sheet
         Record:
            # Points per minute the chase has run (cops wear down). Use a negative number to punish long chases
            Chase_Points_Per_Minute: 3
            # The chase points never go beyond this, either way
            Chase_Points_Max: 15
            # A crime counts as the one that started the chase when it happened this many seconds from the chase start
            Opening_Window_Seconds: 30
            # How it started: points by the stars the opening crime alone was worth (0 stars first, then 1, 2, ...)
            Opening_Stars_Points:
               - 15
               - 0
               - -8
               - -16
               - -24
               - -30
            # How it started when no crime opened it (admin command, sign, bribe, login restore)
            Borrowed_Start_Points: 0
            # Stars at stake: points at 1 star, 2 stars, ... (a shorter list repeats its last number)
            Level_Points:
               - 10
               - 5
               - 0
               - -5
               - -10
            # Crimes that count as violent (names from Wanted.Heat.Crimes)
            Violent_Crimes:
               - Kill_Player
               - Kill_Civilian
               - Kill_Cop
               - Assault_Cop
               - Resisting_Arrest
            # Points for each violent crime at the moment it happens; it fades to 0 over Crime_Fade_Seconds
            Violent_Crime_Points: -15
            # The same for every other crime on the chase
            Other_Crime_Points: -5
            # Seconds a crime takes to fade from full weight to nothing
            Crime_Fade_Seconds: 600
            # The crime points never go below minus this
            Crime_Penalty_Max: 45
            # Points while the cop group is on combat alert or regrouping
            Cop_Pressure_Points: -10
            # Points for each time you already got away from a chase inside Repeat_Window_Minutes (kept in memory,
            # forgotten when the server restarts)
            Repeat_Getaway_Points: -6
            # Minutes a getaway keeps counting
            Repeat_Window_Minutes: 15
            # The repeat points never go below minus this
            Repeat_Penalty_Max: 18
         # How cleanly you got away
         Getaway:
            # Points per second no cop has seen you (the streak keeps growing across several drops)
            Hidden_Points_Per_Second: 0.5
            # The hidden points never go beyond this
            Hidden_Points_Max: 30
            # Points while you are outside the search zone
            Outside_Zone_Points: 10
```

## 7. Config parsing and validation (`CNC/wanted/config/ChaseConfig.java`, `EvasionSettings.java`)

Code shape (additive; nothing existing is renamed):

- `DropMode` (`CNC/wanted/config/DropMode.java:4-8`) gets `AUTO`. `ChaseConfig.dropMode` (`:80-94`) already matches any enum constant case-insensitively, so AUTO parses with no change.
- New record `AutoSettings` (same package) with a compact constructor that clamps numbers and `DEFAULT` = the table above. `EvasionSettings` (`:244-245`) gets a seventh component `AutoSettings auto`; keep the current 6-argument constructor as a secondary constructor delegating `AutoSettings.DEFAULT`, so `EvasionClockTest` lines 212 and 268 and `ChaseConfig.evasion` (`:73`) need no churn beyond the new argument.
- `ChaseConfig.evasion` (`:66-78`) parses the `Auto` block **always**, even when Drop_Mode is not AUTO, so a reload that flips the mode works without a second parse. A file with no `Auto` block (every 0.15.0/0.15.1 file) yields `AutoSettings.DEFAULT` and no warning.

Validation (each uses the existing `report.add(Severity, location, path, message, category)` of `ChaseConfig.java:90-91`; the clamped value is used and the server never refuses to start):

| Rule | Result |
|---|---|
| unknown `Drop_Mode` | the warning text at `ChaseConfig.java:91-92` now reads `(ONE_STAR, ALL_STARS or AUTO)`; this is the one existing string and test (`ChaseConfigTest.java:132-139`) to update |
| `Stars_Thresholds` not strictly rising, or a negative entry | WARNING, list sorted and de-duplicated, negatives dropped; empty list falls back to defaults (as the other lists do, silently, `evasion.md` section 5) |
| `All_Stars_Score` below the first threshold | WARNING, raised to the first threshold (otherwise every drop wipes the chase) |
| `Minimum_Stars` below 1 | clamped to 1; above `Maximum_Stars` when that is > 0 -> WARNING, minimum lowered to the maximum |
| `Maximum_Stars` negative | clamped to 0 |
| `Timer_Per_Point` below 0 | clamped to 0 (no scaling) |
| `Timer_Scale_Min` outside 0.1..1 | clamped; below 0.1 would give near-instant drops |
| `Timer_Scale_Max` below `Timer_Scale_Min` | WARNING, the two swapped |
| `Crime_Fade_Seconds` below 1 | clamped to 1 |
| `Violent_Crimes` entry not a key of `Wanted.Heat.Crimes` | WARNING per entry, kept (a later release may add the crime) |
| any `*_Points` / `*_Max` not a finite number, or beyond +-200 | default for NaN/non-numbers, clamped to +-200 otherwise |
| `Opening_Stars_Points` / `Level_Points` empty | defaults; shorter than the star range repeats the last value |
| `Drop_Mode: AUTO` with `Evasion.Enable: false` | INFO "AUTO does nothing while evasion is off" |
| `Drop_Mode: AUTO` with `Heat.Enable: false` | INFO "no crime record: AUTO scores every chase as a borrowed start with no crime points" |

## 8. Code placement (planning only)

- `CNC/wanted/evasion/AutoDropPlanner.java` - a pure class, no Bukkit. `static Decision plan(AutoSettings, Inputs)` and `static double timerScale(AutoSettings, double record)`. `Inputs(level, chaseSeconds, openingStars /* -1 = borrowed */, List<CrimeAge> crimes, copPressure, recentGetaways, unseenSeconds, outsideZone)`, `CrimeAge(violent, ageSeconds)`, `Part(term, points)`, `Decision(stars, record, getaway, score, timerScale, parts)`. Terms: `Chase_Length, Opening, Level, Crimes_Violent, Crimes_Other, Pressure, Repeat, Hidden, Outside`. All the numbers in section 12 come from this class.
- `EvasionClock`: new constructor argument `HeatLedger`; `Track` gains `long needMs`, `int crimeCount`; new per-player `Map<UUID, Long> chaseStart` and `Map<UUID, ArrayDeque<Long>> getaways`. The chase start and getaway state are **not** in `Track` because `Track` is thrown away at every SEEN (`EvasionClock.java:118-121`). `clear(player)` (called whenever the gate fails, `:99-101`) must NOT end the chase; a separate `endChase(player)` is called by `EvasionListener.onWantedEnd` and `onQuit` (`listener/wanted/EvasionListener.java:19-27`) and drops `chaseStart` (getaways stay until restart). The drop branch becomes:

```java
// design sketch, not applied
long need = cfg.dropMode() == DropMode.AUTO ? track.needMs : cfg.secondsToDropFor(level) * 1000L;
if (track.progress >= need) {
	int count = switch (cfg.dropMode()) {
		case ALL_STARS -> level;
		case ONE_STAR -> 1;
		case AUTO -> decideAuto(player, group, track, level, cfg, now);   // builds Inputs, plans, fires the event
	};
	int dropped = stars.drop(user, count, WantedCause.EVASION);
	...
```

- `HeatLedger.crimeCount(UUID)` (additive).
- `EvasionModuleConfig.evasionClock(...)` passes the `HeatLedger` bean (it already exists as a bean, `HeatModuleConfig.java:32-41`).

## 9. API, events, persistence

- **gangland-api: no change in v1.** The one new event is module-local: `CNC/events/wanted/AutoDropDecidedEvent` (non-cancellable, `super(false)` like `WantedEvasionStateEvent`): player, level, the `Decision` (stars, record, getaway, score, parts). Fired synchronously by the clock **immediately before** `stars.drop`, so a listener sees it before the `WantedLevelChangeEvent` that the drop causes. Reason for no api bump: the only consumer is the HUD listener in the same module, and `gangland-api` growth is on a fixed cadence (2.2 and 2.3 are spoken for by the overhaul plan, `SPEC-0.15.md` roadmap). Upgrade path if another module or plugin wants it: add `WantedAutoDropEvent` to `gangland-api` as an additive minor (2.2 or the next free one), keep the same fields, and have the clock fire that one instead; no existing member changes.
- `WantedEvasionStateEvent` (api 2.1) is unchanged. EVADED still means "stars fell"; the lost count reaches the bar through the HUD stash below, not through the event.
- **Persistence: none.** `ChaseLog.startMs` lives with the chase (cleared on WantedEnd and quit, `EvasionListener`), the getaway deque lives until the server restarts. No table, no repository, no migration, nothing in `settings.yml`. A relog resets the chase age (the player re-enters as a login restore, a borrowed start); that costs at most 15 points of a positive term and is accepted.
- Debug log: one line per decision through the module logger the sandbox already has on (`prep-cnc015.sh` sets Debug on for "Cops N Crooks"): `AUTO drop Runner level=3 R=1.0 G=16.0 S=17.0 -> 1 star (scale 0.99) [Chase_Length +12.0, Opening -8.0, Crimes_Other -3.0, Hidden +16.0]`. This is also what the acceptance verdict reads.

## 10. HUD and star card (explaining the result)

Today the card is chosen by cause only (`StarCard.dropCard`, `CNC/wanted/hud/StarCard.java:35-43`: EVASION -> "You stayed out of sight") and one card is shown per `setLevel` (`WantedHudListener.onLevelChange`, `CNC/listener/wanted/WantedHudListener.java:71-85`). AUTO adds:

1. `WantedHudListener` listens to `AutoDropDecidedEvent` (MONITOR) and stashes `(decision, now)` per player. In `onLevelChange` for a drop with cause EVASION it consumes the stash if it is under 1 second old and builds `StarCard.autoCard(messages, decision, lost)` where `lost = oldLevel - newLevel`; otherwise it falls back to the existing `dropCard` (so ONE_STAR/ALL_STARS and stale cases look exactly like today). A stash left by a cancelled or zero drop is overwritten next time and removed on `WantedEndEvent`/quit.
2. `%gain%` = the largest positive part of the decision, `%held%` = the largest negative part when it is 5 points or worse. The all-stars card shows only the gain. Phrases are module YAML so server owners reword them.
3. Boss bar: SEARCHING stays as is; `secondsLeft` is the ETA to the next drop from the scaled `needMs` and the zone speed (`fireCountdown`, `EvasionClock.java:179-186`, takes `track.needMs`), so the bar never goes stale and never previews the star count (the surprise is the point; the card explains afterwards). The EVADED flash uses `Evaded_Many` when `lost >= 2`.

`RES/wanted_messages.yml` additions (and matching `WantedMessages.Key` fallbacks, `CNC/wanted/WantedMessages.java:19-30`, so an old file still reads):

```yaml
Hud:
   Bar:
      # A star was just lost
      Evaded: "&a%stars% &2&lSTAR LOST"
      # Several stars were lost at once (AUTO drop); %lost% is how many
      Evaded_Many: "&a%stars% &2&l-%lost% STARS"
   Card:
      # AUTO took one star; %gain% is the main reason, %held% is added when something held the drop back
      Drop_Auto_One: "&aYou stayed out of sight &7(%gain%%held%)"
      # AUTO took several stars but not all
      Drop_Auto_Many: "&aYou shook them&7: &f-%dropped% stars &7(%gain%%held%)"
      # AUTO took every star
      Drop_Auto_All: "&aClean getaway &7(%gain%)"
      # Added to a card when something held the drop back; %reason% is that reason
      Auto_Held: " &8| &7held back: %reason%"
   # The reason phrases an AUTO card picks from. Plus = it helped you, Minus = it held you back
   Auto_Reasons:
      Hidden:
         Plus: "you stayed hidden"
      Outside:
         Plus: "you left their search area"
      Chase_Length:
         Plus: "a long chase wore them down"
         Minus: "they have been on you too long"
      Opening:
         Plus: "it started small"
         Minus: "it started big"
      Level:
         Plus: "few stars to lose"
         Minus: "a lot of stars at stake"
      Crimes_Violent:
         Minus: "blood is still fresh"
      Crimes_Other:
         Minus: "fresh crimes"
      Pressure:
         Minus: "the cops are still on alert"
      Repeat:
         Minus: "they know your face"
```

New placeholders (`%dropped%`, `%lost%`, `%gain%`, `%held%`, `%reason%`) are added to the header comment line of the file (`wanted_messages.yml:5-6`). A tiny `WantedMessages.phrase(path, fallback)` reader is added for `Auto_Reasons` (the `Key` enum is a fixed list).

Card the player sees for each worked example is in section 12.

---

## 11. Defaults used (summary of section 6)

Thresholds `[20, 36, 50]`, All_Stars 60, timer scale `1 - 0.01 R` in `[0.5, 2.0]`, chase 3 pts/min (cap 15), opening `[15, 0, -8, -16, -24, -30]`, level `[10, 5, 0, -5, -10]`, violent -15 / other -5 fading over 600 s (cap -45), pressure -10, repeat -6 each in 15 min (cap -18), hidden 0.5/s (cap 30), outside +10. Highest reachable score with defaults is 15 + 15 + 10 + 40 = 80 (a petty one-star chase, long hide, outside the zone); the all-stars jackpot (60) is reachable at 4 stars (E9) but never at 5 stars unless an admin retunes, by design.

## 12. Worked chases (defaults; zone Search_Radius and `Seconds_To_Drop` `[10,20,30,45,60]`, `Lost_Sight_Seconds` 3, `Outside_Zone_Speed` 2.0)

Numbers produced by a throwaway calculator that implements section 5 literally (`scratchpad/calc.py`); the planner unit test pins them (section 15).

| # | Chase | Inputs | R | G | S | Drop | Hide timer |
|---|---|---|---|---|---|---|---|
| E1 | Petty scuffle, 2 stars. Assault_Civilian at t=0 (the opening, 0 stars) and again at t=20, hides inside the zone, 1.5 min in (crime ages 90 s and 70 s), 14 s unseen | chase +4.5, opening +15, level +5, crimes -8.7, hidden +7.0 | 15.8 | 7.0 | 22.8 | **2 of 2: chase over** (ladder 20 reached) | 20 s x 0.84 = 16.8 s |
| E2 | Store robbery, seen by a cop, 3 stars, hides inside the zone. Opening Store_Robbery x1.5 = 300 heat = 2 stars. 4.0 min in, 32 s unseen | chase +12.0, opening -8, level 0, crime -3.0 (age 240 s), hidden +16.0 | 1.0 | 16.0 | 17.0 | **1 star** (3 -> 2) | 30 s x 0.99 = 29.7 s |
| E2c | Same chase right after E2: 2 stars, 4.5 min in, still hidden, 52 s unseen | chase +13.5, opening -8, level +5, crime -2.8, hidden +26.0 | 7.8 | 26.0 | 33.8 | **2 of 2: chase over** | 20 s x 0.92 = 18.4 s |
| E3 | Cop killer, fresh, 4 stars. Kill_Cop at t=0 (the opening, 150 heat = 1 star) and again 70 s later, cop group on combat alert, hides outside the zone, 1.5 min in (ages 90 s and 20 s), 68 s unseen | chase +4.5, opening 0, level -5, crimes -27.2, pressure -10, hidden capped +30.0, outside +10 | -37.8 | 40.0 | 2.2 | **1 star** (4 -> 3) | 45 s x 1.38 = 62.0 s (outside speed 2 makes it about 31 s real) |
| E4 | Same cop killer after 10 min, level 3 by now. The opening Kill_Cop is 600 s old (faded to 0), two more kills 380 s and 330 s ago, group calm, outside the zone, 70 s unseen | chase +15 (cap), opening 0, level 0, crimes -12.2, hidden +30 (cap), outside +10 | 2.8 | 40.0 | 42.8 | **3 of 3: chase over** (ladder 36) | 30 s x 0.97 = 29.2 s |
| E5 | Admin `/glw wanted add 3` (borrowed start, no crimes), 2 min in, 28 s unseen, inside | chase +6, borrowed 0, level 0, hidden +14 | 6.0 | 14.0 | 20.0 | **2 stars** (3 -> 1) | 30 s x 0.94 = 28.2 s |
| E6 | E1 again, but this is his third getaway in 15 min (2 earlier) | as E1 plus repeat -12 | 3.8 | 7.0 | 10.8 | **1 star** (2 -> 1; E1 gave both) | 20 s x 0.96 = 19.2 s |
| E7 | Jailbreak (450 heat = 3 opening stars), 5 stars, 6 min in, outside the zone, 75 s unseen | chase +15, opening -16, level -10, crime -2.0, hidden +30, outside +10 | -13.0 | 40.0 | 27.0 | **2 stars** (5 -> 3) | 60 s x 1.13 = 67.8 s |
| E8 | Trespass (2 opening stars), 3 stars, 8 min in (opening crime 480 s old), outside, 120 s unseen | chase +15, opening -8, level 0, crime -1.0, hidden +30, outside +10 | 6.0 | 40.0 | 46.0 | **3 of 3: chase over** | 30 s x 0.94 = 28.2 s |
| E9 | Petty start (opening 0 stars) that grew to 4 stars, 6 min in, crimes 360 s and 330 s old, outside, 70 s unseen | chase +15, opening +15, level -5, crimes -4.2, hidden +30, outside +10 | 20.8 | 40.0 | 60.8 | **4 of 4: all stars (jackpot, 60; a knife edge by design)** | 45 s x 0.79 = 35.7 s |

Cards (what the player reads; the gain is the biggest positive part, held is the biggest negative part of 5 or more):

- E1: "Clean getaway (it started small)" (nothing held back at 5+ is shown for all-stars cards)
- E2: "You stayed out of sight (you stayed hidden | held back: it started big)"
- E2c: "Clean getaway (you stayed hidden)"
- E3: "You stayed out of sight (you stayed hidden | held back: blood is still fresh)"
- E4: "Clean getaway (you stayed hidden)"
- E5: "You shook them: -2 stars (you stayed hidden)"
- E6: "You stayed out of sight (it started small | held back: they know your face)"
- E7: "You shook them: -2 stars (you stayed hidden | held back: it started big)"
- E9: "Clean getaway (you stayed hidden)"

What the cases show: a small start is forgiven (E1); the same player on his third escape is not (E6); a sheet with fresh violence sheds one star and waits longer (E3); the same sheet 10 minutes later with no new crimes can be wiped (E4); a long, clean hide cascades through several drops (E2 -> E2c, about 48 s hidden for three stars against 60 s under ONE_STAR); admin-created chases use the neutral start (E5).

---

## 13. Risks

1. **Opaque to players.** Mitigated by the card (gain + held back), the debug line and the module event; there is deliberately no live star preview. Optional later: `/glw wanted score [player]` printing the last decision (needs a `commands.json` entry in the module jar).
2. **Wide tuning surface (about 25 keys).** The defaults are pinned by tests and the worked table; every key is commented; a bad value is clamped and reported, never fatal. A short admin guide goes in `documentation/` with the example table.
3. **Hidden points saturate.** At 0.5 per second the cap (30) is reached after 60 s unseen, so at 4-5 stars (timers of 45-60 s plus `Lost_Sight_Seconds`) the hidden term is almost always full and the Record half decides. That is intended (high stars are judged on the record), but an admin who wants hiding to matter more raises `Hidden_Points_Max`.
4. **Quirks inherited from the clock** (all in `evasion.md` section 9): chase history is lost on SEEN because `Track` is recreated (handled by keeping `chaseStart`/getaways outside `Track`); a star raise mid-search carries old `progress` into a larger `need` (handled for AUTO by recomputing `needMs` on level change, still the same behaviour for ONE_STAR/ALL_STARS); the EVADED to SEARCHING re-entry keeps the old zone centre.
5. **Heat disabled.** With `Heat.Enable: false` `HeatLedger.record` returns early (`HeatLedger.java:83`), so there is never a crime list; AUTO scores every chase as borrowed with no crime points (validation INFO above).
6. **Cross-restart learning is absent.** Repeat memory resets on restart; a relog resets chase age. Both are small positive/negative terms. Persisting getaways would need a module repository (`CNC/database`, `setDataSupplier` wiring) and is out of scope; it is the natural first step of any "real learning" design.
7. **Money.** A multi-star drop charges per star when `Take_Money.Enable` is on; AUTO makes big drops likelier for clean getaways, so an admin with a high star price pays more per event. Same arithmetic as ALL_STARS.
8. **Per-tick cost.** The crime list is read only at search start, on a changed `crimeCount`, and at drop time; the per-tick check is a map lookup plus `crimeCount` (hence the new accessor).
9. **Event ordering.** The decision event must fire before `stars.drop` so the stash exists when `WantedLevelChangeEvent` arrives (both synchronous on the main thread). A test pins the order. A cancelled level change (`WantedStars.java:126-127`) returns 0 and leaves a stale stash that the 1-second age check ignores.
10. **Future term slots** (not built): district of the opening crime and "started inside a station" (0.16 districts/stations), hideout bonus to Getaway while inside one (0.16+), dispatch pressure replacing `Cop_Pressure_Points` (0.20 duty state). Each is one more `Part` in the planner and one more key group; no format change.
11. **Docket.** Nothing here is a bug fix, so no docket status row changes. Adjacent, already-known quirks (progress carried over a star raise; Track reset on SEEN) should be checked against the bug docket (`brainstorming/bug-docket-2026-09-06/`, artifact 4102fb1f) before the 0.15.2 work starts; if they are not entries, add them via `triage/<slug>.txt` and `build_docket.py`.

## 14. Release and work breakdown

Own branch `0.15.2` from `0.15.1` (patch-bumped branch per `feedback_version_bump_conventions`), independent of the 0.16 district work; `Host_Api` stays 2.1 (no api change), module version bump only for `cops-n-crooks`.

1. `AutoSettings` + `DropMode.AUTO` + `ChaseConfig` parsing/validation + `wanted.yml` block + warning text (tests first).
2. `AutoDropPlanner` pure class + unit table (tests first, red before the class exists).
3. `HeatLedger.crimeCount`, `EvasionClock` (constructor, `Track.needMs/crimeCount`, `chaseStart`, `getaways`, `endChase`, `insideZone`, drop branch, event), `EvasionModuleConfig`, `EvasionListener`.
4. `AutoDropDecidedEvent`, `WantedHudListener` stash, `StarCard.autoCard`, `WantedHud` many-lost flash, `WantedMessages` keys and `wanted_messages.yml`.
5. Harness: profile, scenarios, verdict (section 16). Docs page and changelog (`.md` + `.bbcode.txt` per `feedback_changelog_dual_format`).

Method braces on their own lines, `@CustomLog`, no Paper APIs, no `FileConfiguration.setDefaults`, block-style YAML only.

## 15. Tests (unit)

All JUnit 5 + Mockito, in `gangland-features/cops-n-crooks/src/test/java/org/luckyraven/gangland/copsncrooks/...`. Every new test is run red against the pre-change code first (compile-red is acceptable for new types; for the behaviour changes of existing classes assert the old behaviour fails). No existing test is test-pinned to flip except the warning text in `ChaseConfigTest`.

- `AutoDropPlannerTest` (`wanted/evasion/`): the ten rows of section 12 as a parameterized table (R, G, S within 0.05, stars, scale); every threshold boundary (S exactly 20, 36, 50, 60 included); negative R floors S at 0 and still drops 1; stars never exceed `level` and never fall below `Minimum_Stars`; `Maximum_Stars` caps the ladder but not `All_Stars_Score`; crime fade at age 0, half, equal to and beyond `Crime_Fade_Seconds`; crime and repeat caps; chase points both signs; list clamping for `Opening_Stars_Points` / `Level_Points`; borrowed start; timer scale clamps at both ends; `Decision.parts` carries each non-zero term once (card source).
- `ChaseConfigTest`: AUTO parses; the whole Auto block round-trips; an old file with no `Auto` block gives `DEFAULT` and an empty report; each validation row of section 7 yields the stated clamp and the stated severity; the unknown-`Drop_Mode` warning now names AUTO (existing test at `:132-139` updated); lowercase `auto` parses.
- `EvasionClockTest` (existing harness: fake clock `now[]`, `events`, mocked `WantedStars`; add a mocked `HeatLedger`): AUTO drop count equals the planner for a staged chase (two crimes, hidden 30 s); the decision event fires once, before `stars.drop`, with the final count; ONE_STAR and ALL_STARS tests unchanged and green (no event, no ledger reads); `needMs` is fixed at search start, recomputed on level change and on a changed crime count, not per tick; SEEN->SEARCHING keeps `chaseStart` and the getaway memory (Track reset does not lose them); `WantedEndEvent`/quit ends the chase (`endChase`) but the gate-failing `clear` does not; getaway recorded only when an AUTO drop ends the chase, pruned by window; a restrained or tipped-off tick changes nothing; `Evasion.Enable: false` disables AUTO; cap at the level when a crime raised stars between search start and drop; `dropped == 0` path unchanged.
- `StarCardTest`: `autoCard` for one, many and all; `%gain%` is the largest positive part, `%held%` appears only at -5 or worse and never on the all-stars card; missing phrase falls back; old messages file (without the new keys) still reads via the `Key` fallbacks.
- `WantedHudListenerTest` (or extend the existing one): stash consumed by the next EVASION drop, ignored when older than 1 s, never used for DECAY/ARREST; ONE_STAR drops still show `Drop_Evasion`.
- `HeatLedgerTest`: `crimeCount` for no chase, a crime-less chase, and after drops (crimes are not trimmed).

## 16. Acceptance harness scenarios (sandbox only, `brainstorming/cnc-overhaul-2026-10-05/acceptance/`)

Never against the live Test Server. Same mechanics as N1: `E:/Programming/java/wt/_programme/` sandbox, mineflayer harness, `gen-cnc015.js` writes `scenarios/*.json`, `cnc-verdict.js` judges. Prerequisite fix: the prep script's `Wanted.Evasion` key edits target `copsncrooks/wanted.yml` on 0.15.1+ (it says `npc/wanted.yml` today).

- New profile `auto` in `prep-cnc015.sh`: default 0.15.x config plus `node yset.js .../copsncrooks/wanted.yml Wanted.Evasion.Drop_Mode AUTO` (the key exists, so no insert is needed) and Debug on for "Cops N Crooks" (already the profile default).
- **A1 auto-cascade** (profile `auto`): N1's setup and hiding room (`fill 96 -61 126 104 -56 134 stone hollow`, tp inside) but `/glw wanted add 3` (borrowed start, no crimes). Expected from section 5 with the sandbox timings: first drop about 3 + 30 s after sealing (R ~ 0, S ~ 17: 1 star, 3 -> 2), second about 18 s later (S ~ 34: 2 stars, 2 -> 0). Pass: exactly two `decreased` lines (a ONE_STAR server needs three, N1-style), final status 0 stars, total under 60 s after sealing, no ERROR, a star card line containing `stayed out of sight` or `Clean getaway`, and two debug lines `AUTO drop Runner level=3 ... -> 1 star` then `level=2 ... -> 2 stars` whose S is within +-8 of the table (the verdict extracts `S=`; timing jitter is why the tolerance is wide).
- **A2 auto-vs-one-star** (profile `default`, same scenario file): the same steps on `ONE_STAR` must show three `decreased` lines; run both rows and compare in one verdict (`cnc-verdict.js A2 <auto run> <default run>`), proving AUTO is selectable and the old mode is unchanged.
- **A3 auto-violent** (profile `auto`, best effort, REVIEW like N5-N8): reuse N8's two cop kills (`N8-regroup.json` steps) to put violent crimes on the sheet at 3-4 stars, then hide in the room. Expected: the first drop is 1 star and arrives later than the A1 baseline (timer scale > 1.0; debug line `scale=` above 1.0), card shows `held back`.
- **A4 auto-config-compat** (no bot): boot the `auto` profile with the 0.15.0-shaped `wanted.yml` (no `Auto` block) and then one with `Stars_Thresholds` unsorted; verdict greps `server.log`: no ERROR, no warning for the first, exactly one warning for the second, `Done (` present. Cheap to add to `cnc-015-boot` smoke later.

Pass criterion for the release: A1, A2, A4 PASS; A3 REVIEW.

## 17. Open decisions for the owner

1. Release: ship as its own 0.15.2 (recommended), or fold into 0.16?
2. `Hold` semantics: this design never refuses a drop ("not yet" = a longer timer, up to 2x). If the owner wants a hard "no drop while the score is under X" rule it is one more key (`Hold_Below_Score`) and one more branch; not included because it could freeze a chase that the safety net cannot help (the net is off while cops hunt).
3. Is an api event wanted now (`WantedAutoDropEvent`, api 2.2) for other plugins, or module-local until a second consumer exists (recommended)?
4. Chase length direction: default rewards long chases (+3 per minute, cap 15); set `Chase_Points_Per_Minute` negative to make long chases harder to shed. Which does the owner prefer as the shipped default?
5. Default `Drop_Mode` stays ONE_STAR; flip to AUTO at the next minor after one release of play-testing?
