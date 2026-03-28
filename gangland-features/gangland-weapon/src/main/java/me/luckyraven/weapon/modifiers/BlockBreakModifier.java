package me.luckyraven.weapon.modifiers;

import org.bukkit.Material;

import java.util.Set;

/**
 * Represents a block break modifier configuration.
 *
 * @param targetMaterials The materials that can be damaged (includes group variants)
 * @param hitsRequired Number of projectile hits required to reach max damage/break
 * @param actuallyBreaks Whether the block should actually break or just show max crack
 */
public record BlockBreakModifier(Set<Material> targetMaterials, int hitsRequired, boolean actuallyBreaks) {

	/**
	 * Constructor with default actuallyBreaks = true for backwards compatibility.
	 */
	public BlockBreakModifier(Set<Material> targetMaterials, int hitsRequired) {
		this(targetMaterials, hitsRequired, true);
	}

	/**
	 * Checks if this modifier applies to the given material.
	 */
	public boolean appliesTo(Material material) {
		return targetMaterials.contains(material);
	}

}
