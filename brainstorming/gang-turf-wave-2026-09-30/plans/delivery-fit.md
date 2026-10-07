# Gang & Turf roadmap: delivery-fit lens (target Gangland 0.14.0)

Planner: `delivery-fit` (Opus 5.5), wave `gang-turf-wave-2026-09-30`, written 2026-09-30. Planning only, no product code.
Lens: the smallest shippable increments on this codebase, fixing the docket first, respecting module boundaries, and
running lanes in parallel with no two lanes owning the same file.

Inputs read in full: every file under `census/` (gang, turf, cnc-ideas, map-feasibility, docket-gr-verified,
docket-tf-verified, ux-surface, seams, docket-gang-turf). Orientation: `graphify explain CaptureService`,
`explain TurfLocationTracker`, `explain GangMembership`, `explain WaypointGangIdCommand`, `explain TurfCommand`,
`query RadioVoice`, `affected GangAllianceRepositoryContract`, `affected TurfPowerupManager`, `affected GangDeleteEvent`.
Graph freshness: `graphify-out/graph.json` 2026-09-29 23:08 is newer than HEAD `ff9d813f` (23:05), so it is current.
Raw reads after orientation: `CaptureService.java` L95-200, `RadioVoice.java`, `TurfInfoCommand.java`, turf `module.yml`,
`GangDeleteCommand.java` payout lines, `documentation/TESTING.md` §6-§9, the smoke `README.md`, git topology of
`npc-roles-medics` and the `h13-*` branches (Gangland) and `phase-h13-npc-roles` (Keystone).

---

## 0. Summary

**Pitch in one line.** Gangs fight over named hoods they can see on a map. Owning a hood pays into the gang bank,
the bank buys guards, the guards hold the hood, and the gang is told when a rival comes, even while offline.

**What ships, in order:**

| Wave | Name | Player-visible result | H13 needed? |
|---|---|---|---|
| W0 | Trustworthy foundations | Almost nothing new; ownership, lifecycle and gang/mail bugs stop lying. 36 docket entries closed (35 fixed, TF-31 closed by design). | no |
| W1 | See the city | `/glw map` (Factions-style chunk grid, hover, click, auto), turf placeholders, an action-bar compass, clearer turf text, a gang onboarding screen, a gang browser, a working default rank ladder. | no |
| W2 | Fair fights | A readable raid rule, living guards count as defenders, guards form a per-turf squad with posts and a leash, reinforcement waves with refunds, turf alerts anywhere, a "while you were away" digest, income messages, gang XP from turf, gang chat. | no |
| W3 | Map everywhere (pick à la carte) | Map zoom, clipped border outline for players, dynmap layer, in-hand map item. | no |
| W4 | Garrison roles | Shield/marksman/commander guards, gang-tagged callsigns, health bars, defender bounty drop, commander-down morale, stuck-guard recycling. | **yes** |

**What gets cut** (details in §1.3): gang bounty (inert), `Gang.State.CLOSE`, the `CAPTURE_DEFENSE_BONUS` buff
(replaced by "living guards count"), alert tiers (`TurfAlertLevel`), a separate turf-radio api seam (not needed), the
partial-coverage glyph on the map, tutorial/first-time-tip systems, per-gang rank trees, player-claimable chunks.

**API cost:** W0 to W3 need **no `gangland-api` surface change** (`GanglandApi.VERSION` stays `2.0`). W4 adds one
record (`NpcRole`, NEW) and bumps it to `2.1`. **Keystone cost:** none in W0 to W3. W4 rides Keystone **1.14.0**
from H13. Two primitives are candidates for Keystone **1.15.0**, but only once a second consumer needs them (§5.9).

---

## 0.1 Baseline, dependencies, census corrections

**Branch and revision.** Target revision **0.14.0**. The next free revision after H13 = Gangland **0.13.0** on
`npc-roles-medics` (`f9295ec0`, only the "open phase H13" commit), whose work sits on four **unmerged** sub-branches:
`h13-roles` (`d1122fef`), `h13-names` (`93714d0b`), `h13-stuck` (`e35b6b77`) and `h13-drop` (`b5f4160f`), plus Keystone
`phase-h13-npc-roles` (**1.14.0**, `6ef88c6`). None of the four sub-branches has been merged into `npc-roles-medics`
yet (verified with `git merge-base --is-ancestor`). `npc-roles-medics` still pins `keystone.version` 1.13.0.

**What H13 touches** (verified `git diff --name-only master...h13-*`): cops-n-crooks (roles/names/stuck/drop),
the new module `gangland-healthbars`, one civilians class (`BartizanNpcWeapons` + 2 tests), one api class
(`GanglandMoneyDropClassifier`), `gangland-impl/.../items/money.yml`, `gangland-infra`, docs, and `pom.xml`.
It does **not** touch `gangland-gang`, `gangland-turf`, `gangland-mail` or `CivilianService`. W0 to W3 can therefore start
from master **now**, in parallel with H13. Only W4 waits for H13 to merge.

- Integration branch: **`gang-turf-territory`** from master `ff9d813f`, worktree `E:/Programming/java/wt/gangland-0.14.0`,
  `pom.xml` `<revision>0.14.0</revision>`, `keystone.version` stays **1.13.0**.
- When 0.13.0 merges to master, merge master into `gang-turf-territory` (conflict expected only on the `<revision>` and
  `keystone.version` lines: keep 0.14.0 and take 1.14.0). **W4 starts only after that merge.**
- Lane worktrees: `E:/Programming/java/wt/gt14-<lane-id>` on branches `gt14-<lane-id>`. Each is cut from the
  integration branch *after* its wave's kickoff commit and merged back by the orchestrator.

**Census corrections** (do not propagate these into lane prompts):

1. `seams.md` says `GangAllianceRepositoryContract` is used by turf (income, garrison). **Wrong.**
   `graphify affected GangAllianceRepositoryContract` shows only gang-module users (`GangConfig`, `GangManager`,
   `GangAllianceRepository`, gang tests, and one mail test). Turf reads alliances through `Gang.isAlly` via `GangLookupContract`.
2. `seams.md` says cops' `CopRadio`/`CopLoader` consult `TurfPowerupManager`. **Wrong.** The only outside consumer is
   `cops-n-crooks/.../listener/turf/TurfFriendlyFireListener` (graph L12/L42).
3. `seams.md` says cops "throws NPE if turf absent". This is moot: cops `Depends: [turf, civilians]`, so it never loads without turf.
4. `ux-surface.md` says titles are unimplemented. **Wrong.** `TurfActionBarListener` sends title and subtitle on enter
   (`Show_Enter_Title`, per turf.md §4). It also says turf messages are "~12 via proxy". The api `Messages` enum has
   ~60 `TURF_*` constants (docket-tf TF-22).
5. `map-feasibility.md` §1.5 treats `.`, `!` and lowercase letters as one glyph-width class. **Wrong** for the vanilla
   font: `.`, `!`, `i`, `l`, `|` are ~2 px wide against ~6 px for most letters, and lowercase widths vary (`f`, `k`, `t`, `l`, `i`).
   §2.3 below restricts the map to one width class.
6. The GR-02 note mentions an "adjacent bug": payout loops re-read the balance inside the loop. That bug is **already fixed** in code:
   `GangDeleteCommand` L186 snapshots `gang.getEconomy().getAmount()` once into `payouts(pool, ...)` (L301), and both
   loops read the precomputed map (L208, L251). Record it as closed; do not re-triage it.
7. CLAUDE.md (local, gitignored) is stale. Modules say `Host_Api: 2.0` (not 1.0), and turf is `Depends: [civilians, gang]`.
   The W0-E lane updates it.

---

## 1. Player-facing design

### 1.1 The core loop (what a player does, in order)

```
 join/create a gang ──► open the map ──► walk into a hood ──► hold it (bar fills) ──► it's yours
        ▲                                                                           │
        │                                                               income every 10 min → gang bank
        │                                                                           │
   gang levels up ◄── captures & holding give gang XP          bank buys guards at the Quartermaster
        │                                                                           │
   leaderboard (/glw turf top)                       rivals attack → alert anywhere → guards fight,
                                                     living guards hold the bar back → come defend
                                                     (offline? a digest on login tells you what happened)
```

Design rules for "natural and easy to understand":

- **One rule per mechanic, shown where it matters.** Every rule has one sentence the player sees at the moment it applies:
  on a title, the action bar, the map legend, or a blocked-reason line. There is no tutorial system and no "first
  time" flags (YAGNI). Contextual text always shows.
- **Names, not ids.** Players never type a numeric id. Every id-taking command accepts a name and tab-completes it
  (ally abandon, turf info, map clicks).
- **Every silent outcome gets a sentence.** Income, capture failure, auto-release and disband cleanup all say
  something to the owning gang. If the gang is offline, the digest on login says it.
- **Nothing invisible protects a turf.** Today a turf is immune while any owner is online, and nothing tells the player
  so. W2 replaces that with a rule the player can see (§6 D1).

### 1.2 Mechanics, the rule, and how the player learns it

| # | Mechanic | The rule (one sentence) | How the player learns it in-game | Status → wave |
|---|---|---|---|---|
| G1 | Joining | "Get an invite, or join an open gang." | `/glw gang` with no gang shows 4 lines: what a gang is, the cost, and clickable `[Browse gangs]` `[Create a gang]` (NEW, replaces the bare help page in `GangCommand.onExecute`). Invite lines carry clickable `[Accept]` `[Decline]`. | fix+new → W1 (browser, onboarding), W0 (decline, GR-24) |
| G2 | Creating | "Costs $100,000; name 3-16 letters/digits/_." | The create confirm line states the cost and what you get. Name errors say the rule. | fix → W1 (GR-29) |
| G3 | Ranks | "Member → Officer → Owner. Officers can invite, deposit and buy guards." | A fresh install seeds a 3-rank ladder with nodes (NEW preset). `gang members` shows ranks. A no-permission message names the rank that can do it. | fix → W1 (GR-13, GR-21 seed path) |
| G4 | Bank & share | "Deposits earn your share of the bank if the gang disbands." | The `deposit` line shows your new share. `gang members` shows each share. Turf activity is a *separate* score (G8). | fix → W2 (contribution split) |
| G5 | Allies | "Allies can't hurt each other and help defend each other's turf." | The ally accept line states both effects. The map shows allies aqua. | keep; fix symmetry (TF-24) → W0 |
| G6 | Gang chat | "`/glw gang chat` toggles gang-only chat." | Listed first in the onboarding screen for members. | new → W2 |
| G7 | Gang level | "Captures and holding turf give gang XP; higher level = bigger turf cap." | The capture broadcast shows "+XP". `/glw turf top` ranks gangs. | wire GR-19 → W2 (D6) |
| G8 | Turf score | "Time spent attacking or defending turf is your turf score." | `/glw turf top` shows top members. It is no longer mixed into the bank share. | split → W2 |
| T1 | Seeing turf | "`/glw map` shows who owns what around you." | The onboarding screen and the `/glw turf` root link to it. The legend explains every symbol. | new → W1 |
| T2 | Claiming unclaimed | "Stand in an unclaimed hood for 90 s; if two gangs are there, the bigger group wins it." | The enter title says "Unclaimed: Docks - hold it to claim". The boss bar fills. | keep (reword) → W1 |
| T3 | Raiding owned turf | Default (D1 = `ALWAYS`): "Any owned hood can be raided after its 15-min cooldown; raids on a gang that is fully offline take twice as long." | The enter title on a rival hood states whether it can be raided and how long a raid takes. The map hover shows the cooldown time left. | redesign → W2 |
| T4 | Guards | "Every living guard on the hood holds back one attacker." | The Quartermaster panel says it. Guard deaths are announced ("2 guards left at Docks"). | new rule → W2 |
| T5 | Income | "Each hood pays its owner every 10 min." | One line to online members per payout ("Your 3 hoods paid $300"). The digest reports it if offline. | fix TF-37 silence → W2 |
| T6 | Alerts | "Your gang is told, anywhere, when a hood is hit, from which side, and how it ended." | The line itself (T1 transfer, §3). | new → W2 |
| T7 | Quartermaster | "Officers spend the gang bank here on guards and boosts." | Right-click shows the panel. A no-permission message names the rank. | fix TF-19, TF-23, dangling `gangland.turf.upgrade` → W2 |
| T8 | Boosts | "Boosts raise income or make guards cheaper for a while." | Panel lore. Two boosts that did nothing are wired or removed (TF-02). | fix → W2 |
| T9 | Limits | "A gang can hold N hoods (N grows with gang level)." | Blocked-reason line on the enter title. `/glw turf` root shows `3/5 hoods`. | new knob → W2 (D9) |
| T10 | Inactivity | "A gang offline for 10 days loses its turf." | Digest + map hover "at risk" when under 24 h left (hover only). | keep; fix TF-09, TF-10 → W0 |

### 1.3 Cuts and merges (YAGNI, each one earns its place or goes)

| Thing | Verdict | Why |
|---|---|---|
| Gang bounty (`Gang.bounty`, `GangBountyEvent`, `GangBountyMessageListener`) | **Cut** (D7). Delete the event and listener. Keep the `gang_bounty` placeholders returning `0` so existing scoreboards don't print raw tokens. | Nothing posts or reads it. A gang bounty duplicates user bounties. |
| `Gang.State.CLOSE` | **Merge** into `INVITE`. Wire only `OPEN` (anyone may `gang join`) and `INVITE` (D8). | Three states with no behaviour confuse players. Two states cover it. |
| `CAPTURE_DEFENSE_BONUS` buff (`reinforced_defense`) | **Cut** from the shipped `turf_powerups.yml`. The loader stays tolerant, so an old file keeps parsing and logs a deprecation warning. | "Living guards count as defenders" (T4) gives the same effect with a visible cause. A phantom number is invisible. |
| `TurfAlertLevel` tiers (cnc T3) | **Cut.** | Waves triggered by guard deaths and milestones already give fights a shape. Tiers come back only with roles (W4) if composition needs them. |
| `RadioVoice.audience` api seam (cnc T1) | **Cut.** | `TurfCaptureNotifier` already reaches online defender-gang members anywhere. Alert lines go through it. Zero api change. |
| Partial-coverage glyph on the map | **Cut.** | It doesn't fit the 6 px glyph class, and hover text carries it instead. Snapping new turfs to chunks (D3) removes the cause. |
| `/glw gang ping`, man-down callouts (cnc T11) | **Defer.** | Gang chat plus alerts cover it. Revisit if players ask. |
| Gang home / HQ | **Don't build.** | Gang-scoped waypoints already exist (`/glw waypoint gangId`, `WaypointGangIdCommand`). Link it from the onboarding screen. |
| `gang_stat.yml` (empty screen, GR-17) | **Cut** the button and file registration. | An empty screen is worse than none. |
| Confirm idioms (`leave` = "type twice", others = `confirm`) | **Merge** onto `confirm`. | One pattern to learn. |
| Alias `gang list` = my members | **Re-point**: `gang list` = all gangs (browser). `gang members` keeps the roster. | "list" meaning "my members" surprises everyone. Note it in the migration doc. |
| Rank tree per gang | **Out of scope.** | A schema change (`rank_tree` has no gang column). The default preset fixes the day-one pain. |
| Player-claimable chunks | **Out of scope** (D4). | A capture/economy rewrite, not a map feature. |

---

## 2. The map

### 2.1 Entry point decision: `/glw map` (turf-owned root command)

Chosen: **`/glw map`**, a new root command class in the turf module's command package. Modules may register roots:
the gang module registers `rank` beside `gang`. A 2-word command is what a Factions player types (`/f map`) and it is
the most-used new command. **No `/glw turf map` alias**: one entry point. The `/glw turf` root prints a clickable
`[map]` link instead. Permission `gangland.command.map` (Keystone-derived root node). W1-M checks whether the command
framework lets a root default to `true`; otherwise the migration doc tells owners to grant it (§7 risk R9).

### 2.2 Command set per layer

| Command | Layer / wave | Who | Effect |
|---|---|---|---|
| `/glw map` | L1 / W1 | players | Send the grid once. |
| `/glw map auto` | L1 / W1 | players | Toggle: re-send when you enter a new cell (min 2 s apart). Default off. Cleared on quit. |
| `/glw map compass` | L2 / W1 | players | Toggle the action-bar line "Rival turf Docks - 120 m NE". Default off (D13). |
| `%gangland_turf_*%` placeholders | L2 / W1 | any PAPI consumer (Plaque sidebar) | `turf_name`, `turf_owner`, `turf_state`, `turf_progress`, `turf_relation`, `turf_income`, `turf_nearest_name`, `turf_nearest_distance`, `turf_nearest_direction`, `turf_owned_count`. |
| `/glw map zoom <in\|out>` | L3 / W3 | players | Cell size cycles 8 → 16 → 32 → 64 blocks. |
| `/glw map border` | L3 / W3 | players | 10 s particle outline of the turf you stand in or the nearest one, clipped to 40 blocks around you. |
| dynmap layer | L3 / W3 | web | Area marker per turf, filled with the gang colour, contested turfs outlined red. Config only, no command. |
| `/glw map item` | L4 / W3 (optional) | admin (gives) / players (hold) | In-hand `FILLED_MAP` with territory overlay. |

### 2.3 Render rules (L1)

- **Window**: `Width` 41 × `Height` 9 cells (both odd, config `turf/turf_map.yml`), **fixed north-up**, rows north→south,
  columns west→east, and the viewer in the centre cell. `Cell_Blocks` default 16, so one cell is one chunk.
- **Glyphs (6 px class only)**: `-` no turf, `#` unclaimed turf, `+` you, and gang letters `A`-`Z` except `I`.
  Digits `2`-`9` are the fallback after 25 gangs are visible. No `.`, `!`, lowercase or bold, because they break column
  alignment. **[verify on client]** at GUI scale 1-4 in W1 manual smoke.
- **Letter allocation**: gangs visible in the window, in ascending gang id. Each takes the first letter of its resolved
  display name (`GangDisplayNameResolver.resolve`, stripped of colour codes). On a collision it takes the next letter of
  its name, then the first free `A`-`Z`. The result is stable for the same visible set.
- **Colour = relation to the viewer**: own gang GREEN, ally AQUA, rival RED, owned turf seen by a gangless viewer
  YELLOW (matches `TurfPresenceBarListener`), unclaimed GRAY, no turf DARK_GRAY, you WHITE.
  **CONTESTING overrides with GOLD** (letter or `#` in gold). COOLDOWN has no colour of its own; the hover shows it.
  Per-gang chat colour is **not** used: `Color` has 16 names but 14 codes, and `BLACK`/`GRAY` are unreadable.
- **Which turf a cell shows (majority rule)**: the turf covering the largest share of the cell's blocks, if that share
  is ≥ 50 %. Otherwise `-`. **Visibility guarantee**: any turf in the window that wins no cell is drawn in the cell that
  holds its centre, so a small turf never vanishes. Turfs never overlap (`TurfManager.findConflict`), so at most one turf
  can pass 50 %.
- **Header** (1 line): `Map · world · 1240, -310 · facing NE · north is up`.
- **Legend** (2 lines, plain text so Bedrock/Geyser players get everything without hover):
  line 1 = one entry per visible gang: `V Vipers (you)  K Kings (ally)  R Rats (rival)`;
  line 2 = fixed keys: `# unclaimed  - no turf  + you  gold = under attack`.
- **Run-length merge**: adjacent cells with the same glyph, colour and hover become one component. That keeps the
  packet small (at most 369 components raw, typically under 60).

### 2.4 Sample frame (L1 default, viewer in Vipers, Kings allied, Rats rival)

```
Map · world · 1240, -310 · facing NE · north is up
--RRRRRRRR-#######-----------------------
--RRRRRRRR-#######------KKKKKKKKK--------
--RRRRRRRR--------------KKKKKKKKK-RRRRRR-
--RRRRRRRR----VVVVVVVVV-KKKKKKKKK-RRRRRR-
--------------VVVVVV+VV-KKKKKKKKK-RRRRRR-
--------------VVVVVVVVV-----------RRRRRR-
--------------VVVVVVVVV--#######--RRRRRR-
-------------------------#######---------
-------------------------#######---------
V Vipers (you)  K Kings (ally)  R Rats (rival)
# unclaimed  - no turf  + you  gold = under attack
```

Colours (not visible in plain text): `V` green, `K` aqua, `R` red, `#` gray, `-` dark gray, `+` white.
The `KKK` at columns 27-29 of rows 3-4 render **gold**: Kings' "Docks East" is being raided (hover: "Docks East -
Kings - under attack by Rats, 62 %"). Every row is exactly 41 glyphs, and `+` sits in column 21 of row 5.

### 2.5 Hover and click

- **Hover** (per merged run): turf name; owner, or "Unclaimed"; state ("Quiet" / "Under attack by X, 62 %" /
  "Claiming, phase 2, 40 %" / "Cooldown 7 min"); income per interval; "Your guards: 4 in reserve" **only for the owner
  gang** (never reveal defence to rivals); "Edge of Docks (40 % of this chunk)" when the cell's winner is < 100 %.
  Coordinates of the cell.
- **Click**: `RUN_COMMAND /glw turf info <id>`. `TurfInfoCommand` today resolves only the selected or standing turf
  (verified: no id argument). W1-M adds an `OptionalArgument` id **and a name form**, with tab-completion, so
  `/glw turf info Docks` also works. Admins additionally see `/glw turf tp <id>` in the hover text; it is not clickable,
  to avoid accidental teleports.

### 2.6 Auto map

`MapPrefsService` (NEW) holds per-player `{auto, compass, cellBlocks}` in memory and clears it on quit (the same pattern as
`WandSelectionManager`). A 1 Hz `BeanLifecycle` task (NEW `MapAutoTask`) compares each opted-in player's **cell**
(not turf) with the last one it sent. It re-sends on change, at most every `Auto_Min_Interval_Seconds` (2), and also
re-sends when the world's turf version bumps **and** the window's raster hash changed. It never uses `PlayerMoveEvent`:
the `TurfLocationTracker` javadoc forbids that. `ponytail:` separate 1 Hz tasks for auto-map and the compass. Fold them
into a single tracker hook only if profiling shows a cost.

### 2.7 Data path

- NEW `turf.map.TurfChunkIndex`: per world, chunk key → turfs whose rectangle touches that chunk. Rebuilt for one world
  on `TurfManager.create`/`delete`. Not needed for a single manual send (~18k rectangle tests for 50 turfs, under 1 ms),
  but it earns its place for auto-map, placeholders ("nearest turf") and the map item.
- NEW `turf.map.TerritoryRaster` (pure, no Bukkit): input = window, cell size, turf rectangles, runtime states and
  viewer relation. Output = cells of `{turfId, coverage, glyph, colourKey}` plus a legend. All the unit tests live here.
- NEW `turf.map.TurfMapRenderer`: raster → Bungee components (`ComponentBuilder`, `HoverEvent`/`Text`, `ClickEvent`),
  the same API `TurfListCommand.sendRow` already compiles at the 1.16.5 floor.
- **Turf version counter**, bumped by a listener on `TurfOwnerChangedEvent`, `TurfCapturedEvent`,
  `TurfCaptureStartEvent`, `TurfCaptureFailedEvent` and create/delete. This only works if **every** ownership change
  fires `TurfOwnerChangedEvent`, which is W0-A's job (TF-10, TF-25, TF-01). The map therefore depends on W0.
- Gang names and colours are resolved at paint time through `GangLookupContract`. The raster caches only ids, so gang
  renames need no new gang events (YAGNI: no `GangRenamedEvent`).

### 2.8 Expansion ladder (after the MVP proves itself)

| Layer | What | Where | Notes |
|---|---|---|---|
| L2 (W1) | Placeholders + action-bar compass | `turf.placeholder.TurfPlaceholderContribution` (NEW, sibling of `GangPlaceholderContribution`), `turf.map.TurfCompassTask` (NEW) | Zero api and zero Plaque changes. The compass uses Keystone `ActionBarManager.sendBackground`, so the enter/exit lines from `TurfActionBarListener.send` win automatically. Nearest turf = clamp the player x/z into the rectangle, then an 8-way bearing (`RadioSides.compass8` exists in api). |
| L3 (W3) | Zoom | `MapPrefsService.cellBlocks` + `MapZoomCommand` (NEW) | Only a parameter of the raster. |
| L3 (W3) | Borders | `task/TurfVisualization` gets distance clipping (40 blocks) and an adaptive step, then player `/glw map border` | Fixes the perf finding first (triage "TurfVisualization cost", paired with TF-21). |
| L3 (W3) | dynmap | `turf.map.web.DynmapTurfLayer` (NEW), `dynmap` added to core `plugin.yml` `softdepend` | Keystone `ReflectionGuard` skips the class when dynmap is absent. Rectangle corners are `(minX,minZ)`-`(maxX+1,maxZ+1)` (inclusive max). Fill uses `Color.getBukkitColor()` (distinct per gang). Privacy knobs `Web.Show_Owner` (true), `Web.Show_Income` (false). All calls on the main thread. |
| L4 (W3, optional) | Map item | `turf.map.item.TurfMapRenderer` (NEW, contextual `MapRenderer(true)`), one shared `MapView` id persisted in `turf_map.yml` | Per-player raster-hash cache, so it does not redraw every tick. `XMaterial.FILLED_MAP`. Measure the render cost with 10 holders at smoke before shipping. |
| Deferred | BlueMap | own tiny jar or reflection | Its API bytecode level vs release 17 is unverified. |
| Deferred | Turf-centre hologram banner | `keystone-hologram` | The Quartermaster nameplate already marks a turf. Low value. |
| Deferred | `TurfMapLayer` seam for cops (jails, manhunts) | turf seam, cops contributes | Only after L1 proves the grid. cops already `Depends: [turf]`, so the direction is right. |
| Out | squaremap, Pl3xMap | Paper-only | Platform rule. |

### 2.9 Keystone promotion candidates (not scheduled)

The painter (window maths, 8-way facing, legend allocator, run-length row builder) is generic. Build it as a Bukkit-free
sub-package `turf.map` now. Promote it to `keystone-common` in a Keystone minor **only when a second consumer
exists** (for example a jail map). No local fork of Keystone code.

---

## 3. Cops-n-crooks H11-H13 transfers

Turf guards and the Quartermaster are `CivilianNpc`s. They **already inherit** H11 pursuit and route planning, H12
formation arc, strafing, retreat, shouts and fire-rate cadence, and they will inherit H13 health bars once
`gangland-healthbars` ships (cnc-ideas §1). Do not re-plan those. The table below is the delta.

| cnc id | Mechanic | Verdict | Exactly how, on this codebase | Wave / lane | Needs H13 |
|---|---|---|---|---|---|
| T1 | Alerts to the owning gang anywhere | **Adopt**, without the api seam | Extend `listener/TurfCaptureNotifier`. It already messages online defender-gang members at start and 50 %. Add: attacker head-count and the side they came from (`RadioSides.compass8(turfCentre, attackerCentroid)`), "N guards left" on each `TurfDefenderDownEvent` (NEW, kickoff), "last stand" when guards reach 0, and a result line to owners on **failure too** (today failure is silent). Lines in NEW `turf/turf_alerts.yml` (module-owned; the `Messages` enum is legacy-only). Throttle: at most 1 alert line per turf per 10 s per player, and milestones only upward (already so). | W2-N | no |
| T2 | Per-turf squad, posts, leash | **Adopt** | NEW `npc/defender/TurfGarrisonSquad` holds one `NpcSquad` per turf. Each deployed guard gets `CivilianNpc.setFactionSquads(turfSquads)` (public Lombok setter). **Civilians change (required):** `CivilianService.alertFaction` must route through `victim.getFactionSquads()` when one is set. Today it `computeIfAbsent(new SquadKey(faction, attacker))`, which pulls a hit guard back into the cross-turf faction squad. Chosen over "faction id per turf" because it is one conditional and leaves `civilians.yml` factions and shout formats untouched. **Posts:** 4 corners inset by 3 blocks plus the centre, surface via the `regionCentreSurface` lookup. **Leash:** in `TurfDefenderDeployer.tick` (already every 5 ticks), a guard farther than `Leash_Margin` (16) outside the region drops its target and `navigateTo(post)`. Same fix: index guards by entity UUID (TF-35). | W2-S | no |
| T3 | Reinforcement waves | **Adopt, simplified** | The stock is a reserve. At contest start deploy `min(Wave_Size, stock)` (default 3). On each guard death, after `Wave_Cooldown_Seconds` (20), top up to `Max_Alive` (default 6) from stock. Spawn at the post farthest from the nearest attacker that is out of its line of sight (`LivingEntity.hasLineOfSight`, a handful of candidates). **Refund** undeployed stock at contest end (D10). **Consume only after a successful spawn**, so a Citizens-less server burns nothing (triage finding). Tiers are cut. | W2-S (TF-20) | no |
| T13 | Stand-down, walk home | **Adopt** | `GarrisonDeployListener.onCaptured/onFailed` stop calling instant `recall`. Survivors walk to a post and despawn after `Stand_Down_Seconds` (8), with a `Turf_Held`/`Turf_Lost` shout. | W2-S | no |
| new | Living guards count as defenders | **Adopt** (replaces `CAPTURE_DEFENSE_BONUS`) | `CaptureService.classify` adds `defenders.aliveCount(turfId)` (kickoff signature on `TurfDefenderDeployer`) to `groups.defenders`, capped at `Guard_Weight_Cap` (default 4, so a huge garrison can't make a turf untakeable). Guards deploy on `TurfCaptureStartEvent`, so they never block the contest start (start still needs zero *players* defending). They only slow or reverse progress, which reads as "clear the guards, then hold". | W2-R | no |
| T4 | Roles (shield Defender, Marksman, Commander = Quartermaster) | **Adopt in W4**, **without Medic** (D-list: wait for cops `GL-CARE`) | Promote a generic `NpcRole` record (NEW) into `gangland-api` next to `TacticsConfig`/`RetreatSettings` (api 2.0 → **2.1**). `CopRole` becomes a thin adapter in cops. Civilians get an optional `AI.Combat.Roles` overlay with built-in defaults, so old `civilians.yml` still loads. Turf gets a garrison `Composition` list in `turf_npcs.yml`. Shield reduction goes through one guard in `CivilianDamageListener`. Uses Keystone 1.14.0 `NpcFanPlacement`, `setLeaderPriority`, `setRangedBand`. | W4-R | **yes** |
| T6 | Gang-tagged callsigns | **Adopt in W4** | `[Vipers] Guard #1042`, Quartermaster `[Vipers] Quartermaster`. `CivilianService.FactionVoice.callsign/extras` supply `%gang%` for turf guards, and `civilian_messages.yml` `Shouts.Format` gains `%gang%` with a fallback to `%faction%`. Reuse the `CopNames` badge/hologram helper from `h13-names`. Never emit `CIT-`. | W4-C | **yes** (shares `CopNames`) |
| T7 | Health bars | **Adopt in W4, verify only** | Civilians are transient and unprotected (`CivilianNpcFactory`), so `gangland-healthbars` draws them with zero code. Verify the line layout with T6's hologram line. Note: `gangland-healthbars` declares `Plugins: [Citizens]` (fail-fast), unlike the house convention. Accept it for an optional module. | W4-C | **yes** |
| T9 | Guard bounty drop | **Adopt in W4** (D11) | NEW `TurfDefenderMoneyDropSource`. A killed guard drops `Bounty_Fraction` (0.5) of its purchase price, **funded by what the owner already paid**, so no money is minted. Owner and ally kills never drop (friendly fire is already cancelled by `TurfFriendlyFireListener`; exclude allies the same way). | W4-D | **yes** (`h13-drop` classifier fix `5b7325b5`) |
| T10 | Commander down | **Adopt in W4** | When the Quartermaster dies during a contest, the squad shouts `Commander_Down`, guards `takeCover` for 5 s, and attackers get a one-off +10 % progress. | W4-D | **yes** (leader priority) |
| T12 | Stuck recycling | **Adopt in W4** | In `TurfDefenderDeployer.tick`, a guard with `millisUnreachable() > Recycle_Seconds` (12) and not in any attacker's view respawns at another post, skipping the one it was stuck at. Port only after the cop version is accepted live. | W4-D | **yes** |
| T5 | Turf heat → wanted level | **Defer** (D12), ship nothing | If adopted later: a `TurfHeat` listener in **cops-n-crooks** (legal edge `Depends: [turf]`; turf never depends on cops), with heat accruing to the initiator only, `Policed` districts only, and cops hunting wanted players, never defenders. | later | no |
| T8 | Neighbour/allied responders | **Defer** | Needs an adjacency notion. Revisit once the map shows whether contiguous land matters to players. | later | no |
| T11 | Player man-down/ping callouts | **Defer** | Gang chat + T1 cover it. | later | no |
| — | Cuff/detain, wanted-decay freeze, suppressive fire, named-NPC progression | **Not transferred** | As cnc-ideas §4 (owner already declined some in H12). | — | — |

Before W2-S starts: **sandbox-accept the existing faction-alert behaviour** (H12 left the "civilian and faction scenarios
(GL-4)" unverified, `migration-0.12.0.md` §9). This is a W2 kickoff task (§5.6), because T2 builds on it.

---

## 4. Docket integration

All 25 open GR and 38 open TF entries were verified still open on `ff9d813f` (docket-gr/tf-verified). Rows below map
each to the lane that closes it. **Pinned tests to flip** (red against today's code before the fix lands):

| Entry | Pinned test (flip in the fix commit) | Lane |
|---|---|---|
| GR-12 | `RankManagerTest` (clear() leaves permissions) | W0-C |
| GR-13 | `RankManagerTest` (detached root on renamed head) | W1-G |
| GR-35 | `GangAllianceTest` (equals without hashCode) | W0-C |
| TF-01 | `CaptureServiceStartAndCompleteTest` (capture into disbanded gang) | W0-A |
| TF-05 | `TurfManagerTest` (id reuse after delete + restart) | W0-B |
| TF-12 | `CaptureServiceOwnedTurfTest` (abandon grace ignored on owned path) | W2-R |
| TF-24 (documents) | `GangAllianceTest` "addAlly is one-directional" | W0-C |
| TF-26 (documents, intentional) | `CaptureServiceOwnedTurfTest` | W2-R: keep, re-document |

### 4.1 Per wave

**W0 (36 entries, all docket-first):**
- W0-A turf ownership pipeline: **TF-01, TF-04, TF-10, TF-13, TF-25, TF-28**, plus the TF-03 residual (new triage: delete does not cancel the contest).
- W0-B turf lifecycle and persistence: **TF-05, TF-06, TF-07, TF-08, TF-09, TF-14, TF-32, TF-33, TF-34, TF-36**, plus the new triage entries "beans initialised twice" and "Quartermaster pending-queue orphan" (the latter shares TF-33).
- W0-C gang hardening: **GR-10, GR-12, GR-15, GR-20, GR-28, GR-30, GR-32, GR-33, GR-34, GR-35, GR-36, GR-37, TF-24**.
- W0-D mail + api hardening: **GR-11, GR-14, GR-23, GR-24, GR-27, GR-31**.
- W0-E docs: **TF-31** closed as "by design" (staff `turf buff` is free on purpose) with an admin-doc note.

**W1 (12; TF-23 is partial here and finishes in W2):**
- W1-M: **TF-27** (derive COOLDOWN from `lastCaptureTimestamp` in `TurfManager.register`).
- W1-T: **TF-16, TF-21** (create-size limits; the visualization half moves to W3-B), **TF-29, TF-30, TF-39**, **TF-23** (wand/status strings only).
- W1-G: **GR-13, GR-16, GR-17, GR-21, GR-29**.

**W2 (13):**
- W2-R: **TF-02** (defense half, by cut), **TF-11** (dead `exclude` code), **TF-12**, **TF-26** (decision recorded: keep single-challenger on owned turf).
- W2-S: **TF-02** (discount half, wired), **TF-17, TF-18, TF-19, TF-20, TF-23** (Quartermaster panel strings), **TF-35, TF-38**.
- W2-N: **TF-37** (silent + uncapped multipliers).
- W2-P: **GR-19** (level wired; bounty event deleted per D7).

**W3:** TF-21 visualization half (W3-B).

**Deferred, with reason:**

| Entry | Why deferred |
|---|---|
| GR-22 (naming half: `RankParent` stores the child in `parentId`) | Cosmetic refactor across fixtures and a migration test. The delete half is fixed. Record as "won't fix, documented" unless the rank model changes. |
| TF-15 (income stored as Double) | Column type change through the backend diff engine. The precision loss is sub-cent. Revisit with a money-column sweep across modules. |
| TF-22 (no Spanish turf strings) | Ships with the cross-cutting i18n item CM-15. New W1/W2 strings go into module YAML with `_es` twins created empty. |

Accounting check: 25 GR = 18 (W0) + 5 (W1) + 1 (W2) + 1 deferred. 38 TF = 18 (W0, incl. TF-24 and TF-31) + 6 (W1) +
12 (W2, incl. TF-23) + 2 deferred.

### 4.2 New triage entries (W0-E writes them to `brainstorming/bug-docket-2026-09-06/triage/turf.txt` / `gangs-ranks-mail.txt`, format `num~~tier~~title~~fix~~tests`, then rebuilds with `build_docket.py`)

| Tier | Title | Fix lane |
|---|---|---|
| P2 | Turf delete does not cancel an in-flight contest: boss bars and guards linger (TF-03 residual) | W0-A |
| P3 | `TurfModuleConfig.garrisonManager`/`turfManager` call `initialize()` explicitly and the LIFECYCLE convention calls it again | W0-B |
| P3 | `TurfPowerupManager.remove` never purges the `pending` chunk queue, so a removed Quartermaster respawns on chunk load | W0-B (with TF-33) |
| P2 | `TurfVisualization` spawns ~4,200 particles/s per viewer on a 256×256 turf, with no distance clipping | W3-B |
| P1 | `Member.contribution` is both the bank share and the turf activity score, so turf farming claims bank money on disband | W2-P |
| P2 | `gangland.turf.capture/contribute/upgrade` rank nodes have no production consumer, so any recruit spends the gang bank at the Quartermaster | W2-S (`upgrade` wired); W2-P (`capture`/`contribute` deleted from `RankPermissionApplierTest` fixtures) |
| P2 | `GarrisonDeployListener` consumes the stock before `deploy` no-ops without Citizens | W2-S |
| P3 | Guard squads key on (faction, target), not turf: two turfs' guards share one squad | W2-S |
| P1 | An owner's alt in a second gang standing inside an owned turf blocks every raid start (the "exactly one challenger" rule) | W2-R |
| — | GR-02 adjacent "payout re-reads balance" | Close as already fixed (census correction 6); no new row |

The docket's live status is written to the artifact db (`bugs` collection, doc id = bug id,
`{status, note, updatedAt}`) **after each wave's merge**, with commit, branch, covering test and anything left out.
This is orchestrator work, not a lane's.

---

## 5. Delivery

### 5.1 Model tiers and concurrency rules

| Tier | Used for in this plan |
|---|---|
| **haiku 4.5** | Kickoff skeleton commits, `commands.json` merges, YAML defaults, docs/migration drafts, triage rows, test scaffolding from a written spec. |
| **sonnet 5.5** | Bounded lane implementation and its tests. |
| **opus 5.5** | Capture-maths redesign (W2-R), the api record + cross-module roles (W4-R), and the adversarial review at every wave gate. **At most 3 concurrently.** |
| **fable 5.1** | Judge at each gate: go/no-go against the exit checklist, and an advisor when a lane hits an open decision it cannot default. |

Agents never spawn sub-agents. Each lane prompt carries its owned file list, the census paths, and the rule "run
`graphify query` first; read raw files only after the graph has oriented you" (CLAUDE.md rule 4). Planners and executors
that write files run as `claude`-type agents. `feature-dev` reviewers have no shell, so the orchestrator hands them the
diff and transcribes their output.

### 5.2 Shared-append files (the only files two lanes may both edit)

| File | Merge owner | Rule |
|---|---|---|
| `gangland-features/gangland-turf/src/main/resources/commands.json` | haiku merge step at wave end | Lanes append their entries. The merge step dedups and validates the JSON. |
| `gangland-features/gangland-gang/src/main/resources/commands.json`, `gangland-mail/.../commands.json` | same | same |
| `gangland-features/gangland-turf/.../TurfModuleConfig.java` | orchestrator | Lanes add `@Bean` methods only, and never edit another lane's bean. |
| `gangland-features/gangland-turf/.../TurfModuleFiles.java` / `TurfModuleFileConfig.java` | orchestrator | Register module YAML files only. |
| `gangland-impl/src/main/resources/plugin.yml` | orchestrator | Only W3-W adds `dynmap` to `softdepend`. |
| `documentation/migration-0.14.0.md` | W-E lane per wave | Lanes hand their notes to it in their exit report. |

Every other file has exactly **one owning lane per wave**. The lane cards list them.

### 5.3 Gate that every lane passes (the "test gate")

1. **Red first**: the lane's first commit adds or flips tests that **fail on the pre-fix code**. The lane pastes the
   failing `mvn -q test -pl <module> -am -Dtest=<Class>` output in its exit report. The fix commit turns them green.
   Pinned tests (§4) are flipped, never deleted.
2. JUnit 5 + Mockito per `documentation/TESTING.md`: pure logic first; persistence round-trips over real SQLite through
   the DatabaseBackend SPI (with `@TempDir(cleanup = CleanupMode.NEVER)` + release in `@AfterEach`); recording fakes in
   `support/` for contracts (`TurfMessageContract`, `GangLookupContract`); Mockito only for wide Bukkit interfaces;
   **no pom changes** (TESTING.md §1).
3. `mvn -q test -pl <owned modules> -am` green, then `mvn clean install -DskipTests` across the reactor green.
4. House rules: method braces on their own lines; XSeries for version-drifting enums; `@CustomLog`; no `Bukkit.getLogger`;
   block-style YAML with `Capitalized_Underscore_Separated` keys; `GanglandChatUtil.color` with `&` codes; commands as
   chained `OptionalArgument` with tab-completion and a `commands.json` entry in the jar that owns them; no Paper APIs;
   compiles against Spigot 1.16.5.

### 5.4 Wave gate (after all lanes merge into `gang-turf-territory`)

1. Full `mvn clean package` green (core jar + `target/modules/*.jar`).
2. **opus 5.5 adversarial review** of the wave diff (concurrency, event ordering, exploit paths, module-boundary leaks:
   no module imports `gangland-impl`, turf never imports cops).
3. **fable 5.1 judge**: go/no-go against the wave's exit criteria below.
4. **Smoke** (`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py`; new rows added to its `scenarios.json` by the W-E
   lane): G1 core + gang + mail + turf + civilians, no Citizens → `Runtime modules: ... loaded`, no fault lines,
   `stop` → `onDisabled` per module; G2 = G1 + `/glw reload` twice → no duplicate-task or "already scheduled" warnings (proves
   TF-07/TF-08); G3 (W3) = G1 with and without dynmap → no `reflection.type.missing` spam when dynmap is absent.
   Console-only smoke cannot render a player's chat map, so **manual checks** (map alignment at GUI scale 1-4, hover, click,
   auto-map throttle) go into `documentation/tests/features/turf.md` as a checklist the owner ticks on the test server.
5. Docket rows written. `graphify update . --force` (classes were added). The migration doc section is complete.

### 5.5 W0: Trustworthy foundations

Kickoff: none (lanes are disjoint). All four implementation lanes run **in parallel**.

**W0-A: Turf ownership pipeline** (sonnet 5.5; opus reviews at the gate) · worktree `wt/gt14-w0a-ownership`
- Scope: one owner-change path and one cancel-contest path. NEW `turf/capture/TurfOwnership` with
  `setOwner(turf, Integer newOwner, Cause)` (always persists, always fires `TurfOwnerChangedEvent`) and
  `cancelContest(turf, Reason)` (always fires `TurfCaptureFailedEvent`, `CANCELLED` included). Route every writer through it:
  `CaptureService.complete` (check the new owner exists *before* writing: TF-01, TF-13), `TurfSetOwnerCommand` (TF-04),
  `InactivityReleaseTask` and `TurfIncomeDistributor` orphan release (TF-10), NEW `listener/TurfGangDeleteListener` on
  `GangDeleteEvent` (TF-25: release the gang's turfs, cancel contests, clear garrison/buffs via existing managers),
  `TurfDeleteCommand` (TF-03 residual: cancel before delete). TF-28 closes by construction.
- Owns: `capture/CaptureService.java` (complete/cancel only), NEW `capture/TurfOwnership.java`,
  `command/TurfSetOwnerCommand.java`, `command/TurfDeleteCommand.java`, `task/InactivityReleaseTask.java`,
  `task/TurfIncomeDistributor.java` (the release branch only), NEW `listener/TurfGangDeleteListener.java`.
- Tests: flip `CaptureServiceStartAndCompleteTest` (TF-01); NEW `TurfOwnershipTest` (each cause fires exactly one
  owner-changed event; cancel fires Failed(CANCELLED)); NEW `TurfGangDeleteListenerTest`; extend `TurfDeleteCommandTest`.
- Exit: `grep setOwnerGangId` in turf main shows only `TurfOwnership` (plus the repository load); all listed tests green.

**W0-B: Turf lifecycle and persistence** (sonnet 5.5) · `wt/gt14-w0b-lifecycle`
- Scope: every task-owning turf bean implements `BeanLifecycle` (start in `onInitialize`, cancel in `onShutdown`):
  `TurfLocationTracker`, `TurfIncomeDistributor` (the scheduling part only), `GangPresenceTracker`, `TurfContributionTickTask`,
  `TurfDefenderDeployer`, `ActiveBuffManager`, `TurfBossBarListener` (keep the task handle) (TF-07, TF-08). Remove the explicit
  `initialize()` calls in `TurfModuleConfig` (double-init triage). `TurfLocationTracker.onQuit` wired through a small
  `@ListenerHandler` class (TF-06). The release sweep's first run happens at +5 min, not +24 h (TF-09). `TurfNpcsConfigLoader`
  reloads on `BeanLifecycle` reload (TF-36). `TurfRepository` loads through `Number` (TF-14). Ids never reused: a one-row
  NEW `turf_sequence` table as the high-water mark, loaded in `TurfManager.initialize` (TF-05). `turfsByWorld` becomes a
  `CopyOnWriteArrayList`, and the getter returns an unmodifiable view (TF-34). `ActiveBuffManager.prune` deletes in one
  batch (TF-32). `TurfPowerupManager.remove` reads its in-memory map and purges `pending` (TF-33 + triage).
- Owns: `TurfModuleConfig.java` (edits to existing beans; shared file, orchestrator merges), `task/TurfLocationTracker.java`,
  `task/GangPresenceTracker.java`, `contribution/TurfContributionTickTask.java`, `npc/defender/TurfDefenderDeployer.java`
  (lifecycle only), `powerups/ActiveBuffManager.java`, `listener/TurfBossBarListener.java`, `npc/config/TurfNpcsConfigLoader.java`,
  `database/TurfRepository.java`, NEW `database/TurfSequenceTable.java`, `manager/TurfManager.java`, `npc/TurfPowerupManager.java`.
- Conflict note: `TurfIncomeDistributor` is also touched by W0-A. Split: W0-B edits only the class declaration, start and
  stop; W0-A edits only the release branch inside `distribute`. The orchestrator merges W0-B first.
- Tests: flip `TurfManagerTest` (TF-05); extend `TurfRepositoryTest` (Long/Integer columns round-trip, TF-14);
  `TurfModuleConfigTest` (no double-init); `ActiveBuffManagerTest` (batch delete); `TurfPowerupManagerTest` (pending purged).
- Exit: smoke G2 (reload twice) clean; tests green.

**W0-C: Gang hardening** (sonnet 5.5) · `wt/gt14-w0c-gang`
- Scope: GR-10 (transfer lock expires on its own timer, cleared on quit), GR-12 (`RankManager.clear` resets everything),
  GR-15 (null-safe gangs in `GangMembersDamageListener`), GR-20 (NULL → null, not "null"), GR-28 (null root guard),
  GR-30, GR-32 (delete dead `MemberManager.initializeMemberData`, `Number` casts in `MemberRepository.doLoadAll`), GR-33 (delete
  the no-arg `Gang()` + `generateId`), GR-34 (re-check gang and permission on colour confirm), GR-35 (`hashCode`), GR-36
  (`GangOptionContribution` guards `instanceof Player`), GR-37 (`rank permission add` validates against the known node list
  and tab-completes it), TF-24 (`Gang.isAlly` checks both directions: one fix in `Gang.java` that every caller, including
  `GangMembershipInstaller.gangsAllied` and `CaptureService`, routes through).
- Owns: the listed files under `gangland-features/gangland-gang/**` only.
- Tests: flip `RankManagerTest` (GR-12) and `GangAllianceTest` (GR-35, and the one-directional case for TF-24); NEW
  cases in `MemberManagerTest`/`RankCommandsTest`; a GangRepository round-trip with NULL columns.
- Exit: tests green; `GangModuleTest` command count unchanged (31).

**W0-D: Mail and api hardening** (sonnet 5.5; opus reviews GR-11 concurrency at the gate) · `wt/gt14-w0d-mail`
- Scope: GR-11 (`MailModuleConfig` expiry sweep runs **sync** and is cancelled in the module's disable path), GR-14 (reuse
  `onlineGangMembers` in `GangAllyRequestCommand`/`GangAllyAcceptCommand`), GR-23 (accepting one invite rejects the other
  pending `GANG_INVITE`s; offline invites expire after `Invite_Offline_Expiry_Hours`, 72, in a module YAML), GR-24 (NEW
  `gang invite decline [gang]` → `MailManager.reject`), GR-31 (skip and log a bad mail row instead of aborting the load), GR-27
  (`Settings` `Display_Name_Char` empty → default `*`; a bug fix, no signature change, no api bump). Mail `commands.json`
  drift (`ally accept`, `ally reject`, `invite decline`).
- Owns: `gangland-features/gangland-mail/**`, `gangland-api/.../file/configuration/Settings.java` (that one method).
- Tests: `MailManagerTest` (accept rejects siblings, offline expiry), `GangMailCommandGuardTest` (no NPE for a cache-less
  online player), NEW `MailRepository` bad-row test, NEW `Settings` test for the empty char.
- Exit: tests green; no `start(true)` left in mail.

**W0-E: Docs, triage, docket** (haiku 4.5) · `wt/gt14-w0e-docs` · runs in parallel, merges last
- Scope: the §4.2 triage rows + `build_docket.py`; gang `commands.json` drift (`gang_change_name` → `rename`,
  `gang_ally_remove` → `abandon`, add missing `members`/`balance`/`transfer`/`delete` entries); `documentation/features/gangs.md`
  and `ranks.md` corrections (real `gangland.gang.*` nodes; no inheritance; real `RankManager` API); TF-31 note; CLAUDE.md
  stale lines (local); skeleton `documentation/migration-0.14.0.md`; smoke rows G1/G2 in `scenarios.json`.
- Owns: the files named, docs only.
- Exit: `python build_docket.py` runs; the docket shows the new rows.

W0 exit criteria: the 36 entries are closed with tests; smoke G1/G2 are clean; the opus review finds no path that
changes ownership without `TurfOwnerChangedEvent`.

### 5.6 W1: See the city

**Kickoff commit** (haiku 4.5, on the integration branch, before lanes fork):
root `turf/map/MapCommand` (`/glw map`, empty children), `turf/map/MapPrefsService` (auto/compass/cellBlocks, cleared on
quit), `turf/turf_map.yml` with all L1/L2 keys (block style), registration in `TurfModuleFiles`, and `commands.json`
entries for `map`, `map auto`, `map compass`. One trivial test per class. This lets W1-M and W1-H fork without touching the
same file.

**W1-M: Map core** (sonnet 5.5; **fable reviews the render rules §2.3 before code**) · `wt/gt14-w1m-map`
- Scope: `TurfChunkIndex`, `TerritoryRaster`, `TurfMapRenderer`, the `MapCommand` body, `MapAutoCommand`, `MapAutoTask`
  (`BeanLifecycle`), the turf version counter listener; `TurfManager` create/delete notify the index; the `TurfInfoCommand`
  id/name argument with tab-completion; TF-27 (runtime COOLDOWN derived on load). It also wires W1-H's
  `MapCompassCommand` into `MapCommand` at merge.
- Owns: `turf/map/**` except `TurfCompassTask`/`MapCompassCommand`/placeholders, `manager/TurfManager.java`,
  `command/TurfInfoCommand.java`, `data/TurfRuntimeState.java`.
- Tests: `TerritoryRasterTest` (pure: majority rule, the visibility guarantee for a tiny turf, legend letter allocation with
  collisions and `I` skipped, relation colours for own/ally/rival/gangless, gold on CONTESTING, fixed north, odd window,
  player cell centred); `TurfChunkIndexTest` (rebuild on create/delete, a turf spanning chunk borders);
  `TurfMapRendererTest` (run-length merge, click command text, owner-only garrison hover); `MapAutoTaskTest` (cell-change
  gate, min interval, hash-diff on version bump).
- Exit: a manual screenshot at GUI scale 2 shows aligned columns (checklist item). All tests green.

**W1-H: HUD (placeholders + compass)** (sonnet 5.5) · `wt/gt14-w1h-hud`
- Scope: NEW `turf/placeholder/TurfPlaceholderContribution` (the `%gangland_turf_*%` tokens in §2.2), O(1) off
  `TurfLocationTracker.getPlayerTurfCache()` with a nearest-turf cache per player per second; NEW `TurfCompassTask`
  (`BeanLifecycle`, `ActionBarManager.sendBackground`, skips unchanged strings); NEW `MapCompassCommand`.
- Owns: `turf/placeholder/**`, `turf/map/TurfCompassTask.java`, `turf/map/MapCompassCommand.java`.
- Tests: `TurfPlaceholderContributionTest` (null for unknown params, each token for inside/outside/gangless), a
  `TurfCompassTask` nearest-point + 8-way bearing test (pure helper).
- Exit: tokens documented in `documentation/features/turf.md` (via W1-E).

**W1-T: Turf clarity and admin** (sonnet 5.5) · `wt/gt14-w1t-turf`
- Scope: the teaching text. `TurfActionBarListener` enter titles for unclaimed/owned/raidable/protected (with the reason);
  `TurfCaptureFeedbackListener` wording; the `TurfCommand` root for gang members shows "Your hoods: 3 · income $300 / 10 min ·
  nearest unclaimed: Docks 120 m NE [map]". Admin: TF-21 create limits (`Min_Side_Blocks` 16, `Max_Side_Blocks` 512)
  and `Snap_To_Chunks` (new turfs only, D3) in `TurfCreateCommand` + `TurfShowCommand` preview via NEW
  `CuboidRegion.snappedToChunks()`; TF-29 (`Selection` keeps the other corner on world change; duplicate-name check);
  TF-39 NEW `turf rename <name>`; TF-30 one namespace (admin subcommands check `gangland.turf.admin` through one helper;
  `list/info/status/select/show` stay player-safe; `tp` becomes admin-only); TF-16 (`y` bounds from
  `World.getMinHeight/getMaxHeight` via a 1.16-safe reflective fallback to 0/256); TF-23 for wand/status strings.
- Owns: `listener/TurfActionBarListener.java`, `listener/TurfCaptureFeedbackListener.java`, `command/TurfCommand.java`,
  `command/TurfCreateCommand.java`, `command/TurfShowCommand.java`, `command/TurfTpCommand.java`, NEW `command/TurfRenameCommand.java`,
  `selection/**`, `data/CuboidRegion.java`, `listener/WandListener.java`, `command/TurfWandCommand.java`,
  `command/TurfStatusCommand.java`, `listener/contribution/TurfContributionListener.java`, NEW `turf/turf_messages.yml`.
- Tests: `CuboidRegionTest` (snap maths incl. negatives: `min & ~15`, `max | 15`); a NEW `TurfCreateCommandTest` (limits,
  duplicate name); a `Selection` world-change test; a namespace helper test.

**W1-G: Gang onboarding** (sonnet 5.5) · `wt/gt14-w1g-gang`
- Scope: the `GangCommand.onExecute` onboarding screen for gangless players; NEW `GangListCommand` (`gang list [page]`, clickable
  names) and `GangInfoCommand` (`gang info <name|id>`, public card: members, level, allies; turf counts are **not** on this card,
  because `/glw turf top` and the `/glw turf` root already show them, and `CommandContribution` attaches sub-arguments,
  not output lines); drop the `list` alias from `members`;
  GR-16 (`alliance_stat.yml` click → `/glw gang info %ally_id%`); GR-17 (remove the `gang_stat` button in `gang_info.yml` and
  its `GameplayConfig` registration); GR-29 name rules (3-16, `[A-Za-z0-9_]`, no `&`, rename too, configurable in NEW
  `gang/gang_rules.yml`); GR-13 + GR-21 seed path: on an **empty** rank table seed `member → officer → owner` with
  `officer` = deposit, invite, ally, `gangland.turf.upgrade`; `member` = deposit; `RankRepository` seeding uses max-id+1; a
  missing head name logs and creates it rather than a detached root; `leave` uses `confirm`.
- Owns: `gangland-gang/**` command/rank files named, `gangland-impl/src/main/resources/inventory/{gang_info,alliance_stat}.yml`,
  `gang_stat.yml` (deleted), `gangland-impl/.../config/GameplayConfig.java` (one line).
- Tests: flip `RankManagerTest` (GR-13); `RankRepositorySpiTest` seeding with gaps (GR-21); `GangCommandTest` onboarding
  for gangless vs member; name-rule table test; `GangModuleTest` count updated (31 → 33).

**W1-E: Docs + merges** (haiku 4.5): the `commands.json` merge, the migration doc (new commands and permissions, the `gang list`
change, `tp` now admin, snap flag), `documentation/features/turf.md` "Map" and "Placeholders" sections, the manual map checklist.

W1 exit: a player with no guidance can open `/glw map`, read who owns what, click a turf and read its card; a gangless
player gets the onboarding screen; fresh install members can deposit; 12 docket entries closed.

### 5.7 W2: Fair fights

**Kickoff commit** (haiku 4.5 + one sonnet check):
1. `TurfDefenderDeployer.aliveCount(int turfId)` signature returning today's count, with a red test for the per-turf count.
2. NEW event `events/TurfDefenderDownEvent(turf, remaining)`.
3. NEW `turf/turf_rules.yml` + `capture/TurfRules` record (`Raid_Rule`, `Offline_Duration_Multiplier`, `Guard_Weight_Cap`,
   `Max_Turfs_Per_Gang`, `Turfs_Per_Gang_Level`) and NEW `turf/turf_alerts.yml`, both registered.
4. **Sandbox acceptance** (sonnet, test server) of today's faction-alert behaviour for `turf_defender`, per cnc-ideas §5.2.
   If it fails, W2-S's first task is to fix it.

Then four lanes run **in parallel** (opus count: W2-R implementer 1 + gate reviewer 1 = 2 ≤ 3).

**W2-R: Capture rules** (**opus 5.5**) · `wt/gt14-w2r-capture`
- Scope: `Raid_Rule` = `ALWAYS | OWNERS_ONLINE | OWNERS_OFFLINE` in `isCapturable` (default per D1); the offline multiplier on
  the owned-contest rate; living guards in `classify` (capped); the **anti-alt rule**: an owned contest starts when one
  challenger gang has a **strict plurality** of challengers inside, instead of "exactly one gang"; TF-12 (`Abandon_Grace_Seconds`
  honoured on the owned path); TF-11 (delete the dead `exclude`); TF-26 kept and re-documented; the per-gang turf cap (D9) checked
  in `tickIdle` through NEW `TurfRules.canOwnMore(gang)`, so a capped gang's contest never starts, with a belt-and-braces
  re-check at `complete` in case the cap changed mid-contest. The blocked reason is shown on enter in
  `TurfCaptureFeedbackListener`. Cut `reinforced_defense` from the default `turf_powerups.yml` and keep the loader tolerant
  (TF-02 defense half).
- Owns: `capture/CaptureService.java`, `capture/CaptureSettings.java`, `capture/TurfRules.java`, `resources/turf/turf_powerups.yml`,
  `powerups/PowerupRegistryLoader.java` (tolerance), `listener/TurfCaptureFeedbackListener.java` (in W2; W1-T owned it in W1).
- Tests: flip `CaptureServiceOwnedTurfTest` (TF-12); NEW cases in `CaptureServiceOwnedTurfTest` (raid rule matrix × owner
  online/offline; multiplier; guards slow and reverse progress; cap stops at 4; plurality start; alt blocker needs matching
  headcount); `CaptureServiceUnclaimedTurfTest` unchanged or green; `PowerupRegistryTest` (old file with `reinforced_defense` loads
  and warns).

**W2-S: Garrison squad** (sonnet 5.5; opus reviews at the gate) · `wt/gt14-w2s-garrison`
- Scope: T2 (per-turf `TurfGarrisonSquad`, posts, leash, UUID index = TF-35), T3 (waves, `Max_Alive`, cooldown, out-of-sight
  spawn, refund per D10, consume-after-spawn), T13 (stand-down), and firing `TurfDefenderDownEvent`. **Civilians:**
  `CivilianService.alertFaction`/`recruit` honour `victim.getFactionSquads()`. Quartermaster: TF-17 (price in
  `turf_npcs.yml` `Garrison.Price`), wire `GARRISON_DISCOUNT` (TF-02 discount half), TF-19 (re-validate owner/ally/turf/gang and
  permission on every purchase click), wire `gangland.turf.upgrade` via `GangPermissions.allows` in `TurfPowerupOpenContractImpl`
  and on purchase, TF-23 (Quartermaster panel strings into `turf_messages.yml`; W1 created the file and W2-S appends to it), TF-38
  (configured name passed), TF-18 (catalogue pages beyond 9).
- Owns: `npc/defender/**`, NEW `npc/defender/TurfGarrisonSquad.java`, `listener/powerups/GarrisonDeployListener.java`,
  `npc/view/**`, `npc/config/TurfPowerupOpenContractImpl.java`, `command/TurfPowerupNpcCommand.java`, `resources/turf/turf_npcs.yml`,
  `gangland-features/gangland-civilians/.../npc/CivilianService.java` (alertFaction/recruit only).
- Tests: `TurfDefenderDeployerTest` (waves, cap, refund, leash decision as a pure function of positions, UUID lookup, no consume
  without Citizens); NEW civilians test: a hit guard with a custom `FactionSquads` stays in the turf squad; `GarrisonManagerTest`
  (refund); a purchase-revalidation test with a recording fake.
- Exit: manual raid on the test server with Citizens shows guards at posts, returning after a chase, and waves topping up.

**W2-N: Notifications** (sonnet 5.5) · `wt/gt14-w2n-alerts`
- Scope: T1 lines in `TurfCaptureNotifier` (start with head-count and side, guard-down, last stand, held/lost incl. failure);
  NEW `turf_event_log` table + `TurfEventLog` (capture won/lost, raid held, released, income per interval aggregated per gang,
  pruned after 7 days); NEW `listener/TurfDigestListener` (on join: up to 5 lines "While you were away ..." since the player's last
  quit, clickable `[map]`); `TurfIncomeDistributor` pays a message to online members (one line per gang per interval) and caps
  the multiplier product at `Max_Income_Multiplier` (3.0) (TF-37); the `TurfCommand` root shows the last 3 log lines.
- Owns: `listener/TurfCaptureNotifier.java`, NEW `log/**`, NEW `listener/TurfDigestListener.java`, `task/TurfIncomeDistributor.java`
  (distribute body), `command/TurfCommand.java`, `resources/turf/turf_alerts.yml`.
- Tests: NEW `TurfEventLogTest` (SQLite round-trip, prune, per-gang aggregation); NEW `TurfCaptureNotifierTest` (audience =
  online owner members anywhere, throttle, failure now notifies); a `TurfIncomeDistributor` cap test; a digest window test.

**W2-P: Progression and gang social** (sonnet 5.5) · `wt/gt14-w2p-progress`
- Scope: contribution split (D5). NEW turf-owned `turf_member_score` table; `TurfContributionTickTask`/`TurfContributionListener`
  write there, and `Member.increaseContribution` stays money-only; existing values stay as they are (migration note).
  Gang XP (GR-19, D6): NEW `GangManager.addExperience(gang, amount)` fires `GangLevelUpEvent` on level change; turf calls it on
  capture (`Capture_Xp`) and per income tick held (`Hold_Xp`). Delete `GangBountyEvent` + `GangBountyMessageListener` (D7); the
  `gang_bounty*` placeholders return `0`. NEW `/glw turf top` (gangs by hoods held, then level; top 5 members by turf score).
  NEW `gang chat [msg]` + toggle listener (`AsyncPlayerChatEvent`, recipients filtered via `GangMembership`, no Bukkit world
  calls off-thread). `Gang.State` (D8): `gang join <name>` for OPEN gangs and `gang state open|invite`. `Max_Members` (D9) in
  `gang/gang_rules.yml`. Delete the dangling `gangland.turf.capture`/`.contribute` fixtures from `RankPermissionApplierTest`
  (`upgrade` is wired by W2-S).
- Owns: `gangland-gang/**` (new commands, `GangManager.addExperience`, the bounty deletions, chat listener,
  `RankPermissionApplierTest`), turf `contribution/**` and `listener/contribution/TurfContributionListener.java`,
  NEW `turf/score/**`, NEW `command/TurfTopCommand.java`.
- Tests: NEW `TurfMemberScoreRepositoryTest`; the `GangDeleteCommandTest` payout ignores turf score; a
  `GangManager.addExperience` level-up event test; a gang chat recipient filter test (pure); a join/state test; the
  `GangModuleTest` count.

**W2-E: Docs + merges** (haiku 4.5): `commands.json`, the migration doc (**raid rule behaviour change**, contribution split,
new tables `turf_sequence`/`turf_event_log`/`turf_member_score`, new YAMLs), the `turf.md`/`gangs.md` "How turf wars work"
page written from §1.2, `_es` twins of the new YAMLs (empty values).

W2 exit: in a manual raid, owners get an alert anywhere, guards hold the bar back, a failed raid notifies both sides, and a
logged-out owner sees the digest on join; the alt-blocker exploit is covered by a red→green test; 13 docket entries closed.

### 5.8 W3: Map everywhere (owner picks lanes; all parallel, sonnet 5.5)

| Lane | Scope | Owns | Tests / exit |
|---|---|---|---|
| W3-Z zoom `wt/gt14-w3z-zoom` | `/glw map zoom in\|out`, cellBlocks 8/16/32/64 with a window that keeps the world span | `turf/map/MapZoomCommand.java`, `MapPrefsService` (cell field) | raster tests at each cell size |
| W3-B borders `wt/gt14-w3b-border` | Clip `TurfVisualization` to 40 blocks around the viewer, adaptive step, then player `/glw map border` (10 s, `XParticle` dust in the relation colour) | `task/TurfVisualization.java`, NEW `turf/map/MapBorderCommand.java` | a pure test that the point count is bounded for a 512×512 turf; closes the TF-21 visualization half + the cost triage |
| W3-W dynmap `wt/gt14-w3w-dynmap` | `DynmapTurfLayer` synced on the version events + full rebuild on API enable | NEW `turf/map/web/**`, core `plugin.yml` softdepend (orchestrator) | a marker-sync test with a fake marker API; smoke G3 |
| W3-I map item (optional) `wt/gt14-w3i-item` | `/glw map item`, contextual renderer, one shared `MapView`, raster-hash cache | NEW `turf/map/item/**` | a cache-hit test; a manual render-cost check with 10 holders |

### 5.9 W4: Garrison roles (starts after H13 merges to master and master is merged into `gang-turf-territory`)

Pin becomes Keystone **1.14.0**. Opus count: W4-R 1 + gate reviewer 1.

| Lane | Tier | Scope | Owns | Test gate |
|---|---|---|---|---|
| W4-R roles `wt/gt14-w4r-roles` | **opus 5.5** | T4: NEW `gangland-api/.../npc/NpcRole` record (api **2.1**, additive, documented in `documentation/gangland-api.md`); `CopRole` → adapter; civilians `AI.Combat.Roles` overlay with defaults; turf `Garrison.Composition`; shield guard in `CivilianDamageListener`. Modules using it set `Host_Api: 2.1`. No Medic. | api `npc/NpcRole.java`, cops `config/CopRole.java` (adapter), civilians type-config overlay + `CivilianDamageListener`, turf composition | `NpcRoleTest` (overlay, band clamp), `CopRole` regression tests unchanged green, a civilians overlay default test |
| W4-C callsigns + bars `wt/gt14-w4c-names` | sonnet 5.5 | T6 + T7 | civilians `FactionVoice`, `civilian_messages.yml` format, turf deployer tag step | a format fallback test; a manual bar/callsign layout check |
| W4-D morale, bounty, stuck `wt/gt14-w4d-tactics` | sonnet 5.5 | T9, T10, T12 | NEW turf `npc/defender/TurfDefenderMoneyDropSource`, deployer tick additions | a drop-funding test (no mint; ally kill = 0), a recycle decision test |

Conflict note: W4-R and W4-C both touch civilians config. W4-R owns the type-config classes, and W4-C owns only
`CivilianService.FactionVoice` + `civilian_messages*.yml`. The orchestrator merges W4-R first.

**Keystone lane (K), not scheduled:** Keystone **1.15.0** only when a second consumer appears for (a) a hold-post +
leash primitive on `AbstractNpc` (turf W2 builds it locally in `TurfGarrisonSquad`), or (b) a region-relative
out-of-sight spawn helper on `EntitySpawner`. If triggered: its own branch `phase-h14-*`, a phase doc, a `mvn clean install`
in Keystone, then a pin bump PR in Gangland. Never a local fork.

### 5.10 Lane timeline (parallelism at a glance)

```
W0  [A sonnet][B sonnet][C sonnet][D sonnet][E haiku]  → gate(opus review, fable judge, smoke G1/G2)
W1  kickoff(haiku) → [M sonnet][H sonnet][T sonnet][G sonnet][E haiku] → gate
W2  kickoff(haiku+sonnet sandbox) → [R OPUS][S sonnet][N sonnet][P sonnet][E haiku] → gate
W3  à la carte [Z][B][W][I] sonnet → gate (smoke G3)
      ... H13 (0.13.0) merges to master → merge master into gang-turf-territory ...
W4  [R OPUS][C sonnet][D sonnet] → gate → merge gang-turf-territory → master as 0.14.0
```

---

## 6. Open decisions for the owner

**D1. Raid rule (load-bearing; decide before W2).** Today an owned turf is raidable only after the whole owner gang has been
offline for 10+ minutes (`CaptureService.isCapturable`, verified L112-127). The alerts, guards and "come defend" loop mostly fire
at an empty gang.
- A `OWNERS_OFFLINE` (today): offline raiding only.
- B `OWNERS_ONLINE`: raidable only while ≥ 1 owner member is online. Fair fights, but a gang can hide by logging out.
- C **`ALWAYS` + offline slowdown**: raidable any time after cooldown. A fully offline gang's turf takes `Offline_Duration_Multiplier`
  (2.0) longer, and guards + the digest cover it.
- D Scheduled war windows (a server-set time of day).
- **Recommend C**, shipped as the `Raid_Rule` enum in `turf_rules.yml` so A and B stay one config line away. It removes the one
  invisible rule and makes alerts matter.

**D2. Map entry point.** A `/glw map` (root, turf-owned) · B `/glw turf map` · C both. **Recommend A** (§2.1).

**D3. Snap new turfs to chunks.** A Off · B **On for new turfs only** · C On + migrate existing (rewrites regions). **Recommend B**:
new maps are crisp, and existing turfs are untouched.

**D4. Claim model.** A **Keep admin-drawn hoods; the map rasterizes them** · B Add player chunk claims as a second mode later · C Replace
with chunk claims. **Recommend A**. B is a separate product with its own economy.

**D5. Contribution.** A Keep one mixed number · B **Split: money share (gang) vs turf score (turf)** · C Drop the money share and pay
out equally. **Recommend B**. It closes the disband-payout exploit.

**D6. Gang level.** A **Wire it: XP from captures and holding; each level +1 turf cap; shown on `turf top`** · B Cosmetic only · C Delete
level. **Recommend A**. It gives the loop a long-term goal at the cost of one method and two knobs.

**D7. Gang bounty.** A **Cut (delete dead events; placeholders return 0)** · B Wire posting a bounty on a gang · C Leave inert.
**Recommend A**.

**D8. Open gangs.** A **Wire `OPEN`/`INVITE` with `gang join`; drop `CLOSE`** · B Delete `Gang.State` · C Leave inert.
**Recommend A**. It is the easiest path into a gang for new players.

**D9. Limits.** Turf cap: A Off · B **Flat base `Max_Turfs_Per_Gang: 3` + `Turfs_Per_Gang_Level: 1`** · C Scaled by members.
Member cap: A **Off (`Max_Members: 0`)** · B 20. **Recommend turf B, members A**. The turf cap stops solo-gang hoarding, and member
caps are a server-culture call.

**D10. Unused guards when a raid ends.** A **Refund to stock** · B Spent. **Recommend A** (defence shouldn't punish the defender for winning fast).

**D11. Guard bounty (W4).** A **Funded from the owner's purchase price (no mint)** · B Minted · C None. **Recommend A**.

**D12. Turf heat → cops (T5).** A **Not now; revisit after W2 playtests** · B On, policed districts only · C Never. **Recommend A**.

**D13. HUD defaults.** Auto-map and compass: A **Both off, opt-in** · B Compass on. **Recommend A** until a manual test-server
check confirms the compass does not fight Bartizan's ammo action bar.

**D14. Web map privacy (W3).** A **Show owner, hide income and guards** · B Show all · C Names only. **Recommend A**.

**D15. Ranks.** A **Keep the global tree + seed a 3-rank preset** · B Per-gang rank trees (schema change) · C Replace the tree with role flags
(GR-12/13/21/22/28 become migrations). **Recommend A**.

---

## 7. Risks, anti-exploit, out of scope

### 7.1 Risks

| # | Risk | Mitigation |
|---|---|---|
| R1 | The raid-rule change (D1 = C) is a behaviour change for live servers. | A new `turf_rules.yml` file; the migration doc leads with it; `OWNERS_OFFLINE` restores the old game in one line. |
| R2 | Chat font alignment differs by client, GUI scale and resource pack. | 6 px glyph class only; plain-text legend; manual checklist at scales 1-4; `Width`/`Height` config. |
| R3 | Bedrock/Geyser players get no hover. | The legend carries every fact the hover has except per-turf details, which the click gives. |
| R4 | Auto-map floods chat or costs TPS. | Cell-change gate, 2 s minimum, raster hash on version bumps, `TurfChunkIndex`, default off. |
| R5 | The compass clobbers Bartizan's action-bar HUD. | `sendBackground` priority; default off (D13); a **manual** checklist item with Bartizan installed (console smoke cannot see a player HUD). |
| R6 | smoke.py drives a **Paper** test server (its README), so it cannot prove Spigot-only behaviour. | The Spigot 1.16.5 compile floor in the pom is the API gate; no `io.papermc` imports (review checklist). Owner runs one manual Spigot boot per release. |
| R7 | Worktree merge conflicts. | The single-owner file table; shared-append files merged by the orchestrator; kickoff commits for shared signatures. |
| R8 | H13 slips. | Only W4 waits. W0-W3 ship value on their own. |
| R9 | Root node `gangland.command.map` defaults to OP. | W1-M checks the framework default; otherwise the migration doc grants it to players. |
| R10 | The civilians `alertFaction` change alters cops/civilian faction behaviour. | The change applies only when a custom `FactionSquads` is set (turf guards). Cops tests run in the W2 gate. |
| R11 | dynmap thread contract unverified. | All marker calls on the main thread; the fake-API test; smoke G3. |
| R12 | Map item render cost. | Optional lane, raster-hash cache, measured before shipping. |

### 7.2 Anti-exploit

| Exploit | Today | Fix (lane) |
|---|---|---|
| Alt in a second gang blocks every raid start ("exactly one challenger") | open | Strict plurality start (W2-R), red→green test |
| Farm turf points, then disband to claim bank share | open | Contribution split (W2-P) |
| Any recruit spends the gang bank at the Quartermaster | open (dangling node) | `gangland.turf.upgrade` + re-validation per click (W2-S, TF-19) |
| Stale Quartermaster session buys after kick/loss | open (TF-19) | Re-check on every click (W2-S) |
| Uncapped income multiplier stacking | open (TF-37) | `Max_Income_Multiplier` (W2-N) |
| Solo gang hoards unlimited turf | open | Turf cap (W2-R, D9) |
| Huge garrison makes a turf untakeable | would be new with T4 | `Guard_Weight_Cap` (W2-R) |
| Farming your own/ally garrison for bounty | would be new with T9 | Owner-funded drops, no drop for owner/ally kills (W4-D) |
| Map reveals defence strength | would be new | Garrison hover for owners only; web hides guards (D14) |
| Capturing into a disbanded gang | open (TF-01) | W0-A |
| Name spoofing with colour codes (`&4Admin`) | open (GR-29) | W1-G |
| Mega-alliances (allies count as defenders) | open, design | Watch after W2; a `Max_Allies` knob is a one-liner if needed (not built now) |

### 7.3 Out of scope (explicit)

Player-claimable chunks (D4-C); per-gang rank trees (D15-B); declared wars / war windows (D1-D); turf heat and cops joining
gang fights (T5, D12); neighbour/ally responders (T8); player ping/man-down callouts (T11); Medic field care (wait for cops
`GL-CARE`); BlueMap, squaremap, Pl3xMap; turf holograms; the `TurfMapLayer` cops overlay seam; gang bank ledger UI; gang home (use
gang waypoints); the Spanish turf strings (TF-22, i18n CM-15); income column type (TF-15); `RankParent` naming (GR-22 half);
promoting the map painter or post/leash to Keystone before a second consumer; any Paper API; any change to `GangMembership`'s
existing three methods (additive only, and none needed).
