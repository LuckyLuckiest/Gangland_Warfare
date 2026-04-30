package org.luckyraven.gangland.file.configuration.weapon;

import lombok.CustomLog;
import org.bukkit.configuration.InvalidConfigurationException;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.FolderLoader;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;
import org.luckyraven.gangland.weapon.configuration.WeaponAddon;

@CustomLog
public class WeaponLoader extends FolderLoader {

	private final FileManager       fileManager;
	private final WeaponAddon       weaponAddon;
	private final AmmunitionManager ammunitionManager;

	public WeaponLoader(Gangland gangland,
	                    FileManager fileManager,
	                    WeaponAddon weaponAddon,
	                    AmmunitionManager ammunitionManager) {
		super(gangland, "weapon", fileManager);
		this.fileManager       = fileManager;
		this.weaponAddon       = weaponAddon;
		this.ammunitionManager = ammunitionManager;
	}

	@Override
	public void initialize() {
		this.load(true, fileHandler -> {
			try {
				weaponAddon.registerWeapon(ammunitionManager, fileHandler);
			} catch (InvalidConfigurationException exception) {
				log.info("There was a problem loading the weapon: {}", exception.getMessage());
			}
		}, fileManager);
	}

}
