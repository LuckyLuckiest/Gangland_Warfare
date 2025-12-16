package me.luckyraven.inventory.part;

import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.condition.ConditionEvaluator;
import me.luckyraven.inventory.condition.ConditionalSlotData;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.TriConsumer;
import org.bukkit.entity.Player;

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
											 resolved.getClickAction());
		}

		return new ConditionalSlotResult(item, clickable, draggable, clickableSlot, null);
	}

	public void setClickable(TriConsumer<Player, InventoryHandler, ItemBuilder> clickable) {
		Preconditions.checkArgument(this.clickable, "The slot is not clickable");
		this.clickableSlot = clickable;
	}

}
