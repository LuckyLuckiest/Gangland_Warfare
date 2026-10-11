package org.luckyraven.gangland.listener.gang;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Routes a toggled player's public chat to his gang. The chat event is async, so it reads only the concurrent toggle
 * set there and cancels; the gang lookup and the delivery run on the main thread.
 */
@ListenerHandler
public class GangChatListener implements Listener {

	private final JavaPlugin          plugin;
	private final GangChatToggles     channel;
	private final UserManager<Player> userManager;
	private final GangMessages        messages;

	public GangChatListener(JavaPlugin plugin, GangChatToggles channel,
	                        @Qualifier("online") UserManager<Player> userManager, GangMessages messages) {
		this.plugin      = plugin;
		this.channel     = channel;
		this.userManager = userManager;
		this.messages    = messages;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		if (!channel.isOn(player.getUniqueId())) {
			return;
		}

		// Cancelled here (async, no gang lookup off the main thread); a gangless sender gets the line re-sent as public
		// chat on the main thread (T-264).
		event.setCancelled(true);
		String message = event.getMessage();
		Bukkit.getScheduler().runTask(plugin, () -> deliver(player, message));
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		channel.forget(event.getPlayer().getUniqueId());
	}

	private void deliver(Player player, String message) {
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) {
			channel.forget(player.getUniqueId());
			player.sendMessage(messages.text("Social.Chat_Off", "&7Gang chat off."));
			// T-264: a null user is not "gangless" (quit removes the user before this task runs); only a known gangless
			// sender gets the line re-sent as public chat.
			if (user != null) {
				player.chat(message);
			}
			return;
		}

		channel.broadcast(messages, user.getGangId(), player.getName(), message, Bukkit.getOnlinePlayers(),
		                  this::gangIdOf);
	}

	private int gangIdOf(Player player) {
		User<Player> user = userManager.getUser(player);
		return user != null && user.hasGang() ? user.getGangId() : -1;
	}

}
