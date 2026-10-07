# Review: code facts (lens "code-facts")

Reviewed: `auto-drop/PLAN.md` against branch `0.15.1` (HEAD 046887e1). The graph (`graphify-out/graph.json`, 01:08) was
older than HEAD (01:20); I ran `graphify update . --force` first, oriented with `graphify explain EvasionClock` /
`graphify query WantedStartEvent`, then read the files below. Paths: `EC` = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks`.

Count: **0 blocker, 6 major, 6 minor.**

Anchors I checked and found correct (no action): `DropMode.java:3-7`; `EvasionSettings.java:16-17, 19-21, 30-32, 34-36`
(37 lines); `EvasionClock.java` Track `:42-51`, ctor `:62-71` (7 args), `:115`, SEEN `:116-126`, `:118`, `:129-133`,
`:140-145`, `:147-149`, `:152`, `startSearch :166-176`, `fireCountdown :179-186`, `speed :189-196`; `ChaseConfig.java:36,
66-78, 80-93` (`report.add(Severity, at, path, msg, code)` at `:90-91`); `HeatLedger.chaseCrimes :166-169`;
`CrimeRecord.java:10`; `Crimes.KILL_COP` at `gangland-api/.../crime/Crimes.java:11`; `HeatListener.java:22-36` (no quit
handler, all MONITOR); `EvasionListener.java:19-22`; `Wanted.setLevel` fires the change event before mutating
(`Wanted.java:68-85`); `WantedStars.drop` returns 0 on a no-op (`WantedStars.java:122`) and charges per star (`:129-134`);
`WantedStartEvent`/`WantedEndEvent` carry `getCause()`; `WantedCause` has every value 3.5 names; crime raises use
`WantedCause.CRIME` (`EntityDamageListener.java:314`); `HeatLedger` and `EvasionClock` both use
`System::currentTimeMillis` (`HeatModuleConfig.java:40`, `EvasionModuleConfig.java:31`); `JailExitService` BeanLifecycle +
`setDataSupplier` in ctor (`JailExitService.java:17-27`); `jailExitService(..., RepositoryRegistry)` at
`CopsNCrooksModuleConfig.java:171-174`; `EC/database` is the scanned `REPOSITORY_PACKAGE` (`CopsNCrooksModule.java:31,43`)
and `EC/listener` the listener package (`:29`); a String PK table exists (`DetainmentTable`, `player_uuid`);
`RepositoryRegistry.saveAll` snapshots suppliers on the calling thread (`PeriodicalUpdates.java:98-101`); `NodeReader`
has `asList().ofDoubles()` (Keystone `NodeReader.java:640`); YAML anchors `wanted.yml:65-66, :82` (3-space indent),
`wanted_messages.yml:6, :17, :35`; `WantedMessages.Key :19-36`; `StarCard :35-43, :46-53`; `WantedHudListener :52-63,
:80-81`; `EvasionClockTest` setUp `:73-112`, 6-arg `EvasionSettings` callers `:212, :268` (and `ChaseConfig.java:73`, the
only main caller); `ChaseConfigTest:132-139`; harness still extracts `npc/wanted.yml` (`prep-cnc015.sh:63-64`,
`README.md:37`) while the jar holds `copsncrooks/wanted.yml`; `GanglandApi.VERSION = "2.2"`, `module.yml` `Host_Api: 2.2`;
no docket entry mentions evasion. No Paper API is used anywhere in the plan.

---

1. **major - the ending of the chase-ending drop is never recorded before the learner reads it.**
   Evidence: the plan records `lastEnding` "after a drop with `dropped > 0`" in `EvasionClock.java:150-159`, but
   `EvasionClock.java:152` returns before that code whenever `!wanted.isWanted()`. The `WantedEndEvent` is fired inside
   `stars.drop` (`Wanted.java:95-96`), so `ChaseArcListener`'s HIGH end handler runs while the last ending is still
   unwritten. The `pending` plan is no fallback: `WantedHudListener.onLevelChange` (MONITOR, `WantedHudListener.java:71-82`)
   takes it during the `WantedLevelChangeEvent`, which fires before the end event (`Wanted.java:73-79` vs `:94-97`).
   Effect: a chase that ends with one PETTY / COLD_TRAIL / CLEAN_BREAK lump is learned with `lastEnding = null` (or the
   ending before it). A HUNKER chain that ends in a COLD_TRAIL lump counts as a "natural getaway", so the self-shortening
   loop that section 2 says is removed comes back. A 1-star getaway with a single HUNKER drop is never learned.
   Fix: write `arc.lastEnding = plan.ending()` in `autoCount`, at the same point as `stashPending`, before `stars.drop`.
   Keep it separate from `pending`, which the HUD consumes. Pin this with an `EvasionClockTest` row where the last star
   ends the chase.

2. **major - the quit counter never fires, so the logout lock does nothing.**
   Evidence: section 5 has `ChaseArcListener` on `PlayerQuitEvent` MONITOR calling `quit` "when wanted". But
   `RemoveAccountListener.onPlayerLeave` (HIGHEST) already calls `userManager.remove(user)` on the `@Qualifier("online")`
   manager (`RemoveAccountListener.java:58-69`, ctor `:32`). By the time a MONITOR handler runs, `users.getUser(player)` is
   null and the player looks not wanted. E7 and A4 then fail: `quits` stays 0.
   Fix: decide "wanted" from the arc itself (`arcs.has(id)` means a chase is running) or run the handler at LOWEST.
   Pin it with a listener test that registers `RemoveAccountListener`'s removal first.

3. **major - a RESTORE raise makes `quiet` negative, so the lock grows with offline time.**
   Evidence: on rejoin `WantedStars.restore` calls `setLevel(level, RESTORE)` (`WantedStars.java:91-98`,
   `UserDataLoader.java:109`). That fires `WantedLevelChangeEvent` 0->N first and `WantedStartEvent(RESTORE)` second
   (`Wanted.java:73-96`). The plan's change handler treats every raise as `hot`, so `lastHotAt = now`. The start handler
   then calls `restore`, which "shifts every stamp forward by `now - offlineAt`". That puts `lastHotAt` in the future,
   `quiet` goes negative, and the lock lasts offline gap + `Lock_Cool_Seconds`. That is the opposite of "offline time
   removed".
   Fix: in the `WantedLevelChangeEvent` handler, ignore cause RESTORE (no `hot`, no `peak`). Also exclude RESTORE from
   "star raise" in the definition of `quiet` (3.1).

4. **major - flipping `Drop_Mode` to AUTO by reload mid-search drops a star at once.**
   Evidence: section 5 sets `need = auto ? track.needMs : ...`, and `needMs` is only computed in `startSearch` (AUTO) or
   after a drop. `ChaseConfigLoader` swaps `loaded` live (`ChaseConfigLoader.java:65`) and the clock reads `config.get()`
   every tick (`EvasionClock.java:97`). A track that started under ONE_STAR has `needMs = 0`, so
   `track.progress >= 0` passes on the next tick and every tracked player loses a star. The plan even advertises the
   reload flip ("a reload that flips the mode needs no second parse").
   Fix: compute `needMs` in every mode (it equals `secondsToDropFor * 1000` outside AUTO), or recompute it when
   `needMs <= 0`. Add a test for a reload mid-search.

5. **major - the validation table names behaviour that `NodeReader` does not have.**
   Evidence: section 4.2 says the `>= 0` keys use "`.min(0)` like `Lost_Sight_Seconds`" and are "clamped", and the
   `>= 1` rows are "WARNING `config.range`, default". In Keystone, `IntAccess.min/max` and `DoubleAccess.min/max` report
   **`Severity.ERROR`** `config.range` and fall back to the default (`keystone-persistence/.../config/NodeReader.java:446-460`,
   `:496-510`). They neither clamp nor warn. `ChaseConfigLoader` only logs the report (`ChaseConfigLoader.java:67`), so
   boot is safe. But section 9's "each row of 4.2 gives its clamp and severity" and A6's "no ERROR ... exactly one
   warning" for `Step_Speed: 1.5` cannot both hold if the rows are built with `min`/`max`.
   Fix: either write the range rows as explicit checks (`report.add(Severity.WARNING, ...)` plus a clamp, like
   `dropMode` at `ChaseConfig.java:89-92`), or change the table to "ERROR `config.range`, default" and let A6 accept
   `config.range` lines.

6. **major - a long logout skips the logout lock, and "never a free lump" is false.**
   Evidence: `HeatLedger` keeps the chase's crimes across a quit. It is only cleared at `WantedEndEvent`
   (`HeatListener.java:33-36`, `HeatLedger.java:150-153`). `ChaseArcs.prune` drops arcs of players offline for over 30 min.
   A player who stays offline for 31 min rejoins with RESTORE and no arc, so a fresh arc gets `quits = 0` and no lock.
   His 1-2 old crimes are still in the ledger, so PETTY pays out in full: the exact thing E7 says logging out costs him.
   Separately, section 6 says a restart mid-chase "falls to HUNKER_DOWN (conservative, never a free lump)". CLEAN_BREAK
   (rule 4) reads no arc fact except `respots`, so a restored player who runs out of the zone still gets the lump.
   Fix: treat a `WantedStartEvent(RESTORE)` with no arc as `quits = 1` (locked until `Lock_Cool_Seconds` of quiet), or
   clear the ledger chase when the arc is pruned. Reword the section 6 sentence to match what the rules actually do.

7. **minor - the mid-search level-change check must read `track.level` before it is overwritten.**
   Evidence: section 5 row `EvasionClock.java:140-145` says "when `level != track.level` ... recompute `needMs`", but
   `:142` assigns `track.level = level` inside that range.
   Fix: compare before `:142`, or say so in the row.

8. **minor - the arc must seed `peak` and `lastHotAt` itself; the events that would set them arrive first.**
   Evidence: the 0->N `WantedLevelChangeEvent` fires before `WantedStartEvent` (`Wanted.java:73-96`), so `peak` and `hot`
   arrive before `start` creates the arc. The crime that starts the chase raises the star from inside
   `HeatListener.onCrime` (`HeatLedger.java:113-119`). `ChaseArcListener.onCrime` is also MONITOR, so whether it runs
   before or after that is undefined. Also, `hot` on `CrimeCommittedEvent` counts crimes that `HeatLedger.record` ignores
   (heat off, or weight <= 0, `HeatLedger.java:82-90`), so `quiet` and `crimes` can disagree.
   Fix: `start` sets `peak = event.getWantedLevel()` and `lastHotAt = now`. Count `hot` only for crimes the ledger kept
   (check `heat.weightOf(id) > 0` and `Heat.Enable`).

9. **minor - the heat arithmetic in E1 and A5 ignores the streak bonus.**
   Evidence: `settings.yml:315-318` ships `Kill_Combo.Enable: true`, `Reset_After: 10`. `HeatLedger.java:99-103` applies
   `Streak_Bonus` 1.5 when `now - last.at <= 10 000`. E1's Kill_Civilian at t=10 is therefore 100 x 1.5 x 1.5 = 225
   (total 345, not 270). A5's two kills within 10 s are 80 + 120 = 200, not 160. Both still land on the same star level,
   so no result changes.
   Fix: correct the numbers, or space the crimes more than 10 s apart.

10. **minor - W2 and W3 hold only when other players dominate the server row.**
    Evidence (section 3.5's own rules): the player's chases feed `S[peak]` with `w = 1` while `H.n < 4`. On a server where
    he is the only data, `p` at peak 3 climbs about 0.55 -> 0.57 -> 0.59 -> 0.61 -> 0.63 -> 0.64 over his six escapes.
    The decayed `expected` sum is then about 2.83, not 2.58, and `delta` comes out about 0.19, below the 0.20 threshold.
    `ChaseLearnerTest` "W2 (`delta = 0.218`)" will not reproduce unless the test seeds a large `S[3]` row.
    Fix: say in 3.6 and in the test plan that W2/W3 assume a warm `S` row (for example `n = 100`).

11. **minor - "lumps never come faster than today's first star" is false for a habitual loser.**
    Evidence: 3.4 says the first countdown "has `steps = 0`, so lumps never come faster than today's first star". But
    `habit` can go as low as `Min_Time_Factor` 0.6 (W3 shows 23.6 s against today's 30 s), and the lump rules (PETTY,
    CLEAN_BREAK) use that same timer.
    Fix: reword the sentence ("never faster than `Min_Time_Factor` x today's"), or apply `max(1, habit)` to the first
    countdown.

12. **minor - the key count is wrong.**
    Evidence: the 4.1 block has 29 leaf keys (35 counting the `Auto` / `Petty` / `Cold_Trail` / `Clean_Break` /
    `Momentum` / `Learning` headers), not "32 keys".
    Fix: correct the count.
