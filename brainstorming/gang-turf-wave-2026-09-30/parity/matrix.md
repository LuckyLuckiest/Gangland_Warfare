# Gangs+ parity matrix (GP-01..GP-45 against master ff9d813f and the 0.14.0 plan)

Written 2026-09-30 for wave `gang-turf-wave-2026-09-30`. Inputs: `competitors/gangsplus.md` (our own checklist, ideas only), `PLAN.md` (final) and `roadmap.json` (lane ids used exactly as PLAN.md names them), `census/gang.md`, `census/ux-surface.md`, and code read after `graphify query` orientation (`WaypointType`, `GangMembersDamageListener`; graph.json 2026-09-29 23:08 is newer than HEAD 23:05). Frozen facts respected: D1 owners-online capture with wake-up, D2 turf cap by gang level with hold-only XP, D3 chunk snap, D4 living guards count (cap 4).

Copyright: the `home` column uses Gangland verbs, nodes and YAML keys only (`/glw gang ...`, `gangland.gang.*`, `gang_rules.yml`); nothing is taken from Gangs+ text, messages, keys or command wording.

Status key: **HAVE** works today; **PARTIAL** part works, gap named; **PLANNED** a PLAN.md lane delivers it (the lane cell says what it does NOT deliver); **MISSING** nothing today and no lane. Size = size of the remaining gap: S about one lane-day, M one lane, L several lanes or a new module.

## 1. Tally

HAVE 3, PARTIAL 16, PLANNED 5, MISSING 21 (total 45). None of the 5 PLANNED rows is delivered in full by the plan: each carries a named gap (GP-04 player-name info, GP-07 five-rung preset, GP-12 rewards, GP-13 ally chat, GP-45 MOTD).

## 2. Matrix

| id | feature | status | evidence (verified) | plan lane | home for the gap | size | conflict |
|---|---|---|---|---|---|---|---|
| GP-01 | Create, rename, disband with confirm; admin disband | **PARTIAL** | HAVE: `GangCreateCommand` (confirm, 60 s, `Create_Cost`), `GangRenameCommand` (`gangland.gang.rename`), `GangDeleteCommand` (Tail rank only, confirm, pro-rata payout, fires `GangDeleteEvent`). MISSING: staff disband - the `gang` root is player-only (`super(gangland, "gang", true)`) and delete is gated on the Tail rank. | None for the staff half (W0-C hardens `GangDeleteCommand`; W1-G puts name rules on create/rename) | Gang module: NEW `GangDisbandService` extracted from `GangDeleteCommand` (also serves GP-40) + NEW console-allowed staff root (section 3, A) verb `disband <gang>`, node `gangland.admin.gang.disband` | S (M with the service extraction) | Sequencing only: `GangDeleteCommand` payout lines are edited by W0-C and W2-P1 (D15 contribution reset), so the extraction goes after W2-P1. Staff disband must fire `GangDeleteEvent` so W0-A `TurfGangDeleteListener` clears turfs (no orphaned ownership). |
| GP-02 | Invite, uninvite, join by name, kick, leave | **HAVE** | Mail module: `GangInviteCommand` (send + list outgoing), `GangInviteCancelCommand`, `GangInviteAcceptCommand` (`gang accept [gang]`, by name); gang module: `GangKickCommand`, `GangLeaveCommand` (type twice). | W0-D adds decline + 72 h offline-invite expiry (hardening, not a gap) | - | - | NEW vs turf: join, leave, kick and accept while standing in a contested turf change which side a player counts on. P3.2 re-stamps presence only when an ally relation changes; add a membership-change re-stamp in W2-R, or a fresh joiner is an instant defender/attacker. |
| GP-03 | Transfer leadership; admin set-leader | **PARTIAL** | HAVE: `GangTransferCommand` (owner only, confirm, old owner drops to the Tail's parent). MISSING: staff set-leader - `RankAssignmentPolicy.evaluate` returns `OWNER_RANK` for the Tail even with `gangland.admin.gang.force_rank`, so `/glw option gang rank` (`GangOptionContribution` L154-196) cannot do it. | None | Staff root verb `setleader <gang> <player>` reusing the `GangTransferCommand` swap logic (extract to a `GangLeadership` helper) | S | None. |
| GP-04 | Gang list (with bank), info by gang or player, help | **PLANNED** | Today: no `gang list`/`gang info` for other gangs (`list` alias = own members); `/glw gang` gangless prints the help page, members get the `gang_info.yml` menu. Help/tab already filter by `sender.hasPermission` (Keystone `Argument.java` L250, L277) but every node defaults OP, so a non-op sees nothing until CM-37. | W1-G (`GangListCommand`, `GangInfoCommand`, gangless onboarding) + W1-K CM-37. Delivers: list, info by gang name, help. NOT: info by player name, a bank column | Gap: `GangInfoCommand` resolves a player name to their gang (S); bank column shown only with `gangland.gang.bank.view_others` (see GP-23) | S (gap) | Public card must not print another gang's bank or online times (PLAN 5.4 rule 7 spirit). |
| GP-05 | Console commands on join, leave, kick | **MISSING** | No join/leave/kick events exist (only `GangDeleteEvent`). Join sites: `GangCreateCommand`, mail `GangInviteAcceptCommand`, planned `gang join`; leave sites: `GangLeaveCommand`, `GangKickCommand`, `GangDeleteCommand` (resets every member). | None (W2-P2 adds `gang join` but no event) | `gang_rules.yml` `Commands.On_Join` / `On_Leave` / `On_Kick`; NEW `GangMemberJoinEvent` / `GangMemberLeaveEvent(reason)` fired from one NEW `GangMembershipService` choke point (W2-P2 is the natural lane) | M | Token injection: `%player%` / `%gang%` in a console command are unsafe if gang names may hold punctuation; pass the ASCII `name`, never `displayName` (ties to GP-06). |
| GP-06 | Unicode/any-language names, colour-code option, hex | **PARTIAL** | `displayName` already takes `&` codes and `&#RRGGBB` (Keystone `ChatUtil`, `GangDisplayCommand`, `Display_Name_Char`); `Gang.color` is one of 16 colours. Raw `name` has no validation today (GR-29). | W1-G `GangNameRules` = 3-16 `[A-Za-z0-9_]`, colour codes stripped. Delivers ASCII names only | `gang_rules.yml` `Name_Pattern` (default ASCII), `Display_Name.Allow_Hex`, `Display_Name.Parse_Colours`, `Display_Name.Max_Length`. Unicode/hex lives on `displayName`; the raw `name` stays ASCII as lookup key and map glyph key | S | CONFLICT: the W1-G ASCII rule contradicts "any language". Resolution above. Map: PLAN 5.1 takes the glyph from the first letter of the resolved display name, so a Cyrillic or CJK display name breaks the one-glyph-width class (5.4 rule 2) and `pixelWidth` has no such widths: the raster must fall back to a free `A-Z` glyph for any non-`A-Z` first letter. |
| GP-07 | Five configurable ranks, same ladder, promote/demote | **PLANNED** | Global rank tree (`RankManager`), names configurable (`Gang.Rank.Head/Tail`, `/glw rank create`), `GangPromoteCommand`/`GangDemoteCommand` exist. Default seeded tree is only `member -> owner` today. | W1-R seeds `member -> officer -> owner` on an empty table (D13 A). Delivers the ladder, three rungs | Gap: `gang_rules.yml` `Rank_Preset` (list of names) so the seed can be five rungs (S) | S (gap) | None. The global tree matches "same ladder in every gang". |
| GP-08 | Per-feature minimum rank | **PARTIAL** | `GangPermissions.allows` (rank node list OR Vault OR top rank) gates deposit, withdraw, rename, description, display, color, invite, ally (`gangland.gang.*`). Kick, promote, demote, delete, transfer are gated by rank position, not a node. | W1-R registers the existing eight nodes, validates and completes them, seeds officer defaults. Does not add nodes for kick, home, chat, fight | `GangPermissions` constants + `gang_rules.yml` `Rank_Defaults` for new verbs (`gangland.gang.kick`, `.home`, `.friendlyfire`, `.fight`); reuse `allows` (S per verb) | S | Bank: W1-R must keep `withdraw` officer-or-above in the preset (a recruit spending the bank is TF-45). |
| GP-09 | A node per command, wildcard nodes | **PARTIAL** | Keystone `Argument.addPermission` registers `gangland.command.<root>.<leaf>` per argument; `plugin.yml` L20-28 declares only `gangland.command.main` (default op), so a non-op cannot run `/glw` at all (CM-37). | W1-K CM-37 (`permissions:` block, default true for player verbs, `PluginYmlPermissionsTest`). Delivers defaults; a wildcard parent is not stated | `plugin.yml` `permissions:` parent node listing the player verbs as children (orchestrator-owned file) (S) | S | None. |
| GP-10 | Level up by paying; remaining amount shown | **MISSING** | `Gang.level` has no producer today; W2-P1 feeds it from actively held turf only (D2 A, PLAN P4.4). `GangLevelUpEvent` exists but is never constructed. | None. W2-P1 delivers XP progression, NOT a purchase (by design) | If wanted: a SEPARATE perk track `Gang.perk_tier` (new column) bought with the bank via `gang upgrade`, showing the amount remaining; it gates member cap, homes, chat prefix (GP-11) and NEVER `Gang.level` or the turf cap | M | CONFLICT with frozen D2: XP only from actively held turf and the turf cap is a function of `Gang.level`. A money-to-level path lets a rich gang buy the turf cap and skip the activity gate. New owner decision D16. |
| GP-11 | Per-level max members, max homes, chat prefix | **MISSING** | No member, home or prefix limit anywhere (census gang.md section 1, grep 0 hits). | None. PLAN sections 1 and 12 explicitly DROPPED member and ally caps | `gang_rules.yml` `Limits` per perk tier: `Max_Members`, `Max_Homes`, `Chat_Prefix`; enforced in mail `GangInviteAcceptCommand`, `GangInviteCommand` and W2-P2 `gang join`; shown on `gang info`. Chat prefix rides the W2-P2 chat format (S) | M | CONFLICT with the PLAN's own decision (section 12 drop). Fits the fair-fight design: a member cap bounds the plurality zerg (P3.2). Key the limits on the perk tier (GP-10), not on turf-XP `Gang.level`, or a new gang starts with no room to grow (D2 bootstrap). Enforce at accept AND at Open `gang join` (D7). |
| GP-12 | Level-up rewards, offline members too, level-up event | **PLANNED** | `GangLevelUpEvent` (extends core `LevelUpEvent`) never constructed; `GangLevelMessageListener` dead. | W2-P1 (`GangManager.addExperience` fires `GangLevelUpEvent`, level-up title to the gang). Delivers the event + title, NOT console-command or offline rewards | `gang_rules.yml` `Level_Rewards.<n>.Commands` run by a listener on `GangLevelUpEvent`; offline members handled by running the command with the member's name from the roster (S) | S (gap) | Rewards are a faucet on held-turf XP (D2): ship empty by default, config only. |
| GP-13 | Gang chat (toggle + one-shot); ally chat | **PLANNED** | No gang chat today (documented in gangs.md, unimplemented). | W2-P2 (P4.5: `GangChatCommand` + `GangChatListener`, toggle and one-shot). Delivers ALL of gang chat; ally chat is NOT in the plan | Gap: an ally channel in the same listener using `GangMembership.alliedOrSame` (S) | S (gap) | Ally chat recipients depend on `Gang.isAlly` symmetry (W0-C) and, for the fair-fight design, on an ally cap (GP-25); none with D1-D4. |
| GP-14 | Admin social spy, log gang/ally chat to console | **MISSING** | Nothing exists. | None | In the W2-P2 `GangChatListener`: staff toggle `gangland.admin.gang.spy` (in-memory set) + `gang_rules.yml` `Chat.Log_To_Console` | S | None; spy off by default. |
| GP-15 | Chat-format token, default prefix for gangless | **PARTIAL** | PAPI tokens exist (`%gangland_gang_display-name%`, `gang_name`, `gang_color-code` in `GangPlaceholderContribution`); a gangless/unanswered Gangland token returns a hardcoded `"NA"` (`GanglandPlaceholder` L160-164). No Gangland chat formatter. | None (W2-P2 chat listener does not format public chat) | `gang_rules.yml` `Chat.Public_Tag_Format` + `Chat.No_Gang_Tag`, applied at LOW in the W2-P2 listener; configurable default for the placeholders (see GP-34) | S | None. |
| GP-16 | Chat events for other plugins | **MISSING** | No chat events. | None | NEW `GangChatEvent` / `AllyChatEvent` in gang `events/gang/` fired by the W2-P2 listener; third-party plugins only see them if they live in `gangland-api` (GP-37) | S | Needs the additive api bump (see GP-37); PLAN section 2 says no new api surface through W3. |
| GP-17 | Friendly-fire toggle per gang and global | **PARTIAL** | Three listeners in three modules decide it today: `GangMembersDamageListener` (gang; unconditional cancel for same gang and allies, no config), `TurfFriendlyFireListener` (cops; owner + allies vs guards and Quartermaster), `GangAllyWeaponImpactListener` (civilians; Bartizan impacts; gated on `Settings.isGangEnabled()`, which is dead). | W0-C only (symmetric `isAlly` widens who is protected). No toggle | `Gang.friendly_fire` column + `gang friendlyfire` (`gangland.gang.friendlyfire`, leader) + `gang_rules.yml` `Friendly_Fire.Global`; ONE fact exposed as an additive method on api `GangMembership`/`GangMembershipView` that all three listeners read (civilians and cops are gang-module-free, so this is the only legal home) | M | (1) Additive api minor bump vs PLAN "no new api surface through W3". (2) `TurfFriendlyFireListener` (guards, Quartermaster) must stay unconditional whatever the toggle, or owners kill their own guards for the D14 refund and the P6.3 bounty. (3) Toggle must not change plurality/presence counting (P3.2). |
| GP-18 | Per-world FF, WorldGuard region flag | **MISSING** | No per-world setting; `Gang.Enable` is dead (only civilians' weapon listener reads it). | None | Worlds: `gang_rules.yml` `Friendly_Fire.Disabled_Worlds` read by the GP-17 fact (S). Region flag: soft WorldGuard hook (L, flags must be registered in the plugin's `onLoad`, before the module loader runs in `onEnable`); Gangland-native alternative `Friendly_Fire.In_Turf` (S) | S (worlds) / L (region flag) | A WorldGuard region flag is a second region system next to admin-drawn turfs; recommend the worlds list plus `In_Turf` first and defer the flag. |
| GP-19 | FF blocks only negative potions; self bow-boost | **MISSING** | Nothing. `GangMembersDamageListener` treats a self-shot as same-gang and cancels it for any gang member, so bow-boosting is blocked today. | None | Same FF listener: `PotionSplashEvent` / `AreaEffectCloudApplyEvent` with an XSeries `XPotion` negative-effect set; `Friendly_Fire.Allow_Self_Boost` (S) | S | None; only matters once the GP-17 toggle exists. |
| GP-20 | Player and gang stats, KDR, WLR, assists, profile | **PARTIAL** | `User` holds kills, deaths, mobKills (core `User.java` L44, KDR L134); `user_kills/deaths/kd` placeholders (`GanglandPlaceholder` L215-218); counted in impl `EntityDamageListener` L121 and `PlayerDeathListener` L79/L107. No assists, no gang aggregates, no wins/losses (need GP-29), no player profile. | W1-G turns `gang_stat.yml` into a stats page (content and aggregation unspecified) | NEW `GangStatsService` (gang module) aggregating member `User` stats over `GangManager`; assist tracking (last-damager window) in impl `EntityDamageListener`; wins/losses columns arrive with GP-29 | M | Kill-farm guards: no count for gang-mates or allies (FF on), same victim inside a cooldown. Stats must never feed `Gang.level` (D2). Counting in gang-disabled worlds needs GP-32. |
| GP-21 | Top-10 leaderboard, sortable | **MISSING** | No leaderboard anywhere (a `settings.yml` comment only). PLAN dropped `/glw turf top`, but that was a per-member turf score; a gang leaderboard is not blocked by that drop. | None (W1-G `gang list` is paged, not sorted) | NEW `GangTopCommand` (`/glw gang top [stat]`) over `GangManager.getAll()`, cached about 60 s; sorts: kdr, level, members, online members, bank, turfs held (Gangland-native, from turf via `Depends`); wlr after GP-29. "Members' total money" needs offline wallets, omit or cache asynchronously | M | Bank sort exposes rich gangs as raid targets: gate with `gangland.gang.top.bank`. KDR is easy to farm with alt gangs: minimum-kills threshold. |
| GP-22 | Admin stat reset; no counting in disabled worlds | **MISSING** | Nothing. | None | Staff root `resetstats <player>` writing `User` kills/deaths/assists (S); the world guard is GP-32 | S | impl `EntityDamageListener` cannot import the gang module, so the world check needs an additive api (see GP-32). |
| GP-23 | Bank deposit/withdraw; hide others' balance unless permitted | **PARTIAL** | `GangDepositCommand`, `GangWithdrawCommand`, `GangBalanceCommand` (nodes `gangland.gang.deposit/withdraw`, `Maximum_Balance` cap). No screen shows another gang's balance today. | W1-R lets a member deposit; nothing decides visibility for the new W1-G list/info or GP-21 top | `gangland.gang.bank.view_others` checked by `GangInfoCommand` / `GangListCommand` / `GangTopCommand` (S) | S | The public `gang info <name>` card in PLAN P2.5 must omit the bank by default. D15 changes what a member's withdraw share means (gangs.md drift). |
| GP-24 | Admin bank balance/give/take/reset | **MISSING** | `command/sub/economy/*` in impl (`EconomySetCommand`, `EconomyDepositCommand`, ...) is user-only; no gang bank staff verbs. | None | Staff root `bank balance/give/take/reset <gang>` on `Gang.getEconomy()`, console-allowed, every write logged (`@CustomLog` + ring buffer) (S) | S | `give` is an unlogged faucet against the D9 economy: must log actor and amount. |
| GP-25 | Alliance request, back to neutral, alliance limit | **PARTIAL** | Mail: `GangAllyRequestCommand`, `GangAllyAcceptCommand`, `GangAllyRejectCommand`, `GangAllyPendingCommand`(+cancel); gang: `GangAllyAbandonCommand` (back to neutral, still takes a numeric id). No limit. | W0-C makes `Gang.isAlly` symmetric, W0-D hardens mail; nothing for the cap or name-based abandon. PLAN section 12 dropped ally caps | `gang_rules.yml` `Limits.Max_Allies`, checked at request and at accept in mail (S); abandon by name (S) | S | Reverses the PLAN section 12 drop, and the fair-fight design needs it: owners + allies all count as defenders (P3.2, `CaptureService.isOwnerAlly`) and are guard-immune (`TurfFriendlyFireListener`), so an unbounded alliance is a defender blob. Recommend a default of 2-3. |
| GP-26 | Multiple named gang homes, safe check, level cap | **PARTIAL** | `Waypoint.WaypointType.GANG` + `gangId` + `WaypointAccess` let members teleport to their gang's waypoints; `WaypointTeleport` (api) has warm-up timer with 1.5-block move-cancel, cooldown, cost, shield, and fires a cancellable `TeleportEvent`. But creation/assignment is admin-only (`/glw waypoint create`, `gangId` sub-command under the OP-default waypoint root), all names share one global namespace (`WaypointManager.get(name)`, two gangs cannot both own "base"), no safe-location check, no rank gate, no per-level cap, and `WaypointManager` is impl-only (api has `WaypointLookupContract`). | None. PLAN section 12: gang home/HQ out of scope beyond the existing waypoint | NEW `gang/home/*` in the gang module: `gang_home` table (gang id, name unique per gang, world/xyz/yaw/pitch), `gang home set/del/list/<name>`, nodes `gangland.gang.home.*`, cap from the perk tier (GP-11), NEW safe-spot check with XSeries `XMaterial`; teleports through api `WaypointTeleport` so no impl dependency | M (L if `WaypointLookupContract` must widen) | Fair fights: (a) a home is a raid escape hatch: veto the cancellable `TeleportEvent` when the player is inside a turf under attack; (b) `Waypoint.shield` does `player.setInvulnerable(true)` at the destination: a home inside a turf yields invulnerable defenders on the bar, so gang homes get no shield; (c) refuse `home set` inside a rival's turf or a turf under attack; (d) arrival must not count as an instant defender (D1, defenders count at once): stamp arrivals like attackers (15 s) or refuse teleport into a contested turf. |
| GP-27 | Regroup at a home (all online members) | **MISSING** | No regroup, no summon. | None | `gang regroup <home>` as a consent request (click to accept) to online members, using the GP-26 service and the same `TeleportEvent` veto (S after homes) | S | As GP-26 (d): a regroup into a contested turf is instant reinforcement and breaks the wake-up/plurality design; refuse it, or stamp arrivals. Only ONLINE members are reached, so D1's owners-online window is unchanged. |
| GP-28 | Admin home tools | **MISSING** | Nothing. | None | Staff root `homes <gang> list/tp/delete` (S) after GP-26 | S | None. |
| GP-29 | Gang fights: challenge, bet, accept/decline, join/leave | **MISSING** | Nothing (grep for duel/arena/challenge hits only the turf capture code). PLAN's whole fight model is turf capture. | None | NEW runtime module `gangland-fights` (`Depends: [gang]`), commands attached through a `CommandContribution` on path `gang` (`gang fight challenge/accept/decline/join/leave`), the bet moves between `Gang.getEconomy()` accounts | L | (1) Duel deaths hit `CustomPlayerDeathListener` (downed state: `PlayerDownedEvent`, `DownedPlayerRegistry`), cops `KillComboWantedTracker` heat and the money-drop classifier; a duel needs the generic `exemptsKill` seam PLAN parks in W4-H (TF-49, optional): promote it or ship fights after it. (2) Fights feed WLR only, never `Gang.level` (D2). (3) Wash trading with alt gangs: minimum gang age and a per-pair cooldown. (4) Participants must not appear in turf presence, boss bars or alerts. |
| GP-30 | Multiple arenas, keep players inside, admin arena tools | **MISSING** | Nothing. | None | Same module: staff verbs `arena create/delete/setspawn/setname/save/list` under the staff root (`gangland.admin.fight.*`); own two-corner region record (do not import turf's `CuboidRegion`) | L (within GP-29) | Arenas must not overlap a turf (a `findConflict`-style check); boundary control by a 1 Hz check plus cancelling `PlayerTeleportEvent`, not per-move handlers. |
| GP-31 | Command blocking, equal teams, winner takes bet, stats | **MISSING** | Nothing. | None | Same module: `PlayerCommandPreprocessEvent` allow/deny list in `fights/fight_rules.yml`, bypass node, `Require_Equal_Teams`, payout, WLR into GP-20 (M within GP-29) | M (within GP-29) | The block list must still allow `gang fight leave` and staff verbs; escrow the bet at accept, not at start, so a crash cannot mint or lose money. |
| GP-32 | Disable gang commands in chosen worlds | **MISSING** | Even the global switch is dead: `Gang.Enable: false` no longer disables gangs (only civilians' `GangAllyWeaponImpactListener` reads it). | None | `gang_rules.yml` `Disabled_Worlds`; NEW `GangWorldPolicy` bean checked in the `GangCommand` root pre-dispatch, the FF listener, stat counting and (turf `Depends: [gang]`) `CaptureEligibility` | S | Turf in a gang-disabled world must say why it is not capturable (a `CaptureEligibility` reason) or income and XP still accrue. impl-side kill counting needs an additive api (`GangWorlds`), sequence with the GP-37 bump. |
| GP-33 | Combat-tag support: no gang escapes mid-combat | **MISSING** | No combat tag anywhere (grep for combat-tag/combat-log finds nothing in source or config). | None | No plugin dependency needed first: a gang-module PvP timer (`Combat.Tag_Seconds`) checked by `gang leave`, `home`, `regroup`, plus the same cancellable `TeleportEvent` veto; optional soft hooks for external combat-tag plugins later (S-M) | S-M | Also stops leave/kick/join gang-hopping mid-contest (see GP-02). No conflict with D1-D4. |
| GP-34 | PlaceholderAPI player/gang/leaderboard tokens | **PARTIAL** | `GangPlaceholderContribution` answers about 30 tokens (`gang_name`, `display-name`, `state`, `color*`, `balance`, `members-size`, `online-members-size`, `offline-members-size`, `ally-list`, `ally-size`, level family, `user_has-gang/gang-id/rank/...`) plus `user_kills/deaths/kd`. Missing: friendly fire, leader, rank number, member name lists, gang kills/deaths/KDR/wins/losses/WLR, members' money, leaderboard tokens, configurable default (hardcoded `"NA"`). | W1-H adds six turf tokens only | Extend `GangPlaceholderContribution` (the `gang_` prefix is already Gangland-owned, no impl line needed); `Placeholders.Default_Value` in `gang_rules.yml`; leaderboard tokens read the GP-21 cache (never sort per request) | S-M | `%gang_bounty%` returns 0 (D8). None with D1-D4. |
| GP-35 | MySQL/SQLite, periodic save, schema updater | **HAVE** | Keystone persistence: `GanglandDatabase` (`SqliteBackend`/`MysqlBackend`), `PeriodicalUpdates` autosave (`settings.yml` `Auto_Save`), diff-engine schema updates; new gang columns/tables ride it. | - | - | - | None. |
| GP-36 | Titles, fully customisable messages, any language | **PARTIAL** | Every message is customisable (`message_en.yml`, `message_es.yml`, 88 GANG_/RANK_ constants in api `Messages`); Keystone `ChatUtil.sendTitle` exists (used by cops detainment). Gang membership notifications (join/leave/kick/promote/rename) are chat only. | W1-T (turf enter titles) and W2-P1 (level-up title) only | NEW `gang/gang_messages.yml` (`Titles` section) registered in the W1 kickoff `GangModuleFileConfig`, NEW `GangNotifier` | S | CLAUDE.md contract: new strings live in module YAML, not `Messages`; the W1 kickoff creates only `gang_rules.yml`, so add the messages file there (kickoff-only registration, PLAN 7.1). |
| GP-37 | Public API: queries and events | **PARTIAL** | api: `GangMembership`/`GangMembershipView` (`gangIdOf`, `gangsAllied`, `nameOf`, `alliedOrSame`, `isInstalled`), `GangItemSourceContribution`. Events: only `GangDeleteEvent`, and it lives in the gang module (invisible to a third-party plugin compiled against the api); `GangLevelUpEvent` starts firing at W2-P1. | W2-P1 (level-up event only) | NEW events in `gangland-api` `events/gang/*` (create, join, leave with reason, rename, chat, ally chat), fired from the gang module; widen `GanglandApi.gangs()` (NEW `GangView`: list, by name, members); ONE additive minor bump shared with GP-17 and GP-32 | M | PLAN section 2: no new api surface through W3 and W4 reads `GanglandApi.VERSION` from the merged master (2.1 or 2.2). A second additive bump must be sequenced and batched (GP-17, GP-32, GP-37 together). |
| GP-38 | Admin reload | **HAVE** | `ReloadCommand` (`/glw reload`, `files`, `inventory`); beans reload through `BeanLifecycle`. W1 kickoff `GangModuleFileConfig` must reload `gang_rules.yml` (P1.2 lifecycle rule). | W1 kickoff (registration only) | - | - | None. |
| GP-39 | Player and gang profile GUI | **PARTIAL** | YAML menus exist: `gang_info.yml` (members' menu), `alliance_stat.yml`, `phone_gang*.yml`, item sources `gangs`/`gang_members`/`gang_allies` (`GangMenuItemSourceContribution`); `gang_stat.yml` is empty (`Size: 54`). | W1-G fills `gang_stat.yml` as a stats page (thin, no aggregates). No player profile | NEW `player_profile.yml` in `gangland-impl/.../inventory/` + a `gang_profile` item source in `GangMenuItemSourceContribution`; shows kills/deaths, rank, join date, turfs held (needs GP-20) | M | A profile of another gang's member must not show online times or guard reserves (PLAN 5.4 rule 7). |
| GP-40 | Auto-purge inactive players and gangs | **MISSING** | No purge exists. Turf `InactivityReleaseTask` only releases turfs after `Inactivity_Release_Days`, keyed on `Gang.lastMemberOnlineAt`, which is written ONLY by turf (`GangPresenceTracker`/`GangPresenceListener`). | None | NEW `GangPurgeTask` (BeanLifecycle) in the gang module tracking its own last-seen (`OfflinePlayer.getLastPlayed()` per member, checked daily) and calling `GangDisbandService` (GP-01); the disband fires `GangDeleteEvent`, so W0-A `TurfGangDeleteListener` releases turfs, no race. Player purge removes long-unseen non-owner members | M | A gang-module purge cannot rely on `lastMemberOnlineAt` when turf is absent (inversion). Disband payout after D15 is an equal split among offline members: decide whether a purged gang pays out or refunds the 25% fee logic. Complements the turf anti-hoarding rules. |
| GP-41 | More fight features, more events and API methods | **MISSING** | Not shipped by the competitor either; depends on the GP-29..31 module and the GP-37 api. | None | Fights module: fight events (`FightStart/End`), spectators, rematch; exported through the same api bump as GP-37 | L (follows GP-29) | As GP-29. |
| GP-42 | Summon-all / teleport requests to the whole gang | **MISSING** | No summon. | None | Same verb as GP-27: `gang regroup` accepting either a home or the leader's position, with consent and the `TeleportEvent` veto | S (after GP-26) | As GP-27, plus the summoned members leave a contested turf: veto when the destination or the source is a turf under attack. |
| GP-43 | Top lists by balance and members; member list by rank | **PARTIAL** | `GangMembersCommand` lists every member with rank and online state in insertion order, no paging, not grouped by rank. Top lists do not exist (GP-21). | W1-G `gang list` is paged but unsorted | `GangMembersCommand`: group by rank + `[page]` (S); the top lists are GP-21 | S | Bank ranking gated as in GP-21. |
| GP-44 | Negotiated ally friendly fire (both agree) | **MISSING** | `GangAlliance` is stored one row per direction (`gang_ally`, `GangAllianceTable`), which fits per-side consent; ally FF is unconditionally off in all three listeners (GP-17). | None (W0-C symmetric `isAlly` only) | `gang_ally.friendly_fire` boolean per row, `gang ally friendlyfire <gang> on/off`; effective only when both rows are on; read through the GP-17 api fact | M | Allies count on the owners' side of the bar (P3.2) and are guard-immune (`TurfFriendlyFireListener`): an ally you may shoot who still counts as your defender is incoherent. Recommend default off, and an FF-on ally neither counts as a defender nor shares guard immunity. |
| GP-45 | Open vs invite-only join; gang MOTD | **PLANNED** | `Gang.State` (`OPEN`/`INVITE`/`CLOSE`) is persisted but inert and defaults to `OPEN` (GR-38). `Gang.description` exists (default "Conquering the hood", edited by `gang desc`); nothing shows it to members on login. | W2-P2 (`gang join`, `gang recruiting open/invite`, D7 + GR-38 migration). Delivers ALL of open/invite; the MOTD is not in the plan | Gap: one line in `MemberJoinListener` showing the description to the joining member, or a NEW members-only `motd` column if the description stays public (S) | S (gap) | `gang join` sets `gangJoinDateLong` (already in the plan) and must respect the GP-11 member cap and the GP-02 contest re-stamp. |

## 3. Cross-cutting homes (several MISSING ids collapse into one place)

A. **Staff root.** No staff verbs exist for gangs at all: the `gang` root is player-only, and `/glw option gang rank` cannot assign the Tail. One NEW console-allowed root (modelled on the `rank` root, `super(gangland, "rank", false)`), node family `gangland.admin.gang.*`, carries GP-01 disband, GP-03 setleader, GP-22 resetstats, GP-24 bank, GP-28 homes and (fights module) arena tools. Its `commands.json` entries are covered by `GangCommandsJsonParityTest`. Size M in total, S per verb.

B. **Lifecycle events and one api bump.** GP-05, GP-12, GP-16, GP-37 need `GangMemberJoinEvent`/`LeaveEvent(reason)`, chat events and create/rename events (census gang.md section 4 already lists them). Fire them from W2-P1/W2-P2 rather than a new lane, through one NEW `GangMembershipService` choke point (join sites: `GangCreateCommand`, mail `GangInviteAcceptCommand`, planned `gang join`; leave sites: `GangLeaveCommand`, `GangKickCommand`, `GangDeleteCommand`). External plugins only see events that live in `gangland-api`, so the events, the widened `GanglandApi.gangs()` view, the friendly-fire fact (GP-17/44) and the world policy (GP-32) share ONE additive api minor bump, sequenced with the W4 `NpcRole` bump.

C. **One friendly-fire fact.** `GangMembersDamageListener` (gang), `TurfFriendlyFireListener` (cops) and `GangAllyWeaponImpactListener` (civilians) each decide it today. Civilians and cops are gang-module-free, so the only legal home is an additive method on `GangMembership`/`GangMembershipView`. The guard/Quartermaster rule in `TurfFriendlyFireListener` stays unconditional.

D. **Teleport policy (homes, regroup, summon, combat tag).** All four ride the cancellable `TeleportEvent` that `WaypointTeleport` already fires before `player.teleport`: one turf-side listener can veto a teleport out of or into a turf under attack and during the wake-up shield, and a PvP timer covers the combat-tag row without a plugin dependency. Homes need their own table (global waypoint names collide across gangs), and `Waypoint.shield` (invulnerability) must not apply to gang homes.

E. **Stats and leaderboards.** GP-20, GP-21, GP-34, GP-39, GP-43 share one NEW `GangStatsService` + cached leaderboard; assists are the only change outside the gang module (impl `EntityDamageListener`).

F. **Perk tier (limits) versus XP level.** GP-10/11 (and GP-26 home cap) hang off one NEW `Gang.perk_tier` column so `Gang.level` stays purely hold-XP (D2).

G. **Fights module.** GP-29..31, GP-41 are one NEW runtime module `gangland-fights` (`Depends: [gang]`); it needs the generic kill-heat exemption (TF-49 seam, PLAN W4-H) and the downed-player path before it ships.

H. **Purge and disband.** GP-01 (staff), GP-40 share NEW `GangDisbandService`; the existing `GangDeleteEvent` -> W0-A `TurfGangDeleteListener` path already clears turfs.

## 4. Conflicts with the plan and with frozen decisions

Against frozen D1-D4 and the fair-fight design:
- **D2 versus GP-10**: a bought level lets a rich gang buy the turf cap and skip the held-turf activity gate. Resolution: a separate perk tier; `Gang.level` never takes money.
- **Fair fights versus GP-26/27/42 (homes, regroup, summon)**: escape hatch out of a raid, instant reinforcement into a contested turf (D1 counts defenders at once), and invulnerable defenders through `Waypoint.shield`. Resolution: `TeleportEvent` veto, arrival stamping, no shield on gang homes, no home inside a rival or contested turf.
- **P3.2 versus GP-02/45 (join, leave, kick, Open join mid-contest)**: presence is re-stamped only on an ally relation change; add a membership-change re-stamp.
- **P3.2 versus GP-17/44 (friendly-fire toggles)**: guards and allies are treated as friendly; ally FF on must not leave the ally counting as a defender; guard immunity stays unconditional.
- **P3.2 versus GP-25 (no ally cap)**: owners plus allies all count as defenders, so an unbounded alliance is a blob.
- **D4/guards versus GP-17**: an FF toggle must not let owners kill their own guards (D14 refund, P6.3 bounty).

Against the plan's own scope decisions (PLAN sections 1 and 12) that the new owner goal reverses:
- Member caps and ally caps (GP-11, GP-25): dropped by the plan, now needed.
- Gang home/HQ beyond the GANG waypoint (GP-26..28, 42): out of scope in the plan.
- "No new api surface through W3" (GP-17, 32, 37, 44).
- W1-G `GangNameRules` ASCII-only (GP-06) and the map glyph rule (5.1, 5.4).
- Persistent gang wars and declared raids stay out of scope; GP-29 duels are consensual, separate from turf, and are not a raid system.

## 5. Owner decisions this matrix raises (proposed ids, none answered)

| Id | Question | Recommended |
|---|---|---|
| D16 | May money buy progression (GP-10)? | A separate perk tier for limits and homes; `Gang.level` and the turf cap stay hold-XP only |
| D17 | Reinstate member and ally caps (GP-11, GP-25)? | Yes, keyed on the perk tier; `Max_Allies` default 2-3 |
| D18 | Gang homes and regroup rules (GP-26/27/42) | Own `gang_home` table, `TeleportEvent` veto around contested turfs, no shield, consent-based regroup |
| D19 | Fights module and the kill-heat exemption (GP-29..31) | New module after the W4-H seam; fights feed WLR only |
| D20 | Friendly-fire model (GP-17/18/44) | One api fact; ally FF default off and non-counting when on; guards unconditional; defer the WorldGuard flag |
| D21 | Unicode names (GP-06) | ASCII `name`, free-form `displayName`; raster falls back to a free letter |

## 6. Machine-readable rows

```json
[
 {
  "id": "GP-01",
  "status": "PARTIAL",
  "evidence": "HAVE: `GangCreateCommand` (confirm, 60 s, `Create_Cost`), `GangRenameCommand` (`gangland.gang.rename`), `GangDeleteCommand` (Tail rank only, confirm, pro-rata payout, fires `GangDeleteEvent`). MISSING: staff disband - the `gang` root is player-only (`super(gangland, \"gang\", true)`) and delete is gated on the Tail rank.",
  "lane": "None for the staff half (W0-C hardens `GangDeleteCommand`; W1-G puts name rules on create/rename)",
  "home": "Gang module: NEW `GangDisbandService` extracted from `GangDeleteCommand` (also serves GP-40) + NEW console-allowed staff root (section 3, A) verb `disband <gang>`, node `gangland.admin.gang.disband`",
  "size": "S (M with the service extraction)",
  "conflict": "Sequencing only: `GangDeleteCommand` payout lines are edited by W0-C and W2-P1 (D15 contribution reset), so the extraction goes after W2-P1. Staff disband must fire `GangDeleteEvent` so W0-A `TurfGangDeleteListener` clears turfs (no orphaned ownership)."
 },
 {
  "id": "GP-02",
  "status": "HAVE",
  "evidence": "Mail module: `GangInviteCommand` (send + list outgoing), `GangInviteCancelCommand`, `GangInviteAcceptCommand` (`gang accept [gang]`, by name); gang module: `GangKickCommand`, `GangLeaveCommand` (type twice).",
  "lane": "W0-D adds decline + 72 h offline-invite expiry (hardening, not a gap)",
  "home": "-",
  "size": "-",
  "conflict": "NEW vs turf: join, leave, kick and accept while standing in a contested turf change which side a player counts on. P3.2 re-stamps presence only when an ally relation changes; add a membership-change re-stamp in W2-R, or a fresh joiner is an instant defender/attacker."
 },
 {
  "id": "GP-03",
  "status": "PARTIAL",
  "evidence": "HAVE: `GangTransferCommand` (owner only, confirm, old owner drops to the Tail's parent). MISSING: staff set-leader - `RankAssignmentPolicy.evaluate` returns `OWNER_RANK` for the Tail even with `gangland.admin.gang.force_rank`, so `/glw option gang rank` (`GangOptionContribution` L154-196) cannot do it.",
  "lane": "None",
  "home": "Staff root verb `setleader <gang> <player>` reusing the `GangTransferCommand` swap logic (extract to a `GangLeadership` helper)",
  "size": "S",
  "conflict": "None."
 },
 {
  "id": "GP-04",
  "status": "PLANNED",
  "evidence": "Today: no `gang list`/`gang info` for other gangs (`list` alias = own members); `/glw gang` gangless prints the help page, members get the `gang_info.yml` menu. Help/tab already filter by `sender.hasPermission` (Keystone `Argument.java` L250, L277) but every node defaults OP, so a non-op sees nothing until CM-37.",
  "lane": "W1-G (`GangListCommand`, `GangInfoCommand`, gangless onboarding) + W1-K CM-37. Delivers: list, info by gang name, help. NOT: info by player name, a bank column",
  "home": "Gap: `GangInfoCommand` resolves a player name to their gang (S); bank column shown only with `gangland.gang.bank.view_others` (see GP-23)",
  "size": "S (gap)",
  "conflict": "Public card must not print another gang's bank or online times (PLAN 5.4 rule 7 spirit)."
 },
 {
  "id": "GP-05",
  "status": "MISSING",
  "evidence": "No join/leave/kick events exist (only `GangDeleteEvent`). Join sites: `GangCreateCommand`, mail `GangInviteAcceptCommand`, planned `gang join`; leave sites: `GangLeaveCommand`, `GangKickCommand`, `GangDeleteCommand` (resets every member).",
  "lane": "None (W2-P2 adds `gang join` but no event)",
  "home": "`gang_rules.yml` `Commands.On_Join` / `On_Leave` / `On_Kick`; NEW `GangMemberJoinEvent` / `GangMemberLeaveEvent(reason)` fired from one NEW `GangMembershipService` choke point (W2-P2 is the natural lane)",
  "size": "M",
  "conflict": "Token injection: `%player%` / `%gang%` in a console command are unsafe if gang names may hold punctuation; pass the ASCII `name`, never `displayName` (ties to GP-06)."
 },
 {
  "id": "GP-06",
  "status": "PARTIAL",
  "evidence": "`displayName` already takes `&` codes and `&#RRGGBB` (Keystone `ChatUtil`, `GangDisplayCommand`, `Display_Name_Char`); `Gang.color` is one of 16 colours. Raw `name` has no validation today (GR-29).",
  "lane": "W1-G `GangNameRules` = 3-16 `[A-Za-z0-9_]`, colour codes stripped. Delivers ASCII names only",
  "home": "`gang_rules.yml` `Name_Pattern` (default ASCII), `Display_Name.Allow_Hex`, `Display_Name.Parse_Colours`, `Display_Name.Max_Length`. Unicode/hex lives on `displayName`; the raw `name` stays ASCII as lookup key and map glyph key",
  "size": "S",
  "conflict": "CONFLICT: the W1-G ASCII rule contradicts \"any language\". Resolution above. Map: PLAN 5.1 takes the glyph from the first letter of the resolved display name, so a Cyrillic or CJK display name breaks the one-glyph-width class (5.4 rule 2) and `pixelWidth` has no such widths: the raster must fall back to a free `A-Z` glyph for any non-`A-Z` first letter."
 },
 {
  "id": "GP-07",
  "status": "PLANNED",
  "evidence": "Global rank tree (`RankManager`), names configurable (`Gang.Rank.Head/Tail`, `/glw rank create`), `GangPromoteCommand`/`GangDemoteCommand` exist. Default seeded tree is only `member -> owner` today.",
  "lane": "W1-R seeds `member -> officer -> owner` on an empty table (D13 A). Delivers the ladder, three rungs",
  "home": "Gap: `gang_rules.yml` `Rank_Preset` (list of names) so the seed can be five rungs (S)",
  "size": "S (gap)",
  "conflict": "None. The global tree matches \"same ladder in every gang\"."
 },
 {
  "id": "GP-08",
  "status": "PARTIAL",
  "evidence": "`GangPermissions.allows` (rank node list OR Vault OR top rank) gates deposit, withdraw, rename, description, display, color, invite, ally (`gangland.gang.*`). Kick, promote, demote, delete, transfer are gated by rank position, not a node.",
  "lane": "W1-R registers the existing eight nodes, validates and completes them, seeds officer defaults. Does not add nodes for kick, home, chat, fight",
  "home": "`GangPermissions` constants + `gang_rules.yml` `Rank_Defaults` for new verbs (`gangland.gang.kick`, `.home`, `.friendlyfire`, `.fight`); reuse `allows` (S per verb)",
  "size": "S",
  "conflict": "Bank: W1-R must keep `withdraw` officer-or-above in the preset (a recruit spending the bank is TF-45)."
 },
 {
  "id": "GP-09",
  "status": "PARTIAL",
  "evidence": "Keystone `Argument.addPermission` registers `gangland.command.<root>.<leaf>` per argument; `plugin.yml` L20-28 declares only `gangland.command.main` (default op), so a non-op cannot run `/glw` at all (CM-37).",
  "lane": "W1-K CM-37 (`permissions:` block, default true for player verbs, `PluginYmlPermissionsTest`). Delivers defaults; a wildcard parent is not stated",
  "home": "`plugin.yml` `permissions:` parent node listing the player verbs as children (orchestrator-owned file) (S)",
  "size": "S",
  "conflict": "None."
 },
 {
  "id": "GP-10",
  "status": "MISSING",
  "evidence": "`Gang.level` has no producer today; W2-P1 feeds it from actively held turf only (D2 A, PLAN P4.4). `GangLevelUpEvent` exists but is never constructed.",
  "lane": "None. W2-P1 delivers XP progression, NOT a purchase (by design)",
  "home": "If wanted: a SEPARATE perk track `Gang.perk_tier` (new column) bought with the bank via `gang upgrade`, showing the amount remaining; it gates member cap, homes, chat prefix (GP-11) and NEVER `Gang.level` or the turf cap",
  "size": "M",
  "conflict": "CONFLICT with frozen D2: XP only from actively held turf and the turf cap is a function of `Gang.level`. A money-to-level path lets a rich gang buy the turf cap and skip the activity gate. New owner decision D16."
 },
 {
  "id": "GP-11",
  "status": "MISSING",
  "evidence": "No member, home or prefix limit anywhere (census gang.md section 1, grep 0 hits).",
  "lane": "None. PLAN sections 1 and 12 explicitly DROPPED member and ally caps",
  "home": "`gang_rules.yml` `Limits` per perk tier: `Max_Members`, `Max_Homes`, `Chat_Prefix`; enforced in mail `GangInviteAcceptCommand`, `GangInviteCommand` and W2-P2 `gang join`; shown on `gang info`. Chat prefix rides the W2-P2 chat format (S)",
  "size": "M",
  "conflict": "CONFLICT with the PLAN's own decision (section 12 drop). Fits the fair-fight design: a member cap bounds the plurality zerg (P3.2). Key the limits on the perk tier (GP-10), not on turf-XP `Gang.level`, or a new gang starts with no room to grow (D2 bootstrap). Enforce at accept AND at Open `gang join` (D7)."
 },
 {
  "id": "GP-12",
  "status": "PLANNED",
  "evidence": "`GangLevelUpEvent` (extends core `LevelUpEvent`) never constructed; `GangLevelMessageListener` dead.",
  "lane": "W2-P1 (`GangManager.addExperience` fires `GangLevelUpEvent`, level-up title to the gang). Delivers the event + title, NOT console-command or offline rewards",
  "home": "`gang_rules.yml` `Level_Rewards.<n>.Commands` run by a listener on `GangLevelUpEvent`; offline members handled by running the command with the member's name from the roster (S)",
  "size": "S (gap)",
  "conflict": "Rewards are a faucet on held-turf XP (D2): ship empty by default, config only."
 },
 {
  "id": "GP-13",
  "status": "PLANNED",
  "evidence": "No gang chat today (documented in gangs.md, unimplemented).",
  "lane": "W2-P2 (P4.5: `GangChatCommand` + `GangChatListener`, toggle and one-shot). Delivers ALL of gang chat; ally chat is NOT in the plan",
  "home": "Gap: an ally channel in the same listener using `GangMembership.alliedOrSame` (S)",
  "size": "S (gap)",
  "conflict": "Ally chat recipients depend on `Gang.isAlly` symmetry (W0-C) and, for the fair-fight design, on an ally cap (GP-25); none with D1-D4."
 },
 {
  "id": "GP-14",
  "status": "MISSING",
  "evidence": "Nothing exists.",
  "lane": "None",
  "home": "In the W2-P2 `GangChatListener`: staff toggle `gangland.admin.gang.spy` (in-memory set) + `gang_rules.yml` `Chat.Log_To_Console`",
  "size": "S",
  "conflict": "None; spy off by default."
 },
 {
  "id": "GP-15",
  "status": "PARTIAL",
  "evidence": "PAPI tokens exist (`%gangland_gang_display-name%`, `gang_name`, `gang_color-code` in `GangPlaceholderContribution`); a gangless/unanswered Gangland token returns a hardcoded `\"NA\"` (`GanglandPlaceholder` L160-164). No Gangland chat formatter.",
  "lane": "None (W2-P2 chat listener does not format public chat)",
  "home": "`gang_rules.yml` `Chat.Public_Tag_Format` + `Chat.No_Gang_Tag`, applied at LOW in the W2-P2 listener; configurable default for the placeholders (see GP-34)",
  "size": "S",
  "conflict": "None."
 },
 {
  "id": "GP-16",
  "status": "MISSING",
  "evidence": "No chat events.",
  "lane": "None",
  "home": "NEW `GangChatEvent` / `AllyChatEvent` in gang `events/gang/` fired by the W2-P2 listener; third-party plugins only see them if they live in `gangland-api` (GP-37)",
  "size": "S",
  "conflict": "Needs the additive api bump (see GP-37); PLAN section 2 says no new api surface through W3."
 },
 {
  "id": "GP-17",
  "status": "PARTIAL",
  "evidence": "Three listeners in three modules decide it today: `GangMembersDamageListener` (gang; unconditional cancel for same gang and allies, no config), `TurfFriendlyFireListener` (cops; owner + allies vs guards and Quartermaster), `GangAllyWeaponImpactListener` (civilians; Bartizan impacts; gated on `Settings.isGangEnabled()`, which is dead).",
  "lane": "W0-C only (symmetric `isAlly` widens who is protected). No toggle",
  "home": "`Gang.friendly_fire` column + `gang friendlyfire` (`gangland.gang.friendlyfire`, leader) + `gang_rules.yml` `Friendly_Fire.Global`; ONE fact exposed as an additive method on api `GangMembership`/`GangMembershipView` that all three listeners read (civilians and cops are gang-module-free, so this is the only legal home)",
  "size": "M",
  "conflict": "(1) Additive api minor bump vs PLAN \"no new api surface through W3\". (2) `TurfFriendlyFireListener` (guards, Quartermaster) must stay unconditional whatever the toggle, or owners kill their own guards for the D14 refund and the P6.3 bounty. (3) Toggle must not change plurality/presence counting (P3.2)."
 },
 {
  "id": "GP-18",
  "status": "MISSING",
  "evidence": "No per-world setting; `Gang.Enable` is dead (only civilians' weapon listener reads it).",
  "lane": "None",
  "home": "Worlds: `gang_rules.yml` `Friendly_Fire.Disabled_Worlds` read by the GP-17 fact (S). Region flag: soft WorldGuard hook (L, flags must be registered in the plugin's `onLoad`, before the module loader runs in `onEnable`); Gangland-native alternative `Friendly_Fire.In_Turf` (S)",
  "size": "S (worlds) / L (region flag)",
  "conflict": "A WorldGuard region flag is a second region system next to admin-drawn turfs; recommend the worlds list plus `In_Turf` first and defer the flag."
 },
 {
  "id": "GP-19",
  "status": "MISSING",
  "evidence": "Nothing. `GangMembersDamageListener` treats a self-shot as same-gang and cancels it for any gang member, so bow-boosting is blocked today.",
  "lane": "None",
  "home": "Same FF listener: `PotionSplashEvent` / `AreaEffectCloudApplyEvent` with an XSeries `XPotion` negative-effect set; `Friendly_Fire.Allow_Self_Boost` (S)",
  "size": "S",
  "conflict": "None; only matters once the GP-17 toggle exists."
 },
 {
  "id": "GP-20",
  "status": "PARTIAL",
  "evidence": "`User` holds kills, deaths, mobKills (core `User.java` L44, KDR L134); `user_kills/deaths/kd` placeholders (`GanglandPlaceholder` L215-218); counted in impl `EntityDamageListener` L121 and `PlayerDeathListener` L79/L107. No assists, no gang aggregates, no wins/losses (need GP-29), no player profile.",
  "lane": "W1-G turns `gang_stat.yml` into a stats page (content and aggregation unspecified)",
  "home": "NEW `GangStatsService` (gang module) aggregating member `User` stats over `GangManager`; assist tracking (last-damager window) in impl `EntityDamageListener`; wins/losses columns arrive with GP-29",
  "size": "M",
  "conflict": "Kill-farm guards: no count for gang-mates or allies (FF on), same victim inside a cooldown. Stats must never feed `Gang.level` (D2). Counting in gang-disabled worlds needs GP-32."
 },
 {
  "id": "GP-21",
  "status": "MISSING",
  "evidence": "No leaderboard anywhere (a `settings.yml` comment only). PLAN dropped `/glw turf top`, but that was a per-member turf score; a gang leaderboard is not blocked by that drop.",
  "lane": "None (W1-G `gang list` is paged, not sorted)",
  "home": "NEW `GangTopCommand` (`/glw gang top [stat]`) over `GangManager.getAll()`, cached about 60 s; sorts: kdr, level, members, online members, bank, turfs held (Gangland-native, from turf via `Depends`); wlr after GP-29. \"Members' total money\" needs offline wallets, omit or cache asynchronously",
  "size": "M",
  "conflict": "Bank sort exposes rich gangs as raid targets: gate with `gangland.gang.top.bank`. KDR is easy to farm with alt gangs: minimum-kills threshold."
 },
 {
  "id": "GP-22",
  "status": "MISSING",
  "evidence": "Nothing.",
  "lane": "None",
  "home": "Staff root `resetstats <player>` writing `User` kills/deaths/assists (S); the world guard is GP-32",
  "size": "S",
  "conflict": "impl `EntityDamageListener` cannot import the gang module, so the world check needs an additive api (see GP-32)."
 },
 {
  "id": "GP-23",
  "status": "PARTIAL",
  "evidence": "`GangDepositCommand`, `GangWithdrawCommand`, `GangBalanceCommand` (nodes `gangland.gang.deposit/withdraw`, `Maximum_Balance` cap). No screen shows another gang's balance today.",
  "lane": "W1-R lets a member deposit; nothing decides visibility for the new W1-G list/info or GP-21 top",
  "home": "`gangland.gang.bank.view_others` checked by `GangInfoCommand` / `GangListCommand` / `GangTopCommand` (S)",
  "size": "S",
  "conflict": "The public `gang info <name>` card in PLAN P2.5 must omit the bank by default. D15 changes what a member's withdraw share means (gangs.md drift)."
 },
 {
  "id": "GP-24",
  "status": "MISSING",
  "evidence": "`command/sub/economy/*` in impl (`EconomySetCommand`, `EconomyDepositCommand`, ...) is user-only; no gang bank staff verbs.",
  "lane": "None",
  "home": "Staff root `bank balance|give|take|reset <gang>` on `Gang.getEconomy()`, console-allowed, every write logged (`@CustomLog` + ring buffer) (S)",
  "size": "S",
  "conflict": "`give` is an unlogged faucet against the D9 economy: must log actor and amount."
 },
 {
  "id": "GP-25",
  "status": "PARTIAL",
  "evidence": "Mail: `GangAllyRequestCommand`, `GangAllyAcceptCommand`, `GangAllyRejectCommand`, `GangAllyPendingCommand`(+cancel); gang: `GangAllyAbandonCommand` (back to neutral, still takes a numeric id). No limit.",
  "lane": "W0-C makes `Gang.isAlly` symmetric, W0-D hardens mail; nothing for the cap or name-based abandon. PLAN section 12 dropped ally caps",
  "home": "`gang_rules.yml` `Limits.Max_Allies`, checked at request and at accept in mail (S); abandon by name (S)",
  "size": "S",
  "conflict": "Reverses the PLAN section 12 drop, and the fair-fight design needs it: owners + allies all count as defenders (P3.2, `CaptureService.isOwnerAlly`) and are guard-immune (`TurfFriendlyFireListener`), so an unbounded alliance is a defender blob. Recommend a default of 2-3."
 },
 {
  "id": "GP-26",
  "status": "PARTIAL",
  "evidence": "`Waypoint.WaypointType.GANG` + `gangId` + `WaypointAccess` let members teleport to their gang's waypoints; `WaypointTeleport` (api) has warm-up timer with 1.5-block move-cancel, cooldown, cost, shield, and fires a cancellable `TeleportEvent`. But creation/assignment is admin-only (`/glw waypoint create`, `gangId` sub-command under the OP-default waypoint root), all names share one global namespace (`WaypointManager.get(name)`, two gangs cannot both own \"base\"), no safe-location check, no rank gate, no per-level cap, and `WaypointManager` is impl-only (api has `WaypointLookupContract`).",
  "lane": "None. PLAN section 12: gang home/HQ out of scope beyond the existing waypoint",
  "home": "NEW `gang/home/*` in the gang module: `gang_home` table (gang id, name unique per gang, world/xyz/yaw/pitch), `gang home set|del|list|<name>`, nodes `gangland.gang.home.*`, cap from the perk tier (GP-11), NEW safe-spot check with XSeries `XMaterial`; teleports through api `WaypointTeleport` so no impl dependency",
  "size": "M (L if `WaypointLookupContract` must widen)",
  "conflict": "Fair fights: (a) a home is a raid escape hatch: veto the cancellable `TeleportEvent` when the player is inside a turf under attack; (b) `Waypoint.shield` does `player.setInvulnerable(true)` at the destination: a home inside a turf yields invulnerable defenders on the bar, so gang homes get no shield; (c) refuse `home set` inside a rival's turf or a turf under attack; (d) arrival must not count as an instant defender (D1, defenders count at once): stamp arrivals like attackers (15 s) or refuse teleport into a contested turf."
 },
 {
  "id": "GP-27",
  "status": "MISSING",
  "evidence": "No regroup, no summon.",
  "lane": "None",
  "home": "`gang regroup <home>` as a consent request (click to accept) to online members, using the GP-26 service and the same `TeleportEvent` veto (S after homes)",
  "size": "S",
  "conflict": "As GP-26 (d): a regroup into a contested turf is instant reinforcement and breaks the wake-up/plurality design; refuse it, or stamp arrivals. Only ONLINE members are reached, so D1's owners-online window is unchanged."
 },
 {
  "id": "GP-28",
  "status": "MISSING",
  "evidence": "Nothing.",
  "lane": "None",
  "home": "Staff root `homes <gang> list|tp|delete` (S) after GP-26",
  "size": "S",
  "conflict": "None."
 },
 {
  "id": "GP-29",
  "status": "MISSING",
  "evidence": "Nothing (grep for duel/arena/challenge hits only the turf capture code). PLAN's whole fight model is turf capture.",
  "lane": "None",
  "home": "NEW runtime module `gangland-fights` (`Depends: [gang]`), commands attached through a `CommandContribution` on path `gang` (`gang fight challenge|accept|decline|join|leave`), the bet moves between `Gang.getEconomy()` accounts",
  "size": "L",
  "conflict": "(1) Duel deaths hit `CustomPlayerDeathListener` (downed state: `PlayerDownedEvent`, `DownedPlayerRegistry`), cops `KillComboWantedTracker` heat and the money-drop classifier; a duel needs the generic `exemptsKill` seam PLAN parks in W4-H (TF-49, optional): promote it or ship fights after it. (2) Fights feed WLR only, never `Gang.level` (D2). (3) Wash trading with alt gangs: minimum gang age and a per-pair cooldown. (4) Participants must not appear in turf presence, boss bars or alerts."
 },
 {
  "id": "GP-30",
  "status": "MISSING",
  "evidence": "Nothing.",
  "lane": "None",
  "home": "Same module: staff verbs `arena create|delete|setspawn|setname|save|list` under the staff root (`gangland.admin.fight.*`); own two-corner region record (do not import turf's `CuboidRegion`)",
  "size": "L (within GP-29)",
  "conflict": "Arenas must not overlap a turf (a `findConflict`-style check); boundary control by a 1 Hz check plus cancelling `PlayerTeleportEvent`, not per-move handlers."
 },
 {
  "id": "GP-31",
  "status": "MISSING",
  "evidence": "Nothing.",
  "lane": "None",
  "home": "Same module: `PlayerCommandPreprocessEvent` allow/deny list in `fights/fight_rules.yml`, bypass node, `Require_Equal_Teams`, payout, WLR into GP-20 (M within GP-29)",
  "size": "M (within GP-29)",
  "conflict": "The block list must still allow `gang fight leave` and staff verbs; escrow the bet at accept, not at start, so a crash cannot mint or lose money."
 },
 {
  "id": "GP-32",
  "status": "MISSING",
  "evidence": "Even the global switch is dead: `Gang.Enable: false` no longer disables gangs (only civilians' `GangAllyWeaponImpactListener` reads it).",
  "lane": "None",
  "home": "`gang_rules.yml` `Disabled_Worlds`; NEW `GangWorldPolicy` bean checked in the `GangCommand` root pre-dispatch, the FF listener, stat counting and (turf `Depends: [gang]`) `CaptureEligibility`",
  "size": "S",
  "conflict": "Turf in a gang-disabled world must say why it is not capturable (a `CaptureEligibility` reason) or income and XP still accrue. impl-side kill counting needs an additive api (`GangWorlds`), sequence with the GP-37 bump."
 },
 {
  "id": "GP-33",
  "status": "MISSING",
  "evidence": "No combat tag anywhere (grep for combat-tag/combat-log finds nothing in source or config).",
  "lane": "None",
  "home": "No plugin dependency needed first: a gang-module PvP timer (`Combat.Tag_Seconds`) checked by `gang leave`, `home`, `regroup`, plus the same cancellable `TeleportEvent` veto; optional soft hooks for external combat-tag plugins later (S-M)",
  "size": "S-M",
  "conflict": "Also stops leave/kick/join gang-hopping mid-contest (see GP-02). No conflict with D1-D4."
 },
 {
  "id": "GP-34",
  "status": "PARTIAL",
  "evidence": "`GangPlaceholderContribution` answers about 30 tokens (`gang_name`, `display-name`, `state`, `color*`, `balance`, `members-size`, `online-members-size`, `offline-members-size`, `ally-list`, `ally-size`, level family, `user_has-gang/gang-id/rank/...`) plus `user_kills/deaths/kd`. Missing: friendly fire, leader, rank number, member name lists, gang kills/deaths/KDR/wins/losses/WLR, members' money, leaderboard tokens, configurable default (hardcoded `\"NA\"`).",
  "lane": "W1-H adds six turf tokens only",
  "home": "Extend `GangPlaceholderContribution` (the `gang_` prefix is already Gangland-owned, no impl line needed); `Placeholders.Default_Value` in `gang_rules.yml`; leaderboard tokens read the GP-21 cache (never sort per request)",
  "size": "S-M",
  "conflict": "`%gang_bounty%` returns 0 (D8). None with D1-D4."
 },
 {
  "id": "GP-35",
  "status": "HAVE",
  "evidence": "Keystone persistence: `GanglandDatabase` (`SqliteBackend`/`MysqlBackend`), `PeriodicalUpdates` autosave (`settings.yml` `Auto_Save`), diff-engine schema updates; new gang columns/tables ride it.",
  "lane": "-",
  "home": "-",
  "size": "-",
  "conflict": "None."
 },
 {
  "id": "GP-36",
  "status": "PARTIAL",
  "evidence": "Every message is customisable (`message_en.yml`, `message_es.yml`, 88 GANG_/RANK_ constants in api `Messages`); Keystone `ChatUtil.sendTitle` exists (used by cops detainment). Gang membership notifications (join/leave/kick/promote/rename) are chat only.",
  "lane": "W1-T (turf enter titles) and W2-P1 (level-up title) only",
  "home": "NEW `gang/gang_messages.yml` (`Titles` section) registered in the W1 kickoff `GangModuleFileConfig`, NEW `GangNotifier`",
  "size": "S",
  "conflict": "CLAUDE.md contract: new strings live in module YAML, not `Messages`; the W1 kickoff creates only `gang_rules.yml`, so add the messages file there (kickoff-only registration, PLAN 7.1)."
 },
 {
  "id": "GP-37",
  "status": "PARTIAL",
  "evidence": "api: `GangMembership`/`GangMembershipView` (`gangIdOf`, `gangsAllied`, `nameOf`, `alliedOrSame`, `isInstalled`), `GangItemSourceContribution`. Events: only `GangDeleteEvent`, and it lives in the gang module (invisible to a third-party plugin compiled against the api); `GangLevelUpEvent` starts firing at W2-P1.",
  "lane": "W2-P1 (level-up event only)",
  "home": "NEW events in `gangland-api` `events/gang/*` (create, join, leave with reason, rename, chat, ally chat), fired from the gang module; widen `GanglandApi.gangs()` (NEW `GangView`: list, by name, members); ONE additive minor bump shared with GP-17 and GP-32",
  "size": "M",
  "conflict": "PLAN section 2: no new api surface through W3 and W4 reads `GanglandApi.VERSION` from the merged master (2.1 or 2.2). A second additive bump must be sequenced and batched (GP-17, GP-32, GP-37 together)."
 },
 {
  "id": "GP-38",
  "status": "HAVE",
  "evidence": "`ReloadCommand` (`/glw reload`, `files`, `inventory`); beans reload through `BeanLifecycle`. W1 kickoff `GangModuleFileConfig` must reload `gang_rules.yml` (P1.2 lifecycle rule).",
  "lane": "W1 kickoff (registration only)",
  "home": "-",
  "size": "-",
  "conflict": "None."
 },
 {
  "id": "GP-39",
  "status": "PARTIAL",
  "evidence": "YAML menus exist: `gang_info.yml` (members' menu), `alliance_stat.yml`, `phone_gang*.yml`, item sources `gangs`/`gang_members`/`gang_allies` (`GangMenuItemSourceContribution`); `gang_stat.yml` is empty (`Size: 54`).",
  "lane": "W1-G fills `gang_stat.yml` as a stats page (thin, no aggregates). No player profile",
  "home": "NEW `player_profile.yml` in `gangland-impl/.../inventory/` + a `gang_profile` item source in `GangMenuItemSourceContribution`; shows kills/deaths, rank, join date, turfs held (needs GP-20)",
  "size": "M",
  "conflict": "A profile of another gang's member must not show online times or guard reserves (PLAN 5.4 rule 7)."
 },
 {
  "id": "GP-40",
  "status": "MISSING",
  "evidence": "No purge exists. Turf `InactivityReleaseTask` only releases turfs after `Inactivity_Release_Days`, keyed on `Gang.lastMemberOnlineAt`, which is written ONLY by turf (`GangPresenceTracker`/`GangPresenceListener`).",
  "lane": "None",
  "home": "NEW `GangPurgeTask` (BeanLifecycle) in the gang module tracking its own last-seen (`OfflinePlayer.getLastPlayed()` per member, checked daily) and calling `GangDisbandService` (GP-01); the disband fires `GangDeleteEvent`, so W0-A `TurfGangDeleteListener` releases turfs, no race. Player purge removes long-unseen non-owner members",
  "size": "M",
  "conflict": "A gang-module purge cannot rely on `lastMemberOnlineAt` when turf is absent (inversion). Disband payout after D15 is an equal split among offline members: decide whether a purged gang pays out or refunds the 25% fee logic. Complements the turf anti-hoarding rules."
 },
 {
  "id": "GP-41",
  "status": "MISSING",
  "evidence": "Not shipped by the competitor either; depends on the GP-29..31 module and the GP-37 api.",
  "lane": "None",
  "home": "Fights module: fight events (`FightStart/End`), spectators, rematch; exported through the same api bump as GP-37",
  "size": "L (follows GP-29)",
  "conflict": "As GP-29."
 },
 {
  "id": "GP-42",
  "status": "MISSING",
  "evidence": "No summon.",
  "lane": "None",
  "home": "Same verb as GP-27: `gang regroup` accepting either a home or the leader's position, with consent and the `TeleportEvent` veto",
  "size": "S (after GP-26)",
  "conflict": "As GP-27, plus the summoned members leave a contested turf: veto when the destination or the source is a turf under attack."
 },
 {
  "id": "GP-43",
  "status": "PARTIAL",
  "evidence": "`GangMembersCommand` lists every member with rank and online state in insertion order, no paging, not grouped by rank. Top lists do not exist (GP-21).",
  "lane": "W1-G `gang list` is paged but unsorted",
  "home": "`GangMembersCommand`: group by rank + `[page]` (S); the top lists are GP-21",
  "size": "S",
  "conflict": "Bank ranking gated as in GP-21."
 },
 {
  "id": "GP-44",
  "status": "MISSING",
  "evidence": "`GangAlliance` is stored one row per direction (`gang_ally`, `GangAllianceTable`), which fits per-side consent; ally FF is unconditionally off in all three listeners (GP-17).",
  "lane": "None (W0-C symmetric `isAlly` only)",
  "home": "`gang_ally.friendly_fire` boolean per row, `gang ally friendlyfire <gang> on|off`; effective only when both rows are on; read through the GP-17 api fact",
  "size": "M",
  "conflict": "Allies count on the owners' side of the bar (P3.2) and are guard-immune (`TurfFriendlyFireListener`): an ally you may shoot who still counts as your defender is incoherent. Recommend default off, and an FF-on ally neither counts as a defender nor shares guard immunity."
 },
 {
  "id": "GP-45",
  "status": "PLANNED",
  "evidence": "`Gang.State` (`OPEN`/`INVITE`/`CLOSE`) is persisted but inert and defaults to `OPEN` (GR-38). `Gang.description` exists (default \"Conquering the hood\", edited by `gang desc`); nothing shows it to members on login.",
  "lane": "W2-P2 (`gang join`, `gang recruiting open|invite`, D7 + GR-38 migration). Delivers ALL of open/invite; the MOTD is not in the plan",
  "home": "Gap: one line in `MemberJoinListener` showing the description to the joining member, or a NEW members-only `motd` column if the description stays public (S)",
  "size": "S (gap)",
  "conflict": "`gang join` sets `gangJoinDateLong` (already in the plan) and must respect the GP-11 member cap and the GP-02 contest re-stamp."
 }
]
```
