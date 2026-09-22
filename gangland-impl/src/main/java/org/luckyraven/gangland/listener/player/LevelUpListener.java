package org.luckyraven.gangland.listener.player;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.events.user.UserLevelUpEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;

/**
 * {@code onGangLevelUp} moved to the gang module's {@code GangLevelMessageListener} (WS5 G2 step 15c) —
 * {@code GangLevelUpEvent} is module-owned (it carries a {@code Gang}), so impl can no longer name it.
 */
@ListenerHandler
public class LevelUpListener implements Listener {

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

	private String replacePlaceholders(String message, Level level) {
		return message.replace("%level%", String.valueOf(level.getLevelValue()))
		              .replace("%next_level%", String.valueOf(level.nextLevel()))
		              .replace("%max_level%", String.valueOf(level.getMaxLevel()));
	}

}
