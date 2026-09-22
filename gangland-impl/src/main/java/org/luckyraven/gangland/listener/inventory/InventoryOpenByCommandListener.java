package org.luckyraven.gangland.listener.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.registry.MenuOpener;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.file.configuration.inventory.InventoryDefinitionStore;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.gangland.menu.InventoryBuilder;
import org.luckyraven.gangland.menu.OpenInventory;
import org.luckyraven.gangland.menu.State;
import org.luckyraven.gangland.menu.condition.ConditionEvaluator;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@ListenerHandler
public class InventoryOpenByCommandListener implements Listener {

	private final Gangland                 gangland;
	private final PlaceholderService       placeholderService;
	private final ConditionEvaluator       conditionEvaluator;
	private final InventoryDefinitionStore definitionStore;
	private final InventoryRuntimeContext  runtimeContext;
	private final InventoryService         inventoryService;

	public InventoryOpenByCommandListener(Gangland gangland,
	                                      PlaceholderService placeholderService,
	                                      ConditionEvaluator conditionEvaluator,
	                                      InventoryDefinitionStore definitionStore,
	                                      InventoryRuntimeContext runtimeContext,
	                                      InventoryService inventoryService) {
		this.gangland           = gangland;
		this.placeholderService = placeholderService;
		this.conditionEvaluator = conditionEvaluator;
		this.definitionStore    = definitionStore;
		this.runtimeContext     = runtimeContext;
		this.inventoryService   = inventoryService;
	}

	@EventHandler
	public void onInventoryCommand(PlayerCommandPreprocessEvent event) {
		// includes the '/' at the beginning
		String[] command = event.getMessage().strip().split(" ");
		Player   player  = event.getPlayer();

		// this event runs before the event
		Set<String> inventoryKeys = definitionStore.getInventoryKeys();

		for (String inventoryKey : inventoryKeys) {
			InventoryBuilder builder = definitionStore.getInventory(inventoryKey);
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
				// Create the opener callback
				MenuOpener opener = runtimeContext::openInventoryForPlayer;

				var menu = builder.createMenu(inventoryService, gangland, placeholderService, player,
				                              InventoryBuilder.DEFAULT_FILL_ITEM, InventoryBuilder.DEFAULT_FILL_NAME,
				                              InventoryBuilder.DEFAULT_LINE_ITEM, InventoryBuilder.DEFAULT_LINE_NAME,
				                              conditionEvaluator, opener);

				menu.open(player);
				event.setCancelled(true);
				break;
			} catch (Exception exception) {
				return true;
			}
		}

		return false;
	}

}
