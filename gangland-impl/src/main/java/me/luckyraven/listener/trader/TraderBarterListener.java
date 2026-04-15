package me.luckyraven.listener.trader;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.events.trader.TraderBarterEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.shop.ShopItemEntry;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.message.ShopMessageContract;
import me.luckyraven.shop.transaction.BarterResult;
import me.luckyraven.shop.transaction.ShopBarterService;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

@CustomLog
@ListenerHandler
public class TraderBarterListener implements Listener {

	private final TraderManager       traderManager;
	private final MoodService         moodService;
	private final ShopBarterService   barterService;
	private final ShopMessageContract messages;
	private final ShopDisplayResolver displayResolver;

	public TraderBarterListener(TraderManager traderManager,
	                            MoodService moodService,
	                            ShopBarterService barterService,
	                            ShopMessageContract messages,
	                            ShopDisplayResolver displayResolver) {
		this.traderManager   = traderManager;
		this.moodService     = moodService;
		this.barterService   = barterService;
		this.messages        = messages;
		this.displayResolver = displayResolver;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onBarter(TraderBarterEvent event) {
		if (event.isCancelled()) return;

		Player        player = event.getPlayer();
		ShopItemEntry entry  = event.getEntry();
		BarterResult  result = barterService.barter(player, entry);

		switch (result.outcome()) {
			case SUCCESS -> {
				TraderTraitDefinition trait = traderManager.resolveTrait(event.getTrader().getData());
				if (trait != null) {
					moodService.recordPurchase(event.getTrader().getData().getId(),
					                           player.getUniqueId(), trait.profile());
				}
				player.sendMessage(messages.barterSuccess(displayResolver.cleanDisplayName(result.delivery())));
			}
			case NOT_BARTERABLE -> {
				event.setCancelled(true);
				String msg = messages.barterNotAllowed();
				event.setReason(msg);
				player.sendMessage(msg);
			}
			case MISSING_ITEMS -> {
				event.setCancelled(true);
				ItemStack cost = entry.getTradeFor();
				String    msg  = messages.barterMissingItems(cost.getAmount(), cost.getType().name());
				event.setReason(msg);
				player.sendMessage(msg);
			}
		}
	}

}
