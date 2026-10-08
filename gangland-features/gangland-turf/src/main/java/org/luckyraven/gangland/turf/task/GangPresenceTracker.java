package org.luckyraven.gangland.turf.task;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.core.user.User;

/**
 * Heartbeat that refreshes each gang's {@code lastMemberOnlineAt} timestamp while at least one member is online. When
 * the last member logs off the value freezes at their last heartbeat, giving the spec's 10-minute post-logoff grace
 * window a stable anchor. Also drives the inactivity auto-release (the {@code Inactivity_Auto_Release_Days} setting)
 * on a once-a-day tick (folded into the same runnable — cheap). The first release sweep runs five minutes after start
 * (TF-09), so a server restarted daily still releases an inactive gang's turf.
 */
@CustomLog
public final class GangPresenceTracker implements BeanLifecycle {

	private static final long HEARTBEAT_TICKS         = 20L * 60L;   // 1 minute
	static final long FIRST_RELEASE_DELAY_TICKS = 5L * 60L * 20L;      // 5 minutes (TF-09)
	private static final long RELEASE_TICKS           = 20L * 60L * 60L * 24L; // 24 hours

	private final JavaPlugin         plugin;
	private final GangLookupContract gangs;
	private final UserLookupContract users;
	private final int                inactivityAutoReleaseDays;
	private final Runnable           turfReleaseFn;

	private BukkitTask heartbeatTask;
	private BukkitTask releaseTask;

	public GangPresenceTracker(JavaPlugin plugin,
	                           GangLookupContract gangs,
	                           UserLookupContract users,
	                           int inactivityAutoReleaseDays,
	                           Runnable turfReleaseFn) {
		this.plugin                    = plugin;
		this.gangs                     = gangs;
		this.users                     = users;
		this.inactivityAutoReleaseDays = inactivityAutoReleaseDays;
		this.turfReleaseFn             = turfReleaseFn;
	}

	public void start() {
		if (heartbeatTask == null) {
			heartbeatTask = Bukkit.getScheduler()
			                      .runTaskTimer(plugin, this::heartbeat, HEARTBEAT_TICKS, HEARTBEAT_TICKS);
		}
		if (releaseTask == null) {
			releaseTask = Bukkit.getScheduler()
			                    .runTaskTimer(plugin, turfReleaseFn, FIRST_RELEASE_DELAY_TICKS, RELEASE_TICKS);
		}
	}

	public void stop() {
		if (heartbeatTask != null) {
			heartbeatTask.cancel();
			heartbeatTask = null;
		}
		if (releaseTask != null) {
			releaseTask.cancel();
			releaseTask = null;
		}
	}

	@Override
	public void onShutdown() {
		stop();
	}

	public int getInactivityAutoReleaseDays() {
		return inactivityAutoReleaseDays;
	}

	private void heartbeat() {
		long now = System.currentTimeMillis();
		for (Player player : Bukkit.getOnlinePlayers()) {
			User<Player> user = users.findByPlayer(player);
			if (user == null || !user.hasGang()) {
				continue;
			}
			Gang gang = gangs.findById(user.getGangId());
			if (gang == null) {
				continue;
			}
			gang.setLastMemberOnlineAt(now);
		}
	}
}
