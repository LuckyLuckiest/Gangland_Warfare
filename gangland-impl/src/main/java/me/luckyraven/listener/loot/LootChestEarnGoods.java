package me.luckyraven.listener.loot;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.level.Level;
import me.luckyraven.feature.level.LevelUpEvent;
import me.luckyraven.feature.level.UserLevelUpEvent;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.loot.data.LootChestSession;
import me.luckyraven.loot.events.lootchest.LootChestCooldownCompleteEvent;
import me.luckyraven.loot.events.lootchest.LootChestOpenEvent;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
public class LootChestEarnGoods implements Listener {

	private final Random                 random;
	private final UserManager<Player>    userManager;
	private final Map<Player, Set<UUID>> openedLootChests;

	public LootChestEarnGoods(Gangland gangland) {
		this.random           = new Random();
		this.userManager      = gangland.getInitializer().getUserManager();
		this.openedLootChests = new ConcurrentHashMap<>();
	}

	@EventHandler
	public void onLootChestOpen(LootChestOpenEvent event) {
		LootChestSession session = event.getLootChestSession();
		Player           player  = session.getPlayer();
		User<Player>     user    = userManager.getUser(player);
		UUID             chestId = session.getChestData().getId();

		Set<UUID> playerOpenedChests = openedLootChests.get(player);
		if (playerOpenedChests != null && playerOpenedChests.contains(chestId)) {
			return;
		}

		openedLootChests.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(chestId);

		// add experience and money
		double money = random.nextDouble(SettingAddon.getLootChestRewardMoneyMinimum(),
										 SettingAddon.getLootChestRewardMoneyMaximum());

		double exp = random.nextDouble(SettingAddon.getLootChestRewardExperienceMinimum(),
									   SettingAddon.getLootChestRewardExperienceMaximum());

		// deposit money
		user.getEconomy().deposit(money);

		// add experience
		Level level = user.getLevel();

		LevelUpEvent levelUpEvent = new UserLevelUpEvent(user, level);
		level.addExperience(exp, levelUpEvent);

		player.sendMessage(ChatUtil.prefixMessage("Opened a loot chest and earned:"));
		player.sendMessage(ChatUtil.color(
				String.format("&a%s +%s", SettingAddon.getMoneySymbol(), SettingAddon.formatDouble(money))));
		player.sendMessage(ChatUtil.color(String.format("&aXP +%.2f", exp)));
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
