package org.luckyraven.gangland.shop.io;

import lombok.CustomLog;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.shop.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CustomLog
public final class ShopYamlWriter {

	public void write(ShopDefinition definition, FileHandler fileHandler) {
		FileConfiguration cfg = fileHandler.getFileConfiguration();

		clearRoot(cfg);

		cfg.set("Title", definition.getTitle());
		cfg.set("Size", definition.getSize());
		cfg.set("Buy_Entries", serializeEntries(definition.getBuyEntries()));
		cfg.set("Sell_Entries", serializeEntries(definition.getSellEntries()));
		cfg.set("Sell_Categories", serializeSellCategories(definition.getSellCategories()));
		cfg.set("Barter_Categories", serializeBarterCategories(definition.getBarterCategories()));

		fileHandler.save();
	}

	public ShopDefinition readKindAwareCopyReplacing(ShopDefinition existing, EntryKind kind,
	                                                 List<ShopItemEntry> newEntries) {
		List<ShopItemEntry> buy  = kind == EntryKind.BUY ? newEntries : existing.getBuyEntries();
		List<ShopItemEntry> sell = kind == EntryKind.SELL ? newEntries : existing.getSellEntries();

		return new ShopDefinition(existing.getKey(), existing.getTitle(), existing.getSize(), buy, sell,
		                          existing.getSellCategories(), existing.getBarterCategories());
	}

	public ShopDefinition copyReplacingSellCategories(ShopDefinition existing, List<SellCategory> newCategories) {
		return new ShopDefinition(existing.getKey(), existing.getTitle(), existing.getSize(),
		                          existing.getBuyEntries(), existing.getSellEntries(),
		                          newCategories, existing.getBarterCategories());
	}

	public ShopDefinition copyReplacingBarterCategories(ShopDefinition existing, List<BarterCategory> newCategories) {
		return new ShopDefinition(existing.getKey(), existing.getTitle(), existing.getSize(),
		                          existing.getBuyEntries(), existing.getSellEntries(),
		                          existing.getSellCategories(), newCategories);
	}

	private void clearRoot(FileConfiguration cfg) {
		for (String rootKey : new ArrayList<>(cfg.getKeys(false))) {
			cfg.set(rootKey, null);
		}
	}

	private List<Map<String, Object>> serializeEntries(List<ShopItemEntry> entries) {
		List<Map<String, Object>> out = new ArrayList<>(entries.size());

		for (ShopItemEntry entry : entries) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("Slot", entry.getSlot());
			map.put("Item", entry.getItem());

			if (entry.hasPrice()) {
				map.put("Price", entry.getPrice().toPlainString());
			}

			out.add(map);
		}

		return out;
	}

	private List<Map<String, Object>> serializeSellCategories(List<SellCategory> categories) {
		List<Map<String, Object>> out = new ArrayList<>(categories.size());

		for (SellCategory category : categories) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("Id", category.getId());
			map.put("Display_Name", category.getDisplayName());
			map.put("Base_Price", category.getBasePrice().toPlainString());

			List<ItemStack> items = new ArrayList<>(category.getItems());
			map.put("Items", items);

			out.add(map);
		}

		return out;
	}

	private List<Map<String, Object>> serializeBarterCategories(List<BarterCategory> categories) {
		List<Map<String, Object>> out = new ArrayList<>(categories.size());

		for (BarterCategory category : categories) {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("Id", category.getId());
			map.put("Display_Name", category.getDisplayName());
			map.put("Base_Price", category.getBasePrice().toPlainString());

			List<ItemStack> items = new ArrayList<>(category.getItems());
			map.put("Items", items);

			out.add(map);
		}

		return out;
	}

}
