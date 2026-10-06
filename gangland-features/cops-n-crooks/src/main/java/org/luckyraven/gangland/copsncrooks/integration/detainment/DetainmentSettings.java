package org.luckyraven.gangland.copsncrooks.integration.detainment;

import lombok.AccessLevel;
import lombok.Getter;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksYamlConfig;
import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.Objects;

/**
 * The detainment knobs of {@code copsncrooks/detainment.yml} (0.15.1, formerly settings.yml {@code Detainment}; the
 * guard radius went to cops.yml). The file keeps the settings.yml paths, and every key is read through
 * {@link MovedSetting} so a server that still tunes the old key keeps its value for one release. Parsed once per load
 * in {@link #initialize()} and cached; the fields hold the old {@code Settings} defaults until then.
 */
@Getter
public final class DetainmentSettings implements FileInitializer {

	static final String FILE_NAME = "detainment";

	private static final String SOUNDS = "Detainment.Sounds.";

	private final FileHandler fileHandler;
	@Getter(AccessLevel.NONE)
	private final FileManager fileManager;

	private int    jailMaxCapacity = 10;
	private int    transitDelayTicks = 400;
	private int    breakFreeTapsRequired = 25;
	private int    breakFreeResetWindowTicks = 40;
	private double handcuffBribeBaseCost = 500.0;
	private double handcuffBribePerLevel = 250.0;
	private double bailBaseCost = 2_500.0;
	private double bailPerLevel = 1_000.0;
	private double jailBribeBaseCost = 1_000.0;
	private double jailBribePerLevel = 500.0;
	private double jailBribeSuccessChance = 0.35;
	private int    jailBribeFailPenaltySeconds = 60;
	private int    sentenceBaseSeconds = 180;
	private int    sentencePerWantedLevelSeconds = 60;
	private String fallbackExitWaypoint = "spawn";
	private String bailSuccessSound = "BLOCK_NOTE_BLOCK_PLING";
	private String bribeSuccessSound = "ENTITY_VILLAGER_YES";
	private String bribeFailSound = "ENTITY_VILLAGER_NO";
	private String transitCommitSound = "BLOCK_IRON_DOOR_CLOSE";
	private String sentenceCompleteSound = "BLOCK_BELL_USE";

	public DetainmentSettings(FileManager fileManager) {
		this.fileManager = fileManager;

		try {
			fileManager.checkFileLoaded(FILE_NAME);
			this.fileHandler = Objects.requireNonNull(fileManager.getFile(FILE_NAME));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public void initialize() {
		MovedSetting m = MovedSetting.of(fileHandler, fileManager, CopsNCrooksYamlConfig.MODULE_ID);

		jailMaxCapacity               = m.getInt(path("Jail.Max_Capacity"), path("Jail.Max_Capacity"), 10);
		transitDelayTicks             = m.getInt(path("Transit.Delay_Ticks"), path("Transit.Delay_Ticks"), 400);
		breakFreeTapsRequired         = m.getInt(path("Break_Free.Taps_Required"), path("Break_Free.Taps_Required"),
		                                         25);
		breakFreeResetWindowTicks     = m.getInt(path("Break_Free.Reset_Window_Ticks"),
		                                         path("Break_Free.Reset_Window_Ticks"), 40);
		handcuffBribeBaseCost         = m.getDouble(path("Handcuff_Bribe.Base_Cost"), path("Handcuff_Bribe.Base_Cost"),
		                                            500.0);
		handcuffBribePerLevel         = m.getDouble(path("Handcuff_Bribe.Per_Wanted_Level"),
		                                            path("Handcuff_Bribe.Per_Wanted_Level"), 250.0);
		bailBaseCost                  = m.getDouble(path("Bail.Base_Cost"), path("Bail.Base_Cost"), 2_500.0);
		bailPerLevel                  = m.getDouble(path("Bail.Per_Wanted_Level"), path("Bail.Per_Wanted_Level"),
		                                            1_000.0);
		jailBribeBaseCost             = m.getDouble(path("Jail_Bribe.Base_Cost"), path("Jail_Bribe.Base_Cost"),
		                                            1_000.0);
		jailBribePerLevel             = m.getDouble(path("Jail_Bribe.Per_Wanted_Level"),
		                                            path("Jail_Bribe.Per_Wanted_Level"), 500.0);
		jailBribeSuccessChance        = m.getDouble(path("Jail_Bribe.Success_Chance"),
		                                            path("Jail_Bribe.Success_Chance"), 0.35);
		jailBribeFailPenaltySeconds   = m.getInt(path("Jail_Bribe.Fail_Penalty_Seconds"),
		                                         path("Jail_Bribe.Fail_Penalty_Seconds"), 60);
		sentenceBaseSeconds           = m.getInt(path("Sentence.Base_Seconds"), path("Sentence.Base_Seconds"), 180);
		sentencePerWantedLevelSeconds = m.getInt(path("Sentence.Per_Wanted_Level_Seconds"),
		                                         path("Sentence.Per_Wanted_Level_Seconds"), 60);
		fallbackExitWaypoint          = m.getString(path("Fallback_Exit_Waypoint"), path("Fallback_Exit_Waypoint"),
		                                            "spawn");

		bailSuccessSound      = m.getString(SOUNDS + "Bail_Success", SOUNDS + "Bail_Success", "BLOCK_NOTE_BLOCK_PLING");
		bribeSuccessSound     = m.getString(SOUNDS + "Bribe_Success", SOUNDS + "Bribe_Success", "ENTITY_VILLAGER_YES");
		bribeFailSound        = m.getString(SOUNDS + "Bribe_Fail", SOUNDS + "Bribe_Fail", "ENTITY_VILLAGER_NO");
		transitCommitSound    = m.getString(SOUNDS + "Transit_Commit", SOUNDS + "Transit_Commit",
		                                    "BLOCK_IRON_DOOR_CLOSE");
		sentenceCompleteSound = m.getString(SOUNDS + "Sentence_Complete", SOUNDS + "Sentence_Complete",
		                                    "BLOCK_BELL_USE");
	}

	/** detainment.yml keeps the settings.yml path, so the module path and the legacy path are the same. */
	private static String path(String key) {
		return "Detainment." + key;
	}
}
