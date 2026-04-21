package me.luckyraven.inventory;

import com.cryptomorin.xseries.XEnchantment;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.Placeholder;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.color.ColorUtil;
import me.luckyraven.core.color.MaterialType;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.inventory.condition.ConditionalSlotData;
import me.luckyraven.inventory.multi.ItemSourceEntry;
import me.luckyraven.inventory.multi.ListEntry;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.multi.MultiInventoryCreation;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.part.Slot;
import me.luckyraven.inventory.util.InventoryUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public record InventoryBuilder(InventoryData inventoryData, String permission) {

	private static String substituteEntry(String template, Map<String, String> entry) {
		if (template == null || template.isEmpty() || entry.isEmpty()) return template;
		String result = template;
		for (Map.Entry<String, String> e : entry.entrySet()) {
			result = result.replace("%" + e.getKey() + "%", e.getValue());
		}
		return result;
	}

	public InventoryHandler createInventory(JavaPlugin plugin, Placeholder placeholder, Player player, Fill fill,
	                                        Fill line, ConditionEvaluator evaluator, InventoryOpener inventoryOpener) {
		// create a new instance
		String     displayName = inventoryData.getDisplayName();
		int        size        = inventoryData.getSize();
		List<Slot> slots       = inventoryData.getSlots();

		// it is special when there is a click event
		var title   = placeholder.convert(player, displayName);
		var handler = new InventoryHandler(plugin, title, size, player);

		for (Slot slot : slots) {
			// get conditional result for this slot
			var result = slot.getConditionalResult(player, evaluator);

			int         usedSlot = slot.getSlot();
			ItemBuilder item     = result.item();
			if (item == null) continue;

			// handles color tag
			String   colorTag = "color";
			Material type     = item.getType();
			if (item.hasNBTTag(colorTag)) {
				// special treatment for colored data
				String value = placeholder.convert(player, item.getStringTagData(colorTag));

				MaterialType material = MaterialType.WOOL;
				for (MaterialType materialType : MaterialType.values()) {
					if (!type.name().contains(materialType.name())) continue;

					material = materialType;
					break;
				}

				type = ColorUtil.getMaterialByColor(value, material.name());
			}

			ItemBuilder newItem = new ItemBuilder(type);

			// handles head data
			String headTag = "head";
			if (item.hasNBTTag(headTag)) {
				String value = placeholder.convert(player, item.getStringTagData(headTag));
				newItem.customHead(value);
			}

			String itemDisplayName = placeholder.convert(player, item.getDisplayName());
			newItem.setDisplayName(itemDisplayName);

			List<String> lore = item.getLore()
					.stream().map(s -> placeholder.convert(player, s)).toList();
			newItem.setLore(lore);

			if (!item.getEnchantments().isEmpty()) {
				newItem.addEnchantment(XEnchantment.UNBREAKING.get(), 1)
				       .addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
			}

			// handle click actions, including anvil inventories
			var clickAction      = result.clickAction();
			var rightClickAction = (TriConsumer<Player, InventoryHandler, ItemBuilder>) null;

			if (result.rawClickAction() instanceof ConditionalSlotData.AnvilAction anvilAction) {
				clickAction = (p, inv, builder) -> openAnvilInventory(plugin, placeholder, p, anvilAction);
			} else if (result.rawClickAction() != null) {
				// Wrap the action to pass the opener
				ConditionalSlotData.ClickAction rawAction = result.rawClickAction();
				clickAction = (p, inv, builder) -> rawAction.execute(p, inv, builder, inventoryOpener);
			}

			// Handle right-click action
			if (result.rawRightClickAction() instanceof ConditionalSlotData.AnvilAction anvilAction) {
				rightClickAction = (p, inv, builder) -> openAnvilInventory(plugin, placeholder, p, anvilAction);
			} else if (result.rawRightClickAction() != null) {
				ConditionalSlotData.ClickAction rawRightAction = result.rawRightClickAction();
				rightClickAction = (p, inv, builder) -> rawRightAction.execute(p, inv, builder, inventoryOpener);
			} else if (slot.getRightClickSlot() != null) {
				rightClickAction = slot.getRightClickSlot();
			}

			// Use the overload that supports both left and right click
			if (rightClickAction != null) {
				handler.setItem(usedSlot, newItem, result.draggable(), clickAction, rightClickAction);
			} else {
				handler.setItem(usedSlot, newItem, result.draggable(), clickAction);
			}
		}

		List<Integer> verticalLine   = inventoryData.getVerticalLine();
		List<Integer> horizontalLine = inventoryData.getHorizontalLine();

		if (!verticalLine.isEmpty()) {
			for (int l : verticalLine) {
				InventoryUtil.verticalLine(handler, line, l);
			}
		}

		if (!horizontalLine.isEmpty()) {
			for (int l : horizontalLine) {
				InventoryUtil.horizontalLine(handler, line, l);
			}
		}

		if (inventoryData.isBorder()) {
			InventoryUtil.createBoarder(handler, fill);
		} else if (inventoryData.isFill()) {
			InventoryUtil.fillInventory(handler, fill);
		}

		return handler;
	}

	public MultiInventory createMultiInventory(JavaPlugin plugin, Placeholder placeholder, Player player,
	                                           List<ItemSourceEntry> entries, ButtonTags buttonTags, Fill fill) {
		if (!inventoryData.isMultiInventory()) {
			throw new IllegalStateException("This inventory is not configured as a multi-inventory");
		}

		String title = placeholder.convert(player, inventoryData.getDisplayName());

		MultiInventory multiInventory;

		// Prepare static items if any
		Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItemsMap = null;

		if (inventoryData.getStaticItems() != null && !inventoryData.getStaticItems().isEmpty()) {
			staticItemsMap = new HashMap<>();

			for (Map.Entry<Integer, Slot> entry : inventoryData.getStaticItems().entrySet()) {
				Slot        slot = entry.getValue();
				ItemBuilder item = slot.getItem();
				if (item == null) continue;

				// Process placeholders and create item
				ItemStack processedItem = processItemStack(item, placeholder, player);

				if (slot.isClickable() && slot.getClickableSlot() != null) {
					staticItemsMap.put(processedItem, slot.getClickableSlot());
				} else {
					staticItemsMap.put(processedItem, (p, inv, builder) -> { });
				}
			}
		}

		boolean staticItems = staticItemsMap != null && !staticItemsMap.isEmpty();

		List<ListEntry> listEntries = renderListEntries(entries, placeholder, player);

		multiInventory = MultiInventoryCreation.dynamicMultiInventory(plugin, player, listEntries, title, staticItems,
		                                                              inventoryData.isBorder(), fill, buttonTags,
		                                                              staticItemsMap);

		return multiInventory;
	}

	private List<ListEntry> renderListEntries(List<ItemSourceEntry> entries, Placeholder placeholder, Player player) {
		Slot   template        = inventoryData.getItemTemplate();
		String commandTemplate = inventoryData.getItemTemplateCommand();

		if (template == null || template.getItem() == null) return List.of();

		List<ListEntry> out = new ArrayList<>(entries.size());
		for (ItemSourceEntry entry : entries) {
			out.add(renderListEntry(template, commandTemplate, entry.placeholders(), placeholder, player));
		}
		return out;
	}

	private ListEntry renderListEntry(Slot template, @org.jetbrains.annotations.Nullable String commandTemplate,
	                                  Map<String, String> entry, Placeholder placeholder, Player player) {
		ItemBuilder source = template.getItem();
		Material    type   = source.getType();

		String colorTag = "color";
		if (source.hasNBTTag(colorTag)) {
			String raw   = substituteEntry(source.getStringTagData(colorTag), entry);
			String value = placeholder.convert(player, raw);

			MaterialType material = MaterialType.WOOL;
			for (MaterialType m : MaterialType.values()) {
				if (!type.name().contains(m.name())) continue;
				material = m;
				break;
			}

			type = ColorUtil.getMaterialByColor(value, material.name());
		}

		ItemBuilder fresh = new ItemBuilder(type);

		String headTag = "head";
		String dataTag = "data";
		if (source.hasNBTTag(headTag) || source.hasNBTTag(dataTag)) {
			String raw = source.hasNBTTag(headTag) ?
			             source.getStringTagData(headTag) :
			             source.getStringTagData(dataTag);
			String value = placeholder.convert(player, substituteEntry(raw, entry));
			fresh.customHead(value);
		}

		String displayName = placeholder.convert(player, substituteEntry(source.getDisplayName(), entry));
		fresh.setDisplayName(displayName);

		List<String> lore = source.getLore()
				.stream()
				.map(s -> placeholder.convert(player, substituteEntry(s, entry)))
				.toList();
		fresh.setLore(lore);

		if (!source.getEnchantments().isEmpty()) {
			fresh.addEnchantment(XEnchantment.UNBREAKING.get(), 1)
			     .addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
		}

		ItemStack stack = fresh.build();

		TriConsumer<Player, InventoryHandler, ItemBuilder> onClick = null;
		if (commandTemplate != null && !commandTemplate.isEmpty()) {
			String substituted = placeholder.convert(player, substituteEntry(commandTemplate, entry));
			String cleaned     = substituted.startsWith("/") ? substituted.substring(1) : substituted;
			onClick = (p, inv, builder) -> p.performCommand(cleaned);
		}

		return new ListEntry(stack, onClick);
	}

	private void openAnvilInventory(JavaPlugin plugin, Placeholder placeholder, Player player,
	                                ConditionalSlotData.AnvilAction anvilAction) {
		String title          = placeholder.convert(player, anvilAction.title());
		String text           = placeholder.convert(player, anvilAction.text());
		String successCommand = anvilAction.successCommand();

		new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
			if (slot != AnvilGUI.Slot.OUTPUT) {
				return Collections.emptyList();
			}

			String output = stateSnapshot.getText();

			if (successCommand != null) {
				// Replace %gangland_anvil_output% with the actual output
				String command = successCommand.replace("%gangland_anvil_output%", output);
				command = placeholder.convert(player, command);

				if (command.startsWith("/")) command = command.substring(1);
				stateSnapshot.getPlayer().performCommand(command);
			}

			return List.of(AnvilGUI.ResponseAction.close());
		}).text(text).title(title).plugin(plugin).open(player);
	}

	private ItemStack processItemStack(ItemBuilder item, Placeholder placeholder, Player player) {
		String   colorTag = "color";
		Material type     = item.getType();

		if (item.hasNBTTag(colorTag)) {
			String       value    = placeholder.convert(player, item.getStringTagData(colorTag));
			MaterialType material = MaterialType.WOOL;

			for (MaterialType materialType : MaterialType.values()) {
				if (!type.name().contains(materialType.name())) continue;
				material = materialType;
				break;
			}

			type = ColorUtil.getMaterialByColor(value, material.name());
		}

		ItemBuilder newItem = new ItemBuilder(type);

		String headTag = "head";
		String dataTag = "data";
		if (item.hasNBTTag(headTag) || item.hasNBTTag(dataTag)) {
			String head  = item.hasNBTTag(headTag) ? item.getStringTagData(headTag) : item.getStringTagData(dataTag);
			String value = placeholder.convert(player, head);
			newItem.customHead(value);
		}

		String itemDisplayName = placeholder.convert(player, item.getDisplayName());
		newItem.setDisplayName(itemDisplayName);

		List<String> lore = item.getLore()
				.stream().map(s -> placeholder.convert(player, s)).toList();
		newItem.setLore(lore);

		if (!item.getEnchantments().isEmpty()) {
			newItem.addEnchantment(XEnchantment.UNBREAKING.get(), 1)
			       .addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
		}

		return newItem.build();
	}

}
