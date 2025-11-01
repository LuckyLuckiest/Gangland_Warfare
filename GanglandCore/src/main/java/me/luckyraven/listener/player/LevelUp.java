package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.user.User;
import me.luckyraven.feature.level.LevelUpEvent;
import me.luckyraven.listener.ListenerHandler;
import me.luckyraven.util.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;

@ListenerHandler
public class LevelUp implements Listener {

	private final Gangland gangland;

	public LevelUp(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onPlayerLevelUp(LevelUpEvent event) {
		User<?> user = event.getUser();

		if (user != null) {
			Player player = user.getUser().getPlayer();

			if (player == null) return;

			player.sendMessage(
					ChatUtil.prefixMessage("You have leveled up to level &b" + event.getLevel().nextLevel() + "&7."));
		}

		Gang gang = event.getGang();

		if (gang != null) {
			List<Player> onlinePlayers = gang.getOnlineMembers(gangland.getInitializer().getUserManager())
					.stream().map(User::getUser).toList();

			for (Player player : onlinePlayers)
				player.sendMessage(ChatUtil.prefixMessage(
						"The gang has leveled up to level &b" + event.getLevel().nextLevel() + "&7."));
		}

	}

}
