package org.luckyraven.gangland.civilians.npc.config;

import org.luckyraven.gangland.npc.radio.RadioSettings;

import java.util.List;
import java.util.Map;

/**
 * Top-level configuration loaded from {@code civilians.yml}.
 *
 * @param defaultCivilianEntities vanilla entity types auto-classified as CIVILIAN
 * @param defaultPoliceEntities vanilla entity types auto-classified as POLICE
 * @param types map of type-id → {@link CivilianTypeConfig}
 * @param groups map of group-id → {@link CivilianGroupConfig}
 * @param aiEnabled whether civilian NPC AI ticking is active
 * @param aiTickRate game ticks between civilian AI evaluations
 * @param shouts faction-shout delivery tuning, from the top-level {@code Shouts:} block
 */
public record CiviliansConfig(
		List<String> defaultCivilianEntities,
		List<String> defaultPoliceEntities,
		Map<String, CivilianTypeConfig> types,
		Map<String, CivilianGroupConfig> groups,
		boolean aiEnabled,
		int aiTickRate,
		RadioSettings shouts
) {

	/**
	 * Shout defaults, matching the shipped {@code civilians.yml}'s own {@code Shouts} block: above
	 * {@code Alert_Range} (16) so a shout reaches further than sight, responders off (gangs don't run radio
	 * drills — recruitment by shout is a separate mechanism), no click sound.
	 */
	public static final RadioSettings DEFAULT_SHOUTS = new RadioSettings(true, 24.0, 32.0, 1500L, 1000L, 25L, 0,
			Map.ofEntries(
					Map.entry("Contact", 8000L), Map.entry("Contact_Lost", 10000L), Map.entry("Engage", 15000L),
					Map.entry("Push", 10000L), Map.entry("Flank_Left", 10000L), Map.entry("Flank_Right", 10000L),
					Map.entry("Search", 15000L), Map.entry("No_Route", 15000L), Map.entry("Check_Fire", 8000L),
					Map.entry("Reloading", 10000L), Map.entry("Man_Down", 5000L), Map.entry("Leader_Down", 5000L),
					Map.entry("Rally", 6000L), Map.entry("Fall_Back", 5000L), Map.entry("In_Cover", 10000L)),
			java.util.Set.of("Contact", "Man_Down", "Leader_Down", "Rally"), null, 1.0f, 1.0f);

	/** Legacy 6-argument constructor, kept for existing callers: defaults {@link #shouts} to {@link #DEFAULT_SHOUTS}. */
	public CiviliansConfig(List<String> defaultCivilianEntities, List<String> defaultPoliceEntities,
	                       Map<String, CivilianTypeConfig> types, Map<String, CivilianGroupConfig> groups,
	                       boolean aiEnabled, int aiTickRate) {
		this(defaultCivilianEntities, defaultPoliceEntities, types, groups, aiEnabled, aiTickRate, DEFAULT_SHOUTS);
	}
}
