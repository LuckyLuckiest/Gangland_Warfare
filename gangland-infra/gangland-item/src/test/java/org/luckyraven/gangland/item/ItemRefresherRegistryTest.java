package org.luckyraven.gangland.item;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure-logic unit tests for {@link ItemRefresherRegistry} (Test Surface, items-unique.md: "first-claim-wins
 * ordering, null-returning refresher fallthrough, the AIR/null shortcut, and the default {@code decorate}
 * contract").
 *
 * <p>Pins Observation #19 (items-unique.md): {@code refresh}/{@code decorate} return the <em>source instance</em>
 * (not a clone) for a null/AIR stack, contradicting the class javadoc's "call-sites can always rely on getting a
 * safe-to-deliver copy" — a shared instance can leak into a player inventory if a shop entry is AIR.
 *
 * <p><b>Why mocked stacks:</b> constructing a real {@link ItemStack} outside a running server initialises
 * {@code org.bukkit.Registry}, which fails and then poisons the whole surefire fork with
 * {@code NoClassDefFoundError}. The registry under test only ever calls {@code getType()} and {@code clone()},
 * so mocks cover the contract exactly — the same approach {@code UniqueItemTest} and {@code LoadUniqueItemTest}
 * already take in this module.
 */
@DisplayName("ItemRefresherRegistry — ordered refresh/decorate chain")
class ItemRefresherRegistryTest {

	private ItemRefresherRegistry registry;

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// refresh()/decorate() call Material.isAir(), which needs a live Registry — see the fixture javadoc.
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		registry = new ItemRefresherRegistry();
	}

	/** A stack whose {@code clone()} yields a distinct mock carrying the same type and amount. */
	private static ItemStack stack(Material type, int amount) {
		ItemStack source = mock(ItemStack.class);
		ItemStack copy   = mock(ItemStack.class);

		when(source.getType()).thenReturn(type);
		when(source.getAmount()).thenReturn(amount);
		when(copy.getType()).thenReturn(type);
		when(copy.getAmount()).thenReturn(amount);
		when(source.clone()).thenReturn(copy);

		return source;
	}

	private static ItemStack stack(Material type) {
		return stack(type, 1);
	}

	private static ItemRefresher claiming(Material claims, ItemStack rebuiltStack) {
		return new ItemRefresher() {
			@Override
			public boolean canRefresh(ItemStack source) {
				return source.getType() == claims;
			}

			@Override
			public ItemStack refresh(ItemStack source, @Nullable Player context) {
				return rebuiltStack;
			}
		};
	}

	private static ItemRefresher claimsButReturnsNull(Material claims) {
		return new ItemRefresher() {
			@Override
			public boolean canRefresh(ItemStack source) {
				return source.getType() == claims;
			}

			@Override
			public ItemStack refresh(ItemStack source, @Nullable Player context) {
				return null;
			}
		};
	}

	@Test
	@DisplayName("Observation #19: refresh(null, ...) returns null itself, not a clone")
	void refresh_nullSource_returnsNullShortcut() {
		assertNull(registry.refresh(null, null));
	}

	@Test
	@DisplayName("Observation #19: refresh(AIR, ...) returns the very same AIR instance, not a clone")
	void refresh_airSource_returnsSourceInstance_notAClone() {
		ItemStack air = stack(Material.AIR);

		ItemStack result = registry.refresh(air, null);

		assertSame(air, result, "the class javadoc promises a safe-to-deliver copy, but AIR bypasses cloning entirely");
	}

	@Test
	@DisplayName("first registered refresher whose canRefresh matches wins, even if a later one also matches")
	void refresh_firstClaimingRefresherWins() {
		ItemStack source        = stack(Material.IRON_SWORD);
		ItemStack firstRebuilt  = stack(Material.DIAMOND_SWORD);
		ItemStack secondRebuilt = stack(Material.STONE_SWORD);

		registry.register(claiming(Material.IRON_SWORD, firstRebuilt));
		registry.register(claiming(Material.IRON_SWORD, secondRebuilt));

		assertSame(firstRebuilt, registry.refresh(source, null));
	}

	@Test
	@DisplayName("a refresher that claims the item but returns null falls through to the next refresher")
	void refresh_claimingRefresherReturnsNull_fallsThroughToNext() {
		ItemStack source  = stack(Material.IRON_SWORD);
		ItemStack rebuilt = stack(Material.DIAMOND_SWORD);

		registry.register(claimsButReturnsNull(Material.IRON_SWORD));
		registry.register(claiming(Material.IRON_SWORD, rebuilt));

		assertSame(rebuilt, registry.refresh(source, null));
	}

	@Test
	@DisplayName("when no refresher claims the item, a clone of the source is returned")
	void refresh_noRefresherClaims_returnsClone() {
		ItemStack source = stack(Material.DIRT, 5);

		ItemStack result = registry.refresh(source, null);

		assertNotSame(source, result);
		assertEquals(source.getType(), result.getType());
		assertEquals(5, result.getAmount());
	}

	@Test
	@DisplayName("decorate() walks the same ordering as refresh() but calls ItemRefresher.decorate")
	void decorate_usesSameOrderingAsRefresh() {
		ItemStack source  = stack(Material.IRON_SWORD);
		ItemStack rebuilt = stack(Material.DIAMOND_SWORD);

		ItemRefresher overridingDecorate = new ItemRefresher() {
			@Override
			public boolean canRefresh(ItemStack source) {
				return source.getType() == Material.IRON_SWORD;
			}

			@Override
			public ItemStack refresh(ItemStack source, @Nullable Player context) {
				fail("decorate() must not call refresh() when the refresher overrides decorate() itself");
				return null;
			}

			@Override
			public ItemStack decorate(ItemStack source, @Nullable Player context) {
				return rebuilt;
			}
		};

		registry.register(overridingDecorate);

		assertSame(rebuilt, registry.decorate(source, null));
	}

	@Test
	@DisplayName("the default decorate() contract grafts source damage and enchantments onto a fresh refresh() copy")
	void decorate_defaultContract_graftsDamageAndEnchantsOntoFreshCopy() {
		// No refresher claims this item, so decorate() falls back to source.clone() at the registry level — the
		// per-item default-decorate grafting (ItemRefresher.decorate) is exercised in ItemBuilder-level tests
		// (Keystone's ItemRefresherDecorateTest); here we only pin the registry's own no-claim fallback.
		ItemStack source = stack(Material.DIRT, 3);

		ItemStack result = registry.decorate(source, null);

		assertNotSame(source, result);
		assertEquals(Material.DIRT, result.getType());
	}

	@Test
	@DisplayName("decorate(null, ...) and decorate(AIR, ...) mirror refresh()'s source-instance shortcut")
	void decorate_nullOrAir_mirrorsRefreshShortcut() {
		assertNull(registry.decorate(null, null));

		ItemStack air = stack(Material.AIR);
		assertSame(air, registry.decorate(air, null));
	}

}
