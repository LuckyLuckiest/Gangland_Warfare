# Evasion clock map (0.15.0) - for the AUTO Drop_Mode plan

Paths: EC = gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks. CORE = gangland-core/src/main/java/org/luckyraven/gangland/core/wanted. Graphify oriented first (EvasionClock node at EvasionClock.java L37).

## 1. Wiring
- EC/config/EvasionModuleConfig.java:28-33 builds the bean `EvasionClock(config, copManager, detainment, wantedStars, users("online"), System::currentTimeMillis, Bukkit callEvent)`. Time and event dispatch are injected, so tests drive them.
- EvasionModuleConfig.java:36-40 `@PostConstruct`: `WantedStars.installDecayPolicy(clock)` (decay seam) and `CopManager.addAiTickHook(clock::tick)`.
- EC/listener/wanted/EvasionListener.java:19-27: WantedEndEvent and PlayerQuitEvent (MONITOR) call `clock.clear(player)`.

## 2. Tick source
- `CopManager.addAiTickHook` EC/npc/police/CopManager.java:423; hook list L76; `runAiTickHooks` L432-440 (try/catch: a throwing hook is logged and skipped).
- Called at CopManager.java:364 (group = null), :749 and :793 (group as found). So `tick(player, group)` runs once per cop AI tick of an ONLINE chased player; group may be null. Main thread only.
- One tick counts at most MAX_DT_MS = 1000 ms (EvasionClock.java:40, used L140).

## 3. EvasionClock (EC/wanted/evasion/EvasionClock.java, 206 lines)
`Track` (L42-51): state, level, secondsLeft (-1 = nothing fired yet), centre (Location), radius, progress (speed-weighted ms), lastTick. `tracks` map L60 (UUID -> Track). A Track exists only for SEEN/SEARCHING/EVADED; absent = untracked.

tick(player, group) L96-164, in order:
1. L97-102 gate: no user, `Evasion.Enable` false, not wanted, or no live cop (`hasLiveCop` L198-205: a valid CopNpc whose state != RETURNING) -> `clear(player)` (fires OFF once) and return.
2. L110-113 pause: restrained (`detainment.isRestrained`) or `group.tippedOffWithin(now, lostMs)` (CopGroup.java:246; stamped by `markTipOff` CopGroup.java:241, called from CopManager.java:655) -> only `lastTick = now`; no progress, no state change.
3. L116-126 SEEN: `group.getSquad().millisSinceSighting() < Lost_Sight_Seconds*1000`. Creates a NEW Track (progress and centre reset) when track is null or not SEEN, fires SEEN (L122). Being seen always wipes any SEARCHING/EVADED track.
4. L129-134 first search: track null or SEEN -> `startSearch` (L166-176) at `squad.lastKnownLocation()` (fallback player location). L135-138: track EVADED -> `startSearch` again at the SAME centre (zone is not re-centred after a drop; it re-centres only through SEEN -> SEARCHING at the new last sighting).
5. L140-145 progress: `dt = clamp(now - lastTick, 0, 1000)`; `speed = speed(...)` (L189-196: 1.0 inside `radius` of centre in the same world, else `max(0.01, Outside_Zone_Speed)`); `progress += dt * speed`.
6. DROP POINT L147-161: `need = cfg.secondsToDropFor(level)*1000`; when `progress >= need`:
   `int dropped = stars.drop(user, cfg.dropMode() == DropMode.ALL_STARS ? level : 1, WantedCause.EVASION);` (L149). progress reset to 0 (L150). If no longer wanted or dropped == 0, return (L152; the last star ended the chase: WantedEndEvent listener already cleared him and fired OFF). Else state = EVADED, level/radius refreshed, secondsLeft 0, fires `WantedEvasionStateEvent(EVADED, level, 0, centre, radius)` (L154-159).
7. L163 `fireCountdown` (L179-186): `left = ceil((need - progress)/1000/speed)`; fires SEARCHING only when `left` changed.
- `snapshot(UUID)` L79-84 -> `EvasionSnapshot(state, level, secondsLeft>=0, zoneCentre, zoneRadius)` (EvasionSnapshot.java:13). No main-code caller found outside the clock; tests only.
- `handlesDecay` L73-76 = `Evasion.Enable && hasLiveCop(group)`. When the last live cop turns RETURNING/invalid, the track is cleared on the next tick (L99-101) and the settings.yml Repeating_Timer takes over again (ONE star per interval).
- EVADED has no timeout of its own: the next tick re-enters SEARCHING (L135-138), so it lasts one tick in the clock; the HUD holds the flash 6 hud ticks (see 7).

Data in scope at the drop point (L148-149): player, user, wanted (level, max level, isWanted), `track` (state, level, centre, radius, progress, lastTick), cfg (EvasionSettings), `now`, speed, `group` (CopGroup: getCops(), getSquad(), tippedOffWithin, casualty/regroup state), squad (millisSinceSighting, lastKnownLocation), detainment, copManager.
NOT in scope today (new constructor deps or Track fields): HeatLedger, chase start time, chase start cause, sighting count / longest seen streak, inside-zone vs outside-zone time, stars already dropped this chase, last-drop time.

## 4. Sightings feed
- Squad (Keystone 1.12 NpcSquad): `reportSighting(Location)`, `hasFreshSighting`, `lastKnownLocation`, `millisSinceSighting`.
- reportSighting callers: EC/listener/police/ShotNoiseListener.java:85 (player fired within radius of the nearest live non-RETURNING cop; nearest found L63-83, then radio line "Shots_Fired"), CopManager.java:121, :219 (cop attacked, attacker's location), :656 (next to markTipOff L655), CopGroup.java:132 (group seed), CopNpc.java:301, state/behavior/IdleBehavior.java:32 (cop sees target). Civilians module also reports (CivilianService.java:201,224; CivilianCombatBehavior.java:126).
- A sighting never calls the clock directly. The clock polls `millisSinceSighting()` each tick, so gunshot, cop LOS and civilian witness all just show up as SEEN at the next tick, with no record of which one it was.

## 5. Config and parsing
- EC/wanted/config/DropMode.java:5-8 enum {ONE_STAR, ALL_STARS}. AUTO = third constant.
- EC/wanted/config/EvasionSettings.java:244-245 record(enabled, lostSightSeconds, dropMode, searchRadius, secondsToDrop, outsideZoneSpeed). DEFAULT L247-249 = (true, 3, ONE_STAR, [40,60,90,130,180], [10,20,30,45,60], 2.0). `radiusFor`/`secondsToDropFor` clamp level into the list (L253-264). Grep `new EvasionSettings(` in tests before changing arity.
- EC/wanted/config/ChaseConfig.java: `evasion(n, report)` L66-78 (Enable, Lost_Sight_Seconds min 0, Drop_Mode, Search_Radius, Seconds_To_Drop, Outside_Zone_Speed min 0); called from L31. `dropMode(n, report)` L80-94: case-insensitive match over `DropMode.values()`, so AUTO parses for free; an unknown value yields `report.add(Severity.WARNING, at, "Wanted.Evasion.Drop_Mode", "unknown Drop_Mode \"x\", using ONE_STAR (ONE_STAR or ALL_STARS)", "config.enum")` and falls back to ONE_STAR. That message hardcodes the two names and must mention AUTO. Empty lists fall back to defaults silently (no warning).
- ChaseConfigLoader (EC/wanted/config/ChaseConfigLoader.java) loads `copsncrooks/wanted.yml`; `config.get()` is read live on every tick (no caching in the clock), so a reload applies at once.
- Shipped YAML: gangland-features/cops-n-crooks/src/main/resources/copsncrooks/wanted.yml:57-81 (`Wanted.Evasion`; Drop_Mode comment at L65-66 says "ONE_STAR or ALL_STARS"). New AUTO knobs: a new commented block under `Wanted.Evasion`, Capitalized_Underscore, block style. Heat vocabulary already in `Wanted.Heat` (HeatSettings.java): Seen_By_Cop_Multiplier, Turf_War_Multiplier, streak bonus, per-crime weights, floors, Assault_Repeat_Seconds.

## 6. Drop mechanics and Repeating_Timer safety net
- CORE/WantedStars.java:112-148 `drop(context, stars, cause)`: off the main thread it reschedules and returns 0 (L113-116); `target = max(0, before - max(0, stars))` L120; `wanted.setLevel(target, cause)` L124 (a cancelled WantedLevelChangeEvent leaves the level and returns 0, L126-127); then star-drop price (Take_Money) summed per star dropped L129-134, withdrawn once; "decreased" message with the NEW level L136-139 (hidden from chat while the HUD star card is on, `suppressStarChat` L52-61); money-loss line L141-143; `wanted.stopTimer()` at level 0 L145. Returns stars actually dropped.
- `stars == 0` is a no-op returning 0, and the clock treats dropped == 0 as "no EVADED" (L152). AUTO choosing "0 now" must skip the call itself.
- Cause enum: CORE/WantedCause.java:16-19 (DECAY = Repeating_Timer, EVASION). Wanted.setLevel(int, WantedCause) is CORE/Wanted.java:68, fires WantedLevelChangeEvent.
- Decay seam: CORE/WantedDecayPolicy.java:17 `handlesDecay(Player, Wanted)`; `installDecayPolicy` WantedStars.java:40-42; `isDecayHandled` L45-50. CORE/WantedExecutor.java:83-99: each Repeating_Timer tick returns if `stars.isDecayHandled(wanted)` (L88), else fires WantedEvent then `stars.drop(context, 1, WantedCause.DECAY)` (L96). Timer (re)started by `raise`/`restore`/`startDecayClock` (WantedStars.java:156-164) at `Repeating_Timer.Time * multiplier^level` (WantedExecutor.java:68-81). The safety net never drops more than one star and is out of AUTO's control.
- Heat coupling: `HeatLedger.onLevelChanged` (EC/wanted/heat/HeatLedger.java:137-147), called from listener/wanted/HeatListener.java:29 on every level change: after a drop, chase heat is clamped to `heat.floorOf(newLevel)`. `HeatLedger.clear` L150 at chase end.

## 7. Events and HUD reaction
- gangland-api/.../events/wanted/WantedEvasionStateEvent.java:16-50: (player, EvasionState, level, secondsLeft, zoneCentre clone, zoneRadius), `super(false)`. EvasionState = OFF, SEEN, SEARCHING, EVADED. Added in api 2.1 (GanglandApi.java:29). Adding a field is additive (new constructor + getter, bump api minor); keep the 6-arg constructor.
- WantedHudListener (EC/listener/wanted/WantedHudListener.java): `onLevelChange` L71-85 - on a drop with `newLevel > 0 || isGetaway(cause)` it calls `announce(player, stars, StarCard.dropCard(messages, cause))` (L80-81; title + subtitle card or chat, L120-129) and `hud.stars(...)` L84. `onEvasionState` L87-91 -> `WantedHud.state(...)`. `onWantedEnd` L94 hides. `isGetaway` L103-105 = EVASION or DECAY.
- `StarCard.dropCard` (EC/wanted/hud/StarCard.java:35-43) switches on cause only: EVASION -> CARD_DROP_EVASION, DECAY -> CARD_DROP_DECAY, else CARD_DROP_OTHER. It knows neither how many stars fell nor why. A card like "you shook them: -2 stars" needs a new message key keyed on (cause, starsDropped) or an extra argument. One card per setLevel, so a multi-star drop shows one card with the new star count.
- WantedHud (EC/wanted/hud/WantedHud.java): `state(...)` L84-100; EVADED flash held EVADED_TICKS = 6 hud ticks (L34, L94-97, L122-124; hud timer every 10 game ticks, WantedHudListener.java:62); zone ring only while SEARCHING (L129); compass/arrow only SEARCHING + Compass enabled (L157-162); boss-bar title `StarCard.barTitle` (StarCard.java:46+) shows only `secondsLeft`. AUTO has no single countdown, so it must supply a sensible secondsLeft (ETA to next drop) or the bar goes stale.

## 8. Where AUTO plugs in (minimum edit points)
1. DropMode.java: add `AUTO`.
2. ChaseConfig.dropMode (L80-94): AUTO parses already; update the warning string (L91-92 area). Parse a new `Wanted.Evasion.Auto` block in `evasion(...)` (L66-78); add fields to EvasionSettings + DEFAULT (L247).
3. EvasionClock.tick drop branch L147-161: when `cfg.dropMode() == AUTO`, replace the fixed `need`/`progress` test and the star count at L149 with a planner call returning (starsToDrop, ETA seconds). ONE_STAR / ALL_STARS stay untouched at L149. In scope: level, progress, centre/radius, speed, dt, squad sighting data.
4. Track (L42-51): chase history cannot live in Track because it is thrown away on every SEEN (L118-121). Put it in a per-player map beside `tracks` (cleared in `clear()` L87-94 and at chase end), or keep the Track across SEEN.
5. HeatLedger (EC/wanted/heat/HeatLedger.java): `chaseCrimes(UUID)` L166, `heatOf` L155, `lastCrime` L160 already expose "how it started" (first CrimeRecord: crimeId, heat, at, location) and "how heavy" (heat, crime count, timestamps). It has no explicit chaseStartedAt (use first CrimeRecord.at) and forgets everything on `clear` L150. EvasionClock needs it as a new constructor argument: EvasionModuleConfig.java:28-33 and EvasionClockTest setUp (L73) build the clock.
6. Event/HUD: additive field on WantedEvasionStateEvent (e.g. planned next drop size) and new StarCard/WantedMessages keys (module-owned YAML, so no api Messages change).
7. "Learns": nothing like it exists in evasion code. Zero-migration option = in-memory per-player/per-server aggregate; persistence needs a module repository (cops-n-crooks database package, `setDataSupplier` wiring) and is a larger scope.
8. Tests: gangland-features/cops-n-crooks/src/test/.../wanted/evasion/EvasionClockTest.java (setUp L73) injects clock + callEvent; ChaseConfigLoader tests asserting the Drop_Mode warning text must be updated.

## 9. Sharp edges
- Chase history is lost on every SEEN (Track recreated, L118).
- EVADED -> SEARCHING keeps `track.centre` (L136); SEEN -> SEARCHING re-centres on the last sighting (L130-132).
- `progress` is speed-weighted ms (20 s need with Outside_Zone_Speed 2.0 = 10 real s outside the zone). AUTO should use wall-clock for "how long was the chase" and weighted progress only for the evade timer.
- Every dropped star is priced (Take_Money) through WantedStars.drop; a multi-star AUTO drop charges as ALL_STARS does today.
- Restraint and tip-off pauses (L110-113) freeze the clock without changing state; AUTO's chase-duration timer must decide whether those pauses count.
- A star raise (CRIME/ADMIN/BRIBE) mid-search refreshes `track.level` (L141) but does not reset `progress`, so old progress is measured against the new, larger `need`.
- Fallback to the Repeating_Timer when the last live cop goes RETURNING (L73-76, L99-101) always costs one star per timer interval, outside AUTO.
