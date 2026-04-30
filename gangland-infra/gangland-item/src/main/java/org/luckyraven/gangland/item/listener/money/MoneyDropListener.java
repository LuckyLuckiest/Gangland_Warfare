package org.luckyraven.gangland.item.listener.money;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.core.bean.autowire.AutowireTarget;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.item.money.*;

/**
 * Spawns cash drop items at the death location of mobs, cops, civilians, and players.
 *
 * <p>The dead entity is classified via {@link MoneyDropClassifier} into a {@link MoneyDropContext}; the addon then
 * picks a variation according to the per-source weights and rolls an amount inside the variation's range. For player
 * deaths with {@code Scale_With_Balance} enabled, the rolled amount is augmented with a fraction of the dead player's
 * balance (clamped to the variation's max).
 */
@ListenerHandler
@AutowireTarget({MoneyAddon.class, MoneyDepositService.class, MoneyDropClassifier.class})
public class MoneyDropListener implements Listener {

	private final MoneyAddon          moneyAddon;
	private final MoneyDepositService depositService;
	private final MoneyDropClassifier classifier;

	public MoneyDropListener(MoneyAddon moneyAddon, MoneyDepositService depositService,
	                         MoneyDropClassifier classifier) {
		this.moneyAddon     = moneyAddon;
		this.depositService = depositService;
		this.classifier     = classifier;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEntityDeath(EntityDeathEvent event) {
		if (!moneyAddon.isEnabled()) return;
		if (event instanceof PlayerDeathEvent) return; // handled by the player-specific overload below

		LivingEntity     entity  = event.getEntity();
		MoneyDropContext context = classifier.classify(entity);

		dropForContext(entity, context, null);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerDeath(PlayerDeathEvent event) {
		if (!moneyAddon.isEnabled()) return;

		Player player = event.getEntity();
		dropForContext(player, MoneyDropContext.PLAYER, player);
	}

	private void dropForContext(LivingEntity origin, MoneyDropContext context, Player playerOrNull) {
		MoneyAddon.DropSource source = moneyAddon.getDropSource(context);
		if (source == null || !source.enabled()) return;

		MoneyItem variation = moneyAddon.rollVariation(context);
		if (variation == null) return;

		int amount = moneyAddon.rollAmount(variation);

		if (playerOrNull != null && source.scaleWithBalance() && source.balanceFraction() > 0) {
			double balance = depositService.getBalance(playerOrNull);
			int    bonus   = (int) Math.floor(balance * source.balanceFraction());
			if (bonus > 0) {
				amount = Math.min(variation.getMax(), amount + bonus);
			}
		}

		if (amount <= 0) return;

		World     world    = origin.getWorld();
		Location  location = origin.getLocation();
		ItemStack item     = MoneyItemFactory.build(variation, amount, depositService);
		world.dropItemNaturally(location, item);
	}

}
