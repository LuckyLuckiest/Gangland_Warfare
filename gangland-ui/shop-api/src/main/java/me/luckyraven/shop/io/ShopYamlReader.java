package me.luckyraven.shop.io;

import lombok.CustomLog;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.config.ConfigReport;
import me.luckyraven.persistence.config.FileHandlerReader;
import me.luckyraven.persistence.config.NodeReader;
import me.luckyraven.shop.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CustomLog
public final class ShopYamlReader {

	private static final String KEY_TITLE             = "Title";
	private static final String KEY_SIZE              = "Size";
	private static final String KEY_BUY_ENTRIES       = "Buy_Entries";
	private static final String KEY_SELL_ENTRIES      = "Sell_Entries";
	private static final String KEY_SELL_CATEGORIES   = "Sell_Categories";
	private static final String KEY_BARTER_CATEGORIES = "Barter_Categories";

	private static final String ENTRY_SLOT  = "Slot";
	private static final String ENTRY_ITEM  = "Item";
	private static final String ENTRY_PRICE = "Price";

	private static final String CATEGORY_ID           = "Id";
	private static final String CATEGORY_DISPLAY_NAME = "Display_Name";
	private static final String CATEGORY_BASE_PRICE   = "Base_Price";
	private static final String CATEGORY_ITEMS        = "Items";

	private static final int    DEFAULT_SIZE  = 54;
	private static final String DEFAULT_TITLE = "Trader";

	public ShopDefinition parse(String key, FileHandler fileHandler) {
		FileConfiguration cfg    = fileHandler.getFileConfiguration();
		ConfigReport      report = new ConfigReport();
		NodeReader        root   = FileHandlerReader.read(fileHandler, report);

		String title = root.get(KEY_TITLE).asString().orDefault(DEFAULT_TITLE);
		int    size  = root.get(KEY_SIZE).asInt().orDefault(DEFAULT_SIZE);

		// Entries and categories are read off the raw FileConfiguration below (the NodeReader positional layer cannot
		// rebuild Bukkit-serialized ItemStacks from their mapping form). Touch the keys here so the unknown-key sweep
		// still recognises them as legitimate root keys.
		root.get(KEY_BUY_ENTRIES);
		root.get(KEY_SELL_ENTRIES);
		root.get(KEY_SELL_CATEGORIES);
		root.get(KEY_BARTER_CATEGORIES);

		if (size <= 0 || size > 54 || size % 9 != 0) {
			log.warn("Shop '{}' has invalid size {}; falling back to {}", key, size, DEFAULT_SIZE);
			size = DEFAULT_SIZE;
		}

		List<ShopItemEntry>  buyEntries       = readEntries(key, cfg, KEY_BUY_ENTRIES, EntryKind.BUY);
		List<ShopItemEntry>  sellEntries      = readEntries(key, cfg, KEY_SELL_ENTRIES, EntryKind.SELL);
		List<SellCategory>   sellCategories   = readSellCategories(key, cfg);
		List<BarterCategory> barterCategories = readBarterCategories(key, cfg);

		if (!report.isEmpty()) report.log(log);

		return new ShopDefinition(key, title, size, buyEntries, sellEntries, sellCategories, barterCategories);
	}

	// ── Sell categories ─────────────────────────────────────────────────────

	private List<SellCategory> readSellCategories(String key, FileConfiguration cfg) {
		List<SellCategory> result = new ArrayList<>();
		List<?>            raw    = cfg.getList(KEY_SELL_CATEGORIES);

		if (raw == null) {
			return result;
		}

		for (int index = 0; index < raw.size(); index++) {
			Object       element = raw.get(index);
			SellCategory category;

			if (element instanceof ConfigurationSection section) {
				category = parseSellCategoryFromSection(key, index, section);
			} else if (element instanceof Map<?, ?> map) {
				category = parseSellCategoryFromMap(key, index, map);
			} else {
				log.warn("Shop '{}' {}[{}] is not a mapping; skipping", key, KEY_SELL_CATEGORIES, index);
				continue;
			}

			if (category != null) {
				result.add(category);
			}
		}

		return result;
	}

	private SellCategory parseSellCategoryFromSection(String key, int index, ConfigurationSection section) {
		String          id          = section.getString(CATEGORY_ID);
		String          displayName = section.getString(CATEGORY_DISPLAY_NAME, id);
		double          basePrice   = section.getDouble(CATEGORY_BASE_PRICE, 0.0);
		List<ItemStack> items       = readCategoryItems(section.getList(CATEGORY_ITEMS));

		if (id == null || id.isBlank()) {
			log.warn("Shop '{}' {}[{}] has no id; skipping", key, KEY_SELL_CATEGORIES, index);
			return null;
		}
		return new SellCategory(id, displayName == null ? id : displayName, basePrice, items);
	}

	private SellCategory parseSellCategoryFromMap(String key, int index, Map<?, ?> map) {
		Object idVal    = map.get(CATEGORY_ID);
		Object nameVal  = map.get(CATEGORY_DISPLAY_NAME);
		Object priceVal = map.get(CATEGORY_BASE_PRICE);
		Object itemsVal = map.get(CATEGORY_ITEMS);

		String          id          = idVal == null ? null : idVal.toString();
		String          displayName = nameVal == null ? id : nameVal.toString();
		double          basePrice   = priceVal instanceof Number n ? n.doubleValue() : 0.0;
		List<ItemStack> items       = readCategoryItems(itemsVal instanceof List<?> rawItems ? rawItems : null);

		if (id == null || id.isBlank()) {
			log.warn("Shop '{}' {}[{}] has no id; skipping", key, KEY_SELL_CATEGORIES, index);
			return null;
		}
		return new SellCategory(id, displayName == null ? id : displayName, basePrice, items);
	}

	// ── Barter categories ──────────────────────────────────────────────────

	private List<BarterCategory> readBarterCategories(String key, FileConfiguration cfg) {
		List<BarterCategory> result = new ArrayList<>();
		List<?>              raw    = cfg.getList(KEY_BARTER_CATEGORIES);

		if (raw == null) {
			return result;
		}

		for (int index = 0; index < raw.size(); index++) {
			Object         element = raw.get(index);
			BarterCategory category;

			if (element instanceof ConfigurationSection section) {
				category = parseBarterCategoryFromSection(key, index, section);
			} else if (element instanceof Map<?, ?> map) {
				category = parseBarterCategoryFromMap(key, index, map);
			} else {
				log.warn("Shop '{}' {}[{}] is not a mapping; skipping", key, KEY_BARTER_CATEGORIES, index);
				continue;
			}

			if (category != null) {
				result.add(category);
			}
		}

		return result;
	}

	private BarterCategory parseBarterCategoryFromSection(String key, int index, ConfigurationSection section) {
		String          id          = section.getString(CATEGORY_ID);
		String          displayName = section.getString(CATEGORY_DISPLAY_NAME, id);
		double          basePrice   = section.getDouble(CATEGORY_BASE_PRICE, 0.0);
		List<ItemStack> items       = readCategoryItems(section.getList(CATEGORY_ITEMS));

		if (id == null || id.isBlank()) {
			log.warn("Shop '{}' {}[{}] has no id; skipping", key, KEY_BARTER_CATEGORIES, index);
			return null;
		}
		return new BarterCategory(id, displayName == null ? id : displayName, basePrice, items);
	}

	private BarterCategory parseBarterCategoryFromMap(String key, int index, Map<?, ?> map) {
		Object idVal    = map.get(CATEGORY_ID);
		Object nameVal  = map.get(CATEGORY_DISPLAY_NAME);
		Object priceVal = map.get(CATEGORY_BASE_PRICE);
		Object itemsVal = map.get(CATEGORY_ITEMS);

		String          id          = idVal == null ? null : idVal.toString();
		String          displayName = nameVal == null ? id : nameVal.toString();
		double          basePrice   = priceVal instanceof Number n ? n.doubleValue() : 0.0;
		List<ItemStack> items       = readCategoryItems(itemsVal instanceof List<?> rawItems ? rawItems : null);

		if (id == null || id.isBlank()) {
			log.warn("Shop '{}' {}[{}] has no id; skipping", key, KEY_BARTER_CATEGORIES, index);
			return null;
		}
		return new BarterCategory(id, displayName == null ? id : displayName, basePrice, items);
	}

	private List<ItemStack> readCategoryItems(List<?> raw) {
		List<ItemStack> items = new ArrayList<>();
		if (raw == null) {
			return items;
		}
		for (Object element : raw) {
			if (element instanceof ItemStack stack) {
				items.add(stack);
			}
		}
		return items;
	}

	// ── Entries ─────────────────────────────────────────────────────────────

	private List<ShopItemEntry> readEntries(String key, FileConfiguration cfg, String path, EntryKind kind) {
		List<ShopItemEntry> result = new ArrayList<>();
		List<?>             raw    = cfg.getList(path);

		if (raw == null) return result;

		for (int index = 0; index < raw.size(); index++) {
			Object element = raw.get(index);
			if (!(element instanceof ConfigurationSection section)) {
				if (element instanceof java.util.Map<?, ?> map) {
					ShopItemEntry entry = parseEntryFromMap(key, path, index, kind, map);
					if (entry != null) result.add(entry);
					continue;
				}
				log.warn("Shop '{}' {}[{}] is not a mapping; skipping", key, path, index);
				continue;
			}

			ShopItemEntry entry = parseEntryFromSection(key, path, index, kind, section);
			if (entry != null) result.add(entry);
		}

		return result;
	}

	private ShopItemEntry parseEntryFromSection(String key, String path, int index, EntryKind kind,
	                                            ConfigurationSection section) {
		int       slot  = section.getInt(ENTRY_SLOT, -1);
		ItemStack item  = section.getItemStack(ENTRY_ITEM);
		Double    price = section.contains(ENTRY_PRICE) ? section.getDouble(ENTRY_PRICE) : null;

		return buildEntry(key, path, index, kind, slot, item, price);
	}

	private ShopItemEntry parseEntryFromMap(String key, String path, int index, EntryKind kind,
	                                        java.util.Map<?, ?> map) {
		Object slotVal  = map.get(ENTRY_SLOT);
		Object itemVal  = map.get(ENTRY_ITEM);
		Object priceVal = map.get(ENTRY_PRICE);

		int       slot  = slotVal instanceof Number n ? n.intValue() : -1;
		ItemStack item  = itemVal instanceof ItemStack is ? is : null;
		Double    price = priceVal instanceof Number n ? n.doubleValue() : null;

		return buildEntry(key, path, index, kind, slot, item, price);
	}

	private ShopItemEntry buildEntry(String key, String path, int index, EntryKind kind, int slot, ItemStack item,
	                                 Double price) {
		if (item == null) {
			log.warn("Shop '{}' {}[{}] has no item; skipping", key, path, index);
			return null;
		}

		if (slot < 0) {
			log.warn("Shop '{}' {}[{}] has invalid slot {}; skipping", key, path, index, slot);
			return null;
		}

		if (price == null) {
			log.warn("Shop '{}' {}[{}] has no price; skipping", key, path, index);
			return null;
		}

		return new ShopItemEntry(slot, kind, item, price);
	}

}
