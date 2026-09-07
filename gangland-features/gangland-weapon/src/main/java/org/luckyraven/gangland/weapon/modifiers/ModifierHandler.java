package org.luckyraven.gangland.weapon.modifiers;

import com.cryptomorin.xseries.XAttribute;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.dto.ModifiersData;
import org.luckyraven.gangland.weapon.modifiers.action.ArmorPiercingModifier;
import org.luckyraven.gangland.weapon.modifiers.action.PenetrationModifier;
import org.luckyraven.gangland.weapon.projectile.ProjectileState;

/**
 * Pure helpers for the weapon modifier pipeline. Damage / penetration math used by the unified {@code WeaponRaytracer};
 * reflection math, ricochet projectile spawning, and tracer-particle rendering have been moved into the raytracer
 * itself.
 */
public class ModifierHandler {

	/**
	 * Calculates final damage with armor piercing modifier applied.
	 *
	 * @param baseDamage The base damage amount
	 * @param target The target entity
	 * @param weapon The weapon used
	 *
	 * @return The final damage after armor calculations
	 */
	public static double calculateArmorPiercingDamage(double baseDamage, LivingEntity target, Weapon weapon) {
		ModifiersData modifiers = weapon.getModifiersData();

		if (!modifiers.hasArmorPiercing()) {
			return baseDamage;
		}

		ArmorPiercingModifier armorPiercing = modifiers.getArmorPiercing();

		// Get target's armor value using XAttribute for cross-version compatibility
		Attribute armorAttribute = XAttribute.ARMOR.get();
		if (armorAttribute == null) {
			return baseDamage;
		}

		AttributeInstance armorInstance = target.getAttribute(armorAttribute);
		if (armorInstance == null) {
			return baseDamage;
		}

		double armor          = armorInstance.getValue();
		double effectiveArmor = armorPiercing.calculateEffectiveArmor(armor);

		// Minecraft damage reduction formula: damage * (1 - min(20, armor) / 25)
		double normalReduction   = Math.min(20, armor) / 25.0;
		double piercingReduction = Math.min(20, effectiveArmor) / 25.0;

		// Calculate the damage difference
		double normalDamage   = baseDamage * (1 - normalReduction);
		double piercingDamage = baseDamage * (1 - piercingReduction);

		// Return the piercing damage (will be reduced again by Minecraft, so we compensate)
		return baseDamage + (piercingDamage - normalDamage);
	}

	/**
	 * Adds the flat damage bonus to the base damage.
	 *
	 * @param baseDamage The damage before the bonus
	 * @param weapon The weapon used
	 *
	 * @return The damage with the flat bonus added, or baseDamage unchanged if modifier is absent
	 */
	public static double applyFlatDamage(double baseDamage, Weapon weapon) {
		ModifiersData modifiers = weapon.getModifiersData();

		if (!modifiers.hasFlatDamage()) {
			return baseDamage;
		}

		return baseDamage + modifiers.getFlatDamage().bonus();
	}

	/**
	 * Handles entity penetration logic.
	 *
	 * @param state The projectile state
	 *
	 * @return true if the projectile should continue, false if it should stop
	 */
	public static boolean handleEntityPenetration(ProjectileState state) {
		if (!state.canPenetrateEntity()) {
			return false;
		}

		PenetrationModifier penetration = state.getWeapon().getModifiersData().getPenetration();

		// Increment penetration count
		state.setEntitiesPenetrated(state.getEntitiesPenetrated() + 1);

		// Apply damage reduction
		state.applyPenetrationReduction(penetration.damageReduction());

		// Check if projectile can continue
		return state.canPenetrateEntity() || state.canPenetrateBlock();
	}

	/**
	 * Handles block penetration logic.
	 *
	 * @param state The projectile state
	 * @param hitBlock The block that was hit
	 *
	 * @return true if the projectile should continue through the block
	 */
	public static boolean handleBlockPenetration(ProjectileState state, Block hitBlock) {
		if (!state.canPenetrateBlock()) {
			return false;
		}

		PenetrationModifier penetration = state.getWeapon().getModifiersData().getPenetration();

		// Check if block is penetrable (non-solid or thin blocks)
		if (!isPenetrableBlock(hitBlock.getType())) {
			return false;
		}

		// Increment penetration count
		state.setBlocksPenetrated(state.getBlocksPenetrated() + 1);

		// Apply damage reduction
		state.applyPenetrationReduction(penetration.damageReduction());

		return true;
	}

	/**
	 * Checks if a block can be penetrated by projectiles.
	 */
	private static boolean isPenetrableBlock(Material material) {
		String name = material.name();

		// Glass and thin blocks
		if (name.contains("GLASS") || name.contains("PANE")) return true;
		if (name.contains("LEAVES")) return true;
		if (name.contains("FENCE") && !name.contains("GATE")) return true;
		if (name.contains("BARS")) return true;
		if (name.contains("CHAIN")) return true;
		if (name.contains("CARPET")) return true;
		if (name.contains("BANNER")) return true;
		if (name.contains("SIGN")) return true;
		if (name.contains("CANDLE")) return true;
		if (name.contains("FLOWER") || name.contains("PLANT") || name.contains("GRASS")) return true;
		if (name.contains("VINE") || name.contains("MOSS")) return true;

		return switch (material) {
			case COBWEB, SNOW, SUGAR_CANE, BAMBOO, SCAFFOLDING, LADDER -> true;
			default -> false;
		};
	}

}
