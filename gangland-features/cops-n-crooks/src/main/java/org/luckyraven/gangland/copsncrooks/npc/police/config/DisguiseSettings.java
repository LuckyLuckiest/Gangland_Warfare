package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.List;
import java.util.Map;

/**
 * Masks and the police outfit ({@code Cops.Disguise}). {@code changesNeeded} is the base count and
 * {@code byDifficulty} adds to it per cop difficulty (0.21.6). @since 0.20.0
 */
public record DisguiseSettings(boolean enabled, List<String> maskItems, List<String> maskWearables, List<String> policeOutfit, int outfitMaxLevel, int changesNeeded, Map<NpcDifficulty, Recognition> byDifficulty) {

	/** One row of {@code Cops.Disguise.By_Difficulty}. @since 0.21.6 */
	public record Recognition(int extraChanges, double memorySeconds) {

		public long memoryMillis() {
			return (long) (memorySeconds * 1000.0);
		}
	}

	// declared before DEFAULT: DEFAULT's constructor reads it
	public static final Map<NpcDifficulty, Recognition> DEFAULT_BY_DIFFICULTY = Map.of(
			NpcDifficulty.EASY, new Recognition(0, 3.0), NpcDifficulty.NORMAL, new Recognition(0, 5.0),
			NpcDifficulty.HARD, new Recognition(1, 10.0), NpcDifficulty.DEADLY, new Recognition(2, 20.0));

	/** Matches the cops.yml defaults. */
	public static final DisguiseSettings DEFAULT = new DisguiseSettings(true,
	                                                                    List.of("CARVED_PUMPKIN", "PLAYER_HEAD", "ZOMBIE_HEAD", "SKELETON_SKULL", "CREEPER_HEAD"),
	                                                                    List.of(), List.of("police_helmet", "police_vest"), 2, 2);

	public DisguiseSettings {
		maskItems = maskItems == null ? List.of() : List.copyOf(maskItems);
		maskWearables = maskWearables == null ? List.of() : List.copyOf(maskWearables);
		policeOutfit = policeOutfit == null ? List.of() : List.copyOf(policeOutfit);
		outfitMaxLevel = Math.max(0, outfitMaxLevel);
		changesNeeded = Math.max(1, changesNeeded);
		byDifficulty = byDifficulty == null ? DEFAULT_BY_DIFFICULTY : Map.copyOf(byDifficulty);
	}

	/** The pre-0.21.6 arity: the shipped scale. */
	public DisguiseSettings(boolean enabled, List<String> maskItems, List<String> maskWearables, List<String> policeOutfit, int outfitMaxLevel, int changesNeeded) {
		this(enabled, maskItems, maskWearables, policeOutfit, outfitMaxLevel, changesNeeded, DEFAULT_BY_DIFFICULTY);
	}

	/** How many pieces must differ against cops of this difficulty: the base count plus the row's extra. */
	public int changesNeeded(NpcDifficulty difficulty) {
		return Math.max(1, changesNeeded + row(difficulty).extraChanges());
	}

	/** How long the cops remember a suspect they last saw, against cops of this difficulty. */
	public long memoryMillis(NpcDifficulty difficulty) {
		return row(difficulty).memoryMillis();
	}

	private Recognition row(NpcDifficulty difficulty) {
		return byDifficulty.getOrDefault(difficulty, DEFAULT_BY_DIFFICULTY.get(difficulty));
	}
}
