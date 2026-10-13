package org.luckyraven.gangland.copsncrooks.wanted.report;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.PursuitReportSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.hud.HudFixtures;
import org.luckyraven.gangland.core.wanted.WantedCause;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P-REPORT-4 / P-REPORT-5: the live headline (stars or time), once per chase, global cooldown, closing headline. */
@DisplayName("BreakingNews")
class BreakingNewsTest {

	private static final long SEC = 1000L;

	@TempDir
	Path tempDir;

	private final UUID idA = UUID.randomUUID();
	private final UUID idB = UUID.randomUUID();

	private AtomicLong        clock;
	private ChaseArcs         arcs;
	private ChaseConfigLoader config;
	private List<String>      sent;
	private BreakingNews      news;
	private Player            steve;
	private Player            alex;

	@BeforeAll
	static void prime() throws ReflectiveOperationException {
		HudFixtures.primeMoneySymbol();
	}

	@BeforeEach
	void setUp() throws IOException {
		clock  = new AtomicLong(1_000_000L);
		arcs   = new ChaseArcs(clock::get);
		sent   = new ArrayList<>();
		config = mock(ChaseConfigLoader.class);
		settings(PursuitReportSettings.DEFAULT);
		news = new BreakingNews(arcs, config, HudFixtures.messages(tempDir), sent::add);

		steve = mock(Player.class);
		when(steve.getUniqueId()).thenReturn(idA);
		when(steve.getName()).thenReturn("Steve");
		alex = mock(Player.class);
		when(alex.getUniqueId()).thenReturn(idB);
		when(alex.getName()).thenReturn("Alex");
	}

	private void settings(PursuitReportSettings s) {
		when(config.getPursuitReport()).thenReturn(s);
	}

	private PursuitReportSettings news(boolean enabled, int stars, int seconds, int cooldown) {
		return new PursuitReportSettings(true, 1, 2500.0, 10.0, enabled, stars, seconds, cooldown);
	}

	private String plain(String line) {
		return line.replaceAll("§.", "");
	}

	@Test
	@DisplayName("level 4 reported twice in one chase -> one broadcast, naming the player")
	void liveOncePerChase_atFourStars() {
		arcs.start(idA, WantedCause.CRIME, 4);

		assertTrue(news.check(steve, 4, "STARS"));
		assertFalse(news.check(steve, 4, "STARS"));

		assertEquals(1, sent.size());
		assertTrue(plain(sent.get(0)).contains("BREAKING") && plain(sent.get(0)).contains("Steve"), sent.get(0));
		assertTrue(arcs.arc(idA).newsSent());
	}

	@Test
	@DisplayName("below Min_Stars and under Min_Seconds -> nothing")
	void belowBothThresholds_noNews() {
		arcs.start(idA, WantedCause.CRIME, 3);
		clock.addAndGet(119 * SEC);

		assertFalse(news.check(steve, 3, "STARS"));
		assertTrue(sent.isEmpty());
		assertFalse(arcs.arc(idA).newsSent());
	}

	@Test
	@DisplayName("a chase that lasts Min_Seconds makes the news from the AI tick, even at one star")
	void timeTrigger_fromAiTick() {
		arcs.start(idA, WantedCause.CRIME, 1);
		CopGroup group = mock(CopGroup.class);

		clock.addAndGet(119 * SEC);
		news.onAiTick(steve, group);
		assertTrue(sent.isEmpty());

		clock.addAndGet(1 * SEC);
		news.onAiTick(steve, group);
		news.onAiTick(steve, group);
		assertEquals(1, sent.size());
	}

	@Test
	@DisplayName("the AI tick of a player with no arc does nothing")
	void aiTickWithoutArc_noNews() {
		news.onAiTick(steve, null);

		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("a check with no arc does nothing")
	void noArc_noNews() {
		assertFalse(news.check(steve, 5, "STARS"));
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("global cooldown: chase A at 120 s makes news; chase B at 4 stars 60 s later does not; after 300 s it does")
	void timeTrigger_andGlobalCooldown() {
		arcs.start(idA, WantedCause.CRIME, 1);
		clock.addAndGet(120 * SEC);
		assertTrue(news.check(steve, 1, "TIME"));

		clock.addAndGet(60 * SEC);
		arcs.start(idB, WantedCause.CRIME, 4);
		assertFalse(news.check(alex, 4, "STARS"));
		assertEquals(1, sent.size());
		assertFalse(arcs.arc(idB).newsSent());

		clock.addAndGet(240 * SEC);
		assertTrue(news.check(alex, 4, "STARS"));
		assertEquals(2, sent.size());
	}

	@Test
	@DisplayName("the very first news is never held back by the cooldown, even on a clock that starts at 0")
	void firstNewsIgnoresCooldown() {
		clock.set(0L);
		arcs.start(idA, WantedCause.CRIME, 4);

		assertTrue(news.check(steve, 4, "STARS"));
	}

	@Test
	@DisplayName("Breaking_News.Enable false -> no live news and no closing headline")
	void disabled_noNews() {
		settings(news(false, 4, 120, 300));
		arcs.start(idA, WantedCause.CRIME, 5);

		assertFalse(news.check(steve, 5, "STARS"));
		assertFalse(news.closing(steve, arcs.arc(idA), "Busted"));
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("a reload that lowers the thresholds applies to the next check")
	void reloadedSettingsApply() {
		arcs.start(idA, WantedCause.CRIME, 2);
		assertFalse(news.check(steve, 2, "STARS"));

		settings(news(true, 2, 120, 300));

		assertTrue(news.check(steve, 2, "STARS"));
	}

	@Test
	@DisplayName("the closing headline goes out only for a chase that made the news, with its ending, and ignores the cooldown")
	void closingHeadlineOnlyForNewsChases() {
		arcs.start(idA, WantedCause.CRIME, 4);
		arcs.start(idB, WantedCause.CRIME, 1);
		assertTrue(news.check(steve, 4, "STARS"));
		sent.clear();

		assertFalse(news.closing(alex, arcs.arc(idB), "Escaped"));
		assertTrue(sent.isEmpty());

		assertTrue(news.closing(steve, arcs.arc(idA), "Busted"));
		assertEquals(1, sent.size());
		assertTrue(plain(sent.get(0)).contains("Steve") && plain(sent.get(0)).contains("Busted"), sent.get(0));
	}

	@Test
	@DisplayName("a chase that made the news restarts nothing: a RESTORE-continued arc keeps newsSent, so no second live headline")
	void restoredChaseKeepsNewsSent() {
		arcs.start(idA, WantedCause.CRIME, 4);
		assertTrue(news.check(steve, 4, "STARS"));
		arcs.quit(idA);
		clock.addAndGet(400 * SEC);
		arcs.restore(idA);

		assertFalse(news.check(steve, 4, "STARS"));
		assertEquals(1, sent.size());
	}
}
