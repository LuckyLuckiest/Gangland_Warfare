package org.luckyraven.gangland.inventory.handler;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.inventory.part.Slot;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Carries all per-slot data needed to build a {@link Slot} from a YAML configuration section.
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
		boolean draggable,
		@Nullable Function<String, ItemStack> itemResolver) {
}
