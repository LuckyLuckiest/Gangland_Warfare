package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.HeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLearner;
import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseRecord;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.events.wanted.WantedStartEvent;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.UUID;

/**
 * Keeps the {@link ChaseArcs} in step with the chase and hands each finished chase to the learner.
 *
 * @since 0.15.2
 */
@ListenerHandler
public class ChaseArcListener implements Listener {

	private final ChaseArcs         arcs;
	private final ChaseLearner      learner;
	private final HeatLedger        heat;
	private final ChaseConfigLoader config;

	public ChaseArcListener(ChaseArcs arcs, ChaseLearner learner, HeatLedger heat, ChaseConfigLoader config) {
		this.arcs    = arcs;
		this.learner = learner;
		this.heat    = heat;
		this.config  = config;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onStart(WantedStartEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		// prune first: a rejoin after 30+ minutes offline must not restore the stale arc (PLAN section 5, "start/join")
		arcs.prune();
		if (event.getCause() == WantedCause.RESTORE && arcs.has(id)) {
			arcs.restore(id);
		} else {
			arcs.start(id, event.getCause(), event.getWantedLevel());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onLevelChange(WantedLevelChangeEvent event) {
		// the 0 -> N RESTORE change fires before the start event; its stamp would land in the future after restore()
		if (event.getCause() == WantedCause.RESTORE) return;

		UUID id = event.getPlayer().getUniqueId();
		arcs.peak(id, event.getNewLevel());
		if (event.getNewLevel() > event.getOldLevel()) arcs.hot(id);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		HeatSettings settings = config.get().heat();
		if (settings.enabled() && settings.weightOf(event.getCrimeId()) > 0) {
			arcs.hot(event.getPlayer().getUniqueId());
		}
	}

	/** HIGH: runs before {@link HeatListener#onChaseEnd} (MONITOR) clears the crimes the arc's end reads. */
	@EventHandler(priority = EventPriority.HIGH)
	public void onChaseEnd(WantedEndEvent event) {
		UUID     id  = event.getPlayer().getUniqueId();
		ChaseArc arc = arcs.arc(id);
		if (arc == null) return;

		long now     = arcs.now();
		long until   = arc.offlineAt() != 0 ? arc.offlineAt() : now;
		long contact = arc.lastLostAt() == 0 ? 0 : arc.lastLostAt() - arc.startedAt();

		learner.record(new ChaseRecord(id, arc.startCause(), event.getCause(), arc.peak(), until - arc.startedAt(),
		                               contact, now), now);
		arcs.end(id, !heat.chaseCrimes(id).isEmpty());
	}

	/** Not via UserManager: RemoveAccountListener (HIGHEST) has already removed the user. */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		if (arcs.has(id)) arcs.quit(id);
	}
}
