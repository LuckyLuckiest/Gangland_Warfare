package org.luckyraven.gangland.menu.part;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.luckyraven.keystone.inventory.click.ClickHandler;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.menu.condition.ConditionEvaluator;
import org.luckyraven.gangland.menu.condition.ConditionalSlotData;

@Getter
@RequiredArgsConstructor
public class Slot {

	private final int         slot;
	private final boolean     clickable;
	private final boolean     draggable;
	private final ItemBuilder item;

	@Setter
	private ConditionalSlotData conditionalData;

	private ClickHandler clickableSlot;
	private ClickHandler rightClickSlot;

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

			// The real click action is built from resolved.getClickAction()/getRightClickAction() by the caller
			// (InventoryBuilder), which has the ClickContext at click time; this result just carries the raw
			// ConditionalSlotData.ClickAction through.
			return new ConditionalSlotResult(resolvedItem, resolvedClickable, resolvedDraggable, null,
			                                 resolved.getClickAction(), resolved.getRightClickAction());
		}

		return new ConditionalSlotResult(item, clickable, draggable, clickableSlot, null, null);
	}

	public void setClickable(ClickHandler clickable) {
		Preconditions.checkArgument(this.clickable, "The slot is not clickable");
		this.clickableSlot = clickable;
	}

	public void setRightClickable(ClickHandler rightClickable) {
		this.rightClickSlot = rightClickable;
	}

}
