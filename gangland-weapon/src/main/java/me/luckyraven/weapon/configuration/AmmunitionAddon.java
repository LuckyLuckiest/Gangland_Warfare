package me.luckyraven.weapon.configuration;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.FileManager;
import me.luckyraven.weapon.ammo.Ammunition;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.*;

public class AmmunitionAddon {

	private static final Logger logger = LogManager.getLogger(AmmunitionAddon.class.getSimpleName());

	private final Map<String, Ammunition> ammunition;

	public AmmunitionAddon(FileManager fileManager) {
		this.ammunition = new HashMap<>();

		FileConfiguration ammunition;
		try {
			fileManager.checkFileLoaded("ammunition");
			ammunition = Objects.requireNonNull(fileManager.getFile("ammunition")).getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		registerAmmunition(ammunition);
	}

	public Ammunition getAmmunition(String key) {
		return ammunition.get(key);
	}

	public Set<String> getAmmunitionKeys() {
		return ammunition.keySet();
	}

	public void clear() {
		ammunition.clear();
	}

	private void registerAmmunition(FileConfiguration ammunition) {
		List<String> temp = new ArrayList<>();

		// initialize the data
		for (String key : ammunition.getKeys(false)) {
			ConfigurationSection section = ammunition.getConfigurationSection(key);

			if (section == null) return;

			String name           = section.getString("Name");
			String materialString = section.getString("Material");

			if (materialString == null || materialString.isEmpty()) return;

			Optional<XMaterial> xMaterialOptional = XMaterial.matchXMaterial(materialString);
			XMaterial           xMaterial         = xMaterialOptional.orElse(XMaterial.IRON_PICKAXE);

			List<String> lore = section.getStringList("Lore");

			Ammunition ammo = new Ammunition(key, name, xMaterial.get(), lore);

			this.ammunition.put(key, ammo);
			temp.add(key);
		}

		logger.info("Loaded the following ammunition:");
		logger.info(temp);
	}

}
