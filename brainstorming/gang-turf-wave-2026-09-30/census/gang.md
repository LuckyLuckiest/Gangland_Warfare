# Census: gangland-gang runtime module (0.12.0, master @ ff9d813f)

Scope: `gangland-features/gangland-gang` (114 java files, 26 test classes) plus the two modules that hang commands under it
(`gangland-mail`, `gangland-turf` consumers). Graphify was oriented first (`graphify query "GangLookupContract"`,
`graphify affected "GangLookupContract"`; graph.json 2026-09-29 23:08 vs latest commit 23:05, so fresh). Every fact below
was then read from source; paths are relative to the repo root. This is a census, not a plan: classes marked NEW appear
only in section 4 as candidate fixes.

Module descriptor: `gangland-gang/src/main/resources/module.yml` = `Id: gang`, `Main: org.luckyraven.gangland.gang.GangModule`,
`Host_Api: 2.0`, no `Depends:` and no `Plugins:` (CLAUDE.md still says `Host_Api: 1.0`; stale).
`GangModule.configure` registers `GangConfig`, listener package `org.luckyraven.gangland.listener.gang`, command packages
`...command.sub.gang` and `...command.sub.rank`, repository package `...gang.database`.

---

## 1. Data model

| Concept | Where | Shape / behaviour |
|---|---|---|
| `Gang` | `gang/Gang.java` | `id` (random int `generateId()`, `new Random()` per call; only `GangCreateCommand` rejection-samples for collisions), `name`, `displayName` (nullable, coloured with `&` codes + `Display_Name_Char` suffix), `color` (enum name from Keystone `Color`, default `LIGHT_BLUE`), `description` (default "Conquering the hood"), `created`, `lastMemberOnlineAt`, `state`, `members` (List, copy on read), `allies` (Set of `GangAlliance`), `level`, `bounty`, `economy`. `equals` is by id, **no `hashCode`** (verified: none in file). |
| `Gang.State` | `Gang.java` bottom | `OPEN, INVITE, CLOSE`. Persisted (`gang.state`), exposed as `%gang_state%`. **Nothing sets it and nothing reads it** (grep over gang, mail, turf, impl). Modelled but inert. |
| `GangAlliance` | `gang/GangAlliance.java` | `record (Gang gang, Gang ally, long since)`. Stored one row per direction. |
| `Member` | `gang/member/Member.java` | `uuid`, `gangId` (-1 = none), `contribution` (double), nullable `Rank`, `gangJoinDateLong`. `hasPermission(node)` = rank's *own* node list OR Vault. |
| `Rank` | `gang/rank/Rank.java` | `name`, `usedId` (static `ID` counter), `Tree.Node<Rank>`, `List<Permission>`, nullable `vaultGroup`. `contains(node)` checks its own list only. |
| Rank tree | `RankManager` | **One global tree for the whole server, not per gang.** Root = Head rank (`Gang.Rank.Head`, default `member`), deepest leaf = Tail (`Gang.Rank.Tail`, default `owner`). Seeded on every boot by `RankRepository.insertInitialRanks` + `RankParentRepository.insertInitialRelation`: exactly two ranks, `member -> owner`, no permissions on either. Anything between is built by an admin with `/glw rank`. |
| Bank | `Gang.economy` (Keystone `EconomyHandler`) | Single vault balance. `Gang.Account.Create_Cost` (100_000) charged on create, quarter refunded on disband; `Initial_Balance` 0; `Maximum_Balance` 100e9 (deposit cap only). |
| Contribution units | `Member.contribution`, `GangDepositCommand`/`GangWithdrawCommand` | Deposit adds `amount / Contribution_Rate` (1000 by default, rounded to the rate's digit count); withdraw *subtracts* the same and may go negative (comment in `GangWithdrawCommand`: "the user can get to negative value"). Disband splits the vault pro rata on `max(0, contribution)` (`GangDeleteCommand.payouts`, owner takes rounding remainder). **The same field is also fed by turf** (`TurfContributionTickTask:97`, `TurfContributionListener:68` call `member.increaseContribution(points)` with 0.5/1.0/50/25 points from `settings.yml` `Turf.Contribution.Points`), so "money-backed share" and "activity score" are one number with two units. |
| Level / XP | `gangland-core` `core/user/Level.java`, `Gang.level` | Built with `IdentitySettings.getUserMaxLevel()/getUserLevelBaseAmount()` and the *user* level formula; there is no gang-specific curve or setting. Persisted (`gang.level`, `gang.experience`). **No producer**: `addExperience`/`addLevels` on a gang level is never called anywhere (the XP call sites in civilians/lootchest/level commands all target `user.getLevel()`). Gang level is 0 forever; only placeholders (`%gang_level%`, `%gang_experience%`, ...) read it. |
| Bounty | `gangland-core` `core/bounty/Bounty.java`, `Gang.bounty` | Constructed from `IdentitySettings` user bounty values. Persisted (`gang.bounty`), shown in `gang_info.yml` and `%gang_bounty%`/`%gang_has-bounty%`. No command posts a gang bounty and no code adds to it (`BountySetCommand` targets users). Inert. |
| Presence | `Gang.lastMemberOnlineAt` | Written only by turf (`GangPresenceTracker:83`, `GangPresenceListener:62`); persisted column `last_member_online_at`. Gang module itself never touches it. |
| Member cap | nowhere | No max members, max gangs, max allies, min/max name length, or per-gang rank customisation anywhere in code or config (grep of `max.?members\|member.?cap\|max.?allies` across gang, mail, impl, api, docs = 0 hits). |

### Persistence (module-owned; `gang/database/**`)

| Table | Class | PK / notes | Repository |
|---|---|---|---|
| `gang` | `GangTable` | `id`; name, display_name, description, color, balance, level, experience, bounty, created, last_member_online_at, state | `GangRepository` |
| `gang_ally` | `GangAllianceTable` | composite `(gang_id, ally_id)`, both FK to `gang.id`; `since` | `GangAllianceRepository` (`deleteAllForGang`) |
| `member` | `MemberTable` | `uuid` (FK to user table), gang_id, contribution, rank_id (FK), join_date | `MemberRepository` (`MemberRepositoryContract`) |
| `rank_tree` | `RankTable` | `id`; name, vault_group | `RankRepository` |
| `rank_parent` | `RankParentTable` | `(id, parent_id)` | `RankParentRepository` (has migration test) |
| `rank_permission` | `RankPermissionTable` | `(rank_id, permission_id)` | `RankPermissionRepository` |
| `permission` | `PermissionTable` | `id`; name | `PermissionRepository` |

`GangManager.initialize()` loads gangs then alliances, wires `gangRepository/allianceRepository.setDataSupplier(...)`.
`GangManager.breakAlliance(a, b)` deletes both DB rows then both memory sides (the GR-06 fix). Foreign-key shims:
`database/fk/ForeignUserTable`, `ForeignPermissionTable`.

---

## 2. Surface area

### 2.1 Commands (31 classes in the module)

`GangModuleTest` javadoc says 31; confirmed: 16 gang files (`GangCommand` + 15 subs) + 2 ally + 13 rank.
Root `gang` is player-only (`super(gangland, "gang", true)`); root `rank` is console-allowed (`super(gangland, "rank", false)`).
Node = `gangland.command.<root>` (Keystone-derived, OP default; sub-arguments inherit it). Extra gates below are in-code.

| Command | Argument shape | Gate | Notes |
|---|---|---|---|
| `/glw gang` | none | none | A YAML menu (`gang_info.yml`) intercepts it for gang members; anyone else gets the help page (`GangCommand.onExecute`). |
| `gang create <name>` then `confirm` | one word (`OptionalArgument`, tab hint `<name>`) | none; needs `Create_Cost` in wallet | 60 s confirm window with countdown chat lines. No length/character validation; duplicate check `equalsIgnoreCase` unless `Name_Duplicates`. Persists gang + member immediately. |
| `gang delete` (aliases `remove`, `del`) then `confirm` | none | owner rank | Splits vault by contribution, resets every member (online and offline, synchronous), deletes gang + alliance rows, refunds 25% of fee, fires `GangDeleteEvent`. |
| `gang kick <name>` | member name | rank-tree descendant check only (no permission node) | Offline members resolved via `Bukkit.getOfflinePlayer` name scan. |
| `gang leave` | none; run twice inside the window | not owner | "Type twice" confirm; a different confirm idiom from create/delete/transfer. Forfeits contribution. |
| `gang promote <name>` / `demote <name>` | member name | rank-tree checks via `RankAssignmentPolicy`; `gangland.admin.gang.force_rank` skips them | Walks one child node; branching tree sends a clickable list running `/glw option gang rank <player> <rank>`. Never promotes to Tail. |
| `gang transfer <name>` then `confirm` | member name | owner rank | Old owner drops to the Tail's parent rank. |
| `gang deposit <amount>` / `withdraw <amount>` | amount | `GangPermissions.DEPOSIT` / `WITHDRAW` | See gate semantics below. |
| `gang balance` (`bal`) | none | none | Chat line. |
| `gang members` (`list`) | none | none | Chat roster with rank + online state. The `list` alias lists members, not gangs. |
| `gang rename <name>` | one word | `RENAME` | Class label is `rename`; `commands.json` documents `/glw gang name`. |
| `gang desc` (`description`) | none | `DESCRIPTION` | Opens an AnvilGUI text editor. |
| `gang display <name>` / `display remove` | one word | `DISPLAY` | `&` codes. |
| `gang color` | none | `COLOR` | Opens a 16-colour wool GUI (`keystone-inventory`) then a confirm screen. |
| `gang ally abandon <id>` | numeric gang id (tab-completion maps names to ids, `name:id` on duplicates) | `ALLY` | Only ally command in this module; calls `GangManager.breakAlliance`. |
| `gang ally` (bare) | none | none | Prints `<request/abandon/accept/reject/pending>` when mail loaded, `<abandon>` otherwise. |
| `rank create <name>` then `confirm` | name | admin (root node) | Creates a rank not yet attached to the tree. |
| `rank delete` (`remove`, `del`) `<name>` | name | admin | Head/Tail, held ranks, and ranks with ranks above are refused (per `ranks.md`, not re-verified in code). |
| `rank list`, `rank info <name>`, `rank traverse` | see left | admin | Read-only views. |
| `rank parent add\|remove <rank> <parent>` | two ranks | admin | `RankManager.addParent/removeParent` persist immediately and refuse cycles. |
| `rank permission add\|remove <rank> <permission>` (alias `perm`) | rank + free-text node | admin | Pushes to online members' attachments and to Vault. |
| `rank vaultgroup` (`vgroup`) `<rank> <group>\|clear` | rank + group | admin | Links a rank to a Vault permission group. |

Commands contributed *into* the gang tree by `gangland-mail` (`Depends: [gang]`; `MailModuleConfig`, `GangMailContribution` on path
`gang`, `GangAllyMailContribution` on path `gang.ally`), 8 classes:
`gang invite <name>` (alias `add`), `gang invite` (lists outgoing), `gang invite cancel <name>`, `gang accept [gang]`,
`gang ally request <gang>`, `gang ally accept [gang]`, `gang ally reject [gang]`, `gang ally pending`, `gang ally pending cancel [gang]`.
Invite and ally-request expiry is 60 s (`INVITE_EXPIRY_MS`, `REQUEST_EXPIRY_MS`); an invite to an *offline* target never
expires (`expireAt = 0`). Invite acceptance joins at the Head rank. There is **no invite decline/reject** command.

Commands contributed *from* the gang module into the core: `/glw option gang rank <player> <rank>` (`GangOptionContribution`)
and `/glw debug gang-data|member-data|rank-data` (`GangDebugContribution`).

`commands.json` (module resource, 33 entries) drift: `gang_change_name` says `name` (real label `rename`); `gang_help`,
`gang_ally_remove` (real `abandon`); mail's `commands.json` has no entries for `ally accept`, `ally reject`, or invite decline (does not exist).

### 2.2 Permission gate semantics (`gang/permission/GangPermissions.java`)

Nodes: `gangland.gang.{withdraw,deposit,rename,description,display,color,invite,ally}` plus staff `gangland.admin.gang.force_rank`.
`allows(member, player, node)` = server grants the node to the player OR the member's rank list holds it (or Vault) OR the
member holds the **top rank** (deepest leaf). None of the `gangland.gang.*` nodes is declared in `plugin.yml` or
registered (only `FORCE_RANK` is added to `PermissionManager`). Consequence on a fresh install with the default 2-rank
tree: only the owner can deposit, withdraw, rename, describe, colour, invite, or manage allies; an ordinary `member` can
do none of it, and `promote` has nowhere to go. `RankPermissionApplier` (static) syncs a member's rank nodes onto their live
`PermissionAttachment` on join (`MemberJoinListener`), promote, demote, transfer and removal.

### 2.3 Menus, item sources, placeholders, events, listeners

- **YAML menus** (all in `gangland-impl/src/main/resources/inventory/`, registered in `GameplayConfig`): `gang_info.yml`
  (77 lines; buttons open `alliance_stat`, `gang_stat`, and run `/glw gang desc`, `/glw gang color`), `alliance_stat.yml`
  (`Item_Source: gang_allies`), `gang_stat.yml` (8 lines, only `Size: 54`, **empty screen**), `phone_gang.yml` (anvil "Create Gang"
  -> `/glw gang create %gangland_anvil_output%`), `phone_gang_search.yml` (`Item_Source: gangs`, filter buttons).
- **`GangMenuItemSourceContribution`** (`gang/menu/`, an api `GangItemSourceContribution`, produced in `GangConfig`): item sources
  `gangs`, `gang_members`, `gang_allies`, backed by `FilterStore/FilterApplier` and the module's `GangFilterAdapter`/`MemberFilterAdapter`.
  Per-gang lore placeholders: `gang_id`, `gang_display-name`, `gang_color-code`, `gang_description`, `gang_leader_name`,
  `gang_members-size`, `gang_online-members-size`, `gang_created`.
- **`GangPlaceholderContribution`** (`gang/placeholder/`): `user_` family (`has-gang`, `gang-id`, `gang-join-date`, `contribution`,
  `contributed-amount`, `has-rank`, `rank`) and `gang_` family (`id`, `name`, `display-name`, `state`, `color`, `color-name`,
  `color-code`, `description`, `created`, `balance`, `bounty`, `has-bounty`, `members-size`, `online-members-size`,
  `offline-members-size`, `ally-list`, `ally-size`, plus the level family `level`, `level-max`, `level-next`, `level-previous`,
  `experience`, `experience-percentage`, `experience-next-level`, `experience-previous-level`, `experience-current-level`,
  `experience-level-<n>`). No turf-related gang placeholder (turfs owned, income, capture score).
- **Events** (`events/gang/`): `GangDeleteEvent` (fired from `GangDeleteCommand:286`; only consumer is mail's `MailGangDeleteListener`),
  `GangLevelUpEvent` (extends core `LevelUpEvent`) and `GangBountyEvent` (extends core `BountyEvent`) are **never constructed**; their
  only references are `GangBountyMessageListener` / `GangLevelMessageListener`, which therefore never fire. **No event exists** for gang
  create, rename, colour/display change, member join/leave/kick, rank change, ally added/removed, or bank deposit/withdraw.
- **Listeners** (`listener/gang/`): `GangMembersDamageListener` (cancels player-vs-player damage between same-gang or allied players,
  no toggle, no config), `MemberJoinListener` (creates a `Member` for a first-time player, applies rank permissions on `UserDataInitEvent`),
  `GangBountyMessageListener`, `GangLevelMessageListener` (both dead, above).
- **Vault**: `gang/vault/permission/VaultPermissionBridge` (facade over Keystone `VaultOfflinePermissionService`, linked in `GangModule.onEnabled`).

### 2.4 Settings (`settings.yml` `Gang:` block, read by `GangSettings` facade / `GanglandGangSettings` and the api `Settings`)

`Enable` (only `GangAllyWeaponImpactListener` in civilians reads it now; `GangCommand` and `GangMembersDamageListener` are no longer
gated, so `Gang.Enable: false` does **not** disable gangs), `Name_Duplicates`, `Display_Name_Char`, `Rank.Head`, `Rank.Tail`,
`Account.Initial_Balance`, `Account.Create_Cost`, `Account.Maximum_Balance`, `Account.Contribution_Rate`. Nine keys. No module YAML (the
"module owns its YAML" move is deferred per `GangConfig` javadoc). Messages: 88 `GANG_*`/`RANK_*` constants in the api `Messages` enum.

---

## 3. Seams exported to other modules

| Seam | Lives in | Consumers (verified) | Notes |
|---|---|---|---|
| `GangMembership` + `GangMembershipView` | `gangland-api` `data/gang/` (core-owned holder, exposed via `GanglandApi.gangs()`) | civilians `GangAllyWeaponImpactListener`, cops-n-crooks `TurfFriendlyFireListener`, gadget `GanglandCarGangs`, impl waypoints | Three methods only: `gangIdOf(UUID)`, `gangsAllied(idA,idB)`, `nameOf(id)` (+ `alliedOrSame`, `isInstalled`). Installed by `GangMembershipInstaller` (a `@Bean` producing a `@PostConstruct` installer). Inert when the module is absent. This is the *only* gang seam in the api. |
| `GangLookupContract` | **`gangland-gang`** (`gang/contract/`), *not* the api (`GanglandApi` javadoc says so on purpose) | `GangManager` implements it; turf's `CaptureService`, `TurfCommand`, `TurfInfoCommand`, `TurfListCommand`, `TurfSetOwnerCommand`, `TurfStatusCommand`, `TurfIncomeDistributor`, `InactivityReleaseTask`, `TurfPowerupOpenContractImpl`, mail | Returns full `Gang` domain objects (`findById`, `getAll`). |
| Direct module coupling (de-facto seam) | `gangland-mail/pom.xml` and `gangland-turf/pom.xml` both declare `gangland-gang` at `provided` scope with `Depends: [gang]` in `module.yml` | mail: `GangManager` (`MailModuleConfig`, ally and invite commands), `GangPermissions`; turf: `Gang`, `Member`, `MemberManager`, `Gang.getEconomy()`, `Member.increaseContribution` | So turf reaches deep into gang internals (bank via `gang.getEconomy()` in the powerup views, contribution via `Member`), not through `gangland-api`. civilians, cops-n-crooks and gadget carry **no** gang dependency (they use `GangMembership`). |
| `GangItemSourceContribution` | `gangland-api` `data/gang/` | implemented by `GangMenuItemSourceContribution`, folded into the core inventory item sources | Feeds `gangs`, `gang_members`, `gang_allies`. |
| `CommandContribution` paths `gang`, `gang.ally` | Keystone/api; queried by `GangCommand` and `GangAllyCommand` via `CommandContributions.from(container)` | mail (invite/accept and the alliance request flow) | Path `rank` is not extensible. |
| `PlaceholderContribution` | api | `GangPlaceholderContribution` | Core PlaceholderService asks the module. |
| `GangPermissions`, `RankPermissionApplier`, `RankAssignmentPolicy` | gang (static utility classes) | mail (`GangPermissions`), option contribution (`RankAssignmentPolicy`) | Not in the api; mail imports them because of `Depends: [gang]`. |
| `GangDeleteEvent` | gang | mail only | Turf does not listen; it polls (`InactivityReleaseTask:36` clears ownership when `findById` returns null), so a disbanded gang keeps its turfs until that task next runs. |

---

## 4. New-player journey and friction analysis

Ordering follows what a first-time player does. Suggested fixes are candidates for the plan wave, not decisions.

| Step | What happens today | Friction / gap |
|---|---|---|
| **Discover gangs exist** | Player types `/glw gang`: no gang -> the help page (a list of subcommands with usage). Phone menu `phone_gang.yml` offers an anvil "Create Gang". | No welcome, no "what is a gang / what does it cost" text, no gang browser reachable from the command (`gangs` item source exists only inside `phone_gang_search.yml`). `list` means "my members". No `gang list` / `gang top` / `gang info <name>` for other gangs. |
| **Create** | `gang create <name>`, then `confirm` within 60 s; fee 100_000 charged. | Fee is stated but the player gets no preview of what a gang provides. Names: one word, no length, character, or colour-code validation (audit Obs. #29 still open) so `&4Admin` style spoofing works. No creation event for other systems. |
| **Invite / join** | Owner runs `gang invite <name>` (mail module). Target has 60 s if online and runs `gang accept` (multiple invites -> `accept [gang]`). | No decline command (Obs. #24). No public join for `OPEN` gangs: `Gang.State` exists and is persisted but is not wired (candidate: `gang join <gang>` honouring OPEN/INVITE/CLOSE). No member cap, so one gang can absorb the whole server. An invite to an offline player never expires. |
| **What can a new member do?** | Joins at Head rank `member`, which has no nodes. `deposit`, `withdraw`, `rename`, `desc`, `display`, `color`, `invite`, `ally` all need a `gangland.gang.*` node the rank does not carry. Only the top rank passes. | The most confusing point: a normal member gets a generic "no permission" for a deposit. `ranks.md` teaches nodes like `gang.deposit`, `gang.chat`, `gang.kick`, which do nothing (real nodes are `gangland.gang.*`; `gang.chat` and `gang.promote` do not exist). No `/glw rank permission` tab-completion of valid nodes, no default officer rank. |
| **Promote** | `gang promote <name>` walks the global tree. Default tree is `member -> owner`; promotion to Tail is forbidden, so `promote` is a dead end until an admin builds ranks. | Ranks are global and admin-only: a gang leader cannot name their own "Lieutenant". No rank-shaped UI for players (`rank list/info/traverse` are admin-gated by the root node). Promote message for the dead end is the "transfer ownership" line, which reads oddly. |
| **Bank** | `deposit`/`withdraw` show `-<contribution>` in raw colour text; `balance` prints a line. Contribution = amount / 1000; withdraw can push it negative. | No per-member ledger view (`contribution` is only a placeholder), no bank log, no vault cap warning beyond an error. Contribution is the payout share on disband **and** the turf activity score, so a member who farms turf points can out-earn depositors on disband; `gangs.md` says withdraw is "limited to your contribution", which the code does not enforce. |
| **Ally** | `gang ally request <gang>` (mail), other side runs `ally accept`/`reject` within 60 s (paused if the target gang is offline). `ally abandon <id>`. | Numeric gang ids in usage and docs (tab-completion hides it; typing a name by hand fails). `alliance_stat.yml` click runs `/glw gang ally info %ally_id%`, a command that does not exist (Obs. #16). Allies: friendly fire is permanently off, no toggle, no shared-turf semantics, no alliance limit, no expiry. |
| **Leave / disband** | `leave` (type twice), `delete` (confirm), `transfer <name>` (confirm). Owner cannot leave. | Three different confirm idioms. Disband asks for confirmation but does not show the payout table first. Deleted gang's turfs linger until the polling task. |
| **Everyday play** | Members see the YAML info menu and placeholders. | **Referenced but unimplemented:** gang chat (`documentation/tests/features/gangs.md` overview lists it; `ranks.md` example uses `gang.chat`), gang home/HQ, war/declared-conflict state, upkeep, member cap, leaderboards (`settings.yml` comment says the module "exposes it to commands / leaderboards"), gang level/XP progression (level, XP and bounty are persisted and shown but nothing feeds them), gang bounty posting, `Gang.State`, `gang_stat.yml` (empty), `ally info`. No gang-wide notifications for kick/promote/rename (only some broadcasts), no join/leave/kick events. |

Candidate fix classes (NEW, for the planners to weigh; nothing exists yet): `GangJoinCommand` (state-aware join),
`GangInfoCommand`/`GangListCommand` (public gang browser), `GangChatService` + `GangChatCommand`, `GangSettings` cap knobs
(`Max_Members`, `Max_Allies`), `GangStateCommand`, `GangCreatedEvent`/`GangMemberJoinEvent`/`GangMemberLeaveEvent`/`GangRenamedEvent`
(needed by any map that must refresh when ownership, colour or name changes), a default rank preset + `gangland.gang.*` node registration,
and a gang-XP producer (e.g. turf capture -> `GangLevelUpEvent`).

---

## 5. Comparison with docs and the workflow audit

### 5.1 `documentation/features/gangs.md`

Accurate: create/confirm flow and 60 s windows, invite/accept, leave semantics, promote/demote traversal, disband split and 25% refund,
alliance command list, `Gang:` settings block. Drifted:

1. Withdraw: "up to your contributed amount" vs code (vault balance only, contribution may go negative).
2. `gang invite`: "60 seconds" holds only for an online target; offline invites never expire.
3. "The gang is immediately active and can accept invites" says nothing about member ranks having no permissions by default.
4. Alliance rows show `<gang_id>` while the client experience is tab-completed names.
5. Missing entirely: `gang members`, `gang balance` alias `bal`, `gang transfer`, `gang delete` aliases, the `gangland.gang.*` permission nodes, `gang ally pending`.

### 5.2 `documentation/features/ranks.md`

1. Claims recursive permission inheritance ("resolved at the time of a permission check"). **Not implemented**: `Rank.contains` checks its own list; `Member.hasPermission` = own list OR Vault. Tree parentage only orders promote/demote.
2. Its `## API` block (`getRank` returning `Optional`, `hasPermission`, `resolvePermissions`, `getParent`, `setParent`, `removeParent(child)`) matches no method on `RankManager` (real API: `get(String)`, `addParent/removeParent(rank,parent)`, `addPermission/removePermission`, `getRankTree`).
3. Example nodes (`gang.chat`, `gang.deposit`, `gang.invite`, `gang.kick`, `gang.promote`, `gang.delete`, `gang.rename`) are wrong: only `gangland.gang.{withdraw,deposit,rename,description,display,color,invite,ally}` are read; kick/promote/delete are gated by rank position, not nodes.
4. "Leader can do everything" is true only via the top-rank clause in `GangPermissions.allows`.

### 5.3 `docs/wiki/workflow-audit-06-gangs-ranks-mail.md` Observations (37 rows)

That page is stale: its Components table cites `gangland-infra/gangland-domain` and `gangland-impl` paths from before the module split
(everything now lives in `gangland-features/gangland-gang`). Status column below uses only what this census read; the rest is deliberately
not re-verified (state lives in the bug docket by GR-nn id, not read here).

| # | Obs | Status seen in code |
|---|---|---|
| 1 | Null `Member` NPEs | **Fixed** (GR-01 guards visible in create/delete/kick/leave/promote/demote) |
| 2 | Async offline payout in disband | **Fixed** (GR-02, synchronous loop in `GangDeleteCommand`, comment cites it) |
| 3 | No gate on withdraw/deposit/rename/... | **Fixed** (GR-03, `GangPermissions`); but see 2.2 for the new default-install consequence |
| 4 | Rank parent edits not persisted | **Fixed** (`RankManager.addParent/removeParent` persist and refuse cycles) |
| 5 | `gang_ally` single-column PK | **Fixed** (`GangAllianceTable` composite `gang_id, ally_id`) |
| 6 | Abandon did not delete DB rows | **Fixed** (GR-06, `GangManager.breakAlliance`) |
| 7 | Promote with >1 child sent nothing | **Fixed** (`GangPromoteCommand` now sends the clickable list) |
| 8 | `option gang rank` escalation | **Fixed** (GR-08, `RankAssignmentPolicy`) |
| 9 | Rank delete hygiene | **Fixed** in `RankManager.remove` (prunes links, detaches node) per its code; guard list per `ranks.md` |
| 16 | `alliance_stat.yml` runs `/glw gang ally info` | **Still open** (`alliance_stat.yml:20`; no such subcommand in `GangAllyCommand`) |
| 17 | `gang_stat.yml` empty | **Still open** (8 lines, `Size: 54`, still linked from `gang_info.yml:77`) |
| 18 | Dead `gangStat` GUI code | **Fixed** (deleted; tombstone comment in `GangCommand`) |
| 19 | `GangBountyEvent`/`GangLevelUpEvent` never constructed | **Still open**, and broader: no producer of gang XP or bounty at all |
| 24 | No invite reject; commands.json gaps | **Still open** (no decline command; mail `commands.json` lacks `ally accept/reject`) |
| 29 | Gang name validation | **Still open** (no length/char checks in create or rename) |
| 33 | `generateId` via `new Random()` | **Still open** (only `GangCreateCommand` rejection-samples) |
| 35 | `Gang.equals` without `hashCode` | **Still open** (no `hashCode` in `Gang.java`) |
| 10-15, 20-23, 25-28, 30-32, 34, 36-37 | various | Not re-verified; status lives in the bug docket by GR-nn id. |

New observations from this census, not in the audit: (a) `Gang.State` is modelled and persisted but unwired; (b) `Gang.Enable` no longer
disables gangs; (c) contribution is dual-unit (bank money vs turf points); (d) default rank tree leaves plain members with no permissions and `promote` a dead end;
(e) `ranks.md` documents an inheritance model and API that do not exist; (f) no lifecycle events for gang/member/ally changes, so downstream modules poll;
(g) `commands.json` `gang_change_name` says `name`, the real label is `rename`; (h) CLAUDE.md `Host_Api: 1.0` is stale (modules say `2.0`).

---

## 6. Facts a map/turf planner should carry forward

- Gang identity is `int id` + display name + `Color` enum name; there is no gang tag, banner, emblem, or symbol beyond `color`/`display`.
- Turf reads gangs through `GangLookupContract` and mutates `Member.contribution` and `gang.getEconomy()` directly; any turf/map feature that needs
  gang membership changes must either poll or get new events (section 3).
- The seam that keeps civilians/cops-n-crooks/gadget gang-free is `GangMembership` (3 methods, gang-id and ally facts only); a map that shows gang
  colours/names for other gangs has no api-level accessor today (`nameOf` exists; no `colorOf`).
- Ranks are global; per-gang customisation would be a schema change (`rank_tree` has no gang column).
- The gang module has 26 test classes (command, manager, repository SPI, permission, policy); any new gang feature should follow
  the `@TempDir(cleanup = CleanupMode.NEVER)` + `releaseDbFiles` rule in CLAUDE.md.
