package org.luckyraven.gangland.fights;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every key {@link FightRules} reads exists in the shipped fight/fight_rules.yml, and nothing else is shipped (GP-38). */
@DisplayName("FightRules keys - present in the shipped fight_rules.yml")
class FightRulesKeyParityTest {

	private static final List<String> KEYS = List.of(
			"Challenge.Challenge_Expiry_Seconds",
			"Challenge.Join_Window_Seconds",
			"Challenge.Countdown_Seconds",
			"Challenge.Max_Duration_Minutes",
			"Challenge.Result_Seconds",
			"Challenge.Min_Team_Size",
			"Challenge.Max_Team_Size",
			"Challenge.Require_Equal_Teams",
			"Challenge.Decline_Cooldown_Seconds",
			"Challenge.Arena_Cooldown_Seconds",
			"Challenge.Timeout_Result",
			"Challenge.Min_Member_Age_Hours",
			"Bets.Max_Bet",
			"Bets.Max_Bet_Percent_Of_Bank",
			"Bets.House_Cut_Percent",
			"Bets.Max_Net_Transfer_Per_Pair_Per_Day",
			"Bets.Max_Bet_Transfer_Per_Gang_Per_Day",
			"Ranked.Ranked_Min_Bet",
			"Ranked.Ranked_Min_Member_Age_Hours",
			"Ranked.Ranked_Min_Opponent_Gang_Age_Days",
			"Ranked.Ranked_Per_Pair_Per_Day",
			"Ranked.Rematch_Cooldown_Minutes",
			"Ranked.Ranked_Board_Wins_Per_Opponent_Per_Day",
			"Guards.Max_Leave_Warnings",
			"Guards.Protect_Blocks",
			"Guards.Allow_Vehicles",
			"Guards.Respect_Totems",
			"Guards.Command_Block_Mode",
			"Guards.Allowed_Commands",
			"Guards.Blocked_Commands",
			"Guards.Block_Same_Address",
			"Arenas.Min_Arena_Volume",
			"Arenas.Outline_Seconds");

	private static YamlConfiguration shipped() throws Exception {
		try (InputStream stream = FightRulesKeyParityTest.class.getResourceAsStream("/fight/fight_rules.yml")) {
			assertNotNull(stream);
			return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
		}
	}

	@Test
	@DisplayName("everyKeyIsShipped")
	void everyKeyIsShipped() throws Exception {
		YamlConfiguration yaml = shipped();
		for (String key : KEYS) {
			assertTrue(yaml.isSet(key), "missing " + key);
		}
	}

	@Test
	@DisplayName("every shipped leaf key is one FightRules reads")
	void everyShippedKeyIsRead() throws Exception {
		YamlConfiguration yaml = shipped();
		for (String key : yaml.getKeys(true)) {
			if (!yaml.isConfigurationSection(key)) {
				assertTrue(KEYS.contains(key), "shipped but not read: " + key);
			}
		}
	}
}
