package org.luckyraven.gangland.copsncrooks.detainment.bail;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.support.FakeRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 0.21.0 fnd-origin (P-ORIGIN-3): the bail quote and the bail charge scale by the arrest origin. */
@DisplayName("BailService - bail scaled by the arrest origin")
class BailServiceTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000ee");

	private DetainmentRegistry         registry;
	private DetainmentCostsContract    costs;
	private DetainmentEconomyContract  economy;
	private DetainmentService          service;
	private ReleasePipeline            release;
	private BailService                bail;
	private Player                     player;

	@BeforeEach
	void setUp() {
		registry = new DetainmentRegistry(new FakeRepository<>(), new JailRegistry());
		costs    = mock(DetainmentCostsContract.class);
		when(costs.computeBailCost(2)).thenReturn(4_500.0);
		when(costs.computeBailCost(0)).thenReturn(2_500.0);
		when(costs.getBailMultiplier(ArrestOrigin.SURRENDERED)).thenReturn(0.75);
		when(costs.getBailMultiplier(ArrestOrigin.CUFFED)).thenReturn(1.0);
		when(costs.getBailMultiplier(ArrestOrigin.BUSTED_DOWNED)).thenReturn(1.0);
		economy = mock(DetainmentEconomyContract.class);
		service = mock(DetainmentService.class);
		release = mock(ReleasePipeline.class);
		bail    = new BailService(service, registry, costs, economy, release, mock(DetainmentSoundContract.class));
		player  = mock(Player.class);
		when(player.getUniqueId()).thenReturn(ID);
	}

	private void arrested(Integer wanted, ArrestOrigin origin) {
		DetainedPlayer row = new DetainedPlayer(ID, 1, DetainmentState.JAILED, null, null, wanted);
		row.setOrigin(origin);
		registry.getDetainedPlayers().put(ID, row);
	}

	@Test
	@DisplayName("a surrendered arrest at two stars pays computeBailCost(2) x the surrender bail multiplier")
	void bailScaledByOrigin() {
		arrested(2, ArrestOrigin.SURRENDERED);

		assertEquals(4_500.0 * 0.75, bail.computeCost(player), 1e-9);
	}

	@Test
	@DisplayName("CUFFED and BUSTED_DOWNED arrests pay the unscaled bail")
	void cuffedAndBusted_unscaled() {
		arrested(2, ArrestOrigin.CUFFED);
		assertEquals(4_500.0, bail.computeCost(player), 1e-9);

		arrested(2, ArrestOrigin.BUSTED_DOWNED);
		assertEquals(4_500.0, bail.computeCost(player), 1e-9);
	}

	@Test
	@DisplayName("a legacy row with no origin pays exactly the 0.19 bail")
	void nullOrigin_unscaled() {
		arrested(2, null);

		assertEquals(4_500.0, bail.computeCost(player), 1e-9);
		verify(costs).getBailMultiplier(ArrestOrigin.CUFFED);
	}

	@Test
	@DisplayName("a player with no detainment row still quotes the level-0 bail, unscaled")
	void noRow_levelZero() {
		assertEquals(2_500.0, bail.computeCost(player), 1e-9);
	}

	@Test
	@DisplayName("tryPayBail charges the scaled amount")
	void tryPayBail_chargesTheScaledAmount() {
		arrested(2, ArrestOrigin.SURRENDERED);
		when(service.isJailed(player)).thenReturn(true);
		when(economy.tryCharge(eq(player), any(double.class))).thenReturn(DetainmentEconomyContract.ChargeResult.SUCCESS);

		assertEquals(BailResult.SUCCESS, bail.tryPayBail(player));

		verify(economy).tryCharge(player, 4_500.0 * 0.75);
	}
}
