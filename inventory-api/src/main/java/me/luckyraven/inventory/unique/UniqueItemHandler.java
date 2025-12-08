package me.luckyraven.inventory.unique;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.block.Action;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class UniqueItemHandler {

	private final String       inventoryName;
	private final String       uniqueItemKey;
	private final List<Action> allowedActions;
	private final String       permission;
	private final boolean      movable;
	private final boolean      droppable;

	public UniqueItemHandler(String inventoryName, String uniqueItemKey, List<Action> allowedActions,
							 String permission) {
		this(inventoryName, uniqueItemKey, allowedActions, permission, false, false);
	}

	public boolean isActionAllowed(Action action) {
		return allowedActions.contains(action);
	}

}
