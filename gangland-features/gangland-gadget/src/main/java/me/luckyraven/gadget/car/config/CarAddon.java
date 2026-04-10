package me.luckyraven.gadget.car.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;

import me.luckyraven.exception.PluginException;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.CarManager;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileInitializer;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.util.Placeholder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@CustomLog
public class CarAddon extends CarManager implements FileInitializer {

	private final Consumer<String> permissionRegistrar;
	private final FileHandler      fileHandler;

	/**
	 * Placeholder resolver injected by the impl side; threaded into every parsed {@link Car} via the builder so its
	 * display name and lore resolve {@code %gangland_*%} tokens at item-build time.
	 */
	@Nullable
	private final Placeholder placeholder;

	public CarAddon(Consumer<String> permissionRegistrar, FileManager fileManager,
	                @Nullable Placeholder placeholder) {
		this.permissionRegistrar = permissionRegistrar;
		this.placeholder         = placeholder;

		try {
			String fileName = "cars";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
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
		loadCars(fileHandler.getFileConfiguration());
	}

	private void loadCars(FileConfiguration config) {
		List<String> loaded = new ArrayList<>();

		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null) continue;

			String materialString = section.getString("Material");
			String displayName    = section.getString("Display_Name");

			if (materialString == null || displayName == null) {
				log.warn("Car '{}' is missing Material or Display_Name - skipped.", key);
				continue;
			}

			Material material = XMaterial.matchXMaterial(materialString).orElse(XMaterial.MINECART).get();

			if (material == null) {
				log.warn("Car '{}' has invalid Material '{}' - skipped.", key, materialString);
				continue;
			}

			int          customModelData = section.getInt("Custom_Model_Data", 0);
			List<String> lore            = section.getStringList("Lore");

			// Vehicle section
			ConfigurationSection vehicleSection = section.getConfigurationSection("Vehicle");

			double maxSpeed     = 0.8;
			double acceleration = 0.04;
			double deceleration = 0.02;
			double turnSpeed    = 4.0;
			double maxHealth    = 100.0;

			if (vehicleSection != null) {
				maxSpeed     = vehicleSection.getDouble("Max_Speed", 0.8);
				acceleration = vehicleSection.getDouble("Acceleration", 0.04);
				deceleration = vehicleSection.getDouble("Deceleration", 0.02);
				turnSpeed    = vehicleSection.getDouble("Turn_Speed", 4.0);
				maxHealth    = vehicleSection.getDouble("Max_Health", 100.0);
			}

			// Fuel section
			ConfigurationSection fuelSection = section.getConfigurationSection("Fuel");

			boolean fuelEnabled = false;
			String  fuelKey     = "";
			int     maxFuel     = 0;

			if (fuelSection != null) {
				fuelEnabled = fuelSection.getBoolean("Enabled", false);
				fuelKey     = fuelSection.getString("Fuel_Key", "");
				maxFuel     = fuelSection.getInt("Max_Fuel", 0);
			}

			// Repair section
			ConfigurationSection repairSection = section.getConfigurationSection("Repair");

			int maxDurability = 500;

			if (repairSection != null) {
				maxDurability = repairSection.getInt("Max_Durability", 500);
			}

			Car car = Car.builder()
			             .carId(key)
			             .displayName(displayName)
			             .itemMaterial(material)
			             .customModelData(customModelData)
			             .lore(lore.isEmpty() ? null : lore)
			             .maxSpeed(maxSpeed)
			             .acceleration(acceleration)
			             .deceleration(deceleration)
			             .turnSpeed(turnSpeed)
			             .maxHealth(maxHealth)
			             .fuelEnabled(fuelEnabled)
			             .fuelKey(fuelKey)
			             .maxFuel(maxFuel)
			             .maxDurability(maxDurability)
			             .placeholder(placeholder)
			             .build();

			register(key, car);
			permissionRegistrar.accept(car.getPermission());
			loaded.add(key);
		}

		log.info("Loaded the following cars:");
		log.info(loaded);
	}
}
