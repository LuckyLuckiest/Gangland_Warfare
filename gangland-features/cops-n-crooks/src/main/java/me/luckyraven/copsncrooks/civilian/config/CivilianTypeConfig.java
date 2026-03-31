package me.luckyraven.copsncrooks.civilian.config;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Full configuration for one civilian NPC type (e.g. "pedestrian", "gang_member", "trader").
 *
 * @param typeId unique key from entity_marker.yml
 * @param displayName raw color-coded name string (translated on spawn via ChatUtil)
 * @param entityType Citizens NPC body entity type
 * @param health max health in half-hearts
 * @param hostile if true the NPC attacks nearby players
 * @param wearables armor slot configuration (raw ItemParser strings)
 * @param itemPool general items randomly given on spawn (raw ItemParser strings)
 * @param weaponNamePool gangland weapon names for random selection (hostile types)
 * @param weaponPool vanilla material weapon ItemStacks for random selection (fallback)
 * @param drops death-drop configuration
 * @param ai per-type AI behavior settings
 * @param inventory optional trader inventory config; null for non-trader types
 */
public record CivilianTypeConfig(
		String typeId,
		String displayName,
		EntityType entityType,
		double health,
		boolean hostile,
		CivilianWearableConfig wearables,
		List<String> itemPool,
		List<String> weaponNamePool,
		List<ItemStack> weaponPool,
		CivilianDropConfig drops,
		CivilianAIBehaviorConfig ai,
		@Nullable CivilianInventoryConfig inventory
) {
}
