package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.seam.DefendingOwnTurf;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.config.CrewHeatSettings;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.events.crime.CrimeCommittedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

import java.util.Locale;
import java.util.Map;

/**
 * Crew heat (0.21.0, P-CREW-1..6): a crime is recorded in each online gang mate's or ally's own heat ledger.
 *
 * @since 0.21.0
 */
@CustomLog
@ListenerHandler
public class CrewHeatListener implements Listener {

	private final HeatLedger           ledger;
	private final GangMembership       gangs;
	private final DetainmentService    detainmentService;
	private final ChaseConfigLoader    config;
	private final WantedMessages       messages;
	private final DefendingOwnTurf     defending;

	public CrewHeatListener(HeatLedger ledger, GangMembership gangs, TurfManager turfs,
	                        DetainmentService detainmentService, ChaseConfigLoader config, WantedMessages messages) {
		this.ledger            = ledger;
		this.gangs             = gangs;
		this.detainmentService = detainmentService;
		this.config            = config;
		this.messages          = messages;
		this.defending         = new DefendingOwnTurf(turfs, gangs);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCrime(CrimeCommittedEvent event) {
		CrewHeatSettings settings = config.getCrewHeat();
		if (!settings.enabled()) {
			return;
		}

		String crimeId = event.getCrimeId();
		if (settings.excludeCrimes().contains(crimeId.toLowerCase(Locale.ROOT))) {
			return;
		}

		Player   actor = event.getPlayer();
		Location loc   = event.getLocation();
		World    world = loc.getWorld();
		if (world == null) {
			return;
		}

		double r2 = settings.radius() * settings.radius();

		for (Player mate : world.getPlayers()) {
			if (mate.getUniqueId().equals(actor.getUniqueId())
			    || NpcSupport.isNpc(mate)
			    || mate.getLocation().distanceSquared(loc) > r2
			    || !gangs.alliedOrSame(actor.getUniqueId(), mate.getUniqueId())
			    || detainmentService.isRestrained(mate)
			    || DownedPlayerRegistry.isDowned(mate.getUniqueId())
			    || defending.test(mate, loc)) {
				continue;
			}

			// the shared record is fed straight to the ledger, never to Bukkit, so it is not re-shared
			double heat = ledger.record(new CrimeCommittedEvent(mate, crimeId, loc, event.isSeenByCop(),
			                                                    event.getWitnesses()));
			if (heat > 0) {
				mate.sendMessage(messages.format(WantedMessages.Key.CREW_HEAT_SHARED,
				                                 Map.of("player", actor.getName(),
				                                        "crime", messages.crimeName(crimeId))));
				log.debug("CREW_HEAT {} -> {} crime={} heat={}", actor.getName(), mate.getName(), crimeId, heat);
			}
		}
	}
}
