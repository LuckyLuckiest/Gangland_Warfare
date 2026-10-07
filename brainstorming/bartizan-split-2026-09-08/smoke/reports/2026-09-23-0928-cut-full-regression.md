# Smoke report: cut-full-regression -- END batch (2026-09-23, fcc41a32): T-53/T-54 fix -- GangMembershipInstaller now a @Bean factory method on GangConfig (real ordering edge), PermissionRepository/PermissionTable moved into the gang module. Eight-module + Bartizan regression boot, /glw reload, /glw lootchest, /glw lootchest help, /glw gang help, /glw rank list, /glw civilian list, /glw modules, /glw debug placeholder. Console confirms bean graph + module YAMLs extract + commands resolve + GanglandApi facade registers; cannot place/open a real chest, open a gang menu or run /glw filter/waypoint gangid (all Player-gated, no player).

**Verdict: PASS**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['mail', 'turf', 'civilians', 'cops', 'gadget', 'npcshops', 'lootchest', 'gang']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-23-0928-cut-full-regression.log`

## Deploy

- core_jar: `E:\Documents\Minecraft\Test Server\plugins\gangland_warfare-0.10.0.jar`
- keystone_jar: `E:\Documents\Minecraft\Test Server\plugins\Keystone-1.11.1.jar`
- modules: ['E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-mail-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-turf-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-civilians-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\cops-n-crooks-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-gadget-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-npc-shops-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-lootchest-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-gang-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Bartizan-0.4.0.jar']

## Expectations

| Check | Result | Detail |
|---|---|---|
| loaded_modules | PASS | want=['civilians', 'copsncrooks', 'gadget', 'gang', 'lootchest', 'mail', 'npcshops', 'turf'] got=['civilians', 'copsncrooks', 'gadget', 'gang', 'lootchest', 'mail', 'npcshops', 'turf'] |
| must_contain | PASS | all present |
| must_not_contain | PASS | none present |
| no_errors_except | PASS | clean |

## Loaded module lines

`Loaded module` ids seen: ['civilians', 'gadget', 'gang', 'lootchest', 'npcshops', 'mail', 'turf', 'copsncrooks']

## Command transcripts

### `glw`
```
>>> glw
[09:29:01 INFO]: 
[09:29:01 INFO]: [38;2;85;85;85m--[38;2;255;170;0m=[3m[38;2;170;170;170mGangland Warfare[0m[38;2;255;170;0m=[38;2;85;85;85m--[0m
[09:29:01 INFO]: [38;2;170;170;170mAuthor[38;2;85;85;85m: [38;2;85;255;255mLuckyRaven10[0m
[09:29:01 INFO]: [38;2;170;170;170mVersion[38;2;85;85;85m: [38;2;85;255;255m0.10.0[0m
[09:29:01 INFO]: [38;2;170;170;170mType [38;2;255;170;0m/glw help [38;2;170;170;170mto start.[0m
[09:29:01 INFO]: 
```

### `glw help`
```
>>> glw help
[09:29:03 INFO]: 
[09:29:03 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Help [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m17[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[09:29:03 INFO]: 
[09:29:03 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;255;85;85m-[0m Shows main help page.
[09:29:03 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in general menu.
[09:29:03 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m update [38;2;255;85;85m-[0m Checks for a new update for the plugin.
[09:29:03 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m update download [38;2;255;85;85m-[0m If there was an update it would try to download it.
[09:29:03 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted [38;2;255;85;85m-[0m Shows the current wanted status.
[09:29:03 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted add [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Adds wanted level.
[09:29:03 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted clear [38;2;255;85;85m-[0m Clears your wanted level.
[09:29:04 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[09:29:04 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
```

### `glw modules`
```
>>> glw modules
[09:29:05 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;170;0;0mDoesn't Exist[38;2;170;170;170m: [38;2;255;170;0m/glw[38;2;170;170;170m modules[0m
[09:29:05 INFO]: [38;2;255;255;85mDid you mean [38;2;85;255;255m"/glw module"[38;2;255;255;85m?[0m
```

### `glw reload`
```
>>> glw reload
[09:29:07 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;255mReloading[38;2;170;170;170m the plugin...[0m
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Force update...
[09:29:07 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 4 weeks, 1 day, 23 hours, 59 minutes and 51 seconds.
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Saving...
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] The process took 229ms
[09:29:07 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:29:07 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:29:07 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:29:07 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[09:29:07 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;85mReload has been completed.[0m
```

### `glw debug inv-data`
```
>>> glw debug inv-data
```

### `glw debug placeholder`
```
>>> glw debug placeholder
[09:29:11 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;170;0;0mWrong Arguments[38;2;170;170;170m: placeholder[0m
[09:29:11 INFO]: [38;2;255;255;85mDid you mean [38;2;85;255;255m"/glw debug placeholder-data"[38;2;255;255;85m?[0m
```

### `glw lootchest`
```
>>> glw lootchest
[09:29:13 INFO]: [38;2;170;0;0mError[38;2;170;170;170m: [38;2;255;85;85mYou need to be a player to use this![0m
```

### `glw lootchest help`
```
>>> glw lootchest help
[09:29:15 INFO]: 
[09:29:15 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Loot Chest Wand [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m1[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[09:29:15 INFO]: 
[09:29:15 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest [38;2;255;85;85m-[0m Get a loot chest wand.
[09:29:15 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest edit [38;2;255;85;85m-[0m Edit wand settings while holding it.
[09:29:15 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in loot chest menu.
[09:29:15 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest remove [38;2;255;85;85m-[0m Removes the loot chest you are looking at.
```

### `glw gang help`
```
>>> glw gang help
[09:29:17 INFO]: 
[09:29:17 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Gang [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m4[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[09:29:17 INFO]: 
[09:29:17 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m gang [38;2;255;85;85m-[0m Shows current gang stats or help page.
[09:29:17 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m gang ally pending [38;2;255;85;85m-[0m Lists outgoing pending ally requests from your gang.
[09:29:17 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m gang ally pending cancel [38;2;170;0;170m<[38;2;170;170;170mgang[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Cancels an outgoing pending ally request.
[09:29:17 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m gang ally abandon [38;2;170;0;170m<[38;2;170;170;170mid[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Abandon alliances to that gang.
[09:29:17 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m gang ally request [38;2;170;0;170m<[38;2;170;170;170mid[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Request alliances to the other gang.
[09:29:17 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m gang balance [38;2;255;85;85m-[0m Shows gang balance.
[09:29:17 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m gang desc [38;2;255;85;85m-[0m Changes current gang description.
```

### `glw rank list`
```
>>> glw rank list
[09:29:19 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m All the ranks:[0m
[09:29:19 INFO]: owner, member
```

### `glw civilian list`
```
>>> glw civilian list
[09:29:21 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m No civilians are currently active.[0m
```

## Distinct ERROR signatures (0)

(none)

## First 40 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-23T05:28:41.158869300Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[09:28:50 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[09:28:54 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[09:28:55 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[09:28:55 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[09:28:55 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[09:28:55 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[09:28:55 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[09:28:55 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[09:28:55 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]`
- `[09:28:55 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]`
- `[09:29:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]`
- `[09:29:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]`
- `[09:29:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-23T05:28:41.158869300Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[09:28:50 INFO]: [Keystone] Loading server plugin Keystone v1.11.1
[09:28:50 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[09:28:50 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[09:28:51 INFO]: [LuckPerms] Successfully enabled. (took 1190ms)
[09:28:53 INFO]: [Keystone] Enabling Keystone v1.11.1
[09:28:53 INFO]: [Keystone] Keystone 1.11.1 loaded. Serving as a library for dependent plugins.
[09:28:54 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[09:28:55 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[09:28:55 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[09:28:55 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[09:28:55 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[09:28:55 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[09:28:55 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[09:28:55 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[09:28:55 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[09:28:55 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[09:28:55 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[09:28:55 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.10.0 from gangland-civilians-0.10.0.jar
[09:28:55 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.10.0 from gangland-gadget-0.10.0.jar
[09:28:55 INFO]: [Keystone Module.ModuleLoader] Loaded module gang 0.10.0 from gangland-gang-0.10.0.jar
[09:28:55 INFO]: [Keystone Module.ModuleLoader] Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar
[09:28:55 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.10.0 from gangland-npc-shops-0.10.0.jar
[09:28:55 INFO]: [Keystone Module.ModuleLoader] Loaded module mail 0.10.0 from gangland-mail-0.10.0.jar
[09:28:55 INFO]: [Keystone Module.ModuleLoader] Loaded module turf 0.10.0 from gangland-turf-0.10.0.jar
[09:28:55 INFO]: [Keystone Module.ModuleLoader] Loaded module copsncrooks 0.10.0 from cops-n-crooks-0.10.0.jar
[09:28:55 INFO]: [Gangland.GanglandContext] Runtime modules: 8 loaded, 0 fault(s)
[09:28:55 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:28:55 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:28:55 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[09:28:55 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[09:28:55 INFO]: [Keystone Persistence.FileHandler] Created file: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\database\gangland.db
[09:28:59 INFO]: [Gangland.GameplayConfig] keystone-inventory service registered
[09:28:59 INFO]: [Gangland.WiringConfig] Placeholder provider published for external consumers (e.g. Plaque)
[09:28:59 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[09:28:59 INFO]: [Gangland.WiringConfig] GanglandApi facade published for external consumers
[09:28:59 INFO]: [Keystone Item.ItemVocabularies] Item vocabulary bartizan contributed 3 converter(s), 3 serializer(s), 3 refresher(s)
[09:28:59 INFO]: [Gangland.GanglandContext] Item vocabularies installed: [bartizan]
[09:29:00 INFO]: [Gangland Civilians.CiviliansModule] Civilians module 0.10.0 enabled
[09:29:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]
[09:29:00 INFO]: [Gangland Gadgets.GadgetModule] Gadget module 0.10.0 enabled
[09:29:00 INFO]: [Gangland.GangModule] Linked Vault permissions
[09:29:00 INFO]: [Gangland.GangModule] Gang module 0.10.0 enabled
[09:29:00 INFO]: [Gangland Loot Chests.LootChestModule] Loot chest module 0.10.0 enabled
[09:29:00 INFO]: [Gangland NPC Shops.NpcShopsModule] NPC Shops module 0.10.0 enabled
[09:29:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]
[09:29:00 INFO]: [Gangland Mail.MailModule] Mail module 0.10.0 enabled
[09:29:00 INFO]: [Gangland Turf.TurfModule] Turf module 0.10.0 enabled
[09:29:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]
[09:29:00 INFO]: [Cops N Crooks.CopsNCrooksModule] Cops-n-crooks module 0.10.0 enabled
[09:29:00 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � cop NPCs will not spawn. [npc.citizens.missing]
[09:29:00 INFO]: [Gangland.Gangland] Linked NBTAPI
[09:29:00 INFO]: [Gangland.Gangland] Found PlaceholderAPI, linking...
[09:29:00 INFO]: [Gangland.Gangland] Linked PlaceholderAPI
[09:29:00 INFO]: [Gangland.Gangland] Found Vault economy, linking...
[09:29:00 INFO]: [Gangland.Gangland] Linked Vault economy
[09:29:00 INFO]: [Gangland.Gangland] Found ViaVersion, linking...
[09:29:00 INFO]: [Gangland.Gangland] Linked ViaVersion
[09:29:00 INFO]: [Keystone Common.UpdateNotifier] Checking for updates
[09:29:01 WARN]: [DeluxeMenus] Could not hook into Vault!
[09:29:01 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[09:29:01 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[09:29:01 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[09:29:01 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[09:29:01 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
[09:29:04 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[09:29:04 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Force update...
[09:29:07 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 4 weeks, 1 day, 23 hours, 59 minutes and 51 seconds.
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Saving...
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] The process took 229ms
[09:29:07 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:29:07 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:29:07 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:29:07 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[09:29:07 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[09:29:07 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[09:29:23 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[09:29:23 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[09:29:23 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[09:29:23 INFO]: [Keystone Module.ModuleLoader] Disabled module copsncrooks 0.10.0
[09:29:23 INFO]: [Keystone Module.ModuleLoader] Disabled module turf 0.10.0
[09:29:23 INFO]: [Keystone Module.ModuleLoader] Disabled module mail 0.10.0
[09:29:23 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.10.0
[09:29:23 INFO]: [Keystone Module.ModuleLoader] Disabled module lootchest 0.10.0
[09:29:23 INFO]: [Keystone Module.ModuleLoader] Disabled module gang 0.10.0
[09:29:23 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.10.0
[09:29:23 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.10.0
[09:29:23 INFO]: [Gangland.PeriodicalUpdates] Force update...
[09:29:23 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 4 weeks, 1 day, 23 hours, 59 minutes and 35 seconds.
[09:29:23 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[09:29:23 INFO]: [Gangland.PeriodicalUpdates] Saving...
[09:29:23 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[09:29:23 INFO]: [Gangland.PeriodicalUpdates] The process took 89ms
[09:29:23 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[09:29:23 INFO]: [Bartizan] Disabling Bartizan v0.4.0
[09:29:23 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[09:29:23 INFO]: [Keystone] Disabling Keystone v1.11.1
[09:29:23 INFO]: [Keystone] Keystone unloaded.
[09:29:23 INFO]: [Vault] Disabling Vault v1.7.3-b131
[09:29:23 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[09:29:23 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[09:29:23 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
