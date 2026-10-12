package org.luckyraven.gangland.gang.safehouse;

/** One saved gang teleport spot. */
public record Safehouse(int gangId, String name, String world, double x, double y, double z, float yaw, float pitch) { }
