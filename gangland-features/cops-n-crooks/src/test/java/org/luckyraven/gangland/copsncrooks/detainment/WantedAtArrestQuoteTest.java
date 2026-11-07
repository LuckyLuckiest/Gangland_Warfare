package org.luckyraven.gangland.copsncrooks.detainment;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.bail.BailService;
import org.luckyraven.gangland.copsncrooks.detainment.bribe.BribeService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.support.FakeRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The wanted level the bail and the jail-bribe quotes are priced at: the level recorded at arrest, and 0 for a player
 * with no detainment row or a row that recorded no level.
 */
@DisplayName("bail and jail-bribe quotes: the wanted level at arrest")
class WantedAtArrestQuoteTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000ef");

	private DetainmentRegistry      registry;
	private DetainmentCostsContract costs;
	private BailService             bail;
	private BribeService            bribe;
	private Player                  player;

	@BeforeEach
	void setUp() {
		registry = new DetainmentRegistry(new FakeRepository<>(), new JailRegistry());
		costs    = mock(DetainmentCostsContract.class);
		when(costs.computeBailCost(0)).thenReturn(100.0);
		when(costs.computeBailCost(3)).thenReturn(300.0);
		when(costs.computeJailBribeCost(0)).thenReturn(10.0);
		when(costs.computeJailBribeCost(3)).thenReturn(30.0);
		when(costs.getBailMultiplier(ArrestOrigin.CUFFED)).thenReturn(1.0);

		bail   = new BailService(mock(DetainmentService.class), registry, costs, mock(DetainmentEconomyContract.class),
		                         mock(ReleasePipeline.class), mock(DetainmentSoundContract.class));
		bribe  = new BribeService(mock(DetainmentService.class), registry, costs,
		                          mock(DetainmentEconomyContract.class), mock(WantedClearContract.class),
		                          mock(ReleasePipeline.class), new CuffLockRegistry(),
		                          mock(DetainmentSoundContract.class));
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(ID);
	}

	private void arrested(Integer wanted) {
		registry.getDetainedPlayers().put(ID, new DetainedPlayer(ID, 1, DetainmentState.JAILED, null, null, wanted));
	}

	@Test
	@DisplayName("a row that recorded level 3 prices both quotes at level 3")
	void recordedLevel_isUsed() {
		arrested(3);

		assertEquals(300.0, bail.computeCost(player), 1e-9);
		assertEquals(30.0, bribe.computeJailBribeCost(player), 1e-9);
		verify(costs).computeBailCost(3);
		verify(costs).computeJailBribeCost(3);
	}

	@Test
	@DisplayName("a row with no recorded level prices both quotes at level 0")
	void nullLevel_isZero() {
		arrested(null);

		assertEquals(100.0, bail.computeCost(player), 1e-9);
		assertEquals(10.0, bribe.computeJailBribeCost(player), 1e-9);
		verify(costs).computeBailCost(0);
		verify(costs).computeJailBribeCost(0);
	}

	@Test
	@DisplayName("a player with no detainment row prices both quotes at level 0")
	void noRow_isZero() {
		assertEquals(100.0, bail.computeCost(player), 1e-9);
		assertEquals(10.0, bribe.computeJailBribeCost(player), 1e-9);
		verify(costs).computeBailCost(0);
		verify(costs).computeJailBribeCost(0);
	}

	@Test
	@DisplayName("a recorded level of 0 is used as it is")
	void recordedZero_isZero() {
		arrested(0);

		assertEquals(100.0, bail.computeCost(player), 1e-9);
		assertEquals(10.0, bribe.computeJailBribeCost(player), 1e-9);
	}
}
