package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFixtures.Wearer;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doReturn;
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
	private WantedMessages      messages;
	private CopGroup            group;
	private NpcSquad            squad;
	private PoliceOutfit        outfit;
	private DisguiseFilter      filter;
	private DisguiseTick        tick;
	private int                 level;
	private boolean             searching;
	private boolean             eyes;
	private NpcDifficulty       difficulty;
	private long                now;

	@BeforeEach
	void setUp() {
		wearer       = new Wearer();
		settings     = DisguiseSettings.DEFAULT;
		descriptions = new SuspectDescriptions(() -> settings, wearer.keys(), DisguiseFixtures.words());
		radio        = mock(CopRadio.class);
		messages     = DisguiseFixtures.words();
		squad        = mock(NpcSquad.class);
		group        = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(squad);
		when(radio.sayFromLeader(any(CopGroup.class), anyString())).thenReturn(true);
		now          = 0;
		when(radio.now()).thenAnswer(i -> now);
		level        = 1;
		searching    = false;
		eyes         = false;
		difficulty   = NpcDifficulty.EASY;
		outfit       = new PoliceOutfit(() -> settings, wearer.keys());
		filter       = new DisguiseFilter(() -> settings, descriptions, outfit, p -> level, p -> level > 0 || searching,
		                          p -> difficulty);
		tick         = new DisguiseTick(() -> settings, descriptions, filter, outfit, p -> eyes, radio, messages, () -> 6.0);
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

	/** Advances the clock to {@code time} and ticks with a cop seeing the player. */
	private void seenAt(long time) {
		now  = time;
		eyes = true;
		tick.tick(wearer.player, group);
	}

	/** Advances the clock to {@code time} and ticks with no cop seeing the player. */
	private void blindAt(long time) {
		now  = time;
		eyes = false;
		tick.tick(wearer.player, group);
	}

	/** Pumpkin and red jacket, stored as the description: two pieces to change. */
	private void describedInPumpkinAndJacket() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
	}

	/** All four armour slots, stored as the description. */
	private void describedInFullArmour() {
		wearer.helmet(Material.CARVED_PUMPKIN)
		      .leatherChest(Color.RED)
		      .legs(Material.IRON_LEGGINGS)
		      .boots(Material.IRON_BOOTS);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
	}

	private void swapPumpkinAndJacket() {
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
	}

	@Test
	@DisplayName("857: one blind tick keeps recognition, and a change made then is learned at the next sighting")
	void oneBlindTickKeepsRecognition() {
		describedInPumpkinAndJacket();

		seenAt(0);
		assertTrue(descriptions.isRecognised(wearer.id), "seen and matching: recognised");

		blindAt(500);
		assertTrue(descriptions.isRecognised(wearer.id), "one blind tick");

		swapPumpkinAndJacket();
		seenAt(1000);

		assertTrue(descriptions.isRecognised(wearer.id), "two pieces changed inside the memory");
		assertEquals("", descriptions.latest(wearer.id).head());
		assertEquals("blue jacket", descriptions.latest(wearer.id).chest());
	}

	@ParameterizedTest
	@CsvSource({"EASY, 3000", "NORMAL, 5000", "HARD, 10000", "DEADLY, 20000"})
	@DisplayName("857: the memory lasts Memory_Seconds for the difficulty, and ends exactly there")
	void memoryLastsPerDifficulty(NpcDifficulty row, long memory) {
		difficulty = row;
		describedInPumpkinAndJacket();
		seenAt(0);

		blindAt(memory - 1);
		assertTrue(descriptions.isRecognised(wearer.id), "a millisecond short of the memory");

		blindAt(memory);
		assertFalse(descriptions.isRecognised(wearer.id), "the memory ran out");
	}

	@Test
	@DisplayName("857: DEADLY, four pieces off late in the memory, seen: learned")
	void changeInsideTheMemoryIsLearnedAtDeadly() {
		difficulty = NpcDifficulty.DEADLY;
		describedInFullArmour();
		seenAt(0);

		blindAt(19500);
		wearer.naked();
		seenAt(19500);

		assertTrue(descriptions.isRecognised(wearer.id));
		assertEquals("", descriptions.latest(wearer.id).head());
		assertEquals("", descriptions.latest(wearer.id).feet());
	}

	@Test
	@DisplayName("857: a glimpse inside the memory restarts it")
	void glimpseRestartsTheMemory() {
		difficulty = NpcDifficulty.DEADLY;
		describedInPumpkinAndJacket();
		seenAt(0);
		blindAt(15000);
		seenAt(19000);

		blindAt(38000);
		assertTrue(descriptions.isRecognised(wearer.id), "19 s after the glimpse");

		blindAt(39000);
		assertFalse(descriptions.isRecognised(wearer.id), "20 s after the glimpse");
	}

	@Test
	@DisplayName("857 (scenario 4): seen and blind alternating never drops recognition")
	void flickerNeverDrops() {
		describedInPumpkinAndJacket();

		for (long time = 0; time <= 10000; time += 500) {
			if (time % 1000 == 0) {
				seenAt(time);
			} else {
				blindAt(time);
			}
			assertTrue(descriptions.isRecognised(wearer.id), "at " + time);
		}
	}

	@Test
	@DisplayName("D7 (scenario 12): the police outfit put on inside the memory and then seen is blown")
	void outfitPutOnInsideTheMemoryIsBlown() {
		level = 2;
		describedInPumpkinAndJacket();
		seenAt(0);

		blindAt(500);
		dressAsCop();
		seenAt(1000);

		assertFalse(outfit.hides(wearer.player, 2));
	}

	@Test
	@DisplayName("857: a flag left with a clock past the memory (the AI task stopped) is not inherited")
	void staleFlagWithAnExpiredDeadlineIsNotInherited() {
		describedInPumpkinAndJacket();
		seenAt(0);

		swapPumpkinAndJacket();
		seenAt(60000);

		assertFalse(descriptions.isRecognised(wearer.id));
		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("857 (scenario 15): forget drops the memory, so a new chase inherits no recognition")
	void forgetDropsTheMemory() {
		describedInPumpkinAndJacket();
		seenAt(0);

		tick.forget(wearer.id);
		swapPumpkinAndJacket();
		seenAt(500);

		assertFalse(descriptions.isRecognised(wearer.id));
	}

	@Test
	@DisplayName("860 (R3): a cop outside any group has eyes on him, so a tick with no group learns the look")
	void aBeatCopSeesHimWithNoGroup() {
		wearer.leatherChest(Color.RED);
		eyes = true;

		tick.tick(wearer.player, null);

		assertTrue(descriptions.isRecognised(wearer.id), "a cop watching, group or not");
		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("861 (R5): a tick with no group keeps the memory, then it lapses")
	void noGroupKeepsTheMemoryThenLapses() {
		describedInPumpkinAndJacket();
		seenAt(0);
		eyes = false;

		now = 500;
		tick.tick(wearer.player, null);
		assertTrue(descriptions.isRecognised(wearer.id), "inside the memory");

		now = 3000;
		tick.tick(wearer.player, null);
		assertFalse(descriptions.isRecognised(wearer.id), "past the memory");
	}

	@Test
	@DisplayName("857: the memory reads the stars now: rising stars extend it, falling stars lapse it")
	void memoryReadsTheStarsNow() {
		describedInPumpkinAndJacket();
		seenAt(0);

		difficulty = NpcDifficulty.DEADLY;
		blindAt(15000);
		assertTrue(descriptions.isRecognised(wearer.id), "20 s at DEADLY");

		difficulty = NpcDifficulty.EASY;
		blindAt(15500);
		assertFalse(descriptions.isRecognised(wearer.id), "back to 3 s at EASY");
	}

	@Test
	@DisplayName("869 (R11): a cop's report starts the memory, so a swap during it is learned at the next sighting")
	void witnessedThenSwappedAndSeenIsLearned() {
		describedInPumpkinAndJacket();

		tick.witnessed(wearer.player);
		swapPumpkinAndJacket();
		seenAt(500);

		assertTrue(descriptions.isRecognised(wearer.id));
		assertEquals("blue jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("869: a cop's report and no cop in sight: the memory lapses and the look at the crime stands")
	void witnessedAndBlindLapsesAndTheHeldLookStands() {
		describedInPumpkinAndJacket();

		tick.witnessed(wearer.player);
		swapPumpkinAndJacket();
		blindAt(500);
		assertTrue(descriptions.isRecognised(wearer.id), "inside the memory");

		blindAt(3000);

		assertFalse(descriptions.isRecognised(wearer.id), "past the memory");
		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("O11: an escape ends the memory; the description stays for the search")
	void escapeEndsTheMemory() {
		describedInPumpkinAndJacket();
		seenAt(0);

		tick.escaped(wearer.id);
		swapPumpkinAndJacket();
		seenAt(500);

		assertFalse(descriptions.isRecognised(wearer.id));
		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("O11: a cop who still knows the player after an escape says so")
	void aSightingAfterAnEscapeIsTold() {
		describedInPumpkinAndJacket();
		seenAt(0);

		tick.escaped(wearer.id);
		seenAt(500);

		List<String> lines = told();
		assertEquals(2, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("The cops recognise you"), lines.get(1));
	}

	@Test
	@DisplayName("guard: the memory lapsed, then two pieces changed: the next cop is fooled")
	void memoryLapsedThenChangeFools() {
		describedInPumpkinAndJacket();

		seenAt(0);
		assertTrue(descriptions.isRecognised(wearer.id), "seen and matching: recognised");

		blindAt(3000);
		assertFalse(descriptions.isRecognised(wearer.id), "the memory ran out");

		swapPumpkinAndJacket();
		seenAt(3000);

		assertFalse(descriptions.isRecognised(wearer.id), "two pieces changed out of sight");
		assertEquals("carved pumpkin", descriptions.latest(wearer.id).head());
		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("guard: past the DEADLY memory, four pieces fool them")
	void fourPiecesAfterTheDeadlyMemoryFool() {
		difficulty = NpcDifficulty.DEADLY;
		describedInFullArmour();
		seenAt(0);

		blindAt(20000);
		wearer.naked();
		seenAt(20000);

		assertFalse(descriptions.isRecognised(wearer.id));
		assertEquals("carved pumpkin", descriptions.latest(wearer.id).head());
	}

	@Test
	@DisplayName("guard: past the DEADLY memory, three pieces are recognised and the new look is learned")
	void threePiecesAfterTheDeadlyMemoryAreLearned() {
		difficulty = NpcDifficulty.DEADLY;
		describedInFullArmour();
		seenAt(0);

		blindAt(20000);
		wearer.helmet(null).chest(null).legs(null);
		seenAt(20000);

		assertTrue(descriptions.isRecognised(wearer.id));
		assertEquals("", descriptions.latest(wearer.id).head());
		assertEquals("", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("guard: held and blind, no cop learns anything and nothing is blown")
	void heldBlindLearnsAndBlowsNothing() {
		level = 2;
		describedInPumpkinAndJacket();
		seenAt(0);

		dressAsCop();
		blindAt(500);

		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
		assertTrue(outfit.hides(wearer.player, 2));
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
	@DisplayName("D7 (0.21.6): a blow with no chase state to end (a quit, a hit at 0 stars) waits for the next crime")
	void unhuntedTickKeepsABlowWithNoChaseState() {
		dressAsCop();
		outfit.onCrime(wearer.player);
		assertNull(descriptions.latest(wearer.id));

		level = 0;
		tick.tick(wearer.player, null);

		assertFalse(outfit.hides(wearer.player, 2), "the next crime outside a chase clears it (SuspectListener)");
	}

	@Test
	@DisplayName("864 (scenario 24): a hit in the outfit at 0 stars while the old squad walks home survives its AI ticks")
	void aHitAtZeroStarsSurvivesTheWalkHome() {
		level = 0;
		dressAsCop();
		CopNpc cop = hitCop(true);

		tick.exposed(cop, wearer.player);
		tick.tick(wearer.player, group);

		assertTrue(filter.allows(cop, wearer.player), "no free attack during the walk home");
	}

	@Test
	@DisplayName("0.21.6: a cop's report that raised no star leaves no flag: no chase followed it")
	void aReportUnderTheFirstStarLeavesNoFlag() {
		level = 0;
		describedInPumpkinAndJacket();

		tick.witnessed(wearer.player);
		tick.reportLanded(wearer.player);

		assertFalse(descriptions.isRecognised(wearer.id));

		level = 1;
		blindAt(5000);
		assertEquals(List.of(), told(), "a chase with no cop involved is not told the cops lost the player");
	}

	@Test
	@DisplayName("0.21.6: guard - a report that started the chase keeps the memory")
	void aReportThatRaisedAStarKeepsTheMemory() {
		describedInPumpkinAndJacket();

		tick.witnessed(wearer.player);
		tick.reportLanded(wearer.player);

		assertTrue(descriptions.isRecognised(wearer.id));
	}

	@Test
	@DisplayName("0.21.6: guard - learnedAt moves on a sighting and a hit, not on a report; forget and an escape drop it")
	void learnedAtFollowsTheLookTheCopsSaw() {
		describedInPumpkinAndJacket();
		assertNull(tick.learnedAt(wearer.id));

		seenAt(100);
		assertEquals(100L, tick.learnedAt(wearer.id));

		now = 200;
		tick.witnessed(wearer.player);
		assertEquals(100L, tick.learnedAt(wearer.id), "a report teaches no look");

		now = 300;
		tick.exposed(hitCop(true), wearer.player);
		assertEquals(300L, tick.learnedAt(wearer.id));

		tick.escaped(wearer.id);
		assertNull(tick.learnedAt(wearer.id));

		seenAt(400);
		tick.forget(wearer.id);
		assertNull(tick.learnedAt(wearer.id));
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

	/** Every chat line the player was sent so far. */
	private List<String> told() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(wearer.player, atLeast(0)).sendMessage(captor.capture());
		return captor.getAllValues();
	}

	@Test
	@DisplayName("850: three ticks recognised, one Recognised line carrying the description")
	void toldOnceWhenRecognised() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;

		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);

		List<String> lines = told();
		assertEquals(1, lines.size(), lines.toString());
		assertTrue(lines.get(0).contains("The cops recognise you"), lines.get(0));
		assertTrue(lines.get(0).contains("red jacket"), lines.get(0));
	}

	@Test
	@DisplayName("850, 865: recognised, the memory runs out (Lost), two pieces swapped, seen again: one Unrecognised line")
	void toldWhenNoLongerRecognised() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;
		tick.tick(wearer.player, group);

		now  = 3000;
		eyes = false;
		tick.tick(wearer.player, group);
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		now  = 3500;
		eyes = true;
		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);

		List<String> lines = told();
		assertEquals(3, lines.size(), lines.toString());
		assertTrue(lines.get(0).contains("The cops recognise you"), lines.get(0));
		assertTrue(lines.get(1).contains("lost you"), lines.get(1));
		assertTrue(lines.get(2).contains("don't recognise"), lines.get(2));
	}

	@Test
	@DisplayName("865: seen, then the memory runs out unseen: one Lost line naming the look and the pieces")
	void toldOnceWhenTheCopsLoseHim() {
		describedInPumpkinAndJacket();
		seenAt(0);

		blindAt(3000);
		blindAt(3500);

		List<String> lines = told();
		assertEquals(2, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("lost you"), lines.get(1));
		assertTrue(lines.get(1).contains("2 of head"), lines.get(1));
		assertTrue(lines.get(1).contains("carved pumpkin"), lines.get(1));
	}

	@Test
	@DisplayName("865: the Lost line names the count for the difficulty")
	void lostNamesTheCountForTheDifficulty() {
		difficulty = NpcDifficulty.DEADLY;
		describedInPumpkinAndJacket();
		seenAt(0);

		blindAt(20000);

		List<String> lines = told();
		assertEquals(2, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("4 of head"), lines.get(1));
	}

	@Test
	@DisplayName("865: Lost, then a cop sees him in the same look: Recognised is told again")
	void toldRecognisedAgainAfterLost() {
		describedInPumpkinAndJacket();
		seenAt(0);

		blindAt(3000);
		seenAt(3500);

		List<String> lines = told();
		assertEquals(3, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("lost you"), lines.get(1));
		assertTrue(lines.get(2).contains("The cops recognise you"), lines.get(2));
	}

	@Test
	@DisplayName("865: guard - the police outfit at its star cap decides, so no Lost line; the next sighting says Outfit")
	void noLostWhileTheOutfitHides() {
		level = 2;
		describedInPumpkinAndJacket();
		seenAt(0);

		dressAsCop();
		blindAt(3000);

		assertEquals(1, told().size(), told().toString());

		seenAt(3500);

		List<String> lines = told();
		assertEquals(2, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("police outfit"), lines.get(1));
	}

	@Test
	@DisplayName("865: a blown police outfit is known whatever else changes, so no Lost line")
	void noLostInABlownOutfit() {
		dressAsCop();
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		outfit.onCrime(wearer.player);
		seenAt(0);
		assertTrue(descriptions.isRecognised(wearer.id), "blown: known");

		blindAt(3000);

		assertEquals(1, told().size(), told().toString());
	}

	@Test
	@DisplayName("865: Changes_Needed plus Extra_Changes above the five compared pieces: clothes never fool, so no Lost line")
	void noLostWhenClothesCannotFool() {
		difficulty = NpcDifficulty.DEADLY;
		settings   = new DisguiseSettings(true, settings.maskItems(), settings.maskWearables(), settings.policeOutfit(),
		                                  settings.outfitMaxLevel(), settings.changesNeeded(),
		                                  Map.of(NpcDifficulty.DEADLY, new DisguiseSettings.Recognition(5, 20.0)));
		describedInPumpkinAndJacket();
		seenAt(0);

		blindAt(20000);

		assertFalse(descriptions.isRecognised(wearer.id), "the memory ran out");
		assertEquals(1, told().size(), told().toString());
	}

	@Test
	@DisplayName("865: a tick with no group still says Lost")
	void lostIsToldWithNoGroup() {
		describedInPumpkinAndJacket();
		seenAt(0);

		now  = 3000;
		eyes = false;
		tick.tick(wearer.player, null);

		List<String> lines = told();
		assertEquals(2, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("lost you"), lines.get(1));
	}

	@Test
	@DisplayName("865: guard - nothing is said while the memory runs")
	void noLostInsideTheMemory() {
		describedInPumpkinAndJacket();
		seenAt(0);

		blindAt(2999);

		assertEquals(1, told().size(), told().toString());
	}

	@Test
	@DisplayName("850: the police outfit at 2 stars, a cop sees him: one Outfit line")
	void toldWhenTheOutfitHolds() {
		dressAsCop();
		level = 2;
		eyes  = true;

		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);

		List<String> lines = told();
		assertEquals(1, lines.size(), lines.toString());
		assertTrue(lines.get(0).contains("police outfit"), lines.get(0));
	}

	@Test
	@DisplayName("850: guard - nothing is told while no cop of the chase sees him")
	void nothingToldWhileUnseen() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));

		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);

		assertTrue(told().isEmpty());
	}

	@Test
	@DisplayName("850: a blank line ('' in the file) is never sent")
	void blankLineIsNotSent() {
		doReturn("").when(messages).format(any(WantedMessages.Key.class), anyMap());
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;

		tick.tick(wearer.player, group);

		verify(wearer.player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("850: guard - forget (a chase ended) lets the next chase be told again")
	void forgetTellsAgain() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;

		tick.tick(wearer.player, group);
		tick.forget(wearer.id);
		tick.tick(wearer.player, group);

		assertEquals(2, told().size());
	}

	@Test
	@DisplayName("850 (scenario 2): only the tunic changed out of sight, seen again: a second Recognised line, new look")
	void toldAgainWhenRecognisedInANewLook() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;
		tick.tick(wearer.player, group);

		eyes = false;
		tick.tick(wearer.player, group);
		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		eyes = true;
		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);

		List<String> lines = told();
		assertEquals(2, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("The cops recognise you"), lines.get(1));
		assertTrue(lines.get(1).contains("blue jacket"), lines.get(1));
	}

	@Test
	@DisplayName("850 (scenario 5): recognised, the police outfit put on in view: a Recognised line naming the outfit")
	void toldTheOutfitWhenSeenPuttingItOn() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		level = 2;
		eyes  = true;
		tick.tick(wearer.player, group);

		dressAsCop();
		tick.tick(wearer.player, group);

		List<String> lines = told();
		assertEquals(2, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("police helmet"), lines.get(1));
	}

	@Test
	@DisplayName("850: recognised, a new look while the radio is on cooldown: no second line until it is radioed")
	void newLookIsToldWhenRadioed() {
		wearer.leatherChest(Color.RED);
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		eyes = true;
		when(radio.sayFromLeader(group, LINE)).thenReturn(true, false, false, true);
		tick.tick(wearer.player, group);

		wearer.leatherChest(Color.fromRGB(20, 20, 250));
		tick.tick(wearer.player, group);
		tick.tick(wearer.player, group);
		assertEquals(1, told().size(), told().toString());

		tick.tick(wearer.player, group);
		List<String> lines = told();
		assertEquals(2, lines.size(), lines.toString());
		assertTrue(lines.get(1).contains("blue jacket"), lines.get(1));
	}

	/** The cop the player hits from 20 blocks; {@code ray} is whether that cop has a line to them. */
	private CopNpc hitCop(boolean ray) {
		CopNpc cop = mock(CopNpc.class);
		when(cop.hasLineOfSight(wearer.player)).thenReturn(ray);
		when(cop.distanceTo(wearer.player)).thenReturn(20.0);
		return cop;
	}

	@Test
	@DisplayName("864: hitting a cop who sees the player: recognised at once, and the next sighting learns the look")
	void hittingACopInSightRecognisesHim() {
		describedInPumpkinAndJacket();
		swapPumpkinAndJacket();

		tick.exposed(hitCop(true), wearer.player);
		assertTrue(descriptions.isRecognised(wearer.id), "recognised by the hit");

		seenAt(500);
		assertTrue(descriptions.isRecognised(wearer.id), "still known when seen");
		assertEquals("blue jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("864 (scenario 22): hit and duck behind a wall: past the memory, the same look is still known")
	void hitThenDuckIsStillKnownAfterTheMemory() {
		describedInPumpkinAndJacket();
		swapPumpkinAndJacket();

		tick.exposed(hitCop(true), wearer.player);
		blindAt(500);
		blindAt(4000);
		assertFalse(descriptions.isRecognised(wearer.id), "the memory ran out unseen");

		seenAt(5000);
		assertTrue(descriptions.isRecognised(wearer.id), "the cops want the look that hit their man");
	}

	@Test
	@DisplayName("864 (scenario 23): a shot from cover: the hit cop has no ray, another cop sees the player: recognised")
	void anotherCopSeeingTheHitCounts() {
		describedInPumpkinAndJacket();
		swapPumpkinAndJacket();
		eyes = true;

		tick.exposed(hitCop(false), wearer.player);

		assertTrue(descriptions.isRecognised(wearer.id));
		assertEquals("blue jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("864: a hit in the police outfit, seen: the outfit is blown and a blind tick does not un-blow it")
	void hitInTheOutfitBlowsIt() {
		dressAsCop();

		tick.exposed(hitCop(true), wearer.player);
		blindAt(500);

		assertFalse(outfit.hides(wearer.player, 1));
	}

	@Test
	@DisplayName("864 (scenario 24): a hit in the police outfit at 0 stars: the cop may fire back, no flag is set")
	void hittingACopInTheOutfitAtZeroStarsBlowsIt() {
		level = 0;
		dressAsCop();
		CopNpc cop = hitCop(true);

		tick.exposed(cop, wearer.player);

		assertTrue(filter.allows(cop, wearer.player), "no free attack once the fire gate is on");
		assertFalse(descriptions.isRecognised(wearer.id));
		assertNull(descriptions.latest(wearer.id));
	}

	@Test
	@DisplayName("864: crew-share stars and no description: the hit stores one, so the next unhunted tick clears the flag")
	void hitWithNoDescriptionLeavesNoStaleFlag() {
		tick.exposed(hitCop(true), wearer.player);
		assertNotNull(descriptions.latest(wearer.id));

		level = 0;
		tick.tick(wearer.player, null);

		assertFalse(descriptions.isRecognised(wearer.id));
	}

	/** A cop hit from {@code distance} blocks with no line to the player: a slab, a pane, a shot over a wall. */
	private CopNpc hitCopBlockedAt(double distance) {
		CopNpc cop = mock(CopNpc.class);
		when(cop.distanceTo(wearer.player)).thenReturn(distance);
		return cop;
	}

	@Test
	@DisplayName("0.21.6: a cop hit from within Witness_Floor knows who hit them, ray or no ray: known in what they wear")
	void aHitWithinTheFloorRecognisesWithNoRay() {
		describedInPumpkinAndJacket();
		swapPumpkinAndJacket();

		tick.exposed(hitCopBlockedAt(3.0), wearer.player);

		assertTrue(descriptions.isRecognised(wearer.id));
		assertEquals("blue jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("0.21.6: a hit within Witness_Floor in the police outfit at 0 stars blows it: no passive cop")
	void aHitWithinTheFloorBlowsTheOutfit() {
		level = 0;
		dressAsCop();
		CopNpc cop = hitCopBlockedAt(3.0);

		tick.exposed(cop, wearer.player);

		assertTrue(filter.allows(cop, wearer.player));
	}

	@Test
	@DisplayName("0.21.6: guard - just past Witness_Floor a hit with no ray and no eyes still changes nothing")
	void aHitJustPastTheFloorNeedsARay() {
		describedInPumpkinAndJacket();
		swapPumpkinAndJacket();

		tick.exposed(hitCopBlockedAt(6.5), wearer.player);

		assertFalse(descriptions.isRecognised(wearer.id));
		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("864: a hit no cop sees changes nothing by itself")
	void hittingACopUnseenChangesNothing() {
		describedInPumpkinAndJacket();
		dressAsCop();

		tick.exposed(hitCop(false), wearer.player);

		assertFalse(descriptions.isRecognised(wearer.id));
		assertTrue(outfit.hides(wearer.player, 1));
		assertEquals("red jacket", descriptions.latest(wearer.id).chest());
	}

	@Test
	@DisplayName("864: with disguises off a hit changes nothing")
	void hitWithDisguisesOffChangesNothing() {
		settings = new DisguiseSettings(false, settings.maskItems(), settings.maskWearables(), settings.policeOutfit(),
		                                settings.outfitMaxLevel(), settings.changesNeeded());
		dressAsCop();

		tick.exposed(hitCop(true), wearer.player);

		assertFalse(descriptions.isRecognised(wearer.id));
		assertTrue(outfit.hides(wearer.player, 1));
		assertNull(descriptions.latest(wearer.id));
	}
}
