package me.luckyraven.data.inventory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.data.inventory.part.Slot;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Getter
@Setter
public class InventoryData {

	private final String name, displayName, type;
	private final int        size;
	private final List<Slot> slots = new ArrayList<>();

	private String        permission;
	private OpenInventory openInventory;
	private boolean       fill, border;
	private List<Integer> verticalLine, horizontalLine;

	public List<Slot> getSlots() {
		return new ArrayList<>(slots);
	}

}
