# Docket entries for gangs/ranks (GR) and turf (TF)

Source: cross-docket bugs.json + live status from both docket artifacts' `bugs` db (pulled 2026-09-30). Missing row = open.

## GR-01 [P0] status=fixed
- title: memberManager.getMember(uuid) is dereferenced without a null check in about 15 command sites
- fix: Add a MemberManager.require(uuid) helper that sends Messages.NOT_IN_GANG and aborts; null-check every call site (GangInviteAcceptCommand:168 now lives in gangland-mail).
- tests: None
- location: `command/sub/gang/GangCreateCommand.java:88,116`; `GangDeleteCommand.java:87,95,148,155,203,206`; `GangKickCommand.java:146,178`; `GangLeaveCommand.java:85,95,132,139`; `GangPromoteCommand.java:78,117,194,201`; `GangDemoteCommand.java:74,111`; `GangDepositCommand.java:74`; `GangWithdrawCommand.java:74`; `command/sub/gang/invite/GangInviteAcceptCommand.java:168`; `command/sub/debug/ComponentExecutorCommand.java:143,170`
- note: Fixed 2026-09-07, commit f6f73626 (GitHub issue #27, closed). Per-site early-return guards across Create/Delete/Kick/Leave/Promote/Demote/Deposit/Withdraw/ComponentExecutor/Debug. Covered by GangAllyAbandonCommandTest. STILL OPEN: the same unchecked deref survives in the mail module - separate area. RELOCATION (WS5 G1-G3, commit 9e2c378e on wt/gangland-0.10.0, 2026-09-23): gangs/ranks/members beco

## GR-02 [P0] status=fixed
- title: Gang delete payout runs async while the sync path deletes the gang
- fix: Do the payout synchronously (or finish the async work and delete in a sync callback); never call Bukkit/Vault off-thread.
- tests: None
- location: `command/sub/gang/GangDeleteCommand.java:230-285`
- note: Fixed 2026-09-07, commit f6f73626 (GitHub issue #28, closed). GangDeleteCommand's payout block is now runQueries (synchronous) to match the sync path that deletes the gang. NO UNIT TEST, deliberately. ADJACENT BUG, not fixed: both payout loops re-read gang.getEconomy().getAmount() INSIDE the loop, so members after the first are underpaid. RELOCATION (WS5 G1-G3, commit 9e2c378e on wt/gangland-0.10.

## GR-03 [P0] status=fixed
- title: /glw gang withdraw (and deposit, rename, desc, display, color, invite, ally) have no rank gate
- fix: Require a rank permission node via Member.hasPermission for every mutating subcommand.
- tests: FIXED 0.8.3 wave 3, commit 8a731fd5; GangPermissions gate on every mutating subcommand (impl + mail); GangPermissionsTest
- location: `command/sub/gang/GangWithdrawCommand.java:67-110`
- note: Fixed 2026-09-07, commit 8a731fd5 on branch worktree-p0-wave-3. New domain gang/permission/GangPermissions declares a node per mutating subcommand and one allows(member, player, node) gate. Covered by GangPermissionsTest (7 cases). STILL OPEN: GangInviteCancelCommand and GangAllyPendingCancelCommand are not gated. RELOCATION (WS5 G1-G3, commit 9e2c378e on wt/gangland-0.10.0, 2026-09-23): gangs/ran

## GR-04 [P1] status=fixed
- title: Rank parent edits never reach RankManager.ranksParent, so they are lost on restart
- fix: Update ranksParent (add/remove RankParent) alongside the tree mutation and persist it.
- tests: None
- location: `command/sub/rank/parent/RankParentAddCommand.java:84`; `RankParentRemoveCommand.java:84`
- note: test-server wave 1 (2026-09-26): rank parent add not persisted, accepts cycles - gangland fix-testserver-wave-1 @ be037447, tests RankManagerTest.HierarchyEditTest; merged to master @ 333f52e3

## GR-05 [P0] status=fixed
- title: gang_ally primary key on gang_id alone keeps one row per gang
- fix: Make the key composite (gang_id, ally_id) in GangAllianceTable and migrate existing rows.
- tests: None
- location: `database/tables/gang/GangAllianceTable.java:16`
- note: Fixed 2026-09-07, commit f6f73626 (GitHub issue #29, closed). GangAllianceTable now declares a composite (gang_id, ally_id) primary key. Covered by GangAllianceRepositorySpiTest over real SQLite (6 cases). RELOCATION (WS5 G1-G3, commit 9e2c378e on wt/gangland-0.10.0, 2026-09-23): gangs/ranks/members become the gangland-gang runtime module; GangAllianceRepositorySpiTest moved there unchanged. No be

## GR-06 [P0] status=fixed
- title: Ally abandon never deletes the row, so the alliance resurrects on restart
- fix: Delete both direction rows in GangAllyAbandonCommand through GangAllianceRepository.
- tests: None
- location: `command/sub/gang/ally/GangAllyAbandonCommand.java:146-147`
- note: Fixed 2026-09-07, commit f6f73626 (GitHub issue #30, closed). New GangManager.breakAlliance(Gang, Gang) deletes both direction rows through the repository contract. Covered by GangManagerAllianceTest + new fake, plus delete_removesOnlyThatDirection in the SPI test. RELOCATION (WS5 G1-G3, commit 9e2c378e on wt/gangland-0.10.0, 2026-09-23): gangs/ranks/members become the gangland-gang runtime module

## GR-07 [P1] status=fixed
- title: Promotion in a branching hierarchy never sends the options
- fix: Send the built ComponentBuilder (or auto-pick when there is one child).
- tests: None
- location: `command/sub/gang/GangPromoteCommand.java:152-164`
- note: test-server wave 1 (2026-09-26): Promote silently does nothing with 2+ child ranks - gangland fix-testserver-wave-1 @ 822f60da, tests GangPromoteCommandTest.promoteWithTwoChildRanks_sendsTheChoice: fails first; merged to master @ 333f52e3

## GR-08 [P0] status=fixed
- title: /glw option gang rank assigns any rank, including owner, to anyone
- fix: Enforce the tree hierarchy and force_rank permission, or restrict the command to admins.
- tests: FIXED 0.8.3 wave 3, commit 8a731fd5; RankAssignmentPolicy hierarchy check + narrowed completer; RankAssignmentPolicyTest (red-first)
- location: `command/sub/debug/ComponentExecutorCommand.java:170-183`
- note: Fixed 2026-09-07, commit 8a731fd5 on branch worktree-p0-wave-3. New domain gang/rank/RankAssignmentPolicy gives /glw option gang rank the hierarchy check GangPromoteCommand already had. Covered by RankAssignmentPolicyTest (9 cases). RELOCATION (WS5 G1-G3, commit 9e2c378e on wt/gangland-0.10.0, 2026-09-23): gangs/ranks/members become the gangland-gang runtime module; RankAssignmentPolicyTest moved 

## GR-09 [P1] status=fixed
- title: Rank delete leaves stale sets that re-upsert the deleted rank
- fix: Detach the node, prune ranksParent/ranksPermissions, delete the rows, re-rank members and guard head/tail.
- tests: RankManagerTest (comment)
- location: `command/sub/rank/RankDeleteCommand.java:60-95`
- note: test-server wave 1 (2026-09-26): rank delete removes Head rank in use, leaves stale rows - gangland fix-testserver-wave-1 @ be037447, tests RankCommandsTest; merged to master @ 333f52e3

## GR-10 [P2] status=open
- title: Transfer confirm lock never times out
- fix: Use a CountdownTimer with auto-unlock and clear pendingTargets on quit.
- tests: None
- location: `command/sub/gang/GangTransferCommand.java:124`
- note: 

## GR-11 [P1] status=open
- title: Mail expiry sweep runs async (start(true)) and is never cancelled (now MailModuleConfig.java:89-90)
- fix: Use start(false) and stop the timer in the module's onDisabled/onPreClear.
- tests: None
- location: `config/MailConfig.java:59-60`
- note: 

## GR-12 [P2] status=open
- title: RankManager.clear leaves permissions, ranksParent and ranksPermissions populated
- fix: Clear every collection and reset Permission.ID.
- tests: RankManagerTest (pins; flip when fixed)
- location: `gang/rank/RankManager.java:223-227`
- note: 

## GR-13 [P2] status=open
- title: A renamed head rank creates a detached root and orphans the hierarchy
- fix: Fail loudly (log and keep the previous root) when Gang.Rank.Head matches no persisted rank.
- tests: RankManagerTest (pins)
- location: `gang/rank/RankManager.java:97-103`
- note: 

## GR-14 [P1] status=open
- title: Online-player streams NPE on uncached members in ally request/accept/abandon
- fix: Filter with a null-safe helper (memberManager.findMember(uuid).map(...)). Files now in gangland-mail (request/accept) and impl (abandon).
- tests: None
- location: `command/sub/gang/ally/GangAllyRequestCommand.java:149,159`; `GangAllyAcceptCommand.java:179,187`; `GangAllyAbandonCommand.java:129,139`
- note: 

## GR-15 [P2] status=open
- title: GangMembersDamageListener uses getGang without a null check
- fix: Null-check and treat as no gang.
- tests: None
- location: `listener/gang/GangMembersDamageListener.java:46-49,65-68`
- note: 

## GR-16 [P2] status=open
- title: alliance_stat.yml button runs a non-existent "ally info" argument
- fix: Register a gang ally info sub-argument or remove the click (re-checked on 0.8.2: still missing).
- tests: None
- location: `resources/inventory/alliance_stat.yml:21`
- note: 

## GR-17 [P2] status=open
- title: gang_stat.yml has no Slots block
- fix: Same as UI-22.
- tests: None
- location: `resources/inventory/gang_stat.yml`
- note: 

## GR-18 [P3] status=fixed
- title: GangCommand.gangStat is dead code (~140 lines)
- fix: Delete it or wire it to the stat button.
- tests: FIXED 0.10.0 CUT, commit 5d6cbefc: gangStat()/itemToBalance() deleted, zero callers anywhere in the reactor, commands.json, /glw help output, or gang_info.yml (which serves /glw gang info through the YAML-driven dialect instead)
- location: `command/sub/gang/GangCommand.java:196-337`
- note: GangCommand.gangStat()/itemToBalance() deleted, zero callers anywhere in the reactor incl. commands.json, /glw help output, or gang_info.yml (which serves /glw gang info through the YAML-driven dialect instead). Fixed in Gangland 0.10.0 CUT, commit 5d6cbefc (wt/gangland-0.10.0).

## GR-19 [P2] status=open
- title: GangBountyEvent and GangLevelUpEvent are never constructed, so the features are inert
- fix: Fire the events from the bounty/level flows or delete the listeners and events.
- tests: None
- location: `events/gang/GangBountyEvent.java`, `events/gang/GangLevelUpEvent.java`
- note: 

## GR-20 [P2] status=open
- title: SQL NULL becomes the literal "null" string in GangRepository
- fix: Map null columns to null or a fallback explicitly.
- tests: None
- location: `database/repositories/gang/GangRepository.java:38-41`
- note: 

## GR-21 [P1] status=open
- title: New rank ids come from totalRanks()+1 and collide after a delete
- fix: Use max(id)+1 or DB autoincrement.
- tests: None
- location: `database/repositories/rank/RankRepository.java:56`
- note: 

## GR-22 [P2] status=open
- title: RankParent.parentId actually stores the child id and child rows are never deleted
- fix: Rename the fields and delete both sides in doDelete.
- tests: None
- location: `gang/rank/RankParent.java` + `gang/rank/RankManager.java:88-93` + `database/repositories/rank/RankParentRepository.java:54-56,73-74`
- note: 

## GR-23 [P2] status=open
- title: Accepting an invite leaves the recipient's other invites PENDING forever
- fix: Cancel the recipient's other pending invites on accept and give offline invites an expiry.
- tests: None
- location: `command/sub/gang/invite/GangInviteAcceptCommand.java:167-184`
- note: 

## GR-24 [P2] status=open
- title: There is no invite reject command and commands.json has gaps
- fix: Add a gang reject argument to the mail contribution and the missing entries (re-checked on 0.8.2: still missing).
- tests: None
- location: `command/sub/gang/invite/**`, `resources/commands.json`
- note: 

## GR-25 [P3] status=fixed
- title: /glw permissions help entries are listed twice
- fix: Remove the duplicate addAll.
- tests: None
- location: `command/sub/permissions/PermissionsCommand.java:35,37`
- note: test-server wave 1 (2026-09-26): /glw help lists waypoint and permissions commands twice - gangland fix-testserver-wave-1 @ 58282e9b, tests New PermissionsCommandTest; merged to master @ 333f52e3

## GR-26 [P3] status=fixed
- title: /glw rank info prints Permission.toString()
- fix: Append permission.getPermission().
- tests: None
- location: `command/sub/rank/RankInfoCommand.java:55`
- note: test-server wave 1 (2026-09-26): /glw rank info shows raw Permission toString - gangland fix-testserver-wave-1 @ 2627badd, tests RankCommandsTest.info_listsPermissionNodes: fails first; merged to master @ 333f52e3

## GR-27 [P3] status=open
- title: Display_Name_Char "" throws at startup
- fix: Validate and default to "*".
- tests: None
- location: `file/configuration/Settings.java:523`
- note: 

## GR-28 [P2] status=open
- title: getRootRank NPEs when the tree has no root
- fix: Null-guard and fall back to the tail rank with a log line.
- tests: None
- location: `file/configuration/gang/GanglandRankLookup.java:26-28`
- note: 

## GR-29 [P2] status=open
- title: No gang-name validation; duplicate detection is skipped when Name_Duplicates is true
- fix: Validate length, charset and colour codes; always reject exact duplicates.
- tests: None
- location: `command/sub/gang/GangCreateCommand.java:155-161`; `GangRenameCommand.java:69-77`
- note: 

## GR-30 [P3] status=open
- title: deleteGangName.get(user).get() can NPE at timer expiry
- fix: Null-check the entry in the confirm handler.
- tests: None
- location: `command/sub/gang/GangDeleteCommand.java:222`
- note: 

## GR-31 [P2] status=open
- title: One bad MailType/MailStatus row aborts the whole mail load (now gangland-mail MailRepository.java:40-41)
- fix: Guard per row: skip and warn.
- tests: None
- location: `database/repositories/mail/MailRepository.java:41-42`
- note: 

## GR-32 [P2] status=open
- title: Positional hard casts in member hydration; a missing member row is skipped under autosave
- fix: Insert the row immediately and use Number casts.
- tests: None
- location: `gang/member/MemberManager.java:51-82`
- note: 

## GR-33 [P3] status=open
- title: The no-arg Gang() constructor can collide on random ids
- fix: Move the collision check into generateId.
- tests: None
- location: `gang/Gang.java:79-82`
- note: 

## GR-34 [P3] status=open
- title: Colour confirm handler assumes the user still has a gang
- fix: Re-check hasGang() before getGang.
- tests: None
- location: `command/sub/gang/GangColorCommand.java:96-110`
- note: 

## GR-35 [P2] status=open
- title: Gang.equals without hashCode
- fix: Add hashCode on id.
- tests: GangAllianceTest (pins; flip when fixed)
- location: `gang/Gang.java:204-212`
- note: 

## GR-36 [P2] status=open
- title: Console-allowed option command casts to Player
- fix: Same as CM-07.
- tests: None
- location: `command/sub/debug/ComponentExecutorCommand.java:42` (`super(gangland, "option", false)`)
- note: 

## GR-37 [P2] status=open
- title: Free-text permission nodes are persisted and online attachments are not refreshed
- fix: Validate via permissionExists and refresh online members' attachments.
- tests: None
- location: `command/sub/rank/permission/RankPermissionAddCommand.java:79`
- note: 

## TF-01 [P1] status=open
- title: A capture completes into a disbanded gang's id with no event and no cleanup
- fix: Check newOwner != null before setOwnerGangId/persist; on null cancel the contest and fire TurfCaptureFailedEvent.
- tests: CaptureServiceStartAndCompleteTest (pins; flip when fixed)
- location: `CaptureService.java:395-408`
- note: 

## TF-02 [P1] status=open
- title: reinforced_defense and garrison_discount buffs are purchasable but do nothing
- fix: Apply CAPTURE_DEFENSE_BONUS in the CaptureService weighting and GARRISON_DISCOUNT in the garrison price.
- tests: None
- location: `ActiveBuffManager` vs. `CaptureService` / `TurfPowerupGarrisonView`
- note: 

## TF-03 [P1] status=fixed
- title: Turf delete leaves contests, boss bars, child rows and NPCs behind
- fix: In TurfManager.delete cancel the contest, fire the failed event, delete child rows and despawn the Quartermaster.
- tests: None
- location: `TurfManager.java:118-126`
- note: test-server wave 1 (2026-09-26): Deleting a turf leaves garrison/buff/QM rows - gangland fix-testserver-wave-1 @ 0c78c2be, tests GarrisonManagerTest +2; merged to master @ 333f52e3

## TF-04 [P2] status=open
- title: Admin setowner leaves bars, defenders and the NPC in contest state
- fix: Fire TurfCaptureFailedEvent(CANCELLED) from TurfSetOwnerCommand.
- tests: None
- location: `TurfSetOwnerCommand.java:71-87`
- note: 

## TF-05 [P1] status=open
- title: A deleted highest id is reused, so a new turf inherits orphaned rows
- fix: Delete child rows on turf delete (TF-03) and never reuse ids (persist nextId or use autoincrement).
- tests: TurfManagerTest (pins; flip when fixed)
- location: `TurfManager.java:56` + `TurfCreateCommand.java:103`
- note: 

## TF-06 [P2] status=open
- title: TurfLocationTracker.onQuit is never registered, so the cache grows forever
- fix: Annotate with @ListenerHandler (drop the @Bean) or register the listener explicitly.
- tests: None
- location: `TurfLocationTracker.java:31,63-66` + `TurfConfig.java:138`
- note: 

## TF-07 [P2] status=open
- title: No turf bean implements BeanLifecycle and the stop() methods have no callers
- fix: Implement BeanLifecycle on the TurfConfig beans: stop tasks in onPreClear, restart in onInitialize.
- tests: None
- location: `TurfConfig.java` (whole file) + grep for `stop()`
- note: 

## TF-08 [P2] status=open
- title: The boss bar refresh task has no cancel path
- fix: Store the task and cancel it in onShutdown.
- tests: None
- location: `TurfBossBarListener.java:81`
- note: 

## TF-09 [P2] status=open
- title: The inactivity task's initial delay equals its 24h period
- fix: Use a short initial delay.
- tests: None
- location: `GangPresenceTracker.java:52-53`
- note: 

## TF-10 [P2] status=open
- title: Auto-release fires no TurfOwnerChangedEvent
- fix: Fire the event and play the owner-cleared sound.
- tests: None
- location: `InactivityReleaseTask.java:37-46` and `TurfIncomeDistributor.java:65-68`
- note: 

## TF-11 [P2] status=open
- title: "Push CLAIM to zero and steal" is not implemented; the exclude parameter is dead
- fix: Implement the CLAIM<=0 branch (exclude the failed challenger) or delete the dead code and fix the settings.yml comments.
- tests: CaptureServiceHelpersTest (dead call site)
- location: `CaptureService.java:425-443`
- note: 

## TF-12 [P2] status=open
- title: Abandon_Grace_Seconds is ignored on owned turfs
- fix: Cancel the contest after the grace window using lastChallengerSeenAt.
- tests: CaptureServiceOwnedTurfTest (pins; flip when fixed)
- location: `CaptureService.java:221-261`
- note: 

## TF-13 [P3] status=open
- title: Unboxing a @Nullable challenger id
- fix: Null-check before unboxing.
- tests: None
- location: `CaptureService.java:390`
- note: 

## TF-14 [P2] status=open
- title: TurfRepository hard casts may ClassCastException on MySQL
- fix: Use Number casts like the sibling repositories.
- tests: None
- location: `TurfRepository.java:42-49`
- note: 

## TF-15 [P3] status=open
- title: income_amount is stored as Double (BigDecimal loss)
- fix: Store as a string or DECIMAL column.
- tests: None
- location: `TurfTable.java:23` + `TurfRepository.java:58`
- note: 

## TF-16 [P2] status=open
- title: A hardcoded y < 0 || y > 319 filter excludes deep players
- fix: Remove the Y filter; the region is Y-agnostic.
- tests: None
- location: `TurfContributionListener.java:64`
- note: 

## TF-17 [P3] status=open
- title: PER_DEFENDER_COST is hard-coded
- fix: Move it to turf_powerups.yml and apply GARRISON_DISCOUNT.
- tests: None
- location: `TurfPowerupGarrisonView.java:39`
- note: 

## TF-18 [P3] status=open
- title: The buff catalogue shows only 9 entries
- fix: Paginate.
- tests: None
- location: `TurfPowerupBuffCatalogueView.java:31-34,57-63`
- note: 

## TF-19 [P2] status=open
- title: The powerup panel does not re-validate ownership on click
- fix: Re-check the turf owner/ally in attemptBuy.
- tests: None
- location: `TurfPowerupFlowSession` + both `attemptBuy` methods
- note: 

## TF-20 [P2] status=open
- title: The whole garrison is consumed on the first capture start
- fix: Deploy incrementally and refund on an instant cancel; spawn around the fight, not one column.
- tests: GarrisonManagerTest
- location: `GarrisonDeployListener.java:48-57`
- note: 

## TF-21 [P3] status=open
- title: No region size validation; perimeter particles every second
- fix: Validate min/max size and throttle particles.
- tests: None
- location: `TurfVisualization.java:83-93` + `TurfCreateCommand`
- note: 

## TF-22 [P3] status=open
- title: 59 TURF_* keys are missing from message_es
- fix: Covered by CM-15.
- tests: None
- location: `message/message_es.yml`
- note: 

## TF-23 [P3] status=open
- title: Hardcoded English and "$" in the turf views
- fix: Route through TurfMessageContract and Settings.getMoneySymbol().
- tests: None
- location: `TurfPowerupMenuView`, `TurfPowerupGarrisonView`, `TurfPowerupBuffCatalogueView`, `TurfPowerupOpenContractImpl:36-60`, `WandListener:49-52`, `TurfWandCommand:38-39`, `TurfStatusCommand:72-74`
- note: 

## TF-24 [P3] status=open
- title: One-directional ally checks
- fix: Use a symmetric helper (either side).
- tests: GangAllianceTest (addAlly is one-directional)
- location: `CaptureService.java:169-175` and `TurfFriendlyFireListener.java:102-107`
- note: 

## TF-25 [P2] status=open
- title: Disbanding a gang does not release its turfs
- fix: Release turfs (fire owner-changed) in GangManager.remove or a gang-delete listener.
- tests: None
- location: `GangManager.remove` (gangland-domain:49)
- note: 

## TF-26 [X] status=open
- title: A third gang on an owned turf is ignored (intentional per javadoc)
- fix: No change; documented.
- tests: CaptureServiceOwnedTurfTest (documents)
- location: `CaptureService.java:224`
- note: 

## TF-27 [P3] status=open
- title: COOLDOWN is not persisted, so a turf reads IDLE after restart
- fix: Derive COOLDOWN from lastCaptureTimestamp on load.
- tests: None
- location: `TurfStatusCommand.java:56-93` and `TurfRuntimeState`
- note: 

## TF-28 [P3] status=open
- title: Reason.CANCELLED is never fired
- fix: Fire it from delete and setowner (TF-03/04).
- tests: None
- location: `TurfCaptureFailedEvent.Reason.CANCELLED`
- note: 

## TF-29 [P3] status=open
- title: A cross-world corner wipes both corners; ID_TAKEN is never sent
- fix: Reject the corner instead and add a duplicate-name check.
- tests: None
- location: `Selection.java:34-37`
- note: 

## TF-30 [P3] status=open
- title: Two admin permission namespaces
- fix: Unify on gangland.command.turf.*.
- tests: None
- location: `WandSelectionManager.ADMIN_PERMISSION` vs. `SubArgument` permissions
- note: 

## TF-31 [P3] status=open
- title: /glw turf buff activates for free (staff tool)
- fix: Document it; optionally charge.
- tests: None
- location: `TurfBuffCommand.java:93`
- note: 

## TF-32 [P3] status=open
- title: prune deletes one row per expired buff on the main thread
- fix: Batch the deletes.
- tests: None
- location: `ActiveBuffManager.java:126-142`
- note: 

## TF-33 [P3] status=open
- title: remove() loads the whole table
- fix: Delete by id.
- tests: None
- location: `TurfPowerupManager.java:100-104`
- note: 

## TF-34 [P3] status=open
- title: getTurfsInWorld returns the live list
- fix: Return an unmodifiable copy.
- tests: None
- location: `TurfManager.java:79-81,138`
- note: 

## TF-35 [P3] status=open
- title: Linear scan per damage event in findOwningTurfId
- fix: Index defenders by entity UUID.
- tests: None
- location: `TurfDefenderDeployer.findOwningTurfId:115-125`
- note: 

## TF-36 [P3] status=open
- title: turf_npcs.yml is never re-read
- fix: Reload in onInitialize (TF-07).
- tests: None
- location: `TurfNpcsConfigLoader.java:47`
- note: 

## TF-37 [P3] status=open
- title: Income has no cap and no online requirement
- fix: Cap the multiplier product; optional online requirement.
- tests: ActiveBuffManagerTest
- location: `TurfIncomeDistributor.distribute`
- note: 

## TF-38 [P3] status=open
- title: The powerup NPC display name is always null
- fix: Pass the configured name.
- tests: None
- location: `TurfPowerupNpcCommand.java:70`
- note: 

## TF-39 [P3] status=open
- title: There is no /glw turf rename
- fix: Add the command.
- tests: None
- location: `Turf.setDisplayName`
- note: 
