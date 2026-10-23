package org.luckyraven.gangland.lootchest.config;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code Cracking_Alarm} of {@code lootchests/loot_chest_settings.yml}: cracking (or failing to crack) a chest of one of
 * {@code tiers} rings an alarm and commits {@code Safe_Cracking}. Tier ids are lowercase.
 *
 * @since 0.20.0
 */
public record CrackingAlarmSettings(boolean enabled, Set<String> tiers, String sound) {

	/** The shipped defaults. */
	public static final CrackingAlarmSettings DEFAULT = new CrackingAlarmSettings(true,
	                                                                              Set.of("police_armory",
	                                                                                     "military_vault"),
	                                                                              "BLOCK_BELL_USE");

	public CrackingAlarmSettings {
		tiers = tiers == null ? Set.of() : tiers.stream()
				.filter(Objects::nonNull)
				.map(tier -> tier.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
		sound = sound == null ? "BLOCK_BELL_USE" : sound;
	}

}
