package org.luckyraven.gangland.turf.task;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;

/**
 * Periodic task (default every 10 minutes) that walks every owned turf and deposits its configured income into the
 * owning gang's economy. Orphaned turfs whose owning gang no longer exists are auto-freed through
 * {@link TurfOwnership}. The timer is stopped on plugin shutdown.
 */
@CustomLog
public final class TurfIncomeDistributor implements BeanLifecycle {

	private final JavaPlugin         plugin;
	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final ActiveBuffManager  buffs;
	private final TurfOwnership      ownership;
	private final long               intervalTicks;

	private BukkitTask task;

	public TurfIncomeDistributor(JavaPlugin plugin,
	                             TurfManager turfs,
	                             GangLookupContract gangs,
	                             ActiveBuffManager buffs,
	                             TurfOwnership ownership,
	                             long intervalTicks) {
		this.plugin        = plugin;
		this.turfs         = turfs;
		this.gangs         = gangs;
		this.buffs         = buffs;
		this.ownership     = ownership;
		this.intervalTicks = intervalTicks;
	}

	public void start() {
		if (task != null) {
			return;
		}
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::distribute, intervalTicks, intervalTicks);
	}

	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	@Override
	public void onShutdown() {
		stop();
	}

	/** Package-private for tests (W2-K). */
	void distribute() {
		for (Turf turf : new ArrayList<>(turfs.getAll())) {
			if (turf.isUnclaimed()) {
				continue;
			}
			Gang gang = gangs.findById(turf.getOwnerGangId());
			if (gang == null) {
				log.info("Turf {} owned by missing gang {} - auto-releasing", turf.getId(), turf.getOwnerGangId());
				ownership.setOwner(turf, null, TurfOwnerChangedEvent.Cause.ORPHANED);
				continue;
			}
			BigDecimal multiplier = BigDecimal.valueOf(buffs.effectiveMultiplier(turf.getId(),
			                                                                     EffectType.INCOME_MULTIPLIER));
			BigDecimal payout = turf.getIncomeAmount().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
			gang.getEconomy().depositAmount(payout);
		}
	}
}
