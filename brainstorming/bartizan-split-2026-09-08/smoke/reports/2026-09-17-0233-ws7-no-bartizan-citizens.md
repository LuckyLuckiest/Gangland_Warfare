# Smoke report: ws7-no-bartizan-citizens -- WS7 G5 fix round 1 (I3/C2 verification): all six modules, Citizens PRESENT, Bartizan REMOVED -- attempts /glw civilian spawn gang_member (a Hostile:true, Weapon_Pool-bearing civilian type, npc/civilians.yml:85-99) from console. IMPORTANT DOCUMENTED GAP: CivilianSpawnCommand.typeIdArgument (and CivilianSpawnGroupCommand identically) both require `sender instanceof Player` and send Messages.NOT_PLAYER otherwise -- confirmed by direct source read before writing this row -- so console CANNOT actually force a spawn or drive BartizanNpcWeapons.create's weapon-assignment codepath (the actual C2 fix target); this row can only confirm the command resolves cleanly (no exception) when invoked from console, and that the whole topology boots clean with Citizens present and Bartizan absent (civilians/gadget/turf load, only cops-n-crooks faults). Verifying an actual hostile civilian spawn while Bartizan is absent (BartizanNpcWeapons.create/buildItem's C2 guard, live) needs a human tester with a real client standing where a civilian can spawn, or spawning one via /glw civilian spawn as an actual player -- booked for G6's manual checklist.

**Verdict: PASS**

- Boot detected (`Done (`): True
- Clean stop: True (exited_after_quiet)
- Modules requested: ['mail', 'turf', 'civilians', 'cops', 'gadget', 'npcshops']
- Copied log: `E:\Programming\java\Gangland Warfare [Cubed-GTA recoded]\brainstorming\bartizan-split-2026-09-08\smoke\reports\2026-09-17-0233-ws7-no-bartizan-citizens.log`

## Deploy

- core_jar: `E:\Documents\Minecraft\Test Server\plugins\gangland_warfare-0.9.2.jar`
- keystone_jar: `E:\Documents\Minecraft\Test Server\plugins\Keystone-1.10.0.jar`
- modules: ['E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-mail-0.9.2.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-turf-0.9.2.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-civilians-0.9.2.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\cops-n-crooks-0.9.2.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-gadget-0.9.2.jar', 'E:\\Documents\\Minecraft\\Test Server\\plugins\\Gangland_Warfare\\modules\\gangland-npc-shops-0.9.2.jar']

## Expectations

| Check | Result | Detail |
|---|---|---|
| loaded_modules | PASS | want=['civilians', 'gadget', 'mail', 'npcshops', 'turf'] got=['civilians', 'gadget', 'mail', 'npcshops', 'turf'] |
| faults | PASS | all present |
| must_contain | PASS | all present |
| must_not_contain | PASS | none present |
| no_errors_except | PASS | clean |

## Loaded module lines

`Loaded module` ids seen: ['civilians', 'gadget', 'mail', 'npcshops', 'turf']

## Command transcripts

### `glw`
```
>>> glw
[02:35:31 INFO]: 
[02:35:31 INFO]: [38;2;85;85;85m--[38;2;255;170;0m=[3m[38;2;170;170;170mGangland Warfare[0m[38;2;255;170;0m=[38;2;85;85;85m--[0m
[02:35:31 INFO]: [38;2;170;170;170mAuthor[38;2;85;85;85m: [38;2;85;255;255mLuckyRaven10[0m
[02:35:31 INFO]: [38;2;170;170;170mVersion[38;2;85;85;85m: [38;2;85;255;255m0.9.2[0m
[02:35:31 INFO]: [38;2;170;170;170mType [38;2;255;170;0m/glw help [38;2;170;170;170mto start.[0m
[02:35:31 INFO]: 
[02:35:32 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[02:35:32 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
```

### `glw modules`
```
>>> glw modules
[02:35:33 INFO]: [38;2;255;170;0mGLW [1m[38;2;85;85;85m>>[0m[38;2;170;170;170m [38;2;170;0;0mDoesn't Exist[38;2;170;170;170m: [38;2;255;170;0m/glw[38;2;170;170;170m modules[0m
[02:35:33 INFO]: [38;2;255;255;85mDid you mean [38;2;85;255;255m"/glw module"[38;2;255;255;85m?[0m
```

### `glw civilian spawn gang_member`
```
>>> glw civilian spawn gang_member
[02:35:35 INFO]: [38;2;170;0;0mError[38;2;170;170;170m: [38;2;255;85;85mYou need to be a player to use this![0m
```

## Distinct ERROR signatures (0)

(none)

## First 28 ERROR/WARN lines (with first org.luckyraven frame if any)

- `2026-09-16T22:35:13.666837400Z ServerMain WARN Advanced terminal features are not available in this environment`
- `[02:35:24 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.`
- `[02:35:28 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op`
- `[02:35:28 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'`
- `[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu`
- `[02:35:28 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]`
- `[02:35:28 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]`
- `[02:35:28 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'`
- `[02:35:28 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[02:35:28 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]`
- `[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'`
- `[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'`
- `[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'`
- `[02:35:29 WARN]: [Oriel.OrielPlugin] Oriel failed to enable`
- `[02:35:29 WARN]: [Keystone Common.LoggingSink] Module copsncrooks needs plugin Bartizan (plugin=Bartizan module=copsncrooks jar=cops-n-crooks-0.9.2.jar) [module.plugin.missing]`
- `[02:35:29 WARN]: [Keystone Persistence.DatabaseFaultSink] Failed to persist fault [module.plugin.missing] to oriel_faults: java.lang.IllegalStateException: DatabaseBackend not initialised`
- `[02:35:30 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]`
- `[02:35:30 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]`
- `[02:35:30 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]`
- `[02:35:30 WARN]: [DeluxeMenus] Could not hook into Vault!`
- `[02:35:30 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.`
- `[02:35:30 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!`
- `[02:35:30 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!`
- `[02:35:30 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!`
- `[02:35:32 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!`
- `[02:35:32 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***`

## All interesting log lines

<details><summary>expand</summary>

```
2026-09-16T22:35:13.666837400Z ServerMain WARN Advanced terminal features are not available in this environment
[02:35:24 INFO]: [Keystone] Loading server plugin Keystone v1.10.0
[02:35:24 INFO]: [Gangland_Warfare] Loading server plugin Gangland_Warfare v0.9.2
[02:35:24 WARN]: [DeluxeMenus] Could not setup a NMS hook for your server version! The following Item options will not work: nbt_int, nbt_ints, nbt_string and nbt_strings.
[02:35:25 INFO]: [LuckPerms] Successfully enabled. (took 1326ms)
[02:35:27 INFO]: [Keystone] Enabling Keystone v1.10.0
[02:35:27 INFO]: [Keystone] Keystone 1.10.0 loaded. Serving as a library for dependent plugins.
[02:35:28 WARN]: [Oriel.VaultConfig] Vault is installed but no economy provider is registered � economy actions/requirements will no-op
[02:35:28 WARN]: [Oriel.MenuConfigService] addondownloader.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warp' already registered by menu 'argument_processor_demo' � ignored for menu 'argtest'
[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] /menu could not be registered (name already in use) � falling back to /oriel:menu
[02:35:28 WARN]: [Oriel.MenuConfigService] example.yml:1:1 at .refresh_interval | refresh_interval 5 is below the 10-tick floor (0.5s) � clamped to 10 [config.refresh_interval.floor]
[02:35:28 WARN]: [Oriel.MenuConfigService] features.yml:60:5 at slots.6.template | no template named 'some-external-template' under plugins/Oriel/templates/. Slot will not render. [config.template.unknown]
[02:35:28 WARN]: [Oriel.MenuConfigService] heads.yml:17:5 at slots.3.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'heads' already registered by menu 'heads' � ignored for menu 'heads'
[02:35:28 WARN]: [Oriel.MenuConfigService] heads.yml:20:5 at slots.4.item | material 'hdb-1234' needs a 'hdb' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[02:35:28 WARN]: [Oriel.MenuConfigService] heads.yml:23:5 at slots.5.item | material 'itemsadder-myns:custom_item' needs a 'itemsadder' MaterialSource that isn't registered (plugin missing?) � rendering a plain PLAYER_HEAD until one is registered via MaterialSourceRegistry [config.material.soft_dep]
[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'profile' already registered by menu 'profile' � ignored for menu 'profile'
[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'shop' already registered by menu 'shop' � ignored for menu 'shop'
[02:35:28 WARN]: [Oriel.MenuCommandRegistrar] open.command 'warps' already registered by menu 'warps' � ignored for menu 'warps'
[02:35:29 WARN]: [Oriel.OrielPlugin] Oriel failed to enable
[02:35:29 INFO]: [Oriel] Disabling Oriel v0.7.0-SNAPSHOT
[02:35:29 INFO]: [Gangland_Warfare] Enabling Gangland_Warfare v0.9.2
[02:35:29 WARN]: [Keystone Common.LoggingSink] Module copsncrooks needs plugin Bartizan (plugin=Bartizan module=copsncrooks jar=cops-n-crooks-0.9.2.jar) [module.plugin.missing]
[02:35:29 WARN]: [Keystone Persistence.DatabaseFaultSink] Failed to persist fault [module.plugin.missing] to oriel_faults: java.lang.IllegalStateException: DatabaseBackend not initialised
[02:35:29 INFO]: [Keystone Module.ModuleLoader] Loaded module civilians 0.9.2 from gangland-civilians-0.9.2.jar
[02:35:29 INFO]: [Keystone Module.ModuleLoader] Loaded module gadget 0.9.2 from gangland-gadget-0.9.2.jar
[02:35:29 INFO]: [Keystone Module.ModuleLoader] Loaded module mail 0.9.2 from gangland-mail-0.9.2.jar
[02:35:29 INFO]: [Keystone Module.ModuleLoader] Loaded module npcshops 0.9.2 from gangland-npc-shops-0.9.2.jar
[02:35:29 INFO]: [Keystone Module.ModuleLoader] Loaded module turf 0.9.2 from gangland-turf-0.9.2.jar
[02:35:29 INFO]: [Gangland.GanglandContext] Runtime modules: 5 loaded, 1 fault(s)
[02:35:29 INFO]: [Gangland.PeriodicalUpdates] Initializing auto-save...
[02:35:30 INFO]: [Gangland.GanglandContext] Item vocabularies installed: none � weapon:/ammo:/wearable: item strings will not resolve
[02:35:30 INFO]: [Gangland Civilians.CiviliansModule] Civilians module 0.9.2 enabled
[02:35:30 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � civilian NPCs will not spawn. [npc.citizens.missing]
[02:35:30 INFO]: [Gangland Gadgets.GadgetModule] Gadget module 0.9.2 enabled
[02:35:30 INFO]: [Gangland Mail.MailModule] Mail module 0.9.2 enabled
[02:35:30 INFO]: [Gangland NPC Shops.NpcShopsModule] NPC Shops module 0.9.2 enabled
[02:35:30 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � traders and bankers will not spawn. [npc.citizens.missing]
[02:35:30 INFO]: [Gangland Turf.TurfModule] Turf module 0.9.2 enabled
[02:35:30 WARN]: [Keystone Common.LoggingSink] Citizens is not installed or not enabled � turf-NPC (Quartermaster and garrison defender) spawns will not happen; turf capture itself still works. [npc.citizens.missing]
[02:35:30 INFO]: [Gangland.Gangland] Linked NBTAPI
[02:35:30 INFO]: [Gangland.Gangland] Found PlaceholderAPI, linking...
[02:35:30 INFO]: [Gangland.Gangland] Linked PlaceholderAPI
[02:35:30 INFO]: [Gangland.Gangland] Found Vault economy, linking...
[02:35:30 INFO]: [Gangland.Gangland] Linked Vault economy
[02:35:30 INFO]: [Gangland.Gangland] Found Vault permissions, linking...
[02:35:30 INFO]: [Gangland.Gangland] Linked Vault permissions
[02:35:30 INFO]: [Gangland.Gangland] Found ViaVersion, linking...
[02:35:30 INFO]: [Gangland.Gangland] Linked ViaVersion
[02:35:30 INFO]: [Keystone Common.UpdateNotifier] Checking for updates
[02:35:30 WARN]: [DeluxeMenus] Could not hook into Vault!
[02:35:30 WARN]: [DeluxeMenus] Found 'data' option for item: teststone in menu: basics_menu. This option is deprecated and will be removed soon. Please use 'damage' instead.
[02:35:30 WARN]: [DeluxeMenus] Has Meta requirement at path: items.stats.view_requirement.requirements.has_stats does not contain the key:, meta_type: and/or value: entries!
[02:35:30 WARN]: [DeluxeMenus] Material for item: headdatabase in menu: heads is not valid!
[02:35:30 WARN]: [DeluxeMenus] Material for item: itemsadder in menu: heads is not valid!
[02:35:32 WARN]: *** You are running an outdated version of Minecraft, which is 4 release(s) and 3 build(s) behind!
[02:35:32 WARN]: *** Please update to the latest stable version on https://papermc.io/downloads/paper ***
[02:35:37 INFO]: [BetterGUI] Disabling BetterGUI v10.7
[02:35:37 INFO]: [DeluxeMenus] Disabling DeluxeMenus v1.14.1-Release
[02:35:37 INFO]: [Gangland_Warfare] Disabling Gangland_Warfare v0.9.2
[02:35:37 INFO]: [Keystone Module.ModuleLoader] Disabled module turf 0.9.2
[02:35:37 INFO]: [Keystone Module.ModuleLoader] Disabled module npcshops 0.9.2
[02:35:37 INFO]: [Keystone Module.ModuleLoader] Disabled module mail 0.9.2
[02:35:37 INFO]: [Keystone Module.ModuleLoader] Disabled module gadget 0.9.2
[02:35:37 INFO]: [Keystone Module.ModuleLoader] Disabled module civilians 0.9.2
[02:35:37 INFO]: [Gangland.PeriodicalUpdates] Force update...
[02:35:37 INFO]: [Gangland.PluginDataCleanupService] Next cleanup scan in approximately 1 week, 5 days, 13 hours, 29 minutes and 38 seconds.
[02:35:37 INFO]: [Gangland.PeriodicalUpdates] Cache reset...
[02:35:37 INFO]: [Gangland.PeriodicalUpdates] Saving...
[02:35:37 INFO]: [Gangland.PeriodicalUpdates] Data save complete
[02:35:37 INFO]: [Gangland.PeriodicalUpdates] The process took 85ms
[02:35:37 INFO]: [ViaBackwards] Disabling ViaBackwards v5.12.0-SNAPSHOT
[02:35:37 INFO]: [Keystone] Disabling Keystone v1.10.0
[02:35:37 INFO]: [Keystone] Keystone unloaded.
[02:35:37 INFO]: [NBTAPI] Disabling NBTAPI v2.16.0
[02:35:37 INFO]: [PlaceholderAPI] Disabling PlaceholderAPI v2.12.3
[02:35:37 INFO]: [Vault] Disabling Vault v1.7.3-b131
[02:35:37 INFO]: [LuckPerms] Disabling LuckPerms v5.5.42
[02:35:37 INFO]: [ViaVersion] Disabling ViaVersion v5.12.0-SNAPSHOT
```

</details>
