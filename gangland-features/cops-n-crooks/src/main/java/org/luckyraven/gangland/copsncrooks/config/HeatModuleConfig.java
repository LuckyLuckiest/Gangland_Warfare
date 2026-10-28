package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolManager;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.copsncrooks.wanted.report.CopCrimeReports;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

import java.util.UUID;

/** Beans of the heat ledger (0.15.0). */
@Configuration
public class HeatModuleConfig {

	private final JavaPlugin          plugin;
	private final DependencyContainer container;

	public HeatModuleConfig(JavaPlugin plugin, DependencyContainer container) {
		this.plugin    = plugin;
		this.container = container;
	}

	@Bean
	public HeatLedger heatLedger(ChaseConfigLoader config, @Qualifier("online") UserManager<Player> users,
	                             CrimeService crimes, CopManager copManager, TurfManager turfs) {
		HeatLedger ledger = new HeatLedger(config, users, crimes, player -> {
			CopGroup group = copManager.groupOf(player.getUniqueId());
			return group != null && group.getSquad().hasFreshSighting();
		}, location -> HeatLedger.contestedTurfAt(turfs, location) != null,
		                                   () -> config.getKillCombo().chainSeconds(),
		                                   System::currentTimeMillis);
		// 0.21.2 (T-543): a wanted start a late report causes names and tips the crime spot, not the live position
		copManager.setReportedScene(ledger::reportedScene);
		return ledger;
	}

	/**
	 * The cop eyewitness reports and silent officers (0.21.2, PLAN.md sections 1 and 2); its 10-tick task follows the
	 * bean lifecycle.
	 */
	@Bean
	public CopCrimeReports copCrimeReports(ChaseConfigLoader config, CopLoader copLoader, CopManager copManager,
	                                       PatrolManager patrol, CopPerception perception, WitnessService witnesses,
	                                       HeatLedger ledger, CrimeService crimes, CopRadio copRadio,
	                                       WantedMessages messages, DetainmentService detainment) {
		CopCrimeReports reports = new CopCrimeReports(config, copLoader, copManager, patrol, perception, witnesses, ledger,
		                                              crimes, copRadio, messages, detainment,
		                                              CopCrimeReports.Hooks.live(plugin));
		// 0.21.2 S5 (T-545): a cop killed unseen keeps his slot and his station's replacement until dispatch notices
		copManager.setSilentRoles(reports::silentRoles);
		patrol.setStationHold(reports::silentStation);
		return reports;
	}

	@PostConstruct
	public void registerAttackedHook() {
		HeatLedger ledger = container.getInstance(HeatLedger.class);

		container.getInstance(CopManager.class).addCopAttackedHook((cop, player) -> {
			Entity entity = cop.getEntity();
			if (entity == null) return;

			// 0.21.2 (T-535, T-536): one tick later, so a lethal blow books only Kill_Cop (the cop is dead by then) and
			// the hit cop's own sighting, which CopManager reports after this hook, already counts as seen
			UUID     copId = entity.getUniqueId();
			Location at    = player.getLocation();
			plugin.getServer().getScheduler().runTask(plugin, () -> {
				if (!entity.isValid() || entity.isDead() || !player.isOnline()) return;

				ledger.reportAssault(player, copId, at);
			});
		});
	}
}
