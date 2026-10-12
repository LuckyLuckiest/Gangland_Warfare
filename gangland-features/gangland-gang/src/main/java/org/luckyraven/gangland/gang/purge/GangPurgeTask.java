package org.luckyraven.gangland.gang.purge;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.events.gangs.GangMemberLeftEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.membership.MembershipQueries;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Disbands gangs nobody has played in for {@code Purge.Inactive_Days} and drops non-owner members unseen for
 * {@code Purge.Member_Inactive_Days}. Off unless {@code Purge.Enabled}; the staff preview and run work either way.
 * An hourly check runs the purge once a day, the first time {@code Purge.First_Run_Delay_Hours} after boot.
 */
public class GangPurgeTask implements BeanLifecycle {

	private static final long HOUR_TICKS = 20L * 60L * 60L;

	private final JavaPlugin               plugin;
	private final GangManager              gangManager;
	private final MembershipQueries        queries;
	private final GangRules                rules;
	private final GangDisbandService       disbandService;
	private final GangMembershipService    membershipService;
	private final Function<UUID, Long>     lastPlayed;
	private final LongSupplier             clock;
	private final long                     bootMillis;

	private BukkitTask task;
	private long       nextRunMillis = -1;

	/**
	 * @param lastPlayed epoch millis the player was last seen ({@code 0} when never); online players must answer "now"
	 */
	public GangPurgeTask(JavaPlugin plugin, GangManager gangManager, MembershipQueries queries, GangRules rules,
	                     GangDisbandService disbandService, GangMembershipService membershipService,
	                     Function<UUID, Long> lastPlayed, LongSupplier clock) {
		this.plugin            = plugin;
		this.gangManager       = gangManager;
		this.queries           = queries;
		this.rules             = rules;
		this.disbandService    = disbandService;
		this.membershipService = membershipService;
		this.lastPlayed        = lastPlayed;
		this.clock             = clock;
		this.bootMillis        = clock.getAsLong();
	}

	/** The seen-time lookup the server uses: online means now, else the offline player's last play. */
	public static Function<UUID, Long> serverLastPlayed() {
		return uuid -> {
			OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
			return player.isOnline() ? System.currentTimeMillis() : player.getLastPlayed();
		};
	}

	/** Gangs the purge would disband now; changes nothing. */
	public List<PurgeCandidate> preview(Instant now) {
		long cutoff = now.toEpochMilli() - Duration.ofDays(rules.getPurgeInactiveDays()).toMillis();

		List<PurgeCandidate> candidates = new ArrayList<>();
		for (Gang gang : gangManager.getAll()) {
			long newest = 0;
			for (Member member : gang.getMembers()) {
				newest = Math.max(newest, lastPlayed.apply(member.getUuid()));
			}
			if (newest < cutoff) candidates.add(new PurgeCandidate(gang.getId(), gang.getName(), newest));
		}
		return candidates;
	}

	/** @return how many gangs were disbanded. */
	public int run(Instant now) {
		int disbanded = 0;
		for (PurgeCandidate candidate : preview(now)) {
			Gang gang = gangManager.getGang(candidate.gangId());
			if (gang == null) continue;

			if (disbandService.disband(gang, null, true, rules.isPurgeRefundFee()).ok()) disbanded++;
		}

		long memberCutoff = now.toEpochMilli() - Duration.ofDays(rules.getPurgeMemberInactiveDays()).toMillis();
		for (Gang gang : gangManager.getAll()) {
			Optional<UUID> owner = queries.ownerOf(gang.getId());
			for (Member member : gang.getMembers()) {
				if (owner.isPresent() && owner.get().equals(member.getUuid())) continue;

				if (lastPlayed.apply(member.getUuid()) < memberCutoff) {
					membershipService.leave(member.getUuid(), GangMemberLeftEvent.Reason.PURGED);
				}
			}
		}
		return disbanded;
	}

	/** The hourly check: runs {@link #run} when enabled, past the first-run delay and a day after the last run. */
	public int tick(Instant now) {
		if (!rules.isPurgeEnabled()) return 0;

		if (nextRunMillis < 0) nextRunMillis = bootMillis + Duration.ofHours(rules.getPurgeFirstRunDelayHours()).toMillis();
		if (now.toEpochMilli() < nextRunMillis) return 0;

		nextRunMillis = now.toEpochMilli() + Duration.ofDays(1).toMillis();
		return run(now);
	}

	@Override
	public void onInitialize(boolean reload) {
		if (task != null || plugin == null) return;

		task = Bukkit.getScheduler()
		             .runTaskTimer(plugin, () -> tick(Instant.ofEpochMilli(clock.getAsLong())), HOUR_TICKS, HOUR_TICKS);
	}

	@Override
	public void onShutdown() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}
}
