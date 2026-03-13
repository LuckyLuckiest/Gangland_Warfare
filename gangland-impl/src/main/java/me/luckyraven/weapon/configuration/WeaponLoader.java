package me.luckyraven.weapon.configuration;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.persistence.FolderLoader;
import org.bukkit.configuration.InvalidConfigurationException;

@CustomLog
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
				Initializer     initializer     = gangland.getInitializer();
				AmmunitionAddon ammunitionAddon = initializer.getAmmunitionAddon();
				initializer.getWeaponAddon().registerWeapon(ammunitionAddon, fileHandler);
			} catch (InvalidConfigurationException exception) {
				log.info("There was a problem loading the weapon: {}", exception.getMessage(), exception);
			}
		}, fileManager);
	}

}
