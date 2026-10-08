# 0.16.1 T-187: escape keeps the search, bounty announcement and HUD; death ends it

Lane: `E:/Programming/java/wt/cnc161-search`, branch `cnc-0.16.1-search`. Base HEAD `2a2369f4`, red commit `b0a1a724`.

## Owner report and ruling

Owner report (0.16.0 playtest, docket T-187): "So when they keep searching for me, even after the wanted stars dropped why I don't have feedback."

Owner ruling (2026-10-08, binding): "I would say that you don't stop searching. You have to say that there is a bounty on you if you escaped. But if you were killed then it stops all together. There needs to be feedback for that bounty on the screen somewhere. A hud would look nice."

## What was found about bounties

- An escape added no bounty. The only server-made bounty is in `EntityDamageListener.handleWanted`, which calls `bounty.addNotoriety(bounty.getAutoBountyIncrease(userLevel, wantedLevel))`.
- `getAutoBountyIncrease` and `addNotoriety` live in gangland-core `core/bounty/Bounty.java`. Cops-n-crooks calls them directly, so no gangland-api change was needed.
- The shown amount is `user.getBounty().getAmount()` (posted escrow plus notoriety), read live on each HUD tick. Formatted with `Settings.getMoneySymbol()` and `Settings.formatAmount`.
- No second money system was added.

## Change

- Escape is detected once, at `WantedLevelChangeEvent` with `cause == EVASION` and `newLevel == 0`. `PostEscapeSearch.isEscape(cause)` is the single predicate every consumer uses.
- `PostEscapeListener.begin` starts the search once per spell and adds the auto-bounty notoriety once. The bounty timer is not started.
- Death (DEATH), arrest (ARREST, via a new `events/police/ArrestedEvent` fired after the wanted clear in `JailIntakeService.admit`), and every other non-escape end route through `CopManager.onWantedEnd(player, cause)`. That stands the squad down, clears targeting, removes the cop-attacker lock and despawns the group.
- Escape keeps the squad, sends no Stand_Down, and re-engages returning cops through a shared `reengageReturning` helper.
- A searched target is never cuffed: `CuffingBehavior.tick` sends the cop to PURSUING through a `Predicate<UUID>` supplied by `CopBehaviorFactory`.
- Re-wanted ends the search silently and `WantedHud.show()` takes the bar back to the red SEEN bar.
- Quit calls `endAll`, which fixes the orphaned group and cops (docket CJ-15).
- `aiTick` breaks when its group is replaced mid-tick (a kill clears the list), fixing a ConcurrentModificationException.
- `EvasionClock` is unchanged. `EvasionState` in gangland-api is unchanged.
- Docs: `documentation/features/wanted-bounty.md` and `documentation/developer/configuration.md` updated.

## HUD and config keys (module YAML, defaults)

`copsncrooks/wanted.yml`, under `Wanted.Post_Escape`:

- `Enable: true`
- `Search_Seconds: 120` (proposed, owner to confirm)
- `Announce: true`
- `Bounty.Enable: true`
- `Bounty.Bar_Color: "YELLOW"`

Note: the brief said `GOLD`. `BarColor` has no GOLD constant, so the shipped default is YELLOW, and an unknown value falls back to YELLOW.

`copsncrooks/wanted_messages.yml`:

- `Hud.Bar.Bounty: "&6&lBOUNTY &e%money_symbol%%amount% &7· &cCops still looking"`
- `Hud.Bar.Bounty_None: "&7Cops still looking"`
- `Hud.Announce.Bounty`, `No_Bounty`, `Gave_Up: "&7The cops gave up the search"`, `Gave_Up_Bounty`

Each key has a matching `WantedMessages` enum entry with its shipped fallback.

Java config: `PostEscapeSettings` record (new, with `DEFAULT`), loaded beside `ChaseConfig`. `ChaseConfig` and `HudSettings` constructors were not changed.

## Tests and red proof

Red commit `b0a1a724` ("0.16.1 T-187: red tests"). Targeted run on that commit: 124 tests, 10 failing, all on missing behaviour:

- `WantedHudListenerTest.lastStarDropByEvasion_sendsTheEscapeCard_thenHides` (flipped from the pinned version): `NeverWantedButInvoked`, `bar.removeAll()` still runs on escape.
- `WantedHudListenerTest.bountyBar_expiry_removesBar_andSaysGaveUp`: bar removed on escape.
- `WantedHudListenerTest.reWanted_takesBarBackFromBounty`: same cause.
- `PostEscapeListenerTest.evasionLastStar_beginsSearch_addsNotorietyOnce`: `bounty.addNotoriety(250)` not invoked.
- `CopManagerSquadTest.escapeWantedEnd_keepsGroup_andSkipsStandDown`: Stand_Down still sent.
- `CopManagerSquadTest.deathWantedEnd_despawnsSquad_andClearsAttackerLock`: group not despawned.
- `CopManagerSquadTest.arrestWantedEnd_despawnsSquad_andClearsCopAttacker`: group not despawned.
- `CopListenerQuitTest.quit_despawnsGroup`: group stays after quit.
- `PursuingBehaviorTest.searchingTarget_isNotCuffed`: CUFFING still happens.
- `WantedTargetingManagerTest.searchingPlayer_isFound_withWantedFalse`: searching player not found.

Guards that stayed green: `PostEscapeListenerTest.evasionNonZeroDrop_beginsNothing`, all of `EvasionClockTest` (including `lastStarDrop_firesNoEvadedAfterTheChaseEnds`), `CopManagerSquadTest.wantedEnd_saysStandDown` and `wantedEnd_calledTwice_saysStandDownOnce`.

Added in the green pass (on top of the red set):

- `CuffingBehaviorTest.searchedTarget_isNeverCuffed` (red verified: fails when the guard is disabled).
- `JailIntakeServiceTest.admit_firesArrestedEvent_afterTheWantedClear`.
- `CopListenerArrestTest.arrested_endsTheSquad` (new file).
- `WantedHudListenerTest.arrestedWhileSearched_hidesTheBountyBar_andEndsTheSearch`.
- `WantedHudListenerTest.escapeWithNoSearch_showsNoBountyBar`.
- `CopManagerSquadTest.killingHit_endsSquadMidTick_withoutConcurrentModification` (red verified: CME with the break removed).
- `CopManagerSquadTest.escapeWantedEnd_returningCop_isPulledBackIntoTheSearch`.
- `PostEscapeSettingsTest` (new file).

Pinned assertion for escape in `lastStarDropByEvasion` is flipped to assert no bar removal and a bounty title, as the brief required.

## Counts

Final cops-n-crooks module run, `mvn -q -o -pl gangland-features/cops-n-crooks -am test` on this worktree: 104 suites, 1043 tests, 0 failures, 0 errors, 0 skipped (read from surefire XML reports).

## Follow-ups and owner decisions

- Search_Seconds 120 is a proposed default; owner to confirm.
- DECAY and BRIBE are not escape (bought off or nothing to search). ADMIN and SIGN end the search. Each can be changed in `PostEscapeSearch.isEscape`.
- Contact during the search: currently a sighting only, not a re-raise to one star. Owner decision.
- The bar does not show on rejoin (the search has ended). Owner decision.
- Notoriety added on escape is collectable by a killer when `Pay_Notoriety` is true (`EntityDamageListener`, about lines 165-205).
- An admin clear of a 0-star searched player via `GanglandWantedClearContract` fires no event, so the search runs to its timer. Not one of the ruling's stop cases; owner call.
- The HUD expiry test assumes the bounty clock advances with the HUD beat (300 beats of 10 ticks = 150 s). If production uses wall-clock time, the test needs a clock seam.
- The "AI task still running" half of the escape test is not asserted; there is no seam for the AI task.
- The docket row T-187 and CJ-15 need their status recorded in the docket database (`bugs` collection) and the docket rebuilt. Not done in this lane.
- Changing `Bar_Color` default from the brief's GOLD to YELLOW is a deliberate deviation (BarColor has no GOLD).
