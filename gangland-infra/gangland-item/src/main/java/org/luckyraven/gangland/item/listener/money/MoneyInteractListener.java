package org.luckyraven.gangland.item.listener.money;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.core.bean.autowire.AutowireTarget;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.item.money.MoneyItem;
import org.luckyraven.gangland.item.money.MoneyItemUtil;

/**
 * Right-click flow for cash items: while a player is holding a money item (creative drop, admin {@code /give}, etc.),
 * right-clicking anything consumes one cash item from the held stack and deposits its NBT-stored amount.
 */
@ListenerHandler
@AutowireTarget({MoneyAddon.class, MoneyDepositService.class})
public class MoneyInteractListener implements Listener {

	private final MoneyAddon          moneyAddon;
	private final MoneyDepositService depositService;

	public MoneyInteractListener(MoneyAddon moneyAddon, MoneyDepositService depositService) {
		this.moneyAddon     = moneyAddon;
		this.depositService = depositService;
	}

	// NOTE: do NOT use ignoreCancelled = true here. Bukkit cancels PlayerInteractEvent by default for RIGHT_CLICK_AIR
	// when nothing is hit, so an ignoreCancelled listener would never receive air clicks — exactly the case we want
	// to handle for picking up money by right-clicking an empty hand-target.
	@EventHandler(priority = EventPriority.HIGH)
	public void onMoneyInteract(PlayerInteractEvent event) {
		if (!moneyAddon.isEnabled()) return;
		if (event.getHand() != EquipmentSlot.HAND) return;

		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

		ItemStack heldItem = event.getItem();
		if (!MoneyItemUtil.isMoneyItem(heldItem)) return;

		int amount = MoneyItemUtil.readAmount(heldItem);
		if (amount <= 0) return;

		String variationId = MoneyItemUtil.readVariationId(heldItem);

		event.setCancelled(true);
		consumeOne(event.getPlayer(), heldItem);

		depositService.deposit(event.getPlayer(), amount, variationId);
		playPickupSound(event.getPlayer(), variationId);
	}

	private void playPickupSound(Player player, String variationId) {
		if (variationId == null) return;

		MoneyItem variation = moneyAddon.getVariation(variationId);
		if (variation == null) return;

		MoneyItem.PickupSound sound = variation.getPickupSound();
		if (sound == null) return;

		sound.soundConfig().playSound(player);
	}

	private void consumeOne(Player player, ItemStack heldItem) {
		if (heldItem.getAmount() <= 1) {
			player.getInventory().setItemInMainHand(null);
			return;
		}
		heldItem.setAmount(heldItem.getAmount() - 1);
	}

}
