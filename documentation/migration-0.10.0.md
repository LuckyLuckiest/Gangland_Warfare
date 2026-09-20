# Migrating a server from 0.9.2 to Gangland 0.10.0

[← Back to Documentation Index](./README.md)

Gangland 0.10.0 is the decoupling wave: the plugin API bumps to a new major line (`Host_Api: 2.0`, see §2), and
Gangland-owned systems start moving out into standalone plugins or Keystone modules. This page grows one section
per gate as the wave lands; the first section (WS1) is below.

## WS1 — the scoreboard is now its own plugin, Plaque

Gangland Warfare no longer has any scoreboard code, config or command. Scoreboard rendering moved to a standalone
sibling plugin, **Plaque** (`E:\Programming\java\Plaque`), which has no dependency on Gangland at all and renders
the same board through PlaceholderAPI `%gangland_*%` tokens.

### 1. Install Plaque

Drop `Plaque-<version>.jar` into `plugins/`, alongside `Gangland_Warfare-<version>.jar` and `Keystone-<version>.jar`
(Plaque only needs Keystone — it works without Gangland installed too, showing literal `%gangland_*%` text instead
of live gang/user data). No `Host_Api` or `Depends:`/`Plugins:` wiring is involved; Plaque is not a Gangland
runtime module, it is a separate plugin.

### 2. Copy `scoreboard.yml`

Plaque's `scoreboard.yml` uses the **identical schema** Gangland's old file used
(`Board.Title.{Interval,Lines}` / `Board.Rows.<n>.{Interval,Lines}`). Copy your existing
`plugins/Gangland_Warfare/scoreboard.yml` to `plugins/Plaque/scoreboard.yml` and it loads unchanged — no key
renames, no reformatting.

### 3. Carry the two `Scoreboard:` settings values into Plaque's own `settings.yml`

Gangland's deleted `settings.yml` `Scoreboard:` block had two keys. Both move to Plaque's own `settings.yml`, at
the top level (not nested under a `Scoreboard:` section there):

| Old (`Gangland_Warfare/settings.yml`) | New (`Plaque/settings.yml`) |
|---|---|
| `Scoreboard.Enable` | `Enable` |
| `Scoreboard.Driver` | `Driver` |

Plaque ships only the `Driver_V3` renderer (Gangland's old `Driver_V1`/`Driver_V2` clustering algorithms were
retired in the split); an unrecognised `Driver:` value falls back to `Driver_V3` with a startup warning instead of
silently reproducing a deleted driver's behaviour.

### 4. Delete the leftover `Scoreboard:` block from Gangland's `settings.yml`

If you upgrade an existing `settings.yml` in place rather than regenerating it, a leftover `Scoreboard:` block is
**harmless but inert** — Gangland's settings loader reads sections by name (`Settings.populate()`'s
`section(root, "X", report)` calls), and nothing calls `section(root, "Scoreboard", report)` any more, so the
block is parsed into memory and never visited. It causes no fault and no warning; it is simply dead weight worth
deleting for a clean file.

### 5. What breaks if you skip Plaque entirely

Nothing errors. Gangland boots with no scoreboard of any kind — no fault line, no missing-dependency warning
(Plaque was never a Gangland dependency in either direction). Players simply see no scoreboard until Plaque is
installed.

## See also

- [`documentation/features/scoreboard.md`](./features/scoreboard.md) — the short in-repo pointer to Plaque.
- [`documentation/developer/configuration.md`](./developer/configuration.md) — the retired `scoreboard.yml`
  section, with the schema comparison.
- [`documentation/migration-0.9.2.md`](./migration-0.9.2.md) — the previous migration note (jetpack ownership,
  Bartizan going soft), unaffected by WS1.

<!-- Later 0.10.0 gates (WS2 inventory-api → keystone-inventory, WS3 lootchest/hologram, WS4 shop, WS5 gang
     module, WS6 api facade) append their own sections here as they land. -->
