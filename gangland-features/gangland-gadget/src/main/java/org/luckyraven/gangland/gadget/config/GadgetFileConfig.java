package org.luckyraven.gangland.gadget.config;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.util.Placeholder;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.contract.GadgetPhysicsConfigImpl;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * FILE-phase wiring for the gadget module: the {@code carAddon} and {@code gadgetPhysicsConfig} beans moved out of
 * the core's {@code FileConfig}. Must stay {@code Phase.FILE} — {@link CarAddon} is a {@code FileInitializer}, and
 * only the FILE-phase hook runs {@code FileManager.initializeAll()} between beans so {@code cars.yml} is loaded
 * before the next FILE bean reads it.
 */
@CustomLog
@Configuration(phase = Phase.FILE)
public class GadgetFileConfig {

	private final JavaPlugin plugin;

	public GadgetFileConfig(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Bean
	public GadgetPhysicsConfig gadgetPhysicsConfig(Settings settings) {
		return new GadgetPhysicsConfigImpl();
	}

	@Bean
	public CarAddon carAddon(PermissionManager permissionManager, FileManager fileManager,
	                         Placeholder placeholderService, ModuleLoader moduleLoader) {
		fileManager.addFile(new FileHandler(plugin, "cars", "items", ".yml", moduleLoader.classLoader()), true);

		CarAddon addon = new CarAddon(permissionManager::addPermission, fileManager, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}

	@Bean
	public JetpackAddon jetpackAddon(PermissionManager permissionManager, FileManager fileManager,
	                                 Placeholder placeholderService, ModuleLoader moduleLoader) {
		fileManager.addFile(new FileHandler(plugin, "jetpacks", "items", ".yml", moduleLoader.classLoader()), true);

		JetpackAddon addon = new JetpackAddon(permissionManager::addPermission, fileManager, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}

	/**
	 * WS7-G4 review I1: a module's own messages file is never the bare {@code messages} name — {@link FileManager}
	 * resolves purely by name (no per-module namespacing of its own), so a second module registering its own
	 * {@code messages.yml} would silently collide with gadget's. The precedent for every module: FileManager name
	 * {@code <module>_messages}, resource {@code <module>/<module>_messages.yml}.
	 */
	@Bean
	public JetpackMessages jetpackMessages(FileManager fileManager, ModuleLoader moduleLoader) {
		fileManager.addFile(new FileHandler(plugin, "gadget_messages", "gadget", ".yml", moduleLoader.classLoader()),
		                    true);

		JetpackMessages messages = new JetpackMessages(fileManager);
		fileManager.registerInitializer(messages);
		return messages;
	}
}
