package org.luckyraven.gangland.turf.task;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.gangland.turf.capture.CaptureSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

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
	private final GangMembership     membership;
	private final int                inactivityAutoReleaseDays;
	private final Runnable           turfReleaseFn;
	private final Supplier<CaptureRules> rules;
	private final CaptureSettings    settings;
	private final Map<Integer, Long> wokeAt = new HashMap<>();

	private BukkitTask heartbeatTask;
	private BukkitTask releaseTask;

	public GangPresenceTracker(JavaPlugin plugin,
	                           GangLookupContract gangs,
	                           UserLookupContract users,
	                           int inactivityAutoReleaseDays,
	                           Runnable turfReleaseFn,
	                           Supplier<CaptureRules> rules,
	                           CaptureSettings settings) {
		this.plugin                    = plugin;
		this.gangs                     = gangs;
		this.users                     = users;
		this.membership                = null;
		this.inactivityAutoReleaseDays = inactivityAutoReleaseDays;
		this.turfReleaseFn             = turfReleaseFn;
		this.rules                     = rules;
		this.settings                  = settings;
	}

	// RED STUB (rf-presence): membership-based constructor; heartbeat still reads the user record.
	public GangPresenceTracker(JavaPlugin plugin,
	                           GangLookupContract gangs,
	                           GangMembership membership,
	                           int inactivityAutoReleaseDays,
	                           Runnable turfReleaseFn,
	                           Supplier<CaptureRules> rules,
	                           CaptureSettings settings) {
		this.plugin                    = plugin;
		this.gangs                     = gangs;
		this.users                     = null;
		this.membership                = membership;
		this.inactivityAutoReleaseDays = inactivityAutoReleaseDays;
		this.turfReleaseFn             = turfReleaseFn;
		this.rules                     = rules;
		this.settings                  = settings;
	}

	/**
	 * Epoch ms gang {@code gangId} last woke up (its first qualifying member came online after the logout window
	 * closed); 0 when unknown. In memory only: after a restart the first login wakes the gang again. Filled by W2-R1.
	 */
	public long wokeAt(int gangId) {
		return wokeAt.getOrDefault(gangId, 0L);
	}

	/** Records a wake-up (W2-R1 calls it from the join listener and the heartbeat). */
	public void recordWakeUp(int gangId, long at) {
		wokeAt.put(gangId, at);
	}

	public void start() {
		if (heartbeatTask == null) {
			heartbeatTask = Bukkit.getScheduler()
			                      .runTaskTimer(plugin, () -> heartbeat(System.currentTimeMillis()),
			                                    HEARTBEAT_TICKS, HEARTBEAT_TICKS);
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

	/** The rules in force now (the join listener reads the minimum member age and the wake-up shield). */
	CaptureRules rules() {
		return rules.get();
	}

	/** True when the gang's logout window has closed: never stamped, or the last stamp is older than the window. */
	boolean windowClosed(Gang gang, long now) {
		long last = gang.getLastMemberOnlineAt();
		return last <= 0 || now - last > CaptureEligibility.onlineWindowMs(settings);
	}

	/**
	 * True when this member keeps the gang's turfs open: any member of a young gang (created less than the minimum
	 * member age ago), an old member, or every member when the age rule is off. A join date of 0 counts as old.
	 */
	static boolean qualifies(Gang gang, Member member, CaptureRules rules, long now) {
		if (member == null) {
			return false;
		}
		long ageMs = rules.minMemberAgeHours() * 3_600_000L;
		if (ageMs <= 0L) {
			return true;
		}
		if (now - gang.getCreated() < ageMs) {
			return true;
		}
		long joined = member.getGangJoinDateLong();
		return joined <= 0L || now - joined >= ageMs;
	}

	/** The gang's member record for {@code uuid}, or null when it is not a member. */
	static Member memberOf(Gang gang, UUID uuid) {
		return gang.getMembers().stream().filter(m -> uuid.equals(m.getUuid())).findFirst().orElse(null);
	}

	/**
	 * Stamps every gang with a qualifying member online. A gang whose window had closed is woken first (its first
	 * qualifying member online after the logout window), so the join listener and the heartbeat agree on wake-ups.
	 */
	void heartbeat(long now) {
		CaptureRules current = rules.get();
		for (Player player : Bukkit.getOnlinePlayers()) {
			User<Player> user = users == null ? null : users.findByPlayer(player);
			if (user == null || !user.hasGang()) {
				continue;
			}
			Gang gang = gangs.findById(user.getGangId());
			if (gang == null) {
				continue;
			}
			if (!qualifies(gang, memberOf(gang, player.getUniqueId()), current, now)) {
				continue;
			}
			if (windowClosed(gang, now)) {
				recordWakeUp(gang.getId(), now);
			}
			gang.setLastMemberOnlineAt(now);
		}
	}
}
