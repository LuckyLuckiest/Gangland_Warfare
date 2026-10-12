package org.luckyraven.gangland.fights.fight;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.fights.arena.ArenaPoint;

import java.util.UUID;

/**
 * What a fighter looked like before the fight, to put back afterwards: where he stood, his health and his food.
 * Taken when he is teleported in, put back by {@link #restore} at the end or on any cancel.
 */
public record PlayerSnapshot(UUID player, ArenaPoint returnTo, double health, int food) {

	public void restore(Player target) {
		World world = Bukkit.getWorld(returnTo.world());
		if (world != null) {
			target.teleport(new Location(world, returnTo.x(), returnTo.y(), returnTo.z(), returnTo.yaw(),
			                             returnTo.pitch()));
		}
		target.setHealth(Math.max(1D, Math.min(health, target.getMaxHealth())));
		target.setFoodLevel(food);
	}
}
