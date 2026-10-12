package org.luckyraven.gangland.turf;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.support.YamlFixture;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every key the 0.17.0 turf config classes read exists in the shipped turf/turf_rules.yml, turf/turf_map.yml and
 * turf/turf_messages.yml, so a server that copies the defaults never falls back silently.
 */
@DisplayName("Turf 0.17.0 YAML - every key read is in the shipped file")
class TurfModuleYamlKeysTest {

	private static final List<String> RULES_KEYS = List.of(
			"Create.Snap_To_Chunks", "Create.Min_Side_Blocks", "Create.Max_Side_Blocks",
			"Capture.Window", "Capture.Wake_Up_Shield_Seconds", "Capture.Window_Min_Member_Age_Hours",
			"Capture.Presence_Seconds", "Capture.Min_Capture_Seconds", "Capture.Max_Contest_Minutes",
			"Capture.Guard_Weight_Cap", "Turf_Cap.Base", "Turf_Cap.Per_Level", "Turf_Cap.Ceiling",
			"Income.Idle_Hours", "Income.Max_Multiplier", "Income.Gang_XP_Per_Payout", "Garrison.Max_Reserve", "Teleport_Guard.Buffer_Blocks");

	private static final List<String> MAP_KEYS = List.of(
			"Map.Width", "Map.Height", "Map.Cell_Blocks", "Map.Auto_Min_Interval_Seconds", "Map.Bedrock_Text_Form",
			"Hud.Enabled", "Hud.Interval_Seconds", "Hud.Priority",
			"Web.Enabled", "Web.Layer_Label", "Web.Fill_Opacity", "Web.Line_Opacity", "Web.Line_Weight",
			"Web.Unclaimed_Color");

	private static final List<String> MESSAGE_KEYS = List.of(
			"Reason.Attackable", "Reason.Cooldown", "Reason.Owners_Active", "Reason.No_Owners_Online", "Reason.Waking_Up",
			"Reason.Over_Cap", "Info.BossBar_Guards",
			"Map.Zoom_Set", "Map.Zoom_Limit_In", "Map.Zoom_Limit_Out", "Map.Zoom_Usage",
			"Map.Border_Shown", "Map.Border_None",
			"Teleport_Guard.Source_Under_Attack", "Teleport_Guard.Destination_Under_Attack",
			"Teleport_Guard.Destination_Rival", "Teleport_Guard.Gang_In_Fight",
			"Teleport_Guard.Safehouse_Refused", "Teleport_Guard.Create_Gang_World_Disabled");

	private static YamlConfiguration load(String resource) throws IOException {
		return YamlConfiguration.loadConfiguration(new StringReader(YamlFixture.shipped(resource)));
	}

	@Test
	@DisplayName("turf_rules.yml has every key TurfRules reads")
	void rulesKeysPresent() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_rules.yml");

		for (String key : RULES_KEYS) {
			assertTrue(yaml.isSet(key), "missing " + key);
		}
	}

	@Test
	@DisplayName("turf_map.yml has every key TurfMapSettings reads")
	void mapKeysPresent() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_map.yml");

		for (String key : MAP_KEYS) {
			assertTrue(yaml.isSet(key), "missing " + key);
		}
	}

	@Test
	@DisplayName("turf_messages.yml has the Reason and Info lines the capture verdict reads")
	void messageKeysPresent() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_messages.yml");

		for (String key : MESSAGE_KEYS) {
			assertTrue(yaml.isSet(key), "missing " + key);
		}
	}
}
