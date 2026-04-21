package me.luckyraven.listener.player;

import me.luckyraven.core.configuration.ResourcePackTracker;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

@ListenerHandler
public class LoadResourcePackListener implements Listener {

	@EventHandler
	public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
		if (!Settings.isResourcePackEnabled()) return;

		Player player = event.getPlayer();

		switch (event.getStatus()) {
			case ACCEPTED -> {
				String message = GanglandChatUtil.prefixMessage("&eDownloading custom resource pack...");

				player.sendMessage(message);
			}
			case SUCCESSFULLY_LOADED -> {
				String message = GanglandChatUtil.prefixMessage("&aResource pack has been downloaded successfully.");

				player.sendMessage(message);
				ResourcePackTracker.markLoaded(player);
			}
			case FAILED_DOWNLOAD -> {
				String message = GanglandChatUtil.errorMessage("Could not download the resource pack.");

				player.sendMessage(message);
			}
			case DECLINED -> {
				if (Settings.isResourcePackKick()) {
					String message = GanglandChatUtil.color("&cYou have to accept the resource pack request!");

					player.kickPlayer(message);
				} else {
					String message = GanglandChatUtil.color(
							"&7If you changed your mind &aclick &7to check how to download the resource pack.");

					TextComponent click = new TextComponent(message);
					click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw option click resource"));

					String messageSpigot = GanglandChatUtil.color(
							"&cYou will miss some custom features if you did not download the resource pack!");

					player.sendMessage(messageSpigot);
					player.spigot().sendMessage(click);
				}
			}
		}
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		if (!Settings.isResourcePackEnabled()) return;

		player.setResourcePack(Settings.getResourcePackUrl());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		ResourcePackTracker.markUnloaded(event.getPlayer());
	}

}
