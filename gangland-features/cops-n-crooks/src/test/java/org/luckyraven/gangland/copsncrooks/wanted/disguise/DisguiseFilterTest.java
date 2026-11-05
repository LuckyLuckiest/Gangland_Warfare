package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFixtures.Wearer;
import org.luckyraven.keystone.npc.NpcDifficulty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
	private boolean            hunted;
	private NpcDifficulty      difficulty;
	private DisguiseFilter     filter;
	private CopNpc             cop;

	@BeforeEach
	void setUp() {
		wearer       = new Wearer();
		settings     = DisguiseSettings.DEFAULT;
		descriptions = new SuspectDescriptions(() -> settings, wearer.keys(), DisguiseFixtures.words());
		outfit       = new PoliceOutfit(() -> settings, wearer.keys());
		level        = 1;
		hunted       = true;
		difficulty   = NpcDifficulty.EASY;
		filter       = new DisguiseFilter(() -> settings, descriptions, outfit, p -> level, p -> hunted, p -> difficulty);
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
	@DisplayName("not recognised and two pieces changed: the cop may not lock on")
	void mismatchWhileUnseenBlocksLockOn() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();

		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));

		assertFalse(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("EASY and NORMAL keep the base two changes: one piece is still him, two are not")
	void easyAndNormalKeepTwoChanges() {
		for (NpcDifficulty each : List.of(NpcDifficulty.EASY, NpcDifficulty.NORMAL)) {
			difficulty = each;
			wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
			crime();

			wearer.helmet(null);
			assertTrue(filter.allows(cop, wearer.player), each + ": one piece");

			wearer.leatherChest(Color.fromRGB(20, 20, 250));
			assertFalse(filter.allows(cop, wearer.player), each + ": two pieces");
		}
	}

	@Test
	@DisplayName("HARD needs three changes: two pieces still fool the filter into allowing, three do not")
	void hardNeedsThreeChanges() {
		difficulty = NpcDifficulty.HARD;
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();

		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		assertTrue(filter.allows(cop, wearer.player), "two pieces");

		wearer.legs(Material.IRON_LEGGINGS);
		assertFalse(filter.allows(cop, wearer.player), "three pieces");
	}

	@Test
	@DisplayName("DEADLY needs four changes")
	void deadlyNeedsFourChanges() {
		difficulty = NpcDifficulty.DEADLY;
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();

		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250)).legs(Material.IRON_LEGGINGS);
		assertTrue(filter.allows(cop, wearer.player), "three pieces");

		wearer.boots(Material.IRON_BOOTS);
		assertFalse(filter.allows(cop, wearer.player), "four pieces");
	}

	@Test
	@DisplayName("a deployed Changes_Needed adds to the scale: base 1 at HARD is two")
	void deployedChangesNeededAddsToTheScale() {
		settings   = new DisguiseSettings(true, List.of("CARVED_PUMPKIN"), List.of(), List.of(), 2, 1);
		difficulty = NpcDifficulty.HARD;
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();

		wearer.helmet(null);
		assertTrue(filter.allows(cop, wearer.player), "one piece");

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		assertFalse(filter.allows(cop, wearer.player), "two pieces");
	}

	@Test
	@DisplayName("862: a blown police outfit is known whatever else changed")
	void blownOutfitNeverFools() {
		dressAsCop();
		crime();
		wearer.naked();
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		dressAsCop();

		assertTrue(filter.allows(cop, wearer.player), "blown: two pieces differ, still known");
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
	@DisplayName("recognised always allows, mismatch or not")
	void recognisedAlwaysAllows() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		descriptions.setRecognised(wearer.id, true);

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("841: the squad timer alone no longer allows a mismatch (the filter no longer reads it)")
	void timerAloneNoLongerAllows() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));

		assertFalse(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("recognised in the police outfit at 2 stars: allowed")
	void recognisedBeatsTheOutfit() {
		dressAsCop();
		level = 2;
		descriptions.setRecognised(wearer.id, true);

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("354: the recognised path logs the seen state")
	void recognisedPathRecordsTheSeenState() {
		descriptions.setRecognised(wearer.id, true);

		filter.allows(cop, wearer.player);

		assertEquals("seen", filter.lastState(wearer.id));
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
		                                settings.outfitMaxLevel(), settings.changesNeeded());

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
	@DisplayName("the outfit hides the player even with no description at all")
	void outfitBeatsEverythingBelow() {
		dressAsCop();

		assertFalse(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("848: a crime committed in the outfit blows it for the chase, taken off and put on again or not")
	void crimeInOutfitBlowsItForTheChase() {
		dressAsCop();
		level = 1;
		assertFalse(filter.allows(cop, wearer.player));

		crime();
		assertTrue(filter.allows(cop, wearer.player), "blown: same look as the description, so a match");

		wearer.naked();
		filter.allows(cop, wearer.player);
		dressAsCop();

		assertTrue(filter.allows(cop, wearer.player), "re-worn: still blown");
	}

	@Test
	@DisplayName("Changes_Needed 1: taking the mask off alone is enough to shake them")
	void changesNeededFromSettings() {
		settings = new DisguiseSettings(true, List.of("CARVED_PUMPKIN"), List.of(), List.of(), 2, 1);
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

	@Test
	@DisplayName("430: a player nobody hunts is not filtered by a stale description")
	void unhuntedPlayerIgnoresAStaleDescription() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		level  = 0;
		hunted = false;

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("the police outfit at 0 stars still hides a player nobody hunts")
	void outfitStillHidesWhenNotHunted() {
		dressAsCop();
		level  = 0;
		hunted = false;

		assertFalse(filter.allows(cop, wearer.player));
	}
}
