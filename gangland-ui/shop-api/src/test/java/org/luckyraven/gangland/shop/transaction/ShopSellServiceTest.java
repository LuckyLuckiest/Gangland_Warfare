package org.luckyraven.gangland.shop.transaction;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.shop.support.FakePaymentHandler;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Proves {@link ShopSellService#sell}: every "nothing to sell" shape (null list, empty list, null total, a
 * zero/negative total, and a list whose stacks all have amount 0) collapses to {@link SellOutcome#NOTHING_VALUED}
 * without touching the payment handler, a successful sell deposits the offered total exactly once and counts items
 * by summed stack amount rather than list size, and a {@link PaymentException} on deposit maps to
 * {@link SellOutcome#ECONOMY_ERROR}.
 */
@DisplayName("ShopSellService — nothing-to-sell short-circuits and the deposit path")
class ShopSellServiceTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	private ShopSellService service;
	private Player          player;

	@BeforeEach
	void setUp() {
		service = new ShopSellService();
		player  = mock(Player.class);
	}

	@Test
	@DisplayName("a null offer list is NOTHING_VALUED and never touches the payment handler")
	void sell_nullList_nothingValued() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);

		SellResult result = service.sell(player, payment, null, BigDecimal.TEN);

		assertEquals(SellOutcome.NOTHING_VALUED, result.outcome());
		assertTrue(payment.deposits.isEmpty());
	}

	@Test
	@DisplayName("an empty offer list is NOTHING_VALUED")
	void sell_emptyList_nothingValued() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);

		SellResult result = service.sell(player, payment, List.of(), BigDecimal.TEN);

		assertEquals(SellOutcome.NOTHING_VALUED, result.outcome());
	}

	@Test
	@DisplayName("a null total is NOTHING_VALUED even with real items offered")
	void sell_nullTotal_nothingValued() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);
		List<ItemStack>    offered = List.of(new ItemStack(Material.DIAMOND, 3));

		SellResult result = service.sell(player, payment, offered, null);

		assertEquals(SellOutcome.NOTHING_VALUED, result.outcome());
	}

	@Test
	@DisplayName("a zero or negative total is NOTHING_VALUED")
	void sell_nonPositiveTotal_nothingValued() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);
		List<ItemStack>    offered = List.of(new ItemStack(Material.DIAMOND, 3));

		assertEquals(SellOutcome.NOTHING_VALUED, service.sell(player, payment, offered, BigDecimal.ZERO).outcome());
		assertEquals(SellOutcome.NOTHING_VALUED,
		            service.sell(player, payment, offered, new BigDecimal("-1")).outcome());
	}

	@Test
	@DisplayName("stacks whose amounts sum to zero are NOTHING_VALUED despite a positive total")
	void sell_zeroAmountStacks_nothingValued() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);
		List<ItemStack>    offered = List.of(new ItemStack(Material.DIAMOND, 0));

		SellResult result = service.sell(player, payment, offered, BigDecimal.TEN);

		assertEquals(SellOutcome.NOTHING_VALUED, result.outcome());
		assertTrue(payment.deposits.isEmpty(), "a zero-amount offer must not deposit money");
	}

	@Test
	@DisplayName("a successful sell deposits the offered total once and counts items by summed stack amount")
	void sell_success_depositsOnceAndCountsBySummedAmount() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);
		List<ItemStack>    offered = List.of(new ItemStack(Material.DIAMOND, 3), new ItemStack(Material.EMERALD, 2));

		SellResult result = service.sell(player, payment, offered, new BigDecimal("50"));

		assertEquals(SellOutcome.SUCCESS, result.outcome());
		assertEquals(new BigDecimal("50"), result.totalPaid());
		assertEquals(5, result.itemsSold(), "item count must sum stack amounts (3 + 2), not list.size() (2)");
		assertEquals(1, payment.deposits.size());
		assertEquals(new BigDecimal("50"), payment.deposits.get(0));
	}

	@Test
	@DisplayName("null entries inside the offer list are skipped when counting items")
	void sell_nullEntriesInList_skippedFromCount() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);
		List<ItemStack>    offered = java.util.Arrays.asList(new ItemStack(Material.DIAMOND, 4), null);

		SellResult result = service.sell(player, payment, offered, new BigDecimal("10"));

		assertEquals(SellOutcome.SUCCESS, result.outcome());
		assertEquals(4, result.itemsSold());
	}

	@Test
	@DisplayName("a PaymentException on deposit maps to ECONOMY_ERROR")
	void sell_depositThrows_mapsToEconomyError() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);
		payment.throwOnDeposit("bank closed");
		List<ItemStack> offered = List.of(new ItemStack(Material.DIAMOND, 1));

		SellResult result = service.sell(player, payment, offered, BigDecimal.TEN);

		assertEquals(SellOutcome.ECONOMY_ERROR, result.outcome());
		assertEquals("bank closed", result.errorDetail());
	}

	@Test
	@DisplayName("the service never reads or mutates the player directly (the view owns item movement)")
	void sell_neverTouchesPlayerDirectly() {
		FakePaymentHandler payment = new FakePaymentHandler(BigDecimal.ZERO);
		List<ItemStack>    offered = List.of(new ItemStack(Material.DIAMOND, 1));

		service.sell(player, payment, offered, BigDecimal.TEN);

		verifyNoInteractions(player);
	}

}
