package me.luckyraven.inventory.handler;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Carries all per-slot data needed to build a {@link me.luckyraven.inventory.part.Slot} from a YAML configuration
 * section.
 */
public record SlotContext(
		@Nullable ConfigurationSection eventSection,
		@Nullable ConfigurationSection rightClickSection,
		int slotLoc,
		String item,
		@Nullable String itemName,
		Map<String, Object> data,
		List<String> lore,
		boolean enchanted,
		boolean draggable) {
}
