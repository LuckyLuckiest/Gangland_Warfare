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

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
	private static final BigDecimal DEFAULT_GUARD_COST = BigDecimal.valueOf(1000);

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

		readGarrisonCost(root, report);
		readLimits(root, report);

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

	/** The guard price from {@code Garrison.Cost_Per_Guard}; absent or unparseable means 1000 (W2-Q, TF-17). */
	private void readGarrisonCost(NodeReader root, ConfigReport report) {
		MappingNode garrisonNode = root.get("Garrison").asMapping().orNull();
		if (garrisonNode == null) {
			registry.setGarrisonCost(DEFAULT_GUARD_COST);
			return;
		}
		String raw = NodeReader.of(garrisonNode, report).get("Cost_Per_Guard").asString().orDefault("1000");
		try {
			BigDecimal cost = new BigDecimal(raw.replace("_", "").trim());
			if (cost.signum() <= 0) {
				log.warn("turf_powerups.yml Garrison.Cost_Per_Guard '{}' must be above zero; using 1000", raw);
				registry.setGarrisonCost(DEFAULT_GUARD_COST);
				return;
			}
			if (cost.stripTrailingZeros().scale() > 0) {
				log.warn("turf_powerups.yml Garrison.Cost_Per_Guard '{}' is fractional; rounded to whole units", raw);
			}
			registry.setGarrisonCost(cost.setScale(0, RoundingMode.HALF_UP));
		} catch (NumberFormatException exception) {
			log.warn("turf_powerups.yml Garrison.Cost_Per_Guard '{}' is not a number; using 1000", raw);
			registry.setGarrisonCost(DEFAULT_GUARD_COST);
		}
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

		if (effectType == EffectType.GARRISON_DISCOUNT && (magnitude <= 0 || magnitude > 1)) {
			log.warn("turf_powerups.yml {}: GARRISON_DISCOUNT Magnitude {} is outside (0, 1]; guard prices are clamped to 10-100% of Cost_Per_Guard", id, magnitude);
		}

		PowerupRegistry.GuardLimits limits = registry.guardLimits();
		switch (effectType) {
			case GUARD_TOUGHNESS, BIGGER_GUARD_WAVES -> {
				if (!Double.isFinite(magnitude) || magnitude < 1.0) {
					log.warn("turf_powerups.yml {}: {} Magnitude {} must be at least 1 and finite - skipping", id,
					         effectType, magnitude);
					return null;
				}
				double limit = effectType == EffectType.GUARD_TOUGHNESS ? limits.maxToughness() : limits.maxWaves();
				if (magnitude > limit) {
					log.warn("turf_powerups.yml {}: {} Magnitude {} is above the limit {}; it is clamped there", id,
					         effectType, magnitude, limit);
				}
			}
			case FASTER_REINFORCEMENTS -> {
				if (!Double.isFinite(magnitude) || magnitude <= 0.0 || magnitude > 1.0) {
					log.warn("turf_powerups.yml {}: FASTER_REINFORCEMENTS Magnitude {} must be in (0, 1] - skipping", id,
					         magnitude);
					return null;
				}
				if (magnitude < limits.minWait()) {
					log.warn("turf_powerups.yml {}: FASTER_REINFORCEMENTS Magnitude {} is below the limit {}; it is clamped there",
					         id, magnitude, limits.minWait());
				}
			}
			default -> {
			}
		}

		if (effectType == EffectType.CAPTURE_DEFENSE_BONUS) {
			log.warn("{} has no effect since 0.17.0 - guards on the capture bar replaced it; entry skipped", id);
			return null;
		}

		BigDecimal cost;
		try {
			cost = new BigDecimal(costRaw.replace("_", "").trim());
		} catch (NumberFormatException exception) {
			log.warn("Powerup '{}' has unparseable Cost '{}' — skipping", id, costRaw);
			return null;
		}

		if (cost.signum() < 0) {
			log.warn("Powerup '{}' has a negative Cost '{}' - skipping", id, costRaw);
			return null;
		}
		if (cost.signum() == 0) {
			log.warn("Powerup '{}' has Cost 0 - it activates for free", id);
		}

		String       icon        = readIcon(id, effectType, r.get("Icon").asString().orDefault(""));
		List<String> description = r.get("Description").asList().ofStrings().orEmpty();

		return new PowerupDefinition(id, displayName, cost, effectType, magnitude, durationSeconds, icon, description);
	}

	/** The icon name when it is an item material, else null (the default of the effect), warning if it was set. */
	private static String readIcon(String id, EffectType effectType, String raw) {
		if (raw.isBlank()) {
			return null;
		}
		Material material = XMaterial.matchXMaterial(raw.trim()).map(XMaterial::get).orElse(null);
		if (material == null || !material.isItem()) {
			log.warn("turf_powerups.yml {}: Icon '{}' is not an item material; using {}", id, raw,
			         PowerupDefinition.defaultIcon(effectType));
			return null;
		}
		return raw.trim();
	}

	/** {@code Limits}: each key out of range or non-finite warns and keeps the code default. */
	private void readLimits(NodeReader root, ConfigReport report) {
		PowerupRegistry.GuardLimits defaults = PowerupRegistry.GuardLimits.DEFAULT;
		MappingNode                 node     = root.get("Limits").asMapping().orNull();
		if (node == null) {
			registry.setGuardLimits(defaults);
			return;
		}
		NodeReader r = NodeReader.of(node, report);
		registry.setGuardLimits(new PowerupRegistry.GuardLimits(
				limit(r, "Max_Guard_Toughness", defaults.maxToughness(), 1.0, Double.MAX_VALUE),
				limit(r, "Max_Guard_Waves", defaults.maxWaves(), 1.0, Double.MAX_VALUE),
				limit(r, "Min_Reinforcement_Wait", defaults.minWait(), Math.nextUp(0.0), 1.0)));
	}

	private static double limit(NodeReader r, String key, double fallback, double min, double max) {
		double value = r.get(key).asDouble().orDefault(fallback);
		if (!Double.isFinite(value) || value < min || value > max) {
			log.warn("turf_powerups.yml Limits.{} {} is out of range; using {}", key, value, fallback);
			return fallback;
		}
		return value;
	}
}
