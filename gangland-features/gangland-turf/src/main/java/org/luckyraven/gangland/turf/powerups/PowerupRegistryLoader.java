package org.luckyraven.gangland.turf.powerups;

import lombok.CustomLog;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * Reads {@code turf/turf_powerups.yml} into the shared {@link PowerupRegistry}. Mirrors the trader-traits / bank-tiers
 * loader idiom: builds a fresh map per load, swaps it in atomically via {@link PowerupRegistry#replaceAll(Map)}, and
 * keeps the previous catalogue in place if the new file parses to zero entries (so a typo can't wipe live powerups
 * while the server is running).
 *
 * <p>Entries with an unknown {@code Effect_Type} or a missing required field are skipped with a warning — the
 * rest of the catalogue still loads. The "Powerups" parent key is required; everything under it is read as a map of
 * lookup-id → definition.
 */
@CustomLog
public final class PowerupRegistryLoader implements BeanLifecycle {

	private static final String FILE_NAME    = "turf_powerups";
	private static final String POWERUPS_KEY = "Powerups";

	private final FileHandler     fileHandler;
	private final PowerupRegistry registry;

	public PowerupRegistryLoader(PowerupRegistry registry, FileManager fileManager) {
		this.registry = registry;

		try {
			fileManager.checkFileLoaded(FILE_NAME);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(FILE_NAME));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		load();
	}

	@Override
	public void onClear() {
		registry.replaceAll(Collections.emptyMap());
	}

	public void load() {
		ConfigReport report = new ConfigReport();
		NodeReader   root   = FileHandlerReader.read(fileHandler, report);

		MappingNode powerupsNode = root.get(POWERUPS_KEY).asMapping().required().orNull();
		if (powerupsNode == null) {
			if (!report.isEmpty()) report.log(log);
			log.warn("turf_powerups.yml is missing the '{}' section; keeping previous registry state", POWERUPS_KEY);
			return;
		}

		Map<String, PowerupDefinition> parsed   = new LinkedHashMap<>();
		NodeReader                     powerups = NodeReader.of(powerupsNode, report);
		for (String rawId : powerups.keys()) {
			String      id    = rawId.toLowerCase(Locale.ROOT);
			MappingNode entry = powerups.get(rawId).asMapping().required().orNull();
			if (entry == null) continue;

			PowerupDefinition def = parseDefinition(id, NodeReader.of(entry, report));
			if (def != null) {
				parsed.put(id, def);
			}
		}

		if (!report.isEmpty()) report.log(log);

		if (parsed.isEmpty()) {
			log.warn("Turf powerups parsed to zero entries; keeping previous registry state");
			return;
		}

		registry.replaceAll(parsed);
		log.debug("Loaded {} turf powerup(s): {}", parsed.size(), parsed.keySet());
	}

	private PowerupDefinition parseDefinition(String id, NodeReader r) {
		String displayName     = r.get("Display_Name").asString().orDefault(id);
		String effectTypeRaw   = r.get("Effect_Type").asString().required().orDefault("");
		double magnitude       = r.get("Magnitude").asDouble().required().orDefault(1.0);
		int    durationSeconds = r.get("Duration_Seconds").asInt().min(1).required().orDefault(60);
		String costRaw         = r.get("Cost").asString().orDefault("0");

		EffectType effectType;
		try {
			effectType = EffectType.valueOf(effectTypeRaw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			log.warn("Powerup '{}' has unknown Effect_Type '{}' — skipping", id, effectTypeRaw);
			return null;
		}

		BigDecimal cost;
		try {
			cost = new BigDecimal(costRaw.replace("_", "").trim());
		} catch (NumberFormatException exception) {
			log.warn("Powerup '{}' has unparseable Cost '{}' — skipping", id, costRaw);
			return null;
		}

		return new PowerupDefinition(id, displayName, cost, effectType, magnitude, durationSeconds);
	}
}
