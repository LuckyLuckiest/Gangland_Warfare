package org.luckyraven.gangland.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;

/**
 * KERNEL-phase registration of the gang module's own YAML files. Filled in by W1-K step 6.
 */
@Configuration(phase = Phase.KERNEL)
public final class GangModuleFileConfig {

	private final JavaPlugin plugin;

	public GangModuleFileConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}
}
