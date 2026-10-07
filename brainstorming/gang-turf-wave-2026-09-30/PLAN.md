# Gang and turf roadmap - final plan (Gangland 0.14.0)

Judge/synthesizer: `fable 5.1`, wave `gang-turf-wave-2026-09-30`, written 2026-09-30, **finalized 2026-09-30 after four
reviews** (`reviews/feasibility.md`, `reviews/gameplay-ux.md`, `reviews/delivery.md`, `reviews/completeness.md`; every
applied and rejected item is in section 14). **Gangs+ parity addendum folded in 2026-09-30** (second pass: judge of
`parity/design-fold-in.md` and `parity/design-parity-wave.md`; the design is section 15, the lanes 7.10-7.11, the
decisions D16-D24, the scorecard and log in sections 13-14, the final mapping `parity/PARITY.md`). **Parity reviews applied
2026-09-30 (third pass, section 14.2:** `reviews/parity-feasibility.md`, `reviews/parity-delivery.md`,
`reviews/parity-completeness.md`; NEW lane W6-Y; vocabulary renames `pvp`, `glw-gang-pvp`, `rank-position`). Planning only; no product code.
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

**Gangs+ parity addendum (section 15).** The owner's second goal - every Gangs+ feature (GP-01..45, `competitors/gangsplus.md`)
available in Gangland's gang system, Gangland outclassing every gang plugin - adds two waves and leaves W0-W4 as planned
(four scope-line amendments A1-A4). **W5 "Parity: gang core"** starts after the W2 gate and runs beside W3 on disjoint
files: levels you earn on turf and pay to claim (D16), member and ally caps (D17), safehouses and rally that cannot break a
turf fight (D18), one friendly-fire rule for fists, bows, potions and Bartizan guns (D20), gang and ally chat with spy,
stats, ranking and profiles, staff verbs under `/glw option gang`, public api events under one bump (D23). **W6 "Parity:
fights, WorldGuard, the parity gate"** runs beside W4: a NEW runtime module `gangland-gang-fights` (D19), the soft WorldGuard
flag, and an audited **"Gangs+ parity reached"** gate. Tally after the plan: HAVE 3, DELIVERED 42, EXCLUDED 0 (GP-33 with one
recorded partial exclusion). Every verb, node, key and message is Gangland's own (15.1). 0.14.0 merges after the later of the
W4 and W6 gates.

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
  no `VERSION` bump). **One additive minor bump per release branch (D23):** W5-K bumps `GanglandApi.VERSION` by one minor
  from whatever the merged master carries (`2.0 -> 2.1`; `2.2` if H13 has already bumped it for `FieldCareSettings`) and
  adds the parity batch (`GangMembershipView` default methods, `events/gangs/*`, `PlaceholderContribution.resolveGlobal`,
  a `LocalizedModuleYaml` body change); W4-R adds the additive record `NpcRole` **NEW** under the same value (A2). Modules
  that read the new members (gang, civilians, turf, fights; cops after W4) set their `Host_Api` to that value, the rest stay
  `2.0` (Keystone 1.9.2 minor floor).
- Module edges: turf `Depends: [civilians, gang]`, mail `Depends: [gang]`, cops `Depends: [turf, civilians]`
  + `Plugins: [Bartizan]`, **NEW** fights `Depends: [gang]` and no `Plugins:` (loads without Bartizan and Citizens). Turf
  never depends on cops. Civilians stays gang-free (it gains a generic squad `scope` and an `extras` hook, never a gang
  type; its Bartizan-impact friendly-fire listener reads the api fact `GangMembership.pvpBlocked`). WorldGuard is a soft
  dependency of the core (`softdepend`, W6-W), read by name from the gang module.
- Gang statistics are gang-owned member counters (`member.kills|deaths|assists`) fed by a HIGH `ignoreCancelled` damage ledger in the gang
  module; core `User` kills/deaths and impl `EntityDamageListener` are untouched by parity, so W5 never collides with W4-H.
- Sides of a turf contest are frozen at its start (W2-R, A4): a member whose `gangJoinDateLong`, or an ally whose
  `GangAlliance.since`, is later than the contest-start stamp is a bystander until the contest ends; safehouse and rally
  teleports are vetoed around a turf under attack (W5-T) and never grant invulnerability.
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
6. Everything a gang plugin does, your gang does here too: levels you earn on turf and pay to claim, safehouses that cannot
   break a fight, gang and ally chat, friendly fire you control, rankings, profiles and arena fights with a bet.

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

### P7 Gangs+ parity: gang core (W5, section 15)

Why: the owner's goal. Every GP row in Gangland's own words; the GP ids sit in the docket column. Mechanics in full: 15.2-15.6.

| Id | Feature | Player view | Mechanics | Code | GP / docket |
|---|---|---|---|---|---|
| P7.1 | Membership lifecycle, staff tools, purge | Join, leave, kick, disband and transfer behave the same from every entry point; staff disband, hand over, fix a bank or reset stats from the console; inactive gangs purged after a preview | **NEW** `GangMembershipService` (one join/leave/kick/disband/set-leader path; cap; combat-tag refusal; `Commands.On_Join|On_Leave|On_Kick` with `%gang%`, `%gang_id%`, `%player%`; api events fired once); **NEW** `GangDisbandService` from `GangDeleteCommand`; **NEW** `GangLeadership` from `GangTransferCommand`; `/glw option gang disband|leader|bank|safehouse|stats reset|spy|purge` (console-safe, every write logged); **NEW** `GangPurgeTask` off by default (D24) | `GG/gang/membership/**`, `GG/gang/purge/**`, named gang and mail commands, `GangOptionContribution` | GP-01, 02, 03, 05, 24, 40 |
| P7.2 | Levels you earn, then pay to claim | Held turf earns XP; `/glw gang upgrade` shows the fee, `upgrade confirm` pays it from the bank; each level raises members, safehouses, allies, prefix and (D2) the turf cap | D16 C: **NEW** `GangLevelTable` + `GangLimits` (`Levels.Level_<n>`: `Cost`, `Max_Members`, `Max_Safehouses`, `Max_Allies`, `Chat_Prefix`, `Commands`); XP banked with `Level.addExperience(xp, false, null)`; the claim checks the XP bar in gang code (core `Level.addLevels` L74-79 levels even below it) then `addLevels(1, event)` + fee; `Cost: 0` auto-claims; rewards for online and offline members; api `GangLevelReachedEvent`; caps grow-only | `GG/gang/level/**`, `GangManager`, **NEW** `GangUpgradeCommand`, mail ally request/accept | GP-10, 11, 12, 25 |
| P7.3 | Minimum ranks and the preset | The server says which rank may invite, kick, withdraw, set a safehouse, ally or fight; a denied action names the lowest rank that can | `Ranks.Minimum` map + one `Tree.isDescendant` clause in `GangPermissions.allows`; `Ranks.Preset` list for the W1-R seed (empty table only, D13 A); no node inheritance | `GG/gang/permission/**`, `GG/gang/rank/RankSeed*` | GP-07, 08, 09 |
| P7.4 | Gang chat, ally chat, spy, token, event | `gang chat [msg]`, `gang chat allies [msg]`; level prefix; staff spy; `{glw_gang}` for chat plugins; a chat event | Ally channel in the P4.5 listener (main-thread snapshots); **NEW** `ChatSpyService`; `Chat.Log_To_Console`; `Chat.Public_Tag.Enabled` off; api `GangChatEvent` | `GangChatListener`, `GangChatCommand`, `GG/gang/chat/**` | GP-13, 14, 15, 16 |
| P7.5 | One friendly-fire rule | `gang pvp on|off` covers fists, bows, potions, fire, pets, TNT and guns; allies only when both agree; per world or region; your own arrow always hits you; guards always safe from their own side | **NEW** pure `FriendlyFireRule` (15.5) + api fact `GangMembership.pvpBlocked` read by `GangMembersDamageListener`, **NEW** `PotionFriendlyFireListener` and civilians' impact listener; **NEW** `DamageLedger` (HIGH, `ignoreCancelled`, damage > 0) -> `CombatTag` + assists; **NEW** `CombatTeleportGuard`; **NEW** cops `RestrainedTeleportListener` (a cuffed or jailed player's warm-up never fires); cops guard immunity untouched | `GG/gang/combat/**` (pure rule, tag), `GG/listener/gang/combat/**` (listeners, scanned package), `GangMembersDamageListener`, `GangMembershipInstaller` delegate `FriendlyFirePolicy`, civilians `GangAllyWeaponImpactListener` (body), cops `listener/police/RestrainedTeleportListener` | GP-17, 18, 19, 33, 44; GR-39, GR-40, GR-41, CJ-43 |
| P7.6 | Safehouses and rally | `gang safehouse set|remove|list|<name>`; `gang rally [safehouse]` asks online members, each answers `rally join`; nothing works into, out of or next to a turf under attack, and a gang in a turf fight teleports nowhere; no invulnerability on arrival | **NEW** `gang_safehouse` table; transient api `Waypoint` (GANG, `shield` 0) through `WaypointTeleport`; **NEW** `SafeSpot`; cap from `GangLimits`; refused in a gang-disabled world (`GangWorldPolicy`); **NEW** cancellable `GangSafehouseSetEvent`; turf: **NEW** `TurfTeleportGuardListener` (source under attack; destination under attack or within `Teleport_Guard_Buffer_Blocks` 48 of it; destination in a non-allied turf; the player's gang is owner or challenger in any running contest), **NEW** `TurfSafehouseSetListener` | `GG/gang/safehouse/**`, `GG/gang/database/**/safehouse/**`, `GT/listener/**` (two NEW) | GP-26, 27, 28, 42 |
| P7.7 | Stats, ranking, profiles, placeholders | `gang profile [player]` + menu; `gang ranking [stat] [page]` in chat, menu and hologram tokens; staff reset; nothing counts in gang-disabled worlds or between friends | **NEW** `GangStatsListener` (death + downed at MONITOR, killer from the HIGH ledger, assists, farm guards: same gang, allies, gangless or young victims, per-victim daily cap) -> `member.kills|deaths|assists`; **NEW** `GangStatsService` snapshot every 60 s; `GangStatContribution` seam (turf: turfs held; fights: W/L); new placeholder tokens + `gang_board_<stat>_<n>_<field>` via api `resolveGlobal`; `player_profile.yml`, `gang_stat.yml`, `gang_ranking.yml` | `GG/gang/stats/**`, `GG/listener/gang/stats/GangStatsListener`, `GangRankingCommand`, `GangProfileCommand`, `GangPlaceholderContribution`, `GangFilterAdapter`, impl `GanglandPlaceholder` (null-player branch), `GT/stat/**` | GP-20, 21, 22, 23, 34, 39, 43 |
| P7.8 | Worlds, names, notice, messages, labels, api | Gangs off per world; any-language display names with hex; `gang notice` on login; every message a YAML line in any language; `/gw`, `/gwc`, `/gwa`; `gang info <player>`; members by rank; a query api and events for other plugins | **NEW** `GangWorldPolicy` (root gate, `Gang.Enable` global switch); D21 `Name_Pattern` + `Display_Name.*`; `gang.notice`; **NEW** `gang/gang_messages.yml` + `GangNotifier` titles; D22 **NEW** `IMPL/command/GangShortLabelExecutor`; api batch under one bump (D23) | `GangWorldPolicy`, `GangCommand` (root gate), `GangInfoCommand`, `GangMembersCommand`, `GangNameRules`, `GangNoticeCommand`, `MemberJoinListener`, `GG/gang/notify/**`, api `GangMembershipView` + `events/gangs/*` | GP-04, 06, 32, 35, 36, 37, 38, 45 |

### P8 Gang fights and the parity gate (W6, section 15 and `parity/fights.md`)

| Id | Feature | Player view | Mechanics | Code | GP / docket |
|---|---|---|---|---|---|
| P8.1 | Arenas | `/glw arena create|corner|spawn|exit|size|finish|show|list|tp|on|off|delete|reload` from where staff stand; several arenas; hand-editable YAML | 3D `ArenaBox`, N spawns per side, one Exit; `ArenaDraft` -> `ArenaRegistry`; `fight/arenas.yml` atomic write + hot reload; save validation (spawn outside the box, missing Exit); "place arenas outside turfs" is a documented admin rule printed by `arena finish` (DF8 A: fights cannot see turfs) | fights `arena/**`, `command/arena/**` | GP-30 |
| P8.2 | Fight flow, bets, records | `gang fight propose <gang> <size> [bet]`, `accept`, `decline`, `enlist`, `drop`, `info`, `arenas`, `stats`; the bet leaves both banks at accept, the winner takes it, a draw refunds; ranked needs a minimum bet, equal teams, 24 h members | `Fight`, `FightState`, `FightManager`, `FightRules` (pure), `FightClock`, `PlayerSnapshot`; escrow with second-withdraw rollback, `FightSettlement`, `abortAll` on disable; allied gangs refused; ranked rules; `fight_stats`, `fight_player_stats`, `fight_record`; `FightWinsStatContribution`; api `GangFightEndedEvent` | fights `fight/**`, `command/fight/**`, `database/**`, `placeholder/**`, `stat/**` | GP-29, 31, 20, 21 |
| P8.3 | Fight guards | A fight death raises no wanted level, drops nothing, never enters the downed state; fighters cannot leave, `/tp`, or quit without forfeiting | `FightDamageListener` at NORMAL before impl `EntityDamageListener` (HIGH) and `CustomPlayerDeathListener` (HIGHEST); containment; namespace-proof command filter with a bypass node; session (quit, join eject, death fallback); `FightWantedGuard`; `FightGangDeleteListener`; ordering proven live in W6-F | fights `listener/**` | GP-31, 41 |
| P8.4 | WorldGuard flag, parity audit, module wiring | `glw-gang-pvp` allow/deny in WorldGuard regions; `/glw module install fights|gang|lootchest`; an audit proves every GP row | W6-W: `softdepend: WorldGuard`, **NEW** `IMPL/hook/worldguard/WorldGuardFlagRegistrar` from `Gangland.onLoad` behind a plugin-present check, **NEW** `GG/gang/combat/region/WorldGuardRegions` (static factory, the only gang class naming WorldGuard) behind a plugin-present check in the type-free `FriendlyFireRegionSource` bean; W6-K: module skeleton, `ModuleInstalls.OFFICIAL` + gang, lootchest, fights (CM-38); W6-X: `parity/audit.md` (names each row's test or owner step, flips nothing); W6-Y after the owner session and W6-J: PARITY rows flipped to HAVE, `roadmap.html` rebuilt | impl hook, gang region, `ModuleInstalls`, `gangland-features/pom.xml`, `gangland-build/pom.xml` | GP-18, 41; CM-38 |

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
| **Parity rows (W5/W6, section 15.7):** `gangland-impl/src/main/resources/plugin.yml` | orchestrator: W5-K adds the parity player nodes, the `gangland.command.player` and `gangland.admin.gang.*` parents and the D22 label commands; W6-K the fight player nodes and `gangland.command.arena.*`; W6-W `WorldGuard` in `softdepend` beside W3-W's `dynmap` line |
| `API/GanglandApi.java` `VERSION` + every `module.yml` `Host_Api` | one minor bump per release branch (D23): W5-K bumps and adds the batch; W4-R adds `NpcRole` under the same value (A2); at the master merge the orchestrator reconciles with H13's value and rewrites the `Host_Api` lines of gang, civilians, turf, fights (cops after W4) |
| `GG/command/sub/gang/GangCommand.java`, `GG/command/sub/gang/ally/GangAllyCommand.java` | **W5-K only** for wiring: registers every new child as a stub (`ranking`, `upgrade`, `safehouse`, `rally`, `profile`, `pvp`, `notice`, `chat allies`; `ally pvp`), adds the `GangWorldPolicy` root gate, and **re-wires `initializeArguments` once** so every child (existing and new) takes the 15.7 seam types (`GangMembershipService`, `GangDisbandService`, `GangLeadership`, `GangLimits`, `GangSafehouseService`, `CombatTag`, ...) as constructor arguments; no lane touches either constructor call block afterwards. W5-U fills the permission filter in `GangCommand.help` (GP-04) |
| `GG/gang/GangModule.java` + `GangModuleTest` | **W5-K only**: registers one no-arg `@Configuration` per bean-producing lane (`GangMembershipConfig` M, `GangLevelConfig` L, `GangCombatConfig` F, `GangSafehouseConfig` H, `GangChatConfig` C, `GangStatsConfig` S, `GangUxConfig` U; all **NEW**, empty at the kickoff; precedent: civilians registers three) and flips the `GangModuleTest` `configurations()` pin red first. The listener package stays `org.luckyraven.gangland.listener.gang` (the Keystone scan is recursive): **every new gang listener lives under `GG/listener/gang/**`**, never under `GG/gang/**` |
| `GG/gang/command/option/GangOptionContribution.java` | W5-K registers the staff stubs; W5-A fills them and the four `(Player) sender` casts of the existing `rank` verbs (GR-42); W5-S fills `stats reset` |
| `GG/gang/GangConfig.java` | **W5-K only**: re-parameterizes the five existing beans that lanes need (`gangPlaceholderContribution`, `gangOptionContribution`, `gangItemSourceContribution`, `gangFilterAdapter`, `gangMembershipInstaller`) with the final seam signatures; lane beans go in the lane's own config class above, never here |
| `GG/gang/GangMembershipInstaller` | W5-K gives it a constructor over the seam types and one delegate per concern with trivial defaults; W5-M fills **NEW** `MembershipQueries`, W5-F **NEW** `FriendlyFirePolicy` (`pvpBlocked`), W5-C **NEW** `ChatChannels` (`chatChannelOf`); nobody edits the anonymous view |
| `API/data/teleportation/WaypointTeleport.java` | **W5-K only** (body): the cancelled and world-missing paths clear `countdownTimer` and `totalDistance` (LS-35), a downed player is refused before the warm-up (LS-36) |
| `gangland-core/.../core/user/Level.java` | **W5-L only**: `addLevels` subtracts XP and counts a level only after a non-cancelled event (US-43); impl user-level tests flipped red first |
| `GG/gang/{Gang,member/Member,GangAlliance}` + `GangTable`, `MemberTable`, `GangAllianceTable` + repositories | **W5-K only**: `gang.notice`, `gang.friendly_fire`, `gang_ally.friendly_fire`, `member.kills|deaths|assists`, each with a round-trip test; W2-P1 (`Level` construction) and W2-P2 (`Gang.State`) are already merged when W5-K runs |
| `GangModuleFiles`/`GangModuleFileConfig` | W5-K registers `gang/gang_messages.yml` (kickoff-only rule unchanged) and extends `GangModuleConfigTest` |
| `gangland-features/gangland-gang/src/main/resources/gang/gang_rules.yml`, **NEW** `gang/gang_messages.yml`; W6: `fight/fight_rules.yml`, `fight/fight_messages.yml` | the kickoff writes every parity section and lays them out **one top-level section per lane** under blank-line headers; a lane edits only its own section (a key K named differently is fixed in the lane's section and reported); **NEW** `GangRulesKeyParityTest` / `FightRulesKeyParityTest` (every key a loader reads exists in the shipped YAML and vice versa); the haiku YAML gets a sonnet pass with the `gangland-yaml-review` skill before fan-out |
| `gangland-features/gangland-turf/src/main/resources/turf/turf_messages.yml` | W3-B and W5-T by section (W5-T adds a `Teleport_Guard` section only) |
| `gangland-features/*/src/main/resources/commands.json` (parity waves) | the kickoff writes the **final** entry for every verb, argument shape included (all shapes are in 15.1); a lane edits `commands.json` only when it changes an argument shape and reports it; E2 proofreads descriptions. `GangCommandsJsonParityTest`/`FightCommandsJsonParityTest` must be green inside every lane |
| `documentation/migration-0.14.0.md` (parity waves) | one E2 lane per merge window: W3-E2, then W5-E2, then W4-E, then W6-E2; never two at once |
| `IMPL/config/GameplayConfig.java` (YAML menu list), `IMPL/data/placeholder/worker/GanglandPlaceholder` (null-player branch) | W5-K only |
| `IMPL/config/WiringConfig.java`, **NEW** `IMPL/command/GangShortLabelExecutor` | W5-U only |
| `GT/TurfModuleConfig.java` | existing rule; W5-T appends beside W3-B |
| `GC/listener/gang/GangAllyWeaponImpactListener` | W5-F only (body switched to the api fact); W3-A never touches it |
| `gangland-features/pom.xml`, `gangland-build/pom.xml`, `IMPL/command/sub/module/ModuleInstalls.java` | W6-K (fights module line, jar copy, `fights`/`gang`/`lootchest` ids). Expected conflict at the W4 master merge: H13 adds `gangland-healthbars` to `gangland-features/pom.xml`; keep both lines |
| `gangland-impl/pom.xml`, `gangland-features/gangland-gang/pom.xml`, root `pom.xml` (enginehub repository) | W6-W only (`worldguard` provided); the root entry is an orchestrator line |
| `IMPL/Gangland.java` (`onLoad`) | W6-W (one line: the WorldGuard flag registrar behind a plugin-present check) |
| `gangland-impl/src/main/resources/inventory/*.yml` | W1-G (existing three); W5-S (`gang_stat.yml` content, NEW `player_profile.yml`, NEW `gang_ranking.yml`) |
| mail `GangInviteAcceptCommand`, `GangAllyRequestCommand`, `GangAllyAcceptCommand` | W0-D in W0; W5-M (invite accept), W5-L (ally request/accept) in W5. `GangAllyAbandonCommand` is **not** a mail file: it lives in the gang module (`GG/command/sub/gang/ally/GangAllyAbandonCommand.java`, W5-U) |

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
boundaries: no module imports impl, turf never imports cops); **orchestrator** go/no-go against the exit criteria; console smoke
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
| W1-M map core | sonnet (**the orchestrator reviews section 5 before code**) | `TerritoryRaster` (incl. `pixelWidth`, legend packing), `TurfMapRenderer`, `MapCommand` body, `MapAutoCommand`, `MapTextCommand`, `MapLegendCommand`, `MapAutoTask`, version counter; TF-27 COOLDOWN derived on load; **A1 (parity, GP-06):** the gang glyph is the first `A-Z` letter of the colour-stripped display name, else of the ASCII `name`, else a free letter, so a display name in any script never breaks the 6 px glyph class | `GT/map/**` except HUD files, `GT/data/TurfRuntimeState`, `GT/manager/TurfManager` (version hooks) | TF-27 | `TerritoryRasterTest` (majority, visibility guarantee, letter allocation viewer-first with collisions and `I` skipped, relation colours, italic/underline state, fixed north, centred viewer, `pixelWidth(header|legend) <= 300` with a small width table, legend packing + `+N more`, empty-window header), `TurfMapRendererTest` (run-length, click text, owners-only hover, own-gang-only online time), `MapAutoTaskTest` (set change, enter/leave, 5 s), NEW `TurfRuntimeStateTest` (TF-27); golden test reproduces section 5.2 without hard-coding the cooldown; A1 case: Cyrillic/CJK/hex display name -> the name's ASCII initial, then a free letter | screenshot at GUI scale 2 shows aligned columns; the block is 10 lines |
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
| W2-R capture rules | **opus** | `Capture_Window` (`OWNERS_ONLINE` default, `OWNERS_OFFLINE`) inside `CaptureEligibility`; wake-up shield; qualifying-member timestamp; contest lock; strict plurality (owners+allies and largest other gang); attackers-only presence with countdown; relation-change re-stamp; `Min_Capture_Seconds`; `Max_Contest_Minutes` -> `STALLED`; single bar (D5); guards count capped; cap check in `tickIdle` + re-check at `complete`; TF-11 dead code; TF-12 grace; TF-26 documented; stop reading `CAPTURE_DEFENSE_BONUS`; `CaptureSettings` keys folded; **A4 (parity C1): sides frozen at contest start** using the contest-start stamp this lane adds for `Max_Contest_Minutes`: `classify` counts a member on any side only if `Member.gangJoinDateLong` precedes the stamp, and an ally for the owners only if `GangAlliance.since` (`Gang.java` L95) precedes it; later joiners and later allies are bystanders until the contest ends (pure rule, no listener, no schema) | `GT/capture/**`, `GT/task/{TurfLocationTracker,GangPresenceTracker}`, `GT/listener/GangPresenceListener`, `GT/listener/TurfCaptureFeedbackListener` (reasons), `GT/listener/TurfBossBarListener` (counts) | TF-11, 12, 26, TF-02 (defence half), TF-48 | flip `CaptureServiceOwnedTurfTest` (TF-12), `CaptureServiceHelpersTest` (TF-11); table tests window x wake-up x presence x guards x cap; plurality (sum and largest-single cases); alt-blocker red -> green; login-snipe (wake-up) test; young-member heartbeat ignored, young-gang all count; stall timeout; ally-abandon re-stamp; min-time clamp; A4 classify cases: member joined after the start is a bystander on the owner side and on the attacking side, ally since after the start is a bystander, both count again once the contest ends | every `CaptureEligibility` reason reachable and tested |
| W2-N alerts + income + digest | sonnet | four alert kinds in `TurfCaptureNotifier`; income activity gate + action-bar line + `Max_Income_Multiplier` + in-cap only; the `addExperience` call per active turf; `TurfEventBuffer` + `TurfDigestListener`; `/glw turf` last 3 lines | `GT/listener/TurfCaptureNotifier`, NEW `GT/log/**`, NEW `GT/listener/TurfDigestListener`, `GT/task/TurfIncomeDistributor` (body), `GT/command/TurfCommand` (last 3 lines) | TF-37 | NEW `TurfCaptureNotifierTest` (audience anywhere, allies start-only, throttle, failure and stall notify), NEW `TurfEventBufferTest` (ring, per-player since-quit, restart fallback), income gate + cap test, XP call test with a recording `GangManager` fake | a logged-out owner sees the digest on join; a dormant gang is paid nothing |
| W2-P1 progression | sonnet | `GangManager.addExperience` body + `GangLevelUpEvent`; `Gang` level from `gang_rules.yml`; delete contribution tick/listener and their references in `TurfCommand`/`TurfModuleConfig` (TF-16, TF-44); contribution reset migration (D15); delete bounty event/listener (D8); delete `gangland.turf.capture/contribute` fixtures (TF-45 half) | `GG/gang/{GangManager,Gang}`, `GG/events/gang/*`, `GG/listener/gang/GangBountyMessageListener` (deleted), `GT/contribution/**` (deleted), `GT/listener/contribution/**` (deleted), `GT/command/TurfCommand` (reference removal, before W2-N rebases), `RankPermissionApplierTest` fixtures | GR-19, TF-16, TF-44 | level-up event fires once per level; days-to-level table test; `GangDeleteCommandTest` payout with all-zero contributions = equal split; migration test (mixed values -> 0) | gang level moves in a real session |
| W2-P2 gang social | sonnet | `GangChatCommand` + listener; `Gang.State` `INVITE` default in constructor/table/`parseState`, `CLOSE` -> `INVITE`, load migration; `gang join` (sets join date) + `gang recruiting` (D7, GR-38) | `GG/command/sub/gang/{GangChatCommand,GangJoinCommand,GangRecruitingCommand}`, `GG/listener/gang/GangChatListener` NEW, `GG/gang/Gang` (state), `GG/gang/database/**` (state default, migration) | GR-38 | chat recipient filter (pure); stored `OPEN`/`CLOSE`/garbage rows load as `INVITE`; `gang join` refused on `INVITE`, accepted on `OPEN` and sets `gangJoinDateLong`; `GangCommandsJsonParityTest` | no existing gang becomes joinable without `gang recruiting open` |
| W2-Q Quartermaster | sonnet | TF-17 price + discount, TF-18 pages, TF-19 re-validation, TF-23 panel strings (append to `turf_messages.yml`), TF-38 name; `gangland.turf.upgrade` via `GangPermissions.allows` (TF-45 half); `Max_Reserve`; purchase log with buyer; `reinforced_defense` cut with a tolerant loader | `GT/npc/view/**`, `GT/npc/config/TurfPowerupOpenContractImpl`, `GT/command/TurfPowerupNpcCommand`, `GT/powerups/PowerupRegistryLoader`, `turf/turf_powerups.yml` | TF-02 (discount half), 17, 18, 19, 23 (panel half), 38, TF-45 | purchase re-validation with a recording fake; `PowerupRegistryTest` (old file with `reinforced_defense` loads and warns); paging test; reserve cap test | a recruit cannot spend the bank; an officer cannot exceed 12 guards |
| W2-E1 docs (parallel) | haiku | "How turf wars work" page from `turf_rules.yml` (the five sentences with live numbers; also printed by **NEW** `/glw turf rules`, a W2-R stub filled here); `_es` twins (empty) | docs | - | - | player docs match code |
| W2-E2 merges + migration (after) | haiku | `commands.json`; migration doc leads with the capture-window behaviour change and the small-server `OWNERS_OFFLINE` switch, the contribution reset, `INVITE` default, `Turf.Capture.*` -> `turf_rules.yml`, new YAML keys, faucet per active turf per day | docs, `commands.json` | - | - | - |

Merge order: W2-R -> W2-N -> W2-P1 -> W2-P2 -> W2-Q -> W2-E2. Opus concurrent peak: 2 (W2-R runs alone; the two gate reviewers
start after the merges, `parallel: false` in the roadmap). The orchestrator re-checks D1 before W2-R starts. Exit: the owner walks the
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
| W4-R roles (**first**) | **opus** | `NpcRole` record (**A2/D23:** if `gang-turf-territory` already carries the W5-K bump, `NpcRole` is added under that value; otherwise `VERSION` is bumped one minor from the merged master's value - one bump per release branch; documented in `documentation/gangland-api.md`); `CopRole` adapter (`copsncrooks/npc/police/config/CopRole.java`); civilians `AI.Combat.Roles` overlay with defaults; turf `Garrison.Composition`; shield guard in `civilians/listener/civilian/CivilianDamageListener`; commander-down; no Medic; per-module `Host_Api` table: turf, cops, civilians all set to the W4 value | api `npc/NpcRole.java` NEW, cops `CopRole.java`, civilians type-config + `CivilianDamageListener`, turf `GT/npc/defender/**` composition, three `module.yml` | - | `NpcRoleTest`; `CopRole` regression tests unchanged; civilians overlay default test | merges first |
| W4-C health bars | haiku verify + sonnet fix if needed | T7 layout with the callsign line | none expected | - | manual check | documented |
| W4-D bounty + stuck (after W4-R) | sonnet | `TurfDefenderMoneyDropSource`; stuck recycling after the cop version is accepted live | NEW `GT/npc/defender/TurfDefenderMoneyDropSource`, deployer tick | - | drop funding (no mint; ally kill = 0); recycle decision on a fake clock | killing guards pays the owner's spend back to attackers |
| W4-H heat exemption (optional, D10; after W4-R) | sonnet | `WantedKillTracker.exemptsKill` default + forward; impl consults it; cops implements via turf contest state | `gangland-core/.../wanted/*`, impl `EntityDamageListener`, cops `seam/KillComboWantedTracker` | TF-49 | `WantedKillTrackersTest`, `KillComboWantedTrackerTest` (defender inside exempt; attacker not; outside not; no contest not) | - |
| W4-E docs | haiku | final migration doc; api notes; changelog (.md + .bbcode.txt) | docs | - | - | - |

Merge order: W4-R -> W4-C -> W4-D -> W4-H -> W4-E, then `gang-turf-territory` -> master as 0.14.0 **after the later of the
W4 and W6 gates (A3, D23 A)**. Opus peak: 1.

### 7.9 Timeline

```
W0  K(orch) -> [B] sonnet -> [A] sonnet  ||  [C][D] sonnet  [E1] haiku  [F] sonnet+owner  -> [E2] -> gate (opus 1, orchestrator go/no-go, smoke G1/G2, owner 20 min)
W1  K(sonnet+haiku) -> [M][X][H][T] after W0-A/B  ||  [G][R] after W0-C  [E1] haiku  -> [E2] -> gate (opus 1, orchestrator go/no-go, owner script to 4:00, master dry-run merge)
W2  K(sonnet) -> [R OPUS] [N][P1][P2][Q] sonnet  [E1]  -> [E2] -> gate (opus 2 reviewers in sequence, orchestrator go/no-go, full script + live fight)
W3  [A OPUS] -> [B] sonnet   ||   [Z][V][W] sonnet (may start after the W1 gate)  [E1]  -> [E2] -> gate (opus 1, orchestrator go/no-go, live waves, master dry-run merge)
W5  (after the W2 gate, beside W3)  pre(orch: docket rows, D16/D22/D23 sign-off) -> K -> fan-out 1 [R][L][M][H][F][C] -> fan-out 2 [T][S][U] -> [A]  [E1] -> [E2] -> gate (opus W5-G two passes after W3-G, orchestrator go/no-go, owner 30 min); lane branches merge only after the W3 gate closes
      ... H13 (0.13.0) merges to master; Keystone master 1.14.0; merge master into gang-turf-territory ...
W4  [R OPUS] -> [C][D][H]  [E]                                -> gate
W6  (fights after W5-K; W after W5-F; X after the W5 gate; beside W4)  K -> [A][S][E][W] -> [F OPUS] -> [X audit]  [E1] -> [E2] -> gate = "Gangs+ parity reached" (owner 40 min, W6-J go) -> [Y flip]
      merge to master as 0.14.0 after the later of the W4 and W6 gates (D23 A)
```

### 7.10 W5 - Parity: gang core (after the W2 gate, beside W3; decisions to settle before the wave starts: D16, D22, D23 need the owner's sign-off before W5-K because the kickoff commits them; D17, D18, D21, D24 and D20's rule half may default per section 8; D20's WorldGuard half is signed off before W6-W)

Depends on the W2 gate: W1-K gang YAML plumbing, W1-G list/info/name rules, W1-R preset + node registration, W2-P1
`addExperience` + level curve, W2-P2 chat listener + `gang join`, W0-C `GangCommandsJsonParityTest`, W0-D
`MailModuleFileConfig`. Runs **beside W3** on disjoint files: W3 owns `GC/npc/CivilianService`, `GC/npc/FactionSquads`,
`CivilianCombatBehavior`, `civilian_messages*.yml`, `GT/npc/defender/**`, `GarrisonDeployListener`, `TurfPowerupNpc`,
`turf_npcs.yml`, the map files, `TurfVisualization` and `gangland-turf/pom.xml`; none appear below. Same branch, lane
worktrees `E:/Programming/java/wt/gt14-w5<x>-<slug>`. Vocabulary: section 15.1; rule text: 15.3-15.6.

**W5-pre** (orchestrator, before W5-K): the triage rows GR-39, GR-40, CM-38 and the adjacent rows US-43, LS-35, LS-36,
CJ-43, GR-41, GR-42, WB-43 (9.3) are **already written (DONE 2026-09-30)** through `triage/*.txt` + `build_docket.py`; the
Gangland docket now holds 622 entries and the cross docket 1074 (both HTML files rebuilt; republishing the two artifacts
and re-seeding the cross docket's status rows stay orchestrator steps, section 8 rule: no lane records a fix against a row
that does not exist). W5-pre only re-checks the ten ids in `bugs.json` and republishes; it also obtains the owner's D16, D22,
D23 answers. Two of the rows carry a caveat found when they were written: **US-43** is the residual of the existing **US-08**
(same `Level.addLevels` defect; US-08 already records the XP burn, US-43 adds the wrong returned count and the fee
consequence, so W5-L records both against one fix and flips the two pinned `LevelTest` cases), and **LS-35** is a sibling of
**LS-06** (static Player-keyed maps), not a replacement.

Kickoff **W5-K** (sonnet code, haiku YAML/JSON, on the integration branch): the section 15.7 api batch + the one `VERSION`
bump (D23), including the `WaypointTeleport` body fixes (LS-35, LS-36); the entity columns (`gang.notice`,
`gang.friendly_fire`, `gang_ally.friendly_fire`, `member.kills|deaths|assists`) with round-trip tests;
`GangCommand`/`GangAllyCommand`/`GangOptionContribution` stubs and the **one-shot constructor re-wiring** of every child over
the seam types (7.1); the per-lane `@Configuration` classes registered in `GangModule` with the `GangModuleTest` flip (7.1);
**NEW** `GangWorldPolicy` (full: tiny) with the `GangCommand` root gate (`Gang.Enable` becomes its global switch, **GR-40**);
**every seam type of the 15.7 seam-signature table** committed with the listed signatures and trivial bodies so lanes compile
in parallel and their red tests fail on an **assertion, never on a compile error**; `GangPermissions` constants for every new
verb; `gang_rules.yml` parity sections (`Levels` rows incl. `Cost`, `Ranks.Minimum`, `Ranks.Preset`, `Safehouse`, `Chat`,
`Friendly_Fire`, `Worlds`, `Combat`, `Stats`, `Commands`, `Placeholders`, `Purge`, `Name_Pattern`, `Display_Name`) laid out
one section per lane; **NEW** `gang/gang_messages.yml` + `GangMessages` (one section per lane); `player_profile.yml`/
`gang_ranking.yml` registered in `GameplayConfig`; `plugin.yml` nodes, the `gangland.command.player` and
`gangland.admin.gang.*` parents and the D22 label commands (each label with its own `permission:` line; registered only when
`GangMembership.isInstalled()` at COMMAND phase, so a server without the gang module squats no label); the **final**
`commands.json` entry for every verb (7.1); `GangCommandsJsonParityTest` extended to the `option gang` staff sub-tree;
`PluginYmlPermissionsTest` asserts that no `gangland.gang.*` node or parent defaults true (15.3 C16). Tests named in full:
api default-method tests, binary-compat test (an anonymous `GangMembershipView` compiled without the new methods still loads;
the interface has three abstract methods, so no lambda), column round-trip tests, **NEW** `GangWorldPolicyTest`, **NEW**
`WaypointTeleportCancelTest` (cancelled and world-missing paths leave no map entry; downed player refused), **NEW**
`LocalizedModuleYamlLanguageTest` (`<base>_<code>.yml` picked), impl **NEW** `GanglandPlaceholderGlobalTest` (a board token
with a null player and no `user_`/`bank_` substring reaches `resolveGlobal`), **NEW** `GangRulesReloadTest` (edit
`gang_rules.yml` on disk, `/glw reload`, the new `Levels` cost is visible: GP-38), **NEW** `GangRulesKeyParityTest`, **NEW**
`HostApiConsistencyTest` (every shipped `module.yml` `Host_Api` <= `GanglandApi.VERSION`; the modules that read new members
equal it), `GangModuleTest` flipped, `GangModuleConfigTest` extended, one trivial test per stub. Before fan-out the **orchestrator**
read of the seam-signature table against the K commit (does not count against the opus cap).

| Lane | Model | Scope | Owns | Docket | Tests (JUnit 5 + Mockito, red first) | Exit |
|---|---|---|---|---|---|---|
| W5-M membership lifecycle and purge (fan-out 1) | sonnet | **NEW** `GangMembershipService` (the one join/leave/kick/disband/set-leader path: create, invite accept, `gang join`, leave, kick, disband, purge; member cap via `GangLimits`, grow-only; combat-tag refusal on leave; `Commands.On_Join|On_Leave|On_Kick` with `%gang%` spliced only when `name` matches `[A-Za-z0-9_]+`, else `%gang_id%`; `%player%`; api `GangCreatedEvent`/`GangMemberJoinedEvent`/`GangMemberLeftEvent(reason)` fired exactly once); **NEW** `GangDisbandService` from `GangDeleteCommand` (payout and `GangDeleteEvent` unchanged); **NEW** `GangLeadership` from `GangTransferCommand` (with a staff flag that bypasses `RankAssignmentPolicy`'s Tail refusal, used only by W5-A's `leader`); the `allows(..., KICK)` gate in `GangKickCommand`; **NEW** `GangPurgeTask` (`BeanLifecycle`, daily, off by default, `Purge.Inactive_Days` 30 gangs / 60 non-owner members via the newest `OfflinePlayer.getLastPlayed` across **all** members including the owner; first run `Purge.First_Run_Delay_Hours` 24 after boot so a server restored from a backup is not purged on its first boot; disband through `GangDisbandService` with `Purge.Refund_Fee` false, fires `PURGED`; D24); **NEW** `MembershipQueries` delegate for `GangMembershipInstaller` | **NEW** `GG/gang/membership/**`, **NEW** `GG/gang/purge/**`, `GG/command/sub/gang/{GangCreateCommand,GangJoinCommand,GangLeaveCommand,GangKickCommand,GangDeleteCommand,GangTransferCommand}` (bodies only; constructors were wired by K), `GM/command/**/{GangInviteCommand,GangInviteAcceptCommand}`, **NEW** `GG/gang/membership/MembershipQueries`, **NEW** `GG/gang/GangMembershipConfig` | - | **NEW** `GangMembershipServiceTest` (every join and leave site fires exactly one event with the right reason; cap refuses the n+1th member at accept and at `gang join` against a **fake `GangLimits`** (W5-L's body merges later); over-cap gang keeps everyone; tagged player cannot leave against a **fake `CombatTag`**; console tokens use `name`, a name outside `[A-Za-z0-9_]+` splices `%gang_id%`, display name never spliced); `GangDeleteCommandTest` characterization green before and after; **NEW** `GangLeadershipTest` (incl. the staff bypass); **NEW** `GangPurgeTaskTest` (fake clock + last-played map: preview lists, run disbands, owner's last-played counts, first-run delay, off by default); invite-accept cap test; mail tests green | no code path adds or removes a member outside the service (grep: `addMember`/`removeMember` only there and in the repository load) |
| W5-L levels, upgrade and limits (fan-out 1) | sonnet | D16 C, D17 A: **NEW** `GangLevelTable` (pure rows `Levels.Level_<n>`: `Cost`, `Max_Members`, `Max_Safehouses`, `Max_Allies`, `Chat_Prefix`, `Commands`; missing rows inherit; 0 = unlimited) + `GangLimits` body; the claim gate in `GangManager.addExperience`: XP banked with core `Level.addExperience(xp, false, null)`; **core fix US-43**: `Level.addLevels` (`gangland-core/.../core/user/Level.java` L74-90) today subtracts the XP before the event and counts a cancelled level, so it burns XP and returns 1 on a cancel for every caller; W5-L makes it subtract and count only after a non-cancelled event (root cause, one file, no other lane touches it); **NEW** `GangUpgradeCommand` (`upgrade` = preview with XP and money still missing; refused at `Gang_Level_Max` with its own sentence; `upgrade confirm` checks the XP bar in gang code because `addLevels` levels even below it, then `addLevels(1, event)` and one fee withdrawal only when it returns 1); `Cost: 0` auto-claims exactly like W2-P1; `Levels.Level_<n>.Commands` only for members whose `gangJoinDateLong` is older than `Levels.Reward_Min_Member_Age_Hours` 24 (online and offline; an `OfflinePlayer` with a null name is skipped with a log line, never spliced as "null"); api `GangLevelReachedEvent`; `Max_Allies` at mail request and accept | **NEW** `GG/gang/level/**`, `GG/gang/GangManager` (level methods), `gangland-core/.../core/user/Level.java`, **NEW** `GG/command/sub/gang/GangUpgradeCommand`, `GM/command/**/{GangAllyRequestCommand,GangAllyAcceptCommand}`, **NEW** `GG/gang/GangLevelConfig` | US-43 | **NEW** `GangLevelTableTest` (rows, inheritance, 0 = unlimited, turf-cap column equal to `TurfRules.cap` = `min(6, 2 + level)`); core `LevelTest` flipped red first (cancelled event: level and XP unchanged, returns 0); **NEW** `GangUpgradeCommandTest` (XP short -> refused with both amounts; money short; both met -> one level, fee withdrawn once; `addLevels` never called below the XP bar; cost 0 auto-levels like W2-P1; money never adds XP; a cancelled `GangLevelUpEvent` leaves level, XP and bank unchanged; max level refused); reward runner with an offline member and with a fresh joiner (no reward); **NEW** `AllyCapTest` (request and accept both refuse) | XP thresholds unchanged (the W2-P1 days-to-level test stays green); the claim step is added after them and is a config knob |
| W5-R minimum ranks and preset (fan-out 1) | sonnet | `Ranks.Minimum` clause in `GangPermissions.allows`: `rank.getNode() == min.getNode() || tree.isDescendant(min.getNode(), rank.getNode())` (Keystone `Tree.isDescendant` returns **false** for the same node, `Tree.java` L76-77, so the named rank itself needs the equality clause; argument order pinned by a test); unknown rank name warns once and disables the clause; `Ranks.Preset` read by the W1-R seed on an empty table only (D13 A; default `member, officer, owner`; five-rung example in the YAML comment; Head/Tail mismatch warns and uses the settings names for the ends); the lowest-rank denial message; gates on kick/promote/demote beside their position checks; shipped minimums in 15.4; **no node inheritance** | `GG/gang/permission/**`, `GG/gang/rank/RankSeed*`, `GG/command/sub/gang/{GangPromoteCommand,GangDemoteCommand}` (gate line) | - | `GangPermissionsTest` table (rank **exactly at** the minimum passes; below and above on a 3- and a 5-rung tree; branching tree; unknown rank warns and disables; server permission and top rank still pass); `RankSeedTest` (preset list, empty table only, mismatch warning) | an officer invites and kicks; a member cannot withdraw on a fresh install |
| W5-C chat (fan-out 1) | sonnet | `gang chat allies [msg]` (toggle + one-shot) in the P4.5 `GangChatListener`, recipients from main-thread snapshots refreshed on join/leave/ally change; **the thread hop stated:** the listener cancels the `AsyncPlayerChatEvent`, schedules delivery on the main thread and fires the synchronous api `GangChatEvent` there (one tick of latency; Bukkit throws on a sync event called from the async chat thread); **NEW** `ChatSpyService` (in memory) behind `/glw option gang spy` (`gangland.admin.gang.spy`); `Chat.Log_To_Console`; `Chat.Public_Tag.Enabled` (default false) replaces `{glw_gang}` in `AsyncPlayerChatEvent.getFormat()` at LOW (documented: EssentialsChat-style plugins rewrite the format at LOWEST/NORMAL, so LOW runs after them; the W5 walkthrough includes one chat-format plugin), `Chat.No_Gang_Tag`; every spliced value (display name, prefix) has `%` escaped as `%%` because `setFormat` is a `String.format` pattern; level prefix from `GangLimits` in both channels; **NEW** `ChatChannels` delegate (`chatChannelOf`) for `GangMembershipInstaller` | `GG/listener/gang/GangChatListener`, `GG/command/sub/gang/GangChatCommand`, **NEW** `GG/gang/chat/**`, **NEW** `GG/gang/GangChatConfig` | - | **NEW** `GangChatRoutingTest` (pure recipient sets: gang, allies, spy, gangless; a cancelled event delivers nothing); snapshot-refresh test; format-token test incl. a `%` in the display name; fake-scheduler test proving no manager read off the main thread and that `GangChatEvent` fires on the main thread | chat never reads `HashMap` managers on the async thread |
| W5-F friendly fire and combat tag (fan-out 1; W5-G's first pass starts here) | sonnet (opus gate review) | D20 A, section 15.5 in full: **NEW** pure `GG/gang/combat/FriendlyFireRule`; `GangMembersDamageListener` rewritten onto it (self damage allowed by default, **GR-39**; GR-15 guard kept) with the attacker resolved from `Player`, `Projectile` shooter, `Tameable` owner and `TNTPrimed.getSource()`, plus `EntityCombustByEntityEvent` so Flame/Fire Aspect fire between gangmates follows the rule (**GR-41**: not cancelled today); **NEW** `PotionFriendlyFireListener` (`Friendly_Fire.Potions` `NEGATIVE_ONLY` over XSeries `XPotion.DEBUFFS` (exists in the pinned 13.6.0), or `ALL`; note `PotionSplashEvent.setIntensity(victim, 0)` strips every effect of a mixed potion for that victim, documented); **NEW** `DamageLedger` at **HIGH, `ignoreCancelled = true`, `getFinalDamage() > 0`** (after the LOWEST friendly-fire cancel, before impl `CustomPlayerDeathListener`'s HIGHEST lethal intercept L91-113, which cancels the killing blow when `Respawn.Enable` is on; a MONITOR ledger would never see a downed player's lethal hit); **NEW** `CombatTag` (`Combat.Tag_Seconds` 15; tags only when **both** players are gang members, so a gangless alt cannot tag-lock) + **NEW** `CombatTeleportGuard` on api `TeleportEvent` for every `WaypointType.GANG` teleport (safehouses, rally, admin gang waypoints) and the leave refusal; **NEW** cops `copsncrooks/listener/police/RestrainedTeleportListener` on api `TeleportEvent` cancelling for `detainmentService.isRestrained(player)` (**CJ-43**: a warm-up started before an arrest teleports the cuffed or jailed player out when it ends; cops has no `TeleportEvent` listener today; the one named extra file, cops otherwise untouched); `gang pvp on|off`, `gang ally pvp <gang> on|off` (per-direction rows, effective only when both are on); **NEW** `FriendlyFirePolicy` delegate (`pvpBlocked`) for `GangMembershipInstaller`; civilians `GangAllyWeaponImpactListener` switched from `Settings.isGangEnabled()` + `alliedOrSame` (L42-47) to `membership.pvpBlocked(...)`, still gang-module-free; cops `TurfFriendlyFireListener` untouched; the no-op `FriendlyFireRegionSource` bean in `GangCombatConfig` (W6-W later adds the plugin-present check and factory call) | `GG/listener/gang/GangMembersDamageListener`, **NEW** `GG/gang/combat/**` (rule, tag, policy), **NEW** `GG/listener/gang/combat/{DamageLedger,PotionFriendlyFireListener,CombatTeleportGuard}`, **NEW** `GangPvpCommand`, **NEW** `ally/GangAllyPvpCommand`, **NEW** `GG/gang/GangCombatConfig`, `GC/listener/gang/GangAllyWeaponImpactListener` (body), **NEW** cops `listener/police/RestrainedTeleportListener` | GR-39, GR-41, CJ-43 | **NEW** `FriendlyFireRuleTest` (every row of the 7-step table; wolf, TNT and combust rows); flip or add the `GangMembersDamageListenerTest` self-shot case (red: cancelled today); potion strip test (negative only vs all, healing reaches friends); `GangAllyWeaponImpactListenerTest` (fact honoured; Bartizan-absent class skipped); **NEW** `DamageLedgerTest` (a cancelled hit never tags or assists; a 0-damage snowball never tags; **respawn system on: a one-shot kill still records the shooter**); **NEW** `CombatTagTest` (expiry on a fake clock; blocks GANG-type waypoint teleports and `gang leave` against a **fake leave path**; leaves SPAWN/GLOBAL waypoints alone; gangless attacker never tags); **NEW** `RestrainedTeleportListenerTest` | one class decides player-vs-player friendly fire; grep finds `alliedOrSame` in no damage listener; Bartizan guns obey `gang pvp on`; guards stay immune to owners and allies |
| W5-H safehouses and rally (fan-out 1) | sonnet | D18 A: **NEW** `gang_safehouse` table + repository (`gang_id`, `name` unique per gang, world, x, y, z, yaw, pitch; `setDataSupplier`; rows deleted in the `GangDeleteEvent` path); **NEW** `GangSafehouseService` building a transient api `Waypoint` (type GANG, `shield` 0, timer/cooldown/cost from `Safehouse.*`) for `WaypointTeleport`; **NEW** `SafeSpot` (2-high passable, solid floor, no lava/fire, `XMaterial`; `Safehouse.Safe_Check`; ponytail note naming Keystone as the promotion target); `safehouse set|remove|list|<name>` (nodes `gangland.gang.safehouse` / `.safehouse.manage`); `safehouse set` and every GANG-type teleport refused in a gang-disabled world (`GangWorldPolicy`); cap from `GangLimits`; `Safehouse.Enabled`; the K-stubbed cancellable `GangSafehouseSetEvent` fired; `rally <safehouse>` and `rally` (caller position, snapshot at send) with a 60 s consent line to online members and `rally join` (each join is a GANG-type teleport, so the turf veto of W5-T applies per arrival) | **NEW** `GG/gang/safehouse/**`, **NEW** `GG/gang/database/**/safehouse/**`, **NEW** `GangSafehouseCommand`, **NEW** `GangRallyCommand`, **NEW** `GG/gang/GangSafehouseConfig` | - | **NEW** `GangSafehouseRepositoryTest` (`@TempDir(cleanup = NEVER)` + release; per-gang uniqueness; two gangs both own `base`; delete on disband); **NEW** `SafeSpotTest` (lava, void, 1-high gap, water); **NEW** `GangSafehouseCommandTest` (cap by level, rank gate, `shield` 0 on the built `Waypoint`, cancelled `GangSafehouseSetEvent` -> refused); **NEW** `GangRallyCommandTest` (consent expiry, online members only, caller snapshot) | two gangs both set `base`; a safehouse teleport grants no invulnerability |
| W5-T turf touchpoints (fan-out 2) | sonnet | **NEW** `GT/listener/TurfTeleportGuardListener` on api `TeleportEvent`, one rule, four cases, reads turf state only (no W2-R file): cancels a `WaypointType.GANG` teleport whose source is inside a turf under attack; whose destination is inside **or within `Teleport_Guard_Buffer_Blocks` (turf module YAML, 48) of** a turf under attack (a caller one block outside an attacked turf must not deliver the whole gang by `rally`); whose destination is inside a turf owned by a gang neither the player's nor allied; or whose player belongs to a gang that is **owner or challenger in any running contest** ("your gang is in a turf fight": closes both directions with one sentence; admin gang waypoints included, no marker); **NEW** `GT/listener/TurfSafehouseSetListener` cancels `GangSafehouseSetEvent` inside a rival or contested turf; `TurfCreateCommand` refuses a gang-disabled world, an existing turf there logs one warning at load; **NEW** `GT/stat/TurfsHeldStatContribution`; `TurfModuleConfig` append; its strings in a `Teleport_Guard` section of `turf_messages.yml`. No membership re-stamp listener: A4 freezes sides in W2-R | **NEW** `GT/listener/{TurfTeleportGuardListener,TurfSafehouseSetListener}`, `GT/command/TurfCreateCommand` (world check), **NEW** `GT/stat/**`, `TurfModuleConfig` (append), `turf_messages.yml` (own section) | - | **NEW** `TurfTeleportGuardListenerTest` (source under attack, destination under attack, destination one block outside an attacked turf, destination in a rival turf, member of a contesting gang anywhere -> cancelled; own quiet turf allowed; non-GANG waypoint untouched); **NEW** `TurfSafehouseSetListenerTest`; `TurfCreateCommandTest` world case; **NEW** `TurfsHeldStatContributionTest` | a rally into or next to a turf under attack is refused with one sentence |
| W5-S stats, ranking, profiles, placeholders (fan-out 2) | sonnet + haiku YAML | **NEW** `GangStatsListener` (`PlayerDeathEvent` + `PlayerDownedEvent` at MONITOR, 500 ms de-dup like `PlayerDeathListener`; killer + assists from the HIGH `DamageLedger`; a kill counts only when the victim is in a **non-allied gang** with `gangJoinDateLong` older than `Stats.Min_Victim_Member_Age_Hours` 24 (a gangless alt is never a valid victim), never for same-gang/allied pairs, repeat pairs inside `Stats.Repeat_Kill_Cooldown_Seconds` 300, more than `Stats.Max_Kills_Per_Victim_Per_Day` 3 per killer-victim pair, or in `Worlds.Disabled`) -> `member.kills|deaths|assists`; **NEW** `GangStatsService` (kills, deaths, KDR, assists, members' money from online then offline `UserManager`; one immutable main-thread snapshot every 60 s shared by command, menu and placeholders); `GangStatContribution` consumers; `gang ranking [stat] [page]` (kdr with `Ranking.Min_Kills`, wlr, level, members, online, bank and members' money behind `gangland.gang.ranking.wealth`, turfs); `gang profile [player]` chat card + GUI; `option gang stats reset` body; `GangPlaceholderContribution` tokens (`gang_friendly-fire`, `gang_leader`, `gang_member-list`, `gang_online-list`, `gang_kills`, `gang_deaths`, `gang_kdr`, `gang_assists`, `gang_members-money`, `gang_safehouses` (reads the K-stubbed `GangSafehouseService.count(gangId)`), `gang_notice`, `gang_member-cap`, `gang_ally-cap`, `gang_upgrade-cost`, `gang_upgrade-xp`, `user_rank-position`, `user_assists`) + `gang_board_<stat>_<n>_<name|value|level>` via api `resolveGlobal` (board tokens never contain `user_` or `bank_`: core substring dispatch runs first) + `Placeholders.Default_Value`; `gang_ranking` item source; **NEW** `inventory/player_profile.yml`, `gang_stat.yml` aggregates, **NEW** `gang_ranking.yml` (impl resources by the existing `gang_stat.yml`/`gang_info.yml` precedent; recorded: these menus render a dead item source when the gang module is absent); `GangFilterAdapter` sort fields | **NEW** `GG/gang/stats/**`, **NEW** `GG/listener/gang/stats/GangStatsListener`, **NEW** `GangRankingCommand`, **NEW** `GangProfileCommand`, `GangPlaceholderContribution`, `GangMenuItemSourceContribution`, `GG/gang/GangFilterAdapter`, **NEW** `GG/gang/GangStatsConfig`, `gangland-impl/src/main/resources/inventory/{gang_stat,player_profile,gang_ranking}.yml` | - | **NEW** `GangStatsListenerTest` (same gang and ally never count; gangless victim never counts; victim younger than 24 h never counts; fourth kill of the same victim in a day not counted; downed + death de-dup; **respawn system on: a one-shot kill credits the shooter**; assist window; disabled world); **NEW** `GangStatsServiceTest` (each sort, ties by id, min-kills floor, wealth hidden without the node, immutable snapshot, fights/turf contribution fakes); **NEW** `GangRankingCommandTest` (paging, unknown stat lists the valid ones); extend `GangPlaceholderContributionTest` (every token, gangless default, `gang_board_kdr_1_name` via `resolveGlobal` with a null player, no `user_`/`bank_` substring) | the same ranking appears in chat, the menu and a hologram placeholder |
| W5-U UX (fan-out 2) | sonnet | D21, D22: **NEW** `IMPL/command/GangShortLabelExecutor` forwarding `/gw` -> `glw gang`, `/gwc` -> `glw gang chat`, `/gwa` -> `glw gang chat allies` into `CommandManager.onCommand` with tab completion delegated (the only label set; remappable per server through `commands.yml`, C18); the GP-04 help filter in `GangCommand.help`: a transient `HelpInfo` holding only the entries whose child `Argument` permission the sender has (api `HelpInfo.displayHelp` L75-100 prints its whole list; Keystone's `CommandVisibilityFilter` covers the top-level `/glw` listing only; the child is found by the usage's third token), no api change; `gang info <gang|player>`; bank column/card line only with `gangland.gang.balance.others`; `GangMembersCommand` grouped by rank + `[page]`; `Name_Pattern` (load check refuses whitespace, `@`, `/`, `;`; a widened pattern can otherwise reach vanilla selectors through the `Commands.*` splice) + `Display_Name.*` in `GangNameRules`/`GangDisplayCommand`, uniqueness on the colour-stripped, NFKC-normalised, lower-cased display name (no `&cRats` beside `Rats`, no homoglyph twins) and an optional `Display_Name.Blocked_Words` list; `gang notice <text>|clear` + login line in `MemberJoinListener`; **NEW** `GangNotifier` + `gang_messages.yml` `Titles`; `gang ally abandon <gang name>` with completion | **NEW** `IMPL/command/GangShortLabelExecutor`, `IMPL/config/WiringConfig` (bean), `GG/command/sub/gang/GangCommand` (`help` body only), `GG/command/sub/gang/{GangInfoCommand,GangListCommand,GangMembersCommand,GangNameRules,GangDisplayCommand}`, **NEW** `GangNoticeCommand`, `GG/listener/gang/MemberJoinListener`, **NEW** `GG/gang/notify/**`, **NEW** `GG/gang/GangUxConfig`, `GG/command/sub/gang/ally/GangAllyAbandonCommand` (gang module, not mail) | - | **NEW** `GangShortLabelExecutorTest` (prefixing, empty args, tab delegation, namespaced fallback); **NEW** `GangHelpFilterTest` (an entry whose node the sender lacks is not listed); **NEW** `GangInfoCommandTest` (player-name resolution, bank hidden); name-rule table (Cyrillic display, hex allowed/denied, whitespace/`@` pattern rejected at load, colour-stripped duplicate refused, blocked word refused); notice shown once per login to members only; `GangAllyAbandonCommandTest` by name | `/gw` works for a non-op; a public card never shows another gang's bank; help lists only what the sender may run |
| W5-A staff verbs (**not parallel**: branches from the head after W5-M, W5-H, W5-C and W5-S merged, like W0-A from W0-B) | sonnet | `/glw option gang disband <gang>` (+confirm), `leader <gang> <player>` (through W5-M's `GangLeadership` staff flag), `bank <gang> balance|give|take|reset [amount]` (every write logged with actor, gang, amount), `safehouse <gang> list|tp|remove`, `stats reset <player>`, `spy`, `purge preview|run`, over the merged W5-M/L/H/S/C services; **console-safe**: new verbs never cast the sender, and the four existing `option gang rank` casts (`GangOptionContribution.java` L68/L80/L115/L183) are fixed while the lane owns the file (**GR-42**) | `GG/gang/command/option/GangOptionContribution`, **NEW** `GG/gang/command/option/**` | GR-42 | **NEW** `GangStaffVerbsTest` (each verb from a console sender, the `rank` verbs included; bank writes logged with actor; disband fires `GangDeleteEvent`; leader assigns the Tail; purge preview then run); `GangCommandsJsonParityTest` covers `option gang` | every staff verb runs from the console; merges last in W5 |
| W5-E1 docs (parallel) | haiku | `documentation/features/gangs.md` rewrite (levels and the paid claim, limits, safehouses and rally, chat, the friendly-fire rule, ranking and profiles, staff verbs, worlds, labels); `ranks.md` corrected to the real model (drops the invented inheritance and API; documents `Ranks.Minimum`/`Ranks.Preset`); `documentation/gangland-api.md` gains the W5-K batch | docs | - | - | docs match `gang_rules.yml` |
| W5-E2 merges + migration (after) | haiku | `commands.json` description proofread (the entries were final at K); migration section (15.9) in its own window | docs, `commands.json` | - | - | help lists every new leaf to an op; a non-op sees only their verbs |
| W5-G gate review | opus | adversarial, **two sequential passes over per-lane diffs**: pass 1 = W5-F first, then W5-H, W5-T, W5-C (friendly-fire bypasses: projectile owner swap, wolves, TNT, potion clouds, Bartizan impacts, fire ticks; teleport escape and reinforcement paths: safehouse, rally, admin gang waypoints, combat tag, the turf buffer, arrest; async chat reads); pass 2 = W5-M, W5-L, W5-R, W5-S, W5-U, W5-A, W5-K (the cap race at concurrent accepts, level-reward hopping, KDR farming, api additivity: a 2.0-built module jar still loads; staff verbs from the console). Never overlaps W3-G | gang, mail, turf, civilians, cops (one listener), api, core (`Level`) | - | - | no open exploit path without a red test |
| W5-J gate judge | orchestrator (opus main session) | go/no-go after the owner walkthrough | - | - | - | go |

Merge order: W5-K -> W5-R -> W5-L -> W5-M -> W5-F -> W5-H -> W5-T -> W5-C -> W5-S -> W5-U -> W5-A -> W5-E2 (L before M so
`GangLimits` has a body when W5-M's cap tests merge; a lane whose test needs a later lane's body tests against a fake of the K
seam type, as the rows say). **Staging:** W5-K merges right after the W2 gate (its additive diff is therefore in the W3 gate
build; W3-G reviews W3 lane diffs only, and a W3 gate failure traced to a parity diff is routed to W5-G); fan-out 1 = R, L,
M, H, F, C (independent through the K seams), fan-out 2 = T, S, U (they also need only K, but the Maven slot is the critical
path - `mvn -q test -pl <modules> -am` recompiles gang, api, core and impl every time - so at most **6 lane agents** run at
once and fan-out 2 starts as fan-out 1 lanes finish), A after M, H, C, S merged; **W5 lane
branches merge into `gang-turf-territory` only after the W3 gate closes** (they wait as branches). At every lane merge the
orchestrator checks `git diff --name-only` against the lane's Owns list plus the 7.1 rows. Opus in W5: the gate reviewer
only. W5 gate: `mvn clean package`; W5-G (two passes); W5-J; smoke G1/G2 re-run under the test-server lock; the owner with two
clients (about 30 min) walks: fresh gang, `/gw`, invite to the cap, `upgrade` refused while short with both amounts shown,
friendly fire toggled both ways with fists, a bow and a Bartizan gun, ally friendly fire needing both sides, safehouse
set/teleport, a rally refused into and next to a turf under attack, gang and ally chat with spy and one chat-format plugin
installed, `ranking`, the profile menu; docket rows GR-39/GR-40/GR-41/GR-42/US-43/LS-35/LS-36/CJ-43; `graphify update .
--force`.

### 7.11 W6 - Parity: fights, WorldGuard, the parity gate (beside W4; decisions to settle before the wave starts: D19 needs the owner's sign-off before W6-K, D20 before W6-W; D23 was settled before W5-K)

Fights lanes depend on **W5-K merged** (`GangStatContribution`, api `GangFightEndedEvent`, the `Fight` key in
`Ranks.Minimum`); W6-W on **W5-F merged** (`FriendlyFireRegionSource`, `GangCombatConfig`) for its merge only (it may start
beside A/S/E on disjoint files); W6-X on the **W5 gate**. W6 runs beside W4, which owns `API/npc/NpcRole`, cops `CopRole`,
civilians type config + `CivilianDamageListener`, `GT/npc/defender/**`, core wanted and impl `EntityDamageListener`; W6
touches none of them. Design: `parity/fights.md` (DF2-DF8 adopted as lane defaults; its `ModuleInstalls` line reference
L24-31 is stale, the map is at L30-35).

Kickoff **W6-K** (sonnet + haiku YAML): **NEW** `gangland-features/gangland-gang-fights` (module id `fights`, `Depends: [gang]`,
no `Plugins:`, `Host_Api` = the D23 value, pom shaped like `gangland-mail`: `gangland-api` + `gangland-gang` at `provided`);
`FightsModule`/`FightsModuleConfig`/`FightsModuleFiles`/`FightsModuleFileConfig` + **NEW** `FightsModuleConfigTest`;
`fight/fight_rules.yml`, `fight/fight_messages.yml` (one section per lane, **NEW** `FightRulesKeyParityTest`, **NEW**
`FightRulesReloadTest` for GP-38), empty `fight/arenas.yml`; `FightContribution` on path `gang` with stubs `propose`, `accept`,
`decline`, `enlist`, `drop`, `info`, `arenas`, `stats`; `ArenaCommand` root stub; **the W6 seam stubs of the 15.7 table**
(`ArenaRegistry`, `FightSettlement`, `FightClock`, `CombatTag` fighting flags) with trivial bodies; the module's own
`commands.json` with the final entry per verb + **NEW** `FightCommandsJsonParityTest`; orchestrator lines:
`gangland-features/pom.xml`, `gangland-build/pom.xml` jar copy, `ModuleInstalls.OFFICIAL` switched to `Map.ofEntries` (nine
pairs after this wave; `Map.of` stops at ten) and gaining `fights` plus the missing `gang` and `lootchest` ids (**CM-38**) +
`ModuleInstallsTest`, `plugin.yml` player fight nodes (default true) and `gangland.command.arena.*` (op).

| Lane | Model | Scope | Owns | Docket | Tests | Exit |
|---|---|---|---|---|---|---|
| W6-A arenas | sonnet | fights.md section 3: `Arena`, `ArenaBox` (3D, inclusive edges), `ArenaRegistry` body, `ArenaFile` (atomic write, hot reload), `ArenaDraft`; `/glw arena create|corner|spawn <side>|exit|size|finish|show|list|tp|on|off|delete|reload` (`finish`, never `save`); save validation (spawn outside the box, missing Exit); "place arenas outside turfs" is a documented admin rule that `arena finish` prints (DF8 A: fights is `Depends: [gang]` and `gangland-api` has no turf type, so the check cannot be coded without dragging in turf, civilians and Bartizan); `XParticle` outline | fights `arena/**`, `command/arena/**` | - | **NEW** `ArenaBoxTest` (inclusive edges, negatives, Y); **NEW** `ArenaFileTest` (round trip, malformed hand edit, atomic write); **NEW** `ArenaSaveValidationTest` (every refusal sentence, the turf reminder line) | an arena built in game without F3 coordinates survives `arena reload` |
| W6-S state machine | sonnet | fights.md section 4: `Fight`, `FightState`, `FightManager`, `FightProposal`, `FightSide`, `Participant`, `PlayerSnapshot`, `FightRules` (pure: allied refused, same gang, size, bet caps, rank gate via `GangPermissions.allows` + the `Fight` minimum **and, for any bet above 0, the `Withdraw` minimum on both `propose` and `accept`** (a `Fight` officer must not be a de facto `Withdraw`), wanted, downed, member age, ranked decision, a `CombatTag`-tagged player refused at `enlist` (GP-33's fights half) **and re-checked at COUNTDOWN**: a participant tagged during the lobby is dropped, and a side that falls short cancels the fight with a refund), `FightClock` body; verb bodies `propose`, `accept`, `decline`, `enlist`, `drop`, `info`, `arenas`; expiry and lobby; sets `CombatTag` fighting flags | fights `fight/**` (minus settlement), `command/fight/**` | - | **NEW** `FightRulesTest` (the challenge matrix; bet needs `Withdraw` on both sides; tagged player refused at enlist; tag at COUNTDOWN drops the participant and refunds a short side); **NEW** `FightStateMachineTest` with a fake `FightClock` (every transition; every cancel refunds) | - |
| W6-E economy and stats | sonnet | fights.md sections 6 and 8: escrow at accept with the second-withdraw rollback, `FightSettlement` body (`House_Cut_Percent` 0, `Maximum_Balance` overflow back to the loser), `abortAll` on disable/reload refunds; ranked vs sparring (`Ranked_Min_Bet`, `Ranked_Min_Member_Age_Hours` 24, `Ranked_Min_Opponent_Gang_Age_Days` 7, `Require_Equal_Teams` on, `Rematch_Cooldown_Minutes` 30, `Ranked_Per_Pair_Per_Day` 3, `Max_Bet_Percent_Of_Bank` 50 **checked against both banks at accept**, `Max_Net_Transfer_Per_Pair_Per_Day` (fights.md 7, restored) and `Max_Bet_Transfer_Per_Gang_Per_Day` across all opponents, so a rogue officer cannot drain a bank through alt gangs); `fight_stats`, `fight_player_stats`, `fight_record`; `FightPlaceholderContribution` (`gang_fight-wins|losses|wlr`); **NEW** `FightWinsStatContribution` (the WLR board counts at most `Ranked_Board_Wins_Per_Opponent_Per_Day` 1 win per opponent per day, so alt gangs cannot farm the ranking); api `GangFightEndedEvent`; `fight stats` body | fights `database/**`, `placeholder/**`, `fight/FightSettlement`, **NEW** `stat/**` | - | **NEW** `FightSettlementTest` with real `EconomyHandler` instances (win, draw, cap overflow, second withdraw throws -> first refunded); **NEW** `FightRulesTest` rows for the bet caps (both banks, pair cap, gang cap, opponent age); **NEW** `FightStatsRepositoryTest`; **NEW** `FightStatContributionTest` (wlr in `gang ranking`; the per-opponent daily cap) | fights are zero-sum bank to bank; a draw refunds both banks |
| W6-F fight guards | **opus** | fights.md sections 5 and 7: `FightDamageListener` (NORMAL lethal intercept before impl `EntityDamageListener` HIGH and `CustomPlayerDeathListener` HIGHEST; countdown/result immunity; **a test proves the intercept cancels every lethal fight hit incl. Bartizan impacts, void and fall damage**, because the `PlayerDeathEvent` fallback would still drop money through `MoneyDropListener` (`gangland-item`, MONITOR, rewritten on the unmerged `h13-drop` branch: named for W6-G, not touched here)), `FightContainmentListener` (move, teleport with an expect set, portal, 5-tick check, `Max_Leave_Warnings` 3, `Protect_Blocks`; **during LOBBY..RESULT non-participants are refused entry to the box by move or teleport and ejected to the Exit by the 5-tick check; block place and break in a busy arena are refused for everyone**), `FightCommandListener` (HIGHEST, lower-case, namespace stripped, aliases resolved, allowlist, bypass node `gangland.gang.fight.bypass_commands`), `FightSessionListener` (quit restores and forfeits, join ejects to the Exit, `PlayerDeathEvent` keep-inventory fallback, respawn at the Exit), `FightWantedGuard`, `FightGangDeleteListener` (forfeit pays the survivor the escrow), **NEW** `FightArenaSafehouseListener` (cancels `GangSafehouseSetEvent` inside any `ArenaBox`; legal: fights depends on gang); starts after W6-S and W6-E merge; never while W4-R and a gate reviewer both run | fights `listener/**` | - | **NEW** `FightDamageListenerTest` (incl. the every-lethal-source case), `CommandFilterTest` (`/MINECRAFT:TP`, aliases, allowlist, bypass), `FightWantedGuardTest`, `FightSessionListenerTest` (incl. outsider refused, outsider ejected, block place refused), `FightGangDeleteListenerTest`, `FightArenaSafehouseListenerTest`; **manual two-client check** on the Spigot test server: lethal hit by hand and by a Bartizan weapon -> wanted 0, no drop, no downed state | a fight death raises no wanted level, drops nothing and never enters the downed state |
| W6-W WorldGuard flag (may start beside A/S/E; merges after W5-F and W6-F) | sonnet | D20 A: `softdepend: WorldGuard` (orchestrator line); `worldguard-bukkit` 7.0.x **and its transitive `worldedit-bukkit`** (`BukkitAdapter` is needed for the query) at `provided` for the 1.16.5 floor in `gangland-impl/pom.xml` and `gangland-gang/pom.xml` (W6-W is the named pom owner for both), the root `pom.xml` enginehub repository as an orchestrator line, reflection-facade fallback if Maven cannot fetch it (same rule as dynmap); **NEW** `IMPL/hook/worldguard/WorldGuardFlagRegistrar` created from `Gangland.onLoad` (`IMPL/Gangland.java` L52) by a type-free factory only when `Bukkit.getPluginManager().getPlugin("WorldGuard") != null` (flags register before the module loader; works because `softdepend` loads WorldGuard first; `FlagConflictException` caught and logged once); the `StateFlag` is `glw-gang-pvp`; **the gang side mirrors the impl registrar, not `ReflectionGuard`**: Keystone `BeanFactory` L259 wraps `getDeclaredMethods` in `ReflectionGuard.orSkip`, so a `@Bean` whose signature names a WorldGuard type would drop the **whole** `GangCombatConfig` on a WorldGuard-less server with a `reflection.type.missing` fault every boot, and a bare `new` throws `NoClassDefFoundError` (KS-MO-07); therefore the `FriendlyFireRegionSource` bean in `GangCombatConfig` (type-free, W5-F's no-op today) gains the plugin-present check and calls **NEW** `GG/gang/combat/region/WorldGuardRegions.create()`, a static factory in the only gang class that names WorldGuard; a WorldGuard flag exists only after a full restart with WorldGuard loaded first (migration note) | impl pom, root pom (orchestrator), `IMPL/Gangland.java` (one line), **NEW** `IMPL/hook/worldguard/**`, gang pom, **NEW** `GG/gang/combat/region/**`, `GG/gang/GangCombatConfig` (one bean body, after W5-F merged) | - | **NEW** `WorldGuardFlagRegistrarTest` (absent plugin -> no call, no `NoClassDefFoundError`); **NEW** `GangCombatConfigTest` (`GangConfig` and `GangCombatConfig` load with WorldGuard absent, zero `reflection.type.missing` faults, no-op source installed); ALLOW/DENY/unset through `FriendlyFireRuleTest` with a fake source; smoke G1 without WorldGuard | no `reflection.type.missing` fault without WorldGuard |
| W6-X parity audit (before the gate) | sonnet (+ haiku writing) | walk GP-01..GP-45 on the merged branch: each row names its test class or owner step, runs the automated ones and records the result; writes `parity/audit.md`; **flips nothing** (the owner steps for GP-18/29/30/31/41 happen at the gate); rereads every verb, node, key and token against `competitors/gangsplus.md` and, through the owner's connected browser, the competitor's public commands and placeholders pages (tokens compared, nothing copied); closes GP-04 and GP-38 by naming `GangHelpFilterTest`, `GangRulesReloadTest`, `FightRulesReloadTest` | brainstorming only | - | - | every row names a test or an owner step; no token collides with the competitor's |
| W6-Y parity flip (after the owner session and W6-J's go) | haiku | flips `status_after_plan` to HAVE row by row in `parity/PARITY.md` (GP-33 keeps its recorded partial exclusion) with the audit row that proves it; `python build_roadmap.py` (rebuilds `roadmap.html` only: the script embeds PARITY.md's JSON block; `roadmap.json` carries no `parity` key and is not rebuilt) | brainstorming only | - | - | 45 rows HAVE, GP-33 with its recorded partial exclusion |
| W6-E1 docs (parallel) | haiku | **NEW** `documentation/features/fights.md`, arena how-to, WorldGuard flag note | docs | - | - | - |
| W6-E2 merges + migration (after) | haiku | `commands.json`; migration section (fight tables, arena data file, fights jar optional, WorldGuard optional, `/glw module install gang|lootchest|fights`) | docs, `commands.json` | - | - | - |
| W6-G gate review | opus | fight event ordering, escrow, containment bypasses (pearl, chorus, elytra, riptide, vehicles, outsiders), command-filter bypasses, the `MoneyDropListener` fallback (is the lethal intercept provably total?), WorldGuard load order, module boundaries (fights imports gang and api only); checklist line: "a fight inside a turf footprint counts fighters on the capture bar - documented rule, no code (DF8 A)" | fights, impl, gang | - | - | - |
| W6-J gate judge | orchestrator (opus main session) | the **"Gangs+ parity reached"** go/no-go and the 0.14.0 merge timing (D23) | - | - | - | go |

Merge order: W6-K -> (W6-A, W6-S, W6-E in parallel; W6-W beside them) -> W6-F -> W6-W -> W6-X -> W6-E2 -> gate -> W6-Y. **The W6
gate = "Gangs+ parity reached":** `mvn clean package` green; W6-G clean; the W6-X audit names a test class or an owner step for
all 45 GP rows (GP-33 with its recorded partial exclusion) and the automated ones ran green; the owner with two clients
(about 40 min) fights a 1v1 with a bet (win pays, draw refunds, quit forfeits, `/tp` blocked; a lethal hit by hand and by a
Bartizan gun leaves wanted 0, no drop, no downed state), then checks the WorldGuard flag in one region; smoke G1 without
WorldGuard and without the fights jar; docket row CM-38; `graphify update . --force`; W6-J go; **then** W6-Y flips
`parity/PARITY.md` and rebuilds `roadmap.html`.

**Scheduling with W5/W6.** Opus peak stays <= 3: executors W3-A, W4-R, W6-F; reviewers W3-G, W5-G, W4-G, W6-G; W3-G and W5-G
run in sequence, never together; W6-F does not start while W4-R and a gate reviewer are both running, and **at most one
reviewer runs while W6-F runs**. Test-server lock order: W3 gate (30 min) -> W5 gate (30 min) -> W4 gate (20 min) -> W6 gate
(40 min); if H13 is late, W6 goes before W4. At most 3 Maven builds at once across W3-W6 lanes. Parity lanes merge into
`gang-turf-territory` between W3 and W4 merges, each only after a green reactor build on the current head; the W3-G and W4-G
reviews exclude parity diffs, W5-G and W6-G own them. Owner sign-off gates: D16, D22, D23 before W5-K; D19 before W6-K; D20
before W6-W (each decides what a kickoff commits; a lane cannot default them).

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
- **Parity waves (section 7.10-7.11):** W5-pre (orchestrator: the parity and adjacent triage rows written and both dockets
  rebuilt; owner sign-off on D16, D22, D23) precedes W5-K; W5-K merges right after the W2 gate; W5 lanes run beside W3 on
  disjoint files in two fan-outs (at most 6 lane agents), W5-A after M/H/C/S merged, and **W5 lane branches merge only after
  the W3 gate closes**; W6 fights lanes start after W5-K merges, W6-W merges after W5-F and W6-F, W6-X after the W5 gate, W6-Y
  after the W6 gate; W6 runs beside W4. Parity lanes merge between W3 and W4 merges, each only after a green reactor build on
  the current head and a `git diff --name-only` check against the lane's Owns list plus 7.1; W3-G/W4-G exclude parity diffs,
  W5-G/W6-G own them. Opus: W3-G and W5-G in sequence; W6-F never while W4-R and a gate reviewer both run; at most one
  reviewer while W6-F runs. Test-server lock order W3 -> W5 -> W4 -> W6 (W6 before W4 if H13 is late). Owner sign-off: D19
  before W6-K, D20 before W6-W.
- **Seam stubs (parity kickoffs):** W5-K and W6-K commit every type of the 15.7 seam-signature table with trivial bodies; a
  lane's red test against a stub fails on an assertion, never on a compile error; a lane whose test needs a later lane's body
  uses a fake of the seam type.
- **Gangs+ is commercial:** lanes take feature ideas only and use the verbs, nodes, keys and strings of section 15.1; a lane
  that finds a drafted verb echoing Gangs+ wording renames it and records the rename in its exit report.

| Model | Used for |
|---|---|
| haiku 4.5 | YAML/JSON text in kickoffs, `commands.json` 3-way merges, docs and migration drafts (E1 parallel, E2 after merges), triage rows, test scaffolding from a written spec, verify-only checks |
| sonnet 5.5 | every bounded implementation lane and its tests; the code half of every kickoff |
| opus 5.5 | W2-R capture rules, W3-A civilians hit path, W4-R api record + cross-module roles, W6-F fight guards, and the adversarial review at every gate (W5-G, W6-G included) |
| (no Fable) | owner decision 2026-09-30: gate go/no-go, the D1 re-check and the map render-rule read are done by the orchestrator (the Opus main session); no Fable agents are spawned |

---

## 9. Docket integration

All 25 open GR and 38 open TF entries (`census/docket-gang-turf.md`, statuses pulled 2026-09-30; TF-03 is `fixed` and excluded)
are placed, plus 12 new rows (TF-40..49, GR-38, CM-37; ids follow the last ids in `bugs.json`: TF-39, GR-37, CM-36) and,
since the parity addendum, 3 more (GR-39, GR-40, CM-38; last ids re-verified in `bugs.json` 2026-09-30: GR-38, CM-37, TF-49). The
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
| W5-K | GR-40 (`Gang.Enable` becomes the `GangWorldPolicy` global switch), LS-35 (`WaypointTeleport` map leak on the cancelled path), LS-36 (downed player may start a waypoint teleport) |
| W5-L | US-43 (`Level.addLevels` burns XP and counts a cancelled level) |
| W5-F | GR-39 (self-shot flipped red -> green), GR-41 (Flame/Fire Aspect fire between gangmates), CJ-43 (a waypoint warm-up survives an arrest) |
| W5-A | GR-42 (`option gang rank` casts `(Player) sender`) |
| W6-K | CM-38 (`ModuleInstalls.OFFICIAL` gains `gang`, `lootchest`, `fights`) |
| Deferred | TF-15 (Double income column: a type change through the diff engine, sub-cent loss; revisit with a money-column sweep), TF-22 (Spanish turf strings ride CM-15; new strings go to module YAML so the `_es` twin is one file later), WB-43 (bounty claim between gangmates once friendly fire can be on: `EntityDamageListener` is W4-H's file, low value because a gangless alt could always claim; revisit with W4-H) |

Accounting: GR 25 open = 11 (W0-C) + 6 (W0-D) + 3 (W1-G) + 4 (W1-R) + 1 (W2-P1). TF 38 open = 9 (W0-B) + 7 (W0-A) + 1 (W0-C)
+ 1 (W0-E1) + 1 (W1-M) + 5 (W1-T) + 4 (W2-R) + 1 (W2-N) + 1 (W2-P1) + 5 (W2-Q) + 2 (W3-B) + 2 deferred = 39 lane slots, TF-02
in two lanes; 38 distinct ids. New rows 12 = TF-40 (W0-A), TF-41/42 (W0-B), TF-43 (W3-V), TF-44 (W2-P1), TF-45 (W2-Q), TF-46
(W3-B), TF-47 (W3-A), TF-48 (W2-R), TF-49 (W4-H), GR-38 (W2-P2), CM-37 (W1-K). `docket.assigned` in roadmap.json carries
the wave id (W0 38, W1 14, W2 15, W3 5, W4 1 = 73 = 61 open placed + 12 new; the 2 deferred are listed apart); lane
granularity lives in each lane's docket list. **Parity delta:** W5 +2 (GR-39, GR-40), W6 +1 (CM-38), plus the review's
adjacent rows W5 +6 (US-43, LS-35, LS-36, CJ-43, GR-41, GR-42) and 1 deferred (WB-43); total 73 -> 82 assigned; new rows
12 -> 22. All ten parity/adjacent rows are **written 2026-09-30** (`bugs.json` went from 612 to 622 entries, last free ids were
GR-38, CM-37, TF-49, US-42, LS-34, CJ-42, WB-42; the cross docket is 1074); `parity/design-parity-wave.md` 7.4 numbers GR-39/GR-40 the other
way round and is superseded by 9.3.

### 9.2 Pinned tests to flip (red first)

`CaptureServiceStartAndCompleteTest` (TF-01, W0-A), `TurfManagerTest` (TF-05, W0-B), `RankManagerTest` (GR-12 W0-C; GR-13 W1-R),
`GangAllianceTest` (GR-35 + TF-24 one-directional case, W0-C), `CaptureServiceOwnedTurfTest` (TF-12, W2-R; TF-26 re-documented),
`CaptureServiceHelpersTest` (TF-11 dead call site, W2-R), `ActiveBuffManagerTest` (TF-37 cap, W2-N), `GarrisonManagerTest`
(TF-20, W3-B), `RankPermissionApplierTest` fixtures naming `gangland.turf.capture/contribute` (deleted, W2-P1),
`CaptureServiceStartAndCompleteTest.ownedTurf_multipleChallengerGangsBlockStart` (TF-48, W2-R: it pins today's 1-vs-1
block; re-scope it to the plurality rule, red first). The W1 kickoff's
`CaptureEligibility` extraction is the one exception: characterization tests, green before and after. **Parity flips:**
`GangMembersDamageListenerTest` self-shot case (GR-39, W5-F: cancelled today, allowed after), `GangAllyWeaponImpactListenerTest`
with `Gang.Enable: false` (GR-40, W5-F: protection silently dropped today), `ModuleInstallsTest` for `gang`/`lootchest`
(CM-38, W6-K: unknown today), `GangModuleTest.configure_declaresConfigAndPackages` (W5-K: it pins `configurations()` to
`List.of(GangConfig.class)`, flipped when the per-lane configs are registered), core `LevelTest` cancelled-event case (US-43,
W5-L: XP burned today), the `GangMembersDamageListenerTest` combust case (GR-41, W5-F). The
`GangDisbandService`/`GangLeadership` extractions (W5-M) ship characterization tests.

### 9.3 New triage rows (ALL TWENTY-TWO DONE 2026-09-30 by the orchestrator - the first twelve, then the ten parity/adjacent rows GR-39..42, CM-38, US-43, LS-35, LS-36, CJ-43, WB-43, code-checked against master and written to the same triage files plus `users-levels-economy-bank.txt`, `lootchests-signs-waypoints.txt`, `cops-detainment-jail.txt`, `wanted-bounty-combat.txt` (confidence High except GR-41 and WB-43 Medium); both dockets rebuilt - Gangland 622, cross 1074; W0-E1 and W5-pre only re-check them. The first twelve went to `triage/turf.txt`, `gangs-ranks-mail.txt`, `commands-messages-platform.txt` as 8-field rows (Gangland 612, cross 1064 at that point). Still open for the orchestrator: republish both artifacts to their URLs and re-seed the cross docket status rows, US-43 is the residual of US-08 and LS-35 a sibling of LS-06)

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
| GR-39 (parity, DONE 2026-09-30) | P3 | A gang member's own arrow or projectile never damages them (no bow-boost, no self-knockback) while gangless players can: `GangMembersDamageListener` L27-48 resolves shooter and victim to the same user with the same gang id and cancels | W5-F |
| GR-40 (parity, DONE 2026-09-30) | P3 | `Gang.Enable: false` no longer disables gangs; its only reader is civilians' `GangAllyWeaponImpactListener` L42 (`Settings.isGangEnabled()`), so the setting drops ally protection against Bartizan guns while melee protection stays | W5-K (`GangWorldPolicy` global switch), W5-F (listener) |
| CM-38 (parity, DONE 2026-09-30) | P2 | `/glw module install gang` and `install lootchest` fail: `ModuleInstalls.OFFICIAL` (`IMPL/command/sub/module/ModuleInstalls.java` L30-35) lists six ids and omits two shipped modules that mail and turf depend on | W6-K |
| US-43 (parity review, DONE 2026-09-30) | P2 | `Level.addLevels` (`gangland-core/.../core/user/Level.java` L74-90) subtracts the XP before firing the event and increments its counter whether or not the event was cancelled: a cancelled level-up burns the XP and reports 1, for user and gang levels alike | W5-L |
| LS-35 (parity review, DONE 2026-09-30) | P3 | `WaypointTeleport.teleport` (`API/data/teleportation/WaypointTeleport.java` L126-134, L158) returns before `countdownTimer.remove(player)` when `TeleportEvent` is cancelled or the world is missing, and `totalDistance` is never cleared on any path: the next 1.5 blocks of walking prints "teleport cancelled" for a finished timer and the accumulator carries into the next warm-up | W5-K |
| LS-36 (parity review, DONE 2026-09-30) | P3 | A downed player (`CustomPlayerDeathListener`) has no command block and can start and complete a waypoint teleport while downed | W5-K (`WaypointTeleport` refuses a downed player) |
| CJ-43 (parity review, DONE 2026-09-30) | P3 | Cops has no `TeleportEvent`/`PlayerTeleportEvent` listener (`DetainmentListener.onCommand` L270-278 blocks commands only) and `WaypointTeleport` cancels a warm-up only on `PlayerMoveEvent`: a GANG waypoint warm-up started before an arrest or jail teleport fires when it ends and teleports the restrained player out | W5-F (NEW cops `RestrainedTeleportListener`) |
| GR-41 (parity review, DONE 2026-09-30) | P3 | `GangMembersDamageListener` L30-35 resolves only `Player` and `Projectile` attackers: Flame/Fire Aspect fire (`EntityCombustByEntityEvent`, then FIRE_TICK damage with no damager), tamed wolves and `TNTPrimed.getSource()` bypass same-gang protection | W5-F |
| GR-42 (parity review, DONE 2026-09-30) | P3 | `GangOptionContribution` `rank` verbs cast `(Player) sender` (L68, L80, L115, L183) under a console-allowed `/glw option` tree: `ClassCastException` from the console | W5-A |
| WB-43 (parity review, DONE 2026-09-30) | P3 | The bounty claim in `IMPL/listener/player/EntityDamageListener.java` L123-126 has no gang-relation check; reachable between gangmates once friendly fire can be switched on (a gangless alt could always claim) | Deferred (W4-H's file) |
| - | - | GR-02 adjacent "payout re-reads balance": already fixed; record in the GR-02 note, no row | - |
| - | - | Not filed (unconfirmed, fights.md section 11 rows 2-3): `EntityDamageListener.handleWanted` may move the bounty after a cancelled wanted raise (confirm against `Bounty.getAutoBountyIncrease` first); totems vs the downed state is an observation, not a bug | - |

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
| **D16** (parity) | Gang level model (GP-10/11/12) with D2 frozen (XP only from actively held turf; turf cap by level) | A XP-only: W2-P1 as planned, the level table only sets limits, no pay-to-level (GP-10 missing). B buy-only: the bank buys levels (the competitor's model) - breaks frozen D2, listed to be ruled out. C hybrid: held-turf XP qualifies a level, `/glw gang upgrade` previews XP and money missing, `upgrade confirm` pays `Levels.Level_<n>.Cost` from the bank; one level number drives members, safehouses, allies, chat prefix and the turf cap; shipped defaults = the 15.6 fee table (a D9 sink), `Cost: 0` on every row restores W2-P1's automatic levelling on the same code path. D separate perk tier: two progression numbers on every screen | **C** with shipped fees: the only option that delivers GP-10 while keeping "XP only from actively held turf" (money can never make XP or buy the cap, only gate a level already earned); a generous level-0 row (8 members, 1 safehouse, 1 ally) removes the bootstrap worry behind a perk tier; the fee is a YAML number the owner may zero | W5-K, W5-L, W5-M, W5-H, W5-C |
| **D17** (parity) | Member and ally caps (reverses the section 12 drop; GP-11, GP-25) | A caps from the D16 table, 0 = unlimited, grow-only (an over-cap gang keeps everyone, cannot recruit or ally until under). B no caps. C member cap only | **A**: owners plus allies all count as defenders (P3.2), so an unbounded alliance is a defender blob and one gang can absorb the server | W5-M, W5-L |
| **D18** (parity) | Safehouses and rally versus turf fights (GP-26/27/42) | A own `gang_safehouse` table, `shield` 0, `TurfTeleportGuardListener` veto (source or destination under attack, destination in a non-allied turf), `GangSafehouseSetEvent` so turf refuses a safehouse inside a rival or contested turf, combat tag, consent requests to online members only. B allow the teleports, stamp arrivals as attackers (a W2-R file change; defenders still escape). C safehouses only outside every turf | **A**: closes the escape hatch and the instant reinforcement without touching W2-R's files; A4 (sides frozen at contest start) covers recruits mid-fight | W5-H, W5-T |
| **D19** (parity) | Where do gang fights live (GP-29..31, GP-41)? | A NEW runtime module `gangland-gang-fights` (`Depends: [gang]`; fights.md DF1, DF2-DF8 as lane defaults). B inside `gangland-gang` (a sixth hot lane on the hub module; no opt-out jar). C turf duels on the capture engine (a declared raid by another name; section 12 cut them, fights.md section 9 rejects them) | **A**: the mail precedent already decided that a state machine + expiry + repository + `CommandContribution` under `gang` is a module | W6-K |
| **D20** (parity) | Friendly-fire model (GP-17/18/19/44) | A one rule (15.5), one api fact `GangMembership.pvpBlocked` read by fists, bows, potions and Bartizan impacts; gang toggle default off; ally friendly fire negotiated (both rows on) and PvP-only; guards and the Quartermaster unconditional; per-world overrides; negative-potions-only; self damage allowed; the soft WorldGuard flag `glw-gang-pvp` in W6-W. B = A without the flag (half of GP-18 missing). C gang module only: move the Bartizan listener into gang (a `bartizan-api` edge on the hub module; cops and later readers still cannot ask) | **A**: one decision point; capture, defender counting and guard semantics untouched; civilians stays gang-free | W5-F, W6-W |
| **D21** (parity) | Gang names (GP-06) | A ASCII `name` key by `Name_Pattern` (default W1-G's `[A-Za-z0-9_]{3,16}`, admins may widen it to any script, whitespace refused at load), free display name (`Display_Name.Allow_Hex` true, `Parse_Colours` true, `Max_Length` 32), console tokens and the map key use `name`, glyph fallback (A1). B Unicode `name` by default. C ASCII only, display name restricted too | **A**: any-language display with a safe key | W5-U, W1-M (A1) |
| **D22** (parity) | Short command labels (GP-04, GP-13) | A only `/glw gang ...` + a documented `commands.yml` recipe (no completion). B struck 2026-09-30 (it proposed the competitor's own label set; the copyright rule forbids command wording, so it is not an option; a server that wants other labels remaps them in `commands.yml`, C18). C `/gw`, `/gwc`, `/gwa` forwarded with completion (Gangland's own labels); a label another plugin holds stays reachable as `/gangland_warfare:<label>` | **C**: own wording, same convenience. Owner sign-off before W5-K (the kickoff writes the `plugin.yml` label commands) | W5-K, W5-U |
| **D23** (parity) | Release shape and the api bump (section 2 promised no api in W0-W3 and one bump in W4) | A one 0.14.0 after the later of the W4 and W6 gates; one additive api bump for the branch (W5-K bumps and adds the batch, W4-R adds `NpcRole` under it, A2); if H13 has not landed by the W6 gate the owner may ship 0.14.0 without W4 (W4 becomes 0.14.1). B 0.14.0 at W4 as planned, parity as 0.15.0 on a follow-up branch (two bumps, two migration docs) | **A**: nothing is released between W2 and W6, so one bump is the honest version and one upgrade for server owners; default methods keep 2.0-built jars loading | W5-K, W4-R (A2), final merge (A3) |
| **D24** (parity) | Auto-purge default (GP-40) | A off; staff run `/glw option gang purge preview` then `run`; when enabled `Purge.Inactive_Days` 30 for gangs and 60 for non-owner members (both above turf `Inactivity_Release_Days` 4), disband through `GangDisbandService` (D15 split, no fee refund). B on by default with those numbers | **A**: a deleted gang cannot be restored | W5-M, W5-A |

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
| **Parity:** the gang module's lane count doubles and a new module lands while W3/W4 run | W5-K creates every seam, stub, column and YAML key before lanes fork; the 7.1 + 15.7 table names one owner per file per wave; W5/W6 touch none of W3's or W4's files; parity merges only after a green reactor build on the current head; W3-G/W5-G sequential |
| Friendly-fire toggles used to farm guards, stats or the D14 refund | guards and the Quartermaster stay immune to owners and allies whatever any toggle says (cops listener untouched); same-gang and allied kills never count; ally friendly fire needs both sides; defender counting ignores the flags |
| Safehouses and rally become raid escapes or instant reinforcement (a caller one block outside an attacked turf rallies the gang); recruits or new allies swing a running bar | `TurfTeleportGuardListener` vetoes the source inside a turf under attack, a destination inside or within `Teleport_Guard_Buffer_Blocks` 48 of one, a destination in a non-allied turf, and every GANG-type teleport of a gang that is owner or challenger in a running contest; `shield` 0; `CombatTag` (gang pairs only); cops `RestrainedTeleportListener`; A4 freezes sides at contest start ("recruited or allied during a fight, you count after it ends" - one sentence on the refused action and in the migration note) |
| Bought levels buy the turf cap (frozen D2) | D16 C: the XP bar (held turf only) is checked in gang code before `Level.addLevels`; money only claims a level already earned; `Cost: 0` restores automatic levelling; the turf-cap column is asserted equal to `TurfRules.cap` |
| Async chat reads the `HashMap` managers off the main thread | recipient snapshots refreshed on join/leave/ally change on the main thread; a fake-scheduler test; W5-G checks |
| The api batch breaks a 2.0-built module jar or forces two bumps on one branch | default methods only, events are new classes; a binary-compat test loads an old anonymous `GangMembershipView` (three abstract methods, not a functional interface); one bump per release branch (D23, A2); `Host_Api` floor per module |
| Gang stats or the world gate leak into core kill credit or collide with W4-H on `EntityDamageListener` | gang stats are gang-owned member counters fed by a HIGH `ignoreCancelled` ledger (a MONITOR ledger never sees the lethal hit that `CustomPlayerDeathListener` cancels at HIGHEST when the downed system is on); core `User` counters and impl `EntityDamageListener` untouched by parity; `PlayerDeathEvent`/`PlayerDownedEvent` read at MONITOR |
| WorldGuard flags register in `onLoad` before the module loader; a server without WorldGuard sees `reflection.type.missing` faults or `NoClassDefFoundError` (Keystone `ReflectionGuard.orSkip` drops the **whole** `@Configuration` whose `@Bean` signature names a missing type: it is a safety net, not a wiring mechanism) | type-free factory behind a plugin-present check on both sides (impl registrar, gang `WorldGuardRegions.create()`); WorldGuard-typed classes only under `IMPL/hook/worldguard/**` and `GG/gang/combat/region/**`, never in a `@Bean` signature; `GangCombatConfigTest` with WorldGuard absent; `ReflectionGuard`; smoke G1 without WorldGuard; W6-W the sole pom owner |
| Fight event ordering (lethal intercept before wanted, bounty, drops, downed state) is only provable live | NORMAL-priority intercept with unit tests per listener; the manual two-client Spigot check is the W6-F exit and a W6 gate item; `FightWantedGuard` as a second net |
| Escrowed bets create or lose money on a crash, reload or disband | escrow at accept with the second-withdraw rollback; `abortAll` on disable and reload refunds; `FightGangDeleteListener` pays the survivor; `FightSettlementTest` with real `EconomyHandler` instances; `House_Cut_Percent` 0 |
| Staff `bank give` is an unlogged faucet; a bank leaderboard exposes rich gangs as raid targets | every staff bank write logs actor, gang and amount; bank sorts, columns and board tokens need `gangland.gang.balance.others` / `ranking.wealth` (default op) |
| Short labels collide with another plugin | Bukkit gives a label to the first registrant; `/gangland_warfare:<label>` always works; `commands.yml` remap; migration note |
| Level-0 caps land on gangs already over them; auto-purge deletes gangs on upgrade | caps are grow-only (nobody is removed); purge off by default with a preview verb (D24); both lead the W5 migration section |
| A drafted verb, key or message copies Gangs+ wording (commercial plugin) | 15.1 fixes Gangland's own vocabulary (the third pass renamed `friendlyfire` -> `pvp`, the flag -> `glw-gang-pvp`, `rank-number` -> `rank-position` and struck D22 B after a token comparison against the competitor's public pages); lanes rename on sight and record it; the W6-X audit rereads every verb, node, key and token against `competitors/gangsplus.md` and the live public pages (tokens compared, nothing copied) |

---

## 12. Out of scope

Player-claimable chunks, overclaim, per-chunk protection; block protection inside turfs; declared raids with fees, warnings
and windows, and persistent gang wars; the `ALWAYS` capture window; loss shields; per-gang rank trees; rank-node inheritance
toward the Tail (the `Ranks.Minimum` map covers "this rank and above"); gang bounty posting; gang vault items; per-member turf
score and `/glw turf top`; `/glw gang ping` and man-down callouts; neighbour/allied responders; Medic field care;
policed-district cop dispatch; the map item; a persisted `turf_event_log` table; `TurfChunkIndex` (until a measured server
needs it); BlueMap, squaremap, Pl3xMap, turf holograms, the `TurfMapLayer` cops overlay seam; Spanish turf strings (TF-22);
the income column type (TF-15); any `RadioVoice`/`SquadRadio` api change; Keystone changes before a second consumer; weapons
on guards (Bartizan owns weapons); any Paper API. **Since the parity addendum, also out:** third-party combat-tag plugin
adapters (the built-in `CombatTag` covers GP-33); turf as a fight stake and spectating eliminated fighters (fights.md DF7 /
DF6 B, later); durable per-gang kill counters beyond current members; a core `PlayerCombatStatEvent` (gang stats are
gang-owned); moving the Bartizan-impact listener out of civilians; admin-chosen short labels through a Keystone `CommandMap`
helper (Keystone 1.15.0). **Since the parity addendum, now in scope:** member and ally caps (D17), safehouses beyond the
existing `WaypointType.GANG` waypoint (D18).

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

### 13.1 Parity scorecard (2026-09-30, second pass)

Scores 1-10. Lenses: parity completeness, fit with the fair-fights/turf design and frozen D1-D4, codebase fit, delivery (file
ownership, parallelism), YAGNI. Both designs were checked against code (`GangMembersDamageListener` L27-48,
`GangAllyWeaponImpactListener` L42, `GangPermissions.allows` L67-77, `ModuleInstalls.OFFICIAL` L30-35, core `Level.addLevels`
L74-79, `GangMembershipView` abstract-only members, `Waypoint.shield` -> `setInvulnerable`, `GangAlliance.since` at `Gang.java`
L95, `GangOptionContribution` `(Player) sender` casts).

| Design | Parity | Fair-fights fit | Codebase fit | Delivery | YAGNI | One-line verdict |
|---|---|---|---|---|---|---|
| fold-in (8 lanes into W1-W3, 0 new waves) | **9** every row, own vocabulary throughout, GP-33 adapters deferred with a reason | **8** sides frozen at contest start is the best answer to recruits mid-fight; guards unconditional; but the core `PlayerCombatStatEvent` lets gang policy cancel core `User` kill credit | 6 moves the Bartizan-impact listener into the hub module (`bartizan-api` edge on gang for one listener), a core event fired from impl `EntityDamageListener` (W4-H's file), rank-node inheritance touching `RankPermissionApplier`, api additions in W2-K against section 2 | 6 ten amendments across existing lanes; W2 becomes eight executors plus two reviewers with a 70-minute owner gate; W3 opus peak 2; no audit lane | 6 inheritance **and** a `Minimum_Rank` map for the same need; a five-rung default preset; `CommandShortcutExecutor` plus label commands; fees are the right sink |
| parity-wave (W5 + W6, W0-W4 untouched) | **9** every row; GP-33 partial exclusion recorded; an audited parity gate (W6-X) | 7 twenty conflicts settled; the membership re-stamp does not close the instant-defender hole a mid-fight recruit opens; fee default 0 hides GP-10 | **8** one api fact keeps civilians gang-free; gang-owned member counters touch no core file; kickoff-created seams; module edges unchanged; WorldGuard behind a type-free factory | **8** two waves on files disjoint from W3/W4, one kickoff per wave, fixed merge orders, opus and test-server scheduling, a "parity reached" gate; cost: 21 lanes and a heavy W5-K | 7 `chatChannelOf`/`GangChatChannel` api for GP-37's chat toggles, three member columns, nine decisions; but verbs `top`, `levelup`, `regroup`, `player`, `motd`, `fight challenge|join|leave` echo Gangs+ wording |

Backbone: **parity-wave**. Grafted from fold-in: Gangland's own vocabulary for every verb (15.1); sides frozen at contest start
(A4 on W2-R, replacing W5-T's re-stamp listener); the cancellable `GangSafehouseSetEvent`; one `rally` verb for GP-27 and GP-42;
the 15.6 fee table as shipped defaults (D16 C); the no-default-true `gangland.gang.*` guard. Rejected from fold-in: rank
inheritance, the five-rung default, the Bartizan listener move, the core stat event, api additions in W2-K, a
`CaptureEligibility` reason for gang-disabled worlds (W2-R's file stays closed; `turf create` refuses instead). Rejected from
parity-wave: the Gangs+-echoing verbs, `/g`/`/gc`/`/ac` as the recommendation (kept as D22 B), `TurfMembershipRestampListener`.

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

### 14.1 Parity log (2026-09-30, second pass)

Inputs: `parity/design-fold-in.md`, `parity/design-parity-wave.md`, `parity/matrix.md`, `parity/reuse.md`, `parity/fights.md`.

**Applied**

| Source | Item | Where |
|---|---|---|
| parity-wave 0/5 | two waves W5/W6 beside W3/W4, W0-W4 untouched except A1-A4; kickoff-created seams; merge orders; opus and test-server scheduling; "Gangs+ parity reached" gate with the W6-X audit | 7.9-7.11, section 8, 15.7 |
| parity-wave 4, C4-C6 | one `FriendlyFireRule`, api fact `GangMembership.pvpBlocked` (default method), civilians listener switched, cops guard immunity unconditional, ally FF PvP-only | 15.5, W5-F, D20 |
| parity-wave 1 (GP-20/22), reuse.md 1.3 | gang-owned `member.kills|deaths|assists` fed by a MONITOR ledger; no core event | P7.7, W5-S, section 2 |
| parity-wave 3, C16 | `Ranks.Minimum` + `Ranks.Preset`; `PluginYmlPermissionsTest` asserts no default-true `gangland.gang.*` | 15.3-15.4, W5-R, W5-K |
| parity-wave D16-D24, A1-A3 | decisions D16-D24; W1-M glyph fallback; W4-R bump rule; W4 merge after the later gate | section 10, 7.5, 7.8 |
| fold-in vocabulary table | every verb renamed to Gangland's own: safehouse, rally, ranking, upgrade, profile, notice, `fight propose|enlist|drop`, `arena finish`, `{glw_gang}`, `/gw` labels | 15.1, P7/P8, 7.10-7.11 |
| fold-in C1 | sides frozen at contest start (`gangJoinDateLong`, `GangAlliance.since` vs the contest-start stamp) as A4 on W2-R; W5-T's re-stamp listener dropped | 7.6, 15.3, section 2 |
| fold-in C2 | cancellable `GangSafehouseSetEvent` + turf refusal inside a rival or contested turf | P7.6, W5-H, W5-T, D18 |
| fold-in GP-27/42 | one `rally` verb: `rally <safehouse>` and `rally` on the caller | P7.6, W5-H |
| fold-in D16 table | fees shipped as defaults (D9 sink), `Cost: 0` = W2-P1 behaviour | 15.6, D16 |
| fold-in docket rows | GR-39 (self-shot), GR-40 (`Gang.Enable`), CM-38 (`ModuleInstalls`) with fold-in's id assignment | 9.1-9.3 |
| fights.md 0-8, DF1-DF8 | module placement, 3D arenas, NORMAL intercept, escrow, ranked rules, tests; `save` renamed `finish` | P8, 7.11, D19 |
| advisor | DELIVERED vocabulary in PARITY.md; W2-R scope **and** tests amended in both files; W5-L exit reworded for shipped fees; byte-identity assertions on `roadmap.json` | parity/PARITY.md, 7.6, 7.10 |

**Rejected**

| Source | Item | Reason |
|---|---|---|
| fold-in 3 | rank-node inheritance toward the Tail + `RankPermissionApplier` change | A second mechanism beside the `Minimum` map for the same need; widens rights on existing installs; touches the live `PermissionAttachment`. |
| fold-in 3 | five-rung default preset (`member, soldier, officer, underboss, owner`) | D13 A and the vocabulary table say Member / Officer / Owner; GP-07 asks for configurability, not a count; five rungs ship as a YAML example. |
| fold-in 4 | move `GangAllyWeaponImpactListener` into the gang module with `bartizan-api` provided | A Bartizan edge on the hub module for one listener; the api fact serves civilians now and cops later without either knowing the gang module. |
| fold-in C9/C12 | core `PlayerCombatStatEvent` fired from impl `EntityDamageListener`/`PlayerDeathListener`, cancelled by gang | Gang policy would cancel core `User` kill credit for every consumer; the fire site is W4-H's file; gang-owned counters need no core change. |
| fold-in C9/D19 | api additions in W2-K and W3-FK | Section 2's "no new api surface in W0-W3" holds when the batch lands in W5-K after the W2 gate; one bump either way. |
| fold-in 5 | eight parity lanes folded into W1-W3 | W2 would run eight executors beside the opus capture rewrite with a 70-minute owner gate; the fair-fights gate stays focused and parity gets its own audited gate. |
| fold-in GP-32 | `CaptureEligibility` reason for gang-disabled worlds | W2-R's file stays closed; `turf create` refuses such a world and an existing turf logs one warning (parity-wave C18). |
| parity-wave 1 | verbs `top`, `levelup`, `regroup`, `player`, `motd`, `fight challenge|join|leave`, `arena save` | They echo the competitor's command wording (`competitors/gangsplus.md` GP-10, 21, 26, 27, 29, 30); the copyright rule forbids it. |
| parity-wave D22 | `/g`, `/gc`, `/ac` as the recommendation | Gangs+'s labels; kept as option B for migrating servers, `/gw`, `/gwc`, `/gwa` recommended. |
| parity-wave C10 | `TurfMembershipRestampListener` + `TurfLocationTracker.restamp` | A re-stamp only delays attackers 15 s; a bystander who joins the owner gang still defends at once. A4 closes both sides. |
| parity-wave D16 | fee default 0 | GP-10 would be invisible on a default install and the D9-B faucet (36,000 per active turf per day) would keep only guards and boosts as sinks; the owner may still zero the fees. |
| matrix.md D16/D17 | separate perk tier | Two progression numbers on every screen; the D16 C level-0 row solves the bootstrap worry (parity-wave's argument, verified against `Level`). |
| matrix.md D20 | ally FF-on stops the ally counting as a defender | Adds a third relation to `CaptureService` (W2-R, opus) for no gain; the anti-farm rules (no stats between friends, guards immune) already remove the exploit. |

### 14.2 Parity review log (2026-09-30, third pass)

Inputs: `reviews/parity-feasibility.md` (FAIL -> PASS_WITH_FIXES once B1-B10 applied), `reviews/parity-delivery.md`
(PASS_WITH_FIXES), `reviews/parity-completeness.md` (PASS, nothing to apply). Finalizer: fable 5.1. Every code claim the
fixes rest on was re-read before applying: core `Level.addLevels` L74-90 (XP subtracted before the event, counter
unconditional), impl `CustomPlayerDeathListener.onEntityDamage` L91-113 (HIGHEST, cancels the lethal hit), api
`WaypointTeleport.teleport` L126-134 (returns before the map removal), Keystone `Tree.isDescendant` L76-77 (same node ->
false), Keystone `BeanFactory` L259 (`ReflectionGuard.orSkip` over `getDeclaredMethods`: the whole config is skipped),
Keystone `ReflectionUtil.findClasses` (recursive: subpackages of the one gang listener package are scanned), `GangModule`
L37-38 + `GangModuleTest` L32-33 (pins), `GangCommand.initializeArguments` L87+ (hand-wired children), api
`HelpInfo.displayHelp` L75-100 (no permission check; `CommandInformation` is `(usage, description)`), `bugs.json` (612
entries, no GR-39/GR-40/CM-38), `GangAllyAbandonCommand` (gang module), `build_roadmap.py` (writes `roadmap.html` only),
`ModuleInstalls.OFFICIAL` (`Map.of`, six pairs), `GangMembershipView` (three abstract methods), `MoneyDropListener`
(MONITOR on `PlayerDeathEvent`). Pre-existing lane ids and D1-D15 are unchanged against `roadmap.pre-parity.json`; new
lane W6-Y; no new decision id (D22's option B is struck in place so every "D22 C" reference stays valid).

**Applied**

| Source | Item | Where |
|---|---|---|
| feasibility B1 | cancelled level-up burned XP and charged the fee: root fix in core `Level.addLevels` (subtract and count only after a non-cancelled event, US-43, W5-L owns the file), fee only when it returns 1, `upgrade` refused at `Gang_Level_Max`; `LevelTest` flipped, `GangUpgradeCommandTest` cases | 7.1, W5-L, 9.1-9.3 |
| feasibility B2 | `DamageLedger` at HIGH, `ignoreCancelled`, `getFinalDamage() > 0` (a MONITOR ledger never sees the killing blow when the downed system is on); "respawn on: one-shot kill credits the shooter" in `DamageLedgerTest` and `GangStatsListenerTest` | section 2, P7.5, 15.5, W5-F, W5-S, risks |
| feasibility B3 | WorldGuard behind a type-free bean + NEW `WorldGuardRegions.create()` static factory (the only gang class naming WorldGuard) instead of a `ReflectionGuard`-skipped bean; `worldedit-bukkit` named at `provided`; `GangCombatConfigTest` with WorldGuard absent; `FlagConflictException` caught (S5) | P8.4, W6-W, risks |
| feasibility B4, delivery B4 | the turf-overlap check cannot be coded (fights sees no turf type): DF8 A as a documented admin rule printed by `arena finish`; W6-G checklist line | P8.1, 15.3 C7, W6-A, W6-G |
| feasibility B5 | `Ranks.Minimum` clause = same node OR `isDescendant(min, rank)`; "exactly at the minimum passes" test row | W5-R |
| feasibility B6 | `WaypointTeleport` cancelled and world-missing paths clear both maps (LS-35), body-only api change in W5-K + `WaypointTeleportCancelTest` | 7.1, W5-K, 15.7, 9.3 |
| feasibility B7 | copyright: `friendlyfire` -> `pvp` (`/glw gang pvp`, `/glw gang ally pvp`, nodes `gangland.gang.pvp`/`gangland.gang.ally.pvp`, classes `GangPvpCommand`/`GangAllyPvpCommand`), flag -> `glw-gang-pvp`, `user_rank-number` -> `user_rank-position`, D22 B struck in place (the competitor's label set), `/gw`/`/gwc`/`/gwa` the only labels; W6-X rereads the live pages through the owner's browser (tokens compared, nothing copied). `Friendly_Fire.*` keys, `FriendlyFireRule`, `gang_friendly-fire` stay | 15.1, 15.5, 15.9, P7.5, P8.4, D20, D22, W5-F, W5-U, W6-W, W6-X, risks, PARITY.md |
| feasibility B8 | rally one block outside an attacked turf: `Teleport_Guard_Buffer_Blocks` 48 + veto of every GANG-type teleport for a gang that is owner or challenger in a running contest ("your gang is in a turf fight"); `TurfTeleportGuardListenerTest` cases | P7.6, 15.3 C2, W5-T, risks |
| feasibility B9 | a bet above 0 needs the `Withdraw` minimum on propose and accept; `Max_Bet_Percent_Of_Bank` against both banks; `Max_Net_Transfer_Per_Pair_Per_Day` restored, `Max_Bet_Transfer_Per_Gang_Per_Day` added; `FightRulesTest` rows | W6-S, W6-E |
| feasibility B10 | KDR: victim must be in a non-allied gang older than `Stats.Min_Victim_Member_Age_Hours` 24, `Stats.Max_Kills_Per_Victim_Per_Day` 3; WLR board counts at most `Ranked_Board_Wins_Per_Opponent_Per_Day` 1 and `Ranked_Min_Opponent_Gang_Age_Days` 7; level rewards only for members older than `Levels.Reward_Min_Member_Age_Hours` 24 | W5-S, W6-E, W5-L, P7.7 |
| feasibility F1, delivery S5 | `GangAllyAbandonCommand` is in the gang module (`GG/command/sub/gang/ally/`), not mail; W5-U keeps it; `GangFilterAdapter` assigned to W5-S; the staff Tail bypass to W5-M's `GangLeadership` | 7.1, W5-U, W5-S, W5-M, W5-A |
| feasibility F2 | binary-compat test with an anonymous class or precompiled stub (three abstract methods, no lambda) | W5-K, 15.7, risks |
| feasibility F3 | NEW cops `listener/police/RestrainedTeleportListener` on api `TeleportEvent` (CJ-43), owned by W5-F as its one cops file | P7.5, W5-F, 9.3 |
| feasibility F4 | attacker resolution covers `Tameable` owner, `TNTPrimed.getSource()` and `EntityCombustByEntityEvent` (GR-41); `FriendlyFireRuleTest` rows | 15.5, W5-F, 9.3 |
| feasibility F5 | `%` escaped as `%%` in every spliced chat-format value; `%` case in the format-token test | W5-C |
| feasibility F6 | the async-to-main hop stated: cancel the async event, deliver and fire `GangChatEvent` on the main thread | W5-C |
| feasibility F7 | `XPotion.DEBUFFS`; `setIntensity(victim, 0)` caveat documented | 15.5, W5-F |
| feasibility F8 | `%gang%` spliced only when `name` matches `[A-Za-z0-9_]+`, else `%gang_id%`; `Name_Pattern` load check refuses `@`, `/`, `;`, whitespace | W5-M, W5-U |
| feasibility F9 | display-name uniqueness on the colour-stripped, NFKC-normalised, lower-cased form; `Display_Name.Blocked_Words` | W5-U |
| feasibility F10 | `safehouse set` and GANG teleports refused in a gang-disabled world; NEW fights `FightArenaSafehouseListener` refuses a safehouse inside any `ArenaBox` | P7.6, W5-H, W6-F |
| feasibility F11 | non-participants refused entry and ejected during LOBBY..RESULT; block place/break refused for everyone in a busy arena | W6-F |
| feasibility F12, delivery S13 | the `fight enlist` refusal and a COUNTDOWN re-check of `CombatTag` live in W6-S (drop the participant, refund a short side); dropped from W5-F's PARITY row | 15.3 C3, W6-S, PARITY.md GP-33 |
| feasibility F13 | W5-A fixes the four `(Player) sender` casts (GR-42) | 7.1, W5-A, 9.3 |
| feasibility F14 | `{glw_gang}` at LOW documented against LOWEST/NORMAL format rewrites; a chat-format plugin in the W5 walkthrough | W5-C, 7.10 gate |
| feasibility F15 | menus in impl resources by precedent; dead item source without the gang module recorded | W5-S |
| feasibility S1 | labels registered only when `GangMembership.isInstalled()`; each label with its own `permission:` line | W5-K |
| feasibility S2 | `CombatTag` tags only when both players are gang members | 15.5, W5-F |
| feasibility S3 | purge uses the newest last-played across all members incl. the owner; `Purge.First_Run_Delay_Hours` 24 | W5-M |
| feasibility S4 | offline reward runner skips a null `OfflinePlayer.getName()` with a log line | W5-L |
| feasibility adjacent 1-7 | triage rows US-43, LS-35, LS-36, CJ-43, GR-41, GR-42 (assigned) and WB-43 (deferred: W4-H's file); fights.md's stale `ModuleInstalls` line reference noted | 9.1-9.3, 7.11 |
| delivery B1 | every new gang listener under `GG/listener/gang/**` (the scan is recursive; `GangModule` registers one package and `GangModuleTest` pins it); Owns cells re-pathed; `GangModuleTest` in 9.2; `git diff --name-only` check at every lane merge | 7.1, 7.10, 9.2, section 8, 15.5, 15.7 |
| delivery B2 | K re-wires `GangCommand`/`GangAllyCommand` constructors once over the seam types; one NEW no-arg `@Configuration` per bean-producing lane registered by K (`GangMembershipConfig`, `GangLevelConfig`, `GangCombatConfig`, `GangSafehouseConfig`, `GangChatConfig`, `GangStatsConfig`, `GangUxConfig`); `GangConfig` re-parameterized by K only; `GangMembershipInstaller` gets a seam constructor and three delegates (`MembershipQueries` M, `FriendlyFirePolicy` F, `ChatChannels` C) | 7.1, W5-K, W5-M/F/C, 15.7 |
| delivery B3 | seam-signature table in 15.7 (W5 and W6 types), committed by the kickoffs; orchestrator read before fan-out; red tests fail on an assertion, never a compile error; W5-A `parallel: false`, branches after M/H/C/S merged; `GangLimits` columns listed | 15.7, 7.10, section 8, roadmap.json |
| delivery B5 | W5-pre orchestrator step writes GR-39/GR-40/CM-38 and the adjacent rows through `triage/*.txt` + `build_docket.py` before W5-K; `design-parity-wave.md` 7.4 numbering superseded | 7.10, 9.1, 9.3, section 8 |
| delivery B6 | GP-04 help clause: `HelpInfo.displayHelp` prints its whole list and Keystone's visibility filter covers `/glw` only, so W5-U filters in `GangCommand.help` by the child `Argument` permission (no api change), `GangHelpFilterTest`; GP-38: `GangRulesReloadTest` (W5-K), `FightRulesReloadTest` (W6-K) | W5-U, W5-K, W6-K, PARITY.md GP-04/GP-38 |
| delivery B7 | W6-X audits before the gate and flips nothing; NEW lane W6-Y (haiku) flips PARITY.md after the owner session and W6-J's go; rebuild claim corrected (`build_roadmap.py` writes `roadmap.html` from PARITY.md's JSON block; `roadmap.json` is not rebuilt) | 7.9, 7.11, P8.4, section 8, roadmap.json |
| delivery S1 | kickoffs write the final `commands.json` entry per verb; lanes edit only on an argument-shape change; E2 proofreads | 7.1, W5-K, W6-K, 15.9 |
| delivery S2 | one top-level section per lane in `gang_rules.yml`, `gang_messages.yml`, `fight_rules.yml`, `fight_messages.yml`; `turf_messages.yml` W5-T beside W3-B by section; `GangRulesKeyParityTest`/`FightRulesKeyParityTest`; sonnet + `gangland-yaml-review` pass on the haiku YAML (S11) | 7.1, W5-K, W6-K |
| delivery S3 | merge order K -> R -> L -> M -> F ...; tests needing a later lane's body use fakes of the K seam types (stated per row) | 7.10 |
| delivery S4 | two fan-outs (R, L, M, H, F, C; then T, S, U), A after M/H/C/S, at most 6 lane agents | 7.9, 7.10, section 8, roadmap.json |
| delivery S6 | K's test list in full in the PLAN row (`GangWorldPolicyTest`, api default-method tests, binary-compat, `LocalizedModuleYamlLanguageTest`, impl `GanglandPlaceholderGlobalTest`, `GangRulesReloadTest`, `GangRulesKeyParityTest`, `HostApiConsistencyTest`, `GangModuleTest` flip, `GangModuleConfigTest`); `FightsModuleConfigTest` in W6-K; `ModuleInstalls.OFFICIAL` -> `Map.ofEntries` | W5-K, W6-K |
| delivery S7 | migration: `inventory/*.yml` created only when missing, `documentation/gangland-api.md` (W5-E1), one E2 lane per window for the migration doc, `CLAUDE.md` lines after W6, WorldGuard flag after a full restart, fees as the D9-B sink | 15.9, 7.1, W5-E1 |
| delivery S8 | wave headings reworded "decisions to settle before the wave starts"; owner sign-off gates D16, D22, D23 before W5-K, D19 before W6-K, D20 before W6-W | 7.10, 7.11, section 8, D22 |
| delivery S9 | W6-F proves the NORMAL intercept cancels every lethal fight source (Bartizan, void, fall); `MoneyDropListener` named for W6-G as the H13 soft spot | W6-F, W6-G |
| delivery S10 | W5-K merges after the W2 gate (its diff is in the W3 gate build; a W3 gate failure traced to parity goes to W5-G); W5 lane branches merge only after the W3 gate closes; at most one reviewer while W6-F runs; W5-G in two sequential passes | 7.10, 7.11, section 8, roadmap.json |
| delivery S12 | W6-W may start beside A/S/E, merges after W5-F and W6-F; `GG/gang/combat/region/**` "after W5-F merged" | 7.11 |
| delivery nits | K exit reworded to the `git diff --name-only` check; `Chat.Public_Tag.Enabled` false and `Purge` off kept; fees-as-sink in the migration text; tally stays HAVE 3 / DELIVERED 42 because GP-04's help clause is built, not excluded | 7.10, 15.9, PARITY.md |
| completeness | PASS: all 45 rows, 24 parity lanes, D16-D24, pillars P7/P8 valid; nothing to apply | - |

**Rejected**

| Source | Item | Reason |
|---|---|---|
| feasibility B1 (gang-side workaround) | compare `getLevelValue()` before and after `addLevels` and restore the XP in gang code | The root cause is three lines in core `Level.addLevels` and every caller (user levels included) has the same bug; W5-L owns the file and no other lane touches it, so the fix goes there once (ponytail: one guard in the shared function, not in every caller). |
| feasibility B8 (rally-send refusal) | refuse `rally` sends for a contesting gang inside the gang module | The gang module cannot see turf state without a new seam; every `rally join` is a GANG-type `TeleportEvent`, so the turf listener's "gang in a turf fight" veto already stops every arrival with one sentence. |
| feasibility F12 (turf re-check at COUNTDOWN) | re-check "inside a turf under attack" at COUNTDOWN | Fights cannot see turfs (B4); the fight teleport is a plain `player.teleport`, not a `TeleportEvent`; the `CombatTag` re-check covers it because anyone fighting in a turf is tagged by the ledger. |
| delivery B4 (b) | `TurfFootprintLookup` api seam so fights can validate arenas against turfs | Grows the W5-K batch and W5-T for a check the design already declined (DF8 A); the printed admin rule and the W6-G checklist line give the outcome. |
| delivery B7 (X1/X2 ids) | rename W6-X into W6-X1/W6-X2 | `W6-X` is referenced in the wave `depends_on`, section 8 and P8.4; keeping it as the audit and adding `W6-Y` for the flip changes fewer references. |
| delivery S9 (W4 half) | a `MoneyDropListener` skip for fighters placed in W4 after H13 merges | `gangland-item` cannot know the fights module; the intercept test makes the fallback provably unreachable, and W6-G reviews the soft spot. YAGNI until the test shows a reachable path. |
| delivery S10 (tag cut) | run the W3 gate on a tag cut before the first W5 merge | Only W5-K's additive diff precedes the W3 gate (lane branches wait); a separate tag adds a second integration head to reconcile. The gate builds the head as it stands and W3-G reviews W3 diffs only. |
| delivery S11 (W5-F model) | run W5-F on opus | Kept sonnet: the opus budget stays for the reviewers and W6-F; W5-G's first pass starts with W5-F and the ledger/tag/rule table is fully unit-tested. |
| delivery B5 (assigned only after rows exist) | remove GR-39/GR-40/CM-38 from `roadmap.json` `docket.assigned` until W5-pre runs | The `assigned` list is the plan's placement, not the docket's state; W5-pre is a named precondition of W5-K and 9.1 says the rows are unwritten. |

---

## 15. Gangs+ parity and beyond

Owner goal (2026-09-30): every Gangs+ feature available in Gangland's gang system, and Gangland outclassing every gang plugin.
Inventory: `competitors/gangsplus.md` (GP-01..38 shipped, GP-39..41 announced and never shipped, GP-42..45 from other
plugins). Final mapping with the machine-readable rows: `parity/PARITY.md`. Path shorthand as in the header plus `API/` =
`gangland-api/src/main/java/org/luckyraven/gangland/`, `IMPL/` = `gangland-impl/src/main/java/org/luckyraven/gangland/`.

**Copyright rule.** Gangs+ is commercial: feature ideas only. Every verb, node, YAML key and message below is Gangland's own;
strings live in module YAML (`gang/gang_messages.yml`, `fight/fight_messages.yml`), keys are `Capitalized_Underscore`, nodes
are `gangland.gang.*` / `gangland.admin.gang.*` / `gangland.command.*`.

### 15.1 Vocabulary (the drafts' Gangs+-echoing verbs, renamed)

| Idea | Gangland wording |
|---|---|
| leaderboard | `/glw gang ranking [stat] [page]`; placeholders `%gangland_gang_board_<stat>_<n>_<name|value|level>%` (our words; the shape is generic) |
| a player's rank position | placeholder `user_rank-position` (not `rank-number`, which mirrors a competitor token's words) |
| pay for the next level | `/glw gang upgrade` (preview: XP and money missing), `/glw gang upgrade confirm` |
| gang homes | **safehouses**: `/glw gang safehouse [name]`, `safehouse set <name>|remove <name>|list` |
| regroup / summon | `/glw gang rally [safehouse]` (no argument = rally on the caller); members answer `/glw gang rally join` |
| player profile | `/glw gang profile [player]` + `inventory/player_profile.yml` |
| gang MOTD | `/glw gang notice <text>|clear` |
| gang chat, ally chat | `/glw gang chat [msg]`, `/glw gang chat allies [msg]` (toggle when no message) |
| friendly fire | `/glw gang pvp on|off`, `/glw gang ally pvp <gang> on|off`; nodes `gangland.gang.pvp`, `gangland.gang.ally.pvp` (the competitor uses the `friendlyfire` subcommand word and node leaf, so those are out; "friendly fire" stays as prose in docs, in the `Friendly_Fire.*` YAML section, in `FriendlyFireRule` and in the dash-style `gang_friendly-fire` placeholder, none of which collide) |
| gang fights | `/glw gang fight propose <gang> <size> [bet]`, `accept`, `decline`, `enlist`, `drop`, `info`, `arenas`, `stats` |
| arenas | `/glw arena create|corner|spawn <side>|exit|size|finish|show|list|tp|on|off|delete|reload` (`finish`, never `save`) |
| staff tools | the existing console-allowed `/glw option gang ...` tree: `disband <gang>`, `leader <gang> <player>`, `bank <gang> balance|give|take|reset`, `safehouse <gang> list|tp|remove`, `stats reset <player>`, `spy`, `purge preview|run` |
| chat-format token | `{glw_gang}` (behind `Chat.Public_Tag.Enabled`, off by default) |
| WorldGuard flag | `glw-gang-pvp` (the competitor's flag is its own name with a prefix; ours shares no word order) |
| short labels | D22 C: `/gw`, `/gwc`, `/gwa`, the only label set (option B struck: it was the competitor's); remappable per server through `commands.yml` |
| module | `gangland-gang-fights`, id `fights` |

### 15.2 Parity table (GP-01..GP-45 -> feature / lane)

Status now from `parity/matrix.md`; **After**: HAVE (works today), DELIVERED (a named lane ships the row), EXCLUDED (with reason).
Tally: HAVE 3, DELIVERED 42, EXCLUDED 0; GP-33 carries one recorded partial exclusion. The full "how" per row is in
`parity/PARITY.md`; here the feature and lane.

| GP | Feature (our words) | Now | After | Feature | Lane(s) |
|---|---|---|---|---|---|
| 01 | create, rename, disband with confirm; staff disband | PARTIAL | DELIVERED | P7.1 (`GangDisbandService`, `option gang disband`) | W5-M, W5-A |
| 02 | invite, cancel invite, join by name, kick, leave | HAVE | HAVE | P1.4 (decline, expiry), P7.1 (one service) | W0-D, W5-M |
| 03 | transfer leadership; staff set-leader | PARTIAL | DELIVERED | P7.1 (`GangLeadership`, `option gang leader`) | W5-M, W5-A |
| 04 | gang list with bank column, info by gang or player, help shows only what you may run | PLANNED | DELIVERED | P2.5 + P7.8 (`gang info <player>`, `balance.others`) | W1-G, W1-K, W5-U |
| 05 | console commands on join/leave/kick | MISSING | DELIVERED | P7.1 (`Commands.On_*`, ASCII name token only) | W5-M |
| 06 | any-language names, colour codes, hex | PARTIAL | DELIVERED | P7.8 (D21) + A1 glyph fallback | W5-U, W1-M |
| 07 | configurable ranks, one ladder everywhere, promote/demote | PLANNED | DELIVERED | P2.6 + P7.3 (`Ranks.Preset`) | W1-R, W5-R |
| 08 | per-feature minimum rank | PARTIAL | DELIVERED | P7.3 (`Ranks.Minimum`) | W5-R |
| 09 | node per command, wildcard nodes | PARTIAL | DELIVERED | P2.5 (CM-37) + P7.8 (`gangland.command.player`, `gangland.admin.gang.*`) | W1-K, W5-K |
| 10 | pay to reach the next level, amount missing shown | MISSING | DELIVERED | P7.2 (D16 C `gang upgrade`) | W5-L |
| 11 | per-level max members, safehouses, chat prefix | MISSING | DELIVERED | P7.2 level table (D16/D17) | W5-L, W5-M, W5-H, W5-C |
| 12 | level-up rewards incl. offline; level-up event | PLANNED | DELIVERED | P4.4 + P7.2 (`Levels.Level_<n>.Commands`, `GangLevelReachedEvent`) | W2-P1, W5-L |
| 13 | gang chat toggle + one-shot; ally chat | PLANNED | DELIVERED | P4.5 + P7.4 (`chat allies`) | W2-P2, W5-C |
| 14 | staff chat spy; chat to server log | MISSING | DELIVERED | P7.4 (`ChatSpyService`, `Chat.Log_To_Console`) | W5-C, W5-A |
| 15 | chat-format token; default for gangless | PARTIAL | DELIVERED | P7.4 (`{glw_gang}`) + P7.7 (`Placeholders.Default_Value`) | W5-C, W5-S |
| 16 | chat events for other plugins | MISSING | DELIVERED | P7.8 api + P7.4 (`GangChatEvent`) | W5-K, W5-C |
| 17 | friendly fire per gang and global | PARTIAL | DELIVERED | P7.5 | W5-F |
| 18 | per-world friendly fire; region flag | MISSING | DELIVERED | P7.5 (`Friendly_Fire.Worlds`) + P8.4 (WorldGuard) | W5-F, W6-W |
| 19 | harmful potions only; self bow-boost | MISSING | DELIVERED | P7.5 (`Friendly_Fire.Potions`, `Allow_Self_Damage`; GR-39) | W5-F |
| 20 | kills, deaths, assists, KDR, fights W/L, profile | PARTIAL | DELIVERED | P7.7 + P8.2 | W5-S, W6-E |
| 21 | leaderboard, sortable | MISSING | DELIVERED | P7.7 (`gang ranking`, turfs via P7.6/W5-T, wlr via P8.2) | W5-S, W5-T, W6-E |
| 22 | staff stat reset; no counting where gangs are off | MISSING | DELIVERED | P7.7 + P7.1 | W5-S, W5-A |
| 23 | bank; others' balance hidden | PARTIAL | DELIVERED | P7.8 + P7.7 (`gangland.gang.balance.others`) | W5-U, W5-S |
| 24 | staff bank balance/give/take/reset | MISSING | DELIVERED | P7.1 (logged) | W5-A |
| 25 | alliance request, back to neutral, alliance limit | PARTIAL | DELIVERED | P1.4 + P7.2 (`Max_Allies`) + P7.8 (abandon by name) | W5-L, W5-U |
| 26 | named homes: set/delete/list/tp, safe check, level cap, switchable | PARTIAL | DELIVERED | P7.6 safehouses | W5-H, W5-T |
| 27 | regroup at a home | MISSING | DELIVERED | P7.6 (`rally <safehouse>`) | W5-H, W5-T |
| 28 | staff home tools | MISSING | DELIVERED | P7.1 (`option gang safehouse`) | W5-A |
| 29 | challenge, team size, bet, accept/decline, join/leave | MISSING | DELIVERED | P8.2 | W6-K, W6-S, W6-E |
| 30 | several arenas, held inside, staff arena tools | MISSING | DELIVERED | P8.1 + P8.3 | W6-A, W6-F |
| 31 | command blocking, equal teams, winner takes the bet, W/L | MISSING | DELIVERED | P8.3 + P8.2 | W6-F, W6-S, W6-E |
| 32 | disable gangs in chosen worlds | MISSING | DELIVERED | P7.8 (`GangWorldPolicy`, GR-40) | W5-K, W5-T |
| 33 | combat-tag support | MISSING | DELIVERED (partial exclusion) | P7.5 built-in `CombatTag` (leave, safehouse, rally) + P8.2 (`fight enlist` refusal and COUNTDOWN re-check); **excluded:** third-party combat-tag adapters (no common API, one soft dependency each; the built-in tag gives the outcome) | W5-F, W6-S |
| 34 | ~25 placeholders, leaderboard tokens, default | PARTIAL | DELIVERED | P7.7 (+ api `resolveGlobal` from P7.8) | W5-K, W5-S |
| 35 | MySQL/SQLite, periodic save, schema updates | HAVE | HAVE | Keystone persistence | - |
| 36 | titles, customisable messages, any language | PARTIAL | DELIVERED | P7.8 (`gang_messages.yml`, `GangNotifier`) | W5-K, W5-U |
| 37 | public API: queries and events | PARTIAL | DELIVERED | P7.8 api batch (D23) | W5-K |
| 38 | staff reload | HAVE | HAVE | `ReloadCommand` + P1.2 | - |
| 39 | player and gang profile GUI (never shipped) | PARTIAL | DELIVERED | P7.7 menus | W5-S |
| 40 | auto-purge (never shipped) | MISSING | DELIVERED | P7.1 (`GangPurgeTask`, off by default, D24) | W5-M, W5-A |
| 41 | more fight features, events, API (never shipped) | MISSING | DELIVERED | P8.1-P8.4 | W6-A, W6-S, W6-E, W6-F |
| 42 | summon every member | MISSING | DELIVERED | P7.6 (`rally` on the caller) | W5-H, W5-T |
| 43 | top by balance and members; members by rank | PARTIAL | DELIVERED | P7.7 + P7.8 | W5-S, W5-U |
| 44 | negotiated ally friendly fire | MISSING | DELIVERED | P7.5 (`gang_ally.friendly_fire`, both rows on) | W5-F |
| 45 | open vs invite-only; gang notice | PLANNED | DELIVERED | P4.5 (D7) + P7.8 (`gang notice`) | W2-P2, W5-U |

### 15.3 Conflicts with fair fights, turf and frozen D1-D4, and how each is settled

| # | Collision | Resolution | Where |
|---|---|---|---|
| C1 | **Join, accept or ally mid-contest** (GP-02, 25, 45): a bystander inside joins the owner gang (or a third gang allies the owners) and defends at once (P3.2 counts defenders immediately); symmetrically a joiner on the attacking side counts after 15 s | **Sides freeze at contest start, both sides.** `classify` counts a member on any side only if `Member.gangJoinDateLong` precedes the contest-start stamp, and an ally for the owners only if `GangAlliance.since` (set to `Instant.now()` at `Gang.java` L95) precedes it; later arrivals are bystanders until the contest ends. `TurfRuntimeState` has no start stamp today (only `lastChallengerSeenAt`, L36); the stamp W2-R adds for `Max_Contest_Minutes` is reused. Pure rule, no listener, no schema. Player sentence: "recruited or allied during a fight, you count after it ends" | A4 on W2-R (7.6) |
| C2 | **Safehouses as raid escapes or instant reinforcement** (GP-26, 27, 42); `Waypoint.shield` calls `player.setInvulnerable(true)` (`WaypointTeleport.java` L150-152) | `TurfTeleportGuardListener` cancels the api `TeleportEvent` (fired by `WaypointTeleport` before the jump) for a GANG-type teleport whose source is inside a turf under attack, whose destination is inside or within `Teleport_Guard_Buffer_Blocks` (48) of one (rally one block outside, walk in: closed), whose destination is inside a non-allied gang's turf, or whose player's gang is owner or challenger in any running contest ("your gang is in a turf fight": no staging pad, no escape, one sentence); safehouse waypoints are built with `shield` 0 (a test pins it) and never added to `WaypointManager`; `GangSafehouseSetEvent` lets turf refuse `safehouse set` inside a rival or contested turf; rally reaches online members only (the D1 owners-online window is untouched). Another plugin's raw `player.teleport` is out of scope; the veto exists to close the hole this feature would open | D18, W5-H, W5-T |
| C3 | **Combat escapes** (GP-33): leave the gang or teleport away mid-fight | `CombatTag` (`Combat.Tag_Seconds` 15, from the HIGH `DamageLedger`, gang pairs only) blocks `gang leave` and every GANG-type waypoint teleport (`CombatTeleportGuard`) in W5-F/W5-M; the `fight enlist` refusal and the COUNTDOWN re-check live in the fights module (W6-S), which does not exist during W5 | W5-F, W5-M, W6-S |
| C4 | **Friendly-fire toggles vs guards, allies, civilians, cars** (GP-17/44): owners farm their own guards (D14 refund, P6.3 bounty); allies that may shoot each other still count as defenders | The toggles change **player-vs-player damage only**. Cops `TurfFriendlyFireListener` (guards and the Quartermaster immune to owner and ally *players*) stays unconditional and untouched; defender counting, ally chat and guard immunity ignore the flags; same-gang and allied kills never count in gang stats (anti-farm); civilians are not gang members; cars are property (`CarDamageListener` damages vehicles only; car access stays `CarAccessPolicy.sharesGang`, GD-06). Divergence from `matrix.md` (FF-on ally stops counting): changing capture semantics adds a third relation to `CaptureService` for no gain once stats and guards are exploit-free | D20, 15.5 |
| C5 | **Bought levels vs frozen D2** (GP-10): money buying `Gang.level` buys the turf cap and skips the activity gate | D16 C: XP still comes only from actively held turf; `gang upgrade confirm` needs `experience >= Level.experienceCalculation(nextLevel())` **and** the fee; the gang code checks the XP bar itself because core `Level.addLevels` (L74-79) levels even when `experience < requiredExp`; turf cap column = `min(6, 2 + level)`, unchanged and asserted | D16, W5-L |
| C6 | **Member and ally caps cut by the synthesis** (sections 1 and 12) | Reinstated as columns of the D16 level table (0 = unlimited); they also bound the plurality zerg (P3.2) and the ally defender blob (`CaptureService.isOwnerAlly`); grow-only on existing gangs | D17, section 12 |
| C7 | **Fights vs turf, wanted, downed state, drops** (GP-29..31) | fights.md 5.1: a NORMAL-priority lethal intercept runs before impl `EntityDamageListener` (HIGH) and `CustomPlayerDeathListener` (HIGHEST): no wanted, bounty, drop, downed state or respawn teleport; arenas are placed outside turfs as a documented admin rule that `arena finish` prints (DF8 A; fights cannot see turfs: `Depends: [gang]` only, no turf type in `gangland-api`); allied gangs cannot fight (bet laundering); fights feed W/L only, never `Gang.level` (D2) | D19, W6-A, W6-F |
| C8 | **"No new api surface in W0-W3"** (section 2) vs GP-16/17/34/37/44 and the fight event | The batch is additive (default methods on `GangMembershipView`, which only has abstract members today, so an abstract addition would break a 2.0-built jar; new event classes; one `default resolveGlobal`) and lands in W5-K after the W2 gate, so the letter of section 2 holds; one bump per release branch, W4-R adds `NpcRole` under it | D23, A2, 15.7 |
| C9 | **WorldGuard region flag** (GP-18): flags register only in `onLoad`, before the module loader | The core registers the flag from `Gangland.onLoad` (`IMPL/Gangland.java` L52) through a type-free factory behind a plugin-present check; the gang module reads it by name through WorldGuard's registry, so no Gangland api seam; soft dependency only | D20, W6-W |
| C10 | **Unicode names vs the map glyph class** (GP-06, 5.4 rule 2) | ASCII `name` under `Name_Pattern`, free `displayName`; glyph = first `A-Z` letter of the colour-stripped display name, else of `name`, else a free letter | D21, A1 |
| C11 | **Stats in gang-disabled worlds and between friends** (GP-22) vs core kill credit in impl `EntityDamageListener` L121 (W4-H's file) | Gang stats are gang-owned `member.kills|deaths|assists` written by `GangStatsListener` at MONITOR from `PlayerDeathEvent`/`PlayerDownedEvent`; core `User` counters stay as they are; no core event, no impl fire site | W5-S, section 2 |
| C12 | **Purge vs D15 payout and turf** (GP-40) | Purge is a disband through `GangDisbandService`: equal split per D15, no 25 % fee refund, `GangDeleteEvent` releases turfs through W0-A `TurfGangDeleteListener`; `Purge.Inactive_Days` 30 exceeds turf `Inactivity_Release_Days` 4; off by default | D24, W5-M |
| C13 | **Wildcard nodes** (GP-09) vs `GangPermissions.allows`, whose first clause is `player.hasPermission(node)` (L69): a default-true `gangland.gang.*` parent would bypass every rank gate | The wildcard for "all player commands" is `gangland.command.player` over the Keystone command nodes; staff verbs under `gangland.admin.gang.*` (op); `PluginYmlPermissionsTest` asserts no `gangland.gang.*` node or parent defaults true | W5-K |
| C14 | **Console commands on join/leave/level with gang names spliced in** (GP-05/12) | Only `%gang%` (the validated ASCII `name`, whitespace stripped), `%gang_id%` and `%player%` are substituted, never the display name; `Name_Pattern` refuses whitespace at load | D21, W5-M, W5-L |
| C15 | **Level prefix vs other chat plugins** (GP-11/15) | The prefix renders inside Gangland's channels and in public chat only when `Chat.Public_Tag.Enabled` is true (default false); `%gangland_gang_level-prefix%` serves chat-format plugins | W5-C |
| C16 | **Turf in a gang-disabled world keeps paying and granting XP** (GP-32) | `turf create` refuses such a world; an existing turf there logs one warning at load; `CaptureEligibility` is not touched (W2-R's file stays closed) | W5-T |
| C17 | **Staff `bank give` as a faucet; bank leaderboards as target lists** (GP-23/24) | Every staff bank write logs actor, gang and amount; bank sorts, columns and board tokens need `gangland.gang.balance.others` / `ranking.wealth` (op) | W5-A, W5-S |
| C18 | **Short labels collide** (D22) | Bukkit gives a label to the first registrant; `/gangland_warfare:<label>` always works; `commands.yml` remap; migration note | W5-U |

### 15.4 Rank model (GP-07/08, reconciled with D13)

`RankManager` holds one global tree (Head `member` at the root, Tail `owner` the deepest leaf; census gang.md s1).
`GangPermissions.allows` (`GG/gang/permission/GangPermissions.java` L67-77) = server permission OR the rank's own node list
(`Member.hasPermission`, Vault included) OR the top rank; there is no inheritance (`ranks.md` documents one that is not
implemented). `RankAssignmentPolicy` already walks the tree with Keystone `Tree.isDescendant`.

- **GP-07** ("same ladder in every gang, names configurable, promote/demote"): the tree is global by construction, names are
  admin-editable (`/glw rank ...`), `GangPromoteCommand`/`GangDemoteCommand` walk it. Only the seeded *count* is a gap: NEW
  `Ranks.Preset` list read by the W1-R seed on an **empty** rank table only (D13 A unchanged). Default `member, officer, owner`
  (the vocabulary table's Member / Officer / Owner); the YAML comment shows a five-rung example.
- **GP-08** ("which rank may invite, kick, withdraw, set a safehouse, ally, fight"): node lists alone would copy a node onto
  every rung. NEW `Ranks.Minimum` map (feature -> rank name) read by **one** extra clause in `allows`: pass when the member's
  rank is the named rank or sits below it toward the Tail (`Tree.isDescendant`, argument order pinned by a test). Node lists,
  Vault and the top-rank clause stay as exceptions. An unknown rank name logs one warning and the clause is off for that
  feature. A denied action names the lowest rank that can (the P2.6 promise).
- Shipped minimums (block style): `Chat`, `Deposit`, `Safehouse` = member; `Fight`, `Rally` = officer; `Invite`, `Kick`,
  `Promote`, `Demote`, `Withdraw` (TF-45 spirit), `Safehouse_Manage`, `Recruiting`, `Description` = officer; `Ally`, `Rename`,
  `Display`, `Color`, `Notice`, `Upgrade`, `Friendly_Fire` = owner. The W2-Q `gangland.turf.upgrade` gate goes through the same
  `allows`, so it gains the map for free.
- Kick, promote and demote keep their rank-position checks *and* gain the gate.
- **Per-gang ranks are not needed**: the competitor's ladder is one global config too. D13 stays **A**; C (per-gang trees, a
  schema change) stays out of scope. Node inheritance is rejected (14.1).

### 15.5 One friendly-fire rule

NEW pure `GG/gang/combat/FriendlyFireRule.blocked(attacker, victim, location)`, unit-tested as a table, first match wins:

1. Either player gangless, or the world in `Worlds.Disabled` (or `Gang.Enable: false`): **not blocked** (gangs do not exist there).
2. `attacker == victim`: blocked only if `Friendly_Fire.Allow_Self_Damage` is false (default **true**: bow boosting and self
   splash pass; fixes GR-39, today `GangMembersDamageListener` L27-48 cancels a member's own arrow).
3. Region flag `glw-gang-pvp` at `location` (`FriendlyFireRegionSource`, a no-op until W6-W): ALLOW -> not blocked,
   DENY -> blocked.
4. `Friendly_Fire.Worlds.<world>: ALLOW|DENY` decides.
5. Same gang: blocked unless `Friendly_Fire.Global` is `GANG_CHOICE` (default) and `gang.friendly_fire` is on (`gang pvp
   on|off`, node `gangland.gang.pvp`), or `Global` is `ON`; `Global: OFF` always blocks.
6. Allied (symmetric `Gang.isAlly` after W0-C): blocked unless **both** `gang_ally.friendly_fire` rows are on (`gang ally
   pvp <gang> on|off`, node `gangland.gang.ally.pvp`, each side sets its own row; GP-44) and `Global` is not `OFF`.
7. Rival gangs: never blocked by this rule.

Attacker resolution (one helper shared by the listeners): `Player`, `Projectile` shooter, `Tameable` owner, `TNTPrimed.getSource()`;
`EntityCombustByEntityEvent` runs through the same rule so Flame/Fire Aspect fire between gangmates is cancelled at the
source (the later FIRE_TICK damage has no damager; GR-41).

Potions (GP-19): `PotionSplashEvent`/`AreaEffectCloudApplyEvent` drop a blocked victim from the affected set only for effects in
XSeries `XPotion.DEBUFFS` (present in the pinned 13.6.0) when `Friendly_Fire.Potions: NEGATIVE_ONLY` (default), or for all
effects when `ALL`; `setIntensity(victim, 0)` strips every effect of a mixed potion for that victim, stated in the docs.

**One fact, three readers.** The api holder gains `GangMembership.pvpBlocked(UUID attacker, UUID victim, Location at)`, backed
by a `default` method on `GangMembershipView` that returns today's `alliedOrSame` semantics, so an older gang module behaves
exactly as now and the holder returns false when no view is installed.

| Reader | Module | Change |
|---|---|---|
| `GG/listener/gang/GangMembersDamageListener` (LOWEST; melee + projectiles) | gang | rewritten onto `FriendlyFireRule`; the GR-15 null guard from W0-C kept |
| NEW `GG/listener/gang/combat/PotionFriendlyFireListener` (scanned package) | gang | GP-19 |
| `GC/listener/gang/GangAllyWeaponImpactListener` (Bartizan `WeaponRaytraceImpactEvent`, `condition = "isBartizanAvailable"`) | civilians | `Settings.isGangEnabled()` + `alliedOrSame` (L42-47) -> `membership.pvpBlocked(...)`; still gang-module-free |
| cops `TurfFriendlyFireListener` | cops-n-crooks | **no change**: player-vs-NPC guard protection, unconditional (C4) |
| gadget car access (`GanglandCarGangs`) | gadget | **no change**: access, not damage (GD-06 pins "shares a gang") |

The damage ledger (NEW `GG/listener/gang/combat/DamageLedger`) listens at **HIGH, `ignoreCancelled = true`, `getFinalDamage() > 0`**:
after the LOWEST friendly-fire cancel, so a hit the rule blocked never tags anyone and never earns an assist; before the
HIGHEST lethal intercept of impl `CustomPlayerDeathListener` (L91-113 cancels the killing blow when `Respawn.Enable` is on),
so a one-shot kill still records the shooter (a MONITOR ledger would credit nobody, or the assister); the fights NORMAL
intercept cancels lethal fight hits before it, which is right because fights own their stats; a 0-damage snowball never tags.
`CombatTag` tags only when both players are gang members (a gangless alt cannot tag-lock a rival out of every safehouse); `CombatTag` (`Combat.Tag_Seconds` 15) and assists
(`Stats.Assist_Window_Seconds` 10) read the same ledger. Out of the rule, by design, stated once in
`documentation/features/gangs.md`: guards and the Quartermaster; who counts on the bar; gang stats between friends; cars;
opposing fighters (never allied by the fights rule).

### 15.6 Level table (D16 C, `gang_rules.yml` `Levels.Level_<n>`)

XP per level = the P4.4 curve (`Gang_Level_Max` 20, `Gang_Level_Base` 100, `base * level ^ 1.5`), cumulative at 72 XP per
day per actively held turf. A missing row inherits the previous row; 0 = unlimited. The turf-cap column is the frozen D2
formula `min(6, 2 + level)` (owned by `TurfRules.cap`, shown and asserted only). Fees are sized against the D9-B faucet
(36,000 per actively held turf per day) and the 100,000 create cost: a three-turf gang pays level 5 in about two days of
income. `Cost: 0` on every row restores W2-P1's automatic levelling on the same code path; `Levels.Level_<n>.Commands` ship empty.

| Level | Cumulative XP | Days at 1 / 3 turfs | Cost (bank) | Max members | Max safehouses | Max allies | Chat prefix | Turf cap (D2) |
|---|---|---|---|---|---|---|---|---|
| 0 | 0 | - | - | 8 | 1 | 1 | none | 2 |
| 1 | 100 | 1.4 / 0.5 | 20,000 | 10 | 1 | 1 | `&8[&71&8]` | 3 |
| 2 | 383 | 5.3 / 1.8 | 40,000 | 12 | 2 | 2 | `&8[&72&8]` | 4 |
| 3 | 903 | 12.5 / 4.2 | 75,000 | 14 | 2 | 2 | `&8[&a3&8]` | 5 |
| 4 | 1,703 | 23.7 / 7.9 | 120,000 | 16 | 3 | 2 | `&8[&a4&8]` | 6 |
| 5 | 2,821 | 39 / 13 | 180,000 | 18 | 3 | 3 | `&8[&b5&8]` | 6 |
| 6-9 | curve | - | +50,000 per level | +1 per level (22 at 9) | 4 from level 7 | 3 | `&8[&b6&8]`.. | 6 |
| 10-20 | curve | - | +75,000 per level | 24 at 10, +1 per 2 levels (29 at 20) | 5 | 3 | `&8[&610&8]`.. | 6 |

### 15.7 The api batch, shared files and amendments

**One additive batch in W5-K, one `VERSION` bump per release branch (D23):**
- `GangMembershipView` default methods: `pvpBlocked(UUID, UUID, Location)` (default = `alliedOrSame`), `gangIds()`,
  `gangIdByName(String)`, `membersOf(int)`, `ownerOf(int)`, `chatChannelOf(UUID)` with NEW enum `GangChatChannel {PUBLIC,
  GANG, ALLIES}`; matching absent-default holder methods on `GangMembership` (the type `GanglandApi.gangs()` returns).
- NEW events under `API/events/gangs/` (plural, so no split package with the module's `events.gang`; ids and names, never the
  module's `Gang` type): `GangCreatedEvent`, `GangMemberJoinedEvent`, `GangMemberLeftEvent(Reason: LEFT, KICKED, DISBANDED,
  PURGED)`, `GangLevelReachedEvent`, `GangChatEvent` (cancellable, channel), `GangFightEndedEvent`. Module code keeps
  `GangDeleteEvent` and `GangLevelUpEvent` as they are.
- `PlaceholderContribution` `default resolveGlobal(String)` returning null + the null-player branch in impl
  `GanglandPlaceholder` for board tokens.
- `LocalizedModuleYaml`: body change only, tries `<base>_<code>.yml` for the picked language before English (`LocalizedModuleYamlLanguageTest`).
- `WaypointTeleport`: body change only (LS-35, LS-36): the cancelled and world-missing paths of `teleport` (L126-134) clear
  `countdownTimer` and `totalDistance` like the success path; a downed player (`DownedPlayerRegistry`, core, re-exported by
  the api) is refused before the warm-up. W5 makes `TeleportEvent` vetoes routine (today only gadget `GrappleAbortListener`
  listens), so the leak would otherwise print "teleport cancelled" on the next 1.5 blocks after every veto
  (`WaypointTeleportCancelTest`).
- `Host_Api`: gang, civilians, turf and fights read new members and carry the new value; mail and the others stay `2.0`.
  Divergence from fights.md (`Host_Api: 2.0`): fights fires `GangFightEndedEvent`, so it must carry the new value.
- Binary-compat test: an anonymous `GangMembershipView` (or a precompiled stub class) compiled without the new methods still
  loads; the interface has three abstract methods (`gangIdOf`, `gangsAllied`, `nameOf`), so it is not a lambda target.

**Seam-signature table (committed by the kickoffs with trivial bodies; an orchestrator read checks it before fan-out):**

| Type (NEW unless stated) | Package | Signatures | Body by |
|---|---|---|---|
| `GangLimits` | `GG/gang/level` | `int maxMembers(int gangId)`, `int maxSafehouses(int gangId)`, `int maxAllies(int gangId)`, `String chatPrefix(int gangId)`, `double upgradeCost(int gangId)`, `double upgradeXpMissing(int gangId)`; 0 = unlimited | W5-L |
| `GangLevelTable` | `GG/gang/level` | `Row row(int level)` (record `Row(int level, double cost, int maxMembers, int maxSafehouses, int maxAllies, String chatPrefix, List<String> commands)`) | W5-L |
| `GangMembershipService` | `GG/gang/membership` | `Result join(int gangId, UUID player, JoinSource source)`, `Result leave(UUID player, LeaveReason reason)`, `Result kick(int gangId, UUID actor, UUID target)`, `Result disband(int gangId, UUID actor, boolean staff)`, `Result setLeader(int gangId, UUID actor, UUID target, boolean staff)` | W5-M |
| `GangDisbandService`, `GangLeadership` | `GG/gang/membership` | extracted from `GangDeleteCommand`/`GangTransferCommand`; K commits the class names with today's bodies moved verbatim (characterization tests green before and after) | W5-M |
| `MembershipQueries`, `FriendlyFirePolicy`, `ChatChannels` | `GG/gang/membership`, `GG/gang/combat`, `GG/gang/chat` | the three `GangMembershipInstaller` delegates: `List<UUID> membersOf(int)`, `Optional<UUID> ownerOf(int)`, `Set<Integer> gangIds()`, `int gangIdByName(String)`; `boolean pvpBlocked(UUID, UUID, Location)`; `GangChatChannel chatChannelOf(UUID)` | W5-M, W5-F, W5-C |
| `DamageLedger` | `GG/listener/gang/combat` | `Optional<UUID> lastDamager(UUID victim)`, `List<UUID> assistants(UUID victim, Duration window)`; HIGH, `ignoreCancelled`, damage > 0 | W5-F |
| `CombatTag` | `GG/gang/combat` | `boolean isTagged(UUID)`, `void tag(UUID attacker, UUID victim)`, `void setFighting(UUID, boolean)` (fights flag), `Duration remaining(UUID)` | W5-F |
| `FriendlyFireRegionSource` | `GG/gang/combat/region` | `Optional<Boolean> allowed(Location at)` (empty = unset); no-op bean in `GangCombatConfig` | W5-F (no-op), W6-W (WorldGuard) |
| `GangSafehouseService` | `GG/gang/safehouse` | `int count(int gangId)`, `Optional<Safehouse> find(int gangId, String name)`, `List<Safehouse> list(int gangId)`, `void remove(int gangId, String name)`, `CompletableFuture<TeleportResult> teleport(Player, Safehouse)` | W5-H |
| `GangSafehouseSetEvent` (gang module event, cancellable) | `GG/events/gang` | `int gangId()`, `Location location()`, `String name()` | K (full) |
| `GangStatContribution` | `GG/gang/stats` | `String key()`, `long value(int gangId)`; collected through the container like `CommandContributions.from(container)` | K (full); turf, fights implement |
| `GangStatsService` | `GG/gang/stats` | `Snapshot snapshot()`, `void resetPlayer(UUID)` (W5-A's `stats reset`) | W5-S |
| `ChatSpyService` | `GG/gang/chat` | `boolean toggle(UUID staff)`, `Set<UUID> spies()` | W5-C |
| `GangWorldPolicy` | `GG/gang` | `boolean enabled()`, `boolean enabledIn(World)` | K (full) |
| `GangPurgeTask` | `GG/gang/purge` | `List<PurgeCandidate> preview(Instant now)`, `int run(Instant now)` | W5-M |
| W6: `ArenaRegistry` | fights `arena` | `Optional<Arena> byName(String)`, `List<Arena> enabled()`, `Optional<Arena> freeArena()`, `void reload()` | W6-A |
| W6: `FightSettlement` | fights `fight` | `EscrowResult escrow(Fight)`, `void settle(Fight, Outcome)`, `void refund(Fight)`, `void abortAll()` | W6-E |
| W6: `FightClock` | fights `fight` | `Instant now()`, `void schedule(Runnable, Duration)` | W6-S |

**Shared files:** the parity rows in 7.1 (the parent files that hand-wire children and beans - `GangCommand`,
`GangAllyCommand`, `GangConfig`, `GangModule`, `GangMembershipInstaller` - are kickoff-only; lane beans live in the lane's
own config class; every new gang listener is under `GG/listener/gang/**`). Every other file has one owning lane per wave
(7.10, 7.11); the orchestrator checks each lane's `git diff --name-only` against its Owns list at merge.

**Amendments to existing lanes (ids unchanged):**
- **A1 W1-M** (GP-06): glyph fallback for a non-`A-Z` display initial; `TerritoryRasterTest` case.
- **A2 W4-R** (D23): "bump `VERSION` by one minor" becomes "if `gang-turf-territory` already carries the W5-K bump, add
  `NpcRole` under that value; one bump per release branch".
- **A3 W4 exit** (D23 A): "then `gang-turf-territory` -> master as 0.14.0" becomes "after the later of the W4 and W6 gates".
- **A4 W2-R** (C1): sides frozen at contest start; classify table cases added; W5-T carries no re-stamp listener.

### 15.8 Beyond parity: why Gangland beats every gang plugin

All concrete, all in W0-W6:

1. **Territory that means something.** Admin-drawn turfs you can take only while the owners are online, after a wake-up
   shield, with sides frozen at the start; no gang plugin has fair, readable territory fights (P3, C1). Gangs+ has no territory.
2. **The city on one screen.** `/glw map` chat grid with hover and click, HUD line, turf placeholders, dynmap layer, a text
   form for Bedrock (P2, P5.4).
3. **Guards that fight like a crew** and count on the bar (D4), with a Quartermaster you can trust (P3.4, P3.6, P5). Gangs+ has
   no NPCs at all.
4. **Told anywhere, caught up on login.** Attack alerts with head-count and direction, outcome lines, the digest (P4.1, P4.3).
5. **Progress you earn, then pay for.** Held-turf XP unlocks levels, the bank completes them (D16 C): money alone cannot buy
   territory, unlike a buy-only ladder.
6. **Fights done right (GP-41, never shipped by Gangs+).** 3D arenas with Y bounds, N spawns per side and an Exit, hot reload,
   hard escrow with rollback, ranked vs sparring with anti-farm rules, rematch cooldowns, a namespace-proof command filter,
   and no wanted level, drops or downed state from a fight death (P8).
7. **Cops and heat that know about gangs.** Fights never raise wanted; defenders can be exempted inside their attacked turf
   (D10, W4-H).
8. **Profiles and purge (GP-39, GP-40, announced and never shipped by Gangs+).** A player profile menu, a gang stats page, one
   ranking snapshot shared by chat, menu and hologram placeholders, a safe default-off purge with a preview (P7.7, P7.1).
9. **One friendly-fire rule** across fists, bows, potions and Bartizan guns, with per-gang, negotiated-ally, per-world and
   region control, and guards that stay safe from their own side (15.5).
10. **Safehouses that cannot break a fight.** Warm-up, cost, cooldown, safe-spot check, no invulnerability, no escape from or
    reinforcement into a turf under attack (P7.6).
11. **A "turfs held" leaderboard** and turf placeholders no gang plugin can offer (W5-T, W1-H).
12. **Install only what you run.** Gang, mail, turf and fights are separate runtime modules (`/glw module install`).

### 15.9 Tests, commands.json, migration

**Tests** are named per lane in 7.10-7.11 (JUnit 5 + Mockito; red first against pre-fix code; the two extractions
`GangDisbandService`/`GangLeadership` ship characterization tests green before and after; DB tests use
`@TempDir(cleanup = CleanupMode.NEVER)` + release in `@AfterEach`; no pom edits except W6-K and W6-W). Red-first flips: 9.2.

**commands.json:** every new verb gets an entry in the jar that owns it (gang `commands.json`: safehouse, rally, profile,
ranking, upgrade, pvp, ally pvp, notice, chat allies, the `option gang ...` staff verbs; the fights module's own
`commands.json`: `gang fight ...`, `arena ...`). `GangCommandsJsonParityTest` (NEW in W0-C) builds `GangCommand` **with** its
contributions and is extended by W5-K to the `option gang` tree; `FightCommandsJsonParityTest` is new in W6-K. **The kickoffs
write the final entry per verb** (argument shapes are all in 15.1), so eleven lanes never append to one JSON tail; a lane
edits an entry only when it changes an argument shape and says so; the E2 lanes proofread descriptions. Lanes merge one at a
time with a green reactor build between, so the parity test must be green inside every lane.

**Migration notes** (for `documentation/migration-0.14.0.md`, W5-E2 / W6-E2):
- `inventory/gang_info.yml`, `gang_stat.yml`, `alliance_stat.yml` are created only when missing (`GameplayConfig` L185 registers
  them through `FileHandler`), so W1-G/W5-S edits reach an existing server only when the owner deletes or merges the old
  files; `player_profile.yml` and `gang_ranking.yml` are new and copy cleanly.
- New columns (diff engine, defaults keep today's behaviour): `gang.notice` (null), `gang.friendly_fire` (false),
  `gang_ally.friendly_fire` (false), `member.kills|deaths|assists` (0; gang stats start counting from the upgrade; `User`
  kills and deaths untouched). New tables: `gang_safehouse`, `fight_stats`, `fight_player_stats`, `fight_record`; arena data
  in `fight/arenas.yml` per server.
- Behaviour changes: a player's own arrow now damages them (`Friendly_Fire.Allow_Self_Damage: false` restores the block);
  Bartizan weapon impacts follow the gang friendly-fire toggle; `Gang.Enable: false` now really turns gangs off; same-gang and
  allied kills never count toward gang stats; sides are frozen at contest start ("recruited or allied during a fight, you
  count after it ends").
- Level fees are a money sink against the D9-B faucet (36,000 per active turf per day). Levels now need held-turf XP **and** a bank fee (`gang upgrade`); `Levels.Level_<n>.Cost: 0` restores automatic levelling;
  level-0 caps apply to existing gangs grow-only (an over-cap gang keeps its members but cannot recruit or ally until it
  levels); `Ranks.Minimum` grants verbs by rank (entries naming a missing rank are ignored with a warning); new nodes listed.
- Existing `WaypointType.GANG` waypoints stay admin waypoints; they are not converted to safehouses, but the turf veto and
  the combat tag apply to them too.
- New module `gangland-gang-fights` (optional jar, `Depends: [gang]`); `/glw module install gang|lootchest|fights` now works.
- Short labels per D22 (collision note); WorldGuard optional, flag `glw-gang-pvp` (exists only after a full restart with WorldGuard loaded first); auto-purge off (D24); public chat
  tags off.
- `gangland-api`: additive only, one `VERSION` bump for the branch (D23); gang, civilians, turf, fights (and cops after W4)
  carry that `Host_Api`, so every jar updates together (`HostApiConsistencyTest` pins it; `documentation/gangland-api.md`
  documents the batch, W5-E1).
- `CLAUDE.md` (orchestrator, main checkout, after the W6 merge): the fights module row, the module count, the `Host_Api`
  lines (its `1.0` text is already stale: `GanglandApi.VERSION` is `2.0` today).
- `documentation/migration-0.14.0.md` is edited by one E2 lane per window (W3-E2, W5-E2, W4-E, W6-E2), never two at once.
