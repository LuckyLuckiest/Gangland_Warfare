package org.luckyraven.gangland.npc;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.npc.NpcFanPlacement;

/**
 * A squad member's job, the module-neutral part of a role: where it stands on the squad's fan, its firing band, how
 * it fights and what it is to the squad. Cops, civilian squads and turf guards all lay one over a tier; each module
 * keeps its own extras (kits, colours) beside it.
 *
 * @param name             the role's key; compositions refer to it.
 * @param displayName      the role's plain word, e.g. for radio lines.
 * @param placement        where on the squad's fan the NPC prefers to stand.
 * @param rangedMin        the NPC's own firing band, {@code null} for the default band; set together with
 *                         {@code rangedMax}.
 * @param rangedMax        see {@code rangedMin}; clamped under the held weapon's reach ({@link #rangedBand}).
 * @param healthMultiplier multiplies the tier's health.
 * @param leaderPriority   the NPC's claim to lead its squad; the highest leads.
 * @param strafeDegrees    replaces the tier's strafe angle, {@code null} keeps it.
 * @param fireRateScale    multiplies the tier's fire rate (below 1 fires slower).
 * @param difficultyBonus  steps above the tier's difficulty, capped at the top one (better aim).
 * @param retreat          replaces the module's retreat rule for this role, {@code null} keeps it.
 * @param blockFraction    the share of damage taken off a hit from inside the front cone (0 = no block).
 * @param blockConeDegrees the front cone's full width, in degrees.
 * @param medic            the role treats hurt squad mates (field care).
 * @param commander        the squad falls back briefly when this NPC dies.
 * @since gangland-api 2.6
 */
public record NpcRole(
		String name,
		String displayName,
		NpcFanPlacement placement,
		@Nullable Double rangedMin,
		@Nullable Double rangedMax,
		double healthMultiplier,
		int leaderPriority,
		@Nullable Double strafeDegrees,
		double fireRateScale,
		int difficultyBonus,
		@Nullable RetreatSettings retreat,
		double blockFraction,
		double blockConeDegrees,
		boolean medic,
		boolean commander
) {

	/** The narrowest band a reach clamp leaves: the Keystone band must keep {@code min < max}. */
	public static final double MIN_BAND_WIDTH = 2.0;

	/** No role: default placement, every multiplier 1, no band, no block, no duty. */
	public static final NpcRole NONE = new NpcRole("", "", NpcFanPlacement.ANY, null, null, 1.0, 0, null, 1.0, 0,
	                                               null, 0, 0, false, false);

	/**
	 * The firing band {@code {min, max}} for an NPC holding a weapon that reaches {@code weaponReach} blocks
	 * ({@code null}: unknown, no clamp): the max never passes the reach, and the min stays at least
	 * {@link #MIN_BAND_WIDTH} under it. {@code null} when the role declares no band.
	 */
	public double @Nullable [] rangedBand(@Nullable Double weaponReach) {
		if (rangedMin == null || rangedMax == null) return null;
		double max = weaponReach != null ? Math.min(rangedMax, weaponReach) : rangedMax;
		double min = Math.max(0, Math.min(rangedMin, max - MIN_BAND_WIDTH));
		return new double[]{min, max};
	}

	/** Whether a hit from {@code angleFromFacingDegrees} off the NPC's facing lands inside the front cone. */
	public boolean blocks(double angleFromFacingDegrees) {
		return blockFraction > 0 && Math.abs(angleFromFacingDegrees) <= blockConeDegrees / 2;
	}
}
