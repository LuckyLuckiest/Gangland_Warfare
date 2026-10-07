# Gang and turf roadmap - final plan (Gangland 0.14.0)

Judge/synthesizer: `fable 5.1`, wave `gang-turf-wave-2026-09-30`, written 2026-09-30, **finalized 2026-09-30 after four
reviews** (`reviews/feasibility.md`, `reviews/gameplay-ux.md`, `reviews/delivery.md`, `reviews/completeness.md`; every
applied and rejected item is in section 14). Planning only; no product code.
Inputs: the three architect roadmaps (`plans/player-first.md`, `plans/mechanics-depth.md`, `plans/delivery-fit.md`), every
`census/*.md`, the reviews, and the verifications below. Graph checked fresh (`graphify-out/graph.json` 2026-09-29 23:08 is
newer than HEAD `ff9d813f` 23:05); oriented with `graphify explain CaptureService | TurfCaptureNotifier | GangPresenceTracker`,
`graphify query "Level getExperience" | "isGanglandOwnedPrefix"`, then raw reads of `CaptureService.isCapturable` (turf
`capture/CaptureService.java` L112-127), `TurfCaptureNotifier.notifyDefenders` (L77-89), `Gang.isAlly` (gang `gang/Gang.java`
L102-104), `GangDeleteCommand` payout lines (L186, L301), `Member.java` (`gangJoinDateLong` L28, set by `GangCreateCommand`
L125 and mail `GangInviteAcceptCommand` L181, persisted by `MemberTable` L42), `core/user/Level.java` (formula curve,
`Level(int, double)` constructor L31, `addExperience` L38), Keystone `ActionBarManager.sendBackground(Player, String, int)`
(L93-116, grace blocks strictly higher priorities), `settings.yml` L187-200 (user level base 1,000, formula
`base * level ^ 1.5`), every `module.yml`, and `git diff --name-only master...<h13 branch>` for the five H13 branches.

Path shorthand: `GT/` = `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/`,
`GG/` = `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/`,
`GM/` = `gangland-features/gangland-mail/src/main/java/org/luckyraven/gangland/mail/`,
`GC/` = `gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians/`.
Classes marked **NEW** do not exist yet.

---

## 1. Summary

**Backbone: `delivery-fit`.** Its four codebase corrections all verified against master, and they change the shape of the wave:

1. H13 (`npc-roles-medics` + `h13-roles/names/stuck/drop`) touches **no** file under `gangland-turf`, `gangland-gang` or
   `gangland-mail` (only `GC/npc/combat/BartizanNpcWeapons.java` and api `GanglandMoneyDropClassifier`). W0-W3 start from
   master `ff9d813f` **now**, in parallel with H13; only W4 waits.
2. `TurfCaptureNotifier.notifyDefenders` already iterates `Bukkit.getOnlinePlayers()` and messages every online owner-gang
   member **anywhere**. The `RadioVoice.audience(NpcSquad)` api seam that player-first and mechanics-depth both proposed is
   unnecessary: `GanglandApi.VERSION` stays `2.0` through W3.
3. The GR-02 note's "adjacent bug" (payout loops re-read the balance) is already fixed: `GangDeleteCommand` snapshots
   `gang.getEconomy().getAmount()` once at L186 into `payouts(pool, ...)` (L301). No triage row.
4. The vanilla font makes `.`, `!`, `i`, `l` about 2 px wide against 6 px for capitals, so the map stays in one glyph
   width class (`-`, `#`, `+`, `A-Z` minus `I`, digits `2-9`).

**Four review corrections that change the design (all verified):**

5. `plugin.yml` L20-28 declares `gangland.command.main: default: op` and Keystone `Argument.addPermission` registers every
   node as a plain `Permission` (Bukkit default OP), so on a fresh install a non-op player cannot run `/glw` at all. The W1
   kickoff adds a `permissions:` block with `default: true` for the player verbs (triage row CM-37).
6. `Gang.State` defaults to `OPEN` in the constructor (`Gang.java` L78), the table default (`GangTable.java` L33) and
   `parseState` (`GangRepository.java` L87/L92); wiring `gang join` for `OPEN` gangs would make every existing gang a walk-in
   gang. `INVITE` becomes the default everywhere with a one-shot migration (triage row GR-38).
7. `ActionBarManager.sendBackground(player, msg)` at priority 0 renews a 2.8-4 s grace each send during which
   higher-priority senders are **blocked**; a 1 Hz turf HUD line would starve Bartizan's ammo bar (`HudService.java` L104)
   and the gadget fuel/jetpack HUDs (priority 10). The HUD line sends at priority 9 every 2 s, inside a turf only.
8. Keystone `NpcSquad` is "a team of NPCs hunting one target" (`reportSightingBy` calls `loseContact` on any other target),
   and civilians create squads only in private `CivilianService.newSquad` where the shout listener and `keyOf` are wired. "One
   `NpcSquad` per turf" would thrash contact and mute shouts; the garrison keeps per-target squads **scoped per turf**
   (`SquadKey` gains a `scope`) and holds posts/leash/reserve in a turf-side `TurfGarrison` state object.

**Grafted from `player-first`:** the vocabulary glossary and the one-channel-per-job rule (boss bar = a fight only; the
"Territory of X" presence bar becomes an action-bar line), the ten-minute first-session script as the W1/W2 acceptance,
a pure `CaptureEligibility` verdict that every surface (enter title, info card, map hover, action bar, boss bar) shares,
the **`OWNERS_ONLINE` capture window** as the default rule, turf income feeding **gang XP** instead of a new score table,
Open/Invite recruiting, and a 41 x 7 window so header + grid + legend equal the unfocused chat height.

**Grafted from `mechanics-depth`:** the 15-second presence rule (attackers only) with a visible countdown, hold-only gang XP
gated by owner activity, the strict-plurality contest start, economy defaults as an owner decision, and the defender heat
exemption as an optional H13-wave lane. **Added by the gameplay review:** the wake-up shield, join-age qualification for
the owners-online timestamp, the activity gate on income and XP, a contest timeout, a minimum capture time.

**Dropped:** declared raids with fee/warning/window and their `RaidService` state machine, the persisted shield column,
member and ally caps, the loss shield, `RadioVoice.audience`, `TurfRadio`, a gang-owned news log, the `turf_member_score`
table and `/glw turf top`, lowercase/`.`/`!` map glyphs, alert tiers; **after review also:** `TurfChunkIndex` (dozens of
turfs x 287 cells is trivial), the `ALWAYS` capture window and its `Offline_Duration_Multiplier` (an untested playstyle
and a hidden number), the `turf_event_log` table (an in-memory ring buffer covers it under the default rule), the map item
(M6), the gold/dark-red colour overrides (colour-blind collapse; state moves to italic/underline), the word "Safe for"
(folded into "Shielded"), 5 of the 11 placeholders. Reasons in sections 13 and 14.

**Waves:** W0 foundations (35 open docket entries + the new rows TF-40..42 assigned; W0-B -> W0-A chain, C/D/E/F beside them)
-> W1 see the city (turf lanes after W0-A/B merge, gang lanes after W0-C merge: `/glw map`, HUD, explain, onboarding, ranks
preset, player permissions) -> W2 fair fights (capture window + wake-up, plurality, single bar, guards count, cap, alerts,
activity-gated income and XP, chat, open gangs) -> W3 guards that fight like a crew, in parallel with the map extras -> W4
garrison identity and roles (H13-gated, Keystone 1.14.0, api 2.1 or 2.2).

---

## 2. Assumptions

- Target **Gangland 0.14.0**, integration branch `gang-turf-territory` from master `ff9d813f`, worktree
  `E:/Programming/java/wt/gangland-0.14.0`, lane worktrees `E:/Programming/java/wt/gt14-<lane>` on branches `gt14-<lane>`.
  The `<revision>` bump to 0.14.0 is the W0-K orchestrator step (D11).
- `<keystone.version>` stays **1.13.0** for W0-W3. When H13 merges to master, master is merged into `gang-turf-territory`
  (expected conflicts: root `pom.xml` `<revision>` keep 0.14.0 and `keystone.version` take 1.14.0, `gangland-features/pom.xml`
  gains `gangland-healthbars`, docs). W4 starts only after the section 7.8 precondition checklist passes; a
  `git merge --no-commit master` dry run (then `--abort`) at the W1 and W3 gates surfaces conflicts early.
- No Keystone change in W0-W4. Promotion candidates (map painter, hold-post + leash primitive, out-of-sight spawn helper)
  are recorded, each triggered only by a second consumer, as a Keystone 1.15.0 lane with a phase doc. Never a local fork.
- `gangland-api`: **no new api surface** in W0-W3 (W0-D changes the body of one `Settings` method, no signature change,
  no `VERSION` bump). W4 adds one additive record (`NpcRole` **NEW**) and bumps `GanglandApi.VERSION` by one minor from
  whatever the merged master carries (`2.0 -> 2.1`; `2.2` if H13 has already bumped it for `FieldCareSettings`); modules
  that read the new members set their `Host_Api` to that value, the rest stay `2.0` (Keystone 1.9.2 minor floor).
- Module edges unchanged: turf `Depends: [civilians, gang]`, mail `Depends: [gang]`, cops `Depends: [turf, civilians]`
  + `Plugins: [Bartizan]`. Turf never depends on cops. Civilians stays gang-free (it gains a generic squad `scope` and an
  `extras` hook, never a gang type).
- New strings and knobs go in **module-owned YAML**: `turf/turf_map.yml`, `turf/turf_rules.yml` (every capture, cap, income
  and presence rule; `settings.yml` `Turf.Capture.*` read as a legacy fallback for one release), `turf/turf_messages.yml`
  (messages **and** alert lines), `gang/gang_rules.yml`, `mail/mail_rules.yml`; never in `Messages`/`Settings` (CLAUDE.md
  contract rule). `gangland-gang` and `gangland-mail` have no module-YAML plumbing today (resources hold only
  `commands.json`, `module.yml`, `module.properties`): **NEW** `GangModuleFileConfig`/`GangModuleFiles` (W1 kickoff) and
  `MailModuleFileConfig`/`MailModuleFiles` (W0-D) copy `TurfModuleFileConfig` (KERNEL-phase `FileHandler` with the module
  classloader), each with a `*ModuleConfigTest` proving the file is created from the module jar.
- Platform: Spigot only, compile floor Spigot 1.16.5 (`LivingEntity.hasLineOfSight(Location)` is Paper-only; use
  `World.rayTraceBlocks`), Java release 17, XSeries for drifting enums, method braces on their own lines.
- Tests per `documentation/TESTING.md`: red first against pre-fix code (a behaviour-preserving extraction gets
  characterization tests, green before and after), pinned tests flipped never deleted, `@TempDir(cleanup = CleanupMode.NEVER)`
  + release in `@AfterEach`, no pom edits except where a lane is named the pom owner (W3-W).
- Worktrees: `CLAUDE.md` (gitignored), `graphify-out/` (gitignored) and `brainstorming/**` (untracked) do **not** exist in a
  `git worktree`. Lanes run `graphify` against the main checkout by absolute path (read-only orientation on the master
  snapshot), reference `brainstorming/`, `CLAUDE.md` and `smoke.py` by their main-checkout absolute paths and never `git add`
  them; `graphify update . --force` runs only in the integration worktree after merges; CLAUDE.md edits are orchestrator work
  in the main checkout.
- Land model stays admin-drawn cuboid turfs (`CuboidRegion`); the map rasterizes them. Player-claimable chunks are out.

---

## 3. Player promise

1. Open `/glw map` and you always know who owns what around you, and what you can attack.
2. Every rule that stops you is one sentence, shown at the moment it applies, and it is the same sentence everywhere.
3. A turf can only be taken while its owners are around to defend it, after a short wake-up; guards hold it when you are
   outnumbered.
4. Your gang is told, anywhere, when a turf is hit, and what happened while you were away.
5. Finding or starting a gang takes one command, and a fresh gang works out of the box.

---

## 4. Pillars and features

Vocabulary (one word per idea, used in every message, lore, hover and doc; new strings in module YAML, existing
`GANG_*`/`TURF_*` constants reworded in place, no key renames):

| Say | Never say | Meaning |
|---|---|---|
| Turf | territory, region, zone, hood, claim | an admin-drawn named area (`Turf`, `CuboidRegion`) |
| Capture / capturing | contest, claim phase, consolidate, raid | filling the bar to take a turf |
| Under attack | contested | a rival is capturing your turf |
| Shielded | protected, safe, cooldown, post-logoff grace | cannot be attacked by you right now, always with the reason (`no Rats online`, `waking up 1:40`, `just captured 12m`, `over your cap`) and a time when one exists |
| Dormant | inactive | pays nothing: no qualifying owner online within `Income_Idle_Hours` |
| Guards | garrison, defenders, turf_defender | the NPCs bought for a turf |
| Quartermaster | powerup NPC | the turf's shop NPC |
| Bank / share | vault / contribution | `Gang.economy` / a member's money share of it |
| Gang XP / level | respect | progression fed by actively holding turf |
| Member / Officer / Owner | tail / head / node | the default ranks |

### P1 Trustworthy foundations (W0)

Why: every later wave listens to ownership events, reloads tasks and keys data by turf id. Today those lie.

| Id | Feature | Player view | Mechanics | Code | Docket |
|---|---|---|---|---|---|
| P1.1 | One ownership pipeline | Boss bars and guards always clean up when a turf changes hands, is deleted, is set by an admin, is auto-released, or its gang disbands | **NEW** `GT/capture/TurfOwnership` (`setOwner(turf, Integer, Cause)`, `cancelContest(turf, Reason)`); every writer routes through it (`CaptureService` L395, `TurfSetOwnerCommand` L78/L90, `InactivityReleaseTask` L38/L44, `TurfIncomeDistributor` L66); always fires `TurfOwnerChangedEvent` / `TurfCaptureFailedEvent(CANCELLED)`; delete cancels the contest first (TF-40); **NEW** `GT/listener/TurfGangDeleteListener` on `GangDeleteEvent`; lifecycle of `InactivityReleaseTask` and `TurfIncomeDistributor` (TF-07/TF-10) lands here, not in P1.2 | `CaptureService.complete/cancel`, `TurfSetOwnerCommand`, `TurfDeleteCommand`, `TurfCommand` (ctor wiring), `InactivityReleaseTask`, `TurfIncomeDistributor` | TF-01, 04, 07, 10, 13, 25, 28, TF-40 |
| P1.2 | Lifecycle and persistence | `/glw reload` leaves one task per job; ids never reused | `BeanLifecycle` on every other task-owning turf bean; boss-bar task handle; quit cleanup; first sweep at +5 min; `Number` casts; **NEW** `turf_sequence` high-water mark (`TurfCreateCommand` L103 id line); copy-on-write world lists; `turf_npcs.yml` reload; batch prune; pending purge (TF-42); no double `initialize()` (TF-41). `TurfContributionTickTask` is skipped: it dies in W2-P1 | `TurfModuleConfig` (existing beans), `task/{TurfLocationTracker,GangPresenceTracker}`, `TurfBossBarListener`, `TurfRepository`, `TurfManager`, `TurfCreateCommand` (id line), `TurfPowerupManager`, `ActiveBuffManager`, `TurfNpcsConfigLoader`, `TurfDefenderDeployer` (lifecycle only) | TF-05, 06, 08, 09, 14, 32, 33, 34, 36, TF-41, TF-42 |
| P1.3 | Gang hardening | No NPE path in gang commands; allies symmetric | `Gang.isAlly` checks both directions (one fix; `CaptureService.isOwnerAlly` and cops `listener/turf/TurfFriendlyFireListener.java` L103 follow via `GangMembershipInstaller.gangsAllied` L56); `hashCode`; null guards; transfer lock timer; node validation; **NEW** `GangCommandsJsonParityTest` (registered sub-commands vs `commands.json`; replaces the phantom "count 31" gate) | `GG/gang/**`, `GG/command/sub/gang/*`, `GG/listener/gang/*` | GR-10, 12, 15, 20, 28, 30, 32, 33, 34, 35, 36, TF-24 |
| P1.4 | Invite and mail hardening | Invites can be declined; stale invites expire; one bad mail row cannot kill the load | sync expiry sweep + cancel; accept rejects sibling invites; `Invite_Offline_Expiry_Hours` (72) in **NEW** `mail/mail_rules.yml` via **NEW** `MailModuleFileConfig`; **NEW** `GangInviteDeclineCommand`; per-row guard; `Display_Name_Char` default | `GM/**`, api `Settings.java` (one method body, no signature change) | GR-11, 14, 23, 24, 27, 31 |
| P1.5 | Faction-alert acceptance | (none) | Sandbox-accept the H12 faction-alert scenario (`migration-0.12.0.md` section 9, GL-4) on the test server via `smoke.py` + one owner step with two clients; report under `smoke/`. Gate for W3-A. Holds the test-server lock while it runs | test server | - |

### P2 See the city (W1)

Why: the owner's ask, and the one thing every census flagged as missing: nothing shows where turfs are.

| Id | Feature | Player view | Mechanics | Code | Docket |
|---|---|---|---|---|---|
| P2.1 | `/glw map` chat grid | 41 x 7 letters: you `+`, own turf green, ally aqua, rival red, unclaimed `#`; *italic* = under attack, underlined = shielded; hover = name/owner/state/reason/pays; click = info card; `+N more` opens the full legend | section 5 | **NEW** `GT/map/{TerritoryRaster, TurfMapRenderer, MapCommand, MapAutoTask, MapPrefsService}` + child stubs from the kickoff, `turf/turf_map.yml`; TF-27 COOLDOWN derived on load in `TurfRuntimeState` | TF-27 |
| P2.2 | One reason everywhere | Enter title, info card, map hover, action bar and boss bar all say the same `attackable` / `shielded: no Rats online (attackable when one logs in)` / `shielded: just captured 12m` / `shielded: over your cap` | **NEW** `GT/capture/CaptureEligibility` (pure verdict + reason + until) extracted from `isCapturable` with identical behaviour **in the W1 kickoff** (characterization tests); W1-X wires the consumers; the W2 rule changes land inside it | `TurfCaptureFeedbackListener`, `TurfInfoCommand` (id **and name** argument with completion), `TurfBossBarListener` (counts + time left) | - |
| P2.3 | HUD line and placeholders | Action bar while inside a turf: `Docks - Rats turf - attackable` (gang members, D12); gangless: `Docks - Rats turf - join a gang to capture`; the sidebar shows a turf line with zero Plaque change | **NEW** `GT/placeholder/TurfPlaceholderContribution` with six tokens in the existing dash style (`%gangland_turf-name|turf-owner|turf-state|turf-relation|turf-nearest-name|turf-nearest-distance%`; `turf-` added to `GanglandPlaceholder.isGanglandOwnedPrefix`, one orchestrator line in impl); **NEW** `GT/map/TurfHudTask` via `ActionBarManager.sendBackground(player, line, 9)` every 2 s, never "skip unchanged", inside a turf only; the outside `Nearest turf` line only behind `map hud on`; `map hud off` per player; retire `TurfPresenceBarListener` (D6); `FuelHoldDisplayListener` (gangland-item L104, priority 0) loses to the turf line by design, recorded | `TurfLocationTracker` cache | - |
| P2.4 | Turf clarity and admin | `turf list` paged by distance with direction; names not ids; `tp` admin-only; new turfs snap to chunks | TF-21 size limits (`Min_Side_Blocks` 16, `Max_Side_Blocks` 512 in `turf_rules.yml`, created by the W1 kickoff), TF-29, TF-30 one namespace `gangland.turf.admin`, TF-39 **NEW** `TurfRenameCommand`, **NEW** `CuboidRegion.snappedToChunks()` behind `Turf.Snap_To_Chunks` (new turfs only, D3; `TurfManager.findConflict` re-run on the snapped region), TF-23 wand/status strings into `turf/turf_messages.yml`, gangless enter title `Join a gang to capture turf` | `GT/command/*`, `GT/selection/*`, `GT/data/CuboidRegion`, `GT/listener/TurfActionBarListener` | TF-21 (limits half), 23 (wand/status half), 29, 30, 39 |
| P2.5 | Gang onboarding | Gangless `/glw gang` = what a gang is, the cost, `[Browse gangs] [Create a gang]`; `gang list` = all gangs; `gang info <name>` public card; name rules; a non-op player can run `/glw` at all | **NEW** `GangListCommand`, **NEW** `GangInfoCommand`, **NEW** `GangNameRules` (3-16, `[A-Za-z0-9_]`, colour codes stripped, duplicates always rejected) in `gang/gang_rules.yml` (**NEW** `GangModuleFileConfig` from the kickoff); `alliance_stat.yml` click -> `gang info`; `gang_stat.yml` becomes the stats page; one confirm idiom; `plugin.yml` `permissions:` block `default: true` for `gangland.command.main`, `.map`, `.gang` + player sub-verbs (info, list, create, join, deposit, chat, leave), `.turf` (+ info, list) - admin and destructive nodes stay OP (CM-37, **NEW** `PluginYmlPermissionsTest`) | `GG/command/sub/gang/*`, `gangland-impl/src/main/resources/inventory/*.yml`, `plugin.yml` (orchestrator) | GR-16, 17, 29, CM-37 |
| P2.6 | Ranks that work on day one | Fresh install: a member can deposit, an officer can invite and buy guards; a denied action names the rank that can | Seed `member -> officer -> owner` with default nodes on an **empty** rank table only (D13 A); register every `gangland.gang.*` node; `rank permission add` validates and completes; `RankParent` field semantics documented and both sides deleted | `GG/gang/rank/**`, `GG/gang/database/repositories/rank/**`, `GG/command/sub/rank/**` | GR-13, 21, 22, 37 |

### P3 One rule per fight (W2)

Why: today an owned turf is capturable only after the whole owner gang has been offline 10+ minutes and nobody is inside
(`CaptureService.isCapturable` L112-127, `tickIdle`). Fights never happen with owners present; the garrison kit is an
offline-defence toy; unclaimed capture has two stacked bars with different rules; one rival alt standing inside blocks every
start (L209 "exactly one challenger").

| Id | Feature | Player view | Mechanics | Code | Docket |
|---|---|---|---|---|---|
| P3.1 | Capture window | "You can attack a turf while its owners are online, after a short wake-up; it stays attackable until ten minutes after the last owner leaves." Shielded turfs are underlined on the map and say why on entry; `Rats are camping Docks - shield ends in 1:40` reaches the owner on login | `Capture_Window: OWNERS_ONLINE` (default, D1) flips `isCapturable`'s last line to `now - owner.getLastMemberOnlineAt() <= graceMs` (`GangPresenceTracker` already keeps that timestamp; `Post_Logoff_Protection_Minutes` moves to `turf_rules.yml` as `Shield_After_Logout_Minutes` 10 with a legacy read); `OWNERS_OFFLINE` (today's rule, the small-server switch) one line away; `ALWAYS` is **not shipped**. `Wake_Up_Shield_Seconds` 120: the turf stays shielded for two minutes after the owner gang goes from zero to one online member (the tracker records that transition in memory). **Qualifying members**: `GangPresenceListener`/heartbeat refresh `lastMemberOnlineAt` only for members whose `Member.getGangJoinDateLong()` age is >= `Window_Min_Member_Age_Hours` 24; a gang younger than that counts every member; `gang join` sets the join date like create/accept do. A running contest continues through logouts (contest lock, already true: nothing re-checks `isCapturable`). `Inactivity_Release_Days` default 4 becomes the anti-hoarding rule | `CaptureEligibility`, `CaptureSettings` (keys folded into `turf_rules.yml`), `TurfRules`, `GT/task/GangPresenceTracker`, `GT/listener/GangPresenceListener` | - |
| P3.2 | Plurality start, presence rule, no stalls | The bar starts when your gang has more people inside than the owners and their allies **and** more than any other single gang; attackers count after 15 s inside, alive (`You count in 12 s`); defenders count at once; a bar never fills faster than 60 s; a fight that goes nowhere for 10 minutes ends | Replace "exactly one challenger and zero defenders" with the strict plurality above (closes the alt blocker, TF-48); entry timestamp in `TurfLocationTracker`; `classify` skips young **attacker** entries only; reset on death, respawn, teleport; an entry is re-stamped when its relation to the turf owner changed since the last tick (an ally that abandons the alliance does not count instantly); `Min_Capture_Seconds` 60 clamps the net rate; `Max_Contest_Minutes` 10 fails the contest with **NEW** `TurfCaptureFailedEvent.Reason.STALLED` (defenders hold, normal cooldown) | `CaptureService.tickIdle/classify`, `TurfLocationTracker` | TF-11, TF-48 |
| P3.3 | One bar for every capture | Unclaimed and owned turfs fill the same bar with the same words: `Capturing Docks 34% - 3 vs 1 +2 guards - 1m50s` | Two-phase CLAIM/CONSOLIDATE folded into the owned tug-of-war (D5); dead `dominantGang(..., exclude)` removed; `Abandon_Grace_Seconds` honoured on owned turfs; third gang on an owned contest stays a bystander (TF-26, documented on the info card) | `CaptureService`, `CapturePhase` | TF-12, 26 |
| P3.4 | Guards count on the bar | "Every living guard on the turf holds back one attacker (up to 4)" | `classify` adds `TurfDefenderDeployer.aliveCount(turfId)` to defenders, capped by `Guard_Weight_Cap` (4); guards deploy on `TurfCaptureStartEvent` so they never block the start; `reinforced_defense` cut from the shipped catalogue, loader tolerant (D4) | `CaptureService`, `TurfDefenderDeployer` (read-only count), `turf_powerups.yml` | TF-02 (defence half) |
| P3.5 | Turf cap | `Turfs 3/4` in the map header, hub and info card; at the cap the entry line says so before a bar starts | `TurfRules.cap(gang)` = `min(Ceiling 6, Base 2 + Per_Level 1 * gang.getLevel().getLevelValue())` (D2 A), written as a pure function with its table test in the **W2 kickoff**, checked in `tickIdle` and re-checked at `complete`; over cap (members left): no forced release, oldest-captured `cap` turfs pay, the rest show `over cap - pays nothing` | `TurfRules`, `TurfIncomeDistributor` | TF-37 (cap half) |
| P3.6 | Quartermaster you can trust | Prices in YAML, catalogue pages, spend gated by rank, re-checked on every click, bounded; named NPC | TF-17 price in `turf_powerups.yml` + `garrison_discount` wired (TF-02 discount half), TF-18 paging, TF-19 re-validation, TF-23 panel strings, TF-38 name; `gangland.turf.upgrade` consumed through `GangPermissions.allows` (officer rank has it; TF-45); `Max_Reserve` 12 guards per turf; every purchase logged with the buyer's name (`@CustomLog` info + the gang ring buffer) | `GT/npc/view/**`, `TurfPowerupOpenContractImpl`, `TurfPowerupNpcCommand`, `PowerupRegistryLoader` | TF-02, 17, 18, 19, 23 (panel half), 38, TF-45 |

### P4 Told what happened (W2)

| Id | Feature | Player view | Mechanics | Code | Docket |
|---|---|---|---|---|---|
| P4.1 | Alerts anywhere | `Docks is under attack! 3 Rats from the north-east` ... `Half the guards at Docks are down` ... `Last stand at Docks` ... `Docks held` / `Docks lost to Rats` / `Docks: the attack stalled` (failure is no longer silent) | Extend `TurfCaptureNotifier` (already reaches online owner members anywhere); **four kinds only**: start, half guards, last stand, outcome (held/lost/stalled); head-count + `RadioSides.compass8(turfCentre, attackerCentroid)`; **NEW** `TurfDefenderDownEvent`; allies get the start line only; lines in `turf/turf_messages.yml` `Alerts` section; throttle 1 line per turf per 10 s per player | `GT/listener/TurfCaptureNotifier` | - |
| P4.2 | Income you can see | Action-bar line at payout: `Turf income +$300 (3 turfs) - bank $12,400`; a dormant gang sees `Docks: dormant - pays nothing` on the map and info card | `TurfIncomeDistributor.distribute` pays a turf only when `now - owner.getLastMemberOnlineAt() <= Income_Idle_Hours` 24 (the qualified timestamp of P3.1), one aggregated action-bar line (`sendBackground` priority 9, chat only in the ring buffer); `Max_Income_Multiplier` (2.0) caps the product; only turfs within the cap pay. Faucet at D9-B: 250 per 10 min = 36,000 per active turf per day; sinks are guards (1,000, `Max_Reserve` 12) and boosts | `TurfIncomeDistributor` | TF-37 |
| P4.3 | While you were away | On login: up to 5 lines since your last quit (income total, captures, losses, releases, stalls) with a `[map]` link; `/glw turf` shows the last 3 | **NEW** `GT/log/TurfEventBuffer`: per-gang in-memory ring buffer of the last 10 events + per-player last-quit stamp from `PlayerQuitEvent`; after a restart the join shows the last 3. No table: under `OWNERS_ONLINE` little happens to an offline gang; a persisted log waits for a playtest ask | `GT/log/**` NEW, **NEW** `TurfDigestListener`, `TurfCommand` | - |
| P4.4 | Gang XP and level | `+12 XP` on each active payout per turf held; level-up title to the gang; level raises the turf cap | Hold-only XP under the same activity gate as income (a dormant gang earns none, so alt gangs cannot farm it passively); `GangManager.addExperience(gang, amount)` (no-op stub in the W2 kickoff, body in W2-P1) fires `GangLevelUpEvent` (the inert `GangLevelMessageListener` starts firing); `Gang` builds its `Level` with `Gang_Level_Max` 20 and `Gang_Level_Base` 100 from `gang_rules.yml` (`Level(int, double)` + `setFormula`, formula `base * level ^ 1.5`), so one turf held actively (72 XP/day) reaches level 1 in about 1.4 days, level 2 in about 5, level 3 in about 12; `Member.contribution` = money share only, stored values reset once (D15); `TurfContributionTickTask`/`TurfContributionListener` deleted (TF-16 goes with them; W2-P1 also removes their references in `TurfCommand`/`TurfModuleConfig`); `GangBountyEvent` + listener deleted, `%gang_bounty%` returns 0 (D8) | `GG/gang/GangManager`, `GG/gang/Gang`, `GG/events/gang/*`, `GT/contribution/**` (deleted), `GT/task/TurfIncomeDistributor` (the XP call, W2-N) | GR-19, TF-16, TF-44 |
| P4.5 | Gang chat and open gangs | `/glw gang chat <msg>` or toggle; `gang join <name>` for Open gangs; `gang recruiting open|invite` | **NEW** `GangChatCommand` + listener (`AsyncPlayerChatEvent`, recipients via `GangMembership`, main-thread dispatch); `Gang.State` default **`INVITE`** in the constructor, `GangTable` default and `parseState` (null/unknown/`CLOSE` -> `INVITE`, never `OPEN`); one-shot migration `state IN ('OPEN','CLOSE') -> 'INVITE'` at load; only `gang recruiting open` makes a gang `OPEN`; `gang join` sets `gangJoinDateLong` (D7, GR-38) | `GG/command/sub/gang/*`, `GG/listener/gang/*`, `GG/gang/Gang`, `GG/gang/database/**` | GR-38 |

### P5 Guards that fight like a crew (W3, H13-independent)

| Id | Feature | Player view | Mechanics | Code | Docket |
|---|---|---|---|---|---|
| P5.1 | Per-turf squads, posts, leash | Guards hold the turf as a team, stop chasing across the map, walk back to their posts | Civilians (W3-A): `SquadKey(faction, scope, targetId)` with default scope `""`; **NEW** public `CivilianService.squadFor(CivilianNpc, LivingEntity, String scope)` creating through `newSquad` so the shout listener and `keyOf` stay wired; `alertFaction`/`recruit` treat `victim.getFactionSquads() != this` as "custom provider" and recruit only NPCs whose provider **and** scope match (also on `drainPendingRecruits`); `FactionSquads` gains `default Map<String, String> extras(NpcSquad)` returning `Map.of()`, merged by `FactionVoice.extras`. Turf (W3-B): **NEW** `GT/npc/defender/TurfGarrisonSquads implements FactionSquads` with scope `"turf:" + turfId`, set on each guard after spawn; **NEW** `GT/npc/defender/TurfGarrison` state object per turf (posts = 4 inset corners + surface centre, leash = region + `Leash_Margin` 16 in `TurfDefenderDeployer.tick`, reserve, wave timers); guards indexed by entity UUID (TF-35) | `GC/npc/CivilianService`, `GC/npc/FactionSquads`, `GC/npc/state/behavior/CivilianCombatBehavior`, `GT/npc/defender/**` | TF-35, TF-47 |
| P5.2 | Reserve and waves | Buying guards is a reserve, not a burn: first wave at attack start, top-ups after each guard falls; survivors and unspent stock return; nothing spent without Citizens | `Wave_Size` 3, `Max_Alive` 6, `Wave_Cooldown_Seconds` 20; spawn at the post farthest from the nearest attacker whose eye-to-post `World.rayTraceBlocks` is blocked; fallback when no post is hidden: farthest post after a 3 s delay; consume only after a successful spawn (TF-46); refund at end (D14 A); stand-down: survivors walk to a post and despawn after `Stand_Down_Seconds` (8) with a `Turf_Held`/`Turf_Lost` shout | `GarrisonDeployListener`, `GarrisonManager`, `turf/turf_npcs.yml` | TF-20, TF-46 |
| P5.3 | Gang-tagged guards | `[VIP] Guard 42`, `[VIP] Quartermaster` over the head and in shouts | Tag = the gang's 3-4 character short code (first letters of the resolved name, colour codes stripped), formatter tested against the 16-character limit, never `CIT-`; turf's `TurfGarrisonSquads.extras` supplies `%gang%`; `civilian_messages.yml` `Shouts.Format` gains `%gang%` with a `%faction%` fallback (W3-A) | `GT/npc/defender/*`, `GC/npc/CivilianService.FactionVoice`, `civilian_messages.yml` | - |
| P5.4 | Map everywhere (a la carte) | `/glw map zoom in|out`, `/glw map border` (10 s clipped outline), dynmap layer, `/glw map text` (list form, automatic for Floodgate UUIDs) | see section 5.5; the map item is cut | `GT/map/**`, `GT/task/TurfVisualization`, `gangland-turf/pom.xml` (W3-W: `dynmap-api` provided + repository) | TF-21 (viz half), TF-43 |

### P6 Garrison identity and roles (W4, needs H13 + Keystone 1.14.0)

| Id | Feature | Player view | Mechanics | Code | Docket |
|---|---|---|---|---|---|
| P6.1 | Roles | Shield guards up front, marksmen behind, the Quartermaster as commander; killing him breaks the line for 5 s | **NEW** `gangland-api/.../npc/NpcRole` record (api 2.1 or 2.2, read from the merged master first), cops `copsncrooks/npc/police/config/CopRole.java` (exists only on `h13-roles`) becomes an adapter, civilians `AI.Combat.Roles` overlay, turf `Garrison.Composition`; Keystone 1.14.0 `NpcFanPlacement`, `setLeaderPriority`, `setRangedBand`; no Medic until cops `GL-CARE` exists | api, cops, civilians (`listener/civilian/CivilianDamageListener`), turf | - |
| P6.2 | Health bars | See how close a guard is to dropping | Zero code: `gangland-healthbars` draws transient unprotected Citizens NPCs; verify the line layout with the P5.3 callsign | verify only | - |
| P6.3 | Guard bounty and stuck recycling | Killing guards pays half of what the owner paid; no guard frozen on a roof | **NEW** `TurfDefenderMoneyDropSource` (H13 `GanglandMoneyDropClassifier`, funded from the owner's spend, owner/ally kills excluded; turf sets `Host_Api` to the W4 api value because the classifier arrived without a bump); `AbstractNpc.millisUnreachable() > Recycle_Seconds` and out of attackers' view -> respawn at another post; **after W4-R** (shares `GT/npc/defender/**`) | `GT/npc/defender/*` | - |
| P6.4 | Defender heat exemption (optional, D10) | Kills you make defending your own turf during an attack do not raise your wanted level | `default boolean exemptsKill(Player, Entity)` on core `WantedKillTracker`, forwarded by `WantedKillTrackers`, consulted in impl `EntityDamageListener.handlePlayerKills` (L100), implemented by cops `KillComboWantedTracker` reading turf contest state through cops' legal `Depends: [turf]`; **after W4-R** (needs its `VERSION` bump and shares cops `module.yml`) | core, impl, cops | TF-49 |

---

## 5. The map

### 5.1 Decisions

| Question | Decision |
|---|---|
| Model | Admin-drawn cuboid turfs, rasterized onto chunk cells. `Turf.Snap_To_Chunks: true` for new turfs (D3). |
| Command | **`/glw map`**: a second `@CommandHandler` root in the turf module (**NEW** `GT/map/MapCommand`, beside `TurfCommand`; the gang module already registers `rank` beside `gang`). Sub-commands are hand-wired in the parent constructor (`TurfCommand` builds about fifteen children), so the **W1 kickoff registers every child as an empty stub** (`MapAutoCommand`, `MapHudCommand`, `MapZoomCommand`, `MapBorderCommand`, `MapTextCommand`, `MapLegendCommand`) and later lanes fill bodies in files they own. No `/glw turf map` alias; the `/glw turf` root prints a `[map]` link. Entries in the turf `commands.json`. |
| Sub-arguments | `/glw map`, `map auto`, `map hud on|off`, `map text`, `map legend` (W1); `map zoom in|out`, `map border` (W3). Chained `OptionalArgument`s with completion. |
| Orientation | Fixed north-up (rows north -> south, columns west -> east), viewer centred. |
| Size | `Width` 41 x `Height` 7 (odd), so header (1) + grid (7) + legend (2) = 10 lines = the unfocused chat height. `Cell_Blocks` 16. Vertical range is only +/-56 blocks; `Height` 9 is documented as the admin option. |
| Glyph = identity | Each gang in the window gets a letter: first letter of its resolved display name (`GangDisplayNameResolver`, colour codes stripped); collision -> next letter of its name, then first free `A-Z` (never `I`); digits `2-9` after 25 gangs. Allocated **viewer's gang first, then allies, then ascending gang id**, so walking one cell never hands your own letter to a newer neighbour. |
| Colour = relation, always | own GREEN, ally AQUA, rival RED, owned-as-seen-by-gangless YELLOW, unclaimed `#` GRAY, no turf `-` DARK_GRAY, you `+` WHITE. Chat colour never carries identity (Keystone `Color` has 16 names but 14 codes) and never carries state: green/red/gold collapse under deuteranopia and dark red approaches dark grey under protanopia. |
| State = formatting | *italic* = under attack; underlined = shielded for the viewer (owners offline, waking up, just captured, over your cap - every `CaptureEligibility` reason maps to the one style and the one word). Both are width-neutral in the vanilla font; bold stays banned (+1 px). Verified in the GUI-scale checklist. |
| Which turf a cell shows | The turf covering the largest share of the cell if >= 50 %, else `-`. Visibility guarantee: a turf that wins no cell is drawn in the cell holding its centre. Turfs never overlap (`TurfManager.findConflict`). Adjacent turfs of one gang join into one run; hover names each. |
| Hover (per merged run, turf-level facts only) | turf name; owner or Unclaimed; state (`Quiet` / `Under attack by Rats, 62 %`); the `CaptureEligibility` reason (`attackable` / `shielded: no Rats online` / `shielded: over your cap`); minutes-since-online **only for the viewer's own gang**; pays per interval or `dormant`; `Your guards: 4 in reserve` **owners only**. No cell coordinates or coverage (they defeat run-length merging). The `+` cell's hover carries the turf under you, your coordinates and facing. |
| Click | `RUN_COMMAND /glw turf info <id>` (read-only). Admins see `/glw turf tp <id>` in the hover text, never as a click. |
| Header | `Map - Turfs 3/4 - north is up - + you` (201 px); empty window: `No turf in view - nearest Docks 320m NE`. |
| Legend | Two plain-text lines, each **<= 300 px** by the vanilla width table (`TerritoryRaster.pixelWidth`, tested): line 1 packs gang entries `V Vipers (you)` while they fit (names truncated to 8 characters, full name in the letter hover) and ends with `+N more` (hover lists them, click = `/glw map legend`); line 2 is fixed: `# unclaimed  - none  italic attacked  underlined shielded` (283 px). Bedrock/Geyser (no hover) and colour-blind players lose nothing. |
| Text form | `/glw map text` prints the `turf list` form (nearest first, relation and state in words); chosen automatically for Floodgate players (UUID prefix `00000000-0000-0000`, no Floodgate dependency) because the Bedrock font is proportional. |
| Auto map | `/glw map auto`: re-send when the **set of turfs in the window changes** or the viewer enters/leaves a turf, at most every `Auto_Min_Interval_Seconds` (5), also when the world's turf version bumped and the raster hash changed. Driven by a 1 Hz `BeanLifecycle` task, never `PlayerMoveEvent` (`TurfLocationTracker` javadoc forbids it). Default off; prefs in memory, cleared on quit. |
| HUD line | Gang members, inside a turf only, priority 9 every 2 s (D12); `map hud on` adds the outside nearest-turf line; `map hud off` per player. |

### 5.2 Sample frame

Viewer in Vipers (3 of 4 turfs), Kings allied, Rats rival with members online, Saints rival all offline. Kings' Docks East
(the 9 x 4 `K` block at columns 25-33 of rows 2-5) is under attack by Rats and renders *italic*; the Saints block renders
underlined (shielded: no Saints online, no countdown). Every row is exactly 41 glyphs (246 px); `+` is column 21 of row 4.
Header 201 px, legend lines 275 px and 283 px. The golden test reproduces this frame from fixture turfs and must not
hard-code the cooldown default (`Cooldown_Minutes` is 15).

```
Map - Turfs 3/4 - north is up - + you
--RRRRRRRR-#######-----------------------
--RRRRRRRR-#######------KKKKKKKKK--------
--RRRRRRRR--------------KKKKKKKKK-SSSSSS-
--RRRRRRRR----VVVVVV+VV-KKKKKKKKK-SSSSSS-
--------------VVVVVVVVV-KKKKKKKKK-SSSSSS-
--------------VVVVVVVVV-----------SSSSSS-
--------------VVVVVVVVV--#######---------
V Vipers (you)  K Kings (ally)  R Rats (rival)  +1 more
# unclaimed  - none  italic attacked  underlined shielded
```

Colours and styles (invisible in plain text): `V` green, `K` aqua italic, `R` red, `S` red underlined, `#` gray, `-` dark
gray, `+` white; `+1 more` hovers `S Saints (rival, shielded)`.

### 5.3 Data path and NEW classes (package `org.luckyraven.gangland.turf.map`)

| Class | Role |
|---|---|
| `TerritoryRaster` **NEW** | pure, Bukkit-free: window + cell size + turf rectangles (from `TurfManager.getTurfsInWorld`, dozens x 287 cells, no index) + runtime states + viewer relation -> cells `{turfId, glyph, colourKey, style}` + header + legend with `pixelWidth`. All unit tests live here. Keystone-promotion candidate only with a second consumer. |
| `TurfMapRenderer` **NEW** | cells -> Bungee components (`ComponentBuilder`, `HoverEvent`, `ClickEvent`, the same API `TurfListCommand.sendRow` compiles at the 1.16.5 floor) with run-length merge of neighbours with identical glyph, colour, style and hover (typically < 60 components). |
| `MapCommand` **NEW** + child stubs | `/glw map` root and children, all registered by the kickoff. |
| `MapAutoTask` **NEW** | 1 Hz `BeanLifecycle` task: turf-set change + enter/leave + interval + hash gates. |
| `MapPrefsService` **NEW** | per-player `{auto, hud, cellBlocks}` in memory, cleared on quit (same pattern as `WandSelectionManager`). |
| `TurfHudTask` **NEW** | action-bar line via `ActionBarManager.sendBackground(player, line, 9)` every 2 s inside a turf. |
| `TurfPlaceholderContribution` **NEW** (`turf.placeholder`) | six `%gangland_turf-*%` tokens, O(1) off `TurfLocationTracker.getPlayerTurfCache()`; nearest turf computed once per second per player. |
| `CaptureEligibility` **NEW** (`turf.capture`, W1 kickoff) | the shared verdict every surface reads. |

Turf version counter: bumped by a listener on `TurfOwnerChangedEvent`, `TurfCapturedEvent`, `TurfCaptureStartEvent`,
`TurfCaptureFailedEvent` and create/delete. It only works because W0 makes **every** ownership change fire the event; the
map therefore depends on W0-A. Gang names and colours resolve at paint time through `GangLookupContract` (no `GangRenamedEvent`).

### 5.4 Render rules

1. Fixed north; odd width and height; viewer in the centre cell.
2. One glyph width class only: `-`, `#`, `+`, `A-Z` minus `I`, digits `2-9`. No `.`, `!`, lowercase or bold.
3. Glyph carries identity, colour carries relation (always), italic marks under attack, underline marks shielded for the viewer.
4. Majority rule (>= 50 %) per cell, with the centre-cell visibility guarantee for small turfs.
5. Header and both legend lines measure <= 300 px by the vanilla width table; the legend repeats every colour and style fact in plain text; hover is never the only carrier.
6. Run-length merge neighbours with identical glyph, colour, style and hover into one component; hover carries turf-level facts only.
7. Owners-only facts (guards in reserve) and other gangs' online times never appear on a rival's hover or the web layer.
8. Never `PlayerMoveEvent`; auto-map is gated by turf-set change, enter/leave, a 5 s interval and the raster hash.
9. Floodgate UUIDs get the text form.

### 5.5 Expansion ladder

| Layer | Surface | Wave | Notes |
|---|---|---|---|
| M1 chat grid + hover/click + auto + text | chat | W1 | the ask |
| M2 HUD line + placeholders | actionbar, sidebar | W1 | inside-turf only by default; zero Plaque change |
| M3 zoom 8/16/32/64 | chat | W3 (may start after the W1 gate) | a raster parameter |
| M4 player border | in-world | W3 (may start after the W1 gate) | after `TurfVisualization` gets 40-block clipping + adaptive step (TF-43) |
| M5 dynmap area markers | web | W3 (may start after the W1 gate) | soft dep; `dynmap-api` `provided` in `gangland-turf/pom.xml` (W3-W owns the pom + root repository entry); dynmap types only inside `GT/map/web/**`, created behind `Bukkit.getPluginManager().getPlugin("dynmap") != null` from a type-free factory so `ReflectionGuard` never skips `TurfModuleConfig`; main thread only; `Web.Show_Owner` true, `Web.Show_Income` false |
| Deferred | BlueMap (Java floor unverified), turf-centre hologram, `TurfMapLayer` seam for cops overlays, map item (M6: render cost, own cache, low value) | - | after M1 proves the grid |
| Out | squaremap, Pl3xMap | - | Paper-only |

---

## 6. Cops-n-crooks transfers (H11-H13)

Turf guards and the Quartermaster are `CivilianNpc`s and already inherit H11 pursuit/route planning, H12 formation arc,
strafing, retreat, shouts and fire cadence (cnc-ideas section 1). Only the delta is planned.

| cnc | Idea | Applied as | Wave | Primitive it rides |
|---|---|---|---|---|
| T1 | Alerts to the owning gang anywhere | Extend `TurfCaptureNotifier` (already global): four kinds (start with head-count and side, half guards, last stand, outcome held/lost/stalled); allies get the start line; `turf_messages.yml` `Alerts`. **No api seam.** | W2 | `RadioSides.compass8`, `TurfCaptureNotifier` |
| T2 | Per-turf squads, posts, leash | Per-target `NpcSquad`s **scoped per turf** (`SquadKey` scope, `CivilianService.squadFor`, `TurfGarrisonSquads` provider); posts/leash/reserve in the turf-side `TurfGarrison` holder | W3 | `NpcSquad`, `CivilianNpc.setFactionSquads`, `FactionSquads` |
| T3 | Reinforcement waves | reserve, `Wave_Size`/`Max_Alive`/cooldown, hidden-post spawn via `World.rayTraceBlocks` with a fallback, consume-after-spawn, refund; tiers cut | W3 | `World.rayTraceBlocks`, `GarrisonManager` |
| T13 | Stand-down, walk home | survivors walk to a post, despawn after 8 s with a shout | W3 | `AbstractNpc.navigateTo` |
| new | Living guards count as defenders | `classify` + `aliveCount`, capped; replaces `CAPTURE_DEFENSE_BONUS` | W2 | `TurfDefenderDeployer` |
| T6 | Gang-tagged callsigns | `[VIP] Guard 42` (3-4 char tag, <= 16 chars); `%gang%` via `FactionSquads.extras` and `Shouts.Format` with a `%faction%` fallback | W3 | `FactionSquads.extras`, `civilian_messages.yml` |
| T7 | Health bars | verify only | W4 | `gangland-healthbars` (H13) |
| T9 | Guard bounty drop | `TurfDefenderMoneyDropSource`, owner-funded | W4 | `GanglandMoneyDropClassifier` (api, on master since h13-drop) |
| T12 | Stuck recycling | respawn at another post when unreachable and unseen | W4 | `AbstractNpc.millisUnreachable` (Keystone 1.14.0) |
| T4 | Roles | `NpcRole` api record, no Medic | W4 | `NpcFanPlacement`, `setLeaderPriority`, `setRangedBand` (Keystone 1.14.0) |
| T10 | Commander down | shout + `takeCover` 5 s + one-off +10 % progress | W4 | `NpcSquadSignal.LEADER_DOWN` |
| T5 | Turf heat -> wanted | defender exemption only, optional lane (D10); policed-district dispatch never in this wave | W4 optional | `WantedKillTracker` (core) |
| T8, T11 | Responders; man-down/ping | deferred (adjacency; gang chat + alerts cover comms) | - | - |
| - | Cuff/detain, wanted-decay freeze, suppressive fire, named-NPC progression | not transferred (cnc-ideas section 4) | - | - |

Precondition: the H12 faction-alert scenario was never accepted outside unit tests. Lane W0-F runs it on the test server;
W3-A does not start until it passes (or its findings are triaged).

---

## 7. Waves and lanes

### 7.1 Shared files (the only files two lanes may both touch)

| File | Owner / rule |
|---|---|
| `gangland-features/*/src/main/resources/commands.json` | lanes append; the haiku merge step at wave end does a real 3-way resolve, dedups and validates |
| `GT/TurfModuleConfig.java` | orchestrator; lanes add `@Bean` methods only, never edit another lane's bean. Exceptions named per wave: W0-B (lifecycle of existing beans), W0-A (the `CaptureService` bean gains `TurfOwnership`, after W0-B merged), W2 kickoff removes the contribution beans |
| `GT/TurfModuleFiles.java` (2-field record) + `GT/TurfModuleFileConfig.java` | kickoff only: every module YAML is registered by the wave kickoff, never mid-lane |
| **NEW** `GangModuleFiles`/`GangModuleFileConfig`, `MailModuleFiles`/`MailModuleFileConfig` | W1 kickoff / W0-D create them; later additions by kickoffs only |
| `GT/command/TurfCommand.java` (ctor wiring) | W0-A in W0, W1-T in W1, W2-N in W2 (last-3-lines) with W2-P1 removing the contribution references first |
| `GT/map/MapCommand.java` | kickoff registers every child stub; no lane edits it afterwards |
| `GG/command/sub/gang/GangCommand.java` | W1 kickoff registers `list`, `info`, `chat`, `join`, `recruiting` as stubs; W1-G and W2-P2 fill bodies |
| `gangland-impl/src/main/resources/plugin.yml` | orchestrator: W1 kickoff `permissions:` block; W3-W adds `dynmap` to `softdepend` |
| root `pom.xml` | orchestrator: `<revision>` 0.14.0 at W0-K; `keystone.version` check at W4 start |
| `gangland-turf/pom.xml` | W3-W only (`dynmap-api` provided + repository) |
| `documentation/migration-0.14.0.md` | the E2 docs lane per wave, after the code lanes merge; lanes hand notes in their exit report |

Every other file has exactly one owning lane per wave (listed per lane below). Cross-wave hot files:
`CaptureService` (W0-A complete/cancel -> W1 kickoff extraction -> W2-R rules), `TurfDefenderDeployer` (W0-B lifecycle -> W2
kickoff `aliveCount` -> W3-B), `TurfIncomeDistributor` (W0-A -> W2-N), `GangPresenceTracker` (W0-B lifecycle -> W2-R
qualification and wake-up), `CivilianService` (W3-A only), `civilian_messages.yml` (W3-A only).

### 7.2 Lane gate (every lane)

1. Red first: the first commit adds or flips tests that fail on pre-fix code; the failing `mvn -q test -pl <module> -am -Dtest=<Class>`
   output goes in the exit report. Pinned tests are flipped, never deleted. A behaviour-preserving extraction (W1 kickoff
   `CaptureEligibility`) ships characterization tests that are green before and after against the old `isCapturable`.
2. `mvn -q test -pl <owned modules> -am` green, then `mvn -q -o verify -DskipTests` across the reactor. **No `install` in a
   lane** (six worktrees writing `~/.m2/.../0.14.0` race on Windows locks); `install` only in the integration worktree at
   merge. At most 3 Maven builds run at once.
3. House rules: braces on their own lines, XSeries, `@CustomLog`, block-style YAML with `Capitalized_Underscore_Separated`
   keys, `GanglandChatUtil.color` with `&`, chained `OptionalArgument`s + `commands.json` entry in the owning jar, no Paper API.
4. Worktree rules from section 2 (graphify against the main checkout, main-checkout absolute paths for `brainstorming/`,
   `CLAUDE.md`, `smoke.py`, never `git add`ed).
5. Docket rows are **handed in the exit report** (commit, branch, what changed, covering test, what was left out); the
   orchestrator writes them (section 8). Lanes never write the artifact db.

### 7.3 Wave gate (after all lanes merge into `gang-turf-territory`)

`mvn clean package` green; **opus** adversarial review of per-lane diffs (concurrency, event ordering, exploit paths, module
boundaries: no module imports impl, turf never imports cops); **fable** go/no-go against the exit criteria; console smoke
(`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`: rows G1 boot without Citizens, G2 `/glw reload` twice, G3
with/without dynmap - the rows do not exist in `scenarios.json` yet; the W0-E1 lane writes G1/G2 before the W0 gate) plus the
manual checklist in **NEW** `documentation/tests/features/turf.md` (map alignment at GUI scale 1-4, italic/underline advance,
hover, click, auto throttle, HUD with a Bartizan gun and inside a car). **The test server is a serial resource**: one lock,
held by W0-F, then by each gate; smoke G2 runs at the W0 gate, not as a lane exit. `smoke.py` is console-only, so every fight
acceptance needs the owner with two clients on the offline-mode server: W0-F about 20 min, W1 gate about 15 min (script to
minute 4), W2 gate about 40 min (full script + one live fight), W3 gate about 30 min (two guard waves, with and without
Citizens), W4 gate about 20 min; those gates are blocked on the owner. Docket rows written by the orchestrator; `graphify
update . --force` in the integration worktree; migration doc section complete (E2); at the W1 and W3 gates a
`git merge --no-commit master` dry run, then `--abort`, to see H13 conflicts early.

### 7.4 W0 - Trustworthy foundations (starts now from master)

Pre-step **W0-K** (orchestrator): create `gang-turf-territory` from `ff9d813f`, set `<revision>` 0.14.0 (D11), create the
integration worktree.

| Lane | Model | Scope | Owns | Docket | Tests | Exit |
|---|---|---|---|---|---|---|
| W0-B lifecycle + persistence (**first**) | sonnet | `BeanLifecycle` on every task-owning turf bean except the two W0-A tasks; task handles; quit cleanup; +5 min first sweep; `Number` casts; `turf_sequence`; copy-on-write lists; config reload; batch prune; pending purge; no double init; skips `TurfContributionTickTask` | `GT/TurfModuleConfig` (existing beans), `GT/task/{TurfLocationTracker,GangPresenceTracker}`, `GT/npc/defender/TurfDefenderDeployer` (lifecycle only), `GT/powerups/ActiveBuffManager`, `GT/listener/TurfBossBarListener`, `GT/npc/config/TurfNpcsConfigLoader`, `GT/database/**`, `GT/manager/TurfManager`, `GT/command/TurfCreateCommand` (id line), `GT/npc/TurfPowerupManager` | TF-05, 06, 08, 09, 14, 32, 33, 34, 36, TF-41, TF-42 | flip `TurfManagerTest`; extend `TurfRepositoryTest` (Long/Integer round-trip); `TurfModuleConfigTest` (no double init); `ActiveBuffManagerTest` (batch); `TurfPowerupManagerTest` (pending purged) | merges first; smoke G2 at the gate |
| W0-A ownership pipeline (**branches from W0-B's merged head**) | sonnet (opus reviews at gate) | `TurfOwnership` + `cancelContest`; every writer routed; disband listener; delete cancels the contest (TF-40); lifecycle of the two tasks; `CaptureService` bean gains `TurfOwnership` | `GT/capture/CaptureService` (complete/cancel), NEW `GT/capture/TurfOwnership`, `GT/command/TurfSetOwnerCommand`, `GT/command/TurfDeleteCommand`, `GT/command/TurfCommand` (ctor), `GT/task/InactivityReleaseTask`, `GT/task/TurfIncomeDistributor`, NEW `GT/listener/TurfGangDeleteListener`, `TurfModuleConfig` (that one bean) | TF-01, 04, 07, 10, 13, 25, 28, TF-40 | flip `CaptureServiceStartAndCompleteTest`; NEW `TurfOwnershipTest` (each cause fires exactly one event), `TurfGangDeleteListenerTest`; extend `TurfDeleteCommandTest` (contest cancelled) | `grep setOwnerGangId` in turf main hits only `TurfOwnership` + the repository load |
| W0-C gang hardening | sonnet | null guards, NULL loads, `hashCode`, symmetric `isAlly`, transfer lock timer, node validation, player-only guard; NEW `GangCommandsJsonParityTest` | `gangland-gang/**` named files | GR-10, 12, 15, 20, 28, 30, 32, 33, 34, 35, 36, TF-24 | flip `RankManagerTest` (GR-12), `GangAllianceTest` (GR-35 + one-directional case); `MemberManagerTest`, `RankCommandsTest` cases; NULL-column round-trip; NEW `GangCommandsJsonParityTest` | parity test green; gates W1-G/W1-R |
| W0-D mail + api hardening | sonnet (opus reviews GR-11 at gate) | NEW `MailModuleFileConfig`/`MailModuleFiles` + `mail/mail_rules.yml`; sync expiry sweep + cancel; `onlineGangMembers` reuse; accept rejects siblings; `Invite_Offline_Expiry_Hours` 72; `gang invite decline`; bad-row skip; `Display_Name_Char` default; mail `commands.json` drift | `gangland-mail/**`, api `Settings.java` (one method body) | GR-11, 14, 23, 24, 27, 31 | `MailManagerTest`, `GangMailCommandGuardTest`, NEW `MailRepository` bad-row test, NEW `Settings` empty-char test, NEW `MailModuleConfigTest` (YAML copied from the module jar) | no `start(true)` left in mail |
| W0-E1 triage + drift (parallel) | haiku | triage rows section 9.3 (TF-40..49, GR-38, CM-37) + `build_docket.py`; gang `commands.json` drift; `gangs.md`/`ranks.md` corrections; TF-31 by-design note; `migration-0.14.0.md` skeleton; smoke rows G1/G2 in `scenarios.json`. CLAUDE.md stale lines (`Host_Api: 2.0`, turf `Depends: [civilians, gang]`) are orchestrator work in the main checkout | docs and resources only | TF-31 | `python build_docket.py` runs | docket shows the new rows |
| W0-E2 migration fill (after merges) | haiku | migration doc from the four exit reports (symmetric allies change friendly fire, ids never reused, `gang invite decline`, 72 h offline invite expiry, new `turf_sequence` table, `mail_rules.yml`) | `documentation/migration-0.14.0.md` | - | - | doc matches the merged diff |
| W0-F faction-alert acceptance | sonnet + owner (two clients) | H12 GL-4 scenario with `turf_defender` on the test server; report under `brainstorming/gang-turf-wave-2026-09-30/smoke/`; holds the server lock | test server | - | scenario report | pass, or findings triaged; gate item for W3-A |

Merge order: W0-B -> W0-A -> W0-C -> W0-D -> W0-E1 -> W0-E2. Opus in W0: 1 (gate reviewer, per-lane diffs). Exit: 35 open
entries + TF-40/41/42 fixed with tests and closed at the gate; smoke G1/G2 clean; the review finds no path that changes
ownership without `TurfOwnerChangedEvent`.

### 7.5 W1 - See the city

Dependencies **per module**: turf lanes (W1-K, M, X, H, T) need W0-B and W0-A merged; gang lanes (W1-G, W1-R) need W0-C
merged; W0-D/E/F gate nothing here. D3, D6, D12 answered.

Kickoff **W1-K** (sonnet for code, haiku for YAML/JSON; on the integration branch): `CaptureEligibility` extracted from
`isCapturable` with identical behaviour + `CaptureEligibilityCharacterizationTest`; `MapCommand` root with **every** child
registered as an empty stub (`MapAutoCommand`, `MapHudCommand`, `MapZoomCommand`, `MapBorderCommand`, `MapTextCommand`,
`MapLegendCommand`); `MapPrefsService`; `turf/turf_map.yml` with every key; `turf/turf_rules.yml` (`Snap_To_Chunks`,
`Min_Side_Blocks`, `Max_Side_Blocks`) + `TurfRules` loader; `turf/turf_messages.yml`; `TurfModuleFiles`/`TurfModuleFileConfig`
registration; NEW `GangModuleFileConfig`/`GangModuleFiles` + `gang/gang_rules.yml` + `GangModuleConfigTest`; `GangCommand`
stubs for `list`, `info`, `chat`, `join`, `recruiting`; `plugin.yml` `permissions:` block (CM-37) + NEW
`PluginYmlPermissionsTest`; `commands.json` entries for every stub; one trivial test per class.

| Lane | Model | Scope | Owns | Docket | Tests | Exit |
|---|---|---|---|---|---|---|
| W1-M map core | sonnet (**fable reviews section 5 before code**) | `TerritoryRaster` (incl. `pixelWidth`, legend packing), `TurfMapRenderer`, `MapCommand` body, `MapAutoCommand`, `MapTextCommand`, `MapLegendCommand`, `MapAutoTask`, version counter; TF-27 COOLDOWN derived on load | `GT/map/**` except HUD files, `GT/data/TurfRuntimeState`, `GT/manager/TurfManager` (version hooks) | TF-27 | `TerritoryRasterTest` (majority, visibility guarantee, letter allocation viewer-first with collisions and `I` skipped, relation colours, italic/underline state, fixed north, centred viewer, `pixelWidth(header|legend) <= 300` with a small width table, legend packing + `+N more`, empty-window header), `TurfMapRendererTest` (run-length, click text, owners-only hover, own-gang-only online time), `MapAutoTaskTest` (set change, enter/leave, 5 s), NEW `TurfRuntimeStateTest` (TF-27); golden test reproduces section 5.2 without hard-coding the cooldown | screenshot at GUI scale 2 shows aligned columns; the block is 10 lines |
| W1-X explain | sonnet | consumers of `CaptureEligibility`: feedback listener, info card (id + name arg, completion), boss-bar text (counts + time left); the P2.2 strings | `GT/listener/TurfCaptureFeedbackListener`, `GT/command/TurfInfoCommand`, `GT/listener/TurfBossBarListener` (text only) | - | `TurfInfoCommandTest` (name completion, reason text), boss-bar text test | one reason text everywhere |
| W1-H HUD | sonnet | `TurfPlaceholderContribution` (six tokens), `TurfHudTask` (priority 9, 2 s, inside only), `MapHudCommand` body; retire `TurfPresenceBarListener` (D6); the `turf-` prefix line in impl handed to the orchestrator | `GT/placeholder/**` NEW, `GT/map/TurfHudTask` NEW, `GT/map/MapHudCommand`, `GT/listener/TurfPresenceBarListener` (deleted) | - | NEW `TurfPlaceholderContributionTest` (fake cache), NEW `TurfHudNearestTest` (nearest point + 8-way bearing, pure), HUD send cadence test with a fake clock | Plaque shows a turf line with zero Plaque change; a Bartizan gun and a car keep their HUD inside a turf (merge after W1-X) |
| W1-T turf clarity + admin | sonnet | enter titles per relation + gangless title; `turf list` paged by distance; TF-21 limits; snap + `findConflict` re-run (D3); TF-29; TF-30 `gangland.turf.admin`; TF-39 rename; TF-23 wand/status strings; `TurfStatusCommand` line for TF-27; `[map]` link on the `/glw turf` root | `GT/listener/TurfActionBarListener`, `GT/command/{TurfCommand,TurfCreateCommand,TurfShowCommand,TurfTpCommand,TurfListCommand,TurfWandCommand,TurfStatusCommand}`, NEW `TurfRenameCommand`, `GT/selection/**`, `GT/data/CuboidRegion`, `GT/listener/WandListener` | TF-21 (limits), 23 (wand/status), 29, 30, 39 | `CuboidRegionTest` (snap incl. negatives: `min & ~15`, `max | 15`; snapped overlap rejected), NEW `TurfCreateCommandTest` (limits, duplicate name), `Selection` world-change test, namespace helper test | help shows player verbs only |
| W1-G gang onboarding | sonnet + haiku YAML | onboarding screen; `GangListCommand`; `GangInfoCommand`; `GangNameRules`; `alliance_stat.yml` retarget; `gang_stat.yml` stats page; `leave` uses `confirm` | `GG/command/sub/gang/*` (named bodies), `gangland-impl/src/main/resources/inventory/{gang_info,alliance_stat,gang_stat}.yml` | GR-16, 17, 29 | `GangCommandTest` (gangless vs member), name-rule table test, NEW `GangListCommandTest` (paging), `GangCommandsJsonParityTest` stays green | script minutes 0-2 true for a non-op player |
| W1-R ranks preset | sonnet | seed `member -> officer -> owner` on an empty table only; register `gangland.gang.*`; max-id+1 seeding; missing head logs and creates; node validation + completion; `RankParent` delete both sides + documented semantics | `GG/gang/rank/**`, `GG/gang/database/repositories/rank/**`, `GG/gang/permission/GangPermissions`, `GG/command/sub/rank/**` | GR-13, 21, 22, 37 | flip `RankManagerTest` (GR-13); `RankRepositorySpiTest` seeding with gaps; NEW `RankSeedTest` (empty vs non-empty table); NEW `RankPermissionAddCommandTest` | fresh install: member deposits, officer invites |
| W1-E1 docs (parallel) | haiku | NEW `documentation/features/turf.md` (Map, Placeholders, HUD) and NEW `documentation/tests/features/turf.md` (manual map checklist incl. italic/underline, HUD with Bartizan/car) | docs | - | - | - |
| W1-E2 merges + migration (after) | haiku | `commands.json` 3-way merge; migration doc (new commands, `/glw` player permissions now default true, `gang list` change, `tp` admin, snap flag, core and gang jars update together because `inventory/*.yml` clicks point at gang-module commands) | docs, `commands.json` | - | - | help pages list every new leaf |

Merge order: W1-M -> W1-X -> W1-H -> W1-T -> W1-G -> W1-R -> W1-E2. Opus: 1 (gate review of W1-K + W1-M). Exit: a non-op
player with no guidance opens `/glw map`, reads who owns what, clicks a turf and reads its card; a gangless player gets the
onboarding screen; a fresh-install member can deposit; the owner walks the ten-minute script to minute 4.

### 7.6 W2 - Fair fights (depends on W1; D1, D2, D4, D5, D7, D8, D9, D15 answered)

Kickoff **W2-K** (sonnet): `TurfDefenderDeployer.aliveCount(int turfId)` returning today's count with a red per-turf test;
NEW `events/TurfDefenderDownEvent(turf, remaining)`; `TurfCaptureFailedEvent.Reason.STALLED`; `turf_rules.yml` gains
`Capture_Window`, `Shield_After_Logout_Minutes`, `Wake_Up_Shield_Seconds`, `Window_Min_Member_Age_Hours`, `Presence_Seconds`,
`Min_Capture_Seconds`, `Max_Contest_Minutes`, `Guard_Weight_Cap`, `Turf_Cap_Base`, `Turf_Cap_Per_Level`, `Turf_Cap_Ceiling`,
`Income_Idle_Hours`, `Max_Income_Multiplier`, `Max_Reserve`, `Inactivity_Release_Days` (legacy `Turf.Capture.*` read as
fallback); `TurfRules.cap(gang)` as a pure function with its table test; `gang_rules.yml` gains `Gang_Level_Max`,
`Gang_Level_Base`, `Gang_XP_Per_Payout`; `GangManager.addExperience(gang, double)` no-op stub; `turf_messages.yml` `Alerts`
section; orchestrator removes the contribution beans from `TurfModuleConfig`.

| Lane | Model | Scope | Owns | Docket | Tests | Exit |
|---|---|---|---|---|---|---|
| W2-R capture rules | **opus** | `Capture_Window` (`OWNERS_ONLINE` default, `OWNERS_OFFLINE`) inside `CaptureEligibility`; wake-up shield; qualifying-member timestamp; contest lock; strict plurality (owners+allies and largest other gang); attackers-only presence with countdown; relation-change re-stamp; `Min_Capture_Seconds`; `Max_Contest_Minutes` -> `STALLED`; single bar (D5); guards count capped; cap check in `tickIdle` + re-check at `complete`; TF-11 dead code; TF-12 grace; TF-26 documented; stop reading `CAPTURE_DEFENSE_BONUS`; `CaptureSettings` keys folded | `GT/capture/**`, `GT/task/{TurfLocationTracker,GangPresenceTracker}`, `GT/listener/GangPresenceListener`, `GT/listener/TurfCaptureFeedbackListener` (reasons), `GT/listener/TurfBossBarListener` (counts) | TF-11, 12, 26, TF-02 (defence half), TF-48 | flip `CaptureServiceOwnedTurfTest` (TF-12), `CaptureServiceHelpersTest` (TF-11); table tests window x wake-up x presence x guards x cap; plurality (sum and largest-single cases); alt-blocker red -> green; login-snipe (wake-up) test; young-member heartbeat ignored, young-gang all count; stall timeout; ally-abandon re-stamp; min-time clamp | every `CaptureEligibility` reason reachable and tested |
| W2-N alerts + income + digest | sonnet | four alert kinds in `TurfCaptureNotifier`; income activity gate + action-bar line + `Max_Income_Multiplier` + in-cap only; the `addExperience` call per active turf; `TurfEventBuffer` + `TurfDigestListener`; `/glw turf` last 3 lines | `GT/listener/TurfCaptureNotifier`, NEW `GT/log/**`, NEW `GT/listener/TurfDigestListener`, `GT/task/TurfIncomeDistributor` (body), `GT/command/TurfCommand` (last 3 lines) | TF-37 | NEW `TurfCaptureNotifierTest` (audience anywhere, allies start-only, throttle, failure and stall notify), NEW `TurfEventBufferTest` (ring, per-player since-quit, restart fallback), income gate + cap test, XP call test with a recording `GangManager` fake | a logged-out owner sees the digest on join; a dormant gang is paid nothing |
| W2-P1 progression | sonnet | `GangManager.addExperience` body + `GangLevelUpEvent`; `Gang` level from `gang_rules.yml`; delete contribution tick/listener and their references in `TurfCommand`/`TurfModuleConfig` (TF-16, TF-44); contribution reset migration (D15); delete bounty event/listener (D8); delete `gangland.turf.capture/contribute` fixtures (TF-45 half) | `GG/gang/{GangManager,Gang}`, `GG/events/gang/*`, `GG/listener/gang/GangBountyMessageListener` (deleted), `GT/contribution/**` (deleted), `GT/listener/contribution/**` (deleted), `GT/command/TurfCommand` (reference removal, before W2-N rebases), `RankPermissionApplierTest` fixtures | GR-19, TF-16, TF-44 | level-up event fires once per level; days-to-level table test; `GangDeleteCommandTest` payout with all-zero contributions = equal split; migration test (mixed values -> 0) | gang level moves in a real session |
| W2-P2 gang social | sonnet | `GangChatCommand` + listener; `Gang.State` `INVITE` default in constructor/table/`parseState`, `CLOSE` -> `INVITE`, load migration; `gang join` (sets join date) + `gang recruiting` (D7, GR-38) | `GG/command/sub/gang/{GangChatCommand,GangJoinCommand,GangRecruitingCommand}`, `GG/listener/gang/GangChatListener` NEW, `GG/gang/Gang` (state), `GG/gang/database/**` (state default, migration) | GR-38 | chat recipient filter (pure); stored `OPEN`/`CLOSE`/garbage rows load as `INVITE`; `gang join` refused on `INVITE`, accepted on `OPEN` and sets `gangJoinDateLong`; `GangCommandsJsonParityTest` | no existing gang becomes joinable without `gang recruiting open` |
| W2-Q Quartermaster | sonnet | TF-17 price + discount, TF-18 pages, TF-19 re-validation, TF-23 panel strings (append to `turf_messages.yml`), TF-38 name; `gangland.turf.upgrade` via `GangPermissions.allows` (TF-45 half); `Max_Reserve`; purchase log with buyer; `reinforced_defense` cut with a tolerant loader | `GT/npc/view/**`, `GT/npc/config/TurfPowerupOpenContractImpl`, `GT/command/TurfPowerupNpcCommand`, `GT/powerups/PowerupRegistryLoader`, `turf/turf_powerups.yml` | TF-02 (discount half), 17, 18, 19, 23 (panel half), 38, TF-45 | purchase re-validation with a recording fake; `PowerupRegistryTest` (old file with `reinforced_defense` loads and warns); paging test; reserve cap test | a recruit cannot spend the bank; an officer cannot exceed 12 guards |
| W2-E1 docs (parallel) | haiku | "How turf wars work" page from `turf_rules.yml` (the five sentences with live numbers; also printed by **NEW** `/glw turf rules`, a W2-R stub filled here); `_es` twins (empty) | docs | - | - | player docs match code |
| W2-E2 merges + migration (after) | haiku | `commands.json`; migration doc leads with the capture-window behaviour change and the small-server `OWNERS_OFFLINE` switch, the contribution reset, `INVITE` default, `Turf.Capture.*` -> `turf_rules.yml`, new YAML keys, faucet per active turf per day | docs, `commands.json` | - | - | - |

Merge order: W2-R -> W2-N -> W2-P1 -> W2-P2 -> W2-Q -> W2-E2. Opus concurrent peak: 2 (W2-R runs alone; the two gate reviewers
start after the merges, `parallel: false` in the roadmap). Fable advises on D1 before W2-R starts. Exit: the owner walks the
whole ten-minute script; in a manual fight owners get an alert anywhere, guards hold the bar, a failed or stalled capture
notifies both sides, the alt blocker and the login snipe are covered by red -> green tests; 15 docket entries closed.

### 7.7 W3 - Guards that fight like a crew, in parallel with map extras (depends on W2 and the W0-F pass; the map lanes may start after the W1 gate)

| Lane | Model | Scope | Owns | Docket | Tests | Exit |
|---|---|---|---|---|---|---|
| W3-A civilians hit path | **opus** (first) | `SquadKey` scope; `CivilianService.squadFor`; provider-identity + scope checks in `alertFaction`/`recruit`/`drainPendingRecruits`; `FactionSquads.extras` default; `FactionVoice.extras` merge; `Shouts.Format` `%gang%` with `%faction%` fallback | `GC/npc/CivilianService`, `GC/npc/FactionSquads`, `GC/npc/state/behavior/CivilianCombatBehavior`, `civilian_messages*.yml` | TF-47 | two attackers on one turf -> one CONTACT per target squad, no CONTACT_LOST thrash; two turfs' guards never share a squad; default path unchanged; format fallback test; cops tests green | cross-turf recruitment impossible |
| W3-B garrison | sonnet (after W3-A) | `TurfGarrisonSquads`, `TurfGarrison`, posts, leash, UUID index, waves, hidden-post spawn via `rayTraceBlocks` + fallback, consume-after-spawn, refund, stand-down, `TurfDefenderDownEvent` firing, callsigns | `GT/npc/defender/**`, `GT/listener/powerups/GarrisonDeployListener`, `GT/npc/TurfPowerupNpc`, `turf/turf_npcs.yml` | TF-20, 35, TF-46 | `TurfDefenderDeployerTest` (waves, cap, refund, leash as a pure function of positions, UUID lookup, no consume without Citizens, fallback post); `GarrisonManagerTest` (refund); NEW `GuardCallsignTest` (<= 16, 3-4 char tag, no `CIT-`) | live: guards at posts, return after a chase, waves top up; with and without Citizens |
| W3-Z zoom | sonnet | `MapZoomCommand` body, cells 8/16/32/64 | `GT/map/MapZoomCommand`, `MapPrefsService` (cell field) | - | NEW `TerritoryRasterZoomTest` (each size) | - |
| W3-V borders | sonnet | `TurfVisualization` 40-block clipping + adaptive step (TF-43); `MapBorderCommand` body | `GT/task/TurfVisualization`, `GT/map/MapBorderCommand` | TF-21 (viz half), TF-43 | NEW `TurfVisualizationClipTest` (bounded point count for a 512 x 512 turf) | closes TF-43 |
| W3-W dynmap | sonnet | `gangland-turf/pom.xml` `dynmap-api` provided + repository (falls back to a reflection facade if Maven cannot fetch it); `DynmapTurfLayer` behind a plugin-present check from a type-free factory, synced on version events + full rebuild on enable; core `plugin.yml` softdepend (orchestrator) | NEW `GT/map/web/**`, `gangland-turf/pom.xml` | - | NEW `DynmapTurfLayerTest` (fake marker API); smoke G3 | no `reflection.type.missing` spam without dynmap |
| W3-E1 docs (parallel) | haiku | `turf.md` guards section; smoke G3 row | docs | - | - | - |
| W3-E2 merges + migration (after) | haiku | `commands.json`; migration doc | docs | - | - | - |

Merge order: W3-A -> W3-B -> W3-Z -> W3-V -> W3-W -> W3-E2. Opus peak: 1 (W3-A, then the gate reviewer). Exit: a live capture
with two guard waves on the test server; nothing consumed without Citizens.

### 7.8 W4 - Garrison identity and roles

Preconditions (all four, checked by the orchestrator): master revision 0.13.0 with all five H13 branches merged;
`keystone.version` 1.14.0 in master's root pom; Keystone master at 1.14.0 (today it exists only on
`phase-h13-npc-roles`, 9 commits ahead); `mvn clean package` green on master. Then merge master into `gang-turf-territory`
(conflicts per section 2) and read `GanglandApi.VERSION` to fix the W4 api value.

| Lane | Model | Scope | Owns | Docket | Tests | Exit |
|---|---|---|---|---|---|---|
| W4-R roles (**first**) | **opus** | `NpcRole` record (api bump, documented in `documentation/gangland-api.md`); `CopRole` adapter (`copsncrooks/npc/police/config/CopRole.java`); civilians `AI.Combat.Roles` overlay with defaults; turf `Garrison.Composition`; shield guard in `civilians/listener/civilian/CivilianDamageListener`; commander-down; no Medic; per-module `Host_Api` table: turf, cops, civilians all set to the W4 value | api `npc/NpcRole.java` NEW, cops `CopRole.java`, civilians type-config + `CivilianDamageListener`, turf `GT/npc/defender/**` composition, three `module.yml` | - | `NpcRoleTest`; `CopRole` regression tests unchanged; civilians overlay default test | merges first |
| W4-C health bars | haiku verify + sonnet fix if needed | T7 layout with the callsign line | none expected | - | manual check | documented |
| W4-D bounty + stuck (after W4-R) | sonnet | `TurfDefenderMoneyDropSource`; stuck recycling after the cop version is accepted live | NEW `GT/npc/defender/TurfDefenderMoneyDropSource`, deployer tick | - | drop funding (no mint; ally kill = 0); recycle decision on a fake clock | killing guards pays the owner's spend back to attackers |
| W4-H heat exemption (optional, D10; after W4-R) | sonnet | `WantedKillTracker.exemptsKill` default + forward; impl consults it; cops implements via turf contest state | `gangland-core/.../wanted/*`, impl `EntityDamageListener`, cops `seam/KillComboWantedTracker` | TF-49 | `WantedKillTrackersTest`, `KillComboWantedTrackerTest` (defender inside exempt; attacker not; outside not; no contest not) | - |
| W4-E docs | haiku | final migration doc; api notes; changelog (.md + .bbcode.txt) | docs | - | - | - |

Merge order: W4-R -> W4-C -> W4-D -> W4-H -> W4-E, then `gang-turf-territory` -> master as 0.14.0. Opus peak: 1.

### 7.9 Timeline

```
W0  K(orch) -> [B] sonnet -> [A] sonnet  ||  [C][D] sonnet  [E1] haiku  [F] sonnet+owner  -> [E2] -> gate (opus 1, fable, smoke G1/G2, owner 20 min)
W1  K(sonnet+haiku) -> [M][X][H][T] after W0-A/B  ||  [G][R] after W0-C  [E1] haiku  -> [E2] -> gate (opus 1, fable, owner script to 4:00, master dry-run merge)
W2  K(sonnet) -> [R OPUS] [N][P1][P2][Q] sonnet  [E1]  -> [E2] -> gate (opus 2 reviewers in sequence, fable, full script + live fight)
W3  [A OPUS] -> [B] sonnet   ||   [Z][V][W] sonnet (may start after the W1 gate)  [E1]  -> [E2] -> gate (opus 1, fable, live waves, master dry-run merge)
      ... H13 (0.13.0) merges to master; Keystone master 1.14.0; merge master into gang-turf-territory ...
W4  [R OPUS] -> [C][D][H]  [E]                                -> gate -> merge to master as 0.14.0
```

---

## 8. Orchestration

Rules:
- No agent spawns sub-agents. Each lane prompt carries its owned file list, the census paths, this plan's section, the
  worktree rules, and "run `graphify query` first (against the main checkout); read raw files only after the graph has
  oriented you".
- At most 3 opus agents concurrently; the real peak is 2 (W2-R then two sequential gate reviewers). `feature-dev` reviewers
  have no shell: the orchestrator hands them per-lane diffs and transcribes their output to disk.
- Every parallel lane runs in its own worktree `E:/Programming/java/wt/gt14-<lane>` branched from `gang-turf-territory` after
  the wave's kickoff commit (W0-A from W0-B's merged head); the orchestrator merges in the stated order and runs the reactor
  build after each merge. At most 3 Maven builds at once; lanes `verify`, never `install`.
- The test server (`E:\Documents\Minecraft\Test Server`) is one lock: W0-F, then each gate; never two smoke runs at once.
- Route `mvn`, `git log` and smoke output through `ctx_execute`; print only derived lines.
- **Docket writes are orchestrator-only**, after each wave merge, from the rows lanes hand in their exit reports: the
  Gangland docket `4102fb1f-20b0-44e9-b1b7-2893a559fa04` is the source of truth (collection `bugs`, `{status, note,
  updatedAt}`); the cross-project docket `4a903fb6-cbdd-4810-90b8-88a863e9013c` gets the same Gangland rows re-seeded at
  the end of each wave (it lags between waves by design). New triage rows go through `triage/*.txt` + `build_docket.py`
  (W0-E1) before any lane records a fix against them. `graphify update . --force` in the integration worktree whenever a
  lane adds, moves or deletes classes.
- A lane that hits an unanswered owner decision defaults to the recommendation in section 10 and records it in its exit report.
- Owner time is a gate input (section 7.3); a gate waits for it rather than substituting a console-only check.

| Model | Used for |
|---|---|
| haiku 4.5 | YAML/JSON text in kickoffs, `commands.json` 3-way merges, docs and migration drafts (E1 parallel, E2 after merges), triage rows, test scaffolding from a written spec, verify-only checks |
| sonnet 5.5 | every bounded implementation lane and its tests; the code half of every kickoff |
| opus 5.5 | W2-R capture rules, W3-A civilians hit path, W4-R api record + cross-module roles, and the adversarial review at every gate |
| fable 5.1 | judge at every gate (go/no-go against the exit criteria), advisor on D1 before W2-R, reviewer of the map render rules before W1-M writes code |

---

## 9. Docket integration

All 25 open GR and 38 open TF entries (`census/docket-gang-turf.md`, statuses pulled 2026-09-30; TF-03 is `fixed` and excluded)
are placed, plus 12 new rows (TF-40..49, GR-38, CM-37; ids follow the last ids in `bugs.json`: TF-39, GR-37, CM-36). The
two verified censuses found 0 fixed-unrecorded and 0 obsolete entries; the only already-fixed item is the GR-02 note's
adjacent payout-loop bug (evidence: `GangDeleteCommand.java` L186/L301), which gets no new row. "Assigned" means placed in a
lane; an entry is closed only at its wave gate.

### 9.1 Assignments

| Lane | Entries |
|---|---|
| W0-B | TF-05, TF-06, TF-08, TF-09, TF-14, TF-32, TF-33, TF-34, TF-36, TF-41, TF-42 |
| W0-A | TF-01, TF-04, TF-07, TF-10, TF-13, TF-25, TF-28, TF-40 |
| W0-C | GR-10, GR-12, GR-15, GR-20, GR-28, GR-30, GR-32, GR-33, GR-34, GR-35, GR-36, TF-24 |
| W0-D | GR-11, GR-14, GR-23, GR-24, GR-27, GR-31 |
| W0-E1 | TF-31 (closed by design, documented) |
| W1-K | CM-37 |
| W1-M | TF-27 |
| W1-T | TF-21, TF-23, TF-29, TF-30, TF-39 (TF-21 viz half finishes in W3-V; TF-23 panel half in W2-Q) |
| W1-G | GR-16, GR-17, GR-29 |
| W1-R | GR-13, GR-21, GR-22, GR-37 |
| W2-R | TF-11, TF-12, TF-26 (documented, intentional), TF-02 (defence half), TF-48 |
| W2-N | TF-37 |
| W2-P1 | GR-19, TF-16, TF-44 |
| W2-P2 | GR-38 |
| W2-Q | TF-02, TF-17, TF-18, TF-19, TF-38, TF-45 |
| W3-A | TF-47 |
| W3-B | TF-20, TF-35, TF-46 |
| W3-V | TF-43 |
| W4-H | TF-49 (optional lane) |
| Deferred | TF-15 (Double income column: a type change through the diff engine, sub-cent loss; revisit with a money-column sweep), TF-22 (Spanish turf strings ride CM-15; new strings go to module YAML so the `_es` twin is one file later) |

Accounting: GR 25 open = 11 (W0-C) + 6 (W0-D) + 3 (W1-G) + 4 (W1-R) + 1 (W2-P1). TF 38 open = 9 (W0-B) + 7 (W0-A) + 1 (W0-C)
+ 1 (W0-E1) + 1 (W1-M) + 5 (W1-T) + 4 (W2-R) + 1 (W2-N) + 1 (W2-P1) + 5 (W2-Q) + 2 (W3-B) + 2 deferred = 39 lane slots, TF-02
in two lanes; 38 distinct ids. New rows 12 = TF-40 (W0-A), TF-41/42 (W0-B), TF-43 (W3-V), TF-44 (W2-P1), TF-45 (W2-Q), TF-46
(W3-B), TF-47 (W3-A), TF-48 (W2-R), TF-49 (W4-H), GR-38 (W2-P2), CM-37 (W1-K). `docket.assigned` in roadmap.json carries
the wave id (W0 38, W1 14, W2 15, W3 5, W4 1 = 73 = 61 open placed + 12 new; the 2 deferred are listed apart); lane
granularity lives in each lane's docket list.

### 9.2 Pinned tests to flip (red first)

`CaptureServiceStartAndCompleteTest` (TF-01, W0-A), `TurfManagerTest` (TF-05, W0-B), `RankManagerTest` (GR-12 W0-C; GR-13 W1-R),
`GangAllianceTest` (GR-35 + TF-24 one-directional case, W0-C), `CaptureServiceOwnedTurfTest` (TF-12, W2-R; TF-26 re-documented),
`CaptureServiceHelpersTest` (TF-11 dead call site, W2-R), `ActiveBuffManagerTest` (TF-37 cap, W2-N), `GarrisonManagerTest`
(TF-20, W3-B), `RankPermissionApplierTest` fixtures naming `gangland.turf.capture/contribute` (deleted, W2-P1),
`CaptureServiceStartAndCompleteTest.ownedTurf_multipleChallengerGangsBlockStart` (TF-48, W2-R: it pins today's 1-vs-1
block; re-scope it to the plurality rule, red first). The W1 kickoff's
`CaptureEligibility` extraction is the one exception: characterization tests, green before and after.

### 9.3 New triage rows (DONE 2026-09-30 by the orchestrator: written to `brainstorming/bug-docket-2026-09-06/triage/turf.txt`, `gangs-ranks-mail.txt`, `commands-messages-platform.txt` as 8-field rows, both dockets rebuilt and republished - Gangland 612, cross 1064; W0-E1 only re-checks them)

| Id | Tier | Finding | Fixed in |
|---|---|---|---|
| TF-40 | P2 | Turf delete does not cancel an in-flight contest (TF-03 residual): boss bars and guards linger | W0-A |
| TF-41 | P3 | `TurfModuleConfig.garrisonManager`/`turfManager` call `initialize()` and the LIFECYCLE convention calls it again | W0-B |
| TF-42 | P3 | `TurfPowerupManager.remove` never purges the `pending` chunk queue | W0-B |
| TF-43 | P2 | `TurfVisualization` ~4,200 particles/s per viewer on a 256 x 256 turf, no clipping | W3-V |
| TF-44 | P1 | `Member.contribution` mixes bank share and turf points, so turf farming claims bank money on disband | W2-P1 |
| TF-45 | P2 | `gangland.turf.capture/contribute/upgrade` have no production consumer; any recruit spends the bank | W2-Q (`upgrade` wired), W2-P1 (fixtures deleted) |
| TF-46 | P2 | `GarrisonDeployListener` consumes stock before `deploy` no-ops without Citizens | W3-B |
| TF-47 | P3 | Guard squads key on (faction, target) with no turf scope; two turfs' guards recruit each other | W3-A |
| TF-48 | P1 | One rival alt standing inside an owned turf blocks every capture start ("exactly one challenger", `CaptureService` L209) | W2-R |
| TF-49 | P3 | Defending your own turf raises your wanted level (design gap) | W4-H (optional) |
| GR-38 | P2 | `Gang.State` defaults to `OPEN` in the constructor, table default and `parseState` fallback; wiring `gang join` would make every stored gang a walk-in gang | W2-P2 |
| CM-37 | P2 | `gangland.command.main` and every framework-registered node default to OP, so a non-op player cannot run `/glw` on a fresh install | W1-K |
| - | - | GR-02 adjacent "payout re-reads balance": already fixed; record in the GR-02 note, no row | - |

---

## 10. Decisions for the owner

| Id | Question | Options | Recommended | Blocks |
|---|---|---|---|---|
| D1 | When can an owned turf be attacked? Today: only after the whole owner gang has been offline 10+ min **and** nobody is inside | A `OWNERS_ONLINE`: attackable while a qualifying owner-gang member (joined >= 24 h ago; every member of a gang younger than 24 h) is online, after a 2-minute wake-up shield when the first one logs in; shielded 10 min after the last logout; a running capture continues through logouts; the start needs a strict plurality of attackers over owners+allies and over any other single gang. B `ALWAYS`: attackable after cooldown, twice as slow when all owners are offline (recorded, not shipped). C `OWNERS_OFFLINE`: today's rule plus alerts and digest (shipped as the small-server switch). D scheduled war windows | **A** (C stays one config line away). One sentence explains it, fights happen with owners present, guards have a visible job, and the 4-day inactivity release plus the activity gate handle gangs that never log in | W2-R |
| D2 | How is snowballing bounded? | A cap = `min(Ceiling 6, Base 2 + 1 per level)` with XP paid only for active turfs (a dormant gang earns none). B cap = `min(6, 1 + members seen in 7 days / 2)` via `OfflinePlayer.getLastPlayed()` at payout time, level cosmetic. C upkeep per turf. D none | **A**: one visible number, level has a consumer, the activity gate closes the passive-farm loop that made B attractive; B is one `getLastPlayed()` loop away if playtests still see alt gangs lifting their cap | W2-R, W2-P1 |
| D3 | Snap new turf boundaries to chunks? | A on for new turfs only. B off. C on + migrate existing | **A** | W1-T |
| D4 | Should guards count on the capture bar? | A yes, each live guard inside counts as one defender (cap 4); delete `reinforced_defense`. B wire `CAPTURE_DEFENSE_BONUS` as invisible defenders. C neither | **A**: visible cause, clear objective for attackers | W2-R, W2-Q |
| D5 | Unclaimed capture | A one bar, same math as owned turfs. B keep two-phase CLAIM/CONSOLIDATE | **A** | W2-R |
| D6 | Persistent "Territory of X" boss bar | A retire; the action-bar HUD line carries it. B keep both | **A**: the boss bar then only means "fight" | W1-H |
| D7 | Open gangs | A wire `OPEN`/`INVITE` with `gang join`; `INVITE` becomes the stored default and every existing `OPEN`/`CLOSE` row migrates to `INVITE` (only `gang recruiting open` opens a gang). B delete `Gang.State`. C leave inert | **A** with the migration; without it every existing gang is a walk-in gang | W2-P2 |
| D8 | Gang bounty | A delete the dead event and listener; placeholders return 0. B build bounty posting. C leave inert | **A** | W2-P1 |
| D9 | Economy defaults | A keep today's numbers (income 100 per 10 min, boosts 5k-15k) with `Max_Income_Multiplier` 2.0. B mechanics-depth table (income 250 per 10 min = 36,000 per active turf per day, guard 1,000, small boost 300, large 1,000) | **B** as shipped defaults in module YAML; at 100 the boosts never pay back; the activity gate (`Income_Idle_Hours` 24) is the faucet's brake and `Max_Reserve` 12 the sink's ceiling | W2-N, W2-Q |
| D10 | Turf fights and wanted level | A never. B defender exemption inside their attacked turf, optional W4 lane. C on in W3 | **B** | W4-H |
| D11 | Revision and branch | A 0.14.0 minor on `gang-turf-territory`, matching the 0.11/0.12/0.13 phase cadence. B 0.13.1 patch per the memory's big-wave habit | **A**; B if 0.13.0 must stay shippable without this wave | W0-K |
| D12 | HUD defaults | A inside-turf line on for gang members (priority 9, every 2 s), nearest-turf line and auto-map off. B everything off until a manual check with Bartizan's ammo action bar | **A**, verified in the W1 manual checklist with a gun and a car; `map hud off` per player | W1-H |
| D13 | Ranks | A keep the global tree, seed a 3-rank preset on empty tables only. B insert `officer` into existing trees. C per-gang trees (schema) | **A** | W1-R |
| D14 | Unused guards when a capture ends | A refund to the reserve. B spent | **A** | W3-B |
| D15 | Stored `Member.contribution` values at the split (today they mix bank share and turf points) | A keep them (turf farmers over-claim once on disband). B reset every value to 0 on first 0.14.0 boot; disband with all-zero contributions splits equally. C recompute from deposits (no history exists) | **B**: the P1 row TF-44 exists because the mix is exploitable; one documented reset beats a permanent distortion | W2-P1 |

---

## 11. Risks

| Risk | Mitigation |
|---|---|
| D1 changes who can attack when on live servers | `turf_rules.yml` ships `Capture_Window`; the migration doc leads with it and names `OWNERS_OFFLINE` as the small-server setting |
| Offline hoarding: log off, keep paying, keep XP | income and XP need a qualifying owner online within `Income_Idle_Hours` 24 (`dormant - pays nothing` otherwise); `Inactivity_Release_Days` 4; TF-09/TF-10 are W0 prerequisites |
| An infiltrator or alt joins an Open gang to open or hold its shield | only members with >= `Window_Min_Member_Age_Hours` 24 refresh the timestamp (young gangs count everyone); `INVITE` is the default; `gang join` sets the join date |
| Login snipe: campers hit the turf the second an owner logs in | `Wake_Up_Shield_Seconds` 120 with the `shield ends in 1:40` alert; defenders count at once, attackers after 15 s; `Min_Capture_Seconds` 60 bounds zerg speed |
| Stalled contest locks a turf forever | `Max_Contest_Minutes` 10 -> `STALLED`, defenders hold, normal cooldown, both sides notified |
| Camping your own turf as immunity | strict-plurality start replaces "zero defenders inside" (W2-R), red -> green test |
| Ally swap (ally inside abandons the alliance and counts instantly) | presence entries are re-stamped when the relation to the owner changes |
| Chat font alignment differs by client, GUI scale, resource pack | 6 px glyph class only; width-neutral italic/underline; plain-text legend <= 300 px; manual checklist at scales 1-4; `Width`/`Height` config; Floodgate players get the text form |
| Colour-blind players cannot separate own/ally/rival or read state | glyph = identity, italic/underline = state, legend in words; colour is the third channel, never the only one |
| Bedrock/Geyser players get no hover | the legend carries every fact; click gives the card; text form by default |
| Auto-map or HUD floods chat or costs TPS | turf-set + enter/leave + 5 s + hash gates, run-length components, HUD reads the tracker cache; auto-map default off |
| HUD line starves Bartizan's ammo bar and the gadget HUDs | priority 9 every 2 s (a priority-10 HUD displaces it in under a second); inside-turf only; manual checklist with a gun and a car; `map hud off` |
| `/glw` becomes reachable by every player on live servers | only player verbs default `true`; admin and destructive nodes stay OP; `PluginYmlPermissionsTest`; migration doc entry |
| Lanes touching `CaptureService`, `TurfDefenderDeployer`, `TurfIncomeDistributor`, `GangPresenceTracker`, `CivilianService` collide | single-owner file table per wave; kickoffs create every shared signature and stub; W0-B -> W0-A chain; fixed merge order |
| The civilians `alertFaction` change regresses cops/civilian behaviour | applies only when the provider is not the default `CivilianService`; W0-F baseline; opus lane; cops tests in the W3 gate |
| H13 slips or lands at the wrong Keystone pin | W0-W3 need nothing from it; W4 waits for the four preconditions; three of the five branches pin 1.13.0 and two pin 1.14.0, so the merged 0.13.0 must land at 1.14.0 with Keystone master merged |
| `smoke.py` drives a Paper test server, console-only, one server | the Spigot 1.16.5 compile floor is the API gate; no `io.papermc` imports (review checklist); one manual Spigot boot per release; owner time budgeted per gate; one server lock |
| Economy imbalance | every number in module YAML; bounty is a transfer, never minted; multiplier cap; only in-cap active turfs pay; `Max_Reserve` |
| Digest value shrinks under `OWNERS_ONLINE` | in-memory ring buffer only; a table waits for a playtest ask |

---

## 12. Out of scope

Player-claimable chunks, overclaim, per-chunk protection; block protection inside turfs; declared raids with fees, warnings
and windows, and persistent gang wars; the `ALWAYS` capture window; member and ally caps; loss shields; per-gang rank trees;
gang bounty posting; gang home/HQ beyond the existing `WaypointType.GANG` waypoint; gang vault items; per-member turf score and
`/glw turf top`; `/glw gang ping` and man-down callouts; neighbour/allied responders; Medic field care; policed-district cop
dispatch; the map item; a persisted `turf_event_log` table; `TurfChunkIndex` (until a measured server needs it); BlueMap,
squaremap, Pl3xMap, turf holograms, the `TurfMapLayer` cops overlay seam; Spanish turf strings (TF-22); the income column
type (TF-15); any `RadioVoice`/`SquadRadio` api change; Keystone changes before a second consumer; weapons on guards
(Bartizan owns weapons); any Paper API.

---

## 13. Scorecard of the three drafts

Scores 1-10. Lenses: player clarity, mechanical soundness, codebase fit, delivery, YAGNI.

| Draft | Clarity | Soundness | Fit | Delivery | YAGNI | One-line verdict |
|---|---|---|---|---|---|---|
| player-first | **9** glossary, one channel per job, the ten-minute script, one reason everywhere | 7 `OWNERS_ONLINE` is the right default, but it leaves the "zero defenders inside" start rule untouched (camping = immunity) and its active-members cap leaves gang level with no consumer | 7 real seams and a good file-owner table, but the `RadioVoice.audience` bump duplicates what `TurfCaptureNotifier` already does, and it gates W0-W2 on H13 needlessly | 7 six clean lanes per wave and fixed merge order, but the whole programme waits for a merge it does not need | 7 gang-owned news log with join lines, a `TurfRadio` wrapper over member-less squads, and a `%gang_bounty%` shim are machinery for outcomes the notifier and a ring buffer give for free |
| mechanics-depth | 6 the five-sentence loop is good, but declared raids add fee + warning + window + shield + snapshot + join-age: six rules where one window rule does | **8** the best exploit matrix (ABANDONED vs DEFENDED split, snapshot, hold-only XP, presence rule, plurality) | 6 `RaidService` state machine, a persisted shield column, a core `WantedKillTracker` change and two api defaults in one wave; L0.1 as opus is over-tiered | 6 spec-first L2.0 is a strong idea, but W2 is a rewrite with three opus agents and two api bumps | 4 fee, warning phase, member cap, `Max_Allies`, shield column, heat seam and `gang_levels.yml` all ship before the loop is played |
| delivery-fit | 7 rules are shown where they apply, but `ALWAYS` + an offline multiplier is a hidden number, and it keeps the two-phase claim | 7 plurality start and `Guard_Weight_Cap` are right; `ALWAYS` makes offline raiding the dominant strategy the diagnosis complains about | **9** every census correction verified (H13 file set, notifier reach, GR-02, font widths); no api change through W3; kickoff commits for shared signatures | **9** single-owner file table, shared-append rules, lane and wave gates, model tiers, true H13 independence | 8 cuts tiers, the api seam and partial glyphs; keeps a `turf_member_score` table and `/glw turf top` that nothing asked for |

Backbone: **delivery-fit**. Grafts and drops as in section 1. Contradictions resolved: capture window (`OWNERS_ONLINE` with a
wake-up shield and join-age qualification, not `ALWAYS` or declared raids); start rule (strict plurality, not "zero
defenders"); alerts (notifier, no api seam); contribution (activity-gated gang XP, not a score table); map glyphs (6 px class,
italic/underline state, relation colour always); garrison squads per target scoped per turf in W3 with the acceptance in
W0-F; H13 gates only W4; api bump only in W4 for `NpcRole`.

---

## 14. Review log (2026-09-30)

Verdicts: feasibility PASS_WITH_FIXES, gameplay-ux PASS_WITH_FIXES, delivery PASS_WITH_FIXES, completeness PASS_WITH_FIXES.
Reviewers disagreed on D2 and on the digest; both were settled against code (see the rejected rows).

### Applied

| Source | Item | Where |
|---|---|---|
| feasibility B1 | `Gang.State` default `INVITE` in constructor/table/`parseState`, `CLOSE` -> `INVITE`, load migration, `gang join` only for `OPEN` | P4.5, W2-P2, D7, GR-38 |
| feasibility B2 | `plugin.yml` `permissions:` block `default: true` for player verbs + `PluginYmlPermissionsTest`; migration entry | P2.5, W1-K, CM-37, risks |
| feasibility B3 | HUD at priority 9 every 2 s, never skip-unchanged, inside-turf only, nearest line opt-in; checklist with a gun and a car; `FuelHoldDisplayListener` note | P2.3, W1-H, D12, risks |
| feasibility B4 | per-target squads scoped per turf (`SquadKey` scope, `squadFor`, provider identity check, `FactionSquads.extras`), turf-side `TurfGarrison` holder, `TurfGarrisonSquads` provider | P5.1, P5.3, section 6 T2/T6, W3-A/W3-B, TF-47 |
| feasibility F1 | `World.rayTraceBlocks` replaces the Paper-only `hasLineOfSight(Location)` | P5.2, T3 |
| feasibility F2, delivery B6 | `GangModuleFileConfig` (W1 kickoff), `MailModuleFileConfig` (W0-D), `mail/mail_rules.yml`, `*ModuleConfigTest` | section 2, P1.4, P2.5, 7.1 |
| feasibility F3 | `TurfModuleFileConfig` in the shared table, kickoff-only registration | 7.1 |
| feasibility F4 | `turf_rules.yml` + `TurfRules` loader created by the W1 kickoff; W2 kickoff adds keys | 7.5, 7.6 |
| feasibility F5 | `findConflict` re-run on the snapped region + test | P2.4, W1-T |
| feasibility F6, F7, gameplay B8 | header and legend <= 300 px with `pixelWidth` test, width-driven packing, `+N more`, hover carries turf-level facts only (no cell coordinates/coverage), sample frame redrawn | 5.1, 5.2, 5.4, W1-M |
| feasibility F8 | callsign `[VIP] Guard 42` with a 3-4 char tag, formatter tested against 16 | P5.3, T6, W3-B |
| feasibility F9, delivery S11 | W3-W owns `gangland-turf/pom.xml` (`dynmap-api` provided + repository, reflection facade fallback); dynmap types only under `GT/map/web/**` behind a plugin-present check | 5.5, 7.1, W3-W |
| feasibility F10 | W4 api value read from the merged master (2.1 or 2.2) | section 2, P6.1, 7.8 |
| feasibility F11, delivery nits | `CopRole` path `copsncrooks/npc/police/config/`; `CivilianCombatBehavior` under `npc/state/behavior/`; `CivilianDamageListener` under `listener/civilian/` | P5.1, P6.1, W3-A, W4-R |
| feasibility F12, delivery B4 | the W1 kickoff registers every `MapCommand` and `GangCommand` child as a stub; `TurfCommand` ctor ownership per wave | 5.1, 7.1, 7.5 |
| feasibility F13, delivery B5 | W2 kickoff removes the contribution beans; W2-P1 removes the `TurfCommand`/`TurfModuleConfig` references before W2-N rebases | 7.6 |
| feasibility F14, delivery B5 | `TurfRules.cap` and the `addExperience` stub in the W2 kickoff; W2-N owns the XP call | P3.5, P4.4, 7.6 |
| feasibility F15 | `documentation/features/turf.md` and `documentation/tests/features/turf.md` marked NEW (W1-E1) | 7.3, 7.5 |
| feasibility F17 | `GangMembershipInstaller.gangsAllied` L56; `TurfFriendlyFireListener` is in cops | P1.3 |
| feasibility F18 | six placeholders in the existing dash style; `turf-` prefix registered by the orchestrator in impl | P2.3, W1-H |
| gameplay B1 | income and XP gated by `Income_Idle_Hours` 24 (`dormant`), `Inactivity_Release_Days` 4, faucet per active turf per day next to D9 | P4.2, P4.4, D9, risks |
| gameplay B2 | join-age qualification `Window_Min_Member_Age_Hours` 24 for the owners-online timestamp; young gangs count everyone; `gang join` sets the join date (verified: create and invite-accept already do) | P3.1, P4.5, W2-R, risks |
| gameplay B3 | defenders count at once, attackers after `Presence_Seconds`; `Min_Capture_Seconds` 60; `Wake_Up_Shield_Seconds` 120 with the camping alert | P3.1, P3.2, W2-R, D1, risks |
| gameplay B5 (timeout) | `Max_Contest_Minutes` 10 -> `TurfCaptureFailedEvent.Reason.STALLED`, notified as an outcome | P3.2, P4.1, W2-K, risks |
| gameplay B6, S10 | relation colour always; italic = under attack, underline = shielded; gold and dark red deleted; "Safe for" folded into "Shielded" with reasons | vocabulary, 5.1, 5.4, W1-M |
| gameplay B7 | `shielded: no Rats online (attackable when one logs in)`; no countdown on the owners-offline shield; Kings block prose corrected to columns 25-33 / rows 2-5; plurality defined (owners+allies and largest other gang); golden test does not hard-code the 15-minute cooldown | P2.2, P3.2, 5.2 |
| gameplay S1 | letter allocation viewer-first, allies, then ascending id | 5.1 |
| gameplay S2 | empty-window header with the nearest turf; gangless enter title and HUD line | 5.1, P2.3, P2.4 |
| gameplay S3 | D12 limited to the inside-turf line | D12 |
| gameplay S4 | auto-map on turf-set change or enter/leave, min 5 s | 5.1, 5.4, W1-M |
| gameplay S5 | four alert kinds, allies get the start line only, income line on the action bar | P4.1, P4.2 |
| gameplay S6 | presence entries re-stamped on a relation change (no event, no schema) | P3.2, W2-R, risks |
| gameplay S7 | hidden-post fallback, `Max_Reserve` 12, purchases logged with the buyer | P3.6, P5.2, W2-Q |
| gameplay S8 | `Turf.Capture.*` folded into `turf_rules.yml` with a legacy read; `turf_alerts.yml` merged into `turf_messages.yml`; `/glw turf rules` | section 2, P3.1, W2-K, W2-E1 |
| gameplay S9 | minutes-since-online only on the viewer's own gang's hover | 5.1, 5.4 |
| gameplay S11 | `/glw map text`, automatic for Floodgate UUIDs | 5.1, 5.4, W1-M |
| gameplay over-built | `TurfChunkIndex` cut; `ALWAYS` cut; digest table replaced by an in-memory ring buffer; placeholders cut to six; map item cut; W1 gated per module on W0-A/B (turf) and W0-C (gang) | 5.3, P4.3, 5.5, 7.5 |
| delivery B1 | W0-A owns `TurfIncomeDistributor`, `InactivityReleaseTask`, `TurfCommand` ctor and branches from W0-B's merged head; W0-B skips `TurfContributionTickTask` and adds the `TurfCreateCommand` id line | 7.4 |
| delivery B2 | `CaptureEligibility` extraction in the W1 kickoff (sonnet) with characterization tests; W1-X owns consumers only | 7.2, 7.5, 9.2 |
| delivery B3 | TF-27 to W1-M (`TurfRuntimeState`); the `TurfStatusCommand` line to W1-T | 9.1, 7.5 |
| delivery B5 (contribution) | stored `Member.contribution` handling is D15 with a migration entry | D15, W2-P1 |
| delivery B7 | worktree rules (graphify against the main checkout, absolute paths, no `git add`, CLAUDE.md edits by the orchestrator) | section 2, 7.2, section 8 |
| delivery B8 | ids for every new row (TF-40..49, GR-38, CM-37); docket writes orchestrator-only, Gangland docket first, cross docket re-seeded per wave | 9.3, section 8 |
| delivery S1 | `GangCommandsJsonParityTest` replaces the phantom count gate | P1.3, W0-C, W1-G, W2-P2 |
| delivery S2 | every test named and NEW ones marked (`TurfPlaceholderContributionTest`, `TurfHudNearestTest`, `TerritoryRasterZoomTest`, `TurfVisualizationClipTest`, `DynmapTurfLayerTest`, `TurfCaptureNotifierTest`, `RankPermissionAddCommandTest`, `GangListCommandTest`, `TurfCreateCommandTest`, ...) | 7.5-7.7 |
| delivery S3, S4 | one test-server lock; G2 at the gate; G1/G2/G3 rows written before use; owner time per gate | 7.3, section 8 |
| delivery S5 | lanes run `mvn -q -o verify -DskipTests`, never `install`; at most 3 builds | 7.2 |
| delivery S6 | W0-K orchestrator step bumps `<revision>` | 7.4, D11 |
| delivery S7 | W4 precondition checklist; master dry-run merges at the W1 and W3 gates | section 2, 7.3, 7.8 |
| delivery S8 | W4-D and W4-H after W4-R; per-module `Host_Api` note (turf too, for the classifier) | P6.3, P6.4, 7.8 |
| delivery S9 | W1-K sonnet for code; W2-P split into P1/P2; gate reviewers `parallel: false`; per-lane diffs to reviewers | 7.5, 7.6, section 8 |
| delivery S12 | every docs lane split into E1 (parallel) and E2 (after merges); core and gang jars update together | 7.4-7.7 |
| delivery nits | "35 assigned" not "closed"; "no new api surface" wording | section 1, 2, 9 |
| advisor | W1 dependencies per module (turf vs gang lanes); `GangPresenceTracker` and the `CaptureSettings` fold owned by W2-R; gang level curve named (`Gang_Level_Base` 100, `base * level ^ 1.5`, 1.4 days to level 1 with one active turf) | 7.5, 7.6, P4.4 |

### Rejected

| Source | Item | Reason |
|---|---|---|
| gameplay B4 | make D2-B (active-member cap) the recommendation and drop gang XP to an optional later lane | No per-member last-seen exists in the codebase (grep: no `lastOnline`/`lastSeen` field in any main source); B would ride `OfflinePlayer.getLastPlayed()` at payout time, which is cheap but leaves gang level with no consumer and GR-19 open. The activity gate (B1) removes the passive-farm loop B4 argued from, `Level` already carries a formula curve, so XP is ~10 lines. B stays the documented alternative. |
| gameplay B5 (decay) | bar decays at 1v0 rate after 30 s at net 0 | A second rule for the same problem; `Max_Contest_Minutes` alone ends the stall. |
| gameplay over-built (digest) | "income line + unread counter" | Replaced by the in-memory ring buffer instead: a counter cannot say what happened; the buffer is one class, no table. |
| gameplay over-built (XP) | replace the XP subsystem | See B4. |
| feasibility F16 | persist a last-quit stamp in the turf log | Superseded: the log table is gone; the stamp lives in the ring buffer's memory map from `PlayerQuitEvent`. |
| feasibility F19 | re-read the docket split at W0-E | No change needed; the lane accounting checks out and W0-E1 re-reads statuses anyway. |
| delivery S10 | move the W3 map lanes into W2 | Kept in W3 but allowed to start after the W1 gate; W2 already carries the opus lane and five sonnet lanes. |
| completeness fix 1 | rename `recommended` to a nested `recommendation` object | The roadmap.json schema given to this wave specifies `"recommended": "A"`; the file already matches it. |
| completeness "lane model structure" | typed lane model | No such requirement exists; lanes carry the twelve schema keys. |
