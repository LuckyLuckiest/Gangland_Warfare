package me.luckyraven.file.configuration.weapon;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.FolderLoader;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.configuration.WeaponAddon;
import org.bukkit.configuration.InvalidConfigurationException;

@CustomLog
public class WeaponLoader extends FolderLoader {

	private final FileManager       fileManager;
	private final WeaponAddon       weaponAddon;
	private final AmmunitionManager ammunitionManager;

	public WeaponLoader(Gangland gangland,
	                    FileManager fileManager,
	                    WeaponAddon weaponAddon,
	                    AmmunitionManager ammunitionManager) {
		super(gangland, "weapon");
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
				log.info("There was a problem loading the weapon: {}", exception.getMessage(), exception);
			}
		}, fileManager);
	}

}
