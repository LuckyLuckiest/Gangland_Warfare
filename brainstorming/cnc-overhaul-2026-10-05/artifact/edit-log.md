# Edit log: overhaul-updated.html (0.15.0 shipped update, 2026-10-05)

Source of facts: `shipped-facts.md`. Original kept byte-identical in `overhaul-original.html`. Only `overhaul-updated.html` edited.

## 1. Masthead
- facts-line item 1: `Checked against <b>0.12.0</b> and the <b>0.13.0</b> branch` -> `Shipped <b>0.15.0</b> on top of <b>0.13.0</b>`.
- Counts unchanged (54 features, 24 new, 13 foundations, 6 releases): no feature or foundation was added or removed.
- Meta description: appended `0.15.0 “Lose them” shipped on 5 October 2026.`
- Legend "Status": unchanged.

## 2. #today
- Eyebrow: `Where 0.12.0 stands` -> `Where 0.12.0 stood (before 0.15)`.
- New `<div class="callout now">` "What changed since this plan was written", after the intro, before `ol.chase`: H13 merged (415f34a5), 0.15.0 merged (9d4776ad); one line each for step 1 (heat ledger replaces the kill combo, exemptions), step 3 (hit on a cop = Assault_Cop crime; one-shot cop kill = 2 stars), step 6 (evasion countdown, search zone, Repeating_Timer safety net, star drop free by default), step 7 (death bill falls back instead of throwing, clamped, Lose_Money false charges nothing), step 8 money (charge sheet, shortfall as time; bounties pay posted money only); closing line on steps 2/4/5 (HUD, regroup) and the "Also missing" items now fixed.
- The eight historical chase steps, "Also missing", "Already working" and the closing callout are untouched.
- "Added since 0.11.0": 9 new `0.15.0` rows (heat ledger, evasion, wanted HUD, charge sheet, safe money formulas, shots give you away, regroup, paid bounties, gangland-api 2.1 surface).

## 3. #versions version line
- Intro: `0.13.0 holds cop roles and medics while they're verified` -> `0.13.0 shipped cop roles and medics (merged 5 October 2026)`; `The overhaul now starts at 0.15, after the turf release it builds on.` -> owner shipped 0.15.0 first, 0.14.0 follows and rebases; API swap (0.15 = 2.1, Gang & Turf = 2.2); later numbers do not move (0.16/0.17/0.20 keep 2.3/2.4/2.5, 0.18/0.19 none). Per the facts file (section 6) the later numbers are definite and unchanged, so no card was renumbered.
- 0.13 card: `vt vt-verifying` -> `vt vt-shipped`; chip `st-v-verifying` "Verifying" -> `st-v-shipped` "Shipped"; scope `Branch npc-roles-medics, built and reviewed, awaiting live verification.` -> `Merged to master as 415f34a5 on 2026-10-05.`
- 0.13.x acceptance card: `vt-verifying` -> `vt-shipped`; chip "Verifying" -> `st-v-shipped` "Complete"; scope rewritten to what ran (R1-R4 in the sandbox harness, Paper 1.21.11, Citizens 2.0.43; became the 0.15 regression suite and pass on the 0.15 build; 0.13.0 baseline fails the LOS row; H13 merged 2026-10-05); dep chip `test-server bot` -> `sandbox bot harness`.
- 0.14 Gang & Turf card: stays `st-v-planned` "Planned"; scope now says it follows 0.15, W4 (not started) rebases onto 0.15 with expected conflicts in core wanted/EntityDamageListener, P6.4 already shipped and shrinks to a check, takes api 2.2; dep chip `gangland-api 2.1` -> `gangland-api 2.2`. Removed superseded text "If H13 is late ... 0.15.0 starts only after W4 has merged".
- 0.15 card: `vt vt-proposed vt-cnc` -> `vt vt-shipped vt-cnc`; chip `st-v-proposed` "This plan" -> `st-v-shipped` "Shipped"; scope adds paid bounties, master 9d4776ad on 2026-10-05, 1791 tests, sandbox pass line (R1-R4 + N1-N4) met; dep chips `Keystone 1.14.0`, `gangland-api 2.2` -> `Keystone 1.14.0`, `Bartizan 0.6.0` (new chip, the pin), `gangland-api 2.1`.
- 0.16-0.20 cards: unchanged (api numbers unchanged per facts section 6).

## 4. Keystone, Bartizan and API versions table
- Keystone 1.14.0: "Verifying" (`st-v-verifying`) -> "Shipped" (`st-v-shipped`); `Branch phase-h13-npc-roles; the floor for 0.15.` -> `On master as b818483; the floor for 0.15, which shipped on it with no Keystone change.`
- Bartizan 0.6.1: "Verifying" -> "Shipped"; `Patch release on feature-crit-sound for H13.` -> `Patch release for H13, on master as 6c8fca9. Gangland keeps its pin at 0.6.0, because WeaponShootEvent and Weapon.getCategory are unchanged in 0.6.1.`
- gangland-api 2.0: chip unchanged ("Shipped"); `Current API. On master and on the H13 branch; H13 adds no bump.` -> API through 0.13.0, seven modules still on Host_Api 2.0 and load on a 2.1 host.
- gangland-api 2.1: was "Planned" "Gang & Turf bump" -> now "Shipped" (`st-v-shipped`) "Crime + evasion surface, 0.15. CrimeService, Crimes, CrimeCommittedEvent, WantedEvasionStateEvent and EvasionState; GanglandApi.VERSION = "2.1"; cops-n-crooks and gangland-civilians declare Host_Api: 2.1".
- gangland-api 2.2: was "This plan" (`st-v-proposed`) "Crime + evasion surface" -> now "Planned" (`st-v-planned`) "Gang & Turf bump ... Was 2.1; moved up because 0.15 shipped first".
- 2.3 / 2.4 / 2.5 rows: unchanged (facts: 0.16 = 2.3, 0.17 = 2.4, 0.20 = 2.5 unchanged).

## 5. Dependency graph
- Nodes -> `s-built` (titles now say "Built"):
  - `e-wanted-seam`, `e-crime-events`, `e-bot-acceptance-scenarios`, `e-money-formulas`: `s-open` -> `s-built`; title `· Foundation · 0.15` -> `· Foundation · Built · 0.15`.
  - `f-heat`, `f-evasion`, `f-hud`: `s-open` -> `s-built`; title `To build` -> `Built`.
  - `n-shots-give-you-away`, `n-cnc-regroup`: `s-open` -> `s-built`; `To build` -> `Built`.
  - `f-fines`, `n-paid-bounties`: `s-partial` -> `s-built`; `Part built` -> `Built`.
- 0.15 band already `gband-built`: unchanged.
- Legend: added `Built (11)` (`sw-built`); `Part built (9)` -> `Part built (7)`; `To build (58)` -> `To build (49)`. Verified against node classes: 67 nodes = 11 built + 7 partial + 49 open.
- Figcaption: prefixed `Everything in the 0.15 column shipped in 0.15.0 on 5 October 2026.`

## 6. Feature write-ups (all seven: status chip -> `<span class="st st-built">Built</span>`)
- `f-heat`: "To build" (`st-open`) -> "Built". Added state-note "Shipped in 0.15.0" (HeatLedger/CrimeRecord/HeatListener/HeatWantedTracker, `npc/wanted.yml` keys + defaults, the five crimes actually published, dormant weights, civilian kill counts once, self-defence exemption (fixed 30 s, no key, simpler form of the 0.16 rules), turf-defender exemption via `exemptsKill` (P6.4 now a check), max-star/self-kill rules, Heat.Enable false, KillComboEvent silent on default config, one-shot cop kill = 2 stars; tests ref).
- `n-cnc-regroup`: "To build" -> "Built". Added state-note (classes, `npc/cops.yml` Regroup keys + radio cooldowns, triggers, regroup grants its own backup ignoring the backup cooldown, push line only with Backup.Enabled and Extra_Cops > 0, end rule, N8; tests ref).
- `f-evasion`: "To build" -> "Built". Added state-note (classes, `npc/wanted.yml` Evasion keys + defaults, who owns decay and the cop-gap tick, reset/zone/recycle/reload rules, drops charged only with Take_Money.Enable, Hideout_Speed moved to 0.16 with the setup wand, R2 + N1; tests ref). Config sketch (with Hideout_Speed) left as written.
- `f-hud`: "To build" -> "Built". Added state-note (classes, `npc/wanted.yml` Hud switches + defaults, strings in `npc/wanted_messages.yml` incl. Unknown_Crime, bar colours, own stars only, title/siren, chat line suppression, compass, action bar untouched, card uses the tier's singular Display_Name, manual HUD checklist still open; tests ref).
- `n-shots-give-you-away`: "To build" -> "Built". Added state-note (ShotNoiseListener, `npc/cops.yml` Shot_Noise keys, throttle = Radio.Cooldowns.Shots_Fired (60), unlisted weapon types silent, nearest cop speaks, sighting always recorded, Plugins: [Bartizan], N5; tests ref).
- `f-fines`: "Part built" (`st-partial`) -> "Built". Replaced the superseded "Where it stands" note (it listed the three remaining pieces: make the star-drop charge a setting, the charge sheet at JailIntakeService.admit, and Lose_Money false paying players) with "Shipped in 0.15.0": star-drop charge keys + defaults, death bill fallback/clamp/Lose_Money, charge sheet keys + formula + extra-time rule + persisted columns, deviations (priced by stars until 0.16, module YAML not DetainmentCostsContract, no sheet on death or innocent jail throw, crime list chat-only), economy.md:127 docs follow-up, N2/N3/N3b/N7; tests ref.
- `n-paid-bounties`: "Part built" -> "Built". Replaced the superseded "Where it stands" note (posters only in memory, kill paid the whole server-made amount, doubling past the cap) with "Shipped in 0.15.0": ledger, `user.bounty_posters`, Pay_Notoriety false, Minimum 0, claim not a crime, legacy rows pay once, no doubling of posted money, allies never collect, deviations (self-posted bounty refunded but kill still a crime, cancelled event refunds, [BOUNTY] sign CLEAR still burns escrow WB-22, notoriety not cleared on death/arrest), N4; tests ref.
- Not touched (per instructions): `n-justified-kills` (0.16, "To build") although 0.15 shipped a simpler self-defence exemption and the posted-bounty takedown; flagged for the owner.

## 7. Foundations list
- `e-wanted-seam`, `e-crime-events`, `e-bot-acceptance-scenarios`, `e-money-formulas`: added `<span class="st st-built">Built</span>` in `fl-head` (previously no chip) and one "Built in 0.15.0." line each on where it lives (WantedCause/WantedStars/WantedDecayPolicy in gangland-core, WantedEvasionStateEvent in api 2.1 not 2.2; CrimeService/Crimes/CrimeCommittedEvent in api 2.1 + CopDeathEvent/KillComboEvent now fire; 15 scenarios in `acceptance/` + smoke row `cnc-015-boot`; MoneyFormula in gangland-core `core/money/`). Original plan paragraphs (including their "2.2" wording) kept.

## 8. Roadmap 0.15
- Added `<span class="st st-v-shipped">Shipped</span>` under the version/name.
- rel-deps: `gangland-api 2.2` -> `gangland-api 2.1`.
- rel-why: after "It starts only after Gang & Turf W4 has merged ..." added `(The owner chose to ship 0.15 first: it merged on 5 October 2026, before Gang & Turf, and W4 now rebases onto it.)`; `Owner decision needed before work starts (...)` -> appended `; decided 2026-10-05, see Decisions for you`.
- Done when: appended verdict `Verdict, 5 October 2026: the sandbox acceptance R1-R4 and N1-N8 pass (N5-N8 read by hand); the manual HUD checklist and the live-server boot are the remaining checks (deployed to the test server 2026-10-05; the cnc-015-boot result, 9 modules and 0 faults expected, is still to be recorded).` No deploy result was available (no report in the smoke reports folder, facts say "happening today").
- why-order paragraph: appended the same "owner chose to ship 0.15 first" note.

## 9. Guardrails
- Decisions for you: new first item "Decided 2026-10-05": owner took every recommended answer except the release order (0.15 shipped first); API numbers moved (0.15 = 2.1, Gang & Turf = 2.2, 0.16/0.17/0.20 keep 2.3/2.4/2.5). Followed by an `<ol class="backlog">` of open owner questions: WB-48 Bounty.Minimum 0; notoriety not cleared on death/arrest; one-shot cop kill = 2 stars; plus the fourth from facts section 7, the self-posted bounty rule.
- Rules for every feature, "Additive API": `(2.2 to 2.5, after Gang & Turf's 2.1)` -> `(2.1 shipped with 0.15, Gang & Turf takes 2.2, then 2.3 to 2.5)` (superseded numbering).
- The individual decision items (including the "gangland-api bump cadence" and "Release order" items) left as written; the new top item explains what changed.

## 10. Footer
- Appended `Updated 5 October 2026 after 0.15.0 shipped (master 9d4776ad).`

## Structural self-check (Python html.parser, original -> updated, open/close counts)
All tags balanced, 0 nesting errors in both. Deltas match the additions exactly:
- div +1 (callout), ul +1 (callout list), ol +1 (open questions), a +1 (link to #guardrails)
- li +20 (callout 5, since 9, graph legend 1, decided 1, open questions 4)
- p +12 (callout 2, foundations 4, new state-notes 5, decided 1; the two replaced notes net 0)
- b +15, span +38, code +128 (inline text in the new notes)
- section, article, svg, g, table, tr, td, details: unchanged.
- Graph: 67 nodes = 11 `s-built` / 7 `s-partial` / 49 `s-open`.
- Status chips, original -> updated: Built 1 -> 12, To build 46 -> 41, Part built 10 -> 8, Planned 3 -> 3, This plan 11 -> 9, Shipped 4 -> 10, Complete 0 -> 1, Verifying 4 -> 0.
- Line endings LF, as the original.

## Pass 2

Sources: `shipped-facts.md`, `acceptance/live-deploy-2026-10-05.md` (DEPLOYED_BOOT_OK), and the docket row WB-50 in `brainstorming/bug-docket-2026-09-06/bugs.json` (title only, to confirm what the fixed id covers). Pre-pass-2 copy kept in the session scratchpad for the structural diff. No new CSS classes.

### P2.1 Live deploy result
- Roadmap 0.15 Done when, verdict: `...pass (N5-N8 read by hand); the manual HUD checklist and the live-server boot are the remaining checks (deployed to the test server 2026-10-05; the <code>cnc-015-boot</code> result, 9 modules and 0 faults expected, is still to be recorded).` -> `...pass (N5-N8 read by hand). The same day the 0.15.0 build (core and 9 modules, on the Keystone 1.14.0 and Bartizan 0.6.1 master builds) was deployed to the owner's test server and booted clean: “Runtime modules: 9 loaded, 0 fault(s)”, with no errors from our plugins; the server was left stopped and the backup kept. The manual HUD checklist (boss bar, title flash, siren, zone ring and compass on a real client) is the one open check.` The `cnc-015-boot` code chip is gone: the deploy booted with its own script, not the smoke row, so the row is not claimed.
- 0.15 version card scope: appended `Deployed the same day to the owner's test server (core and 9 modules, Keystone 1.14.0 and Bartizan 0.6.1 master builds), it booted clean: “Runtime modules: 9 loaded, 0 fault(s)”, no errors from our plugins. The manual HUD checklist is the one open check.` Chip stays `st-v-shipped` "Shipped"; dep chips unchanged (Keystone 1.14.0, Bartizan 0.6.0 pin, gangland-api 2.1).

### P2.2 Money (#money)
- "What an admin can set" callout: new first line after the h3: `<b>0.15.0 shipped.</b> Wanted.Take_Money.Enable and Formula exist and the charge is off by default. The pre-0.15 workaround below is for servers still on 0.13.0 or earlier.` Intro and the rest of the callout unchanged.
- "Where money changes hands", Ships-in cells:
  - A star falls: `today (config); toggle + formula 0.15` -> `today (config); toggle + formula 0.15 (shipped)`
  - Busted: `0.15` -> `0.15 (shipped)`
  - Wasted: `today (config); safe formula 0.15; bill at respawn 0.16` -> `today (config); safe formula 0.15 (shipped); bill at respawn 0.16`
  - Dying with Lose_Money false: `0.15` -> `0.15 (shipped)`
  - Bounty claimed: `0.15` -> `0.15 (shipped)`
  - Post or cancel: `today; saved total 0.15` -> `today; saved total 0.15 (shipped)`
- Busted row, "In this plan" cell: the plan said "a price for each crime", which 0.15 did not ship, so one sentence appended (row text otherwise kept): `As shipped in 0.15.0 the sheet lists the chase's crimes but is priced min(10,000, 200 + 250 × stars at arrest) until per-crime prices arrive in 0.16; the shortfall is served as 0.1 s per unpaid dollar, rounded up, at most 600 s; and an arrest that commits on death gets no sheet, because until 0.16 that player pays the hospital bill instead.`
- Other 0.15 rows: no deviation in the facts file, text unchanged.

### P2.3 #today
- Also missing, item 1 (kills the only crime): appended `From 0.15.0 five crimes are published: Kill_Player, Kill_Civilian, Kill_Cop, Assault_Cop and Resisting_Arrest; the other seven weights (robbery, car theft and safe cracking among them) ship in npc/wanted.yml with no publisher yet, and gunfire reports a sighting but raises no star.`
- Item 2 (pedestrian star): appended `From 0.15.0 the pedestrian kill goes through the heat ledger as one Kill_Civilian crime, so it counts once (docket WB-50, fixed) and the star it gives has a decay clock.` WB-50 is in the facts file's fixed list; its docket title ("A civilian kill raises wanted twice") confirms it is this defect.
- Item 3 (no HUD): appended `0.15.0 shipped one: a star card title and siren on every new star (it replaces the chat line), a boss bar with your stars and countdown, a search-zone ring and an escape compass; the action bar is untouched.`
- Callout "The biggest lever is already built": original text kept, appended `0.15.0 wired it: the evasion clock, installed as the WantedDecayPolicy, counts down only while no squad sighting is younger than 3 s (Lost_Sight_Seconds), so staying unseen now drops stars.`

### P2.4 Self-defence (n-justified-kills)
- Status chip: `<span class="st st-open">To build</span>` -> `<span class="st st-partial">Part built</span>`. Ships in stays 0.16.
- New state-note "Where it stands" (before Builds on): 0.15.0 shipped the simpler self-defence rule (victim struck first, fixed 30 s window, PvP hits only, no config key; no Kill_Player crime, star or notoriety) and the posted-bounty takedown (only other players' escrow counts; a self-posted bounty is refunded but the kill stays a crime; a notoriety-only target takes the crime path). Remaining for 0.16: `Self_Defence_Window` and `Min_Damage` keys (the fixed window can be baited for 30 s), same-gang first-strike guard, per-pair cooldown. Still open: `Bounty.Minimum` ships 0 (WB-48, owner question). Ref: fight map in EntityDamageListener (leak fixed as WB-53, ea072adb); EntityDamageListenerTest.selfPostedBounty_killIsStillACrime, claim_paysPostedOnly. Cops and civilians are already outside the shipped rule (PvP only), so they are not listed as remaining; licensed hunters and on-duty cops stay "later" as the plan says.
- Graph node `n-justified-kills`: `s-open` -> `s-partial`; title `... · To build · 0.16` -> `... · Part built · 0.16`.
- Legend, recounted from node classes (67 nodes): `Built (11)` unchanged; `Part built (7)` -> `Part built (8)`; `To build (49)` -> `To build (48)`.

### P2.5 Roadmap 0.16 Done when
- Appended `“A rival shoots first and you kill them: no star” and “Claim a posted bounty: no star” already hold on 0.15.0, with a fixed 30 s first-strike window; 0.16 adds the tuning keys and the exploit guards.` (facts 3.1 self-defence exemption; 3.7 a claim is not a crime). Worded "hold" rather than "pass": the facts name no live scenario for these two.

### P2.6 Checker minors
- (a) Roadmap 0.15 rel-why: `This is the biggest lever and it is mostly built:` -> `Written before 0.15 shipped: this is the biggest lever and it is mostly built:`.
- (b) Foundations: e-wanted-seam `goes into gangland-api 2.2.` -> `goes into gangland-api 2.1.`; its Built line `went into gangland-api 2.1, not 2.2.` -> `went into gangland-api 2.1 (this plan first said 2.2).` (kept readable now that the plan line says 2.1); e-crime-events `a CrimeService in gangland-api 2.2.` -> `a CrimeService in gangland-api 2.1.` The guardrails "gangland-api bump cadence" decision item (`2.2 (0.15)` ...) left as written, as in pass 1 (a decision record, explained by the Decided item).
- (b) Footer: `Keystone references are to 1.13.0 and the 1.14.0 branch.` -> `Keystone references are to 1.13.0 and the 1.14.0 branch; 1.14.0 is now on master (b818483).`
- (c) why-order: removed `It waits on two things outside this plan: the H12 and H13 live acceptance on the 0.13.x track, so H13 can merge, and Gang & Turf wave W4, which owns the core wanted files 0.15 edits (0.14.0, or 0.14.1 if W4 splits off). In the event the owner chose to ship 0.15 first: H13 merged on 5 October 2026, 0.15.0 merged the same day ahead of Gang & Turf, and W4 rebases onto it.` -> `The owner chose to ship 0.15 first (2026-10-05): H13 merged that day, 0.15.0 merged the same day ahead of Gang & Turf, and W4, which owns the core wanted files 0.15 edits, rebases onto it.`

### P2.7 Star ladder (#ladder)
- Table note: appended `From 0.15.0 the “Search zone” and “Unseen time to drop” columns are live defaults in npc/wanted.yml (Wanted.Evasion.Search_Radius and Seconds_To_Drop).` Facts 3.2 confirm [40, 60, 90, 130, 180] and [10, 20, 30, 45, 60], matching the table; table cells unchanged.

### Structural self-check (Python html.parser, pass 1 -> pass 2)
- 0 nesting errors, no unclosed or unbalanced tags in either; LF line endings kept.
- Open-tag deltas: p +2 (callout state line, state-note), b +1, span +2 (state-note lbl + ref), code +13 (+14 added, -1 the removed `cnc-015-boot`). Everything else unchanged.
- Graph: 67 nodes = 11 `s-built` / 8 `s-partial` / 48 `s-open` (matches the legend).
- Status chips, pass 1 -> pass 2: Part built 8 -> 9, To build 41 -> 40; Built 12, Shipped 10, Complete 1, Planned 3, This plan 9 unchanged.
