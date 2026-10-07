# Smoke report: no-scoreboard-boot -- WS1 G3 (0.10.0): scoreboard fully removed from Gangland -- boots clean, /glw reload survives, no scoreboard bean/fault of any kind. Plan Sec.4 step 18 rows (a)+(d): (a) Gangland alone with no scoreboard plugin installed boots with no scoreboard and no error; (d) a full /glw reload leaves the board (if any external plugin renders one) untouched, since Gangland no longer knows scoreboards exist. Row (d)'s Plaque-installed half is exercised by manually staging Plaque-0.1.0.jar into the server's plugins/ folder before this run (outside this harness's scripted deploy -- 'Plaque' is not a PLUGIN_NAME_GLOBS entry, smoke.py itself was not touched, only this scenario file, per ruling W35) so the same boot proves Gangland is inert to Plaque's presence, not just its absence.

**Verdict: PASS**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: []
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-20-2328-no-scoreboard-boot.log`

## Deploy

- core_jar: `E:\Documents\Minecraft\Test Server\plugins\gangland_warfare-0.10.0.jar`
- keystone_jar: `E:\Documents\Minecraft\Test Server\plugins\Keystone-1.11.0.jar`
- modules: []

## Expectations

| Check | Result | Detail |
|---|---|---|
| loaded_modules | PASS | want=[] got=[] |
| must_contain | PASS | all present |
| must_not_contain | PASS | none present |
| no_errors_except | PASS | clean |

## Loaded module lines

`Loaded module` ids seen: []

## Command transcripts

### `glw reload`
```
>>> glw reload
[23:28:41 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;255mReloading[38;2;170;170;170m the plugin...[0m
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:28:41 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 1 week, 1 day, 16 hours, 36 minutes and 33 seconds.
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] The process took 183ms
[23:28:42 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:28:42 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;85mReload has been completed.[0m
```

### `glw`
```
>>> glw
[23:28:43 INFO]: 
[23:28:43 INFO]: [38;2;85;85;85m--[38;2;255;170;0m=[3m[38;2;170;170;170mGangland Warfare[0m[38;2;255;170;0m=[38;2;85;85;85m--[0m
[23:28:43 INFO]: [38;2;170;170;170mAuthor[38;2;85;85;85m: [38;2;85;255;255mLuckyRaven10[0m
[23:28:43 INFO]: [38;2;170;170;170mVersion[38;2;85;85;85m: [38;2;85;255;255m0.10.0[0m
[23:28:43 INFO]: [38;2;170;170;170mType [38;2;255;170;0m/glw help [38;2;170;170;170mto start.[0m
[23:28:43 INFO]: 
```

### `glw help`
```
>>> glw help
[23:28:45 INFO]: 
[23:28:45 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Help [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m22[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[23:28:45 INFO]: 
[23:28:45 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;255;85;85m-[0m Shows main help page.
[23:28:45 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in general menu.
[23:28:45 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m bounty [38;2;255;85;85m-[0m Shows the current bounty or help menu.
[23:28:45 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m bounty help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in bounty menu.
[23:28:45 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m bounty remove [38;2;170;0;170m<[38;2;170;170;170mplayer[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Removes the bounty you set.
[23:28:45 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m bounty set [38;2;170;0;170m<[38;2;170;170;170mplayer[38;2;170;0;170m>[38;2;170;170;170m [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Sets a bounty on the player head.
[23:28:45 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m update [38;2;255;85;85m-[0m Checks for a new update for the plugin.
```

### `glw help 2`
```
>>> glw help 2
[23:28:47 INFO]: 
[23:28:47 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Help [38;2;85;85;85m[[38;2;170;170;170m2[38;2;170;0;170m/[38;2;170;170;170m22[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[23:28:47 INFO]: 
[23:28:47 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m update download [38;2;255;85;85m-[0m If there was an update it would try to download it.
[23:28:47 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m reload [38;2;255;85;85m-[0m Reloads the whole plugin.
[23:28:47 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m reload data [38;2;255;85;85m-[0m Reloads the data related to the database.
[23:28:47 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m reload files [38;2;255;85;85m-[0m Reloads the files.
[23:28:47 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m reload help [38;2;255;85;85m-[0m Shows the reload help page.
[23:28:47 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted [38;2;255;85;85m-[0m Shows the current wanted status.
[23:28:47 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted add [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Adds wanted level.
```

### `glw help 3`
```
>>> glw help 3
[23:28:49 INFO]: 
[23:28:49 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Help [38;2;85;85;85m[[38;2;170;170;170m3[38;2;170;0;170m/[38;2;170;170;170m22[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[23:28:49 INFO]: 
[23:28:49 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted clear [38;2;255;85;85m-[0m Clears your wanted level.
[23:28:49 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted clear [38;2;170;0;170m<[38;2;170;170;170mplayer[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Clears the wanted level of the specified player.
[23:28:49 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in wanted menu.
[23:28:49 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m wanted remove [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Removes wanted level.
[23:28:49 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m waypoint [38;2;255;85;85m-[0m Get the help page.
[23:28:49 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m waypoint cooldown [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Sets the teleport cooldown.
[23:28:49 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m waypoint cost [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Sets the cost to teleport to that waypoint.
```

### `glw modules`
```
>>> glw modules
[23:28:51 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;170;0;0mDoesn't Exist[38;2;170;170;170m: [38;2;255;170;0m/glw[38;2;170;170;170m modules[0m
[23:28:51 INFO]: [38;2;255;255;85mDid you mean [38;2;85;255;255m"/glw module"[38;2;255;255;85m?[0m
```

## Distinct ERROR signatures (0)

(none)

## First 28 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-20T19:28:12.296503300Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[23:28:25 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[23:28:31 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[23:28:33 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[23:28:33 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[23:28:33 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[23:28:33 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[23:28:33 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:28:33 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[23:28:33 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[23:28:35 WARN]: [DeluxeMenus] Could not hook into Vault!`
- `[23:28:35 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.`
- `[23:28:35 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!`
- `[23:28:35 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!`
- `[23:28:35 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!`
- `[23:28:36 WARN]: [ViaVersion] Could not check for updates, check your connection.`
- `[23:28:36 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!`
- `[23:28:36 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-20T19:28:12.296503300Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[23:28:25 INFO]: [Keystone] Loading server plugin Keystone v1.11.0
[23:28:25 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[23:28:25 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[23:28:27 INFO]: [LuckPerms] Successfully enabled. (took 1654ms)
[23:28:29 INFO]: [Keystone] Enabling Keystone v1.11.0
[23:28:29 INFO]: [Keystone] Keystone 1.11.0 loaded. Serving as a library for dependent plugins.
[23:28:30 INFO]: [Keystone Persistence.FileHandler] Created directory: E:\Documents\Minecraft\Test Server\plugins\Plaque
[23:28:31 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[23:28:33 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[23:28:33 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[23:28:33 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[23:28:33 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[23:28:33 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:28:33 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[23:28:33 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[23:28:33 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[23:28:33 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[23:28:33 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[23:28:33 INFO]: [Gangland.GanglandContext] Runtime modules: 0 loaded, 0 fault(s)
[23:28:33 INFO]: [Keystone Persistence.FileHandler] settings.yml is an old build or corrupted, creating a new one
[23:28:34 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:28:34 INFO]: [Gangland.GanglandContext] Item vocabularies installed: none � weapon:/ammo:/wearable: item strings will not resolve
[23:28:35 INFO]: [Gangland.Gangland] Linked NBTAPI
[23:28:35 INFO]: [Gangland.Gangland] Found PlaceholderAPI, linking...
[23:28:35 INFO]: [Gangland.Gangland] Linked PlaceholderAPI
[23:28:35 INFO]: [Gangland.Gangland] Found Vault economy, linking...
[23:28:35 INFO]: [Gangland.Gangland] Linked Vault economy
[23:28:35 INFO]: [Gangland.Gangland] Found Vault permissions, linking...
[23:28:35 INFO]: [Gangland.Gangland] Linked Vault permissions
[23:28:35 INFO]: [Gangland.Gangland] Found ViaVersion, linking...
[23:28:35 INFO]: [Gangland.Gangland] Linked ViaVersion
[23:28:35 INFO]: [Keystone Common.UpdateNotifier] Checking for updates
[23:28:35 WARN]: [DeluxeMenus] Could not hook into Vault!
[23:28:35 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[23:28:35 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[23:28:35 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[23:28:35 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[23:28:36 WARN]: [ViaVersion] Could not check for updates, check your connection.
[23:28:36 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[23:28:36 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:28:41 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 1 week, 1 day, 16 hours, 36 minutes and 33 seconds.
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:28:41 INFO]: [Gangland.PeriodicalUpdates] The process took 183ms
[23:28:42 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:28:53 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[23:28:53 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[23:28:53 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[23:28:53 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:28:53 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 1 week, 1 day, 16 hours, 36 minutes and 21 seconds.
[23:28:53 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:28:53 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:28:53 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:28:53 INFO]: [Gangland.PeriodicalUpdates] The process took 121ms
[23:28:53 INFO]: [Plaque] Disabling Plaque v0.1.0
[23:28:53 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[23:28:53 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[23:28:53 INFO]: [Keystone] Disabling Keystone v1.11.0
[23:28:53 INFO]: [Keystone] Keystone unloaded.
[23:28:53 INFO]: [Vault] Disabling Vault v1.7.3-b131
[23:28:53 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[23:28:53 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[23:28:54 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
