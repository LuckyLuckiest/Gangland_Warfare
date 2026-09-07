package org.luckyraven.gangland.gadget.config;

import lombok.CustomLog;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.gadget.contract.GadgetPhysicsConfigImpl;
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

	private final Gangland gangland;

	public GadgetFileConfig(Gangland gangland) {
		this.gangland = gangland;
	}

	@Bean
	public GadgetPhysicsConfig gadgetPhysicsConfig(Settings settings) {
		return new GadgetPhysicsConfigImpl();
	}

	@Bean
	public CarAddon carAddon(PermissionManager permissionManager, FileManager fileManager,
	                         PlaceholderService placeholderService, ModuleLoader moduleLoader) {
		fileManager.addFile(new FileHandler(gangland, "cars", "items", ".yml", moduleLoader.classLoader()), true);

		CarAddon addon = new CarAddon(permissionManager::addPermission, fileManager, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}
}
