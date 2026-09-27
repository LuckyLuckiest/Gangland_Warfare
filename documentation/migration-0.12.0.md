# Migrating a server from 0.11.x to Gangland 0.12.0

[← Back to Documentation Index](./README.md)

Gangland 0.12.0 "Heat" replaces the flat kill-point system with a sophisticated heat ledger, introduces skill-based evasion through line-of-sight mechanics, adds a visual HUD with BossBar and search zones, configurable multi-tier cop rosters with backup waves, and per-chase pursuit reports. **Escaping becomes a skill** — a three-star chase can be shaken in under a minute by breaking line of sight, and dying is never the cheapest way out.

No config keys were removed, and every new key has a default, so existing servers keep working. The biggest player-facing change is the default removal of the per-star money drain during a chase (moved to death penalties instead).

## 1. New Config Keys in `settings.yml` → `Wanted:`

All keys have sensible defaults and servers without them will load with correct behavior. Add these to `settings.yml` under `Wanted:` if you want to tune the experience.

### `Evasion` block (new; 0.12)

```yaml
Wanted:
   Evasion:
      Enable: true                           # Turn off to use 0.11.x behavior (repeating decay only)
      Lost_Sight_Seconds: 3                  # Squad must lose the player for this long before the search starts
      Drop_Mode: ONE_STAR                    # ONE_STAR (search restarts at new level) or ALL_STARS (wanted ends)
      Search_Radius: [40, 60, 90, 130, 180]  # Search zone radius per star (blocks)
      Seconds_To_Drop: [10, 20, 30, 45, 60]  # Time to drop a star while unseen and outside the zone
      Outside_Zone_Speed: 2.0                # Decay clock speed outside the zone (1.0 = normal)
      Hideout_Speed: 1.5                     # Reserved for 0.14 (hideout mechanics) — read but not used yet
```

The evasion system is **enabled by default**. To use only the repeating decay timer (0.11.x behavior), set `Evasion.Enable: false`.

### `Take_Money` → `Amount: 0` (changed default; 0.12)

```yaml
Wanted:
   Take_Money:
      Amount: 0               # CHANGED: Was 50, now 0 (disabled). Servers keep their existing value.
      Multiplier: 5           # Unchanged: exponent base for the formula Amount * Multiplier ^ stars
```

The legacy per-star money drain from the fallback decay timer is now **disabled by default** (was 50). The repeating decay timer still exists and runs when evasion disengages (e.g., player has no cop group), but it no longer drains cash. Servers that explicitly set `Take_Money.Amount` to a custom value keep that value — the default changed, not existing configs.

**For servers that want to cost wanted levels:** use the death penalty formula (see below) instead. When a player dies while wanted, the formula can include the `wanted` variable.

## 2. Death Penalty Formula: New Variables (0.12)

The `User.Death.Money.Formula` in `settings.yml` can now reference `wanted` and `bounty` variables (in addition to `balance`):

```yaml
User:
   Death:
      # ... other keys ...
      # The formula used when calculating the value to lose/gain on death.
      # Available variables: balance (current balance), level (player level), experience (current XP),
      # bounty (current withholding bounty), wanted (current wanted level). All may be zero.
      Formula: "balance * 0.15"
      # Example with wanted variable:
      # Formula: "balance * 0.15 + wanted * 500"  # 15% of balance + 500 per wanted star
```

`wanted` and `bounty` were already available to this formula before 0.12 — the formula evaluator itself is unchanged.
0.12 only documents them in the shipped `settings.yml` comment, since servers had no way to discover them otherwise.
Old servers without the `wanted` variable in their formula evaluate `balance * ...` exactly as before.

## 3. New Config Keys in `cops.yml`

All new keys in `cops.yml` have defaults. Existing `cops.yml` files work unchanged.

### Heat ledger (`Heat:` section, new; 0.12)

```yaml
Heat:
   Enable: true                              # Turn off to use 0.11.x flat kill system
   Star_Thresholds: [100, 250, 450, 700, 1000]  # Heat values that trigger each star
   Streak_Bonus: 1.5                         # Multiplier for kills within the combo window
   Turf_War_Multiplier: 0.5                  # Multiplier for kills inside contested turfs
   Assault_Cop_Cooldown_Seconds: 10          # Rate limit: one assault per (player, cop) per window
   Crimes:
      Kill_Player: 80                        # Heat for killing a player
      Kill_Civilian: 100                     # Heat for killing a civilian
      Kill_Cop: 150                          # Heat for killing a cop
      Assault_Cop: 100                       # Heat for any damage to a cop NPC
```

If `Heat.Enable` is false, the system falls back to 0.11.x behavior (one point per kill, `Kill_Counter` thresholds). Existing servers without the `Heat:` block will use code defaults.

### Cop rosters & backup waves (`Cops:` section, new; 0.12)

```yaml
Cops:
   Rosters:                                  # Who shows up per wanted star
      1: { 1: 2 }                            # 1-star: 2 Tier-1 officers
      2: { 1: 2, 2: 2 }                      # 2-star: 2 Tier-1, 2 Tier-2
      3: { 2: 2, 3: 2 }                      # 3-star: 2 Tier-2, 2 Tier-3
      4: { 3: 2, 4: 4 }                      # 4-star: 2 Tier-3, 4 Tier-4
      5: { 4: 3, 5: 4 }                      # 5-star: 3 Tier-4, 4 Tier-5
   Backup_Delay_Seconds: [15, 12, 10, 8, 6] # Seconds before dead cops respawn, per star
   # ... rest of Cops config ...
```

If `Rosters` is missing or a star has no roster entry, the system falls back to 0.11.x behavior (uses `getTierForWantedLevel` + `getTargetCopCount`). The old tier/count system still works.

**Backup waves**: When cops die, missing cops respawn after the per-star delay, not on the next spawn check. Initial cops spawn immediately when wanted starts.

### HUD features (`Hud:` section, new; 0.12)

```yaml
Hud:
   Enable: true                              # Turn off to disable the whole HUD
   Boss_Bar: true                            # The wanted boss bar (red/yellow/green)
   Search_Zone_Ring: true                    # Red-dust particle ring while searching
   Escape_Compass: true                      # Compass points outside the zone while searching
   Star_Gain_Title: true                     # Title flash + sound when a star is gained
   Messages:
      In_Sight: "%stars% &c&lIN SIGHT"      # BossBar title when cops have a fresh sighting
      Searching: "%stars% &e&lSEARCHING %time%"  # BossBar title while clock is counting down
      Wanted: "%stars% &c&lWANTED"          # BossBar title when evasion is off / fallback decay
      Star_Lost: "%stars% &a&lSTAR LOST"    # Flash when a star is dropped
      Star_Gained_Title: "&c&lWANTED"       # Title when a star is gained
      Star_Gained_Subtitle: "%stars%"       # Subtitle showing new star count
```

The HUD is **enabled by default** and can be customized or disabled entirely. `%stars%` is replaced with the star string (e.g., ★★★☆☆), and `%time%` is m:ss format.

### Pursuit reports (`Pursuit_Report:` section, new; 0.12)

```yaml
Pursuit_Report:
   Enable: true                              # Turn off to disable chase tracking and reports
   Breaking_News:
      Enable: true                           # Server-wide broadcast on epic chases
      Min_Stars: 4                           # Broadcast if max stars reached this level OR...
      Min_Duration_Seconds: 120              # ...if the chase ran at least this long
      Cooldown_Seconds: 300                  # Global cooldown between broadcasts
   Messages:
      Report:
         - "&8&m----------&r &c&lPURSUIT REPORT &8&m----------"
         - "&7Outcome: %outcome%"
         - "&7Duration: &f%duration%  &7Top stars: %stars%"
         - "&7Cops down: &f%cops%"
      Outcome_Escaped: "&aESCAPED"
      Outcome_Busted: "&9BUSTED"
      Outcome_Wasted: "&4WASTED"
      Breaking_News: "&c&lBREAKING NEWS &7» &f%player% %outcome_plain% after a %duration% chase at %stars%&f, %cops% officers down."
```

Chases end with one of three outcomes:
- **ESCAPED**: Wanted dropped to 0★ via evasion, decay, or an admin clear, without ever being restrained
- **BUSTED**: Player is (or was cuffed within the last 30 seconds) handcuffed or jailed when the chase ends
- **WASTED**: Player died or was downed at any point during the chase (sticky — wins over BUSTED/ESCAPED even if the
  wanted level later clears some other way)

## 4. Behavior Players Will Notice

### Heat system replaces flat kills

- **Before 0.12**: 1 kill = 1 point toward the next star threshold (2, 5, 10, 15, 20).
- **After 0.12** (with heat enabled): Different crimes have different weights:
  - Kill a player: 80 heat
  - Kill a civilian: 100 heat (self-defence exemption for hostile civs in combat)
  - Kill a cop: 150 heat
  - Assault a cop (any non-lethal damage): 100 heat (rate-limited to once per player-and-cop pair per 10 seconds; a
    lethal hit scores "Kill a cop" instead)
- **Stars now depend on heat thresholds**: 100, 250, 450, 700, 1000 heat = 1, 2, 3, 4, 5 stars.
- **Streak bonus**: From your second kill onward while your kill combo is still running (within the default 10-second
  reset window of the previous kill), each crime's heat is multiplied by 1.5×.
- **Turf war bonus**: Any kill (player, civilian, or cop — not `Assault_Cop`) whose victim stands inside a
  currently-contested turf multiplies its heat by 0.5× (cheaper inside the war zone). No-ops without the turf module.

### Line-of-sight evasion enables skill-based escape

- **New mechanic**: Cops form a squad that shares what they see. Breaking line of sight starts a 3-second timer.
- **After 3 seconds unseen**: The squad enters SEARCHING mode and draws a search zone (radius per star: 40, 60, 90, 130, 180 blocks).
- **While inside the zone**: The clock still counts down, just at normal (1×) speed — staying inside the zone slows
  the drop, it does not stop it.
- **Outside the zone**: The clock counts down twice as fast (2× speed by default), so a star drops sooner —
  10–60 seconds of being unseen (per star) if spent entirely outside the zone, longer if spent inside it.
- **Star loss behavior**: You can drop ONE star and keep searching at the lower level, or drop ALL stars and go free (configurable).
- **Fallback**: Servers with evasion disabled, or players with no cop group, use the old repeating decay timer (still configurable).

### Boss bar and HUD feedback

- **Red full bar**: Cops have a fresh sighting (IN SIGHT).
- **Yellow bar with countdown**: You're in SEARCHING mode. Stars flash filled/grey every second.
- **Green flash**: A star was dropped (STAR LOST).
- **Search zone ring**: Red-dust particles outline the search zone (only the wanted player sees it).
- **Escape compass**: Points to the nearest point outside the zone (helps locate the exit).
- **Title flash**: Sound + title when you gain a star.

### Rosters and backup waves

- **Mixed squads**: Police response is no longer "N cops of tier T", but a mix by tier. For example, 4-star response is 2 Tier-3 + 4 Tier-4 cops.
- **Backup waves**: When a cop dies, missing roster spots don't respawn immediately. They respawn after a per-star delay (15, 12, 10, 8, 6 seconds). This breaks up the response into waves, making the chase less overwhelming early.
- **Backward compatible**: Servers without the `Rosters` config use the old tier/count system unchanged.

### No per-star money drain during chases

- **Before 0.12**: Being wanted drained money every 120 seconds (scaled by stars).
- **After 0.12**: For a chase evasion handles, the repeating decay timer's step (and so its money drain) is cancelled
  outright — evasion drops stars instead. For the fallback case (evasion disabled, or no cop ever sent), the timer
  still runs but **doesn't drain cash** by default, since the shipped `Take_Money.Amount` is now `0`.
- **New cost**: Death penalties. When you die, the formula can include `wanted` stars. Example: `balance * 0.15 + wanted * 500` charges 500 per wanted star on top of the 15% death loss.
- **Arrest costs**: Bail, bribe, and sentence still apply (these are arrest costs, separate from death penalties).
- **Fallback timer**: Without evasion, the old repeating decay still exists and can be configured to drain money (set `Take_Money.Amount` to non-zero).

## 5. Breaking Changes (None)

- Old configs without new keys load with code defaults.
- The heat system is backward compatible — if you leave it disabled, the old kill-counter system runs.
- No public API signatures were removed; all changes are additive.
- The evasion system and the repeating decay timer both exist in the codebase, but only one drives a given chase:
  evasion takes over once a cop is sent, cancelling the timer's own steps for that player; the timer is otherwise the
  fallback.

## 6. Config Migration Checklist

- [ ] **Add** `Wanted.Evasion` block to `settings.yml` (or leave it out for defaults).
- [ ] **Set** `Wanted.Take_Money.Amount: 0` if you want to disable per-star money drain.
- [ ] **Update** `User.Death.Money.Formula` comment if you want to use `wanted` variable (optional).
- [ ] **Add** `Heat:` block to `cops.yml` (or leave it out for defaults).
- [ ] **Add** `Cops.Rosters` and `Cops.Backup_Delay_Seconds` to `cops.yml` (or fall back to old system).
- [ ] **Add** `Hud:` and `Pursuit_Report:` blocks to `cops.yml` (optional; defaults are sensible).
- [ ] **Test**: Start the server. Check console for missing config warnings. Try a wanted chase.

## 7. Optional Tuning

| Setting | Default | Effect |
|---------|---------|--------|
| `Wanted.Evasion.Enable` | true | Turn off for pure repeating decay |
| `Wanted.Evasion.Lost_Sight_Seconds` | 3 | How long unseen before search starts |
| `Wanted.Evasion.Drop_Mode` | ONE_STAR | Drop one or all stars at timeout |
| `Wanted.Evasion.Search_Radius[N]` | [40, 60, 90, 130, 180] | Search zone size per star |
| `Heat.Enable` | true | Turn off for old kill-counter system |
| `Heat.Star_Thresholds` | [100, 250, 450, 700, 1000] | Heat values per star |
| `Heat.Streak_Bonus` | 1.5 | Multiplier for consecutive kills |
| `Cops.Rosters` | 5 mixed-tier squads (1★–5★, see below) | Remove an entry (or the whole section) to fall back to the pre-0.12 single-tier squad for that star |
| `Hud.Enable` | true | Turn off to disable all visual feedback |
| `Pursuit_Report.Enable` | true | Turn off to disable chase tracking |

---

[← Back to Documentation Index](./README.md)
