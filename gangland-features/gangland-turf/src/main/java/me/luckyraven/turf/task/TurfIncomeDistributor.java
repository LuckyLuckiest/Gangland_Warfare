package me.luckyraven.turf.task;

import lombok.CustomLog;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.manager.TurfManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Periodic task (default every 10 minutes) that walks every owned turf and deposits its configured income into the
 * owning gang's economy. Orphaned turfs whose owning gang no longer exists are auto-freed.
 */
@CustomLog
public final class TurfIncomeDistributor {

	private final JavaPlugin         plugin;
	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final long               intervalTicks;

	private BukkitTask task;

	public TurfIncomeDistributor(JavaPlugin plugin,
	                             TurfManager turfs,
	                             GangLookupContract gangs,
	                             long intervalTicks) {
		this.plugin        = plugin;
		this.turfs         = turfs;
		this.gangs         = gangs;
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

	private void distribute() {
		for (Turf turf : turfs.getAll()) {
			if (turf.isUnclaimed()) {
				continue;
			}
			Gang gang = gangs.findById(turf.getOwnerGangId());
			if (gang == null) {
				log.info("Turf {} owned by missing gang {} — auto-releasing", turf.getId(), turf.getOwnerGangId());
				turf.setOwnerGangId(null);
				turfs.persist(turf);
				continue;
			}
			gang.getEconomy().depositAmount(turf.getIncomeAmount());
		}
	}
}
