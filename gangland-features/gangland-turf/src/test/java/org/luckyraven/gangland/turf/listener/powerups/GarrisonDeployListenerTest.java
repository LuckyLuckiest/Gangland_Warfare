package org.luckyraven.gangland.turf.listener.powerups;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.events.TurfCaptureStartEvent;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderConfig;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.support.InMemoryGarrisonRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GarrisonDeployListenerTest {

	private GarrisonManager        garrisons;
	private TurfDefenderDeployer   defenders;
	private TurfNpcsConfigLoader   npcConfig;
	private TurfRules              rules;
	private GarrisonDeployListener listener;

	@BeforeEach
	void setUp() {
		garrisons = new GarrisonManager(new InMemoryGarrisonRepository());
		defenders = mock(TurfDefenderDeployer.class);
		npcConfig = mock(TurfNpcsConfigLoader.class);
		rules     = mock(TurfRules.class);
		when(rules.getMaxReserve()).thenReturn(12);
		when(rules.capture()).thenReturn(CaptureRules.DEFAULTS);
		listener = new GarrisonDeployListener(garrisons, defenders, npcConfig,
		                                      mock(TurfPowerupManager.class), mock(GangLookupContract.class), rules);
	}

	@Test
	@DisplayName("a failed contest returns the surviving guards to the owner's garrison stock")
	void failedContest_givesSurvivingGuardsBackToStock() {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), 1, BigDecimal.TEN, 0L, 0L);
		garrisons.add(1, 0); // stock already consumed at contest start
		when(defenders.aliveCount(1)).thenReturn(2);

		listener.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(2, garrisons.count(1));
		verify(defenders).recall(1);
	}

	@Test
	@DisplayName("a failed contest on an unclaimed turf gives no guards back (no owner to hold stock)")
	void failedContest_unclaimedTurf_addsNothing() {
		Turf turf = new Turf(2, "Turf", new CuboidRegion("world", 0, 0, 10, 10), null, BigDecimal.TEN, 0L, 0L);
		when(defenders.aliveCount(2)).thenReturn(2);

		listener.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.ABANDONED, 3));

		assertEquals(0, garrisons.count(2));
		verify(defenders).recall(2);
	}

	@Test
	@DisplayName("the give-back never takes the stock past Garrison.Max_Reserve (stock 11 + 2 survivors -> 12)")
	void failedContest_giveBackCappedAtMaxReserve() {
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), 1, BigDecimal.TEN, 0L, 0L);
		garrisons.add(1, 11);
		when(defenders.aliveCount(1)).thenReturn(2);

		listener.onFailed(new TurfCaptureFailedEvent(turf, TurfCaptureFailedEvent.Reason.DEFENDED, 2));

		assertEquals(12, garrisons.count(1));
	}

	@Test
	@DisplayName("guards deployed at contest start outlive the longest contest, so a stalled contest keeps them")
	void contestStart_lifespanOutlivesMaxContest() {
		IntSupplier lifespan = captureLifespanAtContestStart();

		// 10 minutes of Capture.Max_Contest_Minutes is 600 s; the guards must live past it.
		assertTrue(lifespan.getAsInt() > CaptureRules.DEFAULTS.maxContestMinutes() * 60,
		           "lifespan " + lifespan.getAsInt() + " must exceed the max contest");
	}

	@Test
	@DisplayName("T-269: a reload that raises Max_Contest_Minutes extends the guards already in the field")
	void deployLifespan_followsReload() {
		IntSupplier lifespan = captureLifespanAtContestStart();
		assertEquals(660, lifespan.getAsInt());

		when(rules.capture()).thenReturn(new CaptureRules(CaptureRules.Window.OWNERS_ONLINE, 120, 24, 15, 60, 30, 4));

		assertEquals(1860, lifespan.getAsInt(), "30 minutes of Max_Contest_Minutes + 60 s");
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

	/** Runs a capture start on an owned turf with 2 guards in stock and returns the lifespan supplier handed to the deployer. */
	private IntSupplier captureLifespanAtContestStart() {
		when(npcConfig.getDefenderConfig()).thenReturn(new TurfDefenderConfig("turf_defender", 16.0, 600));
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), 1, BigDecimal.TEN, 0L, 0L);
		garrisons.add(1, 2);
		Gang challenger = mock(Gang.class);
		when(challenger.getId()).thenReturn(2);

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			World world = mock(World.class);
			when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
			bukkit.statics().when(() -> Bukkit.getWorld("world")).thenReturn(world);

			listener.onCaptureStart(new TurfCaptureStartEvent(turf, challenger));
		}

		ArgumentCaptor<IntSupplier> lifespan = ArgumentCaptor.forClass(IntSupplier.class);
		verify(defenders).deploy(eq(1), any(Location.class), eq("turf_defender"), any(), eq(2), anyDouble(),
		                         lifespan.capture());
		return lifespan.getValue();
	}
}
