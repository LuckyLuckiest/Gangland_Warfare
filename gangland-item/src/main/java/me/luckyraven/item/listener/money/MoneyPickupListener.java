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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

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

		int amount = MoneyItemUtil.readAmount(stack);
		if (amount <= 0) {
			// Tagged but corrupted; remove silently to avoid leaving garbage on the ground
			event.setCancelled(true);
			event.getItem().remove();
			return;
		}

		String variationId = MoneyItemUtil.readVariationId(stack);

		event.setCancelled(true);
		event.getItem().remove();

		depositService.deposit(player, amount, variationId);
	}

}
