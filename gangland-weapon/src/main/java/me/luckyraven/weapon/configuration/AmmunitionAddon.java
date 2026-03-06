package me.luckyraven.weapon.configuration;

import com.cryptomorin.xseries.XMaterial;
import lombok.extern.log4j.Log4j2;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.weapon.ammo.Ammunition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.*;

@Log4j2
public class AmmunitionAddon implements Comparator<Ammunition> {

	private final Map<String, Ammunition> ammunition;
	private final FileManager             fileManager;

	public AmmunitionAddon(FileManager fileManager) {
		this.ammunition  = new HashMap<>();
		this.fileManager = fileManager;
	}

	public void initialize() {
		FileConfiguration fileConfiguration;
		try {
			String fileName = "ammunition";

			fileManager.checkFileLoaded(fileName);

			FileHandler file = Objects.requireNonNull(fileManager.getFile(fileName));
			fileConfiguration = file.getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		registerAmmunition(fileConfiguration);
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

	@Override
	public int compare(Ammunition ammunition1, Ammunition ammunition2) {
		return ammunition1.compareTo(ammunition2);
	}

	private void registerAmmunition(FileConfiguration ammunition) {
		List<String> temp = new ArrayList<>();

		// initialize the data
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

			this.ammunition.put(key, ammo);
			temp.add(key);
		}

		log.info("Loaded the following ammunition:");
		log.info(temp);
	}

}
