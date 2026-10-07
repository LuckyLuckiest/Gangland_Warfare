# Live deploy 2026-10-05: Gangland 0.15.0 to the Test Server

Result: DEPLOYED_BOOT_OK. Server left STOPPED.

## Pre-checks
- No Minecraft java process (only IntelliJ's Maven server, pid 14924); last log write 22:03:59 (shutdown). Proceeded.
- Worktree E:/Programming/java/wt/gangland-0.15.0 HEAD 9d4776ad6f6c7fe770bec5636bb29fa40f7ac791 (committed 20:16 +04); jars built 20:19-20:20, newer than the commit.
- Keystone master b8184831, Bartizan master 6c8fca9c.

## Before (plugins/, sha256)
- gangland_warfare-0.13.0.jar 77A2CA35...111524C
- Keystone-1.14.0.jar (2085343 B) BA332569...C5C0B
- Bartizan-0.6.1.jar (817298 B) 6DEA64A4...CFF76
- modules/: cops-n-crooks, gangland-civilians, gangland-gadget, gangland-gang, gangland-healthbars, gangland-lootchest, gangland-mail, gangland-npc-shops, gangland-turf, all 0.13.0 (no .stale entries).
- Unchanged (not touched): Citizens, DeluxeMenus, bettergui, NBTAPI, LuckPerms, Oriel, PlaceholderAPI, Plaque, Vault, ViaVersion/ViaBackwards.

## Backup
`E:/Documents/Minecraft/Test Server/_deploy-backups/2026-10-05-cnc-0.15/` : 28 files, all re-verified by sha256 against the live originals (0 mismatches, taken before the swap).
Contents: the 3 plugin jars, plugins/Gangland_Warfare/modules (9 jars), database/gangland.db (630784 B, the only DB file; no wal/shm), settings.yml, settings-old.yml, npc/*.yml (11), turf/*.yml (2).
(My first backup attempt mis-copied the DB into a bogus `e/` folder; it was removed and redone and the final tree was re-verified.)

## After (sha256)
- gangland_warfare-0.15.0.jar 1178546 B 6D7CD189212ADBE634E780FA79A6B3B1080F62C72CF0464F8FB2A8F90A091196
- Keystone-1.14.0.jar 2085377 B 1E796D4E6E44B2FA363589139F5EE44B8B8841F52EBEF61DFE07C55785327A18 (replaced: server copy differed from the master build keystone-plugin/target)
- Bartizan-0.6.1.jar 817433 B 237EF0011FF4ABD5CCB35B76D1E280B2E7102D65D9FD1F3D5AF709897978AB84 (replaced: differed from bartizan-plugin/target)
- modules (all 0.15.0): cops-n-crooks A92398B6..., gangland-civilians B2F17FE5..., gangland-gadget FC054E3B..., gangland-gang 1318B4A7..., gangland-healthbars 453E492A..., gangland-lootchest D41E1B83..., gangland-mail E07C9F2B..., gangland-npc-shops 1CEC8A00..., gangland-turf 0E6C9A7C...
- Old 0.13.0 core and module jars deleted; no duplicate versions.

## Commands run (summary)
tasklist-equivalent CIM query; Get-FileHash inventory; Copy-Item backup + hash verification; Remove-Item gangland_warfare-0.13.0.jar and modules/*0.13.0*; Copy-Item of the 0.15.0 jars, Keystone and Bartizan; then a PowerShell boot script (scratchpad boot.ps1) running `java -Xms2G -Xmx4G -XX:+EnableDynamicAgentLoading -Ddisable.watchdog=true -Dgangland.logger.debug=false -XX:+UseG1GC -jar paper-1.21.11.jar nogui` (the .bat minus the jdwp agent and `pause`), waiting for "Done (", sending console input, `stop`. No --restore run.

Boot attempt 1 was a script bug: it matched "Done (" in the stale previous latest.log, sent `stop` and then force-killed the JVM after 120 s (exit -1). That was an unclean stop of a server running the new jars; the DB file size was unchanged afterwards and attempt 2 started cleanly. Attempt 2 is the evidence below.

## Boot evidence (logs/latest.log, attempt 2)
```
[22:38:26] [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.15.0
[22:38:30] [Keystone] Enabling Keystone v1.14.0
[22:38:31] [Bartizan] Enabling Bartizan v0.6.1
[22:38:33] [Gangland_Warfare] Enabling Gangland_Warfare v0.15.0
[22:38:33] Loaded module civilians|gadget|gang|healthbars|lootchest|npcshops|mail|turf|copsncrooks 0.15.0 (9 lines)
[22:38:33] [Gangland.GanglandContext] Runtime modules: 9 loaded, 0 fault(s)
[22:38:34] [Gangland.GanglandContext] Item vocabularies installed: [bartizan]
[22:38:35] all 9 modules "enabled" (Cops-n-crooks module 0.15.0 enabled, etc.)
[22:38:35] Linked NBTAPI, Citizens, PlaceholderAPI (gangland [0.15.0]), ViaVersion
[22:38:35] [Gangland.Gangland] Vault is present but no economy provider is registered; Gangland keeps its internal balances
[22:38:35] Done (23.348s)!
[22:38:46] clean shutdown: all modules disabled, PeriodicalUpdates save, worlds saved
```
- WARN/ERROR lines mentioning our plugins: none. Existing noise is from other plugins only: Oriel (duplicate imported menu ids, unknown template, Vault no economy), DeluxeMenus (NMS hook, Vault, deprecated data), ViaVersion snapshot, Paper outdated-version notice. No stack traces from org.luckyraven, Gangland, Keystone, Bartizan or Citizens.
- npc/wanted.yml and npc/wanted_messages.yml were first-created at 22:36:38 (during boot attempt 1; the log of that run was overwritten, so the creation line is not in the final log). Both exist in plugins/Gangland_Warfare/npc.
- `glw module list` did NOT run: the piped stdin carried a UTF-8 BOM so the console read "\ufeffglw module list" ("Unknown or incomplete command"). Module state is instead evidenced by the "9 loaded, 0 fault(s)" line and nine enable lines.

## Final state
Server STOPPED (log ends at shutdown, no Minecraft java process). 0.15.0 deployment left in place. Rollback source: the backup folder above.
