# Smoke report: cut-full-regression -- END batch (2026-09-23, fcc41a32): T-53/T-54 fix -- GangMembershipInstaller now a @Bean factory method on GangConfig (real ordering edge), PermissionRepository/PermissionTable moved into the gang module. Eight-module + Bartizan regression boot, /glw reload, /glw lootchest, /glw lootchest help, /glw gang help, /glw rank list, /glw civilian list, /glw modules, /glw debug placeholder. Console confirms bean graph + module YAMLs extract + commands resolve + GanglandApi facade registers; cannot place/open a real chest, open a gang menu or run /glw filter/waypoint gangid (all Player-gated, no player).

**Verdict: FAIL**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['mail', 'turf', 'civilians', 'cops', 'gadget', 'npcshops', 'lootchest', 'gang']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-23-0902-cut-full-regression.log`

## Deploy

- core_jar: `E:\Documents\Minecraft\Test Server\plugins\gangland_warfare-0.10.0.jar`
- keystone_jar: `E:\Documents\Minecraft\Test Server\plugins\Keystone-1.11.1.jar`
- modules: ['E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-mail-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-turf-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-civilians-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\cops-n-crooks-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-gadget-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-npc-shops-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-lootchest-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-gang-0.10.0.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Bartizan-0.4.0.jar']

## Expectations

| Check | Result | Detail |
|---|---|---|
| loaded_modules | PASS | want=['civilians', 'copsncrooks', 'gadget', 'gang', 'lootchest', 'mail', 'npcshops', 'turf'] got=['civilians', 'copsncrooks', 'gadget', 'gang', 'lootchest', 'mail', 'npcshops', 'turf'] |
| must_contain | FAIL | missing=['Item vocabularies installed: [bartizan]'] |
| must_not_contain | PASS | none present |
| no_errors_except | FAIL | offending (first 5)=['[09:02:58 ERROR]: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)', '[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: CopSpawner', '[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Member', '[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: TurfPowerupData', '[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: LootChestData'] |

## Loaded module lines

`Loaded module` ids seen: ['civilians', 'gadget', 'gang', 'lootchest', 'npcshops', 'mail', 'turf', 'copsncrooks']

## Command transcripts

### `glw`
```
>>> glw
[09:02:59 ERROR]: Command exception: /glw
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:02:59 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
[09:02:59 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[09:02:59 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
```

### `glw help`
```
>>> glw help
[09:03:01 ERROR]: Command exception: /glw help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:01 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw modules`
```
>>> glw modules
[09:03:03 ERROR]: Command exception: /glw modules
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:03 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw reload`
```
>>> glw reload
[09:03:05 ERROR]: Command exception: /glw reload
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:05 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw debug inv-data`
```
>>> glw debug inv-data
[09:03:07 ERROR]: Command exception: /glw debug inv-data
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:07 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw debug placeholder`
```
>>> glw debug placeholder
[09:03:09 ERROR]: Command exception: /glw debug placeholder
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:09 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw lootchest`
```
>>> glw lootchest
[09:03:11 ERROR]: Command exception: /glw lootchest
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:11 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw lootchest help`
```
>>> glw lootchest help
[09:03:13 ERROR]: Command exception: /glw lootchest help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:13 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw gang help`
```
>>> glw gang help
[09:03:15 ERROR]: Command exception: /glw gang help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:15 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw rank list`
```
>>> glw rank list
[09:03:17 ERROR]: Command exception: /glw rank list
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:17 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw civilian list`
```
>>> glw civilian list
[09:03:19 ERROR]: Command exception: /glw civilian list
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
	at org.bukkit.command.PluginCommand.execute(PluginCommand.java:37) ~[paper-api-1.21.11-R0.1-SNAPSHOT.jar:?]
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
[09:03:19 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

## Distinct ERROR signatures (17)

- (1x) `: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)`
- (1x) `: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: CopSpawner`
- (1x) `: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Member`
- (1x) `: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: TurfPowerupData`
- (1x) `: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: LootChestData`
- (1x) `: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: CivilianSpawner`
- (1x) `: Command exception: /glw`
- (1x) `: Command exception: /glw help`
- (1x) `: Command exception: /glw modules`
- (1x) `: Command exception: /glw reload`
- (1x) `: Command exception: /glw debug inv-data`
- (1x) `: Command exception: /glw debug placeholder`
- (1x) `: Command exception: /glw lootchest`
- (1x) `: Command exception: /glw lootchest help`
- (1x) `: Command exception: /glw gang help`
- (1x) `: Command exception: /glw rank list`
- (1x) `: Command exception: /glw civilian list`

## First 40 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-23T05:02:44.206204200Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[09:02:53 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[09:02:57 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[09:02:57 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[09:02:57 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[09:02:57 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[09:02:57 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[09:02:57 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[09:02:57 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[09:02:58 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]`
- `[09:02:58 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]`
- `[09:02:58 ERROR]: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)`
- `[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: CopSpawner`
- `[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Member`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-23T05:02:44.206204200Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[09:02:53 INFO]: [Keystone] Loading server plugin Keystone v1.11.1
[09:02:53 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[09:02:53 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[09:02:54 INFO]: [LuckPerms] Successfully enabled. (took 1139ms)
[09:02:55 INFO]: [Keystone] Enabling Keystone v1.11.1
[09:02:55 INFO]: [Keystone] Keystone 1.11.1 loaded. Serving as a library for dependent plugins.
[09:02:57 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[09:02:57 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[09:02:57 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[09:02:57 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[09:02:57 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[09:02:57 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[09:02:57 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[09:02:57 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[09:02:58 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[09:02:58 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[09:02:58 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.10.0 from gangland-civilians-0.10.0.jar
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.10.0 from gangland-gadget-0.10.0.jar
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Loaded module gang 0.10.0 from gangland-gang-0.10.0.jar
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.10.0 from gangland-npc-shops-0.10.0.jar
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Loaded module mail 0.10.0 from gangland-mail-0.10.0.jar
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Loaded module turf 0.10.0 from gangland-turf-0.10.0.jar
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Loaded module copsncrooks 0.10.0 from cops-n-crooks-0.10.0.jar
[09:02:58 INFO]: [Gangland.GanglandContext] Runtime modules: 8 loaded, 0 fault(s)
[09:02:58 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:02:58 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:02:58 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[09:02:58 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[09:02:58 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[09:02:58 INFO]: [Gangland.GameplayConfig] keystone-inventory service registered
[09:02:58 INFO]: [Gangland.WiringConfig] Placeholder provider published for external consumers (e.g. Plaque)
[09:02:58 INFO]: [Gangland.WiringConfig] GanglandApi facade published for external consumers
[09:02:58 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[09:02:58 ERROR]: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)
java.lang.IllegalStateException: Ambiguous bean for parameter 'arg0' of type UserManager for bean GangConfig.gangItemSourceContribution(): 2 candidates registered. Add @Qualifier to disambiguate.
[09:02:58 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Disabled module copsncrooks 0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Disabled module turf 0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Disabled module mail 0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Disabled module lootchest 0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Disabled module gang 0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.10.0
[09:02:58 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.10.0
[09:02:58 INFO]: [Gangland.PeriodicalUpdates] Force update...
[09:02:58 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 4 weeks, 1 day, 23 hours, 58 minutes and 19 seconds.
[09:02:58 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[09:02:58 INFO]: [Gangland.PeriodicalUpdates] Saving...
[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: CopSpawner
java.lang.IllegalStateException: No data supplier set for repository: CopSpawnerRepository
[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Member
java.lang.IllegalStateException: No data supplier set for repository: MemberRepository
[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: TurfPowerupData
java.lang.IllegalStateException: No data supplier set for repository: TurfPowerupNpcRepository
[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: LootChestData
java.lang.IllegalStateException: No data supplier set for repository: LootChestRepository
[09:02:58 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: CivilianSpawner
java.lang.IllegalStateException: No data supplier set for repository: CivilianSpawnerRepository
[09:02:58 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[09:02:58 INFO]: [Gangland.PeriodicalUpdates] The process took 99ms
[09:02:58 WARN]: [DeluxeMenus] Could not hook into Vault!
[09:02:58 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[09:02:58 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[09:02:58 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[09:02:58 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[09:02:59 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
[09:02:59 ERROR]: Command exception: /glw
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:02:59 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[09:02:59 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[09:03:01 ERROR]: Command exception: /glw help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:03 ERROR]: Command exception: /glw modules
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:05 ERROR]: Command exception: /glw reload
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:07 ERROR]: Command exception: /glw debug inv-data
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:09 ERROR]: Command exception: /glw debug placeholder
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:11 ERROR]: Command exception: /glw lootchest
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:13 ERROR]: Command exception: /glw lootchest help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:15 ERROR]: Command exception: /glw gang help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:17 ERROR]: Command exception: /glw rank list
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:19 ERROR]: Command exception: /glw civilian list
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[09:03:21 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[09:03:21 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[09:03:21 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[09:03:21 INFO]: [Bartizan] Disabling Bartizan v0.4.0
[09:03:21 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[09:03:21 INFO]: [Keystone] Disabling Keystone v1.11.1
[09:03:21 INFO]: [Keystone] Keystone unloaded.
[09:03:21 INFO]: [Vault] Disabling Vault v1.7.3-b131
[09:03:21 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[09:03:21 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[09:03:21 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
