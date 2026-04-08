package me.luckyraven.lootchest.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.item.ItemParser;
import me.luckyraven.lootchest.item.LootItemReference;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents a collection of loot item references with weighted random selection and rarity-based spawn chances. Actual
 * ItemStacks are produced by delegating to the global {@link ItemParser} — this module never parses items itself.
 */
@Getter
@RequiredArgsConstructor
public class LootTable {

	private final String                  id;
	private final String                  displayName;
	private final List<LootItemReference> itemReferences;
	private final int                     minItems;
	private final int                     maxItems;
	private final List<String>            allowedTiers;

	// Rarity spawn rates can be overridden per table
	private final Map<LootItemReference.Rarity, Double> rarityOverrides;

	private final Random random = new Random();

	/**
	 * Generates random loot based on weights and rarity spawn chances.
	 *
	 * @param tierId the tier of the chest being opened
	 * @param parser the shared item parser used to resolve item strings into ItemStacks
	 *
	 * @return list of generated ItemStacks
	 */
	public List<ItemStack> generateLoot(String tierId, ItemParser parser) {
		// Filter items by tier requirement
		List<LootItemReference> availableItems = filterByTier(tierId);
		if (availableItems.isEmpty()) return Collections.emptyList();

		// Apply rarity filter - items might not spawn based on their rarity
		List<LootItemReference> spawnableItems = filterByRarity(availableItems);
		if (spawnableItems.isEmpty()) return Collections.emptyList();

		int itemCount = random.nextInt(minItems, maxItems + 1);

		List<ItemStack> result = new ArrayList<>();

		// Calculate total effective weight
		double totalWeight = spawnableItems.stream().mapToDouble(LootItemReference::getEffectiveWeight).sum();

		// Prevent duplicate non-stackable items (weapons, cars, wearables, unique items — anything with maxStackSize 1)
		Set<String> selectedNonStackableIds = new HashSet<>();

		int safetyGuard = itemCount * 10;

		while (result.size() < itemCount && safetyGuard-- > 0) {
			LootItemReference selected = selectWeightedRandom(spawnableItems, totalWeight);
			if (selected == null) continue;

			ItemStack item = createItemFromReference(selected, parser);
			if (item == null) continue;

			boolean stackable = item.getMaxStackSize() > 1;

			if (!stackable && selectedNonStackableIds.contains(selected.getId())) {
				continue;
			}

			result.add(item);

			if (!stackable) {
				selectedNonStackableIds.add(selected.getId());
			}
		}

		return result;
	}

	/**
	 * Filters items based on tier requirements
	 */
	private List<LootItemReference> filterByTier(String tierId) {
		return itemReferences.stream().filter(item -> {
			if (item.getTierRequirement() == null) return true;
			if (allowedTiers.isEmpty()) return true;

			int currentTierIndex  = allowedTiers.indexOf(tierId);
			int requiredTierIndex = allowedTiers.indexOf(item.getTierRequirement());

			return currentTierIndex >= requiredTierIndex;
		}).toList();
	}

	/**
	 * Filters items based on rarity spawn chance Each item has a chance to be excluded based on its rarity
	 */
	private List<LootItemReference> filterByRarity(List<LootItemReference> items) {
		return items.stream().filter(item -> {
			double spawnChance = getSpawnChance(item.getRarity());

			return random.nextDouble() <= spawnChance;
		}).toList();
	}

	/**
	 * Gets the spawn chance for a rarity, considering overrides
	 */
	private double getSpawnChance(LootItemReference.Rarity rarity) {
		if (rarityOverrides != null && rarityOverrides.containsKey(rarity)) {
			return rarityOverrides.get(rarity);
		}

		return rarity.getSpawnMultiplier();
	}

	/**
	 * Selects a random item based on effective weights
	 */
	private LootItemReference selectWeightedRandom(List<LootItemReference> items, double totalWeight) {
		double randomValue      = random.nextDouble() * totalWeight;
		double cumulativeWeight = 0;

		for (LootItemReference item : items) {
			cumulativeWeight += item.getEffectiveWeight();

			if (randomValue > cumulativeWeight) continue;

			return item;
		}

		return items.isEmpty() ? null : items.getLast();
	}

	/**
	 * Parses the reference's item string through the shared parser and applies the rolled stack amount.
	 */
	private ItemStack createItemFromReference(LootItemReference reference, ItemParser parser) {
		ItemStack item = parser.parse(reference.getItemString());

		if (item == null) return null;

		int rolled = reference.generateAmount();
		int capped = Math.min(rolled, item.getMaxStackSize());
		int amount = Math.max(1, capped);

		item.setAmount(amount);

		return item;
	}

}
