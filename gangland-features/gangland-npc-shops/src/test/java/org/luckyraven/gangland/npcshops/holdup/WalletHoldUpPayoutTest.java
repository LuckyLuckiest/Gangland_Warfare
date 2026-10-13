package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npcshops.trader.economy.TraderEconomyContract;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WalletHoldUpPayout - uniform take into the wallet")
class WalletHoldUpPayoutTest {

	private final Player                player  = mock(Player.class);
	private final TraderEconomyContract economy = mock(TraderEconomyContract.class);

	private static HoldUpSettings range(double min, double max) {
		return new HoldUpSettings(true, 6.0, 10.0, 10, 10, 0.2, 4, min, max, 300, "f", "d", "a", "c");
	}

	private BigDecimal payWith(double rng, double min, double max) {
		when(economy.deposit(any(), any())).thenReturn(true);
		BigDecimal paid = new WalletHoldUpPayout(economy, () -> range(min, max), () -> rng).pay(player);
		var captor = forClass(BigDecimal.class);
		verify(economy).deposit(org.mockito.ArgumentMatchers.eq(player), captor.capture());
		assertEquals(0, paid.compareTo(captor.getValue()), "returned amount is the deposited amount");
		return captor.getValue();
	}

	@Test
	@DisplayName("rng 0 pays Take_Min, rng 0.999 stays within Take_Max, both rounded to cents")
	void amountWithinBounds() {
		BigDecimal low = payWith(0.0, 200.0, 600.0);
		assertEquals(0, low.compareTo(new BigDecimal("200.00")));
		assertTrue(low.scale() <= 2);
	}

	@Test
	@DisplayName("rng 0.999 never exceeds Take_Max and keeps at most two decimals")
	void upperBound() {
		BigDecimal high = payWith(0.999, 200.0, 600.0);
		assertTrue(high.compareTo(new BigDecimal("600.00")) <= 0);
		assertTrue(high.compareTo(new BigDecimal("599.00")) > 0);
		assertTrue(high.scale() <= 2);
	}

	@Test
	@DisplayName("rng 0.5 pays the midpoint")
	void midpoint() {
		assertEquals(0, payWith(0.5, 200.0, 600.0).compareTo(new BigDecimal("400.00")));
	}

	@Test
	@DisplayName("the bounds are read at pay time so a reload changes the next take")
	void boundsReadAtPayTime() {
		AtomicReference<HoldUpSettings> current = new AtomicReference<>(range(200.0, 600.0));
		when(economy.deposit(any(), any())).thenReturn(true);
		WalletHoldUpPayout payout = new WalletHoldUpPayout(economy, current::get, () -> 0.0);

		current.set(range(1000.0, 2000.0));

		assertEquals(0, payout.pay(player).compareTo(new BigDecimal("1000.00")));
	}

	@Test
	@DisplayName("a refused deposit (no account loaded) pays nothing and reports zero")
	void refusedDepositReportsZero() {
		when(economy.deposit(any(), any())).thenReturn(false);
		BigDecimal paid = new WalletHoldUpPayout(economy, () -> range(200.0, 600.0), () -> 0.5).pay(player);
		assertEquals(0, paid.compareTo(BigDecimal.ZERO));
	}

}
