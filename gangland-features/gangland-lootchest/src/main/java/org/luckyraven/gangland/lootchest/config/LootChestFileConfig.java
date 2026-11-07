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
 * {@code lootchests/tiers.yml}, moved out of {@code gangland-impl}'s {@code KernelConfig.fileManager()} — WS3 G2;
 * {@code lootchests/lootchest_messages.yml}, {@code lootchests/loot_chest_settings.yml} — WS3 G4, moved off
 * {@code gangland-api}'s {@code Messages}/{@code Settings}; {@code lootchests/lootchest_messages_es.yml} — WS3 G4
 * fix round 1 / W53 F1, the Spanish strings {@code GanglandLootChestMessages} picks by
 * {@code Settings.getLanguagePicked()}). The five-argument {@link FileHandler} copies the bundled default out of
 * the MODULE jar (parent-first loader), so the same path must no longer exist in the core jar. Runs in KERNEL,
 * after {@code KernelConfig} produced the {@link FileManager} (parameter = ordering edge) — mirrors
 * {@code CiviliansYamlConfig}.
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
		add(fileManager, moduleLoader, "loot_chests");
		add(fileManager, moduleLoader, "tiers");
		add(fileManager, moduleLoader, "lootchest_messages");
		add(fileManager, moduleLoader, "lootchest_messages_es");
		add(fileManager, moduleLoader, "loot_chest_settings");
		return new LootChestFiles();
	}

	private void add(FileManager fileManager, ModuleLoader moduleLoader, String name) {
		fileManager.addFile(new FileHandler(plugin, name, "lootchests", ".yml", moduleLoader.classLoader()), true);
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class LootChestFiles { }
}
