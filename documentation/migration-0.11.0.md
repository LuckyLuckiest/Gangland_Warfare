# Migrating a server from 0.10.x to Gangland 0.11.0

[← Back to Documentation Index](./README.md)

Gangland 0.11.0 moves cops and hostile civilians onto Keystone 1.12.0's squad navigation. NPCs hunting the same
target share what they see. When the direct path fails, they plan a route from the target's side, so a player on a
roof is reached by the stairs round the back, a door or a ladder. They never give up on a failed path while the
player is wanted. No config key was renamed or removed, and every new key has a default, so existing files keep
working.

## 1. Update the dependencies

- **Keystone 1.12.0.** Replace `Keystone-<version>.jar` in `plugins/`. Gangland 0.11.0 is built against Keystone's
  new squad API and needs it.
- **Citizens 2.0.41 or newer** for route planning. With an older Citizens, NPCs still chase along direct paths, but
  when a direct path fails they wait below the target instead of planning a route. Each skipped plan is reported in
  the console as the fault `npc.route.plan.failed`.

## 2. Settings whose meaning changed (`settings.yml`)

| Key | Default | 0.11.0 meaning |
|---|---|---|
| `Cops.Behaviour.Alert_Range` | `40.0` | Sight range. A cop sees a wanted player this close with line of sight, and every cop hunting the same player learns the position. Before 0.11.0 it only gated idle cops, which spawned cops skip. |
| `Cops.Pursuit.Max_Ticks` | `120` | Counts only AI ticks in which the cop is stuck and no cop hunting the same player can see them (120 ≈ 60 s). After that the cop walks back and the spawner replaces it. |
| `Cops.Pursuit.Max_Distance` | `80.0` | Unchanged: a cop farther than this from the player walks back and is replaced. |

## 3. New optional keys (`npc/civilians.yml`, per civilian type)

| Key | Default | Meaning |
|---|---|---|
| `Faction` | the type id | The side the type fights for. |
| `AI.Combat.Alert_Range` | `16.0` | Sight range, and how far away same-faction allies hear that a member was hit. |
| `AI.Combat.Search_Seconds` | `20` | A civilian gives up when nobody in its squad has seen the target for this long. This replaces the old "gives up at 4 × attack range". |

## 4. Behaviour players will notice

- **Cops hunt as a squad.** Melee cops spread around the player instead of queueing behind each other. Armed cops hold
  a firing spot 7–12 blocks away while they have a clear shot, and climb after the player once the shot is gone.
- **Out of sight is not gone.** Cops go to the last-known spot and search in widening circles until someone sees the
  player again. A player hiding on a roof is found when a searching cop spots them; then the squad climbs up.
- **Attacking a cop gives the attacker away.** Every cop in that cop's group goes after the attacker, starting from
  where the attacker was when the alert went out. This includes a bystander who is not wanted.
- **Rotated-out cops stay out.** A cop that hits `Max_Ticks` or `Max_Distance` walks back to its station. Cops still
  walking back rejoin the hunt if their player becomes wanted again.
- **Civilians fight in factions.** Hitting a hostile civilian pulls in idle allies of its faction within their own
  `Alert_Range`. The alert goes one hop only. A hit from a member of the same faction pulls in nobody.
