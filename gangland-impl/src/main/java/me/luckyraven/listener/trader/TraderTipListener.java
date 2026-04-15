package me.luckyraven.listener.trader;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.events.trader.TraderTipEvent;
import me.luckyraven.copsncrooks.npc.trader.TraderManager;
import me.luckyraven.copsncrooks.npc.trader.message.TraderMessageContract;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.economy.EconomyException;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.util.autowire.bean.Qualifier;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@CustomLog
@ListenerHandler
public class TraderTipListener implements Listener {

	private final UserManager<Player>   userManager;
	private final TraderManager         traderManager;
	private final MoodService           moodService;
	private final TraderMessageContract messages;

	public TraderTipListener(@Qualifier("online") UserManager<Player> userManager,
	                         TraderManager traderManager,
	                         MoodService moodService,
	                         TraderMessageContract messages) {
		this.userManager   = userManager;
		this.traderManager = traderManager;
		this.moodService   = moodService;
		this.messages      = messages;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onTip(TraderTipEvent event) {
		if (event.isCancelled()) return;

		Player       player = event.getPlayer();
		User<Player> user   = userManager.getUser(player);
		if (user == null) {
			event.setCancelled(true);
			return;
		}

		EconomyHandler economy = user.getEconomy();
		if (economy.getBalance() < event.getAmount()) {
			event.setCancelled(true);
			player.sendMessage(messages.tipInsufficientFunds(event.getAmount()));
			return;
		}

		try {
			economy.withdraw(event.getAmount());
		} catch (EconomyException e) {
			event.setCancelled(true);
			log.warn("Economy error during tip for {}: {}", player.getName(), e.getMessage());
			return;
		}

		TraderTraitDefinition trait = traderManager.resolveTrait(event.getTrader().getData());
		if (trait != null) {
			moodService.recordTip(event.getTrader().getData().getId(), player.getUniqueId(),
			                      event.getAmount(), trait.profile());
		}

		player.sendMessage(messages.tipSuccess(event.getAmount()));
	}

}
