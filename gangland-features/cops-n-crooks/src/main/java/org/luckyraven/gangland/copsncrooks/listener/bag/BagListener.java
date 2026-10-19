package org.luckyraven.gangland.copsncrooks.listener.bag;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.copsncrooks.bag.BagEnv;
import org.luckyraven.gangland.copsncrooks.bag.BagService;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.gangland.data.economy.HotCash;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.math.BigDecimal;

/**
 * Wires the bag to the server: wanted endings, quit, the join settle and the hot-bill pickup star.
 *
 * @since 0.20.0
 */
@ListenerHandler
public class BagListener implements Listener {

	private final BagService service;
	private final BagEnv     env;

	public BagListener(BagService service, BagEnv env) {
		this.service = service;
		this.env     = env;
	}

	/** HIGH: after 0.21's report at NORMAL, before its Getaway.Line at MONITOR (D-W2-28). */
	@EventHandler(priority = EventPriority.HIGH)
	public void onWantedEnd(WantedEndEvent event) {
		Player player = event.getPlayer();
		if (player == null) {
			return;
		}
		service.end(player, event.getCause());
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onQuit(PlayerQuitEvent event) {
		service.onQuit(event.getPlayer());
	}

	/** Never PlayerJoinEvent: wanted is restored after the async user load (D-W2-31). */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onUserData(UserDataInitEvent event) {
		Player player = event.getPlayer();
		if (player == null) {
			return;
		}
		env.runTask(() -> {
			if (player.isOnline()) {
				service.settle(player);
			}
		});
	}

	/** NORMAL, before the core HIGH pickup listener; never cancels. */
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPickup(EntityPickupItemEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		if (env.isHotMoney(event.getItem().getItemStack()) && env.wantedLevel(player) == 0) {
			hotStar(player);
		}
	}

	/** LOWEST: a hopper never launders a spilled hot bill (C-40). */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onHopperPickup(InventoryPickupItemEvent event) {
		if (env.isHotMoney(event.getItem().getItemStack())) {
			event.setCancelled(true);
		}
	}

	/**
	 * LOW, before the core HIGH redeem; no ignoreCancelled because air clicks are pre-cancelled. A held hot bill is
	 * routed through the bag instead of the wallet (C-40).
	 */
	@EventHandler(priority = EventPriority.LOW)
	public void onRedeem(PlayerInteractEvent event) {
		if (event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		ItemStack item = event.getItem();
		if (item == null || !env.isHotMoney(item)) {
			return;
		}

		Player    player   = event.getPlayer();
		ItemStack snapshot = item.clone();
		int       value    = env.billValue(item);
		item.setType(Material.AIR);
		event.setCancelled(true);
		if (env.wantedLevel(player) == 0) {
			hotStar(player);
		}

		HotCash.Take result = service.take(player, BigDecimal.valueOf(value), "redeem");
		if (result == HotCash.Take.FULL) {
			player.getInventory().setItemInMainHand(snapshot);
			return;
		}
		if (result == HotCash.Take.WALLET) {
			env.deposit(player, BigDecimal.valueOf(value));
		}
		int left = snapshot.getAmount() - 1;
		if (left <= 0) {
			player.getInventory().setItemInMainHand(null);
		} else {
			snapshot.setAmount(left);
			player.getInventory().setItemInMainHand(snapshot);
		}
	}

	private void hotStar(Player player) {
		env.raiseHotStar(player);
		player.sendMessage(service.hotLine());
		env.debug("BAG " + player.getName() + " hot star");
	}
}
