package org.luckyraven.gangland.gang.file;

import org.luckyraven.gangland.gang.GangLevels;
import lombok.CustomLog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import lombok.Getter;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Gameplay rules read from {@code gang/gang_rules.yml} (0.17.0). Read once per load: the name pattern is compiled, the
 * rank preset is cleaned up, and any bad value falls back to its default with one warning. The preset permissions are
 * read on each call.
 */
@CustomLog
public class GangRules implements FileInitializer {

	static final String       DEFAULT_NAME_REGEX = "[A-Za-z0-9_]{3,16}";
	static final List<String> DEFAULT_PRESET     = List.of("member", "officer", "owner");

	private static final String FILE_NAME        = "gang_rules";
	private static final String NAME_PATTERN_KEY = "Name_Pattern";
	private static final String PRESET_KEY       = "Ranks.Preset";
	private static final String PERMISSIONS_KEY  = "Ranks.Preset_Permissions.";
	private static final String MAX_LEVEL_KEY    = "Progression.Max_Level";
	private static final String BASE_XP_KEY      = "Progression.Base_Experience";
	private static final int    DEFAULT_MAX_LEVEL = 20;
	private static final double DEFAULT_BASE_XP  = 200.0;

	/** Defaults of {@code Ranks.Minimum}: the lowest rank that may use each feature (feature key to rank name). */
	static final Map<String, String> DEFAULT_MINIMUM_RANKS = defaultMinimumRanks();

	/** One row of {@code Levels}: what a gang level costs and allows. {@code 0} limits mean unlimited. */
	public record LevelRowValues(double cost, int maxMembers, int maxSafehouses, int maxAllies, String chatPrefix,
	                             List<String> commands) { }

	private final FileHandler fileHandler;

	@Getter
	private Pattern      namePattern;
	@Getter
	private List<String> rankPreset;
	@Getter
	private int          maxLevel;
	@Getter
	private double       baseExperience;

	// Ranks.Minimum (W5-R)
	@Getter
	private Map<String, String> minimumRanks = DEFAULT_MINIMUM_RANKS;

	// Levels (W5-L)
	@Getter
	private int                          rewardMinMemberAgeHours = 24;
	@Getter
	private Map<Integer, LevelRowValues> levelRows               = Map.of();

	// Commands (W5-M)
	@Getter
	private List<String> joinCommands  = List.of();
	@Getter
	private List<String> leaveCommands = List.of();
	@Getter
	private List<String> kickCommands  = List.of();

	// Purge (W5-M)
	@Getter
	private boolean purgeEnabled;
	@Getter
	private int     purgeInactiveDays       = 30;
	@Getter
	private int     purgeMemberInactiveDays = 60;
	@Getter
	private int     purgeFirstRunDelayHours = 24;
	@Getter
	private boolean purgeRefundFee;

	// Worlds (W5-K), lower case
	@Getter
	private Set<String> disabledWorlds = Set.of();

	// Friendly_Fire (W5-F)
	@Getter
	private String  friendlyFireGlobal          = "GANG_CHOICE";
	@Getter
	private boolean friendlyFireAllowSelfDamage = true;
	@Getter
	private String  friendlyFirePotions         = "NEGATIVE_ONLY";

	// Combat (W5-F)
	@Getter
	private int combatTagSeconds = 15;

	// Safehouse (W5-H)
	@Getter
	private boolean safehouseEnabled             = true;
	@Getter
	private int     safehouseWarmUpSeconds       = 5;
	@Getter
	private int     safehouseCooldownSeconds     = 60;
	@Getter
	private double  safehouseCost;
	@Getter
	private boolean safehouseSafeCheck           = true;
	@Getter
	private int     safehouseRallyConsentSeconds = 60;

	// Chat (W5-C)
	@Getter
	private boolean chatLogToConsole;
	@Getter
	private String  chatNoGangTag = "";
	@Getter
	private boolean chatPublicTagEnabled;

	// Stats (W5-S)
	@Getter
	private int statsAssistWindowSeconds       = 10;
	@Getter
	private int statsMinVictimMemberAgeHours   = 24;
	@Getter
	private int statsRepeatKillCooldownSeconds = 300;
	@Getter
	private int statsMaxKillsPerVictimPerDay   = 3;
	@Getter
	private int statsSnapshotSeconds           = 60;

	// Ranking (W5-S)
	@Getter
	private int rankingMinKills = 10;
	@Getter
	private int rankingPageSize = 10;

	// Placeholders (W5-S)
	@Getter
	private String placeholdersDefaultValue = "-";

	// Display_Name (W5-U2)
	@Getter
	private boolean      displayNameAllowHex     = true;
	@Getter
	private boolean      displayNameParseColours = true;
	@Getter
	private int          displayNameMaxLength    = 24;
	@Getter
	private List<String> displayNameBlockedWords = List.of();

	public GangRules(FileManager fileManager) {
		this.fileHandler = fileManager.getFile(FILE_NAME);
		initialize();
	}

	@Override
	public void initialize() {
		var config = fileHandler.getFileConfiguration();

		String regex = config.getString(NAME_PATTERN_KEY, DEFAULT_NAME_REGEX);
		try {
			namePattern = Pattern.compile(regex);
		} catch (PatternSyntaxException exception) {
			log.warn("gang_rules.yml {} is not a valid regular expression ({}); using {}", NAME_PATTERN_KEY,
			         exception.getDescription(), DEFAULT_NAME_REGEX);
			namePattern = Pattern.compile(DEFAULT_NAME_REGEX);
		}

		Set<String> cleaned = new LinkedHashSet<>();
		for (String rank : config.getStringList(PRESET_KEY)) {
			if (rank != null && !rank.isBlank()) {
				cleaned.add(rank.trim().toLowerCase(Locale.ROOT));
			}
		}
		if (cleaned.size() < 2) {
			log.warn("gang_rules.yml {} needs at least two ranks; using {}", PRESET_KEY, DEFAULT_PRESET);
			rankPreset = DEFAULT_PRESET;
		} else {
			rankPreset = List.copyOf(cleaned);
		}

		maxLevel       = Math.max(1, config.getInt(MAX_LEVEL_KEY, DEFAULT_MAX_LEVEL));
		baseExperience = Math.max(1.0, config.getDouble(BASE_XP_KEY, DEFAULT_BASE_XP));
		GangLevels.bind(maxLevel, baseExperience);

		readParityKeys(config);
	}

	/**
	 * Re-reads the file from disk and applies it again, which is what {@code /glw reload} does through the file manager
	 * (docket GP-38: an edited {@code Levels} row is visible after a reload).
	 */
	public void reload() {
		try {
			fileHandler.reloadData();
		} catch (IOException exception) {
			log.warn("gang_rules.yml could not be reloaded: {}", exception.getMessage());
			return;
		}
		initialize();
	}

	private void readParityKeys(FileConfiguration config) {
		Map<String, String> minimums = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : DEFAULT_MINIMUM_RANKS.entrySet()) {
			String rank = config.getString("Ranks.Minimum." + entry.getKey(), entry.getValue());
			minimums.put(entry.getKey(), rank == null ? entry.getValue() : rank.trim().toLowerCase(Locale.ROOT));
		}
		minimumRanks = Collections.unmodifiableMap(minimums);

		rewardMinMemberAgeHours = Math.max(0, config.getInt("Levels.Reward_Min_Member_Age_Hours", 24));
		levelRows               = readLevelRows(config.getConfigurationSection("Levels"));

		joinCommands  = stringList(config, "Commands.On_Join");
		leaveCommands = stringList(config, "Commands.On_Leave");
		kickCommands  = stringList(config, "Commands.On_Kick");

		purgeEnabled            = config.getBoolean("Purge.Enabled", false);
		purgeInactiveDays       = Math.max(1, config.getInt("Purge.Inactive_Days", 30));
		purgeMemberInactiveDays = Math.max(1, config.getInt("Purge.Member_Inactive_Days", 60));
		purgeFirstRunDelayHours = Math.max(0, config.getInt("Purge.First_Run_Delay_Hours", 24));
		purgeRefundFee          = config.getBoolean("Purge.Refund_Fee", false);

		Set<String> worlds = new LinkedHashSet<>();
		for (String world : config.getStringList("Worlds.Disabled")) {
			if (world != null && !world.isBlank()) worlds.add(world.trim().toLowerCase(Locale.ROOT));
		}
		disabledWorlds = Collections.unmodifiableSet(worlds);

		friendlyFireGlobal          = config.getString("Friendly_Fire.Global", "GANG_CHOICE");
		friendlyFireAllowSelfDamage = config.getBoolean("Friendly_Fire.Allow_Self_Damage", true);
		friendlyFirePotions         = config.getString("Friendly_Fire.Potions", "NEGATIVE_ONLY");

		combatTagSeconds = Math.max(0, config.getInt("Combat.Tag_Seconds", 15));

		safehouseEnabled             = config.getBoolean("Safehouse.Enabled", true);
		safehouseWarmUpSeconds       = Math.max(0, config.getInt("Safehouse.Warm_Up_Seconds", 5));
		safehouseCooldownSeconds     = Math.max(0, config.getInt("Safehouse.Cooldown_Seconds", 60));
		safehouseCost                = Math.max(0D, config.getDouble("Safehouse.Cost", 0D));
		safehouseSafeCheck           = config.getBoolean("Safehouse.Safe_Check", true);
		safehouseRallyConsentSeconds = Math.max(0, config.getInt("Safehouse.Rally_Consent_Seconds", 60));

		chatLogToConsole     = config.getBoolean("Chat.Log_To_Console", false);
		chatNoGangTag        = config.getString("Chat.No_Gang_Tag", "");
		chatPublicTagEnabled = config.getBoolean("Chat.Public_Tag.Enabled", false);

		statsAssistWindowSeconds       = Math.max(0, config.getInt("Stats.Assist_Window_Seconds", 10));
		statsMinVictimMemberAgeHours   = Math.max(0, config.getInt("Stats.Min_Victim_Member_Age_Hours", 24));
		statsRepeatKillCooldownSeconds = Math.max(0, config.getInt("Stats.Repeat_Kill_Cooldown_Seconds", 300));
		statsMaxKillsPerVictimPerDay   = Math.max(0, config.getInt("Stats.Max_Kills_Per_Victim_Per_Day", 3));
		statsSnapshotSeconds           = Math.max(1, config.getInt("Stats.Snapshot_Seconds", 60));

		rankingMinKills = Math.max(0, config.getInt("Ranking.Min_Kills", 10));
		rankingPageSize = Math.max(1, config.getInt("Ranking.Page_Size", 10));

		placeholdersDefaultValue = config.getString("Placeholders.Default_Value", "-");

		displayNameAllowHex     = config.getBoolean("Display_Name.Allow_Hex", true);
		displayNameParseColours = config.getBoolean("Display_Name.Parse_Colours", true);
		displayNameMaxLength    = Math.max(1, config.getInt("Display_Name.Max_Length", 24));
		displayNameBlockedWords = stringList(config, "Display_Name.Blocked_Words");
	}

	/** The rows of {@code Levels} by number, raw: a missing row is simply absent (W5-L inherits from the row before). */
	private static Map<Integer, LevelRowValues> readLevelRows(ConfigurationSection levels) {
		Map<Integer, LevelRowValues> rows = new TreeMap<>();
		if (levels == null) return Collections.unmodifiableMap(rows);

		for (String key : levels.getKeys(false)) {
			if (!key.startsWith("Level_")) continue;

			int level;
			try {
				level = Integer.parseInt(key.substring("Level_".length()));
			} catch (NumberFormatException exception) {
				log.warn("gang_rules.yml Levels.{} is not a level row (Level_<number>); ignored", key);
				continue;
			}

			ConfigurationSection row = levels.getConfigurationSection(key);
			if (row == null) continue;

			rows.put(level, new LevelRowValues(Math.max(0D, row.getDouble("Cost", 0D)),
			                                   Math.max(0, row.getInt("Max_Members", 0)),
			                                   Math.max(0, row.getInt("Max_Safehouses", 0)),
			                                   Math.max(0, row.getInt("Max_Allies", 0)),
			                                   row.getString("Chat_Prefix", ""),
			                                   List.copyOf(row.getStringList("Commands"))));
		}
		return Collections.unmodifiableMap(rows);
	}

	private static List<String> stringList(FileConfiguration config, String path) {
		List<String> list = new ArrayList<>();
		for (String entry : config.getStringList(path)) {
			if (entry != null && !entry.isBlank()) list.add(entry.trim());
		}
		return Collections.unmodifiableList(list);
	}

	private static Map<String, String> defaultMinimumRanks() {
		Map<String, String> map = new LinkedHashMap<>();
		for (String key : List.of("Chat", "Deposit", "Safehouse")) map.put(key, "member");
		for (String key : List.of("Fight", "Rally", "Invite", "Kick", "Promote", "Demote", "Withdraw",
		                          "Safehouse_Manage", "Recruiting", "Description")) map.put(key, "officer");
		for (String key : List.of("Ally", "Rename", "Display", "Color", "Notice", "Upgrade", "Friendly_Fire")) {
			map.put(key, "owner");
		}
		return Collections.unmodifiableMap(map);
	}

	/** Permission nodes a preset rank gets on a fresh install, trimmed; empty when the rank has none listed. */
	public List<String> getPresetPermissions(String rank) {
		List<String> nodes = fileHandler.getFileConfiguration()
				.getStringList(PERMISSIONS_KEY + rank.toLowerCase(Locale.ROOT));
		List<String> trimmed = new ArrayList<>(nodes.size());
		for (String node : nodes) {
			if (node != null && !node.isBlank()) {
				trimmed.add(node.trim());
			}
		}
		return trimmed;
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

}
