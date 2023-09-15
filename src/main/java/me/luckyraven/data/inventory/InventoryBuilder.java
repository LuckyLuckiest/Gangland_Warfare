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

	private final InventoryData inventoryData;
	private final String        permission;

	@Nullable
	private OpenInventory openInventory;

	public InventoryBuilder(InventoryData inventoryData, String permission) {
		this.inventoryData = inventoryData;
		this.permission = permission;
	}

	public InventoryHandler createInventory(Gangland gangland, User<Player> user, String name) {
		InventoryHandler handler = user.getInventory(name);

		// create a new instance
		if (handler == null) {
//			InventoryHandler inventoryHandler = new InventoryHandler(gangland, )
//			InventoryHandler inventoryHandler = builder.getInventoryHandler();
//			handler = new InventoryHandler(gangland,
//			                               gangland.usePlaceholder(user.getUser(), inventoryHandler.getDisplayTitle()),
//			                               inventoryHandler.getSize(), user);

//			handler.copyContent(inventoryHandler, user.getUser());
		}

		return handler;
	}

	public void addOpen(@NotNull State state, String output, String permission) {
		if (openInventory == null) this.openInventory = new OpenInventory(state, output, permission);
	}

}
