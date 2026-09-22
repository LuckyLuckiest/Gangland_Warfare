package org.luckyraven.gangland.lootchest.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves {@code LootItemReference#generateAmount}/{@code #getEffectiveWeight} and the
 * {@code Rarity} enum's weight/spawn-chance maths (Test Surface, lootchests-signs-waypoints.md:
 * "LootItemReference#generateAmount and Rarity#calculateEffectiveWeight").
 */
class LootItemReferenceTest {

	@Test
	@DisplayName("generateAmount with maxAmount <= minAmount returns minAmount, floored at 1")
	void generateAmount_maxNotGreaterThanMin_returnsMinFlooredAtOne() {
		LootItemReference fixed = ref(5, 5);
		assertEquals(5, fixed.generateAmount());

		LootItemReference invertedRange = ref(5, 3);
		assertEquals(5, invertedRange.generateAmount(), "an inverted range still floors on minAmount, not maxAmount");

		LootItemReference zeroMin = ref(0, 0);
		assertEquals(1, zeroMin.generateAmount(), "minAmount of 0 is floored to 1 so a chest never yields a 0-stack");
	}

	@Test
	@DisplayName("generateAmount with maxAmount > minAmount stays within the inclusive range over many rolls")
	void generateAmount_range_staysWithinInclusiveBounds() {
		LootItemReference ranged = ref(2, 6);

		for (int i = 0; i < 500; i++) {
			int amount = ranged.generateAmount();
			assertTrue(amount >= 2 && amount <= 6, "rolled " + amount + " outside [2,6]");
		}
	}

	@Test
	@DisplayName("getEffectiveWeight multiplies the base weight by the rarity's spawn multiplier")
	void getEffectiveWeight_multipliesByRaritySpawnMultiplier() {
		LootItemReference epic = LootItemReference.builder()
		                                          .id("x")
		                                          .itemString("STONE")
		                                          .minAmount(1)
		                                          .maxAmount(1)
		                                          .weight(10.0)
		                                          .rarity(LootItemReference.Rarity.EPIC)
		                                          .build();

		assertEquals(10.0 * 0.15, epic.getEffectiveWeight(), 1e-9);
	}

	@Test
	@DisplayName("Rarity spawn multipliers match the shipped table (common..legendary)")
	void rarity_spawnMultipliers_matchShippedTable() {
		assertEquals(1.0, LootItemReference.Rarity.COMMON.getSpawnMultiplier());
		assertEquals(0.7, LootItemReference.Rarity.UNCOMMON.getSpawnMultiplier());
		assertEquals(0.4, LootItemReference.Rarity.RARE.getSpawnMultiplier());
		assertEquals(0.15, LootItemReference.Rarity.EPIC.getSpawnMultiplier());
		assertEquals(0.05, LootItemReference.Rarity.LEGENDARY.getSpawnMultiplier());
	}

	@Test
	@DisplayName("calculateEffectiveWeight is a pure multiplication with no floor/ceiling")
	void calculateEffectiveWeight_isPureMultiplication() {
		assertEquals(0.0, LootItemReference.Rarity.COMMON.calculateEffectiveWeight(0.0));
		assertEquals(4.0, LootItemReference.Rarity.COMMON.calculateEffectiveWeight(4.0));
		assertEquals(0.6, LootItemReference.Rarity.RARE.calculateEffectiveWeight(1.5), 1e-9);
	}

	private static LootItemReference ref(int minAmount, int maxAmount) {
		return LootItemReference.builder()
		                        .id("x")
		                        .itemString("STONE")
		                        .minAmount(minAmount)
		                        .maxAmount(maxAmount)
		                        .weight(1.0)
		                        .rarity(LootItemReference.Rarity.COMMON)
		                        .build();
	}

}
