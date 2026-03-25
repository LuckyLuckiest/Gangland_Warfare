package me.luckyraven.weapon.types.melee;

import me.luckyraven.util.utilities.ParticleUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.MeleeData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MeleeAction {

	/**
	 * Entity UUIDs currently receiving programmatic melee damage — used to bypass the event cancel guard.
	 */
	public static final  Set<UUID>       pendingDamage = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, Long> cooldowns     = new ConcurrentHashMap<>();

	/**
	 * Activates a melee swing. Spawns a slash arc regardless of hit.
	 *
	 * @return true if at least one entity was hit
	 */
	public static boolean activate(Player player, Weapon weapon) {
		MeleeData data = weapon.getMeleeData();
		if (data == null) return false;

		UUID weaponUuid = weapon.getUuid();
		long now        = System.currentTimeMillis();
		long cooldownMs = data.getCooldown() * 50L;

		Long lastSwing = cooldowns.get(weaponUuid);
		if (lastSwing != null && now - lastSwing < cooldownMs) return false;
		cooldowns.put(weaponUuid, now);

		Vector  lookDir = player.getLocation().getDirection().normalize();
		double  range   = data.getRange();
		boolean hit     = false;

		for (Entity entity : player.getNearbyEntities(range, range, range)) {
			if (!(entity instanceof LivingEntity target)) continue;
			if (entity.equals(player)) continue;
			if (target.isDead()) continue;

			// only hit entities within ~60° arc in front of player
			Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
			if (lookDir.dot(toTarget) < 0.5) continue;

			pendingDamage.add(target.getUniqueId());
			target.damage(data.getDamage(), player);
			hit = true;

			// do not apply knockback to an entity that just died — setting velocity on a
			// dead entity causes the server's physics engine to move the corpse, which
			// can trigger a secondary generic damage event and produce a duplicate death message.
			if (target.isDead()) continue;
			if (data.getKnockback() <= 0) continue;
			Vector knockback = lookDir.clone().multiply(data.getKnockback());
			target.setVelocity(target.getVelocity().add(knockback));
		}

		// slash effect always plays on swing (not just on hit)
		ParticleUtil.spawnSlashArc(player.getLocation(), lookDir, range * 0.6);

		return hit;
	}

}
