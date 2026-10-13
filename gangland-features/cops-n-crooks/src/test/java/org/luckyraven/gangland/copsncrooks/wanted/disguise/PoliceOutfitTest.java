package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFixtures.Wearer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P-DIS-4: the police outfit hides up to Outfit_Max_Level stars until a crime is committed in it. */
@DisplayName("PoliceOutfit - worn, hides, blown")
class PoliceOutfitTest {

	private Wearer wearer;

	@BeforeEach
	void setUp() {
		wearer = new Wearer();
	}

	private PoliceOutfit outfit(DisguiseSettings settings) {
		return new PoliceOutfit(() -> settings, wearer.keys());
	}

	private PoliceOutfit outfit() {
		return outfit(DisguiseSettings.DEFAULT);
	}

	private void dress() {
		wearer.helmet(Material.IRON_HELMET, "police_helmet").chest(Material.IRON_CHESTPLATE, "police_vest");
	}

	@Test
	@DisplayName("worn needs both configured pieces; one piece is not the outfit")
	void wornNeedsBothPieces() {
		PoliceOutfit outfit = outfit();

		assertFalse(outfit.worn(wearer.player), "nothing on");

		wearer.helmet(Material.IRON_HELMET, "police_helmet");
		assertFalse(outfit.worn(wearer.player), "helmet only");

		wearer.chest(Material.IRON_CHESTPLATE, "police_vest");
		assertTrue(outfit.worn(wearer.player));
	}

	@Test
	@DisplayName("the right material without the wearable key is not the outfit")
	void materialAloneIsNotTheOutfit() {
		wearer.helmet(Material.IRON_HELMET).chest(Material.IRON_CHESTPLATE);

		assertFalse(outfit().worn(wearer.player));
	}

	@Test
	@DisplayName("an empty Police_Outfit list is never worn")
	void emptyListNeverWorn() {
		dress();

		assertFalse(outfit(new DisguiseSettings(true, List.of(), List.of(), List.of(), 2, 2)).worn(wearer.player));
	}

	@Test
	@DisplayName("hides at 0..Outfit_Max_Level stars and not above it")
	void hidesUpToMaxLevel() {
		dress();
		PoliceOutfit outfit = outfit();

		assertTrue(outfit.hides(wearer.player, 0));
		assertTrue(outfit.hides(wearer.player, 2));
		assertFalse(outfit.hides(wearer.player, 3));
	}

	@Test
	@DisplayName("Outfit_Max_Level is read live from the settings")
	void maxLevelFromSettings() {
		dress();
		PoliceOutfit outfit = outfit(new DisguiseSettings(true, List.of(), List.of(), List.of("police_helmet", "police_vest"), 4, 2));

		assertTrue(outfit.hides(wearer.player, 4));
		assertFalse(outfit.hides(wearer.player, 5));
	}

	@Test
	@DisplayName("a crime in the outfit blows it while it stays on")
	void crimeInOutfitBlowsIt() {
		dress();
		PoliceOutfit outfit = outfit();

		outfit.onCrime(wearer.player);

		assertFalse(outfit.hides(wearer.player, 1));
		assertFalse(outfit.hides(wearer.player, 1), "still blown on the next ask");
	}

	@Test
	@DisplayName("taking the outfit off and putting it on again clears the blown flag")
	void reWearingClearsBlown() {
		dress();
		PoliceOutfit outfit = outfit();
		outfit.onCrime(wearer.player);

		wearer.naked();
		assertFalse(outfit.hides(wearer.player, 1), "not worn");
		dress();

		assertTrue(outfit.hides(wearer.player, 1));
	}

	@Test
	@DisplayName("a crime without the outfit blows nothing")
	void crimeWithoutOutfitBlowsNothing() {
		PoliceOutfit outfit = outfit();
		outfit.onCrime(wearer.player);

		dress();

		assertTrue(outfit.hides(wearer.player, 1));
	}

	@Test
	@DisplayName("clear forgets a blown outfit (chase end)")
	void clearForgetsBlown() {
		dress();
		PoliceOutfit outfit = outfit();
		outfit.onCrime(wearer.player);

		outfit.clear(wearer.id);

		assertTrue(outfit.hides(wearer.player, 1));
	}

	@Test
	@DisplayName("the Bartizan-backed keys answer null for no item and for a plain item (Bartizan absent in tests)")
	void bartizanKeysAreNullSafe() {
		WearableKeys keys = PoliceOutfit.bartizanKeys();
		assertNotNull(keys);

		ItemStack plain = mock(ItemStack.class);
		when(plain.getType()).thenReturn(Material.STONE);

		assertDoesNotThrow(() -> keys.keyOf(null));
		assertNull(keys.keyOf(null));
		assertDoesNotThrow(() -> keys.keyOf(plain));
		assertNull(keys.keyOf(plain));
	}
}
