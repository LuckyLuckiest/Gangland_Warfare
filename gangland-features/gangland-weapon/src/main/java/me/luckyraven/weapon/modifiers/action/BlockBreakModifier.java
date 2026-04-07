package me.luckyraven.weapon.modifiers.action;

import me.luckyraven.weapon.modifiers.BreakMode;
import org.bukkit.Material;

import java.util.Set;

/**
 * Represents a block break modifier configuration.
 *
 * @param targetMaterials The materials that can be damaged (includes group variants)
 * @param hitsRequired Number of projectile hits required to reach max damage
 * @param mode How the block is treated once the hit threshold is reached (see {@link BreakMode})
 */
public record BlockBreakModifier(Set<Material> targetMaterials, int hitsRequired, BreakMode mode) {

	/**
	 * Checks if this modifier applies to the given material.
	 */
	public boolean appliesTo(Material material) {
		return targetMaterials.contains(material);
	}

}
