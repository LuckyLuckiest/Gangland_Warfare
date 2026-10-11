package org.luckyraven.gangland.gang.combat;

import java.util.Locale;
import java.util.Optional;

/**
 * The one decision point for player-vs-player damage between gangmates and allies (PLAN 15.5), as a pure function so
 * every row of the table is a unit test. First match wins:
 * <ol>
 *     <li>a gangless side, or a world with gangs off: not blocked;</li>
 *     <li>a player hurting himself: blocked only when {@code Allow_Self_Damage} is off;</li>
 *     <li>rival gangs: not blocked (this rule only ever protects friends);</li>
 *     <li>region flag, then per-world rule: ALLOW = not blocked, DENY = blocked;</li>
 *     <li>{@code Global: OFF} always blocks; gangmates are blocked unless {@code Global} is ON or the gang's own
 *     switch is on; allies are blocked unless both alliance rows are on.</li>
 * </ol>
 * ponytail: the region and world overrides are checked only for friends, so a DENY never starts protecting rivals.
 */
public final class FriendlyFireRule {

	public enum Global {
		/** Gangmates and allies can never hurt each other. */
		OFF,
		/** Each gang decides for itself ({@code /glw gang pvp}). */
		GANG_CHOICE,
		/** Gangmates can always hurt each other. */
		ON;

		/** The word in {@code gang_rules.yml}; anything unknown (or missing) is {@link #GANG_CHOICE}. */
		public static Global parse(String value) {
			if (value == null) return GANG_CHOICE;

			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException exception) {
				return GANG_CHOICE;
			}
		}
	}

	private FriendlyFireRule() {
	}

	/**
	 * @param attackerGang gang id of the attacker, {@code -1} when gangless
	 * @param victimGang gang id of the victim, {@code -1} when gangless
	 * @param allied whether the two (different) gangs are allied
	 * @param self whether the attacker and the victim are the same player
	 * @param worldDisabled whether gangs do not exist where the hit lands
	 * @param region a region plugin's verdict at the spot ({@code true} = friendly fire allowed), empty = no opinion
	 * @param worldRule the {@code Friendly_Fire.Worlds} verdict for the world, same encoding
	 * @param global the {@code Friendly_Fire.Global} switch
	 * @param gangFriendlyFire the attacker's gang {@code /glw gang pvp} switch
	 * @param allyFriendlyFire whether both alliance rows are on ({@code /glw gang ally pvp})
	 * @param allowSelfDamage {@code Friendly_Fire.Allow_Self_Damage}
	 *
	 * @return {@code true} when the hit must be cancelled
	 */
	public static boolean blocked(int attackerGang, int victimGang, boolean allied, boolean self,
	                              boolean worldDisabled, Optional<Boolean> region, Optional<Boolean> worldRule,
	                              Global global, boolean gangFriendlyFire, boolean allyFriendlyFire,
	                              boolean allowSelfDamage) {
		if (attackerGang == -1 || victimGang == -1 || worldDisabled) return false;
		if (self) return !allowSelfDamage;

		boolean sameGang = attackerGang == victimGang;
		if (!sameGang && !allied) return false;

		if (region.isPresent()) return !region.get();
		if (worldRule.isPresent()) return !worldRule.get();
		if (global == Global.OFF) return true;

		if (sameGang) return global == Global.GANG_CHOICE && !gangFriendlyFire;
		return !allyFriendlyFire;
	}
}
