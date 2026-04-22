# Gang Turfs — Phase 1 & 2 Implementation Spec

**Plugin:** Gangland Warfare (Spigot/Paper)
**Feature:** Gang territory control with a custom territory system (no WorldGuard dependency)
**Scope:** Phases 1 and 2 only. Later features (turf perks, war windows, cop raids, turf businesses, alliances,
upgrades) are out of scope for this spec.

---

## Design Principles

- Turfs are **admin-defined**, not player-purchased. Admins curate meaningful locations; gangs fight over them.
- The rest of the open world remains free to traverse and play in. Turfs are capture points, not property deeds.
- Gameplay loop should feel rewarding, not punishing. Losing a turf removes future income but never strips existing gang
  resources.
- Keep it GTA-style: readable, fast, not over-engineered.
- **No external region dependency** — the plugin handles its own territory definition, overlap prevention, and spatial
  lookup.

---

# PHASE 1 — Foundation

**Goal:** Turfs exist, have owners, show up visually to players, and generate passive income. No capture mechanic yet.

## 1.1 Data Model

### Turf object (persisted)

```
Turf {
    id: String              // unique, e.g. "downtown_01"
    displayName: String     // shown to players, e.g. "Downtown"
    world: String           // world name
    region: CuboidRegion    // see below
    ownerGangId: String?    // null = unclaimed
    incomeAmount: Double    // money per income tick
    createdAt: Long         // epoch ms
}
```

### CuboidRegion (2D column)

Turfs cover an X/Z rectangle from bedrock to sky. Y is ignored. This matches GTA-style intuition (no one cares about
altitude — if you're standing over the turf, you're in it).

```
CuboidRegion {
    minX: Int
    maxX: Int
    minZ: Int
    maxZ: Int
}
```

Always normalize at creation so `minX <= maxX` and `minZ <= maxZ` regardless of which corner the admin selected first.

### Region interface (forward-compatibility)

Wrap `CuboidRegion` behind a `Region` interface with a single method: `contains(Location): Boolean`. Only one
implementation for now, but this lets you add sphere or polygon regions later without refactoring.

### `contains` check

```
contains(location):
    if location.world.name != this.world: return false
    return location.blockX >= minX AND location.blockX <= maxX
       AND location.blockZ >= minZ AND location.blockZ <= maxZ
```

## 1.2 Persistence

- Store turfs in `turfs.yml` (or existing DB layer if one is already in use by the plugin).
- Save triggers:
    - Ownership changes
    - Admin command edits (create, delete, setowner)
    - Server shutdown
    - Autosave every 5 minutes as safety net
- On startup, load all turfs from disk. Ownership persists across restarts automatically.

## 1.3 Admin Turf Selection (Wand System)

Since there's no WorldEdit, admins need their own selection tool.

### The wand

- `/turf wand` gives the admin a designated item (suggest: blaze rod).
- Mark the item with persistent NBT (`gangturf_wand = true`) so the plugin recognizes it regardless of name/lore
  changes.
- Left-click a block with the wand → sets position 1 for that admin.
- Right-click a block with the wand → sets position 2 for that admin.
- Both actions send a confirmation message with the coordinates.

### Selection storage

```
Map<UUID, Selection> activeSelections

Selection {
    world: String
    pos1: Location?
    pos2: Location?
}
```

- In memory only. Clear on `PlayerQuitEvent`.
- Both positions must be in the same world — reject cross-world selections.

### Fallback commands

For admins who can't be bothered with the wand:

- `/turf pos1` — set position 1 to where the admin is currently standing
- `/turf pos2` — set position 2 to where the admin is currently standing

### Creating a turf from a selection

`/turf create <id> <displayName>`:

1. Read the admin's active selection.
2. Reject if pos1 or pos2 is missing.
3. Normalize into a `CuboidRegion` (min/max of X and Z).
4. Check for overlap with existing turfs in the same world (see 1.4).
5. Reject if id already exists.
6. Save and confirm.

## 1.4 Overlap Prevention

No two turfs in the same world may overlap. Enforce at creation time.

### Rectangle overlap check (2D)

Two rectangles overlap if:

```
minX1 <= maxX2 AND maxX1 >= minX2
AND minZ1 <= maxZ2 AND maxZ1 >= minZ2
```

On `/turf create`, iterate existing turfs in the same world and reject if any overlap is found. Return the conflicting
turf's id to the admin so they know what's in the way.

This also means detection logic can safely return the **first** matching turf without worrying about ambiguity.

## 1.5 Turf Visualization Helper

Admins defining turfs blind is painful. Add a visual outline command.

### `/turf show [id]`

- With id: outline that specific turf for 30 seconds.
- Without id (inside a turf): outline the current turf.
- Spawns particles along the 4 vertical edges at the X/Z corners, from a reasonable Y range around the admin (e.g.
  admin's Y ± 10 blocks) so they're visible regardless of terrain.
- Use `Particle.FLAME` or `Particle.REDSTONE` (colored) — one particle every ~0.5 blocks along each edge, refreshed
  every second.
- Runs as a short-lived `BukkitRunnable` scoped to the admin who ran it.

Also useful: `/turf show` while you have an active selection could preview the pending turf boundary *before* you run
`/turf create`. Small add, big quality-of-life win.

## 1.6 Player → Turf Detection

**Do NOT use `PlayerMoveEvent`** — fires constantly, tanks TPS on busy servers.

Use a single repeating task:

```
Task: TurfLocationTracker
Interval: 20 ticks (1 second)

For each online player:
    currentTurf = findTurfContaining(player.location)
    previousTurf = playerTurfCache[player.uuid]
    if currentTurf != previousTurf:
        if previousTurf != null: fire TurfExitEvent(player, previousTurf)
        if currentTurf != null: fire TurfEnterEvent(player, currentTurf)
        playerTurfCache[player.uuid] = currentTurf
```

### Spatial lookup

Keep turfs grouped by world: `Map<String, List<Turf>> turfsByWorld`.

- For each online player, only iterate turfs in their current world.
- Since overlap is prevented at creation time, the first matching turf can be returned immediately.
- Below ~50 turfs per world, linear scan at 1Hz is negligible. Don't over-engineer.

### Optional: chunk-level index (only if needed)

If turf count grows large, add:

```
Map<ChunkKey, List<Turf>> turfsByChunk
```

On create/delete, compute which chunks the turf overlaps and register. On lookup, check only turfs in the player's
current chunk.

Skip this for v1. Add it only if profiling shows a real issue.

### Other optimizations

- Skip players in worlds with no turfs.
- Clean up `playerTurfCache` entries on `PlayerQuitEvent`.

## 1.7 Custom Events (fire in Phase 1, used in Phase 2)

- `TurfEnterEvent(player, turf)`
- `TurfExitEvent(player, turf)`

## 1.8 Visual Feedback

**On turf enter (action bar):**

- Owned: `» Entering <turfName> — Controlled by <gangName>` (colored by gang)
- Unclaimed: `» Entering <turfName> — Unclaimed territory`

**On turf exit:** clear the action bar.

Use action bar (not BossBar) in Phase 1. BossBar is reserved for capture progress in Phase 2.

## 1.9 Income System

```
Task: TurfIncomeDistributor
Interval: configurable, default every 10 minutes

For each turf with ownerGangId != null:
    gang = gangService.get(ownerGangId)
    if gang exists:
        gang.bank.deposit(turf.incomeAmount)
        log to gang income history (source = turf id)
    else:
        // gang was deleted; free the turf
        turf.ownerGangId = null
        persist
```

## 1.10 Admin Commands (Phase 1)

- `/turf wand` — give the selection wand
- `/turf pos1` — set selection corner 1 to current location
- `/turf pos2` — set selection corner 2 to current location
- `/turf create <id> <displayName>` — create from active selection
- `/turf setowner <id> <gang|none>` — manually assign or clear owner
- `/turf list` — list all turfs (paginated if needed)
- `/turf info <id>` — detailed info: owner, bounds, income, creation date
- `/turf delete <id>` — remove a turf (require confirmation)
- `/turf show [id]` — visualize boundaries with particles

Permission node: `gangturf.admin` (gate all commands under this).

## 1.11 Config Knobs (Phase 1)

- `incomeIntervalMinutes` (default: 10)
- `defaultIncomeAmount` (default: 100)
- `autosaveIntervalMinutes` (default: 5)
- `wandItemType` (default: `BLAZE_ROD`)
- `enterMessageFormat`, `exitMessageFormat` (string templates)
- `visualizationDurationSeconds` (default: 30)
- `visualizationParticle` (default: `FLAME`)

## 1.12 "Done" Criteria

An admin uses the wand to select two corners, creates 3 turfs, and assigns them to 3 different gangs. Those gangs earn
money passively over time. Players walking through see an action bar showing the turf name and owner. Ownership survives
a server restart. Creating a fourth turf that overlaps an existing one is rejected with a clear error.

---

# PHASE 2 — Capturing

**Goal:** Players can take turfs from other gangs. Turns passive ownership into a live game.

## 2.1 Runtime State (in-memory, NOT persisted)

On top of the persisted `Turf`, each turf gets a live runtime state:

```
TurfRuntimeState {
    turfId: String
    state: TurfState             // IDLE | CONTESTING | COOLDOWN
    captureProgress: Double      // 0.0 to 100.0
    challengerGangId: String?    // only set during CONTESTING
    lastChallengerSeenAt: Long   // for abandon detection
}
```

### Persisted addition to Turf

Add one field to the persisted `Turf` object:

- `lastCaptureTimestamp: Long` — used to calculate cooldown across restarts

On server startup, every turf begins in `IDLE` with progress 0. Mid-capture progress is **not** persisted — this is
intentional, keeps things simple.

## 2.2 Capturability Rules

A turf is capturable right now if ALL of these are true:

- State is not `COOLDOWN` (`now >= lastCaptureTimestamp + cooldownMs`)
- Either:
    - The turf is unclaimed, OR
    - The owning gang has ≥1 member online, OR
    - The 10-minute post-logoff grace period has expired

Bundle into a single method: `isCapturable(turf): Boolean`.

## 2.3 Capture Tick Logic

Runs every 1 second (can live inside the same task as `TurfLocationTracker`):

```
For each turf:
    playersInside = players currently inside (from cache)
    defenders = playersInside where gang == turf.ownerGangId
    challengerGangs = playersInside where gang != turf.ownerGangId AND player has a gang
                      (grouped by gang id)

    switch turf.state:

        case IDLE:
            if not isCapturable(turf): break
            if defenders.isEmpty() AND challengerGangs.size() == 1:
                gang = the single challenger gang
                startContest(turf, gang)
            // if multiple challenger gangs: do nothing (they cancel each other)

        case CONTESTING:
            if defenders.isNotEmpty():
                // pause, do not decrement
                optionally flash bossbar
                break

            if turf.challengerGangId not present in challengerGangs:
                // original challenger left
                if now - lastChallengerSeenAt > abandonGraceMs:
                    cancelContest(turf, reason=ABANDONED)
                break

            // valid, progress it
            turf.captureProgress += progressPerTick
            turf.lastChallengerSeenAt = now
            updateBossBar(turf)

            if crossing 25/50/75% milestone:
                fire TurfCaptureProgressEvent(turf, progress)

            if turf.captureProgress >= 100:
                completeCapture(turf)

        case COOLDOWN:
            if now >= turf.lastCaptureTimestamp + cooldownMs:
                turf.state = IDLE
```

### progressPerTick calculation

`progressPerTick = 100.0 / captureDurationSeconds`

So if `captureDurationSeconds = 180`, progress increases by `~0.555` per tick and reaches 100 in 3 minutes.

## 2.4 Capture Functions

```
startContest(turf, gang):
    turf.state = CONTESTING
    turf.challengerGangId = gang.id
    turf.captureProgress = 0
    turf.lastChallengerSeenAt = now
    fire TurfCaptureStartEvent(turf, gang)
    notify defender gang members (private)
    show bossbar to all players inside turf
```

```
completeCapture(turf):
    oldOwner = turf.ownerGangId
    newOwner = turf.challengerGangId
    turf.ownerGangId = newOwner
    turf.lastCaptureTimestamp = now
    turf.state = COOLDOWN
    turf.captureProgress = 0
    turf.challengerGangId = null
    persist turf to disk immediately
    fire TurfCapturedEvent(turf, oldOwner, newOwner)
    broadcast server-wide with colors + sound
    remove bossbar from all players inside
```

```
cancelContest(turf, reason):
    turf.state = IDLE
    turf.captureProgress = 0
    turf.challengerGangId = null
    fire TurfCaptureFailedEvent(turf, reason)
    remove bossbar
    // no broadcast — keep failures quiet, only wins are celebrated
```

## 2.5 New Events

- `TurfCaptureStartEvent(turf, challengerGang)`
- `TurfCaptureProgressEvent(turf, progress)` — fire only at 25/50/75% milestones, not every tick
- `TurfCapturedEvent(turf, oldOwner, newOwner)`
- `TurfCaptureFailedEvent(turf, reason)` — reason enum: `ABANDONED`, `CANCELLED`

## 2.6 Notifications

- **Contest start:** private message to defender gang members only. Example:
  `⚠ Your turf Downtown is being contested by Vagos!` Private notification = urgency without spoiling surprise attacks
  to the whole server.
- **50% milestone:** second warning to defenders, stronger tone.
- **Capture complete:** server-wide broadcast, colored, with sound. This is the bragging-rights moment.
- **Capture failed/abandoned:** silent. No message.

## 2.7 Visual Feedback — BossBar during CONTESTING

Show to all players currently inside the turf:

- **Color:** RED if viewer is in the defending gang, GREEN if in the attacking gang, WHITE otherwise.
- **Title:** `<challengerGang> is capturing <turfName>`
- **Progress:** `captureProgress / 100`
- **Removal triggers:** capture complete, contest cancelled, or player exits turf.

### Optional COOLDOWN feedback

While inside a turf in cooldown, action bar shows: `<turfName> — Securing territory (Xm left)`.

## 2.8 Post-Logoff Grace Period (10 min protection)

Track per-gang `lastMemberOnlineAt` timestamp:

- Update to `now` whenever any gang member is online (refresh on heartbeat or login).
- When the last member of a gang logs off, this value freezes.
- A gang's turfs are **protected** while `now - lastMemberOnlineAt < 10 minutes`.
- After the 10-min window, turfs become capturable even with gang fully offline.
- When any member logs back in, reset the timestamp → turfs re-protect immediately.

This is what `isCapturable()` checks in section 2.2.

## 2.9 Inactivity Auto-Release (10 days)

Run a daily task (or check on server startup):

- For each gang, if `now - lastMemberOnlineAt > 10 days`, release all their turfs (`ownerGangId = null`).
- Log each release.
- Optional: server broadcast so players know turfs just opened up.

## 2.10 New Commands (Phase 2)

- `/turf status <id>` — current state, progress %, cooldown remaining, challenger if any
- `/turf` (no args) — info on the turf you're standing in; if you're not in one, list your gang's turfs

Both available to all players (no admin permission needed).

## 2.11 Config Knobs (Phase 2 additions)

- `captureDurationSeconds` (default: 180) — full capture time from 0 to 100
- `cooldownMinutes` (default: 15) — post-capture lockout
- `abandonGraceSeconds` (default: 15) — challenger-step-out grace before cancel
- `postLogoffProtectionMinutes` (default: 10)
- `inactivityAutoReleaseDays` (default: 10)
- `enableCaptureSound` (default: true)
- `broadcastCaptureGlobally` (default: true)
- `captureProgressMilestones` (default: [25, 50, 75]) — which % points fire the progress event

## 2.12 "Done" Criteria

Two gangs log in on a test server. Gang A walks into Gang B's turf. A bossbar appears and fills. Gang B gets a private
warning. A Gang B member runs over and stands inside — progress pauses. Combat ensues. If Gang A wins and holds the
ground, progress resumes, hits 100%, a server-wide broadcast fires, and the turf is now owned by Gang A, locked for 15
minutes. Everything survives a server restart except in-flight capture progress (which resets — this is intentional).

If that loop feels tense and fair with 4–6 testers, Phase 2 is shippable.

---

## Implementation Order (suggested)

1. Data model + persistence (Turf, CuboidRegion, Region interface, YAML load/save)
2. Wand selection system + `/turf pos1`, `/turf pos2`, `/turf wand`
3. Overlap prevention + `/turf create`, `/turf delete`, `/turf list`, `/turf info`, `/turf setowner`
4. `/turf show` visualization helper
5. Player→turf detection task with enter/exit events
6. Action bar feedback on enter/exit
7. Income distributor task
8. **— Phase 1 shippable here. Test live before proceeding. —**
9. Runtime state + TurfState enum + isCapturable()
10. Capture tick logic inside existing tracker task
11. startContest / completeCapture / cancelContest
12. BossBar rendering
13. Private defender notifications + global capture broadcast
14. Post-logoff grace tracking (per-gang lastMemberOnlineAt)
15. Inactivity auto-release daily task
16. `/turf status` and `/turf` commands
17. **— Phase 2 shippable here —**

## Dependencies

No new external dependencies required. The plugin's existing requirements (Paper/Spigot API, Citizens, NBTAPI if already
used elsewhere) are sufficient. **WorldGuard is NOT required.**

## Out of Scope (later phases)

- Per-turf perks (XP boost, trader discount, reduced cop heat)
- Graffiti/tagging
- War windows (time-of-day capture restrictions)
- Cop raids on turfs
- Turf-specific businesses / contraband generation
- Organized raids with defense meters
- Alliances between gangs
- Turf upgrades / fortifications
- 3D (Y-bounded) turfs
- Non-cuboid region shapes (sphere, polygon)
