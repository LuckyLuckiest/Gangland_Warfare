# Gang and turf roadmap: mechanics depth and the competitive loop

Planner: `mechanics-depth` (Opus 5.5), wave `gang-turf-wave-2026-09-30`, written 2026-09-30. Planning only, no product code.
Repo: Gangland master `ff9d813f` (rev 0.12.0, Keystone pin 1.13.0, `GanglandApi.VERSION = "2.0"`, every `module.yml` `Host_Api: 2.0`).
Graph freshness: `graphify-out/graph.json` 2026-09-29 23:08 is newer than the last commit (23:05), so graph answers are current.
Orientation: `graphify explain CaptureService | TurfIncomeDistributor | GarrisonDeployListener | GangMembership | TurfLocationTracker`,
`graphify query CivilianDeathRewardListener | GangAllianceRepositoryContract | WantedEvent | TurfFriendlyFireListener | GangDeleteEvent | countsForWanted | MailType`.
Raw reads after that: `CaptureService.java` L90-200, `EntityDamageListener.java` L84-260, `TurfIncomeDistributor.java`,
`GangMembersDamageListener.java`, `settings.yml` `Turf:` block (L630-700), `TurfModule.configure`, `Member.java`, `RadioVoice.java`,
`WantedKillTracker.java`, `WantedKillTrackers.java`, `KillComboWantedTracker.java`, `MailType.java`, all `module.yml`.

Lens: mechanics depth and the competitive loop. Every mechanic below carries a one-sentence player rule; anything that
could not be said in one sentence was cut or merged (section 1.4).

**Census conflicts, resolved in favour of the verified files.** `ux-surface.md` says turf titles are missing; `turf.md`
verifies `TurfActionBarListener` sends title + subtitle on enter (gated by `Turf.Show_Enter_Title`). `seams.md` calls
`gangsAllied` bidirectional; TF-24 (`docket-tf-verified.md`) verifies `GangMembershipInstaller.java:59 -> Gang.isAlly:102`
checks one side only. `seams.md`'s claim that `TurfFriendlyFireListener` "throws" without turf is moot (cops `Depends: [turf]`).
Where they disagree, this plan uses `gang.md`, `turf.md`, `cnc-ideas.md`, `map-feasibility.md` and the two `docket-*-verified.md` files.

---

## 0. The one-paragraph diagnosis

The competitive loop is upside down. `CaptureService.isCapturable` (turf `capture/CaptureService.java` L111-127) lets a
rival take an owned turf **only when every member of the owning gang has been offline for 10+ minutes**
(`Post_Logoff_Protection_Minutes`), and `tickIdle` additionally needs zero defenders inside. So gangs never fight each
other for owned land; they wait for each other to log off, and the whole garrison/Quartermaster kit is an offline-defence
toy that nobody sees. Income is silent, gang level is never fed (GR-19), turf points and bank deposits share one field
(`Member.contribution`, so turf activity buys a bigger disband payout), nothing caps how much land or how many members a
gang holds, and every player kill already raises wanted (`EntityDamageListener.handlePlayerKills` L100-148) so a gang
defending its own turf gets police heat for it. The fix is not more features; it is **one visible fight loop** with a map
to find the fight, a warning to show up for it, NPCs to cover you when you cannot, and caps so it does not snowball.

---

## 1. Player-facing design

### 1.1 The core loop, in five sentences a player can repeat

1. **Form a gang**: `/glw gang create <name>`, invite friends, and your gang gets a bank, a colour and a letter on the map.
2. **Claim land**: stand inside an unclaimed turf with more of your gang than anyone else until the bar fills.
3. **Hold land to earn**: every 10 minutes each turf you hold pays your gang bank and earns your gang respect (XP); higher
   gang levels let you hold more turf.
4. **Take land**: declare a raid on a rival turf; they get a 2-minute warning wherever they are, then you have 15 minutes
   to push the bar to full while they (and the defenders they hired) push it back.
5. **Protect land**: spend the bank on hired defenders at your turf's Quartermaster; they fight for you in waves when you
   are raided, even while you are offline, and a turf you lose shields the rest of your land for a while.

`/glw map` shows all of it in one glance: whose land is where, what is shielded, what is under raid, and what you can attack.

### 1.2 Mechanics catalogue (each earns its place or is cut in 1.4)

Legend for the "Anchor" column: existing class = verified in code; **NEW** = proposed. Paths are module-relative:
`GG` = `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland`, `GT` = `gangland-features/gangland-turf/src/main/java/org/luckyraven/gangland/turf`,
`GM` = `gangland-features/gangland-mail/...`, `GC` = `gangland-features/gangland-civilians/...`, `CC` = `gangland-features/cops-n-crooks/...`.

| # | Mechanic | One-sentence rule (the player's version) | How the player learns it in-game | Anchor |
|---|---|---|---|---|
| M1 | Gang intro | "A gang is a crew with a shared bank that can own turf; creating one costs $100,000." | `/glw gang` as a non-member prints a 3-line intro with two click targets (`create`, `top`) instead of the raw help page. | `GG/command/sub/gang/GangCommand.onExecute` (today: help page) |
| M2 | Gang browser | "`/glw gang top` lists every gang by level; click one to see its card." | Intro link, phone menu `phone_gang_search.yml`. | **NEW** `GangTopCommand`, `GangInfoCommand` (gang module); reuses `GangMenuItemSourceContribution` data |
| M3 | Gang card | "`/glw gang info [name]` shows level, members online, turfs held, income and allies." | `/glw gang` for members opens it; `gang_stat.yml` (empty today, GR-17) renders the same card. | **NEW** `GangInfoCommand`; `inventory/gang_stat.yml` filled from placeholders |
| M4 | Gang chat | "`/glw gang chat <msg>` talks to your gang; `/glw gang chat` alone toggles it on." | Intro line, raid alerts land in the same channel with the same prefix. | **NEW** `GangChatCommand` + `GangChatListener` (`AsyncPlayerChatEvent`, re-dispatched to main thread) |
| M5 | Default ranks | "New members can deposit; officers can invite, raid and hire defenders; the owner can do everything." | Rank shown in the gang card and in `/glw gang members`; a "no permission" line names the rank that can. | `RankRepository.insertInitialRanks` seeds `member -> officer -> owner` on a fresh install (**D10**); `GangPermissions` gains `RAID`, `GARRISON` |
| M6 | Member cap | "A gang can have 8 members, +2 per gang level (max 20)." | Invite refused with "Vipers is full (8/8); level up to grow." | **NEW** `gang/gang_levels.yml` (module-owned YAML), checked in `GangInviteCommand` + `GangInviteAcceptCommand` (mail) through a **NEW** `GangLimits` read method |
| M7 | Claim | "Stand inside an unclaimed turf; the gang with the most members inside fills the bar in 90 seconds; an empty turf drains it." | Entering unclaimed turf as a gang member: action bar "Stay with your gang to claim Docks (90 s)" + the existing claim boss bar. | `CaptureService` contest core replaced by **NEW** pure `ContestMath` (one rule for claims and raids, **D6**) |
| M8 | Presence rule | "You count toward the bar once you have been inside, alive, for 15 seconds." | Action bar countdown "You count in 12 s" the first 15 s after entering, respawning or teleporting in. | `TurfLocationTracker` already caches per-player turf at 1 Hz; add entry timestamp; `CaptureService.classify` skips young entries |
| M9 | Income + ledger | "Each turf pays your bank every 10 minutes; online members see one line per payout." | Chat line "+$750 from 3 turfs (bank $12,400)"; `%gangland_turf_income%` on the Plaque sidebar. | `TurfIncomeDistributor.distribute` (silent today, TF-37) sends one aggregated line per gang |
| M10 | Respect (gang XP) | "Your gang earns respect every payout for each turf it holds; respect raises your gang level." | Level-up line to the gang (the existing, inert `GangLevelMessageListener` starts firing); card shows XP bar. | **NEW** `GangProgression.award(gangId, xp)` fires `GangLevelUpEvent` (GR-19); called by `TurfIncomeDistributor` |
| M11 | Turf cap | "Your gang can hold 2 turfs, +1 per level (max 6)." | Map header `Turfs 3/4`; claim/raid refused with "Level up to hold more turf". | **NEW** `turf/turf_rules.yml` `Cap_By_Level`; checked at raid declaration and at claim start |
| M12 | Raid | "Stand inside a rival turf and declare a raid for $2,500 from your bank; after a 2-minute warning you have 15 minutes to fill the bar." | Entering enemy turf: clickable line "Rats own Docks. [Declare raid]" (SUGGEST_COMMAND, never RUN, so no misclick spends money); map hover/click on an enemy cell. | **NEW** `/glw turf raid <turf>` (`TurfRaidCommand`), **NEW** `RaidService` (state lives in `TurfRuntimeState`) |
| M13 | Raid warning | "When your turf is raided, every online member hears it at once, with direction and head-count." | Radio line `[Docks] Rats are coming, 3 of them from the north-east` + the existing capture boss bar for owner-gang members anywhere. | `SquadRadio` with **NEW** additive `RadioVoice.audience(NpcSquad)` (api 2.0 -> 2.1); **NEW** `TurfRadio` |
| M14 | While you were away | "If you were offline, you see what happened to your turf the next time you log in." | Join digest: "While you were away: Rats raided Docks, your 3 defenders held it." | **NEW** `turf_log` table + `TurfDigestListener` (turf-owned, no mail edge, **D12**) |
| M15 | Allies | "Allies fight on your side in raids, if you were allied when the raid was declared; a gang can have 2 allies." | Map colour aqua; raid alert names the snapshotted sides: "Kings will defend with you." | `Gang.isAlly` made symmetric (TF-24); **NEW** `Max_Allies` in `gang_levels.yml`, checked in mail's `GangAllyAcceptCommand`; snapshot held by `RaidService` |
| M16 | Shields | "A turf is shielded for 30 minutes after it is defended or captured, and losing a turf shields all your other turfs for 2 hours; a raid nobody showed up for gives no shield." | Map draws shielded turfs in the dark shade; entering one: "Shielded for 12 min". | Existing `Cooldown_Minutes` generalised; **NEW** persisted `turf.shield_until` column (also fixes TF-27's post-restart IDLE text) |
| M17 | Hired defenders | "Hire defenders at the Quartermaster; they sit in reserve and deploy in waves when your turf is raided; survivors go back to the reserve." | Quartermaster panel shows reserve, max alive, price; raid alert says "Deploying 4 defenders." | `GarrisonManager` (stock = reserve), `GarrisonDeployListener` rewritten into **NEW** `TurfGarrisonSquad` (cnc T2/T3) |
| M18 | Defenders hold the bar | "Every live defender inside the turf pushes the bar back like one player." | Boss bar title shows `3 raiders vs 2 + 4 defenders`. | `ContestMath` counts `TurfGarrisonSquad.liveInside(turfId)`; this replaces the unwired "phantom defender" `CAPTURE_DEFENSE_BONUS` idea |
| M19 | Defender bounty | "Killing a hired defender drops half of what the owner paid for it." | Money drop on the body + the owner's reserve count falls. | **NEW** `TurfDefenderMoneyDropSource` (needs H13 `gl13-drop` classifier, cnc T9) |
| M20 | Reinforced defence buff | "Reinforced defence makes raids on this turf 50% slower for 30 minutes." | Quartermaster buff catalogue; boss bar suffix "(reinforced)". | `EffectType.CAPTURE_DEFENSE_BONUS` read by `ContestMath` (TF-02, **D9**) |
| M21 | Defender heat exemption | "Kills you make while defending your own turf during a raid do not raise your wanted level." | Raid-start alert includes the line once; raiders get the existing wanted message on kills. | **NEW** additive `default boolean exemptsKill(Player killer, Entity victim)` on core `WantedKillTracker`; implemented by `CC/seam/KillComboWantedTracker` reading turf raid state (cops `Depends: [turf]`) |
| M22 | Turf map | "`/glw map` shows the land around you: your letter in green, allies in aqua, enemies in red, `!` where a fight is on." | Intro line, enter-turf title suffix "(/glw map)", the gang card. | **NEW** `turf.map` package (section 2) |

### 1.3 What the player sees at each step (the natural path)

| Moment | Existing surface kept | Change |
|---|---|---|
| First join, no gang | nothing | `/glw gang` intro (M1); phone `phone_gang.yml` unchanged. |
| Walks into turf | enter title + action bar (`TurfActionBarListener`), presence boss bar (`TurfPresenceBarListener`) | Subtitle names the relation and the one next action (claim / raid / shielded Nm / yours). `TurfCaptureFeedbackListener`'s blocked-reason lines are rewritten to the new rules (shield time left, cap reached, "declare a raid"), replacing "owner protected". |
| Claims | claim boss bar (`TurfBossBarListener`) | One bar instead of two (M7); M8 countdown. |
| Holds | presence bar | Payout line (M9), respect (M10), sidebar tokens (section 2.4). |
| Gets raided | owner-gang boss bar anywhere, `TurfCaptureNotifier` chat at start/50% | Radio lines replace the notifier lines (M13), offline digest (M14), defenders deploy (M17). |
| Raids | `TurfCaptureNotifier` | Raid command + warning countdown bar "Raid on Docks starts in 1:40"; MVP line on result ("Top raider: Ryan, 4 min inside"). |
| Loses / wins | global broadcast (`Broadcast_Globally`) | Broadcast kept; loss shield (M16) announced to the loser. |

### 1.4 Cut, merged or deferred (YAGNI applied visibly)

| Thing today | Verdict | Reason |
|---|---|---|
| `Post_Logoff_Protection_Minutes` and the "owner fully offline" gate in `CaptureService.isCapturable` | **Cut** | It inverts the loop (section 0). Declared raids + shields + garrison replace it (**D1**, **D2**). |
| Two-phase unclaimed capture (CLAIM then CONSOLIDATE, two boss bars, rollback to CLAIM@100) | **Merged** into one bar (M7) | Two rules for "take land" is the most confusing part of turf; TF-11/TF-12 debt lives here. **D6**. |
| Phase-1 "steal" and `dominantGang(..., exclude)` (TF-11) | **Cut** | Documented but never implemented; the unified rule makes it moot. |
| Third gang ignored on an owned contest (TF-26) | **Kept, documented** | Only the raid's two snapshotted sides count; a third gang can still shoot, it just does not move the bar. One sentence, no new rule. |
| `garrison_discount` powerup (`EffectType.GARRISON_DISCOUNT`) | **Cut** (**D9**) | A discount on a purchase is not a gameplay decision; it has no consumer (TF-02). Removed from `turf_powerups.yml`; the loader skips unknown ids for rows already bought. |
| `reinforced_defense` powerup | **Wired** as M20 | One sentence, a real counter-decision for owners. |
| Turf contribution points written into `Member.contribution` (`TurfContributionTickTask:97`, `TurfContributionListener:68`) | **Cut** | Dual-unit exploit: turf points inflate the disband payout share (`GangDeleteCommand.payouts`). Contribution goes back to money-only; turf activity feeds gang XP (M10). The per-player tick task and the TF-16 Y filter go with it. |
| Per-member turf score | **Not added** | The raid MVP line uses in-memory contest stats; no new column. |
| XP for winning or defending raids | **Not added** | Farmable with an alt gang (raid your own alt, "defend" against it). XP only from holding (M10) cannot be farmed without holding land. |
| Gang bounty (`Gang.bounty`, `GangBountyEvent`, `GangBountyMessageListener`) | **Cut the dead event + listener**; column left inert | No rule earns it a place next to the user bounty; nothing posts or pays it (GR-19 bounty half). |
| `Gang.State` OPEN/INVITE/CLOSE | **Deferred** (W4 optional) | Open join is nice for onboarding but also an alt vector; ship after M8 and the join-age rule prove out. |
| Per-gang custom ranks | **Deferred** | Schema change (`rank_tree` has no gang column); the default preset (M5) fixes the real pain. |
| `gangland.turf.capture|contribute|upgrade` rank nodes (only in `RankPermissionApplierTest` fixtures) | **Replaced** by `gangland.gang.raid`, `gangland.gang.garrison` | Nodes nothing reads are worse than none; turf reads `GangPermissions` (turf already `Depends: [gang]`). |
| Upkeep per turf | **Not added** (**D5**) | Turf cap by level (M11) does the anti-snowball job with one visible number. |
| Re-ally cooldown | **Not added** | The raid-time alliance snapshot (M15) already defeats mid-raid ally swapping. |
| Gang `ping` callout (cnc T11) | **Deferred** | Gang chat (M4) is the channel; add `ping` only if players ask. |
| `alliance_stat.yml` button to `/glw gang ally info` (no such command, GR-16) | **Retargeted** to `/glw gang info <ally>` | Reuse M3, no new command. |

---

## 2. The map

### 2.1 Decision: `/glw map`, owned by the turf module

- `/glw map` (not `/glw turf map`): it is the most used read command and the Factions reflex is a short `/f map`.
  Feasible: turf registers `COMMAND_PACKAGE` in `TurfModule.configure` (verified) and module-owned roots already exist
  (`rank` is a root owned by the gang module), so **NEW** `turf.command.map.MapCommand` (`@CommandHandler`, root `map`)
  registers `/glw map`. One name, no alias (**D3**). Entries go in the turf module's own `commands.json`.
- Claims model stays: admin-drawn X/Z cuboid turfs (`CuboidRegion`), rasterized onto chunk cells. Player-claimable chunks
  stay an optional owner decision (**D4**) because they would rewrite capture, garrison and powerup ownership.
- Recommended companion: `Turf.Snap_To_Chunks` (turf YAML, default **on for new turfs only**) snaps the two corners at
  `TurfCreateCommand` and `TurfShowCommand.renderSelection` through **NEW** `CuboidRegion.snappedToChunks()`
  (`min & ~15`, `max | 15`), so new land has no partial cells. Existing turfs untouched.

### 2.2 Render rules (MVP)

| Rule | Value |
|---|---|
| Window | 41 x 9 cells, fixed north (rows north->south, columns west->east), odd sizes so the viewer is centred. `Width`/`Height` in **NEW** `turf/turf_map.yml`. |
| Cell | 16 x 16 blocks (a chunk) by default (`Cell_Blocks`). |
| Rasterizer | **NEW** `TerritoryRaster` (pure, no Bukkit types): per cell, intersect turf rectangles (via **NEW** `TurfChunkIndex`, per-world `Map<Long chunkKey, List<Turf>>`, rebuilt on create/delete/owner-change); the turf with the largest coverage owns the cell. Coverage >= 75% = upper-case glyph, else lower-case ("partial"). |
| Identity = glyph | Each gang in the window gets a letter: first letter of its resolved display name (`GangDisplayNameResolver`), collisions take the next free letter, allocated in ascending gang id so it is stable. Chat colour cannot carry identity: Keystone `Color` has 16 entries but 14 chat codes (`map-feasibility.md` 1.4). |
| Relation = colour | own = green, ally = aqua, enemy raidable = red, enemy shielded = dark red, unclaimed turf `-` = gray, no turf `.` = dark gray. |
| Overlays, highest first | viewer `+` (white bold; facing arrow only if `Player_Glyph` is set), `!` gold = raid active or claim in progress, `!` yellow = raid warning phase, then the turf glyph. |
| Header | `Map - <world> (x, z) facing NE - Turfs 3/4 - Shield: none` (the last two only for gang members). |
| Legend | Up to two lines of `letter name (relation)` for gangs present in the window, then the fixed key line. The legend repeats every fact the colours carry, so Bedrock/Geyser players without hover lose nothing. |
| Hover (per cell) | Turf name, owner, relation, state (idle / claim N% / raid warning m:ss / raid N% / shielded m:ss), income per interval, garrison reserve (own and ally turfs only), cell block coordinates. |
| Click | own / ally / unclaimed / shielded: `RUN_COMMAND /glw turf info <id>`. Enemy and raidable: `SUGGEST_COMMAND /glw turf raid <id>` (typed, never run, because it spends money). Admins additionally see `/glw turf tp <id>` in the hover. |
| Size | Run-length merge adjacent cells with equal glyph, colour and hover into one component (most of a map is wilderness). |
| Auto map | `/glw map auto` toggles re-sending on **cell** change, driven by the existing 1 Hz `TurfLocationTracker` tick (its javadoc forbids `PlayerMoveEvent`), minimum 2 s between sends (`Auto_Min_Interval_Seconds`), and only when the rendered window hash changed. Prefs in memory, cleared on quit. Default off. |

### 2.3 Sample frame

Viewer in **Vipers** (level 3, holds 3 of 4 allowed). **Kings** allied. **Rats** enemy: the west block is raidable, the
east block is shielded (drawn dark red; shown here with the same letter). Kings' Docks East is under an active raid (`!`).

```
Map - world (1240, -310) facing NE - Turfs 3/4 - Shield: none
..RRRRRRRR.-------.......................
..RRRRRRRR.-------......KKKKKKKKK........
..RRRRRRRR..............KK!!!KKKK.RRRRRR.
..RRRRRRRR....vVVVVVVVV.KK!!!KKKK.RRRRRR.
..............vVVVVV+VV.KKKKKKKKK.RRRRRR.
..............vVVVVVVVV...........RRRRRR.
..............vVVVVVVVV..-------..RRRRRR.
.........................-------.........
.........................-------.........
V Vipers (you)   K Kings (ally)   R Rats (enemy, east block shielded 12m)
- unclaimed   . no turf   ! fight on   lowercase = partly inside   + you
```

Hover on the `!`: "Kings - Docks East - raided by Rats - 62% - 9:40 left - click for info". Hover on a west `R`:
"Rats - Riverside - income $250 / 10 min - raidable - click to prepare `/glw turf raid 7`".

### 2.4 Expansions ladder (each only after the previous proves itself)

| Layer | What | Command / surface | Where | Wave |
|---|---|---|---|---|
| L1 MVP | Chat grid, hover/click, legend, auto | `/glw map`, `/glw map auto` | `turf.map` (`TurfChunkIndex`, `TerritoryRaster`, `TurfMapRenderer`, `MapPrefsService`, `MapCommand`) | W1 |
| L2 Context | PAPI tokens `%gangland_turf_name|owner|state|progress|relation|income|owned_count|cap|shield|nearest_name|nearest_distance|nearest_direction%` so Plaque shows a turf line with zero Plaque change; action-bar compass "Enemy turf Docks - 120 m NE" via Keystone `ActionBarManager.sendBackground` (yields to the enter/exit foreground lines) | `/glw map compass` toggles the action bar (default on for gang members) | **NEW** `TurfPlaceholderContribution` bean (sibling of `GangPlaceholderContribution`), tracker tick | W1 |
| L3 Zoom | 8/16/32/64-block cells with matching window, because cuboid hoods are not chunk-sized | `/glw map zoom <in|out|blocks>` (chained `OptionalArgument`) | `TerritoryRaster` parameter | W4 |
| L3 Borders | Player-facing border of the turf you stand in or the nearest, 10 s, gang-coloured dust via `XParticle`, **distance-clipped** (only points within ~40 blocks, adaptive step) | `/glw map border` | fix `task/TurfVisualization` cost first (new triage row; TF-21 viz half) | W4 |
| L3 Web | dynmap area markers (Spigot, lowest effort), BlueMap second in its own jar (Java-floor risk). squaremap and Pl3xMap are Paper-only: excluded. Rectangle is `(minX, minZ)-(maxX+1, maxZ+1)` (inclusive max). `Show_Owner` / `Show_Income` privacy flags. | none (soft dep) | `turf.map.web.DynmapTurfLayer`, `BlueMapTurfLayer`; `softdepend:` in core `plugin.yml`; `ReflectionGuard` isolates absent types | W4 |
| L4 Map item | Contextual `MapRenderer` on one shared `MapView` (never `createMap` per player), cached raster, per-gang palette colours (`Color.getBukkitColor()` is distinct), cursors for gang-mates and `RED_X` for raids | `/glw map item` (admin/shop) | `turf.map.item` | W4, owner opt-in |
| L4 Overlays | A `TurfMapLayer` contribution seam so cops-n-crooks could draw jails and heat without turf knowing it | none | only once L1 is proven and a second layer exists | not scheduled |

Keystone promotion: the painter (window maths, legend allocator, run-length row builder) is the candidate for
`keystone-common` **the day a second consumer exists**; it is built Bukkit-free inside `turf.map` so the move is mechanical.

---

## 3. Competitive rules in detail (the lens)

### 3.1 Contest math (one rule, `ContestMath`, pure and unit-tested)

- Sides: for a **claim**, every gang with counted members inside, except a gang already at its turf cap (M11), which is a
  bystander; the leader is the gang with the most counted members (ties stall). For a **raid**, exactly two sides fixed at declaration: raider gang + its allies, owner gang + its allies
  (the M15 snapshot). Everyone else is a bystander for the bar.
- Counted = inside for >= 15 s continuously and alive (M8). `player.isDead()` skip stays (`CaptureService.classify` L150-155).
- Owner side adds `TurfGarrisonSquad.liveInside(turfId)` defenders (M18).
- Raid counting uses the join-age filter on **both** sides: only members who joined their gang >= 2 hours before the
  declaration count (`Member.gangJoinDateLong`).
- Rate per second: `delta = (attackers - defenders) * 100 / Duration_Seconds`; if the turf is reinforced (M20) and
  `delta > 0`, divide it by 1.5 (the buff slows raiders only, never the owners' pushback); empty turf drains at the 1v0
  rate (today's `-1` convention). Raid: `Duration_Seconds` 180 default. Claim: 90.
- Raid ends: bar reaches 100 -> **captured**. Window (15 min) expires or bar sits at 0 with no counted raider for
  `Abandon_Grace_Seconds` (finally honoured, TF-12): if the bar **ever left 0** -> **defended** (shield), otherwise
  -> **abandoned** (no shield, fee kept), matching the existing `TurfCaptureFailedEvent.Reason` values. Server restart or
  admin action -> **cancelled** (fee refunded, see 3.3). All outcomes fire through one ownership path (W0 L0.1).

### 3.2 Raid lifecycle (`RaidService`, state in `TurfRuntimeState`)

```
IDLE --declare (fee, cap, shield, one-raid-per-gang checks)--> WARNING (120 s: radio alert, garrison deploys to posts, map `!` yellow)
WARNING --timer--> ACTIVE (15 min window, bar moves per ContestMath, map `!` gold)
ACTIVE --bar 100--> CAPTURED  -> owner changes, turf shield 30 min, loser's other turfs shield 2 h
ACTIVE --window over / abandon grace, bar moved--> DEFENDED -> turf shield 30 min
ACTIVE --window over / abandon grace, bar never left 0--> ABANDONED -> no shield, fee kept
any   --restart / admin setowner / turf delete / gang disband--> CANCELLED -> fee refunded, no shield
```

Declaration checks, each with its own refusal line: the declarer stands inside the target turf (grounds the radio
line's compass direction and stops remote declarations), has `gangland.gang.raid` (M5), the turf is owned by a
non-allied gang, the turf is not shielded, the raider gang holds fewer turfs than its cap (M11), the raider gang has no
other raid in WARNING/ACTIVE, and the bank covers `Raid_Fee`. Unclaimed land needs no declaration (M7).

### 3.3 Economy: faucets and sinks (defaults to tune, owner signs off in **D14**)

| Flow | Default | Direction | Rationale |
|---|---|---|---|
| Turf income | 250 per 10 min per turf (today `Default_Income_Amount` 100) | faucet | At 100 the buffs never pay back (turf census 3.2). |
| Gang create | 100,000, 25% back on disband (unchanged) | sink | |
| Raid fee | 2,500 (about 1.7 h of one turf's income) | sink, refunded only on CANCELLED | Stops harassment spam; a real decision. |
| Defender hire | 1,000 each (`Garrison.Defender_Cost`, replaces the hardcoded 1500, TF-17) | sink | Survivors return to reserve, so it is a stock, not a burn. |
| Defender bounty | 50% of hire cost to the killer | transfer (owner -> raider), never minted | Rewards attackers, punishes thin defence, cannot be farmed for net money (**D13**). |
| Income boosts | small 1.25x/1 h at 300, large 1.75x/1 h at 1,000 | sink | Pays back only if the turf is held for the hour. |
| Reinforced defence | 30 min at 1,500 | sink | |
| Ledger | one payout line per interval to online members | visibility | TF-37 silent payouts. Multiplier product capped at `Max_Income_Multiplier` 2.0 (TF-37 uncapped half). |

### 3.4 Anti-exploit matrix

| Exploit | Rule that closes it | Anchor |
|---|---|---|
| Offline raiding (waiting for owners to log off) | No offline gate at all; raids are announced (warning + radio), owners who cannot come are covered by paid defenders that hold the bar (M18), and a loss shields the rest of their land for 2 h (M16). | `RaidService`, `TurfGarrisonSquad` |
| Logging off to be immune (today's protection) | Impossible: presence no longer gates raids. | `CaptureService.isCapturable` rewritten |
| Alts padding head-count | Only members who joined their gang >= 2 hours before the raid was declared count for a raid, on both sides (`Member.gangJoinDateLong` already persisted, no schema change); plus the 15 s presence rule. | `ContestMath` input filter |
| Alt gangs farming XP | XP only from holding (M10); raids give none. Alt gangs also cost 100,000 and count against nothing else. | `GangProgression` |
| Alt gangs farming defender bounties | Bounty is paid out of the owner's own spend (neutral); self-farming loses 50% per kill. | `TurfDefenderMoneyDropSource` |
| Ally swapping mid-raid | Sides are snapshotted at declaration; an alliance formed later does not count. `Max_Allies` 2 stops server-wide blocs. | `RaidService` snapshot, mail `GangAllyAcceptCommand` cap |
| Asymmetric alliance rows (half-broken legacy rows) | `Gang.isAlly` checks both directions (TF-24), so friendly fire, capture sides and the map agree. | `GG/gang/Gang.java` L102 |
| Teleport or respawn "spawn-in" reinforcement, edge dancing | 15 s presence rule resets on death, respawn and teleport. | `TurfLocationTracker` entry timestamp |
| Defender NPCs spawned in raiders' faces | Waves spawn at posts out of any raider's line of sight (`LivingEntity.hasLineOfSight`, a handful of candidates). | `TurfGarrisonSquad` |
| Raid spam / griefing | Fee, one active raid per gang, declarer must stand inside the turf, 30 min post-raid shield per turf, 2 h loss shield. | `RaidService` |
| Self-shielding (an owner's alt gang declares, never shows up, and the turf gets a shield) | Only DEFENDED and CAPTURED shield; an ABANDONED raid (bar never left 0) gives no shield and keeps the fee. | `RaidService` outcome split |
| Snowballing | Turf cap by level; member cap by level. | `turf_rules.yml`, `gang_levels.yml` |
| Disband-payout farming via turf points | Contribution back to money-only. | `TurfContributionTickTask`/`TurfContributionListener` removed |
| Recruit spending the bank at the Quartermaster | `gangland.gang.garrison` required, re-checked on every click (TF-19). | `TurfPowerupOpenContractImpl`, garrison/buff views |
| Garrison burned with no Citizens | Stock is only consumed after a successful spawn; without Citizens nothing deploys and nothing is spent. | `GarrisonDeployListener` (new triage row) |
| Defenders getting police heat for defending | M21 exemption inside their raided turf only; raiders keep normal wanted, so police arrive for loud raids on their own. | `WantedKillTracker.exemptsKill` + `KillComboWantedTracker` |
| Disbanded gang still owning turf / capturing into a dead gang | One ownership path handles `GangDeleteEvent` and aborts completes into missing gangs (TF-01, TF-25). | W0 L0.1 |

### 3.5 Cop heat, precisely

Verified: every real-player kill calls `handleWanted` (or the cops' `KillComboWantedTracker.recordKill` when the kill
combo is on) in `gangland-impl/.../listener/player/EntityDamageListener.handlePlayerKills` L100-148, and NPC kills go
through `WantedKillTrackers.countsForWanted` (turf defenders in COMBAT already cost no heat per `CivilianDeathRewardListener`).
`WantedEvent` is constructed but never passed to `callEvent` (only `WantedStartEvent`/`WantedEndEvent` are, `Wanted.java`
L85-87), so it cannot be used to exempt anyone. Impl cannot name turf. The seam is therefore:

1. Core (`gangland-core/.../wanted/WantedKillTracker.java`): add `default boolean exemptsKill(Player killer, Entity victim) { return false; }`;
   `WantedKillTrackers` forwards it. Additive on an interface the api re-exports: api minor 2.0 -> 2.1 (same bump as M13).
2. Impl `EntityDamageListener.handlePlayerKills`: `if (wantedKills.exemptsKill(killer, victim)) skip handleWanted/recordKill`
   (bounty still applies).
3. Cops-n-crooks `KillComboWantedTracker.exemptsKill`: true when the killer's gang (or snapshotted ally) is the defending side of a
   turf in WARNING/ACTIVE and the killer stands inside it. Reads **NEW** read-only `RaidService.defendingSideAt(Location)`
   through cops' legal `Depends: [turf]`.

Without cops-n-crooks the delegate is inert, which is correct: no police, no heat question. The "Policed district"
dispatch line (cnc T5) is W4 and off by default.

---

## 4. Cops-n-crooks H11-H13 transfer

| cnc id | Mechanic | Verdict | Exactly how | Wave | Needs H13 |
|---|---|---|---|---|---|
| T1 | Turf alert radio to the owning gang anywhere | **Yes** (M13) | **NEW** `RadioVoice.audience(NpcSquad)` default `null` (= today's range rule) in `gangland-api/.../npc/radio/RadioVoice.java`; `SquadRadio.speak` delivers to the audience at any distance with the per-player gap. **NEW** `TurfRadio` in turf keys a member-less `NpcSquad` per turf (SquadRadio throttles on the object only). Lines in **NEW** `turf/turf_messages.yml`: `Raid_Declared` (with `RadioSides.compass8` direction and head-count), `Raid_Started`, `Milestone_50`, `Defender_Down`, `Turf_Lost`, `Turf_Held`. Replaces `TurfCaptureNotifier` chat lines. | W2 | no |
| T2 | Garrison as a per-turf squad with posts and a leash | **Yes** (M17) | **NEW** `TurfGarrisonSquad` owns one `NpcSquad` per turf; posts = corners + highest-surface centre (reuse `GarrisonDeployListener.regionCentreSurface`); leash = region + 16 blocks, then `navigateTo(post)` from `TurfDefenderDeployer.tick`. **Civilians change required**: `CivilianService.alertFaction` must route through `victim.getFactionSquads()` when set, or the faction id becomes `turf_defender:<turfId>`, otherwise the first hit drops the defender back into the shared `(faction, attacker)` squad. Index defenders by entity UUID (TF-35). | W3 | no |
| T3 | Reinforcement waves, reserve refund | **Yes** (M17) | Reserve = `GarrisonManager` stock; `Max_Alive` (default 4) alive at once; a `MAN_DOWN` or a 25% milestone triggers the next wave after `Wave_Cooldown_Seconds` 20; survivors and undeployed stock return at raid end (TF-20). Keep it turf-local (`WaveSettings` promotion to api only if cops adopt it). | W3 | no |
| T4 | Roles (shield, marksman, medic, commander) | **Deferred** | Needs a generic `NpcRole` promoted to `gangland-api` out of cop-shaped `CopRole` (`overlay(CopTierConfig)`), Keystone 1.14.0 `NpcFanPlacement`/`setLeaderPriority`/`setRangedBand`, and cops' `GL-CARE` medic (not built). Largest item, least clarity per line. | W4 | yes |
| T5 | Turf heat feeds wanted, cops join gang fights | **Partly** | M21 exemption now (section 3.5); raiders' kills already raise wanted, so cops arrive by the existing wanted dispatch. The per-turf `Policed` flag + "Shots fired at Docks" dispatch is W4, off by default. | W2 / W4 | no |
| T6 | Gang-tagged callsigns | **Yes** | After `spawnCivilian`, name defenders `[Vipers] Defender #1042` and the Quartermaster `[Vipers] Quartermaster` in the owner's colour; `Shouts.Format` uses `%gang%` instead of `[%faction%]` for turf types (fixes `[turf_defender] Turf Defender:`). Promote `CopNames` to shared `NpcNames` only if the format is truly shared; otherwise a turf-local formatter. Never `CIT-` in names, <= 16 chars. | W3 | shares `gl13-names` format |
| T7 | Health bars on defenders and Quartermaster | **Yes, verify only** | `gangland-healthbars` draws for transient unprotected Citizens NPCs; `CivilianNpcFactory` L92-93 already qualifies them. Work = smoke check line layout with the T6 name. Note its `module.yml` `Plugins: [Citizens]` fail-fast differs from the house "Citizens is soft" convention; owner keeps or relaxes it in the H13 wave, not here. | W3 | yes |
| T8 | Neighbour and allied responders | **Deferred** | Rewards contiguous land, but cascade fights and cross-turf squad logic need T2 proven first. | W4 | no |
| T9 | Defender bounty drop | **Yes** (M19) | **NEW** `TurfDefenderMoneyDropSource` shaped like `CopsMoneyDropSource`; allied/owner kills excluded like `TurfFriendlyFireListener`. | W3 | yes (`gl13-drop` classifier fix `5b7325b5`) |
| T10 | Commander-down morale | **Deferred** | Needs leader priority (T4). | W4 | yes |
| T11 | Player man-down / ping callouts | **Folded** into gang chat (M4); `ping` deferred | | W1 | no |
| T12 | Stuck recycling | **Yes** | In `TurfDefenderDeployer.tick`: unreachable (`AbstractNpc.millisUnreachable()`) for `Recycle_Seconds` and out of every raider's view -> respawn at a different post. Only after the cop version passes live acceptance. | W3 | yes (Keystone 1.14.0) |
| T13 | Stand-down and walk home | **Yes** | On raid end, radio `Turf_Held`/`Turf_Lost`, survivors walk to a post and despawn after 10 s back into the reserve instead of vanishing (`defenders.recall` today). | W3 | no |

Precondition from `cnc-ideas.md` 5.2: sandbox-accept today's faction-alert behaviour (H12 left it unit-test-only)
before W3 builds on it. That acceptance is the first task of lane L3.1.

---

## 5. Docket integration

Every open entry lands in exactly one lane or in the deferred list. Status is written to the Gangland docket
(`bugs` collection) and mirrored to the cross-docket per CLAUDE.md, with commit, branch, test and anything left out.

### 5.1 GR (25 open: GR-10..17, 19..24, 27..37)

| Lane | Entries | Notes |
|---|---|---|
| L0.4 gang hardening | GR-12, GR-15, GR-20, GR-27, GR-28, GR-30, GR-32, GR-33, GR-34, GR-35, GR-36 | Flip `RankManagerTest` (GR-12) and `GangAllianceTest` (GR-35) red -> green. GR-32: delete dead `MemberManager.initializeMemberData`, fix `MemberRepository.doLoadAll` casts with `Number`. GR-27 lives in `gangland-api` `Settings.java:565` (no api signature change). |
| L0.5 mail hygiene | GR-11, GR-14 (mail half), GR-23, GR-24, GR-31 | GR-11 async `expiryTimer` -> sync + cancel (concurrency, Sonnet with Opus review). GR-24 = **NEW** `GangInviteDeclineCommand` + `commands.json`. |
| L1.4 gang onboarding | GR-16, GR-17, GR-29 | GR-29 name rules (length 3-16, `[A-Za-z0-9_]`, no `&` codes) before names appear on map legends and raid broadcasts. |
| L1.5 ranks | GR-10, GR-13, GR-21, GR-22, GR-37 | Seed path (GR-13/21) is touched by the preset anyway; GR-37 validates nodes against the registered `gangland.gang.*` set. |
| L2.2 progression | GR-19 | Level half wired (M10); bounty half cut (1.4). |

### 5.2 TF (38 open: TF-01, TF-02, TF-04..TF-39)

| Lane | Entries | Notes |
|---|---|---|
| L0.1 ownership pipeline | TF-01, TF-04, TF-10, TF-13, TF-25, TF-28 | Flip `CaptureServiceStartAndCompleteTest` (TF-01). Adds the TF-03 residual and the "stock burned without Citizens" guard (new rows 1, 6). |
| L0.2 persistence, index, admin surface | TF-05, TF-14, TF-15, TF-21 (region-size half), TF-23 (wand/status strings), TF-29, TF-30, TF-34, TF-39 | Flip `TurfManagerTest` (TF-05: never reuse a deleted id). TF-30: one namespace, `gangland.turf.admin` for staff, `gangland.command.turf.*` for players, `list/info/status/select/tp/show` split into player-safe (`info`, `status`) and staff (`tp`, `select`, `show`). TF-39 = **NEW** `TurfRenameCommand`. |
| L0.3 lifecycle | TF-06, TF-07, TF-08, TF-09, TF-32, TF-36 | Every task-owning bean implements `BeanLifecycle` before W1-W3 add more tasks; new row 2 (double init). |
| L0.6 docs + triage | TF-31 | Document-only (staff buff is free by design). |
| L2.1 capture + raids | TF-02 (defence-bonus half), TF-11, TF-12, TF-16, TF-26, TF-27 | Flip `CaptureServiceOwnedTurfTest` (TF-12). TF-16 disappears with the contribution tick; TF-26 closed as documented design; TF-27 closed by the persisted shield. |
| L2.3 alerts + ledger | TF-37 | Payout ledger line + multiplier cap. |
| L0.4 gang hardening | TF-24 | Lives in the gang module (`Gang.isAlly`). |
| L3.2 garrison squad | TF-02 (discount half), TF-17, TF-18, TF-19, TF-20, TF-23 (Quartermaster panel strings), TF-33, TF-35, TF-38 | New row 3 (Quartermaster pending-queue orphan) with TF-33. |
| **Deferred** | TF-22 (Spanish turf messages) | Ships with the CM-15 i18n item; new turf strings go to `turf/turf_messages.yml` so the Spanish file becomes one module file later. |

### 5.3 New triage rows (lane L0.6 writes `triage/<slug>.txt` and rebuilds with `build_docket.py` before W0 code starts)

| # | Slug | Finding | Fixed in |
|---|---|---|---|
| 1 | `turf-delete-leaks-contest` | TF-03 residual: `TurfDeleteCommand`/`TurfManager.delete` never cancel an in-flight contest; boss bars and defenders leak. | L0.1 |
| 2 | `turf-beans-double-init` | `TurfModuleConfig.garrisonManager`/`turfManager` call `initialize()` beside the LIFECYCLE convention. | L0.3 |
| 3 | `turf-qm-pending-orphan` | `TurfPowerupManager.remove` never purges `pending`, a removed Quartermaster can respawn on chunk load. | L3.2 |
| 4 | `turf-visualization-cost` | `TurfVisualization` ~4,200 particles/s/viewer on a 256x256 turf; must clip before any player-facing border. | W4 borders |
| 5 | `gang-contribution-dual-unit` | Turf points and deposits share `Member.contribution`; turf farming inflates disband payouts. | L2.2 |
| 6 | `turf-garrison-burned-no-citizens` | `GarrisonDeployListener` consumes stock before `deploy` no-ops without Citizens. | L0.1 |
| 7 | `turf-rank-nodes-unconsumed` | `gangland.turf.capture|contribute|upgrade` have no production reader; any recruit spends the bank. | L1.5 + L3.2 |
| 8 | `turf-defender-squad-by-faction` | Defender squads key on `(faction, target)`; two turfs' garrisons share one squad. | L3.1 |
| 9 | `gang-enable-ignored` | `Gang.Enable: false` no longer disables gangs (only civilians reads it). | L0.4 (remove the YAML key and its doc, keep the api getter deprecated; the module being loaded is the switch) |
| 10 | `gang-commands-json-drift` | `gang_change_name` says `name` (label `rename`), `gang_ally_remove` (label `abandon`), mail lacks `ally accept/reject`. | L1.4 |
| 11 | `defender-pvp-heat` | Defending your own turf raises wanted (design gap, P3). | L2.4 |

Before writing any row, L0.6 checks the docket for an existing row (for example the GR-02 note's adjacent "payout loop re-reads the balance" bug); a missing row means unrecorded, not new.

### 5.4 Pinned tests to flip (red first against pre-fix code, then green)

`CaptureServiceStartAndCompleteTest` (TF-01, L0.1), `TurfManagerTest` (TF-05, L0.2), `CaptureServiceOwnedTurfTest`
(TF-12, L2.1), `RankManagerTest` (GR-12, L0.4), `GangAllianceTest` (GR-35, L0.4), plus `RankPermissionApplierTest`
fixtures that name `gangland.turf.*` nodes (L1.5).

---

## 6. Delivery: waves -> lanes

### 6.1 Target and dependencies

- **Target: Gangland 0.14.0** (next free revision after H13's 0.13.0), integration branch `gang-turf-0.14.0` in worktree
  `E:/Programming/java/wt/gangland-0.14.0`. Revision convention is **D11** (minor vs the memory's patch-bump habit).
- **Hard dependency: H13 merged to master first** = Gangland `npc-roles-medics` (head `f9295ec0`, rev 0.13.0; its tip still
  pins Keystone 1.13.0) plus sub-branches `h13-roles`, `h13-stuck`, `h13-names`, `h13-drop` (worktrees `wt/gl13-*`), which
  carry Keystone **1.14.0** (`phase-h13-npc-roles`, `wt/keystone-1.14.0`). After the merge the wave's Keystone pin is 1.14.0
  (a 0.14.0 jar on a 1.13.0 server fails with `NoSuchMethodError`).
- Lanes that **hard-gate on H13 merged**: L3.3 (health bars T7, bounty drop T9 needs the `gl13-drop` classifier, stuck
  recycling T12 needs `millisUnreachable()`), any W4 roles/commander work (T4/T10: `NpcFanPlacement`, `setLeaderPriority`).
- Lanes that **may start from master `ff9d813f` and rebase** if H13 slips: all of W0 and W1 (they touch gang, mail, turf,
  api `Settings` only, none of the H13 files). W2 L2.3/L2.4 touch `gangland-api` and `cops-n-crooks`; start them after H13
  merges to avoid rebasing over `CopRadio`/`KillComboWantedTracker`-adjacent changes.
- **Keystone: no lane in W0-W3.** Nothing here requires a Keystone change. Upstream promotion candidates, each a separate
  lane with a Keystone 1.15.0 bump and phase doc **only when a second consumer appears**: the map painter
  (`keystone-common`), an `AbstractNpc` hold-post-and-leash primitive (`keystone-npc`), a region-relative out-of-sight spawn
  helper on `EntitySpawner`.
- **Api**: one additive minor, `GanglandApi.VERSION` 2.0 -> **2.1** (`RadioVoice.audience`, `WantedKillTracker.exemptsKill`),
  both `default` methods. **turf** and **cops-n-crooks** override them, so their `module.yml` moves to `Host_Api: 2.1`
  (on a 2.0 host the features would silently do nothing; the descriptor refuses that instead); gang, mail, civilians and
  the rest keep `Host_Api: 2.0` and load on a 2.1 host (Keystone 1.9.2 minor floor). Documented in
  `documentation/gangland-api.md`. Nothing is removed from the api: when L0.4 drops the `Gang.Enable` YAML key and L2.1
  drops the `Turf.Contribution.Points` keys, their `Settings` getters stay (marked `@Deprecated`) until the next major. No `Messages`/`Settings` additions: new strings and knobs go to module YAML
  (`turf/turf_rules.yml`, `turf/turf_map.yml`, `turf/turf_messages.yml`, `gang/gang_levels.yml`), copied out by a
  `FileHandler` built with the module classloader.
- **Module edges unchanged**: turf `Depends: [civilians, gang]`, mail `Depends: [gang]`, cops `Depends: [turf, civilians]`,
  `Plugins: [Bartizan]`. No new `Depends:`. civilians stays gang-free (it reads `GangMembership` only).

### 6.2 Model tiers and rules for every lane

Haiku 4.5 = census, docs, YAML, `commands.json`, triage rows, test scaffolding. Sonnet 5.5 = bounded implementation +
tests. Opus 5.5 = cross-module design, concurrency, adversarial review. Fable 5.1 = advisor/judge at gates.
At most **3 Opus agents concurrently**; agents never spawn sub-agents (the orchestrator hands each a file list and
graphify results); every exploring prompt includes "run `graphify query` first; read raw files only after the graph has
oriented you". Each lane runs in its own worktree `E:/Programming/java/wt/gl14-<lane>` branched from `gang-turf-0.14.0`,
merges back after its gate. Test gate for every lane: JUnit 5 + Mockito per `documentation/TESTING.md` (authoritative;
CLAUDE.md's helper names are stale per memory), every new test shown red against pre-fix code before green, DB-touching
tests use `@TempDir(cleanup = CleanupMode.NEVER)` and the release helper `TESTING.md` names, `mvn -pl <module> -am test`
green in the lane, `mvn clean install` green at merge, docket rows written at merge.

### 6.3 Wave W0: trustworthy foundations (no new mechanic; every later wave stands on it)

| Lane | Scope | Modules / files | Tier | Parallel | Depends on | Test gate | Exit |
|---|---|---|---|---|---|---|---|
| L0.6 | Triage rows 1-11 (5.3), docket rebuild; CLAUDE.md/doc drift notes (`Host_Api: 2.0`, turf `Depends: [civilians, gang]`); TF-31 doc | `brainstorming/bug-docket-2026-09-06/triage/*.txt`, `documentation/features/{gangs,ranks,turf}.md` | Haiku | yes, first | none | n/a (docs) | Docket rebuilt, rows visible |
| L0.1 | One ownership + contest-cancel path: **NEW** `TurfOwnership` (`claim`, `transfer`, `release`, `cancelContest(turf, Reason)`) used by `CaptureService.complete`/`cancel`, `TurfSetOwnerCommand`, `TurfDeleteCommand`, `InactivityReleaseTask`, `TurfIncomeDistributor` orphan path, **NEW** `TurfGangDeleteListener` (`GangDeleteEvent`); always fires `TurfOwnerChangedEvent` and `TurfCaptureFailedEvent(CANCELLED)`; abort complete into a missing gang; Citizens guard before `garrisons.consume` | `GT/capture`, `GT/command`, `GT/task`, `GT/listener`, `GT/listener/powerups/GarrisonDeployListener` | **Opus** | yes (1 Opus) | L0.6 | Flip `CaptureServiceStartAndCompleteTest`; new `TurfOwnershipTest` (every path fires exactly one owner-change event), `TurfGangDeleteListenerTest` | TF-01/04/10/13/25/28 + rows 1, 6 fixed; Fable gate G0 reviews the event contract before W1 builds on it |
| L0.2 | Persistence + index + admin surface: never-reused ids, `Number` casts, income precision, copy-on-write world lists, **NEW** `TurfChunkIndex` (used by `findAt` and later the map), region size limits, duplicate names, cross-world selection message, one permission namespace, **NEW** `TurfRenameCommand`, wand/status strings to `turf/turf_messages.yml` | `GT/database`, `GT/manager/TurfManager`, `GT/selection`, `GT/command/{TurfCreateCommand,TurfWandCommand,TurfStatusCommand}` | Sonnet | yes (worktree), disjoint from L0.1 files except `TurfManager` (L0.1 does not edit it) | L0.6 | Flip `TurfManagerTest`; `TurfRepositorySpiTest` (MySQL-shaped `Long` values), `TurfChunkIndexTest` | TF-05/14/15/21a/23a/29/30/34/39 fixed |
| L0.3 | Lifecycle: `BeanLifecycle` on `TurfLocationTracker`, `TurfIncomeDistributor`, `GangPresenceTracker`, `TurfContributionTickTask`, `TurfDefenderDeployer`, `ActiveBuffManager`, `TurfBossBarListener` task handle; `TurfLocationTracker.onQuit` wired; presence sweep initial delay; `TurfNpcsConfigLoader` reload; buff prune batching | `GT/TurfModuleConfig`, `GT/task`, `GT/listener/TurfBossBarListener`, `GT/npc/config` | Sonnet | after L0.1 merges (same task files) | L0.1 | `TurfModuleLifecycleTest` (reload cancels and restarts each task once) | TF-06/07/08/09/32/36 + row 2 fixed |
| L0.4 | Gang hardening: null guards, NULL-safe loads, `hashCode`, symmetric `isAlly`, console-safe `option gang`, `RankManager.clear` resets all maps, `Gang.Enable` YAML key removed (api getter kept, deprecated) | `GG/gang/**`, `GG/listener/gang`, `GG/command/sub/gang`, `gangland-api/.../Settings.java` (GR-27 only), `gangland-impl/.../ComponentExecutorCommand` (GR-36) | Sonnet | yes (gang module only) | L0.6 | Flip `RankManagerTest`, `GangAllianceTest`; `GangIsAllySymmetryTest` | GR-12/15/20/27/28/30/32/33/34/35/36, TF-24, row 9 fixed |
| L0.5 | Mail hygiene: sync expiry sweep with cancel, NPE in ally request/accept streams, accept clears sibling invites, offline invites expire (`Offline_Invite_Expiry_Hours` 72), **NEW** invite decline, tolerant row load | `GM/**`, mail `commands.json` | Sonnet (+ Opus review of GR-11 threading, shared with the W0 gate) | yes (mail module only) | L0.6 | `MailExpirySweepTest` (runs on main thread), `GangInviteDeclineCommandTest`, `MailRepositoryBadRowTest` | GR-11/14/23/24/31 fixed |

W0 gate (G0): Opus adversarial review of L0.1 + L0.5 diffs (1 Opus), Fable judge signs the ownership event contract.
Opus concurrency peak in W0: 2.

### 6.4 Wave W1: see the land, join a gang (map MVP + context + onboarding)

| Lane | Scope | Modules / files | Tier | Parallel | Depends on | Test gate | Exit |
|---|---|---|---|---|---|---|---|
| L1.1 | Map MVP: **NEW** `turf.map` (`TerritoryRaster` pure, `GlyphAllocator`, `TurfMapRenderer` with run-length merge, `MapPrefsService`, `MapCommand` root `map` with `auto`), `turf/turf_map.yml`, `Snap_To_Chunks` + `CuboidRegion.snappedToChunks()` | `GT/map/**`, `GT/data/CuboidRegion`, `GT/command/TurfCreateCommand` (snap only) | Sonnet | yes | L0.1, L0.2 (`TurfChunkIndex`, owner-change event) | `TerritoryRasterTest` (coverage, partial glyph, tie), `GlyphAllocatorTest` (stable, collisions), `TurfMapRendererTest` (component count after merge, click targets: raid = SUGGEST), `MapPrefsServiceTest` (cell-change + min interval) | Sample frame in 2.3 reproduced by a golden-text test |
| L1.2 | Context: **NEW** `TurfPlaceholderContribution` (tokens in 2.4, O(1) via the tracker cache), action-bar compass `/glw map compass` | `GT/placeholder`, `GT/task/TurfLocationTracker` (hook), `GT/config` | Sonnet | yes (disjoint from L1.1 except shared index) | L0.2, L0.3 | `TurfPlaceholderContributionTest`, `NearestTurfTest` (rectangle clamp distance, 8-way bearing) | Plaque line renders on the smoke server |
| L1.3 | `commands.json` entries for `map`, `map auto`, `map compass`, `turf rename`; `documentation/features/turf-map.md`; YAML lint via `gangland-yaml-review` | turf `commands.json`, docs | Haiku | after L1.1/L1.2 land | L1.1, L1.2 | YAML review clean | Help pages list every new leaf |
| L1.4 | Onboarding: intro page (M1), **NEW** `GangTopCommand`, `GangInfoCommand`, `GangChatCommand` + listener, name rules (GR-29), `gang_stat.yml` card, `alliance_stat.yml` retarget, `commands.json` drift (row 10) | `GG/command/sub/gang`, `GG/listener/gang`, `gangland-impl/src/main/resources/inventory/{gang_stat,alliance_stat}.yml`, gang `commands.json` | Sonnet (+ Haiku for YAML/json) | yes (gang module) | L0.4 | `GangNameRulesTest`, `GangTopCommandTest` (paging, order), `GangChatListenerTest` (async -> main dispatch, recipients = online members only) | GR-16/17/29, row 10 fixed |
| L1.5 | Ranks: fresh-install preset `member -> officer -> owner` with nodes, register every `gangland.gang.*` node incl. **NEW** `raid`, `garrison`, deposit ungated, node validation, seed-path fixes, transfer lock timer | `GG/gang/rank/**`, `GG/gang/database/repositories/rank/**`, `GG/gang/permission/GangPermissions`, `GG/command/sub/rank`, `GangTransferCommand` | Sonnet | after L1.4 merges (both touch gang commands) or in parallel if file lists stay disjoint | L0.4 | `RankSeedTest` (fresh install gets 3 ranks; existing install untouched), `GangPermissionsTest` extended, `RankPermissionAddCommandTest` (unknown node refused) | GR-10/13/21/22/37, row 7 (gang half) fixed |

W1 gate (G1): Opus review of L1.1 + L1.2 (1 Opus) and of L1.4 + L1.5 (1 Opus); smoke on the test server with the
`brainstorming/bartizan-split-2026-09-08/smoke/smoke.py` harness (console: `/glw map` output captured). Opus peak: 2.

### 6.5 Wave W2: the competitive core (raids, caps, progression, alerts, heat)

| Lane | Scope | Modules / files | Tier | Parallel | Depends on | Test gate | Exit |
|---|---|---|---|---|---|---|---|
| L2.0 | Raid rules spec: `ContestMath` inputs/outputs, `RaidService` state machine (3.2), shield/abandon split, snapshot, join-age filter, restart semantics; delivered as compiling stub signatures (methods throw `UnsupportedOperationException`) plus tests that compile and fail | `GT/capture` main stubs + test sources | **Opus** + **Fable** judge | first | W0, W1 merged | The spec tests compile and are red | Fable signs the rules in section 3 against the owner's decisions D1, D2, D5, D6, D7 |
| L2.1 | Capture rewrite: unified `ContestMath`, M7/M8/M11/M12/M15/M16/M18/M20, **NEW** `TurfRaidCommand`, `RaidService`, persisted `turf.shield_until`, blocked-reason lines, boss-bar titles, contribution tick removed | `GT/capture/**`, `GT/data/{Turf,TurfRuntimeState}`, `GT/database/{TurfTable,TurfRepository}`, `GT/command/TurfRaidCommand`, `GT/listener/{TurfCaptureFeedbackListener,TurfBossBarListener}`, `GT/contribution/**` (removed) | **Opus** | yes | L2.0 | L2.0 tests green; flip `CaptureServiceOwnedTurfTest`; `RaidServiceTest` (every transition, fee refund only on CANCELLED, one raid per gang), `ContestMathTest` (join-age, 15 s, defenders count, reinforced) | TF-02a/11/12/16/26/27 fixed; row 5 turf half |
| L2.2 | Gang progression + limits: **NEW** `GangProgression` (+`GangLevelUpEvent`), **NEW** `gang/gang_levels.yml` (XP curve, member cap, `Max_Allies`), **NEW** `GangLimits` read seam used by turf (cap lookup) and mail (member/ally caps), contribution money-only, `GangBountyEvent` + listener deleted | `GG/gang/**`, `GG/events/gang`, `GG/listener/gang`, `GM/command/{invite,ally}` (cap checks) | Sonnet | yes (gang + mail) | L1.5 | `GangProgressionTest` (level-up fires once per level), `GangLimitsTest`, `GangInviteCapTest`, `GangAllyCapTest` | GR-19, row 5 gang half |
| L2.3 | Alerts + digest + ledger: `RadioVoice.audience` (api 2.1) + `SquadRadio` delivery, **NEW** `TurfRadio`, `turf/turf_messages.yml` lines, **NEW** `turf_log` table + `TurfDigestListener`, payout ledger line + `Max_Income_Multiplier` | `gangland-api/.../npc/radio/{RadioVoice,SquadRadio}`, `GanglandApi.VERSION`, `documentation/gangland-api.md`, turf `module.yml` `Host_Api: 2.1`, `GT/radio/**` (NEW), `GT/log/**` (NEW), `GT/task/TurfIncomeDistributor` | Sonnet | yes (different turf files from L2.1; TurfIncomeDistributor XP call coordinated with L2.2) | L0.1, H13 merged | Extend `SquadRadioTest` (audience delivery at any distance, player gap kept, `null` = old rule); `TurfDigestListenerTest`; `TurfIncomeLedgerTest` | TF-37 fixed; api 2.1 documented |
| L2.4 | Heat exemption: `WantedKillTracker.exemptsKill` default + `WantedKillTrackers` forward, `EntityDamageListener` consults it, `KillComboWantedTracker` implements via `RaidService.defendingSideAt` | `gangland-core/.../wanted/{WantedKillTracker,WantedKillTrackers}`, `gangland-impl/.../listener/player/EntityDamageListener`, `CC/seam/KillComboWantedTracker`, cops `module.yml` `Host_Api: 2.1` | Sonnet | yes | L2.1 (`defendingSideAt`), H13 merged | `WantedKillTrackersTest` (default false, forward), `KillComboWantedTrackerTest` (defender inside raided turf exempt; raider not; outside turf not; no raid not) | Row 11 fixed |
| L2.5 | Docs + YAML + `commands.json` for raid/rename/chat/top/info; `documentation/features/turf.md` rewritten around the five-sentence loop | docs, module resources | Haiku | after L2.1-L2.4 | all W2 | YAML review | Player docs match code |

W2 gate (G2): **2 Opus** adversarial reviewers in parallel (one on exploits: section 3.4 matrix as a checklist; one on
concurrency/lifecycle of `RaidService` timers and reload) + L2.1's Opus = 3 concurrent max; Fable judges the merge.
Live smoke: declare, warn, capture, defend, cancel-on-restart, digest on login, map `!` states, wanted exemption.

### 6.6 Wave W3: defenders that fight like a crew (needs H13 for L3.3)

| Lane | Scope | Modules / files | Tier | Parallel | Depends on | Test gate | Exit |
|---|---|---|---|---|---|---|---|
| L3.1 | Sandbox-accept today's faction alert (cnc 5.2); then civilians hit-path: `CivilianService.alertFaction`/`recruit` honour `CivilianNpc.getFactionSquads()` (or per-turf faction id) so a hit defender stays in its turf squad; bump the civilians jar revision with the wave | `GC/npc/CivilianService`, `GC/npc/FactionSquads`, `GC/npc/combat/CivilianCombatBehavior` | **Opus** | first | W2 merged | `CivilianServiceSquadRoutingTest` (hit keeps an overridden squad; default path unchanged) | Row 8 fixed |
| L3.2 | **NEW** `TurfGarrisonSquad`: one `NpcSquad` per turf, posts, leash (region + 16), waves (`Max_Alive` 4, `Wave_Cooldown_Seconds` 20), out-of-sight spawn at posts, reserve refund, stand-down (T13), `liveInside` for `ContestMath`, UUID index (TF-35), configurable cost (TF-17), `garrison_discount` removed, panel re-checks + strings (TF-19/23b), 18+ buff slots paged (TF-18), QM name + pending purge (TF-38/33) | `GT/npc/**`, `GT/powerups/**`, `GT/listener/powerups/**`, `turf/turf_powerups.yml`, `turf/turf_npcs.yml` | Sonnet | after L3.1 | L3.1 | `TurfGarrisonSquadTest` (wave cadence, cap, refund math, leash return), `GarrisonManagerTest` extended, `TurfPowerupGarrisonViewTest` (permission re-check per click) | TF-02b/17/18/19/20/23b/33/35/38, row 3 fixed |
| L3.3 | Gang-tagged names + radio callsign (T6), health bars verify (T7), **NEW** `TurfDefenderMoneyDropSource` (T9), stuck recycling (T12) | `GT/npc/defender/**`, `GT/npc/TurfPowerupNpc`, `GC/.../civilian_messages.yml` `Shouts.Format`, turf money source | Sonnet | parallel with L3.2 if it only touches the listed files; else after | L3.1, **H13 merged** | `TurfDefenderNameTest` (<= 16 chars, no `CIT-`), `TurfDefenderMoneyDropSourceTest` (50% of cost, owner/ally kills excluded), stuck-recycle unit test on a fake clock | Live smoke: bars under names, drops on kill |

W3 gate (G3): Opus review (1) + live smoke of a full raid with 2 waves on the test server; Fable judge.

### 6.7 Wave W4: optional expansions (owner picks per item after W3 ships)

Map zoom, clipped player borders (row 4 first), dynmap layer, BlueMap in its own jar, map item, roles T4 (needs `NpcRole`
promotion to api + H13 medic), neighbour/allied responders T8, commander-down T10, policed-district dispatch T5,
`Gang.State` open join, `gang ping`. Each is its own Sonnet lane with a Haiku docs lane; any Keystone promotion is a
separate upstream lane with a Keystone 1.15.0 bump and phase doc.

### 6.8 Concurrency plan (Opus <= 3 at all times)

| Phase | Opus | Sonnet | Haiku |
|---|---|---|---|
| W0 | L0.1 (+1 gate reviewer late) = 2 | L0.2, L0.4, L0.5 then L0.3 | L0.6 |
| W1 | 2 gate reviewers | L1.1, L1.2, L1.4 then L1.5 | L1.3 |
| W2 | L2.0 -> L2.1, then 2 reviewers = 3 | L2.2, L2.3, L2.4 | L2.5 |
| W3 | L3.1, then 1 reviewer = 2 | L3.2, L3.3 | docs |

Session end of every wave: `graphify update . --force` (classes added/moved), docket rows written, `mvn clean package`
producing `target/gangland_warfare-0.14.0.jar` + `target/modules/*.jar`.

---

## 7. Open decisions for the owner

| # | Question | Options | Recommendation |
|---|---|---|---|
| D1 | How are owned turfs taken? | (A) declared raid anytime, 2 min warning, 15 min window, fee; (B) same but only in server-configured raid hours; (C) only while an owner member is online; (D) keep today's offline-only rule | **A**. B is a good server knob later (a `Raid_Hours` list), C rewards logging off, D is the inverted loop. |
| D2 | What protects an offline gang? | (A) hired defenders hold the bar + 2 h loss shield; (B) full offline immunity; (C) raids take twice as long when owners are offline | **A**: paying for defence is the decision that makes the bank matter; B recreates the log-off exploit; C is a hidden rule. |
| D3 | Map command name | (A) `/glw map`; (B) `/glw turf map`; (C) both | **A** (one short name, turf-owned root). |
| D4 | Land model | (A) keep admin cuboids, rasterize; (B) A + `Snap_To_Chunks` on for new turfs; (C) player-claimable chunks | **B**. C is a separate product (claims, overclaim, per-chunk storage). |
| D5 | Anti-snowball | (A) turf cap by gang level; (B) upkeep per turf; (C) diminishing income per extra turf | **A**: one visible number on the map header. |
| D6 | Unclaimed capture | (A) one bar, same math as raids; (B) keep two-phase CLAIM/CONSOLIDATE | **A**. |
| D7 | Allies in raids | (A) allies count for both sides, snapshotted at declaration, `Max_Allies` 2; (B) allies defend only (today, one-sided); (C) allies never count | **A** (symmetric, closes ally swapping). |
| D8 | Heat in gang fights | (A) defenders exempt inside their raided turf, raiders normal; (B) everyone gets heat (today); (C) no raid kill raises wanted | **A**. |
| D9 | Dead garrison buffs | (A) wire `reinforced_defense` as slower raids, cut `garrison_discount`; (B) wire both; (C) cut both | **A**. |
| D10 | Rank preset | (A) 3-rank preset on fresh installs only; (B) also insert `officer` into existing trees; (C) per-gang ranks now | **A**; B can surprise admins who built their own tree; C is a schema change. |
| D11 | Revision | (A) 0.14.0 minor after H13's 0.13.0; (B) patch-bumped 0.13.1 per the memory's big-wave convention | **A**, matching the 0.11/0.12/0.13 phase cadence; B if 0.13.0 must stay shippable without it. |
| D12 | Offline digest home | (A) turf-owned `turf_log` table; (B) mail module `Depends: [turf]` and a `TURF_REPORT` `MailType`; (C) none | **A**: mail must keep loading without turf, and B adds an edge for one message type. |
| D13 | Garrison money | (A) survivors/unspent refund to reserve + bounty = 50% of hire cost paid from the owner's spend; (B) no refund, minted bounty; (C) refund, no bounty | **A** (neutral, unfarmable). |
| D14 | Economy defaults | (A) section 3.3 table; (B) keep today's numbers (100 income, 5k-15k boosts); (C) owner supplies numbers | **A** as shipped defaults, all in module YAML. |

---

## 8. Risks, anti-exploit residue, out of scope

### 8.1 Risks

| Risk | Mitigation |
|---|---|
| `CaptureService` rewrite regresses a working loop | L2.0 writes the spec as failing tests first; the 26 existing turf tests stay green except the pinned ones flipped on purpose; `ContestMath` is pure and exhaustively unit-tested. |
| Raid warning + radio spam in see-saw fights | Milestones fire on upward crossings only (existing); `SquadRadio` squad/key/player gaps; one active raid per gang; shields. |
| Chat map flood | Auto map default off, cell-change + 2 s + hash gate; compass on the action bar is the always-on surface. |
| Server load from waves | Hard `Max_Alive` per turf, reserve-limited, out-of-sight check limited to a few candidates; defenders reuse the existing 5-tick deployer loop. |
| H13 slips | W0/W1 start from master and rebase; W2 L2.3/L2.4 and W3 wait. |
| Faction-alert behaviour unverified live (H12 gap) | L3.1 begins with a sandbox acceptance; W3 does not start its code until it passes. |
| Timezone gangs lose land overnight | Garrison + 2 h loss shield + cap bound the damage to one turf per 2 h per gang; D1(B) raid hours is the escape hatch. |
| Economy imbalance | All numbers in module YAML; defaults are conservative; bounty is a transfer, not a faucet. |
| Colour-blind players / Bedrock clients | Glyph carries identity, the legend repeats every colour fact, hover is never the only carrier. |
| Data migration | Additive only: `turf.shield_until` column, `turf_log` table; the backend diff engine adds them; no existing column changes type (TF-14/15 fixes are read-side). |

### 8.2 Out of scope (explicit)

- Player-claimable chunks, overclaim, per-chunk protection (D4 C).
- Block protection / build rights inside turf (turfs remain PvP zones, not claims).
- Per-gang custom ranks and rank inheritance (`ranks.md` documents inheritance that does not exist; fix the doc, not the model).
- Gang wars as a persistent declared relation between gangs (raids are per turf; a "war" state adds a second rule for the same fight).
- Gang bounty posting, gang home/HQ teleport, gang vault items, leaderboards beyond `gang top`.
- Roles, medics, commander morale, responders, policed-district dispatch (W4 optional).
- squaremap / Pl3xMap (Paper-only), any `io.papermc.*` API.
- Spanish turf strings (TF-22, with CM-15 i18n).
- Keystone changes (promotion candidates only, section 6.1).
- Weapons on defenders (Bartizan owns weapons; `Plugins: [Bartizan]` stays where it is).
