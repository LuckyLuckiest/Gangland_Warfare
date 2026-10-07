# Smoke report: cut-full-regression -- WS2 CUT + WS3 G1-G5 merge (2026-09-22/23): gangland-ui/{inventory-api,hologram-api,lootchest-api} all deleted, loot chests fully module-owned (config/messages/commands) in gangland-features/gangland-lootchest -- seven-module + Bartizan regression boot, /glw reload, /glw lootchest, /glw lootchest help. Console confirms bean graph + module YAMLs extract + commands resolve; cannot place/open a real chest (no player).

**Verdict: PASS**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['mail', 'turf', 'civilians', 'cops', 'gadget', 'npcshops', 'lootchest']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-22-2322-cut-full-regression.log`

## Deploy

- core_jar: `E:\Documents\Minecraft\Test Server\plugins\gangland_warfare-0.10.0.jar`
- keystone_jar: `E:\Documents\Minecraft\Test Server\plugins\Keystone-1.11.1.jar`
- modules: ['E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-mail-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-turf-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-civilians-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\cops-n-crooks-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-gadget-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-npc-shops-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-lootchest-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Bartizan-0.4.0.jar']

## Expectations

| Check | Result | Detail |
|---|---|---|
| loaded_modules | PASS | want=['civilians', 'copsncrooks', 'gadget', 'lootchest', 'mail', 'npcshops', 'turf'] got=['civilians', 'copsncrooks', 'gadget', 'lootchest', 'mail', 'npcshops', 'turf'] |
| must_contain | PASS | all present |
| must_not_contain | PASS | none present |
| no_errors_except | PASS | clean |

## Loaded module lines

`Loaded module` ids seen: ['civilians', 'gadget', 'lootchest', 'mail', 'npcshops', 'turf', 'copsncrooks']

## Command transcripts

### `glw`
```
>>> glw
[23:23:02 INFO]: 
[23:23:02 INFO]: [38;2;85;85;85m--[38;2;255;170;0m=[3m[38;2;170;170;170mGangland Warfare[0m[38;2;255;170;0m=[38;2;85;85;85m--[0m
[23:23:02 INFO]: [38;2;170;170;170mAuthor[38;2;85;85;85m: [38;2;85;255;255mLuckyRaven10[0m
[23:23:02 INFO]: [38;2;170;170;170mVersion[38;2;85;85;85m: [38;2;85;255;255m0.10.0[0m
[23:23:02 INFO]: [38;2;170;170;170mType [38;2;255;170;0m/glw help [38;2;170;170;170mto start.[0m
[23:23:02 INFO]: 
[23:23:04 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[23:23:04 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
```

### `glw help`
```
>>> glw help
[23:23:04 INFO]: 
[23:23:04 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Help [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m23[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[23:23:04 INFO]: 
[23:23:04 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;255;85;85m-[0m Shows main help page.
[23:23:04 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in general menu.
[23:23:04 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m fuel [38;2;255;85;85m-[0m Shows fuel help page.
[23:23:04 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m fuel add [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Adds fuel to the item in hand.
[23:23:04 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m fuel defuel [38;2;255;85;85m-[0m Drains all current fuel from the item in hand without changing max capacity.
[23:23:04 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m fuel defuel [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Drains the specified amount of fuel from the item in hand without changing max capacity.
[23:23:04 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m fuel help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in fuel menu.
```

### `glw modules`
```
>>> glw modules
[23:23:06 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;170;0;0mDoesn't Exist[38;2;170;170;170m: [38;2;255;170;0m/glw[38;2;170;170;170m modules[0m
[23:23:06 INFO]: [38;2;255;255;85mDid you mean [38;2;85;255;255m"/glw module"[38;2;255;255;85m?[0m
```

### `glw reload`
```
>>> glw reload
[23:23:08 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;255mReloading[38;2;170;170;170m the plugin...[0m
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:23:08 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 16 hours, 42 minutes and 7 seconds.
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] The process took 183ms
[23:23:08 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:23:08 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:23:08 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:23:08 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:23:08 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;85mReload has been completed.[0m
```

### `glw debug inv-data`
```
>>> glw debug inv-data
```

### `glw lootchest`
```
>>> glw lootchest
[23:23:12 INFO]: [38;2;170;0;0mError[38;2;170;170;170m: [38;2;255;85;85mYou need to be a player to use this![0m
```

### `glw lootchest help`
```
>>> glw lootchest help
[23:23:14 INFO]: 
[23:23:14 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Loot Chest Wand [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m1[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[23:23:14 INFO]: 
[23:23:14 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest [38;2;255;85;85m-[0m Get a loot chest wand.
[23:23:14 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest edit [38;2;255;85;85m-[0m Edit wand settings while holding it.
[23:23:14 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in loot chest menu.
[23:23:14 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest remove [38;2;255;85;85m-[0m Removes the loot chest you are looking at.
```

## Distinct ERROR signatures (0)

(none)

## First 40 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-22T19:22:38.761329100Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[23:22:51 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[23:22:57 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[23:22:58 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[23:22:58 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[23:22:58 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[23:22:58 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[23:22:58 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[23:22:58 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:22:59 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[23:22:59 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:22:59 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:22:59 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[23:22:59 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[23:22:59 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[23:22:59 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]`
- `[23:22:59 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]`
- `[23:23:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]`
- `[23:23:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]`
- `[23:23:01 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-22T19:22:38.761329100Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[23:22:51 INFO]: [Keystone] Loading server plugin Keystone v1.11.1
[23:22:51 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[23:22:51 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[23:22:53 INFO]: [LuckPerms] Successfully enabled. (took 1527ms)
[23:22:54 INFO]: [Keystone] Enabling Keystone v1.11.1
[23:22:54 INFO]: [Keystone] Keystone 1.11.1 loaded. Serving as a library for dependent plugins.
[23:22:57 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[23:22:58 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[23:22:58 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[23:22:58 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[23:22:58 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[23:22:58 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[23:22:58 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:22:59 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[23:22:59 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:22:59 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:22:59 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[23:22:59 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[23:22:59 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[23:22:59 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[23:22:59 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[23:22:59 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[23:22:59 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.10.0 from gangland-civilians-0.10.0.jar
[23:22:59 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.10.0 from gangland-gadget-0.10.0.jar
[23:22:59 INFO]: [Keystone Module.ModuleLoader] Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar
[23:22:59 INFO]: [Keystone Module.ModuleLoader] Loaded module mail 0.10.0 from gangland-mail-0.10.0.jar
[23:22:59 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.10.0 from gangland-npc-shops-0.10.0.jar
[23:22:59 INFO]: [Keystone Module.ModuleLoader] Loaded module turf 0.10.0 from gangland-turf-0.10.0.jar
[23:22:59 INFO]: [Keystone Module.ModuleLoader] Loaded module copsncrooks 0.10.0 from cops-n-crooks-0.10.0.jar
[23:22:59 INFO]: [Gangland.GanglandContext] Runtime modules: 7 loaded, 0 fault(s)
[23:22:59 INFO]: [Keystone Persistence.FileHandler] Created file: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\lootchests\lootchest_messages.yml (from module resources)
[23:22:59 INFO]: [Keystone Persistence.FileHandler] Created file: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\lootchests\lootchest_messages_es.yml (from module resources)
[23:22:59 INFO]: [Keystone Persistence.FileHandler] Created file: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\lootchests\loot_chest_settings.yml (from module resources)
[23:22:59 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:22:59 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:22:59 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[23:22:59 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[23:23:00 INFO]: [Gangland.GameplayConfig] keystone-inventory service registered
[23:23:00 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:23:00 INFO]: [Gangland.WiringConfig] Placeholder provider published for external consumers (e.g. Plaque)
[23:23:00 INFO]: [Keystone Item.ItemVocabularies] Item vocabulary bartizan contributed 3 converter(s), 3 serializer(s), 3 refresher(s)
[23:23:00 INFO]: [Gangland.GanglandContext] Item vocabularies installed: [bartizan]
[23:23:00 INFO]: [Gangland Civilians.CiviliansModule] Civilians module 0.10.0 enabled
[23:23:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]
[23:23:00 INFO]: [Gangland Gadgets.GadgetModule] Gadget module 0.10.0 enabled
[23:23:00 INFO]: [Gangland Loot Chests.LootChestModule] Loot chest module 0.10.0 enabled
[23:23:00 INFO]: [Gangland Mail.MailModule] Mail module 0.10.0 enabled
[23:23:00 INFO]: [Gangland NPC Shops.NpcShopsModule] NPC Shops module 0.10.0 enabled
[23:23:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]
[23:23:01 INFO]: [Gangland Turf.TurfModule] Turf module 0.10.0 enabled
[23:23:01 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]
[23:23:01 INFO]: [Cops N Crooks.CopsNCrooksModule] Cops-n-crooks module 0.10.0 enabled
[23:23:01 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � cop NPCs will not spawn. [npc.citizens.missing]
[23:23:01 INFO]: [Gangland.Gangland] Linked NBTAPI
[23:23:01 INFO]: [Gangland.Gangland] Found PlaceholderAPI, linking...
[23:23:01 INFO]: [Gangland.Gangland] Linked PlaceholderAPI
[23:23:01 INFO]: [Gangland.Gangland] Found Vault economy, linking...
[23:23:01 INFO]: [Gangland.Gangland] Linked Vault economy
[23:23:01 INFO]: [Gangland.Gangland] Found Vault permissions, linking...
[23:23:01 INFO]: [Gangland.Gangland] Linked Vault permissions
[23:23:01 INFO]: [Gangland.Gangland] Found ViaVersion, linking...
[23:23:01 INFO]: [Gangland.Gangland] Linked ViaVersion
[23:23:01 INFO]: [Keystone Common.UpdateNotifier] Checking for updates
[23:23:01 WARN]: [DeluxeMenus] Could not hook into Vault!
[23:23:01 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[23:23:01 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[23:23:01 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[23:23:01 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[23:23:02 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
[23:23:04 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[23:23:04 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:23:08 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 16 hours, 42 minutes and 7 seconds.
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] The process took 183ms
[23:23:08 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:23:08 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:23:08 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:23:08 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[23:23:08 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[23:23:08 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:23:16 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[23:23:16 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[23:23:16 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[23:23:16 INFO]: [Keystone Module.ModuleLoader] Disabled module copsncrooks 0.10.0
[23:23:16 INFO]: [Keystone Module.ModuleLoader] Disabled module turf 0.10.0
[23:23:16 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.10.0
[23:23:16 INFO]: [Keystone Module.ModuleLoader] Disabled module mail 0.10.0
[23:23:16 INFO]: [Keystone Module.ModuleLoader] Disabled module lootchest 0.10.0
[23:23:16 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.10.0
[23:23:16 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.10.0
[23:23:16 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:23:16 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 16 hours, 41 minutes and 59 seconds.
[23:23:16 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:23:16 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:23:16 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:23:16 INFO]: [Gangland.PeriodicalUpdates] The process took 102ms
[23:23:16 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[23:23:16 INFO]: [Bartizan] Disabling Bartizan v0.4.0
[23:23:16 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[23:23:16 INFO]: [Keystone] Disabling Keystone v1.11.1
[23:23:16 INFO]: [Keystone] Keystone unloaded.
[23:23:16 INFO]: [Vault] Disabling Vault v1.7.3-b131
[23:23:16 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[23:23:16 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[23:23:16 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
