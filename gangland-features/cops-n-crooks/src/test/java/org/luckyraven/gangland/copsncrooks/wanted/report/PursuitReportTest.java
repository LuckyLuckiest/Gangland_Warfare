package org.luckyraven.gangland.copsncrooks.wanted.report;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages.Key;
import org.luckyraven.gangland.copsncrooks.wanted.config.PursuitReportSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.hud.HudFixtures;
import org.luckyraven.gangland.core.wanted.WantedCause;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P-REPORT-1 / P-REPORT-2: the ordered report lines and the ending table. */
@DisplayName("PursuitReport")
class PursuitReportTest {

	private static final long SEC = 1000L;

	@TempDir
	Path tempDir;

	private final UUID id = UUID.randomUUID();

	private AtomicLong      clock;
	private ChaseArcs       arcs;
	private WantedMessages  messages;
	private PursuitReportSettings settings = PursuitReportSettings.DEFAULT;

	@BeforeAll
	static void prime() throws ReflectiveOperationException {
		HudFixtures.primeMoneySymbol();
	}

	@BeforeEach
	void setUp() throws IOException {
		clock    = new AtomicLong(1_000_000L);
		arcs     = new ChaseArcs(clock::get);
		messages = HudFixtures.messages(tempDir);
	}

	private ChaseArc startChase(int level) {
		arcs.start(id, WantedCause.CRIME, level);
		return arcs.arc(id);
	}

	private String plain(String line) {
		return line.replaceAll("§.", "");
	}

	@Test
	@DisplayName("ending maps every cause/origin pair: DECAY+EVASION escaped, ARREST by origin, DEATH wasted, BRIBE+CONTACT paid off, the rest cleared")
	void endingNames() {
		assertEquals(Key.ENDING_ESCAPED, PursuitReport.ending(WantedCause.DECAY, null));
		assertEquals(Key.ENDING_ESCAPED, PursuitReport.ending(WantedCause.EVASION, null));
		assertEquals(Key.ENDING_SURRENDERED, PursuitReport.ending(WantedCause.ARREST, ArrestOrigin.SURRENDERED));
		assertEquals(Key.ENDING_BUSTED, PursuitReport.ending(WantedCause.ARREST, ArrestOrigin.BUSTED_DOWNED));
		assertEquals(Key.ENDING_ARRESTED, PursuitReport.ending(WantedCause.ARREST, ArrestOrigin.CUFFED));
		assertEquals(Key.ENDING_ARRESTED, PursuitReport.ending(WantedCause.ARREST, null));
		assertEquals(Key.ENDING_ARRESTED, PursuitReport.ending(WantedCause.ARREST, ArrestOrigin.DEAD_OR_ALIVE));
		assertEquals(Key.ENDING_WASTED, PursuitReport.ending(WantedCause.DEATH, null));
		assertEquals(Key.ENDING_PAID_OFF, PursuitReport.ending(WantedCause.BRIBE, null));
		assertEquals(Key.ENDING_PAID_OFF, PursuitReport.ending(WantedCause.CONTACT, null));
		assertEquals(Key.ENDING_CLEARED, PursuitReport.ending(WantedCause.SIGN, null));
		assertEquals(Key.ENDING_CLEARED, PursuitReport.ending(WantedCause.ADMIN, null));
		assertEquals(Key.ENDING_CLEARED, PursuitReport.ending(WantedCause.UNKNOWN, null));
	}

	@Test
	@DisplayName("an origin only matters for ARREST: DEATH with a Busted origin is still Wasted")
	void originIgnoredOutsideArrest() {
		assertEquals(Key.ENDING_WASTED, PursuitReport.ending(WantedCause.DEATH, ArrestOrigin.BUSTED_DOWNED));
		assertEquals(Key.ENDING_ESCAPED, PursuitReport.ending(WantedCause.EVASION, ArrestOrigin.SURRENDERED));
	}

	@Test
	@DisplayName("duration excludes the offline time: started 0, away 30 s, now 100 s -> 1m 10s")
	void durationExcludesOffline() {
		ChaseArc arc = startChase(2);
		clock.addAndGet(20 * SEC);
		arcs.quit(id);
		clock.addAndGet(30 * SEC);
		arcs.restore(id);
		clock.addAndGet(50 * SEC);

		List<String> lines = PursuitReport.lines(arc, WantedCause.EVASION, null, 0, settings, messages, arcs.now());

		assertTrue(plain(lines.get(2)).contains("1m 10s"), lines.get(2));
	}

	@Test
	@DisplayName("cost is Per_Cop_Down x cops down + Per_Heat x heat: 2 cops, heat 150 -> 6500")
	void costFormula() {
		ChaseArc arc = startChase(3);
		arcs.copDown(id);
		arcs.copDown(id);

		List<String> lines = PursuitReport.lines(arc, WantedCause.EVASION, null, 150, settings, messages, arcs.now());

		assertTrue(plain(lines.get(6)).replaceAll("[,. ]", "").contains("6500"), lines.get(6));
	}

	@Test
	@DisplayName("cost follows the configured prices")
	void costUsesSettings() {
		ChaseArc arc = startChase(3);
		arcs.copDown(id);
		PursuitReportSettings custom = new PursuitReportSettings(true, 1, 100.0, 1.0, true, 4, 120, 300);

		List<String> lines = PursuitReport.lines(arc, WantedCause.EVASION, null, 50, custom, messages, arcs.now());

		assertTrue(plain(lines.get(6)).replaceAll("[,. ]", "").contains("150"), lines.get(6));
	}

	@Test
	@DisplayName("a chase with no kills and no heat costs nothing")
	void zeroCost() {
		ChaseArc arc = startChase(1);

		List<String> lines = PursuitReport.lines(arc, WantedCause.EVASION, null, 0, settings, messages, arcs.now());

		assertTrue(plain(lines.get(6)).matches(".*[^0-9]0([.,]0+)?$"), lines.get(6));
	}

	@Test
	@DisplayName("seven lines in order: Header first, Ending, Duration, Peak, Cops down, Cuffed, Cost last")
	void lineOrder() {
		ChaseArc arc = startChase(4);
		arcs.copDown(id);
		arcs.bust(id);
		arcs.bust(id);

		List<String> lines = PursuitReport.lines(arc, WantedCause.ARREST, ArrestOrigin.BUSTED_DOWNED, 20, settings,
		                                         messages, arcs.now());

		assertEquals(7, lines.size());
		assertTrue(plain(lines.get(0)).contains("PURSUIT REPORT"), lines.get(0));
		assertTrue(plain(lines.get(1)).contains("Busted"), lines.get(1));
		assertTrue(plain(lines.get(2)).startsWith("Duration"), lines.get(2));
		assertTrue(plain(lines.get(3)).startsWith("Peak"), lines.get(3));
		assertTrue(plain(lines.get(4)).contains("Cops down") && plain(lines.get(4)).endsWith("1"), lines.get(4));
		assertTrue(plain(lines.get(5)).contains("cuffed") && plain(lines.get(5)).endsWith("2"), lines.get(5));
		assertTrue(plain(lines.get(6)).startsWith("Cost"), lines.get(6));
	}

	@Test
	@DisplayName("the peak line shows the peak as that many filled stars")
	void peakLineShowsStars() {
		ChaseArc arc = startChase(2);
		arcs.peak(id, 4);

		List<String> lines = PursuitReport.lines(arc, WantedCause.EVASION, null, 0, settings, messages, arcs.now());

		assertTrue(lines.get(3).contains("★★★★"), lines.get(3));
	}
}
