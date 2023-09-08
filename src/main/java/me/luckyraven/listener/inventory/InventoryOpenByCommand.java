package me.luckyraven.listener.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.data.inventory.InventoryBuilder;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Map;
import java.util.stream.IntStream;

public class InventoryOpenByCommand implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	public InventoryOpenByCommand(Gangland gangland) {
		this.gangland = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler
	public void onInventoryCommand(PlayerCommandPreprocessEvent event) {
		// includes the '/' at the beginning
		String[] command = event.getMessage().strip().split(" ");

		// this event runs before the event
		Map<String, InventoryBuilder> inventories = InventoryAddon.getInventories();

		for (Map.Entry<String, InventoryBuilder> inventory : inventories.entrySet()) {
			String           name    = inventory.getKey();
			InventoryBuilder builder = inventory.getValue();

			if (builder.getOpenInventory() == null) continue;
			// only check command types
			if (builder.getOpenInventory().getState() != InventoryBuilder.State.COMMAND) continue;

			// check if the command array is equal to an inventory command array
			String[] inventoryCommandArr = builder.getOpenInventory().getState().getOutput().split(" ");

			// check length
			if (command.length != inventoryCommandArr.length) continue;

			// check each content
			boolean arraysEqual = IntStream.range(0, command.length).allMatch(
					i -> command[i].equals(inventoryCommandArr[i]));
			if (!arraysEqual) continue;

			Player       player = event.getPlayer();
			User<Player> user   = userManager.getUser(player);

			if (!user.getUser().hasPermission(builder.getOpenInventory().getPermission())) break;
			else {
				try {
					InventoryHandler inventoryHandler = InventoryBuilder.initInventory(gangland, user, name, builder);

					inventoryHandler.open(player);
					event.setCancelled(true);
					break;
				} catch (Exception exception) {
					return;
				}
			}
		}
	}

}
