package me.luckyraven.listener.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.data.inventory.InventoryBuilder;
import me.luckyraven.data.inventory.OpenInventory;
import me.luckyraven.data.inventory.State;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;
import java.util.stream.IntStream;

@ListenerHandler
public class InventoryOpenByCommand implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	public InventoryOpenByCommand(Gangland gangland) {
		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler
	public void onInventoryCommand(PlayerCommandPreprocessEvent event) {
		// includes the '/' at the beginning
		String[]     command = event.getMessage().strip().split(" ");
		Player       player  = event.getPlayer();
		User<Player> user    = userManager.getUser(player);

		// this event runs before the event
		Set<String> inventoryKeys = InventoryAddon.getInventoryKeys();

		for (String inventory : inventoryKeys) {
			InventoryBuilder builder = InventoryAddon.getInventory(inventory);
			if (builder == null) continue;

			OpenInventory openInventory = builder.inventoryData().getOpenInventory();

			if (openInventory == null) continue;
			// only check command types
			if (openInventory.state() != State.COMMAND) continue;

			// check if the command array is equal to an inventory command array
			String[] inventoryCommandArr = openInventory.output().split(" ");

			// check length
			if (command.length != inventoryCommandArr.length) continue;

			// check each content
			boolean arraysEqual = IntStream.range(0, command.length)
										   .allMatch(i -> command[i].equals(inventoryCommandArr[i]));
			if (!arraysEqual) continue;

			String permission = builder.permission();
			if (permission != null && !player.hasPermission(permission)) break;

			if (openInventory.permission() != null && !player.hasPermission(openInventory.permission())) break;

			try {
				InventoryHandler inventoryHandler = builder.createInventory(gangland, user, inventory);

				inventoryHandler.open(player);
				event.setCancelled(true);
				break;
			} catch (Exception exception) {
				return;
			}

		}
	}

}
