# Smoke report: cut-full-regression -- WS2 CUT + WS3 G1-G5 + WS5 G1-G3 + WS6 G3 merge (2026-09-22/23): gangland-ui/{inventory-api,hologram-api,lootchest-api} all deleted, loot chests fully module-owned, gang domain now the gangland-gang runtime module (turf/mail Depends: [gang]), civilians messages module-owned via LocalizedModuleYaml -- eight-module + Bartizan regression boot, /glw reload, /glw lootchest, /glw lootchest help, /glw gang help, /glw rank list, /glw civilian list. Console confirms bean graph + module YAMLs extract + commands resolve; cannot place/open a real chest, open a gang menu or run /glw filter/waypoint gangid (all Player-gated, no player).

**Verdict: FAIL**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['mail', 'turf', 'civilians', 'cops', 'gadget', 'npcshops', 'lootchest', 'gang']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-23-0816-cut-full-regression.log`

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
| no_errors_except | FAIL | offending (first 5)=['[08:17:07 ERROR]: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)', '[08:17:08 ERROR]: Command exception: /glw', '[08:17:10 ERROR]: Command exception: /glw help', '[08:17:12 ERROR]: Command exception: /glw modules', '[08:17:14 ERROR]: Command exception: /glw reload'] |

## Loaded module lines

`Loaded module` ids seen: ['civilians', 'gadget', 'gang', 'lootchest', 'npcshops', 'mail', 'turf', 'copsncrooks']

## Command transcripts

### `glw`
```
>>> glw
[08:17:08 INFO]: [Vault] No new version available
[08:17:08 ERROR]: Command exception: /glw
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
[08:17:08 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
[08:17:08 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
[08:17:08 INFO]: [BetterGUI] You are using the latest version
```

### `glw help`
```
>>> glw help
[08:17:10 ERROR]: Command exception: /glw help
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
[08:17:10 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
[08:17:10 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[08:17:10 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
```

### `glw modules`
```
>>> glw modules
[08:17:12 ERROR]: Command exception: /glw modules
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
[08:17:12 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw reload`
```
>>> glw reload
[08:17:14 ERROR]: Command exception: /glw reload
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
[08:17:14 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw debug inv-data`
```
>>> glw debug inv-data
[08:17:16 ERROR]: Command exception: /glw debug inv-data
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
[08:17:16 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw lootchest`
```
>>> glw lootchest
[08:17:18 ERROR]: Command exception: /glw lootchest
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
[08:17:18 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw lootchest help`
```
>>> glw lootchest help
[08:17:20 ERROR]: Command exception: /glw lootchest help
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
[08:17:20 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw gang help`
```
>>> glw gang help
[08:17:22 ERROR]: Command exception: /glw gang help
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
[08:17:22 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw rank list`
```
>>> glw rank list
[08:17:24 ERROR]: Command exception: /glw rank list
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
[08:17:24 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

### `glw civilian list`
```
>>> glw civilian list
[08:17:26 ERROR]: Command exception: /glw civilian list
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
[08:17:26 INFO]: [38;2;255;85;85mAn unexpected error occurred trying to execute that command[0m
```

## Distinct ERROR signatures (11)

- (1x) `: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)`
- (1x) `: Command exception: /glw`
- (1x) `: Command exception: /glw help`
- (1x) `: Command exception: /glw modules`
- (1x) `: Command exception: /glw reload`
- (1x) `: Command exception: /glw debug inv-data`
- (1x) `: Command exception: /glw lootchest`
- (1x) `: Command exception: /glw lootchest help`
- (1x) `: Command exception: /glw gang help`
- (1x) `: Command exception: /glw rank list`
- (1x) `: Command exception: /glw civilian list`

## First 39 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-23T04:16:45.867805700Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[08:16:59 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[08:17:05 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[08:17:07 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[08:17:07 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[08:17:07 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[08:17:07 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[08:17:07 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:17:07 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[08:17:07 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[08:17:07 ERROR]: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)`
- `[08:17:07 WARN]: [DeluxeMenus] Could not hook into Vault!`
- `[08:17:07 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.`
- `[08:17:07 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!`
- `[08:17:07 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!`
- `[08:17:07 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!`
- `[08:17:08 ERROR]: Command exception: /glw`
- `[08:17:08 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT`
- `[08:17:10 ERROR]: Command exception: /glw help`
- `[08:17:10 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!`
- `[08:17:10 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***`
- `[08:17:12 ERROR]: Command exception: /glw modules`
- `[08:17:14 ERROR]: Command exception: /glw reload`
- `[08:17:16 ERROR]: Command exception: /glw debug inv-data`
- `[08:17:18 ERROR]: Command exception: /glw lootchest`
- `[08:17:20 ERROR]: Command exception: /glw lootchest help`
- `[08:17:22 ERROR]: Command exception: /glw gang help`
- `[08:17:24 ERROR]: Command exception: /glw rank list`
- `[08:17:26 ERROR]: Command exception: /glw civilian list`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-23T04:16:45.867805700Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[08:16:59 INFO]: [Keystone] Loading server plugin Keystone v1.11.1
[08:16:59 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[08:16:59 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[08:17:01 INFO]: [LuckPerms] Successfully enabled. (took 1655ms)
[08:17:03 INFO]: [Keystone] Enabling Keystone v1.11.1
[08:17:03 INFO]: [Keystone] Keystone 1.11.1 loaded. Serving as a library for dependent plugins.
[08:17:05 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[08:17:07 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[08:17:07 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[08:17:07 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[08:17:07 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[08:17:07 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:17:07 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[08:17:07 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[08:17:07 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[08:17:07 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[08:17:07 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.10.0 from gangland-civilians-0.10.0.jar
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.10.0 from gangland-gadget-0.10.0.jar
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Loaded module gang 0.10.0 from gangland-gang-0.10.0.jar
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.10.0 from gangland-npc-shops-0.10.0.jar
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Loaded module mail 0.10.0 from gangland-mail-0.10.0.jar
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Loaded module turf 0.10.0 from gangland-turf-0.10.0.jar
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Loaded module copsncrooks 0.10.0 from cops-n-crooks-0.10.0.jar
[08:17:07 INFO]: [Gangland.GanglandContext] Runtime modules: 8 loaded, 0 fault(s)
[08:17:07 ERROR]: Error occurred while enabling Gangland_Warfare v0.10.0 (Is it up to date?)
java.lang.IllegalStateException: Failed to instantiate @Configuration class org.luckyraven.gangland.gang.GangMembershipInstaller � check that every constructor parameter is already registered in the container.
Caused by: java.lang.IllegalStateException: Cannot resolve required parameter of type org.luckyraven.gangland.data.gang.GangMembership for constructor in org.luckyraven.gangland.gang.GangMembershipInstaller
[08:17:07 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Disabled module copsncrooks 0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Disabled module turf 0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Disabled module mail 0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Disabled module lootchest 0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Disabled module gang 0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.10.0
[08:17:07 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.10.0
[08:17:07 WARN]: [DeluxeMenus] Could not hook into Vault!
[08:17:07 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[08:17:07 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[08:17:07 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[08:17:07 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[08:17:08 ERROR]: Command exception: /glw
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:08 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
[08:17:10 ERROR]: Command exception: /glw help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:10 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[08:17:10 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[08:17:12 ERROR]: Command exception: /glw modules
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:14 ERROR]: Command exception: /glw reload
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:16 ERROR]: Command exception: /glw debug inv-data
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:18 ERROR]: Command exception: /glw lootchest
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:20 ERROR]: Command exception: /glw lootchest help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:22 ERROR]: Command exception: /glw gang help
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:24 ERROR]: Command exception: /glw rank list
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:26 ERROR]: Command exception: /glw civilian list
org.bukkit.command.CommandException: Cannot execute command 'glw' in plugin Gangland_Warfare v0.10.0 - plugin is disabled.
[08:17:28 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[08:17:28 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[08:17:28 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[08:17:28 INFO]: [Bartizan] Disabling Bartizan v0.4.0
[08:17:28 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[08:17:28 INFO]: [Keystone] Disabling Keystone v1.11.1
[08:17:28 INFO]: [Keystone] Keystone unloaded.
[08:17:28 INFO]: [Vault] Disabling Vault v1.7.3-b131
[08:17:28 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[08:17:28 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[08:17:28 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
