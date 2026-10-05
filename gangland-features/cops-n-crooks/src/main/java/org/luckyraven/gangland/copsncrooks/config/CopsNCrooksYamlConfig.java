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
		// Squad roles (0.13.0): its own file, so a server with an older cops.yml still gets the commented catalogue.
		fileManager.addFile(new FileHandler(plugin, "cop_roles", "npc", ".yml", loader), true);
		// Police-radio lines (phase H12), module-owned like civilians' civilian_messages.yml — picked by
		// Settings.getLanguagePicked() via CopRadioMessages/LocalizedModuleYaml.
		fileManager.addFile(new FileHandler(plugin, "cop_radio_messages", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(plugin, "cop_radio_messages_es", "npc", ".yml", loader), true);
		// Chase tuning and HUD/charge-sheet text (0.15.0): heat, evasion, HUD, charge sheet.
		fileManager.addFile(new FileHandler(plugin, "wanted", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(plugin, "wanted_messages", "npc", ".yml", loader), true);
		// turf_npcs.yml moved to the turf module's own TurfModuleFileConfig (group I) alongside the turf-NPC code
		// it configures. trader_traits.yml/bank_tiers.yml moved to gangland-npc-shops' own NpcShopsYamlConfig
		// (group J, T-J5) alongside the trader/banker code they configure.
		return new CopsNCrooksFiles();
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class CopsNCrooksFiles { }
}
