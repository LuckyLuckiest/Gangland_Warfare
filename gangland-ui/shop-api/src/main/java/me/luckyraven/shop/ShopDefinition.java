package me.luckyraven.shop;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public final class ShopDefinition {

	private final String              key;
	private final String              title;
	private final int                 size;
	private final List<ShopItemEntry> buyEntries;
	private final List<ShopItemEntry> sellEntries;
	private final List<SellCategory>  sellCategories;

	public ShopDefinition(String key, String title, int size,
	                      List<ShopItemEntry> buyEntries,
	                      List<ShopItemEntry> sellEntries,
	                      List<SellCategory> sellCategories) {
		this.key            = key;
		this.title          = title;
		this.size           = size;
		this.buyEntries     = buyEntries;
		this.sellEntries    = sellEntries;
		this.sellCategories = sellCategories == null ? new ArrayList<>() : new ArrayList<>(sellCategories);
	}

	public ShopDefinition(String key, String title, int size,
	                      List<ShopItemEntry> buyEntries,
	                      List<ShopItemEntry> sellEntries) {
		this(key, title, size, buyEntries, sellEntries, new ArrayList<>());
	}

	public static ShopDefinition empty(String key, String title, int size) {
		return new ShopDefinition(key, title, size,
		                          Collections.emptyList(), Collections.emptyList(), new ArrayList<>());
	}

	public ShopDefinition withTitle(String newTitle) {
		return new ShopDefinition(key, newTitle, size, buyEntries, sellEntries, sellCategories);
	}

	public List<ShopItemEntry> entriesOf(EntryKind kind) {
		return kind == EntryKind.BUY ? buyEntries : sellEntries;
	}

	public SellCategory getSellCategoryById(String id) {
		if (id == null) {
			return null;
		}
		for (SellCategory category : sellCategories) {
			if (id.equalsIgnoreCase(category.getId())) {
				return category;
			}
		}
		return null;
	}

	public void addSellCategory(SellCategory category) {
		sellCategories.add(category);
	}

	public boolean removeSellCategory(String id) {
		return sellCategories.removeIf(c -> c.getId().equalsIgnoreCase(id));
	}

}
