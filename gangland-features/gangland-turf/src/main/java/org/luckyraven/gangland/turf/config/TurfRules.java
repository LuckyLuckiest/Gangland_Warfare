package org.luckyraven.gangland.turf.config;

import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.Locale;

/**
 * Turf rules read from {@code turf/turf_rules.yml}. Five sections: {@code Create:} (how a new turf is drawn - snapping
 * and side clamps; existing turfs are never changed by these keys), {@code Capture:} (the 0.17.0 capture window, the
 * wake-up shield, presence, the clamp, the stall and the guard weight), {@code Turf_Cap:} (how many turfs a gang may
 * hold per level), {@code Income:} (the idle window, the boost cap and the gang XP per payout) and {@code Garrison:}
 * (the reserve cap). A bad value falls back to its default or is clamped; {@code /glw reload} re-reads the file.
 */
@CustomLog
public class TurfRules implements FileInitializer {

	static final int DEFAULT_MIN_SIDE_BLOCKS = 16;
	static final int DEFAULT_MAX_SIDE_BLOCKS = 512;

	private static final String FILE_NAME          = "turf_rules";
	private static final String SNAP_KEY           = "Create.Snap_To_Chunks";
	private static final String MIN_SIDE_KEY       = "Create.Min_Side_Blocks";
	private static final String MAX_SIDE_KEY       = "Create.Max_Side_Blocks";
	private static final String WINDOW_KEY         = "Capture.Window";
	private static final String WAKE_UP_KEY        = "Capture.Wake_Up_Shield_Seconds";
	private static final String MEMBER_AGE_KEY     = "Capture.Window_Min_Member_Age_Hours";
	private static final String PRESENCE_KEY       = "Capture.Presence_Seconds";
	private static final String MIN_CAPTURE_KEY    = "Capture.Min_Capture_Seconds";
	private static final String MAX_CONTEST_KEY    = "Capture.Max_Contest_Minutes";
	private static final String GUARD_CAP_KEY      = "Capture.Guard_Weight_Cap";
	private static final String CAP_BASE_KEY       = "Turf_Cap.Base";
	private static final String CAP_PER_LEVEL_KEY  = "Turf_Cap.Per_Level";
	private static final String CAP_CEILING_KEY    = "Turf_Cap.Ceiling";
	private static final String IDLE_HOURS_KEY     = "Income.Idle_Hours";
	private static final String MAX_MULTIPLIER_KEY = "Income.Max_Multiplier";
	private static final String XP_PER_PAYOUT_KEY  = "Income.Gang_XP_Per_Payout";
	private static final String MAX_RESERVE_KEY    = "Garrison.Max_Reserve";

	private final FileHandler fileHandler;

	@Getter
	private boolean snapToChunks;
	@Getter
	private int     minSideBlocks;
	@Getter
	private int     maxSideBlocks;

	private volatile CaptureRules capture = CaptureRules.DEFAULTS;

	@Getter
	private int    turfCapBase;
	@Getter
	private int    turfCapPerLevel;
	@Getter
	private int    turfCapCeiling;
	@Getter
	private int    incomeIdleHours;
	@Getter
	private double maxIncomeMultiplier;
	@Getter
	private double gangXpPerPayout;
	@Getter
	private int    maxReserve;

	public TurfRules(FileManager fileManager) {
		this.fileHandler = fileManager.getFile(FILE_NAME);
		initialize();
	}

	@Override
	public void initialize() {
		var config = fileHandler.getFileConfiguration();

		snapToChunks  = config.getBoolean(SNAP_KEY, true);
		minSideBlocks = Math.max(1, config.getInt(MIN_SIDE_KEY, DEFAULT_MIN_SIDE_BLOCKS));
		maxSideBlocks = Math.max(minSideBlocks, config.getInt(MAX_SIDE_KEY, DEFAULT_MAX_SIDE_BLOCKS));

		CaptureRules.Window window;
		String rawWindow = config.getString(WINDOW_KEY, CaptureRules.Window.OWNERS_ONLINE.name());
		try {
			window = CaptureRules.Window.valueOf(rawWindow.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			log.warn("turf_rules.yml {} '{}' is not OWNERS_ONLINE or OWNERS_OFFLINE; using OWNERS_ONLINE", WINDOW_KEY,
			         rawWindow);
			window = CaptureRules.Window.OWNERS_ONLINE;
		}
		capture = new CaptureRules(window,
		                           Math.max(0, config.getInt(WAKE_UP_KEY, 120)),
		                           Math.max(0, config.getInt(MEMBER_AGE_KEY, 24)),
		                           Math.max(0, config.getInt(PRESENCE_KEY, 15)),
		                           Math.max(0, config.getInt(MIN_CAPTURE_KEY, 60)),
		                           Math.max(0, config.getInt(MAX_CONTEST_KEY, 10)),
		                           Math.max(0, config.getInt(GUARD_CAP_KEY, 4)));

		turfCapBase         = Math.max(0, config.getInt(CAP_BASE_KEY, 2));
		turfCapPerLevel     = Math.max(0, config.getInt(CAP_PER_LEVEL_KEY, 1));
		turfCapCeiling      = Math.max(0, config.getInt(CAP_CEILING_KEY, 6));
		incomeIdleHours     = Math.max(1, config.getInt(IDLE_HOURS_KEY, 24));
		maxIncomeMultiplier = Math.max(1.0, config.getDouble(MAX_MULTIPLIER_KEY, 2.0));
		gangXpPerPayout     = Math.max(0.0, config.getDouble(XP_PER_PAYOUT_KEY, 1.0));
		maxReserve          = Math.max(0, config.getInt(MAX_RESERVE_KEY, 12));
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	/** The Capture: rules in force (read through a Supplier, so a /glw reload is seen). */
	public CaptureRules capture() {
		return capture;
	}

	/** Turfs a gang of this level may hold: min(Ceiling, Base + Per_Level x level); a negative level counts as 0. */
	public int cap(int gangLevel) {
		return Math.min(turfCapCeiling, turfCapBase + turfCapPerLevel * Math.max(0, gangLevel));
	}

}
