package me.luckyraven.data.inventory;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.data.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class InventoryBuilder {

	private final InventoryHandler inventoryHandler;

	@Nullable
	private OpenInventory openInventory;

	public InventoryBuilder(InventoryHandler inventoryHandler) {
		this.inventoryHandler = inventoryHandler;
	}

	public static InventoryHandler initInventory(Gangland gangland, User<Player> user, String name,
	                                             InventoryBuilder builder) {
		InventoryHandler handler = user.getInventory(name);

		// create a new instance
		if (handler == null) {
			InventoryHandler inventoryHandler = builder.getInventoryHandler();
			handler = new InventoryHandler(gangland,
			                               gangland.usePlaceholder(user.getUser(), inventoryHandler.getDisplayTitle()),
			                               inventoryHandler.getSize(), user);

			handler.copyContent(inventoryHandler, user.getUser());
		}

		return handler;
	}

	public void addOpen(@NotNull State state, String output, String permission) {
		this.openInventory = new OpenInventory(state, output, permission);
	}

	@Getter
	public enum State {

		COMMAND, EVENT;

		String output;

		void setState(String output) {
			this.output = output;
		}

	}

	@Getter
	public static class OpenInventory {

		private final State  state;
		private final String permission;

		public OpenInventory(@NotNull State state, String output, String permission) {
			this.state = state;
			this.state.setState(output);
			this.permission = permission;
		}

	}

}
