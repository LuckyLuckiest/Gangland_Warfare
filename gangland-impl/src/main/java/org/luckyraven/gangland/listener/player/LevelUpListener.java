package org.luckyraven.gangland.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.bean.Qualifier;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.events.gang.GangLevelUpEvent;
import org.luckyraven.gangland.events.user.UserLevelUpEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.user.Level;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

import java.util.List;

@ListenerHandler
public class LevelUpListener implements Listener {

	private final UserManager<Player> userManager;

	public LevelUpListener(@Qualifier("online") UserManager<Player> userManager) {
		this.userManager = userManager;
	}

	@EventHandler
	public void onPlayerLevelUp(UserLevelUpEvent event) {
		User<?> user  = event.getUser();
		Level   level = event.getLevel();

		if (user == null) return;

		Player player = user.getUser().getPlayer();

		if (player == null) return;

		String message = Messages.LEVEL_UP_PLAYER.toString();

		user.sendMessage(replacePlaceholders(message, level));
	}

	@EventHandler
	public void onGangLevelUp(GangLevelUpEvent event) {
		Gang  gang  = event.getGang();
		Level level = event.getLevel();

		if (gang == null) return;

		List<Player> onlinePlayers = gang.getOnlineMembers(userManager::getUser)
				.stream().map(User::getUser).toList();

		for (Player player : onlinePlayers) {
			User<Player> onlineUser = userManager.getUser(player);

			String message = Messages.LEVEL_UP_GANG.toString();

			if (onlineUser != null) onlineUser.sendMessage(replacePlaceholders(message, level));
		}
	}

	private String replacePlaceholders(String message, Level level) {
		return message.replace("%level%", String.valueOf(level.getLevelValue()))
		              .replace("%next_level%", String.valueOf(level.nextLevel()))
		              .replace("%max_level%", String.valueOf(level.getMaxLevel()));
	}

}
