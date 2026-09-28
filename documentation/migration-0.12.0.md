# Migrating a server from 0.11.x to Gangland 0.12.0

[← Back to Documentation Index](./README.md)

Gangland 0.12.0 gives cops and hostile civilians squad tactics on top of Keystone 1.13.0's formation/engagement API:
shooters fan out and keep moving while they fire instead of freezing shoulder to shoulder, melee officers surround
and swing with a real hit chance, and both sides talk — police radio in chat, gang shouts in chat. No existing key
was renamed or removed, and every new key has a default, so an untouched 0.11.x config keeps working exactly as
before except where a "behaviour changed" note below applies.

## 1. Update the dependencies

- **Keystone 1.13.0.** Replace `Keystone-<version>.jar` in `plugins/`. Gangland 0.12.0 is built against Keystone's
  new squad-engagement, melee-band and squad-signal APIs and needs it.
- **Bartizan 0.6.0** if you use Bartizan weapons on cops or civilians. It is required for the "move while shooting"
  fix: it counts NPC gun cadence in server ticks, re-aims each shot of a burst at a moving target, and reports
  reloads (the squad's `Reloading` radio line and the shooter backing off to reload). On Bartizan 0.5.x the new
  Keystone hooks are never called: NPCs still shoot at the same cadence as 0.11 (`Fire_Rate_Multiplier` has no
  effect on their Bartizan guns), but without the moving re-aim or the reload signal.
- **Citizens 2.0.42 or newer** is recommended (unchanged floor from 0.11.0); it adds the look-at-target visual while
  an NPC is repositioning. An older Citizens still works, just without that visual — the aim itself is unaffected.

## 2. New `npc/cops.yml` keys (`Cops:`)

| Key | Default | Meaning |
|---|---|---|
| `Melee.Reach` | `3.0` | A cop's swing lands only within this many blocks (feet to feet). |
| `Melee.Approach` | `2.0` | Melee cops stop and surround here; capped at each tier's `Cuff_Radius - 0.5`. Full damage inside this distance. |
| `Melee.Damage_Spread` | `0.15` | Melee damage varies by up to this fraction either way. |
| `Melee.Edge_Damage` | `0.7` | Fraction of full damage a swing deals at the very edge of `Reach`, falling linearly from `Approach`. |
| `Tactics.Enabled` | `true` | `false` restores the 0.11.0 behaviour for every tier that doesn't override it: shooters freeze in their firing band, no formation, no radio signals. |
| `Tactics.Formation_Arc` | `270.0` | Degrees the squad's shooters spread over around the target (0–360). Melee tiers ignore this and always surround at 360/n. |
| `Tactics.Strafe_Degrees` | `15.0` | Degrees a shooter side-steps along its ring at each reposition (`0` = walk to its slot and stand). |
| `Tactics.Reposition_Ticks` | `60` | Server ticks between a shooter's repositions, jittered ±25%. |
| `Tactics.Moving_Aim_Error` | `0.10` | Extra aim error while the cop is walking, on top of its `Difficulty`'s aim error. |
| Per-tier `Tiers.<n>.Tactics` | — | Overrides `Tactics` key by key for one tier. Shipped arcs: **Lieutenant 200°, SWAT 270°, Military 330°** (officer/sergeant are melee, so their arc is unused). |
| `Tiers.<n>.Fire_Rate_Multiplier` | `0.1` | The tier's gun cadence as a fraction of the weapon's own (player) fire rate, on top of `Difficulty`. See §5. |
| `Radio.Enabled` | `true` | `false` silences the radio for players; cops still respond to each other (responders, backup) — that's NPC hearing, not chat. |
| `Radio.Range` | `32.0` | Players this close to the speaking or addressed cop hear the line; also how far other cops hear a Contact / Man Down / Backup call and respond. |
| `Radio.Target_Range` | `64.0` | How far the hunted player hears their own pursuers. |
| `Radio.Squad_Gap_Ticks` | `30` | Minimum gap between two lines from one squad (priority lines and acks skip it). |
| `Radio.Player_Gap_Ticks` | `20` | Minimum gap between two low-priority lines reaching one player, across all squads. |
| `Radio.Ack_Delay_Ticks` | `25` | Ticks before an ordered cop acknowledges a delivered order; forced to at least `Player_Gap_Ticks + 5`. |
| `Radio.Responder_Max` | `2` | Nearby cops (returning, idle, or chasing a civilian — **including cops from another wanted player's group**) pulled into the hunt per radio call. `0` disables responders. |
| `Radio.Priority`, `Radio.Cooldown_Ticks`, `Radio.Sound` | see file | Which line kinds skip the gaps, per-kind repeat cooldowns, and the click sound played with every line. |
| `Backup.Enabled` | `true` | When a cop goes down, the squad radios for backup. |
| `Backup.Extra_Cops` | `1` | Extra cops on top of the wanted-level count, still capped by `Behaviour.Max_Per_Player`. |
| `Backup.Duration_Ticks` | `600` (30 s) | How long the extra cops stay requested. Afterwards the surplus cops that aren't fighting walk home. |
| `Backup.Cooldown_Ticks` | `1200` (60 s) | Minimum gap between two backup requests from one group. |
| `Retreat.Enabled` | `true` | A badly hurt cop breaks off, radios it, and takes cover out of the suspect's sight for up to 10 s before coming back out. |
| `Retreat.Health_Fraction` | `0.3` | Retreat at or below this fraction of max health. |
| `Retreat.Radius` | `12.0` | Blocks searched around the cop for a spot the suspect can't see. |

## 3. New `npc/civilians.yml` keys

| Key | Default | Meaning |
|---|---|---|
| `Shouts.*` (top level) | see file | Delivery tuning for faction shouts — same shape as `Cops.Radio` above (`Enabled`, `Range` `24.0`, `Target_Range` `32.0`, gap/cooldown/priority keys), minus the sound and dispatch pieces gangs don't use. `Shouts.Range` is deliberately above `AI.Combat.Alert_Range` so a shout recruits allies further than any member can see the fight itself. `Responder_Max` defaults to `0` — gangs recruit through the Contact shout's own range check, not a separate responder pull. |
| Per-type `AI.Combat.Tactics.*` | arc 160, strafe 15, reposition 60 ticks, moving aim error 0.10 when the block is omitted | Same keys as `Cops.Tactics` above. Shipped arcs are narrower than cops, because same-faction damage is **not** cancelled: gang_member 140°, turf_defender 100°, quartermaster 120°. |
| Per-type `AI.Combat.Melee.*` | reach 3.0, approach 2.0, spread 0.15, edge 0.7 when the block is omitted | Same keys as `Cops.Melee` above; the cooldown is always `Attack_Interval_Ticks`. gang_member ships with `Damage_Spread: 0.20`. |
| Per-type `AI.Combat.Fire_Rate_Multiplier` | `0.05` | Same meaning as the cop key, see §5. |
| Per-type `AI.Combat.Retreat.*` | same defaults as `Cops.Retreat` | A badly hurt member breaks off to cover, shouted as `Fall_Back` / `In_Cover`. |

These keys don't apply to `pedestrian` or `trader` (both have `Combat.Enabled: false`).

## 4. `settings.yml` — meaning changed, no new keys

| Key | New meaning |
|---|---|
| `Cops.Behaviour.Combat_Range` | Now **melee-only**: the distance at which a melee cop may start an attack. A ranged cop fires at anything it can see within `Alert_Range` instead — the old 6–14 block gap between the firing band and this range, where a ranged cop just stood there not shooting, is gone. A swing itself only lands within `Cops.Melee.Reach`. |
| `Cops.Behaviour.Attack_Cooldown_Ticks` | Now actually used, as the cop melee cooldown (previously read but not applied). |
| `Cops.Behaviour.Max_Cuff_Attempts` | Now escalates: see §6. |

`civilians.yml`'s per-type `Attack_Range` keys have the same "melee-only, ranged fires within `Alert_Range`" change.

## 5. Gun cadence: server ticks, offset exactly by `Fire_Rate_Multiplier`

Keystone 1.13.0 and Bartizan 0.6.0 move NPC gun cadence from "one weapon tick per AI tick" to real server ticks, so
an NPC's gun now advances while it strafes instead of only on its own AI heartbeat. On its own that would multiply
the cadence by the AI tick rate: **10× for cops** (`Cops.Behaviour.AI_Tick_Rate: 10`) and **20× for civilians**
(`Civilians.Behaviour.AI_Tick_Rate: 20`). `Fire_Rate_Multiplier` cancels it. The shipped defaults (`0.1` for cops,
`0.05` for civilians) are exactly `1 / AI_Tick_Rate`, which restores the 0.11 cadence exactly, not approximately.
A `cops.yml`/`civilians.yml` without the key gets `1 / AI_Tick_Rate` for whatever tick rate you run. If you run a
non-default `AI_Tick_Rate` and copy the new files, set the multiplier to `1 / your AI_Tick_Rate`.

- **Bartizan guns:** the multiplier is a fraction of the weapon's own (player) fire rate: `1.0` = as fast as a player
  holding the same gun, `0.1` = a tenth as often.
- **Vanilla `BOW` / `CROSSBOW`** (a `Weapon_Pool` entry without `weapon:`, or any ranged NPC on a server without
  Bartizan): counted from a 15-server-tick base, so the default still fires every 150 ticks (7.5 s) for cops and
  every 300 ticks (15 s) for civilians before `Difficulty`, the same as 0.11.

The multiplier is applied on top of the NPC's `Difficulty` (harder difficulties already fire faster). Raising it
above the default makes NPCs fire faster than 0.11 and shortens time-to-kill.

## 6. Behaviour players (and admins) will notice

- **Squads no longer freeze in a line.** Ranged members spread across their formation arc at staggered ranges, keep
  moving while they fire, and check their fire when a squad-mate is in the way. Melee members surround the target.
  Set `Tactics.Enabled: false` (default per tier, or under `Cops.Tactics` / a type's `AI.Combat.Tactics` for
  everyone) to restore the exact 0.11.0 freeze-in-band behaviour for that scope.
- **Real melee.** A swing only lands within `Melee.Reach` (3 blocks), can miss (the NPC's `Difficulty` sets the hit
  chance), and does less damage the further out it lands. Reaction times and cooldowns are now real server ticks
  instead of being silently divided by the AI tick rate — an EASY cop's first swing now comes in about 1.5 s
  instead of 15 s.
- **Cops cuff first, then fight.** A wanted player under `Max_Cuff_Attempts` is cuffed while the rest of the group
  holds surround posts. After enough escapes across the whole group (default 3), or any hit landed on a cop, the
  whole group gives up cuffing, switches to combat, and radios "Suspect is resisting!" There's no separate
  "shoot on sight" key — that's a deliberate simplification for 0.12.0.
- **Police radio, in chat.** Cops call out contacts, orders (with an acknowledgement from the ordered cop), reloads,
  check-fire, casualties, backup requests, and dispatch lines. Every player within `Radio.Range` (32 blocks by
  default) hears it, bystanders included — this is a deliberate default, not a bug. Set `Radio.Enabled: false` to
  silence it for players without disabling the NPC-side behaviour it drives.
- **Radio responders cross groups.** A Contact, Man Down or Backup call can pull in up to `Responder_Max` (2) nearby
  cops that are idle, returning, or chasing a civilian — including cops assigned to a *different* wanted player, as
  long as they aren't currently cuffing, guarding, or hunting a player of their own. This is NPC hearing: it works
  even with no player nearby to see it happen.
- **Backup de-escalates.** When a cop goes down, the squad requests up to `Backup.Extra_Cops` extra cops for
  `Backup.Duration_Ticks`. Once that expires, the surplus cops that aren't fighting walk home and despawn instead of
  lingering forever.
- **Gang shouts recruit further than sight.** A faction member's first sighting of a target is shouted in chat and
  recruits same-faction allies within `Shouts.Range` (24 blocks), which is deliberately wider than `Alert_Range` (16
  blocks) — a gang can rally members that haven't personally seen the fight yet.
- **Retreat to cover.** A cop or hostile civilian at or below `Retreat.Health_Fraction` of its max health (30% by
  default) breaks off, radios/shouts a fall-back line, and looks for a spot out of the target's sight for up to
  10 seconds before rejoining the fight.
- **Two standing bugs are fixed:**
  - A cop killed mid-navigation (not "valid" yet when its `EntityDeathEvent` fires) now correctly triggers its
    squad's Man Down / Leader Down signal, backup request, and drop table instead of being silently skipped.
  - Wanted-level decay no longer freezes for the rest of an episode after a single listener cancels one decay tick
    — that cancel now applies to that tick only, as intended.

## 7. New message files

- `npc/cop_radio_messages.yml` / `_es.yml` (cops-n-crooks) — every police radio line, keyed by signal
  (`Contact`, `Flank_Left`, `Man_Down`, `Backup`, `Dispatch_Wanted`, ...). Each `Lines.<key>` is a list; one entry is
  picked at random, and an explicit `[]` silences that signal.
- `npc/civilian_messages.yml` / `_es.yml` (gangland-civilians) gained a `Shouts:` block with the same shape. Gangs
  ship several signals muted (`Reposition: []`, `Route: []`, `Ack: []`, `Responding: []`) on purpose — gangs don't
  run radio drills, and that gap is part of their flavour.

## 8. Required versions

- Keystone **>= 1.13.0** (required).
- Bartizan **>= 0.6.0** (required for the move-while-shooting fix when Bartizan weapons are in use; see §1. A
  server without Bartizan needs nothing, since its NPCs use vanilla bows and crossbows).
