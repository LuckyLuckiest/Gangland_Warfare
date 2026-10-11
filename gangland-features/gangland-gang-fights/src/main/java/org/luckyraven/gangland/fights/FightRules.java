package org.luckyraven.gangland.fights;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.util.List;

/**
 * Gameplay rules read from {@code fight/fight_rules.yml}. A missing key keeps the shipped default shown here;
 * {@link #reload()} (what {@code /glw reload} runs through the file manager) re-reads the file (GP-38).
 */
@CustomLog
public class FightRules implements FileInitializer {

	private static final String FILE_NAME = "fight_rules";

	private final FileHandler fileHandler;

	// Challenge
	@Getter
	private int challengeExpirySeconds = 60;
	@Getter
	private int joinWindowSeconds = 60;
	@Getter
	private int countdownSeconds = 10;
	@Getter
	private int maxDurationMinutes = 10;
	@Getter
	private int resultSeconds = 5;
	@Getter
	private int minTeamSize = 1;
	@Getter
	private int maxTeamSize = 5;
	@Getter
	private boolean requireEqualTeams = true;
	@Getter
	private int declineCooldownSeconds = 60;
	@Getter
	private int arenaCooldownSeconds = 30;
	@Getter
	private String timeoutResult = "MOST_ALIVE";
	@Getter
	private int minMemberAgeHours = 0;

	// Bets
	@Getter
	private double maxBet = 100000;
	@Getter
	private int maxBetPercentOfBank = 50;
	@Getter
	private int houseCutPercent = 0;
	@Getter
	private double maxNetTransferPerPairPerDay = 200000;
	@Getter
	private double maxBetTransferPerGangPerDay = 300000;

	// Ranked
	@Getter
	private double rankedMinBet = 1000;
	@Getter
	private int rankedMinMemberAgeHours = 24;
	@Getter
	private int rankedMinOpponentGangAgeDays = 7;
	@Getter
	private int rankedPerPairPerDay = 3;
	@Getter
	private int rematchCooldownMinutes = 30;
	@Getter
	private int rankedBoardWinsPerOpponentPerDay = 1;

	// Guards
	@Getter
	private int maxLeaveWarnings = 3;
	@Getter
	private boolean protectBlocks = true;
	@Getter
	private boolean allowVehicles = false;
	@Getter
	private boolean respectTotems = false;
	@Getter
	private String commandBlockMode = "ALLOWLIST";
	@Getter
	private List<String> allowedCommands = List.of("glw gang fight", "gangland gang fight", "msg", "r", "tell");
	@Getter
	private List<String> blockedCommands = List.of();
	@Getter
	private boolean blockSameAddress = false;

	// Arenas
	@Getter
	private int minArenaVolume = 64;
	@Getter
	private int outlineSeconds = 10;

	public FightRules(FileManager fileManager) {
		this.fileHandler = fileManager.getFile(FILE_NAME);
		initialize();
	}

	@Override
	public void initialize() {
		FileConfiguration config = fileHandler.getFileConfiguration();

		challengeExpirySeconds = config.getInt("Challenge.Challenge_Expiry_Seconds", 60);
		joinWindowSeconds = config.getInt("Challenge.Join_Window_Seconds", 60);
		countdownSeconds = config.getInt("Challenge.Countdown_Seconds", 10);
		maxDurationMinutes = config.getInt("Challenge.Max_Duration_Minutes", 10);
		resultSeconds = config.getInt("Challenge.Result_Seconds", 5);
		minTeamSize = config.getInt("Challenge.Min_Team_Size", 1);
		maxTeamSize = config.getInt("Challenge.Max_Team_Size", 5);
		requireEqualTeams = config.getBoolean("Challenge.Require_Equal_Teams", true);
		declineCooldownSeconds = config.getInt("Challenge.Decline_Cooldown_Seconds", 60);
		arenaCooldownSeconds = config.getInt("Challenge.Arena_Cooldown_Seconds", 30);
		timeoutResult = config.getString("Challenge.Timeout_Result", "MOST_ALIVE");
		minMemberAgeHours = config.getInt("Challenge.Min_Member_Age_Hours", 0);
		maxBet = config.getDouble("Bets.Max_Bet", 100000);
		maxBetPercentOfBank = config.getInt("Bets.Max_Bet_Percent_Of_Bank", 50);
		houseCutPercent = config.getInt("Bets.House_Cut_Percent", 0);
		maxNetTransferPerPairPerDay = config.getDouble("Bets.Max_Net_Transfer_Per_Pair_Per_Day", 200000);
		maxBetTransferPerGangPerDay = config.getDouble("Bets.Max_Bet_Transfer_Per_Gang_Per_Day", 300000);
		rankedMinBet = config.getDouble("Ranked.Ranked_Min_Bet", 1000);
		rankedMinMemberAgeHours = config.getInt("Ranked.Ranked_Min_Member_Age_Hours", 24);
		rankedMinOpponentGangAgeDays = config.getInt("Ranked.Ranked_Min_Opponent_Gang_Age_Days", 7);
		rankedPerPairPerDay = config.getInt("Ranked.Ranked_Per_Pair_Per_Day", 3);
		rematchCooldownMinutes = config.getInt("Ranked.Rematch_Cooldown_Minutes", 30);
		rankedBoardWinsPerOpponentPerDay = config.getInt("Ranked.Ranked_Board_Wins_Per_Opponent_Per_Day", 1);
		maxLeaveWarnings = config.getInt("Guards.Max_Leave_Warnings", 3);
		protectBlocks = config.getBoolean("Guards.Protect_Blocks", true);
		allowVehicles = config.getBoolean("Guards.Allow_Vehicles", false);
		respectTotems = config.getBoolean("Guards.Respect_Totems", false);
		commandBlockMode = config.getString("Guards.Command_Block_Mode", "ALLOWLIST");
		allowedCommands = config.isSet("Guards.Allowed_Commands") ? List.copyOf(config.getStringList("Guards.Allowed_Commands")) : List.of("glw gang fight", "gangland gang fight", "msg", "r", "tell");
		blockedCommands = config.isSet("Guards.Blocked_Commands") ? List.copyOf(config.getStringList("Guards.Blocked_Commands")) : List.of();
		blockSameAddress = config.getBoolean("Guards.Block_Same_Address", false);
		minArenaVolume = config.getInt("Arenas.Min_Arena_Volume", 64);
		outlineSeconds = config.getInt("Arenas.Outline_Seconds", 10);
	}

	/** Re-reads the file from disk and applies it again. */
	public void reload() {
		try {
			fileHandler.reloadData();
		} catch (IOException exception) {
			log.warn("fight_rules.yml could not be reloaded: {}", exception.getMessage());
			return;
		}
		initialize();
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

}
