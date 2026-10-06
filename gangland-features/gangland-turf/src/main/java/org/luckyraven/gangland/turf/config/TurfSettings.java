package org.luckyraven.gangland.turf.config;

import lombok.Getter;
import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The turf module's own tuning knobs, read from {@code turf/turf_settings.yml}. They lived in the core
 * {@code settings.yml} under {@code Turf:} until 0.15.1; every key still goes through {@link MovedSetting} so a
 * {@code settings.yml} that was tuned keeps working for one release. Parsed in {@link #initialize()} (at load and on
 * {@code /glw reload}) and cached - never re-read per call, because the fallback logs a warning.
 */
public final class TurfSettings implements FileInitializer {

	/** One capture sound: the XSeries name plus volume and pitch. */
	public record Tone(String name, float volume, float pitch) {
	}

	public static final String FILE_NAME = "turf_settings";

	private final FileHandler fileHandler;
	private final FileManager fileManager;
	/** The constructor already loaded once; the first {@link #initialize()} of the boot pass must not warn twice. */
	private       boolean     freshlyLoaded;

	@Getter private int           incomeIntervalMinutes;
	@Getter private BigDecimal    defaultIncomeAmount;
	@Getter private String        wandItemType;
	@Getter private int           visualizationDurationSeconds;
	@Getter private String        visualizationParticle;
	@Getter private boolean       showEnterTitle;
	@Getter private int           captureDurationSeconds;
	@Getter private int           captureUnclaimedPhase1Seconds;
	@Getter private int           captureUnclaimedPhase2Seconds;
	@Getter private int           captureCooldownMinutes;
	@Getter private int           captureAbandonGraceSeconds;
	@Getter private int           capturePostLogoffProtectionMinutes;
	@Getter private int           captureInactivityAutoReleaseDays;
	@Getter private boolean       captureSoundEnabled;
	@Getter private boolean       captureBroadcastGlobally;
	@Getter private List<Integer> captureProgressMilestones;
	@Getter private Tone          startSound;
	@Getter private Tone          completeSound;
	@Getter private Tone          failedSound;
	@Getter private Tone          tickSound;
	@Getter private Tone          unclaimedSound;
	@Getter private double        contributionDefenderPresenceTick;
	@Getter private double        contributionAttackerPresenceTick;
	@Getter private double        contributionCaptureCompleteBonus;
	@Getter private double        contributionDefenseSuccessBonus;

	/** Bean constructor: the file was registered by {@code TurfModuleFileConfig} in the KERNEL phase. */
	public TurfSettings(FileManager fileManager) {
		this(lookup(fileManager), fileManager);
	}

	public TurfSettings(FileHandler fileHandler, FileManager fileManager) {
		this.fileHandler = Objects.requireNonNull(fileHandler);
		this.fileManager = fileManager;
		load();
		this.freshlyLoaded = true;
	}

	private static FileHandler lookup(FileManager fileManager) {
		try {
			fileManager.checkFileLoaded(FILE_NAME);
			return Objects.requireNonNull(fileManager.getFile(FILE_NAME));
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
		if (freshlyLoaded) {
			freshlyLoaded = false;
			return;
		}
		load();
	}

	private void load() {
		MovedSetting moved = MovedSetting.of(fileHandler, fileManager, "turf");

		incomeIntervalMinutes = moved.getInt("Income_Interval_Minutes", "Turf.Income_Interval_Minutes", 10);
		defaultIncomeAmount   = moved.getMoney("Default_Income_Amount", "Turf.Default_Income_Amount", "100");
		// The code default was BLAZE_ROD but the shipped settings.yml said CARROT_ON_A_STICK; the shipped value is
		// what every server saw, so it is the default here.
		wandItemType                 = moved.getString("Wand_Item_Type", "Turf.Wand_Item_Type", "CARROT_ON_A_STICK");
		visualizationDurationSeconds = moved.getInt("Visualization_Duration_Seconds",
		                                            "Turf.Visualization_Duration_Seconds", 30);
		visualizationParticle        = moved.getString("Visualization_Particle", "Turf.Visualization_Particle",
		                                               "FLAME");
		showEnterTitle               = moved.getBoolean("Show_Enter_Title", "Turf.Show_Enter_Title", true);

		captureDurationSeconds             = moved.getInt("Capture.Duration_Seconds",
		                                                  "Turf.Capture.Duration_Seconds", 180);
		captureUnclaimedPhase1Seconds      = moved.getInt("Capture.Unclaimed_Phase1_Seconds",
		                                                  "Turf.Capture.Unclaimed_Phase1_Seconds", 90);
		captureUnclaimedPhase2Seconds      = moved.getInt("Capture.Unclaimed_Phase2_Seconds",
		                                                  "Turf.Capture.Unclaimed_Phase2_Seconds", 90);
		captureCooldownMinutes             = moved.getInt("Capture.Cooldown_Minutes",
		                                                  "Turf.Capture.Cooldown_Minutes", 15);
		captureAbandonGraceSeconds         = moved.getInt("Capture.Abandon_Grace_Seconds",
		                                                  "Turf.Capture.Abandon_Grace_Seconds", 15);
		capturePostLogoffProtectionMinutes = moved.getInt("Capture.Post_Logoff_Protection_Minutes",
		                                                  "Turf.Capture.Post_Logoff_Protection_Minutes", 10);
		captureInactivityAutoReleaseDays   = moved.getInt("Capture.Inactivity_Auto_Release_Days",
		                                                  "Turf.Capture.Inactivity_Auto_Release_Days", 10);
		captureSoundEnabled                = moved.getBoolean("Capture.Enable_Sound",
		                                                      "Turf.Capture.Enable_Sound", true);
		captureBroadcastGlobally           = moved.getBoolean("Capture.Broadcast_Globally",
		                                                      "Turf.Capture.Broadcast_Globally", true);
		captureProgressMilestones          = milestones(moved);

		startSound     = tone(moved, "Start", "BLOCK_NOTE_BLOCK_PLING", 1.0, 1.0);
		completeSound  = tone(moved, "Complete", "UI_TOAST_CHALLENGE_COMPLETE", 1.0, 1.0);
		failedSound    = tone(moved, "Failed", "ENTITY_VILLAGER_NO", 1.0, 1.0);
		tickSound      = tone(moved, "Tick", "BLOCK_NOTE_BLOCK_HAT", 0.3, 1.8);
		unclaimedSound = tone(moved, "Unclaimed", "ENTITY_ENDER_DRAGON_GROWL", 0.5, 1.5);

		contributionDefenderPresenceTick = moved.getDouble("Contribution.Points.Defender_Presence_Tick",
		                                                   "Turf.Contribution.Points.Defender_Presence_Tick", 0.5);
		contributionAttackerPresenceTick = moved.getDouble("Contribution.Points.Attacker_Presence_Tick",
		                                                   "Turf.Contribution.Points.Attacker_Presence_Tick", 1.0);
		contributionCaptureCompleteBonus = moved.getDouble("Contribution.Points.Capture_Complete_Bonus",
		                                                   "Turf.Contribution.Points.Capture_Complete_Bonus", 50.0);
		contributionDefenseSuccessBonus  = moved.getDouble("Contribution.Points.Defense_Success_Bonus",
		                                                   "Turf.Contribution.Points.Defense_Success_Bonus", 25.0);
	}

	private static Tone tone(MovedSetting moved, String key, String name, double volume, double pitch) {
		String path       = "Capture.Sounds." + key + ".";
		String legacyPath = "Turf." + path;
		return new Tone(moved.getString(path + "Name", legacyPath + "Name", name),
		                (float) moved.getDouble(path + "Volume", legacyPath + "Volume", volume),
		                (float) moved.getDouble(path + "Pitch", legacyPath + "Pitch", pitch));
	}

	/** An empty or unparseable list falls back to 25/50/75, like the old {@code Settings} loader. */
	private static List<Integer> milestones(MovedSetting moved) {
		List<Integer> parsed = new ArrayList<>();
		for (String raw : moved.getStringList("Capture.Progress_Milestones", "Turf.Capture.Progress_Milestones",
		                                      List.of("25", "50", "75"))) {
			try {
				parsed.add(Integer.parseInt(raw.trim()));
			} catch (NumberFormatException ignored) {
				// skip a malformed entry
			}
		}
		return parsed.isEmpty() ? List.of(25, 50, 75) : parsed;
	}
}
