package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
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
		return new HeatLedger(config, users, crimes, player -> {
			CopGroup group = copManager.groupOf(player.getUniqueId());
			return group != null && group.getSquad().hasFreshSighting();
		}, location -> HeatLedger.contestedTurfAt(turfs, location) != null,
		                      () -> config.getKillCombo().chainSeconds(),
		                      System::currentTimeMillis);
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
