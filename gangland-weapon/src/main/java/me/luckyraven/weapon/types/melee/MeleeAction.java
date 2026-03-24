package me.luckyraven.weapon.types.melee;

import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.MeleeData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MeleeAction {

	private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

	public static void activate(Player player, Weapon weapon) {
		MeleeData data = weapon.getMeleeData();
		if (data == null) return;

		UUID weaponUuid = weapon.getUuid();
		long now        = System.currentTimeMillis();
		long cooldownMs = data.getCooldown() * 50L;

		Long lastSwing = cooldowns.get(weaponUuid);
		if (lastSwing != null && now - lastSwing < cooldownMs) return;
		cooldowns.put(weaponUuid, now);

		Vector lookDir = player.getLocation().getDirection().normalize();
		double range   = data.getRange();

		for (Entity entity : player.getNearbyEntities(range, range, range)) {
			if (!(entity instanceof LivingEntity target)) continue;
			if (entity.equals(player)) continue;

			// only hit entities within ~60° arc in front of player
			Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
			if (lookDir.dot(toTarget) < 0.5) continue;

			target.damage(data.getDamage(), player);

			if (data.getKnockback() <= 0) continue;
			Vector knockback = lookDir.clone().multiply(data.getKnockback());
			target.setVelocity(target.getVelocity().add(knockback));
		}
	}

}
