# Version Compatibility

[Back to Developer Docs](./README.md)

---

## Overview

Gangland Warfare supports Minecraft servers 1.16 and newer using the Adapter pattern.
Each server revision has its own module that provides NMS (net.minecraft.server) implementations
for version-specific operations -- primarily weapon recoil effects that require sending raw
packets to rotate the player's camera.

Since Keystone 1.7.3, **version detection and adapter loading are Keystone's job**:
`org.luckyraven.keystone.nms.CraftBukkitRevision` resolves the running server's CraftBukkit
revision (versioned package suffix first, then Keystone's release→revision table for the
unversioned 1.20.5+ packages), and `VersionedAdapterLoader` reflectively constructs the matching
adapter class. Gangland keeps only its contract (`Compatibility` / `RecoilCompatibility`) and the
per-version NMS implementations.

The 1.16 floor matches Keystone's API floor (Spigot 1.16.5, `api-version: 1.16`), and the
whole stack compiles at Java release 17 to match Keystone, so the jar loads on every JVM
Keystone loads on. Players on older clients are supported through ViaVersion/ViaBackwards
on the server (recoil verified working through the Via pipeline).

**Module:** `gangland-compatibility`
**Interface Module:** `gangland-compatibility/version-impl`
**Adapter Modules:** `gangland-compatibility/version-1_16_R1` through `version-1_21_R7`

---

## Architecture

```
                    ╭──────────────────╮
                    │ gangland-weapon  │
                    │  (RecoilManager) │
                    ╰────────┬─────────╯
                             │ uses
                    ╭────────┴──────────╮
                    │   version-impl    │
                    │ Compatibility     │  ← contract interface
                    │ RecoilCompat.     │  ← Bukkit-API fallback base class
                    │ CompatibilityWorker│ ← resolves the adapter via Keystone
                    ╰────────┬──────────╯
                             │ loaded by revision name (Keystone VersionedAdapterLoader)
              ╭──────────────┼──────────────╮
              │              │              │
    ╭─────────┴──╮  ╭────────┴────╮  ╭──────┴───────╮
    │version-1_16│  │version-1_18 │  │version-1_21  │
    │  _R1       │  │  _R2        │  │  _R7         │
    ╰────────────╯  ╰─────────────╯  ╰──────────────╯
         ...             ...              ...
                  (20 modules total)
```

### The contract

```java
public interface Compatibility {
	RecoilCompatibility getRecoilCompatibility();
}
```

Each version module ships a class **named exactly after its CraftBukkit revision** (e.g.
`org.luckyraven.gangland.compatibility.version.v1_21_R7`) implementing `Compatibility`, plus a
`recoil.Recoil_1_XX_RY extends RecoilCompatibility` with the real NMS packet code.

### RecoilCompatibility (base class, not an interface)

```java
public class RecoilCompatibility {
	public void modifyCameraRotation(@NotNull Player player, float yaw, float pitch, boolean position)
}
```

The base implementation is the **fallback**: it rotates the camera through the Bukkit API
(`Player#setRotation`) instead of position packets. ViaVersion is consulted through a supplier
(the API only becomes available after the dependency handler runs); without ViaVersion the gate is
skipped — the 1.16 server floor already guarantees client rotation support.

---

## Version Detection (Keystone-owned)

`CompatibilityWorker` (a KERNEL bean) resolves the adapter once at bootstrap:

```
Server starts
    │
    ├── Keystone CraftBukkitRevision.current()
    │   ├── versioned CraftBukkit package (≤ 1.20.4): parse "v1_21_R3" from the package name
    │   ├── unversioned package (1.20.5+): Keystone's MC-release → revision table
    │   ╰── unknown release: latestKnown() + a nms.revision.unknown fault via Diagnostics
    │
    ├── Keystone VersionedAdapterLoader.loadOrFallback(Compatibility.class,
    │       "org.luckyraven.gangland.compatibility.version", fallback)
    │   ├── Class.forName(<package>.<revision>) + no-arg construction + cast
    │   ╰── failure: nms.adapter.missing / nms.adapter.instantiation fault via Diagnostics
    │
    ╰── no adapter → base RecoilCompatibility (Bukkit-API rotation, "limited functionality")
```

Adapter-load failures are no longer silent: they land in the Diagnostics sinks
(`gangland_faults`, recent-faults ring) with a stable fault code.

---

## Supported Versions

| Module            | Minecraft Version | NMS Revision |
|-------------------|-------------------|--------------|
| `version-1_16_R1` | 1.16.1            | `v1_16_R1`   |
| `version-1_16_R2` | 1.16.2-3          | `v1_16_R2`   |
| `version-1_16_R3` | 1.16.4-5          | `v1_16_R3`   |
| `version-1_17_R1` | 1.17.x            | `v1_17_R1`   |
| `version-1_18_R1` | 1.18-1.18.1       | `v1_18_R1`   |
| `version-1_18_R2` | 1.18.2            | `v1_18_R2`   |
| `version-1_19_R1` | 1.19-1.19.2       | `v1_19_R1`   |
| `version-1_19_R2` | 1.19.3            | `v1_19_R2`   |
| `version-1_19_R3` | 1.19.4            | `v1_19_R3`   |
| `version-1_20_R1` | 1.20-1.20.1       | `v1_20_R1`   |
| `version-1_20_R2` | 1.20.2            | `v1_20_R2`   |
| `version-1_20_R3` | 1.20.3-4          | `v1_20_R3`   |
| `version-1_20_R4` | 1.20.5-6          | `v1_20_R4`   |
| `version-1_21_R1` | 1.21-1.21.1       | `v1_21_R1`   |
| `version-1_21_R2` | 1.21.2-3          | `v1_21_R2`   |
| `version-1_21_R3` | 1.21.4            | `v1_21_R3`   |
| `version-1_21_R4` | 1.21.5            | `v1_21_R4`   |
| `version-1_21_R5` | 1.21.6-8          | `v1_21_R5`   |
| `version-1_21_R6` | 1.21.9-10         | `v1_21_R6`   |
| `version-1_21_R7` | 1.21.11+          | `v1_21_R7`   |

The 1.20.5+ release→revision mapping lives **in Keystone** (`CraftBukkitRevision`'s table), not in
this repo — Gangland deleted its old `Version` enum. Players on clients older than the server are
handled by ViaVersion/ViaBackwards.

---

## How Recoil Works

### The Recoil Pipeline

```
Player fires weapon
    │
    ├── WeaponInteract detects the fire click
    ├── Weapon config has recoil values (pitch, yaw)
    │
    ├── RecoilManager → RecoilCompatibility.modifyCameraRotation(player, yaw, pitch, position)
    │     │
    │     ├── [NMS adapter] Create relative position/rotation packet for this revision
    │     ╰── [NMS adapter] Send packet to the player's connection
    │     (fallback base: Bukkit Player#setRotation)
    │
    ╰── Player camera rotates by the (pitch, yaw) offset
```

### NMS Implementation (Example: modern versions)

```java
public class Recoil_1_21_R7 extends RecoilCompatibility {

	@Override
	public void modifyCameraRotation(Player player, float yaw, float pitch, boolean position) {
		ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();
		// build ClientboundPlayerPositionPacket with relative flags for yaw/pitch
		serverPlayer.connection.send(packet);
	}
}
```

Pre-1.17 versions use the versioned `net.minecraft.server.v1_16_RX.*` packages; 1.20.4+ modules
compile mojang-mapped and are remapped to the obfuscated runtime by the shared specialsource
pipeline in `gangland-compatibility/pom.xml`.

### Recoil Configuration

Each weapon defines its own recoil pattern:

```yaml
# Example weapon config
recoil:
   pitch: 2.5      # Vertical kick (positive = up)
   yaw: 0.3        # Horizontal drift
   recovery: 0.8   # Recovery speed multiplier
```

The `RecoilManager` may also apply modifiers:

- **Sneaking:** Reduced recoil (configurable multiplier)
- **Scoped:** Different recoil values while aiming

---

## ViaVersion Integration

The plugin optionally integrates with ViaVersion to detect the client protocol version of
connecting players — used by the scoreboard driver selection and by the fallback recoil's
rotation gate. `CompatibilityWorker` receives `gangland::getViaAPI` as a supplier because the API
is set by the dependency handler *after* the bean graph is built.

---

## Adding Support for a New Minecraft Version

1. **Keystone side (once per MC release):** add the release→revision entry to
   `CraftBukkitRevision`'s table (and bump `latestKnown()` if it is a new revision), bump the
   Keystone version, `mvn clean install`.
2. **New revision only — create the module** `gangland-compatibility/version-X_XX_RY/` with two
   classes under `org.luckyraven.gangland.compatibility.version`:
   - `vX_XX_RY implements Compatibility` (returns the recoil impl), and
   - `recoil.Recoil_X_XX_RY extends RecoilCompatibility` (the NMS packet code).
   The class name **must** equal the CraftBukkit revision — that is what the loader resolves.
3. **pom.xml:** copy a sibling module's pom; set `spigot.version`. For 1.20.4+ servers use the
   `remapped-mojang` classifier + the inherited specialsource plugin (see `version-1_21_R7`);
   older versions depend on the plain (obfuscated) `spigot` artifact with no build block.
4. **Register the module** in `gangland-compatibility/pom.xml`'s `<modules>` and as a dependency
   in `gangland-build/pom.xml` (the shade must carry it for the reflective load).
5. No detection code changes in Gangland — the loader picks the class up by name.

---

## Troubleshooting

| Issue                        | Cause                                 | Solution                                        |
|------------------------------|---------------------------------------|-------------------------------------------------|
| No recoil on fire            | No adapter for this revision          | Check the `nms.adapter.missing` fault; add the module / bump Keystone's table |
| "Using default recoil"       | Fallback engaged                      | Same as above — the Bukkit-API fallback still rotates, packets don't |
| `ClassNotFoundException`     | NMS classes changed in new MC version | Create new version module                       |
| `NoSuchMethodError`          | NMS method signature changed          | Update the `Recoil_X_XX_RY` implementation      |
| Recoil works but feels wrong | Packet flags not set to relative      | Check relative flags in packet                  |
