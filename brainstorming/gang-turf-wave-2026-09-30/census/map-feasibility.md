# Territory map - feasibility study (gang/turf wave, 2026-09-30)

Scope: a Factions-`/f map`-style territory map for Gangland, then expanded. Planning only; nothing here is code.
Branch master @ ff9d813f (rev 0.12.0). Compile floor Spigot 1.16.5, Java release 17, Spigot only.
Graph freshness: `graphify-out/graph.json` (23:08) is newer than the latest commit (23:05), so graph answers below are current.

Legend for claims: **[verified]** = read in this repo or checked against the 1.16.5 spigot-api jar with `javap`;
**[web]** = from web search/fetch this session; **[unverified]** = stated from memory or not confirmable, must be checked on the test server before it is designed around.

---

## 0. Executive summary

- Build the map **inside the turf module**, on top of a new per-world chunk index, rendering the existing admin-drawn
  cuboid turfs. Do not switch to player-claimable chunks (section 8): that is a rewrite of capture, garrison and
  powerup ownership, not a map feature.
- **MVP** = `/glw turf map` (chat grid with hover/click, compass header, legend) + `/glw turf map on|off` (auto re-send
  on cell change, piggybacking the existing 1 Hz `TurfLocationTracker`). Everything it needs compiles at the 1.16.5
  floor today; the component API is already used in-repo (`TurfListCommand.sendRow`).
- **Cheap, high-value second layer**: turf `PlaceholderContribution` tokens (Plaque sidebar gets a turf line with zero
  Plaque changes) + an action-bar direction line via `ActionBarManager.sendBackground`.
- **Third**: dynmap area markers (works on Spigot, cheapest web map), then BlueMap behind the same seam. squaremap and
  Pl3xMap are Paper-only and fall outside the platform rule.
- **Optional/later**: in-hand map item (works at 1.16.5, medium effort, modest value), turf-centre hologram, and a
  clipped, borders-only variant of `TurfVisualization` (the current one has a real cost problem, section 6).
- Two design constraints found in the code that shape everything: (a) `Gang.color` cannot distinguish gangs (duplicate
  chat codes, section 1.4); (b) turfs are arbitrary X/Z rectangles, not chunk-aligned, so a chunk grid is lossy
  (section 1.3). The recommended answer to (b) is configurable cell size + optional chunk snapping at creation.

---

## 1. Surface 1 - chat ASCII chunk-grid map (Factions `/f map` style)

### 1.1 What Factions does (behaviour to match) [web]
`/f map` prints a grid of chunk-sized cells around the player; each faction gets a symbol, a key at the bottom maps
symbols to faction names, and `/f map on|off` re-sends it every time the player walks into a different chunk. Sources:
[Server Guides](https://www.performium.net/Community/ServerGuides/Guide/29/Factions-Command-Guide-and-Instructions),
[Minecraft Forum thread](https://www.minecraftforum.net/forums/minecraft-java-edition/discussion/2505051-how-to-read-f-map-in-factions).
Factions chat maps are static text: they cannot be updated in place, every refresh is a new block of chat lines.

### 1.2 API availability at 1.16.5 [verified]
- `net.md_5.bungee.api.chat.{ComponentBuilder, TextComponent, HoverEvent, ClickEvent}` and
  `net.md_5.bungee.api.chat.hover.content.Text` (constructors `Text(String)` and `Text(BaseComponent[])`) are in the
  1.16.5 spigot-api jar. `Player.spigot().sendMessage(BaseComponent...)` and `sendMessage(ChatMessageType, ...)` exist.
- The repo already compiles exactly this at the floor: `gangland-features/gangland-turf/.../command/TurfListCommand.java`
  `sendRow` builds `new ComponentBuilder(...).event(new ClickEvent(RUN_COMMAND, ...)).event(new HoverEvent(SHOW_TEXT, new Text(hover))).create()`
  and sends with `sender.spigot().sendMessage(...)`. So hover/click on every cell is proven, not hypothetical.
- Hex colour: `net.md_5.bungee.api.ChatColor.of(String|java.awt.Color)` exists at 1.16.5 (clients below 1.16 would
  downgrade; the project floor is 1.16, so this is usable but not needed for MVP).
- One component per cell means 41 x 9 = 369 components per send. Chat packets handle this, but keep width modest and
  merge adjacent cells that share colour, glyph and hover (a run-length step) - most of a real map is long runs of
  wilderness. [unverified] exact packet-size ceiling; run-length merge keeps it far from any plausible limit.
- Bedrock/Geyser clients do not show hover text [unverified] - so the legend must carry the same info in plain text
  (it does in the layout below); hover is a bonus, never the only carrier.

### 1.3 Rasterizing cuboid turfs onto cells
Verified geometry facts:
- `CuboidRegion` (`turf/data/CuboidRegion.java` L14) is X/Z-only, inclusive block coords, normalised min<=max; Y is
  ignored (bedrock to sky). `contains(Location)` and `overlaps(CuboidRegion)` exist.
- `TurfManager.findConflict` (L104) rejects overlap at creation, so **at most one turf owns any block**, but **two turfs
  can share a chunk** whenever a boundary is not chunk-aligned. `Region` is an interface, so a non-rectangular shape
  could arrive later; the rasterizer should sample through `Region.contains`-style logic, not `CuboidRegion` fields, if
  that door is to stay open (MVP can use the rectangle fast path).
- `TurfManager.findAt` (L~95) is a **linear scan** of `getTurfsInWorld(world)` per call.

Rasterization algorithm (proposed, pure, unit-testable, no Bukkit types in the core):
1. Cell size `S` blocks (config `Cell_Blocks`, default 16 so cells are chunks; also 8/32/64 for zoom - section 1.6).
2. For a viewer at block `(px, pz)` with a `W x H` window (both odd), cell `(i, j)` covers
   `x in [ox + i*S, ox + i*S + S - 1]`, likewise z, with the origin chosen so the centre cell contains the player.
3. For each cell, ask the **chunk index** (below) for the turfs whose bounding rectangle intersects the cell; compute
   intersection area / `S*S` per turf; pick the turf with the largest coverage as the cell's turf, remember the others
   for the hover text.
4. Coverage >= 75 % -> "full" glyph (upper case), otherwise "edge" glyph (lower case). Simple, readable, and it makes
   the partial-coverage problem visible instead of hiding it. If the owners of the top-two turfs in a cell differ and the
   runner-up has >= 25 %, mark the hover with "Also here: ..." (do not invent a third glyph).
5. Cell state (drives the colour/overlay, in this priority): `CONTESTING` -> `!` overlay (gold);
   `COOLDOWN` -> keep glyph, hover shows time left; otherwise relation colour (below). Derived from
   `TurfRuntimeState.getState()` (`IDLE/CONTESTING/COOLDOWN`, `data/TurfRuntimeState.java`) and, for an unclaimed turf,
   `CapturePhase` (`CLAIM` vs `CONSOLIDATE`) for the hover wording (same branching as `TurfActionBarListener.onEnter`).
6. Relation colour, viewer-relative: **own gang** green, **ally** aqua, **enemy** red, **unclaimed turf** gray `-`,
   **no turf (wilderness)** dark-gray `.`. "Enemy" = any owned turf whose gang is neither the viewer's nor an ally:
   there is no explicit enemy/war relation in the code (`Gang.isAlly(Gang)` is the only relation; `Gang.getAllies()`
   returns `GangAlliance(gang, ally, since)`).
7. Viewer's gang comes from `UserLookupContract.findByPlayer(p).getGangId()` (same pattern as `TurfBossBarListener`).
   Gang names for the legend go through `GangDisplayNameResolver.resolve(Gang)` (the shared null-safe resolver).

**Chunk index (NEW `TurfChunkIndex`)**: `Map<String world, Map<Long chunkKey, List<Turf>>>`, rebuilt for the one
world on `TurfManager.create/delete` and on any region change. It gives the rasterizer O(turfs touching this chunk)
per cell instead of O(all turfs in world). Honest sizing: a single 41 x 9 send against 50 turfs is ~18 k rectangle
tests (well under a millisecond), so the index is **not** needed for a manual `/glw turf map`. It earns its keep for
auto-map (1 Hz x N players) and for the map-item renderer (per tick). Incidental win: `findAt` can use it too - note
it, but do not scope-creep the map wave into rewriting `findAt`.

**Raster cache**: cache the per-world cell raster (`state + owner + turf id` per cell) keyed by a world "turf version"
counter bumped on the existing events: `TurfOwnerChangedEvent`, `TurfCapturedEvent`, `TurfCaptureStartEvent`,
`TurfCaptureFailedEvent` (all in `turf/events/`) plus create/delete. Auto-map then re-sends only when the viewer's
cell changed **or** the version bumped and the raster of their window actually differs (hash compare). This stops
capture-progress ticks from spamming chat.

### 1.4 Colours: the gang colour data cannot be the primary key
- `Gang.color` is a `String` holding a Keystone `Color` enum name (`Gang.java` field L40, default
  `Color.LIGHT_BLUE.name()` L75), set from `GangColorCommand` (gangland-gang, `command/sub/gang/GangColorCommand.java` L35)
  which enumerates `Color.values()`.
- `org.luckyraven.keystone.color.Color` has 16 entries but **only 14 distinct chat codes**: `BROWN` and `ORANGE` are both
  `&6`; `PURPLE` and `MAGENTA` are both `&5`. `BLACK` (`&0`) and `GRAY` (`&8`) are near-invisible on the default chat
  background. Two neighbouring gangs can therefore be indistinguishable on a colour-only map, and colour-blind players
  fare worse. (`Color.getBukkitColor()` gives a distinct `org.bukkit.Color`, so the *map item* and *web maps* do not
  have this problem - only chat does.)
- Design consequence (name it in the plan): **glyph carries identity, colour carries relation.** Each gang visible in
  the window gets a letter (first letter of its resolved display name, collision -> next unused letter, allocated in
  ascending gang-id order so the assignment is stable for a given set of visible gangs). The legend maps
  letter -> gang name. An opt-in `Colour_Mode: GANG` uses the gang's chat colour (with the duplicate/legibility caveat
  documented) for servers that prefer it.

### 1.5 Header, compass, facing, legend
- Bukkit yaw: 0 = south (+Z), 90 = west (-X), 180 = north (-Z), 270 = east (+X). Rows run north (top) to south
  (bottom) and columns west to east, i.e. a **fixed-north** map (Factions style; a rotating map is confusing in chat).
  The player cell shows a facing arrow (8-way from yaw) or `+` as a font-safe fallback (`Player_Glyph` setting).
- Header line: `Turf map - <world> (x, z) facing NE` plus a small compass rose. Legend below: glyph, gang name, relation,
  turf count in window; then the fixed keys (`-` unclaimed, `.` wilderness, `!` contested, lowercase = partial).
- Font caveat: Minecraft chat is a proportional font. Do not promise pixel alignment; stay in one glyph width class
  (ASCII letters plus `-`, `.`, `!`, `^`). `[unverified]` whether the arrow glyphs (U+2190 range) are one width on the
  vanilla font; that is why `+`/`^` is the default.

### 1.6 Interaction, zoom, auto map
- Click a cell: `RUN_COMMAND` `/glw turf info <id>` (read-only, safe) for players; admins additionally get
  `/glw turf tp <id>` in the hover text. `TurfSelectCommand` / `TurfSelectionResolver` already accept an id, so no new
  target plumbing.
- Hover text (per cell): turf display name, owner (or Unclaimed), state (Idle / Contested phase and progress % /
  Cooldown time left), income (`Turf.getIncomeAmount()` and `Settings.getTurfIncomeIntervalMinutes()` as in
  `TurfInfoCommand.renderInfo`), garrison/buff presence (via `GarrisonManager` / `ActiveBuffManager`, already injected
  into `TurfCommand`), cell coordinates.
- **Zoom** is the real expansion over Factions: turfs are arbitrary sizes, so add `/glw turf map zoom in|out|<blocks>`
  cycling `Cell_Blocks` through 8/16/32/64 with a matching window so the world span stays useful. A chunk-locked
  Factions map is a poor fit for a 40 x 40 block hood.
- **Auto map**: a per-player toggle held in a `Map<UUID, MapPrefs>` (in-memory, cleared on quit like
  `WandSelectionManager`'s `Selection`). Driver = the existing 1 Hz `TurfLocationTracker.tick()`
  (`turf/task/TurfLocationTracker.java`); its javadoc explicitly forbids `PlayerMoveEvent` "because it fires constantly
  and tanks TPS", so the map must not add a move listener. On each tick compare the player's *cell* (not the turf) to
  the previous one; re-send only on change, with a minimum interval (`Auto_Min_Interval_Seconds`, default 2) so
  sprinting cannot flood chat. Default **off**; the action-bar compass (section 3) is the always-on lightweight
  alternative because chat cannot be cleared.
- Tab-completion for `on|off|zoom`: chain `OptionalArgument`s (house rule, memory `feedback_optional_arguments`); add
  the entries to the turf module's own `commands.json` (`gangland-features/gangland-turf/src/main/resources/commands.json`,
  house rule `feedback_commands_json`), not core.

### 1.7 Where it lives
Turf module, new package `org.luckyraven.gangland.turf.map` (NEW): `TurfChunkIndex`, `TerritoryRaster` (pure),
`TurfMapRenderer` (chat components), `TurfMapCommand` (in `turf.command`, registered in `TurfCommand.initializeArguments()`
beside `show`/`status`), `MapPrefsService`. Reads only `gangland-api` + `gangland-gang` (`GangLookupContract`,
`Gang.isAlly`) which the turf module already depends on (`module.yml`: `Depends: [civilians, gang]`, `Host_Api: 2.0`).
No new `gangland-api` surface needed. New strings/knobs go in the module's own YAML (contract rule in CLAUDE.md:
`Messages`/`Settings` in the api are legacy-only), e.g. `turf/turf_map.yml`.

**Keystone promotion question (checked):** grepping `E:/Programming/java/Keystone` finds **no** grid, map or
chat-component utility (only `ActionBarManager` and inventory click contexts mention Bungee components). The generic
parts - grid window maths, compass/8-way facing, glyph/legend allocator, run-length component-row builder - are
genuinely reusable (a jail map, a loot-chest map, cop patrol areas). The task rule says reusable primitives belong
upstream, but with a single consumer today the disciplined move is: build them as a clean, Bukkit-free sub-package of
the turf module, and **flag the painter (window + legend allocator + row builder) as the promotion candidate to
`keystone-common` the day a second consumer exists** (new Keystone minor + phase doc). The cell *classifier*
(turf/owner/state/relation) stays turf-specific regardless.

---

## 2. Surface 2 - in-hand map item (`MapView` / `MapRenderer` / `MapCanvas` / `MapCursor`)

### 2.1 API at 1.16.5 [verified with javap on `spigot-api-1.16.5-R0.1-SNAPSHOT`]
- `org.bukkit.map.MapRenderer` has ctor `MapRenderer(boolean contextual)` (per-player render), `isContextual()`,
  `initialize(MapView)`, abstract `render(MapView, MapCanvas, Player)`.
- `MapCanvas`: `setPixel(int,int,byte)`, `getPixel`, `getBasePixel`, `drawImage(int,int,Image)`,
  `drawText(int,int,MapFont,String)`, `getCursors()/setCursors(MapCursorCollection)`. (There is no `setPixelColor`; that
  is a later API. Colours are palette bytes.)
- `MapPalette.matchColor(int,int,int)` / `matchColor(java.awt.Color)` -> `byte`, plus named constants (`RED`,
  `DARK_GREEN`, `LIGHT_GREEN`, `PALE_BLUE`, ...). It uses `java.awt`, so it needs an AWT-capable JRE (headless is fine).
- `MapView`: `getId()` (**int**), `isVirtual()`, `getScale()/setScale(Scale)` with `CLOSEST, CLOSE, NORMAL, FAR,
  FARTHEST`, `getCenterX/Z`, `setCenterX/Z`, `setWorld`, `addRenderer/removeRenderer/getRenderers`,
  `setTrackingPosition`, `setUnlimitedTracking`, `setLocked`.
- `MapCursor(byte x, byte y, byte direction, MapCursor.Type type, boolean visible, String caption)`; `Type` is an **enum**
  at 1.16.5 (`WHITE_POINTER`, `GREEN_POINTER`, `RED_POINTER`, `BLUE_POINTER`, `WHITE_CROSS`, `RED_MARKER`, `WHITE_CIRCLE`,
  `SMALL_WHITE_CIRCLE`, `BANNER_*`, `RED_X`, ...). Note: later API revisions changed how cursor types are exposed, so
  reference them only by name through a small adapter if the compile floor ever moves.
- `Bukkit.createMap(World)`, `Bukkit.getMap(int)`, `Player.sendMap(MapView)`, `MapMeta.setMapView(MapView)`.
- Item: `XMaterial.FILLED_MAP` (XSeries, house rule for version-drifting materials) + `MapMeta.setMapView`.

### 2.2 How it would work
Create **one** shared `MapView` (persist its id in module config; reuse it, never `createMap` per player - each
`createMap` allocates a permanent `map_<n>.dat` in the main world) with a single `MapRenderer(true)` (contextual, so the
canvas is per viewer). `render()` rasterizes the viewer's window at the map's scale (128 x 128 px: scale CLOSEST =
1 px/block = 128 blocks across, FARTHEST = 16 blocks/px = 2048 blocks) using the **same** `TerritoryRaster` as the chat
map, filling pixels with palette bytes from `MapPalette.matchColor(gangColor.getBukkitColor())` - this is the one
surface where per-gang colour works well, since `Color.getBukkitColor()` values are distinct. Cursors: own position via
`setTrackingPosition(true)`; other members of the viewer's gang as `GREEN_POINTER`/captioned cursors; the capture in
progress as `RED_X`.

### 2.3 Cost and caveats
- **Performance** [unverified, must be measured]: `render` for a contextual renderer is invoked per viewer while the map
  is held/visible, potentially every tick; the well-known mistake is redrawing every call. Mitigation: cache the last
  rendered raster hash per player and return immediately when neither the viewer's cell nor the world turf version
  changed (the same cache that guards auto-map). Do the palette conversion once per raster, not per pixel per tick.
- 128 px means a hard ceiling on detail: at FARTHEST, one pixel is 16 blocks, exactly a chunk cell - good for a
  "city map", poor for reading turf names (draw names with `drawText` only at the closer scales).
- Duplicated maps/item frames: a contextual renderer renders per *viewer*, so an item frame shows each passer-by their own
  overlay - acceptable, but a wall-mounted "city map" for everyone wants a non-contextual, cached render instead
  (second view id).
- Not versions-fragile at 1.16.5, but cursor-type handling is the part most likely to drift on newer servers.

### 2.4 Verdict
Works at the floor, medium effort (~1 new renderer + one item + one command), and only a modest UX gain over the chat
map - but it is the best "always visible while playing" surface and reuses the same raster. **Expansion, not MVP.**
Code in the turf module (`turf.map.item`), recipe/handing out via a `/glw turf map item` admin/shop command.

---

## 3. Surface 3 - sidebar (Plaque), action-bar compass, boss bar

### 3.1 Sidebar via Plaque - nearly free
- The scoreboard is no longer in Gangland: WS1 moved it to the standalone plugin **Plaque**
  (`E:/Programming/java/Plaque`, `documentation/migration-0.10.0.md` section "WS1"). Plaque has no Gangland dependency;
  it renders `%gangland_*%` tokens through PlaceholderAPI, so a turf line is just another token in
  `plugins/Plaque/scoreboard.yml` (schema `Board.Rows.<n>.Lines`, e.g. `"   &7Turf: &e%gangland_turf_name%"`).
- The seam already exists: `gangland-api` `data/placeholder/extension/PlaceholderContribution` (`String resolve(OfflinePlayer
  player, String parameter)`, returns `null` when it does not own the parameter) with `PlaceholderContributions.from(container)`
  discovering beans; `GanglandPlaceholder` (impl) queries every contribution after its built-ins miss. The gang module
  already ships one (`gangland-gang/.../gang/placeholder/GangPlaceholderContribution.java`, wired in `GangConfig`), so
  the turf module adds a sibling `TurfPlaceholderContribution` bean - **no api change, no Plaque change**.
- Proposed tokens (parameter after the `gangland_` prefix, lower-cased): `turf_name`, `turf_owner`, `turf_state`
  (`idle|contested|cooldown`), `turf_progress` (0-100), `turf_relation` (`own|ally|enemy|unclaimed|none`),
  `turf_income`, `turf_nearest_name`, `turf_nearest_distance`, `turf_nearest_direction` (8-way), `turf_owned_count`.
  Player-scoped ones read `TurfLocationTracker.getPlayerTurfCache()` (already a per-player `Turf` cache, so no scan).
- Caveats: needs PlaceholderAPI installed (Plaque doc says so); PAPI polls each scoreboard interval, so the resolver must
  be O(1) (use the cache above; nearest-turf uses the chunk index, cached per player per second).
- Verdict: **do in layer 2**. Highest value-per-line of code of any surface.

### 3.2 Action-bar compass
- Keystone `ActionBarManager` (`keystone-common/.../util/ActionBarManager.java`) has `send(Player,String)` (foreground,
  locks ~2.5 s) and `sendBackground(Player,String,int priority)`. `TurfActionBarListener.onEnter/onExit` already uses the
  foreground `send` for enter/exit lines - so a compass line sent via `sendBackground` yields to those automatically. This
  is exactly the arbitration the manager was built for.
- Content: `Enemy turf "Docks" - 120 m NE` / `In your turf` / `Unclaimed: Docks - 45 m N (!) contested`. Refresh on the
  1 Hz tracker tick (skip when the string is unchanged). Nearest-turf uses the chunk index; distance is to the nearest point of
  the rectangle (clamp player x/z into `[min,max]`), 8-way bearing from that point.
- Cost: trivial. UX: high for navigation ("where is the nearest thing to fight for"). Per-player toggle
  `/glw turf compass on|off` (default on for gang members, configurable).
- Verdict: **layer 2**, bundled with the placeholders.

### 3.3 Boss bar
Not recommended for the map. `TurfBossBarListener` already owns the boss bar for capture progress (stacked, per-viewer,
1 Hz refresh); adding a direction bar competes with it for screen space and for the same `BossBar` API surface. Keep boss
bars for capture only.

---

## 4. Surface 4 - web map integrations (soft dependencies)

Isolation mechanism (already in CLAUDE.md, Keystone 1.9.1 `ReflectionGuard`): the bean/listener scans skip a class whose
*signature* names an absent soft-dependency type and report fault `reflection.type.missing`. So each integration is its own
small bean class whose constructor/fields name the map API type; declare the plugin in `softdepend:`; **no** `Plugins:`
fail-fast entry in `module.yml`. Never cache the API object across reloads; re-resolve on the plugin's enable callback.
Note module `softdepend` lives in the core `plugin.yml`, so adding a soft dep is a core-jar one-line change.

| Plugin | Spigot? | API shape | Effort | Value |
|---|---|---|---|---|
| **dynmap** | Yes [web] | `DynmapCommonAPIListener.register(...)` -> `apiEnabled(DynmapCommonAPI)` -> `getMarkerAPI()` -> `createMarkerSet(id,label,icons,persistent)` -> `set.createAreaMarker(id,label,markup,world,double[] x,double[] z,persistent)`; style via `setLineStyle(weight,opacity,0xRRGGBB)` / `setFillStyle(opacity,0xRRGGBB)` [web: [MarkerSet.java](https://github.com/webbukkit/DynmapCoreAPI/blob/master/src/main/java/org/dynmap/markers/MarkerSet.java), [AreaMarker.java](https://github.com/webbukkit/DynmapCoreAPI/blob/master/src/main/java/org/dynmap/markers/AreaMarker.java)] | Low: one sync bean, ~120 lines | High |
| **BlueMap** | Yes [web: [BlueMapAPI wiki](https://github.com/BlueMap-Minecraft/BlueMapAPI/wiki)] | `de.bluecolored:bluemap-api` (repo `https://repo.bluecolored.de/releases`, `provided` scope), `BlueMapAPI.onEnable(consumer)` / `onDisable`, `MarkerSet` + `ShapeMarker` (rectangle/shape per world map); **markers are non-persistent - recreate on every enable** [web: [Markers page](https://bluemap.bluecolored.de/wiki/customization/Markers.html)] | Low-medium | Medium-high |
| **squaremap** | **Paper-only** [web: [squaremap](https://github.com/jpenilla/squaremap)] | `SimpleLayerProvider` per world + `Marker` shapes | n/a | Out of scope: violates "Spigot only, not Paper". `ServerEnvironment.isPaper()` (Keystone) exists if a guard is ever wanted; do not build |
| **Pl3xMap** | Paper-only [unverified] | similar layer API | n/a | Out of scope, same reason |

Details that matter:
- **Rectangle corners**: `CuboidRegion` max is *inclusive*, so the drawn rectangle is `(minX, minZ)`-`(maxX + 1, maxZ + 1)`.
  Get this wrong and every turf is one block short on the web map.
- **Sync model**: subscribe to the same four capture/ownership events used by the raster cache (owner changed, captured,
  capture start, capture failed) plus turf create/delete; on each, update only that turf's marker id (`turf-<id>`). A
  full rebuild on API enable/reload. Marker updates should run on the main thread for dynmap; BlueMap's API is callable from
  the plugin thread but treat as main-thread to match the rest of the module. [unverified] dynmap's exact thread contract:
  the docs page we could fetch does not state one, so keep all calls on the main thread.
- **Web colours** are the one place per-gang colour is right: fill with `Color.getBukkitColor()` (distinct values) rather
  than chat codes. Label markup: turf name, owner, state, income. Optional second marker layer for contested turfs
  (dashed/red outline) so an ongoing capture is visible to spectators.
- **BlueMap Java floor is not confirmed** by the material we could fetch ([unverified]). Gangland targets Java release 17;
  if a current `bluemap-api` is built for a newer bytecode level, javac 17 cannot compile against it. De-risk before
  committing: put the BlueMap integration in its own tiny Maven module/jar (or reflect against the API) so a bump there
  cannot break the turf module's build. Dynmap's API has no such concern.
- Privacy/design: gate with `Turf.Map.Web.Show_Owner` / `Show_Income` so servers can hide sensitive info from a public
  web map.

Verdict: **layer 3**, dynmap first (lowest effort, largest install base), BlueMap second. Turf module, one bean class per
plugin under `turf.map.web`.

---

## 5. Surface 5 (part A) - the existing `TurfVisualization` (borders in-world)

Facts [verified, `turf/task/TurfVisualization.java`, L22]: per-viewer `BukkitRunnable` at 20-tick refresh; each refresh draws
4 vertical pillars at the corners and top+bottom rectangles at `viewer.y +/- 10`, spawning **one particle every 0.5 blocks**
(`STEP`) via `viewer.spawnParticle(...)` for every point, for a fixed duration (`Turf.Visualization_Duration_Seconds`, default
30). Triggered by `TurfShowCommand` (admin-oriented: active selection / standing turf / pending wand selection).

**Cost problem worth its own docket triage entry:** particle count per refresh is roughly `4*(width+height)` per rectangle
x2 rectangles + pillars. For a 256 x 256 turf that is ~4,200 `spawnParticle` calls per second per viewer for 30 s, and all
points farther than the client's particle render range (normal particles are only rendered within ~32 blocks [unverified
exact figure]) are wasted packets. This is fine for the intended use (an admin sizing a small selection) and a hazard the
moment the same code is offered to players. **Recommendation:** before any player-facing border feature, add distance
clipping (only emit edge points within R blocks of the viewer, R configurable ~40) and adaptive step (larger `STEP` at
range). Record it as a perf finding in `triage/turf-visualization-cost.txt` and rebuild the docket per the CLAUDE.md
"adjacent bug" rule; no product change in this wave.

Player-facing use: a `/glw turf map border` (or "show current turf border") that reuses the clipped renderer for the turf the
player stands in / the nearest one, 10 s, gang-coloured via `Particle.REDSTONE`+`DustOptions` [verified available at 1.16.5
API; resolve through `XParticle` per house rule]. Effort low once the clipping exists. UX value: medium (people like seeing the
exact line). Turf module, existing package `turf.task`.

## 6. Surface 5 (part B) - `keystone-hologram`

Facts [verified, `Keystone/keystone-hologram`]: `Hologram` = invisible **ArmorStand** lines (`LINE_HEIGHT` 0.25), API
`spawn/update/updateLine/respawnIfStale/despawn/teleport`, and `HologramService.createUpdatingHologram(location,
updateIntervalTicks, ...)`, `registerProtection(owner)`, removal by id/location, `BeanLifecycle.onShutdown` cleanup. The class
javadoc records the deliberate floor decision: ArmorStand-only for the 1.16.5 floor, `TextDisplay` (1.19.4+) only behind a
version gate later.

Use for the map: a floating turf banner at the turf centre (name / owner / status), created at `TurfManager.initialize`,
updated by the capture events. Reuse `GarrisonDeployListener.regionCentreSurface` (turf module, L104: centre X/Z,
`getHighestBlockYAt`+1) for placement. Caveats: entities in possibly-unloaded chunks (Hologram has `respawnIfStale`; the
module already has `TurfPowerupChunkLoadListener` as the pattern for chunk-load re-spawn), armour-stand count scales with turf
count, visible to everyone (not per-viewer), and not useful from distance. Value: low-medium (atmosphere; helps "is this
turf mine" at a glance when you arrive). **Late/optional**, and only after the Quartermaster NPC anchor is considered - a
physical NPC/nameplate per turf may already do the job (`TurfPowerupNpc`).

---

## 7. Cross-surface summary

| Surface | API at 1.16.5 | Perf cost | Complexity | UX value | Where it lives |
|---|---|---|---|---|---|
| 1. Chat grid (hover/click, auto) | Bungee components: verified, used in-repo | Negligible manual; low auto (cell-change + hash-diff gate) | Low-medium | **Very high** (the ask) | Turf module `turf.map`; painter = Keystone-promotion candidate later |
| 2. Map item (`MapRenderer`) | All classes verified via javap; cursor `Type` is an enum | Medium unless cached; must be measured | Medium | Medium | Turf module `turf.map.item` |
| 3a. Plaque sidebar tokens | `PlaceholderContribution` seam exists | O(1) with the per-player turf cache | **Very low** | High | Turf module bean; zero Plaque/api change |
| 3b. Action-bar compass | `ActionBarManager.sendBackground` exists | Negligible | Low | High | Turf module (tracker tick) |
| 3c. Boss bar | Exists | - | - | Not recommended (capture owns it) | - |
| 4a. dynmap areas | Spigot OK [web] | Event-driven, negligible | Low | High (for servers with dynmap) | Turf module `turf.map.web`, soft dep |
| 4b. BlueMap | Spigot OK [web]; Java floor unverified | Event-driven, negligible | Low-medium (+ isolate build) | Medium-high | Same, separate jar recommended |
| 4c. squaremap / Pl3xMap | Paper-only | - | - | Excluded by platform rule | - |
| 5a. In-world particle borders | Existing `TurfVisualization` | **High for large turfs** (finding) | Low to fix | Medium | Turf module, add clipping first |
| 5b. Hologram banner | `keystone-hologram` exists (ArmorStand) | Entities per turf | Low-medium | Low-medium | Turf module; Keystone already has the primitive |

---

## 8. Claims-model question: keep admin cuboids, or player-claimable chunks?

Current model (verified): a turf is an **admin-defined named X/Z rectangle** (`Turf.displayName`, `CuboidRegion`, `ownerGangId`,
`incomeAmount`, `lastCaptureTimestamp`; created with the wand + `/glw turf create <name>`), owned via a **presence-based
capture** (`CaptureService`; two-phase for unclaimed turfs, single-phase for owned ones; `TurfLocationTracker` 1 Hz), with income
(`TurfIncomeDistributor`), garrison (`GarrisonManager`), buffs (`ActiveBuffManager`) and Quartermaster NPCs
(`TurfPowerupManager`) all keyed by turf id.

| | Keep admin cuboids (map rasterizes them) | Player-claimable chunks (Factions style) |
|---|---|---|
| Natural / easy to understand | Named "hoods" read like GTA districts; players learn places by name; owner has full control of shape and value | Familiar to Factions players; grid is exact, no partial cells |
| Map fidelity | Lossy at chunk granularity (mitigate: cell size/zoom, `Snap_To_Chunks`) | Perfect: cell = claim |
| Fit with capture | Unchanged: presence-based capture of a named prize turf | Needs a new rule set: adjacency, overclaim, per-chunk protection/timers, what "capture" means per chunk |
| Economy | One income figure per turf; admin-tuned | Needs power/limits/upkeep economy to prevent land-hoarding |
| Storage | Existing `TurfTable` rows | New per-chunk table (potentially thousands of rows), load/index on boot |
| Powerups/garrison/NPCs | Attach to a turf id | Must re-anchor to "a cluster of chunks" - undefined |
| Cops-n-crooks synergy | Turf = a place cops/crooks talk about ("Docks") | Anonymous chunks lose the sense of place |
| Effort | Map-only wave | A **system rewrite**, not a map feature |

**Recommendation: keep admin cuboids.** Player claiming is a separate product decision (a "land claim" mode) with its own
balance design; it collides with the presence-based capture that is the core gang-vs-gang mechanic.

**The one owner decision that makes the map crisp without a rewrite:** snap turf boundaries to chunk edges *at creation*.
Hook points (verified): `Selection.set(Location, first)` stores raw pos1/pos2; `TurfCreateCommand` builds
`new CuboidRegion(world, pos1.getBlockX(), pos1.getBlockZ(), pos2.getBlockX(), pos2.getBlockZ())`; `TurfShowCommand.renderSelection`
builds the same for the preview. A NEW `CuboidRegion.snappedToChunks()` (min `& ~15`, max `| 15`) applied behind a
`Turf.Snap_To_Chunks` flag (default on for new servers, off to preserve existing behaviour) at those two construction sites
gives chunk-aligned turfs and a map with no partial cells. Existing turfs are untouched. Ask the owner: *snap on by default?*

---

## 9. Recommended layered design

Command names follow the existing `/glw turf ...` tree (`TurfCommand` root, `SubArgument` leaves, `OptionalArgument`
chains, entries in the turf module's `commands.json`; `GanglandApi.SHORT_PREFIX = "glw"`).

### Layer 0 - foundation (invisible, prerequisite)
- NEW `TurfChunkIndex` (per world, rebuilt on create/delete/region change) and a per-world "turf version" counter bumped on
  the four capture/ownership events.
- NEW `TerritoryRaster` (pure; window + cell size -> cells with turf ids, coverage, state); unit-tested without Bukkit.
- NEW `MapPrefsService` (in-memory per-player prefs, cleared on quit).

### Layer 1 - MVP: chat map
- `/glw turf map` - send the grid once. Permission: base `gangland.command.turf` (players); admin-only extras gated by
  `WandSelectionManager.ADMIN_PERMISSION` (the same gate `TurfCreateCommand` uses).
- `/glw turf map on|off` - auto map on cell change (default off, min-interval throttle, driven by `TurfLocationTracker`).
- Hover: name/owner/state/income/garrison; click: `/glw turf info <id>`.
- Config `turf/turf_map.yml` (module-owned, block-style YAML, `Capitalized_Underscore_Separated` keys per house rules):
  `Width`, `Height` (odd), `Cell_Blocks`, `Colour_Mode: RELATION|GANG`, `Player_Glyph`, `Auto_Min_Interval_Seconds`.

### Layer 2 - context everywhere
- `TurfPlaceholderContribution` -> `%gangland_turf_*%` tokens (Plaque line, inventory menus, any PAPI consumer).
- `/glw turf compass on|off` - `ActionBarManager.sendBackground` line "Enemy turf 120 m NE".
- `/glw turf map zoom in|out|<blocks>` - 8/16/32/64 cell sizes.

### Layer 3 - web and borders
- `turf.map.web.DynmapTurfLayer` (soft dep dynmap), then `BlueMapTurfLayer` (separate jar if the Java floor bites).
- Clipped/adaptive `TurfVisualization` and `/glw turf map border` for players (after fixing the cost finding).

### Layer 4 - optional
- `/glw turf map item` (contextual `MapRenderer` item, one shared `MapView`, cached raster).
- Turf-centre hologram banner.
- A module-contributed `TurfMapLayer` bean seam (modelled on `CommandContribution`/`PlaceholderContribution`) so
  **cops-n-crooks** can overlay its own points of interest without the turf module knowing it: jails, cop spawner posts,
  active manhunts/wanted heat, squad-sighting pings from the H12 squad-signal SPI (`NpcSquadSignal`/`NpcSquadListener`,
  Keystone 1.13.0), and Quartermaster/bank/trader locations. Cops-n-crooks already `Depends: [turf, civilians]`, so the
  dependency direction is right. Additive; ship only when Layer 1 has proven the grid.

### Sample rendering (Layer 1, default 41 x 9, fixed-north, viewer in gang "Vipers", "Kings" allied)

```
Turf map - world (1240, -310)  facing NE            N
                                                  W + E
                                                    S
..RRRRRRRR.-------.......................
..RRRRRRRR.-------......KKKKKKKKK........
..RRRRRRRR..............KK!!!KKKK.RRRRRR.
..RRRRRRRR....vVVVVVVVV.KK!!!KKKK.RRRRRR.
..............vVVVVV^VV.KKKKKKKKK.RRRRRR.
..............vVVVVVVVV...........RRRRRR.
..............vVVVVVVVV..-------..RRRRRR.
.........................-------.........
.........................-------.........
V Vipers (yours)   K Kings (ally)   R Rats (enemy)
- unclaimed turf   . no turf   ! contested   lowercase = partial   ^ you
```

Colouring (not visible in plain text): `V` green, `K` aqua, `R` red, `-` gray, `.` dark gray, `!` gold, `^` white/bold.
Row 3-4 `!!!` is Kings' turf being captured (hover: "Kings - Docks East, contested by Rats, phase 2, 62 %").
The lowercase `v` column on the left edge is a partially covered chunk (a turf boundary that is not chunk-aligned).

### Open decisions for the owner
1. Snap turf boundaries to chunk edges at creation (`Turf.Snap_To_Chunks`)? (Section 8.)
2. Default colour mode: viewer-relative (recommended) or per-gang colour?
3. Auto map default on or off? (Recommended: off, with the action-bar compass on.)
4. Show income and owner on public web maps, or keep those hover fields in-game only?
5. Is a physical/hologram marker per turf wanted, or does the Quartermaster NPC already serve that role?

### Verification still needed on the test server (not settled by reading)
- Contextual `MapRenderer.render` call frequency and cost with N holders (drives caching design).
- dynmap marker thread contract; BlueMap API bytecode/Java level vs release 17.
- Glyph widths for arrows/blocks on the vanilla client font (default `+`/`^` avoids the question).
- Whether hover works for the Geyser/Bedrock player base (legend carries the same info regardless).
- Vanilla particle render range used to size the clipping radius for `TurfVisualization`.

### Files verified/read for this study
Turf module (`gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf/`): `data/{CuboidRegion,Region,Turf,TurfRuntimeState}.java`,
`manager/TurfManager.java`, `task/{TurfVisualization,TurfLocationTracker}.java`, `command/{TurfCommand,TurfShowCommand,TurfStatusCommand,TurfListCommand,TurfInfoCommand,TurfCreateCommand}.java`,
`listener/{TurfActionBarListener,TurfBossBarListener,GangDisplayNameResolver}.java`, `listener/powerups/GarrisonDeployListener.java`, `selection/Selection.java`,
`state/TurfState.java`, `TurfModule.java`, `resources/{module.yml,commands.json}`. Gang module: `gang/Gang.java`, `gang/GangAlliance.java`, `gang/contract/GangLookupContract.java`,
`command/sub/gang/GangColorCommand.java`, `gang/placeholder/GangPlaceholderContribution.java`. API: `gangland-api/.../data/placeholder/extension/PlaceholderContribution{,s}.java`, `GanglandApi.SHORT_PREFIX`.
Impl: `data/placeholder/worker/GanglandPlaceholder.java`. Keystone: `color/Color.java`, `util/{ActionBarManager,ServerEnvironment,ParticleUtil}.java`, `keystone-hologram/*`.
Docs: `documentation/migration-0.10.0.md`. Jar check: `spigot-api-1.16.5-R0.1-SNAPSHOT-shaded.jar` via `javap` (`org.bukkit.map.*`, `MapMeta`, `Bukkit`, `Player`, bungee chat classes).
