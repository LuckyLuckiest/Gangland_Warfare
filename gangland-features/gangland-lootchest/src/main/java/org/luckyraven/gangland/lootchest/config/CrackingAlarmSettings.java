package org.luckyraven.gangland.lootchest.config;

import java.util.Set;

/**
 * {@code Cracking_Alarm} of {@code lootchests/loot_chest_settings.yml}: cracking (or failing to crack) a chest of one of
 * {@code tiers} rings an alarm and commits {@code Safe_Cracking}. Tier ids are lowercase.
 *
 * @since 0.20.0
 */
public record CrackingAlarmSettings(boolean enabled, Set<String> tiers, String sound) {

	/** The shipped defaults. */
	public static final CrackingAlarmSettings DEFAULT = new CrackingAlarmSettings(false, Set.of(), "");

}
