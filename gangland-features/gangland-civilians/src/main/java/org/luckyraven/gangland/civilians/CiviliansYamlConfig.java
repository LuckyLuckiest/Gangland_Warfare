package org.luckyraven.gangland.civilians;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * KERNEL-phase registration of the civilians module's own YAML defaults ({@code npc/civilians.yml}, moved out of
 * cops-n-crooks — T-H4; {@code npc/civilian_messages.yml}/{@code npc/civilian_messages_es.yml} — WS6 G3, moved off
 * {@code gangland-api}'s {@code Messages} enum, picked by {@code Settings.getLanguagePicked()} via
 * {@code CivilianMessages}/{@code LocalizedModuleYaml}). The five-argument {@link FileHandler} copies the bundled
 * default out of the MODULE jar (parent-first loader), so the same path must no longer exist in the cops-n-crooks
 * jar. Runs in KERNEL, after {@code KernelConfig} produced the {@link FileManager} (parameter = ordering edge).
 */
@CustomLog
@Configuration(phase = Phase.KERNEL)
public class CiviliansYamlConfig {

	private final JavaPlugin plugin;

	public CiviliansYamlConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public CiviliansFiles civiliansFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		fileManager.addFile(new FileHandler(plugin, "civilians", "npc", ".yml", moduleLoader.classLoader()), true);
		fileManager.addFile(new FileHandler(plugin, "civilian_messages", "npc", ".yml", moduleLoader.classLoader()),
		                    true);
		fileManager.addFile(new FileHandler(plugin, "civilian_messages_es", "npc", ".yml",
		                                    moduleLoader.classLoader()), true);
		return new CiviliansFiles();
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class CiviliansFiles { }
}
