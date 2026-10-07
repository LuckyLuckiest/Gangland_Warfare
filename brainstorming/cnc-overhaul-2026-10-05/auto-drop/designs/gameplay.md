# AUTO Drop_Mode - design "gameplay" (angle C: GTA-feel and endings)

Status: PLAN ONLY. No source edited, nothing committed. Base: Gangland 0.15.1 branch (master 0.15.0 + settings wave), gangland-api `VERSION = "2.2"` (gangland-api/.../GanglandApi.java:33).
Paths: EC = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks`, CORE = `gangland-core/src/main/java/org/luckyraven/gangland/core/wanted`. Every file:line below was re-read in the source on 2026-10-07 (graphify oriented first: `EvasionClock` node at EvasionClock.java L37).

Correction to the brief: the shipped file is `copsncrooks/wanted.yml` and `copsncrooks/wanted_messages.yml` (commit e57da275 "own copsncrooks/ data folder instead of npc/"), not `npc/wanted.yml`. The sandbox harness README (acceptance/README.md, profiles `r3`/`r4`) still says `npc/wanted.yml`; fix that when the AUTO profile is added.

---------------------------------------------------------------------------------------------------------------------

## 1. The idea in plain English

Today a hidden player loses ONE star (or ALL) when the countdown for his level ends. AUTO keeps the countdown and changes two things about it:

1. HOW MANY stars fall when it ends: 1, a share of them (2, 3...) or all. Decided from the story of the chase.
2. HOW LONG the next countdown is: after a one-star step the next clock can be faster ("they are losing you"), so a hidden player slides down the ladder in steps instead of waiting the full time per rung.

The story of the chase is four questions a player would ask himself:

| Question | What AUTO looks at |
|---|---|
| How did it start? | Was it one small crime, or a rampage (a burst of crimes, a cop killed, a 4+ star spike)? |
| How did it go? | Escalating or cooling: how long since my last crime or new star? How many times did they re-spot me? How long was the whole chase? |
| How does it end? | Did I run out of their search area (clean break), sit tight inside it (hunker down), or barely get away after being in their faces (narrow escape)? |
| What have I been up to lately? | Is this the 4th chase in half an hour (repeat offender)? Did I log out in the middle of it? |

Honest note on "learns": in this design AUTO adapts (a) inside one chase, as its arc builds up, and (b) across a player's last few chases, held in memory only. It is NOT a trained model and nothing is persisted (see section 9). Other designers own cross-session persistence; the hook where a persisted record plugs in is section 11.

The size is decided at drop time only. The countdown (ETA) is shown as today and is honest; the size is a surprise that is explained on the star card afterwards. Rejected: a "forecast" on the bar ("next drop: 2 stars") because it hands players the formula to optimise against and AUTO's value is that it feels like the cops' judgement, not a lookup table.

---------------------------------------------------------------------------------------------------------------------

## 2. The endings (the whole decision, in priority order)

At drop time the planner returns one ENDING. First match wins.

Definitions used below (all measured on the player's chase "arc", section 4):
- `crimes` = number of CrimeRecords on the chase (`HeatLedger.chaseCrimes`, HeatLedger.java:166). Admin/sign/bribe raises add no crime, so a chase made only by `/glw wanted add` has 0 crimes.
- `peak` = highest wanted level seen in this chase.
- `quiet` = seconds since the last crime or the last star RAISE (offline time and the logout gap not counted, section 6).
- `chase` = seconds of chase so far (offline gap not counted).
- `respots` = times the player was SEEN again after the squad had already lost him.
- `outsideRatio` = share of this hiding spell spent OUTSIDE the search zone (spell = since the last SEEN, which is one `Track`, section 4).
- LOCKED = the chase may not end in a lump yet. Reasons: RAMPAGE or LOGOUT (below). The lock lifts when `quiet >= Lock_Cool_Seconds`.
  - RAMPAGE = `crimes in the first Opening_Seconds >= Rampage_Crimes` OR `peak >= Rampage_Peak_Level` OR any crime is `Kill_Cop` (`Crimes.KILL_COP`, gangland-api/.../crime/Crimes.java:11).
  - LOGOUT = the player quit while wanted at any point in this chase.

| # | Ending | Condition | Stars dropped | Card says |
|---|---|---|---|---|
| 1 | HUNKER_DOWN (locked) | LOCKED | 1 | "Still hot: one star at a time" |
| 2 | PETTY | `1 <= crimes <= Petty.Max_Crimes` AND `peak <= Petty.Max_Peak_Level` AND not repeat-offender AND `respots <= Respot_Limit` | ALL | "Small fry, they dropped the case" |
| 3 | COLD_TRAIL | `chase >= Cold_Trail.Chase_Seconds[peak]` AND `quiet >= Cold_Trail.Quiet_Seconds` | ALL | "The trail went stone cold" |
| 4 | CLEAN_BREAK | `outsideRatio >= Clean_Break.Outside_Ratio` AND `respots <= Respot_Limit` AND not repeat-offender | `ceil(level * Clean_Break.Drop_Fraction)` (default 0.5: 1 star at level 1-2, 2 at 3-4, 3 at 5) | "Clean break, you left the area" |
| 5 | HUNKER_DOWN (default) | none of the above | 1 | "You stayed out of sight" |

Why this order:
- Rule 1 first: a rampage or a logout can never be washed out in one lump, no matter how cleverly the player hid. This is the anti-cheese backbone.
- Rule 2 before COLD_TRAIL: a petty chase ends fast; it should not have to sit out 60-300 s of "chase length".
- COLD_TRAIL before CLEAN_BREAK: a long quiet chase beats a single lucky run.
- Rule 5 is today's ONE_STAR, so the floor of AUTO is the current default, never worse.

Result sizes in one table (what a player will actually experience):

| Chase | Typical result |
|---|---|
| One brawl, 1-2 stars, hide 10-20 s | everything gone in one drop (PETTY) |
| Same, but this is the 4th chase in 30 min | one star at a time (repeat offender) |
| 3 stars from several crimes, run out of the zone | 2 stars at once, then a fast clock for the last one |
| 3 stars, hide inside the zone | 1 star, then faster clock, then faster |
| Killed a cop, 4-5 stars | one star at a time until 3 min of calm, then lumps become possible |
| Quit and rejoined mid-chase | one star at a time until 3 min of calm |
| 5 stars, 6 min later, no crime for 2 min | ALL (COLD_TRAIL) |
| `/glw wanted add 3` (no crimes) | 1 star per drop (rule 5), so admin tests stay predictable |

### Stepping (the "faster second clock")

Any drop that leaves stars on the player starts the next countdown with a shorter need:

```
needMs = Seconds_To_Drop[level] * 1000 * stepMultiplier
stepMultiplier = max(Momentum.Floor, speed ^ stepsThisSpell)
speed          = narrow ? Momentum.Narrow_Step_Speed : (locked ? 1.0 : Momentum.Step_Speed)
```

- `stepsThisSpell` = star-losing drops since the last SEEN (0 for the first countdown of every spell, so the FIRST drop always costs the full base time; lumps are never faster than today, only bigger).
- A narrow escape = the player had been SEEN for at least `Momentum.Narrow_Seen_Seconds` straight (a real pursuit) when the squad lost him. It earns the quicker `Narrow_Step_Speed` even while LOCKED. Reason: surviving a hard pursuit should feel like a payoff. It does not change the first drop (still 1 star in a locked chase), it only makes the following rungs quicker.
- Being SEEN again resets the steps (a new `Track`, EvasionClock.java:118), so there is nothing to farm by re-spotting.
- Worked example (defaults, Seconds_To_Drop 10/20/30/45/60, rampage, 5 stars, no narrow): 60 s, 45 s, 30 s, 20 s, 10 s = exactly today's ONE_STAR timing (165 s) while locked. After 180 s of calm the lock lifts and momentum applies: remaining rungs 45*0.75, 30*0.56 ... Without a rampage a 5-star hunker-down chase takes 60 + 34 + 17 + 8 + 4 = 123 s.

---------------------------------------------------------------------------------------------------------------------

## 3. Decision inputs and where each is read (file:line)

| Input | Read from | New work |
|---|---|---|
| level (stars now) | `wanted.getLevel()` EvasionClock.java:115 | none |
| peak level | `ChaseArc.peak`, updated from `WantedLevelChangeEvent` (fired at CORE/Wanted.java:76-82; listener pattern HeatListener.java:28-31) | new `ChaseArcListener` |
| crimes, crime ids, opening burst | `HeatLedger.chaseCrimes(id)` HeatLedger.java:166 (oldest first; `CrimeRecord.at()` CrimeRecord.java:10) | none (read at drop time only; the copy is cheap) |
| cop killed | `chaseCrimes` contains id `Crimes.KILL_COP` | none |
| last crime / last raise time | `ChaseArc.lastHotAt`, set by `CrimeCommittedEvent` (api events/crime) and by a level raise | new listener methods |
| chase start / duration | `ChaseArc.startedAt` (set at `WantedStartEvent`; the ledger has no start time, HeatLedger.java:171-178) | new |
| logout in chase | `ChaseArc.quits` via `PlayerQuitEvent` (EvasionListener.java:23-26 already hears it) | new |
| re-spot count | `ChaseArc.respots`, bumped in the SEEN branch EvasionClock.java:117-123 when `track != null` (i.e. a SEEN after a search) | 1 line |
| seen streak at the break | `Track.seenSince`, set where the SEEN Track is created (EvasionClock.java:118); read at the SEEN to SEARCHING switch (L129-133) | 2 lines |
| outside ratio of the spell | `Track.insideMs` / `Track.outsideMs`, added where progress is added (EvasionClock.java:144-145; `speed()` L189-196 already knows inside vs outside, split it into `inside(...)`) | 4 lines |
| steps this spell | `Track.steps`, incremented after a drop that returned `dropped > 0` (L149-153) | 1 line |
| repeat offender | `ChaseArcs.recentEnds(id)`: start times of up to 8 earlier chases that had at least 1 crime, kept in memory | new |
| config | `config.get().evasion().auto()` read live each tick like every other evasion key (EvasionClock.java:97) | new record |

Deliberately NOT used (cost over value, in line with maps/signals.md): cop-kill listener of its own, player health/armour, cuff failures, break-free counts (a break-free after cuffs counts as a re-spot in practice because cops saw him), exact sighting trail (Keystone `NpcSquad` does not expose it), cop tier history.

---------------------------------------------------------------------------------------------------------------------

## 4. Where the decision is made, and what is stored

### Timing
- Size: at the DROP POINT only, EvasionClock.java:147-153, immediately before `stars.drop(...)`.
- Need (ETA): computed when a search starts (`startSearch`, L166-176), after each star-losing drop (EVADED re-entry L135-138) and when the level changes mid-search (L141-142). Stored in a new `Track.needMs`, so `fireCountdown(player, track, need, speed)` (L179-186) is unchanged and the HUD countdown stays truthful.
- Lock, narrow, respots, outsideRatio are live values; nothing is committed to the player before the drop.
- Not decided: anything per tick except bookkeeping (two long additions). No world queries beyond what `speed()` already does (L190-195).

### Data layout (all in memory, main thread only, like the clock)
- `Track` (EvasionClock.java:42-51, a SPELL = from the SEEN that created it until the next SEEN) gains: `long needMs`, `int steps`, `boolean narrow`, `long seenSince`, `long insideMs`, `long outsideMs`.
- `ChaseArc` (new, per player, survives SEEN, cleared at `WantedEndEvent`, kept across quit): `startedAt`, `lastHotAt`, `peak`, `respots`, `quits`, `offlineAt`.
- `ChaseArcs` (new bean in `EvasionModuleConfig`, next to the clock bean at EvasionModuleConfig.java:28-33, same injected `LongSupplier`): `Map<UUID, ChaseArc>` + `Map<UUID, Deque<Long>> recent` (start times of ended chases that had >= 1 crime).
- Why not keep the arc inside `Track`: the track is thrown away on every SEEN (map evasion.md section 9; EvasionClock.java:118). Why not inside `HeatLedger`: it forgets everything at chase end and is heat-only; the arc needs events HeatLedger does not see (SEEN, quit). `HeatLedger` is only READ.

### Planner
`AutoDropPlanner` (new, pure Java, no Bukkit): `static DropPlan plan(AutoSettings s, ChaseView chase, SpellView spell, int level)` returning `DropPlan(int stars, Ending ending, String reason, double stepMultiplier)`. `ChaseView` and `SpellView` are plain records filled by the clock, so every rule in section 2 is a table test with no server.

### Clock edit (the only place behaviour changes)
EvasionClock.java:147-153 becomes, in words:
```
need = (mode == AUTO) ? track.needMs : secondsToDropFor(level) * 1000        // L147
if (track.progress >= need) {
    DropPlan plan = (mode == AUTO) ? planner.plan(...) : fixed(mode, level)   // fixed = ONE_STAR / ALL_STARS as today
    callEvent(new WantedEvasionDropEvent(player, level, plan.stars(), plan.endingId(), ...))   // pre-drop, informational
    int dropped = stars.drop(user, plan.stars(), WantedCause.EVASION);        // L149, the only star call
    track.progress = 0;                                                       // L150
    if (!wanted.isWanted() || dropped == 0) return;                           // L152, unchanged
    track.steps++;  track.needMs = needFor(next level, track);  ...           // L154-159 as today
}
```
ONE_STAR and ALL_STARS keep their exact behaviour and tests (EvasionClockTest.java:210-220 `allStarsMode_dropsEveryStar`).

---------------------------------------------------------------------------------------------------------------------

## 5. Interaction with Seconds_To_Drop and the Repeating_Timer

- `Seconds_To_Drop[level]` stays the base for every countdown; AUTO only multiplies it by `stepMultiplier` (never below `Momentum.Floor`, default 0.4) and never touches `Outside_Zone_Speed`, so running out of the zone is still twice as fast in real time (EvasionClock.java:144-145).
- Progress is weighted ms, so "how long the chase was" is NOT taken from it: `chase`/`quiet` are wall clock (map evasion.md section 9). Only the evade timer uses progress.
- The settings.yml `Repeating_Timer` safety net is untouched and never lumps: when the last live cop goes RETURNING the track is cleared (EvasionClock.java:99-101) and the timer takes one star per interval (CORE/WantedExecutor.java:83-99). AUTO state (`ChaseArc`) survives that (only `Track` is cleared), so an arc that restarts hunting keeps its history. When the chase eventually ends by any cause the arc is dropped.
- A mid-search star RAISE: `track.level` refreshes (L141-142); AUTO also recomputes `needMs` for the new level and marks the arc hot (`lastHotAt = now`). Progress is carried over, same as today (known edge, map section 9).
- Pauses (restrained, tip-off, EvasionClock.java:110-113) freeze progress and are unaffected; the arc's wall clock keeps running during them (a cuffed player is arrested within seconds, and duration is only one of several inputs).
- A drop whose `WantedLevelChangeEvent` is cancelled returns 0 (CORE/WantedStars.java:126-127): `dropped == 0`, no step is counted and the clock returns exactly as it does today (L152).

---------------------------------------------------------------------------------------------------------------------

## 6. Anti-cheese

| Abuse | Rule |
|---|---|
| Log out to shed history or escape the cops | The arc is NOT cleared on quit (the existing quit handler only clears the evasion track, EvasionListener.java:23-26, and `CopListener.onPlayerQuit` removes the cop group, CopListener.java:72-77; the ledger is not cleared either, HeatListener.java:34-36). `quits++` at quit and the chase is LOCKED for lumps until `quiet >= Lock_Cool_Seconds`. Logout is therefore never faster than staying. |
| Offline time inflating "chase length" or "quiet" | On rejoin (the `WantedStartEvent` with cause `RESTORE`, CORE/WantedStars.java:88-98, hits an existing arc) every arc timestamp is shifted forward by the offline gap, so offline time counts as nothing. |
| Server restart mid-chase | The arc is lost; the chase restarts from the restored level with `crimes = 0` (the ledger is empty too). By the rules that makes it ineligible for PETTY (needs `crimes >= 1`) and COLD_TRAIL needs a fresh `Chase_Seconds`, so it falls to HUNKER_DOWN: conservative, never a free lump. A restored 4+ star chase is RAMPAGE by `peak`. |
| Re-spot spam (get seen on purpose to farm "narrow escape" or reset things) | Every SEEN replaces the `Track` (EvasionClock.java:118): steps and outside-ratio reset, progress resets, so spam only delays. Narrow needs `Narrow_Seen_Seconds` (20 s) of real sighting and only quickens the NEXT rungs, never the first drop and never the size. More than `Respot_Limit` re-spots (default 4) turns off PETTY and CLEAN_BREAK for the chase ("they know your tricks"); COLD_TRAIL stays so a very long dance can still end. |
| Splitting stars with admin/sign raises to look petty | PETTY counts `peak` including raises and needs at least 1 real crime. |
| Chase farming (3-4 tiny chases back to back for clean drops or later getaway XP, roadmap 0.18) | REPEAT OFFENDER: if `Repeat_Chases` (3) chases with at least one crime already ended inside `Repeat_Window_Minutes` (30), PETTY and CLEAN_BREAK are off; the result is one star at a time (rule 5). COLD_TRAIL stays. Set `Repeat_Chases: 0` to disable. In memory only. |
| Crime spam to keep `quiet` high | Impossible: crimes reset quiet (they are the lock). |
| Hiding inside a wall to get unlimited ALL | PETTY/COLD_TRAIL are bounded by crimes/duration/quiet, not by how well the player hid; hiding well only gets rule 5. |
| Admin testing trips the repeat rule | Only chases with at least one crime enter the recent list, so `/glw wanted add` testing never poisons it. |

---------------------------------------------------------------------------------------------------------------------

## 7. Config (copsncrooks/wanted.yml, under Wanted.Evasion; block style, every key commented, Capitalized_Underscore)

`Drop_Mode` comment changes to "ONE_STAR, ALL_STARS or AUTO". The `Auto` block is read only when `Drop_Mode: AUTO`; a missing block means every default below, silently (same as Search_Radius, ChaseConfig.java:66-78).

```yaml
      # What a completed evasion takes: ONE_STAR, ALL_STARS, or AUTO (the cops judge how the chase went and take
      # one star, a share of them, or all; see the Auto block)
      Drop_Mode: ONE_STAR
      # Read only when Drop_Mode is AUTO
      Auto:
         # Seconds from the first crime of a chase that count as its opening burst
         Opening_Seconds: 30
         # Crimes inside the opening burst that make the chase a rampage
         Rampage_Crimes: 4
         # Peak stars that make a chase a rampage however it started
         Rampage_Peak_Level: 4
         # A rampage, or a chase you logged out of, cannot end in more than one star until this many seconds have
         # passed with no new crime and no new star
         Lock_Cool_Seconds: 180
         # How many times the cops may spot you again after losing you before small chases and clean breaks stop
         # paying out in lumps
         Respot_Limit: 4
         # One small crime and the cops let it go: everything drops at once
         Petty:
            # Most crimes a chase may hold to count as small
            Max_Crimes: 2
            # Highest star level a chase may reach to count as small (keep it below Rampage_Peak_Level)
            Max_Peak_Level: 2
         # A long chase that went quiet: everything drops at once
         Cold_Trail:
            # Seconds the chase must have lasted for peak star 1, 2, 3, ...
            Chase_Seconds:
               - 60
               - 120
               - 180
               - 240
               - 300
            # Seconds with no new crime and no new star
            Quiet_Seconds: 90
         # You ran out of the search zone instead of sitting in it
         Clean_Break:
            # Share of the hiding spell that must be spent outside the zone (0 to 1)
            Outside_Ratio: 0.5
            # Share of your stars that drop (0 to 1); the result is rounded up and is at least one star
            Drop_Fraction: 0.5
         # After a star falls and you are still hidden, the next countdown is shorter
         Momentum:
            # Each step is this fraction of the previous countdown (1.0 = no speed-up, 0.75 = a quarter faster)
            Step_Speed: 0.75
            # The countdown never drops below this share of Seconds_To_Drop
            Floor: 0.4
            # Same, after a narrow escape; also works during a rampage
            Narrow_Step_Speed: 0.5
            # Seconds a cop must have kept you in sight before losing you counts as a narrow escape
            Narrow_Seen_Seconds: 20
         # Chases with at least one crime that ended recently; at this many the cops stop going easy (0 = off)
         Repeat_Chases: 3
         # How far back those chases count, in minutes
         Repeat_Window_Minutes: 30
```
(19 keys; a `Chase_Seconds` list shorter than the star count is clamped like `Seconds_To_Drop`. The ponytail cut list: a `Hot_Need_Multiplier` (longer first countdown right after a crime), `Narrow_Cooldown`, `Logout_Locks_Lumps` toggle and per-ending fractions for PETTY/COLD_TRAIL were all considered and dropped: they tune what is already tuned by the lock.)

### Validation (ChaseConfig.evasion(...) at ChaseConfig.java:66-78, plus a `normalise` step; every rule reports through `ConfigReport` as WARNING, same channel as `config.enum` at ChaseConfig.java:90)
- Drop_Mode warning text updated to `unknown Drop_Mode "x", using ONE_STAR (ONE_STAR, ALL_STARS or AUTO)` (ChaseConfig.java:91-92; update `ChaseConfigTest` unknown-mode test at ChaseConfigTest.java:132-141, which only asserts a non-empty report).
- Ints/longs: `.min(0)` via NodeReader as the neighbouring keys (`Lost_Sight_Seconds` min 0). `Rampage_Crimes` and `Rampage_Peak_Level` min 1 (0 would make every chase a rampage): below 1 -> warning + default.
- `Drop_Fraction`, `Outside_Ratio` outside [0, 1] -> clamped with a warning.
- `Step_Speed` and `Narrow_Step_Speed` outside (0, 1] -> default with a warning (a value over 1 would make steps SLOWER; reject, do not allow). `Floor` outside (0, 1] -> default. `Narrow_Step_Speed > Step_Speed` -> clamped to `Step_Speed` with a warning (narrow must be at least as quick).
- `Petty.Max_Peak_Level >= Rampage_Peak_Level` -> warning ("PETTY and RAMPAGE overlap; RAMPAGE wins").
- `Cold_Trail.Chase_Seconds`: empty -> default silently; non-positive entry -> that entry replaced with the previous one and a warning; lookup clamps level into the list like `secondsToDropFor` (EvasionSettings.java:253-264).
- `Auto` block present while Drop_Mode is not AUTO: no warning (inert).
- `EvasionSettings` (record, EvasionSettings.java:244-245) gets a 7th component `AutoSettings auto`; keep a 6-argument convenience constructor delegating to `AutoSettings.DEFAULT` so the two existing test call sites (EvasionClockTest.java:212, :268) and `DEFAULT` (EvasionSettings.java:19) keep compiling.
- Shipped file and `AutoSettings.DEFAULT` must carry the same numbers (the gangland-yaml-review dead-key / missing-key check covers every key above).

---------------------------------------------------------------------------------------------------------------------

## 8. Api / event changes (additive only)

1. `DropMode` (EC/wanted/config/DropMode.java:5-8, module-internal): add `AUTO`. It already parses (ChaseConfig.java:85-88 loops `DropMode.values()`).
2. gangland-api: new event `org.luckyraven.gangland.events.wanted.WantedEvasionDropEvent` (not cancellable; `super(false)` like WantedEvasionStateEvent.java:30): `player`, `levelBefore`, `starsToDrop`, `ending` (String id, one of `one_star`, `all_stars`, `petty`, `cold_trail`, `clean_break`, `hunker_down`; a String so a later ending is not a breaking change), `reason` (String id: `rampage`, `logout`, `repeat_offender`, `respots`, ... or empty), `peakLevel`, `chaseSeconds`, `crimes`. Fired on the main thread just BEFORE `stars.drop`, for EVERY evasion drop (also ONE_STAR and ALL_STARS, with the matching fixed ending), so 0.18 "clean getaway XP by peak stars" and the 0.16+ criminal record can subscribe once. A listener cannot change the outcome (cancel the existing `WantedLevelChangeEvent` for that).
3. `GanglandApi.VERSION` "2.2" -> "2.3" (additive minor, per the contract rule in CLAUDE.md "Module API contract"). Coordinate: SPEC-0.15.md cadence reserves 2.3 for 0.16 `RegionProvider`. If AUTO ships before 0.16, take 2.3 and let 0.16 take 2.4; if both ship together, one bump.
4. NOT changed: `WantedEvasionStateEvent` (the HUD learns the count from the new event instead, section 10), `Messages`, `Settings`, `WantedCause` (EVASION stays; the ending travels in the new event).

---------------------------------------------------------------------------------------------------------------------

## 9. Persistence

None in this version. Everything (arcs, recent-chase list) is in memory on the main thread. Costs and ceilings, stated openly:
- A server restart mid-chase restarts the arc (conservative result, section 6). A restart also forgets repeat-offender history, so the farming rule resets on every restart. This is acceptable for 0.15.x because the wave it protects (0.18 XP) comes later.
- If persisted learning is wanted later: a module repository in `copsncrooks.database` (pattern: DetainmentRepository, `setDataSupplier` wired in the owning manager, per feedback_repository_data_supplier). The `recent` list is the only thing that would move, behind the same `ChaseArcs.recentEnds(UUID)` method, so the planner and clock do not change.

---------------------------------------------------------------------------------------------------------------------

## 10. HUD and star-card messaging (copsncrooks/wanted_messages.yml, module-owned, no api `Messages` change)

New `WantedMessages.Key` entries are required, each with an in-code fallback equal to the shipped line (WantedMessages.java:21-38 pattern). New placeholder `%count%` is already listed in the file header (stars lost this drop).

```yaml
Hud:
   Bar:
      # Several stars were just lost at once; %count% is how many
      Evaded_Many: "&a%stars% &2&l%count% STARS LOST"
   Card:
      # AUTO: one small crime, the cops let it go
      Drop_Petty: "&aSmall fry, they dropped the case &7(-%count%)"
      # AUTO: a long chase that went quiet
      Drop_Cold_Trail: "&aThe trail went stone cold &7(-%count%)"
      # AUTO: you left the search area
      Drop_Clean_Break: "&aClean break, you left the area &7(-%count%)"
      # AUTO: only one star falls because the chase is still hot (rampage or you logged out)
      Drop_Still_Hot: "&eStill hot, one star at a time"
      # AUTO: the star that falls after a narrow escape; the next one comes quicker
      Drop_Narrow: "&aThat was close, they are losing you"
      # AUTO: nothing special, you stayed out of sight (same text as Drop_Evasion)
      Drop_Hunker: "&aYou stayed out of sight"
```

Mechanics (no change to WantedLevelChangeEvent or WantedStars):
- `WantedHudListener` listens (MONITOR) to the new `WantedEvasionDropEvent` and remembers `(ending, reason, count, narrow)` per player. The card is built inside `onLevelChange` (WantedHudListener.java:71-85), which runs inside `stars.drop` and therefore AFTER the pre-drop event: for cause EVASION it calls `StarCard.dropCard(messages, cause, pending)` (StarCard.java:35-43 gets an overload; the old signature stays for DECAY/other). Mapping: `petty` -> Drop_Petty, `cold_trail` -> Drop_Cold_Trail, `clean_break` -> Drop_Clean_Break, `hunker_down` with reason `rampage`/`logout` -> Drop_Still_Hot, `hunker_down` + narrow -> Drop_Narrow, plain -> Drop_Hunker (= today's Drop_Evasion), fixed `one_star`/`all_stars` -> today's Drop_Evasion.
- The `Evaded` bar flash (WantedHud.java:93-97; `StarCard.barTitle` EVADED branch) uses `Evaded_Many` when the remembered count is above 1. The remembered entry is cleared on EVADED, WantedEnd and quit (WantedHudListener.java:94-100).
- The searching bar keeps its seconds countdown (`BAR_SEARCHING`, StarCard.java:46-49) with the new `needMs`; no new text while searching.
- A drop to 0 stars still shows the card (`isGetaway`, WantedHudListener.java:80, :103-105), so a PETTY lump reads "Small fry, they dropped the case" on the title subtitle even though the HUD hides immediately after.
- When the drop is a rampage cap, the card says why one star was all you got; this is the single most important line for the "why is this server stingy" support question.
- Debug log (the module's existing debug logger): one line per AUTO decision so admins and the acceptance harness can verify: `AUTO drop: Runner level=4->3 ending=hunker_down reason=rampage crimes=1 peak=4 chase=48s quiet=12s respots=0 outside=0.00 steps=0`.

---------------------------------------------------------------------------------------------------------------------

## 11. Later releases (0.16+): where they plug in, nothing shipped now

- Hideouts (0.16, SPEC: `Hideout_Speed` and region tags, `RegionProvider` api 2.3 per artifact/overhaul-updated.html): add rule `HIDEOUT` between COLD_TRAIL and CLEAN_BREAK: the spell ended with the player inside a region tagged `hideout` and the chase is not LOCKED -> ALL, card "Safe house, they gave up". One new condition in `AutoDropPlanner` and one more `SpellView` boolean, filled from the region lookup. Keys (`Hideout.Drop_Fraction`) are added in that release, not now, so no dead keys ship.
- Districts: leaving the district the chase started in is a second "left the area" signal, folded into `outsideRatio`'s CLEAN_BREAK test.
- Criminal record (0.16): subscribes to `WantedEvasionDropEvent` + `WantedEndEvent`, and replaces the in-memory `recent` list as the repeat-offender source. Dispatch/stations: a station whose spawner hunts the player could report a sighting kind; the arc could then tell a cop-LOS sighting from a gunshot or civilian witness (maps evasion.md section 4 says none of that is recorded today); not needed for 0.15.x.
- 0.18 getaway XP: one payout at chase end from `peakLevel` and the last `WantedEvasionDropEvent.ending`, never per drop, so lumps cannot multiply it.

---------------------------------------------------------------------------------------------------------------------

## 12. Tests

All new tests must be shown red against pre-change code first (repo rule); most are red by compilation (no `AUTO`, no planner).

Unit (JUnit 5, no server):
- `AutoDropPlannerTest` (pure, table driven): each row of section 2 plus edges: petty with 1 crime and peak 2 -> ALL(2); petty with 0 crimes (admin raise) -> 1; petty with 3 crimes -> falls through; cop kill at 2 stars -> locked, 1; rampage by opening burst (4 crimes in 25 s), by peak 4, by Kill_Cop; lock lifts exactly at `quiet == Lock_Cool_Seconds`; logout lock; COLD_TRAIL boundary on `Chase_Seconds[peak]` and `Quiet_Seconds` (list clamped for peak above its length); CLEAN_BREAK fraction rounding (`ceil`, level 1 -> 1, level 3 -> 2, level 5 -> 3, fraction 0.0 -> 1, 1.0 -> all); `Respot_Limit` turns off PETTY and CLEAN_BREAK but not COLD_TRAIL; repeat-offender turns off PETTY and CLEAN_BREAK; rule priority (a chase matching PETTY and COLD_TRAIL picks PETTY); stepMultiplier math and `Floor`; narrow overrides lock for speed.
- `ChaseArcsTest` (injected `LongSupplier`): start/peak/respot bookkeeping, `restore` after quit shifts timestamps by the gap, offline time not counted in `chase`/`quiet`, `quits` locks, end clears the arc and appends to `recent` only with >= 1 crime, `recent` capped at 8 and aged by the window, admin chase leaves no trace.
- `EvasionClockTest` additions (setUp EvasionClockTest.java:73; fake clock + captured events, helper `tickSeconds` L123): AUTO petty drops ALL and the pre-drop event carries `petty`; AUTO default drops 1 then the next `fireCountdown` shows `ceil(base*0.75)`; SEEN resets steps; narrow (SEEN for 25 s then hide) uses 0.5; rampage keeps timing equal to ONE_STAR; `needMs` recomputed on a mid-search raise; cancelled level change -> `dropped == 0` -> no step, no EVADED; ONE_STAR and ALL_STARS regression (existing tests untouched); last-star drop fires no EVADED (existing L311-325 still green with the event added).
- `ChaseConfigTest`: AUTO parses; `Auto` block defaults when missing; every invalid value in section 7 warns and falls back; the new unknown-Drop_Mode text; `Auto` present with Drop_Mode ONE_STAR is inert and silent.
- `StarCardTest` / `WantedMessages` key test: each new key resolves, fallback equals shipped text, `Evaded_Many` substitutes `%count%`.
- `WantedHudListenerTest`: the remembered pending entry picks the card and bar variant and is cleared on EVADED/end/quit.

Acceptance (sandbox harness, `brainstorming/cnc-overhaul-2026-10-05/acceptance/`; pattern of N1 `scenarios/N1-evasion-drop.json`: seal Runner in a stone room, `expectChat: decreased`, `say CNCMARK:` markers; crimes made the N4b way with `damage Target 1000 minecraft:player_attack by Runner`):
- New profile `auto` in `prep-cnc015.sh`: copies a fixture `ACC/fixtures/wanted-auto.yml` over the extracted `copsncrooks/wanted.yml` with `Drop_Mode: AUTO` and short numbers (`Lock_Cool_Seconds: 20`, `Cold_Trail.Chase_Seconds` 15/20/25/30/35, `Quiet_Seconds: 10`, `Repeat_Chases: 3`). A fixture instead of `yset.js` because `yset.js` sets scalar keys and the lists would need list support.
- A1 `auto-petty`: Runner kills Target once (Kill_Player, 1 crime), `/glw wanted add 2`, seal in the room; expect ONE drop from 2 to 0 (`decreased`, wanted level 0, debug line `ending=petty`). Verdict PASS.
- A2 `auto-rampage-lock`: `/glw wanted add 4` plus one kill (peak 4 = RAMPAGE), seal; expect first drop 4 -> 3 (not 0), debug line `ending=hunker_down reason=rampage`. PASS.
- A3 `auto-momentum`: from A2's state (no crime, no re-spot), the second drop comes sooner than the first (timestamps of the debug lines or `decreased` in `chat.txt`, ratio about 1 for locked rungs, about 0.75 after the lock lifts at 20 s). REVIEW on timing tolerance.
- A4 `auto-logout-lock`: crime chase of 2 stars, quit, rejoin (R4a/R4b pattern, same server), seal; expect the drop is 1 star (`reason=logout`), not 2. PASS.
- A5 `auto-clean-break` (best effort): leave the zone radius with `tp` at 3 stars, expect 2 stars lost (`ending=clean_break`). REVIEW.
- Re-spot, narrow escape and repeat-offender need real cop sightings or three consecutive chases: unit tests cover them; the sandbox bot cannot observe boss bars/titles (README "Known limits"), so the manual checklist M gets: "star card text on a petty lump, a rampage cap and a clean break; `Evaded_Many` flash".
- Extend `cnc-verdict.js` with rows `A1..A5` reading the debug line from `server.log`; add them to `run-all-cnc015.sh`. Pass criterion for the AUTO release: A1, A2, A4 PASS, A3/A5 REVIEW read.
- Regression: R1-R4 and N1-N3 unchanged on the `default` profile (ONE_STAR).

---------------------------------------------------------------------------------------------------------------------

## 13. Work breakdown (for the planner that merges the angles)

1. `DropMode.AUTO`, `AutoSettings` record + parse + validation + shipped YAML (ChaseConfig.java:66-94, EvasionSettings.java:244-264, wanted.yml:57-81).
2. `AutoDropPlanner` + `ChaseView`/`SpellView`/`DropPlan` (pure) with `AutoDropPlannerTest`.
3. `ChaseArc`, `ChaseArcs` bean, `ChaseArcListener` (`@ListenerHandler`: WantedStart/LevelChange/End at HIGH for End so the ledger is still readable before HeatListener clears it at MONITOR, CrimeCommitted, Quit); wiring in `EvasionModuleConfig.java:28-40`; `EvasionClock` constructor gains `ChaseArcs` and `HeatLedger` (HeatLedger arrives through the bean-parameter ordering edge, feedback_bean_ordering_via_params).
4. `EvasionClock` edits (Track fields, SEEN/search/drop branches, `speed()` split).
5. api `WantedEvasionDropEvent` + `GanglandApi.VERSION` "2.3".
6. HUD: `WantedMessages.Key` entries, `wanted_messages.yml`, `StarCard.dropCard` overload, `WantedHudListener` pending map, bar variant.
7. Docs: `documentation` page for admins (endings table, tuning guide), bug docket note (no bug found; record the design in the docket README if the owner wants), changelog entry both `.md` and `.bbcode.txt`.
8. Acceptance profile + scenarios + verdict rows.

Order: 1, 2, then 3-4 (clock behind AUTO only), 5-6, 7-8. Steps 1-4 can ship as a hidden feature (default stays ONE_STAR) before the HUD work.

---------------------------------------------------------------------------------------------------------------------

## 14. Risks

1. Defaults are guesses: nobody has played AUTO. Mitigation: the debug line, all numbers in YAML, and a short tuning session in the sandbox with the A-scenarios before the default is ever changed from ONE_STAR. Default stays ONE_STAR; AUTO is opt-in.
2. Players cannot predict the size. Mitigated by the star-card reason and by rule 5 being today's behaviour, so the worst case equals the current default.
3. Lumps are generous for small chases (PETTY drops 2 stars in 20 s). Intended GTA feel; admins who disagree set `Petty.Max_Peak_Level: 1` or `Max_Crimes: 1`.
4. Money: every dropped star is priced by `WantedStars.drop` (CORE/WantedStars.java:129-134, Take_Money), so a lump of 3 charges what 3 single drops would, in one withdrawal. Off by default (`Take_Money.Enable: false`), N2/N3 rows still hold.
5. `quiet` counts crimes and raises, not sightings: a player who keeps being seen but commits nothing can still reach COLD_TRAIL after a long chase. Intended (a long fruitless pursuit ends), bounded by `Respot_Limit` only for PETTY and CLEAN_BREAK.
6. Memory: arcs of players who never return after quitting mid-chase. Mitigation: a quit-time stamp plus pruning arcs older than 30 min on the next join/start (`ChaseArcs.prune`), cap 8 recents per player. Same leak class already exists for `HeatLedger.chases`.
7. Constructor growth of `EvasionClock` (7 -> 9 args) breaks every test that builds it (setUp EvasionClockTest.java:73) and `EvasionModuleConfig.java:28-33`; mechanical.
8. `EvasionSettings` arity change: handled with a secondary constructor (section 7); grep `new EvasionSettings(` before editing (main: ChaseConfig.java:73, EvasionSettings.java:19).
9. Event order assumption: `WantedHudListener` relies on `WantedEvasionDropEvent` being fired BEFORE `stars.drop` so the pending entry exists when the level-change handler runs; a test pins the order.
10. `WantedEndEvent` ordering: the arc reads the ledger's crimes at HIGH priority; HeatListener clears at MONITOR (HeatListener.java:34). If someone raises HeatListener's priority the recents list silently stops recording crimes; a test on `ChaseArcListener` + `HeatListener` ordering pins it.
11. Roadmap clash on the api minor (section 8.3) and on 0.16 region work: AUTO needs nothing from 0.16, so it can ship first.
12. Scope: this design adds ~6 classes. The cut line if smaller is wanted: ship only rules 1, 2, 5 plus momentum (no COLD_TRAIL, CLEAN_BREAK, repeat offender, narrow); each removed rule is one `if` in the planner and a block of keys.
