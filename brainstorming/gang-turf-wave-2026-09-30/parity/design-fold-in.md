# Gangs+ parity addendum to the 0.14.0 roadmap (fold-in design)

Planning only, written 2026-09-30 for wave `gang-turf-wave-2026-09-30`. No product code. This file is an **addendum**: it
does not edit `PLAN.md` or `roadmap.json`; the synthesizer folds it in. Existing lane ids and decision ids are never renamed
or renumbered; existing lanes whose scope grows are listed as **amendments**; new lanes are JSON blocks in the fixed
11-key lane shape (section 5).

Inputs: `competitors/gangsplus.md` (GP-01..45), `competitors/notes.md`, `PLAN.md` (final), `roadmap.json`,
`census/{gang,seams,ux-surface}.md`, `parity/{matrix,reuse,fights}.md`. Graph fresh (`graphify-out/graph.json`
2026-09-29 23:08 > HEAD `ff9d813f` 23:05). Oriented with `graphify explain GangMembersDamageListener | GangMembership |
GangPermissions | TeleportEvent | GangCommand | GangOptionContribution` and `graphify query GangMembership | CarDamage`, then
raw reads of the lines cited below.

Path shorthand as PLAN.md: `GG/` gang module java root, `GM/` mail, `GT/` turf, `GC/` civilians, `IMPL/` =
`gangland-impl/src/main/java/org/luckyraven/gangland/`, `API/` = `gangland-api/src/main/java/org/luckyraven/gangland/`.
**NEW** = does not exist.

**Copyright rule applied.** Gangs+ is commercial: ideas only. The matrix and fights drafts proposed verbs that copy Gangs+'s
own command wording (`gang top`, `gang levelup`, `gang regroup`, `gang player`, `fight challenge/join/leave`, a `{GANG}`
chat token, `top_<stat>_<position>_<property>` placeholders, a `gangs-friendly-fire` flag). This addendum **renames every
one** to Gangland's own vocabulary:

| Idea | Gangland wording (this addendum) |
|---|---|
| leaderboard | `/glw gang ranking [stat]`, placeholders `%gangland_gang_board_<stat>_<n>_<field>%` |
| pay for the next level | `/glw gang upgrade` (preview) then `/glw gang upgrade confirm` |
| gang homes | **safehouses**: `/glw gang safehouse [name]`, `safehouse set|remove|list` |
| regroup / summon | `/glw gang rally [safehouse]` (no argument = rally on the caller), members answer `/glw gang rally join` |
| player profile | `/glw gang profile [player]` + a `player_profile.yml` menu |
| gang MOTD | `/glw gang notice <text>|clear` |
| gang fights | `/glw gang fight propose <gang> <size> [bet]`, `accept`, `decline` (Gangland already uses both for invites), `enlist`, `drop`, `info`, `arenas`, `stats` |
| chat token for format plugins | `{glw_gang}` |
| WorldGuard flag | `glw-friendly-fire` |
| staff tools | under the existing console-allowed `/glw option gang ...` tree (`GangOptionContribution`), never a `gangadmin` root |

Friendly fire keeps the generic industry term (`gang friendlyfire`), which Factions/Towny-era plugins all share.

---

## 1. Parity table (GP-01..GP-45)

Status column from `parity/matrix.md`. "Delivered by" names an existing feature/lane, an **amended** existing lane, or a
**NEW** lane (section 5). Every row delivers; there are **no full exclusions**. Two rows ship a Gangland-native form of the
idea instead of the literal mechanism, with the reason stated (GP-33 external adapters, GP-10 buy-only levels).

| GP | Feature (our words) | Status | Delivered by | How (files verified or NEW) |
|---|---|---|---|---|
| 01 | create, rename, disband with confirm; staff disband | PARTIAL | W1-G (name rules on create/rename), **W3-GA NEW** (staff) | NEW `GG/gang/lifecycle/GangDisbandService` extracted from `GangDeleteCommand` (fires `GangDeleteEvent`, so W0-A `TurfGangDeleteListener` clears turfs); `/glw option gang disband <gang>` + confirm in `GG/gang/command/option/GangOptionContribution` |
| 02 | invite, cancel invite, accept by name, kick, leave | HAVE | W0-D (decline, 72 h expiry); **W2-R amended** (mid-contest joins); **W2-P3 NEW** (events) | Kick/leave/join route through NEW `GangMembershipService`; W2-R freezes sides at contest start (section 2, C1) |
| 03 | transfer leadership; staff set-leader | PARTIAL | **W3-GA NEW** | NEW `GG/gang/lifecycle/GangLeadership` extracted from `GangTransferCommand`; `/glw option gang leader <gang> <player>` (bypasses `RankAssignmentPolicy`'s Tail refusal only on this staff path) |
| 04 | gang list (bank column), info by gang or player, help shows only what you may run | PLANNED | W1-G **amended**, W1-K (CM-37) | `GangInfoCommand` accepts a player name and resolves their gang; bank column/card line only with NEW node `gangland.gang.balance.others`; help filtering already per permission (Keystone `Argument` L250/L277), made real by CM-37 + section 3 |
| 05 | console commands on join/leave/kick | MISSING | **W2-P3 NEW** | `gang/gang_rules.yml` `Commands.On_Join/On_Leave/On_Kick` run by `GangMembershipService` with the ASCII `name` token only (never `displayName`: console injection); reuse shape of `PlayerDeathListener.handleCommandExecution` |
| 06 | any-language names, colour-code option, hex | PARTIAL | W1-G **amended**, W1-M **amended** | Raw `name` stays ASCII (`GangNameRules`, lookup key, console token, map key); `displayName` accepts Unicode + `&#RRGGBB` behind `Display_Name.Allow_Hex`, `Display_Name.Parse_Colours`, `Display_Name.Max_Length` in `gang_rules.yml`; W1-M: a display name whose first letter is not `A-Z` falls back to the ASCII name, then a free letter (PLAN 5.1 glyph rule) |
| 07 | configurable ranks, same ladder everywhere, promote/demote | PLANNED | W1-R **amended** | `gang_rules.yml` `Rank_Preset` list (default five rungs, section 3); global tree kept (D13 A) |
| 08 | per-feature minimum rank | PARTIAL | W1-R **amended** | `Minimum_Rank` map in `gang_rules.yml` read by `GangPermissions.allows` + node inheritance toward the Tail (section 3) |
| 09 | a node per command, wildcard nodes | PARTIAL | W1-K **amended** | `plugin.yml` parents `gangland.command.gang.*` (children = player verbs, `default: true`) and `gangland.admin.gang.*` (`default: op`); `PluginYmlPermissionsTest` asserts both |
| 10 | gang pays to reach the next level, shows what is missing | MISSING | W2-P1 **amended**, D16 | NEW `GangUpgradeCommand`: XP from held turf unlocks the level (frozen D2), the bank pays the fee; preview prints XP and money still missing (section 2, D16) |
| 11 | per-level max members, safehouses, chat prefix | MISSING | W1-K (table loader), W2-P1/W2-P3/W1-GH/W2-P2 (enforcement), D16 | NEW `GG/gang/level/GangLevelRules` (pure) reads `gang_rules.yml` `Levels`; member cap in `GangMembershipService`, ally cap in mail request/accept, safehouse cap in W1-GH, prefix in W2-P2 chat |
| 12 | level-up rewards incl. offline members; level-up event | PLANNED | W2-P1 **amended** | NEW `GangLevelRewardListener` on `GangLevelUpEvent` runs `Levels.<n>.Commands` (empty by default), `Reward_Offline_Members`; api event `GangLevelChangedEvent` (W2-K) for third parties |
| 13 | gang chat (toggle + one-shot), ally chat | PLANNED | W2-P2 **amended** | `gang chat [msg]` (planned) + `gang chat allies [msg]` in the same `GangChatListener`, recipients from main-thread snapshots |
| 14 | staff chat spy, chat to server log | MISSING | W2-P2 **amended** | `gang chat spy` (node `gangland.admin.gang.spy`, in-memory set), `Chat.Log_To_Console` |
| 15 | chat-format token, default for gangless | PARTIAL | W2-P2 **amended**, W2-S | `{glw_gang}` token replaced in `AsyncPlayerChatEvent.getFormat()` at LOW; `Chat.No_Gang_Tag`; PAPI tokens already work (`%gangland_gang_display-name%`); gangless placeholder default `Placeholders.Default_Value` answered by the module (no core `NA`) |
| 16 | chat events for other plugins | MISSING | W2-K (api class), W2-P2 (fires) | NEW `API/events/gang/GangChatEvent` (cancellable; `int gangId`, `UUID sender`, channel `GANG|ALLIES`, message) |
| 17 | friendly fire per gang and global | PARTIAL | **W2-F NEW** | One rule (section 4); `gang friendlyfire on|off`; `Friendly_Fire.Global` |
| 18 | per-world FF, region flag | MISSING | **W2-F NEW** (worlds), W3-W **amended** (WorldGuard) | `Friendly_Fire.World_Overrides`; soft WorldGuard flag `glw-friendly-fire` (D20) |
| 19 | FF blocks only harmful potions; self bow-boost | MISSING | **W2-F NEW** | `PotionSplashEvent`/`AreaEffectCloudApplyEvent` drop friends for harmful `XPotion` effects only; self-damage never blocked (fixes new row GR-39) |
| 20 | player/gang kills, deaths, assists, KDR, fights W/L, profile | PARTIAL | **W2-F NEW** (assists), **W2-S NEW** (aggregates, profile), **W3-FS NEW** (W/L) | `User` kills/deaths reused (core `User.java` L44); NEW `member.assists` column; NEW `GangStatsService`; `gang profile` |
| 21 | leaderboard, sortable | MISSING | **W2-S NEW** | `gang ranking [stat]` over a 60 s main-thread snapshot; sorts kdr (min-kills floor), level, members, online, bank + members' money (node `gangland.gang.ranking.wealth`), turfs (turf contribution), wins/wlr (fights contribution) |
| 22 | staff stat reset; no stat counting where gangs are off | MISSING | **W3-GA NEW**, **W2-F NEW** | `/glw option gang stats reset <player>`; NEW core `PlayerCombatStatEvent` (cancellable) fired at kill/death credit, cancelled by gang in disabled worlds and for same-gang/ally kills |
| 23 | bank deposit/withdraw; others' balance hidden | PARTIAL | W1-G **amended**, W2-S | `gangland.gang.balance.others` on info/list/ranking |
| 24 | staff bank balance/give/take/reset | MISSING | **W3-GA NEW** | `/glw option gang bank <gang> balance|give|take|reset`, console-allowed, every write logged with actor and amount (`@CustomLog`) |
| 25 | alliance request, back to neutral, alliance limit | PARTIAL | **W2-P3 NEW** | Ally cap per level checked at `GM/command/ally/GangAllyRequestCommand` and ally accept; `GangAllyAbandonCommand` takes a name |
| 26 | several named homes, safe check, level cap, switchable | PARTIAL | **W1-GH NEW** | Safehouses: NEW `gang_safehouse` table, transient `Waypoint` (type `GANG`, `shield` 0) through api `WaypointTeleport` (warm-up, move-cancel, cooldown, cost, `TeleportEvent`); NEW `SafeSpot` check (`Safehouse.Safe_Check`); `Safehouse.Enabled` |
| 27 | regroup members at a home | MISSING | **W1-GH NEW** | `gang rally <safehouse>`: consent line to online members, `gang rally join` within 60 s |
| 28 | staff home tools | MISSING | **W3-GA NEW** | `/glw option gang safehouse <gang> list|tp|remove` |
| 29 | challenge, bet, accept/decline, join/leave | MISSING | **W3-FK/FS/FG NEW** (D17) | NEW module `gangland-gang-fights` (id `fights`, `Depends: [gang]`), `CommandContribution` on path `gang` |
| 30 | several arenas, held inside, admin arena tools | MISSING | **W3-FK NEW** | 3D `ArenaBox`, N spawns per side, Exit, `fight/arenas.yml`, `/glw arena ...` (fights.md s3) |
| 31 | command blocking, equal teams, winner takes bet, W/L stats | MISSING | **W3-FS/FG NEW** | escrow at accept, `Require_Equal_Teams`, namespace-stripping command filter, `fight_stats` table |
| 32 | disable gangs in chosen worlds | MISSING | **W2-F NEW**, W2-K, W2-R **amended** | NEW `GangWorldPolicy` (`Disabled_Worlds`; also honours the dead `Gang.Enable`, row GR-40); `GangCommand` root pre-dispatch check (W2-K); `CaptureEligibility` reason `gangs are off in this world` (W2-R) |
| 33 | combat-tag support | MISSING | **W2-F NEW** | Built-in tag: NEW `GangCombatState` ledger (`Combat.Tag_Seconds` 15) blocks `gang leave`, safehouse/rally teleports (gang `TeleportEvent` listener) and `gang fight enlist`. **Deferred until a named combat-tag plugin is requested:** a soft adapter per third-party plugin (each is a new compile dependency; the built-in tag already delivers "no escape mid-combat") |
| 34 | ~25 placeholders + leaderboard tokens + configurable default | PARTIAL | **W2-S NEW** | `GangPlaceholderContribution` gains the missing tokens (s1 list below); `gang_board_*` via NEW default `PlaceholderContribution.resolveGlobal` + null-player branch in `IMPL/data/placeholder/worker/GanglandPlaceholder` |
| 35 | MySQL/SQLite, periodic save, schema updates | HAVE | - | Keystone persistence; new columns/tables ride the diff engine |
| 36 | titles, customisable messages, any language | PARTIAL | W1-K **amended**, **W2-P3 NEW** | NEW `gang/gang_messages.yml` (`LocalizedModuleYaml`), `Titles` section, NEW `GangNotifier`; `LocalizedModuleYaml` tries `<base>_<code>.yml` for any picked language (body change only) |
| 37 | public API: queries and events | PARTIAL | W2-K (api), **W2-P3 NEW** (implements), D19 | api events create/join/leave(reason)/level/chat carrying ids, never `Gang`; `default` methods on `GangMembershipView` (`gangIds()`, `idByName`, `memberIds`) |
| 38 | admin reload | HAVE | W1-K / W2-K | `GangModuleFiles` reload through `BeanLifecycle` |
| 39 | player and gang profile GUI (never shipped by Gangs+) | PARTIAL | **W2-S NEW** | NEW `inventory/player_profile.yml`, `gang_stat.yml` gains aggregates, NEW `gang_ranking.yml` over item source `gang_ranking` |
| 40 | auto-purge inactive players and gangs (never shipped) | MISSING | **W3-GA NEW** | NEW `GangPurgeTask` (`BeanLifecycle`, daily, `OfflinePlayer.getLastPlayed`), default **off**, calls `GangDisbandService` |
| 41 | more fight features, events, API (never shipped) | MISSING | **W3-FK/FS NEW** | api `GangFightStartedEvent`/`GangFightEndedEvent` (W3-FK), N spawns, Exit, ranked vs sparring, rematch cooldown, per-pair caps |
| 42 | summon all members | MISSING | **W1-GH NEW** | `gang rally` with no argument rallies on the caller's position (snapshot at send) |
| 43 | top by balance and size; member list by rank | PARTIAL | W1-G **amended**, W2-S | `GangMembersCommand` grouped by rank with `[page]`; ranking sorts cover bank and members |
| 44 | negotiated ally friendly fire | MISSING | **W2-F NEW** | NEW `gang_ally.friendly_fire` per direction; effective only when both rows are on; `gang ally friendlyfire <gang> on|off` |
| 45 | open vs invite-only; gang notice | PLANNED | W2-P2 (D7) **amended** | `gang notice <text>|clear`, shown on login by `MemberJoinListener`; NEW `gang.notice` column |

GP-34 token list (all `gang_`/`user_` prefixed, dash style): `gang_friendly-fire`, `gang_leader`, `gang_member-list`,
`gang_online-list`, `gang_kills`, `gang_deaths`, `gang_kdr`, `gang_assists`, `gang_members-money`, `gang_safehouses`,
`gang_notice`, `gang_member-cap`, `gang_ally-cap`, `gang_upgrade-cost`, `gang_upgrade-xp`, `user_rank-number`,
`user_assists`, `gang_board_<stat>_<n>_<name|value|level>`; fights adds `gang_fight-wins|losses|wlr` through its own
contribution. Board tokens must never contain `user_` or `bank_` (core substring dispatch runs first, reuse s4.2).

---

## 2. Conflicts with fair fights and frozen D1-D4, and their resolution

| # | Collision | Resolution (lane) | Decision |
|---|---|---|---|
| C1 | **Join/accept/ally mid-contest** (GP-02, 25, 45): a player standing inside joins the owner gang, or a third gang allies the owners, and becomes an instant defender (defenders count at once, P3.2); symmetrically a bystander joins the attacking gang and counts after 15 s. | **Sides freeze at contest start, for both sides.** In `classify`, a player counts on **any** side only if their `Member.gangJoinDateLong` is **before** the contest start stamp, and an ally counts for the owners only if `GangAlliance.since` (set to `Instant.now()` at `Gang.java` L95) is before it too; later arrivals are bystanders until the contest ends. `TurfRuntimeState` has no start stamp today (only `lastChallengerSeenAt`, L36); the stamp W2-R adds for `Max_Contest_Minutes` is reused. Pure rule, no listener, no event. **W2-R amended.** | none |
| C2 | **Safehouses as raid escapes / instant reinforcement** (GP-26, 27, 42): teleport out of a turf under attack, rally into one, invulnerable arrivals (`Waypoint.shield` calls `setInvulnerable`). | NEW `GT/listener/TurfSafehouseGuardListener` cancels the api `TeleportEvent` (fired by `WaypointTeleport` before the jump) when the source **or** destination is inside a turf with a running contest; safehouse waypoints are built with `shield` 0; NEW gang event `GangSafehouseSetEvent` (cancellable) lets the same turf listener refuse `safehouse set` inside another gang's turf or any turf under attack. D1's owners-online window is untouched (rally reaches online members only). **W1-GH NEW.** | none |
| C3 | **Combat escapes** (GP-33): leave the gang or teleport home mid-fight. | `GangCombatState` tag blocks `gang leave`, every waypoint `TeleportEvent` for a tagged player (gang listener, covers safehouses and rally), `gang fight enlist`. **W2-F NEW**, leave check in **W2-P3 NEW**. | none |
| C4 | **Friendly-fire toggles vs guards, allies, civilians, cars** (GP-17/44): owners could farm their own guards (D14 refund, P6.3 bounty); allies that may shoot each other still count as defenders. | Section 4: the toggles change **player-vs-player damage only**. Guard and Quartermaster immunity for owners and allies stays unconditional in cops `TurfFriendlyFireListener` (untouched). Defender counting, ally chat and guard immunity ignore FF flags. Kills between same-gang or allied players never count for stats (anti-farm, `PlayerCombatStatEvent`). Civilians are not gang members, so no change. Cars stay outside the rule (property; `CarDamageListener` has no gang check on damage, only pickup uses `CarAccessPolicy.sharesGang`), recorded in the migration doc. | none |
| C5 | **Bought levels vs D2** (GP-10): money buying `Gang.level` buys the turf cap and skips the activity gate. | Hybrid (D16 C): XP still comes only from actively held turf; `gang upgrade` needs `experience >= Level.experienceCalculation(nextLevel())` **and** the fee. The command must check XP itself: core `Level.addLevels` (L74-89) levels even when `experience < requiredExp`. Turf cap column = `min(6, 2 + level)`, unchanged. | **D16** |
| C6 | **Member and ally caps cut by the synthesis** (PLAN s1 "Dropped", s12). | Reinstated as columns of the D16 level table (`-1` = unlimited per row). They also bound the plurality zerg (P3.2) and the ally defender blob (`CaptureService.isOwnerAlly` L169). Caps apply to growth only: existing over-cap gangs keep everyone and cannot recruit or ally until under. PLAN s12 "member and ally caps" moves out of Out-of-scope. | **D16** |
| C7 | **Fights vs turf, wanted, downed, drops** (GP-29..31). | fights.md s5: NORMAL-priority lethal intercept runs before `EntityDamageListener` (HIGH) and `CustomPlayerDeathListener` (HIGHEST); no wanted, bounty, drop, downed state. Turf: fighters flagged in `GangCombatState.isFighting` are skipped by W2-R's `classify` (one line, read from day one; fights sets it in W3). Allied gangs cannot fight (bet laundering). Fights feed W/L only, never `Gang.level` (D2). | **D17** |
| C8 | **Short aliases vs command wording** (GP-04, 13). | Owner's call; default recommendation uses Gangland labels. | **D18** |
| C9 | **"No new api surface in W0-W3"** (PLAN s2) vs GP-16/22/34/37 and fights events. | All additions are additive: api event classes, `default` methods (`GangMembershipView` is implemented by the gang module; an abstract method would break a 2.0-built jar), one core event. Land in kickoffs W2-K/W3-FK; `GanglandApi.VERSION` bumped once by W4-R. FF needs **no** api: the Bartizan-impact half moves into the gang module (section 4). | **D19** |
| C10 | **WorldGuard region flag** (GP-18): a second region system beside turfs; WG flags register only in `onLoad`, before the module loader. | Core registers the flag in `Gangland.onLoad` (`IMPL/Gangland.java` L52) behind a plugin-present check; the gang module reads it **by name** through WorldGuard's registry, so no Gangland api seam. Soft dependency only. | **D20** |
| C11 | **Unicode names vs the map glyph class** (GP-06, PLAN 5.4 rule 2). | ASCII `name`, free `displayName`; W1-M glyph fallback (row GP-06). | none |
| C12 | **Stats in gang-disabled worlds** (GP-22): kills are credited in impl `EntityDamageListener` L121, which cannot import the gang module. | NEW core `PlayerCombatStatEvent` (see C9) fired at L121 and `PlayerDeathListener` L79/L107; gang cancels it. | covered by D19 |
| C13 | **Existing plan overlap found while folding:** PLAN 7.6 gives `GG/gang/Gang` to **both** W2-P1 (level) and W2-P2 (state default). | Member-level ownership in the shared table (section 5.1): W2-K adds every new field; W2-P1 edits only the `Level` construction, W2-P2 only `Gang.State`; merge P1 before P2. | none |

---

## 3. Rank model (GP-07/08, reconciled with D13)

**Test.** Does the global rank tree + `GangPermissions` + a five-rung preset + a per-feature minimum-rank map satisfy GP-07/08?

- GP-07 "same ladder in every gang, names configurable, promote/demote": the tree is already global (`RankManager`, one tree,
  census gang.md s1), names are admin-editable (`/glw rank ...`), `GangPromoteCommand`/`GangDemoteCommand` walk it. Missing
  only a longer default ladder. **Satisfied** by a preset list.
- GP-08 "which rank may invite, kick, withdraw, set a home, ally, fight": `GangPermissions.allows(member, player, node)`
  (`GG/gang/permission/GangPermissions.java` L67-77) = server permission OR the rank's **own** node list OR top rank. Two gaps:
  (a) a node on `officer` does not reach `underboss` (no inheritance; `ranks.md` documents inheritance that is not implemented,
  census s5.2), so "minimum rank" cannot be expressed; (b) kick/promote/demote/transfer are gated by tree position only.
  `Tree.isDescendant` and `Node.getParent` already exist (`RankAssignmentPolicy` L74-107, `RankManager` L254).

**Verdict: per-gang ranks are not needed.** The global tree satisfies both once three small things land in **W1-R (amended)**:

1. **Inheritance toward the Tail.** `allows` walks the member's rank node up through `getParent()` and passes if any ancestor
   holds the node (root = Head = lowest). `RankPermissionApplier` applies the same inherited set to the live
   `PermissionAttachment`, or `player.hasPermission` and `member.hasPermission` diverge. Existing installs only gain rights
   (widening); migration note.
2. **`Minimum_Rank` map** in `gang_rules.yml`, read at runtime by `allows` as one more clause: pass when the member's rank is
   the named rank or a descendant of it. A name missing from the tree is ignored with one warning (old behaviour stands). Rank
   nodes stay for per-rank extras; the map is the config surface GP-08 asks for.
3. **Nodes for the new verbs** (constants added by the W1-K kickoff so W1-GH can reference them):
   `gangland.gang.{kick, safehouse, safehouse.manage, rally, friendlyfire, upgrade, fight, notice, chat, recruiting}`.
   Kick becomes position check **and** the node when its `Minimum_Rank` entry resolves (else position only, so an
   existing tree without the named rank behaves as today).

**Preset (GP-07).** `Rank_Preset` list, seeded on an **empty** rank table only (D13 A unchanged in substance). Default five
rungs `member, soldier, officer, underboss, owner`: first = `Gang.Rank.Head` default `member`, last = `Gang.Rank.Tail`
default `owner` (settings.yml), so defaults agree; if an admin edits the preset so that first/last disagree with Head/Tail,
the seed logs one warning and uses the settings names for the ends. Invite acceptance still joins at Head.

Default `Minimum_Rank` (shipped in `gang_rules.yml`): chat, deposit, safehouse = `member`; fight, rally = `soldier`;
invite, kick, withdraw, safehouse.manage, recruiting = `officer`; ally, rename, description, display, color, notice,
upgrade = `underboss`; friendlyfire = `owner`. `withdraw` stays officer-or-above (TF-45 lesson).

**D13 reconciliation:** D13 stays **A** (global tree, seed on empty tables, no schema change). Its preset becomes the
`Rank_Preset` list (default five instead of three) and it gains inheritance + `Minimum_Rank`. Option C (per-gang trees) stays
rejected: `rank_tree` has no gang column and nothing in GP-07/08 needs one.

---

## 4. One friendly-fire rule

**Owner:** the gang module only. NEW pure `GG/gang/pvp/FriendlyFireRule.blocks(attacker, victim, world, cause)`, read by
every player-vs-player damage path. **No api change:** civilians' `GangAllyWeaponImpactListener`
(`GC/listener/gang/GangAllyWeaponImpactListener.java`, the Bartizan-impact half, `@ListenerHandler(condition =
"isBartizanAvailable")`, reading the dead `Settings.isGangEnabled()` at L42) **moves** into the gang module as NEW
`GG/listener/gang/GangWeaponImpactListener` with the same condition; `gangland-gang/pom.xml` gains `bartizan-api` at
`provided` (W2-F is the named pom owner; allowed for a runtime module per CLAUDE.md). Civilians loses its only gang listener.

**The rule** (first match wins):

1. Not both players in a gang, or the attacker **is** the victim -> **allow** (self bow-boost and self-damage always pass;
   fixes GR-39: today `GangMembersDamageListener` L41-48 cancels a gang member's own arrow).
2. World in `Disabled_Worlds` (or `Gang.Enable: false`) -> gangs do not exist there -> **allow**.
3. WorldGuard region flag `glw-friendly-fire` at the victim (when WorldGuard is present, W3-W) -> ALLOW or DENY decides.
4. `Friendly_Fire.World_Overrides.<world>` = `ALLOW` | `BLOCK` decides.
5. `Friendly_Fire.Global` = `FORCE_ALLOW` | `FORCE_BLOCK` decides; `GANG_CHOICE` (default) falls through.
6. Same gang -> **block** unless the gang's `friendly_fire` is on (`gang friendlyfire on|off`, node `gangland.gang.friendlyfire`).
7. Allied (symmetric `Gang.isAlly` after W0-C) -> **block** unless **both** `gang_ally.friendly_fire` rows are on
   (`gang ally friendlyfire <gang> on|off`, each side sets its own row; GP-44).
8. Otherwise **allow**.

**Potions (GP-19):** on `PotionSplashEvent` / `AreaEffectCloudApplyEvent`, a "block" drops the victim from the affected set
only when the effect is harmful (XSeries `XPotion` set: poison, harm, weakness, slowness, wither, blindness, nausea, hunger,
mining fatigue, levitation, bad omen); helpful potions reach friends. Controlled by `Friendly_Fire.Harmful_Potions_Only` (true).

**Readers:** `GangMembersDamageListener` (vanilla melee + projectiles, LOWEST, rewritten, GR-15 null guard kept),
`GangWeaponImpactListener` (Bartizan `WeaponRaytraceImpactEvent`, moved), NEW `GangPotionFriendlyFireListener`.

**Out of the rule, by design, stated once in `documentation/features/gangs.md`:**
- Guards and the Quartermaster: owners and allies can never hurt them, whatever any toggle says (cops
  `TurfFriendlyFireListener`, unchanged) - protects the D14 refund and the P6.3 bounty.
- Turf: FF flags never change who counts on the bar, who is shielded or guard immunity.
- Stats: same-gang and allied kills never count (`PlayerCombatStatEvent` cancelled), so FF-on cannot farm KDR.
- Cars: property, outside the rule (today any player can damage any car; gang-mate car griefing is a car-access question).
- Fights: opposing fighters are never allied (fights rule); teammates follow this rule.

---

## 5. New lanes, amendments, file ownership

**Wave count stays five.** No new wave: the fights lanes sit in **W3** because they need W0-C (symmetric `isAlly`), the W1
`plugin.yml` permissions block (CM-37), W1-R's preset (officer-level fight node), the gang YAML plumbing (W1-K) and
`GangCombatState` (W2), and they touch none of W3's turf/civilians files. W3 opus peak: W3-A, then W3-FG (it starts after
W3-FK and W3-FS merge), then the gate reviewer: at most 2 at once.

### 5.1 Additions to PLAN 7.1 (shared files)

| File | Owner / rule |
|---|---|
| `GG/gang/GangConfig.java` | lanes add `@Bean` methods only (W1-GH, W2-F, W2-P3, W2-S, W3-GA), never edit another lane's bean |
| `GG/command/sub/gang/GangCommand.java`, `GG/command/sub/gang/ally/GangAllyCommand.java` | kickoff-only: W1-K registers `safehouse`, `rally`, `profile` stubs; W2-K registers `ranking`, `upgrade`, `friendlyfire`, `notice`, `ally friendlyfire`, the `chat allies`/`chat spy` children and the `GangWorldPolicy` pre-dispatch check |
| `gang/gang_rules.yml`, `gang/gang_messages.yml` | kickoffs create every section (W1-K: names, display, ranks, `Minimum_Rank`, safehouse, `Levels` table; W2-K: chat, friendly fire, worlds, combat, commands, placeholders); lanes append lines inside their own section only |
| `GG/gang/Gang.java`, `GG/gang/GangAlliance.java`, `GG/gang/member/Member.java`, `GG/gang/database/tables/*` | W2-K adds every new field/column (`gang.friendly_fire`, `gang.notice`, `gang_ally.friendly_fire`, `member.assists`); W2-P1 edits only the `Level` construction; W2-P2 only `Gang.State` + its migration; merge P1 before P2 (C13) |
| `GG/gang/placeholder/GangPlaceholderContribution.java` | W2-P1 one line (`%gang_bounty%` -> 0, D8), then W2-S owns it; S merges after P1 |
| `GG/gang/permission/GangPermissions.java` | W1-K adds the new node constants; W1-R owns the body |
| `GG/gang/command/option/GangOptionContribution.java` | W3-GA only |
| `gangland-gang/pom.xml` | W2-F (`bartizan-api` provided), W3-W (`worldguard` provided) |
| `gangland-impl/pom.xml`, root `pom.xml` repositories | W3-W (`worldguard` provided in impl); the root repository entry is an orchestrator line at W3-W |
| `API/**` | kickoffs only: W1-K (`LocalizedModuleYaml` body), W2-K (events, `default` methods), W3-FK (fight events) |
| `gangland-core` new event `PlayerCombatStatEvent` | W2-K creates; W2-F fires it in impl |
| impl `listener/player/EntityDamageListener`, `PlayerDeathListener` | W2-F in W2 (fire sites); W4-H keeps its W4 ownership of `EntityDamageListener` |
| impl `data/placeholder/worker/GanglandPlaceholder` | W1-K one line (`turf-`), W2-S the null-player branch |
| impl `Gangland.java` (`onLoad`) | W3-W (WorldGuard flag registration) |
| `gangland-impl/src/main/resources/inventory/*.yml` | W1-G (existing three), W2-S (`gang_stat.yml` content, NEW `player_profile.yml`, NEW `gang_ranking.yml`) |
| mail `GangInviteAcceptCommand`, `GangAllyRequestCommand`, `GangAllyAcceptCommand` | W0-D in W0, W2-P3 in W2 |
| `gangland-features/pom.xml`, `gangland-build/pom.xml`, `IMPL/command/sub/module/ModuleInstalls.java` | orchestrator lines at W3-FK |

### 5.2 Amendments to existing lanes (scope grows, id and model unchanged)

| Lane | Added scope | Added tests |
|---|---|---|
| W1-K | `gang/gang_messages.yml` registration; `gang_rules.yml` sections above incl. the `Levels` table; NEW `GG/gang/level/GangLevelRules` (pure loader + `limits(level)`, turf-cap column asserted equal to `min(6, 2 + level)`); node constants; `safehouse`/`rally`/`profile` stubs; `LocalizedModuleYaml` any-language lookup; `plugin.yml` wildcard parents + the D18 shortcut commands and NEW `IMPL/command/CommandShortcutExecutor` (forwards + tab-completes into `CommandManager.onCommand`) | `GangLevelRulesTest` (table, inheritance of missing rows, D2 column), `LocalizedModuleYamlTest` (`_de` picked, falls back to `en`), `PluginYmlPermissionsTest` wildcard cases, `CommandShortcutExecutorTest` |
| W1-G | `gang info <player>`; `balance.others` gate; display-name knobs in `GangDisplayCommand`; `GangMembersCommand` grouped by rank + `[page]` | name vs display table test incl. Cyrillic display, hex allowed/denied; `GangInfoCommandTest` (player-name resolution, bank hidden) |
| W1-R | inheritance in `allows` + `RankPermissionApplier`; `Minimum_Rank`; `Rank_Preset` (five); kick node gate | NEW `GangPermissionsInheritanceTest` (officer node reaches underboss, not soldier), `MinimumRankTest` (missing rank ignored), `RankSeedTest` five rungs + Head/Tail mismatch warning |
| W1-M | glyph fallback for a non-`A-Z` display initial | `TerritoryRasterTest` case (CJK display name -> ASCII initial) |
| W2-K | new columns/fields; stubs; api `GangCreatedEvent`, `GangMemberJoinedEvent`, `GangMemberLeftEvent(reason LEFT/KICKED/DISBANDED/PURGED)`, `GangLevelChangedEvent`, `GangChatEvent` (ids and names only); `GangMembershipView` `default` methods `gangIds()`, `idByName(String)`, `memberIds(int)` **plus** forwarding methods on the final `GangMembership` holder (the type `GanglandApi.gangs()` returns) with the same absent-default pattern as `nameOf`; core `PlayerCombatStatEvent(kind KILL|DEATH, player, @Nullable killer)` whose `PlayerDownedEvent` fire site supplies the killer; `PlaceholderContribution` `default resolveGlobal(String)`; gang `GangMembershipService`, `GangCombatState`, `GangWorldPolicy`, `GangStatContribution` signatures | one trivial test per class; api binary-compat test: a `GangMembershipView` lambda compiled without the new methods still loads |
| W2-R | sides frozen at contest start (C1) using its contest-start stamp; skip `GangCombatState.isFighting`; `CaptureEligibility` reason for gang-disabled worlds | NEW cases in the classify table test: joined-after-start member = bystander on the owner side **and** on the attacking side, ally-since-after-start = bystander, fighter skipped, disabled world reason |
| W2-P1 | XP accrues with `Level.addExperience(xp, false, null)` (no auto level) unless `Auto_Level_Up: true`; NEW `GangUpgradeCommand` (preview + confirm, XP gate checked in code, bank fee, amounts missing); NEW `GangLevelRewardListener`; fires api `GangLevelChangedEvent` | `GangUpgradeCommandTest` (XP short -> refused with both amounts; money short; both met -> one level, fee withdrawn once; `addLevels` never called below the XP bar); a cancelled `GangLevelUpEvent` withdraws no fee (the fee is taken only after `addLevels` returns 1; core `Level.addLevels` L74-89 deducts the XP before firing the event, so a cancel still eats the XP - pre-existing core behaviour, documented, not changed); rewards run for offline members when enabled; days-to-level table test restated for cumulative XP (1 turf: L1 1.4 d, L2 5.3 d, L3 12.5 d) |
| W2-P2 | `gang chat allies`, `gang chat spy`, `Chat.Log_To_Console`, `{glw_gang}` + `Chat.No_Gang_Tag`, level prefix from `GangLevelRules`, fires `GangChatEvent`; `gang notice` + login line in `MemberJoinListener`; `gang join` calls `GangMembershipService.join` | recipient filter (gang, allies, spy) pure test; format token test; notice shown once per login; async-safety test (snapshot read only) |
| W2-E1/E2, W1-E1/E2, W3-E1/E2 | `documentation/features/gangs.md` + `ranks.md` rewrite (real nodes, inheritance, `Minimum_Rank`), safehouses, friendly fire rule, ranking, fights; migration entries (section 7) | - |
| W3-W | soft WorldGuard: `softdepend: WorldGuard` (orchestrator line), flag registration in `Gangland.onLoad` from a type-free factory, gang-side reader by flag name behind `Bukkit.getPluginManager().getPlugin("WorldGuard") != null`; `worldguard` at `provided` in `gangland-impl/pom.xml` and `gangland-gang/pom.xml` (W3-W named pom owner for both in W3), the root `pom.xml` repository entry is an orchestrator line at W3-W; reflection-facade fallback if Maven cannot fetch it (same as dynmap) | NEW `WorldGuardFlagReaderTest` (absent plugin -> no opinion; ALLOW/DENY map to rule step 3); smoke G1 without WorldGuard, no `reflection.type.missing` |
| W4-R | the single `GanglandApi.VERSION` bump covers the W2-K/W3-FK additions; its `Host_Api` table adds gang, mail, fights | `GanglandApiVersionTest` if present, else manual check in `documentation/gangland-api.md` |

### 5.3 New lanes (roadmap.json shape)

Insert each block into its wave's `lanes` array **before** that wave's gate lanes (`W1-V`/`W1-J`, `W2-G`/`W2-J`,
`W3-G`/`W3-J`).

**W1 (after W1-R):**

```json
{
 "id": "W1-GH",
 "name": "Safehouses and rally",
 "model": "sonnet",
 "role": "executor",
 "parallel": true,
 "worktree": "E:/Programming/java/wt/gt14-w1gh-safehouse",
 "scope": "GP-26/27/28(read side)/42. NEW gang_safehouse table + repository (gang_id, name unique per gang, world, x, y, z, yaw, pitch; setDataSupplier wired; rows deleted in the GangDeleteEvent path); GangSafehouseManager; safehouse set|remove|list|<name> bodies (nodes gangland.gang.safehouse / .safehouse.manage); cap from GangLevelRules.limits(level).maxSafehouses(); NEW SafeSpot check (2-high passable, solid floor, no lava/fire, XMaterial; Safehouse.Safe_Check) with a ponytail note naming Keystone as the promotion target; teleport through a transient api Waypoint (type GANG, shield 0, timer/cooldown/cost from gang_rules.yml) and WaypointTeleport; rally <safehouse> and rally (caller position) with a 60 s consent line and rally join; NEW cancellable GangSafehouseSetEvent; NEW GT/listener/TurfSafehouseGuardListener cancelling TeleportEvent when source or destination is inside a turf with a running contest and GangSafehouseSetEvent inside another gang's turf or a contested turf.",
 "modules": [
  "gangland-gang",
  "gangland-turf (one NEW listener)"
 ],
 "docket": [],
 "tests": "NEW GangSafehouseRepositoryTest (@TempDir cleanup NEVER, per-gang name uniqueness, delete on disband); NEW SafeSpotTest (pure, block fixtures); NEW GangSafehouseCommandTest (cap by level, rank gate, shield 0 on the built Waypoint); NEW GangRallyCommandTest (consent expiry, only online members, rally on caller snapshot); NEW TurfSafehouseGuardListenerTest (contest at source -> cancelled, at destination -> cancelled, quiet turf -> allowed, set inside rival turf -> cancelled); GangCommandsJsonParityTest green",
 "exit": "a member cannot leave or enter a turf under attack by safehouse or rally; a safehouse never grants invulnerability"
}
```

**W2 (after W2-P2, before W2-Q):**

```json
{
 "id": "W2-P3",
 "name": "Membership lifecycle, caps and notifications",
 "model": "sonnet",
 "role": "executor",
 "parallel": true,
 "worktree": "E:/Programming/java/wt/gt14-w2p3-lifecycle",
 "scope": "GP-05/11/25/36/37. GangMembershipService body: the single join/leave/kick path (create, invite accept, gang join, leave, kick, disband), member cap from GangLevelRules, combat-tag refusal on leave, console Commands.On_Join/On_Leave/On_Kick with the ASCII name token, api GangMemberJoinedEvent/GangMemberLeftEvent(reason)/GangCreatedEvent fired once; ally cap at GM GangAllyRequestCommand and ally accept; GangAllyAbandonCommand by name with completion; NEW GangNotifier + gang_messages.yml Titles for join/leave/kick/promote/demote/transfer/rename; GangMembershipInstaller implements the new GangMembershipView defaults. Owns in W2: GG/command/sub/gang/{GangCreateCommand,GangLeaveCommand,GangKickCommand,GangDeleteCommand,GangPromoteCommand,GangDemoteCommand,GangTransferCommand,GangRenameCommand}, GG/command/sub/gang/ally/GangAllyAbandonCommand, GG/gang/GangMembershipInstaller, GM/command/invite/GangInviteAcceptCommand, GM/command/ally/{GangAllyRequestCommand,GangAllyAcceptCommand}, NEW GG/gang/lifecycle/**.",
 "modules": [
  "gangland-gang",
  "gangland-mail",
  "gangland-api (implements kickoff signatures only)"
 ],
 "docket": [],
 "tests": "NEW GangMembershipServiceTest (every join site and leave site fires exactly one event with the right reason; cap refuses the n+1th join at accept and at gang join; over-cap gang keeps members; tagged player cannot leave); NEW ConsoleCommandTokenTest (name with punctuation rejected upstream, display name never spliced); NEW AllyCapTest (request and accept both refuse); GangAllyAbandonCommandTest by name; NEW GangMembershipViewDefaultsTest; MailManagerTest and GangMailCommandGuardTest stay green; GangCommandsJsonParityTest",
 "exit": "no code path adds or removes a member outside GangMembershipService (grep: addMember/removeMember only there and in the repository load)"
}
```

```json
{
 "id": "W2-F",
 "name": "Friendly fire, worlds and combat state",
 "model": "sonnet",
 "role": "executor",
 "parallel": true,
 "worktree": "E:/Programming/java/wt/gt14-w2f-pvp",
 "scope": "GP-17/18(worlds)/19/22(world gate)/32/33/44 and the assists half of GP-20. NEW pure FriendlyFireRule (section 4 of parity/design-fold-in.md); rewrite GangMembersDamageListener (self-damage allowed, GR-39; GR-15 guard kept); move GC/listener/gang/GangAllyWeaponImpactListener to NEW GG/listener/gang/GangWeaponImpactListener (same isBartizanAvailable condition, delete the civilians file and its test); NEW GangPotionFriendlyFireListener; gang friendlyfire and gang ally friendlyfire bodies; GangWorldPolicy body (Disabled_Worlds, World_Overrides, Gang.Enable false = disabled everywhere, GR-40); GangCombatState body (MONITOR ledger on EntityDamageByEntityEvent player-vs-player, Combat.Tag_Seconds 15, Assist_Window_Seconds 10, isFighting flag set/cleared by fights later); gang TeleportEvent listener refusing waypoint teleports for tagged players; fire core PlayerCombatStatEvent at IMPL EntityDamageListener L121 and PlayerDeathListener L79/L107, and a gang listener cancelling it in disabled worlds and for same-gang/allied pairs, crediting member.assists; gangland-gang/pom.xml bartizan-api provided (named pom owner).",
 "modules": [
  "gangland-gang",
  "gangland-civilians (one listener deleted)",
  "gangland-impl (two fire sites)"
 ],
 "docket": [
  "GR-39",
  "GR-40"
 ],
 "tests": "NEW FriendlyFireRuleTest (table over steps 1-8: self, disabled world, world override, global force, same gang on/off, ally one-sided vs both-on, gangless); flip or add GangMembersDamageListenerTest self-shot case (red: cancelled today); NEW GangWeaponImpactListenerTest (moved cases green, toggle honoured); NEW GangPotionFriendlyFireListenerTest (harmful removed, healing kept); NEW GangWorldPolicyTest (Gang.Enable false, list); NEW GangCombatStateTest (tag expiry on a fake clock, assist window, ally damage not tagged); NEW PlayerCombatStatEventTest in impl (cancelled event leaves User kills/deaths unchanged, uncancelled increments once); civilians module tests green without the moved listener",
 "exit": "one class decides player-vs-player friendly fire; Bartizan guns obey gang friendlyfire on; guards stay immune to owners and allies"
}
```

```json
{
 "id": "W2-S",
 "name": "Stats, ranking and profiles",
 "model": "sonnet",
 "role": "executor",
 "parallel": true,
 "worktree": "E:/Programming/java/wt/gt14-w2s-stats",
 "scope": "GP-20(aggregates)/21/23(ranking gate)/34/39/43(ranking). NEW GangStatsService (gang kills, deaths, KDR, assists, members' money from online then offline UserManager; current members only, caveat documented); NEW GangRankingSnapshot rebuilt every 60 s on the main thread, published immutable, shared by command, menu and placeholders; GangStatContribution consumers (turf NEW GT/placeholder/TurfGangStatContribution: turfs held; fights implements wins/losses/wlr in W3-FS); gang ranking [stat] with Ranking.Min_Kills for kdr and node gangland.gang.ranking.wealth for bank and members' money; gang profile [player] (chat card); GangPlaceholderContribution new tokens + gang_board_* + Placeholders.Default_Value; IMPL GanglandPlaceholder null-player branch calling resolveGlobal; NEW inventory/player_profile.yml, NEW inventory/gang_ranking.yml over item source gang_ranking in GangMenuItemSourceContribution, gang_stat.yml aggregates; GangFilterAdapter stat sort fields.",
 "modules": [
  "gangland-gang",
  "gangland-turf (one NEW contribution)",
  "gangland-impl (GanglandPlaceholder, inventory YAML)"
 ],
 "docket": [],
 "tests": "NEW GangStatsServiceTest (offline members counted, gangless excluded); NEW GangRankingSnapshotTest (each sort, ties by id, min-kills floor, wealth hidden without the node, snapshot immutable); NEW GangRankingCommandTest (paging, unknown stat lists the valid ones); extend GangPlaceholderContributionTest (every new token, default value for gangless, board tokens never contain user_ or bank_); NEW GanglandPlaceholderGlobalTest in impl (null player reaches resolveGlobal); NEW TurfGangStatContributionTest",
 "exit": "the same ranking appears in chat, the menu and a hologram placeholder"
}
```

**W3 (after W3-W):**

```json
{
 "id": "W3-GA",
 "name": "Staff gang tools and purge",
 "model": "sonnet",
 "role": "executor",
 "parallel": true,
 "worktree": "E:/Programming/java/wt/gt14-w3ga-staff",
 "scope": "GP-01(staff)/03/22(reset)/24/28/40. Extract NEW GangDisbandService from GangDeleteCommand (payout and GangDeleteEvent unchanged; player delete calls it); extract NEW GangLeadership from GangTransferCommand; GangOptionContribution gains option gang disband <gang> (+confirm), leader <gang> <player>, bank <gang> balance|give|take|reset (every write logged with actor and amount), safehouse <gang> list|tp|remove, stats reset <player> (User kills/deaths + member.assists); NEW GangPurgeTask (BeanLifecycle, daily, Purge.Enabled false by default, Purge.Gang_Inactive_Days 60, Purge.Member_Inactive_Days 90 for non-owner members, via OfflinePlayer.getLastPlayed; purged disband pays out like a player disband and fires GangMemberLeftEvent PURGED); commands.json entries; extend GangCommandsJsonParityTest to walk the option.gang contribution tree.",
 "modules": [
  "gangland-gang"
 ],
 "docket": [],
 "tests": "NEW GangDisbandServiceTest (characterization: same payouts and event as GangDeleteCommandTest, green before and after); NEW GangLeadershipTest; NEW GangOptionStaffCommandsTest (console sender allowed, each verb, bank log line); NEW GangPurgeTaskTest (fake clock and last-played map: inactive gang disbanded, owner never purged as a member, disabled by default); GangCommandsJsonParityTest covers option.gang",
 "exit": "every staff verb runs from the console; a purged gang's turfs are released through the existing TurfGangDeleteListener"
}
```

```json
{
 "id": "W3-FK",
 "name": "Fights kickoff and arenas",
 "model": "sonnet",
 "role": "executor",
 "parallel": false,
 "worktree": "E:/Programming/java/wt/gt14-w3fk-fights",
 "scope": "GP-30 and the fights skeleton (parity/fights.md sections 2-3). NEW module gangland-features/gangland-gang-fights (Id fights, Depends [gang], no Plugins, Host_Api per D19), FightsModule/FightsModuleConfig/FightsModuleFiles/FightsModuleFileConfig, fight/fight_rules.yml, fight/fight_messages.yml, empty fight/arenas.yml, FightContribution (path gang: propose, accept, decline, enlist, drop, info, arenas, stats stubs) and ArenaCommand; arena model (ArenaBox 3D, Arena, ArenaRegistry, ArenaFile atomic write, ArenaDraft) and every /glw arena verb (create, corner, spawn, exit, size, finish, show, list, tp, on, off, delete, reload; fights.md's save is renamed finish so no arena verb echoes the competitor's flow); api GangFightStartedEvent/GangFightEndedEvent classes; orchestrator lines: gangland-features/pom.xml module, gangland-build/pom.xml jar copy, ModuleInstalls.OFFICIAL gains fights and the missing gang and lootchest ids (CM-38) + ModuleInstallsTest; plugin.yml permission entries for the player fight verbs.",
 "modules": [
  "gangland-gang-fights (NEW)",
  "gangland-api (two event classes)",
  "gangland-impl (ModuleInstalls, plugin.yml, orchestrator lines)"
 ],
 "docket": [
  "CM-38"
 ],
 "tests": "NEW ArenaBoxTest (inclusive edges, negative coords, Y); NEW ArenaFileTest (round trip, malformed hand edit reported, atomic write); NEW ArenaSaveValidationTest (every refusal sentence); NEW FightsModuleConfigTest (YAML copied from the module jar); NEW FightCommandsJsonParityTest; extend ModuleInstallsTest (gang, lootchest, fights resolve)",
 "exit": "the module loads without Bartizan and Citizens; an admin builds and saves an arena without F3 coordinates"
}
```

```json
{
 "id": "W3-FS",
 "name": "Fight state machine, bets and records",
 "model": "sonnet",
 "role": "executor",
 "parallel": false,
 "worktree": "E:/Programming/java/wt/gt14-w3fs-fightstate",
 "scope": "GP-29/31 (bets, teams, results) and fight W/L for GP-20/21 (parity/fights.md sections 4, 6, 7, 8). Fight, FightState, FightManager, FightChallenge, FightSide, Participant, PlayerSnapshot, FightRules (pure), FightClock; the player verb bodies; escrow at accept with the second-withdraw rollback, FightSettlement (House_Cut_Percent 0, Maximum_Balance overflow back to the loser), abortAll on disable and reload; allied gangs refused; ranked vs sparring (Ranked_Min_Bet, Ranked_Min_Member_Age_Hours 24, equal teams, Rematch_Cooldown_Minutes 30, Ranked_Per_Pair_Per_Day 3, Max_Bet_Percent_Of_Bank 50); fight_stats, fight_player_stats, fight_record tables; FightPlaceholderContribution; implements GangStatContribution (wins, losses, wlr); sets GangCombatState fighting flags; fires the api fight events. Branches from W3-FK's merged head.",
 "modules": [
  "gangland-gang-fights"
 ],
 "docket": [],
 "tests": "NEW FightRulesTest (allied, same gang, size, bet caps, rank gate, wanted, downed, member age, ranked decision); NEW FightStateMachineTest with a fake FightClock (every transition, every cancel refunds); NEW FightSettlementTest with real EconomyHandler instances (win, draw, cap overflow, second withdraw throws -> first refunded); NEW FightStatsRepositoryTest (@TempDir cleanup NEVER); NEW FightStatContributionTest",
 "exit": "no path creates or loses money; a draw refunds both banks"
}
```

```json
{
 "id": "W3-FG",
 "name": "Fight guards",
 "model": "opus",
 "role": "executor",
 "parallel": false,
 "worktree": "E:/Programming/java/wt/gt14-w3fg-fightguard",
 "scope": "The adversarial half (parity/fights.md sections 5 and 7): FightDamageListener (NORMAL lethal intercept on EntityDamageEvent before EntityDamageListener HIGH and CustomPlayerDeathListener HIGHEST; countdown/result immunity), FightContainmentListener (move, teleport with an expect-teleport set, portal, 5-tick check, Max_Leave_Warnings 3, Protect_Blocks), FightCommandListener (HIGHEST, lower-case, strip namespace, resolve aliases, allowlist, bypass node gangland.gang.fight.bypass_commands), FightSessionListener (quit restores, join ejects to Exit, PlayerDeathEvent keep-inventory fallback, respawn at Exit), FightWantedGuard (cancel raises for fighters), FightGangDeleteListener (forfeit pays the survivor the escrow). Starts after W3-FS merges.",
 "modules": [
  "gangland-gang-fights"
 ],
 "docket": [],
 "tests": "NEW FightDamageListenerTest (lethal cancelled and eliminated, non-lethal passes, countdown immune, non-fighter untouched); NEW CommandFilterTest (/MINECRAFT:TP, aliases, allowlist, bypass); NEW FightWantedGuardTest; NEW FightSessionListenerTest (quit restores, join eject); NEW FightGangDeleteListenerTest; manual two-account check on the Spigot test server (lethal hit by hand and by a Bartizan weapon: wanted 0, no drop, no downed state)",
 "exit": "a fight death raises no wanted level, drops nothing and never enters the downed state"
}
```

**Merge orders (amended):**
- W1: W1-M -> W1-X -> W1-H -> W1-T -> W1-G -> W1-R -> **W1-GH** -> W1-E2. W1-GH is the one W1 lane touching gang and turf, so
  it starts only after W0-A, W0-B **and** W0-C are merged (PLAN 7.5 gates turf lanes on A/B and gang lanes on C).
- W2: W2-R -> W2-N -> W2-P1 -> **W2-P3** -> W2-P2 -> **W2-F** -> **W2-S** -> W2-Q -> W2-E2. W2 opus unchanged (W2-R, then the two
  gate reviewers in sequence); the gate exploit checklist gains: join/ally mid-contest, safehouse escape, FF-on guard farming,
  stat farming between allies, tagged leave.
- W3: W3-A -> W3-B -> W3-Z -> W3-V -> W3-W -> **W3-GA** -> **W3-FK** -> **W3-FS** -> **W3-FG** -> W3-E2. Opus peak 2. The W3
  gate adds about 10 minutes of owner time (two accounts) for the W3-FG fight check, next to the 30-minute guard-wave run;
  the gate reviewer's diff set adds W3-FG and W3-GA.

New lanes: **8** (W1-GH, W2-P3, W2-F, W2-S, W3-GA, W3-FK, W3-FS, W3-FG). New waves: **0**.

---

## 6. New owner decisions (D16-D20; D1-D4 frozen, D5-D15 recommendations stand, D13 amended as in section 3)

| Id | Question | Options | Recommended | Blocks |
|---|---|---|---|---|
| **D16** | Gang level model (GP-10/11/12, D2 frozen) | **A** XP-only: turf XP levels the gang automatically (today's W2-P1), limits keyed on level, no purchase (GP-10 not delivered). **B** Buy-only: the bank buys levels (Gangs+ model); breaks frozen D2 (money buys the turf cap), acceptable only on servers without turf via config. **C** Hybrid: held-turf XP unlocks each level (D2 intact), `gang upgrade` pays a bank fee to take it; one number drives members, safehouses, allies, chat prefix and turf cap; `Levels.<n>.Cost: 0` everywhere + `Auto_Level_Up: true` gives A, `Require_Xp: false` gives B for turf-less servers. **D** Two tracks: XP level for the turf cap, a separately bought tier for limits (two numbers to explain). | **C** with the table below. It is the only option that delivers GP-10 while keeping D2's "XP only from actively held turf", gives the D9 faucet a large sink, and needs no core change (`Level.addExperience(xp, false, null)` + `addLevels`). Caps grow-only on existing gangs (C6). | W1-K (table), W2-P1, W2-P3, W1-GH |
| **D17** | Where do gang fights live? | **A** NEW runtime module `gangland-gang-fights` (`Depends: [gang]`), lanes W3-FK/FS/FG. **B** inside `gangland-gang` (a sixth lane on the hub module's files; no opt-out jar). **C** turf duels on the capture engine (a declared raid; PLAN s12 cut them). | **A** (fights.md s2 and s9). fights.md DF2-DF8 stand as lane defaults: allied gangs never fight; ranked = bet floor + equal teams + 24 h members + pair caps; equal teams on; timeout = most alive; eliminated fighters go home; money stakes only; arenas placed outside turfs (fighters skipped by turf via `GangCombatState`). | W3-FK, W3-FS, W3-FG |
| **D18** | Short command labels (GP-04, GP-13) | **A** only `/glw gang ...`; docs show the Bukkit `commands.yml` alias recipe (zero code, no argument completion). **B** `/g`, `/gc`, `/ac` shortcuts declared in `plugin.yml`, forwarded with completion into `/glw gang`, `/glw gang chat`, `/glw gang chat allies` (the same labels Gangs+ uses: familiar to migrating players, but its wording). **C** Gangland labels `/gw`, `/gwc`, `/gwa` with the same forwarding. | **C** (own wording, same convenience); B is the owner's call if muscle memory from Gangs+ servers matters more. Either way a label another plugin already holds stays reachable as `/gangland_warfare:<label>` | W1-K |
| **D19** | When does the parity api land (GP-16, 22, 34, 37, 41)? PLAN s2 promised none in W0-W3 | **A** additive only (event classes, `default` methods, one core event) added by the W2-K/W3-FK kickoffs on the integration branch; `GanglandApi.VERSION` bumped once by W4-R, which reads master's value (2.1 or 2.2 after H13) and goes one above; modules using the new members set `Host_Api` to it. **B** bump to 2.1 at W2-K and again at W4 (two numbers on one unreleased branch, and a clash if H13 also takes 2.1). **C** defer every api item to W4 (GP-16/37 events and board placeholders wait; in-module fallbacks until then). | **A**: nothing released between W2 and W4, so one bump is the honest version; `default` methods keep 2.0-built jars loading | W2-K, W3-FK, W4-R |
| **D20** | WorldGuard region flag for friendly fire (GP-18) | **A** soft integration: `softdepend: WorldGuard`, flag `glw-friendly-fire` registered in `Gangland.onLoad` behind a plugin-present check, read by name in the gang module (folded into W3-W, the soft-integration lane). **B** no flag: per-world overrides only. | **A**: small (one core `onLoad` hook, no api seam), and region-scoped PvP rules are what arena and spawn builders already use | W3-W |

Per-level table for **D16 C** (`gang_rules.yml` `Levels`; a missing row inherits the previous row's limits; `-1` = unlimited).
XP per level = PLAN P4.4 curve kept (`Gang_Level_Max` 20, `Gang_Level_Base` 100, `base * level ^ 1.5`), consumed on each
level-up, so "days" is cumulative at 72 XP/day per actively held turf.

| Level | XP for this level | Days with 1 / 3 turfs | Fee (bank) | Max members | Max safehouses | Max allies | Chat prefix | Turf cap (D2, unchanged) |
|---|---|---|---|---|---|---|---|---|
| 0 | - | - | - | 8 | 1 | 1 | none | 2 |
| 1 | 100 | 1.4 / 0.5 | 20,000 | 10 | 1 | 1 | `&8[&71&8]` | 3 |
| 2 | 283 | 5.3 / 1.8 | 40,000 | 12 | 2 | 2 | `&8[&72&8]` | 4 |
| 3 | 520 | 12.5 / 4.2 | 75,000 | 14 | 2 | 2 | `&8[&a3&8]` | 5 |
| 4 | 800 | 23.7 / 7.9 | 120,000 | 16 | 3 | 2 | `&8[&a4&8]` | 6 |
| 5 | 1,118 | 39 / 13 | 180,000 | 18 | 3 | 3 | `&8[&b5&8]` | 6 |
| 6 | 1,470 | 60 / 20 | 250,000 | 20 | 4 | 3 | `&8[&b6&8]` | 6 |
| 8 | 2,263 | - / 39 | 420,000 | 24 | 5 | 3 | `&8[&e8&8]` | 6 |
| 10 | 3,162 | - / 66 | 650,000 | 28 | 5 | 3 | `&8[&610&8]` | 6 |
| 20 | 8,944 | - | 2,150,000 | 40 | 6 | 3 | `&8[&c20&8]` | 6 |

Fees are sized against the D9-B faucet (36,000 per actively held turf per day) and the create cost (100,000): a three-turf
gang pays level 5 in about two days of income. Levels 7, 9 and 11-19 are written out in the shipped YAML (+150,000 per level
after 10; members +1 per level after 10). Rewards (`Levels.<n>.Commands`) ship empty.

---

## 7. Beyond parity: why Gangland beats every gang plugin

Concrete, all planned in W0-W4 or this addendum:

1. **Territory that means something.** Admin-drawn turfs you can take only while the owners are online, after a wake-up
   shield, with sides frozen at the start: no gang plugin has fair, readable territory fights (P3, C1).
2. **The city on one screen.** `/glw map` chat grid with hover and click, HUD line, six turf placeholders, dynmap layer, a
   text form for Bedrock (P2, P5.4).
3. **Guards that fight like a crew.** Bought reserves that deploy in waves, hold posts, count on the bar, wear the gang tag
   (P3.4, P5); Gangs+ has no NPCs at all.
4. **Told anywhere, caught up on login.** Attack alerts with head-count and direction, outcome lines, the while-you-were-away
   digest (P4.1, P4.3).
5. **Progress you earn, then pay for.** Held-turf XP unlocks levels, the bank completes them (D16 C): money alone cannot buy
   territory, unlike a buy-only ladder.
6. **Fights done right (GP-41, which Gangs+ never shipped).** 3D arenas with Y bounds, several spawns per side and an Exit,
   hard escrow with rollback, ranked vs sparring with anti-farm rules, rematch cooldowns, a namespace-proof command filter,
   and no wanted level, drops or downed state from a fight death.
7. **Cops and heat that know about gangs.** Fights never raise wanted; defenders can be exempted inside their attacked turf
   (D10, W4-H).
8. **Profiles and purge (GP-39, GP-40, both announced and never shipped by Gangs+).** A player profile menu, a gang stats
   page, one ranking snapshot shared by chat, menu and hologram placeholders, a safe default-off purge.
9. **One friendly-fire rule** across vanilla hits, Bartizan guns and potions, with per-gang, negotiated-ally, per-world and
   region control, and guards that stay safe from their own side.
10. **Safehouses that cannot break a fight.** Warm-up, cost, cooldown, safe-spot check, no invulnerability, and no escape
    from or reinforcement into a turf under attack.
11. **Modular.** Mail, turf, fights are separate jars with `/glw module install`; a server takes only what it wants.

---

## 8. Tests, commands.json, migration, docket

**Tests** are named per lane in 5.2/5.3 (JUnit 5 + Mockito, red first against pre-fix code; the two extractions
`GangDisbandService`/`GangLeadership` ship characterization tests green before and after; DB tests use
`@TempDir(cleanup = CleanupMode.NEVER)` + release in `@AfterEach`). Red-first flips: `GangMembersDamageListener` self-shot
(GR-39, cancelled today), `GangWeaponImpactListener` with `Gang.Enable: false` (GR-40, protection silently dropped today),
`ModuleInstallsTest` for `gang`/`lootchest` (CM-38, unknown today).

**commands.json:** every new verb gets an entry in the jar that owns it: gang `commands.json` (safehouse, rally, profile,
ranking, upgrade, friendlyfire, ally friendlyfire, notice, chat allies, chat spy, `option gang ...` staff verbs), the fights
module's own `commands.json` (`gang fight ...`, `arena ...`). `GangCommandsJsonParityTest` (NEW in W0-C) must build
`GangCommand` **with** its contributions; W3-GA extends it to the `option.gang` tree; fights gets
`FightCommandsJsonParityTest`. Kickoffs add the stub entries; the E2 lanes do the 3-way merge.

**Migration notes** (for `documentation/migration-0.14.0.md`, handed to the E2 lanes):
- Rank nodes now inherit toward the owner rank; `Minimum_Rank` in `gang_rules.yml` grants verbs by rank; existing trees keep
  working (entries naming a missing rank are ignored with a warning). New nodes listed.
- Member, ally and safehouse caps by gang level; existing over-cap gangs keep everyone but cannot recruit or ally until under.
- Gang levels now need held-turf XP **and** a bank fee (`gang upgrade`); `Levels.*.Cost: 0` + `Auto_Level_Up: true` restores
  pure XP levelling; `Require_Xp: false` for servers without the turf module.
- Friendly fire: one rule; Bartizan-gun protection moved from the civilians jar to the gang jar (update both); gang members
  can now bow-boost; `Gang.Enable: false` now disables gangs everywhere (it previously only dropped gun protection).
- Same-gang and allied kills no longer count toward kills/deaths.
- New tables/columns: `gang_safehouse`, `gang.friendly_fire`, `gang.notice`, `gang_ally.friendly_fire`, `member.assists`,
  fights' `fight_stats`, `fight_player_stats`, `fight_record`; arena data in `fight/arenas.yml` per server.
- New module `gangland-gang-fights` (optional jar, `Depends: [gang]`); `/glw module install gang|lootchest|fights` now works.
- Shortcut labels per D18; WorldGuard soft support per D20.
- `gangland-api`: additive only, one `VERSION` bump at release (D19).

**Docket rows** (hand-ins for the orchestrator: `triage/*.txt` 8-field rows + `build_docket.py`; this lane writes nothing
outside this folder; last ids verified in `bugs.json`: GR-38, CM-37):

| Id | Tier | Finding | Evidence | Fixed in |
|---|---|---|---|---|
| GR-39 | P3 | A gang member's own arrow or projectile never damages them (no bow-boost, no self-knockback) while gangless players can: the self-hit is treated as same-gang friendly fire | `GG/listener/gang/GangMembersDamageListener.java` L31-48: shooter and victim resolve to the same user, same gang id -> cancelled | W2-F |
| GR-40 | P3 | `Gang.Enable: false` no longer disables gangs; its only reader is civilians' Bartizan-impact listener, so setting it drops ally protection against guns while melee protection stays | `Settings.isGangEnabled()` read only at `GC/listener/gang/GangAllyWeaponImpactListener.java` L42 (grep); census gang.md s2.4 | W2-F (`GangWorldPolicy`) |
| CM-38 | P2 | `/glw module install gang` and `install lootchest` fail: `ModuleInstalls.OFFICIAL` lists six ids and omits two shipped modules that mail and turf depend on | `IMPL/command/sub/module/ModuleInstalls.java` L30-35 | W3-FK |

Not filed (unconfirmed, fights.md s11 row 2): `EntityDamageListener.handleWanted` may move the bounty after a cancelled
wanted raise; confirm against `Bounty.getAutoBountyIncrease` before filing.

`docket.assigned` delta: W2 +2 (GR-39, GR-40), W3 +1 (CM-38); total 73 -> 76; new rows 12 -> 15.

**Out-of-scope list edits for PLAN s12:** remove "member and ally caps" and "gang home/HQ beyond `WaypointType.GANG`"; add
"third-party combat-tag plugin adapters", "turf as a fight stake (DF7, later)", "spectating eliminated fighters (DF6 B,
later)", "durable per-gang kill counters (gang KDR is derived from current members)".
