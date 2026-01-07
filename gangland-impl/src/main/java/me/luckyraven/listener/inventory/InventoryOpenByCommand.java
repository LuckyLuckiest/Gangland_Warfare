package me.luckyraven.listener.inventory;

import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.file.configuration.inventory.InventoryAddon;
import me.luckyraven.inventory.InventoryBuilder;
import me.luckyraven.inventory.InventoryOpener;
import me.luckyraven.inventory.OpenInventory;
import me.luckyraven.inventory.State;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@ListenerHandler
public class InventoryOpenByCommand implements Listener {

	private final Gangland gangland;

	public InventoryOpenByCommand(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onInventoryCommand(PlayerCommandPreprocessEvent event) {
		// includes the '/' at the beginning
		String[] command = event.getMessage().strip().split(" ");
		Player   player  = event.getPlayer();

		// this event runs before the event
		Set<String> inventoryKeys = InventoryAddon.getInventoryKeys();

		for (String inventoryKey : inventoryKeys) {
			InventoryBuilder builder = InventoryAddon.getInventory(inventoryKey);
			if (builder == null) continue;

			List<OpenInventory> openInventories = builder.inventoryData().getOpenInventories();

			if (openInventories == null) continue;

			if (checkCommand(event, openInventories, command, builder, player)) return;
		}
	}

	private boolean checkCommand(PlayerCommandPreprocessEvent event, List<OpenInventory> openInventories,
								 String[] command, InventoryBuilder builder, Player player) {
		for (OpenInventory openInventory : openInventories) {
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
				Fill fill = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());
				Fill line = new Fill(SettingAddon.getInventoryLineName(), SettingAddon.getInventoryLineItem());

				// Create the opener callback
				InventoryOpener opener = (p, invName) -> InventoryAddon.openInventoryForPlayer(gangland, p, invName);

				var placeholder = gangland.getInitializer().getPlaceholderService();
				var evaluator   = gangland.getInitializer().getEvaluator();

				var inventoryHandler = builder.createInventory(gangland, placeholder, player, fill, line, evaluator,
															   opener);

				inventoryHandler.open(player);
				event.setCancelled(true);
				break;
			} catch (Exception exception) {
				return true;
			}
		}

		return false;
	}

}
