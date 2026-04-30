package org.luckyraven.gangland.inventory.part;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.condition.ConditionEvaluator;
import org.luckyraven.gangland.inventory.condition.ConditionalSlotData;

@Getter
@RequiredArgsConstructor
public class Slot {

	private final int         slot;
	private final boolean     clickable;
	private final boolean     draggable;
	private final ItemBuilder item;

	@Setter
	private ConditionalSlotData conditionalData;

	private TriConsumer<Player, InventoryHandler, ItemBuilder> clickableSlot;
	private TriConsumer<Player, InventoryHandler, ItemBuilder> rightClickSlot;

	/**
	 * Gets the appropriate item and click action based on conditions
	 */
	public ConditionalSlotResult getConditionalResult(Player player, ConditionEvaluator evaluator) {
		// Check if has conditional data
		if (conditionalData != null) {
			ConditionalSlotData.BranchData resolved = conditionalData.resolve(player, evaluator)
			                                                         .resolveFinal(player, evaluator);

			ItemBuilder resolvedItem      = resolved.getItem();
			boolean     resolvedClickable = resolved.isClickable();
			boolean     resolvedDraggable = resolved.isDraggable();

			// Create click action from the resolved data
			TriConsumer<Player, InventoryHandler, ItemBuilder> action = null;
			if (resolved.getClickAction() != null) {
				action = (p, inv, builder) -> { }; // Placeholder, will be replaced in InventoryBuilder
			}

			return new ConditionalSlotResult(resolvedItem, resolvedClickable, resolvedDraggable, action,
			                                 resolved.getClickAction(), resolved.getRightClickAction());
		}

		return new ConditionalSlotResult(item, clickable, draggable, clickableSlot, null, null);
	}

	public void setClickable(TriConsumer<Player, InventoryHandler, ItemBuilder> clickable) {
		Preconditions.checkArgument(this.clickable, "The slot is not clickable");
		this.clickableSlot = clickable;
	}

	public void setRightClickable(TriConsumer<Player, InventoryHandler, ItemBuilder> rightClickable) {
		this.rightClickSlot = rightClickable;
	}

}
