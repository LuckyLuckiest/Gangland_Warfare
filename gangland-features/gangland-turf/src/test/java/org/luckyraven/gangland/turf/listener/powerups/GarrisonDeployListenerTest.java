package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.defender.DeployerSeam;
import org.luckyraven.gangland.turf.npc.defender.GarrisonRoster;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderConfig;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.npc.defender.TurfGarrison;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.support.InMemoryGarrisonRepository;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GarrisonDeployListenerTest {

	private InMemoryGarrisonRepository repository;
	private GarrisonManager            garrisons;
	private TurfDefenderDeployer       defenders;
	private TurfManager                turfs;
	private GarrisonRoster             roster;
	private GarrisonDeployListener     listener;

	@BeforeEach
	void setUp() {
		repository = new InMemoryGarrisonRepository();
		garrisons  = new GarrisonManager(repository);
		defenders  = mock(TurfDefenderDeployer.class);
		turfs      = mock(TurfManager.class);
		TurfNpcsConfigLoader npcConfig = mock(TurfNpcsConfigLoader.class);
		when(npcConfig.getDefenderConfig()).thenReturn(new TurfDefenderConfig("turf_defender", 16.0, 3, 6, 20, 0, 16));
		ActiveBuffManager buffs = mock(ActiveBuffManager.class);
		when(buffs.effectiveMultiplier(anyInt(), any())).thenReturn(1.0);
		GangLookupContract gangs = mock(GangLookupContract.class);
		roster   = new GarrisonRoster(turfs, garrisons, npcConfig, gangs, mock(TurfMessages.class),
		                              new PowerupRegistry(), buffs);
		listener = new GarrisonDeployListener(garrisons, defenders, mock(TurfPowerupManager.class), gangs, roster);
	}

	private Turf turf(int id, Integer owner) {
		Turf turf = new Turf(id, "Turf", new CuboidRegion("world", 0, 0, 10, 10), owner, BigDecimal.TEN, 0L, 0L);
		when(turfs.get(id)).thenReturn(turf);
		return turf;
	}

	private static Gang challenger() {
		Gang challenger = mock(Gang.class);
		when(challenger.getId()).thenReturn(2);
		return challenger;
	}

	@Test
	@DisplayName("F-GARRISON r1: a contest at stock 0 still registers, so a restarted contest re-arms and a guard bought mid-contest fights")
	void contestStart_atStockZero_stillRegisters() {
		Turf turf = turf(1, 1);

		listener.onCaptureStart(new TurfCaptureStartEvent(turf, challenger()));

		verify(defenders).startGarrison(any(TurfGarrison.class), any());
	}

	@Test
	@DisplayName("a capture of an unclaimed turf arms nothing")
	void contestStart_unclaimed_armsNothing() {
		Turf turf = turf(1, null);

		listener.onCaptureStart(new TurfCaptureStartEvent(turf, challenger()));

		verify(defenders, never()).startGarrison(any(), any());
	}

	@Test
	@DisplayName("a failed contest stands the guards down as held and the count does not move")
	void failedContest_countUnchanged() {
		Turf turf = turf(1, 1);
		garrisons.add(1, 2);

		listener.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		verify(defenders).standDown(1, true);
		assertEquals(2, garrisons.count(1));
	}

	@Test
	@DisplayName("a failed contest on an unclaimed turf gives no guards back (no owner to hold stock)")
	void failedContest_unclaimedTurf_addsNothing() {
		Turf turf = turf(2, null);

		listener.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.ABANDONED, 3));

		verify(defenders).standDown(2, true);
		assertEquals(0, garrisons.count(2));
	}

	@Test
	@DisplayName("contestEnd_killsOnlySpent: stock 5, 3 stand, 1 killed - the count ends at 4, nothing spent at start and nothing returned")
	void contestEnd_killsOnlySpent() {
		Turf turf = turf(1, 1);
		garrisons.add(1, 5);

		listener.onCaptureStart(new TurfCaptureStartEvent(turf, challenger()));
		ArgumentCaptor<TurfGarrison> garrison = ArgumentCaptor.forClass(TurfGarrison.class);
		verify(defenders).startGarrison(garrison.capture(), any());
		assertEquals(5, garrisons.count(1), "contest start spends nothing");
		assertEquals(5, garrison.getValue().owned());

		garrison.getValue().spend(1);
		assertEquals(4, garrisons.count(1));

		listener.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(4, garrisons.count(1));
	}

	@Test
	@DisplayName("a completed capture stands down as lost and leaves the count to the owner change that follows")
	void captured_standsDownAsLost() {
		Turf turf = turf(1, 2);
		garrisons.add(1, 1);

		listener.onCaptured(new org.luckyraven.gangland.turf.events.TurfCapturedEvent(turf, mock(Gang.class), mock(Gang.class)));

		verify(defenders).standDown(1, false);
		assertEquals(1, garrisons.count(1));
	}

	@Test
	@DisplayName("T-883: a turf deleted mid-contest writes no row when the stand-down delay runs out")
	void deleteDuringContest_writesNoRow() {
		TurfDefenderDeployer real = DeployerSeam.deployer();
		GarrisonDeployListener wired = new GarrisonDeployListener(garrisons, real, mock(TurfPowerupManager.class),
		                                                          mock(GangLookupContract.class), roster);
		Turf turf = turf(1, 1);
		garrisons.add(1, 3);
		wired.onCaptureStart(new TurfCaptureStartEvent(turf, challenger()));
		CivilianNpc guard = mock(CivilianNpc.class);
		when(guard.isValid()).thenReturn(true);
		DeployerSeam.track(real, 1, guard);

		// what TurfDeleteCommand does: cancelContest fires the failed event, then the row goes
		wired.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.ABANDONED, 2));
		garrisons.remove(1);
		int saves = repository.saved.size();
		DeployerSeam.tick(real);
		DeployerSeam.tick(real);

		assertEquals(0, garrisons.count(1));
		assertEquals(saves, repository.saved.size(), "no late save");
		assertTrue(repository.loadAll().isEmpty(), "the row stays gone");
	}

	@Test
	@DisplayName("T-270: an owner change recalls the old owner's guards and clears the garrison stock")
	void ownerChange_recallsGuardsAndClearsStock() {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), 1, BigDecimal.TEN, 0L, 0L);
		garrisons.add(1, 3);

		listener.onOwnerChanged(new TurfOwnerChangedEvent(turf, 1, 2, TurfOwnerChangedEvent.Cause.CAPTURE));

		verify(defenders).recall(1);
		assertEquals(0, garrisons.count(1));
	}
}
