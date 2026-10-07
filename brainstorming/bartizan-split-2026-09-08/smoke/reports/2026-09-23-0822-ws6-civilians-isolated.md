# Smoke report: ws6-civilians-isolated -- WS6 G3 isolation (2026-09-23, T-53 workaround): gang/turf/mail dropped from the deploy set (turf+mail Depends: [gang], and gang's own GangMembershipInstaller crashes onEnable -- see T-53) so civilians' LocalizedModuleYaml message extraction can still be verified on a plugin that actually boots. civilians has no gang dependency.

**Verdict: FAIL**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['civilians', 'cops', 'gadget', 'npcshops', 'lootchest']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-23-0822-ws6-civilians-isolated.log`

## Deploy

- core_jar: `E:\Documents\Minecraft\Test Server\plugins\gangland_warfare-0.10.0.jar`
- keystone_jar: `None`
- modules: ['E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-civilians-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\cops-n-crooks-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-gadget-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-npc-shops-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-lootchest-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Bartizan-0.4.0.jar']

## Expectations

| Check | Result | Detail |
|---|---|---|
| loaded_modules | FAIL | want=['civilians', 'copsncrooks', 'gadget', 'lootchest', 'npcshops'] got=['civilians', 'gadget', 'lootchest', 'npcshops'] |
| must_contain | PASS | all present |
| must_not_contain | PASS | none present |
| no_errors_except | FAIL | offending (first 5)=['[08:22:28 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission', '[08:22:32 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission'] |

## Loaded module lines

`Loaded module` ids seen: ['civilians', 'gadget', 'lootchest', 'npcshops']

## Command transcripts

### `glw`
```
>>> glw
[08:22:26 INFO]: [Vault] No new version available
[08:22:26 INFO]: 
[08:22:26 INFO]: [38;2;85;85;85m--[38;2;255;170;0m=[3m[38;2;170;170;170mGangland Warfare[0m[38;2;255;170;0m=[38;2;85;85;85m--[0m
[08:22:26 INFO]: [38;2;170;170;170mAuthor[38;2;85;85;85m: [38;2;85;255;255mLuckyRaven10[0m
[08:22:26 INFO]: [38;2;170;170;170mVersion[38;2;85;85;85m: [38;2;85;255;255m0.10.0[0m
[08:22:26 INFO]: [38;2;170;170;170mType [38;2;255;170;0m/glw help [38;2;170;170;170mto start.[0m
[08:22:26 INFO]: 
[08:22:26 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m The plugin is up to date.[0m
```

### `glw reload`
```
>>> glw reload
[08:22:28 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;255mReloading[38;2;170;170;170m the plugin...[0m
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Force update...
[08:22:28 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 7 hours, 42 minutes and 47 seconds.
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Saving...
[08:22:28 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission
java.lang.IllegalStateException: No data supplier set for repository: PermissionRepository
	at Keystone-1.11.1.jar//org.luckyraven.keystone.persistence.repository.AbstractRepository.saveAllFromMemory(AbstractRepository.java:147) ~[?:?]
	at Keystone-1.11.1.jar//org.luckyraven.keystone.persistence.repository.RepositoryRegistry.saveAll(RepositoryRegistry.java:252) ~[?:?]
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.bootstrap.PeriodicalUpdates.updatingDatabase(PeriodicalUpdates.java:100) ~[?:?]
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.bootstrap.PeriodicalUpdates.task(PeriodicalUpdates.java:229) ~[?:?]
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.bootstrap.PeriodicalUpdates.forceUpdate(PeriodicalUpdates.java:128) ~[?:?]
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.command.sub.ReloadCommand.reloadProcess(ReloadCommand.java:99) ~[?:?]
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.command.sub.ReloadCommand.onExecute(ReloadCommand.java:41) ~[?:?]
	at Keystone-1.11.1.jar//org.luckyraven.keystone.command.argument.Argument.executeArgument(Argument.java:171) ~[?:?]
	at Keystone-1.11.1.jar//org.luckyraven.keystone.command.argument.Argument.execute(Argument.java:155) ~[?:?]
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.command.Command.runExecute(Command.java:64) ~[?:?]
	at gangland_warfare-0.10.0.jar//org.luckyraven.gangland.command.CommandManager.onCommand(CommandManager.java:75) ~[?:?]
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:45) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
	at io.papermc.paper.command.brigadier.bukkit.BukkitCommandNode$BukkitBrigCommand.run(BukkitCommandNode.java:83) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at com.mojang.brigadier.context.ContextChain.runExecutable(ContextChain.java:73) ~[brigadier-1.3.10.jar:?]
	at net.minecraft.commands.execution.tasks.ExecuteCommand.execute(ExecuteCommand.java:30) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.commands.execution.tasks.ExecuteCommand.execute(ExecuteCommand.java:13) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.commands.execution.UnboundEntryAction.lambda$bind$0(UnboundEntryAction.java:8) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.commands.execution.CommandQueueEntry.execute(CommandQueueEntry.java:5) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.commands.execution.ExecutionContext.runCommandQueue(ExecutionContext.java:104) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.commands.Commands.executeCommandInContext(Commands.java:469) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.commands.Commands.performCommand(Commands.java:374) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.commands.Commands.performCommand(Commands.java:362) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.commands.Commands.performPrefixedCommand(Commands.java:353) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.server.dedicated.DedicatedServer.handleConsoleInputs(DedicatedServer.java:594) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.server.dedicated.DedicatedServer.tickConnection(DedicatedServer.java:550) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.server.MinecraftServer.tickChildren(MinecraftServer.java:1833) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1611) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.server.dedicated.DedicatedServer.tickServer(DedicatedServer.java:427) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.server.MinecraftServer.processPacketsAndTick(MinecraftServer.java:1667) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.server.MinecraftServer.runServer(MinecraftServer.java:1335) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at net.minecraft.server.MinecraftServer.lambda$spin$2(MinecraftServer.java:388) ~[paper-1.21.11.jar:1.21.11-129-3837e9a]
	at java.base/java.lang.Thread.run(Thread.java:1447) ~[?:?]
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] The process took 58ms
[08:22:28 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:28 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:28 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:28 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[08:22:28 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[08:22:28 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[08:22:28 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;85mReload has been completed.[0m
```

### `glw civilian list`
```
>>> glw civilian list
[08:22:30 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m No civilians are currently active.[0m
```

## Distinct ERROR signatures (1)

- (2x) `: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission`

## First 40 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-23T04:22:08.561413500Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[08:22:18 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[08:22:23 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[08:22:23 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[08:22:23 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[08:22:23 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[08:22:23 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[08:22:23 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:22:23 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[08:22:24 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[08:22:24 WARN]: [Keystone Common.LoggingSink] Module 'copsncrooks' needs module 'turf', which is not installed or failed to load (dependency=turf module=copsncrooks jar=cops-n-crooks-0.10.0.jar) [module.dependency.missing]`
- `[08:22:24 WARN]: [Keystone Persistence.DatabaseFaultSink] Failed to persist fault [module.dependency.missing] to oriel_faults: java.lang.IllegalStateException: DatabaseBackend not initialised`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]`
- `[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]`
- `[08:22:24 WARN]: [Keystone Persistence.RepositoryRegistry] Possible circular dependency detected, adding remaining tables`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-23T04:22:08.561413500Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[08:22:18 INFO]: [Keystone] Loading server plugin Keystone v1.11.1
[08:22:18 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[08:22:18 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[08:22:20 INFO]: [LuckPerms] Successfully enabled. (took 1182ms)
[08:22:21 INFO]: [Keystone] Enabling Keystone v1.11.1
[08:22:21 INFO]: [Keystone] Keystone 1.11.1 loaded. Serving as a library for dependent plugins.
[08:22:23 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[08:22:23 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[08:22:23 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[08:22:23 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[08:22:23 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[08:22:23 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:22:23 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[08:22:23 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[08:22:24 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[08:22:24 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[08:22:24 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[08:22:24 WARN]: [Keystone Common.LoggingSink] Module 'copsncrooks' needs module 'turf', which is not installed or failed to load (dependency=turf module=copsncrooks jar=cops-n-crooks-0.10.0.jar) [module.dependency.missing]
[08:22:24 WARN]: [Keystone Persistence.DatabaseFaultSink] Failed to persist fault [module.dependency.missing] to oriel_faults: java.lang.IllegalStateException: DatabaseBackend not initialised
[08:22:24 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.10.0 from gangland-civilians-0.10.0.jar
[08:22:24 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.10.0 from gangland-gadget-0.10.0.jar
[08:22:24 INFO]: [Keystone Module.ModuleLoader] Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar
[08:22:24 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.10.0 from gangland-npc-shops-0.10.0.jar
[08:22:24 INFO]: [Gangland.GanglandContext] Runtime modules: 4 loaded, 1 fault(s)
[08:22:24 INFO]: [Keystone Persistence.FileHandler] Created file: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\npc\civilian_messages.yml (from module resources)
[08:22:24 INFO]: [Keystone Persistence.FileHandler] Created file: E:\Documents\Minecraft\Test Server\plugins\Gangland_Warfare\npc\civilian_messages_es.yml (from module resources)
[08:22:24 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:24 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:24 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:24 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[08:22:24 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[08:22:24 WARN]: [Keystone Persistence.RepositoryRegistry] Possible circular dependency detected, adding remaining tables
[08:22:24 INFO]: [Gangland.GameplayConfig] keystone-inventory service registered
[08:22:24 INFO]: [Gangland.WiringConfig] Placeholder provider published for external consumers (e.g. Plaque)
[08:22:25 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[08:22:25 INFO]: [Keystone Item.ItemVocabularies] Item vocabulary bartizan contributed 3 converter(s), 3 serializer(s), 3 refresher(s)
[08:22:25 INFO]: [Gangland.GanglandContext] Item vocabularies installed: [bartizan]
[08:22:25 WARN]: [Keystone Persistence.RepositoryRegistry] Possible circular dependency detected, adding remaining tables
[08:22:25 INFO]: [Gangland Civilians.CiviliansModule] Civilians module 0.10.0 enabled
[08:22:25 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]
[08:22:25 INFO]: [Gangland Gadgets.GadgetModule] Gadget module 0.10.0 enabled
[08:22:25 INFO]: [Gangland Loot Chests.LootChestModule] Loot chest module 0.10.0 enabled
[08:22:25 INFO]: [Gangland NPC Shops.NpcShopsModule] NPC Shops module 0.10.0 enabled
[08:22:25 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]
[08:22:25 INFO]: [Gangland.Gangland] Linked NBTAPI
[08:22:25 INFO]: [Gangland.Gangland] Found PlaceholderAPI, linking...
[08:22:25 INFO]: [Gangland.Gangland] Linked PlaceholderAPI
[08:22:25 INFO]: [Gangland.Gangland] Found Vault economy, linking...
[08:22:25 INFO]: [Gangland.Gangland] Linked Vault economy
[08:22:25 INFO]: [Gangland.Gangland] Found ViaVersion, linking...
[08:22:25 INFO]: [Gangland.Gangland] Linked ViaVersion
[08:22:25 INFO]: [Keystone Common.UpdateNotifier] Checking for updates
[08:22:25 WARN]: [DeluxeMenus] Could not hook into Vault!
[08:22:25 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[08:22:25 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[08:22:25 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[08:22:25 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[08:22:26 WARN]: [ViaVersion] Could not check for updates, check your connection.
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Force update...
[08:22:28 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 7 hours, 42 minutes and 47 seconds.
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Saving...
[08:22:28 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission
java.lang.IllegalStateException: No data supplier set for repository: PermissionRepository
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] The process took 58ms
[08:22:28 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:28 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:28 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:22:28 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[08:22:28 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[08:22:28 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[08:22:28 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[08:22:28 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[08:22:32 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[08:22:32 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[08:22:32 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[08:22:32 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.10.0
[08:22:32 INFO]: [Keystone Module.ModuleLoader] Disabled module lootchest 0.10.0
[08:22:32 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.10.0
[08:22:32 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.10.0
[08:22:32 INFO]: [Gangland.PeriodicalUpdates] Force update...
[08:22:32 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 days, 7 hours, 42 minutes and 43 seconds.
[08:22:32 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[08:22:32 INFO]: [Gangland.PeriodicalUpdates] Saving...
[08:22:32 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission
java.lang.IllegalStateException: No data supplier set for repository: PermissionRepository
[08:22:32 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[08:22:32 INFO]: [Gangland.PeriodicalUpdates] The process took 1ms
[08:22:32 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[08:22:32 INFO]: [Bartizan] Disabling Bartizan v0.4.0
[08:22:32 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[08:22:32 INFO]: [Keystone] Disabling Keystone v1.11.1
[08:22:32 INFO]: [Keystone] Keystone unloaded.
[08:22:32 INFO]: [Vault] Disabling Vault v1.7.3-b131
[08:22:32 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[08:22:32 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[08:22:32 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
