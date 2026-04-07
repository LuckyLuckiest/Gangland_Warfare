# v0.7.4-DEV Changelog

[Back to Documentation Index](../README.md)

---

## Overview

Version 0.7.4-DEV focuses on NPC combat intelligence, experience validation, and code quality
improvements. This update introduces smarter cop and civilian NPC combat behaviors and fixes
several edge cases in the weapon and reload systems.

---

## Changes

### NPC Combat AI

- **Smarter NPC combat behaviors** -- cops and civilians now make more intelligent decisions
  during combat, including better target selection and engagement timing
- **Experience reward validation** -- XP rewards from combat are now validated before being
  applied
- **Wanted level increment for non-hostile combat civilians** -- attacking peaceful civilians
  now correctly increments the player's wanted level

### Weapon & Reload System

- **Validated player shooter condition** -- weapon firing now validates the shooter state
  before allowing shots
- **Downed player reload check** -- downed players can no longer initiate weapon reloads
- **Better reload handling with NPCs** -- NPC weapon reloads are now handled more reliably

### Bug Fixes

- **Jail creation proximity check** -- jail locations are now checked within a 5-block radius
  instead of requiring exact location matches, preventing near-duplicate jails
- **Civilian trader inventory** -- trader NPCs now use the custom `InventoryHandler` framework
  instead of raw Bukkit inventories, ensuring consistent behavior with the rest of the plugin's
  GUI system

### Internal

- **Developer documentation** -- added comprehensive technical documentation covering
  architecture, all modules, persistence layer, command system, weapon internals, NPC AI,
  gadget system, UI framework, version compatibility, and configuration reference
- **Dependency update** -- cops-n-crooks module now depends on inventory-api for custom
  inventory support

---

## Commit History

| Commit    | Description                                                       |
|-----------|-------------------------------------------------------------------|
| `f5b12c4` | Introduced smarter NPC combat behaviors                           |
| `cb2914d` | Added experience reward validation and wanted level for civilians |
| `f422c1e` | Validated player shooter condition                                |
| `422cd09` | Added downed player check to prevent reloading                    |
| `936c658` | Better reload handling with NPCs                                  |

---

## Requirements

No new requirements compared to v0.7.3-DEV. All existing dependencies remain the same:

| Requirement       | Details                    |
|-------------------|----------------------------|
| Minecraft version | 1.20+ (1.21.x recommended) |
| Java              | Java 21 or newer           |
| Required plugins  | NBTAPI, Citizens           |
| Optional plugins  | PlaceholderAPI, Vault      |
