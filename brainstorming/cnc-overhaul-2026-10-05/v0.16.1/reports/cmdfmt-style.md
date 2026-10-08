# 0.16.1 command formatting: the cops-n-crooks style rule

Input for the parallel editors. Base 00fb5aa5 / 2a2369f4 (worktree branch cnc-0.16.1-cmdfmt). Owner report T-188: "Commands don't have proper formatting similar to the others."

## 1. What the rule is

Every reply a cops-n-crooks command or setup tool sends goes through a helper that adds the core plugin prefix by **kind**. Colour codes live in the module's YAML value (house convention; core Messages values do the same). The helper adds the prefix, the YAML adds the body colour.

| Kind of line | Helper (module class) | Prefix it renders | Body colour in the YAML value |
|---|---|---|---|
| Success, state change, command reply, list header, empty list | `Style.COMMAND`: `command(path, fallback)` (LocalizedModuleYaml) | `GLW >> ` (Messages.COMMAND_PREFIX) | `&a` for success, `&7` for neutral |
| Failure, bad input, unknown id, permission, not a player | `Style.ERROR`: `error(path, fallback)` | `Error: ` (Messages.ERROR_PREFIX) | `&c` |
| Row under a list, info body line, hover text, item name/lore | `Style.NONE`: `color(path, fallback)` | none | `&b` labels, `&f` values, `&e(&btp&e)` for the teleport action |
| Missing or bad arguments | `usage(fullUsage)` (see below) | `GLW >> ` + `&4Missing Arguments&7: ` | operand colouring comes from `GanglandChatUtil.commandDesign` |

Rules that follow:

1. **Never** `sender.sendMessage(color(...))`, never a hard-coded English literal, never a `Messages.*` constant whose type is `PREFIX` (`[GLW]`) or `INFORMATION` (`[Information]`) inside a cops command. Those two prefixes are not used by /glw cop commands any more. The `[GLW]` list headers of the jail and spawner lists move to the module's own `COMMAND` keys.
2. Values use `&f`. `&e` is reserved for the clickable `(&btp&e)` action.
3. Lists: one `COMMAND` header with the count `(&f%count%&8)`; then one `NONE` row per item, `" &b- &f<id or name> &e(&btp&e)"` with a RUN_COMMAND click and a SHOW_TEXT hover when a teleport exists. Empty = one `COMMAND` line `&7No ... yet.`
4. Usage: a command with a missing or bad operand calls `usage("/glw <full command> <operands>")`. Pass the full usage starting with `/glw`, not only the operands, so `commandDesign` colours the command name. (Core passes operands only; that is ambiguous for nested roots like `cop setup`, so the cops module deviates on purpose.)
5. Bare roots: a root with a `<root>_help` entry in `commands.json` (`cop_help`, `jail_help`) shows its help page, as CopCommand does. A root without one (`cop setup`) sends `usage(...)`.
6. Errors for unknown ids use the module's own `*_UNKNOWN` ERROR key, not `LOCATION_NOT_FOUND` or `JAIL_NOT_JAILED` (census: wrong key).
7. Permission and not-player stay on `SetupMessages.Key.NO_PERMISSION` / `NOT_PLAYER` (ERROR style) and the core `Messages.COMMAND_NO_PERM` / `NOT_PLAYER` for core-derived commands. Nothing new is added there.
8. `information(...)` / `Style.INFO` is NOT used by cops commands.

## 2. Where the prefix comes from inside the module

The module cannot import gangland-impl, but it can import gangland-api. `LocalizedModuleYaml` (gangland-api, `file/configuration/LocalizedModuleYaml.java`) already has the protected helpers `color`, `command`, `error`, `information`, `prefix` and `raw`. They call `GanglandChatUtil.commandMessage/errorMessage/...`, which prepend `Messages.COMMAND_PREFIX` / `Messages.ERROR_PREFIX` (gangland-api, values from the core `message_en.yml`). So no prefix string is duplicated in the module, and **no gangland-api change is needed**.

`GanglandChatUtil.setArguments(arguments, command)` = `color(arguments + commandDesign(command))`. So `usage` is `setArguments(command(ARGUMENTS_MISSING...), fullUsage)`.

## 3. Group 1 creates (exact names)

**`SetupMessages` (existing, edited)**
- `enum Style { COMMAND, ERROR, NONE }`; `Key(String path, String fallback, Style style)`.
- `format(Key key, Map<String,String> values)` keeps its signature. It now dispatches: `COMMAND` -> `command(key.path, key.fallback)`, `ERROR` -> `error(...)`, `NONE` -> `color(...)`, then replaces `%name%` placeholders.
- `public String usage(String fullUsage)` = `GanglandChatUtil.setArguments(command(Key.ARGUMENTS_MISSING.path, Key.ARGUMENTS_MISSING.fallback), fullUsage)`.
- Key table (path under `Setup.Messages.`, style):
  - `ARGUMENTS_MISSING` "Arguments_Missing", `&4Missing Arguments&7: ` (COMMAND; used only by usage())
  - `LIST_HEADER` "List_Header", `&7Setup %kind% &8(&f%count%&8)&7:` (COMMAND) NEW
  - `NOT_PLAYER` ERROR; `NO_PERMISSION` ERROR
  - `WAND_NAME`, `WAND_LORE` NONE (item text, never prefixed)
  - `WAND_GIVEN` COMMAND; `MODE_SET` COMMAND; `MODE_UNKNOWN` ERROR
  - `POS_SET` COMMAND (SetupWandListener uses format, so it changes with no edit there)
  - `INCOMPLETE` ERROR; `NAME_MISSING` ERROR
  - `STATION_SAVED` COMMAND; `STATION_DUPLICATE` ERROR
  - `REGION_SAVED` COMMAND; `POINT_SAVED` COMMAND
  - `LIST_EMPTY` COMMAND
  - `REMOVED` COMMAND; `NOT_FOUND` ERROR; `TELEPORTED` COMMAND
  - `NO_LOCATION` ERROR; `LINKED` COMMAND; `UNLINKED` COMMAND
  - `UNKNOWN_JAIL` ERROR; `UNKNOWN_KIND` ERROR; `BAD_ID` ERROR
  - `USAGE` is REMOVED (replaced by `usage(...)`).
- `setup.yml`: `Setup.Messages.Usage` removed, `Arguments_Missing` and `List_Header` added, block style, Capitalized_Underscore keys.

**`CommandMessages` (NEW)**: `org.luckyraven.gangland.copsncrooks.command.CommandMessages`, `extends LocalizedModuleYaml`, `BASE_NAME = "commands"`, file `src/main/resources/copsncrooks/commands.yml`. Same shape as SetupMessages: `enum Key { ...; Key(path, fallback, style) }`, `format(Key, Map<String,String>)`, `usage(String fullUsage)` (same body as above), `wandItem`-style helpers not needed. Key table (path under the top level, dotted = nested block):
  - `ARGUMENTS_MISSING` "Arguments_Missing", `&4Missing Arguments&7: ` (COMMAND)
  - `COP_LIST_HEADER` "Cop.List.Header", `&7Players being chased by cops &8(&f%count%&8)&7:` (COMMAND)
  - `COP_LIST_EMPTY` "Cop.List.Empty", `&7No player is being chased by cops.` (COMMAND)
  - `COP_TARGET_HEADER` "Cop.Target.Header", `&7Player &f%player% &7is being chased by cops:` (COMMAND)
  - `COP_TARGET_ROW` "Cop.Target.Row", ` &b- &f%callsign%&7 (%uuid%)` (NONE)
  - `SPAWNER_LIST_HEADER` "Spawner.List.Header", `&7Cop spawners &8(&f%count%&8)&7:` (COMMAND)
  - `SPAWNER_LIST_EMPTY` "Spawner.List.Empty", `&7No cop spawners are placed yet.` (COMMAND)
  - `SPAWNER_LIST_ROW` "Spawner.List.Row", ` &b- &f#%id% &e(&btp&e)` (NONE)
  - `SPAWNER_LIST_HOVER` "Spawner.List.Hover", `%world% - %x%, %y%, %z%` (NONE)
  - `SPAWNER_INFO_HEADER` "Spawner.Info.Header", `&7Cop spawner &f#%id%&7:` (COMMAND)
  - `SPAWNER_INFO_LINE` "Spawner.Info.Line", ` &bWorld: &f%world% &bX: &f%x% &bY: &f%y% &bZ: &f%z%` (NONE)
  - `SPAWNER_UNKNOWN` "Spawner.Unknown", `&cNo cop spawner with id %id%.` (ERROR)
  - `JAIL_LIST_HEADER` "Jail.List.Header", `&7Jails &8(&f%count%&8)&7:` (COMMAND)
  - `JAIL_LIST_EMPTY` "Jail.List.Empty", `&7No jails are placed yet.` (COMMAND)
  - `JAIL_LIST_ROW` "Jail.List.Row", ` &b- &f#%id% &e(&btp&e)` (NONE)
  - `JAIL_LIST_HOVER` "Jail.List.Hover", `%world% - %x%, %y%, %z%` (NONE)
  - `JAIL_INFO_HEADER` "Jail.Info.Header", `&7Jail &f#%id%&7:` (COMMAND)
  - `JAIL_INFO_LINE` "Jail.Info.Line", ` &bWorld: &f%world% &bX: &f%x% &bY: &f%y% &bZ: &f%z% &bCapacity: &f%capacity%` (NONE)
  - `JAIL_UNKNOWN` "Jail.Unknown", `&cNo jail with id %id%.` (ERROR)

  (Groups 3 and 4 may reword a fallback only by editing the YAML value and the fallback together; the key names are fixed.)

**Wiring (group 1)**
- `config/RegistryModuleConfig.java`: build `new CommandMessages(fileManager)` beside `new SetupMessages(fileManager)` (L57) and expose it as a `@Bean` the same way `SetupMessages` is exposed. Constructor injection carries it into the command classes.
- `config/CopsNCrooksYamlConfig.java` L36: add `"commands"` to the list of base names copied out of the module jar (next to `"setup"`).

**`commands.json` (group 1, shared)**
- `cop_list`: usage `/glw cop list <player>`; description says the player is optional.
- `cop_spawner_teleport` and `jail_teleport`: description notes the `tp` alias.
- No new entries are needed: all `cop_setup_*` entries exist (wand, mode, save, list, remove, tp, link).

## 4. Group partition (disjoint, shared files only in group 1)

- G1: `setup/SetupMessages.java`, `resources/copsncrooks/setup.yml`, `resources/commands.json`, NEW `command/CommandMessages.java`, NEW `resources/copsncrooks/commands.yml`, `config/RegistryModuleConfig.java`, `config/CopsNCrooksYamlConfig.java`.
- G2: `setup/SetupCommands.java`, `command/cops/setup/SetupCommand.java`.
- G3: `command/cops/CopListCommand.java`, `command/cops/spawner/CopSpawnerListCommand.java`, `command/cops/spawner/CopSpawnerInfoCommand.java`, `command/jail/JailListCommand.java`, `command/jail/JailInfoCommand.java`.
- G4: `command/jail/JailCommand.java`, `command/jail/JailSetExitCommand.java`, `command/jail/JailTeleportCommand.java`, `command/jail/JailThrowCommand.java`, `command/cuff/CuffCommand.java`, `command/cuff/UncuffCommand.java`, `command/cops/spawner/CopSpawnerCommand.java`.
- No edit: `listener/setup/SetupWandListener.java` (POS_SET goes through format), `command/cops/CopCommand.java`, `command/jail/JailCreateCommand.java`, `JailReleaseCommand.java`, `JailRemoveCommand.java`, `CopSpawnerSetCommand.java`, `CopSpawnerRemoveCommand.java`, `CopSpawnerTeleportCommand.java` (only its `tp` alias, which is commands.json, group 1).

## 5. Out of this lane (report only)

- Census: the module has no contacts, bribe, wanted or debug command classes; `commands.json` has none. Nothing to format there.
- Core, not cops (census only): none read.
- `JailThrowCommand`: the check order makes "already jailed" unreachable when no empty jail exists. A behaviour bug, not formatting; record in the docket, do not fix here.
- `JailRemoveCommand`: reports "removed" without checking the jail existed. Behaviour; docket.
- `JailCreateCommand`: radius `5` is a hard-coded literal in user text. Docket.
- Positional operands (`cop setup save <name>`, `jail set <id>`) read `args[n]`; house rule wants chained `OptionalArgument` nodes with tab completion. Not touched in 0.16.1; docket.
- Owner question (PLAN §2): none. If the owner wants a different prefix than `GLW >>`, change `Messages.COMMAND_PREFIX` in the core YAML; this rule follows it automatically.

## 6. Tests and verification

- Settings caveat: `GanglandChatUtil.color()` reads `Settings.getMoneySymbol()`. A unit test that renders strings must initialise Settings or assert on the key tables and the YAML text rather than the rendered string.
- Each group adds at most one test file, named `<Group>StyleTest`, under the cops-n-crooks test tree. Verify red on the base commit first where the test can express the old behaviour.
- Run: `mvn -q -o -pl gangland-features/cops-n-crooks -am test` (single class: add `-Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`). PowerShell tool only; the Bash tool blocks mvn. Never `mvn install`.
- Braces on their own lines in every method body. YAML block style, Capitalized_Underscore keys, `&` codes only (never `§`), Spigot API only.

## 7. Result (0.16.1, T-188)

**Files changed (cops-n-crooks lane, 29 tracked files plus new files):**
- Command classes now route every line through `CommandMessages` (COMMAND/ERROR/NONE by kind): `command/cops/*`, `command/cops/spawner/*`, `command/jail/*`, `command/cuff/*`, `setup/SetupCommands.java`, `setup/SetupMessages.java`, `config/CopsNCrooksYamlConfig.java`, `config/RegistryModuleConfig.java`.
- New: `command/CommandMessages.java` (the key table and the prefix helper), `resources/copsncrooks/commands.yml` (the module's reply text, colour in the YAML value).
- `resources/copsncrooks/setup.yml`: `Row_Label` now `" &b- &7%kind% &f#%id% &f%name% "` (judge round 1: the kind was added; a console gets the label and the place as one plain line).
- `resources/commands.json`: existing entries reworded only, no entry added. The judge round changed `cop_list` usage to `/glw cop list [player]` and `cop_setup_list` description to say the place is in the hover.
- Tests: `CommandMessagesTest` (new, `everyKeyIsShippedWithItsFallback`), updated `CopSpawnerListCommandTest`, `JailListCommandTest`, `SetupCommandsTest`.
- Style doc: rule 3 reads `&f<id or name>`; key table rows for `SPAWNER_LIST_ROW`, `JAIL_LIST_ROW`, `COP_TARGET_ROW` match the code.

**Ruling:** values use `&f`, `&e` is reserved for the clickable action (rule text wins over the earlier table). `Cop.Target.Row` `(%uuid%)` stays `&7`, decorative, flagged for owner.

**Before / after (3 samples):**
1. Cop list header. Before: `&7Players being chased by cops:` (raw, no prefix, no count). After: `GLW >> &7Players being chased by cops &8(&f2&8)&7:` (COMMAND helper).
2. Cop list row. Before: `&b- &r<name>`, sent raw. After: ` &b- &f<name>` (NONE helper, RUN_COMMAND click and SHOW_TEXT hover).
3. Target not chased. Before: `Messages.COP_TARGET_NOT_CHASED` with `%target%` replaced, no prefix. After: `Error: &cPlayer &f<target>&c is not being chased by cops.` (ERROR helper).

**Tests:** `mvn -o -pl gangland-features/cops-n-crooks -am test`, BUILD SUCCESS. cops-n-crooks 1012 run, 0 failures, 0 errors, 0 skipped. Upstream modules in the `-am` run all green (core 124, item/infra 48, UI/sign 66, api 154, gangs 142, civilians 91, turf 117). `CommandMessagesTest` was red before the Java fallback fix and green after.

**Other-module follow-ups (census only, not fixed):**
- gangland-turf: `TurfWandCommand` (bare `TURF_WAND_GIVEN`, `COMMAND_NO_PERM`); same bare `COMMAND_NO_PERM` in TurfPos1/Pos2/Create/Delete/SetOwner/Income/Garrison/Buff/PowerupNpc commands.
- gangland-npc-shops: `TraderRemoveCommand` (`TRADER_LOOK_AT`, `TRADER_NOT_A_TRADER`, `TRADER_REMOVED`); `TraderCreateCommand`, `TraderEditTraitCommand`, `TraderEditShopCommand`, `TraderEditNameCommand`; `BankerRemoveCommand`, `BankerEditNameCommand`, `BankerCreateCommand`.
- gangland-mail: `GangInviteCommand` (about 12 bare sends); `GangInviteCancelCommand`, `GangInviteAcceptCommand`, ally/* commands.
- gangland-lootchest: `LootChestRemoveCommand`; `LootChestWand` (ChatUtil.color, no prefix); `LootChestWandEditCommand`.
- gangland-civilians: `CivilianListCommand` (raw `listEmpty()`), `CivilianGroupsCommand`, `CivilianSpawnerListCommand`; `CivilianSpawnerInfoCommand`/`RemoveCommand` (core MUST_BE_NUMBERS, LOCATION_NOT_FOUND).
- gangland-gang: `GangDebugContribution` (literal English "Not in a gang..." line 66, raw toString dumps); the gang rank commands (`RankInfoCommand` and siblings) use bare Messages sends, prefix behaviour per key not verified.
- gangland-gadget: `CarInfoCommand`, `CarListCommand`, `CarGiveCommand`, `JetpackGiveCommand`, `GrappleGiveCommand`; sends not read in depth, deviation unconfirmed.

**Not pushed.** Committed on `cnc-0.16.1-cmdfmt`.
