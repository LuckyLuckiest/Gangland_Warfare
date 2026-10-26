package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds a gated crime (Wanted.Witnesses.Crimes) that no cop and no witness saw: the event is cancelled, so the heat
 * ledger and the chase arc skip it, and a civilian who saw it is asked to call 911.
 *
 * <p>No cop saw it means: no seen-by-cop flag, no fresh squad sighting, and (0.21.1) no live cop, squad or beat, that
 * picks the player up right now ({@link CopPerception#acquires}, as an idle cop does, within the cops'
 * {@code Alert_Range}). The commit sites of Kill_Civilian and Car_Theft never set the flag, so without that look a
 * kill in front of a patrol cop was held for a witness.
 *
 * @since 0.20.0
 */
@CustomLog
@ListenerHandler
public class WitnessGateListener implements Listener {

	/** {@code YamlCopConfigProvider}'s fallback when no cop config is loaded. */
	private static final double DEFAULT_ALERT_RANGE = 40.0;

	private final ChaseConfigLoader config;
	private final WitnessService    witnesses;
	private final CopManager        copManager;
	private final PatrolManager     patrol;
	private final CopPerception     perception;
	private final CopLoader         copLoader;

	public WitnessGateListener(ChaseConfigLoader config, WitnessService witnesses, CopManager copManager,
	                           PatrolManager patrol, CopPerception perception, CopLoader copLoader) {
		this.config     = config;
		this.witnesses  = witnesses;
		this.copManager = copManager;
		this.patrol     = patrol;
		this.perception = perception;
		this.copLoader  = copLoader;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		if (!NpcSupport.available()) return;

		WitnessSettings settings = config.getWitnesses();
		if (!settings.enabled() || !settings.crimes().contains(event.getCrimeId())) return;
		if (event.isSeenByCop() || event.getWitnesses() > 0) return;

		CopGroup group = copManager.groupOf(event.getPlayer().getUniqueId());
		if (group != null && group.getSquad().hasFreshSighting()) return;
		if (copSees(event.getPlayer())) return;

		event.setCancelled(true);
		int callers = witnesses.witness(event.getPlayer(), event.getCrimeId(), event.getLocation());
		log.debug("WITNESS {} crime={} callers={}", event.getPlayer().getName(), event.getCrimeId(), callers);
	}

	/** Whether any live squad or beat cop sees {@code player} within the cops' alert range. */
	private boolean copSees(Player player) {
		CopConfigProvider provider = copLoader.getLoadedProvider();
		double            range    = provider == null ? DEFAULT_ALERT_RANGE : provider.getAlertRange();

		List<CopNpc> cops = new ArrayList<>(copManager.liveCops());
		cops.addAll(patrol.beatCops());
		for (CopNpc cop : cops) {
			if (cop.isValid() && perception.acquires(cop, player, range)) return true;
		}
		return false;
	}
}
