# Gang and turf roadmap - lens: player-first clarity and feel

Planner: `player-first`, wave `gang-turf-wave-2026-09-30`, written 2026-09-30. Planning only, no product code.
Inputs: every file in `../census/` (gang, turf, cnc-ideas, map-feasibility, docket-gr-verified, docket-tf-verified,
ux-surface, seams, docket-gang-turf). Graph checked fresh (`graphify-out/graph.json` 2026-09-29 23:08 is newer than the
last commit, 23:05); oriented with `graphify explain TurfLocationTracker`, `graphify explain TurfCommand`,
`graphify query GangCommand|SquadRadio|RadioVoice|GangMembership`, `graphify explain WaypointGangIdCommand`, then raw
reads of `CaptureService.isCapturable` (`capture/CaptureService.java:112-127`), `RadioVoice`, `GangMembership`,
`GanglandApi.VERSION` (`"2.0"`), `inventory/phone_gang_search.yml`, `turf module.yml`, `CommandManager` (help layer).

Where `seams.md` disagrees with `turf.md`, `gang.md` or a `module.yml`, this plan follows the descriptor and the two
deeper censuses. In particular: turf **never loads** without gang (`gangland-turf/.../module.yml` `Depends: [civilians, gang]`),
so seams.md's "turf runs silently without gang" row is wrong, and turf reads gangs through `GangLookupContract`
(gang module), not through a `TurfManager.get(UUID)` or a turf-side `GangAllianceRepositoryContract`.

Path shorthand: `GT/` = `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/`,
`GG/` = `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/`,
`GM/` = `gangland-features/gangland-mail/src/main/java/org/luckyraven/gangland/mail/`,
`GC/` = `gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians/`.
Classes marked **NEW** do not exist yet.

---

## 0. Dependencies and target revision

| Item | Value |
|---|---|
| Target revision | **Gangland 0.14.0** (next free after H13) |
| Hard dependency | H13 merged to master first: Gangland `npc-roles-medics` (0.13.0, head `f9295ec0`) plus `h13-roles`, `h13-stuck`, `h13-names`, `h13-drop`; Keystone `phase-h13-npc-roles` **1.14.0** (worktree `wt/keystone-1.14.0`). |
| Pin trap found | `h13-roles` and `h13-stuck` pin `<keystone.version>1.14.0`, while `npc-roles-medics`, `h13-names`, `h13-drop` still pin 1.13.0. The merged 0.13.0 must land at 1.14.0 before this wave branches, or W3 (`millisUnreachable`, `NpcFanPlacement`) does not compile. |
| Integration branch / worktree | branch `gang-turf-map` at `E:/Programming/java/wt/gangland-0.14.0`; lane worktrees `E:/Programming/java/wt/gl14-<lane>` (same naming as `gl13-*`). |
| API | `GanglandApi.VERSION` **2.0 -> 2.1** for exactly one additive member: `RadioVoice.audience(NpcSquad)` (default method, section 3). The turf `module.yml` then declares `Host_Api: 2.1`; every other module stays `2.0`. **Nothing else in `gangland-api` changes in W0-W3.** |
| Keystone | No Keystone change is required for W0-W3. Upstream lane K is conditional (section 5.6). |

---

## 1. Player-facing design

### 1.1 The core loop, in one sentence each

1. **Find a crew** - browse gangs, join an open one or start your own.
2. **Take a turf** - stand in it with your crew until the bar fills.
3. **Get paid** - each turf pays the gang bank on a timer; the gang levels up from turf fighting.
4. **Spend to hold it** - buy guards and boosts from the turf's Quartermaster.
5. **Fight for it** - rivals attack while you are around; guards and gang-mates defend; the radio tells you where.

Everything below exists to make one of those five steps visible, or it is cut.

### 1.2 Vocabulary (one word per idea, used in every message, menu, hover and doc)

| Say | Never say | Meaning |
|---|---|---|
| Turf | territory, region, zone, claim | an admin-drawn named area (`Turf`, `CuboidRegion`) |
| Capture / capturing | contest, claim phase, consolidate | filling the bar to take a turf |
| Under attack | contested | a rival is capturing your turf |
| Shielded | protected, post-logoff grace | cannot be attacked right now (with the reason) |
| Safe for 12m | cooldown | just changed hands |
| Guards | garrison, defenders, turf_defender | the NPCs bought for a turf |
| Quartermaster | powerup NPC | the turf's shop NPC |
| Bank | vault, economy | `Gang.economy` |
| Share | contribution | a member's money share of the bank (paid out on disband) |
| Gang XP / level | - | progression, fed by turf fighting (decision D3) |
| Leader / Officer / Member | tail / head / rank node | the default ranks (decision D4) |

Implementation rule: new strings go in module YAML (`turf/turf_messages.yml` NEW, `turf/turf_map.yml` NEW, gang-owned
`gang/gang_messages.yml` NEW), never in `Messages`/`Settings` (CLAUDE.md contract rule). Existing `GANG_*`/`TURF_*`
constants are reworded in place where the glossary demands it (text only, no key rename).

### 1.3 One feedback channel per job

Today the same fact reaches players through up to five channels (enter title, action bar, presence boss bar, contest
boss bars, chat notifier). The rule from W1 on:

| Channel | Job (only this) | Source |
|---|---|---|
| Title/subtitle | a moment: entering a turf, winning or losing one | `GT/listener/TurfActionBarListener` (enter), capture events |
| Action bar (background priority) | where am I: `Docks - Rats turf - attackable` or `Nearest turf: Docks 120m NE` | NEW line on the 1 Hz `TurfLocationTracker` tick via Keystone `ActionBarManager.sendBackground` |
| Boss bar | a capture in progress, with numbers: `Capturing Docks 34% - 3 vs 1 - 1m50s` | `GT/listener/TurfBossBarListener` (reworded) |
| Chat | events you must act on + on-demand info (map, info card, digest) | `TurfRadio` NEW (section 3), map, info card |
| Sidebar / menus | standing state (my turfs, income, level) | `TurfPlaceholderContribution` NEW + existing `GangPlaceholderContribution` |

Consequence (decision D9): `GT/listener/TurfPresenceBarListener` (persistent "Territory of X" boss bar) is retired; its
information moves to the action-bar line, so the boss bar only ever means "fight on".

### 1.4 The first 10 minutes (the script W1-W2 must make true)

| Minute | Player does | Player sees | Built in |
|---|---|---|---|
| 0:00 | joins, gangless | one chat line, once per account: `You're not in a gang. /glw gang to find one or start your own.` | R2.2 (`MemberJoinListener` on `UserDataInitEvent`) |
| 0:30 | walks into a turf | title `Docks` / subtitle `Rats turf - attackable`; action-bar line stays while inside | exists + M1.3 |
| 1:00 | `/glw gang` | the gang browser (existing `phone_gang_search.yml`, item source `gangs`), each head lore: members online, turfs held, `Open - click to join` or `Invite only`; a `Start a gang ($100,000)` button that explains what a gang gets you before the confirm | R2.2 |
| 2:00 | clicks an open gang | `gang join` runs; welcome line lists three verbs: `/glw map`, `/glw gang` (hub), `/glw gang chat` | R2.2, R2.6 |
| 3:00 | `/glw map` | the chat grid (section 2): own turfs green, enemies red, shielded enemies dark red, unclaimed gray `-`; hover any cell = name, owner, pays, attackable-or-why-not; click = info card | M1.1, M1.2 |
| 4:00 | clicks an unclaimed cell | info card: `Docks - unclaimed - pays $100 every 10m. To capture: stand inside with your gang, about 3 minutes. Rivals inside push the bar back.` | M1.2 |
| 5:00 | stands in it with a mate | boss bar with numbers; tick sound; gang-mates anywhere hear the radio line `Vipers are taking Docks (2 of us)` | exists + R2.4 |
| 8:00 | bar fills | title `Docks is yours`; chat `+$100 every 10m to the gang bank`; map glyph turns green; gang log entry | R2.4, R2.5 |
| 10:00 | right-clicks the Quartermaster | panel shows guards in stock, what a guard costs, what boosts do - all readable from lore | S3.3 |

### 1.5 Mechanics, how each is learned, and verdicts

| Mechanic | Today (census) | Player-first change | How the player learns it in-game |
|---|---|---|---|
| Gang discovery | only via phone menu; `/glw gang` prints help to gangless players; `list` means "my members" | `/glw gang` for a gangless player opens the browser; `gang list` becomes the browser too; `gang info <name>` public card (NEW `GangInfoCommand`) | first-join tip; browser is the default screen |
| Create | fee charged, no preview; no name rules (GR-29) | NEW `GangNameRules` (3-16 chars, `[A-Za-z0-9_]`, colour codes stripped, case-insensitive uniqueness always on); confirm screen says what you get | create confirm line |
| Join | invite only; `Gang.State` persisted but unwired; no decline (GR-24); offline invites never expire (GR-23) | `Gang.State` becomes **Recruiting: Open / Invite only** (CLOSE folded into INVITE). NEW `GangJoinCommand` for Open gangs; NEW `GangInviteDeclineCommand`; offline invites expire after `Invite_Offline_Expiry_Days` (7) | browser lore, invite line has clickable `[Accept] [Decline]` |
| Ranks | one global admin tree, seeded `member -> owner` with **no** permissions, so a member cannot deposit and `promote` dead-ends | seed **Member -> Officer -> Leader** with default nodes on an **empty** rank table only (D4); register the `gangland.gang.*` nodes | hub "Members" page: each rank's lore lists `Can: deposit, invite ...`; a denied action says which rank can do it |
| Bank and share | deposit adds `amount/1000` to `Member.contribution`; turf also adds points to the same field (dual unit) | share = money only; turf points go to gang XP (D3) | hub bank item shows balance and your share %; gang log records deposits/withdrawals |
| Gang level | persisted, displayed, never fed (GR-19) | fed by turf fighting; `GangLevelUpEvent` finally fires; level raises nothing but pride unless D2 picks the level cap | hub shows level bar; level-up is a gang-wide title |
| Gang bounty | persisted, displayed, no producer (GR-19) | **cut**: delete `GangBountyEvent` + `GangBountyMessageListener`; `%gang_bounty%` keeps returning 0 so user configs do not break | - |
| Allies | numeric ids in `ally abandon`; `ally info` button runs a missing command (GR-16) | names everywhere; `alliance_stat.yml` click runs `gang info <name>`; allies still cannot hurt each other and still count as defenders | ally menu |
| Gang chat | missing | NEW `GangChatCommand`: `/glw gang chat <msg>` one line, `/glw gang chat` toggles the channel | welcome line |
| Offline news | none (census headline 3) | NEW gang log + login digest (D5): `Since you left: Docks lost to Rats 02:10 - +$2,400 turf income - Ryan joined` | automatic on login |
| Turf discovery | stumble in, or `/glw turf list` (every turf, raw bounds, tp link) | the map; `turf list` paged, sorted by distance, with direction, click = info card (tp only for admins) | `/glw map` in the welcome line |
| Capture eligibility | explained only on entry by `TurfCaptureFeedbackListener`, and the rule is "owner offline 10+ min" | one pure verdict (NEW `CaptureEligibility`) feeds entry message, info card, map hover, action bar; rule per D1 | every surface says the same reason |
| Capturing | unclaimed = two bars and two phases (global claim, then per-gang consolidate); owned = one bar | one bar for every capture (D8); bar text carries counts and time left | the bar itself |
| Income | silent; admin flat number; uncapped multipliers (TF-37) | one chat line per gang per payout (`Turf income +$300 (3 turfs)`), multiplier cap, log line | chat + hub + sidebar token |
| Guards | bought one at a time; all spawn at one point at attack start; do not count toward defence; chase forever | guards **count as defenders while alive inside** (D7); deploy in waves from posts; leash to the turf; walk home after | Quartermaster lore + boss bar `3 vs 1 +2 guards` |
| Boosts | 4 sold, 2 do nothing (TF-02) | `garrison_discount` wired; `reinforced_defense` deleted (guards counting replaces it) | Quartermaster lore |
| Turf limit | none; one member can hold unlimited turfs | cap per D2, shown as `Turfs 3/4` in hub, map legend, info card | hitting the cap says so before capture starts |

### 1.6 Cuts and merges (explicit list)

| # | Cut / merge | Why |
|---|---|---|
| C1 | `GangBountyEvent`, `GangBountyMessageListener` deleted | never constructed, no design for it (GR-19 half) |
| C2 | `reinforced_defense` powerup deleted; `CAPTURE_DEFENSE_BONUS` effect type deleted | invisible "phantom defenders"; guards counting as defenders is the visible version (D7) |
| C3 | `Gang.State.CLOSE` folded into `INVITE` | two states are enough: Open / Invite only |
| C4 | `TurfPresenceBarListener` retired (D9) | boss bar reserved for fights; presence moves to the action bar |
| C5 | Unclaimed two-phase capture folded into the owned-turf tug-of-war (D8); `dominantGang(..., exclude)` dead code removed (TF-11) | two stacked bars with different rules is the most confusing screen in the module |
| C6 | Three confirm idioms -> one (`<verb>` then `confirm` within 60 s): `GangLeaveCommand`'s type-twice idiom changes | consistency |
| C7 | `gang_stat.yml` (empty, GR-17) becomes the "Gang stats" page (level, turfs, income tokens) instead of being deleted | the hub button already points there |
| C8 | `gang list` alias stops meaning "members"; `gang members` stays | `list` should list gangs |
| C9 | `TurfCaptureNotifier` chat lines merged into `TurfRadio` lines (section 3) | one voice for "your turf is under attack" |
| C10 | Admin turf subcommands and the `rank` tree hidden from player help (they stay usable with permission) | shrink the visible surface without deleting verbs |

Command surface a player sees after W2 (help, per permission):

- gangless: `gang` (browser), `gang create <name>`, `gang join <gang>`, `gang accept|decline [gang]`, `gang info <gang>`, `map`, `turf`, `turf list`, `turf info [turf]`
- member adds: `gang` (hub), `gang chat [msg]`, `gang members`, `gang deposit|withdraw <amount>`, `gang leave`; officers add `gang invite <player>`, `gang kick <player>`, `gang ally ...`; leaders add `promote|demote|transfer|delete`; rename/description/display/colour/recruiting live in the hub's settings page (their commands stay for power users).

Hiding mechanism: `gangland-impl/.../command/CommandManager.java` already installs Keystone visibility filters
(`DevCommandVisibilityFilter`); M1.4 first verifies whether Keystone's help already skips sub-arguments the sender lacks
permission for. If yes, TF-30's single `gangland.turf.admin` namespace hides them for free; if not, one NEW
`PermissionVisibilityFilter` beside the dev filter (impl only, no Keystone change).

---

## 2. The map

### 2.1 Decisions (not options)

| Question | Decision |
|---|---|
| Model | Keep admin-drawn cuboid turfs; the map **rasterizes** them. Player-claimable chunks are out of scope (section 7). |
| Command | **`/glw map`** - a second `@CommandHandler` root inside the turf module (NEW `MapCommand`, beside `GT/command/TurfCommand`); no collision (grep of every `commands.json` for `map` is empty). Entry in `gangland-features/gangland-turf/src/main/resources/commands.json`. No `/glw turf map` alias. |
| Sub-arguments | `/glw map` (print once) - `/glw map on|off` (auto map) - `/glw map zoom in|out` - `/glw map hud on|off` (the action-bar line). Chained `OptionalArgument`s with tab completion (house rule). W4 adds `map item`, `map border`. |
| Orientation | fixed north (top = -Z), Factions style |
| Size | 41 x 7 cells, so header (1) + grid (7) + legend (2) = 10 lines = the unfocused chat height. `Width`/`Height` configurable, odd. |
| Cell | `Cell_Blocks: 16` (a chunk). Zoom cycles 8 / 16 / 32 / 64. |
| Glyph = identity | each gang in the window gets a letter (first letter of its resolved name; collision -> next unused letter; allocated by ascending gang id so it is stable) |
| Colour = relation | own gang green, ally aqua, enemy **attackable now** red, enemy **shielded** dark red, unclaimed gray `-`, no turf dark gray `.` |
| Overlays | `!` gold = under attack (overrides glyph), `+` = you (white, bold), **lowercase** = the turf covers less than 75 % of the cell |
| Hover | turf name, owner, pays, state and the `CaptureEligibility` reason (`attackable` / `shielded: no Rats online` / `safe for 12m`), guards in stock (owners/allies only), cell coordinates |
| Click | `RUN_COMMAND /glw turf info <id>` (read-only). Admins (`WandSelectionManager.ADMIN_PERMISSION`) see a `tp` hint in the hover; no tp click for players. |
| Legend | carries every fact the hover carries in short form, so the proportional font, Bedrock/Geyser (no hover) and colour-blind players lose nothing |
| Auto map | default **off**; re-sent only when the viewer's **cell** changes or the window's raster hash changes, at most every `Auto_Min_Interval_Seconds` (2); driven by the existing 1 Hz `GT/task/TurfLocationTracker` tick, **never** a `PlayerMoveEvent` (its javadoc forbids it) |
| HUD line | default **on** for gang members (it is the always-on lightweight map); `map hud off` per player |
| Chunk snapping | owner decision D6 (NEW `CuboidRegion.snappedToChunks()` behind `Turf.Snap_To_Chunks`, applied at `TurfCreateCommand` and `TurfShowCommand.renderSelection`) |

### 2.2 Sample frame (viewer in Vipers; Kings allied; Rats enemy with members online; Saints enemy, all offline)

```
Map (1240, -310) facing NE  zoom 16
..RRRRRRRR.-------.......................
..RRRRRRRR.-------......KKKKKKKKK........
..RRRRRRRR..............!!!!!!!!!.SSSSSS.
..............vVVVVV+VV.!!!!!!!!!.SSSSSS.
..............vVVVVVVVV...........SSSSSS.
..............vVVVVVVVV..-------..SSSSSS.
.........................-------.........
V Vipers(you) 3/4  K Kings(ally)  R Rats
S Saints(shield)  -free  .none  !attack  +you  a=edge
```

Chat is a proportional font about 50 letters wide: the header and legend are kept under that width and the legend
allocator wraps entries onto a second/third line instead of letting the client wrap mid-entry (a golden test pins it).

Colours (not visible in plain text): `V` green, `K` aqua, `R` red, `S` dark red, `-` gray, `.` dark gray, `!` gold,
`+` white bold. The `!` block is a second Kings turf (south of the first) being captured (hover: `Kings - Harbour - under attack by Rats, 62 %`);
the lowercase `v` column is a turf edge that is not chunk-aligned. `3/4` is the turf cap from D2.

### 2.3 NEW classes (all in the turf module, package `org.luckyraven.gangland.turf.map`)

| Class | Role | Notes |
|---|---|---|
| `TurfChunkIndex` NEW | `world -> chunkKey -> List<Turf>`; rebuilt on create/delete/owner change | earns its place for auto map + HUD; `findAt` may use it later (not in scope) |
| `TerritoryRaster` NEW | pure, Bukkit-free: window + cell size + turf rectangles + viewer relation -> cells (turf id, coverage, state, relation) | unit-tested without a server; Keystone-promotion candidate **only** when a second consumer exists |
| `TurfMapRenderer` NEW | cells -> Bungee components with run-length merge of equal neighbours; legend allocator | the painter half is the promotion candidate; the classifier stays turf-specific |
| `MapCommand` NEW | `/glw map` root + `on|off|zoom|hud` | player-only |
| `MapPrefsService` NEW | per-player auto/zoom/hud prefs in memory, cleared on quit | not persisted (YAGNI) |
| `TurfPlaceholderContribution` NEW | `%gangland_turf_name|owner|state|progress|relation|income|nearest_name|nearest_distance|nearest_direction|owned_count|cap%` | reads `TurfLocationTracker.getPlayerTurfCache()`; O(1); Plaque sidebar needs no change |
| `TurfMapSettings` NEW | reads `turf/turf_map.yml` | block-style YAML, `Capitalized_Underscore_Separated` keys |

Raster cache: a per-world version counter bumped on `TurfOwnerChangedEvent` (after W0 it fires on **every** owner change),
`TurfCapturedEvent`, `TurfCaptureStartEvent`, `TurfCaptureFailedEvent`, create/delete. This is why the ownership pipeline
(W0 G0.1) is a hard prerequisite of the map.

### 2.4 Expansion ladder

| Layer | Surface | Wave | Why this order |
|---|---|---|---|
| 1 | chat grid + hover/click + auto | W1 | the ask; everything compiles at the 1.16.5 floor (`TurfListCommand.sendRow` already uses the same component API) |
| 2 | action-bar HUD line, placeholders (sidebar), zoom | W1 | highest value per line of code; always-on without chat spam |
| 3 | dynmap area markers (soft dep, `turf.map.web.DynmapTurfLayer` NEW), then BlueMap in its own small jar | W4 | only servers with a web map benefit; BlueMap Java floor unverified |
| 3 | player border `/glw map border` (clipped, adaptive-step `TurfVisualization`) | W4 | needs the particle-cost fix first (triage) |
| 4 | `/glw map item` (contextual `MapRenderer`, one shared `MapView`, same raster) | W4 | medium effort, modest gain over the HUD |
| 4 | turf-centre hologram banner (`keystone-hologram`) | W4, maybe never | the Quartermaster nameplate may already do the job |
| 4 | `TurfMapLayer` seam so cops-n-crooks can overlay jails / manhunts | W4 | additive; only after layer 1 proves the grid |

Out: squaremap and Pl3xMap (Paper-only), boss-bar compass (the boss bar is reserved for fights).

---

## 3. What transfers from cops-n-crooks H11-H13

Turf guards and the Quartermaster are `CivilianNpc`s and already inherit pursuit, route planning, formation arc,
strafing, retreat to cover, shouts and recruitment (cnc-ideas section 1). The transfer plan only fills gaps players can
see. IDs are the cnc-ideas catalogue.

| # | Mechanic | Player-visible result | How, exactly | Wave / lane | Verdict |
|---|---|---|---|---|---|
| T1 | Turf alert radio to the owning gang anywhere | `Docks is under attack! 3 Rats from the north-east` | api: add default method `RadioVoice.audience(NpcSquad)` returning `@Nullable Collection<Player>` (null = today's range rule) in `gangland-api/.../npc/radio/RadioVoice.java`; `SquadRadio.speak` honours it; extend `SquadRadioTest`. Turf: NEW `TurfRadio` wraps `SquadRadio` with a voice whose audience = online owner-gang members and whose `extras()` supply `%turf% %gang% %attackers% %defenders% %progress%`; a member-less `NpcSquad` per turf is the throttle key. Lines `Contest_Start`, `Milestone_50`, `Guard_Down`, `Turf_Lost`, `Turf_Held` from `TurfCaptureStartEvent`/`ProgressEvent`/`CapturedEvent`/`CaptureFailedEvent`. Direction words from `RadioSides.compass8`. | W2 R2.4 | **yes** |
| T2 | Garrison as a per-turf squad with posts and a leash | guards hold the turf, act as a team, stop chasing across the map | civilians first (S3.1): `CivilianService.alertFaction` must route through `victim.getFactionSquads()` when one is set (today it joins the shared `(faction, attacker)` squad on the first hit). Turf (S3.2): NEW `TurfGarrisonSquad` owns one `NpcSquad` per turf and is handed to each guard via the public `CivilianNpc.setFactionSquads`; NEW `TurfPosts` derives 3-6 posts (corners + centre surface); leash in `TurfDefenderDeployer.tick` = clear target + `navigateTo(post)` beyond region + `Leash_Margin` (16). Index guards by entity UUID (TF-35). | W3 S3.1, S3.2 | **yes** |
| T13 | Stand down and walk home | fights end with a radio line, not NPCs vanishing | `GarrisonDeployListener.onCaptured/onFailed` -> `Turf_Held`/`Turf_Lost` line, survivors walk to a post, despawn after `Stand_Down_Seconds` | W3 S3.2 | **yes** |
| T3 | Stock is a reserve, deployed in waves | fights have a shape; buying guards is not burned in one go | `Max_Alive` per turf; first wave at attack start, next wave on `MAN_DOWN` or a milestone after `Wave_Cooldown_Seconds`; spawn at posts out of attackers' line of sight (`hasLineOfSight` on a handful of candidates); undeployed stock refunded on end; nothing consumed when `NpcSupport.available()` is false (TF-20). Wave knobs in `turf/turf_npcs.yml`, turf-local (no api `WaveSettings` promotion until a second consumer). | W3 S3.3 | **yes** |
| T6 | Gang-tagged callsigns | `[Vipers] Guard Bob #1042` over the head and in shouts | set after spawn in `TurfDefenderDeployer.deploy`; format in `turf/turf_npcs.yml` (`Names.Format`); the civilians `FactionVoice.callsign` and `civilian_messages.yml` `Shouts.Format` read `%gang%` instead of `[%faction%]` (so attackers no longer see `[turf_defender]`). Keep turf-local; no `NpcNames` api promotion. Never emit `CIT-` (H13 rule). | W3 S3.4 | **yes** |
| T7 | Health bars on guards and Quartermaster | see how close a guard is to dropping | zero code: `gangland-healthbars` (H13) accepts transient unprotected Citizens NPCs; verify line layout with T6's callsign | W3 S3.4 | **yes (verify only)** |
| T9 | Guard bounty drop | killing guards pays; owners who under-defend fund attackers | NEW `TurfDefenderMoneyDropSource` (module source for the H13 `MoneyDropClassifier`), pays `Bounty_Fraction` (0.5) of the guard price, funded from what the owner paid (no minting); owner and allied kills excluded | W3 S3.5 | **yes** |
| T12 | Stuck recycling | no guard frozen on a roof | `AbstractNpc.millisUnreachable()` (Keystone 1.14.0) > `Recycle_Seconds` and out of attackers' view -> respawn at another post | W3 S3.5 | **yes**, after the cop version is accepted live |
| T11 | Player comms: man-down and ping | `Ryan is down at Docks, 40m north` | uses the T1 audience seam with a member-less squad per gang; only man-down in W2; `/glw gang ping` deferred | W2 R2.6 (man-down only) | **partial** |
| T4 | Roles (shield Guard, Marksman, Medic, Commander = Quartermaster) | a counterable garrison | needs `CopRole` promoted to a generic `NpcRole` in `gangland-api` (api 2.2) + a civilians `AI.Combat.Roles` overlay; Medic field care (`GL-CARE`) is not built even for cops | W4 | **deferred** (ship without Medic when it comes) |
| T10 | Commander-down morale | killing the Quartermaster breaks the garrison briefly | `LEADER_DOWN` + `takeCover` with duration; only meaningful with T4 leader priority | W4 | **deferred** |
| T8 | Neighbour and allied responders | contiguous land pays off | adjacency from `CuboidRegion` bounding boxes; `Responder_Max` 1 | W4 | **deferred** (cascade risk; wants the map live first) |
| T5 | Turf heat feeds wanted level; cops join big fights | risk for war near town | NEW `TurfHeat` in **cops-n-crooks** (legal edge `Depends: [turf, civilians]`; turf never depends on cops) | W4, behind `Turf.Heat.Enabled: false` | **decision D10** |

Not transferred (cnc-ideas section 4 holds): cuff/detain escalation, wanted-decay freeze, suppressive fire,
radio-operator silence, named-NPC progression.

Precondition for W3: **acceptance of the H12 faction-alert scenario**, which H12 never verified outside unit tests
(`migration-0.12.0.md` section 9, "GL-4"). Lane G0.6 runs it on the test server via the console harness
`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`, owner-assisted for the one step that needs a real player.

---

## 4. Docket integration

All 25 open GR and all 38 open TF entries are placed. "Pin" = a unit test that asserts today's wrong behaviour and
must be flipped red-first in that lane (CLAUDE.md).

### 4.1 GR (gangs, ranks, mail)

| Id | Lane | Pin to flip | Note |
|---|---|---|---|
| GR-10 | G0.4 | - | transfer confirm lock gets the 60 s timer; clear `pendingTargets` on quit |
| GR-11 | G0.4 | - | mail expiry sweep to a sync timer, cancelled on disable (prereq: new invite rules) |
| GR-12 | G0.3 | `RankManagerTest` | `RankManager.clear` clears everything |
| GR-13 | R2.3 | `RankManagerTest` | seeding path rewritten with the preset |
| GR-14 | G0.3 | - | mail ally request/accept reuse `onlineGangMembers` |
| GR-15 | G0.3 | - | null-safe `GangMembersDamageListener` |
| GR-16 | R2.2 | - | `alliance_stat.yml` click -> `gang info <name>` |
| GR-17 | R2.2 | - | `gang_stat.yml` becomes the stats page (C7) |
| GR-19 | R2.5 | - | level event wired (D3), bounty event deleted (C1) |
| GR-20 | G0.3 | - | SQL NULL stays null |
| GR-21 | R2.3 | - | seed ids via max+1 |
| GR-22 | R2.3 | - | rename the `RankParent` field semantics (child id), no schema change |
| GR-23 | G0.4 | - | accept expires the recipient's other invites; offline invite expiry |
| GR-24 | G0.4 | - | NEW `GangInviteDeclineCommand` + `commands.json` |
| GR-27 | G0.3 | - | empty `Display_Name_Char` falls back |
| GR-28 | G0.3 | - | root null guard |
| GR-29 | G0.4 | - | NEW `GangNameRules` (create + rename) |
| GR-30 | G0.3 | - | null guard |
| GR-31 | G0.4 | - | one bad mail row skipped and logged, not the whole load |
| GR-32 | G0.3 | - | delete dead `MemberManager.initializeMemberData`; `Number` casts in `MemberRepository.doLoadAll` |
| GR-33 | G0.3 | - | delete the unused no-arg `Gang()` |
| GR-34 | G0.3 | - | colour confirm re-checks gang + permission |
| GR-35 | G0.3 | `GangAllianceTest` | `Gang.hashCode` |
| GR-36 | G0.3 | - | player-only guard in `GangOptionContribution` |
| GR-37 | R2.3 | - | reject unknown nodes (registered `gangland.gang.*` set + Vault-known) |

### 4.2 TF (turf)

| Id | Lane | Pin to flip | Note |
|---|---|---|---|
| TF-01 | G0.1 | `CaptureServiceStartAndCompleteTest` | re-validate the challenger before writing the owner |
| TF-02 | S3.3 | - | wire `garrison_discount`; delete `reinforced_defense` (C2, D7) |
| TF-04 | G0.1 | - | setowner goes through `cancelContest` |
| TF-05 | G0.2 | `TurfManagerTest` | never reuse an id (persist a high-water mark) |
| TF-06 | G0.2 | - | quit cleanup wired |
| TF-07 | G0.2 | - | every task-owning turf bean implements `BeanLifecycle` |
| TF-08 | G0.2 | - | boss-bar task handle + cancel |
| TF-09 | G0.2 | - | first inactivity sweep after a short delay |
| TF-10 | G0.1 | - | auto-release through the one owner-change path |
| TF-11 | R2.1 | `CaptureServiceHelpersTest` | dead `exclude` removed with the single-bar capture (D8) |
| TF-12 | R2.1 | `CaptureServiceOwnedTurfTest` | abandon grace on owned turfs |
| TF-13 | G0.1 | - | nullable challenger |
| TF-14 | G0.2 | - | `Number` casts |
| TF-15 | **deferred** | - | BigDecimal column needs a migration; no player-visible gain |
| TF-16 | R2.1 | - | use world min/max height |
| TF-17 | S3.3 | - | guard price in `turf_powerups.yml` |
| TF-18 | S3.3 | - | paged catalogue |
| TF-19 | S3.3 | - | re-validate ownership on every click |
| TF-20 | S3.3 | `GarrisonManagerTest` | waves, refund, no consume without Citizens |
| TF-21 | M1.4 (size limits) / W4 (particle clipping) | - | map needs sane sizes |
| TF-22 | **deferred** | - | rides the i18n item CM-15 |
| TF-23 | M1.5 | - | strings into `turf/turf_messages.yml` |
| TF-24 | R2.1 | `GangAllianceTest` (addAlly one-directional) | symmetric `Gang.isAlly` (gang module); capture and friendly fire follow |
| TF-25 | G0.1 | - | NEW turf listener on `GangDeleteEvent` releases turfs through the owner-change path |
| TF-26 | R2.1 | `CaptureServiceOwnedTurfTest` (documents) | stays intentional under D8's single bar; documented in the info card (`only the attacking gang counts`) |
| TF-27 | M1.2 | - | "safe for" derived from `lastCaptureTimestamp` |
| TF-28 | G0.1 | - | `Reason.CANCELLED` fired by `cancelContest` |
| TF-29 | M1.4 | - | cross-world corner + duplicate name |
| TF-30 | M1.4 | - | one admin namespace (`gangland.turf.admin`) |
| TF-31 | G0.5 | - | document as staff tool |
| TF-32 | S3.3 | - | batch prune |
| TF-33 | S3.3 | - | lookup by id; purge pending queue |
| TF-34 | G0.2 | - | copy-on-write world lists (the map reads them at 1 Hz) |
| TF-35 | S3.2 | - | UUID index |
| TF-36 | G0.2 | - | `turf_npcs.yml` re-read on reload |
| TF-37 | R2.5 | `ActiveBuffManagerTest` | multiplier cap `Max_Income_Multiplier` (2.0); over the D2 cap only the oldest-captured `cap` turfs pay |
| TF-38 | S3.3 | - | pass the configured name |
| TF-39 | M1.4 | - | NEW `TurfRenameCommand` |

### 4.3 New findings to triage (G0.5, `triage/<slug>.txt` + `build_docket.py`)

TF-03 residual (delete leaves bars/guards; fixed by G0.1), turf beans initialised twice (`TurfModuleConfig.garrisonManager`,
`turfManager`), Quartermaster pending-queue orphan, `TurfVisualization` particle cost, contribution dual unit,
`gangland.turf.capture|contribute|upgrade` nodes with no consumer (decide: delete in R2.3), garrison stock burned without
Citizens (fixed in S3.3), guard squads keyed per faction not per turf (fixed in S3.1/S3.2), and GR-02's noted payout
loop re-reading the balance (**verify first**, then triage if still present).

---

## 5. Delivery

### 5.1 Model tiers and rules

- **haiku 4.5**: census, docs, YAML, `commands.json`, test scaffolding, triage files.
- **sonnet 5.5**: bounded implementation + tests inside one or two modules.
- **opus 5.5**: cross-module design, concurrency/state machines, adversarial review. **At most 3 concurrently.**
- **fable 5.1**: advisor at each wave gate and at the D1 decision.
- No agent spawns sub-agents; the orchestrator hands each lane its file list (from this plan) and the rule "run `graphify query` first".
- Every parallel lane runs in its own worktree `E:/Programming/java/wt/gl14-<lane>` branched from `gang-turf-map`.
- Every exit criterion includes: `mvn test -pl <module> -am` green (plus `mvn clean install -DskipTests` reactor compile at
  the gate), new tests shown **red against the pre-fix code** first, named pins flipped, docket rows written
  (`bugs` collection: commit, branch, what changed, covering test, what was left out), `commands.json` entries in the
  jar that owns the command, and `graphify update . --force` after any lane that adds, moves or deletes classes.
- Tests follow `documentation/TESTING.md` (keystone-testkit seams, fakes over mock chains, Windows DB rules:
  `@TempDir(cleanup = CleanupMode.NEVER)` + release in `@AfterEach`; no pom edits).
- Platform: Spigot 1.16.5 API floor, Java release 17, XSeries for materials/particles/sounds, method braces on their own lines.

### 5.2 File ownership per wave (prevents merge fights)

| File | W0 owner | W1 owner | W2 owner | W3 owner |
|---|---|---|---|---|
| `GT/TurfModuleConfig.java` | G0.2 (G0.1 rebases on it; merge order G0.2 -> G0.1) | M1.1 (others add beans through M1.1's merge) | R2.1 | S3.2 |
| `GT/capture/CaptureService.java` | G0.1 | M1.2 (extraction only, no behaviour change) | R2.1 | - |
| `GT/manager/TurfManager.java` | G0.2 | M1.1 (index hooks) | - | - |
| `GT/listener/powerups/GarrisonDeployListener.java` | - | - | - | S3.3 (S3.2 adds stand-down via S3.3's merge) |
| `GT/npc/defender/TurfDefenderDeployer.java` | - | - | R2.1 (adds read-only `aliveInside` only) | S3.2 (S3.3/S3.4/S3.5 rebase in order S3.2 -> S3.3 -> S3.4 -> S3.5) |
| `GG/command/sub/gang/GangCommand.java` | - | - | R2.2 | - |
| `gangland-impl/src/main/resources/inventory/*.yml` | - | - | R2.2 | - |

### 5.3 Waves and lanes

#### W0 - Trustworthy foundations (no design change; starts as soon as 0.13.0 is on master)

| Lane | Scope | Modules / files | Tier | Parallel | Depends on | Test gate | Exit |
|---|---|---|---|---|---|---|---|
| G0.1 pipeline | one `CaptureService.cancelContest(turf, Reason)` and one owner-change path (NEW `TurfOwnership` service) used by capture, `setowner`, inactivity release, income orphan release, delete, and a NEW `TurfGangDisbandListener` on `GangDeleteEvent`; always fires `TurfOwnerChangedEvent` / `TurfCaptureFailedEvent(CANCELLED)`. TF-01, 04, 10, 13, 25, 28 + TF-03 residual | turf: `capture/CaptureService`, `command/TurfSetOwnerCommand`, `command/TurfDeleteCommand`, `task/InactivityReleaseTask`, `task/TurfIncomeDistributor` | sonnet | yes `wt/gl14-pipeline` | - | flip `CaptureServiceStartAndCompleteTest`; NEW `TurfOwnershipTest`, disband-listener test | every owner change observable by one event; boss bars and guards clean up in all five paths |
| G0.2 lifecycle | `BeanLifecycle` for every task bean; boss-bar task handle; quit cleanup; first sweep delay; `Number` casts; id high-water mark; copy-on-write world lists; `turf_npcs.yml` reload; double `initialize()`. TF-05, 06, 07, 08, 09, 14, 34, 36 | turf: `TurfModuleConfig`, `task/*`, `listener/TurfBossBarListener`, `database/TurfRepository`, `manager/TurfManager`, `npc/config/TurfNpcsConfigLoader` | sonnet | yes `wt/gl14-lifecycle` | - | flip `TurfManagerTest`; reload test per bean | `/glw reload` re-reads turf config and leaves one task per job |
| G0.3 gang hardening | GR-12, 14, 15, 20, 27, 28, 30, 32, 33, 34, 35, 36 | gang (+ mail for GR-14, api `Settings` for GR-27) | sonnet | yes `wt/gl14-gang-hard` | - | flip `RankManagerTest` (GR-12), `GangAllianceTest` (GR-35) | no NPE path left in the census list |
| G0.4 invite and name fixes | GR-10, 11, 23, 24, 29, 31; NEW `GangNameRules`, `GangInviteDeclineCommand`; `Invite_Offline_Expiry_Days` in a gang-owned YAML | mail `GM/MailModuleConfig`, `GM/command/invite/*`, `GM/database/MailRepository`; gang `GangCreateCommand`, `GangRenameCommand`, `GangTransferCommand` | sonnet | yes `wt/gl14-invites` | - | red-first tests per entry | join flow has accept/decline/expiry; names validated |
| G0.5 hygiene | `commands.json` drift (`gang_change_name` -> `rename`, `ally_remove` -> `abandon`, mail `ally accept/reject`); triage files (4.3); verify GR-02 loop; fix `documentation/features/ranks.md` API block and node names; TF-31 doc | docs + resources | haiku | yes `wt/gl14-hygiene` | - | `mvn -q -DskipTests install` unaffected | docket rebuilt with new rows |
| G0.6 acceptance | H12 faction-alert scenario + H13 smoke on merged master (guards squad and shout today) | test server, `smoke.py` | sonnet (+ owner for the player step) | yes | H13 merged | report under `brainstorming/gang-turf-wave-2026-09-30/smoke/` | pass, or findings triaged before W3 |

Gate W0 (fable): all pins flipped with red evidence, reactor compile, docket rows. Opus in W0: **0**.

#### W1 - See the city (map + readability; D6 needed only for M1.4's snapping)

| Lane | Scope | Modules / files | Tier | Parallel | Depends on | Test gate | Exit |
|---|---|---|---|---|---|---|---|
| M1.1 map | section 2.3 classes; `/glw map` print, `on|off`, `zoom`; raster cache on the owner-change event | turf `map/*` NEW, `TurfModuleConfig`, `TurfManager` (index hooks), `turf/turf_map.yml` NEW, turf `commands.json` | sonnet | yes `wt/gl14-map` | G0.1, G0.2 | NEW `TerritoryRasterTest` (coverage, lowercase, relation, stable letters, window centring), `TurfChunkIndexTest`, renderer run-length test | frame in 2.2 reproduced by a golden test; auto map throttled |
| M1.2 explain | NEW `CaptureEligibility` (pure verdict + reason + until) extracted from `CaptureService.isCapturable` with **identical** behaviour; used by `TurfCaptureFeedbackListener`, `TurfInfoCommand` card rewrite, map hover; boss-bar text with counts and time left; TF-27 | turf `capture/*`, `command/TurfInfoCommand`, `listener/TurfBossBarListener`, `listener/TurfCaptureFeedbackListener` | sonnet | yes `wt/gl14-explain` | G0.1 | `CaptureEligibilityTest` covering every reason; existing capture tests unchanged | one reason text everywhere |
| M1.3 HUD | `TurfPlaceholderContribution`; action-bar line + `map hud`; retire `TurfPresenceBarListener` (D9); income summary line per gang | turf `placeholder/*` NEW, `task/TurfLocationTracker`, `task/TurfIncomeDistributor` | sonnet | yes `wt/gl14-hud` | G0.2, M1.2's `CaptureEligibility` (merge after M1.2) | placeholder resolver tests with a fake cache | sidebar shows turf line with zero Plaque change |
| M1.4 turf commands | TF-21 size limits, TF-29, TF-30 one namespace, TF-39 NEW `TurfRenameCommand`, name-based turf ids + completion for `info/select/tp`, `turf list` paged by distance with direction, click = info; help hiding check (C10); D6 snapping | turf `command/*`, `selection/Selection`, `data/CuboidRegion` | sonnet | yes `wt/gl14-turfcmd` | G0.2 | command tests per entry | player help shows 3 turf verbs + `map` |
| M1.5 strings and docs | NEW `turf/turf_messages.yml` (TF-23 strings: Quartermaster panels, wand, status), glossary rewording, `documentation/features/turf.md` map section, player guide page | turf resources + docs | haiku | yes `wt/gl14-strings` | - | YAML lint skill (`gangland-yaml-review`) | no hardcoded English left in `GT/npc/view/*` |

Gate W1 (fable + **1 opus** adversarial review of M1.1/M1.2 for perf and correctness): owner walks the 10-minute script
up to minute 4 on the test server.

#### W2 - Gang life and the rules of war (needs D1-D5, D7, D8 answered)

| Lane | Scope | Modules / files | Tier | Parallel | Depends on | Test gate | Exit |
|---|---|---|---|---|---|---|---|
| R2.1 capture rules | D1 window + contest lock; D7 guards count as defenders (one read-only `TurfDefenderDeployer.aliveInside(turfId)`); D2 turf cap and over-cap rule; D8 single-bar capture; TF-11, 12, 16, 24, 26 | turf `capture/*`, `CaptureSettings`, `TurfModuleConfig`, `npc/defender/TurfDefenderDeployer` (read-only method only); gang `GG/gang/Gang.java` `isAlly` (TF-24: the one-directional check is here, reached via `GangMembershipInstaller.java:59`, so capture and `TurfFriendlyFireListener` both become symmetric without editing cops-n-crooks) | **opus** | yes `wt/gl14-rules` | W1 merged, D1/D2/D7/D8 | flip `CaptureServiceOwnedTurfTest` (TF-12), `CaptureServiceHelpersTest` (TF-11), `GangAllianceTest` (TF-24); NEW table tests for window x presence x guards | every verdict reason in `CaptureEligibility` reachable and tested |
| R2.2 gang onboarding and hub | browser for gangless `/glw gang`; `gang list` = browser; NEW `GangInfoCommand`, `GangJoinCommand`, `GangRecruitCommand` (Open/Invite only, C3); ally by name; leave confirm idiom (C6); first-join tip; GR-16, GR-17 | gang `command/sub/gang/*`, `listener/gang/MemberJoinListener`, `gang/menu/*`; impl `inventory/gang_info.yml`, `alliance_stat.yml`, `gang_stat.yml`, `phone_gang_search.yml` | sonnet (+ haiku for YAML menus) | yes `wt/gl14-hub` | G0.4 | command tests; menu YAML lint | 10-minute script minutes 0-2 true |
| R2.3 ranks preset | D4 seed Member->Officer->Leader on an empty rank table, default nodes; register `gangland.gang.*`; rank "Can:" lore; GR-13, 21, 22, 37; drop or wire unused `gangland.turf.*` rank nodes | gang `rank/*`, `database/repositories/rank/*`, `permission/GangPermissions` | sonnet | yes `wt/gl14-ranks` | G0.3 | flip `RankManagerTest` (GR-13); seed test on empty and non-empty tables | fresh install: a member can deposit, an officer can invite |
| R2.4 alerts and gang log | api `RadioVoice.audience` (2.0 -> 2.1, turf `Host_Api: 2.1`); NEW `TurfRadio` (T1) replacing `TurfCaptureNotifier` chat lines (C9); NEW `GangLogService` + `GangLogRepository` (table `gang_log`, last 20 rows per gang) in the gang module; login digest; turf posts capture/loss/income lines through its existing gang edge | api `npc/radio/*`; turf `radio/*` NEW; gang `log/*` NEW | sonnet; **opus** reviews the api change | yes `wt/gl14-alerts` | G0.1 | `SquadRadioTest` extended (audience null = old behaviour); `GangLogRepository` SPI test with Windows DB rules | offline gang sees what happened on login; `GangLogRepository.setDataSupplier(...)` wired in `GangLogService.initialize()` (else autosave throws `No data supplier set`) |
| R2.5 progression and income | D3 turf points -> gang XP (`Gang.level`, fire `GangLevelUpEvent`); `Member.contribution` money only; delete bounty event (C1); TF-37 cap; income only within cap | turf `contribution/*`, `listener/contribution/*`, `task/TurfIncomeDistributor`; gang `events/gang/*`, `listener/gang/*` | sonnet | yes `wt/gl14-progress` | R2.1 cap API (merge after R2.1) | flip `ActiveBuffManagerTest` (TF-37); XP producer tests | gang level moves in a real fight |
| R2.6 gang chat | NEW `GangChatCommand` (one-shot + toggle); man-down line to gang-mates (T11 part) through the R2.4 seam | gang | sonnet | yes `wt/gl14-chat` | R2.4 for man-down (chat half independent) | chat routing tests | `commands.json` entry in the gang jar |

Gate W2 (fable on the D1 outcome; opus concurrency: R2.1 + R2.4 review = **2**): owner walks the whole 10-minute
script; docket rows.

#### W3 - A garrison that fights like a squad (needs W2 R2.1 + R2.4, G0.6 pass)

| Lane | Scope | Modules / files | Tier | Parallel | Depends on | Test gate | Exit |
|---|---|---|---|---|---|---|---|
| S3.1 civilians hit path | `CivilianService.alertFaction`/`recruit` honour `victim.getFactionSquads()` | civilians `GC/npc/CivilianService`, `GC/npc/combat/CivilianCombatBehavior` | **opus** | yes `wt/gl14-civ` | G0.6 | NEW squad-identity test (hit keeps the turf squad) | cross-turf recruitment impossible |
| S3.2 squad, posts, leash, stand-down | T2, T13, TF-35; NEW `TurfGarrisonSquad`, `TurfPosts` | turf `npc/defender/*`, `listener/powerups/GarrisonDeployListener` (via S3.3 merge) | sonnet | after S3.1 | S3.1 | leash and post tests with fakes | guards return to posts; end-of-fight radio line |
| S3.3 reserve, waves, Quartermaster | T3; TF-02, 17, 18, 19, 20, 32, 33, 38; guard price + discount in `turf_powerups.yml` | turf `powerups/*`, `npc/view/*`, `npc/TurfPowerupManager`, `listener/powerups/GarrisonDeployListener` | sonnet | yes `wt/gl14-waves` | S3.2 merge for deployer edits | flip `GarrisonManagerTest` (TF-20) | stock never burned; refunds work |
| S3.4 identity | T6 callsigns + shout format; T7 health-bar verification and doc | turf `npc/defender/*`, civilians `civilian_messages.yml`, `FactionVoice` extras | sonnet (T6) + haiku (T7 verify/doc) | yes `wt/gl14-names` | S3.2 | callsign format tests (<= 16 chars, no `CIT-`) | attacker reads whose guard it is |
| S3.5 rewards and recycling | T9 NEW `TurfDefenderMoneyDropSource`; T12 stagnation recycle | turf `npc/defender/*`, turf money-drop wiring | sonnet | yes `wt/gl14-bounty` | S3.2, S3.3 (price source) | drop source tests (owner/ally excluded) | killing guards pays the owner's own spend back to attackers |

Gate W3 (fable + **1 opus** adversarial review of S3.1-S3.3; peak opus = 2): server smoke with Citizens and without
(`NpcSupport.available()` false -> no stock consumed).

#### W4 - Optional expansions (owner picks per item; each is independent)

map item (`/glw map item`), dynmap then BlueMap layers, player borders (`/glw map border`, clipped `TurfVisualization`),
roles T4 + commander-down T10 (api 2.2 `NpcRole`), responders T8, heat T5 (D10), hologram banner, `TurfMapLayer`
seam for cops-n-crooks. One sonnet lane each; opus only for T4 (api + civilians) and T5 (cross-module balance).

### 5.4 Concurrency picture

| Wave | Lanes in parallel | Opus at once |
|---|---|---|
| W0 | 6 (G0.1-G0.6) | 0 |
| W1 | 5 (M1.1-M1.5), M1.3 merges after M1.2 | 1 (gate review) |
| W2 | 6, R2.5 merges after R2.1, R2.6 man-down after R2.4 | 2 |
| W3 | S3.1 first, then S3.2, then S3.3-S3.5 in parallel | 2 |

### 5.5 Merge order per wave

W0: G0.2 -> G0.1 -> G0.3 -> G0.4 -> G0.5. W1: M1.1 -> M1.2 -> M1.3 -> M1.4 -> M1.5. W2: R2.1 -> R2.4 -> R2.2 -> R2.3 ->
R2.5 -> R2.6. W3: S3.1 -> S3.2 -> S3.3 -> S3.4 -> S3.5. The integrator runs the reactor build and `graphify update . --force`
after each wave.

### 5.6 Keystone upstream lane (conditional, separate)

No Keystone work is needed for W0-W3. Two candidates are recorded, each only when a **second consumer** exists:
K-1 promote the grid painter (`TerritoryRaster` window maths + legend allocator + run-length row builder) to
`keystone-common` (Keystone 1.15.0, phase doc, `wt/keystone-1.15.0`, then bump `<keystone.version>`); K-2 a generic
`AbstractNpc` hold-post-and-leash primitive if cops adopt the turf leash. Never forked locally.

---

## 6. Open decisions for the owner

| Id | Question | Options | Recommendation |
|---|---|---|---|
| D1 | When can an owned turf be attacked? (today: only when the whole owner gang has been offline 10+ min, `CaptureService.isCapturable:112-127`) | A. **Online war**: attackable while at least one member of the **owner gang itself** is online (an online ally does not open the window); a turf becomes **shielded** 10 min after its last member logs off; a capture already running continues through logouts (contest lock). B. Always attackable; capture runs at half speed while no owner is online. C. Keep offline-only raids, add alerts and digest. D. Scheduled war windows per server. | **A.** One sentence explains it, it creates real fights, and the 10-minute delay stops combat-logging to dodge. Guards then have a clear job: they are your numbers when you are outnumbered, and they count on the bar (D7). Keep today's predicate behind `Capture_Window: OFFLINE` for servers that want it (it is one method). |
| D2 | How is snowballing bounded? | A. **Turf cap by active members**: `Max_Turfs = min(Max_Turfs_Ceiling (6), 1 + active/2)`, where active = members with `OfflinePlayer.getLastPlayed()` within `Active_Member_Days` (7) (no new column). B. Cap by gang level. C. Upkeep cost per turf. D. None. | **A.** Readable (`Turfs 3/4`), punishes one-member gangs and inactive padding, the ceiling stops an open-recruiting zerg, no new economy. B feeds a loop where more turf gives more XP gives more turf. Over cap (members left): no forced release; the oldest-captured `cap` turfs pay, the rest show `over cap - pays nothing` in hover/info, and a gang at or over its cap cannot start a new capture (told so on entry, before the bar starts). |
| D3 | The dual-unit `Member.contribution` | A. **Turf points -> gang XP; contribution = money share only.** B. New per-member `rep` column (schema). C. Keep merged. | **A.** Fixes GR-19's dead level for free and makes disband payouts fair. Existing mixed values cannot be split; document that shares reset only for new points. |
| D4 | Ranks for players | A. **Seed Member -> Officer -> Leader with default nodes on an empty rank table only.** B. Keep `member -> owner`, document admin setup. C. Per-gang ranks (schema change). | **A.** Existing servers untouched; fresh servers work out of the box. C is out of scope. |
| D5 | Where does "while you were away" live? | A. **Gang-owned log (`gang_log` table) + login digest.** B. An additive api event that mail consumes (no digest without mail). C. None. | **A.** Turf already depends on gang; mail must never gain `Depends: [turf]`, and news should work without mail. |
| D6 | Snap new turf boundaries to chunks? | A. **On for new turfs** (`Turf.Snap_To_Chunks: true`), existing untouched. B. Off. | **A.** Crisp map, no migration. |
| D7 | Should guards count on the capture bar? | A. **Yes, each live guard inside counts as one defender; delete `reinforced_defense`.** B. No, wire `CAPTURE_DEFENSE_BONUS` as invisible phantom defenders. C. No, and delete both buffs. | **A.** Visible, and it gives attackers a clear objective (clear the guards). |
| D8 | Unclaimed capture | A. **One bar: attackers vs everyone else inside, same as owned turfs.** B. Keep the two-phase claim/consolidate. | **A.** One rule for every capture; the two stacked bars are the most confusing screen today. |
| D9 | Persistent "Territory of X" boss bar | A. **Retire; move to the action-bar line.** B. Keep both. | **A.** The boss bar then only means "fight". |
| D10 | Turf fights raise wanted level (T5) | A. Never. B. **Later (W4), off by default, initiator only, policed districts only.** C. On in W3. | **B.** Highest griefing risk in the programme; decide after W3 is played. |

---

## 7. Risks, anti-exploit notes, out of scope

### 7.1 Risks

| Risk | Mitigation |
|---|---|
| D1 flips who can attack when; existing servers feel it | `Capture_Window: OFFLINE` keeps today's rule; migration note in `documentation/migration-0.14.0.md` |
| H13 lands late or at the wrong Keystone pin | W0-W2 do not need 1.14.0; only W3 S3.5 (T12) and W4 T4 do. Start W0 on 0.13.0 master regardless. |
| Chat spam (auto map, radio, income lines) | auto map off by default + cell/hash gate + 2 s floor; `SquadRadio` per-squad and per-player throttles; one income line per gang per payout |
| Map cost | chunk index + per-world raster cache + run-length components; HUD reads the tracker cache |
| Four lanes touch `TurfModuleConfig` / `CaptureService` | file-owner table (5.2) + fixed merge order (5.5) |
| Guard squads change identity (S3.1) and could regress civilians | opus lane, G0.6 acceptance baseline before, squad-identity test after |
| `Host_Api: 2.1` turf jar on a 2.0 host | ships in the same 0.14.0 release as the api change; the loader refuses with `module.host.incompatible` rather than failing at runtime |

### 7.2 Anti-exploit

- **Logout to dodge (D1-A):** contest lock + shield only 10 min after the last logout.
- **Friendly ping-pong for XP:** no capture XP or bonus when the previous owner is an ally or when the same two gangs swapped this turf in the last `Swap_No_Reward_Hours` (24).
- **Guard bounty farming (T9):** owner and allied kills pay nothing; bounty is a fraction of what the owner paid, never minted.
- **Alt-gang cap evasion (D2):** allies do not share a cap; accepted residual risk, documented.
- **Zerg and padding (D2):** only members seen in the last 7 days count, and `Max_Turfs_Ceiling` caps any gang regardless of size.
- **Ally opens a window (D1):** only owner-gang members online make a turf attackable; allies defend but never expose.
- **Name spoofing:** `GangNameRules` strips colour codes and enforces a charset (GR-29).
- **Open-join spies:** Open is opt-in; officers can kick; new members join at Member rank with no bank withdraw.
- **Quartermaster bank drain:** ownership re-checked on every click (TF-19); spending gated by a rank node (R2.3 decides whether the unused `gangland.turf.upgrade` node becomes that gate or is deleted).
- **Income printing:** multiplier cap (TF-37) and no income for turfs above the cap.

### 7.3 Out of scope

Player-claimable chunks (a separate product, rewrites capture/garrison/powerups); per-gang rank trees (schema);
squaremap and Pl3xMap (Paper-only); cuff/detain for gangs; a gang home beyond the existing `WaypointType.GANG` waypoint
(`gangland-api/.../data/teleportation/Waypoint.java`) and `WaypointGangIdCommand`; i18n of turf strings (TF-22, rides
CM-15); BigDecimal income column (TF-15); gang bounty posting; per-member rep; `/glw gang ping`; ally chat; any
`io.papermc.*` API.
