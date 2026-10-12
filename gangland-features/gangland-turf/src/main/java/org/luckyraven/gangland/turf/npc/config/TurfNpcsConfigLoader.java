package org.luckyraven.gangland.turf.npc.config;

import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.gangland.turf.npc.defender.GarrisonComposition;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderConfig;
import org.luckyraven.gangland.turf.npc.guard.CopGuardConfig;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.FileHandlerReader;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Reads {@code turf/turf_npcs.yml} into the two settings POJOs turf's own NPC managers consume (moved from
 * cops-n-crooks in group I, T-I5). One file, one section per turf-NPC role:
 * <ul>
 *   <li>{@code Powerup_Npc} — the per-turf Quartermaster's display + protection settings</li>
 *   <li>{@code Garrison} — how the bought guards defend: wave size, most alive, wave cooldown, stand-down time,
 *       leash margin and the roles the guards take in spawn order ({@code Composition}) (they ride on {@link TurfDefenderConfig})</li>
 *   <li>{@code Defender} — deploy-side knobs only (civilian type id from {@code civilians.yml}, targeting radius,
 *       lifespan). Actual defender stats / model / equipment / AI tuning live in {@code civilians.yml} under the
 *       referenced type id — defenders ARE civilians, just with their target hand-picked at deploy time.</li>
 * </ul>
 */
@CustomLog
public final class TurfNpcsConfigLoader implements BeanLifecycle {

	private static final String FILE_NAME = "turf_npcs";
	public static final int DEFAULT_OWNER_ALERT_COOLDOWN_SECONDS = 60;

	private final FileHandler fileHandler;

	@Getter
	private TurfPowerupSettings powerupSettings;
	@Getter
	private TurfDefenderConfig  defenderConfig;
	@Getter
	private CopGuardConfig      copGuardConfig;
	@Getter
	private int                 ownerAlertCooldownSeconds = DEFAULT_OWNER_ALERT_COOLDOWN_SECONDS;

	public TurfNpcsConfigLoader(FileManager fileManager) {
		try {
			fileManager.checkFileLoaded(FILE_NAME);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(FILE_NAME));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
		load();
	}

	/**
	 * TF-36: {@code /glw reload} re-reads the file before this pass, so a later lifecycle pass re-parses it. The first
	 * pass skips the parse because the constructor already loaded it.
	 */
	@Override
	public void onInitialize(boolean firstLoad) {
		if (!firstLoad) {
			load();
		}
	}

	public void load() {
		ConfigReport report = new ConfigReport();
		NodeReader   root   = FileHandlerReader.read(fileHandler, report);

		MappingNode powerupNode = root.get("Powerup_Npc").asMapping().required().orNull();
		if (powerupNode != null) {
			NodeReader pr = NodeReader.of(powerupNode, report);
			powerupSettings = new TurfPowerupSettings(
					pr.get("Type_Id").asString().orDefault("quartermaster"),
					radius(pr),
					pr.get("Display_Name").asString().orDefault("Quartermaster"),
					Math.max(0.0, pr.get("Placement_Cost").asDouble().orDefault(5000.0)));
		} else {
			powerupSettings = new TurfPowerupSettings("quartermaster", 32.0, "Quartermaster", 5000.0);
		}

		MappingNode garNode = root.get("Garrison").asMapping().orNull();
		NodeReader  gr      = garNode == null ? null : NodeReader.of(garNode, report);
		int waveSize = gr == null ? TurfDefenderConfig.DEFAULT_WAVE_SIZE
		                          : gr.get("Wave_Size").asInt().min(1).orDefault(TurfDefenderConfig.DEFAULT_WAVE_SIZE);
		int maxAlive = gr == null ? TurfDefenderConfig.DEFAULT_MAX_ALIVE
		                          : gr.get("Max_Alive").asInt().min(1).orDefault(TurfDefenderConfig.DEFAULT_MAX_ALIVE);
		int waveCooldown = gr == null ? TurfDefenderConfig.DEFAULT_WAVE_COOLDOWN_SECONDS
		                              : gr.get("Wave_Cooldown_Seconds").asInt().min(0)
		                                  .orDefault(TurfDefenderConfig.DEFAULT_WAVE_COOLDOWN_SECONDS);
		int standDown = gr == null ? TurfDefenderConfig.DEFAULT_STAND_DOWN_SECONDS
		                           : gr.get("Stand_Down_Seconds").asInt().min(0)
		                               .orDefault(TurfDefenderConfig.DEFAULT_STAND_DOWN_SECONDS);
		int leashMargin = gr == null ? TurfDefenderConfig.DEFAULT_LEASH_MARGIN
		                             : gr.get("Leash_Margin").asInt().min(0)
		                                 .orDefault(TurfDefenderConfig.DEFAULT_LEASH_MARGIN);

		double bountyShare = gr == null ? TurfDefenderConfig.DEFAULT_BOUNTY_SHARE
		                                : Math.max(0.0, Math.min(1.0, gr.get("Bounty_Share").asDouble()
		                                                                .orDefault(TurfDefenderConfig.DEFAULT_BOUNTY_SHARE)));
		int recycleSeconds = gr == null ? TurfDefenderConfig.DEFAULT_RECYCLE_SECONDS
		                                : gr.get("Recycle_Seconds").asInt().min(0)
		                                    .orDefault(TurfDefenderConfig.DEFAULT_RECYCLE_SECONDS);

		double holdRadius = gr == null ? TurfDefenderConfig.DEFAULT_HOLD_RADIUS
		                               : Math.max(1.0, gr.get("Hold_Radius").asDouble()
		                                                .orDefault(TurfDefenderConfig.DEFAULT_HOLD_RADIUS));

		// absent = the shipped composition; present (even empty) = exactly what the file says
		GarrisonComposition composition = gr != null && gr.has("Composition")
		                                  ? GarrisonComposition.parse(gr.get("Composition").asList().ofStrings().orEmpty(),
		                                                              log::warn)
		                                  : GarrisonComposition.DEFAULT;

		MappingNode defNode = root.get("Defender").asMapping().required().orNull();
		if (defNode != null) {
			NodeReader dr = NodeReader.of(defNode, report);
			defenderConfig = new TurfDefenderConfig(
					dr.get("Type_Id").asString().orDefault("turf_defender"),
					dr.get("Targeting_Radius").asDouble().min(1.0).orDefault(32.0),
					dr.get("Lifespan_Seconds").asInt().min(1).orDefault(600),
					waveSize, maxAlive, waveCooldown, standDown, leashMargin, composition, bountyShare,
					recycleSeconds, holdRadius);
		} else {
			defenderConfig = new TurfDefenderConfig("turf_defender", 32.0, 600, waveSize, maxAlive, waveCooldown,
			                                        standDown, leashMargin, composition, bountyShare,
			                                        recycleSeconds, holdRadius);
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

		MappingNode alertNode = root.get("Owner_Alerts").asMapping().orNull();
		ownerAlertCooldownSeconds = alertNode == null ? DEFAULT_OWNER_ALERT_COOLDOWN_SECONDS
		                                              : Math.max(0, NodeReader.of(alertNode, report).get("Cooldown_Seconds")
		                                                                 .asInt()
		                                                                 .orDefault(DEFAULT_OWNER_ALERT_COOLDOWN_SECONDS));

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
