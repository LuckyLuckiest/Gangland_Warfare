package me.luckyraven.file.configuration.weapon;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.exception.PluginException;
import me.luckyraven.feature.weapon.ammo.Ammunition;
import me.luckyraven.file.FileManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.*;

public class AmmunitionAddon {

	private final Map<String, Ammunition> ammo;

	public AmmunitionAddon(FileManager fileManager) {
		this.ammo = new HashMap<>();

		FileConfiguration ammunition;
		try {
			fileManager.checkFileLoaded("ammunition");
			ammunition = Objects.requireNonNull(fileManager.getFile("ammunition")).getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		registerAmmunition(ammunition);
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

			Ammunition ammo = new Ammunition(key, name, xMaterial.parseMaterial(), lore);

			this.ammo.put(key, ammo);
			temp.add(key);
		}

		Gangland.getLog4jLogger().info("Loaded the following ammunition:");
		Gangland.getLog4jLogger().info(temp);
	}

	public Ammunition getAmmunition(String key) {
		return ammo.get(key);
	}

	public Set<String> getAmmunitionKeys() {
		return ammo.keySet();
	}

}
