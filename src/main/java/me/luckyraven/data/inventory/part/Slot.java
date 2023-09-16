package me.luckyraven.data.inventory.part;

import com.google.common.base.Preconditions;
import lombok.Getter;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.util.TriConsumer;
import org.bukkit.entity.Player;

@Getter
public class Slot {

	private final int     slot;
	private final boolean clickable, draggable;
	private final ItemBuilder item;

	private TriConsumer<Player, InventoryHandler, ItemBuilder> clickableSlot;

	public Slot(int slot, boolean clickable, boolean draggable, ItemBuilder item) {
		this.slot = slot;
		this.clickable = clickable;
		this.draggable = draggable;
		this.item = item;
	}

	public void setClickable(TriConsumer<Player, InventoryHandler, ItemBuilder> clickable) {
		Preconditions.checkArgument(this.clickable, "The slot is not clickable");
		this.clickableSlot = clickable;
	}

}
