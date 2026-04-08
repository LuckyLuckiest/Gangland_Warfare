package me.luckyraven.item.listener.money;

import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.item.money.MoneyItemUtil;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
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
	}

	private void consumeOne(Player player, ItemStack heldItem) {
		if (heldItem.getAmount() <= 1) {
			player.getInventory().setItemInMainHand(null);
			return;
		}
		heldItem.setAmount(heldItem.getAmount() - 1);
	}

}
