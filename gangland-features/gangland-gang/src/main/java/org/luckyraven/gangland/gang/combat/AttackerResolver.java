package org.luckyraven.gangland.gang.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.jetbrains.annotations.Nullable;

/** The player behind a damaging entity: the player, a projectile's shooter, a pet's owner or the one who lit the TNT. */
public final class AttackerResolver {

	private AttackerResolver() {
	}

	@Nullable
	public static Player resolve(@Nullable Entity damager) {
		if (damager instanceof Player player) return player;
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) return shooter;
		if (damager instanceof Tameable pet && pet.getOwner() instanceof Player owner) return owner;
		if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player lighter) return lighter;
		return null;
	}
}
