package org.luckyraven.gangland.copsncrooks.config;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@CustomLog
@Configuration(phase = Phase.KERNEL)
public class CopsNCrooksYamlConfig {

	/** The module named in the settings.yml migration warnings of {@code MovedSetting} (0.15.1). */
	public static final String MODULE_ID = "cops-n-crooks";

	/** The module's own folder, in the module jar and in the data folder. */
	static final String DIRECTORY        = "copsncrooks";
	/** Where these files lived before the module got its own folder; moved out once by {@link #relocateLegacy}. */
	static final String LEGACY_DIRECTORY = "npc";

	/**
	 * cops: tiers and spawning. cop_roles (0.13.0): squad roles, its own file so a server with an older cops.yml
	 * still gets the commented catalogue. cop_radio_messages(_es) (phase H12): police-radio lines, picked by
	 * Settings.getLanguagePicked() via CopRadioMessages/LocalizedModuleYaml. wanted/wanted_messages (0.15.0): chase
	 * tuning (heat, evasion, HUD, charge sheet) and its text. detainment (0.15.1): jail, bail, bribe and sentence
	 * knobs, formerly settings.yml Detainment. setup (0.16.0): the admin setup wand's item, outline and messages.
	 */
	static final String[] FILES = {"cops", "cop_roles", "cop_radio_messages", "cop_radio_messages_es", "wanted",
	                               "wanted_messages", "detainment", "setup"};

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
		for (String name : FILES) {
			relocateLegacy(plugin.getDataFolder(), name);
			fileManager.addFile(new FileHandler(plugin, name, DIRECTORY, ".yml", loader), true);
		}
		// turf_npcs.yml moved to the turf module's own TurfModuleFileConfig (group I) alongside the turf-NPC code
		// it configures. trader_traits.yml/bank_tiers.yml moved to gangland-npc-shops' own NpcShopsYamlConfig
		// (group J, T-J5) alongside the trader/banker code they configure.
		return new CopsNCrooksFiles();
	}

	/**
	 * Moves an admin's {@code npc/<name>.yml} to {@code copsncrooks/<name>.yml} so their tuning survives the folder
	 * change. Runs before the FileHandler would otherwise write the shipped default into the new folder. A file
	 * already in the new folder wins and the old one is left alone with a warning.
	 *
	 * @return true when the file was moved
	 */
	static boolean relocateLegacy(File dataFolder, String name) {
		File legacy = new File(new File(dataFolder, LEGACY_DIRECTORY), name + ".yml");
		File target = new File(new File(dataFolder, DIRECTORY), name + ".yml");

		if (!legacy.isFile()) return false;
		if (target.exists()) {
			log.warn("{} is ignored: {} is the file in use. Delete the old one once its values are copied over.",
			         legacy.getPath(), target.getPath());
			return false;
		}

		try {
			Files.createDirectories(target.toPath().getParent());
			Files.move(legacy.toPath(), target.toPath());
			log.info("Moved {} to {}", legacy.getPath(), target.getPath());
			return true;
		} catch (IOException exception) {
			log.warn("Could not move {} to {}, the shipped default is used instead: {}", legacy.getPath(),
			         target.getPath(), exception.getMessage());
			return false;
		}
	}

	/** Marker so the registration above is an ordinary @Bean in the phased pipeline. */
	public static final class CopsNCrooksFiles { }
}
