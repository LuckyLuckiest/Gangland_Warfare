package org.luckyraven.gangland.copsncrooks.config;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;

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
		                      () -> Settings.isWantedKillComboEnabled() ? Settings.getWantedKillComboResetAfter() : 0,
		                      System::currentTimeMillis);
	}

	@PostConstruct
	public void registerAttackedHook() {
		HeatLedger ledger = container.getInstance(HeatLedger.class);

		container.getInstance(CopManager.class).addCopAttackedHook((cop, player) -> {
			Entity entity = cop.getEntity();
			if (entity == null) return;

			ledger.reportAssault(player, entity.getUniqueId(), player.getLocation());
		});
	}
}
