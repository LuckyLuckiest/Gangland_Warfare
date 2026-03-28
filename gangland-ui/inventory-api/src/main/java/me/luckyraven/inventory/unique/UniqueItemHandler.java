package me.luckyraven.inventory.unique;

import org.bukkit.event.block.Action;

import java.util.List;

public record UniqueItemHandler(String inventoryName, String uniqueItemKey, List<Action> allowedActions,
								String permission) {

	public boolean isActionAllowed(Action action) {
		return allowedActions.contains(action);
	}

}
