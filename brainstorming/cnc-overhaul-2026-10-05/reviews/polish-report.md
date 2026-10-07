# Polish lane report (branch cnc-0.15-polish, worktree wt/cnc015-polish, from 68f6d107)

| # | Item | Commit | Test | Outcome |
|---|------|--------|------|---------|
| 1 | Siren/compass wording (wanted.yml, migration, wanted-bounty.md) | 70dac4b5 | docs | done; HUD never shows cop awareness |
| 2 | Escape compass points further out once outside zone (StarCard.exitPoint) | 8254d29f | StarCardTest.exitPoint_outsideTheZonePointsAwayFromTheCentre (red: expected 200 was 140) | done |
| 3 | Fight map: PvP hits only (damager must be a User), prune >30 s on each hit, forget on quit | ea072adb | EntityDamageListenerTest x2 (red = no trackedFights seam pre-fix) | done |
| 4 | Regroup grants own backup past the backup cooldown, waits if active; silent if backup off | 44cebaff | CopGroupSquadTest x2 (red = no grantRegroupBackup pre-fix) | done |
| 5 | Docs: bounty clear (own post only), regroup arrival, changelog crimes/evasion/table `user`, README index | 70dac4b5 | docs | done; other two migration lines verified correct against code |
| 6 | Streak_Bonus comment (it lives in wanted.yml, not cops.yml) + Shot_Noise ids comment | 70dac4b5 | verified HeatModuleConfig window=0 when combo off | done |
| 7 | WantedEvasionStateEvent.getZoneCentre returns a clone | fc80be1d | WantedEvasionStateEventTest (red: same instance) | done |
| 8 | BountySetCommand refunds poster on cancelled UserBountyEvent | e7ea9068 | BountySetCommandTest.set_eventCancelled_refundsThePoster (red: balance 0 not 100) | done (refund-on-cancel chosen over reorder) |

Tests (mvn -pl gangland-api,gangland-impl,gangland-features/cops-n-crooks -amd -am test): BUILD SUCCESS.
api 96, impl 355, cops-n-crooks 514 tests, 0 failures (surefire xml sums).

## Fix round (34b3bd7a, from 70dac4b5)

| # | Finding | Fix | Test (red against the pre-fix code) |
|---|---------|-----|------|
| 1 | Item 4 had no test on the regroupCheck wiring | Two CopRadioTest cases on the twoDown fixture | `regroup_duringBackupCooldown_grantsBackup_andRadiosRegroup` (red: backupExtra 0 not 1) and `regroup_backupOff_regroupsSilently` (red: Regroup line sent), both with CopRadio:159-162 removed |
| 2 | Four docs still said "push once backup is close" | features/cops-n-crooks.md (prose + YAML sample), developer/configuration.md, tests/features/cops-n-crooks.md use the migration wording; cops.yml radio-cooldown comment too | docs |
| 3 | Regroup_Push "Backup's here!" with backup off | CopManager says Regroup_Push only when `Backup.Enabled` and `Extra_Cops > 0`; with backup off the push is silent, like the fall-back (settings check, not `backupExtra`, so a backup shorter than the fall-back still gets its line) | `CopManagerSquadTest.regroup_backupOff_pushesWithoutRegroupPush` (red: Regroup_Push sent) |
| 4 | Shot_Noise comment said exact match | cops.yml: enum names, matched case-insensitively | verified YamlCopConfigProvider:921 + ShotNoiseSettings.radiusFor |
| 5 | Silent refund on a cancelled bounty | BountySetCommand sends DEPOSIT_MONEY_PLAYER with the refunded amount | `BountySetCommandTest.set_eventCancelled_refundsThePoster` now also verifies the notice (red: no message) |
| 6 | Migration: "whoever the killer is" | "(except a gangmate, an ally or the victim's own arrow)"; backup-off silence added to the regroup bullet | docs |
| 7 | Streak_Bonus condition missing | configuration.md and wanted-bounty.md: only while `Kill_Combo.Enable` is true | docs |
| 8 | Docket rows | triage/wanted-bounty-combat.txt: WB-47 (N1) now fixed on cnc-0.15-polish with its test; new WB-53 for the fight-map leak (ea072adb); `parse_obs.py && build_docket.py` rebuilt locally | Republish of the docket page and the `bugs` db rows (WB-47, WB-53 = fixed) left for the merge, as the finding asks |

Skipped: neutral push text for the backup-off case. The fall-back is silent there, so the push is silent too.
Add a `Regroup_Push_Solo` line if players need the cue.

Tests (`mvn -q -pl gangland-impl,gangland-features/cops-n-crooks -am -amd test`): exit 0.
api 96, impl 355, cops-n-crooks 517 tests, 0 failures (surefire xml sums).
