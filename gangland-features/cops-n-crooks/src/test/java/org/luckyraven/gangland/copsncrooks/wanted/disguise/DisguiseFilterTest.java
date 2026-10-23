package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFixtures.Wearer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** P-DIS-3 / P-DIS-4 / P-DIS-5: may a cop pick this player up? */
@DisplayName("DisguiseFilter - lock-on decision")
class DisguiseFilterTest {

	private Wearer             wearer;
	private DisguiseSettings   settings;
	private SuspectDescriptions descriptions;
	private PoliceOutfit       outfit;
	private int                level;
	private boolean            fresh;
	private DisguiseFilter     filter;
	private CopNpc             cop;

	@BeforeEach
	void setUp() {
		wearer       = new Wearer();
		settings     = DisguiseSettings.DEFAULT;
		descriptions = new SuspectDescriptions(() -> settings, wearer.keys(), DisguiseFixtures.words());
		outfit       = new PoliceOutfit(() -> settings, wearer.keys());
		level        = 1;
		fresh        = false;
		filter       = new DisguiseFilter(() -> settings, descriptions, outfit, p -> level, p -> fresh);
		cop          = mock(CopNpc.class);
	}

	/** The crime snapshot the listener takes. */
	private void crime() {
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		outfit.onCrime(wearer.player);
	}

	private void dressAsCop() {
		wearer.helmet(Material.IRON_HELMET, "police_helmet").chest(Material.IRON_CHESTPLATE, "police_vest");
	}

	@Test
	@DisplayName("no description yet: the cop may lock on")
	void noDescriptionAllows() {
		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("looking the same as at the crime: allowed")
	void sameLookAllows() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("no fresh sighting and two of three changed: the cop may not lock on")
	void mismatchWhileUnseenBlocksLockOn() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();

		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));

		assertFalse(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("only one of three changed is still the suspect")
	void oneChangeStillMatches() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();

		wearer.helmet(null);

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("a fresh sighting always allows, mismatch or not")
	void freshSightingAlwaysAllows() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		fresh = true;

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("a new crime re-snapshots the look: the cops lock on again")
	void newCrimeAfterSwapReacquires() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		assertFalse(filter.allows(cop, wearer.player));

		crime();

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("Disguise.Enable false: always allowed")
	void disabledAlwaysAllows() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		settings = new DisguiseSettings(false, settings.maskItems(), settings.maskWearables(), settings.policeOutfit(),
		                                settings.outfitMaxLevel(), settings.matchNeeded());

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("the police outfit hides up to Outfit_Max_Level stars and not above it")
	void policeOutfitHidesUpToTwoStars() {
		dressAsCop();
		level = 2;
		assertFalse(filter.allows(cop, wearer.player), "level 2: hidden");

		level = 3;
		assertTrue(filter.allows(cop, wearer.player), "level 3: the outfit no longer works");
	}

	@Test
	@DisplayName("the outfit hides the player even with no description at all and a fresh sighting")
	void outfitBeatsEverythingBelow() {
		dressAsCop();
		fresh = true;

		assertFalse(filter.allows(cop, wearer.player), "pursuitRange supplies the fresh-sighting clause itself");
	}

	@Test
	@DisplayName("a crime committed in the outfit blows it until it is taken off and put on again")
	void crimeInOutfitBlowsIt() {
		dressAsCop();
		level = 1;
		assertFalse(filter.allows(cop, wearer.player));

		crime();
		assertTrue(filter.allows(cop, wearer.player), "blown: same look as the description, so a match");

		wearer.naked();
		filter.allows(cop, wearer.player);
		dressAsCop();

		assertFalse(filter.allows(cop, wearer.player), "re-worn: hidden again");
	}

	@Test
	@DisplayName("Match_Needed 3 demands the full look")
	void matchNeededFromSettings() {
		settings = new DisguiseSettings(true, List.of("CARVED_PUMPKIN"), List.of(), List.of(), 2, 3);
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();

		wearer.helmet(null);

		assertFalse(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("test() is allows(): it plugs straight into CopPerception.addLockOnFilter")
	void testDelegatesToAllows() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));

		assertFalse(filter.test(cop, wearer.player));
	}
}
