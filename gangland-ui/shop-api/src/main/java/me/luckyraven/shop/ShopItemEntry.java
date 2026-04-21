package me.luckyraven.shop;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public final class ShopItemEntry {

	private final int       slot;
	private final EntryKind kind;
	private final ItemStack item;

	@Nullable
	private final BigDecimal price;

	public boolean hasPrice() {
		return price != null;
	}

}
