package org.luckyraven.gangland.gadget.grapple.config;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.gangland.gadget.grapple.GrappleService;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.sound.SoundEffect;
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

	/** The client clamps every axis of a velocity packet to this many blocks per tick. */
	private static final double MAX_PACKET_SPEED = 3.9;

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

			int maxDistance = section.getInt("Max_Distance", 25);
			if (maxDistance > GrappleService.HOOK_RANGE_LIMIT) {
				log.warn("Grapple '{}' Max_Distance {} is too close to the vanilla fishing-hook limit (32 blocks) - "
				         + "clamped to {}.", key, maxDistance, GrappleService.HOOK_RANGE_LIMIT);
				maxDistance = GrappleService.HOOK_RANGE_LIMIT;
			}

			// Velocity packets clamp each axis to 3.9 blocks/tick client-side, so nothing faster ever arrives.
			double  maxPullSpeed         = clamp(section.getDouble("Max_Pull_Speed", 1.8), 0.1, MAX_PACKET_SPEED);
			double  pullAcceleration     = Math.max(section.getDouble("Pull_Acceleration", 0.05), 0.01);
			double  reelSpeed            = Math.max(section.getDouble("Reel_Speed", 0.3), 0.01);
			double  minRopeLength        = Math.max(section.getDouble("Min_Rope_Length", 3.0), 0.5);
			// Fix round 1 minor: Arrival_Distance <= 0 makes the anchor-relative direction vector's normalize()
			// produce NaN velocity the tick the player reaches (0,0,0) distance from it - clamp to a small positive
			// floor instead of trusting the YAML.
			double  arrivalDistance      = Math.max(section.getDouble("Arrival_Distance", 3.5), 0.1);
			int     cooldownSeconds      = section.getInt("Cooldown_Seconds", 8);
			int     fallDamageGraceTicks = section.getInt("Fall_Damage_Grace_Ticks", 40);
			boolean requireLineOfSight   = section.getBoolean("Require_Line_Of_Sight", false);
			// Floor keeps a typo'd 0 from leaving the hook hanging in the air.
			double  shotSpeed            = clamp(section.getDouble("Shot_Speed", MAX_PACKET_SPEED), 0.5,
			                                     MAX_PACKET_SPEED);
			int     missCooldownTicks    = Math.max(section.getInt("Miss_Cooldown_Ticks", 10), 0);

			Grapple grapple = Grapple.builder()
			                         .grappleId(key)
			                         .displayName(displayName)
			                         .material(material)
			                         .customModelData(customModelData)
			                         .lore(lore.isEmpty() ? null : lore)
			                         .maxDistance(maxDistance)
			                         .maxPullSpeed(maxPullSpeed)
			                         .pullAcceleration(pullAcceleration)
			                         .reelSpeed(reelSpeed)
			                         .minRopeLength(minRopeLength)
			                         .arrivalDistance(arrivalDistance)
			                         .cooldownSeconds(cooldownSeconds)
			                         .fallDamageGraceTicks(fallDamageGraceTicks)
			                         .requireLineOfSight(requireLineOfSight)
			                         .shotSpeed(shotSpeed)
			                         .missCooldownTicks(missCooldownTicks)
			                         .fireSound(sound(section, "Fire_Sound", "ITEM_CROSSBOW_SHOOT"))
			                         .attachSound(sound(section, "Attach_Sound", "ENTITY_ARROW_HIT"))
			                         .cooldownReadySound(sound(section, "Cooldown_Ready_Sound",
			                                                   "ITEM_CROSSBOW_LOADING_END"))
			                         .placeholder(placeholder)
			                         .build();

			register(key, grapple);
			permissionRegistrar.accept(grapple.getPermission());
			loaded.add(key);
		}

		log.debug("Loaded the following grapples:");
		log.debug(loaded);
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(value, max));
	}

	/**
	 * A vanilla sound name (resolved through XSound at play time), or null for an empty value, which means silent.
	 */
	@Nullable
	private static SoundEffect sound(ConfigurationSection section, String key, String fallback) {
		String name = section.getString(key, fallback);
		if (name == null || name.isBlank()) return null;
		return new SoundEffect(SoundEffect.SoundType.VANILLA, name, 1.0f, 1.0f);
	}
}
