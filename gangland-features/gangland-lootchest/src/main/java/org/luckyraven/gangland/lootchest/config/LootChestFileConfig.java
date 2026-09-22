package org.luckyraven.gangland.lootchest.config;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * KERNEL-phase registration of the loot chest module's own YAML defaults ({@code lootchests/loot_chests.yml},
 * {@code lootchests/tiers.yml}, moved out of {@code gangland-impl}'s {@code KernelConfig.fileManager()} — WS3 G2).
 * The five-argument {@link FileHandler} copies the bundled default out of the MODULE jar (parent-first loader), so
 * the same path must no longer exist in the core jar. Runs in KERNEL, after {@code KernelConfig} produced the
 * {@link FileManager} (parameter = ordering edge) — mirrors {@code CiviliansYamlConfig}.
 */
@CustomLog
@Configuration(phase = Phase.KERNEL)
public class LootChestFileConfig {

	private final JavaPlugin plugin;

	public LootChestFileConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public LootChestFiles lootChestFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		fileManager.addFile(new FileHandler(plugin, "loot_chests", "lootchests", ".yml", moduleLoader.classLoader()),
		                    true);
		fileManager.addFile(new FileHandler(plugin, "tiers", "lootchests", ".yml", moduleLoader.classLoader()), true);
		return new LootChestFiles();
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class LootChestFiles { }
}
