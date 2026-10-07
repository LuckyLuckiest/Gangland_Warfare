# Gang & Turf UX Surface Inventory
**Created:** 2026-09-30 | **Revision:** 0.12.0 | **Scope:** Player-facing touchpoints for gangs and turf systems

---

## Command Tree

### Gang Command Family (`/glw gang` + `/glw rank` + `/glw ally`)

**Base permission:** `gangland.command.gang.*`

#### `/glw gang` — Gang Management (17 leaf commands)
| Command | Usage | Argument Shape | Perm | Tab Complete | Help |
|---------|-------|-----------------|------|--------------|------|
| help | `/glw gang help [page]` | OptionalArgument:page | `.gang.help` | YES | YES |
| create | `/glw gang create <name>` | OptionalArgument:name | `.gang.create` | YES | YES |
| delete | `/glw gang delete` | none | `.gang.delete` | N/A | YES |
| display | `/glw gang display <name>` | OptionalArgument:name | `.gang.display` | YES | YES |
| display remove | `/glw gang display remove` | none | `.gang.display` | N/A | YES |
| color | `/glw gang color` | none | `.gang.color` | N/A | YES |
| deposit | `/glw gang deposit <amount>` | OptionalArgument:amount | `.gang.deposit` | YES | YES |
| withdraw | `/glw gang withdraw <amount>` | OptionalArgument:amount | `.gang.withdraw` | YES | YES |
| balance | `/glw gang balance` | none | `.gang.balance` | N/A | YES |
| members | `/glw gang members` | none | `.gang.members` | N/A | YES |
| name | `/glw gang name <name>` | OptionalArgument:name | `.gang.name` | YES | YES |
| desc | `/glw gang desc` | none | `.gang.desc` | N/A | YES |
| kick | `/glw gang kick <name>` | OptionalArgument:name (player) | `.gang.kick` | YES | YES |
| leave | `/glw gang leave` | none | `.gang.leave` | N/A | YES |
| promote | `/glw gang promote <name>` | OptionalArgument:name (player) | `.gang.promote` | YES | YES |
| demote | `/glw gang demote <name>` | OptionalArgument:name (player) | `.gang.demote` | YES | YES |
| transfer | `/glw gang transfer <name>` | OptionalArgument:name + confirm | `.gang.transfer` | YES | YES |
| ally abandon | `/glw gang ally abandon <id>` | SubArgument:id (numeric) | `.gang.ally` | **NO** | YES |

**Flags:**
- ⚠️ `/glw gang ally abandon <id>`: Forces players to remember numeric gang IDs (no name completion)
- ⚠️ `/glw gang display remove`: Subcommand inconsistency (no `display delete`)
- ⚠️ `/glw gang desc`: No argument name clarity (description for what?)
- ⚠️ Rename/Delete inconsistency: `delete` vs `name` (both modify gang, different verbs)

#### `/glw rank` — Rank Hierarchy (10 leaf commands)
| Command | Usage | Argument Shape | Perm | Tab Complete | Help |
|---------|-------|-----------------|------|--------------|------|
| help | `/glw rank help [page]` | OptionalArgument:page | `.rank.help` | YES | YES |
| create | `/glw rank create <name>` | OptionalArgument:name | `.rank.create` | YES | YES |
| delete | `/glw rank delete <name>` | OptionalArgument:name | `.rank.delete` | YES | YES |
| list | `/glw rank list` | none | `.rank.list` | N/A | YES |
| permission add | `/glw rank permission add <name> <perm>` | SubArgument:name + SubArgument:perm | `.rank.permission` | **PARTIAL** | YES |
| permission remove | `/glw rank permission remove <name> <perm>` | SubArgument:name + SubArgument:perm | `.rank.permission` | **PARTIAL** | YES |
| info | `/glw rank info <name>` | OptionalArgument:name | `.rank.info` | YES | YES |
| traverse | `/glw rank traverse` | none | `.rank.traverse` | N/A | YES |
| parent add | `/glw rank parent add <rank> <parent>` | SubArgument + SubArgument | `.rank.parent` | **NO** | YES |
| parent remove | `/glw rank parent remove <rank> <parent>` | SubArgument + SubArgument | `.rank.parent` | **NO** | YES |
| vaultgroup set | `/glw rank vaultgroup <rank> <group>` | SubArgument:rank + SubArgument:group | `.rank.vaultgroup` | **NO** | YES |
| vaultgroup clear | `/glw rank vaultgroup <rank> clear` | SubArgument:rank | `.rank.vaultgroup` | **NO** | YES |

**Flags:**
- ⚠️ Permission subcommands use `SubArgument` (fixed args, no tab completion for rank name)
- ⚠️ Parent add/remove require numeric IDs or manual lookup (no rank name completion)
- ⚠️ Vault group subcommands have no permission string suggestions
- ⚠️ Inconsistent sub-levels: permission vs parent vs vaultgroup (three different nesting styles)

### Turf Command Family (`/glw turf`)

**Base permission:** `gangland.command.turf.*`

#### `/glw turf` — Territory Management (14 leaf commands)
| Command | Usage | Argument Shape | Perm | Tab Complete | Help |
|---------|-------|-----------------|------|--------------|------|
| show info | `/glw turf` | none | `.turf` | N/A | YES |
| wand | `/glw turf wand` | none | `.turf.wand` | N/A | YES |
| pos1 | `/glw turf pos1` | none | `.turf.pos1` | N/A | YES |
| pos2 | `/glw turf pos2` | none | `.turf.pos2` | N/A | YES |
| create | `/glw turf create <displayName>` | OptionalArgument:name | `.turf.create` | YES | YES |
| delete | `/glw turf delete` | none | `.turf.delete` | N/A | YES |
| setowner | `/glw turf setowner <gang\|none>` | SubArgument (enum) | `.turf.setowner` | **NO** | YES |
| list | `/glw turf list` | none | `.turf.list` | N/A | YES |
| info | `/glw turf info` | none | `.turf.info` | N/A | YES |
| show | `/glw turf show` | none | `.turf.show` | N/A | YES |
| status | `/glw turf status` | none | `.turf.status` | N/A | YES |
| select | `/glw turf select [id]` | OptionalArgument:id (numeric) | `.turf.select` | YES | YES |
| tp | `/glw turf tp [id]` | OptionalArgument:id (numeric) | `.turf.tp` | YES | YES |
| income | `/glw turf income <amount>` | SubArgument:amount | `.turf.income` | YES | YES |
| garrison | `/glw turf garrison [count]` | OptionalArgument:count | `.turf.garrison` | YES | YES |
| buff | `/glw turf buff [powerup_id]` | OptionalArgument:id | `.turf.buff` | **PARTIAL** | YES |
| powerupnpc | `/glw turf powerupnpc <set\|remove>` | SubArgument (enum) | `.turf.powerupnpc` | YES | YES |

**Flags:**
- ⚠️ `/glw turf setowner`: No gang-name completion (only `gang` or `none` enum)
- ⚠️ `/glw turf select [id]` & `/glw turf tp [id]`: Requires numeric turf IDs, no name-based lookup
- ⚠️ `/glw turf buff [powerup_id]`: Powerup ID completion is incomplete — players must memorize or `/glw turf buff` to list
- ⚠️ Inconsistency: `/glw turf create` asks for `<displayName>`, but `/glw turf select [id]` uses numeric ID, not name

### Summary

- **Total Gang/Rank/Ally leaves:** 29 commands
- **Total Turf leaves:** 16 commands  
- **Grand total command leaves:** 45 commands
- **Commands without full tab completion:** 8 (18%)
- **Commands requiring numeric ID remembrance:** 5 (11%)

---

## Message Keys

### GANG_ Keys (74 constants)
Located in `gangland-api/src/main/java/org/luckyraven/gangland/file/configuration/Messages.java`

#### Create/Delete (6)
- GANG_CREATED
- GANG_CREATE_FEE
- GANG_CREATE_CONFIRM
- GANG_REMOVED
- GANG_REMOVE_CONFIRM
- GANG_RENAME

#### Economy (5)
- GANG_BALANCE
- GANG_MONEY_DEPOSIT
- GANG_MONEY_WITHDRAW
- GANG_INVITE_PLAYER
- GANG_INVITE_TARGET

#### Invites (15)
- GANG_INVITE_EXPIRES_ONLINE
- GANG_INVITE_NO_EXPIRY
- GANG_INVITE_ACCEPT
- GANG_INVITE_ALREADY_SENT
- GANG_INVITE_ACCEPT_MULTIPLE
- GANG_INVITE_CANCEL_MULTIPLE
- GANG_INVITE_NO_INVITE_FROM
- GANG_INVITE_PENDING_NONE
- GANG_INVITE_PENDING_HEADER
- GANG_INVITE_PENDING_ENTRY
- GANG_INVITE_CANCEL_NONE
- GANG_INVITE_CANCEL_SENDER
- GANG_INVITE_CANCEL_TARGET
- GANG_PLAYER_JOINED
- GANG_PLAYER_LEFT

#### Membership (13)
- GANG_LEAVE
- GANG_TRANSFER_OWNERSHIP
- GANG_TRANSFER_REQUEST
- GANG_TRANSFER_PLAYER_SUCCESS
- GANG_TRANSFER_TARGET_SUCCESS
- GANG_TRANSFER_NO_PARENT
- GANG_PROMOTE_PLAYER_SUCCESS
- GANG_PROMOTE_TARGET_SUCCESS
- GANG_PROMOTE_END
- GANG_DEMOTE_PLAYER_SUCCESS
- GANG_DEMOTE_TARGET_SUCCESS
- GANG_DEMOTE_END
- GANG_KICKED_TARGET

#### Alliances (15)
- GANG_ALLY_SEND_REQUEST
- GANG_ALLY_RECEIVE_REQUEST
- GANG_ALLY_ACCEPT
- GANG_ALLY_REJECT
- GANG_ALLY_ABANDON
- GANG_ALLY_ACCEPT_MULTIPLE
- GANG_ALLY_REJECT_MULTIPLE
- GANG_ALLY_NO_REQUEST_FROM
- GANG_ALLY_PENDING_NONE
- GANG_ALLY_PENDING_HEADER
- GANG_ALLY_PENDING_ENTRY
- GANG_ALLY_PENDING_CANCEL_NONE
- GANG_ALLY_PENDING_CANCEL_SENDER
- GANG_ALLY_PENDING_CANCEL_TARGET
- GANG_ALLIANCE_ALREADY_SENT

#### Customization (9)
- GANG_DISPLAY_SET
- GANG_DISPLAY_REMOVED
- GANG_COLOR_SET
- GANG_COLOR_RESET
- GANG_DESCRIPTION_CHANGE
- GANG_DESCRIPTION_NO_CHANGE
- GANG_MEMBERS_HEADER
- GANG_MEMBERS_ENTRY
- PLAYER_IN_GANG

#### Errors (6)
- GANG_CANNOT_ACT_SELF
- GANG_SAME_RANK_ACTION
- GANG_HIGHER_RANK_ACTION
- INVALID_GANG_NAME
- DUPLICATE_GANG_NAME
- GANG_DOESNT_EXIST

#### Information (2)
- KICKED_FROM_GANG
- MAIL_PENDING_INVITES / MAIL_PENDING_INVITES_ENTRY

### RANK_ Keys (19 constants)

#### Create/Delete (4)
- RANK_CREATED
- RANK_CREATE_CONFIRM
- RANK_REMOVED
- RANK_REMOVE_CONFIRM

#### Management (11)
- RANK_EXIST
- RANK_PERMISSION_ADD
- RANK_PERMISSION_REMOVE
- RANK_LIST_PRIMARY
- RANK_LIST_SECONDARY
- RANK_INFO_PRIMARY
- RANK_INFO_SECONDARY
- RANK_PARENT_ADD
- RANK_PARENT_REMOVE
- RANK_PERMISSION_EXISTS
- RANK_REMOVE_IN_USE

#### Vault Integration (4)
- RANK_VAULT_GROUP_SET
- RANK_VAULT_GROUP_CLEARED
- RANK_VAULT_GROUP_INVALID
- RANK_VAULT_GROUP_CURRENT
- RANK_VAULT_GROUP_NONE

### TURF_ Keys (TurfMessageContract)
Located in `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/config/GanglandTurfMessages.java`

Uses proxy routing to `Messages` enum for TURF-prefixed constants (lazy loading from settings.yml).

**Capture phase messages:**
- TURF_CAPTURE_START
- TURF_CAPTURE_TICK
- TURF_CAPTURE_PROGRESS
- TURF_CAPTURE_COMPLETE
- TURF_CAPTURE_FAIL
- TURF_CAPTURE_CONTESTED

**Status messages:**
- TURF_UNCLAIMED
- TURF_OWNED_BY (with gang-name replacement)
- TURF_INCOME_EARNED
- TURF_GARRISON_EMPTY

### Message Key Summary

- **GANG_ keys:** 74
- **RANK_ keys:** 19  
- **ALLY_ keys:** Merged into GANG_ALLY_* (15 distinct)
- **TURF_ keys:** ~12 (proxy via contract, not in Messages enum directly)
- **Total documented:** 120

---

## Placeholders (PlaceholderAPI Integration)

### Gang Placeholders (User-accessible via PlaceholderAPI)
**Prefix:** `gang_` and `user_` (gang-related subset)  
**Source:** `GangPlaceholderContribution.java`

#### Member Placeholders (user_ prefix)
- `user_has-gang` → true/false
- `user_gang-id` → numeric gang id
- `user_gang-join-date` → formatted date string
- `user_contribution` → formatted number (raw points)
- `user_contributed-amount` → formatted number (economy value)
- `user_has-rank` → true/false
- `user_rank` → rank name string

#### Gang Info Placeholders (gang_ prefix)
- `gang_id` → numeric gang id
- `gang_name` → gang name
- `gang_display-name` → custom display name or null
- `gang_state` → ACTIVE/DISBANDED (lowercase)
- `gang_color` → color code (RED, BLUE, GREEN, etc.)
- `gang_color-name` → color as readable string (red, blue, green)
- `gang_color-code` → Minecraft color code (§c, §9, §2, etc.)
- `gang_description` → gang description text
- `gang_created` → creation date string

#### Economy Placeholders
- `gang_balance` → formatted numeric balance
- `gang_bounty` → formatted numeric bounty amount
- `gang_has-bounty` → true/false

#### Membership Placeholders
- `gang_members-size` → total member count
- `gang_online-members-size` → current online count
- `gang_offline-members-size` → offline member count

#### Alliance Placeholders
- `gang_ally-list` → comma-separated ally gang names
- `gang_ally-size` → count of alliances

#### Level Placeholders
- `gang_level` → numeric level
- `gang_level-max` → max level
- `gang_level-next` → next milestone
- `gang_level-previous` → previous milestone
- `gang_experience` → formatted exp points
- `gang_experience-percentage` → formatted % to level
- `gang_experience-next-level` → exp needed for next level
- `gang_experience-previous-level` → exp of previous level
- `gang_experience-current-level` → exp of current level
- `gang_experience-level-<N>` → exp requirement for level N

### Turf Placeholders
**Not yet documented in code.** No PlaceholderContribution implementation found for turf system.

**Flags:**
- ⚠️ Turf placeholders missing entirely (players cannot reference turf stats in scoreboards/chat)
- ⚠️ No turf income, status, capture progress, or garrison level placeholders

---

## UI Components

### Gang Menus/Panels
**Source:** `GangColorCommand.java`, `GangOptionContribution.java`, `GangMenuItemSourceContribution.java`

#### Color Selection Menu (Inventory GUI)
- Opens on `/glw gang color`
- Displays 11+ color options as clickable items
- No player navigation UI (static layout)

#### Gang Info Panel / Status Screen
- No dedicated GUI; output via chat
- Shown via `/glw gang` (base command, no args)
- Displays: name, members, balance, alliances, state

**Flags:**
- ⚠️ Color menu is inventory-based but not documented in commands.json
- ⚠️ No visual turf ownership panel for gangs

### Turf UI Components

#### Turf Powerup Menu (NPC-triggered)
**Source:** `TurfPowerupMenuView.java`, `TurfPowerupBuffCatalogueView.java`, `TurfPowerupGarrisonView.java`, `TurfPowerupFlow.java`

- **Quartermaster NPC** triggers via right-click
- **Powerup Buff Catalog:** Lists active buffs; selectable to activate new ones
- **Garrison View:** Displays current garrison stock; adjustable with +/- buttons
- **Flow system:** Multi-step menu navigation (no back button documented)

#### Turf Selection Wand
- Held item triggers left/right click handlers
- Left-click = set corner 1 (pos1)
- Right-click = set corner 2 (pos2)
- No GUI overlay; visual feedback via chat messages only

**Flags:**
- ⚠️ Powerup catalog lacks pagination (assume single page only for now)
- ⚠️ No visual map grid showing turf boundaries to non-admins
- ⚠️ Wand selection lacks visual feedback (particles planned but unimplemented in base system)

---

## Boss Bars & Dynamic UI

### Turf Capture Boss Bars
**Source:** `TurfBossBarListener.java` (1-Hz refresh scheduler)

#### Contested Turf Bars (per-viewer)
**Shown to:**
- Any player physically inside the contested turf
- All members of the challenger gang (any location)
- All members of the defender gang (any location)

**Unclaimed Turf Capture (two bars):**
1. **Neutral "Unclaimed Territory"** bar (gray)
   - Fills 0→100% during CLAIM phase
   - Holds at 100% during CONSOLIDATE phase
2. **Gang Color Bar** (green for challengers, red for defenders, white for bystanders)
   - Stays at 0% during CLAIM phase
   - Fills 0→100% during CONSOLIDATE phase
   - Shows progress: `<GangName> is capturing <TurfName>`

**Owned Turf Capture (one bar):**
- Single consolidated bar showing overall capture progress
- Colored red for defenders, green for attackers

#### Audio Feedback
- Plays subtle tick sound on each progress increase (1-Hz ticks)
- Configurable: `Settings.isTurfCaptureSoundEnabled()`
- Sounds controlled via `GanglandTurfSounds` (start, tick, complete, failed)

### Action Bar Messages
**Source:** `TurfActionBarListener.java`

- Displays active capture status to nearby players
- Updates on capture state changes (start, tick, completion)
- No persistent placeholder integration documented

### Titles (NOT YET IMPLEMENTED)
- No title/subtitle system for capture events in current code

**Flags:**
- ⚠️ Titles missing (boss bars alone insufficient for immersion)
- ⚠️ Action bar tied to proximity (remote gang members see boss bar but no action bar)
- ⚠️ No hologram indicators over contested turfs

---

## Sounds & Audio

### Turf Capture Sounds (GanglandTurfSounds.java)
**Type:** SoundEffect (XSound-backed for cross-version compat)

#### Configurable Sounds
1. **Start Sound** → `playCaptureStart(Player)`
   - Setting: `Turf.Capture.Sound.Start.Name` / `.Volume` / `.Pitch`
   - Default: ENTITY_WITHER_HURT (TBD in settings.yml)

2. **Tick Sound** → `playCaptureTick(Player)`
   - Setting: `Turf.Capture.Sound.Tick.Name` / `.Volume` / `.Pitch`
   - Fires on every progress tick (1-Hz)

3. **Complete Sound** → `playCaptureComplete(Player)`
   - Setting: `Turf.Capture.Sound.Complete.Name` / `.Volume` / `.Pitch`
   - Fires when capture succeeds

4. **Failed Sound** → `playCaptureFailed(Player)`
   - Setting: `Turf.Capture.Sound.Failed.Name` / `.Volume` / `.Pitch`
   - Fires when capture is interrupted

5. **Unclaimed Sound** → `playOwnerCleared(Player)`
   - Setting: `Turf.Capture.Sound.Unclaimed.Name` / `.Volume` / `.Pitch`
   - Fires when ownership is cleared (admin command)

#### Sound Contract (TurfSoundContract)
- Injected into capture listeners
- Routes through `GanglandTurfSounds` bridge
- Decoupled so custom sound implementations are pluggable

**Flags:**
- ⚠️ No gang-specific sound themes (same sounds for all gangs)
- ⚠️ Tick sound may be too frequent (1 per second, potential spam)

---

## Consistency & Usability Flags

### ID-Based Lookups (Breaking Discoverability)
1. **`/glw gang ally abandon <id>`** — Forces player to run `/glw gang ally list` to get numeric gang ID
2. **`/glw turf select [id]` / `/glw turf tp [id]`** — Requires `/glw turf list` first to learn IDs
3. **`/glw turf buff [powerup_id]`** — Powerup IDs must be memorized or discovered via `/glw turf buff` with no arg

**Recommendation:** Add name-based aliases or tab-complete gang/turf names where IDs are currently required.

### Inconsistent Sub-Command Patterns
| System | Add | Remove | List | Info |
|--------|-----|--------|------|------|
| Rank | `rank create` | `rank delete` | `rank list` | `rank info` |
| Rank Permission | `rank permission add` | `rank permission remove` | ❌ NO LIST | ❌ NO INFO |
| Rank Parent | `rank parent add` | `rank parent remove` | ❌ NO LIST | ❌ NO INFO |
| Gang Ally | `gang ally request` (mail) | `gang ally abandon` | ❌ NO LIST | ❌ NO INFO |

**Recommendation:** All CRUD hierarchies should support list/info at the same nesting level.

### Missing Help Text in Commands.json
All commands have `"description"` fields; none have `"aliases"` or extended help sections.

**Recommendation:** Add `"detailed_help"` / `"examples"` sections for complex commands like `/glw turf create` (requires wand selection first).

### Argument Naming Clarity
- **`/glw gang desc`** — Unclear if this *shows* or *edits* description (no argument suggests edit, but should be explicit)
- **`/glw turf show`** — Ambiguous (show *what*? Boundaries? Owner? Status?)
  - Code shows it spawns particles; should be `/glw turf show-boundaries` or `/glw turf outline`

**Recommendation:** Rename ambiguous commands or add clearer sub-arguments.

### Missing Pagination for Lists
- **`/glw gang members`** — No page argument; shows all (could be 100+ members)
- **`/glw turf list`** — No page argument; shows all turfs
- **`/glw rank list`** — No page argument; shows all ranks

**Recommendation:** Add optional `[page]` argument to all list commands for servers with many entries.

### Incomplete Tab Completion
- **Rank permission add/remove:** Suggests rank names but NOT permission nodes
- **Turf setowner:** Suggests only "gang" or "none" (not actual gang names)
- **Turf buff:** Partial completion (shows `[powerup_id]` in help but no suggestions)

**Recommendation:** Implement full tab-completion suggestions for all enumerable values.

### Turf Placeholders Absent
- **Gap:** No integration with PlaceholderAPI for turf stats (income, status, challenger, state)
- **Impact:** Scoreboards cannot display turf progress to watching players

**Recommendation:** Implement `TurfPlaceholderContribution` mirroring `GangPlaceholderContribution`.

---

## Inventory-Based Navigation (Hidden from Commands.json)
- **Gang Color Menu:** Triggered by `/glw gang color`, but the GUI is undocumented
- **Turf Powerup NPC Menu:** Right-click driven, not command-accessible

**Recommendation:** Document all inventory-based UI flows in a separate `ui-flows.md` to parallel commands.json.

---

## Summary of Findings

| Category | Count | Issues |
|----------|-------|--------|
| Command Leaves | 45 | 8 missing full tab-complete |
| Message Keys | 120+ | Well-organized, no gaps detected |
| Placeholders | 30 (gang only) | **Missing turf placeholders entirely** |
| Boss Bars | 2 (per-turf) | ✓ Well-implemented |
| Action Bars | 1 (turf-linked) | ✓ Functional |
| Titles | 0 | **Missing** |
| Sounds | 5 types | ✓ Configurable |
| Menus | 2 (gang color, turf NPC) | Undocumented in commands.json |

---

## Recommended Priority Fixes

### Phase 1 — Usability (UX)
1. **Turf placeholders:** Add `TurfPlaceholderContribution` for scoreboard display
2. **Gang name completion:** Replace `/glw gang ally abandon <id>` with name-based lookup
3. **Turf name lookup:** Add `/glw turf select-by-name <name>` or tab-complete turf names in existing select

### Phase 2 — Clarity (UX)
4. **Rename ambiguous commands:** `/glw turf show` → `/glw turf outline` or `/glw turf show-bounds`
5. **Clarify gang desc:** `/glw gang desc` → `/glw gang description set` / `/glw gang description view`
6. **Add pagination:** `[page]` argument to `/glw gang members`, `/glw turf list`, `/glw rank list`

### Phase 3 — Discoverability (UX)
7. **Complete tab-completion:** Permission node suggestions for `rank permission add/remove`
8. **Gang name completion:** `/glw turf setowner` should suggest gang names
9. **Powerup completion:** `/glw turf buff` should list available powerup IDs with descriptions
10. **Document GUI flows:** Create parallel UI guide for gang color menu, turf NPC, powerup selections

### Phase 4 — Immersion (Design)
11. **Add titles:** Show "Turf Contested!" / "Capture Complete!" messages as Minecraft titles
12. **Turf map grid:** Add `/glw turf map` to show chunk grid with gang ownership (like Factions `/f map`)
13. **Gang status dashboard:** Unified panel via `/glw gang info` (currently outputs to chat only)
14. **Turf hologram indicators:** Place floating text above contested turfs showing challenger/progress

---

## Links to Source Files

- **Commands:** 
  - Gang: `gangland-features/gangland-gang/src/main/resources/commands.json`
  - Turf: `gangland-features/gangland-turf/src/main/resources/commands.json`
  - Core: `gangland-impl/src/main/resources/commands.json`
  
- **Messages:**
  - Main enum: `gangland-api/src/main/java/org/luckyraven/gangland/file/configuration/Messages.java` (155 GANG_/RANK_/ALLY_/TURF_ keys)
  - Turf bridge: `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/config/GanglandTurfMessages.java`
  
- **Placeholders:**
  - Gang: `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/gang/placeholder/GangPlaceholderContribution.java` (30 placeholders)
  
- **Boss Bars:**
  - Listener: `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/listener/TurfBossBarListener.java`
  - Presence: `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/listener/TurfPresenceBarListener.java`
  
- **Action Bars:**
  - Listener: `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/listener/TurfActionBarListener.java`
  
- **Sounds:**
  - Sound config: `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/config/GanglandTurfSounds.java` (5 sound triggers)
  
- **UI Components:**
  - Gang color: `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/command/sub/gang/GangColorCommand.java`
  - Turf powerup: `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/npc/view/TurfPowerupMenuView.java`

