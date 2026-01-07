package me.luckyraven.weapon.projectile;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.weapon.Weapon;

/**
 * Tracks the runtime state of a projectile for modifier calculations.
 */
@Getter
@Setter
public class ProjectileState {

	private final Weapon weapon;
	private final double baseDamage;

	private int    blocksPenetrated;
	private int    entitiesPenetrated;
	private int    bounceCount;
	private double currentDamageMultiplier;

	public ProjectileState(Weapon weapon) {
		this.weapon                  = weapon;
		this.baseDamage              = weapon.getProjectileData().getDamage();
		this.blocksPenetrated        = 0;
		this.entitiesPenetrated      = 0;
		this.bounceCount             = 0;
		this.currentDamageMultiplier = 1.0;
	}

	/**
	 * Calculates the current damage after all modifier reductions.
	 */
	public double getCurrentDamage() {
		return baseDamage * currentDamageMultiplier;
	}

	/**
	 * Applies penetration damage reduction.
	 */
	public void applyPenetrationReduction(double reduction) {
		currentDamageMultiplier *= (1.0 - reduction);
	}

	/**
	 * Applies ricochet damage reduction.
	 */
	public void applyRicochetReduction(double retention) {
		currentDamageMultiplier *= retention;
	}

	/**
	 * Checks if the projectile can still penetrate blocks.
	 */
	public boolean canPenetrateBlock() {
		var modifiers = weapon.getModifiersData();
		if (!modifiers.hasPenetration()) return false;
		return blocksPenetrated < modifiers.getPenetration().penetrateBlocks();
	}

	/**
	 * Checks if the projectile can still penetrate entities.
	 */
	public boolean canPenetrateEntity() {
		var modifiers = weapon.getModifiersData();
		if (!modifiers.hasPenetration()) return false;
		return entitiesPenetrated < modifiers.getPenetration().penetrateEntities();
	}

	/**
	 * Checks if the projectile can still ricochet.
	 */
	public boolean canRicochet() {
		var modifiers = weapon.getModifiersData();
		if (!modifiers.hasRicochet()) return false;

		// Check against the maximum bounces from all ricochet modifiers
		int maxBounces = modifiers.getRicochets()
				.stream().mapToInt(r -> r.maxBounces()).max().orElse(0);

		return bounceCount < maxBounces;
	}

}
