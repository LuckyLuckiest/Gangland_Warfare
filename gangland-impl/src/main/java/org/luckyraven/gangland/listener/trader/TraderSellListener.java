package org.luckyraven.gangland.listener.trader;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.events.trader.TraderSellRequestEvent;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderManager;
import org.luckyraven.gangland.copsncrooks.npc.trader.config.TraderSettings;
import org.luckyraven.gangland.copsncrooks.npc.trader.mood.MoodService;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.economy.EconomyHandler;
import org.luckyraven.gangland.economy.exception.EconomyException;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.shop.message.ShopMessageContract;
import org.luckyraven.gangland.shop.transaction.PaymentException;
import org.luckyraven.gangland.shop.transaction.PaymentHandler;
import org.luckyraven.gangland.shop.transaction.SellResult;
import org.luckyraven.gangland.shop.transaction.ShopSellService;

import java.math.BigDecimal;

@CustomLog
@ListenerHandler
public class TraderSellListener implements Listener {

	private final UserManager<Player> userManager;
	private final TraderManager       traderManager;
	private final MoodService         moodService;
	private final TraderSettings      traderSettings;
	private final ShopSellService     sellService;
	private final ShopMessageContract messages;

	public TraderSellListener(@Qualifier("online") UserManager<Player> userManager,
	                          TraderManager traderManager,
	                          MoodService moodService,
	                          TraderSettings traderSettings,
	                          ShopSellService sellService,
	                          ShopMessageContract messages) {
		this.userManager    = userManager;
		this.traderManager  = traderManager;
		this.moodService    = moodService;
		this.traderSettings = traderSettings;
		this.sellService    = sellService;
		this.messages       = messages;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onSellRequest(TraderSellRequestEvent event) {
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
		SellResult     result  = sellService.sell(player, payment, event.getOfferedItems(), event.getFinalOffer());

		switch (result.outcome()) {
			case SUCCESS -> {
				if (traderManager.resolveTrait(event.getTrader().getData()) != null) {
					moodService.recordSale(event.getTrader().getData().getId(), player.getUniqueId(),
					                       traderSettings.getMoodPerSale());
				}
				player.sendMessage(messages.sellSuccess(result.totalPaid(), result.itemsSold()));
			}
			case NOTHING_VALUED -> {
				event.setCancelled(true);
				String msg = messages.sellNothingValued();
				event.setReason(msg);
				player.sendMessage(msg);
			}
			case ECONOMY_ERROR -> {
				event.setCancelled(true);
				String msg = messages.sellEconomyError(result.errorDetail());
				event.setReason(msg);
				player.sendMessage(msg);
				log.warn("Economy error during sell for {}: {}", player.getName(), result.errorDetail());
			}
		}
	}

	private PaymentHandler adapt(EconomyHandler economy) {
		return new PaymentHandler() {
			@Override
			public BigDecimal getBalance() {
				return economy.getAmount();
			}

			@Override
			public void withdraw(BigDecimal amount) throws PaymentException {
				try {
					economy.withdrawAmount(amount);
				} catch (EconomyException e) {
					throw new PaymentException(e.getMessage(), e);
				}
			}

			@Override
			public void deposit(BigDecimal amount) throws PaymentException {
				try {
					economy.depositAmount(amount);
				} catch (EconomyException e) {
					throw new PaymentException(e.getMessage(), e);
				}
			}
		};
	}

}
