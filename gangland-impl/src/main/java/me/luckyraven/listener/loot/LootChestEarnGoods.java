package me.luckyraven.listener.loot;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.level.Level;
import me.luckyraven.feature.level.LevelUpEvent;
import me.luckyraven.feature.level.UserLevelUpEvent;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.loot.data.LootChestData;
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
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
public class LootChestEarnGoods implements Listener {

	private final Random                     random;
	private final UserManager<Player>        userManager;
	private final Map<Player, LootChestData> openedLootChests;

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

		if (openedLootChests.containsKey(player)) return;

		// add experience and money
		int    money = random.nextInt(1_000);
		double exp   = random.nextDouble(100D);

		// deposit money
		user.getEconomy().deposit(money);

		// add experience
		Level level = user.getLevel();

		LevelUpEvent levelUpEvent = new UserLevelUpEvent(user, level);
		level.addExperience(exp, levelUpEvent);

		player.sendMessage(ChatUtil.prefixMessage("Opened a loot chest and earned:"));
		player.sendMessage(ChatUtil.color(String.format("&a%s +%d", SettingAddon.getMoneySymbol(), money)));
		player.sendMessage(ChatUtil.color(String.format("&aXP +%.2f", exp)));

		openedLootChests.put(player, session.getChestData());
	}

	@EventHandler
	public void onLootSessionEnd(LootChestCooldownCompleteEvent event) {
		LootChestData chestData = event.getLootChestData();

		openedLootChests.entrySet().removeIf(entry -> {
			var lootChestData = entry.getValue();
			var checkedId     = lootChestData.getId();
			var currentId     = chestData.getId();

			return checkedId.equals(currentId);
		});
	}

}
