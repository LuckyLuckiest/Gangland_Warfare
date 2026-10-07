# Smoke report: cut-full-regression -- WS2 CUT + WS3 G1-G5 merge (2026-09-22/23): gangland-ui/{inventory-api,hologram-api,lootchest-api} all deleted, loot chests fully module-owned (config/messages/commands) in gangland-features/gangland-lootchest -- seven-module + Bartizan regression boot, /glw reload, /glw lootchest, /glw lootchest help. Console confirms bean graph + module YAMLs extract + commands resolve; cannot place/open a real chest (no player).

**Verdict: PASS**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['mail', 'turf', 'civilians', 'cops', 'gadget', 'npcshops', 'lootchest']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-22-2324-cut-full-regression.log`

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
[23:25:07 INFO]: 
[23:25:07 INFO]: [38;2;85;85;85m--[38;2;255;170;0m=[3m[38;2;170;170;170mGangland Warfare[0m[38;2;255;170;0m=[38;2;85;85;85m--[0m
[23:25:07 INFO]: [38;2;170;170;170mAuthor[38;2;85;85;85m: [38;2;85;255;255mLuckyRaven10[0m
[23:25:07 INFO]: [38;2;170;170;170mVersion[38;2;85;85;85m: [38;2;85;255;255m0.10.0[0m
[23:25:07 INFO]: [38;2;170;170;170mType [38;2;255;170;0m/glw help [38;2;170;170;170mto start.[0m
[23:25:07 INFO]: 
[23:25:08 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[23:25:08 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
```

### `glw help`
```
>>> glw help
[23:25:09 INFO]: 
[23:25:09 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Help [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m23[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[23:25:09 INFO]: 
[23:25:09 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;255;85;85m-[0m Shows main help page.
[23:25:09 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in general menu.
[23:25:09 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m bounty [38;2;255;85;85m-[0m Shows the current bounty or help menu.
[23:25:09 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m bounty help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in bounty menu.
[23:25:09 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m bounty remove [38;2;170;0;170m<[38;2;170;170;170mplayer[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Removes the bounty you set.
[23:25:09 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m bounty set [38;2;170;0;170m<[38;2;170;170;170mplayer[38;2;170;0;170m>[38;2;170;170;170m [38;2;170;0;170m<[38;2;170;170;170mamount[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Sets a bounty on the player head.
[23:25:09 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m rank [38;2;255;85;85m-[0m Shows ranks help page.
```

### `glw modules`
```
>>> glw modules
[23:25:11 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;170;0;0mNo Existe[38;2;170;170;170m: [38;2;255;170;0m/glw[38;2;170;170;170m modules[0m
[23:25:11 INFO]: [38;2;255;255;85mDid you mean [38;2;85;255;255m"/glw module"[38;2;255;255;85m?[0m
```

### `glw reload`
```
>>> glw reload
[23:25:13 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;255mReloading[38;2;170;170;170m the plugin...[0m
[23:25:13 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:25:13 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 d�as, 16 horas, 40 minutos and 1 segundo.
[23:25:13 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:25:13 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:25:14 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:25:14 INFO]: [Gangland.PeriodicalUpdates] The process took 273ms
[23:25:14 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:14 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:14 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:14 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader] message_es.yml is missing 116 declared key(s):
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.Player
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.All
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Deposit_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Withdraw_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Upgrade_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Removed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Renamed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.No_Account
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Cash
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Bank_Funds
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Daily_Deposit_Reached
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cap_Exceeded
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Max_Tier
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Insufficient_Funds
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Tier_Missing
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Name_Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Look_At
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Not_Banker
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Create_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Rename_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Already_Has_Account
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cannot_Afford_Creation
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Create_Name_Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Cannot_Afford
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Unchanged
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Weekly_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Monthly_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_On_Cooldown
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Disabled
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Cap_Full
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Gang.Invite_Already_Sent
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Accept_Multiple
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Multiple
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.No_Invite_From
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.None
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Entry
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.None
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Sender
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Target
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Accept_Multiple
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Reject_Multiple
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.No_Request_From
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.None
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Entry
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.None
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Sender
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Target
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Bounty.Below_Minimum
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Created
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Removed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Sell.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Nothing_Valued
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Economy_Error
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Death.Global
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Owned
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Unclaimed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Claim
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Consolidate
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Exit
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Title
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Owned
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Unclaimed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Claim
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Consolidate
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Owned
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Unclaimed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Cooldown
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Protected
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Income.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Income.Invalid
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Set
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Removed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.View
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.Set
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Row
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.Activated
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.BossBar_Title
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Unclaimed_Bossbar_Title
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Cooldown_Actionbar
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Captured_Broadcast
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Given
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Pos_Set
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Create.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Delete.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Cleared
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Select.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Tp.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Show.Started
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Owner
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Region
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Income
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.State
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Row
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Idle
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Contesting
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Cooldown
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Start_Defender
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Half_Defender
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Overlap
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.No_Selection
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Cross_World
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Id_Taken
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Found
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Inside
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Gang_Not_Found
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Gang
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Active
[23:25:14 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:25:14 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;85;255;85mReload has been completed.[0m
```

### `glw debug inv-data`
```
>>> glw debug inv-data
```

### `glw lootchest`
```
>>> glw lootchest
[23:25:17 INFO]: [38;2;170;0;0mError[38;2;170;170;170m: [38;2;255;85;85m�Necesitas ser un jugador para usar esto![0m
```

### `glw lootchest help`
```
>>> glw lootchest help
[23:25:19 INFO]: 
[23:25:19 INFO]: [38;2;0;170;170mOo[9m[38;2;0;170;170m------[0m [1m[38;2;85;85;85m[[0m[38;2;85;255;255mG[38;2;255;255;255mL[38;2;85;255;255mW[1m[38;2;85;85;85m][0m[38;2;170;170;170m Loot Chest Wand [38;2;85;85;85m[[38;2;170;170;170m1[38;2;170;0;170m/[38;2;170;170;170m1[38;2;85;85;85m] [9m[38;2;0;170;170m------[0m[38;2;0;170;170moO[0m
[23:25:19 INFO]: 
[23:25:19 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest [38;2;255;85;85m-[0m Get a loot chest wand.
[23:25:19 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest edit [38;2;255;85;85m-[0m Edit wand settings while holding it.
[23:25:19 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest help [38;2;170;0;170m<[38;2;170;170;170mpage[38;2;170;0;170m>[38;2;170;170;170m [38;2;255;85;85m-[0m Shows the specified page in loot chest menu.
[23:25:19 INFO]: [38;2;255;170;0m/glw[38;2;170;170;170m lootchest remove [38;2;255;85;85m-[0m Removes the loot chest you are looking at.
```

## Distinct ERROR signatures (0)

(none)

## First 40 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-22T19:24:51.583813700Z ServerMain WARN Advanced terminal features are not available in this environment`
- `WARNING: A terminally deprecated method in sun.misc.Unsafe has been called`
- `WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)`
- `WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2`
- `WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release`
- `[23:25:00 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[23:25:05 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[23:25:05 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[23:25:05 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[23:25:05 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[23:25:05 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[23:25:05 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:25:05 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[23:25:05 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]`
- `[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]`
- `[23:25:05 WARN]: [Keystone Persistence.LanguageLoader] message_es.yml is missing 116 declared key(s):`
- `[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.Player`
- `[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.All`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-22T19:24:51.583813700Z ServerMain WARN Advanced terminal features are not available in this environment
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::allocateMemory has been called by io.netty.util.internal.PlatformDependent0$2 (file:/E:/Documents/Minecraft/Test%20Server/libraries/io/netty/netty-common/4.2.7.Final/netty-common-4.2.7.Final.jar)
WARNING: Please consider reporting this to the maintainers of class io.netty.util.internal.PlatformDependent0$2
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
[23:25:00 INFO]: [Keystone] Loading server plugin Keystone v1.11.1
[23:25:00 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.10.0
[23:25:00 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[23:25:01 INFO]: [LuckPerms] Successfully enabled. (took 1145ms)
[23:25:03 INFO]: [Keystone] Enabling Keystone v1.11.1
[23:25:03 INFO]: [Keystone] Keystone 1.11.1 loaded. Serving as a library for dependent plugins.
[23:25:05 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[23:25:05 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[23:25:05 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[23:25:05 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[23:25:05 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[23:25:05 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:25:05 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[23:25:05 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[23:25:05 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[23:25:05 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[23:25:05 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.10.0
[23:25:05 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.10.0 from gangland-civilians-0.10.0.jar
[23:25:05 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.10.0 from gangland-gadget-0.10.0.jar
[23:25:05 INFO]: [Keystone Module.ModuleLoader] Loaded module lootchest 0.10.0 from gangland-lootchest-0.10.0.jar
[23:25:05 INFO]: [Keystone Module.ModuleLoader] Loaded module mail 0.10.0 from gangland-mail-0.10.0.jar
[23:25:05 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.10.0 from gangland-npc-shops-0.10.0.jar
[23:25:05 INFO]: [Keystone Module.ModuleLoader] Loaded module turf 0.10.0 from gangland-turf-0.10.0.jar
[23:25:05 INFO]: [Keystone Module.ModuleLoader] Loaded module copsncrooks 0.10.0 from cops-n-crooks-0.10.0.jar
[23:25:05 INFO]: [Gangland.GanglandContext] Runtime modules: 7 loaded, 0 fault(s)
[23:25:05 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:05 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:05 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:05 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[23:25:05 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader] message_es.yml is missing 116 declared key(s):
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.Player
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.All
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Deposit_Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Withdraw_Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Upgrade_Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Removed
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Renamed
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.No_Account
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Cash
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Bank_Funds
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Daily_Deposit_Reached
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cap_Exceeded
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Max_Tier
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Insufficient_Funds
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Tier_Missing
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Name_Empty
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Look_At
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Not_Banker
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Create_Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Rename_Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Already_Has_Account
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cannot_Afford_Creation
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Create_Name_Empty
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Cannot_Afford
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Empty
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Unchanged
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Weekly_Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Monthly_Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_On_Cooldown
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Disabled
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Cap_Full
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Gang.Invite_Already_Sent
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Accept_Multiple
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Multiple
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.No_Invite_From
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.None
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Header
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Entry
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.None
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Sender
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Target
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Accept_Multiple
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Reject_Multiple
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.No_Request_From
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.None
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Header
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Entry
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.None
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Sender
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Target
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Bounty.Below_Minimum
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Created
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Removed
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Sell.Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Nothing_Valued
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Economy_Error
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Death.Global
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Owned
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Unclaimed
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Claim
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Consolidate
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Exit
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Title
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Owned
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Unclaimed
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Claim
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Consolidate
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Owned
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Unclaimed
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Cooldown
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Protected
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Income.Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Income.Invalid
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Set
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Removed
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.View
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.Set
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Empty
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Header
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Row
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.Activated
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.BossBar_Title
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Unclaimed_Bossbar_Title
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Cooldown_Actionbar
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Captured_Broadcast
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Given
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Pos_Set
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Create.Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Delete.Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Cleared
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Select.Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Tp.Success
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Show.Started
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Header
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Owner
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Region
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Income
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.State
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Header
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Row
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Empty
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Idle
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Contesting
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Cooldown
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Start_Defender
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Half_Defender
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Overlap
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.No_Selection
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Cross_World
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Id_Taken
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Found
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Inside
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Gang_Not_Found
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Gang
[23:25:05 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Active
[23:25:06 INFO]: [Gangland.GameplayConfig] keystone-inventory service registered
[23:25:06 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:25:06 INFO]: [Gangland.WiringConfig] Placeholder provider published for external consumers (e.g. Plaque)
[23:25:06 INFO]: [Keystone Item.ItemVocabularies] Item vocabulary bartizan contributed 3 converter(s), 3 serializer(s), 3 refresher(s)
[23:25:06 INFO]: [Gangland.GanglandContext] Item vocabularies installed: [bartizan]
[23:25:06 INFO]: [Gangland Civilians.CiviliansModule] Civilians module 0.10.0 enabled
[23:25:06 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]
[23:25:06 INFO]: [Gangland Gadgets.GadgetModule] Gadget module 0.10.0 enabled
[23:25:06 INFO]: [Gangland Loot Chests.LootChestModule] Loot chest module 0.10.0 enabled
[23:25:06 INFO]: [Gangland Mail.MailModule] Mail module 0.10.0 enabled
[23:25:06 INFO]: [Gangland NPC Shops.NpcShopsModule] NPC Shops module 0.10.0 enabled
[23:25:06 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]
[23:25:07 INFO]: [Gangland Turf.TurfModule] Turf module 0.10.0 enabled
[23:25:07 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]
[23:25:07 INFO]: [Cops N Crooks.CopsNCrooksModule] Cops-n-crooks module 0.10.0 enabled
[23:25:07 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � cop NPCs will not spawn. [npc.citizens.missing]
[23:25:07 INFO]: [Gangland.Gangland] Linked NBTAPI
[23:25:07 INFO]: [Gangland.Gangland] Found PlaceholderAPI, linking...
[23:25:07 INFO]: [Gangland.Gangland] Linked PlaceholderAPI
[23:25:07 INFO]: [Gangland.Gangland] Found Vault economy, linking...
[23:25:07 INFO]: [Gangland.Gangland] Linked Vault economy
[23:25:07 INFO]: [Gangland.Gangland] Found Vault permissions, linking...
[23:25:07 INFO]: [Gangland.Gangland] Linked Vault permissions
[23:25:07 INFO]: [Gangland.Gangland] Found ViaVersion, linking...
[23:25:07 INFO]: [Gangland.Gangland] Linked ViaVersion
[23:25:07 INFO]: [Keystone Common.UpdateNotifier] Checking for updates
[23:25:07 WARN]: [DeluxeMenus] Could not hook into Vault!
[23:25:07 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[23:25:07 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[23:25:07 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[23:25:07 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[23:25:07 WARN]: [ViaVersion] There is a newer plugin version available: 5.12.0, you're on: 5.12.0-SNAPSHOT
[23:25:08 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[23:25:08 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[23:25:13 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:25:13 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 d�as, 16 horas, 40 minutos and 1 segundo.
[23:25:13 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:25:13 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:25:14 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:25:14 INFO]: [Gangland.PeriodicalUpdates] The process took 273ms
[23:25:14 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Trader:' block � those keys moved to plugins/Gangland_Warfare/npc/trader_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:14 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Banker:' block � those keys moved to plugins/Gangland_Warfare/npc/banker_settings.yml (extracted by the npc-shops module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:14 WARN]: [Gangland.Settings] settings.yml still has a legacy 'Loot_Chest:' block � those keys moved to plugins/Gangland_Warfare/lootchests/loot_chest_settings.yml and lootchests/lootchest_messages.yml (extracted by the gangland-lootchest module); customised values are NOT auto-migrated and must be copied over by hand. See documentation/migration-0.10.0.md.
[23:25:14 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Max_Health | unknown key 'Max_Health' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Invulnerable | unknown key 'Invulnerable' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:669:4 at Banker.Fallback_Tier_Id | unknown key 'Fallback_Tier_Id' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Respawn_Cooldown | unknown key 'Respawn_Cooldown' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Head_Track_Radius | unknown key 'Head_Track_Radius' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Fallback_Trait_Id | unknown key 'Fallback_Trait_Id' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Max_Mode_Multiplier | unknown key 'Max_Mode_Multiplier' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Sell | unknown key 'Sell' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:646:4 at Trader.Tip_Amount | unknown key 'Tip_Amount' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Countdown_Timer | unknown key 'Countdown_Timer' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Sound | unknown key 'Sound' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Allowed_Blocks | unknown key 'Allowed_Blocks' [config.unknown_key]
[23:25:14 WARN]: [Gangland.Settings] settings.yml:581:4 at Loot_Chest.Rewards | unknown key 'Rewards' [config.unknown_key]
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader] message_es.yml is missing 116 declared key(s):
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.Player
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Bank.Reset_Cap.All
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Deposit_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Withdraw_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Upgrade_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Removed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Renamed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.No_Account
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Cash
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Insufficient_Bank_Funds
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Daily_Deposit_Reached
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cap_Exceeded
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Max_Tier
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Upgrade_Insufficient_Funds
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Tier_Missing
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Name_Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Look_At
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Not_Banker
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Create_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Rename_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Already_Has_Account
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Cannot_Afford_Creation
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Create_Name_Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Cannot_Afford
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Rename_Name_Unchanged
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Weekly_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Banker.Loan_Monthly_Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_On_Cooldown
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Disabled
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Banker.Loan_Cap_Full
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Gang.Invite_Already_Sent
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Accept_Multiple
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Multiple
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.No_Invite_From
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.None
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Pending.Entry
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.None
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Sender
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Invite.Cancel.Target
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Accept_Multiple
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Reject_Multiple
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.No_Request_From
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.None
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Entry
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.None
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Sender
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Gang.Ally.Pending.Cancel.Target
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Bounty.Below_Minimum
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Created
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Admin.Category_Removed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Shop.Sell.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Nothing_Valued
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Shop.Sell.Economy_Error
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Death.Global
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Owned
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Unclaimed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Claim
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Contesting_Consolidate
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Exit
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Title
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Owned
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Unclaimed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Claim
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Enter.Subtitle_Contesting_Consolidate
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Owned
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Presence.Unclaimed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Cooldown
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Blocked.Protected
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Income.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Income.Invalid
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Set
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.PowerupNpc.Removed
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.View
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Garrison.Set
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.List_Row
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Buff.Activated
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.BossBar_Title
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Unclaimed_Bossbar_Title
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Cooldown_Actionbar
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Capture.Captured_Broadcast
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Given
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Wand.Pos_Set
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Create.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Delete.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.SetOwner.Cleared
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Select.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Tp.Success
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Show.Started
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Owner
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Region
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.Income
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Info.State
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Header
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Row
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.List.Empty
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Idle
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Contesting
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Commands.Turf.Status.Cooldown
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Start_Defender
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Information.Turf.Contest_Half_Defender
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Overlap
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.No_Selection
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Cross_World
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Create.Id_Taken
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Found
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Not_Inside
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.Gang_Not_Found
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Gang
[23:25:14 WARN]: [Keystone Persistence.LanguageLoader]   - Errors.Turf.No_Active
[23:25:14 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[23:25:21 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[23:25:21 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[23:25:21 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.10.0
[23:25:21 INFO]: [Keystone Module.ModuleLoader] Disabled module copsncrooks 0.10.0
[23:25:21 INFO]: [Keystone Module.ModuleLoader] Disabled module turf 0.10.0
[23:25:21 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.10.0
[23:25:21 INFO]: [Keystone Module.ModuleLoader] Disabled module mail 0.10.0
[23:25:21 INFO]: [Keystone Module.ModuleLoader] Disabled module lootchest 0.10.0
[23:25:21 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.10.0
[23:25:21 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.10.0
[23:25:21 INFO]: [Gangland.PeriodicalUpdates] Force update...
[23:25:21 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 6 d�as, 16 horas, 39 minutos and 53 segundos.
[23:25:21 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[23:25:21 INFO]: [Gangland.PeriodicalUpdates] Saving...
[23:25:22 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[23:25:22 INFO]: [Gangland.PeriodicalUpdates] The process took 258ms
[23:25:22 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[23:25:22 INFO]: [Bartizan] Disabling Bartizan v0.4.0
[23:25:22 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[23:25:22 INFO]: [Keystone] Disabling Keystone v1.11.1
[23:25:22 INFO]: [Keystone] Keystone unloaded.
[23:25:22 INFO]: [Vault] Disabling Vault v1.7.3-b131
[23:25:22 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[23:25:22 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[23:25:22 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
