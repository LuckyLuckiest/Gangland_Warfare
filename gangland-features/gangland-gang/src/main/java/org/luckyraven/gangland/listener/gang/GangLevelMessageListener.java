package org.luckyraven.gangland.listener.gang;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangLevelUpEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.List;

/**
 * Moved out of gangland-impl's {@code LevelUpListener} (WS5 G2 step 15c) — {@link GangLevelUpEvent} carries a
 * {@link Gang}, so impl can no longer name it; {@code onPlayerLevelUp} stayed behind, unchanged.
 */
@ListenerHandler
public class GangLevelMessageListener implements Listener {

	private final UserManager<Player> userManager;

	public GangLevelMessageListener(@Qualifier("online") UserManager<Player> userManager) {
		this.userManager = userManager;
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
