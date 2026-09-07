package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

@CustomLog
@Configuration(phase = Phase.KERNEL)
public class CopsNCrooksYamlConfig {

	private final Gangland gangland;

	public CopsNCrooksYamlConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	/**
	 * Registers this module's YAML defaults with the host FileManager. The five-argument FileHandler copies the
	 * bundled default out of the MODULE jar (parent-first loader), so the same paths must no longer exist in the
	 * core jar. Runs in KERNEL, after KernelConfig produced the FileManager (parameter = ordering edge).
	 */
	@Bean
	public CopsNCrooksFiles copsNCrooksFiles(FileManager fileManager, ModuleLoader moduleLoader) {
		ClassLoader loader = moduleLoader.classLoader();
		fileManager.addFile(new FileHandler(gangland, "cops", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(gangland, "civilians", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(gangland, "trader_traits", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(gangland, "bank_tiers", "npc", ".yml", loader), true);
		fileManager.addFile(new FileHandler(gangland, "turf_npcs", "turf", ".yml", loader), true);
		return new CopsNCrooksFiles();
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class CopsNCrooksFiles { }
}
