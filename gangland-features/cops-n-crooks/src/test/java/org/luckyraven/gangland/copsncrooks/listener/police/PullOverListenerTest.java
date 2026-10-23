package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PullOverSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PullOverTracker;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.events.vehicle.CarMoveEvent;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-PAT-3: {@link PullOverListener} turns a {@code CarMoveEvent} over the speed limit into a pull-over order when a beat
 * cop within range sees a non-wanted driver, and feeds every move's speed to the tracker.
 */
@DisplayName("PullOverListener")
class PullOverListenerTest {

	private PullOverTracker   tracker;
	private PatrolManager     patrol;
	private TargetingManager  targeting;
	private CopConfigProvider provider;
	private PullOverListener  listener;
	private Player            driver;
	private UUID              id;

	@BeforeEach
	void setUp() {
		tracker   = mock(PullOverTracker.class);
		patrol    = mock(PatrolManager.class);
		targeting = mock(TargetingManager.class);
		provider  = mock(CopConfigProvider.class);
		when(provider.getPullOverSettings()).thenReturn(PullOverSettings.DEFAULT);
		CopLoader loader = mock(CopLoader.class);
		when(loader.getLoadedProvider()).thenReturn(provider);
		listener = new PullOverListener(tracker, patrol, targeting, loader);

		driver = mock(Player.class);
		id     = UUID.randomUUID();
		when(driver.getUniqueId()).thenReturn(id);
		when(tracker.canOrder(id)).thenReturn(true);
		when(patrol.beatCopSees(driver, 24.0)).thenReturn(true);
		when(targeting.isWanted(id)).thenReturn(false);
	}

	private CarMoveEvent move(double blocksPerSecond) {
		Location here = new Location(null, 0, 64, 0);
		return new CarMoveEvent(driver, mock(Entity.class), here, here, blocksPerSecond);
	}

	@Test
	@DisplayName("speed 15 with a beat cop in range that sees the driver: ordered to pull over")
	void speederSeenGetsOrder() {
		listener.onCarMove(move(15.0));

		verify(tracker).order(driver, 15.0);
	}

	@Test
	@DisplayName("speed 10 (and exactly the limit 12): no order")
	void underOrAtLimit_noOrder() {
		listener.onCarMove(move(10.0));
		listener.onCarMove(move(12.0));

		verify(tracker, never()).order(any(), anyDouble());
	}

	@Test
	@DisplayName("a wanted driver is never pulled over")
	void wantedDriver_noOrder() {
		when(targeting.isWanted(id)).thenReturn(true);

		listener.onCarMove(move(20.0));

		verify(tracker, never()).order(any(), anyDouble());
	}

	@Test
	@DisplayName("no beat cop sees the driver: no order")
	void noCopSees_noOrder() {
		when(patrol.beatCopSees(driver, 24.0)).thenReturn(false);

		listener.onCarMove(move(20.0));

		verify(tracker, never()).order(any(), anyDouble());
	}

	@Test
	@DisplayName("a driver who was stopped recently (cooldown or open order): no new order")
	void cooldown_noOrder() {
		when(tracker.canOrder(id)).thenReturn(false);

		listener.onCarMove(move(20.0));

		verify(tracker, never()).order(any(), anyDouble());
	}

	@Test
	@DisplayName("Range comes from Cops.Pull_Over.Range")
	void rangeFromSettings() {
		when(provider.getPullOverSettings()).thenReturn(new PullOverSettings(true, 40.0, 12.0, 6, 1.0, 2, 150.0, 120));
		when(patrol.beatCopSees(driver, 40.0)).thenReturn(true);
		when(patrol.beatCopSees(driver, 24.0)).thenReturn(false);

		listener.onCarMove(move(20.0));

		verify(tracker).order(driver, 20.0);
	}

	@Test
	@DisplayName("Cops.Pull_Over.Enable false: no order, but the speed still reaches the tracker")
	void disabled_noOrder_butFeedsSpeed() {
		when(provider.getPullOverSettings()).thenReturn(new PullOverSettings(false, 24.0, 12.0, 6, 1.0, 2, 150.0, 120));

		listener.onCarMove(move(20.0));

		verify(tracker, never()).order(any(), anyDouble());
		verify(tracker).onSpeed(id, 20.0);
	}

	@Test
	@DisplayName("every move feeds the driver's speed to the tracker, slow ones included")
	void everyMoveFeedsSpeed() {
		listener.onCarMove(move(0.5));
		listener.onCarMove(move(15.0));

		verify(tracker).onSpeed(id, 0.5);
		verify(tracker).onSpeed(id, 15.0);
	}

	@Test
	@DisplayName("a player quitting forgets the order and the cooldown")
	void quit_forgets() {
		Player quitter = mock(Player.class);
		when(quitter.getUniqueId()).thenReturn(id);

		listener.onQuit(new PlayerQuitEvent(quitter, "bye"));

		verify(tracker).forget(id);
	}
}
