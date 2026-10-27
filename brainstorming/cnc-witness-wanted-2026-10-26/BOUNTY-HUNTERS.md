# Bounty hunters: the existing plan, re-grounded on 0.21.1

**Status: a plan existed.** The owner asked to "check cops n crooks overhaul if it has that plan and add it if not
available". It has one, as two roadmap cards. This file keeps both cards, their ids and their release slots. It
re-grounds them on the 0.21.1 code and fixes the gaps and contradictions found. No new card is added.

Read-only. Nothing was edited. `graphify query "BountyHunter"` returned no nodes: no hunter code exists in any worktree.

## Path prefixes

- `cnc/` = `E:/Programming/java/wt/gl-0.21.1-civ/gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/`
- `civ/` = `E:/Programming/java/wt/gl-0.21.1-civ/gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians/`
- `ks/` = `E:/Programming/java/wt/keystone-1.15.4/keystone-npc/src/main/java/org/luckyraven/keystone/npc/`
- `EDL` = `gangland-impl/.../listener/player/EntityDamageListener.java`
- `ROAD` = `brainstorming/cnc-overhaul-2026-10-05/artifact/overhaul-live-2026-10-08.html`

## 0. Where the plan lives

- **"Wanted posters and bounty hunters"** (ROAD:2292-2302, grid :917). Work item **W-49**, "CnC: Record, jail and the road", milestone **0.22.0** (`brainstorming/release-1.0-2026-10-08/apply_owner_scope.py:119-122`).
- **"Bounty hunter license"** (ROAD:2494-2507, grid :928). Work item **W-51**, "Both sides", milestone **0.23.0** (`apply_owner_scope.py:127-130`). W-51 depends on W-49, W-50 and OD-20.
- W-50 is "Keystone 1.17.0: NPC carrier bodies" (`:123`), not the posse.
- Mentioned in passing in:
  - SPEC-0.15 (`:187,340,371,390-391`);
  - v0.16/SPEC-0.16 (`:179,185,241,288`);
  - v0.16/DECISIONS.md:57 (D20, the 3-per-hour takedown cap "until licensed hunters").

**Card text to fix in ROAD.** This is a plan edit, not code; the artifact is not edited here.
- The release labels still use the old numbering: "Ships in 0.19" near :2296 and "Ships in 0.20" near :2498. They should read 0.22.0 and 0.23.0.
- The short license text "Alive pays in full, dead pays half" (:2503) contradicts the long text (:2504), which says dead delivery pays only the posted bounty. **The long text wins** (§3).

## 1. What changes against the cards, and why

1. **The trigger is the bounty, not the criminal record.**
   - Heat = `Bounty.getNotoriety()` (`gangland-core/.../core/bounty/Bounty.java:173`) + `getPostedAmountExcluding(P)` (`:168`).
   - So the posse does not wait for the criminal record (also in 0.22.0). The record can later feed notoriety without changing the trigger.
2. **Dispatch follows a fixed rule.** A fixed cooldown and a tier table set the posse size. This replaces the card's "every so often" and obeys "no randomness in outcomes".
3. **Hunters come only at 0 stars, after the post-escape search.**
   - An escape turns peak stars into notoriety (`cnc/wanted/escape/PostEscapeSearch.java:95-110`).
   - Stars stay police business. Hunters are the pressure left once the stars are gone.
4. **Capture is a new branch in `BustedListener`, not `CuffingListener`.**
   - `BustedListener.onDamage` cancels the lethal hit and busts the player (`cnc/listener/police/BustedListener.java:73-91`, `bust` :118-140).
   - `CuffingListener` refuses downed targets (`:33,53`).
   - `CuffedEvent`'s constructor needs a `CopNpc` (`cnc/events/.../CuffedEvent.java:25`).
   - The capture writes the reserved `ArrestOrigin.DEAD_OR_ALIVE` (`cnc/detainment/ArrestOrigin.java:16-17`).
5. **The 60-block spawn needs its own spawn config.**
   - `findHiddenSpawnLocation` is protected and reads min/max distance from its `SpawnConfigProvider` (`ks/entity/EntitySpawner.java:260-283`).
   - The civilian spawn caps at 50 (`npc/civilians.yml:118-121`).
6. **Hunters never call 911, and they never take the live position.**
   - `WitnessService.witness` has no faction filter (T-460).
   - `CivilianService.squadFor` seeds a new squad with `reportSighting(target.getLocation())`, the live position (`civ/npc/CivilianService.java:263`; T-459).
   - So the posse uses its own pre-tipped squad.
7. **Payout: the long card text wins.**
   - Alive pays the posted bounty plus a capped share of the charge sheet. Dead pays the posted bounty only.
   - `Bounty.claimPosted` is all-or-nothing (`Bounty.java:184-194`), so there is no half claim.
8. **The D20 takedown cap stays** (`EDL:59-60,326-342`) until both the license fee and the per-target contract cooldown (SPEC-0.15:371) are live. Dropping it earlier reopens R37 (an alt posts $100 and murders become crime-free takedowns).
9. **The phone bounty board has no click actions** (`inventory/phone_bounty.yml:16-25`, rows UI-21 and WB-37). The license's "Part built" is true only for the Place Bounty anvil (:28-38).

## 2. Part A: the NPC posse (0.22.0, W-49)

### A1. Who the hunters are

- **The type.** A hostile civilian type `bounty_hunter` in `npc/civilians.yml` `Types:`, shaped like `gang_member` (`civilians.yml:244-260`):
  - `Hostile: true` and `Faction: bounty_hunter`;
  - a `Weapon_Pool` with one entry, so the weapon is not a random pick;
  - `Drops.Experience: 0` and no items;
  - no spawner entry, so it never spawns as ambient.
- **The mark.** They carry the CIVILIAN mark; `EntityMark` has only CIVILIAN, POLICE and UNSET (`civ/npc/entity/EntityMark.java:5-7`).
- **Targeting** (critic correction; not traced end to end, verify in W-49 planning).
  - Hostile civilians are *reactive*: combat starts only when one is damaged (`civ/listener/civilian/CivilianDamageListener.java:66,90`, `isHostile() && combatEnabled`).
  - So `HunterDispatcher`, right after each spawn, puts the hunter in the posse squad (`joinSquad(squad, P)`) and switches its state to the pursuit/combat behaviour against P.
  - A target filter, on the `CombatEligibility` / target-filter seam the civilians module exposes, limits hunters to their mark and to whoever damages them. A hunter never attacks bystanders or other players.

### A2. How a bounty grows (existing code)

**What feeds it:**
- **Stars:** `handleWanted` adds `getAutoBountyIncrease(level, stars)` as each star lands (`EDL:401-425`; `Bounty.java:123-127`). That is `baseAmount × stars × (1 + level × levelMultiplier / 10)`.
- **`baseAmount`** is `Bounty.Kill.Each` = 5 (`gangland-core/.../core/user/User.java:60`; `settings.yml:269-272`).
- **Escape:** `PostEscapeSearch.begin` adds peak-star notoriety, subject to the cap (`:105-106`, `fitsCap` :117).
- **Per kill:** each player kill adds `Kill.Each` (`EDL:440-456`), up to `Kill.Maximum` 50 000.
- **Timer:** `BountyExecutor` grows notoriety ×`Multiple` 2 every `Time` 300 s, up to `Maximum` 20 000 (`settings.yml:274-280`; `core/bounty/BountyExecutor.java:41-63`). When it runs is not traced: `startBountyTimer`, `EDL:428-438`.
- **Posted escrow:** players post escrow through `BountySetCommand`. It has no self-post guard (`command/sub/bounty/BountySetCommand.java:70-125`; T-458), which is why the trigger excludes self-posted money.
- **Most Wanted** stays stars-only (`cnc/wanted/mostwanted/MostWanted.java:104`).

**What a typical bounty reaches** (this sets the tier defaults; critic correction):
- **Escape alone is small.** An escape at ★5 adds about 25 × the level scale.
- **The timer is what grows it.** From 25 it doubles to about 1 600 in 30 min and reaches the 20 000 cap in about 50 min.
- **Escrow is unbounded.**
- So the draft tiers of 5 000/15 000/40 000 were too high: the top tier was unreachable by notoriety alone. **The new defaults are 1 000/4 000/12 000** (OD-6).

**With the witness plan** (PLAN.md), stars, and so notoriety, come only from crimes someone reported. Hunters come only for what the city knows.

### A3. Dispatch (`cnc/hunter/HunterDispatcher`, 1 s tick, BeanLifecycle)

A posse rides out for an online player P only when **all** of these hold:
- `Hunters.Enabled` is on and `NpcSupport.available()` is true;
- P's wanted level is 0 (`WantedClearContract.getWantedLevel`, as in `BustedListener.java:84`);
- P is not in a post-escape search (`PostEscapeSearch.isSearching`, :120);
- P is not restrained (`cnc/detainment/DetainmentService.java:62`);
- heat ≥ the lowest tier's `Bounty`;
- P has no posse alive and P's cooldown has expired;
- P has a `LastReported` spot (A4).

**How it rides out:**
- **Size:** the highest tier reached.
- **Spawn spot:** chosen by `HunterSpawnLocator extends EntitySpawner`, which calls `findHiddenSpawnLocation` (`EntitySpawner.java:260`). Its config delegates to the cops.yml `Spawn` provider and overrides only the three distances in hunters.yml.
- **Spawning:** each hunter goes through the public `CivilianSpawnManager.spawnCivilian(loc, "bounty_hunter")` (`civ/npc/spawn/CivilianSpawnManager.java:90`).
- **Randomness:** only the ring angle is random, and the spot is out of sight. The size, the timing and the tip are fixed.
- **Message:** P gets `Posse_Out`.

### A4. Tracking: last-known only (R4)

**`LastReported`** is a **shared** bean in cops-n-crooks: a map from UUID to (Location, time). Critic correction: it is fed by **both**
- a `CrimeCommittedEvent` listener (MONITOR, `ignoreCancelled`: only crimes that survived the witness gate), and
- `CopCrimeReports` from PLAN.md, for spots that never become a committed event: a silent cop's `lastReport`, a scene's spot, and a cop report's `at` the moment it lands.

`PostEscapeSearch.begin` (the escape spot) also feeds it. The 0.22.0 criminal record later replaces it with a persisted last-known district.

**`HunterSquads implements FactionSquads`** (`civ/npc/FactionSquads.java:18-40`; the pattern is `gangland-turf/.../TurfGarrisonSquads.java:15-40`):
- one `NpcSquad` per target, with scope `"hunters:" + uuid`;
- created with `reportTip(lastReported)` (`ks/NpcSquad.java:204-207`) **before** any member joins;
- `CivilianService.squadFor` is never called.

**Pursuit** uses Keystone's contact-gated `pursue`. The goal is the live position only while a hunter can see P, and `lastKnownLocation` otherwise (`ks/NpcPursuit.java:232-248`). The search widens through `searchPoint` (`NpcSquad.java:306-310`).

**No 911 calls:** `WitnessService.witness` skips the `bounty_hunter` faction (fixes T-460 for hunters). Civilians still report P to the police as usual.

**Giving up:** once `millisSinceSighting() > Give_Up_Seconds × 1000` (`NpcSquad.java:279`), the posse walks off, despawns out of sight, and P gets `Posse_Gave_Up`.

### A5. Hunters and the police

- **Separate factions.** Cops and hunters ignore each other. Hunters never feed police squads or `CopCrimeReports`, and never raise stars.
- **Shootouts:**
  - A hunter shooting P is not a crime.
  - P killing a hunter in COMBAT is not a crime (`civ/listener/npc/CivilianDeathRewardListener.java:51-52`).
  - P killing an idle hunter is a gated `Kill_Civilian`, which needs a witness as for any civilian.
- **Stars mid-hunt.** If stars land on P while a posse is out, the posse stands down and goes home. The cooldown is not reset.
- **Bribe:** not in 0.22.0 (OD-3).

### A6. Endings

**Capture (alive).** A new branch in `BustedListener.onDamage` runs after the cop branch, so a cop bust always wins (the order is fixed in code).
- It applies when `findHunter == null` (:87-88) and a living posse member is within `Capture_Radius` of the lethal hit.
- It keeps the existing guards: `isRespawnEnabled`, not downed, not restrained, and `SKIP_CAUSES` (:75-86).
- The `level < 1` check (:85) gates the cop branch only.
- It cancels the hit, then calls `detainmentService.handcuff(P, DEAD_OR_ALIVE)` (`DetainmentService.java:72`) and `transitService.schedule(P)` (`cnc/detainment/transit/TransitService.java:49`).
- It also **drops P's open witness-plan cases** (PLAN.md §1.4; critic correction), so no stars land on a captured player.

**At intake.** `JailIntakeService.admit` (`cnc/detainment/intake/JailIntakeService.java:80-118`) runs as for any arrest. `DEAD_OR_ALIVE` takes the default ×1.0 sentence, fine and bail (`cnc/integration/detainment/GanglandDetainmentCosts.java:91-103`).
- New: if `origin == DEAD_OR_ALIVE`, intake calls `bounty.claimPosted()`. The escrow is spent and nobody is paid, so it is a money sink (OD-2).
- Notoriety stays (`cnc-overhaul/PLAN.md:119`).

**Other endings.** Every ending below starts the cooldown, and none changes the bounty unless stated.
- **Killed by hunters:** an ordinary death, and wanted resets (`EDL:399`).
- **P kills the whole posse, or it gives up:** the posse ends.
- **Quit:** the posse despawns. There is no penalty: hunters are not police.
- **Rescue:** none in 0.22.0. Crew rescue needs a guarding cop (OD-4).
- **Escape:** hunters never feed `EvasionClock`.

### A7. Rewards and anti-abuse

- NPC hunters pay nobody.
- Hunters drop 0 XP and no items, so there is nothing to farm.
- Self-posted escrow does not count toward the trigger.
- An alt who posts on a main summons hunters at the alt's own cost, because capture burns that escrow.

### A8. Config: `copsncrooks/hunters.yml` (cops-n-crooks jar)

```yaml
Hunters:
   Enabled: true
   # npc/civilians.yml type id the posse is made of
   Type: bounty_hunter
   # bounty (notoriety + other players' escrow) -> posse size; the highest tier reached wins
   Tiers:
      1:
         Bounty: 1000
         Size: 2
      2:
         Bounty: 4000
         Size: 3
      3:
         Bounty: 12000
         Size: 4
   Spawn:
      Min_Distance: 60.0
      Max_Distance: 90.0
      Phase1_Min_Distance: 70.0
   # fixed gap after a posse ends (any ending) before the next one
   Cooldown_Seconds: 600
   # no hunter has seen the mark this long -> the posse leaves
   Give_Up_Seconds: 180
   # a lethal hit with a hunter this close captures instead of killing
   Capture_Radius: 4.0
```

- **Messages:** `copsncrooks/hunter_messages.yml` and `_es`, with `Posse_Out`, `Posse_Gave_Up`, `Captured_Title` and `Captured_Subtitle`.
- **No `gangland-api` change**, so `Host_Api` stays where it is.

### A9. Files

**New, in `cnc/hunter/`:**
- `HunterDispatcher`
- `HunterSquads`
- `HunterSpawnLocator`
- `HunterSettings`
- `LastReported`, a shared bean that `CopCrimeReports` also writes

**New resources:**
- `hunters.yml`
- `hunter_messages.yml` and `_es`

**Changed:**
- `BustedListener`
- `JailIntakeService`
- `civ/witness/WitnessService` (faction filter)
- `npc/civilians.yml` (the type)
- `CopsNCrooksModuleConfig` (beans)
- `commands.json` (`/glw hunters send <player>`, an admin test hook)
- `documentation/features/wanted-bounty.md`
- the ROAD card text (§0)

### A10. Tests (each shown red first)

| Test | Asserts |
|---|---|
| `HunterDispatcherTest.noPosseWhileWanted` | Level ≥ 1 sends no posse. |
| `HunterDispatcherTest.noPosseDuringPostEscapeSearch` | No posse during the search. |
| `HunterDispatcherTest.selfPostedDoesNotCount` | Self-posted escrow does not trigger one. |
| `HunterDispatcherTest.tierPicksSize` | The highest tier reached picks the size. |
| `HunterDispatcherTest.cooldownAfterEveryEnding` | The cooldown starts after every ending. |
| `HunterDispatcherTest.noTipNoPosse` | No `LastReported` spot, no posse. |
| `HunterDispatcherTest.spawnedHunterJoinsSquadAndTargetsMark` | Undamaged hunters still go after P. |
| `HunterSquadsTest.seededByTipNotSighting` | The R4 pin: `hasFreshSighting()` is false, and `lastKnownLocation` is the tip. |
| `HunterSquadsTest.giveUpAfterSilence` | The posse leaves after `Give_Up_Seconds`. |
| `HunterTargetFilterTest.ignoresBystanders` | Hunters attack only the mark and their attackers. |
| `LastReportedTest.fedBySilenceAndScene` | `CopCrimeReports` spots reach `LastReported`. |
| `BustedListenerTest.copBustBeatsHunterCapture` | A cop bust wins. |
| `BustedListenerTest.hunterCaptureIgnoresStars` | Capture works at 0 stars. |
| `BustedListenerTest.hunterCaptureWritesDeadOrAlive` | The origin is `DEAD_OR_ALIVE`. |
| `BustedListenerTest.hunterCaptureDropsOpenCases` | No stars land after a capture. |
| `BustedListenerTest.noHunterInRadiusDies` | No hunter in range, the hit kills. |
| `JailIntakeServiceTest.deadOrAliveClaimsPostedKeepsNotoriety` | Escrow is claimed and notoriety kept. |
| `JailIntakeServiceTest.cuffedOriginLeavesBounty` | Other origins leave the bounty alone. |
| `WitnessServiceTest.hunterNeverCalls911` | A hunter is never a 911 caller. |
| `CivilianDeathRewardListenerTest.killingCombatHunterIsNoCrime` | Pins today's behaviour. |
| `Bundled022YamlTest` | Every hunters.yml key is read by a loader. |

### A11. Live acceptance rows

| Row | Given | Expect |
|---|---|---|
| H-1 | Bounty ≥ T1, 0 stars, a reported crime 2 min ago | 2 hunters spawn ≥ 60 blocks away, out of sight; `HUNTERS dispatch size=2 tip=<x,y,z>`. |
| H-2 | P is 100 blocks from the tip | Hunters walk to the tip; `HUNTERS sighting` appears only after line of sight. |
| H-3 | P hides past `Give_Up_Seconds` | The posse despawns out of view; `Posse_Gave_Up`. |
| H-4 | Lethal hit next to a hunter, 0 stars | CAPTURED, transit, intake `origin=DEAD_OR_ALIVE`, posted 0, notoriety unchanged. |
| H-5 | A cop and a hunter both in radius, ★1+ | BUSTED wins; the origin is `BUSTED_DOWNED`. |
| H-6 | A hunter in view of an idle pedestrian | No 911 call. |
| H-7 | A crime in front of a cop while a posse is out | The posse stands down; police chase. |
| H-8 | Self-post above T3, notoriety 0 | No posse. |
| H-9 | No Citizens | The module boots; the dispatcher stays idle; no `NoClassDefFoundError`. |
| H-10 | Another player near P while hunters fight | Hunters never target him. |

## 3. Part B: bounty hunter license (0.23.0, W-51; updated plan, not yet a build list)

**Players as hunters:**
- `/glw hunter license` buys a license; the fee is a config key.
- Contracts are listed on the phone board, which first needs `OnClick` actions (UI-21, WB-37).
- A contract is offered only when `getPostedAmountExcluding(hunter) ≥ Bounty.Takedown_Minimum` (100, `settings.yml:267`).
- A compass points at `LastReported`, never at the live position.

**Endings** (both call one claim method, so the bounty is paid once):
- **Dead:** the existing kill claim pays the posted bounty (`EDL:166-181`).
- **Alive:** a licensed player cuffs a downed target. This needs a player-captor cuff path that bypasses `CuffingListener:33,53`, plus a new `captor_id` column on the detainment row (a schema change; today only `origin_type` is stored).
- **Intake** then pays the captor through `claimPosted()`, plus a capped share of the charge-sheet fine (`JailIntakeService.java:133-160`).

**Anti-abuse:**
- Allied and same-gang captors get nothing (`EDL:172`).
- Self-posted escrow is excluded.
- Each target has a contract cooldown.
- The license fee acts as the alt tax.
- The D20 cap stays (§1.8).

**Needs first:**
- the posse (`LastReported`, `HunterSquads`);
- the playable jail and the phone board from 0.22.0;
- OD-20.

## 4. Posters (0.22.0, same card as the posse)

- Notice boards reuse the `BountySign` VIEW head display (`sign/aspect/BountyAspect.java:104-134`). The only new work is placing the signs.
- "Recognized on sight" (ROAD:1069-1070) stays with the criminal record, not with hunters.

## 5. Placement and dependencies

- **Module:** cops-n-crooks. It owns capture, intake, `ArrestOrigin` and transit, and already declares `Depends: [turf, civilians]` and `Plugins: [Bartizan]`.
- **Bartizan:** the hunter `Weapon_Pool` uses `weapon:`. Without Bartizan, both civilians and cops-n-crooks are skipped.
- **Citizens:** the dispatcher checks `NpcSupport.available()`. A later Citizens-typed handler (a bribe, for example) carries `@ListenerHandler(condition = "isCitizensAvailable")`.
- **Other work it relies on:**
  - the witness plan's `CopCrimeReports` feeding `LastReported`, if PLAN.md ships first; otherwise `LastReported` starts with the event listener and the escape spot only;
  - the rule that a bounty outlives the escape, which already holds (`PostEscapeSearch.java:105-106`).

## 6. Docket

**Existing rows:**
- WB-37 and UI-21 (the board has no click actions) are prerequisites for the license.
- WB-43 (no gang check on claims) looks fixed by `alliedOrSame` (`EDL:172`); check the row.
- D20 stays open.

**New rows (in docket-rows.txt):**
- T-458: `/glw bounty set` accepts a bounty on yourself (P3).
- T-459: `CivilianService.squadFor` seeds the live position (P2, R4).
- T-460: `WitnessService.witness` has no faction filter (P3).

## Owner decisions

Each decision has a recommended default; the plan above uses it.

1. **Do hunters also come while P is wanted?** Default: no. They come only at 0 stars, after the post-escape search.
2. **What happens to the posted escrow on a hunter capture?** Default: it is burned, as a money sink. The alternative is refunding the posters.
3. **Can the posse be bought off?** Default: not in 0.22.0. If wanted later: right-click a hunter and pay posted × a multiplier, then `claimPosted()`.
4. **Can a crew mate rescue a hunter capture in transit?** Default: no.
5. **Does a posse kill claim the posted bounty?** Default: no; the bounty stands.
6. **Tier amounts and posse sizes.** Default: 1 000/4 000/12 000 for sizes 2/3/4, set from the arithmetic in A2.
   - The alternative is tiers relative to `Repeating_Timer.Maximum` (5 %/20 %/60 %).
   - The draft's 40 000 tier is unreachable without escrow.
7. **The license payout split.** Default: the long card text. Alive pays posted + a capped charge-sheet share; dead pays posted only. "Dead pays half" is struck.
8. **When is the D20 takedown cap dropped?** Default: only after the license fee and the per-target contract cooldown ship.
9. **The posse trigger.** Default: bounty-keyed (notoriety + others' escrow), not the criminal record threshold, so 0.22.0 can ship the posse before or beside the record.

## Staging / release slot

- **Posters and the NPC posse:** **0.22.0** "Record, jail and the road", work item W-49 (`apply_owner_scope.py:119-122`). It no longer waits for the criminal record.
  - Docket prerequisites: T-459 and T-460 are fixed inside this work.
  - Order: if the witness plan (PLAN.md, 0.21.2) has shipped, `LastReported` is also fed by `CopCrimeReports`; if not, it works from committed crimes and escape spots only.
- **The license:** **0.23.0** "Both sides", work item W-51 (`:127-130`). It needs W-49, the playable jail, the phone-board click handlers (UI-21, WB-37), the `captor_id` schema change and OD-20.
- **Not in 0.21.x:** this is new gameplay, not a patch.
- **Plan edit, anytime:** relabel the two ROAD cards to 0.22.0 and 0.23.0, and strike "dead pays half".
