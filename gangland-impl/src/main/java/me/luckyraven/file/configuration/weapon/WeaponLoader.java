package me.luckyraven.file.configuration.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.FolderLoader;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.configuration.InvalidConfigurationException;

public class WeaponLoader extends FolderLoader {

	private final Gangland gangland;

	public WeaponLoader(Gangland gangland) {
		super(gangland, "weapon");
		this.gangland = gangland;
	}

	@Override
	public void initialize() {
		FileManager fileManager = gangland.getInitializer().getFileManager();
		this.load(true, fileHandler -> {
			try {
				AmmunitionAddon ammunitionAddon = gangland.getInitializer().getAmmunitionAddon();
				gangland.getInitializer().getWeaponAddon().registerWeapon(ammunitionAddon, fileHandler);
			} catch (InvalidConfigurationException exception) {
				Gangland.getLog4jLogger()
						.info("There was a problem loading the weapon: {}", exception.getMessage(), exception);
			}
		}, fileManager);
	}

}
