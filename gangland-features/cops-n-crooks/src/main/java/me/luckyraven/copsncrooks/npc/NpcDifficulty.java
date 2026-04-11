package me.luckyraven.copsncrooks.npc;

import lombok.Getter;

/**
 * Bundles all combat-tuning knobs that scale how dangerous an NPC is to engage. Composed onto an {@link AbstractNpc}
 * via constructor — the AI state machines and the weapon module never reference this enum, so adding new NPC types only
 * requires passing one of these values into {@code super(...)} to participate in the difficulty system.
 * <p>
 * The four knobs are read at exactly three points inside {@link AbstractNpc}:
 * <ul>
 *   <li>{@link #aimError} — applied in {@code faceTarget} / {@code faceTargetEntity} as a random angular offset
 *       on the NPC's facing direction. Because {@code WeaponShooting} reads
 *       {@code shooter.getEyeLocation().getDirection()} after the NPC has been turned, the imperfect aim propagates
 *       into the bullet path with no changes required to the weapon module.</li>
 *   <li>{@link #reactionTimeTicks} — added to {@code attackCooldown} the first time a fresh target is acquired,
 *       inside {@code attack} / {@code attackEntity}.</li>
 *   <li>{@link #fireRateMultiplier} — multiplies the per-shot cooldown in {@code performGanglandWeaponAttack},
 *       {@code performVanillaRangedAttack}, and the melee perform-* paths.</li>
 *   <li>{@link #meleeDamageMultiplier} — scales the damage dealt by the melee perform-* paths. Ranged damage is
 *       intentionally left untouched in this iteration so the difficulty system stays decoupled from the
 *       {@code gangland-weapon} module.</li>
 * </ul>
 */
@Getter
public enum NpcDifficulty {

	// aimError, reactionTimeTicks, fireRateMultiplier, meleeDamageMultiplier
	EASY(0.35, 30, 1.5, 0.7),
	NORMAL(0.18, 15, 1.0, 1.0),
	HARD(0.08, 5, 0.85, 1.15),
	DEADLY(0.0, 0, 0.7, 1.3);

	/**
	 * Magnitude of random angular offset added to the NPC's aim vector. 0 = perfect aim.
	 */
	private final double aimError;
	/**
	 * Ticks of delay injected on first target acquisition (simulates reaction).
	 */
	private final int    reactionTimeTicks;
	/**
	 * Multiplier on the per-shot cooldown (>1 = slower fire rate, <1 = faster).
	 */
	private final double fireRateMultiplier;
	/**
	 * Multiplier applied to melee damage. Ranged damage is left alone for now.
	 */
	private final double meleeDamageMultiplier;

	NpcDifficulty(double aimError, int reactionTimeTicks, double fireRateMultiplier, double meleeDamageMultiplier) {
		this.aimError              = aimError;
		this.reactionTimeTicks     = reactionTimeTicks;
		this.fireRateMultiplier    = fireRateMultiplier;
		this.meleeDamageMultiplier = meleeDamageMultiplier;
	}
}
