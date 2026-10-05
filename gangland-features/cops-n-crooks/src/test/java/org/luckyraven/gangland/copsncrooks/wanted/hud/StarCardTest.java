package org.luckyraven.gangland.copsncrooks.wanted.hud;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.wanted.EvasionState;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link StarCard}: the card and bar texts from the shipped message lines, the bar colours and the zone-edge point. */
@DisplayName("StarCard")
class StarCardTest {

	@TempDir
	Path tempDir;

	@BeforeAll
	static void prime() throws ReflectiveOperationException {
		HudFixtures.primeMoneySymbol();
	}

	private static String plain(String coloured) {
		return ChatColor.stripColor(coloured);
	}

	private static String tierName(String displayName) {
		CopTierConfig tier = mock(CopTierConfig.class);
		when(tier.displayName()).thenReturn(displayName);
		return CopRadio.tierName(tier);
	}

	@Test
	@DisplayName("the raise card names the crime, the tier (singular, as the tier config spells it) and the cuffs")
	void raiseCard_namesCrimeTierAndCuffs() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);

		assertEquals("Assault on an officer: Sergeant inbound, they still want you in cuffs",
		             plain(StarCard.raiseCard(messages, messages.crimeName("Assault_Cop"), tierName("&9Sergeant"),
		                                      false)));
	}

	@Test
	@DisplayName("a tier that skips the cuffs says they shoot first")
	void raiseCard_skipCuffingTier_saysShootFirst() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);

		assertEquals("Assault on an officer: Sergeant inbound, they shoot first",
		             plain(StarCard.raiseCard(messages, "Assault on an officer", "Sergeant", true)));
	}

	@Test
	@DisplayName("with no crime on record the card names the reported crime")
	void raiseCard_noCrime_usesReportedCrime() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);

		assertEquals("Reported crime: Officer inbound, they still want you in cuffs",
		             plain(StarCard.raiseCard(messages, messages.crimeName("Unknown_Crime"), "Officer", false)));
	}

	@Test
	@DisplayName("the drop card depends on why the star went")
	void dropCard_perCause() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);

		assertEquals("You stayed out of sight", plain(StarCard.dropCard(messages, WantedCause.EVASION)));
		assertEquals("The trail went cold", plain(StarCard.dropCard(messages, WantedCause.DECAY)));
		assertEquals("A star is gone", plain(StarCard.dropCard(messages, WantedCause.ADMIN)));
	}

	@Test
	@DisplayName("the searching title carries the stars and the countdown")
	void barTitle_searching_showsTheCountdown() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);

		assertEquals("** SEARCHING 45s", plain(StarCard.barTitle(messages, EvasionState.SEARCHING, "**", 45)));
		assertEquals("** SEARCHING 1m 05s", plain(StarCard.barTitle(messages, EvasionState.SEARCHING, "**", 65)));
		assertEquals("** IN SIGHT", plain(StarCard.barTitle(messages, EvasionState.SEEN, "**", 0)));
		assertEquals("** STAR LOST", plain(StarCard.barTitle(messages, EvasionState.EVADED, "**", 0)));
	}

	@Test
	@DisplayName("red when seen, yellow and white while searching, green when a star is lost")
	void barColor_flashesYellowAndWhite() {
		assertEquals(BarColor.RED, StarCard.barColor(EvasionState.SEEN, true));
		assertEquals(BarColor.YELLOW, StarCard.barColor(EvasionState.SEARCHING, true));
		assertEquals(BarColor.WHITE, StarCard.barColor(EvasionState.SEARCHING, false));
		assertEquals(BarColor.GREEN, StarCard.barColor(EvasionState.EVADED, true));
	}

	@Test
	@DisplayName("the exit point sits on the zone edge towards the player, at the player's height")
	void exitPoint_isOnTheZoneEdgeTowardsThePlayer() {
		World    world  = mock(World.class);
		Location centre = new Location(world, 100, 64, 100);

		Location east = StarCard.exitPoint(centre, 40, new Location(world, 110, 70, 100));
		assertEquals(140, east.getX(), 1.0E-9);
		assertEquals(100, east.getZ(), 1.0E-9);
		assertEquals(70, east.getY(), 1.0E-9);

		Location diagonal = StarCard.exitPoint(centre, 50, new Location(world, 103, 64, 104));
		assertEquals(130, diagonal.getX(), 1.0E-9);
		assertEquals(140, diagonal.getZ(), 1.0E-9);

		Location atCentre = StarCard.exitPoint(centre, 40, centre);
		assertEquals(140, atCentre.getX(), 1.0E-9);
		assertEquals(100, atCentre.getZ(), 1.0E-9);
	}

	@Test
	@DisplayName("outside the zone the exit point keeps pointing away from the centre")
	void exitPoint_outsideTheZonePointsAwayFromTheCentre() {
		World    world  = mock(World.class);
		Location centre = new Location(world, 100, 64, 100);

		Location out = StarCard.exitPoint(centre, 40, new Location(world, 160, 64, 100));
		assertEquals(200, out.getX(), 1.0E-9);
		assertEquals(100, out.getZ(), 1.0E-9);
	}
}
