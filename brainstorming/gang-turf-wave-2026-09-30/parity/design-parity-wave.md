# Gangs+ parity addendum to the 0.14.0 roadmap (design)

Planning only, written 2026-09-30 for wave `gang-turf-wave-2026-09-30`, master `ff9d813f` (0.12.0). No product code was
touched. Inputs: `competitors/gangsplus.md` (GP-01..GP-45), `competitors/notes.md`, `PLAN.md` (final),
`roadmap.json`, `census/gang.md`, `census/seams.md`, `census/ux-surface.md`, `parity/matrix.md`, `parity/reuse.md`,
`parity/fights.md`. The graph was checked fresh (`graphify-out/graph.json` 2026-09-29 23:08 is newer than HEAD 23:05;
the working tree only changes `brainstorming/`). Orientation: `graphify explain GangMembership | GangPermissions |
TurfFriendlyFireListener | RankAssignmentPolicy | CommandManager | GangOptionContribution`, `graphify query
GangAllyWeaponImpactListener | CarGangContract`. After that, raw reads of the files cited below. No browser read was
needed: the competitor inventory is already local and every fact here comes from our own code.

Copyright: Gangs+ is a commercial plugin. This document takes feature ideas only. Every verb, node, YAML key and message
below belongs to Gangland (`/glw gang ...`, `gangland.gang.*`, `Capitalized_Underscore` keys, strings in module YAML).

Path shorthand (as in PLAN.md): `GG/` = `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/`,
`GM/` = `gangland-features/gangland-mail/src/main/java/org/luckyraven/gangland/mail/`,
`GT/` = `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/`,
`GC/` = `gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians/`,
`IMPL/` = `gangland-impl/src/main/java/org/luckyraven/gangland/`, `API/` = `gangland-api/src/main/java/org/luckyraven/gangland/`.
**NEW** = does not exist yet.

## 0. Shape in one screen

- W0-W4 stay exactly as planned. Lane ids and D1-D15 do not change. Three one-line scope amendments to existing lanes
  are listed in section 5.4.
- **W5 "Parity: gang core"** starts after the W2 gate and runs **beside W3**. Its files are disjoint from W3: W3 owns
  civilians squads, turf `npc/defender/**` and the map files; W5 owns gang, mail, a few new turf files, one civilians
  listener, and impl resources. W5 delivers 38 of the 45 rows.
- **W6 "Parity: fights, WorldGuard, the parity gate"**: the fights lanes may start as soon as W5-K merges. W6-W waits
  for W5-F, and W6-X waits for the W5 gate. W6 runs beside W4 (disjoint: W4 owns `NpcRole`, cops, civilians roles,
  turf defenders, core wanted). **The W6 gate is the "Gangs+ parity reached" gate.**
- One additive `gangland-api` minor bump for the whole release branch. W5-K takes it, and W4-R adds `NpcRole` under
  the same value (amendment A2).
- New owner decisions **D16-D24**. D5-D15 keep their recommendations; nothing here argues against them.

---

## 1. Parity table (GP-01..GP-45)

Status now: from `parity/matrix.md` (HAVE 3, PARTIAL 16, PLANNED 5, MISSING 21). Status after plan: **HAVE** means it
works on master today and no lane is needed; **PLANNED** means a named lane delivers the whole row (existing lane,
feature or NEW lane); **EXCLUDED** means dropped, with the reason given. There is no full exclusion. **GP-33 is the only
partial exclusion.**

| Id | Feature (short) | Now | After | Delivered by | Lane(s) |
|---|---|---|---|---|---|
| GP-01 | Create, rename, disband with confirm; staff disband | PARTIAL | PLANNED | Create/rename/delete-with-confirm exist (`GangCreateCommand`, `GangRenameCommand`, `GangDeleteCommand`); NEW `GangDisbandService` extracted from `GangDeleteCommand`; staff verb `/glw option gang disband <gang>` (console-safe) | W5-M, W5-A |
| GP-02 | Invite, uninvite, join by name, kick, leave | HAVE | HAVE | Mail `GangInviteCommand`/`GangInviteCancelCommand`/`GangInviteAcceptCommand`, gang `GangKickCommand`/`GangLeaveCommand`; decline + expiry in W0-D; all routed through NEW `GangMembershipService`; turf re-stamps presence on a membership change (conflict C10) | W0-D, W5-M, W5-T |
| GP-03 | Transfer ownership; staff set-owner | PARTIAL | PLANNED | `GangTransferCommand` exists; swap logic extracted to NEW `GangLeadership`; staff verb `/glw option gang setowner <gang> <player>` | W5-M, W5-A |
| GP-04 | Gang list (bank column), info by gang or player, help | PLANNED | PLANNED | P2.5 (`GangListCommand`, `GangInfoCommand`, CM-37 help); gap filled: `gang info <player>` resolves the player's gang, bank column only with `gangland.gang.bank.view_others`. Help hides what server nodes forbid; hiding what a *rank* forbids is beyond parity (hook: Keystone `CommandVisibilityFilter`) | W1-G, W1-K, W5-U |
| GP-05 | Console commands on join, leave, kick | MISSING | PLANNED | `gang_rules.yml` `Commands.On_Join|On_Leave|On_Kick`, run by `GangMembershipService` with the ASCII `name` token only (conflict C15) | W5-M |
| GP-06 | Any-language names, colour-code option, hex | PARTIAL | PLANNED | D21: `name` stays the lookup key under `Name_Pattern`; the display name takes Unicode, `&` codes and hex behind `Display_Name.*` keys; map glyph fallback (A1) | W5-U, W1-M (A1) |
| GP-07 | Five configurable ranks, one ladder, promote/demote | PLANNED | PLANNED | Global rank tree (D13 A) + `Ranks.Preset` list read by the seed on an empty table; default 3 rungs, five is a one-line edit (section 3) | W1-R, W5-R |
| GP-08 | Per-feature minimum rank | PARTIAL | PLANNED | `Ranks.Minimum` map + one new clause in `GangPermissions.allows` (section 3) | W5-R |
| GP-09 | Node per command, wildcard nodes | PARTIAL | PLANNED | CM-37 `permissions:` block + NEW parent `gangland.command.player` over the player verb nodes; **never** a default-true `gangland.gang.*` parent (conflict C16) | W1-K, W5-K |
| GP-10 | Level up by paying; amount remaining shown | MISSING | PLANNED | D16 C: XP from held turf qualifies a level, `gang levelup` pays `Claim_Cost` from the bank; the reply shows the amount remaining | W5-L |
| GP-11 | Per-level max members, max homes, chat prefix | MISSING | PLANNED | D16/D17 level table (`Levels.Rows.Level_<n>`); enforced at join (W5-M), ally request/accept (W5-L), home set (W5-H), chat prefix (W5-C) | W5-L, W5-M, W5-H, W5-C |
| GP-12 | Level-up rewards incl. offline members; level-up event | PLANNED | PLANNED | P4.4 fires `GangLevelUpEvent`; NEW `Level_Rewards.Level_<n>.Commands` per member (online and offline, by roster name); api `GangLevelReachedEvent` | W2-P1, W5-L |
| GP-13 | Gang chat (toggle + one-shot); ally chat | PLANNED | PLANNED | P4.5 gang chat; NEW `gang ally chat [message]` channel in the same listener | W2-P2, W5-C |
| GP-14 | Staff social spy; log gang/ally chat | MISSING | PLANNED | NEW `ChatSpyService` (in memory) + `/glw option gang spy`; `Chat.Log_To_Console` | W5-C, W5-A |
| GP-15 | Chat-format token, default for gangless | PARTIAL | PLANNED | PAPI tokens exist; NEW `Chat.Public_Tag.Enabled|Format|No_Gang` in the chat listener (off by default); `Placeholders.Default_Value` replaces the hardcoded `NA` for `gang_*` tokens | W5-C, W5-S |
| GP-16 | Chat events for other plugins | MISSING | PLANNED | api `GangChatEvent` (cancellable, channel GANG/ALLY) fired on the main thread | W5-K, W5-C |
| GP-17 | Friendly fire per gang and global | PARTIAL | PLANNED | Section 4: `Gang.friendly_fire` + `gang friendlyfire on|off` + `Friendly_Fire.Global`; one `FriendlyFireRule`, one api fact | W5-F |
| GP-18 | Per-world friendly fire; WorldGuard region flag | MISSING | PLANNED | `Friendly_Fire.Worlds.<world>` overrides (W5-F); soft WorldGuard flag `gangland-friendly-fire` (W6-W) | W5-F, W6-W |
| GP-19 | Block only negative potions; self bow boost | MISSING | PLANNED | `Friendly_Fire.Potions: NEGATIVE_ONLY|ALL` (XSeries `XPotion` set), `Friendly_Fire.Allow_Self_Damage` | W5-F |
| GP-20 | Kills, deaths, assists, KDR, fights W/L, WLR, profile | PARTIAL | PLANNED | NEW member counters (kills/deaths/assists) fed by the damage ledger; fight W/L from the fights module through a gang-module `GangStatContribution`; `gang player [name]` | W5-S, W6-E |
| GP-21 | Top 10, sortable | MISSING | PLANNED | NEW `GangTopCommand` over one cached snapshot: kdr, wlr, level, members, online, bank (gated), members' money, **turfs held** (turf contribution) | W5-S, W5-T, W6-E |
| GP-22 | Staff stat reset; no counting in gang-disabled worlds | MISSING | PLANNED | `/glw option gang resetstats <player>`; the ledger listener skips `Worlds.Disabled` | W5-S, W5-A |
| GP-23 | Bank; hide others' balance unless permitted | PARTIAL | PLANNED | Bank exists; `gangland.gang.bank.view_others` checked by list/info/top | W5-U, W5-S |
| GP-24 | Staff bank balance/give/take/reset | MISSING | PLANNED | `/glw option gang bank <gang> balance|give|take|reset [amount]`, every write logged with the actor (`@CustomLog`) | W5-A |
| GP-25 | Alliance request, back to neutral, alliance limit | PARTIAL | PLANNED | Mail flow exists (W0-C/W0-D harden it); `Max_Allies` from the level table at request and accept; `gang ally abandon <gang name>` | W5-L, W5-U |
| GP-26 | Named homes: set/delete/list/tp, safe check, level cap | PARTIAL | PLANNED | NEW `gang_home` table + `GangHomeService` using transient api `Waypoint`/`WaypointTeleport` (warm-up, move-cancel, cooldown, **no shield**); NEW safe-spot check; cap from the level table | W5-H |
| GP-27 | Regroup at a home | MISSING | PLANNED | `gang regroup <home>` consent request to online members, same teleport path, turf veto | W5-H, W5-T |
| GP-28 | Staff home tools | MISSING | PLANNED | `/glw option gang homes <gang> list|tp|delete [home]` | W5-A |
| GP-29 | Challenge, team size, bet, accept/decline, join/leave | MISSING | PLANNED | NEW module `gangland-gang-fights` (D19 A), verbs `gang fight challenge|accept|decline|join|leave|info|arenas|stats` | W6-K, W6-S, W6-E |
| GP-30 | Several arenas, held inside, staff arena tools | MISSING | PLANNED | 3D `ArenaBox`, N spawns per side, exit point, `/glw arena ...`, containment listener | W6-A, W6-F |
| GP-31 | Command blocking, equal teams, winner takes bet, stats | MISSING | PLANNED | `FightCommandListener`, `Require_Equal_Teams`, escrow + `FightSettlement`, WLR | W6-F, W6-S, W6-E |
| GP-32 | Disable gang commands in chosen worlds | MISSING | PLANNED | `Worlds.Disabled` + NEW `GangWorldPolicy`; root gate in `GangCommand` (covers mail and fights sub-trees); also gates friendly fire and stats | W5-K |
| GP-33 | Combat tag: no gang escapes mid-combat | MISSING | PLANNED (partial exclusion) | Built-in `CombatTag` on the damage ledger blocks leave, home, regroup and summon. **Excluded: adapters for third-party combat-tag plugins.** They share no common API, each would be one more soft dependency, and the built-in tag gives the outcome the row asks for | W5-F |
| GP-34 | ~25 placeholders + leaderboard tokens + default | PARTIAL | PLANNED | Extend `GangPlaceholderContribution`; `gang_top_<stat>_<n>_<name|value>` through api `PlaceholderContribution.resolveGlobal`; `Placeholders.Default_Value` | W5-K, W5-S |
| GP-35 | MySQL/SQLite, periodic save, schema updater | HAVE | HAVE | Keystone persistence, `PeriodicalUpdates`, diff engine | - |
| GP-36 | Titles, every message customisable, any language | PARTIAL | PLANNED | NEW `gang/gang_messages.yml` (`GangMessages extends LocalizedModuleYaml`); `LocalizedModuleYaml` tries `<base>_<code>.yml` for any language code (body change); NEW `GangNotifier` titles for join/leave/kick/promote/level | W5-K, W5-U |
| GP-37 | Public API: queries and events | PARTIAL | PLANNED | One api bump: `GangMembershipView` default query methods + NEW api events (section 5.3) | W5-K (+ fired by W5-M, W5-L, W5-C, W6-E) |
| GP-38 | Staff reload | HAVE | HAVE | `ReloadCommand`; module YAML reload per the P1.2 lifecycle rule | - |
| GP-39 | Player and gang profile GUI (Gangs+ never shipped it) | PARTIAL | PLANNED | NEW `player_profile.yml`, filled `gang_stat.yml`, NEW `gang_top.yml` over the `gang_top` item source | W5-S |
| GP-40 | Auto-purge inactive players and gangs (never shipped) | MISSING | PLANNED | NEW `GangPurgeTask` (off by default, D24) + `/glw option gang purge preview|run`, disband through `GangDisbandService`, so turf releases via `GangDeleteEvent` | W5-M, W5-A |
| GP-41 | More fight features, events, API (never shipped) | MISSING | PLANNED | Fights beyond the reference: N spawns, Y bounds, exit point, hot reload, ranked vs sparring, escrow, per-pair caps, a fight record table, api `GangFightEndedEvent` | W6-A, W6-S, W6-E, W6-F |
| GP-42 | Summon every member (other plugin) | MISSING | PLANNED | `gang summon` consent request to the owner's position, same teleport path and veto | W5-H, W5-T |
| GP-43 | Top by balance and by members; members by rank | PARTIAL | PLANNED | Top sorts (GP-21); `GangMembersCommand` grouped by rank with `[page]` | W5-S, W5-U |
| GP-44 | Negotiated ally friendly fire (other plugin) | MISSING | PLANNED | `gang_ally.friendly_fire` per direction; `gang ally friendlyfire <gang> on|off`; takes effect only when both rows are on (conflict C5) | W5-F |
| GP-45 | Open vs invite-only; gang MOTD (other plugin) | PLANNED | PLANNED | P4.5 `gang join` + `gang recruiting` (D7); NEW `gang motd [text|clear]` shown to members on login | W2-P2, W5-U |

Tally after the plan: HAVE 3 (GP-02, GP-35, GP-38), PLANNED 42, EXCLUDED 0. One row (GP-33) carries a partial
exclusion.

---

## 2. Conflicts with fair fights, turf and D1-D4, and how each is settled

| # | Collision | Resolution | Decision |
|---|---|---|---|
| C1 | **Homes as raid escapes** (GP-26): a defender or attacker teleports out of a turf under attack | NEW `GT/listener/TurfTeleportGuardListener` on the api `TeleportEvent` (fired by `WaypointTeleport` before `player.teleport`) cancels a gang teleport whose **source** is inside a turf under attack. The combat tag (GP-33) also vetoes it. Scope, stated honestly: the veto covers every `WaypointType.GANG` teleport through `WaypointTeleport` (homes, regroup, summon, and admin waypoints attached to a gang). Another plugin's raw `player.teleport` is out of scope; the veto exists to close the hole this feature would open | D18 |
| C2 | **Regroup/summon during a contest** (GP-27/42): instant reinforcement. D1 counts defenders at once, and P3.2 resets presence on teleport but defenders count immediately | The same listener cancels a gang teleport whose **destination** is inside a turf under attack, or inside a turf owned by a gang that is neither yours nor allied. Only online members get a request, so the D1 owners-online window is unchanged | D18 |
| C3 | `Waypoint.shield` sets `player.setInvulnerable(true)` after arrival, which would make defenders invulnerable on the bar | Gang homes are transient `Waypoint` objects built with `shield = 0`, and a test pins it. They are never added to `WaypointManager`, so global waypoint names cannot collide | D18 |
| C4 | **Friendly-fire toggle vs guards** (`TurfFriendlyFireListener`, cops, `EventPriority.HIGH`): owners killing their own guards for the D14 refund and the P6.3 bounty | That listener protects NPCs (Quartermaster, defenders) from owner and ally *players*. It is not player-vs-player friendly fire and stays **unconditional**, untouched by any toggle | D20 |
| C5 | **Ally friendly fire** (GP-44) vs P3.2 (allies count as defenders) and ally guard immunity. `matrix.md` recommends that an FF-on ally stops counting | **Divergence from matrix.md, with the reason:** negotiated ally FF changes player-vs-player damage only. The alliance still counts on the owner's side of the bar and stays guard-immune. Exploit check: (a) allied or same-gang kills **never** count in gang stats, FF on or off (W5-S farm guard), so sparring allies cannot farm KDR; (b) there is no bounty or wanted exemption, so a kill between sparring allies is punished like any PvP kill; (c) mutual consent (both rows on) means no one can grief an ally into taking damage. Changing capture semantics would add a third relation ("ally for chat, rival for capture") to `CaptureService` (a W2-R opus file) for no gain | D20 |
| C6 | Friendly fire vs civilians' `GangAllyWeaponImpactListener` (Bartizan impacts) and cars | The civilians listener switches from `alliedOrSame` + the dead `Settings.isGangEnabled()` to the new api fact `GangMembership.pvpBlocked(...)`. Guns, fire ticks, clouds and fists then follow one rule. Cars: gadget has no car-hits-player damage path (verified: `CarDamageListener` and `CarDamageMath` only damage vehicles), and car-mounted weapons go through Bartizan impacts, which the civilians listener covers. Damage to a gang mate's *car* is property damage, not friendly fire, and stays unchanged | D20 |
| C7 | **Bought levels vs frozen D2** (turf cap by level; XP only from held turf) | D16 C: money never makes XP. A level needs its XP threshold (held turf only), and money can only be an *extra* gate on claiming it. The default claim cost is 0, so shipped behaviour equals D2 and W2-P1 exactly | D16 |
| C8 | **Member and ally caps** dropped by the synthesis (PLAN sections 1 and 12) | Reinstated as rows of the level table. P3.2 supports them: owners plus allies all count as defenders, so an unbounded alliance becomes a defender blob. A value of 0 means unlimited, so a server can opt out | D17 |
| C9 | **Unicode names** (GP-06) vs W1-G `GangNameRules` (ASCII) and the map glyph rule (PLAN 5.1/5.4) | `name` stays the lookup, console-token and map key under `Name_Pattern` (default W1-G's ASCII); Unicode lives on the display name. The glyph comes from the first `A-Z` letter of the colour-stripped display name, then `name`, then a free letter. The display name already accepts any text today (`gang display`), so this is W1-M's own latent issue (A1) | D21 |
| C10 | **Join/leave/kick mid-contest** (GP-02, W2-P2 `gang join`): P3.2 re-stamps presence only on an ally relation change, so a fresh joiner is an instant defender | NEW `GT/listener/TurfMembershipRestampListener` on api `GangMemberJoinedEvent`/`GangMemberLeftEvent` calls a new `TurfLocationTracker.restamp(UUID)`, so the entry counts as new (attackers wait 15 s). The combat tag also blocks `gang leave` | - |
| C11 | **Fights vs wanted, downed state, drops, turf presence** | fights.md 5.1: a NORMAL-priority lethal intercept runs before `EntityDamageListener` (HIGH) and `CustomPlayerDeathListener` (HIGHEST). Arenas are placed outside turfs (DF8 A); allied gangs cannot fight | D19 |
| C12 | **Purge vs D15 payout and turf** | Purge is a disband through `GangDisbandService`: equal split per D15, no 25% fee refund (`Purge.Refund_Fee: false`), `GangDeleteEvent` releases turfs through W0-A `TurfGangDeleteListener`. `Purge.Inactive_Days` (30) must exceed turf `Inactivity_Release_Days` (4). **Off by default** | D24 |
| C13 | Staff `bank give` is an unlogged faucet against the D9 economy | Every staff bank write is logged with actor, gang and amount (`@CustomLog` info). There is no silent path | - |
| C14 | A bank leaderboard exposes rich gangs as raid targets | The bank sort, bank column and `gang_top_bank_*` token need `gangland.gang.bank.view_others` (default op) | - |
| C15 | Console commands on join/leave/level (GP-05/12) with gang names spliced in | Only `%gang%` (the validated `name`, whitespace stripped), `%gang_id%` and `%player%` are substituted, never the display name. The same reason stops `Name_Pattern` from allowing whitespace (the loader rejects such a pattern) | D21 |
| C16 | **Wildcard nodes** (GP-09) vs `GangPermissions.allows`: its first clause is `player.hasPermission(node)`, so a default-true `gangland.gang.*` parent would bypass every rank gate for every player | The wildcard for "all player commands" is a NEW parent `gangland.command.player` over the Keystone command nodes (`gangland.command.gang`, `...map`, `...turf` and so on). `PluginYmlPermissionsTest` asserts that no `gangland.gang.*` node or parent defaults to true | - |
| C17 | Level-based chat prefix vs other chat plugins | The prefix renders only inside Gangland's gang and ally channels, and in public chat only when `Chat.Public_Tag.Enabled` is true (default false). PAPI `%gangland_gang_level-prefix%` serves chat-format plugins | - |
| C18 | Turf in a gang-disabled world (GP-32) keeps paying and granting XP | `turf create` refuses a gang-disabled world, and an existing turf there logs one warning at load. `CaptureEligibility` is not touched (W2-R's file stays closed) | - |
| C19 | Short roots `/g`, `/gc`, `/ac` collide with other plugins | Bukkit gives a label to the first plugin that registers it, and `/gangland_warfare:g` always works; admins can remap in `commands.yml` | D22 |
| C20 | A second api bump vs W4-R's bump (PLAN section 2: "no new api surface in W0-W3", one bump in W4) | One minor bump per release branch (D23, A2). W5 is not W0-W3, so the rule's letter holds, and its intent (one bump per release) is kept | D23 |

---

## 3. Rank model: is the global tree enough for GP-07/08? Yes

Tested against the code: `RankManager` holds one global tree (Head `member` at the root, Tail `owner` the deepest
leaf, `census/gang.md` s1). `GangPermissions.allows` (`GG/gang/permission/GangPermissions.java` L67-79) answers yes on
a server permission, the rank's own node list or Vault (`Member.hasPermission`), or the top rank. `RankAssignmentPolicy`
already walks the tree with Keystone `Tree.isDescendant` (L74, L79, L107).

- **GP-07 (five ranks, configurable names, one ladder in every gang):** the global tree is "one ladder in every gang"
  by construction, names are configurable (`Gang.Rank.Head/Tail`, `/glw rank create`), and promote/demote exist. The
  only gap is the *count* of the seeded ladder. W5-R makes the W1-R seed read NEW `Ranks.Preset` (a list, default
  `[member, officer, owner]`, D13 A). The YAML comment shows a five-rung example (`[recruit, member, officer, lieutenant,
  owner]`). Seeding still happens **only on an empty rank table** (D13 A unchanged).
- **GP-08 (per-feature minimum rank):** node lists alone are awkward because `Rank.contains` has no inheritance, so
  "officer and above" would mean copying a node onto every rung. Fix: NEW `Ranks.Minimum` map in `gang_rules.yml`
  (feature key -> rank name), read by one extra clause in `allows`. The clause passes when the member's rank *is* the
  named rank or sits below it toward the Tail. It uses the `Tree.isDescendant` helper `RankAssignmentPolicy` already
  uses, and the lane pins the argument order with a test. Node lists, Vault and the top-rank clause stay, as
  exceptions. An unknown rank name logs one warning, and the clause is then off for that feature.
- Defaults (block style, `Capitalized_Underscore` keys): `Deposit: member`, `Withdraw: officer` (TF-45 spirit),
  `Invite: officer`, `Kick: officer`, `Promote: officer`, `Demote: officer`, `Ally: owner`, `Rename: owner`,
  `Description: officer`, `Display: owner`, `Color: owner`, `Home: member`, `Home_Set: officer`, `Regroup: officer`,
  `Summon: owner`, `Friendly_Fire: owner`, `Level_Up: owner`, `Motd: officer`, `Chat: member`, `Ally_Chat: member`,
  `Fight: officer`, `Upgrade: officer` (the W2-Q `gangland.turf.upgrade` gate goes through the same `allows`, so it
  gains the map for free).
- Kick, promote and demote keep their rank-position checks *and* gain the gate (`gangland.gang.kick|promote|demote`).
  Position alone lets any higher rank kick; the gate lets the server say which rung may.
- A denied action names the lowest rank that can (the P2.6 player promise), for example "Officers and above can invite".
- **Per-gang ranks are not needed.** The competitor's ladder is one global config too. D13 stays **A**; C (per-gang
  trees, a schema change) stays out of scope.

---

## 4. One friendly-fire rule, used everywhere

**NEW `GG/gang/combat/FriendlyFireRule`** (pure; unit-tested as a table). `blocked(attacker, victim, location)`, first
match wins:

1. Either player is gangless, or the world is in `Worlds.Disabled` (GP-32): **not blocked** (gangs do not exist there).
2. `attacker == victim`: blocked only if `Friendly_Fire.Allow_Self_Damage` is false (default **true**, which allows
   bow boosting; GP-19).
3. A region flag at `location` (W6-W; `FriendlyFireRegionSource`, a no-op until WorldGuard is present):
   `gangland-friendly-fire` ALLOW means not blocked, DENY means blocked.
4. World override `Friendly_Fire.Worlds.<world>: ALLOW|DENY`.
5. Same gang: blocked unless `Friendly_Fire.Global` is `GANG_CHOICE` (default) and `Gang.friendly_fire` is on (the
   `gang friendlyfire on|off` node gate), or `Global` is `ON`. `Global: OFF` always blocks.
6. Allied (`Gang.isAlly`, symmetric after W0-C): blocked unless **both** `gang_ally.friendly_fire` rows are on (GP-44)
   and `Global` is not `OFF`.
7. Rival gangs: never blocked by this rule.

Potions (GP-19): `PotionSplashEvent`/`AreaEffectCloudApplyEvent` strip only the effects an XSeries `XPotion`
negative set contains when `Friendly_Fire.Potions: NEGATIVE_ONLY` (default), or all effects when `ALL`, for every
victim the rule blocks.

**One fact, three readers.** The api holder gains `GangMembership.pvpBlocked(UUID attacker, UUID victim, Location at)`,
backed by a **default** method on `GangMembershipView`. The default returns today's `alliedOrSame` semantics, so an
older gang module behaves exactly as now. The holder returns false when no view is installed. Readers:

| Reader | Module | Change |
|---|---|---|
| `GG/listener/gang/GangMembersDamageListener` (LOWEST, melee + projectile) | gang | rewritten onto `FriendlyFireRule`; the GR-15 null guard comes from W0-C |
| NEW `GG/gang/combat/PotionFriendlyFireListener` | gang | GP-19 |
| `GC/listener/gang/GangAllyWeaponImpactListener` (Bartizan impacts) | civilians | `alliedOrSame` + dead `Settings.isGangEnabled()` -> `membership.pvpBlocked(...)`; still gang-module-free |
| cops `TurfFriendlyFireListener` | cops-n-crooks | **no change**: player-vs-NPC guard protection, unconditional (C4) |
| gadget car access (`GanglandCarGangs`) | gadget | **no change**: access, not damage (GD-06 pins "shares a gang") |

The damage ledger (NEW `GG/gang/combat/DamageLedger`) listens at **`MONITOR`, `ignoreCancelled = true`**, so a hit the
rule blocked never tags anyone and never earns an assist. The combat tag (`Combat.Tag_Seconds` 15) and the assists
(`Stats.Assist_Window_Seconds` 10) read the same ledger.

---

## 5. New lanes

### 5.1 Waves and why

- **W5 - Parity: gang core.** Depends on the **W2 gate**: it builds on W1-K gang YAML plumbing, W1-G list/info/name
  rules, W1-R preset + node registration, W2-P1 `addExperience` + level curve, W2-P2 chat listener + `gang join`,
  W0-C `GangCommandsJsonParityTest`, and W0-D `MailModuleFileConfig`. It runs **beside W3**: W3 owns
  `GC/npc/CivilianService`, `GC/npc/FactionSquads`, `GC/npc/state/behavior/CivilianCombatBehavior`,
  `civilian_messages*.yml`, `GT/npc/defender/**`, `GarrisonDeployListener`, `TurfPowerupNpc`, `turf_npcs.yml`, the
  map files, `TurfVisualization` and `gangland-turf/pom.xml`. None of those appear below. Same branch
  `gang-turf-territory`; lane worktrees `E:/Programming/java/wt/gt14-w5<x>-<slug>`.
- **W6 - Parity: fights, WorldGuard, parity gate.** Fights lanes depend on **W5-K merged** (seams `GangStatContribution`,
  the api `GangFightEndedEvent`, the `Fight` key in `Ranks.Minimum`). W6-W depends on **W5-F merged**
  (`FriendlyFireRegionSource`). W6-X depends on the **W5 gate**. W6 runs **beside W4**: W4 owns `API/npc/NpcRole`,
  cops `CopRole`, civilians type config + `CivilianDamageListener`, `GT/npc/defender/**`, core wanted and impl
  `EntityDamageListener`. W6 touches none of them.
- Why two waves: fights is ~30 classes in a new module with its own opus lane and its own gate walkthrough, and
  WorldGuard adds a core pom dependency. Keeping both out of W5 lets the core-parity gate close on gang files only, and
  lets fights ride beside W4 instead of waiting for it.

### 5.2 File ownership and the shared-append rows (additions to PLAN 7.1)

Every file below has one owning lane per wave. Rows added to PLAN 7.1:

| File | Owner / rule |
|---|---|
| `gangland-impl/src/main/resources/plugin.yml` | orchestrator: W5-K adds the parity player nodes, the `gangland.command.player` parent and the `g`/`gc`/`ac` commands (D22); W6-K adds the fight player nodes; W6-W adds `WorldGuard` to `softdepend`; W3-W's `dynmap` line stays beside them |
| `API/GanglandApi.java` `VERSION` line **and every `module.yml` `Host_Api` line** | one minor bump per release branch (D23): W5-K bumps; W4-R adds `NpcRole` under the same value (A2); at the W4 master merge the orchestrator sets `VERSION` = master's value + 1 minor if H13 already consumed a minor, and rewrites the `Host_Api` lines that read new members (gang, civilians, turf, fights, cops for W4) to that value |
| `GG/command/sub/gang/GangCommand.java`, `GG/command/sub/gang/ally/GangAllyCommand` | W5-K registers every new child as a stub (`top`, `player`, `home`, `regroup`, `summon`, `friendlyfire`, `levelup`, `motd`; `ally chat`, `ally friendlyfire`) and adds the `GangWorldPolicy` root gate; lanes fill only their own command classes |
| `GG/gang/command/option/GangOptionContribution.java` | W5-K registers staff stubs; W5-A fills them |
| `GG/gang/GangConfig.java` | bean-append only (like `TurfModuleConfig`): lanes add `@Bean` methods, never edit another lane's |
| `GG/gang/GangMembershipInstaller` (view) | W5-K implements the thin query defaults; W5-F fills `pvpBlocked` |
| `GG/gang/{Gang,member/Member,GangAlliance}` + `GangTable`, `MemberTable`, `GangAllianceTable` + their repositories | **W5-K only**: `gang.motd` (text, null), `gang.friendly_fire` (bool, false), `gang_ally.friendly_fire` (bool, false), `member.kills|deaths|assists` (int, 0), each with a round-trip test |
| `GangModuleFiles`/`GangModuleFileConfig` | W5-K registers `gang/gang_messages.yml` (kickoff-only rule unchanged) |
| `gangland-features/gangland-gang/src/main/resources/gang/gang_rules.yml` | W5-K writes every parity key; lanes read only |
| `IMPL/config/GameplayConfig.java` (YAML menu list) | W5-K registers `player_profile.yml`, `gang_top.yml` |
| `IMPL/config/WiringConfig.java` | W5-U only (short-root executors) |
| `GT/TurfModuleConfig.java` | existing rule; W5-T appends beside W3-B |
| `gangland-features/pom.xml`, `gangland-build/pom.xml`, `IMPL/command/sub/module/ModuleInstalls.java` | W6-K (fights module line, jar copy, `fights` id). **Expected conflict** at the W4 master merge: H13 adds `gangland-healthbars` to `gangland-features/pom.xml` (PLAN section 2); keep both lines |
| root `pom.xml` | orchestrator: W6-W adds the enginehub repository for the WorldGuard api (provided) |
| `documentation/migration-0.14.0.md` | W5-E2 / W6-E2 after merges |

Interleaving rule: parity lanes merge into `gang-turf-territory` between W3 and W4 merges, each only after a green
reactor build on the current head. The W3-G and W4-G reviews exclude parity diffs; W5-G and W6-G own them.

### 5.3 The one api batch (W5-K, additive, one bump)

- `GangMembershipView` default methods: `pvpBlocked(UUID, UUID, Location)` (default = `alliedOrSame` semantics),
  `gangIds()`, `gangIdByName(String)`, `membersOf(int)`, `ownerOf(int)`, `chatChannelOf(UUID)` (NEW enum
  `GangChatChannel {PUBLIC, GANG, ALLY}`). `GangMembership` gains matching holder methods with absent defaults.
- NEW events in `API/events/gangs/` (plural, so there is no split package with the module's `events.gang`; they carry
  ids and names, never the module's `Gang` type): `GangCreatedEvent`, `GangMemberJoinedEvent`,
  `GangMemberLeftEvent(Reason: LEFT, KICKED, DISBANDED, PURGED)`, `GangLevelReachedEvent`, `GangChatEvent`
  (cancellable, channel), `GangFightEndedEvent`. Module code keeps `GangDeleteEvent` and `GangLevelUpEvent` as they are.
- `PlaceholderContribution` default `resolveGlobal(String)` returning null, plus a null-player branch in impl
  `GanglandPlaceholder` (L104-113/L137) for leaderboard tokens.
- `LocalizedModuleYaml`: body change only, tries `<base>_<code>.yml` for the picked language code before English.
- Host_Api: gang, civilians, turf and fights read new members, so they carry the new value; mail and the others stay.
  **Divergence from fights.md** (which says `Host_Api: 2.0`): fights fires `GangFightEndedEvent`, so it reads a new
  member and must carry the new value.

### 5.4 Amendments to existing lanes (scope lines only, ids unchanged)

- **A1 W1-M:** the glyph takes the first `A-Z` letter of the colour-stripped display name, else of `name`, else a free
  letter. `TerritoryRasterTest` gains a Cyrillic/CJK/hex display-name case. The display name accepts any text today,
  so this is W1-M's own edge case.
- **A2 W4-R:** "bump `GanglandApi.VERSION` by one minor" becomes "if `gang-turf-territory` already carries the W5-K
  bump, add `NpcRole` under that value; one bump per release branch" (D23).
- **A3 W4 exit:** "then `gang-turf-territory` -> master as 0.14.0" becomes "after the later of the W4 and W6 gates"
  (D23 A; D23 B keeps the original line).

### 5.5 W5 lanes

Kickoff **W5-K** (sonnet code + haiku YAML/JSON, on the integration branch): section 5.3 api batch + bump; the
section 5.2 kickoff rows (entity columns, stubs, YAML keys, menus, plugin.yml); NEW `GangWorldPolicy` (full: tiny) with
the root gate; NEW seam stubs with signatures and trivial bodies so lanes compile in parallel: `GangLimits`,
`DamageLedger`, `CombatTag`, `GangStatContribution` (gang module interface: `key()`, `value(int gangId)`, collected
through the container like `CommandContributions.from(container)`), `FriendlyFireRegionSource`,
`GangPermissions` constants for every new verb; NEW `GangMessages`; `commands.json` entries for every stub;
`GangCommandsJsonParityTest` extended to the `option gang` staff sub-tree.
The docket row GR-39 (`Gang.Enable: false` disables nothing) closes here: `Gang.Enable` becomes the global switch of
`GangWorldPolicy`.

| Lane | Model | Scope | Owns | Docket | Tests (JUnit 5 + Mockito, red first) | Exit |
|---|---|---|---|---|---|---|
| W5-M membership lifecycle | sonnet | NEW `GangMembershipService` (join/leave/kick/disband/set-owner choke point; member cap via `GangLimits`; api events; `Commands.On_*`); NEW `GangDisbandService` extracted from `GangDeleteCommand`; NEW `GangLeadership` from `GangTransferCommand`; the `GangPermissions.allows(..., KICK)` gate in `GangKickCommand` (the `Kick` minimum of section 3); combat-tag check on leave; NEW `GangPurgeTask` (`BeanLifecycle`, daily, off by default, `OfflinePlayer.getLastPlayed()`, D24) | NEW `GG/gang/membership/**`, NEW `GG/gang/purge/**`, `GG/command/sub/gang/{GangCreateCommand,GangJoinCommand,GangLeaveCommand,GangKickCommand,GangDeleteCommand,GangTransferCommand}`, `GM/command/**/{GangInviteCommand,GangInviteAcceptCommand}` | - | NEW `GangMembershipServiceTest` (every join/leave path fires exactly one event with the right reason; cap refuses the 9th member at level 0; console tokens use `name`, whitespace stripped); `GangDeleteCommandTest` characterization stays green after the extraction; NEW `GangPurgeTaskTest` (fake clock: preview lists, run disbands, owner-only gangs, off by default); `GangInviteAcceptCommand` cap test | every join and leave path goes through the service (grep: no `Gang.addMember` outside it) |
| W5-L levels and limits | sonnet | NEW `GangLevelTable` (pure step rows) + `GangLimits` body; D16 C claim gate inside `GangManager.addExperience` (auto-claim when `Claim_Cost` 0; otherwise core `Level.addExperience(xp, false, null)` banks the XP without levelling and the claim calls `Level.addLevels(1, event)`, both existing in `gangland-core/.../user/Level.java` L38/L71, so no core change); `gang levelup` with the amount remaining; `Level_Rewards` for online and offline members; api `GangLevelReachedEvent`; ally cap at request and accept | NEW `GG/gang/level/**`, `GG/gang/GangManager` (level methods), `GG/command/sub/gang/GangLevelUpCommand`, `GM/command/**/{GangAllyRequestCommand,GangAllyAcceptCommand}` | - | NEW `GangLevelTableTest` (step rows, missing rows, 0 = unlimited); NEW `GangLevelClaimTest` (cost 0 auto-levels exactly like W2-P1; cost > 0 holds at the threshold and reports the remaining amount; money never adds XP); reward runner test with an offline member; ally cap test at request and at accept | W2-P1's days-to-level test still green with default costs |
| W5-R ranks and gates | sonnet | `Ranks.Minimum` clause in `GangPermissions.allows`; `Ranks.Preset` read by the W1-R seed; "who can" denial message; new nodes registered | `GG/gang/permission/**`, `GG/gang/rank/RankSeed*` (W1-R's seed), `GG/command/sub/gang/{GangPromoteCommand,GangDemoteCommand}` (gate line) | - | `GangPermissionsTest` table (rank at, below and above the minimum on a 3- and a 5-rung tree; branching tree; unknown rank name warns and disables the clause); `RankSeedTest` (preset list, empty table only) | an officer invites and kicks; a member cannot withdraw on a fresh install |
| W5-C chat | sonnet | ally channel (`gang ally chat`, toggle + one-shot), `ChatSpyService`, `Chat.Log_To_Console`, public tag (off by default), level prefix in channels, api `GangChatEvent` on the main thread, `%player%`-safe formatting | `GG/listener/gang/GangChatListener`, `GG/command/sub/gang/GangChatCommand`, NEW `GG/command/sub/gang/ally/GangAllyChatCommand`, NEW `GG/gang/chat/**` | - | NEW `GangChatRoutingTest` (pure recipient sets: gang, ally, spy, gangless; cancelled event delivers nothing); snapshot-refresh test (join/leave/ally change); no manager read off the main thread (listener test with a fake scheduler) | chat never reads `HashMap` managers on the async thread |
| W5-F friendly fire and combat tag | sonnet (opus gate review) | section 4 in full: `FriendlyFireRule`, `GangMembersDamageListener` rewrite, `PotionFriendlyFireListener`, `DamageLedger` (MONITOR, `ignoreCancelled`), `CombatTag` + NEW `CombatTeleportGuard` on api `TeleportEvent` for every `WaypointType.GANG` teleport (gang homes, regroup, summon **and** admin waypoints attached to a gang: one rule, no marker needed), `gang friendlyfire`, `gang ally friendlyfire`, `pvpBlocked` in the installer, civilians listener switched | `GG/listener/gang/GangMembersDamageListener`, NEW `GG/gang/combat/**`, NEW `GG/command/sub/gang/{GangFriendlyFireCommand}`, NEW `GG/command/sub/gang/ally/GangAllyFriendlyFireCommand`, `GG/gang/GangMembershipInstaller` (fact body), `GC/listener/gang/GangAllyWeaponImpactListener` | GR-40 | NEW `FriendlyFireRuleTest` (the 7-step table, every row); flip: self-shot while in a gang is **cancelled** today (GR-40, red) -> allowed by default; potion strip test (negative only vs all); `GangAllyWeaponImpactListenerTest` (fact honoured, Bartizan-absent class skipped); ledger ignores a cancelled hit; tag blocks any `GANG`-type waypoint teleport and leaves `SPAWN`/`GLOBAL` waypoints alone | one decision point: grep finds `alliedOrSame` in no damage listener |
| W5-H homes, regroup, summon | sonnet | NEW `gang_home` table + repository (module `gang.database` package, `setDataSupplier` wired); `GangHomeService` with transient `Waypoint` (`shield` 0, YAML warm-up/cooldown/cost); NEW safe-spot check (2-high passable, solid floor, XSeries `XMaterial`; `Homes.Safe_Check`); `gang home [name]`, `home set|delete|list`; regroup and summon consent requests (clickable, 30 s expiry); cap from `GangLimits`; `GangDeleteEvent` cleanup | NEW `GG/gang/home/**`, NEW `GG/gang/database/**/home/**`, NEW `GG/command/sub/gang/{GangHomeCommand,GangRegroupCommand,GangSummonCommand}` | - | NEW `GangHomeRepositoryTest` (`@TempDir(cleanup = NEVER)` + release); NEW `SafeSpotTest` (lava, void, 1-high gap, water); NEW `GangHomeServiceTest` (shield is 0, cap, names unique per gang, two gangs both own `base`); request expiry test | two gangs both set `base`; a home teleport grants no invulnerability |
| W5-T turf touchpoints | sonnet | `TurfTeleportGuardListener` (C1/C2; applies to every `WaypointType.GANG` teleport, admin gang waypoints included); `TurfMembershipRestampListener` + `TurfLocationTracker.restamp` (C10); `turf create` refuses a gang-disabled world, load warning (C18); NEW `TurfsHeldStatContribution` (a `GangStatContribution`) | NEW `GT/listener/{TurfTeleportGuardListener,TurfMembershipRestampListener}`, `GT/task/TurfLocationTracker` (restamp method), `GT/command/TurfCreateCommand` (world check), NEW `GT/stat/**`, `TurfModuleConfig` (append) | - | NEW `TurfTeleportGuardListenerTest` (source under attack, destination under attack, destination in a rival turf, own quiet turf allowed, non-gang waypoint untouched); restamp test (a joiner counts as new; attackers wait `Presence_Seconds`); `TurfCreateCommandTest` world case | a regroup into a turf under attack is refused with one sentence |
| W5-S stats, top, profile, placeholders | sonnet + haiku YAML | NEW `GangStatsListener` (PlayerDeathEvent + PlayerDownedEvent at MONITOR, 500 ms de-dup like `PlayerDeathListener`, killer + assists from the ledger; no count for allied/same gang, repeat pair inside `Stats.Repeat_Kill_Cooldown_Seconds` 300, or a disabled world); NEW `GangStatsService` (main-thread snapshot every 60 s, immutable); `gang top [stat] [page]` (min kills for kdr); `gang player [name]` chat + GUI; `gang_top` item source; placeholders + leaderboard tokens + `Placeholders.Default_Value` | NEW `GG/gang/stats/**`, NEW `GG/command/sub/gang/{GangTopCommand,GangPlayerCommand}`, `GG/gang/placeholder/GangPlaceholderContribution`, `GG/gang/menu/GangMenuItemSourceContribution`, `gangland-impl/src/main/resources/inventory/{gang_stat,player_profile,gang_top}.yml` | - | NEW `GangStatsListenerTest` (farm guards, downed + death de-dup, assist window); NEW `GangStatsServiceTest` (sort order per stat, ties, bank sort hidden without the node, contributions from fights/turf); placeholder tests (`gang_top_kdr_1_name`, null player via `resolveGlobal`, gangless default, no `user_` substring in token names) | tokens, command and GUI show the same order (one snapshot) |
| W5-U UX | sonnet | NEW `IMPL/command/GangShortRootExecutor` (`/g` -> `glw gang`, `/gc` -> `glw gang chat`, `/ac` -> `glw gang ally chat`, tab completion delegated; D22); `gang info <gang|player>`; bank column gate in list/info; `GangMembersCommand` grouped by rank + `[page]`; `Name_Pattern`/`Display_Name.*` in `GangNameRules` (D21); `gang motd` + login line in `MemberJoinListener`; NEW `GangNotifier` titles; `gang ally abandon <gang name>` | NEW `IMPL/command/GangShortRootExecutor`, `IMPL/config/WiringConfig` (bean), `GG/command/sub/gang/{GangInfoCommand,GangListCommand,GangMembersCommand,GangNameRules}`, NEW `GangMotdCommand`, `GG/listener/gang/MemberJoinListener`, NEW `GG/gang/notify/**`, `GG/command/sub/gang/ally/GangAllyAbandonCommand` | - | `GangShortRootExecutorTest` (prefixing, empty args, tab delegation); `GangInfoCommandTest` (player-name resolution, bank hidden); name-rule table (Unicode display, whitespace pattern rejected); MOTD shown to members only | `/g` works for a non-op; a public card never shows another gang's bank |
| W5-A staff verbs | sonnet | `/glw option gang disband|setowner|bank|homes|resetstats|spy|purge` bodies over the W5-M/L/H/S/C services; every write logged; **console-safe** (the existing `option gang rank` casts `(Player) sender`; new verbs must not) | `GG/gang/command/option/GangOptionContribution`, NEW `GG/gang/command/option/**` | - | NEW `GangStaffVerbsTest` (each verb from a console sender; bank give/take/reset logged with actor; disband fires `GangDeleteEvent`); `GangCommandsJsonParityTest` green | merges last in W5 |
| W5-E1 docs (parallel) | haiku | `documentation/features/gangs.md` rewrite (levels, limits, homes, chat, friendly fire, staff verbs), `ranks.md` fixed to the real model (drops the invented inheritance and API) | docs | - | - | docs match `gang_rules.yml` |
| W5-E2 merges + migration (after) | haiku | `commands.json` 3-way merges; migration section (section 7.3) | docs, `commands.json` | - | - | help lists every new leaf |
| W5-G gate review | opus | adversarial: friendly-fire bypasses (projectile owner swap, potion clouds, Bartizan impacts), teleport escape paths, async chat reads, the cap race at concurrent accepts, api additivity | gang, mail, turf, civilians, api | - | - | no open exploit path without a red test |
| W5-J gate judge | fable | go/no-go after the owner walkthrough (two clients, about 30 min) | - | - | - | go |

Merge order: W5-K -> W5-R -> W5-M -> W5-L -> W5-F -> W5-H -> W5-T -> W5-C -> W5-S -> W5-U -> W5-A -> W5-E2.
Opus in W5: the gate reviewer only.

W5 gate: `mvn clean package`; W5-G; W5-J; console smoke G1/G2 re-run (boot without Citizens, reload twice) under the
test-server lock; the owner with two clients walks: fresh gang, `/g`, invite to cap, levelup refused while short
(remaining shown), friendly fire toggled both ways with fists, a bow and a Bartizan gun, ally friendly fire needing
both sides, home set/teleport, a regroup refused into a turf under attack, gang and ally chat with spy, `gang top`,
profile GUI; docket rows written; `graphify update . --force`.

### 5.6 W6 lanes

Kickoff **W6-K** (sonnet + haiku YAML): module skeleton `gangland-features/gangland-gang-fights` (module id `fights`,
`Depends: [gang]`, no `Plugins:`, `Host_Api` = the D23 value). Its pom has the same shape as `gangland-mail`'s
(`gangland-api` + `gangland-gang` at `provided`), which is the existing precedent for the "api + Depends:" rule. Also:
`FightsModuleFiles`/`FileConfig`, `fight/fight_rules.yml`, `fight/fight_messages.yml`, empty `fight/arenas.yml`,
`FightContribution` + `ArenaCommand` stubs, `commands.json`, `FightCommandsJsonParityTest`; the shared lines in
section 5.2; `ModuleInstalls.OFFICIAL` gains `fights`, `gang` and `lootchest` (docket CM-38) + `ModuleInstallsTest`.

| Lane | Model | Scope | Owns | Docket | Tests | Exit |
|---|---|---|---|---|---|---|
| W6-A arenas | sonnet | fights.md section 3: `Arena`, `ArenaBox` (3D), `ArenaRegistry`, `ArenaFile`, `ArenaDraft`, `/glw arena ...`, outline (XParticle) | fights `arena/**`, `command/arena/**` | - | `ArenaBoxTest` (inclusive edges, negatives, Y); `ArenaFileTest` (round trip, malformed hand edit); save validation table | an arena saved in game survives `arena reload` |
| W6-S state machine | sonnet | fights.md section 4: `Fight`, `FightState`, `FightManager`, `FightRules`, `FightClock`, `PlayerSnapshot`, player verbs | fights `fight/**` (minus settlement), `command/fight/**` | - | `FightRulesTest` (allied, same gang, size, rank gate via `GangPermissions.allows` + `Fight` minimum, wanted, downed, member age); `FightStateMachineTest` (fake clock, every cancel refunds) | - |
| W6-E economy and stats | sonnet | fights.md sections 6 and 8: escrow, `FightSettlement`, ranked rules, `fight_stats`/`fight_player_stats`/`fight_record`, `FightPlaceholderContribution`, NEW `FightWinsStatContribution` (a `GangStatContribution`: wins, losses, wlr), api `GangFightEndedEvent` | fights `database/**`, `placeholder/**`, `fight/FightSettlement`, NEW `stat/**` | - | `FightSettlementTest` (real `EconomyHandler`: win, draw, cap overflow, second withdraw throws -> first refunded); `FightStatsRepositoryTest`; WLR shows up in `gang top wlr` (fake contribution) | fights are zero-sum bank to bank |
| W6-F fight guards | **opus** | fights.md section 5 and section 7 listeners: lethal intercept (NORMAL), containment, command filter, session (quit, join eject, death fallback), wanted guard, gang-delete forfeit | fights `listener/**` | - | `FightDamageListenerTest`, `CommandFilterTest` (`/MINECRAFT:TP`, aliases, allowlist, bypass), `FightWantedGuardTest`, `FightSessionListenerTest`, `FightGangDeleteListenerTest`; **manual two-client Spigot check**: lethal hit by hand and by a Bartizan gun -> wanted 0, no drop, no downed state | - |
| W6-W WorldGuard flag | sonnet | `softdepend: WorldGuard`; WorldGuard api (provided, 7.0.x for the 1.16.5 floor) on impl and gang; NEW `IMPL/hook/worldguard/WorldGuardFlagRegistrar` created by a type-free factory from `Gangland.onLoad` only when the WorldGuard plugin is present; the StateFlag is named `gangland-friendly-fire`; NEW `GG/gang/combat/region/WorldGuardFriendlyFireSource` (WorldGuard-typed; Keystone `ReflectionGuard` skips it on a server without WorldGuard) filling `FriendlyFireRegionSource` | impl pom, root pom repository (orchestrator), `IMPL/Gangland.java` (one `onLoad` line), NEW `IMPL/hook/worldguard/**`, gang pom, NEW `GG/gang/combat/region/**` | - | `WorldGuardFlagRegistrarTest` (absent plugin = no call, no `NoClassDefFoundError`); region source ALLOW/DENY/unset through `FriendlyFireRuleTest` with a fake source; smoke G1 boot without WorldGuard | no `reflection.type.missing` spam without WorldGuard |
| W6-X parity audit | sonnet (+ haiku writing) | walk GP-01..GP-45 against the merged branch: each row names its test class or a manual step, and is run; writes `brainstorming/gang-turf-wave-2026-09-30/parity/audit.md`; flips `status_after_plan` to HAVE row by row | brainstorming only | - | - | 45 rows HAVE, GP-33 with its recorded partial exclusion |
| W6-E1 docs (parallel) | haiku | `documentation/features/fights.md`, arena how-to, WorldGuard flag note | docs | - | - | - |
| W6-E2 merges + migration (after) | haiku | `commands.json`; migration section | docs, `commands.json` | - | - | - |
| W6-G gate review | opus | fights event ordering, escrow, containment bypasses (pearl, chorus, elytra, riptide), WorldGuard load order | fights, impl, gang | - | - | - |
| W6-J gate judge | fable | the **"Gangs+ parity reached"** go/no-go | - | - | - | go |

Merge order: W6-K -> (W6-A, W6-S, W6-E in parallel) -> W6-F -> W6-W -> W6-X -> W6-E2.

**The "Gangs+ parity reached" gate (W6 gate):** `mvn clean package` green; W6-G clean; the W6-X audit shows all 45
GP rows HAVE (GP-33 with its recorded partial exclusion), each with a named test or a performed manual step; the owner
with two clients (about 40 min) fights a 1v1 with a bet (win pays, draw refunds, quit forfeits, `/tp` blocked), then
checks the WorldGuard flag in one region; smoke G1 without WorldGuard and without the fights jar; docket rows written;
`graphify update . --force`; `parity/PARITY.md` and `roadmap.json` updated (build_roadmap.py reads `parity`).

### 5.7 Scheduling

- **Opus peak <= 3.** Opus executors: W3-A, W4-R, W6-F. Reviewers: W3-G, W5-G, W4-G, W6-G. Rules: W6-F does not
  start while W4-R **and** a gate reviewer are both running; W3-G and W5-G run in sequence, never together.
- **Test-server lock** (one server, owner time): W3 gate (30 min) -> W5 gate (30 min) -> W4 gate (20 min) -> W6 gate
  (40 min). If H13 is late, W6 goes before W4. The W6-F manual check holds the lock for about 15 min.
- **At most 3 Maven builds** at once across W3, W4, W5 and W6 lanes (orchestrator). Lanes `verify`, never `install`.
- Worktrees `E:/Programming/java/wt/gt14-w5<x>-<slug>` and `gt14-w6<x>-<slug>`, branched from `gang-turf-territory`
  after the wave kickoff commit.

Timeline (PLAN 7.9 with W5/W6 added):

```
W0  K(orch) -> [B] -> [A]  ||  [C][D] [E1] [F]+owner -> [E2] -> gate
W1  K -> [M][X][H][T]  ||  [G][R]  [E1] -> [E2] -> gate
W2  K -> [R OPUS] [N][P1][P2][Q] [E1] -> [E2] -> gate
W3  [A OPUS] -> [B]  ||  [Z][V][W]  [E1] -> [E2] -> gate
W5  (after W2 gate, beside W3)  K -> [R][M][L][F][H][T][C][S][U] -> [A] [E1] -> [E2] -> gate (opus 1, fable, owner 30 min)
      ... H13 (0.13.0) merges to master; master merged into gang-turf-territory ...
W4  [R OPUS] -> [C][D][H] [E]                                    -> gate
W6  (fights after W5-K; W-W after W5-F; X after W5 gate; beside W4)
    K -> [A][S][E] -> [F OPUS] -> [W] -> [X] [E1] -> [E2] -> gate = "Gangs+ parity reached"
    merge to master as 0.14.0 after the later of the W4 and W6 gates (D23 A)
```

---

## 6. Owner decisions D16-D24

### D16 Gang level model (GP-10/11) - blocks W5-L, W5-M, W5-H, W5-C

| Option | What | Consequence |
|---|---|---|
| A XP-only | W2-P1 as planned; the level table only sets limits | GP-10 missing (no pay-to-level); D2 and W2-P1 unchanged |
| B Buy-only | money buys levels | **breaks frozen D2** (a rich gang buys the turf cap); listed only to be ruled out |
| **C Hybrid (recommended)**: XP qualifies, money claims | a level needs its XP threshold (held turf only, D2); `gang levelup` pays `Claim_Cost` from the bank; claim cost 0 auto-levels | **With default costs 0**, shipped behaviour, W2-P1's days-to-level table, its exit test and D2's cap pacing are identical; GP-10 is a config knob and the sample costs sit in comments. Setting costs above 0 money-gates a poor but active gang's turf cap (a stricter D2, never looser) and turns claims into a D9 sink. Known ceiling: a server without the turf module has no XP producer, so gangs stay at level 0 and the level-0 row governs (add a buy-without-XP switch only if such a server asks) |
| D Separate perk tier (`matrix.md`) | a second bought track for limits, XP level for the turf cap | two progression numbers on every screen |

Why one level number beats matrix.md's perk tier: players read one number ("Level 3") in the map header, info card,
placeholders and chat prefix. The bootstrap worry behind the perk tier (a new gang with no room to grow) is solved by a
generous level-0 row (8 members, 1 home, 1 ally). A second track would duplicate `Level`, its placeholders and its
event for a problem the table already removes.

Per-level table (`gang_rules.yml` `Levels.Rows.Level_<n>`; a row applies from its level until the next row; 0 =
unlimited). XP is cumulative on the W2-P1 curve (`Gang_Level_Base` 100, `base * level ^ 1.5`, 72 XP per active turf
per day). The turf cap column shows the frozen D2 formula `min(6, 2 + level)` (owned by `TurfRules.cap`, shown here
only):

| Level | Cumulative XP | Days at 1 active turf | Max members | Max homes | Max allies | Turf cap (D2) | Chat prefix | Claim cost (default 0; sample in comments) |
|---|---|---|---|---|---|---|---|---|
| 0 | 0 | - | 8 | 1 | 1 | 2 | none | - |
| 1 | 100 | 1.4 | 10 | 1 | 1 | 3 | `&8[&7I&8]` | 10,000 |
| 2 | 383 | 5.3 | 12 | 2 | 2 | 4 | `&8[&7II&8]` | 25,000 |
| 3 | 903 | 12.5 | 14 | 2 | 2 | 5 | `&8[&fIII&8]` | 50,000 |
| 4 | 1,703 | 23.7 | 16 | 3 | 3 | 6 | `&8[&fIV&8]` | 100,000 |
| 5 | 2,821 | 39.2 | 18 | 3 | 3 | 6 | `&8[&eV&8]` | 150,000 |
| 6-9 | - | - | +1 per level (22 at 9) | 4 from level 7 | 3 | 6 | `&8[&eVI..IX&8]` | +50,000 per level |
| 10-20 | - | - | 24 at 10, +1 per 2 levels (29 at 20) | 5 | 3 | 6 | `&8[&6X..XX&8]` | +75,000 per level |

### D17 Member and ally caps (reverses the PLAN section 12 drop) - blocks W5-M, W5-L

A **(recommended)** caps from the D16 table (0 = unlimited). B no caps (every row 0). C member cap only. Why: an
unbounded alliance is a defender blob under P3.2, and one gang can absorb the server.

### D18 Homes, regroup and summon vs turf - blocks W5-H, W5-T

A **(recommended)** own `gang_home` table, `shield` 0, warm-up and cooldown from YAML, a veto for any gang teleport
whose source or destination is in a turf under attack or whose destination is in a non-allied gang's turf, the combat
tag, and consent requests for regroup and summon. B allow teleports but stamp arrivals as attackers (a W2-R file
change; defenders still escape). C homes allowed only outside every turf (kills the "home in our turf" fantasy).

### D19 Where fights live - blocks W6-K

A **(recommended)** NEW runtime module `gangland-gang-fights` (`Depends: [gang]`), as in fights.md DF1: removable,
fault-isolated, loads without Bartizan or Citizens. B inside `gangland-gang` (a sixth hot lane on its files). fights.md
DF2-DF8 are adopted as lane defaults: allies never fight, the ranked rules, equal teams on, `MOST_ALIVE` on timeout,
eliminated fighters go home, money-only stakes, arenas outside turfs.

### D20 Friendly-fire model - blocks W5-F, W6-W

A **(recommended)** section 4 in full: one rule, one api fact; the gang toggle defaults off (today's behaviour); ally
FF negotiated and PvP-only (C5); guards unconditional; per-world overrides; negative-potions-only; self damage allowed;
the WorldGuard flag in W6-W. B = A without the WorldGuard flag (drops half of GP-18). C gang-module only, no api fact:
guns and fire keep blocking while fists follow the toggle (inconsistent).

### D21 Gang names - blocks W5-U, A1

A **(recommended)** `name` validated by `Name_Pattern` (default W1-G's `[A-Za-z0-9_]{3,16}`; admins may widen it to
`[\p{L}\p{N}_]{3,16}`; a pattern that allows whitespace is refused at load); the display name is free text
(`Display_Name.Allow_Hex` true, `Display_Name.Parse_Colours` true, `Display_Name.Max_Length` 32); console tokens use
`name`. B Unicode `name` by default (map, console and tab completion all get harder). C ASCII only, display name
restricted too.

### D22 Short command roots - blocks W5-K (plugin.yml), W5-U

A only `/glw gang ...`, plus a documented `commands.yml` recipe (no tab completion). **B (recommended)** plugin.yml
roots `/g` -> `/glw gang`, `/gc` -> `/glw gang chat`, `/ac` -> `/glw gang ally chat` through one delegating executor
with tab completion. These are the generic abbreviations gang plugins share, not the competitor's wording, and
`/gangland_warfare:g` works on a label collision. C = B with admin-chosen labels registered at runtime through a
Keystone CommandMap helper (a Keystone 1.15.0 lane; PLAN says no Keystone change in this wave).

### D23 Release shape and api bump - blocks W5-K, W4-R (A2), the final merge (A3)

A **(recommended)** one 0.14.0: `gang-turf-territory` merges to master after the later of the W4 and W6 gates; one
`GanglandApi` minor bump for the branch (W5-K bumps, W4-R adds under it); if H13 has not landed by the W6 gate, the
owner may ship 0.14.0 without W4 (W4 becomes 0.14.1). B keep W4's merge as planned and ship W5/W6 as 0.15.0 on a
follow-up branch (two api bumps, two migration docs).

### D24 Auto-purge default (GP-40) - blocks W5-M

A **(recommended)** off by default; staff run `/glw option gang purge preview` then `run`; when enabled,
`Purge.Inactive_Days` 30 for gangs (every member unseen) and 60 for non-owner members. B on by default with those
numbers (a deleted gang cannot be restored: data-loss risk on upgrade).

D5-D15: recommendations stand unchanged. D7 A and D13 A are extended, not changed (sections 3 and 1).

---

## 7. Beyond parity, tests, migration, docket

### 7.1 Beyond parity: what no gang plugin ships (all concrete, all in this plan)

1. **Territory you can see and fight over**: `/glw map` chat grid, HUD line, dynmap layer, admin-drawn turfs with a
   capture bar that follows one sentence everywhere (P2, P3). The competitor has no territory at all.
2. **Fair turf fights**: capture only while the owners are online, after a wake-up shield (D1), a strict-plurality
   start, presence timers, stall timeout. Gangs+ homes are raid escapes; ours are vetoed around a turf under attack
   (C1/C2).
3. **Guards that fight like a crew** (W3) and count on the bar (D4), with a Quartermaster you can trust (P3.6).
4. **Levels earned by holding ground** (D2 + D16): money can never buy the turf cap.
5. **Cops and heat**: fights never raise wanted (fights.md intercept); the optional defender heat exemption (W4-H).
6. **The two features Gangs+ announced and never shipped**: a player and gang profile GUI (GP-39, W5-S) and safe
   auto-purge with a preview (GP-40, W5-M, D24).
7. **Better fights than the reference** (GP-41): 3D arenas with N spawns per side and an exit point, hot reload,
   escrowed bets, ranked vs sparring, per-pair caps, a fight record table, api `GangFightEndedEvent`.
8. **A "turfs held" leaderboard** and turf placeholders no gang plugin can offer (W5-T contribution, W1-H tokens).
9. **Alerts anywhere and a digest** when your turf is hit while you are away (P4.1, P4.3).
10. **Install only what you run**: gang, mail, turf and fights are separate runtime modules (`/glw module install`).

### 7.2 Tests and commands.json

- Every lane: red first (failing `mvn -q test -pl <module> -am -Dtest=<Class>` output in the exit report);
  characterization tests for the `GangDeleteCommand` -> `GangDisbandService` and `GangTransferCommand` ->
  `GangLeadership` extractions (green before and after); DB tests use `@TempDir(cleanup = CleanupMode.NEVER)` +
  release in `@AfterEach`; no pom edits except W6-K and W6-W.
- The pinned test to flip in this addendum is GR-40 (self-shot cancelled), in W5-F.
- `commands.json`: every stub gets its entry in the jar that owns the command (gang module for `gang ...` and
  `option gang ...`; fights module for `gang fight ...` and `arena ...`). `GangCommandsJsonParityTest` (W0-C) is
  extended by W5-K to the `option gang` staff sub-tree. `FightCommandsJsonParityTest` is new in W6-K. The E2 lanes do
  the 3-way merges.
- `PluginYmlPermissionsTest` (W1-K) gains assertions that the player parity verbs default true, the staff and
  `bank.view_others` nodes default op, and **no** `gangland.gang.*` node or parent defaults true (C16).

### 7.3 Migration notes (W5-E2 / W6-E2 into `documentation/migration-0.14.0.md`)

- New columns (diff engine, defaults keep today's behaviour): `gang.motd` (null), `gang.friendly_fire` (false),
  `gang_ally.friendly_fire` (false), `member.kills|deaths|assists` (0; gang stats start counting from the upgrade;
  `User` kills and deaths are untouched). New tables: `gang_home`, `fight_stats`, `fight_player_stats`, `fight_record`.
- Behaviour changes: a player's own arrow now damages them (bow boosting; `Friendly_Fire.Allow_Self_Damage: false`
  restores the old block). Bartizan weapon impacts follow the gang friendly-fire toggle. `Gang.Enable: false` now
  really turns gangs off (GR-39). Level-0 caps apply to existing gangs: a gang already over `Max_Members` keeps its
  members but cannot accept new ones until it levels; the same holds for allies.
- Existing `WaypointType.GANG` waypoints stay as they are (admin waypoints); they are not converted to homes.
- New short roots `/g`, `/gc`, `/ac` (D22) may collide with another plugin; use `/gangland_warfare:g` or
  `commands.yml`.
- `GanglandApi.VERSION` rises one minor for the whole release; gang, civilians, turf, fights (and cops for W4) carry
  that `Host_Api`, and every jar updates together.
- WorldGuard is optional; the flag is `gangland-friendly-fire`.
- Auto-purge is off (D24). Chat public tags are off.

### 7.4 Docket rows found (new triage rows; orchestrator writes `triage/*.txt` + `build_docket.py`)

| Proposed id | Tier | Finding | Evidence | Fixed in |
|---|---|---|---|---|
| GR-39 | P3 | `Gang.Enable: false` no longer disables gangs; only civilians' `GangAllyWeaponImpactListener` reads `Settings.isGangEnabled()` (CM-03's fix removed the `GangCommand` gate) | `GC/listener/gang/GangAllyWeaponImpactListener.java` L41; census gang.md s2.4 | W5-K (`GangWorldPolicy` global switch), W5-F (civilians listener) |
| GR-40 | P3 | A gang member's own projectile cannot hurt them: `GangMembersDamageListener` treats damager == victim as same gang and cancels (no bow boosting, no self-inflicted splash from arrows) | `GG/listener/gang/GangMembersDamageListener.java` L27-49 (the `getGangId() ==` clause matches self) | W5-F (pinned red test flipped) |
| CM-38 | P3 | `ModuleInstalls.OFFICIAL` lists six ids ("The six official module ids") and omits the real `gang` and `lootchest` modules, so `/glw module install gang` needs a full coordinate | `IMPL/command/sub/module/ModuleInstalls.java` L24-31 vs `gangland-features/` (8 modules) | W6-K |

Rows already in the docket and only re-touched here: GR-15 (null guard, W0-C), TF-49 (defender heat, W4-H). fights.md's
two remaining observations (bounty moving after a cancelled wanted raise; totems vs the downed state) stay
unfiled until a lane confirms them against `Bounty.getAutoBountyIncrease`; nothing here depends on them.

---

## 8. Machine-readable blocks

### 8.1 Parity rows (`build_roadmap.py` shape)

```json
[
 {"id": "GP-01", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "GangDisbandService + /glw option gang disband", "lane": "W5-M, W5-A"},
 {"id": "GP-02", "status": "HAVE", "status_after_plan": "HAVE", "delivered_by": "mail invite/accept, gang kick/leave; GangMembershipService; turf re-stamp", "lane": "W0-D, W5-M, W5-T"},
 {"id": "GP-03", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "GangLeadership + /glw option gang setowner", "lane": "W5-M, W5-A"},
 {"id": "GP-04", "status": "PLANNED", "status_after_plan": "PLANNED", "delivered_by": "P2.5 list/info/help + info by player, gated bank column", "lane": "W1-G, W1-K, W5-U"},
 {"id": "GP-05", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "Commands.On_Join/On_Leave/On_Kick in GangMembershipService", "lane": "W5-M"},
 {"id": "GP-06", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "Name_Pattern + free display name (D21), map glyph fallback", "lane": "W5-U, W1-M"},
 {"id": "GP-07", "status": "PLANNED", "status_after_plan": "PLANNED", "delivered_by": "global rank tree + Ranks.Preset list (D13 A)", "lane": "W1-R, W5-R"},
 {"id": "GP-08", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "Ranks.Minimum clause in GangPermissions.allows", "lane": "W5-R"},
 {"id": "GP-09", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "CM-37 permissions block + gangland.command.player parent", "lane": "W1-K, W5-K"},
 {"id": "GP-10", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "gang levelup: XP qualifies, bank claims (D16 C)", "lane": "W5-L"},
 {"id": "GP-11", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "level table: members, homes, allies, prefix (D16/D17)", "lane": "W5-L, W5-M, W5-H, W5-C"},
 {"id": "GP-12", "status": "PLANNED", "status_after_plan": "PLANNED", "delivered_by": "GangLevelUpEvent + Level_Rewards incl. offline + api GangLevelReachedEvent", "lane": "W2-P1, W5-L"},
 {"id": "GP-13", "status": "PLANNED", "status_after_plan": "PLANNED", "delivered_by": "gang chat (P4.5) + gang ally chat", "lane": "W2-P2, W5-C"},
 {"id": "GP-14", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "ChatSpyService + option gang spy + Log_To_Console", "lane": "W5-C, W5-A"},
 {"id": "GP-15", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "Chat.Public_Tag + Placeholders.Default_Value", "lane": "W5-C, W5-S"},
 {"id": "GP-16", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "api GangChatEvent", "lane": "W5-K, W5-C"},
 {"id": "GP-17", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "FriendlyFireRule + gang friendlyfire + Friendly_Fire.Global", "lane": "W5-F"},
 {"id": "GP-18", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "Friendly_Fire.Worlds overrides + WorldGuard flag gangland-friendly-fire", "lane": "W5-F, W6-W"},
 {"id": "GP-19", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "Friendly_Fire.Potions NEGATIVE_ONLY + Allow_Self_Damage", "lane": "W5-F"},
 {"id": "GP-20", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "member kills/deaths/assists + fight W/L + gang player", "lane": "W5-S, W6-E"},
 {"id": "GP-21", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "gang top over one snapshot incl. turfs held and WLR", "lane": "W5-S, W5-T, W6-E"},
 {"id": "GP-22", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "option gang resetstats + disabled-world skip", "lane": "W5-S, W5-A"},
 {"id": "GP-23", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "gangland.gang.bank.view_others on list/info/top", "lane": "W5-U, W5-S"},
 {"id": "GP-24", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "option gang bank balance/give/take/reset, logged", "lane": "W5-A"},
 {"id": "GP-25", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "mail ally flow + Max_Allies + abandon by name", "lane": "W5-L, W5-U"},
 {"id": "GP-26", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "gang_home + GangHomeService (no shield, safe spot, cap)", "lane": "W5-H"},
 {"id": "GP-27", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "gang regroup consent + turf veto", "lane": "W5-H, W5-T"},
 {"id": "GP-28", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "option gang homes list/tp/delete", "lane": "W5-A"},
 {"id": "GP-29", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "gangland-gang-fights module: gang fight verbs", "lane": "W6-K, W6-S, W6-E"},
 {"id": "GP-30", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "3D arenas + /glw arena + containment", "lane": "W6-A, W6-F"},
 {"id": "GP-31", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "command filter, equal teams, escrow settlement, WLR", "lane": "W6-F, W6-S, W6-E"},
 {"id": "GP-32", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "Worlds.Disabled + GangWorldPolicy root gate", "lane": "W5-K"},
 {"id": "GP-33", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "built-in CombatTag; third-party combat-tag adapters excluded (no common API)", "lane": "W5-F"},
 {"id": "GP-34", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "GangPlaceholderContribution + gang_top tokens via resolveGlobal + default value", "lane": "W5-K, W5-S"},
 {"id": "GP-35", "status": "HAVE", "status_after_plan": "HAVE", "delivered_by": "Keystone persistence + PeriodicalUpdates", "lane": "-"},
 {"id": "GP-36", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "gang_messages.yml + any-language LocalizedModuleYaml + GangNotifier titles", "lane": "W5-K, W5-U"},
 {"id": "GP-37", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "api view queries + api events (one bump)", "lane": "W5-K"},
 {"id": "GP-38", "status": "HAVE", "status_after_plan": "HAVE", "delivered_by": "ReloadCommand + module YAML lifecycle", "lane": "-"},
 {"id": "GP-39", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "player_profile.yml, gang_stat.yml, gang_top.yml", "lane": "W5-S"},
 {"id": "GP-40", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "GangPurgeTask (off by default) + option gang purge", "lane": "W5-M, W5-A"},
 {"id": "GP-41", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "fights beyond the reference + api GangFightEndedEvent", "lane": "W6-A, W6-S, W6-E, W6-F"},
 {"id": "GP-42", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "gang summon consent + turf veto", "lane": "W5-H, W5-T"},
 {"id": "GP-43", "status": "PARTIAL", "status_after_plan": "PLANNED", "delivered_by": "top by bank/members + members grouped by rank", "lane": "W5-S, W5-U"},
 {"id": "GP-44", "status": "MISSING", "status_after_plan": "PLANNED", "delivered_by": "gang_ally.friendly_fire both sides, PvP only", "lane": "W5-F"},
 {"id": "GP-45", "status": "PLANNED", "status_after_plan": "PLANNED", "delivered_by": "gang join/recruiting (D7) + gang motd", "lane": "W2-P2, W5-U"}
]
```

### 8.2 Waves and lanes (`roadmap.json` shape)

```json
[
 {
  "id": "W5",
  "name": "Parity: gang core",
  "goal": "Every Gangs+ gang feature except fights and the WorldGuard flag works in gangland-gang: levels with limits, per-feature minimum ranks, one friendly-fire rule, homes that cannot be used to escape a raid, ally chat and spy, stats and leaderboards, staff verbs, public api events.",
  "depends_on": ["W2 gate"],
  "branch": "gang-turf-territory",
  "gate": "mvn clean package green; opus W5-G adversarial review (friendly-fire bypasses, teleport escapes, async chat, cap race, api additivity); fable go/no-go; smoke G1/G2 re-run under the test-server lock; owner walkthrough with two clients about 30 min (levelup short, friendly fire with fists/bow/Bartizan gun, ally FF both sides, home + refused regroup into a turf under attack, chat + spy, top, profile); docket rows; graphify update --force.",
  "lanes": [
   {"id": "W5-K", "name": "Kickoff: api batch, columns, stubs, YAML", "model": "sonnet", "role": "executor", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "One additive api bump (GangMembershipView defaults pvpBlocked/gangIds/gangIdByName/membersOf/ownerOf/chatChannelOf; events/gangs GangCreatedEvent, GangMemberJoinedEvent, GangMemberLeftEvent, GangLevelReachedEvent, GangChatEvent, GangFightEndedEvent; PlaceholderContribution.resolveGlobal + GanglandPlaceholder null-player branch; LocalizedModuleYaml any language). gang.motd, gang.friendly_fire, gang_ally.friendly_fire, member kills/deaths/assists columns with round-trip tests. GangCommand/GangAllyCommand/GangOptionContribution stubs + GangWorldPolicy root gate (Gang.Enable as global switch, GR-39). Seam stubs GangLimits, DamageLedger, CombatTag, GangStatContribution, FriendlyFireRegionSource; GangPermissions constants; gang_rules.yml parity keys; gang/gang_messages.yml + GangMessages; menus in GameplayConfig; plugin.yml nodes, gangland.command.player parent, g/gc/ac commands; commands.json; GangCommandsJsonParityTest covers option gang.", "modules": ["gangland-api", "gangland-gang", "gangland-impl (plugin.yml, GanglandPlaceholder, GameplayConfig)"], "docket": ["GR-39"], "tests": "api default-method tests (old view keeps alliedOrSame semantics); column round-trip tests; GangWorldPolicyTest; PluginYmlPermissionsTest (no default-true gangland.gang.*); GangCommandsJsonParityTest extended", "exit": "lanes fork without touching the same file"},
   {"id": "W5-M", "name": "Membership lifecycle and purge", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5m-membership", "scope": "NEW GangMembershipService (join/leave/kick/disband/set-owner, member cap, api events, Commands.On_*), GangDisbandService from GangDeleteCommand, GangLeadership from GangTransferCommand, the KICK gate in GangKickCommand, combat-tag check on leave, GangPurgeTask off by default (D24).", "modules": ["gangland-gang", "gangland-mail"], "docket": [], "tests": "GangMembershipServiceTest; GangDeleteCommandTest characterization; GangPurgeTaskTest; invite-accept cap test", "exit": "no Gang.addMember outside the service"},
   {"id": "W5-L", "name": "Levels and limits", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5l-levels", "scope": "GangLevelTable + GangLimits; D16 C claim gate in GangManager.addExperience (cost 0 auto-claims; otherwise Level.addExperience(xp, false, null) then Level.addLevels(1, event) at claim, no core change); gang levelup with the remaining amount; Level_Rewards for online and offline members; api GangLevelReachedEvent; ally cap at mail request and accept.", "modules": ["gangland-gang", "gangland-mail"], "docket": [], "tests": "GangLevelTableTest; GangLevelClaimTest; reward runner offline test; ally cap tests", "exit": "W2-P1 days-to-level test green with default costs"},
   {"id": "W5-R", "name": "Minimum ranks and preset", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5r-ranks", "scope": "Ranks.Minimum clause (Tree.isDescendant) in GangPermissions.allows; Ranks.Preset read by the W1-R seed on empty tables; lowest-rank denial message; gates on promote/demote.", "modules": ["gangland-gang"], "docket": [], "tests": "GangPermissionsTest min-rank table on 3- and 5-rung and branching trees; RankSeedTest preset", "exit": "an officer invites and kicks; a member cannot withdraw"},
   {"id": "W5-C", "name": "Chat: ally channel, spy, tag, event", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5c-chat", "scope": "gang ally chat (toggle + one-shot), ChatSpyService, Chat.Log_To_Console, public tag off by default, level prefix, api GangChatEvent on the main thread.", "modules": ["gangland-gang"], "docket": [], "tests": "GangChatRoutingTest; snapshot refresh test; no async manager reads", "exit": "chat never reads HashMap managers off the main thread"},
   {"id": "W5-F", "name": "Friendly fire and combat tag", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5f-friendlyfire", "scope": "FriendlyFireRule (7 steps), GangMembersDamageListener rewrite, PotionFriendlyFireListener, DamageLedger at MONITOR ignoreCancelled, CombatTag + CombatTeleportGuard, gang friendlyfire, gang ally friendlyfire, pvpBlocked in GangMembershipInstaller, civilians GangAllyWeaponImpactListener onto the api fact. TurfFriendlyFireListener untouched.", "modules": ["gangland-gang", "gangland-civilians"], "docket": ["GR-40"], "tests": "FriendlyFireRuleTest; flip GR-40 self-shot; potion strip test; GangAllyWeaponImpactListenerTest; ledger ignores cancelled hits; tag blocks GANG-type waypoint teleports only", "exit": "no damage listener calls alliedOrSame directly"},
   {"id": "W5-H", "name": "Homes, regroup, summon", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5h-homes", "scope": "gang_home table + repository, GangHomeService on transient Waypoint (shield 0), safe-spot check, home set/delete/list/tp, regroup and summon consent requests, cap from GangLimits, disband cleanup.", "modules": ["gangland-gang"], "docket": [], "tests": "GangHomeRepositoryTest; SafeSpotTest; GangHomeServiceTest (shield 0, per-gang names); request expiry test", "exit": "two gangs both own a home called base; no invulnerability on arrival"},
   {"id": "W5-T", "name": "Turf touchpoints", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5t-turf", "scope": "TurfTeleportGuardListener (source or destination under attack, destination in a non-allied turf); TurfMembershipRestampListener + TurfLocationTracker.restamp; turf create refuses gang-disabled worlds; TurfsHeldStatContribution.", "modules": ["gangland-turf"], "docket": [], "tests": "TurfTeleportGuardListenerTest; restamp test; TurfCreateCommandTest world case", "exit": "a regroup into a turf under attack is refused with one sentence"},
   {"id": "W5-S", "name": "Stats, top, profile, placeholders", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5s-stats", "scope": "GangStatsListener (death + downed, de-dup, ledger assists, farm guards, world gate); GangStatsService snapshot; gang top; gang player; gang_top item source; player_profile/gang_stat/gang_top menus; placeholders incl. gang_top tokens and Placeholders.Default_Value.", "modules": ["gangland-gang", "gangland-impl (inventory YAML)"], "docket": [], "tests": "GangStatsListenerTest; GangStatsServiceTest; placeholder tests incl. resolveGlobal", "exit": "command, GUI and tokens agree"},
   {"id": "W5-U", "name": "UX: short roots, info, members, names, MOTD", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5u-ux", "scope": "GangShortRootExecutor (/g, /gc, /ac; D22); gang info by player; gated bank column; members grouped by rank + paging; Name_Pattern and Display_Name keys (D21); gang motd + login line; GangNotifier titles; ally abandon by name.", "modules": ["gangland-impl", "gangland-gang"], "docket": [], "tests": "GangShortRootExecutorTest; GangInfoCommandTest; name-rule table; MOTD members-only", "exit": "/g works for a non-op"},
   {"id": "W5-A", "name": "Staff verbs", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5a-staff", "scope": "/glw option gang disband, setowner, bank, homes, resetstats, spy, purge over the lane services; console-safe; every write logged.", "modules": ["gangland-gang"], "docket": [], "tests": "GangStaffVerbsTest from a console sender; GangCommandsJsonParityTest", "exit": "merges last in W5"},
   {"id": "W5-E1", "name": "Docs (parallel)", "model": "haiku", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w5e1-docs", "scope": "gangs.md rewrite; ranks.md corrected to the real model.", "modules": ["docs"], "docket": [], "tests": "-", "exit": "docs match gang_rules.yml"},
   {"id": "W5-E2", "name": "Merges and migration (after)", "model": "haiku", "role": "executor", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "commands.json 3-way merges; migration section (columns, self-damage, Gang.Enable, level-0 caps, short roots, api value).", "modules": ["docs", "commands.json"], "docket": [], "tests": "-", "exit": "help lists every new leaf"},
   {"id": "W5-G", "name": "Gate review", "model": "opus", "role": "reviewer", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "Adversarial review of W5 diffs: friendly-fire bypasses, teleport escapes, async chat, cap race, api additivity. Never overlaps W3-G.", "modules": ["gangland-gang", "gangland-mail", "gangland-turf", "gangland-civilians", "gangland-api"], "docket": [], "tests": "-", "exit": "no open exploit path without a red test"},
   {"id": "W5-J", "name": "Gate judge", "model": "fable", "role": "advisor", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "Go/no-go after the owner walkthrough.", "modules": [], "docket": [], "tests": "-", "exit": "go"}
  ]
 },
 {
  "id": "W6",
  "name": "Parity: fights, WorldGuard, the parity gate",
  "goal": "Gang fights in 3D arenas with escrowed bets, the WorldGuard friendly-fire flag, and an audited Gangs+ parity: all 45 GP rows HAVE, GP-33 with its recorded partial exclusion.",
  "depends_on": ["W5-K merged (fights lanes)", "W5-F merged (W6-W)", "W5 gate (W6-X)"],
  "branch": "gang-turf-territory",
  "gate": "Gangs+ parity reached: mvn clean package green; opus W6-G review (event ordering, escrow, containment bypasses, WorldGuard load order); W6-X audit shows 45 rows HAVE (GP-33 with its recorded partial exclusion), each with a named test or performed manual step; owner with two clients about 40 min (1v1 with bet: win pays, draw refunds, quit forfeits, /tp blocked; WorldGuard flag in one region); smoke G1 without WorldGuard and without the fights jar; docket rows; graphify update --force; parity rows written to roadmap.json. Merge to master as 0.14.0 after the later of the W4 and W6 gates (D23).",
  "lanes": [
   {"id": "W6-K", "name": "Fights kickoff", "model": "sonnet", "role": "executor", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "gangland-gang-fights skeleton (id fights, Depends [gang], Host_Api = D23 value, mail-shaped pom), module files and YAML, FightContribution and ArenaCommand stubs, commands.json, FightCommandsJsonParityTest; gangland-features and gangland-build pom lines; ModuleInstalls.OFFICIAL gains fights, gang, lootchest (CM-38); plugin.yml fight player nodes.", "modules": ["gangland-gang-fights", "gangland-features/pom.xml", "gangland-build/pom.xml", "gangland-impl (ModuleInstalls, plugin.yml)"], "docket": ["CM-38"], "tests": "ModuleInstallsTest (eight ids); FightCommandsJsonParityTest; one trivial test per stub", "exit": "fights jar lands in target/modules"},
   {"id": "W6-A", "name": "Arenas", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w6a-arenas", "scope": "Arena, ArenaBox (3D), ArenaRegistry, ArenaFile, ArenaDraft, /glw arena verbs, outline.", "modules": ["gangland-gang-fights"], "docket": [], "tests": "ArenaBoxTest; ArenaFileTest; save validation table", "exit": "a saved arena survives arena reload"},
   {"id": "W6-S", "name": "Fight state machine", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w6s-state", "scope": "Fight, FightState, FightManager, FightRules, FightClock, PlayerSnapshot, gang fight player verbs.", "modules": ["gangland-gang-fights"], "docket": [], "tests": "FightRulesTest; FightStateMachineTest (fake clock, every cancel refunds)", "exit": "-"},
   {"id": "W6-E", "name": "Fight economy and stats", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w6e-economy", "scope": "Escrow, FightSettlement, ranked rules, fight_stats/fight_player_stats/fight_record, FightPlaceholderContribution, FightWinsStatContribution, api GangFightEndedEvent.", "modules": ["gangland-gang-fights"], "docket": [], "tests": "FightSettlementTest (real EconomyHandler); FightStatsRepositoryTest; WLR in gang top", "exit": "fights are zero-sum bank to bank"},
   {"id": "W6-F", "name": "Fight guards", "model": "opus", "role": "executor", "parallel": false, "worktree": "E:/Programming/java/wt/gt14-w6f-guards", "scope": "Lethal intercept at NORMAL, containment, command filter, session listener, wanted guard, gang-delete forfeit. Does not start while W4-R and a gate reviewer both run.", "modules": ["gangland-gang-fights"], "docket": [], "tests": "FightDamageListenerTest; CommandFilterTest; FightWantedGuardTest; FightSessionListenerTest; FightGangDeleteListenerTest; manual two-client Spigot check (wanted 0, no drop, no downed state)", "exit": "manual check passed"},
   {"id": "W6-W", "name": "WorldGuard friendly-fire flag", "model": "sonnet", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w6w-worldguard", "scope": "softdepend WorldGuard; WorldGuard api provided on impl and gang; WorldGuardFlagRegistrar from Gangland.onLoad through a type-free factory; StateFlag gangland-friendly-fire; WorldGuardFriendlyFireSource filling FriendlyFireRegionSource (ReflectionGuard skips it without WorldGuard).", "modules": ["gangland-impl", "gangland-gang", "root pom.xml (orchestrator)"], "docket": [], "tests": "WorldGuardFlagRegistrarTest (absent plugin); FriendlyFireRuleTest with a fake region source; smoke G1 without WorldGuard", "exit": "no reflection.type.missing spam without WorldGuard"},
   {"id": "W6-X", "name": "Parity audit", "model": "sonnet", "role": "executor", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "Walk GP-01..GP-45 on the merged branch; each row names and runs its test or manual step; write parity/audit.md; flip status_after_plan to HAVE row by row.", "modules": ["brainstorming"], "docket": [], "tests": "-", "exit": "45 HAVE, GP-33 partial exclusion recorded"},
   {"id": "W6-E1", "name": "Docs (parallel)", "model": "haiku", "role": "executor", "parallel": true, "worktree": "E:/Programming/java/wt/gt14-w6e1-docs", "scope": "documentation/features/fights.md, arena how-to, WorldGuard flag note.", "modules": ["docs"], "docket": [], "tests": "-", "exit": "-"},
   {"id": "W6-E2", "name": "Merges and migration (after)", "model": "haiku", "role": "executor", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "commands.json; migration section (fight tables, WorldGuard optional).", "modules": ["docs", "commands.json"], "docket": [], "tests": "-", "exit": "-"},
   {"id": "W6-G", "name": "Gate review", "model": "opus", "role": "reviewer", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "Fights event ordering, escrow, containment bypasses (pearl, chorus, elytra, riptide), WorldGuard load order.", "modules": ["gangland-gang-fights", "gangland-impl", "gangland-gang"], "docket": [], "tests": "-", "exit": "-"},
   {"id": "W6-J", "name": "Gate judge: Gangs+ parity reached", "model": "fable", "role": "advisor", "parallel": false, "worktree": "E:/Programming/java/wt/gangland-0.14.0", "scope": "Go/no-go on the parity gate and the 0.14.0 merge timing (D23).", "modules": [], "docket": [], "tests": "-", "exit": "go"}
  ]
 }
]
```

### 8.3 Decisions (`roadmap.json` shape, abbreviated details)

```json
[
 {"id": "D16", "question": "Gang level model (GP-10/11)", "options": [{"key": "A", "label": "XP-only", "detail": "W2-P1 as planned; no pay-to-level (GP-10 missing)."}, {"key": "B", "label": "Buy-only", "detail": "Breaks frozen D2; ruled out."}, {"key": "C", "label": "Hybrid: XP qualifies, money claims", "detail": "A level needs its held-turf XP; gang levelup pays Claim_Cost from the bank; default cost 0 auto-claims, so shipped behaviour, W2-P1 tests and D2 pacing are unchanged. Costs above 0 money-gate a poor active gang's turf cap (stricter than D2, never looser). Per-level table: members 8/10/12/14/16/18, homes 1/1/2/2/3/3, allies 1/1/2/2/3/3, turf cap min(6, 2+level), prefix per level."}, {"key": "D", "label": "Separate perk tier", "detail": "Second bought track; two progression numbers on every screen."}], "recommended": "C", "why": "One level number everywhere; money never makes XP; a generous level-0 row removes the bootstrap problem a perk tier was meant to solve.", "blocks": ["W5-L", "W5-M", "W5-H", "W5-C"]},
 {"id": "D17", "question": "Member and ally caps (reverses the PLAN section 12 drop)", "options": [{"key": "A", "label": "Caps from the D16 table (0 = unlimited)", "detail": "Enforced at join/accept and at ally request/accept."}, {"key": "B", "label": "No caps", "detail": "Every row 0."}, {"key": "C", "label": "Member cap only", "detail": "Allies unbounded."}], "recommended": "A", "why": "Owners plus allies all count as defenders (P3.2): an unbounded alliance is a defender blob.", "blocks": ["W5-M", "W5-L"]},
 {"id": "D18", "question": "Homes, regroup and summon vs turf", "options": [{"key": "A", "label": "Veto around turfs under attack, no shield, combat tag, consent", "detail": "Own gang_home table; TeleportEvent veto on source or destination under attack and destination in a non-allied turf."}, {"key": "B", "label": "Stamp arrivals as attackers", "detail": "W2-R file change; defenders can still escape."}, {"key": "C", "label": "Homes only outside every turf", "detail": "Simplest; no home in your own turf."}], "recommended": "A", "why": "Closes the escape hatch and the instant reinforcement that the feature would otherwise open.", "blocks": ["W5-H", "W5-T"]},
 {"id": "D19", "question": "Where do gang fights live?", "options": [{"key": "A", "label": "New runtime module gangland-gang-fights (Depends [gang])", "detail": "fights.md DF1; DF2-DF8 adopted as lane defaults."}, {"key": "B", "label": "Inside gangland-gang", "detail": "A sixth hot lane on gang files."}], "recommended": "A", "why": "Removable, fault-isolated, loads without Bartizan or Citizens, touches no W0-W4 hot file.", "blocks": ["W6-K"]},
 {"id": "D20", "question": "Friendly-fire model", "options": [{"key": "A", "label": "One rule, one api fact, WorldGuard flag", "detail": "Gang toggle default off; ally FF negotiated and PvP-only; guards unconditional; per-world; negative-potions-only; self damage allowed."}, {"key": "B", "label": "A without the WorldGuard flag", "detail": "Half of GP-18 missing."}, {"key": "C", "label": "Gang module only", "detail": "Guns and fire stay blocked while fists follow the toggle."}], "recommended": "A", "why": "One decision point for fists, bows, potions and Bartizan impacts; capture and guard semantics untouched.", "blocks": ["W5-F", "W6-W"]},
 {"id": "D21", "question": "Gang names", "options": [{"key": "A", "label": "ASCII name key by Name_Pattern, free display name", "detail": "Admins may widen the pattern to letters of any script; whitespace patterns refused; console tokens use name; map glyph fallback."}, {"key": "B", "label": "Unicode name by default", "detail": "Map, console tokens and tab completion get harder."}, {"key": "C", "label": "ASCII only", "detail": "Display name restricted too."}], "recommended": "A", "why": "Any-language display with a safe key.", "blocks": ["W5-U", "W1-M (A1)"]},
 {"id": "D22", "question": "Short command roots", "options": [{"key": "A", "label": "Only /glw gang ...", "detail": "Documented commands.yml recipe, no tab completion."}, {"key": "B", "label": "/g, /gc, /ac in plugin.yml", "detail": "One delegating executor with tab completion; /gangland_warfare:g on collision."}, {"key": "C", "label": "B with admin-chosen labels", "detail": "Runtime CommandMap helper in Keystone 1.15.0."}], "recommended": "B", "why": "Short roots are table stakes for gang plugins; C needs a Keystone release this wave does not have.", "blocks": ["W5-K", "W5-U"]},
 {"id": "D23", "question": "Release shape and api bump", "options": [{"key": "A", "label": "One 0.14.0 after the later of the W4 and W6 gates; one api bump", "detail": "W5-K bumps, W4-R adds under it; if H13 is late, ship without W4 (W4 becomes 0.14.1)."}, {"key": "B", "label": "0.14.0 at W4; parity as 0.15.0", "detail": "Two api bumps and two migration docs."}], "recommended": "A", "why": "One upgrade for server owners, one Host_Api value.", "blocks": ["W5-K", "W4-R", "final merge"]},
 {"id": "D24", "question": "Auto-purge default", "options": [{"key": "A", "label": "Off; staff preview then run", "detail": "Purge.Inactive_Days 30 (gangs) / 60 (members) when enabled."}, {"key": "B", "label": "On by default", "detail": "Data-loss risk on upgrade."}], "recommended": "A", "why": "A deleted gang cannot be restored.", "blocks": ["W5-M"]}
]
```
