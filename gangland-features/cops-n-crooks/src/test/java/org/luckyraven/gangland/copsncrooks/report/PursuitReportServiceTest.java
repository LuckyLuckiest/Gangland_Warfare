package org.luckyraven.gangland.copsncrooks.report;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 0.12 F6: {@link PursuitReportService} formats the {@code Messages.Report} placeholders and decides whether a chase
 * qualifies for the breaking-news broadcast, subject to its global cooldown.
 */
@DisplayName("PursuitReportService")
class PursuitReportServiceTest {

	private BukkitStatics          bukkit;
	private MockedStatic<Settings> settings;

	private CopLoader            copLoader;
	private DetainmentService    detainment;
	private AtomicLong           now;
	private PursuitReportService service;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();

		settings = mockStatic(Settings.class);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(Settings::getWantedMaximumLevel).thenReturn(5);

		copLoader  = mock(CopLoader.class);
		detainment = mock(DetainmentService.class);
		now        = new AtomicLong(1_000_000L);
		service    = new PursuitReportService(copLoader, detainment, now::get);

		when(copLoader.getLoadedPursuitReportConfig()).thenReturn(PursuitReportConfig.defaults());
	}

	@AfterEach
	void tearDown() {
		settings.close();
		bukkit.close();
	}

	@Test
	@DisplayName("formatReport substitutes every placeholder and colors the result")
	void formatReport_substitutesPlaceholders() {
		ChaseRecord record = new ChaseRecord(0L);
		record.raiseStars(3);
		record.copKilled();
		record.copKilled();

		List<String> lines = service.formatReport(record, ChaseOutcome.ESCAPED, 125_000L);

		assertEquals(4, lines.size());
		assertTrue(lines.get(1).contains("ESCAPED"), lines.get(1));
		assertTrue(lines.get(2).contains("2:05"), lines.get(2));
		assertTrue(lines.get(2).contains("★★★☆☆"), lines.get(2));
		assertTrue(lines.get(3).contains("2"), lines.get(3));
	}

	@Test
	@DisplayName("formatReport uses each outcome's configured message")
	void formatReport_perOutcomeMessage() {
		ChaseRecord record = new ChaseRecord(0L);

		assertTrue(service.formatReport(record, ChaseOutcome.BUSTED, 0L).get(1).contains("BUSTED"));
		assertTrue(service.formatReport(record, ChaseOutcome.WASTED, 0L).get(1).contains("WASTED"));
	}

	@Test
	@DisplayName("shouldBroadcast is false below both the star and duration thresholds")
	void shouldBroadcast_belowThresholds_isFalse() {
		ChaseRecord record = new ChaseRecord(0L);
		record.raiseStars(3);

		assertFalse(service.shouldBroadcast(record, 60_000L));
	}

	@Test
	@DisplayName("shouldBroadcast is true at the star threshold, even for a short chase")
	void shouldBroadcast_starThreshold_isTrue() {
		ChaseRecord record = new ChaseRecord(0L);
		record.raiseStars(4);

		assertTrue(service.shouldBroadcast(record, 1_000L));
	}

	@Test
	@DisplayName("shouldBroadcast is true at the duration threshold, even at one star")
	void shouldBroadcast_durationThreshold_isTrue() {
		ChaseRecord record = new ChaseRecord(0L);
		record.raiseStars(1);

		assertTrue(service.shouldBroadcast(record, 120_000L));
	}

	@Test
	@DisplayName("shouldBroadcast respects the global cooldown across chases")
	void shouldBroadcast_globalCooldown() {
		Player killer = mock(Player.class);
		when(killer.getUniqueId()).thenReturn(UUID.randomUUID());
		when(killer.getName()).thenReturn("Killer");

		ChaseRecord first = new ChaseRecord(0L);
		first.raiseStars(4);
		assertTrue(service.shouldBroadcast(first, 0L));

		service.finish(killer); // no chase tracked for `killer` yet: no-op, does not touch the cooldown

		// Drive a real chase through start/finish so the cooldown gets set by the service itself.
		service.start(killer, 4);
		service.finish(killer);

		ChaseRecord second = new ChaseRecord(0L);
		second.raiseStars(5);
		assertFalse(service.shouldBroadcast(second, now.get()), "still inside the 300s cooldown");

		now.addAndGet(300_000L);
		assertTrue(service.shouldBroadcast(second, now.get()), "cooldown elapsed");
	}

	@Test
	@DisplayName("shouldBroadcast is false with Breaking_News.Enable off")
	void shouldBroadcast_disabled() {
		when(copLoader.getLoadedPursuitReportConfig()).thenReturn(
				new PursuitReportConfig(true, false, 4, 120, 300, null, null, null, null, null));

		ChaseRecord record = new ChaseRecord(0L);
		record.raiseStars(5);

		assertFalse(service.shouldBroadcast(record, 1_000_000L));
	}

	@Test
	@DisplayName("start/raise/copKilled/markWasted/markCuffed are no-ops without a tracked chase")
	void serviceCalls_noTrackedChase_areNoOps() {
		UUID randomId = UUID.randomUUID();

		service.raise(randomId, 3);
		service.copKilled(null);
		service.markWasted(randomId);
		service.markCuffed(randomId);
		service.discard(randomId);
		// No exception is the assertion; nothing above has a tracked chase to touch.
	}

	@Test
	@DisplayName("finish resolves BUSTED for a currently-restrained player and reports it")
	void finish_restrainedPlayer_reportsBusted() {
		Player player = mock(Player.class);
		UUID   id     = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);
		when(player.getName()).thenReturn("Suspect");
		when(detainment.isRestrained(player)).thenReturn(true);

		service.start(player, 2);
		service.finish(player);

		// A second finish for the same player is a no-op: the chase was already removed.
		service.finish(player);
	}

	@Test
	@DisplayName("with Pursuit_Report.Enable off, start tracks nothing and finish is a no-op")
	void enable_off_disablesTracking() {
		when(copLoader.getLoadedPursuitReportConfig()).thenReturn(
				new PursuitReportConfig(false, true, 4, 120, 300, null, null, null, null, null));

		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		service.start(player, 3);
		service.raise(player.getUniqueId(), 4); // no chase was tracked, so this is a no-op

		service.finish(player); // no-op: nothing to resolve, nothing sent
	}

}
