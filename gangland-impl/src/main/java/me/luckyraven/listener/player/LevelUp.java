package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.level.GangLevelUpEvent;
import me.luckyraven.feature.level.Level;
import me.luckyraven.feature.level.UserLevelUpEvent;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

@ListenerHandler
public class LevelUp implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	public LevelUp(Gangland gangland) {
		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler
	public void onPlayerLevelUp(UserLevelUpEvent event) {
		User<?> user  = event.getUser();
		Level   level = event.getLevel();

		if (user == null) return;

		Player player = user.getUser().getPlayer();

		if (player == null) return;

		String message = MessageAddon.LEVEL_UP_PLAYER.toString();

		user.sendMessage(replacePlaceholders(message, level));
	}

	@EventHandler
	public void onGangLevelUp(GangLevelUpEvent event) {
		Gang  gang  = event.getGang();
		Level level = event.getLevel();

		if (gang == null) return;

		List<Player> onlinePlayers = gang.getOnlineMembers(gangland.getInitializer().getUserManager())
				.stream().map(User::getUser).toList();

		for (Player player : onlinePlayers) {
			User<Player> onlineUser = userManager.getUser(player);

			String message = MessageAddon.LEVEL_UP_GANG.toString();

			onlineUser.sendMessage(replacePlaceholders(message, level));
		}
	}

	private String replacePlaceholders(String message, Level level) {
		return message.replace("%level%", String.valueOf(level.getLevelValue()))
					  .replace("%next_level%", String.valueOf(level.nextLevel()))
					  .replace("%max_level%", String.valueOf(level.getMaxLevel()));
	}

}
