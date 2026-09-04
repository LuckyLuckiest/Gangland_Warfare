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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves {@link ShopBarterService#barter} is a pure item-for-item swap, matching civilians-traders-shops.md's
 * "barter is a pure item-for-item swap with no economy involvement" design decision: the method signature takes no
 * {@code PaymentHandler} at all, so there is no way for money to move through this path. Also pins the
 * {@code offered} → {@code consumed} cloning (defensive copies, not the same list references) and the two outcome
 * short-circuits ({@code NOT_BARTERABLE} on an empty offer, {@code INSUFFICIENT_VALUE} below the asking price).
 */
@DisplayName("ShopBarterService — pure item swap, no economy involvement")
class ShopBarterServiceTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// Subject code reaches Material.isAir() / an XSeries registry lookup — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	private ItemRefresherRegistry refreshers;
	private ShopBarterService     service;
	private ShopItemEntry         entry;

	@BeforeEach
	void setUp() {
		refreshers = new ItemRefresherRegistry();
		service    = new ShopBarterService(refreshers);
		entry      = new ShopItemEntry(0, EntryKind.BARTER, new ItemStack(Material.GOLD_INGOT, 1), null);
	}

	@Test
	@DisplayName("a null offer list is NOT_BARTERABLE")
	void barter_nullOffer_notBarterable() {
		BarterResult result = service.barter(mock(Player.class), entry, BigDecimal.TEN, BigDecimal.ZERO, null);

		assertEquals(BarterOutcome.NOT_BARTERABLE, result.outcome());
		assertEquals(List.of(), result.consumed());
	}

	@Test
	@DisplayName("an empty offer list is NOT_BARTERABLE")
	void barter_emptyOffer_notBarterable() {
		BarterResult result = service.barter(mock(Player.class), entry, BigDecimal.TEN, BigDecimal.ZERO, List.of());

		assertEquals(BarterOutcome.NOT_BARTERABLE, result.outcome());
	}

	@Test
	@DisplayName("an offered value below the asking value is INSUFFICIENT_VALUE and delivers nothing")
	void barter_offeredBelowAsking_insufficientValue() {
		List<ItemStack> offered = List.of(new ItemStack(Material.IRON_INGOT, 4));

		BarterResult result = service.barter(mock(Player.class), entry, new BigDecimal("10"),
		                                     new BigDecimal("9.99"), offered);

		assertEquals(BarterOutcome.INSUFFICIENT_VALUE, result.outcome());
		assertEquals(List.of(), result.consumed());
	}

	@Test
	@DisplayName("an offered value exactly equal to the asking value succeeds (>= threshold, not strictly >)")
	void barter_offeredEqualsAsking_succeeds() {
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(mock(World.class));
		when(player.getLocation()).thenReturn(mock(Location.class));

		List<ItemStack> offered = List.of(new ItemStack(Material.IRON_INGOT, 4));

		BarterResult result = service.barter(player, entry, new BigDecimal("10"), new BigDecimal("10"), offered);

		assertEquals(BarterOutcome.SUCCESS, result.outcome());
	}

	@Test
	@DisplayName("a successful barter clones every offered stack into consumed() rather than sharing references")
	void barter_success_clonesOfferedIntoConsumed() {
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(mock(World.class));
		when(player.getLocation()).thenReturn(mock(Location.class));

		ItemStack       offeredStack = new ItemStack(Material.IRON_INGOT, 4);
		List<ItemStack> offered      = List.of(offeredStack);

		BarterResult result = service.barter(player, entry, new BigDecimal("5"), new BigDecimal("5"), offered);

		assertEquals(BarterOutcome.SUCCESS, result.outcome());
		assertEquals(1, result.consumed().size());
		assertEquals(offeredStack, result.consumed().get(0), "clone must still be .equals() to the original");
		assertNotSame(offeredStack, result.consumed().get(0), "consumed() must hold a clone, not the same instance");
		assertNotNull(result.delivery());
		verify(inventory, times(1)).addItem(any(ItemStack.class));
	}

	@Test
	@DisplayName("null entries inside the offer list are skipped rather than crashing the clone loop")
	void barter_nullEntriesInOffer_skipped() {
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(mock(World.class));
		when(player.getLocation()).thenReturn(mock(Location.class));

		List<ItemStack> offered = java.util.Arrays.asList(new ItemStack(Material.IRON_INGOT, 4), null);

		BarterResult result = service.barter(player, entry, new BigDecimal("5"), new BigDecimal("5"), offered);

		assertEquals(BarterOutcome.SUCCESS, result.outcome());
		assertEquals(1, result.consumed().size(), "the null entry must be skipped, not turned into a null consumed()");
	}

	@Test
	@DisplayName("no PaymentHandler exists on this call at all — barter cannot move economy money by construction")
	void barter_signatureHasNoPaymentHandler() throws NoSuchMethodException {
		var method = ShopBarterService.class.getMethod("barter", Player.class, ShopItemEntry.class,
		                                                BigDecimal.class, BigDecimal.class, List.class);

		for (Class<?> paramType : method.getParameterTypes()) {
			assertNotEconomyType(paramType);
		}
	}

	private void assertNotEconomyType(Class<?> paramType) {
		if (paramType.getSimpleName().toLowerCase().contains("payment")
		    || paramType.getSimpleName().toLowerCase().contains("economy")) {
			throw new AssertionError("ShopBarterService.barter must not accept an economy/payment parameter: "
			                          + paramType);
		}
	}

}
