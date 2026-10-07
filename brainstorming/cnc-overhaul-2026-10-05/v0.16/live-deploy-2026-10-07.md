# Live deploy 2026-10-07: Gangland 0.16.0 + Keystone 1.15.0 to the Test Server

Result: DEPLOYED_BOOT_OK. Server left STOPPED.

## Pre-checks
- No Minecraft java process (only IntelliJ's Maven server, pid 28584).
- Branch 0.16.0 (209336af) is an ancestor of master (d4e39e69); `git diff --name-only 0.16.0 master` outside brainstorming/ is empty (docket/brainstorming only).
- Gangland built in E:/Programming/java/wt/gangland-0.16.0 (`mvn -q clean package -DskipTests`, 34 s): core jar + 9 module jars, all 0.16.0.
- Keystone master (rev 1.15.0) built with `mvn -q clean install -DskipTests`; plugin jar keystone-plugin/target/Keystone-1.15.0.jar.
- Bartizan: server copy 0.6.1 hash equals bartizan-plugin/target/Bartizan-0.6.1.jar (master 6c8fca9), so NOT replaced.

## Before (sha256)
- gangland_warfare-0.15.0.jar 1179581 B 24EDE976121296BA04C8A686DF7C88264032ACAB9D00DA95A5CBD0B2746F25EC
- Keystone-1.14.0.jar 2085377 B 1E796D4E6E44B2FA363589139F5EE44B8B8841F52EBEF61DFE07C55785327A18
- Bartizan-0.6.1.jar 817433 B 237EF0011FF4ABD5CCB35B76D1E280B2E7102D65D9FD1F3D5AF709897978AB84 (unchanged)
- modules 0.15.0: cops-n-crooks 735D2C5C, civilians 0A431E2D, gadget B8C9AC86, gang C94D6877, healthbars DD574CB9, lootchest 3C1E2683, mail BC0F7D7F, npc-shops DFF421F4, turf 3B3051DF

## After (sha256)
- gangland_warfare-0.16.0.jar 1214832 B 2314E705DFAC42556A6665103A2327945230665CE85FC203A8AB79EB69D230AA
- Keystone-1.15.0.jar 2088671 B 41AD9EA64C14805C587A99F97A8E51C5838F6504DFBC906CC0B818C47EF39A87
- modules 0.16.0: cops-n-crooks 7D14C550, civilians 3EC4BB56, gadget A46C99F4, gang F5D4ED12, healthbars 30114DE4, lootchest E20C8BF1, mail C9128210, npc-shops 7997C38C, turf 5BA00008
- Old 0.15.0 core, Keystone-1.14.0 and all 0.15.0 module jars removed; no duplicates, no .stale entries.

## Backup
`E:/Documents/Minecraft/Test Server/_deploy-backups/2026-10-07-cnc-0.16/plugins/`: 3 jars + the whole plugins/Gangland_Warfare tree (modules, database/gangland.db with no wal/shm, every yml, settings-old files). 56 files, sha256 verified against the live originals before the swap: 0 mismatches.

## Commands
CIM java query; git ancestry checks; mvn builds (PowerShell); Get-FileHash inventory; Copy-Item backup + hash verification; Remove-Item old jars; Copy-Item new jars; boot script (scratchpad boot.ps1/boot2.ps1) running `java -Xms2G -Xmx4G -XX:+EnableDynamicAgentLoading -Ddisable.watchdog=true -Dgangland.logger.debug=false -XX:+UseG1GC -jar paper-1.21.11.jar nogui`, waiting for a fresh "Done (" (log LastWriteTime after start), then console input, then `stop`. No --restore.

Two boots:
- Attempt 1 (log kept as live-deploy-2026-10-07-attempt1.log): clean boot and clean exit (exit 0, no kill). The first stdin line again arrived with a UTF-8 BOM ("Unknown or incomplete command" for `glw module list`) even though raw ASCII/UTF-8-no-BOM was intended; Keystone/Gangland version commands worked.
- Attempt 2 (live-deploy-2026-10-07.log, the evidence): a throwaway `help x` line first absorbs the BOM, then `glw module list`, `version Keystone`, `version Gangland_Warfare`, `stop`. Clean exit 0, not killed. Second boot did not touch settings again.
  Note: the BOM is on the first line written to the pipe regardless of encoder (likely the console reader/JVM), so send a dummy line first.

## Boot evidence
```
Keystone 1.15.0 loaded. Serving as a library for dependent plugins.      (floor guard did not refuse)
Bartizan 0.6.1 enabled; Item vocabulary published: bartizan
Loaded module civilians|gadget|gang|healthbars|lootchest|npcshops|mail|turf|copsncrooks 0.16.0
[Gangland.GanglandContext] Runtime modules: 9 loaded, 0 fault(s)
Schema migration: ADD COLUMN cop_spawner.station_id
Item vocabularies installed: [bartizan]
all 9 modules "... module 0.16.0 enabled"
Linked NBTAPI, Citizens, PlaceholderAPI (gangland [0.16.0]), ViaVersion
Done (31.4s) attempt 1 / Done (24.0s) attempt 2
clean shutdown: all modules disabled, PeriodicalUpdates save
```
Module list output (attempt 2):
```
GLW >> Loaded modules (9):
civilians v0.16.0 (Host_Api 2.2), gadget 2.2, gang 2.2, healthbars 2.0, lootchest 2.0, npcshops 2.2, mail 2.0, turf 2.3, copsncrooks 2.3
Keystone version 1.15.0 ; Gangland_Warfare version 0.16.0
```
Config handling (attempt 1, first boot on new jars):
- settings.yml "is an old build or corrupted, creating a new one": regenerated with Config_Version '0.16.0' (141 keys vs 293; module-owned keys moved out). The 0.15.0 file was renamed to `settings-old (2).yml` (hash identical to the backed-up settings.yml) alongside the older settings-old files.
- Created: turf/turf_settings.yml, copsncrooks/detainment.yml, copsncrooks/setup.yml, gadget/gadget_settings.yml, gang/gang_settings.yml (and inventory/phone_contacts.yml).
- Moved from npc/ to copsncrooks/: cops.yml, cop_roles.yml, cop_radio_messages(.yml, _es.yml), wanted.yml, wanted_messages.yml.
- No region/stations file was created at boot (stations are created by setup, not on boot).

## WARN/ERROR lines mentioning org.luckyraven / Gangland / Keystone / Bartizan / Citizens
None in either boot. Remaining WARN/ERROR are other plugins' existing noise: Oriel (duplicate imported menu ids, unknown template, hdb/itemsadder sources, Vault no economy), DeluxeMenus (NMS hook, Vault, deprecated data, invalid materials), ViaVersion snapshot notice, Paper outdated-version notice. Verdict: no action. Only INFO "Vault is present but no economy provider" from Gangland (same as 0.15.0).

## Final state
Server STOPPED (no Minecraft java process). 0.16.0 + Keystone 1.15.0 left in place. Rollback source: the backup folder above.
