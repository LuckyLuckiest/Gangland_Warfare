package me.luckyraven.shop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public final class ShopItemEntry {

	private final int       slot;
	private final EntryKind kind;
	private final ItemStack item;

	@Nullable
	private final Double    price;
	@Nullable
	private final ItemStack tradeFor;

	public boolean hasPrice() {
		return price != null;
	}

	public boolean hasBarter() {
		return tradeFor != null;
	}

}
