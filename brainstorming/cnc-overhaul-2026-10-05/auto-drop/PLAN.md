# AUTO Drop_Mode - implementation plan (judge's merge)

Status: PLAN ONLY. No source edited, nothing committed. Base: branch `0.15.1` (HEAD f475888d after the settings-ownership
merges; first drafted on 046887e1; gangland-api `VERSION = "2.2"`, cops-n-crooks `module.yml` `Host_Api: 2.2`, root
`pom.xml:57` `<revision>0.15.1</revision>`). Proposed release: **0.15.2**, own branch cut from `0.15.1` (patch-bump
convention). 0.15.1 is **not yet in master** (`git merge-base --is-ancestor` fails), so 0.15.2 ships after or together with
it (D1). Review rulings: `reviews/rulings.md` (revision 2 of this plan).

Paths: `EC` = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks`,
`ECR` = `gangland-features/cops-n-crooks/src/main/resources/copsncrooks`, `ECT` = the matching `src/test/java/...` tree,
`CORE` = `gangland-core/src/main/java/org/luckyraven/gangland/core`.

Inputs judged: `maps/evasion.md`, `maps/signals.md`, `maps/history.md`, `designs/rules.md` (A), `designs/learning.md` (B),
`designs/gameplay.md` (C). Every anchor below was re-read in the source on 2026-10-07. Graphify was queried first
(`EvasionClock` node at EvasionClock.java L37); the graph is one commit older than HEAD (046887e1 touches api settings
code, not the evasion files), so anchors were confirmed against the files, not the graph.

Corrections to the brief and the maps (verified):

- The YAML is `ECR/wanted.yml` and `ECR/wanted_messages.yml` (data folder `copsncrooks/` since e57da275), not
  `npc/wanted.yml`. The acceptance harness still extracts `npc/wanted.yml` (`acceptance/prep-cnc015.sh:63-64`,
  `acceptance/README.md:37`).
- `EvasionSettings` is a 37-line record: components at `EvasionSettings.java:16-17`, `DEFAULT` at `:19-21`,
  `secondsToDropFor` at `:30-32`. The map's "L244-249" is wrong.
- `HeatListener.onChaseEnd` (`EC/listener/wanted/HeatListener.java:33-36`) and `EvasionListener.onWantedEnd`
  (`EC/listener/wanted/EvasionListener.java:19-22`) both run at MONITOR, so their order is undefined. Anything that must
  read the chase's crimes at chase end runs at HIGH.
- The settings-ownership merges after 046887e1 moved `Kill_Combo` from settings.yml into `ECR/wanted.yml` (now lines
  10-23, read by `ChaseConfigLoader.getKillCombo()`), so every `wanted.yml` line below is 15 lower than in the first draft
  (`Drop_Mode` comment `:80`, `Outside_Zone_Speed` `:97`), and `ChaseConfig.java` moved 3 lines (`block` `:39`,
  `evasion(...)` `:69-81`, the `Drop_Mode` warning `:92-94`). The evasion Java files are unchanged.
- Nothing in the bug docket mentions evasion (grep of `bug-docket-2026-09-06/triage` and `cross-docket-2026-09-10`:
  no hits). AUTO fixes no docket entry.

---

## 1. Summary in plain words

AUTO keeps the 0.15 evasion clock (seen, searching, star lost) and changes two things.

1. **How many stars fall** when the hide timer runs out. The cops read the story of the chase and pick one of five
   named endings: *still hot* (1 star), *small fry* (all stars), *cold trail* (all stars), *clean break* (half your
   stars, rounded up, so 2 of 3 or 3 of 5) or *hunker down* (1 star, today's behaviour).
2. **How long the next timer is.** After a star falls and you are still hidden, the next countdown is shorter
   ("they are losing you"). A player the server has learned always gets away waits longer; one who is always caught
   waits less.

The story has four parts, matching the owner's words:

| Owner's words | What AUTO reads |
|---|---|
| "how it started" | A rampage (4+ crimes in the first 30 s, a cop killed, or 4+ stars) locks the chase to one star at a time. A small chase (1-2 crimes, at most 2 stars) is forgiven in one go. |
| "how long was the chase" | Wall-clock seconds since the chase began, with offline time removed, compared with what this server has **learned** a typical getaway at that star level takes. |
| "after a specific period" | The lock lifts after 180 s with no new crime or star. A cold trail needs 90 s of quiet. The per-level `Seconds_To_Drop` stays the base timer. |
| "how it might end" | How this hiding spell is going: did you leave the search zone (clean break), sit tight (hunker down), or just slip a long pursuit (narrow escape, faster next timer). Plus the learned escape habit of this player. |
| "learns" | Two small tables: per player, an escape habit (escapes compared with what the server expects for his chases); per star level, how long the cops typically stay on a player's tail before losing him for good. Both are updated once per finished chase, by one fixed rule, and survive restarts. |

Cold start: a fresh server with no rows plays exactly the endings above with the configured guesses. AUTO is opt-in;
`Drop_Mode: ONE_STAR` stays the shipped default.

---

## 2. Design scores

1-10, higher is better. "Cost/risk" is high when the change is cheap and safe on the 0.15 code.

| Design | Fits the request | Explainable to players | Tunable by admins | Exploit resistance | Cost/risk | Testability | Total |
|---|---|---|---|---|---|---|---|
| A "readable rules" (score ladder) | 6 | 5 | 6 | 4 | 8 | 9 | **38** |
| B "it learns" (statistics) | 7 | 4 | 4 | 7 | 4 | 7 | **33** |
| C "gameplay" (named endings) | 7 | 9 | 8 | 9 | 7 | 9 | **49** |

Why:

- **A** covers period, length and start well and its pure planner is easy to test. But a score of points (R + G
  against a threshold ladder) is hard to tell a player, 25 keys interact, and "learns" is an in-memory getaway count
  that a restart wipes. It also has a real hole: `chaseStart` is dropped on quit while `HeatLedger` keeps the crimes
  (HeatListener has no quit handler, `HeatListener.java:22-36`), so after a relog the opening crime falls outside
  `Opening_Window_Seconds` and the "started big" penalty (-8 to -30) becomes the neutral "borrowed" 0. Relogging pays.
- **B** is the only design that really learns and persists, and its anti-farming analysis is the best of the three.
  But `delta` and `T` are hard to explain, the 13 statistics knobs are not admin-friendly, it ignores how the chase
  started apart from its cause, and it needs two tables plus an api change. Two flaws found while checking its maths:
  (1) with `Decay_Per_Chase` 0.90 and `Prior_Chases` 5, a player who escapes every chase at a 0.55 escape-rate level
  approaches `delta` = 4.5/15 = 0.30 only in the limit, so the shipped `Habitual_Escaper_Delta: 0.30` never fires;
  (2) it learns the typical length from every escape, including the shortened ones AUTO itself hands out, so the
  typical length can ratchet down (AUTO shortens chases, shorter chases teach "chases are short", AUTO shortens more).
- **C** maps one-to-one onto the owner's words, every result has a name and a card a player understands, its 19 keys
  each tune one rule, and it has the strongest anti-cheese (logout lock with offline time removed, re-spot limit,
  repeat offender, rampage lock). Its gap is the word "learns": memory only, forgotten at restart.

**Winner: C.** Grafted in:

- From **B**: the learner (per-player habit, per-level typical getaway length), its persistence pattern, its
  cold-start guarantee, its farming guards and the try/catch fallback to ONE_STAR. Fixed: the habitual-escaper
  threshold becomes 0.20 (reached after about 6 straight escapes at 3 stars on a busy server; section 3.6 W2), and the
  typical length measures **contact time**, from the start of the chase to the last time the cops lost sight of him,
  learned from every getaway whatever its ending. AUTO only changes what happens after that moment, so it cannot teach
  itself that chases are short, and no ending is left out of the sample. It is clamped to half..double the configured
  guess. The habit also feeds C's "known face" rule, so a habitual escaper loses the lumps.
- From **A**: the strict validation table, the debug line carrying every input, and the split between "the clock
  stops tracking you" (`clear`, gate failure) and "the chase ended" (handled by the arc listener).
- From **A**: no gangland-api change in this release (C wanted a new api event). The only consumer is the HUD in the
  same module; it reads the pending decision from a bean instead of an event. See section 7.

---

## 3. How AUTO decides

### 3.1 Inputs

| Input | Meaning | Source |
|---|---|---|
| `level` | stars now | `wanted.getLevel()`, `EvasionClock.java:115` |
| `peak` | highest level this chase | new `ChaseArc.peak`, seeded by `start` with `WantedStartEvent.getWantedLevel()` (the 0->N `WantedLevelChangeEvent` fires before the start event, so no arc exists yet for it, `CORE/wanted/Wanted.java:68-96`), then raised from `WantedLevelChangeEvent.getNewLevel()`; RESTORE changes are ignored |
| `crimes` | crimes on this chase | `HeatLedger.chaseCrimes(id)` (`EC/wanted/heat/HeatLedger.java:166-169`), read only at search start, after a drop and at drop time (it copies the list) |
| `opening` | crimes within `Opening_Seconds` of the first crime | same list, `CrimeRecord.at()` (`CrimeRecord.java:10`) |
| `copKilled` | any crime id `Crimes.KILL_COP` | same list; `gangland-api/.../crime/Crimes.java:11` |
| `chase` | seconds since the chase began, offline time removed | new `ChaseArc.startedAt`, stamped at `WantedStartEvent` |
| `quiet` | seconds since the last crime or star raise, offline time removed | new `ChaseArc.lastHotAt`, seeded by `start`, then set by a `CrimeCommittedEvent` the heat ledger keeps (`Heat.Enable` and weight > 0, as `HeatLedger.java:81-90` checks) and by raises; a RESTORE raise is not a raise here |
| `quits` | times the player quit mid-chase; a RESTORE start with no arc (restart, or offline over 30 min) counts as 1 | new `ChaseArc.quits`, `PlayerQuitEvent` when an arc exists (not via `UserManager`, see section 5) |
| `respots` | times seen again after the squad had lost him | new `ChaseArc.respots`: `ChaseArc.searched` is set when a search starts; a new SEEN track (`EvasionClock.java:116-123`, also after `clear` dropped the track because the squad went RETURNING) bumps `respots` and resets the flag when it is set |
| `outsideRatio` | share of this spell's searching time spent outside the zone | new `Track.insideMs` / `Track.outsideMs`, added where progress is added (`EvasionClock.java:140-145`) |
| `teleported` | he jumped more than 32 blocks or into another world by command, plugin or portal during this spell (ender pearls and chorus fruit do not count) | new `Track.teleported`, set by `EvasionListener.onTeleport` through `EvasionClock.teleported(player)` |
| `narrow` | he had been in sight `Narrow_Seen_Seconds` straight before he broke away | new `Track.seenSince`, set at `EvasionClock.java:118`, read at the SEEN to SEARCHING switch `:129-133` |
| `steps` | stars lost since the last SEEN | new `Track.steps`, `++` after a drop with `dropped > 0` (`:149-153`) |
| `recentEnds` | crime chases this player ended inside `Repeat_Window_Minutes` | new `ChaseArcs.recent`, memory only |
| `delta` | learned escape habit, -1..1 (0 = stranger) | new `ChaseLearner.delta(UUID)` |
| `T(peak)` | learned typical getaway length at that peak, seconds | new `ChaseLearner.typicalSeconds(peak)` |

Wall-clock seconds are used for `chase` and `quiet`; `Track.progress` is speed-weighted and only drives the timer
(maps/evasion.md section 9).

### 3.2 Derived flags

```
rampage   = opening >= Rampage_Crimes  OR  peak >= Rampage_Peak_Level  OR  copKilled
locked    = (rampage OR quits > 0)  AND  quiet < Lock_Cool_Seconds
knownFace = (Repeat_Chases > 0 AND recentEnds >= Repeat_Chases)            # short-term, memory
            OR delta >= Learning.Habitual_Escaper_Delta                    # long-term, learned
```

### 3.3 The drop table (decided at the drop point only, first match wins)

| # | Ending | Condition | Stars dropped | Card |
|---|---|---|---|---|
| 1 | `STILL_HOT` | `locked` | 1 | Drop_Still_Hot |
| 2 | `PETTY` | `1 <= crimes <= Petty.Max_Crimes` and `peak <= Petty.Max_Peak_Level` and not `knownFace` and `respots <= Respot_Limit` | all | Drop_Petty |
| 3 | `COLD_TRAIL` | `chase >= Cold_Trail.Ratio * T(peak)` and `quiet >= Cold_Trail.Quiet_Seconds` | all | Drop_Cold_Trail |
| 4 | `CLEAN_BREAK` | `outsideRatio >= Clean_Break.Outside_Ratio` and not `teleported` and `respots <= Respot_Limit` and not `knownFace` | `max(1, ceil(level * Clean_Break.Drop_Fraction))` | Drop_Clean_Break |
| 5 | `HUNKER_DOWN` | otherwise | 1 | Drop_Known_Face if `knownFace`, Drop_Narrow if `narrow`, else today's Drop_Evasion |

Every result is clamped to `1..level`. AUTO never asks for 0 stars (`WantedStars.drop` with 0 is a no-op returning 0,
which the clock reads as "no drop", `EvasionClock.java:152`).

Rule 1 first, so a rampage or a logout can never be washed away in one lump. Rule 5 is today's ONE_STAR, so AUTO's
floor is the current default.

### 3.4 The timer (decided at search start, after each drop, and when the level changes mid-search)

```
speed   = locked ? 1.0 : (narrow ? Momentum.Narrow_Step_Speed : Momentum.Step_Speed)
step    = speed ^ steps
habit   = clamp(1 + Learning.Habit_Time_Strength * delta, Learning.Min_Time_Factor, Learning.Max_Time_Factor)
factor  = clamp(step * habit, Momentum.Floor, Learning.Max_Time_Factor)
needMs  = max(1000, Seconds_To_Drop[level] * 1000 * factor)
```

`needMs` is stored in `Track.needMs`, so the boss-bar countdown stays honest (`fireCountdown`, `EvasionClock.java:179-186`,
keeps its formula). The first countdown of a spell has `steps = 0`, so it is today's first-star timer times `habit`: a
stranger waits exactly today's time, a habitual loser at least `Min_Time_Factor` (0.6) of it. A locked chase never gets
momentum, narrow or not, so the lock really is today's ONE_STAR timing. Being seen again starts a new `Track`
(`:117-121`), which resets steps, the outside ratio and `teleported`.

Outside AUTO, and whenever `needMs` is still 0 (a reload flipped the mode to AUTO in the middle of a search), the clock uses
today's `cfg.secondsToDropFor(level) * 1000`, so a mode flip never drops a star at once.

### 3.5 The learning rule (once per chase, at `WantedEndEvent`)

Outcome `o`: EVASION = 1 (escaped), DECAY = 0.5 (the safety-net timer finished it), ARREST / BRIBE = 0 (caught), and
these count only when the chase **peaked at 2 stars or more** (a 1-star arrest is the cheapest chase and would otherwise
be the strongest signal). DEATH is never counted: the reset fires "no matter how the player died"
(`gangland-impl/.../listener/player/EntityDamageListener.java:306-307`), so a fall or lava would otherwise teach the
server that he is always caught. ADMIN, SIGN, RESTORE, UNKNOWN ends are not counted either. A chase counts only if it
**started** with cause CRIME, lasted at least `Min_Chase_Seconds`, and the player's previous counted chase ended at
least `Min_Seconds_Between_Outcomes` ago. Learning runs only while `Drop_Mode` is AUTO and `Learning.Enable` is true
(owner decision D3).

`contact` = seconds from the chase start to the last time the squad lost sight of him (`ChaseArc.lastLostAt`, stamped at
the SEEN to SEARCHING switch, `EvasionClock.java:129-133`), offline time removed.

```
# server expectation for this peak, read BEFORE this chase is added
p   = (S[peak].escaped + 20 * Escape_Rate[peak]) / (S[peak].n + 20)               # 20 = Server_Prior_Chases

# player row H (decay d = Decay_Per_Chase)
H.n = H.n * d + 1;   H.actual = H.actual * d + o;   H.expected = H.expected * d + p;   H.lastAt = now

# server row, a heavy player counts for less: w = min(1, 4 / max(1, H.n before this chase))
S.n = S.n * 0.99 + w;   S.escaped = S.escaped * 0.99 + w * o
if o == 1:                                     # every getaway, whatever the last ending was
    S.typical += sign(contact - S.typical) * 0.10 * S.typical * w   (first sample: S.typical = contact)
    S.typicalCount = min(100, S.typicalCount + w)

# reading back
delta    = clamp((H.actual - H.expected) / (H.n + Prior_Chases), -1, 1)
T(peak)  = clamp((S.typical * c + Typical_Seconds[peak] * 20) / (c + 20),  0.5 * Typical_Seconds[peak], 2 * Typical_Seconds[peak]),
           c = S.typicalCount
```

The 0.99 server decay, the 0.10 median step, the fair-share 4 and the server prior 20 are constants, not keys
(`ponytail:` comments; promote to keys if an admin ever needs them). No randomness: the same rows and the same chase
always give the same answer.

### 3.6 Worked examples

Shipped numbers: `Seconds_To_Drop` 10/20/30/45/60, `Lost_Sight_Seconds` 3, `Outside_Zone_Speed` 2.0, heat thresholds
100/250/450/700/1000, `Seen_By_Cop_Multiplier` 1.5, `Streak_Bonus` 1.5 for a crime within `Kill_Combo.Reset_After` (10 s,
`wanted.yml:16`) of the previous one, and the AUTO defaults of section 4. Times are seconds from the first crime.
"Seen" on a crime means the cop-seen heat multiplier. In every example the last unbroken stretch in sight before a search
is under `Narrow_Seen_Seconds` (the squad lost and re-found him in between), so `narrow` is false unless a row says so.
Examples E1-E7 are a **cold-start server** (no rows: `delta` 0, `T` = `Typical_Seconds` 30/60/90/120/150, so the
cold-trail line is 60/120/180/240/300 s).

| # | Chase | Decision | Result | Today (ONE_STAR) |
|---|---|---|---|---|
| E1 small fry | t=0 Kill_Player seen (120 heat, 1 star), t=10 Kill_Civilian seen and chained (100 x 1.5 x 1.5 = 225, total 345 = 2 stars). Last seen at 25, search from 28, hides. | Drop at 48: not locked (2 opening crimes, peak 2, no cop killed), PETTY (2 crimes, peak 2) | **all: 2 -> 0 at 48 s**, card "Small fry, they dropped the case (-2)" | 2 -> 1 at 48, 1 -> 0 at 58 |
| E2 hunker cascade | t=0 Store_Robbery seen (300 = 2 stars), t=40 Kill_Civilian seen (450 = 3 stars). Last seen at 60, search from 63, hides inside the zone. | Not locked; PETTY fails (peak 3); cold trail line 180 s not reached; inside zone, so HUNKER each time | **1 star x3**: 3->2 at 93 (30 s), 2->1 at 108 (20 x 0.75 = 15 s), 1->0 at ~114 (10 x 0.5625 = 5.6 s); 51 s hidden | 60 s hidden |
| E3 clean break, 2 stars | E2, but he runs out of the zone 6 s into the search. Real time: 6 s inside, 12 s outside (24 s of progress at speed 2). | Drop at 81: outsideRatio 12/18 = 0.67 >= 0.5, CLEAN_BREAK, ceil(3 x 0.5) = 2 | **2 stars: 3 -> 1 at 81**, then 1 -> 0 at ~85 (7.5 s of progress at speed 2, clean break again) | 3->2 at 81, 2->1 at 91, 1->0 at 96 |
| E4 N of 5 | t=0 Jailbreak seen (675 = 3), t=30 Kill_Cop seen (+225 = 4), t=60 Assault_Cop seen (+150 = 5). Re-spotted twice, loses them for good at 230, search from 233, runs out at once (4 s in, 28 s out). | Drop at 265: rampage (cop killed) but quiet = 205 >= 180 so not locked; PETTY no; cold trail line 300 not reached; outsideRatio 0.875, respots 2 <= 4 | **3 stars: 5 -> 2 at 265** "Clean break (-3)"; then 2 -> 1 at ~272 (ceil 1) and 1 -> 0 at ~275 | five single drops, about 85 s in all (AUTO: about 42 s) |
| E5 cold trail, all of 4 | Cop-killer chase at 4 stars, last crime at t=90, re-spotted 3 times while dancing around, loses them at 380, search from 383, hides inside. | Drop at 428: quiet 338, not locked; PETTY no; chase 428 >= 2 x 120 = 240 and quiet >= 90: COLD_TRAIL | **all: 4 -> 0 at 428**, "The trail went stone cold (-4)" | four drops over 105 s more |
| E6 still hot | Same cop killer hides right after his last crime at t=90; search from 93. | locked at every drop (quiet 48, 78, 98, 108 < 180); speed 1.0, so no momentum | **1 star x4** at 138, 168, 188, 198: exactly today's timing; card "Still hot, one star at a time" | same |
| E7 logout | E1, but he quits at t=20 and rejoins 5 min later (offline gap removed from every arc stamp); once cops hunt him again, he hides. | quits = 1 and quiet 33 < 180: locked | **1 star**: 2 -> 1, then 1 -> 0 10 s later; logging out cost him the PETTY lump | 2 single drops |
| E8 cold-start server, admin chase | `/glw wanted add 3`, no crimes, sealed room. | crimes = 0 so never PETTY; not a rampage; HUNKER with momentum | 3->2 after 30 s, 2->1 after 15 s, 1->0 after 5.6 s; nothing is learned (start cause ADMIN) | 30 + 20 + 10 s |

After the server has learned (warm server):

| # | Situation | Arithmetic | Effect |
|---|---|---|---|
| W1 learned typical length | 40 getaways at peak 3, running median of their contact time settled at 70 s | `T(3) = (70 x 40 + 90 x 20) / 60 = 76.7` (inside the 45..180 clamp) | cold-trail line at 3 stars moves from 180 s to 153 s |
| W2 habitual escaper | busy server (`S[3]` holds 100 chases at 0.55), he escaped his last 6 chases, all peaking at 3; his own escapes still nudge `p` up as section 3.5 says | `n = (1 - 0.9^6) / 0.1 = 4.69`; replaying 3.5 exactly gives `delta` 0.075, 0.123, 0.157, 0.181, 0.199, **0.213** after 1-6 chases (0.218 if `p` stayed at 0.55) | known face from the 6th. E1 rerun: no PETTY; 2->1 after 20 x 1.17 = 23.4 s, 1->0 after 10 x 0.75 x 1.17 = 8.8 s; card "They know your face, one star at a time". A lone tester (no other rows) tops out at 0.206 near his 10th escape. At peak 5 (`p` 0.20) two escapes already give 0.215: rare getaways are strong evidence, by design |
| W3 habitual loser | busy server, arrested in his last 6 chases at peak 3 | `delta` = -0.26, habit 0.792 | E2 rerun: 23.8 s, 11.9 s, 4.5 s; never more stars per drop, only shorter waits |
| W4 one arrest | stranger, arrested at peak 3 | `n = 1, actual 0, expected 0.55`, `delta = -0.55 / 6 = -0.09`, habit 0.93 | one result barely moves a player; a streak does |
| W5 farming level 1 | escapes from 1-star chases only (p = 0.90) | `delta <= 0.1 x n / (n + 5) < 0.067` | never a known face from level 1 alone; the memory repeat-offender rule (3 crime chases in 30 min) covers that farm |
| W6 farming the loser side | one crime for 1 star, wait 30 s, jump in lava, repeat | DEATH is not counted, and a 1-star arrest or bribe is not counted either | nothing learned. Before this rule a replay gave habit 0.88, 0.81, 0.76 after 1-3 such chases, a cheap 25-30 % cut on every later timer |

---

## 4. Config additions

### 4.1 `ECR/wanted.yml`, under `Wanted.Evasion`

Replace the `Drop_Mode` comment at `wanted.yml:80` and add the `Auto` block after `Outside_Zone_Speed` (`:97`).
Block style, 3-space indents as in the file, every key commented. A file without the block reads as these defaults with
no warning.

```yaml
      # What a completed evasion takes: ONE_STAR (one star), ALL_STARS (every star) or AUTO (the cops judge how the
      # chase went and take one star, a share of them or all; see the Auto block below)
      Drop_Mode: ONE_STAR
```

```yaml
      # Read only while Drop_Mode is AUTO. First match wins when the hide timer runs out: a hot chase loses one star;
      # a small chase or a long quiet one loses every star; leaving the search zone loses half your stars; anything
      # else loses one star, and the next timer is shorter
      Auto:
         # Seconds from the first crime of a chase that count as its opening
         Opening_Seconds: 30
         # Crimes in the opening that make the chase a rampage
         Rampage_Crimes: 4
         # Peak stars that make a chase a rampage however it started (killing a cop always does)
         Rampage_Peak_Level: 4
         # A rampage, or a chase you logged out of, loses one star at a time until this many seconds pass with no
         # new crime and no new star
         Lock_Cool_Seconds: 180
         # Times the cops may spot you again after losing you before small chases and clean breaks stop paying out
         Respot_Limit: 4
         # A small chase: the cops let it go and every star drops at once
         Petty:
            # Most crimes a chase may hold to count as small (a chase with no crime never counts)
            Max_Crimes: 2
            # Highest star level a chase may reach to count as small (keep it below Rampage_Peak_Level)
            Max_Peak_Level: 2
         # A long chase that went quiet: every star drops at once
         Cold_Trail:
            # A fresh server's guess, in seconds, of how long the cops stay on your tail before they lose you for
            # good, for a chase that peaks at star 1, 2, 3, ... With Learning on, the server replaces it with what it
            # measures, never below half or above double these numbers
            Typical_Seconds:
               - 30
               - 60
               - 90
               - 120
               - 150
            # The whole chase must have lasted this many times that typical time
            Ratio: 2.0
            # Seconds with no new crime and no new star
            Quiet_Seconds: 90
         # You ran out of the search zone instead of sitting in it. A teleport of more than 32 blocks or into another
         # world (a command, a plugin or a portal; not an ender pearl) during the search never counts as a clean break
         Clean_Break:
            # Share of the search time you must spend outside the zone (0 to 1)
            Outside_Ratio: 0.5
            # Share of your stars that drop (0 to 1), rounded up, at least one star
            Drop_Fraction: 0.5
         # After a star falls and you are still hidden, the next timer is shorter
         Momentum:
            # Each step is this share of the previous timer (1.0 = no speed-up)
            Step_Speed: 0.75
            # The same after a narrow escape. A chase that is still hot (a rampage, or you logged out) gets no
            # speed-up at all, narrow or not
            Narrow_Step_Speed: 0.5
            # Seconds a cop must have kept you in sight before you broke away for it to count as a narrow escape
            Narrow_Seen_Seconds: 20
            # No timer ever shrinks below this share of Seconds_To_Drop, whatever momentum and the learned habit say
            Floor: 0.4
         # Chases with at least one crime that you ended recently; at this many the cops stop going easy (0 = off)
         Repeat_Chases: 3
         # How far back those chases count, in minutes (kept in memory, forgotten at restart)
         Repeat_Window_Minutes: 30
         # Learning from finished chases (two small tables in the plugin database)
         Learning:
            # false = nothing is stored or read; AUTO uses Typical_Seconds and treats everyone as a stranger
            Enable: true
            # How often chases that peak at 1, 2, 3, ... stars end in a getaway on a fresh server (0 to 1)
            Escape_Rate:
               - 0.90
               - 0.75
               - 0.55
               - 0.35
               - 0.20
            # Chases of evidence a player needs before his habit counts fully (bigger = slower to judge)
            Prior_Chases: 5
            # How much an older chase still counts after each newer one (0.90 = roughly the last 10 chases)
            Decay_Per_Chase: 0.90
            # A player whose escape habit reaches this is a known face: no small-fry or clean-break lumps
            Habitual_Escaper_Delta: 0.20
            # How strongly the habit stretches (always escapes) or shortens (always caught) the timer
            Habit_Time_Strength: 0.8
            # The habit on its own never shortens a timer below this share; with momentum on top, Momentum.Floor is
            # the overall limit
            Min_Time_Factor: 0.6
            # No timer is ever stretched above this share of Seconds_To_Drop
            Max_Time_Factor: 1.6
            # Chases shorter than this many seconds are not learned from
            Min_Chase_Seconds: 30
            # A player's chases that end closer together than this many seconds are not learned from
            Min_Seconds_Between_Outcomes: 180
            # Habit rows of players not seen in a chase for this many days, and star-level rows not updated for this
            # many days, are deleted at startup
            Forget_After_Days: 90
```

29 keys (35 nodes counting the six block headers). Cut on purpose (ponytail): B's half-life, server decay, median step, fair share and server prior (constants);
A's per-crime point weights (the rampage rule covers violence); C's `Hot_Need_Multiplier` and per-ending fractions.

### 4.2 Validation (`ChaseConfig`; the report is only logged, `ChaseConfigLoader.java:78`, so the server never refuses to start)

Two kinds of check, and they report differently.

**(a) Single-key ranges use the house pattern `.asInt()/.asDouble().min(x).max(y).orDefault(def)`**, as `heat(...)`
already does (`ChaseConfig.java:56-65`). Keystone's `NodeReader` then reports **ERROR `config.range`** and the key takes
its **default**; it never clamps and never warns (`keystone-persistence/.../config/NodeReader.java:446-460`, `:496-510`).

| Key | Range |
|---|---|
| `Opening_Seconds`, `Lock_Cool_Seconds`, `Respot_Limit`, `Quiet_Seconds`, `Narrow_Seen_Seconds`, `Repeat_Chases`, `Repeat_Window_Minutes`, `Min_Chase_Seconds`, `Min_Seconds_Between_Outcomes` | `.min(0)` |
| `Rampage_Crimes`, `Rampage_Peak_Level`, `Petty.Max_Crimes`, `Petty.Max_Peak_Level`, `Prior_Chases`, `Forget_After_Days` | `.min(1)` |
| `Ratio` | `.min(0.01)` |
| `Outside_Ratio`, `Drop_Fraction` | `.min(0).max(1)` |
| `Step_Speed`, `Narrow_Step_Speed`, `Floor` | `.min(0.01).max(1)` (above 1 would slow steps down) |
| `Decay_Per_Chase` | `.min(0.5).max(1)` |
| `Habitual_Escaper_Delta` | `.min(0.05).max(1)` |
| `Habit_Time_Strength` | `.min(0).max(2)` |
| `Min_Time_Factor` / `Max_Time_Factor` | `.min(0.1).max(1)` / `.min(1).max(4)` |

**(b) Hand-written checks in the new private `ChaseConfig.auto(...)`**, each a
`report.add(Severity.WARNING, at, path, message, code)` call like the `Drop_Mode` warning (`ChaseConfig.java:92-94`), then
the fix shown.

| Check | Fix |
|---|---|
| `Drop_Mode` not ONE_STAR / ALL_STARS / AUTO (any case) | `config.enum`, ONE_STAR; message now `(ONE_STAR, ALL_STARS or AUTO)` |
| a `Typical_Seconds` entry `< 5` | `config.range`, use the previous entry (the first falls back to its default). An empty list is the default silently; a short list repeats its last entry (the `EvasionSettings.at` clamp, `EvasionSettings.java:34-36`) |
| an `Escape_Rate` entry outside `0..1` | `config.range`, clamped to `0..1` |
| `Narrow_Step_Speed > Step_Speed` | `config.conflict`, set to `Step_Speed` |
| `Petty.Max_Peak_Level >= Rampage_Peak_Level` | `config.conflict`, kept; message "PETTY and rampage overlap; the rampage lock wins" |

**(c) Cross-block notes in `ChaseConfig.parse(...)`**, the only place that sees both `Heat` and `Evasion` (`:27-37`):
INFO "AUTO does nothing while evasion is off" (`Drop_Mode: AUTO` with `Evasion.Enable: false`) and INFO "no heat ledger:
every chase has 0 crimes, so no small-fry or rampage rules" (`Drop_Mode: AUTO` with `Heat.Enable: false`). An `Auto` block
under ONE_STAR / ALL_STARS is silent.

### 4.3 `ECR/wanted_messages.yml`

Add under `Hud.Bar` (after `Evaded`, `:17`) and `Hud.Card` (after `Drop_Other`, `:35`). `%count%` is already in the
header placeholder list (`:6`); update the header sentence to say it also fills the star-drop lines.

```yaml
      # Several stars were just lost at once; %count% is how many
      Evaded_Many: "&a%stars% &2&l-%count% STARS"
```

```yaml
      # AUTO: a small chase, every star dropped at once; %count% is how many
      Drop_Petty: "&aSmall fry, they dropped the case &7(-%count%)"
      # AUTO: a long chase that went quiet, every star dropped at once
      Drop_Cold_Trail: "&aThe trail went stone cold &7(-%count%)"
      # AUTO: you left the search area and lost a share of your stars
      Drop_Clean_Break: "&aClean break, you left the area &7(-%count%)"
      # AUTO: only one star, because the chase is still hot (a rampage, or you logged out)
      Drop_Still_Hot: "&eStill hot, one star at a time"
      # AUTO: only one star, because the cops know you (you keep getting away)
      Drop_Known_Face: "&eThey know your face, one star at a time"
      # AUTO: one star after a narrow escape; the next timer is quicker
      Drop_Narrow: "&aThat was close, they are losing you"
```

A plain HUNKER_DOWN drop keeps the existing `Drop_Evasion` line, so ONE_STAR, ALL_STARS and AUTO's default look the same.

---

## 5. Code changes by file

All in cops-n-crooks. `gangland-api`, `gangland-core`, `gangland-impl` and the other modules are untouched.

### New classes

| File | What |
|---|---|
| `EC/wanted/config/AutoSettings.java` | record + nested records `Petty`, `ColdTrail(List<Integer> typicalSeconds, double ratio, int quietSeconds)`, `CleanBreak`, `Momentum`, `Learning`; `DEFAULT` = section 4.1; `typicalFor(level)` and `escapeRateFor(level)` with the `EvasionSettings.at` clamp. |
| `EC/wanted/evasion/AutoDrop.java` | the shared value types, so the planner, the arcs and the learner can be written in parallel (task T2): a final holder class with `record ChaseView(int crimes, int opening, boolean copKilled, int peak, long chaseMs, long quietMs, int quits, int respots, int recentEnds)`, `record SpellView(long insideMs, long outsideMs, int steps, boolean narrow, boolean teleported)`, `record Learned(double delta, double typicalSeconds)` with `Learned.cold(AutoSettings, int peak)` = `(0, Typical_Seconds[peak])`, `record DropPlan(int stars, Ending ending, String reason)`, `enum Ending {STILL_HOT, PETTY, COLD_TRAIL, CLEAN_BREAK, HUNKER_DOWN}`. |
| `EC/wanted/evasion/AutoDropPlanner.java` | pure, no Bukkit. `static DropPlan plan(AutoSettings s, ChaseView c, SpellView sp, Learned l, int level)` (section 3.3) and `static double factor(AutoSettings s, ChaseView c, SpellView sp, Learned l)` (section 3.4). |
| `EC/wanted/evasion/ChaseArc.java` | mutable per-player facts that survive SEEN and quit: `startedAt`, `startCause`, `lastHotAt`, `lastLostAt`, `peak`, `respots`, `searched`, `quits`, `offlineAt`, `pending` (the `DropPlan` the HUD has not shown yet). |
| `EC/wanted/evasion/ChaseArcs.java` | bean, injected `LongSupplier`. `Map<UUID, ChaseArc>`, `Map<UUID, ArrayDeque<Long>> recent` (end times of chases with at least one crime, capped at 8, pruned by window). Methods: `has(id)`; `start(id, cause, level)` (seeds `peak = level` and `lastHotAt = now`; cause RESTORE also sets `quits = 1`); `restore` (shifts every stamp forward by `now - offlineAt`); `hot`; `peak`; `searchStarted(id)` (sets `searched`); `seen(id)` (when `searched`: `respots++`, clears `searched`); `lost(id)` (stamps `lastLostAt`); `quit`; `end` (appends to `recent` when the chase had a crime, removes the arc); `view(...)`; `stashPending`; `takePending`; `prune` (drops arcs of players offline over 30 min, run on start/join; a pruned player who comes back gets the RESTORE start above). |
| `EC/listener/wanted/ChaseArcListener.java` | `@ListenerHandler`, constructor `(ChaseArcs, ChaseLearner, HeatLedger, ChaseConfigLoader)`. `WantedStartEvent` MONITOR: cause RESTORE and an arc exists, `restore`; otherwise `start(id, cause, event.getWantedLevel())`. `WantedLevelChangeEvent` MONITOR ignoreCancelled: ignored when the cause is RESTORE (the 0->N restore change fires before the start event and would put `lastHotAt` in the future after the shift); otherwise `peak`, and a raise also `hot`. `CrimeCommittedEvent` MONITOR ignoreCancelled: `hot` only when `Heat.Enable` and `heat.weightOf(crime) > 0`, the crimes the ledger keeps. `WantedEndEvent` **HIGH** (before HeatListener's MONITOR clear): build the `ChaseRecord`, `learner.record(...)`, `arcs.end(...)`. `PlayerQuitEvent` MONITOR: `quit` when `arcs.has(id)`. It must not ask `UserManager`: `RemoveAccountListener.onPlayerLeave` (HIGHEST) has already removed the user (`gangland-impl/.../listener/player/RemoveAccountListener.java:58-69`). |
| `EC/wanted/learn/ChaseLearner.java` | owns the two caches (`ConcurrentHashMap` of immutable records, replaced whole so the async autosave never sees half an update) and the two repositories. `record(ChaseRecord, long now)` (section 3.5), `delta(UUID)`, `typicalSeconds(int peak, AutoSettings)`. Implements `BeanLifecycle` (`JailExitService` shape, `EC/jail/JailExitService.java:17-27`) but loads **only when `firstLoad`** (`JailExitService` reloads every time, which here would throw away learning not yet autosaved on each `/glw reload`); at that load it deletes habit rows whose `last_at` and level rows whose `updated_at` are older than `Forget_After_Days`. `setDataSupplier(cache::values)` in the constructor (feedback_repository_data_supplier). `Learning.Enable: false` (read live, so a reload can flip it) short-circuits every method to cold values and writes nothing; the caches stay in memory. |
| `EC/wanted/learn/ChaseHabit.java`, `ChaseLevelStat.java` | records, one per table row (section 6). |
| `EC/wanted/learn/ChaseRecord.java` | `record ChaseRecord(UUID player, WantedCause startCause, WantedCause endCause, int peak, long chaseMs, long contactMs, long endedAt)`, built by `ChaseArcListener` from the arc; `chaseMs` and `contactMs` already have offline time removed. |
| `EC/database/ChaseHabitTable.java`, `ChaseHabitRepository.java`, `ChaseLevelStatTable.java`, `ChaseLevelStatRepository.java` | `@Repository(ChaseHabit.class)` / `@Repository(ChaseLevelStat.class)`, constructor `(JavaPlugin, DatabaseHandler, DatabaseBackend)`, shape of `JailExitRepository` (`EC/database/JailExitRepository.java:20-87`); found by the existing module package scan. |

### Changed code

| File:line | Change |
|---|---|
| `EC/wanted/config/DropMode.java:3-7` | add `AUTO`; update the javadoc. |
| `EC/wanted/config/EvasionSettings.java:16-21` | 7th component `AutoSettings auto`; keep a 6-argument constructor delegating `AutoSettings.DEFAULT` (callers `ChaseConfig.java:76`, `EvasionClockTest.java:212` and `:268`); `DEFAULT` passes `AutoSettings.DEFAULT`; add `@param auto`. |
| `EC/wanted/config/ChaseConfig.java:69-81` | `evasion(...)` also parses `auto(block(n, "Auto", report), report)` (helper `block` at `:39`), always, so a reload that flips the mode needs no second parse. New private `auto(...)` with section 4.2 (a) and (b). |
| `EC/wanted/config/ChaseConfig.java:27-37` | `parse(...)` adds the two INFO notes of section 4.2 (c). |
| `EC/wanted/config/ChaseConfig.java:92-94` | warning text `(ONE_STAR, ALL_STARS or AUTO)`. |
| `EC/wanted/evasion/EvasionClock.java:42-51` | `Track` gains `long needMs`, `int steps`, `boolean narrow`, `long seenSince`, `long insideMs`, `long outsideMs`, `boolean teleported`. |
| `EvasionClock.java:62-71` | constructor gains `HeatLedger ledger`, `ChaseArcs arcs`, `ChaseLearner learner`. |
| `EvasionClock.java:116-123` (SEEN) | whenever a new SEEN track is made (also when `track == null` because `clear` dropped it while the squad was RETURNING, `:99-101`): `arcs.seen(id)`, which counts a respot only after a search; the new track gets `seenSince = now`. |
| `EvasionClock.java:129-133` (SEEN to SEARCHING) | before `startSearch`: `track.narrow = now - track.seenSince >= Narrow_Seen_Seconds * 1000` (only when coming from SEEN), `arcs.lost(id)` and `arcs.searchStarted(id)`. |
| `EvasionClock.java:140-145` | read `boolean raised = level != track.level` **before** `:142` overwrites `track.level`. Split `speed(...)` (`:189-196`) into `inside(player, track)` plus the speed; add `dt` to `insideMs` or `outsideMs`. When `raised` and the mode is AUTO, recompute `needMs`. |
| `EvasionClock.java:147-149` | `long need = auto && track.needMs > 0 ? track.needMs : cfg.secondsToDropFor(level) * 1000L;` (the fallback covers a reload that flips the mode mid-search) and `int count = auto ? autoCount(...) : (cfg.dropMode() == DropMode.ALL_STARS ? level : 1);` where `autoCount` builds the views, calls the planner, writes the debug line, `arcs.stashPending(id, plan)` and returns `plan.stars()`. The whole AUTO branch is in a try/catch that logs once per player and returns 1 (ONE_STAR), so a planner bug never freezes a chase. ONE_STAR/ALL_STARS keep their exact expression. |
| `EvasionClock.java:150-159` | after a drop with `dropped > 0` that leaves him wanted: `track.steps++`, `needMs` recomputed for the new level. The chase-ending drop returns at `:152` as today; nothing after it is needed, because the learner reads no ending. A cancelled level change (`dropped == 0`) changes nothing, as today. |
| `EvasionClock.java:166-176` | `startSearch` computes `track.needMs` (AUTO) and passes it to `fireCountdown`. |
| new `EvasionClock.teleported(Player)` | sets `teleported` on the player's track, if any. |
| `EC/listener/wanted/EvasionListener.java` | new handler `onTeleport(PlayerTeleportEvent)` MONITOR ignoreCancelled: when the cause is not ENDER_PEARL, CHORUS_FRUIT or UNKNOWN and the jump is over 32 blocks or into another world, `clock.teleported(player)`. `clear` still only stops tracking; the chase end belongs to `ChaseArcListener`. (32 is a constant: a car dismount, `gangland-gadget/.../CarDismountListener.java:63`, moves the player a block or two.) |
| `EC/config/EvasionModuleConfig.java:28-33` | beans `ChaseArcs chaseArcs()` and `ChaseLearner chaseLearner(ChaseConfigLoader, RepositoryRegistry)` (getRepository as at `CopsNCrooksModuleConfig.java:171-174`; it throws `IllegalStateException` when the `@Repository` class is missing, Keystone `RepositoryRegistry.java:169-175`, so this wiring needs T6); `evasionClock(...)` takes `HeatLedger`, `ChaseArcs`, `ChaseLearner` (the bean parameters are also the ordering edges). |
| `EC/wanted/WantedMessages.java:19-36` | keys `BAR_EVADED_MANY`, `CARD_DROP_PETTY`, `CARD_DROP_COLD_TRAIL`, `CARD_DROP_CLEAN_BREAK`, `CARD_DROP_STILL_HOT`, `CARD_DROP_KNOWN_FACE`, `CARD_DROP_NARROW`, each with the shipped line as fallback. |
| `EC/wanted/hud/StarCard.java:35-43` | overload `dropCard(messages, cause, @Nullable DropPlan pending, int lost)`: EVASION with a pending plan maps the ending (and reason) to its key and fills `%count%`; otherwise the old method. |
| `EC/wanted/hud/StarCard.java:46-53` | overload `barTitle(..., int lost)`: EVADED with `lost >= 2` uses `BAR_EVADED_MANY`. |
| `EC/wanted/hud/WantedHud.java:84-97, :136, :240-250` | `Entry.lost`; new `lost(Player, int)`; `render` passes it to `barTitle`; cleared when the EVADED flash ends. |
| `EC/listener/wanted/WantedHudListener.java:52-63, :80-81` | constructor takes `ChaseArcs`; for a drop with cause EVASION: `pending = arcs.takePending(id)`, `lost = old - new`, `StarCard.dropCard(messages, cause, pending, lost)`, `hud.lost(player, lost)`. The pending plan is stashed before `stars.drop` and read inside it (both synchronous on the main thread); a test pins the order. |
| `ECR/wanted.yml:80-81, after :97` | section 4.1. |
| `ECR/wanted_messages.yml:6-7, after :17, after :35` | section 4.3. |

Debug line, one per AUTO decision (`@CustomLog`, `log.debug`, read by the acceptance verdict):

```
AUTO drop {player} level={old}->{new} ending={ending} reason={reason} crimes={n} opening={n} peak={n} chase={s}s
quiet={s}s quits={n} respots={n} outside={ratio} teleported={b} steps={n} narrow={b} delta={d} typical={s}s factor={f}
```

Optional, not in this plan's DAG: `/glw wanted auto [player]` printing the same line (decision D7).

---

## 6. Persistence and schema

Two new tables, module-owned, created by the backend diff engine at startup. No existing table changes; dropping AUTO
leaves two harmless tables.

`chase_habit` (one row per player who finished a counted chase):

| Column | Type | Meaning |
|---|---|---|
| `player_uuid` | String, PK | player |
| `n` | Double | decayed count of counted chases (at most 10 at decay 0.90) |
| `actual` | Double | decayed sum of outcomes |
| `expected` | Double | decayed sum of the server escape rate for each of his chases |
| `last_at` | Long | millis of his last counted chase (rate limit and `Forget_After_Days`) |

`chase_level_stat` (at most one row per peak level; a missing row means cold start):

| Column | Type | Meaning |
|---|---|---|
| `level` | Integer, PK | chase peak level |
| `n` | Double | decayed weighted chase count (at most 100) |
| `escaped` | Double | decayed weighted sum of outcomes |
| `typical_s` | Double | running median of contact time (chase start to the last loss of sight) over getaways, seconds |
| `typical_count` | Double | samples behind it, capped at 100 |
| `updated_at` | Long | millis (also drives `Forget_After_Days` for level rows) |

Autosave: `PeriodicalUpdates` upserts `cache.values()` of both through the data suppliers; shutdown flushes. Full
snapshot per autosave: fine up to tens of thousands of rows (`ponytail:` upgrade path = a dirty set). Two servers on one
MySQL overwrite each other's `chase_level_stat` rows (decision D10). Chase arcs and the repeat-offender list stay in
memory. A restart mid-chase, or a rejoin after the arc was pruned (30 min offline), restores the level with cause
RESTORE and no arc; the start handler then makes a fresh arc with `peak` = the restored level and `quits = 1`, so the
chase is **locked** (one star at a time, no momentum) until `Lock_Cool_Seconds` pass with no new crime. After a restart
the heat ledger is empty too (it is memory only), so PETTY cannot fire; CLEAN_BREAK can, once the lock has lifted. A
RESTORE-started chase is never learned from.

---

## 7. API, events and versions

- **gangland-api: no change. `GanglandApi.VERSION` stays "2.2"; cops-n-crooks `Host_Api` stays 2.2.** Only the
  cops-n-crooks module version moves (0.15.2).
- No new Bukkit event. The HUD reads the pending `DropPlan` from the `ChaseArcs` bean. `WantedEvasionStateEvent`,
  `WantedCause`, `WantedStars`, `Messages`, `Settings` are unchanged.
- Upgrade path, when a second consumer appears (0.16 criminal record or 0.18 getaway XP, if outside this module): add
  `WantedEvasionDropEvent(player, levelBefore, stars, ending id String, reason id String, peak, chaseSeconds, crimes)`
  to gangland-api in the **2.3** bump that 0.16 already takes for `RegionProvider` (SPEC-0.15.md api cadence: 2.3 =
  0.16), fired by the clock right where `stashPending` is called. Additive, one bump, no clash (decision D4).

---

## 8. Task breakdown (DAG)

| Id | Task | Files | Depends on | Size | Model |
|---|---|---|---|---|---|
Every task records its own red run (the failing assertion, or the compile error for a new type) before it goes green;
T11 only collects them.

| Id | Task | Files | Depends on | Size | Model |
|---|---|---|---|---|---|
| TR | Release plumbing: branch `0.15.2` from `0.15.1`, root `pom.xml:57` `<revision>0.15.2</revision>` (`module.yml` takes `${project.version}`), no api bump | `pom.xml` | - | S | haiku |
| T0 | Harness path fix: extract and edit `copsncrooks/wanted.yml`, not `npc/wanted.yml` | `acceptance/prep-cnc015.sh:63-64`, `acceptance/README.md:37` | - | S | haiku |
| T1 | `DropMode.AUTO`, warning text, update the unknown-mode test | `DropMode.java`, `ChaseConfig.java:92-94`, `ChaseConfigTest.java:132-139` | TR | S | haiku |
| T2 | `AutoSettings`, the shared types in `AutoDrop.java`, `EvasionSettings` 7th component, `ChaseConfig.auto` parse + section 4.2 (a)(b)(c), shipped YAML block | `AutoSettings.java`, `AutoDrop.java`, `EvasionSettings.java`, `ChaseConfig.java:27-37, 69-81`, `ECR/wanted.yml`, `ChaseConfigTest` | T1 | M | sonnet |
| T3 | `AutoDropPlanner` + table test of section 3.6 | `AutoDropPlanner.java`, `AutoDropPlannerTest` | T2 | M | opus |
| T4 | `ChaseArc`, `ChaseArcs`, `ChaseArcListener` | 3 new files + tests | T2 | M | sonnet |
| T5 | `ChaseLearner` maths + caches + `ChaseRecord` (no DB yet; repositories behind `IRepository<T>` mocks) | `wanted/learn/*` + `ChaseLearnerTest` | T2 | M | opus |
| T6 | Tables, repositories, learner load (first load only) / prune / save wiring | `EC/database/Chase*`, `ChaseLearner` lifecycle, repository SPI tests | T5 | M | sonnet |
| T7 | `EvasionClock` edits, `EvasionListener.onTeleport`, `EvasionModuleConfig` wiring | `EvasionClock.java`, `EvasionListener.java`, `EvasionModuleConfig.java`, `EvasionClockTest` | T3, T4, T5, T6 | L | opus |
| T8 | HUD: messages keys, YAML lines, `StarCard` overloads, `WantedHud.lost`, `WantedHudListener` pending read | `WantedMessages.java`, `wanted_messages.yml`, `StarCard.java`, `WantedHud.java`, `WantedHudListener.java` + tests | T4 | M | sonnet |
| T9 | YAML lint (gangland-yaml-review on both files: block style, keys, dead/missing keys against the 29 keys of `AutoSettings.DEFAULT`); docs: `documentation/developer/configuration.md:485` (Drop_Mode row + the `Auto` keys), `documentation/features/wanted-bounty.md:42-49` (evasion paragraph: endings, learning, warm-up, and that an upgraded 0.15.1 server keeps its old `wanted.yml`, so it has no `Auto` block and reads the defaults until an admin copies it in), `documentation/features/cops-n-crooks.md:220-225`, the four AUTO card checks in `documentation/tests/features/wanted-bounty.md` (near `:58`), new `documentation/v0.15.2/CHANGELOG.md` + `CHANGELOG.bbcode.txt` (precedent `v0.15.0/`), README index row in `documentation/README.md:13` (0.15.2 Current). `migration-0.15.0.md` is historical: leave it | docs, changelog | T2, T8 | S | haiku |
| T10 | Acceptance: profile `auto` (three `yset.js` keys on `copsncrooks/wanted.yml`), scenarios A1-A9 in `gen-cnc015.js`, verdict rows in `cnc-verdict.js`, `run-all-cnc015.sh`, the A5/A6 fixtures named in section 9 | acceptance folder | T0, T7, T8 | M | sonnet |
| T11 | Gate: `mvn clean install -DskipTests` then `mvn test` (via PowerShell / ctx_execute), collect each task's red run, review against this plan, `graphify update . --force`; docket: add the P3 row of D9 as id 179 in `bug-docket-2026-09-06/triage/new-findings.txt` (`~~`-separated like the 178 rows above it), run `python build_docket.py`, republish the Gangland docket artifact, mirror it into `cross-docket-2026-09-10` (rebuild + republish) | - | T6, T7, T8, T9, T10 | S | opus |

13 tasks. Parallel lanes: {TR, T0} at once; T1; T2; then {T3, T4, T5} at once (they share only the `AutoDrop` types
T2 made); then {T6, T8} (T6 needs T5; T8 needs T4); then T7 (needs T3, T4, T5, T6: its bean wiring calls
`getRepository`, which throws without T6's `@Repository` classes); then {T9, T10}; then T11. At most two Opus tasks overlap
(T3 and T5; T7 runs alone), per the agent budget rule. Steps up to T7 can merge as a hidden feature (default stays
ONE_STAR) before the HUD lands.

---

## 9. Tests

Each test below is **red** (a new type, or new behaviour on an existing class: name the failing assertion when you run it
against the pre-change code) unless it is marked **pin**, a regression guard that is green before and after (for
example the existing `ChaseConfigTest` "shipped wanted.yml parses to exactly the in-code DEFAULT" at
`ChaseConfigTest.java:44-48`, and the ONE_STAR / ALL_STARS clock tests). JUnit 5 + Mockito in `ECT`; database tests follow
`documentation/TESTING.md` and the pattern of `ECT/database/DetainmentRepositorySpiTest.java`. The cops-n-crooks tests
have no event dispatcher (no MockBukkit, no `SimplePluginManager`), so event order is pinned the way
`CopListenerDeathTest.java:142-144` does it: read `EventHandler.priority()` by reflection, then call the handlers directly
in the order Bukkit would.

Unit:

- `AutoDropPlannerTest` (pure, parameterised): rows E1-E8 and W2-W3 of section 3.6 (stars, ending, factor within
  0.001); each rampage trigger alone (4 opening crimes, peak 4, Kill_Cop); lock lifts exactly at `quiet ==
  Lock_Cool_Seconds`; logout lock; PETTY with 0 crimes is never petty; PETTY with 3 crimes falls through; rule order
  (PETTY beats COLD_TRAIL, STILL_HOT beats both); COLD_TRAIL boundary on `Ratio * T` and `Quiet_Seconds`; CLEAN_BREAK
  `ceil` at levels 1-5 and fractions 0.0 / 1.0; `teleported` blocks CLEAN_BREAK only; `Respot_Limit` and `knownFace`
  switch off PETTY and CLEAN_BREAK but not COLD_TRAIL; stars never 0, never above level; momentum power, `Floor`;
  **narrow gives no speed-up under a lock**; habit clamps both ends; **invariant: with `Learned.cold`, a chase matching
  no lump rule gives stars 1 and factor `max(0.4, 0.75^steps)` (1.0 when locked)**.
- `ChaseLearnerTest`: W1 (`T(3) = 76.7`); W2 with the server row in the loop and `S[3]` seeded with 100 chases at 0.55
  (`delta` 0.199 after 5, 0.213 after 6) plus the lone-tester row (top 0.206); W3; W4; W5 bound; W6 (DEATH and 1-star
  ARREST/BRIBE ends learn nothing); a 2-star ARREST does count; `n` stays under 10 over 1000 chases; `typical_s` moves
  from every EVASION end by its `contactMs`, whatever the last ending, and a long hide after the last loss of sight does
  not move it; clamp to half/double `Typical_Seconds`; guards (`Min_Chase_Seconds`, `Min_Seconds_Between_Outcomes`,
  ADMIN/SIGN/RESTORE/UNKNOWN ends, non-CRIME starts); fair-share weight for a heavy player; replay determinism;
  `Learning.Enable: false` returns cold values and writes nothing; no learning while the mode is not AUTO; a second
  `onInitialize(false)` (reload) keeps unsaved cache rows; first load deletes habit and level rows past
  `Forget_After_Days`.
- `ChaseArcsTest` (injected clock): `start` at level 4 seeds `peak` 4 (so a `/glw wanted add 4` chase is a rampage) and
  `lastHotAt = now`; peak, hot; `seen` counts a respot only after `searchStarted`, including when the old track was
  dropped; `restore` shifts stamps by the offline gap so `chase` and `quiet` exclude it; `quit` sets the lock; a RESTORE
  start with no arc gives `quits = 1` and `peak` = level; `end` appends to `recent` only with a crime; `recent` capped at
  8 and aged by the window; `prune`; admin chase leaves no trace in `recent`.
- `ChaseArcListenerTest`: by reflection, the end handler is HIGH and `HeatListener.onChaseEnd` is MONITOR; called
  directly, the end handler reads the crimes from a real `HeatLedger` before `HeatListener` clears them; a RESTORE
  level change is ignored (fire the 0->N RESTORE change, then the RESTORE start, then check `quiet >= 0`); a crime with
  weight 0 or with heat off does not reset `quiet`; quit with an arc counts even when the online `UserManager` returns
  null for the player (as after `RemoveAccountListener`); quit with no arc does nothing.
- `ChaseHabitRepositorySpiTest`, `ChaseLevelStatRepositorySpiTest`: round trip, empty load, prune past
  `Forget_After_Days`.
- `ChaseConfigTest`: AUTO parses (and `auto`); the block round-trips; a file without the block gives `DEFAULT` and an
  empty report (pin once the block ships); each row of section 4.2 (a) gives ERROR `config.range` and the default; each
  row of (b) gives WARNING with its code and fix; each note of (c) gives INFO; the unknown-mode message names AUTO; the
  block is silent under ONE_STAR.
- `EvasionClockTest` (setUp `:73-112`, fake clock, captured events, mocked `WantedStars`; add mocked `HeatLedger`,
  real `ChaseArcs`, `ChaseLearner` stub): AUTO E1 drops 2 at once and stashes PETTY before `stars.drop`; AUTO E2 needs
  30 s then 15 s then 6 s (`fireCountdown` values); SEEN resets steps, outside time and `teleported` but not the arc;
  a squad that went RETURNING and comes back counts one respot; narrow uses 0.5 when not locked; locked keeps ONE_STAR
  timing, narrow or not; level raised mid-search recomputes `needMs` (the comparison runs before `track.level` is
  overwritten); **a reload that flips ONE_STAR to AUTO mid-search drops nothing on the next tick** (`needMs` 0 falls back
  to `Seconds_To_Drop`); `teleported(player)` stops a CLEAN_BREAK; cancelled drop leaves steps alone; planner exception
  drops 1 and logs once; ONE_STAR / ALL_STARS tests unchanged (pin) and no arc or ledger reads in those modes.
- `EvasionListenerTest`: a 40-block COMMAND teleport and a world change mark the track; a 2-block PLUGIN teleport (car
  dismount) and an ENDER_PEARL teleport do not.
- `StarCardTest`, `WantedHudListenerTest`: each ending picks its card with `%count%`; no pending plan gives today's
  `Drop_Evasion`; `Evaded_Many` at `lost >= 2`; pending consumed once and never used for DECAY/ARREST; old messages
  file without the new keys reads through the fallbacks.

Acceptance (sandbox only, never the live Test Server). New profile `auto` in `prep-cnc015.sh`: the 0.15.2 jar ships
the `Auto` block, so `yset.js` sets three existing keys: `Wanted.Evasion.Drop_Mode AUTO`,
`Wanted.Evasion.Auto.Learning.Min_Chase_Seconds 5`, `Wanted.Evasion.Auto.Learning.Min_Seconds_Between_Outcomes 5`.
Every other number stays shipped, so the lock (180 s) and the cold-trail line (180 s at 3 stars) cannot fire inside the
rows below and change their result.

| Row | Steps | Pass |
|---|---|---|
| A1 auto-hunker | `/glw wanted add 3`, seal Runner in the N1 stone room | three `decreased` lines, gaps about 30 / 15 / 6 s (+-3), debug `ending=HUNKER_DOWN` x3, no ERROR |
| A2 auto-petty | one console kill (`damage Target 1000 minecraft:player_attack by Runner`, N4b pattern) then `/glw wanted add 2`, seal | one drop 2 -> 0, debug `ending=PETTY` (start cause ADMIN, so nothing is learned) |
| A3 auto-rampage-lock | `/glw wanted add 4` plus one kill, seal at once | first drop 4 -> 3, debug `ending=STILL_HOT reason=rampage` |
| A4 auto-logout-lock | A2 setup, quit and rejoin (R4a/R4b pattern), seal once cops hunt him again | first drop 2 -> 1, `reason=logout` (REVIEW if R4b shows no cop group re-forms after a rejoin) |
| A5 auto-learn-persist | twice: two console kills inside 10 s (80 + 80 x 1.5 streak = 200 heat = 1 star, cause CRIME), seal, escape (PETTY); 5 s+ apart; stop the server; `sqlite3 "$G/<the one *.db file in plugins/Gangland_Warfare/>" "SELECT player_uuid, n, actual, expected FROM chase_habit;"` (sqlite3 is `/c/msys64/ucrt64/bin/sqlite3`); start again and repeat once | the row for Runner has `n` = 1.9 and survives the restart; the next debug line shows `delta>0` (about 0.03) |
| A6 auto-config-compat | boot `auto` with the 0.15.1 `copsncrooks/wanted.yml` (no `Auto` block), extracted with `jarcat` from the 0.15.1 cops-n-crooks jar as `prep-cnc015.sh:63-64` does; then the same file with `Wanted.Evasion.Auto.Momentum.Step_Speed: 1.5` added by `yset.js` | `Done (` present and no exception both times; the first boot logs no `config.` line; the second logs exactly one `config.range` line (an ERROR report line, the house range check) naming `Step_Speed` |
| A7 auto-clean-break (REVIEW) | `/glw wanted add 3`, after loss of sight `tp` Runner 15 blocks further out every second (each hop under the 32-block teleport rule) | a 2-star drop with `ending=CLEAN_BREAK` |
| A8 auto-flee-on-foot (REVIEW) | as A7 but 5 blocks a second, about a sprint | `ending=HUNKER_DOWN` with `outside` about 0.2-0.3 (90 blocks of zone at 3 stars take 18 s, then 6 s outside): running on foot is not a clean break |
| A9 auto-teleport (REVIEW) | as A7 but one `tp` 150 blocks out | `teleported=true`, no CLEAN_BREAK |
| Regression | R1-R4, N1-N8 on the default profile (ONE_STAR) | unchanged |

Release pass: A1-A3, A5, A6 PASS, A4 PASS or REVIEW, A7-A9 REVIEW read, regression green. The bot cannot read titles or boss bars (README "Known
limits"), so the manual checklist gains: a petty card, a still-hot card, a clean-break card, the `Evaded_Many` flash.

---

## 10. Risks and open owner decisions

Risks:

1. **Unpredictable size.** Mitigated: named endings with a card that says why, the worst case is today's ONE_STAR, and
   no preview on the bar (it would hand players the formula; C's reasoning).
2. **Defaults are guesses.** Nobody has played AUTO. All numbers are YAML; tune in the sandbox with A1-A7 before any
   default flip.
3. **Learning feedback.** Learning the typical length from AUTO's own lumps would shrink it, and learning it only from
   some endings would bias it. Fixed by learning contact time (chase start to the last loss of sight, which AUTO never
   changes) from every getaway, plus the half/double clamp. A test pins it.
4. **Lose-to-win.** DEATH is never learned and 1-star arrests or bribes are not either, so the cheap farms (lava, a fall,
   a 1-star arrest) teach nothing (W6). What is left is getting arrested or bribing at 2+ stars on purpose: that costs
   jail time or money each time, never adds stars per drop, and shortens later timers by at most 40 % from the habit
   alone (`Min_Time_Factor`; with momentum on top the overall limit is `Momentum.Floor`). Bounded, not eliminated (D8).
5. **Narrow escapes are the common case.** Twenty seconds in sight happens in most foot or car chases, so the 0.5 step
   will apply more often than the 0.75 one. It never applies under a lock. Tune `Narrow_Seen_Seconds` in the sandbox if
   cascades feel too quick.
6. **Event-order coupling.** The pending plan is stashed before `stars.drop` and read in `onLevelChange` inside it;
   the chase-end recorder must stay at HIGH while `HeatListener` clears at MONITOR. Both pinned by tests.
7. **Take_Money charges per star** (`CORE/wanted/WantedStars.java:129-134`): a 3-star lump costs what three single drops
   cost, as ALL_STARS does today. Off by default.
8. **Constructor growth.** `EvasionClock` goes from 7 to 10 arguments; `EvasionClockTest.setUp` and
   `EvasionModuleConfig` change. `EvasionSettings` keeps its 6-argument constructor.
9. **Memory.** Arcs of players who quit mid-chase and never return: pruned after 30 min offline (a returning player is
   locked by the RESTORE rule, so pruning gives nothing away); `recent` capped at 8.
10. **Inherited clock quirk.** A star raise mid-search keeps old `progress` against the new, larger need
    (`EvasionClock.java:140-147`). AUTO recomputes `needMs` there but does not change the carry-over (D9).
11. **Rampage counts crimes, not heat (0.16 follow-up).** Every crime 0.15 reports weighs 80 or more, so four crimes in
    30 s is real violence. 0.16 starts reporting cheap crimes (`Brandish_Near_Cop` 25, `Assault_Civilian` 30,
    `wanted.yml:49-51`); before that ships, the rampage opening must count only heavy crimes or heat. Noted for the 0.16 plan.
12. **Cold start.** Until a level row has real samples, the guessed `Escape_Rate` decides who is a known face, and it
    weighs most at 4-5 stars, where escapes are rare (two 5-star getaways already make a known face, W2). The admin page
    (T9) explains the warm-up and how to tune `Escape_Rate` to the server's real numbers.
13. **Zone shrink.** After a drop the zone radius shrinks to the new level while the centre stays put, so a player
    standing still can move from inside to outside mid-spell and drift toward a clean break. Accepted: it reads as "they
    are losing you".

Owner decisions (recommended option first):

| # | Decision | Recommended | Alternative |
|---|---|---|---|
| D1 | Release | own 0.15.2 branch from 0.15.1, independent of 0.16. 0.15.1 is not in master yet: merge 0.15.1 first, or merge 0.15.2 together with it | fold into 0.16 |
| D2 | Shipped `Drop_Mode` | stay ONE_STAR; consider AUTO as default after one release of play | ship AUTO as default now |
| D3 | When learning runs | only while `Drop_Mode` is AUTO (no tables written on servers that do not use it) | in every mode, so a server switching to AUTO starts warm |
| D4 | Public event | none now; add `WantedEvasionDropEvent` in the 0.16 api 2.3 bump when criminal record needs it | add it now as api 2.3 and move 0.16 to 2.4 |
| D5 | Rampage by stars | any chase reaching 4 stars is locked until 180 s quiet | only 5 stars (`Rampage_Peak_Level: 5`) |
| D6 | Small-fry generosity | up to 2 crimes and 2 stars go at once | `Petty.Max_Peak_Level: 1` |
| D7 | Explain command `/glw wanted auto [player]` | later; the card and debug line cover 0.15.2 | add now (`CommandContribution` + module `commands.json`) |
| D8 | Habit for always-caught players | keep the shorter timer (habit alone bounded at 0.6x; DEATH and 1-star catches are never learned, so only 2+ star arrests or bribes count) | tighten only: clamp `delta` at 0 from below |
| D9 | Progress carried over a mid-search raise | fix later; the docket entry is not optional (CLAUDE.md: a bug found that is not in the docket is added): T11 files it as P3 id 179 | fix it in 0.15.2: reset `progress` when the level rises mid-search |
| D10 | Shared MySQL across servers | accept (level stats are server-wide, last writer wins) | add `server_id` to the `chase_level_stat` key |
