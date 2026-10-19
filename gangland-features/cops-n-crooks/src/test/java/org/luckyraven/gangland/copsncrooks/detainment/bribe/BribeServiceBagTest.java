package org.luckyraven.gangland.copsncrooks.detainment.bribe;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.bag.BagFixture;
import org.luckyraven.gangland.copsncrooks.bag.BagFunding;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract.ChargeResult;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.luckyraven.gangland.copsncrooks.bag.BagFixture.bd;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane bag, P-FENCE-3 (D-W2-13, 14): a bribe draws from the bag first and charges the wallet only for the rest; the
 * bag is debited only after the wallet part succeeded, and always before the wanted clear (which converts what is left).
 */
@DisplayName("BribeService - bag first")
class BribeServiceBagTest {

	@TempDir
	Path tempDir;

	private BagFixture                fx;
	private Player                    player;
	private DetainmentService         detainment;
	private DetainmentRegistry        registry;
	private DetainmentCostsContract   costs;
	private DetainmentEconomyContract economy;
	private WantedClearContract       wanted;
	private ReleasePipeline           release;
	private CuffLockRegistry          locks;
	private BribeService              bribes;
	private CopNpc                    cop;
	private BigDecimal                bagAtClear;

	@BeforeEach
	void setUp() {
		fx     = new BagFixture(tempDir);
		player = fx.player("Runner");
		UUID id    = player.getUniqueId();
		UUID copId = UUID.randomUUID();

		detainment = mock(DetainmentService.class);
		when(detainment.isHandcuffed(player)).thenReturn(true);
		when(detainment.isJailed(player)).thenReturn(true);
		registry = new DetainmentRegistry(mock(IRepository.class), new JailRegistry());
		DetainedPlayer detained = new DetainedPlayer(id, 1, DetainmentState.JAILED);
		detained.setWantedAtArrest(2);
		registry.getDetainedPlayers().put(id, detained);

		costs = mock(DetainmentCostsContract.class);
		when(costs.computeHandcuffBribeCost(2)).thenReturn(1000.0);
		when(costs.computeJailBribeCost(2)).thenReturn(1000.0);
		when(costs.getJailBribeSuccessChance()).thenReturn(1.0);
		when(costs.getJailBribeFailPenaltySeconds()).thenReturn(60);

		economy = mock(DetainmentEconomyContract.class);
		when(economy.tryCharge(eq(player), anyDouble())).thenReturn(ChargeResult.SUCCESS);

		wanted = mock(WantedClearContract.class);
		when(wanted.getWantedLevel(id)).thenReturn(2);
		doAnswer(inv -> {
			bagAtClear = fx.bag(player);
			return null;
		}).when(wanted).clearWanted(eq(id), any(WantedCause.class));

		release = mock(ReleasePipeline.class);
		locks   = new CuffLockRegistry();
		locks.tryAcquire(id, copId);

		cop = mock(CopNpc.class);
		NPC npc = mock(NPC.class);
		when(npc.getUniqueId()).thenReturn(copId);
		when(cop.getNpc()).thenReturn(npc);

		bribes = new BribeService(detainment, registry, costs, economy, wanted, release, locks,
		                          mock(DetainmentSoundContract.class));
		bribes.setBagFunding(fx.service);

		fx.level(player, 2);
	}

	private void bag(String amount) {
		fx.service.take(player, bd(amount), "pickup");
	}

	@Test
	@DisplayName("cost 1000, bag 600: the wallet is charged 400, the bag is empty at the clear, the player is released")
	void handcuffBribeBagFirst() {
		bag("600");

		assertEquals(BribeResult.SUCCESS, bribes.tryHandcuffBribe(player, cop));

		verify(economy).tryCharge(player, 400.0);
		assertEquals(0, bd("0").compareTo(bagAtClear), "debited before the wanted clear");
		verify(wanted).clearWanted(player.getUniqueId(), WantedCause.BRIBE);
		verify(release).release(player, ReleaseReason.HANDCUFF_BRIBE);
		assertTrue(fx.debug.contains("BAG Runner bribe bag=600 wallet=400"), fx.debug.toString());
	}

	@Test
	@DisplayName("bag 1500 covers the whole cost: the wallet is never charged and 500 stays for the clear to convert")
	void bagCoversTheCost() {
		bag("1500");

		assertEquals(BribeResult.SUCCESS, bribes.tryHandcuffBribe(player, cop));

		verify(economy, never()).tryCharge(any(), anyDouble());
		assertEquals(0, bd("500").compareTo(bagAtClear));
	}

	@Test
	@DisplayName("D-W2-14: the wallet part fails - the bag is untouched and nobody is cleared")
	void walletShortLeavesBagUntouched() {
		bag("600");
		when(economy.tryCharge(player, 400.0)).thenReturn(ChargeResult.INSUFFICIENT_FUNDS);

		assertEquals(BribeResult.INSUFFICIENT_FUNDS, bribes.tryHandcuffBribe(player, cop));

		assertEquals(0, bd("600").compareTo(fx.bag(player)));
		verify(wanted, never()).clearWanted(any(), any());
	}

	@Test
	@DisplayName("an economy error also leaves the bag untouched")
	void economyErrorLeavesBagUntouched() {
		bag("600");
		when(economy.tryCharge(player, 400.0)).thenReturn(ChargeResult.ECONOMY_ERROR);

		assertEquals(BribeResult.ECONOMY_ERROR, bribes.tryHandcuffBribe(player, cop));

		assertEquals(0, bd("600").compareTo(fx.bag(player)));
	}

	@Test
	@DisplayName("an empty bag: the wallet pays the whole cost and no Bag.Bribe line is printed")
	void emptyBagPaysFromWallet() {
		assertEquals(BribeResult.SUCCESS, bribes.tryHandcuffBribe(player, cop));

		verify(economy).tryCharge(player, 1000.0);
		assertTrue(fx.chat.isEmpty(), fx.chat.toString());
	}

	@Test
	@DisplayName("no funding installed (bag module off): today's wallet-only path")
	void noFundingIsWalletOnly() {
		bribes.setBagFunding(null);
		bag("600");

		assertEquals(BribeResult.SUCCESS, bribes.tryHandcuffBribe(player, cop));

		verify(economy).tryCharge(player, 1000.0);
		assertEquals(0, bd("600").compareTo(bagAtClear), "the bag is left for the clear to convert");
	}

	@Test
	@DisplayName("the funding is asked before the charge and debited only on success (mock funding)")
	void debitOnlyAfterSuccess() {
		BagFunding funding = mock(BagFunding.class);
		when(funding.available(player)).thenReturn(bd("600"));
		bribes.setBagFunding(funding);
		when(economy.tryCharge(player, 400.0)).thenReturn(ChargeResult.INSUFFICIENT_FUNDS);

		bribes.tryHandcuffBribe(player, cop);

		verify(funding, never()).debit(any(), any());
	}

	@Test
	@DisplayName("D-W2-13: the jail bribe uses the same helper - bag 300, wallet 700")
	void jailBribeBagFirst() {
		bag("300");

		assertEquals(BribeResult.SUCCESS, bribes.tryJailBribe(player));

		verify(economy).tryCharge(player, 700.0);
		assertEquals(0, bd("0").compareTo(fx.bag(player)));
		verify(release).release(player, ReleaseReason.JAIL_BRIBE);
	}

	@Test
	@DisplayName("a failed jail bribe still consumes the bag part (the money is gone either way)")
	void jailBribeFailStillConsumesBag() {
		when(costs.getJailBribeSuccessChance()).thenReturn(0.0);
		bag("300");

		BribeResult result = bribes.tryJailBribe(player);

		assertEquals(BribeResult.FAIL, result);
		assertEquals(0, bd("0").compareTo(fx.bag(player)));
		verify(economy).tryCharge(player, 700.0);
	}
}
