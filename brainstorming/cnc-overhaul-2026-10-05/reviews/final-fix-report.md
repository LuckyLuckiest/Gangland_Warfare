# Final-review fix report - Cops N Crooks 0.15 "Lose them"

- Worktree `E:/Programming/java/wt/cnc015-final`, branch `cnc-0.15-final`, from `cnc-lose-them`.
- headBefore `07fdd4f9e18a212d7d3aba57f53e80b8acc00723`, headAfter `68f6d10794aa59c2a9bec33ee69af4fa39328675` (7 commits, nothing pushed).
- Final run from the worktree root: `mvn -q -pl gangland-core,gangland-impl,gangland-features/cops-n-crooks -amd test` exited 0.
  Surefire totals: **1781 tests, 0 failures, 0 errors, 0 skipped**. By module: api 95, core 121, impl 352, cops-n-crooks 511,
  civilians 86, gadget 126, gang 136, healthbars 18, lootchest 60, mail 34, npc-shops 20, turf 108, item 48, sign-api 66.
  `mvn -q clean verify -DskipTests` (reactor) also exited 0.
- Nothing was written to the integration worktree, the docket, or triage files.

## Commits

| Commit | Summary |
|---|---|
| 743d7415 | Self-kill, self-posted bounty and max-star kills mint nothing (F6, F2, F8) + WB-14 coverage (F16) |
| e2b39a53 | Kill-tracker seam: `exemptsKill` (F1) and `appliesComboSwitch` (F9) |
| bae9ebfd | Login bounty restore merges over live posts on the main thread (F7) |
| 82cf2d71 | `bounty_posters` is a TEXT column (F5) |
| 447c3d79 | Reload turns the evasion clock OFF (F4); no charge sheet for an innocent jail throw (F14) |
| 87ef637f | Tests: heat/evasion `@PostConstruct` wiring (F15), regroup switch and arrival count (F17), Star_Card off (F18) |
| 68f6d107 | Docs: Bartizan still gates cops-n-crooks (F10), Shots_Fired respects the radio gaps (F11), death-formula fallback (F13), plus the rules this round added |

## Findings

| Id | Outcome | Commit | Test (how it went red) | What changed |
|---|---|---|---|---|
| F1 | fixed | e2b39a53 | `EntityDamageListenerTest.turfDefenderKill_addsNoNotoriety_andStartsNoBountyTimer`: red by assertion (notoriety 5 instead of 0) after the seam default was added but before the listener consulted it. `HeatWantedTrackerTest.exemptsKill_onlyAPlayerKillDefendingYourOwnTurf`: compile red (new method). | New `default boolean exemptsKill(Player, Entity)` on core `WantedKillTracker` (false), forwarded by `WantedKillTrackers.exemptsKill`. `HeatWantedTracker` implements it (real player victim and `defendingOwnTurf`), and `recordKill` reuses it. `EntityDamageListener` returns beside the self-defence return, so a defender gets no `Kill.Each` notoriety and no bounty timer. This matches the docket TF-49 fix direction. A posted-bounty payout still happens first. |
| F2 | fixed (rule needs owner confirmation) | 743d7415 | `EntityDamageListenerTest.selfPostedBounty_killIsStillACrime`: red by assertion (0 stars instead of 1). | New `Bounty.getPostedAmountExcluding(posterId)`. The crime-free takedown return now needs escrow from someone other than the killer. The killer still gets his own escrow back ("pays back only what you put in", spec line 341), but the kill takes the crime path. Left open (owner): an accomplice who is not an ally can still post a token amount, because `Bounty.Minimum` ships at 0. See new bug N2. |
| F3 | **disputed** | - | - | See "Disputed" below. |
| F4 | fixed | 447c3d79 | `CopManagerSquadTest.preClear_runsTheAiTickHooksWithNoGroup`: red by assertion (0 hook calls). | `CopManager.shutdown()` (run by `onPreClear` on reload and by `onShutdown`) despawns each group, then runs the AI-tick hooks with a `null` group for every online hunted player. `EvasionClock.tick(player, null)` then clears the player and fires OFF, so the HUD and compass reset. |
| F5 | fixed | 82cf2d71 | `UserTableTest.bountyPosters_isANullableTextColumn` (new class): red by assertion (VARCHAR instead of TEXT). | The `bounty_posters` attribute is now `Types.LONGVARCHAR`, which maps to ColumnType TEXT: TEXT on MySQL (64 KB, about 1,100 distinct posters) and TEXT on SQLite. This matches `migration-0.15.0.md` ("text, nullable"). Changed before release because Keystone's diff compares column names only. A `ponytail:` comment names the new ceiling. No source-side poster cap was added (ponytail: add one with a `bounty_post` table if 1,100 posters is ever reachable). Keystone's legacy MySQL dialect does not map LONGVARCHAR (new bug N3), but Gangland always uses the backend path. |
| F6 | fixed | 743d7415 | `EntityDamageListenerTest.selfKill_claimsNothing`: red by assertion (wallet 1000 instead of 0). | The first line of `handlePlayerKills` returns when killer == victim (an own projectile): no claim, no kill count, no crime. **Not in the docket: needs a new row** (see newBugs). |
| F7 | fixed | bae9ebfd | `UserDataLoaderTest.bountyPostedDuringTheLoad_survivesIt`: red by assertion (amount 100 instead of 150). | The loader no longer touches the bounty off-thread. It applies the bounty inline when already on the main thread, else through `runTask`. The new `Bounty.restoreSaved(saved, ledger)` merges every live ledger entry and the live amount on top of the saved row instead of clearing them. The notoriety-timer start moved into the same main-thread step. Left as is: the loader's other fields (kills, balance, level, bank) are still set off-thread, a pattern older than this wave. |
| F8 | fixed | 743d7415 | `EntityDamageListenerTest.killAtMaxWanted_addsNoAutoBounty`: red by assertion (notoriety 55 instead of 5). | `handleWanted` returns when `wantedStars.raise(..) == 0` (already at the maximum, or cancelled), so it adds no auto bounty, no bounty timer and no chat line. `raise` still restarts the clock at the maximum. Closes the build-deferred.md:118 note. |
| F9 | fixed | e2b39a53 | `EntityDamageListenerTest.comboDisabled_trackerWithoutTheSwitch_keepsTheCoreGate`: red by assertion (tracker called; 0 stars instead of 1). `HeatWantedTrackerTest.appliesComboSwitch_isTrue`: compile red. | New `default boolean appliesComboSwitch()` (false) and `WantedKillTrackers.routesKills(comboEnabled)`. The three kill routes in `EntityDamageListener` use `routesKills(Settings.isWantedKillComboEnabled())`. `HeatWantedTracker` answers true, so 0.15 behaviour is unchanged. A 0.13 jar's tracker answers false and keeps the core gate, as on a 2.0 host. The existing `comboDisabled_activeTracker_stillGetsTheKill` now stubs `appliesComboSwitch` to true. |
| F10 | fixed (docs) | 68f6d107 | n/a (docs) | `migration-0.15.0.md` sections 1 and 8, and both CHANGELOG files: cops-n-crooks still declares `Plugins: [Bartizan]`, so without Bartizan the whole chase is skipped. Civilians still loads, but civilian kills raise no stars. |
| F11 | fixed (docs; code follows the PLAN ruling) | 68f6d107 | n/a (docs) | PLAN Task 15 and CONTRACTS C17 rule that `Shots_Fired` stays a direct `sayAs` and that a squad-gap drop is accepted, so the code is unchanged. Corrected `migration-0.15.0.md`, `developer/configuration.md:437` and `features/cops-n-crooks.md`: only the two regroup lines bypass the gaps. `Shots_Fired` can be dropped inside them while the silent sighting always lands; add it to `Radio.Priority` if it must always be heard. |
| F13 | fixed (docs) | 68f6d107 | n/a (docs) | CHANGELOG.md and .bbcode.txt: "a broken formula warns once and charges 15% of the wallet". `migration-0.15.0.md`: the death bill's fallback is `balance * 0.15`. |
| F14 | fixed | 447c3d79 | `JailIntakeServiceTest.zeroStarsNoCrimes_chargesNoSheet`: red by assertion (finePaid 200 instead of null). | `chargeSheet` returns 0 when `wantedLevel <= 0 && crimes.isEmpty()`, so an admin `/glw jail throw` of an innocent player costs nothing and adds no time. Documented in `features/jail-detainment.md` and the migration guide. Contract gap in C15, filled here. |
| F15 | fixed (tests) | 87ef637f | New `EvasionModuleConfigTest` (installEvasion installs the decay policy and an AI-tick hook that ticks the clock; `@PostConstruct` present) and `HeatModuleConfigTest.registerAttackedHook_isAPostConstruct`. Red against a mutant with both `@PostConstruct` annotations removed. | Production code unchanged; it was already correct. |
| F16 | fixed (tests) | 743d7415 | `killWithTimerOn_addsKillEachNotoriety_andStartsTheTimer`, `killEach_overKillMaximum_isSkipped`, `cancelledBountyEvent_addsNothing`. Red against a mutant with the `Kill.Maximum` skip removed, the cancel return removed and `start(true)`. | WB-14 now has covering tests; record them in the WB-14 row. |
| F17 | fixed (tests) | 87ef637f | `CopGroupSquadTest.regroupDisabled_neverRegroups` and `CopManagerSquadTest.regroup_holdsWhileOnlyOneOfTwoCopsHasArrived`. Red against mutants (`!r.enabled()` removed; `arrived > 0`). | Production code unchanged. The CopRadioTest disabled case was skipped: `CopRadio` reaches the switch only through `CopGroup.shouldRegroup`, which the new test covers. The RETURNING and other-world exclusions in `isArrived` are still untested. |
| F18 | fixed (tests) | 87ef637f | `WantedHudListenerTest.starCardOff_suppressorAnswersFalse_soTheChatLineReturns` and `starCardSwitch_isReadPerCall`. Red against a `() -> true` mutant. | Production code unchanged. |

## Disputed

**F3: a lethal first hit on a cop gives 2 stars.** I am certain this is designed behaviour, not a defect:

- CONTRACTS C6 (lines 191-192) and C12 publish `Assault_Cop` (cop-attacked hook, every hit, deduplicated per attacker and victim
  for `Assault_Repeat_Seconds`) and `Kill_Cop` (kill path) as separate crimes. No line says a lethal hit counts once.
- PLAN Risk 7 counts the wanted-start seed and hitting a cop as a sighting on purpose, so the seen multiplier is intended.
  Risk 8 accepts that streak and seen multipliers stack to several stars, and says tuning is config-only.
- The proposed fix would make cop kills inconsistent. A cop killed after any earlier hit already ends at 2 stars on the shipped
  weights:
  - Within the 10 s streak window: Assault 100 (1 star), then Kill 150 x 1.5 streak x 1.5 seen = 337.5, total 437.5.
  - After the window: 100 + 150 x 1.5 = 325.
  - A one-shot kill (375 or 437.5, depending on listener order) lands at the same 2 stars.

  Skipping the assault on the lethal hit would make a one-shot kill (150, 1 star) cheaper than a two-hit kill. Absorbing the
  assault into the kill would change the two-hit case as well. "Kill_Cop alone = 1 star" is never reachable in play, because the
  lethal hit is itself an assault.
- The spec Done-when "Hit a cop once and the heat ledger gives you a star" holds (100, 1 star).
- If the owner wants cop kills at 1 star, the fix is a weight or threshold change in `npc/wanted.yml`. No code change is needed.

## New bugs (for `triage/<slug>.txt`; this report does not edit the docket)

Rows to add for the fixed findings that are not in the docket: F6 (self-kill claim), F2 (self-posted takedown), F5 (bounty_posters
overflow), F7 (login ledger wipe), F4 (reload freezes the evasion HUD), F14 (innocent jail-throw fine), F9 (stale 2.0 jar ignores
Kill_Combo.Enable). The commits and tests are listed above. F8, F14 and F16 also appear in `reviews/build-deferred.md`
(lines 118, 233, 254).

Found while fixing:

- **N1 - BountySetCommand keeps the poster's money when the bounty event is cancelled** (`wanted-bounty-combat`, P2).
  `gangland-impl/.../command/sub/bounty/BountySetCommand.java:~115-143`. The sender's `withdrawAmount(value)` runs before
  `callEvent(UserBountyEvent)`. When a listener cancels the event, `addBounty` is skipped, so the money is neither booked nor
  refunded. Impact: money loss, needing a third-party listener. Fix: call the event before the withdraw, or refund on cancel.
- **N2 - an accomplice's token bounty still makes a kill crime-free** (`wanted-bounty-combat`, P2, owner decision). This is what
  remains of F2. `Bounty.Minimum` ships at 0 (`settings.yml:254`), so a friend who is not an ally can post $0.01 on the victim and
  the killer's kill becomes a crime-free takedown (EntityDamageListener `byOthers.signum() > 0`). Fix direction: ship a
  non-trivial `Bounty.Minimum`, or require the escrow from others to reach a floor before the exemption applies.
- **N3 - Keystone's legacy MySQL dialect maps `Types.LONGVARCHAR` to `VARCHAR(255)`** (Keystone, `KS-` row, P3).
  `Keystone keystone-persistence .../database/type/MySQL.java:~292-302` (`getStringDataType`: no LONGVARCHAR or TEXT case, falls to
  `default -> "VARCHAR(255)"`). This matters only for the legacy, no-backend `RepositoryRegistry.createTables` path and the legacy
  `Table` ALTER helpers; Gangland always connects a `DatabaseBackend`, so the new TEXT column is created correctly. Fix upstream:
  `case Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB -> "TEXT"`.
