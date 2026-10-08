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
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.DropPlan;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop.Ending;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.wanted.EvasionState;

import org.bukkit.boss.BarColor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

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
	@DisplayName("an evasion drop with a pending AUTO plan picks the card of its ending and fills %count%")
	void dropCard_pendingPlan_picksTheEndingCard() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);

		assertEquals("Small fry, the heat is off you for now (-3)",
		             plain(StarCard.dropCard(messages, WantedCause.EVASION, new DropPlan(3, Ending.PETTY, "petty"), 3)));
		assertEquals("The trail is fading (-4)", plain(StarCard.dropCard(messages, WantedCause.EVASION,
		                                                                       new DropPlan(4, Ending.COLD_TRAIL, "cold"), 4)));
		assertEquals("Clean break, you left the area (-2)", plain(StarCard.dropCard(messages, WantedCause.EVASION,
		                                                                           new DropPlan(2, Ending.CLEAN_BREAK, "out"), 2)));
		assertEquals("Still hot, one star at a time", plain(StarCard.dropCard(messages, WantedCause.EVASION,
		                                                                      new DropPlan(1, Ending.STILL_HOT, "rampage"), 1)));
		assertEquals("They know your face, one star at a time", plain(StarCard.dropCard(
				messages, WantedCause.EVASION, new DropPlan(1, Ending.HUNKER_DOWN, "known_face"), 1)));
		assertEquals("That was close, they are losing you", plain(StarCard.dropCard(
				messages, WantedCause.EVASION, new DropPlan(1, Ending.HUNKER_DOWN, "narrow"), 1)));
		assertEquals("You stayed out of sight", plain(StarCard.dropCard(
				messages, WantedCause.EVASION, new DropPlan(1, Ending.HUNKER_DOWN, "hunker"), 1)));
	}

	@Test
	@DisplayName("no pending plan, or a cause other than evasion, gives today's card")
	void dropCard_noPlan_orOtherCause_isTodaysCard() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);
		DropPlan       plan     = new DropPlan(3, Ending.PETTY, "petty");

		assertEquals("You stayed out of sight", plain(StarCard.dropCard(messages, WantedCause.EVASION, null, 1)));
		assertEquals("The trail went cold", plain(StarCard.dropCard(messages, WantedCause.DECAY, plan, 3)));
		assertEquals("A star is gone", plain(StarCard.dropCard(messages, WantedCause.ARREST, plan, 3)));
	}

	@Test
	@DisplayName("the lost-star bar names the count from 2 stars lost, and reads as before for 1")
	void barTitle_evaded_namesTheCountFromTwo() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);

		assertEquals("** -3 STARS", plain(StarCard.barTitle(messages, EvasionState.EVADED, "**", 0, 3)));
		assertEquals("** STAR LOST", plain(StarCard.barTitle(messages, EvasionState.EVADED, "**", 0, 1)));
		assertEquals("** SEARCHING 45s", plain(StarCard.barTitle(messages, EvasionState.SEARCHING, "**", 45, 3)));
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

	@Test
	@DisplayName("the arrow is relative to where the player faces: ahead, right, behind, left and the diagonals")
	void arrow_isRelativeToTheFacing() {
		// yaw 0 faces +Z; turning right raises the yaw, so facing +Z the player's right is -X
		assertEquals("↑", StarCard.arrow(0, 0, 10));
		assertEquals("→", StarCard.arrow(0, -10, 0));
		assertEquals("↓", StarCard.arrow(0, 0, -10));
		assertEquals("←", StarCard.arrow(0, 10, 0));
		assertEquals("↗", StarCard.arrow(0, -10, 10));
		assertEquals("↖", StarCard.arrow(0, 10, 10));

		// facing -X (yaw 90) the same -X target is straight ahead; negative yaws wrap
		assertEquals("↑", StarCard.arrow(90, -10, 0));
		assertEquals("↑", StarCard.arrow(-270, -10, 0));
		assertEquals("↓", StarCard.arrow(-90, -10, 0));
	}

	@Test
	@DisplayName("the way hint shows the arrow and the blocks to the zone edge")
	void wayHint_arrowAndDistance() throws IOException {
		WantedMessages messages = HudFixtures.messages(tempDir);
		World          world    = mock(World.class);
		Location       centre   = new Location(world, 100, 64, 100);
		Location       from     = new Location(world, 110, 64, 100, -90, 0);

		// facing +X, 30 blocks short of the east edge
		assertEquals("↑ 30m", plain(StarCard.wayHint(messages, centre, 40, from)).trim());
	}

	@Test
	@DisplayName("the bar colour is read in ROOT locale: a Turkish default still reads WHITE, not WHİTE")
	void bountyColor_isReadInRootLocale() {
		Locale previous = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
		try {
			assertEquals(BarColor.WHITE, StarCard.bountyColor("white"));
		} finally {
			Locale.setDefault(previous);
		}
	}
}
