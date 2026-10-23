package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract.ChargeResult;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PullOverSettings;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-PAT-3: the pull-over order. Slowing below Stop_Speed for Stop_Hold_Seconds within Comply_Seconds is a wallet-only
 * ticket (never a crime); not stopping commits Evading_Police as seen by a cop; one order per player per 120 s.
 */
@DisplayName("PullOverTracker - stop orders, tickets and fleeing")
class PullOverTrackerTest {

	private PatrolFixture             fx;
	private CrimeService              crimes;
	private DetainmentEconomyContract economy;
	private WantedMessages            messages;
	private PullOverSettings          settings;
	private Map<UUID, Player>         online;
	private PullOverTracker           tracker;
	private Player                    player;

	@BeforeEach
	void setUp() {
		fx       = new PatrolFixture();
		crimes   = mock(CrimeService.class);
		economy  = mock(DetainmentEconomyContract.class);
		messages = PatrolFixture.messages();
		settings = PullOverSettings.DEFAULT;
		online   = new HashMap<>();
		tracker  = new PullOverTracker(mock(JavaPlugin.class), () -> settings, () -> fx.clock[0], crimes, economy,
		                               messages, online::get,
		                               amount -> Map.of("money_symbol", "$", "amount", String.valueOf((long) amount)));
		player   = fx.player(0, 0);
		online.put(player.getUniqueId(), player);
		when(economy.getBalance(player)).thenReturn(1_000.0);
		when(economy.tryCharge(any(), anyDouble())).thenReturn(ChargeResult.SUCCESS);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	/** Advances the clock by {@code ms}, reports {@code speed} and runs one tracker tick. */
	private void step(long ms, double speed) {
		fx.clock[0] += ms;
		tracker.onSpeed(player.getUniqueId(), speed);
		tracker.tick();
	}

	private void verifyNoCrime() {
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("order sends Patrol.Pull_Over with Comply_Seconds and opens the order; a second order is refused")
	void order_opensAndMessages() {
		assertTrue(tracker.canOrder(player.getUniqueId()));

		assertTrue(tracker.order(player, 15.0));

		verify(messages).format(WantedMessages.Key.PATROL_PULL_OVER, Map.of("seconds", "6"));
		verify(player).sendMessage("PATROL_PULL_OVER");
		assertTrue(tracker.isOrdered(player.getUniqueId()));
		assertFalse(tracker.canOrder(player.getUniqueId()));
		assertFalse(tracker.order(player, 15.0));
	}

	@Test
	@DisplayName("speed 0.5 for 2 s within 6 s: the wallet is charged 150, the ticket message shows it, no crime")
	void stoppingIssuesTicket() {
		tracker.order(player, 15.0);

		step(1_000, 0.5);
		step(1_000, 0.5);
		verify(economy, never()).tryCharge(any(), anyDouble());
		step(1_000, 0.5);

		verify(economy).tryCharge(player, 150.0);
		verify(messages).format(WantedMessages.Key.PATROL_TICKET,
		                        Map.of("money_symbol", "$", "amount", "150"));
		verify(player).sendMessage("PATROL_TICKET");
		verifyNoCrime();
		assertFalse(tracker.isOrdered(player.getUniqueId()));
	}

	@Test
	@DisplayName("a driver with 80 in the wallet is charged 80 and shown 80")
	void poorDriverChargedWhatTheyHave() {
		when(economy.getBalance(player)).thenReturn(80.0);
		tracker.order(player, 15.0);

		step(1_000, 0.0);
		step(2_000, 0.0);

		verify(economy).tryCharge(player, 80.0);
		verify(messages).format(WantedMessages.Key.PATROL_TICKET, Map.of("money_symbol", "$", "amount", "80"));
		verifyNoCrime();
	}

	@Test
	@DisplayName("an empty wallet: nothing is charged, the ticket still closes the stop, no crime")
	void emptyWallet_noCharge() {
		when(economy.getBalance(player)).thenReturn(0.0);
		tracker.order(player, 15.0);

		step(1_000, 0.0);
		step(2_000, 0.0);

		verify(economy, never()).tryCharge(any(), anyDouble());
		verifyNoCrime();
		assertFalse(tracker.isOrdered(player.getUniqueId()));
	}

	@Test
	@DisplayName("an economy error is not punished: no crime, no exception, the order closes")
	void economyError_noCrime() {
		when(economy.tryCharge(any(), anyDouble())).thenReturn(ChargeResult.ECONOMY_ERROR);
		tracker.order(player, 15.0);

		step(1_000, 0.0);
		step(2_000, 0.0);

		verifyNoCrime();
		assertFalse(tracker.isOrdered(player.getUniqueId()));
	}

	@Test
	@DisplayName("moving again resets the hold: slow 1 s, fast, slow 2 s later still tickets within the 6 s")
	void movingResetsHold() {
		tracker.order(player, 15.0);

		step(1_000, 0.5);
		step(1_000, 10.0);
		step(1_000, 0.5);
		step(1_000, 0.5);
		verify(economy, never()).tryCharge(any(), anyDouble());
		step(1_000, 0.5);

		verify(economy).tryCharge(player, 150.0);
	}

	@Test
	@DisplayName("no CarMoveEvent for 10 ticks (the car stopped or the driver got out) counts as speed 0")
	void noRecentEvent_countsAsStopped() {
		tracker.order(player, 15.0);
		tracker.onSpeed(player.getUniqueId(), 15.0);

		fx.clock[0] += 1_000;
		tracker.tick();
		fx.clock[0] += 2_000;
		tracker.tick();

		verify(economy).tryCharge(player, 150.0);
	}

	@Test
	@DisplayName("no stop in 6 s: Evading_Police is committed as seen by a cop and Patrol.Fled is sent, no charge")
	void fleeingCommitsEvadingPolice() {
		tracker.order(player, 15.0);

		step(5_999, 20.0);
		verifyNoCrime();
		step(1, 20.0);

		verify(crimes).commit(player, Crimes.EVADING_POLICE, fx.at(0, 0), true, 0);
		verify(messages).format(WantedMessages.Key.PATROL_FLED, Map.of());
		verify(player).sendMessage("PATROL_FLED");
		verify(economy, never()).tryCharge(any(), anyDouble());
		assertFalse(tracker.isOrdered(player.getUniqueId()));
	}

	@Test
	@DisplayName("stopping too late (the 2 s hold is not complete at the 6 s deadline) is fleeing")
	void stopTooLate_isFleeing() {
		tracker.order(player, 15.0);

		step(5_000, 20.0);
		step(1_000, 0.5);

		verify(crimes).commit(eq(player), eq(Crimes.EVADING_POLICE), any(), eq(true), eq(0));
		verify(economy, never()).tryCharge(any(), anyDouble());
	}

	@Test
	@DisplayName("an offline driver drops the order silently: no crime, no charge")
	void offlinePlayer_dropsOrder() {
		tracker.order(player, 15.0);
		online.remove(player.getUniqueId());

		fx.clock[0] += 7_000;
		tracker.tick();

		verifyNoCrime();
		verify(economy, never()).tryCharge(any(), anyDouble());
		assertFalse(tracker.isOrdered(player.getUniqueId()));
	}

	@Test
	@DisplayName("one stop per player per 120 s, counted from the order: 119 s refused, 120 s allowed")
	void repeatCooldown() {
		tracker.order(player, 15.0);
		step(1_000, 0.0);
		step(2_000, 0.0);

		fx.clock[0] += 116_999;
		assertFalse(tracker.canOrder(player.getUniqueId()));
		fx.clock[0] += 1;
		assertTrue(tracker.canOrder(player.getUniqueId()));
	}

	@Test
	@DisplayName("forget clears the order and the cooldown of a player who quit")
	void forget_clearsCooldown() {
		tracker.order(player, 15.0);

		tracker.forget(player.getUniqueId());

		assertFalse(tracker.isOrdered(player.getUniqueId()));
		assertTrue(tracker.canOrder(player.getUniqueId()));
	}

	@Test
	@DisplayName("Cops.Pull_Over.Enable false: nobody can be ordered")
	void disabled_noOrders() {
		settings = new PullOverSettings(false, 24.0, 12.0, 6, 1.0, 2, 150.0, 120);

		assertFalse(tracker.canOrder(player.getUniqueId()));
		assertFalse(tracker.order(player, 15.0));
	}

	@Test
	@DisplayName("a tick with no open order does nothing")
	void tickWithoutOrders_isNoop() {
		tracker.tick();

		verifyNoCrime();
		verify(economy, never()).tryCharge(any(), anyDouble());
	}

	@Test
	@DisplayName("onInitialize schedules one repeating task; onShutdown cancels it and drops every order")
	void lifecycle() {
		BukkitTask task = mock(BukkitTask.class);
		when(fx.bukkit.scheduler().runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(task);

		tracker.onInitialize(true);
		verify(fx.bukkit.scheduler(), times(1)).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(),
		                                                     anyLong());

		tracker.order(player, 15.0);
		tracker.onShutdown();

		verify(task).cancel();
		assertFalse(tracker.isOrdered(player.getUniqueId()));
	}
}
