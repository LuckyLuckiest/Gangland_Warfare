package me.luckyraven.shop.io;

import lombok.CustomLog;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.shop.EntryKind;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@CustomLog
public final class ShopYamlWriter {

	public void write(ShopDefinition definition, FileHandler fileHandler) {
		FileConfiguration cfg = fileHandler.getFileConfiguration();

		clearRoot(cfg);

		cfg.set("title", definition.getTitle());
		cfg.set("size", definition.getSize());
		cfg.set("buy-entries", serializeEntries(definition.getBuyEntries()));
		cfg.set("sell-entries", serializeEntries(definition.getSellEntries()));

		fileHandler.save();
	}

	public ShopDefinition readKindAwareCopyReplacing(ShopDefinition existing, EntryKind kind,
	                                                 List<ShopItemEntry> newEntries) {
		List<ShopItemEntry> buy  = kind == EntryKind.BUY ? newEntries : existing.getBuyEntries();
		List<ShopItemEntry> sell = kind == EntryKind.SELL ? newEntries : existing.getSellEntries();

		return new ShopDefinition(existing.getKey(), existing.getTitle(), existing.getSize(), buy, sell);
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
			map.put("slot", entry.getSlot());
			map.put("item", entry.getItem());

			if (entry.hasPrice()) {
				map.put("price", entry.getPrice());
			}
			if (entry.hasBarter()) {
				map.put("trade-for", entry.getTradeFor());
			}

			out.add(map);
		}

		return out;
	}

}
