# Smoke report: cut-full-regression -- WS2 CUT (0.10.0): gangland-ui/inventory-api deleted outright, every menu now on keystone-inventory + gangland-impl's menu.* dialect -- full six-module + Bartizan regression boot (mail, turf, civilians, cops-n-crooks, gadget, npcshops), then /glw reload and /glw debug inv-data from console. Console can only confirm the bean graph boots clean and the tracker command resolves without an exception -- it CANNOT open a real chest menu or drive a click (no in-game player); the CUT manual checklist (exec/G010/WS2-manual-checklist.md, CUT section) covers loot-chest open/take and wand-preview rows a human must run.

**Verdict: PASS**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['mail', 'turf', 'civilians', 'cops', 'gadget', 'npcshops']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-22-1814-cut-full-regression.log`

## Deploy

- core_jar: `E:\Documents\Minecraft\Test Server\plugins\gangland_warfare-0.10.0.jar`
- keystone_jar: `E:\Documents\Minecraft\Test Server\plugins\Keystone-1.11.1.jar`
- modules: ['E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-mail-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-turf-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-civilians-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\cops-n-crooks-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-gadget-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-npc-shops-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Bartizan-0.4.0.jar']

## Expectations

| Check | Result | Detail |
|---|---|---|
| loaded_modules | PASS | want=['civilians', 'copsncrooks', 'gadget', 'mail', 'npcshops', 'turf'] got=['civilians', 'copsncrooks', 'gadget', 'mail', 'npcshops', 'turf'] |
| must_contain | PASS | all present |
| must_not_contain | PASS | none present |
| no_errors_except | PASS | clean |

## Loaded module lines

`Loaded module` ids seen: ['civilians', 'gadget', 'mail', 'npcshops', 'turf', 'copsncrooks']

## Command transcripts

### `glw`
```
>>> glw
[18:15:19 INFO]: 
[18:15:19 INFO]: [38;2;85;85;85m--[38;2;255;170;0m=[3m[38;2;170;170;170mGangland Warfare[0m[38;2;255;170;0m=[38;2;85;85;85m--[0m
[18:15:19 INFO]: [38;2;170;170;170mAuthor[38;2;85;85;85m: [38;2;85;255;255mLuckyRaven10[0m
[18:15:19 INFO]: [38;2;170;170;170mVersion[38;2;85;85;85m: [38;2;85;255;255m0.10.0[0m
[18:15:19 INFO]: [38;2;170;170;170mType [38;2;255;170;0m/glw help [38;2;170;170;170mto start.[0m
[18:15:19 INFO]: 
[18:15:20 INFO]: [Vault] No new version available
[18:15:20 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m The plugin is up to date.[0m
[18:15:20 INFO]: [BetterGUI] You are using the latest version
[18:15:21 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
```

### `glw help`
```
>>> glw help
[18:15:21 INFO]: 
[18:15:21 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Help [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m24[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[18:15:21 INFO]: 
[18:15:21 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;255;85;85m-[0m Shows main help page.
[18:15:21 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in general menu.
[18:15:21 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m waypoint [38;2;255;85;85m-[0m Get the help page.
[18:15:21 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m waypoint cooldown [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Sets the teleport cooldown.
[18:15:21 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m waypoint cost [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Sets the cost to teleport to that waypoint.
[18:15:21 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m waypoint create [38;2;170;0;170m<[38;2;170;170;170mname[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Creates a new waypoint according to the current location.
[18:15:21 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m waypoint deselect [38;2;255;85;85m-[0m Deselects the selected waypoint.
[18:15:22 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[18:15:22 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
```

### `glw modules`
```
>>> glw modules
[18:15:23 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;170;0;0mDoesn't Exist[38;2;170;170;170m: [38;2;255;170;0m/glw[38;2;170;170;170m modules[0m
[18:15:23 INFO]: [38;2;255;255;85mDid you mean [38;2;85;255;255m"/glw module"[38;2;255;255;85m?[0m
```

### `glw reload`
```
>>> glw reload
[18:15:25 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;255mReloading[38;2;170;170;170m the plugin...[0m
[18:15:25 INFO]: [Gangland.PeriodicalUpdates] Force update...
[18:15:25 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 21 hours, 49 minutes and 49 seconds.
[18:15:25 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[18:15:25 INFO]: [Gangland.PeriodicalUpdates] Saving...
[18:15:26 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[18:15:26 INFO]: [Gangland.PeriodicalUpdates] The process took 139ms
[18:15:26 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[18:15:26 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[18:15:26 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[18:15:26 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[18:15:26 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;85mReload has been completed.[0m
```

### `glw debug inv-data`
```
>>> glw debug inv-data
```

## Distinct ERROR signatures (1)

- (1x) `: [PlaceholderAPI] Failed to download anti malware hash check list from https://check.placeholderapi.com`

## First 40 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-22T14:14:57.528997400Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[18:15:10 ERROR]: [PlaceholderAPI] Failed to download anti malware hash check list from https://check.placeholderapi.com`
- `[18:15:10 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[18:15:15 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[18:15:16 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[18:15:16 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[18:15:16 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[18:15:16 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[18:15:16 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[18:15:16 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[18:15:17 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]`
- `[18:15:17 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]`
- `[18:15:18 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]`
- `[18:15:18 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]`
- `[18:15:18 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]`
- `[18:15:18 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � cop NPCs will not spawn. [npc.citizens.missing]`
- `[18:15:19 WARN]: [DeluxeMenus] Could not hook into Vault!`
- `[18:15:19 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.`
- `[18:15:19 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-22T14:14:57.528997400Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[18:15:10 ERROR]: [PlaceholderAPI] Failed to download anti malware hash check list from https://check.placeholderapi.com
java.net.UnknownHostException: check.placeholderapi.com
[18:15:10 INFO]: [Keystone] Loading server plugin Keystone v1.11.1
[18:15:10 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[18:15:10 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[18:15:12 INFO]: [LuckPerms] Successfully enabled. (took 1625ms)
[18:15:13 INFO]: [Keystone] Enabling Keystone v1.11.1
[18:15:13 INFO]: [Keystone] Keystone 1.11.1 loaded. Serving as a library for dependent plugins.
[18:15:15 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[18:15:16 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[18:15:16 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[18:15:16 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[18:15:16 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[18:15:16 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[18:15:16 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[18:15:16 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[18:15:17 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[18:15:17 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[18:15:17 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[18:15:17 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.10.0 from gangland-civilians-0.10.0.jar
[18:15:17 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.10.0 from gangland-gadget-0.10.0.jar
[18:15:17 INFO]: [Keystone Module.ModuleLoader] Loaded module mail 0.10.0 from gangland-mail-0.10.0.jar
[18:15:17 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.10.0 from gangland-npc-shops-0.10.0.jar
[18:15:17 INFO]: [Keystone Module.ModuleLoader] Loaded module turf 0.10.0 from gangland-turf-0.10.0.jar
[18:15:17 INFO]: [Keystone Module.ModuleLoader] Loaded module copsncrooks 0.10.0 from cops-n-crooks-0.10.0.jar
[18:15:17 INFO]: [Gangland.GanglandContext] Runtime modules: 6 loaded, 0 fault(s)
[18:15:17 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[18:15:17 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[18:15:17 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[18:15:18 INFO]: [Gangland.GameplayConfig] keystone-inventory service registered
[18:15:18 INFO]: [Gangland.WiringConfig] Placeholder provider published for external consumers (e.g. Plaque)
[18:15:18 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[18:15:18 INFO]: [Keystone Item.ItemVocabularies] Item vocabulary bartizan contributed 3 converter(s), 3 serializer(s), 3 refresher(s)
[18:15:18 INFO]: [Gangland.GanglandContext] Item vocabularies installed: [bartizan]
[18:15:18 INFO]: [Gangland Civilians.CiviliansModule] Civilians module 0.10.0 enabled
[18:15:18 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]
[18:15:18 INFO]: [Gangland Gadgets.GadgetModule] Gadget module 0.10.0 enabled
[18:15:18 INFO]: [Gangland Mail.MailModule] Mail module 0.10.0 enabled
[18:15:18 INFO]: [Gangland NPC Shops.NpcShopsModule] NPC Shops module 0.10.0 enabled
[18:15:18 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]
[18:15:18 INFO]: [Gangland Turf.TurfModule] Turf module 0.10.0 enabled
[18:15:18 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]
[18:15:18 INFO]: [Cops N Crooks.CopsNCrooksModule] Cops-n-crooks module 0.10.0 enabled
[18:15:18 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � cop NPCs will not spawn. [npc.citizens.missing]
[18:15:19 INFO]: [Gangland.Gangland] Linked NBTAPI
[18:15:19 INFO]: [Gangland.Gangland] Found PlaceholderAPI, linking...
[18:15:19 INFO]: [Gangland.Gangland] Linked PlaceholderAPI
[18:15:19 INFO]: [Gangland.Gangland] Found Vault economy, linking...
[18:15:19 INFO]: [Gangland.Gangland] Linked Vault economy
[18:15:19 INFO]: [Gangland.Gangland] Found Vault permissions, linking...
[18:15:19 INFO]: [Gangland.Gangland] Linked Vault permissions
[18:15:19 INFO]: [Gangland.Gangland] Found ViaVersion, linking...
[18:15:19 INFO]: [Gangland.Gangland] Linked ViaVersion
[18:15:19 INFO]: [Keystone Common.UpdateNotifier] Checking for updates
[18:15:19 WARN]: [DeluxeMenus] Could not hook into Vault!
[18:15:19 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[18:15:19 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[18:15:19 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[18:15:19 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[18:15:21 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
[18:15:22 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[18:15:22 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[18:15:25 INFO]: [Gangland.PeriodicalUpdates] Force update...
[18:15:25 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 21 hours, 49 minutes and 49 seconds.
[18:15:25 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[18:15:25 INFO]: [Gangland.PeriodicalUpdates] Saving...
[18:15:26 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[18:15:26 INFO]: [Gangland.PeriodicalUpdates] The process took 139ms
[18:15:26 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[18:15:26 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[18:15:26 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[18:15:26 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[18:15:26 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[18:15:30 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[18:15:30 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[18:15:30 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[18:15:30 INFO]: [Keystone Module.ModuleLoader] Disabled module copsncrooks 0.10.0
[18:15:30 INFO]: [Keystone Module.ModuleLoader] Disabled module turf 0.10.0
[18:15:30 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.10.0
[18:15:30 INFO]: [Keystone Module.ModuleLoader] Disabled module mail 0.10.0
[18:15:30 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.10.0
[18:15:30 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.10.0
[18:15:30 INFO]: [Gangland.PeriodicalUpdates] Force update...
[18:15:30 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 21 hours, 49 minutes and 45 seconds.
[18:15:30 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[18:15:30 INFO]: [Gangland.PeriodicalUpdates] Saving...
[18:15:30 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[18:15:30 INFO]: [Gangland.PeriodicalUpdates] The process took 81ms
[18:15:30 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[18:15:30 INFO]: [Bartizan] Disabling Bartizan v0.4.0
[18:15:30 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[18:15:30 INFO]: [Keystone] Disabling Keystone v1.11.1
[18:15:30 INFO]: [Keystone] Keystone unloaded.
[18:15:30 INFO]: [Vault] Disabling Vault v1.7.3-b131
[18:15:30 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[18:15:30 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[18:15:30 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
