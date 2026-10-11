# Migrating a server to Gangland 0.17.0 (Gang & Turf I)

[← Back to Documentation Index](./README.md)

This guide covers the upgrade from 0.16.1 to 0.17.0. Parts 1, 2 and 3 are the Gang & Turf I waves (W0, W1 and W2); all three
ship together in 0.17.0. Part 4 lists the release fixes made after the waves.

Gangland 0.17.0 needs Keystone 1.15.2 (the `keystone.version` in the root `pom.xml`). Nothing needs a new dependency.
`gangland-api` is `2.4` (adds `Messages.USER_DATA_LOAD_FAILED`) and no module's `Host_Api` changes. The new files are created on first boot, and the
first boot also refreshes the shipped menus (see Part 4, item 12). Read the notes below before you start the server on the
new jar.

## Part 1 - Trustworthy foundations

### 1. Turf ids and the new `turf_sequence` table

- New table `turf_sequence` (one row, `id = 1`), created automatically on first boot.
- Turf ids are never reused. On the first boot after the upgrade the server reserves `max(existing turf ids)`, so every turf
  that exists today keeps its id.
- The one window left: a turf deleted before the upgrade whose id was never stored can be handed out once more on the first
  boot after the upgrade. From then on the id stays reserved, including across restarts.

### 2. Gang disband, admin turf commands and captures

- Disbanding a gang releases every turf it owned and cancels any capture it was running.
- `/glw turf setowner` and `/glw turf delete` (admin) cancel a running capture, so boss bars and guards are cleaned up.
- A capture whose gang was disbanded part-way through is cancelled.
- `TurfOwnerChangedEvent` now also fires on captures (before `TurfCapturedEvent`), carries `getCause()`
  (`CAPTURE`, `ADMIN`, `INACTIVITY`, `ORPHANED`, `GANG_DISBANDED`) and fires only when the owner actually changes.
- `TurfCaptureFailedEvent.Reason.CANCELLED` is now fired.

### 3. Inactivity auto-release

- The inactivity auto-release first runs 5 minutes after boot, then every 24 hours.
- Server downtime counts as gang inactivity. A server that was down longer than `Capture.Inactivity_Auto_Release_Days`
  (`turf/turf_settings.yml`, default 4) releases those turfs 5 minutes after it comes back.

### 4. Turf NPC config reloads

- `turf/turf_npcs.yml` (Quartermaster type and radius, guard type, radius and lifespan, the `Cop_Response` block) now reloads
  with `/glw reload`. No restart needed.

### 5. Alliances are symmetric

- An alliance stored on only one side now counts for both gangs. Friendly fire is blocked both ways, and turf, guard and
  Quartermaster logic treat the two as allies.
- `Gang.removeAlly(gang)` drops only that gang's side. The in-game commands already drop both sides through `GangManager`, so
  nothing changes for players. Addon code that calls `removeAlly` directly must call it on both gangs.
- `new Gang()` (no-arg) is removed. Use `new Gang(int id)`.

### 6. Gang transfer confirmation

- `/glw gang transfer` and `/glw gang leave` confirmations lapse on their own. The effective window is **30 seconds**: the
  confirm lock is Keystone's fixed `ConfirmArgument` expiry, which has no public setting. A 60-second target needs an upstream
  Keystone change (tracked as a new docket row, see the wave log).

### 7. Gang invites, decline and offline expiry

- New verb `/glw gang decline [gang]`. Accepting one gang's invite cancels the player's other gang invites.
- Invites to online players expire after 60 seconds.
- Invites to offline players expire after `Invite_Offline_Expiry_Hours` (default `72`) in the new `mail/mail_rules.yml`.
  Set it to `0` for the old behaviour (the invite waits until the player next logs in).
- Invites to offline players already stored with `expiresAt = 0` keep never expiring. There is no backfill.
- New files on first boot: `plugins/Gangland_Warfare/mail/mail_rules.yml`, `mail/mail_messages.yml`, `mail/mail_messages_es.yml`.
  They ship in the mail module jar and are copied to the data folder, not to `modules/`.
- Help text: `/glw gang rename <name>` replaces `/glw gang name <name>` (the verb has always been `rename`).

### 8. Ranks

- A rank has exactly the permissions listed on it. Parent ranks order promotion and demotion only; they do not pass
  permissions down. See [ranks](./features/ranks.md).

### 9. Addon authors

- Turf module internals: `TurfDefenderConfig` and `CopGuardConfig` are no longer beans. Read them through
  `TurfNpcsConfigLoader` (`getDefenderConfig` / `getCopGuardConfig`). `TurfPowerupManager` and `TurfCopGuard` take suppliers.
- `ActiveBuffRepositoryContract` gained `deleteExpired(long)`. Any other implementation must add it.
- `CaptureService`, `TurfIncomeDistributor`, `InactivityReleaseTask`, `TurfSetOwnerCommand`, `TurfDeleteCommand` and
  `TurfCommand` gained a `TurfOwnership` parameter. `TurfOwnerChangedEvent` has a 4-argument constructor.
- `gangland-api` 2.4 adds `Messages.USER_DATA_LOAD_FAILED` (additive; modules built for 2.0-2.3 load unchanged), no `Host_Api` change, no config change beyond the keys above.

### 10. By design

- `/glw turf buff <powerup>` is an admin tool and activates a buff for free.

## Part 2 - See the city

### 1. Player commands are open by default (CM-37)

- `/glw`, `/glw map ...`, `/glw turf info|list` and these gang player verbs now default to `true` for every player:
  create, leave, list, info, members, balance, deposit, withdraw, invite, invite cancel, accept, decline, kick, promote,
  demote. The gang menus `gang_info`, `gang_stat` and `alliance_stat` also default to `true` through their
  `gangland.inventory.*` nodes (`gang_info` since T-226). `/glw gang` opens the gang menu for members; gangless players
  get the onboarding screen from the menu's `Open.Condition` (see Part 3, item 5).
- A server that wanted `/glw` op-only negates `gangland.command.main` for the default group.
- Transfer, delete, rename, description, display, color and ally stay op until a later release.
- The gang `list` alias of `members` is gone. `/glw gang list [page]` now lists every gang (most members first); use
  `/glw gang members` for your own gang's members.

### 2. Turf admin namespace (TF-30)

- `gangland.turf.admin` now grants every turf admin command. `/glw turf tp|select|show|status` need it, as do the other
  admin verbs (wand, pos1, pos2, create, rename, delete, setowner, income, garrison, buff, powerupnpc). Players without
  it get `Only turf admins can do that.`
- `/glw turf wand` now also clears the active turf selection, so `tp` and `show` with no id no longer fall back to
  the stand-in turf until `select` is run again.
- `/glw turf rename <name>` is new (admin). Names must be unique, ignoring case and colour codes.

### 3. Turf creation (TF-29, D3 A)

- New turfs snap outward to whole chunks (`Create.Snap_To_Chunks`, default `true` in `turf/turf_rules.yml`). Each side,
  measured after snapping, must be `Create.Min_Side_Blocks` (16) to `Create.Max_Side_Blocks` (512) blocks.
- Existing turfs are unchanged.
- A corner in another world than the other corner is refused with `Turf.Selection_Other_World`.

### 4. New files on first boot (nothing to migrate; defaults shown)

- `plugins/Gangland_Warfare/turf/turf_rules.yml`, `turf/turf_map.yml`, `turf/turf_messages.yml`.
- `plugins/Gangland_Warfare/gang/gang_rules.yml`, `gang/gang_messages.yml`.
- A server whose `turf_messages.yml` predates a key falls back to the shipped wording in code, so no line goes blank.

### 5. Map and HUD (D6 A)

- `/glw map` prints a chat map of the turfs around you (10 lines by default). Variants: `auto`, `hud`, `text`, `legend`.
  Auto is off by default and forgotten on quit. Bedrock players (`Map.Bedrock_Text_Form`) get the text list.
- The action-bar turf line (`turf_map.yml` `Hud:`, priority 9) replaces the persistent "territory" boss bar. The
  `TURF_PRESENCE_*` message keys are now unused and can be removed from custom files.
- Six placeholders for scoreboards: `%gangland_turf-name%`, `%gangland_turf-owner%`, `%gangland_turf-state%`,
  `%gangland_turf-relation%`, `%gangland_turf-nearest-name%`, `%gangland_turf-nearest-distance%`.
- `turf_messages.yml` `Map.Hover_Name` and `Map.Text_Here` are new. Older files fall back to the in-code wording.

### 6. One reason everywhere

- Turf entry chat lines and the info card show one reason sentence (`shielded: ...` or `attackable`). Allied turfs no
  longer print the "protected" line. Chat lines appear only for a rival or unclaimed turf that cannot be captured yet.
- `/glw turf info` accepts `[id|name]` and tab-completes both. The card gains a `Capture:` line.

### 7. Gangs

- `/glw gang info [gang]` shows a public card (name, description, members online/total, owner, allies, created date).
  It never shows the bank balance. It accepts a name (any case) or a numeric id.
- Gang names must match `gang_rules.yml` `Name_Pattern` (default 3 to 16 letters, digits or underscores). Duplicates are
  always refused, so `Name_Duplicates` in `gang_settings.yml` is ignored. Legacy names with spaces cannot be reached by
  the list click or `gang info`.
- Players without a gang see the onboarding text (`gang/gang_messages.yml` `Onboarding:`) on `/glw gang`. Players with
  a gang see help. The gang menu needs `gangland.inventory.gang_info`, which defaults to true.
- Fresh installs get the `member -> officer -> owner` ranks with default nodes from `gang_rules.yml`
  (`Ranks.Preset`, `Ranks.Preset_Permissions`). Existing rank trees are untouched.
- A renamed `Rank.Head` logs a warning and keeps the persisted root. A renamed `Rank.Tail` logs a warning and changes
  nothing; set `Rank.Tail` back to an existing rank name, or a gang created meanwhile gives its creator no rank for
  deposit, withdraw and disband.
- `gangland.gang.*` nodes appear in `/glw permission` search, help and LuckPerms completion, default OP.
- `rank permission add` refuses unknown nodes and applies accepted nodes to online members at once. Node names are
  saved lower case.

### 8. Jars

- The core jar and the gang and turf module jars must be updated together: the inventory YAML clicks and the `turf-`
  placeholder prefix live in the core jar, and the gang verbs they point at live in the gang module.

## Part 3 - Fair fights

### 1. Capture window flipped (D1): turfs can be attacked while their owners are online

- **Behaviour change.** A turf can now be attacked while one of its owners is online, and for
  `Capture.Post_Logoff_Protection_Minutes` after the last one leaves. Before 0.17.0 a turf was only attackable when all
  its owners were offline. `Capture.Post_Logoff_Protection_Minutes` (`turf/turf_settings.yml`, default 10) keeps its name; it now means "the window after the last owner logs off" in both modes.
- **Wake-up shield.** After the first qualifying owner logs in, the turf is shielded for 2 minutes (the boss bar and map
  say "waking up"). The wake-up record is in memory only: after a restart the first qualifying login wakes the gang again.
- **Qualifying members.** Only members who joined their gang at least 24 h ago keep its turfs open and wake them up
  (`Capture.Window_Min_Member_Age_Hours`). In a gang younger than that every member counts; `0` counts everyone.
- **Small servers keep the old rule** with one setting in `turf/turf_rules.yml`: `Capture.Window: OWNERS_OFFLINE`. The
  qualifying-member age rule still applies in that mode. For the exact pre-0.17.0 behaviour set both
  `Capture.Window: OWNERS_OFFLINE` and `Capture.Window_Min_Member_Age_Hours: 0`.
- **Camping line.** An owner who logs in during a closed window gets one line per owned turf that has rivals inside.

### 2. One capture bar on every turf (D5)

- An unclaimed capture is one bar at `Capture.Duration_Seconds` (`turf/turf_settings.yml`). The `Unclaimed_Phase1_Seconds`
  and `Unclaimed_Phase2_Seconds` keys there are no longer read; leave them or delete them.
- The bigger crew inside wins the start (ties do not start), attackers count after `Capture.Presence_Seconds` (15 s),
  guards hold the bar, each bar takes at least `Capture.Min_Capture_Seconds` (60 s), and a contest ends STALLED after
  `Capture.Max_Contest_Minutes` (10). STALLED sets no cooldown, so a contest can restart at 0% on the next tick if the
  attackers still dominate.
- Sides are frozen at the start: a gang that joins the fight later is a bystander until the next contest.
- Entering a contested turf shows "`%gang%` capturing". `message/message_en.yml` and `message/message_es.yml` are rewritten on the
  first 0.17.0 boot (their `Config_Version` changes); the old copies are kept as `*-old.yml`, so copy your edits back by hand. `Commands.Turf.Enter.Contesting_Claim` and `Subtitle_Contesting_Claim` are no longer read.
- `turf_messages.yml` drops `Turf.Status_Phase_Claim` and `Turf.Status_Phase_Consolidate`; `/glw turf status` shows no
  phase any more.

### 3. New file content

- `turf/turf_rules.yml` gains the `Capture`, `Turf_Cap`, `Income` and `Garrison` sections (defaults are in the file).
  Existing keys did not move: the plan's `Turf.Capture.*` to `turf_rules.yml` move was dropped.
- `gang/gang_rules.yml` gains `Progression` (`Max_Level: 20`, `Base_Experience: 200`). Gang XP comes only from turf income.
- `turf/turf_messages.yml` gains `Alerts.*`, `Income.*`, `Digest.*`, `Quartermaster.*` and `Rules.*` sections, and
  `gang/gang_messages.yml` gains `Bounty.*`, `Progression.Fee_Refund`, `Social.*` and `List.Row`. Every new key has an
  in-code fallback equal to the shipped line, so an old file still shows text; copy the shipped section in to reword it.
- `turf_powerups.yml`: `CAPTURE_DEFENSE_BONUS` is retired (the `reinforced_defense` entry is removed). An entry still
  using it is skipped with a warning. `GARRISON_DISCOUNT` now multiplies the guard price (`Garrison.Cost_Per_Guard`,
  shipped at 1000). Shipped prices changed: `Cost` 5000 to 300, 15000 to 1000.
- `turf_npcs.yml`: the Quartermaster has `Display_Name: "Quartermaster"`. Changing it renames Quartermasters placed after
  a restart; existing ones keep the name they were placed with.
- Reserve cap per turf is `turf_rules.yml` `Garrison.Max_Reserve`.

### 4. Turf income, cap and levels

- **Dormant gangs stop earning.** A turf pays only while its owning gang had a member online within `Income.Idle_Hours`
  (default 24).
- **Faucet.** New turfs pay 250 per 10 minutes (`Default_Income_Amount`, stamped on new turfs only; existing turfs keep their amount),
  36,000 per day at that amount (D9 = B), capped by `Income.Max_Multiplier` (2.0) and
  the gang's turf cap.
- **Turf cap** (`Turf_Cap`) grows with the gang level. A gang over its cap is paid only for its oldest turfs (ties by turf
  id).
- **Gang XP** grows from turf income (`Income.Gang_XP_Per_Payout`, default 1.0 per paying turf). Gang levels follow the
  turf held, and the turf cap reads the level. A level-up line goes to online members.
- Each payout gives an action-bar line to online members (income, turf count, bank). Joining shows the income and up to
  four recent turf events since you left, then the map link. After a restart you see the last three events.
- `/glw turf` shows your gang's turfs against its cap, how many are paying, and the last three events.
- Quartermaster purchases are logged to the server log only; they are not in the gang digest yet.

### 5. Gang changes

- **Gang chat**: `/glw gang chat <message>` sends to your online gang; `/glw gang chat` toggles chat mode. Leaving the gang
  or quitting clears it.
- **Open gangs**: every existing gang is INVITE after the upgrade. Run `/glw gang recruiting open` to let players join with
  `/glw gang join <gang>`. Recruiting needs the `gangland.gang.invite` node or the top rank.
- **Gang bounty**: `/glw gang bounty <gang> <amount>` (permission `gangland.command.gang.bounty`). Any player, gangless
  ones too, puts money from his own balance on another gang (not his own or an ally). The gang that captures one of that
  gang's turfs collects the whole bounty into its bank, unless the bounty would take that bank over `Account.Maximum_Balance`.
  Then nothing moves and the bounty stays (Part 4, item 4). A bounty on a gang with no turf cannot be claimed until it holds
  one. A bountied gang that disbands destroys its bounty; posters are not refunded.
- **Disband split.** A disband with zero contributions splits the gang bank equally among members (the owner takes the
  rounding remainder). The disband fee refund has its own line (`Progression.Fee_Refund`).
- **Gangless players** get the onboarding screen from `/glw gang` (`gang_info` now has `Open.Condition:
  "%gangland_user_has-gang%"`). An unversioned `gang_info.yml` is replaced on the first boot (Part 4, item 12); copy your own edits back from `gang_info-old.yml`.
- **Officer preset** includes `gangland.turf.upgrade` (buy at the Quartermaster) on a fresh install. Existing rank trees
  get it once: the first 0.17.0 boot grants `gangland.turf.upgrade` to every rank that holds `gangland.gang.withdraw`
  and logs one line. Removing the node later sticks; the grant does not come back on later boots (Part 4, item 2).
- **Permissions**: `gangland.command.turf.rules`, `gangland.command.gang.chat|join|recruiting|bounty` and
  `gangland.inventory.gang_info` default true.
- `/glw turf rules` prints the five "how turf wars work" sentences with your server's numbers; run `/glw reload` after
  editing `turf_rules.yml` or `turf_settings.yml`.

### 6. Quartermaster

- Prices and the garrison discount come from `turf_powerups.yml`. Every purchase re-checks membership and rank on the
  click; a turf that changed hands closes the panel. Allies can open the Quartermaster and look, but only the owning gang
  buys. The boost catalogue pages nine boosts at a time.

### 7. Command notes

- `/glw gang accept [gang]` names the gang when you have more than one invite.
- A player without the command node gets the server's no-permission text. `Only turf admins can do that` appears only for
  players who hold an admin command node but not `gangland.turf.admin`.
- `turf create` and `turf rename` refuse a name with no letter or digit (`Turf.Name_Empty`).

### 8. Database and jars

- New table `gang_migration` (created automatically).
- Repository loads now read numeric columns through `Number` (NULL reads as 0), so a MySQL server returning a wider
  numeric type or NULL no longer fails the load.
- The core jar and the gang/turf/mail module jars must be updated together.

## Part 4 - Release fixes

These fixes went into 0.17.0 after the three parts above. Each item names the file or key and its default.

### 1. One-shot migrations survive a failed read

- A failed read of the gang or member table no longer counts as done. The D7 migration (invite-only default) and the D15
  migration (contribution reset) wait for a boot that reads those tables.
- A failed read of `gang_migration` counts as done for that boot and writes nothing; the table is read again on the next
  check.
- `gang_migration.done_at` is the real time the migration ran, kept per migration id.

### 2. Turf-upgrade grant

- The first 0.17.0 boot grants `gangland.turf.upgrade` once to every rank that holds `gangland.gang.withdraw` and logs one
  line. Fresh installs get no log line; their preset already holds the node. Removing the node later sticks. See Part 3,
  section 5.

### 3. Level-ups

- Level-up lines name the level reached: `%next_level%` in the `Level_Up` lines of `message/message_en.yml` is the level the
  player or gang just reached. `%level%` is the level before it.
- A level-up event fires after the level is raised. If a listener cancels it, that one level is undone and no further
  levels are added.
- Gangs and players collect no XP at the max level.
- **Developers:** a `LevelUpEvent` listener reads the level just reached with `event.getLevel().getLevelValue()`. The
  event is fired after the increment, so a listener that reads the level before the step sees the old value.

### 4. Gang bounty: own gang and bank cap

- A bounty on your own gang is refused, even when the gang lookup was stale.
- A claim that would push the capturing gang's bank over `Account.Maximum_Balance` (`gang/gang_settings.yml`, default
  `100_000_000_000`) does not happen. The bounty stays on the bountied gang, and the capturing gang's online members get
  `Bounty.Bank_Full` from `gang/gang_messages.yml`:

  ```yaml
  Bounty:
     Bank_Full: "&eYour gang bank cannot hold the %money_symbol%%amount% bounty on %gang%; it stays on them."
  ```

  Existing `gang_messages.yml` files without this key show the in-code fallback, which is the same line.

### 5. Joining cancels other invites

- `/glw gang join <gang>` cancels the joiner's other pending gang invites, the same as `/glw gang accept`. The player gets
  `Invite_Siblings_Cancelled` from `mail/mail_messages.yml` only when there were invites to cancel.

### 6. Gang chat after leaving

- A player who left his gang while in gang chat mode now has his next line sent to public chat, after the `Social.Chat_Off` line
  from `gang/gang_messages.yml`. Before 0.17.0 the line was dropped.

### 7. Captures: stepping out keeps the contest

- A challenger who steps out of a contested turf and back in keeps the contest. The attackers' contest ends `ABANDONED` only
  after no member of the attacking gang has been inside for `Capture.Abandon_Grace_Seconds` (`turf/turf_settings.yml`,
  default `15`).
- This is the defaulted reading of an open owner decision. The owner has not yet confirmed it.

### 8. Guards

- Only a guard that dies fires the "guard down" event. A guard that vanishes (chunk unload, NPC despawn) is dropped
  quietly. Known gap: the "half the guards are down" ratio still counts vanished guards in its total.
- A `/glw reload` extends guards already in the field when it raises `Capture.Max_Contest_Minutes` or
  `Defender.Lifespan_Seconds` (`turf/turf_npcs.yml`). The lifespan is read on every tick.
- A turf that changes owner loses its garrison stock. The old owner's guards are recalled.

### 9. Repeated outcome lines are quiet

- A held, failed or stalled line, and the digest line for the same outcome, is shown once per turf and attacking gang
  within a fixed 15-minute window. It is not a config key. The next repeat after that window is shown again.

### 10. New owner's turf is attackable at once

- When a turf changes hands, it reads "attackable" at once while one of the new owner's qualifying members is online. The
  "no `<gang>` online" minute no longer shows.

### 11. Quartermaster prices and wording

- `turf/turf_messages.yml` `Quartermaster.Reserve_Full` now reads:

  ```yaml
  Quartermaster:
     Reserve_Full: "&cThis turf already has %max% guards (stock and on duty)."
  ```

  The old text was `&cThis turf already holds %max% guards.`. An existing `turf_messages.yml` keeps the old text until you
  edit it.
- `garrison_discount` is clamped. The guard price is `Garrison.Cost_Per_Guard` (`turf/turf_powerups.yml`, shipped at
  `1000`) times the discount, kept between 10 % and 100 % of `Garrison.Cost_Per_Guard`, and never below 1. A
  `GARRISON_DISCOUNT` entry whose `Magnitude` is outside (0, 1] logs one warning at load.

### 12. Menus refresh by version

- Every shipped `inventory/*.yml` now carries `Config_Version: '0.17.0'` (the plugin version, filtered at build). On the first 0.17.0 boot, a shipped menu
  that has no `Config_Version` is replaced from the jar. The old file is kept as `inventory/<name>-old.yml`. Copy your
  customisations back by hand.
- Menus you added yourself (no copy in the jar) are not touched.
- From 0.17.0 on, shipped menus follow the plugin version, like `settings.yml`. This is how the `Open.Condition` line in
  `gang_info.yml` reaches existing servers.

### 13. Bad uuid rows and waypoint ids

- A database row with a bad or missing uuid is skipped with a warning that names the table. This covers users, banks,
  members, loot chests, bankers, traders, seized inventory and detainments. Fix or delete those rows.
- Waypoint renumbering reads ids as numbers, so MySQL `BIGINT` ids work. A waypoint row with a null id is skipped with one
  warning.

---

[← Back to Documentation Index](./README.md)
