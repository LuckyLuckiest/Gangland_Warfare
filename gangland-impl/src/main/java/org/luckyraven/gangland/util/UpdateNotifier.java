package org.luckyraven.gangland.util;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.timer.RepeatingTimer;
import org.luckyraven.keystone.update.UpdateChecker;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Product-side update flow over Keystone's {@link UpdateChecker} (1.7.x migration). Keystone owns version
 * fetching, comparison and download; this class keeps Gangland's policy: the periodic operator notification, the
 * optional auto-download ({@code Settings.isUpdaterAutoUpdate()}), and the player-facing update message the join
 * listener sends. Exposes delegate reads so callers keep one handle.
 */
@CustomLog
public final class UpdateNotifier {

	private final JavaPlugin     plugin;
	private final UpdateChecker  checker;
	private final RepeatingTimer repeatingTimer;
	private final AtomicBoolean  checked;

	public UpdateNotifier(JavaPlugin plugin, UpdateChecker checker, long intervalSeconds) {
		this.plugin         = plugin;
		this.checker        = checker;
		this.checked        = new AtomicBoolean();
		this.repeatingTimer = new RepeatingTimer(plugin, intervalSeconds * 20L, timer -> task());
	}

	public void start() {
		log.info("Checking for updates");
		task();
		this.repeatingTimer.start(true);
	}

	public String getCheckPermission() {
		return checker.getCheckPermission();
	}

	public String getLatestVersion() {
		return checker.getLatestVersion();
	}

	public boolean updateAvailable() {
		return checker.updateAvailable();
	}

	public boolean downloadLatestVersion() {
		return checker.downloadLatestVersion();
	}

	public String getUpdateMessage() {
		if (!updateAvailable()) return "The plugin is up to date.";

		return String.format("The current version is %s, please update to the newest version available: %s",
		                     plugin.getDescription().getVersion(), getLatestVersion());
	}

	private void task() {
		String updateMessage = getUpdateMessage();

		if (updateAvailable()) {
			GanglandChatUtil.sendToOperators(getCheckPermission(), updateMessage, log, true);
			if (Settings.isUpdaterAutoUpdate()) {
				downloadLatestVersion();
			}
			return;
		}

		if (checked.get()) return;

		GanglandChatUtil.sendToOperators(getCheckPermission(), updateMessage);
		checked.set(true);
	}

}
