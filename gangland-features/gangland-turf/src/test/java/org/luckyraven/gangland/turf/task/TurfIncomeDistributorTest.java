package org.luckyraven.gangland.turf.task;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.contract.TurfRepositoryContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.log.TurfEventBuffer;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.TurfTextFixture;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pins the income task: TF-10 (orphan branch fires ORPHANED and deposits nothing), TF-07 (shutdown stops the timer)
 * and TF-37 (only an active gang is paid, the buff multiplier is capped, only the oldest in-cap turfs pay, gang XP is
 * granted per paying turf, and one action-bar line goes to each online member).
 */
@DisplayName("TurfIncomeDistributor - activity gate, multiplier cap, in-cap payout, XP, payout line (TF-37, TF-10, TF-07)")
class TurfIncomeDistributorTest {

	private static final long INTERVAL   = 1200L;
	private static final long HOUR_MS    = 3_600_000L;
	private static final long NOW        = 1_000L * HOUR_MS;
	private static final String RULES    = """
			Income:
			   Idle_Hours: 24
			   Max_Multiplier: 2.0
			   Gang_XP_Per_Payout: 1.0
			""";

	@TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER)
	Path tempDir;

	private JavaPlugin        plugin;
	private TurfManager       turfs;
	private FakeGangLookup    gangs;
	private FakeUserLookup    users;
	private ActiveBuffManager buffs;
	private TurfEventBuffer   buffer;
	private TurfRules         rules;
	private TurfMessages      text;
	private org.mockito.MockedStatic<Settings> settings;
	private final List<String> sent = new java.util.ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		plugin = mock(JavaPlugin.class);
		turfs  = new TurfManager(mock(TurfRepositoryContract.class));
		gangs  = new FakeGangLookup();
		users  = new FakeUserLookup();
		buffs  = mock(ActiveBuffManager.class);
		when(buffs.effectiveMultiplier(anyInt(), any(EffectType.class))).thenReturn(1.0);
		buffer = new TurfEventBuffer();
		rules  = TurfTextFixture.rules(tempDir, RULES);
		text   = TurfTextFixture.messages(tempDir, mock(org.luckyraven.gangland.turf.contract.TurfMessageContract.class));
		settings = TurfTextFixture.stubMoneySymbol();
		settings.when(() -> Settings.formatAmount(any(BigDecimal.class)))
				.thenAnswer(invocation -> ((BigDecimal) invocation.getArgument(0)).toPlainString());
	}

	@AfterEach
	void tearDown() {
		settings.close();
	}

	private TurfIncomeDistributor distributor() {
		return new TurfIncomeDistributor(plugin, turfs, gangs, users, buffs, new TurfOwnership(turfs), INTERVAL,
		                                 rules, buffer, text, (player, line, priority) -> sent.add(line),
		                                 () -> List.of(), () -> NOW);
	}

	private static Turf turf(int id, int ownerGangId, long capturedAt) {
		return turf(id, ownerGangId, capturedAt, "250");
	}

	private static Turf turf(int id, int ownerGangId, long capturedAt, String income) {
		return new Turf(id, "Turf" + id, new CuboidRegion("world", 0, 0, 10, 10), ownerGangId,
		                new BigDecimal(income), 0L, capturedAt);
	}

	/** A gang whose level value is {@code levelValue}, with its bank as a mock economy. */
	private Gang gang(int id, long lastMemberOnlineAt, int levelValue, EconomyHandler economy) {
		Gang gang = CaptureFixtures.gang(id);
		Level level = new Level(20, 200);
		level.setLevelValue(levelValue);
		when(gang.getLevel()).thenReturn(level);
		when(gang.getEconomy()).thenReturn(economy);
		when(gang.getLastMemberOnlineAt()).thenReturn(lastMemberOnlineAt);
		when(gang.getName()).thenReturn("Gang" + id);
		gangs.register(gang);
		return gang;
	}

	/** A real bank (the gang economy) starting at zero; a mock cannot be made of this Keystone class. */
	private static EconomyHandler economy() {
		return new EconomyHandler(null);
	}

	private static void assertBank(String expected, EconomyHandler economy) {
		assertEquals(0, new BigDecimal(expected).compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("a turf whose owner gang is missing is released with cause ORPHANED when other gangs are loaded")
	void missingGang_withOtherGangsLoaded_autoReleases() {
		Turf turf = new Turf(1, "T", new CuboidRegion("world", 0, 0, 10, 10), 99, BigDecimal.TEN, 0L, 0L);
		turfs.create(turf);
		gangs.register(CaptureFixtures.gang(1));
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			TurfIncomeDistributor distributor = distributor();
			distributor.start();

			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			verify(bukkit.scheduler()).runTaskTimer(any(Plugin.class), task.capture(), anyLong(), anyLong());
			task.getValue().run();

			assertNull(turf.getOwnerGangId());
			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), times(1)).callEvent(captor.capture());
			assertEquals(TurfOwnerChangedEvent.Cause.ORPHANED,
			             ((TurfOwnerChangedEvent) captor.getValue()).getCause());
		}
	}

	@Test
	@DisplayName("onShutdown cancels the scheduled income task started by start()")
	void onShutdown_cancelsScheduledTask() {
		BukkitTask scheduled = mock(BukkitTask.class);
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			when(bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
					.thenReturn(scheduled);
			TurfIncomeDistributor distributor = distributor();
			distributor.start();

			distributor.onShutdown();

			verify(scheduled).cancel();
		}
	}

	@Test
	@DisplayName("dormantGang_isPaidNothing: owner last online 25h ago with a 24h idle window gets no deposit and no XP")
	void dormantGang_isPaidNothing() {
		EconomyHandler economy = economy();
		gang(1, NOW - 25 * HOUR_MS, 0, economy);
		turfs.create(turf(1, 1, 0L));

		distributor().distribute();

		assertBank("0", economy);
		assertTrue(gangs.experienceCalls().isEmpty());
	}

	@Test
	@DisplayName("neverOnlineGang_isPaidNothing: a gang with lastMemberOnlineAt 0 gets no deposit")
	void neverOnlineGang_isPaidNothing() {
		EconomyHandler economy = economy();
		gang(1, 0L, 0, economy);
		turfs.create(turf(1, 1, 0L));

		distributor().distribute();

		assertBank("0", economy);
	}

	@Test
	@DisplayName("activeGang_isPaid_andEarnsXp: an active gang is paid the turf income and earns one XP per paying turf")
	void activeGang_isPaid_andEarnsXp() {
		EconomyHandler economy = economy();
		gang(1, NOW - HOUR_MS, 0, economy);
		turfs.create(turf(1, 1, 0L));

		distributor().distribute();

		assertBank("250", economy);
		assertEquals(List.of(new FakeGangLookup.XpCall(1, 1.0)), gangs.experienceCalls());
	}

	@Test
	@DisplayName("multiplierIsCapped: buffs of 3.0625 are capped at Income.Max_Multiplier 2.0 when paying")
	void multiplierIsCapped() {
		EconomyHandler economy = economy();
		gang(1, NOW - HOUR_MS, 0, economy);
		turfs.create(turf(1, 1, 0L));
		when(buffs.effectiveMultiplier(anyInt(), any(EffectType.class))).thenReturn(3.0625);

		distributor().distribute();

		assertBank("500", economy);
	}

	@Test
	@DisplayName("overCap_paysOldestTurfsOnly: at level 0 (cap 2) the turfs captured at 100 and 200 pay 250 each; the 1000 turf captured at 300 does not")
	void overCap_paysOldestTurfsOnly() {
		EconomyHandler economy = economy();
		gang(1, NOW - HOUR_MS, 0, economy);
		turfs.create(turf(1, 1, 300L, "1000"));
		turfs.create(turf(2, 1, 100L));
		turfs.create(turf(3, 1, 200L));

		distributor().distribute();

		assertBank("500", economy);
		assertEquals(2, gangs.experienceCalls().size());
	}

	@Test
	@DisplayName("zeroIncomeTurf_earnsNoXp: a turf that pays nothing grants no gang XP and no action-bar line")
	void zeroIncomeTurf_earnsNoXp() {
		EconomyHandler economy = economy();
		gang(1, NOW - HOUR_MS, 0, economy);
		turfs.create(turf(1, 1, 0L, "0"));

		distributor().distribute();

		assertBank("0", economy);
		assertTrue(gangs.experienceCalls().isEmpty());
		assertTrue(sent.isEmpty());
	}

	@Test
	@DisplayName("oneActionBarLinePerOnlineMember: each online member gets one line naming 500 and 2 turfs; a member of another gang gets none")
	void oneActionBarLinePerOnlineMember() {
		EconomyHandler economy = economy();
		gang(1, NOW - HOUR_MS, 3, economy);
		turfs.create(turf(1, 1, 100L));
		turfs.create(turf(2, 1, 200L));
		Player online1 = mock(Player.class);
		Player online2 = mock(Player.class);
		Player otherGang = mock(Player.class);
		users.register(online1, memberOf(1));
		users.register(online2, memberOf(1));
		users.register(otherGang, memberOf(2));
		TurfIncomeDistributor distributor = new TurfIncomeDistributor(plugin, turfs, gangs, users, buffs,
		                                                              new TurfOwnership(turfs), INTERVAL, rules,
		                                                              buffer, text,
		                                                              (player, line, priority) -> {
			                                                              if (priority == 9) {
				                                                              sent.add(player.getName() + "|" + line);
			                                                              }
		                                                              },
		                                                              () -> List.of(online1, online2, otherGang),
		                                                              () -> NOW);
		when(online1.getName()).thenReturn("one");
		when(online2.getName()).thenReturn("two");
		when(otherGang.getName()).thenReturn("rival");

		distributor.distribute();

		assertEquals(2, sent.size());
		assertTrue(sent.stream().allMatch(line -> line.contains("+$500.00 ") && line.contains("(2 turfs)")));
		assertTrue(sent.stream().noneMatch(line -> line.startsWith("rival|")));
	}

	@Test
	@DisplayName("paidIncome_isRecordedInTheBuffer: after a payout the buffer holds the 500 income for the gang")
	void paidIncome_isRecordedInTheBuffer() {
		EconomyHandler economy = economy();
		gang(1, NOW - HOUR_MS, 0, economy);
		turfs.create(turf(1, 1, 0L));
		turfs.create(turf(2, 1, 0L));

		distributor().distribute();

		assertEquals(0, new BigDecimal("500").compareTo(buffer.incomeSince(1, 0L)));
	}

	private static User<Player> memberOf(int gangId) {
		@SuppressWarnings("unchecked")
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(gangId);
		return user;
	}
}
