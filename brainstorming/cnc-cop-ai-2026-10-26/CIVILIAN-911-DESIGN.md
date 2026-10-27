# Civilian witness and 911: deterministic, visible design

Owner ruling R4, part 2 (2026-10-26): "the 911 calls or civilians' interaction with the player need to happen similarly
to what happens in the radio; at the end when you face a civilian or interact with them there needs to be visual
feedback and not randomness."

This is a design only. No source was edited. Base: master 0.21.0 `a14bcfbcb`. The graph (`graphify-out/graph.json`,
2026-10-26 11:20) is newer than the last commit (2026-10-24), so it is fresh.

## 1. The finding

**The witness decision has no dice.** `WitnessService.witness` (`gangland-civilians/.../witness/WitnessService.java:98`)
uses fixed rules to choose the caller: the **nearest** civilian within `Witness.Range` (24) that has line of sight. That
civilian must not be FLEEING, COMBAT, already CALLING, or the victim of a hit in the last 2 s. Landing is also
fixed: after `Call_Seconds` (8), unless the caller is hit, killed, scared, or the criminal logs out.

The only randomness in the flow is cosmetic:
- `SquadRadio.speak` picks a line from a pool with `rng` (`gangland-api/.../npc/radio/SquadRadio.java:200`).
- `CivilianIdleBehavior` wanders 70% of the time (`:75-76`), and `CivilianLookController.lookRandom` turns to a
  random yaw (`:97`).

**What the owner sees as randomness is missing feedback and hidden rules:**

| What the player experiences | Cause in code |
|---|---|
| "Someone called the cops, but nobody was looking at me." | `AbstractNpc.canSee` checks only distance and line of sight. It has no facing cone (Keystone `keystone-npc/.../AbstractNpc.java:502-505`). A civilian 24 blocks behind the player, facing away, is a valid witness. |
| "I don't know who is calling." | The only cues are glow (`CivilianCallingBehavior.java:25`) and one action-bar line, "A witness is calling 911! Stop them!", sent once and shown for 100 ticks (`WitnessService.java:65,127`). The call lasts 8 s, so the bar disappears 3 s before the call lands. The bar does not name the caller or show a countdown. |
| "I aimed at them and nothing happened," or "they ran off for no reason." | The scare check is a 12° cone within 16 blocks while armed (`WitnessAim`; `civilians.yml:487-490`). It is sampled **once per AI tick**, and `AI_Tick_Rate: 20` (`civilians.yml:112`) makes that once per second (`WitnessService.java:208`). Every cancel (SCARED, HIT, LOST) is silent: the civilian just runs. |
| "Right-clicking a civilian does nothing." | No civilian right-click handler exists. `NPCRightClickEvent` is handled only for traders, bankers, cars, detainment, rescue and handcuff bribes. |
| "The star came but nobody said anything." | `NineOneOneListener.java:52` speaks `Caller_911` with `sayFromLeader`. That returns false when the group has no leader or cop yet (`CopRadio.java:163-168`), which is exactly the case where the 911 commit **creates** the group. This is docket **T-345**. |
| "Punching someone unseen gets nothing, but killing them unseen gets an instant star." | `Wanted.Witnesses.Crimes` gates only `Car_Theft` and `Assault_Civilian` (`copsncrooks/wanted.yml:399-405`). `Kill_Civilian` is committed straight away (`CivilianDeathRewardListener.java:52`, `HeatWantedTracker.java:106`). |

So the design makes the existing rules visible. It documents the once-per-second aim sampling and adds an opt-in
facing cone (open question 2). It does not replace dice, because there are none.

## 2. The current flow, end to end

1. **Crime.** A crime fires `CrimeCommittedEvent`. Examples: `HotwireService.java:169` (Car_Theft), and
   `WitnessService.onPlayerHitCivilian` `:159` (Assault_Civilian, throttled per attacker and victim by
   `Assault_Repeat_Seconds`).
2. **Gate.** `WitnessGateListener.onCrime` (`cops-n-crooks/.../listener/wanted/WitnessGateListener.java:36`, HIGH)
   applies only when the crime is in `Wanted.Witnesses.Crimes`. It does nothing if a cop saw the crime, if
   `witnesses > 0`, or if the squad has a fresh sighting. Otherwise it cancels the event (`:47`), so no heat and no
   star, and calls `witnesses.witness(...)` (`:48`).
3. **Pick the caller.** `WitnessService.witness` (`:98`): candidates are in range, in the same world, not busy, not
   the victim, and pass `hooks.sight()` (that is `npc.canSee`). The nearest one calls. The others are kept as
   `watchers` for a hand-over. The criminal gets the action bar (`:127`).
4. **Calling state.** `startCall` (`:141`) attaches a `WitnessCall` and moves the civilian into CALLING.
   `CivilianCallingBehavior.onEnter` stops navigation, glows if `Caller_Glow`, and turns the civilian to face **away**
   from the criminal (`:19-31`).
5. **Each AI tick.** `tickCaller` (`:195`) checks, in order:
   - The criminal is offline: LOGOUT, and the civilian goes IDLE.
   - The caller is invalid: LOST.
   - The criminal is aiming at the caller: SCARED, and the caller flees.
   - Otherwise, once `Call_Seconds` have elapsed, it publishes `CivilianCalledInEvent` (`:215`) and the caller flees.
6. **Hit or death.**
   - A non-lethal hit cancels with HIT, and the caller flees. The hit also commits Assault_Civilian, so the remaining
     watchers can start a new call.
   - A lethal hit lets `WitnessListener.onCivilianDeath` call `onCallerDeath`, which hands the call to the first
     watcher who still sees the criminal (a fresh 8 s call), or cancels it as KILLED.
7. **The call lands.** `NineOneOneListener.onCalledIn` (`:38`):
   - `crimes.commit(..., seenByCop=false, witnesses=1)` (`:44`) passes the gate. This adds the star, and the
     single-site crime subtitle from R1 shows it.
   - `group.getSquad().reportSighting(at)` (`:49`). The R4 lane moves this to `reportTip` so it is last-known only;
     that is not redesigned here.
   - `sayFromLeader("Caller_911", ...)` (`:52`). It is silent when there is no cop yet (T-345).

## 3. Design: make it read like the radio

The radio works because every moment has a named speaker and a chat line in a fixed format. The 911 flow should do the
same. It can reuse what the civilians module already has: `CivilianService.shout(speaker, key, extras)`
(`CivilianService.java:280`) sends NPC speech through `SquadRadio`, with the text in `civilian_messages.yml` under
`Shouts.Lines.<key>`. The design adds no new delivery system.

### 3.1 The four visible moments

| Moment | Rule (no dice) | What the player sees and hears |
|---|---|---|
| **Notice** (call starts) | Today's rule: the nearest civilian in range with line of sight calls. An optional facing cone (`Witness.View_Angle`, full width, measured from the civilian's eye direction) is added. It ships at 360°, which keeps today's behaviour; see open question 2. | The caller snaps to **look at** the criminal for `Notice_Seconds` (1). A `Witness.Notice_Particle` (default `VILLAGER_ANGRY`, through XParticle) appears above its head. A chat line is spoken: `&c[911] &fPedestrian&7: &fHey! I saw that! I'm calling the cops!` (`Witness_Start`). |
| **Dialling** (`Call_Seconds`) | Fixed timer. | After Notice, the caller turns away and holds `Witness.Phone_Item` (default `CLOCK`, through XMaterial; the old main hand is restored when the call ends) and glows. The criminal's action bar is refreshed every AI tick with a countdown: `&c911 &fPedestrian &7is calling - &e5s`. `Witness_Calling` gains `%caller%` and `%seconds%`; the old text with no placeholders still works. |
| **Stopped** | Fixed triggers: hit (HIT), aimed at for one full AI tick (SCARED), or right-clicked by the criminal within `Intimidate_Range` (INTIMIDATED, new). A kill hands the call to the next watcher, as today, and that watcher plays its own **Notice**, so the hand-over is visible. | The caller drops the phone (main hand restored) and the glow stops. `Witness_Scared` is spoken: `&c[911] &fPedestrian&7: &fOkay, okay! I'm hanging up!`. The caller then flees, as today. The action bar shows `Witness_Stopped`: `&aCall stopped.` |
| **Connected** (lands) | Fixed timer. | `Witness_Landed` is spoken: `&c[911] &fPedestrian&7: &fYes, police? He's right here, near %place%!`. Then dispatch speaks `Caller_911` (§3.3), and R1 adds the star with its chat cause. The caller flees. |

Delivery: every Witness line names the **criminal as the addressee** (`SquadRadio.say(..., addressee=criminal, ...)`),
so he always hears it, even beyond `Shouts.Range`. Bystanders within `Shouts.Range` hear it too. The three line keys go in
`Shouts.Priority` (`civilians.yml:52`) so the per-player gap in `SquadRadio.speak` never drops them. The squad gap is
not a risk, because a lone civilian gets a fresh `NpcSquad` for each shout. Default pools have **one line each**, so the
words are fixed too. More lines in a pool bring back random wording but not random outcomes. Per-key radio cooldowns
default to 0 (`RadioSettings.cooldownFor`), so no `Cooldown_Ticks` entries are needed. `Shouts.Enabled: false` also
silences the 911 speech. The countdown action bar and the Stopped bar still show, so the criminal is never left with no
feedback.

### 3.2 The interaction rule ("when you face a civilian or interact with them")

- **Face or aim at it.** This keeps today's armed-aim scare, but it becomes visible: the Stopped line is spoken and the
  phone is dropped. Because `tickCaller` runs once per AI tick (1 s), the rule is "aim at the caller for one second".
  Document it as that in `Scare_Angle`'s YAML comment, instead of leaving it as a hidden per-second sample. Whether to
  widen `Scare_Angle` from 12° is an owner question.
- **Right-click it.** This is new: `listener/civilian/CivilianInteractListener` on `NPCRightClickEvent`. The class
  needs `@ListenerHandler(condition = "isCitizensAvailable")`, because the handler's parameter is a Citizens type (the
  same pattern as `TraderInteractListener` and `HandcuffBribeListener`).
  - If the clicked civilian is CALLING, the clicker is that call's criminal, and he is within `Intimidate_Range`, call
    `witnesses.intimidate(npc, player)`, which runs `cancelCall(INTIMIDATED)`. No weapon is needed: walking up and
    grabbing the phone always works.
  - `cancelCall` adds INTIMIDATED to the HIT/SCARED flee branch.
  - Right-clicking a civilian who is **not** calling does nothing new. Out of scope; see the open questions.

### 3.3 The cop side (cops-n-crooks)

- **`NineOneOneListener.onCalledIn`:** speak `Caller_911` with `copRadio.dispatch(group, criminal, "Caller_911",
  level, tier, extras)` and `Dispatch_Format` instead of `sayFromLeader`.
  - The line already starts "Dispatch: ..." (`cop_radio_messages.yml:307-308`).
  - `dispatch` needs no speaker, so it works when the commit has just created the group. This closes **T-345**.
  - `Caller_911` is already in `Cops.Radio.Priority` (`cops.yml:239`), so it is not lost behind `Dispatch_Wanted`
    from `CopManager:151`, which fires during the same commit.
  - Neither `%level%` nor `%tier%` appears in the `Caller_911` text, so pass `0, ""`. `MostWantedModuleConfig:57`
    already passes `""` for the tier.
- **`reportSighting` to `reportTip`** (`:49`) belongs to the R4 lane and flips `NineOneOneListenerTest:77` there. This
  design depends on it and does not duplicate it.
- The star, subtitle and chat cause come from R1's single crime-subtitle site. The 911 commit carries `witnesses=1`,
  so R1's cause line can say who reported the crime if the owner wants that (open question 7).

## 4. Config keys

All keys are module-owned. Block style, Capitalized_Underscore. A missing key keeps today's behaviour where one exists.

`gangland-civilians/src/main/resources/npc/civilians.yml`, `Witness:` block (read by `WitnessRules.parse`):

```yaml
Witness:
   # ... existing keys unchanged ...
   # Full width, in degrees, of the cone a civilian sees a crime in, measured from where it is looking.
   # 360 = sees all around (today's behaviour). Below 360, the civilian's idle head turns decide who witnesses.
   View_Angle: 360.0
   # Seconds the witness stares at the criminal before turning away to dial (counted inside Call_Seconds).
   Notice_Seconds: 1
   # Particle shown above the witness when it notices a crime (XParticle name); "" = none.
   Notice_Particle: VILLAGER_ANGRY
   # Item the caller holds while dialling (XMaterial name); "" = keep its hand as it is.
   Phone_Item: CLOCK
   # The criminal stops a call by right-clicking the caller within this many blocks; 0 = off.
   Intimidate_Range: 4.0
```

The same file, `Shouts:` block: add `Witness_Start`, `Witness_Scared` and `Witness_Landed` to `Priority`.

`gangland-civilians/src/main/resources/npc/civilian_messages.yml`, with the same keys in `_es`:

```yaml
Witness_Calling: "&c911 &f%caller% &7is calling - &e%seconds%s"
Witness_Stopped: "&aCall stopped."
Shouts:
   Witness_Format: "&c[911] &f%unit%&7: &f%line%"
   Lines:
      Witness_Start:
         - "Hey! I saw that! I'm calling the cops!"
      Witness_Scared:
         - "Okay, okay! I'm hanging up!"
      Witness_Landed:
         - "Yes, police? He's right here, near %place%!"
```

`%place%` is resolved in the civilians module from the `PlaceNames` bean. That bean is produced in core
(`gangland-impl/.../config/DataConfig.java:182`) and its type is in `gangland-api`, so civilians can inject it without
cops-n-crooks. If no place name resolves, the line falls back to "here". gangland-api is not
changed and `Host_Api` is not bumped.

## 5. Files to change

gangland-civilians:
- `witness/WitnessService.java`
  - `Hooks` gains a `speech` sink with the shape `(CivilianNpc speaker, Player criminal, String key, Map<String,String> extras)`.
    `Hooks.live` wires it to `CivilianService.shout(...)` with `Witness_Format` and the criminal as addressee.
  - `startCall` speaks `Witness_Start`.
  - `tickCaller` sends the countdown action bar when the whole number of seconds changes, and speaks `Witness_Landed`
    before it publishes the event.
  - `cancelCall` speaks `Witness_Scared` and sends `Witness_Stopped` for HIT, SCARED and INTIMIDATED.
  - New: `intimidate(CivilianNpc, Player)`.
  - `witness()` applies the view cone. This is a pure-geometry predicate beside `WitnessAim`, using the civilian's eye
    direction and `View_Angle`. At 360° (the default) it passes everything.
  - The constructor takes `PlaceNames` for `%place%`, and `CiviliansModuleConfig.witnessService` (`:143`) passes it.
- `witness/WitnessCall.java`: add the last-shown second and the saved main-hand item.
- `witness/WitnessRules.java`: add `viewAngle`, `noticeSeconds`, `noticeParticle`, `phoneItem` and `intimidateRange`,
  plus their parse entries.
- `witness/CancelReason.java`: add `INTIMIDATED`.
- `witness/WitnessTestKit.java` (test): add the `speech` fake to `Hooks`.
- `npc/state/behavior/CivilianCallingBehavior.java`
  - `onEnter`: look at the criminal, spawn the particle, swap in the phone.
  - `tick`: after `Notice_Seconds`, turn away (today's yaw formula).
  - `onExit`: restore the main hand.
- `npc/CivilianService.java`: add an overload `shout(speaker, key, formatKey, addressee, extras)`. The existing
  `shout` delegates to it with `"Format"` and `null`.
- `message/CivilianMessages.java`
  - `lines()` gets a `Witness_Format` case.
  - `DEFAULT_LINES` gets the three Witness pools.
  - `witnessCalling(caller, seconds)` and `witnessStopped()`.
- New: `listener/civilian/CivilianInteractListener.java`, conditional on Citizens.
- `resources/npc/civilians.yml`, `civilian_messages.yml`, `civilian_messages_es.yml`.

cops-n-crooks:
- `listener/wanted/NineOneOneListener.java`: send `Caller_911` through `dispatch` (T-345).

Docs: the civilians and witness section of the module docs and the CHANGELOG.

## 6. Tests

Write each test first and confirm it is red on unfixed code.

- **`WitnessServiceTest`**
  - `callStart_speaksWitnessStartToCriminal`: the `speech` hook gets `Witness_Start` with the criminal as addressee.
  - `scaredCaller_speaksScaredAndToldStopped`.
  - `landing_speaksLandedBeforeCalledInEvent`.
  - `countdown_actionBarShowsCallerAndSeconds`: sent once per whole second, ending at 1.
  - `intimidate_byCriminalInRange_cancelsAndFlees`.
  - `intimidate_byStranger_orOutOfRange_keepsCalling`.
  - `civilianFacingAway_isNotAWitness`: with `View_Angle: 180` set and the criminal behind the civilian, there are 0
    callers.
  - `viewAngle360_keepsOldBehaviour`. This guards `nearestSeeingCivilianCalls`.
  - `handOver_newCallerSpeaksStart`.
- **`CivilianCallingBehaviorTest`**: looks at the criminal on enter, turns away after `Notice_Seconds`, holds
  `Phone_Item` while calling, and gets its original main hand back on exit, including exit through `dropCall`.
- **`CivilianInteractListenerTest`**: right-click on a CALLING caller by its criminal leads to `intimidate`.
  Right-clicks on a non-caller or by another player are ignored. Add a bean-condition test for the
  `isCitizensAvailable` gate, the same way the trader listener is tested.
- **`WitnessRulesTest`**: the new keys parse, defaults hold when keys are missing, and `Phone_Item: ""` means none.
- **`CivilianMessagesWitnessTest`**: `Witness_Format`, the three pools, the `%caller%`/`%seconds%` fill, and the old
  placeholder-free `Witness_Calling` still renders. Run it for the `_es` file too.
  - **Pinned flip:** `:45` asserts the shipped default text "A witness is calling 911! Stop them!". That assertion
    flips to the new countdown text, and the spec-line check at `:49` changes with it.
- **`NineOneOneListenerTest`**: `landedCall_withNoCopYet_stillRadiosCaller911ViaDispatch` (red today, T-345), and
  `landedCall_usesDispatchFormat`.
- **`CiviliansModuleConfigWitnessTest`**: `Hooks.live` wires `speech` to `CivilianService`.

## 7. Docket

- **T-317** (`%suspect%` literal) is already fixed. No change.
- **T-345** (Caller_911 silent when the commit creates the group) is closed by §3.3. Record the commit and the
  `NineOneOneListenerTest` case when it lands.
- New rows go in `brainstorming/bug-docket-2026-09-06/triage/` in the `num~~tier~~title~~fix~~tests~~location~~observation~~sys`
  format. The last id allocated is T-829, so these are proposed as T-830 onward. Parallel lanes may allocate too, so
  the orchestrator should assign the final numbers. (Filed 2026-10-27 as T-442 facing cone, T-443 feedback, T-444 scare
  sampling, T-445 right-click, T-446 Kill_Civilian gate; the lane's further rows are T-447..T-462.)
  - `civilians-traders-shops.txt`:
    - P2: Witness has no facing cone; a civilian facing away calls 911 (`AbstractNpc.canSee` is distance plus line of
      sight). The `View_Angle` key is added, but whether a narrower cone becomes the default waits on open question 2.
    - P2: The 911 call gives no named or timed feedback. The action bar lasts 5 s, the call lasts 8 s, and cancels are
      silent.
    - P3: The scare is sampled once per AI tick (1 s) and is undocumented.
    - P3: There is no right-click interaction with a calling civilian.
  - `cops-detainment-jail.txt`:
    - P3: `Kill_Civilian` is not witness-gated while `Assault_Civilian` is. An unseen kill stars at once and an unseen
      punch does not. Owner question 1; no change until it is ruled.
- No KS- row is needed. Keystone `canSee` stays as it is, and the cone is a Gangland-side predicate.

## 8. Open questions for the owner

1. **Should an unseen kill wait for a witness?** Add `Kill_Civilian` (and perhaps `Store_Robbery`) to
   `Wanted.Witnesses.Crimes`, so every crime against civilians needs someone to see it. Today an unseen kill stars at
   once.
2. **How wide is the view cone?** It ships at 360°, which is today's behaviour: civilians see all around. A narrower
   cone, such as 180°, makes "they had their back to me" true. The trade-off is that idle civilians turn their heads
   at random (`CivilianLookController.lookRandom`, `CivilianIdleBehavior`). Below 360°, whether the nearest civilian
   witnesses a crime would depend on that random glance. The glance is visible, but it is chance. Choose one:
   - keep 360°;
   - go to 180° and accept the visible chance;
   - go to 180° and make witnesses face the noise of the crime first. That is more work.
3. **Can an unarmed right-click stop a call?** Proposed: yes, within 4 blocks. Alternatives: armed only, or a
   right-click costs money as a bribe, like the handcuff bribe.
4. **Who can intimidate?** Only the reported criminal (proposed), or his gang mates as well?
5. **Which way does the caller face while dialling?** Proposed: it looks at the criminal for 1 s, then turns away to
   hide the phone (today it only turns away). Or it keeps facing him the whole time.
6. **Who hears the 911 speech?** Proposed: the criminal and bystanders within `Shouts.Range`. The alternative is the
   criminal only.
7. **Should R1's chat cause name the 911 call?** For example: "★ Car theft (reported by a witness)".
8. **Should a scared or intimidated civilian be blocked from calling again for a while?** Today it can call again as
   soon as it stops fleeing. The suggested key is `Witness.Scared_Cooldown_Seconds`.
9. **Should `Scare_Angle` stay at 12°?** At 16 blocks a 12° cone is narrow; 20° is easier to hit on purpose.
10. **Should right-clicking a civilian who is not calling do anything?** For example, a "Don't shoot!" cower line when
    the player is armed. This is not proposed now (YAGNI). Add it if the owner wants civilians to react to every
    interaction, not only to callers.
