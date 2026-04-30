package org.luckyraven.gangland.listener.loot;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.economy.Currency;
import org.luckyraven.gangland.events.user.UserLevelUpEvent;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.events.level.LevelUpEvent;
import org.luckyraven.gangland.gang.user.Level;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.lootchest.data.LootChestSession;
import org.luckyraven.gangland.lootchest.events.lootchest.LootChestCooldownCompleteEvent;
import org.luckyraven.gangland.lootchest.events.lootchest.LootChestOpenEvent;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
public class LootChestEarnGoodsListener implements Listener {

	private final Random                 random;
	private final UserManager<Player>    userManager;
	private final Map<Player, Set<UUID>> openedLootChests;

	public LootChestEarnGoodsListener(@Qualifier("online") UserManager<Player> userManager) {
		this.random           = new Random();
		this.userManager      = userManager;
		this.openedLootChests = new ConcurrentHashMap<>();
	}

	@EventHandler
	public void onLootChestOpen(LootChestOpenEvent event) {
		LootChestSession session = event.getLootChestSession();
		Player           player  = session.getPlayer();
		User<Player>     user    = userManager.getUser(player);

		if (user == null) return;

		UUID chestId = session.getChestData().getId();

		Set<UUID> playerOpenedChests = openedLootChests.get(player);
		if (playerOpenedChests != null && playerOpenedChests.contains(chestId)) {
			return;
		}

		openedLootChests.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(chestId);

		// add experience and money
		double money = random.nextDouble(Settings.getLootChestRewardMoneyMinimum(),
		                                 Settings.getLootChestRewardMoneyMaximum());

		double exp = random.nextDouble(Settings.getLootChestRewardExperienceMinimum(),
		                               Settings.getLootChestRewardExperienceMaximum());

		// deposit money
		user.getEconomy().depositAmount(Currency.of(money));

		// add experience
		Level level = user.getLevel();

		LevelUpEvent levelUpEvent = new UserLevelUpEvent(false, user, level);
		level.addExperience(exp, levelUpEvent);

		player.sendMessage(GanglandChatUtil.prefixMessage("Opened a loot chest and earned:"));
		player.sendMessage(GanglandChatUtil.color(
				String.format("&c-> &a%s +%s", Settings.getMoneySymbol(), Settings.formatDouble(money))));
		player.sendMessage(GanglandChatUtil.color(String.format("&c-> &aXP +%.2f", exp)));

		for (String command : Settings.getLootChestRewardCommands()) {
			if (command.isEmpty()) continue;
			if (command.startsWith("/")) command = command.substring(1);

			player.performCommand(command);
		}
	}

	@EventHandler
	public void onLootSessionEnd(LootChestCooldownCompleteEvent event) {
		var chestData = event.getLootChestData();
		var chestId   = chestData.getId();

		// remove this chest ID from all players opened sets
		openedLootChests.values().forEach(chestIds -> chestIds.remove(chestId));

		// clean up empty sets
		openedLootChests.entrySet().removeIf(entry -> entry.getValue().isEmpty());
	}

}
