package org.luckyraven.gangland.file.configuration.turf;

import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.gangland.copsncrooks.npc.turf.config.TurfPowerupSettings;
import org.luckyraven.gangland.copsncrooks.npc.turf.defender.TurfDefenderConfig;
import org.luckyraven.gangland.exception.PluginException;
import org.luckyraven.gangland.persistence.FileHandler;
import org.luckyraven.gangland.persistence.FileManager;
import org.luckyraven.gangland.persistence.config.ConfigReport;
import org.luckyraven.gangland.persistence.config.FileHandlerReader;
import org.luckyraven.gangland.persistence.config.MappingNode;
import org.luckyraven.gangland.persistence.config.NodeReader;

import java.io.IOException;
import java.util.Objects;

/**
 * Reads {@code turf/turf_npcs.yml} into the two settings POJOs the cops-n-crooks NPC managers consume. One file, one
 * section per turf-NPC role:
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
			powerupSettings = new TurfPowerupSettings(pr.get("Type_Id").asString().orDefault("quartermaster"));
		} else {
			powerupSettings = new TurfPowerupSettings("quartermaster");
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

		if (!report.isEmpty()) report.log(log);
	}
}
