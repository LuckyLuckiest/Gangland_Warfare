package me.luckyraven.listener.trader;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.events.trader.TraderTradeInRequestEvent;
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
import me.luckyraven.shop.transaction.ShopTradeInService;
import me.luckyraven.shop.transaction.TradeInResult;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@CustomLog
@ListenerHandler
public class TraderTradeInListener implements Listener {

	private final UserManager<Player> userManager;
	private final TraderManager       traderManager;
	private final MoodService         moodService;
	private final ShopTradeInService  tradeInService;
	private final ShopMessageContract messages;
	private final ShopDisplayResolver displayResolver;

	public TraderTradeInListener(@Qualifier("online") UserManager<Player> userManager,
	                             TraderManager traderManager,
	                             MoodService moodService,
	                             ShopTradeInService tradeInService,
	                             ShopMessageContract messages,
	                             ShopDisplayResolver displayResolver) {
		this.userManager     = userManager;
		this.traderManager   = traderManager;
		this.moodService     = moodService;
		this.tradeInService  = tradeInService;
		this.messages        = messages;
		this.displayResolver = displayResolver;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onTradeInRequest(TraderTradeInRequestEvent event) {
		if (event.isCancelled()) {
			return;
		}

		Player       player = event.getPlayer();
		User<Player> user   = userManager.getUser(player);
		if (user == null) {
			event.setCancelled(true);
			event.setReason("User record not found.");
			return;
		}

		PaymentHandler payment = adapt(user.getEconomy());
		TradeInResult result = tradeInService.tradeIn(player, payment, event.getEntry(),
		                                              event.getBuyPrice(), event.getTradeInCredit());

		switch (result.outcome()) {
			case SUCCESS -> {
				TraderTraitDefinition trait = traderManager.resolveTrait(event.getTrader().getData());
				if (trait != null) {
					moodService.recordPurchase(event.getTrader().getData().getId(),
					                           player.getUniqueId(), trait.profile());
				}
				player.sendMessage(messages.tradeInSuccess(result.tradeInCredit(), result.moneyOwed()));

				// Trader refunds the over-valuation when the trait allows it (view already gated on the flag).
				double refund = event.getRefund();
				if (refund > 0.0) {
					try {
						payment.deposit(refund);
						player.sendMessage(messages.tradeInRefund(refund));
					} catch (PaymentException e) {
						log.warn("Failed to deposit trade-in refund for {}: {}", player.getName(), e.getMessage());
					}
				}
			}
			case INSUFFICIENT_FUNDS -> {
				event.setCancelled(true);
				String msg = messages.tradeInInsufficientFunds(result.moneyOwed());
				event.setReason(msg);
				player.sendMessage(msg);
			}
			case INVENTORY_FULL -> {
				event.setCancelled(true);
				String msg = messages.tradeInInventoryFull();
				event.setReason(msg);
				player.sendMessage(msg);
			}
			case ECONOMY_ERROR -> {
				event.setCancelled(true);
				String msg = messages.tradeInEconomyError(result.errorDetail());
				event.setReason(msg);
				player.sendMessage(msg);
				log.warn("Economy error during trade-in for {}: {}", player.getName(), result.errorDetail());
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

			@Override
			public void deposit(double amount) throws PaymentException {
				try {
					economy.deposit(amount);
				} catch (EconomyException e) {
					throw new PaymentException(e.getMessage(), e);
				}
			}
		};
	}

}
