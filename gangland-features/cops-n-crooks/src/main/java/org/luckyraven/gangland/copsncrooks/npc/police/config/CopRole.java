package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcEngagement;
import org.luckyraven.keystone.npc.NpcFanPlacement;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A cop's job inside its squad ({@code cops.yml} {@code Cops.Roles.<Name>}), laid over its tier when it spawns: the
 * tier still decides weapons, armour and the formation arc, the role only shifts where the cop stands and how it
 * fights. Picked per spawn from {@code Cops.Squad_Composition} by {@link #nextRole}.
 *
 * @param name             the role's key under {@code Cops.Roles}; compositions refer to it.
 * @param displayName      the role's label ({@code Display_Name}, default the name). Never replaces the tier's
 *                         {@code Display_Name}: nameplates and radio callsigns combine the two.
 * @param placement        where on the squad's fan the cop prefers to stand.
 * @param rangedMin        the cop's own firing band, {@code null} for the settings.yml band; set together with
 *                         {@code rangedMax}.
 * @param rangedMax        see {@code rangedMin}; clamped under the held weapon's reach at spawn ({@link #rangedBand}).
 * @param healthMultiplier multiplies the tier's {@code Health}.
 * @param offHand          an item held in the off hand (the Defender's shield); cosmetic, never dropped.
 * @param leaderPriority   the cop's claim to lead its squad (radio voice, {@code Leader_Down}); the highest leads.
 * @param strafeDegrees    replaces the tier's {@code Tactics.Strafe_Degrees}, {@code null} keeps it.
 * @param fireRateScale    multiplies the tier's {@code Fire_Rate_Multiplier} (below 1 fires slower).
 * @param difficultyBonus  steps above the tier's {@code Difficulty}, capped at the top one (better aim).
 * @param retreat          replaces {@code Cops.Retreat} for this role, {@code null} keeps it.
 * @param blockFraction    the share of damage taken off a hit from inside the front cone (0 = no block).
 * @param blockConeDegrees the front cone's full width, in degrees.
 * @param medic            the role treats hurt squad mates (field care).
 * @param commander        the squad falls back briefly and radios {@code Commander_Down} when this cop dies.
 */
public record CopRole(
		String name,
		String displayName,
		NpcFanPlacement placement,
		@Nullable Double rangedMin,
		@Nullable Double rangedMax,
		double healthMultiplier,
		@Nullable ItemStack offHand,
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
	static final double MIN_BAND_WIDTH = 2.0;

	/**
	 * The tier with this role laid over it: {@code Health} multiplied, {@code Difficulty} stepped up,
	 * {@code Fire_Rate_Multiplier} scaled and {@code Strafe_Degrees} replaced; a LEGACY tier stays LEGACY. Every other
	 * field, the display name included, is the tier's.
	 */
	public CopTierConfig overlay(CopTierConfig tier) {
		NpcEngagement engagement = tier.tactics().engagement();
		if (strafeDegrees != null && engagement.enabled())
			engagement = new NpcEngagement(true, strafeDegrees, engagement.repositionMs(), engagement.movingAimError());

		NpcDifficulty[] difficulties = NpcDifficulty.values();
		NpcDifficulty difficulty = difficulties[Math.min(difficulties.length - 1,
		                                                 tier.difficulty().ordinal() + difficultyBonus)];

		return new CopTierConfig(tier.tier(), tier.displayName(), tier.health() * healthMultiplier, tier.damage(),
		                         tier.speed(), tier.cuffRadius(), tier.canUseWeapons(), tier.skipCuffing(),
		                         tier.weaponNamePool(), tier.weaponPool(), tier.helmet(), tier.chestplate(),
		                         tier.leggings(), tier.boots(), difficulty,
		                         new TacticsConfig(engagement, tier.tactics().formationArc()),
		                         tier.fireRateMultiplier() * fireRateScale);
	}

	/**
	 * The firing band {@code {min, max}} for a cop holding a weapon that reaches {@code reach} blocks ({@code null}:
	 * unknown, no clamp): the max never passes the reach, and the min stays at least {@link #MIN_BAND_WIDTH} under
	 * it. {@code null} when the role declares no band.
	 */
	public double @Nullable [] rangedBand(@Nullable Double reach) {
		if (rangedMin == null || rangedMax == null) return null;
		double max = reach != null ? Math.min(rangedMax, reach) : rangedMax;
		double min = Math.max(0, Math.min(rangedMin, max - MIN_BAND_WIDTH));
		return new double[]{min, max};
	}

	/**
	 * Whether a hit from {@code from} lands inside this role's front cone, around the way {@code self} faces
	 * (horizontally: height is ignored). Never with no {@code Block_Fraction}.
	 */
	public boolean blocks(Location self, Location from) {
		if (blockFraction <= 0) return false;
		double dx = from.getX() - self.getX(), dz = from.getZ() - self.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1e-6) return true; // point blank: whatever is in the cop's face

		double yaw = Math.toRadians(self.getYaw());
		double cos = (-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / length;
		return cos >= Math.cos(Math.toRadians(blockConeDegrees / 2));
	}

	/**
	 * The role the next cop joining a group gets: walks {@code composition} in order, each entry filled by one live
	 * cop holding that role, and returns the first unfilled entry; with every entry filled, the last one again (backup
	 * and extra cops). {@code liveRoles} are the group's counted cops' roles ({@code null} for a role-less cop).
	 * {@code null} when there is no composition (roles off).
	 */
	public static @Nullable CopRole nextRole(@Nullable List<CopRole> composition,
	                                         Collection<? extends @Nullable CopRole> liveRoles) {
		if (composition == null || composition.isEmpty()) return null;

		Map<String, Integer> held = new HashMap<>();
		for (CopRole role : liveRoles)
			if (role != null) held.merge(role.name(), 1, Integer::sum);

		for (CopRole role : composition) {
			int count = held.getOrDefault(role.name(), 0);
			if (count == 0) return role;
			held.put(role.name(), count - 1);
		}
		return composition.get(composition.size() - 1);
	}
}
