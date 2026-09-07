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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the priority overload added for the module split (weapon T8, §6 C-1 / README decision "weapon C-1
 * accepted"): mirrors {@link ItemSerializerRegistryPriorityTest} but for {@link ItemRefresherRegistry}. Today
 * {@code WeaponRefresher} and {@code WearableRefresher} are registered <em>before</em>
 * {@code UniqueItemRefresher} in {@code ItemConfig}, and a unique weapon carries both the weapon and the
 * uniqueItem NBT tag — so after the flip the weapon module must be able to register at a priority that still
 * outranks the core's {@code uniqueItemRefresher}, while a default-priority module refresher (the ammunition one)
 * must keep sorting <em>behind</em> the core's default-priority refresher, exactly as plain insertion order does
 * today.
 */
@DisplayName("ItemRefresherRegistry — priority overload")
class ItemRefresherRegistryPriorityTest {

	private ItemRefresherRegistry registry;

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		// refresh() calls Material.isAir(), which needs a live Registry — see ItemRefresherRegistryTest's javadoc.
		BukkitRegistryFixture.install();
	}

	@BeforeEach
	void setUp() {
		registry = new ItemRefresherRegistry();
	}

	/** A stack whose {@code clone()} yields a distinct mock carrying the same type. */
	private static ItemStack stack(Material type) {
		ItemStack source = mock(ItemStack.class);
		ItemStack copy    = mock(ItemStack.class);

		when(source.getType()).thenReturn(type);
		when(copy.getType()).thenReturn(type);
		when(source.clone()).thenReturn(copy);

		return source;
	}

	private static ItemRefresher claimsEverythingAndReturns(ItemStack rebuiltStack) {
		return new ItemRefresher() {
			@Override
			public boolean canRefresh(ItemStack source) {
				return true;
			}

			@Override
			public ItemStack refresh(ItemStack source, @Nullable Player context) {
				return rebuiltStack;
			}
		};
	}

	@Test
	@DisplayName("a refresher registered later at a higher priority outranks one registered earlier at the default priority")
	void refresh_higherPriorityRegisteredLater_stillWins() {
		ItemStack source = stack(Material.IRON_SWORD);
		ItemStack fromA  = stack(Material.DIAMOND_SWORD);
		ItemStack fromB  = stack(Material.STONE_SWORD);

		ItemRefresher a = claimsEverythingAndReturns(fromA);
		ItemRefresher b = claimsEverythingAndReturns(fromB);

		// A registers first, at the default (plain-overload) priority — as the core always does.
		registry.register(a);
		// B registers afterwards but at a higher priority — as a module refresher that must outrank the core's.
		registry.register(b, 10);

		assertSame(fromB, registry.refresh(source, null));
	}

	@Test
	@DisplayName("two default-priority refreshers keep insertion-order precedence (stable sort)")
	void refresh_twoDefaultPriorityRefreshers_keepInsertionOrder() {
		ItemStack source = stack(Material.IRON_SWORD);
		ItemStack fromA  = stack(Material.DIAMOND_SWORD);
		ItemStack fromB  = stack(Material.STONE_SWORD);

		ItemRefresher a = claimsEverythingAndReturns(fromA);
		ItemRefresher b = claimsEverythingAndReturns(fromB);

		registry.register(a);
		registry.register(b);

		assertSame(fromA, registry.refresh(source, null));
	}

	@Test
	@DisplayName("§1.6(a): a default-priority refresher registered after uniqueItemRefresher stays behind it — the "
			+ "ammunition refresher never overtakes the core's unique-item refresher")
	void refresh_defaultPriorityRegisteredAfterAnotherDefault_staysBehindIt() {
		ItemStack source                   = stack(Material.IRON_SWORD);
		ItemStack fromUniqueItemRefresher   = stack(Material.DIAMOND_SWORD);
		ItemStack fromAmmunitionRefresher   = stack(Material.STONE_SWORD);

		ItemRefresher uniqueItemRefresher = claimsEverythingAndReturns(fromUniqueItemRefresher);
		ItemRefresher ammunitionRefresher = claimsEverythingAndReturns(fromAmmunitionRefresher);

		// core registers uniqueItemRefresher first, at the default priority
		registry.register(uniqueItemRefresher);
		// the weapon module registers ammunitionItemRefresher afterwards, also at the default priority
		registry.register(ammunitionRefresher);

		assertSame(fromUniqueItemRefresher, registry.refresh(source, null));
	}
}
