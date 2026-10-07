# Cops N Crooks Overhaul — 0.16 "Where they come from" SPEC

Source: plan page `artifact/overhaul-updated.html` ("Cops N Crooks Overhaul", owner-approved, 6 releases 0.15-0.20; updated
2026-10-07 after 0.15.0 shipped and the 0.15.2 plan landed). Extracted 2026-10-07. This file is the binding spec for the 0.16 wave.
Text in the VERBATIM sections is the page text, tags stripped and lightly reflowed (4-space indent = quoted page text).

## Owner rulings (2026-10-07)
- ALL "Decisions for you" items that belong to 0.16 take their RECOMMENDED answer (the first option of each item in section 4
  below), the owner's precedent in 0.15.
- Base = **0.15.2 on master** (0.15.1 and 0.15.2 merged first). 0.16 is built ON TOP of 0.15.2: treat AutoDropPlanner, ChaseArc /
  ChaseArcs / ChaseArcListener, ChaseLearner, AutoSettings, the chase_habit and chase_level_stat tables, DropMode.AUTO and the
  COLD_TRAIL ending as existing code. Plan: brainstorming/cnc-overhaul-2026-10-05/auto-drop/PLAN.md.
- Plugin revision: **0.16.0** (root pom `<revision>`; module.yml takes `${project.version}`). Branch named after the revision.
- **gangland-api 2.2 -> 2.3** (additive only; contract rule: no removal/rename/signature change). The page's versions table says:
  "gangland-api 2.3 | This plan | Regions + places. 0.16: RegionProvider SPI, PlaceNames lookup, Waypoint.WaypointType.HOSPITAL."
  So 2.3 carries exactly three additions: the `RegionProvider` SPI, the `PlaceNames` lookup (`PlaceNames.locate(Location)`), and the
  additive `Waypoint.WaypointType.HOSPITAL` value. `GanglandApi.VERSION` = "2.3"; cops-n-crooks `module.yml` `Host_Api: 2.3`
  (it uses the new surface). Any other module that consumes RegionProvider (turf, gang) bumps its own Host_Api only if it uses it.
  No public `WantedEvasionDropEvent` in 2.3 (0.15.2 decision D4: "None now; add it in the 0.16 api 2.3 bump when the criminal record
  needs it"; the record is 0.19 and no 0.16 card needs it, so it is NOT added in 0.16).
- **Keystone 1.15.0** (new minor, Gangland pin moves 1.14.0 -> 1.15.0). Per the page: "Promotions (reserved by Gang & Turf).
  Out-of-sight spawn helper and hold-post + leash, promoted from Gangland when Cops N Crooks 0.16 becomes the second consumer
  (dispatch from stations, containment perimeter). Also carries the Gang & Turf CommandMap helper (admin-chosen short labels) if
  that deferred item is pulled in; otherwise it stays deferred and, if it arrives after 1.15.0 ships, takes a 1.15.x slot. The map
  painter stays in Gangland." Guardrail: "Keystone stays generic. Only product-free pieces move into Keystone ... the out-of-sight
  spawn helper and hold-post leash in 1.15.0 ... Wanted levels, crimes and stations stay in Gangland." Decision text: "Keep the map
  painter, block restore and perch finder in Gangland."
  Where from: the page names Gang & Turf T3 (reinforcement waves, hidden-post spawn via World.rayTraceBlocks with a fallback) as the
  origin of the spawn helper, and Gang & Turf T2 (posts/leash in the turf-side TurfGarrison holder) as the origin of hold-post + leash.
  **Flag: Gang & Turf (0.14.0) has NOT started on master, so neither exists in the repo.** Ruling for 0.16: write both product-free in
  Keystone `keystone-npc` for 1.15.0, extracting from the code that exists today (cops-n-crooks `CopSpawnManager.spawnNearPlayer` ring
  spawn over Keystone `EntitySpawner.findClosestSpawnerLocation`; `PursuingBehavior` distance leash; `ReturningBehavior`), with cops as
  first consumer; Gang & Turf adopts them when it rebases. CommandMap helper stays deferred (1.15.x). Keystone fixes go UPSTREAM
  (CLAUDE.md): own phase doc + version bump, `mvn clean install` in the Keystone repo before bumping `<keystone.version>`.
- No Bartizan change (Gangland keeps its 0.6.0 pin). Citizens stays a soft dependency (NpcSupport).
- **Heat-weight must-fix (0.15.2 PLAN.md risk 11) is IN 0.16 SCOPE**: the AUTO rampage opening (`Rampage_Crimes` within
  `Opening_Seconds`) must count only heavy crimes or heat, not raw crime count, before 0.16 reports any cheap crime. The page's 0.17
  note says the two plans disagree on which release first publishes a cheap crime (PLAN.md: 0.16; page: 0.17, Brandish_Near_Cop).
  Ruling: land the fix in 0.16.0 regardless. See section 6.
- Behind a toggle: every feature gets its own `Enable` key in the module's OWN YAML (copsncrooks/ folder), never `Messages`/`Settings`
  (module API rule); new strings in the module's own message YAML.

## 1. Foundations (ship in 0.16) - VERBATIM


    Districts, regions and place namesgangland-api + gangland-core0.16
    gangland-api 2.3 RegionProvider SPI (id, name, 3D cuboid or sphere, owner, tags such as restricted/hideout) plus PlaceNames.locate(Location). gangland-core keeps admin regions; gangland-turf contributes owned turfs and their names, so cops never depend on turf. A district is public geography; a turf stays a gang claim.
    UnblocksStation registry · New crimes

    Police station registrycops-n-crooks0.16
    A station has an id, a name, a district, its spawner ids and an optional jail link. Existing spawners are grouped under stations through an additive column. Dispatch, patrol beats, clock-in, payoff and transport destinations all read the same registry.
    UnblocksAdmin setup wand · Dispatch from stations · Patrols & pull-overs · Criminal record

    Admin setup wandcops-n-crooks0.16
    One wand item with modes (station, district corners, hideout, pickup point, restricted zone, breaker structure + trigger), a particle outline of the selection, and /cops setup list|remove|tp. It writes through each registry's repository.
    UnblocksHideouts · Crooked contacts · Pursuit breakers

Page graph tags: all three are "Foundation, no player-facing feature of its own"; Districts and Station registry have no
prerequisites; the Admin setup wand follows the Station registry.

## 2. Features (0.16) - VERBATIM

Each block: headline, effort, ships-in, status, description, implementation note, "Where it stands" (already built), "Builds on",
"Needs first".

### 2.1 Dispatch from stations (Stage 2; Effort M; Part built)

    Dispatch from stations
    GTA V
    EffortM
    Ships in0.16
    StatusPart built
    The first response comes from whichever patrol is nearby. Everyone else leaves the nearest station, and the radio tells you they're coming. A crime next to a precinct is answered at once. A crime out in the hills buys you time.
    Squad members spawn at the nearest registered cop spawner within Spawner_Preference_Radius (80 blocks), otherwise on a ring beyond the player's view, and today they appear instantly. This feature adds named stations, a travel delay from the nearest one, and place-named radio ('Units 4 and 7 en route from Northside Station, ETA 20 s'). The crime-scene first sighting and the Dispatch_Wanted/Escalate lines already exist.
    [Dispatch] 10-71, shots fired in Market turf. Units respond.
    [Dispatch] Units 4 and 7 en route from Northside Station, ETA 20 s.
    [Unit 4] Visual on suspect. On foot, heading north to the Docks.
    [Unit 7] Lost visual. Searching the last known position.
    Where it standsBuilt in 0.12.0: nearest spawner within Spawner_Preference_Radius 80, else a ring spot (CopSpawnManager.spawnNearPlayer :71-84); crime-scene seed sighting and Dispatch_Wanted/Escalate radio with compass sides (CopManager.java:91-117, RadioSides.compass8). Built in 0.13.0: a spawner filter predicate. Remaining: named stations, a travel delay (pending-unit queue with ETA = distance/speed), place-named radio lines, and units appearing out of sight through the Keystone 1.15.0 out-of-sight spawn helper.
    Builds onCopSpawnManager.spawnNearPlayer(Player,int[,Predicate<Location>,CopRole]) over Keystone EntitySpawner.findClosestSpawnerLocation · settings.yml Cops.Spawn.Spawner_Preference_Radius · CopManager.onWantedStart/spawnTick · CopRadio.dispatch + RadioSides.compass8 · cop_radio_messages.yml Dispatch_Wanted · station registry names + districts & places PlaceNames · Keystone 1.15.0 out-of-sight spawn helper (promoted from Gang & Turf T3)
    Needs firstStation registry

### 2.2 Mixed squads and backup waves (Stage 2; Effort S; Part built)

    Mixed squads and backup waves
    GTA V
    EffortS
    Ships in0.16
    StatusPart built
    Three stars looks different from two. Sergeants push in while lieutenants with rifles hang back. Wipe a squad and you get a real breather before backup arrives, and the radio tells you it's on the way.
    Each star already scales squad size (Cops.Count) and, from 0.13.0, fills a role composition; a downed cop radios one extra cop for 30 seconds. Still open: let each Squad_Composition entry optionally name a Tier (so sergeants push in while lieutenants hang back), and after a full wipe hold the refill in the dispatch queue for a delay that shrinks with stars (15 s at one star down to 6 at five).
    Where it standsBuilt in 0.12.0: per-star count (Cops.Count) and Cops.Backup (+1 cop for 30 s on a casualty). Built in 0.13.0: per-star role composition (Squad_Composition, CopRole.nextRole). Remaining: tier mixing per star (one tier per star today, getTierForWantedLevel = min(level, maxTier), CopSpawnManager.java:118) and a breather after a full wipe (spawnTick refills everyone in one pass, CopManager.java:~508-520).
    Builds onCopSpawnManager.getTargetCopCount / getTierForWantedLevel · CopManager.spawnTick · cops.yml Cops.Squad_Composition, Cops.Roles, Cops.Tiers, Cops.Backup (H13) · BackupSettings, CopGroup.requestBackup/backupExtra · CopRole.nextRole · dispatch from stations pending-unit queue
    Needs firstDispatch from stations

### 2.3 Pursuit hand-off and intercept (Stage 2; Effort S; To build)

    Pursuit hand-off and intercept
    GTA IV
    Need for Speed: Most Wanted
    EffortS
    Ships in0.16
    StatusTo build
    Outrun a squad and the chase does not reset. The last officer radios which way you went, and the next units come at you from ahead, already knowing where you are.
    On the leash edge the group records your heading from the last 2 seconds of positions and the leader radios Handoff with a compass direction. For 10 seconds the spawn predicate prefers spots within 60 degrees ahead of you, and each new squad is seeded with the last-known sighting. With dispatch from stations in place it picks the station ahead.
    Builds onPursuingBehavior distance leash (:~77) · CopManager.spawnTick -> spawnNearPlayer with Predicate<Location> (CopManager.java:531, CopSpawnManager.java:79) · NpcSquad.reportSighting (:186) · RadioSides.compass8

### 2.4 Hideouts and cooldown spots (Stage 3; Effort M; To build)

    Hideouts and cooldown spots
    NFS Most Wanted
    GTA V
    EffortM
    Ships in0.16
    StatusTo build
    Barns, underpasses and gang garages are marked hiding spots. Duck into one while unseen and the search clock runs faster. Your gang's waypoints and owned turf count as hideouts for members.
    Admin-placed areas (and, for members, gang safehouses, gang waypoints and owned turf, all supplied through the districts & places RegionProvider) multiply the evasion clock. They only count if you arrive unseen.
    Builds online-of-sight evasion clock speed multiplier · districts & places RegionProvider (hideout tag; turf and gang safehouses contribute without a cops->turf import) · gangland-api Waypoint.WaypointType.GANG · admin setup wand for placement
    Needs firstLine-of-sight evasion · Admin setup wand

### 2.5 Crooked contacts and bribe stars (Stage 3; Effort S; Part built)

    Crooked contacts and bribe stars
    GTA Online
    GTA San Andreas
    EffortS
    Ships in0.16
    StatusPart built
    Call a crooked desk sergeant from your phone to wipe a star or two, at a price and on a cooldown. It only works while nobody is looking at you, and the same goes for the [WANTED] signs. Or find the police bribe stars hidden around the map: grab one and lose a star.
    The [WANTED] sign already removes stars for money. This puts it on the phone as a contacts page, with a price per star and a 10-minute cooldown, usable only while the evasion state is SEARCHING or EVADED. The sign's REMOVE and CLEAR get the same gate and cooldown in WantedAspect.canExecute, so paying to vanish in front of a squad stops working. Admin signs priced at 0 stay free. Both clears tag their cause through the wanted seam, so a bag you are carrying converts at the fence's cut instead of banking. Bribe stars are floating pickups at admin-set points on a respawn timer, built on the lootchest spawn-point pattern.
    Where it standsBuilt: the [WANTED] sign sells star removal. MoneyAspect withdraws the sign's flat price, then WantedAspect removes stars (WantedSign.java:37-40). It works anywhere, even with cops watching: canExecute only checks that you have stars (WantedAspect.java:62-79). Remaining: a phone contacts page (inventory/phone.yml), a price per star, a 10-minute cooldown, the unseen gate on both the phone and the sign, and bribe-star pickups placed with the wand. This is not the same as the detainment Handcuff_Bribe.
    Builds ongangland-impl WantedSign / WantedAspect · inventory/phone.yml and phone_*.yml pages · line-of-sight evasion state (SEARCHING or EVADED) as the unseen gate · Wanted.decrementLevel · admin setup wand pickup mode · gangland-lootchest respawn-timer pattern (copied, not imported)
    Needs firstLine-of-sight evasion · Admin setup wand

### 2.6 Containment perimeter (Stage 3; Effort M; To build)

    Containment perimeter
    GTA V
    SWAT 4
    Watch Dogs
    EffortM
    Ships in0.16
    StatusTo build
    Duck into a building at three stars and the cops stop piling in after you: two of them post up on the corners around the block while the rest sweep inside. Slip out between the posts and you are clear; walk past one and the whole squad is back on you.
    On CONTACT_LOST at 3 or more stars, up to 2-3 cops (Marksman and Defender first) take posts on a ring of the line-of-sight evasion zone radius with a clear rayTraceBlocks lane, and watch with canSee. A sighting reports, radios Eyes_On and returns the cop to PURSUING. Posts are exempt from stuck recycling. The perimeter ends on CONTACT, at wanted end, or after Max_Seconds.
    Builds onNpcSquadSignal.CONTACT_LOST/SEARCH · NpcSquad.lastKnownLocation/reportSighting · AbstractNpc.navigateTo/canSee/pauseNavigation · CopRadio.listenerFor · CopManager.recycles (exempt posts) · Keystone 1.15.0 hold-post
    Needs firstLine-of-sight evasion

### 2.7 Cold trail (Stage 3; Effort S; To build)

    Cold trail
    GTA V
    Need for Speed: Most Wanted
    Watch Dogs
    EffortS
    Ships in0.16
    StatusTo build
    Stop committing crimes and the city slowly loses interest. Every quiet minute makes your search clock run faster, and the cops stop calling more backup. Keep fighting and they keep coming; go quiet and the chase wraps up in a few minutes.
    One more multiplier on the evasion clock: 1 + Per_Minute (0.25) for each full minute since your last crime in the heat ledger, capped at Max (2.0). After 60 s with no crime, backup requests for your group are skipped and the leader says one 'Units returning to patrol' line. Exploit guards: it never runs while a cop can see you, it never lowers the base squad size for your stars (it only stops extra backup), any new crime resets it, and only heat-ledger crimes count (stars from signs and commands do not).
    Builds onEvasion clock speed multipliers (next to the outside-the-zone one and the hideout one) · heat ledger last-crime time · CopRadio.requestBackup / CopGroup.requestBackup · cops-n-crooks radio lines (next to Stand_Down) · Evasion block in cops-n-crooks YAML
    Needs firstLine-of-sight evasion · Heat ledger

### 2.8 Hospital respawn and one bill (Stage 4; Effort S; To build)

    Hospital respawn and one bill
    GTA
    Saints Row
    EffortS
    Ships in0.16
    StatusTo build
    When you are knocked down, you wake up at the nearest hospital, not at the one global spawn. You pay one bill: nothing if you're nearly broke, never more than a slice of your wallet, and less with bank insurance. With the shipped settings, your clean cash doesn't scatter on the street.
    An additive HOSPITAL WaypointType. performRespawn picks the nearest HOSPITAL waypoint in the death world and falls back to the configured one. The bill is the existing Death.Money charge through the shared evaluator: the Threshold check at PlayerDeathListener.java:116-119 and the bank-tier discount. It is charged at performRespawn on the downed path, keeping the recentDeaths dedup, or at quit while downed, and it is shown as a ward bill. The bundled items/money.yml ships Drop_Sources.PLAYER.Enabled: false; the key stays, so an admin can turn the drop back on. Exploit guard: with the wallet drop off, a friend can no longer kill you to move your wallet onto the ground.
    Builds ongangland-api Waypoint.WaypointType (Waypoint.java:82-90) · CustomPlayerDeathListener.performRespawn · WaypointRepository.java:56 (valueOf by name, so old rows still load) · WaypointTypeCommand
    Needs firstMoney formulas

### 2.9 Self-defence and posted-bounty takedowns are not crimes (Stage 1; Effort M; Part built)

    Self-defence and posted-bounty takedowns are not crimes
    RDR2 (bounty hunters are not outlaws)
    Rust (blame goes to whoever started the fight)
    EffortM
    Ships in0.16
    StatusPart built
    If someone jumps you and you win the fight, you don't get stars for it. Hunt down a player with a price on their head that other players posted, and you collect the money without becoming wanted. Kill a wanted player nobody has put money on, start the fight, or finish off someone who stopped fighting, and it's a crime as usual.
    A per-plugin map of victim to (last attacker, time, damage), fed by EntityDamageByEntityEvent and expiring after Self_Defence_Window (8 s). A kill counts as self-defence when the dead target dealt at least Min_Damage to the killer inside the window and the killer did not hit first. Cops are never covered (resisting the police is still a crime), and neither are uninvolved civilians. Exploit guards: same-gang pairs never count as a first strike, and a per-pair cooldown stops two accounts trading staged fights or bounties. A bounty takedown counts only when a posted bounty above Bounty.Minimum was actually paid; notoriety alone never exempts a kill. Licensed hunters and on-duty cops get their own rules later.
    Where it stands0.15.0 shipped a simpler form of both rules. Self-defence: a kill mints no Kill_Player crime, star or notoriety when the victim struck the killer first in the fight, inside a fixed 30 s window, counting player-on-player hits only (no config key). Posted-bounty takedown: only other players' escrow makes a kill a crime-free takedown; a killer who posted on the victim gets his own money back, but the kill stays a crime, and a target with only notoriety takes the crime path. Remaining for 0.16: the Self_Defence_Window and Min_Damage keys (the fixed first-strike window can be baited for 30 s), the same-gang first-strike guard and the per-pair cooldown. Still open: Bounty.Minimum ships at 0, so an accomplice's $0.01 post turns a kill into a takedown (WB-48, an owner question).Fight map in EntityDamageListener (its leak fixed in ea072adb); EntityDamageListenerTest.selfPostedBounty_killIsStillACrime, claim_paysPostedOnly
    Builds onEntityDamageListener.handlePlayerKills (gangland-impl listener/player/EntityDamageListener.java:120-147) and handleMobKills · EntityDamageByEntityEvent (same listener) as the attacker feed · CivilianDeathRewardListener hostile COMBAT exemption (gangland-civilians) · heat ledger crime entry point · cops-n-crooks Heat block
    Needs firstHeat ledger · Paid bounties


Page text inside non-0.16 cards that constrains the 0.16 cards:
- Scanner map (0.17) "hideouts regions for the green markers"; Patrols (0.17) "station registry as beat anchors" and "Keystone 1.15.0
  hold-post shared with turf guards": 0.17 consumers of the 0.16 registries. Keep the registry and RegionProvider read API
  query-by-location so 0.17 needs no 0.16 rework.
- Crimes worth committing (0.17) "districts & places restricted tag for trespass": the RegionProvider tag vocabulary MUST include
  `restricted` and `hideout` (page: "tags such as restricted/hideout"); the wand has a "restricted zone" mode.
- Pursuit breakers / Roadblocks (0.19): the wand lists "breaker structure + trigger" mode; 0.16 may ship the mode as a stored-only
  placeholder; the behaviour is 0.19.
- Mounted units (0.20): "dispatched from the ring (no station within Spawner_Preference_Radius)": keep that ring-vs-station
  distinction observable in dispatch code (a flag on the dispatched unit).

## 3. Money rules tagged 0.16 - VERBATIM

Page table columns: Moment | Today | In this plan | Why it makes sense | Ships in. Rows that carry 0.16 (group-label lines like
"Hospital & one bill" are the page's card tags for the row above):


    Wasted (any death or down, wanted or not) | Death.Money.Formula 'balance * 0.15' is burned from the wallet, minus the bank-tier discount, and is skipped when the balance is at or under Death.Money.Threshold. The shipped default is 1,000 (settings.yml:227). It is charged on both the death path and the downed path (PlayerDeathListener.java:81-85, :91-114, threshold check :116-119, charge :131-157). A broken formula throws out of the death handler (:213-228). | Still one hospital bill per death, wanted or not, from the existing formula. A broken formula no longer breaks deaths: it falls back to the shipped 'balance * 0.15' and warns once. On the downed path the bill moves to the moment you actually respawn, so a down that turns into an arrest pays only the charge sheet. No second medical charge is added. | You pay for your treatment once. Poor players are not ruined and the bank is never touched. | today (config); safe formula 0.15 (shipped); bill at respawn 0.16
    Hospital & one bill |

    Your own cash dropped on death | A cash item is rolled at 10-2,000, scaled by floor(balance × 0.15) and clamped to the variation max. It is withdrawn from the wallet and dropped at the body, on top of the death bill (MoneyDropListener.java:67-88; items/money.yml Drop_Sources.PLAYER). | An admin toggle, Money.Drop_Sources.PLAYER.Enabled in items/money.yml. The bundled file ships it false, so a death costs one bill. Existing servers keep the value they have until an admin changes it. Once the bag exists, only your hot bag spills on the street. | One death should cost one thing. Spilling stolen cash makes sense, but scattering your savings does not. | today (config); bundled default off 0.16
    Hospital & one bill |

    [WANTED] sign removes stars | Charges the sign's flat price (MoneyAspect withdraw, WantedSign.java:37-40) and works anywhere, even with cops watching. canExecute only checks that you have stars (WantedAspect.java:62-79). | Same price, but it works only while no cop can see you, with the contacts cooldown. If you are carrying a bag, it converts at the fence's cut. | Paying to lie low is fine. Paying to vanish in front of a squad is not, and neither is using the sign as a cheaper fence. | 0.16; bag rule 0.17
    Crooked contacts |
    Crooked contact on the phone | Does not exist. | A price per star, only while unseen, on a 10-minute cooldown. | A chosen purchase with a cost and a risk. | 0.16
    Crooked contacts |

    Busted (jail intake) | No money moves. Your items are seized and returned on release, your stars are cleared, and the sentence is 180 s + 60 s per star (JailIntakeService.java:50-64). | A charge sheet with a price for each crime from this chase, priced linearly by base plus per star until the heat ledger has prices, and capped. Your wallet pays what it holds and the rest becomes extra time, also capped. You are charged once and never also get a hospital bill. As shipped in 0.15.0 the sheet lists the chase's crimes but is priced min(10,000, 200 + 250 × stars at arrest) until per-crime prices arrive in 0.16; the shortfall is served as 0.1 s per unpaid dollar, rounded up, at most 600 s; and an arrest that commits on death gets no sheet, because until 0.16 that player pays the hospital bill instead. | It is a court fine for named crimes, charged at the moment you are caught. A broke player pays in time and nobody goes into debt. | 0.15 (shipped)


Binding reading of those rows for 0.16:
- Hospital bill: one bill per death, wanted or not, existing `Death.Money.Formula` through MoneyFormula (0.15), the Threshold check
  (PlayerDeathListener `:116-119`) and the bank-tier discount; on the downed path charged at `performRespawn` (keeps `recentDeaths`
  dedup) or at quit while downed; shown as a ward bill. A down that turns into an arrest pays only the charge sheet (the arrest-on-down
  path is 0.18; 0.15.0's "no sheet when the arrest commits on death" deviation stays until then).
- Bundled `items/money.yml` ships `Money.Drop_Sources.PLAYER.Enabled: false` ("bundled default off 0.16"); the key stays; existing
  servers keep their value (FileHandler never overwrites an existing file). COP drop default off is 0.17, not 0.16.
- `[WANTED]` sign: same price, works only while no cop can see you, with the contacts cooldown; admin signs priced at 0 stay free;
  the bag-conversion clause is 0.17.
- Crooked contact: a price per star, only while unseen (evasion state SEARCHING or EVADED), on a 10-minute cooldown.
- Per-crime charge-sheet prices: the 0.15.0 deviation says "until per-crime prices arrive in 0.16", but the page's 0.16 roadmap and
  card list contain no charge-sheet card. Ruling: keep the shipped star-priced sheet (`min(10000, 200 + 250 x stars)`); the per-crime
  table is NOT in 0.16 unless the owner pulls it in (open point O4, section 4). Do not change the shipped formula silently.

Guardrails that apply to every 0.16 card (page "Rules for every feature"):

    Behind a toggle. Each feature gets its own Enable key in the module's own config, which is where the module API rules put new settings.
    Stock APIs only. Boss bars, per-player particles, map renderers, compass targets and Citizens NPCs cover everything here. The core jar ships no NMS, and this plan doesn't need any.
    Inside the AI budget. Choppers, dogs and roadblock cops count toward Max_Per_Player, and their sight checks run on the existing AI_Tick_Rate of 10 ticks.
    Fair PvP. No bounty payouts between gang mates or allies, a cooldown on switching sides, no clocking in while wanted, and a per-target cooldown on hunter contracts.
    Keystone stays generic. Only product-free pieces move into Keystone, and only once a second plugin uses them: the out-of-sight spawn helper and hold-post leash in 1.15.0, NPC carrier bodies (hover, mount, animal) in 1.16.0. Wanted levels, crimes and stations stay in Gangland.
    Spigot 1.16.5 floor. Nothing here needs newer Bukkit API or Paper. Sight checks use World.rayTraceBlocks, never Paper's hasLineOfSight(Location).
    Additive API. New surface such as CrimeCommittedEvent, WantedEvasionStateEvent, RegionProvider, the vehicle events and DutyView is added, never changed, with one minor bump per release that adds any (2.1 shipped with 0.15, Gang & Turf takes 2.2, then 2.3 to 2.5).


## 3a. Roadmap entry for 0.16 - VERBATIM


    0.16
    Where they come from
    Keystone 1.15.0
    gangland-api 2.3
    Police come from real places: named stations and districts, units that take time to arrive and that the radio names, rosters that look different per star, and somewhere to hide.
    Dispatch from stations
    Mixed squads
    Hideouts
    Crooked contacts
    Pursuit hand-off
    Containment perimeter
    Hospital & one bill
    Cold trail
    Self-defence rules
    FoundationsDistricts & places · Station registry · Admin setup wand
    Stations and districts are the shared geography that patrols, record, transport, gang heat and duty all need, so they go in early. This is also the release that justifies the Keystone 1.15.0 promotions Gang & Turf reserved. Hideouts, contacts and the perimeter consume the 0.15 evasion clock, and hideouts and contact pickups are placed with the wand. The hospital WaypointType rides the same 2.3 API bump.
    Done whenAn admin uses the setup wand to place 'Northside Station' and a 'Docks' district. Commit a crime in the Docks: the radio says units are en route from Northside Station with an ETA, and they arrive after the delay from a spot you cannot see. At three stars the squad mixes tiers: sergeants push in while higher-tier officers hold back. Wipe a three-star squad and get a breather before the refill. Break contact and two cops post up on the corners while the rest search. Outrun the squad and the next units come from ahead of you. Reach a hideout unseen and the clock runs faster. Stop committing crimes for a minute and the radio says units are returning to patrol while your clock speeds up. Wipe a star from the phone contacts page while unseen; try a [WANTED] sign in a cop's view and it refuses. A rival shoots first and you kill them: no star. Claim a posted bounty: no star. Kill a five-star player nobody has put money on: you gain a star. Die and wake at the nearest hospital with one bill, charged when you respawn, and with the bundled settings none of your own cash on the street. “A rival shoots first and you kill them: no star” and “Claim a posted bounty: no star” already hold on 0.15.0, with a fixed 30 s first-strike window; 0.16 adds the tuning keys and the exploit guards.


## 4. "Decisions for you" relevant to 0.16 (recommended option = decided)

Page status: "Decided 2026-10-05. The owner took every recommended answer below, except the release order." Format: id: question,
RECOMMENDED answer, then the page's alternatives.

- **K1 Keystone promotions and the shared 1.15.0 slot.** RECOMMENDED: promote the out-of-sight spawn helper and hold-post + leash into
  Keystone 1.15.0 at the start of 0.16 (Cops N Crooks is the second consumer); 1.15.0 also carries the Gang & Turf CommandMap helper
  if it is pulled in by then (else 1.15.x); keep the map painter, block restore and perch finder in Gangland; carrier bodies (hover,
  mount, animal) are Keystone 1.16.0 for 0.20 only. Alternatives: keep everything in Gangland and accept two copies of the spawn and
  post logic; or build the chopper and mount bodies in Gangland behind Citizens reflection.
- **A1 gangland-api bump cadence.** RECOMMENDED: one additive minor per release that adds surface: 2.2 (0.15 as the page first
  numbered it), 2.3 (0.16), 2.4 (0.17), 2.5 (0.20); 0.18 and 0.19 add none. Live numbers: 0.15.0 = 2.1, 0.15.1 = 2.2, so 0.16 = 2.3.
  Alternative: batch every Cops N Crooks API surface into one bump at 0.15 (front-loads design of seams not yet needed).
- **W1 What does being wasted cost: a hospital bill, or your cash on the street?** RECOMMENDED: one hospital bill for every death,
  wanted or not. Use the existing Death.Money formula: 15% of the wallet, skipped at or under the threshold, minus the bank discount,
  charged at respawn on the downed path. Ship the PLAYER cash drop off in the bundled file, as a switch an admin can turn back on. Once
  the bag exists, only hot cash spills. Alternatives: keep the street drop as the only loss and set Death.Money.Formula to '0'; or
  charge the bill only while wanted (gate the formula on wanted > 0); or keep both charges, as today.
- **W2 Is killing a wanted player nobody has put money on a crime?** RECOMMENDED: yes, unless it was self-defence. Only posted-bounty
  takedowns are exempt. Licensed hunters and on-duty cops get their own rules when those features land. Alternative: exempt any kill of
  a player at or above a notoriety or star threshold (makes vigilante play easy; a crew can farm one of their own for free kills).
- **W3 What does logging out mid-chase do?** RECOMMENDED: stars persist, as today. The bag spills where you logged out (server stop
  saves it instead; the bag is 0.17). On rejoin, the pursuit comes from a station after a short grace period, so quitting is never the
  best way out. Alternatives: freeze the stars and spawn nothing until your next crime (today's free escape); or count a logout while
  seen as a crime on your record. 0.16 share: a RESTORE start with a live chase dispatches through the station queue with a grace
  delay instead of an instant ring spawn.
- **W4 Who pays a bounty, and bounties at the upgrade** (settled in 0.15; Self-defence depends on it). RECOMMENDED: by default only
  money players posted is paid; notoriety is not paid unless Bounty.Pay_Notoriety is on; every pre-upgrade bounty counts as posted
  once. Alternatives: treat every existing bounty as notoriety only; or keep the server-made bounty, cap it hard and stop the 300 s
  doubling.
- **O1 Bounty.Minimum ships 0 (WB-48).** An accomplice who is not an ally can post $0.01 and turn a kill into a crime-free takedown.
  Page fix: a non-trivial Minimum, or a floor on other players' escrow (settings.yml:254). RECOMMENDED: fix it in the Self-defence card
  (a takedown counts only when a posted bounty above Bounty.Minimum was paid; ship a non-trivial default Minimum or an escrow floor).
- **O2 Self-posted bounties.** A killer who posted on the victim gets the money back, but the kill is still a crime. RECOMMENDED:
  confirm (as shipped).
- **O3 A one-shot cop kill gives two stars** (Assault_Cop 100 + Kill_Cop 150, x1.5 when seen). RECOMMENDED: leave it; tune weights or
  thresholds in YAML (no code change). Note: in AUTO a cop kill is always a rampage trigger.
- **O4 Per-crime charge-sheet prices** (not a page decision; raised by the 0.15.0 deviation note). RECOMMENDED: keep star pricing in 0.16.
- Notoriety is not cleared on death or arrest (page: open). RECOMMENDED: leave to 0.19 (criminal record reads it).
- Already decided and binding on 0.16 (SPEC-0.15 rulings): line-of-sight evasion replaces the fixed timer (Repeating_Timer safety net,
  Evasion.Enable switch); Drop_Mode ONE_STAR default; the wanted boss bar shows own stars and countdown, never cop awareness; star-drop
  charge off by default; Lose_Money false = no charge; turf-war kills half heat, turf-defender kills none; police = a shift, not a
  faction (0.20).
- 0.15.2 decisions (AUTO plan, each recommended answer taken): D1 own 0.15.2 branch, 0.15.1 merges first; D2 ONE_STAR stays the default;
  D3 learn only while AUTO; **D4 public event: none now, add WantedEvasionDropEvent in the 0.16 api 2.3 bump only when the criminal
  record needs it (0.19), so NOT in 0.16**; D5 rampage by stars (peak 4); D6 small fry up to 2 crimes and 2 stars; D7 no
  /glw wanted auto yet; D8 keep the shorter timer for always-caught players; D9 mid-search progress carry-over fixed later (docket 179);
  D10 shared MySQL last writer wins.

## 5. "Already built" - what exists today (page "Where it stands" + artifact/shipped-facts.md)

Base: 0.15.0 on master 9d4776ad (1791 tests; GanglandApi 2.1, now 2.2 on 0.15.1), plus 0.15.2.

- **Dispatch from stations.** Built 0.12.0: nearest cop spawner within `Cops.Spawn.Spawner_Preference_Radius` 80, else a ring spot
  (`CopSpawnManager.spawnNearPlayer` :71-84); crime-scene seed sighting and `Dispatch_Wanted`/`Escalate` radio with compass sides
  (`CopManager.java:91-117`, `RadioSides.compass8`). Built 0.13.0: a spawner filter predicate. Remaining: named stations, a travel
  delay (pending-unit queue, ETA = distance/speed), place-named radio lines, units appearing out of sight through the Keystone 1.15.0
  helper. Example line: "Units 4 and 7 en route from Northside Station, ETA 20 s".
- **Mixed squads.** Built 0.12.0: per-star count `Cops.Count` (Base 2, Per_Level 1) and `Cops.Backup` (+1 cop for 30 s on a
  casualty). Built 0.13.0: per-star role composition (`Squad_Composition`, `CopRole.nextRole`). Built 0.15.0: regroup (Regroup.Enabled,
  Casualties 2, Window_Seconds 20, Fall_Back_Seconds 15, Cooldown_Seconds 60, Arrival_Radius 24.0; a regroup grants its own backup that
  ignores the backup cooldown). Remaining: tier mixing per star (one tier per star today, `getTierForWantedLevel = min(level, maxTier)`,
  `CopSpawnManager.java:118`) and a breather after a full wipe (`spawnTick` refills everyone in one pass, `CopManager.java:~508-520`).
- **Crooked contacts.** Built: the `[WANTED]` sign sells star removal (`MoneyAspect` withdraw, then `WantedAspect` removes stars,
  `WantedSign.java:37-40`); `canExecute` only checks stars (`WantedAspect.java:62-79`). Built 0.15.0: WantedCause (10 causes incl. SIGN,
  BRIBE) and `Wanted.setLevel(int, WantedCause)`; WantedEndEvent carries the cause. Remaining: phone contacts page
  (`inventory/phone.yml`), price per star, 10-minute cooldown, unseen gate on phone and sign, bribe-star pickups placed with the wand.
  Not the detainment Handcuff_Bribe.
- **Self-defence / takedowns.** 0.15.0 shipped a simpler form: no Kill_Player crime/star/notoriety when the victim struck the killer
  first inside a fixed 30 s window, player-on-player only, no config key (fight map in `EntityDamageListener`, leak fixed ea072adb).
  Takedown: only other players' escrow makes a kill crime-free; a self-poster gets his money back but the kill stays a crime; notoriety
  alone takes the crime path. Tests: `EntityDamageListenerTest.selfPostedBounty_killIsStillACrime`, `claim_paysPostedOnly`. Remaining for
  0.16: `Self_Defence_Window` (8 s) and `Min_Damage` keys, same-gang first-strike guard, per-pair cooldown, WB-48.
- **Hideouts.** Not built. 0.15.0 evasion has `Outside_Zone_Speed 2.0`; "Hideout_Speed is not in 0.15; it moves to 0.16, where
  hideouts arrive with the setup wand". gangland-api already has `Waypoint.WaypointType.GANG`.
- **Containment perimeter.** Not built. Existing: evasion clock (0.15), Keystone `NpcSquadSignal.CONTACT_LOST/SEARCH`,
  `NpcSquad.lastKnownLocation/reportSighting`, `AbstractNpc.navigateTo/canSee/pauseNavigation`, `CopManager.recycles`.
- **Pursuit hand-off.** Not built. Existing: `PursuingBehavior` distance leash (`:~77`), `CopManager.spawnTick -> spawnNearPlayer` with
  `Predicate<Location>` (`CopManager.java:531`, `CopSpawnManager.java:79`), `NpcSquad.reportSighting`, `RadioSides.compass8`.
- **Cold trail card.** Not built. Existing: HeatLedger last-crime time (`CrimeRecord.at()`), the evasion clock and its outside-zone
  multiplier, `CopRadio.requestBackup`/`CopGroup.requestBackup`, the "Stand_Down" radio line.
- **Hospital respawn.** Not built. 0.15.0 shipped MoneyFormula (death bill falls back to `balance * 0.15`, warns once; Lose_Money
  false = no charge or payout). Touch points: `Waypoint.WaypointType` (`Waypoint.java:82-90`), `WaypointRepository.java:56` (valueOf by
  name, old rows still load), `WaypointTypeCommand`, `CustomPlayerDeathListener.performRespawn`, `PlayerDeathListener.java:116-119`.
- **Regions, stations, wand.** Nothing exists. Admin regions live in gangland-core; turfs in gangland-turf.
- Heat and evasion shipped in 0.15.0: HeatLedger + CrimeRecord (`wanted/heat/`); crimes published = Kill_Player, Kill_Civilian,
  Kill_Cop, Assault_Cop (once per attacker+cop per 10 s), Resisting_Arrest; the other seven weights ship with no publisher.
  EvasionClock/EvasionSnapshot (`wanted/evasion/`): Lost_Sight_Seconds 3, Search_Radius [40,60,90,130,180], Seconds_To_Drop
  [10,20,30,45,60], Outside_Zone_Speed 2.0; config in the module's `copsncrooks/wanted.yml` (npc/wanted.yml before 0.15.1).

## 6. Overlap with 0.15.2 (AUTO) - where 0.16 cards touch it

0.15.2: AUTO keeps the 0.15 evasion clock and decides at the drop point how many stars fall and how long the next timer is. Endings
(first match wins): STILL_HOT, PETTY, COLD_TRAIL (all stars), CLEAN_BREAK (half), HUNKER_DOWN (1). Momentum: next timer =
Seconds_To_Drop x step x habit, clamped 0.4-1.6 of Seconds_To_Drop and never below 1 s; habit factor 0.6-1.6.

- **Evasion clock multipliers.** Hideouts (Hideout_Speed) and the Cold trail card (1 + Per_Minute 0.25 per full quiet minute, max 2.0)
  are two more speed multipliers next to Outside_Zone_Speed. They scale countdown PROGRESS (speed-weighted `Track.progress`); AUTO's
  momentum factor scales the NEED. So: need = Seconds_To_Drop x step x habit; progress per second = base x outside x hideout x
  coldTrail. AUTO's Floor (0.4) bounds the timer, not the speeds, so add a cap on the combined speed (suggested key and default 4.0)
  to keep "no timer below 0.4" true in effect. Hideout speed counts only "if you arrive unseen"; Cold trail "never runs while a cop can
  see you" and "any new crime resets it"; only heat-ledger crimes count (stars from signs and commands do not).
- **ChaseArc `quiet` vs Cold trail minutes.** ChaseArc `quiet` (0.15.2) counts seconds since the last ledger-kept crime OR star raise;
  the card counts only ledger crimes. Do not reuse `quiet` for the card: read the HeatLedger last-crime time, as the card says.
- **COLD_TRAIL ending vs the Cold trail card.** Page: "Not the same as the 0.16 Cold trail card: that one speeds the clock up while you
  stay quiet; this one decides how many stars fall when it runs out." Both ship and are independent: the card changes WHEN the clock
  finishes, the ending HOW MANY stars fall. The card makes the clock finish sooner, so AUTO's COLD_TRAIL test (`chase >= Ratio x T(peak)`
  and `quiet >= Quiet_Seconds` 90) is evaluated earlier in the chase. Do not reuse the config block name `Cold_Trail` (the AUTO block
  already owns `Wanted.Evasion.Auto.Cold_Trail`); suggested block `Wanted.Evasion.Quiet_Speed` (Enable, Per_Minute 0.25, Max 2.0,
  Backup_Skip_Seconds 60). Name is a suggestion.
- **Backup suppression vs regroup/backup.** After 60 s with no crime, backup requests are skipped and the leader says one "Units
  returning to patrol" line. A 0.15 regroup grants its own backup that ignores the backup cooldown; the card's skip must also suppress
  regroup-granted backup and never lowers the base squad size for your stars. The mixed-squads post-wipe breather delays refilling the
  BASE squad and is not skipped by cold trail.
- **Rampage opening and heat crime weights (MUST-FIX, PLAN.md risk 11).** AUTO rampage = opening >= Rampage_Crimes (4 crimes in the
  first Opening_Seconds 30) OR peak >= Rampage_Peak_Level (4) OR a cop killed. Today every published crime weighs 80 or more, so four
  crimes in 30 s is real violence. Cheap crimes: Brandish_Near_Cop 25, Assault_Civilian 30, Car_Theft 60. Before 0.16 publishes any
  crime lighter than 80, the opening must count only heavy crimes (suggested: crimes with weight >= a new `Rampage_Min_Weight`, default
  80) or sum heat instead of counting crimes; PETTY's `Max_Crimes` has the same flaw (two cheap crimes read as small fry by count).
  Pin with a test: four Brandish_Near_Cop crimes in 30 s are NOT a rampage; one Kill_Cop still is. The 0.16 cards publish no new
  crime themselves (restricted zones are only a RegionProvider tag until Trespass_Restricted in 0.17), so this is a pre-condition for
  0.17; the 0.15.2 follow-up note files it for 0.16, so land it in 0.16.0.
- **Self-defence vs heat ledger.** A self-defence or takedown kill publishes NO Kill_Player crime, so it never feeds AUTO's crimes,
  opening or quiet inputs. The new Self_Defence_Window (8 s) replaces the fixed 30 s window.
- **Hospital bill vs AUTO learning.** Any death resets the stars and is never learned (0.15.2). The hospital respawn must keep
  `WantedCause.DEATH` on the reset so the planner and learner guards still fire.
- **Dispatch grace vs `quits`/RESTORE.** A RESTORE start with no arc counts quits = 1 and locks the chase STILL_HOT (logout lock). The
  W3 rejoin grace delay must not change that lock, must not count as a sighting, and must not reset `quiet`.
- **Perimeter vs evasion zone and respots.** Posts sit on a ring of the evasion zone radius (Search_Radius; the zone shrinks to the new
  level after a drop while the centre stays). A post sighting is a sighting: it resets the countdown and bumps ChaseArc `respots`
  (affects PETTY/CLEAN_BREAK via Respot_Limit 4). Posts are exempt from stuck recycling (`CopManager.recycles`).
- **Dispatch queue vs evasion ownership.** EvasionClock owns decay only while the group has a live cop that is not walking home;
  with units still in the dispatch queue and none live, the Repeating_Timer safety net could drop a star. Recommended: a non-empty
  pending-unit queue counts as live for ownership.
- **Config.** All new keys go in the module's `copsncrooks/` YAML with code defaults (an upgraded server keeps its old files; a
  missing key = default); the 29-key AUTO block is read only while Drop_Mode is AUTO.

## 7. Done when - release pass

Page "Done when" for 0.16 is quoted in 3a. Release pass (0.15 practice): each sentence becomes an acceptance scenario in
brainstorming/cnc-overhaul-2026-10-05/acceptance/; R1-R4, N1-N8, A1-A9 stay green on default profiles; `mvn clean install
-DskipTests` and `mvn test` green; docs + CHANGELOG.md + CHANGELOG.bbcode.txt; docket updated; `graphify update . --force`.
