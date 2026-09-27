package org.luckyraven.gangland.copsncrooks.hud;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The escape compass (0.12 F3): while SEARCHING, the wanted player's compass points to the nearest point outside the
 * search zone instead of their bed/respawn point; when the chase stops SEARCHING, the compass target the player had
 * before is restored.
 */
public final class EscapeCompass {

	private final Map<UUID, Location> previousTargets = new ConcurrentHashMap<>();

	/**
	 * The nearest point outside the search zone, on the horizontal ray from {@code center} through {@code player}:
	 * {@code radius + 5} blocks out along that ray, so the compass keeps pointing there rather than sitting on the
	 * zone edge. A player standing exactly on the center (no direction to extend) is pointed east ({@code +X}).
	 *
	 * @param center the search zone center
	 * @param radius the search zone radius in blocks
	 * @param player the wanted player's current location
	 *
	 * @return the point to aim the compass at, in {@code center}'s world
	 */
	public static Location pointOutside(Location center, double radius, Location player) {
		double dx     = player.getX() - center.getX();
		double dz     = player.getZ() - center.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		double target = radius + 5D;

		if (length < 1.0E-6) return center.clone().add(target, 0D, 0D);

		double scale = target / length;
		return center.clone().add(dx * scale, 0D, dz * scale);
	}

	/**
	 * Points {@code player}'s compass at {@code target}, remembering their compass target the first time so it can
	 * be {@link #restore(Player) restored} once the chase leaves SEARCHING. Safe to call every tick.
	 *
	 * @param player the wanted player
	 * @param target where to point the compass
	 */
	public void point(Player player, Location target) {
		previousTargets.computeIfAbsent(player.getUniqueId(), id -> player.getCompassTarget());
		player.setCompassTarget(target);
	}

	/**
	 * Restores the compass target {@code player} had before {@link #point(Player, Location)} first ran, if any.
	 * No-op if the player was never pointed.
	 *
	 * @param player the player
	 */
	public void restore(Player player) {
		Location previous = previousTargets.remove(player.getUniqueId());
		if (previous != null) player.setCompassTarget(previous);
	}
}
