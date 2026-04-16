package me.luckyraven.shop.io;

import lombok.CustomLog;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.shop.EntryKind;
import me.luckyraven.shop.SellCategory;
import me.luckyraven.shop.ShopDefinition;
import me.luckyraven.shop.ShopItemEntry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CustomLog
public final class ShopYamlReader {

	private static final String KEY_TITLE           = "title";
	private static final String KEY_SIZE            = "size";
	private static final String KEY_BUY_ENTRIES     = "buy-entries";
	private static final String KEY_SELL_ENTRIES    = "sell-entries";
	private static final String KEY_SELL_CATEGORIES = "sell-categories";

	private static final String ENTRY_SLOT      = "slot";
	private static final String ENTRY_ITEM      = "item";
	private static final String ENTRY_PRICE     = "price";
	private static final String ENTRY_TRADE_FOR = "trade-for";

	private static final String CATEGORY_ID           = "id";
	private static final String CATEGORY_DISPLAY_NAME = "display-name";
	private static final String CATEGORY_BASE_PRICE   = "base-price";
	private static final String CATEGORY_ITEMS        = "items";

	private static final int    DEFAULT_SIZE  = 54;
	private static final String DEFAULT_TITLE = "Trader";

	public ShopDefinition parse(String key, FileHandler fileHandler) {
		FileConfiguration cfg = fileHandler.getFileConfiguration();

		String title = cfg.getString(KEY_TITLE, DEFAULT_TITLE);
		int    size  = cfg.getInt(KEY_SIZE, DEFAULT_SIZE);

		if (size <= 0 || size > 54 || size % 9 != 0) {
			log.warn("Shop '{}' has invalid size {}; falling back to {}", key, size, DEFAULT_SIZE);
			size = DEFAULT_SIZE;
		}

		List<ShopItemEntry> buyEntries     = readEntries(key, cfg, KEY_BUY_ENTRIES, EntryKind.BUY);
		List<ShopItemEntry> sellEntries    = readEntries(key, cfg, KEY_SELL_ENTRIES, EntryKind.SELL);
		List<SellCategory>  sellCategories = readSellCategories(key, cfg);

		return new ShopDefinition(key, title, size, buyEntries, sellEntries, sellCategories);
	}

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
				category = parseCategoryFromSection(key, index, section);
			} else if (element instanceof Map<?, ?> map) {
				category = parseCategoryFromMap(key, index, map);
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

	private SellCategory parseCategoryFromSection(String key, int index, ConfigurationSection section) {
		String id          = section.getString(CATEGORY_ID);
		String displayName = section.getString(CATEGORY_DISPLAY_NAME, id);
		double basePrice   = section.getDouble(CATEGORY_BASE_PRICE, 0.0);

		List<ItemStack> items = new ArrayList<>();
		List<?>         raw   = section.getList(CATEGORY_ITEMS);
		if (raw != null) {
			for (Object element : raw) {
				if (element instanceof ItemStack stack) {
					items.add(stack);
				}
			}
		}

		return buildCategory(key, index, id, displayName, basePrice, items);
	}

	private SellCategory parseCategoryFromMap(String key, int index, Map<?, ?> map) {
		Object idVal    = map.get(CATEGORY_ID);
		Object nameVal  = map.get(CATEGORY_DISPLAY_NAME);
		Object priceVal = map.get(CATEGORY_BASE_PRICE);
		Object itemsVal = map.get(CATEGORY_ITEMS);

		String          id          = idVal == null ? null : idVal.toString();
		String          displayName = nameVal == null ? id : nameVal.toString();
		double          basePrice   = priceVal instanceof Number n ? n.doubleValue() : 0.0;
		List<ItemStack> items       = new ArrayList<>();

		if (itemsVal instanceof List<?> rawItems) {
			for (Object element : rawItems) {
				if (element instanceof ItemStack stack) {
					items.add(stack);
				}
			}
		}

		return buildCategory(key, index, id, displayName, basePrice, items);
	}

	private SellCategory buildCategory(String key, int index, String id, String displayName,
	                                   double basePrice, List<ItemStack> items) {
		if (id == null || id.isBlank()) {
			log.warn("Shop '{}' {}[{}] has no id; skipping", key, KEY_SELL_CATEGORIES, index);
			return null;
		}
		return new SellCategory(id, displayName == null ? id : displayName, basePrice, items);
	}

	private List<ShopItemEntry> readEntries(String key, FileConfiguration cfg, String path, EntryKind kind) {
		List<ShopItemEntry> result = new ArrayList<>();
		List<?>             raw    = cfg.getList(path);

		if (raw == null) return result;

		for (int index = 0; index < raw.size(); index++) {
			Object element = raw.get(index);
			if (!(element instanceof ConfigurationSection section)) {
				// When loaded from YAML, list entries become LinkedHashMaps. Wrap them.
				if (element instanceof java.util.Map<?, ?> map) {
					ShopItemEntry entry = parseFromMap(key, path, index, kind, map);
					if (entry != null) result.add(entry);
					continue;
				}
				log.warn("Shop '{}' {}[{}] is not a mapping; skipping", key, path, index);
				continue;
			}

			ShopItemEntry entry = parseFromSection(key, path, index, kind, section);
			if (entry != null) result.add(entry);
		}

		return result;
	}

	private ShopItemEntry parseFromSection(String key, String path, int index, EntryKind kind,
	                                       ConfigurationSection section) {
		int       slot     = section.getInt(ENTRY_SLOT, -1);
		ItemStack item     = section.getItemStack(ENTRY_ITEM);
		Double    price    = section.contains(ENTRY_PRICE) ? section.getDouble(ENTRY_PRICE) : null;
		ItemStack tradeFor = section.getItemStack(ENTRY_TRADE_FOR);

		return buildEntry(key, path, index, kind, slot, item, price, tradeFor);
	}

	private ShopItemEntry parseFromMap(String key, String path, int index, EntryKind kind,
	                                   java.util.Map<?, ?> map) {
		Object slotVal  = map.get(ENTRY_SLOT);
		Object itemVal  = map.get(ENTRY_ITEM);
		Object priceVal = map.get(ENTRY_PRICE);
		Object tradeVal = map.get(ENTRY_TRADE_FOR);

		int       slot     = slotVal instanceof Number n ? n.intValue() : -1;
		ItemStack item     = itemVal instanceof ItemStack is ? is : null;
		Double    price    = priceVal instanceof Number n ? n.doubleValue() : null;
		ItemStack tradeFor = tradeVal instanceof ItemStack is ? is : null;

		return buildEntry(key, path, index, kind, slot, item, price, tradeFor);
	}

	private ShopItemEntry buildEntry(String key, String path, int index, EntryKind kind,
	                                 int slot, ItemStack item, Double price, ItemStack tradeFor) {
		if (item == null) {
			log.warn("Shop '{}' {}[{}] has no item; skipping", key, path, index);
			return null;
		}

		if (slot < 0) {
			log.warn("Shop '{}' {}[{}] has invalid slot {}; skipping", key, path, index, slot);
			return null;
		}

		if (price == null && tradeFor == null) {
			log.warn("Shop '{}' {}[{}] has neither price nor trade-for; skipping", key, path, index);
			return null;
		}

		return new ShopItemEntry(slot, kind, item, price, tradeFor);
	}

}
