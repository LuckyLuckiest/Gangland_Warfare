package org.luckyraven.gangland.turf.stat;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.stats.GangStatContribution;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** W5-T's beans (gang links): the turfs-held ranking stat and the gang-disabled-world check on TurfManager. */
@Configuration
public final class TurfGangLinkConfig {

	@Bean
	public GangStatContribution turfsHeldStat(TurfManager turfs) {
		return new TurfsHeldStatContribution(turfs);
	}

	/**
	 * Hands TurfManager the gang world policy at bean creation, before its lifecycle {@code initialize()} runs, so the
	 * load-time warning (one per turf in a gang-disabled world) can see it.
	 */
	@Bean
	public TurfGangWorldLink turfGangWorldLink(TurfManager turfs, GangWorldPolicy policy) {
		// ponytail: an unloaded world resolves to null and follows only the global switch; the per-world list needs it loaded
		turfs.setGangsDisabledIn(name -> {
			World world = Bukkit.getWorld(name);
			return !policy.enabledIn(world);
		});
		return new TurfGangWorldLink();
	}

	/** Marker so the wiring above is a bean. */
	public record TurfGangWorldLink() {
	}
}
