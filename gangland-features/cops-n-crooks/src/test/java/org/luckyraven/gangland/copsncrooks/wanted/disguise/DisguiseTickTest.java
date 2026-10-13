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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * P-DIS-1 / P-DIS-5: the AI tick keeps the description current while a cop has a fresh sighting and the squad leader
 * radios {@code Suspect_Description} once per chase and again when the text changes. The line is said through
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
	private DisguiseTick        tick;

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
		tick = new DisguiseTick(() -> settings, descriptions, radio);
	}

	@Test
	@DisplayName("while a cop has a fresh sighting the stored description follows what the player wears")
	void descriptionFollowsWhileSeen() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		when(squad.hasFreshSighting()).thenReturn(true);

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		tick.tick(wearer.player, group);

		assertEquals("blue jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("with no fresh sighting the stored description does not change")
	void descriptionFrozenWhileUnseen() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		when(squad.hasFreshSighting()).thenReturn(false);

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		tick.tick(wearer.player, group);

		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("a seen player with no stored description gets one")
	void seenPlayerWithoutDescriptionGetsOne() {
		wearer.leatherChest(Color.RED);
		when(squad.hasFreshSighting()).thenReturn(true);

		tick.tick(wearer.player, group);

		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("three ticks with the same description say one Suspect_Description; a change says a second")
	void suspectLineSpokenOncePerChange() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		when(squad.hasFreshSighting()).thenReturn(true);

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
		when(squad.hasFreshSighting()).thenReturn(true);
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
		when(squad.hasFreshSighting()).thenReturn(true);

		tick.tick(wearer.player, group);
		tick.tick(wearer.player, null);
		tick.tick(wearer.player, group);

		verify(radio, times(2)).sayFromLeader(group, LINE);
	}

	@Test
	@DisplayName("no description known and nobody sees the player: nothing said")
	void nothingToSay() {
		when(squad.hasFreshSighting()).thenReturn(false);

		tick.tick(wearer.player, group);

		verify(radio, never()).sayFromLeader(any(CopGroup.class), anyString());
	}

	@Test
	@DisplayName("Disguise.Enable false: no refresh and no line")
	void disabledDoesNothing() {
		settings = new DisguiseSettings(false, settings.maskItems(), settings.maskWearables(), settings.policeOutfit(),
		                                settings.outfitMaxLevel(), settings.matchNeeded());
		wearer.leatherChest(Color.RED);
		when(squad.hasFreshSighting()).thenReturn(true);

		tick.tick(wearer.player, group);

		verify(radio, never()).sayFromLeader(any(CopGroup.class), anyString());
		assertNull(descriptions.latest(wearer.id));
	}

	@Test
	@DisplayName("%suspect% for a ticked squad is the formatted latest description; an unknown squad has none")
	void extrasSpeakTheLatestDescription() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		when(squad.hasFreshSighting()).thenReturn(false);
		tick.tick(wearer.player, group);

		assertEquals("unknown suspect in a carved pumpkin, red jacket, on foot", tick.extras(squad).get("suspect"));
		assertTrue(tick.extras(mock(NpcSquad.class)).isEmpty());
	}
}
