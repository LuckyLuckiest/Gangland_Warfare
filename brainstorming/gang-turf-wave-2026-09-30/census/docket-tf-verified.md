# Open TF docket entries verified against master (ff9d813f, rev 0.12.0)

Verified 2026-09-30 by docket-tf. Graph freshness: graphify-out/graph.json (2026-09-29 23:08) is newer than the last commit (2026-09-29 23:05), so graph orientation was current. Source of the open list: `census/docket-gang-turf.md` (TF-03 is `fixed` there and excluded; 38 open entries: TF-01, TF-02, TF-04..TF-39).

**Module move summary:** the turf code now lives in `gangland-features/gangland-turf` (package `org.luckyraven.gangland.turf.*`, `module.yml` `Depends: [civilians, gang]`). Old `TurfConfig` became `TurfModuleConfig`; `TurfNpcsConfigLoader`/`TurfDefenderDeployer`/`TurfPowerupManager` moved from cops-n-crooks into `turf.npc.*`. `TurfFriendlyFireListener` stayed in cops-n-crooks (`listener/turf/`). `GangManager`/`Gang` moved to `gangland-features/gangland-gang` (0.10.0 WS5, commit 9e2c378e). Paths in the table are relative to `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/` unless a module or prefix is named.

## Summary

- still-open: 38, fixed-unrecorded: 0, obsolete: 0, needs-recheck: 0. No open TF entry was fully fixed by the 0.9.x-0.12.0 work or the module move. The commits that touched turf since the docket (0c78c2be GI-34/TF-03, b7f65191 GI-35, ca6bb93f GI-32, b5b773c4 GI-82) only partly reach TF-05, TF-15, TF-30 and TF-07 (flagged PARTIAL or residual in the evidence column). No docket status was written.
- Prerequisites for a turf redesign (11): TF-01, TF-04, TF-05, TF-07, TF-08, TF-10, TF-14, TF-21, TF-25, TF-28, TF-30.
- The rest are either subsumed by the redesign of capture/garrison/powerups (fix by rewriting, not patching: TF-02, TF-11, TF-12, TF-17, TF-20, TF-26, TF-35, TF-37) or independent hygiene.

## Table

| id | sev | classification | theme | redesign prerequisite | evidence (current code) |
|---|---|---|---|---|---|
| TF-01 | P1 | still-open | turf-capture | YES | capture/CaptureService.java:388-409 - complete() unboxes challenger, sets turf.setOwnerGangId(newOwnerId) and persists at :395-401 BEFORE the `newOwner != null` check at :406, so a disbanded challenger still gets the turf and no TurfCapturedEvent/Failed event fires. Pinned by CaptureServiceStartAndCompleteTest. Ownership/event pipeline must be trustworthy before map/notifications build on it. |
| TF-02 | P1 | still-open | powerups | no | powerups/ActiveBuffManager.effectiveMultiplier (:95) has exactly one caller: task/TurfIncomeDistributor.java:70 (INCOME_MULTIPLIER). No CAPTURE_DEFENSE_BONUS use in capture/CaptureService.java; npc/view/TurfPowerupGarrisonView.java:40 hardcodes PER_DEFENDER_COST so GARRISON_DISCOUNT is dead. turf_powerups.yml:46,52 still sells both. Subsumed by powerup/garrison redesign. |
| TF-04 | P2 | still-open | admin-tools | YES | command/TurfSetOwnerCommand.java:76-95 - resetCaptureState() calls state.reset() silently and fires only TurfOwnerChangedEvent; nothing fires TurfCaptureFailedEvent(CANCELLED), so TurfBossBarListener.onFailed (:171) never clears bars and GarrisonDeployListener.onFailed (:84) never recalls defenders/pacifies the Quartermaster. Fix together with TF-28 (one cancel-contest helper). |
| TF-05 | P1 | still-open | persistence | YES | PARTIAL: orphan-row half fixed by 0c78c2be (command/TurfDeleteCommand.java:63-66 cascades garrison/buff/NPC rows, see ActiveBuffManager.removeAll:122). Id-reuse half still open: manager/TurfManager.java:43-56 reseeds nextId = max(existing id)+1 on every load, so deleting the highest id then restarting reuses it (TurfCreateCommand.java:109 allocateId). Any new per-turf data keyed by id (map tiles, history, cosmetics) inherits it. Pinned by TurfManagerTest. |
| TF-06 | P2 | still-open | other | no | task/TurfLocationTracker.java:31,63-66 implements Listener with @EventHandler onQuit but is a plain @Bean (TurfModuleConfig.java:153-158) with no @ListenerHandler; Keystone only registers @ListenerHandler classes (GanglandContext listenerManager.registerEvents), so onQuit is never wired. playerTurfCache entries of departed players remain (bounded by unique players; capture.tick skips them via Bukkit.getPlayer null). |
| TF-07 | P2 | still-open | reload | YES | TurfModuleConfig.java: TurfLocationTracker, TurfIncomeDistributor, GangPresenceTracker, TurfContributionTickTask, TurfDefenderDeployer, ActiveBuffManager all start BukkitTasks but none implements BeanLifecycle; Keystone BeanFactory only auto-calls initialize() by convention (BeanFactory.java:360/:380 onInitialize/onShutdown are BeanLifecycle-only); grep finds zero callers of their stop()/shutdown(). TurfModule.onDisabled (TurfModule.java:70) only logs. Only TurfPowerupManager and PowerupRegistryLoader implement BeanLifecycle. A redesign adds more tasks; settle the lifecycle pattern first. |
| TF-08 | P2 | still-open | reload | YES | listener/TurfBossBarListener.java:81 - constructor calls runTaskTimer and discards the BukkitTask; class has no shutdown hook. Same fix batch as TF-07. |
| TF-09 | P2 | still-open | other | no | task/GangPresenceTracker.java:22-23,52-53 - RELEASE_TICKS = 24h used as both initial delay and period, so the inactive-gang sweep never runs in the first 24h after boot (and a restart within 24h resets it). |
| TF-10 | P2 | still-open | turf-capture | YES | task/InactivityReleaseTask.java:36-47 and task/TurfIncomeDistributor.java:63-68 - both call turf.setOwnerGangId(null)+persist with no TurfOwnerChangedEvent, so TurfOwnerSoundListener / TurfPresenceBarListener.onOwnerChanged (:82) never react and bars keep the old colour. A map layer needs one owner-change event path. |
| TF-11 | P3 | still-open | turf-capture | no | capture/CaptureService.java:190,329 always call dominantGang(..., null); exclude parameter (:425-443) is dead. The 'push CLAIM to zero and steal' mechanic was replaced by the two-phase ping-pong (Phase 2 rollback -> CLAIM@100, :350-358; commit 8d38f1c4). settings.yml no longer carries the old comment. Residual = dead-code cleanup; the design question is folded into the capture redesign. |
| TF-12 | P2 | still-open | turf-capture | no | capture/CaptureService.java:221-261 tickContestingOwned writes state.setLastChallengerSeenAt (:227) but never reads it; Abandon_Grace_Seconds (settings.yml:673) is only honoured in tickContestingUnclaimed (:275). Pinned by CaptureServiceOwnedTurfTest. Rewritten by capture redesign. |
| TF-13 | P3 | still-open | turf-capture | no | capture/CaptureService.java:390 - `int newOwnerId = state.getChallengerGangId();` unboxes a @Nullable Integer (NPE if state was reset before complete()). |
| TF-14 | P2 | still-open | persistence | YES | database/TurfRepository.java:44-52 - minX/maxX/minZ/maxZ hard-cast `(int)`, income `(double)`, createdAt/lastCapture `(long)`; only id uses Number. Fails on MySQL numeric drivers (INT/BIGINT mismatch). Any schema growth for the redesign hits this loader first. |
| TF-15 | P3 | still-open | persistence | no | database/TurfTable.java:23 income_amount is Attribute<Double>; TurfRepository.sanitizeIncomeAmount (GI-32, ca6bb93f) only guards NaN/Infinity, precision loss unchanged. |
| TF-16 | P2 | still-open | turf-capture | no | listener/contribution/TurfContributionListener.java:64 - `if (y < 0 // y > 319) continue;` still present; players below Y0 (1.18+ worlds go to -64) or above 319 never earn defence/capture contribution. |
| TF-17 | P3 | still-open | garrison-npc | no | npc/view/TurfPowerupGarrisonView.java:40 - PER_DEFENDER_COST = BigDecimal.valueOf(1500) hardcoded (javadoc :28-29 admits it); not in turf_powerups.yml, no discount applied. Subsumed by garrison redesign. |
| TF-18 | P3 | still-open | powerups | no | npc/view/TurfPowerupBuffCatalogueView.java:33-34,57-59 - BUFF_START=9/BUFF_END=18, loop breaks at slot 18, so max 9 buffs shown. Latent: turf_powerups.yml defines only 4 today. |
| TF-19 | P2 | still-open | powerups | no | npc/view/TurfPowerupFlowSession.java holds the Turf/Gang resolved once at open; TurfPowerupGarrisonView.attemptBuy (:95-108) and TurfPowerupBuffCatalogueView.attemptBuy re-check nothing (owner/ally/turf-exists) before withdrawing from the viewer gang bank. |
| TF-20 | P2 | still-open | garrison-npc | no | listener/powerups/GarrisonDeployListener.java:57-77 - on the first owned-turf TurfCaptureStartEvent it consumes the entire stock (garrisons.consume(turfId, stock) :70) and deploys all at one region-centre column (regionCentreSurface); no incremental deploy, no refund on instant cancel (onFailed :84 only recalls), and stock is also consumed when Citizens is absent (TurfDefenderDeployer.deploy returns early). Core of the garrison-NPC redesign (squads/roles from H11-H13). |
| TF-21 | P3 | still-open | visualisation | YES | task/TurfVisualization.java:25-27,44-53 redraws the full perimeter each 20 ticks at 0.5 step with no size cap; command/TurfCreateCommand.java:90-106 has no min/max region-size validation. Region size limits are also needed before a chunk-grid map. |
| TF-22 | P3 | still-open | other | no | gangland-impl/src/main/resources/message/message_es.yml contains zero Turf entries (message_en.yml has Turf blocks at :388-450, :579-614; api Messages enum has 60 TURF_* constants). Bigger than the original '59 keys'. Ships with the CM-15 i18n item. |
| TF-23 | P3 | still-open | other | no | Hardcoded English/'$' remain: npc/config/TurfPowerupOpenContractImpl.java:36-60, npc/view/TurfPowerupGarrisonView.java:79-101, TurfPowerupBuffCatalogueView.java:75-101, listener/WandListener.java:47-51, command/TurfWandCommand.java:44-45, command/TurfStatusCommand.java:50,73-74. |
| TF-24 | P3 | still-open | turf-capture | no | capture/CaptureService.java:169-175 uses other.isAlly(ownerGang); cops-n-crooks listener/turf/TurfFriendlyFireListener.java:96-99 uses membership.gangsAllied, implemented one-directionally at gangland-gang GangMembershipInstaller.java:59 -> Gang.isAlly:102 (checks only this.allies). Alliance rows are stored per direction; a half-broken/legacy row gives asymmetric friendly-fire and capture rules. The friendly-fire listener lives in cops-n-crooks (Depends: turf), not in turf. |
| TF-25 | P2 | still-open | turf-capture | YES | gangland-gang GangManager.remove (:49) only drops the map entry; command/sub/gang/GangDeleteCommand.java:286 fires GangDeleteEvent but no turf listener consumes it (only gangland-mail MailGangDeleteListener does - precedent). Partial mitigation: InactivityReleaseTask.java:36 and TurfIncomeDistributor.java:63 release owner-less turfs at the next sweep (<=24h / next income tick), silently, leaving garrison, buffs and Quartermaster attached, and CaptureService keeps running against a dead owner id. Turf already has Depends: [gang] (module.yml) so a GangDeleteEvent listener is a one-class fix. |
| TF-26 | X | still-open | turf-capture | no | capture/CaptureService.java:224 - third-gang challengers on an owned turf are ignored (intentional; javadoc + CaptureServiceOwnedTurfTest document it). No defect; a design decision to revisit inside the capture redesign (multi-gang contests). |
| TF-27 | P3 | still-open | persistence | no | data/TurfRuntimeState.java (state=IDLE in ctor) + manager/TurfManager.java:139 recreate state IDLE on every load; command/TurfStatusCommand.java:56-93 and TurfInfoCommand.java:48-49 display that state. Gameplay is correct (CaptureService.tickIdle -> isCapturable:112-127 uses lastCaptureTimestamp) - only the status text reads IDLE during a post-restart cooldown. |
| TF-28 | P3 | still-open | turf-capture | YES | events/TurfCaptureFailedEvent.java:40 declares Reason.CANCELLED; no caller anywhere in gangland-features (CaptureService.cancel uses only ABANDONED/DEFENDED). Root of TF-04 and of the TF-03 residual (see new findings). |
| TF-29 | P3 | still-open | admin-tools | no | selection/Selection.java:34-40 - on world mismatch it nulls BOTH corners then stores the new one; command/TurfCreateCommand.java has no duplicate-name check, so the 'Id_Taken' message (message_en.yml:584) is never sent. |
| TF-30 | P3 | still-open | admin-tools | YES | selection/WandSelectionManager.java:16 ADMIN_PERMISSION = "gangland.turf.admin" gates create/delete/setowner/buff/garrison/income/powerupnpc/wand (GI-35 b7f65191) while the SubArgument tree uses gangland.command.turf.*. Two namespaces persist; decide the scheme before adding map/claim admin commands. |
| TF-31 | P3 | still-open | admin-tools | no | command/TurfBuffCommand.java:99 buffs.activate(...) with no charge (staff tool; admin-gated since GI-35). Document-only. |
| TF-32 | P3 | still-open | powerups | no | powerups/ActiveBuffManager.java:140-157 prune() runs each second on the main thread and calls repository.delete(buff) per expired buff; no batching. |
| TF-33 | P3 | still-open | garrison-npc | no | npc/TurfPowerupManager.java:97-106 remove() does repository.loadAll().stream().filter(turfId).findFirst() - a full table read per removal (and never purges the `pending` chunk queue at :41, so a removed Quartermaster can still spawn on chunk load). |
| TF-34 | P3 | still-open | other | no | manager/TurfManager.java:79-81 returns the live turfsByWorld list; register :138 builds it as a plain ArrayList inside a ConcurrentHashMap and the 1 Hz tracker iterates it (findAt) while create/delete mutate it -> latent ConcurrentModificationException. |
| TF-35 | P3 | still-open | garrison-npc | no | npc/defender/TurfDefenderDeployer.java:120-130 findOwningTurfId walks every group/defender per call; caller is cops-n-crooks TurfFriendlyFireListener.resolveTurfId (:109) on every weapon impact. Subsumed by garrison/squad redesign. |
| TF-36 | P3 | still-open | reload | no | npc/config/TurfNpcsConfigLoader.java:36-47 - load() runs only from the constructor; TurfModuleConfig.java:230-233 hands out immutable TurfDefenderConfig/TurfPowerupSettings snapshots. turf_npcs.yml edits need a restart (PowerupRegistryLoader, by contrast, implements BeanLifecycle). |
| TF-37 | P3 | still-open | turf-income | no | task/TurfIncomeDistributor.java:58-75 - payout = incomeAmount x product of all active INCOME_MULTIPLIER buffs, uncapped, paid whether or not any member is online (and silently: no message, no ledger). Income design is part of the redesign. |
| TF-38 | P3 | still-open | garrison-npc | no | command/TurfPowerupNpcCommand.java:76 powerupNpcs.place(turfId, location, null) - configured name never passed; TurfPowerupOpenContractImpl.java:56-58 then falls back to the literal "Quartermaster". |
| TF-39 | P3 | still-open | admin-tools | no | No rename command exists in command/ (18 command classes, none renames); Turf.setDisplayName is unused by any command. |

## Prerequisite clusters (land before or with the redesign)

1. **One ownership/contest event pipeline**: TF-01, TF-04, TF-10, TF-25, TF-28. A single `cancelContest(turf, Reason)` and a single owner-change path (capture, admin setowner, inactivity release, gang disband, delete) that always fires the right event; listeners (boss bars, garrison, sounds, the future map) then need no special cases.
2. **Lifecycle**: TF-07, TF-08 (TF-36 is a nice-to-have). Adopt `BeanLifecycle` for every task-owning bean before adding map/squad tasks.
3. **Persistence shape**: TF-05 (persist nextId or autoincrement), TF-14 (Number casts). Before new columns/tables for map/claim data.
4. **Admin surface**: TF-21 (region-size limits), TF-30 (one permission namespace). Before new admin/map commands.

## New findings noticed while verifying (NOT in the docket; candidates for `triage/<slug>.txt`)

- **TF-03 residual (P2)**: `command/TurfDeleteCommand.java:63-67` removes the turf, garrison, buffs and Quartermaster rows but never cancels an in-flight contest. `TurfManager.delete` drops the runtime state without firing `TurfCaptureFailedEvent`, so `TurfBossBarListener.refreshProgress` (`turf == null -> continue`) leaves the boss bars on screen until players relog, and defenders/engaged Quartermaster are not recalled. The original TF-03 fix text asked for this; 0c78c2be covered only the child rows.
- **Turf beans initialised twice (P3)**: `TurfModuleConfig.garrisonManager` (:90-94) and `turfManager` (:112-116) call `initialize()` explicitly while the BeanFactory LIFECYCLE convention also calls it (same class as GI-82, fixed for ActiveBuffManager in b5b773c4). Harmless today (idempotent reload) but resets runtime state if it ever runs after boot.
- **Quartermaster pending queue orphan (P3)**: `npc/TurfPowerupManager.remove` (:97-106) does not purge `pending` (:41); see TF-33.

## Machine-readable

```json
[
 {
  "id": "TF-01",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": true,
  "evidence": "capture/CaptureService.java:388-409 - complete() unboxes challenger, sets turf.setOwnerGangId(newOwnerId) and persists at :395-401 BEFORE the `newOwner != null` check at :406, so a disbanded challenger still gets the turf and no TurfCapturedEvent/Failed event fires. Pinned by CaptureServiceStartAndCompleteTest. Ownership/event pipeline must be trustworthy before map/notifications build on it."
 },
 {
  "id": "TF-02",
  "classification": "still-open",
  "theme": "powerups",
  "prerequisite": false,
  "evidence": "powerups/ActiveBuffManager.effectiveMultiplier (:95) has exactly one caller: task/TurfIncomeDistributor.java:70 (INCOME_MULTIPLIER). No CAPTURE_DEFENSE_BONUS use in capture/CaptureService.java; npc/view/TurfPowerupGarrisonView.java:40 hardcodes PER_DEFENDER_COST so GARRISON_DISCOUNT is dead. turf_powerups.yml:46,52 still sells both. Subsumed by powerup/garrison redesign."
 },
 {
  "id": "TF-04",
  "classification": "still-open",
  "theme": "admin-tools",
  "prerequisite": true,
  "evidence": "command/TurfSetOwnerCommand.java:76-95 - resetCaptureState() calls state.reset() silently and fires only TurfOwnerChangedEvent; nothing fires TurfCaptureFailedEvent(CANCELLED), so TurfBossBarListener.onFailed (:171) never clears bars and GarrisonDeployListener.onFailed (:84) never recalls defenders/pacifies the Quartermaster. Fix together with TF-28 (one cancel-contest helper)."
 },
 {
  "id": "TF-05",
  "classification": "still-open",
  "theme": "persistence",
  "prerequisite": true,
  "evidence": "PARTIAL: orphan-row half fixed by 0c78c2be (command/TurfDeleteCommand.java:63-66 cascades garrison/buff/NPC rows, see ActiveBuffManager.removeAll:122). Id-reuse half still open: manager/TurfManager.java:43-56 reseeds nextId = max(existing id)+1 on every load, so deleting the highest id then restarting reuses it (TurfCreateCommand.java:109 allocateId). Any new per-turf data keyed by id (map tiles, history, cosmetics) inherits it. Pinned by TurfManagerTest."
 },
 {
  "id": "TF-06",
  "classification": "still-open",
  "theme": "other",
  "prerequisite": false,
  "evidence": "task/TurfLocationTracker.java:31,63-66 implements Listener with @EventHandler onQuit but is a plain @Bean (TurfModuleConfig.java:153-158) with no @ListenerHandler; Keystone only registers @ListenerHandler classes (GanglandContext listenerManager.registerEvents), so onQuit is never wired. playerTurfCache entries of departed players remain (bounded by unique players; capture.tick skips them via Bukkit.getPlayer null)."
 },
 {
  "id": "TF-07",
  "classification": "still-open",
  "theme": "reload",
  "prerequisite": true,
  "evidence": "TurfModuleConfig.java: TurfLocationTracker, TurfIncomeDistributor, GangPresenceTracker, TurfContributionTickTask, TurfDefenderDeployer, ActiveBuffManager all start BukkitTasks but none implements BeanLifecycle; Keystone BeanFactory only auto-calls initialize() by convention (BeanFactory.java:360/:380 onInitialize/onShutdown are BeanLifecycle-only); grep finds zero callers of their stop()/shutdown(). TurfModule.onDisabled (TurfModule.java:70) only logs. Only TurfPowerupManager and PowerupRegistryLoader implement BeanLifecycle. A redesign adds more tasks; settle the lifecycle pattern first."
 },
 {
  "id": "TF-08",
  "classification": "still-open",
  "theme": "reload",
  "prerequisite": true,
  "evidence": "listener/TurfBossBarListener.java:81 - constructor calls runTaskTimer and discards the BukkitTask; class has no shutdown hook. Same fix batch as TF-07."
 },
 {
  "id": "TF-09",
  "classification": "still-open",
  "theme": "other",
  "prerequisite": false,
  "evidence": "task/GangPresenceTracker.java:22-23,52-53 - RELEASE_TICKS = 24h used as both initial delay and period, so the inactive-gang sweep never runs in the first 24h after boot (and a restart within 24h resets it)."
 },
 {
  "id": "TF-10",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": true,
  "evidence": "task/InactivityReleaseTask.java:36-47 and task/TurfIncomeDistributor.java:63-68 - both call turf.setOwnerGangId(null)+persist with no TurfOwnerChangedEvent, so TurfOwnerSoundListener / TurfPresenceBarListener.onOwnerChanged (:82) never react and bars keep the old colour. A map layer needs one owner-change event path."
 },
 {
  "id": "TF-11",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": false,
  "evidence": "capture/CaptureService.java:190,329 always call dominantGang(..., null); exclude parameter (:425-443) is dead. The 'push CLAIM to zero and steal' mechanic was replaced by the two-phase ping-pong (Phase 2 rollback -> CLAIM@100, :350-358; commit 8d38f1c4). settings.yml no longer carries the old comment. Residual = dead-code cleanup; the design question is folded into the capture redesign."
 },
 {
  "id": "TF-12",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": false,
  "evidence": "capture/CaptureService.java:221-261 tickContestingOwned writes state.setLastChallengerSeenAt (:227) but never reads it; Abandon_Grace_Seconds (settings.yml:673) is only honoured in tickContestingUnclaimed (:275). Pinned by CaptureServiceOwnedTurfTest. Rewritten by capture redesign."
 },
 {
  "id": "TF-13",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": false,
  "evidence": "capture/CaptureService.java:390 - `int newOwnerId = state.getChallengerGangId();` unboxes a @Nullable Integer (NPE if state was reset before complete())."
 },
 {
  "id": "TF-14",
  "classification": "still-open",
  "theme": "persistence",
  "prerequisite": true,
  "evidence": "database/TurfRepository.java:44-52 - minX/maxX/minZ/maxZ hard-cast `(int)`, income `(double)`, createdAt/lastCapture `(long)`; only id uses Number. Fails on MySQL numeric drivers (INT/BIGINT mismatch). Any schema growth for the redesign hits this loader first."
 },
 {
  "id": "TF-15",
  "classification": "still-open",
  "theme": "persistence",
  "prerequisite": false,
  "evidence": "database/TurfTable.java:23 income_amount is Attribute<Double>; TurfRepository.sanitizeIncomeAmount (GI-32, ca6bb93f) only guards NaN/Infinity, precision loss unchanged."
 },
 {
  "id": "TF-16",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": false,
  "evidence": "listener/contribution/TurfContributionListener.java:64 - `if (y < 0 || y > 319) continue;` still present; players below Y0 (1.18+ worlds go to -64) or above 319 never earn defence/capture contribution."
 },
 {
  "id": "TF-17",
  "classification": "still-open",
  "theme": "garrison-npc",
  "prerequisite": false,
  "evidence": "npc/view/TurfPowerupGarrisonView.java:40 - PER_DEFENDER_COST = BigDecimal.valueOf(1500) hardcoded (javadoc :28-29 admits it); not in turf_powerups.yml, no discount applied. Subsumed by garrison redesign."
 },
 {
  "id": "TF-18",
  "classification": "still-open",
  "theme": "powerups",
  "prerequisite": false,
  "evidence": "npc/view/TurfPowerupBuffCatalogueView.java:33-34,57-59 - BUFF_START=9/BUFF_END=18, loop breaks at slot 18, so max 9 buffs shown. Latent: turf_powerups.yml defines only 4 today."
 },
 {
  "id": "TF-19",
  "classification": "still-open",
  "theme": "powerups",
  "prerequisite": false,
  "evidence": "npc/view/TurfPowerupFlowSession.java holds the Turf/Gang resolved once at open; TurfPowerupGarrisonView.attemptBuy (:95-108) and TurfPowerupBuffCatalogueView.attemptBuy re-check nothing (owner/ally/turf-exists) before withdrawing from the viewer gang bank."
 },
 {
  "id": "TF-20",
  "classification": "still-open",
  "theme": "garrison-npc",
  "prerequisite": false,
  "evidence": "listener/powerups/GarrisonDeployListener.java:57-77 - on the first owned-turf TurfCaptureStartEvent it consumes the entire stock (garrisons.consume(turfId, stock) :70) and deploys all at one region-centre column (regionCentreSurface); no incremental deploy, no refund on instant cancel (onFailed :84 only recalls), and stock is also consumed when Citizens is absent (TurfDefenderDeployer.deploy returns early). Core of the garrison-NPC redesign (squads/roles from H11-H13)."
 },
 {
  "id": "TF-21",
  "classification": "still-open",
  "theme": "visualisation",
  "prerequisite": true,
  "evidence": "task/TurfVisualization.java:25-27,44-53 redraws the full perimeter each 20 ticks at 0.5 step with no size cap; command/TurfCreateCommand.java:90-106 has no min/max region-size validation. Region size limits are also needed before a chunk-grid map."
 },
 {
  "id": "TF-22",
  "classification": "still-open",
  "theme": "other",
  "prerequisite": false,
  "evidence": "gangland-impl/src/main/resources/message/message_es.yml contains zero Turf entries (message_en.yml has Turf blocks at :388-450, :579-614; api Messages enum has 60 TURF_* constants). Bigger than the original '59 keys'. Ships with the CM-15 i18n item."
 },
 {
  "id": "TF-23",
  "classification": "still-open",
  "theme": "other",
  "prerequisite": false,
  "evidence": "Hardcoded English/'$' remain: npc/config/TurfPowerupOpenContractImpl.java:36-60, npc/view/TurfPowerupGarrisonView.java:79-101, TurfPowerupBuffCatalogueView.java:75-101, listener/WandListener.java:47-51, command/TurfWandCommand.java:44-45, command/TurfStatusCommand.java:50,73-74."
 },
 {
  "id": "TF-24",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": false,
  "evidence": "capture/CaptureService.java:169-175 uses other.isAlly(ownerGang); cops-n-crooks listener/turf/TurfFriendlyFireListener.java:96-99 uses membership.gangsAllied, implemented one-directionally at gangland-gang GangMembershipInstaller.java:59 -> Gang.isAlly:102 (checks only this.allies). Alliance rows are stored per direction; a half-broken/legacy row gives asymmetric friendly-fire and capture rules. The friendly-fire listener lives in cops-n-crooks (Depends: turf), not in turf."
 },
 {
  "id": "TF-25",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": true,
  "evidence": "gangland-gang GangManager.remove (:49) only drops the map entry; command/sub/gang/GangDeleteCommand.java:286 fires GangDeleteEvent but no turf listener consumes it (only gangland-mail MailGangDeleteListener does - precedent). Partial mitigation: InactivityReleaseTask.java:36 and TurfIncomeDistributor.java:63 release owner-less turfs at the next sweep (<=24h / next income tick), silently, leaving garrison, buffs and Quartermaster attached, and CaptureService keeps running against a dead owner id. Turf already has Depends: [gang] (module.yml) so a GangDeleteEvent listener is a one-class fix."
 },
 {
  "id": "TF-26",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": false,
  "evidence": "capture/CaptureService.java:224 - third-gang challengers on an owned turf are ignored (intentional; javadoc + CaptureServiceOwnedTurfTest document it). No defect; a design decision to revisit inside the capture redesign (multi-gang contests)."
 },
 {
  "id": "TF-27",
  "classification": "still-open",
  "theme": "persistence",
  "prerequisite": false,
  "evidence": "data/TurfRuntimeState.java (state=IDLE in ctor) + manager/TurfManager.java:139 recreate state IDLE on every load; command/TurfStatusCommand.java:56-93 and TurfInfoCommand.java:48-49 display that state. Gameplay is correct (CaptureService.tickIdle -> isCapturable:112-127 uses lastCaptureTimestamp) - only the status text reads IDLE during a post-restart cooldown."
 },
 {
  "id": "TF-28",
  "classification": "still-open",
  "theme": "turf-capture",
  "prerequisite": true,
  "evidence": "events/TurfCaptureFailedEvent.java:40 declares Reason.CANCELLED; no caller anywhere in gangland-features (CaptureService.cancel uses only ABANDONED/DEFENDED). Root of TF-04 and of the TF-03 residual (see new findings)."
 },
 {
  "id": "TF-29",
  "classification": "still-open",
  "theme": "admin-tools",
  "prerequisite": false,
  "evidence": "selection/Selection.java:34-40 - on world mismatch it nulls BOTH corners then stores the new one; command/TurfCreateCommand.java has no duplicate-name check, so the 'Id_Taken' message (message_en.yml:584) is never sent."
 },
 {
  "id": "TF-30",
  "classification": "still-open",
  "theme": "admin-tools",
  "prerequisite": true,
  "evidence": "selection/WandSelectionManager.java:16 ADMIN_PERMISSION = \"gangland.turf.admin\" gates create/delete/setowner/buff/garrison/income/powerupnpc/wand (GI-35 b7f65191) while the SubArgument tree uses gangland.command.turf.*. Two namespaces persist; decide the scheme before adding map/claim admin commands."
 },
 {
  "id": "TF-31",
  "classification": "still-open",
  "theme": "admin-tools",
  "prerequisite": false,
  "evidence": "command/TurfBuffCommand.java:99 buffs.activate(...) with no charge (staff tool; admin-gated since GI-35). Document-only."
 },
 {
  "id": "TF-32",
  "classification": "still-open",
  "theme": "powerups",
  "prerequisite": false,
  "evidence": "powerups/ActiveBuffManager.java:140-157 prune() runs each second on the main thread and calls repository.delete(buff) per expired buff; no batching."
 },
 {
  "id": "TF-33",
  "classification": "still-open",
  "theme": "garrison-npc",
  "prerequisite": false,
  "evidence": "npc/TurfPowerupManager.java:97-106 remove() does repository.loadAll().stream().filter(turfId).findFirst() - a full table read per removal (and never purges the `pending` chunk queue at :41, so a removed Quartermaster can still spawn on chunk load)."
 },
 {
  "id": "TF-34",
  "classification": "still-open",
  "theme": "other",
  "prerequisite": false,
  "evidence": "manager/TurfManager.java:79-81 returns the live turfsByWorld list; register :138 builds it as a plain ArrayList inside a ConcurrentHashMap and the 1 Hz tracker iterates it (findAt) while create/delete mutate it -> latent ConcurrentModificationException."
 },
 {
  "id": "TF-35",
  "classification": "still-open",
  "theme": "garrison-npc",
  "prerequisite": false,
  "evidence": "npc/defender/TurfDefenderDeployer.java:120-130 findOwningTurfId walks every group/defender per call; caller is cops-n-crooks TurfFriendlyFireListener.resolveTurfId (:109) on every weapon impact. Subsumed by garrison/squad redesign."
 },
 {
  "id": "TF-36",
  "classification": "still-open",
  "theme": "reload",
  "prerequisite": false,
  "evidence": "npc/config/TurfNpcsConfigLoader.java:36-47 - load() runs only from the constructor; TurfModuleConfig.java:230-233 hands out immutable TurfDefenderConfig/TurfPowerupSettings snapshots. turf_npcs.yml edits need a restart (PowerupRegistryLoader, by contrast, implements BeanLifecycle)."
 },
 {
  "id": "TF-37",
  "classification": "still-open",
  "theme": "turf-income",
  "prerequisite": false,
  "evidence": "task/TurfIncomeDistributor.java:58-75 - payout = incomeAmount x product of all active INCOME_MULTIPLIER buffs, uncapped, paid whether or not any member is online (and silently: no message, no ledger). Income design is part of the redesign."
 },
 {
  "id": "TF-38",
  "classification": "still-open",
  "theme": "garrison-npc",
  "prerequisite": false,
  "evidence": "command/TurfPowerupNpcCommand.java:76 powerupNpcs.place(turfId, location, null) - configured name never passed; TurfPowerupOpenContractImpl.java:56-58 then falls back to the literal \"Quartermaster\"."
 },
 {
  "id": "TF-39",
  "classification": "still-open",
  "theme": "admin-tools",
  "prerequisite": false,
  "evidence": "No rename command exists in command/ (18 command classes, none renames); Turf.setDisplayName is unused by any command."
 }
]
```
