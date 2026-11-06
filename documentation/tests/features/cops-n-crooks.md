# Cops N Crooks — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/cops-n-crooks.md) | [Developer Doc](../../developer/cops-n-crooks.md)

---

## Overview

Police NPC AI: cop spawners scale with wanted level, pursuit + detainment + jailing flow. Cops are Citizens NPCs.
Managed by `CopService`, `CopSpawnManager`, `DetainmentService`, `JailManager`. All NPC infrastructure lives in
`gangland-features/cops-n-crooks` per `feedback_npcs_in_copsncrooks`.

**Modules involved:** `gangland-features/cops-n-crooks`, `gangland-impl`.

---

## Pre-Conditions

- [ ] Citizens plugin loaded.
- [ ] At least one cop spawner configured or created at runtime.
- [ ] `copsncrooks/cops.yml` and `cops.yml` (root) present.

---

## Smoke Test

- [ ] `/glw cop spawner set` → creates a cop spawner at your location.
- [ ] `/glw cop spawner list` → shows the new spawner.
- [ ] Raise wanted level: `/glw wanted add 3` → cops spawn from the spawner within the configured radius.
- [ ] Confirm each spawned cop NPC has `NPC.Metadata.SHOULD_SAVE = false` — run `/citizens save` and verify the
  spawned cops are **not** persisted by Citizens. Reference: `feedback_citizens_should_save_flag`.
- [ ] Cop pursues the wanted player, fires their pooled weapon.
- [ ] Cop catches up → handcuff/detainment flow begins (see [jail-detainment](./jail-detainment.md)).

---

## Edge Cases

- [ ] Spawn cops, then `kill -9` server → cops do not re-spawn as orphan Citizens NPCs on restart. Our repository
  owns them.
- [ ] Remove a spawner with active cops → active cops persist until culled; new ones do not spawn.
- [ ] Wanted player enters safe zone → cops break pursuit per config.
- [ ] Wanted player logs out mid-pursuit → cops despawn per config.

---

## Lose Them, Regroup and Shot Noise (0.15.0)

- [ ] Cops hunting you lose sight of you for `Lost_Sight_Seconds`: the squad radios the lost contact, the zone ring
      shows, and a star drops after `Seconds_To_Drop` out of sight (see the Wanted & Bounty checklist).
- [ ] Kill two cops in quick succession (`Regroup.Casualties` inside `Window_Seconds`): the rest pull back to cover with the
      Regroup line, radio for backup, and push together with the Regroup_Push line once all of them, backup included,
      are within `Arrival_Radius` of you (or after `Fall_Back_Seconds`). With `Cops.Backup.Enabled: false` they fall
      back and push without either line.
- [ ] `Cops.Regroup.Enabled: false`: the squad keeps fighting as in 0.13.0. A squad set to cuff first never regroups.
- [ ] Fire a Bartizan gun within 48 blocks of a cop of your squad: the squad turns to your position (counts as a sighting)
      and the nearest cop says the Shots_Fired line, at most once per 3 s.
- [ ] A shot outside `Shot_Noise.Radius`, a melee weapon, a throwable beyond 16 blocks, or a shot by a player who is not
      wanted, reveals nothing; `Shot_Noise.Enabled: false` silences all of it.
- [ ] An existing `cops.yml` without the `Regroup` / `Shot_Noise` / three new `Cooldown_Ticks` keys behaves as the shipped file.
- [ ] `copsncrooks/wanted.yml` and `copsncrooks/wanted_messages.yml` are copied on first boot and are never overwritten afterwards.
- [ ] Console shows `Runtime modules: ... loaded, 0 fault(s)` with cops-n-crooks and civilians at `Host_Api: 2.1`.

---

## Where the Police Come From (0.16.0)

Numbers S1-S16 match the acceptance scenarios; S9-S13 are in the [Wanted & Bounty](./wanted-bounty.md) checklist and
S14-S15 in the [Waypoints](./waypoints.md) checklist. Run with `Debug.Enabled: true` for module "Cops N Crooks" to see the
`DISPATCH`, `UNIT`, `PERIMETER`, `HANDOFF` and `EVASION` debug lines. Needs Keystone 1.15.0 and the `gangland.command.cop.setup`
permission.

**S1 Setup wand**
- [ ] `/glw cop setup wand` gives the wand; left click a block = pos1, right click = pos2 (one right click sets pos2 once).
- [ ] A particle outline of the selection shows to the admin holding the wand and to nobody else; it stops when the wand is not held.
- [ ] `mode station`, stand at the spot, `save Northside Station` → "Station Northside Station (#1) saved, N spawner(s) assigned".
      Saving the same name again stores nothing.
- [ ] `mode district` with pos1 + pos2 around the docks, `save Docks` → a region saved with the `district` tag.
- [ ] `list`, `list station`, `list region` print `kind id name world x y z [tags]`; `tp station 1` and `tp region 1` teleport you;
      `remove region 1` and `remove station 1` remove them (the station frees its spawners).
- [ ] `link 1 <jailId>` then `link 1 none`; an unknown station or jail id refuses.
- [ ] A player without `gangland.command.cop.setup` can use none of it, and the wand does nothing in his hand.
- [ ] Restart the server: stations, regions and points are still there (SQLite and MySQL).

**S2 Dispatch from a station**
- [ ] With a station 90 blocks away and the suspect indoors/hidden, commit a crime in the Docks: the radio says the wanted line with
      "the Docks" and "3 units en route from Northside Station, ETA 9 s" (ETA = distance / `Unit_Speed`, 0..40 s).
- [ ] Units appear only after the ETA, from a spot you cannot see (`UNIT ... fromStation=true hidden=true`).
- [ ] While the units are on the road no star drops and no search opens (`EVASION ... hold=enroute`).
- [ ] A world with no station: cops appear at once around you as in 0.15 (`station=ring eta=0s`).
- [ ] `Cops.Dispatch.Enabled: false` restores the instant ring spawn.

**S3 Mixed squads**
- [ ] At three stars the squad has mixed tiers (`UNIT ... tier=2` and `tier=3`): the Pointman/Assault tier 2, the others tier 3.
      Four and five stars use the `Commander@4` / `@5` lists. Removing the `@suffixes` from `cop_roles.yml` makes every unit the star's tier.

**S4 Breather**
- [ ] Kill every cop of a three-star squad inside 10 s: "Squad down. Backup inbound in N s." and `DISPATCH ... reason=wipe hold>0`
      (10 s + the ETA at 3 stars); the refill arrives after the hold. A wipe is counted once.
- [ ] `Breather.Enabled: false`: the refill is immediate.

**S5 Perimeter**
- [ ] At three stars break contact: `PERIMETER ... start posts=2 radius=32`, two Marksmen/Defenders walk to posts, say "Holding the corner.",
      and hold within 4 blocks; the rest of the squad searches.
- [ ] Walk into a post's line of sight: "Eyes on suspect ..." and the perimeter ends (`end reason=SIGHTED`/`CONTACT`); stay hidden
      60 s: `end reason=TIMEOUT`. Every post killed or sent home by a star drop: `end reason=EMPTY`. A search at 1-2 stars posts nobody. `Perimeter.Enabled: false` posts nobody.
- [ ] Attack a posted cop: it drops its post and fights.

**S6 Hand-off**
- [ ] Outrun the squad until the pursuers give up and walk home: "Lost him heading <direction>. Units ahead, pick him up."
      (`HANDOFF ... heading=`), then the next `DISPATCH ... bias=true` and `UNIT ... bias=true ahead=true` spawn in the 60 degree cone
      ahead of you and report your last position, not your live one. One hand-off per bias; `Handoff.Enabled: false` skips it.

**S7 Hideout**
- [ ] Place a `hideout` region with the wand (or own a turf, or set a gang waypoint) and reach it unseen: `EVASION ... hideout=2.0`
      and the countdown runs about twice as fast. A rival gang's turf or waypoint gives no bonus (`hideout=1.0`); the hideout you were
      seen in gives none. The total never exceeds `Max_Speed`.

**S8 Cold trail**
- [ ] Stay quiet for a minute (no crime, no new star, no sighting): `EVASION ... quiet=1.25`, rising to 2.0; at 60 s "Units returning
      to patrol." and no backup wave. Offline time never counts. `Quiet_Speed.Enable: false` keeps `quiet=1.0`.

**Bribe stars** (placed with `mode pickup`)
- [ ] A floating nether star appears at the pickup point and never despawns or falls.
- [ ] Wanted and unseen, walk within 1.5 blocks: you lose 1 star ("You pocketed a police bribe star"), the item vanishes and returns
      after 300 s. With a cop who saw you recently: "Not with a cop watching.", the star stays. `Bribe_Stars.Enable: false` shows none.

**S16 Logout and rejoin**
- [ ] At one star, quit mid-chase and rejoin: the chase is restored, `DISPATCH ... reason=restore hold=15s`, `EVASION ... hold=enroute`,
      the star is still there when the first unit appears; the cops are not dropped on your spot.

**Ids**
- [ ] Start with a cop spawner and a jail in a world that is not loaded at boot (a world a multiworld plugin loads later); place a new
      spawner and a new jail → their ids do not reuse the unloaded world's ids (docket T-180); load the world → both rows present.

---

## Disguises and Suspect Description (0.21.6)

Each row needs a wanted player with a cop of the chase (Test Server; Bartizan for the police outfit and the AK-47). Chat
lines come from `wanted_messages.yml` `Disguise.*`; debug lines need `Cops.Debug` on.

- [ ] **Cover blink, 1 star.** Step behind a trunk for 0.5 s, swap two pieces, step out → still recognised, the radio says
      the new look, no "don't recognise" line (failed in 0.21.5). Live: DIS-BLINK.
- [ ] **Sneak tap or dark, clear line.** Night road, cop 13 blocks away in view, hold shift, swap two pieces → still
      recognised.
- [ ] **Beat cop watching, squad far away.** 1 star, daylight, beat cop 8 blocks away, swap two pieces → still recognised
      when the squad arrives. Live: DIS-WITNESS.
- [ ] **Flicker.** Run along a lamp-lined street, seen and blind alternating → recognition never drops at any difficulty.
- [ ] **Last watcher killed.** 2 stars, kill the only cop in range, swap → cops arriving within 3 s learn the look; arriving
      after it, they are fooled.
- [ ] **Real disguise, EASY (1-2 stars).** Out of every line for 3 s → chat "The cops lost you. They want: ... 2 of head,
      chest, legs, boots, on foot / in a car must differ." once. Two pieces changed, then a cop sees you → "The cops
      don't recognise you.", no acquire, no cuff, stars drop on the evasion clock; `DISGUISE <you> state=mismatch`. Live:
      DIS-SWAP.
- [ ] **Too little, EASY.** Same with one piece → "The cops recognise you: ..." with the new look; the swap is wasted.
- [ ] **NORMAL, 3 stars.** 5 s and two pieces → a gun cop in view holds fire and walks past.
- [ ] **HARD, 4 stars.** 10 s and two pieces → recognised; 10 s and three pieces → fooled.
- [ ] **DEADLY, 5 stars.** 19.5 s and four pieces → recognised, new look learned; 20 s and three pieces → recognised; 20 s
      and four pieces → fooled, one "don't recognise" line. Live: DIS-DEADLY. On foot, see DIS-ESCAPE-3/4/5 below.
- [ ] **Fight back.** Disguised, hit a cop who can see you → recognised at once, fired on at the next tick, and the look you
      wear is the one they now want. A hit from out of every cop's sight changes nothing by itself.
- [ ] **Outfit in the window.** 2 stars, recognised, put the police outfit on behind a corner and be seen within 3 s →
      Recognised naming the outfit, blown. Put it on after the memory ends → the Outfit line, cops ignore you.
- [ ] **Blown means blown.** Blown, taken off in view, re-worn out of sight past the memory → still known.
- [ ] **Relog is no escape.** Quit and rejoin with a changed look at stars → no description, known at first sight.
- [ ] **New chase, same player.** A chase ends and a new one starts within the old memory → no recognition is inherited.
- [ ] **Stars rise.** Disguised with two pieces at 2 stars, reach 4 → the next sighting recognises you (line told).
- [ ] **Swap in front of the reporter.** 0 stars, kill a civilian 8 blocks from a beat cop, swap two pieces while the
      report is pending, stay in view → when it lands, "The cops recognise you" naming the new look (failed in 0.21.5).
- [ ] **Swap in front of the reporter, in a chase.** Disguised and unrecognised, a gated crime in view, a swap during the
      3 s → same.
- [ ] **A 911 call on an unseen swap.** As above with no cop in sight and a civilian calling → the crime's look stands, two
      pieces fool the first cop who comes. This one is earned.
- [ ] **Hit and duck.** Disguised, shoot a cop, be behind a wall within 0.5 s, wait past the memory, step out in the same
      look → recognised. A kill inside the 3 s report counts the same.
- [ ] **Shot from cover.** A projectile over a wall hits a cop with no line, a second cop 10 blocks away sees you →
      recognised.
- [ ] **Police outfit, 0 stars.** Shoot a beat cop while wearing the outfit → he fires back; the outfit stays blown until
      your next crime outside a chase clears it.
- [ ] **Squad lost, a beat cop nearby.** Beat cop at 30 blocks → the bar goes to SEARCHING and the Lost line comes. Beat cop
      at 10 blocks with a clear line → he reports, the bar stays on IN SIGHT, no Lost line.
- [ ] **Change in view.** A cop has an unbroken line, swap four pieces → still recognised, the new look radioed.
- [ ] **Glass, bars and fence.** Recognised, a hunting cop within 6 blocks behind a window or iron bars, swap two pieces,
      come out past the memory → recognised (the cop counts through it at that range); a fence changes nothing (the
      line clears it). Live: DIS-WINDOW, DIS-BARS, DIS-FENCE. A wall of stone within 6 blocks counts the same.
- [ ] **DIS-ESCAPE-3 / -4 / -5 (owner playtest, REVIEW).** A real squad at 3, 4 and 5 stars, on foot, then by car: can you
      get the memory on foot, and how? The numbers change in YAML only (`By_Difficulty`).
- [ ] **Police outfit.** Put on `police_helmet` + `police_vest` out of sight at 2 stars or fewer → chat "In the police
      outfit, the cops take you for one of their own."; cops ignore you; `state=outfit`. Put it on in view instead → the
      Recognised line with "police helmet, police vest", `state=blown`, blown for the chase. Taking a piece off and on again
      does not reset it.
- [ ] **Stars cleared** (sign, bribe, admin, decay) → no "Suspect is ..." line afterwards; at 0 stars beat cops warn for
      brandishing whatever you wear.
- [ ] **Post-escape search.** Escape, then on the search change 2 pieces → searchers and perimeter posts walk past, and
      there is no "Spotted" star.
- [ ] **A gated crime's look.** Steal a car bare-faced in front of a civilian, then pull on a pumpkin during the 8 s call →
      the cops get the bare-faced look. Commit it masked and take the mask off before the 3 s cop report lands → the cops
      get the masked look.
- [ ] **The first 911 call** of a chase names the real description (for example "Suspect: masked in a carved pumpkin,
      wearing red jacket, on foot"), never "unknown suspect"; nothing reads "Suspect is suspect".
- [ ] **Description text.** Four armour pieces + a Bartizan AK-47 → "wearing diamond helmet, red jacket, iron leggings, iron
      boots, armed with AK-47, on foot"; no armour → "in plain clothes"; a cobblestone in hand → no "armed" part; in a car →
      "in a car".
- [ ] **Spanish** (`Settings.Language: es`) → radio "Sospechoso: ..."; chat lines in Spanish; item words stay English (row
      852).
- [ ] **Config.** An old `Match_Needed` in `cops.yml` → console warning, ignored; `Changes_Needed: 1` → one changed piece
      shakes the cops.
      A `By_Difficulty` block with `Extra_Changes` and `Memory_Seconds` per Easy/Normal/Hard/Deadly → the pieces and the
      seconds follow your stars; a row or field left out keeps the shipped value; a `Memory_Seconds` below 1 is one config error and the shipped value applies.

---

## Reload Safety

- [ ] `/glw reload` while cops are pursuing → cops despawn cleanly, spawners re-register, wanted level preserved.
  Reference: `project_copmanager_reload_npe` — this was a past bug; `BeanPostInitialize` fixes it.
- [ ] New cops spawn after reload when wanted level is still high.

---

## Persistence

- [ ] Spawners persist across restart.
- [ ] Active cops (Citizens entities) do **not** persist via Citizens saves — they respawn from spawner rules.
- [ ] Verified on SQLite and MySQL.

---

## Regression Risks

- `CopManager` / `CopSpawnManager` — the reload NPE that triggered the `BeanPostInitialize` contract.
- `gangland-weapon` — cops fire pooled weapons; weapon changes must not break cop gunfire.
- Wanted-level feedback loop — clearing wanted despawns cops.

---

[Back to Test Index](../README.md)
