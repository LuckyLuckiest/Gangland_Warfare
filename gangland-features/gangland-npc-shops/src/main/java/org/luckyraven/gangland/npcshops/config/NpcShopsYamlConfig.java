package org.luckyraven.gangland.npcshops.config;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * Registers this module's YAML defaults with the host FileManager (T-J5, group J). The five-argument FileHandler
 * copies the bundled default out of the MODULE jar (parent-first loader), so the same paths must no longer exist in
 * the core or cops-n-crooks jars. Runs in KERNEL, after KernelConfig produced the FileManager (parameter = ordering
 * edge) — modelled on {@code CopsNCrooksYamlConfig}/{@code CiviliansYamlConfig}, whose {@code trader_traits.yml}/
 * {@code bank_tiers.yml} registrations this class replaces.
 */
@CustomLog
@Configuration(phase = Phase.KERNEL)
public class NpcShopsYamlConfig {

	private final JavaPlugin plugin;

	public NpcShopsYamlConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public NpcShopsFiles npcShopsFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		ClassLoader loader = moduleLoader.classLoader();
		fileManager.addFile(new FileHandler(plugin, "trader_traits", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(plugin, "bank_tiers", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(plugin, "trader_settings", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(plugin, "banker_settings", "npc", ".yml", loader), true);
		return new NpcShopsFiles();
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class NpcShopsFiles { }
}
