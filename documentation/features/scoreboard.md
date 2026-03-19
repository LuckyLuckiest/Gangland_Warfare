# Scoreboard

[← Unique Items](./unique-items.md) | [Back to Index](../README.md) | [Next: Database & Setup →](./database.md)

---

## Overview

The scoreboard displays live player stats on the right side of the screen. It supports animated titles, per-row update
intervals, and three different rendering drivers. The displayed rows update independently at configurable tick
intervals, keeping the board responsive without overloading the server.

---

## What Is Displayed

The scoreboard shows the following player stats by default:

- Wanted stars
- Bounty amount
- Cash balance (Purse)
- Player level and XP percentage
- Bank account name and balance
- Gang display name and contribution
- Gang member count (online / total)

The content and update rates for each row are fully configurable in `scoreboard.yml`.

---

## Drivers

The scoreboard supports three rendering drivers. Set the active driver in `scoreboard.yml`.

| Driver      | Description                                                                                                                                                                                |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Driver_V1` | Caches scoreboard lines in memory and uses a clustering algorithm to minimize update packets. Good baseline performance.                                                                   |
| `Driver_V2` | Similar to V1 but delegates cluster management to the FastBoard library. More stable line handling under rapid updates.                                                                    |
| `Driver_V3` | Uses interactive scoreboards. Temporarily replaces the main scoreboard when showing alternate views. The most feature-rich driver — recommended for servers using interactive UI features. |

---

## Animated Title

The scoreboard title supports animation by defining multiple frames. The title cycles through each frame at a
configurable interval (in ticks).

The default animation spells out **GANGLAND** with a sweeping highlight effect across 27 frames.

---

## Configuration

Scoreboard settings live in `scoreboard.yml`:

```yaml
Scoreboard:
   Enable: true
   Driver: "Driver_V3"        # Driver_V1, Driver_V2, or Driver_V3

Board:
   Title:
      Interval: 2              # Ticks between title animation frames
      Lines:
         - "&f&l<>"             # Frame 1
         - "&f&l<&8=&f&l>"      # Frame 2
         # ... additional frames

   Rows:
      1:
         Interval: 10           # Ticks between updates for this row
         Lines:
            - "&7+--------------------+"
      2:
         Interval: 20
         Lines:
            - "&7 Wanted: &c{wanted_stars}"
      # ... additional rows
```

Each row can have its own `Interval`, allowing high-frequency stats (like wanted level) to update more often than
low-frequency ones (like gang info).

---

[← Unique Items](./unique-items.md) | [Back to Index](../README.md) | [Next: Database & Setup →](./database.md)
