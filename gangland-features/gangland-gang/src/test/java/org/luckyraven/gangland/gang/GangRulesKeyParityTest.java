package org.luckyraven.gangland.gang;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every key {@code GangRules} reads exists in the shipped gang/gang_rules.yml. */
@DisplayName("GangRules keys - present in the shipped gang_rules.yml")
class GangRulesKeyParityTest {

	private static final List<String> KEYS = List.of(
			"Name_Pattern", "Ranks.Preset", "Ranks.Preset_Permissions.member", "Progression.Max_Level",
			"Progression.Base_Experience", "Ranks.Preset_Permissions.officer",
			// Ranks.Minimum (W5-R)
			"Ranks.Minimum.Chat", "Ranks.Minimum.Deposit", "Ranks.Minimum.Safehouse", "Ranks.Minimum.Fight",
			"Ranks.Minimum.Rally", "Ranks.Minimum.Invite", "Ranks.Minimum.Kick", "Ranks.Minimum.Promote",
			"Ranks.Minimum.Demote", "Ranks.Minimum.Withdraw", "Ranks.Minimum.Safehouse_Manage",
			"Ranks.Minimum.Recruiting", "Ranks.Minimum.Description", "Ranks.Minimum.Ally", "Ranks.Minimum.Rename",
			"Ranks.Minimum.Display", "Ranks.Minimum.Color", "Ranks.Minimum.Notice", "Ranks.Minimum.Upgrade",
			"Ranks.Minimum.Friendly_Fire",
			// Levels (W5-L)
			"Levels.Reward_Min_Member_Age_Hours", "Levels.Level_0.Cost", "Levels.Level_0.Max_Members",
			"Levels.Level_0.Max_Safehouses", "Levels.Level_0.Max_Allies", "Levels.Level_0.Chat_Prefix",
			"Levels.Level_0.Commands", "Levels.Level_1.Cost", "Levels.Level_5.Chat_Prefix",
			"Levels.Level_10.Cost", "Levels.Level_20.Max_Members", "Levels.Level_20.Commands",
			// Commands, Purge (W5-M)
			"Commands.On_Join", "Commands.On_Leave", "Commands.On_Kick", "Purge.Enabled", "Purge.Inactive_Days",
			"Purge.Member_Inactive_Days", "Purge.First_Run_Delay_Hours", "Purge.Refund_Fee",
			// Worlds (W5-K)
			"Worlds.Disabled",
			// Friendly_Fire, Combat (W5-F)
			"Friendly_Fire.Global", "Friendly_Fire.Allow_Self_Damage", "Friendly_Fire.Potions", "Combat.Tag_Seconds",
			// Safehouse (W5-H)
			"Safehouse.Enabled", "Safehouse.Warm_Up_Seconds", "Safehouse.Cooldown_Seconds", "Safehouse.Cost",
			"Safehouse.Safe_Check", "Safehouse.Rally_Consent_Seconds",
			// Chat (W5-C)
			"Chat.Log_To_Console", "Chat.No_Gang_Tag", "Chat.Public_Tag.Enabled",
			// Stats, Ranking, Placeholders (W5-S)
			"Stats.Assist_Window_Seconds", "Stats.Min_Victim_Member_Age_Hours", "Stats.Repeat_Kill_Cooldown_Seconds",
			"Stats.Max_Kills_Per_Victim_Per_Day", "Stats.Snapshot_Seconds", "Ranking.Min_Kills", "Ranking.Page_Size",
			"Placeholders.Default_Value",
			// Display_Name (W5-U2)
			"Display_Name.Allow_Hex", "Display_Name.Parse_Colours", "Display_Name.Max_Length",
			"Display_Name.Blocked_Words");

	/** The 21 level rows are shipped explicitly (a missing row inherits, but the file lists all of them). */
	@Test
	@DisplayName("all 21 level rows are shipped")
	void allLevelRowsShipped() throws Exception {
		try (InputStream stream = GangRulesKeyParityTest.class.getResourceAsStream("/gang/gang_rules.yml")) {
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
					new InputStreamReader(stream, StandardCharsets.UTF_8));
			for (int level = 0; level <= 20; level++) {
				assertTrue(yaml.isSet("Levels.Level_" + level + ".Cost"), "missing Level_" + level);
			}
		}
	}

	@Test
	@DisplayName("every key GangRules reads is set in gang/gang_rules.yml")
	void everyKeyIsShipped() throws Exception {
		try (InputStream stream = GangRulesKeyParityTest.class.getResourceAsStream("/gang/gang_rules.yml")) {
			assertNotNull(stream);
			YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
					new InputStreamReader(stream, StandardCharsets.UTF_8));
			for (String key : KEYS) {
				assertTrue(yaml.isSet(key), "missing " + key);
			}
		}
	}
}
