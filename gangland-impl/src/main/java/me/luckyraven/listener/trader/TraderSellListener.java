package me.luckyraven.listener.trader;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.events.trader.TraderSellRequestEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.item.ItemSerializerRegistry;
import me.luckyraven.market.bank.EconomyException;
import me.luckyraven.market.bank.EconomyHandler;
import me.luckyraven.market.ledger.TransactionContext;
import me.luckyraven.market.ledger.TransactionDirection;
import me.luckyraven.market.ledger.TransactionLedger;
import me.luckyraven.shop.message.ShopMessageContract;
import me.luckyraven.shop.transaction.PaymentException;
import me.luckyraven.shop.transaction.PaymentHandler;
import me.luckyraven.shop.transaction.SellResult;
import me.luckyraven.shop.transaction.ShopSellService;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

@CustomLog
@ListenerHandler
public class TraderSellListener implements Listener {

	private final UserManager<Player>    userManager;
	private final TraderManager          traderManager;
	private final MoodService            moodService;
	private final TraderSettings         traderSettings;
	private final ShopSellService        sellService;
	private final ShopMessageContract    messages;
	private final TransactionLedger      transactionLedger;
	private final ItemSerializerRegistry itemSerializerRegistry;

	public TraderSellListener(@Qualifier("online") UserManager<Player> userManager,
	                          TraderManager traderManager,
	                          MoodService moodService,
	                          TraderSettings traderSettings,
	                          ShopSellService sellService,
	                          ShopMessageContract messages,
	                          TransactionLedger transactionLedger,
	                          ItemSerializerRegistry itemSerializerRegistry) {
		this.userManager            = userManager;
		this.traderManager          = traderManager;
		this.moodService            = moodService;
		this.traderSettings         = traderSettings;
		this.sellService            = sellService;
		this.messages               = messages;
		this.transactionLedger      = transactionLedger;
		this.itemSerializerRegistry = itemSerializerRegistry;
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
				TraderTraitDefinition trait = traderManager.resolveTrait(event.getTrader().getData());
				if (trait != null) {
					moodService.recordSale(event.getTrader().getData().getId(), player.getUniqueId(),
					                       traderSettings.getMoodPerSale());
				}
				recordSellLedger(event, player, result, trait);
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

	private void recordSellLedger(TraderSellRequestEvent event,
	                              Player player,
	                              SellResult result,
	                              TraderTraitDefinition trait) {
		boolean marketLinked = trait == null || trait.profile().marketLinked();

		// Aggregate offered items by their canonical id (via ItemSerializerRegistry) so that e.g. ammunition vs.
		// raw materials land on their logical market key rather than their low-level Material. Price is distributed
		// proportionally to each id's quantity share so the per-item unit price is a fair average.
		Map<String, Integer> byId = new HashMap<>();
		for (ItemStack item : event.getOfferedItems()) {
			if (item == null) {
				continue;
			}
			String id = itemSerializerRegistry.serialize(item);
			if (id == null) {
				continue;
			}
			byId.merge(id, item.getAmount(), Integer::sum);
		}

		int    totalSold = Math.max(1, result.itemsSold());
		double totalPaid = result.totalPaid();

		if (byId.isEmpty()) {
			// Fallback when the event somehow yields no items — still record a summary row.
			transactionLedger.append(new TransactionContext(player.getUniqueId(),
			                                                event.getTrader().getData().getId(), "bundle", totalSold,
			                                                totalPaid / totalSold, TransactionDirection.SELL,
			                                                marketLinked));
			return;
		}

		for (Map.Entry<String, Integer> entry : byId.entrySet()) {
			int    qty   = entry.getValue();
			double share = totalPaid * ((double) qty / totalSold);
			transactionLedger.append(new TransactionContext(player.getUniqueId(),
			                                                event.getTrader().getData().getId(), entry.getKey(), qty,
			                                                qty == 0 ? 0D : share / qty, TransactionDirection.SELL,
			                                                marketLinked));
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
