# Cops N Crooks

[← Changelog](../v0.7.5-DEV/CHANGELOG.md) | [Back to Index](../README.md) | [Next: Traders →](./traders.md)

---

## Overview

Cops N Crooks brings fully AI-driven police NPCs to your server. When a player accumulates a wanted level, police
officers spawn in the world, track the player down, and attempt to arrest them. The system is powered by the Citizens
plugin and requires it to function.

The number and strength of cops that respond scales with the player's wanted level — a low-level offender draws a couple
of rookie officers, while a five-star fugitive faces a military response.

> **Module status (0.7.5-DEV): feature-complete.** Cops N Crooks shipped across three releases:
> 0.7.3-DEV laid down the cop AI and wanted system, 0.7.4-DEV added civilians and the shared NPC base,
> and 0.7.5-DEV closes the loop with traders, a banker, and bail. See the **Related Systems** footer.

---

## How It Works

1. A player earns **wanted stars** through kills, crimes, or admin commands.
2. The system periodically checks for wanted players and spawns cops near them.
3. Cops pick up the player's trail, pursue them, and attempt a cuff-and-arrest.
4. If the arrest is successful, the player is jailed. If the player escapes or kills the cops, their wanted level decays
   over time.
5. **Dying clears your wanted level** — but so does evading the cops long enough.

---

## Cop Tiers

Cop strength is determined by the player's wanted level. Higher tiers have more health, deal more damage, move faster,
and carry better equipment.

| Tier | Name       | Health | Damage | Speed | Can Use Firearms | Skips Cuffing |
|------|------------|--------|--------|-------|------------------|---------------|
| 1    | Officer    | 20     | 2.0    | 1.0×  | No               | No            |
| 2    | Sergeant   | 25     | 3.0    | 1.1×  | No               | No            |
| 3    | Lieutenant | 30     | 4.0    | 1.2×  | Yes              | No            |
| 4    | SWAT       | 40     | 5.0    | 1.3×  | Yes              | Yes           |
| 5    | Military   | 60     | 7.0    | 1.4×  | Yes              | Yes           |

> **Skip Cuffing**: Tier 4 and 5 cops do not attempt to cuff the player first — they go straight to lethal engagement.

---

## Cop AI Behavior

Cops follow a state machine with three primary states. All cops hunting the same wanted player form one **squad** that
shares what its members see.

### Squad awareness

A cop knows where the player is only through a sighting — its own or any squad member's. A cop *sees* the player when
they are within `Alert_Range` (default 40 blocks) and in its line of sight. The crime scene counts as the first
sighting, and a player who hits a cop gives their position away to the whole squad.

- **Seen in the last 1.5 seconds:** the squad chases them. Melee cops surround them instead of queueing behind
  each other; armed cops spread across their tier's formation arc 7–12 blocks away and keep moving while they fire
  (see [Squad Tactics](#squad-tactics)).
- **Out of sight:** the squad goes to where they were last seen, then fans out and searches in widening circles until
  someone spots them again.

### Pursuit

The cop is closing in to cuff the player. When the direct path fails — stairs on the far side of a building, a closed
door, a long approach — the squad plans a route starting from the player's side (a rooftop is searched from the top
down), shares it between its members, opens doors and climbs ladders on the way. If no route exists at all, the cops
wait at the foot of the structure, facing the player, for as long as they are wanted.

A cop leaves a pursuit in only two cases, and the spawner then replaces it: it stayed stuck for `Pursuit.Max_Ticks`
AI ticks while no squad member could see the player, or the player got farther away than `Pursuit.Max_Distance`.

### Combat

Armed cops fire at the player whenever they can see them within `Alert_Range`, with proper reload cycles. Inside
their firing band they work a post on the squad's formation and side-step to a new spot every few seconds while
they keep shooting; out of view they climb after the player like any other cop. A shooter holds its shot while a
squad-mate stands in its line of fire.

Melee cops start a swing within `Combat_Range` (4 blocks), but a swing only lands within `Melee.Reach` (3 blocks). It
can miss (the tier's `Difficulty` sets the hit chance), does less damage the further out it lands, and varies by
`Melee.Damage_Spread`. When one cop is attacked, every cop in its group joins the fight.

A badly hurt cop (30% health or less by default) breaks off, radios it, and takes cover out of the player's sight for
up to 10 seconds before it comes back out and fights on. A cop with no cover nearby keeps fighting.

### Cuffing

Officers and sergeants cuff first, then fight. While one cop cuffs the player, the rest of the group holds posts
around them. Only one cop can cuff a player at a time. Every cuff the player breaks out of counts against the whole
group: after `Max_Cuff_Attempts` escapes (3 by default), or as soon as the player hits a cop, the whole group stops
cuffing, switches to combat and radios "Suspect is resisting!" The group stays hostile until the wanted level clears.

---

## Squad Tactics

Every tier fights as a squad. Shooters spread over a **formation arc** around the player, so they never stand
shoulder to shoulder on one side. Melee tiers always surround the player evenly, whatever the arc says.

| Tier       | Role   | Formation arc | Strafe | Reposition |
|------------|--------|---------------|--------|------------|
| Officer    | Melee  | surround      | 10°    | every 4 s  |
| Sergeant   | Melee  | surround      | 10°    | every 4 s  |
| Lieutenant | Ranged | 200°          | 10°    | every 4 s  |
| SWAT       | Ranged | 270°          | 15°    | every 3 s  |
| Military   | Ranged | 330°          | 20°    | every 2 s  |

A shooter that is walking aims a little worse (`Moving_Aim_Error`). Set `Tactics.Enabled: false` (for every tier, or
in one tier's own `Tactics` block) to restore 0.11's positioning only: shooters freeze in their firing band, with no
formation, no strafing and no order / contact / reload / check-fire radio lines. It is not a full rollback to 0.11:
casualty, backup and dispatch radio, retreat to cover and the cuff-then-fight escalation stay on. Switch those off
with `Radio.Enabled: false`, `Backup.Enabled: false`, `Retreat.Enabled: false` and `Radio.Responder_Max: 0`.

### Squad Roles (0.13.0)

Each new cop takes a **role** inside its squad, laid over its tier: the role decides how the cop is dressed and armed,
where it stands and how it fights, and it shows in the cop's name. Roles fill in the order of the wanted level's
`Squad_Composition`; cops past the end of the list (backup, extra cops) take its last role. Everything about the roles
lives in its own file, `copsncrooks/cop_roles.yml`, written on first start when it is missing (an older `cops.yml` is never
touched). Delete the file and the same built-in roles apply.

| Role         | From level | Looks like                                    | Gun by tier (3 / 4 / 5)        | Does                                                                                   |
|--------------|------------|-----------------------------------------------|--------------------------------|----------------------------------------------------------------------------------------|
| ➤ Pointman   | 1          | Blue leather cap                              | rifle / steyr_aug / steyr_aug  | Front and centre, close in; the squad's radio voice while no Commander is on it        |
| ⚔ Assault    | 1          | Black balaclava, chainmail                    | mp5 / steyr_aug / golden_ak47  | The fan's ends, pushing in and strafing wide                                           |
| ★ Commander  | 3          | Glinting gold helmet                          | revolver / revolver / golden_ak47 | Radio voice from the rear of the band; when it goes down: "Commander down!", fall back |
| ⛨ Defender   | 3          | Shield, the squad's heaviest armour           | shotgun / shotgun / sawn_off or shotgun | Holds the centre post up front; its shield takes half of every hit from its front |
| ⌖ Marksman   | 3          | Green ghillie hood and leggings               | scout / scout / awp            | Stands 22-32 blocks out, far behind the rest; slower but surer shots                   |
| ✚ Medic      | 4          | Red cap, white leather, a golden apple        | revolver / mp5 / mp5 or steyr_aug | Walks over to hurt squad mates and patches them up (see Field Care below)          |

Every role keeps its identity piece on every tier, while its armour and gun grow with the tier: Officer and Sergeant
light (leather/chain), Lieutenant chain/iron, SWAT iron/diamond, Military diamond/netherite. The Medic always carries
the weakest gun of its squad, but never a weak one at Military. Tiers 1 and 2 are melee (`Can_Use_Weapons: false`):
their cops keep the tier's melee weapon whatever the role's `Weapon_Pool` says.

The Marksman's band is clamped under its gun's reach (`Projectile.Distance`), so it only stands far with a long gun:
the scout (100) and the awp (120) leave its 22-32 band whole, the M4 rifle (10) would pull it in to 8-10.

The default callsign `Format` is `"%rank% %role% &f%name% &7#%badge%"`: `Officer ✚ Medic Bob #1592` above the head,
with the role's colour and symbol from its `Display` block. `%rank%` stays the tier's `Display_Name`; for a cop with
no role (`Roles_Enabled: false`) `%role%` disappears with its space. The radio uses the same text without colours.
Remove `%role%` from `Cops.Names.Format` to hide the role. Set `Roles_Enabled: false` in `cop_roles.yml` to spawn
every cop as its plain tier again.

The Defender's front cone is judged from whoever dealt the damage: the shooter for a bullet or arrow, the attacker for
anything else. Area damage (a grenade, fire) is credited to its thrower, so a blast behind a Defender is still halved
while the thrower stands in front of it. The Commander fall-back is a retreat: `Cops.Retreat.Enabled: false` turns it
off, and each cop that falls back may radio the Fall_Back line right after "Commander down!".

### Field Care (0.13.0)

A cop at or below `Field_Care.Health_Fraction` (half) of its max health is **hurt**: it limps (`Limp_Speed`, 0.7 of its
tier speed), bleeds (redstone-block blood at random body spots, denser the more hurt, with a heavier burst on every hit taken) and radios "I'm hit!" once. A **Medic** cop of the same squad within
`Medic_Radius` (36 blocks, enough to reach a Marksman at its far post) walks straight over to it (a direct route, not from cover to cover), and the squad calls
"Covering fire!". Once the medic is within `Heal_Range` (2.5 blocks) both hold still: the patient crouches and both keep
shooting. A patient on its way to cover is treated once it gets there, never held in the open; the time it spends
walking there does not count toward the 15 s below. After `Channel_Ticks` (3 s) the patient gets `Heal_Fraction` (half)
of its max health back, with heart particles and "Patched up". A hit on the medic starts the 3 s over ("Pinned down"). A
squad without a Medic cop never heals; a medic that cannot reach its patient within 15 s gives up. If another plugin
cancels the heal, the treatment ends quietly and that cop is not treated again for 15 s.

| Key (`Cops.Field_Care`) | Default | Does                                                     |
|-------------------------|---------|----------------------------------------------------------|
| `Enabled`               | true    | false: no limp, bleeding or treatment                    |
| `Health_Fraction`       | 0.5     | Hurt at or below this share of max health (0-1)          |
| `Limp_Speed`            | 0.7     | A hurt cop's speed, as a share of its tier speed (0.1-1) |
| `Medic_Enabled`         | true    | false: cops still get hurt, but nobody treats them       |
| `Medic_Radius`          | 36.0    | Blocks a medic answers a hurt squad mate within (2-64)   |
| `Heal_Range`            | 2.5     | Blocks from the patient the medic treats it from (1-6)   |
| `Channel_Ticks`         | 60      | Ticks one treatment takes                                |
| `Heal_Fraction`         | 0.5     | Share of max health one treatment restores (0-1)         |
| `Hurt.Bleed_Particle`   | BLOCK_CRACK | Blood particle; drawn with redstone-block data (`BLOCK` on 1.20.5+), falls back to red dust |
| `Hurt.Bleed_Count`      | 6       | Particles per spot (1-50), up to double at death's door  |
| `Hurt.Bleed_Spots`      | all six | HEAD, CHEST, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG; a burst picks 1-2 |

Every key is optional: a `cops.yml` without the block gets these defaults.

"I'm hit!" and "Patched up" are `Radio.Priority` lines, so other squad chatter never swallows them. A `cops.yml` from
before 0.13.0 keeps its own `Radio.Priority` list: add `"Hit"` and `"Patched_Up"` to it.

---

## Police Radio

Cops talk on the radio, and the lines show up in chat. Every player within `Radio.Range` (32 blocks) of the speaking
cop hears them, bystanders included. The hunted player hears their own pursuers from `Radio.Target_Range` (64
blocks). Each line plays a short click sound.

- **Contacts:** a cop that spots the player calls out the distance and direction, and the squad calls it when it
  loses sight of them.
- **Orders and acknowledgements:** the squad leader orders members to push in or take a flank, and the ordered cop
  answers ("Copy.") a moment later. Shooters also call out repositioning, reloading and check-fire.
- **Casualties:** "Officer down!" when a cop dies, or a leader change when the leader dies.
- **Dispatch:** a new wanted level, a tier escalation, and the stand-down when the player is cleared.
- **Responders:** a Contact, Officer Down or Backup call pulls in up to `Radio.Responder_Max` (2) nearby cops within
  `Radio.Range`. Only cops that are idle, walking home or chasing a civilian answer, including cops from another
  wanted player's group. A cop that is cuffing, guarding or hunting a player of its own never leaves it. This is the
  cops hearing each other, so it works even when no player is near.
- **Backup:** when a cop goes down, the squad requests `Backup.Extra_Cops` (1) extra cops for
  `Backup.Duration_Ticks` (30 s), at most once per `Backup.Cooldown_Ticks` (60 s). When the backup runs out, the
  surplus cops that aren't fighting walk home.
- **Regroup (0.15.0):** "Two down! Pull back to cover, backup is coming!" when the squad falls back, and "Backup's
  here! All units, push together!" when it pushes. See Regroup below.
- **Shots fired (0.15.0):** the nearest cop of the hunted player's squad says where it heard a shot. See Shot Noise below.
- **Dispatch (0.16.0):** "3 units en route from Central, ETA 12 s." when units leave a station, "Squad down. Backup inbound
  in 13 s." after a wipe, "Lost him heading north-east. Units ahead, pick him up." on a hand-off, "Holding the corner." and
  "Eyes on suspect near the Docks, moving north-east." from posted cops, and "Units returning to patrol." when the trail
  goes cold. Every line may carry `%place%`, the district name at the spot, or "the area" (`Unknown_Place`).
- **Resisting**, **retreat** and **field care** lines, as described above.

Lines are throttled per squad and per player, so chat never floods. Every line is in `copsncrooks/cop_radio_messages.yml`
(Spanish: `_es.yml`). Each key is a list that one entry is picked from at random, and `[]` silences that line.
`Radio.Enabled: false` silences the radio for players but keeps responders and backup working.

---

## Losing the Cops and the Chase (0.15.0 and 0.15.2)

The chase around a wanted player is configured in `copsncrooks/wanted.yml` (heat, evasion, HUD, charge sheet) and
`copsncrooks/wanted_messages.yml`; see [Wanted & Bounty](./wanted-bounty.md) for the player-facing rules.

- **Evasion reads the squad's sightings.** Every cop in the group shares what it sees. The evasion clock counts "no
  cop has seen you" from the squad's last sighting, and a cop that is walking home (`RETURNING`) does not count as
  pursuit. When no live hunting cop is left the evasion state turns off and the fixed decay timer takes over.
- **Evasion end decisions (0.15.2+).** With `Drop_Mode: ONE_STAR` (the default), every evasion drops one star. With `ALL_STARS`, every evasion drops all. With `AUTO`, the cops judge the chase and decide: a rampage drops one star at a time, a small chase or a long quiet one drops all stars, leaving the zone drops half your stars. The server can learn typical chase lengths and escape habits to tune timers and identify repeat offenders. Full rules in [Wanted & Bounty](./wanted-bounty.md).
- **Regroup.** When `Regroup.Casualties` (2) cops of one squad die within `Regroup.Window_Seconds` (20), the whole squad
  falls back to cover for at most `Regroup.Fall_Back_Seconds` (15), radios for backup (the regroup grants it itself
  when none is active), and pushes together once all of it, backup included, is within `Regroup.Arrival_Radius` (24)
  blocks of the suspect, or when `Fall_Back_Seconds` runs out. With `Backup.Enabled: false` it falls back and pushes
  without the two radio lines. One regroup per `Regroup.Cooldown_Seconds` (60) and squad; a squad
  that cuffs first never regroups. `Regroup.Enabled: false` keeps the 0.13.0 behaviour. A Commander call for a fall-back
  during a regroup keeps the longer of the two.
- **Shot noise.** A Bartizan weapon fired by a wanted player inside `Shot_Noise.Radius` of a cop of his squad (GUN 48,
  THROWABLE 16, MELEE 0 = silent; a type that is not listed is silent) reports the shooter's position to the squad.
  The squad counts it as a sighting, so evasion resets, and the nearest cop calls "Shots fired!" (throttled to once
  per `Cooldown_Ticks.Shots_Fired`, 3 s, per shooter). The line respects the radio gaps like any line outside
  `Radio.Priority`, so it can be dropped right after another squad line; the sighting always lands. Players who are not
  wanted, and NPC shooters, are ignored.
- **Wanted HUD.** The boss bar, star card, title, siren, zone ring and compass are shown to the hunted player only and
  disappear when the chase ends.

---

## Where the Police Come From (0.16.0)

Until 0.15 a cop popped into existence near the suspect. From 0.16.0 the police have **stations**: units leave the
nearest station, take time to arrive, and the squad plays out like a response, not a spawn. Everything here has its own
`Enabled` switch in `copsncrooks/cops.yml` (`Cops.Dispatch`, `Breather`, `Handoff`, `Perimeter`) and `wanted.yml`; switching
one off restores the 0.15 behaviour for that part.

### Stations, dispatch and ETA

An admin places a **station** with the setup wand (below). Cop spawners within `Dispatch.Station_Radius` (32 blocks)
of a station belong to it; `/glw cop spawner set` assigns a new spawner to a station that close, and saving a station
takes the spawners already standing near it.

When a player turns wanted, every missing cop becomes a **pending unit** that leaves the nearest station of the
player's world:

- The **ETA** is the horizontal distance from the station to the suspect divided by `Dispatch.Unit_Speed` (10
  blocks per second), clamped to `Min_Eta_Seconds` (0) .. `Max_Eta_Seconds` (40) and rounded up to whole seconds.
- The squad radios it: "3 units en route from Central, ETA 12 s." (`Dispatch_En_Route`, with the place of the crime in
  the `Dispatch_Wanted` line).
- A unit appears out of sight: first at a spawner of its station, otherwise on a hidden ring around the suspect,
  otherwise (every spot is visible) the old spawn rules. "Out of sight" means no player who matters has a clear line
  of sight to the spot (a block ray, not a facing cone); the hunted player counts at any distance.
- A world with no station keeps the old ring spawn with no delay (ETA 0).
- **The chase clock waits for the cops.** While units are on the road and none stands in the world, the evasion clock holds:
  no search opens and no star can drop. A unit that fails to spawn for 10 s past its ETA stops counting.
- A **logout** drops every unit on its way. After the **rejoin** the first units are sent only once
  `Rejoin_Grace_Seconds` (15) have passed, and still need their ETA; the cops are not dropped on the spot where he
  reappeared, and the chase clock holds until a unit arrives.

### Mixed squads

`cop_roles.yml` `Squad_Composition` entries may carry a tier: `"Marksman@3"` is a Marksman of tier 3; `"Marksman"` keeps
the star's tier. The bundled file mixes tiers from three stars up (1-2 stars have no `@`):

| Stars | Squad |
|---|---|
| 3 | Commander@3, Pointman@2, Defender@3, Marksman@3, Assault@2 |
| 4 | Commander@4, Pointman@3, Defender@4, Marksman@4, Medic@3, Assault@3 |
| 5 | Commander@5, Pointman@4, Defender@5, Marksman@5, Medic@4, Assault@4 |

Five stars used to fall back to the four-star squad; it now has its own entry. Remove the `@suffixes` from a line to
get one tier for the whole squad again. An unknown or invalid tier is reported at startup and counts as "the star's tier".

### The breather

When a squad is **wiped out** (it loses every cop within `Breather.Wipe_Window_Seconds`, 10 s) the replacement does not
arrive at once: the player gets a breather of `Breather.Seconds` for his star level (15, 13, 10, 8 and 6 seconds for
1 to 5 stars) plus the units' ETA, and the radio says "Squad down. Backup inbound in N s." (`Wipe_Refill`). A wipe is
counted once; a new casualty after the breather starts a new one.

### The perimeter

When a suspect of at least `Perimeter.Min_Level` (3) stars is out of sight and a search opens, up to `Perimeter.Posts` (2)
cops (Marksmen and Defenders first; the last free cop is never posted) walk to **posts** on a ring around the search
zone, hold within `Leash_Radius` (4 blocks) and watch him. The ring radius is the smaller of the zone radius and 0.8 x
`Sight_Range` (40), so the posts can see the middle of the zone, and each post keeps `Lane_Length` (16) blocks of clear
lane toward the centre. The perimeter ends when a post sights him ("Eyes on suspect near the docks, moving north-east."),
when he is seen by anyone (the cops pursue again), when the search ends, or after `Max_Seconds` (60). It posts once per
search; a new sighting re-arms it. A posted cop that the suspect attacks drops its post and fights.

### The hand-off

When the suspect outruns the cops that chased him (the pursuers give up and walk home) the squad leader radios his
heading ("Lost him heading north-east. Units ahead, pick him up.") and the **next units appear ahead of him**: for
`Handoff.Bias_Seconds` (10) new units spawn within `Cone_Degrees` (60) of the heading and report his last position,
not his live one. The heading is read over the last `Heading_Seconds` (2) of his movement.

### Hideouts and the cold trail

Two things change how fast the "nobody has seen you" clock runs (`wanted.yml` `Wanted.Evasion`, capped together with
the zone speed by `Max_Speed`, 4.0):

- **Hideout** (`Hideout.Speed`, 2.0): searching **inside a hideout** that is not the one he was last seen in. A hideout is
  a turf owned by a gang (open to that gang's members only), a gang waypoint (radius 8 blocks when the waypoint's own
  radius is 0, at most 64) or an admin-placed `hideout` region (open to everyone). A rival gang's hideout does not count.
- **Cold trail** (`Quiet_Speed`): the longer he stays quiet (no crime, no new star, no cop sighting, offline time
  never counts) the faster the clock runs, `Per_Minute` (0.25) for every full quiet minute up to `Max` (2.0). After
  `Backup_Skip_Seconds` (60) of quiet the next backup wave is skipped and the squad radios "Units returning to patrol."

### Bribe stars

An admin can place **pickup points** (wand mode `pickup`). Each holds a floating `NETHER_STAR` (`Wanted.Bribe_Stars.Item`).
A wanted player who stands within `Pickup_Radius` (1.5 blocks) while **no cop has seen him recently** takes it: he
loses `Stars` (1) and the item returns `Respawn_Seconds` (300) later. With a cop watching he gets "Not with a cop
watching." and the star stays. Taking one counts as a crooked contact (cause `CONTACT`); see
[Wanted & Bounty](./wanted-bounty.md) for the phone and the sign, which follow the same unseen rule.

### The setup wand

`/glw cop setup wand` gives admins the **setup wand** (`Setup.Wand.Item`, `BLAZE_ROD` unless changed in
`copsncrooks/setup.yml`): left click sets pos1, right click pos2, and a particle outline of the selection is shown to
the admin holding it. `mode` chooses what `save <name>` stores:

| Mode | Stores | Uses |
|---|---|---|
| `station` | a police station at pos1 (and assigns the cop spawners within `Station_Radius`) | pos1 |
| `district` | a named district: the place name the radio speaks ("the Docks") | pos1 + pos2 cuboid |
| `hideout` | a hideout region, open to everyone | cuboid |
| `restricted` | a restricted-area region (tag `restricted`; stored for later releases, no behaviour in 0.16) | cuboid |
| `breaker` | a breaker structure region plus a trigger point where you stand (stored for later releases, no behaviour in 0.16) | cuboid |
| `pickup` | a bribe-star pickup point | pos1 |

Regions are cuboids bounded in height as well as width, so pick pos2 at the right height. A station name must be
unique (a duplicate saves nothing). The commands are listed under Commands below; the wand and every sub-command need the
`gangland.command.cop.setup` permission.

### Districts and place names

A district is a named region (`district` tag). The radio uses it everywhere: "wanted in the Docks", "Lost visual near the
Docks", "Eyes on suspect near the Docks". Any module can publish places: a named turf, an admin region or a gang hideout waypoint all
answer to `PlaceNames` (see [the api guide](../gangland-api.md)). The smallest region at a spot wins. With no named
region the radio says "the area" (`Unknown_Place`).

---

## Spawner System

Cops spawn from **spawner locations** you place in the world. When the system needs to spawn cops for a wanted player,
it looks for the nearest spawner within 80 blocks and spawns from there.

If no configured spawner is nearby, it falls back through a series of phases:

1. **Phase 1** — Attempts to find a valid location in a ring approximately 30 blocks behind the player.
2. **Phase 2** — Shrinks the search radius progressively until a valid spot is found.
3. **Global fallback** — Spawns at any valid location if all else fails.

Each spawner candidate is validated: the location must have at least two open sides and solid ground beneath it, and
must not be indoors in a way that would trap the NPC.

Since 0.16.0 spawners also **belong to stations** (`Cops.Dispatch.Station_Radius`): a station's units spawn at its own
spawners first, out of sight of the suspect. See "Where the Police Come From" above. A spawner or jail you placed in a world
that was not loaded yet when Gangland started keeps its id (older versions could hand that id to a new row and overwrite
the unloaded world's row).

---

## Commands

All commands require appropriate permissions.

### Spawner Management

| Command                          | Description                                                 |
|----------------------------------|-------------------------------------------------------------|
| `/glw cop spawner set`           | Places a cop spawner at your current location.              |
| `/glw cop spawner remove <id>`   | Removes the spawner with the given ID.                      |
| `/glw cop spawner list`          | Lists all configured spawners with their IDs and locations. |
| `/glw cop spawner info <id>`     | Shows details about a specific spawner.                     |
| `/glw cop spawner teleport <id>` | Teleports you to a spawner's location.                      |

### Setup (0.16.0)

Needs `gangland.command.cop.setup`. Kinds for `list`, `remove` and `tp` are `station`, `region` and `point`.

| Command                                    | Description                                                                              |
|--------------------------------------------|------------------------------------------------------------------------------------------|
| `/glw cop setup wand`                      | Gives you the setup wand: left click = pos1, right click = pos2.                         |
| `/glw cop setup mode <mode>`               | `station`, `district`, `hideout`, `pickup`, `restricted` or `breaker`: what `save` stores. |
| `/glw cop setup save <name...>`            | Stores the selection under a name according to the mode.                                 |
| `/glw cop setup list [kind]`               | One line per row: `kind id name world x y z [tags]`.                                     |
| `/glw cop setup remove <kind> <id>`        | Removes a row; removing a station frees its spawners.                                    |
| `/glw cop setup tp <kind> <id>`            | Teleports you to a station, a point, or the centre of a region.                          |
| `/glw cop setup link <stationId> <jailId\|none>` | Sets the jail a station books arrests into, or `none`.                             |

### Active Cops

| Command         | Description                          |
|-----------------|--------------------------------------|
| `/glw cop list` | Lists all currently active cop NPCs. |

---

## Configuration

Cop behavior lives in `cops.yml`: tier definitions, AI tuning and, since 0.15.1, the cop count scaling, behaviour,
spawn, pursuit, return and navigation knobs that used to be in `settings.yml` (same `Cops.*` paths; navigation is
`Cops.Navigation`, a copy of `settings.yml` `NPC_Navigation`, and the guarding radius is `Cops.Behaviour.Guard_Radius`,
formerly `Detainment.Transit.Guard_Radius`). The jail, bail, bribe and sentence knobs moved to `detainment.yml` and
`Wanted.Kill_Combo` to `wanted.yml`. For one release a value still tuned in `settings.yml` is used, with a console
warning naming the new file, while the module file holds the shipped default; copy it over and delete it from
`settings.yml`.

The module's own files (`cops.yml`, `cop_roles.yml`, `cop_radio_messages(_es).yml`, `wanted.yml`,
`wanted_messages.yml`, `detainment.yml`) live in `plugins/Gangland_Warfare/copsncrooks/`. Older versions kept them in `npc/`; on the
first boot after the update each one is moved from `npc/` to `copsncrooks/` with its values intact. If a file exists in
both folders, the `copsncrooks/` one is used and the `npc/` copy is left alone with a console warning.

The 0.16.0 blocks (`Cops.Dispatch`, `Breather`, `Handoff`, `Perimeter` in `cops.yml`; `Wanted.Evasion.Hideout`,
`Quiet_Speed`, `Max_Speed` and `Wanted.Bribe_Stars` in `wanted.yml`; `setup.yml`) are listed with every default in the
[Configuration Reference](../developer/configuration.md). A server that keeps its old files reads the shipped defaults for
every key it lacks; see the [0.16.0 migration guide](../migration-0.16.0.md).

---

### Tier Configuration (`cops.yml`)

Tiers are numbered `1` through `5` under `Cops.Tiers`. Each tier defines the stats and equipment for that cop rank.

```yaml
Cops:
   Tiers:
      1:
         Display_Name: "&9Officer"   # The %rank% in Cops.Names.Format (supports & color codes)
         Health: 20.0                # Max health points
         Damage: 2.0                 # Melee damage per attack
         Speed: 1.0                  # Movement speed multiplier (1.0 = normal player speed)
         Cuff_Radius: 3.0            # Blocks from target at which this tier can attempt a cuff
         Can_Use_Weapons: false      # Whether this tier fires Gangland ranged weapons
         Skip_Cuffing: false         # If true, skips cuffing entirely and goes straight to lethal combat
         Difficulty: EASY            # EASY / NORMAL / HARD / DEADLY: aim error, reaction time, fire rate, melee hit chance
         Fire_Rate_Multiplier: 0.1   # Gun cadence as a fraction of the weapon's own fire rate (0.1 = the 0.11 cadence)
         Tactics:                    # Overrides Cops.Tactics key by key for this tier
            Formation_Arc: 200.0
         Weapon_Pool: # Items the cop can carry. One is selected randomly on spawn.
            - "WOODEN_SWORD"          # Vanilla Bukkit material name
            - "weapon:rifle"          # Custom Gangland weapon — prefix with "weapon:" then the weapon name
         Helmet: ""                  # Vanilla armor material for the helmet slot (empty = none)
         Chestplate: ""              # Vanilla armor material for the chestplate slot
         Leggings: ""                # Vanilla armor material for the leggings slot
         Boots: ""                   # Vanilla armor material for the boots slot
```

`Fire_Rate_Multiplier` defaults to `1 / Cops.Behaviour.AI_Tick_Rate`, which keeps the 0.11 cadence. A vanilla
`BOW`/`CROSSBOW` counts from a 15-tick base, so at `0.1` it fires every 150 ticks before `Difficulty`. See
[Migrating to 0.12.0](../migration-0.12.0.md), section 5.

`Weapon_Pool` accepts two formats:

- Plain vanilla material (e.g., `IRON_SWORD`, `CROSSBOW`) — gives the NPC that vanilla item.
- `weapon:<name>` (e.g., `weapon:rifle`) — gives the NPC a configured Gangland weapon from the `weapon/` folder.

---

### Tactics, Melee, Radio, Backup, Retreat and Stuck (`cops.yml`)

```yaml
Cops:
   Melee:
      Reach: 3.0                   # A swing lands only within this many blocks
      Approach: 2.0                # Melee cops surround here (capped at Cuff_Radius - 0.5); full damage inside it
      Damage_Spread: 0.15          # Damage varies by up to +/-15%
      Edge_Damage: 0.7             # Fraction of full damage at the edge of Reach
   Tactics:                        # Defaults for every tier; a tier's own Tactics block overrides key by key
      Enabled: true                # false = 0.11 positioning only (freeze in band); radio/backup/retreat stay on
      Formation_Arc: 270.0         # Degrees the shooters spread over (melee tiers always surround)
      Strafe_Degrees: 15.0         # Side-step per reposition
      Reposition_Ticks: 60         # Server ticks between repositions (+/-25%)
      Moving_Aim_Error: 0.10       # Extra aim error while walking
   Radio:
      Enabled: true                # false silences chat; responders and backup still work
      Range: 32.0                  # Who hears a line, and how far other cops hear a call
      Target_Range: 64.0           # How far the hunted player hears their pursuers
      Squad_Gap_Ticks: 30          # Gap between two lines of one squad (priority lines and acks skip it)
      Player_Gap_Ticks: 20         # Gap between two low-priority lines reaching one player
      Ack_Delay_Ticks: 25          # Delay before an ordered cop answers
      Responder_Max: 2             # Nearby cops pulled in per call (0 = none)
      # Priority (line kinds that skip the gaps), Cooldown_Ticks (per-kind repeat cooldown; 0.15.0 adds Regroup 1200,
      # Regroup_Push 1200 and Shots_Fired 60) and Sound: see the file
   Backup:
      Enabled: true
      Extra_Cops: 1                # On top of the wanted-level count, capped by Max_Per_Player
      Duration_Ticks: 600          # 30 s; afterwards the surplus cops that aren't fighting walk home
      Cooldown_Ticks: 1200         # 60 s between requests from one group
   Regroup:                        # 0.15.0; every key falls back to the value shown
      Enabled: true
      Casualties: 2                # Cops lost inside Window_Seconds that trigger a pull-back
      Window_Seconds: 20
      Fall_Back_Seconds: 15        # Longest stay in cover before the squad pushes anyway
      Cooldown_Seconds: 60         # One regroup per squad per this long
      Arrival_Radius: 24.0         # Push once the whole squad, backup included, is this close
   Shot_Noise:                     # 0.15.0
      Enabled: true
      Radius:                      # Blocks a cop hears a shot, by weapon type; 0 or unlisted = silent
         GUN: 48
         THROWABLE: 16
         MELEE: 0
   Retreat:
      Enabled: true
      Health_Fraction: 0.3         # Retreat at or below 30% health
      Radius: 12.0                 # Blocks searched for cover the player can't see
   Stuck:                          # Optional block (0.13.0); every key falls back to the value shown
      Enabled: true                # false = a cop that finds no way to the player stays where it is
      Recycle_Seconds: 12          # A cop stranded this long, out of the player's sight, is replaced
      Avoid_Spawner_Seconds: 60    # Its spawner is skipped for replacements this long (0 = never)
```

A stranded cop is not replaced while the player is looking at it (in front of him, clear line, within
`Cops.Spawn.Visibility_Check_Distance`, never less than 24 blocks), while another player faces it, or while it is within
melee reach on his level with nothing in between. Once it has been stranded for twice `Recycle_Seconds`, a view only
keeps it within 24 blocks, the player's own and every other player's alike. With its spawner skipped, the replacement comes from the next spawner within `Spawner_Max_Y_Diff`, or from
the ring around the player at his level (for a player indoors, the street outside counts too). The wanted-level cop
count and `Max_Per_Player` still cap the total. Dispatch also tells the replacements where the player is now, so a
player who slipped away unseen from a spot the stranded cops could not reach is not hunted there again.

---

### Roles and Squad_Composition (`copsncrooks/cop_roles.yml`, 0.13.0)

Every key is optional; an entry under `Roles` is read key by key over the built-in role of the same name (a new name
starts from a plain role). The shipped file lists the whole catalogue with a comment per key. A bad value (an unknown
material, a colour that is not `#RRGGBB` or `R, G, B`, a number out of range) is reported at startup and the built-in
value kept.

```yaml
Roles_Enabled: true
Roles:
   Medic:
      Display:
         Name: "Medic"               # the word, also used by radio lines
         Color: "&c"
         Symbol: "✚"                 # "" = none
      Fan_Placement: CENTER          # ANY, CENTER or FLANK
      Ranged_Min_Distance: 8.0       # the role's own firing band, clamped under the gun's Projectile Distance
      Ranged_Max_Distance: 12.0
      Health_Multiplier: 1.0
      Leader_Priority: 0             # the highest live priority speaks for the squad
      Strafe_Degrees: 15.0           # replaces the tier's Tactics.Strafe_Degrees
      Fire_Rate_Scale: 1.0           # multiplies the tier's Fire_Rate_Multiplier
      Difficulty_Bonus: 0            # steps above the tier's Difficulty
      Block_Fraction: 0.0            # damage taken off a hit from inside the front cone (the Defender: 0.5)
      Block_Cone_Degrees: 60.0
      Medic: true                    # treats hurt squad mates
      Commander: false               # its death: Commander_Down, the squad falls back for 5 s
      Retreat:
         Health_Fraction: 0.5        # read over cops.yml Cops.Retreat
      Weapon_Pool:                   # weapon:<Bartizan weapon>; other entries are vanilla items held without Bartizan
         - "weapon:pistol"
      Gear:                          # Helmet, Chestplate, Leggings, Boots, Off_Hand
         Helmet:
            Material: LEATHER_HELMET
            Leather_Color: "#B02E26" # or "176, 46, 38"
            Glow: false
         Off_Hand: GOLDEN_APPLE      # a plain material name works too; "" empties the slot
      Tiers:                         # by level number or the tier's Display_Name without colours (Military)
         5:
            Weapon_Pool:
               - "weapon:mp5"
               - "weapon:steyr_aug"
            Gear:
               Chestplate: NETHERITE_CHESTPLATE
Squad_Composition:
   3:
      - "Commander"
      - "Pointman"
      - "Defender"
      - "Marksman"
      - "Assault"
```

Which gear a cop wears, slot by slot, and which weapon pool it draws from:

1. the role's entry for the cop's tier (`Roles.<Name>.Tiers.<tier>`),
2. else the role's own `Gear` / `Weapon_Pool`,
3. else the tier's own `Wearables` / `Weapon_Pool` from `cops.yml`.

On a melee tier step 2's `Weapon_Pool` is skipped (the cop keeps its melee weapon). A role pool with no vanilla item
keeps the tier's vanilla items, which the cop holds when no Bartizan weapon resolves (Bartizan missing, unknown name).

### AI Settings (`cops.yml` → `Cops.Behaviour`)

```yaml
Cops:
   Behaviour:
      Max_Per_Player: 8             # Hard cap on active cop NPCs per wanted player at any time
      AI_Tick_Rate: 10              # Ticks between each AI decision cycle. Lower = faster reactions, more CPU.
      Spawn_Check_Rate: 40          # Ticks between checks that decide whether to spawn more cops
      Cuff_Radius: 3.0              # Default cuff radius in blocks (individual tiers override this)
      Max_Cuff_Attempts: 3          # Cuffs the player may break out of (across the group) before the group fights
      Cuff_Cooldown_Ticks: 100      # Ticks between consecutive cuffing attempts
      Alert_Range: 40.0             # Sight range: a cop sees a wanted player this close with line of sight; shared by the squad
      Combat_Range: 4.0             # Distance at which a melee cop starts a swing (ranged cops fire within Alert_Range)
      Attack_Cooldown_Ticks: 20     # Server ticks between melee swings
      Guard_Radius: 5.0             # Blocks the guarding cop stays within the cuffed player (was Detainment.Transit)
```

---

### Spawn Settings (`cops.yml` → `Cops.Spawn`)

```yaml
Cops:
   Spawn:
      Min_Distance: 10.0            # Minimum spawn distance from the player (blocks)
      Max_Distance: 50.0            # Maximum spawn distance from the player (blocks)
      Phase1_Min_Distance: 30.0     # Target ring radius for Phase 1 (preferred, behind-player) spawn attempts
      Radius_Shrink_Step: 5.0       # How much the ring radius shrinks per Phase 2 iteration
      Vertical_Search_Range: 10     # Blocks searched above and below the player's Y to find valid ground
      Y_Offset: 0                   # Vertical offset from the player's Y when searching (0 = same level)
      Min_Open_Sides: 2             # Minimum open horizontal sides required at a spawn position
      Spawner_Preference_Radius: 80.0  # Blocks within which a placed cop spawner is preferred over a random position
      Visibility_Check_Distance: 48.0  # Distance within which nearby players trigger despawn visibility checks
      Phase1_Attempts: 20           # Number of spawn attempts in Phase 1 (preferred ring)
      Phase2_Attempts: 15           # Number of attempts per shrink step in Phase 2
```

---

### Navigation Settings (`cops.yml` → `Cops.Navigation`)

```yaml
Cops:
   Navigation:
      Stuck_Check_Interval: 5       # AI ticks between movement-progress samples for stuck detection
      Max_Stuck_Checks: 3           # Consecutive stuck samples before the cop retries pathfinding
      Max_Hopeless_Stuck_Checks: 6  # Consecutive stuck samples before navigation is considered permanently failed
      Hopeless_Close_Threshold: 8.0 # If the target is within this many blocks, a hopeless cop still tries to navigate directly
      Min_Progress_Distance: 0.75   # Minimum blocks moved between samples to count as progress (not stuck)
      Ranged_Min_Distance: 7.0      # Ranged cops hold their firing position when target is closer than this
      Ranged_Max_Distance: 12.0     # Ranged cops hold position when target is farther than this
```

`Recalculation_Ticks` (10) and `Min_Repath_After_Loss_Ticks` (2) live in the same `Cops.Navigation` block; the old `settings.yml` `NPC_Navigation` values are honoured for one release.

---

### Pursuit Settings (`cops.yml` → `Cops.Pursuit`)

```yaml
Cops:
   Pursuit:
      Max_Distance: 80.0            # A pursuing cop farther than this from the player (blocks) is rotated out and replaced
      Max_Ticks: 120                # AI ticks a cop may stay stuck while no squad member sees the player (120 ≈ 60 s)
```

---

### Return Settings (`cops.yml` → `Cops.Return`)

```yaml
Cops:
   Return:
      Max_Ticks: 600                # AI ticks before a cop with no target is force-despawned (600 ≈ 30 s)
      Station_Arrival_Distance: 3.0 # Blocks from the spawn station at which the cop considers itself arrived and despawns
```

---

### Cop Count Scaling (`cops.yml` → `Cops.Count`)

```yaml
Cops:
   Count:
      Formula_Enabled: false        # If true, evaluates the Formula string instead of the linear calculation
      Formula: "base + (level - 1) * perLevel"
      # Custom expression when Formula_Enabled is true.
      # Available variables: level, base, perLevel, max
      Base: 2                       # Cops spawned at 1 wanted star (also the 'base' variable in the formula)
      Per_Level: 1                  # Additional cops per additional wanted star (also 'perLevel' in the formula)
      Max: 8                        # Hard cap — result is always clamped to this value

```

---

## Known Limits (0.16.0)

These are accepted for 0.16 and are the next cards, not bugs to report:

- **Dying resets all stars.** A death clears the wanted level, which also ends a chase the cops were winning.
- **Hospital camping.** After the 5 second hospital respawn shield a player can wait at his hospital; nothing keeps cops
  there yet.
- **Vehicles and pearls outrun posts.** Perimeter posts are on foot and fixed; a car, a boat or an ender pearl crosses the
  ring unseen.
- **Waypoint teleports still escape**, under the normal waypoint rules (cost, timer, cooldown).
- **A logout no longer escapes.** The chase is restored on rejoin (the pending units wait out `Rejoin_Grace_Seconds`).
- **Spawn distances need a restart.** After `/glw reload` the `Cops.Spawn` distances and attempts keep their boot values for
  the out-of-sight search until the server restarts; everything else reloads at once.
- **Restricted and breaker regions do nothing yet.** They are stored for a later release.

---

## API

The main entry point for the cops system is `CopService`, accessible from the plugin context.

```java
CopService copService = gangland.getContext().get(CopService.class);

// Check if a player is being pursued
boolean pursued = copService.isBeingPursued(player);

// Manually trigger a cop spawn for a player
copService.

spawnCopsFor(player);

// Despawn all cops currently targeting a player
copService.

despawnCopsFor(player);
```

The `CopSpawnManager` handles spawner persistence:

```java
CopSpawnManager spawnerManager = gangland.getContext().get(CopSpawnManager.class);

// Get all registered spawner locations
List<CopSpawner> spawners = spawnerManager.getSpawners();
```

---

## Related Systems

Cops N Crooks is the umbrella for several NPC / economy systems that ship in the same module. Each has its own
feature doc:

- [Traders](./traders.md) — stationary shop NPCs with buy / barter / sell / tip and a per-player mood model.
- [Bank & Banker](./bank.md) — the Banker NPC and tiered bank-balance ladder.
- [Jail & Detainment](./jail-detainment.md) — handcuffs, jail, bail, bribery, and sentence timers.
- [Wanted & Bounty](./wanted-bounty.md) — how players accumulate stars and how bounties are placed and claimed.

---

[← Changelog](../v0.7.5-DEV/CHANGELOG.md) | [Back to Index](../README.md) | [Next: Traders →](./traders.md)
