package org.luckyraven.gangland.gadget.grapple.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.util.Placeholder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Loads {@code items/grapples.yml} and holds the resulting {@link Grapple} catalogue, mirroring
 * {@code jetpack.config.JetpackAddon} (itself mirroring {@code car.config.CarAddon}). Folds the registry role
 * directly into this class — no separate {@code GrappleManager}, same as {@code JetpackAddon}.
 */
@CustomLog
public class GrappleAddon implements FileInitializer {

	private final Map<String, Grapple> grapples = new HashMap<>();

	private final Consumer<String> permissionRegistrar;
	private final FileHandler      fileHandler;

	/**
	 * Placeholder resolver injected by the impl side; threaded into every parsed {@link Grapple} via the builder so
	 * its display name and lore resolve {@code %gangland_*%} tokens at item-build time.
	 */
	@Nullable
	private final Placeholder placeholder;

	public GrappleAddon(Consumer<String> permissionRegistrar, FileManager fileManager,
	                    @Nullable Placeholder placeholder) {
		this.permissionRegistrar = permissionRegistrar;
		this.placeholder         = placeholder;

		try {
			String fileName = "grapples";

			fileManager.checkFileLoaded(fileName);

			this.fileHandler = Objects.requireNonNull(fileManager.getFile(fileName));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	public void register(String key, Grapple grapple) {
		grapples.put(key.toLowerCase(), grapple);
	}

	@Nullable
	public Grapple getGrapple(String key) {
		return grapples.get(key.toLowerCase());
	}

	public Map<String, Grapple> getGrapples() {
		return Collections.unmodifiableMap(grapples);
	}

	public void clear() {
		grapples.clear();
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		loadGrapples(fileHandler.getFileConfiguration());
	}

	void loadGrapples(FileConfiguration config) {
		List<String> loaded = new ArrayList<>();

		for (String key : config.getKeys(false)) {
			ConfigurationSection section = config.getConfigurationSection(key);
			if (section == null) continue;

			String materialString = section.getString("Material");
			String displayName    = section.getString("Display_Name");

			if (materialString == null || displayName == null) {
				log.warn("Grapple '{}' is missing Material or Display_Name - skipped.", key);
				continue;
			}

			Material material = XMaterial.matchXMaterial(materialString).map(XMaterial::get).orElse(null);

			if (material == null) {
				log.warn("Grapple '{}' has invalid Material '{}' - skipped.", key, materialString);
				continue;
			}

			int          customModelData = section.getInt("Custom_Model_Data", 0);
			List<String> lore            = section.getStringList("Lore");

			int     maxDistance          = section.getInt("Max_Distance", 25);
			double  maxPullSpeed         = section.getDouble("Max_Pull_Speed", 1.8);
			double  pullAcceleration     = section.getDouble("Pull_Acceleration", 0.35);
			double  arrivalDistance      = section.getDouble("Arrival_Distance", 1.5);
			int     cooldownSeconds      = section.getInt("Cooldown_Seconds", 8);
			int     maxDurationTicks     = section.getInt("Max_Duration_Ticks", 100);
			int     fallDamageGraceTicks = section.getInt("Fall_Damage_Grace_Ticks", 40);
			boolean requireLineOfSight   = section.getBoolean("Require_Line_Of_Sight", true);

			Grapple grapple = Grapple.builder()
			                         .grappleId(key)
			                         .displayName(displayName)
			                         .material(material)
			                         .customModelData(customModelData)
			                         .lore(lore.isEmpty() ? null : lore)
			                         .maxDistance(maxDistance)
			                         .maxPullSpeed(maxPullSpeed)
			                         .pullAcceleration(pullAcceleration)
			                         .arrivalDistance(arrivalDistance)
			                         .cooldownSeconds(cooldownSeconds)
			                         .maxDurationTicks(maxDurationTicks)
			                         .fallDamageGraceTicks(fallDamageGraceTicks)
			                         .requireLineOfSight(requireLineOfSight)
			                         .placeholder(placeholder)
			                         .build();

			register(key, grapple);
			permissionRegistrar.accept(grapple.getPermission());
			loaded.add(key);
		}

		log.debug("Loaded the following grapples:");
		log.debug(loaded);
	}
}
