package org.luckyraven.gangland.turf.npc.config;

import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderConfig;
import org.luckyraven.gangland.turf.npc.guard.CopGuardConfig;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.util.Objects;

/**
 * Reads {@code turf/turf_npcs.yml} into the two settings POJOs turf's own NPC managers consume (moved from
 * cops-n-crooks in group I, T-I5). One file, one section per turf-NPC role:
 * <ul>
 *   <li>{@code Powerup_Npc} — the per-turf Quartermaster's display + protection settings</li>
 *   <li>{@code Defender} — deploy-side knobs only (civilian type id from {@code civilians.yml}, targeting radius,
 *       lifespan). Actual defender stats / model / equipment / AI tuning live in {@code civilians.yml} under the
 *       referenced type id — defenders ARE civilians, just with their target hand-picked at deploy time.</li>
 * </ul>
 */
@CustomLog
public final class TurfNpcsConfigLoader {

	private static final String FILE_NAME = "turf_npcs";

	private final FileHandler fileHandler;

	@Getter
	private TurfPowerupSettings powerupSettings;
	@Getter
	private TurfDefenderConfig  defenderConfig;
	@Getter
	private CopGuardConfig      copGuardConfig;

	public TurfNpcsConfigLoader(FileManager fileManager) {
		try {
			fileManager.checkFileLoaded(FILE_NAME);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(FILE_NAME));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
		load();
	}

	public void load() {
		ConfigReport report = new ConfigReport();
		NodeReader   root   = FileHandlerReader.read(fileHandler, report);

		MappingNode powerupNode = root.get("Powerup_Npc").asMapping().required().orNull();
		if (powerupNode != null) {
			NodeReader pr = NodeReader.of(powerupNode, report);
			powerupSettings = new TurfPowerupSettings(
					pr.get("Type_Id").asString().orDefault("quartermaster"),
					radius(pr));
		} else {
			powerupSettings = new TurfPowerupSettings("quartermaster", 32.0);
		}

		MappingNode defNode = root.get("Defender").asMapping().required().orNull();
		if (defNode != null) {
			NodeReader dr = NodeReader.of(defNode, report);
			defenderConfig = new TurfDefenderConfig(
					dr.get("Type_Id").asString().orDefault("turf_defender"),
					dr.get("Targeting_Radius").asDouble().min(1.0).orDefault(32.0),
					dr.get("Lifespan_Seconds").asInt().min(1).orDefault(600));
		} else {
			defenderConfig = new TurfDefenderConfig("turf_defender", 32.0, 600);
		}

		MappingNode copNode = root.get("Cop_Response").asMapping().orNull();
		if (copNode != null) {
			NodeReader cr = NodeReader.of(copNode, report);
			copGuardConfig = new CopGuardConfig(
					cr.get("Enabled").asBool().orDefault(true),
					radius(cr),
					cr.get("Include_Allies").asBool().orDefault(true));
		} else {
			copGuardConfig = new CopGuardConfig(true, 32.0, true);
		}

		if (!report.isEmpty()) report.log(log);
	}

	/**
	 * A {@code Targeting_Radius} clamped up to one block (absent means 32). Keystone's {@code min} would reject a value
	 * below the floor and fall back to the default, so the clamp is explicit.
	 */
	private static double radius(NodeReader reader) {
		return Math.max(1.0, reader.get("Targeting_Radius").asDouble().orDefault(32.0));
	}
}
