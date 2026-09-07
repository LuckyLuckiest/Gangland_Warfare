package org.luckyraven.gangland.weapon.configuration;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.util.Placeholder;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.gangland.weapon.ammo.Ammunition;
import org.luckyraven.gangland.weapon.ammo.AmmunitionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure parser for the {@code ammunition.yml} file. Reads each ammunition entry and registers it into the provided
 * {@link AmmunitionManager}. Does not store any data itself.
 */
@CustomLog
public class AmmunitionAddon implements FileInitializer {

	private final FileHandler       fileHandler;
	private final AmmunitionManager ammunitionManager;
	/**
	 * Placeholder resolver injected by the impl side; propagated to each parsed {@link Ammunition} so its rendering
	 * path can resolve {@code %gangland_*%} tokens.
	 */
	@Nullable
	private final Placeholder       placeholder;

	public AmmunitionAddon(FileManager fileManager, @NotNull AmmunitionManager ammunitionManager,
	                       @Nullable Placeholder placeholder) {
		this.placeholder = placeholder;

		try {
			String fileName = "ammunition";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler       = Objects.requireNonNull(fileManager.getFile(fileName));
			this.ammunitionManager = ammunitionManager;
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		registerAmmunition(ammunitionManager, fileHandler.getFileConfiguration());
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

			int          customModelData = section.getInt("Custom_Model_Data", 0);
			List<String> lore            = section.getStringList("Lore");

			Ammunition ammo = new Ammunition(key, name, xMaterial.get(), customModelData, lore);
			ammo.setPlaceholder(placeholder);

			manager.register(key, ammo);
			temp.add(key);
		}

		log.debug("Loaded the following ammunition:");
		log.debug(temp);
	}

}
