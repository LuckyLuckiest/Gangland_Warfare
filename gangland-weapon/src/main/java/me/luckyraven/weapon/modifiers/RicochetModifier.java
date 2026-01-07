package me.luckyraven.weapon.modifiers;

import org.bukkit.Material;

import java.util.Set;

/**
 * Allows projectiles to ricochet off surfaces.
 *
 * @param maxBounces Maximum number of bounces
 * @param bounceOffBlocks Materials that allow ricochets
 * @param damageRetention Damage kept after each bounce (0.0 - 1.0)
 */
public record RicochetModifier(int maxBounces, Set<Material> bounceOffBlocks, double damageRetention) {

	public boolean canBounceOff(Material material) {
		return bounceOffBlocks.isEmpty() || bounceOffBlocks.contains(material);
	}

}
