package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFixtures.Wearer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterisation of how {@code SuspectDescriptions#describe} reads the {@code Mask_Items} and {@code Mask_Wearables}
 * lists: a case-insensitive match against any entry, so a mask listed in another case or after other entries still
 * masks, and an empty list masks nothing.
 */
@DisplayName("SuspectDescriptions - the mask lists match any entry, ignoring case")
class SuspectDescriptionsMaskListTest {

	private Wearer wearer;

	@BeforeEach
	void setUp() {
		wearer = new Wearer();
	}

	private boolean masked(List<String> maskItems, List<String> maskWearables) {
		DisguiseSettings settings = new DisguiseSettings(true, maskItems, maskWearables, List.of(), 2, 2);
		return DisguiseFixtures.descriptions(wearer, settings).describe(wearer.player).masked();
	}

	@Test
	@DisplayName("a Mask_Items entry in another case, or after other entries, masks the helmet")
	void maskItems_ignoreCase_anyEntry() {
		wearer.helmet(Material.CARVED_PUMPKIN);

		assertTrue(masked(List.of("carved_pumpkin"), List.of()));
		assertTrue(masked(List.of("LEATHER_HELMET", "Carved_Pumpkin"), List.of()));
		assertFalse(masked(List.of("LEATHER_HELMET", "IRON_HELMET"), List.of()));
		assertFalse(masked(List.of(), List.of()));
	}

	@Test
	@DisplayName("a Mask_Wearables entry in another case, or after other entries, masks the wearable")
	void maskWearables_ignoreCase_anyEntry() {
		wearer.helmet(Material.LEATHER_HELMET, "black_mask");

		assertTrue(masked(List.of(), List.of("BLACK_MASK")));
		assertTrue(masked(List.of(), List.of("red_mask", "Black_Mask")));
		assertFalse(masked(List.of(), List.of("red_mask", "blue_mask")));
		assertFalse(masked(List.of(), List.of()));
	}
}
