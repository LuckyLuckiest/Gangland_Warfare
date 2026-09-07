package org.luckyraven.gangland.shop.transaction;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.item.ItemRefresherRegistry;
import org.luckyraven.gangland.shop.EntryKind;
import org.luckyraven.gangland.shop.ShopItemEntry;
import org.luckyraven.gangland.shop.support.FakePaymentHandler;

import java.math.BigDecimal;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Proves {@link ShopPurchaseService#purchase}: the balance short-circuit happens before any withdrawal, a
 * {@link PaymentException} maps to {@link PurchaseOutcome#ECONOMY_ERROR} without delivering anything, {@code copies}
 * is defensively clamped to at least 1 (civilians-traders-shops.md W14), and — pinning Observation #7
 * (civilians-traders-shops.md) — delivery overflow is silently dropped at the player's feet with the outcome still
 * reported as {@code SUCCESS} rather than {@link PurchaseOutcome#INVENTORY_FULL}, which the audit found is never
 * actually produced by this service despite the enum constant existing.
 */
@DisplayName("ShopPurchaseService — balance check, debit-then-deliver, copies clamping")
class ShopPurchaseServiceTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	private ItemRefresherRegistry refreshers;
	private ShopPurchaseService   service;
	private ShopItemEntry         entry;

	@BeforeEach
	void setUp() {
		refreshers = new ItemRefresherRegistry(); // no refreshers registered -> falls back to ItemStack.clone()
		service    = new ShopPurchaseService(refreshers);
		entry      = new ShopItemEntry(0, EntryKind.BUY, new ItemStack(Material.IRON_SWORD, 1), BigDecimal.TEN);
	}

	private Player mockBuyer(PlayerInventory inventory, World world) {
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(mock(Location.class));
		return player;
	}

	@Test
	@DisplayName("balance below price short-circuits to INSUFFICIENT_FUNDS without touching the player")
	void purchase_insufficientBalance_neverWithdrawsOrDelivers() {
		FakePaymentHandler payment = new FakePaymentHandler(new BigDecimal("5"));
		Player             player  = mock(Player.class);

		PurchaseResult result = service.purchase(player, payment, entry, BigDecimal.TEN);

		assertEquals(PurchaseOutcome.INSUFFICIENT_FUNDS, result.outcome());
		assertTrue(payment.withdrawals.isEmpty());
		verifyNoInteractions(player);
	}

	@Test
	@DisplayName("a PaymentException on withdraw maps to ECONOMY_ERROR and delivers nothing")
	void purchase_withdrawThrows_mapsToEconomyError() {
		FakePaymentHandler payment = new FakePaymentHandler(new BigDecimal("100"));
		payment.throwOnWithdraw("vault offline");
		Player player = mock(Player.class);

		PurchaseResult result = service.purchase(player, payment, entry, BigDecimal.TEN);

		assertEquals(PurchaseOutcome.ECONOMY_ERROR, result.outcome());
		assertEquals("vault offline", result.errorDetail());
		verifyNoInteractions(player);
	}

	@Test
	@DisplayName("a successful single-copy purchase withdraws once and delivers once")
	void purchase_success_withdrawsAndDeliversOnce() {
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		Player              player  = mockBuyer(inventory, world);
		FakePaymentHandler  payment = new FakePaymentHandler(new BigDecimal("100"));

		PurchaseResult result = service.purchase(player, payment, entry, BigDecimal.TEN);

		assertEquals(PurchaseOutcome.SUCCESS, result.outcome());
		assertEquals(BigDecimal.TEN, result.pricePaid());
		assertNotNull(result.delivery());
		assertEquals(1, payment.withdrawals.size());
		assertEquals(BigDecimal.TEN, payment.withdrawals.get(0));
		verify(inventory, times(1)).addItem(any(ItemStack.class));
	}

	@Test
	@DisplayName("copies < 1 is defensively clamped to exactly one delivery")
	void purchase_copiesBelowOne_clampsToOneDelivery() {
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		Player              player  = mockBuyer(inventory, world);
		FakePaymentHandler  payment = new FakePaymentHandler(new BigDecimal("100"));

		PurchaseResult result = service.purchase(player, payment, entry, BigDecimal.TEN, 0);

		assertEquals(PurchaseOutcome.SUCCESS, result.outcome());
		verify(inventory, times(1)).addItem(any(ItemStack.class));
	}

	@Test
	@DisplayName("multiple copies deliver one stack per copy while debiting the total exactly once")
	void purchase_multipleCopies_deliversOncePerCopy() {
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		Player              player  = mockBuyer(inventory, world);
		FakePaymentHandler  payment = new FakePaymentHandler(new BigDecimal("100"));

		PurchaseResult result = service.purchase(player, payment, entry, new BigDecimal("30"), 3);

		assertEquals(PurchaseOutcome.SUCCESS, result.outcome());
		assertEquals(1, payment.withdrawals.size(), "the total is debited once, not once per copy");
		assertEquals(new BigDecimal("30"), payment.withdrawals.get(0));
		verify(inventory, times(3)).addItem(any(ItemStack.class));
	}

	@Test
	@DisplayName("Observation #7 (civilians-traders-shops.md): inventory-full overflow is dropped at the player's " +
	            "feet and the outcome is still SUCCESS, never INVENTORY_FULL")
	void purchase_inventoryFull_dropsOverflowButStillReportsSuccess() {
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		ItemStack       leftover  = new ItemStack(Material.IRON_SWORD, 1);
		HashMap<Integer, ItemStack> overflow = new HashMap<>();
		overflow.put(0, leftover);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(overflow);
		Player              player  = mockBuyer(inventory, world);
		FakePaymentHandler  payment = new FakePaymentHandler(new BigDecimal("100"));

		PurchaseResult result = service.purchase(player, payment, entry, BigDecimal.TEN);

		assertEquals(PurchaseOutcome.SUCCESS, result.outcome(),
		            "the current implementation never returns INVENTORY_FULL, even when the inventory is full");
		verify(world, times(1)).dropItemNaturally(any(Location.class), eq(leftover));
	}

}
