package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("HotCashRouter - holder with a safe fallback")
class HotCashRouterTest {

	private final Player        player = mock(Player.class);
	private final BigDecimal    ten    = BigDecimal.TEN;
	private final HotCashRouter router = new HotCashRouter();

	@Test
	@DisplayName("nothing installed: WALLET, fence false, not installed")
	void uninstalledTakesWallet() {
		assertFalse(router.isInstalled());
		assertEquals(HotCash.Take.WALLET, router.take(player, ten, "pickup"));
		assertFalse(router.fence(player));
	}

	@Test
	@DisplayName("installed: answers pass through, install(null) uninstalls")
	void installedDelegates() {
		HotCash hotCash = mock(HotCash.class);
		when(hotCash.take(player, ten, "pickup")).thenReturn(HotCash.Take.BAGGED);
		when(hotCash.take(player, ten, "store")).thenReturn(HotCash.Take.FULL);
		when(hotCash.fence(player)).thenReturn(true);
		router.install(hotCash);

		assertTrue(router.isInstalled());
		assertEquals(HotCash.Take.BAGGED, router.take(player, ten, "pickup"));
		assertEquals(HotCash.Take.FULL, router.take(player, ten, "store"));
		assertTrue(router.fence(player));
		verify(hotCash).fence(player);

		router.install(null);
		assertFalse(router.isInstalled());
		assertEquals(HotCash.Take.WALLET, router.take(player, ten, "pickup"));
	}

	@Test
	@DisplayName("an implementation that throws falls back to WALLET / false and nothing escapes")
	void throwingHotCashFallsBackToWallet() {
		HotCash hotCash = mock(HotCash.class);
		when(hotCash.take(any(), any(), any())).thenThrow(new IllegalStateException("boom"));
		when(hotCash.fence(any())).thenThrow(new IllegalStateException("boom"));
		router.install(hotCash);

		assertDoesNotThrow(() -> router.take(player, ten, "pickup"));
		assertEquals(HotCash.Take.WALLET, router.take(player, ten, "pickup"));
		assertFalse(router.fence(player));
	}

	@Test
	@DisplayName("an implementation answering null is treated as WALLET")
	void nullTakeIsWallet() {
		router.install(mock(HotCash.class));

		assertEquals(HotCash.Take.WALLET, router.take(player, ten, "pickup"));
	}

}
