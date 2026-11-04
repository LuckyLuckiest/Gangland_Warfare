package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFixtures.Wearer;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-DIS-1 / P-DIS-5: the AI tick keeps the description current while a cop of the chase sees and recognises him, and
 * the squad leader radios {@code Suspect_Description} once per chase and again when the text changes. The line is said through
 * {@code CopRadio.sayFromLeader(group, "Suspect_Description")}; {@code %suspect%} comes from {@link DisguiseTick#extras}.
 */
@DisplayName("DisguiseTick - description follows while seen, radioed once per change")
class DisguiseTickTest {

	private static final String LINE = "Suspect_Description";

	private Wearer              wearer;
	private DisguiseSettings    settings;
	private SuspectDescriptions descriptions;
	private CopRadio            radio;
	private CopGroup            group;
	private NpcSquad            squad;
	private PoliceOutfit        outfit;
	private DisguiseFilter      filter;
	private DisguiseTick        tick;
	private int                 level;
	private boolean             searching;
	private boolean             eyes;

	@BeforeEach
	void setUp() {
		wearer       = new Wearer();
		settings     = DisguiseSettings.DEFAULT;
		descriptions = new SuspectDescriptions(() -> settings, wearer.keys(), DisguiseFixtures.words());
		radio        = mock(CopRadio.class);
		squad        = mock(NpcSquad.class);
		group        = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(squad);
		when(radio.sayFromLeader(any(CopGroup.class), anyString())).thenReturn(true);
		level        = 1;
		searching    = false;
		eyes         = false;
		outfit       = new PoliceOutfit(() -> settings, wearer.keys());
		filter       = new DisguiseFilter(() -> settings, descriptions, outfit, p -> level, p -> level > 0 || searching);
		tick         = new DisguiseTick(() -> settings, descriptions, filter, outfit, (p, g) -> eyes, radio);
	}

	@Test
	@DisplayName("while a cop of the chase sees him the stored description follows what the player wears")
	void descriptionFollowsWhileSeen() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		tick.tick(wearer.player, group);

		assertEquals("blue jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("with no cop seeing him the stored description does not change")
	void descriptionFrozenWhileUnseen() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		tick.tick(wearer.player, group);

		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("a seen player with no stored description gets one")
	void seenPlayerWithoutDescriptionGetsOne() {
		wearer.leatherChest(Color.RED);
		eyes = true;

		tick.tick(wearer.player, group);

		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("three ticks with the same description say one Suspect_Description; a change says a second")
	void suspectLineSpokenOncePerChange() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;

		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);
		verify(radio, times(1)).sayFromLeader(group, LINE);

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);

		verify(radio, times(2)).sayFromLeader(group, LINE);
	}

	@Test
	@DisplayName("a leader who could not speak (no leader yet) is retried on the next tick")
	void unspokenLineIsRetried() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;
		when(radio.sayFromLeader(group, LINE)).thenReturn(false, true);

		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);

		verify(radio, times(2)).sayFromLeader(group, LINE);
	}

	@Test
	@DisplayName("the chase ending (no group) forgets what was said, so the next chase says it again")
	void nextChaseSaysItAgain() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;

		tick.tick(wearer.player, group);
		tick.tick(wearer.player, null);
		tick.tick(wearer.player, group);

		verify(radio, times(2)).sayFromLeader(group, LINE);
	}

	@Test
	@DisplayName("a chase end (forget) lets a repeat offender in the same clothes hear the line again")
	void chaseEndForgetsTheSpokenLine() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;

		tick.tick(wearer.player, group);
		tick.forget(wearer.id);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		tick.tick(wearer.player, group);

		verify(radio, times(2)).sayFromLeader(group, LINE);
	}

	@Test
	@DisplayName("no description known and nobody sees the player: nothing said")
	void nothingToSay() {

		tick.tick(wearer.player, group);

		verify(radio, never()).sayFromLeader(any(CopGroup.class), anyString());
	}

	@Test
	@DisplayName("Disguise.Enable false: no refresh and no line")
	void disabledDoesNothing() {
		settings = new DisguiseSettings(false, settings.maskItems(), settings.maskWearables(), settings.policeOutfit(),
		                                settings.outfitMaxLevel(), settings.changesNeeded());
		wearer.leatherChest(Color.RED);
		eyes = true;

		tick.tick(wearer.player, group);

		verify(radio, never()).sayFromLeader(any(CopGroup.class), anyString());
		assertNull(descriptions.latest(wearer.id));
	}

	@Test
	@DisplayName("%suspect% for the hunted id is the formatted latest description before any tick; unknown for none")
	void extrasNameTheHuntedBeforeAnyTick() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));

		assertEquals("masked in a carved pumpkin, wearing red jacket, on foot", tick.extras(wearer.id).get("suspect"));
		assertEquals("unknown suspect", tick.extras((UUID) null).get("suspect"));
		assertEquals("unknown suspect", tick.extras(UUID.randomUUID()).get("suspect"));
	}

	@Test
	@DisplayName("T-342: a fresh squad or Disguise.Enable false still gets a non-empty %suspect% (the Unknown word)")
	void extrasFallBackToUnknownWord() {
		assertEquals("unknown suspect", tick.extras(wearer.id).get("suspect"));
		settings = new DisguiseSettings(false, settings.maskItems(), settings.maskWearables(), settings.policeOutfit(),
		                                settings.outfitMaxLevel(), settings.changesNeeded());
		tick.tick(wearer.player, group);
		assertEquals("unknown suspect", tick.extras(wearer.id).get("suspect"));
	}

	@Test
	@DisplayName("D10: drawing a weapon changes the line, so it is radioed again")
	void drawingAWeaponChangesTheLine() {
		wearer.leatherChest(Color.RED).hand(null);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;

		tick.tick(wearer.player, group);
		wearer.hand(Material.DIAMOND_SWORD);
		tick.tick(wearer.player, group);

		verify(radio, times(2)).sayFromLeader(group, LINE);
	}

	@Test
	@DisplayName("411: after a clear, a cop seeing a player nobody hunts neither re-learns nor re-speaks the look")
	void noRelearnOrRespeakAfterAClear() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;
		tick.tick(wearer.player, group);
		verify(radio, times(1)).sayFromLeader(group, LINE);

		descriptions.clear(wearer.id);
		tick.forget(wearer.id);
		level = 0;
		tick.tick(wearer.player, group);

		assertNull(descriptions.latest(wearer.id));
		verify(radio, times(1)).sayFromLeader(group, LINE);
	}

	@Test
	@DisplayName("a player nobody hunts ticked with no group (shutdown, emptied group) loses the stale description")
	void unhuntedNullGroupClearsTheDescription() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		level = 0;

		tick.tick(wearer.player, null);

		assertNull(descriptions.latest(wearer.id));
	}

	@Test
	@DisplayName("0 stars but still searched for: the description is kept")
	void searchKeepsTheDescription() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		level     = 0;
		searching = true;

		tick.tick(wearer.player, group);

		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	private void dressAsCop() {
		wearer.helmet(Material.IRON_HELMET, "police_helmet").chest(Material.IRON_CHESTPLATE, "police_vest");
	}

	@Test
	@DisplayName("841: the squad timer is fresh but no cop sees him: a change is not learned")
	void changeOutOfSightIsNotLearnedWhileTimerFresh() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		when(squad.hasFreshSighting()).thenReturn(true);
		eyes = false;

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		tick.tick(wearer.player, group);

		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("D2: one tick with no cop seeing him drops recognition; a change made then is not learned")
	void oneBlindTickDropsRecognition() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));

		eyes = true;
		tick.tick(wearer.player, group);
		assertTrue(descriptions.isRecognised(wearer.id), "seen and matching: recognised");

		eyes = false;
		tick.tick(wearer.player, group);
		assertFalse(descriptions.isRecognised(wearer.id), "one blind tick: dropped");

		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		eyes = true;
		tick.tick(wearer.player, group);

		assertFalse(descriptions.isRecognised(wearer.id), "two pieces changed out of sight");
		assertEquals("carved pumpkin", descriptions.latest(wearer.id).head());
		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("P-DIS-5: a change made while a cop watches is learned")
	void changeInPlainViewIsLearned() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;
		tick.tick(wearer.player, group);

		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		tick.tick(wearer.player, group);

		assertEquals("", descriptions.latest(wearer.id).head());
		assertEquals("blue jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("D7: the search giving up (no WantedEndEvent) clears a blown outfit")
	void searchGaveUpClearsBlown() {
		dressAsCop();
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		outfit.onCrime(wearer.player);
		assertFalse(outfit.hides(wearer.player, 2), "blown");

		level     = 0;
		searching = false;
		tick.tick(wearer.player, group);

		assertTrue(outfit.hides(wearer.player, 2), "nobody hunts him: the outfit fools them again");
	}

	@Test
	@DisplayName("D7: putting the police outfit on in view blows it, and a blind tick does not un-blow it")
	void outfitPutOnInViewIsBlown() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		level = 2;
		eyes  = true;
		tick.tick(wearer.player, group);

		dressAsCop();
		tick.tick(wearer.player, group);
		assertFalse(outfit.hides(wearer.player, 2), "seen putting it on");

		eyes = false;
		tick.tick(wearer.player, group);
		assertFalse(outfit.hides(wearer.player, 2), "still blown after a blind tick");
	}
}
