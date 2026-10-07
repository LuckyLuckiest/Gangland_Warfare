# Adversarial feasibility + gameplay review: Gangs+ parity additions (W5, W6, section 15, PARITY.md)

Reviewer: opus 5.5, 2026-09-30. Planning only; no product code touched. Scope: the parity additions only (PLAN.md P7/P8,
7.1 parity rows, 7.10, 7.11, D16-D24, risks rows marked parity, 13.1, 14.1, section 15; `parity/PARITY.md`;
`parity/fights.md` as adopted; the W5/W6 lanes in `roadmap.json`).

Graph freshness: `graphify-out/graph.json` 2026-09-29 23:08 > HEAD `ff9d813f` 23:05, fresh. Oriented with `graphify explain
WaypointTeleport | GangMembershipView | PlaceholderContribution | LocalizedModuleYaml | CommandContributions | Level` and
`graphify query TeleportEvent`; `GangSafehouseSetEvent`, `DamageLedger`, `CombatTag`, `GangWorldPolicy`, `GangLimits`,
`GangStatContribution`, `FriendlyFireRegionSource` return nothing (correct: all NEW). Raw reads only after that. Keystone
checked by Grep/Read on `E:/Programming/java/Keystone` master. Spigot API checked with `javap` against
`~/.m2/.../spigot-api-1.16.5-R0.1-SNAPSHOT-shaded.jar`; XSeries with `javap` against `XSeries-13.6.0.jar` (the pinned
`xseries.version`). Competitor wording checked through the owner's connected Chrome on the competitor's public docs
(commands page and placeholders page), comparing only our own tokens against theirs; nothing from those pages is quoted here.

Path shorthand as in PLAN.md: `GG/` gang module, `GM/` mail, `GT/` turf, `GC/` civilians, `API/` gangland-api,
`IMPL/` gangland-impl.

## Verdict: FAIL (flips to PASS_WITH_FIXES once B1-B10 are applied; none needs a new owner decision except striking D22 B)

The architecture of the parity addendum is sound: module edges are legal (turf already has `Depends: [civilians, gang]` and
`gangland-gang` at `provided`, so turf may listen to a gang-module `GangSafehouseSetEvent` and implement a gang-module
`GangStatContribution`; fights with `Depends: [gang]` copies mail's pom shape exactly), the api batch is genuinely additive,
nothing needs Paper, and the W5/W6 file ownership is disjoint from W3/W4. But the plan as written does not hold on ten
points: three mechanisms contradict the code they name (level claim, kill ledger, WorldGuard bean), one validation is
impossible under the module graph, one rank clause is off by one, the new teleport veto leaks state in an api class, the
copyright rule is broken in three tokens, and four griefer loops (rally reinforcement, bet drain, KDR farming, reward hopping)
have no stated control and no red test while the W5-G exit says "no open exploit path without a red test". Every fix below
fits inside an existing lane.

---

## 1. Blocking

### B1. W5-L: the fee is charged and the XP is lost on a cancelled level-up

Plan (7.10 W5-L, 15.3 C5): "`addLevels(1, event)` and one fee withdrawal after it returns 1"; test "a cancelled
`GangLevelUpEvent` withdraws no fee".
Code: core `gangland-core/.../core/user/Level.java` L74-90. Inside the loop, L79 subtracts `requiredExp` **before** L81 fires the
event; L83 increments `levelValue` only if not cancelled; L86 increments `counter` unconditionally. So `addLevels` returns 1 on a
cancelled level-up (the fee is withdrawn) and the XP bar is already spent (the gang loses the XP and gains no level).
Also at `maxLevel` `nextLevel()` returns `maxLevel` (L62-64) and `addLevels` returns 0 silently.
Fix (W5-L body, no core change): read `getLevelValue()` before and after `addLevels(1, event)`; withdraw the fee only when it
rose by one; if the event was cancelled, restore the XP with `addExperience(required, false, null)`; refuse `upgrade` at
`Gang_Level_Max` with its own sentence. Add both to `GangUpgradeCommandTest` (cancelled event: level, XP and bank unchanged;
max level refused). Adjacent triage row (below): `Level.addLevels` burns XP on a cancelled event for every caller.

### B2. W5-F/W5-S: the kill ledger never sees the killing blow when the downed system is on

Plan (15.5): `DamageLedger` at **MONITOR, `ignoreCancelled = true`**; W5-S `GangStatsListener` credits the killer "from
`DamageLedger`" on `PlayerDeathEvent` + `PlayerDownedEvent`.
Code: `IMPL/listener/player/CustomPlayerDeathListener.java` L91-113 cancels every lethal player hit at HIGHEST
(`event.setCancelled(true)`, health set to the downed value) when `Settings.isRespawnEnabled()`; `PlayerDownedEvent` is then
fired from `enterDownedState` (L211) with no killer field. A MONITOR `ignoreCancelled` listener therefore never records the
lethal hit: a one-shot kill credits nobody, and any earlier hit credits the wrong player (the assister becomes the killer).
The plan explicitly designs for `PlayerDownedEvent`, i.e. exactly the configuration where it is broken 100 % of the time.
(`settings.yml` L228-229 ships `Respawn.Enable: false`, so default servers take the vanilla `PlayerDeathEvent` path, which works.)
The fights intercept (NORMAL) cancels lethal hits too, which is fine: fights own their stats.
Fix: record at **HIGH, `ignoreCancelled = true`** (after the LOWEST friendly-fire cancel, before the HIGHEST downed intercept;
impl `EntityDamageListener` also sits at HIGH, order between the two does not matter because neither cancels there), or keep
MONITOR for tags/assists and add a HIGH `LethalHitRecorder` (NEW) that stamps the last damager for the death/downed read.
Add a `GangStatsListenerTest` case "respawn system on: one-shot kill credits the shooter". Also require
`getFinalDamage() > 0` for a ledger entry so a 0-damage snowball does not tag or assist (see G5).

### B3. W6-W: a WorldGuard-typed bean either drops the whole gang config or spams `reflection.type.missing`

Plan (7.11 W6-W, risks): `GG/gang/combat/region/WorldGuardFriendlyFireSource` is "WorldGuard-typed; Keystone `ReflectionGuard`
skips it without WorldGuard"; exit "no `reflection.type.missing` spam without WorldGuard".
Code: Keystone `keystone-bean/.../BeanFactory.java` L259 wraps `configClass::getDeclaredMethods` in `ReflectionGuard.orSkip`
(`keystone-common/.../diagnostics/ReflectionGuard.java`): if any `@Bean` method signature of a `@Configuration` class names a
missing type, the **whole class** is skipped (every bean in it) and a `reflection.type.missing` fault plus a WARN is reported on
every boot. `ReflectionGuard` is a safety net for scans, not a wiring mechanism. A `@Bean` in `GangConfig` that returns or takes
`WorldGuardFriendlyFireSource` would drop every gang bean on a WorldGuard-less server; a body-only `new` without a guard throws
`NoClassDefFoundError` at invocation (the KS-MO-07 abort class).
Fix: mirror the impl registrar. The `@Bean` returns the type-free `FriendlyFireRegionSource`, checks
`Bukkit.getPluginManager().getPlugin("WorldGuard") != null`, and only then calls a static factory in a separate class
(`GG/gang/combat/region/WorldGuardRegions.create()`, NEW) that is the only code naming WorldGuard; otherwise returns the no-op
source. Test: `GangConfig` loads with WorldGuard absent and zero faults. Also note the query needs WorldEdit's `BukkitAdapter`
(`worldedit-bukkit`, transitive of `worldguard-bukkit` 7.0.x) at `provided`; name it in the pom line.

### B4. W6-A: "arena overlapping a turf" cannot be validated under the module graph

Plan: 7.11 W6-A "save validation (spawn outside the box, missing Exit, arena overlapping a turf)"; P8.1 "arena inside a turf";
15.3 C7 "arenas are placed outside turfs (DF8 A, validated at `arena finish`)".
Code/graph: fights has `Depends: [gang]` only; `gangland-api` has no turf type (grep of `API/`: only `GangMembership`,
`Messages`, `Settings` mention turf). `parity/fights.md` L272 and DF8 A say the check is an admin rule in v1 precisely because
fights cannot see turfs. Adding `Depends: [turf]` would make fights unloadable without turf and civilians.
Fix: delete the check from W6-A/P8.1/C7 and state DF8 A (documented admin rule, printed by `arena finish`), or add the
additive api seam it needs (a `TurfLookup` holder like `GangMembership`, installed by turf) to the W5-K batch and name it. The
first is the ponytail answer.

### B5. W5-R: `Tree.isDescendant(rank, rank)` is false, so "the named rank" itself fails the minimum

Plan (15.4): pass "when the member's rank is the named rank or sits below it toward the Tail (`Tree.isDescendant`)".
Code: Keystone `keystone-common/.../datastructure/Tree.java` L74-77 returns `false` when `ancestor == descendant`.
Fix: the clause is `rank.getNode() == min.getNode() || tree.isDescendant(min.getNode(), rank.getNode())`; add "rank exactly at
the minimum passes" to the `GangPermissionsTest` table (the plan lists "at, below and above", so the test would catch it, but
the lane text should not steer the executor into the bug).

### B6. W5-H/W5-T/W5-F: every new veto exercises a state leak in `WaypointTeleport`

Plan: `TurfTeleportGuardListener` and `CombatTeleportGuard` cancel the api `TeleportEvent`.
Code: `API/data/teleportation/WaypointTeleport.java` L126-134: on a cancelled `TeleportEvent` the method returns before L158
`countdownTimer.remove(player)`; `totalDistance` is never cleared on any path (only in `onPlayerMove` L104-106). After a veto
the player stays in the static `countdownTimer` map, so the next 1.5 blocks of walking prints "teleport cancelled" and cancels a
finished timer, and the distance accumulator carries into the next warm-up. Today almost nothing cancels `TeleportEvent`
(grep: only gadget `GrappleAbortListener` listens); W5 makes vetoes routine.
Fix: W5-K (owner of the api batch) clears both maps on the cancelled and world-missing paths; the veto sentence goes through
the listener. Test in `API/` beside the existing api tests. Body change in an api class, no signature change, additive.

### B7. Copyright: three tokens copy the competitor

Checked against the competitor's public commands and placeholders pages (tokens compared, nothing copied here):
- D22 option B (`/g`, `/gc`, `/ac`) is the competitor's own label set. Keeping it as an owner option is a standing proposal to
  copy command wording. Strike D22 B from PLAN.md section 10, `roadmap.json` (decision D22 options, W5-U scope, risk row) and
  15.1; `/gw`, `/gwc`, `/gwa` become the only labels, remappable per server through `commands.yml` (C18 already says so).
- `gang friendlyfire on|off` and node `gangland.gang.friendlyfire`: the competitor uses exactly this subcommand word and this
  node leaf. 15.1 keeps it as a "generic industry term"; the rule forbids command wording and node spellings, and "default to
  does not hold". Rename to our own, e.g. `/glw gang pvp on|off`, `/glw gang ally pvp <gang> on|off`, nodes
  `gangland.gang.pvp`, `gangland.gang.ally.pvp`.
  "Friendly fire" can stay as prose in docs. Rename scope is bounded to the observed collisions (the two command words, the
  two nodes, the region flag): the `Friendly_Fire.*` YAML section (our own key style), the `FriendlyFireRule` class and the
  `gang_friendly-fire` placeholder (dash style, not the competitor's form) stay, so executors do not churn unrelated files.
- WorldGuard flag `gangland-friendly-fire` is the competitor's flag name with the prefix swapped. Rename, e.g. `glw-gang-pvp`.
- Low risk, recorded only: placeholder `user_rank-number` mirrors a competitor token's words; prefer `user_rank-position`.
  The `gang_board_<stat>_<n>_<field>` shape mirrors their leaderboard token shape; our words differ, acceptable.
Our own new verbs (`safehouse`, `rally`, `upgrade`, `notice`, `profile`, `ranking`, `recruiting`, `abandon`, `propose`,
`enlist`, `drop`, arena `finish`) do not occur on their commands page. `spy` occurs there only inside a compound word; ours is
`option gang spy`, acceptable. `accept`/`decline`/`info`/`list`/`kick`/`leave` are generic and already Gangland's.
W6-X must reread against the live competitor page, not only `competitors/gangsplus.md`.

### B8. G1 (below): rally and safehouses still give instant reinforcement to any turf fight

`TurfTeleportGuardListener` vetoes only a destination **inside** a turf under attack. `rally` with no argument teleports every
consenting online member to the caller's snapshot; a caller standing one block outside an attacked turf delivers the whole
gang, which then walks in. A4 freezes membership, not position: these are pre-contest members and count as defenders (at once)
or attackers (after 15 s). A safehouse set one block outside a rival turf is a permanent staging pad for the D1 wake-up window.
The plan's own risk row claims "no escape from or reinforcement into a turf under attack" (15.8 item 10).
Fix (W5-T, reads turf state only, no W2-R file): veto a GANG-type teleport whose destination is within
`Teleport_Guard_Buffer_Blocks` (turf module YAML, e.g. 48) of a turf under attack, **and** refuse `rally` sends and GANG-type
teleports for any member of a gang that is currently owner or challenger in any contest. The second rule alone closes both
directions with one sentence ("your gang is in a turf fight"). Add the one-block-outside case to `TurfTeleportGuardListenerTest`.

### B9. G2: `Fight` minimum = de facto `Withdraw`; the bet cap is checked on one bank only

Plan: `Ranks.Minimum` ships `Fight` = officer and `Withdraw` = officer, but an owner who raises `Withdraw` to owner still leaves
`Fight` at officer. fights.md 6: `bet <= Max_Bet_Percent_Of_Bank` "of the challenger's bank"; accept re-checks only that both
banks cover the bet. `Max_Net_Transfer_Per_Pair_Per_Day` (fights.md 7) is **not** in W6-E's key list.
Exploit: a rogue officer proposes (or accepts, from the other side) a 1v1 against an alt gang, enlists alone, stands still; the
alt gang takes 2x the bet. Every 30 min (`Rematch_Cooldown_Minutes`) per alt gang, unbounded across alt gangs; sparring fights
have no daily cap. The accepting side can stake up to 100 % of its bank when the challenger is twice as rich.
Fix (W6-S/W6-E): a bet above 0 requires the member to also pass the `Withdraw` minimum (`GangPermissions.allows(...,
WITHDRAW)`), on both propose and accept; `Max_Bet_Percent_Of_Bank` checked against **both** banks at accept; restore
`Max_Net_Transfer_Per_Pair_Per_Day` and add `Max_Bet_Transfer_Per_Gang_Per_Day` (across all opponents) to W6-E; tests in
`FightRulesTest`.

### B10. G3/G4: KDR/WLR farming and level-up reward hopping have no control

- KDR: W5-S excludes only same-gang and allied victims, a 300 s repeat-pair cooldown and disabled worlds. A **gangless** alt
  is a valid victim: 12 kills per alt per hour, N alts in parallel; `gang ranking kdr` and `gang_board_kdr_*` are farmable at
  zero cost. Fix: count a kill only when the victim is in a non-allied gang with `gangJoinDateLong` >= `Stats.Min_Victim_Member_Age_Hours`
  (24), plus a per-killer-per-victim daily cap (`Stats.Max_Kills_Per_Victim_Per_Day` 3). Tests in `GangStatsListenerTest`.
- WLR: ranked needs a bet, equal teams and 24 h members, but alt gangs (create cost 100,000 once) give 3 ranked wins per pair
  per day each, zero-sum money shuffled back through `gang withdraw`. Fix: `gang ranking wlr` counts only wins against distinct
  opponents in the ranking window (or ranks by wins against distinct gangs), and `Ranked_Min_Opponent_Gang_Age_Days` (7).
- Level rewards: `Levels.Level_<n>.Commands` run for every current member, online and offline. With `gang join` on OPEN gangs
  (D7) a player (or ten alts) joins a gang about to claim, collects the reward, leaves, repeats elsewhere; an owner can also fill
  the member cap with alts before `upgrade confirm`. Fix (W5-L): reward only members whose `gangJoinDateLong` is older than
  `Levels.Reward_Min_Member_Age_Hours` (24); test with a fresh joiner.

---

## 2. Fixes (real, small, name the owner lane)

| # | Plan says | Code says | Fix |
|---|---|---|---|
| F1 | W5-U owns `GM/command/ally/GangAllyAbandonCommand`; 7.1 lists it among mail files | the class is `GG/command/sub/gang/ally/GangAllyAbandonCommand.java` (gang module) | correct the path in PLAN.md 7.1 and W5-U (`roadmap.json` names only the test class, so it needs no path edit); W5-U keeps ownership |
| F2 | 15.7 "a `GangMembershipView` **lambda** compiled without the new methods still loads" | `API/data/gang/GangMembershipView.java` has three abstract methods (L17, L23, L28), not a functional interface | the binary-compat test uses an anonymous class or a precompiled stub class; defaults are binary compatible, so the claim itself holds |
| F3 | GP-33 / C3: `CombatTag` covers "every GANG-type waypoint teleport" | cops has no `TeleportEvent` or `PlayerTeleportEvent` listener (grep); `DetainmentListener.onCommand` L270-278 blocks commands only; `WaypointTeleport` cancels a warm-up only on `PlayerMoveEvent` (L78-109), and `PlayerTeleportEvent` has its own `HandlerList` in 1.16.5 (javap) so an arrest teleport does not cancel it | a player who starts a safehouse warm-up and is cuffed or jailed mid-countdown is teleported out when it ends. Add one NEW cops listener (`copsncrooks/listener/police/RestrainedTeleportListener`) on api `TeleportEvent` cancelling for `detainmentService.isRestrained(player)`, owned by W5-F (which already owns `CombatTag`/`CombatTeleportGuard`) as a named single extra file; cops otherwise untouched. Pre-existing for admin GANG waypoints, amplified by safehouses and rally |
| F4 | 15.5 "one rule across fists, bows, potions and Bartizan guns" | `GG/listener/gang/GangMembersDamageListener.java` L30-35 resolves only `Player` and `Projectile` shooters | tamed wolves, `TNTPrimed.getSource()`, and fire from Flame/Fire Aspect (`EntityCombustByEntityEvent` is separate from the cancelled damage, then FIRE_TICK damage has no damager) all bypass. Add `EntityCombustByEntityEvent` and `Tameable` owner / `TNTPrimed` source resolution to W5-F's `FriendlyFireRule` inputs, or state them out of the rule in 15.5. Rows in `FriendlyFireRuleTest` |
| F5 | W5-C `{glw_gang}` replaced in `AsyncPlayerChatEvent.getFormat()`; D21 display names allow any Unicode | `setFormat` is a `String.format` pattern; a `%` in a display name throws `IllegalFormatException` on every chat line | escape `%` as `%%` in every spliced value (display name, prefix); add a `%` case to the format-token test |
| F6 | W5-C "api `GangChatEvent` on the main thread" | Bukkit throws `IllegalStateException` for a synchronous event called from the async chat thread | state the hop: cancel the async event, schedule delivery on the main thread, fire `GangChatEvent` there (one tick latency), or declare the event async (`super(true)`) and keep it off manager reads |
| F7 | 15.5 negative set as a hand list | `XPotion.DEBUFFS` exists in XSeries 13.6.0 (javap) | use `XPotion.DEBUFFS`; note `PotionSplashEvent.setIntensity(victim, 0)` strips every effect of a mixed potion for that victim |
| F8 | W5-M/W5-L `Commands.*` splice `%gang%` = `name` "under `Name_Pattern`", which admins may widen to any script | a widened pattern can admit `@` and vanilla selectors (`@a`) reach every console command | splice `%gang%` only when the name matches `[A-Za-z0-9_]+`, else splice `%gang_id%`; `Name_Pattern` load check refuses `@`, `/`, `;` as well as whitespace |
| F9 | D21 free display names | nothing stops `&cRats` vs `Rats`, Cyrillic homoglyphs, or "[Staff]" in public tags and rankings | uniqueness on the colour-stripped, NFKC-normalised, lower-cased display name; optional `Display_Name.Blocked_Words` list |
| F10 | GP-26/27/42 safehouse and rally | no check for `Worlds.Disabled`; no check for arena boxes | `safehouse set` and GANG teleports refused in a gang-disabled world (`GangWorldPolicy`, W5-H); fights listens to `GangSafehouseSetEvent` and refuses inside any `ArenaBox` (W6-F, legal: fights depends on gang) |
| F11 | fights 5.4 containment covers fighters only; `Protect_Blocks` covers fighters only | outsiders can walk or rally into a busy arena, splash-heal their side, place lava or cobwebs (environmental damage lands) | during LOBBY..RESULT, non-participants are refused entry to the box (move/teleport) and ejected to the Exit by the 5-tick check; block place/break in a busy arena refused for everyone. Tests in `FightSessionListenerTest` |
| F12 | W6-S: `CombatTag` blocks `fight enlist` | the tag is checked at enlist only | enlist while untagged, get tagged in the 60 s lobby, the COUNTDOWN teleport carries you out of a turf fight. Re-check the tag (and "inside a turf under attack" via B8's rule) at COUNTDOWN; drop the participant, cancel with refund if a side falls short |
| F13 | W5-A/C: `option gang spy`, `bank`, etc. "console-safe" | fine; but `GangOptionContribution` L68/L80/L115/L183 casts stay in the existing `rank` verbs | W5-A should fix the four casts while it owns the file (one docket row), or say explicitly they stay |
| F14 | GP-15/C15 public tag | EssentialsChat and similar set the format at LOWEST/NORMAL; LOW may run before a later format rewrite | document the priority; a test cannot prove third-party order, so the manual W5 walkthrough should include one chat-format plugin |
| F15 | `inventory/player_profile.yml`, `gang_ranking.yml` in impl resources for a module feature | pre-existing pattern (`gang_stat.yml`, `gang_info.yml` already live in `gangland-impl/src/main/resources/inventory/`) | not blocking; record that the menus render a dead item source when the gang module is absent |

## 3. Suggestions

- S1. `GangShortLabelExecutor` lives in impl and `/gw` is declared in core `plugin.yml`, so a server without the gang module
  owns a dead label that squats `/gw`. Register the labels only when `GangMembership.isInstalled()` at COMMAND phase, or accept
  and document. Note `gangland.command.main` is `default: op` today; the labels need their own `permission:` lines.
- S2. Combat tag lock: a rival alt can keep a player tagged forever with cheap hits and deny every safehouse and `gang leave`.
  The B2 `> 0` damage floor limits it; consider tagging only when both players are gang members.
- S3. `GangPurgeTask` should skip a gang with any member online in the last `Inactive_Days` including the owner, and never run
  on the first boot after an upgrade (a server restored from a backup looks inactive).
- S4. Level-up rewards for offline members: `OfflinePlayer.getName()` can be null for never-cached UUIDs; skip with a log line
  rather than splicing "null".
- S5. W6-W: flag registration in `onLoad` works only because `softdepend: WorldGuard` loads WorldGuard first and its registry
  locks at its enable; catch `FlagConflictException` (another plugin owns the name) and log once.

---

## 4. Griefer playthrough (with alts)

| # | Play | Today's plan | Holds? |
|---|---|---|---|
| G1 | Owners come online, rally ten members to a caller 1 block outside the rival turf, walk in during the wake-up shield | veto only for destination inside | **No** -> B8 |
| G1b | Defender under attack walks 1 block out, safehouse home | allowed; walking away is always allowed | yes |
| G1c | Start a safehouse warm-up, get cuffed, teleport out of detainment/jail | no cops veto | **No** -> F3 |
| G2 | Rogue officer throws bet fights to an alt gang | `Fight` minimum only; one-bank cap; net-transfer cap dropped | **No** -> B9 |
| G2b | Launder between allies: unally, fight, re-ally | allies refused; pair caps | yes, bounded by B9 caps |
| G3 | KDR farm on gangless alts | only same-gang/allied excluded | **No** -> B10 |
| G3b | WLR farm with alt gangs | 3 ranked per pair per day, 24 h members | **No**, N alt gangs -> B10 |
| G4 | Level-reward hopping through OPEN gangs | rewards for all current members | **No** -> B10 |
| G4b | Buy levels with money | XP bar checked in gang code | yes, once B1's return-value bug is fixed |
| G4c | Farm gang XP with an alt gang on unclaimed turf | per-gang, not transferable; D2 frozen | yes (out of parity scope) |
| G5 | Tag-lock a rival with snowballs to block their safehouse | any ledger entry tags | partial -> B2 floor, S2 |
| G6 | FF on: gangmate kills you to claim the bounty on your head | `EntityDamageListener` bounty claim (L123-126) has no gang check; before 0.14.0 same-gang damage was always cancelled | new path, low value (a gangless alt could always do it) -> triage row |
| G7 | Enlist before a turf fight turns on you, get teleported out at countdown | tag at enlist only | **No** -> F12 |
| G8 | Rally or safehouse into a busy arena, heal your side, drop lava | containment for fighters only | **No** -> F10/F11 |
| G9 | Disband mid-fight to keep the bet | `FightGangDeleteListener` forfeits and pays the survivor | yes |
| G10 | Recruit or ally mid-contest to swing the bar | A4 freezes sides by join/ally stamps | yes |
| G11 | Allied FF farm guards or the D14 refund | cops `TurfFriendlyFireListener` unconditional | yes |
| G12 | Staff `bank give` faucet | logged with actor | yes (audit, not prevention; acceptable) |

## 5. Verified as stated (no change)

| Claim | Evidence |
|---|---|
| `TeleportEvent` fires at the jump, after the warm-up, with source location and `Waypoint` (type readable) | `API/data/teleportation/WaypointTeleport.java` L126-134; `Waypoint.WaypointType.GANG` (`Waypoint.java` L82-85) |
| `shield` -> `setInvulnerable(true)` | same file L148-155 |
| `GangMembershipView` abstract-only, holder with absent defaults | `API/data/gang/GangMembershipView.java` L12-29, `GangMembership.java` L17-72 |
| `PlaceholderContribution.resolve` non-null player; core `user_`/`bank_` substring dispatch first; null-player branch returns `NA` for owned prefixes | `API/.../PlaceholderContribution.java` L13-22; `IMPL/.../GanglandPlaceholder.java` L106-163 (`getBank` returns null for an unmatched `bank_` token, so a board token containing `bank_` falls through; the "no `bank_`" constraint is a precaution, `gang ranking bank` may keep its name) |
| `LocalizedModuleYaml` in api, used by civilians/cops | `API/file/configuration/LocalizedModuleYaml.java` L30 |
| `CommandContributions.from(container)` pattern | `API/command/extension/CommandContributions.java` L23 |
| `Level.addExperience(xp, false, null)` banks XP without levelling; `addLevels` levels below the bar | `Level.java` L38-42, L74-90 |
| `GangPermissions.allows` = node OR member node OR top rank | `GG/gang/permission/GangPermissions.java` (method at ~L67-77) |
| `GangAlliance.since` persisted and reloaded | `GG/gang/GangAlliance.java` record; `GangAllianceRepository.java` L115; `Gang.addAlly` stamps `Instant.now()` only for new alliances |
| `Member.gangJoinDateLong` | `GG/gang/member/Member.java` L28 |
| `GangOptionContribution` `(Player) sender` casts | L68, L80, L115, L183 |
| `GangAllyWeaponImpactListener` uses `Settings.isGangEnabled()` + `alliedOrSame` | `GC/listener/gang/GangAllyWeaponImpactListener.java` L42-47 |
| `ModuleInstalls.OFFICIAL` six ids, `Map.of` (9 pairs after W6-K, under the 10-pair limit) | `IMPL/command/sub/module/ModuleInstalls.java` L30-35 (fights.md cites L24-31: stale) |
| `Gangland.onLoad` exists at L52 | `IMPL/Gangland.java` |
| Turf may import gang-module types | `gangland-turf/pom.xml` `gangland-gang` provided; `module.yml` `Depends: [civilians, gang]` |
| Fights pom shape = mail | `gangland-mail/pom.xml` `gangland-gang` + `gangland-api` provided; `module.yml` `Depends: [gang]` |
| `Host_Api` today | `GanglandApi.VERSION = "2.0"`; every `module.yml` `Host_Api: 2.0` (CLAUDE.md's `1.0` text is stale) |
| Offline `UserManager` for members' money | `@Bean(name = "offline")` in `IMPL/config/DataConfig.java` L77 |
| Spigot 1.16.5: `AreaEffectCloud.getSource`, `PotionSplashEvent.setIntensity`, `PlayerTeleportEvent` own `HandlerList` | javap on the 1.16.5 shaded jar |
| `DetainmentListener.onCommand` precedent | `copsncrooks/listener/police/DetainmentListener.java` L270-278 |
| api additivity | new default methods, new event classes, one `default resolveGlobal`, a body-only change in `LocalizedModuleYaml` and (B6) `WaypointTeleport`: no removal, rename or signature change |

## 6. Adjacent findings for triage (orchestrator writes docket rows; not written here)

1. `Level.addLevels` (`gangland-core/.../core/user/Level.java` L79-86) spends the XP before the event and counts a cancelled
   level as done; affects user and gang levels (system: core levels).
2. `WaypointTeleport.teleport` cancelled/world-missing paths leave `countdownTimer` and `totalDistance` entries
   (`API/.../WaypointTeleport.java` L114-134, L158).
3. A GANG waypoint warm-up survives an arrest or jail teleport (no cops `TeleportEvent` veto) - F3.
4. Downed players (`CustomPlayerDeathListener`) have no command block; they can start waypoint teleports while downed.
5. Bounty claim in `IMPL/listener/player/EntityDamageListener.java` L123-126 has no gang relation check (becomes reachable
   between gangmates once friendly fire can be switched on) - G6.
6. Fire from Flame/Fire Aspect between gangmates is not cancelled today (`EntityCombustByEntityEvent`) - F4.
7. `ModuleInstalls` line reference in `parity/fights.md` is stale (L24-31 vs L30-35).
