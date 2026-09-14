package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

@CustomLog
@Configuration(phase = Phase.KERNEL)
public class CopsNCrooksYamlConfig {

	private final JavaPlugin plugin;

	public CopsNCrooksYamlConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Registers this module's YAML defaults with the host FileManager. The five-argument FileHandler copies the
	 * bundled default out of the MODULE jar (parent-first loader), so the same paths must no longer exist in the
	 * core jar. Runs in KERNEL, after KernelConfig produced the FileManager (parameter = ordering edge).
	 */
	@Bean
	public CopsNCrooksFiles copsNCrooksFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		ClassLoader loader = moduleLoader.classLoader();
		fileManager.addFile(new FileHandler(plugin, "cops", "npc", ".yml", loader), true);
		// turf_npcs.yml moved to the turf module's own TurfModuleFileConfig (group I) alongside the turf-NPC code
		// it configures. trader_traits.yml/bank_tiers.yml moved to gangland-npc-shops' own NpcShopsYamlConfig
		// (group J, T-J5) alongside the trader/banker code they configure.
		return new CopsNCrooksFiles();
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class CopsNCrooksFiles { }
}
