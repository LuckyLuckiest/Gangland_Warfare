# Gang Fights

[← Gangs](./gangs.md) | [Arenas →](./arenas.md) | [Back to Index](../README.md)

---

## Overview

A **gang fight** is a staged, arena-based duel between two gangs. One gang challenges another, both sides field the
same number of fighters inside a pre-built arena, and the last gang with fighters standing wins. A fight can carry a bet
from each gang's bank: the winner takes the pot, and a bet at or above `Ranked.Ranked_Min_Bet` makes the fight ranked.

The module ships as the runtime module `gangland-gang-fights` (module id `fights`). It needs the `gang` module and
nothing else. Its rules live in `fight/fight_rules.yml`, its player text in `fight/fight_messages.yml`, and its arenas in
`fight/arenas.yml`. Build the arenas with the [Arenas guide](./arenas.md).

---

## Installing the Module

```
/glw module install fights
```

Restart the server afterwards. Module installs and updates take effect only after a restart. The `gang` module must be
installed first (`module.yml` `Depends: [gang]`). Without `gang` the module is skipped at boot and the `/glw gang fight`
verbs do not exist.

---

## Commands

Players run these under `/glw gang fight`. Each verb is a permission node under `gangland.command.gang.fight.*`, all
default true.

| Command                                      | What it does                                                                 |
|----------------------------------------------|------------------------------------------------------------------------------|
| `/glw gang fight propose <gang> <size> [bet]` | Challenge another gang to a fight of `<size>` fighters per side. The bet is optional. |
| `/glw gang fight accept <gang>`              | Accept a gang's challenge. Both banks pay the stake here.                    |
| `/glw gang fight decline <gang>`             | Decline a gang's challenge. Starts the decline cooldown for the proposer.    |
| `/glw gang fight enlist`                     | Join your gang's side in the fight lobby.                                    |
| `/glw gang fight drop`                       | Leave the lobby before the countdown starts.                                 |
| `/glw gang fight info`                       | Your gang's challenge or fight: sides, bet, arena, time left.                |
| `/glw gang fight arenas`                     | List the arenas open for fights.                                             |
| `/glw gang fight stats [gang]`               | Fight wins, losses and draws. Without a gang name it reads your own gang.    |

`propose`, `accept` and `decline` take a gang name (any case). `propose` takes the size and bet as chained optional
arguments, so tab completion offers them in order.

---

## How a Fight Runs

The fight is a state machine: **proposed, lobby, countdown, running, result, ended**. A cancel from any step before the
result refunds any stake that was already paid.

1. **Propose.** The proposer's gang sends the challenge. The other gang sees it in chat and has
   `Challenge.Challenge_Expiry_Seconds` (60) to answer. A lapsed challenge counts as a decline.
2. **Accept or decline.** Accepting pays the bet from both banks. Declining, or letting it lapse, starts the proposer's
   `Challenge.Decline_Cooldown_Seconds` (60) before that pair can challenge again.
3. **Lobby.** Members enlist with `/glw gang fight enlist` during `Challenge.Join_Window_Seconds` (60). Each side needs at
   least `Min_Team_Size` fighters. With `Require_Equal_Teams: true`, both sides must reach exactly `<size>` or the fight
   is cancelled and refunded.
4. **Countdown.** `Challenge.Countdown_Seconds` (10). Fighters stand in the arena and cannot attack yet.
5. **Running.** Fighters fight. A fighter who dies is eliminated for the fight (see [What Happens to a Fighter](#what-happens-to-a-fighter)).
   A fight still running after `Challenge.Max_Duration_Minutes` (10) ends by `Challenge.Timeout_Result`:
   `MOST_ALIVE` gives the win to the side with more fighters left (equal is a draw), `DRAW` makes it a draw.
6. **Result.** The result is shown for `Challenge.Result_Seconds` (5), the bet is settled, and fighters are sent back to
   where they came from.

Team size is `Challenge.Min_Team_Size` (1) to `Challenge.Max_Team_Size` (5). The proposer picks `<size>` inside that range.

### Who may fight

- A member whose rank may not start or accept fights is refused (`Challenge.Rank_Needed`).
- Any stake also needs a rank that may withdraw from the gang bank (`Challenge.Withdraw_Needed`).
- A fighter may not be tagged in combat, downed, or wanted when he enlists (`Challenge.Tagged`, `Challenge.Downed`,
  `Challenge.Wanted`).
- `Challenge.Member_Too_Young` applies when a fighter has not been in the gang for the required hours (see below).
- Same gang and allied gangs cannot fight each other (`Challenge.Same_Gang`, `Challenge.Allied`).

---

## Bets

A bet moves money bank to bank and is zero-sum. Both banks pay at accept time. A failed second withdrawal gives the
first stake back.

- The **winner** takes the whole pot minus `Bets.House_Cut_Percent` (0 by default, so nothing is kept).
- A **draw** or a **cancel** gives each stake back.
- Whatever would push the winner's bank above the gang bank maximum (`Account.Maximum_Balance`) goes back to the loser.

| Key                                         | Default  | Meaning |
|---------------------------------------------|----------|---------|
| `Bets.Max_Bet`                              | 100000   | Most money one gang may stake on one fight. |
| `Bets.Max_Bet_Percent_Of_Bank`              | 50       | A stake may not be more than this percent of the staking gang's bank. The other bank must be able to match it. |
| `Bets.House_Cut_Percent`                    | 0        | Percent of the pot the server keeps on settlement. |
| `Bets.Max_Net_Transfer_Per_Pair_Per_Day`    | 200000   | Most money that may move between one pair of gangs through bets per day. |
| `Bets.Max_Bet_Transfer_Per_Gang_Per_Day`    | 300000   | Most money one gang may win from bets per day. |

When a daily cap is reached, the fight is refused with a message (`Economy.Pair_Cap` or `Economy.Gang_Cap`).

---

## Ranked Fights

A fight is **ranked** when its bet is at least `Ranked.Ranked_Min_Bet` (1000). A ranked fight also needs:

| Key                                             | Default | Meaning |
|-------------------------------------------------|---------|---------|
| `Ranked.Ranked_Min_Member_Age_Hours`            | 24      | Ranked fighters must have been in their gang this many hours. |
| `Ranked.Ranked_Min_Opponent_Gang_Age_Days`      | 7       | The other gang must be at least this many days old. |
| `Ranked.Ranked_Per_Pair_Per_Day`                | 3       | Most ranked fights between the same two gangs per day. |
| `Ranked.Rematch_Cooldown_Minutes`               | 30      | After a ranked fight the same two gangs wait this long before another ranked fight. |
| `Ranked.Ranked_Board_Wins_Per_Opponent_Per_Day` | 1       | Wins against the same opponent that count on the board per day. |

Ranked wins feed the `wins` stat of the gang ranking (`/glw gang ranking wins`). Extra wins over the same opponent in one
day do not count.

Sparring fights (no bet, unranked) use `Challenge.Min_Member_Age_Hours` (0) instead of the ranked age.

---

## What Happens to a Fighter

| Event                          | Result |
|--------------------------------|--------|
| **Dies** in the arena          | Eliminated for this fight. The lethal hit is cancelled, so nothing really dies: no death, no drops, no kill credit, no money drop, no downed state, no hospital respawn and no wanted level. |
| **Quits** the server           | Forfeits the fight and is restored while still online. A crash or kick mid-fight strands nobody: a fighter who joins inside an arena lands at its exit. |
| **Leaves the gang** or the gang is **disbanded** | Counts as a forfeit. A disband to escape a fight means the other side wins the settlement (or the fight is cancelled and refunded during the countdown). |
| **Steps out of the arena box** | Warned up to `Guards.Max_Leave_Warnings` (3) times. The next leave forfeits. Fighters found outside are put back at their side's spawn. |
| **Drops out of the lobby**     | Allowed with `/glw gang fight drop` before the countdown. |

A fight never gives a wanted level, a bounty payout, a dropped inventory, or a downed state. Fighters keep their
inventory and level.

---

## Guards During a Fight

While a fight uses an arena, these rules apply to fighters and to everyone else:

- **Command limit.** Controlled by `Guards.Command_Block_Mode`:
  - `ALLOWLIST` (default): a fighter may only run commands that start with an entry in `Guards.Allowed_Commands`.
    The defaults are `glw gang fight`, `gangland gang fight`, `msg`, `r` and `tell`.
  - `BLOCKLIST`: a fighter may run anything except the entries in `Guards.Blocked_Commands`.
  - `OFF`: no limit.
  
  Aliases and namespaces are resolved first, so `/minecraft:tp` counts as `tp`. A blocked line is cancelled with the
  message `Guards.Command_Blocked`.
- **Bypass.** `gangland.gang.fight.bypass_commands` (op by default, through `gangland.admin.gang`) skips the command limit.
- **Blocks.** With `Guards.Protect_Blocks: true` (default), nobody may break or place blocks inside a busy arena.
- **Vehicles.** `Guards.Allow_Vehicles: false` (default) stops fighters from riding vehicles or mounts.
- **Totems.** `Guards.Respect_Totems: false` (default) ignores a totem of undying. Set it to `true` to let a totem save a
  fighter by hand, once.
- **Safehouses.** A gang cannot set a safehouse inside any arena, open or not (`Safehouse.Set_Refused`).
- **Wanted.** A wanted level cannot be raised on a player who is fighting in the arena.

---

## Forfeit Rules Summary

- Quitting, disbanding and leaving the box past the warning limit forfeit the fighter.
- **Running fight:** a forfeit eliminates the fighter and the fight goes on. When one side has no fighters left, the other
  side wins and the stakes settle.
- **Countdown:** a forfeit that empties either side cancels the fight and refunds both stakes.

---

## Fight Statistics

`/glw gang fight stats [gang]` shows a gang's fight wins, losses and draws. Ranked wins also appear in the gang ranking
under `wins` (see [Ranked Fights](#ranked-fights)).

---

## WorldGuard Flag `glw-gang-pvp`

WorldGuard is an **optional** soft dependency (`softdepend` in `plugin.yml`). When it is installed, Gangland registers a
region flag named `glw-gang-pvp` (a StateFlag) at startup, before the module loader runs.

- Set it inside a region with `/rg flag <region> glw-gang-pvp allow` or `deny`.
- `allow` lets gangmates and allies hurt each other inside the region, whatever the gang settings say.
- `deny` blocks friendly fire inside the region.
- Unset falls through to the normal friendly-fire rules in `gang/gang_rules.yml` (`Friendly_Fire`).

The flag exists only after a **full restart with WorldGuard loaded first**. A `/reload` or a plugin reload does not
register it. Without WorldGuard the flag does not exist and nothing changes. The flag governs friendly fire between
gangmates and allies; it does not change how fights are run.

---

## Keys with no effect yet

These keys are read from `fight/fight_rules.yml` but nothing in the module acts on them in this release. Changing them
does nothing:

- `Challenge.Arena_Cooldown_Seconds`: the arena is not held closed after a fight.
- `Guards.Block_Same_Address`: two players on the same IP may still fight on opposite sides.

---

## fight_rules.yml

`fight/fight_rules.yml` is read on boot and by `/glw reload`. Every key is block style with `Capitalized_Underscore`
names. All 34 keys:

| Section      | Key                                         | Default |
|--------------|---------------------------------------------|---------|
| `Challenge`  | `Challenge_Expiry_Seconds`                  | 60 |
| `Challenge`  | `Join_Window_Seconds`                       | 60 |
| `Challenge`  | `Countdown_Seconds`                         | 10 |
| `Challenge`  | `Max_Duration_Minutes`                      | 10 |
| `Challenge`  | `Result_Seconds`                            | 5 |
| `Challenge`  | `Min_Team_Size`                             | 1 |
| `Challenge`  | `Max_Team_Size`                             | 5 |
| `Challenge`  | `Require_Equal_Teams`                       | true |
| `Challenge`  | `Decline_Cooldown_Seconds`                  | 60 |
| `Challenge`  | `Arena_Cooldown_Seconds` (no effect yet)    | 30 |
| `Challenge`  | `Timeout_Result`                            | MOST_ALIVE |
| `Challenge`  | `Min_Member_Age_Hours`                      | 0 |
| `Bets`       | `Max_Bet`                                   | 100000 |
| `Bets`       | `Max_Bet_Percent_Of_Bank`                   | 50 |
| `Bets`       | `House_Cut_Percent`                         | 0 |
| `Bets`       | `Max_Net_Transfer_Per_Pair_Per_Day`         | 200000 |
| `Bets`       | `Max_Bet_Transfer_Per_Gang_Per_Day`         | 300000 |
| `Ranked`     | `Ranked_Min_Bet`                            | 1000 |
| `Ranked`     | `Ranked_Min_Member_Age_Hours`               | 24 |
| `Ranked`     | `Ranked_Min_Opponent_Gang_Age_Days`         | 7 |
| `Ranked`     | `Ranked_Per_Pair_Per_Day`                   | 3 |
| `Ranked`     | `Rematch_Cooldown_Minutes`                  | 30 |
| `Ranked`     | `Ranked_Board_Wins_Per_Opponent_Per_Day`    | 1 |
| `Guards`     | `Max_Leave_Warnings`                        | 3 |
| `Guards`     | `Protect_Blocks`                            | true |
| `Guards`     | `Allow_Vehicles`                            | false |
| `Guards`     | `Respect_Totems`                            | false |
| `Guards`     | `Command_Block_Mode`                        | ALLOWLIST |
| `Guards`     | `Allowed_Commands`                          | glw gang fight, gangland gang fight, msg, r, tell |
| `Guards`     | `Blocked_Commands`                          | (empty) |
| `Guards`     | `Block_Same_Address` (no effect yet)        | false |
| `Arenas`     | `Min_Arena_Volume`                          | 64 |
| `Arenas`     | `Outline_Seconds`                           | 10 |

Note that `Challenge.Arena_Cooldown_Seconds` and `Guards.Block_Same_Address` are the two keys listed under
[Keys with no effect yet](#keys-with-no-effect-yet).

---

## Related

- [Arenas](./arenas.md): building, validating and opening arenas.
- [Gangs](./gangs.md): the gang module that fights need, friendly fire, the bank and the ranking.
- [Ranks](./ranks.md): which rank may fight, bet and withdraw.
