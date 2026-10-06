package org.luckyraven.gangland.listener.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.update.UpdateNotifier;

import java.util.UUID;

/**
 * {@code Update_Checker.Notify_Privileged_Players}: a player holding the update-check permission is told on join when
 * a newer release is out. The flag is read per join, so {@code /glw reload} applies it; with
 * {@code Update_Checker.Enable: false} there is no notifier and nothing is sent.
 */
@ListenerHandler
public final class UpdateNotifyListener implements Listener {

	private final Gangland gangland;

	public UpdateNotifyListener(Gangland gangland) {
		this.gangland = gangland;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		if (!Settings.isNotifyPrivilegedPlayers()) return;

		UpdateNotifier notifier = gangland.getUpdateChecker();
		if (notifier == null) return;

		Player player = event.getPlayer();
		if (!player.hasPermission(notifier.getCheckPermission())) return;

		UUID playerId = player.getUniqueId();

		// The version lookup is a blocking HTTP call, so it runs off the server thread.
		// ponytail: one SpigotMC fetch per privileged join (two when an update exists); cache the last check in
		// Keystone's UpdateNotifier if staff joins ever make this noticeable.
		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			if (!notifier.updateAvailable()) return;

			String message = GanglandChatUtil.commandMessage(notifier.getUpdateMessage());

			Bukkit.getScheduler().runTask(gangland, () -> {
				Player online = Bukkit.getPlayer(playerId);
				if (online != null) online.sendMessage(message);
			});
		});
	}

}
