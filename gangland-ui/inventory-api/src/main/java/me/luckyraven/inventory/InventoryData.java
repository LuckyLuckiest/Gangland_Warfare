package me.luckyraven.inventory;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import me.luckyraven.inventory.part.Slot;

import java.util.*;

@Data
public class InventoryData {

	private final String name, displayName, type;
	private final int        size;
	private final List<Slot> slots = new ArrayList<>();

	private String              permission;
	@Setter(AccessLevel.NONE)
	private List<OpenInventory> openInventories = new ArrayList<>();
	private boolean             fill, border;
	private List<Integer> verticalLine, horizontalLine;

	// multi inventory
	private boolean isMultiInventory;
	private String  itemSource;
	private int     perPage;

	private Map<Integer, Slot> staticItems;

	/**
	 * Optional per-entry template used by multi-inventories to render each {@code ItemSourceEntry} from the provider.
	 * When null, the provider is responsible for producing fully-built ItemStacks; when set, the provider returns
	 * placeholder data and the renderer substitutes it into this template.
	 */
	private Slot   itemTemplate;
	private String itemTemplateCommand;

	public void addOpenInventory(OpenInventory openInventory) {
		openInventories.add(openInventory);
	}

	public void addAllSlots(Collection<Slot> slots) {
		this.slots.addAll(slots);
	}

	public void addSlot(Slot slot) {
		slots.add(slot);
	}

	public List<Slot> getSlots() {
		return Collections.unmodifiableList(slots);
	}

}
