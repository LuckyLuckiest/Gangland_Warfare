# Migrating a server to Gangland 0.14.0 (gang and turf line)

[← Back to Documentation Index](./README.md)

Gangland 0.14.0 is the gang and turf line: turf ids, captures, alliances, gang transfer and invites are made trustworthy.
Nothing needs a new dependency and `gangland-api` stays at `2.3` with no `Host_Api` change. The new files are created on first
boot. Read the notes below before you start the server on the new jar.

## W0 - trustworthy foundations

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
  (`turf/turf_settings.yml`, default 10) releases those turfs 5 minutes after it comes back.

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
- No `gangland-api` change (still `2.3`), no `Host_Api` change, no config change beyond the keys above.

### 10. By design

- `/glw turf buff <powerup>` is an admin tool and activates a buff for free.

## Wave W1 - See the city (ships in 0.17.0)

### 1. Player commands are open by default (CM-37)

- `/glw`, `/glw map ...`, `/glw turf info|list` and these gang player verbs now default to `true` for every player:
  create, leave, list, info, members, balance, deposit, withdraw, invite, invite cancel, accept, decline, kick, promote,
  demote. The gang menus `gang_stat` and `alliance_stat` also default to `true` through their
  `gangland.inventory.*` nodes. `gangland.inventory.gang_info` is not default true: it is OP-only, so `/glw gang`
  reaches the gang command for every non-op player (gangless players see the onboarding, members see help).
  Grant `gangland.inventory.gang_info` to members if you want `/glw gang` to open the gang menu for them.
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
  a gang see help. The gang menu needs `gangland.inventory.gang_info`, which is OP-only by default.
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

## Wave W2 - Fair fights (ships in 0.17.0)

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
- Entering a contested turf shows "`%gang%` capturing". The server's `message_en.yml` keeps its old "Phase 2" wording
  until you edit it. `Commands.Turf.Enter.Contesting_Claim` and `Subtitle_Contesting_Claim` are no longer read.
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
- **Faucet.** 250 per 10 minutes, 36,000 per active turf per day (D9 = B), capped by `Income.Max_Multiplier` (2.0) and
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
  gang's turfs collects the whole bounty into its bank. A bounty on a gang with no turf cannot be claimed until it holds
  one. A bountied gang that disbands destroys its bounty; posters are not refunded.
- **Disband split.** A disband with zero contributions splits the gang bank equally among members (the owner takes the
  rounding remainder). The disband fee refund has its own line (`Progression.Fee_Refund`).
- **Gangless players** get the onboarding screen from `/glw gang` (`gang_info` now has `Open.Condition:
  "%gangland_user_has-gang%"`). A server that edited `gang_info.yml` adds that line under `Open:` itself.
- **Officer preset** includes `gangland.turf.upgrade` (buy at the Quartermaster), on a fresh install only.
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

## W3 - to be filled

## W4 - to be filled

## W5 - to be filled

## W6 - to be filled

---

[← Back to Documentation Index](./README.md)
