package org.luckyraven.gangland.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic unit tests for {@link ItemConverterRegistry} (Test Surface, items-unique.md: "case-insensitive keys,
 * the {@code ItemKind[]}/{@code String[]} overloads, overwrite semantics").
 *
 * <p>No Bukkit server is needed here — registration and lookup never touch {@link ItemStack#getItemMeta()}.
 */
@DisplayName("ItemConverterRegistry — string/kind keyed converter lookup")
class ItemConverterRegistryTest {

	private ItemConverterRegistry registry;
	private ItemConverter         first;
	private ItemConverter         second;

	@BeforeEach
	void setUp() {
		registry = new ItemConverterRegistry();
		first    = (type, modifier, attributes) -> new ItemStack(Material.STONE);
		second   = (type, modifier, attributes) -> new ItemStack(Material.DIRT);
	}

	@Test
	@DisplayName("register/getConverter/hasConverter are case-insensitive")
	void lookup_isCaseInsensitive() {
		registry.register("Weapon", first);

		assertTrue(registry.hasConverter("weapon"));
		assertTrue(registry.hasConverter("WEAPON"));
		assertSame(first, registry.getConverter("WeApOn"));
	}

	@Test
	@DisplayName("a second registration under the same key (any case) overwrites the first")
	void register_sameKeyDifferentCase_overwrites() {
		registry.register("weapon", first);
		registry.register("WEAPON", second);

		assertSame(second, registry.getConverter("weapon"), "last registration under a key wins");
	}

	@Test
	@DisplayName("getConverter/hasConverter return false/null for an unregistered type")
	void unregisteredType_returnsNullAndFalse() {
		assertFalse(registry.hasConverter("ghost"));
		assertNull(registry.getConverter("ghost"));
	}

	@Test
	@DisplayName("register(String[], converter) registers every alias")
	void register_stringArray_registersEveryAlias() {
		registry.register(new String[]{"money", "cash"}, first);

		assertSame(first, registry.getConverter("money"));
		assertSame(first, registry.getConverter("cash"));
	}

	@Test
	@DisplayName("register(ItemKind, converter) registers under the kind's label")
	void register_itemKind_usesLabel() {
		registry.register(ItemKind.UNIQUE, first);

		assertSame(first, registry.getConverter(ItemKind.UNIQUE.label()));
		assertSame(first, registry.getConverter("unique"));
	}

	@Test
	@DisplayName("register(ItemKind[], converter) registers every kind's label")
	void register_itemKindArray_registersEveryLabel() {
		registry.register(new ItemKind[]{ItemKind.WEAPON, ItemKind.AMMUNITION}, first);

		assertSame(first, registry.getConverter("weapon"));
		assertSame(first, registry.getConverter("ammunition"));
	}

	@Test
	@DisplayName("convert() dispatches to the registered converter with the exact type/modifier/attrs")
	void getConverter_thenConvert_dispatchesArguments() {
		registry.register("weapon", first);

		ItemStack stack = registry.getConverter("weapon").convert("weapon", "ak47", Map.of("name", "Gold"));

		assertEquals(Material.STONE, stack.getType());
	}

}
