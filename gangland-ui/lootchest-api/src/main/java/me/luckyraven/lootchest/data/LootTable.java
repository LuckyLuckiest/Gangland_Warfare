package me.luckyraven.lootchest.data;

import lombok.CustomLog;
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
@CustomLog
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
		// Defensive: empty tables should have been rejected by LootChestLoader. If one slips through,
		// there is nothing to roll against — return empty rather than looping forever.
		if (itemReferences.isEmpty()) return Collections.emptyList();

		// Apply drop-chance filter - items might not spawn based on their rarity roll.
		// If every item fails its rarity roll, fall back to the full entry list so the chest still
		// produces loot (guaranteed ≥1 item contract).
		List<LootItemReference> spawnableItems = filterByRarity(itemReferences);
		if (spawnableItems.isEmpty()) spawnableItems = itemReferences;

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

		// Safety-guard exhaustion (all picks collided with non-stackable dedup, or parser kept returning null)
		// can leave result empty. Force one guaranteed pick against the full entry list.
		if (result.isEmpty()) {
			ItemStack guaranteed = pickGuaranteedItem(parser);
			if (guaranteed != null) {
				result.add(guaranteed);
			} else {
				log.warn("Loot table '{}' produced zero items — every entry failed to parse", id);
			}
		}

		return result;
	}

	/**
	 * Returns a human-readable problem description if this table cannot produce loot, or null if it is valid. Called by
	 * the loader at startup so broken tables can be logged and skipped.
	 */
	public String validate() {
		if (itemReferences == null || itemReferences.isEmpty()) {
			return "has no items defined under 'Items'";
		}

		if (minItems < 1) {
			return "Min_Items must be >= 1 (was " + minItems + ")";
		}

		if (maxItems < minItems) {
			return "Max_Items (" + maxItems + ") must be >= Min_Items (" + minItems + ")";
		}

		return null;
	}

	/**
	 * Final fallback when weighted selection yields nothing. Walks the full entry list in weight order, returning the
	 * first one the parser can resolve. Returns null only if every Item: string is broken.
	 */
	private ItemStack pickGuaranteedItem(ItemParser parser) {
		double totalWeight = itemReferences.stream().mapToDouble(LootItemReference::getEffectiveWeight).sum();

		for (int attempt = 0; attempt < itemReferences.size(); attempt++) {
			LootItemReference selected = selectWeightedRandom(itemReferences, totalWeight);
			if (selected == null) continue;

			ItemStack item = createItemFromReference(selected, parser);
			if (item != null) return item;
		}

		return null;
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
