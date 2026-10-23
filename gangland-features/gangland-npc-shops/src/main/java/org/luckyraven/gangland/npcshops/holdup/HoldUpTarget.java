package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.Location;

import java.util.UUID;

/**
 * The trader a player is currently aiming at: identity (cooldown / mood key), display name (messages, debug) and
 * the spot the silent alarm is reported at.
 */
public record HoldUpTarget(UUID traderId, String name, Location location) {

}
