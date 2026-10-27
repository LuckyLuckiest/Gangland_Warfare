# Witness-based wanted and bounty hunters (2026-10-26)

This folder answers the owner's request of 2026-10-26, quoted exactly:

> "the cop killing needs to be smarter too. You need a timer before you get a wanted level for killing a cop it doesn't
> jump quickly, a cop sees you kill another cop or civilian and triggers a wanted level, or a cop was silent for a bit
> too long and thus they will send backup at the last reported checked location. You need to properly have a system for
> increasing the wanted level based on viewers rather than randomly. Sometimes you can kill all the cops and backups
> can't arrive but the wanted level is still there until escaped properly. There would need to be a plan for bounty
> hunters, check cops n crooks overhaul if it has that plan and add it if not available."

It is a read-only design: no source was edited and nothing was committed.

## Files

- **[PLAN.md](PLAN.md): witness-based wanted for Cops N Crooks 0.21.2.**
  - Cop crimes are booked through `CopCrimeReports`. A cop eyewitness has a named, glowing reporter, and the report lands 3 s after the crime. Otherwise a civilian calls 911, which takes 8 s. If nobody saw it, nothing happens.
  - An unseen cop death is a silent unit. Dispatch radios a check at 10 s and declares him missing at 20 s. Backup is then biased to his last reported spot and a scene opens there.
  - Stars scale with the witnesses' role and head count, and no hidden 1.5 s squad bonus applies to gated crimes.
  - Stars never drain because every cop is dead. The hide timer runs at the last-known spot, and only a real escape clears stars.
  - Six bugs (B1-B6) are fixed along the way.
  - The plan ends with 11 owner decisions and a staging in six stages (S1-S6) on branch `0.21.2`.
- **[BOUNTY-HUNTERS.md](BOUNTY-HUNTERS.md): bounty hunters.** The plan **already existed** as two cards in the CnC overhaul roadmap:
  - the NPC posse and posters, W-49, 0.22.0;
  - the player license, W-51, 0.23.0.

  The file keeps both cards and re-grounds them on the 0.21.1 code. It changes the tier amounts so they can actually be reached, settles the payout contradiction in the cards, adds hunter targeting and a shared `LastReported` spot, and ends with 9 owner decisions and the release slots.
- **[docket-rows.txt](docket-rows.txt): 17 proposed docket rows, T-446..T-462**, in the `triage/new-findings.txt` format.
  - T-434..T-445 are reserved by the parallel lanes (no triage file in the three trees uses them yet; the r4 commit cites T-437..T-440). The orchestrator assigned T-446 as the start.
  - The docket itself was not edited. Append these rows to `brainstorming/bug-docket-2026-09-06/triage/new-findings.txt` and run `build_docket.py`.

## Sources

**Code**, read at these commits:
- `E:/Programming/java/wt/gangland-0.21.1` at a0b016f24 (spawn r4);
- `E:/Programming/java/wt/gl-0.21.1-civ` at b8ca509f1 (civ 911 plus its review fixes);
- `E:/Programming/java/wt/keystone-1.15.4`;
- master 0.21.0 (a14bcfbcb) for `gangland-core` and `gangland-impl` bounty code.

**Documents:**
- `brainstorming/cnc-cop-ai-2026-10-26/INVESTIGATION.md` and `CIVILIAN-911-DESIGN.md`;
- `brainstorming/cnc-overhaul-2026-10-05/artifact/overhaul-live-2026-10-08.html` (cards at :2292-2302 and :2494-2507);
- `SPEC-0.15.md` and `v0.16/SPEC-0.16.md`;
- `v0.16/DECISIONS.md` (D20);
- `brainstorming/release-1.0-2026-10-08/apply_owner_scope.py:115-130`.

**Owner rulings honoured:**
- R1: a short subtitle, with the cause in chat.
- R4: tips are last-known only.
- R5: the hand-off lead.
- Civilians witness deterministically, in a 180-degree cone, and loud crimes turn them toward the noise.
- An unseen Kill_Civilian needs a witness.
- No randomness in outcomes.

**Graph check:** `graph.json` (2026-10-26 11:20) is newer than the last commit (2026-10-24), so the graph is fresh. `graphify query` ran before any raw read.

## How this was produced

1. **Opus trace.** Traced the cop-kill and civilian-crime wanted flow end to end (findings B1-B6), the "all cops dead" case and the seams to reuse.
2. **Sonnet plan sweep.** Checked whether a bounty-hunter plan exists, mapped the bounty code and the release slots. It mis-cited the posse as W-50; it is W-49, and W-50 is "Keystone 1.17.0: NPC carrier bodies" (`apply_owner_scope.py:119-123`).
3. **Haiku docket sweep.** Checked existing rows (T-121, T-136, T-181, T-187, T-341, T-345, T-400, T-404, T-408, T-412, T-421, BZ-EV-20, WB-37, UI-21, WB-43, D20) so that no new row repeats one.
4. **Three Opus designers.** Wrote competing designs: realism, fairness and minimal.
5. **Two Opus judges.** Both picked the realism design and listed its fatal flaws.
6. **Opus synthesis.** Built one design per plan, taking the strongest parts of the other two designs.
7. **Sonnet critic.** Checked the drafts against the three worktrees. Its 32 corrections, plus the orchestrator's own checks, were applied in this final pass.

### Critic corrections applied

- **The 0.21.1 base (#2-7).**
  - Both gate bypasses are replaced: the fresh-sighting bypass and b8ca509f1's `copSees`.
  - The plan reuses the existing `liveCops()`.
  - It grows `HeatLedger.commitReported`/`reporting` into a `ReportKind` instead of adding a side channel.
  - Cops and civilians share one loudness rule (`heardShot`/`Loud_Weapon_Types`), so `SightModel.shotWithin` is dropped.
  - Line anchors are re-based on a0b016f24 and the obsolete "uncommitted spawn r3" caveat is removed (#6); the r4 episode-trim block is checked for held slots.
- **Wrong or unsafe claims (#8-17).**
  - `%place%` comes from the crime spot, through `dispatchAt`.
  - The silent path is gated on `Check_In.Enable` and Kill_Cop being in the gate list.
  - A silent cop is removed from every squad.
  - `CopDeathEvent.silent` is added, and the standoff and pursuit report are deferred to discovery.
  - The backup-hold release moves to the commit.
  - The real test class names are used, and the pinned tests are listed.
  - The `WitnessSettings` record change is covered by `Bundled020YamlTest`.
  - A 6-block close-range witness floor is added.
  - An explicit `spendBiasSeed()` is added.
  - The station replacement is held until Officer_Missing, two beat units respond, and `Respawn_Seconds` 30 is noted.
- **Unhandled states (#18-24).**
  - A null killer keeps today's path.
  - A cuff, an arrest or a hunter capture drops the open cases, and restraint is re-checked at landing.
  - One quit policy covers both lanes (defer to the next join).
  - `BeanLifecycle` handles reload and shutdown.
  - A torn-down group, or a cop with no group and no station, sends no unit.
  - Transit, rescue and intake cops are flagged for verification.
  - Without Citizens, crimes commit at once.
- **Request clauses (#25-27).**
  - Ongoing viewer escalation is owner decision 9 (default: per crime).
  - "Only a death starts a silence" is owner decision 10.
  - The random spawn-ring angle is disclosed.
- **Plan B (#28-32).**
  - Hunters get explicit squad, targeting and target-filter rules, because hostile civilians are reactive.
  - `LastReported` is a shared bean fed by `CopCrimeReports`.
  - A hunter capture drops the open cases.
  - Relabelling the cards and striking "dead pays half" is listed as a plan edit.
  - The tiers are rescaled to 1 000/4 000/12 000. The bounty base is `Kill.Each` 5 (`User.java:60`), so the draft's 40 000 tier was unreachable by notoriety.

### Critic corrections rejected or narrowed

- **#1 (CIV lane uncommitted), rejected as stale.** The lane is now committed through b8ca509f1 and its tree is clean. The real prerequisite is to merge that lane **and** a0b016f24, which the CIV lane lacks, into 0.21.1 before cutting 0.21.2.
- **#8, heard-around point, narrowed.** `%place%` moves to the crime spot or `lastReport` for every new line and for `Dispatch_Wanted`. The heard-around point stays at the player: he is the addressee and must hear the line, and who hears a broadcast is not a tip to the cops, so R4 is not at stake.
- **#19, downed player, narrowed.** Cases are dropped on a cuff, an arrest or a capture, but **not** on downed. A downed player always ends in a cuff, a death or a recovery, and each of those already settles the case.
- **#20, quit policy.** The critic offered "cancel for both lanes" as one option. The plan rejects it because it makes logging out a free escape. It also rejects "commit at quit", because that builds a cop group for an offline player. The plan defers the report and commits it at the next join (OD-5, row T-462).
- **#31 is valid.** The cards do still read "Ships in 0.19" and "Ships in 0.20" (ROAD near :2296 and :2498). The artifact is not edited here; the fix is listed as a plan edit.
