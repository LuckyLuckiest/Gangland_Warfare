package org.luckyraven.gangland.lootchest.data;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.item.ItemParser;
import org.luckyraven.gangland.lootchest.item.LootItemReference;
import org.luckyraven.gangland.lootchest.support.TestItemParsers;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@code LootTable#generateLoot} and {@code #validate} — the loot roll is the highest-value
 * pure-logic surface in the loot-chest domain (Test Surface, lootchests-signs-waypoints.md).
 *
 * <p>{@code LootTable} does not expose a seedable {@code Random} (the field is
 * {@code private final Random random = new Random()}, excluded from the Lombok
 * {@code @RequiredArgsConstructor} because it has an initializer), so these tests avoid asserting
 * exact rolls and instead lean on single-entry / zero-weight setups where the outcome is
 * deterministic regardless of the RNG seed.
 *
 * <p>{@code createItemFromReference} calls {@code ItemStack#getMaxStackSize()}. On this Spigot API
 * version that resolves through a live server registry ({@code Material#asItemType()} →
 * {@code Registry.ITEM}), which has no backing server in a plain unit test and cannot be faked with
 * plain Mockito either ({@code ItemType}'s own static initializer rejects a mocked instance). Rather
 * than stand up a fake server, {@link TestItemParsers#materialOnly()} hands back a
 * {@code Mockito.spy} of a real {@code ItemStack} with only {@code getMaxStackSize()} stubbed to a
 * known value — every other method (type, amount, equality) runs the real Bukkit code.
 */
class LootTableTest {

	private final ItemParser parser = TestItemParsers.materialOnly();

	@Test
	@DisplayName("empty item list returns empty loot without looping")
	void generateLoot_emptyReferences_returnsEmpty() {
		LootTable table = table(List.of(), 1, 5);

		assertTrue(table.generateLoot("any-tier", parser).isEmpty());
	}

	@Test
	@DisplayName("a single stackable entry fills exactly the fixed item count")
	void generateLoot_singleStackableEntry_fillsFixedCount() {
		LootItemReference stone = ref("stone", "STONE", 1, 1, 10.0, LootItemReference.Rarity.COMMON);
		LootTable         table = table(List.of(stone), 3, 3);

		List<ItemStack> loot = table.generateLoot("any-tier", parser);

		assertEquals(3, loot.size(), "min==max==3 with only one always-eligible entry must yield exactly 3 stacks");
		loot.forEach(item -> assertEquals(org.bukkit.Material.STONE, item.getType()));
	}

	@Test
	@DisplayName("a single non-stackable entry is de-duplicated to exactly one copy")
	void generateLoot_nonStackableEntry_isDeduplicated() {
		// DIAMOND_SWORD has a max stack size of 1.
		LootItemReference sword = ref("sword", "DIAMOND_SWORD", 1, 1, 10.0, LootItemReference.Rarity.COMMON);
		LootTable         table = table(List.of(sword), 5, 5);

		List<ItemStack> loot = table.generateLoot("any-tier", parser);

		assertEquals(1, loot.size(),
		             "the safety-guard loop keeps re-picking the same non-stackable entry and skipping it, so a "
		             + "requested count of 5 collapses to the single de-duplicated copy actually added");
	}

	@Test
	@DisplayName("tierId is accepted but has no effect on which entries are eligible (Observation #16, lootchests-signs-waypoints.md)")
	void generateLoot_tierIdIsIgnored() {
		LootItemReference stone = ref("stone", "STONE", 2, 2, 10.0, LootItemReference.Rarity.COMMON);
		LootTable         table = table(List.of(stone), 1, 1);

		List<ItemStack> lootForNonsenseTier = table.generateLoot("this-tier-does-not-exist-anywhere", parser);
		List<ItemStack> lootForNullTier     = table.generateLoot(null, parser);

		assertEquals(1, lootForNonsenseTier.size());
		assertEquals(1, lootForNullTier.size(),
		             "generateLoot must not throw or filter differently for a null/garbage tierId — the parameter "
		             + "is dead, matching LootTable#generateLoot never reading it");
	}

	@Test
	@DisplayName("rarity filtering that excludes everything falls back to the full entry list rather than an empty roll")
	void generateLoot_allFilteredByRarity_fallsBackToFullList() {
		// A rarity override of 0.0 means random.nextDouble() <= 0.0 almost never passes, so in
		// practice every entry fails the filter and the fallback branch (spawnableItems = itemReferences) fires.
		LootItemReference stone = ref("stone", "STONE", 1, 1, 10.0, LootItemReference.Rarity.LEGENDARY);
		Map<LootItemReference.Rarity, Double> allZero = new EnumMap<>(LootItemReference.Rarity.class);
		for (LootItemReference.Rarity rarity : LootItemReference.Rarity.values()) {
			allZero.put(rarity, 0.0);
		}
		LootTable table = new LootTable("t", "T", List.of(stone), 1, 1, List.of(), allZero);

		List<ItemStack> loot = table.generateLoot("any-tier", parser);

		assertEquals(1, loot.size(), "even with every rarity roll failing, the chest must still produce loot");
	}

	@Test
	@DisplayName("every entry failing to parse produces an empty result rather than an infinite loop")
	void generateLoot_everyEntryUnparseable_returnsEmpty() {
		LootItemReference bogus = ref("bogus", "totally_bogus_type:xyz", 1, 1, 10.0, LootItemReference.Rarity.COMMON);
		LootTable         table = table(List.of(bogus), 2, 2);

		List<ItemStack> loot = table.generateLoot("any-tier", parser);

		assertTrue(loot.isEmpty(), "pickGuaranteedItem also fails when every reference is unparseable");
	}

	@Test
	@DisplayName("validate rejects an empty item list")
	void validate_emptyItems_rejected() {
		LootTable table = table(List.of(), 1, 5);

		assertNotNull(table.validate());
	}

	@Test
	@DisplayName("validate rejects Min_Items below 1")
	void validate_minItemsBelowOne_rejected() {
		LootTable table = table(List.of(ref("s", "STONE", 1, 1, 1.0, LootItemReference.Rarity.COMMON)), 0, 5);

		assertNotNull(table.validate());
	}

	@Test
	@DisplayName("validate rejects Max_Items below Min_Items")
	void validate_maxBelowMin_rejected() {
		LootTable table = table(List.of(ref("s", "STONE", 1, 1, 1.0, LootItemReference.Rarity.COMMON)), 5, 2);

		assertNotNull(table.validate());
	}

	@Test
	@DisplayName("validate accepts a well-formed table")
	void validate_wellFormed_returnsNull() {
		LootTable table = table(List.of(ref("s", "STONE", 1, 1, 1.0, LootItemReference.Rarity.COMMON)), 1, 5);

		assertNull(table.validate());
	}

	private static LootTable table(List<LootItemReference> items, int minItems, int maxItems) {
		return new LootTable("test-table", "Test Table", items, minItems, maxItems, List.of(), Map.of());
	}

	private static LootItemReference ref(String id, String itemString, int minAmount, int maxAmount, double weight,
	                                     LootItemReference.Rarity rarity) {
		return LootItemReference.builder()
		                        .id(id)
		                        .itemString(itemString)
		                        .minAmount(minAmount)
		                        .maxAmount(maxAmount)
		                        .weight(weight)
		                        .rarity(rarity)
		                        .build();
	}

}
