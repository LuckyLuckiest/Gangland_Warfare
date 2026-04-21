package me.luckyraven.listener.player;

import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.data.account.Level;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.events.gang.GangLevelUpEvent;
import me.luckyraven.events.user.UserLevelUpEvent;
import me.luckyraven.file.configuration.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

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

		List<Player> onlinePlayers = gang.getOnlineMembers(userManager)
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
