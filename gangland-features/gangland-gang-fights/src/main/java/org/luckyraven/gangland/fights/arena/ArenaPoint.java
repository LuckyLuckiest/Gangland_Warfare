package org.luckyraven.gangland.fights.arena;

/** A spot in a world with a facing: arena spawns and exits. */
public record ArenaPoint(String world, double x, double y, double z, float yaw, float pitch) {
}
