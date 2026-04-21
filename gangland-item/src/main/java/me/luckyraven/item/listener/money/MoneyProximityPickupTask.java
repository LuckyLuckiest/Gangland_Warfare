package me.luckyraven.item.listener.money;

import me.luckyraven.core.downed.DownedPlayerRegistry;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.item.money.MoneyItem;
import me.luckyraven.item.money.MoneyItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

/**
 * Fallback pickup for money items when the player's inventory is full. Spigot does not fire
 * {@link org.bukkit.event.entity.EntityPickupItemEvent} when the inventory cannot accept the item, so this periodic
 * task scans for nearby money {@link Item} entities and deposits them directly.
 *
 * <p>Runs every 10 ticks (0.5 s). Only players whose inventory is confirmed full are scanned for nearby money items.
 * The full-inventory set is refreshed every {@value #REFRESH_INTERVAL} runs (~2.5 s) to avoid iterating all online
 * players on every tick. The normal {@link MoneyPickupListener} handles the standard-inventory path; this task only
 * activates for items that the event-based listener could not reach.
 */
public class MoneyProximityPickupTask extends BukkitRunnable {

	private static final double PICKUP_RANGE     = 1.5;
	private static final int    REFRESH_INTERVAL = 5;

	private final MoneyAddon          moneyAddon;
	private final MoneyDepositService depositService;
	private final Set<UUID>           fullInventoryPlayers = new HashSet<>();
	private       int                 tickCounter;

	public MoneyProximityPickupTask(MoneyAddon moneyAddon, MoneyDepositService depositService) {
		this.moneyAddon     = moneyAddon;
		this.depositService = depositService;
	}

	@Override
	public void run() {
		if (!moneyAddon.isEnabled()) return;

		// Periodically rebuild the full-inventory set from all online players (cheap firstEmpty check)
		tickCounter++;
		if (tickCounter >= REFRESH_INTERVAL) {
			tickCounter = 0;
			refreshFullInventorySet();
		}

		if (fullInventoryPlayers.isEmpty()) return;

		Iterator<UUID> it = fullInventoryPlayers.iterator();
		while (it.hasNext()) {
			Player player = Bukkit.getPlayer(it.next());
			if (player == null || !player.isOnline()) {
				it.remove();
				continue;
			}

			// Player freed a slot since last refresh — remove from set, the event listener handles them now
			if (player.getInventory().firstEmpty() != -1) {
				it.remove();
				continue;
			}

			scanAndDeposit(player);
		}
	}

	private void refreshFullInventorySet() {
		fullInventoryPlayers.clear();

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (player.getGameMode() == GameMode.SPECTATOR) continue;
			if (DownedPlayerRegistry.isDowned(player.getUniqueId())) continue;
			if (player.getInventory().firstEmpty() != -1) continue;
			fullInventoryPlayers.add(player.getUniqueId());
		}
	}

	private void scanAndDeposit(Player player) {
		for (Entity entity : player.getNearbyEntities(PICKUP_RANGE, PICKUP_RANGE, PICKUP_RANGE)) {
			if (!(entity instanceof Item item)) continue;
			if (!item.isValid() || item.isDead()) continue;

			ItemStack stack = item.getItemStack();
			if (!MoneyItemUtil.isMoneyItem(stack)) continue;

			int perItem = MoneyItemUtil.readAmount(stack);
			if (perItem <= 0) {
				item.remove();
				continue;
			}

			int    total       = perItem * stack.getAmount();
			String variationId = MoneyItemUtil.readVariationId(stack);

			item.remove();

			depositService.deposit(player, total, variationId);
			playPickupSound(player, variationId);
		}
	}

	private void playPickupSound(Player player, String variationId) {
		if (variationId == null) return;

		MoneyItem variation = moneyAddon.getVariation(variationId);
		if (variation == null) return;

		MoneyItem.PickupSound sound = variation.getPickupSound();
		if (sound == null) return;

		sound.soundConfig().playSound(player);
	}
}
