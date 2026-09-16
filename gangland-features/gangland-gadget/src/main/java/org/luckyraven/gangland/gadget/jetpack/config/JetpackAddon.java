package org.luckyraven.gangland.gadget.jetpack.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.util.Placeholder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Loads {@code items/jetpacks.yml} and holds the resulting {@link Jetpack} catalogue, mirroring
 * {@code car.config.CarAddon}. Folds {@code CarManager}'s registry role directly into this class (no separate
 * {@code JetpackManager} — the plan's file list names only {@code Jetpack}/{@code JetpackKey}/{@code config
 * .JetpackAddon}).
 */
@CustomLog
public class JetpackAddon implements FileInitializer {

	private final Map<String, Jetpack> jetpacks = new HashMap<>();

	private final Consumer<String> permissionRegistrar;
	private final FileHandler      fileHandler;

	/**
	 * Placeholder resolver injected by the impl side; threaded into every parsed {@link Jetpack} via the builder
	 * so its display name and lore resolve {@code %gangland_*%} tokens at item-build time.
	 */
	@Nullable
	private final Placeholder placeholder;

	public JetpackAddon(Consumer<String> permissionRegistrar, FileManager fileManager,
	                    @Nullable Placeholder placeholder) {
		this.permissionRegistrar = permissionRegistrar;
		this.placeholder         = placeholder;

		try {
			String fileName = "jetpacks";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	public void register(String key, Jetpack jetpack) {
		jetpacks.put(key.toLowerCase(), jetpack);
	}

	@Nullable
	public Jetpack getJetpack(String key) {
		return jetpacks.get(key.toLowerCase());
	}

	public Map<String, Jetpack> getJetpacks() {
		return Collections.unmodifiableMap(jetpacks);
	}

	public void clear() {
		jetpacks.clear();
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		loadJetpacks(fileHandler.getFileConfiguration());
	}

	void loadJetpacks(FileConfiguration config) {
		List<String> loaded = new ArrayList<>();

		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null) continue;

			String materialString = section.getString("Material");
			String name           = section.getString("Name");

			if (materialString == null || name == null) {
				log.warn("Jetpack '{}' is missing Material or Name - skipped.", key);
				continue;
			}

			Material material = XMaterial.matchXMaterial(materialString).map(XMaterial::get).orElse(null);

			if (material == null) {
				log.warn("Jetpack '{}' has invalid Material '{}' - skipped.", key, materialString);
				continue;
			}

			String fuelKey = section.getString("Fuel_Key");
			if (fuelKey == null || fuelKey.isBlank()) {
				log.warn("Jetpack '{}' is missing a mandatory Fuel_Key - skipped.", key);
				continue;
			}

			int maxFuel = section.getInt("Max_Fuel", 3600);
			if (maxFuel <= 0) {
				log.warn("Jetpack '{}' has a non-positive Max_Fuel ({}) - skipped (buildItem must never ship a "
				         + "permanently dry jetpack).", key, maxFuel);
				continue;
			}

			int          customModelData = section.getInt("Custom_Model_Data", 0);
			List<String> lore            = section.getStringList("Lore");

			double ascendPower         = section.getDouble("Ascend_Power", 0.2);
			double maxSpeedY           = section.getDouble("Max_Speed_Y", 0.45);
			int    fuelConsumptionRate = section.getInt("Fuel_Consumption_Rate", 1);

			ConfigurationSection soundsSection = section.getConfigurationSection("Sounds");
			Map<String, Object>  sounds        = soundsSection == null ? null : sectionToMap(soundsSection);

			ConfigurationSection bartizanTraitsSection = section.getConfigurationSection("Bartizan_Traits");

			double baseDamageReduction = 0.0;
			Map<String, Integer> traits = Map.of();

			if (bartizanTraitsSection != null) {
				baseDamageReduction = bartizanTraitsSection.getDouble("Base_Damage_Reduction", 0.0);
				traits              = traitsMap(bartizanTraitsSection.getConfigurationSection("Traits"));
			}

			boolean bartizanRegistered = false;
			if (Settings.isBartizanAvailable() && bartizanTraitsSection != null) {
				bartizanRegistered = JetpackBartizanTraitBridge.register(key, baseDamageReduction, traits);
			}

			Jetpack jetpack = Jetpack.builder()
			                         .jetpackId(key)
			                         .displayName(name)
			                         .material(material)
			                         .customModelData(customModelData)
			                         .lore(lore.isEmpty() ? null : lore)
			                         .fuelKey(fuelKey)
			                         .maxFuel(maxFuel)
			                         .ascendPower(ascendPower)
			                         .maxSpeedY(maxSpeedY)
			                         .fuelConsumptionRate(fuelConsumptionRate)
			                         .sounds(sounds)
			                         .bartizanRegistered(bartizanRegistered)
			                         .placeholder(placeholder)
			                         .build();

			register(key, jetpack);
			permissionRegistrar.accept(jetpack.getPermission());
			loaded.add(key);
		}

		log.debug("Loaded the following jetpacks:");
		log.debug(loaded);
	}

	/**
	 * Recursively converts a {@link ConfigurationSection} into a plain map — nested sections become nested maps,
	 * leaf values pass through as-is. Used for the {@code Sounds:} block, read later by G3's
	 * {@code JetpackTask.soundTag()}.
	 */
	private static Map<String, Object> sectionToMap(ConfigurationSection section) {
		Map<String, Object> map = new LinkedHashMap<>();
		for (String key : section.getKeys(false)) {
			Object value = section.get(key);
			if (value instanceof ConfigurationSection nested) {
				map.put(key, sectionToMap(nested));
			} else {
				map.put(key, value);
			}
		}
		return map;
	}

	/**
	 * Reads a {@code Traits:} section the same way Bartizan's own trait table reads {@code wearables.yml} —
	 * upper-case YAML keys, lower-cased on read ({@code Wearable.TRAIT_TABLE} is keyed lower-case).
	 */
	private static Map<String, Integer> traitsMap(@Nullable ConfigurationSection traitsSection) {
		if (traitsSection == null) return Map.of();

		Map<String, Integer> traits = new LinkedHashMap<>();
		for (String traitKey : traitsSection.getKeys(false)) {
			traits.put(traitKey.toLowerCase(), traitsSection.getInt(traitKey));
		}
		return traits;
	}
}
