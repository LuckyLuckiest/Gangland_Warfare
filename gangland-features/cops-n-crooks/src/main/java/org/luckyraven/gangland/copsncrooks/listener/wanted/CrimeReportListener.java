package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.events.police.ArrestedEvent;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.user.UserDataInitEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * Feeds the criminal's fate to {@link CopCrimeReports} (0.21.2, PLAN.md 1.4): death, a cuff or an arrest drops his
 * pending reports (it settles him; a downed player keeps them, his cuff or death decides), a quit defers them, and his
 * next join lands the deferred ones one tick after his user data loaded - never on PlayerJoinEvent, which runs before
 * the async load restores his stars (D-W2-31, as {@code BagListener}).
 *
 * @since 0.21.2
 */
@ListenerHandler
public class CrimeReportListener implements Listener {

	private final CopCrimeReports reports;
	private final JavaPlugin      plugin;

	public CrimeReportListener(CopCrimeReports reports, JavaPlugin plugin) {
		this.reports = reports;
		this.plugin  = plugin;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		reports.drop(event.getEntity(), "death");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onCuffed(CuffedEvent event) {
		reports.drop(event.getTarget(), "cuffed");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onArrested(ArrestedEvent event) {
		reports.drop(event.getPlayer(), "arrested");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		Player player = event.getPlayer();
		if (event.getCause() == WantedCause.ARREST && player != null) reports.drop(player, "arrested");
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		reports.onQuit(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onUserData(UserDataInitEvent event) {
		Player player = event.getPlayer();
		if (player == null) return;

		plugin.getServer().getScheduler().runTask(plugin, () -> {
			if (player.isOnline()) reports.onJoin(player);
		});
	}
}
