package me.luckyraven.item.converter;

import lombok.RequiredArgsConstructor;
import me.luckyraven.gadget.repair.RepairManager;
import me.luckyraven.gadget.repair.material.RepairMaterial;
import me.luckyraven.item.ItemAttributes;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Converts a {@code repair:<name>} item string into a fully-built {@link RepairMaterial} ItemStack.
 *
 * <p>Usage examples:
 * <pre>
 *   repair:cleaning_kit
 * </pre>
 *
 * <p>The {@code modifier} is the repair material registry key (the name defined in {@code repair.yml}).
 * Attributes supported by {@link me.luckyraven.item.ItemAttributes#applyAttributes} (name, lore) are applied after the
 * item is built, allowing per-use overrides without altering the registered repair definition.
 */
@RequiredArgsConstructor
public class RepairConverter extends ItemAttributes {

	private final RepairManager repairManager;

	@Override
	public ItemStack convert(String type, String modifier, Map<String, String> attributes) {
		if (modifier == null || modifier.isBlank()) return null;

		RepairMaterial repairMaterial = repairManager.getMaterialManager().getMaterial(modifier.trim());

		if (repairMaterial == null) return null;

		ItemStack itemStack = repairMaterial.buildItem();

		applyAttributes(itemStack, attributes);

		return itemStack;
	}
}
