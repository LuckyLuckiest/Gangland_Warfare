package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@code MoneyDepositService#withdraw}, the debit side introduced for Observation #6
 * (users-levels-economy-bank.md) / US-06: player-death cash drops used to spawn a money item without taking
 * anything off the dead player, so every death minted currency. The drop listener now debits first and drops only
 * what came back, which makes the clamping behaviour proved here load-bearing — a player who cannot cover the
 * rolled amount must drop what they had, not throw {@code EconomyException} and abort the whole death path.
 */
@DisplayName("GanglandMoneyDepositService - withdraw clamps to the balance and never goes negative")
class GanglandMoneyDepositServiceTest {

	@SuppressWarnings("unchecked")
	private final UserManager<Player> userManager = mock(UserManager.class);

	private GanglandMoneyDepositService service;
	private Player                      player;

	@BeforeEach
	void setUp() {
		service = new GanglandMoneyDepositService(userManager, mock(MoneyAddon.class), mock(PlaceholderService.class));
		player  = mock(Player.class);
	}

	@SuppressWarnings("unchecked")
	private EconomyHandler cacheUserWith(double balance) {
		// A detached handler: no EconomyOwner, so no Vault round-trip, but the arithmetic is the production one.
		EconomyHandler economy = new EconomyHandler(Currency.of(balance), null, false);
		User<Player>   user    = mock(User.class);
		when(user.getEconomy()).thenReturn(economy);
		when(userManager.getUser(player)).thenReturn(user);
		return economy;
	}

	@Test
	@DisplayName("a covered amount is taken in full")
	void withdraw_coveredAmount_debitsTheFullAmount() {
		EconomyHandler economy = cacheUserWith(500D);

		assertEquals(100D, service.withdraw(player, 100D), 1e-9);
		assertEquals(0, Currency.of(400D).compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("an amount larger than the balance is clamped, leaving the player at zero rather than throwing")
	void withdraw_moreThanTheBalance_clampsToTheBalance() {
		EconomyHandler economy = cacheUserWith(40D);

		assertEquals(40D, service.withdraw(player, 100D), 1e-9);
		assertEquals(0, Currency.ZERO.compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("a broke player gives up nothing")
	void withdraw_zeroBalance_returnsZero() {
		EconomyHandler economy = cacheUserWith(0D);

		assertEquals(0D, service.withdraw(player, 100D), 1e-9);
		assertEquals(0, Currency.ZERO.compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("an uncached player gives up nothing")
	void withdraw_unknownPlayer_returnsZero() {
		when(userManager.getUser(player)).thenReturn(null);

		assertEquals(0D, service.withdraw(player, 100D), 1e-9);
	}

	@Test
	@DisplayName("null and non-positive requests are refused")
	void withdraw_nullOrNonPositive_returnsZero() {
		EconomyHandler economy = cacheUserWith(500D);

		assertEquals(0D, service.withdraw(null, 100D), 1e-9);
		assertEquals(0D, service.withdraw(player, 0D), 1e-9);
		assertEquals(0D, service.withdraw(player, -100D), 1e-9);
		assertEquals(0, Currency.of(500D).compareTo(economy.getAmount()), "no request may add money");
	}

}
