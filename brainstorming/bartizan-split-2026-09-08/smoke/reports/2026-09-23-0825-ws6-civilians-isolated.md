# Smoke report: ws6-civilians-isolated -- WS6 G3 isolation (2026-09-23, T-53 workaround): gang/turf/mail dropped from the deploy set (turf+mail Depends: [gang], and gang's own GangMembershipInstaller crashes onEnable -- see T-53) so civilians' LocalizedModuleYaml message extraction can still be verified on a plugin that actually boots. civilians has no gang dependency.

**Verdict: FAIL**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['civilians', 'cops', 'gadget', 'npcshops', 'lootchest']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-23-0825-ws6-civilians-isolated.log`

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
| no_errors_except | FAIL | offending (first 5)=['[08:25:42 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission', '[08:25:46 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission'] |

## Loaded module lines

`Loaded module` ids seen: ['civilians', 'gadget', 'lootchest', 'npcshops']

## Command transcripts

### `glw`
```
>>> glw
[08:25:40 INFO]: 
[08:25:40 INFO]: [38;2;85;85;85m--[38;2;255;170;0m=[3m[38;2;170;170;170mGangland Warfare[0m[38;2;255;170;0m=[38;2;85;85;85m--[0m
[08:25:40 INFO]: [38;2;170;170;170mAuthor[38;2;85;85;85m: [38;2;85;255;255mLuckyRaven10[0m
[08:25:40 INFO]: [38;2;170;170;170mVersion[38;2;85;85;85m: [38;2;85;255;255m0.10.0[0m
[08:25:40 INFO]: [38;2;170;170;170mType [38;2;255;170;0m/glw help [38;2;170;170;170mto start.[0m
[08:25:40 INFO]: 
[08:25:41 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[08:25:41 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
```

### `glw reload`
```
>>> glw reload
[08:25:42 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;255mReloading[38;2;170;170;170m the plugin...[0m
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Force update...
[08:25:42 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 d�as, 7 horas, 39 minutos and 33 segundos.
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Saving...
[08:25:42 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission
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
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] The process took 52ms
[08:25:42 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:42 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:42 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader] message_es.yml is missing 116 declared key(s):
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.Player
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.All
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Deposit_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Withdraw_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Upgrade_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Removed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Renamed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.No_Account
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Cash
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Bank_Funds
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Daily_Deposit_Reached
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cap_Exceeded
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Max_Tier
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Insufficient_Funds
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Tier_Missing
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Name_Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Look_At
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Not_Banker
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Create_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Rename_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Already_Has_Account
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cannot_Afford_Creation
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Create_Name_Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Cannot_Afford
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Unchanged
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Weekly_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Monthly_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_On_Cooldown
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Disabled
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Cap_Full
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Gang.Invite_Already_Sent
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Accept_Multiple
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Multiple
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.No_Invite_From
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.None
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Entry
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.None
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Sender
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Target
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Accept_Multiple
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Reject_Multiple
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.No_Request_From
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.None
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Entry
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.None
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Sender
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Target
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Bounty.Below_Minimum
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Created
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Removed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Sell.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Nothing_Valued
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Economy_Error
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Death.Global
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Owned
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Unclaimed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Claim
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Consolidate
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Exit
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Title
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Owned
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Unclaimed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Claim
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Consolidate
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Owned
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Unclaimed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Cooldown
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Protected
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Income.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Income.Invalid
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Set
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Removed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.View
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.Set
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Row
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.Activated
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.BossBar_Title
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Unclaimed_Bossbar_Title
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Cooldown_Actionbar
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Captured_Broadcast
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Given
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Pos_Set
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Create.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Delete.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Cleared
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Select.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Tp.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Show.Started
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Owner
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Region
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Income
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.State
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Row
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Idle
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Contesting
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Cooldown
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Start_Defender
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Half_Defender
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Overlap
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.No_Selection
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Cross_World
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Id_Taken
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Found
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Inside
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Gang_Not_Found
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Gang
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Active
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[08:25:42 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;85mReload has been completed.[0m
```

### `glw civilian list`
```
>>> glw civilian list
[08:25:44 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m No hay civiles activos actualmente.[0m
```

## Distinct ERROR signatures (1)

- (2x) `: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission`

## First 40 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-23T04:25:24.820764Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[08:25:33 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[08:25:37 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[08:25:38 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[08:25:38 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[08:25:38 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[08:25:38 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[08:25:38 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:25:38 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[08:25:38 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[08:25:38 WARN]: [Keystone Common.LoggingSink] Module 'copsncrooks' needs module 'turf', which is not installed or failed to load (jar=cops-n-crooks-0.10.0.jar module=copsncrooks dependency=turf) [module.dependency.missing]`
- `[08:25:38 WARN]: [Keystone Persistence.DatabaseFaultSink] Failed to persist fault [module.dependency.missing] to oriel_faults: java.lang.IllegalStateException: DatabaseBackend not initialised`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]`
- `[08:25:38 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]`
- `[08:25:38 WARN]: [Keystone Persistence.LanguageLoader] message_es.yml is missing 116 declared key(s):`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-23T04:25:24.820764Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[08:25:33 INFO]: [Keystone] Loading server plugin Keystone v1.11.1
[08:25:33 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[08:25:33 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[08:25:34 INFO]: [LuckPerms] Successfully enabled. (took 1082ms)
[08:25:36 INFO]: [Keystone] Enabling Keystone v1.11.1
[08:25:36 INFO]: [Keystone] Keystone 1.11.1 loaded. Serving as a library for dependent plugins.
[08:25:37 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[08:25:38 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[08:25:38 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[08:25:38 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[08:25:38 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[08:25:38 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:25:38 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[08:25:38 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[08:25:38 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[08:25:38 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[08:25:38 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[08:25:38 WARN]: [Keystone Common.LoggingSink] Module 'copsncrooks' needs module 'turf', which is not installed or failed to load (jar=cops-n-crooks-0.10.0.jar module=copsncrooks dependency=turf) [module.dependency.missing]
[08:25:38 WARN]: [Keystone Persistence.DatabaseFaultSink] Failed to persist fault [module.dependency.missing] to oriel_faults: java.lang.IllegalStateException: DatabaseBackend not initialised
[08:25:38 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.10.0 from gangland-civilians-0.10.0.jar
[08:25:38 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.10.0 from gangland-gadget-0.10.0.jar
[08:25:38 INFO]: [Keystone Module.ModuleLoader] Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar
[08:25:38 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.10.0 from gangland-npc-shops-0.10.0.jar
[08:25:38 INFO]: [Gangland.GanglandContext] Runtime modules: 4 loaded, 1 fault(s)
[08:25:38 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:38 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:38 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:38 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[08:25:38 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader] message_es.yml is missing 116 declared key(s):
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.Player
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.All
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Deposit_Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Withdraw_Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Upgrade_Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Removed
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Renamed
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.No_Account
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Cash
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Bank_Funds
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Daily_Deposit_Reached
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cap_Exceeded
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Max_Tier
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Insufficient_Funds
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Tier_Missing
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Name_Empty
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Look_At
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Not_Banker
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Create_Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Rename_Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Already_Has_Account
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cannot_Afford_Creation
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Create_Name_Empty
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Cannot_Afford
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Empty
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Unchanged
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Weekly_Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Monthly_Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_On_Cooldown
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Disabled
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Cap_Full
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Gang.Invite_Already_Sent
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Accept_Multiple
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Multiple
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.No_Invite_From
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.None
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Header
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Entry
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.None
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Sender
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Target
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Accept_Multiple
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Reject_Multiple
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.No_Request_From
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.None
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Header
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Entry
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.None
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Sender
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Target
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Bounty.Below_Minimum
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Created
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Removed
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Sell.Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Nothing_Valued
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Economy_Error
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Death.Global
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Owned
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Unclaimed
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Claim
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Consolidate
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Exit
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Title
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Owned
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Unclaimed
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Claim
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Consolidate
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Owned
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Unclaimed
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Cooldown
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Protected
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Income.Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Income.Invalid
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Set
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Removed
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.View
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.Set
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Empty
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Header
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Row
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.Activated
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.BossBar_Title
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Unclaimed_Bossbar_Title
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Cooldown_Actionbar
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Captured_Broadcast
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Given
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Pos_Set
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Create.Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Delete.Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Cleared
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Select.Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Tp.Success
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Show.Started
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Header
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Owner
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Region
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Income
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.State
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Header
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Row
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Empty
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Idle
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Contesting
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Cooldown
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Start_Defender
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Half_Defender
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Overlap
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.No_Selection
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Cross_World
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Id_Taken
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Found
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Inside
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Gang_Not_Found
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Gang
[08:25:38 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Active
[08:25:38 WARN]: [Keystone Persistence.RepositoryRegistry] Possible circular dependency detected, adding remaining tables
[08:25:38 INFO]: [Gangland.GameplayConfig] keystone-inventory service registered
[08:25:38 INFO]: [Gangland.WiringConfig] Placeholder provider published for external consumers (e.g. Plaque)
[08:25:38 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[08:25:38 INFO]: [Keystone Item.ItemVocabularies] Item vocabulary bartizan contributed 3 converter(s), 3 serializer(s), 3 refresher(s)
[08:25:38 INFO]: [Gangland.GanglandContext] Item vocabularies installed: [bartizan]
[08:25:39 WARN]: [Keystone Persistence.RepositoryRegistry] Possible circular dependency detected, adding remaining tables
[08:25:39 INFO]: [Gangland Civilians.CiviliansModule] Civilians module 0.10.0 enabled
[08:25:39 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]
[08:25:39 INFO]: [Gangland Gadgets.GadgetModule] Gadget module 0.10.0 enabled
[08:25:39 INFO]: [Gangland Loot Chests.LootChestModule] Loot chest module 0.10.0 enabled
[08:25:39 INFO]: [Gangland NPC Shops.NpcShopsModule] NPC Shops module 0.10.0 enabled
[08:25:39 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]
[08:25:39 INFO]: [Gangland.Gangland] Linked NBTAPI
[08:25:39 INFO]: [Gangland.Gangland] Found PlaceholderAPI, linking...
[08:25:39 INFO]: [Gangland.Gangland] Linked PlaceholderAPI
[08:25:39 INFO]: [Gangland.Gangland] Found Vault economy, linking...
[08:25:39 INFO]: [Gangland.Gangland] Linked Vault economy
[08:25:39 INFO]: [Gangland.Gangland] Found ViaVersion, linking...
[08:25:39 INFO]: [Gangland.Gangland] Linked ViaVersion
[08:25:39 INFO]: [Keystone Common.UpdateNotifier] Checking for updates
[08:25:39 WARN]: [DeluxeMenus] Could not hook into Vault!
[08:25:39 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[08:25:39 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[08:25:39 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[08:25:39 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[08:25:39 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
[08:25:41 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[08:25:41 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Force update...
[08:25:42 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 d�as, 7 horas, 39 minutos and 33 segundos.
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Saving...
[08:25:42 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission
java.lang.IllegalStateException: No data supplier set for repository: PermissionRepository
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] The process took 52ms
[08:25:42 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:42 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:42 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[08:25:42 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader] message_es.yml is missing 116 declared key(s):
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.Player
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.All
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Deposit_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Withdraw_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Upgrade_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Removed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Renamed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.No_Account
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Cash
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Bank_Funds
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Daily_Deposit_Reached
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cap_Exceeded
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Max_Tier
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Insufficient_Funds
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Tier_Missing
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Name_Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Look_At
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Not_Banker
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Create_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Rename_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Already_Has_Account
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cannot_Afford_Creation
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Create_Name_Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Cannot_Afford
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Unchanged
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Weekly_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Monthly_Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_On_Cooldown
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Disabled
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Cap_Full
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Gang.Invite_Already_Sent
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Accept_Multiple
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Multiple
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.No_Invite_From
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.None
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Entry
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.None
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Sender
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Target
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Accept_Multiple
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Reject_Multiple
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.No_Request_From
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.None
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Entry
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.None
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Sender
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Target
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Bounty.Below_Minimum
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Created
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Removed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Sell.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Nothing_Valued
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Economy_Error
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Death.Global
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Owned
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Unclaimed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Claim
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Consolidate
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Exit
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Title
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Owned
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Unclaimed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Claim
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Consolidate
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Owned
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Unclaimed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Cooldown
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Protected
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Income.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Income.Invalid
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Set
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Removed
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.View
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.Set
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Row
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.Activated
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.BossBar_Title
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Unclaimed_Bossbar_Title
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Cooldown_Actionbar
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Captured_Broadcast
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Given
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Pos_Set
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Create.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Delete.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Cleared
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Select.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Tp.Success
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Show.Started
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Owner
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Region
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Income
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.State
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Header
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Row
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Empty
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Idle
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Contesting
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Cooldown
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Start_Defender
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Half_Defender
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Overlap
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.No_Selection
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Cross_World
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Id_Taken
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Found
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Inside
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Gang_Not_Found
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Gang
[08:25:42 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Active
[08:25:42 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[08:25:46 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[08:25:46 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[08:25:46 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[08:25:46 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.10.0
[08:25:46 INFO]: [Keystone Module.ModuleLoader] Disabled module lootchest 0.10.0
[08:25:46 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.10.0
[08:25:46 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.10.0
[08:25:46 INFO]: [Gangland.PeriodicalUpdates] Force update...
[08:25:46 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 d�as, 7 horas, 39 minutos and 29 segundos.
[08:25:46 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[08:25:46 INFO]: [Gangland.PeriodicalUpdates] Saving...
[08:25:46 ERROR]: [Keystone Persistence.RepositoryRegistry] Failed to save data for repository: Permission
java.lang.IllegalStateException: No data supplier set for repository: PermissionRepository
[08:25:46 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[08:25:46 INFO]: [Gangland.PeriodicalUpdates] The process took 1ms
[08:25:46 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[08:25:46 INFO]: [Bartizan] Disabling Bartizan v0.4.0
[08:25:46 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[08:25:46 INFO]: [Keystone] Disabling Keystone v1.11.1
[08:25:46 INFO]: [Keystone] Keystone unloaded.
[08:25:46 INFO]: [Vault] Disabling Vault v1.7.3-b131
[08:25:46 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[08:25:46 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[08:25:46 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
