package me.luckyraven.data.inventory;

import lombok.Getter;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import org.jetbrains.annotations.Nullable;

@Getter
public class InventoryBuilder {

	private final InventoryHandler inventoryHandler;

	private State  openState;
	private String openPermission;

	public InventoryBuilder(InventoryHandler inventoryHandler) {
		this.inventoryHandler = inventoryHandler;
	}

	public void addOpen(@Nullable State state, String output, @Nullable String permission) {
		this.openState = state;
		if (state != null) this.openState.setState(output);
		this.openPermission = permission;
	}

	@Getter
	public enum State {
		COMMAND, EVENT;

		String output;

		void setState(String output) {
			this.output = output;
		}

	}

}
