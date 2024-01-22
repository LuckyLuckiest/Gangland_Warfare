package me.luckyraven.data.inventory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.data.inventory.part.Slot;

import java.util.ArrayList;
import java.util.Collection;
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

	public void addAllSlots(Collection<Slot> slots) {
		this.slots.addAll(slots);
	}

	public void addSlot(Slot slot) {
		slots.add(slot);
	}

	public List<Slot> getSlots() {
		return new ArrayList<>(slots);
	}

}
