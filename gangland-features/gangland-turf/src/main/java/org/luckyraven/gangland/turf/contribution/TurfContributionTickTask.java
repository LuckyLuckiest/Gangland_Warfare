package org.luckyraven.gangland.turf.contribution;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.UUID;

/**
 * 1-Hz scanner that awards contribution points to online players standing inside a CONTESTING turf. Defender gang
 * members inside an owned turf being contested earn {@code defenderPresenceTick} per tick; attacker gang members inside
 * the turf they're capturing earn {@code attackerPresenceTick}. All awards land in
 * {@link Member#increaseContribution(double)} which the gang module already persists.
 *
 * <p>Cheap per-tick — iterates online players, looks up "which turf am I standing in?" via {@link
 * TurfManager#findAt(org.bukkit.Location)}, and only acts on players in CONTESTING regions. No per-turf scan when
 * nobody is contesting.
 */
@CustomLog
public final class TurfContributionTickTask {

	private final JavaPlugin               plugin;
	private final TurfManager              turfs;
	private final GangLookupContract       gangs;
	private final UserLookupContract       users;
	private final TurfContributionSettings settings;

	private BukkitTask task;

	public TurfContributionTickTask(JavaPlugin plugin,
	                                TurfManager turfs,
	                                GangLookupContract gangs,
	                                UserLookupContract users,
	                                TurfContributionSettings settings) {
		this.plugin   = plugin;
		this.turfs    = turfs;
		this.gangs    = gangs;
		this.users    = users;
		this.settings = settings;
	}

	public void start() {
		if (task != null) return;
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
	}

	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	private void tick() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			Turf at = turfs.findAt(player.getLocation());
			if (at == null) continue;

			TurfRuntimeState state = turfs.getRuntimeState(at.getId());
			if (state == null || state.getState() != TurfState.CONTESTING) continue;

			User<Player> user = users.findByPlayer(player);
			if (user == null || !user.hasGang()) continue;

			int     viewerGangId = user.getGangId();
			Integer defenderId   = at.getOwnerGangId();
			Integer challengerId = state.getChallengerGangId();

			double points = 0.0;
			if (defenderId != null && viewerGangId == defenderId) {
				points = settings.defenderPresenceTick();
			} else if (challengerId != null && viewerGangId == challengerId) {
				points = settings.attackerPresenceTick();
			}
			if (points <= 0.0) continue;

			award(player.getUniqueId(), viewerGangId, points);
		}
	}

	private void award(UUID playerId, int gangId, double points) {
		Gang gang = gangs.findById(gangId);
		if (gang == null) return;
		for (Member member : gang.getMembers()) {
			if (member.getUuid().equals(playerId)) {
				member.increaseContribution(points);
				return;
			}
		}
	}
}
