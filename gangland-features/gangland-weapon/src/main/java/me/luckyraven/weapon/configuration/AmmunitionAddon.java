package me.luckyraven.weapon.configuration;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure parser for the {@code ammunition.yml} file. Reads each ammunition entry and registers it into the provided
 * {@link AmmunitionManager}. Does not store any data itself.
 */
@CustomLog
public class AmmunitionAddon {

	private final FileManager fileManager;

	public AmmunitionAddon(FileManager fileManager) {
		this.fileManager = fileManager;
	}

	public void initialize(AmmunitionManager manager) {
		FileConfiguration fileConfiguration;
		try {
			String fileName = "ammunition";

			fileManager.checkFileLoaded(fileName);

			FileHandler file = Objects.requireNonNull(fileManager.getFile(fileName));
			fileConfiguration = file.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		registerAmmunition(manager, fileConfiguration);
	}

	private void registerAmmunition(AmmunitionManager manager, FileConfiguration ammunition) {
		List<String> temp = new ArrayList<>();

		for (String key : ammunition.getKeys(false)) {
			ConfigurationSection section = ammunition.getConfigurationSection(key);

			if (section == null) continue;

			String name           = section.getString("Name");
			String materialString = section.getString("Material");

			if (materialString == null || materialString.isEmpty()) continue;

			var xMaterialOptional = XMaterial.matchXMaterial(materialString);
			var xMaterial         = xMaterialOptional.orElse(XMaterial.IRON_PICKAXE);

			List<String> lore = section.getStringList("Lore");

			Ammunition ammo = new Ammunition(key, name, xMaterial.get(), lore);

			manager.register(key, ammo);
			temp.add(key);
		}

		log.info("Loaded the following ammunition:");
		log.info(temp);
	}

}
