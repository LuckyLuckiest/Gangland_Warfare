package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.List;

/** Masks and the police outfit ({@code Cops.Disguise}). @since 0.20.0 */
public record DisguiseSettings(boolean enabled, List<String> maskItems, List<String> maskWearables, List<String> policeOutfit, int outfitMaxLevel, int matchNeeded) {

	/** TDD stub: neutral values until the green chunk sets the shipped defaults and clamps. */
	public static final DisguiseSettings DEFAULT = new DisguiseSettings(false, List.of(), List.of(), List.of(), 0, 0);
}
