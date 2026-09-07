package org.luckyraven.gangland.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the priority overload added for the module split (gadget T3, README decision "shared seams fixed"): a
 * module always registers its serializer <em>after</em> the core's catch-all, so the catch-all must opt out of
 * plain insertion order via {@link ItemSerializerRegistry#CATCH_ALL_PRIORITY} while everything registered at the
 * default priority keeps today's insertion-order precedence (stable sort).
 */
@DisplayName("ItemSerializerRegistry — CATCH_ALL_PRIORITY overload")
class ItemSerializerRegistryPriorityTest {

	private ItemSerializerRegistry registry;

	@BeforeEach
	void setUp() {
		registry = new ItemSerializerRegistry();
	}

	private static Predicate<ItemStack> materialIs(Material material) {
		return stack -> stack != null && stack.getType() == material;
	}

	private static ItemSerializer fixedSerializer(ItemKind kind, @Nullable String value) {
		return new ItemSerializer() {
			@Override
			public ItemKind kind() {
				return kind;
			}

			@Override
			public String extract(ItemStack stack) {
				return value;
			}
		};
	}

	@Test
	@DisplayName("a serializer registered after the catch-all still wins, because the catch-all sorts last")
	void serialize_catchAllRegisteredFirst_stillLosesToALaterSpecificSerializer() {
		ItemStack stack = new ItemStack(Material.IRON_SWORD);

		// The catch-all registers first (as the core always does), then a module registers its specific
		// serializer afterwards at the default priority — it must still win.
		registry.register(stack2 -> true, fixedSerializer(ItemKind.MATERIAL, "iron_sword"),
		                  ItemSerializerRegistry.CATCH_ALL_PRIORITY);
		registry.register(materialIs(Material.IRON_SWORD), fixedSerializer(ItemKind.CAR, "sports_car"));

		assertEquals("car:sports_car", registry.serialize(stack));
	}

	@Test
	@DisplayName("two default-priority serializers keep insertion-order precedence (stable sort)")
	void serialize_twoDefaultPrioritySerializers_keepInsertionOrder() {
		ItemStack stack = new ItemStack(Material.IRON_SWORD);

		registry.register(materialIs(Material.IRON_SWORD), fixedSerializer(ItemKind.UNIQUE, "phone"));
		registry.register(materialIs(Material.IRON_SWORD), fixedSerializer(ItemKind.WEAPON, "ak47"));

		assertEquals("unique:phone", registry.serialize(stack));
	}
}
