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
			"Create.Snap_To_Chunks", "Create.Min_Side_Blocks", "Create.Max_Side_Blocks");

	private static final List<String> MAP_KEYS = List.of(
			"Map.Width", "Map.Height", "Map.Cell_Blocks", "Map.Auto_Min_Interval_Seconds", "Map.Bedrock_Text_Form",
			"Hud.Enabled", "Hud.Interval_Seconds", "Hud.Priority");

	private static final List<String> MESSAGE_KEYS = List.of(
			"Reason.Attackable", "Reason.Cooldown", "Reason.Owners_Active");

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
	@DisplayName("turf_messages.yml has the three Reason lines the capture verdict reads")
	void messageKeysPresent() throws IOException {
		YamlConfiguration yaml = load("/turf/turf_messages.yml");

		for (String key : MESSAGE_KEYS) {
			assertTrue(yaml.isSet(key), "missing " + key);
		}
	}
}
