package org.luckyraven.gangland.weapon;

import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.configuration.AmmunitionAddon;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;
import org.luckyraven.gangland.weapon.file.WeaponBlockRegenerationSettings;
import org.luckyraven.gangland.weapon.file.WeaponLoader;
import org.luckyraven.gangland.weapon.wearable.WearableAddon;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Phase;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.List;

/**
 * FILE-phase registration of the weapon module's own YAML defaults (weapon/ammunition/wearable data folders).
 * Every {@link FileHandler} here is built with {@link ModuleLoader#classLoader()} so it resolves the default from
 * the module jar (the same relative path the file used to ship at inside the core jar), rather than the core
 * classloader.
 */
@Configuration(phase = Phase.FILE)
public final class WeaponFileConfig {

	private final Gangland     gangland;
	private final ModuleLoader moduleLoader;

	public WeaponFileConfig(Gangland gangland, ModuleLoader moduleLoader) {
		this.gangland     = gangland;
		this.moduleLoader = moduleLoader;
	}

	/**
	 * Empty {@link AmmunitionManager} created in the FILE phase so {@link AmmunitionAddon} can populate it.
	 * {@code Settings} is an ordering-only parameter — the bean-graph edge that keeps this after core settings load.
	 */
	@Bean
	public AmmunitionManager ammunitionManager(Settings settings) {
		return new AmmunitionManager();
	}

	@Bean
	public AmmunitionAddon ammunitionAddon(FileManager fileManager, AmmunitionManager ammunitionManager,
	                                       PlaceholderService placeholderService) {
		// AmmunitionAddon resolves fileManager.getFile("ammunition") in its CONSTRUCTOR, so the handler must be
		// registered first. The default now ships in this module's jar at items/ammunition.yml.
		fileManager.addFile(new FileHandler(gangland, "ammunition", "items", ".yml", moduleLoader.classLoader()),
		                    true);
		AmmunitionAddon addon = new AmmunitionAddon(fileManager, ammunitionManager, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}

	@Bean
	public WearableAddon wearableAddon(PermissionManager permissionManager, FileManager fileManager,
	                                   PlaceholderService placeholderService) {
		fileManager.addFile(new FileHandler(gangland, "wearables", "items", ".yml", moduleLoader.classLoader()),
		                    true);
		WearableAddon addon = new WearableAddon(permissionManager::addPermission, fileManager, placeholderService);
		fileManager.registerInitializer(addon);
		return addon;
	}

	@Bean
	public WeaponBlockRegenerationSettings blockRegenerationSettings() {
		return new WeaponBlockRegenerationSettings();
	}

	/**
	 * {@code AmmunitionAddon} is an ordering-only parameter — the bean-graph edge that forces ammunition to load
	 * before weapons.
	 */
	@Bean
	public WeaponAddon weaponAddon(AmmunitionAddon ammunitionAddon, PlaceholderService placeholderService) {
		return new WeaponAddon(placeholderService);
	}

	/**
	 * {@link WeaponLoader} reads its own folder of YAML files (rifle, grenade, knife, flamethrower, syringe_gun) and
	 * registers them via {@link WeaponAddon}.
	 */
	@Bean
	public WeaponLoader weaponLoader(FileManager fileManager, AmmunitionManager ammunitionManager,
	                                 WeaponAddon weaponAddon) {
		WeaponLoader loader   = new WeaponLoader(gangland, fileManager, weaponAddon, ammunitionManager);
		ClassLoader  loaderCl = moduleLoader.classLoader();
		for (String name : List.of("rifle", "grenade", "knife", "flamethrower", "syringe_gun")) {
			loader.addExpectedFile(new FileHandler(gangland, name, "weapon", ".yml", loaderCl));
		}
		return loader;
	}
}
