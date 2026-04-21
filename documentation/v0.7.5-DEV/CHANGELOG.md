# 🚨 Case Closed — v0.7.5-DEV Changelog

> *Traders open their doors, Bankers start taking deposits, and the cuffs
> finally come off — for a price. Welcome to **Part 3 of Cops N Crooks**,
> the release that closes out the module.*

---

[← Back to Documentation Index](../README.md) | [Previous: v0.7.4-DEV →](../v0.7.4-DEV/CHANGELOG.md)

---

## Overview

Three systems land and **Cops N Crooks ships as feature-complete**. A
stationary **Trader NPC** brings a full buy / barter / sell / tip economy
to the street, built on a new shared **shop-api** framework. A **Banker
NPC** takes deposits, issues withdrawals, and gates a tier ladder that
controls your max balance, daily cap, interest, and weekly / monthly
loans. And **bail** finally closes the arrest loop — handcuff → jail →
pay-your-way-out (or serve your time and walk).

Alongside those, the release ships a real YAML validation layer, a debug
logging block, Vault permissions integration, loot-chest polish, and a
wave of command ergonomics (tab-completion fixes, a `/gangland` alias,
optional player targets on `bank` commands). One feature shipped and was
rolled back inside the cycle — see the Ambiguous list.

---

## 📄 Configuration Changes

Upgrading from 0.7.4? You'll need to merge in new files and new
`settings.yml` sections — or regenerate them by bumping `Config_Version`.

**New files shipped in this version:**

| File                    | Purpose                                                                |
|-------------------------|------------------------------------------------------------------------|
| `npc/trader_traits.yml` | Trader personality catalogue (mood rates, barter rules, max health).   |
| `npc/bank_tiers.yml`    | Banker tier ladder — balance cap, daily cap, interest, loans per tier. |
| `shop/<shop-key>.yml`   | One file per shop. Managed through the in-game admin GUIs.             |

**New sections added to `settings.yml`:** `Debug` (module-level debug
logging toggle), a rewritten `User.Bank` block (rolling-window caps,
rename fee), and a new top-level `Detainment` block with nested
`Transit`, `Break_Free`, `Handcuff_Bribe`, `Bail`, `Jail_Bribe`,
`Sentence`, and `Sounds` sub-blocks.

**Removed sections:** per-account bank knobs that are now tier-scoped
(max balance, daily deposit limit, interest rate, death-loss discount,
weekly / monthly loan amounts) — they live in `bank_tiers.yml` instead.

**Required YAML validation:** every loader now rejects unknown keys and
reports a line-accurate error. If you'd been silently carrying dead keys
from earlier versions, expect a first-boot warning.

---

## ✨ Features

### Trader NPCs

A damageable, stationary Citizens NPC that runs a full shop. Placed via
an admin command, bound to a **shop key** and a **trait** from
`trader_traits.yml`. Right-click opens a buy view; the trader also
supports **barter** (item-for-item swap, no money involved), **sell**
(you hand items to the trader for cash), and **tip** (spend money to
improve the trader's mood toward you).

- **Six built-in traits** — `easygoing`, `hotheaded`, `stubborn`,
  `generous`, `shrewd`, `timid` — each with its own mood rates, barter
  policy, sell-price ratio, and HP. Define your own by adding a new
  top-level id.
- **Per-player mood** — mood is a **positive-only** scalar. Tipping and
  successful purchases both push mood up; there's no anger / negative
  track by design. At max mood the trader gives the configured
  `Min_Friend_Discount` on buys.
- **Barter is a pure item swap** — no money ever changes hands on a
  barter. The trader values incoming items against the category rates
  set in the shop file and accepts / rejects based on the trait's
  `Barter_Price_Ratio`. Turn it off per-trait with `Allows_Barter: false`.
- **Sell** — per-category valuation via `Sell_Price_Ratio`. Players
  can liquidate loot they don't want; server owners tune how generous
  each trait is per category.
- **Item freshness via decorate, not NBT** — newly-delivered shop items
  are re-decorated on every purchase, so configured lore / display
  names refresh even if an old YAML shape changed in between.
- **Admin editing in-game** — no YAML wrestling needed day-to-day.
  `/glw trader edit shop <key>` retargets the trader in your crosshair,
  `edit trait <id>` changes the personality, `edit name` opens an anvil
  to rename (spaces supported), `remove` deletes the trader you're
  looking at.
- **Trader is damageable by default** — `Invulnerable: false` on the
  trait makes the NPC killable so you can actually rob a shop. Default
  traits ship invulnerable; `stubborn` is the example "killable" trait.
- **Persistent repository** — traders are saved by the plugin, **not
  by Citizens' `saves.yml`** (`SHOULD_SAVE = false` at spawn). One
  source of truth.

**New file — `trader_traits.yml` shape:**

```yaml
easygoing:
   Display_Name: "Easygoing"
   Mood_Per_Tip_Currency: 0.0008   # mood gain per currency unit spent on a tip
   Mood_Per_Purchase: 0.03         # mood gain on a successful buy
   Min_Friend_Discount: 0.92       # price multiplier at mood = +1 (0.92 = 8% off)
   Allows_Barter: true
   Sell_Price_Ratio: 0.55          # fraction of ask the trader pays when BUYING from the player
   Barter_Price_Ratio: 1.0         # barter credit per item (1.0 = parity; defaults to Sell_Price_Ratio)
   Max_Health: 20.0                # half-hearts
   # Invulnerable: false           # (optional) omit for default-invulnerable traits
```

**New commands:**

```
/glw trader create <shopKey> <traitId> [displayName]
/glw trader edit shop <shopKey>
/glw trader edit trait <traitId>
/glw trader edit name
/glw trader remove
```

---

### Shop API Framework

The shop UI, persistence, and transaction logic were extracted into a
new **`gangland-ui/shop-api`** module so any future shop surface — not
just traders — can reuse them.

- **Per-shop YAML files** under `plugins/<plugin>/shop/<key>.yml`,
  scanned on boot and hot-reloaded through the standard
  `/glw reload` pipeline.
- **Typed transaction returns** — `PurchaseResult`, `BarterResult`,
  `SellResult` replace the old boolean-plus-enum mess. Each carries a
  clean reason enum plus the outcome payload (item stack delivered,
  money debited, etc.).
- **Shop-level messaging contract** — `ShopMessageContract` and
  `TraderMessageContract` route every user-facing string back through
  the `Messages` enum, so every shop / trader line is translatable
  without feature-module imports.
- **Admin GUIs** — shop definitions, buy entries, sell categories, and
  per-category barter / sell pricing are all editable in-game. Edits
  round-trip to YAML via `ShopYamlWriter` with zero restarts.
- **Shop entries deliver per-copy stacks** — a buy entry's
  `template.amount` is one *copy*; the `BUY AMOUNT` picker counts
  copies, not items. Multi-item copies (e.g. a stack of 5 arrows) are
  supported.

**New admin command:**

```
/glw shop remove
```

---

### Banking & Banker NPC

Every player gets an **account balance** (cash in hand) and a **bank
balance**. 0.7.5 turns the bank from a cash drop into a full ladder with
a dedicated NPC.

- **Banker NPC** — a stationary Citizens NPC that opens the bank UI on
  right-click. Create / delete via a command; identical-looking Banker
  NPCs can be scattered across your map without each one needing its
  own config.
- **Tier ladder** — four default tiers (`Basic`, `Premium`, `Elite`,
  `Vault`) gate max balance, daily deposit cap, interest rate,
  death-loss discount, and weekly / monthly loan drops. Every Banker
  NPC offers the same ladder — there's no per-NPC pricing.
- **Rolling-window daily cap** — deposits and withdrawals use a rolling
  window, not midnight, so splitting a transaction across midnight can
  never count against two separate days.
- **Interest** — accrued continuously as a fraction of bank balance
  per 24h, clamped at `Max_Balance`.
- **Weekly / monthly loans** — free currency deposited into the bank on
  a fixed cadence per tier (`Weekly_Loan_Amount`, `Monthly_Loan_Amount`).
- **Death-loss discount** — tier-scoped fraction subtracted from the
  death-tax deduction (so higher tiers take a smaller hit on death).
- **Rename fee** — charged from cash every time a player renames their
  account at a Banker. `0` disables it.
- **BigDecimal money handling** — price arithmetic on bank operations
  now uses `BigDecimal`, so large balances stop suffering from
  floating-point drift.
- **Admin reset** — `/glw bank resetcap <player|all>` wipes the
  rolling-window cap for one player or everyone.
- **Optional player targets on every bank command** —
  `/glw bank deposit <amount> [player]` /
  `/glw bank withdraw <amount> [player]` credit / debit another
  player's bank as admin actions.

**New file — `npc/bank_tiers.yml` shape:**

```yaml
Basic:
   Display_Name: "&7Basic Account"
   Max_Balance: 100_000
   Upgrade_Cost: 0                # first tier should be 0
   Order: 0                       # ascending; lower = earlier in the ladder
   Daily_Deposit_Limit: 10_000    # 0 = uncapped
   Interest_Rate: 0.0             # fraction per 24h (0.015 = 1.5%/day)
   Death_Loss_Discount: 0.10      # 0–1; fraction subtracted from death tax
   Weekly_Loan_Amount: 500        # 0 = disabled
   Monthly_Loan_Amount: 2_000

Elite:
   Display_Name: "&6Elite Account"
   Max_Balance: 10_000_000
   Upgrade_Cost: 500_000
   Order: 2
   Daily_Deposit_Limit: 1_000_000
   Interest_Rate: 0.0075
   Death_Loss_Discount: 0.50
   Weekly_Loan_Amount: 10_000
   Monthly_Loan_Amount: 50_000
```

**Add to `settings.yml`:**

```yaml
User:
   Bank:
      Initial_Balance: 0
      Create_Cost: 5_000
      Rename_Fee: 1_000           # charged from cash on every rename; 0 disables
      Reset_Period: 86_400        # rolling-window length in seconds
```

---

### Bail — Closing the Arrest Loop

Handcuff → jail now has a paid way out. When a player is jailed, the
paperwork view exposes a **Pay Bail** action that charges the player's
balance, restores their seized inventory, and releases them.

- **Cost scales with wanted level** —
  `Bail.Base_Cost + (wanted_level * Bail.Per_Wanted_Level)`. A
  one-star arrest pays base; a five-star fugitive pays a lot more.
- **Inventory restoration** — a successful bail routes through the
  same `ReleasePipeline` that a served sentence or admin-release uses,
  so seized items come back intact.
- **Insufficient-funds path** — `BailResult.INSUFFICIENT_FUNDS` is
  surfaced as a distinct message; no partial charge.
- **Handcuff bribe is separate** — while handcuffed (pre-jail) you can
  still bribe the arresting cop; that's `Handcuff_Bribe`, with its own
  `Base_Cost` / `Per_Wanted_Level`. Bail is post-jail only.
- **Jail bribe** — a risky shortcut from inside the cell. Pay the cost
  and roll against `Success_Chance`; on failure you eat a
  `Fail_Penalty_Seconds` sentence extension.
- **Sentence** — if you pay nothing, serving `Base_Seconds +
  wanted * Per_Wanted_Level_Seconds` releases you automatically.

**Add to `settings.yml`:**

```yaml
Detainment:
   Transit:
      Delay_Ticks: 400
      Guard_Radius: 5.0
   Break_Free:
      Taps_Required: 25
      Reset_Window_Ticks: 40
   Handcuff_Bribe:
      Base_Cost: 500.0
      Per_Wanted_Level: 250.0
   Bail:
      Base_Cost: 2500.0
      Per_Wanted_Level: 1000.0
   Jail_Bribe:
      Base_Cost: 1000.0
      Per_Wanted_Level: 500.0
      Success_Chance: 0.35
      Fail_Penalty_Seconds: 60
   Sentence:
      Base_Seconds: 180
      Per_Wanted_Level_Seconds: 60
   Fallback_Exit_Waypoint: "spawn"
   Sounds:
      Bail_Success: "BLOCK_NOTE_BLOCK_PLING"
      Bribe_Success: "ENTITY_VILLAGER_YES"
      Bribe_Fail: "ENTITY_VILLAGER_NO"
      Transit_Commit: "BLOCK_IRON_DOOR_CLOSE"
      Sentence_Complete: "BLOCK_BELL_USE"
```

---

### Loot Chest Polish

Loot chests got a big quality pass.

- **Item preview** — the chest advertises what it's holding before you
  open it. No more blind clicks.
- **Floating key / lockpick** — the required unlock item hovers above
  the chest as a plain item (no more armor-stand placeholder), so the
  visual lines up with how the game actually renders custom items.
- **Drop chances replace tier requirement** — per-table
  `Rarity_Overrides` and per-item `Drop_Chance` buckets (`COMMON`,
  `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`) decide what rolls. Tier
  gating still controls *which table* a chest draws from; it no longer
  gates individual items inside a table.
- **Natural drops** — weapons rolled from a chest now drop as real
  weapon items (not placeholder renders).
- **Hologram drop for weapons** — weapon drops float under a hologram
  showing the weapon's display name.
- **Repair system deleted** — the loot-chest repair path was removed.
  If you were relying on it, wire it back through the shop-api sell
  flow or a custom handler.
- **Validation layer** — the chest loader now reports malformed
  entries with a line number instead of silently skipping them.

---

### Civilians

Small but load-bearing.

- **Per-type drop chances** — civilians now roll their drop table with
  the same `Drop_Chance` semantics as loot chests, so a friendly
  villager and a hostile mercenary don't drop with the same
  probability.
- **Inventory field removed** — non-trader civilians no longer carry a
  Bukkit inventory. Trader behavior moved to the dedicated Trader NPC
  (above). If your `civilians.yml` had `Inventory:` on a type, it's
  now an unknown key and the new validation layer will flag it —
  delete the line.
- **Stray-entity despawn fix** — civilians and cops with no active
  player were sometimes left alive past the despawn radius. Now
  cleaned up on the same 3D proximity check.

---

### Weapons & Projectiles Polish

- **Flame particle** added to incendiary hits — the cone-spray visual
  is more obviously *fire* now.
- **Natural drops** across weapon spawns (hologram + item, not
  armor stands).
- **Weapon drop holograms** — dropped weapons float under a name
  hologram so players can identify loot at a glance.

---

### Commands & Permissions

- **New `/glw permissions` command** — list / inspect / grant / revoke
  permissions directly in-game. Splits cleanly from the rank command.
- **Rank permission subcommands split** —
  `/glw rank add permission` and `/glw rank remove permission` are now
  separate subcommands. Tab-completion routes by verb; no more
  positional confusion.
- **Vault permission integration** — Vault (soft-dep) is detected and
  used for permission checks when present. If Vault isn't installed,
  the built-in permission system handles everything on its own.
- **`/gangland` alias for `/glw`** — both resolve to the same dispatcher.
- **Legacy commands revamped** — the old flat-args subcommands were
  rewritten as chained optional-argument nodes, so every positional
  slot has a tab-completion suggestion list. No more terminal
  `args[n]` reads that silently fall through.
- **Help commands fixed** — the `/glw <cat> help <page>` surfaces
  (bank, jail, cop, trader, car, money, civilian, waypoint, rank…)
  now paginate correctly. Previously some pages rendered blank.
- **Tab-completion polish** — trailing-space bug fixed, per-argument
  suggestions align with what actually parses.
- **Optional player targets** — `bank deposit` / `bank withdraw` now
  accept an optional `[player]` that admins can target for credit /
  debit.
- **Delete-confirm command fixed** — the two-step confirmation flow on
  destructive commands (gang delete, shop remove, etc.) was resetting
  its pending state on a race; now stable.
- **Custom messages for every command** — every user-facing command
  string now routes through the `Messages` enum. Previously-hardcoded
  strings are removed.

---

### YAML Validation Layer

Every loader in the plugin now runs through a shared validator.

- **Unknown-key detection** — a key that isn't in the declared schema
  gets reported with its line number. Catches dead keys left over
  from upgrades.
- **Required-key / missing-node reporting** — a missing section still
  falls back to defaults (same as before) but now logs that the
  fallback was used, so you can re-add the section on the next edit.
- **Depth tracking** — nested paths like `Cops.Behaviour.Cuff_Radius`
  resolve and validate correctly without custom code in every loader.
- **Fallback message loading from the jar** — if `message/*.yml` is
  missing or corrupt, the loader falls back to the bundled copy
  inside the jar instead of hard-failing.
- **Missing configuration nodes added** — the defaults that were
  loaded silently in prior versions are now explicitly documented in
  the files so `/glw reload` reports them accurately.

---

### Debug Logging

Module-level debug logging is now a first-class config surface instead
of a JVM-flag dance.

- `Debug.Enabled: true` + `Debug.Modules: [<module>, ...]` flips log
  levels at runtime. Restart the server after editing.
- Valid module names: `Gangland`, `Gangland Core`, `Gangland Weapons`,
  `Cops N Crooks`, `Inventory API`, `Lootchest API`, `Scoreboard API`,
  `Sign API`, `Plugin Persistence`, `Version Handler`. Empty list =
  every module on the classpath.
- **Startup logs demoted to `DEBUG`** — your console is quiet on
  normal boot. Flip the module list to see the old chatter.
- **Logging infrastructure moved to `plugin-common`** — every module's
  logger setup is now centralized.

**Add to `settings.yml`:**

```yaml
Debug:
   Enabled: false
   Modules: []
```

---

### Vehicle Persistence

- **Dismount on shutdown** — mounted players were being serialized
  into `player.dat` as a `RootVehicle`, which spawned rogue duplicate
  vehicles on rejoin. `PlayerQuitEvent` now ejects riders first, and
  the destroy-all runs in two passes so no entity is left behind.
- **Half-block vertical traversal** — vehicles now climb slabs and
  half-blocks smoothly. Previously they caught on the lip.
- **Session cleanup ordering** — vehicle session save runs before
  close, not after, so a server restart never loses the last known
  position.

---

### Internal — Module Reorg & Beans Cleanup

Not user-facing, but flagged here for anyone shipping custom modules
against this version.

- Listener and command packages moved inside the `bean` package for
  consistency with `@ListenerHandler` / `@CommandHandler`.
- Autowire package folded under `bean`.
- `gangland-core` main package renamed to `me.luckyraven.core`.
- `BankerViewOpener` contract removed — the view is specified directly
  at the call site, dropping an unnecessary indirection.
- Trader trait loader moved onto the shared bean lifecycle.
- Input interceptors abstracted so anvil-GUI / chat-prompt sessions
  share the same cancellation pipeline.
- Test suite expanded before shipping (repository lifecycle,
  cleanup on Windows, HikariCP init).

---

## 🐛 Bug Fixes

### Trader & Bank

- **Users without a bank** — newly-created users without a bank row
  were NPE'ing the Banker view. Now lazy-created on first right-click.
- **Trader invulnerable tag** — the `Invulnerable: false` trait
  wasn't propagating to the Citizens NPC, so "killable" traits
  weren't actually killable. Fixed by setting both the Citizens
  protected flag and the Bukkit `LivingEntity` invulnerable flag at
  spawn.
- **Bargain / tip views dropped** — per the trader simplification,
  bargain and tip-view GUIs were removed and their bug list with them.
  Tipping is still supported via command / shop integration; the
  dedicated anvil flow is gone.
- **Item moved from inventory into created inventory** — a bug where
  shift-clicking into a newly-opened shop view dropped the item into
  the wrong slot is fixed.

### Jail & Detainment

- **Handcuff → jail → release flow** — the release leg occasionally
  left seized inventory in limbo. Bail, sentence-served, and
  admin-release now all route through the same `ReleasePipeline`.

### Loot Chests

- **Loot chest validation** — malformed entries previously crashed or
  silently skipped; now reported with file + line number.
- **Icon swap** — the placeholder armor stand used for the
  "hold-to-open" item was replaced with a proper Bukkit item, so
  resource packs render it consistently.

### Commands

- **Delete-confirm command** — the two-step confirmation was racing
  its own state reset.
- **Help commands** — pages beyond the first weren't rendering for
  several categories; fixed.
- **Tab-completion** — trailing-space bug and a mis-ordered suggestion
  list on some subcommands.

### System & Reliability

- **Gang deletion database consistency** — deleting a gang now
  cascades cleanly: members and alliances get orphan-handling passes
  so stale rows don't accumulate.
- **Reload periodical updates** — the periodic auto-save and cache
  cleanup weren't re-registering after `/glw reload`. Now rebuilt on
  every reload.
- **Stray civilians / cops despawn** — NPCs that were out of range
  weren't always cleaned up.
- **Vehicle dismount on shutdown** — see the Vehicle section above —
  mounts were being serialized into the player save file and spawning
  duplicates on rejoin.
- **Language loader** — a missing `message/*.yml` used to hard-fail
  startup; now falls back to the bundled copy inside the jar.
- **Filename typo in prior changelogs** — the 0.7.3 / 0.7.4 files
  shipped with an inconsistent filename casing; standardized.
- **Bean ordering race** — some beans were being resolved before their
  graph dependencies had finished initializing. Topological-sort pass
  hardened.
- **Naming-convention sweep** — internal file / class names aligned
  to the project's house convention (`Capitalized_Underscore`
  YAML keys, package paths match module names).

---

## ⚠️ Possibly Fixes / Ambiguous Changes

Commits whose intent didn't obviously map to either a feature or a fix.
Flagged so server owners can spot behavior changes in their own setups.

- **"Added a market system"** → **"Removed the market system"** — a
  player-to-player market / auction-house prototype shipped and was
  rolled back inside this cycle. It may have left behind
  `gangland-market` module traces or stale SQL tables if you ran a
  development build between those two commits. **"Moved economy and
  bank to gangland-market"** is part of the same arc — the economy
  and bank code briefly lived under `gangland-market`, then moved
  back when the market was cut. If you drifted from mainline during
  the cycle, diff your database schema and module list against the
  0.7.5 default.
- **"Removed the trade in"** — the trade-in variant of the trader
  sell flow was cut after landing. The remaining sell path is
  category-based valuation through `Sell_Price_Ratio`. If you had
  placeholder text, messages, or admin workflows wired to trade-in,
  they no longer resolve.
- **"Removed Bargin and tip views"** — per the trader simplification,
  the anvil-GUI bargain flow, the dedicated tip view, and the
  propose-price widget were removed. The trait record dropped from
  16 fields to 8. Tipping still works as a *currency-spent* signal
  for mood, but there's no standalone tip GUI anymore.
- **"Removed the repair system"** — the loot-chest repair path went
  away. If any of your shop templates still reference repair-kit
  materials, they'll parse as ordinary items.
- **"Removed civilian inventory"** — non-trader civilians lost their
  `Inventory:` field. Existing `civilians.yml` files with the key
  will trigger the new unknown-key validator.
- **"Changed the default values"** — one commit tweaked defaults
  across loaders without specifying which. If you upgrade by
  regenerating `settings.yml` / catalogue files, diff against a
  backup of your prior version so intentional overrides survive.
- **"Removed the banker view opener contract so that you directly
  specify it"** — API surface change for anyone wiring a custom
  banker view. The view is now specified inline at the call site.
- **"Added a bean lifecycle for trader trait loader"** — traits now
  participate in `/glw reload`. If you had custom code reloading
  traits manually, remove it — the framework does it for you.
- **"Added moving vertically on half blocks or less"** — vehicle
  movement change; watch for slab-heavy parkour tracks behaving
  differently.
- **"Added hologram drop for weapons"** — weapon spawns are now
  accompanied by a floating display name. If you were relying on
  entity-count-based triggers near weapon drops, you'll see one more
  entity per drop.

---

## Requirements

No new hard dependencies compared to v0.7.4-DEV.

| Requirement       | Details                                              |
|-------------------|------------------------------------------------------|
| Minecraft version | 1.20+ (1.21.x recommended)                           |
| Java              | Java 21 or newer                                     |
| Required plugins  | NBTAPI, Citizens                                     |
| Optional plugins  | PlaceholderAPI, Vault, ViaVersion                    |
| Server platform   | **Spigot** (not Paper — Paper-only APIs aren't used) |

---

[← Back to Documentation Index](../README.md) | [Previous: v0.7.4-DEV →](../v0.7.4-DEV/CHANGELOG.md)
