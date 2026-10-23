package org.luckyraven.gangland.data.economy;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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


	private MoneyAddon chatAddon() {
		MoneyAddon addon = mock(MoneyAddon.class);
		when(addon.getPickupChatMessage()).thenReturn("GOT %amount%");
		return addon;
	}

	private HotCash lastHotCash;

	private GanglandMoneyDepositService routed(HotCash.Take answer, MoneyAddon addon) {
		HotCash hotCash = mock(HotCash.class);
		lastHotCash = hotCash;
		when(hotCash.take(any(), any(), anyString())).thenReturn(answer);
		HotCashRouter router = new HotCashRouter();
		router.install(hotCash);
		PlaceholderService ph = mock(PlaceholderService.class);
		when(ph.convert(any(), anyString())).thenAnswer(i -> i.getArgument(1));
		return new GanglandMoneyDepositService(userManager, addon, ph, router);
	}

	@Test
	@DisplayName("pickup, router WALLET: balance rises and the chat template is sent")
	void pickupNotWantedPaysWallet() {
		EconomyHandler economy = cacheUserWith(10D);

		assertTrue(routed(HotCash.Take.WALLET, chatAddon()).depositPickup(player, 50D, "small"));

		assertEquals(0, Currency.of(60D).compareTo(economy.getAmount()));
		verify(player).sendMessage(contains("GOT 50"));
	}

	@Test
	@DisplayName("pickup, router BAGGED: wallet untouched, no chat line, true")
	void pickupBaggedSkipsWallet() {
		EconomyHandler economy = cacheUserWith(10D);

		assertTrue(routed(HotCash.Take.BAGGED, chatAddon()).depositPickup(player, 50D, "small"));

		assertEquals(0, Currency.of(10D).compareTo(economy.getAmount()));
		verify(player, never()).sendMessage(anyString());
		verify(lastHotCash).take(eq(player), argThat(amount -> amount.compareTo(new BigDecimal("50")) == 0),
		                         eq("pickup"));
	}

	@Test
	@DisplayName("pickup, router FULL: false, wallet untouched, no chat line")
	void pickupFullReturnsFalse() {
		EconomyHandler economy = cacheUserWith(10D);

		assertFalse(routed(HotCash.Take.FULL, chatAddon()).depositPickup(player, 50D, "small"));

		assertEquals(0, Currency.of(10D).compareTo(economy.getAmount()));
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("pickup with a null player or a non-positive amount is the old silent no-op (true, router unused)")
	void pickupNoOpCases() {
		HotCash hotCash = mock(HotCash.class);
		HotCashRouter router = new HotCashRouter();
		router.install(hotCash);
		GanglandMoneyDepositService svc = new GanglandMoneyDepositService(userManager, chatAddon(),
		                                                                 mock(PlaceholderService.class), router);

		assertTrue(svc.depositPickup(null, 50D, "small"));
		assertTrue(svc.depositPickup(player, 0D, "small"));
		verify(hotCash, never()).take(any(), any(), anyString());
	}

	@Test
	@DisplayName("pickup with the 3-arg constructor (no router) pays the wallet")
	void pickupWithoutRouterPaysWallet() {
		EconomyHandler economy = cacheUserWith(10D);

		assertTrue(service.depositPickup(player, 5D, "small"));

		assertEquals(0, Currency.of(15D).compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("shop/trader deposit(BigDecimal) never consults the router, even when bagging")
	void shopDepositNeverConsultsRouter() {
		EconomyHandler economy = cacheUserWith(10D);
		HotCash hotCash = mock(HotCash.class);
		when(hotCash.take(any(), any(), anyString())).thenReturn(HotCash.Take.BAGGED);
		HotCashRouter router = new HotCashRouter();
		router.install(hotCash);
		GanglandMoneyDepositService svc = new GanglandMoneyDepositService(userManager, mock(MoneyAddon.class),
		                                                                 mock(PlaceholderService.class), router);

		svc.deposit(player, new BigDecimal("25"), "shop");

		assertEquals(0, Currency.of(35D).compareTo(economy.getAmount()));
		verify(hotCash, never()).take(any(), any(), anyString());
	}

}
