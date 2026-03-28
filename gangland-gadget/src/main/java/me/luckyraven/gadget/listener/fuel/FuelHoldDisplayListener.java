package me.luckyraven.gadget.listener.fuel;

import me.luckyraven.gadget.fuel.FuelService;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.item.fuel.Fuel;
import me.luckyraven.util.item.fuel.FuelBar;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a fuel bar on the action bar while the player is holding a fuel item in their hand. A lightweight repeating
 * task keeps the action bar visible (it fades after ~2 seconds).
 */
@ListenerHandler
@AutowireTarget({FuelService.class, JavaPlugin.class})
public class FuelHoldDisplayListener implements Listener {

	private static final long DISPLAY_INTERVAL_TICKS = 10L;

	private final FuelService fuelService;
	private final JavaPlugin  plugin;

	private final Map<UUID, BukkitTask> displayTasks = new ConcurrentHashMap<>();

	public FuelHoldDisplayListener(FuelService fuelService, JavaPlugin plugin) {
		this.fuelService = fuelService;
		this.plugin      = plugin;
	}

	@EventHandler
	public void onItemHeld(PlayerItemHeldEvent event) {
		Player    player  = event.getPlayer();
		ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

		if (Fuel.isFuelItem(newItem)) {
			startDisplay(player);
		} else {
			stopDisplay(player);
		}
	}

	@EventHandler
	public void onDrop(PlayerDropItemEvent event) {
		Player player = event.getPlayer();

		// Check if the held item after the drop is still a fuel item
		plugin.getServer().getScheduler().runTask(plugin, () -> {
			ItemStack heldItem = player.getInventory().getItemInMainHand();
			if (!Fuel.isFuelItem(heldItem)) {
				stopDisplay(player);
			}
		});
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		stopDisplay(event.getPlayer());
		fuelService.clearCache(event.getPlayer().getUniqueId());
	}

	private void startDisplay(Player player) {
		UUID playerId = player.getUniqueId();
		if (displayTasks.containsKey(playerId)) return;

		BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
			if (!player.isOnline()) {
				stopDisplay(player);
				return;
			}

			ItemStack heldItem = player.getInventory().getItemInMainHand();
			if (!Fuel.isFuelItem(heldItem)) {
				stopDisplay(player);
				return;
			}

			int current = Fuel.getCurrentFuel(heldItem);
			int max     = Fuel.getMaxFuel(heldItem);
			ChatUtil.sendActionBar(player, FuelBar.render(current, max));
		}, 0L, DISPLAY_INTERVAL_TICKS);

		displayTasks.put(playerId, task);
	}

	private void stopDisplay(Player player) {
		BukkitTask task = displayTasks.remove(player.getUniqueId());
		if (task != null && !task.isCancelled()) {
			task.cancel();
		}
	}

}
