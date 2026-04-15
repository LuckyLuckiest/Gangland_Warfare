package me.luckyraven.shop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;

@Getter
@RequiredArgsConstructor
public final class ShopDefinition {

	private final String              key;
	private final String              title;
	private final int                 size;
	private final List<ShopItemEntry> buyEntries;
	private final List<ShopItemEntry> sellEntries;

	public static ShopDefinition empty(String key, String title, int size) {
		return new ShopDefinition(key, title, size, Collections.emptyList(), Collections.emptyList());
	}

	public ShopDefinition withTitle(String newTitle) {
		return new ShopDefinition(key, newTitle, size, buyEntries, sellEntries);
	}

	public List<ShopItemEntry> entriesOf(EntryKind kind) {
		return kind == EntryKind.BUY ? buyEntries : sellEntries;
	}

}
