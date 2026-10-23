package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFixtures.Wearer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** P-DIS-1 / P-DIS-2 / P-DIS-3: describing a player, comparing two looks, and the words the radio speaks. */
@DisplayName("SuspectDescriptions - describe, match and format")
class SuspectDescriptionsTest {

	private Wearer wearer;

	@BeforeEach
	void setUp() {
		wearer = new Wearer();
	}

	private SuspectDescriptions descriptions() {
		return DisguiseFixtures.descriptions(wearer, DisguiseSettings.DEFAULT);
	}

	@Test
	@DisplayName("a configured mask material makes an unknown suspect in that mask")
	void maskMakesUnknownSuspect() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED).inCar(false);

		SuspectDescription d = descriptions().describe(wearer.player);

		assertTrue(d.masked());
		assertEquals("carved pumpkin", d.head());
		assertTrue(descriptions().format(d).startsWith("unknown suspect in a carved pumpkin"), descriptions().format(d));
	}

	@Test
	@DisplayName("the whole line reads: unknown suspect in a carved pumpkin, red jacket, on foot")
	void fullLine() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);

		assertEquals("unknown suspect in a carved pumpkin, red jacket, on foot",
		             descriptions().format(descriptions().describe(wearer.player)));
	}

	@Test
	@DisplayName("a Bartizan wearable listed in Mask_Wearables is a mask named after its key")
	void maskWearableKey() {
		wearer.helmet(Material.LEATHER_HELMET, "black_mask");
		DisguiseSettings settings = new DisguiseSettings(true, List.of(), List.of("black_mask"), List.of(), 2, 2);

		SuspectDescription d = DisguiseFixtures.descriptions(wearer, settings).describe(wearer.player);

		assertTrue(d.masked());
		assertEquals("black mask", d.head());
	}

	@Test
	@DisplayName("a wearable not listed in Mask_Wearables is just a hat: described, not masked")
	void unlistedWearableIsNotAMask() {
		wearer.helmet(Material.LEATHER_HELMET, "black_mask");

		SuspectDescription d = descriptions().describe(wearer.player);

		assertFalse(d.masked());
		assertEquals("leather helmet", d.head());
		assertTrue(descriptions().format(d).startsWith("suspect"), descriptions().format(d));
	}

	@Test
	@DisplayName("an ordinary helmet is the head text but never a mask")
	void ordinaryHelmetIsNotMasked() {
		wearer.helmet(Material.IRON_HELMET);

		SuspectDescription d = descriptions().describe(wearer.player);

		assertFalse(d.masked());
		assertEquals("iron helmet", d.head());
	}

	@Test
	@DisplayName("nothing on the head is an empty head and not masked")
	void bareHead() {
		SuspectDescription d = descriptions().describe(wearer.player);

		assertEquals("", d.head());
		assertFalse(d.masked());
	}

	@Test
	@DisplayName("a red leather chestplate is a red jacket; a blue one a blue jacket (nearest dye colour)")
	void leatherChestColourNamed() {
		wearer.leatherChest(Color.RED);
		assertEquals("red jacket", descriptions().describe(wearer.player).chest());

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		assertEquals("blue jacket", descriptions().describe(wearer.player).chest());
	}

	@Test
	@DisplayName("a non-leather chest piece is its material; no chest piece is empty")
	void chestMaterialAndNone() {
		wearer.chest(Material.IRON_CHESTPLATE);
		assertEquals("iron chestplate", descriptions().describe(wearer.player).chest());

		wearer.chest(null);
		assertEquals("", descriptions().describe(wearer.player).chest());
	}

	@Test
	@DisplayName("with no chest piece the line leaves no empty slot between commas")
	void formatSkipsEmptyChest() {
		assertEquals("suspect, on foot", descriptions().format(descriptions().describe(wearer.player)));
	}

	@Test
	@DisplayName("the held weapon is its material; empty hands, air and null say so")
	void weaponText() {
		wearer.hand(Material.DIAMOND_SWORD);
		assertEquals("diamond sword", descriptions().describe(wearer.player).weapon());

		wearer.hand(Material.AIR);
		assertEquals(SuspectDescriptions.EMPTY_HANDS, descriptions().describe(wearer.player).weapon());

		wearer.hand(null);
		assertEquals(SuspectDescriptions.EMPTY_HANDS, descriptions().describe(wearer.player).weapon());
	}

	@Test
	@DisplayName("in a car when the player has a vehicle")
	void inCar() {
		wearer.inCar(true);
		SuspectDescription d = descriptions().describe(wearer.player);

		assertTrue(d.inCar());
		assertTrue(descriptions().format(d).endsWith("in a car"), descriptions().format(d));
	}

	@Test
	@DisplayName("two of head, chest and vehicle still equal: a match; only one equal: no match")
	void matchNeedsTwoOfThree() {
		SuspectDescription stored = new SuspectDescription("carved pumpkin", "red jacket", "", false, true);

		wearer.helmet(null).leatherChest(Color.RED).inCar(false);
		assertTrue(descriptions().matches(wearer.player, stored, 2), "chest and vehicle still equal");

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		assertFalse(descriptions().matches(wearer.player, stored, 2), "only the vehicle equals");
	}

	@Test
	@DisplayName("Match_Needed 3 wants all three; the held weapon never counts")
	void matchNeededThreeAndWeaponIgnored() {
		SuspectDescription stored = new SuspectDescription("", "red jacket", "pistol", false, false);

		wearer.leatherChest(Color.RED).hand(Material.DIAMOND_SWORD);
		assertTrue(descriptions().matches(wearer.player, stored, 3), "weapon differs but is descriptive only");

		wearer.inCar(true);
		assertFalse(descriptions().matches(wearer.player, stored, 3));
	}

	@Test
	@DisplayName("store, latest and clear keep the newest description per player")
	void storeLatestClear() {
		SuspectDescriptions store  = descriptions();
		SuspectDescription  first  = new SuspectDescription("", "red jacket", "", false, false);
		SuspectDescription  second = new SuspectDescription("", "blue jacket", "", false, false);

		assertNull(store.latest(wearer.id));
		store.store(wearer.id, first);
		store.store(wearer.id, second);
		assertEquals(second, store.latest(wearer.id));

		store.clear(wearer.id);
		assertNull(store.latest(wearer.id));
	}

	@Test
	@DisplayName("format honours the configured Suspect.Format template")
	void formatHonoursTemplate() {
		org.luckyraven.gangland.copsncrooks.wanted.WantedMessages messages = DisguiseFixtures.words();
		when(messages.format(org.mockito.ArgumentMatchers.eq(
				org.luckyraven.gangland.copsncrooks.wanted.WantedMessages.Key.SUSPECT_FORMAT),
		                     org.mockito.ArgumentMatchers.anyMap())).thenReturn("TEMPLATE");
		SuspectDescriptions subject = new SuspectDescriptions(() -> DisguiseSettings.DEFAULT, item -> null, messages);

		assertTrue(subject.format(new SuspectDescription("", "red jacket", "", false, false)).contains("TEMPLATE"));
	}

	@Test
	@DisplayName("T-353: an AIR helmet slot is empty, not an item named air")
	void airHelmetIsEmpty() {
		wearer.helmet(Material.AIR);

		assertEquals("", descriptions().describe(wearer.player).head());
	}

	@Test
	@DisplayName("T-353: an AIR chest slot is empty, so the radio never says air")
	void airChestIsEmpty() {
		wearer.chest(Material.AIR);

		SuspectDescription d = descriptions().describe(wearer.player);

		assertEquals("", d.chest());
		assertFalse(descriptions().format(d).contains("air"), descriptions().format(d));
	}
}
