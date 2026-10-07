# Gameplay + UX adversarial review - gang and turf roadmap (PLAN.md, roadmap.json)

Reviewer lens: (a) brand-new player, (b) gang leader, (c) griefer with alts, (d) server admin. Planning-only wave, 2026-09-30.
Verified against code before judging: `CaptureService.isCapturable/classify/tickIdle/tickContestingOwned` (turf
`capture/CaptureService.java`), `TurfIncomeDistributor.distribute` (`task/TurfIncomeDistributor.java` L58-75),
`GangPresenceTracker.heartbeat` + `InactivityReleaseTask.run` (`task/`), `GangCreateCommand` (fee `Settings.getGangCreateFee`,
default `Create_Cost` 100000, `Member.gangJoinDateLong` already set on create), turf defaults in `settings.yml` L635-670
(`Income_Interval_Minutes` 10, `Duration_Seconds` 180, `Cooldown_Minutes` 15, `Post_Logoff_Protection_Minutes` 10).
Pixel widths computed with the vanilla font table (6 px caps/digits/`-#+`, `I`=4, space=4, `.,:!`=2, `l`=3, `t`=4, `f`/`k`=5).

## Verdict: PASS_WITH_FIXES

The architecture is right (owners-online window, plurality start, guards on the bar, one reason everywhere, six-pixel glyph class).
It is not safe to build W2 as written: the default rule plus offline income and XP makes "log off and hold" the dominant
strategy, the shield can be opened by an infiltrator, and the map legend as drawn does not fit a chat line (B8). All fixes are small
edits to plan text or one-line gates; none changes the wave structure. W0 and W1 (except the map render rules, B5/B6) are not blocked.

## 1. Walkthroughs

### (a) Brand-new player
- Minute 0: `/glw gang` onboarding (P2.5) is good: what a gang is, the cost, two buttons. But the cost is `Create_Cost` 100000 by
  default, so a newcomer's real path is Open gang -> `gang join`, and the plan gives Open gangs no safeguard (see B2).
- `/glw map` for a gangless player on a young server is 41x7 of dark-grey `-`. Nothing tells them where the nearest turf is (the
  "nearest turf" line exists only in the HUD, which a gangless player does not get by default per D12). FIX-S2.
- Gangless players are silently ignored by `classify` (`user.hasGang()` false -> `continue`). The enter title for a gangless
  player must say "join a gang to capture turf" or they stand in a turf believing they are capturing. FIX-S2.
- Two words for one idea: glossary defines "Shielded" (cannot be attacked, with reason) and then "Safe for 12m" (just changed
  hands) as a separate word. A newcomer cannot tell the difference and neither can the map colour (only shielded has one). FIX-B6.
- No in-game place to read the rules. Each surface explains one turf; nothing prints the five sentences with the live numbers.
  FIX-S8 (`/glw turf rules`, text from the same YAML the messages use).

### (b) Gang leader
- Under `OWNERS_ONLINE` the leader learns that the safest thing is to be offline. Income keeps paying offline (no online gate in
  `distribute`), XP will too. A leader who logs in for 30 s every 9 days beats the 10-day `InactivityReleaseTask` (any member's
  heartbeat refreshes `lastMemberOnlineAt`, including an alt). That is not "fights happen with owners present"; it is a
  risk-free annuity. FIX-B1.
- Logging in is punished: the shield drops the instant the first member is online, attackers already camping the border are
  "present" (15 s rule satisfied), and with N attackers the bar fills in `180/N` s (10 attackers = 18 s, 5 = 36 s). The alert
  reaches the owner as the bar is already half full; guards deploy on start (wave of 3, cooldown 20 s). FIX-B3.
- "Officer can buy guards" spends the shared bank, re-checked on click (good), but there is no cap on reserve size. An infiltrated
  or rogue officer drains the bank into guards that cannot be sold back. FIX-S7.
- The leader wants to know why the turf they own is shown gold or dark red: the gold override replaces the relation colour, so
  "my turf under attack" and "rival turf under attack" are the same gold. FIX-B6.

### (c) Griefer / exploiter with alts
See section 3 (exploit matrix). Headline: infiltrate an Open gang to open its shield; hold turf offline forever; solo alt
gangs level up passively and lift their own cap; stall a contest indefinitely with two AFK accounts.

### (d) Server admin
- Rules are spread across `settings.yml` (`Turf.Capture.*`, including `Post_Logoff_Protection_Minutes`, which the new
  `OWNERS_ONLINE` grace reuses) and five new module YAMLs (`turf_rules`, `turf_map`, `turf_messages`, `turf_alerts`,
  `gang_rules`) plus the existing `turf_npcs`/`turf_powerups`. One rule (the 10-minute grace) lives in two files. FIX-S8.
- D9-B (income 100 -> 250 per 10 min) is 250 x 144 = 36,000 per turf per day, all payable while the owners are offline,
  against a 100,000 gang fee and 1,000 guards. On any server with a few turfs that is a large faucet with the only sink being
  guards and boosts. Numbers are tuning, but the plan recommends B without naming the sink. FIX-B1 (activity gate) is the
  balancing lever; the doc must state expected faucet per turf per day.
- Low-population servers: `OWNERS_ONLINE` means nothing can be attacked while owners sleep, so a 5-gang server may see no
  turf change for days. The plan's answer (`OWNERS_OFFLINE`, one line) is right and should be documented as the small-server
  setting in the migration doc's first paragraph.

## 2. One-sentence test

| Mechanic | Sentence | Pass? |
|---|---|---|
| Capture window | You can attack a turf while its owners are online; it is shielded ten minutes after the last one leaves. | Yes, but the tail is exposure, not protection (attackable for 10 min after logout). Say "until 10 minutes after". |
| Presence 15 s | You count after 15 s inside. | Yes for attackers. Wrong for defenders (B3). |
| Plurality start | The bar starts when your gang has more people inside than everyone else. | Ambiguous: "everyone else" = sum or biggest single rival? Sum stalls every three-way fight; the bar math treats rival gangs as bystanders. Define: start needs attackers > owners+allies inside and > the largest other single gang. |
| Guards count | Each living guard holds back one attacker, up to 4. | Yes. |
| Turf cap | You hold up to Base + level turfs, max 6. | Yes as a sentence, but see B4 (cap grows passively). |
| Shielded vs Safe | (two words) | No. Collapse to Shielded + reason (B6). |
| Over cap / pays nothing | Turfs beyond your cap pay nothing. | Yes. |
| Gang XP | Holding turf earns gang XP. | Yes, but the XP -> level curve is not specified anywhere (Gang uses `Level.getExperience()`); state days-to-level-1 at 12 XP per payout. |
| Wave/Leash/Stand-down knobs | (admin only) | Fine, keep out of player text. |

## 3. Exploit matrix

| Exploit | Plan's answer | Holds? | Fix |
|---|---|---|---|
| Offline raid | `OWNERS_ONLINE` shield | Yes | - |
| Offline hoarding (log off, keep paying, keep XP) | 10-day inactivity release | No: one login per 9 days, any member, resets it; payouts have no online gate | B1 |
| Infiltrator/alt joins an Open gang to open its shield or keep it open | "open-join gangs are opt-in" | No: `lastMemberOnlineAt` is gang-level and refreshed by any member, AFK included (`GangPresenceTracker.heartbeat`, `GangPresenceListener`) | B2 |
| Login snipe (camp shielded turf, hit on first login) | none | No | B3 |
| Zerg capture faster than response | none | No: rate = net x 100/180 per s, unbounded in N | B3 |
| Alt padding of head count | 15 s presence | Partly: 15 s is trivial for alts; real bound is guard cap and gang fee (100k). Acceptable | note |
| Alt blocker (rival alt inside blocks start) | strict plurality | Yes once "everyone else" is defined | B7 |
| Capture stalling (attacker + ally/defender AFK, net 0) | none | No: bar freezes, contest lock survives logouts, third gangs are bystanders, turf is locked | B5 |
| Ally swap (ally stands inside as defender, abandons alliance, is already "present") | none (`isAlly` symmetric fix only) | No: presence timestamp is entry-based, not relation-based | S6 |
| Turf cap bypass via alt gangs | cap per gang | Partly: fee is the only barrier; solo alt gangs also level passively | B4 |
| Income farming via alt gangs | hold-only XP "unfarmable" | Wrong: hold-only XP accrues offline, so passive farming is exactly what it rewards | B4 |
| Guard farm (kill guards for bounty) | bounty funded by owner spend, half back | Yes (net loss for the owner; laundering at 50 % loss is not worth it) | - |
| Spawn camping | guards spawn at farthest post out of line of sight | Mostly; no fallback when no post is out of sight (small turfs) and posts are deterministic | S7 |
| Officer drains bank into guards | rank gate | Rank yes, bound no | S7 |
| Rival intel from hover (`no Rats online 14m`) | none | Leaks another gang's schedule to raiders | S9 |
| Timezone dominance | window rule | Accepted design; document | - |

## 4. Blocking issues (fold into PLAN.md before the named wave starts)

**B1. Offline income and XP make holding risk-free.** `TurfIncomeDistributor.distribute` pays every owned turf with no
activity check; P4.4 adds XP to the same payout; `Capture_Window: OWNERS_ONLINE` shields the turf while offline. The
inactivity release (10 days, any member login resets) is declared "load-bearing anti-hoarding" but is defeated by a 30 s login
per 9 days. *Fix (W2-N, one comparison in `distribute`, plus a W0-E doc line):* a turf pays income and XP only if the owner
gang had a (qualifying, see B2) member online within `Income_Idle_Hours` (default 24); otherwise it shows `dormant - pays
nothing` on the map and info card. Cut `Inactivity_Release_Days` default to 4. State the expected faucet per turf per day next
to D9 (250/10 min = 36,000/day online-active).

**B2. The shield can be opened or held by an infiltrator.** `owner.getLastMemberOnlineAt()` is refreshed by any member, and P4.5
makes Open gangs joinable by anyone. An alt joins a victim Open gang, logs in, and every turf of that gang is attackable while
the real members sleep (or stays permanently attackable). The plan dropped the join-age filter as "new machinery", but
`Member.gangJoinDateLong` already exists (`GangCreateCommand` sets it). *Fix (W1-X/W2-R):* `GangPresenceListener`/heartbeat only
refresh `lastMemberOnlineAt` for a member whose join age >= `Window_Min_Member_Age_Hours` (24, `turf_rules.yml`); the same
qualification is used by B1. No new table. A gang younger than the threshold counts every member as qualifying (or the founder always qualifies), otherwise a fresh gang never refreshes the timestamp and is uncapturable for 24 h, a churnable alt-gang immunity window. W2-R must confirm `gang join` and invite-accept also set `gangJoinDateLong` (verified only on the `GangCreateCommand` path).

**B3. Presence is not defence: login snipe and unbounded capture speed.** (i) Defenders must count immediately; only attackers
need the 15 s presence (today's `classify` change would apply the entry-age filter to both, so owners logging in inside the
turf are ignored for 15 s while campers already count). (ii) Clamp the bar rate: `Min_Capture_Seconds` 60 (net clamped to 3 at
the 180 s default) so no zerg fills a bar faster than a response can form. (iii) `Wake_Up_Shield_Seconds` 120: the turf stays
shielded for two minutes after the first owner logs in, with the alert "Rats are camping Docks; shield ends in 1:40". Sentence
stays one line: "It can be attacked while its owners are online, after a short wake-up." These are three lines inside `CaptureEligibility`
and `classify`, decided before W2-R writes its table tests.

**B4. Passive level and cap growth contradict "unfarmable".** D2-A grows the cap with gang level, level comes from hold-only XP,
XP accrues offline (before B1), and a solo alt gang levels the same as a real one. Cap is per gang, so alt gangs multiply it.
*Fix:* choose D2-B: cap = `min(Ceiling, 1 + activeMembers/2)` with active = qualifying members seen in 7 days (B2), level stays
cosmetic and `addExperience`/`GangLevelUpEvent` become an optional later lane. If the owner prefers D2-A, XP must be gated by B1
and the level curve must be specified (currently absent). Either way the map header `Turfs 3/4` is unchanged.

**B5. Contest stalling has no answer.** Owned contests use `net = challengers - defenders`; at 0 the bar freezes, and the plan adds
a contest lock that survives logouts. An attacker and any counted defender (ally or alt) standing AFK inside pins the turf in
CONTESTING forever, blocks everyone else (bystanders), and never reaches COOLDOWN. *Fix (W2-R):* `Max_Contest_Minutes` 10; on
expiry the contest fails with a new `TurfCaptureFailedEvent.Reason.STALLED` (defenders hold, turf shielded for the normal
cooldown), notified by the same failure line (P4.1). While net is 0 for 30 s the bar decays at 1v0 rate.

**B6. Map colours: single channel, one unmapped state, and gold hides relation.** Numbers: green `#55FF55`, red `#FF5555`, gold
`#FFAA00` collapse under deuteranopia/protanopia; dark red `#AA0000` approaches dark grey `#555555` under protanopia. Relation
(own/ally/rival), attackable/shielded and under-attack are all colour-only, and the under-attack override replaces the relation
colour (in the sample frame the ally's turf turns gold, so the viewer cannot tell "my ally is hit" from "a rival is hit").
"Safe for 12m" and "over your cap" have no colour. *Fix (W1-M, fable review item):* keep relation colour always; carry state with
width-neutral formatting (italic = under attack, underline = shielded/cannot attack now for any reason; bold stays banned because
it adds 1 px). Delete the gold and dark-red rows. Legend states it in words. Define shielded as "cannot be attacked by you now
(owners offline, wake-up, just captured, over your cap)" so every reason maps to one style and one glossary word; drop "Safe for".
Verify italic/underline advance in the GUI-scale checklist.

**B7. Spec contradictions that W2-R would encode as tests.** (1) PLAN P2.2 and roadmap.json L182 give `shielded: Rats online 3m ago`;
under `OWNERS_ONLINE` a gang online 3 minutes ago is attackable. Correct string: `shielded: no Rats online (attackable when one logs in)`.
(2) Sample frame legend `Saints (rival, shielded 12m)`: the owners-online shield has no countdown; only the just-captured and
wake-up reasons do. (3) The frame's `KKK` gold sub-block ("columns 27-29 of rows 3-4", Docks East) sits inside a 9x4 Kings block (columns 25-33, rows 2-5, counted by script): consistent, but it shows that adjacent turfs of one owner render as one blob with hover as the only separator; add turf-boundary cues (alternate `-` gap cell or underline break) or say so in the legend. (4) Plurality definition (section 2 table).
(5) Cooldown default is 15 minutes (`Cooldown_Minutes 15`), the samples say 12 and 7; harmless, but the golden test must not hard-code them.

**B8. The legend does not fit a chat line (W1-M).** Measured with the vanilla table: header line 303 px (fits 320 px
default chat width, barely); legend line 1 (`V Vipers (you)  K Kings (ally)  R Rats (rival)  S Saints (rival, shielded 12m)`)
is 78 chars = 381 px; line 2 is 71 chars = 364 px. Both wrap, so the block is 12 lines, the header scrolls off the 10-line
unfocused chat, and a window with 6+ gangs cannot fit two lines at all. *Fix:* legend line <= 300 px, at most 3 gang entries per
line and `+N more: /glw map legend`; shorten state text; add a `TerritoryRasterTest` assertion `pixelWidth(line) <= 300` for header
and legend with a small width table; header becomes `Map - Turfs 3/4 - north is up` with coordinates and facing in the `+` hover.
(Blocking because W1-M's exit criterion "10 lines" is false as drawn.)

## 5. Map readability checklist

| Check | Result |
|---|---|
| Grid width | 41 x 6 px = 246 px, fits; every sample row is exactly 41 glyphs (verified by script); `+` at index 20 of row 4 as stated |
| Glyph class | Correct: no `.`, `!`, lowercase, bold |
| Letter stability | Weak: "allocated in ascending gang id" means walking one cell can hand your own gang's letter to a newly visible older gang (Vikings id 3 takes `V`, Vipers becomes `P`). FIX-S1: viewer's gang first, then allies, then ascending id |
| Vertical range | 7 rows x 16 = +/-56 blocks north-south vs +/-328 east-west; borders north/south are barely visible. Acceptable for 10 lines; consider `Height` 9 as an admin option and say so |
| Viewer cell | `+` hides the turf under you; put owner/state in the `+` hover |
| Bedrock/Geyser | Bedrock font is proportional, so columns misalign, not just hover. Legend covers colours, not alignment. Floodgate players should get the `turf list` text form (FIX-S11) |
| Hover/click | Sound (`TurfListCommand.sendRow` precedent) |
| Auto map | 10 lines per cell change at >= 2 s = chat flood while sprinting (5.6 blocks/s crosses a 16-block cell every ~3 s), pushing gang chat and alerts out. FIX-S4 |
| Owners-only facts | Correct rule, keep |

## 6. Should-fix (before the wave that owns them)

- **S1** Letter allocation: viewer's gang first, then allies, then ascending id (W1-M).
- **S2** Empty-window header: `No turf in view. Nearest: Docks 320 m NE`; gangless enter title: `Join a gang to capture turf` (W1-M, W1-T).
- **S3** HUD default (D12): action bar line inside a turf only; the always-on `Nearest turf` line off by default (it competes
  with Bartizan's ammo bar and every foreground line); `map hud on` opts in.
- **S4** Auto map: send when the set of turfs in the window changes or the viewer enters/leaves a turf, min interval 5 s; not
  on every cell change.
- **S5** Message volume: alert lines limited to start, half guards, last stand, held/lost (4 kinds, not per-guard); the
  per-payout income line goes to the action bar (or every hour), not chat every 10 minutes for every member; allies get the start line only.
- **S6** Alliance abandon: in-memory 30 min mutual capture lock between the two gangs (cleared on restart), or reset presence
  timestamps on any relation change. One map entry, no schema.
- **S7** Guards: spawn fallback when no post is out of sight (farthest post, then delay 3 s); `Max_Reserve` per turf (12);
  purchases logged with the buyer's name.
- **S8** Admin surface: fold `Turf.Capture.*` into `turf_rules.yml` (legacy keys read as fallback for one release) so a rule lives
  in one file; merge `turf_alerts.yml` into `turf_messages.yml` (five new turf/gang YAMLs become three); add `/glw turf rules`
  printing the live one-sentence rules from that YAML.
- **S9** Hover intel: show `shielded`/`attackable` and the reason class to everyone; minutes-since-online only for the viewer's own gang.
- **S10** Prefer `Safe for` to disappear entirely (B6); otherwise it needs a colour and glossary line.
- **S11** Floodgate/Geyser players receive the text list instead of the grid (one branch in `MapCommand`).

## 7. Over-built

| Item | Recommendation |
|---|---|
| `TurfChunkIndex` (P2.1, section 5.3) | Cut. Admin-drawn turfs number in the dozens; `TurfManager.getTurfsInWorld` x 287 cells is trivial, and the raster hash cache already prevents recomputation. Add the index only if a measured server needs it. HUD nearest-turf is O(turfs) per player per second, also fine |
| `Capture_Window: ALWAYS` + `Offline_Duration_Multiplier` | Cut. Ships an untested playstyle, a hidden number and a third message vocabulary. Keep `OWNERS_ONLINE` (default) and `OWNERS_OFFLINE` (today's rule, migration and small-server switch) |
| Digest (P4.3): `turf_event_log` table, prune, listener | Defer. Under the default rule almost nothing happens to an offline gang (captures need owners online; only income, releases and contest-lock losses remain). The plan's own risk row concedes this. Ship the income line and an unread counter in `/glw turf`; add the table when playtests ask |
| Gang XP subsystem (P4.4) | Replace by D2-B (member-based cap) at W2; keep `GangLevelUpEvent` wiring as an optional later lane. Removes `addExperience`, level curve and passive-farm risk |
| 11 placeholder tokens (P2.3) | Ship name, owner, state, relation, nearest_name, nearest_distance (6); the rest on request |
| `map item` (W3-I) | Cut from the roadmap (low value, render cost, own cache); keep zoom, border, dynmap. Dynmap is the admin-facing win |
| W0 gating | The map depends only on W0-A (ownership events) and W0-B (lifecycle); gang and mail hardening (W0-C/D), docs, and the H12 acceptance do not block it. Run them alongside W1 so the first player-visible result does not wait for 35 closures and two gates |
| Five new YAMLs | See S8 |

## 8. What is right and should not change

- One vocabulary and one-channel-per-job (boss bar = a fight only).
- `CaptureEligibility` as one pure verdict shared by every surface; the biggest UX win in the plan.
- Strict-plurality start replacing "zero defenders, exactly one challenger" (closes the alt-blocker).
- Guards on the bar with a cap of 4, guards deploy after the start so they never block it, consume-after-spawn and refund.
- Owners-only hover facts; read-only click; no `PlayerMoveEvent`; 1 Hz task; run-length merge.
- `TurfCaptureNotifier` extension instead of an api seam (already global; verified in the plan).
- Dropped scope (declared raids, loss shield, member caps, `RadioVoice.audience`, score table) is correctly dropped.

## 9. Concrete edit list for PLAN.md and roadmap.json

1. P3.1 text: add `Wake_Up_Shield_Seconds` 120, `Window_Min_Member_Age_Hours` 24, and "attackable until 10 minutes after the last owner leaves".
2. P3.2 text: defenders count at once; attackers need `Presence_Seconds`; define plurality precisely; add `Min_Capture_Seconds` 60 and `Max_Contest_Minutes` 10 (`STALLED` reason).
3. P4.2/P4.4: income and XP gated by `Income_Idle_Hours` 24; D2 recommended option changed to B; D9 gets the faucet-per-day line; inactivity default 4 days.
4. P2.2, roadmap.json L182: replace the `shielded: Rats online 3m ago` example.
5. Section 5.1/5.2/5.4: relation colour always, state by italic/underline, shorter legend/header with pixel test, letter allocation viewer-first, empty-window nearest turf line, correct the `KKK` column claim.
6. Vocabulary table: delete "Safe for"; Shielded carries reasons.
7. Sections 4/7: mark `TurfChunkIndex`, `ALWAYS`, digest table, `map item`, `TurfMapLayer` as cut/deferred; W0 note that C/D/E/F run alongside W1.
8. Section 10: D2 recommendation B; D12 action-bar default limited to inside-turf.
9. Section 11: add risks "infiltrator opens shield (B2)", "stalled contest (B5)", "login snipe (B3)" with the mitigations above.
