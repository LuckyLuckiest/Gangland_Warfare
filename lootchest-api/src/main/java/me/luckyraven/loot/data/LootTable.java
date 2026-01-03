package me.luckyraven.loot.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.loot.item.LootItemProvider;
import me.luckyraven.loot.item.LootItemReference;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents a collection of loot item references with weighted random selection and rarity-based spawn chances
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
	 * Generates random loot based on weights and rarity spawn chances
	 *
	 * @param tierId The tier of the chest being opened
	 * @param provider The provider to fetch actual items from configurations
	 *
	 * @return List of generated ItemStacks
	 */
	public List<ItemStack> generateLoot(String tierId, LootItemProvider provider) {
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

		// Prevent duplicate weapons/unique items
		Set<String> selectedIds = new HashSet<>();

		for (int i = 0; i < itemCount; i++) {
			LootItemReference selected = selectWeightedRandom(spawnableItems, totalWeight);
			if (selected == null) continue;

			// For weapons and unique items, don't allow duplicates
			if ((selected.getCategory() == LootItemReference.LootCategory.WEAPON ||
				 selected.getCategory() == LootItemReference.LootCategory.UNIQUE) &&
				selectedIds.contains(selected.getReferenceId())) {
				continue;
			}

			ItemStack item = createItemFromReference(selected, provider);

			if (item == null) continue;

			result.add(item);
			selectedIds.add(selected.getReferenceId());
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
	 * Creates an actual ItemStack from a reference using the provider
	 */
	private ItemStack createItemFromReference(LootItemReference reference, LootItemProvider provider) {
		int amount = reference.generateAmount();

		return switch (reference.getCategory()) {
			case WEAPON -> provider.getWeapon(reference.getReferenceId());
			case AMMO -> provider.getAmmunition(reference.getReferenceId(), amount);
			case UNIQUE -> provider.getUniqueItem(reference.getReferenceId());
			case REPAIR -> provider.getRepairItem(reference.getReferenceId(), amount);
			case CONSUMABLE -> provider.getConsumable(reference.getReferenceId(), amount);
			case MATERIAL -> provider.getMaterial(reference.getReferenceId(), amount);
			case MISC -> provider.getMiscItem(reference.getReferenceId(), amount);
		};
	}

}
