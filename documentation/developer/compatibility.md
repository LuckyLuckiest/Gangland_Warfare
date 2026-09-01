# Version Compatibility

[Back to Developer Docs](./README.md)

---

## Overview

Gangland Warfare supports Minecraft servers 1.16 and newer using the Adapter pattern.
Each server revision has its own module that provides NMS (net.minecraft.server) implementations
for version-specific operations -- primarily weapon recoil effects that require sending raw
packets to rotate the player's camera.

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
                    │  gangland-impl   │
                    │  (RecoilManager) │
                    ╰────────┬─────────╯
                             │ uses
                    ╭────────┴─────────╮
                    │   version-impl   │
                    │  (Compatibility) │
                    │  (interface)     │
                    ╰────────┬─────────╯
                             │ implemented by
              ╭──────────────┼──────────────╮
              │              │              │
    ╭─────────┴──╮  ╭────────┴────╮  ╭──────┴───────╮
    │version-1_16│  │version-1_18 │  │version-1_21  │
    │  _R1       │  │  _R2        │  │  _R7         │
    ╰────────────╯  ╰─────────────╯  ╰──────────────╯
         ...             ...              ...
                  (20 modules total)
```

### Compatibility Interface

```java
public interface Compatibility {
	RecoilCompatibility getRecoilCompatibility();
}
```

Each version module provides a `VersionImplementation` class that implements this interface.

### RecoilCompatibility Interface

```java
public interface RecoilCompatibility {
	void applyRecoil(Player player, float pitch, float yaw);
}
```

Each version module implements this with the correct NMS packet construction for that version.

---

## Version Detection

### CompatibilitySetup

At plugin load time, `CompatibilitySetup` detects the running server version:

```
Server starts
    │
    ├── Get server package version string
    │   e.g., "org.bukkit.craftbukkit.v1_21_R3"
    │
    ├── Extract NMS version: "v1_21_R3"
    │
    ├── Map to module class:
    │   "org.luckyraven.gangland.compatibility.v1_21_R3.VersionImplementation"
    │
    ├── Instantiate via reflection
    │
    ╰── Store as active Compatibility instance
```

If the version is not supported, the compatibility layer is null and recoil effects are
silently disabled.

### VersionSetup

Runs before `CompatibilitySetup` to detect the Minecraft version number (e.g., `1.21.3`)
for feature gating and API compatibility decisions.

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

Servers up to 1.20.4 are detected directly from the versioned CraftBukkit package name.
Since 1.20.5 the CraftBukkit package is no longer versioned, so the `Version` enum in
`version-impl` maps the reported Bukkit version to the correct adapter (including Spigot
revisions that share one CraftBukkit revision) — that is why the enum only lists 1.20.5+
entries even though older servers are supported. Players on clients older than the server
are handled by ViaVersion/ViaBackwards.

---

## How Recoil Works

### The Recoil Pipeline

```
Player fires weapon
    │
    ├── WeaponInteract detects left-click
    ├── WeaponService.handleShoot() called
    ├── Weapon config has recoil values (pitch, yaw)
    │
    ├── RecoilManager.applyRecoil(player, pitch, yaw)
    │     │
    │     ├── Get active Compatibility instance
    │     ├── Get RecoilCompatibility from it
    │     ╰── recoilCompat.applyRecoil(player, pitch, yaw)
    │           │
    │           ├── [NMS] Create position/rotation packet
    │           ├── [NMS] Set relative pitch and yaw offsets
    │           ╰── [NMS] Send packet to player's connection
    │
    ╰── Player camera rotates by (pitch, yaw) offset
```

### NMS Implementation (Example: 1.21)

For modern versions (1.17+), the implementation typically looks like:

```java
public class RecoilCompatibilityImpl implements RecoilCompatibility {

	@Override
	public void applyRecoil(Player player, float pitch, float yaw) {
		// Get CraftPlayer handle
		ServerPlayer serverPlayer = ((CraftPlayer) player).getHandle();

		// Create relative position packet (flags indicate relative values)
		ClientboundPlayerPositionPacket packet = new ClientboundPlayerPositionPacket(
				0, 0, 0,       // x, y, z (0 = no movement)
				yaw,            // yaw offset
				pitch,          // pitch offset
				RELATIVE_FLAGS, // all values are relative
				0               // teleport ID
		);

		// Send packet
		serverPlayer.connection.send(packet);
	}
}
```

For older versions (pre-1.17), the NMS package names differ:

```java
// 1.16 and earlier use versioned NMS packages

import net.minecraft.server.v1_16_R3.PacketPlayOutPosition;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
```

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

The plugin optionally integrates with ViaVersion to detect the client protocol version
of connecting players. This is used primarily by the scoreboard system to select the
correct packet format for scoreboard updates.

```java
// In Gangland.java
ViaAPI<?> viaAPI;  // null if ViaVersion not present
```

This allows the plugin to handle players connecting with different client versions on
a single server (e.g., a 1.21 server accepting 1.20 clients via ViaVersion).

---

## Adding Support for a New Version

### Step 1: Create the Module

Create a new directory:

```
gangland-compatibility/version-X_XX_RX/
  ├── pom.xml
  ╰── src/main/java/me/luckyraven/compatibility/vX_XX_RX/
        ├── VersionImplementation.java
        ╰── RecoilCompatibilityImpl.java
```

### Step 2: pom.xml

```xml

<project>
    <parent>
        <groupId>org.luckyraven.gangland</groupId>
        <artifactId>gangland-compatibility</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>version-X_XX_RX</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.luckyraven.gangland</groupId>
            <artifactId>version-impl</artifactId>
            <version>${project.parent.version}</version>
        </dependency>
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot</artifactId>
            <version>X.XX.X-RX-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

### Step 3: Implement Compatibility

```java
package org.luckyraven.gangland.compatibility.vX_XX_RX;

public class VersionImplementation implements Compatibility {

	private final RecoilCompatibility recoilCompatibility = new RecoilCompatibilityImpl();

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}
}
```

### Step 4: Implement Recoil

```java
package org.luckyraven.gangland.compatibility.vX_XX_RX;

public class RecoilCompatibilityImpl implements RecoilCompatibility {

	@Override
	public void applyRecoil(Player player, float pitch, float yaw) {
		// Use the correct NMS classes for this version
		// Send position packet with relative pitch/yaw
	}
}
```

### Step 5: Register in Parent POM

Add to `gangland-compatibility/pom.xml`:

```xml

<module>version-X_XX_RX</module>
```

And to the root `pom.xml`:

```xml

<module>gangland-compatibility/version-X_XX_RX</module>
```

### Step 6: Update CompatibilitySetup

Add the new version string to the version detection map in `CompatibilitySetup` so it
maps to the correct `VersionImplementation` class.

---

## Troubleshooting

| Issue                        | Cause                                 | Solution                           |
|------------------------------|---------------------------------------|------------------------------------|
| No recoil on fire            | Version not recognized                | Check `CompatibilitySetup` mapping |
| `ClassNotFoundException`     | NMS classes changed in new MC version | Create new version module          |
| `NoSuchMethodError`          | NMS method signature changed          | Update `RecoilCompatibilityImpl`   |
| Recoil works but feels wrong | Packet flags not set to relative      | Check relative flags in packet     |
