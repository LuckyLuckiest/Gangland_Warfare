# 0.16.1 T-189: turf defenders protect their gang from cops

Branch `cnc-0.16.1-qm` (worktree `E:/Programming/java/wt/cnc161-qm`), base `2a2369f4` (0.16.1 revision bump).
Commits: `8062b8a1` (red tests), `461472ed` (implementation), `69f5ccc7` (reviewer fix: a chase switch releases), plus the lane commit that adds this report and the roadmap wave.
Nothing pushed.

## Owner report
"The turf quartermaster doesn't attack the cops when they are attacking the players." Also asked: improve the Quartermaster and put the plans into the Gangs & Turfs roadmap so NPC interaction works effortlessly.

## Design chosen (Design A)
- Cop recognition goes through `CopManager.isCopNpc` / `findCopByEntity` in cops-n-crooks. The turf module never names a cop type.
- The listener lives in cops-n-crooks (`TurfCopProtectionListener`) and hands hits to `TurfCopGuard` in gangland-turf. Turf depends on civilians only, so the module rule holds.
- Hit sources: `EntityDamageByEntityEvent` (melee, and projectiles with a cop shooter) and `WeaponRaytraceImpactEvent` (Bartizan). The raytrace path mirrors `TurfFriendlyFireListener`.
- Defenders stay idempotent: `CivilianNpc.addEntityTargetToFront` does remove-then-addFirst, so one shot that fires both events is safe.
- Rejected Design B (POLICE mark): it depends on civilians.yml mark defaults and leaves the raytrace gap open.

## Behaviour
- A cop that damages a player standing on a turf owned by that player's gang (or an allied gang when `Include_Allies` is true) becomes the target of the Quartermaster and live garrison defenders within `Cop_Response.Targeting_Radius` of the cop.
- Defenders never start a fight on their own, never target protected players, and do not react to cops chasing non-members.
- Each engagement is held per cop. The tick (every 5 ticks, sync) releases it when the cop dies, leaves the world, the victim goes offline, is downed, leaves the turf, loses protection, or the owner changes.
- A cop that moves out of range of an individual NPC is removed from that NPC only. When an NPC has no entity target left and no player target, it goes back to IDLE.
- A cop whose chase switches to a non-member releases the engagement (reviewer fix `69f5ccc7`, via the `CopTargetLookup` seam). A cop with no player target, or an offline one, keeps its engagement.
- No timeout.
- `TurfCopGuard` is a bean in `TurfModuleConfig`, started at boot; its tick task is cancelled by Bukkit on plugin disable (no `stop()`). It is not a listener.

## Files
Added:
- `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks/listener/turf/TurfCopProtectionListener.java`
- `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/npc/guard/CopGuardConfig.java`
- `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/npc/guard/TurfCopGuard.java`

Changed:
- `gangland-features/gangland-civilians/.../npc/npc/CivilianNpc.java` (`removeEntityTarget`)
- `gangland-features/gangland-turf/.../npc/config/TurfNpcsConfigLoader.java` (new keys, fallbacks)
- `gangland-features/gangland-turf/.../npc/config/TurfPowerupSettings.java` (`targetingRadius`)
- `gangland-features/gangland-turf/.../npc/TurfPowerupManager.java` (radius from config instead of the literal 32.0; `civilianNpcsOf(turfId)`)
- `gangland-features/gangland-turf/.../npc/defender/TurfDefenderDeployer.java` (`liveDefenders(turfId)`)
- `gangland-features/gangland-turf/.../TurfModuleConfig.java` (`TurfCopGuard` bean, started at boot)
- `gangland-features/gangland-turf/src/main/resources/turf/turf_npcs.yml`

Tests added or changed:
- `gangland-features/gangland-turf/src/test/.../npc/guard/TurfCopGuardTest.java` (new)
- `gangland-features/gangland-turf/src/test/.../TurfModuleConfigTest.java` (cop response and radius cases)
- `gangland-features/gangland-turf/src/test/.../npc/TurfPowerupManagerTest.java` (constructor arity only)
- `gangland-features/cops-n-crooks/src/test/.../listener/turf/TurfCopProtectionListenerTest.java` (new)
- `gangland-features/gangland-civilians/src/test/.../npc/npc/CivilianNpcEntityTargetTest.java` (new)

## Config keys (`turf/turf_npcs.yml`, the turf module's own YAML)
```
Powerup_Npc:
   Type_Id: quartermaster
   Targeting_Radius: 32.0
Cop_Response:
   Enabled: true
   Targeting_Radius: 32.0
   Include_Allies: true
```
- `Cop_Response.Enabled`: default true. Set false to turn the defenders off.
- `Cop_Response.Targeting_Radius`: default 32.0, minimum 1.0.
- `Cop_Response.Include_Allies`: default true.
- `Powerup_Npc.Targeting_Radius`: default 32.0, minimum 1.0. Replaces the literal 32.0 that was in `TurfPowerupManager`.

The literal 32.0 remains only as the code fallback in `TurfNpcsConfigLoader` and as the default field value in `TurfPowerupNpc`. Those cover a missing key; they are not the live value.

## Tests and red proof
Red commit `8062b8a1` (compile-red first, then behaviour-red against no-op stubs). The failing reasons recorded at that step:
- `TurfCopGuardTest.ownerMemberOnTurf_inRadiusNpcsTargetCop_outOfRadiusDoNot`: the in-radius NPC never got `addEntityTargetToFront`.
- `TurfCopGuardTest.alliedMember_protectedWhenIncludeAlliesTrue`: same failure, allied member.
- `TurfCopGuardTest.copMovesToSecondTurf_releasedFromFirst`: release and target expectations unmet.
- `TurfCopGuardTest.tick_deadCop_dropsEngagement`: precondition `expected 1 but was 0`.
- `TurfCopGuardTest.tick_victimLeavesTurf_releasesAll`, `tick_copBeyondRadius_removedFromThatNpcOnly`, `tick_idleOnlyWhenNoTargetsLeft`: release, range and IDLE expectations unmet.
- `TurfCopGuardTest.protects_sameGang_true`, `protects_allyByFlag`: `expected true but was false`.
- `TurfCopProtectionListenerTest.melee_copHitsPlayer_callsGuard`, `projectile_copShooter_resolvesToCop`, `raytrace_copShooter_callsGuard`: guard never invoked.
- `CivilianNpcEntityTargetTest.removeEntityTarget_removesOnlyThatEntity_keepsOrder:38`: `expected true but was false`.
- `TurfModuleConfigTest.turfNpcsYaml_copResponseDefaults`, `turfNpcsYaml_overrides`, `turfNpcsYaml_radiiClampToOneBlock`: stub values did not match.

Caveat: the red step was compile-red for the new classes, so the pre-fix production code was not re-run separately. The negative tests (challenger, off-turf, other turf, disabled, non-hostile, non-member, non-cop attacker, NPC victim, absent entity, and the GI-82 test) pass on the stub and act as regression guards.

Green re-run in this pass (`mvn -o -pl <module> -am test`, offline, from the worktree; no `install`):
- `gangland-features/gangland-turf` (civilians built via `-am`): BUILD SUCCESS. Turf module 141 tests, 0 failures, 0 errors. Includes TurfCopGuardTest 21, TurfModuleConfigTest 4, TurfDefenderDeployerTest 2, TurfPowerupManagerTest 1.
- `gangland-features/cops-n-crooks` (turf and civilians built via `-am`): BUILD SUCCESS. 1017 tests, 0 failures, 0 errors. Includes TurfCopProtectionListenerTest 6.

## Verified
- The Quartermaster has combat enabled (`civilians.yml:398`), so the COMBAT gate in the guard does not block it.
- The Quartermaster type is Hostile, so the isHostile gate does not exclude it.

## Not verified yet
- The Bartizan raytrace shooter: whether `getShooter()` is the cop LivingEntity in game. The unit test covers only the code path. Check in the Test Server console smoke.
- Owner playtest of the defender behaviour.

## Docket
- T-189 in the Gangland docket (artifact `4102fb1f`, collection `bugs`): status `fixed`, version 2, recorded by the T-189 lane. The note lists commits `8062b8a1`, `461472ed`, `69f5ccc7`, the covering tests and the counts above, plus the `Targeting_Radius` literal change.
- The local `brainstorming/bug-docket-2026-09-06/bugs.json` was not edited. Its `fix` field still says "Not fixed", and the main checkout has uncommitted changes to that file, so editing it here would conflict on merge.

## Roadmap additions (Gang & Turf roadmap)
- Wave W7 "NPC interaction: Quartermaster and guards that just work", pillar P9 (P9.1 to P9.7), in `brainstorming/gang-turf-wave-2026-09-30/roadmap.json`. `roadmap.html` regenerated with `build_roadmap.py` (8 waves, 29 decisions). `PLAN.md` gained section 16.
- Lanes: W7-A (defenders protect the owning gang from cops, delivered in 0.16.1, T-189), W7-B (Quartermaster hub and placement), W7-C (ownership nameplate, hover and refusal text), W7-D (menu and feedback copy into turf YAML, haiku, parallel), W7-E (owner feedback lines), W7-F (hold/defend orders, opus), W7-H (hire, reserve and top-up in the menu), W7-G (opus gate review and smoke).
- Decisions: D25 (hub access for allies), D26 (which guard orders), D27 (nameplate owner text), D28 (owner alert channel and cooldown), D29 (who may place a Quartermaster). Each has a recommended option.

## Follow-ups and skipped
- Skipped per spec: Memory_Seconds aggressor window; defenders do not pick up a cop that arrives later without a new hit; alertFaction on the defender side. Add when a real case needs them.
- Line endings: the new and edited Java and YAML files are LF while the repo expects CRLF. Normalise in a separate tidy-up commit, not in this one.
- Roadmap JSON is CRLF and was re-serialised to match. The diff is additive only (344 insertions, no existing lines changed).
- Bartizan raytrace shooter check in game (see Not verified yet).
