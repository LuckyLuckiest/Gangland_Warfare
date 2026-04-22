package me.luckyraven.turf.task;

import lombok.CustomLog;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Heartbeat that refreshes each gang's {@code lastMemberOnlineAt} timestamp while at least one member is online. When
 * the last member logs off the value freezes at their last heartbeat, giving the spec's 10-minute post-logoff grace
 * window a stable anchor. Also drives the 10-day inactivity auto-release on a once-a-day tick (folded into the same
 * runnable — cheap).
 */
@CustomLog
public final class GangPresenceTracker {

	private static final long HEARTBEAT_TICKS = 20L * 60L;   // 1 minute
	private static final long RELEASE_TICKS   = 20L * 60L * 60L * 24L; // 24 hours

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
			                    .runTaskTimer(plugin, turfReleaseFn, RELEASE_TICKS, RELEASE_TICKS);
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
