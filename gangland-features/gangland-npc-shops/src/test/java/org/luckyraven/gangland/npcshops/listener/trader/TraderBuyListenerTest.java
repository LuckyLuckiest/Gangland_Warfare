package org.luckyraven.gangland.npcshops.listener.trader;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.npcshops.events.trader.TraderBuyRequestEvent;
import org.luckyraven.gangland.npcshops.trader.TraderManager;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;
import org.luckyraven.gangland.npcshops.trader.mood.MoodService;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.keystone.shop.EntryKind;
import org.luckyraven.keystone.shop.ShopItemEntry;
import org.luckyraven.keystone.shop.message.ShopDisplayResolver;
import org.luckyraven.keystone.shop.message.ShopMessageContract;
import org.luckyraven.keystone.shop.transaction.PaymentException;
import org.luckyraven.keystone.shop.transaction.PaymentHandler;
import org.luckyraven.keystone.shop.transaction.PurchaseOutcome;
import org.luckyraven.keystone.shop.transaction.PurchaseResult;
import org.luckyraven.keystone.shop.transaction.ShopPurchaseService;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Characterises the {@code PaymentHandler} that {@link TraderBuyListener} builds over the user's economy. */
@DisplayName("TraderBuyListener - economy to PaymentHandler adapter")
class TraderBuyListenerTest {

	private EconomyHandler                        economy        = new EconomyHandler(new BigDecimal("42.5"), null, false);
	private final AtomicReference<PaymentHandler> captured       = new AtomicReference<>();
	private final ShopPurchaseService             purchaseService = mock(ShopPurchaseService.class);

	@SuppressWarnings("unchecked")
	private TraderBuyListener listenerForUserWithEconomy() {
		Player player = mock(Player.class);
		User<Player> user = mock(User.class);
		when(user.getEconomy()).thenReturn(economy);
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);
		when(purchaseService.purchase(any(), any(), any(), any(), anyInt())).thenAnswer(invocation -> {
			captured.set(invocation.getArgument(1));
			return PurchaseResult.of(PurchaseOutcome.INVENTORY_FULL);
		});
		TraderBuyListener listener = new TraderBuyListener(userManager, mock(TraderManager.class),
		                                                   mock(MoodService.class), purchaseService,
		                                                   mock(ShopMessageContract.class),
		                                                   mock(ShopDisplayResolver.class));
		ShopItemEntry entry = new ShopItemEntry(0, EntryKind.BUY, new ItemStack(Material.DIAMOND), BigDecimal.TEN);
		listener.onBuyRequest(new TraderBuyRequestEvent(player, mock(TraderNpc.class), entry, BigDecimal.TEN, 2));
		return listener;
	}

	@Test
	@DisplayName("the purchase service is handed a payment handler over the user's economy")
	void purchase_receivesAnAdapter() {
		listenerForUserWithEconomy();

		assertTrue(captured.get() != null);
	}

	@Test
	@DisplayName("getBalance reads the economy amount")
	void getBalance_readsEconomy() {
		listenerForUserWithEconomy();

		assertEquals(0, new BigDecimal("42.5").compareTo(captured.get().getBalance()));
	}

	@Test
	@DisplayName("withdraw and deposit change the economy amount")
	void withdrawAndDeposit_forward() throws Exception {
		listenerForUserWithEconomy();

		captured.get().withdraw(new BigDecimal("3"));
		assertEquals(0, new BigDecimal("39.5").compareTo(economy.getAmount()));
		captured.get().deposit(new BigDecimal("4"));
		assertEquals(0, new BigDecimal("43.5").compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("an EconomyException from withdraw is rewrapped as a PaymentException with the same message and cause")
	void withdraw_wrapsEconomyException() {
		EconomyException failure = new EconomyException("not enough");
		economy = new EconomyHandler(BigDecimal.TEN, null, false) {
			@Override
			public void withdrawAmount(BigDecimal amount) throws EconomyException {
				throw failure;
			}
		};
		listenerForUserWithEconomy();

		PaymentException thrown = assertThrows(PaymentException.class, () -> captured.get().withdraw(BigDecimal.ONE));

		assertEquals("not enough", thrown.getMessage());
		assertSame(failure, thrown.getCause());
	}

	@Test
	@DisplayName("an EconomyException from deposit is rewrapped as a PaymentException with the same message and cause")
	void deposit_wrapsEconomyException() {
		EconomyException failure = new EconomyException("vault down");
		economy = new EconomyHandler(BigDecimal.TEN, null, false) {
			@Override
			public void depositAmount(BigDecimal amount) {
				throw failure;
			}
		};
		listenerForUserWithEconomy();

		PaymentException thrown = assertThrows(PaymentException.class, () -> captured.get().deposit(BigDecimal.ONE));

		assertEquals("vault down", thrown.getMessage());
		assertSame(failure, thrown.getCause());
	}
}