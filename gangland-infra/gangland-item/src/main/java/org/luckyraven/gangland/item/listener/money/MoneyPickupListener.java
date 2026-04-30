package org.luckyraven.gangland.item.listener.money;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.core.bean.autowire.AutowireTarget;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.gangland.item.money.MoneyDepositService;
import org.luckyraven.gangland.item.money.MoneyItem;
import org.luckyraven.gangland.item.money.MoneyItemUtil;

/**
 * Intercepts pickups of cash items: cancels the inventory transfer entirely, deposits the rolled amount into the
 * player's balance via {@link MoneyDepositService}, and removes the dropped item entity.
 */
@ListenerHandler
@AutowireTarget({MoneyAddon.class, MoneyDepositService.class})
public class MoneyPickupListener implements Listener {

	private final MoneyAddon          moneyAddon;
	private final MoneyDepositService depositService;

	public MoneyPickupListener(MoneyAddon moneyAddon, MoneyDepositService depositService) {
		this.moneyAddon     = moneyAddon;
		this.depositService = depositService;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onMoneyPickup(EntityPickupItemEvent event) {
		if (!moneyAddon.isEnabled()) return;
		if (!(event.getEntity() instanceof Player player)) return;

		ItemStack stack = event.getItem().getItemStack();
		if (!MoneyItemUtil.isMoneyItem(stack)) return;

		int perItem = MoneyItemUtil.readAmount(stack);
		if (perItem <= 0) {
			// Tagged but corrupted; remove silently to avoid leaving garbage on the ground
			event.setCancelled(true);
			event.getItem().remove();
			return;
		}

		// Stack count matters: a stack of 5 small bills worth $10 each deposits $50, not $10.
		int    total       = perItem * stack.getAmount();
		String variationId = MoneyItemUtil.readVariationId(stack);

		event.setCancelled(true);
		event.getItem().remove();

		depositService.deposit(player, total, variationId);
		playPickupSound(player, variationId);
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
