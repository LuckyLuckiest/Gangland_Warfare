# Review - spec coverage (0.16 PLAN / CONTRACTS / CONSTRAINTS vs SPEC-0.16.md)

Reviewer lens: every 0.16 card, Done-when sentence, owner ruling and decision is built by a named task with a test that would
catch its absence; no 0.17+ creep; 0.15.2 overlap coherent. Code checked on master 629af929 and the 0.15.2 worktree (276cbbcb).

Coverage summary: all nine cards, the three foundations, W1/W2/W3/O1/O2/O4/D4/K1/A1 and risk 11 map to a task. No 0.17+ scope
creep found (breaker = stored-only, restricted = tag only, no patrols/scanner/record/bag/per-crime sheet). Defects below.

## Critical
1. **T7 breaks an unowned test's compile.** C5 gives `CopRadio` a new `PlaceNames` constructor parameter, but
   `CNC test .../npc/police/radio/CopRadioRolesTest.java` also calls `new CopRadio(` and is not in T7's file list (only CopRadioTest).
   Fix: keep the old constructor delegating with an inert `new PlaceNames()`, or add CopRadioRolesTest to T7's OWN list.

## Important
2. **Hand-off bias and seed are read at ARRIVAL, not at dispatch.** T11 step 2 calls `spawnUnit(..., group.biasAt(now))` and seeds
   only "when biasAt(now) is set" at takeDue time; the bias lives Bias_Seconds 10, but a station unit arrives after ETA (up to 40 s,
   plus breather). With any station farther than 100 blocks the units spawn with no "ahead" preference and no last-known seed, so
   card 2.3 ("each new squad is seeded with the last-known sighting", "spots ahead") and Done-when "Outrun the squad and the next
   units come from ahead of you" only hold in the ring/ETA-0 case. `biasActive_newUnitsAreSeededWithTipOff` and
   `CopSpawnManagerUnitTest` (no bias case at all) cannot catch it. Fix: capture the bias in `PendingUnit` at enqueue (or keep it
   until the biased units spawn); add `CopSpawnManagerUnitTest.activeBias_ringSpotIsAhead` and a CopManagerDispatchTest case with
   ETA > Bias_Seconds; add `bias={b}` to the C8 `UNIT` debug line so S6 can grade "ahead".
3. **W3 rejoin grace becomes free search time.** R20 makes a non-empty queue a live cop, and R21 gives a RESTORE start no seed plus a
   15 s grace. EvasionClock.tick (0.15.2 :115-130) then starts a search at the player's position at once and counts it down with no
   cop in the world: a rejoining 1-star player (Seconds_To_Drop 10) loses the star before the first unit exists. Contradicts W3
   ("quitting is never the best way out"). No test covers it (`restoreStart_noSeed_andGraceDelay` checks only seed + delay). Fix:
   hold the clock during the rejoin grace (e.g. `group.markTipOff` for the grace or a hold flag EvasionClock honours) + test.
4. **Cold-trail quiet counts offline time.** C10 `quietMs = now - ledger.lastCrime(id).at()`; CrimeRecord stamps are wall-clock and
   HeatLedger is not cleared on quit, while 0.15.2 deliberately removes offline time from ChaseArc stamps (`restore`). Log out 4 min,
   rejoin: speed 2.0 at once and backup held - another W3 escape. Fix: subtract the arc's offline gap (or take max(ledger at,
   arc-shifted stamp)); add `QuietTrailTest.offlineTimeIsNotQuiet`.
5. **Risk-11 plumbing unspecified in C7.** The opening is built in `ChaseArcs.view(UUID, List<CrimeRecord>, AutoSettings)` (0.15.2
   ChaseArcs.java:128-145), which has no access to `HeatSettings.weightOf`. C7 says "count only records with heat.weightOf(...) >=
   rampageMinWeight" but not how weights reach `view`; changing its signature breaks `ChaseArcListenerTest:92` (not owned by T5) and
   the future planner caller. (Using `CrimeRecord.heat()` instead would be wrong: Car_Theft 60 x 1.5 seen = 90 >= 80.) Fix: name the
   seam in C7 (e.g. overload `view(id, crimes, settings, ToIntFunction<String> weightOf)` keeping the 3-arg form) and list every caller.
6. **C9 vs PLAN T15 on the POSTED pins, with CONTRACTS winning.** C9: "`recycles` ... `hasLiveCop` counts POSTED ... both pinned by T15
   tests"; PLAN T15: "pins land in Task 16". CONSTRAINTS says CONTRACTS wins over a task section, so T15 would edit
   CopManagerStuckTest (owned by T11 in the SAME wave) and EvasionClockTest -> merge conflict. Fix: change C9 to "pinned by T16".
7. **Owner rulings overridden without OWNER CHECK.** SPEC owner rulings: api 2.3 carries "exactly three additions" and every new knob
   goes in the module's own YAML, "never Messages/Settings". R2/R27/R36 add WantedCause.CONTACT, ten `Settings` getters and seven
   `Messages` constants to the api (irreversible within major 2). Probably necessary (sign, self-defence, death listeners are impl),
   but it contradicts two binding rulings; only R5 is tagged OWNER CHECK. Fix: tag R2/R27 OWNER CHECK, or read the core knobs through
   an impl-only holder so the api grows only by the three named surfaces + CONTACT.
8. **Setup wand: outline, list, tp, link untested.** Card: "a particle outline of the selection, and /cops setup list|remove|tp".
   T12 tests cover selection, save, remove-station and the listener only; nothing pins `list`, `tp`, `link` or that the outline task
   draws only for wand-holding admins and stops (S1 cannot see particles). Fix: add SetupListCommandTest/SetupTpCommandTest/
   SetupLinkCommandTest cases and an outline unit test (edges count, max 256, non-holder gets nothing).

## Minor
9. **R28 widens the card's gate.** Spec: contacts usable "only while the evasion state is SEARCHING or EVADED"; R28 also allows "no
   track". Mark it OWNER CHECK or keep the spec's set and refuse with no track while cops-n-crooks is loaded.
10. **Place-named radio is only two lines.** Card remaining work lists "place-named radio lines" with sighting examples ("heading north
    to the Docks", "Lost visual"); the plan adds `%place%` only to Dispatch_Wanted/Dispatch_En_Route/Post_Up/Eyes_On. Either make
    `%place%` available to every CopRadio line (cheap, in CopRadio) or record a ruling that CONTACT/CONTACT_LOST lines stay unnamed.
11. **Toggle/guard pins missing.** No `Hideout.Enable false` test in EvasionClockTest; no `BreatherSettings.breatherMs` by-star test
    (15 at 1 star .. 6 at 5, clamp); no pin that cold-trail hold never lowers the base refill (card guard); tier mix has no Enable key
    (rules: "every feature gets its own Enable key") - say so in a ruling or gate it on Breather/Dispatch.
12. **S14 is vacuous for the cash clause.** S14 runs with Death.Respawn.Enable true (downed path), where MoneyDropListener never fires
    (map death-hospital-heat drift 5), so "no cash item at the body" passes without T8. Add a Respawn-off vanilla-death row.
13. **R5 OWNER CHECK not gated.** T7 builds `cop_region` in cops-n-crooks in W2; put the owner answer on the orchestrator's pre-W2
    checklist.
14. **Phone page and placeholders unpinned.** T10 owns `GanglandPlaceholder` (`%gangland_contact_price%`, `%gangland_contact_cooldown%`)
    and the phone YAML but lists no test for them.

## 0.15.2 overlap check (passes)
- COLD_TRAIL ending vs card: separate block `Wanted.Evasion.Quiet_Speed`, card reads ledger not ChaseArc `quiet` - coherent.
- New WantedCause.CONTACT: ChaseLearner.outcome and StarCard.dropCard both have `default` branches (NaN / CARD_DROP_OTHER), so no
  exhaustive-switch compile break and contact clears are never learned.
- Risk 11: R38 + T5 pin (four Brandish not a rampage, one Kill_Cop is) present; see finding 5 for the plumbing gap.
