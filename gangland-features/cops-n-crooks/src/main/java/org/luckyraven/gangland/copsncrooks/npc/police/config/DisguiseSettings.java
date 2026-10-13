package org.luckyraven.gangland.copsncrooks.npc.police.config;

import java.util.List;

/** Masks and the police outfit ({@code Cops.Disguise}). @since 0.20.0 */
public record DisguiseSettings(boolean enabled, List<String> maskItems, List<String> maskWearables, List<String> policeOutfit, int outfitMaxLevel, int matchNeeded) {

	/** Matches the 0.20.0 cops.yml defaults. */
	public static final DisguiseSettings DEFAULT = new DisguiseSettings(true,
	                                                                    List.of("CARVED_PUMPKIN", "PLAYER_HEAD", "ZOMBIE_HEAD", "SKELETON_SKULL", "CREEPER_HEAD"),
	                                                                    List.of(), List.of("police_helmet", "police_vest"), 2, 2);

	public DisguiseSettings {
		maskItems = maskItems == null ? List.of() : List.copyOf(maskItems);
		maskWearables = maskWearables == null ? List.of() : List.copyOf(maskWearables);
		policeOutfit = policeOutfit == null ? List.of() : List.copyOf(policeOutfit);
		outfitMaxLevel = Math.max(0, outfitMaxLevel);
		matchNeeded = Math.max(1, matchNeeded);
	}
}
