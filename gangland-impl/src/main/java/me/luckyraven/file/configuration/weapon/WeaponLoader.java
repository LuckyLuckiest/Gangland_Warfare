package me.luckyraven.file.configuration.weapon;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.FolderLoader;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.InvalidConfigurationException;

public class WeaponLoader extends FolderLoader {

	private static final Logger logger = LogManager.getLogger(WeaponLoader.class.getSimpleName());

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
				Initializer     initializer     = gangland.getInitializer();
				AmmunitionAddon ammunitionAddon = initializer.getAmmunitionAddon();
				initializer.getWeaponAddon().registerWeapon(ammunitionAddon, fileHandler);
			} catch (InvalidConfigurationException exception) {
				logger.info("There was a problem loading the weapon: {}", exception.getMessage(), exception);
			}
		}, fileManager);
	}

}
