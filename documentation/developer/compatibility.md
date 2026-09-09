# Version Compatibility

[Back to Developer Docs](./README.md)

---

## Overview

**Gangland Warfare ships no NMS code of its own as of 0.9.0.** The 21 Maven modules that used to provide it
(`gangland-compatibility/version-impl` + 20 per-revision recoil adapters, `version-1_16_R1` through `version-1_21_R7`)
were deleted with the weapon module — recoil (the only reason those adapters existed) is now Bartizan's problem,
implemented as a single **reflective Keystone call**, not a per-version Gangland artifact.

Weapon fire (and therefore recoil) lives entirely in the standalone **Bartizan** plugin. Its `RecoilManager` calls:

```java
PacketBridge.adapter().relativeCameraRotation(player, -yaw + 1, pitch - 1);
```

`org.luckyraven.keystone.nms.PacketAdapter.relativeCameraRotation(Player viewer, float deltaYaw, float deltaPitch)`
is a **`default` method** on Keystone's existing `PacketAdapter` interface (`keystone-common`, since Keystone 1.9.0)
— adding it broke no existing implementor. `PacketBridge.install(PacketAdapter)` / `.adapter()` / `.reset()` is the
same lazily-resolved holder pattern used elsewhere in this codebase (never cached by the caller across calls).
`org.luckyraven.keystone.nms.internal.ReflectivePacketAdapter` is Keystone's real implementation: it reflects
against whichever NMS position-packet constructor shape the running server has (see "Historical: the three
position-packet shapes" below) and builds a zero-movement, relative-rotation-only packet. When no known shape
resolves (a server revision Keystone has never seen), it reports the fault `nms.recoil.unsupported` once and falls
back to the Bukkit API (`Player#setRotation`) — the same fallback shape the old per-version `RecoilCompatibility`
base class used, just now living in Keystone instead of 20 Gangland modules. `setRotation` teleports and feels
rough on older clients; it is the *fallback*, never the first choice, because of exactly that (the user's own
in-game finding during the Bartizan wave's architecture pick — see `PICK.md`'s refinement 1).

None of this is Gangland's code to maintain any more. If recoil ever needs a fix, it goes into Keystone
(`ReflectivePacketAdapter`/`CameraRotationPackets`, `keystone-common`) or Bartizan (`RecoilManager`,
`bartizan-api/.../weapon/recoil/`) — never back into a resurrected `gangland-compatibility`.

The 1.16 floor (Spigot 1.16.5, `api-version: 1.16`) and Java release 17 are unchanged — both come from Keystone's
own floor, not from anything NMS-specific this repo used to ship. Players on older clients are still supported
through ViaVersion/ViaBackwards (recoil verified working through the Via pipeline on the live test server).

---

## Historical: the three position-packet shapes

This section is kept as the reference record Keystone's `keystone-npc`/`keystone-common` planner ("P1" in the
Bartizan-wave brainstorming) actually read before writing `ReflectivePacketAdapter` — the 20 deleted
`Recoil_1_xx_Ry` classes below are no longer in this tree (or in Bartizan's; they were never copied, only read),
so this is the only place their shape survives.

Every one of Gangland's 20 per-revision recoil adapters ultimately called one of the constructors of the server's
relative-camera-rotation packet (`ClientboundPlayerPositionPacket` in mojang-mapped builds,
`PacketPlayOutPosition` in obfuscated ones). Reading all 20 across the whole 1.16–1.21.11 range, they collapse to
**exactly three constructor shapes**, in the priority order Keystone's `CameraRotationPackets.shapeFor(...)`
probes them:

1. **Shape C — 1.21.2+, `PositionMoveRotation`-based.** The packet constructor takes
   `(int teleportId, PositionMoveRotation move, Set<Relative> flags)`, and `PositionMoveRotation` itself is built
   from `(Vec3 position, Vec3 deltaMovement, float yRot, float xRot)`. A zero-movement rotation-only packet passes
   `Vec3.ZERO` for both position vectors.
2. **Shape A2a — the trailing-boolean 8-arg constructor.** `(double x, double y, double z, float yaw, float pitch,
   Set<Relative> flags, int teleportId, boolean dismountVehicle)` — the shape a handful of intermediate revisions
   used before the 1.21.2 `PositionMoveRotation` refactor.
3. **Shape A1/A2b/B — the plain 7-arg constructor**, shared by every other revision in range: `(double x, double y,
   double z, float yaw, float pitch, Set<Relative> flags, int teleportId)` (no dismount flag).

Every shape shares the same invariants regardless of era: the position deltas are always zero (this is a *rotation*
packet, not a teleport), the teleport id is always `0`, and the flag set is always the same five relative flags —
`X`, `Y`, `Z`, `Y_ROT`, `X_ROT` (never the absolute-position variant a full teleport would use). The flag *enum*
itself renames across eras too (`Relative` on 1.21.2+, `RelativeMovement` on the intermediate mojang-mapped
releases, an inner `EnumPlayerTeleportFlags` on the obfuscated ones) and is resolved the same reflective way: by
the literal constant name `X` when the enum declares one (1.16 and 1.21.2+ both do), otherwise by the first five
ordinals (every obfuscated 1.17–1.20.x build declares exactly the five it needs, in that order).

This collapse — 20 version-specific Gangland classes reduced to 3 reflectively-probed constructor shapes — is what
lets a consumer plugin (Bartizan) ship **zero** version-specific modules for recoil. It lives in Keystone's
`keystone-common` package `org.luckyraven.keystone.nms.internal` (`CameraRotationPackets`,
`ReflectivePacketAdapter`) as of Keystone 1.9.0, not in this repo.

---

## Troubleshooting

| Issue                          | Cause                                                    | Solution                                                        |
|---------------------------------|-----------------------------------------------------------|------------------------------------------------------------------|
| No recoil, or recoil feels rough (teleport-like) | `nms.recoil.unsupported` — no known packet shape resolved on this server revision; `setRotation` fallback engaged | Check the `nms.recoil.unsupported` fault in Keystone's Diagnostics sink; file it against Keystone (a new shape may be needed), not Gangland |
| No recoil at all, no fault      | Bartizan not installed, or not enabled                    | Recoil (and every other weapon behavior) requires Bartizan — see [`bartizan-integration.md`](../bartizan-integration.md) |
| Recoil feels different than before 0.9.0 | Expected — Keystone's reflective shape vs. the old per-version compiled NMS code should be behaviorally identical, but this is the one part of the split the user verifies in-game, not by a unit test | Report as a Bartizan/Keystone finding, not a Gangland one |
