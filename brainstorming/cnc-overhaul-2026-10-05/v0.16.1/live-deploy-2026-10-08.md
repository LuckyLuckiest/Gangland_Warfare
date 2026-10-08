# Live deploy 2026-10-08: Gangland 0.16.1 + Keystone 1.15.1 to the Test Server

Result: DEPLOYED_BOOT_OK. Server left STOPPED.

## Source
- Gangland master b6d2ec0e (merge of branch 0.16.1, pushed). Deploy jars built from master with `mvn -q -o clean install -DskipTests`.
  The full `mvn -o clean install` on 0.16.1 (9e17fb51) had 2644 tests and 0 failures. The only later code change, a default
  for the Spotted crime name (4368d35a), was tested in cops-n-crooks: 1105 tests, 0 failures.
- Keystone master 7a1dbb2 (1.15.1, pushed). `mvn -o clean install` passed with 1798 tests.
- Bartizan 0.6.1 was unchanged and not replaced.

## Pre-check
The only java process running was IntelliJ's Maven server (pid 25180). No Minecraft server was running.

## Backup
`E:/Documents/Minecraft/Test Server/_deploy-backups/2026-10-08-cnc-0.16.1/plugins/` holds Keystone-1.15.0.jar,
gangland_warfare-0.16.0.jar, Bartizan-0.6.1.jar and the whole plugins/Gangland_Warfare tree: 63 files, every one
sha256-checked against the live original, 0 mismatches.

## Swap (sha256 prefix)
- Keystone-1.15.0.jar -> Keystone-1.15.1.jar 210933F00152DA18
- gangland_warfare-0.16.0.jar -> gangland_warfare-0.16.1.jar F7EFBFE9E8A07D17
- modules, every 0.16.0 jar removed:
  - cops-n-crooks 227E2F7F
  - civilians 3164132C
  - gadget AEDAB49A
  - gang 95737735
  - healthbars 79B5E3F2
  - lootchest 9AFE6B63
  - mail 5E2746CB
  - npc-shops DDB291EE
  - turf EFADF56F

## Boot (scratchpad boot2.ps1)
- `Keystone 1.15.1 loaded`. The floor guard passed.
- All nine modules loaded at 0.16.1: `Runtime modules: 9 loaded, 0 fault(s)`.
- `Done (42.807s)`.
- `glw module list` showed the nine modules at 0.16.1. Turf and copsncrooks report Host_Api 2.3, gang, gadget,
  civilians and npcshops 2.2, and healthbars, lootchest and mail 2.0.
- `version Keystone` returned 1.15.1, and `version Gangland_Warfare` returned 0.16.1.
- The server stopped cleanly (exit 0, not killed).
- There were no WARN or ERROR lines from Gangland, Keystone, Bartizan, Citizens, turf or copsncrooks. The only noise
  came from other plugins: the DeluxeMenus NMS hook, the Paper outdated notice and the ViaVersion snapshot notice.
- Full log: live-deploy-2026-10-08.log. It is gitignored and stays local.

## Config handling
- Two new files were created: copsncrooks/commands.yml and copsncrooks/commands_es.yml.
- settings.yml was regenerated for Config_Version 0.16.1. The old file is now `settings-old (3).yml`. Comparing the two
  with comments ignored, only Config_Version differs, so no owner value was lost.
- No other YAML was rewritten. New keys use their code defaults:
  - Post_Escape on
  - quiet titles
  - Cop_Response on, 32 blocks, allies included
  - Speaker_Name set to the old visible format

## Owner checklist
The in-game checks are in reports/final-gate.json (`ownerChecklist`). The headline items:
- Bounty bar after an escape. Getting spotted during the search makes you wanted again. Death ends it.
- Quiet subtitle titles.
- `/glw jail list` header and rows.
- Turf defenders against cops while you are on your gang's turf.
- `Speaker_Name: "{rank} {name}"`.
- `/glw ` + Tab shows no aliases, and `/gangland ` completes.
