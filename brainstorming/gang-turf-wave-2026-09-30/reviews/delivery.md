# Delivery and orchestration review - gang/turf roadmap (PLAN.md + roadmap.json)

Reviewer: delivery lens, adversarial. Date 2026-09-30. Read: `PLAN.md` (all), `roadmap.json` (all lanes), census docket, H13 branches, Keystone branches, module sources named below.

Verdict: **PASS_WITH_FIXES**. The backbone is right (W0-W3 start from master now, H13 gates only W4, no api change before W4) and I confirmed the load-bearing claims. But the "one owner per file per wave" promise is broken in several places, three W1 lanes are not actually independent, some named test gates do not exist, and the orchestration assumes a checkout layout that git worktrees do not provide. All are plan edits; none needs a redesign.

## 1. What I verified as true

- H13 (`npc-roles-medics`, `h13-roles`, `h13-names`, `h13-stuck`, `h13-drop`) touches nothing under `gangland-turf`, `gangland-gang`, `gangland-mail`. Civilians: only `BartizanNpcWeapons.java` (+2 tests). Api: only `GanglandMoneyDropClassifier` (+test); `GanglandApi.java` is not in the diff, so `VERSION` is still `2.0`.
- Master `pom.xml`: revision 0.12.0, `keystone.version` 1.13.0. H13 branches pin: `npc-roles-medics` 1.13.0, `h13-names` 1.13.0, `h13-drop` 1.13.0, `h13-roles` 1.14.0, `h13-stuck` 1.14.0. Every H13 branch also edits root `pom.xml` (revision 0.13.0); `h13-names` also edits `gangland-build/pom.xml` and `gangland-features/pom.xml` (new `gangland-healthbars` module).
- Keystone 1.14.0 is installed in `~/.m2` but exists in source only on the **unmerged Keystone branch `phase-h13-npc-roles`** (9 commits ahead of Keystone master, whose revision is still 1.13.0). `NpcFanPlacement`, `setLeaderPriority`, `setRangedBand`, `millisUnreachable` are absent on Keystone master and present on that branch. `ActionBarManager.sendBackground` and `NpcSquadSignal.LEADER_DOWN` exist on master, so W1-H is safe on 1.13.0.
- `Gang.isAlly` symmetric fix flows to `CaptureService.isOwnerAlly` (`other.isAlly(ownerGang)`), so TF-24 needs no `CaptureService` edit.
- `GangPresenceTracker.heartbeat()` stamps `lastMemberOnlineAt` for every online gang member, so `OWNERS_ONLINE` (`now - lastMemberOnlineAt <= grace`) is sound.
- Sample map frame: all 7 rows are 41 glyphs, `+` at column 21 of row 4. Nit: the prose says the Kings block is "columns 27-29 of rows 3-4"; the frame shows it 9 wide (columns 25-33) over rows 2-5. Fix the prose before the golden test is written from it.
- Test classes named for flipping all exist: `CaptureServiceStartAndCompleteTest`, `TurfManagerTest`, `RankManagerTest`, `GangAllianceTest`, `CaptureServiceOwnedTurfTest`, `CaptureServiceHelpersTest`, `ActiveBuffManagerTest`, `GarrisonManagerTest`, `RankPermissionApplierTest`, `TurfDeleteCommandTest`, `TurfRepositoryTest`, `TurfModuleConfigTest`, `MailManagerTest`, `GangMailCommandGuardTest`, `TurfPowerupManagerTest`, `RankRepositorySpiTest`, `RankCommandsTest`, `MemberManagerTest`, `GangCommandTest`, `TurfDefenderDeployerTest`, `WantedKillTrackersTest`, `KillComboWantedTrackerTest`, `CuboidRegionTest`.

## 2. Blocking findings (fix in PLAN.md / roadmap.json before any lane prompt is issued)

### B1. W0-A and W0-B edit the same files in the same wave
- `GT/task/TurfIncomeDistributor`: W0-A (release branch, TF-10) and W0-B (start/stop lifecycle, TF-07). PLAN 7.1 lists it as a cross-wave hot file, but W0-B's Owns cell omits it while W0-A's includes it.
- `GT/task/InactivityReleaseTask`: W0-A owns it; P1.2 gives W0-B `task/*` (lifecycle for every task-owning bean).
- `GT/TurfModuleConfig`: 7.1 says orchestrator-owned, lanes add `@Bean` only. But W0-B's Owns cell lists it as "existing beans", and W0-A must change the `CaptureService` bean to inject `TurfOwnership`. Two lanes editing existing beans is not append-only.
- `TurfCreateCommand:103` (TF-05 id reuse) is W0-B work per the docket location but is not in W0-B's Owns.
- `TurfContributionTickTask` gets lifecycle work in W0-B and is deleted in W2-P (TF-16). Throwaway work.

Fix: (a) give TurfIncomeDistributor and InactivityReleaseTask lifecycle and release wholly to W0-A and drop them from W0-B; (b) branch W0-A from W0-B's merged head (a two-step chain, not parallel), or state that a conflict-resolution step is budgeted; (c) W0-B skips `TurfContributionTickTask` (it dies in W2-P) and its TF-07 exit line stops naming it; (d) add `TurfCreateCommand` (id line only) to W0-B and `TurfCommand` (constructor wiring, see B4) to W0-A.

### B2. W1: three "parallel" lanes cannot compile without W1-X's `CaptureEligibility`
W1-M (hover reason), W1-H (`%gangland_turf_state%`, HUD line) and W1-T (enter titles per relation, `turf list` state) all consume the verdict class W1-X creates, yet merge order is M -> X -> H -> T. M merges before the class exists, so M either does not compile or carries a private stub that X then duplicates.

Fix: move the pure `CaptureEligibility` extraction (verdict + reason + until, identical behaviour) into the W1 kickoff commit as a sonnet task, not haiku. W1-X then owns only its consumers (`TurfCaptureFeedbackListener`, `TurfInfoCommand`, `TurfBossBarListener` text). Gate rule 1 (red first) cannot apply to a behaviour-preserving extraction, so name the tests "characterization tests, green before and after, against the old `isCapturable`".

### B3. TF-27 is assigned to a lane that does not own its files
The docket location for TF-27 is `TurfStatusCommand` + `TurfRuntimeState`. PLAN puts TF-27 in W1-X, whose Owns cell has neither; `TurfStatusCommand` belongs to W1-T (TF-23 status strings) and `TurfRuntimeState` to W1-M.

Fix: reassign TF-27 to W1-M (it owns `TurfRuntimeState` and needs derived COOLDOWN for "Safe for 12m" on the map anyway). The one-line `TurfStatusCommand` change goes to W1-T.

### B4. Sub-command registration is hand-wired in the parent constructor and the plan does not own it
`TurfCommand` (192 lines) constructs every child explicitly (`new TurfWandCommand(getPlugin(), getArgumentTree(), getArgument(), selections)` and about fourteen more, each ctor-injected with services). There is no auto-registration. Consequences:
- W0-A must add `TurfOwnership` to `TurfSetOwnerCommand`/`TurfDeleteCommand`, hence edit `TurfCommand`, which W1-T owns later and no W0 lane lists.
- `MapCommand` children: `map hud` (W1-H, `MapHudCommand`), `map zoom` (W3-Z), `map border` (W3-V), `map item` (W3-I) each need a line in `MapCommand`, owned by W1-M in W1 and by nobody in W3. Four parallel W3 lanes would edit one constructor.
- Same for `GangCommand` (W1-G list/info, W2-P chat/join/recruiting).

Fix: add `MapCommand` (child block), `GangCommand` (child block) and `TurfCommand` (ctor wiring) to the 7.1 shared-append table with an owner and merge order. Better: the W1 kickoff registers **all** child classes as empty stubs (`MapHudCommand`, `MapZoomCommand`, `MapBorderCommand`, `MapItemCommand`) so later lanes fill bodies in files they own and never touch the parent.

### B5. W2 cross-lane dependencies the kickoff does not cover
- XP grant: P4.4 says each payout adds XP, so `TurfIncomeDistributor` (W2-N) must call `GangManager.addExperience` (created by W2-P, gang module). Neither lane's Owns cell contains the call site. Fix: W2-N owns the call; the W2 kickoff adds a no-op `addExperience(gang, long)` so both compile; W2-P fills it.
- "Only in-cap turfs pay" (W2-N) needs `TurfRules.cap(gang)`, owned by W2-R (`GT/capture/**`). Fix: the kickoff includes `TurfRules.cap(...)` as a pure function with its table test.
- W2-P deletes `GT/contribution/**` and `GT/listener/contribution/**`, but `TurfCommand` (W2-N in W2) and `TurfModuleConfig` reference them (grep hits: `TurfCommand`, `TurfContributionSettings`, `TurfContributionTickTask`, `TurfModuleConfig`). Fix: W2-P owns exactly those reference removals in `TurfCommand`/`TurfModuleConfig`; W2-N rebases after.
- W2-P changes `Member.contribution` to money share only (P1 triage row). Existing databases hold mixed values and there is no migration step or doc line. Fix: W2-P states what happens to stored values as an explicit owner-visible decision and the migration doc lists it.

### B6. Gang and mail modules have no module-YAML mechanism, yet two lanes load YAML from them
`gangland-gang` has no `*ModuleFiles`/`*FileConfig` (only `GangConfig`, `GangModule`); `gangland-mail` has only `MailModuleConfig` and `module.yml`, no yml. `TurfModuleFileConfig` is the pattern (KERNEL-phase `FileHandler` with the module classloader), and `TurfModuleFiles` is a positional record, so each added YAML changes its constructor.
- W0-D "Invite_Offline_Expiry_Hours (72) in mail YAML": no mail YAML exists, and `Settings` has no invite key.
- W1-G `gang/gang_rules.yml`: same gap.

Fix: add module-YAML plumbing as an explicit task with an Owns entry and a `*ModuleConfigTest` proving the file is created from the module jar: `MailModuleFileConfig` in W0-D, `GangModuleFileConfig` in the W1 kickoff. Also move `turf_messages.yml` (created by W1-T mid-lane, which changes the `TurfModuleFiles` record) into the W1 kickoff.

### B7. Lane worktrees lack untracked and ignored inputs the lane rules require
`CLAUDE.md` (gitignored, `.gitignore:37`), `graphify-out/` (gitignored) and the `brainstorming/**` wave folders (untracked, `git status` shows `??`) are not present in a `git worktree`. Verified: `E:/Programming/java/wt/gangland-0.13.0/graphify-out` does not exist. Consequences:
- The mandatory "run `graphify query` first" step fails inside `wt/gt14-*`.
- W0-E edits `CLAUDE.md` inside worktree `gt14-w0e-docs` where the file does not exist; the edit is lost or creates a stray copy.
- `build_docket.py`, `smoke.py` with its `smoke/reports/`, and W0-F's report path under `brainstorming/gang-turf-wave-2026-09-30/smoke/` live only in the main checkout.

Fix: the lane prompt template states that graphify is run against the main checkout with an absolute path (read-only orientation on the master snapshot) or `graph.json` is copied into the worktree; every path under `brainstorming/`, `CLAUDE.md` and `smoke.py` is the main-checkout absolute path and is never `git add`ed. `graphify update . --force` runs only in the integration worktree after merges. W0-E's CLAUDE.md edit becomes orchestrator work in the main checkout.

### B8. Docket write path is ambiguous and ownerless
PLAN 9.3 correctly targets the Gangland docket (`bug-docket-2026-09-06/triage/*.txt`, `build_docket.py`, artifact `4102fb1f-20b0-44e9-b1b7-2893a559fa04`), which CLAUDE.md declares the source of truth for Gangland bugs. What does not survive:
- (a) The 10 new triage rows have no ids, so no lane can record "fixed" against them and `roadmap.json` `docket.assigned` cannot list them. Assign ids in 9.3 and reference them in the lane docket columns.
- (b) Gate rule 7.2(4) says lanes write db rows; section 8 says the orchestrator does. Make it orchestrator-only after each wave merge; lanes hand rows in the exit report (parallel lanes must not write a shared artifact db, and `ArtifactData` writes need the artifact URL and approvals a lane does not hold).
- (c) Gangland status rows live in **both** artifacts (the census pulled statuses from `4102fb1f-...` and the cross-project docket `4a903fb6-cbdd-4810-90b8-88a863e9013c` seeded its Gangland rows from it). The plan never says whether a fix is written to one db or both, and a status written to only one drifts. State one rule (write the Gangland docket, the source of truth per CLAUDE.md; note the cross docket needs the same rows re-seeded or accept it lags) and put it in section 8.

## 3. Should-fix (before the relevant wave)

- S1 **Phantom test gate.** "`GangModuleTest` command count unchanged (31)" and "31 -> 33" (W0-C, W1-G, W2-P): `GangModuleTest` has no command-count assertion (no `31` anywhere under `gangland-gang/src/test`). Add a NEW `GangCommandsJsonParityTest` in W0-C (registered sub-commands vs `commands.json`) and gate on that; it also puts a test behind W0-E's "commands.json drift" item.
- S2 **Tests named but nonexistent and not marked NEW:** `TurfCaptureNotifierTest` (W2-N), `RankPermissionAddCommandTest` (W1-R), `GangListCommandTest` (W1-G), `TurfCreateCommandTest` (W1-T). **Unnamed gates:** W1-H ("resolver tests"), W3-Z, W3-V, W3-W, W3-I, W4-C, W1-E. Name them (for example `TurfPlaceholderContributionTest`, `TurfHudNearestTest`, `TerritoryRasterZoomTest`, `TurfVisualizationClipTest`, `DynmapTurfLayerTest`, `MapItemRendererCacheTest`).
- S3 **The test server is a serial resource.** `smoke.py` drives one server (`E:\Documents\Minecraft\Test Server`) and deploys jars into its `plugins/`. W0-F (parallel with W0 lanes), W0-B's exit ("smoke G2 clean, merge before W0-A") and the W0 gate smoke can collide, and other programmes use it too. Add a single server-lock rule to section 8; W0-B's G2 runs at the gate, not as a lane exit. G1/G2/G3 rows do not exist in `scenarios.json` (only S1-S8); W0-E writes G1/G2 in parallel with the lane that needs them, so sequence those rows first or let the gate own them.
- S4 **`smoke.py` is console-only.** It cannot put two gangs inside a turf. Every fight acceptance (W0-F, W1 minute-4 walk, W2 full script, W3 two-wave capture) needs a human with two clients (owner plus alt) on an offline-mode server. Say so, list the owner time each gate needs, and treat those gates as blocked on the owner.
- S5 **Parallel `install` races `~/.m2`.** Lane gate 7.2(2) runs `mvn clean install -DskipTests` in up to six concurrent worktrees, all writing `org/luckyraven/gangland-*/0.14.0` (Windows file locks, and a lane can resolve another lane's jar). Use `mvn -q -o verify -DskipTests` in lanes; `install` only in the integration worktree at merge. Also cap concurrent Maven builds at about 3 (W0 has five sonnet lanes) so the SQLite/Hikari tests do not flake under CPU pressure.
- S6 **Revision bump has no owner.** `<revision>` 0.14.0 in root `pom.xml` belongs to nobody's lane. Put it in the W0 kickoff (orchestrator) after D11 is answered. Same for the `keystone.version` check at W4 start.
- S7 **"H13 merged to master" is undefined and late.** It is five unmerged branches that all edit `pom.xml`, plus the Keystone-side `phase-h13-npc-roles` merge (1.14.0), which the plan never lists as a precondition. Fix: W4 precondition checklist = master revision 0.13.0, `keystone.version` 1.14.0, Keystone master at 1.14.0, `mvn clean package` green on master. Run `git merge --no-commit master` (dry run, then abort) into `gang-turf-territory` at the W1 and W3 gates to see conflicts early. Expect conflicts in `pom.xml` (revision, keystone), `gangland-features/pom.xml`, docs. The risk-table row understates the pin skew (three of five branches pin 1.13.0).
- S8 **W4 lanes are not disjoint.** W4-R (turf `Garrison.Composition`) and W4-D (`TurfDefenderDeployer` tick, `TurfDefenderMoneyDropSource`) both touch `GT/npc/defender/**` and `turf_npcs.yml`. W4-R and W4-H both touch cops `module.yml` (`Host_Api: 2.1`). W4-H depends on the `GanglandApi.VERSION` bump made by W4-R (and `exemptsKill` is an additive change to a core interface the api re-exports), so the "optional" lane hard-depends on the opus lane. `GanglandMoneyDropClassifier` arrives in the api with H13 without a VERSION bump, so turf's W4 money-drop source must also set `Host_Api: 2.1`. Fix: sequence W4-D after W4-R, and add a per-module `Host_Api` table (turf 2.1, cops 2.1 if roles or W4-H ship, civilians 2.1 if it reads `NpcRole`).
- S9 **Model tiers.** Sensible overall (haiku: kickoff text, docs, JSON merge, triage; sonnet: bounded lanes; opus: W2-R, W3-A, W4-R and gate reviews; fable: judge/advisor). Adjustments:
  - W1-K creates a command root, beans, a positional-record change and the `gangland.command.map` permission default: use sonnet for the code, keep haiku for JSON/YAML text.
  - W2-P (gang + turf, persisted `contribution` semantics, level/XP, chat, state enum) is the riskiest sonnet lane: split into W2-P1 (XP, contribution, deletions) and W2-P2 (chat, join, recruiting).
  - The W0 gate diff spans four lanes: hand reviewers per-lane diffs.
  - "Opus peak 3" in W2 is a gate-time figure. `roadmap.json` marks W2-R `parallel:true` and W2-G `parallel:true`, implying three simultaneous; the reviewers start after W2-R merges, so the real peak is 2. Set gate reviewers `parallel:false`. The <=3 opus rule is respected in every wave.
- S10 **W3 could overlap W2.** W3-A -> W3-B is a real serial chain, but Z/V/W/I depend only on W1's map core and own `GT/map/**` files disjoint from W2's lanes. Optional speed-up; if kept in W3, fine.
- S11 **W3-W dynmap has no pom owner.** A marker-sync test "with a fake API" and `DynmapTurfLayer` need `dynmap-api` types (a `provided` dependency and repository in `gangland-turf/pom.xml`) or a pure reflection facade. Decide which, name who edits the pom, and confirm Maven can fetch it. `ReflectionGuard` covers the class scan either way.
- S12 **`*-E` docs lanes cannot finish in parallel.** W0-E writes the migration doc skeleton while lanes hand their behaviour notes in exit reports (symmetric allies change friendly fire, ids never reused, `gang invite decline`, 72 h offline invite expiry, new `turf_sequence` table). Split W0-E into E1 (triage, CLAUDE.md, commands.json drift; parallel) and E2 (migration doc fill; after the four lanes merge); same shape for every wave. Migration doc must also say core and gang jars update together (W1-G rewires core `inventory/*.yml` clicks to new gang-module commands).

## 4. Nits

- `GangLevelUpEvent` and `Gang.getLevel()` already exist in the gang module, so W2-P's "inert listener starts firing" is right.
- W0 "35 closed" should read "35 assigned"; an entry is closed only after the wave gate.
- 9.1 arithmetic is consistent (GR 25, TF 38 distinct).
- W3-A Owns cell path is wrong: `CivilianCombatBehavior` lives at `civilians/npc/state/behavior/CivilianCombatBehavior.java`, not `GC/npc/combat/`. `CivilianDamageListener` is at `civilians/listener/civilian/` (W4-R).
- PLAN 2 says "gangland-api changes: none in W0-W3" but W0-D edits api `Settings.java` (behaviour only, no new member, no VERSION bump); reword to "no new api surface". Every lane's `-am` build recompiles the api anyway.

## 5. Checklist against the review brief

| Question | Result |
|---|---|
| Parallel lanes independent (files, classes, YAML, commands.json, Messages)? | No: B1 (W0-A/B), B2 (W1 CaptureEligibility), B3 (TF-27), B4 (child registration), B5 (W2 XP, cap, contribution), S8 (W4). Messages: none touched (good, all new strings in module YAML). commands.json: append-only is acceptable but git still conflicts textually, so the merge step must be a real 3-way resolve, not a dedup. |
| Dependencies and wave ordering right? | W0-W3 vs H13: yes. W4 gate: needs the Keystone-side merge and a defined master state (S7, S8). W3-A before W3-B: right. |
| Unmerged H13 and Keystone bump | Handled correctly in intent; preconditions incomplete (S7). `keystone.version` 1.13.0 through W3 looks right: spot-checked `sendBackground`, `LEADER_DOWN` (Keystone master), `RadioSides.compass8` (api) and `CivilianNpc.setFactionSquads/getFactionSquads` (civilians `CivilianNpcRegistry`, `CivilianCombatBehavior`) all exist; `NpcSquad` is 1.12.0 per CLAUDE.md, not re-checked. |
| Model tiers sensible, <=3 opus? | Yes, with the S9 adjustments; the rule is respected. |
| Test gates concrete, red-before-green | Mostly. Phantom count test (S1), four nonexistent test classes unmarked and several unnamed gates (S2), red-first impossible for the pure refactor (B2). |
| Smoke steps | G1/G2/G3 rows do not exist yet; harness is console-only and single-server (S3, S4). |
| Migration doc, commands.json, docket per wave | Present as `*-E` lanes; ordering and ownership problems (S12, B8); docket write rule ambiguous (B8). |

## 6. Concrete edits to PLAN.md and roadmap.json

1. 7.1: add rows `MapCommand` (child block), `GangCommand` (child block), `TurfCommand` (ctor wiring), and the positional `TurfModuleFiles`/`GangModuleFiles`/`MailModuleFiles` records, each with owner and merge order.
2. 7.2: replace lane `mvn clean install` with `mvn -q -o verify -DskipTests`; add the worktree rules (graphify, CLAUDE.md, brainstorming paths), the server-lock rule, and orchestrator-only docket writes.
3. 7.4: W0-A owns TurfIncomeDistributor, InactivityReleaseTask and `TurfCommand` ctor; W0-B drops both tasks and skips `TurfContributionTickTask`; add mail YAML plumbing to W0-D; W0-C adds `GangCommandsJsonParityTest`; split W0-E; smoke G2 at the gate.
4. 7.5: kickoff (sonnet) = `CaptureEligibility`, all Map child stubs, `turf_messages.yml`, gang YAML plumbing; TF-27 to W1-M; mark NEW tests.
5. 7.6: kickoff adds the `GangManager.addExperience` stub and `TurfRules.cap`; W2-N owns the XP call; W2-P owns contribution-reference removals; split W2-P; add the contribution migration note.
6. 7.8: W4 precondition checklist; sequence W4-D after W4-R; per-module `Host_Api` table.
7. 9.3: give the new rows ids; state the single docket write rule (orchestrator-only, Gangland docket `4102fb1f-...` first, cross docket `4a903fb6-cbdd-4810-90b8-88a863e9013c` lag or re-seed).
8. `roadmap.json`: gate reviewers `parallel:false`; mirror all of the above.
