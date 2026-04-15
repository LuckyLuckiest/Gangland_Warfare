package me.luckyraven.listener.trader;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.events.trader.TraderBuyRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.economy.EconomyException;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.shop.message.ShopMessageContract;
import me.luckyraven.shop.transaction.PaymentException;
import me.luckyraven.shop.transaction.PaymentHandler;
import me.luckyraven.shop.transaction.PurchaseResult;
import me.luckyraven.shop.transaction.ShopPurchaseService;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@CustomLog
@ListenerHandler
public class TraderBuyListener implements Listener {

	private final UserManager<Player> userManager;
	private final TraderManager       traderManager;
	private final MoodService         moodService;
	private final ShopPurchaseService purchaseService;
	private final ShopMessageContract messages;
	private final ShopDisplayResolver displayResolver;

	public TraderBuyListener(@Qualifier("online") UserManager<Player> userManager,
	                         TraderManager traderManager,
	                         MoodService moodService,
	                         ShopPurchaseService purchaseService,
	                         ShopMessageContract messages,
	                         ShopDisplayResolver displayResolver) {
		this.userManager     = userManager;
		this.traderManager   = traderManager;
		this.moodService     = moodService;
		this.purchaseService = purchaseService;
		this.messages        = messages;
		this.displayResolver = displayResolver;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onBuyRequest(TraderBuyRequestEvent event) {
		if (event.isCancelled()) return;

		Player       player = event.getPlayer();
		User<Player> user   = userManager.getUser(player);
		if (user == null) {
			event.setCancelled(true);
			event.setReason("User record not found.");
			return;
		}

		double         price   = event.getFinalPrice();
		PaymentHandler payment = adapt(user.getEconomy());
		PurchaseResult result  = purchaseService.purchase(player, payment, event.getEntry(), price);

		switch (result.outcome()) {
			case SUCCESS -> {
				TraderTraitDefinition trait = traderManager.resolveTrait(event.getTrader().getData());
				if (trait != null) {
					moodService.recordPurchase(event.getTrader().getData().getId(),
					                           player.getUniqueId(), trait.profile());
				}
				player.sendMessage(messages.purchaseSuccess(displayResolver.cleanDisplayName(result.delivery()),
				                                            result.pricePaid()));
			}
			case INSUFFICIENT_FUNDS -> {
				event.setCancelled(true);
				String msg = messages.purchaseInsufficientFunds(price);
				event.setReason(msg);
				player.sendMessage(msg);
			}
			case ECONOMY_ERROR -> {
				event.setCancelled(true);
				String msg = messages.purchaseEconomyError(result.errorDetail());
				event.setReason(msg);
				player.sendMessage(msg);
				log.warn("Economy error during purchase for {}: {}", player.getName(), result.errorDetail());
			}
			case INVENTORY_FULL -> {
				// Service drops leftovers naturally; kept for exhaustiveness.
			}
		}
	}

	private PaymentHandler adapt(EconomyHandler economy) {
		return new PaymentHandler() {
			@Override
			public double getBalance() {
				return economy.getBalance();
			}

			@Override
			public void withdraw(double amount) throws PaymentException {
				try {
					economy.withdraw(amount);
				} catch (EconomyException e) {
					throw new PaymentException(e.getMessage(), e);
				}
			}
		};
	}

}
