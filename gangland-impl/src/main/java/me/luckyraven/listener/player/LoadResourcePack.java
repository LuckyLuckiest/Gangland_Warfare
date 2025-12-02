package me.luckyraven.listener.player;

import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.listener.ListenerHandler;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

@ListenerHandler
public class LoadResourcePack implements Listener {

	@EventHandler
	public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
		if (!SettingAddon.isResourcePackEnabled()) return;

		Player player = event.getPlayer();

		switch (event.getStatus()) {
			case ACCEPTED -> {
				String message = ChatUtil.prefixMessage("&eDownloading custom resource pack...");

				player.sendMessage(message);
			}
			case SUCCESSFULLY_LOADED -> {
				String message = ChatUtil.prefixMessage("&aResource pack has been downloaded successfully.");

				player.sendMessage(message);
			}
			case FAILED_DOWNLOAD -> {
				String message = ChatUtil.errorMessage("Could not download the resource pack.");

				player.sendMessage(message);
			}
			case DECLINED -> {
				if (SettingAddon.isResourcePackKick()) {
					String message = ChatUtil.color("&cYou have to accept the resource pack request!");

					player.kickPlayer(message);
				} else {
					String message = ChatUtil.color(
							"&7If you changed your mind &aclick &7to check how to download the resource pack.");

					TextComponent click = new TextComponent(message);
					click.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw option click resource"));

					String messageSpigot = ChatUtil.color(
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

		if (!SettingAddon.isResourcePackEnabled()) return;

		player.setResourcePack(SettingAddon.getResourcePackUrl());
	}

}
