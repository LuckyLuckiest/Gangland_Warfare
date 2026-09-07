package org.luckyraven.gangland.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-logic unit tests for {@link ItemSerializerRegistry} and the shipped {@link MaterialItemSerializer} catch-all
 * (Test Surface, items-unique.md: "priority order, empty-extraction fallthrough, lowercasing, null stack").
 *
 * <p>Pins Observation #20 (items-unique.md): the extracted value is lowercased unconditionally, so a registry key
 * containing uppercase characters cannot round-trip back through {@code ItemParser}.
 */
@DisplayName("ItemSerializerRegistry — ordered predicate/serializer chain")
class ItemSerializerRegistryTest {

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
	@DisplayName("null stack short-circuits to null before any predicate runs")
	void serialize_nullStack_returnsNull() {
		registry.register(stack -> true, fixedSerializer(ItemKind.MATERIAL, "anything"));

		assertNull(registry.serialize(null));
	}

	@Test
	@DisplayName("the first matching predicate in registration order wins, even if a later one would also match")
	void serialize_firstRegisteredMatchWins() {
		ItemStack stack = new ItemStack(Material.IRON_SWORD);

		// Both predicates match an IRON_SWORD; UNIQUE is registered first, so it must win — mirrors ItemConfig's
		// UNIQUE-before-WEAPON registration order.
		registry.register(materialIs(Material.IRON_SWORD), fixedSerializer(ItemKind.UNIQUE, "phone"));
		registry.register(materialIs(Material.IRON_SWORD), fixedSerializer(ItemKind.WEAPON, "ak47"));

		assertEquals("unique:phone", registry.serialize(stack));
	}

	@Test
	@DisplayName("a matched predicate whose serializer extracts null/empty falls through to the next entry")
	void serialize_emptyExtraction_fallsThroughToNextEntry() {
		ItemStack stack = new ItemStack(Material.IRON_SWORD);

		// A weapon-tagged stack whose tag came back empty degrades to the material catch-all (edge case documented
		// under W4 in items-unique.md).
		registry.register(materialIs(Material.IRON_SWORD), fixedSerializer(ItemKind.WEAPON, ""));
		registry.register(stack2 -> true, new MaterialItemSerializer());

		assertEquals("material:iron_sword", registry.serialize(stack));
	}

	@Test
	@DisplayName("a matched predicate whose serializer extracts null (not just empty) also falls through")
	void serialize_nullExtraction_fallsThroughToNextEntry() {
		ItemStack stack = new ItemStack(Material.IRON_SWORD);

		registry.register(materialIs(Material.IRON_SWORD), fixedSerializer(ItemKind.WEAPON, null));
		registry.register(stack2 -> true, new MaterialItemSerializer());

		assertEquals("material:iron_sword", registry.serialize(stack));
	}

	@Test
	@DisplayName("no predicate matching returns null")
	void serialize_noMatch_returnsNull() {
		registry.register(materialIs(Material.DIAMOND), fixedSerializer(ItemKind.MATERIAL, "diamond"));

		assertNull(registry.serialize(new ItemStack(Material.STONE)));
	}

	@Test
	@DisplayName("Observation #20 (items-unique.md): the extracted value is always lowercased")
	void serialize_alwaysLowercasesTheExtractedValue() {
		registry.register(stack -> true, fixedSerializer(ItemKind.UNIQUE, "EpicKey"));

		assertEquals("unique:epickey", registry.serialize(new ItemStack(Material.STONE)),
		             "a registry key with uppercase characters cannot round-trip through ItemParser afterwards");
	}

	// -------------------------------------------------------------- MaterialItemSerializer, the shipped catch-all

	@Test
	@DisplayName("MaterialItemSerializer.extract lowercases the material name")
	void materialSerializer_extract_lowercasesMaterialName() {
		MaterialItemSerializer serializer = new MaterialItemSerializer();

		assertEquals("iron_sword", serializer.extract(new ItemStack(Material.IRON_SWORD)));
		assertEquals(ItemKind.MATERIAL, serializer.kind());
	}

	@Test
	@DisplayName("MaterialItemSerializer.extract returns null for AIR and for a null stack")
	void materialSerializer_extract_nullForAirOrNullStack() {
		MaterialItemSerializer serializer = new MaterialItemSerializer();

		assertNull(serializer.extract(new ItemStack(Material.AIR)));
		assertNull(serializer.extract(null));
	}

}
